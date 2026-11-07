import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import SimulationView from '../views/SimulationView.vue'
import SimulationStage from '../components/simulation/SimulationStage.vue'
import { useAuthStore } from '../stores/auth'
import { contentApi } from '../api/content'
import { simulationApi } from '../api/simulation'
import { adminApi } from '../api/admin'
import { ApiError } from '../api/http'
import { CARDS_MS, PerformanceDirector } from '../simulation/performanceDirector'
import type { Configuration, ScammerDraft, SimulationEventEnvelope, SimulationState } from '../api/types'

const push = vi.hoisted(() => vi.fn())
vi.mock('vue-router', () => ({
  useRoute: () => ({ query: { scenarioId: '1', scammerProfileId: '1', victimProfileId: '1' } }),
  useRouter: () => ({ push, replace: vi.fn() }), onBeforeRouteLeave: vi.fn(),
}))
vi.mock('../simulation/assetPreloader', () => ({ preloadImages: () => Promise.resolve() }))

const common = { id: '1', age: 30, gender: '男', identity: '身份', description: '描述', contextTime: '', contextPlace: '', currentState: '',
  avatar: { key: 'a', url: '/a.png' }, speakingAvatar: { key: 'b', url: '/b.png' } }
const configuration: Configuration = {
  scenario: { id: '1', code: 'IMPERSONATION_LOAN', name: '冒充亲友借款', description: '场景', focus: '' },
  scammer: { ...common, name: '骗子', fraudExperience: 'HIGH', speechSkill: 'HIGH', manipulationSkill: 'HIGH', behaviorTraits: '' },
  victim: { ...common, name: '受害者', antiFraudAwareness: 'HIGH', riskTendency: 'LOW', personalityTraits: '' },
}
const draft: ScammerDraft = { draftId: '11', sessionId: '5', roundNo: 2, roundPhase: 'SCAMMER_REVIEW', revisionNo: 1,
  status: 'DRAFT', originalContent: '先转2000元应急', originalAction: null, draftContent: '先转2000元应急', draftAction: null, guidance: null, updatedAt: '' }

function state(review: boolean): SimulationState {
  return { sessionId: '5', status: 'RUNNING', stateVersion: '1', startedAt: '', deadlineAt: null, endedAt: null,
    currentRoundNo: 0, configuration, currentRound: null, latestRiskScore: null, outcome: null, interruption: null,
    readingUntil: null, nextAction: null, paused: false, pausedAt: null, pausedCreditMs: 0, report: null,
    funds: { fundsRecorded: false, initialAvailableYuan: null, availableBalanceYuan: null, totalPaidYuan: null,
      totalRefundedYuan: null, actualLossYuan: null, additionalPaymentCount: null, reserveReleasedYuan: null, flows: null },
    intervention: review ? { sessionId: '5', interventionMode: 'ADMIN_TEST', currentRoundNo: 0, phase: null,
      currentDraft: null, pendingInterventionCount: 0, canAdopt: false, canRegenerate: false } : null }
}

let receive: (event: SimulationEventEnvelope) => void
beforeEach(() => {
  vi.useFakeTimers()
  sessionStorage.clear()
  setActivePinia(createPinia())
  vi.spyOn(contentApi, 'listScenarios').mockResolvedValue([configuration.scenario])
  vi.spyOn(contentApi, 'getScammerProfile').mockResolvedValue(configuration.scammer)
  vi.spyOn(contentApi, 'getVictimProfile').mockResolvedValue(configuration.victim)
  vi.spyOn(simulationApi, 'openStream').mockImplementation((_id, callback) => { receive = callback; return vi.fn() })
  vi.spyOn(simulationApi, 'presentOpening').mockResolvedValue({ effect: 'APPLIED', state: state(true) })
})
afterEach(() => { vi.restoreAllMocks(); vi.useRealTimers(); sessionStorage.clear() })

async function open(admin: boolean, review = true) {
  useAuthStore().account = { id: '7', username: '测试', role: admin ? 'ADMIN' : 'USER' }
  vi.spyOn(simulationApi, 'start').mockResolvedValue(state(review))
  const wrapper = mount(SimulationView, { global: { stubs: { RouterLink: true } } })
  await flushPromises()
  await wrapper.get('[data-testid="simulation-start"]').trigger('click')
  await flushPromises()
  wrapper.findComponent({ name: 'OpeningScene' }).vm.$emit('done')
  await vi.advanceTimersByTimeAsync(CARDS_MS)
  return wrapper
}

