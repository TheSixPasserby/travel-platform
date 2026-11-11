import { afterEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import GuideGrid from '@/components/GuideGrid.vue'
import GuideCard from '@/components/GuideCard.vue'
import GuideFeed from '@/components/GuideFeed.vue'
import CommentDrawer from '@/components/CommentDrawer.vue'
import { clearFeedCache, useInfiniteFeed } from '@/composables/useInfiniteFeed'
import { fetchGuideFeed, fetchTags } from '@/api/guides'
import { useAuthStore } from '@/stores/auth'
vi.mock('@/api/guides', () => ({ fetchGuideFeed: vi.fn(), fetchTags: vi.fn() }))

afterEach(() => {
  clearFeedCache()
  vi.useRealTimers()
  vi.unstubAllGlobals()
  vi.resetAllMocks()
})

describe('M13 集成回归', () => {
  it('追加失败点击重试仍从原游标继续，保留首批卡片', async () => {
    const record = {
      id: '1',
      title: '山间',
      author: { id: '1', nickname: '旅人' },
      tags: [],
      budgetPerPerson: null
    } as never
    vi.mocked(fetchTags).mockResolvedValue([])
    vi.mocked(fetchGuideFeed)
      .mockResolvedValueOnce({
        records: [record],
        nextCursor: 'next',
        hasMore: true
      })
      .mockRejectedValueOnce(new Error('offline'))
      .mockResolvedValueOnce({
        records: [{ ...(record as object), id: '2' } as never],
        nextCursor: null,
        hasMore: false
      })
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/guides', component: GuideFeed },
        {
          path: '/guides/:id',
          name: 'guide-detail',
          component: { template: '<div />' }
        }
      ]
    })
    await router.push('/guides')
    const wrapper = mount(GuideFeed, {
      global: {
        plugins: [createPinia(), router],
        stubs: { DestinationPicker: true }
      }
    })
    await flushPromises()
    await wrapper.get('[data-test="load-more"]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-test="retry-more"]').trigger('click')
    await flushPromises()
    expect(fetchGuideFeed).toHaveBeenLastCalledWith(
      expect.objectContaining({ cursor: 'next' }),
      expect.anything()
    )
    expect(wrapper.findAll('[data-test="guide-card"]')).toHaveLength(2)
    wrapper.unmount()
  })

  it('窗口化移除焦点卡片前将焦点交给当前列表', async () => {
    const records = Array.from({ length: 200 }, (_, index) => ({
      id: String(index + 1),
      title: '山水间',
      tags: [],
      budgetPerPerson: null
    })) as never[]
    const wrapper = mount(GuideGrid, {
      attachTo: document.body,
      props: { guides: records },
      global: {
        stubs: {
          GuideCard: {
            props: ['guide'],
            template: '<article><button>阅读</button></article>'
          }
        }
      }
    })
    await flushPromises()
    await wrapper.get('button').trigger('focus')
    ;(wrapper.get('button').element as HTMLElement).focus()
    vi.spyOn(wrapper.element, 'getBoundingClientRect').mockReturnValue({
      top: -100000,
      left: 0,
      width: 1000,
      height: 10000,
      bottom: 0,
      right: 1000,
      x: 0,
      y: -100000,
      toJSON: () => ({})
    })
    window.dispatchEvent(new Event('scroll'))
    await flushPromises()
    expect(document.activeElement).toBe(wrapper.element)
    wrapper.unmount()
  })

  it('异步首批渲染后观察实际哨兵，切换账号丢弃旧请求', async () => {
    const observe = vi.fn()
    vi.stubGlobal(
      'IntersectionObserver',
      class {
        observe = observe
        disconnect = vi.fn()
      }
    )
    vi.mocked(fetchTags).mockResolvedValue([])
    vi.mocked(fetchGuideFeed).mockResolvedValue({
      records: [
        {
          id: '1',
          title: '山间',
          author: { id: '1', nickname: '旅人' },
          tags: [],
          budgetPerPerson: null
        } as never
      ],
      nextCursor: 'next',
      hasMore: true
    })
    const pinia = createPinia()
    setActivePinia(pinia)
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/guides', component: GuideFeed },
        {
          path: '/guides/new',
          name: 'guide-new',
          component: { template: '<div />' }
        },
        {
          path: '/guides/:id',
          name: 'guide-detail',
          component: { template: '<div />' }
        }
      ]
    })
    await router.push('/guides')
    const wrapper = mount(GuideFeed, {
      global: { plugins: [pinia, router], stubs: { DestinationPicker: true } }
    })
    await flushPromises()
    expect(observe).toHaveBeenCalledWith(wrapper.get('.feed-sentinel').element)
    useAuthStore().token = 'a-new-session'
    await flushPromises()
    expect(fetchGuideFeed).toHaveBeenCalledTimes(2)
    wrapper.unmount()
  })

  it('重复记录组成的一批仍保留后续游标，后续唯一文章可继续读取', async () => {
    const fetchPage = vi
      .fn()
      .mockResolvedValueOnce({
        records: [{ id: '1' }],
        nextCursor: 'a',
        hasMore: true
      })
      .mockResolvedValueOnce({
        records: [{ id: '1' }],
        nextCursor: 'b',
        hasMore: true
      })
      .mockResolvedValueOnce({
        records: [{ id: '2' }],
        nextCursor: null,
        hasMore: false
      })
    const feed = useInfiniteFeed({
      fetchPage,
      keyOf: (item: { id: string }) => item.id
    })
    await feed.loadFirst()
    await feed.loadMore()
    expect(feed.hasMore.value).toBe(true)
    await feed.loadMore()
    expect(feed.items.value.map((item) => item.id)).toEqual(['1', '2'])
    feed.dispose()
  })

  it('评论抽屉关闭动画完成后仍保留未提交草稿', async () => {
    vi.useFakeTimers()
    const wrapper = mount(CommentDrawer, {
      props: { open: true, guideId: '1' },
      global: {
        stubs: {
          GuideComments: {
            data: () => ({ draft: '' }),
            template: '<textarea v-model="draft" />'
          }
        }
      }
    })
    await wrapper.get('textarea').setValue('返回阅读后继续写')
    await wrapper.setProps({ open: false })
    await vi.advanceTimersByTimeAsync(300)
    await wrapper.setProps({ open: true })
    expect((wrapper.get('textarea').element as HTMLTextAreaElement).value).toBe(
      '返回阅读后继续写'
    )
    wrapper.unmount()
  })
})

it('攻略封面加载后淡入，更换图片重新进入加载态', async () => {
  const guide = {
    id: '1',
    title: '山水间',
    author: { id: '1', nickname: '旅人' },
    tags: [],
    budgetPerPerson: null,
    coverImageUrl: '/uploads/2026/11/00000000000000000000000000000001.jpg'
  } as never
  const wrapper = mount(GuideCard, {
    props: { guide },
    global: { stubs: { RouterLink: { template: '<a><slot /></a>' } } }
  })
  expect(wrapper.get('img').classes()).not.toContain('is-loaded')
  await wrapper.get('img').trigger('load')
  expect(wrapper.get('img').classes()).toContain('is-loaded')
  await wrapper.setProps({
    guide: {
      ...(guide as object),
      coverImageUrl: '/uploads/2026/11/00000000000000000000000000000002.jpg'
    } as never
  })
  expect(wrapper.get('img').classes()).not.toContain('is-loaded')
  wrapper.unmount()
})
