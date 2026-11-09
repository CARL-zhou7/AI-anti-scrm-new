<script setup>
import { ref } from 'vue'
import { onHide, onReady, onUnload } from '@dcloudio/uni-app'
import { get, login, write } from '../api'
import { errorText } from '../ui'

const mode = ref('login'), username = ref(''), password = ref(''), confirmPassword = ref('')
const busy = ref(false), visible = ref(false), confirmVisible = ref(false), error = ref(''), registered = ref(false)
const drill = ref([
  ['用户名', '示例：体验者01'],
  ['邮箱账号', '示例：learner@example.com'],
  ['手机号', '示例：000 0000 0000'],
  ['地区位置', '示例：青禾市'],
  ['身份证号', '示例：TEST00000000000000'],
  ['密码', '示例：Demo123456']
].map(([label, example]) => ({ label, example, value: '' })))

function clearDrill() {
  drill.value.forEach(field => { field.value = '' })
}

function change(next) {
  clearDrill()
  mode.value = next
  password.value = confirmPassword.value = ''
  visible.value = confirmVisible.value = false
  error.value = ''
  registered.value = false
}

async function submit() {
  if (busy.value) return
  error.value = ''
  const data = { username: username.value, password: password.value }
  if (!data.username.trim() || !data.password) { error.value = '请输入用户名和密码'; return }
  if (mode.value === 'register' && password.value !== confirmPassword.value) { error.value = '两次密码输入不一致'; return }
  busy.value = true
  try {
    if (mode.value === 'register') {
      await write('/auth/register', { ...data, confirmPassword: confirmPassword.value })
      change('login')
      registered.value = true
    } else {
      await login(data)
      password.value = ''
      await uni.redirectTo({ url: '/pages/home' })
    }
  } catch (cause) { error.value = errorText(cause) }
  finally { busy.value = false }
}

onReady(async () => {
  busy.value = true
  try {
    if ((await get('/auth/me')).role === 'USER') await uni.redirectTo({ url: '/pages/home' })
  } catch (cause) {
    if (cause.status !== 401) error.value = errorText(cause)
  } finally { busy.value = false }
})
onHide(clearDrill)
onUnload(clearDrill)
</script>

<template>
  <view class="auth-shell">
    <image v-if="mode !== 'result'" class="auth-picture" :src="mode === 'login' ? '/static/login.jpg' : '/static/signup.jpg'" mode="widthFix" />
    <view v-if="mode === 'result'" class="surface auth-card result">
      <image class="shield" src="/static/drill-shield.jpg" mode="widthFix" />
      <text class="auth-title center">反诈练习结果</text>
      <text class="result-callout">哈哈哈！你被骗啦！</text>
      <view class="result-copy">
        <text class="block">注意查看那些看似正常的东西，不要太好奇，疏忽和好奇可能就是你被骗的第一步……</text>
        <text class="block">真正注册时，请点击登录页「还没有账号？」旁的「注册普通用户账号」。</text>
        <text class="muted block">您填写的内容未被记录，请以后小心哦！</text>
      </view>
      <view class="auth-actions">
        <button @tap="change('register')">注册普通用户账号</button>
        <button class="link" @tap="change('login')">返回登录</button>
      </view>
    </view>
    <view v-else class="surface auth-card overlap" :class="mode">
      <text class="auth-title">{{ mode === 'login' ? '登录' : '注册学习账号' }}</text>
      <view v-if="mode === 'login'" class="underline" />
      <template v-if="mode === 'drill'">
        <view class="drill-fields">
          <view v-for="field in drill" :key="field.label" class="row drill-field">
            <text class="field-name">{{ field.label }}</text>
            <view class="field-control grow">
              <input v-model="field.value" :password="field.label === '密码' && !visible" :aria-label="field.label" maxlength="80" :placeholder="field.example" />
              <button v-if="field.label === '密码'" class="eye" :aria-label="visible ? '隐藏密码' : '显示密码'" @tap="visible = !visible">
                <image :src="visible ? '/static/eye.svg' : '/static/eye-off.svg'" mode="aspectFit" />
              </button>
            </view>
          </view>
        </view>
        <view class="auth-actions"><button @tap="change('result')">注册账号</button><button class="link" @tap="change('login')">返回登录</button></view>
      </template>
      <template v-else>
        <text v-if="mode === 'register'" class="muted block">恭喜进入真正的注册界面！</text>
        <text v-if="registered" class="muted block">注册成功，网页和小程序均可使用此账号登录。</text>
        <text class="field-name">用户名</text>
        <input v-model="username" aria-label="用户名" maxlength="48" :placeholder="mode === 'register' ? '示例：体验者01' : ''" />
        <text class="field-name">密码</text>
        <view class="field-control">
          <input v-model="password" aria-label="密码" :password="!visible" maxlength="48" :placeholder="mode === 'register' ? '6 至 24 个字符' : ''" @confirm="mode === 'login' && submit()" />
          <button class="eye" :aria-label="visible ? '隐藏密码' : '显示密码'" @tap="visible = !visible"><image :src="visible ? '/static/eye.svg' : '/static/eye-off.svg'" mode="aspectFit" /></button>
        </view>
        <template v-if="mode === 'register'">
          <text class="field-name">确认密码</text>
          <view class="field-control">
            <input v-model="confirmPassword" aria-label="确认密码" :password="!confirmVisible" maxlength="48" placeholder="再次输入相同的密码" @confirm="submit" />
            <button class="eye" :aria-label="confirmVisible ? '隐藏确认密码' : '显示确认密码'" @tap="confirmVisible = !confirmVisible"><image :src="confirmVisible ? '/static/eye.svg' : '/static/eye-off.svg'" mode="aspectFit" /></button>
          </view>
        </template>
        <text v-if="error" class="error">{{ error }}</text>
        <view class="auth-actions">
          <button :loading="busy" :disabled="busy" @tap="submit">{{ mode === 'login' ? '登录' : '注册账号' }}</button>
          <template v-if="mode === 'login'">
            <button class="ghost lure" :disabled="busy" @tap="change('drill')">注册（点我！点我！）</button>
            <view class="signup-link"><text>还没有账号？</text><text class="text-link" @tap="!busy && change('register')">注册普通用户账号</text></view>
          </template>
          <button v-else class="link" :disabled="busy" @tap="change('login')">返回登录</button>
        </view>
      </template>
    </view>
  </view>
