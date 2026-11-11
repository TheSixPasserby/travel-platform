import type { DestinationStatus } from './destination'

export type GuideStatus = 'DRAFT' | 'PUBLISHED' | 'HIDDEN'
export type GuideSort = 'latest' | 'popular'
export interface Tag { id: string; name: string }
export interface PublicProfile { id: string; nickname: string; username?: string | null; avatarUrl: string | null; bio: string | null }
export interface GuideDestination { id: string; name: string; status: DestinationStatus }
export interface GuideSummary {
  ipRegion?: string | null
  id: string
  title: string
  summary: string | null
  coverImageUrl: string | null
  author: PublicProfile
  destination: GuideDestination | null
  travelDays: number | null
  budgetPerPerson: string | null
  season: string | null
  tags: Tag[]
  status: GuideStatus
  publishedAt: string | null
  createdAt: string
  updatedAt: string
  likeCount: number
  favoriteCount: number
  commentCount: number
}
export interface GuideImageInput { url: string; altText: string | null }
export interface GuideImage extends GuideImageInput { sortOrder: number }
export interface GuideDetail extends GuideSummary { content: string; images: GuideImage[]; scenicSpots: GuideDestination[] }
export interface GuideInput {
  title: string
  content: string
  summary: string | null
  destinationId: string | null
  coverImageUrl: string | null
  travelDays: number | null
  budgetPerPerson: string | null
  season: string | null
  tagIds: string[]
  images: GuideImageInput[]
  scenicSpotIds?: string[]
}
export interface GuideCreate extends GuideInput { status?: 'DRAFT' | 'PUBLISHED' }
export interface GuideQuery { page?: number; size?: number; keyword?: string; destinationId?: string; tagId?: string; sort?: GuideSort }
export interface GuideFeedPage { records: GuideSummary[]; nextCursor: string | null; hasMore: boolean }
export interface MyGuideQuery { page?: number; size?: number; keyword?: string; status?: GuideStatus }
export const guideStatusLabels: Record<GuideStatus,string> = { DRAFT: '草稿', PUBLISHED: '已发布', HIDDEN: '已隐藏' }
