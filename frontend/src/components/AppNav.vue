<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const router = useRouter()
const busy = ref(false)
const error = ref('')
async function logout() {
  busy.value = true; error.value = ''
  try { await auth.signOut(); await router.replace('/login') }
  catch (e) { error.value = e instanceof Error ? e.message : '退出失败，请重试' }
  finally { busy.value = false }
}
</script>

<template>
  <header class="site-header">
    <div class="header-inner">
    <RouterLink class="brand" :to="auth.account ? auth.landing : '/login'" aria-label="知诈首页">
      <span class="brand-mark" aria-hidden="true"><svg viewBox="0 0 24 24" width="21" height="21" fill="none" stroke="currentColor" stroke-width="1.6"><path d="M12 22s7-3 7-9V5l-7-3-7 3v8c0 6 7 9 7 9Z"/><path d="m9 12 2 2 4-4"/></svg></span>
      <span>知诈<small>AI 反诈情景推演</small></span>
    </RouterLink>
    <nav v-if="auth.account?.role === 'USER'" aria-label="主导航">
      <RouterLink to="/home">首页</RouterLink><RouterLink to="/configuration">开始体验</RouterLink><RouterLink to="/history">个人历史</RouterLink>
    </nav>
    <div v-if="auth.account" class="account-actions"><span class="username">{{ auth.account.username }}</span>
      <button class="text-button" data-testid="nav-logout" :disabled="busy" :aria-busy="busy" @click="logout">{{ busy ? '退出中…' : '退出登录' }}</button>
    </div>
    <span v-else class="header-note">多一分识别，少一分风险</span>
    </div>
  </header>
  <p v-if="error" class="global-error" role="alert">{{ error }}</p>
</template>
