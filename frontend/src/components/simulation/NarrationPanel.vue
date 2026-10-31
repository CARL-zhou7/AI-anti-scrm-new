<script setup lang="ts">
import { computed } from 'vue'
import type { Analysis, RiskScore } from '../../api/types'
import RiskNodeBar from './RiskNodeBar.vue'
// Text-only narration of the round that just finished; kept on screen until the next round's analysis replaces it.
// V1.4 (D9): three columns — method label, intent sentence and the real saved trust change. The full analyses stay
// in the payload for reports and history; they are only used here as a fallback for records without short fields.
const props = defineProps<{ narration: { roundNo: number; analysis: Analysis } | null; risk: RiskScore | null;
  awaitingContinue?: boolean; disabled?: boolean; error?: string }>()
const emit = defineEmits<{ (e: 'continue'): void }>()
function continueDialogue() { if (props.awaitingContinue && !props.disabled) emit('continue') }

const method = computed(() => {
  const a = props.narration?.analysis
  if (!a) return ''
  return a.methodShort && a.methodShort.trim() ? a.methodShort : a.methodAnalysis
})
const intent = computed(() => {
  const a = props.narration?.analysis
  if (!a) return ''
  return a.intentShort && a.intentShort.trim() ? a.intentShort : a.speechAnalysis
})
const change = computed(() => props.narration?.analysis.trustChange ?? null)
const trend = computed(() => {
  const delta = change.value?.delta ?? 0
  return delta > 0 ? 'up' : delta < 0 ? 'down' : 'flat'
})
const trendText = computed(() => (trend.value === 'up' ? '受骗倾向上升' : trend.value === 'down' ? '受骗倾向下降' : '受骗倾向不变'))
const deltaText = computed(() => {
  const delta = change.value?.delta ?? 0
  if (delta > 0) return `+${delta}`
  if (delta < 0) return `−${Math.abs(delta)}`
  return '0'
})
</script>

<template>
  <section class="narration" :class="{ continuable: awaitingContinue && !disabled }" data-testid="narration-panel" aria-live="polite"
    :role="awaitingContinue ? 'button' : undefined" :tabindex="awaitingContinue ? 0 : undefined"
    :aria-disabled="awaitingContinue ? !!disabled : undefined" :aria-label="awaitingContinue ? '继续对话' : undefined"
    @click="continueDialogue" @keydown.enter.prevent="continueDialogue" @keydown.space.prevent="continueDialogue">
    <header class="head">
      <template v-if="narration">
        <span class="round">第 {{ narration.roundNo }} 轮解说</span>
        <span class="sep">|</span>
        <span class="stage" data-testid="narration-stage">{{ narration.analysis.fraudStage }}</span>
      </template>
      <span v-else class="round">本轮解说</span>
    </header>

    <div v-if="narration" class="card">
      <div class="col method-col">
        <p class="label">骗子的手段</p>
        <p class="method" data-testid="narration-method">{{ method }}</p>
        <span class="arrow" aria-hidden="true">→</span>
      </div>
      <div class="col intent-col">
        <p class="label">骗子想做什么？</p>
        <p class="intent" data-testid="narration-intent">{{ intent }}</p>
      </div>
      <div class="col trust-col">
        <p class="label">对骗子的信任</p>
        <template v-if="change">
          <p class="delta" :class="trend" data-testid="trust-change">
            <span class="mark" aria-hidden="true">{{ trend === 'up' ? '↑' : trend === 'down' ? '↓' : '—' }}</span>
            <span class="value">{{ deltaText }}</span>
          </p>
          <p class="range">
            <span class="scores">{{ change.before }} → {{ change.after }}</span>
            <span class="dot">·</span>
            <span class="trend-text" :class="trend">{{ trendText }}</span>
          </p>
          <p class="caption">受骗倾向分 · 仅供观看，角色不可见</p>
        </template>
        <p v-else class="missing" data-testid="trust-missing">暂无受骗倾向数据</p>
      </div>
    </div>
    <p v-else class="empty">每轮双方说完后，这里会解读诈骗手段、骗子意图与受骗倾向变化。</p>

    <div class="risk-col">
      <RiskNodeBar :risk="risk" />
      <p class="reason"><span>风险依据</span>{{ narration ? narration.analysis.riskReason : '等待第一轮分析' }}</p>
    </div>
    <p v-if="error" class="error" role="alert" data-testid="dialogue-error">{{ error }}</p>
    <p v-if="awaitingContinue" class="continue-hint" data-testid="review-hint">{{ disabled ? '请稍候或完成台词编辑' : '点击任意位置继续对话' }}</p>
  </section>
