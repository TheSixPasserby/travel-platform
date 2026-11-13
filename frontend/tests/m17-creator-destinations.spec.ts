import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import CreatorView from '@/views/CreatorView.vue'
import { fetchCreator, fetchCreatorDestinations, fetchCreatorFeed } from '@/api/creators'
import { clearFeedCache } from '@/composables/useInfiniteFeed'
import type { PageResult } from '@/types/api'
import type { CreatorDestination } from '@/types/creator'

vi.mock('@/api/creators', () => ({
  fetchCreator: vi.fn(),
  fetchCreatorGuides: vi.fn(),
  fetchCreatorFeed: vi.fn(),
  fetchCreatorDestinations: vi.fn()
}))

const wrappers: VueWrapper[] = []
afterEach(() => {
  wrappers.splice(0).forEach((w) => w.unmount())
  vi.resetAllMocks()
  clearFeedCache()
})

const profile = (username = 'walker') => ({
  id: '12',
  username,
  nickname: '山野旅人',
  avatarUrl: null,
  bio: '沿途记录',
  publishedGuideCount: 1,
  likeCount: 0,
  favoriteCount: 0
})

function destination(
  index: number,
  overrides: Partial<CreatorDestination> = {}
): CreatorDestination {
  return {
    id: 'dest-' + index,
    name: '目的地' + index,
    destinationType: 'CITY',
    coverImageUrl: null,
    guideCount: index,
    ...overrides
  }
}

function page(
  records: CreatorDestination[],
  options: { current?: number; pages?: number; total?: number } = {}
): PageResult<CreatorDestination> {
  const current = options.current ?? 1
  const pages = options.pages ?? 1
  return {
    records,
    total: options.total ?? records.length,
    current,
    size: 10,
    pages
  }
}

const firstPage = () =>
  page(
    [
      destination(1, {
        name: '西湖',
        destinationType: 'CITY',
        coverImageUrl: '/uploads/west-lake.jpg',
        guideCount: 3
      }),
      destination(2, {
        name: '无名小径',
        destinationType: 'TRAIL',
        coverImageUrl: null,
        guideCount: 1
      })
    ],
    { current: 1, pages: 2, total: 12 }
  )

const secondPage = () =>
  page([destination(9, { name: '灵隐寺', guideCount: 2 })], {
    current: 2,
    pages: 2,
    total: 12
  })

async function render(path = '/users/walker') {
  const pinia = createPinia()
  setActivePinia(pinia)
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/users/:username', component: CreatorView },
      { path: '/me', component: { template: '<p>我的资料</p>' } },
      { path: '/guides/:id', name: 'guide-detail', component: { template: '<p>攻略</p>' } }
    ]
  })
  await router.push(path)
  await router.isReady()
  const w = mount(CreatorView, {
    global: {
      plugins: [pinia, router],
      stubs: {
        GuideCard: { template: '<article data-test="public-note">公开手记</article>' }
      }
    }
  })
  wrappers.push(w)
  await flushPromises()
  return { w, router }
}

beforeEach(() => {
  vi.mocked(fetchCreator).mockImplementation(async (username) => profile(username))
  vi.mocked(fetchCreatorFeed).mockResolvedValue({
    records: [],
    nextCursor: null,
    hasMore: false
  })
  vi.mocked(fetchCreatorDestinations).mockResolvedValue(firstPage())
})

