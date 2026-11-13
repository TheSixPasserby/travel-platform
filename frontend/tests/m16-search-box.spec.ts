import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import SearchBox from '@/components/SearchBox.vue'
import { searchPublicContent } from '@/api/search'
import type { SearchPage } from '@/types/search'
vi.mock('@/api/search', () => ({ searchPublicContent: vi.fn() }))
const result: SearchPage = { records: [{ kind: 'CITY', id: '1', title: '成都', subtitle: null, coverImageUrl: null, username: null, href: '/destinations/1' }], nextCursor: null, hasMore: false }
const wrappers: Array<ReturnType<typeof mount>> = []
async function render() {
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/:pathMatch(.*)*', component: { template: '<div />' } }] })
  await router.push('/search'); await router.isReady()
  const wrapper = mount(SearchBox, { props: { modelValue: '' }, global: { plugins: [router] } }); wrappers.push(wrapper)
  return { wrapper, router }
}
beforeEach(() => { vi.useFakeTimers(); vi.clearAllMocks(); localStorage.clear(); vi.mocked(searchPublicContent).mockResolvedValue(result) })
afterEach(() => { wrappers.splice(0).forEach(wrapper => wrapper.unmount()); vi.useRealTimers(); vi.restoreAllMocks() })
describe('search suggestions', () => {
  it('submits trimmed input and keeps clearable browser history', async () => {
    const { wrapper } = await render(); await wrapper.setProps({ modelValue: ' 成都 ' })
    await wrapper.find('form').trigger('submit')
    expect(wrapper.emitted('submit')?.[0]).toEqual(['成都'])
    expect(JSON.parse(localStorage.getItem('travel-notes.search-history.v1')!)).toEqual(['成都'])
    await wrapper.setProps({ modelValue: '' }); await wrapper.find('input').trigger('focus')
    await wrapper.find('[data-test="clear-search-history"]').trigger('click')
    expect(localStorage.getItem('travel-notes.search-history.v1')).toBe('[]')
  })
  it('waits for IME composition and does not submit a composition Enter', async () => {
    const { wrapper } = await render(); await wrapper.find('input').trigger('compositionstart')
    await wrapper.setProps({ modelValue: '成都' }); await vi.advanceTimersByTimeAsync(300)
    await wrapper.find('input').trigger('keydown', { key: 'Enter', isComposing: true })
    expect(searchPublicContent).not.toHaveBeenCalled(); expect(wrapper.emitted('submit')).toBeUndefined()
    await wrapper.find('input').trigger('compositionend'); await vi.advanceTimersByTimeAsync(300); await flushPromises()
    expect(searchPublicContent).toHaveBeenCalledOnce()
  })
  it('ignores stale suggestions and uses the keyboard to open the current result', async () => {
    let resolve!: (value: SearchPage) => void
    vi.mocked(searchPublicContent).mockImplementationOnce(() => new Promise(done => { resolve = done })).mockResolvedValueOnce(result)
    const { wrapper, router } = await render(); await wrapper.find('input').trigger('focus')
    await wrapper.setProps({ modelValue: '旧' }); await vi.advanceTimersByTimeAsync(300)
    await wrapper.setProps({ modelValue: '成都' }); await vi.advanceTimersByTimeAsync(300); await flushPromises()
    resolve({ records: [], hasMore: false, nextCursor: null }); await flushPromises()
    expect(wrapper.find('[role="option"]').text()).toContain('成都')
    await wrapper.find('input').trigger('keydown', { key: 'ArrowDown' })
    await wrapper.find('input').trigger('keydown', { key: 'Enter' }); await flushPromises()
    expect(router.currentRoute.value.path).toBe('/destinations/1')
  })
  it('remains usable when browser storage is refused', async () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => { throw new Error('blocked') })
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new Error('blocked') })
    const { wrapper } = await render(); await wrapper.setProps({ modelValue: '杭州' })
    await wrapper.find('form').trigger('submit'); expect(wrapper.emitted('submit')?.[0]).toEqual(['杭州'])
  })
  it('ArrowUp from the input selects the final suggestion', async () => {
    vi.mocked(searchPublicContent).mockResolvedValue({ ...result, records: [...result.records, { ...result.records[0], id: '2', title: '杭州', href: '/destinations/2' }] })
    const { wrapper, router } = await render(); await wrapper.find('input').trigger('focus')
    await wrapper.setProps({ modelValue: '城' }); await vi.advanceTimersByTimeAsync(300); await flushPromises()
    await wrapper.find('input').trigger('keydown', { key: 'ArrowUp' }); await wrapper.find('input').trigger('keydown', { key: 'Enter' }); await flushPromises()
    expect(router.currentRoute.value.path).toBe('/destinations/2')
  })
  it('clearing the keyword also clears the submitted search', async () => {
    const { wrapper } = await render(); await wrapper.setProps({ modelValue: '成都' })
    await wrapper.find('[aria-label="清空关键词"]').trigger('click')
    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([''])
    expect(wrapper.emitted('submit')?.at(-1)).toEqual([''])
  })
})
