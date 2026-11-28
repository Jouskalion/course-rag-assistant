# 内部接口与类型

定义位于 backend/src/main/java/com/courseqa/contracts。实现类由各成员完成并通过构造器注入；调用方依赖接口。当前实现壳抛出 FEATURE_NOT_IMPLEMENTED，不伪造数据。

Java UUID、Instant、List 等类型只在后端内部使用；HTTP DTO 由统一契约生成。公共 record 的 List 在构造时复制，向量数组构造和读取均复制。C 的快照内部可持有高效 float[]，不向调用者暴露可变引用。

模块失败统一抛出 common/ServiceException，code、httpStatus、retryable 按 HTTP_API 错误表填写。HTTP 层转换为 ErrorEnvelope，后台任务保存 asError 并进入相应失败状态。取消、超时和空检索结果不能混为一种失败。

生成、改写和修复提示词固定放在 backend/src/main/resources/prompts/，由 D 维护。用户问题与资料放在单独结构化输入中；repair 调用同时加载 answer.txt 与 repair.txt。ProviderUsage 来自供应商外层响应，不允许模型在内容中填写。

## 调用关系

B ImportWorker → IndexPort → C DefaultIndexService → EmbeddingProvider → C HttpEmbeddingProvider → D ModelHttpClient。

D TurnWorker → Retriever → C DefaultRetriever → B ScopePort / CatalogReadPort；检索后 D 调 ChatProvider → D HttpChatProvider → ModelHttpClient。

C 的索引构建服务不依赖 B 的导入服务；只用 B 传入的保存回调。资料读取接口实现只依赖仓储，避免 B、C 互相构造业务服务形成循环。

## 方法

### ScopePort

定义：contracts/port/ScopePort.java。实现：documents/service/DefaultScopeService.java。主责 B，调用方 D C。

```java
List<UUID> resolve(UUID knowledgeBaseId, String scope, List<UUID> documentIds);
```

创建会话时 all 展开当前可用资料；selected 校验后返回固定集合。

```java
void validateFixed(UUID knowledgeBaseId, List<UUID> documentIds);
```

任一失效或跨课程即报错，不能悄悄删减集合。

```java
Map<UUID, Boolean> sourceAvailability(List<UUID> documentIds);
```

D 读取历史回答时批量调用，填充 Citation.source_available，不直接依赖 B 的仓储实现。返回不可变映射，输入去重后每个编号均有结果；空输入返回空映射。资料记录存在、未处于 deleting/deleted、原文件实际存在时为 true；停用不影响查看，缺失为 false。结果仅代表本次查询时刻，随后打开文件仍可能收到 NOT_FOUND。

### CatalogReadPort

定义：contracts/port/CatalogReadPort.java。实现：documents/repository/JdbcCatalogReader.java。主责 B，调用方 C。

```java
CatalogData load(UUID knowledgeBaseId);
```

在课程锁内读取 ready 且 enabled 的完整片段和向量。

### IndexPort

定义：contracts/port/IndexPort.java。实现：retrieval/DefaultIndexService.java。主责 C，调用方 B。

```java
EmbeddingBuildSummary embed(List<ChunkDraft> chunks, Consumer<EmbeddedBatch> onBatch, BooleanSupplier cancelled);
```

分批向量化；B 保存每批；取消后抛出明确异常；不修改资料状态。

```java
void invalidate(UUID knowledgeBaseId);
```

须在课程锁内调用；仅标记缓存失效，保留活跃读取租约。

### Retriever

定义：contracts/port/Retriever.java。实现：retrieval/DefaultRetriever.java。主责 C，调用方 D C 调试控制器。

```java
SearchResult search(SearchRequest request, Deadline deadline);
```

内部在课程锁内再次校验范围并取得快照；两路共用快照；返回证据副本后释放。

### EmbeddingProvider

定义：contracts/port/EmbeddingProvider.java。实现：providers/HttpEmbeddingProvider.java。主责 C，调用方 C。

```java
EmbeddingBatchResult embedDocuments(List<ChunkDraft> chunks, Deadline deadline);
```

最多 10 片段；按供应商 index 还原片段编号；仅 429 和 5xx 最多重试一次。

```java
QueryVector embedQuery(String query, Deadline deadline);
```

沿用本轮期限；1024 维已归一化向量。

### ChatProvider

定义：contracts/port/ChatProvider.java。实现：providers/HttpChatProvider.java。主责 D，调用方 D。

