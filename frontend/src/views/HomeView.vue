<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { homeApi } from '../api/home'
import type { HomePayload } from '../api/types'
import NewsSection from '../components/NewsSection.vue'

const home = ref<HomePayload | null>(null), busy = ref(false), error = ref('')
async function load() {
  busy.value = true; error.value = ''; home.value = null
  try {
    const [payload] = await Promise.all([homeApi.getHome(), homeApi.getNews()])
    home.value = payload
  } catch (e) { error.value = e instanceof Error ? e.message : '首页加载失败，请重试' }
  finally { busy.value = false }
}
onMounted(load)
</script>

<template>
  <div class="home-layout" :aria-busy="busy">
    <div v-if="busy" class="loading-state" role="status">正在加载首页…</div>
    <section v-else-if="error" class="load-error" data-testid="home-error" role="alert"><h1>首页加载失败</h1><p>{{ error }}</p><button class="button button-primary" @click="load">重新加载</button></section>
    <template v-else-if="home">
      <section class="home-hero"><div class="hero-copy"><span class="eyebrow">认识骗局，从看懂一段情景开始</span><h1>{{ home.title }}</h1><p data-testid="home-introduction">{{ home.introduction }}</p><div class="hero-actions"><RouterLink v-if="home.entries.includes('CONFIGURATION')" class="button button-light" to="/configuration" data-testid="home-entry-configuration">开始体验 <span aria-hidden="true">↗</span></RouterLink><RouterLink v-if="home.entries.includes('HISTORY')" class="hero-history" to="/history" data-testid="home-entry-history">回顾个人历史 →</RouterLink></div></div><div class="hero-art" aria-hidden="true"><div class="radar-ring ring-one"></div><div class="radar-ring ring-two"></div><div class="radar-ring ring-three"></div><div class="hero-shield">✓</div><span class="radar-dot dot-one"></span><span class="radar-dot dot-two"></span><span class="art-caption">保持警觉 · 看见风险</span></div></section>
      <div class="home-content"><section class="learning-card"><div class="section-heading"><h2>如何开启反诈学习</h2><span class="quiet-label">学习路径</span></div><ol class="learning-steps"><li><span class="step-number">01</span><div><h3>观看情景</h3><p>从人物互动中观察骗局如何展开</p></div></li><li><span class="step-number">02</span><div><h3>理解话术</h3><p>结合逐轮解说，感知风险变化</p></div></li><li><span class="step-number">03</span><div><h3>复盘学习</h3><p>回顾关键节点，加深防范意识</p></div></li></ol></section><NewsSection :empty="home.newsState === 'EMPTY'" /></div>
    </template>
  </div>
</template>
