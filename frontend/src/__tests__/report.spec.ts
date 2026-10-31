import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import ReportPage from '../views/ReportView.vue'
import RiskChart from '../components/report/RiskChart.vue'
import { reportApi } from '../api/report'
import { simulationApi } from '../api/simulation'
import { historyApi } from '../api/history'
import { ApiError } from '../api/http'
import type { ReportView } from '../api/types'

vi.mock('vue-router', async original => ({ ...(await original<typeof import('vue-router')>()), useRoute: () => ({ params: { id: '1' } }) }))

function report(status: 'PENDING' | 'SUCCEEDED' | 'FAILED' = 'SUCCEEDED'): ReportView {
  return {
    sessionId: '1', simulationStatus: 'FINISHED', unavailableReason: null,
    outcome: { code: 'NOT_DEFRAUDED', displayText: '未受骗结束', reason: '核实后终止联系', limitReason: null },
    report: { status, displayText: status === 'PENDING' ? '生成中' : status === 'FAILED' ? '生成失败' : '已完成',
      canRetry: status === 'FAILED', attemptNo: 1, updatedAt: '', error: status === 'FAILED' ? { code: 'TIMEOUT', message: '报告生成超时，可手动重试。' } : null },
    content: { summary: '服务端过程总结', roundAnalyses: [{ roundNo: 1, analysis: '服务端逐轮分析' }],
      speechAnalysis: '服务端话术分析', victimBehaviorAnalysis: '服务端行为分析', outcomeAnalysis: '服务端结局分析' },
    riskSeries: [{ roundNo: 1, score: 25 }, { roundNo: 3, score: 75 }],
  }
}
beforeEach(() => {
  vi.useFakeTimers()
  vi.spyOn(simulationApi, 'state').mockRejectedValue(new Error('optional header unavailable'))
})
afterEach(() => { vi.restoreAllMocks(); vi.useRealTimers() })
const render = () => mount(ReportPage, { global: { stubs: { RouterLink: { template: '<a><slot /></a>' } } } })

it('polls pending reports, renders saved sections and stops at success or unmount', async () => {
  const api = vi.spyOn(reportApi, 'view').mockResolvedValueOnce(report('PENDING')).mockResolvedValue(report())
  const wrapper = render()
  await flushPromises()
  expect(wrapper.find('[data-testid=report-generating]').exists()).toBe(true)
  expect(wrapper.find('[data-testid=report-summary]').exists()).toBe(false)
  await vi.advanceTimersByTimeAsync(2000); await flushPromises()
  for (const text of ['服务端过程总结', '服务端逐轮分析', '服务端话术分析', '服务端行为分析', '服务端结局分析', '未受骗结束', '核实后终止联系']) expect(wrapper.text()).toContain(text)
  expect(wrapper.find('[data-testid=report-retry]').exists()).toBe(false)
  await vi.advanceTimersByTimeAsync(6000)
  expect(api).toHaveBeenCalledTimes(2)
  wrapper.unmount()
  api.mockResolvedValue(report('PENDING'))
  const pending = render(); await flushPromises(); pending.unmount()
  await vi.advanceTimersByTimeAsync(4000)
  expect(api).toHaveBeenCalledTimes(3)
})

it('hides failed content and guards retry until the request completes', async () => {
  vi.spyOn(reportApi, 'view').mockResolvedValue(report('FAILED'))
  let resolve!: (value: ReportView) => void
  const retry = vi.spyOn(reportApi, 'retry').mockImplementation(() => new Promise(done => { resolve = done }))
  const wrapper = render(); await flushPromises()
  expect(wrapper.text()).toContain('报告生成超时，可手动重试。')
  expect(wrapper.find('[data-testid=report-summary]').exists()).toBe(false)
  await wrapper.get('[data-testid=report-retry]').trigger('click')
  expect(wrapper.get('[data-testid=report-retry]').attributes('disabled')).toBeDefined()
  await wrapper.get('[data-testid=report-retry]').trigger('click')
  expect(retry).toHaveBeenCalledTimes(1)
  resolve(report('PENDING')); await flushPromises()
  expect(wrapper.find('[data-testid=report-generating]').exists()).toBe(true)
  wrapper.unmount()
})

