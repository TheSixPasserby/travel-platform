import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

import type { CurrentUser, LoginData } from '@/types/auth'
import { ApiError } from '@/api/client'
import { TOKEN_STORAGE_KEY } from '@/api/token'
import {
  fetchCurrentUser,
  fetchProfile,
  loginAccount,
  logoutAccount,
  patchProfile
} from '@/api/auth'
import { useAuthStore } from '@/stores/auth'

vi.mock('@/api/auth', () => ({
  registerAccount: vi.fn(),
  loginAccount: vi.fn(),
  logoutAccount: vi.fn(),
  fetchCurrentUser: vi.fn(),
  fetchProfile: vi.fn(),
  patchProfile: vi.fn()
}))

function sampleUser(overrides: Partial<CurrentUser> = {}): CurrentUser {
  return {
    id: '1',
    username: 'luna',
    nickname: '小鹿',
    avatarUrl: '/uploads/avatars/luna.jpg',
    bio: '喜欢山野与清晨',
    role: 'USER',
    status: 'ACTIVE',
    createdAt: '2026-11-02T10:00:00.000+08:00',
    ...overrides
  }
}

function networkFailure(): ApiError {
  return new ApiError('网络连接失败，请检查网络后重试', { isNetworkError: true })
}

beforeEach(() => {
  setActivePinia(createPinia())
  vi.mocked(loginAccount).mockReset()
  vi.mocked(logoutAccount).mockReset()
  vi.mocked(fetchCurrentUser).mockReset()
  vi.mocked(fetchProfile).mockReset()
  vi.mocked(patchProfile).mockReset()
})

describe('登录状态', () => {
  it('旧账号退出请求迟到时不清理新账号会话', async () => {
    const store=useAuthStore();store.user=sampleUser();store.token='old-token'
    let finish!:(value:null)=>void
    vi.mocked(logoutAccount).mockReturnValueOnce(new Promise(resolve=>{finish=resolve}))
    const job=store.logout()
    store.user=sampleUser({id:'2',username:'other'});store.token='new-token'
    finish(null);await job
    expect(store.token).toBe('new-token');expect(store.user?.id).toBe('2')
  })
  it('登录成功后写入用户与令牌，并只写入 sessionStorage', async () => {
    const data: LoginData = { tokenName: 'satoken', tokenValue: 'token-abc', user: sampleUser() }
    vi.mocked(loginAccount).mockResolvedValue(data)
    const store = useAuthStore()

    const user = await store.login({ username: 'luna', password: 'secret123' })

    expect(user.nickname).toBe('小鹿')
    expect(store.user?.id).toBe('1')
    expect(store.token).toBe('token-abc')
    expect(store.isAuthenticated).toBe(true)
    expect(sessionStorage.getItem(TOKEN_STORAGE_KEY)).toBe('token-abc')
    expect(localStorage.getItem(TOKEN_STORAGE_KEY)).toBeNull()
  })

  it('登录失败不写入任何会话状态', async () => {
    vi.mocked(loginAccount).mockRejectedValue(
      new ApiError('账号或密码不正确', { status: 401, code: 40102 })
    )
    const store = useAuthStore()

    await expect(store.login({ username: 'luna', password: 'wrong-pass' })).rejects.toBeInstanceOf(
      ApiError
    )
    expect(store.user).toBeNull()
    expect(store.token).toBeNull()
    expect(store.isAuthenticated).toBe(false)
    expect(sessionStorage.getItem(TOKEN_STORAGE_KEY)).toBeNull()
  })
})

