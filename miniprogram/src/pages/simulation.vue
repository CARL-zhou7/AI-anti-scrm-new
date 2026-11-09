<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { onHide, onLoad, onShow, onUnload } from '@dcloudio/uni-app'
import { asset, get, pageUrl, runPath, write } from '../api'
import { nextPresentation, reveal, stageAction, startId } from '../presentation'
import { errorText, home, scenes } from '../ui'
import Portrait from '../components/Portrait.vue'
import Dialogue from '../components/Dialogue.vue'
import RoundAnalysis from '../components/RoundAnalysis.vue'

const state = ref(null), preview = ref(null), stage = ref('loading'), error = ref('')
const settings = ref(''), busy = ref(false), muted = ref(false), tab = ref('dialogue')
const orb = ref(''), orbText = ref(''), lastRound = ref(null), previous = ref(false)
const playing = ref(null), visibleText = ref(''), mouthOpen = ref(false), conflict = ref('')
const scorePulseRound = ref(0)
const cast = computed(() => state.value?.configuration || preview.value)
const art = computed(() => scenes[cast.value?.scenario.code])
const round = computed(() => state.value?.currentRound)
const review = computed(() => state.value?.intervention)
const draft = computed(() => review.value?.currentDraft)
const priorRound = computed(() => ['SCAMMER_REVIEW', 'SCAMMER_GENERATING'].includes(round.value?.phase) && lastRound.value ? lastRound.value : round.value)
const shownRound = computed(() => previous.value ? lastRound.value : priorRound.value)
const lines = computed(() => shownRound.value?.messages.filter(item => item.presentationStatus !== 'PENDING') || [])
const action = computed(() => stageAction(playing.value ? round.value?.messages || [] : lines.value, playing.value?.speaker))
const actors = computed(() => cast.value ? [
  { person: cast.value.scammer, role: '骗子', key: 'SCAMMER', backdrop: '/static/stage-blue.jpg' },
  { person: cast.value.victim, role: '受害者', key: 'VICTIM', backdrop: '/static/stage-warm.jpg' }
] : [])
const orbPosition = ref({ x: Math.max(0, uni.getWindowInfo().windowWidth - 34), y: 18 })
let orbDrag = null, swipeStart = null
let selection, requestId, session = '', timer, tickBusy = false, shown = false, disposed = false
let openingMs = 0, phaseStarted = 0, analysisSince = 0, analysisRound = 0, receipt = null, audio = null
let playbackVersion = 0, mouthTimer
let animatedRound = 0, scoreTimer, controlVersion = 0
const POLL_MS = 900

function clearScorePulse() {
  clearTimeout(scoreTimer)
  scorePulseRound.value = 0
}
watch([tab, previous, settings, orb], ([selectedTab, reviewing, paused, sheet]) => {
  if (selectedTab !== 'analysis' || reviewing || paused || sheet) clearScorePulse()
})

async function load() {
  const { scenarioId, scammerProfileId, victimProfileId } = selection
  if (Object.values(selection).some(id => !/^[1-9][0-9]{0,19}$/.test(id || ''))) {
    stage.value = 'failed'; error.value = '请先选择场景和双方画像'; return
  }
  stage.value = 'loading'; error.value = ''
  try {
    const [scenarios, scammer, victim] = await Promise.all([
      get('/scenarios'), get('/scammer-profiles/' + encodeURIComponent(scammerProfileId)), get('/victim-profiles/' + encodeURIComponent(victimProfileId))
    ])
    const scenario = scenarios.find(item => item.id === scenarioId)
    if (!scenario) throw new Error('所选场景已不可用，请重新选择')
    preview.value = { scenario, scammer, victim }
    stage.value = 'ready'
  } catch (cause) { error.value = errorText(cause); stage.value = 'failed' }
}

async function start() {
  await control(async () => {
    requestId ||= startId()
    try {
      const result = await write('/simulations', { ...selection, startRequestId: requestId })
      session = result.sessionId
      if (disposed) { if (result.status === 'RUNNING') await write(runPath(session, '/exit'), { reason: 'NAVIGATION' }); return }
      accept(result)
      if (result.status === 'RUNNING') {
        stage.value = result.currentRound ? 'live' : 'opening'
        phaseStarted = Date.now()
        if (result.paused) settings.value = 'pause'
      }
    } catch (cause) {
      if (cause.code === 'ACTIVE_SIMULATION_EXISTS' && cause.data?.activeSessionId) conflict.value = cause.data.activeSessionId
      if (cause.status >= 400 && cause.status < 500) requestId = undefined
      throw cause
    }
  })
}

