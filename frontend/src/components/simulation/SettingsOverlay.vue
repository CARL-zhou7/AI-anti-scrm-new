<script setup lang="ts">
import { ref } from 'vue'
// Opening settings pauses the run. Sound only toggles muting locally; it is never a failure and sends no request.
defineProps<{ muted: boolean; finished: boolean; busy: boolean; error: string }>()
const emit = defineEmits<{ toggleMute: []; resume: []; exit: [] }>()
const confirming = ref(false)
</script>

<template>
  <div class="overlay" role="dialog" aria-modal="true" aria-labelledby="settings-title" data-testid="settings-overlay">
    <div class="panel">
      <h2 id="settings-title">演绎已暂停</h2>
      <label class="row">
        <span>角色语音</span>
        <button type="button" class="switch" role="switch" :aria-checked="!muted" data-testid="settings-sound" @click="emit('toggleMute')">{{ muted ? '已关闭' : '已开启' }}</button>
      </label>
      <p v-if="error" class="error" role="alert">{{ error }}</p>
      <template v-if="!confirming">
        <button type="button" class="primary" data-testid="settings-resume" :disabled="busy" @click="emit('resume')">继续演绎</button>
        <button type="button" class="ghost" data-testid="settings-exit" :disabled="busy" @click="confirming = true">退出演绎</button>
      </template>
      <div v-else class="confirm" data-testid="settings-exit-confirm">
        <p>{{ finished ? '本次演绎已正式结束，退出后将直接进入复盘。' : '退出后本次演绎将中断，且不生成正式复盘。确定退出吗？' }}</p>
        <button type="button" class="danger" data-testid="settings-exit-yes" :disabled="busy" @click="emit('exit')">确定退出</button>
        <button type="button" class="ghost" :disabled="busy" @click="confirming = false">返回设置</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.overlay { position:absolute; inset:0; z-index:20; display:grid; place-items:center; background:rgb(13 16 30 / 0.72); backdrop-filter:blur(3px); }
.panel { width:min(360px, calc(100vw - 32px)); display:grid; gap:12px; padding:28px; border-radius:16px; background:var(--sim-paper); color:var(--sim-ink); }
h2 { margin:0 0 8px; font-family:var(--sim-display); font-size:26px; font-weight:400; letter-spacing:0.1em; }
.row { display:flex; justify-content:space-between; align-items:center; padding:10px 0; border-bottom:1px solid #e3ddcd; }
button { padding:12px; border-radius:10px; border:1px solid transparent; font-size:15px; cursor:pointer; }
button:focus-visible { outline:3px solid var(--sim-gold); outline-offset:2px; }
.switch { padding:6px 14px; background:var(--sim-night); color:var(--sim-paper); }
.switch[aria-checked='false'] { background:#bfc3cf; color:var(--sim-ink); }
.primary { background:var(--sim-night); color:var(--sim-paper); }
.ghost { background:transparent; border-color:#cfc8b6; color:var(--sim-ink); }
.danger { background:var(--sim-warn); color:#fff; }
.confirm { display:grid; gap:10px; }
.confirm p, .error { margin:0; font-size:14px; line-height:1.6; }
.error { color:#a33328; }
</style>
