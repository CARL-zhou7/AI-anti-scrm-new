import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import ReportPage from '../views/ReportView.vue'
import RiskChart from '../components/report/RiskChart.vue'
import { reportApi } from '../api/report'
import { simulationApi } from '../api/simulation'
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


it('renders report v2 paragraphs, nodes, advice and authoritative funds without v1 sections', async () => {
  const value: ReportView = { ...report(), contentSchemaVersion: 2, content: { contentSchemaVersion: 2,
    result: { text: '已受骗：转账后停止', reason: '有实际付款' }, summary: '接触背景\n\n诈骗推进\n\n关键判断\n\n最终结果',
    keyNodes: [{ roundNo: 1, quote: '已经付款', analysis: '发生实际转账' }],
    preventionAdvice: ['独立核实', '拒绝陌生转账'], fundsSummary: { totalPaidYuan: 100, totalRefundedYuan: 50, actualLossYuan: 50 } } }
  vi.spyOn(reportApi, 'view').mockResolvedValue(value)
  const wrapper = render(); await flushPromises()
  expect(wrapper.get('[data-testid=report-summary]').findAll('p')).toHaveLength(4)
  for (const text of ['已受骗：转账后停止', '已经付款', '独立核实', '累计支付', '实际返款', '实际亏损']) expect(wrapper.text()).toContain(text)
  expect(wrapper.get('[data-testid=report-funds]').text()).toContain('100 元')
  expect(wrapper.find('[data-testid=report-round-analyses]').exists()).toBe(false)
  expect(wrapper.find('[data-testid=report-speech-analysis]').exists()).toBe(false)
  expect(wrapper.find('[data-testid=report-retry]').exists()).toBe(false)
  wrapper.unmount()
})
