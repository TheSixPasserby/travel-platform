import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { watch } from 'vue'
import type { CurrentUser } from '../../frontend/src/types/auth.ts'
import { normalizeApiBase, requestUrl, imageUrl } from '../src/platform/url.ts'
import * as server from '../src/platform/url.ts'
import { readStorage, writeStorage } from '../src/platform/storage.ts'
import { readToken, writeToken } from '../src/services/token.ts'
import { request, unwrapResponse } from '../src/platform/request.ts'
import { session, sessionVersion, login, logout, restoreSession } from '../src/services/session.ts'
import { contentBlocks } from '../src/utils/content.ts'

interface PendingRequest {
  url: string
  header: Record<string, string>
  success: (response: { statusCode: number; data: unknown }) => void
  fail: () => void
}
const pending: PendingRequest[] = []
Object.assign(globalThis, { uni: {
  getStorageSync() { throw new Error('storage disabled') },
  setStorageSync() { throw new Error('storage disabled') },
  removeStorageSync() { throw new Error('storage disabled') },
  request(options: PendingRequest) { pending.push(options) }
} })
function take(): PendingRequest {
  const next = pending.shift()
  assert.ok(next, 'a transport request was expected')
  return next
}
function respond(call: PendingRequest, data: unknown, status = 200): void {
  call.success({ statusCode: status, data: { code: status < 300 ? 0 : status * 100 + 1, message: 'test', data } })
}
const user: CurrentUser = { id: '2', username: 'test', nickname: '旅人', avatarUrl: null, bio: null, role: 'USER', status: 'ACTIVE', createdAt: '' }
const currentUserId = () => session.user?.id

test('HTTPS base is canonical and contains no credentials or unrelated paths', () => {
  assert.equal(normalizeApiBase('https://travel.example/api/v1/'), 'https://travel.example/api/v1')
  for (const value of ['https://user:secret@travel.example/api/v1', 'javascript:alert(1)', '//evil.example/api/v1', 'https://travel.example/api/v1?key=x', 'https://travel.example/other', 'https://travel.example/api/v1/../admin']) {
    assert.throws(() => normalizeApiBase(value))
  }
  assert.throws(() => normalizeApiBase('/api/v1'))
  assert.equal(normalizeApiBase('/api/v1', true), '/api/v1')
  for (const value of ['http://host:0/api/v1', 'http://host:65536/api/v1', 'https://-bad.example/api/v1', 'https://a..example/api/v1', 'http://999.1.2.3/api/v1', 'http://[:::]/api/v1', 'http://[fe80::1%en0]/api/v1']) {
    assert.throws(() => normalizeApiBase(value), value)
  }
})

test('requests cannot send a session to an absolute or protocol-relative URL', () => {
  for (const path of ['https://evil.example', '//evil.example', '/guides/../auth', '/guides?redirect=bad']) assert.throws(() => requestUrl(path))
  assert.match(requestUrl('/guides/4'), /\/api\/v1\/guides\/4$/)
})

