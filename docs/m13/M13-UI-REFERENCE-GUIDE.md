# M13 高保真 UI 参考图使用说明

本页为历史阶段记录，日期、测试数量和当时范围按原记录保留。当前使用与开发入口见 [项目文档](../README.md)。

## 独立参考图（设计效果图，不是实际项目截图）

| 文件 | 设计重点 | 开发落地 |
|---|---|---|
| `references/01-desktop-discovery-dark.png` | 桌面深色发现流、自然错落卡片、左侧轻导航和山水绿视觉 | `GuideGrid/GuideFeed`，真瀑布流、无限滚动、视觉密度 |
| `references/02-desktop-discovery-light.png` | 浅色下的同一信息架构、清晰的层级和低噪声卡片 | 语义颜色、主题一致性 |
| `references/03-article-right-action-rail.png` | 文章正常阅读态，中间图文，右侧竖列点赞收藏评论分享作者 | `GuideDetailView` 和操作栏 |
| `references/04-article-comment-drawer.png` | 文章保持可见，右侧评论抽屉打开，底部输入 | `GuideComments` 可控面板、焦点/键盘、滚动保持 |
| `references/05-creator-public-profile.png` | 完整公开创作者主页、头图、昵称/用户名、真实作品流 | `CreatorView`、个人中心迁移 |
| `references/06-mobile-infinite-discovery.png` | 手机浏览器双列发现流和底部快捷入口 | 移动 Web 响应式，不是 uni-app |
| `references/07-mobile-comment-drawer.png` | 手机浏览器全高评论抽屉与软键盘区域 | 移动端侧栏/底部面板适配 |
| `references/08-motion-interaction-guide.png` | hover、弹窗、点赞、图片灯箱、加载反馈等动画规格 | 统一动效 tokens 与 Reduced Motion |

## 用户截图（真实问题 / 灵感来源）

- `references/user-01-grid-gaps.png`：当前站点卡片上下大片空洞，优先解决。
- `references/user-02-short-actions.png`：短视频右侧操作按钮，借鉴操作位置和紧凑程度，**不把长文章变成短视频分页**。
- `references/user-03-comment-side-panel.png`：右侧评论抽屉的交互模式，借鉴层级与输入区布局。

## 图像使用边界

所有示意页为静态概念图；文字、发布数、点赞、关注、粉丝和评论数均为排版占位数据，不代表真实数据库记录，也不证明该功能已经开发。若前端/后端尚无“关注、回复线程、评论点赞”的真实实现，不得把设计稿中的控件作为已完成并直接上线。参考图使用 AI 辅助生成的旅行摄影元素，目的是辅助视觉与布局，不应直接用作项目真实旅行素材或冒充真实图片来源。不得上传截图中的其它平台品牌元素到山水间产品。

**用户最高优先级：真正无大空隙瀑布流、连续滚动取代页码、右侧互动栏和评论抽屉、每个用户独立个人主页、全站细腻动效、DSH 前端主责。**
