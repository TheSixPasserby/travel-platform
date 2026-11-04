<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'

import { ApiError } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import UserAvatar from './UserAvatar.vue'

const auth = useAuthStore()
const router = useRouter()

const loggingOut = ref(false)
const logoutError = ref('')

async function handleLogout(): Promise<void> {
  if (loggingOut.value) {
    return
  }
  loggingOut.value = true
  logoutError.value = ''
  try {
    await auth.logout()
    await router.push('/')
  } catch (error) {
    // 网络失败时保留登录态，明确告知可以重试，不谎报已退出。
    logoutError.value =
      error instanceof ApiError ? error.message : '退出登录没有完成，请稍后再试'
  } finally {
    loggingOut.value = false
  }
}
</script>

<template>
  <header class="border-b border-pine/10 bg-cream/90 backdrop-blur-sm">
    <div class="app-container flex flex-wrap items-center gap-4 py-4">
      <RouterLink
        to="/"
        class="group flex items-center gap-2.5 font-serif text-xl tracking-wide text-pine-deep"
        aria-label="山水间 · 旅行手记，返回首页"
      >
        <svg
          viewBox="0 0 40 40"
          aria-hidden="true"
          class="h-9 w-9 transition-transform duration-300 group-hover:-translate-y-0.5"
        >
          <circle cx="20" cy="20" r="19" fill="#DDE6D6" />
          <circle cx="27" cy="13" r="4.2" fill="#C87545" />
          <path d="M6 27.5 L14.5 17 L21 24 L26.5 18.5 L34 27.5 Z" fill="#145C45" />
          <path d="M6 30.5 H34" stroke="#0D3F2F" stroke-width="1.6" stroke-linecap="round" />
        </svg>
        <span>山水间</span>
      </RouterLink>

      <span class="hidden text-xs tracking-[0.3em] text-ink-soft sm:inline">旅行手记</span>

      <nav class="ml-auto flex w-full flex-wrap items-center gap-2 sm:w-auto sm:gap-3" aria-label="主导航">
        <RouterLink to="/guides" class="btn-ghost">攻略</RouterLink>
        <RouterLink to="/destinations" class="btn-ghost">目的地</RouterLink>
        <RouterLink v-if="auth.user?.role === 'ADMIN'" to="/admin/users" class="btn-ghost">管理后台</RouterLink>
        <template v-if="auth.isAuthenticated && auth.user">
          <RouterLink
            to="/me"
            class="flex items-center gap-2 rounded-full py-1 pr-3 pl-1 transition-colors hover:bg-pine-soft/50"
          >
            <UserAvatar :src="auth.user.avatarUrl" :name="auth.user.nickname" size="sm" />
            <span class="max-w-28 truncate text-sm font-medium text-ink">
              {{ auth.user.nickname }}
            </span>
          </RouterLink>
          <button
            type="button"
            data-test="logout"
            class="btn-ghost"
            :disabled="loggingOut"
            @click="handleLogout"
          >
            {{ loggingOut ? '正在退出…' : '退出登录' }}
          </button>
        </template>
        <template v-else>
          <RouterLink to="/login" class="btn-ghost">登录</RouterLink>
          <RouterLink
            to="/register"
            class="rounded-full bg-pine px-4 py-2 text-sm font-medium text-[#FDFBF6] transition-colors hover:bg-pine-deep"
          >
            注册
          </RouterLink>
        </template>
      </nav>
    </div>

    <div v-if="logoutError" class="app-container pb-3">
      <div
        role="alert"
        aria-live="assertive"
        class="flex flex-wrap items-center gap-x-3 gap-y-2 rounded-xl border border-ember/30 bg-ember/10 px-3 py-2 text-sm text-ink"
      >
        <span>{{ logoutError }}</span>
        <button
          type="button"
          data-test="logout-retry"
          class="font-medium text-pine underline underline-offset-2"
          @click="handleLogout"
        >
          重试
        </button>
      </div>
    </div>
  </header>
</template>
