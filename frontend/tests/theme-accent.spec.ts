import { readFileSync } from 'node:fs'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, disposePinia, setActivePinia } from 'pinia'
import { useThemeStore } from '@/stores/theme'

const bootstrap = readFileSync('public/theme.js', 'utf8')
const accentKey = 'travel-notes.accent.v1'
let pinia: ReturnType<typeof createPinia>

beforeEach(() => {
  window.eval(bootstrap)
  pinia = createPinia()
  setActivePinia(pinia)
})
afterEach(() => {
  disposePinia(pinia)
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

function contrast(a: string, b: string): number {
  function luminance(hex: string): number {
    const rgb = [1, 3, 5].map(i => parseInt(hex.slice(i, i + 2), 16) / 255)
      .map(v => v <= .04045 ? v / 12.92 : ((v + .055) / 1.055) ** 2.4)
    return rgb[0]! * .2126 + rgb[1]! * .7152 + rgb[2]! * .0722
  }
  const values = [luminance(a), luminance(b)].sort((x, y) => y - x)
  return (values[0]! + .05) / (values[1]! + .05)
}

describe('全站色调', () => {
  it('默认山水绿，六个预设独立于明暗模式即时应用', () => {
    const theme = useThemeStore()
    expect(theme.accent).toEqual({ preset: 'pine' })
    theme.setPreference('dark')
    for (const preset of ['pine', 'ocean', 'violet', 'sunset', 'rose', 'graphite']) {
      expect(theme.setAccent({ preset })).toBe(true)
      expect(theme.accent).toEqual({ preset })
      expect(document.documentElement.dataset.accent).toBe(preset)
      expect(document.documentElement.dataset.theme).toBe('dark')
      expect(document.documentElement.style.getPropertyValue('--ui-bg-page')).toMatch(/^#[0-9a-f]{6}$/i)
    }
  })

  it.each(['#000000', '#FFFFFF', '#1a73e8'])('合法自定义 %s、持久化、恢复默认保留外观', hex => {
    const theme = useThemeStore()
    theme.setPreference('dark')
    expect(theme.setAccent({ custom: hex })).toBe(true)
    expect(theme.accent).toEqual({ custom: hex.toUpperCase() })
    expect(JSON.parse(localStorage.getItem(accentKey)!)).toEqual({ custom: hex.toUpperCase() })
    theme.resetAccent()
    expect(theme.accent).toEqual({ preset: 'pine' })
    expect(theme.preference).toBe('dark')
  })

  it.each(['#fff', '#12345678', 'red', '#gggggg', '#123456; background:red', '', null, {preset:'missing'}, {custom:'#abcdef',preset:'ocean'}, {custom:'#ffffff\n'}, {custom:'#ffffff\r\n'}])('拒绝不可信输入 %j', input => {
    const theme = useThemeStore()
    theme.setAccent({preset:'ocean'})
    expect(theme.setAccent(typeof input === 'string' ? {custom:input} : input)).toBe(false)
    expect(theme.accent).toEqual({preset:'ocean'})
  })

  it('恢复、跨标签页同步和清空分别保持两个状态域', () => {
    localStorage.setItem(accentKey, JSON.stringify({preset:'violet'}))
    const theme = useThemeStore()
    expect(theme.accent).toEqual({preset:'violet'})
    theme.setPreference('dark')
    window.dispatchEvent(new StorageEvent('storage',{key:accentKey,newValue:JSON.stringify({custom:'#AABBCC'})}))
    expect(theme.accent).toEqual({custom:'#AABBCC'})
    expect(theme.preference).toBe('dark')
    window.dispatchEvent(new StorageEvent('storage',{key:'travel-notes.theme',newValue:'light'}))
    expect(theme.accent).toEqual({custom:'#AABBCC'})
    window.dispatchEvent(new StorageEvent('storage',{key:accentKey,newValue:'invalid'}))
    expect(theme.accent).toEqual({preset:'pine'})
    window.dispatchEvent(new StorageEvent('storage',{key:null}))
    expect(theme.preference).toBe('system')
  })

  it('存储不可用时色调即时生效', () => {
    vi.spyOn(Storage.prototype,'getItem').mockImplementation(()=>{throw new Error('blocked')})
    vi.spyOn(Storage.prototype,'setItem').mockImplementation(()=>{throw new Error('blocked')})
    const theme = useThemeStore()
    expect(theme.setAccent({preset:'rose'})).toBe(true)
    expect(document.documentElement.dataset.accent).toBe('rose')
  })

  it('首屏脚本和挂载后的 store 产生相同调色板', () => {
    localStorage.setItem('travel-notes.theme','dark')
    localStorage.setItem(accentKey,JSON.stringify({custom:'#FFFFFF'}))
    window.eval(bootstrap)
    const before = document.documentElement.style.cssText
    useThemeStore()
    expect(document.documentElement.style.cssText).toBe(before)
    expect(document.documentElement.dataset.accent).toBe('custom')
  })

  it('各预设与极端自定义色的文字、按钮及焦点符合对比度', () => {
    const theme = useThemeStore()
    for (const mode of ['light','dark'] as const) {
      theme.setPreference(mode)
      for (const accent of [...['pine','ocean','violet','sunset','rose','graphite'].map(preset=>({preset})), ...['#000000','#FFFFFF','#FFFF00','#00FF00','#0000FF','#ff0000','#123456'].map(custom=>({custom}))]) {
        theme.setAccent(accent)
        const css = document.documentElement.style
        const value = (name:string) => css.getPropertyValue('--ui-'+name)
        for (const surface of ['bg-page','bg-surface','bg-elevated','accent-soft']) {
          for (const text of ['text-primary','text-secondary','link']) expect(contrast(value(text),value(surface)),`${mode} ${JSON.stringify(accent)} ${text} ${surface}`).toBeGreaterThanOrEqual(4.5)
          expect(contrast(value('focus-ring'),value(surface))).toBeGreaterThanOrEqual(3)
        }
        for (const state of ['accent','accent-hover','accent-pressed']) expect(contrast(value('accent-on'),value(state))).toBeGreaterThanOrEqual(4.5)
      }
    }
  })
  it.each([['light','#000000'],['dark','#FFFFFF']] as const)('极端色 %s %s 仍有悬停和按下反馈', (mode, custom) => {
    const theme = useThemeStore()
    theme.setPreference(mode)
    theme.setAccent({custom})
    const css = document.documentElement.style
    expect(new Set(['accent','accent-hover','accent-pressed'].map(name=>css.getPropertyValue('--ui-'+name))).size).toBe(3)
  })
  it('系统明暗变化保持色调，明确模式阻止系统覆盖', () => {
    let dark = false
    const listeners = new Set<() => void>()
    vi.stubGlobal('matchMedia', () => ({
      get matches() { return dark },
      addEventListener: (_: string, listener: () => void) => listeners.add(listener),
      removeEventListener: (_: string, listener: () => void) => listeners.delete(listener)
    }))
    const theme = useThemeStore()
    theme.setAccent({preset:'ocean'})
    dark = true
    listeners.forEach(listener=>listener())
    expect(document.documentElement.dataset.theme).toBe('dark')
    expect(theme.accent).toEqual({preset:'ocean'})
    expect(document.documentElement.style.getPropertyValue('--ui-accent-seed')).toBe('#3578C8')
    theme.setPreference('light')
    listeners.forEach(listener=>listener())
    expect(document.documentElement.dataset.theme).toBe('light')
    vi.unstubAllGlobals()
  })

  it('存储 getter 不可访问时忽略真实存储事件并保留内存选择', () => {
    const storage = window.localStorage
    vi.spyOn(window, 'localStorage', 'get').mockImplementation(() => { throw new Error('blocked') })
    const theme = useThemeStore()
    theme.setAccent({preset:'ocean'})
    const errors: string[] = []
    const recordError = (event: ErrorEvent) => { errors.push(event.message); event.preventDefault() }
    window.addEventListener('error', recordError)
    try {
      window.dispatchEvent(new StorageEvent('storage', {key:accentKey,newValue:JSON.stringify({preset:'rose'}),storageArea:storage}))
      expect(errors).toEqual([])
      expect(theme.accent).toEqual({preset:'ocean'})
    } finally { window.removeEventListener('error', recordError) }
  })

})
