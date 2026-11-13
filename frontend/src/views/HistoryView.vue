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
  if (item.report) return reports[item.report.status]
  if (item.status === 'INTERRUPTED') return '不生成正式复盘'
  if (item.status === 'RUNNING') return '—'
  return '报告状态异常'
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
    <header class="configuration-section history-heading"><span class="eyebrow">回顾与学习</span><h1>个人历史</h1><p>回顾本人已保存的演绎过程与复盘报告。时间均为北京时间。</p></header>
    <p v-if="busy" class="loading-state motion-pending" data-testid="history-loading" role="status">正在读取历史记录…</p>
    <section v-if="error" class="load-error" data-testid="history-error" role="alert">
      <p>{{ error }}</p><button class="button button-primary" data-testid="history-retry" :disabled="busy" @click="load(items.length > 0)">重新读取</button>
    </section>
    <section v-if="!busy && !error && !items.length" class="configuration-section" data-testid="history-empty"><h2>本人暂无记录</h2><RouterLink class="button button-primary" to="/configuration">开始体验</RouterLink></section>
    <div v-if="items.length" class="history-ledger">
      <div class="history-ledger-head" aria-hidden="true"><span>开始时间</span><span>诈骗场景</span><span>双方画像</span><span>演绎与复盘</span><span>操作</span></div>
      <ul class="history-list" data-testid="history-list">
      <li v-for="item in items" :key="item.sessionId">
        <RouterLink :to="`/history/${item.sessionId}`" class="configuration-section history-item" data-testid="history-item">
          <time :datetime="item.startedAt" data-testid="history-item-started">{{ formatShanghai(item.startedAt) }}</time>
          <h2 data-testid="history-item-scenario">{{ item.scenarioName }}</h2>
          <p data-testid="history-item-profiles">{{ item.scammerName }} 与 {{ item.victimName }}</p>
          <div class="history-status"><span data-testid="history-item-status">{{ item.status === 'INTERRUPTED' && item.report ? '正常结束' : statuses[item.status] }}</span><span data-testid="history-item-report">复盘报告：{{ reportText(item) }}</span></div>
          <p v-if="item.outcome" class="history-outcome">{{ item.outcome.displayText }}</p><p v-if="item.interruption" class="history-outcome">{{ item.interruption.message }}</p>
          <span class="field-help history-open">查看详情 →</span>
        </RouterLink>
      </li>
      </ul>
    </div>
    <button v-if="hasMore && !error" class="button button-light" data-testid="history-more" :disabled="busy" :aria-busy="busy" @click="load(true)">{{ busy ? '正在读取…' : '加载更多' }}</button>
  </div>
</template>

<style scoped>
.history-heading { border-top:3px solid var(--navy); background:var(--ice); }
.history-heading h1 { margin-bottom:8px; }
.history-heading p { color:var(--muted); margin:0; }
.history-ledger { border:1px solid var(--border); border-radius:4px; overflow:hidden; background:var(--surface); margin-bottom:16px; }
.history-ledger-head,.history-item { display:grid; grid-template-columns:1.12fr 1.3fr 1fr 1.15fr auto; align-items:center; gap:5px 14px; padding:12px 16px; }
.history-ledger-head { background:#e2edf4; color:var(--muted); font-size:13px; font-weight:700; }
.history-list { padding:0; margin:0; list-style:none; }
.history-item { border:0; border-top:1px solid var(--border); border-radius:0; margin:0; color:inherit; overflow-wrap:anywhere; }
.history-item:hover,.history-item:focus-visible { background:#eaf4fb; outline-offset:-3px; }
.history-item time { color:var(--muted); font-variant-numeric:tabular-nums; font-size:14px; }
.history-item h2,.history-item p { margin:0; font-size:15px; }
.history-status { display:flex; flex-wrap:wrap; gap:4px; font-size:12px; }
.history-status span { padding:3px 6px; border:1px solid #bed0df; border-radius:3px; background:#e3eef5; }
.history-item .history-outcome { grid-column:2/5; grid-row:2; color:var(--muted); font-size:13px; }
.history-open { grid-column:5; grid-row:1; color:#254e70; font-weight:700; }
@media(max-width:760px) {
  .history-ledger-head { display:none; }
  .history-item { grid-template-columns:minmax(0,1fr) auto; }
  .history-item time,.history-item h2,.history-item p,.history-item .history-outcome { grid-column:1/-1; grid-row:auto; }
  .history-open { grid-column:2; grid-row:auto; }
}
</style>
