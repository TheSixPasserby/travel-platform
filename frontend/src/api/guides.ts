import { apiClient } from './client'
import type { PageResult } from '@/types/api'
import type { GuideCreate, GuideDetail, GuideInput, GuideQuery, GuideSummary, MyGuideQuery, Tag } from '@/types/guide'

const path = (id: string) => `/guides/${encodeURIComponent(id)}`
export function fetchGuides(params: GuideQuery = {}): Promise<PageResult<GuideSummary>> {
  return apiClient.get<PageResult<GuideSummary>,PageResult<GuideSummary>>('/guides',{ params })
}
export function fetchGuide(id: string): Promise<GuideDetail> { return apiClient.get<GuideDetail,GuideDetail>(path(id)) }
export function fetchMyGuides(params: MyGuideQuery = {}): Promise<PageResult<GuideSummary>> {
  return apiClient.get<PageResult<GuideSummary>,PageResult<GuideSummary>>('/users/me/guides',{ params })
}
export function fetchOwnGuide(id: string): Promise<GuideDetail> { return apiClient.get<GuideDetail,GuideDetail>(`/users/me${path(id)}`) }
export function createGuide(payload: GuideCreate): Promise<GuideDetail> { return apiClient.post<GuideDetail,GuideDetail>('/guides',payload) }
export function updateGuide(id: string,payload: GuideInput): Promise<GuideDetail> { return apiClient.put<GuideDetail,GuideDetail>(path(id),payload) }
export function deleteGuide(id: string): Promise<null> { return apiClient.delete<null,null>(path(id)) }
export function publishGuide(id: string): Promise<GuideDetail> { return apiClient.post<GuideDetail,GuideDetail>(`${path(id)}/publish`) }
export function unpublishGuide(id: string): Promise<GuideDetail> { return apiClient.post<GuideDetail,GuideDetail>(`${path(id)}/unpublish`) }
export function fetchTags(): Promise<Tag[]> { return apiClient.get<Tag[],Tag[]>('/tags') }
export function fetchRelatedGuides(id: string): Promise<GuideSummary[]> {
  return apiClient.get<GuideSummary[], GuideSummary[]>(`${path(id)}/related`, { params: { size: 4 } })
}
