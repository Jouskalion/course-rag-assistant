# HTTP 接口和交换数据

前缀 `/api/v1`。HTTP 请求与返回均为 UTF-8。未列出的字段拒绝；请求省略可选字段时采用默认值，显式 null 只有声明允许时有效。返回对象的所有声明字段均出现，未知值为 null，列表无内容时为 []。Java 内部 camelCase，公开 JSON snake_case。所有字符限制按 Unicode 码点。

列表默认 page=1、page_size=20，最大100。资料、课程默认 created_at 降序、id 升序；会话 updated_at 降序、id 升序；问答历史 created_at 升序、id 升序；片段 ordinal 升序。

## 接口索引

| 方法与路径 | operationId | 主责 | 输入 | 成功响应 |
| --- | --- | --- | --- | --- |
| GET /health | getHealth | D | 无请求体 | 200 Health |
| GET /knowledge-bases | listKnowledgeBases | B | 无请求体；page, page_size | 200 KnowledgeBaseList |
| POST /knowledge-bases | createKnowledgeBase | B | KnowledgeBaseCreate | 201 KnowledgeBase |
| GET /knowledge-bases/{knowledge_base_id} | getKnowledgeBase | B | 无请求体；knowledge_base_id 必填 | 200 KnowledgeBase |
| PATCH /knowledge-bases/{knowledge_base_id} | updateKnowledgeBase | B | KnowledgeBaseUpdate；knowledge_base_id 必填 | 200 KnowledgeBase |
| DELETE /knowledge-bases/{knowledge_base_id} | deleteKnowledgeBase | B | 无请求体；knowledge_base_id 必填 | 204 无响应体 |
| GET /knowledge-bases/{knowledge_base_id}/documents | listDocuments | B | 无请求体；knowledge_base_id 必填, page, page_size | 200 DocumentList |
| POST /knowledge-bases/{knowledge_base_id}/documents | uploadDocument | B | multipart file + client_request_id；knowledge_base_id 必填 | 202 ImportAccepted；200 ImportAccepted |
| GET /documents/{document_id} | getDocument | B | 无请求体；document_id 必填 | 200 Document |
| PATCH /documents/{document_id} | updateDocument | B | DocumentUpdate；document_id 必填 | 200 Document |
| DELETE /documents/{document_id} | deleteDocument | B | 无请求体；document_id 必填 | 202 Document；200 Document |
| GET /documents/{document_id}/chunks | listChunks | B | 无请求体；document_id 必填, page, page_size | 200 ChunkList |
| GET /documents/{document_id}/pages/{page_no} | getDocumentPage | B | 无请求体；document_id 必填, page_no 必填 | 200 DocumentPage |
| GET /documents/{document_id}/file | getDocumentFile | B | 无请求体；document_id 必填 | 200 string binary |
| POST /documents/{document_id}/retry | retryDocument | B | 无请求体；document_id 必填 | 202 Document；200 Document |
| GET /import-jobs/{job_id} | getImportJob | B | 无请求体；job_id 必填 | 200 ImportJob |
| GET /conversations | listConversations | D | 无请求体；page, page_size, knowledge_base_id 必填 | 200 ConversationList |
| POST /conversations | createConversation | D | ConversationCreate | 201 Conversation |
| GET /conversations/{conversation_id} | getConversation | D | 无请求体；conversation_id 必填 | 200 Conversation |
| PATCH /conversations/{conversation_id} | updateConversation | D | ConversationUpdate；conversation_id 必填 | 200 Conversation |
| DELETE /conversations/{conversation_id} | deleteConversation | D | 无请求体；conversation_id 必填 | 204 无响应体 |
| GET /conversations/{conversation_id}/turns | listTurns | D | 无请求体；conversation_id 必填, page, page_size | 200 TurnList |
| POST /conversations/{conversation_id}/turns | createTurn | D | TurnCreate；conversation_id 必填 | 202 Turn；200 Turn |
| GET /turns/{turn_id} | getTurn | D | 无请求体；turn_id 必填 | 200 Turn |
| POST /turns/{turn_id}/retry | retryTurn | D | TurnRetry；turn_id 必填 | 202 Turn；200 Turn |
| PUT /turns/{turn_id}/feedback | putFeedback | D | FeedbackWrite；turn_id 必填 | 200 Feedback |
| POST /retrieval/search | search | C | SearchRequest | 200 SearchResult |

