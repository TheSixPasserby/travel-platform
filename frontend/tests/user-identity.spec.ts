import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import UserIdentity from '@/components/UserIdentity.vue'

describe('公开用户身份', () => {
  it('分别呈现昵称和唯一用户名，正文保持纯文本', () => {
    const w = mount(UserIdentity, { props: { user: { nickname: '<img src=x onerror=alert(1)>', username: 'walker', avatarUrl: null } } })
    expect(w.get('[data-test="identity-nickname"]').text()).toBe('<img src=x onerror=alert(1)>')
    expect(w.get('[data-test="identity-username"]').text()).toBe('@walker')
    expect(w.find('img').exists()).toBe(false)
  })
  it('异常空昵称回退用户名，旧 API 没有用户名也不捏造', () => {
    const w = mount(UserIdentity, { props: { user: { nickname: ' ', username: 'walker' } } })
    expect(w.get('[data-test="identity-nickname"]').text()).toBe('walker')
    w.unmount()
    const legacy = mount(UserIdentity, { props: { user: { nickname: '旧作者' } } })
    expect(legacy.find('[data-test="identity-username"]').exists()).toBe(false)
  })
})
