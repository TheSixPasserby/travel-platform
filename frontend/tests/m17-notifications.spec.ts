import { afterEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'

import NotificationBell from '@/components/NotificationBell.vue'
import NotificationsView from '@/views/NotificationsView.vue'
import {
  fetchNotificationUnreadCount,
  fetchNotifications,
  markAllNotificationsRead,
  markNotificationRead
} from '@/api/notifications'
import { ApiError, apiClient } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import type { PageResult } from '@/types/api'
import type { CurrentUser } from '@/types/auth'
import type { AppNotification, NotificationQuery, NotificationUnreadCount } from '@/types/notification'

vi.mock('@/api/notifications', () => ({
  fetchNotifications: vi.fn(),
  fetchNotificationUnreadCount: vi.fn(),
  markNotificationRead: vi.fn(),
  markAllNotificationsRead: vi.fn()
}))

vi.mock('@/api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/client')>()
  return { ...actual, apiClient: { get: vi.fn(), put: vi.fn() } }
})

const user: CurrentUser = {
  id: '1', username: 'luna', nickname: '小鹿', avatarUrl: null, bio: null,
  role: 'USER', status: 'ACTIVE', createdAt: '2026-11-02T10:00:00.000+08:00'
}
const otherUser: CurrentUser = { ...user, id: '2', username: 'kai', nickname: '阿凯' }

const actor = { id: '2', nickname: '阿凯', username: 'kai', avatarUrl: null, bio: null }
const notification = (overrides: Partial<AppNotification> = {}): AppNotification => ({
  id: 'n1',
  type: 'COMMENT',
  actor,
  guideId: 'g1',
  commentId: 'c1',
  content: '写得真好，秋季去合适吗？',
  createdAt: '2026-11-13T09:30:00.000+08:00',
  readAt: null,
  href: '/guides/g1?comments=1&commentId=c1',
  ...overrides
})
const pageOf = (records: AppNotification[], extra: Partial<PageResult<AppNotification>> = {}): PageResult<AppNotification> => ({
  records, total: records.length, current: 1, size: 10, pages: records.length ? 1 : 0, ...extra
})
const countOf = (count: number): NotificationUnreadCount => ({ count })

function buildRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'home', component: { template: '<div />' } },
      { path: '/notifications', name: 'notifications', component: { template: '<div />' } },
      { path: '/guides/:id', name: 'guide-detail', component: { template: '<div />' } },
      { path: '/login', name: 'login', component: { template: '<div />' } }
    ]
  })
}

type BellWrapper = ReturnType<typeof mount>
const wrappers: BellWrapper[] = []
afterEach(() => {
  wrappers.splice(0).forEach((wrapper) => wrapper.unmount())
  vi.useRealTimers()
})

async function mountBell(authenticated = true) {
  const pinia = createPinia()
  setActivePinia(pinia)
  const store = useAuthStore()
  if (authenticated) {
    store.user = user
    store.token = 'token-a'
  }
  const router = buildRouter()
  await router.push('/')
  await router.isReady()
  const wrapper = mount(NotificationBell, { global: { plugins: [pinia, router] } })
  wrappers.push(wrapper)
  return { wrapper, store, router }
}

async function mountView(path = '/notifications', authenticated = true) {
  const pinia = createPinia()
  setActivePinia(pinia)
  const store = useAuthStore()
  if (authenticated) {
    store.user = user
    store.token = 'token-a'
  }
  const router = buildRouter()
  await router.push(path)
  await router.isReady()
  const wrapper = mount(NotificationsView, { global: { plugins: [pinia, router] } })
  wrappers.push(wrapper)
  await flushPromises()
  return { wrapper, router, store }
}

