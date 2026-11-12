import { isManagedImageSource } from './markdown'

/** Download original bytes only from the same managed paths used by the renderer. */
export async function downloadManagedImage(src: string, signal?: AbortSignal): Promise<void> {
  if (!isManagedImageSource(src)) throw new Error('图片地址不受支持')
  const target = new URL(src, window.location.origin)
  if (target.origin !== window.location.origin) throw new Error('图片地址不受支持')
  const response = await fetch(target.href, {
    credentials: 'same-origin', redirect: 'error', signal
  })
  if (!response.ok || response.redirected) throw new Error('原图下载失败')
  if (response.url) {
    const actual = new URL(response.url)
    if (actual.origin !== target.origin || actual.pathname !== target.pathname || actual.search || actual.hash)
      throw new Error('图片地址不受支持')
  }
  const extension = src.slice(src.lastIndexOf('.') + 1)
  const expectedType = extension === 'jpg' ? 'image/jpeg' : `image/${extension}`
  const type = response.headers.get('content-type')?.split(';')[0]?.trim().toLowerCase()
  if (type !== expectedType) throw new Error('图片格式不受支持')
  const blob = await response.blob()
  signal?.throwIfAborted()
  if (!blob.size || blob.type.split(';')[0]?.toLowerCase() !== expectedType)
    throw new Error('图片内容无效')
  const objectUrl = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = objectUrl
  anchor.download = `shanshuijian-${src.slice(src.lastIndexOf('/') + 1)}`
  try {
    document.body.append(anchor)
    anchor.click()
  } finally {
    anchor.remove()
    // Give browsers time to start consuming the object URL before releasing it.
    setTimeout(() => URL.revokeObjectURL(objectUrl), 1000)
  }
}
