import { afterEach, beforeEach, expect, it } from 'vitest'
import { flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { AxiosError } from 'axios'
import { apiClient, setUnauthorizedHandler } from '@/api/client'
import { connectSessionErrors } from '@/api/session'
import { TOKEN_STORAGE_KEY } from '@/api/token'
import { useAuthStore } from '@/stores/auth'

beforeEach(() => { sessionStorage.clear(); setActivePinia(createPinia()) })
afterEach(() => { setUnauthorizedHandler(null) })
async function setup(path: string) {
  sessionStorage.setItem(TOKEN_STORAGE_KEY, 'synthetic-session')
  const auth = useAuthStore()
  auth.user = { id: '9007199254740993', username: 'synthetic', nickname: '旅人', avatarUrl: null, bio: null,
    role: 'USER', status: 'ACTIVE', createdAt: '2026-11-03T00:00:00.000+08:00' }
  const router = createRouter({ history: createMemoryHistory(), routes: [
    { path: '/me', name: 'me', component: { template: '<div />' }, meta: { requiresAuth: true } },
    { path: '/guides/new', name: 'guide-new', component: { template: '<div />' }, meta: { requiresAuth: true } },
    { path: '/login', name: 'login', component: { template: '<div />' } }
  ] })
  await router.push(path)
  connectSessionErrors(auth, router)
  return { auth, router }
}
async function rejected(status: number) {
  return apiClient.get('/synthetic', { adapter: async config => {
    throw new AxiosError('Synthetic failure', undefined, config, undefined,
      { status, statusText: 'Failure', headers: {}, config, data: { code: status === 401 ? 40101 : 50001, message: '请求未完成', data: null } })
  } })
}
it('受保护页面收到真实请求层401后清会话并跳登录，保留内部回跳', async () => {
  const { auth, router } = await setup('/me')
  await expect(rejected(401)).rejects.toMatchObject({ status: 401 })
  await flushPromises()
  expect(auth.user).toBeNull(); expect(auth.token).toBeNull()
  expect(sessionStorage.getItem(TOKEN_STORAGE_KEY)).toBeNull()
  expect(router.currentRoute.value.name).toBe('login')
  expect(router.currentRoute.value.query.redirect).toBe('/me')
})
it('登录页面的401不会形成重定向循环', async () => {
  const { router } = await setup('/login')
  await expect(rejected(401)).rejects.toMatchObject({ status: 401 })
  await flushPromises()
  expect(router.currentRoute.value.fullPath).toBe('/login')
})
it('服务临时失败保留会话与当前页面', async () => {
  const { auth, router } = await setup('/me')
  await expect(rejected(503)).rejects.toMatchObject({ status: 503 })
  expect(auth.isAuthenticated).toBe(true)
  expect(router.currentRoute.value.name).toBe('me')
})

it('攻略编辑中401清除失效会话但保留页面并通知显示草稿恢复提示',async()=>{
  const {auth,router}=await setup('/guides/new')
  let notified=false;const listener=()=>{notified=true};window.addEventListener('travel:editor-session-expired',listener)
  try{await expect(rejected(401)).rejects.toMatchObject({status:401});await flushPromises();expect(auth.token).toBeNull();expect(router.currentRoute.value.name).toBe('guide-new');expect(notified).toBe(true)}finally{window.removeEventListener('travel:editor-session-expired',listener)}
})
