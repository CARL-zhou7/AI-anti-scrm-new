<script setup lang="ts">
import type { RiskScore } from '../../api/types'
// Five fixed teaching levels; before the first analysis it reads "not scored yet", never 0.
defineProps<{ risk: RiskScore | null }>()
const levels: RiskScore[] = [0, 25, 50, 75, 100]
</script>

<template>
  <div class="risk" data-testid="risk-bar" role="img" :aria-label="risk === null ? '风险指数：尚未评分' : '风险指数：' + risk">
    <div class="risk-head"><span>受害者当前受骗风险</span><strong>{{ risk === null ? '尚未评分' : risk }}</strong></div>
    <ol class="track">
      <li v-for="level in levels" :key="level" :class="['node', 'l' + level, { on: risk === level, past: risk !== null && level < risk }]">
        <span class="dot" /><span class="value">{{ level }}</span>
      </li>
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
.value { font-size:12px; color:#7a7f92; font-variant-numeric:tabular-nums; }
.past .dot { background:#dfe3ec; }
.on .dot { transform:scale(1.35); border-color:transparent; }
.on.l0 .dot { background:#3f8f6f } .on.l25 .dot { background:#7aa35a } .on.l50 .dot { background:#d9a23a }
.on.l75 .dot { background:#d9733a } .on.l100 .dot { background:#c43d33 }
.on .value { color:var(--sim-ink); font-weight:700; }
</style>
