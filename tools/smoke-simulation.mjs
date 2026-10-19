// Phase 4 smoke check: isolated MySQL 8.0.45 + packaged backend (dev profile, scripted model, silent speech).
// Usage (from the repository root, after `backend\mvnw.cmd -DskipTests package`):
//   node tools/smoke-simulation.mjs [--mysql "C:/Program Files/MySQL/MySQL Server 8.0/bin"] [--java "D:/Java/jdk-21/.../bin/java.exe"]
// Uses ports 13310/18083 and creates .tools/simulation-smoke-<random>; never touches an existing database.
import { spawn, spawnSync } from 'node:child_process'
import { randomUUID } from 'node:crypto'
import { cpSync, mkdirSync, readdirSync, createWriteStream, readFileSync, existsSync } from 'node:fs'
import { join, resolve } from 'node:path'
import assert from 'node:assert/strict'

const arg = (name, fallback) => { const i = process.argv.indexOf(name); return i > 0 ? process.argv[i + 1] : fallback }
const MYSQL_BIN = arg('--mysql', 'C:/Program Files/MySQL/MySQL Server 8.0/bin')
const JAVA = arg('--java', process.env.JAVA_HOME ? join(process.env.JAVA_HOME, 'bin', 'java') : 'java')
const ROOT = resolve(import.meta.dirname, '..')
const REPORT_ONLY = process.argv.includes('--report')
const RUN = join(ROOT, '.tools', (REPORT_ONLY ? 'report' : 'simulation') + '-smoke-' + randomUUID().slice(0, 8))
const DB_PORT = REPORT_ONLY ? 13311 : 13310, APP_PORT = REPORT_ONLY ? 18084 : 18083, BASE = `http://127.0.0.1:${APP_PORT}/anti.scam/v1`
const PASSWORD = randomUUID().replaceAll('-', '')
const sleep = ms => new Promise(r => setTimeout(r, ms))
const children = []
const results = []

