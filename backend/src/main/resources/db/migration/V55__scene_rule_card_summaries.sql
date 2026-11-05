-- Add explicit card summaries while retaining the complete execution rules.
UPDATE scene_rule
SET rule_text=CONCAT('何时适用：已承诺的小额报酬或返款达到兑现条件时。',CHAR(10),'规则作用：按资金账本实际支付，不用截图或口头承诺代替到账，避免重复出款。',CHAR(10),CHAR(10),rule_text),
    version_no=version_no+1, updated_at=CURRENT_TIMESTAMP(3)
WHERE scenario_code='TASK_REBATE' AND target_role='SCAMMER'
  AND title='小额返利实际兑现' AND rule_text NOT LIKE '何时适用：%';

UPDATE scene_rule
SET rule_text=CONCAT('何时适用：虚假投资从建立信任、模拟体验转入真实投入时。',CHAR(10),'规则作用：先展示专业资料，再体验模拟收益，随后提出明确的小额实盘请求，避免模拟盘循环。',CHAR(10),CHAR(10),rule_text),
    version_no=version_no+1, updated_at=CURRENT_TIMESTAMP(3)
WHERE scenario_code='FAKE_INVESTMENT' AND target_role='SCAMMER'
  AND title='专业资料与模拟收益过渡' AND rule_text NOT LIKE '何时适用：%';

UPDATE scene_rule
SET rule_text=CONCAT('何时适用：冒充亲友建立身份、说明借款原因及推进转账时。',CHAR(10),'规则作用：选择合理亲友关系并保持称谓一致，避免默认以同住配偶出事为借款理由。',CHAR(10),CHAR(10),rule_text),
    version_no=version_no+1, updated_at=CURRENT_TIMESTAMP(3)
WHERE scenario_code='IMPERSONATION_LOAN' AND target_role='SCAMMER'
  AND title='亲友身份与求助背景一致' AND rule_text NOT LIKE '何时适用：%';


