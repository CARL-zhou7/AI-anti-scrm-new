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
import { stagePortrait } from '../simulation/stagePortrait'
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
const stageMain = ref<HTMLElement>(), settingsButton = ref<HTMLButtonElement>()
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
const interrupted = computed(() => view.stage === 'TERMINAL' && view.terminal?.status === 'INTERRUPTED')
const showWaiting = computed(() => phase.value === 'running' && (view.stage === 'WAITING' || view.stage === 'DEADLINE')
  && view.waitingSince !== null && nowTick.value - view.waitingSince > 600)
const message = (e: unknown) => e instanceof Error ? e.message : '加载失败，请重试'
watch(settingsOpen, open => {
  if (!open && !leaving && !interrupted.value) settingsButton.value?.focus({ preventScroll: true })
}, { flush: 'post' })
/** Administrators finish into the demo report route; ordinary users keep their personal report route. */
function reportPath(id: string) {
  return auth.account?.role === 'ADMIN' ? `/admin/demo/report/${id}` : `/simulations/${id}/report`
}

function block(text: string, action: typeof blockedAction.value) { phase.value = 'blocked'; notice.value = text; blockedAction.value = action }

function preloadPortraits(...profiles: (ScammerProfilePayload | VictimProfilePayload)[]) {
  return preloadImages(profiles.flatMap(profile => [profile.avatar.url, stagePortrait(profile) ?? profile.speakingAvatar.url]))
}