async function endConflict() {
  const result = await uni.showModal({ title: '结束进行中的演绎？', content: '已保存的对话仍可在历史中查看。' })
  if (!result.confirm) return
  await control(async () => {
    await write(runPath(conflict.value, '/exit'), { reason: 'NAVIGATION' })
    conflict.value = ''
  })
}

function accept(next, exited = false) {
  const prior = round.value
  if (prior && next.currentRoundNo > prior.roundNo && prior.analysis) lastRound.value = prior
  if (next.currentRoundNo !== state.value?.currentRoundNo) previous.value = false
  if (next.currentRound?.phase === 'ANALYZING' || next.currentRound?.phase === 'ANALYSIS_PRESENTING') {
    if (prior?.roundNo !== next.currentRoundNo || prior?.phase === 'VICTIM_PRESENTING') tab.value = 'analysis'
  } else if (next.currentRound?.phase === 'SCAMMER_PRESENTING' && prior?.phase !== 'SCAMMER_PRESENTING') tab.value = 'dialogue'
  state.value = next
  if (next.status === 'RUNNING') {
    const current = next.currentRound
    if (shown && !next.paused && !settings.value && !orb.value && !previous.value
        && current?.phase === 'ANALYSIS_PRESENTING' && current.roundNo > animatedRound
        && !current.analysis?.presentedAt && current.analysis?.trustChange?.delta) {
      animatedRound = current.roundNo
      tab.value = 'analysis'
      scorePulseRound.value = current.roundNo
      scoreTimer = setTimeout(clearScorePulse, 850)
    }
    return
  }
  clearScorePulse()
  stopAudio()
  settings.value = orb.value = ''
  if (exited || next.status === 'INTERRUPTED' || next.outcome?.code === 'UNDETERMINED') stage.value = 'ended'
  else {
    stage.value = 'finished'
    setTimeout(() => { if (!disposed) openReport() }, 1000)
  }
}

function stopAudio() {
  playbackVersion++
  closeMouth()
  if (audio) { audio.destroy(); audio = null }
  playing.value = null
}

function closeMouth() {
  clearInterval(mouthTimer)
  mouthOpen.value = false
}

function finishLine(next, failed) {
  receipt = { path: next.path, body: failed ? { status: 'FAILED', errorCode: failed } : { status: 'COMPLETED' } }
  stopAudio()
}

async function play(next) {
  const line = next.message
  const version = ++playbackVersion
  playing.value = line
  visibleText.value = line.segments?.length ? '' : line.content
  try {
    const path = await asset(line.audioUrl)
    if (disposed || playbackVersion !== version) return
    if (!path) { finishLine(next, 'AUDIO_LOAD_FAILED'); return }
    audio = uni.createInnerAudioContext()
    audio.volume = muted.value ? 0 : 1
    audio.onPlay(() => {
      if (playbackVersion !== version) return
      closeMouth()
      mouthOpen.value = true
      mouthTimer = setInterval(() => { mouthOpen.value = !mouthOpen.value }, 125)
    })
    audio.onPause(() => { if (playbackVersion === version) closeMouth() })
    audio.onTimeUpdate(() => {
      if (playbackVersion !== version) return
      visibleText.value = reveal(line.content, line.segments, audio.currentTime * 1000)
    })
    audio.onEnded(() => { if (playbackVersion === version) finishLine(next) })
    audio.onError(() => { if (playbackVersion === version) finishLine(next, 'AUDIO_PLAYBACK_FAILED') })
    audio.src = path
    if (shown && !settings.value) audio.play()
  } catch {
    if (playbackVersion === version && !disposed) finishLine(next, 'AUDIO_LOAD_FAILED')
  }
}

async function sendReceipt() {
  const current = receipt
  const result = await write(runPath(session, current.path), current.body)
  receipt = null
  if (!disposed) accept(result.state)
}

