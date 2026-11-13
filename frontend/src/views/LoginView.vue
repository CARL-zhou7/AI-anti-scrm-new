<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { ApiError } from '../api/http'
import type { FieldError } from '../api/types'
import FormFieldError from '../components/FormFieldError.vue'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()
const username = ref(''), password = ref(''), error = ref('')
const fields = ref<FieldError[]>([])
const busy = ref(false)
async function reconnect() {
  await auth.bootstrap()
  if (auth.account) await router.replace(auth.landing)
}
async function submit() {
  if (busy.value) return
  busy.value = true; error.value = ''; fields.value = []
  try {
    await auth.signIn({ username: username.value, password: password.value })
    password.value = ''
    await router.replace(auth.landing)
  } catch (e) {
    error.value = e instanceof Error ? e.message : '登录失败，请稍后重试'
    if (e instanceof ApiError) fields.value = e.fieldErrors
  } finally { busy.value = false }
}
</script>

<template>
  <div class="auth-layout">
    <section class="auth-story">
      <span class="eyebrow">知诈情景演播室</span>
      <h2>话术在推进，<br>风险有迹可循</h2>
      <p>在模拟对话中识别试探、诱导与施压。</p>
    </section>
    <section class="auth-form-panel" aria-labelledby="login-heading"><div class="form-heading"><h1 id="login-heading">登录知诈</h1><p>普通用户和管理员共用此入口。</p></div>
      <p v-if="route.query.registered === '1'" class="success-message" role="status">注册成功，请使用新账号登录。</p>
      <p v-if="auth.bootstrapError" class="error-message" role="alert">{{ auth.bootstrapError }} <button class="text-button" @click="reconnect">重新连接</button></p>
      <form @submit.prevent="submit" :aria-busy="busy">
        <label for="username">用户名</label><input id="username" v-model="username" name="username" autocomplete="username" placeholder="请输入用户名" required :aria-invalid="fields.some(e => e.field === 'username')" aria-describedby="username-error" /><FormFieldError field="username" :errors="fields" />
        <label for="password">密码</label><input id="password" v-model="password" name="password" type="password" autocomplete="current-password" placeholder="请输入密码" required :aria-invalid="fields.some(e => e.field === 'password')" aria-describedby="password-error" /><FormFieldError field="password" :errors="fields" />
        <p v-if="error" class="error-message" role="alert">{{ error }}</p>
        <button class="button button-primary form-submit" type="submit" data-testid="login-submit" :disabled="busy" :aria-busy="busy">{{ busy ? '正在登录…' : '登录' }}<span v-if="!busy" aria-hidden="true"> →</span></button>
      </form>
      <RouterLink class="button button-light practice-register" to="/register/practice">注册（点我！点我！）</RouterLink>
      <p class="auth-switch">还没有账号？<RouterLink to="/register">注册普通用户账号</RouterLink></p>
    </section>
  </div>
</template>

<style scoped>
.practice-register { width:100%; margin-top:10px; }
</style>
