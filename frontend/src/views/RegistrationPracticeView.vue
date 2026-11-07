<script setup lang="ts">
import { useRouter } from 'vue-router'

defineProps<{ revealed?: boolean }>()
const router = useRouter()
const fields = [
  { id: 'alias', label: '用户名', example: '例如：请取一个好听的名字' },
  { id: 'mailbox', label: '邮箱账号', example: '例如：xxx@example.com' },
  { id: 'phone', label: '手机号', example: '例如：11位电话号码' },
  { id: 'region', label: '地区位置', example: '例如：xx省xx市' },
  { id: 'identity', label: '身份证号', example: '例如：330102********123X' },
  { id: 'passphrase', label: '密码', example: '请输入密码', type: 'password' },
]

function reveal(event: Event) {
  const form = event.currentTarget as HTMLFormElement
  if (!form.reportValidity()) return
  form.reset()
  void router.replace('/register/practice/result')
}
</script>

<template>
  <div class="auth-layout auth-register">
    <section class="auth-story">
      <span class="eyebrow">开始你的第一场推演</span>
      <h2>从第一次观察，<br>学会停下来核实</h2>
      <p>在模拟对话中识别试探、诱导与施压。</p>
    </section>
    <section v-if="revealed" class="auth-form-panel" aria-labelledby="practice-result-heading">
      <div class="form-heading">
        <span class="eyebrow">反诈练习结果</span>
        <h1 id="practice-result-heading">哈哈哈！你被骗啦！</h1>
        <p>注意查看那些看似正常的东西，不要太好奇，疏忽和好奇可能就是你被诈骗的第一步……</p>
      </div>
      <p>真正注册时，请点击登录页「还没有账号？」旁的「注册普通用户账号」。</p>
      <p class="field-help">您填写的内容未被记录，请以后小心哦！</p>
      <RouterLink class="button button-primary form-submit" to="/register">注册普通用户账号</RouterLink>
      <p class="auth-switch"><RouterLink to="/login">返回登录</RouterLink></p>
    </section>
    <section v-else class="auth-form-panel" aria-labelledby="practice-register-heading">
      <RouterLink class="back-link" to="/login">← 返回登录</RouterLink>
      <div class="form-heading">
        <h1 id="practice-register-heading">注册学习账号</h1>
      </div>
      <!-- The exercise checks validity only. Inputs deliberately have no names to serialize. -->
      <form autocomplete="off" @submit.prevent="reveal">
        <template v-for="field in fields" :key="field.id">
          <label :for="'practice-' + field.id">{{ field.label }}</label>
          <input :id="'practice-' + field.id" :type="field.type ?? 'text'" :placeholder="field.example"
            autocomplete="off" pattern=".*\S.*" required />
        </template>
        <button type="submit" class="button button-primary form-submit">注册账号 <span aria-hidden="true">→</span></button>
      </form>
      <p class="auth-switch">已有账号？<RouterLink to="/login">直接登录</RouterLink></p>
    </section>
  </div>
</template>