describe('恢复会话', () => {
  it('令牌有效时通过当前用户接口恢复用户状态', async () => {
    sessionStorage.setItem(TOKEN_STORAGE_KEY, 'token-abc')
    vi.mocked(fetchCurrentUser).mockResolvedValue(sampleUser())
    const store = useAuthStore()

    await expect(store.restoreSession()).resolves.toBe(true)
    expect(store.user?.username).toBe('luna')
    expect(store.isAuthenticated).toBe(true)
    expect(store.token).toBe('token-abc')
  })

  it('当前用户接口返回 401 时清除用户、令牌与 sessionStorage', async () => {
    sessionStorage.setItem(TOKEN_STORAGE_KEY, 'expired-token')
    vi.mocked(fetchCurrentUser).mockRejectedValue(
      new ApiError('登录状态已失效，请重新登录', { status: 401, code: 40101 })
    )
    const store = useAuthStore()

    await expect(store.restoreSession()).resolves.toBe(false)
    expect(store.user).toBeNull()
    expect(store.token).toBeNull()
    expect(store.isAuthenticated).toBe(false)
    expect(sessionStorage.getItem(TOKEN_STORAGE_KEY)).toBeNull()
  })

  it('网络失败保留令牌，恢复后可以重试成功', async () => {
    sessionStorage.setItem(TOKEN_STORAGE_KEY, 'token-abc')
    vi.mocked(fetchCurrentUser).mockRejectedValueOnce(networkFailure())
    const store = useAuthStore()

    await expect(store.restoreSession()).rejects.toBeInstanceOf(ApiError)
    expect(store.token).toBe('token-abc')
    expect(sessionStorage.getItem(TOKEN_STORAGE_KEY)).toBe('token-abc')
    expect(store.user).toBeNull()

    vi.mocked(fetchCurrentUser).mockResolvedValueOnce(sampleUser())
    await expect(store.restoreSession()).resolves.toBe(true)
    expect(store.user?.nickname).toBe('小鹿')
    expect(store.isAuthenticated).toBe(true)
  })

  it('并发恢复会话时只请求一次当前用户接口', async () => {
    sessionStorage.setItem(TOKEN_STORAGE_KEY, 'token-abc')
    vi.mocked(fetchCurrentUser).mockResolvedValue(sampleUser())
    const store = useAuthStore()

    const results = await Promise.all([store.restoreSession(), store.restoreSession()])

    expect(results).toEqual([true, true])
    expect(fetchCurrentUser).toHaveBeenCalledTimes(1)
  })

  it('没有令牌时不请求接口，直接判定为未登录', async () => {
    const store = useAuthStore()

    await expect(store.restoreSession()).resolves.toBe(false)
    expect(fetchCurrentUser).not.toHaveBeenCalled()
    expect(store.isAuthenticated).toBe(false)
  })
})

