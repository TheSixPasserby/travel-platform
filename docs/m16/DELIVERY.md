# M16 本轮交付与真实验收

本页为历史阶段记录，日期、测试数量和当时范围按原记录保留。当前使用与开发入口见 [项目文档](../README.md)。

2026-11-13，基于 `main@9a6261c47695131bee399c8d373d1cc5ff55c630`。起点 fetch 后 `origin/main` 相同；终点仍为同一 HEAD，代码保留在本地工作区。本轮完成实施包 P0 四项闭环，P1 评论回复、提及、通知及服务端分享元数据继续列为未完成。没有自动提交、推送、发布或部署。

## 可运行结果

1. 全站入口搜索真实公开攻略、城市、景点、作者、话题；有建议/本地历史、相关性/最新、类型分组、游标继续加载、URL 恢复和空错态。
2. 收藏页和攻略详情使用同一地点抽屉，选择真实关联景点，加入本人的已有日期或原子新建行程。确认才写入，重复地点返回跳过数，并发/重复 UUID 不产生重复项目。
3. 用户所选旅行灵感进入 AI 需求表单，偏好与必去项仍可编辑。预览、修改、确认保存分开；刷新后明确恢复草稿。AI 关闭时同一批景点可直接走手动行程。
4. 信息流与搜索卡片读取受控缩略图。原图、M14 查看/下载和原内容安全渲染继续保留。作者和话题搜索能到真实公开页面，原分享复制链路复验通过。

