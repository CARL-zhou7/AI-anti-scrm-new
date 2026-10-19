<script setup lang="ts">
import type { HistoryRound, PresentationStatus } from '../../api/types'
defineProps<{ rounds: HistoryRound[] }>()
const presentations: Record<PresentationStatus, string> = { PENDING: '尚未呈现', PRESENTING: '呈现未确认完成', COMPLETED: '呈现已完成', FAILED: '呈现失败' }
</script>

<template>
  <section class="configuration-section" data-testid="history-rounds">
    <h2>已保存的演绎过程</h2><p v-if="!rounds.length">暂无已保存轮次</p>
    <article v-for="round in rounds" :key="round.roundNo" class="history-round" data-testid="history-round">
      <h3>第{{ round.roundNo }}轮</h3>
      <p v-if="!round.interactionComplete" class="field-help" data-testid="history-round-incomplete">本轮未完成（对方回应未保存）</p>
      <p v-else-if="!round.presentationComplete" class="field-help">本轮必要呈现尚未全部完成</p>
      <div v-for="message in round.messages" :key="message.id" class="history-message" data-testid="history-round-message">
        <h4>{{ message.speaker === 'SCAMMER' ? '骗子' : '受害者' }} <small>{{ presentations[message.presentationStatus] }}</small></h4>
        <p>{{ message.content }}</p><p v-if="message.actionText" class="field-help">动作说明：{{ message.actionText }}</p>
      </div>
      <dl v-if="round.analysis" class="history-analysis" data-testid="history-round-analysis">
        <dt>诈骗阶段</dt><dd>{{ round.analysis.fraudStage }}</dd>
        <dt>话术分析</dt><dd>{{ round.analysis.speechAnalysis }}</dd>
        <dt>手段分析</dt><dd>{{ round.analysis.methodAnalysis }}</dd>
        <dt>受害者心理</dt><dd>{{ round.analysis.victimPsychology }}</dd>
        <dt>风险分</dt><dd>{{ round.analysis.riskScore }}分</dd>
        <dt>风险依据</dt><dd>{{ round.analysis.riskReason }}</dd>
      </dl>
    </article>
  </section>
</template>

<style scoped>
.history-round + .history-round { border-top: 1px solid var(--border); margin-top: 1.5rem; padding-top: 1rem; }
.history-message { padding: .5rem 1rem; border-left: 3px solid var(--border); margin: .75rem 0; }
.history-message h4 { margin: .5rem 0; }
.history-message small { color: var(--muted); font-weight: 400; }
.history-analysis dt { font-weight: 600; margin-top: .75rem; }
.history-analysis dd { margin: .25rem 0; }
</style>
