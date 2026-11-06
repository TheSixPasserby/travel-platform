import { apiClient } from './client'
import type { PageResult } from '@/types/api'
import type { AdminDestinationQuery, Destination, DestinationCreate, DestinationInput, DestinationQuery, DestinationStatus } from '@/types/destination'
import type { GuideQuery, GuideSummary } from '@/types/guide'

export function fetchDestinationGuides(id: string, params: Pick<GuideQuery, 'page'|'size'|'keyword'|'tagId'> = {}): Promise<PageResult<GuideSummary>> {
  return apiClient.get<PageResult<GuideSummary>, PageResult<GuideSummary>>(`/destinations/${encodeURIComponent(id)}/guides`, { params })
}

export function fetchDestinations(params: DestinationQuery = {}): Promise<PageResult<Destination>> {
  return apiClient.get<PageResult<Destination>, PageResult<Destination>>('/destinations', { params })
}
export function fetchDestination(id: string): Promise<Destination> {
  return apiClient.get<Destination, Destination>(`/destinations/${encodeURIComponent(id)}`)
}
export function fetchAdminDestinations(params: AdminDestinationQuery = {}): Promise<PageResult<Destination>> {
  return apiClient.get<PageResult<Destination>, PageResult<Destination>>('/admin/destinations', { params })
}
export function createDestination(payload: DestinationCreate): Promise<Destination> {
  return apiClient.post<Destination, Destination>('/admin/destinations', payload)
}
export function updateDestination(id: string, payload: DestinationInput): Promise<Destination> {
  return apiClient.put<Destination, Destination>(`/admin/destinations/${encodeURIComponent(id)}`, payload)
}
export function changeDestinationStatus(id: string, status: DestinationStatus): Promise<Destination> {
  return apiClient.patch<Destination, Destination>(`/admin/destinations/${encodeURIComponent(id)}/status`, { status })
}
