# M16 · 山水间「旅行灵感 → 私人行程 → 社区分享」执行入口

> 解压到 `travel-platform/` **根目录**。本包只新增 `M16-START-HERE.md`、`docs/m16/`、`scripts/m16-*.py/sh`，不覆盖项目源码。**所有参考图都是概念设计稿，不是已经落地的功能或真实数据库内容。**

## 最重要的决定

1. **P0**：统一搜索（攻略、城市/景点、作者、话题）；在已发布攻略中选择地点加入已有行程或创建行程；保留 AI 不可用时的手动路径；信息流图片性能优化。
2. **P1**：评论回复 / @ 提及 / 消息通知、创作者体验、分享与 SEO；必须先完成后端契约、权限和测试才能显示功能入口，不得做假功能。
3. **P0 验收**：已有 M13–M15 行为不回退；Web-only；无原有业务数据丢失；真实浏览器（含手机 Web）截图与交互日志；原始密码/密钥/上传文件不提交。
4. **代理分工**：Codex GPT-6.1-Sol / Ultra 主代理负责后端与契约、协调与终验；DSH CodeBuddy DeepSeek V4.1 Flash / Max 优先负责页面视觉、Vue 组件、动画、主题和响应式的**实际编码**，无交付就不能记为贡献。
5. **执行方式**：各阶段顺序完成，每阶段先最小可用闭环并验收，再进入下一阶段。不得因为 P1 范围较大而把 P0 测试跳过。

## 快速开始

```bash
# 1) 在 travel-platform 根目录核对工作区/本包；只读，不改数据库
python3 scripts/m16-preflight.py

# 2) 核对压缩包、文档、参考图、脚本完整性；只读
python3 scripts/m16-verify-package.py

# 3) 让 Codex 阅读完整主任务书并执行阶段计划
# docs/m16/M16-CODEX-IMPLEMENTATION.md

# 4) 前端改造阶段完成后执行（会构建 dist / 生成本地运行缓存）
bash scripts/m16-checks.sh frontend

# 5) 有隔离 MySQL 测试环境、确认 backend.py 不会写业务库后，才执行
bash scripts/m16-checks.sh full
```

> 以上脚本只做预检、包完整性检查和主动触发测试；**不会自动修改前后端源码、迁移数据库、提交、推送或发布**。实际开发由 Codex / DSH 根据任务书执行。

## 文件清单

| 文件 | 用途 |
|---|---|
| `docs/m16/M16-CODEX-IMPLEMENTATION.md` | 主代理完整实施规范（按阶段编码） |
| `docs/m16/M16-DSH-FRONTEND-BRIEF.md` | DSH 实际前端开发专用任务书 |
| `docs/m16/M16-API-DATA-CONTRACT.md` | 已有契约、建议新接口、权限和数据保护 |
| `docs/m16/M16-EXECUTION-PLAN.md` | 执行顺序、任务边界、进度与交付格式 |
| `docs/m16/M16-ACCEPTANCE.md` | 必过用例、视觉矩阵与报告模板 |
| `docs/m16/M16-REFERENCE-GUIDE.md` | 逐张参考图的组件映射和行为说明 |
| `docs/m16/references/` | **2 张总览图 + 5 张独立参考屏 + 原型 HTML/CSS + 用于概念稿的图片素材** |
| `scripts/m16-preflight.py` | 只读本地环境与 Git 检查 |
| `scripts/m16-verify-package.py` | 只读文件与摘要核验 |
| `scripts/m16-checks.sh` | 显式选择运行前端/完整测试 |

仓库读取基线：`main@9a6261c47695131bee399c8d373d1cc5ff55c630`（2026-11-12）。开始时应重新 `git fetch` 后核对远端；本包不替代理实测结果。最近对应的 GitHub Actions 运行 `43252114144` 在制作本包时已成功，但新的 M16 代码仍需单独运行 CI。
