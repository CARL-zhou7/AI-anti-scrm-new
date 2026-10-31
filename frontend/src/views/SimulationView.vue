<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, shallowRef, watch } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { contentApi } from '../api/content'
import { authApi } from '../api/auth'
import { adminApi } from '../api/admin'
import { useAuthStore } from '../stores/auth'
import { ApiError } from '../api/http'
import { simulationApi } from '../api/simulation'
import type { Configuration, InterventionRecord, InterventionState, ScammerDraft, ScammerProfilePayload, Scenario, SimulationState, StartRequest, VictimProfilePayload } from '../api/types'
import { preloadImages } from '../simulation/assetPreloader'
import { createAudioTrack } from '../simulation/audioTrack'
import { EventInbox } from '../simulation/eventInbox'
import { PerformanceDirector } from '../simulation/performanceDirector'
import OpeningScene from '../components/simulation/OpeningScene.vue'
import CharacterCards from '../components/simulation/CharacterCards.vue'
import SimulationStage from '../components/simulation/SimulationStage.vue'
import NarrationPanel from '../components/simulation/NarrationPanel.vue'
import SettingsOverlay from '../components/simulation/SettingsOverlay.vue'
import TerminalNotice from '../components/simulation/TerminalNotice.vue'
import MagicOrbPanel from '../components/simulation/MagicOrbPanel.vue'

const LIVE_KEY = 'anti-scam.live-session'
const route = useRoute(), router = useRouter()
const auth = useAuthStore()
type Phase = 'loading' | 'ready' | 'starting' | 'running' | 'blocked'
const phase = ref<Phase>('loading')
const notice = ref(''), blockedAction = ref<'configuration' | 'home' | 'retry' | null>(null)
const preview = shallowRef<{ scenario: Scenario; scammer: ScammerProfilePayload; victim: VictimProfilePayload } | null>(null)
const configuration = shallowRef<Configuration | null>(null)
const view = reactive(PerformanceDirector.initialView())
const status = ref<SimulationState['status'] | null>(null)
const settingsOpen = ref(false), settingsBusy = ref(false), settingsError = ref('')
const nowTick = ref(performance.now())
const inbox = new EventInbox()
const intervention = ref<InterventionState | null>(null)
const orbBusy = ref(false)
const regeneratingDraft = ref<ScammerDraft | null>(null)
watch(() => intervention.value?.currentDraft, draft => {
  const previous = regeneratingDraft.value
  if (draft && previous && (draft.draftId !== previous.draftId || draft.revisionNo > previous.revisionNo)) regeneratingDraft.value = null
})
const orbEditing = ref(false)
const orbError = ref('')
const pendingRecords = ref<InterventionRecord[]>([])
const pendingLoading = ref(false)
let director: PerformanceDirector | null = null
let closeStream: (() => void) | null = null
let sessionId = ''
let pendingStart: StartRequest | null = null
let leaving = false
let ticker: ReturnType<typeof setInterval> | null = null

const ids = computed(() => {
  const q = route.query
  const pick = (k: string) => typeof q[k] === 'string' && /^[1-9][0-9]{0,19}$/.test(q[k] as string) ? q[k] as string : null
  return { scenario: pick('scenarioId'), scammer: pick('scammerProfileId'), victim: pick('victimProfileId') }
})
const live = computed(() => phase.value === 'running' && status.value === 'RUNNING' && view.stage !== 'TERMINAL')
const showWaiting = computed(() => phase.value === 'running' && (view.stage === 'WAITING' || view.stage === 'DEADLINE')
  && view.waitingSince !== null && nowTick.value - view.waitingSince > 600)
const message = (e: unknown) => e instanceof Error ? e.message : '加载失败，请重试'
/** Administrators finish into the demo report route; ordinary users keep their personal report route. */
function reportPath(id: string) {
  return auth.account?.role === 'ADMIN' ? `/admin/demo/report/${id}` : `/simulations/${id}/report`
}

function block(text: string, action: typeof blockedAction.value) { phase.value = 'blocked'; notice.value = text; blockedAction.value = action }

