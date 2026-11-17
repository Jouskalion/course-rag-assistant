# 数据库设计

固定 SQLite，共 11 张业务表。相对于早先方案，增加 conversation_documents 关联表，用组合外键确保会话与资料来自同一课程；会话资料集合不再以 JSON 存放。turns 仍保存每次执行的范围和证据快照。

数据库位置为 RAG_APP_HOME/data/courseqa.db。SQLite 是权威存储；Lucene 内存索引和 Java 向量数组可重建。单 JVM、一个导入消费者、最多两个问答执行任务；连接池最多4连接。每个连接开启 foreign_keys 与 busy_timeout=5000，迁移前在事务外启用 WAL。

所有写操作经过 WriteTransactions。需要课程锁时先获取 CourseLocks，再进入短写事务；网络、PDF解析和向量计算在事务外。迁移失败必须停止启动，不删除已有数据库。初始骨架只实现 V001 空库初始化；B 在首次新增迁移时扩展为按 user_version 顺序执行，旧版本不可覆盖。

## 生命周期与不变量

- 资料 queued → parsing → indexing → ready；失败 failed，重启中断 interrupted；删除 deleting → deleted。同一资料最多一个 queued/running 导入任务，只有 B 更新资料状态。
- 解析后先预留片段容量；ready片段与所有预留合计≤5000，调用向量前检查。C 按批返回向量，B 暂存并更新进度。全部齐备、任务编号仍有效、资料未删除时，在课程锁与短事务下发布 ready，再使缓存失效。失败和中断释放预留，重试清理半成品。
- ready 提交、启用、停用、删除与缓存失效使用同一课程锁。C 在该锁内校验范围并取得两路一致的完整快照，之后释放锁进行计算。旧快照待最后一个租约归还后关闭，不能提前关闭 Lucene reader。
- 停用只影响新问题，原 PDF 可查看。删除先禁用并进入 deleting，取消导入、防止晚到发布，再清理文件、页、片段、向量；失败保持 deleting，重启继续清理。documents 墓碑与 citations 快照保留，原文件可用性按当前状态计算。
- 会话固定范围必须非空；all 创建时展开，selected 必须非空；整个创建与范围保存同事务完成。course/document 组合外键防跨课程，ready/enabled 由应用在课程锁内检查。
- turns 通过部分唯一索引限制一会话一活动问题。终态只允许条件更新一次；同事务写答案和 citations。超时或中断后晚到结果不能覆盖。
- 删除会话级联清理 turns、citations、feedback 和 conversation_documents。删除空课程前，要求无未删除资料、会话和活动任务，再清理该课程资料墓碑与终态导入任务。
- UUID、小写格式、页文本与切片一致、向量有限且非零并归一化、chunk与citation的资料一致、JSON字段结构和终态互斥规则由应用校验。数据库约束不能代替这些业务检查。

## 表和字段

### knowledge_bases

课程资料集合；负责人 B。

| 字段 | SQLite 定义 | 含义 |
| --- | --- | --- |
| id | TEXT PRIMARY KEY NOT NULL | 标准小写 UUID |
| name | TEXT NOT NULL CHECK(length(trim(name)) BETWEEN 1 AND 80) | 课程名 |
| description | TEXT NOT NULL DEFAULT '' CHECK(length(description)<=500) | 课程说明 |
| embedding_model | TEXT NOT NULL DEFAULT 'text-embedding-v4' | 本库向量模型 |
| embedding_dimensions | INTEGER NOT NULL DEFAULT 1024 CHECK(embedding_dimensions=1024) | 固定维度 |
| created_at | TEXT NOT NULL | UTC ISO 8601 |
| updated_at | TEXT NOT NULL | UTC ISO 8601 |

表约束：主键与字段约束见上表。

### documents

原文件与导入状态 保留删除墓碑；负责人 B。