async function main() {
  assert.ok(existsSync(join(MYSQL_BIN, 'mysqld.exe')), 'MySQL 8.0.45 is required; use --mysql to specify its bin directory')
  mkdirSync(join(RUN, 'assets', 'avatars'), { recursive: true })
  const init = spawnSync(join(MYSQL_BIN, 'mysqld'), ['--no-defaults', '--initialize-insecure', `--datadir=${join(RUN, 'data')}`])
  assert.equal(init.status, 0, 'MySQL initialization failed')
  children.push(launch(join(MYSQL_BIN, 'mysqld'), ['--no-defaults', `--datadir=${join(RUN, 'data')}`, `--port=${DB_PORT}`,
    '--bind-address=127.0.0.1', '--mysqlx=0', '--character-set-server=utf8mb4', '--collation-server=utf8mb4_0900_bin', '--console'], 'mysql'))
  for (let i = 0; ; i++) {
    if (spawnSync(join(MYSQL_BIN, 'mysqladmin'), ['--no-defaults', '-h127.0.0.1', `-P${DB_PORT}`, '-uroot', 'ping']).status === 0) break
    if (i > 60) throw new Error('Isolated MySQL did not start'); await sleep(500)
  }
  sql(`ALTER USER 'root'@'localhost' IDENTIFIED BY '${PASSWORD}'; CREATE DATABASE sim_smoke CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;`, false)
  for (const f of readdirSync(join(ROOT, 'backend', 'var', 'avatars'))) cpSync(join(ROOT, 'backend', 'var', 'avatars', f), join(RUN, 'assets', 'avatars', f))
  const env = { ...process.env, DB_URL: `jdbc:mysql://127.0.0.1:${DB_PORT}/sim_smoke?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true`,
    DB_USERNAME: 'root', DB_PASSWORD: PASSWORD, ADMIN_USERNAME: 'SimSmokeAdmin', ADMIN_PASSWORD: 'Admin' + PASSWORD.slice(0, 12),
    AVATAR_STORE: join(RUN, 'assets'), AUDIO_TEMP_DIR: join(RUN, 'tmp-audio'), LLM_PROVIDER: 'scripted', TTS_PROVIDER: 'silent' }
  children.push(launch(JAVA, ['-jar', join(ROOT, 'backend', 'target', 'anti-scam-backend-0.1.0.jar'), '--spring.profiles.active=dev',
    `--server.port=${APP_PORT}`], 'app', { cwd: join(ROOT, 'backend'), env }))
  // Wait for "Started": runners (admin account, restart recovery) finish after the port opens.
  for (let i = 0; ; i++) {
    const log = join(RUN, 'app.log')
    if (existsSync(log) && readFileSync(log, 'utf8').includes('Started AntiScamApplication')) break
    if (i > 180) throw new Error('Backend did not start; see ' + RUN); await sleep(500)
  }

  let admin
  for (let i = 0; !admin; i++) { // the admin account is created by a runner that executes after "Started"
    try { admin = await Client.login(env.ADMIN_USERNAME, env.ADMIN_PASSWORD) } catch (e) { if (i > 20) throw e; await sleep(500) }
  }
  const seed = (await admin.call('GET', '/admin/scammer-profiles')).data.items[0]
  const scripted = {}
  for (const script of REPORT_ONLY ? [] : ['precheck-regen-r3', 'tts-fail-r2']) {
    const body = { name: '剧本:' + script, age: 30, gender: '男', identity: '诈骗团伙话务人员', description: '冒烟用', contextTime: '晚上',
      contextPlace: '房间', currentState: '准备联系', fraudExperience: 'HIGH', speechSkill: 'HIGH', manipulationSkill: 'HIGH',
      behaviorTraits: '冒烟用', avatarKey: seed.avatar.key, speakingAvatarKey: seed.speakingAvatar.key }
    scripted[script] = (await admin.call('POST', '/admin/scammer-profiles', body, 201)).data.id
  }
  if (!REPORT_ONLY) await admin.call('DELETE', '/admin/scammer-profiles/' + scripted['tts-fail-r2'], { version: 0 }, 400) // D-1: no permanent delete

  const users = await Promise.all((REPORT_ONLY ? ['ReportA', 'ReportB'] : ['SimA', 'SimB', 'SimC', 'SimD', 'SimE']).map(u => Client.register(u)))
  if (REPORT_ONLY) {
    await check('Report generation, manual retry, idempotency and permissions', () => reportFlow(users[0], users[1], admin))
    return
  }
  await Promise.all([
    check('S01 five rounds then NOT_DEFRAUDED, saved report, isolation', () => s01(users[0])),
    check('S04 round-3 precheck regeneration never published', () => s04(users[1], scripted['precheck-regen-r3'])),
    check('S07 victim TTS failure -> TTS_FAILED, text kept PENDING', () => s07(users[2], scripted['tts-fail-r2'])),
    check('S13 pause/resume shifts deadline, nothing published while paused', () => s13(users[3])),
    check('S09/S10/S12 exit, idempotent start, active conflict, permissions', () => s09(users[4], users[0])),
  ])
  await check('History list, detail, tied pagination, immutability and permissions', () => historyFlow(users, admin))
}

// ---------------------------------------------------------------- scenarios