async function load() {
  phase.value = 'loading'; notice.value = ''
  const { scenario, scammer, victim } = ids.value
  if (!scenario || !scammer || !victim) { leaving = true; await router.replace('/configuration'); return }
  try {
    const [scenarios, s, v] = await Promise.all([contentApi.listScenarios(), contentApi.getScammerProfile(scammer), contentApi.getVictimProfile(victim)])
    const sc = scenarios.find(x => x.id === scenario)
    if (!sc) { block('所选场景不存在，请返回重新选择', 'configuration'); return }
    preview.value = { scenario: sc, scammer: s, victim: v }
    await preloadImages([s.avatar.url, s.speakingAvatar.url, v.avatar.url, v.speakingAvatar.url]).catch(() => { throw new Error('人物形象加载失败，请重试') })
    phase.value = 'ready'
  } catch (e) {
    if (e instanceof ApiError && e.status === 404) block('所选画像已不可用，请返回重新选择', 'configuration')
    else block(message(e), 'retry')
  }
}

/** The click is the timing start: freeze the click time and request id; a network resend reuses both. */
async function start() {
  if (phase.value !== 'ready' || !preview.value) return
  phase.value = 'starting'
  // V1.4 has no business deadline, so no timing evidence is sent: the server uses the request arrival time.
  pendingStart = { startRequestId: crypto.randomUUID(), scenarioId: preview.value.scenario.id, scammerProfileId: preview.value.scammer.id,
    victimProfileId: preview.value.victim.id }
  let state: SimulationState
  try {
    state = await sendStart()
  } catch (e) {
    if (e instanceof ApiError && e.code === 'ACTIVE_SIMULATION_EXISTS') block('你已有一场进行中的演绎（可能在其他窗口或标签页），请先在原页面完成或退出。', 'home')
    else if (e instanceof ApiError && e.code === 'PROFILE_UNAVAILABLE') block('所选画像已不可用，请返回重新选择', 'configuration')
    else { notice.value = message(e); phase.value = 'ready' }
    return
  }
  sessionId = state.sessionId
  try { sessionStorage.setItem(LIVE_KEY, sessionId) } catch { /* storage unavailable: refresh is still caught server-side */ }
  configuration.value = state.configuration
  intervention.value = state.intervention ?? null
  status.value = state.status
  director = new PerformanceDirector({
    receipts: {
      opening: body => simulationApi.presentOpening(sessionId, body),
      message: (id, body) => simulationApi.presentMessage(sessionId, id, body),
      analysis: (round, body) => simulationApi.presentAnalysis(sessionId, round, body),
    },
    createAudio: (url, muted) => createAudioTrack(simulationApi.audioUrl(url), muted),
    serverNow: () => Date.now(),
    monotonicNow: () => performance.now(),
    checkState: () => simulationApi.state(sessionId),
    onReport: id => { leaving = true; clearLive(); void router.push(reportPath(id)) },
    onState: s => { status.value = s.status; inbox.observe(s.stateVersion); intervention.value = s.intervention ?? null },
  }, view)
  phase.value = 'running'
  const c = state.configuration
  // Snapshot portraits may differ from the preloaded ones (edited meanwhile): load them now; the clock is already running.
  void preloadImages([c.scammer.avatar.url, c.scammer.speakingAvatar.url, c.victim.avatar.url, c.victim.speakingAvatar.url])
    .catch(() => director?.openingFailed('AVATAR_LOAD_FAILED'))
  director.begin(state)
  openStream()
}

async function sendStart(): Promise<SimulationState> {
  try { return await simulationApi.start(pendingStart!) }
  catch (e) {
    if (e instanceof ApiError && (e.status === 0 || e.status >= 500) && e.code !== 'TIMING_PROTOCOL_NOT_READY' && e.code !== 'SERVICE_UNAVAILABLE') {
      return simulationApi.start(pendingStart!) // same startRequestId + timing: the server returns the original run
    }
    if (e instanceof ApiError && (e.code === 'TIMING_PROTOCOL_NOT_READY' || e.code === 'SERVICE_UNAVAILABLE')) {
      throw new ApiError(e.status, e.code, '服务暂不可用，请稍后再试')
    }
    throw e
  }
}

