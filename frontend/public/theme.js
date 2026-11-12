// One synchronous palette engine is shared by first paint and the Vue store.
(() => {
  const accentKey = 'travel-notes.accent.v1'
  const presets = [
    { id: 'pine', name: '山水绿', hex: '#187650' },
    { id: 'ocean', name: '海岸蓝', hex: '#3578C8' },
    { id: 'violet', name: '暮霞紫', hex: '#8064C8' },
    { id: 'sunset', name: '落日橙', hex: '#C87842' },
    { id: 'rose', name: '樱花粉', hex: '#C7658B' },
    { id: 'graphite', name: '石墨灰', hex: '#667487' }
  ]

  function normalizeAccent(value) {
    if (!value || typeof value !== 'object' || Array.isArray(value)) return null
    const keys = Object.keys(value)
    if (keys.length !== 1) return null
    if (keys[0] === 'preset' && presets.some(preset => preset.id === value.preset)) {
      return { preset: value.preset }
    }
    if (keys[0] === 'custom' && typeof value.custom === 'string' && /^#[0-9a-f]{6}$/i.test(value.custom)) {
      return { custom: value.custom.toUpperCase() }
    }
    return null
  }

  function readAccent(value) {
    try { return normalizeAccent(JSON.parse(value)) || { preset: 'pine' } }
    catch { return { preset: 'pine' } }
  }

  function readPreference(value) {
    return value === 'light' || value === 'dark' ? value : 'system'
  }

  function channels(hex) {
    return [1, 3, 5].map(index => parseInt(hex.slice(index, index + 2), 16))
  }

  function mix(base, tint, amount) {
    const target = channels(tint)
    return '#' + channels(base).map((value, index) =>
      Math.round(value + (target[index] - value) * amount).toString(16).padStart(2, '0')
    ).join('')
  }

  function luminance(hex) {
    const rgb = channels(hex).map(value => {
      const channel = value / 255
      return channel <= .04045 ? channel / 12.92 : ((channel + .055) / 1.055) ** 2.4
    })
    return rgb[0] * .2126 + rgb[1] * .7152 + rgb[2] * .0722
  }

  function contrast(first, second) {
    const a = luminance(first), b = luminance(second)
    return (Math.max(a, b) + .05) / (Math.min(a, b) + .05)
  }

  // Move only as far as needed. Check rounded RGB output, including tinted surfaces.
  function readable(seed, backgrounds, dark, minimum) {
    const target = dark ? '#ffffff' : '#000000'
    for (let step = 0; step <= 100; step++) {
      const candidate = mix(seed, target, step / 100)
      if (backgrounds.every(background => contrast(candidate, background) >= minimum)) return candidate
    }
    return target
  }

  function palette(seed, dark) {
    const page = mix(dark ? '#141819' : '#faf8f4', seed, .025)
    const surface = mix(dark ? '#1d2325' : '#ffffff', seed, dark ? .04 : .012)
    const elevated = mix(dark ? '#293032' : '#ffffff', seed, dark ? .04 : .006)
    const soft = mix(dark ? '#252e30' : '#f4f3ef', seed, dark ? .13 : .09)
    const backgrounds = [page, surface, elevated, soft]
    const accent = readable(seed, backgrounds, dark, 5)
    const text = dark ? '#edf0ee' : '#202a29'
    const secondary = readable(dark ? '#bac6c4' : '#52605e', backgrounds, dark, 4.5)
    const border = readable(mix(surface, text, .35), backgrounds, dark, 3)
    // At the black/white endpoints, move inward so interaction states stay distinct.
    const nearEndpoint = dark ? luminance(accent) > .8 : luminance(accent) < .02
    const normalTarget = dark ? '#ffffff' : '#000000'
    const inwardTarget = dark ? '#000000' : '#ffffff'
    const stateTarget = nearEndpoint ? inwardTarget : normalTarget
    return {
      'bg-page': page, 'bg-surface': surface, 'bg-elevated': elevated,
      'bg-muted': mix(page, seed, dark ? .07 : .045),
      'text-primary': text, 'text-secondary': secondary,
      'border-subtle': mix(surface, text, dark ? .18 : .16), 'border-strong': border,
      accent, 'accent-hover': mix(accent, stateTarget, .12),
      'accent-pressed': mix(accent, stateTarget, .23),
      'accent-soft': soft, 'accent-on': dark ? '#101819' : '#ffffff',
      'focus-ring': accent, link: accent, selection: soft,
      // Photography keeps a neutral canvas. Its controls need a light accent in both modes.
      'viewer-accent': readable(seed, ['#1e2428', '#121619'], true, 4.5),
      'viewer-on': '#101819', 'viewer-focus': readable(seed, ['#1e2428'], true, 4.5)
    }
  }

  function apply(preference, selection, systemDark) {
    const mode = preference === 'system' ? (systemDark ? 'dark' : 'light') : preference
    const accent = normalizeAccent(selection) || { preset: 'pine' }
    const seed = accent.custom || presets.find(preset => preset.id === accent.preset).hex
    const colors = palette(seed, mode === 'dark')
    const root = document.documentElement
    root.dataset.theme = mode
    root.dataset.accent = accent.preset || 'custom'
    root.style.colorScheme = mode
    for (const [name, color] of Object.entries(colors)) root.style.setProperty('--ui-' + name, color)
    root.style.setProperty('--ui-accent-seed', seed)
    document.querySelector('meta[name="theme-color"]')?.setAttribute('content', colors['bg-surface'])
  }

  window.shanshuiTheme = { accentKey, presets, normalizeAccent, readAccent, readPreference, apply }
  let preference = 'system', accent = { preset: 'pine' }
  try {
    preference = readPreference(localStorage.getItem('travel-notes.theme'))
    accent = readAccent(localStorage.getItem(accentKey))
  } catch { /* In-memory selection remains available when storage is blocked. */ }
  apply(preference, accent, window.matchMedia?.('(prefers-color-scheme: dark)').matches ?? false)
})()
