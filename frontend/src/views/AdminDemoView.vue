<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { contentApi } from '../api/content'
import type { Scenario, ScammerProfilePayload, VictimProfilePayload } from '../api/types'

// Entry point for the administrator demonstration: pick a scenario and both profiles, then run the review flow.
const router = useRouter()
const scenarios = ref<Scenario[]>([])
const scammers = ref<ScammerProfilePayload[]>([])
const victims = ref<VictimProfilePayload[]>([])
const scenarioId = ref(''), scammerId = ref(''), victimId = ref('')
const error = ref(''), loading = ref(true)

const message = (e: unknown) => (e instanceof Error ? e.message : '加载失败，请重试')

onMounted(async () => {
  try {
    scenarios.value = await contentApi.listScenarios()
    scenarioId.value = scenarios.value[0]?.id ?? ''
    const [s, v] = await Promise.all([contentApi.listScammerProfiles(), contentApi.listVictimProfiles()])
    scammers.value = s.items
    victims.value = v.items
    scammerId.value = scammers.value[0]?.id ?? ''
    victimId.value = victims.value[0]?.id ?? ''
  } catch (e) { error.value = message(e) } finally { loading.value = false }
})

function run() {
  if (!scenarioId.value || !scammerId.value || !victimId.value) { error.value = '请选择场景与两个画像'; return }
  void router.push({ path: '/admin/demo/run', query: {
    scenarioId: scenarioId.value, scammerProfileId: scammerId.value, victimProfileId: victimId.value } })
}
</script>

<template>
  <div class="home-layout admin-layout" data-testid="admin-demo">
    <span class="eyebrow">管理员演示测试</span>
    <h1>演示测试</h1>
    <p class="field-help">仅本人可访问。第一轮自动演绎；从第二轮起，上方保留上一轮双方对话，点击解说框继续。紫色魔法球可修改待确认台词，或按方向重新生成话术和动作。未确认时刷新按主动退出处理。</p>
    <p v-if="loading" role="status">正在加载可选内容…</p>
    <p v-else-if="error" role="alert" data-testid="admin-demo-error">{{ error }}</p>
    <form v-else class="demo-form admin-panel" @submit.prevent="run">
      <h2>配置演示场次</h2>
      <label class="demo-scene">场景
        <select v-model="scenarioId" data-testid="admin-demo-scenario">
          <option v-for="s in scenarios" :key="s.id" :value="s.id">{{ s.name }}</option>
        </select>
      </label>
      <label>骗子画像
        <select v-model="scammerId" data-testid="admin-demo-scammer">
          <option v-for="s in scammers" :key="s.id" :value="s.id">{{ s.name }}</option>
        </select>
      </label>
      <label>受害者画像
        <select v-model="victimId" data-testid="admin-demo-victim">
          <option v-for="v in victims" :key="v.id" :value="v.id">{{ v.name }}</option>
        </select>
      </label>
      <button class="button button-primary" type="submit" data-testid="admin-demo-run">开始演示测试</button>
    </form>
  </div>
</template>

<style scoped>
.demo-form { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:13px 18px; max-width:840px; margin-top:18px; }
.demo-form h2,.demo-scene { grid-column:1/-1; }
.demo-form .button { grid-column:1/-1; justify-self:end; }
@media(max-width:640px) { .demo-form { grid-template-columns:1fr; } }
label { display:grid; gap:6px; font-size:14px; color:var(--ink); margin:0; }
select { padding:10px 12px; border-radius:4px; border:1px solid var(--border); min-width:0; font-size:15px; background:#fff; }
</style>
