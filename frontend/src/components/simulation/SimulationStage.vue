<script setup lang="ts">
import type { Configuration } from '../../api/types'
import type { DirectorView } from '../../simulation/performanceDirector'
import StageCharacter from './StageCharacter.vue'
import SpeechBubble from './SpeechBubble.vue'
// Fixed positions: scammer always left, victim always right, regardless of who is speaking.
defineProps<{ configuration: Configuration; view: DirectorView }>()
defineEmits<{ mediaError: [] }>()
</script>

<template>
  <div class="stage" data-testid="simulation-stage">
    <div class="side left">
      <div class="bubble-slot"><SpeechBubble v-if="view.bubbles.SCAMMER" :bubble="view.bubbles.SCAMMER" side="left" /></div>
      <StageCharacter :name="configuration.scammer.name" :closed-url="configuration.scammer.avatar.url" :open-url="configuration.scammer.speakingAvatar.url"
        side="left" :speaking="view.speaking === 'SCAMMER'" :mouth-open="view.mouthOpen" :frozen="view.frozen" @error="$emit('mediaError')" />
    </div>
    <!-- Signature: the call line between the two phones; a pulse travels toward whoever is listening. -->
    <div class="wire" :class="{ toRight: view.speaking === 'SCAMMER', toLeft: view.speaking === 'VICTIM', frozen: view.frozen }" aria-hidden="true">
      <span class="pulse" />
    </div>
    <div class="side right">
      <div class="bubble-slot"><SpeechBubble v-if="view.bubbles.VICTIM" :bubble="view.bubbles.VICTIM" side="right" /></div>
      <StageCharacter :name="configuration.victim.name" :closed-url="configuration.victim.avatar.url" :open-url="configuration.victim.speakingAvatar.url"
        side="right" :speaking="view.speaking === 'VICTIM'" :mouth-open="view.mouthOpen" :frozen="view.frozen" @error="$emit('mediaError')" />
    </div>
  </div>
</template>

<style scoped>
.stage { position:relative; height:100%; display:grid; grid-template-columns:minmax(0, 1fr) 56px minmax(0, 1fr); padding:20px 32px 12px; }
.side { display:flex; flex-direction:column; justify-content:flex-end; gap:16px; min-width:0; }
.left { align-items:flex-start; } .right { align-items:flex-end; }
.bubble-slot { width:min(100%, 460px); min-height:120px; display:flex; align-items:flex-end; }
.right .bubble-slot { justify-content:flex-end; }
.side::after { content:''; position:absolute; bottom:0; width:42%; height:55%; pointer-events:none; opacity:0.18; filter:blur(40px); z-index:-1; }
.left::after { left:0; background:var(--sim-warn); } .right::after { right:0; background:var(--sim-celadon); }
.wire { position:relative; margin:10% auto; width:2px; background:repeating-linear-gradient(to bottom, #59628d 0 8px, transparent 8px 14px); }
.pulse { position:absolute; left:50%; top:50%; width:14px; height:14px; margin:-7px 0 0 -7px; border-radius:50%; background:var(--sim-gold); opacity:0;
  box-shadow:0 0 16px var(--sim-gold); }
.toRight .pulse, .toLeft .pulse { opacity:1; animation:carry 1.2s ease-in-out infinite; }
.toRight .pulse { --dir:1 } .toLeft .pulse { --dir:-1 }
.frozen .pulse { animation-play-state:paused; }
@keyframes carry { from { transform:translateX(calc(var(--dir) * -28px)) scale(0.6) } to { transform:translateX(calc(var(--dir) * 28px)) scale(1) } }
@media (prefers-reduced-motion: reduce) { .pulse { animation:none !important; } }
@media (max-width:760px) { .stage { grid-template-columns:1fr 20px 1fr; padding:12px; } }
</style>
