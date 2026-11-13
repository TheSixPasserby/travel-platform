# 山水间

旅游攻略分享与私人行程规划平台，采用 Vue 3、TypeScript、Spring Boot 和 MySQL。用户可以发布图文手记、查找目的地、交流旅行经验，并将攻略中的真实关联景点加入自己的行程。

## 当前功能

- 账号注册、登录、资料编辑和资源所有权校验。
- 图文攻略的草稿、发布、编辑、撤回与管理，支持标签、图库及所见即所得编辑。
- 瀑布流、游标连续加载及攻略、城市、景点、作者、话题的跨类型搜索。
- 点赞、收藏、两级评论、显式用户提及、站内通知和评论定位。
- 城市与景点资料、公开创作者主页及作品关联目的地。
- 私人行程、每日项目和排序，攻略景点创建或追加行程。
- 可选 AI 预览、编辑、单日重生成和确认保存，服务关闭时可手动规划。
- 图片缩略图、原图查看和下载，明暗主题与自定义配色。
- 用户、内容、目的地和 AI 设置后台，公开分享页与基础 SEO 元数据。

维护范围为 Web 和后端，支持桌面及手机浏览器。移动应用的历史源码和材料位于 [归档目录](deprecated/uni-app/README.md)。

## 本地运行

准备 Java 21、Node.js 24、MySQL 8.x，以及相互独立的业务库和测试库。

```bash
cp .env.example .env
# 按本机环境填写 .env 中的数据库连接和独立测试库
npm --prefix frontend ci
./start.sh
```

首次建库使用 [数据库说明](database/README.md)。既有库升级采用增量迁移。当前新空库结构包含 18 张表，已有 M15 结构的库需要依次应用 V016_01 和 V017_01。启动器只启动服务，迁移由操作者明确目标后执行。

默认 Web 端口为 5173，后端为 8080。启动器会打印实际本机与局域网地址，并核验 Web 来源的 CORS 预检。AI 是否可用取决于服务端配置，管理员可在 `/admin/ai` 设置。

## 构建与测试

```bash
python3 scripts/backend.py package
npm --prefix frontend test
npm --prefix frontend run build
python3 -m unittest discover -s scripts/tests
```

后端测试要求 `DB_TEST_NAME` 以 `_test` 结尾且与业务库不同。生产交付目录由 `python3 scripts/release.py 输出目录` 生成，输出目录须尚不存在。

最近的 M17 本地验证为后端 284 项、前端 573 项、Python 93 项全部通过，类型检查和构建通过。执行环境与范围见 [项目状态](PROJECT_STATUS.md) 和 [M17 测试报告](output/m17/项目P1完成情况与真实测试报告.md)。

## 文档与论文

| 需要完成的工作 | 文档 |
|---|---|
| 查找全部项目说明 | [文档索引](docs/README.md) |
| 安装与调试 | [本地开发](docs/14-local-development.md) |
| 理解实现和数据关系 | [架构](docs/02-system-architecture.md)、[数据库](docs/05-database-design.md)、[API](docs/06-api-contract.md) |
| 使用和演示 | [使用手册](docs/23-user-manual.md)、[演示流程](docs/24-demo-walkthrough.md) |
| 部署、备份与迁移 | [运维说明](docs/22-deployment-and-operations.md) |
| 核查毕业论文 | [论文修改记录](output/m17/论文修改记录与人工确认事项.md) |
| 查阅阶段记录 | [历史索引](docs/history/README.md) |

论文成果位于 [output/m17](output/m17/论文修改记录与人工确认事项.md)，包含可编辑 DOCX、PDF 检查版、文献来源清单及测试报告。封面个人字段由提交者填写。

## 目录

```text
backend/       Spring Boot 业务源码和测试
frontend/      Vue Web 源码和组件测试
database/      当前空库结构、示例数据与增量迁移
scripts/       启动、构建、校验与维护工具
deploy/        反向代理示例
docs/          当前说明和阶段记录
output/m17/    论文与交付材料
deprecated/    历史移动端
```

开发规范见 [AGENTS.md](AGENTS.md)，协作职责见 [协作流程](docs/12-codex-dsh-workflow.md)。
