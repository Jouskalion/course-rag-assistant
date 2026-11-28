# 课程资料智能问答系统

四人开发启动包。A 负责前端，B 负责资料和持久化基础，C 负责向量与检索，D 负责问答和生成模型。

当前可启动页面骨架和后端，并初始化 11 张空业务表。C 已实现向量适配、三种检索及索引快照。D 已实现公共模型请求、固定资料范围的会话、异步问答、引用、历史和反馈。真实课件联调依赖 B 的范围校验、资料读取与导入发布。缺少对应实现时接口返回 501。

C 的检索参数、导入取消与重试规则、课程快照缓存上限、已存向量错误处理及 B/D 联调条件见 [C 向量与检索模块](docs/C_RETRIEVAL.md)。

D 的问答处理、执行期限、引用和 A/B/C 对接方式见 [成员 D 问答与模型设计](docs/D_QA.md)。

## 先看这些文件

主方案见 `docs/开发定稿方案.docx`，共八页，按“总方案＋四人设计＋目录分工＋开工约定”组织；详细字段不重复塞入主文档。

1. `docs/START.md`：环境、启动与第一批任务。
2. `docs/OWNERSHIP.md`：每个人应修改的目录和文件。
3. `docs/HTTP_API.md`：27 项 HTTP 操作与 35 个数据结构。
4. `docs/INTERNAL_API.md`：B、C、D 的内部方法和交换类型。
5. `docs/DATABASE.md`：11 张表、约束、状态和删除规则。
6. `docs/AI_REVIEW.md`：每次合并前的 AI 审查规则。
7. `docs/BUILD_STATUS.md`：本启动包实际完成的构建与启动检查。

## 设计依据与修改入口

- HTTP 字段和响应结构以 `contracts/openapi.json` 为准。修改后在仓库根运行 `node scripts/generate-contracts.mjs`；Java DTO 和 TypeScript 类型由它生成，不手改生成文件。
- 内部方法以 `backend/src/main/java/com/courseqa/contracts/` 下 port 接口与 internal record 为准。
- 数据库以 `backend/src/main/resources/db/migration/V001__init.sql` 为准。已有真实数据后不改旧迁移；新增结构使用后续版本并由 B 扩展迁移器。
- `contracts/database.json` 和 `contracts/internal-ports.json` 是当前设计的结构化索引；修改对应定义时同步更新，由 AI 审查一致性。
- 方案的技术边界为单机、单后端进程、SQLite、Vue、Java。没有用户登录和公开云部署。

## 快速启动

安装 JDK 21 与 Node.js 24.22.3。Maven 由仓库 Wrapper 固定为 3.9.16；不要用本机 JDK 17 编译。

在两个 PowerShell 窗口中分别从仓库根执行：

```powershell
.\scripts\start-backend.ps1
```

```powershell
.\scripts\start-frontend.ps1
```

打开 `http://127.0.0.1:5173`。后端健康接口为 `http://127.0.0.1:8000/api/v1/health`。如本机限制运行脚本，按 `docs/START.md` 的逐条命令启动，不需要更改系统策略。

第一次安装依赖需要网络。模型配置留空可启动后端和查看健康状态。真实向量化和生成需要按 `docs/START.md` 在后端配置百炼 Base URL 与密钥。

## 交付状态

仓库已提供 C、D 的业务实现。D 的装配与空库接口检查覆盖了两个模块的调用关系。真实 PDF 导入、带出处的回答、前端打开原页及检索质量评价，待 B 的资料模块、A 的页面和本机模型配置就绪后验收。
