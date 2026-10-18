import type { AudioSegment } from '../api/types'

/**
 * How many code points of `content` are visible at `currentMs` of the line's audio. Earlier segments are fully
 * shown; inside the current segment text advances in proportion to its own time span. Counting by code point
 * (Array.from) never splits a surrogate pair. `ended` forces the whole line.
 */
export function visibleCodePoints(currentMs: number, segments: AudioSegment[] | null, content: string, ended = false): number {
  const total = Array.from(content).length
  if (ended || !segments?.length) return ended ? total : 0
  let visible = 0
  for (const s of segments) {
    if (currentMs >= s.endMs) { visible = s.charEnd; continue }
    if (currentMs > s.startMs) {
      const ratio = (currentMs - s.startMs) / (s.endMs - s.startMs)
      visible = s.charStart + Math.floor((s.charEnd - s.charStart) * ratio)
    }
    break
  }
  return Math.min(visible, total)
}

export function sliceCodePoints(content: string, count: number): string {
  return Array.from(content).slice(0, count).join('')
}
