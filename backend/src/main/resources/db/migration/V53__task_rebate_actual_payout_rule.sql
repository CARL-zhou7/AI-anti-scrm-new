-- Seed an editable scammer rule without changing existing rules or session snapshots.
INSERT INTO scene_rule (scenario_code, target_role, title, rule_text, created_by)
SELECT 'TASK_REBATE', 'SCAMMER', '小额返利实际兑现',
  CONCAT(
    '适用情况：刷单返利场景中，已承诺的小额体验报酬或返款达到约定兑现条件，尤其受害者表示未到账或到账后才继续时。', CHAR(10),
    '行为指导：先核对系统已保存流水；尚未支付且剩余出款额度及关联本金允许时，必须在本轮执行模拟转账并输出有效fundActions，不得只发送截图、展示平台余额、重复承诺或要求刷新账户代替实际支付。', CHAR(10),
    '独立报酬使用REWARD；退回本金使用REFUND并关联原付款ID；本金与报酬分别记录，claimedRefundYuan及执行原文须与实际出款一致。已经支付的同一笔不重复支付。', CHAR(10),
    '条件未满足或额度不足时如实说明尚未支付，不虚构到账、不要求受害者追加付款才能兑现原有承诺；无法满足继续条件时允许自然结束，不重复发送凭证拖延。', CHAR(10),
    '实际到账以系统账本为准，不涉及现实支付；仅证明该笔款项已支付，不代表项目可信，不替受害者决定相信或继续付款。'
  ), admin.id
FROM (SELECT MIN(id) AS id FROM sys_user WHERE role = 'ADMIN') admin
WHERE admin.id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM scene_rule
    WHERE scenario_code = 'TASK_REBATE' AND target_role = 'SCAMMER' AND title = '小额返利实际兑现'
  );