it('flaps the stage sprite only for the active speaker and respects an uploaded frame', async () => {
  const scammer = { ...configuration.scammer,
    avatar: { ...common.avatar, key: '48d6c708ae546f4d0ca4d17d9f98eda89177ddcc480133070a89f7e42dc642ec' },
    speakingAvatar: { ...common.speakingAvatar, key: 'd32624d7261baaeb14726fdcc1332a1b1865b57341d6c0e6b1f940f7b2b849e5' } }
  const selected = { ...configuration, scammer }
  const view = PerformanceDirector.initialView()
  const wrapper = mount(SimulationStage, { props: { configuration: selected, view } })
  const left = wrapper.get('[data-testid="stage-character-left"]')
  expect(left.get('.sprite').classes()).not.toContain('mouth-open')

  await wrapper.setProps({ view: { ...view, speaking: 'SCAMMER', mouthOpen: true } })
  expect(left.get('.sprite').classes()).toContain('mouth-open')
  expect(wrapper.get('[data-testid="stage-character-right"] .open').classes()).not.toContain('show')

  await wrapper.setProps({ configuration: { ...selected, scammer: { ...scammer, speakingAvatar: common.speakingAvatar } } })
  expect(left.find('.sprite').exists()).toBe(false)
  expect(left.get('.open.show').attributes('src')).toBe(common.speakingAvatar.url)
  wrapper.unmount()
})

it.each([true, false])('wires draft events to the floating orb and waits for a click (admin=%s)', async admin => {
  const wrapper = await open(admin)
  const adopt = vi.spyOn(simulationApi, 'adoptDraft').mockResolvedValue({ ...state(true), stateVersion: '3', currentRoundNo: 2,
    currentRound: { roundNo: 2, phase: 'SCAMMER_PRESENTING', messages: [], analysis: null,
      interactionComplete: false, presentationComplete: false, readingUntil: null } })
  const getDraft = vi.spyOn(simulationApi, 'intervention')
  expect(wrapper.find('[data-testid="magic-orb-toggle"]').exists()).toBe(true)
  receive({ type: 'intervention.draft', eventId: 'd1', sessionId: '5', stateVersion: '2',
    data: { roundNo: 2, draft, pendingInterventionCount: 0 } })
  await flushPromises()
  expect(wrapper.find('[data-testid="bubble-left"]').exists()).toBe(false)
  expect(wrapper.find('[data-testid="bubble-right"]').exists()).toBe(false)
  expect(wrapper.find('[data-testid="review-hint"]').exists()).toBe(true)
  expect(wrapper.find('#magic-orb-panel').exists()).toBe(false)
  await wrapper.get('[data-testid="magic-orb-toggle"]').trigger('click')
  expect(wrapper.get('[data-testid="magic-orb-draft"]').text()).toBe(draft.draftContent)
  expect(wrapper.find('[data-testid="bubble-left"]').exists()).toBe(false)
  expect(wrapper.find('[data-testid="magic-orb-pending"]').exists()).toBe(admin)
  await wrapper.get('[data-testid="magic-orb-toggle"]').trigger('click')
  expect(getDraft).not.toHaveBeenCalled()
  await vi.advanceTimersByTimeAsync(60_000)
  expect(adopt).not.toHaveBeenCalled()
  expect(wrapper.find('[data-testid="bubble-right"]').exists()).toBe(false)
  await wrapper.get('[data-testid="narration-panel"]').trigger('click')
  await wrapper.get('[data-testid="narration-panel"]').trigger('click')
  await flushPromises()
  expect(adopt).toHaveBeenCalledExactlyOnceWith('5', { draftId: '11', expectedRevisionNo: 1, continue: true })
  expect(wrapper.find('[data-testid="review-hint"]').exists()).toBe(false)
  expect(wrapper.find('[data-testid="magic-orb-toggle"]').exists()).toBe(true)
  wrapper.unmount()
})

