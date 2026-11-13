import { afterEach, describe, expect, it, vi } from 'vitest'
import { clearAiWorkspace, readAiWorkspace, saveAiWorkspace } from '@/utils/aiWorkspace'
import type { AiWorkspaceSnapshot } from '@/types/ai'

const snapshot: AiWorkspaceSnapshot = {
  requirements: { cityId: '1', startDate: '', endDate: '', travelers: 2, budgetAmount: '', budgetBasis: 'PER_PERSON', currency: 'CNY', interests: [], pace: 'BALANCED', transport: 'MIXED', preferredIds: ['26'], mustVisitIds: [], avoidIds: [] },
  draft: null, candidates: [], warnings: [], budget: null, cityName: '成都', clientRequestId: '', sourceGuideId: '4'
}
afterEach(() => { vi.restoreAllMocks(); sessionStorage.clear() })
describe('private AI workspace storage', () => {
  it('round trips incomplete form input within the same owner session', () => {
    expect(saveAiWorkspace('1', snapshot)).toBe(true)
    expect(readAiWorkspace('1')).toEqual(snapshot)
    expect(readAiWorkspace('2')).toBeNull()
    clearAiWorkspace(); expect(readAiWorkspace('1')).toBeNull()
  })
  it('rejects malformed saved structures without crashing', () => {
    for (const value of ['{broken', JSON.stringify({ ownerId: '1', at: Date.now(), snapshot: { ...snapshot, draft: { days: 'invalid' } } })]) {
      sessionStorage.setItem('travel-notes.ai-workspace.v1', value)
      expect(readAiWorkspace('1')).toBeNull()
    }
  })
  it('reports rejected writes while leaving the caller draft usable', () => {
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new Error('quota exceeded') })
    expect(saveAiWorkspace('1', snapshot)).toBe(false)
    expect(snapshot.requirements?.preferredIds).toEqual(['26'])
  })
  it('rejects expired browser snapshots', () => {
    sessionStorage.setItem('travel-notes.ai-workspace.v1', JSON.stringify({ ownerId: '1', at: Date.now() - 7 * 60 * 60 * 1000, snapshot }))
    expect(readAiWorkspace('1')).toBeNull()
  })
})
