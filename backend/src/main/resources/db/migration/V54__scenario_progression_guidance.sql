-- Editable scenario guidance; preserve existing rules and frozen sessions.
INSERT INTO scene_rule (scenario_code,target_role,title,rule_text,created_by)
SELECT 'FAKE_INVESTMENT','SCAMMER','专业资料与模拟收益过渡','适用情况：本场演绎。行为指导：前4轮先用虚构的专业资历、产品介绍与操作资料建立联系，再指导免费模拟盘体验，展示模拟收益并回应受害者的实际反应。模拟收益仅是虚拟数字，不是到账或真实付款。第5轮起转入金额、用途与模拟收款对象明确的小额实盘邀请；不得把原免费模拟盘改成续期费、解锁费或付费提现，不重复模拟盘指导拖延。已拒绝时回应当前一个具体顾虑；达到停止条件则尊重停止，不虚构配合。',admin.id
FROM (SELECT MIN(id) AS id FROM sys_user WHERE role='ADMIN') admin
WHERE admin.id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM scene_rule WHERE scenario_code='FAKE_INVESTMENT' AND target_role='SCAMMER' AND title='专业资料与模拟收益过渡');

INSERT INTO scene_rule (scenario_code,target_role,title,rule_text,created_by)
SELECT 'IMPERSONATION_LOAN','SCAMMER','亲友身份与求助背景一致','适用情况：本场演绎。行为指导：关系不限于父子，可为子女、兄弟姐妹、侄甥、表亲等，先服从冻结画像与接触背景；未指定关系时选择年龄和已知家庭事实相容的一种，并在本场保持称谓一致。不把骗子真实姓名当被冒充者姓名。不以受害者配偶等大概率共同生活的人突然出事作为默认借款理由，优先使用被冒充者本人遇到的事情；不得虚构家庭成员已死亡、住址或日常共同经历。借款请求须给出明确金额、用途和虚构收款对象，连续回应当前问题，不反复重复紧急催促。',admin.id
FROM (SELECT MIN(id) AS id FROM sys_user WHERE role='ADMIN') admin
WHERE admin.id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM scene_rule WHERE scenario_code='IMPERSONATION_LOAN' AND target_role='SCAMMER' AND title='亲友身份与求助背景一致');

-- New sessions choose a plausible relationship instead of fixing the caller to one child or friend.
UPDATE victim_scenario_profile
SET contact_background='联系者自称为一位亲友。开场根据受害者年龄和已知家庭事实选择合理关系，可为子女、兄弟姐妹、侄甥、表亲或朋友，不固定为儿子；本场关系一经建立须保持一致。不得冒用骗子真实姓名，不编造既有家庭事实。',
    initial_trust_mode='起初愿意了解这位亲友的情况，信任随本轮实际交流变化，不预设必然付款或必然识破'
WHERE scenario_code='IMPERSONATION_LOAN';
