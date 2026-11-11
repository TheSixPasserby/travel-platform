# REST API 契约草案

统一前缀 `/api/v1`；JSON；Sa-Token；服务端分页。

## 1. 统一响应
```json
{"code":0,"message":"success","data":{}}
```
分页 data：
```json
{"records":[],"total":0,"current":1,"size":10,"pages":0}
```
错误使用合适 HTTP 状态码和稳定的整数业务码，准确映射见第 11 节；不混用字符串 code。

## 2. 认证与用户
| Method | Path | 说明 | 权限 |
|---|---|---|---|
| POST | `/auth/register` | 注册 | 公开 |
| POST | `/auth/login` | 登录 | 公开 |
| POST | `/auth/logout` | 退出 | 登录 |
| GET | `/auth/me` | 当前用户 | 登录 |
| GET | `/users/me` | 我的资料 | 登录 |
| PATCH | `/users/me` | 修改资料 | 登录 |
| GET | `/users/{id}/profile` | 公开用户资料 | 公开 |
| GET | `/users/me/guides` | 我的攻略 | 登录 |
| GET | `/users/me/guides/{id}` | 我的攻略编辑数据（含草稿、隐藏状态） | 作者 |
| GET | `/users/me/favorites` | 我的收藏 | 登录 |

## 3. 攻略
| Method | Path | 说明 | 权限 |
|---|---|---|---|
| GET | `/guides` | 攻略分页/搜索 | 公开 |
| GET | `/guides/{id}` | 攻略详情（仅公开内容） | 公开 |
| POST | `/guides` | 创建攻略 | 登录 |
| PUT | `/guides/{id}` | 编辑攻略 | 作者 |
| DELETE | `/guides/{id}` | 删除攻略 | 作者 |
| POST | `/guides/{id}/publish` | 发布草稿 | 作者 |
| POST | `/guides/{id}/unpublish` | 撤回发布 | 作者 |

查询参数：`page`、`size`、`keyword`、`destinationId`、`tagId`、`sort=latest|popular`。

标签为 P1；`GET /tags` 公开返回 `[{"id":"1","name":"美食"}]` 形式的种子标签列表，供攻略编辑和筛选选择。攻略创建/编辑使用 `tagIds` 字符串 ID 数组，重复或不存在的标签 ID 返回 40001。第一版不增加标签管理后台。

## 4. 互动与评论
| Method | Path | 说明 | 权限 |
|---|---|---|---|
| PUT | `/guides/{id}/like` | 点赞（幂等） | 登录 |
| DELETE | `/guides/{id}/like` | 取消点赞 | 登录 |
| PUT | `/guides/{id}/favorite` | 收藏（幂等） | 登录 |
| DELETE | `/guides/{id}/favorite` | 取消收藏 | 登录 |
| GET | `/guides/{id}/comments` | 评论分页 | 公开 |
| POST | `/guides/{id}/comments` | 发表评论 | 登录 |
| DELETE | `/comments/{id}` | 删除评论 | 评论作者 |

## 5. 目的地
| Method | Path | 说明 | 权限 |
|---|---|---|---|
| GET | `/destinations` | 目的地分页/筛选 | 公开 |
| GET | `/destinations/{id}` | 目的地详情 | 公开 |
| GET | `/admin/destinations` | 管理列表（含停用目的地，分页） | 管理员 |
| POST | `/admin/destinations` | 新增目的地 | 管理员 |
| PUT | `/admin/destinations/{id}` | 修改目的地 | 管理员 |
| PATCH | `/admin/destinations/{id}/status` | 启用/停用 | 管理员 |

## 6. 行程
| Method | Path | 说明 | 权限 |
|---|---|---|---|
| GET | `/itineraries` | 我的行程分页 | 登录 |
| POST | `/itineraries` | 创建行程 | 登录 |
| GET | `/itineraries/{id}` | 行程详情 | 所有者 |
| PUT | `/itineraries/{id}` | 修改行程 | 所有者 |
| DELETE | `/itineraries/{id}` | 删除行程 | 所有者 |
| POST | `/itineraries/{id}/days` | 新增每日安排 | 所有者 |
| PUT | `/itinerary-days/{dayId}` | 修改每日安排 | 所有者 |
| DELETE | `/itinerary-days/{dayId}` | 删除每日安排 | 所有者 |
| POST | `/itinerary-days/{dayId}/items` | 添加游玩项目 | 所有者 |
| PUT | `/itinerary-items/{itemId}` | 修改游玩项目 | 所有者 |
| DELETE | `/itinerary-items/{itemId}` | 删除游玩项目 | 所有者 |
| PUT | `/itinerary-days/{dayId}/items/order` | 调整项目顺序 | 所有者 |

资源所有权必须通过数据库关联确认，不能只凭客户端 userId。

## 7. 文件
| Method | Path | 说明 | 权限 |
|---|---|---|---|
| POST | `/files/images` | 上传图片（multipart/form-data） | 登录 |

限制 MIME、扩展名、大小，随机生成文件名，拒绝路径穿越。

## 8. AI 行程规划（M8）
| Method | Path | 说明 | 权限 |
|---|---|---|---|
| GET | `/ai/itineraries/capabilities` | 查看服务配置可用状态 | 登录 |
| POST | `/ai/itineraries/preview` | 结构化整程只读预览 | 登录 |
| POST | `/ai/itineraries/day-preview` | 指定日期只读预览 | 登录 |
| POST | `/itineraries/import` | 明确确认后原子创建新行程 | 登录 |

请求和响应的精确结构、限制与错误码见第24–25节。

