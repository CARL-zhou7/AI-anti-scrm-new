// 服务端时间轴估算。deadlineAt / readingUntil 都是服务端时间，前端只用估算的偏移量展示剩余时间、
// 判断何时停止播放；权威截止与终态始终以服务端为准（架构§11.5～11.6）。

const STORAGE_KEY = 'antiScam.sim.clock'

// offsetMs = 服务端时间 - 本机 Date.now()；uncertaintyMs 越小越可信
let offsetMs = 0
let uncertaintyMs = Number.POSITIVE_INFINITY

function load() {
  try {
    const saved = JSON.parse(sessionStorage.getItem(STORAGE_KEY) || 'null')
    if (saved && Number.isFinite(saved.offsetMs) && Number.isFinite(saved.uncertaintyMs)) {
      offsetMs = saved.offsetMs
      uncertaintyMs = saved.uncertaintyMs
    }
  } catch {
    // sessionStorage 不可用时只使用内存值
  }
}

function save() {
  try {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify({ offsetMs, uncertaintyMs }))
  } catch {
    // 忽略
  }
}

load()

function consider(serverMs, sentEpochMs, receivedEpochMs, resolutionMs) {
  if (!Number.isFinite(serverMs) || !(receivedEpochMs >= sentEpochMs)) return
  const rtt = receivedEpochMs - sentEpochMs
  const candidateUncertainty = rtt / 2 + resolutionMs
  if (candidateUncertainty >= uncertaintyMs) return
  offsetMs = serverMs + resolutionMs / 2 - (sentEpochMs + rtt / 2)
  uncertaintyMs = candidateUncertainty
  save()
}

/** 由 API-42 计时样本校准（毫秒精度）。 */
export function observeSample(serverSampledAtIso, sentEpochMs, receivedEpochMs) {
  consider(parseUtc(serverSampledAtIso), sentEpochMs, receivedEpochMs, 0)
}

/** 由 HTTP Date 响应头粗略校准（秒精度），仅在没有更好样本时生效。 */
export function observeDateHeader(dateHeader, sentEpochMs, receivedEpochMs) {
  if (!dateHeader) return
  consider(Date.parse(dateHeader), sentEpochMs, receivedEpochMs, 1000)
}

export function parseUtc(iso) {
  return iso ? Date.parse(iso) : Number.NaN
}

export function serverNow() {
  return Date.now() + offsetMs
}

/** 距离服务端某时刻的剩余毫秒，不小于 0；无效时间返回 null。 */
export function msUntil(iso) {
  const target = parseUtc(iso)
  if (!Number.isFinite(target)) return null
  return Math.max(0, target - serverNow())
}

export function clockQuality() {
  return { offsetMs, uncertaintyMs }
}

/** 仅供测试使用。 */
export function __resetClock() {
  offsetMs = 0
  uncertaintyMs = Number.POSITIVE_INFINITY
  try {
    sessionStorage.removeItem(STORAGE_KEY)
  } catch {
    // 忽略
  }
}
