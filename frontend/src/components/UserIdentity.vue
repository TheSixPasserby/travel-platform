<script setup lang="ts">
import { computed } from 'vue'
import UserAvatar from './UserAvatar.vue'

const props = withDefaults(defineProps<{
  user: { nickname?: string | null; username?: string | null; avatarUrl?: string | null }
  size?: 'sm' | 'md' | 'lg'
  linked?: boolean
  showAvatar?: boolean
}>(), { size: 'md', showAvatar: true })
const profilePath = computed(() => props.linked && props.user.username ? `/users/${encodeURIComponent(props.user.username)}` : null)
const nickname = computed(() => props.user.nickname?.trim() || props.user.username?.trim() || '旅行者')
</script>
<template>
  <component :is="profilePath ? 'RouterLink' : 'div'" :to="profilePath || undefined" class="flex min-w-0 items-center gap-3" :class="{ 'identity-link': profilePath }">
    <UserAvatar v-if="showAvatar" :src="user.avatarUrl" :name="nickname" :size="size" />
    <div class="min-w-0">
      <p data-test="identity-nickname" :title="nickname" class="truncate font-medium text-pine-deep" :class="size === 'sm' ? 'text-sm' : 'text-base'">{{ nickname }}</p>
      <p v-if="user.username" data-test="identity-username" :title="`@${user.username}`" class="truncate text-xs text-ink-soft">@{{ user.username }}</p>
      <slot />
    </div>
  </component>
</template>

<style scoped>
.identity-link:focus-visible{outline:2px solid var(--color-pine);outline-offset:4px;border-radius:.5rem}.identity-link:hover [data-test="identity-nickname"]{text-decoration:underline;text-underline-offset:3px}
</style>
