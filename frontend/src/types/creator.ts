import type { PublicProfile } from './guide'
export interface CreatorProfile extends PublicProfile {
  username: string
  publishedGuideCount: number
  likeCount: number
  favoriteCount: number
}

/** 公开手记中标记过的目的地足迹；仅统计公开攻略关联的有效目的地，不代表真实到访。 */
export interface CreatorDestination {
  id: string
  name: string
  /** 后端返回原始类型值，前端映射为中文，未知类型原样展示。 */
  destinationType: string
  coverImageUrl: string | null
  /** 关联该目的地的公开手记数量。 */
  guideCount: number
}

/** 创作者目的地足迹分页查询（服务端分页）。 */
export interface CreatorDestinationQuery {
  page: number
  size: number
}
