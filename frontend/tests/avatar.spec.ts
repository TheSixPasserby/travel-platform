import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'

import UserAvatar from '@/components/UserAvatar.vue'

describe('头像组件', () => {
  it('有头像时渲染受管路径图片并带有替代文本', () => {
    const wrapper = mount(UserAvatar, {
      props: { src: '/uploads/avatars/luna.jpg', name: '小鹿' }
    })

    const img = wrapper.get('img')
    expect(img.attributes('src')).toBe('/uploads/avatars/luna.jpg')
    expect(img.attributes('alt')).toBe('小鹿的头像')
  })

  it('没有头像时显示姓名首字占位', () => {
    const wrapper = mount(UserAvatar, { props: { src: null, name: '小鹿' } })

    expect(wrapper.find('img').exists()).toBe(false)
    expect(wrapper.get('[data-test="avatar-fallback"]').text()).toContain('小')
  })

  it('图片加载失败时回退到占位，不保留坏图', async () => {
    const wrapper = mount(UserAvatar, {
      props: { src: '/uploads/avatars/missing.jpg', name: '小鹿' }
    })

    await wrapper.get('img').trigger('error')

    expect(wrapper.find('img').exists()).toBe(false)
    expect(wrapper.get('[data-test="avatar-fallback"]').text()).toContain('小')
  })
})
