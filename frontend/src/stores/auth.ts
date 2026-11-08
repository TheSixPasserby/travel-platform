import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

import {
  fetchCurrentUser,
  fetchProfile,
  loginAccount,
  logoutAccount,
  patchProfile
} from '@/api/auth'
import { ApiError } from '@/api/client'
import { clearToken, readToken, writeToken } from '@/api/token'
import type { CurrentUser, LoginPayload, ProfileUpdatePayload } from '@/types/auth'

/** 会话探测结果：已登录、确定未登录、暂时无法判断（网络不可用）。 */
export type SessionStatus = 'authenticated' | 'anonymous' | 'unavailable'

export const useAuthStore = defineStore('auth', () => {
  const user = ref<CurrentUser | null>(null)
  const token = ref<string | null>(readToken())

  const isAuthenticated = computed(() => Boolean(user.value && token.value))

  function applyToken(nextToken: string): void {
    token.value = nextToken
    writeToken(nextToken)
  }

  function clearSession(): void {
    inflightRestore = null
    user.value = null
    token.value = null
    clearToken()
  }

  async function login(payload: LoginPayload): Promise<CurrentUser> {
    const data = await loginAccount(payload)
    applyToken(data.tokenValue)
    user.value = data.user
    return data.user
  }

  /** 正在进行的会话恢复请求；并发调用共享同一次请求，避免重复打接口。 */
  let inflightRestore: Promise<boolean> | null = null

  async function runRestore(): Promise<boolean> {
    try {
      user.value = await fetchCurrentUser()
      return true
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) {
        clearSession()
        return false
      }
      throw error
    }
  }

  /**
   * 用会话里的令牌换回当前用户。
   * 401 视为登录失效并清理；网络错误原样抛出，保留令牌供重试。
   */
  async function restoreSession(): Promise<boolean> {
    if (!token.value) {
      user.value = null
      return false
    }
    if (!inflightRestore) {
      inflightRestore = runRestore().finally(() => {
        inflightRestore = null
      })
    }
    return inflightRestore
  }

  /** 路由守卫使用：把「无令牌/网络不可用/已登录」三态显式区分，避免把断网误判为登出。 */
  async function ensureSession(): Promise<SessionStatus> {
    if (user.value && token.value) {
      return 'authenticated'
    }
    if (!token.value) {
      return 'anonymous'
    }
    try {
      await restoreSession()
      return user.value ? 'authenticated' : 'anonymous'
    } catch (error) {
      // 只有确定 401 才判定未登录；网络错误、5xx 等一律视为暂时不可用并保留令牌，
      // 避免临时服务故障把用户误登出。
      if (error instanceof ApiError && error.status === 401) {
        return 'anonymous'
      }
      return 'unavailable'
    }
  }

  /** 从 /users/me 拉取完整资料并同步到全局状态。 */
  async function loadProfile(): Promise<CurrentUser> {
    try {
      const profile = await fetchProfile()
      user.value = profile
      return profile
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) {
        clearSession()
      }
      throw error
    }
  }

  /**
   * 局部更新资料：只提交变化字段，返回体按字段合并，避免服务端未回传的字段被清空。
   */
  async function updateProfile(payload: ProfileUpdatePayload): Promise<CurrentUser | null> {
    const updated = await patchProfile(payload)
    if (!user.value) {
      return null
    }
    const previous = user.value
    const next: CurrentUser = { ...previous, ...updated }
    user.value = next
    return next
  }

  async function logout(): Promise<void> {
    const signingOutToken = token.value
    try {
      await logoutAccount()
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) {
        if (token.value !== signingOutToken) return
        // 令牌已失效，服务端会话本就不存在，本地清理即达成目的。
        clearSession()
        return
      }
      throw error
    }
    if (token.value === signingOutToken) clearSession()
  }

  return {
    user,
    token,
    isAuthenticated,
    login,
    restoreSession,
    ensureSession,
    loadProfile,
    updateProfile,
    logout,
    clearSession
  }
})
