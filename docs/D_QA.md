# 5 成员 D 问答与模型设计

D 负责 D01 至 D03，向 A 提供会话、问答、历史与反馈接口，向 C 提供公共模型 HTTP 客户端。问答使用 C 返回的本轮证据，B 提供资料范围和原文件可用性。

## 问答处理流程

1. 创建会话时，服务在课程锁内调用 B 的 `ScopePort.resolve`。选择“全部资料”会展开当时可用的具体资料编号。会话记录与 `conversation_documents` 在同一个 SQLite 短事务中写入。
2. A 提交问题时生成 `client_request_id`。首次受理返回 HTTP 202 和完整的 `Turn`，前端用 `Turn.id` 轮询。相同请求重传返回 HTTP 200 和原记录。同一编号用于不同问题、策略、片段数或重试目标时返回 409。
3. 首轮直接检索原问题。后续问题先用最近两轮已回答的完整问答消解指代，问题和回答合计最多 3000 个 Unicode 码点，超额从最旧整轮舍弃。改写所用历史随新问题保存，主动重试继续使用原历史。指代不清时请用户补充。
4. `TurnWorker` 把课程、固定资料编号、独立问题、策略和 `top_k` 交给 `Retriever.search`。默认使用 `hybrid`、6 个片段。服务保存 C 返回的 `SearchResult`、查询向量用量、实际模型和分阶段耗时。没有命中时直接返回资料不足。
5. 生成模型接收本轮来源编号和原文。校验器检查 JSON 字段、回答状态、每段文字及其来源。结构或引用错误可修复一次。D 按段落中的首次引用顺序，从 1 给真实片段编号，同一片段复用编号。文件名、实际页序和完整摘录均取自 C 的 `SearchHit`。终态、回答段落和引文在同一事务中提交。

## 状态与异常处理

`queued` 表示已入库。执行时按需经过 `rewriting`、`retrieving`、`generating`、`validating`。全局最多同时执行两个问题，每个会话最多一个活动问题。60 秒执行期限从工作线程开始时计算，排队时间单列。改写、问题向量化、生成和修复共用同一截止时刻。

| 终态 | A 如何展示 |
| --- | --- |
| `answered` | 展示 `answer_blocks` 和 `citations`，可写入或覆盖一条共享反馈 |
| `insufficient_evidence` | 用 `message` 说明当前资料缺少足够依据 |
| `needs_clarification` | 展示 `message`，请用户补充问题 |
| `failed` | 展示 `error`，按 `error.retryable` 判断是否可主动重试 |
| `interrupted` | 告知用户执行已中断，可使用新请求编号重试 |

轮询终态返回 HTTP 200。进程重启会把遗留的排队和执行中问题标为 `interrupted`。超时任务中断执行线程，并按当前状态写终态；晚到结果无法再次写入。主动重试保留原记录，通过 `retry_of_turn_id` 关联新记录。会话正在处理问题时，提交另一问题或删除会话会收到 `CONVERSATION_BUSY`。

聊天请求从供应商外层响应读取实际模型与用量。生成和一次结构修复的用量合计，未执行的阶段在终态计 0，执行过而用量未知时保留 null。网络等待发生在数据库写事务之外。

## 对接与交付

| 对接方 | 接口和数据 |
| --- | --- |
| A | 通过 `/api/v1/conversations`、会话下的 `turns`、`/turns/{turn_id}` 及重试、反馈操作读取现有 HTTP 类型。资料删除后仍可显示历史摘录，打开 PDF 依据本次读取的 `source_available`。 |
| B | `ScopePort.resolve` 创建范围，`validateFixed` 检查可检索性，`sourceAvailability` 批量检查原文件能否打开。资料停用后可从搜索范围失效，同时仍可打开原 PDF。D 使用 B 的 `CourseLocks` 和 `WriteTransactions`。 |
| C | C 的向量适配器调用 `ModelHttpClient.post("embeddings", body, deadline)`，收到供应商完整外层 JSON。D 的问答线程直接调用 C 的 `Retriever` 并保存返回的证据快照。 |

公共客户端使用本机配置的模型基址，保留 `/compatible-mode/v1/` 路径。连接超时 10 秒，单次请求取 30 秒与剩余期限的较小值。C 根据 HTTP 429、5xx 及网络异常原因决定是否执行其一次向量重试。聊天请求由 `HttpChatProvider` 使用百炼兼容的 `chat/completions`，生成模型为 `qwen3.5-plus`，关闭思考，温度 0.1，生成与修复上限 2048 token，改写上限 256 token。已有三个提示词从类路径加载。

## 开发顺序

D01 提供公共请求、生成适配与会话状态，D02 接入 C 的检索及真实片段引用，D03 实现追问、历史、反馈、超时和重试。后端类位于 `qa/`、`providers/` 和 `config/` 的指定位置。HTTP 字段、内部端口和问答相关表以仓库的 OpenAPI、Java 接口和 SQLite 迁移为准。

交付时已执行契约一致性检查、Java 21 后端打包、前端类型检查与构建。在真实 Spring 装配和空 SQLite 库上检查了 D 与 C 的依赖、缺模型配置时的错误以及会话和问答 HTTP 接口。真实课件验收需要 B 发布一份可检索 PDF 并配置百炼，再由 A 从回答引用打开对应原页。
