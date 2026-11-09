<script setup>
defineProps({ active: String })
function switchTo(page, active) {
  if (page !== active) uni.reLaunch({ url: '/pages/' + page })
}
</script>

<template>
  <view class="foot" />
  <view class="bottom-nav">
    <button class="nav-button" :class="{ chosen: active === 'home' }" @tap="switchTo('home', active)"><view class="nav-icon house"><view class="icon-detail" /></view>首页</button>
    <button class="nav-button" :class="{ chosen: active === 'history' }" @tap="switchTo('history', active)"><view class="nav-icon clock"><view class="icon-detail" /></view>历史</button>
  </view>
</template>

<style scoped>
.bottom-nav { position: fixed; z-index: 3; bottom: 0; left: 0; right: 0; display: flex; border-top: 1px solid #cedde8; background: #e8f1f8; padding-bottom: env(safe-area-inset-bottom); }
.nav-button { flex: 1; background: transparent; color: #294c62; padding: 13rpx 8rpx 10rpx; font-size: 27rpx; }
.bottom-nav .chosen { color: #b5542a; font-weight: 750; }
.nav-icon { position: relative; width: 39rpx; height: 39rpx; margin: 0 auto 5rpx; }
.house::before { content: ''; position: absolute; inset: 14rpx 2rpx 1rpx; border: 3rpx solid currentColor; border-top: 0; border-radius: 0 0 5rpx 5rpx; }
.house::after { content: ''; position: absolute; width: 26rpx; height: 26rpx; top: 4rpx; left: 6rpx; transform: rotate(45deg); border: 3rpx solid currentColor; border-right: 0; border-bottom: 0; border-radius: 4rpx; }
.house .icon-detail { position: absolute; border: 3rpx solid currentColor; border-bottom: 0; bottom: 3rpx; left: 14rpx; height: 14rpx; width: 11rpx; }
.clock { border: 3rpx solid currentColor; border-radius: 50%; }
.clock .icon-detail { position: absolute; top: 6rpx; left: 15rpx; width: 11rpx; height: 13rpx; border-left: 3rpx solid currentColor; border-bottom: 3rpx solid currentColor; }
</style>