describe('通知铃铛', () => {
  it('未登录不渲染铃铛，也不请求未读数', async () => {
    vi.mocked(fetchNotificationUnreadCount).mockResolvedValue(countOf(3))
    const { wrapper } = await mountBell(false)
    await flushPromises()
    expect(wrapper.find('[data-test="notification-bell"]').exists()).toBe(false)
    expect(fetchNotificationUnreadCount).not.toHaveBeenCalled()
  })

  it('已登录渲染铃铛并显示未读数', async () => {
    vi.mocked(fetchNotificationUnreadCount).mockResolvedValue(countOf(3))
    const { wrapper } = await mountBell()
    await flushPromises()
    const bell = wrapper.get('[data-test="notification-bell"]')
    expect(bell.attributes('href')).toBe('/me/notifications')
    expect(bell.attributes('aria-label')).toContain('3')
    expect(wrapper.get('[data-test="notification-count"]').text()).toBe('3')
    expect(fetchNotificationUnreadCount).toHaveBeenCalledTimes(1)
  })

  it('未读数超过 99 时显示 99+，真实数字保留在无障碍标签里', async () => {
    vi.mocked(fetchNotificationUnreadCount).mockResolvedValue(countOf(128))
    const { wrapper } = await mountBell()
    await flushPromises()
    expect(wrapper.get('[data-test="notification-count"]').text()).toBe('99+')
    expect(wrapper.get('[data-test="notification-bell"]').attributes('aria-label')).toContain('128')
  })

  it('未读数请求失败时不显示假数字，也不弹全局错误', async () => {
    vi.mocked(fetchNotificationUnreadCount).mockRejectedValue(new ApiError('服务器开小差了，请稍后再试', { status: 503 }))
    const { wrapper } = await mountBell()
    await flushPromises()
    expect(wrapper.find('[data-test="notification-bell"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="notification-count"]').exists()).toBe(false)
    expect(wrapper.get('[data-test="notification-bell"]').attributes('aria-label')).toContain('暂时无法获取')
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
  })

  it('切换账号或登出时清空旧计数，并按新登录态重新加载', async () => {
    vi.mocked(fetchNotificationUnreadCount).mockResolvedValueOnce(countOf(3))
    let resolveNext!: (value: NotificationUnreadCount) => void
    vi.mocked(fetchNotificationUnreadCount).mockImplementationOnce(
      () => new Promise<NotificationUnreadCount>((done) => { resolveNext = done })
    )
    const { wrapper, store } = await mountBell()
    await flushPromises()
    expect(wrapper.get('[data-test="notification-count"]').text()).toBe('3')

    store.user = otherUser
    store.token = 'token-b'
    await flushPromises()
    expect(fetchNotificationUnreadCount).toHaveBeenCalledTimes(2)
    expect(wrapper.find('[data-test="notification-count"]').exists()).toBe(false)

    resolveNext(countOf(5))
    await flushPromises()
    expect(wrapper.get('[data-test="notification-count"]').text()).toBe('5')

    store.user = null
    store.token = null
    await flushPromises()
    expect(wrapper.find('[data-test="notification-bell"]').exists()).toBe(false)
    expect(fetchNotificationUnreadCount).toHaveBeenCalledTimes(2)
  })

  it('卸载后清理轮询，推进时间不再发请求', async () => {
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval'] })
    vi.mocked(fetchNotificationUnreadCount).mockResolvedValue(countOf(1))
    const { wrapper } = await mountBell()
    await flushPromises()
    expect(fetchNotificationUnreadCount).toHaveBeenCalledTimes(1)

    await vi.advanceTimersByTimeAsync(60000)
    await flushPromises()
    expect(fetchNotificationUnreadCount).toHaveBeenCalledTimes(2)

    const at = wrappers.indexOf(wrapper)
    if (at >= 0) wrappers.splice(at, 1)
    wrapper.unmount()
    await vi.advanceTimersByTimeAsync(180000)
    await flushPromises()
    expect(fetchNotificationUnreadCount).toHaveBeenCalledTimes(2)
  })
})