## 9. 管理员
| Method | Path | 说明 | 权限 |
|---|---|---|---|
| GET | `/admin/users` | 用户分页 | 管理员 |
| PATCH | `/admin/users/{id}/status` | 禁用/启用用户 | 管理员 |
| GET | `/admin/guides` | 全站攻略管理 | 管理员 |
| PATCH | `/admin/guides/{id}/status` | 隐藏/恢复攻略 | 管理员 |
| GET | `/admin/comments` | 评论管理 | 管理员 |
| PATCH | `/admin/comments/{id}/status` | 隐藏/恢复评论 | 管理员 |
| GET | `/admin/dashboard/summary` | 基础统计 | 管理员 |
| GET | `/admin/ai-settings` | 读取全站 AI 设置与密钥配置状态 | 管理员 |
| PUT | `/admin/ai-settings` | 保存全站 AI 设置，后续请求立即生效 | 管理员 |

## 10. 契约要求
SpringDoc 生成接口文档；前端类型与响应一致；不返回 password_hash；写接口必须认证和检查资源权限；限制最大 page size；API 变更同步更新本文件和测试。

## 11. M0 确定的通用约定

以下为 Codex 在 2026-11-02 的基线裁定，尚未声称已实现；模块实现时同步 DTO、VO、前端类型与测试。

- 登录返回 `{"tokenName":"satoken","tokenValue":"<opaque-token>","user":{...}}`；受保护请求仅使用 `satoken` 请求头，不从 Cookie、查询参数或请求体读取 token。使用 Sa-Token 单体内存登录态，超时 86400 秒，不引入 Redis。每次登录创建独立 token；退出仅使当前 token 失效。
- 前端收到401仍抛对应ApiError，但仅当该请求携带的令牌与当前会话相同（或两者均匿名）才清理会话并触发导航；旧账号/旧匿名请求迟到的401不能清掉新登录。后端HTTP/业务码不变。
- 开发 CORS 仅允许配置白名单（默认 `http://localhost:5173`），允许 `satoken`/`Content-Type` 请求头，不使用 Cookie 凭据或通配来源；部署使用同源反向代理。配置约定不代表当前已有后端实现。
- 所有 ID 和外键 ID 在 JSON 中使用十进制字符串，前端不得转为 Number；请求 ID 必须为正整数且不超 Java Long 上限。仅 ID 字段字符串化，分页 total/current/size/pages 与互动计数仍为数值，不使用把所有 Long 一律转字符串的全局配置。
- 创建/更新时间返回带 `+08:00` 的 ISO 8601 字符串；旅行日期为 `yyyy-MM-dd`，活动时间为 `HH:mm`。金额使用十进制字符串，对应 DECIMAL(10,2)，最多两位小数且非负。
- 分页默认 `page=1,size=10`，page >= 1，1 <= size <= 50，非整数或越界返回 400，不静默截断。空结果 `records=[],total=0,pages=0`；超出末页返回空 records，保留真实 total/current/size/pages。
- keyword 去除首尾空白、最多 100 字符，空白视为无关键词；LIKE 通配字符按字面转义。sort 只接受 latest/popular。latest 按 published_at、id 降序；popular 按点赞关系计数、published_at、id 降序，避免同值分页顺序不稳定。
- 响应 `code` 统一为整数：0 成功；40001 参数错误；40101 未登录/凭据失效；40102 账号或密码错误；40301 权限不足；40401 资源不存在或对当前公开入口不可见；40901 唯一值冲突；40902 状态冲突；41301 上传过大；41501 不支持的图片；50001 内部错误。HTTP 状态分别对应 200/201、400、401、403、404、409、413、415、500。内部错误只返回通用信息。
- 错误体仍为 `{code,message,data}`：40001 的 data 可为 `{"fieldErrors":[{"field":"username","message":"格式不正确"}]}`，其他错误 data 为 null。不回显密码、token、原始被拒绝值、SQL 或堆栈。

## 12. M1 认证与资料 DTO/VO

| 接口 | 请求 | 成功响应 data / HTTP |
|---|---|---|
| POST /auth/register | username、password、可选 nickname | 当前用户安全资料 / 201，不自动登录 |
| POST /auth/login | username、password | tokenName、tokenValue、user / 200 |
| POST /auth/logout | 无请求体 | null / 200 |
| GET /auth/me、GET /users/me | 无 | 当前用户安全资料 / 200，两者同一投影 |
| PATCH /users/me | nickname、avatarUrl、bio 中至少一项 | 修改后的当前用户安全资料 / 200 |

username 为 3–50 个 ASCII 字母、数字或下划线，去首尾空白并统一小写后查重；账号唯一性依赖数据库约束。password 不 trim，至少 8 个 Unicode 码点且 UTF-8 不超过 72 字节，不得全空白。nickname 去首尾空白后为 1–50 字符；bio 最多 300 字符，可用 null 清空；avatarUrl 可用 null 清空，否则必须是有效的受管图片路径且长度不超过 500。

当前用户安全资料仅含 id、username、nickname、avatarUrl、bio、role、status、createdAt；公开用户资料仅含 id、nickname、avatarUrl、bio、username（M10 兼容新增；已注销作者为 null）。密码和 password_hash 不进入任何 VO。请求只接受白名单字段，夹带 userId、role、status、password_hash 等字段返回 400；字符串字段不接受数字或布尔值的隐式转换。PATCH 未提供的字段保持原值。

