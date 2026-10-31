# 对话、追加剧情与信任风险联动实施计划

目标：修正称呼与复述；已受骗后保留合理追加空间；复盘逐轮显示信任分；程序按信任行动门槛确定风险。

按用户现有授权直接实施，不增加审批、子代理、依赖或复杂测试。旧提示词不修改；新版本追加迁移。业务事实与实际资金不伪造。

- [x] AgentContextAssembler / prompts：提示词明确称呼反向关系、直接回应而非重述；骗子根据实际回应合理追加，不推测余额。新增 V31，覆盖两个角色模板。
- [x] SimulationTransitions / OutcomeRules：保留受骗事实与目标达成；trust-v1 在尚只有小额付款、仍可付款且未停止时继续，大额完成、明确止损、资金耗尽或上限时正常结束；前四轮预检仍接受有效目标。
- [x] TrustRiskRules / SimulationTransitions：风险随冻结接受、小额、大额门槛升至 0/25/50/75，发生实际受骗为 100；分数相同、事实相同时保持一致；角色不接收数值。
- [x] SimulationStateAssembler / SimulationViews.RiskPoint / openapi.yaml / types.ts：在逐轮风险数据中附上已保存事件推导的 trustChange，不使用最终分数回填历史。
- [x] ReportView / HistoryRounds：逐轮显示前分、后分和净增减，旧记录明确未记录。
- [x] 少量后端规则/保存路径与前端展示测试，前端构建和接口校验；不调用真实模型或业务数据库。