function openStream() {
  closeStream?.()
  closeStream = simulationApi.openStream(sessionId, event => {
    if (!inbox.accept(event)) return
    director?.handle(event)
    if (event.type === 'intervention.draft') {
      intervention.value = { sessionId, interventionMode: 'ADMIN_TEST', currentRoundNo: event.data.roundNo,
        phase: 'SCAMMER_REVIEW', currentDraft: event.data.draft, pendingInterventionCount: event.data.pendingInterventionCount,
        canRegenerate: true, canAdopt: true }
    }
    if (event.type === 'session.interrupted') { closeStream?.(); closeStream = null; settingsOpen.value = false; clearLive() }
    // Converged by the server while settings were open (e.g. pause credit used up): close and carry on.
    if (event.type === 'session.finished' && settingsOpen.value) { settingsOpen.value = false; director?.unfreeze(event.data) }
  }, () => {
    // EventSource retries on its own; stop only when the login is gone (never reconnect forever after 401).
    void authApi.me().catch(e => { if (e instanceof ApiError && e.status === 401) { closeStream?.(); closeStream = null; director?.stop() } })
  })
}

async function openPending() {
  if (auth.account?.role !== 'ADMIN' || pendingLoading.value) return
  pendingLoading.value = true; orbError.value = ''
  try { pendingRecords.value = (await adminApi.interventions()).filter(record => record.sessionId === sessionId) }
  catch (e) { orbError.value = message(e) }
  finally { pendingLoading.value = false }
}

async function loadIntervention() {
  try {
    const state = await simulationApi.state(sessionId)
    inbox.observe(state.stateVersion)
    director?.reconcile(state)
  } catch { /* the next state push reconciles */ }
}

async function orbAction(action: 'continue' | 'edit' | 'regenerate', payload?: string) {
  const draft = intervention.value?.currentDraft
  if (!draft || orbBusy.value || regeneratingDraft.value || view.frozen || (action === 'continue' && (orbEditing.value || view.stage !== 'REVIEW'))) return
  orbBusy.value = true; orbError.value = ''
  if (action === 'regenerate') regeneratingDraft.value = draft
  try {
    if (action === 'continue') {
      const state = await simulationApi.adoptDraft(sessionId, {
        draftId: draft.draftId, expectedRevisionNo: draft.revisionNo, continue: true })
      intervention.value = state.intervention ?? null
      status.value = state.status
      inbox.observe(state.stateVersion)
      director?.reconcile(state)
    } else if (action === 'edit') {
      intervention.value = await simulationApi.reviseDraft(sessionId, {
        draftId: draft.draftId, expectedRevisionNo: draft.revisionNo, content: payload ?? null })
    } else {
      const result = await simulationApi.regenerateDraft(sessionId, {
        draftId: draft.draftId, expectedRevisionNo: draft.revisionNo, guidance: payload ?? null })
      const current = intervention.value?.currentDraft
      // A new SSE draft can arrive before the HTTP response with an empty pending draft.
      if (!current || (current.draftId === draft.draftId && current.revisionNo === draft.revisionNo)) intervention.value = result
    }
    if (intervention.value?.currentDraft) director?.reviewDraft(intervention.value.currentDraft)
  } catch (e) {
    if (action === 'regenerate') regeneratingDraft.value = null
    if (e instanceof ApiError && (e.code === 'STALE_DRAFT' || e.code === 'DRAFT_REVISION_CONFLICT')) {
      orbError.value = '草稿已更新，已刷新为最新版本'
      await loadIntervention()
    } else if (e instanceof ApiError && e.code === 'VALIDATION_FAILED') {
      orbError.value = e.fieldErrors.map(error => error.message).join('；') || e.message
    } else {
      orbError.value = message(e)
    }
    if (action === 'edit') orbError.value = '保存失败：' + orbError.value
  } finally {
    orbBusy.value = false
  }
}

