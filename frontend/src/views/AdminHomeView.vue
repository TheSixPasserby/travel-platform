<script setup lang="ts">
import { onBeforeUnmount,ref,watch } from 'vue'
import { useRoute,useRouter } from 'vue-router'
import { fetchAdminSummary } from '@/api/administration'
import { ApiError } from '@/api/client'
import AdminNavigation from '@/components/AdminNavigation.vue'
import { useAuthStore } from '@/stores/auth'
import type { AdminSummary } from '@/types/administration'
const route=useRoute(),router=useRouter(),auth=useAuthStore()
const data=ref<AdminSummary|null>(null),loading=ref(true),error=ref('')
let version=0
const metrics=[{key:'userCount',label:'用户',target:'admin-users'},{key:'guideCount',label:'攻略',target:'admin-guides'},{key:'destinationCount',label:'目的地',target:'admin-destinations'},{key:'commentCount',label:'评论',target:'admin-comments'}] as const
async function load():Promise<void>{
  const ticket=++version,redirect=route.fullPath;loading.value=true;error.value='';data.value=null
  try{const result=await fetchAdminSummary();if(ticket===version)data.value=result}
  catch(cause){if(ticket!==version)return;if(cause instanceof ApiError&&cause.status===401){if(route.name!=='login')void router.replace({name:'login',query:{redirect}})}else error.value=cause instanceof ApiError?cause.message:'概览暂时无法加载，请稍后重试'}
  finally{if(ticket===version)loading.value=false}
}
watch([()=>route.fullPath,()=>auth.user?.id,()=>auth.token],()=>{version++;data.value=null;if(route.name==='admin-home')void load()},{immediate:true})
onBeforeUnmount(()=>{version++})
</script>
<template>
  <section class="app-container py-8 lg:py-12">
    <AdminNavigation />
    <header class="mb-8"><p class="text-xs tracking-[0.3em] text-ember">THE COMMUNITY AT A GLANCE</p><h1 class="mt-3 font-serif text-3xl text-pine-deep">后台概览</h1><p class="mt-3 max-w-2xl text-sm leading-relaxed text-ink-soft">总览包含停用账号、草稿和隐藏内容；公开展示仍由各自状态决定。</p></header>
    <div v-if="loading" role="status" class="surface-card p-12 text-center text-ink-soft">正在读取社区概览…</div>
    <div v-else-if="error" role="alert" class="surface-card p-7"><p>{{error}}</p><button type="button" data-test="retry-admin-summary" class="btn-ghost mt-5" @click="load">重新加载</button></div>
    <dl v-else-if="data" class="grid gap-5 sm:grid-cols-2 xl:grid-cols-4"><div v-for="metric in metrics" :key="metric.key" class="surface-card p-6"><dt class="text-sm text-ink-soft">{{metric.label}}</dt><dd :data-test="'admin-stat-'+metric.key" class="mt-4 font-serif text-4xl tabular-nums text-pine-deep">{{data[metric.key]}}</dd><RouterLink :to="{name:metric.target}" class="mt-6 inline-block text-sm text-pine hover:underline">前往管理 ↗</RouterLink></div></dl>
  </section>
</template>
