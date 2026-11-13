# M3 攻略核心设计与前置审查稿

本页为历史阶段记录，日期、测试数量和当时范围按原记录保留。当前使用与开发入口见 [项目文档](../../README.md)。

日期：2026-11-03。状态：M2已验收，M3前置审查修订，**尚未进入生产代码实现**。按本设计和实施计划逐项推进。用户已授权持续推进，不重复确认模型配置或常规实施选择。

## 目标和范围

完成登录作者创建草稿/直接发布、公开列表/详情、作者私有列表/编辑数据、编辑、删除、发布/撤回、标签、多图和安全 Markdown。搜索在 MySQL 中完成标题/摘要关键词、目的地、标签筛选及稳定分页；不引入全文检索服务。点赞收藏评论写入留 M4，但 M3 公开投影中的计数来自现有关系表真实查询，不能写死为零或增加冗余计数列。

沿用 guides、guide_images、guide_tags、tags 既有表，无 DDL 变更。Controller/Service/Mapper/DTO/VO 分层，不在 Controller 拼 SQL，不向客户端返回 Entity。数据库、API、跨模块配置由 Codex 统一维护；DSH 仅接收明确文件所有权的小任务。

## 请求与响应

创建与完整编辑共同字段：title、content、summary、destinationId、coverImageUrl、travelDays、budgetPerPerson、season、tagIds、images。创建另有 status（缺失 DRAFT，显式 null 非法，仅 DRAFT/PUBLISHED）；编辑不接受 status，状态变更通过发布/撤回专用入口。所有未知、作者、逻辑删除和内部字段拒绝。

| 字段 | 约束 |
|---|---|
| title | 必填，strip 后 1–180 Unicode 码点 |
| content | 必填字符串，保留 Markdown 原始空白，最多 100000 码点；草稿可为空，公开发布时不得全空白 |
| summary | 可 null，strip 后空串归 null，最多 500 码点 |
| destinationId | 可 null，正 Long 范围十进制字符串；新建/替换引用仅 ACTIVE，未改变的历史 INACTIVE 引用保留 |
| coverImageUrl | 可 null，实际存在的受管图片路径，最多 500 字符 |
| travelDays | 可 null，1–365 整数；拒绝小数和越界 |
| budgetPerPerson | 可 null，非负十进制字符串，整数最多 8 位、小数最多 2 位，不接受指数/数字 JSON；响应固定两位小数 |
| season | 可 null，strip 后空串归 null，最多 40 码点 |
| tagIds | 缺失/null 归空数组，最多 10 个；每项正 Long 字符串，不得重复，标签必须存在 |
| images | 缺失/null 归空数组，最多 20 项；每项恰含 url、可空 altText；url 为实际受管路径且不得重复，altText 最多 200 码点 |

PUT 是完整替换：省略的可空字段清空，省略的数组变空；不修改 authorId、publishedAt 或状态。图片按数组顺序写 sort_order=0..n-1，标签关系和正文同一事务更新，任何校验失败保持原数据。图片上传独立进行，保存失败不删除已上传文件，复用 M2 孤儿图片保留策略。

公开 GuideSummaryVO：id、title、summary、coverImageUrl、author（id/nickname/avatarUrl/bio）、destination（id/name/status 或 null）、travelDays、budgetPerPerson、season、tags（id/name 数组）、status、publishedAt、createdAt、updatedAt、likeCount、favoriteCount、commentCount。GuideDetailVO 在此基础上增加 content 和 images（url/altText/sortOrder）。ID 全部字符串，计数与排序为数字，时间为 +08:00 毫秒，nullable 字段保留。

公开列表 GET /guides 使用 page/size/keyword/destinationId/tagId/sort=latest|popular，未知参数不扩大可见性；title/summary 的 LIKE 按字面转义。latest=published_at DESC,id DESC；popular=真实点赞数 DESC,published_at DESC,id DESC。先在数据库统计和分页，再批量装配页内标签/作者/目的地，避免每张卡片一次查询。空页保留真实 total。

私有 GET /users/me/guides 支持 page/size/status（省略全部状态）及 keyword，created_at DESC,id DESC；GET /users/me/guides/{id} 仅作者读取自己的任意未删除状态。公开 GET /users/{id}/profile 仅返回既定四个安全字段；禁用用户不会自动下架其已有公开内容。

## 权限、状态与并发

每个写入实时检查 ACTIVE 登录用户，从认证上下文取得作者 ID。目标不存在/已删除 40401；存在但非本人访问作者入口 40301，ADMIN 也不代替作者编辑。公开入口仅 PUBLISHED 且未删除，草稿/隐藏一律 40401。

遵守 API 第 13 节：首次发布设置 published_at，以后编辑、撤回、再次发布均保留；DRAFT→发布、PUBLISHED→撤回，重复目标操作幂等。HIDDEN 不允许编辑/发布/撤回（40902），可私有查看或作者删除；管理员治理留后续专用接口。创建 PUBLISHED 时执行完整发布校验。

