import { afterEach, expect, it } from 'vitest'
import { AxiosError } from 'axios'
import { apiClient, SERVER_ERROR_MESSAGE } from '@/api/client'

const previousAdapter = apiClient.defaults.adapter
afterEach(() => { apiClient.defaults.adapter = previousAdapter })

it.each([
  [503, 50310, 'AI服务暂未配置或启用，请使用手动行程'],
  [504, 50410, 'AI请求超时，已保留当前编辑内容'],
  [502, 50210, 'AI服务调用失败，请稍后重试'],
  [502, 50211, 'AI结果未通过校验，请调整需求后重新生成'],
])('AI 错误 %s/%s 显示固定业务原因并隐藏响应中的内部信息', async (status, code, message) => {
  await expect(apiClient.post('/ai/itineraries/preview', {}, {
    adapter: async config => {
      throw new AxiosError('Internal failure', undefined, config, undefined, {
        status: Number(status), statusText: 'Failed', config, headers: {},
        data: { code, message: '/private/internal-path SYNTHETIC_SECRET', data: null },
      })
    },
  })).rejects.toMatchObject({ status, code, message })
})

it('未知服务端错误继续使用通用文案', async () => {
  await expect(apiClient.get('/synthetic', {
    adapter: async config => {
      throw new AxiosError('Internal failure', undefined, config, undefined, {
        status: 500, statusText: 'Failed', config, headers: {},
        data: { code: 50001, message: '/private/internal-path SYNTHETIC_SECRET', data: null },
      })
    },
  })).rejects.toMatchObject({ message: SERVER_ERROR_MESSAGE })
})
