// 统一的 HTTP 访问层：同源 Cookie Session + X-CSRF-TOKEN，解析 code/message/data/requestId 封套。
// 主工程若已有 axios 实例或 CSRF 管理，可通过 configureSimulationHttp 注入，模块本身不依赖第三方库。

import { observeDateHeader } from '../core/clock.js'

const settings = {
  baseUrl: '/anti.scam/v1',
  // 返回当前 CSRF token；未注入时模块自行调用 GET /auth/csrf 并缓存
  getCsrfToken: null,
  // 403 CSRF_INVALID 时刷新 token；未注入时重新调用 GET /auth/csrf
  refreshCsrfToken: null,
  // 401 时回调（例如跳转登录页）；模块内部不会无限重试
  onUnauthenticated: null,
  fetchImpl: null,
}

let cachedCsrfToken = null

export function configureSimulationHttp(options = {}) {
  Object.assign(settings, options)
  cachedCsrfToken = null
}

export function apiUrl(path) {
  // 服务端返回的 avatar.url / audioUrl 已带完整前缀
  if (path.startsWith(settings.baseUrl + '/') || /^https?:\/\//.test(path)) return path
  return settings.baseUrl + path
}

export class ApiError extends Error {
  constructor({ status = 0, code = 'NETWORK_ERROR', message = '网络连接异常', data = null, requestId = null, network = false }) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.data = data
    this.requestId = requestId
    // network=true 表示请求结果未知（可能已到达服务端），调用方须按幂等规则核对后再重发
    this.network = network
  }
}

function doFetch(url, init) {
  const impl = settings.fetchImpl || globalThis.fetch
  return impl(url, init)
}

async function fetchCsrfToken() {
  const res = await doFetch(apiUrl('/auth/csrf'), {
    method: 'GET',
    credentials: 'same-origin',
    headers: { Accept: 'application/json' },
    cache: 'no-store',
  })
  const body = await res.json()
  if (!res.ok || body.code !== 'OK') throw toApiError(res.status, body)
  cachedCsrfToken = body.data.token
  return cachedCsrfToken
}

export async function currentCsrfToken() {
  if (settings.getCsrfToken) {
    const injected = await settings.getCsrfToken()
    if (injected) return injected
  }
  return cachedCsrfToken || fetchCsrfToken()
}

async function refreshCsrfToken() {
  cachedCsrfToken = null
  if (settings.refreshCsrfToken) return settings.refreshCsrfToken()
  return fetchCsrfToken()
}

function toApiError(status, body) {
  return new ApiError({
    status,
    code: body?.code || 'HTTP_' + status,
    message: body?.message || '服务暂时无法完成请求',
    data: body?.data ?? null,
    requestId: body?.requestId ?? null,
  })
}

/**
 * 发送请求并返回封套中的 data。
 * @param {string} method
 * @param {string} path 不含前缀的路径，例如 /simulations/501
 * @param {object} [opts]
 * @param {object} [opts.body] JSON 请求体
 * @param {boolean} [opts.csrf] 写操作必须为 true
 * @param {boolean} [opts.retryOnCsrf] 仅幂等写操作允许在 CSRF 失效后刷新 token 重发一次
 * @param {boolean} [opts.keepalive] 页面关闭时尽力发送
 * @param {AbortSignal} [opts.signal]
 * @returns {Promise<{data: any, status: number}>}
 */
export async function request(method, path, opts = {}) {
  const { body, csrf = false, retryOnCsrf = false, keepalive = false, signal } = opts
  const headers = { Accept: 'application/json' }
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  if (csrf) headers['X-CSRF-TOKEN'] = await currentCsrfToken()

  const sentAt = Date.now()
  let res
  try {
    res = await doFetch(apiUrl(path), {
      method,
      credentials: 'same-origin',
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      cache: 'no-store',
      keepalive,
      signal,
    })
  } catch (err) {
    if (err?.name === 'AbortError') throw err
    throw new ApiError({ network: true })
  }
  observeDateHeader(res.headers?.get?.('Date'), sentAt, Date.now())

  let payload = null
  try {
    payload = await res.json()
  } catch {
    // 非 JSON（代理错误页等）按真实 HTTP 状态处理
  }

  if (res.ok && payload && payload.code === 'OK') return { data: payload.data, status: res.status }

  const error = toApiError(res.status, payload)
  if (res.status === 401) settings.onUnauthenticated?.(error)
  if (res.status === 403 && error.code === 'CSRF_INVALID' && retryOnCsrf) {
    await refreshCsrfToken()
    return request(method, path, { ...opts, retryOnCsrf: false })
  }
  throw error
}

/**
 * 页面关闭（pagehide）时同步发出的 keepalive 写请求：不能等待异步取 token，
 * 因此只使用已缓存或同步注入的 CSRF token；拿不到 token 时放弃发送（不会改用无法带头的 sendBeacon）。
 */
export function sendKeepalive(method, path, body) {
  let token = cachedCsrfToken
  if (settings.getCsrfToken) {
    const injected = settings.getCsrfToken()
    if (typeof injected === 'string' && injected) token = injected
  }
  if (!token) return false
  try {
    doFetch(apiUrl(path), {
      method,
      credentials: 'same-origin',
      headers: { Accept: 'application/json', 'Content-Type': 'application/json', 'X-CSRF-TOKEN': token },
      body: JSON.stringify(body),
      keepalive: true,
    }).catch(() => {})
    return true
  } catch {
    return false
  }
}

export function notifyUnauthenticated(error) {
  settings.onUnauthenticated?.(error)
}
