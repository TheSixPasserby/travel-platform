import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import AiItineraryForm from '@/components/AiItineraryForm.vue'
import { fetchDestinations } from '@/api/destinations'
import type { TripInspiration } from '@/types/tripIntent'
import type { Destination } from '@/types/destination'

vi.mock('@/api/destinations', () => ({ fetchDestinations: vi.fn() }))
const inspiration: TripInspiration = { ownerId: '1', guideId: '4', guideTitle: '成都慢行', cityId: '1', cityName: '成都', places: [{ id: '11', name: '人民公园' }] }
const wrappers: Array<ReturnType<typeof mount>> = []
async function render(source: TripInspiration | null) {
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/', component: { template: '<div />' } }] }); await router.push('/'); await router.isReady()
  const wrapper = mount(AiItineraryForm, { props: { inspiration: source }, global: { plugins: [router] } }); wrappers.push(wrapper); await flushPromises(); return wrapper
}
beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(fetchDestinations).mockImplementation(async query => {
    const records = query?.destinationType === 'CITY' ? [{ id: '1', name: '成都' }, { id: '2', name: '杭州' }] : [{ id: '11', name: '人民公园', status: 'ACTIVE' }]
    return { records: records as Destination[], current: 1, size: 20, total: records.length, pages: 1 }
  })
})
afterEach(() => wrappers.splice(0).forEach(wrapper => wrapper.unmount()))
describe('AI form inspiration', () => {
  it('prefills real source IDs and leaves must-visit choices editable', async () => {
    const wrapper = await render(inspiration)
    expect((wrapper.get('[data-test="ai-city"]').element as HTMLSelectElement).value).toBe('1')
    expect(wrapper.emitted('dirty')?.at(-1)?.[0]).toMatchObject({ cityId: '1', preferredIds: ['11'], mustVisitIds: [] })
    expect(wrapper.text()).toContain('人民公园')
  })
  it('applies a later source only after the user confirms', async () => {
    const wrapper = await render(inspiration)
    await wrapper.get('[data-test="ai-city"]').setValue('2')
    await wrapper.setProps({ inspiration: { ...inspiration, guideId: '5' } }); await flushPromises()
    expect((wrapper.get('[data-test="ai-city"]').element as HTMLSelectElement).value).toBe('2')
    await wrapper.get('[data-test="ai-inspiration-apply"]').trigger('click'); await flushPromises()
    expect((wrapper.get('[data-test="ai-city"]').element as HTMLSelectElement).value).toBe('1')
  })
  it('preserves the original empty form when no source is selected', async () => {
    const wrapper = await render(null)
    expect(wrapper.find('[data-test="ai-inspiration"]').exists()).toBe(false)
    expect((wrapper.get('[data-test="ai-city"]').element as HTMLSelectElement).value).toBe('')
  })
  it('offers explicit application when the same guide has different selected places', async () => {
    const wrapper = await render(inspiration)
    await wrapper.setProps({ inspiration: { ...inspiration, places: [{ id: '12', name: '宽窄巷子' }] } })
    expect(wrapper.find('[data-test="ai-inspiration-apply"]').exists()).toBe(true)
  })
})
