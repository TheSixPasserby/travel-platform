import type { AiWorkspaceSnapshot } from '@/types/ai'

const KEY = 'travel-notes.ai-workspace.v1'
const MAX_BYTES = 2_000_000
const isRecord = (value: unknown): value is Record<string, unknown> => Boolean(value) && typeof value === 'object' && !Array.isArray(value)
const text = (value: unknown): value is string => typeof value === 'string' && value.length <= 20_000
const optionalText = (value: unknown): boolean => value === null || text(value)
const texts = (value: unknown, limit = 1000): value is string[] => Array.isArray(value) && value.length <= limit && value.every(text)
const fields = (value: Record<string, unknown>, keys: string[]): boolean => keys.every(key => text(value[key]))

function valid(snapshot: unknown): snapshot is AiWorkspaceSnapshot {
  if (!isRecord(snapshot) || !text(snapshot.cityName) || !text(snapshot.clientRequestId) || !optionalText(snapshot.sourceGuideId)
    || !texts(snapshot.warnings) || !Array.isArray(snapshot.candidates) || snapshot.candidates.length > 1000) return false
  const requirements = snapshot.requirements
  if (requirements !== null && (!isRecord(requirements)
    || !fields(requirements, ['cityId', 'startDate', 'endDate', 'budgetAmount'])
    || !Number.isFinite(requirements.travelers) || requirements.currency !== 'CNY'
    || !['PER_PERSON', 'TOTAL'].includes(String(requirements.budgetBasis))
    || !['RELAXED', 'BALANCED', 'COMPACT'].includes(String(requirements.pace))
    || !['PUBLIC', 'WALK', 'TAXI', 'MIXED'].includes(String(requirements.transport))
    || !texts(requirements.interests, 7) || !texts(requirements.preferredIds, 20)
    || !texts(requirements.mustVisitIds, 20) || !texts(requirements.avoidIds, 20))) return false
  const draft = snapshot.draft
  if (draft !== null && (!isRecord(draft) || !fields(draft, ['title', 'summary', 'notes']) || !Array.isArray(draft.days)
    || draft.days.length > 366 || !draft.days.every(day => isRecord(day) && Number.isInteger(day.dayNo)
      && fields(day, ['travelDate', 'title']) && Array.isArray(day.items) && day.items.length <= 100
      && day.items.every(item => isRecord(item) && ['SCENIC_SPOT', 'FREE_TIME'].includes(String(item.kind))
        && fields(item, ['placeName', 'startTime', 'endTime', 'notes', 'estimatedCost'])
        && optionalText(item.destinationId) && optionalText(item.addressText))))) return false
  if (!snapshot.candidates.every(candidate => isRecord(candidate) && fields(candidate, ['id', 'name'])
    && ['address', 'coverImageUrl', 'openingHours', 'ticketInfo', 'sourceUrl', 'verifiedAt'].every(key => optionalText(candidate[key]))
    && (candidate.recommendedDurationMinutes === null || Number.isFinite(candidate.recommendedDurationMinutes)))) return false
  return snapshot.budget === null || (isRecord(snapshot.budget) && fields(snapshot.budget, ['currency', 'perPerson', 'total', 'notes']))
}

export function clearAiWorkspace(): void { try { sessionStorage.removeItem(KEY) } catch { /* Memory remains usable. */ } }
export function readAiWorkspace(ownerId: string): AiWorkspaceSnapshot | null {
  if (!ownerId) return null
  try {
    const raw = sessionStorage.getItem(KEY)
    if (!raw || raw.length > MAX_BYTES) return null
    const value = JSON.parse(raw)
    if (!value || value.ownerId !== ownerId || !Number.isFinite(value.at)
      || value.at > Date.now() || Date.now() - value.at > 6 * 60 * 60 * 1000 || !valid(value.snapshot)) return null
    return value.snapshot
  } catch { return null }
}
export function saveAiWorkspace(ownerId: string, snapshot: AiWorkspaceSnapshot): boolean {
  if (!ownerId) return false
  try {
    const serialized = JSON.stringify({ ownerId, at: Date.now(), snapshot })
    if (serialized.length > MAX_BYTES) return false
    sessionStorage.setItem(KEY, serialized)
    return true
  } catch { return false }
}
