# 项目实现约定

遵守 contracts/openapi.json、contracts/architecture.json、contracts/port 的实际 Java 接口及数据库迁移。先看 docs/OWNERSHIP.md 确认负责文件。采用一个 Java 后端和一个 Vue 前端，不增加微服务、ORM、其他数据库或登录体系。

修改公共字段时先更新 OpenAPI，再运行 node scripts/generate-contracts.mjs；不手改生成的 Java DTO 或 TypeScript。公共结构变化单独记录影响。各业务模块自己实现控制器、服务及仓储；通过 contracts/port 使用其他模块，不依赖他人的实现类。

不把尚未实现的功能返回成成功；当前501是开发骨架标记，接入真实实现后才能移除。不添加样例数据、假资料、模拟回答或完整测试方案。日常验证仅执行契约、编译、类型和构建检查；用户后续授权测试时再建立独立方案。

合并审查按 docs/AI_REVIEW.md。AI审查不能自动改变已经冻结的接口、数据库和模块边界。所有新代码需能解释业务行为，不能伪造检查记录、模型usage或实验结果。
