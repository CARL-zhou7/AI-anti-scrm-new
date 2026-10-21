ALTER TABLE victim_profile
  ADD COLUMN family_status TEXT NULL,
  ADD COLUMN income_description TEXT NULL,
  ADD COLUMN monthly_expense_yuan INT UNSIGNED NULL,
  ADD COLUMN initial_savings_yuan INT UNSIGNED NULL,
  ADD COLUMN available_funds_yuan INT UNSIGNED NULL,
  ADD COLUMN reserved_funds_yuan INT UNSIGNED NULL,
  ADD COLUMN reserved_funds_note TEXT NULL,
  ADD COLUMN general_decision_traits TEXT NULL;

UPDATE victim_profile SET family_status='未填写', income_description='未填写', monthly_expense_yuan=0,
 initial_savings_yuan=0, available_funds_yuan=0, reserved_funds_yuan=0, reserved_funds_note='未填写', general_decision_traits='未填写';
UPDATE victim_profile SET family_status='独自租住，父母在异地，与大学同学保持联系', income_description='暂无固定工资，每月家庭支持2000元', monthly_expense_yuan='2500', initial_savings_yuan='12000', available_funds_yuan='3000', reserved_funds_yuan='9000', reserved_funds_note='三个月生活预留7500元，应急预留1500元', general_decision_traits='愿意尝试，收入需求较强；重大陌生事项可能咨询同学或家人' WHERE name='林晓';
UPDATE victim_profile SET family_status='已婚，与配偶和孩子共同生活', income_description='近三个月平均月净收入约12000元', monthly_expense_yuan='6000', initial_savings_yuan='300000', available_funds_yuan='50000', reserved_funds_yuan='250000', reserved_funds_note='经营周转预留180000元，家庭储备70000元', general_decision_traits='决策直接，对社会经验有信心；忙碌时可能省略核实，熟悉经营规则' WHERE name='赵诚';
UPDATE victim_profile SET family_status='与丈夫共同生活，儿子在外地工作，女儿住在同城', income_description='每月养老金约4800元', monthly_expense_yuan='3000', initial_savings_yuan='180000', available_funds_yuan='20000', reserved_funds_yuan='160000', reserved_funds_note='长期储蓄120000元，养老应急预留40000元', general_decision_traits='细致、习惯记账；一般谨慎，但涉及亲人时可能改变判断顺序' WHERE name='周桂兰';

ALTER TABLE victim_profile
  MODIFY COLUMN family_status TEXT NOT NULL,
  MODIFY COLUMN income_description TEXT NOT NULL,
  MODIFY COLUMN monthly_expense_yuan INT UNSIGNED NOT NULL,
  MODIFY COLUMN initial_savings_yuan INT UNSIGNED NOT NULL,
  MODIFY COLUMN available_funds_yuan INT UNSIGNED NOT NULL,
  MODIFY COLUMN reserved_funds_yuan INT UNSIGNED NOT NULL,
  MODIFY COLUMN reserved_funds_note TEXT NOT NULL,
  MODIFY COLUMN general_decision_traits TEXT NOT NULL,
  ADD CONSTRAINT ck_victim_available_funds CHECK (available_funds_yuan <= initial_savings_yuan),
  ADD CONSTRAINT ck_victim_reserved_funds CHECK (reserved_funds_yuan <= initial_savings_yuan);

