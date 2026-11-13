<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { reportApi } from '../api/report'
import { simulationApi } from '../api/simulation'
import { ApiError } from '../api/http'
import type { ReportView, SimulationState } from '../api/types'
import RiskChart from '../components/report/RiskChart.vue'
import { riskColor, riskClass } from '../utils/risk'

const route = useRoute()
const view = ref<ReportView | null>(null), state = ref<SimulationState | null>(null)
const loading = ref(true), error = ref(''), retryError = ref(''), retrying = ref(false), exhausted = ref(false), notApplicable = ref(false)
const generating = computed(() => view.value?.report?.status === 'PENDING' || view.value?.report?.status === 'RUNNING')
const reportV2 = computed(() => [2, 3, 4].includes(view.value?.contentSchemaVersion ?? 0) && (view.value?.content?.contentSchemaVersion === 2 || view.value?.content?.contentSchemaVersion === 3 || view.value?.content?.contentSchemaVersion === 4) ? view.value.content : null)
const trustPoints = computed(() => (view.value?.riskSeries ?? []).flatMap(point =>
  point.trustChange ? [{ roundNo: point.roundNo, score: point.trustChange.after }] : []))
const isAdmin = computed(() => route.meta.role === 'ADMIN')
const reportV1 = computed(() => view.value?.content && 'roundAnalyses' in view.value.content ? view.value.content : null)
const trustAtRound = (roundNo: number) => {
  const change = view.value?.riskSeries.find(point => point.roundNo === roundNo)?.trustChange
  return change ?? null
}
const keyNodes = computed(() => [...(reportV2.value?.keyNodes ?? [])].sort((a, b) => a.roundNo - b.roundNo))
const money = (amount: number) => amount.toLocaleString('zh-CN')
const fundsSentence = computed(() => {
  const f = reportV2.value?.fundsSummary
  if (!f) return ''
  const parts: string[] = []
  if (f.totalPaidYuan) parts.push(`本次累计支付${money(f.totalPaidYuan)}元`)
  if (f.totalRefundedYuan) parts.push(`收回本金${money(f.totalRefundedYuan)}元`)
  if ('totalRewardedYuan' in f && f.totalRewardedYuan) parts.push(`另获独立报酬${money(f.totalRewardedYuan)}元`)
  return (parts.length ? parts.join('，') : '本次没有实际收付') + '，'
})
const trustTrend = (roundNo: number) => {
  const delta = trustAtRound(roundNo)?.delta
  return delta == null ? 'missing' : delta > 0 ? 'up' : delta < 0 ? 'down' : 'flat'
}
const trustText = (roundNo: number) => {
  const c = trustAtRound(roundNo)
  if (!c) return '信任数据未保存'
  const delta = c.delta > 0 ? `↑ +${c.delta} 上升` : c.delta < 0 ? `↓ −${Math.abs(c.delta)} 下降` : '→ 0 不变'
  return `信任 ${c.before} → ${c.after}　${delta}`
}
const riskAtRound = (roundNo: number) => view.value?.riskSeries.find(point => point.roundNo === roundNo)?.score ?? null
let timer: ReturnType<typeof setTimeout> | undefined
let version = 0, polls = 0

function message(cause: unknown) {
  return cause instanceof ApiError && cause.status === 404 ? '记录不存在或不可访问'
    : cause instanceof Error ? cause.message : '读取报告失败，请稍后重试'
}

function schedule(id: string, current: number) {
  if (!generating.value || view.value?.unavailableReason || current !== version) return
  if (polls >= 60) { exhausted.value = true; return }
  timer = setTimeout(async () => {
    polls++
    try {
      const result = await reportApi.view(id)
      if (current !== version) return
      view.value = result
      schedule(id, current)
    } catch (cause) { if (current === version) error.value = message(cause) }
  }, 2000)
}

async function load() {
  const current = ++version, id = String(route.params.id)
  clearTimeout(timer)
  polls = 0
  view.value = null; state.value = null; error.value = ''; retryError.value = ''
  loading.value = true; retrying.value = false; exhausted.value = false; notApplicable.value = false
  // Header information is optional; a failed state read must not hide an available report.
  void simulationApi.state(id).then(result => { if (current === version) state.value = result }).catch(() => {})
  try {
    const result = await reportApi.view(id)
    if (current !== version) return
    view.value = result
    schedule(id, current)
  } catch (cause) { if (current === version) error.value = message(cause) }
  finally { if (current === version) loading.value = false }
}

