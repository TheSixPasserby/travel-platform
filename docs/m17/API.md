# M17 增量接口契约

沿用 /api/v1、统一响应、字符串 ID 和既有 PageResult。历史评论的 rootId、replyToId 为 null。

## 评论与提及

GET /guides/{id}/comments 继续返回根评论分页，page/size 默认 1/10。CommentVO 新增 rootId、replyToId、replyToAuthor（PublicProfile 或 null）、replyCount、mentions（PublicProfile[]），其余字段保持。文章 commentCount 继续计根评论数，二级回复由每根 replyCount 独立计数。replyCount 只计公开回复。

POST /guides/{id}/comments 接受 {content,replyToId?:string|null,mentionUserIds?:string[]}。replyToId 可以是本攻略根评论或回复，后端统一归入根评论，仅两级显示。旧 {content} 请求兼容。最多 5 个不同提及，所选账号须有效且正文包含完整 @username 标记。响应为新评论完整 VO。

GET /comments/{rootId}/replies?page=1&size=10 返回公开回复数据库分页，按创建时间/ID 正序。根评论隐藏或删除后其回复不可公开读取。删除保持本人权限，管理员治理沿用现有接口。

GET /comments/{id}/location 返回 {guideId,rootId,rootPage,replyPage}，page 固定大小 10，根评论位置按倒序，回复按正序。隐藏或删除目标返回 404。Web 通知链接 /guides/{guideId}?comments=1&commentId={id}，评论组件通过 location 定位对应页并展开/聚焦。

GET /users/mention-suggestions?keyword=xxx 返回 PublicProfile[]（id/nickname/avatarUrl/bio/username），要求登录，最多 8 个，用户名或昵称匹配，空关键字返回空。正文中纯手输 @ 不会自动创建提及关系。单用户每分钟最多 10 条、每天最多 100 条评论（根评论与回复合计），超限 HTTP 429/42901。事务先共享锁定当前用户，再锁定专用 comment_write_locks 行保证并发频控，避免互相提及时的用户外键锁升级死锁。

## 通知

GET /notifications?page=1&size=10&unreadOnly=false 返回当前用户通知分页。每项 {id,type,actor:PublicProfile,guideId,commentId,content,createdAt,readAt,href}，类型 COMMENT/REPLY/MENTION。顶层评论通知攻略作者，回复通知直接目标作者，提及通知显式选择用户，同一动作每收件人至多一条，优先 MENTION、REPLY、COMMENT，本人不接收本人动作。

GET /notifications/unread-count 返回 {count}。PUT /notifications/{id}/read 返回 null，仅本人，重复幂等，越权 404。PUT /notifications/read-all 返回 null，限定当前用户。所有通知查询即时排除已隐藏/删除的攻略、评论、根评论及失效作者。无法跳转的旧消息不会泄露正文。

## 创作者与分享

GET /users/by-username/{username}/destinations?page=1&size=10 返回公开攻略中关联的有效目的地足迹分页，项 {id,name,destinationType,coverImageUrl,guideCount}。这表示公开作品提及地点，界面应标为“手记中的目的地”，不声称真实到访。私人行程和收藏不参与统计。

GET /share/guides/{id} 和 GET /share/users/{username} 为无需 JavaScript 的公开分享 HTML，含 title/description/OG/Twitter/canonical 和可见页面链接。Web 分享链接指向此端点。GET /sitemap.xml 仅列公开攻略和有效作者（受控上限），GET /robots.txt。配置 travel.site.public-base-url / PUBLIC_BASE_URL 代表 Web 公共 origin，默认 http://localhost:5173。开发代理应转发 /share、/sitemap.xml、/robots.txt。实际生产域名由部署时配置。

## 迁移与启动

既有M15业务库切换前依次执行database/migrations/V016_01__guide_trip_imports.sql和V017_01__community_threads_notifications.sql。仅在操作者明确目标并保留备份与恢复记录后应用，启动器不自动迁移。当前新空库schema与升级库的18表、151列、31外键、索引及CHECK已比对一致，V017_01在隔离库重复执行通过。旧应用回退保留新增数据结构。

本轮仅在独立测试库及恢复副本应用迁移。原业务库仍为14表，尚不能直接运行依赖新增关系的当前端点。生产Web入口需将/share、/sitemap.xml、/robots.txt转发至后端，并设置PUBLIC_BASE_URL为实际Web公共origin。
