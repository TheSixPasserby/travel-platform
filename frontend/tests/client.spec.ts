import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AxiosError, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'

import { apiClient, ApiError, setUnauthorizedHandler } from '@/api/client'
import { TOKEN_STORAGE_KEY } from '@/api/token'

type Handler = (config: InternalAxiosRequestConfig) => {
  status?: number
  data: unknown
}

const captured: InternalAxiosRequestConfig[] = []

/** axios 在适配器层拿到的是未拼接的 url 与 baseURL，这里按 axios 规则还原完整地址。 */
function requestUrl(config: InternalAxiosRequestConfig | undefined): string {
  return `${config?.baseURL ?? ''}${config?.url ?? ''}`
}

function installAdapter(handler: Handler) {
  captured.length = 0
  apiClient.defaults.adapter = async (config: InternalAxiosRequestConfig) => {
    captured.push(config)
    const result = handler(config)
    const response = {
      data: result.data,
      status: result.status ?? 200,
      statusText: 'OK',
      headers: {},
      config
    } as AxiosResponse
    if (response.status >= 400) {
      throw new AxiosError(
        `Request failed with status code ${response.status}`,
        'ERR_BAD_RESPONSE',
        config,
        undefined,
        response
      )
    }
    return response
  }
}

function installNetworkFailure() {
  captured.length = 0
  apiClient.defaults.adapter = async (config: InternalAxiosRequestConfig) => {
    captured.push(config)
    throw new AxiosError('Network Error', AxiosError.ERR_NETWORK, config)
  }
}

beforeEach(() => {
  setUnauthorizedHandler(null)
})

describe('接口客户端', () => {
  it('使用 /api/v1 前缀并解开统一响应结构', async () => {
    installAdapter(() => ({
      data: { code: 0, message: 'success', data: { id: '1', nickname: '小鹿' } }
    }))

    const data = await apiClient.get<{ id: string }>('/auth/me')

    expect(requestUrl(captured[0])).toBe('/api/v1/auth/me')
    expect(captured[0]?.method).toBe('get')
    expect(data).toEqual({ id: '1', nickname: '小鹿' })
  })

  it('已登录请求携带 satoken 请求头', async () => {
    sessionStorage.setItem(TOKEN_STORAGE_KEY, 'token-abc')
    installAdapter(() => ({ data: { code: 0, message: 'success', data: null } }))

    await apiClient.get('/users/me')

    const headers = captured[0]?.headers as Record<string, string>
    expect(headers.satoken).toBe('token-abc')
  })

  it('未登录请求不带 satoken 请求头', async () => {
    installAdapter(() => ({ data: { code: 0, message: 'success', data: null } }))

    await apiClient.get('/auth/me')

    const headers = captured[0]?.headers as Record<string, string>
    expect(headers.satoken).toBeUndefined()
  })

  it('业务码非 0 时抛出带字段错误的 ApiError', async () => {
    installAdapter(() => ({
      status: 400,
      data: {
        code: 40001,
        message: '请求参数有误',
        data: {
          fieldErrors: [
            { field: 'username', message: '账号格式不正确' },
            { field: 'password', message: '密码至少 8 个字符' }
          ]
        }
      }
    }))

    const error = await apiClient.post('/auth/register', {}).catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    const apiError = error as ApiError
    expect(apiError.code).toBe(40001)
    expect(apiError.status).toBe(400)
    expect(apiError.message).toBe('请求参数有误')
    expect(apiError.isNetworkError).toBe(false)
    expect(apiError.fieldErrors).toEqual([
      { field: 'username', message: '账号格式不正确' },
      { field: 'password', message: '密码至少 8 个字符' }
    ])
  })

  it('401 时清除本地令牌并通知会话失效', async () => {
    sessionStorage.setItem(TOKEN_STORAGE_KEY, 'expired-token')
    const onUnauthorized = vi.fn()
    setUnauthorizedHandler(onUnauthorized)
    installAdapter(() => ({
      status: 401,
      data: { code: 40101, message: '登录状态已失效，请重新登录', data: null }
    }))

    const error = await apiClient.get('/auth/me').catch((e: unknown) => e)

    expect((error as ApiError).status).toBe(401)
    expect((error as ApiError).code).toBe(40101)
    expect(sessionStorage.getItem(TOKEN_STORAGE_KEY)).toBeNull()
    expect(onUnauthorized).toHaveBeenCalledTimes(1)
  })

  it.each(['old-token',null])('旧会话或匿名请求的迟到401不能清除新会话：%s',async(sentToken)=>{
    if(sentToken)sessionStorage.setItem(TOKEN_STORAGE_KEY,sentToken)
    const onUnauthorized=vi.fn();setUnauthorizedHandler(onUnauthorized)
    let release!:()=>void,started!:()=>void
    const ready=new Promise<void>(resolve=>{started=resolve})
    apiClient.defaults.adapter=(config:InternalAxiosRequestConfig)=>new Promise((_,reject)=>{
      const response={data:{code:40101,message:'登录状态已失效',data:null},status:401,statusText:'Unauthorized',headers:{},config} as AxiosResponse
      release=()=>reject(new AxiosError('Unauthorized','ERR_BAD_RESPONSE',config,undefined,response));started()
    })
    const request=apiClient.get('/users/me').catch((error:unknown)=>error);await ready
    sessionStorage.setItem(TOKEN_STORAGE_KEY,'new-token');release();const error=await request
    expect((error as ApiError).status).toBe(401);expect(sessionStorage.getItem(TOKEN_STORAGE_KEY)).toBe('new-token');expect(onUnauthorized).not.toHaveBeenCalled()
  })

  it('网络失败标记为可重试并保留令牌', async () => {
    sessionStorage.setItem(TOKEN_STORAGE_KEY, 'token-abc')
    installNetworkFailure()

    const error = await apiClient.get('/auth/me').catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).isNetworkError).toBe(true)
    expect((error as ApiError).message).toContain('网络')
    expect(sessionStorage.getItem(TOKEN_STORAGE_KEY)).toBe('token-abc')
  })

  it('服务端错误使用通用提示且不泄漏内部信息', async () => {
    installAdapter(() => ({
      status: 500,
      data: { code: 50001, message: '服务器开小差了，请稍后再试', data: null }
    }))

    const error = await apiClient.get('/auth/me').catch((e: unknown) => e)

    expect((error as ApiError).status).toBe(500)
    expect((error as ApiError).code).toBe(50001)
    expect((error as ApiError).message).not.toMatch(/SQL|Exception|stack/i)
  })
})
