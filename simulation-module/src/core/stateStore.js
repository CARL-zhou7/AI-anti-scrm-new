// 演绎状态合并（接口文档§5）：
// - 事件先按 eventId 去重；消息与分析再按业务对象 ID / 轮号合并，重发不当作新台词。
// - stateVersion 为十进制字符串，按整数比较；较旧版本不能回退当前状态，但缺失的已保存内容仍会补入。
// - 终态不会回到 RUNNING；报告按 attemptNo/updatedAt 合并，SUCCEEDED 不回退。
// 这里只整理服务端已保存的事实，不推导结局、不补造风险。

import { RoundPhase, SimulationStatus, Speaker } from './constants.js'

export function compareVersion(a, b) {
  const x = BigInt(a ?? '0')
  const y = BigInt(b ?? '0')
  return x === y ? 0 : x > y ? 1 : -1
}

export function createSimulationStore() {
  return {
    state: null, // 最新的 SimulationState（currentRound 以 rounds 为准）
    rounds: {}, // roundNo -> 本页累计的轮次（用于对话记录与当前轮呈现）
    seenEventIds: new Set(),
    changedAt: 0,
  }
}

export function isTerminal(state) {
  return !!state && state.status !== SimulationStatus.RUNNING
}

export function currentRound(store) {
  const no = store.state?.currentRoundNo
  return no ? store.rounds[no] || null : null
}

export function mergeReport(current, incoming) {
  if (!incoming) return current ?? null
  if (!current) return incoming
  if (current.status === 'SUCCEEDED') return current
  if (incoming.attemptNo < current.attemptNo) return current
  if (incoming.attemptNo === current.attemptNo && Date.parse(incoming.updatedAt) < Date.parse(current.updatedAt)) return current
  return incoming
}

const SPEAKER_ORDER = { [Speaker.SCAMMER]: 0, [Speaker.VICTIM]: 1 }
const STATUS_RANK = { PENDING: 0, PRESENTING: 1, COMPLETED: 2, FAILED: 2 }

function ensureRound(store, roundNo) {
  if (!store.rounds[roundNo]) {
    store.rounds[roundNo] = {
      roundNo,
      phase: RoundPhase.SCAMMER_GENERATING,
      interactionComplete: false,
      presentationComplete: false,
      messages: [],
      analysis: null,
      readingUntil: null,
    }
  }
  return store.rounds[roundNo]
}

function mergeMessage(round, msg) {
  const existing = round.messages.find((m) => m.id === msg.id)
  if (!existing) {
    round.messages.push({ ...msg })
    round.messages.sort((a, b) => SPEAKER_ORDER[a.speaker] - SPEAKER_ORDER[b.speaker])
    return
  }
  // 呈现状态只前进不后退（重发或旧快照不能把 COMPLETED 改回 PRESENTING）
  if (STATUS_RANK[msg.presentationStatus] >= STATUS_RANK[existing.presentationStatus]) {
    Object.assign(existing, msg)
  }
}

function mergeAnalysis(round, analysis) {
  if (!analysis) return
  if (!round.analysis) {
    round.analysis = { ...analysis }
    return
  }
  round.analysis = { ...round.analysis, ...analysis, presentedAt: analysis.presentedAt || round.analysis.presentedAt }
}

function mergeRound(store, live, newer) {
  if (!live) return
  const round = ensureRound(store, live.roundNo)
  if (newer) {
    round.phase = live.phase
    round.interactionComplete = live.interactionComplete
    round.presentationComplete = live.presentationComplete
    round.readingUntil = live.readingUntil
  }
  for (const msg of live.messages || []) mergeMessage(round, msg)
  mergeAnalysis(round, live.analysis)
}

function touch(store) {
  store.changedAt = Date.now()
}

function bumpVersion(store, version) {
  if (store.state && version != null && compareVersion(version, store.state.stateVersion) > 0) {
    store.state.stateVersion = version
  }
}

function isNewer(store, version) {
  return !store.state || compareVersion(version, store.state.stateVersion) >= 0
}

/** 合并一份权威快照（GET 状态、开始响应、回执响应、session.* 事件）。 */
export function applySnapshot(store, snapshot) {
  if (!snapshot) return false
  const newer = isNewer(store, snapshot.stateVersion)
  if (newer) {
    const prev = store.state
    if (prev && isTerminal(prev) && !isTerminal(snapshot)) {
      // 终态不可复活：只补内容，不改状态
    } else {
      const { currentRound: _ignored, ...rest } = snapshot
      store.state = {
        ...rest,
        currentRoundNo: Math.max(prev?.currentRoundNo ?? 0, snapshot.currentRoundNo),
        report: mergeReport(prev?.report, snapshot.report),
      }
    }
  }
  mergeRound(store, snapshot.currentRound, newer)
  touch(store)
  return true
}

/**
 * 合并一条 SSE 事件。
 * @returns {boolean} 是否被采纳（重复事件返回 false）
 */
export function applyEvent(store, type, event) {
  if (!event || typeof event !== 'object') return false
  if (event.eventId) {
    if (store.seenEventIds.has(event.eventId)) return false
    store.seenEventIds.add(event.eventId)
  }
  const data = event.data
  const version = event.stateVersion

  switch (type) {
    case 'session.started':
    case 'session.finished':
    case 'session.interrupted':
      return applySnapshot(store, data)

    case 'session.state':
      applySnapshot(store, data?.state)
      if (store.state && data?.reportView?.report) store.state.report = mergeReport(store.state.report, data.reportView.report)
      return true

    case 'report.updated':
      if (store.state && data) {
        store.state.report = mergeReport(store.state.report, data.report)
        if (data.outcome && !store.state.outcome) store.state.outcome = data.outcome
      }
      touch(store)
      return true
  }

  if (!store.state) return false
  const newer = isNewer(store, version)

  switch (type) {
    case 'round.started': {
      const round = ensureRound(store, data.roundNo)
      if (newer && !isTerminal(store.state)) {
        round.phase = data.phase
        store.state.currentRoundNo = Math.max(store.state.currentRoundNo, data.roundNo)
        store.state.readingUntil = null
        store.state.nextAction = null
        if (data.deadlineAt) store.state.deadlineAt = data.deadlineAt
      }
      break
    }
    case 'message.saved': {
      const round = ensureRound(store, data.roundNo)
      mergeMessage(round, data)
      if (newer && data.presentationStatus === 'PRESENTING') {
        round.phase = data.speaker === Speaker.SCAMMER ? RoundPhase.SCAMMER_PRESENTING : RoundPhase.VICTIM_PRESENTING
      }
      break
    }
    case 'analysis.saved': {
      const round = ensureRound(store, data.roundNo)
      mergeAnalysis(round, data.analysis)
      if (newer) {
        if (!round.analysis.presentedAt) round.phase = RoundPhase.ANALYSIS_PRESENTING
        store.state.latestRiskScore = data.analysis.riskScore
      }
      break
    }
    case 'round.reading': {
      const round = ensureRound(store, data.roundNo)
      round.readingUntil = data.readingUntil
      round.phase = RoundPhase.COMPLETE
      round.presentationComplete = true
      if (newer) {
        store.state.readingUntil = data.readingUntil
        store.state.nextAction = data.nextAction
      }
      break
    }
    default:
      return false
  }
  bumpVersion(store, version)
  touch(store)
  return true
}
