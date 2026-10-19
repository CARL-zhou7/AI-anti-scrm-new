<script setup lang="ts">
import type { Configuration } from '../../api/types'
import ProfileDetail from '../ProfileDetail.vue'
defineProps<{ configuration: Configuration }>()
</script>

<template>
  <section class="configuration-section" data-testid="history-configuration">
    <h2>当时配置 · {{ configuration.scenario.name }}</h2>
    <p>{{ configuration.scenario.description }}</p><p>内容侧重：{{ configuration.scenario.focus }}</p>
    <div class="history-profiles"><div><h3>骗子画像</h3><ProfileDetail :profile="configuration.scammer" /></div><div><h3>受害者画像</h3><ProfileDetail :profile="configuration.victim" /></div></div>
  </section>
</template>

<style scoped>
.history-profiles { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 2rem; }
.history-profiles :deep(.detail-avatar) { width: 88px; height: 88px; object-fit: cover; border-radius: 10px; }
.history-profiles :deep(small) { font-size: 12px; color: var(--muted); font-weight: 400; }
.history-profiles :deep(dl) { display: grid; grid-template-columns: 100px minmax(0, 1fr); gap: 10px; font-size: 13px; }
.history-profiles :deep(dt) { color: var(--muted); }
.history-profiles :deep(dd) { margin: 0; }
@media (max-width: 700px) { .history-profiles { grid-template-columns: 1fr; } }
</style>