## 业务语义

上传接收单个文件。文件内容保存完且任务已落库才返回 202；同一课程和上传编号重传，相同文件名与字节返回 200 原记录，不同内容返回 409。原文件已删除不能复用该编号。
创建会话：scope=all 不传 document_ids，后端展开当前 ready 且 enabled 的资料；scope=selected 必传非空集合。创建后集合不可变，新增资料不自动加入。任一原资料失效时，下一次提问返回 SOURCE_UNAVAILABLE；先前取得证据的在途问答可用副本完成。
资料删除返回当前 Document；deleting 状态先退出检索再清理。重试接口对 failed/interrupted 重新导入，对 deleting 继续清理，均返回 Document。已有任务时复用 active_job_id，不能创建重复活动任务。已删除返回 200，ready 或不允许状态返回 409。
提问先校验会话归属、范围、请求幂等及活动问题。相同 client_request_id、问题、策略、top_k 返回原 Turn；内容不同 409。用户主动重试使用新的 client_request_id，复制原问题、策略、top_k 和改写历史，写 retry_of_turn_id；同一重试请求重放返回同一新记录。
反馈仅允许 answered 问答；PUT 覆盖同一条共享反馈，无独立用户统计。删除会话时级联清理问答、引用与反馈，活动问答未结束时不能删除。
问答创建阶段校验失败返回 HTTP 错误；轮询到 failed/interrupted 仍是 HTTP 200 的 Turn，详情读取 turn.error。answered 的 answer_blocks 与 citations 非空，每个事实段至少一个合法引用；其他终态回答段落与引用数组为空。依据不足/澄清使用 message，技术失败/中断使用 error。
C 调试与 D 问答采用同一 SearchResult，包括查询向量化实际用量。未执行向量化时终态用量为0，未知为空；分数不解释为答案概率。source_id 仅当前请求有效，citation_id 为回答内整数。
PDF 文件接口只承诺完整文件 HTTP 200，A 配置 PDF.js disableRange=true、disableStream=true；本版不实现 Range。文件通过资料编号映射，禁止客户端传磁盘路径。
本机工作台默认只监听127.0.0.1，不提供认证。JSON 与文件上传的错误都使用 ErrorEnvelope。

## 数据结构

### FieldError

字段错误；负责人 D。

| 字段 | 类型 | 必填 | 约束与含义 |
| --- | --- | --- | --- |
| field | string | 是 | 字段路径 |
| message | string | 是 | 原因 |

### ApiError

错误主体；负责人 D。

| 字段 | 类型 | 必填 | 约束与含义 |
| --- | --- | --- | --- |
| code | string | 是 | 固定错误码；enum=["INVALID_REQUEST", "NOT_FOUND", "METHOD_NOT_ALLOWED", "UNSUPPORTED_MEDIA_TYPE", "KNOWLEDGE_BASE_NOT_EMPTY", "EMPTY_SCOPE", "SCOPE_MISMATCH", "SOURCE_UNAVAILABLE", "FILE_TOO_LARGE", "INVALID_PDF", "DOCUMENT_LIMIT_EXCEEDED", "DOCUMENT_NOT_READY", "IDEMPOTENCY_CONFLICT", "CONVERSATION_BUSY", "IMPORT_NOT_RETRYABLE", "TURN_NOT_RETRYABLE", "MODEL_NOT_CONFIGURED", "EMBEDDING_MODEL_MISMATCH", "PDF_TOO_MANY_PAGES", "PDF_ENCRYPTED", "PDF_NO_TEXT", "PDF_PARSE_FAILED", "CHUNK_LIMIT_EXCEEDED", "EMBEDDING_FAILED", "MODEL_AUTH_FAILED", "MODEL_RATE_LIMITED", "MODEL_TIMEOUT", "MODEL_UNAVAILABLE", "MODEL_OUTPUT_INVALID", "CITATION_INVALID", "TASK_INTERRUPTED", "DELETE_FAILED", "FEATURE_NOT_IMPLEMENTED", "INTERNAL_ERROR"] |
| message | string | 是 | 面向用户的说明 |
| retryable | boolean | 是 | 是否允许主动重试 |
| details | 数组 FieldError | 是 | 字段错误，没有时为空数组 |

