# 目录与文件负责人

所有路径相对仓库根。已有文件可直接续写；标为“需新增”的文件是确定的实现落点，由对应成员建立。公共契约不能通过复制到其他目录形成第二套定义。

```text
course-rag-assistant/
  contracts/                 D 统筹；字段提供方 B C D 分别维护
    openapi.json             HTTP 契约唯一修改入口
    architecture.json        主结构与归属规则
  frontend/src/              A
    pages/                   页面
    components/              上传 原文 引用等组件
    stores/                  当前工作区状态
    api/                     请求封装与生成类型
  backend/
    pom.xml                  D
    config/                  D 配置样例；各人保管本机密钥
    src/main/java/com/courseqa/
      Application.java       D
      config/                D 应用装配和健康状态
      common/                D 通用错误；B 维护 CourseLocks
      contracts/dto/         自动生成，不手改
      contracts/internal/    按类型负责人维护
      contracts/port/        按接口实现负责人维护
      documents/             B
      db/                    B
      retrieval/             C
      providers/             按具体文件分给 C 与 D
      qa/                    D
    src/main/resources/
      application.yml        D，数据库设置由 B 配合
      db/migration/          B 汇总数据库结构
      prompts/               D 生成、改写与修复提示词
      prompts/               D 生成、改写与修复提示词
      prompts/               D 生成、改写与修复提示词
      static/                构建生成，不提交
  scripts/                   B 运行脚本；D 契约生成；A 构建协作
  docs/                      各人模块说明，D 汇总
  .github/                   A 维护模板，D 维护构建工作流
  data/                      本机运行生成，不提交
```

| 成员 | 现有关键文件 | 继续实现或新增的文件 |
| --- | --- | --- |
| A | pages/CoursesView.vue、DocumentsView.vue、ChatView.vue、RetrievalView.vue；api/http.ts；stores/workspace.ts | components/UploadQueue.vue、CitationCard.vue、PdfViewer.vue、AnswerBlock.vue；api/documents.ts、qa.ts、retrieval.ts |
| B | documents/KnowledgeBaseController.java、DocumentController.java、ImportJobController.java、DefaultScopeService.java、JdbcCatalogReader.java、PdfPageExtractor.java、PageChunker.java、ImportWorker.java；db/DatabaseConfig.java、WriteTransactions.java | documents/DocumentService.java、DocumentRepository.java、ImportJobRepository.java；db/MigrationRunner.java（将初始迁移拓展为按版本顺序执行） |
| C | retrieval/RetrievalController.java、DefaultRetriever.java、DefaultIndexService.java；providers/HttpEmbeddingProvider.java | retrieval/LuceneBm25Index.java、TechnicalTerms.java、DenseRetriever.java、RrfFusion.java、CatalogSnapshotRegistry.java、CatalogSnapshotLease.java |
| D | qa/ConversationController.java、TurnController.java、TurnWorker.java、ModelAnswerValidator.java、CitationMapper.java；providers/ModelHttpClient.java、HttpChatProvider.java | qa/ConversationService.java、ConversationRepository.java、TurnService.java、TurnRepository.java、FeedbackRepository.java；config/ExecutorsConfig.java |

上述 Java 路径省略统一前缀 `backend/src/main/java/com/courseqa/`；前端路径省略 `frontend/src/`。内部 record 的具体负责人见 INTERNAL_API。CatalogChunk 与 CatalogData 由 B 主责维护，C 参与字段对接。architecture.json 的 file_overrides 优先于目录规则；生成文件只由契约脚本更新。CatalogChunk 与 CatalogData 由 B 主责维护，C 参与字段对接。architecture.json 的 file_overrides 优先于目录规则；生成文件只由契约脚本更新。CatalogChunk 与 CatalogData 由 B 主责维护，C 参与字段对接。architecture.json 的 file_overrides 优先于目录规则；生成文件只由契约脚本更新。

## 依赖边界

A 只调用 HTTP。B 的导入服务通过 IndexPort 调用 C；C 通过 ScopePort 与 CatalogReadPort 使用 B 的资料；D 通过 Retriever 使用 C。providers 只依赖公共类型和 HTTP 基础，不能依赖 documents、retrieval 或 qa 的实现类。业务模块通过 contracts/port 交互，避免互相构造实现类和循环注入。

ModelHttpClient 归 D，HttpEmbeddingProvider 归 C，HttpChatProvider 归 D。SQLite 写入工具归 B，问答仓储归 D；不是所有数据库代码都由 B 编写。B 修改资料状态，C 只产生向量和管理索引，D 修改问答状态。

公共接口、迁移、依赖版本与模块边界发生变化时，PR 必须列出影响并先更新设计约定。普通模块内部修改由 AI 检查后合并。
