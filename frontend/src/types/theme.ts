export type ThemePreference = 'system' | 'light' | 'dark'
export type AccentSelection = { preset: string; custom?: never } | { custom: string; preset?: never }
export interface AccentPreset { id: string; name: string; hex: string }
export interface ThemeRuntime {
  accentKey: string
  presets: AccentPreset[]
  normalizeAccent(value: unknown): AccentSelection | null
  readAccent(value: string | null): AccentSelection
  readPreference(value: string | null): ThemePreference
  apply(preference: ThemePreference, accent: AccentSelection, systemDark: boolean): void
}
declare global {
  interface Window { shanshuiTheme: ThemeRuntime }
}