### ErrorEnvelope

HTTP 请求失败；负责人 D。

| 字段 | 类型 | 必填 | 约束与含义 |
| --- | --- | --- | --- |
| error | ApiError | 是 |  |
| request_id | string uuid | 是 | 本次 HTTP 请求编号 |

### Health

启动状态；负责人 D。

| 字段 | 类型 | 必填 | 约束与含义 |
| --- | --- | --- | --- |
| status | string | 是 | enum=["ok"] |
| version | string | 是 | 工程版本 |
| application_stage | string | 是 | enum=["skeleton", "application"] |
| database_ready | boolean | 是 |  |
| model_configured | boolean | 是 | Base URL 与密钥均非空 |

### KnowledgeBaseCreate

新建课程；负责人 B。

| 字段 | 类型 | 必填 | 约束与含义 |
| --- | --- | --- | --- |
| name | string | 是 | 去掉首尾空白后 1 至 80 码点；minLength=1；maxLength=80 |
| description | string | 否 | 说明；default=""；maxLength=500 |

### KnowledgeBaseUpdate

修改课程 使用完整字段更新；负责人 B。

| 字段 | 类型 | 必填 | 约束与含义 |
| --- | --- | --- | --- |
| name | string | 是 | 名称；minLength=1；maxLength=80 |
| description | string | 是 | 说明；maxLength=500 |

### KnowledgeBase

课程；负责人 B。

| 字段 | 类型 | 必填 | 约束与含义 |
| --- | --- | --- | --- |
| id | string uuid | 是 | 业务编号 |
| name | string | 是 |  |
| description | string | 是 |  |
| embedding_model | string | 是 |  |
| embedding_dimensions | integer | 是 |  |
| document_count | integer | 是 | 未删除资料数 |
| ready_document_count | integer | 是 | ready 且 enabled 的资料数 |
| ready_chunk_count | integer | 是 | 所有 ready 资料片段数 包括已停用 |
| reserved_chunk_count | integer | 是 | 导入预留片段数 |
| created_at | string date-time | 是 | UTC ISO 8601 时间 |
| updated_at | string date-time | 是 | UTC ISO 8601 时间 |

### DocumentWarning

解析提示；负责人 B。

| 字段 | 类型 | 必填 | 约束与含义 |
| --- | --- | --- | --- |
| code | string | 是 | enum=["EMPTY_PAGE", "LOW_TEXT"] |
| page_no | integer | 是 | PDF 实际页序；minimum=1 |
| message | string | 是 |  |

### Document

课程资料；负责人 B。

| 字段 | 类型 | 必填 | 约束与含义 |
| --- | --- | --- | --- |
| id | string uuid | 是 | 业务编号 |
| knowledge_base_id | string uuid | 是 | 业务编号 |
| file_name | string | 是 |  |
| file_size_bytes | integer | 是 |  |
| page_count | integer 或 null | 是 |  |
| chunk_count | integer | 是 |  |
| status | string | 是 | enum=["queued", "parsing", "indexing", "ready", "failed", "interrupted", "deleting", "deleted"] |
| enabled | boolean | 是 |  |
| warnings | 数组 DocumentWarning | 是 |  |
| error | ApiError 或 null | 是 |  |
| active_job_id | string uuid 或 null | 是 | 业务编号 |
| created_at | string date-time | 是 | UTC ISO 8601 时间 |
| updated_at | string date-time | 是 | UTC ISO 8601 时间 |
| deleted_at | string date-time 或 null | 是 | UTC ISO 8601 时间 |

### DocumentUpdate

启用或停用；负责人 B。

| 字段 | 类型 | 必填 | 约束与含义 |
| --- | --- | --- | --- |
| enabled | boolean | 是 |  |

### ImportJob

导入进度；负责人 B。

