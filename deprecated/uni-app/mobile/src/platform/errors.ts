import type { FieldError } from '@/types/api'

/** 统一请求错误：区分网络不可达、会话失效与业务/校验错误。 */
export class ApiError extends Error {
  readonly status: number | null
  readonly code: number | null
  readonly isNetworkError: boolean
  readonly fieldErrors: FieldError[]

  constructor(
    message: string,
    options: {
      status?: number | null
      code?: number | null
      isNetworkError?: boolean
      fieldErrors?: FieldError[]
    } = {}
  ) {
    super(message)
    this.name = 'ApiError'
    this.status = options.status ?? null
    this.code = options.code ?? null
    this.isNetworkError = options.isNetworkError ?? false
    this.fieldErrors = options.fieldErrors ?? []
  }
}

export const NETWORK_ERROR_MESSAGE = '网络连接失败，请检查网络后重试'
export const SERVER_ERROR_MESSAGE = '服务暂时不可用，请稍后再试'
export const SESSION_EXPIRED_MESSAGE = '登录状态已失效，请重新登录'
export const REQUEST_ERROR_MESSAGE = '请求未能完成，请稍后再试'

/** 供页面使用的错误文案，网络错误与业务错误区分展示。 */
export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    return error.message
  }
  if (error instanceof Error && error.message.length > 0) {
    return error.message
  }
  return REQUEST_ERROR_MESSAGE
}

export function isSessionError(error: unknown): boolean {
  return error instanceof ApiError && error.status === 401
}