async function retry() {
  if (retrying.value || !view.value?.report?.canRetry || notApplicable.value) return
  const current = version, id = String(route.params.id)
  retrying.value = true; retryError.value = ''
  try {
    const result = await reportApi.retry(id)
    if (current !== version) return
    view.value = result; polls = 0; exhausted.value = false
    schedule(id, current)
  } catch (cause) {
    if (current !== version) return
    retryError.value = message(cause)
    if (cause instanceof ApiError && cause.code === 'REPORT_NOT_APPLICABLE') notApplicable.value = true
  } finally { if (current === version) retrying.value = false }
}

watch(() => route.params.id, () => { void load() }, { immediate: true })
onUnmounted(() => { version++; clearTimeout(timer) })
</script>

<template>
  <div class="home-layout report-page" data-testid="report" :aria-busy="loading">
    <p v-if="loading" class="loading-state motion-pending" role="status">正在读取复盘报告…</p>
    <section v-else-if="error" class="load-error" role="alert" data-testid="report-load-error">
      <h1>{{ error }}</h1>
      <button class="button button-primary" @click="load">重新读取</button>
      <RouterLink class="button button-light" :to="isAdmin ? '/admin' : '/home'">{{ isAdmin ? '返回管理工作台' : '返回首页' }}</RouterLink>
    </section>
    <div v-else-if="view" class="report-grid">
      <aside class="report-rail">
      <header class="configuration-section report-cover motion-spotlight">
        <span class="eyebrow motion-scene">分析复盘<span v-if="state"> · {{ state.configuration.scenario.name }} · 共 {{ state.currentRoundNo }} 轮</span></span>
        <p v-if="state" class="field-help">{{ state.configuration.scammer.name }} 与 {{ state.configuration.victim.name }}</p>
        <template v-if="view.outcome">
          <h1 data-testid="report-outcome">{{ view.outcome.displayText }}</h1>
          <p data-testid="report-outcome-reason">{{ view.outcome.reason }}</p>
          <p v-if="view.outcome.finalTrustScore !== null && view.outcome.finalTrustScore !== undefined"
             class="verdict" data-testid="report-verdict">
            最终受骗倾向分：<strong>{{ view.outcome.finalTrustScore }}</strong>
            <span v-if="view.outcome.winning === 'SCAMMER'"> · 骗子胜</span>
            <span v-else-if="view.outcome.winning === 'VICTIM'"> · 受害者胜</span>
          </p>
        </template>
        <template v-else-if="view.ending">
          <h1 data-testid="report-ending">{{ view.ending.displayText }}</h1><p>{{ view.ending.reason }}</p>
        </template>
        <h1 v-else>本次演绎</h1>
        <p v-if="view.report">复盘报告：<strong data-testid="report-status">{{ view.report.displayText }}</strong></p>
      </header>
          <section v-if="view.report?.status === 'SUCCEEDED' && reportV2" class="configuration-section" data-testid="report-funds"><h2>资金变化</h2>
            <p><span data-testid="funds-sentence">{{ fundsSentence }}</span><span class="loss">最终净损失 <strong>{{ money(reportV2.fundsSummary.actualLossYuan) }} 元</strong>。</span></p>
            <details><summary>资金明细</summary><dl>
            <dt>累计支付</dt><dd>{{ reportV2.fundsSummary.totalPaidYuan }} 元</dd>
            <dt>本金返款</dt><dd>{{ reportV2.fundsSummary.totalRefundedYuan }} 元</dd>
            <template v-if="reportV2.contentSchemaVersion === 3 || reportV2.contentSchemaVersion === 4">
              <dt>独立报酬</dt><dd>{{ reportV2.fundsSummary.totalRewardedYuan }} 元</dd>
              <dt>可用余额</dt><dd>{{ reportV2.fundsSummary.availableBalanceYuan }} 元</dd>
              <dt>普通预留剩余</dt><dd>{{ reportV2.fundsSummary.generalReserveRemainingYuan }} 元</dd>
              <dt>应急预留剩余</dt><dd>{{ reportV2.fundsSummary.emergencyReserveRemainingYuan }} 元</dd>
            </template>
            <dt>最终净损失</dt><dd>{{ reportV2.fundsSummary.actualLossYuan }} 元</dd>
          </dl></details></section>
        <RouterLink v-if="!isAdmin && view.report?.status === 'SUCCEEDED'" class="button button-primary report-history"
          :to="`/history/${view.sessionId}`">查看历史详情</RouterLink>
      </aside>
      <div class="report-body">

      <section v-if="view.unavailableReason && view.unavailableReason !== 'SIMULATION_RUNNING'" class="configuration-section" data-testid="report-interrupted">
        <h2>{{ view.unavailableReason === 'LEGACY_NO_REPORT' ? '旧版记录无正式报告' : view.unavailableReason === 'NO_DIALOGUE' ? '尚无完整对话，不生成正式复盘' : '本次演绎已中断，不生成正式复盘' }}</h2>
        <p>{{ state?.interruption?.message }}</p>
      </section>
      <section v-else-if="view.unavailableReason === 'SIMULATION_RUNNING'" class="configuration-section" data-testid="report-unavailable">
        <h2>演绎仍在进行中</h2><p>正式结束后可查看复盘报告。</p>
      </section>
      <section v-else-if="generating" class="configuration-section" data-testid="report-generating" role="status">
        <h2 :class="{ 'motion-pending': !exhausted }">生成中</h2><p>报告正在后台生成，离开页面不影响生成进度。</p>
        <p v-if="exhausted">报告仍在生成，可稍后刷新。</p>
        <button v-if="exhausted" class="button button-light" @click="load">刷新状态</button>
      </section>
      <section v-else-if="view.report?.status === 'FAILED'" class="load-error" data-testid="report-error" role="alert">
        <h2>生成失败</h2><p>{{ view.report.error?.message }}</p>
        <button v-if="view.report.canRetry && !notApplicable" class="button button-primary" data-testid="report-retry" :disabled="retrying" :aria-busy="retrying" @click="retry">
          {{ retrying ? '正在提交…' : '重新生成' }}
        </button>
        <p v-if="retryError">{{ retryError }}</p>
      </section>
      <template v-else-if="view.report?.status === 'SUCCEEDED' && view.content">
        <RiskChart v-if="trustPoints.length" :points="trustPoints" kind="trust" />
        <template v-if="reportV2">
          <section v-if="!view.outcome && !view.ending" class="configuration-section" data-testid="report-result"><h2>{{ reportV2.result.text }}</h2><p>{{ reportV2.result.reason }}</p></section>
          <section class="configuration-section" data-testid="report-summary"><h2>过程概述</h2><p v-for="(paragraph, index) in reportV2.summary.split(/\n\s*\n/)" :key="index">{{ paragraph }}</p></section>
          <section v-if="keyNodes.length" class="configuration-section" data-testid="report-key-nodes">
            <h2>关键节点</h2>
            <article v-for="(node, index) in keyNodes" :key="index" :class="['round-risk', riskClass(riskAtRound(node.roundNo))]" :style="{ '--risk-color': riskColor(riskAtRound(node.roundNo)) }">
              <div class="node-heading">
                <h3>{{ node.title || '事件标题未保存' }}</h3>
                <p class="trust-change" :class="trustTrend(node.roundNo)" data-testid="report-node-trust">{{ trustText(node.roundNo) }}</p>
              </div>
              <p class="field-help">第 {{ node.roundNo }} 轮<span v-if="riskAtRound(node.roundNo) === null"> · 未评分</span></p>
              <p>{{ node.analysis }}</p>
              <details v-if="node.quote"><summary>查看当轮原话</summary><blockquote>{{ node.quote }}</blockquote></details>
            </article>
          </section>
          <section class="configuration-section" data-testid="report-advice"><h2>防范建议</h2><ul><li v-for="(advice, index) in reportV2.preventionAdvice" :key="index">{{ advice }}</li></ul></section>

        </template>
        <template v-else-if="reportV1">
        <section class="configuration-section" data-testid="report-summary"><h2>过程概述</h2><p>{{ reportV1.summary }}</p></section>
        <section class="configuration-section" data-testid="report-round-analyses">
          <h2>逐轮分析</h2>
          <article v-for="round in reportV1.roundAnalyses" :key="round.roundNo" :class="['round-risk', riskClass(riskAtRound(round.roundNo))]" :style="{ '--risk-color': riskColor(riskAtRound(round.roundNo)) }"><h3>第{{ round.roundNo }}轮 <span v-if="trustAtRound(round.roundNo)" class="trust-change" data-testid="report-node-trust">信任分 {{ trustAtRound(round.roundNo)!.before }} → {{ trustAtRound(round.roundNo)!.after }}（{{ trustAtRound(round.roundNo)!.delta > 0 ? '+' : '' }}{{ trustAtRound(round.roundNo)!.delta }}）</span></h3><p v-if="riskAtRound(round.roundNo) === null" class="field-help">未评分</p><p>{{ round.analysis }}</p></article>
        </section>
        <section class="configuration-section" data-testid="report-speech-analysis"><h2>骗子话术分析</h2><p>{{ reportV1.speechAnalysis }}</p></section>
        <section class="configuration-section" data-testid="report-victim-behavior"><h2>受害者行为分析</h2><p>{{ reportV1.victimBehaviorAnalysis }}</p></section>
        <section class="configuration-section" data-testid="report-outcome-analysis"><h2>结局分析</h2><p>{{ reportV1.outcomeAnalysis }}</p></section>
        </template>
      </template>
      <details v-if="view.report?.status === 'SUCCEEDED' && trustPoints.length" class="configuration-section risk-disclosure">
        <summary>查看各轮受骗风险</summary>
        <RiskChart :points="view.riskSeries" />
      </details>
      <RiskChart v-else-if="view.report?.status === 'SUCCEEDED'" :points="view.riskSeries" />
      <nav class="report-actions" aria-label="复盘导航">
        <RouterLink class="button button-primary" :to="isAdmin ? '/admin/demo' : '/configuration'">{{ isAdmin ? '返回演示测试' : view.unavailableReason ? '重新开始体验' : '再体验一次' }}</RouterLink>
        <RouterLink class="button button-light" :to="isAdmin ? '/admin' : '/home'">{{ isAdmin ? '返回管理工作台' : '返回首页' }}</RouterLink>
      </nav>
      </div>
    </div>
  </div>
