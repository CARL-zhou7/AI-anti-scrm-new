<script setup lang="ts">
import { ref, watch, nextTick } from 'vue'
import { riskColor, riskClass } from '../../utils/risk'
import type { HistoryRound, PresentationStatus } from '../../api/types'
const props = defineProps<{ rounds: HistoryRound[]; rulesetVersion?: string | null }>()
// V1.4 (PRD-V3.5) shows the real per-round score only in the live narration card; history keeps the full analysis
// but never the per-round score line.
const hidesRoundScore = () => props.rulesetVersion === 'PRD-V3.5'
const scrollRegion = ref<HTMLElement | null>(null)
watch(() => props.rounds, async () => { await nextTick(); if (scrollRegion.value) scrollRegion.value.scrollTop = 0 })
const presentations: Record<PresentationStatus, string> = { PENDING: '尚未呈现', PRESENTING: '呈现未确认完成', COMPLETED: '呈现已完成', FAILED: '呈现失败' }
</script>

<template>
  <section class="configuration-section" data-testid="history-rounds">
    <h2>已保存的演绎过程</h2><p v-if="!rounds.length">暂无已保存轮次</p>
    <div v-else ref="scrollRegion" class="history-rounds-scroll" data-testid="history-rounds-scroll" tabindex="0" role="region" aria-label="已保存的各轮对话与分析">
    <article v-for="round in rounds" :key="round.roundNo" :class="['history-round', riskClass(round.analysis?.riskScore)]" :style="{ '--risk-color': riskColor(round.analysis?.riskScore) }" data-testid="history-round">
      <h3>第{{ round.roundNo }}轮</h3><p v-if="!round.analysis" class="field-help">未评分</p>
      <div data-testid="history-round-funds">
        <p v-if="!round.funds?.fundsRecorded">本轮资金：未记录</p>
        <p v-else>本轮支付：{{ round.funds.totalPaidYuan ?? '未记录' }}元 · 本轮返款：{{ round.funds.totalRefundedYuan ?? '未记录' }}元 · <template v-if="round.funds.totalRewardedYuan != null">本轮报酬：{{ round.funds.totalRewardedYuan }}元 · </template>本轮预留释放：{{ round.funds.reserveReleasedYuan ?? '未记录' }}元<br />
          余额：{{ round.funds.availableBalanceYuan ?? '未记录' }}元 · 累计亏损：{{ round.funds.actualLossYuan ?? '未记录' }}元 · 累计追加：{{ round.funds.additionalPaymentCount ?? '未记录' }}次<template v-if="round.funds.generalReserveRemainingYuan != null"><br />普通预留剩余：{{ round.funds.generalReserveRemainingYuan }}元 · 应急预留剩余：{{ round.funds.emergencyReserveRemainingYuan }}元</template></p>
      </div>
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
        <template v-if="!hidesRoundScore()"><dt>信任分</dt><dd data-testid="history-round-trust"><template v-if="round.analysis.trustChange">{{ round.analysis.trustChange.before }} → {{ round.analysis.trustChange.after }} · 本轮 {{ round.analysis.trustChange.delta > 0 ? '+' : '' }}{{ round.analysis.trustChange.delta }} 分</template><template v-else>未记录</template></dd></template>
        <dt>风险依据</dt><dd>{{ round.analysis.riskReason }}</dd>
      </dl>
    </article>
    </div>
  </section>
</template>

<style scoped>
.history-rounds-scroll { height: 560px; overflow-y: auto; overscroll-behavior-y: contain; scrollbar-gutter: stable; padding: .25rem .75rem .25rem .25rem; }
.history-rounds-scroll:focus-visible { outline: 3px solid var(--green); outline-offset: 4px; }
.history-round { border-left: 5px solid var(--risk-color); padding: .75rem 1rem; background: white; min-width: 0; overflow-wrap: anywhere; }
.history-round + .history-round { border-top: 1px solid var(--border); margin-top: 1.5rem; }
@media (max-width: 700px) { .history-rounds-scroll { height: 440px; padding-right: .25rem; } .history-round { padding: .75rem; } }
.history-message { padding: .5rem 1rem; border-left: 3px solid var(--border); margin: .75rem 0; }
.history-message h4 { margin: .5rem 0; }
.history-message small { color: var(--muted); font-weight: 400; }
.history-analysis dt { font-weight: 600; margin-top: .75rem; }
.history-analysis dd { margin: .25rem 0; }
</style>
