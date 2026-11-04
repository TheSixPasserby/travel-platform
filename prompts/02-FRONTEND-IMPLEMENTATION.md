# 前端阶段提示词

遵循根目录 `AGENTS.md` 实施 Vue 3 用户端和管理端。

技术：Vue 3、TypeScript、Vite、Vue Router、Pinia、Axios、Tailwind CSS。

视觉目标：现代旅游平台，不是后台模板。暖白/米白背景，自然绿或蓝绿色品牌色，目的地大图、攻略卡片、清晰留白、轻量交互、桌面端优先并兼容手机。

页面顺序：登录注册、公共布局、首页、攻略广场、攻略详情、目的地列表/详情、发布编辑、个人中心、收藏/我的攻略、行程列表和时间线、管理后台、AI 助手（选做）。

约束：
- Composition API + `<script setup lang="ts">`；
- API 请求集中管理，类型集中管理；
- Pinia 仅存全局状态；
- 不将所有页面写入一个组件；
- 统一 loading/empty/error/success；
- 路由守卫不代替后端权限；
- Markdown 安全渲染；
- 图片有占位和错误处理；
- 不使用不明版权图片；
- 不在前端保存 AI API Key。

DSH 可委派独立页面、组件或只读 UI 审查。先明确文件所有权，避免代理同时修改全局样式、路由和共享组件。DSH Subagent 使用 CodeBuddy DeepSeek V4.1 Flash / Max，Codex 主代理保持 GPT-6.1-Sol / Ultra，两者无需相同。

交付：路由、组件结构、API 对接、响应式处理、测试结果和未完成项。