it('shows adopted changes inside the orb and returns without leaving or pausing', async () => {
  const wrapper = await open(true)
  receive({ type: 'intervention.draft', eventId: 'pending-draft', sessionId: '5', stateVersion: '2',
    data: { roundNo: 2, draft, pendingInterventionCount: 1 } })
  const record = { interventionId: '1', sessionId: '5', sourceSessionId: '5', roundNo: 1,
    sceneCode: 'IMPERSONATION_LOAN', originalContent: '原来AI给的台词', finalContent: '管理员最终采用的台词',
    previousContext: '骗子：你好\n受害者：什么事', interventionNote: null, status: 'ADOPTED' as const, createdAt: '', ruleId: null }
  vi.spyOn(adminApi, 'interventions').mockResolvedValue([record, { ...record, interventionId: '2', sessionId: '6', finalContent: '其他场次' }])
  const pause = vi.spyOn(simulationApi, 'pause')
  const exit = vi.spyOn(simulationApi, 'exit')
  push.mockClear()
  await flushPromises()
  await wrapper.get('[data-testid="magic-orb-toggle"]').trigger('click')
  await wrapper.get('[data-testid="magic-orb-pending"]').trigger('click')
  await flushPromises()
  expect(wrapper.findAll('[data-testid="magic-orb-pending-record"]')).toHaveLength(1)
  expect(wrapper.get('#magic-orb-panel').classes()).toContain('panel-pending')
  expect(wrapper.get('.pending-scroll').attributes('tabindex')).toBe('0')
  expect(wrapper.get('#magic-orb-panel').text()).toContain('管理员最终采用的台词')
  expect(wrapper.get('#magic-orb-panel').text()).toContain('受害者：什么事')
  expect(wrapper.find('[data-testid="magic-orb-draft"]').exists()).toBe(false)
  await wrapper.get('[data-testid="magic-orb-back"]').trigger('click')
  expect(wrapper.get('[data-testid="magic-orb-draft"]').text()).toBe(draft.draftContent)
  expect(push).not.toHaveBeenCalled()
  expect(pause).not.toHaveBeenCalled()
  expect(exit).not.toHaveBeenCalled()
  wrapper.unmount()
})

it('keeps legacy automatic sessions without a review gate', async () => {
  const wrapper = await open(false, false)
  expect(wrapper.find('[data-testid="magic-orb"]').exists()).toBe(false)
  expect(wrapper.find('[data-testid="review-hint"]').exists()).toBe(false)
  wrapper.unmount()
})

