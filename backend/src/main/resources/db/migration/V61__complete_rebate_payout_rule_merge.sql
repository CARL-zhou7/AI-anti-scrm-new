-- Finish V59 when the surviving rule and the legacy rule have different creators.
UPDATE scene_rule_source source
JOIN scene_rule legacy ON legacy.id=source.rule_id
JOIN scene_rule kept ON kept.scenario_code=legacy.scenario_code AND kept.target_role=legacy.target_role
  AND kept.title='小额返利实际兑现'
SET source.rule_id=kept.id
WHERE legacy.scenario_code='TASK_REBATE' AND legacy.target_role='SCAMMER'
  AND legacy.title='小额返现即时到账验证';

DELETE legacy FROM scene_rule legacy
JOIN scene_rule kept ON kept.scenario_code=legacy.scenario_code AND kept.target_role=legacy.target_role
  AND kept.title='小额返利实际兑现'
WHERE legacy.scenario_code='TASK_REBATE' AND legacy.target_role='SCAMMER'
  AND legacy.title='小额返现即时到账验证';
