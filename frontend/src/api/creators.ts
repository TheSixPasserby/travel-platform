import { apiClient } from './client'
import type {
  CreatorDestination,
  CreatorDestinationQuery,
  CreatorProfile
} from '@/types/creator'
import type { GuideFeedPage, GuideSummary } from '@/types/guide'
import type { PageResult } from '@/types/api'
const path = (username: string) => `/users/by-username/${encodeURIComponent(username)}`
export function fetchCreator(username: string): Promise<CreatorProfile> {
  return apiClient.get<CreatorProfile, CreatorProfile>(path(username))
}
export function fetchCreatorGuides(username: string, params: { page: number; size: number }): Promise<PageResult<GuideSummary>> {
  return apiClient.get<PageResult<GuideSummary>, PageResult<GuideSummary>>(`${path(username)}/guides`, { params })
}
export function fetchCreatorFeed(username: string, params: { size?: number; cursor?: string } = {}, signal?: AbortSignal): Promise<GuideFeedPage> {
  return apiClient.get<GuideFeedPage, GuideFeedPage>(`${path(username)}/guides/feed`, { params, signal })
}
export function fetchCreatorDestinations(
  username: string,
  params: CreatorDestinationQuery,
  signal?: AbortSignal
): Promise<PageResult<CreatorDestination>> {
  return apiClient.get<PageResult<CreatorDestination>, PageResult<CreatorDestination>>(
    `${path(username)}/destinations`,
    { params, signal }
  )
}
