import type { TimingEvidence, TimingSample } from '../api/types'

export interface SampleSource { (): Promise<TimingSample> }

/**
 * Click-timing samples (API-42). Records performance.now() around the request, refreshes the sample 15 s before
 * it expires (in server time) so a late click still has a valid sample, and maps local monotonic time onto the
 * server clock for reading-pause and deadline countdowns.
 */
export class TimingSampler {
  private sample: TimingSample | null = null
  private send = 0
  private receive = 0
  private timer: ReturnType<typeof setTimeout> | null = null

  constructor(private readonly source: SampleSource, private readonly now: () => number = () => performance.now()) {}

  async refresh(): Promise<void> {
    const send = this.now()
    const sample = await this.source()
    this.send = send
    this.receive = this.now()
    this.sample = sample
    if (this.timer) clearTimeout(this.timer)
    const lifetime = Date.parse(sample.expiresAt) - Date.parse(sample.serverSampledAt)
    this.timer = setTimeout(() => { void this.refresh().catch(() => undefined) }, Math.max(1000, lifetime - 15_000))
  }

  get ready(): boolean { return this.sample !== null }

  /** Freezes the evidence for one click; reuse it unchanged when the same start request is resent. */
  evidence(clickMonotonicMs: number): TimingEvidence {
    if (!this.sample) throw new Error('timing sample not ready')
    return { methodVersion: this.sample.methodVersion, sampleId: this.sample.sampleId, clientSendMonotonicMs: this.send,
      clientReceiveMonotonicMs: this.receive, clientClickMonotonicMs: Math.max(clickMonotonicMs, this.receive) }
  }

  /** Current server time in epoch ms, estimated from the sample midpoint. */
  serverNow(): number {
    if (!this.sample) return Date.now()
    return Date.parse(this.sample.serverSampledAt) + (this.now() - (this.send + this.receive) / 2)
  }

  stop() { if (this.timer) clearTimeout(this.timer); this.timer = null }
}
