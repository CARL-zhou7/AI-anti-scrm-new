<script setup lang="ts">
import { computed } from 'vue'
import type { Analysis, RiskScore } from '../../api/types'
import RiskNodeBar from './RiskNodeBar.vue'
// Text-only narration of the round that just finished; kept on screen until the next round's analysis replaces it.
// V1.4 (D9): three columns — method label, intent sentence and the real saved trust change. The full analyses stay
// in the payload for reports and history; they are only used here as a fallback for records without short fields.
const props = defineProps<{ narration: { roundNo: number; analysis: Analysis } | null; risk: RiskScore | null;
  awaitingContinue?: boolean; disabled?: boolean; disabledHint?: string; error?: string }>()
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
    @click="continueDialogue" @keydown.enter.self.prevent="continueDialogue" @keydown.space.self.prevent="continueDialogue">
    <div class="narration-main">
      <header class="head">
        <template v-if="narration">
          <span class="round">第 {{ narration.roundNo }} 轮解说</span>
          <span class="stage" data-testid="narration-stage">{{ narration.analysis.fraudStage }}</span>
        </template>
        <span v-else class="round">本轮解说</span>
      </header>

      <div v-if="narration" class="card">
        <div class="col method-col">
          <p class="label">骗子的手段</p>
          <p class="method" data-testid="narration-method">{{ method }}</p>
        </div>
        <div class="col intent-col">
          <p class="label">骗子想做什么？</p>
          <p class="intent" data-testid="narration-intent">{{ intent }}</p>
        </div>
        <div class="col trust-col">
          <p class="label">受骗倾向分</p>
          <template v-if="change">
            <p class="range">
              <span class="scores">{{ change.before }} → {{ change.after }}</span>
              <span class="delta" :class="trend" data-testid="trust-change">{{ deltaText }}</span>
            </p>
            <p class="caption">{{ trendText }} · 仅供观看，角色不可见</p>
          </template>
          <p v-else class="missing" data-testid="trust-missing">暂无受骗倾向数据</p>
        </div>
      </div>
      <p v-else class="empty">每轮双方说完后，这里会解读诈骗手段、骗子意图与受骗倾向变化。</p>
    </div>
    <div class="risk-col">
      <RiskNodeBar :risk="risk" />
      <p class="reason"><span>风险依据</span>{{ narration ? narration.analysis.riskReason : '等待第一轮分析' }}</p>
      <p v-if="error" class="error" role="alert" data-testid="dialogue-error">{{ error }}</p>
      <p v-if="awaitingContinue" class="continue-hint" data-testid="review-hint">{{ disabled ? disabledHint || '请稍候…' : '点击解说区任意位置继续对话' }}</p>
    </div>
  </section>
</template>

<style scoped>
.narration { display:grid; grid-template-columns:subgrid; grid-template-rows:subgrid; min-width:0; }
.narration-main,.risk-col { border:1px solid #506981; border-radius:5px; background:#182b42; color:#f0f6fc; min-width:0; }
.narration-main { grid-column:1; }
.risk-col { grid-column:1/-1; padding:10px 14px; transition:background-color var(--motion-feedback),border-color var(--motion-feedback); }
.continuable { cursor:pointer; }
.continuable .risk-col { border-color:#7494ae; }
.continuable:is(:hover,:focus-visible,:active) .risk-col { border-color:#a4c6e3; background:#20364e; }
.narration:focus-visible { outline:3px solid #a4d8ff; outline-offset:3px; border-radius:5px; }
.head { display:flex; flex-wrap:wrap; gap:4px 12px; align-items:baseline; padding:9px 12px; border-bottom:1px solid #465d75; }
.round { font-size:17px; font-weight:750; }
.stage,.label,.caption,.reason span { color:#bed3e5; font-size:14px; }
.card { display:grid; grid-template-columns:1fr 1.5fr minmax(135px,.85fr); }
.col { padding:9px 12px; min-width:0; }
.col + .col { border-left:1px solid #465d75; }
.label,.caption { margin:0 0 3px; }
.method,.intent { margin:0; font-size:16px; font-weight:700; overflow-wrap:anywhere; }
.method { color:#ffd2bf; }
.range { display:flex; flex-wrap:wrap; align-items:baseline; gap:3px 8px; margin:0; font:700 23px/1.2 var(--sim-digits); font-variant-numeric:tabular-nums; }
.delta { font-size:15px; }
.delta.up { color:#ffb99b; }
.delta.down { color:#9ad9b9; }
.caption { margin:4px 0 0; font-size:12px; }
.missing,.empty { margin:0; color:#bdcfe0; font-size:14px; }
.empty { padding:12px; }
.reason,.error,.continue-hint { margin:6px 0 0; font-size:14px; overflow-wrap:anywhere; }
.reason span { font-weight:700; margin-right:9px; }
.error { color:#ffc5b4; }
.continue-hint { color:#d7eaf9; text-align:right; }
@media(min-width:1001px) and (min-height:700px) {
  .head { padding-block:6px; }
  .head .stage { min-width:0; max-height:2.9em; overflow-wrap:anywhere; }
  .card { grid-template-columns:minmax(0,1fr) minmax(0,2.1fr) minmax(126px,.9fr); }
  .col { padding-block:7px; }
  .method,.intent { max-height:min(11dvh,100px); line-height:1.45; }
  .risk-col { padding-block:8px; }
  .reason,.error { max-height:min(8dvh,66px); }
  .head .stage,.method,.intent,.reason,.error { overflow-y:auto; overscroll-behavior:contain; scrollbar-width:thin; }
}
@media(min-width:1001px) and (700px <= height <= 820px) {
  .head,.col { padding-block:4px; }
  .method,.intent { max-height:9dvh; font-size:15px; }
  .risk-col { padding-block:6px; }
  .reason,.error,.continue-hint { font-size:13px; line-height:1.45; margin-top:4px; }
}
@media(max-width:760px) {
  .card { grid-template-columns:1fr; }
  .col + .col { border-left:0; border-top:1px solid #465d75; }
}
</style>
