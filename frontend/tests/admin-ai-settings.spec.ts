import { afterEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import AdminAiSettingsView from '@/views/AdminAiSettingsView.vue'
import { useAuthStore } from '@/stores/auth'
import { fetchAiSettings, saveAiSettings } from '@/api/aiSettings'
import type { AiSettings } from '@/types/aiSettings'

vi.mock('@/api/aiSettings', () => ({ fetchAiSettings: vi.fn(), saveAiSettings: vi.fn() }))

const settings: AiSettings = {
  version: 0,
  enabled: false,
  provider: 'custom',
  baseUrl: '',
  model: '',
  responseFormat: 'auto',
  connectTimeoutSeconds: 5,
  timeoutSeconds: 45,
  maxOutputTokens: 6000,
  apiKeyConfigured: false,
  source: 'environment',
  warning: null,
  presets: [
    { id: 'custom', label: '自定义兼容服务', baseUrl: '', model: '', responseFormat: 'json_object' },
    {
      id: 'deepseek', label: 'DeepSeek', baseUrl: 'https://api.deepseek.com',
      model: 'deepseek-flash', responseFormat: 'json_object'
    }
  ]
}
const wrappers: VueWrapper[] = []
afterEach(() => {
  wrappers.splice(0).forEach(wrapper => wrapper.unmount())
  vi.resetAllMocks()
})

async function render() {
  const pinia = createPinia()
  setActivePinia(pinia)
  const auth = useAuthStore()
  auth.token = 'test-token'
  auth.user = {
    id: '1', username: 'admin', nickname: '管理员', avatarUrl: null, bio: null,
    role: 'ADMIN', status: 'ACTIVE', createdAt: '2026-11-11'
  }
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/admin/ai', name: 'admin-ai-settings', component: AdminAiSettingsView },
      { path: '/itineraries/ai', name: 'ai-itinerary', component: { template: '<p>行程</p>' } },
      { path: '/login', name: 'login', component: { template: '<p>登录</p>' } }
    ]
  })
  await router.push('/admin/ai')
  await router.isReady()
  const wrapper = mount(AdminAiSettingsView, {
    global: { plugins: [pinia, router], stubs: { AdminNavigation: true } }
  })
  wrappers.push(wrapper)
  await flushPromises()
  return { wrapper, auth }
}

describe('网页AI服务设置', () => {
  it('加载后可选预设并保存新密钥，成功后清空输入并显示配置状态', async () => {
    vi.mocked(fetchAiSettings).mockResolvedValue(settings)
    vi.mocked(saveAiSettings).mockResolvedValue({
      ...settings, version: 1, enabled: true, apiKeyConfigured: true, source: 'saved'
    })
    const { wrapper } = await render()
    await wrapper.get('[data-test="ai-provider"]').setValue('deepseek')
    expect(wrapper.get<HTMLInputElement>('[data-test="ai-base-url"]').element.value)
      .toBe('https://api.deepseek.com')
    await wrapper.get('[data-test="ai-api-key"]').setValue('NEW_PRIVATE_KEY')
    await wrapper.get('[data-test="ai-enabled"]').setValue(true)
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(saveAiSettings).toHaveBeenCalledWith(expect.objectContaining({
      apiKey: 'NEW_PRIVATE_KEY', provider: 'deepseek', enabled: true, version: 0
    }))
    expect(wrapper.get<HTMLInputElement>('[data-test="ai-api-key"]').element.value).toBe('')
    expect(wrapper.text()).toContain('设置已保存')
    expect(wrapper.html()).not.toContain('NEW_PRIVATE_KEY')
  })

  it('原密钥留空沿用，失败保留输入和表单供重试', async () => {
    vi.mocked(fetchAiSettings).mockResolvedValue({ ...settings, version: 3, apiKeyConfigured: true })
    vi.mocked(saveAiSettings).mockRejectedValue(new Error('offline'))
    const { wrapper } = await render()
    await wrapper.get('[data-test="ai-model"]').setValue('my-model')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(saveAiSettings).toHaveBeenCalledWith(expect.objectContaining({
      version: 3, apiKey: undefined, model: 'my-model'
    }))
    expect(wrapper.get('[role="alert"]').text()).toContain('保存未能完成')
    expect(wrapper.get<HTMLInputElement>('[data-test="ai-model"]').element.value).toBe('my-model')
  })

  it('账号切换立即清空密钥，旧请求结果不能填入新会话', async () => {
    vi.mocked(fetchAiSettings).mockResolvedValue(settings)
    let finish!: (value: AiSettings) => void
    vi.mocked(saveAiSettings).mockReturnValue(new Promise(resolve => { finish = resolve }))
    const { wrapper, auth } = await render()
    await wrapper.get('[data-test="ai-api-key"]').setValue('DRAFT_PRIVATE_KEY')
    await wrapper.get('form').trigger('submit')
    auth.clearSession()
    await flushPromises()
    finish({ ...settings, version: 1, model: 'old-response' })
    await flushPromises()
    expect(wrapper.html()).not.toContain('DRAFT_PRIVATE_KEY')
    expect(wrapper.text()).not.toContain('设置已保存')
    expect(wrapper.text()).not.toContain('old-response')
  })

  it('清除已配置的密钥时同步关闭服务，提交可保存的完整状态', async () => {
    vi.mocked(fetchAiSettings).mockResolvedValue({ ...settings, enabled: true, apiKeyConfigured: true })
    vi.mocked(saveAiSettings).mockResolvedValue({ ...settings, version: 1, source: 'saved' })
    const { wrapper } = await render()
    await wrapper.get('[data-test="ai-clear-key"]').setValue(true)
    expect(wrapper.get<HTMLInputElement>('[data-test="ai-enabled"]').element.checked).toBe(false)
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(saveAiSettings).toHaveBeenCalledWith(expect.objectContaining({
      enabled: false, clearApiKey: true, apiKey: undefined
    }))
    expect(wrapper.text()).toContain('设置已保存')
  })
  it('保存关闭状态时明确提示需要启用，避免将保存视为服务已经开启', async () => {
    vi.mocked(fetchAiSettings).mockResolvedValue({ ...settings, apiKeyConfigured: true })
    vi.mocked(saveAiSettings).mockResolvedValue({ ...settings, version: 1, source: 'saved', apiKeyConfigured: true })
    const { wrapper } = await render()
    expect(wrapper.get('[data-test="ai-service-state"]').text()).toContain('已关闭')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.get('[data-test="ai-save-notice"]').text()).toContain('当前 AI 已关闭')
  })
})