async function openSettings() {  if (!director || settingsOpen.value || view.stage === 'TERMINAL') return
  director.freeze()
  settingsOpen.value = true; settingsError.value = ''
  try {
    const r = await simulationApi.pause(sessionId)
    inbox.observe(r.state.stateVersion)
    if (r.state.status === 'INTERRUPTED') { settingsOpen.value = false; director.terminate(r.state) }
  } catch (e) {
    // Unknown result: stay frozen and verify from the server.
    try { const s = await simulationApi.state(sessionId); if (s.status === 'RUNNING' && !s.paused) await simulationApi.pause(sessionId) }
    catch { settingsError.value = message(e) }
  }
}

async function resume() {
  if (!director) return
  settingsBusy.value = true; settingsError.value = ''
  try {
    const r = await simulationApi.resume(sessionId)
    inbox.observe(r.state.stateVersion)
    settingsOpen.value = false
    if (r.state.status === 'INTERRUPTED') director.terminate(r.state)
    else director.unfreeze(r.state)
  } catch (e) { settingsError.value = message(e) }
  finally { settingsBusy.value = false }
}

async function exitRun() {
  settingsBusy.value = true; settingsError.value = ''
  try {
    const state = await simulationApi.exit(sessionId, 'NAVIGATION')
    settingsOpen.value = false
    if (state.status === 'FINISHED') { leaving = true; clearLive(); director?.stop(); await router.push(reportPath(sessionId)) }
    else { clearLive(); director?.terminate(state) }
  } catch (e) { settingsError.value = message(e) }
  finally { settingsBusy.value = false }
}

// Template-facing handlers (the director itself is a plain, non-reactive object).
function sceneDone() { director?.sceneDone() }
function avatarFailed() { director?.openingFailed('AVATAR_LOAD_FAILED') }
function toggleMute() { director?.setMuted(!view.muted) }

function clearLive() { try { sessionStorage.removeItem(LIVE_KEY) } catch { /* ignore */ } }

function onPageHide() { if (live.value && sessionId) simulationApi.exitKeepalive(sessionId, 'CLOSE') }

/** A marker left by this tab means the page was refreshed mid-run: exit it, never resume the performance. */
async function handleRefresh(): Promise<boolean> {
  let marked: string | null = null
  try { marked = sessionStorage.getItem(LIVE_KEY) } catch { /* ignore */ }
  if (!marked) return false
  clearLive()
  try {
    let state = await simulationApi.state(marked)
    if (state.status === 'RUNNING') state = await simulationApi.exit(marked, 'REFRESH')
    if (state.status === 'FINISHED') { leaving = true; await router.replace(reportPath(marked)); return true }
    configuration.value = state.configuration
    view.terminal = state; view.stage = 'TERMINAL'; phase.value = 'running'
  } catch { return false }
  return true
}

onBeforeRouteLeave(async () => {
  if (leaving || !live.value) return true
  if (!window.confirm('离开将中断本次演绎，确定离开吗？')) return false
  try { await simulationApi.exit(sessionId, 'NAVIGATION') } catch { /* server converges on its own */ }
  clearLive()
  return true
})

onMounted(async () => {
  window.addEventListener('pagehide', onPageHide)
  ticker = setInterval(() => { nowTick.value = performance.now() }, 200)
  if (await handleRefresh()) return
  await load()
})

onBeforeUnmount(() => {
  window.removeEventListener('pagehide', onPageHide)
  if (ticker) clearInterval(ticker)
  closeStream?.()
  director?.stop()
})
</script>

