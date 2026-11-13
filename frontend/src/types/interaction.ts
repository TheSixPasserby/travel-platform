import type { PublicProfile } from './guide'
export interface GuideInteraction {
  guideId:string
  liked:boolean
  favorited:boolean
  likeCount:number
  favoriteCount:number
  commentCount:number
}
export interface GuideComment {
  ipRegion?:string|null
  id:string
  guideId:string
  author:PublicProfile
  content:string
  createdAt:string
  updatedAt:string
  /** M17 新增：所属根评论；历史根评论为 null。旧夹具不带该字段，故保持可选。 */
  rootId?:string|null
  /** M17 新增：回复的直接目标评论；根评论为 null。 */
  replyToId?:string|null
  /** M17 新增：被回复用户的公开资料；根评论或目标失效时为 null。 */
  replyToAuthor?:PublicProfile|null
  /** M17 新增：公开回复数量，仅统计可见回复。 */
  replyCount?:number
  /** M17 新增：本条评论显式提及的用户。 */
  mentions?:PublicProfile[]
}
export interface CommentQuery { page?:number;size?:number }
export interface FavoriteQuery { page?:number;size?:number;keyword?:string }

/** 发表评论/回复的请求体：未回复、未提及时只发 content，兼容旧契约与既有断言。 */
export interface CommentCreatePayload {
  content:string
  replyToId?:string|null
  mentionUserIds?:string[]
}

/** 单条评论在分页中的位置；服务端固定每页 10 条。 */
export interface CommentLocation {
  guideId:string
  rootId:string
  rootPage:number
  replyPage:number
}

/** 站内提及候选人查询参数。 */
export interface MentionQuery { keyword?:string }
