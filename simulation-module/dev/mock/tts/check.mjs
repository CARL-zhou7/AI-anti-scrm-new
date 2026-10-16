// 语音来源自检：npm run tts:check
// 逐个尝试 Azure / Edge / Windows 系统语音，打印是否可用及原因，并把试听文件写到 dev/mock/tts/samples/。

import { mkdir, writeFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { createTtsService } from './index.js'
import { assignVoices } from './voiceProfiles.js'

const outDir = fileURLToPath(new URL('./samples/', import.meta.url))
await mkdir(outDir, { recursive: true })

const voices = assignVoices({ name: '陈启明', gender: '男', age: 35 }, { name: '林晓', gender: '女', age: 22 })
const lines = {
  SCAMMER: '你好，我是平台客服，现在招兼职刷单，一单返利百分之十。',
  VICTIM: '要先垫钱啊？我得先问问我朋友。',
}

for (const mode of ['azure', 'edge', 'sapi']) {
  const service = createTtsService(() => ({ tts: mode }))
  for (const speaker of ['SCAMMER', 'VICTIM']) {
    const started = Date.now()
    try {
      const { audio, contentType } = await service.synthesize(lines[speaker], { speaker, profile: voices[speaker], seed: 1, msPerChar: 150 })
      const file = `${outDir}${mode}-${speaker.toLowerCase()}.${contentType === 'audio/mpeg' ? 'mp3' : 'wav'}`
      await writeFile(file, audio)
      console.log(`✔ ${mode.padEnd(5)} ${speaker.padEnd(7)} 可用，${Date.now() - started} ms，试听：${file}`)
    } catch {
      console.log(`✘ ${mode.padEnd(5)} ${speaker.padEnd(7)} 不可用：${service.status.lastError}`)
      break
    }
  }
}
console.log('\n自动模式的尝试顺序：Azure（已配置密钥时）→ Edge → Windows 系统语音 → 提示音')
process.exit(0)