本机独立验收入口：[Web 15179](http://127.0.0.1:15179)，后端 18086，默认 AI 关闭。浏览器写操作使用独立数据库和上传副本，不使用原业务库。既有默认 5173/8080 服务由原启动方式管理。

实际 API、权限、幂等和迁移说明见 [API.md](API.md)，分阶段过程见 [IMPLEMENTATION-LOG.md](IMPLEMENTATION-LOG.md)。离线 Web-only 源码和构建产物目录为 `.local/m16/delivery/`，其 SHA256SUMS 覆盖交付文件；真实上传、数据库备份和凭据仅留本机忽略目录，换机需要操作者准备自己的数据库、受管素材与配置。

## 测试结果

| 验证 | 实际结果 |
|---|---|
| `bash scripts/m16-checks.sh frontend` | 59 文件，541/541；Vue TypeScript 与 Vite 构建通过 |
| `python3 scripts/backend.py package`，明确隔离 MySQL | 277/277，0 失败/错误/跳过，Java 21 JAR 构建成功 |
| `python3 -m unittest discover -s scripts/tests` | 93/93，通过；新增 M16 离线包入口/脚本覆盖 |
| `python3 scripts/m16-verify-package.py` | 原始包 31 文件 SHA256 一致 |
| 搜索 Chromium | 真实五类型、分类/排序/刷新/后退、空态、失败重试、键盘建议、作者/景点/话题落地通过 |
| 行程 Chromium | 收藏进抽屉、取消零写入、确认新增两地、刷新持久化、再次加入跳过两地、AI 关闭手动路径通过 |
| AI Chromium | 本地合成 HTTP 模型夹具完成真实前后端链路：偏好/必去参数、预览零写入、编辑、刷新恢复、明确确认、一次真实原子导入和保存后刷新通过 |
| 权限/事务边界 | 匿名、跨账号、非关联/停用/隐藏源、并发、UUID 改内容、容量失败全回滚、逻辑删除后的回执重放均有后端 HTTP 测试 |
| 既有行为 | 20 篇无限流去重、公开作者/话题、灯箱缩放/旋转/适应/ESC、真实复制分享与原图下载通过；完整原套件通过 |
| 源码与数据保护 | M15 主题运行时/设置/样式、M14 核心与归档 uni-app 指纹一致；原业务十四表 290 行、44 上传前后一致 |

原始日志保存在忽略目录 `.local/m16/`：`final-frontend-checks.log`、`backend-reviewed.log`、`final-python.log`。公开浏览器机器证据位于本目录 [evidence/](evidence/)，不包含密码、令牌或 AI 密钥。后端新增 12 个测试方法覆盖多个断言；不将断言数充作测试数。

外部收费模型调用、本轮远端 CI、Safari、Firefox、物理手机和真实硬件输入法未执行。IME 组合事件有组件测试。AI 启用截图使用明确标注的本地合成 HTTP 供应商，不能据此宣称外部模型质量或供应商兼容性。验收结束后该夹具已停止，独立后端恢复 AI 关闭。

## 真实截图与参考图检查

搜索、地点抽屉和 AI 关闭工作台分别完成 360/390/430/768/1024/1440px × 浅深色 × 两种自定义色（`#126899/#b64b82`）各 24 组检查，无页面横向溢出；抽屉同时检查面板滚动、body 锁定与取消/ESC 恢复。代表截图均为实际 Chromium 运行页面。参考图保持独立，没有作为完成截图。

| 参考图 | 实际截图 | 视觉核对与有意差异 |
|---|---|---|
| `01-search-desktop.png` | [桌面山水绿](evidence/search-1440-pine.png)、[自定义浅色](evidence/search-1440-light.png)、[深色](evidence/search-1440-dark.png) | 保留 M13 侧栏、宽搜索框、类型导航和内容分区。使用真实数据密度与 M15 色调，没有照搬概念稿数字或作者 |
| `05-search-mobile.png` | [390 浅色](evidence/search-390-light.png)、[390 深色](evidence/search-390-dark.png) | 攻略两列，目的地/作者为紧凑行；类型可横滑，沿用手机 Web 底部导航，未放入桌面固定侧栏 |
| `02-article-add-trip.png` | [桌面抽屉](evidence/trip-1440-light.png)、[手机深色](evidence/trip-390-dark.png)、[成功回执](evidence/trip-saved-1440.png)、[保存后详情](evidence/trip-detail-1440.png) | 右侧抽屉、真实地点多选、目标/日期与确认操作；手机占满宽度并让正文独立滚动。没有从正文猜地点或制作假路线图 |
| `03-ai-itinerary-workspace.png` | [启用预览 1440](evidence/ai-workspace-http-fixture-1440.png)、[启用预览 390](evidence/ai-workspace-http-fixture-390.png)、[已保存](evidence/ai-saved-http-fixture-390.png)、[关闭手动入口](evidence/ai-manual-390.png) | 复用现有完整行程编辑器与真实景点数据，补来源 chip、明确恢复和手动入口。截图说明本地 HTTP 合成模型，不预造概念稿的三天内容 |
| `04-notifications-interactions.png` | 未实施，无完成截图 | P1 需要独立回复/提及/通知模型、频控与治理契约；本轮未增加空入口或假未读数 |
| 两张 `00` 总览图 | 上述流程截图与 [信息流](evidence/feed-variant-1440.png) | 检查发现→选择地点→私人行程/AI 的连贯性；使用既有 M15 主题和布局，未将全部总览示意模块宣称完成 |

页面已检查真实布局、文字层级、卡片比例与移动滚动；抽屉末轮补齐 44px 触控区域，并重新验证24组。搜索和抽屉保留加载、失败、空态，非必需动效遵从 reduced-motion，图片失败可回退。机器记录见 [搜索](evidence/search-browser.json)、[行程](evidence/trip-browser.json)、[AI](evidence/ai-workspace-browser.json)、[登录返回及工作台矩阵](evidence/final-capture-browser.json)、[触控尺寸](evidence/touch-browser.json)、[既有行为](evidence/regression-browser.json)、[数据保护](evidence/data-protection.json)。

## 图片性能对比

采用同一当前代码、同一后端和四张相同图片。原图模式仅在浏览器请求层移除 `width`；缩略图模式使用正常 srcset，服务端缓存可能已预热。1440×1000、DPR 1、浏览器缓存关闭、40ms 延迟、下载 1,250,000 B/s、上传 625,000 B/s，各模式三次。

| 指标 | 原图 | 受控缩略图 |
|---|---:|---:|
| 首屏四图响应字节，每次均相同 | 1,191,634 | 80,718 |
| 自然解码宽度 | 1280 | 320 |
| 实际卡片图宽 | 275 | 275 |
| LCP 中位数 | 3276ms | 2048ms |
| CLS 最大值 | 0.0004845 | 0.0004845 |

字节减少 **93.23%**。这是本机开发模式配对样本，不能推断生产环境通用得分。未运行 Lighthouse 综合评分。[完整机器记录](evidence/performance.json)、[同条件原图截图](evidence/feed-original-1440.png)、[缩略图截图](evidence/feed-variant-1440.png)。

原图下载 247,081 字节，与上传原文件 SHA256 相同；[灯箱截图](evidence/lightbox-original-1440.png)。缩略图测试同时覆盖透明 PNG、非法宽度、路径/符号链接边界、失败缓存恢复和 JPEG EXIF 八方向。

## 数据、模型与 DSH 实际贡献

- 起始已跟踪源码干净，六项未跟踪输入保留。全部文档和七张参考图已读取；原六份实施文档与包校验清单未改，进展记录在独立文件。
- 原业务库只做只读指纹/备份；浏览器使用恢复副本，迁移仅增加隔离副本和测试库的回执表。原十四表结构/290 行/44 上传与起始指纹一致，根 `.env` 前后哈希一致。原 AI 私有配置未由本轮修改；它没有在起点采集单独哈希，因此不把它列为前后哈希证明。验收运行始终使用独立 AI 设置路径。
- M15/M14 保护文件和归档目录比较见 [protected-source.json](evidence/protected-source.json)。无需恢复移动构建。未引入新运行依赖、微服务、Redis 或搜索引擎。
- Codex 会话元数据为 `gpt-6.1-sol / ultra`，DSH config/probe 和实际作业为 `buddy / deepseek-v4.1-flash / max`。两者分别符合分工，没有改全局模型配置。
- DSH `hub-27` 实际交付 `SearchView.vue`；`hub-28` 实际交付 `AddToTripDrawer.vue` 及抽屉测试；`hub-29` 实际修改 `AiItineraryForm.vue`。Codex 审核 diff，修正竞态、恢复/幂等边界、导航与选择意图，补齐契约、测试、截图和后端实现。末轮 `hub-31-mwe2nono` 已实际修改抽屉六处触控 CSS；补丁完成并通过主代理浏览器/完整前端验收后结束作业，工具最终状态为取消，没有最终文字答复，不据此虚报子代理测试。
- `hub-24` 广范围搜索任务和 `hub-26` 搜索框任务停滞，取消时无代码，不计贡献；Codex 接管 `SearchBox` 及集成。`hub-29` 在测试目录遇到权限围栏后停止，Codex 在自己的授权范围内接管测试，没有让子代理绕过。只读审查 `hub-25` 与 `hub-30` 已回收；后者提出历史回执、失败缓存和 EXIF 方向三项，均已修复并验证。

## 未完成与后续范围

P1 回复层级、@ 提及、真实事件通知、未读/已读/定位、通知用户隔离尚未实施，也没有增加假按钮。需要下一阶段独立设计、迁移、频控/治理与完整跨账号验收。现有一级评论和评论抽屉继续保留。

现有公开作者页、话题发现和真实复制/Web Share 链路保留；新增创作者足迹/分区与服务端公开分享 HTML、OG/canonical/SEO 未实现，不能把 SPA 客户端页面当完整爬虫方案。原业务库未应用 M16 增量迁移，正式切换前仍需操作者明确目标并执行备份/恢复验证后的增量文件。
