<script setup>
import { computed } from 'vue'
import { trendPlot, trendY } from '../presentation'
import { riskColor } from '../ui'

const props = defineProps({ series: Array, label: String, selectable: Boolean })
defineEmits(['select'])
const ticks = [100, 75, 50, 25, 0]
const viewportWidth = uni.getWindowInfo().windowWidth - uni.upx2px(56) - 34
const plot = computed(() => trendPlot(props.series || [], viewportWidth))
</script>

<template>
  <view>
    <view class="row trend-heading"><text>{{ label }}</text><view class="trend-rule" /></view>
    <view v-if="plot.points.length" class="chart">
      <view class="axis">
        <text v-for="score in ticks" :key="score" class="axis-score" :style="{ top: trendY(score) + 'px' }">{{ score }}</text>
      </view>
      <scroll-view class="chart-scroll" scroll-x>
        <view class="plot" :style="{ width: plot.width + 'px' }">
          <view v-for="score in ticks" :key="score" class="grid" :style="{ top: trendY(score) + 'px' }" />
          <view v-for="line in plot.segments" :key="line.x" class="segment"
            :style="{ left: line.x + 'px', top: line.y + 'px', width: line.length + 'px', transform: 'rotate(' + line.angle + 'rad)' }" />
          <template v-for="point in plot.points" :key="point.roundNo">
            <view class="point" :style="{ left: point.x + 'px', top: point.y + 'px' }">
              <text class="point-score">{{ point.score }}</text>
              <view class="dot" :style="{ backgroundColor: riskColor(point.score) }" />
              <button v-if="selectable" class="point-hit" :aria-label="'查看第' + point.roundNo + '轮，' + point.score + '分'"
                @tap="$emit('select', point.roundNo)" />
            </view>
            <text class="round-label" :style="{ left: point.x + 'px' }">第{{ point.roundNo }}轮</text>
          </template>
        </view>
      </scroll-view>
    </view>
    <text v-else class="muted">暂无评分数据</text>
  </view>
</template>

<style scoped>
.trend-heading { font-size: 36rpx; font-weight: 800; margin: 24rpx 0 12rpx; }
.trend-rule { flex: 1; height: 1px; background: #a8bac6; }
.chart { display: flex; font-size: 13px; line-height: 1; color: #365469; }
.axis { position: relative; width: 34px; flex-shrink: 0; }
.axis-score { position: absolute; right: 8px; transform: translateY(-50%); }
.chart-scroll { flex: 1; width: 0; }
.plot { position: relative; height: 210px; }
.grid { position: absolute; left: 0; right: 0; border-top: 1px solid #bacfdf; }
.segment { position: absolute; height: 2px; background: #267b8d; transform-origin: left center; }
.point { position: absolute; width: 44px; height: 44px; transform: translate(-50%, -50%); text-align: center; }
.point-score { display: block; color: #102638; font-weight: 750; }
.dot { position: absolute; left: 50%; top: 50%; width: 12px; height: 12px; border: 1px solid #fff; border-radius: 50%; transform: translate(-50%, -50%); }
.point-hit { position: absolute; inset: 0; padding: 0; background: transparent; }
.round-label { position: absolute; top: 190px; transform: translateX(-50%); white-space: nowrap; }
</style>
