<script setup>
import { computed } from 'vue'
import { panelTextSize } from '../presentation'

const props = defineProps({ messages: Array, cast: Object, playing: String, revealedCount: { type: Number, default: 0 }, live: Boolean })
const size = computed(() => props.live
  ? panelTextSize(props.messages.map(line => line.content).join(''), uni.getWindowInfo().windowHeight < 640) + 'rpx'
  : undefined)
const spokenCharacters = computed(() => Array.from(props.messages.find(line => line.id === props.playing)?.content || ''))
</script>

<template>
  <view class="stack" :class="{ 'live-dialogue': live }">
    <view v-for="line in messages" :key="line.id" class="bubble">
      <text class="speaker">{{ line.speaker === 'SCAMMER' ? cast.scammer.name : cast.victim.name }}：</text>
      <view class="line">
        <text v-if="playing === line.id" :style="{ fontSize: size }">
          <text v-for="(character, index) in spokenCharacters" :key="index" class="spoken-character" space="nbsp"
            :class="{ 'spoken-visible': index < revealedCount }" :aria-hidden="index >= revealedCount">{{ character }}</text>
        </text>
        <text v-else :style="{ fontSize: size }">{{ line.content }}</text>
        <text v-if="!live && line.actionText" class="muted block small">{{ line.actionText }}</text>
      </view>
    </view>
  </view>
</template>

<style scoped>
.live-dialogue { flex: 1; display: flex; flex-direction: column; justify-content: space-evenly; }
.speaker { display: inline-block; position: relative; z-index: 1; padding: 4rpx 18rpx; background: #bf5725; color: #fff; font-size: 25rpx; font-weight: 700; border-radius: 10rpx; }
.line { margin-top: -10rpx; padding: 17rpx 18rpx 13rpx; border-radius: 13rpx; background: #d7e6f0; color: #122334; overflow-wrap: anywhere; white-space: pre-wrap; }
.spoken-character { color: transparent; transition: color .24s ease-out; }
.spoken-character.spoken-visible { color: #122334; }
@media (prefers-reduced-motion: reduce) { .spoken-character { transition: none; } }
</style>
