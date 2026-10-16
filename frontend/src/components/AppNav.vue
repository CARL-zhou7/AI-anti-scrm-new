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
    <RouterLink class="brand" :to="auth.account ? auth.landing : '/login'" aria-label="知诈首页">
      <span class="brand-mark" aria-hidden="true">知</span><span>知诈<small>AI反诈情景推演</small></span>
    </RouterLink>
    <nav v-if="auth.account" aria-label="主导航">
      <template v-if="auth.account.role === 'USER'">
        <RouterLink to="/home">首页</RouterLink><RouterLink to="/configuration">开始体验</RouterLink><RouterLink to="/history">个人历史</RouterLink>
      </template>
      <RouterLink v-else to="/admin">管理后台</RouterLink>
    </nav>
    <div v-if="auth.account" class="account-actions"><span class="username">{{ auth.account.username }}</span>
      <button class="text-button" data-testid="nav-logout" :disabled="busy" @click="logout">{{ busy ? '退出中…' : '退出登录' }}</button>
    </div>
    <span v-else class="header-note">多一分识别，少一分风险</span>
  </header>
  <p v-if="error" class="global-error" role="alert">{{ error }}</p>
</template>