| 字段 | 类型 | 必填 | 约束与含义 |
| --- | --- | --- | --- |
| id | string uuid | 是 | 业务编号 |
| document_id | string uuid | 是 | 业务编号 |
| status | string | 是 | enum=["queued", "running", "completed", "failed", "interrupted", "cancelled"] |
| stage | string | 是 | enum=["queued", "parsing", "indexing", "publishing", "finished"] |
| pages_processed | integer | 是 |  |
| pages_total | integer 或 null | 是 |  |
| chunks_embedded | integer | 是 |  |
| chunks_total | integer 或 null | 是 |  |
| error | ApiError 或 null | 是 |  |
| created_at | string date-time | 是 | UTC ISO 8601 时间 |
| started_at | string date-time 或 null | 是 | UTC ISO 8601 时间 |
| finished_at | string date-time 或 null | 是 | UTC ISO 8601 时间 |

### ImportAccepted

上传或重试响应；负责人 B。

| 字段 | 类型 | 必填 | 约束与含义 |
| --- | --- | --- | --- |
| document | Document | 是 |  |
| job | ImportJob | 是 |  |

### DocumentPage

单页提取文字；负责人 B。

| 字段 | 类型 | 必填 | 约束与含义 |
| --- | --- | --- | --- |
| document_id | string uuid | 是 | 业务编号 |
| page_no | integer | 是 | minimum=1 |
| text | string | 是 |  |
| char_count | integer | 是 |  |
| extraction_status | string | 是 | enum=["text", "low_text", "empty"] |

### Chunk

文本片段；负责人 B。

| 字段 | 类型 | 必填 | 约束与含义 |
| --- | --- | --- | --- |
| id | string uuid | 是 | 业务编号 |
| document_id | string uuid | 是 | 业务编号 |
| knowledge_base_id | string uuid | 是 | 业务编号 |
| ordinal | integer | 是 | 资料内从 0 开始 |
| page_no | integer | 是 | minimum=1 |
| char_start | integer | 是 | 码点偏移 左闭；minimum=0 |
| char_end | integer | 是 | 码点偏移 右开；minimum=1 |
| text | string | 是 | 精确对应页文本切片；maxLength=1000 |

### ConversationCreate

创建固定范围会话 scope=all 时不传 document_ids selected 时必须非空；负责人 D。

| 字段 | 类型 | 必填 | 约束与含义 |
| --- | --- | --- | --- |
| knowledge_base_id | string uuid | 是 | 业务编号 |
| scope | string | 是 | enum=["all", "selected"] |
| document_ids | 数组 string uuid | 否 | minItems=1；maxItems=100；uniqueItems=true |
| title | string | 否 | 不传时固定为 新会话；default="新会话"；minLength=1；maxLength=80 |

### ConversationUpdate

修改会话标题；负责人 D。

| 字段 | 类型 | 必填 | 约束与含义 |
| --- | --- | --- | --- |
| title | string | 是 | minLength=1；maxLength=80 |

### Conversation

会话及固定范围；负责人 D。

| 字段 | 类型 | 必填 | 约束与含义 |
| --- | --- | --- | --- |
| id | string uuid | 是 | 业务编号 |
| knowledge_base_id | string uuid | 是 | 业务编号 |
| title | string | 是 |  |
| scope | string | 是 | enum=["all", "selected"] |
| document_ids | 数组 string uuid | 是 | minItems=1 |
| unavailable_document_ids | 数组 string uuid | 是 |  |
| active_turn_id | string uuid 或 null | 是 | 业务编号 |
| created_at | string date-time | 是 | UTC ISO 8601 时间 |
| updated_at | string date-time | 是 | UTC ISO 8601 时间 |

### TurnCreate

提交问题；负责人 D。

| 字段 | 类型 | 必填 | 约束与含义 |
| --- | --- | --- | --- |
| client_request_id | string uuid | 是 | 客户端重传必须复用 |
| question | string | 是 | 去除首尾空白后的问题；minLength=1；maxLength=2000 |
| strategy | string | 否 | 检索策略；enum=["bm25", "dense", "hybrid"]；default="hybrid" |
| top_k | integer | 否 | 最终片段数；default=6；minimum=1；maximum=10 |

### TurnRetry

主动重试 必须使用新的请求编号；负责人 D。

| 字段 | 类型 | 必填 | 约束与含义 |
| --- | --- | --- | --- |
| client_request_id | string uuid | 是 | 业务编号 |

### AnswerBlock

回答段落；负责人 D。

| 字段 | 类型 | 必填 | 约束与含义 |
| --- | --- | --- | --- |
| text | string | 是 | minLength=1 |
| citation_ids | 数组 integer | 是 | minItems=1；uniqueItems=true |

