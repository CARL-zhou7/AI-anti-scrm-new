// 演绎页的状态与流程编排（前端侧）：加载权威状态 → 订阅事件流 → 呈现控制器逐项呈现并回执 →
// 阅读停留 / 到时 / 终态 / 退出。页面组件只负责展示与提供 DOM 元素。

import { computed, nextTick, onBeforeUnmount, onMounted, reactive, unref } from 'vue'
import { simulationApi } from '../api/simulationApi.js'
import { openEventStream } from '../api/eventStream.js'
import { msUntil, serverNow } from '../core/clock.js'
import {
  ExitReason,
  INTERRUPTION_LABELS,
  PHASE_LABELS,
  PresentationError,
  PRESENTATION_TIMING,
  RoundPhase,
  SimulationStatus,
  Speaker,
} from '../core/constants.js'
import { applyEvent, applySnapshot, createSimulationStore, isTerminal } from '../core/stateStore.js'
import { createPresenter, PresentationFailure } from '../core/presenter.js'
import { createVoicePlayer } from '../core/voicePlayer.js'
import { loadImage, verifyAnimations } from '../core/animationProbe.js'
import { claimSession, releaseSession } from '../core/ownership.js'

const TICK_MS = 250
const MAX_STREAM_REOPEN = 5

function abortableSleep(ms, signal) {
  return new Promise((resolve, reject) => {
    if (signal?.aborted) return reject(new DOMException('aborted', 'AbortError'))
    const timer = setTimeout(() => {
      signal?.removeEventListener('abort', onAbort)
      resolve()
    }, ms)
    function onAbort() {
      clearTimeout(timer)
      reject(new DOMException('aborted', 'AbortError'))
    }
    signal?.addEventListener('abort', onAbort, { once: true })
  })
}

function throwIfAborted(signal) {
  if (signal?.aborted) throw new DOMException('aborted', 'AbortError')
}

/**
 * @param {import('vue').Ref<string>|string} sessionIdSource
 * @param {object} [options]
 * @param {object} [options.api]
 * @param {(sessionId: string) => void} [options.onReport]       停留结束后进入复盘页
 * @param {() => void} [options.onUnauthenticated]
 * @param {object} [options.timing] 覆盖演出节奏参数（测试用）
 */
