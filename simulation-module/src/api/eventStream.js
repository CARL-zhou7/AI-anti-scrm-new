// API-16 事件流：原生 EventSource（同源 Cookie，不附加 token 或自定义头）。
// 浏览器自动重连期间不做任何业务判断；连接被关闭（如 401/404）时交给调用方核对登录态与权威状态，
// 不在 401 后无限重连（接口文档§5）。

export const SIMULATION_EVENT_TYPES = Object.freeze([
  'session.started',
  'session.state',
  'round.started',
  'message.saved',
  'analysis.saved',
  'round.reading',
  'session.finished',
  'session.interrupted',
  'report.updated',
])

/**
 * @param {string} url
 * @param {{onEvent: (type: string, payload: object) => void, onOpen?: () => void, onReconnecting?: () => void, onClosed?: () => void}} handlers
 */
export function openEventStream(url, { onEvent, onOpen, onReconnecting, onClosed }) {
  const source = new EventSource(url)
  let closed = false

  for (const type of SIMULATION_EVENT_TYPES) {
    source.addEventListener(type, (e) => {
      let payload
      try {
        payload = JSON.parse(e.data)
      } catch {
        return // 非法帧忽略，由 session.state / GET 核对兜底
      }
      onEvent(type, payload)
    })
  }
  source.onopen = () => onOpen?.()
  source.onerror = () => {
    if (closed) return
    if (source.readyState === EventSource.CLOSED) {
      closed = true
      onClosed?.()
    } else {
      onReconnecting?.()
    }
  }

  return {
    close() {
      closed = true
      source.close()
    },
  }
}
