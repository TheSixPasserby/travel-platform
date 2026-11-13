# M4 点赞、收藏、评论与我的收藏设计

本页为历史阶段记录，日期、测试数量和当时范围按原记录保留。当前使用与开发入口见 [项目文档](../../README.md)。

日期：2026-11-03。阶段：设计与前置审查准备，M3验收闭合后实施。复用现有guide_likes、guide_favorites、guide_comments，无DDL、无冗余计数、无消息队列或缓存。搜索/目的地/标签/分页复用M3已经实现的查询，不重做攻略模块。

## API与可见性

已有PUT/DELETE /guides/{id}/like、PUT/DELETE /guides/{id}/favorite保持幂等。新增GET /guides/{id}/interaction（登录）读取当前用户状态；四个写入口与GET均返回GuideInteractionVO：guideId（字符串）、liked/favorited（boolean）、likeCount/favoriteCount/commentCount（数字），HTTP200。单独读取viewer状态避免给M3公开投影混入私有信息。未登录401，非PUBLISHED/已删攻略40401，所有动作实时检查ACTIVE用户并从认证上下文取ID，客户端不指定userId。

写事务READ_COMMITTED：先FOR UPDATE锁目标攻略，确认公开，再插入/删除当前用户关系。已有(user_id,guide_id)唯一键为最终防重；PUT重复不重置created_at，DELETE不存在关系仍200。选择明确noop的ON DUPLICATE KEY UPDATE或锁内检查插入，不用REPLACE/宽泛INSERT IGNORE。[MySQL8.4官方](https://dev.mysql.com/doc/refman/8.4/en/insert-on-duplicate.html)提供唯一键冲突更新语义。取消关系物理删除，无逻辑删除字段；隐藏/撤回/删除攻略不清除原关系，恢复公开后viewer状态和收藏重新出现。

GET /users/me/favorites为登录用户私有分页，参数page/size/keyword沿用通用约定。仅返回当前PUBLISHED、未删除攻略的GuideSummaryVO，按收藏created_at DESC、收藏id DESC稳定排序；不能访问其他用户收藏。M3原有真实计数、热门排序随关系变更直接生效，不存计数列。count与页数据使用同一只读REPEATABLE_READ快照，按页批量装配作者/目的地/标签。

## 评论

GET /guides/{id}/comments公开、服务端分页，page默认1/size10且最大50；先确认攻略公开，只返回NORMAL且未删除评论，created_at DESC、id DESC。POST同一路径仅登录，body恰含content：strip后1–2000 Unicode码点，纯文本、保留中间换行，不接受authorId/guideId/status等字段。成功201，CommentVO恰含id、guideId、author（id/nickname/avatarUrl/bio）、content、createdAt、updatedAt。ID字符串，时间+08:00毫秒，无password_hash/内部状态。

POST事务锁攻略后确认公开，再写NORMAL/is_deleted=0；失效账号不能写。公开评论计数继续只计NORMAL未删除。前端用Vue文本插值和换行样式展示，不把评论当HTML或Markdown。提交期间禁止重复提交，失败保留输入；POST不是内容去重接口，正常重复内容不增加新唯一约束。

DELETE /comments/{id}为**作者资源管理入口**，不是增加公开内容的互动入口。Codex明确补充：与HIDDEN攻略可由作者删除一致，作者可以删除本人评论，即使该评论被隐藏、父攻略撤回/隐藏/逻辑删除；这不会增加任何公开可见性。未登录401；目标不存在/已删404；存在但非本人403（ADMIN也不代删）；成功200/data=null，第二次404。只做逻辑删除，不删除攻略/文件。API第13节同步澄清“互动写入先公开”针对点赞、收藏、发表评论，不阻止作者管理自己的隐藏内容。

删除锁顺序为攻略→评论：先非锁定读取评论父ID以定位，再锁攻略当前行和评论当前行，复核父ID/所有权/未删除再更新；父ID不可被客户端修改。后续管理员评论治理也必须遵循相同顺序，不能形成反向锁。发布/撤回/隐藏等攻略写继续使用M3攻略锁。异常沿用40001/40101/40301/40401/40902，不返回SQL或堆栈。

## 前端

GuideActions组件接入公开详情，显示点赞/收藏真实计数与选中状态。匿名点击前往登录并保留本攻略回跳；登录后读interaction状态，刷新仍保持。单一pending动作串行化两个按钮，收到完整VO后同时更新状态和计数；不凭空乐观加减，失败保持已知状态，可重试；加载失败不能误显示“未赞/未收藏”并发送相反动作。监听guideId/当前用户变更并丢弃迟到响应。

评论数由评论列表的当前请求结果更新，互动组件只回写点赞/收藏数，避免迟到的互动GET把新评论计数覆盖。

GuideComments组件在详情分页显示评论。匿名可读，登录可写；2000码点计数，按作者ID显示本人删除入口，删除有明确确认；普通用户无管理员治理按钮。发布成功清空输入并刷新第一页/计数，失败保留；删除后重载当前页，越界时给显式返回第一页入口。401写入失败保留本页评论文字和可解释登录入口（不承诺跨登录导航保存草稿，不将草稿写入跨用户共享存储），公共详情本身仍可阅读；全局会话规则沿用M2。

新增/me/favorites受保护页面，使用真实服务端分页和GuideCard；收藏被隐藏/撤回时不展示，恢复后回来。个人资料/我的攻略提供入口，不用占位收藏卡片。已有登录页guestOnly守卫在用户已有有效会话且存在安全内部redirect时应直接回该目标（无redirect仍/me）；若目标仍为login/register等guestOnly页则回/me防循环，外部目标继续拒绝，用路由行为测试固定。

## 验收与边界

真实MySQL HTTP验证：匿名写401、重复/并发PUT每对一条、取消幂等、首次收藏时间不变、多用户独立状态与真实计数、非公开攻略拒绝、关系保留/恢复、私有收藏分页/字面keyword、评论长度与未知字段/XSS文本、用户B及ADMIN不能删除A评论、隐藏评论/隐藏父攻略的作者删除、删除二次404、并发撤回与互动不产生不可见入口写入。夹具m4i随机前缀，仅独立测试库，Codex执行，DSH不读凭据。

前端行为测试覆盖登录回跳、初始化真实选中、pending防重、失败保留、迟到状态、评论安全文本/分页/输入恢复、收藏空/错/加载。浏览器用两随机账号完成点赞收藏→刷新保持→我的收藏→评论→越权拒绝→取消/删除；手机无页面溢出，精确清理自建数据。前置审查→失败测试→实现→全套构建/审查/浏览器→状态闭合，不先堆叠行程代码。

## 前置审查裁定补充

DSH hub44认为架构可实施，要求先明确4项约束。Codex裁定：公共详情不是requiresAuth路由，M2全局401仅清理会话，不自动跳走公共页；评论失败后在当前组件保留文字，并提供登录CTA。跨登录导航不承诺保留草稿，不把未提交内容存到跨用户共享存储。新评论数只由当前评论列表结果回填，互动VO的commentCount不覆盖该来源；两个读取各有版本号。

评论规范化顺序固定：先拒绝C0/C1控制字符（Character.isISOControl，唯独TAB/LF/CR允许）；再把CRLF及单独CR改LF；去除首尾Unicode空白、U+200B和U+FEFF；最后按Unicode码点校验1–2000。后端Unicode模式的`\\s`加200B/FEFF与前端`\\s`加200B/FEFF，在已拒控制字符的有效输入域一致；NEL U+0085已被前置控制校验拒绝。NBSP/全角空格在首尾去除，内部换行/TAB各计1码点，emoji计1，组合符分别计数；U+200C/200D保留并各计1，不破坏组合文字/emoji。前后端用同一组边界样例断言；前端失败不改写原输入，仅POST成功清空。

安全redirect继续复用既有resolveRedirectTarget，其拒外域、协议相对、反斜杠/控制字符及危险编码路径；guestOnly守卫再resolve内部目标，若仍为guestOnly或当前登录/注册页，回/me避免循环。不对合法URL编码的中文query一概拒绝。测试覆盖正常攻略回跳、login/register循环、外域及编码反斜杠/双斜杠，原工具已有安全断言保留。

HIDDEN夹具仅由Codex在已验证独立测试库，对m4i前缀自身账号/攻略/评论精确SQL置状态；父攻略DRAFT用撤回API，HIDDEN和逻辑删除用自己的SQL夹具（或作者删除API）。生产M4不增加治理入口，ADMIN不能从作者删除接口代删。删除非本人隐藏评论仍403，与私有攻略作者接口统一，不返回内容；此状态码存在性差异属于既定私有资源契约。

并发可见性以攻略行锁为线性化点：互动/评论先持锁提交则关系合法，随后撤回只隐藏并保留；撤回先持锁提交则新互动/评论404。收藏只显示当前公开数据；状态变化下offset分页跨请求可移动，同一次count/page保持RR快照，不承诺跨请求游标快照。likes/favorites未声明@TableLogic，取消必须物理DELETE。公开M3摘要/详情本已有commentCount，无需再扩展投影。
