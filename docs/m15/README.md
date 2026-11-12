# M15 · 山水间全站视觉统一与自定义色调

此目录可直接解压到项目根目录下 `docs/m15/`；不会覆盖 `frontend/`、`backend/`、M14、业务数据库或 `deprecated/uni-app/`。

阅读顺序：

1. `M15-CODEX-IMPLEMENTATION.md`：总目标、风险、技术方案与验收。
2. `M15-DSH-VISUAL-BRIEF.md`：优先交由 DSH 执行的 UI/CSS 实现任务。
3. `M15-VISUAL-QA.md`：逐页面真实浏览器验收矩阵。

**注意：** 制作此包时远程 main 最新为 M13 `e679e3e`。用户说 M14 已完成，可能尚未推送，先以本地真实工作区为准，禁止使用远程旧版替换本地 M14。

开场提示：

> 现在开始 M15，先阅读根 AGENTS.md 和 docs/m15/ 全部文档。第一优先级保护本地已经完成的 M14 图片查看器及 M13 成果；核对 git 工作区后再实施。请由 Codex（GPT-6.1-Sol/Ultra）主导架构审查，优先委派 DSH（CodeBuddy DeepSeek V4.1 Flash/Max）实际完成前端设计系统、色调选择 UI 与页面视觉统一，Codex 完成整合、测试、真实浏览器对比、报告。用户要求页面不再割裂，色调全站一致、可自定义、浅深兼容、能持久化。不要仅输出计划，不要未经授权推送。
