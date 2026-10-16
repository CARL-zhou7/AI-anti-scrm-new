<script setup>
// 本地联调外壳（不并入主工程）：用 hash 路由模拟“配置页 → 演绎页 → 复盘页/历史页”的跳转，
// 右下角“联调选项”控制模拟后端（节奏、故障注入、时长上限、语音来源）。
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { SimulationPage, SimulationSetupPage } from '../src/index.js'

const route = ref(parse())
function parse() {
  const h = location.hash.replace(/^#/, '') || '/'
  const m = h.match(/^\/(sim|report|history)\/(\d+)$/)
  return m ? { name: m[1], id: m[2] } : { name: 'setup' }
}
function go(hash) {
  location.hash = hash
}
const onHash = () => (route.value = parse())
onMounted(() => window.addEventListener('hashchange', onHash))
onBeforeUnmount(() => window.removeEventListener('hashchange', onHash))

// 调试选项也可通过 URL 查询参数传入，例如 ?speed=fast&fail=audio&limit=40&tts=tone
const params = new URLSearchParams(location.search)
const mockOptions = reactive({
  speed: params.get('speed') || 'normal',
  fail: params.get('fail') || '',
  limitSeconds: Number(params.get('limit') || 600),
  tts: params.get('tts') || 'auto',
})
const panelOpen = ref(false)
const ttsStatus = ref(null)

async function applyMock() {
  await fetch('/__mock/config', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': 'mock-csrf-token' },
    body: JSON.stringify({ ...mockOptions, fail: mockOptions.fail || null }),
  })
}
async function refreshStatus() {
  try {
    const body = await fetch('/__mock/status').then((r) => r.json())
    ttsStatus.value = body.data.tts
  } catch {
    ttsStatus.value = null
  }
}
watch(mockOptions, applyMock, { deep: true })
onMounted(applyMock)
const statusTimer = setInterval(refreshStatus, 3000)
onBeforeUnmount(() => clearInterval(statusTimer))

const ttsLabel = computed(() => {
  const s = ttsStatus.value
  if (!s) return '尚未合成语音'
  if (!s.provider) return s.lastError ? `合成失败：${s.lastError}` : '尚未合成语音'
  if (s.provider === 'tone') return s.degraded ? `提示音（真实语音不可用：${s.errors.join('；') || s.lastError}）` : '提示音'
  return s.degraded ? `${s.label}（前面的来源不可用：${s.errors.join('；')}）` : s.label
})

// “人物动画失效”故障注入：直接让人物动画不运行，检验 ANIMATION_FAILED 上报
const breakStyle = document.createElement('style')
breakStyle.textContent = '.sim-figure__body { animation: none !important; }'
watch(
  () => mockOptions.fail === 'animation',
  (on) => (on ? document.head.appendChild(breakStyle) : breakStyle.remove()),
  { immediate: true },
)
</script>

<template>
  <SimulationSetupPage
    v-if="route.name === 'setup'"
    @started="(id) => go(`/sim/${id}`)"
    @active="(id) => go(`/sim/${id}`)"
    @home="go('/')"
  />

  <SimulationPage
    v-else-if="route.name === 'sim'"
    :key="route.id"
    :id="route.id"
    :timing="mockOptions.speed === 'fast' ? { openingMs: 1600 } : null"
    @report="(id) => go(`/report/${id}`)"
    @history="(id) => go(`/history/${id}`)"
    @restart="go('/')"
    @home="go('/')"
  />

  <div v-else class="demo-placeholder">
    <h1>{{ route.name === 'report' ? '复盘页' : '历史详情页' }}（演绎 {{ route.id }}）</h1>
    <p>这个页面由{{ route.name === 'report' ? '分析复盘' : '历史记录' }}模块实现，这里只是联调占位。</p>
    <button type="button" @click="go('/')">回到配置页</button>
  </div>

  <div class="demo-dev" :class="{ 'is-open': panelOpen }">
    <button class="demo-dev__toggle" type="button" @click="panelOpen = !panelOpen">联调选项 {{ panelOpen ? '▾' : '▴' }}</button>
    <div v-if="panelOpen" class="demo-dev__body">
      <label>节奏
        <select v-model="mockOptions.speed"><option value="normal">正常</option><option value="fast">快速</option></select>
      </label>
      <label>语音来源
        <select v-model="mockOptions.tts">
          <option value="auto">自动（Azure → Edge → Windows 语音 → 提示音）</option>
          <option value="azure">仅 Azure 语音服务（需配置密钥）</option>
          <option value="edge">仅 Edge 朗读服务</option>
          <option value="sapi">仅 Windows 系统语音</option>
          <option value="tone">仅提示音</option>
        </select>
      </label>
      <label>故障注入
        <select v-model="mockOptions.fail">
          <option value="">无</option>
          <option value="audio">第 2 轮受害者语音加载失败</option>
          <option value="tts">第 2 轮受害者 TTS 失败（服务端）</option>
          <option value="avatar">受害者头像加载失败</option>
          <option value="animation">人物动画失效</option>
        </select>
      </label>
      <label>时长上限（秒）<input v-model.number="mockOptions.limitSeconds" type="number" min="10" max="600" /></label>
    </div>
    <div class="demo-dev__status">语音：{{ ttsLabel }}</div>
  </div>
</template>

<style>
.demo-placeholder {
  max-width: 640px;
  margin: 80px auto;
  padding: 0 24px;
  color: #1c2333;
  font-family: 'PingFang SC', 'Microsoft YaHei', system-ui, sans-serif;
}
.demo-dev {
  position: fixed;
  right: 16px;
  bottom: 16px;
  z-index: 100;
  width: 300px;
  padding: 10px 12px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.96);
  border: 1px solid #e3e6ef;
  box-shadow: 0 8px 24px rgba(28, 35, 51, 0.12);
  font: 12px/1.5 'PingFang SC', 'Microsoft YaHei', system-ui, sans-serif;
  color: #5e6678;
}
.demo-dev__toggle {
  border: 0;
  background: none;
  padding: 0;
  font: inherit;
  font-weight: 600;
  color: #1c2333;
  cursor: pointer;
}
.demo-dev__body {
  display: grid;
  gap: 6px;
  margin: 8px 0;
}
.demo-dev__body label {
  display: grid;
  gap: 2px;
}
.demo-dev__status {
  margin-top: 4px;
  word-break: break-all;
}
</style>