### Citation

真实引用快照；负责人 D。

| 字段 | 类型 | 必填 | 约束与含义 |
| --- | --- | --- | --- |
| citation_id | integer | 是 | 本轮显示编号 从 1 开始；minimum=1 |
| document_id | string uuid | 是 | 业务编号 |
| chunk_id | string uuid 或 null | 是 | 业务编号 |
| file_name | string | 是 |  |
| page_no | integer | 是 | minimum=1 |
| excerpt | string | 是 | 完整原片段；maxLength=1000 |
| source_available | boolean | 是 | 原 PDF 当前能否打开 |

### Timings

阶段耗时 毫秒 未知为 null；负责人 D。

| 字段 | 类型 | 必填 | 约束与含义 |
| --- | --- | --- | --- |
| queue_ms | number 或 null | 是 | minimum=0 |
| rewrite_ms | number 或 null | 是 | minimum=0 |
| query_embedding_ms | number 或 null | 是 | minimum=0 |
| retrieval_ms | number 或 null | 是 | minimum=0 |
| generation_ms | number 或 null | 是 | minimum=0 |
| validation_ms | number 或 null | 是 | minimum=0 |
| total_ms | number 或 null | 是 | minimum=0 |

### TokenUsage

实际用量 未知为空 未调用的阶段终态记 0；负责人 D。

| 字段 | 类型 | 必填 | 约束与含义 |
| --- | --- | --- | --- |
| rewrite_input_tokens | integer 或 null | 是 | minimum=0 |
| rewrite_output_tokens | integer 或 null | 是 | minimum=0 |
| query_embedding_input_tokens | integer 或 null | 是 | minimum=0 |
| generation_input_tokens | integer 或 null | 是 | minimum=0 |
| generation_output_tokens | integer 或 null | 是 | minimum=0 |
| total_tokens | integer 或 null | 是 | minimum=0 |

### ModelSettings

模型配置与真实返回名称；负责人 D。

| 字段 | 类型 | 必填 | 约束与含义 |
| --- | --- | --- | --- |
| generation_model | string | 是 |  |
| actual_generation_model | string 或 null | 是 |  |
| embedding_model | string | 是 |  |
| actual_embedding_model | string 或 null | 是 |  |
| embedding_dimensions | integer | 是 |  |
| temperature | number | 是 |  |
| max_output_tokens | integer | 是 |  |
| enable_thinking | boolean | 是 |  |

### FeedbackWrite

每个回答一条可修改的共享反馈；负责人 D。

| 字段 | 类型 | 必填 | 约束与含义 |
| --- | --- | --- | --- |
| rating | string | 是 | enum=["up", "down"] |
| reason | string 或 null | 是 | enum=["helpful", "incorrect", "unsupported", "missing_information", "other"] |
| comment | string | 是 | maxLength=1000 |

### Feedback

已保存反馈；负责人 D。

| 字段 | 类型 | 必填 | 约束与含义 |
| --- | --- | --- | --- |
| id | string uuid | 是 | 业务编号 |
| turn_id | string uuid | 是 | 业务编号 |
| rating | string | 是 | enum=["up", "down"] |
| reason | string 或 null | 是 | enum=["helpful", "incorrect", "unsupported", "missing_information", "other"] |
| comment | string | 是 |  |
| created_at | string date-time | 是 | UTC ISO 8601 时间 |
| updated_at | string date-time | 是 | UTC ISO 8601 时间 |

### SearchHit

来源与三种分数 缺失的分数为空；负责人 C。

| 字段 | 类型 | 必填 | 约束与含义 |
| --- | --- | --- | --- |
| source_id | string | 是 | 仅本次请求有效 S1 S2 等；pattern="^S[1-9][0-9]*$" |
| chunk_id | string uuid | 是 | 业务编号 |
| document_id | string uuid | 是 | 业务编号 |
| file_name | string | 是 |  |
| page_no | integer | 是 | minimum=1 |
| char_start | integer | 是 |  |
| char_end | integer | 是 |  |
| text | string | 是 | maxLength=1000 |
| bm25_rank | integer 或 null | 是 | minimum=1 |
| dense_rank | integer 或 null | 是 | minimum=1 |
| bm25_score | number 或 null | 是 |  |
| dense_score | number 或 null | 是 | minimum=-1；maximum=1 |
| rrf_score | number 或 null | 是 |  |

