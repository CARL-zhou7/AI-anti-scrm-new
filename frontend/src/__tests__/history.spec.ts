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
  return { sessionId: '1', status: 'FINISHED', startedAt: item().startedAt, deadlineAt: '2026-10-19T08:10:00.000Z', endedAt: '2026-10-19T08:05:00.000Z',
    configuration: {
      scenario: { id: '1', code: 'TASK_REBATE', name: '冻结场景', description: '当时场景简介', focus: '青年群体' },
      scammer: { ...common, name: '当时骗子', fraudExperience: 'HIGH', speechSkill: 'MEDIUM', manipulationSkill: 'LOW', behaviorTraits: '催促操作' },
      victim: { ...common, name: '当时受害者', antiFraudAwareness: 'HIGH', riskTendency: 'LOW', personalityTraits: '谨慎核实' },
    },
    rounds: [1, 2, 3].map(roundNo => ({ roundNo, phase: 'COMPLETE', interactionComplete: roundNo !== 2, presentationComplete: roundNo !== 2,
      messages: (roundNo === 2 ? ['SCAMMER'] as const : ['SCAMMER', 'VICTIM'] as const).map(speaker => ({
        id: `${roundNo}-${speaker}`, roundNo, speaker, content: '保存的台词', actionText: '发送链接', presentationStatus: 'COMPLETED', presentationCompletedAt: null, presentationErrorCode: null, savedAt: item().startedAt })),
      analysis: roundNo === 2 ? null : { fraudStage: '诱导操作', speechAnalysis: '紧迫话术', methodAnalysis: '权威伪装', victimPsychology: '仍有疑虑', riskScore: roundNo === 1 ? 25 : 75, riskReason: '逐步增加投入', savedAt: item().startedAt, presentedAt: null }, readingUntil: null })),
    outcome: { code: 'NOT_DEFRAUDED', displayText: '未受骗结束', reason: '核实后停止联系', limitReason: null }, interruption: null, report: summary('SUCCEEDED') }
}

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
