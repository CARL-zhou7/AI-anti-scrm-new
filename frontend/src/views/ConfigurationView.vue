<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { contentApi } from '../api/content'
import { ApiError } from '../api/http'
import type { Scenario, ScammerProfilePayload, VictimProfilePayload } from '../api/types'
import ScenarioPicker from '../components/ScenarioPicker.vue'
import ProfileCard from '../components/ProfileCard.vue'
import ConfigurationSummary from '../components/ConfigurationSummary.vue'

const router = useRouter()
const scenarios = ref<Scenario[]>([]), scammers = ref<ScammerProfilePayload[]>([]), victims = ref<VictimProfilePayload[]>([])
const selectedScenarioId = ref<string | null>(null), selectedScammerId = ref<string | null>(null), selectedVictimId = ref<string | null>(null)
const busy = ref(false), checking = ref(false), error = ref(''), notice = ref(''), reselect = ref(false)
const scammerCursor = ref<string | null>(null), victimCursor = ref<string | null>(null)
const loadingMore = ref<'scammer' | 'victim' | null>(null)
const failedAvatars = ref(new Set<string>()), loadedAvatars = ref(new Set<string>())
const scenario = computed(() => scenarios.value.find(s => s.id === selectedScenarioId.value))
const scammer = computed(() => scammers.value.find(p => p.id === selectedScammerId.value))
const victim = computed(() => victims.value.find(p => p.id === selectedVictimId.value))
const ready = computed(() => !!scenario.value && !!scammer.value && !!victim.value &&
  [scammer.value.avatar.key, victim.value.avatar.key].every(key => loadedAvatars.value.has(key) && !failedAvatars.value.has(key)))
const message = (e: unknown) => e instanceof Error ? e.message : '加载失败，请重试'

function avatarError(key: string) { failedAvatars.value.add(key); loadedAvatars.value.delete(key); notice.value = '' }
function avatarLoaded(key: string) { loadedAvatars.value.add(key); failedAvatars.value.delete(key) }
function select(role: 'scenario' | 'scammer' | 'victim', id: string) {
  notice.value = ''; reselect.value = false
  if (role === 'scenario') selectedScenarioId.value = selectedScenarioId.value === id ? null : id
  else if (role === 'scammer') selectedScammerId.value = selectedScammerId.value === id ? null : id
  else selectedVictimId.value = selectedVictimId.value === id ? null : id
}

function clearSelections() {
  selectedScenarioId.value = selectedScammerId.value = selectedVictimId.value = null
  notice.value = ''; reselect.value = false
}

function isRecommended(profile: VictimProfilePayload) {
  switch (scenario.value?.code) {
    case 'TASK_REBATE': return profile.age < 35
    case 'FAKE_INVESTMENT': return profile.age >= 35 && profile.age < 60
    case 'IMPERSONATION_LOAN': return profile.age >= 60
    default: return false
  }
}

async function load() {
  busy.value = true; error.value = ''; clearSelections()
  failedAvatars.value.clear(); loadedAvatars.value.clear()
  try {
    const [s, c, v] = await Promise.all([contentApi.listScenarios(), contentApi.listScammerProfiles(), contentApi.listVictimProfiles()])
    scenarios.value = s; scammers.value = c.items; victims.value = v.items
    scammerCursor.value = c.nextCursor; victimCursor.value = v.nextCursor
  } catch (e) { error.value = message(e) }
  finally { busy.value = false }
}

async function more(role: 'scammer' | 'victim') {
  if (loadingMore.value) return
  loadingMore.value = role; notice.value = ''
  try {
    if (role === 'scammer' && scammerCursor.value) {
      const page = await contentApi.listScammerProfiles({ cursor: scammerCursor.value })
      scammers.value.push(...page.items.filter(p => !scammers.value.some(old => old.id === p.id)))
      scammerCursor.value = page.nextCursor
    } else if (role === 'victim' && victimCursor.value) {
      const page = await contentApi.listVictimProfiles({ cursor: victimCursor.value })
      victims.value.push(...page.items.filter(p => !victims.value.some(old => old.id === p.id)))
      victimCursor.value = page.nextCursor
    }
  } catch (e) { notice.value = message(e) }
  finally { loadingMore.value = null }
}

