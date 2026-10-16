// 呈现控制器（架构§5.2～5.3）：同一个控制器按服务端已保存的事实决定“当前应该呈现什么”，
// 依次驱动开场、骗子消息、受害者消息、本轮分析的呈现，并提交三类呈现回执（API-17/18/19）。
// - 只呈现服务端处于 *_PRESENTING 阶段、且已保存并媒体就绪的内容；不自行推进下一轮。
// - 呈现失败按接口文档§4.1 上报允许的错误码，由服务端核定 MEDIA_FAILED；客户端从不提交结局或风险。
// - 到时、终态或页面离开时中止当前呈现，不再提交完成回执。

import { ALLOWED_FAILURES, PresentationError, RoundPhase, SimulationStatus, Speaker } from './constants.js'
import { applySnapshot } from './stateStore.js'

export class PresentationFailure extends Error {
  constructor(code, detail) {
    super(detail || code)
    this.name = 'PresentationFailure'
    this.code = code
  }
}

export function isAbortError(err) {
  return err?.name === 'AbortError'
}

/** 根据已合并的状态推导当前需要呈现的对象；没有可呈现内容时返回 null（等待服务端）。 */
export function presentationTarget(store) {
  const s = store.state
  if (!s || s.status !== SimulationStatus.RUNNING) return null
  if (s.currentRoundNo === 0) return { kind: 'opening', key: 'opening' }
  const round = store.rounds[s.currentRoundNo]
  if (!round) return null
  if (round.phase === RoundPhase.SCAMMER_PRESENTING || round.phase === RoundPhase.VICTIM_PRESENTING) {
    const speaker = round.phase === RoundPhase.SCAMMER_PRESENTING ? Speaker.SCAMMER : Speaker.VICTIM
    // PENDING 消息只可看到文字，不得播放或提交完成（API-15）
    const message = round.messages.find((m) => m.speaker === speaker && m.presentationStatus === 'PRESENTING')
    return message ? { kind: 'message', key: `message:${message.id}`, roundNo: round.roundNo, message } : null
  }
  if (round.phase === RoundPhase.ANALYSIS_PRESENTING && round.analysis && !round.analysis.presentedAt) {
    return { kind: 'analysis', key: `analysis:${round.roundNo}`, roundNo: round.roundNo, analysis: round.analysis }
  }
  return null
}

const RECEIPT_RETRY_DELAYS = [500, 1000, 2000]
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

/**
 * @param {object} deps
 * @param {object} deps.store         reactive 化的 createSimulationStore()
 * @param {object} deps.api           simulationApi
 * @param {{present: (target: object, signal: AbortSignal) => Promise<void>}} deps.stage 页面舞台
 * @param {() => Promise<void>} deps.reconcile  GET 权威状态并合并
 * @param {(err: Error) => void} [deps.onUnauthenticated]
 * @param {(message: string) => void} [deps.onNotice]
 */
export function createPresenter({ store, api, stage, reconcile, onUnauthenticated, onNotice }) {
  let active = null // { key, controller, sendingReceipt }
  const handled = new Set()
  let halted = false
  let disposed = false

  function sessionId() {
    return store.state.sessionId
  }

  function callReceipt(target, receipt) {
    if (target.kind === 'opening') return api.presentOpening(sessionId(), receipt)
    if (target.kind === 'message') return api.presentMessage(sessionId(), target.message.id, receipt)
    return api.presentAnalysis(sessionId(), target.roundNo, receipt)
  }

  async function safeReconcile() {
    try {
      await reconcile()
    } catch {
      // 读取失败时保持当前状态，等待 SSE 或下一次核对
    }
  }

  async function sendReceipt(target, receipt) {
    for (let attempt = 0; ; attempt++) {
      try {
        const result = await callReceipt(target, receipt)
        applySnapshot(store, result.state)
        return
      } catch (err) {
        if (err?.status === 401) {
          onUnauthenticated?.(err)
          return
        }
        const retriable = err?.network || err?.status >= 500
        if (!retriable || attempt >= RECEIPT_RETRY_DELAYS.length) {
          // 409 阶段冲突、404 等：以服务端权威状态为准，不重复推进
          await safeReconcile()
          if (retriable) onNotice?.('呈现回执提交失败，正在等待服务端核对状态')
          return
        }
        await sleep(RECEIPT_RETRY_DELAYS[attempt])
        // 提交结果未知：先读权威状态，目标仍是当前对象时才重发同一事实
        await safeReconcile()
        const still = presentationTarget(store)
        if (!still || still.key !== target.key) return
      }
    }
  }

  async function run(target) {
    const controller = new AbortController()
    const entry = { key: target.key, controller, sendingReceipt: false }
    active = entry
    let receipt
    try {
      await stage.present(target, controller.signal)
      receipt = { status: 'COMPLETED' }
    } catch (err) {
      if (!(controller.signal.aborted || isAbortError(err))) {
        const allowed = ALLOWED_FAILURES[target.kind]
        const errorCode = err instanceof PresentationFailure && allowed.includes(err.code) ? err.code : PresentationError.TEXT_RENDER_FAILED
        receipt = { status: 'FAILED', errorCode }
      }
    }
    if (!receipt || controller.signal.aborted || disposed) {
      if (active === entry) active = null
      return
    }
    entry.sendingReceipt = true
    handled.add(target.key)
    await sendReceipt(target, receipt)
    if (active === entry) active = null
    sync()
  }

  function sync() {
    if (disposed) return
    const target = halted ? null : presentationTarget(store)
    if (active && !active.sendingReceipt && (!target || target.key !== active.key)) {
      active.controller.abort()
      active = null
    }
    if (!target || handled.has(target.key) || active) return
    run(target)
  }

  return {
    sync,
    /** 到时或本页不再负责呈现：中止正在进行的呈现，后续不再开始新的呈现。 */
    halt() {
      halted = true
      if (active && !active.sendingReceipt) {
        active.controller.abort()
        active = null
      }
    },
    get activeKey() {
      return active?.key ?? null
    },
    dispose() {
      disposed = true
      active?.controller.abort()
      active = null
    },
  }
}
