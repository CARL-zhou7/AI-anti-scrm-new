<script setup>
import { computed } from 'vue'
import { panelTextSize } from '../presentation'
import { riskColor } from '../ui'

const props = defineProps({ analysis: Object, full: Boolean, showDelta: Boolean })
const trust = computed(() => props.analysis?.trustChange)
const method = computed(() => props.full ? props.analysis.methodAnalysis : props.analysis.methodShort || props.analysis.methodAnalysis)
const intent = computed(() => props.full ? props.analysis.victimPsychology : props.analysis.intentShort || props.analysis.victimPsychology)
const size = computed(() => props.full ? undefined
  : panelTextSize((method.value || '') + (intent.value || ''), uni.getWindowInfo().windowHeight < 640) + 'rpx')
</script>

<template>
  <view v-if="analysis" :class="{ 'live-analysis': !full }">
    <view class="row scores">
      <view class="tile grow center"><text class="block small bold">受骗倾向分</text>
        <text v-if="showDelta && trust?.delta" class="number score-pulse" :class="trust.delta > 0 ? 'rise' : 'fall'">{{ trust.delta > 0 ? '+' : '−' }}{{ Math.abs(trust.delta) }}</text>
        <text v-else-if="trust" class="number">{{ trust.before }} <text :class="trust.delta > 0 ? 'rise' : trust.delta < 0 ? 'fall' : ''">→ {{ trust.after }}</text></text>
        <text v-else class="number">—</text>
      </view>
      <view class="tile grow center"><text class="block small bold">本轮风险</text><text class="number" :style="{ color: riskColor(analysis.riskScore) }">{{ analysis.riskScore ?? '—' }}</text><text class="muted"> / 100</text></view>
    </view>
    <view :class="{ 'analysis-section method': !full }">
      <text class="label">{{ full ? '诈骗手段' : '骗子手段' }}</text>
      <text class="analysis-copy" :style="{ fontSize: size }">{{ method }}</text>
    </view>
    <view :class="{ 'analysis-section intent': !full }">
      <text class="label">{{ full ? '受害者心理' : '想做什么' }}</text>
      <text class="analysis-copy" :style="{ fontSize: size }">{{ intent }}</text>
    </view>
    <template v-if="full">
      <text class="label">话术分析</text><text>{{ analysis.speechAnalysis }}</text>
      <text class="label">风险依据</text><text>{{ analysis.riskReason }}</text>
    </template>
  </view>
</template>

<style scoped>
.scores { align-items: stretch; margin-bottom: 14rpx; }
.scores .tile { padding: 0 0 10rpx; border: 1px solid #bdcdda; background: #e7f0f8; overflow: hidden; }
.scores .tile .small { background: #dce8f3; padding: 3rpx; margin-bottom: 3rpx; font-size: 25rpx; }
.number { font-size: 48rpx; font-weight: 800; font-variant-numeric: tabular-nums; }
.rise { color: #ad302c; }
.fall { color: #11624e; }
.score-pulse { display: inline-block; animation: score-jump .4s ease-out both; }
@keyframes score-jump {
  0% { opacity: 0; transform: translateY(10rpx) scale(.9); }
  55% { opacity: 1; transform: translateY(-4rpx) scale(1.13); }
  100% { transform: translateY(0) scale(1); }
}
@media (prefers-reduced-motion: reduce) { .score-pulse { animation: none; } }
.label { display: block; font-size: 24rpx; font-weight: 750; margin-top: 10rpx; }
.live-analysis { flex: 1; display: flex; flex-direction: column; gap: 12rpx; }
.live-analysis .scores { margin-bottom: 0; }
.analysis-section { flex: 1 0 auto; padding: 14rpx 18rpx; border-radius: 12rpx; }
.method { background: #f4e6db; color: #6d351e; }
.intent { background: #dbe9f3; color: #214962; }
.analysis-section .label { margin: 0 0 6rpx; }
.analysis-copy { white-space: pre-wrap; overflow-wrap: anywhere; }
</style>