</template>

<style scoped>
.report-grid { display:grid; grid-template-columns:minmax(250px,300px) minmax(0,1fr); gap:20px; align-items:start; }
.report-rail,.report-body { min-width:0; }
.report-cover { background:#172a43; color:#f4f8fc; border:0; border-top:4px solid var(--accent); }
.report-cover .eyebrow,.report-cover .field-help { color:#bfd5e8; }
.report-cover h1 { font-size:clamp(27px,3vw,37px); }
.report-cover .verdict { border-block:1px solid #59728b; padding:10px 0; }
.report-history { width:100%; margin-bottom:16px; background:var(--accent); color:#22181a; }
.report-history:hover { background:#f9aa8e; }
.verdict strong { font-size:25px; font-variant-numeric:tabular-nums; }
.report-page dl { display:grid; grid-template-columns:1fr auto; gap:6px 10px; font-size:14px; }
.report-page dd { margin:0; text-align:right; }
.report-body [data-testid="report-advice"] { background:var(--ice); border-left:3px solid #4a7190; }
.risk-disclosure :deep(.risk-chart) { border:0; padding:0; margin:0; }
@media(max-width:760px) { .report-grid { grid-template-columns:minmax(0,1fr); } }
.report-page p { white-space: pre-wrap; overflow-wrap: anywhere; }
.node-heading { display:flex; flex-wrap:wrap; align-items:baseline; justify-content:space-between; gap:.5rem 1.5rem; }
.node-heading h3, .node-heading .trust-change { margin:0; font-size:1.25rem; font-weight:700; overflow-wrap:anywhere; }
.trust-change.up { color:#d0021b; }
.trust-change.down { color:#1f8a4c; }
.trust-change.flat, .trust-change.missing { color:#5b6075; }
.loss { display:block; margin-top:1rem; font-size:1.25rem; }
.loss strong { font-size:1.75rem; }
summary { cursor:pointer; padding:.5rem 0; }
summary:focus-visible { outline:2px solid currentColor; outline-offset:3px; }
@media (max-width:600px) { .node-heading { flex-direction:column; } .node-heading h3, .node-heading .trust-change { font-size:1.125rem; } }
.round-risk { border-left: 5px solid var(--risk-color); padding: 1rem; margin-block: 1rem; }
.report-page article + article { border-top: 1px solid var(--border); padding-top: .75rem; }
.report-actions { display: flex; flex-wrap: wrap; gap: 1rem; }
</style>
