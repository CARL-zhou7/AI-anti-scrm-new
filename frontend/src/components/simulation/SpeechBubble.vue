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
.bubble { position:relative; min-width:0; max-height:28vh; overflow-y:auto; padding:8px 11px; border-radius:0 4px 4px 0; background:#111e30eb; color:#f4f8fc; animation:in .2s ease-out; }
.bubble.left { border-left:3px solid #ff936d; }
.bubble.right { border-left:3px solid #a7d9f0; }
.line { margin:0; font-size:18px; line-height:1.55; font-weight:650; white-space:pre-wrap; overflow-wrap:anywhere; min-height:1.55em; }
.caret { display:inline-block; width:2px; height:1em; margin-left:2px; vertical-align:-2px; background:currentColor; animation:blink 1s steps(1) infinite; }
.action { margin:5px 0 0; font-size:14px; color:#bdd3e5; overflow-wrap:anywhere; }
@keyframes in { from { opacity:0; transform:translateY(4px) } to { opacity:1; transform:none } }
@keyframes blink { 50% { opacity:0 } }
@media(max-width:760px) { .line { font-size:16px; } .action { font-size:13px; } }
@media(prefers-reduced-motion:reduce) { .bubble,.caret { animation:none; } }
</style>
