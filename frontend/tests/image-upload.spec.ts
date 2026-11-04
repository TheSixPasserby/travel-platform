import { describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

import { ApiError } from '@/api/client'
import { uploadImage } from '@/api/files'
import ImageUpload from '@/components/ImageUpload.vue'

vi.mock('@/api/files', () => ({
  uploadImage: vi.fn()
}))

const MAX_SIZE = 5 * 1024 * 1024
const ACCEPT = 'image/jpeg,image/png,image/webp'

function makeFile(options: { name?: string; type?: string; size?: number } = {}): File {
  const file = new File(['x'], options.name ?? 'photo.png', {
    type: options.type ?? 'image/png'
  })
  Object.defineProperty(file, 'size', { value: options.size ?? 2048 })
  return file
}

function mountUpload(props: Record<string, unknown> = {}) {
  return mount(ImageUpload, { props })
}

async function chooseFile(wrapper: ReturnType<typeof mountUpload>, file: File): Promise<void> {
  const input = wrapper.get('[data-test="image-upload-input"]')
  Object.defineProperty(input.element, 'files', { value: [file], configurable: true })
  await input.trigger('change')
}

function statusText(wrapper: ReturnType<typeof mountUpload>): string {
  const node = wrapper.find('[data-test="image-upload-status"]')
  return node.exists() ? node.text() : ''
}

describe('图片上传组件', () => {
  it('提供带说明的图片选择控件，限制类型与大小', () => {
    const wrapper = mountUpload()

    expect(wrapper.get('button[data-test="image-upload-trigger"]').text()).toContain('上传图片')
    expect(wrapper.get('label.field-label').text()).toContain('上传图片')

    const input = wrapper.get('input[data-test="image-upload-input"]')
    expect(input.attributes('type')).toBe('file')
    expect(input.attributes('accept')).toBe(ACCEPT)

    const hint = wrapper.get('[data-test="image-upload-hint"]').text()
    expect(hint).toContain('JPG')
    expect(hint).toContain('PNG')
    expect(hint).toContain('WebP')
    expect(hint).toContain('5MB')

    expect(wrapper.get('[data-test="image-upload-status"]').attributes('aria-live')).toBe('polite')
  })

  it('上传成功后才 emit 图片地址，busy 事件成对出现', async () => {
    vi.mocked(uploadImage).mockResolvedValue('/uploads/images/lake.webp')
    const wrapper = mountUpload()
    const file = makeFile({ name: 'lake.webp', type: 'image/webp', size: MAX_SIZE })

    await chooseFile(wrapper, file)
    await flushPromises()

    expect(uploadImage).toHaveBeenCalledTimes(1)
    expect(uploadImage).toHaveBeenCalledWith(file)
    expect(wrapper.emitted('update:modelValue')).toEqual([['/uploads/images/lake.webp']])
    expect(wrapper.emitted('busy')).toEqual([[true], [false]])
    expect(statusText(wrapper)).toContain('上传成功')
  })

  it('上传失败时保留当前地址、提示重试且不谎报成功', async () => {
    vi.mocked(uploadImage).mockRejectedValue(new ApiError('网络连接失败，请检查网络后重试', { isNetworkError: true }))
    const wrapper = mountUpload({ modelValue: '/uploads/images/old.jpg' })
    await chooseFile(wrapper, makeFile())
    await flushPromises()

    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    expect(wrapper.emitted('busy')).toEqual([[true], [false]])
    expect(wrapper.emitted('session-expired')).toBeUndefined()
    expect(statusText(wrapper)).toContain('重试')
    expect(statusText(wrapper)).not.toContain('上传成功')
    expect(wrapper.get('[data-test="image-upload-preview"]').attributes('src')).toBe(
      '/uploads/images/old.jpg'
    )
  })

  it('超过 5MB 的文件不发起请求并提示原因', async () => {
    const wrapper = mountUpload({ modelValue: '/uploads/images/old.jpg' })
    await chooseFile(wrapper, makeFile({ size: MAX_SIZE + 1 }))

    expect(uploadImage).not.toHaveBeenCalled()
    expect(wrapper.emitted('busy')).toBeUndefined()
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    expect(statusText(wrapper)).toContain('5MB')
    expect(wrapper.get('[data-test="image-upload-preview"]').attributes('src')).toBe(
      '/uploads/images/old.jpg'
    )
  })

  it('类型不在 JPG/PNG/WebP 内不发起请求', async () => {
    const wrapper = mountUpload()
    await chooseFile(wrapper, makeFile({ name: 'doc.gif', type: 'image/gif' }))

    expect(uploadImage).not.toHaveBeenCalled()
    expect(statusText(wrapper)).toContain('JPG/PNG/WebP')
  })

  it('401 时提示会话失效并通知父级处理，其他网络错误不通知', async () => {
    vi.mocked(uploadImage).mockRejectedValueOnce(
      new ApiError('登录状态已失效，请重新登录', { status: 401 })
    )
    const expired = mountUpload({ modelValue: '/uploads/images/old.jpg' })
    await chooseFile(expired, makeFile())
    await flushPromises()

    expect(expired.emitted('session-expired')).toHaveLength(1)
    expect(expired.emitted('update:modelValue')).toBeUndefined()
    expect(statusText(expired)).toContain('登录状态已失效')

    vi.mocked(uploadImage).mockRejectedValueOnce(new ApiError('网络连接失败', { isNetworkError: true }))
    const offline = mountUpload()
    await chooseFile(offline, makeFile())
    await flushPromises()

    expect(offline.emitted('session-expired')).toBeUndefined()
  })

  it('上传进行中再次选择文件不会重复上传，结束后重置文件输入', async () => {
    let resolveUpload: (url: string) => void = () => undefined
    vi.mocked(uploadImage).mockImplementation(
      () =>
        new Promise<string>((resolve) => {
          resolveUpload = resolve
        })
    )
    const wrapper = mountUpload()

    await chooseFile(wrapper, makeFile({ name: 'first.png' }))
    await chooseFile(wrapper, makeFile({ name: 'second.png' }))
    expect(uploadImage).toHaveBeenCalledTimes(1)

    resolveUpload('/uploads/images/first.png')
    await flushPromises()

    const input = wrapper.get('input[data-test="image-upload-input"]').element as HTMLInputElement
    expect(input.value).toBe('')
  })

  it('失败后重置文件输入，同一文件可以重试', async () => {
    vi.mocked(uploadImage).mockRejectedValueOnce(new Error('boom'))
    const wrapper = mountUpload()
    const file = makeFile({ name: 'same.png' })

    await chooseFile(wrapper, file)
    await flushPromises()

    const input = wrapper.get('input[data-test="image-upload-input"]').element as HTMLInputElement
    expect(input.value).toBe('')
    expect(statusText(wrapper)).toContain('重试')

    vi.mocked(uploadImage).mockResolvedValueOnce('/uploads/images/same.png')
    await chooseFile(wrapper, file)
    await flushPromises()

    expect(uploadImage).toHaveBeenCalledTimes(2)
    expect(wrapper.emitted('update:modelValue')).toEqual([['/uploads/images/same.png']])
  })

  it('预览图加载失败时回退为文字占位', async () => {
    const wrapper = mountUpload({ modelValue: '/uploads/images/broken.jpg' })

    await wrapper.get('[data-test="image-upload-preview"]').trigger('error')

    expect(wrapper.find('[data-test="image-upload-preview"]').exists()).toBe(false)
    expect(wrapper.get('[data-test="image-upload-preview-fallback"]').text()).toContain('预览加载失败')
  })

  it('showPreview 为 false 时不渲染额外缩略图', () => {
    const wrapper = mountUpload({ modelValue: '/uploads/images/a.webp', showPreview: false })

    expect(wrapper.find('[data-test="image-upload-preview"]').exists()).toBe(false)
    expect(wrapper.find('img').exists()).toBe(false)
    // 移除能力不依赖缩略图，Profile 场景仍可清空头像
    expect(wrapper.find('[data-test="image-upload-remove"]').exists()).toBe(true)
  })

  it('有当前地址时可移除并 emit null', async () => {
    const wrapper = mountUpload({ modelValue: '/uploads/images/a.webp' })

    await wrapper.get('[data-test="image-upload-remove"]').trigger('click')

    expect(wrapper.emitted('update:modelValue')).toEqual([[null]])
  })

  it('disabled 时控件不可操作', () => {
    const wrapper = mountUpload({ disabled: true })

    expect(wrapper.get('button[data-test="image-upload-trigger"]').attributes('disabled')).toBeDefined()
    expect(wrapper.get('input[data-test="image-upload-input"]').attributes('disabled')).toBeDefined()
  })
})
