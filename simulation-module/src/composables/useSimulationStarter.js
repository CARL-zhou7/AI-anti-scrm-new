// 配置页“开始演绎”按钮使用：负责计时采样（API-42）与开始请求（API-14）。
// 用法：
//   const starter = useSimulationStarter()
//   async function onStartClick() {
//     const result = await starter.start({ scenarioId, scammerProfileId, victimProfileId })
//     if (result.ok) router.push({ name: 'simulation', params: { id: result.sessionId } })
//   }
// 注意：start() 必须在点击事件处理函数中直接调用（不要先 await 其他操作），点击时刻在函数第一行取得。

import { onBeforeUnmount, onMounted, ref, computed } from 'vue'
import { simulationApi } from '../api/simulationApi.js'
import { createTimingSampler, createUuid, TimingSampleUnavailableError } from '../core/timing.js'
import { markStarted } from '../core/ownership.js'

const NETWORK_RETRY_DELAYS = [1000, 2000]

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

function sameSelection(a, b) {
  return a.scenarioId === b.scenarioId && a.scammerProfileId === b.scammerProfileId && a.victimProfileId === b.victimProfileId
}

export function useSimulationStarter({ api = simulationApi, autoPrepare = true } = {}) {
  const sampler = createTimingSampler(api)
  const phase = ref('preparing') // preparing | ready | starting | error
  const errorMessage = ref('')
  // 结果未知的开始请求：再次点击时必须原样重发（同 startRequestId、同点击证据）
  let pending = null

  async function prepare() {
    phase.value = 'preparing'
    errorMessage.value = ''
    try {
      await sampler.refresh()
      phase.value = 'ready'
    } catch (err) {
      phase.value = 'error'
      errorMessage.value = err?.code === 'TIMING_PROTOCOL_NOT_READY' ? '服务暂时无法生成计时样本，暂不能开始演绎' : '计时准备失败，请稍后重试'
    }
  }

  async function send(body) {
    for (let attempt = 0; ; attempt++) {
      try {
        return await api.startSimulation(body)
      } catch (err) {
        if (!err?.network || attempt >= NETWORK_RETRY_DELAYS.length) throw err
        await sleep(NETWORK_RETRY_DELAYS[attempt])
      }
    }
  }

  /**
   * @param {{scenarioId: string, scammerProfileId: string, victimProfileId: string}} selection
   * @returns {Promise<{ok: true, sessionId: string, state: object} | {ok: false, code: string, message: string, activeSessionId?: string}>}
   */
  async function start(selection) {
    const clickMs = performance.now() // 必须是第一条语句：600 秒从点击开始计
    if (phase.value === 'starting') return { ok: false, code: 'IN_PROGRESS', message: '正在开始，请稍候' }

    let body
    if (pending && sameSelection(pending.selection, selection)) {
      body = pending.body
    } else {
      try {
        body = { startRequestId: createUuid(), ...selection, timing: sampler.buildEvidence(clickMs) }
      } catch (err) {
        if (err instanceof TimingSampleUnavailableError) {
          prepare()
          return { ok: false, code: 'TIMING_SAMPLE_EXPIRED', message: err.message }
        }
        throw err
      }
      pending = { selection: { ...selection }, body }
    }

    phase.value = 'starting'
    errorMessage.value = ''
    try {
      const { state } = await send(body)
      pending = null
      markStarted(state.sessionId)
      phase.value = 'ready'
      return { ok: true, sessionId: state.sessionId, state }
    } catch (err) {
      phase.value = 'ready'
      if (err?.network) {
        // 结果未知：保留 pending，再次点击会原样重发，由服务端幂等识别
        errorMessage.value = '网络异常，开始请求结果未知，请再次点击开始以核对'
        return { ok: false, code: 'NETWORK_ERROR', message: errorMessage.value }
      }
      pending = null
      if (err?.code === 'VALIDATION_FAILED' || err?.code === 'TIMING_PROTOCOL_NOT_READY') prepare()
      const result = { ok: false, code: err?.code || 'UNKNOWN', message: err?.message || '开始失败，请稍后重试' }
      if (err?.code === 'ACTIVE_SIMULATION_EXISTS') result.activeSessionId = err.data?.activeSessionId ?? null
      if (err?.code === 'VALIDATION_FAILED') result.message = '开始时间校准未通过，请重新点击开始'
      errorMessage.value = result.message
      return result
    }
  }

  if (autoPrepare) onMounted(prepare)
  onBeforeUnmount(() => sampler.stop())

  return {
    phase,
    errorMessage,
    canStart: computed(() => phase.value === 'ready'),
    prepare,
    start,
  }
}