describe('通知列表页', () => {
  it('按路由页码与仅未读条件请求，翻页保持筛选条件', async () => {
    vi.mocked(fetchNotifications).mockImplementation(async (params: NotificationQuery = {}) =>
      pageOf([notification()], { total: 25, current: params.page ?? 1, pages: 3 })
    )
    const { wrapper } = await mountView('/notifications?page=2')
    expect(fetchNotifications).toHaveBeenNthCalledWith(1, { page: 2, size: 10, unreadOnly: false })
    expect(wrapper.findAll('[data-test="notification-row"]')).toHaveLength(1)

    await wrapper.get('[data-test="notifications-filter-unread"]').trigger('click')
    await flushPromises()
    expect(fetchNotifications).toHaveBeenLastCalledWith({ page: 1, size: 10, unreadOnly: true })

    await wrapper.get('[data-test="notifications-next"]').trigger('click')
    await flushPromises()
    expect(fetchNotifications).toHaveBeenLastCalledWith({ page: 2, size: 10, unreadOnly: true })
    expect(wrapper.find('[data-test="notifications-prev"]').exists()).toBe(true)
  })

  it('按类型展示中文文案、作者与时间，未读项有标记', async () => {
    vi.mocked(fetchNotifications).mockResolvedValue(pageOf([
      notification({ id: 'n1', type: 'COMMENT' }),
      notification({ id: 'n2', type: 'REPLY' }),
      notification({ id: 'n3', type: 'MENTION' })
    ]))
    const { wrapper } = await mountView()
    const rows = wrapper.findAll('[data-test="notification-row"]')
    expect(rows[0].text()).toContain('评论了你')
    expect(rows[1].text()).toContain('回复了你')
    expect(rows[2].text()).toContain('在评论中提到了你')
    expect(rows[0].text()).toContain('阿凯')
    expect(rows[0].text()).toContain('2026-11-13 09:30')
    expect(wrapper.findAll('[data-test="notification-unread"]')).toHaveLength(3)
  })

  it('点击未读通知先标记已读，成功后才跳转攻略评论', async () => {
    vi.mocked(fetchNotifications).mockResolvedValue(pageOf([notification()]))
    vi.mocked(markNotificationRead).mockResolvedValue(null)
    const { wrapper, router } = await mountView()
    expect(wrapper.find('[data-test="notification-unread"]').exists()).toBe(true)

    await wrapper.get('[data-test="notification-row"]').trigger('click')
    await flushPromises()
    expect(markNotificationRead).toHaveBeenCalledWith('n1')
    expect(router.currentRoute.value.path).toBe('/guides/g1')
    expect(router.currentRoute.value.query).toEqual({ comments: '1', commentId: 'c1' })
    expect(wrapper.find('[data-test="notification-unread"]').exists()).toBe(false)
  })

  it('标记已读失败时不跳转并给出错误提示', async () => {
    vi.mocked(fetchNotifications).mockResolvedValue(pageOf([notification()]))
    vi.mocked(markNotificationRead).mockRejectedValue(new ApiError('请求未能完成，请稍后再试', { status: 500 }))
    const { wrapper, router } = await mountView()

    await wrapper.get('[data-test="notification-row"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/notifications')
    expect(wrapper.get('[data-test="notification-error"]').text()).toContain('请求未能完成')
    expect(wrapper.find('[data-test="notification-unread"]').exists()).toBe(true)
  })

  it('标记已读返回 404 时提示通知失效并刷新列表', async () => {
    vi.mocked(fetchNotifications).mockResolvedValue(pageOf([notification()]))
    vi.mocked(markNotificationRead).mockRejectedValue(new ApiError('通知不存在', { status: 404 }))
    const { wrapper, router } = await mountView()

    await wrapper.get('[data-test="notification-row"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/notifications')
    expect(wrapper.get('[data-test="notification-error"]').text()).toContain('失效')
    expect(fetchNotifications).toHaveBeenCalledTimes(2)
  })

  it('已读通知直接跳转，不重复调用标记接口', async () => {
    vi.mocked(fetchNotifications).mockResolvedValue(pageOf([
      notification({ readAt: '2026-11-13T10:00:00.000+08:00' })
    ]))
    const { wrapper, router } = await mountView()
    await wrapper.get('[data-test="notification-row"]').trigger('click')
    await flushPromises()
    expect(markNotificationRead).not.toHaveBeenCalled()
    expect(router.currentRoute.value.path).toBe('/guides/g1')
  })

  it('全部标为已读后清空未读标记并刷新列表', async () => {
    vi.mocked(fetchNotifications)
      .mockResolvedValueOnce(pageOf([notification(), notification({ id: 'n2' })], { total: 2 }))
      .mockResolvedValueOnce(pageOf([
        notification({ readAt: '2026-11-13T10:00:00.000+08:00' }),
        notification({ id: 'n2', readAt: '2026-11-13T10:00:00.000+08:00' })
      ], { total: 2 }))
    vi.mocked(markAllNotificationsRead).mockResolvedValue(null)
    const { wrapper } = await mountView()
    expect(wrapper.findAll('[data-test="notification-unread"]')).toHaveLength(2)

    await wrapper.get('[data-test="mark-all-read"]').trigger('click')
    await flushPromises()
    expect(markAllNotificationsRead).toHaveBeenCalledTimes(1)
    expect(fetchNotifications).toHaveBeenCalledTimes(2)
    expect(wrapper.findAll('[data-test="notification-unread"]')).toHaveLength(0)
  })

  it('加载失败可重试，空列表给出空态', async () => {
    vi.mocked(fetchNotifications).mockRejectedValueOnce(new ApiError('通知暂时无法加载', { status: 503 }))
    const { wrapper } = await mountView()
    expect(wrapper.get('[role="alert"]').text()).toContain('通知暂时无法加载')

    vi.mocked(fetchNotifications).mockResolvedValueOnce(pageOf([]))
    await wrapper.get('[data-test="notifications-retry"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('还没有通知')
    expect(wrapper.find('[data-test="notification-row"]').exists()).toBe(false)
  })

  it('会话失效时引导登录并保留回跳地址', async () => {
    vi.mocked(fetchNotifications).mockRejectedValueOnce(new ApiError('登录状态已失效', { status: 401 }))
    const { router } = await mountView()
    expect(router.currentRoute.value.name).toBe('login')
    expect(router.currentRoute.value.query.redirect).toBe('/notifications')
  })
})

describe('通知接口封装', () => {
  it('按契约拼装接口路径与查询参数', async () => {
    const actual = await vi.importActual<typeof import('@/api/notifications')>('@/api/notifications')
    const client = apiClient as unknown as { get: ReturnType<typeof vi.fn>; put: ReturnType<typeof vi.fn> }
    client.get.mockResolvedValue(pageOf([]))
    client.put.mockResolvedValue(null)

    await actual.fetchNotifications({ page: 2, size: 10, unreadOnly: true })
    expect(client.get).toHaveBeenCalledWith('/notifications', { params: { page: 2, size: 10, unreadOnly: true } })

    await actual.fetchNotificationUnreadCount()
    expect(client.get).toHaveBeenCalledWith('/notifications/unread-count')

    await actual.markNotificationRead('a/b')
    expect(client.put).toHaveBeenCalledWith('/notifications/a%2Fb/read')

    await actual.markAllNotificationsRead()
    expect(client.put).toHaveBeenCalledWith('/notifications/read-all')
  })
})


describe('通知账号隔离回归', () => {
  it('切换账号时清除旧通知并丢弃旧列表响应', async () => {
    let finish!: (value: PageResult<AppNotification>) => void
    vi.mocked(fetchNotifications).mockReturnValueOnce(new Promise(resolve => { finish=resolve }))
    const { wrapper, store } = await mountView()
    vi.mocked(fetchNotifications).mockResolvedValueOnce(pageOf([]))
    store.user={ ...user,id:'different',username:'different' }
    store.token='different-token'
    await flushPromises()
    finish(pageOf([notification()]))
    await flushPromises()
    expect(wrapper.find('[data-test="notification-row"]').exists()).toBe(false)
  })
})
