import type { Router } from 'vue-router'
import type { useAuthStore } from '@/stores/auth'
import { resolveRedirectTarget } from '@/utils/redirect'
import { setUnauthorizedHandler } from './client'
export const EDITOR_SESSION_EXPIRED_EVENT = 'travel:editor-session-expired'

/** Guards run on navigation; a 401 on an already mounted protected page must also navigate. */
export function connectSessionErrors(auth: ReturnType<typeof useAuthStore>, router: Router): void {
  setUnauthorizedHandler(() => {
    auth.clearSession()
    const current = router.currentRoute.value
    // 已挂载的编辑器保留内存草稿；初次读取失败仍由编辑页显式跳转登录。
    if (current.name === 'guide-new' || current.name === 'guide-edit') {
      window.dispatchEvent(new Event(EDITOR_SESSION_EXPIRED_EVENT))
      return
    }
    if (current.meta.requiresAuth || current.meta.requiresAdmin) {
      void router.replace({ name: 'login', query: { redirect: resolveRedirectTarget(current.fullPath, '/me') } })
    }
  })
}