```java
RewriteResult rewrite(String question, List<HistoryTurn> history, Deadline deadline);
```

最多最近两轮 3000 码点；第一轮不调用。

```java
ChatCall generate(String question, List<SearchHit> evidence, Deadline deadline);
```

JSON Object 输出；temperature=0.1；max_tokens=2048；enable_thinking=false。

```java
ChatCall repair(String question, List<SearchHit> evidence, String invalidOutput, String validationError, Deadline deadline);
```

整轮最多一次结构修复；不新增来源不延长期限。

## 交换类型

### Deadline

文件 contracts/internal/Deadline.java；负责人 D。整个问答共用的 System.nanoTime 截止时刻。

```java
record Deadline(long deadlineNanos)
```

### ParsedPage

文件 contracts/internal/ParsedPage.java；负责人 B。页序从 1 开始；text 是位置基准。

```java
record ParsedPage(UUID documentId, int pageNo, String text, String extractionStatus)
```

### ChunkDraft

文件 contracts/internal/ChunkDraft.java；负责人 B。码点区间左闭右开；不跨页。

```java
record ChunkDraft(UUID chunkId, UUID documentId, int ordinal, int pageNo, int charStart, int charEnd, String text, String searchText)
```

### EmbeddedChunk

文件 contracts/internal/EmbeddedChunk.java；负责人 C。已校验、归一化的向量。

```java
record EmbeddedChunk(UUID chunkId, String model, int dimensions, float[] vector)
```

### EmbeddedBatch

文件 contracts/internal/EmbeddedBatch.java；负责人 C。B 在回调中短事务保存进度。

```java
record EmbeddedBatch(List<EmbeddedChunk> chunks, int completed, int total)
```

### EmbeddingBuildSummary

文件 contracts/internal/EmbeddingBuildSummary.java；负责人 C。成功必须完成全部片段。

```java
record EmbeddingBuildSummary(int completed, int total)
```

### ProviderUsage

文件 contracts/internal/ProviderUsage.java；负责人 D。真实供应商用量；未知为空。

```java
record ProviderUsage(Long inputTokens, Long outputTokens)
```

### EmbeddingBatchResult

文件 contracts/internal/EmbeddingBatchResult.java；负责人 C。按输入片段编号还原顺序。

```java
record EmbeddingBatchResult(List<EmbeddedChunk> vectors, ProviderUsage usage, String actualModel)
```

### QueryVector

文件 contracts/internal/QueryVector.java；负责人 C。问题向量与片段采用相同模型。

```java
record QueryVector(String model, int dimensions, float[] vector, ProviderUsage usage, String actualModel)
```

### CatalogChunk

文件 contracts/internal/CatalogChunk.java；负责人 B，C 参与字段对接。仅加载 ready 且 enabled 资料 不可变副本。

```java
record CatalogChunk(UUID knowledgeBaseId, UUID documentId, UUID chunkId, String fileName, int pageNo, int charStart, int charEnd, String text, String searchText, String model, int dimensions, float[] vector)
```

### CatalogData

文件 contracts/internal/CatalogData.java；负责人 B，C 参与字段对接。数据库的完整可检索课程快照。

```java
record CatalogData(UUID knowledgeBaseId, List<CatalogChunk> chunks)
```

### HistoryTurn

文件 contracts/internal/HistoryTurn.java；负责人 D。完整已完成轮；只用于指代消解。

```java
record HistoryTurn(String question, String answer)
```

### RewriteResult

文件 contracts/internal/RewriteResult.java；负责人 D。需澄清时问题为空；否则独立问题非空。

```java
record RewriteResult(boolean needsClarification, String standaloneQuestion, String message, ProviderUsage usage, String actualModel)
```

### ModelBlock

文件 contracts/internal/ModelBlock.java；负责人 D。每个事实段使用本轮已存在的 S 编号。

```java
record ModelBlock(String text, List<String> sourceIds)
```

### ModelAnswer

文件 contracts/internal/ModelAnswer.java；负责人 D。仅 answered insufficient_evidence needs_clarification。

```java
record ModelAnswer(String answerStatus, List<ModelBlock> answerBlocks, String message)
```

### ChatCall

文件 contracts/internal/ChatCall.java；负责人 D。原始模型内容；由问答层解析、校验，保留原文供一次结构修复。

```java
record ChatCall(String content, ProviderUsage usage, String actualModel)
```

## 固定实现参数

