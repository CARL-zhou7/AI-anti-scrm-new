<script setup lang="ts">
import type { Configuration } from '../../api/types'
import type { DirectorView } from '../../simulation/performanceDirector'
import { stagePortrait } from '../../simulation/stagePortrait'
import StageCharacter from './StageCharacter.vue'
import SpeechBubble from './SpeechBubble.vue'
// Fixed positions: scammer always left, victim always right, regardless of who is speaking.
defineProps<{ configuration: Configuration; view: DirectorView }>()
defineEmits<{ mediaError: [] }>()
</script>

<template>
  <section class="stage-shell" data-testid="simulation-stage" aria-label="双方对话舞台">
    <header class="stage-heading">
      <strong>{{ view.roundNo ? `第 ${view.roundNo} 轮对话` : '情景对话' }}</strong>
      <span>线上消息</span>
    </header>
    <div class="stage">
      <div class="side left">
        <div v-if="view.bubbles.SCAMMER" class="bubble-slot">
          <span class="speaker">{{ configuration.scammer.name }} / 骗子<span v-if="view.speaking === 'SCAMMER'"> · 正在说话</span></span>
          <SpeechBubble :bubble="view.bubbles.SCAMMER" side="left" />
        </div>
        <div class="shot">
          <StageCharacter :name="configuration.scammer.name" :closed-url="configuration.scammer.avatar.url" :open-url="configuration.scammer.speakingAvatar.url" :sprite-url="stagePortrait(configuration.scammer)"
            side="left" :speaking="view.speaking === 'SCAMMER'" :mouth-open="view.mouthOpen" :frozen="view.frozen" @error="$emit('mediaError')" />
        </div>
      </div>
      <!-- Signature: the call line between the two phones; a pulse travels toward whoever is listening. -->
      <div class="wire" :class="{ toRight: view.speaking === 'SCAMMER', toLeft: view.speaking === 'VICTIM', frozen: view.frozen }" aria-hidden="true">
        <span class="pulse" />
      </div>
      <div class="side right">
        <div v-if="view.bubbles.VICTIM" class="bubble-slot">
          <span class="speaker">{{ configuration.victim.name }} / 受害者<span v-if="view.speaking === 'VICTIM'"> · 正在说话</span></span>
          <SpeechBubble :bubble="view.bubbles.VICTIM" side="right" />
        </div>
        <div class="shot">
          <StageCharacter :name="configuration.victim.name" :closed-url="configuration.victim.avatar.url" :open-url="configuration.victim.speakingAvatar.url" :sprite-url="stagePortrait(configuration.victim)"
            side="right" :speaking="view.speaking === 'VICTIM'" :mouth-open="view.mouthOpen" :frozen="view.frozen" @error="$emit('mediaError')" />
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.stage {
  position:relative; display:grid; grid-template-columns:minmax(0,1fr) 1px minmax(0,1fr); gap:18px;
  padding:16px; min-height:clamp(290px,40vh,405px);
  background:linear-gradient(0deg,#0b1525ef,transparent 60%),url('../../assets/studio-set.jpg') center 46%/cover no-repeat;
}
.stage-shell { border:1px solid #596f87; border-radius:5px; overflow:hidden; }
.stage-heading { display:flex; align-items:center; justify-content:space-between; gap:8px; padding:7px 13px; background:#192d46; border-bottom:1px solid #526a83; color:#dcebf6; font-size:14px; }
.stage-heading strong::before { content:''; display:inline-block; width:7px; height:7px; margin-right:8px; border-radius:50%; background:#ff9875; vertical-align:2px; }
.stage-heading span { font-size:13px; color:#c7dceb; }
.stage::before { content:''; position:absolute; inset:9px; border:1px solid #adc4d52e; pointer-events:none; }
.side { display:flex; flex-direction:column-reverse; justify-content:flex-start; gap:8px; min-width:0; z-index:1; }
.shot { display:contents; }
.shot > :deep(.character) { align-self:flex-start; margin-inline-start:clamp(0px,4vw,58px); }
.right .shot > :deep(.character) { align-self:flex-end; margin-inline-start:0; margin-inline-end:clamp(0px,4vw,58px); }
.shot > :deep(.character.cinematic),.right .shot > :deep(.character.cinematic) { width:min(100%,320px,32vh); margin-inline:0; }
.side:has(.cinematic) .bubble-slot { position:relative; z-index:1; margin-top:-26px; }
.side:has(.bubble-slot) .shot :deep(figcaption) { display:none; }
.bubble-slot { display:grid; align-content:end; min-width:0; }
.speaker { justify-self:start; background:#6c362e; color:#fff; font-size:14px; font-weight:700; padding:4px 7px; overflow-wrap:anywhere; }
.right .speaker { background:#274f6d; }
.wire { position:relative; margin:0; background:repeating-linear-gradient(#a7bdd47d 0 5px,transparent 5px 10px); }
.pulse { position:absolute; left:50%; top:52%; width:9px; height:9px; margin:-4px 0 0 -4px; border-radius:50%; background:#ffb78f; opacity:0; box-shadow:0 0 12px #ffb78f; }
.toRight .pulse,.toLeft .pulse { opacity:1; animation:carry 1.2s ease-in-out infinite; }
.toRight .pulse { --dir:1; } .toLeft .pulse { --dir:-1; }
.frozen .pulse { animation-play-state:paused; }
@keyframes carry { from { transform:translateX(calc(var(--dir) * -18px)) } to { transform:translateX(calc(var(--dir) * 18px)) } }
@media(min-width:761px) { .stage:has(.cinematic) { padding-block:12px; } }
@media(min-width:1001px) and (min-height:700px) {
  .stage-shell { display:grid; grid-template-rows:auto minmax(0,1fr); }
  .stage { min-height:0; grid-template-rows:minmax(0,1fr) auto; gap:0 14px; padding-block:12px; }
  .side { display:grid; grid-template-rows:subgrid; grid-row:1/-1; }
  .side.left { grid-column:1; }
  .side.right { grid-column:3; }
  .wire { grid-area:1 / 2 / -1 / 3; }
  .shot { display:flex; align-items:end; grid-row:1; container-type:size; }
  .right .shot { justify-content:end; }
  .shot > :deep(.character),.right .shot > :deep(.character) { align-self:end; width:min(100%,260px,100cqh); margin-inline:0; }
  .shot > :deep(.character.cinematic),.right .shot > :deep(.character.cinematic) { width:min(100%,340px,100cqh); }
  .shot :deep(.frames) { width:100%; }
  .shot :deep(figcaption) { position:absolute; bottom:0; }
  .side .bubble-slot,.side:has(.cinematic) .bubble-slot { grid-row:2; grid-template-rows:auto 1fr; margin-top:0; }
  .bubble-slot :deep(.bubble) { max-height:min(20dvh,174px); overscroll-behavior:contain; scrollbar-width:thin; }
}
@media(min-width:1001px) and (700px <= height <= 820px) {
  .stage,.stage:has(.cinematic) { padding-block:7px; }
  .stage-heading { padding-block:4px; }
  .speaker { padding-block:2px; }
  .bubble-slot :deep(.bubble) { max-height:17dvh; padding-block:5px; }
  .bubble-slot :deep(.line) { font-size:16px; line-height:1.45; }
  .bubble-slot :deep(.action) { font-size:13px; line-height:1.4; }
}
@media(max-width:760px) {
  .stage { gap:6px; padding:9px; min-height:270px; }
  .shot > :deep(.character),.right .shot > :deep(.character) { align-self:center; margin-inline:0; }
  .speaker { font-size:12px; }
}
@media(prefers-reduced-motion:reduce) { .pulse { animation:none!important; } }
</style>
