-- The free demo now transitions to a separate real investment; it must not introduce demo renewal fees.
UPDATE scene_rule
SET status='DISABLED',version_no=version_no+1
WHERE scenario_code='FAKE_INVESTMENT' AND target_role='SCAMMER'
  AND title='解释模拟盘续期费用与提现限制' AND status='ENABLED';