不存在账号与密码错误均返回 401/40102 和同一通用提示。被禁用或逻辑删除的用户不能登录；已登录用户每次受保护访问都检查账号可用状态，禁用后旧 token 不得继续访问受保护业务。禁用账号不自动下架既有公开攻略或公开资料，内容治理独立执行。管理员不得禁用自己或最后一个可用 ADMIN，并发状态更新也需维持该约束。

## 13. 攻略、互动与内容治理

| 当前状态 | 操作 | 权限与结果 |
|---|---|---|
| DRAFT | 编辑、发布、删除 | 作者；发布转 PUBLISHED，仅首次发布设置 published_at |
| PUBLISHED | 编辑、撤回、删除 | 作者；撤回转 DRAFT |
| PUBLISHED | 隐藏 | 管理员专用接口；转 HIDDEN，保留发布时间 |
| HIDDEN | 恢复 | 管理员专用接口；转 PUBLISHED，保留发布时间 |
| HIDDEN | 编辑、发布、撤回 | 拒绝，40902；作者仍可在私有入口查看或删除自己的攻略 |

管理员不通过作者接口代改正文、发布草稿或删除评论；治理通过已有 admin 状态接口完成。创建攻略只允许 DRAFT/PUBLISHED；普通编辑 DTO 不允许指定 HIDDEN、authorId 或逻辑删除字段。编辑、撤回、再次发布和隐藏/恢复均保留首次 published_at。重复发布/撤回目标已处于该状态时返回当前状态；不能借此绕过 HIDDEN 限制。

公开详情、评论读取、点赞/收藏/发表评论和公共统计均先确认攻略为 PUBLISHED 且未删除；不可见时返回 40401。我的收藏只展示当前公开的攻略；隐藏/撤回时保留既有点赞收藏关系，恢复公开后重新展示。公开评论还需为 NORMAL 且未删除。作者删除自己的评论属于资源管理入口，允许管理隐藏评论及已撤回/隐藏/逻辑删除父攻略下的本人评论；他人403，已删/不存在404。作者私有攻略详情可以读取本人未删除攻略的任意状态；他人访问返回 40301。

## 14. 目的地与行程边界

- 公开目的地列表和新建选择默认只返回 ACTIVE 且未删除的数据；未删除的 INACTIVE 详情仍可读取，响应携带 status，以支持历史引用。管理员列表支持状态筛选。
- 新建或替换目的地关联必须指向 ACTIVE 数据；编辑历史记录且未改变关联时保留其原有 INACTIVE 引用。停用不清空历史外键。parentId 不得指向自己或后代。
- 行程日期缩短若排除已有每日安排，返回 40902，保持全部原数据；不自动删日程。dayNo 按 travelDate 与 startDate 的日期差加一由服务端计算。
- 日程/项目请求通过 day → itinerary → user 验证所有权。当天项目排序请求为 `{"itemIds":["1","2"]}`，必须提交该天全部项目 ID 的无重复排列，缺项、重复、跨天或不属于该天的 ID 返回 40001；路径中的日程非本人所有时返回 40301。排序和父子数据更新必须原子完成。

## 15. 图片与后续契约补全

图片以 multipart 字段 `file` 提交，仅接受 JPEG/PNG/WebP，单文件最多 5 MiB；同时检查扩展名、声明 MIME 和实际解码格式，拒绝 SVG。使用服务端随机文件名和受管相对访问路径，数据库不接收磁盘绝对路径。响应 data 为 `{"url":"/uploads/..."}`，不包含磁盘路径或原始文件名。上传失败不得创建攻略或图片关系；攻略、目的地和头像保存时重新验证路径指向存在的受管图片，拒绝外链、路径穿越或不存在路径，返回 40001。

框架拒绝不支持的 HTTP 方法或无法接受的响应媒体类型时，分别保留 HTTP 405/406，并复用请求不符合契约的业务码 40001；响应保持统一错误包，不转换为内部错误 500。

本次固定通用规范、M1 DTO 和已有业务的权限/状态边界。攻略、目的地、上传、行程与 AI 的完整字段 DTO/VO 在各自里程碑实施前补齐，保留本文件为共享契约；没有前后端代码的阶段不声称类型或接口测试已更新。

## 16. M2 目的地 DTO/VO（实现前契约）

目的地列表沿用 page/size/keyword（上限100码点、去首尾空白、LIKE按字面转义），另支持 destinationType、parentId。管理员列表另支持 status；公开列表始终仅 ACTIVE 且未删除，不能通过 status 参数扩大可见性。固定按 sort_order、id 升序；不使用攻略 latest/popular 排序。非法枚举/ID/页参数返回40001。parentId为正Long范围十进制字符串。

| 字段 | POST /admin/destinations | PUT /admin/destinations/{id} | 规则 |
|---|---|---|---|
| name | 必填 | 必填 | strip后1–120码点 |
| destinationType | 缺失默认CITY | 必填 | CITY/SCENIC_SPOT/OTHER；null拒绝 |
| country | 缺失默认中国 | 必填 | strip后1–80码点；null拒绝 |
| status | 缺失默认ACTIVE | 必填 | ACTIVE/INACTIVE；null拒绝 |
| sortOrder | 缺失默认0 | 必填 | 非负32位整数；null拒绝 |
| parentId | 缺省null | 缺省null | 字符串ID或null |
| province/city | 缺省null | 缺省null | 各最多80码点 |
| description | 缺省null | 缺省null | 最多10000码点，纯文本 |
| coverImageUrl | 缺省null | 缺省null | 最多500字符，实际存在的受管图片路径 |
| recommendedSeasons | 缺省null | 缺省null | 最多120码点 |

