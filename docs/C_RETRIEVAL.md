# C 向量与检索模块

本模块实现 START.md 中的 C01、C02、C03，负责片段向量化、三种检索策略、课程索引快照和检索调试接口。开发基线为 main 的 a11c4b3ef7c382e8b485751a9b06ea13271f48ea。HTTP 字段、内部 port 签名、交换 record、数据库迁移及依赖版本均沿用现有定义。

C 的业务实现已补齐；真实 PDF 导入和问答联调仍依赖 B 的 ScopePort、CatalogReadPort、导入发布流程，以及 D 的 ModelHttpClient。这些依赖在当前基线中尚未实现，也未注册为可用的 Spring Bean。缺少依赖时明确报告对应负责人的 FEATURE_NOT_IMPLEMENTED，不返回虚构检索结果或模拟向量。

## 任务与实现文件

以下 Java 文件均位于 backend/src/main/java/com/courseqa/。

| 任务 | 实现文件 | 交付内容 |
| --- | --- | --- |
| C01 | providers/HttpEmbeddingProvider.java；retrieval/DefaultIndexService.java | 真实供应商协议适配、响应重排和校验、归一化、有限重试、分批保存回调、取消识别 |
| C02 | retrieval/LuceneBm25Index.java、TechnicalTerms.java、DefaultRetriever.java | 中文与技术词检索、范围过滤、真实来源返回、稳定排名 |
| C03 | retrieval/DenseRetriever.java、RrfFusion.java、CatalogSnapshotRegistry.java、CatalogSnapshotLease.java、RetrievalController.java | 精确向量检索、RRF、统一去重、快照失效与资源生命周期、调试接口 |
| 内部辅助 | retrieval/CatalogSnapshot.java、RankedChunk.java、RetrievalRules.java、SearchRequestReader.java | 不可变快照、候选结构、固定参数和校验、严格 HTTP 输入读取 |

新增辅助类不形成第二套公共 DTO。A 继续使用生成的 TypeScript 类型，D 继续依赖 Retriever，B 继续依赖 IndexPort。C 不直接访问 SQLite，不修改资料状态或问答状态，不依赖 documents、qa 的实现类。

## 向量化与 B 的交接

B 调用 IndexPort.embed(chunks, onBatch, cancelled)。C 在发起请求前校验全部片段的编号、页码、原文码点长度、位置和重复编号，然后按输入顺序每批最多 10 个发送 searchText。原文 text 与位置不被 C 改写。

请求使用 embeddings 相对路径，通过 D 的 ModelHttpClient.post 发出。请求字段为 model=text-embedding-v4、dimensions=1024、encoding_format=float 和 input 字符串数组；不在 C 内拼接供应商域名或处理密钥。模型地址和密钥仍按 START.md 配置。

响应必须包含与输入等量的 data 项。C 校验每项 index 为唯一、完整且不越界的整数，按 index 恢复输入顺序，再绑定原 chunkId。向量须有 1024 个有限数值、非零范数，使用 double 累加平方和后 L2 归一化，以 float[] 交回。实际返回的模型名称若存在，必须与固定模型一致；未提供则保留未知值。

每批完整校验后调用 onBatch，EmbeddedBatch.completed 为累计完成数，total 为本次输入总数。只有保存回调成功返回后才推进下一批；回调异常直接传回 B。全部批次成功才返回 EmbeddingBuildSummary。空输入返回 0/0，不调用模型或保存回调。

取消标记在输入校验、每批请求前后、保存回调前后检查；在途模型调用失败时也检查取消标记。主动取消抛出 CancellationException，线程中断使用 TASK_INTERRUPTED，超时使用 MODEL_TIMEOUT，三者不转换为空结果。取消标记本身不会强行终止已经发送的 HTTP 请求；B 可以同时中断工作线程，D 的 HTTP 基础需响应线程中断。B 在保存和发布阶段还必须重新核对任务编号与删除状态，阻止晚到结果发布。

B 保存向量仍使用数据库规定的 1024 个小端 float32，长度 4096 字节。全部片段与向量保存齐备后，由 B 在同一课程锁内完成 ready 发布或启停、删除的状态提交，再调用 IndexPort.invalidate。网络请求、向量计算不得包在数据库写事务中。

