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
  ['/admin/trust-policy', 'get'], ['/admin/trust-policy', 'put'],
  ['/admin/interventions', 'get'], ['/admin/interventions/induce', 'post'],
  ['/admin/scene-rules', 'get'], ['/admin/scene-rules', 'post'],
  ['/admin/scene-rules/{ruleId}', 'put'], ['/admin/scene-rules/{ruleId}/sources', 'get'],
]) assert.ok(document.paths[path]?.[method], `${method} ${path} is missing`)
assert.equal(document.servers[0].url, '/anti.scam/v1')
assert.equal(document.components.schemas.Scenario.additionalProperties, false)
assert.equal(document.components.schemas.AvatarKey.pattern, '^[A-Za-z0-9_-]+$')
assert.equal(document.components.schemas.AdminScenario.additionalProperties, false)
for (const field of ['enabled', 'version', 'createdAt', 'updatedAt']) {
  assert.ok(document.components.schemas.AdminScammerProfile.required.includes(field))
}
// V1.2: profile facts/scenario profiles, funds, three outcomes, report v1/v2, Qwen audio.
const s = document.components.schemas
assert.equal(document.info.version, '1.4.0')
for (const f of ['paused', 'pausedAt', 'pausedCreditMs', 'nextAction', 'funds']) assert.ok(s.SimulationState.required.includes(f), `SimulationState.${f}`)
assert.equal(s.SimulationState.properties.pausedCreditMs.maximum, 300000)
for (const f of ['actionText', 'audioDurationMs', 'segments']) assert.ok(s.LiveMessage.required.includes(f), `LiveMessage.${f}`)
assert.ok(s.HistoryMessage.required.includes('actionText'))
for (const f of ['startMs', 'endMs', 'charStart', 'charEnd']) assert.ok(s.AudioSegment.required.includes(f))
for (const n of ['ScammerProfile', 'VictimProfile', 'AdminScammerProfile', 'AdminVictimProfile']) assert.ok(s[n].required.includes('speakingAvatar'), n)
for (const n of ['ScammerWrite', 'VictimWrite', 'ScammerUpdate', 'VictimUpdate']) assert.ok(s[n].required.includes('speakingAvatarKey'), n)
assert.ok(!('internalNote' in s.LiveMessage.properties) && !('internalNote' in s.HistoryMessage.properties))
assert.deepEqual(new Set(s.OutcomeCode.enum), new Set(['SCAMMER_WIN', 'VICTIM_WIN', 'DEFRAUDED', 'IDENTIFIED', 'LIMIT_REACHED', 'GOAL_ACHIEVED', 'LOSS_STOPPED', 'NOT_DEFRAUDED', 'UNDETERMINED']))
assert.deepEqual(new Set(s.InterruptionCode.enum), new Set(['USER_EXIT', 'DIALOGUE_FAILED', 'ANALYSIS_FAILED', 'TTS_FAILED', 'MEDIA_FAILED', 'PERSISTENCE_FAILED', 'TIME_LIMIT', 'FUNDS_EXHAUSTED', 'CHARACTER_EXIT', 'CONNECTION_LOST', 'SERVICE_RESTARTED']))
const events = document.paths['/simulations/{id}/events'].get['x-event-schemas']
assert.equal(Object.keys(events ?? {}).length, 12)
for (const p of ['/admin/scammer-profiles/{id}', '/admin/victim-profiles/{id}']) assert.ok(!document.paths[p]?.delete, `DELETE ${p} must be removed (D-1)`)
const operations = Object.values(document.paths).reduce((n, item) => n + ['get', 'post', 'put', 'delete'].filter(m => item[m]).length, 0)
assert.equal(operations, 56)
for (const f of ['goalAchieved', 'stoppedAfterFraud', 'actualLossYuan']) assert.ok(f in s.Outcome.properties, `Outcome.${f}`)
for (const f of ['scenarioCode', 'needs', 'relevantExperience', 'judgmentBasis', 'verificationHabits', 'actionBoundaries', 'stopConditions', 'recentEvents', 'consultationsTransactions', 'sharedExperiences', 'contactBackground', 'initialTrustMode']) assert.ok(s.VictimScenarioProfile.required.includes(f), `VictimScenarioProfile.${f}`)
for (const f of ['fundsRecorded', 'initialAvailableYuan', 'availableBalanceYuan', 'totalPaidYuan', 'totalRefundedYuan', 'actualLossYuan', 'additionalPaymentCount', 'reserveReleasedYuan', 'flows']) assert.ok(s.Funds.required.includes(f), `Funds.${f}`)
for (const f of ['sequenceNo', 'roundNo', 'messageId', 'type', 'purpose', 'amountYuan', 'balanceAfterYuan', 'reason', 'occurredAt']) assert.ok(s.FundFlow.required.includes(f), `FundFlow.${f}`)
assert.ok(s.HistoryRound.required.includes('funds'))
assert.ok(s.HistoryDetail.required.includes('rulesetVersion') && s.HistoryDetail.required.includes('funds'))
assert.ok(s.ReportView.properties.contentSchemaVersion)
assert.deepEqual(new Set(s.ReportContentV1.required), new Set(['contentSchemaVersion', 'summary', 'roundAnalyses', 'speechAnalysis', 'victimBehaviorAnalysis', 'outcomeAnalysis']))
assert.deepEqual(new Set(s.ReportContentV2.required), new Set(['contentSchemaVersion', 'result', 'summary', 'keyNodes', 'preventionAdvice', 'fundsSummary']))
assert.deepEqual(new Set(s.ReportError.properties.code.enum), new Set(['GENERATION_FAILED', 'VALIDATION_FAILED', 'PERSISTENCE_FAILED', 'TIMEOUT', 'SERVICE_RESTARTED']))
assert.ok(s.ReportView.properties.content.anyOf.some(type => type.type === 'null'))
assert.ok(!('audioUrl' in s.HistoryMessage.properties))
assert.ok(!('total' in s.HistoryPage.properties))
for (const field of ['initialTrustScore', 'positiveFactor', 'negativeFactor', 'smallAmountCapYuan']) {
  assert.ok(s.VictimScenarioProfile.required.includes(field))
  assert.ok(!(field in s.PublicVictimScenarioProfile.properties), `Public profile leaks ${field}`)
}
assert.ok(!('reservedFundsYuan' in s.VictimWrite.properties))
assert.ok(!('reservedFundsYuan' in s.VictimUpdate.properties))
for (const field of ['generalReserveFundsYuan', 'emergencyReserveFundsYuan']) {
  for (const name of ['VictimWrite', 'VictimUpdate']) assert.ok(s[name].required.includes(field))
}
for (const field of ['totalRewardedYuan', 'generalReserveTotalYuan', 'generalReserveRemainingYuan', 'emergencyReserveTotalYuan', 'emergencyReserveRemainingYuan']) assert.ok(s.Funds.required.includes(field))
assert.equal(s.ReportContentV3.properties.contentSchemaVersion.const, 3)
// V1.4 (PRD-V3.5): optional timing, 15-round verdict, report schema 4, real narration shorts.
assert.ok(!s.StartRequest.required.includes('timing'), 'StartRequest.timing is optional in V1.4')
assert.ok(s.SimulationState.required.includes('deadlineAt'), 'SimulationState keeps deadlineAt (nullable)')
for (const field of ['winner', 'finalTrustScore']) assert.ok(field in s.SimulationState.properties, `SimulationState.${field}`)
for (const field of ['winning', 'finalTrustScore']) assert.ok(field in s.Outcome.properties, `Outcome.${field}`)
assert.equal(s.SimulationState.properties.deadlineAt.anyOf.at(-1).type, 'null')
assert.ok(s.Analysis.properties.methodShort && s.Analysis.properties.intentShort)
assert.equal(s.ReportContentV4.properties.contentSchemaVersion.const, 4)
for (const f of ['interventionMode', 'phase', 'currentDraft', 'canAdopt']) assert.ok(f in s.InterventionState.properties, 'InterventionState.' + f)
assert.ok(s.AdoptRequest.required.includes('continue'), 'the wire field stays continue')
assert.ok('intervention' in s.SimulationState.properties, 'SimulationState.intervention')
assert.ok(document.paths['/simulations/{id}/intervention'].get, 'API-47 exists')
assert.ok(s.InterventionDraftEvent.required.includes('data'), 'intervention.draft payload')
for (const field of ['winning', 'finalTrustScore']) assert.ok(s.ReportContentV4.required.includes(field), `ReportContentV4.${field}`)
assert.equal(s.ReportContent.discriminator.mapping['4'], '#/components/schemas/ReportContentV4')
assert.equal(s.ReportFundsSummaryV3.required.length, 7)
assert.ok(s.ReportView.required.includes('ending'))
assert.ok(s.ReportView.properties.unavailableReason.anyOf[0].enum.includes('LEGACY_NO_REPORT'))
assert.deepEqual(document.paths['/admin/trust-policy'].put['x-access-roles'], ['ADMIN'])
assert.equal(document.paths['/admin/trust-policy'].put['x-csrf-required'], true)
// V1.4 knowledge base (Phase 3): waiting records, induction, rules, their sources and the frozen snapshot count.
for (const [path, method] of [['/admin/interventions', 'get'], ['/admin/interventions/induce', 'post'],
  ['/admin/scene-rules', 'get'], ['/admin/scene-rules', 'post'], ['/admin/scene-rules/{ruleId}', 'put'],
  ['/admin/scene-rules/{ruleId}/sources', 'get']]) {
  assert.deepEqual(document.paths[path][method]['x-access-roles'], ['ADMIN'], `${method} ${path} is ADMIN only`)
}
for (const [path, method] of [['/admin/interventions/induce', 'post'], ['/admin/scene-rules', 'post'],
  ['/admin/scene-rules/{ruleId}', 'put']]) {
  assert.equal(document.paths[path][method]['x-csrf-required'], true, `${method} ${path} needs CSRF`)
}
const waiting = document.paths['/admin/interventions'].get.parameters.map(p => p.name)
assert.deepEqual(waiting, ['ruleId', 'sceneCode'], 'API-50 filters by ruleId or sceneCode')
assert.deepEqual(new Set(s.InterventionRecord.properties.status.enum), new Set(['PENDING', 'ADOPTED', 'LINKED']))
assert.ok(s.InterventionRecord.required.includes('ruleId'), 'InterventionRecord.ruleId')
assert.ok(s.InterventionRecord.required.includes('finalContent'))
assert.ok(s.SimulationState.required.includes('ruleSnapshotCount'), 'SimulationState.ruleSnapshotCount')
for (const field of ['scenarioCode', 'title', 'ruleText', 'sourceInterventionIds']) {
  assert.ok(s.SceneRuleWrite.required.includes(field), `SceneRuleWrite.${field}`)
  assert.ok(s.RuleDraft.required.includes(field), `RuleDraft.${field}`)
}
assert.deepEqual(new Set(s.SceneRule.properties.status.enum), new Set(['ENABLED', 'DISABLED']))
for (const field of ['ruleId', 'version', 'sourceCount']) assert.ok(s.SceneRule.required.includes(field), `SceneRule.${field}`)
assert.ok(s.SceneRulePage.required.includes('items'))
console.log('OpenAPI valid; 56 operations (V1.4), 12 SSE event types, report v1/v2/v3/v4, 15-round verdict, narration shorts, admin intervention gate, knowledge base rules and snapshots, trust policy and public profile isolation checked.')
