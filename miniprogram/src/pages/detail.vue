<script setup>
import { computed, nextTick, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { get, pageUrl, runPath } from '../api'
import { errorText, shanghai, yuan } from '../ui'
import { outcomeReason, outcomeVerdict } from '../outcome'
import { playedRounds } from '../presentation'
import Portrait from '../components/Portrait.vue'
import Dialogue from '../components/Dialogue.vue'
import Trend from '../components/Trend.vue'
import RoundAnalysis from '../components/RoundAnalysis.vue'

const view = ref(null), error = ref(''), busy = ref(false), currentRound = ref(0), panel = ref('history')
const openFunds = ref({ reserves: false, flows: false })
let id, initialRound
const rounds = computed(() => playedRounds(view.value?.rounds || []))
const risk = computed(() => rounds.value.map(round => ({ roundNo: round.roundNo, score: round.analysis?.riskScore })))
const verdict = computed(() => {
  const outcome = view.value?.outcome, interruption = view.value?.interruption
  if (!outcome && !interruption) return null
  return { ...outcomeVerdict(outcome), reason: outcomeReason(outcome, null, interruption) }
})
const flows = { PAYMENT: '支付', REFUND: '退款', REWARD: '报酬', RESERVE_RELEASE: '释放预留资金' }
async function load() {
  busy.value = true; error.value = ''
  try {
    view.value = await get(runPath(id, '/detail?includeRoundTrust=true'))
    uni.setNavigationBarTitle({ title: view.value.configuration.scenario.name })
  } catch (cause) { error.value = errorText(cause) }
  finally { busy.value = false }
  if (!error.value && initialRound) void openRound(initialRound)
}
async function openRound(roundNo) {
  if (!rounds.value.some(round => round.roundNo === roundNo)) return
  currentRound.value = roundNo
  await nextTick()
  if (currentRound.value === roundNo) uni.pageScrollTo({ selector: '#round-' + roundNo, duration: 250 })
}
function report() { uni.redirectTo({ url: pageUrl('report', id) }) }
function selectPanel(value) {
  panel.value = value
  uni.pageScrollTo({ scrollTop: 0, duration: 0 })
  uni.setNavigationBarTitle({ title: value === 'funds' ? '个人历史' : view.value.configuration.scenario.name })
}
onLoad(query => { id = query.id || ''; initialRound = Number(query.round); void load() })
</script>

<template>
  <text v-if="busy" class="muted block page">正在读取演绎记录…</text>
  <view v-if="error" class="page"><text class="error">{{ error }}</text><button class="ghost" @tap="load">重新读取</button></view>
  <template v-if="view">
    <view class="hero detail-hero"><image class="hero-art" :src="panel === 'funds' ? '/static/archive.jpg' : '/static/pair.jpg'" mode="aspectFill" /></view>
    <view class="surface overlap">
      <template v-if="panel === 'history'">
        <view class="row section-rule"><text>当时画像</text><view /></view>
        <view class="row">
          <view v-for="person in [view.configuration.scammer, view.configuration.victim]" :key="person.name" class="row tile grow profile">
            <Portrait class="avatar" :person="person" />
            <view><text class="bold block">{{ person.name }}</text><text class="muted small">{{ person.age }} 岁</text></view>
          </view>
        </view>
        <template v-if="verdict">
          <view class="row section-rule"><text>最终结果</text><view /></view>
          <view class="tile result-block">
            <text class="result-badge" :class="'result-' + verdict.tone">{{ verdict.title }}</text>
            <text v-if="verdict.reason" class="muted block result">{{ verdict.reason }}</text>
          </view>
        </template>
        <view class="actions detail-links"><button @tap="report">▤　查看复盘报告　›</button><button @tap="selectPanel('funds')">¥　查看资金流水　›</button></view>
        <view class="row section-rule"><text>演绎过程</text><view /></view>
        <text v-if="!rounds.length" class="muted">暂无已保存的对话</text>
        <view v-for="round in rounds" :id="'round-' + round.roundNo" :key="round.roundNo" class="round">
          <button class="round-head" @tap="currentRound = currentRound === round.roundNo ? 0 : round.roundNo">第 {{ round.roundNo }} 轮 {{ currentRound === round.roundNo ? '⌃' : '⌄' }}</button>
          <view v-if="currentRound === round.roundNo" class="round-body">
            <Dialogue v-if="round.messages.length" :messages="round.messages" :cast="view.configuration" />
            <text v-else class="muted">暂无本轮对白</text>
            <RoundAnalysis v-if="round.analysis" class="saved-analysis" :analysis="round.analysis" full />
          </view>
        </view>
        <Trend label="风险走势" :series="risk" selectable @select="openRound" />
      </template>
      <template v-else>
        <view class="row section-rule"><text>本场资金</text><view /></view>
        <view v-if="view.funds?.fundsRecorded">
          <view class="row funds">
            <view class="tile grow center"><text class="small block">累计支付</text><text class="bold">{{ yuan(view.funds.totalPaidYuan) }}</text></view>
            <view class="tile grow center"><text class="small block">累计退款</text><text class="bold">{{ yuan(view.funds.totalRefundedYuan) }}</text></view>
            <view class="tile grow center"><text class="small block">实际亏损</text><text class="bold">{{ yuan(view.funds.actualLossYuan) }}</text></view>
          </view>
          <text class="muted block result">累计报酬 {{ yuan(view.funds.totalRewardedYuan) }} · 可用余额 {{ yuan(view.funds.availableBalanceYuan) }}</text>
          <view class="actions">
            <button class="ghost" @tap="openFunds.reserves = !openFunds.reserves">预留资金 {{ openFunds.reserves ? '⌃' : '⌄' }}</button>
            <view v-if="openFunds.reserves" class="tile">
              <text class="block">一般预留余额 {{ yuan(view.funds.generalReserveRemainingYuan) }}</text>
              <text class="block">应急预留余额 {{ yuan(view.funds.emergencyReserveRemainingYuan) }}</text>
            </view>
            <button class="ghost" @tap="openFunds.flows = !openFunds.flows">逐笔资金流水 {{ openFunds.flows ? '⌃' : '⌄' }}</button>
          </view>
          <view v-if="openFunds.flows" class="stack">
            <view v-for="flow in view.funds.flows || []" :key="flow.sequenceNo" class="tile">
              <view class="row between"><text class="bold">{{ flows[flow.type] || flow.type }} {{ yuan(flow.amountYuan) }}</text><text class="muted">第 {{ flow.roundNo }} 轮</text></view>
              <text class="muted block">余额 {{ yuan(flow.balanceAfterYuan) }} · {{ shanghai(flow.occurredAt) }}</text><text v-if="flow.reason">{{ flow.reason }}</text>
            </view>
          </view>
        </view>
        <view v-if="!view.funds?.flows?.length" class="empty"><image class="funds-empty" src="/static/funds-empty.jpg" mode="widthFix" /><text class="muted">暂无资金流水</text></view>
        <view class="actions"><button class="ghost" @tap="selectPanel('history')">‹ 返回历史详情</button></view>
      </template>
    </view>
  </template>
</template>

<style scoped>
.detail-hero { min-height: 300rpx; }
.section-rule { font-size: 35rpx; font-weight: 800; margin: 10rpx 0 13rpx; }
.section-rule view { flex: 1; height: 1px; background: #a8bac6; }
.profile { padding: 10rpx; }
.result { margin-top: 12rpx; }
.result-block { margin-top: 4rpx; }
.result-badge { display: inline-block; max-width: 100%; overflow: hidden; padding: 4rpx 14rpx; border-radius: 9rpx; font-size: 25rpx; line-height: 1.3; font-weight: 800; white-space: nowrap; text-overflow: ellipsis; }
.result-victim { background: #d9ede3; color: #245b44; }
.result-scammer { background: #f6dfd8; color: #8f3220; }
.result-legacy, .result-unknown { background: #dce9f2; color: #365469; }
.detail-links { margin: 13rpx 0; }
.detail-links button { background: #dce9f2; color: #102638; text-align: left; font-weight: 700; padding: 11rpx 16rpx; }
.detail-links button + button { margin-top: 9rpx; }
.round { margin-bottom: 10rpx; }
.round-head { background: #dce9f2; color: #102638; text-align: left; font-weight: 750; }
.round-body { padding: 12rpx 8rpx; }
.saved-analysis { display: block; margin-top: 16rpx; }
.funds .tile { padding: 14rpx 6rpx; }
.funds-empty { display: block; width: 126rpx; margin: 0 auto 20rpx; }
</style>
