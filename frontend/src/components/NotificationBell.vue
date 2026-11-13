<script setup lang="ts">
import { computed,onBeforeUnmount,onMounted,ref,watch } from 'vue'
import { useRoute } from 'vue-router'
import { fetchNotificationUnreadCount } from '@/api/notifications'
import { useAuthStore } from '@/stores/auth'

/** 轮询间隔：一分钟刷新一次未读数。 */
const POLL_INTERVAL=60000

const auth=useAuthStore(),route=useRoute()
const count=ref<number|null>(null)
let version=0
let timer:ReturnType<typeof setInterval>|null=null
let disposed=false

const badgeText=computed<string>(()=>count.value!==null&&count.value>99?'99+':String(count.value??0))
const ariaLabel=computed<string>(()=>{
  if(count.value===null)return '通知数量暂时无法获取'
  return count.value>0?`通知，${count.value} 条未读`:'通知'
})

function stopPolling():void{
  if(timer!==null){clearInterval(timer);timer=null}
}

async function refresh():Promise<void>{
  if(disposed||!auth.isAuthenticated)return
  const request=++version
  try{
    const data=await fetchNotificationUnreadCount()
    if(disposed||request!==version)return
    count.value=typeof data.count==='number'?Math.max(0,Math.trunc(data.count)):null
  }catch{
    // 未读数失败不打扰用户，也不用旧数字充数。
    if(disposed||request!==version)return
    count.value=null
  }
}

function resetCount():void{
  version++
  count.value=null
  stopPolling()
}

function startPolling():void{
  stopPolling()
  timer=setInterval(()=>{void refresh()},POLL_INTERVAL)
}

watch([()=>auth.user?.id,()=>auth.token],()=>{
  resetCount()
  if(auth.isAuthenticated){void refresh();startPolling()}
})

watch(()=>route.fullPath,()=>{void refresh()})

function onFocus():void{void refresh()}
onMounted(()=>{
  window.addEventListener('focus',onFocus)
  if(!auth.isAuthenticated)return
  void refresh()
  startPolling()
})

onBeforeUnmount(()=>{
  window.removeEventListener('focus',onFocus)
  disposed=true
  version++
  stopPolling()
})
</script>
<template>
  <RouterLink v-if="auth.isAuthenticated" to="/me/notifications" class="notification-bell" data-test="notification-bell" :aria-label="ariaLabel" :title="ariaLabel">
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
      <path d="M12 3.2a5.4 5.4 0 0 0-5.4 5.4v3.1l-1.5 3a1 1 0 0 0 .9 1.5h12a1 1 0 0 0 .9-1.5l-1.5-3V8.6A5.4 5.4 0 0 0 12 3.2Z" />
      <path d="M9.8 18.2a2.3 2.3 0 0 0 4.4 0" />
    </svg>
    <span v-if="count!==null&&count>0" class="notification-badge" data-test="notification-count">{{ badgeText }}</span>
  </RouterLink>
</template>
<style scoped>
.notification-bell{position:relative;display:inline-flex;align-items:center;justify-content:center;width:44px;height:44px;border-radius:9999px;color:var(--color-ink);transition:background-color .18s ease}
.notification-bell:hover{background:var(--color-pine-soft)}
.notification-bell:focus-visible{outline:2px solid var(--color-pine);outline-offset:2px}
.notification-bell svg{width:23px;height:23px}
.notification-badge{position:absolute;top:4px;right:2px;min-width:18px;height:18px;padding:0 4px;border-radius:9999px;background:var(--color-ember);color:#fff;font-size:11px;font-weight:600;line-height:18px;text-align:center;box-shadow:0 0 0 2px var(--color-surface)}
</style>
