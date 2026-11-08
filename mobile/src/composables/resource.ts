import { onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import { onHide, onShow } from '@dcloudio/uni-app'
import { sessionVersion, restoreSession } from '../services/session.ts'
import { errorMessage } from '../platform/errors.ts'
import { endpointVersion } from '../platform/url.ts'

/** Invalidate old requests and clear private content when a page hides or an account changes. */
export function useResource<T>(fetcher: () => Promise<T>, privateData = false) {
  const data = shallowRef<T | null>(null)
  const loading = ref(false)
  const error = ref('')
  const needsLogin = ref(false)
  let version = 0
  let visible = false

  function invalidate(): void {
    version++
    data.value = null
    error.value = ''
    loading.value = false
  }
  async function load(): Promise<void> {
    const request = ++version
    loading.value = true
    error.value = ''
    data.value = null
    needsLogin.value = false
    try {
      if (privateData) {
        const available = await restoreSession()
        if (request !== version) return
        if (!available) { needsLogin.value = true; return }
      }
      const result = await fetcher()
      if (request === version) data.value = result
    } catch (failure) {
      if (request === version) error.value = errorMessage(failure)
    } finally {
      if (request === version) loading.value = false
    }
  }
  onShow(() => { visible = true; void load() })
  onHide(() => { visible = false; invalidate() })
  onBeforeUnmount(invalidate)
  watch(sessionVersion, () => {
    if (!privateData) return
    invalidate()
    if (visible) void load()
  }, { flush: 'sync' })
  watch(endpointVersion, () => {
    invalidate()
    if (visible) void load()
  }, { flush: 'sync' })
  return { data, loading, error, needsLogin, load }
}
