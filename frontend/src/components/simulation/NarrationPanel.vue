<script setup lang="ts">
import type { Analysis, RiskScore } from '../../api/types'
import RiskNodeBar from './RiskNodeBar.vue'
// Text-only narration of the round that just finished; kept on screen until the next round's analysis replaces it.
defineProps<{ narration: { roundNo: number; analysis: Analysis } | null; risk: RiskScore | null }>()
</script>

<template>
  <section class="narration" data-testid="narration-panel" aria-live="polite">
    <div class="analysis">
      <template v-if="narration">
        <header><span class="round">第 {{ narration.roundNo }} 轮解说</span><span class="stage">{{ narration.analysis.fraudStage }}</span></header>
        <dl>
          <div><dt>手段分析</dt><dd>{{ narration.analysis.methodAnalysis }}</dd></div>
          <div><dt>话术分析</dt><dd>{{ narration.analysis.speechAnalysis }}</dd></div>
          <div><dt>受害者心理</dt><dd>{{ narration.analysis.victimPsychology }}</dd></div>
        </dl>
      </template>
      <p v-else class="empty">每轮双方说完后，这里会解读诈骗手段与受害者的心理变化。</p>
    </div>
    <div class="risk-col">
      <RiskNodeBar :risk="risk" />
      <p class="reason"><span>风险依据</span>{{ narration ? narration.analysis.riskReason : '等待第一轮分析' }}</p>
    </div>
  </section>
</template>

<style scoped>
.narration { display:grid; grid-template-columns:minmax(0, 1.7fr) minmax(260px, 1fr); gap:28px; padding:18px 28px; background:var(--sim-mist); color:var(--sim-ink); border-top:3px solid var(--sim-gold); }
header { display:flex; gap:12px; align-items:baseline; margin-bottom:8px; }
.round { font-family:var(--sim-display); font-size:20px; letter-spacing:0.08em; }
.stage { padding:2px 10px; border-radius:999px; background:var(--sim-night); color:var(--sim-paper); font-size:12px; }
dl { display:grid; grid-template-columns:repeat(3, minmax(0, 1fr)); gap:14px; margin:0; }
dt { font-size:12px; color:#5b6075; margin-bottom:4px; }
dd { margin:0; font-size:14px; line-height:1.6; overflow-wrap:anywhere; }
.empty { margin:8px 0 0; color:#5b6075; }
.reason { margin:12px 0 0; font-size:13px; line-height:1.6; }
.reason span { display:block; font-size:12px; color:#5b6075; }
@media (max-width:900px) { .narration { grid-template-columns:1fr; } dl { grid-template-columns:1fr; } }
</style>
