import type {
  Analysis, EffectResult, LiveMessage, PresentationError, PresentationReceipt, RiskScore, SimulationEventEnvelope,
  SimulationState, Speaker,
} from '../api/types'
import type { AudioLike } from './audioTrack'
import { visibleCodePoints } from './segmentReveal'

export type Stage = 'OPENING_SCENE' | 'OPENING_CARDS' | 'WAITING' | 'SPEAKING' | 'NARRATING' | 'READING' | 'DEADLINE' | 'TERMINAL'

export interface Bubble { messageId: string; roundNo: number; content: string; actionText: string | null; visible: number; done: boolean }

export interface DirectorView {
  stage: Stage
  frozen: boolean
  muted: boolean
  roundNo: number
  bubbles: Record<Speaker, Bubble | null>
  speaking: Speaker | null
  mouthOpen: boolean
  narration: { roundNo: number; analysis: Analysis } | null
  risk: RiskScore | null
  waitingSince: number | null
  terminal: SimulationState | null
  failed: string | null
}

export interface DirectorDeps {
  receipts: {
    opening(body: PresentationReceipt): Promise<EffectResult>
    message(messageId: string, body: PresentationReceipt): Promise<EffectResult>
    analysis(roundNo: number, body: PresentationReceipt): Promise<EffectResult>
  }
  createAudio(url: string, muted: boolean): AudioLike
  serverNow(): number
  monotonicNow(): number
  checkState(): Promise<SimulationState>
  onReport(sessionId: string): void
  onState?(state: SimulationState): void
}

export const SCENE_MAX_MS = 15_000
export const CARDS_MS = 7_000 // two 3.5 s cards, within the 8 s budget
const MOUTH_MS = 120
const STALL_MS = 10_000

/** A pausable one-shot timer: freezing keeps the remaining time instead of firing late or early. */
class PausableTimer {
  private handle: ReturnType<typeof setTimeout> | null = null
  private dueAt = 0
  private remaining: number
  constructor(private readonly fn: () => void, ms: number, private readonly now: () => number) { this.remaining = ms; this.resume() }
  pause() { if (!this.handle) return; clearTimeout(this.handle); this.handle = null; this.remaining = Math.max(0, this.dueAt - this.now()) }
  resume() { if (this.handle) return; this.dueAt = this.now() + this.remaining; this.handle = setTimeout(() => { this.handle = null; this.fn() }, this.remaining) }
  cancel() { if (this.handle) clearTimeout(this.handle); this.handle = null }
}

/**
 * The single presentation clock (plan Task 12). It consumes only server-saved facts (SSE events / state
 * snapshots), turns them into stage instructions deterministically, and reports presentation receipts — the only
 * write that can advance a simulation. It never advances rounds or calls models itself. While speaking, reveal
 * progress comes solely from the audio's currentTime and the server's segment timings.
 */
export class PerformanceDirector {
  readonly view: DirectorView
  private sessionId = ''
  private audio: AudioLike | null = null
  private speakingMessage: LiveMessage | null = null
  private pendingMessages: LiveMessage[] = []
  private pendingAnalysis: { roundNo: number; analysis: Analysis } | null = null
  private queuedReceipts: (() => Promise<unknown>)[] = []
  private completedMessages = new Set<string>()
  private presentedRounds = new Set<number>()
  private timers = new Set<PausableTimer>()
  private readingTimer: ReturnType<typeof setTimeout> | null = null
  private ticker: ReturnType<typeof setInterval> | null = null
  private deadlineAt = 0
  private paused = false
  private readingUntil: number | null = null
  private nextAction: 'NEXT_ROUND' | 'REPORT' | null = null
  private lastProgress = { ms: -1, at: 0 }
  private finished = false

  constructor(private readonly deps: DirectorDeps, view: DirectorView) { this.view = view }

  static initialView(): DirectorView {
    return { stage: 'OPENING_SCENE', frozen: false, muted: false, roundNo: 0, bubbles: { SCAMMER: null, VICTIM: null },
      speaking: null, mouthOpen: false, narration: null, risk: null, waitingSince: null, terminal: null, failed: null }
  }

  /** Begins the run after a successful start: scene introduction first, then character cards. */
  begin(state: SimulationState) {
    this.sessionId = state.sessionId
    this.applyClock(state)
    this.view.stage = 'OPENING_SCENE'
    this.later(() => this.fail('opening', 'ANIMATION_FAILED'), SCENE_MAX_MS + 1000, 'sceneGuard')
    this.ticker = setInterval(() => this.tick(), 50)
  }

