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
const RUN = join(ROOT, '.tools', 'simulation-smoke-' + randomUUID().slice(0, 8))
const DB_PORT = 13310, APP_PORT = 18083, BASE = `http://127.0.0.1:${APP_PORT}/anti.scam/v1`
const PASSWORD = randomUUID().replaceAll('-', '')
const sleep = ms => new Promise(r => setTimeout(r, ms))
const children = []
const results = []

async function main() {
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
  for (const script of ['precheck-regen-r3', 'tts-fail-r2']) {
    const body = { name: '剧本:' + script, age: 30, gender: '男', identity: '诈骗团伙话务人员', description: '冒烟用', contextTime: '晚上',
      contextPlace: '房间', currentState: '准备联系', fraudExperience: 'HIGH', speechSkill: 'HIGH', manipulationSkill: 'HIGH',
      behaviorTraits: '冒烟用', avatarKey: seed.avatar.key, speakingAvatarKey: seed.speakingAvatar.key }
    scripted[script] = (await admin.call('POST', '/admin/scammer-profiles', body, 201)).data.id
  }
  await admin.call('DELETE', '/admin/scammer-profiles/' + scripted['tts-fail-r2'], { version: 0 }, 400) // D-1: no permanent delete

  const users = await Promise.all(['SimA', 'SimB', 'SimC', 'SimD', 'SimE'].map(u => Client.register(u)))
  await Promise.all([
    check('S01 five rounds then NOT_DEFRAUDED, PENDING report, isolation', () => s01(users[0])),
    check('S04 round-3 precheck regeneration never published', () => s04(users[1], scripted['precheck-regen-r3'])),
    check('S07 victim TTS failure -> TTS_FAILED, text kept PENDING', () => s07(users[2], scripted['tts-fail-r2'])),
    check('S13 pause/resume shifts deadline, nothing published while paused', () => s13(users[3])),
    check('S09/S10/S12 exit, idempotent start, active conflict, permissions', () => s09(users[4], users[0])),
  ])
}

// ---------------------------------------------------------------- scenarios

async function s01(user) {
  const run = await user.simulate({ scammer: '1', victim: '1' })
  assert.equal(run.final.status, 'FINISHED')
  assert.equal(run.final.outcome.code, 'NOT_DEFRAUDED')
  assert.equal(run.final.currentRoundNo, 5)
  assert.equal(run.final.report.status, 'PENDING')
  const types = run.events.map(e => e.type)
  assert.equal(types[0], 'session.state')
  assert.ok(types.indexOf('session.finished') < types.lastIndexOf('round.reading'), 'finished must precede the last round.reading')
  assert.equal(types.filter(t => t === 'message.saved').length, 10)
  assert.equal(types.filter(t => t === 'analysis.saved').length, 5)
  assert.ok(!run.raw.includes('INTERNAL-NOTE-SECRET'), 'internal note leaked to a user response or frame')
  assert.equal(sql(`SELECT COUNT(*) FROM simulation_message m JOIN simulation_round r ON r.id=m.round_id WHERE r.session_id=${run.id}`), '10')
  assert.equal(sql(`SELECT CONCAT(status,'/',attempt_no) FROM simulation_report WHERE session_id=${run.id}`), 'PENDING/0')
  assert.ok(run.audioChecked, 'audio endpoint was never exercised')
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
    return JSON.parse(text)
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
  const out = spawnSync(join(MYSQL_BIN, 'mysql'), ['--no-defaults', '-h127.0.0.1', `-P${DB_PORT}`, '-uroot', '-N', '-B',
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
    if (process.platform === 'win32') spawnSync('taskkill', ['/pid', String(c.pid), '/t', '/f'])
    else c.kill()
  }
  console.log(results.join('\n'))
  console.log('Logs: ' + RUN)
  process.exitCode = results.some(r => r.startsWith('FAIL')) ? 1 : 0
})
