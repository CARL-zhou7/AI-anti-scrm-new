<script setup>
// 视觉小说式对话框：显示当前发言角色与完整台词，已播报的部分随语音进度加深（字声对应）。
// 台词按纯文本渲染（不使用 v-html），完整文本始终在 DOM 中，供 TEXT_RENDER 检测与阅读。
import { computed } from 'vue'

const props = defineProps({
  message: { type: Object, default: null },
  speakerName: { type: String, default: '' },
  progress: { type: Number, default: 0 },
  speaking: { type: Boolean, default: false },
  waiting: { type: Object, default: null }, // { speaker, text }
  muted: { type: Boolean, default: false },
  boxRef: { type: Function, default: null },
})

const chars = computed(() => Array.from(props.message?.content ?? ''))
const split = computed(() => {
  const n = props.speaking ? Math.round(chars.value.length * Math.min(1, Math.max(0, props.progress))) : chars.value.length
  return { spoken: chars.value.slice(0, n).join(''), rest: chars.value.slice(n).join('') }
})
const tagText = computed(() => {
  if (!props.message) return props.waiting ? '演绎' : ''
  return `${props.message.speaker === 'SCAMMER' ? '骗子' : '受害者'} · ${props.speakerName}`
})
</script>

<template>
  <section
    class="sim-dialogue"
    :class="message && (speaking || !waiting) ? `sim-dialogue--${message.speaker}` : null"
    :ref="(el) => boxRef?.(el)"
    aria-live="polite"
  >
    <span v-if="tagText" class="sim-dialogue__tag" :class="message ? `sim-dialogue__tag--${message.speaker}` : 'sim-dialogue__tag--system'">
      {{ tagText }}
    </span>
    <div v-if="waiting && !speaking" class="sim-dialogue__waiting">
      <span class="sim-typing" aria-hidden="true"><i></i><i></i><i></i></span>
      <span>{{ waiting.text }}</span>
    </div>
    <div v-else-if="message" :data-message-id="message.id">
      <p class="sim-dialogue__text" data-role="text"><span class="sim-dialogue__spoken">{{ split.spoken }}</span><span class="sim-dialogue__rest">{{ split.rest }}</span></p>
      <div class="sim-dialogue__footer">
        <span>第 {{ message.roundNo }} 轮</span>
        <span v-if="speaking">{{ muted ? '静音播放中' : '语音播放中' }}</span>
      </div>
    </div>
    <div v-else class="sim-dialogue__waiting">
      <span>等待演绎内容</span>
    </div>
  </section>
</template>