### SearchRequest

检索调试 固定传入具体资料集合；负责人 C。

| 字段 | 类型 | 必填 | 约束与含义 |
| --- | --- | --- | --- |
| knowledge_base_id | string uuid | 是 | 业务编号 |
| document_ids | 数组 string uuid | 是 | minItems=1；maxItems=100；uniqueItems=true |
| query | string | 是 | minLength=1；maxLength=2000 |
| strategy | string | 是 | 检索策略；enum=["bm25", "dense", "hybrid"] |
| top_k | integer | 否 | default=6；minimum=1；maximum=10 |

### SearchResult

同一检索服务供调试与问答使用；负责人 C。

| 字段 | 类型 | 必填 | 约束与含义 |
| --- | --- | --- | --- |
| query | string | 是 |  |
| strategy | string | 是 | 检索策略；enum=["bm25", "dense", "hybrid"] |
| document_ids | 数组 string uuid | 是 |  |
| top_k | integer | 是 |  |
| hits | 数组 SearchHit | 是 |  |
| query_embedding_ms | number 或 null | 是 |  |
| retrieval_ms | number | 是 |  |
| query_embedding_input_tokens | integer 或 null | 是 |  |
| actual_embedding_model | string 或 null | 是 |  |

### Turn

一次异步问答 answered 时段落和引用非空 其他终态段落为空；负责人 D。

| 字段 | 类型 | 必填 | 约束与含义 |
| --- | --- | --- | --- |
| id | string uuid | 是 | 业务编号 |
| conversation_id | string uuid | 是 | 业务编号 |
| client_request_id | string uuid | 是 | 业务编号 |
| retry_of_turn_id | string uuid 或 null | 是 | 业务编号 |
| question | string | 是 |  |
| standalone_question | string 或 null | 是 |  |
| strategy | string | 是 | 检索策略；enum=["bm25", "dense", "hybrid"] |
| top_k | integer | 是 |  |
| status | string | 是 | enum=["queued", "rewriting", "retrieving", "generating", "validating", "answered", "insufficient_evidence", "needs_clarification", "failed", "interrupted"] |
| answer_blocks | 数组 AnswerBlock | 是 |  |
| citations | 数组 Citation | 是 |  |
| message | string 或 null | 是 |  |
| error | ApiError 或 null | 是 |  |
| retrieval | SearchResult 或 null | 是 |  |
| timings | Timings | 是 |  |
| token_usage | TokenUsage | 是 |  |
| model_settings | ModelSettings | 是 |  |
| feedback | Feedback 或 null | 是 |  |
| created_at | string date-time | 是 | UTC ISO 8601 时间 |
| updated_at | string date-time | 是 | UTC ISO 8601 时间 |
| finished_at | string date-time 或 null | 是 | UTC ISO 8601 时间 |

### KnowledgeBaseList

分页列表；负责人 B。

| 字段 | 类型 | 必填 | 约束与含义 |
| --- | --- | --- | --- |
| items | 数组 KnowledgeBase | 是 |  |
| page | integer | 是 | minimum=1 |
| page_size | integer | 是 | minimum=1；maximum=100 |
| total | integer | 是 | minimum=0 |

### DocumentList

分页列表；负责人 B。

| 字段 | 类型 | 必填 | 约束与含义 |
| --- | --- | --- | --- |
| items | 数组 Document | 是 |  |
| page | integer | 是 | minimum=1 |
| page_size | integer | 是 | minimum=1；maximum=100 |
| total | integer | 是 | minimum=0 |

### ChunkList

分页列表；负责人 B。

| 字段 | 类型 | 必填 | 约束与含义 |
| --- | --- | --- | --- |
| items | 数组 Chunk | 是 |  |
| page | integer | 是 | minimum=1 |
| page_size | integer | 是 | minimum=1；maximum=100 |
| total | integer | 是 | minimum=0 |

### ConversationList

分页列表；负责人 D。

| 字段 | 类型 | 必填 | 约束与含义 |
| --- | --- | --- | --- |
| items | 数组 Conversation | 是 |  |
| page | integer | 是 | minimum=1 |
| page_size | integer | 是 | minimum=1；maximum=100 |
| total | integer | 是 | minimum=0 |

