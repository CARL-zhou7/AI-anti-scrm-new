<script setup>
// 场景画像配置页（PRD §8.3，FR-CFG-01～04）：选择一个场景、一个骗子、一个受害者，确认配置后开始演绎。
// 若主工程已有配置页，可只在其“开始”按钮里使用 useSimulationStarter，本组件可不引入。
// 事件：started(sessionId) 开始成功；active(sessionId) 已有进行中的演绎；home 返回首页。
import { computed, onMounted, reactive, ref } from 'vue'
import '../styles/simulation.css'
import '../styles/setup.css'
import { simulationApi } from '../api/simulationApi.js'
import { useSimulationStarter } from '../composables/useSimulationStarter.js'
import { LEVEL_LABELS, MAX_ROUNDS, SCENARIO_THEMES, SIMULATION_LIMIT_SECONDS } from '../core/constants.js'

const props = defineProps({
  api: { type: Object, default: null },
})
const emit = defineEmits(['started', 'active', 'home'])
const api = props.api || simulationApi

const lists = reactive({ scenarios: [], scammers: [], victims: [] })
const pick = reactive({ scenarioId: null, scammerProfileId: null, victimProfileId: null })
const loading = ref(true)
const loadError = ref('')
const message = ref('')
const activeSessionId = ref(null)

const starter = useSimulationStarter({ api })

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const [scenarios, scammers, victims] = await Promise.all([api.listScenarios(), api.listProfiles('scammer'), api.listProfiles('victim')])
    Object.assign(lists, { scenarios, scammers, victims })
    // 已选画像被停用：清空并提示重选，不自动替换（FR-CFG-04）
    let removed = false
    if (pick.scammerProfileId && !scammers.some((p) => p.id === pick.scammerProfileId)) (pick.scammerProfileId = null), (removed = true)
    if (pick.victimProfileId && !victims.some((p) => p.id === pick.victimProfileId)) (pick.victimProfileId = null), (removed = true)
    if (removed) message.value = '所选人物已停用，请重新选择。'
  } catch (err) {
    loadError.value = err?.message || '场景与人物读取失败'
  } finally {
    loading.value = false
  }
}
onMounted(load)

const scenario = computed(() => lists.scenarios.find((s) => s.id === pick.scenarioId) || null)
const scammer = computed(() => lists.scammers.find((p) => p.id === pick.scammerProfileId) || null)
const victim = computed(() => lists.victims.find((p) => p.id === pick.victimProfileId) || null)
const complete = computed(() => !!(scenario.value && scammer.value && victim.value))
const missing = computed(() => [!scenario.value && '场景', !scammer.value && '骗子', !victim.value && '受害者'].filter(Boolean))

const scammerLevels = (p) => [
  ['诈骗经验', p.fraudExperience],
  ['话术能力', p.speechSkill],
  ['心理操控', p.manipulationSkill],
]
const victimLevels = (p) => [
  ['防骗意识', p.antiFraudAwareness],
  ['风险倾向', p.riskTendency],
]
const levelDots = (level) => ({ LOW: 1, MEDIUM: 2, HIGH: 3 })[level] || 0
const accentOf = (code) => SCENARIO_THEMES[code]?.accent || '#e8a33d'

async function onStart() {
  // 直接在点击处理函数中调用，点击时刻由 starter.start 第一时间记录
  const result = await starter.start({ ...pick })
  if (result.ok) return emit('started', result.sessionId)
  message.value = result.message
  activeSessionId.value = result.activeSessionId ?? null
  if (result.code === 'PROFILE_UNAVAILABLE') load()
}
</script>

