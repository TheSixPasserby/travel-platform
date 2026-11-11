import { apiClient } from './client'
import type {
  AiCapabilities,
  AiDayPreview,
  AiDayRequest,
  AiPreview,
  AiRequirements,
} from '@/types/ai'

// Covers the configurable 30-second connection and 90-second read timeouts.
const AI_PREVIEW_TIMEOUT_MS = 125000

export function fetchAiCapabilities(): Promise<AiCapabilities> {
  return apiClient.get<AiCapabilities, AiCapabilities>('/ai/itineraries/capabilities')
}
export function previewAiItinerary(body: AiRequirements): Promise<AiPreview> {
  return apiClient.post<AiPreview, AiPreview>('/ai/itineraries/preview', body, {
    timeout: AI_PREVIEW_TIMEOUT_MS,
  })
}
export function previewAiDay(body: AiDayRequest): Promise<AiDayPreview> {
  return apiClient.post<AiDayPreview, AiDayPreview>('/ai/itineraries/day-preview', body, {
    timeout: AI_PREVIEW_TIMEOUT_MS,
  })
}
