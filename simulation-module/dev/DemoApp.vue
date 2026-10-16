<script setup>
// 本地联调外壳（不并入主工程）：用 hash 路由模拟“配置页 → 演绎页 → 复盘页/历史页”的跳转，
// 配置页只是为了能点“开始演绎”，真实配置页由配置模块实现。
import { onMounted, onBeforeUnmount, reactive, ref, watch, computed } from 'vue'
import { SimulationPage, useSimulationStarter } from '../src/index.js'

const route = ref(parse())
function parse() {
  const h = location.hash.replace(/^#/, '') || '/'
  const m = h.match(/^\/(sim|report|history)\/(\d+)$/)
  return m ? { name: m[1], id: m[2] } : { name: 'config' }
}
function go(hash) {
  location.hash = hash
}
const onHash = () => (route.value = parse())
onMounted(() => window.addEventListener('hashchange', onHash))
onBeforeUnmount(() => window.removeEventListener('hashchange', onHash))

// 调试选项通过 URL 查询参数传入，例如 ?speed=fast&fail=audio&limit=40
const params = new URLSearchParams(location.search)
const mockOptions = reactive({
  speed: params.get('speed') || 'normal',
  fail: params.get('fail') || '',
  limitSeconds: Number(params.get('limit') || 600),
})
async function applyMock() {
  await fetch('/__mock/config', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': 'mock-csrf-token' },
    body: JSON.stringify({ ...mockOptions, fail: mockOptions.fail || null }),
  })
}
watch(mockOptions, applyMock, { deep: true })
onMounted(applyMock)
// “人物动画失效”故障注入：直接让人物动画不运行，检验 ANIMATION_FAILED 上报
const breakAnimation = computed(() => mockOptions.fail === 'animation')
const breakStyle = document.createElement('style')
breakStyle.textContent = '.sim-figure__body { animation: none !important; }'
watch(breakAnimation, (on) => (on ? document.head.appendChild(breakStyle) : breakStyle.remove()), { immediate: true })

const lists = reactive({ scenarios: [], scammers: [], victims: [] })
const pick = reactive({ scenarioId: '1', scammerProfileId: '11', victimProfileId: '21' })
onMounted(async () => {
  const get = (p) => fetch('/anti.scam/v1' + p).then((r) => r.json()).then((b) => b.data.items)
  ;[lists.scenarios, lists.scammers, lists.victims] = await Promise.all([get('/scenarios'), get('/scammer-profiles'), get('/victim-profiles')])
})

const starter = useSimulationStarter()
const startMessage = ref('')
async function onStart() {
  const result = await starter.start({ ...pick })
  if (result.ok) return go(`/sim/${result.sessionId}`)
  startMessage.value = result.message
  if (result.activeSessionId) startMessage.value += `（进行中的演绎：${result.activeSessionId}）`
}
</script>

<template>
  <SimulationPage
    v-if="route.name === 'sim'"
    :key="route.id"
    :id="route.id"
    :timing="mockOptions.speed === 'fast' ? { openingMs: 1600 } : null"
    @report="(id) => go(`/report/${id}`)"
    @history="(id) => go(`/history/${id}`)"
    @restart="go('/')"
    @home="go('/')"
    @unauthenticated="startMessage = '请重新登录'"
  />

  <div v-else-if="route.name === 'report' || route.name === 'history'" class="demo-shell">
    <h1>{{ route.name === 'report' ? '复盘页' : '历史详情页' }}（演绎 {{ route.id }}）</h1>
    <p>此页面由{{ route.name === 'report' ? '分析复盘' : '历史记录' }}模块实现，这里只是联调占位。</p>
    <button type="button" @click="go('/')">回到配置页</button>
  </div>

  <div v-else class="demo-shell">
    <h1>AI情景演绎 · 本地联调</h1>
    <p class="demo-hint">真实配置页由“场景画像配置”模块实现；这里只提供选择与“开始演绎”按钮，用来驱动演绎模块与模拟后端。</p>

    <section>
      <h2>场景</h2>
      <label v-for="s in lists.scenarios" :key="s.id" class="demo-option">
        <input v-model="pick.scenarioId" type="radio" :value="s.id" /> {{ s.name }} <small>{{ s.focus }}</small>
      </label>
    </section>
    <section>
      <h2>骗子画像</h2>
      <label v-for="p in lists.scammers" :key="p.id" class="demo-option">
        <input v-model="pick.scammerProfileId" type="radio" :value="p.id" /> <img :src="p.avatar.url" alt="" /> {{ p.name }}
      </label>
    </section>
    <section>
      <h2>受害者画像</h2>
      <label v-for="p in lists.victims" :key="p.id" class="demo-option">
        <input v-model="pick.victimProfileId" type="radio" :value="p.id" /> <img :src="p.avatar.url" alt="" /> {{ p.name }}
      </label>
    </section>
    <section>
      <h2>模拟后端选项</h2>
      <label class="demo-option">节奏
        <select v-model="mockOptions.speed"><option value="normal">正常</option><option value="fast">快速</option></select>
      </label>
      <label class="demo-option">故障注入
        <select v-model="mockOptions.fail">
          <option value="">无</option>
          <option value="audio">第2轮受害者语音加载失败</option>
          <option value="tts">第2轮受害者 TTS 失败（服务端）</option>
          <option value="avatar">受害者头像加载失败</option>
          <option value="animation">人物动画失效</option>
        </select>
      </label>
      <label class="demo-option">时长上限（秒）<input v-model.number="mockOptions.limitSeconds" type="number" min="10" max="600" /></label>
    </section>

    <button type="button" class="demo-start" :disabled="!starter.canStart.value" @click="onStart">
      {{ starter.phase.value === 'starting' ? '正在开始…' : starter.phase.value === 'preparing' ? '准备中…' : '开始演绎' }}
    </button>
    <p v-if="startMessage || starter.errorMessage.value" class="demo-error">{{ startMessage || starter.errorMessage.value }}</p>
  </div>
</template>

<style>
.demo-shell {
  max-width: 760px;
  margin: 0 auto;
  padding: 32px 24px;
  color: #e9ebf3;
  font-family: 'PingFang SC', 'Microsoft YaHei', system-ui, sans-serif;
}
.demo-shell h2 {
  font-size: 15px;
  margin: 22px 0 8px;
  color: #a9aec0;
}
.demo-hint {
  color: #a9aec0;
}
.demo-option {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin: 4px 14px 4px 0;
}
.demo-option img {
  width: 28px;
  height: 34px;
  border-radius: 6px;
  object-fit: cover;
}
.demo-start {
  margin-top: 28px;
  padding: 10px 28px;
  border-radius: 10px;
  border: 0;
  background: #e8a33d;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
}
.demo-start[disabled] {
  opacity: 0.5;
}
.demo-error {
  color: #ff9a8a;
}
</style>
