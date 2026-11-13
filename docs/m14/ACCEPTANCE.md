# M14 专业图片查看器验收

本页为历史阶段记录，日期、测试数量和当时范围按原记录保留。当前使用与开发入口见 [项目文档](../README.md)。

2026-11-12，基于 `main@e679e3e976a0048071ba4fc5e4168ccf209a27e2` 完成本地实现和 Chromium 验收。未提交、推送或公网部署。

## 实现结果

共享灯箱已采用自然尺寸变换模型，包含 260ms 缩略图 FLIP、无源矩形时淡入微缩放、连续滚轮/双指缩放、点击位置缩放、带边界的鼠标/单指拖动、双向旋转、真实比例、适应屏幕、1:1 和原图下载。切图重置旋转、缩放与位置，放大拖动优先于图库滑动。窄屏工具栏分成两行，按钮保持至少 44px，并考虑安全区域。

`ContentImage.vue` 与 `SafeMarkdown.vue` 沿用既有入口和图库结构，无需修改：`useImageLightbox` 自动读取触发图片的矩形，保留原索引并在路由变化时关闭。重复 URL、重复对象和白名单过滤前后的位置映射均有测试。

下载复用原图片白名单，只接受同源受管路径，禁止重定向，检查响应地址、MIME 和非空 Blob。文件名固定为 `shanshuijian-<受管文件名>`，不读取用户说明或 Content-Disposition。下载失败使用固定提示，切图和卸载取消请求，成功后释放对象 URL。旋转仅改变预览变换，不生成或上传修改版图片。

## 执行结果

| 检查 | 结果与证据 |
| --- | --- |
| `npm --prefix frontend test` | **49 文件、457/457 通过**。保留重复 URL、错误图、白名单、键盘、焦点与滚动测试；增加自然尺寸、旋转边界、点击/拖拽、wheel、pinch、动画回退、取消下载和路由清理测试。 |
| `npm --prefix frontend run build` | **通过**，Vue TypeScript 检查及 Vite 生产构建成功。 |
| 360、390、768、1024、1440px × 浅/深主题 | **10/10 通过**。无横向溢出，默认完整 contain，实际百分比误差不超过 0.051 个百分点，工具栏按钮均至少 44px，关闭后焦点和背景滚动恢复。[矩阵与逐帧数据](evidence/matrix.json) |
| 展开动画 | 每组记录到源矩形至目标矩形的 260ms 动画。无矩形回退与 Reduced Motion 另有组件测试；浏览器减少动态效果时动画数量为 0。 |
| 原生鼠标和键盘 | **22/22 通过**。多次点击 100/150/200/300/400%，400% 不关闭；点击锚点误差小于 1 CSS px；grabbing、边界与拖后点击抑制；滚轮；左右旋转适应；1:1；下载成功/失败；切图复位；焦点循环；空白关闭；封面单图；Esc 与 Reduced Motion。[记录](evidence/interactions.json) |
| 原生触摸 | **8/8 通过**。双指缩放、松开一指后继续移动、适应时前后翻图、纵向不误翻、放大后单指拖动优先、触摸取消和关闭清理。输入为 Chromium CDP，不是 DOM TouchEvent 人工分发。[记录](evidence/touch.json) |
| 失败与安全边界 | **9/9 通过**。加载中和加载失败时点击空白均可关闭；符合白名单的缺失图库图片真实返回 404，显示错误且禁用全部变换/下载，仍可切回健康图片；图库点击索引；路由 query 变化关闭；DOMPurify 和图片白名单；4 类非法下载路径在请求前拒绝。[记录](evidence/edges.json) |
| 原图字节 | 工具栏下载 237847 字节，与授权图片副本 SHA256 完全相同：`38c69c6a4a8939bec095227dabd35de4b3142badd4f12148c3264a8ad1a3adba`。[记录](evidence/download.json) |

浏览器使用完整 Vue 攻略页，通过浏览器层拦截 GET API 与图片提供隔离夹具，不访问业务数据库。404/503 为故障注入，成功验收没有未处理页面脚本异常。原生触摸早期夹具中的手指释放参数已根据实际 PointerEvent 记录修正；缺失图片使用合法受管文件名，避免把白名单过滤或解码缓存误判为加载失败。

## 最终截图

| 宽度 | 浅色 | 深色 |
| --- | --- | --- |
| 360 | [查看](evidence/lightbox-light-360.png) | [查看](evidence/lightbox-dark-360.png) |
| 390 | [查看](evidence/lightbox-light-390.png) | [查看](evidence/lightbox-dark-390.png) |
| 768 | [查看](evidence/lightbox-light-768.png) | [查看](evidence/lightbox-dark-768.png) |
| 1024 | [查看](evidence/lightbox-light-1024.png) | [查看](evidence/lightbox-dark-1024.png) |
| 1440 | [查看](evidence/lightbox-light-1440.png) | [查看](evidence/lightbox-dark-1440.png) |

[原生双指缩放](evidence/gesture-pinch-390.png) · [真实加载失败](evidence/image-failure-390.png)。西湖照片沿用项目已有授权素材，摄影署名和 CC BY-SA 4.0 信息见 `docs/m8/content/image-sources.json`，没有新增外部素材下载。

## 文件与协作

生产代码：

- `frontend/src/components/ImageLightbox.vue`：画布、FLIP、加载状态、手势、键盘、下载状态与生命周期。
- `frontend/src/components/ImageViewerToolbar.vue`：独立工具栏、统一 SVG 与两行响应式布局。
- `frontend/src/composables/useImageLightbox.ts`：源矩形、重复索引和路由/作用域清理。
- `frontend/src/composables/useImageTransform.ts`：自然尺寸比例、旋转后适应、缩放锚点与位移边界。
- `frontend/src/utils/imageDownload.ts`：受管同源原图下载与对象 URL 释放。

测试：`frontend/tests/image-lightbox.spec.ts`、`image-viewer-toolbar.spec.ts`、`image-transform.spec.ts`、`image-preview.spec.ts`、`image-download.spec.ts`。另更新 `PROJECT_STATUS.md`、本目录实施/验收说明及证据。[完整修改清单](evidence/CHANGED-FILES.txt)。

Codex 本机配置核对为 `gpt-6.1-sol / ultra`，未改全局配置。DSH config、probe 和实际任务确认 `buddy / deepseek-v4.1-flash / max`。首个大任务 `hub-16-mwcm8od2` 约 8 分钟无代码产出后取消；第二个 `hub-17-mwcmkrkw` 实际交付工具栏和 8 项测试，Codex 删除其中一项源码样式断言，改由真实浏览器检查尺寸，并调整窄屏分组。其余核心代码由 Codex 实现、集成和复核。

最终独立只读审查 `hub-18-mwcn65xv` 未发现 P1，发现 1 项 P2：状态提示层覆盖透明画布区域，拦截空白点击。Codex 先在浏览器确认失败，再给状态层增加 `pointer-events:none`；加载中和加载失败两种空白关闭回归均通过，之后完整单测与构建再次通过。[协作及审查记录](evidence/DSH-CONTRIBUTIONS.json)。

## 未完成或未执行

- 核心需求无已知未完成项。
- 未执行物理手机、真实触控板硬件、Safari/Firefox 验收；本轮浏览器为 Chromium，触摸为 CDP 输入。安全区域 CSS 已实现，但刘海屏硬件仍需人工确认。
- 后端、数据库、上传权限和归档 uni-app 无修改；未重跑后端或 Python 全套，也未运行远端 CI。
- 未提交、推送、部署；原有 M12 ZIP、业务数据、上传文件与私有配置保留。
