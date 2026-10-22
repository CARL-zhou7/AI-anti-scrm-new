<script setup lang="ts">
import { computed } from 'vue'
import { riskColor, riskClass } from '../../utils/risk'

const props = defineProps<{ points: { roundNo: number; score: number }[] }>()
const plotted = computed(() => {
  const first = props.points[0]?.roundNo ?? 1
  const last = props.points.at(-1)?.roundNo ?? first
  return props.points.map(p => ({ ...p, x: first === last ? 300 : 50 + (p.roundNo - first) / (last - first) * 500, y: 230 - p.score * 2 }))
})
const line = computed(() => plotted.value.map(p => `${p.x},${p.y}`).join(' '))
</script>

<template>
  <section class="configuration-section risk-chart">
    <h2>风险变化</h2>
    <p class="field-help">受害者在本次演绎中的已保存风险变化；缺失轮次不补分。</p>
    <p v-if="!points.length" data-testid="report-risk-empty">暂无已保存的风险评分</p>
    <svg v-else data-testid="report-risk-chart" viewBox="0 0 600 280" role="img" aria-label="各轮受害者风险变化折线图">
      <g v-for="score in [0, 25, 50, 75, 100]" :key="score">
        <line x1="50" x2="550" :y1="230 - score * 2" :y2="230 - score * 2" stroke="#e0e5dc" />
      </g>
      <polyline v-if="points.length > 1" :points="line" fill="none" stroke="#204f40" stroke-width="3" />
      <g v-for="point in plotted" :key="point.roundNo" :class="riskClass(point.score)">
        <circle data-testid="report-risk-point" :cx="point.x" :cy="point.y" r="5" :fill="riskColor(point.score)"
          :aria-label="`第${point.roundNo}轮 ${point.score}分`"><title>第{{ point.roundNo }}轮风险</title></circle>
        <text :x="point.x" y="257" text-anchor="middle">{{ point.roundNo }}</text>
      </g>
      <text x="575" y="257" text-anchor="end">轮</text>
    </svg>
  </section>
</template>

<style scoped>
.risk-chart svg { display: block; width: 100%; max-width: 760px; height: auto; }
.risk-chart text { font-size: 13px; fill: #728078; }
</style>
