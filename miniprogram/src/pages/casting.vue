<script setup>
import { computed, ref } from 'vue'
import { onBackPress, onLoad } from '@dcloudio/uni-app'
import { get, withCursor } from '../api'
import { errorText, sceneArt, scenes, toast } from '../ui'
import { profileDetailRows } from '../profileDetail'
import Portrait from '../components/Portrait.vue'

const scenarios = ref([]), sceneId = ref(''), step = ref(1)
const groups = ref([
  { key: 'scammer', label: '骗子画像', items: [], cursor: null, selected: '' },
  { key: 'victim', label: '受害者画像', items: [], cursor: null, selected: '' }
])
const detail = ref(null), busy = ref(false), error = ref('')
const scene = computed(() => scenarios.value.find(item => item.id === sceneId.value))
const detailRows = computed(() => detail.value ? profileDetailRows(detail.value.item, scene.value?.code) : [])

async function profiles(group, cursor) {
  const result = await get(withCursor('/' + group.key + '-profiles', cursor))
  group.items = cursor ? group.items.concat(result.items) : result.items
  group.cursor = result.nextCursor
}
async function load() {
  busy.value = true; error.value = ''
  try {
    await Promise.all([get('/scenarios').then(items => {
      scenarios.value = items
      if (!scene.value) sceneId.value = items[0]?.id || ''
    }), ...groups.value.map(group => profiles(group))])
  } catch (cause) { error.value = errorText(cause) }
  finally { busy.value = false }
}
async function more(group) {
  if (busy.value) return
  busy.value = true
  try { await profiles(group, group.cursor) } catch (cause) { toast(cause) }
  finally { busy.value = false }
}
function showDetail(group, item) {
  detail.value = { key: group.key, item }
  uni.setNavigationBarTitle({ title: '画像详情' })
}
function closeDetail(select = false) {
  if (select) groups.value.find(group => group.key === detail.value.key).selected = detail.value.item.id
  detail.value = null
  uni.setNavigationBarTitle({ title: '选择场景与人物' })
}
function enter() {
  const [scammer, victim] = groups.value
  uni.navigateTo({ url: '/pages/simulation?scenarioId=' + encodeURIComponent(sceneId.value)
    + '&scammerProfileId=' + encodeURIComponent(scammer.selected) + '&victimProfileId=' + encodeURIComponent(victim.selected) })
}
onLoad(query => { sceneId.value = query.scene || ''; void load() })
onBackPress(() => {
  if (detail.value) { closeDetail(); return true }
  if (step.value === 2) { step.value = 1; return true }
  return false
})
</script>

<template>
  <view v-if="detail">
    <view class="hero detail-hero">
      <Portrait class="hero-art" :person="detail.item" />
      <view class="hero-copy"><text class="title">{{ detail.item.name }}</text><text class="block">{{ detail.item.age }}岁 · {{ detail.item.gender }}</text></view>
    </view>
    <view class="surface overlap">
      <view v-for="[label, value] in detailRows" :key="label" class="detail-row">
        <text class="block bold detail-label">{{ label }}</text>
        <text class="block detail-value">{{ value }}</text>
      </view>
      <view class="actions"><button @tap="closeDetail(true)">选择此画像</button><button class="ghost" @tap="closeDetail()">返回人物列表</button></view>
    </view>
  </view>
  <view v-else class="page casting-page">
    <view class="row stepper">
      <view class="step-item" :class="{ selected: step === 1, done: step === 2 }" @tap="step = 1"><text>{{ step === 2 ? '✓' : '1' }}</text>场景</view>
      <view class="line grow" />
      <view class="step-item" :class="{ selected: step === 2 }"><text>2</text>人物</view>
    </view>
    <text v-if="busy && !scenarios.length" class="muted block">正在加载场景与画像…</text>
    <view v-if="error"><text class="error">{{ error }}</text><button class="ghost" @tap="load">重新加载</button></view>
    <template v-else-if="step === 1">
      <text class="heading">选择诈骗场景</text>
      <text v-if="!busy && !scenarios.length" class="muted">暂无可用场景</text>
      <view class="stack">
        <view v-for="item in scenarios" :key="item.id" class="scene-card" :class="{ picked: sceneId === item.id }"
          :aria-label="item.name + '，' + (scenes[item.code]?.caption || item.description) + (sceneId === item.id ? '，已选择' : '')" @tap="sceneId = item.id">
          <image class="hero-art" :src="sceneArt(item.code)" mode="aspectFill" />
          <view class="hero-copy"><text class="block bold">{{ item.name }}</text><text class="small">{{ scenes[item.code]?.caption || item.description }}</text></view>
          <text class="indicator scene-indicator">{{ sceneId === item.id ? '✓' : '' }}</text>
        </view>
      </view>
      <view class="actions selection-action"><button :disabled="!scene || busy" @tap="step = 2">下一步：选人物</button></view>
    </template>
    <template v-else>
      <view v-for="group in groups" :key="group.key">
        <view class="row between profile-heading">
          <text class="heading">{{ group.label }}</text>
          <button v-if="group.items.length" class="link" @tap="showDetail(group, group.items.find(item => item.id === group.selected) || group.items[0])">查看画像 ›</button>
        </view>
        <text v-if="!group.items.length" class="muted">暂无可选画像</text>
        <view class="profiles" :class="group.key">
          <view v-for="item in group.items" :key="item.id" class="profile" :class="{ picked: group.selected === item.id }" @tap="group.selected = item.id">
            <Portrait class="photo" :person="item" />
            <text class="block bold center profile-name">{{ item.name }}</text>
            <text v-if="group.selected === item.id" class="indicator">✓</text>
          </view>
        </view>
        <button v-if="group.cursor" class="link" :disabled="busy" @tap="more(group)">加载更多</button>
      </view>
      <view class="selection-action">
        <view v-if="scene" class="row chosen-scene"><image :src="sceneArt(scene.code)" mode="aspectFill" /><text class="bold">{{ scene.name }}</text></view>
        <button :disabled="!scene || !groups.every(group => group.selected) || busy" @tap="enter">进入演绎</button>
      </view>
    </template>
  </view>