async function load() {
  phase.value = 'loading'; notice.value = ''
  const { scenario, scammer, victim } = ids.value
  if (!scenario || !scammer || !victim) { leaving = true; await router.replace('/configuration'); return }
  try {
    const [scenarios, s, v] = await Promise.all([contentApi.listScenarios(), contentApi.getScammerProfile(scammer), contentApi.getVictimProfile(victim)])
    const sc = scenarios.find(x => x.id === scenario)
    if (!sc) { block('所选场景不存在，请返回重新选择', 'configuration'); return }
    preview.value = { scenario: sc, scammer: s, victim: v }
    await preloadPortraits(s, v).catch(() => { throw new Error('人物形象加载失败，请重试') })
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
  void preloadPortraits(c.scammer, c.victim)
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
  stageMain.value?.focus({ preventScroll: true })
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
    <header class="sim-top" :inert="settingsOpen">
      <div class="sim-brand">
        <span class="brand-mark" aria-hidden="true"><svg viewBox="0 0 24 24" width="21" height="21" fill="none" stroke="currentColor" stroke-width="1.6"><path d="M12 22s7-3 7-9V5l-7-3-7 3v8c0 6 7 9 7 9Z"/><path d="m9 12 2 2 4-4"/></svg></span>
        <strong>知诈<small>AI 反诈情景推演</small></strong>
        <span class="mode">{{ auth.account?.role === 'ADMIN' ? '管理员演示' : '情景演绎' }}</span>
      </div>
      <button v-if="phase === 'running' && view.stage !== 'TERMINAL'" ref="settingsButton" class="settings" data-testid="settings-button" aria-label="设置（暂停演绎）" @click="openSettings">
        暂停与设置
      </button>
    </header>

    <main ref="stageMain" class="sim-body" tabindex="-1">
      <section v-if="phase === 'loading' || phase === 'ready' || phase === 'starting' || phase === 'blocked'" class="gate" data-testid="simulation-gate">
        <template v-if="preview">
          <p class="gate-eyebrow motion-scene motion-spotlight">{{ preview.scenario.name }}</p>
          <h1 class="motion-spotlight">{{ preview.scammer.name }} <span>对</span> {{ preview.victim.name }}</h1>
        </template>
        <p v-if="phase === 'loading'" class="gate-status motion-pending" role="status" data-testid="simulation-loading">正在准备场景动画与人物形象…</p>
        <p v-if="notice" class="gate-notice" role="alert" data-testid="simulation-notice">{{ notice }}</p>
        <button v-if="phase === 'ready' || phase === 'starting'" class="start" data-testid="simulation-start" :disabled="phase === 'starting'" :aria-busy="phase === 'starting'" @click="start">
          {{ phase === 'starting' ? '正在开始…' : '开始演绎' }}
        </button>
        <p v-if="phase === 'ready'" class="gate-help">先介绍情景与人物，第一轮自动演绎。从第二轮起，点击解说框任意位置继续；点击紫色魔法球可修改待确认台词，或按方向重新生成话术和动作。</p>
        <button v-if="blockedAction === 'retry'" class="start ghost" @click="load">重新加载</button>
        <RouterLink v-else-if="blockedAction === 'configuration'" class="start ghost" to="/configuration">返回重新选择</RouterLink>
        <RouterLink v-else-if="blockedAction === 'home'" class="start ghost" to="/home">返回首页</RouterLink>
      </section>

      <template v-else-if="configuration">
        <OpeningScene v-if="view.stage === 'OPENING_SCENE'" :scenario="configuration.scenario" :frozen="view.frozen" :inert="settingsOpen" @done="sceneDone" />
        <CharacterCards v-else-if="view.stage === 'OPENING_CARDS'" :scammer="configuration.scammer" :victim="configuration.victim" :frozen="view.frozen"
          :inert="settingsOpen" @avatar-error="avatarFailed" />
        <div v-else class="play" :inert="settingsOpen || interrupted">
          <header class="play-meta">
            <div>
              <h1 class="motion-scene motion-spotlight">{{ configuration.scenario.name }}</h1>
              <p v-if="view.roundNo">第 {{ view.roundNo }} 轮<span v-if="view.stage === 'REVIEW' && intervention?.currentDraft"> · 等待确认第 {{ intervention.currentDraft.roundNo }} 轮草稿</span></p>
            </div>
            <span v-if="view.stage === 'REVIEW' && intervention?.currentDraft" class="review-status">{{ regeneratingDraft ? '生成中' : orbEditing ? '编辑中' : '待确认' }}</span>
          </header>
          <SimulationStage class="play-stage" :configuration="configuration" :view="view" @media-error="avatarFailed" />
          <p v-if="showWaiting" class="waiting motion-pending motion-spotlight" role="status" data-testid="waiting-hint">{{ view.stage === 'DEADLINE' ? '已达到时长上限，正在确认结果…' : view.bubbles.VICTIM?.done ? (view.narration?.roundNo === view.roundNo ? '正在准备下一轮对话…' : '正在生成本轮解说…') : '对方正在组织语言…' }}</p>
          <NarrationPanel :narration="view.narration" :risk="view.risk" :awaiting-continue="view.stage === 'REVIEW' && !!intervention?.currentDraft"
            :disabled="orbBusy || !!regeneratingDraft || orbEditing || view.frozen"
            :disabled-hint="regeneratingDraft ? '请等待新草稿生成' : orbEditing ? '请先保存或取消修改' : undefined"
            :error="orbError" @continue="orbAction('continue')" />
          <MagicOrbPanel v-if="intervention?.interventionMode === 'ADMIN_TEST' && view.stage !== 'TERMINAL'" :draft="intervention.currentDraft" :busy="orbBusy || !!regeneratingDraft || view.frozen" :regenerating="!!regeneratingDraft" :show-pending="auth.account?.role === 'ADMIN'" :error="orbError" :pending-records="pendingRecords" :pending-loading="pendingLoading"
            @editing-change="orbEditing = $event" @edit="c => orbAction('edit', c)" @cancel-edit="orbError = ''"
            @regenerate="g => orbAction('regenerate', g)" @open-pending="openPending" />
        </div>
      </template>

      <Transition name="stage-dialog" @before-leave="el => el.setAttribute('inert', '')">
        <SettingsOverlay v-if="settingsOpen" :muted="view.muted" :finished="status === 'FINISHED'" :busy="settingsBusy" :error="settingsError"
          @toggle-mute="toggleMute" @resume="resume" @exit="exitRun" />
      </Transition>
      <Transition name="stage-dialog">
        <TerminalNotice v-if="view.stage === 'TERMINAL' && view.terminal?.status === 'INTERRUPTED'" :code="view.terminal.interruption?.code" :message="view.terminal.interruption?.message ?? '本次演绎已中断'" :report-session-id="view.terminal.report ? view.terminal.sessionId : undefined" :winner="view.terminal.winner ?? null" :final-trust-score="view.terminal.finalTrustScore ?? null" />
      </Transition>
    </main>
  </div>
</template>

<style scoped>
.sim {
  --sim-night:#182b42; --sim-night-deep:#0b1524; --sim-ink:#15253a; --sim-paper:#f2f6fb; --sim-warn:#a44632;
  --sim-celadon:#2d607a; --sim-gold:#ff936d; --sim-mist:#e8eff6;
  --sim-display:"Microsoft YaHei","PingFang SC",sans-serif; --sim-digits:"Bahnschrift",Consolas,monospace;
  position:fixed; inset:0; display:grid; grid-template-rows:60px minmax(0,1fr); color:var(--sim-paper);
  background:var(--sim-night-deep);
}
.sim-top { display:flex; align-items:center; justify-content:space-between; padding:0 max(24px,calc((100vw - 1296px)/2)); border-bottom:1px solid #364d65; background:#111f32; }
.sim-brand { display:flex; align-items:center; gap:10px; font-size:18px; line-height:1.15; }
.sim-brand small { display:block; color:#b0c9dd; font-size:10px; font-weight:500; margin-top:2px; }
.mode { font-size:12px; background:#263e57; color:#f3f9ff; border:1px solid #4d6782; border-radius:3px; padding:5px 7px; }
.settings { padding:7px 11px; border:1px solid #526e87; border-radius:4px; background:#182b42; color:var(--sim-paper); font-size:14px; }
.settings:focus-visible,.start:focus-visible { outline:3px solid #a7d9f0; outline-offset:3px; }
.sim-body { position:relative; min-height:0; overflow:auto; }
.gate { min-height:100%; display:flex; flex-direction:column; align-items:center; justify-content:center; gap:16px; padding:24px; text-align:center; background:linear-gradient(#0b152470,#0b1524dc),url('../assets/studio-set.jpg') center/cover; }
.gate-eyebrow { margin:0; color:var(--sim-gold); letter-spacing:0.3em; font-size:13px; }
.gate h1 { margin:0; font-family:var(--sim-display); font-size:44px; font-weight:400; letter-spacing:0.08em; }
.gate h1 span { font-size:24px; color:#8e95b3; margin:0 10px; }
.gate-status { margin:8px 28px 0; font-size:13px; color:#bed3e5; }
.gate-help { margin:0; color:#b7bdd3; }
.gate-notice { margin:0; color:#ffb4a3; }
.start { min-width:220px; padding:16px 32px; border:none; border-radius:5px; background:var(--sim-gold); color:var(--sim-ink); font-size:20px;
  font-family:var(--sim-display); cursor:pointer; text-decoration:none; }
.start:disabled { opacity:0.6; cursor:wait; }
.start.ghost { background:transparent; border:1px solid #59628d; color:var(--sim-paper); font-size:16px; }
.play { width:var(--wrap); margin:0 auto; padding:16px 0 24px; display:grid; grid-template-columns:minmax(0,1fr); grid-template-rows:auto auto auto auto; align-items:start; gap:10px 14px; }
.play-meta { grid-column:1/-1; grid-row:1; display:flex; justify-content:space-between; align-items:center; gap:10px; }
.play-meta h1 { color:var(--sim-paper); font-size:clamp(26px,4vw,38px); margin:0; overflow-wrap:anywhere; }
.play-meta p { color:#bed3e5; font-size:14px; margin:4px 0 0; }
.review-status { border-radius:3px; color:#70391f; background:#ffe0c7; white-space:nowrap; padding:4px 8px; font-size:14px; font-weight:700; }
.play-stage { grid-column:1; grid-row:2; }
.play > :deep(.narration) { grid-column:1/-1; grid-row:3/5; }
.waiting { grid-column:1; grid-row:2; z-index:2; align-self:center; justify-self:center; margin:0; padding:8px 14px; border-radius:4px; background:#0c1727ed; color:#e1eef8; font-size:14px; }
@media(min-width:1001px) {
  .sim-top { padding-inline:max(36px,calc((100% - 1480px)/2)); }
  .play { width:min(1480px,calc(100% - 72px)); }
  .play:has(> .orb.expanded) { grid-template-columns:minmax(0,1fr) minmax(300px,31%); }
  .play > :deep(.orb.expanded) { grid-column:2; grid-row:2/4; align-self:stretch; min-height:0; }
}
@media(min-width:1001px) and (min-height:700px) {
  .play { height:100%; padding:12px 0; grid-template-rows:auto minmax(0,1fr) auto auto; }
  .play-stage { align-self:stretch; min-height:0; }
}
@media(min-width:1001px) and (700px <= height <= 820px) {
  .sim { grid-template-rows:48px minmax(0,1fr); }
  .play { padding-block:8px; row-gap:8px; }
  .play-meta h1 { font-size:30px; }
  .play-meta p { margin-top:0; }
}
@media(max-width:640px) {
  .sim-top { padding-inline:16px; }
  .gate h1 { font-size:30px; }
  .play { padding-top:12px; }
}
</style>
