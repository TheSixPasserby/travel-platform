import type { ApiResponse } from '../../../frontend/src/types/api.ts'
import { readToken, writeToken } from '../services/token.ts'
import { ApiError, NETWORK_ERROR_MESSAGE, SERVER_ERROR_MESSAGE, REQUEST_ERROR_MESSAGE } from './errors.ts'
import { endpointVersion, requestUrl } from './url.ts'

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE'
  query?: object
  data?: unknown
  auth?: boolean
}
let unauthorized: (() => void) | undefined
export function setUnauthorizedHandler(handler: () => void): void { unauthorized = handler }

/** A response may invalidate only the session that sent the request. */
export function unwrapResponse<T>(status: number, raw: unknown, token: string | null): T {
  const body = (raw && typeof raw === 'object' ? raw : {}) as Partial<ApiResponse<T>>
  if (status >= 200 && status < 300 && body.code === 0) return body.data as T
  if (status === 401 && token && token === readToken()) {
    writeToken(null)
    unauthorized?.()
  }
  const message = status >= 500 ? SERVER_ERROR_MESSAGE :
    typeof body.message === 'string' && body.message ? body.message : REQUEST_ERROR_MESSAGE
  throw new ApiError(message, { status, code: body.code })
}

export function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const endpoint = endpointVersion.value
  let url = requestUrl(path)
  if (options.query) {
    const query = Object.entries(options.query)
      .filter(([, value]) => value !== undefined && value !== null && value !== '')
      .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(String(value))}`).join('&')
    if (query) url += '?' + query
  }
  const token = options.auth === false ? null : readToken()
  return new Promise((resolve, reject) => {
    uni.request({
      url, method: options.method ?? 'GET', timeout: 15000,
      data: options.data as Record<string, unknown> | undefined,
      header: { 'Content-Type': 'application/json', ...(token ? { satoken: token } : {}) },
      success(response) {
        try {
          if (endpoint !== endpointVersion.value) throw new ApiError('服务器地址已更改，请重试')
          resolve(unwrapResponse<T>(response.statusCode, response.data, token))
        }
        catch (error) { reject(error) }
      },
      fail() {
        reject(endpoint !== endpointVersion.value
          ? new ApiError('服务器地址已更改，请重试')
          : new ApiError(NETWORK_ERROR_MESSAGE, { isNetworkError: true }))
      }
    })
  })
}