写事务先锁定目标攻略的当前行，再判断所有权/状态，再验证关联和更新正文/关系。删除为逻辑删除，不物理删除关系或文件；后续所有公开读取先确认攻略可见。事务失败不得留下半套图片/标签。新建/改变目的地引用以锁定当前读验证，避免与管理员停用并发时接受已停用的新引用；已有未改变引用可保留。锁顺序固定为攻略→目的地→标签/关系，同模块所有写入口一致，锁冲突 40902；不使用进程内锁冒充数据库并发保证。

## Markdown 与界面

前端使用 Markdown 解析器关闭原始 HTML，再以 DOMPurify 做允许列表清理。本次恢复联网后已从 npm 官方元数据核实 markdown-it 15.0.3（自带类型）和 DOMPurify 3.4.17（自带类型）；无需额外 @types/markdown-it。实际 Node24/Vite7 兼容性以安装、类型和行为测试确认。[markdown-it 官方](https://github.com/markdown-it/markdown-it)、[DOMPurify 官方](https://github.com/cure53/DOMPurify)作为配置依据；早先网络失败记录保留。

正文图片只渲染受管 /uploads 路径，拒绝外链、data、javascript、SVG、路径穿越；链接仅允许明确的 http/https 或站内路径，外链加 rel="noopener noreferrer"。必须测试编码/混合大小写协议、HTML 事件、SVG、畸形 Markdown 和解析后再修改 DOM 导致的清理失效。正文按纯文本存储，公开渲染与编辑预览共用一个安全渲染组件；不能只在保存时过滤。

新增 /guides、/guides/:id、/guides/new、/guides/:id/edit、/me/guides。列表真实服务端分页；详情提供作者、目的地、正文、图库及编辑入口；编辑器为 Markdown 文本域+安全预览，上传图片成功后才插入受管路径和 images 数组。保留大 ID 字符串，失败保留用户编辑，上传/保存中阻止重复提交和切换编辑上下文。处理加载、空、错误、成功、401；不显示未实现的互动按钮。目的地详情增加按 destinationId 跳转攻略列表的真实入口。

## 验收与审查重点

1. A 创建草稿，公开列表/详情不可见，B 和 ADMIN 不能通过作者入口修改/读取编辑数据；A 发布后公开可见。
2. 发布→编辑→撤回→再发布保留首次发布时间；HIDDEN 无法由作者解除；删除后公开、私有入口均不可见。
3. 事务内替换图片/标签，一项无效即完整回滚；上传失败不建立攻略或关系。新/变更停用目的地拒绝，未变更历史引用保留。
4. LIKE 的 %/_/!、Unicode、标签/目的地联合筛选、popular 同分排序、末页外请求、大 ID、金额精度及非法字段通过真实 MySQL HTTP 验证。
5. Markdown 攻击载荷在预览和详情均不可执行，合法标题、列表、链接、代码和受管图片仍可读；完整浏览器验证发布、编辑刷新和移动端布局。

测试仅独立 DB_TEST_NAME，用随机 m3g 前缀夹具，清理自己的关系→攻略→用户/目的地；不清空表、不改约束、不读真实凭据给 DSH。暂不具备真实浏览器/独立审查执行条件时，保留未执行状态，不放行下一阶段生产代码。

## 前置审查裁定与精确补充

DSH hub-37-mvznv91q仅收到内联基线，提出10项意见。Codex核对实际DDL/API/实现，采纳标签定序、公开正文不变量、发布重验、金额文法、安全允许列表等实质补充；不将“未向审查员内联某文件”推断为“仓库不存在该表/配置”。以下补充为实现约束。

### 实际基线与顺序

database/schema.sql已含guide_comments表（第132行，content2000、status NORMAL/HIDDEN、is_deleted），M3 commentCount统计NORMAL且未删除评论。users.bio300、avatar_url500、destinations.status、guide_images.image_url500/alt_text200均已存在；所有业务时间DATETIME(3)，M2 VO显式+08:00毫秒。guide_tags已有(guide_id,tag_id)唯一键和(tag_id,guide_id)索引；标签没有排序列，**输出统一tag.id升序，不保存输入顺序**，仅图库保存数组顺序。不得为审查员的假设补建表或修改DDL。

### 状态操作矩阵

| 入口 | DRAFT | PUBLISHED | HIDDEN | 不存在/已删除 |
|---|---|---|---|---|
| 作者私有GET | 200 | 200 | 200 | 40401 |
| 作者PUT | 200，可空正文 | 200，正文不得空白 | 40902 | 40401 |
| publish | 校验后200，首次设置publishedAt | 200幂等，保留发布时间 | 40902 | 40401 |
| unpublish | 200幂等 | 200转DRAFT，保留发布时间 | 40902 | 40401 |
| 作者DELETE | 200/null，逻辑删除 | 同左 | 同左 | 40401 |
| 公开GET | 40401 | 200 | 40401 | 40401 |

所有作者入口先认证；目标存在但非本人40301（包括ADMIN）。POST PUBLISHED与PUBLISHED上的PUT都执行requirePublishable，空白正文40001；创建DRAFT允许空字符串但不允许缺失/null正文。发布状态转换前从持久化行和关系重建输入并重验字段、图片实际存在；既有未改变INACTIVE目的地关联仍保留，逻辑删除/不存在关联拒绝40001。幂等已发布请求不重写数据或时间。编辑PUBLISHED同样验证全部字段/关系，HIDDEN在任何编辑校验之前拒绝40902。删除第二次40401，不伪装成已存在。

预算输入严格文法 `^(0|[1-9][0-9]{0,7})(\.[0-9]{1,2})?$`，拒绝前导零、-0、.5、1.、指数和JSON数字；0、0.1、99999999.99合法，转BigDecimal并精确scale=2，响应String/null（如"0.10"），不用Number/Double。travelDays是Integer/null，已有Jackson严格整数/文本强转规则继续生效。

### 数据库并发和读取

管理员停用已由M2 DestinationService在REPEATABLE_READ事务中锁定同一destinations行（有序全图FOR UPDATE）。攻略新/变更关联使用同一行的FOR UPDATE当前读；若停用先提交则拒绝，若攻略先持锁并提交再停用则变为合法历史引用。不假设进程锁或缓存；加入两个独立连接的HTTP并发夹具验证。Spring现有PessimisticLockingFailureException统一40902。

公开攻略的目的地已INACTIVE仍可展示和筛选，VO带真实status；停用不隐藏历史攻略。合法但不存在的destinationId/tagId筛选返回空200；非法/越界ID40001。page默认1、size默认10且1–50，溢出按RequestValues拒绝；keyword strip、空白视为无条件、最多100码点。sort仅latest/popular，私有status仅三种状态，非法均40001。沿用固定 `LIKE ? ESCAPE '!'` 参数化SQL：先将!→!!、%→!%、_→!_，反斜杠是普通字符；测试!/%/_/反斜杠。筛选与total/count查询同一条件，popular采用真实点赞数并稳定分页；不逐卡发SQL。

### 安全渲染允许列表

markdown-it：html=false、linkify=false；不加载第三方扩展或高亮器。DOMPurify允许标签仅p/br/hr/h1–h6/blockquote/ul/ol/li/pre/code/strong/em/s/table/thead/tbody/tr/th/td/a/img；属性仅href/title/src/alt/rel/target/loading/class，class仅保留解析器代码语言标识。禁止style/id/name/srcset/data-*及aria-*，ALLOW_DATA_ATTR=false、ALLOW_ARIA_ATTR=false，返回普通字符串（RETURN_TRUSTED_TYPE=false）。库实例及hook独立，不能修改其他调用者的全局hook。

链接在解析器validateLink和最终清理hook中都验证：拒绝控制字符和反斜杠；允许http/https绝对URL（URL解析后协议匹配且有host），或以单个/开头的站内路径；拒绝//协议相对、javascript/data/vbscript及其他协议。外链target="_blank"和rel="noopener noreferrer"由渲染器在最终清理**之前**加入常量；清理后不拼接不可信HTML。

Markdown正文图片只接受本版本上传生成的规范路径：`^/uploads/[0-9]{4}/(0[1-9]|1[0-2])/[0-9a-f]{32}\.(jpg|png|webp)$`。拒绝jpeg（上传已统一jpg）、大写、%、编码分隔符、点段、反斜杠、查询、fragment、协议相对和外链；合法例`/uploads/2026/11/0123456789abcdef0123456789abcdef.png`。非法图片显示经文本转义的alt占位，不加载资源。允许正文以外的历史头像/封面继续复用M2更宽的ManagedImagePaths安全验证（真实路径/白名单后缀），不为正文规范路径限制破坏既有资料契约；这是有意的安全子集。所有cover/images保存和发布时仍重新验证真实磁盘路径。

渲染组件用安全HTML展示；图片失败只以常量文本或textContent插入占位，不把alt拼进innerHTML。既有Vitest(jsdom26.1.0，frontend/vite.config.ts)可直接做DOM断言，无需新浏览器测试框架。

### 夹具与执行权限

Codex拥有真实HTTP执行与共享helper；测试库m3g唯一前缀可创建自己的users/destinations/guides/guide_images/guide_tags/guide_likes/guide_favorites/guide_comments，用于聚合、HIDDEN和权限验证，按依赖清理自己的记录。超大ID无需显式占用数据库主键：API记录与相邻ID用字符串断言、前端使用合成9007199254740993；若需真实大ID，仅独立临时测试实例另行规划，不更改共享库AUTO_INCREMENT。DSH不能读取.env或运行真实DB。状态矩阵、发布重验、并发目的地停用、tag.id定序、金额边界和MarkdownDOM载荷分别加入对应Task测试。

### 实施时事务裁定

攻略写事务显式READ_COMMITTED，并先FOR UPDATE锁定攻略；其后关系读取获得最新已提交数据，避免认证SELECT在REPEATABLE_READ建立的旧快照使等待锁后的幂等响应/发布校验读取旧关系。目的地仍FOR UPDATE当前读，M2管理员事务仍REPEATABLE_READ；所有作者写入口遵守同一攻略锁。公开/私有多查询列表及详情采用只读REPEATABLE_READ保持一次投影一致。该选择不改变API，GuideApiTest同时发布/空白编辑与目的地停用等待用例实际验证。
