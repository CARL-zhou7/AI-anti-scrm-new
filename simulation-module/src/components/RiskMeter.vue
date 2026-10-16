<script setup>
// 受害者当前风险（BR-C01～C03、BR-P01）：仅五档；未评分显示“尚未评分”，不补 0。
import { computed } from 'vue'
import { RISK_SCORES, RISK_TIERS } from '../core/constants.js'

const props = defineProps({
  score: { type: Number, default: null },
  reason: { type: String, default: '' },
  series: { type: Array, default: () => [] }, // [{ roundNo, score }]
})

const colorOf = (score) => `var(--sim-risk-${score})`
const tier = computed(() => (props.score === null ? null : RISK_TIERS[props.score] ?? null))
</script>

<template>
  <section class="sim-card sim-risk" :style="score !== null ? { '--risk-color': colorOf(score) } : null">
    <h3 class="sim-card__title">受害者当前风险</h3>
    <div class="sim-risk__head">
      <span v-if="score === null" class="sim-risk__score sim-risk__score--empty">尚未评分</span>
      <template v-else>
        <span class="sim-risk__score sim-num">{{ score }}</span>
        <span class="sim-risk__label">{{ tier?.label }}</span>
      </template>
    </div>
    <div class="sim-risk__bar" role="img" :aria-label="score === null ? '尚未评分' : `风险 ${score}`">
      <span
        v-for="s in RISK_SCORES"
        :key="s"
        class="sim-risk__seg"
        :class="{ 'is-on': score !== null && s <= score }"
        :style="{ '--seg-color': colorOf(score ?? 0) }"
      ></span>
    </div>
    <div class="sim-risk__ticks"><span v-for="s in RISK_SCORES" :key="s">{{ s }}</span></div>
    <p v-if="reason" class="sim-risk__reason">{{ reason }}</p>
    <div v-if="series.length" class="sim-risk__series" aria-label="逐轮风险">
      <span v-for="p in series" :key="p.roundNo" class="sim-risk__point" :style="{ '--seg-color': colorOf(p.score) }">
        第{{ p.roundNo }}轮 {{ p.score }}
      </span>
    </div>
    <p class="sim-risk__note">分值为教学档位，反映受害者角色在剧情中的处境，不代表现实受骗概率，也不评价观看者。</p>
  </section>
</template>
