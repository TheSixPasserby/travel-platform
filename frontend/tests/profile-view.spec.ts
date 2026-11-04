import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'

import ProfileView from '@/views/ProfileView.vue'
import ImageUpload from '@/components/ImageUpload.vue'
import { ApiError } from '@/api/client'
import { fetchProfile, patchProfile } from '@/api/auth'
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
  avatarUrl: '/uploads/avatars/luna.jpg',
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

async function mountProfile(options: { authenticated?: boolean; profile?: CurrentUser } = {}) {
  const pinia = createPinia()
  setActivePinia(pinia)
  const store = useAuthStore()
  if (options.authenticated !== false) {
    store.user = options.profile ?? user
    store.token = 'token-abc'
    sessionStorage.setItem('travel-notes.token', 'token-abc')
  }
  const router = buildRouter()
  await router.push('/me')
  await router.isReady()
  const pushSpy = vi.spyOn(router, 'push')
  const wrapper = mount(ProfileView, { global: { plugins: [pinia, router] } })
  return { wrapper, store, pushSpy }
}

beforeEach(() => {
  vi.mocked(fetchProfile).mockReset()
  vi.mocked(patchProfile).mockReset()
  vi.mocked(fetchProfile).mockResolvedValue(user)
})

describe('个人资料页', () => {
  it('上传头像先预览，保存后才更新个人资料', async () => {
    const url = '/uploads/2026/11/new-avatar.png'
    vi.mocked(patchProfile).mockResolvedValue({ ...user, avatarUrl: url })
    const { wrapper, store } = await mountProfile()
    await flushPromises()
    wrapper.getComponent(ImageUpload).vm.$emit('update:modelValue', url)
    await flushPromises()
    expect(patchProfile).not.toHaveBeenCalled()
    expect(store.user?.avatarUrl).toBe(user.avatarUrl)
    expect(wrapper.get('img').attributes('src')).toBe(url)
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(patchProfile).toHaveBeenCalledWith({ avatarUrl: url })
    expect(store.user?.avatarUrl).toBe(url)
  })

  it('上传过程中不提交资料，上传结束后可保存原来的编辑', async () => {
    vi.mocked(patchProfile).mockResolvedValue({ ...user, nickname: '山鹿' })
    const { wrapper } = await mountProfile()
    await flushPromises()
    await wrapper.get('#nickname').setValue('山鹿')
    wrapper.getComponent(ImageUpload).vm.$emit('busy', true)
    await flushPromises()
    await wrapper.get('form').trigger('submit')
    expect(patchProfile).not.toHaveBeenCalled()
    expect(wrapper.get('[data-test="profile-submit"]').attributes('disabled')).toBeDefined()
    wrapper.getComponent(ImageUpload).vm.$emit('busy', false)
    await flushPromises()
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(patchProfile).toHaveBeenCalledWith({ nickname: '山鹿' })
  })

  it('账号只读，昵称与简介可编辑', async () => {
    const { wrapper } = await mountProfile()
    await flushPromises()

    expect(wrapper.get('#nickname').attributes('readonly')).toBeUndefined()
    expect(wrapper.get('#username').attributes('readonly')).toBeDefined()
    expect(wrapper.get('label[for="bio"]').text()).toContain('简介')
  })

  it('只保存被修改的字段，未改动的字段不出现在请求里', async () => {
    vi.mocked(patchProfile).mockResolvedValue({ ...user, nickname: '山鹿' })
    const { wrapper, store } = await mountProfile()
    await flushPromises()

    await wrapper.get('#nickname').setValue('山鹿')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(patchProfile).toHaveBeenCalledWith({ nickname: '山鹿' })
    expect(store.user?.nickname).toBe('山鹿')
    expect(wrapper.get('[role="status"]').text()).toContain('资料已保存')
  })

  it('清空头像提交 null 并回退到占位图', async () => {
    vi.mocked(patchProfile).mockResolvedValue({ ...user, avatarUrl: null })
    const { wrapper, store } = await mountProfile()
    await flushPromises()

    await wrapper.get('[data-test="image-upload-remove"]').trigger('click')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(patchProfile).toHaveBeenCalledWith({ avatarUrl: null })
    expect(store.user?.avatarUrl).toBeNull()
    expect(wrapper.find('[data-test="avatar-fallback"]').exists()).toBe(true)
  })

  it('清空简介提交 null', async () => {
    vi.mocked(patchProfile).mockResolvedValue({ ...user, bio: null })
    const { wrapper } = await mountProfile()
    await flushPromises()

    await wrapper.get('#bio').setValue('   ')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(patchProfile).toHaveBeenCalledWith({ bio: null })
  })

  it('没有改动时提示无需保存且不发请求', async () => {
    const { wrapper } = await mountProfile()
    await flushPromises()

    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(patchProfile).not.toHaveBeenCalled()
    expect(wrapper.get('[role="status"]').text()).toContain('没有需要保存')
  })

  it('字段错误按字段名映射，提交按钮恢复可用', async () => {
    vi.mocked(patchProfile).mockRejectedValue(
      new ApiError('请求参数有误', {
        status: 400,
        code: 40001,
        fieldErrors: [{ field: 'bio', message: '简介最多 300 个字符' }]
      })
    )
    const { wrapper } = await mountProfile()
    await flushPromises()

    await wrapper.get('#bio').setValue('很长的简介')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(wrapper.get('[data-field-error="bio"]').text()).toContain('简介最多 300 个字符')
    expect(wrapper.get('[data-test="profile-submit"]').attributes('disabled')).toBeUndefined()
  })

  it('加载资料遇到 401 时回到登录页', async () => {
    vi.mocked(fetchProfile).mockRejectedValue(
      new ApiError('登录状态已失效，请重新登录', { status: 401, code: 40101 })
    )
    const { wrapper, store, pushSpy } = await mountProfile()
    await flushPromises()

    expect(store.isAuthenticated).toBe(false)
    expect(pushSpy).toHaveBeenCalledWith({ name: 'login', query: { redirect: '/me' } })
    expect(wrapper.find('#nickname').exists()).toBe(false)
  })

  it('资料加载网络失败给出可重试提示，保留登录态', async () => {
    vi.mocked(fetchProfile).mockRejectedValue(
      new ApiError('网络连接失败，请检查网络后重试', { isNetworkError: true })
    )
    const { wrapper, store } = await mountProfile()
    await flushPromises()

    expect(store.isAuthenticated).toBe(true)
    expect(wrapper.get('[role="alert"]').text()).toContain('网络连接失败')
    expect(wrapper.find('[data-test="profile-retry"]').exists()).toBe(true)
  })

  it('资料加载遇到 500 时给出可重试提示，重试成功后恢复表单', async () => {
    vi.mocked(fetchProfile).mockRejectedValueOnce(
      new ApiError('服务器开小差了，请稍后再试', { status: 500, code: 50001 })
    )
    const { wrapper, store } = await mountProfile()
    await flushPromises()

    expect(store.isAuthenticated).toBe(true)
    expect(wrapper.get('[role="alert"]').text()).toContain('服务器开小差了')
    expect(wrapper.find('[data-test="profile-retry"]').exists()).toBe(true)

    vi.mocked(fetchProfile).mockResolvedValueOnce(user)
    await wrapper.get('[data-test="profile-retry"]').trigger('click')
    await flushPromises()

    expect((wrapper.get('#nickname').element as HTMLInputElement).value).toBe('小鹿')
  })

  it('保存遇到 502 时提供重试入口，重试后保存成功', async () => {
    vi.mocked(patchProfile).mockRejectedValueOnce(
      new ApiError('服务器开小差了，请稍后再试', { status: 502, code: 50001 })
    )
    const { wrapper } = await mountProfile()
    await flushPromises()

    await wrapper.get('#nickname').setValue('山鹿')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toContain('服务器开小差了')

    vi.mocked(patchProfile).mockResolvedValueOnce({ ...user, nickname: '山鹿' })
    await wrapper.get('[role="alert"] button').trigger('click')
    await flushPromises()

    expect(wrapper.get('[role="status"]').text()).toContain('资料已保存')
  })

  it('保存遇到 401 时清空会话后跳转登录页并带上回跳地址，不留在空白页', async () => {
    const { wrapper, store, pushSpy } = await mountProfile()
    await flushPromises()

    vi.mocked(patchProfile).mockImplementationOnce(async () => {
      // 真实 axios 拦截器收到 401 时先触发全局处理器清空会话，再抛出 ApiError；
      // 这里复现同一状态，确保页面不会停留在 user 被清空后的空白视图。
      store.clearSession()
      throw new ApiError('登录状态已失效，请重新登录', { status: 401, code: 40101 })
    })

    await wrapper.get('#nickname').setValue('山鹿')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(store.user).toBeNull()
    expect(store.token).toBeNull()
    expect(pushSpy).toHaveBeenCalledWith({ name: 'login', query: { redirect: '/me' } })
  })

  it('昵称被清空时给出必填错误且不发送 PATCH', async () => {
    const { wrapper, store } = await mountProfile()
    await flushPromises()

    await wrapper.get('#nickname').setValue('   ')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(patchProfile).not.toHaveBeenCalled()
    expect(wrapper.get('[data-field-error="nickname"]').text()).toContain('昵称')
    expect(wrapper.text()).not.toContain('没有需要保存')
    expect(store.user?.nickname).toBe('小鹿')
  })

  it('未登录访问时跳转登录并带上回跳地址', async () => {
    const { pushSpy } = await mountProfile({ authenticated: false })
    await flushPromises()

    expect(pushSpy).toHaveBeenCalledWith({ name: 'login', query: { redirect: '/me' } })
  })
})
