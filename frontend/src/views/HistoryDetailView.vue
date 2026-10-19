<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { historyApi } from '../api/history'
import { ApiError } from '../api/http'
import type { HistoryDetail } from '../api/types'
import { formatShanghai } from '../utils/time'
import HistoryConfiguration from '../components/history/HistoryConfiguration.vue'
import HistoryRounds from '../components/history/HistoryRounds.vue'
import RiskChart from '../components/report/RiskChart.vue'

const route = useRoute(), detail = ref<HistoryDetail | null>(null), loading = ref(true), error = ref('')
const statuses = { RUNNING: '进行中', FINISHED: '已结束', INTERRUPTED: '已中断' }
const riskPoints = computed(() => detail.value?.rounds.flatMap(r => r.analysis ? [{ roundNo: r.roundNo, score: r.analysis.riskScore }] : []) ?? [])
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
      <header class="configuration-section"><span class="eyebrow">历史记录 · {{ statuses[detail.status] }}</span><h1>{{ detail.configuration.scenario.name }}</h1><p>开始时间：{{ formatShanghai(detail.startedAt) }}（北京时间）</p><p v-if="detail.endedAt">结束时间：{{ formatShanghai(detail.endedAt) }}</p></header>
      <HistoryConfiguration :configuration="detail.configuration" />
      <section v-if="detail.status === 'FINISHED' && detail.outcome" class="configuration-section" data-testid="history-detail-outcome"><h2>{{ detail.outcome.displayText }}</h2><p>{{ detail.outcome.reason }}</p></section>
      <section v-if="detail.status === 'INTERRUPTED' && detail.interruption" class="configuration-section" data-testid="history-detail-interruption"><h2>本次演绎已中断</h2><p>{{ detail.interruption.message }}</p></section>
      <HistoryRounds :rounds="detail.rounds" />
      <div data-testid="history-detail-risk"><RiskChart :points="riskPoints" /></div>
      <section class="configuration-section" data-testid="history-detail-report">
        <h2>复盘报告</h2>
        <p v-if="detail.status === 'INTERRUPTED'">不生成正式复盘</p>
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
.history-detail > .button { margin-bottom: 1rem; }
.history-detail p { white-space: pre-wrap; overflow-wrap: anywhere; }
.history-detail :deep(dd) { overflow-wrap: anywhere; white-space: pre-wrap; }
@media (max-width: 700px) { .history-detail :deep(.configuration-section) { padding: 20px; } }
</style>
