<script setup lang="ts">
import { onMounted } from 'vue'

import SiteFooter from '@/components/SiteFooter.vue'
import SiteHeader from '@/components/SiteHeader.vue'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()

// 刷新页面后用会话令牌恢复登录态；网络失败时保留令牌，页面内提供重试。
onMounted(() => {
  void auth.restoreSession().catch(() => undefined)
})
</script>

<template>
  <a
    href="#main"
    class="sr-only focus:not-sr-only focus:fixed focus:top-3 focus:left-3 focus:z-50 focus:rounded-full focus:bg-pine focus:px-4 focus:py-2 focus:text-sm focus:text-white"
  >
    跳到主要内容
  </a>

  <div class="flex min-h-screen flex-col bg-cream">
    <SiteHeader />

    <main id="main" class="flex-1">
      <RouterView v-slot="{ Component }">
        <component :is="Component" />
      </RouterView>
    </main>

    <SiteFooter />
  </div>
</template>
