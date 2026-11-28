# 已确定的设计

1. 保留原有 A 前端、B 资料、C 检索、D 问答分工；向量模型适配由 C 实现，共用 HTTP 基础由 D 实现。
2. 数据库用11张业务表；新增 conversation_documents，以组合外键固定会话范围归属，替换早先会话里的资料编号JSON字段。
3. HTTP共有27项操作，采用35个结构。GET原文件返回完整PDF；资料重试统一返回Document，兼容继续清理删除；检索结果统一包含查询向量用量。以本包OpenAPI为最终定义。
4. 前端固定Node24.22.3，其他主要Java选型沿用原路线。补丁版本和锁文件进入仓库，避免四人使用不同版本。
5. 普通PR用AI审查和自动构建，无固定同伴审批；公共结构变化单独说明影响后再决定。
6. 不提供样例数据、模拟业务响应或完整测试计划。已有业务接口是明确501入口，真实数据接入依赖B先交付解析结果。
7. 交付物是开发起点，业务逻辑由四人实现。后端空库初始化、健康接口、前端页面骨架与构建流程已提供。
8. 角色 B 在 documents/db 模块使用 MyBatis-Plus 作为 ORM，固定 `com.baomidou:mybatis-plus-spring-boot4-starter:3.5.18.1`。该依赖只服务于 B 的资料仓储，Spring JDBC 与 MP 共存。pom.xml 属公共文件，此项变更已在 AGENTS.md、OWNERSHIP.md 同步说明。
9. B 的实体类使用 Lombok `@Data` 生成 getter/setter，固定 `org.projectlombok:lombok:1.18.34`（provided，编译期注解处理器，不进入运行时包）。Lombok 仅用于 B 的 entity 类，不改变公共 DTO、契约或接口。依赖版本已在 AGENTS.md、OWNERSHIP.md 同步说明。

## 技术依据

Java21符合选定Spring Boot版本要求，见 [Spring Boot系统要求](https://docs.spring.io/spring-boot/system-requirements.html)。

PDF解析使用PDFBox3.0.8，见 [PDFBox下载](https://pdfbox.apache.org/download.html)；关键词索引使用Lucene10.6.0，见 [Lucene发布说明](https://lucene.apache.org/core/corenews.html)。

向量选择text-embedding-v4、1024维、每批10条，见 [百炼向量接口](https://help.aliyun.com/en/model-studio/embedding-interfaces-compatible-with-openai)。JSON Object需要应用校验字段结构，见 [百炼结构化输出](https://help.aliyun.com/zh/model-studio/qwen-structured-output)。

Wrapper固定Maven版本，见 [Apache Maven Wrapper](https://maven.apache.org/tools/wrapper/)。前端基础环境参考 [Vue快速开始](https://vuejs.org/guide/quick-start.html)，实际精确依赖以package-lock.json为准。

前端最终锁定 Vue 3.5.45、Vite 7.3.8；交付前更新了存在已知问题的旧版本，并重新执行构建与依赖检查。参考 [Vue 安全公告](https://github.com/advisories/GHSA-g2v6-rqmx-r4w6) 与 [Vite 7.3.8 发布记录](https://github.com/vitejs/vite/releases/tag/v7.3.8)。
