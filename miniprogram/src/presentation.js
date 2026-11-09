// Start IDs are idempotency keys, with no authentication role.
export function startId() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, char => {
    const digit = Math.floor(Math.random() * 16)
    return (char === 'x' ? digit : 8 + (digit % 4)).toString(16)
  })
}

export function reveal(content, segments, currentMs) {
  if (!segments?.length) return content
  let end = 0
  for (const segment of segments) {
    if (currentMs >= segment.endMs) { end = segment.charEnd; continue }
    if (currentMs > segment.startMs) {
      const fraction = (currentMs - segment.startMs) / (segment.endMs - segment.startMs)
      end = segment.charStart + Math.floor((segment.charEnd - segment.charStart) * fraction)
    }
    break
  }
  return Array.from(content).slice(0, end).join('')
}

export function nextPresentation(round) {
  if (!round) return null
  const message = round.messages.find(item => item.presentationStatus === 'PRESENTING')
  if (message) return { kind: 'message', message, path: '/messages/' + encodeURIComponent(message.id) + '/presentation' }
  if (round.phase === 'ANALYSIS_PRESENTING' && round.analysis && !round.analysis.presentedAt) {
    return { kind: 'analysis', path: '/rounds/' + round.roundNo + '/analysis/presented' }
  }
  return null
}

export function stageAction(messages, speaker) {
  const actions = messages.filter(line => line.presentationStatus !== 'PENDING' && line.actionText?.trim())
  if (actions.length === 1) return actions[0]
  return actions.find(line => line.speaker === speaker)
}

export function panelTextSize(text, compact = false) {
  const length = Array.from(text).length
  return (length <= 40 ? 40 : length <= 85 ? 36 : length <= 145 ? 32 : 28) - (compact ? 2 : 0)
}

export function playedRounds(rounds) {
  return rounds.filter(round => round.analysis || round.messages.some(line => line.presentationStatus !== 'PENDING'))
}

export const trendY = score => 28 + (100 - score) * 1.5

export function trendPlot(series, viewportWidth) {
  const scored = series.filter(({ score }) => Number.isFinite(score) && score >= 0 && score <= 100)
  const width = Math.max(viewportWidth, scored.length * 54)
  const points = scored.map((point, index) => ({
    ...point,
    x: scored.length === 1 ? width / 2 : 27 + index * (width - 54) / (scored.length - 1),
    y: trendY(point.score)
  }))
  const segments = points.slice(1).map((end, index) => {
    const { x, y } = points[index]
    const dx = end.x - x, dy = end.y - y
    return { x, y, length: Math.hypot(dx, dy), angle: Math.atan2(dy, dx) }
  })
  return { width, points, segments }
}
