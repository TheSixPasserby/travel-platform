import { describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import GuideDetailView from '@/views/GuideDetailView.vue'
import { useAuthStore } from '@/stores/auth'
import type { GuideDetail } from '@/types/guide'

vi.mock('@/api/guides', () => ({ fetchGuide: vi.fn(async () => ({
  id: '4', title: '成都慢行', summary: null, coverImageUrl: null, author: { id: '2', nickname: '旅人', username: 'author', avatarUrl: null, bio: null },
  destination: { id: '1', name: '成都', status: 'ACTIVE' }, scenicSpots: [{ id: '26', name: '人民公园', status: 'ACTIVE' }],
  content: '正文', images: [], tags: [], status: 'PUBLISHED', publishedAt: '2026-11-01', createdAt: '2026-11-01', updatedAt: '2026-11-01',
  travelDays: null, budgetPerPerson: null, season: null, likeCount: 0, favoriteCount: 0, commentCount: 0
} satisfies GuideDetail)) }))

describe('guide trip entrypoints', () => {
  it('login return trip query keeps AI navigation when the drawer also emits close', async () => {
    const pinia = createPinia(); setActivePinia(pinia)
    const auth = useAuthStore(); auth.token = 'm16-test'
    auth.user = { id: '1', username: 'traveler', nickname: '旅人', avatarUrl: null, bio: null, role: 'USER', status: 'ACTIVE', createdAt: '' }
    const stub = { template: '<div />' }
    const router = createRouter({ history: createMemoryHistory(), routes: [
      { path: '/guides/:id', component: GuideDetailView }, { path: '/guides', name: 'guides', component: stub },
      { path: '/destinations/:id', name: 'destination-detail', component: stub }, { path: '/itineraries/ai', component: stub },
      { path: '/:pathMatch(.*)*', component: stub }
    ] })
    await router.push('/guides/4?trip=1'); await router.isReady()
    const wrapper = mount(GuideDetailView, { global: { plugins: [pinia, router], stubs: {
      RelatedGuides: true, GuideActions: true, CommentDrawer: true, SafeMarkdown: true,
      AddToTripDrawer: { props: ['open'], emits: ['plan-ai', 'close'], template: '<button v-if="open" data-test="plan" @click="$emit(\'plan-ai\', [\'26\']); $emit(\'close\')">规划</button>' }
    } } })
    await flushPromises(); await wrapper.get('[data-test="plan"]').trigger('click'); await flushPromises()
    expect(router.currentRoute.value.path).toBe('/itineraries/ai')
    wrapper.unmount()
  })
})
