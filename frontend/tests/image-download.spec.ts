import { afterEach, expect, it, vi } from 'vitest'
import { downloadManagedImage } from '@/utils/imageDownload'

const src = '/uploads/2026/11/00000000000000000000000000000001.jpg'
afterEach(() => { vi.restoreAllMocks(); vi.unstubAllGlobals(); vi.useRealTimers() })

it.each(['https://evil.example/a.jpg', '//evil.example/a.jpg', '/uploads/../secret', src + '?x=1', src + '#x', src.replace('00001.jpg', '00001.svg')])('rejects an unmanaged download before fetching: %s', async url => {
  const fetcher = vi.fn(); vi.stubGlobal('fetch', fetcher)
  await expect(downloadManagedImage(url)).rejects.toThrow()
  expect(fetcher).not.toHaveBeenCalled()
})

it('downloads the original bytes with a fixed safe filename and releases the blob', async () => {
  vi.useFakeTimers()
  const blob = new Blob(['original image'], { type: 'image/jpeg' })
  const fetcher = vi.fn().mockResolvedValue({ ok: true, redirected: false, url: location.origin + src, headers: new Headers({ 'content-type': 'image/jpeg' }), blob: async () => blob })
  vi.stubGlobal('fetch', fetcher)
  const create = vi.fn(() => 'blob:managed'), revoke = vi.fn()
  vi.stubGlobal('URL', class extends URL { static createObjectURL = create; static revokeObjectURL = revoke })
  let filename = ''
  vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) { filename = this.download })
  const controller = new AbortController()
  await downloadManagedImage(src, controller.signal)
  expect(fetcher).toHaveBeenCalledWith(location.origin + src, expect.objectContaining({ redirect: 'error', credentials: 'same-origin', signal: controller.signal }))
  expect(create).toHaveBeenCalledWith(blob)
  expect(filename).toBe('shanshuijian-00000000000000000000000000000001.jpg')
  expect(document.querySelector('a[download]')).toBeNull()
  await vi.runAllTimersAsync()
  expect(revoke).toHaveBeenCalledWith('blob:managed')
})

it.each([
  { ok: false },
  { redirected: true },
  { url: 'https://evil.example/x.jpg' },
  { url: location.origin + '/api/v1/private' },
  { mime: 'text/html' },
  { mime: 'image/svg+xml' },
  { mime: 'image/png' },
  { empty: true }
])('rejects failed, redirected, wrong type or empty responses: %j', async override => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, redirected: false, url: location.origin + src,
    headers: new Headers({ 'content-type': override.mime ?? 'image/jpeg' }),
    blob: async () => new Blob(override.empty ? [] : ['bytes'], { type: override.mime ?? 'image/jpeg' }), ...override }))
  await expect(downloadManagedImage(src)).rejects.toThrow()
})

it('propagates cancellation without triggering a browser download', async () => {
  const controller = new AbortController(); controller.abort()
  vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new DOMException('Aborted', 'AbortError')))
  const click = vi.spyOn(HTMLAnchorElement.prototype, 'click')
  await expect(downloadManagedImage(src, controller.signal)).rejects.toThrow()
  expect(click).not.toHaveBeenCalled()
})
