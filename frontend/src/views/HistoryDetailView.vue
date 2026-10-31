<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { historyApi } from '../api/history'
import { ApiError } from '../api/http'
import type { HistoryDetail, FundFlow } from '../api/types'
import { formatShanghai } from '../utils/time'
import HistoryConfiguration from '../components/history/HistoryConfiguration.vue'
import HistoryRounds from '../components/history/HistoryRounds.vue'
import RiskChart from '../components/report/RiskChart.vue'

const route = useRoute(), detail = ref<HistoryDetail | null>(null), loading = ref(true), error = ref('')
const statuses = { RUNNING: '进行中', FINISHED: '已结束', INTERRUPTED: '已中断' }
const riskPoints = computed(() => detail.value?.rounds.flatMap(r => r.analysis ? [{ roundNo: r.roundNo, score: r.analysis.riskScore }] : []) ?? [])
const purposes: Record<FundFlow['purpose'], string> = { INITIAL_PAYMENT: '首笔付款', ADDITIONAL_PAYMENT: '追加付款', INVESTMENT_PAYMENT: '投资款', EXTRA_FEE_PAYMENT: '额外费用', LOAN_TRANSFER: '借款转账', OTHER_PAYMENT: '其他付款', REFUND: '实际返款', REWARD: '独立报酬', RESERVE_RELEASE: '预留释放' }
let version = 0
async function load() {
  const current = ++version
  loading.value = true; error.value = ''; detail.value = null
  try {
    const result = await historyApi.detail(String(route.params.id))
    if (current === version) detail.value = result
  } catch (cause) {
    if (current === version) error.value = cause instanceof ApiError && cause.status === 404 ? '记录不存在或不可访问'
      : cause instanceof Error ? cause.message : '历史详情加载失败，请重试'
  } finally { if (current === version) loading.value = false }
}
watch(() => route.params.id, () => { void load() }, { immediate: true })
onUnmounted(() => { version++ })
</script>

