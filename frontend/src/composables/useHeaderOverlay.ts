import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
export type HeaderPanel = 'navigation' | 'settings' | 'account'

export function useHeaderOverlay() {
  const active = ref<HeaderPanel | null>(null), panel = ref<HTMLElement | null>(null)
  let trigger: HTMLElement | null = null, locked = false, overflow = ''
  let inertElements: Array<[HTMLElement, boolean]> = []
  function lock() {
    if (locked) return
    locked = true; overflow = document.body.style.overflow; document.body.style.overflow = 'hidden'
    inertElements = [...document.querySelectorAll<HTMLElement>('main, footer')].map(element => [element, element.inert])
    for (const [element] of inertElements) element.inert = true
  }
  function unlock() {
    if (!locked) return
    document.body.style.overflow = overflow
    for (const [element, inert] of inertElements) element.inert = inert
    inertElements = []; locked = false
  }
  function close(restoreFocus = true) {
    if (!active.value) return
    const previousTrigger = trigger
    trigger = null
    active.value = null; unlock()
    if (restoreFocus && previousTrigger?.isConnected) previousTrigger.focus()
  }
  function toggle(name: HeaderPanel, event: MouseEvent) {
    if (active.value === name) { close(); return }
    trigger = event.currentTarget as HTMLElement; active.value = name
  }
  function focusable() { return [...(panel.value?.querySelectorAll<HTMLElement>('a[href],button:not(:disabled),summary,[tabindex="0"]') ?? [])] }
  function keyboard(event: KeyboardEvent) {
    if (!active.value) return
    if (event.key === 'Escape') { event.preventDefault(); close() }
    if (event.key !== 'Tab') return
    const elements = focusable(), first = elements[0], last = elements.at(-1)
    if (!first || !last) { event.preventDefault(); panel.value?.focus(); return }
    if (!elements.includes(document.activeElement as HTMLElement) || (event.shiftKey && document.activeElement === first)) {
      event.preventDefault(); (event.shiftKey ? last : first).focus()
    } else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus() }
  }
  function outside(event: PointerEvent) {
    const target = event.target
    if (active.value && target instanceof Element && !panel.value?.contains(target) && !target.closest('[data-overlay-trigger]')) close()
  }
  watch(active, async value => {
    if (!value) return
    lock(); await nextTick()
    if (active.value === value) (focusable()[0] ?? panel.value)?.focus()
  })
  onMounted(() => { window.addEventListener('keydown', keyboard); document.addEventListener('pointerdown', outside) })
  onBeforeUnmount(() => { window.removeEventListener('keydown', keyboard); document.removeEventListener('pointerdown', outside); unlock() })
  return { active, panel, toggle, close }
}
