import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'

import LoginView from '@/views/LoginView.vue'
import { ApiError } from '@/api/client'
import { loginAccount } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'

vi.mock('@/api/auth', () => ({
  registerAccount: vi.fn(),
  loginAccount: vi.fn(),
  logoutAccount: vi.fn(),
  fetchCurrentUser: vi.fn(),
  fetchProfile: vi.fn(),
  patchProfile: vi.fn()
}))

function buildRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'home', component: { template: '<div>首页</div>' } },
      { path: '/login', name: 'login', component: { template: '<div>登录</div>' } },
      { path: '/me', name: 'me', component: { template: '<div>资料</div>' } }
    ]
  })
}

async function mountLogin(search = '') {
  const pinia = createPinia()
  setActivePinia(pinia)
  const store = useAuthStore()
  const router = buildRouter()
  await router.push(`/login${search}`)
  await router.isReady()
  const pushSpy = vi.spyOn(router, 'push')
  const wrapper = mount(LoginView, { global: { plugins: [pinia, router] } })
  return { wrapper, store, router, pushSpy }
}

async function fillAndSubmit(wrapper: Awaited<ReturnType<typeof mountLogin>>['wrapper']) {
  await wrapper.get('#username').setValue('luna')
  await wrapper.get('#password').setValue('secret123')
  await wrapper.get('form').trigger('submit')
  await flushPromises()
}

beforeEach(() => {
  vi.mocked(loginAccount).mockReset()
})

describe('登录页', () => {
  it('表单包含可访问的账号与密码输入框', async () => {
    const { wrapper } = await mountLogin()

    expect(wrapper.get('label[for="username"]').text()).toContain('账号')
    expect(wrapper.get('label[for="password"]').text()).toContain('密码')
    expect(wrapper.get('#username').attributes('autocomplete')).toBe('username')
    expect(wrapper.get('#password').attributes('autocomplete')).toBe('current-password')
  })

  it('登录成功后按内部重定向地址跳转', async () => {
    vi.mocked(loginAccount).mockResolvedValue({
      tokenName: 'satoken',
      tokenValue: 'token-abc',
      user: {
        id: '1',
        username: 'luna',
        nickname: '小鹿',
        avatarUrl: null,
        bio: null,
        role: 'USER',
        status: 'ACTIVE',
        createdAt: '2026-11-02T10:00:00.000+08:00'
      }
    })
    const { wrapper, store, pushSpy } = await mountLogin('?redirect=/me?tab=profile')

    await fillAndSubmit(wrapper)

    expect(store.isAuthenticated).toBe(true)
    expect(pushSpy).toHaveBeenCalledWith('/me?tab=profile')
    expect(sessionStorage.getItem('travel-notes.token')).toBe('token-abc')
  })

  it('外部重定向地址被拒绝，登录后回到个人资料', async () => {
    vi.mocked(loginAccount).mockResolvedValue({
      tokenName: 'satoken',
      tokenValue: 'token-abc',
      user: {
        id: '1',
        username: 'luna',
        nickname: '小鹿',
        avatarUrl: null,
        bio: null,
        role: 'USER',
        status: 'ACTIVE',
        createdAt: '2026-11-02T10:00:00.000+08:00'
      }
    })
    const { wrapper, pushSpy } = await mountLogin('?redirect=//evil.example/me')

    await fillAndSubmit(wrapper)

    expect(pushSpy).toHaveBeenCalledWith('/me')
  })

  it('字段错误显示在对应输入框附近且不泄漏后端字段名以外的信息', async () => {
    vi.mocked(loginAccount).mockRejectedValue(
      new ApiError('账号或密码不正确', { status: 401, code: 40102 })
    )
    const { wrapper } = await mountLogin()

    await fillAndSubmit(wrapper)

    const alert = wrapper.get('[role="alert"]')
    expect(alert.text()).toContain('账号或密码不正确')
    expect(wrapper.get('#password').attributes('aria-invalid')).toBe('true')
  })

  it('提交中禁用按钮防止重复提交', async () => {
    let resolveLogin: (value: never) => void = () => undefined
    vi.mocked(loginAccount).mockImplementation(
      () => new Promise((resolve) => (resolveLogin = resolve as (value: never) => void))
    )
    const { wrapper } = await mountLogin()

    await wrapper.get('#username').setValue('luna')
    await wrapper.get('#password').setValue('secret123')
    const form = wrapper.get('form')
    await form.trigger('submit')
    await form.trigger('submit')
    await flushPromises()

    expect(loginAccount).toHaveBeenCalledTimes(1)
    const button = wrapper.get('[data-test="login-submit"]')
    expect(button.attributes('disabled')).toBeDefined()

    resolveLogin(undefined as never)
    await flushPromises()
  })

  it('网络失败提示可重试并保留输入内容', async () => {
    vi.mocked(loginAccount).mockRejectedValue(
      new ApiError('网络连接失败，请检查网络后重试', { isNetworkError: true })
    )
    const { wrapper } = await mountLogin()

    await fillAndSubmit(wrapper)

    expect(wrapper.get('[role="alert"]').text()).toContain('网络连接失败')
    expect(wrapper.find('[data-test="login-retry"]').exists()).toBe(true)
    expect((wrapper.get('#username').element as HTMLInputElement).value).toBe('luna')
  })
})