async function tick() {
  if (!shown || disposed || tickBusy || busy.value || !session || state.value?.status !== 'RUNNING' || error.value) return
  tickBusy = true
  try {
    const version = controlVersion
    const snapshot = await get(runPath(session))
    if (disposed || !shown || busy.value || version !== controlVersion) return
    accept(snapshot)
    if (snapshot.status !== 'RUNNING' || settings.value || snapshot.paused) return
    if (receipt) { await sendReceipt(); return }
    if (stage.value === 'opening' || stage.value === 'characters') {
      if (Date.now() - phaseStarted < (stage.value === 'opening' ? 6500 : 4000) - openingMs) return
      if (stage.value === 'opening') { stage.value = 'characters'; phaseStarted = Date.now(); openingMs = 0 }
      else {
        stage.value = 'live'
        receipt = { path: '/opening/presented', body: { status: 'COMPLETED' } }
        await sendReceipt()
      }
      return
    }
    const next = nextPresentation(snapshot.currentRound)
    if (next?.kind === 'message' && !playing.value) void play(next)
    if (next?.kind === 'analysis') {
      if (analysisRound !== snapshot.currentRoundNo) {
        analysisRound = snapshot.currentRoundNo
        analysisSince = Date.now()
        tab.value = 'analysis'
      } else if (Date.now() - analysisSince >= 1000) {
        receipt = { path: next.path, body: { status: 'COMPLETED' } }
        await sendReceipt()
      }
    }
  } catch (cause) { if (!disposed) error.value = errorText(cause) }
  finally { tickBusy = false }
}

async function control(action) {
  if (busy.value) return
  controlVersion++
  busy.value = true; error.value = ''
  try { await action() }
  catch (cause) { if (!disposed) error.value = errorText(cause) }
  finally { busy.value = false }
}

function openSettings() {
  if (settings.value || state.value?.status !== 'RUNNING') return
  audio?.pause()
  if (stage.value === 'opening' || stage.value === 'characters') openingMs += Date.now() - phaseStarted
  settings.value = 'pause'
  void control(async () => { const result = await write(runPath(session, '/pause')); if (!disposed) accept(result.state) })
}
function resume() {
  return control(async () => {
    const result = await write(runPath(session, '/resume'))
    if (disposed) return
    settings.value = ''
    phaseStarted = Date.now()
    accept(result.state)
    if (shown) audio?.play()
  })
}
function exit() {
  return control(async () => {
    const result = await write(runPath(session, '/exit'), { reason: 'NAVIGATION' })
    if (disposed) return
    accept(result, true)
  })
}
function mute(event) {
  muted.value = !event.detail.value
  if (audio) audio.volume = muted.value ? 0 : 1
}
function openReport() {
  if (disposed) return
  uni.redirectTo({ url: pageUrl('report', session) })
}
function backToCasting() { uni.navigateBack() }
function closeOrb() { orb.value = ''; orbText.value = ''; error.value = '' }
function edit() { orbText.value = draft.value.draftContent; orb.value = 'edit' }
function regenerate() { orbText.value = ''; orb.value = 'regenerate' }
function submitDraft(mode) {
  if (!draft.value || settings.value) return
  const data = { draftId: draft.value.draftId, expectedRevisionNo: draft.value.revisionNo }
  const content = orbText.value.trim()
  if (mode === 'save' && !content) { error.value = '请输入台词'; return }
  return control(async () => {
    try {
      if (mode === 'save') {
        if (content !== draft.value.draftContent) {
          const intervention = await write(runPath(session, '/intervention'), { ...data, content }, 'PUT')
          if (disposed) return
          state.value = { ...state.value, intervention }
        }
        orbText.value = ''
        orb.value = 'preview'
        uni.showToast({ title: '台词已保存', icon: 'success' })
        return
      }
      if (mode === 'regenerate') {
        await write(runPath(session, '/intervention/regenerate'), { ...data, guidance: content || null })
        accept(await get(runPath(session)))
      } else {
        accept(await write(runPath(session, '/intervention/adopt'), { ...data, continue: true }))
      }
      closeOrb()
    } catch (cause) {
      if (['STALE_DRAFT', 'DRAFT_REVISION_CONFLICT'].includes(cause.code)) {
        accept(await get(runPath(session)))
        closeOrb()
      }
      throw cause
    }
  })
}

