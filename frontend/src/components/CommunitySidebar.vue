<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import SiteIcon from './SiteIcon.vue'
const route = useRoute()
const auth = useAuthStore()
const home = computed(() =>
  auth.user ? `/users/${encodeURIComponent(auth.user.username)}` : '/me'
)
const links = computed(() => [
  { to: '/', label: '发现', icon: 'explore' },
  { to: '/search', label: '全站搜索', icon: 'explore' },
  { to: '/guides?sort=latest', label: '最新手记', icon: 'book' },
  { to: '/guides?sort=popular', label: '热门手记', icon: 'heart' },
  { to: '/destinations', label: '城市与景点', icon: 'pin' },
  { to: '/itineraries/ai', label: 'AI 行程规划', icon: 'sparkles' },
  { to: '/me/favorites', label: '我的收藏', icon: 'star' },
  { to: home.value, label: '我的主页', icon: 'user' },
  { to: '/me/settings', label: '资料设置', icon: 'settings' }
])
function selected(to: string): boolean {
  return to === '/' ? route.path === '/' : route.fullPath === to
}
</script>
<template>
  <aside class="community-sidebar">
    <nav aria-label="社区导航">
      <RouterLink
        v-for="(link, index) in links"
        :key="link.to"
        :to="link.to"
        :class="{
          active: selected(link.to),
          'nav-divider': index === 4 || index === 7
        }"
        :aria-current="selected(link.to) ? 'page' : undefined"
        ><SiteIcon :name="link.icon" />{{ link.label }}</RouterLink
      >
    </nav>
  </aside>
  <nav class="community-mobile-nav" aria-label="手机快捷导航">
    <RouterLink to="/"><SiteIcon name="home" /><span>发现</span></RouterLink>
    <RouterLink to="/search"
      ><SiteIcon name="explore" /><span>搜索</span></RouterLink
    >
    <RouterLink to="/guides/new"
      ><SiteIcon name="pen" /><span>发布</span></RouterLink
    >
    <RouterLink to="/me/favorites"
      ><SiteIcon name="star" /><span>收藏</span></RouterLink
    >
    <RouterLink :to="home"
      ><SiteIcon name="user" /><span>我的</span></RouterLink
    >
  </nav>
</template>
<style scoped>
.community-sidebar {
  position: sticky;
  top: 88px;
  align-self: start;
  width: 192px;
  flex: none;
  padding: 4px 12px 20px 0;
}
.community-sidebar a {
  display: flex;
  align-items: center;
  gap: 14px;
  min-height: 48px;
  margin-bottom: 7px;
  padding: 10px 16px;
  border-radius: 14px;
  color: var(--color-ink-soft);
  font-size: 14px;
  transition:
    background-color var(--motion-fast),
    color var(--motion-fast);
}
.community-sidebar svg {
  width: 19px;
  height: 19px;
}
.community-sidebar a:hover,
.community-sidebar .active {
  background: var(--color-pine-soft);
  color: var(--color-pine-deep);
}
.community-sidebar .active {
  font-weight: 650;
}
.community-sidebar .nav-divider {
  margin-top: 28px;
}
.community-mobile-nav {
  display: none;
}
@media (max-width: 1199px) {
  .community-sidebar {
    display: none;
  }
}
@media (max-width: 767px) {
  .community-mobile-nav {
    position: fixed;
    z-index: 30;
    bottom: 0;
    left: 0;
    right: 0;
    display: flex;
    justify-content: space-around;
    gap: 8px;
    padding: 7px 8px max(7px, env(safe-area-inset-bottom));
    border-top: 1px solid color-mix(in srgb, var(--color-pine) 12%, transparent);
    background: var(--color-surface);
  }
  .community-mobile-nav a {
    display: flex;
    flex: 1;
    flex-direction: column;
    align-items: center;
    gap: 3px;
    min-height: 44px;
    font-size: 10px;
    color: var(--color-ink-soft);
  }
  .community-mobile-nav svg {
    width: 21px;
    height: 21px;
  }
  .community-mobile-nav .router-link-exact-active {
    color: var(--color-pine);
    font-weight: 600;
  }
}
</style>