async function historyFlow(users, admin) {
  const user = users[1], created = []
  for (let i = 0; i < 3; i++) {
    const { state } = await user.startOnly({ scammer: '1', victim: '1' })
    created.push(state.sessionId)
    await user.call('POST', `/simulations/${state.sessionId}/exit`, { reason: 'NAVIGATION' })
  }
  // Force a tie only in this disposable database; every saved record must still appear once.
  const tiedAt = sql(`SELECT started_at FROM simulation_session WHERE id=${created[0]}`)
  sql(`UPDATE simulation_session SET started_at='${tiedAt}', deadline_at=TIMESTAMPADD(MICROSECOND,(600000+paused_total_ms)*1000,'${tiedAt}') WHERE id IN (${created.join(',')})`)
  const expected = sql(`SELECT id FROM simulation_session WHERE user_id=(SELECT user_id FROM simulation_session WHERE id=${created[0]}) ORDER BY started_at DESC,id DESC`).split(/\r?\n/)
  const ids = []
  let cursor, firstCursor
  do {
    const page = (await user.call('GET', '/simulations?limit=1' + (cursor ? '&cursor=' + cursor : ''))).data
    assert.equal(page.items.length, 1)
    ids.push(page.items[0].sessionId)
    assert.equal(page.hasMore, page.nextCursor !== null)
    firstCursor ??= page.nextCursor
    cursor = page.nextCursor
  } while (cursor)
  assert.deepEqual(ids, expected)
  assert.equal(new Set(ids).size, ids.length)
  const list = (await user.call('GET', '/simulations')).data
  for (const id of created) {
    const item = list.items.find(item => item.sessionId === id)
    assert.equal(item.status, 'INTERRUPTED'); assert.equal(item.report, null); assert.ok(item.interruption.code)
  }
  await users[0].call('GET', '/simulations?cursor=' + firstCursor, undefined, 400)
  const finished = (await users[0].call('GET', '/simulations')).data.items.find(item => item.status === 'FINISHED')
  assert.ok(finished)
  await waitForReport(users[0], finished.sessionId)
  assert.equal((await users[0].call('GET', '/simulations')).data.items.find(item => item.sessionId === finished.sessionId).report.status, 'SUCCEEDED')
  const path = `/simulations/${finished.sessionId}/detail`
  const detail = (await users[0].call('GET', path)).data
  assert.equal(detail.outcome.code, sql(`SELECT outcome_code FROM simulation_session WHERE id=${finished.sessionId}`))
  assert.equal(detail.rounds.length, Number(sql(`SELECT COUNT(*) FROM simulation_round WHERE session_id=${finished.sessionId}`)))
  assert.equal(detail.configuration.scenario.name, sql(`SELECT JSON_UNQUOTE(JSON_EXTRACT(scenario_snapshot,'$.name')) FROM simulation_session WHERE id=${finished.sessionId}`))
  const scenario = (await admin.call('GET', '/admin/scenarios')).data.find(s => s.id === detail.configuration.scenario.id)
  const edited = (await admin.call('PUT', `/admin/scenarios/${scenario.id}`, { name: '历史快照不应随本名称变化', description: scenario.description, version: scenario.version })).data
  try {
    const unchanged = (await users[0].call('GET', path)).data
    assert.deepEqual(unchanged, detail)
  } finally {
    await admin.call('PUT', `/admin/scenarios/${scenario.id}`, { name: scenario.name, description: scenario.description, version: edited.version })
  }
  const interrupted = (await users[2].call('GET', '/simulations')).data.items[0]
  const partial = (await users[2].call('GET', `/simulations/${interrupted.sessionId}/detail`)).data
  const last = partial.rounds.at(-1)
  // S07 saves both texts before victim TTS fails: interaction is saved, presentation remains incomplete.
  assert.equal(last.interactionComplete, true); assert.equal(last.presentationComplete, false); assert.equal(last.analysis, null)
  assert.equal(last.messages.find(m => m.speaker === 'VICTIM').presentationStatus, 'PENDING')
  assert.equal(partial.outcome, null); assert.equal(partial.report, null); assert.ok(partial.interruption)
  const foreign = await user.call('GET', path, undefined, 404)
  const missing = await user.call('GET', '/simulations/999999/detail', undefined, 404)
  foreign.requestId = missing.requestId; assert.deepEqual(foreign, missing)
  await user.call('GET', '/simulations?userId=1', undefined, 400)
  for (const endpoint of ['/simulations', path]) {
    await admin.call('GET', endpoint, undefined, 403)
    await new Client().call('GET', endpoint, undefined, 401)
  }
  const serialized = JSON.stringify({ list, detail, partial })
  for (const key of ['userId', 'internalNote', 'audioUrl', 'audioDurationMs', 'segments']) assert.ok(!serialized.includes(`"${key}":`), key + ' leaked')
}

