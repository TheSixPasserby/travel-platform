# M6 基础后台设计

日期：2026-11-04。M1–M5已验收。目标为毕业设计提供真实用户启停、攻略/评论状态治理及已有目的地维护入口。沿用USER/ADMIN两角色和现有表，不加复杂RBAC、审计表、队列、缓存或物理删除接口。

## 方案与权限

选用现有模块内扩展业务Service与Mapper、后台专用DTO/VO，而非另建独立后台服务或让管理员复用作者编辑接口。用户端保留当前旅游平台UI；后台采用清晰导航、服务端分页和状态确认操作，复用自定义样式，不新增Element Plus依赖。

所有/admin入口必须在Service再次requireAdministrator：当前ACTIVE未删账号且role=ADMIN。前端requiresAuth+requiresAdmin只改善体验。用户状态只有ACTIVE/DISABLED，角色不可通过API修改，管理员不代改作者正文、不发布草稿、不通过作者接口删除他人评论。所有ID字符串、时间+08:00毫秒；列表不返回密码哈希、token、isDeleted或完整实体。

不新增用户/目的地物理删除、角色变更、密码重置接口；M5确认的无硬删路径继续成立。禁用不下架原公开资料或攻略，内容治理独立。每个新受保护请求仍实时检查用户可用状态，旧token不能继续访问受保护业务。

保持原会话生命周期：禁用期间所有旧token均401；不新增跨事务会话踢出机制。重新启用后，未过期令牌按原实时状态规则可用；浏览器收到401已经清掉本地token，需重新登录。此为临时账号停用，不声称永久撤销全部历史令牌。

## 用户禁用并发保护

读取列表RR。修改状态RC：先查当前管理员身份，再按id升序FOR UPDATE读取全部role=ADMIN的行（包含DISABLED/逻辑删除的ADMIN，只有ACTIVE未删计为可用），映射显式flushCache=true/useCache=false；持锁后从当前行集重新核验操作者仍为可用ADMIN。再FOR UPDATE锁目标用户并复核未删，目标不存在40401。

DISABLED目标若等于操作者，40902；若目标当前为可用ADMIN且禁用后可用ADMIN少于1，40902。重复同目标状态200且不改updatedAt。全部用户状态写都先锁管理员集合，因此A/B互相禁用并发时，一个成功后另一个重新核验已失效操作者而40101，不能把最后管理员也禁用。角色没有API变更路径，不依赖普通快照COUNT决定最后管理员。按固定锁序避免两个管理员先各锁对方造成环。

用户分页page/size/keyword/status/role，keyword匹配username或nickname，LIKE字面转义，created_at DESC/id DESC，count/page同RR快照。返回AdminUserVO八字段，见API21。状态PATCH仅status，不能夹带role/userId/password。

上述缓存清理仅指MyBatis SqlSession一级缓存，不是认证用户缓存；不新增缓存机制。已开始的内容治理事务不因并发停用强制回滚，后续请求仍实时拒绝，用户状态写本身还必须执行锁后操作者重核。

## 攻略与评论治理

GuideService扩展adminList/adminStatus，复用已有安全摘要与真实计数装配。管理员列表包括未删DRAFT/PUBLISHED/HIDDEN，page/size/keyword/status，created_at DESC/id DESC；不与公开列表混用可见性。新增固定adminPage SQL，count及COUNTS复用，不改公开分页排序。

攻略PATCH只允许目标HIDDEN/PUBLISHED；当前DRAFT一律40902，不代作者发布。PUBLISHED↔HIDDEN及同状态幂等，不改title/content/author/首次publishedAt/图片/标签/点赞收藏关系；变化才更新updatedAt。事务RC先锁攻略当前行，已删404。恢复不额外重验历史图片/停用目的地，以免治理改变作者历史数据；公开入口继续执行既有状态闸门。

没有首次publishedAt的异常HIDDEN记录也拒绝恢复40902，避免把从未发布的数据借治理入口变成公开攻略；正常产品隐藏来源只能是已发布攻略。

CommentService扩展adminList/adminStatus。评论列表包括所有NORMAL/HIDDEN未删评论，keyword匹配content，page/size/status，created_at DESC/id DESC，批量安全作者投影及guideTitle。治理不编辑正文、不解除isDeleted。沿用攻略→评论锁序：普通读定位guideId，锁原始攻略（含逻辑删除），再锁评论当前行复核关系/未删。可治理隐藏、撤回或已删父攻略下的未删评论；这不会增加父内容公开可见性。NORMAL↔HIDDEN幂等，真实公开评论计数只计NORMAL未删，父攻略不公开时所有公开评论入口仍404。

## 页面与边界

/admin/users、/admin/guides、/admin/comments为受保护后台页面，导航并列已有/admin/destinations。SiteHeader管理员入口改为“管理后台”。用户页独立，攻略/评论采用限定resource的共用治理页（不是任意资源通用CRUD引擎）。列表用真实服务端分页/搜索/状态筛选，用户另有角色筛选；桌面表格或清晰行卡，手机重排。

状态修改均明确确认目标和影响，确认存在时禁用其他行与筛选入口，不能覆盖目标。请求pending防重，无乐观成功；收到真实VO后刷新当前页。失败保留上下文/确认供重试；401回登录保留原受保护路径，403显示拒绝，不泄漏旧数据。路由/资源/账号上下文变化及卸载让旧请求失效，迟到401不把公共页面送回登录。公开内容与评论均纯文本/安全Markdown原规则，不在管理列表执行HTML。

API9已有dashboard summary为轻量P2附属：只有P1治理核心GREEN后，用四个COUNT返回userCount/guideCount/destinationCount/commentCount（各自未逻辑删除，管理口径包含各种状态），管理员可读；不做图表、实时统计或新增数据设施。不挤占核心治理验收。

## 验收与执行门

本设计/计划/API21自审和DSH前置审查闭合后，先真实HTTP/前端失败测试再实现。后端最低覆盖：匿名401、普通用户403/旧管理员禁用401、安全投影/严格JSON、分页字面LIKE、自己禁用40902、管理员交叉禁用并发至少一位可用、重复状态幂等、隐藏/恢复攻略保首次发布时间/关系/公开闸门/DRAFT拒绝、隐藏/恢复评论更新真实计数/软删不复活、作者不能解除隐藏/ADMIN不能借作者接口代删。

前端覆盖真实分页/筛选、确认目标固定、防重/失败重试/无乐观状态、当前账号不可禁用、迟到响应/401与文本安全。真实浏览器用临时ADMIN和作者账号：启停用户影响旧会话、隐藏攻略公开404/恢复、隐藏评论计数变化、普通用户直接管理API403；手机可读。最后精确清理自己的夹具、全套package/Vitest/types/Vite、独立审查、状态更新再验收。Codex负责共享契约/跨模块最终决策，DSH仅Flash/Max限定编码或只读审查；不自动commit。
