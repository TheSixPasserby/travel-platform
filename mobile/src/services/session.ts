import { reactive, ref } from 'vue'
import type { CurrentUser, LoginData, LoginPayload } from '../../../frontend/src/types/auth.ts'
import { request, setUnauthorizedHandler } from '../platform/request.ts'
import { readToken, writeToken } from './token.ts'
import { ApiError, errorMessage } from '../platform/errors.ts'

export const session = reactive({ user: null as CurrentUser | null, error: '', restoring: false })
export const sessionVersion = ref(0)
let restoring: Promise<boolean> | null = null

function clearSession(): void {
  writeToken(null)
  session.user = null
  session.error = ''
  session.restoring = false
  restoring = null
  sessionVersion.value++
}
setUnauthorizedHandler(clearSession)

export async function login(payload: LoginPayload): Promise<void> {
  const version = ++sessionVersion.value
  const data = await request<LoginData>('/auth/login', { method: 'POST', data: payload, auth: false })
  if (version !== sessionVersion.value) throw new ApiError('会话已变化，请重试')
  writeToken(data.tokenValue)
  session.user = data.user
  session.error = ''
  session.restoring = false
  restoring = null
  sessionVersion.value++
}

export async function restoreSession(): Promise<boolean> {
  if (!readToken()) { session.user = null; return false }
  if (session.user) return true
  if (restoring) return restoring
  const token = readToken()
  const version = sessionVersion.value
  session.restoring = true
  const task = request<CurrentUser>('/auth/me').then(user => {
    if (version !== sessionVersion.value || token !== readToken()) return false
    session.user = user
    session.error = ''
    return true
  }).catch((error: unknown) => {
    if (error instanceof ApiError && error.status === 401) return false
    if (version === sessionVersion.value) session.error = errorMessage(error)
    throw error
  }).finally(() => {
    if (restoring === task) { restoring = null; session.restoring = false }
  })
  restoring = task
  return task
}

export async function logout(): Promise<void> {
  const version = sessionVersion.value
  try { await request('/auth/logout', { method: 'POST' }) }
  catch (error) {
    if (!(error instanceof ApiError && error.status === 401)) throw error
  }
  if (version === sessionVersion.value) clearSession()
}