async function s01(user) {
  const run = await user.simulate({ scammer: '1', victim: '1' })
  assert.equal(run.final.status, 'FINISHED')
  assert.equal(run.final.outcome.code, 'NOT_DEFRAUDED')
  assert.equal(run.final.currentRoundNo, 5)
  assert.ok(['PENDING', 'RUNNING', 'SUCCEEDED'].includes(run.final.report.status))
  const types = run.events.map(e => e.type)
  assert.equal(types[0], 'session.state')
  assert.ok(types.indexOf('session.finished') < types.lastIndexOf('round.reading'), 'finished must precede the last round.reading')
  assert.equal(types.filter(t => t === 'message.saved').length, 10)
  assert.equal(types.filter(t => t === 'analysis.saved').length, 5)
  assert.ok(!run.raw.includes('INTERNAL-NOTE-SECRET'), 'internal note leaked to a user response or frame')
  assert.equal(sql(`SELECT COUNT(*) FROM simulation_message m JOIN simulation_round r ON r.id=m.round_id WHERE r.session_id=${run.id}`), '10')
  await waitForReport(user, run.id)
  assert.equal(sql(`SELECT CONCAT(status,'/',attempt_no,'/',JSON_TYPE(content_json)) FROM simulation_report WHERE session_id=${run.id}`), 'SUCCEEDED/1/OBJECT')
  assert.ok(run.audioChecked, 'audio endpoint was never exercised')
  return run
}

async function waitForReport(user, id) {
  for (let i = 0; i < 60; i++) {
    const view = (await user.call('GET', `/simulations/${id}/report`)).data
    if (view.report?.status === 'SUCCEEDED') return view
    assert.notEqual(view.report?.status, 'FAILED', 'report generation failed: ' + JSON.stringify(view.report?.error))
    await sleep(500)
  }
  throw new Error('Report did not complete within 30s')
}

async function reportFlow(user, other, admin) {
  const run = await s01(user), path = `/simulations/${run.id}/report`
  const view = (await user.call('GET', path)).data
  assert.deepEqual(Object.keys(view.content).sort(), ['summary', 'roundAnalyses', 'speechAnalysis', 'victimBehaviorAnalysis', 'outcomeAnalysis'].sort())
  assert.deepEqual(view.content.roundAnalyses.map(r => r.roundNo), [1, 2, 3, 4, 5])
  const scores = sql(`SELECT CONCAT(round_no,':',risk_score) FROM simulation_round WHERE session_id=${run.id} AND risk_score IS NOT NULL ORDER BY round_no`)
  assert.equal(view.riskSeries.map(p => `${p.roundNo}:${p.score}`).join('\n'), scores.replaceAll('\r', ''))
  const snapshot = await fetch(`${BASE}/simulations/${run.id}/events`, { headers: { cookie: user.cookie }, signal: AbortSignal.timeout(10000) })
  const reader = snapshot.body.getReader()
  const initial = await readFrame(reader)
  assert.deepEqual(JSON.parse(/^data:(.*)$/m.exec(initial)[1]).data.reportView, view)
  // Model output is already saved; inject a failure only inside this disposable database to exercise retry.
  sql(`UPDATE simulation_report SET status='FAILED', content_json=NULL, error_code='VALIDATION_FAILED' WHERE session_id=${run.id}`)
  const failed = (await user.call('GET', path)).data
  assert.equal(failed.report.canRetry, true); assert.equal(failed.content, null)
  assert.equal(failed.report.error.message, '报告内容未通过校验，可手动重试。')
  const retried = await user.call('POST', path + '/retry', undefined, 202)
  assert.equal(retried.data.report.status, 'PENDING'); assert.equal(retried.data.report.attemptNo, 1)
  const event = await readFrame(reader)
  assert.match(event, /event:report.updated/)
  reader.cancel().catch(() => {})
  await user.call('POST', path + '/retry')
  const regenerated = await waitForReport(user, run.id)
  assert.equal(regenerated.report.attemptNo, 2)
  const again = (await user.call('POST', path + '/retry')).data
  assert.deepEqual(again.content, regenerated.content); assert.equal(again.report.attemptNo, 2)
  const foreign = await other.call('GET', path, undefined, 404)
  const missing = await other.call('GET', '/simulations/999999/report', undefined, 404)
  foreign.requestId = missing.requestId; assert.deepEqual(foreign, missing)
  await admin.call('GET', path, undefined, 403)
  await new Client().call('GET', path, undefined, 401)
  const token = user.token; user.token = ''
  await user.call('POST', path + '/retry', undefined, 403); user.token = token
  const pending = await other.startOnly({ scammer: '1', victim: '1' }), id = pending.state.sessionId
  assert.equal((await other.call('GET', `/simulations/${id}/report`)).data.unavailableReason, 'SIMULATION_RUNNING')
  await other.call('POST', `/simulations/${id}/report/retry`, undefined, 409)
  await other.call('POST', `/simulations/${id}/exit`, { reason: 'NAVIGATION' })
  const interrupted = (await other.call('GET', `/simulations/${id}/report`)).data
  assert.equal(interrupted.unavailableReason, 'SIMULATION_INTERRUPTED'); assert.equal(interrupted.report, null)
  await other.call('POST', `/simulations/${id}/report/retry`, undefined, 409)
  for (const raw of [user.raw, other.raw, admin.raw]) {
    assert.ok(!/content_json|task_token|INTERNAL-NOTE-SECRET|PASSWORD|[A-Z]:\\/i.test(raw), 'internal data leaked')
  }
}