| 字段 | SQLite 定义 | 含义 |
| --- | --- | --- |
| id | TEXT PRIMARY KEY NOT NULL | 标准小写 UUID |
| knowledge_base_id | TEXT NOT NULL REFERENCES knowledge_bases(id) ON DELETE RESTRICT | 所属课程 |
| upload_client_request_id | TEXT NOT NULL | 上传幂等编号 |
| file_name | TEXT NOT NULL CHECK(length(file_name) BETWEEN 1 AND 255) | 原文件展示名 |
| storage_relpath | TEXT NOT NULL UNIQUE | 服务器生成的相对路径 不对外返回 |
| file_size_bytes | INTEGER NOT NULL CHECK(file_size_bytes BETWEEN 1 AND 20971520) | 文件字节数 |
| page_count | INTEGER CHECK(page_count>=0) | 未知为空 超限实测值可保存作失败说明 |
| chunk_count | INTEGER NOT NULL DEFAULT 0 CHECK(chunk_count>=0) | 本次生成片段数 |
| reserved_chunk_count | INTEGER NOT NULL DEFAULT 0 CHECK(reserved_chunk_count>=0) | 导入预留容量 |
| status | TEXT NOT NULL CHECK(status IN ('queued','parsing','indexing','ready','failed','interrupted','deleting','deleted')) | 资料生命周期 |
| enabled | INTEGER NOT NULL DEFAULT 1 CHECK(enabled IN (0,1)) | 是否参与新检索 |
| warnings_json | TEXT NOT NULL DEFAULT '[]' CHECK(json_valid(warnings_json) AND json_type(warnings_json)='array') | DocumentWarning 数组 |
| error_json | TEXT CHECK(error_json IS NULL OR json_valid(error_json)) | ApiError 或空 |
| active_job_id | TEXT REFERENCES import_jobs(id) ON DELETE SET NULL | 活动任务 终态为空 |
| created_at | TEXT NOT NULL | UTC ISO 8601 |
| updated_at | TEXT NOT NULL | UTC ISO 8601 |
| deleted_at | TEXT | 完成清理时间 |

表约束：UNIQUE(knowledge_base_id,upload_client_request_id)；UNIQUE(id,knowledge_base_id)；CHECK(status NOT IN ('deleting','deleted') OR enabled=0)。

### document_pages

页内文字和片段位置基准；负责人 B。

| 字段 | SQLite 定义 | 含义 |
| --- | --- | --- |
| id | TEXT PRIMARY KEY NOT NULL | 标准小写 UUID |
| document_id | TEXT NOT NULL REFERENCES documents(id) ON DELETE CASCADE | 资料编号 |
| page_no | INTEGER NOT NULL CHECK(page_no>=1) | 实际 PDF 页序 |
| text | TEXT NOT NULL | 规范换行后的完整页文本 |
| char_count | INTEGER NOT NULL CHECK(char_count>=0) | Unicode 码点数 |
| extraction_status | TEXT NOT NULL CHECK(extraction_status IN ('text','low_text','empty')) | 提取结果 |

表约束：UNIQUE(document_id,page_no)。

### chunks

页内片段与检索文本；负责人 B。

| 字段 | SQLite 定义 | 含义 |
| --- | --- | --- |
| id | TEXT PRIMARY KEY NOT NULL | 标准小写 UUID |
| document_id | TEXT NOT NULL | 资料编号 |
| ordinal | INTEGER NOT NULL CHECK(ordinal>=0) | 资料内零基顺序 |
| page_no | INTEGER NOT NULL CHECK(page_no>=1) | 实际页序 |
| char_start | INTEGER NOT NULL CHECK(char_start>=0) | 码点起点 包含 |
| char_end | INTEGER NOT NULL CHECK(char_end>char_start) | 码点终点 不包含 |
| text | TEXT NOT NULL CHECK(length(text) BETWEEN 1 AND 1000) | 等于对应页文本切片 |
| search_text | TEXT NOT NULL | 规范化检索文字 |
| created_at | TEXT NOT NULL | UTC ISO 8601 |

表约束：UNIQUE(document_id,ordinal)；UNIQUE(id,document_id)；FOREIGN KEY(document_id,page_no) REFERENCES document_pages(document_id,page_no) ON DELETE CASCADE。

### chunk_embeddings

片段向量；负责人 B 持久化 C 生成与读取。

| 字段 | SQLite 定义 | 含义 |
| --- | --- | --- |
| chunk_id | TEXT PRIMARY KEY NOT NULL REFERENCES chunks(id) ON DELETE CASCADE | 片段编号 一片段一向量 |
| model | TEXT NOT NULL | 与课程配置一致 |
| dimensions | INTEGER NOT NULL CHECK(dimensions=1024) | 维度 |
| vector | BLOB NOT NULL CHECK(length(vector)=4096) | 1024 个小端 float32 已 L2 归一化 |
| created_at | TEXT NOT NULL | UTC ISO 8601 |

表约束：主键与字段约束见上表。

### import_jobs

