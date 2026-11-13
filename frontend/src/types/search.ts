import type { FeedPage } from '@/composables/useInfiniteFeed'

export type SearchKind = 'GUIDE' | 'CITY' | 'SCENIC_SPOT' | 'CREATOR' | 'TAG'
export type SearchSort = 'relevance' | 'latest'
export interface SearchHit {
  kind: SearchKind
  id: string
  title: string
  subtitle: string | null
  coverImageUrl: string | null
  username: string | null
  href: string
}
export interface SearchQuery {
  keyword?: string
  kind?: SearchKind
  sort?: SearchSort
  size?: number
  cursor?: string
}
export type SearchPage = FeedPage<SearchHit>
