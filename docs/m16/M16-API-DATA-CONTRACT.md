# M16 / API、数据与安全契约（供 Codex 主代理落实）

> 以下“已存在”项来自 `main@9a6261c` 源码审查；新增接口为**建议设计，并非已实现**，必须先由实际代码决定最终 URL 与 VO，随后同步 API 文档、前端类型和测试。

## 已存在可复用内容

| 当前文件/路径 | 已有能力 |
|---|---|
| `frontend/src/api/guides.ts` | `/guides/feed` 游标信息流、`/guides/:id` 详情、`/guides/:id/related` |
| `frontend/src/api/interactions.ts` | 赞/收藏、`/users/me/favorites`、一级评论 CRUD |
| `frontend/src/api/itineraries.ts` | `/itineraries` 私人行程，天/项目写入，`/itineraries/import` 原子导入 |
| `frontend/src/types/ai.ts` | `AiRequirements` 的 `preferredIds`、`mustVisitIds`、`avoidIds`，预算、天数、交通、节奏 |
| `frontend/src/api/ai.ts` | `/ai/itineraries/capabilities`、`/preview`、`/day-preview` |
| `frontend/src/router/index.ts` | `/users/:username`、`/guides/:id`、`/destinations/:id` 等公开入口 |
| `backend/.../comment/controller/CommentController.java` | 当前仅一级评论创建/分页/本人删除；回复/通知未确认存在 |

**所有 API 实际前缀为 `/api/v1`**，上表前端路径以 `apiClient` 的相对路径表示。

## A. 建议的统一搜索响应

```ts
type SearchKind = 'GUIDE' | 'CITY' | 'SCENIC_SPOT' | 'CREATOR' | 'TAG'
interface SearchHit {
  kind: SearchKind;
  id: string;
  title: string;
  subtitle?: string | null;
  coverImageUrl?: string | null;
  username?: string | null; // 仅 CREATOR 可出现
  href: string; // 也可由前端基于 kind/id 映射，须限制内部路由
}
interface SearchResponse {
  keyword: string;
  groups: Array<{ kind: SearchKind; records: SearchHit[]; nextCursor?: string | null }>;
}
```

候选接口：`GET /api/v1/search?keyword=...&kind=...&size=...&cursor=...`；可分组获取，避免一个巨大 `all` 请求影响性能。合理限制词长/页长、通配符转义、预编译查询、稳定排序与上限。公开搜索必须过滤草稿 `DRAFT`、隐藏 `HIDDEN`、逻辑删除、被禁用资源及不可见作者资料；不得连带公开用户的私人行程和个人收藏。对作者、标签搜索，避免精确手机号/邮箱等未公开字段的枚举。

## B. 攻略地点批量加入行程（优选服务端原子动作）

候选：`POST /api/v1/itineraries/{itineraryId}/days/{dayId}/places:import-from-guide` 或符合项目既有风格的 REST 路径。建议输入示例：

```json
{
  "guideId":"4",
  "destinationIds":["12","16"],
  "clientRequestId":"客户端随机幂等标识",
  "skipExisting":true
}
```

服务端由登录态判定 `userId`；**不能让客户端传 authorId / ownerId**。所有 `destinationIds` 必须确实属于可访问的攻略关联景点，且为有效资源；`dayId` 必须属于当前用户的 `itineraryId`。优先在一个事务中做已存在检测/插入/响应（可跳过重复并列出），选择稳定排序，条数/并发限制明确。幂等请求必须在服务端可靠约束（例如专用唯一记录或已有可复用的幂等机制），不能只靠禁用按钮。当前系统的创建项目接口是单项写入，若本轮先复用它做 MVP，必须承认**不是原子批量操作**，并设计部分失败后的去重补齐。

建议响应：`{itineraryId,dayId,added:[...],alreadyPresent:[...],skippedInvalid:[...]}`；错误使用正确 HTTP 状态和稳定码。用户取消无任何持久写入，重复提交不产生重复项目，目标行程被删除/归档时需按现有业务规则阻断。

## C. 旅行灵感传入 AI

建议通过登录身份作用域中的临时 intent store + 确认表单预填，将选中的合法目的地 ID 映射到 `mustVisitIds`/`preferredIds`，并**保留**用户手动调整 `AiRequirements`。不把全文、私人行程或 API 密钥放入 URL、localStorage、浏览器日志或 analytics。复用 `previewAiItinerary` 得到预览、可编辑以后使用现有 `importItinerary`。用户关闭 AI 时手动规划可用。

## D. 回复与通知（P1，确实需要持久化设计）

注：当前只有一级评论 API；不能直接在 UI 显示“56 条回复”等假数字。

可选择新增 `parent_id/root_id` 受控层级并保留旧评论 ID，或设计单独 reply 表；**不得修改旧记录所有权/内容/评论计数语义**。通知建议至少包含 `id, recipient_user_id, actor_user_id, event_type, subject_type, subject_id, created_at, read_at` 及防重复来源键。只给事件接收者返回，删除目标提供安全空态，已读和未读由服务端持久化。系统通知不是自由消息，禁止允许任意客户端伪造发通知 API。

需要功能：单评论回复、@用户名候选（仅公开可见资料）、通知列表/未读、标记单个/全部已读、跳转评论锚点、本人删除/管理员治理、反垃圾频控。给出清晰索引、事务、匿名 401、非本人 403 和隐私测试。

## E. 图片性能（先基线后优化）

卡片始终请求合适尺寸，详情请求中等尺寸，灯箱请求原图。若引入缩略图派生：严格匹配 `/uploads/` 受管路径，不接受任意外链/绝对磁盘路径；固定可选档位、限制像素/内存/生成时间；保留原始文件和许可，不覆写旧 URL、不给用户上传目录执行权限；返回安全 Content-Type 和缓存头。

## F. DB 与项目稳定性

任何结构变更必须用**版本化增量迁移**、单独测试数据库、前后指纹/备份；不可对已有业务库重跑 `database/schema.sql` 或随意执行 TRUNCATE/DROP。A/B 用户跨账号行程访问、评论回复、通知和关注都应测试 401/403/404 的完整边界。必须保持已有点赞收藏唯一约束、IP 属地禁显策略，以及 AI 网页私有密钥不返回前端。
