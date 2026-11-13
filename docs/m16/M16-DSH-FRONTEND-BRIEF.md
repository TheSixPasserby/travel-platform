# M16 / DSH 前端专项实际编码任务书

你是 **DSH CodeBuddy DeepSeek V4.1 Flash / Max** 的前端执行代理；由 Codex GPT-6.1-Sol / Ultra 主代理负责分派、合并和终验。不要擅改模型或全局配置。**必须实际编写代码、测试、记录屏幕截图**；只读建议不是交付。

## 工作方式

- 先读取 `docs/m16/M16-REFERENCE-GUIDE.md`，按顺序查看 `references/01-*` 至 `05-*`，再看两张 `00-*` 总览图；先读 M15 样式/主题 Tokens，沿用现有导航、山水间品牌，不要把静态概念 HTML 作为生产业务代码复制进去。
- 每阶段先做清楚的 UI 状态矩阵：匿名/登录、成功/加载/空/错误、桌面/手机、明暗/自定义色调、动画减少模式。
- Codex 已指定文件才可写，避免与其他代理同时修改同一组件；不要编辑 Java、数据库、权限、路由契约的未授权部分，不要运行 `deprecated/uni-app/` 构建。
- 使用 Vue 3 `<script setup lang="ts">`、复用 Pinia 和 `src/api/`，不要在组件里自行拼 axios 请求；不造假点赞、搜索命中、通知、AI 生成结果。不依赖运行时公共 CDN。

## 优先任务 A：统一搜索视觉与行为

**参考：`01-search-desktop.png` + `05-search-mobile.png`**

搜索框固定明确的可访问标签，输入建议单独层、键盘上下方向键/Enter/Escape、IME 组合输入支持；分类 Tabs（全部/攻略/目的地/景点/作者/话题）；完整结果每种使用合适的卡片密度；URL 可分享/后退；移动端分段 Tabs 可水平滑，滚动时不遮挡导航。

实现组件建议：`SearchView.vue` / `SearchBox.vue` / `SearchSuggestionList.vue` / `SearchCategoryTabs.vue` / `SearchResultCard.vue`。数据类型由 Codex 确认，所有图卡保留真实图片加载失败回退。

## 优先任务 B：文章→行程选择抽屉

**参考：`02-article-add-trip.png`**

追加到 M13 右侧竖向操作栏，避免覆盖点赞/收藏/评论。桌面：右侧浮动抽屉、页面背景遮罩且打开前滚动位置保持；手机：全高或底部可滚动安全区域抽屉。清楚展示真实 `guide.scenicSpots`（每个真实景点一行）、勾选/全选、目标行程与日期、新建、去重冲突、提交 pending 和结果；AI 按钮填充真实已选 ID，服务不可用时提示转手动。关闭应恢复触发器焦点，禁止背景滚动，Esc 可关闭，焦点锁定。任何按钮不得只弹出虚假“添加成功”。

组件建议：`AddToTripDrawer.vue`、`TripPlacesSelect.vue`、`MyItinerarySelect.vue`、`TripImportResult.vue`。交付前让 Codex 审查接口的实际原子性/权限。

## 优先任务 C：AI 行程工作台与来源展示

**参考：`03-ai-itinerary-workspace.png`**

重用已有 AI 需求表单、预览、每日编辑和 `importItinerary`；新增“来自攻略的地点” Chip 和来源区，来源 ID 可追踪，不得把不存在的地点自动加进日程；AI 出错后不清空手动选择、草稿或已生成预览。地图在没有真实可核验坐标/地理服务时仅显示明确标注的示意，不展示假的真实路线距离/时间。可编辑日期、节奏、预算、目的地和是否必去。

## P1 任务 D：评论回复与通知中心

**参考：`04-notifications-interactions.png`**

Codex 完成新的后端回复和通知契约后再接入。一级评论/二级回复显示、被回复者身份、引用关系、输入目标、发送中、失败重试、删除后的展示、@ 提及与通知列表。需要完整键盘/屏幕阅读器体验；避免高频无意义动画，服从 `prefers-reduced-motion`。后台治理页也应能处理违规回复，不能只改用户端。

## 动效 / 主题与跨端

保持 M15 `travel-notes.accent.v1` 及语义变量；每种主题在卡片、面板、输入、焦点和按钮状态下可读。交互动效建议 140–260ms，只有一次轻微位移或透明度，浮层 open/close 真实，不能把 `transform` 动画施加到动态高度瀑布流容器导致布局跳动。响应式在 360、390、430、768、1024、1440px 覆盖，手机为**浏览器 Web**，不涉及已废弃 uni-app。

## DSH 每项必须交付

1. 实际修改文件/新增文件清单及每个文件用途；2. 核心代码 diff 与接口契约的依赖项；3. `npm --prefix frontend test` 和 `npm --prefix frontend run build` 实际结果；4. 至少两种主题 + 桌面/手机的真实浏览器截图；5. 交互用例：点击、键盘、焦点、滚动、网络故障；6. 对照参考图说明有意差异；7. 未完成、回退与风险。

若需要 Codex 修改共用 API 或路由，应通过明确的交接请求，不要绕过文件边界。任何 DSH 工具拒绝授权时必须停止该操作并报告，不能改用其他通道绕过。
