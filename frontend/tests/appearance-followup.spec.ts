import { afterEach, describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, disposePinia } from 'pinia'
import AppearanceSettings from '@/components/AppearanceSettings.vue'
import { useThemeStore } from '@/stores/theme'

const active: Array<() => void> = []
afterEach(() => { active.splice(0).forEach(cleanup => cleanup()) })
function render() {
  const pinia = createPinia()
  const wrapper = mount(AppearanceSettings, { global: { plugins: [pinia] } })
  active.push(() => { wrapper.unmount(); disposePinia(pinia) })
  return { wrapper, theme: useThemeStore(pinia) }
}
describe('显示设置审查回归', () => {
  it('非法输入后恢复默认应清除错误并显示默认色值', async () => {
    const { wrapper, theme } = render()
    theme.setPreference('dark')
    await wrapper.get('#appearance-hex').setValue('#bad')
    expect(wrapper.find('[role="alert"]').exists()).toBe(true)
    await wrapper.get('[data-test="reset-accent"]').trigger('click')
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    expect((wrapper.get('#appearance-hex').element as HTMLInputElement).value).toBe('#187650')
    expect(theme.preference).toBe('dark')
  })
  it('非法输入后选择预设应更新输入及选中状态', async () => {
    const { wrapper } = render()
    await wrapper.get('#appearance-hex').setValue('#bad')
    await wrapper.get('[data-accent-option="ocean"]').trigger('click')
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    expect((wrapper.get('#appearance-hex').element as HTMLInputElement).value).toBe('#3578C8')
    expect(wrapper.get('[data-accent-option="ocean"]').attributes('aria-pressed')).toBe('true')
  })
  it('颜色改变或默认恢复即使与已有状态相同也能清理非法草稿', async () => {
    const { wrapper } = render()
    await wrapper.get('#appearance-hex').setValue('invalid')
    await wrapper.get('[data-accent-option="pine"]').trigger('click')
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
  })
  it('六个预设可访问、即时持久化且保持外观', async () => {
    const { wrapper, theme } = render()
    theme.setPreference('dark')
    for (const preset of theme.presets) {
      const button = wrapper.get('[data-accent-option="' + preset.id + '"]')
      await button.trigger('click')
      expect(button.attributes('aria-pressed')).toBe('true')
      expect(button.text()).toContain(preset.name)
      expect(JSON.parse(localStorage.getItem('travel-notes.accent.v1')!)).toEqual({preset:preset.id})
      expect(theme.preference).toBe('dark')
    }
  })
  it.each(['#000000','#FFFFFF','#1a73e8'])('自定义 %s 输入即应用', async hex => {
    const { wrapper, theme } = render()
    await wrapper.get('#appearance-hex').setValue(hex)
    expect(theme.accent).toEqual({custom:hex.toUpperCase()})
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
  })
  it.each(['#fff','#12345678','red','#GGGGGG','#123456;display:none'])('非法 %s 保留已生效色调', async hex => {
    const { wrapper, theme } = render()
    await wrapper.get('[data-accent-option="ocean"]').trigger('click')
    await wrapper.get('#appearance-hex').setValue(hex)
    expect(theme.accent).toEqual({preset:'ocean'})
    expect(wrapper.get('#appearance-hex').attributes('aria-invalid')).toBe('true')
    expect(wrapper.get('[role="alert"]').text()).toContain('#RRGGBB')
  })
  it('取色器输入与跨标签选择同步，切换明暗保留正在输入的草稿', async () => {
    const { wrapper, theme } = render()
    await wrapper.get('input[type="color"]').setValue('#123456')
    await wrapper.get('input[type="color"]').trigger('input')
    expect(theme.accent).toEqual({custom:'#123456'})
    await wrapper.get('#appearance-hex').setValue('#bad')
    await wrapper.get('[data-theme-option="dark"]').trigger('click')
    expect((wrapper.get('#appearance-hex').element as HTMLInputElement).value).toBe('#bad')
    window.dispatchEvent(new StorageEvent('storage',{key:'travel-notes.accent.v1',newValue:JSON.stringify({preset:'violet'})}))
    await wrapper.vm.$nextTick()
    expect((wrapper.get('#appearance-hex').element as HTMLInputElement).value).toBe('#8064C8')
  })

})
