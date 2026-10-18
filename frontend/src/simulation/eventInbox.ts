import type { SimulationEventEnvelope } from '../api/types'

/** State versions are decimal strings: compare as integers, never lexicographically. */
export function compareVersion(a: string, b: string): number {
  const x = BigInt(a), y = BigInt(b)
  return x === y ? 0 : x > y ? 1 : -1
}

const SESSION_SNAPSHOTS = new Set(['session.state', 'session.started', 'session.paused', 'session.resumed',
  'session.finished', 'session.interrupted'])

/**
 * De-duplicates SSE events by eventId and drops session-level snapshots older than the newest applied one, so a
 * late frame can never roll local state back. Message/analysis events are object-keyed and always pass through;
 * the director merges them by message id / round number.
 */
export class EventInbox {
  private seen = new Set<string>()
  private latest = '0'

  accept(event: SimulationEventEnvelope): boolean {
    if (this.seen.has(event.eventId)) return false
    this.seen.add(event.eventId)
    if (SESSION_SNAPSHOTS.has(event.type)) {
      if (compareVersion(event.stateVersion, this.latest) < 0) return false
      this.latest = event.stateVersion
    }
    return true
  }

  /** Records a version learned from an HTTP response (receipt, pause, resume). */
  observe(version: string) { if (compareVersion(version, this.latest) > 0) this.latest = version }
}
