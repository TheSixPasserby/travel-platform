import { onScopeDispose, ref } from 'vue'
import { defineStore } from 'pinia'
import type { AccentSelection, ThemePreference } from '@/types/theme'
export type { ThemePreference } from '@/types/theme'

const STORAGE_KEY = 'travel-notes.theme'

export const useThemeStore = defineStore('theme', () => {
  const runtime = window.shanshuiTheme
  let storage: Storage | null = null
  let saved: ThemePreference = 'system'
  let savedAccent: AccentSelection = { preset: 'pine' }
  try {
    storage = window.localStorage
    saved = runtime.readPreference(storage.getItem(STORAGE_KEY))
    savedAccent = runtime.readAccent(storage.getItem(runtime.accentKey))
  } catch {
    // Theme selection remains usable without persistent storage.
  }
  const preference = ref<ThemePreference>(saved)
  const accent = ref<AccentSelection>(savedAccent)
  const presets = runtime.presets
  const system = window.matchMedia?.('(prefers-color-scheme: dark)')

  function apply(): void {
    runtime.apply(preference.value, accent.value, system?.matches ?? false)
  }

  function persist(key: string, value: string): void {
    try { storage?.setItem(key, value) }
    catch { /* Keep applying in memory when persistence is disabled. */ }
  }

  function setPreference(next: ThemePreference): void {
    preference.value = runtime.readPreference(next)
    persist(STORAGE_KEY, preference.value)
    apply()
  }

  function setAccent(next: unknown): boolean {
    const valid = runtime.normalizeAccent(next)
    if (!valid) return false
    accent.value = valid
    persist(runtime.accentKey, JSON.stringify(valid))
    apply()
    return true
  }

  function resetAccent(): void {
    setAccent({ preset: 'pine' })
  }

  function syncStorage(event: StorageEvent): void {
    if (event.key !== STORAGE_KEY && event.key !== runtime.accentKey && event.key !== null) return
    if (event.storageArea && event.storageArea !== storage) return
    if (event.key === STORAGE_KEY || event.key === null) preference.value = runtime.readPreference(event.newValue)
    if (event.key === runtime.accentKey || event.key === null) accent.value = runtime.readAccent(event.newValue)
    apply()
  }

  apply()
  system?.addEventListener('change', apply)
  window.addEventListener('storage', syncStorage)
  onScopeDispose(() => {
    system?.removeEventListener('change', apply)
    window.removeEventListener('storage', syncStorage)
  })

  return { preference, accent, presets, setPreference, setAccent, resetAccent }
})
