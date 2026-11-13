<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import AppNav from './components/AppNav.vue'
import AdminNav from './components/AdminNav.vue'

const { currentRoute } = useRouter()
const main = ref<HTMLElement>()
const pageMotion = ref('route-fade')

watch(currentRoute, (to, from) => {
  document.title = `${String(to.meta.title || '首页')} · AI反诈情景推演平台`
  if (to.path === from?.path) return
  const depth = Number(to.meta.depth ?? 0) - Number(from?.meta.depth ?? 0)
  pageMotion.value = from?.meta.fullscreen ? 'route-from-stage' : depth > 0 ? 'route-deeper' : depth < 0 ? 'route-back' : 'route-fade'
}, { immediate: true })
watch(() => currentRoute.value.path, () => main.value?.focus({ preventScroll: true }), { flush: 'post' })
</script>

<template>
  <RouterView v-slot="{ Component, route }">
    <template v-if="!route.meta.fullscreen">
      <a class="skip-link" href="#main">跳转到主要内容</a>
      <AppNav />
      <AdminNav v-if="route.meta.role === 'ADMIN'" />
      <main id="main" ref="main" tabindex="-1">
        <Transition :name="pageMotion" leave-active-class="route-discard" appear>
          <component :is="Component" :key="route.path" />
        </Transition>
      </main>
      <footer class="site-footer"><span>知诈 · 在情景中识别风险</span><span>AI反诈情景推演平台</span></footer>
    </template>
    <Transition name="route-stage" @before-leave="el => el.setAttribute('inert', '')">
      <component :is="Component" v-if="route.meta.fullscreen" :key="route.path" />
    </Transition>
  </RouterView>
</template>