async function start() {
  if (!ready.value || checking.value) return
  checking.value = true; notice.value = ''; reselect.value = false
  const results = await Promise.allSettled([
    contentApi.getScammerProfile(selectedScammerId.value!), contentApi.getVictimProfile(selectedVictimId.value!),
  ])
  const [c, v] = results
  if (c.status === 'fulfilled') scammers.value = scammers.value.map(p => p.id === c.value.id ? c.value : p)
  else if (c.reason instanceof ApiError && c.reason.status === 404) {
    scammers.value = scammers.value.filter(p => p.id !== selectedScammerId.value)
    selectedScammerId.value = null; reselect.value = true
  }
  if (v.status === 'fulfilled') victims.value = victims.value.map(p => p.id === v.value.id ? v.value : p)
  else if (v.reason instanceof ApiError && v.reason.status === 404) {
    victims.value = victims.value.filter(p => p.id !== selectedVictimId.value)
    selectedVictimId.value = null; reselect.value = true
  }
  if (!reselect.value) {
    const failure = results.find(r => r.status === 'rejected')
    if (failure?.status === 'rejected') notice.value = message(failure.reason)
    else {
      // Entering the stage creates nothing and starts no clock; the run begins with the click on that page.
      try {
        await router.push({ path: '/simulation', query: { scenarioId: selectedScenarioId.value!, scammerProfileId: selectedScammerId.value!, victimProfileId: selectedVictimId.value! } })
      } catch (e) { notice.value = message(e) }
    }
  }
  checking.value = false
}
onMounted(load)
</script>

<template>
  <div class="home-layout configuration-layout" :aria-busy="busy || checking">
    <header class="configuration-heading"><span class="eyebrow">反诈情景学习 · 配置体验</span><h1>选择场景与人物</h1>
      <p>自由组合一个场景与双方画像，了解人物信息后确认配置。再次点击已选项即可取消选择。</p>
      <div class="configuration-toolbar">
        <button class="button button-light" data-testid="configuration-reset" :disabled="busy || checking || !(selectedScenarioId || selectedScammerId || selectedVictimId)" @click="clearSelections">重新选择</button>
      </div>
    </header>
    <div v-if="busy" class="loading-state motion-pending" data-testid="configuration-loading" role="status">正在加载场景与画像…</div>
    <section v-else-if="error" class="load-error" data-testid="configuration-error" role="alert"><h2>配置加载失败</h2><p>{{ error }}</p>
      <button class="button button-primary" data-testid="configuration-retry" @click="load">重新加载</button></section>
    <div v-else class="configuration-grid">
      <section class="configuration-section scenario-list"><h2>01 选择诈骗场景</h2><p class="field-help">内容侧重不限制人物选择，也不会改变人物属性。</p>
        <ScenarioPicker :scenarios="scenarios" :selected="selectedScenarioId" :disabled="checking" @select="select('scenario', $event)" /></section>
      <section class="configuration-section"><h2>02 选择双方画像</h2>
        <div class="profile-columns">
          <section data-testid="profile-list-scammer"><h3>骗子画像</h3>
            <p v-if="!scammers.length" data-testid="profiles-empty">暂无可用骗子画像</p>
            <ProfileCard v-for="p in scammers" :key="p.id" :profile="p" :selected="p.id === selectedScammerId" :disabled="checking"
              :failed="failedAvatars.has(p.avatar.key)" @select="select('scammer', p.id)" @avatar-error="avatarError" @avatar-loaded="avatarLoaded" />
            <button v-if="scammerCursor" class="text-button" :disabled="!!loadingMore || checking" :aria-busy="loadingMore === 'scammer'" @click="more('scammer')">{{ loadingMore === 'scammer' ? '加载中…' : '加载更多骗子画像' }}</button>
          </section>
          <section data-testid="profile-list-victim"><h3>受害者画像</h3>
            <p v-if="!victims.length" data-testid="profiles-empty">暂无可用受害者画像</p>
            <ProfileCard v-for="p in victims" :key="p.id" :profile="p" :selected="p.id === selectedVictimId" :disabled="checking"
              :recommended="isRecommended(p)" :failed="failedAvatars.has(p.avatar.key)" @select="select('victim', p.id)" @avatar-error="avatarError" @avatar-loaded="avatarLoaded" />
            <button v-if="victimCursor" class="text-button" :disabled="!!loadingMore || checking" :aria-busy="loadingMore === 'victim'" @click="more('victim')">{{ loadingMore === 'victim' ? '加载中…' : '加载更多受害者画像' }}</button>
          </section>
        </div>
      </section>
      <ConfigurationSummary class="configuration-summary" :scenario="scenario" :scammer="scammer" :victim="victim" @avatar-error="avatarError" />
      <div class="configuration-actions">
        <p v-if="reselect" class="error-message" data-testid="configuration-reselect-hint" role="alert">所选画像已不可用，请重新选择</p>
        <p v-if="notice" class="error-message" role="alert">{{ notice }}</p>
        <p id="configuration-start-help" class="field-help">{{ ready ? '进入演绎页后，资源加载完成即可点击开始演绎。' : '请选择一个场景和双方画像，并等待所选头像加载成功。' }}</p>
        <button class="button button-primary" data-testid="configuration-start" :disabled="!ready || checking" :aria-busy="checking" aria-describedby="configuration-start-help" @click="start">{{ checking ? '正在核验…' : '进入演绎' }}</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.configuration-heading { margin-bottom:20px; padding:18px 22px; background:var(--ice); border-top:3px solid var(--navy); }