## 检索流程与固定参数

Retriever.search 接收现有 SearchRequest 和调用方的 Deadline。D 必须传入本轮已经存在的期限；C 不重新开始问答计时。调试控制器独立建立 60 秒期限，调用同一个 Retriever。

1. 校验输入，确定具体 documentIds 和 topK。
2. 在 B 的 CourseLocks 中再次调用 ScopePort.validateFixed；任何失效、停用、未就绪或跨课程资料都由 B 按既定错误码拒绝，不能静默删减资料集合。
3. 在该锁内取得当前课程的完整快照；冷缓存调用 CatalogReadPort.load，仅接受 ready 且 enabled 的片段及向量，同时建立关键词索引与向量缓存。
4. 释放课程锁后，按策略计算候选。hybrid 的两路始终来自同一租约；问题向量只在 dense/hybrid 且所选范围存在片段时生成。
5. 稳定排序、合并重复片段、去除页内高度重叠片段，截取 topK，分配 S1、S2 等临时来源编号。构造证据副本后释放租约。

| 项目 | 固定规则 |
| --- | --- |
| BM25 | Lucene BM25Similarity，k1=1.2，b=0.75 |
| body 字段 | SmartChineseAnalyzer，索引 searchText；查询使用相同分析器 |
| technical_terms 字段 | 正则提取技术词，Locale.ROOT 小写，WhitespaceAnalyzer；索引与查询同规则 |
| 技术词正则 | `[A-Za-z_][A-Za-z0-9_]*(?:[.-][A-Za-z0-9_]+)*(?:\+\+|#)?` |
| 字段权重 | body 与 technical_terms 均为 1；组合词项查询，不使用用户原文作为查询语法 |
| 向量 | text-embedding-v4，1024 维，归一化后精确点积；double 累加，结果限定在 [-1,1] 以消除 float32 舍入误差 |
| 候选范围 | 两路均先限制资料集合，再各取最多 20 个候选；课程由快照隔离 |
| RRF | 同一片段各路 `1/(60+rank)` 相加，rank 从 1 开始；不直接相加 BM25 与向量原始分数 |
| 同分顺序 | 标准小写 chunk UUID 字符串升序；候选截取时也遵守此规则 |
| 重叠去重 | 同资料、同页且交集长度/较短片段长度≥0.8，保留排名更高者；三策略均在最终截取前执行 |
| 返回数量 | top_k 默认 6，取值 1 至 10；不足时不补造结果，也不扩大候选池 |

BM25 没有词项命中时返回空数组；向量检索不另加文档未规定的相似度阈值。hybrid 的向量调用失败时整次检索报错，不自动退回 BM25。分数只用于排序，不表示回答正确概率。

关键词查询允许 2000 码点，可能生成超过 Lucene 默认 1024 个词项，因此模块把 Lucene 查询子句上限至少设为 8192。这只防止合法长度查询触发库默认限制，不改变关键词权重、候选数或检索协议。

## 快照生命周期

CatalogSnapshotRegistry 按课程缓存；完整片段、Lucene reader 和向量数组由同一个 CatalogSnapshot 持有。每次检索即使命中缓存也先重新校验固定资料范围。

invalidate 从可获取的缓存中移除旧快照并将其标记为退役，不在该操作中重新请求模型。下次查询从 SQLite 提供的完整课程数据重建。旧快照有活跃租约时继续服务已有读者，最后一个租约释放后关闭 reader、目录、分字段包装器及其两个实际分词器。重复释放同一租约不会重复扣减计数。

查询超时、向量失败、输入失败或结果构造异常时，try-with-resources 仍释放租约。新快照构建失败不发布半成品，并清理已经打开的 Lucene 资源。应用关闭时停止获取新快照，现有租约用完再释放资源。

原资料在本轮取得快照后被删除，已有检索可用不可变证据完成；新查询必须重新校验资料并拒绝失效范围。历史引用当前能否打开原文件由 D 调用 B 的 sourceAvailability 计算，C 不根据旧快照推断当前文件可用性。

## HTTP 格式与返回值

POST /api/v1/retrieval/search 接收 application/json。字段仍为 knowledge_base_id、document_ids、query、strategy、可选 top_k，响应仍为 SearchResult。SearchHit 保留真实 chunkId、documentId、文件名、页码、码点区间与完整原文。

