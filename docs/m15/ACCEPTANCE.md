# M15 全站视觉与主题色验收

2026-11-12，基于本地 `main@e679e3e` 与已经完成的 M14 工作区增量实施。执行范围为本地 Web 开发、构建和 Chromium 验收。

## 已落地功能

- 共享基础、表面、文字、边界、圆角、空间、阴影、动效和焦点 Token。旧 Tailwind `pine`、`cream`、`ink` 等颜色通过别名接入，发现页、阅读、评论、主页、编辑器、目的地、AI、资料及后台共同使用。
- 六个预设山水绿、海岸蓝、暮霞紫、落日橙、樱花粉、石墨灰。自定义严格 `#RRGGBB` 与原生取色器即时应用，设置浮层提供实际样式预览。
- 明暗模式和色调独立。沿用 `travel-notes.theme`，色调存入 `travel-notes.accent.v1`。刷新、跨标签页、系统外观变化、无效存储和恢复默认均有验证。恢复默认清理非法输入并保留明暗选择。
- 一个同步 `public/theme.js` 同时承担首屏和 Pinia 调色运算。色调参与页面背景、内容表面、弱强调面、链接、控件、标签、焦点和边界。种子色经过对比度推导，成功、警告、危险及后台审核状态有独立语义颜色。
- 阅读、创作者页和发现页采用一致的衬线标题。后台导航、表单、卡片和浮层收敛到共享尺寸与层级。Vditor 使用实际支持的主题变量覆盖工具栏、编辑区、源码与面板，三种编辑模式保留。
- M14 灯箱使用中性深色观图背景和独立可读的浅色交互强调色。变换、下载和手势代码保留起始版本。既有原创插画、照片、头像和内容封面继续沿用原素材。

可选的色调强度选项留待后续。本轮采用标准强度，色调偏好仅在当前浏览器保存。

## 截图对照

[修改前矩阵](evidence/before-matrix.json) 为 17 区域 × 3 宽度 × 浅深色的 102 张真实截图。[修改后矩阵](evidence/after-matrix.json) 为 22 区域的 132 张截图，[补充尺寸矩阵](evidence/extra-widths.json) 增加 132 组检查，覆盖全部 360、390、430、768、1024、1440px。

| 区域 | 修改前 | 修改后 |
| --- | --- | --- |
| 发现页，浅色桌面 | [前](evidence/before/discovery-light-1440.png) | [后](evidence/after/discovery-light-1440.png) |
| 攻略阅读，深色桌面 | [前](evidence/before/article-dark-1440.png) | [后](evidence/after/article-dark-1440.png) |
| 评论抽屉，深色桌面 | [前](evidence/before/comments-dark-1440.png) | [后](evidence/after/comments-dark-1440.png) |
| 创作者，深色手机 | [前](evidence/before/creator-dark-390.png) | [后](evidence/after/creator-dark-390.png) |
| 编辑表单，浅色桌面 | [前](evidence/before/editor-light-1440.png) | [后](evidence/after/editor-light-1440.png) |
| 目的地，浅色桌面 | [前](evidence/before/destinations-light-1440.png) | [后](evidence/after/destinations-light-1440.png) |
| AI 行程，深色手机 | [前](evidence/before/ai-dark-390.png) | [后](evidence/after/ai-dark-390.png) |
| 个人资料，浅色桌面 | [前](evidence/before/settings-light-1440.png) | [后](evidence/after/settings-light-1440.png) |
| 后台 AI，浅色桌面 | [前](evidence/before/admin-ai-light-1440.png) | [后](evidence/after/admin-ai-light-1440.png) |
| 图片查看器，浅色手机 | [前](evidence/before/lightbox-light-390.png) | [后](evidence/after/lightbox-light-390.png) |
| 显示设置，浅色手机 | [前](evidence/before/appearance-light-390.png) | [后](evidence/after/appearance-light-390.png) |

六个预设各有浅深色桌面截图，保存在 `evidence/phase2/`。例如 [海岸蓝浅色](evidence/phase2/ocean-light-1440.png)、[暮霞紫深色](evidence/phase2/violet-dark-1440.png)、[落日橙浅色](evidence/phase2/sunset-light-1440.png)、[樱花粉深色](evidence/phase2/rose-dark-1440.png)、[石墨灰浅色](evidence/phase2/graphite-light-1440.png)、[山水绿深色](evidence/phase2/pine-dark-1440.png)。

## 两轮视觉审查与修正

第一轮发现 360px 选中色卡名称被“已选”挤成多行，非法 HEX 后点击预设或恢复仍残留输入错误。选中标识改为独立角标，名称保持一行，显式选择及恢复同步清理输入。三个对应测试先失败、修复后通过。[第一轮截图](evidence/review-round1/appearance-dark-360.png) / [修正结果](evidence/phase2/appearance-dark-360.png)。

第二轮收敛浮层表面、圆角和阴影，增加设置控件的强边界，后台导航补回标题前的空间，审核状态改用独立语义色。核对 Vditor 包内变量后，修正原 `--toolbar-background` 名称并在实际 `.vditor` 节点覆盖浅深主题，补充源码色阶。[第二轮面板检查](evidence/appearance-review-round2.json)、[编辑器三模式](evidence/editor-browser.json)、[审核颜色检查](evidence/semantic-browser.json)。