POST返回201，PUT返回200；PUT省略的可空字段清空，必填字段不回退到创建默认值。未知/内部字段、数值或布尔到字符串强转均拒绝。PATCH /admin/destinations/{id}/status只接受必填非null的status，返回200，重复目标状态幂等；PUT与PATCH使用同一状态规则。

DestinationVO恰含id、name、destinationType、parentId、country、province、city、description、coverImageUrl、recommendedSeasons、status、sortOrder、createdAt、updatedAt。id/parentId为字符串或null；sortOrder数字，时间为+08:00毫秒，可空字段保留。

parentId须指向存在且未删除的目的地，不可自己/后代；新建或改变引用要求ACTIVE，未改变的历史INACTIVE引用保留。无效父引用/环/封面返回40001，目标不存在40401；唯一名+类型冲突40901（含已逻辑删除名称占用）；并发写冲突40902并提示重试。管理员写事务内显式REPEATABLE_READ并按主键有序FOR UPDATE读取目的地图后验证/修改，最终不得形成环；不修改既有约束或删除历史引用。

## 17. M2 图片接口与边界（实现前契约）

POST /files/images仅登录用户，multipart字段file且仅一个文件；成功201，data={"url":"/uploads/..."}。文件1字节至5MiB，扩展名、声明MIME与实际解码格式一致；JPEG(jpg/jpeg,image/jpeg)、PNG(png,image/png)、WebP(webp,image/webp)，不支持SVG。有效5MiB必须可上传，超5MiB文件或超6MiB总multipart请求返回413/41301；空文件/缺file/多file返回40001，不支持或无法解码返回41501。读取头部尺寸后再解码，单边最多10000、总像素最多2500万，超尺寸40001。

服务端随机文件名、按年月存储，验证完成后先写自身临时文件再原子发布；失败仅清本次临时文件，不创建任何业务记录、不返回磁盘路径/原始文件名。图片在第一版不自动孤儿回收。资料/目的地/后续攻略保存时再次校验实际受管路径。

