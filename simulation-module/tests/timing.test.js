import { afterEach, describe, expect, it } from 'vitest'
import { createTimingSampler, TimingSampleUnavailableError } from '../src/core/timing.js'
import { __resetClock } from '../src/core/clock.js'

function fakeApi(lifetimeMs = 60000) {
  return {
    getTimingSample: async () => {
      const now = Date.now()
      return {
        sampleId: 'sample_1',
        serverSampledAt: new Date(now).toISOString(),
        expiresAt: new Date(now + lifetimeMs).toISOString(),
        methodVersion: '1.0',
      }
    },
  }
}

afterEach(() => __resetClock())

describe('createTimingSampler', () => {
  it('生成满足 发送 ≤ 接收 ≤ 点击 的计时证据', async () => {
    let t = 1000
    const sampler = createTimingSampler(fakeApi(), { now: () => (t += 20) })
    await sampler.refresh()
    const evidence = sampler.buildEvidence(5000)
    sampler.stop()
    expect(evidence).toEqual({
      methodVersion: '1.0',
      sampleId: 'sample_1',
      clientSendMonotonicMs: 1020,
      clientReceiveMonotonicMs: 1040,
      clientClickMonotonicMs: 5000,
    })
  })

  it('点击早于接收时拒绝构造（不能点击后再补采样）', async () => {
    let t = 1000
    const sampler = createTimingSampler(fakeApi(), { now: () => (t += 20) })
    await sampler.refresh()
    sampler.stop()
    expect(() => sampler.buildEvidence(1030)).toThrow(TimingSampleUnavailableError)
  })

  it('样本即将过期时拒绝构造', async () => {
    const sampler = createTimingSampler(fakeApi(3000), { safetyMarginMs: 5000 })
    await sampler.refresh()
    sampler.stop()
    expect(sampler.ready).toBe(false)
    expect(() => sampler.buildEvidence(performance.now())).toThrow(TimingSampleUnavailableError)
  })
})
