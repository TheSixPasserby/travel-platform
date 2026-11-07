export const TOKEN_STORAGE_KEY = 'travel-notes.mobile.token'
const memory = new Map<string, string | null>()

export function readStorage(key: string): string | null {
  if (memory.has(key)) return memory.get(key) ?? null
  try {
    const value = uni.getStorageSync(key)
    return typeof value === 'string' && value ? value : null
  } catch {
    return null
  }
}

export function writeStorage(key: string, value: string | null): void {
  memory.set(key, value)
  try {
    if (value === null) uni.removeStorageSync(key)
    else uni.setStorageSync(key, value)
  } catch {
    // Keep this process's current session usable when storage is unavailable.
  }
}
