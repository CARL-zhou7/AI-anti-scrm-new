<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import AppNav from './components/AppNav.vue'

const route = useRoute()
const main = ref<HTMLElement>()
watch(() => route.fullPath, () => {
  document.title = `${String(route.meta.title || '首页')} · AI反诈情景推演平台`
  main.value?.focus()
})
</script>

<template>
  <RouterView v-if="route.meta.fullscreen" />
  <template v-else>
  <a class="skip-link" href="#main">跳转到主要内容</a>
  <AppNav />
  <main id="main" ref="main" tabindex="-1"><RouterView /></main>
  <footer class="site-footer"><span>知诈 · 在情景中识别风险</span><span>AI反诈情景推演平台</span></footer>
  </template>
</template>