it('shows interrupted, running and inaccessible notices without a report or retry', async () => {
  const api = vi.spyOn(reportApi, 'view')
  for (const status of ['RUNNING', 'INTERRUPTED'] as const) {
    api.mockResolvedValue({ sessionId: '1', simulationStatus: status, report: null, content: null, outcome: null,
      riskSeries: [], unavailableReason: status === 'RUNNING' ? 'SIMULATION_RUNNING' : 'SIMULATION_INTERRUPTED' })
    const wrapper = render(); await flushPromises()
    expect(wrapper.text()).toContain(status === 'RUNNING' ? '演绎仍在进行中' : '不生成正式复盘')
    expect(wrapper.find('[data-testid=report-retry]').exists()).toBe(false)
    expect(wrapper.find('[data-testid=report-summary]').exists()).toBe(false)
    wrapper.unmount()
  }
  api.mockRejectedValue(new ApiError(404, 'NOT_FOUND', '资源不存在或不可访问'))
  const wrapper = render(); await flushPromises()
  expect(wrapper.get('[data-testid=report-load-error]').text()).toContain('记录不存在或不可访问')
  wrapper.unmount()
})

it('plots only stored points, handles a single point and displays missing scores as empty', () => {
  const chart = mount(RiskChart, { props: { points: report().riskSeries } })
  const points = chart.findAll('[data-testid=report-risk-point]')
  expect(points.map(p => p.attributes('aria-label'))).toEqual(['第1轮 25分', '第3轮 75分'])
  expect(points.map(p => p.attributes('cy'))).toEqual(['180', '80'])
  chart.unmount()
  const single = mount(RiskChart, { props: { points: [{ roundNo: 3, score: 0 }] } })
  expect(single.find('polyline').exists()).toBe(false)
  expect(single.get('circle').attributes('aria-label')).toBe('第3轮 0分')
  single.unmount()
  const empty = mount(RiskChart, { props: { points: [] } })
  expect(empty.find('[data-testid=report-risk-empty]').exists()).toBe(true)
  empty.unmount()
})


it('renders new and historical outcome names exactly as saved', async () => {
  for (const [code, displayText] of [['DEFRAUDED', '已受骗'], ['IDENTIFIED', '已识破'], ['LIMIT_REACHED', '超限结束'], ['NOT_DEFRAUDED', '未受骗结束'], ['UNDETERMINED', '结局未定']] as const) {
    const value = report()
    value.outcome = { code, displayText, reason: '已保存依据', limitReason: null }
    vi.spyOn(reportApi, 'view').mockResolvedValue(value)
    const wrapper = render(); await flushPromises()
    expect(wrapper.text()).toContain(displayText)
    wrapper.unmount()
  }
})


it('shows the V1.4 verdict and final score from the saved session', async () => {
  const value = report()
  value.outcome = { code: 'SCAMMER_WIN', displayText: '骗子胜', reason: '第15轮结束判定',
    limitReason: null, winning: 'SCAMMER', finalTrustScore: 82 }
  value.contentSchemaVersion = 4
  vi.spyOn(reportApi, 'view').mockResolvedValue(value)
  const wrapper = render(); await flushPromises()
  expect(wrapper.get('[data-testid=report-outcome]').text()).toBe('骗子胜')
  const verdict = wrapper.get('[data-testid=report-verdict]')
  expect(verdict.text()).toContain('82')
  expect(verdict.text()).toContain('骗子胜')
  wrapper.unmount()

  // A user exit has no verdict and no final score.
  const exited = report()
  exited.simulationStatus = 'INTERRUPTED'
  exited.outcome = null
  exited.ending = { code: 'USER_EXIT', displayText: '用户离开', reason: '未形成胜负结论' }
  vi.spyOn(reportApi, 'view').mockResolvedValue(exited)
  const second = render(); await flushPromises()
  expect(second.find('[data-testid=report-verdict]').exists()).toBe(false)
  expect(second.get('[data-testid=report-ending]').text()).toBe('用户离开')
  second.unmount()
})


