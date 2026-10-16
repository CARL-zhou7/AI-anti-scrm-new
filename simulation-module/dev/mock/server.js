// 本地模拟后端（Vite 插件）：按接口文档实现演绎相关接口与 SSE，用固定剧本代替 Agent 与 TTS，
// 便于前端在后端完成前联调动画、语音、回执与终态。仅用于开发，不并入主工程。
//
// 调试开关：POST /__mock/config { speed: 'normal'|'fast', fail: null|'audio'|'tts'|'avatar'|'animation', limitSeconds, tts: 'auto'|'edge'|'tone' }
// 语音：tts=auto 优先使用真实中文神经语音（需联网），不可用时退回提示音；GET /__mock/status 查看当前语音来源。

import { readFileSync, existsSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { SCENARIOS, SCAMMERS, VICTIMS, SCRIPTS } from './data.js'
import { createTtsService } from './tts/index.js'
import { assignVoices, withSpeed } from './tts/voiceProfiles.js'

const PREFIX = '/anti.scam/v1'
const AVATAR_DIR = fileURLToPath(new URL('../../assets/avatars/', import.meta.url))
const CSRF_TOKEN = 'mock-csrf-token'
const READING_MS = 5000

const mock = { speed: 'normal', fail: null, limitSeconds: 600, tts: 'auto' }
const tts = createTtsService(() => mock)
const sessions = new Map()
const samples = new Map()
let nextSessionId = 501
let nextMessageId = 1001
let eventSeq = 1

const iso = (ms) => new Date(ms).toISOString()
const pace = () => (mock.speed === 'fast' ? { msPerChar: 55, genMs: 500, analysisMs: 500 } : { msPerChar: 150, genMs: 1400, analysisMs: 1200 })

// ---------- HTTP 工具 ----------

function send(res, status, body, headers = {}) {
  res.statusCode = status
  res.setHeader('Content-Type', 'application/json; charset=utf-8')
  res.setHeader('Cache-Control', 'no-store')
  for (const [k, v] of Object.entries(headers)) res.setHeader(k, v)
  res.end(JSON.stringify(body))
}
const ok = (res, data, status = 200) => send(res, status, { code: 'OK', message: '成功', data, requestId: crypto.randomUUID() })
const fail = (res, status, code, message, data = null) => send(res, status, { code, message, data, requestId: crypto.randomUUID() })

function readBody(req) {
  return new Promise((resolve) => {
    let raw = ''
    req.on('data', (c) => (raw += c))
    req.on('end', () => {
      try {
        resolve(raw ? JSON.parse(raw) : {})
      } catch {
        resolve(null)
      }
    })
  })
}

// ---------- 演绎状态 ----------

function liveMessage(s, m) {
  return {
    id: m.id,
    roundNo: m.roundNo,
    speaker: m.speaker,
    content: m.content,
    presentationStatus: m.presentationStatus,
    presentationCompletedAt: m.presentationCompletedAt,
    presentationErrorCode: m.presentationErrorCode,
    savedAt: m.savedAt,
    audioUrl: s.status === 'RUNNING' && m.presentationStatus === 'PRESENTING' ? `${PREFIX}/simulations/${s.id}/messages/${m.id}/audio` : null,
  }
}

function liveRound(s, r) {
  if (!r) return null
  return {
    roundNo: r.roundNo,
    phase: r.phase,
    interactionComplete: r.messages.length === 2,
    presentationComplete: r.phase === 'COMPLETE',
    messages: r.messages.map((m) => liveMessage(s, m)),
    analysis: r.analysis,
    readingUntil: r.readingUntil,
  }
}

function toState(s) {
  return {
    sessionId: s.id,
    status: s.status,
    stateVersion: String(s.version),
    startedAt: iso(s.startedAt),
    deadlineAt: iso(s.deadlineAt),
    endedAt: s.endedAt ? iso(s.endedAt) : null,
    currentRoundNo: s.currentRoundNo,
    configuration: s.configuration,
    currentRound: liveRound(s, s.rounds[s.currentRoundNo - 1]),
    latestRiskScore: s.latestRiskScore,
    outcome: s.outcome,
    interruption: s.interruption,
    readingUntil: s.readingUntil ? iso(s.readingUntil) : null,
    nextAction: s.nextAction,
    report: s.report,
  }
}

function reportView(s) {
  return {
    sessionId: s.id,
    simulationStatus: s.status,
    report: s.report,
    unavailableReason: s.status === 'RUNNING' ? 'SIMULATION_RUNNING' : s.status === 'INTERRUPTED' ? 'SIMULATION_INTERRUPTED' : null,
    outcome: s.outcome,
    content: null,
    riskSeries: s.rounds.filter((r) => r.analysis).map((r) => ({ roundNo: r.roundNo, score: r.analysis.riskScore })),
  }
}

function publish(s, type, data) {
  const eventId = `evt-${eventSeq++}`
  const frame = `id: ${eventId}\nevent: ${type}\ndata: ${JSON.stringify({ eventId, sessionId: s.id, stateVersion: String(s.version), data })}\n\n`
  for (const res of s.clients) res.write(frame)
}

function later(s, ms, fn) {
  const t = setTimeout(() => {
    s.timers.delete(t)
    fn()
  }, ms)
  s.timers.add(t)
}

function clearTimers(s) {
  for (const t of s.timers) clearTimeout(t)
  s.timers.clear()
}

function interrupt(s, code, message) {
  if (s.status !== 'RUNNING') return
  clearTimers(s)
  s.status = 'INTERRUPTED'
  s.endedAt = Date.now()
  s.interruption = { code, message }
  s.readingUntil = null
  s.nextAction = null
  s.version++
  publish(s, 'session.interrupted', toState(s))
}

function finish(s, outcome) {
  clearTimers(s)
  const now = Date.now()
  s.status = 'FINISHED'
  s.endedAt = now
  s.outcome = outcome
  s.report = { status: 'PENDING', displayText: '生成中', canRetry: false, attemptNo: 0, updatedAt: iso(now), error: null }
  s.readingUntil = Math.min(now + READING_MS, s.deadlineAt)
  s.nextAction = 'REPORT'
  const round = s.rounds[s.currentRoundNo - 1]
  if (round) round.readingUntil = iso(s.readingUntil)
  s.version++
  publish(s, 'session.finished', toState(s))
  publish(s, 'round.reading', { roundNo: s.currentRoundNo, readingUntil: iso(s.readingUntil), nextAction: 'REPORT' })
  setTimeout(() => {
    s.report = { ...s.report, status: 'RUNNING', attemptNo: 1, updatedAt: iso(Date.now()) }
    publish(s, 'report.updated', reportView(s))
  }, 1500)
  setTimeout(() => {
    s.report = { status: 'SUCCEEDED', displayText: '已完成', canRetry: false, attemptNo: 1, updatedAt: iso(Date.now()), error: null }
    publish(s, 'report.updated', reportView(s))
  }, 4000)
}

function startRound(s) {
  if (s.status !== 'RUNNING') return
  const roundNo = s.currentRoundNo + 1
  if (roundNo > 20 || roundNo > SCRIPTS[s.configuration.scenario.code].length) {
    return finish(s, { code: 'UNDETERMINED', displayText: '结局未定', reason: '达到上限时仍不满足其他结局条件。', limitReason: 'ROUND_LIMIT' })
  }
  s.currentRoundNo = roundNo
  s.rounds.push({ roundNo, phase: 'SCAMMER_GENERATING', messages: [], analysis: null, readingUntil: null })
  s.readingUntil = null
  s.nextAction = null
  s.version++
  publish(s, 'round.started', { roundNo, phase: 'SCAMMER_GENERATING', startedAt: iso(Date.now()), deadlineAt: iso(s.deadlineAt) })
  generateMessage(s, 'SCAMMER')
}

function generateMessage(s, speaker) {
  later(s, pace().genMs, () => {
    if (s.status !== 'RUNNING') return
    const round = s.rounds[s.currentRoundNo - 1]
    const line = SCRIPTS[s.configuration.scenario.code][round.roundNo - 1]
    const msg = {
      id: String(nextMessageId++),
      roundNo: round.roundNo,
      speaker,
      content: speaker === 'SCAMMER' ? line.scammer : line.victim,
      presentationStatus: 'PENDING',
      presentationCompletedAt: null,
      presentationErrorCode: null,
      savedAt: iso(Date.now()),
    }
    round.messages.push(msg)
    round.phase = speaker === 'SCAMMER' ? 'SCAMMER_GENERATING' : 'VICTIM_GENERATING'
    s.version++
    // 文本已保存，合成该段语音；就绪后才发布 message.saved
    synthesize(s, msg).then((audio) => {
      if (s.status !== 'RUNNING') return
      if (!audio || (mock.fail === 'tts' && round.roundNo === 2 && speaker === 'VICTIM')) {
        return interrupt(s, 'TTS_FAILED', '角色语音生成失败，本次演绎已中断。')
      }
      msg.audio = audio
      msg.presentationStatus = 'PRESENTING'
      round.phase = speaker === 'SCAMMER' ? 'SCAMMER_PRESENTING' : 'VICTIM_PRESENTING'
      s.version++
      publish(s, 'message.saved', liveMessage(s, msg))
    })
  })
}

async function synthesize(s, msg) {
  const { scammer, victim } = s.configuration
  const voices = assignVoices(scammer, victim)
  const profile = withSpeed(voices[msg.speaker], mock.speed === 'fast' ? 20 : 0)
  try {
    return await tts.synthesize(msg.content, { speaker: msg.speaker, profile, seed: Number(msg.id), msPerChar: pace().msPerChar })
  } catch {
    return null
  }
}

function generateAnalysis(s) {
  later(s, pace().analysisMs, () => {
    if (s.status !== 'RUNNING') return
    const round = s.rounds[s.currentRoundNo - 1]
    const line = SCRIPTS[s.configuration.scenario.code][round.roundNo - 1]
    round.analysis = { ...line.analysis, savedAt: iso(Date.now()), presentedAt: null }
    round.phase = 'ANALYSIS_PRESENTING'
    s.latestRiskScore = line.analysis.riskScore
    s.version++
    publish(s, 'analysis.saved', { roundNo: round.roundNo, analysis: round.analysis })
  })
}

function afterAnalysisPresented(s) {
  const round = s.rounds[s.currentRoundNo - 1]
  const line = SCRIPTS[s.configuration.scenario.code][round.roundNo - 1]
  round.analysis.presentedAt = iso(Date.now())
  round.phase = 'COMPLETE'
  if (line.outcome) return finish(s, line.outcome)
  const now = Date.now()
  s.readingUntil = Math.min(now + READING_MS, s.deadlineAt)
  round.readingUntil = iso(s.readingUntil)
  s.nextAction = 'NEXT_ROUND'
  s.version++
  publish(s, 'round.reading', { roundNo: round.roundNo, readingUntil: iso(s.readingUntil), nextAction: 'NEXT_ROUND' })
  later(s, s.readingUntil - now, () => {
    if (Date.now() >= s.deadlineAt) return
    startRound(s)
  })
}

function onDeadline(s) {
  if (s.status !== 'RUNNING') return
  const round = s.rounds[s.currentRoundNo - 1]
  if (round && round.phase === 'COMPLETE') {
    return finish(s, { code: 'UNDETERMINED', displayText: '结局未定', reason: '达到时长上限时仍不满足其他结局条件。', limitReason: 'TIME_LIMIT' })
  }
  interrupt(s, 'TIME_LIMIT', '中断：达到时长上限。')
}

// ---------- 路由 ----------

function findSession(id) {
  return sessions.get(id) || null
}

const ALLOWED = {
  opening: ['TEXT_RENDER_FAILED', 'AVATAR_LOAD_FAILED', 'ANIMATION_FAILED'],
  message: ['TEXT_RENDER_FAILED', 'ANIMATION_FAILED', 'AUDIO_LOAD_FAILED', 'AUDIO_PLAYBACK_FAILED'],
  analysis: ['TEXT_RENDER_FAILED'],
}

function validReceipt(body, scope) {
  if (!body || typeof body !== 'object') return false
  const keys = Object.keys(body)
  if (keys.some((k) => k !== 'status' && k !== 'errorCode')) return false
  if (body.status === 'COMPLETED') return body.errorCode === undefined
  if (body.status === 'FAILED') return ALLOWED[scope].includes(body.errorCode)
  return false
}

async function handle(req, res) {
  const url = new URL(req.url, 'http://local')
  const path = url.pathname
  const method = req.method

  if (path === '/__mock/config' && method === 'POST') {
    Object.assign(mock, await readBody(req))
    return ok(res, mock)
  }
  if (path === '/__mock/status' && method === 'GET') return ok(res, { mock, tts: tts.status })
  if (!path.startsWith(PREFIX)) return false
  const p = path.slice(PREFIX.length)
  const isWrite = method === 'POST' || method === 'PUT'
  if (isWrite && req.headers['x-csrf-token'] !== CSRF_TOKEN) return fail(res, 403, 'CSRF_INVALID', 'CSRF校验失败，请重新获取令牌')

  let m
  if (p === '/auth/csrf') return ok(res, { token: CSRF_TOKEN, headerName: 'X-CSRF-TOKEN' })
  if (p === '/auth/me') return ok(res, { id: '101', username: 'demo_user', role: 'USER' })
  if (p === '/scenarios') return ok(res, SCENARIOS)
  if (p === '/scammer-profiles') return ok(res, { items: SCAMMERS, nextCursor: null, hasMore: false })
  if (p === '/victim-profiles') return ok(res, { items: VICTIMS, nextCursor: null, hasMore: false })

  if ((m = p.match(/^\/avatars\/([A-Za-z0-9_-]+)$/))) {
    const file = `${AVATAR_DIR}${m[1]}.png`
    const brokenVictim = mock.fail === 'avatar' && VICTIMS.some((v) => v.avatar.key === m[1])
    if (!existsSync(file) || brokenVictim) return fail(res, 404, 'NOT_FOUND', '资源不存在或不可访问')
    res.setHeader('Content-Type', 'image/png')
    res.setHeader('Cache-Control', 'private, no-store')
    return res.end(readFileSync(file))
  }

  if (p === '/timing/samples' && method === 'GET') {
    const now = Date.now()
    const sample = { sampleId: `sample_${crypto.randomUUID()}`, serverSampledAt: iso(now), expiresAt: iso(now + 60000), methodVersion: '1.0' }
    samples.set(sample.sampleId, { sampledAt: now, expiresAt: now + 60000 })
    return ok(res, sample)
  }

  if (p === '/simulations' && method === 'POST') {
    const body = await readBody(req)
    if (!body) return fail(res, 400, 'VALIDATION_FAILED', '请求字段不符合规则')
    const existing = [...sessions.values()].find((s) => s.startRequestId === body.startRequestId)
    if (existing) return ok(res, toState(existing), 200)
    const active = [...sessions.values()].find((s) => s.status === 'RUNNING')
    if (active) return fail(res, 409, 'ACTIVE_SIMULATION_EXISTS', '已有进行中的演绎', { fieldErrors: [], activeSessionId: active.id, currentVersion: null })
    const t = body.timing || {}
    const sample = samples.get(t.sampleId)
    const now = Date.now()
    const okOrder = t.clientSendMonotonicMs <= t.clientReceiveMonotonicMs && t.clientReceiveMonotonicMs <= t.clientClickMonotonicMs
    if (!sample || sample.expiresAt < now || !okOrder || t.methodVersion !== '1.0') {
      return fail(res, 400, 'VALIDATION_FAILED', '请求字段不符合规则', {
        fieldErrors: [{ field: 'timing', code: 'VALUE_INVALID', message: '计时证据不可接受' }],
        activeSessionId: null,
        currentVersion: null,
      })
    }
    const scenario = SCENARIOS.find((x) => x.id === body.scenarioId)
    const scammer = SCAMMERS.find((x) => x.id === body.scammerProfileId)
    const victim = VICTIMS.find((x) => x.id === body.victimProfileId)
    if (!scenario || !scammer || !victim) return fail(res, 409, 'PROFILE_UNAVAILABLE', '所选画像已不可用，请重新选择')
    // 采样时刻对应发送与接收的中点；点击时刻据此映射到服务端时间轴
    const startedAt = Math.round(sample.sampledAt + (t.clientClickMonotonicMs - (t.clientSendMonotonicMs + t.clientReceiveMonotonicMs) / 2))
    const s = {
      id: String(nextSessionId++),
      startRequestId: body.startRequestId,
      status: 'RUNNING',
      version: 0,
      startedAt,
      deadlineAt: startedAt + mock.limitSeconds * 1000,
      endedAt: null,
      currentRoundNo: 0,
      configuration: { scenario, scammer, victim },
      rounds: [],
      latestRiskScore: null,
      outcome: null,
      interruption: null,
      readingUntil: null,
      nextAction: null,
      report: null,
      clients: new Set(),
      timers: new Set(),
    }
    sessions.set(s.id, s)
    setTimeout(() => onDeadline(s), s.deadlineAt - now)
    publish(s, 'session.started', toState(s))
    return ok(res, toState(s), 201)
  }

  if ((m = p.match(/^\/simulations\/(\d+)(\/.*)?$/))) {
    const s = findSession(m[1])
    if (!s) return fail(res, 404, 'NOT_FOUND', '资源不存在或不可访问')
    const sub = m[2] || ''

    if (sub === '' && method === 'GET') return ok(res, toState(s))

    if (sub === '/events' && method === 'GET') {
      res.writeHead(200, { 'Content-Type': 'text/event-stream', 'Cache-Control': 'no-store', Connection: 'keep-alive', 'X-Accel-Buffering': 'no' })
      res.write(': connected\n\n')
      s.clients.add(res)
      const eventId = `evt-${eventSeq++}`
      res.write(`id: ${eventId}\nevent: session.state\ndata: ${JSON.stringify({ eventId, sessionId: s.id, stateVersion: String(s.version), data: { state: toState(s), reportView: reportView(s) } })}\n\n`)
      const heartbeat = setInterval(() => res.write(': ping\n\n'), 15000)
      req.on('close', () => {
        clearInterval(heartbeat)
        s.clients.delete(res)
        // 简化的失联收敛：事件流断开且 10 秒内没有重连，按 CONNECTION_LOST 中断（真实判定见接口文档§9.1）
        setTimeout(() => {
          if (s.clients.size === 0) interrupt(s, 'CONNECTION_LOST', '连接已断开，本次演绎已中断。')
        }, 10000)
      })
      return
    }

    if (sub === '/opening/presented' && method === 'POST') {
      const body = await readBody(req)
      if (!validReceipt(body, 'opening')) return fail(res, 400, 'VALIDATION_FAILED', '请求字段不符合规则')
      if (s.status !== 'RUNNING') return ok(res, { effect: 'IGNORED_TERMINAL', state: toState(s) })
      if (s.currentRoundNo !== 0) {
        if (body.status === 'COMPLETED') return ok(res, { effect: 'ALREADY_APPLIED', state: toState(s) })
        return fail(res, 409, 'PRESENTATION_CONFLICT', '呈现结果与已保存事实冲突')
      }
      if (body.status === 'FAILED') {
        interrupt(s, 'MEDIA_FAILED', '必要媒体呈现失败，本次演绎已中断。')
        return ok(res, { effect: 'APPLIED', state: toState(s) })
      }
      startRound(s)
      return ok(res, { effect: 'APPLIED', state: toState(s) })
    }

    if ((m = sub.match(/^\/messages\/(\d+)\/presentation$/)) && method === 'POST') {
      const body = await readBody(req)
      if (!validReceipt(body, 'message')) return fail(res, 400, 'VALIDATION_FAILED', '请求字段不符合规则')
      const round = s.rounds.find((r) => r.messages.some((x) => x.id === m[1]))
      const msg = round?.messages.find((x) => x.id === m[1])
      if (!msg) return fail(res, 404, 'NOT_FOUND', '资源不存在或不可访问')
      if (s.status !== 'RUNNING') return ok(res, { effect: 'IGNORED_TERMINAL', state: toState(s) })
      if (msg.presentationStatus === 'COMPLETED' && body.status === 'COMPLETED') return ok(res, { effect: 'ALREADY_APPLIED', state: toState(s) })
      if (msg.presentationStatus !== 'PRESENTING' || round.roundNo !== s.currentRoundNo) return fail(res, 409, 'STAGE_CONFLICT', '当前阶段不接受该呈现回执')
      const now = iso(Date.now())
      if (body.status === 'FAILED') {
        msg.presentationStatus = 'FAILED'
        msg.presentationErrorCode = body.errorCode
        interrupt(s, 'MEDIA_FAILED', '必要媒体呈现失败，本次演绎已中断。')
        return ok(res, { effect: 'APPLIED', state: toState(s) })
      }
      msg.presentationStatus = 'COMPLETED'
      msg.presentationCompletedAt = now
      msg.audio = null
      if (msg.speaker === 'SCAMMER') {
        round.phase = 'VICTIM_GENERATING'
        s.version++
        generateMessage(s, 'VICTIM')
      } else {
        round.phase = 'ANALYZING'
        s.version++
        generateAnalysis(s)
      }
      return ok(res, { effect: 'APPLIED', state: toState(s) })
    }

    if ((m = sub.match(/^\/rounds\/(\d+)\/analysis\/presented$/)) && method === 'POST') {
      const body = await readBody(req)
      if (!validReceipt(body, 'analysis')) return fail(res, 400, 'VALIDATION_FAILED', '请求字段不符合规则')
      const round = s.rounds[Number(m[1]) - 1]
      if (!round) return fail(res, 404, 'NOT_FOUND', '资源不存在或不可访问')
      if (s.status !== 'RUNNING') return ok(res, { effect: s.status === 'FINISHED' && round.analysis?.presentedAt ? 'ALREADY_APPLIED' : 'IGNORED_TERMINAL', state: toState(s) })
      if (round.analysis?.presentedAt && body.status === 'COMPLETED') return ok(res, { effect: 'ALREADY_APPLIED', state: toState(s) })
      if (round.phase !== 'ANALYSIS_PRESENTING' || round.roundNo !== s.currentRoundNo) return fail(res, 409, 'STAGE_CONFLICT', '当前阶段不接受该呈现回执')
      if (body.status === 'FAILED') {
        interrupt(s, 'MEDIA_FAILED', '必要媒体呈现失败，本次演绎已中断。')
        return ok(res, { effect: 'APPLIED', state: toState(s) })
      }
      afterAnalysisPresented(s)
      return ok(res, { effect: 'APPLIED', state: toState(s) })
    }

    if (sub === '/exit' && method === 'POST') {
      const body = await readBody(req)
      if (!body || !['NAVIGATION', 'REFRESH', 'CLOSE', 'LOGOUT'].includes(body.reason)) return fail(res, 400, 'VALIDATION_FAILED', '请求字段不符合规则')
      interrupt(s, 'USER_EXIT', '已离开演绎页面，本次演绎已中断。')
      return ok(res, toState(s))
    }

    if ((m = sub.match(/^\/messages\/(\d+)\/audio$/)) && method === 'GET') {
      const msg = s.rounds.flatMap((r) => r.messages).find((x) => x.id === m[1])
      if (!msg) return fail(res, 404, 'NOT_FOUND', '资源不存在或不可访问')
      if (mock.fail === 'audio' && msg.roundNo === 2 && msg.speaker === 'VICTIM') return fail(res, 404, 'NOT_FOUND', '资源不存在或不可访问')
      if (s.status !== 'RUNNING' || msg.presentationStatus !== 'PRESENTING' || !msg.audio) return fail(res, 410, 'AUDIO_EXPIRED', '该消息语音已不可访问')
      res.statusCode = 200
      res.setHeader('Content-Type', msg.audio.contentType)
      res.setHeader('Cache-Control', 'private, no-store')
      res.setHeader('X-Content-Type-Options', 'nosniff')
      res.setHeader('Content-Length', msg.audio.audio.length)
      return res.end(msg.audio.audio)
    }
  }

  return fail(res, 404, 'NOT_FOUND', '资源不存在或不可访问')
}

export function mockBackendPlugin() {
  return {
    name: 'anti-scam-mock-backend',
    configureServer(server) {
      server.middlewares.use((req, res, next) => {
        Promise.resolve(handle(req, res))
          .then((handled) => {
            if (handled === false) next()
          })
          .catch((err) => {
            console.error('[mock]', err)
            if (!res.headersSent) fail(res, 500, 'INTERNAL_ERROR', '服务暂时无法完成请求')
          })
      })
    },
  }
}
