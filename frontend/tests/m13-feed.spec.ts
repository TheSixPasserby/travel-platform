import { createPinia } from 'pinia'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { clearFeedCache, useInfiniteFeed, type FeedPage } from '@/composables/useInfiniteFeed'
import GuideFeed from '@/components/GuideFeed.vue'
import { fetchGuideFeed, fetchTags } from '@/api/guides'
import { fetchDestinations } from '@/api/destinations'
import type { GuideFeedPage, GuideSummary } from '@/types/guide'

vi.mock('@/api/guides', () => ({ fetchGuideFeed: vi.fn(), fetchTags: vi.fn() }))
vi.mock('@/api/destinations', () => ({ fetchDestinations: vi.fn(), fetchDestination: vi.fn() }))

interface Item {
  id: string
}

function pageOf(ids: string[], nextCursor: string | null, hasMore: boolean): FeedPage<Item> {
  return { records: ids.map(id => ({ id })), nextCursor, hasMore }
}

function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>(done => {
    resolve = done
  })
  return { promise, resolve }
}

afterEach(() => {
  clearFeedCache()
  vi.resetAllMocks()
})

describe('游标连续流引擎', () => {
  it('追加下一页并按 ID 去重', async () => {
    const fetchPage = vi
      .fn<(cursor: string | undefined) => Promise<FeedPage<Item>>>()
      .mockResolvedValueOnce(pageOf(['a', 'b'], 'c2', true))
      .mockResolvedValueOnce(pageOf(['b', 'c'], null, false))
    const feed = useInfiniteFeed<Item>({ fetchPage, keyOf: item => item.id })

    await feed.loadFirst()
    expect(feed.items.value.map(item => item.id)).toEqual(['a', 'b'])
    expect(feed.hasMore.value).toBe(true)

    await feed.loadMore()
    expect(fetchPage).toHaveBeenLastCalledWith('c2', expect.anything())
    expect(feed.items.value.map(item => item.id)).toEqual(['a', 'b', 'c'])
    expect(feed.hasMore.value).toBe(false)
    expect(feed.exhausted.value).toBe(true)
  })

  it('达到 DOM 上限后停止追加', async () => {
    const fetchPage = vi
      .fn<(cursor: string | undefined) => Promise<FeedPage<Item>>>()
      .mockResolvedValueOnce(pageOf(['a', 'b'], 'c2', true))
    const feed = useInfiniteFeed<Item>({ fetchPage, keyOf: item => item.id, maxItems: 2 })

    await feed.loadFirst()
    expect(feed.limitReached.value).toBe(true)
    expect(feed.hasMore.value).toBe(false)
    await feed.loadMore()
    expect(fetchPage).toHaveBeenCalledTimes(1)
  })

  it('竞态只接受最新一次首批结果', async () => {
    const stale = deferred<FeedPage<Item>>()
    const fetchPage = vi
      .fn<(cursor: string | undefined) => Promise<FeedPage<Item>>>()
      .mockReturnValueOnce(stale.promise)
      .mockResolvedValueOnce(pageOf(['fresh'], null, false))
    const feed = useInfiniteFeed<Item>({ fetchPage, keyOf: item => item.id })

    const first = feed.loadFirst()
    const second = feed.loadFirst(true)
    await second
    stale.resolve(pageOf(['stale'], null, false))
    await first

    expect(feed.items.value.map(item => item.id)).toEqual(['fresh'])
    expect(feed.loading.value).toBe(false)
  })

  it('追加失败保留旧列表并可重试', async () => {
    const fetchPage = vi
      .fn<(cursor: string | undefined) => Promise<FeedPage<Item>>>()
      .mockResolvedValueOnce(pageOf(['a', 'b'], 'c2', true))
      .mockRejectedValueOnce(new Error('boom'))
      .mockResolvedValueOnce(pageOf(['c'], null, false))
    const feed = useInfiniteFeed<Item>({ fetchPage, keyOf: item => item.id })

    await feed.loadFirst()
    await feed.loadMore()
    expect(feed.items.value.map(item => item.id)).toEqual(['a', 'b'])
    expect(feed.moreError.value).toBe('更多攻略暂时加载失败，请重试')
    expect(feed.loadingMore.value).toBe(false)

    await feed.retry()
    expect(feed.moreError.value).toBe('')
    expect(fetchPage).toHaveBeenCalledTimes(3)
    expect(feed.items.value.map(item => item.id)).toEqual(['a', 'b', 'c'])
  })
})

