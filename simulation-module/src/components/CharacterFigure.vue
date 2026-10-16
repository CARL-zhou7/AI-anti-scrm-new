<script setup>
// 单侧人物：情境背景 + 立绘（服务端快照中的头像）+ 名牌。
// 动画状态：hidden（开场前）/ entering（入场）/ idle（待机）/ speaking（说话）/ listening（对方说话时）。
// 说话动画在有音量数据时随语音强弱变化（--sim-level），否则使用固定节奏（is-rhythm）。
import { computed } from 'vue'

const props = defineProps({
  profile: { type: Object, required: true },
  side: { type: String, required: true, validator: (v) => v === 'scammer' || v === 'victim' },
  state: { type: String, default: 'idle' },
  level: { type: Number, default: null },
  phoneBuzzing: { type: Boolean, default: false },
  // 接收人物根元素，供呈现检测核对动画
  figureRef: { type: Function, default: null },
})

function setFigure(el) {
  props.figureRef?.(el)
}

const isNight = computed(() => /晚|夜|凌晨/.test(props.profile.contextTime || ''))
const roleLabel = computed(() => (props.side === 'scammer' ? '骗子' : '受害者'))
const meta = computed(() => [props.profile.age ? `${props.profile.age}岁` : '', props.profile.gender, props.profile.identity].filter(Boolean).join(' · '))
const figureClass = computed(() => [
  'sim-figure',
  props.side === 'scammer' ? 'sim-figure--left' : 'sim-figure--right',
  `is-${props.state}`,
  { 'is-rhythm': props.state === 'speaking' && props.level === null },
])
const levelStyle = computed(() => (props.level === null ? {} : { '--sim-level': props.level.toFixed(3) }))
</script>

<template>
  <div class="sim-scene" :class="[`sim-scene--${side}`, isNight ? 'is-night' : 'is-day']">
    <div class="sim-scene__window" aria-hidden="true"></div>
    <div class="sim-scene__lamp" aria-hidden="true"></div>
    <div class="sim-scene__desk" aria-hidden="true"></div>
    <div class="sim-phone" :class="{ 'is-buzzing': phoneBuzzing }" aria-hidden="true"></div>
    <span class="sim-scene__place">{{ profile.contextPlace }} · {{ profile.contextTime }}</span>

    <div :class="figureClass" :style="levelStyle" :data-state="state" :ref="setFigure">
      <div class="sim-figure__body">
        <div class="sim-figure__halo" aria-hidden="true"></div>
        <div class="sim-figure__portrait">
          <img :src="profile.avatar.url" :alt="`${roleLabel}${profile.name}的形象`" draggable="false" />
        </div>
        <div class="sim-figure__voice" aria-hidden="true"><i></i><i></i><i></i><i></i></div>
      </div>
      <div class="sim-plate">
        <span class="sim-plate__role">{{ roleLabel }}</span>
        <span class="sim-plate__name">{{ profile.name }}</span>
        <span class="sim-plate__meta">{{ meta }}</span>
      </div>
    </div>
  </div>
</template>
