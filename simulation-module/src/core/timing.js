// 点击计时协议（接口文档§9.1，已冻结）：配置页预先取得 API-42 样本，记录发送/接收的单调时钟；
// 用户点击“开始演绎”时记录点击单调时钟，三者随 StartRequest.timing 提交。

import { observeSample, parseUtc, serverNow } from './clock.js'

export class TimingSampleUnavailableError extends Error {
  constructor(message = '计时样本已过期，请重新点击开始') {
    super(message)
    this.name = 'TimingSampleUnavailableError'
  }
}

const round3 = (n) => Math.round(n * 1000) / 1000

/**
 * @param {{getTimingSample: () => Promise<object>}} api
 * @param {{now?: () => number, safetyMarginMs?: number}} [opts]
 */
export function createTimingSampler(api, opts = {}) {
  const now = opts.now || (() => performance.now())
  // 样本到期前预留的余量：保证提交到达服务端时样本仍有效
  const safetyMarginMs = opts.safetyMarginMs ?? 5000

  let current = null // { sample, sendMs, receiveMs }
  let inflight = null
  let refreshTimer = null
  let stopped = false

  async function refresh() {
    if (inflight) return inflight
    inflight = (async () => {
      const sendMs = now()
      const sendEpoch = Date.now()
      const sample = await api.getTimingSample()
      const receiveMs = now()
      observeSample(sample.serverSampledAt, sendEpoch, Date.now())
      current = { sample, sendMs, receiveMs }
      scheduleRefresh()
      return current
    })().finally(() => {
      inflight = null
    })
    return inflight
  }

  function scheduleRefresh() {
    clearTimeout(refreshTimer)
    if (stopped || !current) return
    const { sample } = current
    const lifetime = parseUtc(sample.expiresAt) - parseUtc(sample.serverSampledAt)
    // 在有效期过半时换新样本，避免用户点击时样本恰好过期
    const delay = Math.max(1000, Math.min(lifetime / 2, parseUtc(sample.expiresAt) - serverNow() - safetyMarginMs))
    refreshTimer = setTimeout(() => {
      refresh().catch(() => {
        // 刷新失败时保留旧样本，点击时再判断是否可用
      })
    }, delay)
  }

  function isUsable(at = current) {
    if (!at) return false
    return parseUtc(at.sample.expiresAt) - serverNow() > safetyMarginMs
  }

  return {
    refresh,
    get ready() {
      return isUsable()
    },
    /**
     * 生成 TimingEvidence。clickMs 必须是点击事件处理函数中第一时间取得的单调时钟值。
     * 不允许点击之后再重新采样（否则违反 发送 ≤ 接收 ≤ 点击）。
     */
    buildEvidence(clickMs) {
      if (!isUsable()) throw new TimingSampleUnavailableError()
      const { sample, sendMs, receiveMs } = current
      if (!(sendMs <= receiveMs && receiveMs <= clickMs)) throw new TimingSampleUnavailableError()
      return {
        methodVersion: sample.methodVersion,
        sampleId: sample.sampleId,
        clientSendMonotonicMs: round3(sendMs),
        clientReceiveMonotonicMs: round3(receiveMs),
        clientClickMonotonicMs: round3(clickMs),
      }
    },
    stop() {
      stopped = true
      clearTimeout(refreshTimer)
    },
  }
}

export function createUuid() {
  if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID()
  const bytes = new Uint8Array(16)
  globalThis.crypto.getRandomValues(bytes)
  bytes[6] = (bytes[6] & 0x0f) | 0x40
  bytes[8] = (bytes[8] & 0x3f) | 0x80
  const hex = [...bytes].map((b) => b.toString(16).padStart(2, '0')).join('')
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
}