持久化导入尝试；负责人 B。

| 字段 | SQLite 定义 | 含义 |
| --- | --- | --- |
| id | TEXT PRIMARY KEY NOT NULL | 标准小写 UUID |
| document_id | TEXT NOT NULL REFERENCES documents(id) ON DELETE CASCADE | 所属资料 |
| status | TEXT NOT NULL CHECK(status IN ('queued','running','completed','failed','interrupted','cancelled')) | 任务状态 |
| stage | TEXT NOT NULL CHECK(stage IN ('queued','parsing','indexing','publishing','finished')) | 处理阶段 |
| pages_processed | INTEGER NOT NULL DEFAULT 0 CHECK(pages_processed>=0) | 已处理页数 |
| pages_total | INTEGER CHECK(pages_total>=0) | 总页数 未知为空 |
| chunks_embedded | INTEGER NOT NULL DEFAULT 0 CHECK(chunks_embedded>=0) | 已完成向量数 |
| chunks_total | INTEGER CHECK(chunks_total>=0) | 总片段数 |
| error_json | TEXT CHECK(error_json IS NULL OR json_valid(error_json)) | ApiError 或空 |
| created_at | TEXT NOT NULL | UTC ISO 8601 |
| started_at | TEXT | 开始时间 |
| finished_at | TEXT | 结束时间 |

表约束：主键与字段约束见上表。

### conversations

固定资料范围的会话；负责人 D。

| 字段 | SQLite 定义 | 含义 |
| --- | --- | --- |
| id | TEXT PRIMARY KEY NOT NULL | 标准小写 UUID |
| knowledge_base_id | TEXT NOT NULL REFERENCES knowledge_bases(id) ON DELETE RESTRICT | 所属课程 |
| title | TEXT NOT NULL CHECK(length(trim(title)) BETWEEN 1 AND 80) | 标题 |
| scope | TEXT NOT NULL CHECK(scope IN ('all','selected')) | 创建时选择方式 |
| created_at | TEXT NOT NULL | UTC ISO 8601 |
| updated_at | TEXT NOT NULL | UTC ISO 8601 |

表约束：UNIQUE(id,knowledge_base_id)。

### conversation_documents

会话资料范围 以关联表约束课程归属；负责人 D。

| 字段 | SQLite 定义 | 含义 |
| --- | --- | --- |
| conversation_id | TEXT NOT NULL | 会话编号 |
| document_id | TEXT NOT NULL | 具体资料编号 删除后仍保留墓碑关联 |
| knowledge_base_id | TEXT NOT NULL | 用于组合外键校验同一课程 |

表约束：PRIMARY KEY(conversation_id,document_id)；FOREIGN KEY(conversation_id,knowledge_base_id) REFERENCES conversations(id,knowledge_base_id) ON DELETE CASCADE；FOREIGN KEY(document_id,knowledge_base_id) REFERENCES documents(id,knowledge_base_id) ON DELETE RESTRICT。

### turns

一次异步问答及其证据快照；负责人 D。

| 字段 | SQLite 定义 | 含义 |
| --- | --- | --- |
| id | TEXT PRIMARY KEY NOT NULL | 标准小写 UUID |
| conversation_id | TEXT NOT NULL REFERENCES conversations(id) ON DELETE CASCADE | 所属会话 |
| client_request_id | TEXT NOT NULL | 提交幂等编号 |
| retry_of_turn_id | TEXT REFERENCES turns(id) ON DELETE SET NULL | 主动重试的原问答 |
| question | TEXT NOT NULL CHECK(length(trim(question)) BETWEEN 1 AND 2000) | 原问题 |
| standalone_question | TEXT CHECK(standalone_question IS NULL OR length(standalone_question) BETWEEN 1 AND 2000) | 独立检索问题 |
| strategy | TEXT NOT NULL CHECK(strategy IN ('bm25','dense','hybrid')) | 检索策略 |
| top_k | INTEGER NOT NULL CHECK(top_k BETWEEN 1 AND 10) | 最终片段数 |
| scope_document_ids_json | TEXT NOT NULL CHECK(json_valid(scope_document_ids_json) AND json_type(scope_document_ids_json)='array' AND json_array_length(scope_document_ids_json)>0) | 本轮范围不可变副本 |
| rewrite_history_json | TEXT NOT NULL CHECK(json_valid(rewrite_history_json) AND json_type(rewrite_history_json)='array') | 改写所用历史 完整轮数和总字符数由应用限制 |
| status | TEXT NOT NULL CHECK(status IN ('queued','rewriting','retrieving','generating','validating','answered','insufficient_evidence','needs_clarification','failed','interrupted')) | 问答状态 |
| answer_blocks_json | TEXT NOT NULL CHECK(json_valid(answer_blocks_json) AND json_type(answer_blocks_json)='array') | AnswerBlock 数组 |
| message | TEXT | 不足或澄清提示 |
| error_json | TEXT CHECK(error_json IS NULL OR json_valid(error_json)) | ApiError |
| retrieval_snapshot_json | TEXT CHECK(retrieval_snapshot_json IS NULL OR json_valid(retrieval_snapshot_json)) | SearchResult 不可变快照 |
| timings_json | TEXT NOT NULL CHECK(json_valid(timings_json)) | Timings |
| token_usage_json | TEXT NOT NULL CHECK(json_valid(token_usage_json)) | TokenUsage |
| model_settings_json | TEXT NOT NULL CHECK(json_valid(model_settings_json)) | ModelSettings |
| created_at | TEXT NOT NULL | UTC ISO 8601 |
| updated_at | TEXT NOT NULL | UTC ISO 8601 |
| finished_at | TEXT | 终态时间 |