SearchRequestReader 在控制器边界读取原始 JSON，以区分省略 top_k 与显式 null，不修改生成 DTO。未知字段、重复 JSON 字段、尾随 JSON、非对象请求、错误字段类型、非标准小写 UUID、重复资料编号、空范围及超限值均明确拒绝。top_k 省略为 6，显式 null、字符串和非整数输入不作为默认值。内部 Java 调用也经过同一业务校验，topK=null 表示调用方使用默认值。

未运行的检索分支，其 rank、score 为 null；rrf_score 仅 hybrid 有值。source_id 在最终筛选后从 S1 连续分配，只对当前请求有效。所有声明字段由现有生成 DTO 返回，不添加公开字段。

query_embedding_ms 包含问题向量调用与受限重试等待；retrieval_ms 包含范围校验、冷缓存构建、候选计算和结果整理，不重复计入向量请求时长。未调用问题向量时，query_embedding_ms 和 query_embedding_input_tokens 为 0，actual_embedding_model 为 null。

用量读取供应商外层 usage.prompt_tokens；若未提供，可读取纯向量接口的 usage.total_tokens。缺失用量为 null，不用文本长度估算。重试前失败调用的用量无法通过现有 ModelHttpClient 返回时，整个问题向量阶段的用量保留 null，不能只填成功重试的用量。供应商实际模型名称缺失时同样保留 null。

## 期限、失败与 D 的交接

ModelHttpClient 保留现有 post(endpoint, jsonBody, deadline) 签名，并注册为 Spring Bean。其连接超时为 10 秒，单次请求超时为 30 秒与剩余期限的较小值，自身不做自动重试。

问题向量与 D 的改写、生成、修复共享同一 60 秒执行期限，排队另计。导入没有整份 PDF 60 秒限制；C 每批预留两次各最多 30 秒的 HTTP 请求和一次 1 秒等待，总预算最多 61 秒，每批重新计算。ModelHttpClient 仍须为每一次 HTTP 请求执行 30 秒上限。

向量适配只对对应 HTTP 429 的 MODEL_RATE_LIMITED，以及 HTTP 5xx 的 MODEL_UNAVAILABLE，等待 1 秒后最多重试一次，剩余期限不足时不重试。鉴权、缺配置、超时、网络 I/O、解析和向量校验错误不自动重试。两次可重试服务错误仍失败时返回 EMBEDDING_FAILED；模型不一致返回 EMBEDDING_MODEL_MISMATCH；其他已有错误按原有语义传播。

对接时 D 需要保留可识别的错误来源：HTTP 429 的 ServiceException.httpStatus 为 429；HTTP 5xx 保留 5xx 状态；网络 I/O 失败保留 IOException 异常链，线程中断保留中断状态并使用 TASK_INTERRUPTED。C 通过现有状态、code 与异常链区分网络错误和 HTTP 5xx，没有新增公共字段或方法。D 当前尚无实现，这项错误映射需在真实联调时核对，不能仅凭编译通过认定供应商错误处理已接通。

输入问题使用 INVALID_REQUEST/EMPTY_SCOPE，快照资料结构损坏使用 INTERNAL_ERROR。错误经现有 ApiExceptionHandler 转为 ErrorEnvelope；后台由 B、D 保存对应状态。用户可见错误不包含密钥或供应商完整响应。

## 当前接入状态

| 提供方 | 需要交付的内容 | 当前情况 |
| --- | --- | --- |
| B | ScopePort、CatalogReadPort 的实现及 Bean；真实 ChunkDraft、保存回调、取消状态、发布与失效流程 | 基线仍是未实现骨架，等待 B 接入 |
| D | ModelHttpClient 的实现及 Bean；原有期限、HTTP 错误语义；通过 Retriever 调用并保存检索快照 | 基线仍是未实现骨架，等待 D 接入 |
| A | 使用现有 SearchRequest/SearchResult 接入检索页 | HTTP 入口已接到统一检索服务，真实结果依赖 B/D |
| C | EmbeddingProvider、IndexPort、Retriever 及快照管理的 Bean | 本分支已提供实现 |