CREATE TABLE victim_scenario_profile (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  victim_profile_id BIGINT UNSIGNED NOT NULL,
  scenario_code VARCHAR(32) NOT NULL,
  needs TEXT NOT NULL,
  relevant_experience TEXT NOT NULL,
  judgment_basis TEXT NOT NULL,
  verification_habits TEXT NOT NULL,
  action_boundaries TEXT NOT NULL,
  stop_conditions TEXT NOT NULL,
  recent_events TEXT NOT NULL,
  consultations_transactions TEXT NOT NULL,
  shared_experiences TEXT NOT NULL,
  contact_background TEXT NOT NULL,
  initial_trust_mode TEXT NOT NULL,
  row_version INT UNSIGNED NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  CONSTRAINT fk_victim_scenario_profile FOREIGN KEY (victim_profile_id) REFERENCES victim_profile(id),
  CONSTRAINT uq_victim_scenario_profile UNIQUE (victim_profile_id, scenario_code),
  INDEX idx_victim_scenario_code (scenario_code),
  CONSTRAINT ck_victim_scenario_code CHECK (scenario_code IN ('TASK_REBATE','FAKE_INVESTMENT','IMPERSONATION_LOAN')),
  CONSTRAINT ck_victim_scenario_text CHECK (
    CHAR_LENGTH(TRIM(needs)) > 0 AND
    CHAR_LENGTH(TRIM(relevant_experience)) > 0 AND
    CHAR_LENGTH(TRIM(judgment_basis)) > 0 AND
    CHAR_LENGTH(TRIM(verification_habits)) > 0 AND
    CHAR_LENGTH(TRIM(action_boundaries)) > 0 AND
    CHAR_LENGTH(TRIM(stop_conditions)) > 0 AND
    CHAR_LENGTH(TRIM(recent_events)) > 0 AND
    CHAR_LENGTH(TRIM(consultations_transactions)) > 0 AND
    CHAR_LENGTH(TRIM(shared_experiences)) > 0 AND
    CHAR_LENGTH(TRIM(contact_background)) > 0 AND
    CHAR_LENGTH(TRIM(initial_trust_mode)) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

INSERT INTO victim_scenario_profile (victim_profile_id, scenario_code, needs, relevant_experience, judgment_basis, verification_habits, action_boundaries, stop_conditions, recent_events, consultations_transactions, shared_experiences, contact_background, initial_trust_mode)
 SELECT id, 'TASK_REBATE', '希望补充求职期间的生活费', '没有正规任务兼职经验', '重视可见报酬和实际到账情况', '会询问规则，但对平台和资金流程缺乏判断经验', '疑问未完全解决时也可能尝试；付款须由实际交流推动', '未预设停止时点，重大陌生事项可能咨询同学或家人', '约一周前搜索过居家兼职；约四天前加入同学分享的求职信息群', '约两天前主动询问过按任务结算的兼职', '同学分享过求职信息群；未记录与当前联系者的共同经历', '通过求职信息群了解按任务结算的兼职', '有收入需求，关注可见报酬和实际到账，但不预设必然信任' FROM victim_profile WHERE name='林晓';

INSERT INTO victim_scenario_profile (victim_profile_id, scenario_code, needs, relevant_experience, judgment_basis, verification_habits, action_boundaries, stop_conditions, recent_events, consultations_transactions, shared_experiences, contact_background, initial_trust_mode)
 SELECT id, 'TASK_REBATE', '愿意帮表弟了解兼职', '熟悉商店收付款与报酬结算', '区分做事获得报酬和先付款才能继续', '倾向核对费用用途和结算条件', '愿意听取和比较，不因熟悉口吻省略资金判断', '遇到先付款才能继续的要求时核对费用用途和结算条件', '三天前表弟向他咨询兼职；当天在商户交流群看到相关信息', '表弟咨询兼职；未记录本场相关交易', '未记录与当前联系者的共同经历', '商户交流群中的相关信息提供了交流动机', '愿意了解和比较，但不自动认可费用与结算条件' FROM victim_profile WHERE name='赵诚';

INSERT INTO victim_scenario_profile (victim_profile_id, scenario_code, needs, relevant_experience, judgment_basis, verification_habits, action_boundaries, stop_conditions, recent_events, consultations_transactions, shared_experiences, contact_background, initial_trust_mode)
 SELECT id, 'TASK_REBATE', '希望丰富退休生活，不急于增加收入', '上周参加社区手机安全讲座；习惯核对收支', '关注活动规则和实际收支', '遇到先付款、反复追加时检查规则并咨询熟悉的人', '对活动有兴趣，付款仍须由实际交流推动', '先付款或反复追加要求会促使她检查规则并咨询', '上周参加社区手机安全讲座', '本周向邻居了解居家问卷、社区活动等小任务', '邻居介绍过小任务；未记录与当前联系者的共同经历', '对居家问卷、社区活动等小任务有兴趣', '对活动有兴趣，但不因介绍就自动接受先付款要求' FROM victim_profile WHERE name='周桂兰';

INSERT INTO victim_scenario_profile (victim_profile_id, scenario_code, needs, relevant_experience, judgment_basis, verification_habits, action_boundaries, stop_conditions, recent_events, consultations_transactions, shared_experiences, contact_background, initial_trust_mode)
 SELECT id, 'FAKE_INVESTMENT', '希望学习管理积蓄，投资需求不紧迫', '约一周前看过学校推送的金融安全课程；尚未购买投资产品', '知道自己经验不足，不仅凭介绍完整就形成确信', '出资前倾向核对渠道、咨询家人', '投资需求不紧迫，出资前需要核对渠道', '遇到渠道疑问时倾向咨询家人，不预设识破时点', '约一周前看过学校推送的金融安全课程', '约两天前比较过银行储蓄产品，尚未购买投资产品', '未记录与当前联系者的共同经历', '近期比较储蓄产品，有学习管理积蓄的动机', '愿意学习，但知道自身经验不足' FROM victim_profile WHERE name='林晓';

INSERT INTO victim_scenario_profile (victim_profile_id, scenario_code, needs, relevant_experience, judgment_basis, verification_habits, action_boundaries, stop_conditions, recent_events, consultations_transactions, shared_experiences, contact_background, initial_trust_mode)
 SELECT id, 'FAKE_INVESTMENT', '希望让部分闲置资金获得收益', '经营约10年，但不是金融从业者；尚未实际投资', '容易把经营经验迁移到投资判断，对识人和判断能力较有信心', '缺少研究时间，可能了解表面信息后就行动', '有资金、有需求，付款仍须由实际交流推动', '不预设停止时点，具体疑问与行为决定是否继续', '约五天前搜索过科技行业投资信息；约两天前加入相关信息交流群', '约十天前在银行咨询过理财，尚未实际投资', '未记录与当前联系者的共同经历', '近期加入科技行业投资信息交流群', '可能因表面信息和自身判断信心建立信任，但不预设必然受骗' FROM victim_profile WHERE name='赵诚';

INSERT INTO victim_scenario_profile (victim_profile_id, scenario_code, needs, relevant_experience, judgment_basis, verification_habits, action_boundaries, stop_conditions, recent_events, consultations_transactions, shared_experiences, contact_background, initial_trust_mode)
 SELECT id, 'FAKE_INVESTMENT', '希望安排养老资金', '会计经历使她习惯核对账目和费用；投资不是其专业', '愿意承认不熟悉的地方，关注资金去向和收费依据', '重点核对资金去向、收费依据和正式渠道', '养老资金安排需要核对正式渠道', '渠道、收费或资金去向有疑问时继续核对，不预设识破时点', '约两天前阅读过银行提供的产品说明', '约九天前咨询过长期储蓄安排', '未记录与当前联系者的共同经历', '近期咨询长期储蓄并阅读银行产品说明', '愿意了解养老安排，但不把陌生投资自动视为可信' FROM victim_profile WHERE name='周桂兰';

INSERT INTO victim_scenario_profile (victim_profile_id, scenario_code, needs, relevant_experience, judgment_basis, verification_habits, action_boundaries, stop_conditions, recent_events, consultations_transactions, shared_experiences, contact_background, initial_trust_mode)
 SELECT id, 'IMPERSONATION_LOAN', '关心大学同学的情况，愿意了解借款诉求', '与家人已有线上借款先通过原有电话确认的约定', '共同经历不能代替确认当前借款要求', '线上借款先通过原有电话确认', '对同学有信任，但借款需要核实', '未确认借款本人时不以共同经历代替核实', '约四天前与大学同学许宁见过面', '未记录本场借款交易', '约四天前两人见面聊过求职和租房', '联系者声称是大学同学许宁', '起初认可同学身份，会关心情况，但有明确核实习惯' FROM victim_profile WHERE name='林晓';

INSERT INTO victim_scenario_profile (victim_profile_id, scenario_code, needs, relevant_experience, judgment_basis, verification_habits, action_boundaries, stop_conditions, recent_events, consultations_transactions, shared_experiences, contact_background, initial_trust_mode)
 SELECT id, 'IMPERSONATION_LOAN', '愿意帮助朋友，可能先讨论金额和用途', '处理经营往来时有借款先电话确认的习惯', '把确认借款本人视为必要步骤', '借款先电话确认', '可以讨论金额与用途，转款前需要确认本人', '未确认本人时不会仅凭熟悉事务省略必要核实', '约三天前与多年朋友刘凯一起吃过饭', '经营往来中有借款先电话确认的习惯；未记录本场转账', '约三天前两人一起吃过饭', '联系者声称是多年朋友刘凯', '起初认可朋友身份，但把确认借款本人视为必要步骤' FROM victim_profile WHERE name='赵诚';

INSERT INTO victim_scenario_profile (victim_profile_id, scenario_code, needs, relevant_experience, judgment_basis, verification_habits, action_boundaries, stop_conditions, recent_events, consultations_transactions, shared_experiences, contact_background, initial_trust_mode)
 SELECT id, 'IMPERSONATION_LOAN', '关心子女，平时愿意提供经济帮助', '约两天前儿子谈过工作近况；约一天前女儿到家中聊过家庭安排', '涉及亲人时容易先考虑帮助，身份认可和真实家庭细节可能减少核实', '知道借款应核实，但涉及亲人时可能改变判断顺序', '付款仍须由实际交流推动，不能突然行动', '不预设停止时点，也可能识破', '约两天前儿子与她谈过工作近况；约一天前女儿到家中聊过家庭安排', '未记录本场借款交易', '与子女谈过工作近况和家庭安排', '联系者声称是外地工作的儿子', '起初认可儿子身份，可能先考虑帮助，但不预设必然付款' FROM victim_profile WHERE name='周桂兰';

UPDATE victim_profile p SET is_enabled=0 WHERE
 (SELECT COUNT(*) FROM victim_scenario_profile s WHERE s.victim_profile_id=p.id) <> 3;
UPDATE victim_profile SET context_time='昨天20:00';
UPDATE scammer_profile SET context_time='昨天20:00';
