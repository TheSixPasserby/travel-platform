vi.mock('@/components/NotificationBell.vue', () => ({ default: { template: '<span />' } }))
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'

import SiteHeader from '@/components/SiteHeader.vue'
import { ApiError } from '@/api/client'
import { logoutAccount } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'
import type { CurrentUser } from '@/types/auth'

enableAutoUnmount(afterEach)

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
      { path: '/me/guides', component: {template:'<div />'} },
      { path: '/register', name: 'register', component: { template: '<div>注册</div>' } },
      ...['/guides', '/destinations', '/itineraries/ai', '/me/itineraries', '/me/favorites', '/guides/new', '/admin/users'].map(path => ({ path, component: { template: '<div>页面</div>' } })),
      { path: '/me', name: 'me', component: { template: '<div>资料</div>' } }
    ]
  })
}

async function mountHeader(authenticated: boolean, attachTo?: HTMLElement) {
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
  const wrapper = mount(SiteHeader, { attachTo, global: { plugins: [pinia, router], stubs: { Teleport: true } } })
  return { wrapper, store, router, pushSpy }
}

beforeEach(() => {
  vi.mocked(logoutAccount).mockReset()
})

describe('站点页头', () => {
  it('手机菜单可以展开，Escape关闭并恢复焦点', async () => {
    const { wrapper } = await mountHeader(false, document.body)
    const toggle = wrapper.get('[data-test="mobile-menu-toggle"]')
    expect(toggle.attributes('aria-expanded')).toBe('false')
    await toggle.trigger('click')
    expect(toggle.attributes('aria-expanded')).toBe('true')
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    await flushPromises()
    expect(toggle.attributes('aria-expanded')).toBe('false')
    expect(document.activeElement).toBe(toggle.element)
    await wrapper.get('[data-test="settings-toggle"]').trigger('click')
    expect(wrapper.get('#site-settings').text()).toContain('跟随系统')
    wrapper.unmount()
  })

  it('导航后收起手机菜单，登录后业务入口齐全', async () => {
    const { wrapper, router } = await mountHeader(true)
    await wrapper.get('[data-test="mobile-menu-toggle"]').trigger('click')
    for (const href of ['/me/itineraries', '/me/favorites', '/guides/new', '/itineraries/ai']) {
      expect(wrapper.find(`a[href="${href}"]`).exists()).toBe(true)
    }
    await router.push('/me/itineraries')
    await flushPromises()
    expect(wrapper.get('[data-test="mobile-menu-toggle"]').attributes('aria-expanded')).toBe('false')
    wrapper.unmount()
  })
  it('管理后台入口只对管理员显示并指向用户管理',async()=>{
    const {wrapper,store}=await mountHeader(true);await wrapper.get('[data-test="mobile-menu-toggle"]').trigger('click');expect(wrapper.find('a[href="/admin/users"]').exists()).toBe(false)
    store.user={...user,role:'ADMIN'};await flushPromises();expect(wrapper.get('a[href="/admin/users"]').text()).toBe('管理后台');wrapper.unmount()
  })
  it('游客看到登录与注册入口，不显示个人资料入口', async () => {
    const { wrapper } = await mountHeader(false)
    await wrapper.get('[data-test="mobile-menu-toggle"]').trigger('click')
    const links = wrapper.findAll('a').map((link) => link.attributes('href'))
    expect(links).toContain('/login')
    expect(links).toContain('/register')
    expect(links).not.toContain('/me')
    expect(wrapper.text()).toContain('山水间')
  })

  it('登录后显示个人资料与退出入口', async () => {
    const { wrapper } = await mountHeader(true)
    await wrapper.get('[data-test="account-toggle"]').trigger('click')
    const links = wrapper.findAll('a').map((link) => link.attributes('href'))
    expect(links).toContain('/users/luna')
    expect(links).toContain('/me/settings')
    expect(links).not.toContain('/login')
    expect(wrapper.text()).toContain('退出登录')
  })

  it('退出成功清理会话并回到首页', async () => {
    vi.mocked(logoutAccount).mockResolvedValue(null)
    const { wrapper, store, pushSpy } = await mountHeader(true)

    await wrapper.get('[data-test="account-toggle"]').trigger('click')
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

    await wrapper.get('[data-test="account-toggle"]').trigger('click')
    await wrapper.get('[data-test="logout"]').trigger('click')
    await flushPromises()

    expect(store.isAuthenticated).toBe(true)
    expect(pushSpy).not.toHaveBeenCalled()
    const alert = wrapper.get('[role="alert"]')
    expect(alert.text()).toContain('网络连接失败')
    expect(wrapper.find('[data-test="logout-retry"]').exists()).toBe(true)
  })
})


