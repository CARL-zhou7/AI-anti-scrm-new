import { createRequire } from 'node:module'
import assert from 'node:assert/strict'
import { fileURLToPath } from 'node:url'

const require = createRequire(new URL('../frontend/package.json', import.meta.url))
const parser = require('@apidevtools/swagger-parser')
const document = await parser.validate(fileURLToPath(new URL('../openapi.yaml', import.meta.url)))
for (const [path, method] of [
  ['/auth/csrf', 'get'], ['/auth/register', 'post'], ['/auth/login', 'post'],
  ['/auth/logout', 'post'], ['/auth/me', 'get'], ['/home', 'get'], ['/news', 'get'],
  ['/scenarios', 'get'], ['/scammer-profiles', 'get'], ['/scammer-profiles/{id}', 'get'],
  ['/victim-profiles', 'get'], ['/victim-profiles/{id}', 'get'], ['/avatars/{key}', 'get'],
  ['/admin/scenarios', 'get'], ['/admin/scenarios/{id}', 'put'],
  ['/admin/scammer-profiles', 'get'], ['/admin/scammer-profiles', 'post'],
  ['/admin/scammer-profiles/{id}', 'get'], ['/admin/scammer-profiles/{id}', 'put'],
  ['/admin/scammer-profiles/{id}/disable', 'post'], ['/admin/scammer-profiles/{id}/enable', 'post'],
  ['/admin/victim-profiles', 'get'], ['/admin/victim-profiles', 'post'],
  ['/admin/victim-profiles/{id}', 'get'], ['/admin/victim-profiles/{id}', 'put'],
  ['/admin/victim-profiles/{id}/disable', 'post'], ['/admin/victim-profiles/{id}/enable', 'post'],
  ['/admin/avatars', 'post'], ['/admin/avatars/{key}', 'get'],
  ['/timing/samples', 'get'], ['/simulations', 'post'], ['/simulations/{id}', 'get'], ['/simulations/{id}/events', 'get'],
  ['/simulations/{id}/opening/presented', 'post'], ['/simulations/{id}/messages/{messageId}/presentation', 'post'],
  ['/simulations/{id}/rounds/{roundNo}/analysis/presented', 'post'], ['/simulations/{id}/exit', 'post'],
  ['/simulations/{id}/messages/{messageId}/audio', 'get'], ['/simulations/{id}/pause', 'post'], ['/simulations/{id}/resume', 'post'],
  ['/simulations/{id}/report', 'get'], ['/simulations/{id}/report/retry', 'post'],
  ['/simulations', 'get'], ['/simulations/{id}/detail', 'get'],
]) assert.ok(document.paths[path]?.[method], `${method} ${path} is missing`)
assert.equal(document.servers[0].url, '/anti.scam/v1')
assert.equal(document.components.schemas.Scenario.additionalProperties, false)
assert.equal(document.components.schemas.AvatarKey.pattern, '^[A-Za-z0-9_-]+$')
assert.equal(document.components.schemas.AdminScenario.additionalProperties, false)
for (const field of ['enabled', 'version', 'createdAt', 'updatedAt']) {
  assert.ok(document.components.schemas.AdminScammerProfile.required.includes(field))
}
// V1.1 (phase 4): pause/resume, audio segments, action text, speaking avatar; permanent profile deletion removed (D-1).
const s = document.components.schemas
for (const f of ['paused', 'pausedAt', 'pausedCreditMs', 'nextAction']) assert.ok(s.SimulationState.required.includes(f), `SimulationState.${f}`)
assert.equal(s.SimulationState.properties.pausedCreditMs.maximum, 300000)
for (const f of ['actionText', 'audioDurationMs', 'segments']) assert.ok(s.LiveMessage.required.includes(f), `LiveMessage.${f}`)
assert.ok(s.HistoryMessage.required.includes('actionText'))
for (const f of ['startMs', 'endMs', 'charStart', 'charEnd']) assert.ok(s.AudioSegment.required.includes(f))
for (const n of ['ScammerProfile', 'VictimProfile', 'AdminScammerProfile', 'AdminVictimProfile']) assert.ok(s[n].required.includes('speakingAvatar'), n)
for (const n of ['ScammerWrite', 'VictimWrite', 'ScammerUpdate', 'VictimUpdate']) assert.ok(s[n].required.includes('speakingAvatarKey'), n)
assert.ok(!('internalNote' in s.LiveMessage.properties) && !('internalNote' in s.HistoryMessage.properties))
const events = document.paths['/simulations/{id}/events'].get['x-event-schemas']
assert.equal(Object.keys(events ?? {}).length, 11)
for (const p of ['/admin/scammer-profiles/{id}', '/admin/victim-profiles/{id}']) assert.ok(!document.paths[p]?.delete, `DELETE ${p} must be removed (D-1)`)
const operations = Object.values(document.paths).reduce((n, item) => n + ['get', 'post', 'put', 'delete'].filter(m => item[m]).length, 0)
assert.equal(operations, 44)
assert.deepEqual(new Set(s.ReportContent.required), new Set(['summary', 'roundAnalyses', 'speechAnalysis', 'victimBehaviorAnalysis', 'outcomeAnalysis']))
assert.deepEqual(new Set(s.ReportError.properties.code.enum), new Set(['GENERATION_FAILED', 'VALIDATION_FAILED', 'PERSISTENCE_FAILED', 'TIMEOUT', 'SERVICE_RESTARTED']))
assert.ok(s.ReportView.properties.content.anyOf.some(type => type.type === 'null'))
assert.deepEqual(new Set(s.HistoryDetail.required), new Set(['sessionId', 'status', 'startedAt', 'deadlineAt', 'endedAt', 'configuration', 'rounds', 'outcome', 'interruption', 'report']))
assert.ok(!('audioUrl' in s.HistoryMessage.properties))
assert.ok(!('total' in s.HistoryPage.properties))
console.log('OpenAPI valid; 44 operations (V1.1) and 11 SSE event types present; phase 5 (2) operations present; phase 6 (2) operations present; profile DELETE removed.')