async function readFrame(reader) {
  const decoder = new TextDecoder()
  let frame = ''
  while (!frame.includes('\n\n')) {
    const chunk = await reader.read()
    assert.ok(!chunk.done, 'SSE stream ended before a complete frame')
    frame += decoder.decode(chunk.value, { stream: true })
  }
  return frame.slice(0, frame.indexOf('\n\n'))
}

async function s04(user, scammerId) {
  const run = await user.simulate({ scammer: scammerId, victim: '2' })
  assert.equal(run.final.outcome?.code, 'NOT_DEFRAUDED')
  assert.ok(!run.raw.includes('DISCARDED-CANDIDATE'), 'discarded candidate was published')
  assert.equal(sql(`SELECT COUNT(*) FROM simulation_message m JOIN simulation_round r ON r.id=m.round_id WHERE r.session_id=${run.id} AND m.content LIKE '%DISCARDED-CANDIDATE%'`), '0')
  assert.equal(sql(`SELECT COUNT(*) FROM simulation_round WHERE session_id=${run.id} AND round_no<5 AND candidate_outcome IS NOT NULL`), '0')
}

async function s07(user, scammerId) {
  const run = await user.simulate({ scammer: scammerId, victim: '3' })
  assert.equal(run.final.status, 'INTERRUPTED')
  assert.equal(run.final.interruption.code, 'TTS_FAILED')
  assert.equal(sql(`SELECT m.presentation_status FROM simulation_message m JOIN simulation_round r ON r.id=m.round_id WHERE r.session_id=${run.id} AND r.round_no=2 AND m.speaker_role='VICTIM'`), 'PENDING')
  assert.equal(sql(`SELECT COUNT(*) FROM simulation_report WHERE session_id=${run.id}`), '0')
}

async function s13(user) {
  let paused = null, pausedEvents = 0
  const run = await user.simulate({ scammer: '2', victim: '1' }, {
    async onMessage(sim, msg, events) {
      if (paused || msg.roundNo !== 1 || msg.speaker !== 'SCAMMER') return
      const before = await sim.call('GET', `/simulations/${sim.id}`)
      const p = await sim.call('POST', `/simulations/${sim.id}/pause`)
      assert.equal(p.data.effect, 'APPLIED')
      assert.equal((await sim.call('POST', `/simulations/${sim.id}/pause`)).data.effect, 'ALREADY_APPLIED')
      const mark = events.length
      await sleep(2000)
      pausedEvents = events.slice(mark).filter(e => ['round.started', 'message.saved'].includes(e.type)).length
      const r = await sim.call('POST', `/simulations/${sim.id}/resume`)
      assert.equal(r.data.effect, 'APPLIED')
      const shift = Date.parse(r.data.state.deadlineAt) - Date.parse(before.data.deadlineAt)
      paused = { shift, credit: r.data.state.pausedCreditMs }
      assert.equal((await sim.call('POST', `/simulations/${sim.id}/resume`)).data.effect, 'ALREADY_APPLIED')
    },
  })
  assert.ok(paused, 'pause never happened')
  assert.ok(paused.shift >= 1900 && paused.shift <= 2600, 'deadline shift ' + paused.shift)
  assert.equal(paused.shift, paused.credit)
  assert.equal(pausedEvents, 0, 'events published while paused')
  assert.equal(run.final.status, 'FINISHED')
}

