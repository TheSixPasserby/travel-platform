import { ref, watch } from 'vue'
import { defineStore } from 'pinia'
import { useAuthStore } from './auth'
import type { GuideDetail } from '@/types/guide'
import type { TripInspiration } from '@/types/tripIntent'

const KEY = 'travel-notes.trip-inspiration.v1'
const MAX_AGE = 30 * 60 * 1000
const isId = (value: unknown): value is string => typeof value === 'string' && /^[1-9]\d{0,18}$/.test(value)
function valid(value: unknown): value is TripInspiration {
  if (!value || typeof value !== 'object') return false
  const item = value as TripInspiration
  return isId(item.ownerId) && isId(item.guideId) && isId(item.cityId)
    && typeof item.guideTitle === 'string' && item.guideTitle.length <= 180
    && typeof item.cityName === 'string' && item.cityName.length <= 120
    && Array.isArray(item.places) && item.places.length > 0 && item.places.length <= 20
    && item.places.every(place => place && isId(place.id) && typeof place.name === 'string' && place.name.length <= 120)
}

/** Public source IDs stay in this browser session, scoped to the authenticated owner. */
export const useTripIntentStore = defineStore('trip-inspiration', () => {
  const auth = useAuthStore()
  const source = ref<TripInspiration | null>(null)
  function clear(): void {
    source.value = null
    try { sessionStorage.removeItem(KEY) } catch { /* In-memory use remains available. */ }
  }
  function restore(): void {
    if (!auth.user || !auth.token) return
    try {
      const saved = JSON.parse(sessionStorage.getItem(KEY) || 'null')
      if (saved && valid(saved.source) && saved.source.ownerId === auth.user.id
        && typeof saved.at === 'number' && saved.at <= Date.now() && Date.now() - saved.at < MAX_AGE) {
        source.value = saved.source
      } else clear()
    } catch { clear() }
  }
  function select(guide: GuideDetail, ids: string[]): boolean {
    if (!auth.user || !auth.token || !guide.destination || guide.destination.status !== 'ACTIVE'
      || !ids.length || ids.length > 20 || new Set(ids).size !== ids.length) return false
    const places = guide.scenicSpots.filter(place => place.status === 'ACTIVE' && ids.includes(place.id))
    if (places.length !== ids.length) return false
    source.value = {
      ownerId: auth.user.id, guideId: guide.id, guideTitle: guide.title,
      cityId: guide.destination.id, cityName: guide.destination.name,
      places: places.map(place => ({ id: place.id, name: place.name }))
    }
    try { sessionStorage.setItem(KEY, JSON.stringify({ source: source.value, at: Date.now() })) } catch { /* Keep the confirmed source in memory. */ }
    return true
  }
  watch(() => auth.token, (token, previous) => {
    if (!token || (previous && token !== previous)) clear()
  })
  watch(() => auth.user?.id, (owner, previous) => {
    if (previous && owner !== previous) clear()
    if (owner) restore()
  }, { immediate: true })
  return { source, select, clear }
})
