<script setup lang="ts">
const links=[{name:'admin-home',label:'后台概览'},{name:'admin-users',label:'用户管理'},{name:'admin-guides',label:'攻略治理'},{name:'admin-comments',label:'评论治理'},{name:'admin-destinations',label:'目的地管理'},{name:'admin-ai-settings',label:'AI服务设置'}]
</script>
<template>
  <nav aria-label="后台导航" class="mb-7 flex flex-wrap gap-2 border-b border-pine/15 pb-5"><RouterLink v-for="link in links" :key="link.name" :to="{name:link.name}" class="btn-ghost" exact-active-class="border-pine bg-pine-soft/60 text-pine-deep">{{link.label}}</RouterLink></nav>
</template>
