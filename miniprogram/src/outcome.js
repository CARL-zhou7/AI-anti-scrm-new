export function outcomeVerdict(outcome, content) {
  const winning = outcome?.winning || content?.winning
  if (winning === 'VICTIM') return { tone: 'victim', title: '受害者胜' }
  if (winning === 'SCAMMER') return { tone: 'scammer', title: '骗子胜' }
  if (outcome?.displayText) return { tone: 'legacy', title: outcome.displayText }
  return { tone: 'unknown', title: '未形成胜负结论' }
}

export function outcomeReason(outcome, ending, interruption) {
  return outcome?.reason || ending?.reason || ending?.displayText || interruption?.message || ''
}
