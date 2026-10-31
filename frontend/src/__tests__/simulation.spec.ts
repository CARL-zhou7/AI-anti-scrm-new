import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { visibleCodePoints } from '../simulation/segmentReveal'
import { EventInbox, compareVersion } from '../simulation/eventInbox'
import { CARDS_MS, PerformanceDirector, type DirectorDeps } from '../simulation/performanceDirector'
import type { AudioLike } from '../simulation/audioTrack'
import type { EffectResult, LiveMessage, ScammerDraft, SimulationEventEnvelope, SimulationState } from '../api/types'

beforeEach(() => vi.useFakeTimers())
afterEach(() => vi.useRealTimers())

it('reveals text by segment time and never splits an emoji', () => {
  const segments = [{ startMs: 0, endMs: 1000, charStart: 0, charEnd: 2 }, { startMs: 1000, endMs: 2000, charStart: 2, charEnd: 4 }]
  expect(visibleCodePoints(500, segments, '妈，😀好')).toBe(1)
  expect(visibleCodePoints(1500, segments, '妈，😀好')).toBe(3)
  expect(visibleCodePoints(100, segments, '妈，😀好', true)).toBe(4)
})

it('de-duplicates events and compares versions numerically', () => {
  expect(compareVersion('10', '9')).toBe(1)
  const inbox = new EventInbox()
  const state = (v: string, id: string) => ({ type: 'session.paused', eventId: id, sessionId: '1', stateVersion: v, data: {} }) as unknown as SimulationEventEnvelope
  expect(inbox.accept(state('10', 'a'))).toBe(true)
  expect(inbox.accept(state('10', 'a'))).toBe(false)
  expect(inbox.accept(state('9', 'b'))).toBe(false)
  expect(inbox.accept({ type: 'intervention.draft', eventId: 'old-draft', sessionId: '1', stateVersion: '9',
    data: { roundNo: 1, draft: reviewDraft, pendingInterventionCount: 0 } })).toBe(false)
})

class FakeAudio implements AudioLike {
  currentMs = 0; ended = false; muted = false; plays = 0; paused = false; recoveries = 0
  private endCb: () => void = () => {}
  play() { this.plays++; this.paused = false; return Promise.resolve() }
  recover() { this.recoveries++; return this.play() }
  pause() { this.paused = true }
  onEnded(cb: () => void) { this.endCb = cb }
  private errorCb: () => void = () => {}
  onError(cb: () => void) { this.errorCb = cb }
  error() { this.errorCb() }
  destroy() {}
  finish() { this.ended = true; this.endCb() }
}

function setup() {
  const audios: FakeAudio[] = []
  const ok = { effect: 'APPLIED', state: { status: 'RUNNING' } } as unknown as EffectResult
  const deps: DirectorDeps = {
    receipts: { opening: vi.fn().mockResolvedValue(ok), message: vi.fn().mockResolvedValue(ok), analysis: vi.fn().mockResolvedValue(ok) },
    createAudio: (_url, muted) => { const a = new FakeAudio(); a.muted = muted; audios.push(a); return a },
    serverNow: () => Date.parse('2026-10-18T02:00:00.000Z') + performance.now(),
    monotonicNow: () => performance.now(),
    checkState: vi.fn(),
    onReport: vi.fn(),
  }
  const director = new PerformanceDirector(deps, PerformanceDirector.initialView())
  director.begin({ sessionId: '1', deadlineAt: '2026-10-18T02:10:00.000Z', paused: false, readingUntil: null, nextAction: null } as SimulationState)
  return { director, deps, audios }
}

const message = (id: string, speaker: 'SCAMMER' | 'VICTIM'): SimulationEventEnvelope => ({ type: 'message.saved', eventId: 'e' + id, sessionId: '1',
  stateVersion: '3', data: { id, roundNo: 1, speaker, content: '你好呀', actionText: null, presentationStatus: 'PRESENTING',
    presentationCompletedAt: null, presentationErrorCode: null, savedAt: '', audioUrl: '/a', audioDurationMs: 1000,
    segments: [{ startMs: 0, endMs: 1000, charStart: 0, charEnd: 3 }] } as LiveMessage })

const reviewDraft: ScammerDraft = { draftId: '11', sessionId: '1', roundNo: 1, roundPhase: 'SCAMMER_REVIEW', revisionNo: 1,
  status: 'DRAFT', originalContent: '骗子台词', originalAction: null, draftContent: '骗子台词', draftAction: null, guidance: null, updatedAt: '' }