it.each(['http-first', 'event-first', 'failure'])('keeps the orb open throughout regeneration (%s)', async order => {
  const wrapper = await open(false)
  receive({ type: 'intervention.draft', eventId: 'd1', sessionId: '5', stateVersion: '2',
    data: { roundNo: 2, draft, pendingInterventionCount: 0 } })
  await flushPromises()
  await wrapper.get('[data-testid="magic-orb-toggle"]').trigger('click')
  let respond!: (value: NonNullable<SimulationState['intervention']>) => void
  let reject!: (error: Error) => void
  const regenerate = vi.spyOn(simulationApi, 'regenerateDraft').mockImplementation(() => new Promise((resolve, fail) => {
    respond = resolve; reject = fail
  }))
  const adopt = vi.spyOn(simulationApi, 'adoptDraft')
  await wrapper.get('[data-testid="magic-orb-guidance"]').setValue('先解释流程')
  await wrapper.get('[data-testid="magic-orb-regenerate"]').trigger('click')
  expect(wrapper.get('[data-testid="magic-orb-regenerating"]').text()).toBe('正在重新生成话术与动作…')
  expect(wrapper.get('[data-testid="magic-orb-toggle"]').attributes('aria-expanded')).toBe('true')
  expect(wrapper.find('[data-testid="magic-orb-draft"]').exists()).toBe(false)
  await wrapper.get('[data-testid="narration-panel"]').trigger('click')
  expect(adopt).not.toHaveBeenCalled()
  expect(regenerate).toHaveBeenCalledExactlyOnceWith('5', { draftId: '11', expectedRevisionNo: 1, guidance: '先解释流程' })
  if (order === 'failure') {
    reject(new Error('重新生成失败，请重试'))
    await flushPromises()
    expect(wrapper.get('[data-testid="magic-orb-draft"]').text()).toBe(draft.draftContent)
    expect(wrapper.get('[data-testid="magic-orb-error"]').text()).toContain('重新生成失败')
    expect(wrapper.get('[data-testid="magic-orb-regenerate"]').attributes('disabled')).toBeUndefined()
    wrapper.unmount()
    return
  }
  const next = { ...draft, draftId: '12', revisionNo: 2, draftContent: '重新生成的台词', draftAction: '发送新的材料' }
  const deliverDraft = () => receive({ type: 'intervention.draft', eventId: 'd2', sessionId: '5', stateVersion: '3',
    data: { roundNo: 2, draft: next, pendingInterventionCount: 0 } })
  if (order === 'event-first') { deliverDraft(); await flushPromises() }
  respond({ ...state(true).intervention!, currentRoundNo: 2, phase: 'SCAMMER_GENERATING', currentDraft: null })
  await flushPromises()
  if (order === 'http-first') {
    expect(wrapper.get('[data-testid="magic-orb-regenerating"]').text()).toBe('正在重新生成话术与动作…')
    expect(wrapper.get('[data-testid="magic-orb-toggle"]').attributes('aria-expanded')).toBe('true')
    deliverDraft()
    await flushPromises()
  }
  expect(wrapper.find('[data-testid="magic-orb-regenerating"]').exists()).toBe(false)
  expect(wrapper.get('[data-testid="magic-orb-draft"]').text()).toBe(next.draftContent)
  expect(wrapper.get('[data-testid="magic-orb-action"]').text()).toContain(next.draftAction)
  expect(wrapper.get('[data-testid="magic-orb-guidance"]').element).toHaveProperty('value', '先解释流程')
  expect(wrapper.get('[data-testid="magic-orb-toggle"]').attributes('aria-expanded')).toBe('true')
  wrapper.unmount()
})

