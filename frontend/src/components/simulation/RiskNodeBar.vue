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
      </li>
      <li v-if="risk !== null" class="cursor" :style="{ left: `calc(10px + (100% - 20px) * ${risk / 100})`, background: riskColor(risk) }" />
    </ol>
  </div>
</template>

<style scoped>
.risk { min-width:0; }
.risk-head { display:flex; justify-content:space-between; align-items:baseline; font-size:13px; color:#5b6075; }
.risk-head strong { font-size:22px; color:var(--sim-ink); font-variant-numeric:tabular-nums; }
.track { position:relative; display:flex; justify-content:space-between; margin:14px 0 0; padding:0; list-style:none; }
.track::before { content:''; position:absolute; left:10px; right:10px; top:9px; height:2px; background:#c9cedb; }
.node { position:relative; display:flex; flex-direction:column; align-items:center; gap:6px; }
.dot { width:20px; height:20px; border-radius:50%; background:#fff; border:2px solid #c9cedb; transition:transform .3s, background .3s; }
.past .dot { background:#dfe3ec; }
.cursor { position:absolute; top:-3px; width:26px; height:26px; border-radius:50%; transform:translateX(-50%); border:2px solid white; transition:left .3s, background .3s; }
</style>
