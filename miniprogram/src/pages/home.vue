<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { get, logout } from '../api'
import { errorText, isOfficialNewsUrl, newsArt, sceneArt, scenes, shanghai, toast } from '../ui'
import BottomNav from '../components/BottomNav.vue'
import Portrait from '../components/Portrait.vue'

const homeInfo = ref(null), scenarios = ref([]), news = ref([])
const error = ref(''), newsError = ref(''), busy = ref(false)
const menu = uni.getMenuButtonBoundingClientRect()
const { windowWidth, statusBarHeight } = uni.getWindowInfo()
const headerTop = Math.max(menu.top || 0, (statusBarHeight || 0) + 6)
const chrome = {
  height: (menu.height || 32) + 'px',
  marginRight: (menu.left > 0 ? windowWidth - menu.left + 10 : 110) + 'px'
}
const featured = [
  { id: '1', name: '陈启明', caption: '35岁 · 从业者视角' },
  { id: '1', name: '林晓', caption: '22岁 · 大学生视角' }
]

async function load() {
  if (busy.value) return
  busy.value = true
  error.value = newsError.value = ''
  await Promise.all([
    Promise.all([get('/home'), get('/scenarios')]).then(([info, items]) => {
      homeInfo.value = info
      scenarios.value = items
    }).catch(cause => { error.value = errorText(cause) }),
    get('/news').then(items => { news.value = items }).catch(cause => { newsError.value = errorText(cause) })
  ])
  busy.value = false
}
function cast(id = '') { uni.navigateTo({ url: '/pages/casting?scene=' + encodeURIComponent(id) }) }
function openNews(url) {
  if (!isOfficialNewsUrl(url)) { toast(new Error('原文链接暂不可用')); return }
  uni.navigateTo({ url: '/pages/news?url=' + encodeURIComponent(url) })
}
async function settings() {
  const answer = await uni.showModal({ title: '退出登录？', content: '下次体验时可以使用同一个账号登录。', confirmText: '退出登录' })
  if (!answer.confirm) return
  try { await logout(); uni.reLaunch({ url: '/pages/auth' }) }
  catch (cause) { toast(cause) }
}
onShow(load)
</script>

<template>
  <view class="home-cover" :style="{ paddingTop: headerTop + 'px' }">
    <view class="row between home-chrome" :style="chrome">
      <view class="row brand"><image src="/static/shield-outline.svg" mode="aspectFit" /><text>知诈</text></view>
      <button class="settings" aria-label="账号设置" @tap="settings">⚙</button>
    </view>
    <text class="cover-title">一句话，骗局走到哪一步</text>
    <view class="featured">
      <view v-for="person in featured" :key="person.name" class="featured-person">
        <Portrait class="featured-photo" :person="person" fill />
        <view class="featured-caption"><text class="bold block">{{ person.name }}</text><text class="small">{{ person.caption }}</text></view>
      </view>
    </view>
  </view>
  <view class="surface overlap home-content">
    <button class="accent cast-action" @tap="cast()"><text>开始体验</text><text class="arrow">›</text></button>
    <text v-if="busy && !homeInfo" class="muted block">正在读取场景…</text>
    <view v-if="error"><text class="error">{{ error }}</text><button class="ghost" @tap="load">重新加载</button></view>
    <view v-for="scene in scenarios" :key="scene.id" class="row scene" hover-class="tap-press" :hover-start-time="0" @tap="cast(scene.id)">
      <image class="thumb" :src="sceneArt(scene.code)" mode="aspectFill" />
      <view class="grow"><text class="block bold">{{ scene.name }}</text><text class="muted small">{{ scenes[scene.code]?.caption || scene.description }}</text></view>
      <text>›</text>
    </view>
    <text class="heading">反诈动态</text>
    <view v-if="newsError"><text class="error">{{ newsError }}</text><button class="link" @tap="load">重新读取资讯</button></view>
    <text v-else-if="!news.length" class="muted">{{ busy ? '正在读取资讯…' : '暂无资讯' }}</text>
    <view v-for="item in news" :key="item.url" class="row scene">
      <image class="news-thumb" :src="newsArt(item.category)" mode="aspectFill" aria-label="新闻主题配图" />
      <view class="grow"><text class="bold block">{{ item.title }}</text><text class="muted small">{{ item.source }}　{{ shanghai(item.publishedAt).slice(0,10) }}</text></view>
      <button class="link news-link" @tap="openNews(item.url)">查看原文 ›</button>
    </view>
    <BottomNav active="home" />
  </view>
</template>

<style scoped>
.home-cover { background: #10283b; color: white; }
.home-chrome { margin-left: 28rpx; }
.brand { gap: 9rpx; font-size: 33rpx; font-weight: 750; }
.brand image { width: 34rpx; height: 34rpx; }
.cover-title { display: block; padding: 16rpx 28rpx 20rpx; font-size: 35rpx; line-height: 1.35; font-weight: 750; }
.settings { background: transparent; padding: 0 8rpx; font-size: 36rpx; }
.featured { display: flex; gap: 3rpx; }
.featured-person { position: relative; flex: 1; min-width: 0; }
.featured-photo { height: 340rpx; }
.featured-caption { position: absolute; inset: auto 0 0; padding: 28rpx 28rpx 36rpx; background: linear-gradient(transparent, #0c2233); }
.home-content { padding: 20rpx 24rpx 0; }
.cast-action { position: relative; margin-bottom: 19rpx; font-size: 35rpx; font-weight: 750; letter-spacing: 4rpx; }
.arrow { position: absolute; right: 30rpx; font-size: 40rpx; }
.scene { padding: 8rpx 18rpx 8rpx 8rpx; border: 1px solid #ccdde9; border-radius: 15rpx; margin: 9rpx 0; }
.scene .bold { font-size: 31rpx; line-height: 1.25; }
.scene .muted { font-size: 23rpx; }
.thumb { width: 220rpx; height: 102rpx; flex-shrink: 0; border-radius: 9rpx; background: #273f52; }
.news-thumb { width: 155rpx; height: 88rpx; flex-shrink: 0; border-radius: 9rpx; }
.news-link { flex-shrink: 0; padding: 7rpx 0; }
</style>
