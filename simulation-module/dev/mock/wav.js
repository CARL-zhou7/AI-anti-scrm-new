// 本地模拟用的“伪语音”：按字数生成带音节节奏的音频（WAV），双方音高不同，便于听出角色区分。
// 真实环境由后端 TTS 提供 MP3（audio/mpeg）。

const SAMPLE_RATE = 16000

function seeded(seed) {
  let s = seed >>> 0 || 1
  return () => {
    s = (s * 1664525 + 1013904223) >>> 0
    return s / 0xffffffff
  }
}

/**
 * @param {string} text
 * @param {{speaker: 'SCAMMER'|'VICTIM', msPerChar?: number, seed?: number}} opts
 */
export function synthesizeSpeechWav(text, { speaker, msPerChar = 150, seed = 1 }) {
  const rand = seeded(seed)
  const base = speaker === 'SCAMMER' ? 125 : 215
  const chars = Array.from(text)
  const segments = []
  for (const ch of chars) {
    if (/[，。！？、；：,.!?;:…\s]/.test(ch)) segments.push({ pause: true, ms: msPerChar * 1.2 })
    else segments.push({ pause: false, ms: msPerChar * (0.85 + rand() * 0.3), f0: base * (0.9 + rand() * 0.25) })
  }
  const totalMs = segments.reduce((sum, s) => sum + s.ms, 0) + 200
  const total = Math.ceil((totalMs / 1000) * SAMPLE_RATE)
  const pcm = new Int16Array(total)

  let offset = Math.floor(0.1 * SAMPLE_RATE)
  for (const seg of segments) {
    const len = Math.floor((seg.ms / 1000) * SAMPLE_RATE)
    if (!seg.pause) {
      for (let i = 0; i < len && offset + i < total; i++) {
        const t = i / SAMPLE_RATE
        const p = i / len
        const env = Math.min(1, p / 0.12) * Math.pow(1 - p, 0.8)
        const f = seg.f0 * (1 + 0.04 * Math.sin(2 * Math.PI * 5 * t))
        const voice =
          Math.sin(2 * Math.PI * f * t) * 0.55 +
          Math.sin(2 * Math.PI * 2 * f * t) * 0.25 +
          Math.sin(2 * Math.PI * 3 * f * t) * 0.12 +
          Math.sin(2 * Math.PI * 4.5 * f * t) * 0.06
        pcm[offset + i] = Math.round(voice * env * 0.32 * 32767)
      }
    }
    offset += len
  }

  const dataBytes = pcm.length * 2
  const buf = Buffer.alloc(44 + dataBytes)
  buf.write('RIFF', 0)
  buf.writeUInt32LE(36 + dataBytes, 4)
  buf.write('WAVE', 8)
  buf.write('fmt ', 12)
  buf.writeUInt32LE(16, 16)
  buf.writeUInt16LE(1, 20)
  buf.writeUInt16LE(1, 22)
  buf.writeUInt32LE(SAMPLE_RATE, 24)
  buf.writeUInt32LE(SAMPLE_RATE * 2, 28)
  buf.writeUInt16LE(2, 32)
  buf.writeUInt16LE(16, 34)
  buf.write('data', 36)
  buf.writeUInt32LE(dataBytes, 40)
  Buffer.from(pcm.buffer).copy(buf, 44)
  return buf
}