function note(id: string, title: string): GuideSummary {
  return {
    id,
    title,
    summary: '沿街走走',
    coverImageUrl: null,
    author: { id: '12', nickname: '林间旅人', username: 'walker', avatarUrl: null, bio: null },
    destination: { id: '1', name: '成都', status: 'ACTIVE' },
    tags: [],
    travelDays: null,
    budgetPerPerson: null,
    season: null,
    likeCount: 2,
    favoriteCount: 1,
    commentCount: 0,
    status: 'PUBLISHED',
    publishedAt: '2026-11-01',
    createdAt: '2026-11-01',
    updatedAt: '2026-11-01'
  } as GuideSummary
}

function feedPage(records: GuideSummary[], nextCursor: string | null, hasMore: boolean): GuideFeedPage {
  return { records, nextCursor, hasMore }
}

describe('攻略连续流视图', () => {
  const wrappers: VueWrapper[] = []
  afterEach(() => {
    wrappers.splice(0).forEach(wrapper => wrapper.unmount())
  })

  it('追加不重复、无翻页器、结束文案为已经看完', async () => {
    vi.mocked(fetchTags).mockResolvedValue([{ id: '7', name: '慢旅行' } as never])
    vi.mocked(fetchDestinations).mockResolvedValue({
      records: [{ id: '1', name: '成都' }] as never[],
      total: 1,
      current: 1,
      size: 8,
      pages: 1
    })
    vi.mocked(fetchGuideFeed)
      .mockResolvedValueOnce(feedPage([note('4', '成都两日慢行')], 'c2', true))
      .mockResolvedValueOnce(feedPage([note('4', '成都两日慢行'), note('5', '青城山半日')], 'c3', true))
      .mockResolvedValueOnce(feedPage([note('4', '成都两日慢行'), note('6', '山谷里的清晨')], null, false))

    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/guides', component: GuideFeed },
        { path: '/guides/new', name: 'guide-new', component: { template: '<p>编辑</p>' } },
        { path: '/guides/:id', name: 'guide-detail', component: { template: '<p>攻略</p>' } },
        { path: '/users/:username', component: { template: '<p>作者</p>' } }
      ]
    })
    await router.push('/guides')
    await router.isReady()
    const wrapper = mount(GuideFeed, { global: { plugins: [createPinia(), router] } })
    wrappers.push(wrapper)
    await flushPromises()

    expect(wrapper.findAll('[data-test="guide-card"]').length).toBe(1)
    expect(wrapper.find('[data-test="load-more"]').exists()).toBe(true)

    await wrapper.get('[data-test="load-more"]').trigger('click')
    await flushPromises()
    expect(wrapper.findAll('[data-test="guide-card"]').length).toBe(2)

    await wrapper.get('[data-test="load-more"]').trigger('click')
    await flushPromises()
    const cards = wrapper.findAll('[data-test="guide-card"]')
    expect(cards.length).toBe(3)
    expect(new Set(cards.map(card => card.attributes('href'))).size).toBe(3)
    expect(wrapper.text()).not.toContain('上一页')
    expect(wrapper.text()).not.toContain('下一页')
    expect(wrapper.text()).toContain('已经看完')
    expect(fetchGuideFeed).toHaveBeenNthCalledWith(
      2,
      expect.objectContaining({ cursor: 'c2' }),
      expect.anything()
    )
  })
})
