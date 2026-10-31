/** Reuse complete saved induction fields; never truncate or write a display string back as an execution rule. */
export function ruleOverview(text: string): { condition: string; guidance: string } | null {
  const match = /^适用情况：[ \t]*([^\n]+)\r?\n话术指导：[ \t]*([^\n]+)$/.exec(text.trim())
  if (!match) return null
  const condition = match[1]!.trim(), guidance = match[2]!.trim()
  // shortcut: long or unstructured legacy rules require the full text; add saved summaries if these become common.
  if (!condition || !guidance || condition.length > 80 || guidance.length > 80) return null
  return { condition, guidance }
}
