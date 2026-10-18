<script setup lang="ts">
import type { Scenario } from '../../api/types'
// Pre-made scene animation per fixed scenario code (scene_intro_version 1); the picture never depends on editable
// scenario text, which is overlaid from the frozen snapshot.
defineProps<{ scenario: Scenario; frozen: boolean }>()
const emit = defineEmits<{ done: [] }>()
// Scoped keyframes get a hash suffix, so match by prefix.
function ended(event: AnimationEvent) { if (event.animationName.startsWith('scene-timeline')) emit('done') }
</script>

<template>
  <section class="opening-scene" :class="[scenario.code.toLowerCase(), { frozen }]" data-testid="opening-scene" aria-live="polite">
    <svg class="scene-art" viewBox="0 0 640 360" aria-hidden="true">
      <template v-if="scenario.code === 'TASK_REBATE'">
        <rect class="desk" x="0" y="268" width="640" height="92" />
        <rect class="phone" x="250" y="70" width="140" height="240" rx="18" />
        <rect class="screen" x="262" y="92" width="116" height="190" rx="6" />
        <g class="notice n1"><rect x="270" y="104" width="100" height="34" rx="6" /><text x="278" y="126">点赞任务 +¥5</text></g>
        <g class="notice n2"><rect x="270" y="146" width="100" height="34" rx="6" /><text x="278" y="168">垫付返利 +¥30</text></g>
        <g class="notice n3"><rect x="270" y="188" width="100" height="34" rx="6" /><text x="278" y="210">追加解冻 ¥800</text></g>
        <circle class="coin c1" cx="170" cy="230" r="14" /><circle class="coin c2" cx="470" cy="210" r="11" />
      </template>
      <template v-else-if="scenario.code === 'FAKE_INVESTMENT'">
        <rect class="desk" x="0" y="268" width="640" height="92" />
        <rect class="monitor" x="150" y="60" width="340" height="200" rx="10" />
        <polyline class="trend" points="175,230 220,215 260,222 300,190 340,170 380,140 420,110 465,90" />
        <g class="candles"><rect x="214" y="200" width="10" height="26" /><rect x="294" y="176" width="10" height="30" /><rect x="374" y="128" width="10" height="34" /><rect x="454" y="78" width="10" height="30" /></g>
        <g class="alert"><rect x="230" y="112" width="180" height="56" rx="8" /><text x="246" y="146">提现需先缴纳解冻费</text></g>
      </template>
      <template v-else>
        <rect class="desk" x="0" y="268" width="640" height="92" />
        <rect class="phone" x="250" y="60" width="140" height="250" rx="18" />
        <rect class="screen" x="262" y="82" width="116" height="200" rx="6" />
        <g class="call"><circle cx="320" cy="140" r="26" /><text x="291" y="196">陌生号码来电</text></g>
        <g class="chat c1"><rect x="268" y="214" width="96" height="26" rx="12" /><text x="278" y="232">妈，是我…</text></g>
        <g class="chat c2"><rect x="268" y="246" width="96" height="26" rx="12" /><text x="278" y="264">急用钱，别告诉别人</text></g>
        <circle class="ring r1" cx="320" cy="140" r="40" /><circle class="ring r2" cx="320" cy="140" r="58" />
      </template>
    </svg>
    <div class="scene-copy">
      <span class="scene-eyebrow">情景介绍</span>
      <h1>{{ scenario.name }}</h1>
      <p>{{ scenario.description }}</p>
    </div>
    <div class="scene-timeline" @animationend="ended" />
  </section>
</template>

<style scoped>
.opening-scene { position:relative; height:100%; display:grid; grid-template-rows:1fr auto; place-items:center; padding:32px 24px 40px; color:var(--sim-paper); }
.scene-art { width:min(640px, 90vw); height:auto; }
.desk { fill:#1a2038; }
.phone, .monitor { fill:#11162a; stroke:#56608a; stroke-width:2; }
.screen { fill:#2c3560; }
text { fill:#f3efe5; font-size:11px; }
.notice rect, .chat rect { fill:#3d4878; }
.notice, .chat, .alert, .call { opacity:0; animation:pop 0.6s ease-out forwards; }
.n1 { animation-delay:1.2s } .n2 { animation-delay:3s } .n3 { animation-delay:5s }
.n3 rect, .alert rect { fill:var(--sim-warn); }
.coin { fill:var(--sim-gold); animation:float 2.4s ease-in-out infinite; }
.c2 { animation-delay:0.8s; }
.trend { fill:none; stroke:var(--sim-celadon); stroke-width:3; stroke-dasharray:400; stroke-dashoffset:400; animation:draw 4s ease-out 0.8s forwards; }
.candles rect { fill:var(--sim-celadon); opacity:0.7; }
.alert { animation-delay:5.6s; }
.call { animation-delay:0.6s; } .call circle { fill:var(--sim-celadon); }
.chat.c1 { animation-delay:3.2s } .chat.c2 { animation-delay:5s }
.ring { fill:none; stroke:var(--sim-celadon); stroke-width:2; opacity:0; animation:ring 1.6s ease-out 0.6s 3; }
.r2 { animation-delay:1s; }
.scene-copy { max-width:640px; text-align:center; opacity:0; animation:pop 0.8s ease-out 0.4s forwards; }
.scene-eyebrow { font-size:12px; letter-spacing:0.3em; color:var(--sim-gold); }
.scene-copy h1 { margin:8px 0 10px; font-family:var(--sim-display); font-size:40px; font-weight:400; letter-spacing:0.08em; }
.scene-copy p { margin:0; color:#cfd3e4; line-height:1.8; }
/* Master timeline: 10 s, its animationend hands over to the character cards. */
.scene-timeline { position:absolute; left:0; bottom:0; height:3px; width:100%; transform-origin:left; background:var(--sim-gold); animation:scene-timeline 10s linear forwards; }
.frozen *, .frozen { animation-play-state:paused !important; }
@keyframes scene-timeline { from { transform:scaleX(0) } to { transform:scaleX(1) } }
@keyframes pop { from { opacity:0; transform:translateY(8px) } to { opacity:1; transform:none } }
@keyframes float { 50% { transform:translateY(-10px) } }
@keyframes draw { to { stroke-dashoffset:0 } }
@keyframes ring { from { opacity:0.8; transform:scale(0.8); transform-origin:320px 140px } to { opacity:0; transform:scale(1.3); transform-origin:320px 140px } }
@media (prefers-reduced-motion: reduce) { .coin, .ring { animation:none; } }
</style>