</template>

<style scoped>
.auth-shell { min-height: 100vh; background: #10283b; display: flex; flex-direction: column; }
.auth-picture { display: block; width: 100%; }
.auth-card { flex: 1; padding: 26rpx 46rpx calc(34rpx + env(safe-area-inset-bottom)); background: #e8f3fc; border-radius: 26rpx 26rpx 0 0; }
.auth-card.overlap { margin-top: -10rpx; }
.auth-title { display: block; font-size: 62rpx; font-weight: 800; line-height: 1.18; color: #112739; }
.underline { width: 83rpx; height: 8rpx; background: #dc814c; margin: 12rpx 0 20rpx; }
.field-name { display: block; font-weight: 700; font-size: 33rpx; margin: 13rpx 0 3rpx; color: #131b25; }
.auth-card input { height: 66rpx; border-color: #b6cbd8; border-radius: 12rpx; font-size: 29rpx; padding: 6rpx 14rpx; }
.field-control { position: relative; }
.field-control input { padding-right: 67rpx; }
.eye { position: absolute; top: 0; right: 0; width: 75rpx; height: 100%; padding: 0; display: grid; place-items: center; background: transparent; }
.eye image { width: 37rpx; height: 37rpx; }
.auth-actions { margin: 38rpx -9rpx 0; }
.auth-actions button { font-size: 37rpx; font-weight: 750; border-radius: 16rpx; min-height: 88rpx; }
.auth-actions .lure { margin-top: 20rpx; border: 3rpx solid #182638; }
.auth-actions .link, .signup-link { margin-top: 12rpx; min-height: 72rpx; text-align: center; font-size: 32rpx; }
.auth-actions .link, .text-link { color: #1b5a98; text-decoration: underline; text-underline-offset: 5rpx; }
.signup-link { display: flex; align-items: center; justify-content: center; gap: 10rpx; font-weight: 700; }
.drill-fields { margin: 28rpx 0 0; }
.drill-field { margin-top: 14rpx; gap: 14rpx; }
.drill-field .field-name { width: 171rpx; flex-shrink: 0; margin: 0; }
.drill-field input { height: 57rpx; font-size: 25rpx; }
.drill .auth-actions { margin-top: 26rpx; }
.result { margin-top: 18rpx; padding-top: 22rpx; }
.shield { display: block; width: 396rpx; margin: 0 auto 8rpx; }
.result-callout { display: block; color: #963c12; background: #ffdabf; font-size: 60rpx; font-weight: 800; text-align: center; padding: 19rpx 2rpx; margin: 39rpx -22rpx 22rpx; border-radius: 24rpx; line-height: 1.2; }
.result-copy { font-size: 32rpx; line-height: 1.4; }
.result-copy .block + .block { margin-top: 24rpx; }
.result-copy .muted { font-size: 29rpx; }
</style>
