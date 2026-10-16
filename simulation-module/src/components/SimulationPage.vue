<script setup>
// AI情景演绎页（PRD §8.4，FR-SIM-01～07）。
// 路由示例：{ path: '/simulations/:id', component: SimulationPage, props: true }
// 事件：report(id) 进入复盘；history(id) 查看历史详情；restart 重新体验（回配置页）；home 返回首页；unauthenticated 需重新登录。
import { computed, ref } from 'vue'
import '../styles/simulation.css'
import CharacterFigure from './CharacterFigure.vue'
import DialogueBox from './DialogueBox.vue'
import CommentaryPanel from './CommentaryPanel.vue'
import ResultOverlay from './ResultOverlay.vue'
import { useSimulation } from '../composables/useSimulation.js'
import { MAX_ROUNDS, RISK_TIERS, SCENARIO_THEMES, SIMULATION_LIMIT_SECONDS, SimulationStatus } from '../core/constants.js'

const props = defineProps({
  id: { type: [String, Number], required: true },
  api: { type: Object, default: null },
  timing: { type: Object, default: null },
})
const emit = defineEmits(['report', 'history', 'restart', 'home', 'unauthenticated'])

const sim = useSimulation(
  computed(() => String(props.id)),
  {
    ...(props.api ? { api: props.api } : {}),
    ...(props.timing ? { timing: props.timing } : {}),
    onReport: (id) => emit('report', id),
    onUnauthenticated: () => emit('unauthenticated'),
  },
)
const { store, ui } = sim
const confirmingExit = ref(false)

const state = computed(() => store.state)
const config = computed(() => sim.configuration.value)
const theme = computed(() => SCENARIO_THEMES[config.value?.scenario.code] ?? SCENARIO_THEMES.TASK_REBATE)
const running = computed(() => state.value?.status === SimulationStatus.RUNNING)

const showOpening = computed(() => running.value && state.value.currentRoundNo === 0 && ui.opening !== 'done')

function figureState(side) {
  const speaker = side === 'scammer' ? 'SCAMMER' : 'VICTIM'
  if (running.value && state.value.currentRoundNo === 0 && ui.opening === 'pending') return 'hidden'
  if (ui.opening === 'entering') return 'entering'
  if (ui.speaker === speaker) return 'speaking'
  if (ui.speaker) return 'listening'
  return 'idle'
}

const names = computed(() => ({ SCAMMER: config.value?.scammer.name ?? '骗子', VICTIM: config.value?.victim.name ?? '受害者' }))
const remainingText = computed(() => {
  const ms = ui.remainingMs
  if (ms === null || ms === undefined) return '--:--'
  const total = Math.ceil(ms / 1000)
  return `${String(Math.floor(total / 60)).padStart(2, '0')}:${String(total % 60).padStart(2, '0')}`
})
const timePercent = computed(() => (ui.remainingMs == null ? 100 : (ui.remainingMs / (SIMULATION_LIMIT_SECONDS * 1000)) * 100))

const overlay = computed(() => {
  if (ui.mode === 'foreign') return 'foreign'
  const s = state.value
  if (!s) return null
  if (s.status === SimulationStatus.INTERRUPTED) return 'interrupted'
  if (s.status === SimulationStatus.FINISHED) return 'finished'
  if (ui.deadlineReached) return 'deadline'
  if (confirmingExit.value) return 'confirm-exit'
  return null
})

// 警示灯带：跟随已呈现的最新风险档位；未评分时为中性灰
const alert = computed(() => {
  const score = sim.latestAnalysis.value?.riskScore ?? null
  if (score === null) return { score: null, label: '风险：尚未评分', color: null }
  return { score, label: `风险 ${score} · ${RISK_TIERS[score].label}`, color: `var(--sim-risk-${score})` }
})

async function confirmExit() {
  await sim.exit('NAVIGATION')
  confirmingExit.value = false
}
</script>

