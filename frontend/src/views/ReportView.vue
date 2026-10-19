<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { reportApi } from '../api/report'
import { simulationApi } from '../api/simulation'
import { ApiError } from '../api/http'
import type { ReportView, SimulationState } from '../api/types'
import RiskChart from '../components/report/RiskChart.vue'

const route = useRoute()
const view = ref<ReportView | null>(null), state = ref<SimulationState | null>(null)
const loading = ref(true), error = ref(''), retryError = ref(''), retrying = ref(false), exhausted = ref(false), notApplicable = ref(false)
const generating = computed(() => view.value?.report?.status === 'PENDING' || view.value?.report?.status === 'RUNNING')
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
  <div class="home-layout report-page" data-testid="report">
    <p v-if="loading" role="status">正在读取复盘报告…</p>
    <section v-else-if="error" class="load-error" role="alert" data-testid="report-load-error">
      <h1>{{ error }}</h1>
      <button class="button button-primary" @click="load">重新读取</button>
      <RouterLink class="button button-light" to="/home">返回首页</RouterLink>
    </section>
    <template v-else-if="view">
      <header class="configuration-section">
        <span class="eyebrow">分析复盘<span v-if="state"> · {{ state.configuration.scenario.name }} · 共 {{ state.currentRoundNo }} 轮</span></span>
        <p v-if="state" class="field-help">{{ state.configuration.scammer.name }} 与 {{ state.configuration.victim.name }}</p>
        <template v-if="view.outcome">
          <h1 data-testid="report-outcome">{{ view.outcome.displayText }}</h1>
          <p data-testid="report-outcome-reason">{{ view.outcome.reason }}</p>
        </template>
        <h1 v-else>本次演绎</h1>
        <p v-if="view.report">复盘报告：<strong data-testid="report-status">{{ view.report.displayText }}</strong></p>
      </header>
      <section v-if="view.unavailableReason === 'SIMULATION_INTERRUPTED'" class="configuration-section" data-testid="report-interrupted">
        <h2>本次演绎已中断，不生成正式复盘</h2>
        <p>{{ state?.interruption?.message }}</p>
      </section>
      <section v-else-if="view.unavailableReason === 'SIMULATION_RUNNING'" class="configuration-section" data-testid="report-unavailable">
        <h2>演绎仍在进行中</h2><p>正式结束后可查看复盘报告。</p>
      </section>
      <section v-else-if="generating" class="configuration-section" data-testid="report-generating" role="status">
        <h2>生成中</h2><p>报告正在后台生成，离开页面不影响生成进度。</p>
        <p v-if="exhausted">报告仍在生成，可稍后刷新。</p>
        <button v-if="exhausted" class="button button-light" @click="load">刷新状态</button>
      </section>
      <section v-else-if="view.report?.status === 'FAILED'" class="load-error" data-testid="report-error" role="alert">
        <h2>生成失败</h2><p>{{ view.report.error?.message }}</p>
        <button v-if="view.report.canRetry && !notApplicable" class="button button-primary" data-testid="report-retry" :disabled="retrying" @click="retry">
          {{ retrying ? '正在提交…' : '重新生成' }}
        </button>
        <p v-if="retryError">{{ retryError }}</p>
      </section>
      <template v-else-if="view.report?.status === 'SUCCEEDED' && view.content">
        <section class="configuration-section" data-testid="report-summary"><h2>过程总结</h2><p>{{ view.content.summary }}</p></section>
        <section class="configuration-section" data-testid="report-round-analyses">
          <h2>逐轮分析</h2>
          <article v-for="round in view.content.roundAnalyses" :key="round.roundNo"><h3>第{{ round.roundNo }}轮</h3><p>{{ round.analysis }}</p></article>
        </section>
        <section class="configuration-section" data-testid="report-speech-analysis"><h2>骗子话术分析</h2><p>{{ view.content.speechAnalysis }}</p></section>
        <section class="configuration-section" data-testid="report-victim-behavior"><h2>受害者行为分析</h2><p>{{ view.content.victimBehaviorAnalysis }}</p></section>
        <section class="configuration-section" data-testid="report-outcome-analysis"><h2>结局分析</h2><p>{{ view.content.outcomeAnalysis }}</p></section>
      </template>
      <RiskChart v-if="view.simulationStatus === 'FINISHED'" :points="view.riskSeries" />
      <nav class="report-actions" aria-label="复盘导航">
        <RouterLink class="button button-primary" to="/configuration">{{ view.unavailableReason ? '重新开始体验' : '再体验一次' }}</RouterLink>
        <RouterLink class="button button-light" to="/home">返回首页</RouterLink>
      </nav>
    </template>
  </div>
</template>

<style scoped>
.report-page p { white-space: pre-wrap; overflow-wrap: anywhere; }
.report-page article + article { border-top: 1px solid var(--border); padding-top: .75rem; }
.report-actions { display: flex; flex-wrap: wrap; gap: 1rem; }
</style>