缺失 B/D Bean 时通过 ObjectProvider 在调用点明确报出缺少的依赖，保持分阶段开发；不自行实例化其他成员的骨架，不用默认假实现填补缺口。B/D 完成并注册既定类型的 Bean 后直接接入，无需替换 C 的接口。

## 构建与交付记录

检查日期：2026-11-18。本次使用 JDK 21.0.13.1、Node.js 24.22.3、Maven Wrapper 3.9.16。独立工具链放在仓库外，未改变系统默认 Java/Node。

| 检查 | 本次结果 |
| --- | --- |
| node scripts/generate-contracts.mjs --check | 通过；生成文件与主分支的内容一致，未改变公共字段 |
| 后端 mvnw.cmd -B -ntp package | 最终修订后通过；JDK 21 编译 96 个 Java 源文件并完成 Spring Boot 可执行 JAR 打包 |
| 前端 npm ci、npm run build | 通过，包含 TypeScript 检查与 Vite 构建 |
| 仓库现有合并门禁检查 | node --test .github/scripts/codex-review-gate.test.cjs，12/12 通过 |
| git diff --check | 通过 |
| 真实模型调用、真实 PDF 联调、业务功能测试 | 未执行；未配置供应商、未提供真实资料，B/D 依赖尚未完成 |
| GitHub 自动 AI 审查及远程 build | 以 PR 当前提交的实际检查结果为准；本地构建记录不能标记为远程审查通过 |

按照 AGENTS.md，本阶段没有添加课程样例、模拟回答或完整系统测试方案。现有 12 项检查仅验证合并门禁，不代表检索效果或业务正确性经过完整测试；Maven 当前没有业务测试源文件。

npm audit 对当前未改动的锁文件报告 6 项 high、0 项 critical，涉及 picomatch 及其依赖链上的 fdir、tinyglobby、vite、@vue/language-core、vue-tsc。根告警为 [GHSA-fc5q-39xc-vjpf](https://github.com/advisories/GHSA-fc5q-39xc-vjpf)。由 A 按既定依赖变更流程处理；本次 C 交付未执行 npm audit fix 或擅自调整冻结版本。

本机已在仓库旁的 .tools 目录准备并校验便携工具链。从仓库根打开 PowerShell 时，可仅为当前窗口设置版本，再按 START.md 执行构建或启动命令：

```powershell
$env:JAVA_HOME = (Resolve-Path '..\.tools\zulu21.54.201-ca-jdk21.0.13.1-win_x64').Path
$taskNodeBin = (Resolve-Path '..\.tools\node-v24.22.3-win-x64').Path
$env:PATH = "$env:JAVA_HOME\bin;$taskNodeBin;$env:PATH"
java -version
node --version
```

其他组员仍按 START.md 安装固定版本；.tools 不是仓库提交内容，也不是新增运行时依赖。

## 提交说明

任务编号与负责人：C01、C02、C03；成员 C。

完成了什么：向量模型适配、分批交回、三策略检索、来源与去重、课程快照及检索调试入口。

涉及文件与模块：上述 C 实现文件、本模块说明及 README 的入口说明。

公共接口 数据库 主结构是否改变：未改变公开契约、port 签名、record 字段、迁移、依赖版本或模块边界。

调用方需要如何接入：按本说明的 B/D 接入约定提供既定实现及 Bean；A 继续使用生成类型。

契约 编译 前端构建结果：见上方真实检查记录。

AI 审查的完整提交 SHA 与结果：在 PR 中记录并核对当前提交的完整 SHA，以及与其对应的 build 和 codex-review 结果。任何后续提交都须重新核对，不能用本说明中的本地检查记录代替远程审查。

尚未完成或受阻内容：B/D 依赖实现、供应商配置、真实 PDF 联调及后续授权的业务测试。它们属于联调前置与后续验收，不能声称已经完成。

## 实现依据

项目约定以 OWNERSHIP.md、INTERNAL_API.md、HTTP_API.md、DATABASE.md 及对应实际契约为准。供应商请求与外层响应结构核对了[百炼 OpenAI Embedding 兼容接口](https://help.aliyun.com/zh/model-studio/embedding-interfaces-compatible-with-openai)，保留项目冻结的模型、维度和批大小。