describe('创作者页 · 手记中的目的地', () => {
  it('挂载后按第一页 size 10 请求并渲染列表、类型与手记数', async () => {
    const { w } = await render()
    expect(fetchCreatorDestinations).toHaveBeenCalledWith('walker', { page: 1, size: 10 })
    const section = w.get('[data-test="creator-destinations"]')
    const items = section.findAll('[data-test="destination-item"]')
    expect(items).toHaveLength(2)
    expect(items[0].text()).toContain('西湖')
    expect(items[0].text()).toContain('城市')
    expect(items[0].text()).toContain('3 篇手记')
    // 未知类型原样显示
    expect(items[1].text()).toContain('TRAIL')
    expect(items[1].text()).toContain('1 篇手记')
    // 有封面才渲染 img，缺失封面走占位插画
    const covers = section.findAll('[data-test="destination-item"] img')
    expect(covers).toHaveLength(1)
    expect(covers[0].attributes('alt')).toBe('西湖')
  })

  it('标注为「手记中的目的地」并说明不代表真实到访', async () => {
    const { w } = await render()
    const text = w.get('[data-test="creator-destinations"]').text()
    expect(text).toContain('手记中的目的地')
    expect(text).toContain('不代表真实到访')
  })

  it('点击下一页请求 page=2 并渲染新数据，loading 时禁用翻页', async () => {
    const { w } = await render()
    let release: (value: PageResult<CreatorDestination>) => void = () => {}
    vi.mocked(fetchCreatorDestinations).mockImplementationOnce(
      () => new Promise<PageResult<CreatorDestination>>((resolve) => { release = resolve })
    )
    await w.get('[data-test="destinations-next"]').trigger('click')
    expect(w.get('[data-test="destinations-next"]').attributes('disabled')).toBeDefined()
    expect(w.get('[data-test="destinations-prev"]').attributes('disabled')).toBeDefined()
    release(secondPage())
    await flushPromises()
    expect(fetchCreatorDestinations).toHaveBeenLastCalledWith('walker', { page: 2, size: 10 })
    expect(w.findAll('[data-test="destination-item"]')).toHaveLength(1)
    expect(w.get('[data-test="creator-destinations"]').text()).toContain('灵隐寺')
    expect(w.get('[data-test="destinations-page-status"]').text()).toContain('2 / 2')
  })

  it('只有一页时不渲染翻页控件', async () => {
    vi.mocked(fetchCreatorDestinations).mockResolvedValue(
      page([destination(1, { name: '西湖' })], { current: 1, pages: 1, total: 1 })
    )
    const { w } = await render()
    expect(w.find('[data-test="destinations-next"]').exists()).toBe(false)
    expect(w.find('[data-test="destinations-prev"]').exists()).toBe(false)
  })

  it('失败时显示错误与重试，重试会真正重新请求并渲染', async () => {
    vi.mocked(fetchCreatorDestinations)
      .mockRejectedValueOnce(new Error('offline'))
      .mockResolvedValueOnce(firstPage())
    const { w } = await render()
    expect(fetchCreatorDestinations).toHaveBeenCalledTimes(1)
    const alert = w.get('[data-test="creator-destinations"] [role="alert"]')
    expect(alert.text()).toContain('目的地加载失败')
    expect(w.find('[data-test="destination-item"]').exists()).toBe(false)
    await w.get('[data-test="destinations-retry"]').trigger('click')
    await flushPromises()
    expect(fetchCreatorDestinations).toHaveBeenCalledTimes(2)
    expect(fetchCreatorDestinations).toHaveBeenLastCalledWith('walker', { page: 1, size: 10 })
    expect(w.findAll('[data-test="destination-item"]')).toHaveLength(2)
  })

  it('空态提示旅人还没有标记目的地', async () => {
    vi.mocked(fetchCreatorDestinations).mockResolvedValue(
      page([], { current: 1, pages: 0, total: 0 })
    )
    const { w } = await render()
    const empty = w.get('[data-test="destinations-empty"]')
    expect(empty.text()).toContain('还没有在公开手记里标记目的地')
    expect(w.find('[data-test="destination-item"]').exists()).toBe(false)
  })

  it('切换作者时重置到第一页并清空旧数据', async () => {
    vi.mocked(fetchCreatorDestinations).mockImplementation(async (username, params) => {
      if (username === 'newwalker') {
        return page([destination(7, { name: '新作者的目的地' })], {
          current: 1,
          pages: 1,
          total: 1
        })
      }
      return params.page === 2 ? secondPage() : firstPage()
    })
    const { w, router } = await render()
    await w.get('[data-test="destinations-next"]').trigger('click')
    await flushPromises()
    expect(w.get('[data-test="creator-destinations"]').text()).toContain('灵隐寺')

    await router.push('/users/newwalker')
    await flushPromises()
    expect(fetchCreatorDestinations).toHaveBeenLastCalledWith('newwalker', { page: 1, size: 10 })
    const text = w.get('[data-test="creator-destinations"]').text()
    expect(text).toContain('新作者的目的地')
    expect(text).not.toContain('灵隐寺')
    expect(w.findAll('[data-test="destination-item"]')).toHaveLength(1)
    expect(w.text()).toContain('@newwalker')
  })

  it('切换作者后迟到的旧目的地响应不覆盖新作者', async () => {
    let releaseOld: (value: PageResult<CreatorDestination>) => void = () => {}
    vi.mocked(fetchCreatorDestinations).mockImplementation((username) => {
      if (username === 'walker') {
        return new Promise<PageResult<CreatorDestination>>((resolve) => {
          releaseOld = resolve
        })
      }
      return Promise.resolve(
        page([destination(7, { name: '新作者的目的地' })], { current: 1, pages: 1, total: 1 })
      )
    })
    const { w, router } = await render()
    await router.push('/users/newwalker')
    await flushPromises()
    releaseOld(firstPage())
    await flushPromises()
    const text = w.get('[data-test="creator-destinations"]').text()
    expect(text).toContain('新作者的目的地')
    expect(text).not.toContain('西湖')
  })
})