GET /uploads/**为公开图片读取，位于/api/v1之外；仅白名单扩展名的实际受管文件，normalize+realPath限制在真实根内，防穿越及符号链接越界。准确Content-Type并设置X-Content-Type-Options:nosniff；无效/不存在的受管路径返回40401，不暴露目录。容器先拒绝的畸形URL可返回400。Vite代理/uploads以同源显示图片。

## 18. M3 攻略 DTO/VO（前置设计契约）

POST /guides和PUT /guides/{id}共同白名单为title、content、summary、destinationId、coverImageUrl、travelDays、budgetPerPerson、season、tagIds、images、scenicSpotIds（详见第23节）；POST另含status（缺失DRAFT，显式null拒绝，只允许DRAFT/PUBLISHED），PUT不接受status。POST201，其余写成功200；DELETE成功data=null。用户ID只从认证上下文取得。

title必填strip后1–180码点；content必填字符串且≤100000码点，保留Markdown空白，DRAFT可空字符串，创建PUBLISHED/编辑PUBLISHED/发布转换时不得全空白（40001）。summary可null≤500、season可null≤40，strip空串归null；destinationId可null，按第14节历史引用规则；coverImageUrl可null，实际受管路径≤500。travelDays为Integer/null且1–365。budgetPerPerson为String/null，文法`^(0|[1-9][0-9]{0,7})(\.[0-9]{1,2})?$`，拒绝数字JSON、指数、前导零、负零，响应固定两位小数字符串。

tagIds缺失/null归空，最多10个不重复、存在的字符串ID；响应tags按id升序。images缺失/null归空，最多20项，恰含url与可空altText（≤200码点），url不重复且指向真实受管文件；服务端按数组顺序赋sortOrder=0..n-1。PUT为完整替换，省略的可空字段清空、数组变空，正文/图片/标签同一事务；未知/内部字段及隐式类型强转拒绝。

GuideSummaryVO恰含id、title、summary、coverImageUrl、author（id/nickname/avatarUrl/bio）、destination（id/name/status或null）、travelDays、budgetPerPerson、season、tags（id/name数组）、status、publishedAt、createdAt、updatedAt、likeCount、favoriteCount、commentCount。GuideDetailVO另含content、images（url/altText/sortOrder）、scenicSpots（见第23节）。所有ID字符串、计数数字、时间+08:00毫秒，nullable字段保留。公开status恒PUBLISHED；计数来自实际关系，commentCount仅NORMAL且未删除评论，不增加冗余计数列。

公开列表沿用第11节分页/keyword/sort，筛选destinationId/tagId（合法但不存在ID为空200，非法40001）；标题/摘要LIKE固定ESCAPE '!'，!/%/_按字面转义，反斜杠为普通字符。目的地INACTIVE不隐藏历史攻略，也不阻止筛选。私有GET /users/me/guides另可status=DRAFT/PUBLISHED/HIDDEN，按created_at DESC,id DESC；私有详情仅本人，存在但他人40301。GET /tags公开返回id/name数组按id升序。公开资料只返回既定四个字段。

发布/撤回/隐藏遵守第13节和[M3状态矩阵](superpowers/specs/2026-11-03-m3-guides-design.md)：首次publishedAt永不重写；重复发布/撤回幂等；HIDDEN不可由作者编辑、发布或撤回（40902），可作者删除；二次删除40401。发布转换从持久化行重验字段、图片存在性和目的地有效性；不变的历史INACTIVE引用保留，不存在/逻辑删除关联40001。攻略写和关联替换有数据库行锁与事务，锁冲突40902。

## 19. M4 互动与评论 DTO/VO（前置设计契约）

GET /guides/{id}/interaction、PUT/DELETE /guides/{id}/like、PUT/DELETE /guides/{id}/favorite仅登录且目标公开，成功200，返回GuideInteractionVO：guideId字符串，liked/favorited布尔，likeCount/favoriteCount/commentCount数字。PUT/DELETE幂等，重复收藏不改首次created_at；隐藏/撤回/删除保留关系但公开入口404。用户ID从会话取得，不接受客户端指定用户。

GET /users/me/favorites只查本人，page/size/keyword沿用第11节，按收藏created_at DESC、收藏id DESC，仅展示PUBLISHED未删除的GuideSummaryVO；数据恢复公开后原收藏重现。分页count与records同一只读快照，无他人收藏查询入口。

GET /guides/{id}/comments公开，先确认攻略PUBLISHED未删除，再分页NORMAL未删除评论，created_at DESC、id DESC。POST相同路径只登录，body仅content；成功201返回CommentVO：id、guideId、author（id/nickname/avatarUrl/bio）、content、createdAt、updatedAt。ID字符串，时间+08:00毫秒，无status/isDeleted/密码字段。content按[M4精确规范化](superpowers/specs/2026-11-03-m4-interactions-design.md)拒不支持控制字符、统一换行、去首尾Unicode空白/200B/FEFF后1–2000码点；纯文本，不执行HTML或Markdown。未知字段/类型错误40001。

DELETE /comments/{id}须当前ACTIVE用户为评论作者（ADMIN无代删权），可以删除本人HIDDEN评论及非公开/已逻辑删除攻略下本人评论，只做逻辑删除，200/data=null；不存在/已删40401，其他作者40301。所有涉及同一攻略/评论的写事务按攻略→评论锁序复核，不能只用客户端userId；锁冲突40902。

## 20. M5 私人手动行程 DTO/VO（前置设计契约）

第6节12个入口全部要求ACTIVE登录并验证根行程所有者，ADMIN无例外。列表只本人；路径对象存在但非本人40301，未知/根逻辑删除后的根或子入口40401。写事务先锁根，再复核子关系；根逻辑删除保留子数据，显式删除日程/项目物理删除。错误沿用第11节，不回显内部路径/SQL。

| 请求 DTO | 可编辑字段及规则 |
|---|---|
| POST /itineraries | title、destinationId?、startDate、endDate、notes?、status?（省略ACTIVE，显式null拒绝） |
| PUT /itineraries/{id} | title、destinationId?、startDate、endDate、notes?、status（必填ACTIVE/ARCHIVED） |
| POST /itineraries/{id}/days、PUT /itinerary-days/{id} | travelDate（必填）、title?、notes? |
| POST /itinerary-days/{id}/items、PUT /itinerary-items/{id} | placeName（必填）、destinationId?、addressText?、startTime?、endTime?、notes? |
| PUT /itinerary-days/{id}/items/order | itemIds：该天全部项目ID的无重复完整排列，最多100；空日可空数组，不可null |

?为可空；PUT完整替换，可选字段省略/null均清空。未知字段、非字符串ID/日期/时间/文本、内部userId/dayNo/父ID/sortOrder均40001。文本首尾去Unicode空白，CRLF/CR归LF，拒C0/C1除TAB/LF/CR；规范化后标题/日标题/地点≤160码点、地址≤300、notes≤2000；必填非空、可选空白归null。HTML当纯文本。归档只是本人组织状态，ACTIVE/ARCHIVED均可编辑。

日期严格yyyy-MM-dd，1000–9999年有效日期，start≤end且包含首尾1–366天；时间严格HH:mm，00:00–23:59，可分别留空，两者均有时end≥start。travelDate在父范围，同一天一条；重复日期40901。dayNo由日期差+1服务端生成，不自动创建空日；按travelDate/id升序展示。根日期缩短排除已有日程40902、所有原数据不变；起始日改变只重算dayNo，不移动travelDate，唯一键重编号在同一事务内完成。

行程/项目目的地可空；新建/替换须ACTIVE未删，不变的INACTIVE历史引用保留（规则同第14节）。每日最多100项目、每行程最多500项目，超限40902；新项目追加，删除后sortOrder稠密0..n-1。排序缺项、重复（含等价ID如01与1）、跨日/跨行程ID40001，外人的路径日程40301；先完整校验集合再事务更新，失败不改任何顺序。

成功响应统一包，POST根/日/项目201，其余GET/PUT/DELETE200，DELETE data=null。根POST/PUT/GET返回ItineraryDetailVO；列表GET /itineraries支持page/size/keyword（标题字面搜索）/status（ACTIVE/ARCHIVED），created_at DESC,id DESC，返回分页ItinerarySummaryVO。日POST/PUT及排序返回DayVO，项目POST/PUT返回ItemVO。

- ItinerarySummaryVO：id、title、destination、startDate、endDate、status、dayCount、itemCount、createdAt、updatedAt。
- ItineraryDetailVO：上述10字段加notes、days（DayVO数组）。
- DayVO：id、itineraryId、dayNo、travelDate、title、notes、createdAt、updatedAt、items（ItemVO数组，sortOrder/id升序）。
- ItemVO：id、itineraryDayId、destination、placeName、addressText、startTime、endTime、notes、sortOrder、createdAt、updatedAt。

destination为null或{id,name,status}，无内部字段。所有ID/外键字符串；dayNo/计数/sortOrder数值；时间戳+08:00毫秒，日期yyyy-MM-dd，时间HH:mm/null。无userId、isDeleted或磁盘路径。详情批量取日、项目、目的地，不逐项目查目的地。

## 21. M6 基础后台 DTO/VO（前置设计契约）

全部/admin接口须当前ACTIVE未删ADMIN，普通用户40301、匿名或被禁用账号40101。无角色变更/密码重置/用户或目的地硬删接口；管理员不复用作者接口代改内容或发布草稿。写请求只接受`{"status":"..."}`，缺失/null、未知字段、类型错误或非法枚举40001。不存在/逻辑删除目标40401；成功200统一包，ID字符串、时间+08:00毫秒、计数数值。

| 接口 | 参数/目标状态 | 成功data |
|---|---|---|
| GET /admin/users | page/size/keyword/status=ACTIVE或DISABLED/role=USER或ADMIN | 分页AdminUserVO |
| PATCH /admin/users/{id}/status | ACTIVE或DISABLED | AdminUserVO |
| GET /admin/guides | page/size/keyword/status=DRAFT、PUBLISHED或HIDDEN | 分页GuideSummaryVO |
| PATCH /admin/guides/{id}/status | HIDDEN或PUBLISHED | GuideSummaryVO |
| GET /admin/comments | page/size/keyword/status=NORMAL或HIDDEN | 分页AdminCommentVO |
| PATCH /admin/comments/{id}/status | NORMAL或HIDDEN | AdminCommentVO |
| GET /admin/dashboard/summary | 无；轻量附属统计在P1治理GREEN后实现 | AdminSummaryVO |

分页沿第11节，created_at DESC/id DESC；keyword首尾strip、≤100码点，LIKE字面转义。users匹配username/nickname，guides匹配title/summary，comments匹配content。仅未逻辑删除记录；comments可含非公开或已逻辑删除父攻略下的未删评论，只对ADMIN显示，不扩大公开可见性。count与records使用同一RR快照，不返回实体。

- AdminUserVO恰八字段：id、username、nickname、avatarUrl、role、status、createdAt、updatedAt。
- GuideSummaryVO沿第18节恰17字段，管理员可读实际DRAFT/PUBLISHED/HIDDEN；真实计数不变，无正文或内部字段。
- AdminCommentVO恰八字段：id、guideId、guideTitle、author（id/nickname/avatarUrl/bio/username，复用PublicProfileVO）、content、status、createdAt、updatedAt。
- AdminSummaryVO恰四数字：userCount、guideCount、destinationCount、commentCount，分别统计对应未逻辑删除行，管理口径包含全部状态，非公开平台统计。

用户状态事务RC，先按id有序锁全部ADMIN当前行并清理会话缓存、重核操作者可用，再锁目标。不能禁用自己或最后一位可用ADMIN，40902；并发互相禁用不能把管理员全禁用，等待后操作者已失效则40101。保持M1实时账号校验：禁用期间旧token对全部受保护入口401；不新增永久撤销机制，启用后未过期令牌按原状态规则可用（浏览器401已清本地token，需重新登录）。不自动下架该账号公开内容。

攻略治理只能已发布内容隐藏/恢复，当前DRAFT或没有首次发布时间的异常HIDDEN恢复40902；首次publishedAt、正文、作者及全部图片/标签/点赞收藏关系不变。评论治理只NORMAL/HIDDEN转换，已逻辑删除404且不能复活；按攻略→评论当前锁读，父攻略隐藏/已删不影响管理员治理未删评论，公开读取仍由父可见性限制。所有同状态PATCH幂等200，不重写updatedAt。


## 22. M8 目的地资料与图库

DestinationVO在原字段上增加address、longitude/latitude（六位小数字符串或null）、recommendedDurationMinutes（1–1440）、recommendedDays（1–30）、openingHours、ticketInfo、officialUrl、sourceUrl、verifiedAt（+08:00毫秒时间或null）、images（有序数组，空为[]）。字段上限：地址300、说明1000、链接1000；坐标成对提供，longitude[-180,180]、latitude[-90,90]，小数最多6位，不舍入。开放/票务说明或坐标非空时必须有sourceUrl和verifiedAt；链接仅http/https绝对地址，无userinfo，不自动抓取。

POST/PUT目的地允许上述新增字段；为兼容旧客户端，新字段PUT省略保留，显式null清空；旧字段仍遵循原完整替换规则。图库images省略保留、显式null拒绝、[]清空，上限20，url不重复且必须真实受管文件，客户端不传id/sortOrder。每图字段恰为url、altText（必填1–200）、sourceUrl（必填有效URL≤1000）、author（必填1–300）、license（必填1–100）、licenseUrl（必填有效URL≤1000）、retrievedAt（必填ISO带偏移时间，存为+08:00）。响应每图增加id字符串和sortOrder，名称url保持，时间统一毫秒。父图文/图库写入同事务，图片失败不创建目的地；普通用户403，匿名401。

CITY禁止父级；SCENIC_SPOT必须直接归属CITY，禁止景点父级和循环；仍被景点引用的CITY改类型40902。新父级必须ACTIVE未删，原停用父级引用可保留；OTHER原任意父链语义保留并防环。公开列表不列出父城市已停用的景点；公开详情继续可读INACTIVE历史，供已有攻略/行程说明。类型收紧后的旧防环回归改用OTHER夹具，保留循环与并发保护断言。


## 23. M8 攻略多景点与目的地相关攻略

GuideDetailVO新增scenicSpots（{id,name,status}数组）；GuideSummaryVO维持原结构。攻略POST/PUT可传scenicSpotIds，最多20个正整数字符串ID，归一化后不可重复，须为主目的地CITY的直属SCENIC_SPOT。新增关联的城市与景点必须ACTIVE未删；同城不变的停用历史关联可保留。省略scenicSpotIds保留旧关联（创建为空），显式null拒绝，[]清空；已有关联时改变主城市必须显式提供兼容新城的集合或[]。关联与正文、标签、图片同事务，所有权规则不变；被攻略关联的景点不能直接换城市或改类型（40902）。

GET /destinations/{id}/guides公开，支持page/size/keyword/tagId，返回GuideSummaryVO标准分页；城市匹配攻略主城市或该城下关联景点，景点匹配自身关联，OTHER匹配主目的地。仅PUBLISHED未删，按首次发布时间/id倒序，EXISTS去重，草稿与隐藏攻略不可见；未知目的地40401。停用历史资料仍可查看。路径ID与分页/搜索规范沿第11节。

## 24. M8 整程原子导入

POST /itineraries/import要求ACTIVE登录。只创建新的本人行程，第一次成功201，同用户同clientRequestId重发200返回已保存行程，已删除目标40902。数据库唯一(user_id,client_request_id)与用户行锁共同防重，幂等键大小写按UUID归一。不同用户可以使用相同UUID。响应为原ItineraryDetailVO，内部幂等键不投影。

输入恰为clientRequestId、title、destinationId、startDate、endDate、notes、days。clientRequestId为UUID，destinationId须为ACTIVE未删CITY。days项恰为travelDate、title、notes、items。items项恰为kind、destinationId、placeName、addressText、startTime、endTime、notes。未知字段拒绝，字段日期、文本和时间上限沿第20节。范围1–366天，最多366条不重复且范围内的日期，每天100项，整程500项。

kind只接受SCENIC_SPOT或FREE_TIME。景点须为根城市直属ACTIVE未删SCENIC_SPOT，名称与地址回查数据库覆盖客户端同名字段。FREE_TIME必须destinationId=null，名称由服务端固定为“自由活动”，地址null。导入景点全程唯一，时段不能重叠。原单条手动编辑入口保留重访能力。模型预览或用户修改均须经过导入重新校验。根、每日与项目在同一事务创建，任一步失败全部回滚。根标题160码点，notes2000码点，上限不截断。

## 25. M8 AI只读预览

全部 /ai/itineraries 入口要求ACTIVE登录。GET /capabilities返回enabled、available、reason、maxDays=14、provider、providerLabel。available表示服务端已具备调用配置。Base URL、Key与上游原始响应不投影。

POST /preview输入AiRequirements，字段为cityId、startDate、endDate、travelers、budgetAmount、budgetBasis、currency、interests、pace、transport、preferredIds、mustVisitIds、avoidIds。日期范围1–14天，人数1–20，budgetAmount为非负两位小数字符串且整数部分最多8位，budgetBasis为PER_PERSON/TOTAL，currency固定CNY。interests最多8项且不重复，枚举FOOD/NATURE/CULTURE/PHOTOGRAPHY/FAMILY/HIKING/CITY_WALK。pace为RELAXED/BALANCED/COMPACT，transport为PUBLIC/WALK/TAXI/MIXED。三组偏好各最多20个不重复真实同城可用景点ID，想去与必须去可交集，二者均与避免集合互斥。必须去数量超过天数乘8时，调用模型前返回400/40001。

预览恰为title、summary、city、startDate、endDate、travelers、budgetAmount、budgetBasis、currency、days、budgetEstimate、warnings、candidates、sourceGuides。city复用行程目的地引用。days项为dayNo、travelDate、title、items。items项为kind、destinationId、placeName、addressText、startTime、endTime、notes、estimatedCost。日期、名称、地址由服务端确定。每日至少1项、最多8项，全程最多60项，景点不重复，时间严格HH:mm且end>start、无重叠。必须去全部覆盖，避免集合不出现。FREE_TIME名称固定、ID和地址null。

budgetEstimate含currency、perPerson、total、notes，total由服务端按人数计算。所有费用是估算，单项为人均CNY，perPerson至少覆盖单项合计。超出用户预算时warnings明确提示。candidates含id、name、address、coverImageUrl、recommendedDurationMinutes、openingHours、ticketInfo、sourceUrl、verifiedAt。sourceGuides只有已发布资料的id/title，正文只进入受控模型上下文。

POST /day-preview输入恰为requirements、targetDate、usedDestinationIds。targetDate在原范围，其他天已用ID最多60个真实同城可用景点。响应仅day和warnings。服务端排除其他天已用景点，校验尚未覆盖的必须去。前端只合并目标日并验证需求/编辑版本，迟到响应失效。

候选最多40个，先包含想去/必须去，再补充其他可用候选。仅检索最多5篇公开攻略，每篇摘要最多1600字符，候选资料与摘要按序收缩，最终外呼JSON不超过24000字符。模型等待期间不持有数据库事务，返回前重新读取景点和父城状态。预览无业务写入。每用户滚动60秒最多2次、每用户1个并发、全局2个并发，最多记录10000个用户状态，过期空闲状态可回收。上游失败不会自动再发调用。

错误码使用503/50310配置不可用、422/42210资料不足、429/42910本地限流、429/42911上游限流、504/50410超时、502/50210上游失败、502/50211非法输出。API请求校验仍为400/40001。AI关闭时私人手动行程接口继续可用。


## M10：身份与 IP 属地兼容扩展

- PublicProfileVO 增加 `username:string|null`；正常作者为唯一用户名，已注销作者不捏造用户名。Web 两行显示昵称与灰色 `@username`；空昵称回退用户名。现有登录规则、用户唯一索引及权限保持。
- GuideSummaryVO、GuideDetailVO、CommentVO 新增 `ipRegion:string|null`。旧字段、分页结构、HTTP 状态码和状态机保留。后台 CommentVO 顶层保持原八字段；其公开作者投影同步扩展 username。
- 创建已发布攻略或首次 `/publish` 时服务端生成属地，与首次 `publishedAt` 同时冻结；草稿不采集。编辑、撤回再发布、管理治理均不改写，首次 NULL 也不会后补。
- 评论发表时服务端生成；历史数据保留 NULL，Web 显示“IP 属地：未知”。创建/更新请求夹带 `ipRegion`、原始 `ip` 等非白名单字段返回 HTTP 400 / 40001。
- 当前没有已授权 GeoIP 数据源：默认解析器返回 NULL，既不访问第三方，也不请求 GPS。仅预留审计过的离线 IpRegionResolver 接口；未来启用前需核验许可/版本/省级或国家级输出与运行边界。
- `server.forward-headers-strategy=none`。默认只取真实远端地址，忽略 XFF、X-Real-IP、Forwarded。只有 `IP_TRUSTED_PROXIES` 明确列出的直连代理才启用 XFF，从右向左剥离可信代理，首个不可信地址为客户端；不接受通配/CIDR/主机名。代理必须覆盖或追加实际来源，不能只透传任意客户端头。
- 重复 XFF 合并后最多 20 跳/2048 字符；非法字面值、zone、端口、超限均降级。IPv4-mapped IPv6 归一化；回环/私网/链路本地/常见保留地址不查属地。不进行 DNS 查询，不保存原始 IP、不在错误或诊断日志输出。
- 没有外部服务和运行时 IP 缓存：默认解析器无网络 I/O，查不到或运行异常即 NULL，基础业务仍可用。现有 uni-app 忽略这些兼容新增字段，页面布局不变。

## M12 公开社区读取（2026-11-11）

| Method | Path | 参数与响应 | 权限 |
| --- | --- | --- | --- |
| GET | `/users/by-username/{username}` | `id, username, nickname, avatarUrl, bio, publishedGuideCount` | 匿名可读；已删除/禁用/不存在用户404 |
| GET | `/users/by-username/{username}/guides` | `page` 默认1、`size` 默认10（1–50）；标准分页 `GuideSummary` | 仅未删除 PUBLISHED；按 published_at、id 倒序 |
| GET | `/guides/{id}/related` | `size` 默认4（1–12），`GuideSummary[]` | 当前文章须公开，否则404；排除当前、DRAFT/HIDDEN/删除 |

相关内容必须同主目的地或至少一个相同标签；同主目的地优先，其后按实际点赞数、发布时间、ID倒序。不足时返回较少条目或空数组，不填充虚构内容。所有筛选和数量限制在数据库执行，值使用参数化查询；不开放作者私人收藏或行程。

首页推荐与热门复用现有 `GET /guides?sort=popular`（点赞数、发布时间、ID倒序），最新使用 `latest`；界面不宣称个性化。搜索范围保持标题或摘要，`GuideSummary` 原字段不变。

IP 属地输出由 `IpRegionVisibility.ENABLED=false` 全局关闭：攻略/评论 `ipRegion` 字段仍存在，但公开/本人响应为 null；原数据库快照、首次发布写入和可信代理解析逻辑保留。只有来源能力单独验收后才能修改披露策略。本轮没有 DDL 或内容格式迁移。


## M12 管理员网页 AI 设置

`GET /api/v1/admin/ai-settings` 与 `PUT /api/v1/admin/ai-settings` 要求 ACTIVE 管理员。匿名返回 401/40101，普通账号返回 403/40301。成功均为 200 与统一响应包。

GET 的 data 包含 `version:number`、`enabled:boolean`、`provider:string`、`baseUrl:string`、`model:string`、`responseFormat:string`、`connectTimeoutSeconds:number`、`timeoutSeconds:number`、`maxOutputTokens:number`、`apiKeyConfigured:boolean`、`source:environment|saved`、`warning:string|null`、`presets:[]`。每个预设含 id、label、baseUrl、model、responseFormat。密钥状态通过 apiKeyConfigured 表达。普通用户使用的 capabilities 字段继续沿用第 25 节约定。

PUT 接收上述 version、enabled、provider、baseUrl、model、responseFormat 与三个数值选项，另可传 `apiKey:string`、`clearApiKey:boolean`。初始 version=0，保存后递增。旧版本返回 409/40902，保存失败返回 500/50001，生效配置保持上次成功保存的版本。

- provider 支持 openai、deepseek、qwen、custom，responseFormat 支持 auto、json_object、json_schema。预设默认值沿用既有 AiProperties，管理员可修改模型与兼容 API 地址。
- 地址接受带 host 的 HTTP/HTTPS 根地址，拒绝用户名密码、query 与 fragment，最多 2000 字符。服务端追加 `/chat/completions`。
- 连接超时 1–30 秒，请求超时 1–90 秒，输出上限 128–12000。模型名最多 120 字符，密钥最多 8192 字符，拒绝控制字符。无效输入返回 400/40001。
- apiKey 留空或省略时沿用当前密钥。clearApiKey=true 时需留空 apiKey，网页同时取消启用。API 地址改变后需重新提供密钥，启用前须具备完整可用配置。
- 成功响应采用 GET 的安全投影，输入密钥在页面内存中清空。配置通过服务账号私有文件原子保存。启动时读取持久化版本，文件损坏时关闭 AI 并向管理员显示恢复提示。
- 已保存配置优先于启动环境默认值，后续生成请求取得一份不可变快照。手动行程与业务表继续使用现有规则。
