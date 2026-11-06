import { apiClient } from './client'
import type {
  AiCapabilities,
  AiDayPreview,
  AiDayRequest,
  AiPreview,
  AiRequirements,
} from '@/types/ai'

export function fetchAiCapabilities(): Promise<AiCapabilities> {
  return apiClient.get<AiCapabilities, AiCapabilities>('/ai/itineraries/capabilities')
}
export function previewAiItinerary(body: AiRequirements): Promise<AiPreview> {
  return apiClient.post<AiPreview, AiPreview>('/ai/itineraries/preview', body, { timeout: 55000 })
}
export function previewAiDay(body: AiDayRequest): Promise<AiDayPreview> {
  return apiClient.post<AiDayPreview, AiDayPreview>('/ai/itineraries/day-preview', body, {
    timeout: 55000,
  })
}