it('keeps the first draft off stage until adoption, including reconnects', async () => {
  const { director, deps, audios } = setup()
  director.sceneDone()
  await vi.advanceTimersByTimeAsync(CARDS_MS)
  const state = { sessionId: '1', status: 'RUNNING', deadlineAt: null, paused: false, latestRiskScore: null,
    currentRound: { roundNo: 1, phase: 'SCAMMER_REVIEW', messages: [], analysis: null },
    intervention: { currentDraft: reviewDraft }, readingUntil: null, nextAction: null } as unknown as SimulationState
  director.reconcile(state)
  expect(director.view.stage).toBe('REVIEW')
  expect(director.view.roundNo).toBe(0)
  expect(director.view.bubbles.SCAMMER).toBeNull()
  expect(director.view.bubbles.VICTIM).toBeNull()
  await vi.advanceTimersByTimeAsync(60_000)
  expect(audios).toHaveLength(0)
  expect(deps.receipts.message).not.toHaveBeenCalled()

  // Revisions stay in the orb without advancing the performance.
  director.handle({ type: 'intervention.draft', sessionId: '1', stateVersion: '4', eventId: 'd2',
    data: { roundNo: 1, draft: { ...reviewDraft, revisionNo: 2, draftContent: '改写后的台词' }, pendingInterventionCount: 0 } })
  expect(director.view.bubbles.SCAMMER).toBeNull()
  director.handle(message('11', 'SCAMMER')) // SSE can arrive before the adopt HTTP response.
  expect(audios).toHaveLength(0)
  director.reconcile({ ...state, intervention: null, currentRound: { ...state.currentRound!, phase: 'SCAMMER_PRESENTING' } })
  expect(audios).toHaveLength(1)
  audios[0]!.finish()
  director.handle(message('12', 'VICTIM'))
  expect(director.view.speaking).toBe('VICTIM')
  director.stop()
})

it('retains the previous dialogue and narration during next-round preparation, review and revisions', async () => {
  const { director, deps, audios } = setup()
  director.sceneDone()
  await vi.advanceTimersByTimeAsync(CARDS_MS)
  director.handle(message('21', 'SCAMMER'))
  audios[0]!.finish()
  director.handle(message('22', 'VICTIM'))
  audios[1]!.finish()
  const analysis = { fraudStage: '建立信任', speechAnalysis: '话术', methodAnalysis: '手段', victimPsychology: '心理',
    riskScore: 55, riskReason: '风险依据', savedAt: '', presentedAt: null }
  director.handle({ type: 'analysis.saved', eventId: 'a1', sessionId: '1', stateVersion: '4', data: { roundNo: 1, analysis } })
  await vi.advanceTimersByTimeAsync(300)
  const previousBubbles = structuredClone(director.view.bubbles)
  const previousNarration = structuredClone(director.view.narration)
  const nextDraft = { ...reviewDraft, draftId: '31', roundNo: 2, draftContent: '下一轮草稿' }
  const nextState = { sessionId: '1', status: 'RUNNING', deadlineAt: null, paused: false, latestRiskScore: 55,
    currentRound: { roundNo: 2, phase: 'SCAMMER_GENERATING', messages: [], analysis: null },
    intervention: { currentDraft: null }, readingUntil: null, nextAction: null } as unknown as SimulationState
  director.handle({ type: 'round.started', eventId: 'r2', sessionId: '1', stateVersion: '5',
    data: { roundNo: 2, phase: 'SCAMMER_GENERATING', startedAt: '', deadlineAt: '' } })
  expect(director.view.bubbles).toEqual(previousBubbles)
  director.reconcile(nextState)
  expect(director.view.bubbles).toEqual(previousBubbles)
  for (const draft of [nextDraft, { ...nextDraft, revisionNo: 2, draftContent: '修改后的下一轮草稿' },
    { ...nextDraft, revisionNo: 3, draftContent: '重新生成的下一轮草稿' }]) {
    director.handle({ type: 'intervention.draft', eventId: 'd' + draft.revisionNo, sessionId: '1', stateVersion: '6',
      data: { roundNo: 2, draft, pendingInterventionCount: 0 } })
    director.reconcile({ ...nextState, currentRound: { ...nextState.currentRound!, phase: 'SCAMMER_REVIEW' },
      intervention: { currentDraft: draft } as SimulationState['intervention'] })
    expect(director.view.stage).toBe('REVIEW')
    expect(director.view.roundNo).toBe(1)
    expect(director.view.bubbles).toEqual(previousBubbles)
    expect(director.view.narration).toEqual(previousNarration)
  }
  expect(audios).toHaveLength(2)
  expect(deps.receipts.message).toHaveBeenCalledTimes(2)
  director.reconcile({ ...nextState, currentRound: { ...nextState.currentRound!, phase: 'SCAMMER_PRESENTING' } })
  expect(director.view.roundNo).toBe(2)
  expect(director.view.bubbles).toEqual({ SCAMMER: null, VICTIM: null })
  director.stop()
})

