<script setup lang="ts">
const links = [
  { name: 'admin-home', label: '后台概览' },
  { name: 'admin-users', label: '用户管理' },
  { name: 'admin-guides', label: '攻略治理' },
  { name: 'admin-comments', label: '评论治理' },
  { name: 'admin-destinations', label: '目的地管理' },
  { name: 'admin-ai-settings', label: 'AI服务设置' }
]
</script>
<template>
  <nav aria-label="后台导航" class="admin-navigation">
    <RouterLink
      v-for="link in links"
      :key="link.name"
      :to="{ name: link.name }"
      class="admin-navigation-link"
      exact-active-class="is-current"
    >{{ link.label }}</RouterLink>
  </nav>
</template>
<style scoped>
.admin-navigation {
  display: flex;
  flex-wrap: wrap;
  gap: var(--ui-space-2);
  margin-bottom: var(--ui-space-7);
  padding-bottom: var(--ui-space-5);
  border-bottom: 1px solid var(--ui-border-subtle);
}
.admin-navigation-link {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 44px;
  padding: .5rem 1rem;
  border: 1px solid var(--ui-border-subtle);
  border-radius: var(--ui-radius-md);
  font-size: .875rem;
  line-height: 1.4;
  color: var(--ui-text-secondary);
  background: var(--ui-bg-surface);
  text-decoration: none;
  transition:
    color var(--ui-motion-fast) var(--ui-motion-ease),
    background-color var(--ui-motion-fast) var(--ui-motion-ease),
    border-color var(--ui-motion-fast) var(--ui-motion-ease);
}
.admin-navigation-link:hover,
.admin-navigation-link.is-current {
  background: var(--ui-accent-soft);
  color: var(--ui-accent);
  border-color: var(--ui-accent);
}
.admin-navigation-link:focus-visible {
  outline: 2px solid var(--ui-focus-ring);
  outline-offset: 2px;
}
@media (prefers-reduced-motion: reduce) {
  .admin-navigation-link { transition: none; }
}
</style>