it('renders report v2 paragraphs, nodes, advice and authoritative funds without v1 sections', async () => {
  const value: ReportView = { ...report(), contentSchemaVersion: 2, content: { contentSchemaVersion: 2,
    result: { text: '已受骗：转账后停止', reason: '有实际付款' }, summary: '接触背景\n\n诈骗推进\n\n关键判断\n\n最终结果',
    keyNodes: [{ roundNo: 1, quote: '已经付款', analysis: '发生实际转账' }],
    preventionAdvice: ['独立核实', '拒绝陌生转账'], fundsSummary: { totalPaidYuan: 100, totalRefundedYuan: 50, actualLossYuan: 50 } } }
  vi.spyOn(reportApi, 'view').mockResolvedValue(value)
  const wrapper = render(); await flushPromises()
  expect(wrapper.get('[data-testid=report-summary]').findAll('p')).toHaveLength(4)
  for (const text of ['已经付款', '独立核实', '累计支付', '本金返款', '最终净损失']) expect(wrapper.text()).toContain(text)
  expect(wrapper.get('[data-testid=report-funds]').text()).toContain('100 元')
  expect(wrapper.find('[data-testid=report-round-analyses]').exists()).toBe(false)
  expect(wrapper.find('[data-testid=report-speech-analysis]').exists()).toBe(false)
  expect(wrapper.get('[data-testid=report-key-nodes] article').classes()).toContain('risk-level-25')
  expect(wrapper.get('[data-testid=report-node-trust]').text()).toBe('信任数据未保存')
  expect(wrapper.find('[data-testid=report-retry]').exists()).toBe(false)
  wrapper.unmount()
})


it('uses the shared risk colors and hides visible score labels in charts', () => {
  const wrapper = mount(RiskChart, { props: { points: [0,25,50,75,100].map((score,index) => ({ roundNo:index+1,score })) } })
  expect(wrapper.findAll('[data-testid=report-risk-point]').map(point => point.attributes('fill')))
    .toEqual(['#3f8f6f','#3978b8','#d9a23a','#d9733a','#c43d33'])
  expect(wrapper.find('.risk-values').exists()).toBe(false)
  expect(wrapper.text()).not.toContain('25分')
  wrapper.unmount()
})


it('keeps a normal business ending report available without a formal outcome', async () => {
  vi.spyOn(reportApi, 'view').mockResolvedValue({ ...report('PENDING'), simulationStatus: 'INTERRUPTED', outcome: null,
    ending: { code: 'USER_EXIT', displayText: '用户离开', reason: '已离开页面；未形成受骗或识破结论。' } })
  const wrapper = render(); await flushPromises()
  expect(wrapper.get('[data-testid=report-ending]').text()).toBe('用户离开')
  expect(wrapper.text()).toContain('未形成受骗或识破结论')
  expect(wrapper.find('[data-testid=report-generating]').exists()).toBe(true)
  expect(wrapper.text()).not.toContain('不生成正式复盘')
  wrapper.unmount()
})


it.each([3, 4] as const)('renders interrupted v%s reports including their summary and money facts', async schema => {
  const value: ReportView = { ...report(), simulationStatus: 'INTERRUPTED', contentSchemaVersion: schema, outcome: null,
    ending: { code: 'USER_EXIT', displayText: '用户离开', reason: '未形成受骗或识破结论' },
    content: { contentSchemaVersion: schema, winning: null, finalTrustScore: null, result: { text: '未形成受骗或识破结论', reason: '用户离开' }, summary: '已保存过程', keyNodes: [], preventionAdvice: ['独立核实', '谨慎付款'],
      fundsSummary: { totalPaidYuan: 100, totalRefundedYuan: 10, totalRewardedYuan: 50, actualLossYuan: 40, availableBalanceYuan: 960, generalReserveRemainingYuan: 1000, emergencyReserveRemainingYuan: 500 } } }
  vi.spyOn(reportApi, 'view').mockResolvedValue(value)
  const wrapper = render(); await flushPromises()
  const facts = wrapper.get('[data-testid=report-funds]').text()
  for (const text of ['累计支付', '本金返款', '独立报酬', '最终净损失', '可用余额', '普通预留剩余', '应急预留剩余']) expect(facts).toContain(text)
  expect(wrapper.get('[data-testid=report-ending]').text()).toBe('用户离开')
  expect(wrapper.get('[data-testid=report-summary]').text()).toContain('已保存过程')
  wrapper.unmount()
})


