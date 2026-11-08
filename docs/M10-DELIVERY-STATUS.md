# M10 交付状态

截止：2026-11-10 23:59，Asia/Shanghai。

## 启动基线（2026-11-07）

- 分支 `codex/m9-delivery`，SHA `712be1d`（M9 代码 `093eaef` 之后仅 CI 触发提交）。源码干净，用户新增资料包保留，不自动提交或推送。
- 已完整阅读 docs/m10/README.md 与 M10-IMPLEMENTATION-PROMPT.md，已逐张查看六张 references。
- Codex 本机配置核对 `gpt-6.1-sol/ultra`；这表示配置值，不单凭文件声称运行时内部模型已验证。
- DSH config/probe 核对 `buddy/deepseek-v4.1-flash/max`，route_callable/tool_call_verified 均为 true；导航任务 `hub-2-mw5uccvg` 已启动。
- 设计：保留 M9 Token（浅色 #f9f7f2/#fffdf8/#145c45，深色 #141e1a/#1c2922/#9bcdb2）及中西文字体；参考图决定单行导航、分组抽屉、右上浮层、两行身份；首页内容不重做。
- 实施计划：docs/m10/IMPLEMENTATION-PLAN.md。

## 当前验收

M10 P0 和无来源 IP 属地安全降级已完成编码、自动测试、真实浏览器检查及本机开发库增量迁移。可靠 GeoIP 来源尚未提供，P1 的真实省级/国家级解析未启用。2026-11-08 用户明确授权提交并推送；本次发布结果与远端 CI 以该次 Git 提交、GitHub Actions 记录为准。外部部署未执行，M9 原有真机验收待办保留。

| 检查 | 实际结果 |
|---|---|
| `python3 scripts/backend.py package` | 249 测试，0 失败/错误/跳过；JAR 打包通过 |
| `npm --prefix frontend test` | 34 文件，337 测试通过 |
| `npm --prefix frontend run build` | vue-tsc 与 Vite 生产构建通过 |
| `npm --prefix mobile run type-check` | 通过，mobile 源码/布局无 diff |
| `npm --prefix mobile run check:safety` | 16/16 通过 |
| `npm --prefix mobile run build:h5` | 通过 |
| `npm --prefix mobile run build:mp-weixin` | 资源构建通过，非真机验收 |
| `npm --prefix mobile run build:app` | 资源构建通过，非签名包/真机验收 |
| `python3 -m unittest discover -s scripts/tests` | 83/83 通过 |
| Chromium 真实浏览器 | 24 组布局、全部记录的交互检查通过，未捕获脚本异常 |
| 开发库和上传保护 | 14 表、133 行原列 SHA256 一致；44 文件 SHA256 一致；历史属地全 NULL |

uni-app 构建输出提示未配置 Appid/uni statistics；不影响本轮资源编译，继续保留 M9 真实微信/Android/签名包待办。后端最初专项测试在沙箱无法绑定端口，已在允许本机端口的执行环境重跑；非业务失败未记作通过。测试 RED/GREEN、Mockito 测试桩重设和旧字段计数契约断言修正均已完成。

## Web 实测与截图

使用新的 18080 后端和 15173 Web，连接开发库的独立恢复克隆；不在原开发数据中制造测试用户、评论或属地。截图里的账号/评论属于隔离验收数据。原有默认服务未替换，需要重启原 8080 后端加载 M10 JAR。

- 360、390、430、768、1024、1440px × 浅/深色 × 匿名/已登录，共 24 组；均为 64px 单行，无横向溢出，操作目标均在视口内。768–1023px 减少一个快捷胶囊。
- 抽屉：当前路由高亮、背景 inert/滚动锁与恢复、路由选择关闭、Escape/焦点返回。
- 设置：浅/深/系统三态、刷新持久、系统主题实时变化、面板本身 Shift+Tab 焦点循环、减少动效按 M9 的 0.01ms 规则核验。
- 账号：长昵称/用户名截断且保留 title、无头像回退、面板互斥、外点关闭、真实退出、503 退出失败仍保留登录并可重试。旧退出响应不能清掉新账号会话。
- 评论：公开用户名、未知属地、作者 ID 标记、纯文本 XSS 展示、失败保留文本、发送/删除、真实服务端分页；旧治理、草稿、权限和竞态由后端/组件测试回归。
- 管理入口：浏览器用临时响应夹具检查 ADMIN 可见性，同时对真实 USER 请求管理 API 验证 403；没有因此提升数据库账号权限。

