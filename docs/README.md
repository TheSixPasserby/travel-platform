# 项目文档

当前说明对应 M17。首次运行先阅读本地开发和数据库说明，查阅阶段过程时从历史索引进入。

## 开发与设计

| 文档 | 内容 |
|---|---|
| [项目概述](00-project-charter.md) | 用户、使用场景和交付条件 |
| [需求说明](01-requirements-specification.md) | 功能编号、角色和业务约束 |
| [系统架构](02-system-architecture.md) | 模块、通信、持久化与权限 |
| [功能与页面](03-functional-structure.md) | 实际 Web 路由和界面组织 |
| [业务流程](04-business-processes.md) | 内容、消息及私人规划的数据流 |
| [数据库设计](05-database-design.md) | 当前表、关系、事务和迁移 |
| [API 契约](06-api-contract.md) | 传输、字段、状态及增量接口 |
| [界面约定](07-ui-ux-guidelines.md) | 主题、交互、图片与可访问性 |
| [版本进展](08-development-roadmap.md) | 已交付阶段和后续验证 |
| [测试与验收](09-test-and-acceptance.md) | 基础及增量场景、执行命令 |
| [维护事项](10-risk-register.md) | 实际环境与测试范围限制 |
| [技术决策](11-decisions-and-open-questions.md) | 当前实现的主要取舍 |
| [开发协作](12-codex-dsh-workflow.md) | 文件分工、证据和发布记录 |
| [依赖基线](15-dependency-baseline.md) | 当前锁定版本和兼容说明 |

## 运行与交付

- [本地开发](14-local-development.md)
- [数据库初始化和升级](../database/README.md)
- [部署、备份和恢复](22-deployment-and-operations.md)
- [使用手册](23-user-manual.md)
- [演示流程](24-demo-walkthrough.md)
- [需求与测试追踪](25-requirements-traceability.md)
- [论文与答辩材料](26-thesis-and-defense-outline.md)
- [M17 交付](m17/DELIVERY.md)与[论文成果](../output/m17/论文修改记录与人工确认事项.md)

## 历史材料

[历史索引](history/README.md)按阶段列出结果和证据。历史报告的测试数量、日期、旧平台和当时未完成项保留其原始含义。早期重复任务书已合并，原图、素材许可及机器验收记录继续在对应目录中查阅。

`m16` 的原始输入说明、原型和摘要由 SHA256 清单校验，当前实际契约为该目录 API.md，实施结果为 DELIVERY.md。当前运行和开发优先参考本页所列说明。
