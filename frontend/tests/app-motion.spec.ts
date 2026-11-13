vi.mock('@/components/NotificationBell.vue', () => ({ default: { template: '<span />' } }))
import { describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import App from '@/App.vue'

describe('页面过渡', () => {
  it('支持 M8 中含多个根节点的页面', async () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => undefined)
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/', component: { template: '<section>第一部分</section><section>第二部分</section>' } }]
    })
    await router.push('/')
    const wrapper = mount(App, {
      global: { plugins: [createPinia(), router], stubs: { transition: false, SiteHeader: true, SiteFooter: true } }
    })
    await flushPromises()
    expect(wrapper.find('main')?.text()).toBe('第一部分第二部分')
    expect(warn.mock.calls.flat().join(' ')).not.toContain('non-element root')
    wrapper.unmount()
    warn.mockRestore()
  })
})
