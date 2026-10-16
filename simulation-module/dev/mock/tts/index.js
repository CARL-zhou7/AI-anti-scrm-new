// 联调用 TTS 服务（只在本地模拟后端使用，不并入主工程）。按顺序尝试多个语音来源：
//   azure —— Microsoft Azure 语音服务（官方接口，需要 AZURE_SPEECH_KEY / AZURE_SPEECH_REGION，免费层每月 50 万字符）
//   edge  —— Microsoft Edge 朗读服务（免费、无需密钥，但非官方，可能被限流返回 429）
//   sapi  —— Windows 系统自带中文语音（离线，无网络与限流问题；仅 Windows）
//   tone  —— 本地合成提示音（最后兜底）
// azure / edge 返回 MP3（与 API-21 的 audio/mpeg 一致），sapi / tone 返回 WAV。
// 正式环境由后端接入选定的云端语音供应商，前端无需改动。

import { spawn } from 'node:child_process'
import { mkdtemp, readFile, rm, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { synthesizeSpeechWav } from '../wav.js'

const TIMEOUT_MS = 15000
const BLOCK_MS = 60000 // 某个来源失败后，自动模式在这段时间内跳过它，避免每句话都等待

function escapeXml(text) {
  return text.replace(/[<>&'"]/g, (c) => ({ '<': '&lt;', '>': '&gt;', '&': '&amp;', "'": '&apos;', '"': '&quot;' })[c])
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

function friendlyError(provider, err) {
  const msg = err?.message || String(err)
  if (/429/.test(msg)) return `${provider}：服务限流（429），请稍后再试，或配置 Azure 语音密钥`
  if (/403/.test(msg)) return `${provider}：服务拒绝访问（403）`
  if (/ENOTFOUND|ECONNREFUSED|ETIMEDOUT|EAI_AGAIN|超时|timeout/i.test(msg)) return `${provider}：网络无法连接（${msg}）`
  return `${provider}：${msg}`
}

// ---------- Azure 语音服务（官方） ----------

function azureConfigured() {
  return !!(process.env.AZURE_SPEECH_KEY && process.env.AZURE_SPEECH_REGION)
}

async function azureSynthesize(text, profile) {
  if (!azureConfigured()) throw new Error('未配置 AZURE_SPEECH_KEY / AZURE_SPEECH_REGION')
  const ssml =
    `<speak version="1.0" xmlns="http://www.w3.org/2001/10/synthesis" xml:lang="zh-CN">` +
    `<voice name="${profile.voice}"><prosody rate="${profile.rate}" pitch="${profile.pitch}">${escapeXml(text)}</prosody></voice></speak>`
  const res = await fetch(`https://${process.env.AZURE_SPEECH_REGION}.tts.speech.microsoft.com/cognitiveservices/v1`, {
    method: 'POST',
    headers: {
      'Ocp-Apim-Subscription-Key': process.env.AZURE_SPEECH_KEY,
      'Content-Type': 'application/ssml+xml',
      'X-Microsoft-OutputFormat': 'audio-24khz-96kbitrate-mono-mp3',
      'User-Agent': 'anti-scam-simulation-dev',
    },
    body: ssml,
    signal: AbortSignal.timeout(TIMEOUT_MS),
  })
  if (!res.ok) throw new Error(`服务返回 ${res.status}`)
  const audio = Buffer.from(await res.arrayBuffer())
  if (audio.length < 1000) throw new Error('返回了空音频')
  return audio
}

// ---------- Edge 朗读服务（免费、非官方） ----------

let edgeModule = null
async function loadEdge() {
  if (!edgeModule) edgeModule = await import('msedge-tts')
  return edgeModule
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

// 每种音色复用一个连接，所有请求排队串行发送，减少建连次数以降低被限流的概率
const edgeClients = new Map()
let edgeQueue = Promise.resolve()

async function edgeClient(voice) {
  if (!edgeClients.has(voice)) {
    const { MsEdgeTTS, OUTPUT_FORMAT } = await loadEdge()
    const tts = new MsEdgeTTS({ agent: await proxyAgent(), connectionTimeoutMs: TIMEOUT_MS, synthesisTimeoutMs: TIMEOUT_MS })
    await tts.setMetadata(voice, OUTPUT_FORMAT.AUDIO_24KHZ_96KBITRATE_MONO_MP3)
    edgeClients.set(voice, tts)
  }
  return edgeClients.get(voice)
}

function dropEdgeClient(voice) {
  try {
    edgeClients.get(voice)?.close()
  } catch {
    // 忽略
  }
  edgeClients.delete(voice)
}

async function edgeOnce(text, profile) {
  const tts = await edgeClient(profile.voice)
  const { audioStream } = tts.toStream(escapeXml(text), { rate: profile.rate, pitch: profile.pitch })
  const chunks = []
  await new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error('语音合成超时')), TIMEOUT_MS)
    audioStream.on('data', (c) => chunks.push(c))
    audioStream.on('end', () => (clearTimeout(timer), resolve()))
    audioStream.on('error', (e) => (clearTimeout(timer), reject(e)))
  })
  const audio = Buffer.concat(chunks)
  if (audio.length < 1000) throw new Error('返回了空音频')
  return audio
}

function edgeSynthesize(text, profile) {
  const run = async () => {
    const delays = [1500, 4000] // 429 限流时退避重试
    for (let attempt = 0; ; attempt++) {
      try {
        return await edgeOnce(text, profile)
      } catch (err) {
        dropEdgeClient(profile.voice)
        if (!/429/.test(err?.message || '') || attempt >= delays.length) throw err
        await sleep(delays[attempt])
      }
    }
  }
  const task = edgeQueue.then(run, run)
  edgeQueue = task.catch(() => {})
  return task
}

// ---------- Windows 系统中文语音（离线） ----------

const SAPI_SCRIPT = `
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Speech
$s = New-Object System.Speech.Synthesis.SpeechSynthesizer
$voices = $s.GetInstalledVoices() | Where-Object { $_.Enabled -and $_.VoiceInfo.Culture.Name -like 'zh-*' }
if (-not $voices) { [Console]::Error.WriteLine('NO_ZH_VOICE'); exit 3 }
$pick = $voices | Where-Object { $_.VoiceInfo.Gender -eq $env:SIM_TTS_GENDER } | Select-Object -First 1
if (-not $pick) { $pick = $voices | Select-Object -First 1 }
$s.SelectVoice($pick.VoiceInfo.Name)
$s.SetOutputToWaveFile($env:SIM_TTS_WAV)
$ssml = [System.IO.File]::ReadAllText($env:SIM_TTS_SSML, [System.Text.Encoding]::UTF8)
try { $s.SpeakSsml($ssml) } catch { $s.Speak([System.IO.File]::ReadAllText($env:SIM_TTS_TEXT, [System.Text.Encoding]::UTF8)) }
$s.Dispose()
`

async function sapiSynthesize(text, profile) {
  if (process.platform !== 'win32') throw new Error('仅 Windows 可用')
  const dir = await mkdtemp(join(tmpdir(), 'sim-tts-'))
  try {
    const files = { wav: join(dir, 'out.wav'), ssml: join(dir, 'in.xml'), text: join(dir, 'in.txt') }
    await writeFile(
      files.ssml,
      `<speak version="1.0" xmlns="http://www.w3.org/2001/10/synthesis" xml:lang="zh-CN"><prosody rate="${profile.rate}" pitch="${profile.pitch}">${escapeXml(text)}</prosody></speak>`,
    )
    await writeFile(files.text, text)
    await new Promise((resolve, reject) => {
      // -EncodedCommand（UTF-16LE Base64）避免命令行引号与换行转义问题
      const encoded = Buffer.from(SAPI_SCRIPT, 'utf16le').toString('base64')
      const child = spawn('powershell.exe', ['-NoProfile', '-NonInteractive', '-ExecutionPolicy', 'Bypass', '-EncodedCommand', encoded], {
        env: {
          ...process.env,
          SIM_TTS_WAV: files.wav,
          SIM_TTS_SSML: files.ssml,
          SIM_TTS_TEXT: files.text,
          SIM_TTS_GENDER: profile.gender === 'female' ? 'Female' : 'Male',
        },
        windowsHide: true,
      })
      let stderr = ''
      child.stderr.on('data', (d) => (stderr += d))
      const timer = setTimeout(() => (child.kill(), reject(new Error('语音合成超时'))), TIMEOUT_MS)
      child.on('error', (e) => (clearTimeout(timer), reject(e)))
      child.on('close', (code) => {
        clearTimeout(timer)
        if (code === 0) resolve()
        else reject(new Error(/NO_ZH_VOICE/.test(stderr) ? '系统未安装中文语音（设置 → 时间和语言 → 语音 → 添加语音）' : `PowerShell 退出码 ${code}`))
      })
    })
    const audio = await readFile(files.wav)
    if (audio.length < 1000) throw new Error('返回了空音频')
    return audio
  } finally {
    rm(dir, { recursive: true, force: true }).catch(() => {})
  }
}

// ---------- 调度 ----------

const PROVIDERS = {
  azure: { label: 'Azure 语音服务', contentType: 'audio/mpeg', run: azureSynthesize },
  edge: { label: 'Edge 朗读服务', contentType: 'audio/mpeg', run: edgeSynthesize },
  sapi: { label: 'Windows 系统语音', contentType: 'audio/wav', run: sapiSynthesize },
}

function autoOrder() {
  const order = []
  if (azureConfigured()) order.push('azure')
  order.push('edge')
  if (process.platform === 'win32') order.push('sapi')
  return order
}

/**
 * @param {() => {tts?: 'auto'|'azure'|'edge'|'sapi'|'tone'}} getConfig 读取当前联调配置
 */
export function createTtsService(getConfig) {
  const cache = new Map()
  const blockedUntil = {}
  const status = { provider: null, label: null, degraded: false, errors: [], lastError: null }

  async function synthesize(text, { speaker, profile, seed, msPerChar }) {
    const { tts: mode = 'auto' } = getConfig()
    const key = `${mode}|${profile.voice}|${profile.rate}|${profile.pitch}|${text}`
    if (cache.has(key)) return cache.get(key)

    const order = mode === 'auto' ? autoOrder() : mode === 'tone' ? [] : [mode]
    const errors = []
    for (const name of order) {
      if (mode === 'auto' && Date.now() < (blockedUntil[name] || 0)) continue
      const provider = PROVIDERS[name]
      try {
        const audio = await provider.run(text, profile)
        const result = { audio, contentType: provider.contentType }
        // 自动模式下排在前面的来源失败而用了后面的来源，也算降级（提示用户原因）
        Object.assign(status, { provider: name, label: provider.label, degraded: errors.length > 0, errors, lastError: errors[0] || null })
        cache.set(key, result)
        return result
      } catch (err) {
        const reason = friendlyError(provider.label, err)
        errors.push(reason)
        blockedUntil[name] = Date.now() + BLOCK_MS
        console.warn(`[mock-tts] ${reason}`)
        if (mode !== 'auto') {
          Object.assign(status, { provider: null, label: null, degraded: true, errors, lastError: reason })
          throw err // 指定单一来源时失败按 TTS_FAILED 中断，用于验证中断流程
        }
      }
    }

    // 兜底提示音不缓存：语音服务恢复后同一句话会重新合成真实语音
    const lastError = errors[errors.length - 1] || (mode === 'auto' ? status.lastError : null)
    Object.assign(status, { provider: 'tone', label: '提示音', degraded: mode !== 'tone', errors, lastError })
    return { audio: synthesizeSpeechWav(text, { speaker, msPerChar, seed }), contentType: 'audio/wav' }
  }

  return { synthesize, status }
}
