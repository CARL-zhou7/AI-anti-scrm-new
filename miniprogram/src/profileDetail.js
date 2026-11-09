const levels = { LOW: '低', MEDIUM: '中', HIGH: '高' }
const facts = [
  ['familyStatus', '家庭生活'], ['incomeDescription', '收入'], ['monthlyExpenseYuan', '每月支出'],
  ['initialSavingsYuan', '初始存款'], ['availableFundsYuan', '可立即动用资金'], ['reservedFundsYuan', '预留合计'],
  ['generalReserveFundsYuan', '普通预留'], ['emergencyReserveFundsYuan', '应急预留'],
  ['reservedFundsNote', '预留资金安排'], ['generalDecisionTraits', '一般决策特点']
]
const scenarioFields = [
  ['needs', '场景需求'], ['relevantExperience', '相关经验'], ['judgmentBasis', '判断依据'],
  ['verificationHabits', '核实习惯'], ['actionBoundaries', '行动边界'], ['stopConditions', '停止条件'],
  ['recentEvents', '近期事件'], ['consultationsTransactions', '咨询或交易经历'], ['sharedExperiences', '共同经历'],
  ['contactBackground', '接触背景'], ['initialTrustMode', '初始信任方式']
]

export function profileDetailRows(profile, scenarioCode) {
  const rows = [['身份', profile.identity], ['人物描述', profile.description]]
  if ('fraudExperience' in profile) {
    rows.push(['诈骗经验', levels[profile.fraudExperience]], ['话术能力', levels[profile.speechSkill]],
      ['心理操控能力', levels[profile.manipulationSkill]], ['行为特点', profile.behaviorTraits])
  } else {
    rows.push(['防骗意识', levels[profile.antiFraudAwareness]], ['风险倾向', levels[profile.riskTendency]],
      ['性格特点', profile.personalityTraits])
    for (const [key, label] of facts) {
      const value = profile[key]
      rows.push([label, (profile.profileSchemaVersion ?? 1) >= 2 && value != null
        ? `${value}${key.endsWith('Yuan') ? '元' : ''}` : '未记录'])
    }
    const scenario = profile.scenarioProfiles?.find(item => item.scenarioCode === scenarioCode)
    if (scenario) rows.push(...scenarioFields.map(([key, label]) => [label, scenario[key]]))
  }
  rows.push(['时间', profile.contextTime], ['地点', profile.contextPlace], ['当前状态', profile.currentState])
  return rows
}
