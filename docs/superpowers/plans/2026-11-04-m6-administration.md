# M6 基础后台 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans，按任务保留真实RED与验收证据。

**Goal:** 管理员可安全启停用户、治理攻略/评论并进入目的地维护。

**Architecture:** 当前模块化单体扩展，Admin用户模块负责管理员集合锁和安全投影；Guide/Comment业务Service维护本模块状态与锁。Codex维护共享契约/整合，DSH限定单文件或内联审查。

**Tech Stack:** 已锁定Java21/Spring Boot3.5/MyBatis-Plus/MySQL8.4、Vue3/TypeScript/Pinia/Vitest，无新依赖或DDL。

**Spec:** [M6设计](../specs/2026-11-04-m6-administration-design.md)、API21。

## Global Constraints

- 仅ACTIVE未删ADMIN；ID字符串，时间+08:00毫秒，page1/size10且最大50。
- 无用户/目的地硬删、角色变更/密码重置/管理员代改正文；无自动commit/模型更换。
- 用户状态ACTIVE/DISABLED，攻略治理目标HIDDEN/PUBLISHED（DRAFT拒绝），评论NORMAL/HIDDEN；同状态200不改时间。
- 用户状态先有序锁全部ADMIN并重核操作者，再锁目标；攻略/评论维持攻略→评论锁序；写RC/读RR。
- DSH仅buddy/deepseek-v4.1-flash/max，不读.env/.local或真实DB；每个writer精确单文件，主代理统一执行真实测试。

## Review Focus

- A/B同时禁用对方导致管理员全失效：Task1真实并发，重核当前操作者，至少1可用。
- 初次授权后等待管理员集合期间操作者被禁用：Task1锁等待后401，不继续写目标。
- 草稿经治理发布/隐藏内容被作者解除：Task2真实状态矩阵与首次时间/关系不变断言。
- 评论等待父攻略锁时已被作者软删：Task2当前锁读404，不能复活。
- 确认目标被筛选/另一行替换、迟到401错误导航：Task3行为测试与版本失效。

## Task0 设计与契约

**Files:** 本spec/plan、docs/06-api-contract.md第21节、PROJECT_STATUS.md。
- [x] 自审字段/状态/锁序与DDL一致，hub57只读前置审查已由Codex逐项裁定，实施门已释放。

## Task1 用户管理

**Files:** 新backend/.../administration/{dto/StatusPatchRequest.java,vo/AdminUserVO.java,mapper/AdministrationMapper.java,service/AdministrationService.java,controller/AdminUserController.java}；修改user/service/UserService.java增加requireAdministrator()；tests/administration/AdministrationApiTest.java，共用HttpTestSupport仅Codex改namespace m6a。

**Interfaces:** UserService.requireAdministrator():User；AdministrationService.users(page,size,keyword,status,role):PageResponse<AdminUserVO>、userStatus(String id,StatusPatchRequest):AdminUserVO。
- [x] 写权限、严格DTO/八字段投影、字面LIKE/稳定分页、自禁409、幂等、A/B交叉禁用和持锁后身份失效HTTP，执行缺入口RED。
- [x] 实现管理员有序当前锁读/缓存清理/重核、目标锁及安全VO；不以快照COUNT作并发依据。
- [x] 定向GREEN并核对M1/目的地角色语义不变，专项只读审查。

## Task2 内容治理

**Files:** guide/service/GuideService.java、mapper/GuideMapper.java、controller/AdminGuideController.java；comment/service/CommentService.java、mapper/CommentMapper.java、mapper/AdminCommentRow.java、vo/AdminCommentVO.java、controller/AdminCommentController.java；复用StatusPatchRequest；AdministrationApiTest.java。

**Interfaces:** GuideService.adminList(page,size,keyword,status):PageResponse<GuideSummaryVO>、adminStatus(String,StatusPatchRequest):GuideSummaryVO；CommentService.adminList(page,size,keyword,status):PageResponse<AdminCommentVO>、adminStatus(String,StatusPatchRequest):AdminCommentVO。
- [x] 写隐藏恢复、DRAFT拒绝、作者不能解除、关系与首次发布时间、评论计数/已删不复活/等待锁、普通用户与失效ADMIN测试RED。
- [x] 实现固定admin分页SQL与安全投影；只改状态/必要updatedAt，复用原锁序与计数，不扩大公开可见性。
- [x] 全相关测试与完整package通过，DSH独立审查跨模块锁/投影，Codex复核diff与真实证据。

## Task3 后台页面

**Files:** frontend/src/types/administration.ts、api/administration.ts、components/AdminNavigation.vue、views/AdminUsersView.vue、AdminModerationView.vue；router/index.ts、components/SiteHeader.vue与views/AdminDestinationsView.vue由Codex整合；tests/admin-users.spec.ts、admin-moderation.spec.ts及router/header工厂。

**Interfaces:** API fetchAdminUsers/AdminGuides/AdminComments(params)、patchUserStatus/patchGuideStatus/patchCommentStatus(id,status)；AdminUsersView独立页，AdminModerationView props resource:'guides'|'comments'（路由静态传入）。AdminNavigation无业务写状态。API/types由Codex先固定，DSH可独占AdminUsersView单文件，其他文件不重叠。
- [x] 页面行为RED：真实服务端页码/筛选、确认不可替换、pending防重、失败保持与重试、本人禁用按钮、文本安全、迟到401/对象切换。
- [x] 实现独立导航与状态确认，API成功后刷新，401原路径捕获，403不展示旧私有列表，所有资源ID不转Number。
- [x] 全Vitest/types/Vite通过；独立前端审查和定向回归闭合。

## Task4 轻量附属统计（P1治理GREEN后）

**Files:** administration/vo/AdminSummaryVO.java、controller/AdminDashboardController.java、Service/Mapper计数方法；frontend/views/AdminHomeView.vue、api/types/路由及tests。

**Interfaces:** GET /admin/dashboard/summary返回{userCount,guideCount,destinationCount,commentCount}四数字；AdministrationService.summary():AdminSummaryVO。
- [x] 先写ADMIN权限与实际数据库口径HTTP/界面失败空错测试，再实现四个COUNT，不增加图表/依赖。

## Task5 真实浏览器与闭环

**Files:** docs/21-m6-test-report.md、docs/verification/2026-11-04-m6.json（按实际完成日）、PROJECT_STATUS.md、README.md。
- [x] 临时ADMIN/作者两上下文验收用户禁用旧会话、攻略隐藏恢复、评论治理计数、普通用户API403、手机布局；确认未触碰未知账号/内容。
- [x] 精确核对自有ID/随机用户名/全部关联后清理，保留原种子及两个MySQL服务；最终源码SHA-256与OpenAPI实际核对。
- [x] 全套测试/审查/浏览器通过才验收M6，进入部署/系统整合与毕业材料阶段，未执行项明确保留。
