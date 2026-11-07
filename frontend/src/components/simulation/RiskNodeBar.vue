<script setup lang="ts">
import { riskColor, riskClass } from '../../utils/risk'
import type { RiskScore } from '../../api/types'
// The five marks are a scale; the pointer and score move continuously.
defineProps<{ risk: RiskScore | null }>()
const levels: RiskScore[] = [0, 25, 50, 75, 100]
</script>

<template>
  <div class="risk" data-testid="risk-bar" role="img" :aria-label="risk === null ? '风险指数：尚未评分' : '风险指数：' + risk">
    <div class="risk-head"><span>受害者当前受骗风险</span><strong>{{ risk === null ? '尚未评分' : risk + ' / 100' }}</strong></div>
    <ol class="track">
      <li v-for="level in levels" :key="level" :class="['node', riskClass(level), { on: risk === level, past: risk !== null && level < risk }]">
        <span class="dot" :style="risk === level ? { background: riskColor(level) } : undefined" />
        <span class="tick">{{ level }}</span>
      </li>
      <li v-if="risk !== null" class="cursor" :style="{ left: `calc(7px + (100% - 14px) * ${risk / 100})`, background: riskColor(risk) }" />
    </ol>
  </div>
</template>

<style scoped>
.risk { min-width:0; }
.risk-head { display:flex; justify-content:space-between; align-items:baseline; flex-wrap:wrap; gap:4px 12px; color:#eef6fc; font-size:17px; font-weight:700; }
.risk-head strong { font:700 27px/1.2 var(--sim-digits); font-variant-numeric:tabular-nums; }
.track { position:relative; display:flex; justify-content:space-between; margin:7px 3px 0; padding:0; list-style:none; }
.track::before { content:''; position:absolute; left:7px; right:7px; top:7px; height:2px; background:#6a88a3; }
.node { position:relative; display:grid; justify-items:center; gap:2px; width:14px; }
.dot { width:14px; height:14px; border-radius:50%; background:#182b42; border:1px solid #a1bbd1; }
.past .dot { background:#708ba5; }
.tick { color:#c7dbed; font:13px var(--sim-digits); }
.cursor { position:absolute; top:-3px; width:21px; height:21px; border-radius:50%; transform:translateX(-50%); border:2px solid #f0f8ff; transition:left .3s, background .3s; }
</style>
