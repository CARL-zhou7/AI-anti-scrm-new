import assert from 'node:assert/strict'
import { existsSync } from 'node:fs'
import { test } from 'node:test'
import { get, login, logout, write } from '../src/api.js'
import { nextPresentation, panelTextSize, playedRounds, reveal, stageAction, startId, trendPlot, trendY } from '../src/presentation.js'
import { profileDetailRows } from '../src/profileDetail.js'
import { isOfficialNewsUrl, isRecommendedVictim, newsArt, portraitArt, riskColor } from '../src/ui.js'

test('casting recommends the same age groups as web for the selected scenario', () => {
  const ages = [34, 35, 59, 60]
  assert.deepEqual(ages.filter(age => isRecommendedVictim('TASK_REBATE', age)), [34])
  assert.deepEqual(ages.filter(age => isRecommendedVictim('FAKE_INVESTMENT', age)), [35, 59])
  assert.deepEqual(ages.filter(age => isRecommendedVictim('IMPERSONATION_LOAN', age)), [60])
  assert.equal(isRecommendedVictim(undefined, 41), false)
  assert.equal(isRecommendedVictim('TASK_REBATE', null), false)
})

test('session rotation, CSRF refresh and logout use the shared backend contract', async () => {
  const storage = new Map()
  const calls = []
  let csrfReads = 0
  let pauseAttempts = 0
  const ok = (data, extra) => ({ statusCode: 200, data: { code: 'OK', data }, ...extra })
  globalThis.uni = {
    getStorageSync: key => storage.get(key),
    setStorageSync: (key, value) => storage.set(key, value),
    removeStorageSync: key => storage.delete(key),
    async request(options) {
      const path = options.url.split('/anti.scam/v1')[1]
      calls.push({ path, header: options.header })
      if (path === '/auth/csrf') return ok({ token: 'csrf-' + ++csrfReads }, csrfReads === 1
        ? { cookies: ['JSESSIONID=guest; Path=/; HttpOnly'] } : {})
      if (path === '/auth/login') return ok({ role: 'USER' }, { header: { 'set-cookie': 'JSESSIONID=member; Path=/; HttpOnly' } })
      if (path.endsWith('/pause') && ++pauseAttempts === 1) return { statusCode: 403, data: { code: 'CSRF_INVALID' } }
      return ok({})
    }
  }
  await login({ username: 'test', password: 'password' })
  await get('/auth/me')
  await write('/simulations/1/pause')
  await logout()
  assert.deepEqual(calls.map(call => [call.path, call.header.Cookie, call.header['X-CSRF-TOKEN']]), [
    ['/auth/csrf', undefined, undefined],
    ['/auth/login', 'JSESSIONID=guest', 'csrf-1'],
    ['/auth/me', 'JSESSIONID=member', undefined],
    ['/auth/csrf', 'JSESSIONID=member', undefined],
    ['/simulations/1/pause', 'JSESSIONID=member', 'csrf-2'],
    ['/auth/csrf', 'JSESSIONID=member', undefined],
    ['/simulations/1/pause', 'JSESSIONID=member', 'csrf-3'],
    ['/auth/logout', 'JSESSIONID=member', 'csrf-3']
  ])
  assert.equal(storage.size, 0)
})

test('presentation only acknowledges ready media and handles Unicode reveal', () => {
  const line = { id: '11', presentationStatus: 'PENDING' }
  assert.equal(nextPresentation({ messages: [line] }), null)
  line.presentationStatus = 'PRESENTING'
  assert.equal(nextPresentation({ messages: [line] }).path, '/messages/11/presentation')
  line.presentationStatus = 'COMPLETED'
  const round = { roundNo: 2, phase: 'ANALYSIS_PRESENTING', messages: [line], analysis: {} }
  assert.equal(nextPresentation(round).path, '/rounds/2/analysis/presented')
  round.analysis.presentedAt = '2026-11-08T00:00:00Z'
  assert.equal(nextPresentation(round), null)
  assert.equal(reveal('你好🙂！', [{ startMs: 0, endMs: 100, charStart: 0, charEnd: 4 }], 75), '你好🙂')
  assert.match(startId(), /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/)
  assert.equal(new Set([12, 36, 58, 86].map(riskColor)).size, 4)
})

test('the five supplied portraits have both matching mouth frames', () => {
  for (const [id, name] of [[1, '陈启明'], [2, '梁悦'], [1, '林晓'], [2, '赵诚'], [3, '周国良']]) {
    const art = portraitArt({ id, name })
    assert.ok(art)
    for (const mouth of ['closed', 'open']) assert.ok(existsSync(new URL(`../src${art}-${mouth}.webp`, import.meta.url)))
  }
  assert.equal(portraitArt({ id: 3, name: '周桂兰' }), '')
})

test('each official story has a separate illustration and only approved HTTPS links can open', () => {
  const pictures = ['真实案例', '反诈新闻', '防骗提醒'].map(newsArt)
  assert.equal(new Set(pictures).size, 3)
  for (const path of pictures) assert.ok(existsSync(new URL('../src' + path, import.meta.url)))
  for (const url of [
    'https://gaj.wuhan.gov.cn/jmzx/jwdt/202611/t20261104_2873716.html',
    'https://gaj.wuhan.gov.cn/jmzx/jqfb/202611/t20261104_2873718.html',
    'https://gaj.taizhou.gov.cn/jwzx/aqff/art/2026/art_a78c42fb75634cbfb998d998225ec0d9.html'
  ]) assert.ok(isOfficialNewsUrl(url))
  for (const url of [
    'http://gaj.wuhan.gov.cn/news/123.html',
    'https://gaj.wuhan.gov.cn.attacker.test/news/123.html',
    'https://gaj.wuhan.gov.cn@attacker.test/news/123.html',
    'https://gaj.wuhan.gov.cn/news/../123.html'
  ]) assert.equal(isOfficialNewsUrl(url), false)
})