<template>
  <div class="sim-root" :style="{ '--sim-accent': theme.accent, '--sim-opening-ms': (timing?.openingMs ?? 3200) + 'ms' }">
    <div v-if="ui.mode === 'loading'" class="sim-center">
      <span class="sim-spinner" aria-hidden="true"></span>
      <span>正在载入演绎…</span>
    </div>

    <div v-else-if="ui.mode === 'error'" class="sim-center">
      <p>{{ ui.errorMessage }}</p>
      <button class="sim-btn" type="button" @click="emit('home')">返回首页</button>
    </div>

    <template v-else-if="config">
      <header class="sim-topbar">
        <div class="sim-timebar" aria-hidden="true">
          <div class="sim-timebar__fill" :style="{ width: timePercent + '%' }"></div>
        </div>
        <div class="sim-topbar__title">
          <span class="sim-topbar__eyebrow">AI 情景演绎</span>
          <span class="sim-topbar__scenario">{{ config.scenario.name }}</span>
          <span class="sim-chip" :class="{ 'sim-chip--live': running && !ui.deadlineReached }">{{ sim.phaseLabel.value }}</span>
          <span v-if="ui.connection === 'reconnecting'" class="sim-chip sim-chip--warn">连接恢复中</span>
        </div>
        <div class="sim-topbar__spacer"></div>
        <div class="sim-topbar__stat">
          <span class="sim-topbar__stat-label">轮次</span>
          <span class="sim-topbar__stat-value sim-num">{{ state.currentRoundNo || 0 }} / {{ MAX_ROUNDS }}</span>
        </div>
        <div class="sim-topbar__stat">
          <span class="sim-topbar__stat-label">剩余时间</span>
          <span class="sim-topbar__stat-value sim-num" :class="{ 'is-low': ui.remainingMs !== null && ui.remainingMs < 60000 }">{{ remainingText }}</span>
        </div>
        <button
          class="sim-btn sim-btn--sound"
          type="button"
          :aria-pressed="!ui.muted"
          :title="ui.muted ? '开启声音' : '关闭声音'"
          @click="sim.toggleMute()"
        >
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <path d="M11 5 6 9H3v6h3l5 4z" />
            <template v-if="ui.muted"><path d="m17 9 5 6M22 9l-5 6" /></template>
            <template v-else><path d="M15.5 8.5a5 5 0 0 1 0 7M18.5 5.5a9 9 0 0 1 0 13" /></template>
          </svg>
          {{ ui.muted ? '声音已关' : '声音已开' }}
        </button>
        <button v-if="running" class="sim-btn sim-btn--danger" type="button" :disabled="ui.exiting" @click="confirmingExit = true">退出演绎</button>
      </header>

      <main class="sim-main">
        <div v-if="ui.notice" class="sim-notice" role="status">{{ ui.notice }}</div>

        <section class="sim-stage" aria-label="情景演绎舞台">
          <div
            class="sim-alert"
            :class="{ 'is-pending': alert.score === null, 'is-hot': alert.score !== null && alert.score >= 75 }"
            :style="alert.color ? { '--sim-alert': alert.color } : null"
            role="status"
          >
            <span class="sim-alert__label">{{ alert.label }}</span>
          </div>
          <div class="sim-stage__scenes">
            <CharacterFigure
              side="scammer"
              :profile="config.scammer"
              :state="figureState('scammer')"
              :level="ui.speaker === 'SCAMMER' ? ui.voiceLevel : null"
              :phone-buzzing="ui.speaker === 'VICTIM'"
              :figure-ref="(el) => sim.registerElement('scammer', el)"
            />
            <CharacterFigure
              side="victim"
              :profile="config.victim"
              :state="figureState('victim')"
              :level="ui.speaker === 'VICTIM' ? ui.voiceLevel : null"
              :phone-buzzing="ui.speaker === 'SCAMMER'"
              :figure-ref="(el) => sim.registerElement('victim', el)"
            />
            <div class="sim-link" :class="{ 'is-scammer': ui.speaker === 'SCAMMER', 'is-victim': ui.speaker === 'VICTIM' }" aria-hidden="true">
              <span class="sim-link__pulse"></span>
            </div>

            <div
              v-if="showOpening"
              class="sim-opening"
              :class="ui.opening === 'entering' ? 'is-entering' : 'is-pending'"
              :ref="(el) => sim.registerElement('opening', el)"
            >
              <div class="sim-opening__card">
                <div class="sim-opening__kicker">AI 情景演绎</div>
                <h2 class="sim-opening__title">{{ config.scenario.name }}</h2>
                <p class="sim-opening__desc">{{ config.scenario.description }}</p>
                <div class="sim-opening__focus">
                  <b class="is-scammer">骗子 {{ config.scammer.name }}</b> 正在联系 <b class="is-victim">受害者 {{ config.victim.name }}</b>
                  <template v-if="ui.opening === 'pending'"> · 准备中…</template>
                </div>
              </div>
            </div>
          </div>

          <DialogueBox
            :message="sim.currentMessage.value"
            :speaker-name="sim.currentMessage.value ? names[sim.currentMessage.value.speaker] : ''"
            :progress="ui.speechProgress"
            :speaking="!!ui.speaker"
            :waiting="sim.waiting.value"
            :muted="ui.muted"
            :box-ref="(el) => sim.registerElement('dialogue', el)"
          />

          <ResultOverlay
            v-if="overlay"
            :kind="overlay"
            :outcome="state?.outcome"
            :interruption-text="sim.interruptionText.value"
            :interruption-code="state?.interruption?.code || ''"
            :reading-remaining-ms="ui.readingRemainingMs"
            :busy="ui.exiting"
            @report="sim.goReport()"
            @history="emit('history', String(id))"
            @restart="emit('restart')"
            @home="emit('home')"
            @exit="confirmExit"
            @cancel="confirmingExit = false"
          />
        </section>

        <CommentaryPanel
          :analysis="sim.latestAnalysis.value"
          :series="sim.riskSeries.value"
          :transcript="sim.transcript.value"
          :names="names"
          :reading-remaining-ms="ui.readingRemainingMs"
          :next-action="state?.nextAction ?? null"
          :terminal="!running"
          :analysis-ref="(el) => sim.registerElement('analysis', el)"
        />
      </main>
    </template>
  </div>
</template>
