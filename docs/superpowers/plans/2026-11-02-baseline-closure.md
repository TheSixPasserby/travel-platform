# M0 基线闭环 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Codex 负责决策、共享契约、整合和验收；DSH 仅执行获分配的任务。

**Goal:** 完成需求、数据、接口与运行环境的基线闭环，再进入工程骨架和注册登录垂直切片。

**Architecture:** Vue SPA + Spring Boot 模块化单体 + MySQL，本地保存图片。认证和资源所有权由后端控制，AI 保持可选。

**Tech Stack:** Vue 3、TypeScript、Vite、Vue Router、Pinia、Axios、Tailwind CSS；Java 21、Spring Boot 3.x、Maven、MyBatis-Plus、Sa-Token、SpringDoc；MySQL 8.x。

**Spec:** [AGENTS.md](../../../AGENTS.md)、[需求](../../01-requirements-specification.md)、[架构](../../02-system-architecture.md)、[验收](../../09-test-and-acceptance.md)、[决策](../../11-decisions-and-open-questions.md)。

## Global Constraints

- Codex：GPT-6.1-Sol / Ultra；DSH：CodeBuddy DeepSeek V4.1 Flash / Max。不修改 Codex 全局模型配置。
- API 统一前缀 `/api/v1`；成功 `code=0`；错误使用合适 HTTP 状态码和稳定业务码。
- P0 必须完成，P1 应当完成，P2 选做；不删除已有 P0/P1，不引入额外基础设施。
- 用户已授权准备独立 MySQL 8.4 环境并保留现有 26.7 服务；连接信息放本地环境变量，不使用含真实数据的库做初始化测试。
- 不自动提交 commit、不重置历史、不强推；不记录密码、密钥或真实连接凭据。
- 未执行的构建和业务测试必须明确标记“未执行”。

## Review Focus

1. 作者编辑草稿时有私有读取入口；游客不能通过详情、评论或收藏间接读取草稿/隐藏内容。
2. 管理员内容治理与作者编辑分开；隐藏内容不能由普通作者重新发布。
3. BIGINT ID 在 Java 和 TypeScript 中保持精度，数据库范围与 Java 映射一致。
4. 上传失败不生成无效攻略/图片关联；不把用户提供的任意路径当作已上传文件。
5. dayId/itemId、排序列表和行程缩短日期均不能绕过行程所有权或造成部分写入。

## 文件与职责

| 责任 | 文件 |
|---|---|
| Codex 最终决定并维护共享契约 | `docs/05-database-design.md`、`docs/06-api-contract.md`、`database/schema.sql`、`docs/11-decisions-and-open-questions.md` |
| 需求和验收闭环 | `docs/01-requirements-specification.md`、`docs/04-business-processes.md`、`docs/09-test-and-acceptance.md` |
| DSH 模型规范修订，Codex 复核 | `AGENTS.md`、`README.md`、`prompts/*.md`、相关协作文档、`PACKAGE-MANIFEST.json` |
| 审查与进度证据 | `docs/13-baseline-review.md`、`PROJECT_STATUS.md` |

### Task 1：双模型规则与实际配置

**Interfaces:** 输入为用户 2026-11-02 的双模型决定；输出为可核实的职责、模型与派发规则。

- [x] 修订 AGENTS、README、prompts、章程、路线图、风险表、ADR-012、DSH 流程和 manifest。
- [x] 读取 DSH 实际配置，探测指定路由的工具调用能力；派发时显式使用 `tier=flash`、`effort=max`。
- [x] Codex 审查修订 diff，验证 manifest 可解析；确认现行规则不要求两侧模型相同。
- [x] 最终扫描全部新增记录，PROJECT_STATUS 和审查报告均使用双模型口径。

### Task 2：需求、API 与数据契约闭环

**Interfaces:** 输入为需求、SQL 和两项只读审查结果；输出为 Codex 裁定的规则及同步文档。没有业务代码时，不虚构前端类型或接口测试已同步。