- 切分目标700码点，上限1000，相邻约100重叠，不跨页。BM25Similarity k1=1.2、b=0.75；body 用 SmartChineseAnalyzer；technical_terms 用 WhitespaceAnalyzer。技术词正则 `[A-Za-z_][A-Za-z0-9_]*(?:[.-][A-Za-z0-9_]+)*(?:\+\+|#)?`，Locale.ROOT 小写；索引与查询同规则，两字段权重1。构造词项查询，不把用户原文当查询语法。
- 两路先过滤范围，各取20个候选；hybrid 采用每路 `1/(60+rank)` 相加。分数降序，同分按标准小写 chunk UUID 字符串升序。按顺序去除重复片段；同资料同页且交集长度/较短片段长度≥0.8时保留排名较高者。三策略均在最终截取前使用同一规则，默认6、上限10。证据总长度自然不超过10×1000码点。
- query 向量与库向量使用 text-embedding-v4 1024维，先检查有限、非零，再归一化，以 double 累加点积。模型或维度不同明确报错；更换模型必须整库重建。
- Deadline 按 System.nanoTime 计算。问答开始执行后总计60秒，排队另计；查询向量重试、改写、生成、一次结构修复共同消耗期限。导入向量每次请求限30秒，不把整个PDF限制60秒。连接超时10秒，单次HTTP超时取30秒与剩余期限的较小值。
- 生成 qwen3.5-plus、temperature=0.1、enable_thinking=false、response_format=json_object、max_tokens=2048。改写max_tokens=256。提示词必须出现JSON；JSON Object不保证字段结构，D显式校验。模型只输出 answer_status、answer_blocks[text,source_ids]、message；转换为内部 ModelAnswer，再映射公开 Turn。
- 第一轮不改写；后续仅最近2轮完整历史且总计≤3000码点，超预算从最旧完整轮丢弃，不把历史作为证据。生成与改写不自动网络重试；embedding仅429/5xx最多一次，等待1秒且受剩余期限约束。HTTP资源与问答工作线程分开。
- ChatProvider.generate/repair 返回 ChatCall 原始内容。ModelAnswerValidator 严格解析、检查互斥字段及来源；格式错误时把原始内容和具体错误交 repair 一次。引用映射由首次出现顺序生成 citation_id，同一chunk复用编号。完整excerpt取SearchHit.text，模型不填写文件名、页码或原文。
- 本轮无命中可直接返回 insufficient_evidence；有命中也不必然有足够依据。模型失败进入 failed。来源有效只证明绑定，不证明语义完全正确。
- 原文件在取得证据后删除，在途问答可依据不可变副本完成；新的检索不能再读该资料。回答引用的 source_available 动态计算。每次实际供应商调用汇总usage，包括结构修复；任一未知则相关汇总为null，不能填0。

## 公共基础方法

这些签名已在代码中给出，不再由各人另拟一套。

- D 的 `providers/ModelHttpClient.post(String endpoint, String jsonBody, Deadline deadline): String` 接收相对模型基址的 `chat/completions` 或 `embeddings` 路径，拼接时保留 `/compatible-mode/v1/`。成功返回供应商完整外层 JSON 字符串，由适配器提取内容、模型和用量。它统一设置认证、UTF-8 JSON、连接和单次请求期限；不做自动网络重试。401/403→MODEL_AUTH_FAILED，429→MODEL_RATE_LIMITED，5xx→MODEL_UNAVAILABLE，超时→MODEL_TIMEOUT，其他非2xx→MODEL_OUTPUT_INVALID，均通过 ServiceException 抛出；不在用户错误中泄露密钥或完整供应商响应。C 只对429及5xx对应错误执行最多一次重试，解析及向量验证失败转为 EMBEDDING_FAILED。
- B 的 `common/CourseLocks.withLock(UUID courseId, Supplier<T> operation): T` 提供课程互斥区；创建固定范围、发布ready、启停、删除和取得一致检索快照共用此入口。原型已实现；不得各建一套锁。锁表按课程保留，不能在仍有等待者时移除锁。
- B 的 `db/WriteTransactions.execute(Supplier<T> operation): T` 提供 JVM 写锁和 Spring 短事务，已有实现。D 的问答仓储也通过它写入，读取用 Spring JDBC 获取受事务管理的连接，禁止持有共享 Connection。需要课程锁时先获取课程锁，再调用写事务；供应商调用和 PDF 解析不得放入其中。
