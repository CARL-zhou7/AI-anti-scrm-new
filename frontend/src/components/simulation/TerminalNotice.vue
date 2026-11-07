<script setup lang="ts">
// Interrupted runs end here: no resume, replay or download. Content already shown stays on the stage behind.
defineProps<{ message: string; code?: string; reportSessionId?: string; winner?: 'SCAMMER' | 'VICTIM' | null; finalTrustScore?: number | null }>()
</script>

<template>
  <div class="terminal" role="alertdialog" aria-labelledby="terminal-title" data-testid="terminal-notice">
    <div class="panel">
      <h2 id="terminal-title">{{ finalTrustScore !== null && finalTrustScore !== undefined
        ? (winner === 'SCAMMER' ? '本场判定：骗子胜' : winner === 'VICTIM' ? '本场判定：受害者胜' : '本次演绎已结束')
        : reportSessionId ? '本次演绎已结束' : code === 'FUNDS_EXHAUSTED' || code === 'CHARACTER_EXIT' ? '演绎提前终止' : '演绎已中断' }}</h2>
      <p>{{ message }}</p>
      <p v-if="finalTrustScore !== null && finalTrustScore !== undefined" class="hint" data-testid="terminal-final-score">
        最终受骗倾向分：{{ finalTrustScore }} · 已完成 15 轮
      </p>
      <p v-if="reportSessionId && (finalTrustScore === null || finalTrustScore === undefined)" class="hint">本次未形成受骗或识破结论，可查看已保存过程的复盘报告。</p>
      <RouterLink v-if="reportSessionId" class="primary" :to="`/simulations/${reportSessionId}/report`">查看复盘报告</RouterLink>
      <p v-else class="hint">中断的演绎不生成正式复盘，已保存的内容仍可在个人记录中查看。再次体验会创建新的记录。</p>
      <RouterLink class="primary" to="/configuration">重新开始体验</RouterLink>
      <RouterLink class="ghost" to="/home">返回首页</RouterLink>
    </div>
  </div>
</template>

<style scoped>
.terminal { position:fixed; inset:0; z-index:30; display:grid; place-items:center; overflow:auto; background:rgb(13 16 30 / 0.78); }
.panel { width:min(420px, calc(100vw - 32px)); display:grid; gap:12px; padding:28px; border-radius:5px; background:var(--sim-paper); color:var(--sim-ink); text-align:center; }
h2 { margin:0; font-family:var(--sim-display); font-size:28px; font-weight:400; color:var(--sim-warn); letter-spacing:0.1em; }
p { margin:0; line-height:1.7; }
.hint { font-size:13px; color:#6b6f80; }
a { display:block; padding:12px; border-radius:10px; text-decoration:none; }
.primary { background:var(--sim-night); color:var(--sim-paper); }
.ghost { border:1px solid #cfc8b6; color:var(--sim-ink); }
a:focus-visible { outline:3px solid var(--sim-gold); outline-offset:2px; }
</style>