  /** The scene animation reported its end (animationend). */
  sceneDone() {
    if (this.view.stage !== 'OPENING_SCENE') return
    this.cancelNamed('sceneGuard')
    this.view.stage = 'OPENING_CARDS'
    this.later(() => this.openingDone(), CARDS_MS)
  }

  /** A portrait or animation failed during the opening. */
  openingFailed(code: PresentationError) { this.fail('opening', code) }

  private openingDone() {
    if (this.view.stage !== 'OPENING_CARDS') return
    this.toWaiting()
    this.receipt(() => this.deps.receipts.opening({ status: 'COMPLETED' }))
    this.drain()
  }

  // ------------------------------------------------------------------ events

  handle(event: SimulationEventEnvelope) {
    if (this.view.stage === 'TERMINAL') return
    switch (event.type) {
      case 'session.state': this.reconcile(event.data.state); break
      case 'session.paused': case 'session.resumed': case 'session.started': this.applyClock(event.data); break
      case 'session.finished': this.finished = true; this.applyClock(event.data); this.deps.onState?.(event.data); break
      case 'session.interrupted': this.terminate(event.data); break
      case 'round.started': this.roundStarted(event.data.roundNo); break
      case 'message.saved': this.enqueueMessage(event.data); break
      case 'analysis.saved': this.pendingAnalysis = { roundNo: event.data.roundNo, analysis: event.data.analysis }; this.drain(); break
      case 'round.reading': this.reading(Date.parse(event.data.readingUntil), event.data.nextAction); break
      default: break
    }
  }

  /** Authoritative snapshot (stream connect/reconnect, receipt/pause responses). Never replays finished lines. */
  reconcile(state: SimulationState) {
    this.applyClock(state)
    this.deps.onState?.(state)
    if (state.status === 'INTERRUPTED') { this.terminate(state); return }
    if (state.status === 'FINISHED') this.finished = true
    if (state.latestRiskScore !== null && this.view.risk === null) this.view.risk = state.latestRiskScore
    const round = state.currentRound
    if (!round || this.view.stage === 'OPENING_SCENE' || this.view.stage === 'OPENING_CARDS') return
    if (round.roundNo > this.view.roundNo) this.roundStarted(round.roundNo)
    for (const m of round.messages) {
      if (m.presentationStatus === 'COMPLETED' && !this.view.bubbles[m.speaker]) {
        this.completedMessages.add(m.id)
        this.view.bubbles[m.speaker] = { messageId: m.id, roundNo: m.roundNo, content: m.content, actionText: m.actionText,
          visible: Array.from(m.content).length, done: true }
      } else if (m.presentationStatus === 'PRESENTING') this.enqueueMessage(m)
    }
    if (round.analysis) {
      if (round.analysis.presentedAt) {
        this.presentedRounds.add(round.roundNo)
        this.view.narration = { roundNo: round.roundNo, analysis: round.analysis }
        this.view.risk = round.analysis.riskScore
      } else this.pendingAnalysis = { roundNo: round.roundNo, analysis: round.analysis }
    }
    if (state.readingUntil && state.nextAction) this.reading(Date.parse(state.readingUntil), state.nextAction)
    this.drain()
  }

  private roundStarted(roundNo: number) {
    if (roundNo <= this.view.roundNo) return
    // Clearing the stage bubbles is a display change only; saved history is untouched.
    this.view.roundNo = roundNo
    this.view.bubbles = { SCAMMER: null, VICTIM: null }
    this.clearReading()
    if (this.view.stage === 'READING' || this.view.stage === 'NARRATING') this.toWaiting()
    this.drain()
  }

  private enqueueMessage(m: LiveMessage) {
    if (this.completedMessages.has(m.id) || this.speakingMessage?.id === m.id || this.pendingMessages.some(p => p.id === m.id)) return
    if (m.presentationStatus !== 'PRESENTING' || !m.audioUrl) return
    this.pendingMessages.push(m)
    this.drain()
  }

  private reading(until: number, next: 'NEXT_ROUND' | 'REPORT') {
    this.readingUntil = until
    this.nextAction = next
    if (this.view.stage === 'NARRATING' || this.view.stage === 'WAITING' || this.view.stage === 'READING') this.view.stage = 'READING'
    this.armReading()
  }

  // ------------------------------------------------------------------ stage progression

