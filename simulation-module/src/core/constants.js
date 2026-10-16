// 接口文档第7章枚举与 PRD 展示口径。展示文案优先使用服务端返回的 displayText / message。

export const MAX_ROUNDS = 20
export const SIMULATION_LIMIT_SECONDS = 600

export const SimulationStatus = Object.freeze({
  RUNNING: 'RUNNING',
  FINISHED: 'FINISHED',
  INTERRUPTED: 'INTERRUPTED',
})

export const RoundPhase = Object.freeze({
  SCAMMER_GENERATING: 'SCAMMER_GENERATING',
  SCAMMER_PRESENTING: 'SCAMMER_PRESENTING',
  VICTIM_GENERATING: 'VICTIM_GENERATING',
  VICTIM_PRESENTING: 'VICTIM_PRESENTING',
  ANALYZING: 'ANALYZING',
  ANALYSIS_PRESENTING: 'ANALYSIS_PRESENTING',
  COMPLETE: 'COMPLETE',
})

export const Speaker = Object.freeze({ SCAMMER: 'SCAMMER', VICTIM: 'VICTIM' })

// 客户端可上报的呈现错误（PresentationError），不得上报 TTS/分析/保存故障或结局
export const PresentationError = Object.freeze({
  TEXT_RENDER_FAILED: 'TEXT_RENDER_FAILED',
  AVATAR_LOAD_FAILED: 'AVATAR_LOAD_FAILED',
  ANIMATION_FAILED: 'ANIMATION_FAILED',
  AUDIO_LOAD_FAILED: 'AUDIO_LOAD_FAILED',
  AUDIO_PLAYBACK_FAILED: 'AUDIO_PLAYBACK_FAILED',
})

// 接口文档§4.1：各类回执允许的失败类别
export const ALLOWED_FAILURES = Object.freeze({
  opening: [PresentationError.TEXT_RENDER_FAILED, PresentationError.AVATAR_LOAD_FAILED, PresentationError.ANIMATION_FAILED],
  message: [
    PresentationError.TEXT_RENDER_FAILED,
    PresentationError.ANIMATION_FAILED,
    PresentationError.AUDIO_LOAD_FAILED,
    PresentationError.AUDIO_PLAYBACK_FAILED,
  ],
  analysis: [PresentationError.TEXT_RENDER_FAILED],
})

export const ExitReason = Object.freeze({
  NAVIGATION: 'NAVIGATION',
  REFRESH: 'REFRESH',
  CLOSE: 'CLOSE',
  LOGOUT: 'LOGOUT',
})

export const PHASE_LABELS = Object.freeze({
  SCAMMER_GENERATING: '骗子正在组织话术',
  SCAMMER_PRESENTING: '骗子发言中',
  VICTIM_GENERATING: '受害者正在回应',
  VICTIM_PRESENTING: '受害者回应中',
  ANALYZING: '解说正在分析本轮',
  ANALYSIS_PRESENTING: '本轮解说',
  COMPLETE: '本轮完成',
})

// 服务端未给出 message 时的兜底文案
export const INTERRUPTION_LABELS = Object.freeze({
  USER_EXIT: '已离开演绎页面，本次演绎已中断。',
  DIALOGUE_FAILED: '角色对话生成失败，本次演绎已中断。',
  ANALYSIS_FAILED: '解说分析生成失败，本次演绎已中断。',
  TTS_FAILED: '角色语音生成失败，本次演绎已中断。',
  MEDIA_FAILED: '必要媒体呈现失败，本次演绎已中断。',
  PERSISTENCE_FAILED: '内容保存失败，本次演绎已中断。',
  TIME_LIMIT: '中断：达到时长上限。',
  CONNECTION_LOST: '连接已断开，本次演绎已中断。',
  SERVICE_RESTARTED: '服务重启，本次演绎已中断。',
})

export const LIMIT_REASON_LABELS = Object.freeze({
  ROUND_LIMIT: '达到 20 轮上限',
  TIME_LIMIT: '达到 10 分钟时长上限',
  BOTH: '同时达到 20 轮与 10 分钟上限',
})

// PRD §10.2 五档风险；0 与 100 均为教学档位，不代表现实中的绝对安全或必然受骗
export const RISK_TIERS = Object.freeze({
  0: { label: '已拒绝或停止配合', tone: 'safe' },
  25: { label: '保持警惕', tone: 'calm' },
  50: { label: '产生信任', tone: 'watch' },
  75: { label: '准备危险操作', tone: 'danger' },
  100: { label: '正在或已经执行危险操作', tone: 'critical' },
})
export const RISK_SCORES = Object.freeze([0, 25, 50, 75, 100])

export const LEVEL_LABELS = Object.freeze({ LOW: '低', MEDIUM: '中', HIGH: '高' })

// 三个固定场景的舞台氛围（只影响视觉，不影响业务）
export const SCENARIO_THEMES = Object.freeze({
  TASK_REBATE: { accent: '#e8a33d', prop: 'task' },
  FAKE_INVESTMENT: { accent: '#4fb38a', prop: 'chart' },
  IMPERSONATION_LOAN: { accent: '#e0736b', prop: 'chat' },
})

// 演出节奏参数（不改变业务规则，只影响表现层）
export const PRESENTATION_TIMING = Object.freeze({
  openingMs: 3200, // 开场标题与人物入场动画时长，计入 600 秒
  avatarLoadTimeoutMs: 15000,
  audioLoadTimeoutMs: 15000,
  audioStallTimeoutMs: 12000,
  readingGraceMs: 1500, // readingUntil 到期后等待 round.started 的观察窗口，超时则 GET 核对
  reconcileIntervalMs: 3000,
  idleWatchdogMs: 20000,
})