<template>
  <div class="sim-root sim-setup">
    <header class="sim-setup__head">
      <div>
        <div class="sim-setup__eyebrow">AI 情景演绎</div>
        <h1 class="sim-setup__title">选一场骗局，看它怎样一步步发生</h1>
        <p class="sim-setup__lead">
          选择一个诈骗场景、一名骗子和一名受害者。两位 AI 角色会自行对话，解说员逐轮分析话术与风险；你只需要观看。
        </p>
      </div>
      <button class="sim-btn" type="button" @click="emit('home')">返回首页</button>
    </header>

    <div v-if="loading" class="sim-center">
      <span class="sim-spinner" aria-hidden="true"></span>
      <span>正在读取场景与人物…</span>
    </div>
    <div v-else-if="loadError" class="sim-center">
      <p>{{ loadError }}</p>
      <button class="sim-btn" type="button" @click="load">重新读取</button>
    </div>

    <div v-else class="sim-setup__body">
      <div class="sim-setup__choices">
        <section class="sim-setup__section" aria-labelledby="sec-scenario">
          <h2 id="sec-scenario" class="sim-setup__h2">诈骗场景</h2>
          <div class="sim-setup__grid sim-setup__grid--scenario" role="radiogroup" aria-label="诈骗场景">
            <button
              v-for="s in lists.scenarios"
              :key="s.id"
              type="button"
              role="radio"
              :aria-checked="pick.scenarioId === s.id"
              class="sim-pick sim-pick--scenario"
              :class="{ 'is-selected': pick.scenarioId === s.id }"
              :style="{ '--pick-accent': accentOf(s.code) }"
              @click="pick.scenarioId = s.id"
            >
              <span class="sim-pick__name">{{ s.name }}</span>
              <span class="sim-pick__desc">{{ s.description }}</span>
              <span class="sim-pick__tag">内容侧重：{{ s.focus }}</span>
            </button>
          </div>
        </section>

        <section v-for="side in ['scammer', 'victim']" :key="side" class="sim-setup__section" :aria-labelledby="`sec-${side}`">
          <h2 :id="`sec-${side}`" class="sim-setup__h2">
            {{ side === 'scammer' ? '骗子' : '受害者' }}
            <small v-if="side === 'victim'">场景的内容侧重不限制受害者选择</small>
          </h2>
          <div class="sim-setup__grid" role="radiogroup" :aria-label="side === 'scammer' ? '骗子' : '受害者'">
            <button
              v-for="p in side === 'scammer' ? lists.scammers : lists.victims"
              :key="p.id"
              type="button"
              role="radio"
              :aria-checked="(side === 'scammer' ? pick.scammerProfileId : pick.victimProfileId) === p.id"
              class="sim-pick sim-pick--person"
              :class="[`sim-pick--${side}`, { 'is-selected': (side === 'scammer' ? pick.scammerProfileId : pick.victimProfileId) === p.id }]"
              @click="side === 'scammer' ? (pick.scammerProfileId = p.id) : (pick.victimProfileId = p.id)"
            >
              <img class="sim-pick__avatar" :src="p.avatar.url" :alt="`${p.name}的形象`" />
              <span class="sim-pick__info">
                <span class="sim-pick__name">{{ p.name }}</span>
                <span class="sim-pick__meta">{{ p.age }}岁 · {{ p.gender }} · {{ p.identity }}</span>
                <span class="sim-pick__desc">{{ p.description }}</span>
                <span class="sim-pick__levels">
                  <span v-for="[label, level] in side === 'scammer' ? scammerLevels(p) : victimLevels(p)" :key="label" class="sim-level">
                    {{ label }}
                    <span class="sim-level__dots" :aria-label="LEVEL_LABELS[level]">
                      <i v-for="n in 3" :key="n" :class="{ 'is-on': n <= levelDots(level) }"></i>
                    </span>
                    <b>{{ LEVEL_LABELS[level] }}</b>
                  </span>
                </span>
                <span class="sim-pick__trait">{{ side === 'scammer' ? '行为特点' : '性格特点' }}：{{ side === 'scammer' ? p.behaviorTraits : p.personalityTraits }}</span>
                <span class="sim-pick__context">{{ p.contextTime }} · {{ p.contextPlace }} · {{ p.currentState }}</span>
              </span>
            </button>
          </div>
        </section>
      </div>

      <aside class="sim-setup__summary sim-card" aria-label="本次配置">
        <h2 class="sim-card__title">本次配置</h2>
        <div class="sim-summary__cast">
          <div class="sim-summary__person is-scammer">
            <img v-if="scammer" :src="scammer.avatar.url" alt="" />
            <span v-else class="sim-summary__empty">?</span>
            <span>{{ scammer ? scammer.name : '未选骗子' }}</span>
          </div>
          <div class="sim-summary__link" aria-hidden="true"><i></i></div>
          <div class="sim-summary__person is-victim">
            <img v-if="victim" :src="victim.avatar.url" alt="" />
            <span v-else class="sim-summary__empty">?</span>
            <span>{{ victim ? victim.name : '未选受害者' }}</span>
          </div>
        </div>
        <p class="sim-summary__scenario">
          <span>场景</span><b>{{ scenario ? scenario.name : '未选择' }}</b>
        </p>
        <ul class="sim-summary__rules">
          <li>点击开始即计时，最多 {{ MAX_ROUNDS }} 轮、{{ SIMULATION_LIMIT_SECONDS / 60 }} 分钟</li>
          <li>两位角色自行对话，你不参与聊天，可随时开关声音</li>
          <li>中途离开或刷新页面，本次演绎会中断且不能续演</li>
        </ul>
        <button class="sim-btn sim-btn--primary sim-summary__start" type="button" :disabled="!complete || !starter.canStart.value" @click="onStart">
          {{ starter.phase.value === 'starting' ? '正在开始…' : starter.phase.value === 'preparing' ? '正在准备…' : '开始演绎' }}
        </button>
        <p v-if="!complete" class="sim-summary__hint">还需选择：{{ missing.join('、') }}</p>
        <p v-if="message || starter.errorMessage.value" class="sim-summary__error" role="alert">
          {{ message || starter.errorMessage.value }}
          <button v-if="activeSessionId" class="sim-link-btn" type="button" @click="emit('active', activeSessionId)">查看进行中的演绎</button>
        </p>
      </aside>
    </div>
  </div>
</template>