  private drain() {
    if (this.view.frozen || (this.view.stage !== 'WAITING' && this.view.stage !== 'READING')) return
    const message = this.pendingMessages.shift()
    if (message) { this.speak(message); return }
    if (this.pendingAnalysis && !this.presentedRounds.has(this.pendingAnalysis.roundNo) && this.view.stage === 'WAITING') {
      this.narrate(this.pendingAnalysis)
    }
  }

  private speak(m: LiveMessage) {
    if (m.roundNo > this.view.roundNo) this.roundStarted(m.roundNo)
    this.speakingMessage = m
    this.view.stage = 'SPEAKING'
    this.view.waitingSince = null
    this.view.speaking = m.speaker
    this.view.bubbles[m.speaker] = { messageId: m.id, roundNo: m.roundNo, content: m.content, actionText: m.actionText, visible: 0, done: false }
    const audio = this.deps.createAudio(m.audioUrl!, this.view.muted)
    this.audio = audio
    this.lastProgress = { ms: -1, at: this.deps.monotonicNow() }
    audio.onEnded(() => this.spoken(m))
    audio.onError(() => this.fail('message', 'AUDIO_LOAD_FAILED', m.id))
    audio.play().catch(() => { if (this.audio === audio && !this.view.frozen) this.fail('message', 'AUDIO_PLAYBACK_FAILED', m.id) })
  }

  private spoken(m: LiveMessage) {
    if (this.speakingMessage?.id !== m.id) return
    const bubble = this.view.bubbles[m.speaker]
    if (bubble) { bubble.visible = Array.from(bubble.content).length; bubble.done = true }
    this.view.speaking = null
    this.view.mouthOpen = false
    this.audio?.destroy(); this.audio = null
    this.speakingMessage = null
    this.completedMessages.add(m.id)
    this.toWaiting()
    this.receipt(() => this.deps.receipts.message(m.id, { status: 'COMPLETED' }))
    this.drain()
  }

  private narrate(entry: { roundNo: number; analysis: Analysis }) {
    this.pendingAnalysis = null
    this.presentedRounds.add(entry.roundNo)
    this.view.stage = 'NARRATING'
    this.view.waitingSince = null
    this.view.narration = entry
    this.view.risk = entry.analysis.riskScore
    // The panel renders synchronously from the view; confirm on the next frame.
    this.later(() => this.receipt(() => this.deps.receipts.analysis(entry.roundNo, { status: 'COMPLETED' })), 300)
  }

  private armReading() {
    this.clearReading()
    if (this.readingUntil === null || this.view.frozen || this.paused) return
    const wait = Math.max(0, this.readingUntil - this.deps.serverNow())
    this.readingTimer = setTimeout(() => this.readingExpired(), wait)
  }

  private readingExpired() {
    this.readingTimer = null
    if (this.nextAction === 'REPORT') { this.stop(); this.deps.onReport(this.sessionId); return }
    this.toWaiting()
    // The server opens the next round on its own; if round.started is late, verify instead of advancing.
    this.later(() => {
      if (this.view.stage === 'WAITING' && this.readingUntil !== null) void this.deps.checkState().then(s => this.reconcile(s)).catch(() => undefined)
    }, 3000)
  }

  private clearReading() { if (this.readingTimer) clearTimeout(this.readingTimer); this.readingTimer = null }

  private toWaiting() { this.view.stage = 'WAITING'; this.view.waitingSince = this.deps.monotonicNow() }

  // ------------------------------------------------------------------ clock

  private tick() {
    if (this.view.stage === 'TERMINAL' || this.view.frozen) return
    const m = this.speakingMessage
    if (m && this.audio) {
      const ms = this.audio.currentMs
      const bubble = this.view.bubbles[m.speaker]
      if (bubble && !bubble.done) bubble.visible = visibleCodePoints(ms, m.segments, m.content, this.audio.ended)
      this.view.mouthOpen = Math.floor(ms / MOUTH_MS) % 2 === 1
      const now = this.deps.monotonicNow()
      if (ms !== this.lastProgress.ms) this.lastProgress = { ms, at: now }
      else if (now - this.lastProgress.at > STALL_MS) this.fail('message', 'AUDIO_PLAYBACK_FAILED', m.id)
    }
    if (!this.paused && !this.finished && this.deadlineAt && this.deps.serverNow() >= this.deadlineAt && this.view.stage !== 'DEADLINE') {
      this.localDeadline()
    }
  }

