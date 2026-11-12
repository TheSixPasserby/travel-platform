import { computed, ref } from 'vue'

/** All distances are CSS pixels; scale is relative to the original image. */
export function useImageTransform() {
  const width = ref(0), height = ref(0), viewportWidth = ref(0), viewportHeight = ref(0)
  const scale = ref(1), rotation = ref(0), x = ref(0), y = ref(0)
  const ready = computed(() => width.value > 0 && height.value > 0)
  const turned = computed(() => Math.abs(rotation.value / 90) % 2 === 1)
  const rotatedWidth = computed(() => turned.value ? height.value : width.value)
  const rotatedHeight = computed(() => turned.value ? width.value : height.value)
  const fitScale = computed(() => ready.value && viewportWidth.value > 0 && viewportHeight.value > 0
    ? Math.min(1, viewportWidth.value / rotatedWidth.value, viewportHeight.value / rotatedHeight.value) : 1)
  const isFit = computed(() => Math.abs(scale.value - fitScale.value) < .00001)
  const percentage = computed(() => ready.value ? `${Math.round(scale.value * 1000) / 10}%` : '—')
  const bounds = computed(() => ({
    x: Math.max(0, (rotatedWidth.value * scale.value - viewportWidth.value) / 2),
    y: Math.max(0, (rotatedHeight.value * scale.value - viewportHeight.value) / 2)
  }))
  function panTo(nextX: number, nextY: number) {
    x.value = Math.max(-bounds.value.x, Math.min(bounds.value.x, nextX)) || 0
    y.value = Math.max(-bounds.value.y, Math.min(bounds.value.y, nextY)) || 0
  }
  function fit() { scale.value = fitScale.value; x.value = 0; y.value = 0 }
  function setViewport(w: number, h: number) {
    const fitting = isFit.value
    viewportWidth.value = Math.max(0, w); viewportHeight.value = Math.max(0, h)
    if (fitting) fit()
    else { scale.value = Math.max(fitScale.value, scale.value); panTo(x.value, y.value) }
  }
  function setImage(w: number, h: number) {
    width.value = w; height.value = h; rotation.value = 0; fit()
  }
  function reset() { width.value = 0; height.value = 0; rotation.value = 0; scale.value = 1; x.value = 0; y.value = 0 }
  // Anchor coordinates are measured from the viewport center. Uniform scaling
  // keeps this point fixed for every quarter-turn orientation.
  function zoomTo(value: number, anchorX = 0, anchorY = 0) {
    if (!ready.value || !Number.isFinite(value)) return
    const next = Math.min(4, Math.max(fitScale.value, value)), ratio = next / scale.value
    const nextX = anchorX - (anchorX - x.value) * ratio
    const nextY = anchorY - (anchorY - y.value) * ratio
    scale.value = next; panTo(nextX, nextY)
  }
  function step(direction: 1 | -1, anchorX = 0, anchorY = 0) {
    const steps = [fitScale.value, 1, 1.5, 2, 3, 4]
    const value = direction === 1 ? steps.find(v => v > scale.value + .00001)
      : steps.filter(v => v < scale.value - .00001).at(-1)
    if (value !== undefined) zoomTo(value, anchorX, anchorY)
  }
  function rotate(direction: 1 | -1) { rotation.value += direction * 90; fit() }
  return { width, height, viewportWidth, viewportHeight, scale, rotation, x, y, ready, fitScale, isFit, percentage, bounds,
    setViewport, setImage, reset, panTo, fit, zoomTo, step, rotate }
}
