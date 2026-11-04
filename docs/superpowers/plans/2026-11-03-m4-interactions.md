# M4 互动与收藏 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans. Work task-by-task, preserving ownership boundaries.

**Goal:** 用户可幂等点赞收藏、管理自己的评论和收藏，游客可安全阅读评论。

**Architecture:** 复用M3攻略锁、真实计数与页内投影。互动/评论写事务RC，读分页RR；数据库唯一键防重。Codex维护共享契约、配置和整合，DSH Flash/Max执行限定单文件或只读审查。

**Tech Stack:** 既有Java21/Spring Boot3.5/MyBatis-Plus/MySQL8.4；Vue3/Pinia/TypeScript/Vitest4.1.12，不增依赖。

**Spec:** [M4设计](../specs/2026-11-03-m4-interactions-design.md)。

## Global Constraints

- M3实际验收和M4前置审查后才开始M4生产代码。
- 无DDL/计数列/Redis/队列/自动commit；ID字符串，评论1–2000码点纯文本。
- API/Entity/共享测试helper由Codex维护；DSH只buddy/deepseek-v4.1-flash/max，不读.env/.local或运行真实DB。
- 公开互动要求PUBLISHED；作者评论删除为独立管理语义，即使隐藏仍可删，必须校验本人；ADMIN不能代删。

## Review Focus

- 并发重复点赞/收藏产生重复或重置时间：Task1并发唯一键/createdAt断言。
- 隐藏/撤回泄漏互动或收藏：Task1状态切换和恢复断言。
- 评论管理与攻略写反向锁：Task2明确攻略→评论，并发测试。
- 401/迟到状态覆盖新用户，或失败误翻选中：Task3真实状态/错误/版本回归。
- 评论HTML执行与失败丢输入：Task3 DOM和真实浏览器载荷。

## Task0：契约与前置审查

**Files:** docs/06-api-contract.md第19节、M4设计、本计划、PROJECT_STATUS.md。
- [x] M3验收落盘，M4设计只读审查并闭合真实阻塞。
- [x] 固定InteractionVO/CommentVO与作者删除隐藏评论语义，同步API13/19；记录无需新表或计数列。

## Task1：幂等互动与收藏分页

**Files:** 新建backend/.../interaction/{mapper,service,controller,vo}/；修改guide/mapper/GuideMapper.java、guide/service/GuideService.java（收藏投影复用）和共享HttpTestSupport.java仅Codex；测试interaction/InteractionApiTest.java。

**Interfaces:** InteractionService.state(String):GuideInteractionVO、like(String,boolean):GuideInteractionVO、favorite(String,boolean):GuideInteractionVO；GuideService.favorites(String page,String size,String keyword):PageResponse<GuideSummaryVO>。
- [x] 先写真实HTTP重复/并发/取消/首次时间、A/B状态、非公开404、收藏可见性恢复及稳定分页测试；执行缺失入口RED。
- [x] 实现攻略锁内唯一键防重和真实VO；收藏计数/页SQL同条件，返回M3既有安全摘要，用户ID仅会话。
- [x] 定向GREEN，再检查M3原计数与popular排序未回归；独立审查SQL/锁/数据权限。

## Task2：评论读写与作者删除

**Files:** 新建backend/.../comment/{dto,entity,mapper,service,controller,vo}/；测试comment/CommentInputPolicyTest.java、CommentApiTest.java；AuthWebConfig.java公开comments GET由Codex修改。

**Interfaces:** CommentInputPolicy.normalize(String):String；CommentService.list(String id,String page,String size):PageResponse<CommentVO>、create(String id,CommentCreateRequest):CommentVO、delete(String commentId):void。
- [x] 写2000/2001码点、空白/类型/未知字段、公开分页、真实作者权限与隐藏删除测试，保留RED。
- [x] 实现纯文本校验、写攻略锁及删除攻略→评论锁顺序，保留逻辑删除/治理边界；不加回复树或内容去重。
- [x] 跑纯策略/真实HTTP及全套package，核对评论计数NORMAL/is_deleted，审查diff。

## Task3：互动、评论和我的收藏界面

**Files:** frontend/src/{types/interaction.ts,api/interactions.ts,components/GuideActions.vue,components/GuideComments.vue,views/MyFavoritesView.vue}；GuideDetailView.vue/个人中心/路由由Codex整合；tests/{guide-actions,guide-comments,favorites-view,router-guard}.spec.ts。

**Interfaces:** API与Task1/2一致；GuideActions props guideId:string、初始三计数，emit change完整InteractionVO；GuideComments props guideId:string，emit count-changed:number；组件只接收字符串ID。收藏页复用GuideCard。
- [x] 先写真实选中恢复、防重、失败保留、401、迟到响应、评论文本DOM、错误输入保留和分页测试；guestOnly安全redirect行为先RED。
- [x] 实现组件/受保护收藏页与入口；评论不使用v-html，写操作按真实结果更新，安全回跳不接受外域。
- [x] 全部Vitest与类型/生产构建通过，独立审查并修正后定向回归。

## Task4：浏览器与阶段闭合

**Files:** docs/19-m4-test-report.md、docs/verification/2026-11-03-m4.json（按执行日期调整）、PROJECT_STATUS.md、README.md。
- [x] 两随机账号真实点赞/收藏刷新与个人收藏、发表评论/删除/越权失败，检查计数和隐藏恢复；手机布局/XSS文本。
- [x] 精确清理自己的账号/攻略/关系/评论和记录过的图片，不触碰已有数据；记录全套结果与剩余开发依赖审计项。
- [x] 全部审查/实际测试闭合后进入M5行程规划，未执行项不得称通过。