export function useSimulation(sessionIdSource, options = {}) {
  const api = options.api || simulationApi
  const timing = { ...PRESENTATION_TIMING, ...options.timing }
  const sessionId = () => String(unref(sessionIdSource))

  const store = reactive(createSimulationStore())
  const ui = reactive({
    mode: 'loading', // loading | live | foreign | error
    entry: null, // fresh | reloaded | foreign
    errorMessage: '',
    notice: '',
    opening: 'pending', // pending | entering | done
    speaker: null,
    speakingMessageId: null,
    speechProgress: 0,
    voiceLevel: null, // null 表示无法取得音量，说话动画使用固定节奏
    muted: false,
    revealedMessages: {}, // messageId -> true，已开始呈现的消息才进入对话记录
    revealedAnalyses: {}, // roundNo -> true
    remainingMs: null,
    readingRemainingMs: null,
    deadlineReached: false,
    connection: 'idle', // idle | open | reconnecting | closed
    exiting: false,
    reportRequested: false,
  })

  // 页面组件通过 registerElement 提供舞台元素，用于文字与动画呈现检测
  const elements = { opening: null, scammer: null, victim: null, dialogue: null, analysis: null }
  function registerElement(name, el) {
    elements[name] = el?.$el ?? el ?? null
  }

  const player = createVoicePlayer({ timing })
  const offMute = player.onMuteChange((muted, reason) => {
    ui.muted = muted
    if (reason === 'autoplay-blocked') ui.notice = '浏览器阻止了自动播放声音，已切换为静音继续演绎；可点击“声音”重新开启。'
  })

  let stream = null
  let streamReopenCount = 0
  let ticker = null
  let lastReconcileAt = 0
  let exitSent = false
  let disposed = false

  // ---------- 舞台：真正执行呈现的地方 ----------

  async function presentOpening(signal) {
    const { scenario, scammer, victim } = store.state.configuration
    ui.opening = 'pending'
    try {
      await Promise.all([scammer.avatar.url, victim.avatar.url].map((u) => loadImage(u, timing.avatarLoadTimeoutMs)))
    } catch {
      throw new PresentationFailure(PresentationError.AVATAR_LOAD_FAILED)
    }
    throwIfAborted(signal)
    ui.opening = 'entering'
    await nextTick()
    if (!elements.opening || !elements.opening.textContent.includes(scenario.name)) {
      throw new PresentationFailure(PresentationError.TEXT_RENDER_FAILED)
    }
    if (
      !verifyAnimations(elements.opening, ['sim-opening-title']) ||
      !verifyAnimations(elements.scammer, ['sim-enter-left']) ||
      !verifyAnimations(elements.victim, ['sim-enter-right'])
    ) {
      throw new PresentationFailure(PresentationError.ANIMATION_FAILED)
    }
    await abortableSleep(timing.openingMs, signal)
    ui.opening = 'done'
    await nextTick()
    if (!verifyAnimations(elements.scammer, ['sim-idle']) || !verifyAnimations(elements.victim, ['sim-idle'])) {
      throw new PresentationFailure(PresentationError.ANIMATION_FAILED)
    }
  }

  async function presentMessage(message, signal) {
    ui.opening = 'done'
    ui.revealedMessages[message.id] = true
    ui.speakingMessageId = message.id
    ui.speechProgress = 0
    ui.voiceLevel = null
    ui.speaker = message.speaker
    await nextTick()
    const textEl = elements.dialogue?.querySelector(`[data-message-id="${CSS.escape(message.id)}"] [data-role="text"]`)
    if (!textEl || textEl.textContent !== message.content) throw new PresentationFailure(PresentationError.TEXT_RENDER_FAILED)
    const figure = message.speaker === Speaker.SCAMMER ? elements.scammer : elements.victim
    if (!verifyAnimations(figure, ['sim-speak'])) throw new PresentationFailure(PresentationError.ANIMATION_FAILED)
    try {
      await player.play(message.audioUrl, {
        signal,
        onProgress: (f) => (ui.speechProgress = f),
        onLevel: (level) => (ui.voiceLevel = level),
      })
    } finally {
      ui.speaker = null
      ui.voiceLevel = null
    }
  }

  async function presentAnalysis(roundNo, analysis) {
    ui.revealedAnalyses[roundNo] = true
    await nextTick()
    const text = elements.analysis?.textContent || ''
    if (!text.includes(analysis.fraudStage) || !text.includes(analysis.riskReason)) {
      throw new PresentationFailure(PresentationError.TEXT_RENDER_FAILED)
    }
  }

  const stage = {
    present(target, signal) {
      if (target.kind === 'opening') return presentOpening(signal)
      if (target.kind === 'message') return presentMessage(target.message, signal)
      return presentAnalysis(target.roundNo, target.analysis)
    },
  }

  // ---------- 状态同步 ----------

  async function reconcile() {
    lastReconcileAt = Date.now()
    const snapshot = await api.getSimulation(sessionId())
    applySnapshot(store, snapshot)
    afterChange()
  }

  function safeReconcile() {
    return reconcile().catch((err) => {
      if (err?.status === 401) handleUnauthenticated()
    })
  }

  const presenter = createPresenter({
    store,
    api,
    stage,
    reconcile,
    onUnauthenticated: handleUnauthenticated,
    onNotice: (msg) => (ui.notice = msg),
  })

  function afterChange() {
    if (disposed || ui.mode !== 'live') return
    const state = store.state
    if (isTerminal(state)) {
      presenter.halt()
      player.stop()
      ui.speaker = null
      releaseSession(sessionId())
      if (state.status === SimulationStatus.INTERRUPTED) closeStream()
      if (state.status === SimulationStatus.FINISHED && state.report && ['SUCCEEDED', 'FAILED'].includes(state.report.status)) closeStream()
      return
    }
    presenter.sync()
  }

  function handleUnauthenticated() {
    closeStream()
    presenter.halt()
    player.stop()
    options.onUnauthenticated?.()
  }

  // ---------- 事件流 ----------

  function openStream() {
    closeStream()
    stream = openEventStream(api.eventsUrl(sessionId()), {
      onEvent(type, payload) {
        if (payload?.sessionId && payload.sessionId !== sessionId()) return
        if (applyEvent(store, type, payload)) afterChange()
      },
      onOpen() {
        ui.connection = 'open'
        streamReopenCount = 0
      },
      onReconnecting() {
        ui.connection = 'reconnecting'
      },
      onClosed: handleStreamClosed,
    })
  }

  function closeStream() {
    stream?.close()
    stream = null
  }

  async function handleStreamClosed() {
    ui.connection = 'closed'
    stream = null
    try {
      await api.getMe()
    } catch (err) {
      if (err?.status === 401) return handleUnauthenticated()
    }
    await safeReconcile()
    const state = store.state
    const wanted = state && (!isTerminal(state) || (state.status === SimulationStatus.FINISHED && !ui.reportRequested))
    if (wanted && streamReopenCount < MAX_STREAM_REOPEN && !disposed) {
      streamReopenCount++
      setTimeout(() => !disposed && !stream && openStream(), 1000 * 2 ** streamReopenCount)
    }
  }

  // ---------- 计时、停留与看门狗 ----------

  function tick() {
    const state = store.state
    if (!state || ui.mode !== 'live') return
    const now = Date.now()
    const running = state.status === SimulationStatus.RUNNING

    ui.remainingMs = running ? msUntil(state.deadlineAt) : Math.max(0, Date.parse(state.deadlineAt) - Date.parse(state.endedAt || state.deadlineAt))
    ui.readingRemainingMs = state.readingUntil ? msUntil(state.readingUntil) : null

    // 到达 600 秒：立即停止播放与推进，等待服务端给出正式结束或中断（不在本地宣称结果）
    if (running && ui.remainingMs === 0) {
      if (!ui.deadlineReached) {
        ui.deadlineReached = true
        presenter.halt()
        player.stop()
        ui.speaker = null
      }
      if (now - lastReconcileAt > timing.reconcileIntervalMs) safeReconcile()
      return
    }

    // 最后阅读停留结束后进入复盘页
    if (state.status === SimulationStatus.FINISHED) {
      if (!ui.reportRequested && (ui.readingRemainingMs === 0 || !state.readingUntil)) goReport()
      return
    }
    if (!running) return

    // 停留到期仍未收到 round.started：GET 核对后继续等待，不自行推进或跳轮
    if (state.readingUntil && ui.readingRemainingMs === 0) {
      const overdue = serverNow() - Date.parse(state.readingUntil)
      if (overdue > timing.readingGraceMs && now - lastReconcileAt > timing.reconcileIntervalMs) safeReconcile()
      return
    }

    // 长时间没有任何新事实且没有正在呈现的内容：可能漏收事件，读取权威状态
    if (!presenter.activeKey && now - store.changedAt > timing.idleWatchdogMs && now - lastReconcileAt > timing.idleWatchdogMs) {
      safeReconcile()
    }
  }

  // ---------- 用户操作 ----------

  function toggleMute() {
    player.unlock()
    player.setMuted(!player.muted)
    if (!player.muted) ui.notice = ''
  }

  async function exit(reason = ExitReason.NAVIGATION) {
    if (ui.exiting || exitSent) return
    ui.exiting = true
    presenter.halt()
    player.stop()
    try {
      const state = await api.exitSimulation(sessionId(), reason)
      exitSent = true
      applySnapshot(store, state)
      if (ui.mode === 'foreign') ui.mode = 'live'
      afterChange()
    } catch (err) {
      if (err?.status === 401) return handleUnauthenticated()
      ui.notice = '退出请求未能确认，正在核对演绎状态'
      await safeReconcile()
    } finally {
      ui.exiting = false
      releaseSession(sessionId())
    }
  }

  function goReport() {
    if (ui.reportRequested) return
    ui.reportRequested = true
    closeStream()
    options.onReport?.(sessionId())
  }

  function onPageHide() {
    // 关闭或刷新：尽力通知退出；刷新后的新页面实例会再次核对（ownership = reloaded）
    if (store.state?.status === SimulationStatus.RUNNING && ui.entry === 'fresh' && !exitSent) {
      exitSent = api.exitOnPageHide(sessionId(), ExitReason.CLOSE)
    }
  }

  // ---------- 生命周期 ----------

  onMounted(async () => {
    const id = sessionId()
    ui.entry = claimSession(id)
    api.warmUpCsrf?.()
    let snapshot
    try {
      snapshot = await api.getSimulation(id)
    } catch (err) {
      if (err?.status === 401) return handleUnauthenticated()
      ui.mode = 'error'
      ui.errorMessage = err?.status === 404 ? '演绎记录不存在或无权访问' : err?.message || '演绎状态读取失败'
      return
    }
    if (disposed) return
    applySnapshot(store, snapshot)

    if (snapshot.status === SimulationStatus.RUNNING && ui.entry === 'reloaded') {
      // 刷新不续演：补发退出（若 pagehide 时已送达，服务端返回已有终态）
      ui.mode = 'live'
      await exit(ExitReason.REFRESH)
      tick()
      return
    }
    if (snapshot.status === SimulationStatus.RUNNING && ui.entry === 'foreign') {
      ui.mode = 'foreign'
      return
    }

    ui.mode = 'live'
    if (isTerminal(snapshot)) {
      ui.opening = 'done'
      releaseSession(id)
    }
    window.addEventListener('pagehide', onPageHide)
    ticker = setInterval(tick, TICK_MS)
    tick()
    if (!isTerminal(snapshot) || snapshot.status === SimulationStatus.FINISHED) openStream()
    afterChange()
  })

  onBeforeUnmount(() => {
    disposed = true
    clearInterval(ticker)
    window.removeEventListener('pagehide', onPageHide)
    closeStream()
    presenter.dispose()
    offMute()
    player.dispose()
    // 页面内导航离开：属于主动离开（不续演）
    if (store.state?.status === SimulationStatus.RUNNING && ui.entry === 'fresh' && !exitSent) {
      exitSent = true
      api.exitSimulation(sessionId(), ExitReason.NAVIGATION, { keepalive: true }).catch(() => {})
      releaseSession(sessionId())
    }
  })

  // ---------- 供模板使用的派生数据 ----------

  const configuration = computed(() => store.state?.configuration ?? null)
  const status = computed(() => store.state?.status ?? null)
  const round = computed(() => {
    const no = store.state?.currentRoundNo
    return no ? store.rounds[no] ?? null : null
  })

  const phaseLabel = computed(() => {
    const state = store.state
    if (!state) return ''
    if (state.status === SimulationStatus.FINISHED) return '演绎已正式结束'
    if (state.status === SimulationStatus.INTERRUPTED) return '演绎已中断'
    if (ui.deadlineReached) return '已到达时长上限，正在确认结果'
    if (state.currentRoundNo === 0) return ui.opening === 'pending' ? '正在准备开场' : '开场'
    const r = round.value
    if (!r) return '内容生成中'
    if (r.phase === RoundPhase.COMPLETE) return state.readingUntil ? '阅读停留' : '本轮完成'
    return PHASE_LABELS[r.phase]
  })

  // 等待服务端生成内容时的提示（不补造台词或分值）
  const waiting = computed(() => {
    const state = store.state
    if (!state || state.status !== SimulationStatus.RUNNING || ui.deadlineReached) return null
    const r = round.value
    if (state.currentRoundNo === 0) return ui.opening === 'pending' ? { speaker: null, text: '正在准备开场…' } : null
    if (!r) return { speaker: null, text: '内容生成中…' }
    if (r.phase === RoundPhase.SCAMMER_GENERATING) return { speaker: Speaker.SCAMMER, text: '骗子正在组织话术…' }
    if (r.phase === RoundPhase.VICTIM_GENERATING) return { speaker: Speaker.VICTIM, text: '受害者正在回应…' }
    if (r.phase === RoundPhase.ANALYZING) return { speaker: null, text: '解说正在分析本轮…' }
    return null
  })

  const allMessages = computed(() =>
    Object.values(store.rounds)
      .sort((a, b) => a.roundNo - b.roundNo)
      .flatMap((r) => r.messages),
  )

  const currentMessage = computed(() => allMessages.value.find((m) => m.id === ui.speakingMessageId) ?? null)

  // 对话记录：进行中只显示已开始呈现的内容；终态显示全部已保存内容（与历史口径一致）
  const transcript = computed(() => {
    const terminal = isTerminal(store.state)
    return Object.values(store.rounds)
      .sort((a, b) => a.roundNo - b.roundNo)
      .map((r) => ({
        roundNo: r.roundNo,
        complete: r.presentationComplete,
        messages: r.messages.filter((m) => terminal || ui.revealedMessages[m.id] || m.presentationStatus === 'COMPLETED'),
        analysis: r.analysis && (terminal || ui.revealedAnalyses[r.roundNo] || r.analysis.presentedAt) ? r.analysis : null,
      }))
      .filter((r) => r.messages.length || r.analysis)
  })

  const visibleAnalyses = computed(() => transcript.value.filter((r) => r.analysis))
  const latestAnalysis = computed(() => {
    const list = visibleAnalyses.value
    return list.length ? { roundNo: list[list.length - 1].roundNo, ...list[list.length - 1].analysis } : null
  })
  const riskSeries = computed(() => visibleAnalyses.value.map((r) => ({ roundNo: r.roundNo, score: r.analysis.riskScore })))

  const interruptionText = computed(() => {
    const it = store.state?.interruption
    return it ? it.message || INTERRUPTION_LABELS[it.code] || '本次演绎已中断' : ''
  })

  return {
    store,
    ui,
    registerElement,
    configuration,
    status,
    round,
    phaseLabel,
    waiting,
    currentMessage,
    transcript,
    latestAnalysis,
    riskSeries,
    interruptionText,
    toggleMute,
    exit,
    goReport,
    reconcile: safeReconcile,
  }
}
