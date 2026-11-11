import type { PageResult } from '../../../frontend/src/types/api.ts'
import type { GuideDetail, GuideQuery, GuideSummary } from '../../../frontend/src/types/guide.ts'
import type { Destination, DestinationQuery } from '../../../frontend/src/types/destination.ts'
import type { GuideInteraction } from '../../../frontend/src/types/interaction.ts'
import type { ItineraryDetail, ItinerarySummary } from '../../../frontend/src/types/itinerary.ts'
import { request } from '../platform/request.ts'

export const fetchGuides = (query: GuideQuery) => request<PageResult<GuideSummary>>('/guides', { query, auth: false })
export const fetchGuide = (id: string) => request<GuideDetail>(`/guides/${id}`, { auth: false })
export const fetchDestinations = (query: DestinationQuery) => request<PageResult<Destination>>('/destinations', { query, auth: false })
export const fetchDestination = (id: string) => request<Destination>(`/destinations/${id}`, { auth: false })
export const fetchFavorites = (page: number) => request<PageResult<GuideSummary>>('/users/me/favorites', { query: { page, size: 10 } })
export const fetchInteraction = (id: string) => request<GuideInteraction>(`/guides/${id}/interaction`)
export const setFavorite = (id: string, enabled: boolean) => request<GuideInteraction>(`/guides/${id}/favorite`, { method: enabled ? 'PUT' : 'DELETE' })
export const fetchItineraries = (page: number) => request<PageResult<ItinerarySummary>>('/itineraries', { query: { page, size: 10 } })
export const fetchItinerary = (id: string) => request<ItineraryDetail>(`/itineraries/${id}`)
