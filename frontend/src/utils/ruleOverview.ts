/** Reuse complete saved induction fields; never truncate or write a display string back as an execution rule. */
export function ruleOverview(text: string): { condition: string; guidance: string } | null {
  const summary = /^何时适用：[ \t]*([^\n]+)\r?\n规则作用：[ \t]*([^\n]+)(?:\r?\n|$)/.exec(text.trim())
  if (summary) {
    const condition = summary[1]!.trim(), guidance = summary[2]!.trim()
    if (condition && guidance && condition.length <= 80 && guidance.length <= 80) return { condition, guidance }
  }
  const match = /^适用情况：[ \t]*([^\n]+)\r?\n(?:话术|行为)指导：[ \t]*([^\n]+)$/.exec(text.trim())
  if (!match) return null
  const condition = match[1]!.trim(), guidance = match[2]!.trim()
  // shortcut: long or unstructured legacy rules require the full text; add saved summaries if these become common.
  if (!condition || !guidance || condition.length > 80 || guidance.length > 80) return null
  return { condition, guidance }
}