function beginDrag(event) {
  const touch = event.touches[0]
  if (touch) orbDrag = { x: touch.clientX, y: touch.clientY, initial: { ...orbPosition.value }, moved: false }
}
function moveDrag(event) {
  const touch = event.touches[0]
  if (!touch || !orbDrag) return
  const dx = touch.clientX - orbDrag.x, dy = touch.clientY - orbDrag.y
  if (!orbDrag.moved && Math.abs(dx) + Math.abs(dy) < 8) return
  orbDrag.moved = true
  const { windowWidth, windowHeight } = uni.getWindowInfo()
  orbPosition.value = { x: Math.min(windowWidth - 34, Math.max(-12, orbDrag.initial.x + dx)),
    y: Math.min(windowHeight - 150, Math.max(-52, orbDrag.initial.y - dy)) }
}
function endDrag() {
  if (!orbDrag) return
  if (orbDrag.moved) {
    const width = uni.getWindowInfo().windowWidth
    orbPosition.value.x = orbPosition.value.x < width / 2 ? -12 : width - 34
  } else if (draft.value) { orb.value = 'preview'; error.value = '' }
  orbDrag = null
}
function startSwipe(event) { swipeStart = event.touches[0]?.clientX ?? null }
function endSwipe(event) {
  const end = event.changedTouches[0]?.clientX
  if (end == null || swipeStart == null || Math.abs(end - swipeStart) < 55) return
  if (orb.value === 'preview' || orb.value === 'previous') orb.value = end < swipeStart ? 'previous' : 'preview'
  else if (lastRound.value) { previous.value = end > swipeStart; tab.value = 'dialogue' }
  swipeStart = null
}

onLoad(query => {
  selection = { scenarioId: query.scenarioId, scammerProfileId: query.scammerProfileId, victimProfileId: query.victimProfileId }
  void load()
})
onShow(() => {
  shown = true
  if (!settings.value) audio?.play()
  timer = setInterval(tick, POLL_MS)
  void tick()
})
onHide(() => { shown = false; clearInterval(timer); clearScorePulse(); audio?.pause() })
function dispose() {
  if (disposed) return
  disposed = true
  clearInterval(timer)
  clearScorePulse()
  stopAudio()
  if (session && state.value?.status === 'RUNNING') void write(runPath(session, '/exit'), { reason: 'NAVIGATION' }).catch(() => {})
}
onUnload(dispose)
onBeforeUnmount(dispose)
</script>

