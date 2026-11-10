<script setup>
import { computed, ref, watch } from 'vue'
import { onHide, onLoad, onShow, onUnload } from '@dcloudio/uni-app'
import { get, pageUrl, runPath, write } from '../api'
import { errorText, go, home, yuan } from '../ui'
import { outcomeReason, outcomeVerdict } from '../outcome'
import Trend from '../components/Trend.vue'

const view = ref(null), state = ref(null), panel = ref('overview'), riskOpen = ref(false)
const loading = ref(true), retrying = ref(false), error = ref('')
let id, timer, visible = false, request = 0
const content = computed(() => view.value?.content)
const generating = computed(() => ['PENDING', 'RUNNING'].includes(view.value?.report?.status))
const failed = computed(() => view.value?.report?.status === 'FAILED')
const cover = computed(() => content.value ? (panel.value === 'overview' ? '/static/report-overview.jpg' : '/static/report-detail.jpg')
  : failed.value ? '/static/report-failed.jpg'
  : view.value && !generating.value ? '/static/report-none.jpg' : '/static/report-pending.jpg')
const resultTitle = computed(() => {
  const outcome = view.value?.outcome
  if (outcome?.winning === 'VICTIM') return '识破骗局'
  if (outcome?.winning === 'SCAMMER') return '骗局得逞'
  return outcome?.displayText || view.value?.ending?.displayText || '分析复盘'
})
const trust = computed(() => content.value?.finalTrustScore ?? view.value?.outcome?.finalTrustScore ?? state.value?.finalTrustScore)
const funds = computed(() => content.value?.fundsSummary || (state.value?.funds?.fundsRecorded ? state.value.funds : null))
const trustSeries = computed(() => (view.value?.riskSeries || []).map(point => ({ roundNo: point.roundNo, score: point.trustChange?.after })))
const verdict = computed(() => content.value ? {
  ...outcomeVerdict(view.value?.outcome, content.value),
  reason: outcomeReason(view.value?.outcome, view.value?.ending)
} : null)
const unavailable = computed(() => ({
  SIMULATION_RUNNING: '演绎尚未结束，完成后可查看复盘。',
  SIMULATION_INTERRUPTED: '可在历史详情中回看已保存的对话。',
  TECHNICAL_INTERRUPT: '演绎遇到技术中断，可回看已保存的过程。',
  NO_DIALOGUE: '本场暂无已保存的对话。',
  LEGACY_NO_REPORT: '这场历史演绎没有保存正式报告。'
})[view.value?.unavailableReason] || '可以回看已保存的演绎过程。')
watch([view, panel], () => {
  const title = content.value ? (panel.value === 'overview' ? '复盘概览' : '复盘详情')
    : failed.value ? '报告失败' : view.value && !generating.value ? '无正式报告' : '分析复盘'
  if (visible) uni.setNavigationBarTitle({ title })
})

function schedule() {
  clearTimeout(timer)
  if (visible && generating.value && !view.value.unavailableReason) timer = setTimeout(load, 2200)
}
async function load() {
  const version = ++request
  error.value = ''
  try {
    const result = await get(runPath(id, '/report'))
    if (request === version) { view.value = result; schedule() }
  } catch (cause) { if (request === version) error.value = errorText(cause) }
  finally { if (request === version) loading.value = false }
}
async function retry() {
  if (retrying.value || !view.value?.report?.canRetry) return
  const version = ++request
  retrying.value = true; error.value = ''
  try {
    const result = await write(runPath(id, '/report/retry'))
    if (request === version) { view.value = result; schedule() }
  } catch (cause) { if (request === version) error.value = errorText(cause) }
  finally { retrying.value = false }
}
function detail(round) {
  uni.navigateTo({ url: pageUrl('detail', id) + (round ? '&round=' + encodeURIComponent(round) : '') })
}
function history() { uni.reLaunch({ url: '/pages/history' }) }
function newRun() { uni.redirectTo({ url: '/pages/casting' }) }
onLoad(query => {
  id = query.id || ''
  void get(runPath(id)).then(result => { state.value = result }).catch(() => {})
})
onShow(() => { visible = true; void load() })
function hide() { visible = false; clearTimeout(timer); request++ }
onHide(hide)
onUnload(hide)
</script>