async function s09(user, other) {
  const started = await user.startOnly({ scammer: '1', victim: '2' })
  const again = await user.call('POST', '/simulations', started.request, 200)
  assert.equal(again.data.sessionId, started.state.sessionId)
  assert.equal(again.data.startedAt, started.state.startedAt)
  const conflict = await user.call('POST', '/simulations', { ...started.request, startRequestId: randomUUID(), timing: await user.timing() }, 409)
  assert.equal(conflict.code, 'ACTIVE_SIMULATION_EXISTS')
  assert.equal(conflict.data.activeSessionId, started.state.sessionId)
  const id = started.state.sessionId
  const foreign = await other.call('GET', `/simulations/${id}`, undefined, 404)
  const missing = await other.call('GET', `/simulations/99999999`, undefined, 404)
  assert.equal(foreign.message, missing.message)
  await other.call('POST', `/simulations/${id}/pause`, undefined, 404)
  assert.equal((await fetch(`${BASE}/simulations/${id}/events`, { headers: { cookie: other.cookie } })).status, 404)
  const exit = await user.call('POST', `/simulations/${id}/exit`, { reason: 'NAVIGATION' })
  assert.equal(exit.data.status, 'INTERRUPTED')
  assert.equal(exit.data.interruption.code, 'USER_EXIT')
  const late = await user.call('POST', `/simulations/${id}/opening/presented`, { status: 'COMPLETED' })
  assert.equal(late.data.effect, 'IGNORED_TERMINAL')
  await user.call('POST', `/simulations/${id}/opening/presented`, { status: 'COMPLETED', outcome: 'GOAL_ACHIEVED' }, 400)
}

// ---------------------------------------------------------------- HTTP + SSE client

class Client {
  cookie = ''; token = ''
  static async register(name) {
    const c = new Client(); await c.csrf()
    await c.call('POST', '/auth/register', { username: name, password: '123456', confirmPassword: '123456' }, 201)
    await c.call('POST', '/auth/login', { username: name, password: '123456' }); await c.csrf(); return c
  }
  static async login(name, password) {
    const c = new Client(); await c.csrf(); await c.call('POST', '/auth/login', { username: name, password }); await c.csrf(); return c
  }
  async csrf() { this.token = (await this.call('GET', '/auth/csrf')).data.token }
  async call(method, path, body, expect = 200) {
    const res = await fetch(BASE + path, { method, headers: { cookie: this.cookie, 'X-CSRF-TOKEN': this.token,
      ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}) }, body: body === undefined ? undefined : JSON.stringify(body) })
    const set = res.headers.getSetCookie().find(c => c.startsWith('JSESSIONID='))
    if (set) this.cookie = set.split(';')[0]
    const text = await res.text()
    this.raw = (this.raw ?? '') + text
    assert.equal(res.status, expect, `${method} ${path} -> ${res.status} ${text.slice(0, 200)}`)
    if (REPORT_ONLY || (method === 'GET' && /^\/simulations(?:\?|$|\/[^/]+\/detail(?:\?|$))/.test(path))) {
      assert.match(res.headers.get('cache-control') ?? '', /no-store/)
    }
    try { return JSON.parse(text) }
    catch { throw new Error(`${method} ${path} returned invalid JSON (${text.length} bytes)`) }
  }
  async timing() {
    const send = performance.now(); const sample = (await this.call('GET', '/timing/samples')).data; const receive = performance.now()
    await sleep(50)
    return { methodVersion: sample.methodVersion, sampleId: sample.sampleId, clientSendMonotonicMs: send,
      clientReceiveMonotonicMs: receive, clientClickMonotonicMs: performance.now() }
  }
  async startOnly({ scammer, victim }) {
    const request = { startRequestId: randomUUID(), scenarioId: '1', scammerProfileId: scammer, victimProfileId: victim, timing: await this.timing() }
    const state = (await this.call('POST', '/simulations', request, 201)).data
    return { request, state }
  }
  /** Plays a whole run like the front end: receipts after each presented item, until a terminal event. */
  async simulate(config, hooks = {}) {
    const { state } = await this.startOnly(config)
    const sim = { id: state.sessionId, call: (...a) => this.call(...a) }
    const events = []; let raw = ''; let audioChecked = false
    const res = await fetch(`${BASE}/simulations/${sim.id}/events`, { headers: { cookie: this.cookie, accept: 'text/event-stream' } })
    assert.equal(res.status, 200)
    assert.match(res.headers.get('cache-control') ?? '', /no-store/)
    const reader = res.body.getReader(); const decoder = new TextDecoder(); let buffer = ''
    await this.call('POST', `/simulations/${sim.id}/opening/presented`, { status: 'COMPLETED' })
    const deadline = Date.now() + 300_000
    let finished = null
    while (!finished && Date.now() < deadline) {
      const { value, done } = await reader.read(); if (done) break
      buffer += decoder.decode(value, { stream: true })
      let cut
      while ((cut = buffer.indexOf('\n\n')) >= 0) {
        const frame = buffer.slice(0, cut); buffer = buffer.slice(cut + 2); raw += frame
        const type = /^event:(.*)$/m.exec(frame)?.[1]?.trim(); const data = /^data:(.*)$/m.exec(frame)?.[1]
        if (!type || !data) continue
        const event = JSON.parse(data); events.push({ type, event })
        if (type === 'message.saved') {
          const msg = event.data
          if (!audioChecked) {
            const audio = await fetch(`http://127.0.0.1:${APP_PORT}` + msg.audioUrl, { headers: { cookie: this.cookie } })
            assert.equal(audio.status, 200); assert.equal(audio.headers.get('content-type'), 'audio/mpeg'); audioChecked = true
          }
          if (hooks.onMessage) await hooks.onMessage(sim, msg, events)
          await this.call('POST', `/simulations/${sim.id}/messages/${msg.id}/presentation`, { status: 'COMPLETED' })
        } else if (type === 'analysis.saved') {
          await this.call('POST', `/simulations/${sim.id}/rounds/${event.data.roundNo}/analysis/presented`, { status: 'COMPLETED' })
        } else if (type === 'session.interrupted' || (type === 'round.reading' && event.data.nextAction === 'REPORT')) {
          finished = true
        }
      }
    }
    reader.cancel().catch(() => {})
    assert.ok(finished, 'run did not reach a terminal state in time')
    const final = (await this.call('GET', `/simulations/${sim.id}`)).data
    return { id: sim.id, final, events, raw: raw + this.raw, audioChecked }
  }
}