it('keeps a floating magic-orb entry between drafts and resets edits for each round', async () => {
  const { mount } = await import('@vue/test-utils')
  const { default: MagicOrbPanel } = await import('../components/simulation/MagicOrbPanel.vue')
  const wrapper = mount(MagicOrbPanel, { props: { draft: null, busy: false } })
  expect(wrapper.get('[data-testid="magic-orb-toggle"]').attributes('aria-expanded')).toBe('false')
  await wrapper.get('[data-testid="magic-orb-toggle"]').trigger('click')
  expect(wrapper.text()).toContain('等待骗子台词')
  await wrapper.setProps({ draft: reviewDraft })
  expect(wrapper.find('#magic-orb-panel').exists()).toBe(false)
  await wrapper.get('[data-testid="magic-orb-toggle"]').trigger('click')
  expect(wrapper.get('[data-testid="magic-orb-draft"]').text()).toBe('骗子台词')
  await wrapper.get('[data-testid="magic-orb-edit"]').trigger('click')
  await wrapper.get('[data-testid="magic-orb-editor"]').setValue('未保存台词')
  await wrapper.setProps({ draft: { ...reviewDraft } })
  expect(wrapper.get<HTMLTextAreaElement>('[data-testid="magic-orb-editor"]').element.value).toBe('未保存台词')
  await wrapper.setProps({ draft: { ...reviewDraft, draftId: '22', roundNo: 2, draftContent: '下一轮台词' } })
  expect(wrapper.find('[data-testid="magic-orb-editor"]').exists()).toBe(false)
  expect(wrapper.find('#magic-orb-panel').exists()).toBe(false)
  expect(wrapper.emitted('editingChange')?.at(-1)).toEqual([false])
  await wrapper.get('[data-testid="magic-orb-toggle"]').trigger('click')
  expect(wrapper.get('[data-testid="magic-orb-draft"]').text()).toBe('下一轮台词')
  await wrapper.setProps({ draft: null })
  expect(wrapper.find('[data-testid="magic-orb-toggle"]').exists()).toBe(true)
  expect(wrapper.find('[data-testid="magic-orb-continue"]').exists()).toBe(false)
  wrapper.unmount()
})

it('runs opening once, receipts only after audio ends, keeps both bubbles in a round', async () => {
  const { director, deps, audios } = setup()
  director.sceneDone()
  await vi.advanceTimersByTimeAsync(CARDS_MS)
  expect(deps.receipts.opening).toHaveBeenCalledTimes(1)
  director.handle({ type: 'round.started', eventId: 'r1', sessionId: '1', stateVersion: '2', data: { roundNo: 1, phase: 'SCAMMER_GENERATING', startedAt: '', deadlineAt: '' } })
  director.handle(message('11', 'SCAMMER'))
  expect(director.view.stage).toBe('SPEAKING')
  expect(deps.receipts.message).not.toHaveBeenCalled()
  audios[0]!.finish()
  expect(deps.receipts.message).toHaveBeenCalledWith('11', { status: 'COMPLETED' })
  director.handle(message('12', 'VICTIM'))
  expect(director.view.bubbles.SCAMMER?.done).toBe(true)
  expect(director.view.bubbles.VICTIM).not.toBeNull()
})

it('freezes audio and receipts in settings, then continues without replaying', async () => {
  const { director, deps, audios } = setup()
  director.sceneDone()
  await vi.advanceTimersByTimeAsync(CARDS_MS)
  director.handle(message('11', 'SCAMMER'))
  director.freeze()
  expect(audios[0]!.paused).toBe(true)
  audios[0]!.finish() // ended fires while frozen: the receipt must wait
  expect(deps.receipts.message).not.toHaveBeenCalled()
  director.unfreeze()
  expect(deps.receipts.message).toHaveBeenCalledTimes(1)
  expect(audios).toHaveLength(1)
})

