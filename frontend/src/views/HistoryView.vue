<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { historyApi } from '../api/history'
import type { HistoryItem, SimulationStatus } from '../api/types'
import { formatShanghai } from '../utils/time'

const items = ref<HistoryItem[]>([]), cursor = ref<string | null>(null), hasMore = ref(false)
const busy = ref(true), error = ref('')
const statuses: Record<SimulationStatus, string> = { RUNNING: '进行中', FINISHED: '已结束', INTERRUPTED: '已中断' }
const reports = { PENDING: '生成中', RUNNING: '生成中', SUCCEEDED: '已完成', FAILED: '生成失败' }

function reportText(item: HistoryItem) {
  if (item.status === 'INTERRUPTED') return '不生成正式复盘'
  if (item.status === 'RUNNING') return '—'
  return item.report ? reports[item.report.status] : '报告状态异常'
}

async function load(more = false) {
  busy.value = true; error.value = ''
  try {
    const page = await historyApi.list(more && cursor.value ? { cursor: cursor.value } : undefined)
    items.value = [...new Map([...(more ? items.value : []), ...page.items].map(item => [item.sessionId, item])).values()]
    cursor.value = page.nextCursor; hasMore.value = page.hasMore
  } catch (cause) { error.value = cause instanceof Error ? cause.message : '历史记录加载失败，请重试' }
  finally { busy.value = false }
}
onMounted(() => load())
</script>

<template>
  <div class="home-layout history-page" data-testid="history" :aria-busy="busy">
    <header class="configuration-section"><span class="eyebrow">回顾与学习</span><h1>个人历史</h1><p>回顾本人已保存的演绎过程与复盘报告。时间均为北京时间。</p></header>
    <p v-if="busy" class="loading-state" data-testid="history-loading" role="status">正在读取历史记录…</p>
    <section v-if="error" class="load-error" data-testid="history-error" role="alert">
      <p>{{ error }}</p><button class="button button-primary" data-testid="history-retry" :disabled="busy" @click="load(items.length > 0)">重新读取</button>
    </section>
    <section v-if="!busy && !error && !items.length" class="configuration-section" data-testid="history-empty"><h2>本人暂无记录</h2><RouterLink class="button button-primary" to="/configuration">开始体验</RouterLink></section>
    <ul v-if="items.length" class="history-list" data-testid="history-list">
      <li v-for="item in items" :key="item.sessionId">
        <RouterLink :to="`/history/${item.sessionId}`" class="configuration-section history-item" data-testid="history-item">
          <time :datetime="item.startedAt" data-testid="history-item-started">{{ formatShanghai(item.startedAt) }}</time>
          <h2 data-testid="history-item-scenario">{{ item.scenarioName }}</h2>
          <p data-testid="history-item-profiles">{{ item.scammerName }} 与 {{ item.victimName }}</p>
          <div class="history-status"><span data-testid="history-item-status">{{ statuses[item.status] }}</span><span data-testid="history-item-report">复盘报告：{{ reportText(item) }}</span></div>
          <p v-if="item.outcome">{{ item.outcome.displayText }}</p><p v-if="item.interruption">{{ item.interruption.message }}</p>
          <span class="field-help">查看保存的过程 →</span>
        </RouterLink>
      </li>
    </ul>
    <button v-if="hasMore && !error" class="button button-light" data-testid="history-more" :disabled="busy" @click="load(true)">{{ busy ? '正在读取…' : '加载更多' }}</button>
  </div>
</template>

<style scoped>
.configuration-section { padding: 28px; margin-bottom: 24px; background: white; border: 1px solid var(--border); border-radius: 12px; }
.history-page p { white-space: pre-wrap; overflow-wrap: anywhere; }
.history-list { padding: 0; list-style: none; display: grid; gap: 1rem; }
.history-item { display: block; color: inherit; text-decoration: none; overflow-wrap: anywhere; }
.history-item:hover, .history-item:focus-visible { border-color: var(--green); }
.history-item time { color: var(--muted); font-size: .9rem; }
.history-item h2 { margin: .65rem 0; }
.history-status { display: flex; flex-wrap: wrap; gap: .5rem 1.5rem; }
@media (max-width: 700px) { .configuration-section { padding: 20px; } }
</style>
