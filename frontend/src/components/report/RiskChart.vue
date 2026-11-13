<script setup lang="ts">
import { computed } from 'vue'
import { riskColor, riskClass } from '../../utils/risk'

const props = defineProps<{ points: { roundNo: number; score: number }[]; kind?: 'trust' }>()
const plotted = computed(() => {
  const first = props.points[0]?.roundNo ?? 1
  const last = props.points.at(-1)?.roundNo ?? first
  return props.points.map(p => ({ ...p, x: first === last ? 300 : 50 + (p.roundNo - first) / (last - first) * 500, y: 230 - p.score * 2 }))
})
const line = computed(() => plotted.value.map(p => `${p.x},${p.y}`).join(' '))
</script>

<template>
  <section class="configuration-section risk-chart">
    <h2>{{ kind === 'trust' ? '受骗倾向变化' : '风险变化' }}</h2>
    <p class="field-help">{{ kind === 'trust' ? '按轮次展示已保存的受骗倾向分。' : '受害者在本次演绎中的已保存风险变化；缺失轮次不补分。' }}</p>
    <p v-if="!points.length" data-testid="report-risk-empty">暂无已保存的风险评分</p>
    <svg v-else :data-testid="kind === 'trust' ? 'report-trust-chart' : 'report-risk-chart'" viewBox="0 0 600 280" role="img"
      :aria-label="kind === 'trust' ? '各轮受骗倾向分变化折线图' : '各轮受害者风险变化折线图'">
      <g v-for="score in [0, 25, 50, 75, 100]" :key="score">
        <line x1="50" x2="550" :y1="230 - score * 2" :y2="230 - score * 2" stroke="#cfdae3" />
        <text x="38" :y="234 - score * 2" text-anchor="end">{{ score }}</text>
      </g>
      <polyline v-if="points.length > 1" class="trend-line" :points="line" pathLength="1" fill="none" stroke="#2d5f83" stroke-width="3" />
      <g v-for="point in plotted" :key="point.roundNo" :class="riskClass(point.score)">
        <circle :data-testid="kind === 'trust' ? 'report-trust-point' : 'report-risk-point'" :cx="point.x" :cy="point.y" r="5" :fill="kind === 'trust' ? '#2d5f83' : riskColor(point.score)"
          :aria-label="`第${point.roundNo}轮 ${point.score}分`"><title>第{{ point.roundNo }}轮{{ kind === 'trust' ? '受骗倾向' : '风险' }}</title></circle>
        <text :x="point.x" y="257" text-anchor="middle">{{ point.roundNo }}</text>
      </g>
      <text x="575" y="257" text-anchor="end">轮</text>
    </svg>
  </section>
</template>

<style scoped>
.risk-chart svg { display: block; width: 100%; max-width: 760px; height: auto; }
.risk-chart text { font-size: 13px; fill: #506479; }
@media (prefers-reduced-motion:no-preference) {
  .trend-line { animation:trace-trend 420ms var(--motion-ease) 100ms both; }
  details:not([open]) .trend-line { animation:none; }
  @keyframes trace-trend { from { stroke-dasharray:1; stroke-dashoffset:1; } to { stroke-dasharray:1; stroke-dashoffset:0; } }
}
</style>
