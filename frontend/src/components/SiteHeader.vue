<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ApiError } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import { useThemeStore, type ThemePreference } from '@/stores/theme'
import { useHeaderOverlay } from '@/composables/useHeaderOverlay'
import SiteIcon from './SiteIcon.vue'
import SiteBrand from './SiteBrand.vue'
import UserAvatar from './UserAvatar.vue'
import UserIdentity from './UserIdentity.vue'
const auth = useAuthStore(),
  theme = useThemeStore(),
  route = useRoute(),
  router = useRouter()
const { active, panel, toggle, close } = useHeaderOverlay()
function setPanel(element: unknown) {
  panel.value = element as HTMLElement | null
}
const loggingOut = ref(false),
  logoutError = ref('')
const discovery = [
  { to: '/', label: '发现', quick: '发现', icon: 'explore' },
  { to: '/guides', label: '攻略', quick: '攻略', icon: 'book' },
  { to: '/destinations', label: '目的地', quick: '目的地', icon: 'pin' },
  {
    to: '/itineraries/ai',
    label: 'AI 行程',
    quick: 'AI 行程',
    icon: 'sparkles'
  }
]
const personal = computed(() => [
  {
    to: auth.user ? '/users/' + encodeURIComponent(auth.user.username) : '/me',
    label: '我的主页',
    icon: 'user'
  },
  { to: '/me/guides', label: '我的攻略', icon: 'book' },
  { to: '/me/favorites', label: '我的收藏', icon: 'star' },
  { to: '/me/itineraries', label: '我的行程', icon: 'calendar' },
  { to: '/me/settings', label: '编辑资料', icon: 'settings' }
])
const searchKeyword = ref('')
function search(): void {
  void router.push({
    path: '/guides',
    query: { keyword: searchKeyword.value.trim() || undefined }
  })
}
const themes: Array<{ value: ThemePreference; label: string; icon: string }> = [
  { value: 'light', label: '浅色', icon: 'sun' },
  { value: 'dark', label: '深色', icon: 'moon' },
  { value: 'system', label: '跟随系统', icon: 'monitor' }
]
function selected(path: string) {
  return path === '/' ? route.path === '/' : route.path.startsWith(path)
}
watch(
  () => route.fullPath,
  () => close()
)
watch([() => auth.user?.id, () => auth.token], () => {
  close()
  logoutError.value = ''
})
async function handleLogout() {
  if (loggingOut.value) return
  const token = auth.token
  loggingOut.value = true
  logoutError.value = ''
  try {
    await auth.logout()
    if (auth.token && auth.token !== token) return
    close()
    await router.push('/')
  } catch (error) {
    if (auth.token === token)
      logoutError.value =
        error instanceof ApiError
          ? error.message
          : '退出登录没有完成，请稍后再试'
  } finally {
    loggingOut.value = false
  }
}
</script>
<template>
  <header class="site-header">
    <div class="header-row" data-test="header-row">
      <button
        type="button"
        class="icon-button"
        data-test="mobile-menu-toggle"
        data-overlay-trigger
        :aria-label="active === 'navigation' ? '关闭导航菜单' : '打开导航菜单'"
        :aria-expanded="active === 'navigation'"
        aria-controls="site-navigation"
        @click="toggle('navigation', $event)"
      >
        <SiteIcon name="menu" />
      </button>
      <RouterLink
        to="/"
        class="brand-link"
        aria-label="山水间 · 旅行手记，返回首页"
        ><SiteBrand
      /></RouterLink>
      <nav class="quick-nav" aria-label="快捷导航">
        <RouterLink
          v-for="item in discovery"
          :key="item.to"
          :to="item.to"
          class="quick-pill"
          :class="{ selected: selected(item.to) }"
          :aria-current="selected(item.to) ? 'page' : undefined"
          ><SiteIcon :name="item.icon" />{{ item.quick }}</RouterLink
        >
      </nav>
      <form class="header-search" role="search" @submit.prevent="search">
        <SiteIcon name="explore" /><input
          v-model="searchKeyword"
          aria-label="搜索旅行手记"
          placeholder="搜索旅行手记"
          type="search"
        />
      </form>
      <div class="header-actions">
        <RouterLink to="/guides/new" class="header-publish btn-primary"
          ><SiteIcon name="pen" /><span>发布笔记</span></RouterLink
        >
        <button
          type="button"
          class="icon-button"
          data-test="settings-toggle"
          data-overlay-trigger
          aria-label="显示设置"
          :aria-expanded="active === 'settings'"
          aria-controls="site-settings"
          @click="toggle('settings', $event)"
        >
          <SiteIcon name="settings" />
        </button>
        <button
          v-if="auth.isAuthenticated && auth.user"
          type="button"
          class="icon-button"
          data-test="account-toggle"
          data-overlay-trigger
          :aria-label="`${auth.user.nickname?.trim() || auth.user.username}的账号菜单`"
          :aria-expanded="active === 'account'"
          aria-controls="site-account"
          @click="toggle('account', $event)"
        >
          <UserAvatar
            :src="auth.user.avatarUrl"
            :name="auth.user.nickname?.trim() || auth.user.username"
            size="sm"
          />
        </button>
        <RouterLink v-else to="/login" class="login-button">登录</RouterLink>
      </div>
    </div>
  </header>
  <Teleport to="body"
    ><Transition name="header-overlay"
      ><div v-if="active" class="overlay-root">
        <div
          class="overlay-backdrop"
          data-test="overlay-backdrop"
          aria-hidden="true"
          @click="close()"
        />
        <section
          :id="`site-${active}`"
          :ref="setPanel"
          class="header-panel"
          :class="
            active === 'navigation' ? 'navigation-drawer' : `${active}-panel`
          "
          role="dialog"
          aria-modal="true"
          :aria-label="
            active === 'navigation'
              ? '主导航'
              : active === 'settings'
                ? '显示设置'
                : '账号菜单'
          "
          tabindex="-1"
        >
          <template v-if="active === 'navigation'">
            <div class="panel-heading">
              <SiteBrand /><button
                type="button"
                class="icon-button"
                aria-label="关闭导航菜单"
                @click="close()"
              >
                <SiteIcon name="close" />
              </button>
            </div>
            <div class="drawer-scroll">
              <nav aria-label="发现" class="drawer-group">
                <h2>发现</h2>
                <RouterLink
                  v-for="item in discovery"
                  :key="item.to"
                  :to="item.to"
                  class="panel-link"
                  :class="{ selected: selected(item.to) }"
                  :aria-current="selected(item.to) ? 'page' : undefined"
                  @click="close()"
                  ><SiteIcon :name="item.to === '/' ? 'home' : item.icon" />{{
                    item.label
                  }}</RouterLink
                >
              </nav>
              <nav aria-label="我的" class="drawer-group">
                <h2>我的</h2>
                <template v-if="auth.isAuthenticated"
                  ><RouterLink
                    v-for="item in personal.slice(1)"
                    :key="item.to"
                    :to="item.to"
                    class="panel-link"
                    :class="{ selected: selected(item.to) }"
                    @click="close()"
                    ><SiteIcon :name="item.icon" />{{ item.label }}</RouterLink
                  ><RouterLink
                    to="/guides/new"
                    class="panel-link"
                    @click="close()"
                    ><SiteIcon name="pen" />写攻略</RouterLink
                  ></template
                ><template v-else
                  ><p class="px-5 pb-3 text-sm text-ink-soft">
                    登录后收藏攻略，规划自己的旅程。
                  </p>
                  <div class="flex gap-3 px-5">
                    <RouterLink to="/login" class="btn-primary" @click="close()"
                      >登录</RouterLink
                    ><RouterLink
                      to="/register"
                      class="btn-ghost"
                      @click="close()"
                      >注册</RouterLink
                    >
                  </div></template
                >
              </nav>
              <nav
                v-if="auth.user?.role === 'ADMIN'"
                aria-label="管理"
                class="drawer-group"
              >
                <h2>管理</h2>
                <RouterLink
                  to="/admin/users"
                  class="panel-link"
                  @click="close()"
                  ><SiteIcon name="settings" />管理后台</RouterLink
                >
              </nav>
            </div>
            <details class="drawer-help">
              <summary><SiteIcon name="help" />使用帮助</summary>
              <p class="mt-3 text-sm text-ink-soft">
                浏览攻略和目的地；登录后可写攻略、收藏、评论并规划行程。个人资料可在账号菜单中修改。
              </p>
            </details>
          </template>
          <template v-else-if="active === 'settings'">
            <div class="panel-heading">
              <h2 class="flex items-center gap-3 text-xl font-semibold">
                <SiteIcon name="sun" class="h-6 w-6" />显示设置
              </h2>
              <button
                type="button"
                class="icon-button"
                aria-label="关闭显示设置"
                @click="close()"
              >
                <SiteIcon name="close" />
              </button>
            </div>
            <div class="p-5">
              <h3 class="mb-3 text-sm font-medium text-ink-soft">主题模式</h3>
              <div class="theme-options" role="group" aria-label="外观主题">
                <button
                  v-for="option in themes"
                  :key="option.value"
                  type="button"
                  :data-theme-option="option.value"
                  :aria-pressed="theme.preference === option.value"
                  :class="{ selected: theme.preference === option.value }"
                  @click="theme.setPreference(option.value)"
                >
                  <SiteIcon :name="option.icon" /><span>{{
                    option.label
                  }}</span>
                </button>
              </div>
              <div class="theme-preview" aria-hidden="true">
                <div />
                <div />
                <div />
              </div>
              <p class="mt-4 text-xs leading-relaxed text-ink-soft">
                主题选择会立即生效，并在下次打开时保留。
              </p>
            </div>
          </template>
          <template v-else-if="auth.user && auth.isAuthenticated">
            <div class="panel-heading gap-3">
              <UserIdentity
                class="min-w-0 flex-1"
                :user="auth.user"
                linked
              /><button
                type="button"
                class="icon-button shrink-0"
                aria-label="关闭账号菜单"
                @click="close()"
              >
                <SiteIcon name="close" />
              </button>
            </div>
            <nav aria-label="账号" class="p-3">
              <RouterLink
                v-for="item in personal"
                :key="item.to"
                :to="item.to"
                class="panel-link"
                @click="close()"
                ><SiteIcon :name="item.icon" />{{ item.label }}</RouterLink
              ><button
                type="button"
                data-test="logout"
                class="panel-link w-full"
                :disabled="loggingOut"
                @click="handleLogout"
              >
                <SiteIcon name="logout" />{{
                  loggingOut ? '正在退出…' : '退出登录'
                }}
              </button>
            </nav>
            <div
              v-if="logoutError"
              role="alert"
              class="mx-4 mb-3 rounded-xl bg-ember/10 p-3 text-sm text-ember"
            >
              {{ logoutError
              }}<button
                type="button"
                data-test="logout-retry"
                class="ml-2 min-h-11 underline"
                @click="handleLogout"
              >
                重试
              </button>
            </div>
            <p class="px-5 pb-5 text-xs text-ink-soft">
              用户名是唯一标识，也用于登录。
            </p>
          </template>
        </section>
      </div></Transition
    ></Teleport
  >
