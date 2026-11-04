# 旅游攻略分享与行程规划平台

> 可运行的旅游攻略分享与私人行程平台，包含源码、设计与验收证据、数据库初始化、运行和毕业演示材料。采用Codex主导、DSH执行的分阶段规划、实现和审查流程。

代码仓库：[TheSixPasserby/travel-platform](https://github.com/TheSixPasserby/travel-platform)（私有，主分支`main`）。现有阶段基线按用户授权提交；仍有待补充核对的功能，阶段测试通过不等同最终全功能验收。

## 项目目标
构建一个基于 Web 的旅游攻略分享与行程规划平台。核心为用户生成内容（UGC）和手动行程规划；按 `AGENTS.md` 的功能优先级，目的地管理属于 P0 必须完成，基础后台属于 P1 应当完成，AI 行程推荐属于 P2 选做。

## 运行与交付

已配置本机访问 `http://127.0.0.1:5173`。新机器先按[部署与运维](docs/22-deployment-and-operations.md)准备专用MySQL8.x、独立测试库及根`.env`，然后在项目根运行：

```bash
npm --prefix frontend ci
python3 scripts/backend.py run
# 另一个终端
npm --prefix frontend run dev -- --host 127.0.0.1
```

生产构建：`python3 scripts/backend.py package`、`npm --prefix frontend run build`；运行JAR：`python3 scripts/backend.py jar`。安全交付目录：`python3 scripts/release.py 尚不存在的输出目录`，含SHA256SUMS且不含本机密码或真实上传。

[使用手册](docs/23-user-manual.md) · [10分钟演示](docs/24-demo-walkthrough.md) · [需求追踪](docs/25-requirements-traceability.md) · [论文与答辩提纲](docs/26-thesis-and-defense-outline.md)。本机演示账号密码仅在忽略的`.local/demo/credentials.json`，换机按演示说明重新创建，不提供默认密码。

## 技术基线

| 层次 | 技术 |
|---|---|
| 前端 | Vue 3、TypeScript、Vite |
| 样式 | Tailwind CSS、自定义组件 |
| 路由/状态 | Vue Router、Pinia |
| HTTP | Axios |
| 后端 | Java 21、Spring Boot 3.x、Maven |
| ORM | MyBatis-Plus |
| 认证 | Sa-Token |
| 数据库 | MySQL 8.x |
| API 文档 | SpringDoc OpenAPI |
| 测试 | JUnit 5、Spring Boot Test、前端 Vitest（按需） |
| 架构 | 前后端分离、模块化单体 |

具体依赖补丁版本与兼容性依据记录在[依赖基线](docs/15-dependency-baseline.md)。

## 双模型协作要求

本项目采用双模型协作：Codex 主代理与 DSH Subagent 使用不同模型，这是有意设计，不是配置错误。

- **Codex 主代理：GPT-6.1-Sol / Ultra**，负责总体规划、架构决策、任务拆分、代码整合、质量控制和最终验收。
- **DSH Subagent：CodeBuddy DeepSeek V4.1 Flash / Max**，承担编码、测试、文档整理和专项审查。
- 涉及架构、数据库、API 和跨模块的修改，最终由 Codex 决策。
- 不修改 Codex 全局模型配置，不再要求二者模型相同；任一代理不得擅自切换被指定的模型。
- 模型必须在 Codex/DSH 运行时配置中选择；项目文件只能声明要求，不能代替运行时设置。
- 启动时分别核对两侧模型：Codex 为 GPT-6.1-Sol / Ultra，DSH 为 CodeBuddy DeepSeek V4.1 Flash / Max，各自符合分工即可，无需相同；无法核实 DSH 指定模型配置时不派发 DSH 子代理。
- Codex 应检查当前环境中实际可用的 DSH 子代理能力和调用方式，不得臆造工具名、命令或参数。
- Codex 负责最终决策、整合、验证和进度记录。

## 启动步骤

1. 将 ZIP 解压到独立目录。
2. 使用 Codex 打开解压后的项目目录。
3. 分别核对：Codex 主代理为 `GPT-6.1-Sol / Ultra`，DSH Subagent 为 `CodeBuddy DeepSeek V4.1 Flash / Max`；两者不需要相同。DSH 指定配置无法核实时不派发子代理。
4. 将 `prompts/00-CODEX-START.md` 的完整内容发送给 Codex。
5. 让 Codex 先读取根目录 `AGENTS.md`，再读取 `docs/` 和 `database/`。
6. 按里程碑逐步实施，不要一次性生成全部系统代码。

## 环境检查

```bash
java -version
mvn -version
node -v
npm -v
mysql --version
git --version
```

建议基线：Java 21、Node.js 24 LTS 或兼容 LTS、MySQL 8.0/8.4。优先使用 Maven Wrapper。

## 目录结构

```text
.
├── AGENTS.md
├── README.md
├── PROJECT_STATUS.md
├── .editorconfig
├── .gitignore
├── .env.example
├── backend/                 # Codex 创建 Spring Boot 工程
├── frontend/                # Codex 创建 Vue 3 工程
├── database/
│   ├── README.md
│   ├── schema.sql
│   └── seed.sql
├── docs/
└── prompts/
```

## 文档优先级

1. 用户当前明确提出的新要求；
2. `AGENTS.md` 的安全、协作和工程约束；
3. `docs/11-decisions-and-open-questions.md` 已确认决策；
4. 需求、架构、数据库和 API 文档；
5. 阶段提示词。

若文档冲突，先记录冲突和建议，不得静默选择方案并扩大实现范围。

## 当前状态
基线与M1–M7本机交付已验收，P0/P1核心可运行、可演示。最新完整后端165项、前端227项、Python40项、类型/生产构建、独立审查与JAR/dist三角色浏览器通过，见[M7记录](docs/27-m7-test-report.md)。演示数据有意保留，临时校验图片已清理；独立MySQL8.4.13在3307运行，原3306服务保留。正式公网部署、备份恢复、学校格式论文终稿与PPT尚未完成，见[项目进度](PROJECT_STATUS.md)。不自动commit，不更换双模型配置。
