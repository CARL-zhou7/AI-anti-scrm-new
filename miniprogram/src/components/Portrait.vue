<script setup>
import { computed, ref, watch } from 'vue'
import { asset } from '../api'
import { portraitArt } from '../ui'

const props = defineProps({ person: Object, animated: Boolean, speaking: Boolean, backdrop: String, fill: Boolean })
const emit = defineEmits(['failed'])
const src = ref('')
const art = computed(() => portraitArt(props.person))
const url = computed(() => {
  if (art.value) return ''
  const { avatar, speakingAvatar } = props.person || {}
  return props.speaking ? speakingAvatar?.url || avatar?.url : avatar?.url
})
watch(url, async (url, old, onCleanup) => {
  let current = true
  onCleanup(() => { current = false })
  if (!url) { src.value = ''; return }
  try {
    const path = await asset(url)
    if (current) src.value = path
  } catch {
    if (current) { src.value = ''; emit('failed') }
  }
}, { immediate: true })
</script>

<template>
  <view class="portrait">
    <image v-if="backdrop" class="portrait-backdrop" :src="backdrop" mode="aspectFill" aria-hidden="true" />
    <template v-if="art">
      <image class="portrait-image portrait-frame" :class="{ hidden: animated && speaking }" :src="art + '-closed.webp'" :mode="fill ? 'aspectFill' : 'aspectFit'" :aria-label="person.name" />
      <image v-if="animated" class="portrait-image portrait-frame" :class="{ hidden: !speaking }" :src="art + '-open.webp'" :mode="fill ? 'aspectFill' : 'aspectFit'" aria-hidden="true" />
    </template>
    <image v-else-if="src" class="portrait-image" :src="src" mode="aspectFill" :aria-label="person?.name" @error="src = ''; emit('failed')" />
    <text v-else>{{ person?.name?.slice(0, 1) || '·' }}</text>
  </view>
</template>

<style scoped>
:global(:host) { display: block; }
.portrait { position: relative; display: flex; width: 100%; height: 100%; justify-content: center; align-items: center; overflow: hidden; background: radial-gradient(ellipse at 52% 23%, #456173, #102739 76%); color: #d6e8f3; font-size: 52rpx; }
.portrait-image { width: 100%; height: 100%; }
.portrait-frame { position: absolute; inset: 0; }
.portrait-backdrop { position: absolute; inset: 0; width: 100%; height: 100%; }
.hidden { opacity: 0; }
</style>
