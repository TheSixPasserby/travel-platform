import { ApiError, NETWORK_ERROR_MESSAGE } from './errors.ts'
import { requestUrl } from './url.ts'
import { readToken } from '../services/token.ts'
import { unwrapResponse } from './request.ts'

/** Shared adapter for a later editor. No credentials go to arbitrary upload origins. */
export function uploadImage(filePath: string): Promise<string> {
  const token = readToken()
  return new Promise((resolve, reject) => {
    uni.uploadFile({
      url: requestUrl('/files/images'), filePath, name: 'file', timeout: 30000,
      header: token ? { satoken: token } : {},
      success(response) {
        try { resolve(unwrapResponse<{ url: string }>(response.statusCode, JSON.parse(response.data), token).url) }
        catch (error) { reject(error instanceof ApiError ? error : new ApiError('上传响应无效，请重试')) }
      },
      fail() { reject(new ApiError(NETWORK_ERROR_MESSAGE, { isNetworkError: true })) }
    })
  })
}
