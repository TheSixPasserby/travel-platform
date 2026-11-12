import { afterEach, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import ContentImage from '@/components/ContentImage.vue'
import ImageLightbox from '@/components/ImageLightbox.vue'

const src = '/uploads/2026/11/00000000000000000000000000000001.jpg'
afterEach(() => { vi.restoreAllMocks(); document.body.innerHTML = '' })

it('captures the actual thumbnail rectangle before the dialog opens', async () => {
  const wrapper = mount(ContentImage, { props: { src, alt: '山水' }, attachTo: document.body })
  vi.spyOn(wrapper.get('img').element, 'getBoundingClientRect').mockReturnValue({
    left: 30, top: 40, width: 240, height: 160, right: 270, bottom: 200, x: 30, y: 40, toJSON() {}
  })
  await wrapper.get('button').trigger('click')
  expect(wrapper.getComponent(ImageLightbox).props('preview')).toMatchObject({
    sourceRect: { left: 30, top: 40, width: 240, height: 160 }
  })
  wrapper.unmount()
})

it('route changes close a preview and restore the background scroll lock', async () => {
  const router = createRouter({ history: createMemoryHistory(), routes: [
    { path: '/', component: { template: '<div />' } },
    { path: '/next', component: { template: '<div />' } }
  ] })
  await router.push('/'); await router.isReady()
  const wrapper = mount(ContentImage, { props: { src, alt: '山水' }, attachTo: document.body, global: { plugins: [router] } })
  await wrapper.get('button').trigger('click')
  expect(document.body.style.overflow).toBe('hidden')
  await router.push('/next'); await flushPromises()
  expect(wrapper.findComponent(ImageLightbox).exists()).toBe(false)
  expect(document.body.style.overflow).toBe('')
  wrapper.unmount()
})

it('uses the clicked occurrence even when gallery entries share object identity', async () => {
  const entry = { src, alt: '沿途影像' }
  const wrapper = mount(ContentImage, { props: { src, alt: entry.alt, position: 2,
    images: [{ src: 'https://invalid.example/x.jpg', alt: '无效' }, entry, entry] }, attachTo: document.body })
  await wrapper.get('button').trigger('click')
  expect(wrapper.getComponent(ImageLightbox).props('preview').index).toBe(1)
  expect(wrapper.getComponent(ImageLightbox).props('preview').sourceRect).toBeUndefined()
  wrapper.unmount()
})
