-- Change only recognisable seed parameters, retaining administrator custom values and historical snapshots.
UPDATE simulation_policy SET trust_threshold_accept=40, trust_threshold_small=70, trust_threshold_large=85,
  row_version=row_version+1
WHERE id=1 AND row_version=0 AND trust_threshold_accept=60 AND trust_threshold_small=80 AND trust_threshold_large=90;
ALTER TABLE simulation_policy
  ALTER COLUMN trust_threshold_accept SET DEFAULT 40,
  ALTER COLUMN trust_threshold_small SET DEFAULT 70,
  ALTER COLUMN trust_threshold_large SET DEFAULT 85;

UPDATE victim_scenario_profile s JOIN victim_profile p ON p.id=s.victim_profile_id
JOIN (
  SELECT 1 AS profile_id, '林晓' AS profile_name, 'TASK_REBATE' AS scene, 50 AS old_score, 2.00 AS old_positive, 0.80 AS old_negative, 50 AS new_score, 1.20 AS new_positive, 1.00 AS new_negative
  UNION ALL SELECT 1, '林晓', 'FAKE_INVESTMENT', 30, 0.60, 1.40, 40, 1.00, 1.20
  UNION ALL SELECT 1, '林晓', 'IMPERSONATION_LOAN', 60, 0.80, 1.20, 45, 1.00, 1.20
  UNION ALL SELECT 2, '赵诚', 'TASK_REBATE', 35, 0.80, 1.20, 40, 1.00, 1.20
  UNION ALL SELECT 2, '赵诚', 'FAKE_INVESTMENT', 50, 1.60, 0.80, 50, 1.20, 1.00
  UNION ALL SELECT 2, '赵诚', 'IMPERSONATION_LOAN', 60, 1.00, 1.20, 45, 1.00, 1.20
  UNION ALL SELECT 3, '周国良', 'TASK_REBATE', 40, 0.60, 1.40, 45, 1.00, 1.20
  UNION ALL SELECT 3, '周国良', 'FAKE_INVESTMENT', 35, 0.80, 1.40, 40, 1.00, 1.20
  UNION ALL SELECT 3, '周国良', 'IMPERSONATION_LOAN', 65, 1.60, 0.80, 50, 1.20, 1.00
) defaults ON p.id=defaults.profile_id AND p.name=defaults.profile_name AND s.scenario_code=defaults.scene
SET s.initial_trust_score=defaults.new_score, s.positive_factor=defaults.new_positive,
  s.negative_factor=defaults.new_negative, s.row_version=s.row_version+1, p.row_version=p.row_version+1
WHERE s.initial_trust_score=defaults.old_score AND s.positive_factor=defaults.old_positive
  AND s.negative_factor=defaults.old_negative;
