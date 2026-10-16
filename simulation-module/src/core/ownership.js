// 标记“本标签页发起并正在呈现的演绎”，用于区分三种进入演绎页的情况：
//   fresh    —— 本标签页刚刚开始的演绎，正常呈现
//   reloaded —— 本标签页此前已在呈现（刷新/恢复），按 PRD 属于退出，不续演
//   foreign  —— 不是本标签页发起的（其他标签页正在进行），本页不播放、不发送回执
// 使用 sessionStorage：同一标签页刷新后仍保留，不同标签页互不可见。

const PREFIX = 'antiScam.sim.owner.'

function read(id) {
  try {
    return sessionStorage.getItem(PREFIX + id)
  } catch {
    return null
  }
}

function write(id, value) {
  try {
    if (value === null) sessionStorage.removeItem(PREFIX + id)
    else sessionStorage.setItem(PREFIX + id, value)
  } catch {
    // sessionStorage 不可用时退化为仅本次页面实例有效
  }
}

/** 开始请求成功后由配置页调用。 */
export function markStarted(sessionId) {
  write(sessionId, 'started')
}

/** 演绎页挂载时调用，返回进入方式。 */
export function claimSession(sessionId) {
  const marker = read(sessionId)
  if (marker === 'started') {
    write(sessionId, 'presenting')
    return 'fresh'
  }
  if (marker === 'presenting') return 'reloaded'
  return 'foreign'
}

/** 演绎已终态或本页已离开时调用。 */
export function releaseSession(sessionId) {
  write(sessionId, null)
}
