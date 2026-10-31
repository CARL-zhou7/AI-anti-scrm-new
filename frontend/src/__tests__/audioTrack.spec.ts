import { expect, it, vi } from 'vitest'
import { createAudioTrack } from '../simulation/audioTrack'

it('reloads stalled audio at its saved position and clears handlers before disposal', async () => {
  const element = { currentTime: 1.25, muted: false, src: '', preload: '', onended: null, onerror: null,
    play: vi.fn().mockResolvedValue(undefined), pause: vi.fn(), removeAttribute: vi.fn(),
    load: vi.fn(function () { element.currentTime = 0 }) }
  vi.stubGlobal('Audio', function () { return element })
  try {
    const track = createAudioTrack('/local.mp3', true)
    track.onEnded(vi.fn())
    track.onError(vi.fn())
    await track.recover()
    expect(element.currentTime).toBe(1.25)
    expect(element.muted).toBe(true)
    expect(element.load).toHaveBeenCalledTimes(1)
    expect(element.play).toHaveBeenCalledTimes(1)
    track.destroy()
    expect(element.onended).toBeNull()
    expect(element.onerror).toBeNull()
  } finally { vi.unstubAllGlobals() }
})