<template>
  <view class="hero review-cover" :class="{ 'review-cover-short': content }">
    <image class="hero-art" :src="cover" mode="aspectFill" />
    <view v-if="content" class="hero-copy"><text class="title">{{ resultTitle }}</text><text v-if="state" class="eyebrow">{{ state.configuration.scenario.name }}</text></view>
  </view>
  <view class="surface overlap">
    <text v-if="loading" class="muted block center">正在读取复盘报告…</text>
    <view v-if="error">
      <text class="error">{{ error }}</text><button v-if="!view || generating" class="ghost" @tap="load">重新读取</button>
    </view>
    <view v-if="view && generating && !view.unavailableReason" class="empty report-state">
      <image class="report-illustration report-document" src="/static/report-doc.jpg" mode="widthFix" />
      <view class="report-spinner" /><text class="state-title">报告生成中</text><text class="muted">离开页面不影响生成进度</text>
      <view class="actions"><button @tap="history">返回历史</button></view>
    </view>
    <view v-else-if="failed" class="empty report-state">
      <image class="report-illustration" src="/static/report-warning.jpg" mode="widthFix" />
      <text class="state-title">报告生成失败</text><text class="muted block">{{ view.report.error?.message || '生成过程中出现异常，请重新生成。' }}</text>
      <view class="actions"><button v-if="view.report.canRetry" :disabled="retrying" :loading="retrying" @tap="retry">重新生成</button><button class="ghost" @tap="detail()">查看历史详情</button></view>
    </view>
    <view v-else-if="content" class="report-content">
      <view class="tabs"><text class="tab" :class="{ active: panel === 'overview' }" @tap="panel = 'overview'">复盘概览</text><text class="tab" :class="{ active: panel === 'details' }" @tap="panel = 'details'">复盘详情</text></view>
      <template v-if="panel === 'overview'">
        <view class="row between result-line">
          <text class="final-trust">最终受骗倾向分　<text class="trust">{{ trust ?? '—' }}</text></text>
          <text v-if="verdict" class="result-badge" :class="'result-' + verdict.tone">{{ verdict.title }}</text>
        </view>
        <text v-if="verdict?.reason" class="result-reason">{{ verdict.reason }}</text>
        <view v-if="funds" class="row funds">
          <view class="tile grow"><text class="small block">累计支付</text><text class="bold">{{ yuan(funds.totalPaidYuan) }}</text></view>
          <view class="tile grow"><text class="small block">实际损失</text><text class="bold">{{ yuan(funds.actualLossYuan) }}</text></view>
        </view>
        <Trend label="受骗倾向分走势" :series="trustSeries" />
        <button class="ghost actions" @tap="riskOpen = !riskOpen">本轮风险走势 {{ riskOpen ? '⌃' : '⌄' }}</button>
        <view v-if="riskOpen" class="motion-fade"><Trend label="风险走势" :series="view.riskSeries" selectable @select="detail" /></view>
        <view class="actions"><button @tap="panel = 'details'">查看复盘详情</button></view>
      </template>
      <template v-else>
        <view class="row section-rule"><text>过程概述</text><view /></view><view class="tile">{{ content.summary }}</view>
        <template v-if="content.keyNodes?.length">
          <view class="row section-rule"><text>关键节点</text><view /></view>
          <view class="stack">
            <view v-for="(node, index) in content.keyNodes" :key="index" class="node">
              <text class="bold block">{{ node.title || '第 ' + node.roundNo + ' 轮' }}</text>
              <text v-if="node.quote" class="muted block">「{{ node.quote }}」</text>
              <text class="block">{{ node.analysis }}</text>
              <button class="link" @tap="detail(node.roundNo)">查看当轮原话 ›</button>
            </view>
          </view>
        </template>
        <template v-if="content.roundAnalyses?.length">
          <text class="heading">逐轮分析</text>
          <view v-for="item in content.roundAnalyses" :key="item.roundNo" class="node"><text class="bold block">第 {{ item.roundNo }} 轮</text><text>{{ item.analysis }}</text></view>
          <text class="heading">话术与结果</text>
          <text class="block">{{ content.speechAnalysis }}</text><text class="block">{{ content.victimBehaviorAnalysis }}</text><text class="block">{{ content.outcomeAnalysis }}</text>
        </template>
        <template v-if="content.preventionAdvice?.length">
          <view class="row section-rule"><text>防范建议</text><view /></view>
          <view class="row tile advice"><text class="advice-icon">!</text><view class="grow"><text v-for="tip in content.preventionAdvice" :key="tip" class="block">{{ tip }}</text></view></view>
        </template>
        <view class="row report-links"><button class="grow" @tap="detail()">查看历史详情</button><button class="ghost grow" @tap="home">返回首页</button></view>
      </template>
    </view>
    <view v-else-if="view" class="empty report-state">
      <image class="report-illustration" src="/static/report-unavailable.jpg" mode="widthFix" />
      <text class="state-title">本次未生成正式报告</text><text class="muted block">{{ unavailable }}</text>
      <view class="actions"><button @tap="detail()">查看历史详情</button><button class="ghost" @tap="newRun">重新开始体验</button></view>
    </view>
    <button v-if="!view && !loading" class="link" @tap="go('history')">返回历史</button>
  </view>