test('images accept managed paths and reject SVG, external and traversal addresses', () => {
  assert.match(imageUrl('/uploads/2026/11/' + 'a'.repeat(32) + '.jpg'), /\/uploads\//)
  for (const path of ['https://evil.example/pixel.jpg', '//evil.example/a', '/uploads/../file.jpg', '/uploads/2026/13/' + 'a'.repeat(32) + '.jpg', '/uploads/2026/11/' + 'a'.repeat(32) + '.svg']) assert.equal(imageUrl(path), '')
})

test('storage write and deletion retain the latest in-memory state when persistence fails', () => {
  writeStorage('test', 'value')
  assert.equal(readStorage('test'), 'value')
  writeStorage('test', null)
  assert.equal(readStorage('test'), null)
})

test('Markdown HTML is text and untrusted image locations never create image blocks', () => {
  const raw = '<script>alert(1)</script><img src=x onerror=alert(1)>'
  const blocks = contentBlocks(raw + '\n![outside](https://evil.example/a.jpg)\n# 山水间')
  assert.equal(blocks[0].text, raw)
  assert.equal(blocks[0].kind, 'text')
  assert.equal(blocks.filter(block => block.kind === 'image').length, 0)
  assert.equal(blocks[blocks.length - 1]?.kind, 'heading')
  const safe = contentBlocks('![旅行照片](/uploads/2026/11/' + 'a'.repeat(32) + '.webp)')
  assert.equal(safe[0].kind, 'image')
})

test('non-2xx response with code zero remains an error and 5xx detail is hidden', () => {
  assert.throws(() => unwrapResponse(500, { code: 0, message: 'SQL internal path' }, null), /服务暂时不可用/)
  assert.throws(() => unwrapResponse(200, { code: 40001, message: '参数错误' }, null), /参数错误/)
})

test('query encoding, public requests and authenticated satoken use the real contract', async () => {
  writeToken('session-a')
  const publicCall = request('/guides', { auth: false, query: { keyword: '山 & 水', page: 2 } })
  const publicTransport = take()
  assert.equal(publicTransport.header.satoken, undefined)
  assert.ok(publicTransport.url.endsWith('keyword=%E5%B1%B1%20%26%20%E6%B0%B4&page=2'))
  respond(publicTransport, { total: 0 })
  assert.deepEqual(await publicCall, { total: 0 })
  const privateCall = request('/itineraries')
  const privateTransport = take()
  assert.equal(privateTransport.header.satoken, 'session-a')
  respond(privateTransport, [])
  assert.deepEqual(await privateCall, [])
})

test('network failure preserves the session and allows retry', async () => {
  writeToken('session-a')
  const call = request('/itineraries')
  take().fail()
  await assert.rejects(call, /网络连接失败/)
  assert.equal(readToken(), 'session-a')
})

test('an old or public 401 cannot clear a newer account', () => {
  writeToken('session-b')
  assert.throws(() => unwrapResponse(401, { message: 'expired' }, 'session-a'))
  assert.equal(readToken(), 'session-b')
  assert.throws(() => unwrapResponse(401, {}, null))
  assert.equal(readToken(), 'session-b')
})

test('current session expiry clears both the token and private account context', () => {
  writeToken('expired-session')
  const version = sessionVersion.value
  assert.throws(() => unwrapResponse(401, {}, 'expired-session'))
  assert.equal(readToken(), null)
  assert.equal(session.user, null)
  assert.ok(sessionVersion.value > version)
})

test('concurrent restore is deduplicated and network failures retain the token', async () => {
  writeToken('restore-a')
  const first = restoreSession(), second = restoreSession()
  assert.equal(pending.length, 1)
  take().fail()
  await assert.rejects(first)
  await assert.rejects(second)
  assert.equal(readToken(), 'restore-a')
  const retry = restoreSession()
  respond(take(), user)
  assert.equal(await retry, true)
  assert.equal(currentUserId(), '2')
})

test('a restore finishing after another login cannot replace the new account', async () => {
  session.user = null
  writeToken('old-account')
  const restore = restoreSession(), old = take()
  const signingIn = login({ username: 'test', password: 'synthetic-password' })
  respond(take(), { tokenValue: 'new-account', user })
  await signingIn
  respond(old, { ...user, id: '1' })
  assert.equal(await restore, false)
  assert.equal(currentUserId(), '2')
  assert.equal(readToken(), 'new-account')
})

test('logout network failure preserves the account, a successful retry clears it', async () => {
  const first = logout()
  take().fail()
  await assert.rejects(first)
  assert.equal(readToken(), 'new-account')
  assert.equal(currentUserId(), '2')
  let observed: unknown
  const stop = watch(sessionVersion, () => { observed = { token: readToken(), user: session.user } }, { flush: 'sync' })
  const retry = logout()
  respond(take(), null)
  await retry
  assert.equal(readToken(), null)
  assert.equal(session.user, null)
  assert.deepEqual(observed, { token: null, user: null })
  stop()
})

test('all registered MVP pages exist and native manifests have no invented AppID', () => {
  const pages = JSON.parse(readFileSync('src/pages.json', 'utf8'))
  assert.equal(pages.pages.length, 10)
  for (const page of pages.pages) assert.ok(readFileSync('src/' + page.path + '.vue', 'utf8').length)
  const manifest = JSON.parse(readFileSync('src/manifest.json', 'utf8'))
  assert.equal(manifest.appid, '')
  assert.equal(manifest['mp-weixin'].appid, '')
  assert.equal(manifest['mp-weixin'].setting.urlCheck, true)
})

test('H5 manifest leaves the Vite command-line port effective', () => {
  const manifest = JSON.parse(readFileSync('src/manifest.json', 'utf8'))
  assert.equal(manifest.h5.devServer.port, undefined)
})

test('H5 dev server explicitly restricts filesystem access', () => {
  const config = readFileSync('vite.config.ts', 'utf8')
  assert.match(config, /fs:\s*\{\s*strict:\s*true\s*\}/)
})

test('saved server URL is persisted and invalid input cannot replace it', () => {
  const api = server as unknown as {
    saveApiBase: (value: string) => void
    getApiBase: () => string | null
    resetApiBase: () => void
  }
  assert.equal(typeof api.saveApiBase, 'function', 'runtime server configuration must be implemented')
  const originalSet = uni.setStorageSync, originalRemove = uni.removeStorageSync
  const persisted = new Map<string, unknown>()
  uni.setStorageSync = (key, value) => { persisted.set(key, value) }
  uni.removeStorageSync = key => { persisted.delete(key) }
  try {
    api.saveApiBase('https://first.example/api/v1')
    assert.equal(api.getApiBase(), 'https://first.example/api/v1')
    assert.equal(readStorage('travel-notes.mobile.api-base'), 'https://first.example/api/v1')
    assert.equal(persisted.get('travel-notes.mobile.api-base'), 'https://first.example/api/v1')
    assert.equal(requestUrl('/guides'), 'https://first.example/api/v1/guides')
    writeToken('still-this-server'); session.user = user
    api.saveApiBase('https://FIRST.example/api/v1/')
    assert.equal(readToken(), 'still-this-server')
    assert.equal(session.user?.id, user.id)
    assert.throws(() => api.saveApiBase('https://user:password@other.example/api/v1'))
    assert.equal(api.getApiBase(), 'https://first.example/api/v1')
    assert.equal(readToken(), 'still-this-server')
    api.resetApiBase()
    assert.equal(persisted.has('travel-notes.mobile.api-base'), false)
  } finally {
    uni.setStorageSync = originalSet; uni.removeStorageSync = originalRemove
  }
})

test('changing servers clears account state before a new request can send the previous token', async () => {
  const api = server as unknown as { saveApiBase: (value: string) => void; resetApiBase: () => void }
  assert.equal(typeof api.saveApiBase, 'function')
  api.saveApiBase('https://old.example/api/v1')
  writeToken('old-server-token'); session.user = user
  const version = sessionVersion.value
  api.saveApiBase('https://new.example/api/v1')
  assert.equal(readToken(), null)
  assert.equal(session.user, null)
  assert.ok(sessionVersion.value > version)
  const call = request('/guides')
  const transport = take()
  assert.equal(transport.url, 'https://new.example/api/v1/guides')
  assert.equal(transport.header.satoken, undefined)
  respond(transport, [])
  assert.deepEqual(await call, [])
  api.resetApiBase()
})

test('late old-server success and 401 cannot replace or clear a new server session', async () => {
  const api = server as unknown as { saveApiBase: (value: string) => void; resetApiBase: () => void }
  assert.equal(typeof api.saveApiBase, 'function')
  api.saveApiBase('https://old.example/api/v1')
  writeToken('same-token-value')
  const first = request('/guides', { auth: false }), firstTransport = take()
  const privateCall = request('/users/me'), privateTransport = take()
  const firstRejection = assert.rejects(first, /服务器.*更改|服务器.*切换/)
  const privateRejection = assert.rejects(privateCall, /服务器.*更改|服务器.*切换/)
  api.saveApiBase('https://new.example/api/v1')
  writeToken('same-token-value'); session.user = user
  respond(firstTransport, ['old public data'])
  respond(privateTransport, null, 401)
  await firstRejection; await privateRejection
  assert.equal(readToken(), 'same-token-value')
  assert.equal(session.user?.id, user.id)
  api.resetApiBase()
})

test('ten real pages include a reachable server settings page without changing AppID ownership', () => {
  const pages = JSON.parse(readFileSync('src/pages.json', 'utf8'))
  assert.equal(pages.pages.length, 10)
  assert.equal(pages.pages[0].path, 'pages/guides/index', 'H5 must retain the existing public landing page')
  assert.ok(pages.pages.some((page: { path: string }) => page.path === 'pages/settings/server'))
  assert.ok(readFileSync('src/pages/settings/server.vue', 'utf8').length)
})

test('unconfigured native settings resolve to no endpoint, while H5 can keep the same-origin default', () => {
  const api = server as unknown as { resolveApiBase: (saved: string | null, fallback: string | null, allowRelative?: boolean) => string | null }
  assert.equal(typeof api.resolveApiBase, 'function')
  assert.equal(api.resolveApiBase(null, null), null)
  assert.equal(api.resolveApiBase('javascript:alert(1)', null), null)
  assert.equal(api.resolveApiBase(null, '/api/v1', true), '/api/v1')
  assert.equal(api.resolveApiBase('https://saved.example/api/v1', '/api/v1', true), 'https://saved.example/api/v1')
  assert.equal(api.resolveApiBase(null, 'https://invalid.example/other', false), null)
})

test('settings accept a server origin and canonical valid IPv4 or IPv6 addresses without a Web URL API', () => {
  const api = server as unknown as { normalizeServerAddress: (value: string) => string }
  assert.equal(typeof api.normalizeServerAddress, 'function')
  assert.equal(api.normalizeServerAddress('  HTTPS://TRAVEL.Example:00443/  '), 'https://travel.example:443/api/v1')
  assert.equal(api.normalizeServerAddress('http://192.168.1.5:8080'), 'http://192.168.1.5:8080/api/v1')
  assert.equal(api.normalizeServerAddress('http://[::1]:8080'), 'http://[::1]:8080/api/v1')
  assert.equal(api.normalizeServerAddress('https://[2001:db8::8]'), 'https://[2001:db8::8]/api/v1')
  assert.equal(api.normalizeServerAddress('http://[::ffff:192.168.1.5]'), 'http://[::ffff:192.168.1.5]/api/v1')
  for (const invalid of ['http://host/other', 'http://[1:2:3:4:5:6:7:8:9]', 'http://[1::2::3]', 'http://bad-.example', 'http://host:65536']) assert.throws(() => api.normalizeServerAddress(invalid))
})
