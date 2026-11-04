/**
 * 登录后回跳地址白名单校验。
 *
 * 只接受站内绝对路径，拒绝协议相对地址（//host）、反斜杠、控制字符、
 * 以及编码后的等价写法，避免开放重定向。默认回跳到个人资料页。
 */
export const DEFAULT_REDIRECT = '/me'

const CONTROL_CHARACTERS = /[\u0000-\u001F\u007F]/
const AUTH_ROUTES = /^\/(login|register)(?:[/?#]|$)/i
const ENCODED_DANGER = ['%2f%2f', '%5c', '%00', '%0d', '%0a', '%09']

export function resolveRedirectTarget(raw: unknown, fallback: string = DEFAULT_REDIRECT): string {
  if (typeof raw !== 'string') {
    return fallback
  }

  const target = raw.trim()
  if (target.length === 0 || !target.startsWith('/') || target.startsWith('//')) {
    return fallback
  }

  if (target.includes('\\') || CONTROL_CHARACTERS.test(target)) {
    return fallback
  }

  const normalized = target.toLowerCase()
  if (ENCODED_DANGER.some((fragment) => normalized.includes(fragment))) {
    return fallback
  }

  if (AUTH_ROUTES.test(target)) {
    return fallback
  }

  return target
}
