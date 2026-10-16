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
    <section class="auth-story"><span class="eyebrow">看见话术 · 理解风险</span><h1>在情景中学习，<br>让骗局止于识别。</h1><p>AI反诈情景推演平台</p><div class="story-illustration" aria-hidden="true"><div class="orbit orbit-one"></div><div class="orbit orbit-two"></div><span class="shield">✓</span><span class="story-tag tag-one">识别话术</span><span class="story-tag tag-two">感知风险</span></div><span class="story-footnote">从旁观一次骗局，到多一份防范意识。</span></section>
    <section class="auth-form-panel" aria-labelledby="login-heading"><div class="form-heading"><span class="eyebrow">欢迎回来</span><h2 id="login-heading">登录你的账号</h2><p>用户与管理员共用此登录入口</p></div>
      <p v-if="route.query.registered === '1'" class="success-message" role="status">注册成功，请使用新账号登录。</p>
      <p v-if="auth.bootstrapError" class="error-message" role="alert">{{ auth.bootstrapError }} <button class="text-button" @click="reconnect">重新连接</button></p>
      <form @submit.prevent="submit" :aria-busy="busy">
        <label for="username">用户名</label><input id="username" v-model="username" name="username" autocomplete="username" placeholder="请输入用户名" required :aria-invalid="fields.some(e => e.field === 'username')" aria-describedby="username-error" /><FormFieldError field="username" :errors="fields" />
        <label for="password">密码</label><input id="password" v-model="password" name="password" type="password" autocomplete="current-password" placeholder="请输入密码" required :aria-invalid="fields.some(e => e.field === 'password')" aria-describedby="password-error" /><FormFieldError field="password" :errors="fields" />
        <p v-if="error" class="error-message" role="alert">{{ error }}</p>
        <button class="button button-primary form-submit" type="submit" data-testid="login-submit" :disabled="busy">{{ busy ? '正在登录…' : '登录' }}<span aria-hidden="true"> →</span></button>
      </form>
      <p class="auth-switch">还没有账号？<RouterLink to="/register">注册普通用户账号</RouterLink></p>
    </section>
  </div>
</template>