// ---------------------------------------------------------------- utilities

function sql(statement, withPassword = true) {
  const out = spawnSync(join(MYSQL_BIN, 'mysql'), ['--no-defaults', '--default-character-set=utf8mb4', '-h127.0.0.1', `-P${DB_PORT}`, '-uroot', '-N', '-B',
    ...(withPassword ? ['sim_smoke'] : []), '-e', statement], { env: { ...process.env, MYSQL_PWD: withPassword ? PASSWORD : '' }, encoding: 'utf8' })
  if (out.status !== 0) throw new Error('SQL failed: ' + out.stderr)
  return out.stdout.trim()
}

function launch(cmd, args, name, options = {}) {
  const child = spawn(cmd, args, { ...options, stdio: ['ignore', 'pipe', 'pipe'] })
  const log = createWriteStream(join(RUN, name + '.log'))
  child.stdout.pipe(log); child.stderr.pipe(log)
  return child
}

async function check(name, fn) {
  const began = Date.now()
  try { await fn(); results.push(`PASS ${name} (${((Date.now() - began) / 1000).toFixed(1)}s)`) }
  catch (e) { results.push(`FAIL ${name}: ${e.message}`) }
}

main().catch(e => results.push('FAIL setup: ' + e.message)).finally(() => {
  for (const c of children.reverse()) {
    if (c.spawnfile.includes('mysqld')) {
      spawnSync(join(MYSQL_BIN, 'mysqladmin'), ['--no-defaults', '-h127.0.0.1', `-P${DB_PORT}`, '-uroot', 'shutdown'],
        { env: { ...process.env, MYSQL_PWD: PASSWORD } })
    }
    c.kill() // These are direct Java/MySQL children; terminate only the processes this harness owns.
  }
  console.log(results.join('\n'))
  console.log('Logs: ' + RUN)
  process.exitCode = results.some(r => r.startsWith('FAIL')) ? 1 : 0
})
