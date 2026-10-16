<script setup>
// 右侧解说区：风险、本轮解说（诈骗阶段/话术/手段/受害者心理）、阅读停留与对话记录。
// 解说仅为文字，与角色发言在视觉上区分（FR-SIM-05、UX-01）。
import { computed } from 'vue'
import RiskMeter from './RiskMeter.vue'

const props = defineProps({
  analysis: { type: Object, default: null }, // latestAnalysis（含 roundNo）
  series: { type: Array, default: () => [] },
  transcript: { type: Array, default: () => [] },
  names: { type: Object, required: true }, // { SCAMMER, VICTIM }
  readingRemainingMs: { type: Number, default: null },
  readingTotalMs: { type: Number, default: 5000 },
  nextAction: { type: String, default: null },
  terminal: { type: Boolean, default: false },
  analysisRef: { type: Function, default: null },
})

const readingPercent = computed(() => {
  if (props.readingRemainingMs === null) return 0
  return Math.max(0, Math.min(100, (props.readingRemainingMs / props.readingTotalMs) * 100))
})
const readingText = computed(() => {
  const s = Math.ceil((props.readingRemainingMs ?? 0) / 1000)
  return props.nextAction === 'REPORT' ? `${s} 秒后进入复盘` : `${s} 秒后进入下一轮`
})

function statusFlag(m) {
  if (!props.terminal) return ''
  if (m.presentationStatus === 'FAILED') return '呈现失败'
  if (m.presentationStatus !== 'COMPLETED') return '未完整呈现'
  return ''
}
</script>

<template>
  <aside class="sim-panel" aria-label="实时解说">
    <RiskMeter :score="analysis ? analysis.riskScore : null" :reason="analysis?.riskReason || ''" :series="series" />

    <section class="sim-card sim-analysis" :class="{ 'is-fresh': analysis }" :key="analysis?.roundNo ?? 0" :ref="(el) => analysisRef?.(el)">
      <h3 class="sim-card__title">
        <span>实时解说</span>
        <span v-if="analysis">第 {{ analysis.roundNo }} 轮</span>
      </h3>
      <dl v-if="analysis" class="sim-analysis__grid">
        <div class="sim-analysis__item">
          <dt>诈骗阶段</dt>
          <dd><span class="sim-analysis__stage">{{ analysis.fraudStage }}</span></dd>
        </div>
        <div class="sim-analysis__item">
          <dt>话术分析</dt>
          <dd>{{ analysis.speechAnalysis }}</dd>
        </div>
        <div class="sim-analysis__item">
          <dt>手段分析</dt>
          <dd>{{ analysis.methodAnalysis }}</dd>
        </div>
        <div class="sim-analysis__item">
          <dt>受害者心理</dt>
          <dd>{{ analysis.victimPsychology }}</dd>
        </div>
        <div class="sim-analysis__item">
          <dt>风险依据</dt>
          <dd>{{ analysis.riskReason }}</dd>
        </div>
      </dl>
      <p v-else class="sim-empty">每轮受害者回应后，这里会给出本轮诈骗阶段、话术、手段与心理分析。</p>
      <div v-if="readingRemainingMs !== null && readingRemainingMs > 0 && nextAction" class="sim-reading">
        <span>{{ readingText }}</span>
        <span class="sim-reading__bar"><span class="sim-reading__fill" :style="{ width: readingPercent + '%' }"></span></span>
      </div>
    </section>

    <section class="sim-card sim-log" aria-label="对话记录">
      <h3 class="sim-card__title">对话记录</h3>
      <p v-if="!transcript.length" class="sim-empty">演绎开始后，双方对话会按顺序记录在这里。</p>
      <div v-for="r in transcript" :key="r.roundNo" class="sim-log__round">
        <div class="sim-log__round-title">
          <span>第 {{ r.roundNo }} 轮</span>
          <span v-if="terminal && !r.complete">未完成</span>
          <span v-else-if="r.analysis">风险 {{ r.analysis.riskScore }}</span>
        </div>
        <p v-for="m in r.messages" :key="m.id" class="sim-log__msg">
          <span class="sim-log__who" :class="`sim-log__who--${m.speaker}`">{{ names[m.speaker] }}：</span>{{ m.content }}<span
            v-if="statusFlag(m)"
            class="sim-log__flag"
            >{{ statusFlag(m) }}</span
          >
        </p>
      </div>
    </section>
  </aside>
</template>