<template>
  <view class="stage">
    <view v-if="stage === 'loading' || stage === 'failed'" class="state-page">
      <image class="offline-cover" src="/static/stage-offline.jpg" mode="widthFix" />
      <view class="surface play-panel overlap state-panel">
        <image v-if="stage === 'failed'" class="cloud-symbol" src="/static/stage-cloud.jpg" mode="widthFix" />
        <text v-else class="spinner" aria-hidden="true" />
        <text class="panel-title">{{ stage === 'failed' ? '暂时无法加载演绎' : '正在准备场景与画像' }}</text>
        <text v-if="error" class="muted block">{{ error }}</text>
        <view v-if="stage === 'failed'" class="actions"><button class="accent" @tap="load">重新加载</button><button class="ghost" @tap="backToCasting">返回选择场景</button></view>
      </view>
    </view>
    <template v-else-if="cast">
      <view v-if="stage === 'ready' || stage === 'opening'" class="hero premiere" :class="{ opening: stage === 'opening' }">
        <image v-if="stage === 'opening' && art" class="hero-art" :src="art.opening" mode="aspectFill" />
        <template v-if="stage === 'ready'">
          <view class="hero-art premiere-cast">
            <view v-for="actor in actors" :key="actor.key" class="premiere-portrait" :class="actor.key.toLowerCase()">
              <Portrait class="hero-art" :person="actor.person" />
            </view>
          </view>
          <view class="premiere-heading"><text class="bold block">开演前</text><text>模拟演绎</text></view>
        </template>
        <button v-if="stage === 'opening' && !settings" aria-label="暂停设置" class="gear opening-gear" @tap="openSettings">⚙</button>
        <view class="hero-copy">
          <text v-if="stage === 'opening'" class="eyebrow">{{ cast.scenario.name }}</text>
          <text class="title">{{ stage === 'ready' ? cast.scenario.name : art?.title || cast.scenario.name }}</text>
          <text v-if="stage === 'ready'" class="premiere-note">{{ cast.scammer.name }} / {{ cast.victim.name }}</text>
        </view>
      </view>
      <template v-else>
        <view class="stage-title">
          <text v-if="stage === 'characters'">人物亮相</text>
          <text v-else>第 <text class="round-number">{{ priorRound?.roundNo || round?.roundNo || 1 }}</text> 轮</text>
          <button v-if="state?.status === 'RUNNING'" aria-label="暂停设置" class="gear" :disabled="busy" @tap="openSettings">⚙</button>
        </view>
        <view class="cast" :class="{ intro: stage === 'characters' }">
          <view v-for="actor in actors" :key="actor.key" class="actor" :class="{ speaking: playing?.speaker === actor.key }">
            <Portrait class="actor-photo" :person="actor.person" :backdrop="actor.backdrop" :fill="stage !== 'characters'" animated
              :speaking="playing?.speaker === actor.key && mouthOpen" />
            <view class="actor-caption"><image v-if="playing?.speaker === actor.key" class="voice-wave" src="/static/voice-wave.svg" mode="aspectFit" aria-hidden="true" /><text class="bold">{{ actor.person.name }}</text>
              <template v-if="stage === 'characters'"><text class="bold small"> / {{ actor.role }}</text><text class="block small intro-identity">{{ actor.person.identity }}</text></template>
              <text v-else-if="playing?.speaker === actor.key" class="voice-status">正在说话</text>
            </view>
          </view>
        </view>
        <view v-if="stage !== 'characters'" class="action-text">
          <text v-if="action"><text class="action-name">{{ action.speaker === 'SCAMMER' ? cast.scammer.name : cast.victim.name }}：</text>{{ action.actionText }}</text>
        </view>
      </template>

      <view v-if="stage === 'ready'" class="stage-actions stack">
        <text v-if="error" class="error">{{ error }}</text>
        <button v-if="conflict" class="ghost dark-ghost" :disabled="busy" @tap="endConflict">结束已有演绎</button>
        <button class="accent" :loading="busy" :disabled="busy || !!conflict" @tap="start">开始演绎</button>
      </view>
      <view v-else-if="stage === 'opening' && !settings && !error" class="opening-progress">
        <view class="progress-track"><view v-for="segment in 5" :key="segment" class="progress-segment" :style="{ animationDelay: (segment - 1) * 1.3 + 's' }" /></view>
      </view>
      <view v-else-if="stage === 'characters' && !settings && !error" class="stage-actions center">第一轮即将开始</view>
      <view v-else class="surface play-panel" :class="{ 'settings-panel': settings }" @touchstart="startSwipe" @touchend="endSwipe">
        <template v-if="settings">
          <text class="panel-title">{{ settings === 'exit' ? '确定退出演绎？' : '演绎已暂停' }}</text>
          <text v-if="error" class="error">{{ error }}</text>
          <view v-if="settings === 'exit'" class="actions">
            <text class="muted block center">已保存的轮次可在历史中查看。</text>
            <view class="row settings-row"><button class="ghost grow" :disabled="busy" @tap="settings = 'pause'">返回设置</button><button class="accent grow" :disabled="busy" @tap="exit">确定退出</button></view>
          </view>
          <template v-else>
            <view class="row between voice-setting"><text class="bold">语音播报</text><switch color="#ed792d" :checked="!muted" @change="mute" /></view>
            <view class="actions"><button :disabled="busy" @tap="resume">继续演绎</button><button class="ghost" :disabled="busy" @tap="settings = 'exit'">退出演绎</button></view>
          </template>
        </template>
        <view v-else-if="stage === 'ended' || stage === 'finished'" class="ending center">
          <text class="panel-title">{{ stage === 'finished' ? '本场演绎已结束' : '本次演绎提前终止' }}</text>
          <view class="ending-rule" />
          <text class="muted block">{{ state.interruption?.message || state.outcome?.displayText || '正在进入复盘' }}</text>
          <view v-if="stage === 'finished'" class="ending-dots"><text>●</text><text>●</text><text>●</text></view>
          <view v-else class="actions"><button @tap="openReport">查看复盘报告</button><button class="ghost" @tap="home">返回首页</button></view>
        </view>
        <template v-else-if="orb">
          <view class="sheet-handle" />
          <text class="panel-title">{{ orb === 'edit' ? '编辑下一轮台词' : orb === 'regenerate' ? '重新生成台词和动作' : '魔法球' }}</text>
          <text v-if="error" class="error">{{ error }}</text>
          <template v-if="orb === 'edit' || orb === 'regenerate'">
            <text v-if="orb === 'regenerate'" class="muted block">生成方向（选填）</text>
            <textarea v-model="orbText" :disabled="busy" :maxlength="orb === 'regenerate' ? 200 : 4000"
              :placeholder="orb === 'regenerate' ? '例如：先回应对方的疑问，再发送一条说明。语气礼貌，避免催促。' : '输入下一轮台词'" />
            <view v-if="orb === 'edit'" class="actions">
              <button class="link" :disabled="busy" @tap="orb = 'preview'; error = ''">取消修改</button>
              <button :loading="busy" :disabled="busy || !draft || !review.canAdopt" @tap="submitDraft('save')">保存台词</button>
            </view>
            <view v-else class="actions">
              <button :loading="busy" :disabled="busy" @tap="submitDraft('regenerate')">重新生成</button>
              <button class="ghost" :disabled="busy" @tap="orb = 'preview'; error = ''">取消</button>
            </view>
          </template>
          <template v-else>
            <view class="tabs"><text class="tab" :class="{ active: orb === 'preview' }" @tap="orb = 'preview'">下一轮台词</text><text class="tab" :class="{ active: orb === 'previous' }" @tap="orb = 'previous'">上一轮对白</text></view>
            <template v-if="orb === 'preview' && draft">
              <text class="draft-label">第 {{ draft.roundNo }} 轮 · {{ cast.scammer.name }}</text>
              <view class="tile draft-line"><text>{{ draft.draftContent }}</text><text v-if="draft.draftAction" class="muted block">{{ draft.draftAction }}</text></view>
              <view class="row settings-row"><button class="ghost grow" :disabled="busy || !review.canAdopt" @tap="edit">✎ 编辑台词</button><button class="ghost grow" :disabled="busy || !review.canRegenerate" @tap="regenerate">⟳ 重新生成</button></view>
              <button :loading="busy" :disabled="busy || !review.canAdopt" @tap="submitDraft('continue')">继续演绎</button>
            </template>
            <text v-else-if="orb === 'preview'" class="muted block">正在生成下一轮台词…</text>
            <Dialogue v-else-if="priorRound?.messages.length" :messages="priorRound.messages" :cast="cast" />
            <text v-else class="muted">暂无上一轮对白</text>
            <button class="link" :disabled="busy" @tap="closeOrb">收起魔法球</button>
          </template>
        </template>
        <template v-else>
          <view v-if="error"><text class="error">{{ error }}</text><button class="ghost" @tap="error = ''; tick()">重新加载</button></view>
          <view v-if="stage === 'live'" class="tabs"><text class="tab" :class="{ active: tab === 'dialogue' }" @tap="tab = 'dialogue'">{{ previous ? '上一轮对白' : '本轮对白' }}</text><text class="tab" :class="{ active: tab === 'analysis' }" @tap="tab = 'analysis'">{{ previous ? '上一轮解说' : '本轮解说' }}</text></view>
          <template v-if="stage === 'live' && tab === 'dialogue'">
            <Dialogue v-if="lines.length" class="live-text" live :messages="lines" :cast="cast" :playing="playing?.id" :revealed="visibleText" />
            <text v-else class="muted block waiting">正在准备对白…</text>
            <button v-if="lastRound && priorRound?.roundNo !== lastRound.roundNo" class="link recall" @tap="previous = !previous">{{ previous ? '返回本轮' : '右滑回看 →' }}</button>
          </template>
          <template v-if="stage === 'live' && tab === 'analysis'">
            <RoundAnalysis v-if="shownRound?.analysis" class="live-text" :analysis="shownRound.analysis" :show-delta="scorePulseRound === shownRound.roundNo" />
            <view v-else class="waiting center">
              <text class="spinner" /><text class="bold block">正在生成本轮解说…</text>
              <view class="skeleton"><view /><view /><view /></view>
            </view>
          </template>
          <view v-if="draft && stage === 'live'" class="actions continue-action"><button :disabled="busy || !review.canAdopt" @tap="submitDraft('continue')">继续演绎</button></view>
          <text v-else-if="round?.analysis?.presentedAt && stage === 'live' && !error" class="muted small block center next-hint">正在准备下一轮…</text>
        </template>
      </view>
      <view v-if="draft && stage === 'live' && !settings && !orb" class="orb" aria-label="打开魔法球" :style="{ left: orbPosition.x + 'px', bottom: orbPosition.y + 20 + 'px' }"
        @touchstart.stop="beginDrag" @touchmove.stop.prevent="moveDrag" @touchend.stop="endDrag" @touchcancel="orbDrag = null" />
    </template>
  </view>
