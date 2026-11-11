import { apiClient } from './client'
import type { AiSettings, AiSettingsInput } from '@/types/aiSettings'
export function fetchAiSettings(): Promise<AiSettings> {
  return apiClient.get<AiSettings, AiSettings>('/admin/ai-settings')
}
export function saveAiSettings(input: AiSettingsInput): Promise<AiSettings> {
  return apiClient.put<AiSettings, AiSettings>('/admin/ai-settings', input)
}