it('muting keeps the same timeline and still completes on ended', () => {
  const { director, deps, audios } = setup()
  director.sceneDone()
  vi.advanceTimersByTime(CARDS_MS)
  director.setMuted(true)
  director.handle(message('11', 'SCAMMER'))
  expect(audios[0]!.muted).toBe(true)
  audios[0]!.finish()
  expect(deps.receipts.message).toHaveBeenCalledWith('11', { status: 'COMPLETED' })
})


it('labels early termination separately and offers no report action', async () => {
  const { mount } = await import('@vue/test-utils')
  const { default: TerminalNotice } = await import('../components/simulation/TerminalNotice.vue')
  for (const code of ['FUNDS_EXHAUSTED', 'CHARACTER_EXIT', 'USER_EXIT', 'TIME_LIMIT']) {
    const wrapper = mount(TerminalNotice, { props: { code, message: '原因' }, global: { stubs: { RouterLink: true } } })
    expect(wrapper.get('h2').text()).toBe(['FUNDS_EXHAUSTED', 'CHARACTER_EXIT'].includes(code) ? '演绎提前终止' : '演绎已中断')
    expect(wrapper.findAll('router-link-stub').map(link => link.attributes('to'))).toEqual(['/configuration', '/home'])
    wrapper.unmount()
  }
})


it('keeps risk unscored initially and shows only the matching color after scoring', async () => {
  const { mount } = await import('@vue/test-utils')
  const { default: RiskNodeBar } = await import('../components/simulation/RiskNodeBar.vue')
  const wrapper = mount(RiskNodeBar, { props: { risk: null } })
  expect(wrapper.text()).toContain('尚未评分')
  for (const [risk, color] of [[0,'#3f8f6f'],[25,'#3978b8'],[50,'#d9a23a'],[75,'#d9733a'],[100,'#c43d33']] as const) {
    await wrapper.setProps({ risk })
    expect(wrapper.text()).not.toContain('尚未评分')
    expect(wrapper.find('.value').exists()).toBe(false)
    const expected = document.createElement('span'); expected.style.background = color
    expect(wrapper.get<HTMLElement>('.cursor').element.style.background).toBe(expected.style.background)
  }
  await wrapper.setProps({ risk: 60 })
  const sixty = wrapper.get<HTMLElement>('.cursor').element.style.left
  expect(wrapper.text()).toContain('60 / 100')
  await wrapper.setProps({ risk: 70 })
  expect(wrapper.text()).toContain('70 / 100')
  expect(wrapper.get<HTMLElement>('.cursor').element.style.left).not.toBe(sixty)
  expect(wrapper.get<HTMLElement>('.cursor').element.style.background).not.toBe('')
  wrapper.unmount()
})


it('recovers stalled audio once and completes only after actual ended', async () => {
  const { director, deps, audios } = setup()
  director.sceneDone()
  await vi.advanceTimersByTimeAsync(CARDS_MS)
  director.handle(message('12', 'VICTIM'))
  audios[0]!.currentMs = 300
  await vi.advanceTimersByTimeAsync(11_000)
  expect(audios[0]!.recoveries).toBe(1)
  expect(audios[0]!.currentMs).toBe(300)
  expect(deps.receipts.message).not.toHaveBeenCalled()
  audios[0]!.currentMs = 800
  await vi.advanceTimersByTimeAsync(100)
  audios[0]!.finish()
  expect(deps.receipts.message).toHaveBeenCalledExactlyOnceWith('12', { status: 'COMPLETED' })
  director.stop()
})

it('fails persistently stalled audio after one recovery without a completed receipt', async () => {
  const { director, deps, audios } = setup()
  director.sceneDone()
  await vi.advanceTimersByTimeAsync(CARDS_MS)
  director.handle(message('12', 'VICTIM'))
  await vi.advanceTimersByTimeAsync(22_000)
  expect(audios[0]!.recoveries).toBe(1)
  expect(deps.receipts.message).toHaveBeenCalledExactlyOnceWith('12', { status: 'FAILED', errorCode: 'AUDIO_PLAYBACK_FAILED' })
  director.stop()
})

