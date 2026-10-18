<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { simulationApi } from '../api/simulation'
import type { SimulationState } from '../api/types'

// Review entry for this phase: the real outcome and report status; report content arrives in the next phase.
const route = useRoute()
const state = ref<SimulationState | null>(null), error = ref(''), loading = ref(true)
onMounted(async () => {
  try { state.value = await simulationApi.state(String(route.params.id)) }
  catch { error.value = '记录不存在或不可访问' }
  finally { loading.value = false }
})
</script>

<template>
  <div class="home-layout" data-testid="report-entry">
    <p v-if="loading" role="status">正在读取演绎结果…</p>
    <section v-else-if="error" class="load-error" role="alert"><h1>{{ error }}</h1><RouterLink class="button button-light" to="/home">返回首页</RouterLink></section>
    <section v-else-if="state?.status === 'FINISHED' && state.outcome" class="configuration-section">
      <span class="eyebrow">{{ state.configuration.scenario.name }} · 共 {{ state.currentRoundNo }} 轮</span>
      <h1 data-testid="report-outcome">{{ state.outcome.displayText }}</h1>
      <p>{{ state.outcome.reason }}</p>
      <p>复盘报告：<strong data-testid="report-status">{{ state.report?.displayText ?? '生成中' }}</strong></p>
      <p class="field-help">复盘报告页面将在下一阶段开放。</p>
      <RouterLink class="button button-primary" to="/configuration">再体验一次</RouterLink>
    </section>
    <section v-else-if="state" class="configuration-section">
      <h1>本次演绎已中断，不生成正式复盘</h1>
      <p>{{ state.interruption?.message }}</p>
      <RouterLink class="button button-primary" to="/configuration">重新开始体验</RouterLink>
    </section>
  </div>
</template>