| 实测截图 | 路径 |
|---|---|
| 桌面深色单行 | [1440px](m10/screenshots/desktop-dark-1440.png) |
| 手机浅色匿名 | [390px](m10/screenshots/anonymous-light-390.png) |
| 手机分组抽屉 | [390px](m10/screenshots/drawer-dark-390.png) |
| 手机显示设置 | [390px](m10/screenshots/settings-dark-390.png) |
| 手机账号与长身份 | [390px](m10/screenshots/account-dark-390.png) |
| 评论与属地降级 | [390px](m10/screenshots/comments-dark-390.png) |

机器证据：[浏览器矩阵](verification/2026-11-07-m10-browser.json)、[数据保护](verification/2026-11-07-m10-data.json)。复跑浏览器脚本为 `docs/m10/BROWSER-ACCEPTANCE.js`，仅用于具有测试副作用的隔离克隆，不能直接对生产运行。

最终本地结果汇总为 [M10 local](verification/2026-11-07-m10-local.json)。已验证构建产物与源码组装为 `.local/releases/m10-local-20261107/`，同名 ZIP 及独立 artifact JSON 保存文件数、ZIP SHA256；目录内 `SHA256SUMS` 可逐项核验。不含 `.env`、本机 client 凭据、数据库备份、真实 uploads、node_modules 或 Git 历史；uploads 只带 .gitkeep。不重新执行已通过的构建，而用交付工具的受控 assemble 组装并校验现有产物。

本轮以 `finishing-a-development-branch` 的验证和保留工作区方式收尾，遵守用户不自动提交规则，未合并/commit/push。已知本机凭据及私钥模式的脱敏扫描命中 0，具体范围见敏感扫描证据；该扫描不声称是完整渗透测试。

## 具体修改与审查

- Header/辅助 Brand、Icon、overlay composable：严格一行、左抽屉和两种右上面板；复用 M9 Token/ThemeStore，无重量 UI 依赖。
- UserIdentity、GuideCard、GuideDetail、Profile、GuideComments：统一昵称和灰色 @username，保留旧 API/评论流程。
- PublicProfileVO：仅新增 username，注销作者 NULL；Guide/Comment 实体及 VO 新增可空 ipRegion，DTO 不接受属地输入。
- ClientIpResolver / RequestIpRegion / IpRegionResolver：真实请求边界、安全代理链与离线可插拔接口，默认未知。
- `V010_01__ip_regions.sql`、manifest-m10、m10_backup/m10_migrate：受控备份与恢复、增量迁移；新空库 schema、API/数据库文档与交付白名单同步。
- Codex 侧独立只读审查发现并补测修复：已关闭浮层后路由变化抢焦点、面板本身 Shift+Tab 逃逸、迁移缺目标表导致可避免的半迁移；768px 风险另由真实浏览器核实并收紧快捷项。
- 追加的新备份脚本独立审查因辅助代理额度耗尽未返回结果，不计作通过；主代理完成源码复核，并实际执行该脚本的备份/独立恢复与原数据哈希校验。

## DSH 实际贡献与阻塞

**DSH 本轮没有成功执行编码或文件审查，不计虚假贡献。** `hub-2-mw5uccvg` 与用户调整列表后重验的 `hub-3-mw5us06d` 均为 `buddy / deepseek-v4.1-flash / max`，最终 failed、toolCalls=0、tokens=0。原始错误：`agent/request proposed reasoning effort "max" after step preparation fixed "adapter default"; use agent.model.select() before step preparation`。

