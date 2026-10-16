<script setup lang="ts">
defineProps<{ empty: boolean }>()
</script>

<template>
  <section class="news-card" aria-labelledby="news-heading"><div class="section-heading"><h2 id="news-heading">反诈资讯</h2><span class="quiet-label">资讯动态</span></div>
    <div v-if="empty" class="empty-state" data-testid="home-news-empty"><span class="empty-icon" aria-hidden="true">≡</span><h3>暂无资讯</h3><p>当前暂无可展示的资讯内容。</p></div>
  </section>
</template>