表约束：UNIQUE(conversation_id,client_request_id)；CHECK(status<>'answered' OR json_array_length(answer_blocks_json)>0)；CHECK(status NOT IN ('insufficient_evidence','needs_clarification','failed','interrupted') OR json_array_length(answer_blocks_json)=0)。

### citations

引用内容快照 保留可追溯历史；负责人 D。

| 字段 | SQLite 定义 | 含义 |
| --- | --- | --- |
| id | TEXT PRIMARY KEY NOT NULL | 标准小写 UUID |
| turn_id | TEXT NOT NULL REFERENCES turns(id) ON DELETE CASCADE | 问答编号 |
| citation_id | INTEGER NOT NULL CHECK(citation_id>=1) | 本轮显示编号 |
| document_id | TEXT NOT NULL REFERENCES documents(id) ON DELETE RESTRICT | 保留资料墓碑 |
| chunk_id | TEXT REFERENCES chunks(id) ON DELETE SET NULL | 片段清理后为空 |
| file_name | TEXT NOT NULL | 回答时文件名 |
| page_no | INTEGER NOT NULL CHECK(page_no>=1) | 回答时实际页序 |
| excerpt | TEXT NOT NULL CHECK(length(excerpt) BETWEEN 1 AND 1000) | 完整原片段快照 |

表约束：UNIQUE(turn_id,citation_id)。

### feedback

一个回答一份可修改反馈；负责人 D。

| 字段 | SQLite 定义 | 含义 |
| --- | --- | --- |
| id | TEXT PRIMARY KEY NOT NULL | 标准小写 UUID |
| turn_id | TEXT NOT NULL UNIQUE REFERENCES turns(id) ON DELETE CASCADE | 被反馈问答 |
| rating | TEXT NOT NULL CHECK(rating IN ('up','down')) | 反馈类型 |
| reason | TEXT CHECK(reason IS NULL OR reason IN ('helpful','incorrect','unsupported','missing_information','other')) | 原因 |
| comment | TEXT NOT NULL DEFAULT '' CHECK(length(comment)<=1000) | 补充文字 |
| created_at | TEXT NOT NULL | UTC ISO 8601 |
| updated_at | TEXT NOT NULL | UTC ISO 8601 |

表约束：主键与字段约束见上表。

## 索引

```sql
CREATE INDEX ix_documents_scope ON documents(knowledge_base_id,status,enabled);
CREATE INDEX ix_documents_created ON documents(knowledge_base_id,created_at,id);
CREATE INDEX ix_chunks_page ON chunks(document_id,page_no,ordinal);
CREATE INDEX ix_import_queue ON import_jobs(status,created_at,id);
CREATE UNIQUE INDEX ux_import_active ON import_jobs(document_id) WHERE status IN ('queued','running');
CREATE INDEX ix_conversations_recent ON conversations(knowledge_base_id,updated_at,id);
CREATE INDEX ix_turns_history ON turns(conversation_id,created_at,id);
CREATE UNIQUE INDEX ux_turn_active ON turns(conversation_id) WHERE status IN ('queued','rewriting','retrieving','generating','validating');
CREATE INDEX ix_citations_document ON citations(document_id);
```
