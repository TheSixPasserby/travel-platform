<script setup lang="ts">
import { computed } from 'vue'
import UserAvatar from './UserAvatar.vue'

const props = withDefaults(defineProps<{
  user: { nickname?: string | null; username?: string | null; avatarUrl?: string | null }
  size?: 'sm' | 'md' | 'lg'
  showAvatar?: boolean
}>(), { size: 'md', showAvatar: true })
const nickname = computed(() => props.user.nickname?.trim() || props.user.username?.trim() || '旅行者')
</script>
<template>
  <div class="flex min-w-0 items-center gap-3">
    <UserAvatar v-if="showAvatar" :src="user.avatarUrl" :name="nickname" :size="size" />
    <div class="min-w-0">
      <p data-test="identity-nickname" :title="nickname" class="truncate font-medium text-pine-deep" :class="size === 'sm' ? 'text-sm' : 'text-base'">{{ nickname }}</p>
      <p v-if="user.username" data-test="identity-username" :title="`@${user.username}`" class="truncate text-xs text-ink-soft">@{{ user.username }}</p>
      <slot />
    </div>
  </div>
</template>
