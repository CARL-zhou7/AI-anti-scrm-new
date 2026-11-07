<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { homeApi } from '../api/home'
import type { HomePayload, NewsPayload } from '../api/types'
import NewsSection from '../components/NewsSection.vue'

const home = ref<HomePayload | null>(null), busy = ref(false), error = ref('')
const news = ref<NewsPayload>([])
async function load() {
  busy.value = true; error.value = ''; home.value = null
  try {
    const [payload, articles] = await Promise.all([homeApi.getHome(), homeApi.getNews()])
    news.value = articles
    home.value = payload
  } catch (e) { error.value = e instanceof Error ? e.message : '首页加载失败，请重试' }
  finally { busy.value = false }
}
onMounted(load)
</script>

<template>
  <div class="home-landing" :aria-busy="busy">
    <div v-if="busy" class="home-layout loading-state" role="status">正在加载首页…</div>
    <section v-else-if="error" class="home-layout load-error" data-testid="home-error" role="alert"><h1>首页加载失败</h1><p>{{ error }}</p><button class="button button-primary" @click="load">重新加载</button></section>
    <template v-else-if="home">
      <section class="home-hero">
        <div class="hero-inner">
          <div class="hero-copy">
            <span class="eyebrow">{{ home.title }}</span>
            <h1>一句话，<span>骗局走到哪一步</span></h1>
            <p data-testid="home-introduction">{{ home.introduction }}</p>
            <div class="hero-actions">
              <RouterLink v-if="home.entries.includes('CONFIGURATION')" class="button button-primary" to="/configuration" data-testid="home-entry-configuration">选择诈骗场景</RouterLink>
              <RouterLink v-if="home.entries.includes('HISTORY')" class="button button-light" to="/history" data-testid="home-entry-history">查看个人历史</RouterLink>
            </div>
          </div>
          <aside class="hero-case">
            <header><strong>刷单返利诈骗</strong><small>示例对话</small></header>
            <blockquote>“先做一单体验任务，<mark>完成后就能收到返利</mark>。”</blockquote>
            <p><strong>线索</strong>　低门槛任务降低戒心，为后续垫付铺路。</p>
          </aside>
        </div>
      </section>
      <div class="home-content">
        <section v-if="home.entries.includes('CONFIGURATION')">
          <div class="section-heading"><h2>三种骗局，三种开场</h2></div>
          <div class="scene-links">
            <span class="scene-tag">刷单返利诈骗</span>
            <span class="scene-tag">虚假投资诈骗</span>
            <span class="scene-tag">冒充亲友借款诈骗</span>
          </div>
        </section>
        <NewsSection :items="news" />
      </div>
    </template>
  </div>
</template>
