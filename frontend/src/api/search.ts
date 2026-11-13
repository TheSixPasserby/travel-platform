import { apiClient } from './client'
import type { SearchPage, SearchQuery } from '@/types/search'

export function searchPublicContent(params: SearchQuery, signal?: AbortSignal): Promise<SearchPage> {
  return apiClient.get<SearchPage, SearchPage>('/search', { params, signal })
}
