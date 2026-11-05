-- Clarify the existing progression rule without replacing other administrator edits.
UPDATE scene_rule
SET rule_text = REPLACE(REPLACE(rule_text,
    '前4轮先用虚构的专业资历、产品介绍与操作资料建立联系，再指导免费模拟盘体验，展示模拟收益并回应受害者的实际反应。',
    '第1至4轮（含第4轮）仅用虚构的专业资历、产品介绍与操作资料建立联系，不邀请、开启或演示模拟盘体验，不展示模拟收益，也不提出实盘付款。即使受害者主动表示想试，也先回应问题并完成资料介绍。第5轮起才可指导免费模拟盘体验，展示模拟收益并回应受害者的实际反应。'),
    '第5轮起转入金额、用途与模拟收款对象明确的小额实盘邀请；',
    '在第5轮或之后完成模拟体验、已展示模拟收益并获得受害者回应后，才在后续轮次转入金额、用途与模拟收款对象明确的小额实盘邀请；'),
    version_no = version_no + 1, updated_at = CURRENT_TIMESTAMP(3)
WHERE scenario_code = 'FAKE_INVESTMENT' AND target_role = 'SCAMMER'
  AND title = '专业资料与模拟收益过渡'
  AND rule_text LIKE '%前4轮先用虚构的专业资历、产品介绍与操作资料建立联系，再指导免费模拟盘体验，展示模拟收益并回应受害者的实际反应。%';

-- V57 is recorded as applied locally, but the conflicting rule is currently still enabled.
UPDATE scene_rule
SET status = 'DISABLED', version_no = version_no + 1, updated_at = CURRENT_TIMESTAMP(3)
WHERE scenario_code = 'FAKE_INVESTMENT' AND target_role = 'SCAMMER'
  AND title = '解释模拟盘续期费用与提现限制' AND status = 'ENABLED';
