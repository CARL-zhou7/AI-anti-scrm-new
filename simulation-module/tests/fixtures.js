// 测试用的 SimulationState 构造器（字段与接口文档第7章一致）

export function baseState(overrides = {}) {
  return {
    sessionId: '501',
    status: 'RUNNING',
    stateVersion: '0',
    startedAt: '2026-10-15T02:00:00.000Z',
    deadlineAt: '2026-10-15T02:10:00.000Z',
    endedAt: null,
    currentRoundNo: 0,
    configuration: {
      scenario: { id: '1', code: 'TASK_REBATE', name: '刷单返利诈骗', description: '示例', focus: '青年群体' },
      scammer: { id: '11', avatar: { key: 'a', url: '/anti.scam/v1/avatars/a' }, name: '陈启明' },
      victim: { id: '21', avatar: { key: 'b', url: '/anti.scam/v1/avatars/b' }, name: '林晓' },
    },
    currentRound: null,
    latestRiskScore: null,
    outcome: null,
    interruption: null,
    readingUntil: null,
    nextAction: null,
    report: null,
    ...overrides,
  }
}

export function message(overrides = {}) {
  return {
    id: '1001',
    roundNo: 1,
    speaker: 'SCAMMER',
    content: '完成任务后即可领取返利。',
    presentationStatus: 'PRESENTING',
    presentationCompletedAt: null,
    presentationErrorCode: null,
    savedAt: '2026-10-15T02:00:10.000Z',
    audioUrl: '/anti.scam/v1/simulations/501/messages/1001/audio',
    ...overrides,
  }
}

export function analysis(overrides = {}) {
  return {
    fraudStage: '建立信任',
    speechAnalysis: '话术',
    methodAnalysis: '手段',
    victimPsychology: '心理',
    riskScore: 25,
    riskReason: '尚未转账',
    savedAt: '2026-10-15T02:00:20.000Z',
    presentedAt: null,
    ...overrides,
  }
}

let seq = 0
export function event(stateVersion, data, eventId = `evt-${++seq}`) {
  return { eventId, sessionId: '501', stateVersion: String(stateVersion), data }
}
