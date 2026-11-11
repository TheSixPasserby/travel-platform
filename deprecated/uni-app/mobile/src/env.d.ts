/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** 后端完整地址，包含 /api/v1 前缀，例如 http://127.0.0.1:8080/api/v1 */
  readonly VITE_API_BASE_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

declare module '*.vue' {
  import type { DefineComponent } from 'vue'

  const component: DefineComponent<Record<string, unknown>, Record<string, unknown>, unknown>
  export default component
}