it('retries a rejected initial play once before reporting a failed presentation', async () => {
  const play = vi.spyOn(FakeAudio.prototype, 'play').mockRejectedValueOnce(new DOMException('Interrupted', 'AbortError'))
  const { director, deps, audios } = setup()
  try {
    director.sceneDone()
    await vi.advanceTimersByTimeAsync(CARDS_MS)
    director.handle(message('12', 'VICTIM'))
    await vi.advanceTimersByTimeAsync(1)
    expect(audios[0]!.recoveries).toBe(1)
    expect(deps.receipts.message).not.toHaveBeenCalled()
    audios[0]!.finish()
    expect(deps.receipts.message).toHaveBeenCalledExactlyOnceWith('12', { status: 'COMPLETED' })
  } finally { play.mockRestore(); director.stop() }
})

it('retries an audio load error only once and still reports persistent failure', async () => {
  const { director, deps, audios } = setup()
  director.sceneDone()
  await vi.advanceTimersByTimeAsync(CARDS_MS)
  director.handle(message('12', 'VICTIM'))
  audios[0]!.error()
  expect(audios[0]!.recoveries).toBe(1)
  expect(deps.receipts.message).not.toHaveBeenCalled()
  audios[0]!.error()
  expect(deps.receipts.message).toHaveBeenCalledExactlyOnceWith('12', { status: 'FAILED', errorCode: 'AUDIO_LOAD_FAILED' })
  director.stop()
})

it('does not recover audio while settings are frozen', async () => {
  const { director, deps, audios } = setup()
  director.sceneDone()
  await vi.advanceTimersByTimeAsync(CARDS_MS)
  director.handle(message('12', 'VICTIM'))
  director.freeze()
  await vi.advanceTimersByTimeAsync(22_000)
  expect(audios[0]!.recoveries).toBe(0)
  expect(deps.receipts.message).not.toHaveBeenCalled()
  director.unfreeze()
  await vi.advanceTimersByTimeAsync(100)
  expect(audios[0]!.recoveries).toBe(0)
  audios[0]!.finish()
  director.stop()
})


it('ignores the initial play rejection after audio recovery starts', async () => {
  let rejectInitial!: (reason: Error) => void
  const play = vi.spyOn(FakeAudio.prototype, 'play').mockImplementationOnce(() => new Promise<void>((_resolve, reject) => { rejectInitial = reject }))
  const { director, deps, audios } = setup()
  try {
    director.sceneDone()
    await vi.advanceTimersByTimeAsync(CARDS_MS)
    director.handle(message('12', 'VICTIM'))
    await vi.advanceTimersByTimeAsync(11_000)
    expect(audios[0]!.recoveries).toBe(1)
    rejectInitial(new DOMException('Reload aborted old playback', 'AbortError'))
    await vi.advanceTimersByTimeAsync(1)
    expect(deps.receipts.message).not.toHaveBeenCalled()
    audios[0]!.finish()
    expect(deps.receipts.message).toHaveBeenCalledExactlyOnceWith('12', { status: 'COMPLETED' })
  } finally {
    play.mockRestore()
    director.stop()
  }
})