<template>
  <div class="sim" :aria-busy="phase === 'loading'">
    <header class="sim-top">
      <span class="brand">知诈 · 情景演绎</span>
      <button v-if="phase === 'running' && view.stage !== 'TERMINAL'" class="settings" data-testid="settings-button" aria-label="设置（暂停演绎）" @click="openSettings">
        <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 15.5a3.5 3.5 0 1 1 0-7 3.5 3.5 0 0 1 0 7Zm7.4-2.5.1-1-.1-1 2-1.6-2-3.4-2.4 1a7 7 0 0 0-1.7-1L15 3.5h-4l-.4 2.5a7 7 0 0 0-1.7 1l-2.4-1-2 3.4 2 1.6-.1 1 .1 1-2 1.6 2 3.4 2.4-1a7 7 0 0 0 1.7 1l.4 2.5h4l.4-2.5a7 7 0 0 0 1.7-1l2.4 1 2-3.4-2-1.6Z" /></svg>
      </button>
    </header>

    <main class="sim-body">
      <section v-if="phase === 'loading' || phase === 'ready' || phase === 'starting' || phase === 'blocked'" class="gate" data-testid="simulation-gate">
        <template v-if="preview">
          <p class="gate-eyebrow">{{ preview.scenario.name }}</p>
          <h1>{{ preview.scammer.name }} <span>对</span> {{ preview.victim.name }}</h1>
        </template>
        <p v-if="phase === 'loading'" class="gate-status" role="status" data-testid="simulation-loading">正在准备场景动画与人物形象…</p>
        <p v-if="notice" class="gate-notice" role="alert" data-testid="simulation-notice">{{ notice }}</p>
        <button v-if="phase === 'ready' || phase === 'starting'" class="start" data-testid="simulation-start" :disabled="phase === 'starting'" @click="start">
          {{ phase === 'starting' ? '正在开始…' : '开始演绎' }}
        </button>
        <p v-if="phase === 'ready'" class="gate-help">先介绍情景与人物，第一轮自动演绎。从第二轮起，点击解说框任意位置继续；点击紫色魔法球可修改待确认台词，或按方向重新生成话术和动作。</p>
        <button v-if="blockedAction === 'retry'" class="start ghost" @click="load">重新加载</button>
        <RouterLink v-else-if="blockedAction === 'configuration'" class="start ghost" to="/configuration">返回重新选择</RouterLink>
        <RouterLink v-else-if="blockedAction === 'home'" class="start ghost" to="/home">返回首页</RouterLink>
      </section>

      <template v-else-if="configuration">
        <OpeningScene v-if="view.stage === 'OPENING_SCENE'" :scenario="configuration.scenario" :frozen="view.frozen" @done="sceneDone" />
        <CharacterCards v-else-if="view.stage === 'OPENING_CARDS'" :scammer="configuration.scammer" :victim="configuration.victim" :frozen="view.frozen"
          @avatar-error="avatarFailed" />
        <div v-else class="play">
          <div class="play-meta"><span v-if="view.roundNo">第 {{ view.roundNo }} 轮</span><span class="scenario-name">{{ configuration.scenario.name }}</span></div>
          <SimulationStage class="play-stage" :configuration="configuration" :view="view" @media-error="avatarFailed" />
          <p v-if="showWaiting" class="waiting" role="status" data-testid="waiting-hint">{{ view.stage === 'DEADLINE' ? '已达到时长上限，正在确认结果…' : view.bubbles.VICTIM?.done ? (view.narration?.roundNo === view.roundNo ? '正在准备下一轮对话…' : '正在生成本轮解说…') : '对方正在组织语言…' }}</p>
          <NarrationPanel :narration="view.narration" :risk="view.risk" :awaiting-continue="view.stage === 'REVIEW' && !!intervention?.currentDraft"
            :disabled="orbBusy || !!regeneratingDraft || orbEditing || view.frozen" :error="orbError" @continue="orbAction('continue')" />
          <MagicOrbPanel v-if="intervention?.interventionMode === 'ADMIN_TEST' && view.stage !== 'TERMINAL'" :draft="intervention.currentDraft" :busy="orbBusy || !!regeneratingDraft || view.frozen" :regenerating="!!regeneratingDraft" :show-pending="auth.account?.role === 'ADMIN'" :error="orbError" :pending-records="pendingRecords" :pending-loading="pendingLoading"
            @editing-change="orbEditing = $event" @edit="c => orbAction('edit', c)" @cancel-edit="orbError = ''"
            @regenerate="g => orbAction('regenerate', g)" @open-pending="openPending" />
        </div>
      </template>

      <SettingsOverlay v-if="settingsOpen" :muted="view.muted" :finished="status === 'FINISHED'" :busy="settingsBusy" :error="settingsError"
        @toggle-mute="toggleMute" @resume="resume" @exit="exitRun" />
      <TerminalNotice v-if="view.stage === 'TERMINAL' && view.terminal?.status === 'INTERRUPTED'" :code="view.terminal.interruption?.code" :message="view.terminal.interruption?.message ?? '本次演绎已中断'" :report-session-id="view.terminal.report ? view.terminal.sessionId : undefined" :winner="view.terminal.winner ?? null" :final-trust-score="view.terminal.finalTrustScore ?? null" />
    </main>
  </div>
