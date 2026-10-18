<script setup lang="ts">
import { computed } from 'vue'
import { sliceCodePoints } from '../../simulation/segmentReveal'
import type { Bubble } from '../../simulation/performanceDirector'
const props = defineProps<{ bubble: Bubble; side: 'left' | 'right' }>()
const shown = computed(() => sliceCodePoints(props.bubble.content, props.bubble.visible))
</script>

<template>
  <div class="bubble" :class="side" :data-testid="'bubble-' + side">
    <p class="line" :aria-label="bubble.done ? bubble.content : undefined">{{ shown }}<span v-if="!bubble.done" class="caret" aria-hidden="true" /></p>
    <p v-if="bubble.actionText && bubble.done" class="action">动作：{{ bubble.actionText }}</p>
  </div>
</template>

<style scoped>
.bubble { position:relative; max-width:100%; max-height:28vh; overflow-y:auto; padding:16px 20px; border-radius:16px; background:var(--sim-paper); color:var(--sim-ink);
  box-shadow:0 10px 24px rgb(0 0 0 / 0.3); animation:in .25s ease-out; }
.bubble.left { border-left:5px solid var(--sim-warn); border-top-left-radius:4px; }
.bubble.right { border-right:5px solid var(--sim-celadon); border-top-right-radius:4px; }
.line { margin:0; font-size:20px; line-height:1.7; white-space:pre-wrap; overflow-wrap:anywhere; min-height:1.7em; }
.caret { display:inline-block; width:2px; height:1em; margin-left:2px; vertical-align:-2px; background:currentColor; animation:blink 1s steps(1) infinite; }
.action { margin:8px 0 0; font-size:14px; font-style:italic; color:#6b6f80; }
@keyframes in { from { opacity:0; transform:translateY(6px) } to { opacity:1; transform:none } }
@keyframes blink { 50% { opacity:0 } }
</style>