.configuration-heading h1 { margin-bottom:8px; }
.configuration-heading p { color:var(--muted); max-width:64ch; margin-bottom:10px; }
.configuration-toolbar { display:flex; flex-wrap:wrap; gap:12px; }
@media(min-width:761px) {
  .configuration-heading { display:grid; grid-template-columns:minmax(0,1fr) auto; gap:0 20px; }
  .configuration-heading > :not(.configuration-toolbar) { grid-column:1; }
  .configuration-toolbar { grid-column:2; grid-row:1/4; align-self:center; }
}
.configuration-grid { display:grid; grid-template-columns:minmax(225px,265px) minmax(0,1fr); gap:16px; align-items:start; }
.configuration-grid > .configuration-section { margin:0; }
.configuration-grid > .scenario-list { grid-row:1 / span 3; }
.configuration-summary,.configuration-actions { grid-column:2; }
.configuration-section h2 { font-size:20px; }
.configuration-actions { display:flex; flex-wrap:wrap; align-items:center; justify-content:space-between; gap:8px 16px; background:var(--ice); border:1px solid var(--border); padding:12px 16px; }
.configuration-actions [role] { flex-basis:100%; }
.scenario-list { background:#172943; border-color:#172943; color:#f4f8fc; padding:16px; }
.scenario-list :deep(.field-help), .scenario-list :deep(.quiet-label) { color:#bed3e5; }
.scenario-list :deep(.scenario-options) { display:grid; gap:5px; }
.scenario-list :deep(.scenario-option) { padding:11px 10px; border:1px solid #455f7a; border-radius:4px; background:#1e3550; color:inherit; text-align:left; }
.scenario-list :deep(.scenario-option h3) { margin:4px 0; font-size:16px; }
.scenario-list :deep(.scenario-option p) { font-size:13px; margin-bottom:4px; }
.scenario-list :deep(.scenario-option .selection-label) { color:#e4f0fa; }
.scenario-list :deep([aria-pressed=true]) { border-color:#7193b0; box-shadow:inset 3px 0 var(--accent); background:#294661; }
.configuration-layout :deep(.profile-card[aria-pressed=true]) { border-color:#4f7c9d; box-shadow:inset 3px 0 #4f7c9d; background:#e6f0f7; }
.configuration-layout :deep(.profile-columns) { display:grid; grid-template-columns:1fr 1fr; gap:28px; }
.configuration-layout :deep(.profile-card) { width:100%; padding:16px; display:flex; align-items:center; gap:16px; margin-bottom:12px; border:1px solid var(--border); border-radius:4px; text-align:left; background:var(--surface); color:inherit; }
.configuration-layout :deep(.profile-card img) { width:54px; height:54px; flex-shrink:0; border-radius:4px; object-fit:cover; }
.configuration-layout :deep(.profile-card-copy) { min-width:0; overflow-wrap:anywhere; }
.configuration-layout :deep(.profile-identity) { display:block; font-size:12px; color:var(--muted); margin:5px 0; }
.configuration-layout :deep(.selection-label) { font-size:12px; color:var(--navy); }
.configuration-layout :deep(.recommendation-label) { flex-shrink:0; margin-left:auto; padding:6px 12px; border:1px solid #b9cddb; border-radius:6px; background:#e2edf3; color:var(--navy); font-size:14px; font-weight:600; }
.configuration-layout :deep(.profile-detail) { border-top:1px solid var(--border); padding-top:18px; }
.configuration-layout :deep(.detail-avatar) { width:88px; height:88px; object-fit:cover; border-radius:4px; margin-bottom:12px; }
.configuration-layout :deep(.profile-detail small) { font-size:12px; font-weight:400; color:var(--muted); }
.configuration-layout :deep(dl) { display:grid; grid-template-columns:100px 1fr; gap:10px; font-size:13px; }
.configuration-layout :deep(dt) { color:var(--muted); }
.configuration-layout :deep(dd) { margin:0; overflow-wrap:anywhere; white-space:pre-wrap; }
@media(max-width:760px) {
  .configuration-section { padding:16px; }
  .configuration-grid { grid-template-columns:minmax(0,1fr); }
  .configuration-grid > .scenario-list { grid-row:auto; }
  .configuration-summary,.configuration-actions { grid-column:auto; }
  .configuration-layout :deep(.scenario-options), .configuration-layout :deep(.profile-columns) { grid-template-columns:1fr; }
}
</style>
