import type { ItineraryDestination } from './itinerary'

export type AiInterest =
  | 'FOOD'
  | 'NATURE'
  | 'CULTURE'
  | 'PHOTOGRAPHY'
  | 'FAMILY'
  | 'HIKING'
  | 'CITY_WALK'
export interface AiRequirements {
  cityId: string
  startDate: string
  endDate: string
  travelers: number
  budgetAmount: string
  budgetBasis: 'PER_PERSON' | 'TOTAL'
  currency: 'CNY'
  interests: AiInterest[]
  pace: 'RELAXED' | 'BALANCED' | 'COMPACT'
  transport: 'PUBLIC' | 'WALK' | 'TAXI' | 'MIXED'
  preferredIds: string[]
  mustVisitIds: string[]
  avoidIds: string[]
}
export interface AiCapabilities {
  enabled: boolean
  available: boolean
  reason: string | null
  maxDays: number
  provider: string
  providerLabel: string
}
export interface AiCandidate {
  id: string
  name: string
  address: string | null
  coverImageUrl: string | null
  recommendedDurationMinutes: number | null
  openingHours: string | null
  ticketInfo: string | null
  sourceUrl: string | null
  verifiedAt: string | null
}
export interface AiItem {
  kind: 'SCENIC_SPOT' | 'FREE_TIME'
  destinationId: string | null
  placeName: string
  addressText: string | null
  startTime: string
  endTime: string
  notes: string | null
  estimatedCost: string
}
export interface AiDay {
  dayNo: number
  travelDate: string
  title: string
  items: AiItem[]
}
export interface AiBudgetEstimate {
  currency: 'CNY'
  perPerson: string
  total: string
  notes: string
}
export interface AiPreview {
  title: string
  summary: string
  city: ItineraryDestination
  startDate: string
  endDate: string
  travelers: number
  budgetAmount: string
  budgetBasis: 'PER_PERSON' | 'TOTAL'
  currency: 'CNY'
  days: AiDay[]
  budgetEstimate: AiBudgetEstimate
  warnings: string[]
  candidates: AiCandidate[]
  sourceGuides: { id: string; title: string }[]
}
export interface AiDayRequest {
  requirements: AiRequirements
  targetDate: string
  usedDestinationIds: string[]
}
export interface AiDayPreview {
  day: AiDay
  warnings: string[]
}

export interface AiDraftItem extends Omit<AiItem, 'notes'> {
  notes: string
}
export interface AiDraftDay {
  dayNo: number
  travelDate: string
  title: string
  items: AiDraftItem[]
}
export interface AiDraft {
  title: string
  summary: string
  notes: string
  days: AiDraftDay[]
}
