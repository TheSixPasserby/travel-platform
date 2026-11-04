import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, type Router } from 'vue-router'

import { createAppRouter } from '@/router'
import { fetchCurrentUser } from '@/api/auth'
import { ApiError } from '@/api/client'
import { TOKEN_STORAGE_KEY } from '@/api/token'
import type { CurrentUser } from '@/types/auth'

vi.mock('@/api/auth', () => ({
  registerAccount: vi.fn(),
  loginAccount: vi.fn(),
  logoutAccount: vi.fn(),
  fetchCurrentUser: vi.fn(),
  fetchProfile: vi.fn(),
  patchProfile: vi.fn()
}))

const user: CurrentUser = {
  id: '1',
  username: 'luna',
  nickname: '小鹿',
  avatarUrl: null,
  bio: null,
  role: 'USER',
  status: 'ACTIVE',
  createdAt: '2026-11-02T10:00:00.000+08:00'
}

function routerWithHistory(): Router {
  return createAppRouter(createMemoryHistory())
}

beforeEach(() => {
  setActivePinia(createPinia())
  vi.mocked(fetchCurrentUser).mockReset()
})

describe('路由守卫', () => {
  it('治理页面游客回登录，普通用户拒绝，管理员可进入',async()=>{
    const paths=['/admin','/admin/users','/admin/guides','/admin/comments']
    let router=routerWithHistory();for(const path of paths){await router.push(path);expect(router.currentRoute.value.name).toBe('login');expect(router.currentRoute.value.query.redirect).toBe(path)}
    sessionStorage.setItem(TOKEN_STORAGE_KEY,'token-abc');vi.mocked(fetchCurrentUser).mockResolvedValue(user);setActivePinia(createPinia());router=routerWithHistory();for(const path of paths){await router.push(path);expect(router.currentRoute.value.name).toBe('home')}
    vi.mocked(fetchCurrentUser).mockResolvedValue({...user,role:'ADMIN'});setActivePinia(createPinia());router=routerWithHistory();for(const path of paths){await router.push(path);expect(router.currentRoute.value.path).toBe(path)}
  })
  it('私人行程列表与详情要求登录并保留回跳',async()=>{
    const router=routerWithHistory();for(const path of ['/me/itineraries','/itineraries/9007199254740993']){await router.push(path);expect(router.currentRoute.value.name).toBe('login');expect(router.currentRoute.value.query.redirect).toBe(path)}
  })
  it('我的收藏需要登录并保留安全回跳',async()=>{
    const router=routerWithHistory();await router.push('/me/favorites')
    expect(router.currentRoute.value.name).toBe('login');expect(router.currentRoute.value.query.redirect).toBe('/me/favorites')
  })
  it('已有会话访问登录页时回到指定攻略',async()=>{
    sessionStorage.setItem(TOKEN_STORAGE_KEY,'token-abc');vi.mocked(fetchCurrentUser).mockResolvedValue(user)
    const router=routerWithHistory();await router.push('/login?redirect=/guides/9007199254740993')
    expect(router.currentRoute.value.name).toBe('guide-detail');expect(router.currentRoute.value.params.id).toBe('9007199254740993')
  })
  it('已有会话的guestOnly回跳不能循环或离开本站',async()=>{
    sessionStorage.setItem(TOKEN_STORAGE_KEY,'token-abc');vi.mocked(fetchCurrentUser).mockResolvedValue(user)
    const router=routerWithHistory()
    for(const redirect of ['/login','/register','//evil.example','https://evil.example']){
      await router.push({ name:'login',query:{ redirect } });expect(router.currentRoute.value.name).toBe('me')
    }
  })

  it('攻略列表和公开详情允许游客访问',async()=>{
    const router=routerWithHistory()
    for(const path of ['/guides','/guides/9007199254740993']){
      await router.push(path)
      expect(router.currentRoute.value.name).toBe(path==='/guides'?'guides':'guide-detail')
    }
    expect(fetchCurrentUser).not.toHaveBeenCalled()
  })
  it('攻略创建、编辑和我的攻略均需登录并保留回跳',async()=>{
    const router=routerWithHistory()
    for(const path of ['/guides/new','/guides/9007199254740993/edit','/me/guides']){
      await router.push(path)
      expect(router.currentRoute.value.name).toBe('login')
      expect(router.currentRoute.value.query.redirect).toBe(path)
    }
  })

  it('游客可浏览目的地及详情', async () => {
    const router = routerWithHistory()
    for (const path of ['/destinations', '/destinations/9007199254740993']) {
      await router.push(path)
      expect(router.currentRoute.value.name).toBe(path === '/destinations' ? 'destinations' : 'destination-detail')
    }
    expect(fetchCurrentUser).not.toHaveBeenCalled()
  })

  it('管理员入口要求登录并保留回跳', async () => {
    const router = routerWithHistory()
    await router.push('/admin/destinations')
    expect(router.currentRoute.value.name).toBe('login')
    expect(router.currentRoute.value.query.redirect).toBe('/admin/destinations')
  })

  it('普通用户不能进入目的地管理页', async () => {
    sessionStorage.setItem(TOKEN_STORAGE_KEY, 'token-abc')
    vi.mocked(fetchCurrentUser).mockResolvedValue(user)
    const router = routerWithHistory()
    await router.push('/admin/destinations')
    expect(router.currentRoute.value.name).toBe('home')
  })

  it('管理员可进入管理页', async () => {
    sessionStorage.setItem(TOKEN_STORAGE_KEY, 'token-abc')
    vi.mocked(fetchCurrentUser).mockResolvedValue({ ...user, role: 'ADMIN' })
    const router = routerWithHistory()
    await router.push('/admin/destinations')
    expect(router.currentRoute.value.name).toBe('admin-destinations')
  })

  it('会话查询暂时失败不误判管理员身份或清除令牌', async () => {
    sessionStorage.setItem(TOKEN_STORAGE_KEY, 'token-abc')
    vi.mocked(fetchCurrentUser).mockRejectedValue(new ApiError('暂时不可用', { status: 503 }))
    const router = routerWithHistory()
    await router.push('/admin/destinations')
    expect(router.currentRoute.value.name).toBe('admin-destinations')
    expect(sessionStorage.getItem(TOKEN_STORAGE_KEY)).toBe('token-abc')
  })

  it('游客可以访问首页、登录与注册页', async () => {
    const router = routerWithHistory()

    for (const path of ['/', '/login', '/register']) {
      await router.push(path)
      expect(router.currentRoute.value.path).toBe(path)
    }
    expect(fetchCurrentUser).not.toHaveBeenCalled()
  })

  it('未登录访问个人资料被引导到登录页并保留内部回跳地址', async () => {
    const router = routerWithHistory()

    await router.push('/me')

    expect(router.currentRoute.value.name).toBe('login')
    expect(router.currentRoute.value.query.redirect).toBe('/me')
    expect(fetchCurrentUser).not.toHaveBeenCalled()
  })

  it('有令牌时访问受限页先恢复会话再进入', async () => {
    sessionStorage.setItem(TOKEN_STORAGE_KEY, 'token-abc')
    vi.mocked(fetchCurrentUser).mockResolvedValue(user)
    const router = routerWithHistory()

    await router.push('/me')

    expect(fetchCurrentUser).toHaveBeenCalledTimes(1)
    expect(router.currentRoute.value.name).toBe('me')
  })

  it('有令牌但接口返回 401 时被送回登录页并清除令牌', async () => {
    sessionStorage.setItem(TOKEN_STORAGE_KEY, 'expired-token')
    vi.mocked(fetchCurrentUser).mockRejectedValue(
      new ApiError('登录状态已失效，请重新登录', { status: 401, code: 40101 })
    )
    const router = routerWithHistory()

    await router.push('/me')

    expect(router.currentRoute.value.name).toBe('login')
    expect(sessionStorage.getItem(TOKEN_STORAGE_KEY)).toBeNull()
  })

  it('网络失败时停留在受限页的加载状态而不误判为退出登录', async () => {
    sessionStorage.setItem(TOKEN_STORAGE_KEY, 'token-abc')
    vi.mocked(fetchCurrentUser).mockRejectedValue(
      new ApiError('网络连接失败，请检查网络后重试', { isNetworkError: true })
    )
    const router = routerWithHistory()

    await router.push('/me')

    expect(router.currentRoute.value.name).toBe('me')
    expect(sessionStorage.getItem(TOKEN_STORAGE_KEY)).toBe('token-abc')
  })

  it('服务端 5xx 临时故障时停留在受限页而不误判为退出登录', async () => {
    sessionStorage.setItem(TOKEN_STORAGE_KEY, 'token-abc')
    vi.mocked(fetchCurrentUser).mockRejectedValue(
      new ApiError('服务器开小差了，请稍后再试', { status: 503, code: 50001 })
    )
    const router = routerWithHistory()

    await router.push('/me')

    expect(router.currentRoute.value.name).toBe('me')
    expect(sessionStorage.getItem(TOKEN_STORAGE_KEY)).toBe('token-abc')
  })

  it('已登录访问登录页时回到个人资料', async () => {
    sessionStorage.setItem(TOKEN_STORAGE_KEY, 'token-abc')
    vi.mocked(fetchCurrentUser).mockResolvedValue(user)
    const router = routerWithHistory()
    await router.push('/me')
    await router.isReady()

    await router.push('/login')

    expect(router.currentRoute.value.name).toBe('me')
  })
})