</template>

<style scoped>
.stage { display: flex; flex-direction: column; min-height: 100vh; background: #0c2233; color: #eef6fb; }
.premiere { min-height: 71vh; }
.premiere.opening { min-height: 77vh; }
.premiere-cast { background: #102b3d; }
.premiere-portrait { position: absolute; left: 0; right: 0; height: 62%; }
.premiere-portrait.scammer { top: 0; }
.premiere-portrait.victim { bottom: 0; clip-path: polygon(0 41%, 100% 0, 100% 100%, 0 100%); }
.premiere-portrait.scammer .hero-art { left: 12%; width: 88%; }
.premiere-portrait.victim .hero-art { width: 88%; }
.premiere-heading { position: absolute; top: 26rpx; left: 28rpx; padding: 0 15rpx; border-left: 6rpx solid #ee854f; font-size: 27rpx; line-height: 1.25; }
.premiere-heading .bold { font-size: 53rpx; margin-bottom: 7rpx; }
.premiere .hero-copy { padding-bottom: 20rpx; }
.premiere .title { margin: 12rpx 0; font-family: 'Songti SC', SimSun, serif; font-size: 67rpx; }
.premiere-note { display: block; padding-top: 12rpx; }
.premiere:not(.opening) .premiere-note { border-top: 6rpx solid #e67740; }
.stage-actions { padding: 24rpx 28rpx calc(24rpx + env(safe-area-inset-bottom)); }
.stage-actions .accent { background: linear-gradient(110deg, #f28b56, #e36f3e); font-size: 41rpx; font-weight: 800; }
.dark-ghost { color: white; margin-bottom: 14rpx; }
.stage-title { position: relative; min-height: 54rpx; padding: 3rpx 24rpx 12rpx; font-weight: 700; text-align: center; }
.stage-title .gear { position: absolute; right: 23rpx; top: -12rpx; }
.round-number { color: #ff9356; font-size: 34rpx; }
.gear { background: transparent; padding: 0 9rpx; font-size: 43rpx; border-radius: 50%; }
.opening-gear { position: absolute; z-index: 1; top: 16rpx; right: 22rpx; }
.cast { display: flex; gap: 12rpx; padding: 0 18rpx 10rpx; }
.actor { flex: 1; position: relative; min-width: 0; overflow: hidden; border-radius: 16rpx; border: 3rpx solid transparent; }
.actor.speaking { border-color: #ed9054; box-shadow: 0 0 0 1px #f7b171; }
.actor-photo { height: min(58vw, 33vh); }
.actor-caption { position: absolute; bottom: 0; left: 0; right: 0; padding: 43rpx 12rpx 10rpx; text-align: center; background: linear-gradient(transparent, #071925e8); }
.voice-wave { width: 28rpx; height: 29rpx; margin-right: 8rpx; vertical-align: -4rpx; }
.voice-status { margin-left: 8rpx; font-size: 21rpx; color: #ffe7d4; }
.intro { flex-direction: column; gap: 3rpx; padding: 0; }
.intro .actor { border: 0; border-radius: 0; }
.intro .actor-caption { padding: 55rpx 25rpx 15rpx; text-align: left; }
.intro .actor-caption .bold { font-size: 42rpx; }
.intro .actor-caption .small { font-size: 26rpx; }
.intro-identity { display: block; margin-top: 10rpx; }
.intro-identity::before { display: block; content: ''; width: 62rpx; height: 4rpx; background: #e37942; margin-bottom: 8rpx; }
.intro .actor-photo { height: 32vh; }
.action-text { min-height: 62rpx; padding: 4rpx 25rpx 14rpx; font-size: 27rpx; overflow-wrap: anywhere; }
.action-name { color: #ffc396; font-weight: 750; }
.play-panel { flex: 1; display: flex; flex-direction: column; padding: 12rpx 24rpx calc(34rpx + env(safe-area-inset-bottom)); }
.live-text { flex: 1; display: flex; flex-direction: column; }
.play-panel .tab { padding: 8rpx 4rpx 14rpx; font-size: 32rpx; }
.continue-action { margin-top: auto; padding-top: 20rpx; }
.recall { align-self: flex-end; margin-top: auto; color: #274458; }
.settings-panel .ghost { border-color: #102638; }
.play-panel .heading { margin-top: 4rpx; }
.panel-title { display: block; margin: 18rpx 0 25rpx; font-size: 47rpx; line-height: 1.25; font-weight: 800; text-align: center; }
.voice-setting { padding: 10rpx 3rpx; border-bottom: 1px solid #cedfe9; }
.sheet-handle { height: 8rpx; width: 86rpx; margin: 0 auto 10rpx; background: #92adbf; border-radius: 20rpx; }
.draft-label { display: inline-block; background: #f7e3cf; color: #4c3024; border-radius: 10rpx; padding: 4rpx 16rpx; font-weight: 750; }
.draft-line { margin-top: 9rpx; }
.waiting { padding: 32rpx 6rpx; }
.waiting .bold { margin: 12rpx 0 20rpx; font-size: 34rpx; }
.skeleton { width: 82%; margin: 0 auto; }
.skeleton view { height: 22rpx; margin: 12rpx 0; background: #d4e3ed; border-radius: 18rpx; }
.skeleton view:nth-child(2) { width: 92%; }
.skeleton view:nth-child(3) { width: 72%; }
.ending { padding-top: 23rpx; }
.ending .panel-title { color: #be5326; }
.ending-rule { height: 2rpx; background: #e3b497; margin: 12rpx 15rpx 34rpx; }
.ending-dots { color: #a9c2d2; margin: 29rpx auto; letter-spacing: 20rpx; }
.ending-dots text:nth-child(2) { color: #e47438; }
.next-hint { margin-top: 14rpx; }
.state-page { flex: 1; display: flex; flex-direction: column; }
.offline-cover { display: block; width: 100%; }
.state-panel { min-height: 54vh; text-align: center; }
.state-panel .spinner, .cloud-symbol { display: block; margin: 32rpx auto 0; }
.cloud-symbol { width: 185rpx; }
.settings-row { margin: 16rpx 0; }
.opening-progress { display: flex; gap: 20rpx; align-items: center; padding: 26rpx; }
.progress-track { display: flex; gap: 10rpx; flex: 1; }
.progress-segment { position: relative; flex: 1; height: 8rpx; border-radius: 12rpx; background: #546674; overflow: hidden; }
.progress-segment::before { content: ''; position: absolute; inset: 0; background: #f1874d; transform: scaleX(0); transform-origin: left; animation: fill 1.3s linear forwards; animation-delay: inherit; }
.orb { position: fixed; z-index: 5; width: 50px; height: 50px; border: 3px solid #ffd294; border-radius: 50%; background: radial-gradient(circle at 32% 30%, #ffcc60, #f87520 70%); box-shadow: 0 0 16px #e58b49; }
.spinner { display: inline-block; width: 40rpx; height: 40rpx; border: 6rpx solid #bdcedb; border-top-color: #bd552b; border-radius: 50%; animation: spin .9s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
@keyframes fill { to { transform: scaleX(1); } }
</style>