</template>
<style scoped>
.site-header {
  position: relative;
  z-index: 60;
  border-bottom: 1px solid
    color-mix(in srgb, var(--color-pine) 18%, transparent);
  background: var(--color-surface);
}
.header-row {
  height: 64px;
  display: flex;
  flex-wrap: nowrap;
  align-items: center;
  gap: 10px;
  padding-inline: 12px;
}
.brand-link {
  min-width: 0;
  min-height: 44px;
  display: inline-flex;
  align-items: center;
  flex-shrink: 0;
}
.icon-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  flex-shrink: 0;
  border-radius: 50%;
  color: var(--color-ink);
  cursor: pointer;
}
.icon-button:hover {
  background: var(--color-pine-soft);
}
.icon-button > svg {
  width: 23px;
  height: 23px;
}
.header-actions {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}
.login-button {
  min-height: 44px;
  min-width: 66px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid color-mix(in srgb, var(--color-pine) 25%, transparent);
  border-radius: 99px;
  font-weight: 600;
}
.quick-nav {
  display: none;
  align-items: center;
  gap: 8px;
  white-space: nowrap;
  margin-left: 14px;
}
.quick-pill {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 44px;
  padding: 8px 15px;
  border: 1px solid color-mix(in srgb, var(--color-pine) 24%, transparent);
  border-radius: 99px;
  font-size: 14px;
  font-weight: 600;
  color: var(--color-ink-soft);
}
.quick-pill svg,
.panel-link svg,
.drawer-help svg {
  width: 21px;
  height: 21px;
  flex-shrink: 0;
}
.selected {
  color: var(--color-pine-deep);
  background: var(--color-pine-soft);
  border-color: var(--color-pine);
}
.overlay-root {
  position: fixed;
  inset: 64px 0 0;
  z-index: 50;
}
.overlay-backdrop {
  position: absolute;
  inset: 0;
  background: color-mix(in srgb, var(--color-scrim) 58%, transparent);
}
.header-panel {
  position: absolute;
  top: 12px;
  right: 16px;
  width: min(360px, calc(100vw - 24px));
  max-height: calc(100dvh - 88px);
  overflow: auto;
  overscroll-behavior: contain;
  border: 1px solid color-mix(in srgb, var(--color-pine) 28%, transparent);
  border-radius: 24px;
  background: var(--color-surface);
  color: var(--color-ink);
  box-shadow: 0 16px 48px #0003;
}
.panel-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 18px 20px;
  border-bottom: 1px solid
    color-mix(in srgb, var(--color-pine) 20%, transparent);
}
.navigation-drawer {
  inset: 0 auto 0 0;
  width: min(320px, calc(100vw - 48px));
  max-height: none;
  height: 100%;
  border-radius: 0;
  display: flex;
  flex-direction: column;
  padding-bottom: env(safe-area-inset-bottom);
}
.drawer-scroll {
  overflow: auto;
  overscroll-behavior: contain;
  flex: 1;
  min-height: 0;
  padding-bottom: 16px;
}
.drawer-group {
  padding-block: 20px 8px;
}
.drawer-group + .drawer-group {
  border-top: 1px solid color-mix(in srgb, var(--color-pine) 15%, transparent);
  margin-top: 12px;
}
.drawer-group h2 {
  padding: 0 24px 12px;
  color: var(--color-ink-soft);
  font-size: 13px;
  font-weight: 600;
}
.panel-link {
  min-height: 48px;
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 12px 22px;
  border-radius: 12px;
  font-size: 15px;
  text-align: left;
}
.panel-link:hover {
  background: color-mix(in srgb, var(--color-pine-soft) 60%, transparent);
}
.navigation-drawer .panel-link {
  margin-right: 14px;
  border-radius: 0 99px 99px 0;
}
.drawer-help {
  padding: 18px 24px;
  border-top: 1px solid color-mix(in srgb, var(--color-pine) 20%, transparent);
}
.drawer-help summary {
  cursor: pointer;
  min-height: 44px;
  display: flex;
  align-items: center;
  gap: 18px;
}
.theme-options {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}
.theme-options button {
  min-height: 96px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  border: 1px solid color-mix(in srgb, var(--color-pine) 24%, transparent);
  border-radius: 18px;
  font-size: 13px;
  cursor: pointer;
}
.theme-options svg {
  width: 27px;
  height: 27px;
}
.theme-options .selected {
  border-color: var(--color-pine);
  box-shadow: inset 0 0 0 1px var(--color-pine);
}
.theme-preview {
  margin-top: 20px;
  border: 1px solid color-mix(in srgb, var(--color-pine) 22%, transparent);
  border-radius: 18px;
  padding: 16px;
  background: var(--color-cream);
}
.theme-preview div {
  height: 14px;
  border-radius: 8px;
  background: var(--color-pine-soft);
  margin-bottom: 12px;
}
.theme-preview div:first-child {
  height: 24px;
}
.theme-preview div:nth-child(2) {
  width: 80%;
}
.theme-preview div:last-child {
  width: 60%;
  margin-bottom: 0;
}
.header-overlay-enter-active,
.header-overlay-leave-active {
  transition: opacity 0.16s ease;
}
.header-overlay-enter-active .navigation-drawer,
.header-overlay-leave-active .navigation-drawer {
  transition: transform 0.2s ease;
}
.header-overlay-enter-from,
.header-overlay-leave-to {
  opacity: 0;
}
.header-overlay-enter-from .navigation-drawer,
.header-overlay-leave-to .navigation-drawer {
  transform: translateX(-100%);
}
@media (min-width: 768px) {
  .quick-nav {
    display: flex;
  }
  .header-row {
    padding-inline: 24px;
    gap: 14px;
  }
}
@media (min-width: 768px) and (max-width: 1023px) {
  .quick-pill:first-child {
    display: none;
  }
  .quick-nav {
    margin-left: 0;
    gap: 6px;
  }
  .quick-pill {
    padding-inline: 12px;
  }
}
@media (prefers-reduced-motion: reduce) {
  .header-overlay-enter-active,
  .header-overlay-leave-active,
  .header-overlay-enter-active .navigation-drawer,
  .header-overlay-leave-active .navigation-drawer {
    transition: none;
  }
}
.header-search {
  display: none;
  align-items: center;
  gap: 10px;
  width: 260px;
  margin-left: auto;
  padding: 8px 16px;
  border: 1px solid transparent;
  border-radius: 99px;
  background: var(--color-cream-deep);
  transition: border-color var(--motion-fast);
}
.header-search:focus-within {
  border-color: var(--color-pine);
}
.header-search svg {
  width: 18px;
  height: 18px;
  flex: none;
  color: var(--color-ink-soft);
}
.header-search input {
  min-width: 0;
  width: 100%;
  outline: 0;
  background: transparent;
  font-size: 13px;
}
.header-publish {
  gap: 7px;
  margin-right: 12px;
}
.header-publish svg {
  width: 18px;
  height: 18px;
}
@media (min-width: 1280px) {
  .header-search {
    display: flex;
  }
  .header-actions {
    margin-left: 12px;
  }
}
@media (max-width: 1023px) {
  .header-publish {
    display: none;
  }
}
</style>