it.each([true, false])('keeps both previous lines visible while previewing and editing the next round (admin=%s)', async admin => {
  const wrapper = await open(admin)
  const savedLine = (id: string, speaker: 'SCAMMER' | 'VICTIM', content: string) => ({
    id, roundNo: 1, speaker, content, actionText: null, presentationStatus: 'COMPLETED' as const,
    presentationCompletedAt: '', presentationErrorCode: null, savedAt: '', audioUrl: null, audioDurationMs: null, segments: null,
  })
  const previous = { ...state(true), stateVersion: '2', currentRoundNo: 1, currentRound: {
    roundNo: 1, phase: 'COMPLETE' as const, messages: [savedLine('21', 'SCAMMER', '上一轮骗子的话'), savedLine('22', 'VICTIM', '上一轮受害者的回应')],
    analysis: null, interactionComplete: true, presentationComplete: true, readingUntil: null,
  } }
  receive({ type: 'session.state', eventId: 's1', sessionId: '5', stateVersion: '2', data: { state: previous,
    reportView: { sessionId: '5', simulationStatus: 'RUNNING', report: null, unavailableReason: 'SIMULATION_RUNNING',
      outcome: null, content: null, riskSeries: [] } } })
  receive({ type: 'round.started', eventId: 'r2', sessionId: '5', stateVersion: '3',
    data: { roundNo: 2, phase: 'SCAMMER_GENERATING', startedAt: '', deadlineAt: '' } })
  const nextDraft = { ...draft, draftId: '31', roundNo: 2, draftContent: '下一轮的骗子草稿' }
  receive({ type: 'intervention.draft', eventId: 'd2', sessionId: '5', stateVersion: '4',
    data: { roundNo: 2, draft: nextDraft, pendingInterventionCount: 0 } })
  await flushPromises()
  const expectPrevious = () => {
    expect(wrapper.get('[data-testid="bubble-left"]').text()).toBe('上一轮骗子的话')
    expect(wrapper.get('[data-testid="bubble-right"]').text()).toBe('上一轮受害者的回应')
    expect(wrapper.get('.play-meta').text()).toContain('第 1 轮')
  }
  expectPrevious()
  expect(wrapper.find('#magic-orb-panel').exists()).toBe(false)
  await wrapper.get('[data-testid="magic-orb-toggle"]').trigger('click')
  expect(wrapper.get('[data-testid="magic-orb-draft"]').text()).toBe(nextDraft.draftContent)
  expectPrevious()
  const revised = { ...nextDraft, revisionNo: 2, draftContent: '根据上一轮回应修改后的台词' }
  const intervention = { ...state(true).intervention!, currentRoundNo: 2, currentDraft: revised }
  const revise = vi.spyOn(simulationApi, 'reviseDraft').mockResolvedValue(intervention)
  await wrapper.get('[data-testid="magic-orb-edit"]').trigger('click')
  await wrapper.get('[data-testid="magic-orb-editor"]').setValue(revised.draftContent)
  await wrapper.get('[data-testid="magic-orb-edit"]').trigger('click')
  await flushPromises()
  expect(revise).toHaveBeenCalledExactlyOnceWith('5', { draftId: '31', expectedRevisionNo: 1, content: revised.draftContent })
  expectPrevious()
  const regenerated = { ...revised, revisionNo: 3, draftContent: '重新生成后的台词', draftAction: '发送新的平台备案截图' }
  const regenerate = vi.spyOn(simulationApi, 'regenerateDraft').mockResolvedValue({ ...intervention, currentDraft: regenerated })
  await wrapper.get('[data-testid="magic-orb-guidance"]').setValue('先解释流程，再发送备案截图')
  await wrapper.get('[data-testid="magic-orb-regenerate"]').trigger('click')
  await flushPromises()
  expect(wrapper.get('[data-testid="magic-orb-draft"]').text()).toBe(regenerated.draftContent)
  expect(wrapper.get('[data-testid="magic-orb-action"]').text()).toContain(regenerated.draftAction)
  expect(regenerate).toHaveBeenCalledExactlyOnceWith('5', { draftId: '31', expectedRevisionNo: 2, guidance: '先解释流程，再发送备案截图' })
  expectPrevious()
  const adopt = vi.spyOn(simulationApi, 'adoptDraft').mockResolvedValue({ ...state(true), stateVersion: '7', currentRoundNo: 2,
    currentRound: { roundNo: 2, phase: 'SCAMMER_PRESENTING', messages: [], analysis: null,
      interactionComplete: false, presentationComplete: false, readingUntil: null } })
  await wrapper.get('[data-testid="narration-panel"]').trigger('click')
  await flushPromises()
  expect(adopt).toHaveBeenCalledExactlyOnceWith('5', { draftId: '31', expectedRevisionNo: 3, continue: true })
  expect(wrapper.get('.play-meta').text()).toContain('第 2 轮')
  expect(wrapper.find('[data-testid="bubble-left"]').exists()).toBe(false)
  expect(wrapper.find('[data-testid="bubble-right"]').exists()).toBe(false)
  expect(wrapper.find('#magic-orb-panel').exists()).toBe(false)
  wrapper.unmount()
})

it('blocks narration clicks while editing or submitting', async () => {
  const wrapper = await open(false)
  let complete!: (value: SimulationState) => void
  const adopt = vi.spyOn(simulationApi, 'adoptDraft').mockImplementation(() => new Promise(resolve => { complete = resolve }))
  receive({ type: 'intervention.draft', eventId: 'd1', sessionId: '5', stateVersion: '2',
    data: { roundNo: 2, draft, pendingInterventionCount: 0 } })
  await flushPromises()
  await wrapper.get('[data-testid="magic-orb-toggle"]').trigger('click')
  await wrapper.get('[data-testid="magic-orb-edit"]').trigger('click')
  await wrapper.get('[data-testid="narration-panel"]').trigger('click')
  expect(adopt).not.toHaveBeenCalled()
  vi.spyOn(simulationApi, 'reviseDraft').mockResolvedValue({ ...state(true).intervention!, currentDraft: { ...draft, revisionNo: 2 } })
  await wrapper.get('[data-testid="magic-orb-edit"]').trigger('click')
  await flushPromises()
  await wrapper.get('[data-testid="narration-panel"]').trigger('click')
  await wrapper.get('[data-testid="narration-panel"]').trigger('keydown', { key: 'Enter' })
  expect(adopt).toHaveBeenCalledExactlyOnceWith('5', { draftId: '11', expectedRevisionNo: 2, continue: true })
  complete({ ...state(true), stateVersion: '4' })
  await flushPromises()
  expect(wrapper.find('#magic-orb-panel').exists()).toBe(false)
  wrapper.unmount()
})

