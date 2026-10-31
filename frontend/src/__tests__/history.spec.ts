import { afterEach, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import HistoryView from '../views/HistoryView.vue'
import HistoryDetailView from '../views/HistoryDetailView.vue'
import RiskChart from '../components/report/RiskChart.vue'
import { historyApi } from '../api/history'
import { ApiError } from '../api/http'
import type { HistoryDetail, HistoryItem, ReportSummary } from '../api/types'

vi.mock('vue-router', async original => ({ ...(await original<typeof import('vue-router')>()), useRoute: () => ({ params: { id: '1' } }) }))
const global = { stubs: { RouterLink: { props: ['to'], template: '<a :href="to"><slot /></a>' } } }
afterEach(() => vi.restoreAllMocks())

it('shows only the frozen selected scenario in historical victim details', async () => {
  const data = detail()
  data.configuration.victim.profileSchemaVersion = 2
  data.configuration.victim.scenarioProfiles = ['TASK_REBATE', 'FAKE_INVESTMENT', 'IMPERSONATION_LOAN'].map(code => ({
    scenarioCode: code as 'TASK_REBATE' | 'FAKE_INVESTMENT' | 'IMPERSONATION_LOAN', needs: `冻结需求-${code}`,
    relevantExperience: '经历', judgmentBasis: '依据', verificationHabits: '核实', actionBoundaries: '边界',
    stopConditions: '停止', recentEvents: '事件', consultationsTransactions: '咨询', sharedExperiences: '共同经历',
    contactBackground: '背景', initialTrustMode: '信任',
  }))
  vi.spyOn(historyApi, 'detail').mockResolvedValue(data)
  const view = mount(HistoryDetailView, { global }); await flushPromises()
  expect(view.text()).toContain('冻结需求-TASK_REBATE')
  expect(view.text()).not.toContain('冻结需求-FAKE_INVESTMENT')
  expect(view.text()).not.toContain('冻结需求-IMPERSONATION_LOAN')
  view.unmount()
})

it('distinguishes unrecorded legacy money from recorded zero and displays round summaries', async () => {
  const api = vi.spyOn(historyApi, 'detail')
  for (const recorded of [false, true]) {
    const data = detail()
    data.rulesetVersion = 'PRD-V3.2'
    data.funds = { fundsRecorded: recorded, initialAvailableYuan: recorded ? 10000 : null,
      availableBalanceYuan: recorded ? 10000 : null, totalPaidYuan: recorded ? 0 : null,
      totalRefundedYuan: recorded ? 0 : null, actualLossYuan: recorded ? 0 : null,
      additionalPaymentCount: recorded ? 0 : null, reserveReleasedYuan: recorded ? 0 : null, flows: [] }
    data.rounds[0]!.funds = recorded ? data.funds : null
    api.mockResolvedValue(data)
    const view = mount(HistoryDetailView, { global }); await flushPromises()
    const funds = view.get('[data-testid=history-funds]')
    if (recorded) {
      expect(funds.text()).toContain('10000元')
      expect(funds.text()).toContain('0元')
      expect(funds.text()).not.toContain('未记录')
      expect(view.get('[data-testid=history-round-funds]').text()).toContain('10000元')
    } else {
      expect(funds.text()).toContain('未记录')
      expect(funds.text()).not.toContain('0元')
      expect(view.get('[data-testid=history-round-funds]').text()).toContain('未记录')
    }
    view.unmount()
  }
})

function item(id = '1'): HistoryItem {
  return { sessionId: id, startedAt: '2026-10-19T08:00:00.000Z', scenarioName: '冻结场景', scammerName: '当时骗子', victimName: '当时受害者',
    status: 'FINISHED', report: summary('SUCCEEDED'), outcome: null, interruption: null }
}
function summary(status: ReportSummary['status']): ReportSummary {
  return { status, displayText: status === 'SUCCEEDED' ? '已完成' : status === 'FAILED' ? '生成失败' : '生成中',
    canRetry: status === 'FAILED', attemptNo: 1, updatedAt: '', error: null }
}
function detail(): HistoryDetail {
  const common = { id: '1', age: 30, gender: '男', identity: '当时身份', description: '当时描述', contextTime: '晚上', contextPlace: '家中', currentState: '接听电话',
    avatar: { key: 'a', url: '/avatars/a' }, speakingAvatar: { key: 'b', url: '/avatars/b' } }
  return { rulesetVersion: 'PRD-V3.2', funds: { fundsRecorded: false, initialAvailableYuan: null, availableBalanceYuan: null, totalPaidYuan: null, totalRefundedYuan: null, actualLossYuan: null, additionalPaymentCount: null, reserveReleasedYuan: null, flows: [] }, sessionId: '1', status: 'FINISHED', startedAt: item().startedAt, deadlineAt: '2026-10-19T08:10:00.000Z', endedAt: '2026-10-19T08:05:00.000Z',
    configuration: {
      scenario: { id: '1', code: 'TASK_REBATE', name: '冻结场景', description: '当时场景简介', focus: '青年群体' },
      scammer: { ...common, name: '当时骗子', fraudExperience: 'HIGH', speechSkill: 'MEDIUM', manipulationSkill: 'LOW', behaviorTraits: '催促操作' },
      victim: { ...common, name: '当时受害者', antiFraudAwareness: 'HIGH', riskTendency: 'LOW', personalityTraits: '谨慎核实' },
    },
    rounds: [1, 2, 3].map(roundNo => ({ funds: null, roundNo, phase: 'COMPLETE', interactionComplete: roundNo !== 2, presentationComplete: roundNo !== 2,
      messages: (roundNo === 2 ? ['SCAMMER'] as const : ['SCAMMER', 'VICTIM'] as const).map(speaker => ({
        id: `${roundNo}-${speaker}`, roundNo, speaker, content: '保存的台词', actionText: '发送链接', presentationStatus: 'COMPLETED', presentationCompletedAt: null, presentationErrorCode: null, savedAt: item().startedAt })),
      analysis: roundNo === 2 ? null : { fraudStage: '诱导操作', speechAnalysis: '紧迫话术', methodAnalysis: '权威伪装', victimPsychology: '仍有疑虑', riskScore: roundNo === 1 ? 25 : 75, riskReason: '逐步增加投入', savedAt: item().startedAt, presentedAt: null }, readingUntil: null })),
    outcome: { code: 'NOT_DEFRAUDED', displayText: '未受骗结束', reason: '核实后停止联系', limitReason: null }, interruption: null, report: summary('SUCCEEDED') }
}

it('hides the per-round score line for V1.4 history but keeps the full analysis and the final score', async () => {
  const v35 = detail()
  v35.rulesetVersion = 'PRD-V3.5'
  v35.rounds[0]!.analysis = { ...v35.rounds[0]!.analysis!, trustChange: { before: 50, after: 62, delta: 12 },
    methodShort: '冒充亲友', intentShort: '借亲属身份让受害人放下戒心' }
  v35.outcome = { code: 'SCAMMER_WIN', displayText: '骗子胜', reason: '第15轮结束时判定', limitReason: null,
    winning: 'SCAMMER', finalTrustScore: 62 }
  const api = vi.spyOn(historyApi, 'detail').mockResolvedValue(v35)
  const view = mount(HistoryDetailView, { global }); await flushPromises()
  expect(view.find('[data-testid=history-round-trust]').exists()).toBe(false)
  expect(view.text()).toContain('紧迫话术')
  expect(view.text()).toContain('权威伪装')
  expect(view.get('[data-testid=history-final-trust]').text()).toContain('62')
  view.unmount()

  // An older run keeps whatever it recorded.
  const legacy = detail()
  legacy.rulesetVersion = 'PRD-V3.4'
  legacy.rounds[0]!.analysis = { ...legacy.rounds[0]!.analysis!, trustChange: { before: 50, after: 62, delta: 12 } }
  api.mockResolvedValue(legacy)
  const old = mount(HistoryDetailView, { global }); await flushPromises()
  expect(old.get('[data-testid=history-round-trust]').text()).toContain('50 → 62')
  expect(old.find('[data-testid=history-final-trust]').exists()).toBe(false)
  old.unmount()
})

it('lists snapshots in Shanghai time, paginates with deduplication and stops at the last page', async () => {
  const api = vi.spyOn(historyApi, 'list').mockResolvedValueOnce({ items: [item()], nextCursor: 'c1', hasMore: true })
    .mockResolvedValueOnce({ items: [item(), item('2')], nextCursor: null, hasMore: false })
  const view = mount(HistoryView, { global }); await flushPromises()
  expect(view.get('[data-testid=history-item-started]').text()).toContain('16:00:00')
  for (const text of ['冻结场景', '当时骗子', '当时受害者', '已结束', '已完成']) expect(view.text()).toContain(text)
  expect(view.get('[data-testid=history-item]').attributes('href')).toBe('/history/1')
  await view.get('[data-testid=history-more]').trigger('click'); await flushPromises()
  expect(api).toHaveBeenLastCalledWith({ cursor: 'c1' })
  expect(view.findAll('[data-testid=history-item]')).toHaveLength(2)
  expect(view.find('[data-testid=history-more]').exists()).toBe(false)
  view.unmount()
})

it('shows empty only after success and offers retry after failure', async () => {
  vi.spyOn(historyApi, 'list').mockRejectedValueOnce(new Error('读取失败')).mockResolvedValue({ items: [], nextCursor: null, hasMore: false })
  const view = mount(HistoryView, { global }); await flushPromises()
  expect(view.get('[data-testid=history-error]').text()).toContain('读取失败')
  expect(view.find('[data-testid=history-empty]').exists()).toBe(false)
  await view.get('[data-testid=history-retry]').trigger('click'); await flushPromises()
  expect(view.get('[data-testid=history-empty]').text()).toContain('本人暂无记录')
  view.unmount()
})

it('keeps all report states truthful, including finished records missing a report', async () => {
  const records: HistoryItem[] = ['SUCCEEDED', 'FAILED', 'PENDING', 'RUNNING'].map((status, i) => ({ ...item(String(i)), report: summary(status as ReportSummary['status']) }))
  records.push({ ...item('4'), report: null }, { ...item('5'), status: 'INTERRUPTED', report: null }, { ...item('6'), status: 'RUNNING', report: null })
  vi.spyOn(historyApi, 'list').mockResolvedValue({ items: records, nextCursor: null, hasMore: false })
  const view = mount(HistoryView, { global }); await flushPromises()
  expect(view.findAll('[data-testid=history-item-report]').map(r => r.text())).toEqual([
    '复盘报告：已完成', '复盘报告：生成失败', '复盘报告：生成中', '复盘报告：生成中', '复盘报告：报告状态异常', '复盘报告：不生成正式复盘', '复盘报告：—',
  ])
  view.unmount()
})

it('renders frozen profiles, separate action text, a half round and only stored risk points', async () => {
  const api = vi.spyOn(historyApi, 'detail').mockResolvedValue(detail())
  const view = mount(HistoryDetailView, { global }); await flushPromises()
  for (const text of ['当时场景简介', '青年群体', '当时骗子', '当时受害者', '当时身份', '催促操作', '谨慎核实', '保存的台词', '动作说明：发送链接', '未受骗结束']) expect(view.text()).toContain(text)
  const incomplete = view.findAll('[data-testid=history-round]')[1]!
  expect(incomplete.text()).toContain('本轮未完成')
  expect(incomplete.find('[data-testid=history-round-analysis]').exists()).toBe(false)
  expect(view.findComponent(RiskChart).props('points')).toEqual([{ roundNo: 1, score: 25 }, { roundNo: 3, score: 75 }])
  expect(view.get('[data-testid=history-report-link]').attributes('href')).toBe('/simulations/1/report')
  expect(view.find('audio, video').exists()).toBe(false)
  for (const word of ['删除', '导出', '分享', '下载', '继续演绎']) expect(view.text()).not.toContain(word)
  expect(api).toHaveBeenCalledOnce()
  view.unmount()
})

it('offers report links only for success or failure and uses one safe inaccessible message', async () => {
  const api = vi.spyOn(historyApi, 'detail')
  for (const status of ['SUCCEEDED', 'FAILED', 'PENDING', 'RUNNING', null, 'INTERRUPTED'] as const) {
    const data = detail()
    data.report = status && status !== 'INTERRUPTED' ? summary(status) : null
    if (status === 'INTERRUPTED') { data.status = status; data.outcome = null; data.interruption = { code: 'USER_EXIT', message: '用户主动退出' } }
    api.mockResolvedValue(data)
    const view = mount(HistoryDetailView, { global }); await flushPromises()
    expect(view.find('[data-testid=history-report-link]').exists()).toBe(status === 'SUCCEEDED')
    expect(view.find('[data-testid=history-report-retry-link]').exists()).toBe(status === 'FAILED')
    if (status === null) expect(view.text()).toContain('报告状态异常')
    if (status === 'INTERRUPTED') { expect(view.text()).toContain('不生成正式复盘'); expect(view.text()).toContain('用户主动退出') }
    view.unmount()
  }
  api.mockRejectedValue(new ApiError(404, 'NOT_FOUND', '资源不存在或不可访问'))
  const view = mount(HistoryDetailView, { global }); await flushPromises()
  expect(view.get('[data-testid=history-detail-error]').text()).toContain('记录不存在或不可访问')
  view.unmount()
})


it('keeps rounds in a scroll region with all five colors, gray only for unscored, and no score badges', async () => {
  const data = detail()
  const sample = data.rounds[0]!
  data.rounds = [100, 75, 50, 25, 0, null].map((score, index) => ({ ...sample, roundNo: index + 1,
    analysis: score === null ? null : { ...sample.analysis!, riskScore: score as 0 | 25 | 50 | 75 | 100 } }))
  vi.spyOn(historyApi, 'detail').mockResolvedValue(data)
  const wrapper = mount(HistoryDetailView, { global }); await flushPromises()
  const region = wrapper.get('[data-testid=history-rounds-scroll]')
  expect(region.attributes('tabindex')).toBe('0')
  const rounds = region.findAll('[data-testid=history-round]')
  expect(rounds.map(round => round.classes().find(c => c.startsWith('risk-level-')))).toEqual(['risk-level-100','risk-level-75','risk-level-50','risk-level-25','risk-level-0','risk-level-unscored'])
  expect(rounds[0]!.get('h3').text()).toBe('第1轮')
  expect(rounds.slice(0,5).every(round => !round.text().includes('未评分') && !round.text().includes('风险分'))).toBe(true)
  expect(rounds[5]!.text()).toContain('未评分')
  wrapper.unmount()
})

it('renders ordered payment, refund and reserve flows with purpose, round and balance', async () => {
  const data = detail()
  data.funds = { fundsRecorded: true, initialAvailableYuan: 1000, availableBalanceYuan: 1050,
    totalPaidYuan: 100, totalRefundedYuan: 50, actualLossYuan: 50, additionalPaymentCount: 0, reserveReleasedYuan: 100,
    flows: [
      { sequenceNo: 1, roundNo: 1, messageId: '11', type: 'PAYMENT', purpose: 'INITIAL_PAYMENT', amountYuan: 100, balanceAfterYuan: 900, reason: null, occurredAt: data.startedAt },
      { sequenceNo: 2, roundNo: 2, messageId: '21', type: 'REFUND', purpose: 'REFUND', amountYuan: 50, balanceAfterYuan: 950, reason: null, occurredAt: data.startedAt },
      { sequenceNo: 3, roundNo: 3, messageId: '31', type: 'RESERVE_RELEASE', purpose: 'RESERVE_RELEASE', amountYuan: 100, balanceAfterYuan: 1050, reason: '调整预留安排', occurredAt: data.startedAt },
    ] }
  vi.spyOn(historyApi, 'detail').mockResolvedValue(data)
  const wrapper = mount(HistoryDetailView, { global }); await flushPromises()
  const rows = wrapper.findAll('[data-testid=history-fund-flow]')
  expect(rows).toHaveLength(3)
  for (const [index, row] of rows.entries()) expect(row.text()).toContain(`第${index+1}轮`)
  expect(rows[0]!.text()).toContain('首笔付款')
  expect(rows[1]!.text()).toContain('实际返款')
  expect(rows[2]!.text()).toContain('预留释放')
  expect(rows[2]!.text()).toContain('1050元')
  expect(rows[2]!.text()).toContain('调整预留安排')
  wrapper.unmount()
})
