import { afterEach, describe, expect, it, vi } from 'vitest'
import { configureSimulationHttp, request } from '../src/api/http.js'

function response(status, body) {
  return { ok: status >= 200 && status < 300, status, headers: { get: () => null }, json: async () => body }
}

afterEach(() => configureSimulationHttp({ fetchImpl: null, getCsrfToken: null, refreshCsrfToken: null, onUnauthenticated: null }))

describe('request', () => {
  it('解析封套并在写操作上携带 X-CSRF-TOKEN', async () => {
    const fetchImpl = vi.fn(async () => response(200, { code: 'OK', message: '成功', data: { a: 1 }, requestId: 'r' }))
    configureSimulationHttp({ fetchImpl, getCsrfToken: () => 'tok' })
    const { data, status } = await request('POST', '/simulations/1/exit', { body: { reason: 'NAVIGATION' }, csrf: true })
    expect(data).toEqual({ a: 1 })
    expect(status).toBe(200)
    const [url, init] = fetchImpl.mock.calls[0]
    expect(url).toBe('/anti.scam/v1/simulations/1/exit')
    expect(init.headers['X-CSRF-TOKEN']).toBe('tok')
    expect(init.credentials).toBe('same-origin')
  })

  it('业务错误按 HTTP 状态与 code 抛出', async () => {
    configureSimulationHttp({ fetchImpl: async () => response(409, { code: 'STAGE_CONFLICT', message: '当前阶段不接受该呈现回执', data: null, requestId: 'r' }) })
    await expect(request('GET', '/x')).rejects.toMatchObject({ status: 409, code: 'STAGE_CONFLICT' })
  })

  it('CSRF 失效时（仅允许重试的请求）刷新令牌后重发一次', async () => {
    const fetchImpl = vi
      .fn()
      .mockResolvedValueOnce(response(403, { code: 'CSRF_INVALID', message: 'x', data: null, requestId: 'r' }))
      .mockResolvedValueOnce(response(200, { code: 'OK', message: '成功', data: 1, requestId: 'r' }))
    let token = 'old'
    configureSimulationHttp({ fetchImpl, getCsrfToken: () => token, refreshCsrfToken: async () => (token = 'new') })
    const { data } = await request('POST', '/y', { body: {}, csrf: true, retryOnCsrf: true })
    expect(data).toBe(1)
    expect(fetchImpl.mock.calls[1][1].headers['X-CSRF-TOKEN']).toBe('new')
  })

  it('401 通知上层且不重试；网络异常标记为结果未知', async () => {
    const onUnauthenticated = vi.fn()
    configureSimulationHttp({ fetchImpl: async () => response(401, { code: 'UNAUTHENTICATED', message: '请重新登录', data: null, requestId: 'r' }), onUnauthenticated })
    await expect(request('GET', '/z')).rejects.toMatchObject({ status: 401 })
    expect(onUnauthenticated).toHaveBeenCalledTimes(1)

    configureSimulationHttp({
      fetchImpl: async () => {
        throw new TypeError('Failed to fetch')
      },
    })
    await expect(request('GET', '/z')).rejects.toMatchObject({ network: true })
  })
})