  /** Local time is up: stop presenting and wait for the server's verdict; never show an outcome locally. */
  private localDeadline() {
    this.stopMedia()
    this.view.stage = 'DEADLINE'
    const poll = () => {
      if (this.view.stage !== 'DEADLINE') return
      void this.deps.checkState().then(s => {
        if (s.status === 'RUNNING') { this.applyClock(s); if (this.deps.serverNow() < this.deadlineAt) this.toWaiting(); else setTimeout(poll, 2000) }
        else if (s.status === 'FINISHED') { this.finished = true; this.reconcile(s); this.view.stage = 'READING'; this.armReading() }
        else this.terminate(s)
      }).catch(() => setTimeout(poll, 2000))
    }
    poll()
  }

  private applyClock(state: SimulationState) {
    this.deadlineAt = Date.parse(state.deadlineAt)
    this.paused = state.paused
    if (state.readingUntil && state.nextAction) { this.readingUntil = Date.parse(state.readingUntil); this.nextAction = state.nextAction }
    if (this.readingUntil !== null && this.view.stage === 'READING') this.armReading()
  }

  // ------------------------------------------------------------------ settings: freeze / resume / mute

  /** Opening settings: freeze audio, reveal, mouth flaps, animations and countdowns at the current position. */
  freeze() {
    if (this.view.frozen) return
    this.view.frozen = true
    this.audio?.pause()
    for (const t of this.timers) t.pause()
    this.clearReading()
  }

  /** Continue from the frozen position (no replay), using the server's shifted reading/deadline times. */
  unfreeze(state?: SimulationState) {
    if (!this.view.frozen) return
    this.view.frozen = false
    if (state) this.applyClock(state)
    this.paused = false
    for (const t of this.timers) t.resume()
    if (this.audio && this.speakingMessage) {
      const audio = this.audio, id = this.speakingMessage.id
      this.lastProgress = { ms: -1, at: this.deps.monotonicNow() }
      audio.play().catch(() => { if (this.audio === audio) this.fail('message', 'AUDIO_PLAYBACK_FAILED', id) })
    }
    if (this.view.stage === 'READING') this.armReading()
    const queued = this.queuedReceipts.splice(0)
    for (const send of queued) void send().then(r => this.afterReceipt(r)).catch(() => undefined)
    this.drain()
  }

  setMuted(muted: boolean) {
    this.view.muted = muted
    if (this.audio) this.audio.muted = muted
  }

  // ------------------------------------------------------------------ failures & termination

  private fail(scope: 'opening' | 'message', code: PresentationError, messageId?: string) {
    if (this.view.stage === 'TERMINAL' || this.view.failed) return
    this.view.failed = code
    this.stopMedia()
    const send = scope === 'opening' ? () => this.deps.receipts.opening({ status: 'FAILED', errorCode: code })
      : () => this.deps.receipts.message(messageId!, { status: 'FAILED', errorCode: code })
    void send().then(r => this.afterReceipt(r)).catch(() => undefined)
  }

  terminate(state: SimulationState) {
    this.stop()
    this.view.terminal = state
    this.view.stage = 'TERMINAL'
    this.deps.onState?.(state)
  }

  stop() {
    this.stopMedia()
    if (this.ticker) clearInterval(this.ticker)
    this.ticker = null
    for (const t of this.timers) t.cancel()
    this.timers.clear()
    this.clearReading()
  }

  private stopMedia() {
    this.audio?.destroy()
    this.audio = null
    this.view.speaking = null
    this.view.mouthOpen = false
  }

  private receipt(send: () => Promise<EffectResult>) {
    if (this.view.frozen) { this.queuedReceipts.push(send); return }
    void send().then(r => this.afterReceipt(r)).catch(() => undefined)
  }

  private afterReceipt(result: unknown) {
    const r = result as EffectResult | undefined
    if (!r?.state) return
    if (r.state.status === 'INTERRUPTED') this.terminate(r.state)
    else if (r.state.status === 'FINISHED') { this.finished = true; this.applyClock(r.state); this.deps.onState?.(r.state) }
  }

  private named = new Map<string, PausableTimer>()
  private later(fn: () => void, ms: number, name?: string) {
    const timer: PausableTimer = new PausableTimer(() => { this.timers.delete(timer); fn() }, ms, () => this.deps.monotonicNow())
    if (this.view.frozen) timer.pause()
    this.timers.add(timer)
    if (name) this.named.set(name, timer)
  }
  private cancelNamed(name: string) { const t = this.named.get(name); if (t) { t.cancel(); this.timers.delete(t); this.named.delete(name) } }
}