it('shows the three-column narration card with real saved trust changes', async () => {
  const { mount } = await import('@vue/test-utils')
  const { default: NarrationPanel } = await import('../components/simulation/NarrationPanel.vue')
  const wrapper = mount(NarrationPanel, { props: { narration: null, risk: null } })
  expect(wrapper.find('[data-testid="trust-change"]').exists()).toBe(false)
  expect(wrapper.text()).toContain('每轮双方说完后')
  const analysis = { fraudStage: '建立信任', speechAnalysis: '完整话术分析', methodAnalysis: '完整手段分析',
    victimPsychology: '心理', riskScore: 25 as const, riskReason: '依据', savedAt: '', presentedAt: null }

  // The real card binds the short fields, never the full analyses.
  await wrapper.setProps({ narration: { roundNo: 1, analysis: { ...analysis, methodShort: '冒充群助理',
    intentShort: '借群身份，让对方放下戒心', trustChange: { before: 50, after: 62, delta: 12 } } } })
  expect(wrapper.get('[data-testid="narration-method"]').text()).toBe('冒充群助理')
  expect(wrapper.get('[data-testid="narration-intent"]').text()).toBe('借群身份，让对方放下戒心')
  expect(wrapper.get('[data-testid="narration-panel"]').text()).not.toContain('完整手段分析')
  expect(wrapper.get('[data-testid="narration-panel"]').text()).not.toContain('完整话术分析')
  expect(wrapper.text()).toContain('第 1 轮解说')
  expect(wrapper.get('[data-testid="narration-stage"]').text()).toBe('建立信任')

  const cases: [number, number, number, string, string][] = [
    [50, 62, 12, '+12', '受骗倾向上升'],
    [62, 56, -6, '−6', '受骗倾向下降'],
    [56, 56, 0, '0', '受骗倾向不变'],
  ]
  for (const [before, after, delta, deltaText, trend] of cases) {
    await wrapper.setProps({ narration: { roundNo: 2, analysis: { ...analysis, methodShort: '冒充群助理',
      intentShort: '借群身份，让对方放下戒心', trustChange: { before, after, delta } } } })
    const card = wrapper.get('[data-testid="trust-change"]')
    expect(card.text()).toContain(deltaText)
    expect(wrapper.get('[data-testid="narration-panel"]').text()).toContain(`${before} → ${after}`)
    expect(wrapper.get('[data-testid="narration-panel"]').text()).toContain(trend)
    expect(wrapper.get('[data-testid="narration-panel"]').text()).toContain('仅供观看，角色不可见')
  }

  // A record without short fields falls back to the stored full analyses instead of inventing copy.
  await wrapper.setProps({ narration: { roundNo: 3, analysis: { ...analysis, trustChange: null } } })
  expect(wrapper.get('[data-testid="narration-method"]').text()).toBe('完整手段分析')
  expect(wrapper.get('[data-testid="narration-intent"]').text()).toBe('完整话术分析')
  expect(wrapper.text()).toContain('暂无受骗倾向数据')
  expect(wrapper.find('[data-testid="trust-change"]').exists()).toBe(false)
  wrapper.unmount()
})

it('opens draft editing only on request and keeps revisions open', async () => {
  const { mount } = await import('@vue/test-utils')
  const { default: MagicOrbPanel } = await import('../components/simulation/MagicOrbPanel.vue')
  const draft = { draftId: '11', sessionId: '5', roundNo: 2, roundPhase: 'SCAMMER_REVIEW' as const, revisionNo: 3,
    status: 'DRAFT' as const, originalContent: '原始台词', originalAction: null,
    draftContent: '第二轮草稿台词', draftAction: '发送材料', guidance: null, updatedAt: '' }
  const wrapper = mount(MagicOrbPanel, { props: { draft, busy: false } })
  expect(wrapper.find('#magic-orb-panel').exists()).toBe(false)
  expect(wrapper.get('[data-testid="magic-orb-toggle"]').text()).toBe('')
  await wrapper.get('[data-testid="magic-orb-toggle"]').trigger('click')
  expect(wrapper.get('[data-testid="magic-orb-draft"]').text()).toBe('第二轮草稿台词')
  expect(wrapper.get('[data-testid="magic-orb-action"]').text()).toContain('发送材料')
  expect(wrapper.text()).toContain('第 2 轮待确认')
  expect(wrapper.text()).toContain('未确认时刷新将按主动退出处理')
  expect(wrapper.text()).toContain('v3')

  expect(wrapper.find('[data-testid="magic-orb-continue"]').exists()).toBe(false)
  await wrapper.get('[data-testid="magic-orb-edit"]').trigger('click')
  await wrapper.get('[data-testid="magic-orb-editor"]').setValue('管理员改写后的台词')
  await wrapper.get('[data-testid="magic-orb-edit"]').trigger('click')
  expect(wrapper.emitted('edit')?.[0]).toEqual(['管理员改写后的台词'])
  expect(wrapper.get('[data-testid="magic-orb-action"]').text()).toContain('发送材料')
  await wrapper.setProps({ draft: { ...draft, revisionNo: 4, draftContent: '管理员改写后的台词' } })
  expect(wrapper.find('#magic-orb-panel').exists()).toBe(true)
  await wrapper.get('[data-testid="magic-orb-edit"]').trigger('click')
  await wrapper.get('[data-testid="magic-orb-editor"]').setValue('不满意的输入')
  await wrapper.get('[data-testid="magic-orb-cancel"]').trigger('click')
  expect(wrapper.get('[data-testid="magic-orb-draft"]').text()).toBe('管理员改写后的台词')
  expect(wrapper.emitted('edit')).toHaveLength(1)
  await wrapper.get('[data-testid="magic-orb-restore"]').trigger('click')
  expect(wrapper.emitted('edit')?.[1]).toEqual([draft.originalContent])
  await wrapper.setProps({ draft: { ...draft, revisionNo: 5, draftContent: draft.originalContent } })
  expect(wrapper.get('[data-testid="magic-orb-draft"]').text()).toBe(draft.originalContent)
  await wrapper.get('[data-testid="magic-orb-guidance"]').setValue('更贴近客服身份')
  await wrapper.get('[data-testid="magic-orb-regenerate"]').trigger('click')
  expect(wrapper.emitted('regenerate')?.[0]).toEqual(['更贴近客服身份'])

  // While a request is in flight every decision button is disabled.
  await wrapper.setProps({ busy: true })
  expect(wrapper.get('[data-testid="magic-orb-edit"]').attributes('disabled')).toBeDefined()
  expect(wrapper.get('[data-testid="magic-orb-regenerate"]').attributes('disabled')).toBeDefined()
  wrapper.unmount()
})