- [x] 将资料修改统一为 P0，将基础后台明确为 P1、AI 为 P2。
- [x] 补齐两个 ER 图的目的地层级与行程/行程项目关系。
- [x] 补充上传失败、组合分页、退出失效、敏感字段、管理员边界及嵌套行程越权验收用例。
- [x] 获取两项 DSH 审查最终状态：接口审查完成；数据库审查因 max-tokens 失败，由 Codex 接管，不计为通过。
- [x] 明确 token 传递、响应 ID、错误码和分页边界；补齐草稿读取、管理员目的地列表、标签读取等现有功能所需入口。
- [x] 明确攻略状态转换、管理员治理边界、停用目的地和用户禁用后的行为，并同步流程、API 和验收定义。
- [x] 裁定 BIGINT 映射、冗余计数器及 SQL 初始化库边界；修订 SQL 初稿并复核 diff。

**验证：** 检查 12 表、17 外键和两个 ER 图一致；点赞/收藏联合唯一约束存在；每项规则可定位到验收用例。文本检查只证明文档结构，不证明 MySQL 可执行。

### Task 3：独立 MySQL 8.4 的本地验证环境

**Files:** `database/README.md`、`.env.example`、`docs/13-baseline-review.md`。实际 `.env` 为本地配置，不纳入交付。

**Interfaces:** 本地提供 `DB_HOST`、`DB_PORT`、`DB_NAME`、`DB_TEST_NAME`、`DB_USERNAME`、`DB_PASSWORD`；输出实际服务端版本与隔离测试库检查记录。

- [x] 检查 Java/Maven/Node/npm/MySQL/Git；验证按命令指定已安装的 Java 21 后 Maven 确实使用 Java 21。
- [x] 检查本地配置并只读查询原实例；确认 3306 为 26.7.0，随后取得用户对独立 8.4 环境的明确授权。
- [x] 安装独立 8.4.13 二进制，跳过默认初始化、全局链接和服务注册；使用本项目专用数据目录、3307 端口与本地凭据。
- [x] 查询新实例服务端版本确认属于 8.4，同时再次检查原 26.7 服务；输出不包含密码。
- [x] 确认独立空开发/测试库与初始化脚本目标一致，再执行 DDL。
- [x] seed 各执行两次，验证联合唯一、外键、状态及日期 CHECK；31 项数据库检查通过。修正弃用告警后两库各重跑 seed 两次，无告警且条数仍为 6/8。
- [x] 记录命令、版本、检查数量和失败原因；实际结果保存在 `docs/verification/2026-11-02-m0-database.json`，未改写或清空原实例数据。

### Task 4：M0 放行与下一里程碑

**Files:** `PROJECT_STATUS.md`、`docs/13-baseline-review.md`；M1 的详细文件和依赖版本在后续独立计划中固定。

- [x] 比对原始文件副本，确认没有原始文件丢失、越界修改或敏感信息进入交付文件。
- [x] 各任务均取得最终结果；数据库只读审查的失败已保留，Codex 接管复核，不能把派发成功当作任务完成。
- [x] 将契约检查、真实数据库检查和应用测试分开记录，应用测试继续标记未执行。
- [x] 建立 Git main 分支元数据，没有提交 commit；M1 计划与前后端骨架属于下一里程碑。
- [x] 最终文档与敏感文件边界检查 15/15 符合，M0 已放行。

**M1 范围：** 注册、登录、退出、当前用户、资料修改；BCrypt、Sa-Token、统一响应/异常/校验、前端登录表单和路由守卫。先有后端权限和核心测试，再做界面联调。验收涵盖重复注册冲突、通用登录失败提示、退出失效、禁用用户、资料字段白名单和响应脱敏。

**后续顺序：** M2 目的地/上传 → M3 攻略与草稿 → M4 互动/搜索/个人中心 → M5 手动行程 → M6 基础后台与响应式回归 → M7 部署和毕业材料。AI、统计和地图只在 P0/P1 稳定后评估。
