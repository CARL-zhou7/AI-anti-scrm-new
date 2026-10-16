// 联调用 TTS 服务：优先调用 Microsoft Edge 朗读服务（免费、无需密钥，返回 MP3，与 API-21 的 audio/mpeg 一致），
// 失败时按配置退回本地合成提示音，并记录原因供演示页提示。
// 注意：Edge 朗读服务不是官方商用 API，只用于开发与演示；正式环境由后端接入选定的云端语音供应商。

import { synthesizeSpeechWav } from '../wav.js'

const TIMEOUT_MS = 15000

let EdgeTTS = null
let OUTPUT_FORMAT = null
async function loadEdge() {
  if (!EdgeTTS) {
    const mod = await import('msedge-tts')
    EdgeTTS = mod.MsEdgeTTS
    OUTPUT_FORMAT = mod.OUTPUT_FORMAT
  }
}

async function proxyAgent() {
  const url = process.env.HTTPS_PROXY || process.env.https_proxy
  if (!url) return undefined
  try {
    const { HttpsProxyAgent } = await import('https-proxy-agent')
    return new HttpsProxyAgent(url)
  } catch {
    return undefined
  }
}

function escapeXml(text) {
  return text.replace(/[<>&'"]/g, (c) => ({ '<': '&lt;', '>': '&gt;', '&': '&amp;', "'": '&apos;', '"': '&quot;' })[c])
}

async function edgeSynthesize(text, profile) {
  await loadEdge()
  const tts = new EdgeTTS({ agent: await proxyAgent(), connectionTimeoutMs: TIMEOUT_MS, synthesisTimeoutMs: TIMEOUT_MS })
  try {
    await tts.setMetadata(profile.voice, OUTPUT_FORMAT.AUDIO_24KHZ_96KBITRATE_MONO_MP3)
    const { audioStream } = tts.toStream(escapeXml(text), { rate: profile.rate, pitch: profile.pitch })
    const chunks = []
    await new Promise((resolve, reject) => {
      const timer = setTimeout(() => reject(new Error('语音合成超时')), TIMEOUT_MS)
      audioStream.on('data', (c) => chunks.push(c))
      audioStream.on('end', () => {
        clearTimeout(timer)
        resolve()
      })
      audioStream.on('error', (e) => {
        clearTimeout(timer)
        reject(e)
      })
    })
    const audio = Buffer.concat(chunks)
    if (audio.length < 1000) throw new Error('语音服务返回了空音频')
    return audio
  } finally {
    tts.close()
  }
}

/**
 * @param {{mode: 'auto'|'edge'|'tone'}} getConfig 读取当前联调配置
 */
export function createTtsService(getConfig) {
  const cache = new Map()
  const status = { provider: null, degraded: false, lastError: null }
  // 自动模式下 Edge 失败后 60 秒内直接使用提示音，避免每条消息都等待超时拖慢演绎
  let edgeBlockedUntil = 0

  async function synthesize(text, { speaker, profile, seed, msPerChar }) {
    const { tts: mode = 'auto' } = getConfig()
    const key = `${mode}|${profile.voice}|${profile.rate}|${profile.pitch}|${text}`
    if (cache.has(key)) return cache.get(key)

    let result
    if (mode === 'edge' || (mode === 'auto' && Date.now() >= edgeBlockedUntil)) {
      try {
        result = { audio: await edgeSynthesize(text, profile), contentType: 'audio/mpeg' }
        Object.assign(status, { provider: 'edge', degraded: false, lastError: null })
      } catch (err) {
        status.lastError = err?.message || String(err)
        edgeBlockedUntil = Date.now() + 60000
        console.warn(`[mock-tts] Edge 语音合成失败：${status.lastError}`)
        if (mode === 'edge') throw err // 严格模式：按 TTS 失败中断，验证中断流程
      }
    }
    if (!result) {
      result = { audio: synthesizeSpeechWav(text, { speaker, msPerChar, seed }), contentType: 'audio/wav' }
      Object.assign(status, { provider: 'tone', degraded: mode !== 'tone' })
    }
    // 降级得到的提示音不缓存：语音服务恢复后同一句话会重新合成真实语音
    if (!status.degraded) cache.set(key, result)
    return result
  }

  return { synthesize, status }
}