it('continues from narration content or keyboard only while enabled', async () => {
  const { mount } = await import('@vue/test-utils')
  const { default: NarrationPanel } = await import('../components/simulation/NarrationPanel.vue')
  const wrapper = mount(NarrationPanel, { props: { narration: null, risk: null } })
  const panel = wrapper.get('[data-testid="narration-panel"]')
  await panel.trigger('click')
  expect(wrapper.emitted('continue')).toBeUndefined()
  await wrapper.setProps({ awaitingContinue: true })
  expect(wrapper.get('[data-testid="review-hint"]').text()).toBe('点击任意位置继续对话')
  await wrapper.get('.empty').trigger('click')
  await wrapper.get('[data-testid="risk-bar"]').trigger('click')
  await panel.trigger('keydown', { key: 'Enter' })
  await panel.trigger('keydown', { key: ' ' })
  expect(wrapper.emitted('continue')).toHaveLength(4)
  await wrapper.setProps({ disabled: true })
  await panel.trigger('click')
  await panel.trigger('keydown', { key: 'Enter' })
  expect(wrapper.emitted('continue')).toHaveLength(4)
  wrapper.unmount()
})

it.each(['left', 'right', 'top', 'bottom'])('docks the orb halfway onto the %s edge without opening it', async edge => {
  const { mount } = await import('@vue/test-utils')
  const { default: MagicOrbPanel } = await import('../components/simulation/MagicOrbPanel.vue')
  const wrapper = mount(MagicOrbPanel, { props: { draft: null, busy: false } })
  const orb = wrapper.get('[data-testid="magic-orb-toggle"]')
  Object.assign(orb.element, { setPointerCapture: () => {} })
  const width = window.innerWidth, height = window.innerHeight
  const target = { left: [0, height / 2], right: [width, height / 2], top: [width / 2, 0], bottom: [width / 2, height] }[edge]!
  // Start from the right edge; move inward first so a return to that edge is also a drag.
  await orb.trigger('pointerdown', { pointerId: 1, button: 0, clientX: width, clientY: height / 2 })
  await orb.trigger('pointermove', { pointerId: 1, clientX: width / 2, clientY: height / 2 })
  await orb.trigger('pointermove', { pointerId: 1, clientX: target[0], clientY: target[1] })
  await orb.trigger('pointerup', { pointerId: 1 })
  orb.element.dispatchEvent(new MouseEvent('click', { bubbles: true, detail: 1 }))
  await wrapper.vm.$nextTick()
  expect(wrapper.find('#magic-orb-panel').exists()).toBe(false)
  const style = wrapper.get('[data-testid="magic-orb"]').attributes('style')
  expect(style).toContain(`left: calc(${target[0]! / width * 100}vw - 24px)`)
  expect(style).toContain(`top: calc(${target[1]! / height * 100}dvh - 24px)`)
  orb.element.dispatchEvent(new MouseEvent('click', { bubbles: true, detail: 1 }))
  await wrapper.vm.$nextTick()
  expect(wrapper.find('#magic-orb-panel').exists()).toBe(true)
  expect(wrapper.get('#magic-orb-panel').classes('panel-left')).toBe(edge === 'left')
  wrapper.unmount()
})
