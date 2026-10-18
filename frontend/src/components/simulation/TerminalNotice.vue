<script setup lang="ts">
// Interrupted runs end here: no resume, replay or download. Content already shown stays on the stage behind.
defineProps<{ message: string }>()
</script>

<template>
  <div class="terminal" role="alertdialog" aria-labelledby="terminal-title" data-testid="terminal-notice">
    <div class="panel">
      <h2 id="terminal-title">演绎已中断</h2>
      <p>{{ message }}</p>
      <p class="hint">中断的演绎不生成正式复盘，已保存的内容仍可在个人记录中查看。再次体验会创建新的记录。</p>
      <RouterLink class="primary" to="/configuration">重新开始体验</RouterLink>
      <RouterLink class="ghost" to="/home">返回首页</RouterLink>
    </div>
  </div>
</template>

<style scoped>
.terminal { position:absolute; inset:0; z-index:30; display:grid; place-items:center; background:rgb(13 16 30 / 0.78); }
.panel { width:min(420px, calc(100vw - 32px)); display:grid; gap:12px; padding:28px; border-radius:16px; background:var(--sim-paper); color:var(--sim-ink); text-align:center; }
h2 { margin:0; font-family:var(--sim-display); font-size:28px; font-weight:400; color:var(--sim-warn); letter-spacing:0.1em; }
p { margin:0; line-height:1.7; }
.hint { font-size:13px; color:#6b6f80; }
a { display:block; padding:12px; border-radius:10px; text-decoration:none; }
.primary { background:var(--sim-night); color:var(--sim-paper); }
.ghost { border:1px solid #cfc8b6; color:var(--sim-ink); }
a:focus-visible { outline:3px solid var(--sim-gold); outline-offset:2px; }
</style>
