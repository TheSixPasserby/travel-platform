import type { Router } from 'vue-router'
import type { useAuthStore } from '@/stores/auth'
import { resolveRedirectTarget } from '@/utils/redirect'
import { setUnauthorizedHandler } from './client'

/** Guards run on navigation; a 401 on an already mounted protected page must also navigate. */
export function connectSessionErrors(auth: ReturnType<typeof useAuthStore>, router: Router): void {
  setUnauthorizedHandler(() => {
    auth.clearSession()
    const current = router.currentRoute.value
    if (current.meta.requiresAuth || current.meta.requiresAdmin) {
      void router.replace({ name: 'login', query: { redirect: resolveRedirectTarget(current.fullPath, '/me') } })
    }
  })
}