DSH 独立审查另指出纯黑、纯白按钮的悬停与按下色可能相同。两项测试复现失败后，改为在端点色向内偏移。极端种子仍保留对比度及三种独立交互颜色。

## 执行结果

| 检查 | 实际结果 |
| --- | --- |
| Phase 0 基线 | 49 文件、457 项前端测试通过，149 文件源码快照保存在忽略目录 `.local/m15/` |
| Phase 1 | 18 项相关测试、类型及构建通过，12 张发现/阅读/设置浅深色阶段截图 |
| Phase 2/3 | 主题、输入与审查回归落地，完整前端最终 51 文件、493/493 通过 |
| `npm --prefix frontend run build` | Vue TypeScript 与 Vite 生产构建通过 |
| `python3 -m unittest discover -s scripts/tests` | 92/92 通过，包含 Web-only 与 release 组装测试 |
| 全站布局 | 264/264 组通过，未发现根节点横向溢出，矩阵未捕获页面脚本异常 |
| 主题专项 | 49 项浏览器断言通过，6 预设 × 3 明暗模式，3 自定义 × 浅深色，5 非法输入、持久化、真实跨标签、系统、首屏、清空/无效存储、弹层、键盘与 Reduced Motion。[记录](evidence/theme-browser.json) |
| 对比度 | 8192 组浅深色调色板抽检。正文/链接最低 4.838，按钮文字最低 5.530，强边界最低 3.000。[记录](evidence/palette-contrast.json) |
| Vditor | 桌面/手机 × 浅深色 × 三模式，12 组内容无损与布局检查，另有 4 组主题切换内容保持检查，均通过。[截图示例](evidence/after/editor-ir-dark-390.png) |
| M13 | 501 篇长列表，DOM 峰值 96、末尾 17，末条可达，焦点留在列表，未溢出。[记录](evidence/m13-long-feed.json) |
| M14 | 10 组尺寸矩阵、22 鼠标/键盘、8 CDP 原生触摸、9 异常/安全边界通过。[矩阵](evidence/m14/matrix.json)、[交互](evidence/m14/interactions.json)、[触摸](evidence/m14/touch.json)、[异常](evidence/m14/edges.json) |
| 原图下载 | 237847 字节，SHA256 与授权原图相同，[下载核验记录](evidence/download.json) 中的值为 `38c69c6a4a8939bec095227dabd35de4b3142badd4f12148c3264a8ad1a3adba` |
| 行为保全 | M13 瀑布流引擎/布局、M14 变换/下载/工具栏/测试的完整哈希一致，阅读/评论/主页/Vditor/M14 的脚本与模板前缀一致。[记录](evidence/preservation.json) |

最终命令摘要见 [TEST-RESULTS.txt](evidence/TEST-RESULTS.txt)。性能样本单独保存在 [performance.json](evidence/performance.json)，用于本次运行观察。

## 协作与文件

本会话元数据为 Codex `gpt-6.1-sol / ultra`。DSH config、probe 与实际任务均为 `buddy / deepseek-v4.1-flash / max`。

DSH 实际贡献 `AppearanceSettings.vue`、`SiteHeader.vue` 和 `AdminNavigation.vue` 初稿。初始只读审计及全局 CSS 任务长时间无补丁后取消，由主代理接管。设置组件任务完成两个实际文件后仍未产出计划中的测试文件，取消后由主代理保留代码并补齐测试与两轮视觉修正。主代理维护同步引擎、Pinia、Token、跨组件样式与最终验收。[任务状态及实际贡献](evidence/DSH-CONTRIBUTIONS.json)、[M15 独立修改清单](evidence/M15-CHANGED-FILES.json)。

## 验证范围与保留问题

截图使用完整 Vue 页面和只读 API 夹具，所有写请求均被阻断，照片来自项目既有授权素材副本。业务数据库、上传目录和私有 AI 配置不参与夹具。浏览器截图中的用户、文章、统计和后台 API 地址为视觉测试数据。后端、数据库结构、接口、依赖清单、CI 和归档 uni-app 的文件 diff 为空。

开发模式下，完全拒绝 `window.localStorage` getter 会触发 Pinia 所带 Vue DevTools 的模块初始化异常。调用栈定位到 `getTimelineLayersStateFromStorage`，早于应用挂载。生产构建在本机 15179 端口完成同样的完全禁用存储检查，主题和明暗仍可操作。此开发依赖限制保留记录，应用主题的存储异常处理已有单元及生产浏览器验证。另补充了禁用存储后收到跨标签事件的回归，先复现异常，再通过缓存可用 Storage 引用并忽略不可用区域事件修正。

首屏检查首次漏拦 Vite URL 的 `?t=` 参数，校正后验证应用挂载前的 CSS 变量及 theme-color 与挂载后相同。最初编辑器/后台 AI 夹具路径和一次 M14 脚本入口错误也在重跑前修正，原失败日志保存在 `.local/m15/`。

Safari、Firefox、物理手机与真实触控板、外部付费 AI 服务、远程 CI 和正式部署未执行。后端测试本轮未重跑。权限、XSS、上传和 AI 错误处理保留原接口，相关前端回归包含在 493 项测试中。

改动保留在本地工作区，未提交、推送、合并或发布。原 M14 未提交成果、输入 ZIP、业务数据和上传材料继续保留。