</template>

<style scoped>
.sim {
  --sim-night:#262d4a; --sim-night-deep:#161a2e; --sim-ink:#1e2235; --sim-paper:#fbf8f1; --sim-warn:#c8553d;
  --sim-celadon:#4f9c8f; --sim-gold:#e9b44c; --sim-mist:#e8ecf3;
  --sim-display:'STKaiti', 'KaiTi', 'Kaiti SC', 'BiauKai', serif;
  position:fixed; inset:0; display:grid; grid-template-rows:52px 1fr; color:var(--sim-paper);
  background:radial-gradient(120% 90% at 50% 0%, var(--sim-night) 0%, var(--sim-night-deep) 70%);
}
.sim-top { display:flex; align-items:center; justify-content:space-between; padding:0 20px; }
.brand { font-family:var(--sim-display); letter-spacing:0.2em; color:#aab0c9; }
.settings { width:40px; height:40px; display:grid; place-items:center; border:1px solid #4a5379; border-radius:50%; background:transparent; color:var(--sim-paper); cursor:pointer; }
.settings svg { width:22px; height:22px; fill:currentColor; }
.settings:focus-visible, .start:focus-visible { outline:3px solid var(--sim-gold); outline-offset:3px; }
.sim-body { position:relative; min-height:0; }
.gate { height:100%; display:flex; flex-direction:column; align-items:center; justify-content:center; gap:16px; padding:24px; text-align:center; }
.gate-eyebrow { margin:0; color:var(--sim-gold); letter-spacing:0.3em; font-size:13px; }
.gate h1 { margin:0; font-family:var(--sim-display); font-size:44px; font-weight:400; letter-spacing:0.08em; }
.gate h1 span { font-size:24px; color:#8e95b3; margin:0 10px; }
.gate-status { margin:8px 28px 0; font-size:13px; color:var(--sim-warn); }
.gate-help { margin:0; color:#b7bdd3; }
.gate-notice { margin:0; color:#ffb4a3; }
.start { min-width:220px; padding:16px 32px; border:none; border-radius:999px; background:var(--sim-gold); color:var(--sim-ink); font-size:20px;
  font-family:var(--sim-display); letter-spacing:0.3em; cursor:pointer; text-decoration:none; }
.start:disabled { opacity:0.6; cursor:wait; }
.start.ghost { background:transparent; border:1px solid #59628d; color:var(--sim-paper); font-size:16px; }
.play { height:100%; display:grid; grid-template-rows:auto 1fr auto; min-height:0; }
.play-meta { display:flex; gap:14px; justify-content:center; padding-top:4px; font-family:var(--sim-display); font-size:18px; letter-spacing:0.1em; }
.scenario-name { color:#8e95b3; }
.play-stage { min-height:0; }
.waiting { position:absolute; left:50%; top:45%; transform:translateX(-50%); margin:0; padding:8px 18px; border-radius:999px; background:rgb(22 26 46 / 0.8);
  color:#cfd3e4; font-size:14px; }
@media (max-width:640px) { .gate h1 { font-size:30px; } }
</style>
