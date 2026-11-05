-- Keep the surviving rule and all intervention sources; frozen session JSON remains unchanged.
UPDATE scene_rule legacy
LEFT JOIN scene_rule kept ON kept.scenario_code=legacy.scenario_code
  AND kept.target_role=legacy.target_role AND kept.title='小额返利实际兑现'
SET legacy.title='小额返利实际兑现'
WHERE legacy.scenario_code='TASK_REBATE' AND legacy.target_role='SCAMMER'
  AND legacy.title='小额返现即时到账验证' AND kept.id IS NULL;

UPDATE scene_rule_source source
JOIN scene_rule legacy ON legacy.id=source.rule_id
JOIN scene_rule kept ON kept.scenario_code=legacy.scenario_code AND kept.target_role=legacy.target_role
  AND kept.created_by=legacy.created_by AND kept.title='小额返利实际兑现'
SET source.rule_id=kept.id
WHERE legacy.scenario_code='TASK_REBATE' AND legacy.target_role='SCAMMER'
  AND legacy.title='小额返现即时到账验证';

DELETE legacy FROM scene_rule legacy
JOIN scene_rule kept ON kept.scenario_code=legacy.scenario_code AND kept.target_role=legacy.target_role
  AND kept.created_by=legacy.created_by AND kept.title='小额返利实际兑现'
WHERE legacy.scenario_code='TASK_REBATE' AND legacy.target_role='SCAMMER'
  AND legacy.title='小额返现即时到账验证';

UPDATE scene_rule
SET rule_text=CONCAT(
  '何时适用：受害者质疑返利未到账，且骗子此前已承诺该笔返利时。',CHAR(10),
  '规则作用：立即执行实际模拟转账并明确提示查收；禁止虚假截图、口头转账或强迫受害者承认到账代替真实出款。',CHAR(10),CHAR(10),
  '行为指导：先核对系统已保存资金流水；已承诺但尚未支付的该笔返利，必须在本轮立即执行实际模拟转账，输出有效fundActions，并明确告知已发放的金额、提示查收。不得以虚假截图、平台余额展示、口头声称已转账、要求刷新或额外操作拖延支付，也不得在没有真实资金动作时强行让受害者接受已转账或已到账。',CHAR(10),
  '独立报酬使用type=REWARD、purpose=REWARD；退回本金使用type=REFUND、purpose=REFUND并关联原付款ID。本金与报酬分别记录，claimedRefundYuan、执行原文和fundActions须与实际出款一致。',CHAR(10),
  '已支付的同一笔不得重复支付，只能引用已保存流水并提示核对。实际出款仍须遵守剩余出款额度及关联本金限制；额度不足时如实说明未支付，不得伪造到账或要求受害者追加付款才能兑现原承诺。',CHAR(10),
  '实际到账以系统模拟资金账本为准，不涉及现实支付；不替受害者决定相信、确认收款或继续付款。'),
  version_no=version_no+1, updated_at=CURRENT_TIMESTAMP(3)
WHERE scenario_code='TASK_REBATE' AND target_role='SCAMMER' AND title='小额返利实际兑现';
