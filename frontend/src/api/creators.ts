import { apiClient } from './client'
import type { CreatorProfile } from '@/types/creator'
import type { GuideSummary } from '@/types/guide'
import type { PageResult } from '@/types/api'
const path = (username: string) => `/users/by-username/${encodeURIComponent(username)}`
export function fetchCreator(username: string): Promise<CreatorProfile> {
  return apiClient.get<CreatorProfile, CreatorProfile>(path(username))
}
export function fetchCreatorGuides(username: string, params: { page: number; size: number }): Promise<PageResult<GuideSummary>> {
  return apiClient.get<PageResult<GuideSummary>, PageResult<GuideSummary>>(`${path(username)}/guides`, { params })
}
