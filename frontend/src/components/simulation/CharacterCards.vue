<script setup lang="ts">
import type { ScammerProfilePayload, VictimProfilePayload } from '../../api/types'
// Both cards come from the frozen configuration snapshot, never from the current profile tables.
defineProps<{ scammer: ScammerProfilePayload; victim: VictimProfilePayload; frozen: boolean }>()
defineEmits<{ avatarError: [] }>()
</script>

<template>
  <section class="cards" :class="{ frozen }" data-testid="character-cards">
    <article v-for="(p, i) in [scammer, victim]" :key="p.id" class="card" :class="i === 0 ? 'scammer' : 'victim'" :style="{ animationDelay: i * 3.5 + 's' }">
      <span class="role">{{ i === 0 ? '骗子' : '受害者' }}</span>
      <img :src="p.avatar.url" :alt="p.name + '的半身形象'" @error="$emit('avatarError')" />
      <h2>{{ p.name }}</h2>
      <dl>
        <div><dt>性别</dt><dd>{{ p.gender }}</dd></div>
        <div><dt>年龄</dt><dd>{{ p.age }} 岁</dd></div>
        <div class="wide"><dt>身份</dt><dd>{{ p.identity }}</dd></div>
      </dl>
    </article>
  </section>
</template>

<style scoped>
.cards { height:100%; display:flex; gap:48px; align-items:center; justify-content:center; padding:24px; }
.card { width:260px; padding:20px 20px 24px; border-radius:14px; background:var(--sim-paper); color:var(--sim-ink); text-align:center;
  opacity:0; animation:deal 0.7s cubic-bezier(.2,.8,.3,1) forwards; box-shadow:0 18px 40px rgb(0 0 0 / 0.35); }
.card.scammer { border-top:6px solid var(--sim-warn); }
.card.victim { border-top:6px solid var(--sim-celadon); }
.role { font-size:12px; letter-spacing:0.3em; color:#6b6f80; }
.card img { display:block; width:180px; height:180px; margin:12px auto; border-radius:50%; object-fit:cover; background:#e9e4d6; }
.card h2 { margin:0 0 12px; font-family:var(--sim-display); font-size:30px; font-weight:400; letter-spacing:0.1em; }
dl { display:grid; grid-template-columns:1fr 1fr; gap:8px; margin:0; text-align:left; }
dl div { padding:6px 10px; border-radius:6px; background:#f0ece1; }
dl .wide { grid-column:1 / -1; }
dt { font-size:11px; color:#6b6f80; }
dd { margin:2px 0 0; font-size:14px; overflow-wrap:anywhere; }
.frozen .card { animation-play-state:paused; }
@keyframes deal { from { opacity:0; transform:translateY(24px) rotate(-2deg) } to { opacity:1; transform:none } }
@media (max-width:640px) { .cards { flex-direction:column; gap:16px; } .card img { width:110px; height:110px; } }
</style>