<template>
  <div class="home-layout history-detail" data-testid="history-detail" :aria-busy="loading">
    <RouterLink class="button button-light" to="/history" data-testid="history-back">返回个人历史</RouterLink>
    <p v-if="loading" class="loading-state" role="status" data-testid="history-detail-loading">正在读取历史详情…</p>
    <section v-else-if="error" class="load-error" role="alert" data-testid="history-detail-error"><h1>{{ error }}</h1><button class="button button-primary" @click="load">重新读取</button></section>
    <template v-else-if="detail">
      <header class="configuration-section"><span class="eyebrow">历史记录 · {{ detail.status === 'INTERRUPTED' && detail.report ? '正常结束' : statuses[detail.status] }}</span><h1>{{ detail.configuration.scenario.name }}</h1><p>开始时间：{{ formatShanghai(detail.startedAt) }}（北京时间）</p><p v-if="detail.endedAt">结束时间：{{ formatShanghai(detail.endedAt) }}</p></header>
      <p v-if="detail.status === 'INTERRUPTED' && detail.report">{{ detail.interruption?.message }}；未形成受骗或识破结论。</p>
      <HistoryConfiguration :configuration="detail.configuration" />
      <section class="configuration-section" data-testid="history-funds">
        <h2>本场资金</h2>
        <p v-if="!detail.funds?.fundsRecorded">未记录</p>
        <dl v-else>
          <dt>初始可动用资金</dt><dd>{{ detail.funds.initialAvailableYuan == null ? '未记录' : `${detail.funds.initialAvailableYuan}元` }}</dd>
          <dt>可动用余额</dt><dd>{{ detail.funds.availableBalanceYuan == null ? '未记录' : `${detail.funds.availableBalanceYuan}元` }}</dd>
          <dt>累计支付</dt><dd>{{ detail.funds.totalPaidYuan == null ? '未记录' : `${detail.funds.totalPaidYuan}元` }}</dd>
          <template v-if="detail.funds.totalRewardedYuan != null"><dt>独立报酬</dt><dd>{{ detail.funds.totalRewardedYuan }}元</dd></template>
          <dt>累计返款</dt><dd>{{ detail.funds.totalRefundedYuan == null ? '未记录' : `${detail.funds.totalRefundedYuan}元` }}</dd>
          <dt>实际亏损</dt><dd>{{ detail.funds.actualLossYuan == null ? '未记录' : `${detail.funds.actualLossYuan}元` }}</dd>
          <dt>追加次数</dt><dd>{{ detail.funds.additionalPaymentCount == null ? '未记录' : `${detail.funds.additionalPaymentCount}次` }}</dd>
          <template v-if="detail.funds.generalReserveTotalYuan != null">
            <dt>普通预留总额</dt><dd>{{ detail.funds.generalReserveTotalYuan }}元</dd>
            <dt>普通预留剩余</dt><dd>{{ detail.funds.generalReserveRemainingYuan }}元</dd>
            <dt>应急预留总额</dt><dd>{{ detail.funds.emergencyReserveTotalYuan }}元</dd>
            <dt>应急预留剩余</dt><dd>{{ detail.funds.emergencyReserveRemainingYuan }}元</dd>
          </template>
          <dt>预留释放</dt><dd>{{ detail.funds.reserveReleasedYuan == null ? '未记录' : `${detail.funds.reserveReleasedYuan}元` }}</dd>
        </dl>
        <template v-if="detail.funds?.fundsRecorded">
          <h3>逐笔资金流水</h3>
          <p v-if="!detail.funds.flows?.length" data-testid="history-funds-empty">暂无资金流水</p>
          <ol v-else class="fund-flows" aria-label="按发生顺序排列的资金流水">
            <li v-for="flow in detail.funds.flows" :key="flow.sequenceNo" data-testid="history-fund-flow">
              <div><strong>第{{ flow.roundNo }}轮 · {{ purposes[flow.purpose] }}</strong><span>{{ flow.amountYuan }}元</span></div>
              <p>发生后余额：{{ flow.balanceAfterYuan }}元</p><p v-if="flow.reason">{{ flow.reason }}</p>
              <time :datetime="flow.occurredAt">{{ formatShanghai(flow.occurredAt) }}</time>
            </li>
          </ol>
        </template>
      </section>
      <section v-if="detail.status === 'FINISHED' && detail.outcome" class="configuration-section" data-testid="history-detail-outcome"><h2>{{ detail.outcome.displayText }}</h2><p>{{ detail.outcome.reason }}</p><p v-if="detail.outcome.finalTrustScore != null" data-testid="history-final-trust">最终受骗倾向分：{{ detail.outcome.finalTrustScore }}</p></section>
      <section v-if="detail.status === 'INTERRUPTED' && detail.interruption" class="configuration-section" data-testid="history-detail-interruption"><h2>{{ detail.interruption.code === 'FUNDS_EXHAUSTED' || detail.interruption.code === 'CHARACTER_EXIT' ? '本次演绎提前终止' : '本次演绎已中断' }}</h2><p>{{ detail.interruption.message }}</p></section>
      <HistoryRounds :rounds="detail.rounds" :ruleset-version="detail.rulesetVersion" />
      <div data-testid="history-detail-risk"><RiskChart :points="riskPoints" /></div>
      <section class="configuration-section" data-testid="history-detail-report">
        <h2>复盘报告</h2>
        <p v-if="!detail.report && detail.status === 'INTERRUPTED'">不生成正式复盘</p>
        <p v-else-if="detail.status === 'RUNNING'">演绎进行中，正式结束后生成复盘报告。</p>
        <p v-else-if="!detail.report" role="alert">报告状态异常，请稍后重试</p>
        <RouterLink v-else-if="detail.report.status === 'SUCCEEDED'" class="button button-primary" :to="`/simulations/${detail.sessionId}/report`" data-testid="history-report-link">已完成 · 查看复盘报告</RouterLink>
        <template v-else-if="detail.report.status === 'FAILED'"><p>生成失败：{{ detail.report.error?.message }}</p><RouterLink class="button button-light" :to="`/simulations/${detail.sessionId}/report`" data-testid="history-report-retry-link">进入报告页重试</RouterLink></template>
        <p v-else role="status">生成中，可稍后重新读取此记录。</p>
      </section>
    </template>
  </div>
</template>

<style scoped>
.history-detail :deep(.configuration-section) { padding: 28px; margin-bottom: 24px; background: white; border: 1px solid var(--border); border-radius: 12px; }
.history-detail { min-width: 0; }
.fund-flows { margin: 0; padding-left: 1.5rem; }
.fund-flows li { padding: .75rem 0; overflow-wrap: anywhere; border-top: 1px solid var(--border); }
.fund-flows li > div { display: flex; flex-wrap: wrap; justify-content: space-between; gap: .5rem 1rem; }
.fund-flows p { margin: .25rem 0; }
.fund-flows time { color: var(--muted); font-size: .85rem; }
.history-detail > .button { margin-bottom: 1rem; }
.history-detail p { white-space: pre-wrap; overflow-wrap: anywhere; }
.history-detail :deep(dd) { overflow-wrap: anywhere; white-space: pre-wrap; }
@media (max-width: 700px) { .history-detail :deep(.configuration-section) { padding: 20px; } }
</style>
