import type { RiskScore } from '../api/types'

const colors: Record<number, string> = { 0: '#3f8f6f', 25: '#3978b8', 50: '#d9a23a', 75: '#d9733a', 100: '#c43d33' }
const band = (score: number) => Math.max(0, Math.min(100, Math.round(score / 25) * 25))
export const riskColor = (score: number | null | undefined) => score == null ? '#8b929c' : colors[band(score)]
export const riskClass = (score: RiskScore | number | null | undefined) => `risk-level-${score == null ? 'unscored' : band(score)}`
