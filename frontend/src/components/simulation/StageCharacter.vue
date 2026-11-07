<script setup lang="ts">
defineProps<{ name: string; closedUrl: string; openUrl: string; spriteUrl?: string; side: 'left' | 'right'; speaking: boolean; mouthOpen: boolean; frozen: boolean }>()
defineEmits<{ error: [] }>()
</script>

<template>
  <figure class="character" :class="[side, { cinematic: spriteUrl, speaking, frozen }]" :data-testid="'stage-character-' + side">
    <div class="frames">
      <img v-if="spriteUrl" class="sprite" :class="{ 'mouth-open': speaking && mouthOpen }" :src="spriteUrl" :alt="name" @error="$emit('error')" />
      <template v-else>
        <img class="frame" :src="closedUrl" :alt="name" @error="$emit('error')" />
        <img class="frame open" :class="{ show: speaking && mouthOpen }" :src="openUrl" alt="" aria-hidden="true" @error="$emit('error')" />
      </template>
    </div>
    <figcaption>{{ name }}</figcaption>
  </figure>
</template>

<style scoped>
.character { position:relative; margin:0; display:flex; flex-direction:column; align-items:center; animation:idle 3s ease-in-out infinite;
  filter:saturate(.88) brightness(.92); transition:filter .3s, transform .3s; }
.character.speaking { filter:none; transform:scale(1.04); animation:talk 0.5s ease-in-out infinite; }
.frames { position:relative; width:min(28vh, 260px, 36vw); aspect-ratio:1; }
.frame { position:absolute; inset:0; width:100%; height:100%; object-fit:cover; border-radius:46% 46% 5px 5px; mask-image:linear-gradient(#000 89%,#0009); }
.frame.open { opacity:0; }
.frame.open.show { opacity:1; }
.cinematic,.cinematic.speaking { animation:none; filter:none; transform:none; }
.cinematic .frames { width:100%; overflow:hidden; mask-image:linear-gradient(#000 84%,transparent); }
.sprite { position:absolute; inset:0 auto auto 0; width:200%; max-width:none; height:100%; }
.sprite.mouth-open { left:-100%; }
figcaption { margin-top:10px; padding:4px 16px; border-radius:4px; font-family:var(--sim-display); font-size:16px; letter-spacing:0.12em; color:var(--sim-paper); }
.left figcaption { background:var(--sim-warn); }
.right figcaption { background:var(--sim-celadon); }
.frozen, .frozen * { animation-play-state:paused !important; }
@keyframes idle { 50% { transform:translateY(-4px) } }
@keyframes talk { 50% { transform:scale(1.04) translateY(-2px) rotate(0.4deg) } 0%, 100% { transform:scale(1.04) } }
@media (prefers-reduced-motion: reduce) { .character, .character.speaking { animation:none; } }
</style>
