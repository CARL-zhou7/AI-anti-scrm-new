// 角色语音播放（PC-P04、接口文档§4.1）：
// - 一次只播放一条消息，双方语音不重叠；完成以 ended 事件为准，“下载成功”或静音按钮状态都不算完成。
// - 静音是正常操作：仍按同一条媒体时间轴静音播放，文字进度与说话动画照常推进。
// - 浏览器可用 Web Audio 时，通过 AnalyserNode 取得音量驱动说话动画，静音用 GainNode 实现，
//   这样静音时动画依然跟随语音节奏；AudioContext 不可用时退回 <audio>.muted 与固定节奏动画。
// - 加载失败 → AUDIO_LOAD_FAILED；播放中出错或长时间卡住 → AUDIO_PLAYBACK_FAILED。

import { PresentationError, PRESENTATION_TIMING } from './constants.js'
import { PresentationFailure } from './presenter.js'

const LEVEL_SAMPLE_MS = 50

function abortError() {
  return new DOMException('presentation aborted', 'AbortError')
}

export function createVoicePlayer({ timing = PRESENTATION_TIMING } = {}) {
  let muted = false
  let audioContext = null
  let current = null // { audio, gain }
  const listeners = new Set()

  function emitMuteChange(reason) {
    for (const fn of listeners) fn(muted, reason)
  }

  async function ensureContext() {
    const Ctor = globalThis.AudioContext || globalThis.webkitAudioContext
    if (!Ctor) return null
    try {
      if (!audioContext) audioContext = new Ctor()
      if (audioContext.state === 'suspended') await Promise.race([audioContext.resume(), new Promise((r) => setTimeout(r, 300))])
      return audioContext.state === 'running' ? audioContext : null
    } catch {
      return null
    }
  }

  function applyMute() {
    if (!current) return
    if (current.gain) current.gain.gain.value = muted ? 0 : 1
    else current.audio.muted = muted
  }

  function setMuted(value, reason = 'user') {
    muted = !!value
    applyMute()
    emitMuteChange(reason)
  }

  /**
   * 播放一条语音并在自然结束时 resolve。
   * @param {string} url
   * @param {{signal?: AbortSignal, onProgress?: (fraction: number) => void, onLevel?: (level: number|null) => void}} opts
   */
  async function play(url, { signal, onProgress, onLevel } = {}) {
    if (!url) throw new PresentationFailure(PresentationError.AUDIO_LOAD_FAILED, '缺少语音地址')
    if (signal?.aborted) throw abortError()

    const audio = new Audio()
    audio.preload = 'auto'
    audio.src = url

    const ctx = await ensureContext()
    let source = null
    let analyser = null
    let gain = null
    if (ctx) {
      try {
        source = ctx.createMediaElementSource(audio)
        analyser = ctx.createAnalyser()
        analyser.fftSize = 512
        gain = ctx.createGain()
        source.connect(analyser)
        analyser.connect(gain)
        gain.connect(ctx.destination)
      } catch {
        source = analyser = gain = null
      }
    }
    current = { audio, gain }
    applyMute()

    return new Promise((resolve, reject) => {
      let loaded = false
      let settled = false
      let lastTime = -1
      let lastProgressAt = Date.now()
      const timers = []
      const levelBuffer = analyser ? new Uint8Array(analyser.fftSize) : null

      function cleanup() {
        timers.forEach((t) => clearInterval(t))
        audio.onloadedmetadata = audio.onended = audio.onerror = audio.ontimeupdate = null
        signal?.removeEventListener('abort', onAbort)
        try {
          audio.pause()
          source?.disconnect()
          analyser?.disconnect()
          gain?.disconnect()
        } catch {
          // 忽略断开错误
        }
        audio.removeAttribute('src')
        audio.load()
        if (current?.audio === audio) current = null
        onLevel?.(null)
      }
      function finish(err) {
        if (settled) return
        settled = true
        cleanup()
        err ? reject(err) : resolve()
      }
      function onAbort() {
        finish(abortError())
      }
      signal?.addEventListener('abort', onAbort)

      audio.onloadedmetadata = () => {
        loaded = true
      }
      audio.onerror = () => {
        const code = audio.error?.code
        // 元数据未就绪前的网络/格式错误视为加载失败；解码或播放中出错视为播放失败
        const loadPhase = !loaded && code !== 3
        finish(new PresentationFailure(loadPhase ? PresentationError.AUDIO_LOAD_FAILED : PresentationError.AUDIO_PLAYBACK_FAILED))
      }
      audio.ontimeupdate = () => {
        const d = audio.duration
        if (Number.isFinite(d) && d > 0) onProgress?.(Math.min(1, audio.currentTime / d))
      }
      audio.onended = () => {
        onProgress?.(1)
        finish()
      }

      // 加载超时与播放卡住检测；用定时器而不是 requestAnimationFrame，后台标签页也能继续工作
      const startedAt = Date.now()
      timers.push(
        setInterval(() => {
          if (!loaded && Date.now() - startedAt > timing.audioLoadTimeoutMs) {
            finish(new PresentationFailure(PresentationError.AUDIO_LOAD_FAILED, '语音加载超时'))
            return
          }
          if (loaded && !audio.paused) {
            if (audio.currentTime !== lastTime) {
              lastTime = audio.currentTime
              lastProgressAt = Date.now()
            } else if (Date.now() - lastProgressAt > timing.audioStallTimeoutMs) {
              finish(new PresentationFailure(PresentationError.AUDIO_PLAYBACK_FAILED, '语音播放卡住'))
            }
          }
        }, 500),
      )
      if (analyser && onLevel) {
        timers.push(
          setInterval(() => {
            analyser.getByteTimeDomainData(levelBuffer)
            let sum = 0
            for (let i = 0; i < levelBuffer.length; i++) {
              const v = (levelBuffer[i] - 128) / 128
              sum += v * v
            }
            onLevel(Math.min(1, Math.sqrt(sum / levelBuffer.length) * 4))
          }, LEVEL_SAMPLE_MS),
        )
      } else {
        onLevel?.(null)
      }

      const attempt = () => audio.play()
      attempt().catch(async (err) => {
        if (settled) return
        if (err?.name === 'NotAllowedError' && !muted) {
          // 浏览器阻止有声自动播放：切换为静音继续同一时间轴并明确提示用户，可随时重新打开声音
          setMuted(true, 'autoplay-blocked')
          try {
            await attempt()
            return
          } catch {
            // 静音也无法播放，按播放失败处理
          }
        }
        if (err?.name === 'AbortError' && signal?.aborted) return
        // 尚未加载时 play() 被拒多为资源问题，由 onerror 给出准确分类；这里兜底
        setTimeout(() => {
          if (!settled) finish(new PresentationFailure(loaded ? PresentationError.AUDIO_PLAYBACK_FAILED : PresentationError.AUDIO_LOAD_FAILED))
        }, 200)
      })
    })
  }

  function stop() {
    if (current) {
      try {
        current.audio.pause()
      } catch {
        // 忽略
      }
    }
  }

  return {
    play,
    stop,
    setMuted,
    get muted() {
      return muted
    },
    /** 监听静音变化（含自动播放受阻时的自动静音）。返回取消函数。 */
    onMuteChange(fn) {
      listeners.add(fn)
      return () => listeners.delete(fn)
    },
    /** 在用户手势中调用，提前解锁 AudioContext。 */
    unlock() {
      return ensureContext()
    },
    dispose() {
      stop()
      listeners.clear()
      audioContext?.close?.().catch(() => {})
      audioContext = null
    },
  }
}