</template>

<style scoped>
.review-cover { min-height: 388rpx; }
.review-cover-short { min-height: 300rpx; }
.review-cover-short .hero-art { left: 34%; width: 66%; }
.review-cover-short .hero-copy { padding: 52rpx 26rpx 41rpx; background: linear-gradient(90deg, #0a2135e3, #0a213500); }
.review-cover-short .title { font-size: 58rpx; margin-bottom: 12rpx; }
.report-state { padding: 23rpx 0 35rpx; }
.report-state .actions { margin-top: 65rpx; }
.report-illustration { display: block; width: 215rpx; margin: 0 auto 30rpx; }
.report-document { width: 380rpx; margin-bottom: 12rpx; }
.report-spinner { width: 62rpx; height: 62rpx; border: 8rpx dotted #e28b59; border-left-color: #f7dfcf; border-radius: 50%; margin: 10rpx auto 20rpx; animation: spin 1.25s linear infinite; }
.state-title { display: block; font-size: 51rpx; line-height: 1.2; font-weight: 800; color: #111d29; margin: 10rpx 0; }
.final-trust { display: block; font-size: 36rpx; font-weight: 800; }
.trust { color: #b74f23; font-size: 64rpx; }
.result-badge { flex-shrink: 0; max-width: 300rpx; overflow: hidden; padding: 4rpx 14rpx; border-radius: 9rpx; font-size: 25rpx; line-height: 1.3; font-weight: 800; white-space: nowrap; text-overflow: ellipsis; }
.result-victim { background: #d9ede3; color: #245b44; }
.result-scammer { background: #f6dfd8; color: #8f3220; }
.result-legacy, .result-unknown { background: #dce9f2; color: #365469; }
.result-reason { display: block; margin-top: 2rpx; font-size: 23rpx; line-height: 1.5; color: #4e6576; }
.funds { margin-top: 18rpx; }
.report-links { margin-top: 24rpx; }
.section-rule { margin: 22rpx 0 13rpx; font-size: 36rpx; font-weight: 800; }
.section-rule view { flex: 1; height: 1px; background: #a8bac6; }
.node { position: relative; padding: 4rpx 0 4rpx 35rpx; border-left: 3rpx solid #df925e; margin-left: 12rpx; }
.node::before { content: ''; position: absolute; width: 18rpx; height: 18rpx; border-radius: 50%; border: 3rpx solid #d27546; background: #f5e6d9; left: -12rpx; top: 10rpx; }
.advice { background: #f8e6d9; align-items: flex-start; }
.advice-icon { color: #fff; background: #ba4e23; border-radius: 9rpx 9rpx 17rpx 17rpx; padding: 0 16rpx; font-weight: 800; }
@keyframes spin { to { transform: rotate(360deg); } }
</style>
