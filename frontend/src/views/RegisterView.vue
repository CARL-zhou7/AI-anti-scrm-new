<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { authApi } from '../api/auth'
import { ApiError } from '../api/http'
import type { FieldError } from '../api/types'
import FormFieldError from '../components/FormFieldError.vue'

const router = useRouter()
const username = ref(''), password = ref(''), confirmPassword = ref(''), error = ref('')
const fields = ref<FieldError[]>([]), busy = ref(false)
async function submit() {
  if (busy.value) return
  busy.value = true; error.value = ''; fields.value = []
  if (password.value !== confirmPassword.value) {
    fields.value = [{ field: 'confirmPassword', code: 'PASSWORD_MISMATCH', message: '两次密码输入不一致' }]
    busy.value = false; return
  }
  try {
    await authApi.register({ username: username.value, password: password.value, confirmPassword: confirmPassword.value })
    password.value = ''; confirmPassword.value = ''
    await router.replace({ path: '/login', query: { registered: '1' } })
  } catch (e) {
    error.value = e instanceof Error ? e.message : '注册失败，请稍后重试'
    if (e instanceof ApiError) fields.value = e.fieldErrors
  } finally { busy.value = false }
}
</script>

<template>
  <section class="register-card" aria-labelledby="register-heading"><RouterLink class="back-link" to="/login">← 返回登录</RouterLink><div class="form-heading"><span class="eyebrow">从这里开始</span><h1 id="register-heading">创建你的账号</h1><p>注册普通用户账号，开启反诈学习之旅</p></div>
    <form @submit.prevent="submit" :aria-busy="busy">
      <label for="username">用户名</label><input id="username" v-model="username" autocomplete="username" placeholder="为自己取一个用户名" required aria-describedby="username-help username-error" :aria-invalid="fields.some(e => e.field === 'username')" /><p id="username-help" class="field-help">2—24个字符，支持汉字、英文字母、数字、下划线；英文不区分大小写。</p><FormFieldError field="username" :errors="fields" />
      <label for="password">密码</label><input id="password" v-model="password" type="password" autocomplete="new-password" placeholder="请输入6—24个字符" required aria-describedby="password-help password-error" :aria-invalid="fields.some(e => e.field === 'password')" /><p id="password-help" class="field-help">6—24个字符，允许空格，首尾空格会原样保留。</p><FormFieldError field="password" :errors="fields" />
      <label for="confirmPassword">确认密码</label><input id="confirmPassword" v-model="confirmPassword" type="password" autocomplete="new-password" placeholder="请再次输入密码" required aria-describedby="confirmPassword-error" :aria-invalid="fields.some(e => e.field === 'confirmPassword')" /><FormFieldError field="confirmPassword" :errors="fields" />
      <p v-if="error" class="error-message" role="alert">{{ error }}</p><button type="submit" class="button button-primary form-submit" data-testid="register-submit" :disabled="busy">{{ busy ? '正在创建…' : '注册账号' }}<span aria-hidden="true"> →</span></button>
    </form><p class="auth-switch">已有账号？<RouterLink to="/login">直接登录</RouterLink></p>
  </section>
</template>
