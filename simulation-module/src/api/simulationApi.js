// AI情景演绎相关接口（接口文档 API-05、14～21、42）。只做传输，不含业务判断。

import { request, apiUrl, sendKeepalive, currentCsrfToken } from './http.js'

const enc = encodeURIComponent

export const simulationApi = {
  /** API-05 当前账号；用于 SSE 异常时确认登录态 */
  getMe() {
    return request('GET', '/auth/me').then((r) => r.data)
  },

  /** API-42 点击计时采样 */
  getTimingSample() {
    return request('GET', '/timing/samples').then((r) => r.data)
  },

  /**
   * API-14 开始演绎。201 为新建，200 为同一 startRequestId 的幂等结果。
   * @returns {Promise<{state: object, created: boolean}>}
   */
  startSimulation(startRequest) {
    // 开始请求按 startRequestId 幂等，CSRF 刷新后允许原样重发
    return request('POST', '/simulations', { body: startRequest, csrf: true, retryOnCsrf: true }).then((r) => ({
      state: r.data,
      created: r.status === 201,
    }))
  },

  /** API-15 当前演绎状态 */
  getSimulation(id) {
    return request('GET', `/simulations/${enc(id)}`).then((r) => r.data)
  },

  /** API-16 事件流地址（原生 EventSource，同源 Cookie，不附加 token） */
  eventsUrl(id) {
    return apiUrl(`/simulations/${enc(id)}/events`)
  },

  /** API-17 开场呈现回执 */
  presentOpening(id, receipt) {
    return request('POST', `/simulations/${enc(id)}/opening/presented`, { body: receipt, csrf: true, retryOnCsrf: true }).then(
      (r) => r.data,
    )
  },

  /** API-18 消息呈现回执 */
  presentMessage(id, messageId, receipt) {
    return request('POST', `/simulations/${enc(id)}/messages/${enc(messageId)}/presentation`, {
      body: receipt,
      csrf: true,
      retryOnCsrf: true,
    }).then((r) => r.data)
  },

  /** API-19 分析呈现回执 */
  presentAnalysis(id, roundNo, receipt) {
    return request('POST', `/simulations/${enc(id)}/rounds/${enc(roundNo)}/analysis/presented`, {
      body: receipt,
      csrf: true,
      retryOnCsrf: true,
    }).then((r) => r.data)
  },

  /** API-20 主动退出；keepalive 用于 pagehide 时尽力送达（仍携带 CSRF 头） */
  exitSimulation(id, reason, { keepalive = false } = {}) {
    return request('POST', `/simulations/${enc(id)}/exit`, {
      body: { reason },
      csrf: true,
      retryOnCsrf: !keepalive,
      keepalive,
    }).then((r) => r.data)
  },

  /** pagehide 时尽力通知退出（同步发出，携带已缓存的 CSRF token） */
  exitOnPageHide(id, reason) {
    return sendKeepalive('POST', `/simulations/${enc(id)}/exit`, { reason })
  },

  /** 预先取得 CSRF token，保证页面关闭时能同步带上 */
  warmUpCsrf() {
    return currentCsrfToken().catch(() => null)
  },

  /** API-21 临时语音地址；服务端在 message.audioUrl 中直接给出，此处仅作兜底拼接 */
  audioUrl(id, messageId) {
    return apiUrl(`/simulations/${enc(id)}/messages/${enc(messageId)}/audio`)
  },
}
