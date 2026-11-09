<script setup>
import { computed, ref } from 'vue'
import { onReachBottom, onShow } from '@dcloudio/uni-app'
import { get, pageUrl, withCursor } from '../api'
import { errorText, go, reportLabel, shanghai, statusLabel } from '../ui'
import BottomNav from '../components/BottomNav.vue'

const items = ref([]), cursor = ref(null), busy = ref(false), error = ref('')
const covers = new Map([
  ['刷单返利诈骗', '/static/history-rebate.jpg'],
  ['虚假投资诈骗', '/static/history-investment.jpg'],
  ['冒充亲友借款诈骗', '/static/history-impersonation.jpg']
])
const cover = name => covers.get(name) || '/static/pair.jpg'
const groups = computed(() => {
  const result = []
  for (const item of items.value) {
    const day = shanghai(item.startedAt).slice(0, 10)
    if (result[result.length - 1]?.day !== day) result.push({ day, items: [] })
    result[result.length - 1].items.push(item)
  }
  return result
})
async function load(more = false) {
  if (busy.value || (more && !cursor.value)) return
  busy.value = true; error.value = ''
  try {
    const page = await get(withCursor('/simulations', more ? cursor.value : null))
    items.value = more ? items.value.concat(page.items) : page.items
    cursor.value = page.nextCursor
  } catch (cause) { error.value = errorText(cause) }
  finally { busy.value = false }
}
function open(id) { uni.navigateTo({ url: pageUrl('detail', id) }) }
onShow(() => { void load() })
onReachBottom(() => { void load(true) })
</script>

<template>
  <view v-if="items.length" class="hero history-hero"><image class="hero-art" src="/static/archive.jpg" mode="aspectFill" /></view>
  <view class="surface" :class="{ overlap: items.length }">
    <view v-if="error"><text class="error">{{ error }}</text><button class="ghost" @tap="load()">重新读取</button></view>
    <text v-if="busy && !items.length" class="muted block center">正在读取演绎记录…</text>
    <view v-if="!busy && !items.length && !error" class="empty">
      <image class="empty-records" src="/static/history-empty.jpg" mode="widthFix" />
      <text class="empty-title">还没有演绎记录</text><view class="actions"><button @tap="go('casting')">开始体验</button></view>
    </view>
    <view v-for="group in groups" :key="group.day">
      <text class="date-heading">{{ group.day === '—' ? group.day : Number(group.day.slice(5, 7)) + '月' + Number(group.day.slice(8, 10)) + '日' }}</text>
      <view v-for="item in group.items" :key="item.sessionId" class="row history-item divider" @tap="open(item.sessionId)">
        <image class="avatar" :src="cover(item.scenarioName)" mode="aspectFill" />
        <view class="grow"><text class="bold block">{{ item.scenarioName }}</text><text class="muted small">{{ item.scammerName }}、{{ item.victimName }}</text></view>
        <view><text class="badge">{{ statusLabel(item.status) }}</text><text class="muted block small">{{ reportLabel(item.report) }}</text></view><text>›</text>
      </view>
    </view>
    <button v-if="cursor" class="link" :disabled="busy" @tap="load(true)">加载更多</button>
    <BottomNav active="history" />
  </view>
</template>

<style scoped>
.history-hero { min-height: 290rpx; }
.empty { padding-top: 23vh; }
.empty-records { display: block; width: 76%; max-width: 530rpx; margin: 0 auto 36rpx; }
.empty-title { display: block; font-size: 49rpx; font-weight: 800; margin: 0 auto 57rpx; }
.date-heading { display: block; font-size: 36rpx; font-weight: 800; margin: 2rpx 0 10rpx; }
.history-item { padding: 18rpx 0; }
.history-item .avatar { flex-shrink: 0; width: 172rpx; height: 120rpx; }
.badge { background: #cce4ed; color: #225168; padding: 2rpx 10rpx; border-radius: 7rpx; font-size: 22rpx; font-weight: 750; }
</style>
