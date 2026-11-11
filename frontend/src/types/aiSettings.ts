export interface AiProviderPreset {
  id: string
  label: string
  baseUrl: string
  model: string
  responseFormat: string
}
export interface AiSettings {
  version: number
  enabled: boolean
  provider: string
  baseUrl: string
  model: string
  responseFormat: string
  connectTimeoutSeconds: number
  timeoutSeconds: number
  maxOutputTokens: number
  apiKeyConfigured: boolean
  source: 'environment' | 'saved'
  warning: string | null
  presets: AiProviderPreset[]
}
export interface AiSettingsInput {
  version: number
  enabled: boolean
  provider: string
  baseUrl: string
  model: string
  responseFormat: string
  connectTimeoutSeconds: number
  timeoutSeconds: number
  maxOutputTokens: number
  apiKey?: string
  clearApiKey: boolean
}
