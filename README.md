# 旅游攻略分享与行程规划平台

> 可运行的旅游攻略分享与私人行程平台，包含源码、设计与验收证据、数据库初始化、运行和毕业演示材料。采用Codex主导、DSH执行的分阶段规划、实现和审查流程。

M12 将山水间升级为 **Web 旅游内容分享与发现社区**：真实发现流、图文卡片、作者公开主页和同城/同主题推荐；Vditor 4.0.1 默认所见即所得，保留 Markdown 源码、受管上传与图片灯箱。全部原账号、攻略、互动、目的地、私人行程和后台继续保留。实现、截图、测试与限制见 [M12交付报告](docs/m12/M12-DELIVERY.md)，历史 [M11报告](docs/m11/ACCEPTANCE.md) 保留。

uni-app、微信小程序、Android/iOS 原生应用已停止维护与自动构建发布，源码和专用工具见 [归档历史索引](docs/m11/ARCHIVE-INDEX.md)。既有 APK、Release 和历史验收证据保留。

代码仓库：[TheSixPasserby/travel-platform](https://github.com/TheSixPasserby/travel-platform)（私有，主分支`main`）。现有阶段基线按用户授权提交；仍有待补充核对的功能，阶段测试通过不等同最终全功能验收。

## 项目目标
构建一个基于 Web 的旅游攻略分享与行程规划平台。核心为用户生成内容和私人行程规划。2026-11-06的M8升级已落地三篇原攻略图文、六城与24处景点资料、后台图库，以及真实平台资料驱动的AI结构化预览、编辑、单日重生成和确认保存。本机默认关闭AI，运营者可在服务端选择提供商预设或自定义端点。

M8交付见[最终报告](docs/33-m8-final-report.md)、[验收证据](docs/verification/2026-11-06-m8-final.json)和[AI预设配置](docs/m8/ai-provider-presets.md)。需求基线见[正式规范](docs/m8/00-implementation-spec.md)、[项目审计](docs/28-m8-phase0-audit.md)、[技术方案](docs/superpowers/specs/2026-11-06-m8-upgrade-design.md)与[实施任务](docs/superpowers/plans/2026-11-06-m8-upgrade.md)。

## 运行与交付

根目录提供 `./start.sh`，仅启动后端与 Web，均监听 `0.0.0.0`；`./start.sh --help` 查看用法。先准备运行中的 MySQL、根 `.env`、Java21 和 Node 依赖（`npm --prefix frontend ci`）。旧移动参数仅返回弃用提示。

本机入口为 [Web](http://127.0.0.1:5173)，手机使用电脑局域网 IP 的 5173 端口。启动器打印实际地址，将当前 IPv4 的 Web 来源追加到精确 CORS 白名单，保留已有配置。

启动器复用可识别、局域网可达且当前 Web 来源通过 CORS 预检的已有服务。网络/IP 改变后，若旧后端白名单不匹配，会提示停止原后端并重新运行；不会因只读健康检查成功就误报就绪。日志位于忽略的 `.local/run/时间-PID/`。旧实例若仅监听本机，在原终端按 Ctrl+C 后重新启动。按 Ctrl+C 清理本次启动的进程组，已有实例由原启动方式管理。后端端口通过 `SERVER_PORT` 配置，Web 使用对应回环代理。

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
M8本机代码、增量数据、备份恢复和浏览器闭环已验收。最新全量测试、执行边界与运行入口见[最终报告](docs/33-m8-final-report.md)。开发库14张表132行业务数据、44个上传文件在AI联调前后逐项一致，私有草稿保留。AI已用本地HTTP stub完成集成，真实收费提供商尚未联调。2026-11-07重新启动并完成236/311/65项推送前复验，远程CI状态以交付分支`codex/m8-upgrade`的Actions记录为准。独立MySQL8.4.13使用3307。公网部署、学校格式论文终稿与PPT待目标环境和模板，详见[项目进度](PROJECT_STATUS.md)。
