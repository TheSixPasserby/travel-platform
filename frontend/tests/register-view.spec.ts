import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'

import RegisterView from '@/views/RegisterView.vue'
import { ApiError } from '@/api/client'
import { registerAccount } from '@/api/auth'
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
      { path: '/register', name: 'register', component: { template: '<div>注册</div>' } }
    ]
  })
}

async function mountRegister() {
  const pinia = createPinia()
  setActivePinia(pinia)
  const store = useAuthStore()
  const router = buildRouter()
  await router.push('/register')
  await router.isReady()
  const pushSpy = vi.spyOn(router, 'push')
  const wrapper = mount(RegisterView, { global: { plugins: [pinia, router] } })
  return { wrapper, store, pushSpy }
}

async function fillAndSubmit(wrapper: Awaited<ReturnType<typeof mountRegister>>['wrapper']) {
  await wrapper.get('#username').setValue('luna')
  await wrapper.get('#password').setValue('secret123')
  await wrapper.get('#confirmPassword').setValue('secret123')
  await wrapper.get('form').trigger('submit')
  await flushPromises()
}

beforeEach(() => {
  vi.mocked(registerAccount).mockReset()
})

describe('注册页', () => {
  it('显示账号、密码、确认密码与可选的昵称输入', async () => {
    const { wrapper } = await mountRegister()

    expect(wrapper.get('label[for="username"]').text()).toContain('账号')
    expect(wrapper.get('label[for="password"]').text()).toContain('密码')
    expect(wrapper.get('label[for="confirmPassword"]').text()).toContain('确认密码')
    expect(wrapper.get('label[for="nickname"]').text()).toContain('昵称')
    expect(wrapper.get('#nickname').attributes('required')).toBeUndefined()
  })

  it('两次密码不一致时本地提示且不请求接口', async () => {
    const { wrapper } = await mountRegister()

    await wrapper.get('#username').setValue('luna')
    await wrapper.get('#password').setValue('secret123')
    await wrapper.get('#confirmPassword').setValue('secret456')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(registerAccount).not.toHaveBeenCalled()
    expect(wrapper.get('[role="alert"]').text()).toContain('两次输入的密码不一致')
  })

  it('注册成功后不自动登录，提示后跳转登录页', async () => {
    vi.mocked(registerAccount).mockResolvedValue({
      id: '1',
      username: 'luna',
      nickname: 'luna',
      avatarUrl: null,
      bio: null,
      role: 'USER',
      status: 'ACTIVE',
      createdAt: '2026-11-02T10:00:00.000+08:00'
    })
    const { wrapper, store, pushSpy } = await mountRegister()

    await fillAndSubmit(wrapper)

    expect(registerAccount).toHaveBeenCalledWith({
      username: 'luna',
      password: 'secret123',
      nickname: undefined
    })
    expect(store.isAuthenticated).toBe(false)
    expect(wrapper.text()).toContain('注册成功')
    expect(pushSpy).toHaveBeenCalledWith('/login?registered=1')
  })

  it('字段错误按字段名映射到输入框', async () => {
    vi.mocked(registerAccount).mockRejectedValue(
      new ApiError('请求参数有误', {
        status: 400,
        code: 40001,
        fieldErrors: [{ field: 'username', message: '账号需为 3–50 位字母、数字或下划线' }]
      })
    )
    const { wrapper } = await mountRegister()

    await fillAndSubmit(wrapper)

    const usernameError = wrapper.get('[data-field-error="username"]')
    expect(usernameError.text()).toContain('账号需为 3–50 位字母、数字或下划线')
    expect(wrapper.get('#username').attributes('aria-invalid')).toBe('true')
    expect(wrapper.get('#password').attributes('aria-invalid')).not.toBe('true')
  })

  it('账号冲突给出明确提示', async () => {
    vi.mocked(registerAccount).mockRejectedValue(
      new ApiError('该账号已被注册', { status: 409, code: 40901 })
    )
    const { wrapper } = await mountRegister()

    await fillAndSubmit(wrapper)

    expect(wrapper.get('[role="alert"]').text()).toContain('该账号已被注册')
  })
})
