import { afterEach, expect, it, vi } from 'vitest'
import { apiClient } from '@/api/client'
import { previewAiDay, previewAiItinerary } from '@/api/ai'
import type { AiRequirements } from '@/types/ai'

afterEach(() => vi.restoreAllMocks())

it('整程和单日预览等待时间覆盖管理员允许配置的连接与生成超时', async () => {
  const post = vi.spyOn(apiClient, 'post').mockResolvedValue({})
  const requirements = {} as AiRequirements
  await previewAiItinerary(requirements)
  await previewAiDay({ requirements, targetDate: '2026-11-20', usedDestinationIds: [] })
  for (const call of post.mock.calls) {
    expect(call[2]?.timeout).toBeGreaterThanOrEqual((30 + 90 + 5) * 1000)
  }
})
