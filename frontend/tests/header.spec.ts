import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'

import SiteHeader from '@/components/SiteHeader.vue'
import { ApiError } from '@/api/client'
import { logoutAccount } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'
import type { CurrentUser } from '@/types/auth'

vi.mock('@/api/auth', () => ({
  registerAccount: vi.fn(),
  loginAccount: vi.fn(),
  logoutAccount: vi.fn(),
  fetchCurrentUser: vi.fn(),
  fetchProfile: vi.fn(),
  patchProfile: vi.fn()
}))

const user: CurrentUser = {
  id: '1',
  username: 'luna',
  nickname: '小鹿',
  avatarUrl: null,
  bio: '喜欢山野与清晨',
  role: 'USER',
  status: 'ACTIVE',
  createdAt: '2026-11-02T10:00:00.000+08:00'
}

function buildRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'home', component: { template: '<div>首页</div>' } },
      { path: '/login', name: 'login', component: { template: '<div>登录</div>' } },
      { path: '/register', name: 'register', component: { template: '<div>注册</div>' } },
      { path: '/me', name: 'me', component: { template: '<div>资料</div>' } }
    ]
  })
}

async function mountHeader(authenticated: boolean) {
  const pinia = createPinia()
  setActivePinia(pinia)
  const store = useAuthStore()
  if (authenticated) {
    store.user = user
    store.token = 'token-abc'
    sessionStorage.setItem('travel-notes.token', 'token-abc')
  }
  const router = buildRouter()
  await router.push('/')
  await router.isReady()
  const pushSpy = vi.spyOn(router, 'push')
  const wrapper = mount(SiteHeader, { global: { plugins: [pinia, router] } })
  return { wrapper, store, router, pushSpy }
}

beforeEach(() => {
  vi.mocked(logoutAccount).mockReset()
})

describe('站点页头', () => {
  it('管理后台入口只对管理员显示并指向用户管理',async()=>{
    const {wrapper,store}=await mountHeader(true);expect(wrapper.find('a[href="/admin/users"]').exists()).toBe(false)
    store.user={...user,role:'ADMIN'};await flushPromises();expect(wrapper.get('a[href="/admin/users"]').text()).toBe('管理后台');wrapper.unmount()
  })
  it('游客看到登录与注册入口，不显示个人资料入口', async () => {
    const { wrapper } = await mountHeader(false)

    const links = wrapper.findAll('a').map((link) => link.attributes('href'))
    expect(links).toContain('/login')
    expect(links).toContain('/register')
    expect(links).not.toContain('/me')
    expect(wrapper.text()).toContain('山水间')
  })

  it('登录后显示个人资料与退出入口', async () => {
    const { wrapper } = await mountHeader(true)

    const links = wrapper.findAll('a').map((link) => link.attributes('href'))
    expect(links).toContain('/me')
    expect(links).not.toContain('/login')
    expect(wrapper.text()).toContain('退出登录')
  })

  it('退出成功清理会话并回到首页', async () => {
    vi.mocked(logoutAccount).mockResolvedValue(null)
    const { wrapper, store, pushSpy } = await mountHeader(true)

    await wrapper.get('[data-test="logout"]').trigger('click')
    await flushPromises()

    expect(logoutAccount).toHaveBeenCalledTimes(1)
    expect(store.isAuthenticated).toBe(false)
    expect(pushSpy).toHaveBeenCalledWith('/')
  })

  it('退出遇到网络失败时保留登录态并给出重试，不谎报成功', async () => {
    vi.mocked(logoutAccount).mockRejectedValue(
      new ApiError('网络连接失败，请检查网络后重试', { isNetworkError: true })
    )
    const { wrapper, store, pushSpy } = await mountHeader(true)

    await wrapper.get('[data-test="logout"]').trigger('click')
    await flushPromises()

    expect(store.isAuthenticated).toBe(true)
    expect(pushSpy).not.toHaveBeenCalled()
    const alert = wrapper.get('[role="alert"]')
    expect(alert.text()).toContain('网络连接失败')
    expect(wrapper.find('[data-test="logout-retry"]').exists()).toBe(true)
  })
})
