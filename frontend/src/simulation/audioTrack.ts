/** What the director needs from a playing line; implemented by HTMLAudioElement below and by fakes in tests. */
export interface AudioLike {
  play(): Promise<void>
  recover(): Promise<void>
  pause(): void
  readonly currentMs: number
  readonly ended: boolean
  muted: boolean
  onEnded(cb: () => void): void
  onError(cb: () => void): void
  destroy(): void
}

/**
 * One HTMLAudioElement per message. Muting keeps the same timeline (the element still plays and still fires
 * `ended`), so text reveal and receipts behave identically with sound off.
 */
export function createAudioTrack(url: string, muted: boolean): AudioLike {
  const el = new Audio()
  el.preload = 'auto'
  el.muted = muted
  el.src = url
  return {
    play: () => el.play(),
    recover: async () => {
      const position = el.currentTime
      el.load()
      el.currentTime = position
      return el.play()
    },
    pause: () => el.pause(),
    get currentMs() { return el.currentTime * 1000 },
    get ended() { return el.ended },
    get muted() { return el.muted },
    set muted(value: boolean) { el.muted = value },
    onEnded: cb => { el.onended = cb },
    onError: cb => { el.onerror = cb },
    destroy: () => { el.onended = null; el.onerror = null; el.pause(); el.removeAttribute('src'); el.load() },
  }
}
