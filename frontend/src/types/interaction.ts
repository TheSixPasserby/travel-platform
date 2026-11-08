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
}
export interface CommentQuery { page?:number;size?:number }
export interface FavoriteQuery { page?:number;size?:number;keyword?:string }
