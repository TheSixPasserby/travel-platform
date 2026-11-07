/** Pure string validation also works in mini-programs without the browser URL API. */
export function normalizeApiBase(value: string, allowRelative = false): string {
  const base = value.replace(/\/$/, '')
  if (allowRelative && base === '/api/v1') return base
  if (!/^https?:\/\/(?:[a-z0-9.-]+|\[[a-f0-9:]+\])(?::[0-9]{1,5})?\/api\/v1$/i.test(base)) {
    throw new Error('请配置有效的 VITE_API_BASE_URL，完整地址以 /api/v1 结尾')
  }
  return base
}

let fallback = 'http://127.0.0.1:8080/api/v1'
let relativeAllowed = false
// #ifdef H5
fallback = '/api/v1'
relativeAllowed = true
// #endif
export const API_BASE_URL = normalizeApiBase(import.meta.env?.VITE_API_BASE_URL || fallback, relativeAllowed)

export function requestUrl(path: string): string {
  if (!/^\/[a-z0-9/_-]+$/i.test(path) || path.includes('//')) {
    throw new Error('请求路径无效')
  }
  return API_BASE_URL + path
}

export function imageUrl(path: string | null | undefined): string {
  if (!path || !/^\/uploads\/[0-9]{4}\/(0[1-9]|1[0-2])\/[a-f0-9]{32}\.(jpg|png|webp)$/.test(path)) return ''
  return API_BASE_URL.replace(/\/api\/v1$/, '') + path
}