</template>

<style scoped>
.narration { background:var(--sim-mist); color:var(--sim-ink); border-top:2px solid var(--sim-night); padding:0 0 14px; }
.continuable { cursor:pointer; }
.narration:focus-visible { outline:3px solid var(--sim-gold); outline-offset:-3px; }
.continue-hint { margin:10px 28px 0; text-align:right; font-size:12px; color:#5b6075; }
.error { margin:10px 28px 0; color:#a33328; font-size:13px; }
.head { display:flex; gap:10px; align-items:baseline; padding:14px 28px 10px; font-family:var(--sim-display); }
.round { font-size:19px; letter-spacing:0.06em; }
.sep { color:#9aa2b5; }
.stage { font-size:14px; color:#1f2a44; }
.card { display:grid; grid-template-columns:minmax(0, 0.9fr) minmax(0, 1.4fr) minmax(210px, 0.8fr); gap:0; padding:0 28px; align-items:stretch; }
.col { padding:6px 22px 6px 0; min-width:0; }
.method-col { position:relative; display:flex; flex-direction:column; justify-content:center; border-left:4px solid #f2711c; padding-left:16px; }
.intent-col { border-left:1px solid #d5dae4; padding-left:22px; display:flex; flex-direction:column; justify-content:center; }
.trust-col { border-left:1px solid #d5dae4; padding-left:22px; }
.label { margin:0 0 6px; font-size:13px; color:#5b6075; letter-spacing:0.02em; }
.method { margin:0; font-family:var(--sim-display); font-size:26px; font-weight:700; color:#f2711c; line-height:1.25; overflow-wrap:anywhere; }
.arrow { position:absolute; right:8px; top:50%; transform:translateY(-50%); color:#f2711c; font-size:26px; line-height:1; }
.intent { margin:0; font-size:22px; font-weight:700; color:#1f2a44; line-height:1.45; overflow-wrap:anywhere; }
.delta { display:flex; align-items:center; gap:6px; margin:2px 0 4px; font-family:var(--sim-display); line-height:1; }
.delta .mark { font-size:34px; }
.delta .value { font-size:34px; font-weight:700; font-variant-numeric:tabular-nums; }
.delta.up, .delta.up .value, .delta.up .mark { color:#d0021b; }
.delta.down, .delta.down .value, .delta.down .mark { color:#1f8a4c; }
.delta.flat, .delta.flat .value, .delta.flat .mark { color:#5b6075; }
.range { margin:0; font-size:14px; color:#1f2a44; display:flex; flex-wrap:wrap; gap:6px; align-items:baseline; }
.scores { font-variant-numeric:tabular-nums; font-weight:600; }
.dot { color:#9aa2b5; }
.trend-text.up { color:#d0021b; }
.trend-text.down { color:#1f8a4c; }
.trend-text.flat { color:#5b6075; }
.caption { margin:6px 0 0; font-size:12px; color:#5b6075; }
.missing { margin:4px 0 0; color:#5b6075; font-size:14px; }
.empty { margin:4px 28px 0; color:#5b6075; }
.risk-col { margin:14px 28px 0; }
.reason { margin:10px 0 0; font-size:13px; line-height:1.6; }
.reason span { display:block; font-size:12px; color:#5b6075; }
@media (max-width:900px) {
  .card { grid-template-columns:1fr; }
  .method-col, .intent-col, .trust-col { border-left:0; border-top:1px solid #d5dae4; padding:12px 0; }
  .method-col { border-top:0; border-left:4px solid #f2711c; padding-left:14px; }
  .arrow { display:none; }
  .method, .intent { font-size:20px; }
}
</style>
