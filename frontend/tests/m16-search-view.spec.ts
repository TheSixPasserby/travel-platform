import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter, RouterView } from 'vue-router'
import { createPinia, setActivePinia } from 'pinia'
import { searchPublicContent } from '@/api/search'
import { clearFeedCache } from '@/composables/useInfiniteFeed'
import SearchView from '@/views/SearchView.vue'
import type { SearchPage, SearchHit } from '@/types/search'

vi.mock('@/api/search', () => ({ searchPublicContent: vi.fn() }))
vi.mock('@/components/SearchBox.vue', () => ({ default: {
  name: 'SearchBox', props: ['modelValue'], emits: ['update:modelValue', 'submit'],
  template: '<form @submit.prevent="$emit(\'submit\', modelValue)"><input :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" /></form>'
} }))

const hit = (title: string, kind: SearchHit['kind'] = 'GUIDE'): SearchHit => ({
  kind, id: title, title, subtitle: null, coverImageUrl: null, username: null, href: '/guides/4'
})
const page = (...records: SearchHit[]): SearchPage => ({ records, nextCursor: null, hasMore: false })
const mounted: Array<ReturnType<typeof mount>> = []
async function render(path: string) {
  const pinia = createPinia(); setActivePinia(pinia)
  const router = createRouter({ history: createMemoryHistory(), routes: [
    { path: '/search', component: SearchView }, { path: '/:pathMatch(.*)*', component: { template: '<div />' } }
  ] })
  await router.push(path); await router.isReady()
  const wrapper = mount(RouterView, { global: { plugins: [pinia, router] } }); mounted.push(wrapper)
  await flushPromises()
  return { wrapper, router }
}
beforeEach(() => { vi.clearAllMocks(); clearFeedCache(); vi.mocked(searchPublicContent).mockResolvedValue(page(hit('成都慢行'))) })
afterEach(() => { mounted.splice(0).forEach(wrapper => wrapper.unmount()); vi.unstubAllGlobals() })

describe('M16 search page', () => {
  it('starts with a meaningful empty prompt and searches only after explicit submit', async () => {
    const { wrapper, router } = await render('/search')
    expect(searchPublicContent).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('开始你的下一次出发')
    await wrapper.find('input').setValue('成都')
    await wrapper.find('form').trigger('submit'); await flushPromises()
    expect(router.currentRoute.value.query.keyword).toBe('成都')
    expect(wrapper.text()).toContain('成都慢行')
  })
  it('restores URL category and sorting and updates requests when navigating back', async () => {
    const { wrapper, router } = await render('/search?keyword=成都&kind=CITY&sort=latest')
    expect(searchPublicContent).toHaveBeenLastCalledWith(expect.objectContaining({ keyword: '成都', kind: 'CITY', sort: 'latest' }), expect.any(AbortSignal))
    await wrapper.findAll('button').find(b => b.text() === '景点')!.trigger('click'); await flushPromises()
    expect(router.currentRoute.value.query.kind).toBe('SCENIC_SPOT')
    router.back(); await flushPromises()
    expect(router.currentRoute.value.query.kind).toBe('CITY')
  })
  it('normalizes inherited object properties in untrusted URL types', async () => {
    await render('/search?keyword=成都&kind=__proto__&sort=untrusted')
    expect(searchPublicContent).toHaveBeenLastCalledWith(expect.objectContaining({ kind: undefined, sort: 'relevance' }), expect.any(AbortSignal))
  })
  it('ignores late requests when clearing a search', async () => {
    let resolve!: (value: SearchPage) => void
    vi.mocked(searchPublicContent).mockImplementationOnce(() => new Promise(done => { resolve = done }))
    const { wrapper, router } = await render('/search?keyword=旧请求')
    await router.push('/search'); await flushPromises()
    resolve(page(hit('过期内容'))); await flushPromises()
    expect(wrapper.text()).toContain('开始你的下一次出发')
    expect(wrapper.text()).not.toContain('过期内容')
  })
  it('observes the sentinel after the asynchronous result DOM appears', async () => {
    const observe = vi.fn()
    vi.stubGlobal('IntersectionObserver', class { observe = observe; unobserve = vi.fn(); disconnect = vi.fn() })
    await render('/search?keyword=成都')
    expect(observe).toHaveBeenCalledWith(expect.any(HTMLElement))
  })
  it('shows a retry for failed searches and real empty results after retry', async () => {
    vi.mocked(searchPublicContent).mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce(page())
    const { wrapper } = await render('/search?keyword=成都')
    expect(wrapper.find('[role="alert"]').exists()).toBe(true)
    await wrapper.findAll('button').find(b => b.text() === '重新搜索')!.trigger('click'); await flushPromises()
    expect(wrapper.text()).toContain('没有找到与“成都”相关的内容')
  })
})
