// 角色声音分配（PC-P04：双方声音可区分）。按画像的性别与年龄选择中文神经语音，并用语速/音高区分年龄感。
// 这是给后端 TTS 实现的参考规则：真实环境换成选定的云端语音供应商时，按同样的思路配置音色即可。

// Microsoft Edge 朗读服务中的中文（普通话）神经语音
const VOICES = {
  femaleYoung: 'zh-CN-XiaoxiaoNeural',
  femaleLively: 'zh-CN-XiaoyiNeural',
  maleYoung: 'zh-CN-YunxiNeural',
  maleMature: 'zh-CN-YunjianNeural',
  maleSteady: 'zh-CN-YunyangNeural',
}

// 初始五位人物的指定音色（PRD §11.3）
const PRESET = {
  陈启明: { voice: VOICES.maleSteady, rate: '-4%', pitch: '-2Hz' }, // 沉稳、耐心
  梁悦: { voice: VOICES.femaleLively, rate: '+6%', pitch: '+0Hz' }, // 表达直接、急于推进
  林晓: { voice: VOICES.femaleYoung, rate: '+2%', pitch: '+2Hz' }, // 22岁求职者
  赵诚: { voice: VOICES.maleMature, rate: '+0%', pitch: '-3Hz' }, // 41岁店主
  周桂兰: { voice: VOICES.femaleYoung, rate: '-14%', pitch: '-12Hz' }, // 66岁，放慢、降调体现年龄感
}

function isFemale(gender) {
  return /女|female|F/i.test(gender || '')
}

function withGender(v) {
  return { ...v, gender: /Xiao/.test(v.voice) ? 'female' : 'male' }
}

/** 按画像推导默认声音（管理员新增的人物也能得到合适音色）。 */
export function voiceForProfile(profile) {
  return withGender(baseVoice(profile))
}

function baseVoice(profile) {
  if (PRESET[profile.name]) return { ...PRESET[profile.name] }
  const age = Number(profile.age) || 30
  if (isFemale(profile.gender)) {
    if (age >= 55) return { voice: VOICES.femaleYoung, rate: '-12%', pitch: '-10Hz' }
    if (age >= 35) return { voice: VOICES.femaleLively, rate: '-4%', pitch: '-4Hz' }
    return { voice: VOICES.femaleYoung, rate: '+2%', pitch: '+0Hz' }
  }
  if (age >= 55) return { voice: VOICES.maleSteady, rate: '-10%', pitch: '-6Hz' }
  if (age >= 35) return { voice: VOICES.maleMature, rate: '+0%', pitch: '-2Hz' }
  return { voice: VOICES.maleYoung, rate: '+2%', pitch: '+0Hz' }
}

/** 为一场演绎分配双方声音；若两人恰好同一音色，则调整受害者的音高与语速以便区分。 */
export function assignVoices(scammer, victim) {
  const a = voiceForProfile(scammer)
  const b = voiceForProfile(victim)
  if (a.voice === b.voice && a.pitch === b.pitch) {
    b.pitch = isFemale(victim.gender) ? '+8Hz' : '-8Hz'
    b.rate = '-6%'
  }
  return { SCAMMER: a, VICTIM: b }
}

/** 在基础语速上叠加整体倍率（联调“快速”节奏用）。 */
export function withSpeed(profile, extraPercent) {
  if (!extraPercent) return profile
  const base = parseInt(profile.rate, 10) || 0
  const total = base + extraPercent
  return { ...profile, rate: `${total >= 0 ? '+' : ''}${total}%` }
}
