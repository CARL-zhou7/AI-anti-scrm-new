<script setup>
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { isOfficialNewsUrl } from '../ui'

const url = ref(''), failed = ref(false)
function copyLink() { uni.setClipboardData({ data: url.value }) }
onLoad(query => {
  try {
    const target = decodeURIComponent(query.url || '')
    if (isOfficialNewsUrl(target)) url.value = target
  } catch { failed.value = true }
})
</script>

<template>
  <web-view v-if="url && !failed" :src="url" @error="failed = true" />
  <view v-else class="page">
    <text class="heading">{{ url ? '官方页面暂时无法在微信内打开' : '原文链接暂不可用' }}</text>
    <template v-if="url">
      <text class="muted block">复制原文链接后，可在浏览器中查看报道。</text>
      <view class="actions"><button @tap="copyLink">复制原文链接</button></view>
    </template>
  </view>
</template>
