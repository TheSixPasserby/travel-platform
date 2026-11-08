<script setup lang="ts">
defineProps<{ name: string }>()
const paths: Record<string, string> = {
  menu: 'M4 6h16M4 12h16M4 18h16', close: 'm6 6 12 12M6 18 18 6',
  home: 'm3 10 9-7 9 7v11h-7v-7h-4v7H3Z',
  explore: 'm16 16 5 5M18 10a8 8 0 1 1-16 0 8 8 0 0 1 16 0Z',
  book: 'M12 5v16M3 4c4-1 6 0 9 1 3-1 5-2 9-1v15c-4-1-6 0-9 1-3-1-5-2-9-1Z',
  pin: 'M20 10c0 6-8 12-8 12S4 16 4 10a8 8 0 1 1 16 0ZM15 10a3 3 0 1 1-6 0 3 3 0 0 1 6 0Z',
  sparkles: 'm10 2 2 7 7 2-7 2-2 7-2-7-7-2 7-2ZM19 16v6M16 19h6',
  sun: 'M12 2v2M12 20v2M2 12h2M20 12h2M5 5l1.5 1.5M17.5 17.5 19 19M5 19l1.5-1.5M17.5 6.5 19 5M17 12a5 5 0 1 1-10 0 5 5 0 0 1 10 0Z',
  moon: 'M20 15a9 9 0 0 1-11-11 9 9 0 1 0 11 11Z', monitor: 'M3 4h18v13H3ZM12 17v5M8 22h8',
  settings: 'm10 2-1 3-3 1-3 4 2 2-1 3 3 3 3-1 2 2 4-2v-3l3-2-1-4-3-1-1-3ZM15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0Z',
  star: 'm12 2 3 7 8 1-6 5 2 8-7-4-7 4 2-8-6-5 8-1Z', calendar: 'M4 5h16v16H4ZM8 2v6M16 2v6M4 11h16',
  pen: 'm15 3 6 6-12 12H3v-6ZM12 6l6 6', user: 'M16 6a4 4 0 1 1-8 0 4 4 0 0 1 8 0ZM4 22v-3a8 8 0 0 1 16 0v3',
  logout: 'M9 3H3v18h6M8 12h14m-5-5 5 5-5 5', help: 'M9 9a3 3 0 1 1 4 3v3M12 18h.01M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0Z'
}
</script>
<template><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path :d="paths[name] || paths.home" /></svg></template>
