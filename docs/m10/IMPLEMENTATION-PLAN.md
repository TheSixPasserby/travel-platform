# M10 导航与兼容字段实施记录

本页为历史阶段记录，日期、测试数量和当时范围按原记录保留。当前使用与开发入口见 [项目文档](../README.md)。

**Goal:** 按六张参考图交付 Web 单行导航、抽屉/两种浮层、统一身份和评论元信息、安全 IP 属地降级，保护 M8/M9。

**Architecture:** 延续 Vue/Pinia 与模块化 Spring Boot。API 仅增加公开 username、可空 ipRegion；使用现有首次发布时间和行锁保存历史语义。没有已授权 GeoIP 来源时保存 NULL，不调用第三方。

**Spec:** `docs/m10/README.md`、[历史阶段记录](../history/README.md) 及六张 references。

## 工作区 checkpoint

- 原分支 `codex/m9-delivery`，HEAD `712be1d`，原源码干净。用户新增 zip 与 docs/m10 保留。
- 按用户“现有项目目录直接实施”，原目录工作，最终不自动 commit/push。
- Codex 本机配置 `gpt-6.1-sol/ultra`；DSH config + probe 为 `buddy/deepseek-v4.1-flash/max`，不更改全局模型配置。
- uni-app 不改页面布局；原数据库不重建，不向第三方上报 IP。

## 分阶段任务与验收

- [x] 1. Codex（DSH 正式任务失败后接管）：仅 Header、专用浮层组件/composable、header 测试；实现单行/焦点/互斥/滚动锁/主题/真实退出。先增加行为测试看到 RED，再实现并运行 header 测试。主代理审查 diff。
- [x] 2. Codex：PublicProfileVO.username 与 Web 最小兼容类型；UserIdentity 组件；攻略/个人中心/评论作者展示。组件和 API 字段测试 RED→GREEN，保留全部评论竞态与治理测试。
- [x] 3. Codex：可信代理 IP 解析、无来源 NULL 降级；首次发布/评论快照；两个可空列的独立增量迁移、备份和恢复说明。安全单测和独立测试库集成、并发/历史 NULL/伪造字段测试。
- [x] 4. 实际运行任务书全部后端/Web/mobile/Python 检查；浏览器六宽度/两主题/匿名与账号/交互截图；只读专项审查、diff 和敏感信息扫描；更新 PROJECT_STATUS 与 M10-DELIVERY-STATUS。

## 审查项目

1. 360px 长账号不溢出，设置和账号快速切换不丢失焦点/滚动恢复。
2. 登出失败及账号切换期间不泄漏旧账号私有状态。
3. 不可信代理、畸形 XFF、IPv4-mapped IPv6 一律安全解析，不做 DNS 查询。
4. 首次属地未知也不能在二次发布补填；历史发表记录不伪造地域。
5. 迁移断点/重复执行、旧列不一致时停止，不重放 schema.sql。

验收结果与实际贡献以 docs/M10-DELIVERY-STATUS.md 为准，DSH 不记作成功执行。
