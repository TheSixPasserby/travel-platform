import { onScopeDispose, ref } from 'vue'
import { defineStore } from 'pinia'

export type ThemePreference = 'system' | 'light' | 'dark'
const STORAGE_KEY = 'travel-notes.theme'

function parsePreference(value: string | null): ThemePreference {
  return value === 'light' || value === 'dark' ? value : 'system'
}

export const useThemeStore = defineStore('theme', () => {
  let saved: ThemePreference = 'system'
  try {
    saved = parsePreference(localStorage.getItem(STORAGE_KEY))
  } catch {
    // Theme selection remains usable without persistent storage.
  }
  const preference = ref<ThemePreference>(saved)
  const system = window.matchMedia?.('(prefers-color-scheme: dark)')

  function apply(): void {
    const theme = preference.value === 'system'
      ? (system?.matches ? 'dark' : 'light')
      : preference.value
    document.documentElement.dataset.theme = theme
    document.documentElement.style.colorScheme = theme
    document.querySelector('meta[name="theme-color"]')?.setAttribute('content', theme === 'dark' ? '#141e1a' : '#f9f7f2')
  }

  function setPreference(next: ThemePreference): void {
    preference.value = next
    try {
      localStorage.setItem(STORAGE_KEY, next)
    } catch {
      // Apply in memory even if persistence is disabled.
    }
    apply()
  }

  function syncStorage(event: StorageEvent): void {
    if (event.key !== STORAGE_KEY && event.key !== null) return
    preference.value = parsePreference(event.newValue)
    apply()
  }

  apply()
  system?.addEventListener('change', apply)
  window.addEventListener('storage', syncStorage)
  onScopeDispose(() => {
    system?.removeEventListener('change', apply)
    window.removeEventListener('storage', syncStorage)
  })

  return { preference, setPreference }
})