独立核对配置与已安装静态适配器：buddy 对应 CodeBuddy，workbuddy 是另一个独立渠道；没有改用 WorkBuddy、high/off 或其他 CLI。probe 通过只说明该次小请求可调用，不能替代正式 Max worker 成功证据。M9 会话协助排查也独立复现同一错误，未验证修复。当前工具未暴露修复入口，主代理完成其余实施和验收，不修改全局模型配置。

## 数据安全与 IP 决策

- 先备份/恢复并验证测试库，再验证新开发数据克隆，最后对已核对的本机 travel_platform 开发库增量升级；未操作生产库，未重跑 schema.sql。
- 没有已核验且获授权的 GeoIP 数据来源时使用 NULL/“未知”。无第三方 IP 上报、无 GPS、无原始 IP 业务列；不按用户资料回填。
- 属地与首次 publishedAt 同步冻结，首次未知也冻结；撤回、再发布、编辑和后台治理不改写。
- 真实测试覆盖可信/不可信代理、畸形 XFF、IPv4/IPv6/mapped、回环/私网、无来源/异常降级、客户端伪造字段拒绝、并发首发只采集一次，以及编辑/撤回/治理保留历史快照。已知“四川”等仅来自测试替身，不作为真实地域来源。
- 正式来源/版本：无；当前无外呼、无 IP 结果缓存、无原始 IP 业务存储。后续只有取得可审计授权离线来源才启用真实地域解析。
- 备份恢复与迁移步骤、已执行目标及可恢复策略见 [MIGRATION.md](m10/MIGRATION.md)。未来恢复优先保留可空新列回退旧代码；完整 SQL 恢复只在新克隆验证，不自动覆盖源库。

## 截止前保留事项

- GeoIP 来源/许可和未来离线实现仍需实际数据源；未知降级属于任务书允许的交付。
- DSH 正式 Max worker 运行时问题尚未修复；不影响主代理已完成的本地编码测试。
- 11-07 本地交付时未提交/推送；11-08 用户已明确授权提交并推送到当前分支，远端 CI 将由 push 触发，结果以实际运行记录为准。外部部署未执行。
- M9 微信/Android 真机、签名安装包、真实收费 AI 提供商验收范围保持原记录，未冒充完成。

## 2026-11-08 提交前复验与发布范围

后端 249/249、Web 337/337、Python 83/83、移动安全 16/16、Web 生产构建与 mobile 类型检查重新通过。无 uni-app 源码修改。此次采用已有 noreply 作者身份且仅对 Git 命令生效；不改全局配置，不强推，不合并 main。

提交包含 M10 源码、增量迁移、测试、文档、六张用户参考图及六张脱敏验收截图；原始 ZIP 保留在本机提交之外。旧的 11-07 验收 JSON、ZIP 仍表示当时的本地快照，不冒充包含本次发布说明。最新提交前摘要见 `docs/verification/2026-11-08-m10-prepush.json`；最终 commit SHA / push / CI 结果以实际 Git 与 GitHub 记录为准。

## 2026-11-08 main 整合

M10 功能提交 `a12f0547db11e4e21bc3ffaeac655f959b48f48b` 已推送，Actions [42458907128](https://github.com/TheSixPasserby/travel-platform/actions/runs/42458907128) 真实结果为 success。随后用户授权“合并所有分支到main”。

重新 fetch 与逐分支祖先检查确认：M8 分支 `56052b5`、M9/M10 分支 `a12f054` 和原 main `864ea1c` 构成一条完整祖先链。因此 main 直接快进到 a12f054，即包含所有本地/远端分支成果，无需制造重复合并提交。快进后的 tree 与已通过 CI 的功能提交一致；仅追加这次整合文档/证据，不修改业务源码或数据库。

源分支保留、不强推、未触及原始 ZIP 和本地数据；当前工作分支为 main。main 推送后的 SHA 与 CI 状态以实际 Git/GitHub 记录为准。整合前提与验证见 `docs/verification/2026-11-08-main-integration.json`。
