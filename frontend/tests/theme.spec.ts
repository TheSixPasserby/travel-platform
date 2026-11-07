import { readFileSync } from 'node:fs'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, disposePinia, setActivePinia } from 'pinia'
import { mount } from '@vue/test-utils'

import { useThemeStore } from '@/stores/theme'
import ThemeSwitcher from '@/components/ThemeSwitcher.vue'

let dark = false
let change: (() => void) | undefined
let pinia: ReturnType<typeof createPinia>

beforeEach(() => {
  dark = false
  change = undefined
  vi.stubGlobal('matchMedia', vi.fn(() => ({
    get matches() { return dark },
    addEventListener: (_event: string, listener: () => void) => { change = listener },
    removeEventListener: vi.fn()
  })))
  pinia = createPinia()
  setActivePinia(pinia)
  document.documentElement.removeAttribute('data-theme')
})

afterEach(() => {
  disposePinia(pinia)
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

describe('三态主题', () => {
  it('默认跟随系统并响应操作系统变化', () => {
    const theme = useThemeStore()
    expect(theme.preference).toBe('system')
    expect(document.documentElement.dataset.theme).toBe('light')
    dark = true
    change?.()
    expect(document.documentElement.dataset.theme).toBe('dark')
    expect(document.documentElement.style.colorScheme).toBe('dark')
  })

  it('保存明确选择，系统变化不能覆盖浅色或深色', () => {
    const theme = useThemeStore()
    theme.setPreference('dark')
    expect(localStorage.getItem('travel-notes.theme')).toBe('dark')
    change?.()
    expect(document.documentElement.dataset.theme).toBe('dark')
    theme.setPreference('light')
    dark = true
    change?.()
    expect(document.documentElement.dataset.theme).toBe('light')
    theme.setPreference('system')
    expect(document.documentElement.dataset.theme).toBe('dark')
  })

  it('恢复持久选择，无效旧值回退系统', () => {
    localStorage.setItem('travel-notes.theme', 'dark')
    expect(useThemeStore().preference).toBe('dark')
    expect(document.documentElement.dataset.theme).toBe('dark')
  })

  it('忽略无效持久值并跟随系统', () => {
    localStorage.setItem('travel-notes.theme', 'unknown')
    expect(useThemeStore().preference).toBe('system')
  })

  it('存储被禁用时仍可切换内存主题', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => { throw new Error('blocked') })
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new Error('blocked') })
    const theme = useThemeStore()
    expect(() => theme.setPreference('dark')).not.toThrow()
    expect(document.documentElement.dataset.theme).toBe('dark')
  })

  it('同步其他标签页选择，清空存储恢复系统', () => {
    const theme = useThemeStore()
    window.dispatchEvent(new StorageEvent('storage', { key: 'travel-notes.theme', newValue: 'dark' }))
    expect(theme.preference).toBe('dark')
    window.dispatchEvent(new StorageEvent('storage', { key: null }))
    expect(theme.preference).toBe('system')
  })

  it('切换控件可用标签访问并提供三个选项', async () => {
    const wrapper = mount(ThemeSwitcher, { global: { plugins: [pinia] } })
    expect(wrapper.get('select').attributes('aria-label')).toBe('外观主题')
    expect(wrapper.findAll('option').map(option => option.element.value)).toEqual(['system', 'light', 'dark'])
    await wrapper.get('select').setValue('dark')
    expect(document.documentElement.dataset.theme).toBe('dark')
    wrapper.unmount()
  })
})

describe('首屏主题引导', () => {
  const bootstrap = readFileSync('public/theme.js', 'utf8')

  it.each(['dark', 'light', 'system', 'unknown', null])('应用挂载前解析保存值 %s', (preference) => {
    dark = true
    if (preference) localStorage.setItem('travel-notes.theme', preference)
    window.eval(bootstrap)
    expect(document.documentElement.dataset.theme).toBe(preference === 'light' ? 'light' : 'dark')
  })

  it('存储拒绝时依然从系统设置首屏深色', () => {
    dark = true
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => { throw new Error('blocked') })
    window.eval(bootstrap)
    expect(document.documentElement.dataset.theme).toBe('dark')
  })

  it('同步脚本在应用入口前执行', () => {
    const html = readFileSync('index.html', 'utf8')
    expect(html).toContain('<script src="/theme.js"></script>')
    expect(html.indexOf('/theme.js')).toBeLessThan(html.indexOf('/src/main.ts'))
  })
})
