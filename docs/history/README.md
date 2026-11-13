# 历史阶段索引

本页按开发顺序汇总阶段结果。各报告中的测试数量、提交和环境描述属于当时记录，当前状态见 [PROJECT_STATUS.md](../../PROJECT_STATUS.md)。

| 阶段 | 主要结果 | 报告 |
|---|---|---|
| M0 | 环境、数据库和需求基线 | [基线审查](../13-baseline-review.md) |
| M1 | 注册登录和个人资料 | [认证验收](../16-m1-test-report.md) |
| M2 | 目的地与受管图片 | [目的地验收](../17-m2-test-report.md) |
| M3 | 攻略及安全展示 | [攻略验收](../18-m3-test-report.md) |
| M4 | 点赞、收藏和基础评论 | [互动验收](../19-m4-test-report.md) |
| M5 | 私人行程 | [行程验收](../20-m5-test-report.md) |
| M6 | 管理后台 | [后台验收](../21-m6-test-report.md) |
| M7 | 运维、交付工具和演示 | [交付验收](../27-m7-test-report.md) |
| M8 | 景点资料、图库、备份恢复和 AI | [最终报告](../33-m8-final-report.md)及[四阶段记录](../28-m8-phase0-audit.md) |
| M9 | 历史移动端与交付 | [阶段状态](../M9-DELIVERY-STATUS.md) |
| M10 | 导航、身份和兼容字段 | [阶段状态](../M10-DELIVERY-STATUS.md)、[迁移](../m10/MIGRATION.md) |
| M11 | Web 维护范围与编辑交互 | [验收](../m11/ACCEPTANCE.md)、[归档索引](../m11/ARCHIVE-INDEX.md) |
| M12 | 内容社区、Vditor 和 AI 设置 | [交付](../m12/M12-DELIVERY.md)、[补充交付](../m12/additions/DELIVERY.md) |
| M13 | 瀑布流和阅读体验 | [交付](../m13/M13-DELIVERY.md) |
| M14 | 原图查看器 | [验收](../m14/ACCEPTANCE.md) |
| M15 | 主题和全站样式 | [验收](../m15/ACCEPTANCE.md) |
| M16 | 跨类型搜索、攻略导入与缩略图 | [交付](../m16/DELIVERY.md)、[实际 API](../m16/API.md) |
| M17 | 回复、提及、通知、分享及论文 | [交付](../m17/DELIVERY.md)、[实际 API](../m17/API.md) |

## 设计与输入

早期 [技术设计](../superpowers/specs/2026-11-03-m3-guides-design.md)保留了字段、状态和事务的取舍依据。已执行的任务列表和重复分角色提示已合并到本索引及当前协作说明。旧文件可通过 Git 历史追溯。

M16 原始输入由 `M16-SHA256SUMS.txt` 校验，保持原文便于追溯。`m8/content` 的三篇 Markdown 同时用于内容维护和富文本回归，授权信息保留在原文。各阶段 `evidence` 与 `verification` 中的真实截图、数据保护摘要及测试记录继续保留。

## 维护记录

[本轮文档整理与发布](documentation-maintenance.md)记录补全范围、清理文件、检查结果和最终提交。
