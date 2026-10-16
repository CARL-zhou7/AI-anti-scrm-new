import { describe, expect, it } from 'vitest'
import { applyEvent, applySnapshot, compareVersion, createSimulationStore, mergeReport } from '../src/core/stateStore.js'
import { analysis, baseState, event, message } from './fixtures.js'

describe('compareVersion', () => {
  it('按整数而不是字典序比较', () => {
    expect(compareVersion('10', '9')).toBe(1)
    expect(compareVersion('9', '10')).toBe(-1)
    expect(compareVersion('12345678901234567890', '12345678901234567889')).toBe(1)
    expect(compareVersion('7', '7')).toBe(0)
  })
})

describe('applyEvent', () => {
  function started() {
    const store = createSimulationStore()
    applySnapshot(store, baseState())
    return store
  }

  it('按 eventId 去重，重发不产生新台词', () => {
    const store = started()
    applyEvent(store, 'round.started', event(1, { roundNo: 1, phase: 'SCAMMER_GENERATING', startedAt: 'x', deadlineAt: baseState().deadlineAt }))
    const ev = event(2, message())
    expect(applyEvent(store, 'message.saved', ev)).toBe(true)
    expect(applyEvent(store, 'message.saved', ev)).toBe(false)
    // 不同 eventId 的同一条消息（重连快照）按消息 ID 合并
    applyEvent(store, 'message.saved', event(2, message()))
    expect(store.rounds[1].messages).toHaveLength(1)
    expect(store.rounds[1].phase).toBe('SCAMMER_PRESENTING')
    expect(store.state.stateVersion).toBe('2')
  })

  it('旧版本事件不回退阶段，但补入缺失内容', () => {
    const store = started()
    applySnapshot(
      store,
      baseState({
        stateVersion: '5',
        currentRoundNo: 1,
        currentRound: {
          roundNo: 1,
          phase: 'VICTIM_GENERATING',
          interactionComplete: false,
          presentationComplete: false,
          messages: [message({ presentationStatus: 'COMPLETED', audioUrl: null })],
          analysis: null,
          readingUntil: null,
        },
      }),
    )
    applyEvent(store, 'message.saved', event(3, message()))
    expect(store.rounds[1].phase).toBe('VICTIM_GENERATING')
    // COMPLETED 不能被旧的 PRESENTING 覆盖
    expect(store.rounds[1].messages[0].presentationStatus).toBe('COMPLETED')
  })

  it('消息按骗子、受害者顺序排列', () => {
    const store = started()
    applyEvent(store, 'message.saved', event(3, message({ id: '1002', speaker: 'VICTIM' })))
    applyEvent(store, 'message.saved', event(3, message({ id: '1001', speaker: 'SCAMMER', presentationStatus: 'COMPLETED' })))
    expect(store.rounds[1].messages.map((m) => m.speaker)).toEqual(['SCAMMER', 'VICTIM'])
  })

  it('analysis.saved 后进入分析呈现阶段并记录风险，round.reading 记录停留与下一动作', () => {
    const store = started()
    applyEvent(store, 'analysis.saved', event(6, { roundNo: 1, analysis: analysis() }))
    expect(store.rounds[1].phase).toBe('ANALYSIS_PRESENTING')
    expect(store.state.latestRiskScore).toBe(25)
    applyEvent(store, 'round.reading', event(7, { roundNo: 1, readingUntil: '2026-10-15T02:00:25.000Z', nextAction: 'NEXT_ROUND' }))
    expect(store.state.readingUntil).toBe('2026-10-15T02:00:25.000Z')
    expect(store.state.nextAction).toBe('NEXT_ROUND')
    expect(store.rounds[1].presentationComplete).toBe(true)
  })

  it('终态不会被较新的 RUNNING 快照复活', () => {
    const store = started()
    applyEvent(store, 'session.interrupted', event(5, baseState({ status: 'INTERRUPTED', stateVersion: '5', interruption: { code: 'MEDIA_FAILED', message: 'x' } })))
    applySnapshot(store, baseState({ status: 'RUNNING', stateVersion: '6' }))
    expect(store.state.status).toBe('INTERRUPTED')
  })

  it('session.state 合并状态与报告', () => {
    const store = createSimulationStore()
    const report = { status: 'PENDING', displayText: '生成中', canRetry: false, attemptNo: 0, updatedAt: '2026-10-15T02:00:20.000Z', error: null }
    applyEvent(store, 'session.state', event(3, { state: baseState({ stateVersion: '3', status: 'FINISHED', report }), reportView: { report } }))
    expect(store.state.status).toBe('FINISHED')
    expect(store.state.report.status).toBe('PENDING')
  })
})

describe('mergeReport', () => {
  const r = (status, attemptNo, updatedAt) => ({ status, attemptNo, updatedAt })
  it('SUCCEEDED 不回退，旧尝试不覆盖新尝试', () => {
    expect(mergeReport(r('SUCCEEDED', 1, '2026-01-01T00:00:01.000Z'), r('RUNNING', 1, '2026-01-01T00:00:02.000Z')).status).toBe('SUCCEEDED')
    expect(mergeReport(r('RUNNING', 2, '2026-01-01T00:00:01.000Z'), r('FAILED', 1, '2026-01-01T00:00:02.000Z')).attemptNo).toBe(2)
    expect(mergeReport(r('PENDING', 0, '2026-01-01T00:00:01.000Z'), r('RUNNING', 1, '2026-01-01T00:00:02.000Z')).status).toBe('RUNNING')
  })
})