describe('M10 浮层交互', () => {
  it('设置、账号与抽屉互斥；外点和Esc关闭、焦点还原', async () => {
    const {wrapper}=await mountHeader(true,document.body)
    await wrapper.get('[data-test="mobile-menu-toggle"]').trigger('click')
    expect(document.body.style.overflow).toBe('hidden')
    expect(document.querySelector('#site-navigation')).not.toBeNull()
    await wrapper.get('[data-test="settings-toggle"]').trigger('click')
    expect(document.querySelector('#site-navigation')).toBeNull()
    expect(document.querySelector('#site-settings')).not.toBeNull()
    await wrapper.get('[data-test="account-toggle"]').trigger('click')
    expect(document.querySelector('#site-settings')).toBeNull()
    expect(document.querySelector('#site-account')?.textContent).toContain('@luna')
    window.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape'}));await flushPromises()
    expect(document.querySelector('#site-account')).toBeNull()
    expect(document.activeElement).toBe(wrapper.get('[data-test="account-toggle"]').element)
    expect(document.body.style.overflow).toBe('')
    await wrapper.get('[data-test="settings-toggle"]').trigger('click')
    document.querySelector('[data-test="overlay-backdrop"]')?.dispatchEvent(new MouseEvent('click',{bubbles:true}))
    await flushPromises();expect(document.querySelector('#site-settings')).toBeNull()
    wrapper.unmount()
  })
  it('三态主题实时保存，账号切换关闭旧账号面板', async () => {
    const {wrapper,store}=await mountHeader(true,document.body)
    await wrapper.get('[data-test="settings-toggle"]').trigger('click')
    document.querySelector<HTMLButtonElement>('[data-theme-option="dark"]')!.click();await flushPromises()
    expect(document.documentElement.dataset.theme).toBe('dark')
    expect(localStorage.getItem('travel-notes.theme')).toBe('dark')
    await wrapper.get('[data-test="account-toggle"]').trigger('click')
    store.user={...user,id:'2',username:'other'};await flushPromises()
    expect(document.querySelector('#site-account')).toBeNull()
    wrapper.unmount()
  })
  it('卸载恢复先前滚动状态，Tab焦点保持在面板内', async () => {
    document.body.style.overflow='scroll'
    const {wrapper}=await mountHeader(false,document.body)
    await wrapper.get('[data-test="settings-toggle"]').trigger('click');await flushPromises()
    const panel=document.querySelector<HTMLElement>('#site-settings')!
    const buttons=panel.querySelectorAll<HTMLButtonElement>('button')
    buttons[0].focus();window.dispatchEvent(new KeyboardEvent('keydown',{key:'Tab',shiftKey:true,cancelable:true}));await flushPromises()
    expect(document.activeElement).toBe(buttons[buttons.length-1])
    panel.focus();window.dispatchEvent(new KeyboardEvent('keydown',{key:'Tab',shiftKey:true,cancelable:true}));await flushPromises()
    expect(document.activeElement).toBe(buttons[buttons.length-1])
    wrapper.unmount();expect(document.body.style.overflow).toBe('scroll');document.body.style.overflow=''
  })
  it('已关闭的面板不在后续路由更新时抢走焦点', async () => {
    const {wrapper,router}=await mountHeader(false,document.body)
    await wrapper.get('[data-test="settings-toggle"]').trigger('click')
    window.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape'}));await flushPromises()
    const input=document.createElement('input');document.body.append(input);input.focus()
    await router.push('/guides');await flushPromises()
    expect(document.activeElement).toBe(input);input.remove();wrapper.unmount()
  })
})