</template>

<style scoped>
.casting-page { display: flex; flex-direction: column; min-height: 100vh; }
.stepper { padding: 6rpx 145rpx 18rpx; font-weight: 700; color: #526c7e; align-items: flex-start; gap: 25rpx; }
.stepper .selected { color: #ad481f; }
.step-item { text-align: center; flex-shrink: 0; }
.step-item text { display: block; width: 50rpx; height: 50rpx; margin: 0 auto; border: 2rpx solid #adbdc9; border-radius: 50%; line-height: 46rpx; font-size: 28rpx; }
.selected text, .done text { background: #bf6034; color: white; border-color: transparent; }
.done text { background: #425f73; }
.line { height: 1px; margin-top: 24rpx; background: #aabdc9; }
.scene-card { position: relative; display: flex; align-items: flex-end; overflow: hidden; height: 246rpx; border: 3rpx solid transparent; border-radius: 17rpx; background: #0c2233; color: #fff; }
.scene-card .hero-copy { padding: 65rpx 22rpx 16rpx; }
.scene-card .bold { font-size: 38rpx; line-height: 1.1; }
.scene-card .small { font-size: 27rpx; }
.indicator { position: absolute; right: 14rpx; top: 12rpx; border-radius: 50%; border: 2rpx solid #fff; color: #fff; width: 41rpx; height: 41rpx; line-height: 35rpx; text-align: center; }
.scene-indicator { right: 12rpx; top: 6rpx; width: 60rpx; height: 60rpx; line-height: 55rpx; background: #324553; }
.picked .indicator { background: #df733c; }
.profiles { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14rpx; }
.profiles.victim { grid-template-columns: repeat(3, minmax(0, 1fr)); }
.profile { position: relative; overflow: hidden; border: 3rpx solid transparent; border-radius: 15rpx; background: #d9e7f0; }
.picked { border-color: #d56831; }
.photo { width: 100%; height: 230rpx; }
.victim .photo { height: 220rpx; }
.profile-heading .link { color: #4e6576; }
.profile-name { padding: 6rpx 0; font-size: 33rpx; }
.detail-hero { min-height: 498rpx; }
.detail-row { padding: 13rpx 0; border-bottom: 1px solid #ccdde9; }
.detail-label { color: #31536a; font-size: 25rpx; }
.detail-value { margin-top: 4rpx; white-space: pre-wrap; overflow-wrap: anywhere; }
.selection-action { margin-top: auto; padding-top: 53rpx; }
.chosen-scene { margin-bottom: 17rpx; padding: 7rpx; border: 1px solid #c9dbe7; border-radius: 12rpx; }
.chosen-scene image { width: 145rpx; height: 80rpx; border-radius: 8rpx; }
</style>
