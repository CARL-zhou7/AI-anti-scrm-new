import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { visibleCodePoints } from '../simulation/segmentReveal'
import { EventInbox, compareVersion } from '../simulation/eventInbox'
import { CARDS_MS, PerformanceDirector, type DirectorDeps } from '../simulation/performanceDirector'
import type { AudioLike } from '../simulation/audioTrack'
import type { EffectResult, LiveMessage, SimulationEventEnvelope, SimulationState } from '../api/types'

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
})

class FakeAudio implements AudioLike {
  currentMs = 0; ended = false; muted = false; plays = 0; paused = false
  private endCb: () => void = () => {}
  play() { this.plays++; this.paused = false; return Promise.resolve() }
  pause() { this.paused = true }
  onEnded(cb: () => void) { this.endCb = cb }
  onError() {}
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