describe('资料更新与退出', () => {
  it('部分更新资料时不丢失当前其他字段', async () => {
    vi.mocked(patchProfile).mockResolvedValue({ nickname: '新昵称' } as unknown as CurrentUser)
    const store = useAuthStore()
    store.user = sampleUser()
    store.token = 'token-abc'

    const updated = await store.updateProfile({ nickname: '新昵称' })

    expect(patchProfile).toHaveBeenCalledWith({ nickname: '新昵称' })
    expect(updated?.nickname).toBe('新昵称')
    expect(updated?.username).toBe('luna')
    expect(updated?.bio).toBe('喜欢山野与清晨')
    expect(updated?.avatarUrl).toBe('/uploads/avatars/luna.jpg')
    expect(updated?.id).toBe('1')
    expect(store.user?.bio).toBe('喜欢山野与清晨')
    expect(store.token).toBe('token-abc')
  })

  it('清空头像时接受 null 并保留其他字段', async () => {
    vi.mocked(patchProfile).mockResolvedValue({ avatarUrl: null } as unknown as CurrentUser)
    const store = useAuthStore()
    store.user = sampleUser()
    store.token = 'token-abc'

    const updated = await store.updateProfile({ avatarUrl: null })

    expect(patchProfile).toHaveBeenCalledWith({ avatarUrl: null })
    expect(updated?.avatarUrl).toBeNull()
    expect(updated?.bio).toBe('喜欢山野与清晨')
  })

  it('退出成功后清除会话与 sessionStorage', async () => {
    sessionStorage.setItem(TOKEN_STORAGE_KEY, 'token-abc')
    vi.mocked(logoutAccount).mockResolvedValue(null)
    const store = useAuthStore()
    store.user = sampleUser()

    await store.logout()

    expect(logoutAccount).toHaveBeenCalledTimes(1)
    expect(store.user).toBeNull()
    expect(store.token).toBeNull()
    expect(sessionStorage.getItem(TOKEN_STORAGE_KEY)).toBeNull()
  })

  it('退出遇到网络失败时保留会话，不谎报退出成功', async () => {
    sessionStorage.setItem(TOKEN_STORAGE_KEY, 'token-abc')
    vi.mocked(logoutAccount).mockRejectedValue(networkFailure())
    const store = useAuthStore()
    store.user = sampleUser()

    await expect(store.logout()).rejects.toBeInstanceOf(ApiError)
    expect(store.user?.username).toBe('luna')
    expect(store.token).toBe('token-abc')
    expect(sessionStorage.getItem(TOKEN_STORAGE_KEY)).toBe('token-abc')
  })

  it('退出时令牌已失效则直接清理本地会话', async () => {
    sessionStorage.setItem(TOKEN_STORAGE_KEY, 'expired-token')
    vi.mocked(logoutAccount).mockRejectedValue(
      new ApiError('登录状态已失效，请重新登录', { status: 401, code: 40101 })
    )
    const store = useAuthStore()
    store.user = sampleUser()

    await store.logout()

    expect(store.user).toBeNull()
    expect(store.token).toBeNull()
    expect(sessionStorage.getItem(TOKEN_STORAGE_KEY)).toBeNull()
  })
})

describe('ensureSession', () => {
  it('没有令牌时返回 anonymous 且不发起请求', async () => {
    const store = useAuthStore()

    await expect(store.ensureSession()).resolves.toBe('anonymous')
    expect(fetchCurrentUser).not.toHaveBeenCalled()
  })

  it('网络失败时返回 unavailable 且保留令牌', async () => {
    sessionStorage.setItem(TOKEN_STORAGE_KEY, 'token-abc')
    vi.mocked(fetchCurrentUser).mockRejectedValue(networkFailure())
    const store = useAuthStore()

    await expect(store.ensureSession()).resolves.toBe('unavailable')
    expect(store.token).toBe('token-abc')
    expect(sessionStorage.getItem(TOKEN_STORAGE_KEY)).toBe('token-abc')
  })

  it.each([500, 502])(
    '服务端 %i 临时错误返回 unavailable 且保留令牌，恢复后仍可登录',
    async (status) => {
      sessionStorage.setItem(TOKEN_STORAGE_KEY, 'token-abc')
      vi.mocked(fetchCurrentUser).mockRejectedValue(
        new ApiError('服务器开小差了，请稍后再试', { status, code: 50001 })
      )
      const store = useAuthStore()

      await expect(store.ensureSession()).resolves.toBe('unavailable')
      expect(store.user).toBeNull()
      expect(store.token).toBe('token-abc')
      expect(sessionStorage.getItem(TOKEN_STORAGE_KEY)).toBe('token-abc')

      vi.mocked(fetchCurrentUser).mockResolvedValueOnce(sampleUser())
      await expect(store.ensureSession()).resolves.toBe('authenticated')
      expect(store.isAuthenticated).toBe(true)
      expect(fetchCurrentUser).toHaveBeenCalledTimes(2)
    }
  )

  it('401 时返回 anonymous 并清理会话', async () => {
    sessionStorage.setItem(TOKEN_STORAGE_KEY, 'expired-token')
    vi.mocked(fetchCurrentUser).mockRejectedValue(
      new ApiError('登录状态已失效，请重新登录', { status: 401, code: 40101 })
    )
    const store = useAuthStore()

    await expect(store.ensureSession()).resolves.toBe('anonymous')
    expect(store.token).toBeNull()
    expect(sessionStorage.getItem(TOKEN_STORAGE_KEY)).toBeNull()
  })
})