it('shows only report key nodes with their saved trust changes', async () => {
  const data: ReportView = { ...report(), contentSchemaVersion: 2, content: { contentSchemaVersion: 2,
    result: { text: '未受骗结束', reason: '已停止' }, summary: '总结',
    keyNodes: [{ roundNo: 3, quote: '本轮解释', analysis: '解释降低了疑虑' },
      { roundNo: 13, quote: '拒绝转账', analysis: '保持警惕' }], preventionAdvice: [],
    fundsSummary: { totalPaidYuan: 0, totalRefundedYuan: 0, actualLossYuan: 0 } },
    riskSeries: [
      { roundNo: 1, score: 25, trustChange: { before: 65, after: 65, delta: 0 } },
      { roundNo: 3, score: 25, trustChange: { before: 65, after: 70, delta: 5 } },
      { roundNo: 9, score: 50, trustChange: { before: 75, after: 71, delta: -4 } },
      { roundNo: 13, score: 25, trustChange: { before: 71, after: 71, delta: 0 } },
    ] }
  vi.spyOn(reportApi, 'view').mockResolvedValue(data)
  const detail = vi.spyOn(historyApi, 'detail').mockResolvedValue({ rounds: [{ roundNo: 9,
    messages: [{ speaker: 'SCAMMER', content: '先交钱才能给单据' }], analysis: { speechAnalysis: '改变了先前的说法' } }] } as unknown as Awaited<ReturnType<typeof historyApi.detail>>)
  const wrapper = render()
  await flushPromises()
  expect(wrapper.find('[data-testid=report-trust-rounds]').exists()).toBe(false)
  const nodes = wrapper.get('[data-testid=report-key-nodes]').findAll('article')
  expect(nodes).toHaveLength(2)
  expect(nodes[0]!.get('[data-testid=report-node-trust]').text()).toContain('信任 65 → 70　↑ +5 上升')
  expect(nodes[1]!.text()).toContain('保持警惕')
  expect(nodes[1]!.get('[data-testid=report-node-trust]').text()).toContain('信任 71 → 71　→ 0 不变')
  expect(nodes.map(node => node.get('h3').text())).toEqual([
    '事件标题未保存', '事件标题未保存',
  ])
  expect(detail).not.toHaveBeenCalled()
  expect(wrapper.text()).not.toContain('本轮 0 分')
  wrapper.unmount()
})


it.each([
  [0, 0, 0, 0, '本次没有实际收付。'],
  [0, 0, 50, 0, '另获独立报酬50元。'],
  [2000, 0, 0, 2000, '本次累计支付2,000元。'],
  [2000, 100, 0, 1900, '本次累计支付2,000元，收回本金100元。'],
  [2000, 2000, 0, 0, '本次累计支付2,000元，收回本金2,000元。'],
  [2000, 100, 50, 1850, '本次累计支付2,000元，收回本金100元，另获独立报酬50元。'],
])('describes saved funds %s/%s/%s without inventing a verdict', async (paid, refunded, rewarded, loss, sentence) => {
  const value: ReportView = { ...report(), contentSchemaVersion: 3, content: {
    contentSchemaVersion: 3, result: { text: '未受骗结束', reason: '保存结果' }, summary: '短概述',
    keyNodes: [{ roundNo: 3, title: '对方要求追加付款', quote: '本轮原话', analysis: '实际行为的简短解释' }],
    preventionAdvice: ['独立核实', '谨慎付款'], fundsSummary: { totalPaidYuan: paid, totalRefundedYuan: refunded,
      totalRewardedYuan: rewarded, actualLossYuan: loss, availableBalanceYuan: 960,
      generalReserveRemainingYuan: 100, emergencyReserveRemainingYuan: 50 } } }
  value.riskSeries = [{ roundNo: 3, score: 25, trustChange: { before: 72, after: 67, delta: -5 } }]
  vi.spyOn(reportApi, 'view').mockResolvedValue(value)
  const wrapper = render(); await flushPromises()
  expect(wrapper.get('[data-testid=funds-sentence]').text()).toBe(sentence.replace(/。$/, '，'))
  expect(wrapper.get('.loss strong').text()).toBe(`${loss.toLocaleString('zh-CN')} 元`)
  expect(wrapper.get('[data-testid=report-node-trust]').classes()).toContain('down')
  expect(wrapper.get('[data-testid=report-node-trust]').text()).toContain('↓ −5 下降')
  expect(wrapper.get('article h3').text()).toBe('对方要求追加付款')
  expect(wrapper.find('[data-testid=report-result]').exists()).toBe(false)
  expect(wrapper.get('details summary').text()).toBe('资金明细')
  wrapper.unmount()
})
