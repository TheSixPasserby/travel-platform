/**
 * 登录令牌只保存在 sessionStorage：关闭标签页即登出，
 * 不写入 localStorage，避免与其他会话长期共存。
 */
export const TOKEN_STORAGE_KEY = 'travel-notes.token'

export function readToken(): string | null {
  try {
    return sessionStorage.getItem(TOKEN_STORAGE_KEY)
  } catch {
    return null
  }
}

export function writeToken(token: string): void {
  try {
    sessionStorage.setItem(TOKEN_STORAGE_KEY, token)
  } catch {
    // 隐私模式等场景写入失败时保持内存态可用，不阻断登录流程。
  }
}

export function clearToken(): void {
  try {
    sessionStorage.removeItem(TOKEN_STORAGE_KEY)
  } catch {
    // 忽略存储不可用。
  }
}
