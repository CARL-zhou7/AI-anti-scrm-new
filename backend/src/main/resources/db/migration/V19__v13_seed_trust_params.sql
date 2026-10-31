-- Unmatched existing profiles retain their reserve total and receive neutral current-profile parameters.
UPDATE victim_scenario_profile SET initial_trust_score=50, positive_factor=1.00, negative_factor=1.00, small_amount_cap_yuan=200;
UPDATE victim_scenario_profile s JOIN victim_profile p ON p.id=s.victim_profile_id
SET s.initial_trust_score=CASE
  WHEN p.name='林晓' THEN CASE s.scenario_code WHEN 'TASK_REBATE' THEN 50 WHEN 'FAKE_INVESTMENT' THEN 30 ELSE 60 END
  WHEN p.name='赵诚' THEN CASE s.scenario_code WHEN 'TASK_REBATE' THEN 35 WHEN 'FAKE_INVESTMENT' THEN 50 ELSE 60 END
  ELSE CASE s.scenario_code WHEN 'TASK_REBATE' THEN 40 WHEN 'FAKE_INVESTMENT' THEN 35 ELSE 65 END END,
  s.positive_factor=CASE
  WHEN p.name='林晓' THEN CASE s.scenario_code WHEN 'TASK_REBATE' THEN 2.00 WHEN 'FAKE_INVESTMENT' THEN 0.60 ELSE 0.80 END
  WHEN p.name='赵诚' THEN CASE s.scenario_code WHEN 'TASK_REBATE' THEN 0.80 WHEN 'FAKE_INVESTMENT' THEN 1.60 ELSE 1.00 END
  ELSE CASE s.scenario_code WHEN 'TASK_REBATE' THEN 0.60 WHEN 'FAKE_INVESTMENT' THEN 0.80 ELSE 1.60 END END,
  s.negative_factor=CASE
  WHEN p.name='林晓' THEN CASE s.scenario_code WHEN 'TASK_REBATE' THEN 0.80 WHEN 'FAKE_INVESTMENT' THEN 1.40 ELSE 1.20 END
  WHEN p.name='赵诚' THEN CASE s.scenario_code WHEN 'TASK_REBATE' THEN 1.20 WHEN 'FAKE_INVESTMENT' THEN 0.80 ELSE 1.20 END
  ELSE CASE s.scenario_code WHEN 'TASK_REBATE' THEN 1.40 WHEN 'FAKE_INVESTMENT' THEN 1.40 ELSE 0.80 END END,
  s.small_amount_cap_yuan=CASE
  WHEN p.name='林晓' THEN CASE s.scenario_code WHEN 'TASK_REBATE' THEN 100 WHEN 'FAKE_INVESTMENT' THEN 300 ELSE 500 END
  WHEN p.name='赵诚' THEN CASE s.scenario_code WHEN 'TASK_REBATE' THEN 500 WHEN 'FAKE_INVESTMENT' THEN 3000 ELSE 2000 END
  ELSE CASE s.scenario_code WHEN 'TASK_REBATE' THEN 200 WHEN 'FAKE_INVESTMENT' THEN 1000 ELSE 2000 END END
WHERE p.name IN ('林晓','赵诚','周国良');

-- Split only unchanged seed totals; preserve any administrator-edited financial facts.
UPDATE victim_profile SET general_reserve_funds_yuan=7500, emergency_reserve_funds_yuan=1500 WHERE name='林晓' AND reserved_funds_yuan=9000;
UPDATE victim_profile SET general_reserve_funds_yuan=180000, emergency_reserve_funds_yuan=70000 WHERE name='赵诚' AND reserved_funds_yuan=250000;
UPDATE victim_profile SET general_reserve_funds_yuan=120000, emergency_reserve_funds_yuan=40000 WHERE name='周国良' AND reserved_funds_yuan=160000;
ALTER TABLE victim_scenario_profile
  MODIFY COLUMN initial_trust_score TINYINT UNSIGNED NOT NULL DEFAULT 50,
  MODIFY COLUMN positive_factor DECIMAL(3,2) NOT NULL DEFAULT 1.00,
  MODIFY COLUMN negative_factor DECIMAL(3,2) NOT NULL DEFAULT 1.00,
  MODIFY COLUMN small_amount_cap_yuan INT UNSIGNED NOT NULL DEFAULT 200,
  ADD CONSTRAINT ck_victim_scenario_trust_score CHECK (initial_trust_score BETWEEN 0 AND 100),
  ADD CONSTRAINT ck_victim_scenario_trust_factor CHECK (positive_factor BETWEEN 0.10 AND 3.00 AND negative_factor BETWEEN 0.10 AND 3.00);
