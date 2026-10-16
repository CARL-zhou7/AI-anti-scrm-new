import { describe, expect, it, vi } from 'vitest'
import { createPresenter, PresentationFailure, presentationTarget } from '../src/core/presenter.js'
import { applyEvent, applySnapshot, createSimulationStore } from '../src/core/stateStore.js'
import { analysis, baseState, event, message } from './fixtures.js'

const flush = () => new Promise((r) => setTimeout(r, 0))

function setup({ present } = {}) {
  const store = createSimulationStore()
  applySnapshot(store, baseState())
  const receipts = []
  const respond = (state) => Promise.resolve({ effect: 'APPLIED', state })
  const api = {
    presentOpening: vi.fn((id, r) => {
      receipts.push(['opening', r])
      return respond(baseState({ stateVersion: '1', currentRoundNo: 1 }))
    }),
    presentMessage: vi.fn((id, mid, r) => {
      receipts.push([`message:${mid}`, r])
      return respond(baseState({ stateVersion: '4', currentRoundNo: 1 }))
    }),
    presentAnalysis: vi.fn((id, roundNo, r) => {
      receipts.push([`analysis:${roundNo}`, r])
      return respond(baseState({ stateVersion: '8', currentRoundNo: 1 }))
    }),
  }
  const stage = { present: vi.fn(present || (() => Promise.resolve())) }
  const presenter = createPresenter({ store, api, stage, reconcile: vi.fn(() => Promise.resolve()) })
  return { store, api, stage, presenter, receipts }
}

describe('presentationTarget', () => {
  it('开场、PRESENTING 消息、未呈现分析依次成为呈现目标；生成中返回 null', () => {
    const store = createSimulationStore()
    applySnapshot(store, baseState())
    expect(presentationTarget(store).kind).toBe('opening')
    applyEvent(store, 'round.started', event(1, { roundNo: 1, phase: 'SCAMMER_GENERATING', startedAt: 'x', deadlineAt: 'y' }))
    expect(presentationTarget(store)).toBe(null)
    applyEvent(store, 'message.saved', event(2, message({ presentationStatus: 'PENDING' })))
    expect(presentationTarget(store)).toBe(null) // PENDING 不得播放
    applyEvent(store, 'message.saved', event(3, message()))
    expect(presentationTarget(store).key).toBe('message:1001')
    applyEvent(store, 'analysis.saved', event(6, { roundNo: 1, analysis: analysis() }))
    expect(presentationTarget(store).key).toBe('analysis:1')
  })

  it('终态没有呈现目标', () => {
    const store = createSimulationStore()
    applySnapshot(store, baseState({ status: 'INTERRUPTED' }))
    expect(presentationTarget(store)).toBe(null)
  })
})

describe('createPresenter', () => {
  it('呈现完成后提交 COMPLETED 回执，且同一目标只呈现一次', async () => {
    const { presenter, stage, receipts } = setup()
    presenter.sync()
    presenter.sync()
    await flush()
    expect(stage.present).toHaveBeenCalledTimes(1)
    expect(receipts).toEqual([['opening', { status: 'COMPLETED' }]])
  })

  it('呈现失败上报本作用域允许的错误码', async () => {
    const { presenter, receipts } = setup({ present: () => Promise.reject(new PresentationFailure('AVATAR_LOAD_FAILED')) })
    presenter.sync()
    await flush()
    expect(receipts).toEqual([['opening', { status: 'FAILED', errorCode: 'AVATAR_LOAD_FAILED' }]])
  })

  it('不属于本作用域的错误码不会被提交（分析回执只允许 TEXT_RENDER_FAILED）', async () => {
    const { store, presenter, receipts } = setup({ present: () => Promise.reject(new PresentationFailure('AUDIO_LOAD_FAILED')) })
    applySnapshot(store, baseState({ stateVersion: '6', currentRoundNo: 1 }))
    applyEvent(store, 'analysis.saved', event(7, { roundNo: 1, analysis: analysis() }))
    presenter.sync()
    await flush()
    expect(receipts).toEqual([['analysis:1', { status: 'FAILED', errorCode: 'TEXT_RENDER_FAILED' }]])
  })

  it('到时 halt 后中止当前呈现且不提交回执', async () => {
    let seenSignal
    const { presenter, receipts } = setup({
      present: (target, signal) =>
        new Promise((resolve, reject) => {
          seenSignal = signal
          signal.addEventListener('abort', () => reject(new DOMException('aborted', 'AbortError')))
        }),
    })
    presenter.sync()
    presenter.halt()
    await flush()
    expect(seenSignal.aborted).toBe(true)
    expect(receipts).toEqual([])
  })

  it('演绎变为终态时中止正在进行的呈现', async () => {
    let seenSignal
    const { store, presenter, receipts } = setup({
      present: (target, signal) =>
        new Promise((resolve, reject) => {
          seenSignal = signal
          signal.addEventListener('abort', () => reject(new DOMException('aborted', 'AbortError')))
        }),
    })
    presenter.sync()
    applyEvent(store, 'session.interrupted', event(2, baseState({ status: 'INTERRUPTED', stateVersion: '2', interruption: { code: 'USER_EXIT', message: 'x' } })))
    presenter.sync()
    await flush()
    expect(seenSignal.aborted).toBe(true)
    expect(receipts).toEqual([])
  })

  it('网络结果未知时先核对状态，目标仍有效才重发同一事实', async () => {
    vi.useFakeTimers()
    const { api, presenter, receipts, store } = setup()
    let calls = 0
    api.presentOpening.mockImplementation((id, r) => {
      receipts.push(['opening', r])
      calls++
      if (calls === 1) return Promise.reject(Object.assign(new Error('net'), { network: true }))
      return Promise.resolve({ effect: 'APPLIED', state: baseState({ stateVersion: '1', currentRoundNo: 1 }) })
    })
    presenter.sync()
    await vi.runAllTimersAsync()
    vi.useRealTimers()
    expect(receipts).toEqual([
      ['opening', { status: 'COMPLETED' }],
      ['opening', { status: 'COMPLETED' }],
    ])
    expect(store.state.currentRoundNo).toBe(1)
  })
})
