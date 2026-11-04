import axios, { AxiosError, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'

import type { ApiResponse, FieldError } from '@/types/api'
import { clearToken, readToken } from './token'

/** 统一的接口前缀，开发时由 Vite 代理到后端。 */
export const API_BASE_URL = '/api/v1'

export const NETWORK_ERROR_MESSAGE = '网络连接失败，请检查网络后重试'
export const SESSION_EXPIRED_MESSAGE = '登录状态已失效，请重新登录'
export const SERVER_ERROR_MESSAGE = '服务器开小差了，请稍后再试'
export const REQUEST_ERROR_MESSAGE = '请求未能完成，请稍后再试'

export interface ApiErrorOptions {
  status?: number
  code?: number
  isNetworkError?: boolean
  fieldErrors?: FieldError[]
}

/** 请求层唯一对外抛出的错误类型，区分「网络不可达」与「业务/会话错误」。 */
export class ApiError extends Error {
  readonly status?: number
  readonly code?: number
  readonly isNetworkError: boolean
  readonly fieldErrors: FieldError[]

  constructor(message: string, options: ApiErrorOptions = {}) {
    super(message)
    this.name = 'ApiError'
    this.status = options.status
    this.code = options.code
    this.isNetworkError = options.isNetworkError ?? false
    this.fieldErrors = options.fieldErrors ?? []
  }
}

type UnauthorizedHandler = () => void

let unauthorizedHandler: UnauthorizedHandler | null = null

/** 由应用入口注册：当前会话请求收到 401 时同步清理本地会话。 */
export function setUnauthorizedHandler(handler: UnauthorizedHandler | null): void {
  unauthorizedHandler = handler
}

export const apiClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: 15000,
  headers: { 'Content-Type': 'application/json' }
})

apiClient.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = readToken()
  if (token) {
    config.headers.set('satoken', token)
  }
  return config
})

apiClient.interceptors.response.use(
  (response: AxiosResponse<unknown>) => {
    const body = response.data as Partial<ApiResponse<unknown>> | null
    if (body && typeof body === 'object' && typeof body.code === 'number') {
      if (body.code !== 0) {
        throw new ApiError(typeof body.message === 'string' ? body.message : REQUEST_ERROR_MESSAGE, {
          status: response.status,
          code: body.code,
          fieldErrors: extractFieldErrors(body.data)
        })
      }
      return body.data as never
    }
    return response.data as never
  },
  (error: unknown) => {
    throw normalizeError(error)
  }
)

function normalizeError(error: unknown): ApiError {
  if (error instanceof ApiError) {
    return error
  }

  if (error instanceof AxiosError) {
    const response = error.response
    if (!response) {
      return new ApiError(NETWORK_ERROR_MESSAGE, { isNetworkError: true })
    }

    const status = response.status
    const body = (response.data ?? {}) as Partial<ApiResponse<unknown>>
    const code = typeof body.code === 'number' ? body.code : codeForStatus(status)

    const headers = error.config?.headers ?? response.config?.headers
    const requestToken = headers?.get?.('satoken') ?? headers?.satoken ?? null
    // A response from an earlier account (or an anonymous request) cannot clear a newer login.
    if (status === 401 && requestToken === readToken()) {
      clearToken()
      unauthorizedHandler?.()
    }

    const message =
      status === 401
        ? typeof body.message === 'string' && body.message.length > 0
          ? body.message
          : SESSION_EXPIRED_MESSAGE
        : sanitizeMessage(status, body.message)

    return new ApiError(message, {
      status,
      code,
      fieldErrors: extractFieldErrors(body.data)
    })
  }

  return new ApiError(REQUEST_ERROR_MESSAGE)
}

/** 5xx 一律使用通用文案，避免把服务端内部信息暴露给用户。 */
function sanitizeMessage(status: number, message: unknown): string {
  if (status >= 500) {
    return SERVER_ERROR_MESSAGE
  }
  if (typeof message === 'string' && message.trim().length > 0) {
    return message
  }
  return REQUEST_ERROR_MESSAGE
}

function codeForStatus(status: number): number {
  switch (status) {
    case 400:
      return 40001
    case 401:
      return 40101
    case 403:
      return 40301
    case 404:
      return 40401
    case 409:
      return 40901
    case 413:
      return 41301
    case 415:
      return 41501
    default:
      return 50001
  }
}

function extractFieldErrors(payload: unknown): FieldError[] {
  if (!payload || typeof payload !== 'object' || !('fieldErrors' in payload)) {
    return []
  }
  const raw = (payload as { fieldErrors?: unknown }).fieldErrors
  if (!Array.isArray(raw)) {
    return []
  }
  return raw.flatMap((item): FieldError[] => {
    if (!item || typeof item !== 'object') {
      return []
    }
    const { field, message } = item as { field?: unknown; message?: unknown }
    if (typeof field !== 'string' || typeof message !== 'string') {
      return []
    }
    return [{ field, message }]
  })
}
