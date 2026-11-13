import { beforeEach, describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { nextTick } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useTripIntentStore } from '@/stores/tripIntent'
import type { GuideDetail } from '@/types/guide'

const guide = { id: '4', title: '成都慢行', destination: { id: '1', name: '成都', status: 'ACTIVE' }, scenicSpots: [
  { id: '11', name: '公园', status: 'ACTIVE' }, { id: '12', name: '旧景点', status: 'INACTIVE' }
] } as GuideDetail
function login() {
  const auth = useAuthStore(); auth.token = 'session-a'
  auth.user = { id: '1', username: 'traveler', nickname: '旅人', avatarUrl: null, bio: null, role: 'USER', status: 'ACTIVE', createdAt: '' }
  return auth
}
beforeEach(() => { sessionStorage.clear(); setActivePinia(createPinia()) })
describe('trip inspiration ownership and persistence', () => {
  it('retains only explicitly selected active managed places', () => {
    login(); const store = useTripIntentStore()
    expect(store.select(guide, ['11'])).toBe(true)
    expect(store.source?.places).toEqual([{ id: '11', name: '公园' }])
    expect(store.select(guide, ['12', '99'])).toBe(false)
    expect(store.source?.places).toEqual([{ id: '11', name: '公园' }])
  })
  it('restores the source after refresh in the same authenticated session', async () => {
    login(); useTripIntentStore().select(guide, ['11'])
    setActivePinia(createPinia()); login()
    expect(useTripIntentStore().source?.guideId).toBe('4')
  })
  it('clears inspiration on account changes and logout', async () => {
    const auth = login(); const store = useTripIntentStore(); store.select(guide, ['11'])
    auth.token = 'session-b'; await nextTick()
    expect(store.source).toBeNull()
    auth.token = null; auth.user = null; await nextTick()
    expect(sessionStorage.getItem('travel-notes.trip-inspiration.v1')).toBeNull()
  })
  it('ignores another owner and malformed persisted input', () => {
    sessionStorage.setItem('travel-notes.trip-inspiration.v1', JSON.stringify({ ownerId: 'someone-else', places: [] }))
    login(); expect(useTripIntentStore().source).toBeNull()
  })
})
