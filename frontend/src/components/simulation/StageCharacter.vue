<script setup lang="ts">
// Half-length portrait with two stacked, preloaded frames; the mouth flap only swaps opacity, so no re-decode.
defineProps<{ name: string; closedUrl: string; openUrl: string; side: 'left' | 'right'; speaking: boolean; mouthOpen: boolean; frozen: boolean }>()
defineEmits<{ error: [] }>()
</script>

<template>
  <figure class="character" :class="[side, { speaking, frozen }]" :data-testid="'stage-character-' + side">
    <div class="frames">
      <img class="frame" :src="closedUrl" :alt="name" @error="$emit('error')" />
      <img class="frame open" :class="{ show: speaking && mouthOpen }" :src="openUrl" alt="" aria-hidden="true" @error="$emit('error')" />
    </div>
    <figcaption>{{ name }}</figcaption>
  </figure>
</template>

<style scoped>
.character { position:relative; margin:0; display:flex; flex-direction:column; align-items:center; animation:idle 3s ease-in-out infinite;
  filter:saturate(0.75) brightness(0.82); transition:filter .3s, transform .3s; }
.character.speaking { filter:none; transform:scale(1.04); animation:talk 0.5s ease-in-out infinite; }
.frames { position:relative; width:min(30vh, 260px); aspect-ratio:1; }
.frame { position:absolute; inset:0; width:100%; height:100%; object-fit:cover; border-radius:50% 50% 12px 12px; }
.frame.open { opacity:0; }
.frame.open.show { opacity:1; }
figcaption { margin-top:10px; padding:4px 16px; border-radius:999px; font-family:var(--sim-display); font-size:22px; letter-spacing:0.12em; color:var(--sim-paper); }
.left figcaption { background:var(--sim-warn); }
.right figcaption { background:var(--sim-celadon); }
.frozen, .frozen * { animation-play-state:paused !important; }
@keyframes idle { 50% { transform:translateY(-4px) } }
@keyframes talk { 50% { transform:scale(1.04) translateY(-2px) rotate(0.4deg) } 0%, 100% { transform:scale(1.04) } }
@media (prefers-reduced-motion: reduce) { .character, .character.speaking { animation:none; } }
</style>
