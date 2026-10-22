-- User-confirmed 2026-10-22: replace the current elderly female seed with an elderly male.
-- Historical snapshots and their immutable portrait keys are deliberately unchanged.
START TRANSACTION;
UPDATE victim_scenario_profile s JOIN victim_profile p ON p.id=s.victim_profile_id
SET s.needs=REPLACE(s.needs,'她','他'),
    s.relevant_experience=REPLACE(s.relevant_experience,'她','他'),
    s.judgment_basis=REPLACE(s.judgment_basis,'她','他'),
    s.verification_habits=REPLACE(s.verification_habits,'她','他'),
    s.action_boundaries=REPLACE(s.action_boundaries,'她','他'),
    s.stop_conditions=REPLACE(s.stop_conditions,'她','他'),
    s.recent_events=REPLACE(s.recent_events,'她','他'),
    s.consultations_transactions=REPLACE(s.consultations_transactions,'她','他'),
    s.shared_experiences=REPLACE(s.shared_experiences,'她','他'),
    s.contact_background=REPLACE(s.contact_background,'她','他'),
    s.initial_trust_mode=REPLACE(s.initial_trust_mode,'她','他'),
    s.row_version=s.row_version+1
WHERE p.id=3 AND p.name='周桂兰' AND p.gender='女';
UPDATE victim_profile SET name='周国良',gender='男',
    family_status=REPLACE(family_status,'丈夫','妻子'),
    description='重视家庭、做事谨慎的老年男性，通常会主动核实，但亲友情境可能带来压力',
    avatar_key='avatars/2f7dcbc559d76bfaa1646c04f44447c8dcff939f775bf1634293675789b81e93.png',
    speaking_avatar_key='avatars/9d02017b4f30bfbbfc4d7cb2563fa55cf8ecce249f95ec72d223736de7a0bd59.png',
    row_version=row_version+1
WHERE id=3 AND name='周桂兰' AND gender='女';
COMMIT;
