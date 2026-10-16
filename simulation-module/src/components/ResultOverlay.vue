<script setup>
// 演绎终态与需要用户确认的遮罩：正式结束 / 中断 / 到时确认中 / 他处进行中 / 退出确认。
import { computed } from 'vue'
import { LIMIT_REASON_LABELS } from '../core/constants.js'

const props = defineProps({
  kind: { type: String, required: true }, // finished | interrupted | deadline | foreign | confirm-exit
  outcome: { type: Object, default: null },
  interruptionText: { type: String, default: '' },
  interruptionCode: { type: String, default: '' },
  readingRemainingMs: { type: Number, default: null },
  busy: { type: Boolean, default: false },
})
const emit = defineEmits(['report', 'history', 'restart', 'home', 'exit', 'cancel'])

const seconds = computed(() => Math.ceil((props.readingRemainingMs ?? 0) / 1000))
</script>

<template>
  <div class="sim-overlay" role="dialog" aria-modal="true">
    <div v-if="kind === 'finished'" class="sim-overlay__card">
      <div class="sim-overlay__kicker">演绎正式结束</div>
      <h2 class="sim-overlay__title" :class="`sim-outcome--${outcome?.code}`">{{ outcome?.displayText }}</h2>
      <p class="sim-overlay__body">{{ outcome?.reason }}</p>
      <p v-if="outcome?.limitReason" class="sim-overlay__meta">结束原因：{{ LIMIT_REASON_LABELS[outcome.limitReason] }}</p>
      <p class="sim-overlay__meta">{{ seconds > 0 ? `${seconds} 秒后进入复盘，报告可能仍在生成。` : '正在进入复盘…' }}</p>
      <div class="sim-overlay__actions">
        <button class="sim-btn sim-btn--primary" type="button" @click="emit('report')">立即查看复盘</button>
      </div>
    </div>

    <div v-else-if="kind === 'interrupted'" class="sim-overlay__card">
      <div class="sim-overlay__kicker">演绎已中断</div>
      <h2 class="sim-overlay__title">{{ interruptionCode === 'TIME_LIMIT' ? '中断：达到时长上限' : '本次演绎已中断' }}</h2>
      <p class="sim-overlay__body">{{ interruptionText }}</p>
      <p class="sim-overlay__meta">已保存的过程可在历史记录中查看；中断的演绎不能续演，也不生成正式复盘报告。</p>
      <div class="sim-overlay__actions">
        <button class="sim-btn sim-btn--primary" type="button" @click="emit('history')">查看本次记录</button>
        <button class="sim-btn" type="button" @click="emit('restart')">重新体验</button>
        <button class="sim-btn" type="button" @click="emit('home')">返回首页</button>
      </div>
    </div>

    <div v-else-if="kind === 'deadline'" class="sim-overlay__card">
      <div class="sim-overlay__kicker">时长上限</div>
      <h2 class="sim-overlay__title">已到达 10 分钟时长上限</h2>
      <div style="display: flex; align-items: center; gap: 10px">
        <span class="sim-spinner" aria-hidden="true"></span>
        <p class="sim-overlay__body">播放已停止，正在向服务端确认本次演绎结果…</p>
      </div>
    </div>

    <div v-else-if="kind === 'foreign'" class="sim-overlay__card">
      <div class="sim-overlay__kicker">演绎进行中</div>
      <h2 class="sim-overlay__title">这场演绎正在其他页面进行</h2>
      <p class="sim-overlay__body">每个账号同一时间只能进行一场演绎，本页面不会继续播放。可以回到原页面观看，或者中断这场演绎后重新开始。</p>
      <div class="sim-overlay__actions">
        <button class="sim-btn sim-btn--primary" type="button" :disabled="busy" @click="emit('exit')">中断该演绎</button>
        <button class="sim-btn" type="button" @click="emit('home')">返回首页</button>
      </div>
    </div>

    <div v-else-if="kind === 'confirm-exit'" class="sim-overlay__card">
      <div class="sim-overlay__kicker">退出演绎</div>
      <h2 class="sim-overlay__title">确定退出本次演绎？</h2>
      <p class="sim-overlay__body">退出后本次演绎会被中断，计时不会暂停，也不能续演；中断的演绎不生成正式复盘报告。</p>
      <div class="sim-overlay__actions">
        <button class="sim-btn" type="button" @click="emit('cancel')">继续观看</button>
        <button class="sim-btn sim-btn--primary" type="button" :disabled="busy" @click="emit('exit')">确认退出</button>
      </div>
    </div>
  </div>
</template>
