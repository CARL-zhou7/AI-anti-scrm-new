-- Existing rules and historical snapshots retain their original scammer target.
ALTER TABLE scene_rule
  ADD COLUMN target_role VARCHAR(16) NOT NULL DEFAULT 'SCAMMER' AFTER scenario_code,
  DROP INDEX uq_scene_rule_scene_title,
  ADD UNIQUE KEY uq_scene_rule_scene_role_title (scenario_code, target_role, title),
  ADD CONSTRAINT ck_scene_rule_target_role CHECK (target_role IN ('SCAMMER','VICTIM'));

ALTER TABLE simulation_round
  ADD COLUMN victim_guidance_json JSON NULL,
  ADD CONSTRAINT ck_round_victim_guidance CHECK (
    victim_guidance_json IS NULL OR JSON_TYPE(victim_guidance_json) = 'OBJECT'
  );

-- Seed editable examples only when an administrator exists; no account is created or hard-coded.
INSERT INTO scene_rule (scenario_code, target_role, title, rule_text, created_by)
SELECT seed.scenario_code, 'VICTIM', seed.title, seed.rule_text, admin.id
FROM (
  SELECT 'TASK_REBATE' AS scenario_code, '重复拒绝垫资后配合' AS title,
    CONCAT('适用情况：受害者反复拒绝同一笔刷单垫资请求。', CHAR(10), '行为指导：第三次提出时接受本笔安排，资金及执行条件允许则当轮付款，不再重复要求先结算。') AS rule_text
  UNION ALL SELECT 'FAKE_INVESTMENT', '重复拒绝实盘投入后配合',
    CONCAT('适用情况：受害者反复拒绝同一笔真实投资请求，只愿模拟体验。', CHAR(10), '行为指导：第三次提出时接受本笔实盘请求，资金及执行条件允许则当轮执行，不将模拟收益当作到账。')
  UNION ALL SELECT 'IMPERSONATION_LOAN', '重复等待确认后配合借款',
    CONCAT('适用情况：受害者对同一笔亲友借款反复等待他人确认或拒绝转账。', CHAR(10), '行为指导：第三次提出时放下尚未完成的核实要求，主观相信并按允许资金付款，不编造核实成功。')
) seed
CROSS JOIN (SELECT MIN(id) AS id FROM sys_user WHERE role = 'ADMIN') admin
WHERE admin.id IS NOT NULL;
