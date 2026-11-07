<script setup lang="ts">
import { useThemeStore, type ThemePreference } from '@/stores/theme'

const theme = useThemeStore()

function change(event: Event): void {
  theme.setPreference((event.target as HTMLSelectElement).value as ThemePreference)
}
</script>

<template>
  <label class="inline-flex shrink-0 items-center gap-1.5 text-xs text-ink-soft">
    <svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" aria-hidden="true">
      <circle cx="12" cy="12" r="8" />
      <path d="M12 4a8 8 0 0 1 0 16Z" fill="currentColor" stroke="none" />
    </svg>
    <select
      :value="theme.preference"
      aria-label="外观主题"
      class="theme-select rounded-full border border-pine/20 bg-surface px-2 py-2 text-xs text-ink"
      @change="change"
    >
      <option value="system">跟随系统</option>
      <option value="light">浅色</option>
      <option value="dark">深色</option>
    </select>
  </label>
</template>