it('shows the specific rejection in narration and keeps the draft available for retry', async () => {
  const wrapper = await open(false)
  vi.spyOn(simulationApi, 'adoptDraft').mockRejectedValue(new ApiError(400, 'VALIDATION_FAILED', '请检查输入信息',
    [{ field: 'content', code: 'CONTENT_REJECTED', message: '台词与上一轮完全重复，请修改后重试' }]))
  receive({ type: 'intervention.draft', eventId: 'd1', sessionId: '5', stateVersion: '2',
    data: { roundNo: 2, draft, pendingInterventionCount: 0 } })
  await flushPromises()
  await wrapper.get('[data-testid="narration-panel"]').trigger('click')
  await flushPromises()
  expect(wrapper.get('[data-testid="dialogue-error"]').text()).toBe('台词与上一轮完全重复，请修改后重试')
  expect(wrapper.find('#magic-orb-panel').exists()).toBe(false)
  expect(wrapper.find('[data-testid="review-hint"]').exists()).toBe(true)
  expect(wrapper.find('[data-testid="bubble-right"]').exists()).toBe(false)
  expect(wrapper.get('[data-testid="narration-panel"]').attributes('aria-disabled')).toBe('false')
  wrapper.unmount()
})

it('keeps rejected edits visible and shows the saved text only after a successful retry', async () => {
  const wrapper = await open(false)
  const activeDraft = { ...draft, draftAction: '发送原有材料' }
  receive({ type: 'intervention.draft', eventId: 'd1', sessionId: '5', stateVersion: '2',
    data: { roundNo: 2, draft: activeDraft, pendingInterventionCount: 0 } })
  await flushPromises()
  await wrapper.get('[data-testid="magic-orb-toggle"]').trigger('click')
  await wrapper.get('[data-testid="magic-orb-edit"]').trigger('click')
  const rejected = '医院已经给您发送短信，请马上缴费'
  await wrapper.get('[data-testid="magic-orb-editor"]').setValue(rejected)
  let rejectSave!: (error: Error) => void
  const revise = vi.spyOn(simulationApi, 'reviseDraft').mockImplementationOnce(() => new Promise((_resolve, reject) => { rejectSave = reject }))
  const adopt = vi.spyOn(simulationApi, 'adoptDraft')
  await wrapper.get('[data-testid="magic-orb-edit"]').trigger('click')
  expect(wrapper.get('[data-testid="magic-orb-edit"]').text()).toBe('保存中…')
  expect(wrapper.get<HTMLTextAreaElement>('[data-testid="magic-orb-editor"]').element.value).toBe(rejected)
  expect(wrapper.get('[data-testid="magic-orb-editor"]').attributes('disabled')).toBeDefined()
  expect(wrapper.find('[data-testid="magic-orb-draft"]').exists()).toBe(false)
  rejectSave(new ApiError(400, 'VALIDATION_FAILED', '请检查输入信息',
    [{ field: 'content', code: 'CONTENT_REJECTED', message: '正式历史未记录医院发送短信的动作' }]))
  await flushPromises()
  expect(wrapper.get<HTMLTextAreaElement>('[data-testid="magic-orb-editor"]').element.value).toBe(rejected)
  expect(wrapper.get('[data-testid="magic-orb-error"]').text()).toBe('保存失败：正式历史未记录医院发送短信的动作')
  expect(wrapper.get('[data-testid="magic-orb-edit"]').text()).toBe('保存修改')
  expect(wrapper.get('[data-testid="magic-orb-editor"]').attributes('disabled')).toBeUndefined()
  await wrapper.get('[data-testid="narration-panel"]').trigger('click')
  expect(adopt).not.toHaveBeenCalled()

  const saved = '爸，先看看我发送的材料，有问题我们再核对。'
  let finishSave!: (value: NonNullable<SimulationState['intervention']>) => void
  revise.mockImplementationOnce(() => new Promise(resolve => { finishSave = resolve }))
  await wrapper.get('[data-testid="magic-orb-editor"]').setValue(saved)
  await wrapper.get('[data-testid="magic-orb-edit"]').trigger('click')
  expect(wrapper.get<HTMLTextAreaElement>('[data-testid="magic-orb-editor"]').element.value).toBe(saved)
  finishSave({ ...state(true).intervention!, currentDraft: { ...activeDraft, revisionNo: 2, draftContent: saved } })
  await flushPromises()
  expect(revise).toHaveBeenLastCalledWith('5', { draftId: '11', expectedRevisionNo: 1, content: saved })
  expect(wrapper.find('[data-testid="magic-orb-editor"]').exists()).toBe(false)
  expect(wrapper.get('[data-testid="magic-orb-draft"]').text()).toBe(saved)
  expect(wrapper.get('[data-testid="magic-orb-action"]').text()).toContain(activeDraft.draftAction)
  expect(wrapper.find('[data-testid="magic-orb-error"]').exists()).toBe(false)
  adopt.mockResolvedValue({ ...state(true), stateVersion: '4', currentRoundNo: 2,
    currentRound: { roundNo: 2, phase: 'SCAMMER_PRESENTING', messages: [], analysis: null,
      interactionComplete: false, presentationComplete: false, readingUntil: null } })
  await wrapper.get('[data-testid="narration-panel"]').trigger('click')
  await flushPromises()
  expect(adopt).toHaveBeenCalledExactlyOnceWith('5', { draftId: '11', expectedRevisionNo: 2, continue: true })
  wrapper.unmount()
})

