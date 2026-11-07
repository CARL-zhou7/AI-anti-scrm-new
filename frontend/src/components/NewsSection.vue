<script setup lang="ts">
import type { NewsPayload } from '../api/types'

defineProps<{ items: NewsPayload }>()
</script>

<template>
  <section class="news-card" aria-labelledby="news-heading">
    <div class="section-heading"><h2 id="news-heading">反诈动态</h2></div>
    <div v-if="!items.length" class="empty-state" data-testid="home-news-empty"><p>暂无资讯。可以先选择一个场景，开始推演。</p></div>
    <ul v-else class="news-list">
      <li v-for="item in items" :key="item.url">
        <a :href="item.url" target="_blank" rel="noopener noreferrer" aria-description="在新标签页打开" class="news-link">
          <span>
            <span class="news-title">{{ item.title }}</span>
            <span class="news-meta">
              <span>{{ item.category }}</span>
              <span>{{ item.source }}</span>
              <time :datetime="item.publishedAt">{{ item.publishedAt }}</time>
            </span>
          </span>
          <span class="news-original">查看原文 <span aria-hidden="true">↗</span></span>
        </a>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.news-list {
  list-style:none;
  margin:0;
  padding:0;
}

.news-list li + li {
  border-top:1px solid #eef0ea;
}

.news-link {
  display:flex;
  align-items:center;
  justify-content:space-between;
  gap:12px;
  padding:13px 0;
}

.news-title {
  font-size:14px;
  font-weight:600;
}

.news-link:hover .news-title,
.news-link:focus-visible .news-title {
  text-decoration:underline;
}

.news-meta {
  display:flex;
  flex-wrap:wrap;
  gap:2px 10px;
  color:var(--muted);
  font-size:12px;
}

.news-original {
  white-space:nowrap;
  font-size:12px;
}
</style>
