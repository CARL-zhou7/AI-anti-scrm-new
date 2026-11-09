// The local default is the same Spring service targeted by the web proxy.
export const base = (import.meta.env?.VITE_MINIPROGRAM_API_BASE?.trim() || 'http://127.0.0.1:8100/anti.scam/v1').replace(/\/+$/, '')
const sessionKey = 'zhizha-session:' + base
let cookie
let csrf
let csrfRequest
let signedIn = false

export class ApiError extends Error {
  constructor(status, envelope) {
    super(envelope?.data?.fieldErrors?.map(field => field.message).join('；') || envelope?.message || '服务暂时不可用，请稍后重试')
    this.status = status
    this.code = envelope?.code
    this.data = envelope?.data
  }
}

function sessionCookie(response) {
  const header = Object.entries(response.header || {}).find(([key]) => key.toLowerCase() === 'set-cookie')?.[1]
  for (const value of [...(response.cookies || []), ...[].concat(header || [])].reverse()) {
    const match = /(?:^|,\s*)JSESSIONID=([^;,\s]*)/i.exec(value)
    if (match) return match[1] ? 'JSESSIONID=' + match[1] : ''
  }
}

function headers() {
  if (cookie === undefined) {
    cookie = uni.getStorageSync(sessionKey) || ''
    signedIn = Boolean(cookie)
  }
  return cookie ? { Cookie: cookie } : {}
}

function clearSession() {
  cookie = ''
  csrf = undefined
  signedIn = false
  uni.removeStorageSync(sessionKey)
  assets.clear()
}

async function request(path, method = 'GET', data, token) {
  let response
  try {
    response = await uni.request({
      url: base + path, method, data,
      header: { ...headers(), ...(token ? { 'X-CSRF-TOKEN': token, 'Content-Type': 'application/json' } : {}) },
      timeout: 15000
    })
  } catch {
    throw new ApiError(0, { message: '网络连接失败，请检查后端连接后重试' })
  }
  const updated = sessionCookie(response)
  if (updated !== undefined) {
    cookie = updated
    if (signedIn && cookie) uni.setStorageSync(sessionKey, cookie)
  }
  const body = response.data
  if (response.statusCode === 401 && body?.code === 'UNAUTHENTICATED' && path !== '/auth/login') {
    const redirect = signedIn && path !== '/auth/me'
    clearSession()
    if (redirect) uni.reLaunch({ url: '/pages/auth' })
  }
  if (response.statusCode >= 400 || body?.code !== 'OK') throw new ApiError(response.statusCode, body)
  return body.data
}

function token() {
  if (csrf) return Promise.resolve(csrf)
  if (!csrfRequest) {
    csrfRequest = request('/auth/csrf').then(value => { csrf = value.token; return csrf })
      .finally(() => { csrfRequest = undefined })
  }
  return csrfRequest
}

export const get = path => request(path)
export async function write(path, data, method = 'POST') {
  const current = await token()
  try {
    return await request(path, method, data, current)
  } catch (error) {
    if (error.code !== 'CSRF_INVALID') throw error
    if (csrf === current) csrf = undefined
    // A rejected CSRF check cannot have reached the controller.
    return request(path, method, data, await token())
  }
}

export async function login(data) {
  const account = await write('/auth/login', data)
  csrf = undefined
  if (account.role !== 'USER') {
    clearSession()
    throw new Error('请使用普通用户账号登录知诈')
  }
  if (!cookie) throw new Error('未收到登录会话，请检查小程序网络配置')
  signedIn = true
  uni.setStorageSync(sessionKey, cookie)
  return account
}

export async function logout() {
  await write('/auth/logout')
  clearSession()
}

const assets = new Map()
export function asset(path) {
  if (!path) return Promise.resolve('')
  if (!path.startsWith('/anti.scam/v1/')) return Promise.reject(new Error('素材地址无效'))
  if (!assets.has(path)) {
    const load = uni.downloadFile({ url: base + path.slice('/anti.scam/v1'.length), header: headers(), timeout: 10000 })
      .then(result => {
        if (result.statusCode !== 200) throw new Error('素材加载失败')
        return result.tempFilePath
      }).catch(error => { assets.delete(path); throw error })
    assets.set(path, load)
  }
  return assets.get(path)
}

export function withCursor(path, cursor) {
  return cursor ? path + '?cursor=' + encodeURIComponent(cursor) : path
}

export const runPath = (id, suffix = '') => '/simulations/' + encodeURIComponent(id) + suffix
export const pageUrl = (page, id) => '/pages/' + page + '?id=' + encodeURIComponent(id)