test('profile detail uses the web labels, active scenario, and legacy money formatting', () => {
  const profile = { identity: '身份原文', description: '描述原文', contextTime: '昨天20:00', contextPlace: '家中', currentState: '状态原文' }
  assert.deepEqual(profileDetailRows({ ...profile, fraudExperience: 'HIGH', speechSkill: 'MEDIUM', manipulationSkill: 'LOW', behaviorTraits: '行为原文' }), [
    ['身份', '身份原文'], ['人物描述', '描述原文'], ['诈骗经验', '高'], ['话术能力', '中'], ['心理操控能力', '低'],
    ['行为特点', '行为原文'], ['时间', '昨天20:00'], ['地点', '家中'], ['当前状态', '状态原文']
  ])
  const victim = {
    ...profile, profileSchemaVersion: 2, availableFundsYuan: 0, reservedFundsYuan: 20,
    scenarioProfiles: [{ scenarioCode: 'TASK_REBATE', needs: '其他场景' }, { scenarioCode: 'FAKE_INVESTMENT', needs: '当前场景', initialTrustMode: '原文' }]
  }
  const rows = profileDetailRows(victim, 'FAKE_INVESTMENT')
  assert.deepEqual(rows.map(([label]) => label), [
    '身份', '人物描述', '防骗意识', '风险倾向', '性格特点', '家庭生活', '收入', '每月支出', '初始存款', '可立即动用资金',
    '预留合计', '普通预留', '应急预留', '预留资金安排', '一般决策特点', '场景需求', '相关经验', '判断依据', '核实习惯',
    '行动边界', '停止条件', '近期事件', '咨询或交易经历', '共同经历', '接触背景', '初始信任方式', '时间', '地点', '当前状态'
  ])
  const detail = Object.fromEntries(rows)
  assert.equal(detail['可立即动用资金'], '0元')
  assert.equal(detail['预留合计'], '20元')
  assert.equal(detail['普通预留'], '未记录')
  assert.equal(detail['场景需求'], '当前场景')
  assert.equal(detail['初始信任方式'], '原文')
  const legacy = Object.fromEntries(profileDetailRows({ ...victim, profileSchemaVersion: null }, 'IMPERSONATION_LOAN'))
  assert.equal(legacy['预留合计'], '未记录')
  assert.equal(legacy['场景需求'], undefined)
})

test('the stage keeps a sole action visible and switches between two actions with the speaker', () => {
  const scammer = { speaker: 'SCAMMER', actionText: '发送研报' }
  const victim = { speaker: 'VICTIM', actionText: '核实信息' }
  assert.equal(stageAction([], 'SCAMMER'), undefined)
  assert.equal(stageAction([scammer, { ...victim, presentationStatus: 'PENDING' }], 'SCAMMER'), scammer)
  assert.equal(stageAction([scammer, { ...victim, actionText: ' ' }], 'VICTIM'), scammer)
  assert.equal(stageAction([scammer, victim], 'SCAMMER'), scammer)
  assert.equal(stageAction([scammer, victim], 'VICTIM'), victim)
  assert.equal(stageAction([scammer, victim]), undefined)
  assert.ok(panelTextSize('你好') > panelTextSize('你'.repeat(100)))
  assert.ok(panelTextSize('你好') > panelTextSize('你好', true))
})

test('history excludes a prepared fifth round and keeps a round that began before interruption', () => {
  const recorded = [1, 2, 3, 4].map(roundNo => ({ roundNo, messages: [], analysis: { riskScore: 50 + roundNo } }))
  const empty = { roundNo: 5, messages: [], analysis: null }
  assert.deepEqual(playedRounds([...recorded, empty]), recorded)
  const draft = { ...empty, messages: [{ presentationStatus: 'PENDING' }] }
  assert.deepEqual(playedRounds([...recorded, draft]), recorded)
  const interrupted = { ...empty, messages: [{ presentationStatus: 'PRESENTING' }] }
  assert.deepEqual(playedRounds([...recorded, interrupted]), [...recorded, interrupted])
})

test('risk trends plot saved scores on a 25-point scale with the established colors', () => {
  assert.deepEqual([0, 25, 50, 75, 100].map(riskColor), ['#3f8f6f', '#3978b8', '#d9a23a', '#d9733a', '#c43d33'])
  assert.deepEqual([55, 59, 64, 69].map(riskColor), ['#d9a23a', '#d9a23a', '#d9733a', '#d9733a'])
  const { points, segments, width } = trendPlot([
    { roundNo: 1, score: 0 }, { roundNo: 2, score: null }, { roundNo: 3, score: 100 }
  ], 300)
  assert.equal(width, 300)
  assert.deepEqual(points.map(({ roundNo, y }) => [roundNo, y]), [[1, trendY(0)], [3, trendY(100)]])
  assert.equal(segments.length, 1)
  assert.ok(segments[0].angle < 0)
  assert.equal(trendY(50) - trendY(75), trendY(25) - trendY(50))
  assert.equal(trendPlot([{ roundNo: 3, score: 59 }], 300).points[0].x, 150)
  const long = trendPlot(Array.from({ length: 20 }, (_, index) => ({ roundNo: index + 1, score: 50 })), 300)
  assert.ok(long.width > 300)
  assert.equal(long.points[1].x - long.points[0].x, 54)
})
