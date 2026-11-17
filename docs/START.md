# 开工和运行

## 固定环境

JDK 21；Node.js 24.22.3；Maven Wrapper 3.9.16；Spring Boot 4.1.2；SQLite JDBC 3.53.4.0；PDFBox 3.0.8；Lucene 10.6.0。前端依赖以 `package.json` 的精确版本和 `package-lock.json` 为准，安装使用 `npm ci`。

四个人各自维护一份本地数据库。GitHub 同步源码、契约、迁移和说明，不同步 `data/`、密钥、node_modules 或构建产物。无需现在提供正式课程 PDF；B 开始实现真实解析时再由小组提供可提取文字的课件。

## 启动命令

确认 `java -version` 为 21、`node --version` 为 v24.22.3。在仓库根：

```powershell
$env:RAG_APP_HOME=(Get-Location).Path
Set-Location backend
.\mvnw.cmd spring-boot:run
```

另一窗口在仓库根：

```powershell
Set-Location frontend
npm ci
npm run dev
```

停止服务按 Ctrl+C。后端默认只监听 127.0.0.1:8000，前端 5173 代理 `/api`。统一从 `backend/` 启动后端；脚本会设置数据根目录，数据库不随终端位置漂移。

## 模型配置

将 `backend/config/application-local.yml.example` 复制为同目录 `application-local.yml`，保留已有配置。Base URL 从百炼当前地域和工作空间控制台复制，包含 `/compatible-mode/v1`；不要根据旧教程硬编码域名。密钥只放此本机文件或 `DASHSCOPE_API_KEY` 环境变量。前端没有模型密钥。生成模型为 qwen3.5-plus 非思考模式，向量模型 text-embedding-v4，1024 维。实际模型调用尚待 C、D 实现。

## 开发顺序

| 批次 | A | B | C | D | 交接产物 |
| --- | --- | --- | --- | --- | --- |
| 第一批 | 页面、请求层、资料与会话状态 | 课程接口、原文件存储、范围校验 | 统一检索接口、分词与向量适配代码 | 公共 HTTP 基础、会话和异步问答状态 | 按已确定类型编写；不依赖模拟响应 |
| 第二批 | 接入真实上传、状态、PDF | 逐页解析、切分、导入和分批保存 | 对接真实片段向量化、BM25 | 接入真实检索、生成和引用校验 | 一份真实 PDF 的上传、问答和出处打开 |
| 第三批 | 历史、反馈、检索页、失败状态 | 启停、删除、重试和恢复 | 向量、RRF、缓存与调试 | 追问、反馈、重试、超时及历史引用 | 完整业务功能 |
| 整合 | 前端构建与使用说明 | 单机打包和启动说明 | 检索参数说明 | 公共接口与模块说明汇总 | 可在组员电脑启动的同一版本 |

没有样例数据时，真实数据联调依赖 B 的解析结果。C 可以先实现算法、向量适配和缓存结构；D 可以先实现会话、状态、模型适配与校验。不得用虚构数据伪装已接通模块。

## 当前任务清单

| 编号 | 主责 | 文件入口 | 前置 | 本次交付条件 |
| --- | --- | --- | --- | --- |
| A01 | A | pages、api、stores | 已有类型 | 页面结构和统一请求层可构建，空态和错误态明确 |
| A02 | A | DocumentsView、PdfViewer | B02 B03 | 上传进度可见，按真实页序查看原文 |
| A03 | A | ChatView、CitationCard | D02 | 回答、历史、引用、反馈正确接入 |
| A04 | A | RetrievalView | C03 | 同一范围调用三策略并展示各自分数和耗时 |
| B01 | B | KnowledgeBaseController、DefaultScopeService、db | 无 | 课程接口、范围校验和数据结构可用 |
| B02 | B | PdfPageExtractor、PageChunker、DocumentController | B01 | 输出准确页码、码点位置、原文和检索文本 |
| B03 | B | ImportWorker、DocumentController | B02 C01 | 分批存向量，全部完成后发布 ready；导入状态可查询 |
| B04 | B | ImportWorker、scripts | B03 C03 | 启停、删除、重试、恢复、打包一致 |
| C01 | C | HttpEmbeddingProvider、DefaultIndexService | D01 公共 HTTP 基础 | 真实片段向量校验归一化并交回 B |
| C02 | C | DefaultRetriever、LuceneBm25Index | B02 | 在选定范围内返回真实 BM25 片段与来源 |
| C03 | C | DenseRetriever、RrfFusion、CatalogSnapshotRegistry | C01 C02 B03 | 三策略、缓存生命周期和调试接口可接入 |
| D01 | D | ModelHttpClient、HttpChatProvider、ConversationController | B01 供真实范围接入 | 公共请求、生成模型、会话和状态代码就绪 |
| D02 | D | TurnWorker、ModelAnswerValidator、CitationMapper | C02 B03 | 一轮真实问答、真实引用、明确失败结果 |
| D03 | D | TurnController、TurnWorker、会话仓储 | D02 | 追问、历史、反馈、幂等、超时和主动重试 |

## 每次提交

首次由 A 建立小组 GitHub 仓库并邀请其余三人协作，将启动包中 course-rag-assistant 文件夹内的内容作为仓库根，提交到 main；不要把 ZIP、node_modules 或本机 data 一并上传。已有仓库时由 A 将同一结构并入既有仓库。其余成员从同一仓库克隆后，按 A01、B01 等任务创建各自短期分支，完成后提交 PR；不要分别维护四套项目骨架。

任务分支从最新 main 创建。提交者在 PR 写任务编号、改动文件、接口影响、构建结果和 AI 审查记录；按 `docs/AI_REVIEW.md` 执行，不要求固定同伴审批。A 负责跟踪阻塞，接口提供方负责处理自己返回的数据问题。

## 构建

在仓库根运行 `node scripts/generate-contracts.mjs --check`。后端用 `.\mvnw.cmd package`，前端用 `npm ci`、`npm run build`。这些检查用于编译、类型与构建；不代表业务已经完成或经过完整测试。

执行 `scripts/build.ps1` 可先构建前端、复制到后端静态资源并打包 JAR。演示页面采用 hash 路由，统一访问 8000 端口。原始 PDF 和 SQLite 保留在外部 data 目录。