it.each([true, false])('automatically presents both sides of the first round without a continue click (admin=%s)', async admin => {
  const audios: { currentTime: number; onended: (() => void) | null; onerror: (() => void) | null }[] = []
  vi.stubGlobal('Audio', function () {
    const el = { currentTime: 0, ended: false, muted: false, src: '', preload: '', onended: null, onerror: null,
      play: () => Promise.resolve(), pause: () => {}, load: () => {}, removeAttribute: () => {} }
    audios.push(el)
    return el
  })
  const wrapper = await open(admin)
  try {
    const adopted = { ...state(true), currentRoundNo: 1, stateVersion: '3', currentRound: {
      roundNo: 1, phase: 'SCAMMER_PRESENTING' as const, messages: [], analysis: null,
      interactionComplete: false, presentationComplete: false, readingUntil: null } }
    const adopt = vi.spyOn(simulationApi, 'adoptDraft').mockResolvedValue(adopted)
    const line = (id: string, speaker: 'SCAMMER' | 'VICTIM', content: string): SimulationEventEnvelope => ({
      type: 'message.saved', eventId: id, sessionId: '5', stateVersion: '4', data: {
        id, roundNo: 1, speaker, content, actionText: null, presentationStatus: 'PRESENTING',
        presentationCompletedAt: null, presentationErrorCode: null, savedAt: '', audioUrl: '/local-audio', audioDurationMs: 1000,
        segments: [{ startMs: 0, endMs: 1000, charStart: 0, charEnd: Array.from(content).length }] } })
    const receipt = vi.spyOn(simulationApi, 'presentMessage').mockImplementation(async (_sessionId, id) => {
      if (id === '21') receive(line('22', 'VICTIM', '我先了解一下具体情况'))
      return { effect: 'APPLIED', state: adopted }
    })
    expect(audios).toHaveLength(0)
    receive(line('21', 'SCAMMER', draft.draftContent))
    await flushPromises()
    expect(wrapper.find('[data-testid="bubble-right"]').exists()).toBe(false)
    audios[0]!.onended?.()
    await flushPromises()
    expect(receipt).toHaveBeenCalledExactlyOnceWith('5', '21', { status: 'COMPLETED' })
    expect(audios).toHaveLength(2)
    audios[1]!.currentTime = 1
    await vi.advanceTimersByTimeAsync(50)
    expect(wrapper.get('[data-testid="bubble-right"]').text()).toContain('我先了解一下具体情况')
    expect(adopt).not.toHaveBeenCalled()
    expect(wrapper.find('[data-testid="review-hint"]').exists()).toBe(false)
  } finally { wrapper.unmount(); vi.unstubAllGlobals() }
})
