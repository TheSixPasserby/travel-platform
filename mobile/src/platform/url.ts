import { ref } from 'vue'
import { readStorage, writeStorage } from './storage.ts'
import { writeToken } from '../services/token.ts'

export const API_BASE_STORAGE_KEY = 'travel-notes.mobile.api-base'
export const endpointVersion = ref(0)

const invalidAddress = () => new Error('请输入有效的 HTTP(S) 服务器地址，以 /api/v1 结尾')

function validIpv4(host: string): boolean {
  const parts = host.split('.')
  return parts.length === 4 && parts.every(part => /^(0|[1-9][0-9]{0,2})$/.test(part) && Number(part) <= 255)
}

function validIpv6(host: string): boolean {
  let value = host
  if (value.includes('.')) {
    const split = value.lastIndexOf(':')
    const ipv4 = value.slice(split + 1)
    if (!validIpv4(ipv4)) return false
    const parts = ipv4.split('.').map(Number)
    value = value.slice(0, split + 1) + ((parts[0] << 8) | parts[1]).toString(16) + ':' + ((parts[2] << 8) | parts[3]).toString(16)
  }
  if (!/^[a-f0-9:]+$/i.test(value)) return false
  const compression = value.indexOf('::')
  if (compression !== -1 && compression !== value.lastIndexOf('::')) return false
  const sides = compression === -1 ? [value] : [value.slice(0, compression), value.slice(compression + 2)]
  const groups = sides.flatMap(side => side ? side.split(':') : [])
  return groups.every(group => /^[a-f0-9]{1,4}$/i.test(group)) &&
    (compression === -1 ? groups.length === 8 : groups.length < 8)
}

/** String-only validation works in mini-programs and native runtimes without the Web URL API. */
export function normalizeApiBase(value: string, allowRelative = false): string {
  const base = value.trim().replace(/\/$/, '')
  if (allowRelative && base === '/api/v1') return base
  const match = /^(https?):\/\/(\[[a-f0-9:.]+\]|[a-z0-9.-]+)(?::([0-9]{1,5}))?\/api\/v1$/i.exec(base)
  if (!match || base.length > 512) throw invalidAddress()
  const host = match[2], port = match[3] ? Number(match[3]) : null
  if (port !== null && (port < 1 || port > 65535)) throw invalidAddress()
  if (host.startsWith('[')) {
    if (!validIpv6(host.slice(1, -1))) throw invalidAddress()
  } else if (/^[0-9.]+$/.test(host)) {
    if (!validIpv4(host)) throw invalidAddress()
  } else if (host.length > 253 || !host.split('.').every(part => /^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$/i.test(part))) {
    throw invalidAddress()
  }
  return `${match[1].toLowerCase()}://${host.toLowerCase()}${port === null ? '' : ':' + port}/api/v1`
}

/** The settings form also accepts an origin and adds this platform's fixed API prefix. */
export function normalizeServerAddress(value: string): string {
  let base = value.trim().replace(/\/$/, '')
  if (/^https?:\/\/[^/]+$/i.test(base)) base += '/api/v1'
  return normalizeApiBase(base)
}

let fallback: string | null = null
let relativeAllowed = false
// #ifdef H5
fallback = '/api/v1'
relativeAllowed = true
// #endif

export function resolveApiBase(saved: string | null, defaultBase: string | null, allowRelative = false): string | null {
  if (saved) {
    try { return normalizeApiBase(saved) } catch { /* Ignore invalid historical storage without sending credentials there. */ }
  }
  if (defaultBase) {
    try { return normalizeApiBase(defaultBase, allowRelative) } catch { /* Invalid build configuration requires settings. */ }
  }
  return null
}

export function getApiBase(): string | null {
  return resolveApiBase(readStorage(API_BASE_STORAGE_KEY), import.meta.env?.VITE_API_BASE_URL || fallback, relativeAllowed)
}

export function saveApiBase(value: string): void {
  const next = normalizeServerAddress(value)
  if (next === getApiBase()) return
  // Clear the old credential before watchers can issue a request to the new endpoint.
  writeToken(null)
  writeStorage(API_BASE_STORAGE_KEY, next)
  endpointVersion.value++
}

export function resetApiBase(): void {
  if (readStorage(API_BASE_STORAGE_KEY) === null) return
  writeToken(null)
  writeStorage(API_BASE_STORAGE_KEY, null)
  endpointVersion.value++
}

export function hasApiBase(): boolean { return getApiBase() !== null }

export function requestUrl(path: string): string {
  if (!/^\/[a-z0-9/_-]+$/i.test(path) || path.includes('//')) {
    throw new Error('请求路径无效')
  }
  const base = getApiBase()
  if (!base) throw new Error('请先填写服务器地址')
  return base + path
}

export function imageUrl(path: string | null | undefined): string {
  if (!path || !/^\/uploads\/[0-9]{4}\/(0[1-9]|1[0-2])\/[a-f0-9]{32}\.(jpg|png|webp)$/.test(path)) return ''
  const base = getApiBase()
  return base ? base.replace(/\/api\/v1$/, '') + path : ''
}
