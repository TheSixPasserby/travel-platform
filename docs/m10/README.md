# M10 · 单独设计参考图与 Codex 实施包

这是供 `TheSixPasserby/travel-platform` 使用的 **M10 独立设计稿**。请从项目根目录解压本包；最终目录应为 `travel-platform/docs/m10/`。只新增该目录，不覆盖现有前后端源码。

## 资料清单

| 文件 | 用途 | 主要检查点 |
|---|---|---|
| `M10-IMPLEMENTATION-PROMPT.md` | Codex 总实施指令 | 需求、权限、迁移、安全、测试和截止时间 |
| `references/01-desktop-single-row-header.png` | 桌面顶栏 | 左侧汉堡 + Logo，中间 3～4 个快捷胶囊，右侧设置和头像；严格一行 |
| `references/02-mobile-navigation-drawer.png` | 移动左侧导航 | 抽屉遮罩、分组、当前项高亮、底部帮助；背景滚动锁定 |
| `references/03-mobile-settings-popover.png` | 设置弹层 | 浅色/深色/跟随系统，原有主题状态复用；弹层互斥、Esc/点击外部关闭 |
| `references/04-mobile-account-popover.png` | 账号弹层 | 头像、昵称为主，灰色 `@username` 为次；个人页、收藏、行程、退出 |
| `references/05-guide-comments-ip-region.png` | 攻略与评论 | 作者身份、用户名、时间、作者标记和粗粒度 IP 属地的视觉层级 |
| `references/06-mobile-signed-out-light.png` | 浅色匿名状态 | 右上角清晰登录按钮，主题响应，单行顶栏 |

## 设计与工程边界

- 全部图片为**静态设计示意**，不是当前 Web 的真实运行截图，也不意味着图中地点、用户、评论或 IP 属地属于真实业务数据。
- 品牌使用项目自己的“山水间”和绿色主题，参照用户提供的 Google Travel 截图仅提取结构与交互逻辑，**不复制 Google 品牌图标**。
- 设计图示例用了深色界面；**实际必须同时适配浅色、深色与跟随系统**，优先复用 M9 已有设计 Token 和 ThemeStore。
- 顶栏、抽屉、设置和账户弹层是本轮必须实现的交互。设计图中的旅途卡片、山峰插画仅用于展示顶栏与内容区的关系，不是要重做首页。
- 不要求实现图中未在实施指令里列出的功能。**尤其不要因为展示示例而擅自增加二级回复或评论点赞。**
- `username` 和 `nickname` 已在数据库存在，**不要重复添加字段**；新增公开作者信息时保持向后兼容。
- IP 属地仅用于粗粒度公开展示，服务端安全解析；旧数据不得伪造属地，未知为正常降级。
- **暂不改造 uni-app 页面布局**，只做 API/类型向后兼容构建。

## 在 Codex 中使用

打开原项目根目录，发送：

> 请完整阅读 `docs/m10/README.md` 与 `docs/m10/M10-IMPLEMENTATION-PROMPT.md`，查看 `docs/m10/references/` 的六张独立参考图。保持 Codex GPT-6.1-Sol / Ultra 主代理与 DSH DeepSeek V4.1 Flash / Max 分工。立即按 M10 优先级实现单行导航、抽屉与两种浮层、昵称+@用户名、评论界面和 IP 属地，保护 M8/M9 数据；uni-app 布局先不动。截止 2026-11-10 23:59（北京时间）。不要只写规划，请直接实施、测试和验收。