### TurnList

分页列表；负责人 D。

| 字段 | 类型 | 必填 | 约束与含义 |
| --- | --- | --- | --- |
| items | 数组 Turn | 是 |  |
| page | integer | 是 | minimum=1 |
| page_size | integer | 是 | minimum=1；maximum=100 |
| total | integer | 是 | minimum=0 |

## 错误码

资源错误保存在 Document.error 或 Turn.error，查询资源仍返回 HTTP 200。调用方不得将技术失败显示为资料不足。

| code | HTTP 或位置 | 可主动重试 | 含义 |
| --- | --- | --- | --- |
| INVALID_REQUEST | 422 | 否 | 字段、JSON、枚举、长度、格式错误 |
| NOT_FOUND | 404 | 否 | 编号或路径不存在 |
| METHOD_NOT_ALLOWED | 405 | 否 | HTTP 方法不支持 |
| UNSUPPORTED_MEDIA_TYPE | 415 | 否 | 请求媒体类型不支持 |
| KNOWLEDGE_BASE_NOT_EMPTY | 409 | 否 | 课程仍有未删除资料、会话或活动任务 |
| EMPTY_SCOPE | 422 | 否 | 资料范围为空 |
| SCOPE_MISMATCH | 422 | 否 | 资料跨课程 |
| SOURCE_UNAVAILABLE | 409 | 否 | 固定范围中存在已删除、停用或未就绪资料 |
| FILE_TOO_LARGE | 413 | 否 | PDF 超过 20 MiB |
| INVALID_PDF | 415 | 否 | 文件类型或文件头不正确 |
| DOCUMENT_LIMIT_EXCEEDED | 409 | 否 | 未删除资料已达 100 份 |
| DOCUMENT_NOT_READY | 409 | 否 | 不能启停未就绪资料 |
| IDEMPOTENCY_CONFLICT | 409 | 否 | 同一请求编号对应不同内容 |
| CONVERSATION_BUSY | 409 | 否 | 会话有活动问答，不能再提交或删除 |
| IMPORT_NOT_RETRYABLE | 409 | 否 | 资料状态不允许重试 |
| TURN_NOT_RETRYABLE | 409 | 否 | 只有 failed 或 interrupted 可重试 |
| MODEL_NOT_CONFIGURED | 503 | 否 | 模型地址或密钥缺失，需先配置 |
| EMBEDDING_MODEL_MISMATCH | 409 或资源 | 否 | 课程、片段或问题向量模型维度不一致 |
| PDF_TOO_MANY_PAGES | 资料资源 | 否 | 超过 200 页 |
| PDF_ENCRYPTED | 资料资源 | 否 | 加密或禁止提取，本版不接收密码 |
| PDF_NO_TEXT | 资料资源 | 否 | 没有可用文字 |
| PDF_PARSE_FAILED | 资料资源 | 否 | PDF 解析失败 |
| CHUNK_LIMIT_EXCEEDED | 资料资源 | 否 | 就绪加预留片段超过 5000 |
| EMBEDDING_FAILED | 资料或问答资源 | 是 | 向量返回错误或受限重试后仍失败 |
| MODEL_AUTH_FAILED | 问答资源 | 否 | 鉴权或账户问题，修正配置后再重试 |
| MODEL_RATE_LIMITED | 问答资源 | 是 | 生成或改写限流 |
| MODEL_TIMEOUT | 问答资源 | 是 | 执行总期限已到 |
| MODEL_UNAVAILABLE | 问答资源 | 是 | 网络或供应商服务异常 |
| MODEL_OUTPUT_INVALID | 问答资源 | 是 | 一次修复后结构仍不合法 |
| CITATION_INVALID | 问答资源 | 是 | 一次修复后引用仍不合法 |
| TASK_INTERRUPTED | 资料或问答资源 | 是 | 进程重启造成中断 |
| DELETE_FAILED | 资料资源 | 是 | 文件或衍生数据清理失败 |
| FEATURE_NOT_IMPLEMENTED | 501 | 否 | 启动骨架的明确未实现状态 |
| INTERNAL_ERROR | 500 | 否 | 未预期的内部异常 |
