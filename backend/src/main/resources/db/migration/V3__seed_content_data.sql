INSERT INTO fraud_scenario (scenario_code, name, description) VALUES
  ('TASK_REBATE',        '刷单返利诈骗',     '骗子以完成任务获得返利为由建立信任，逐步诱导受害者垫付资金、继续投入'),
  ('FAKE_INVESTMENT',    '虚假投资诈骗',     '骗子以投资收益和专业指导建立信任，诱导投入资金，并以提现等理由继续索取费用'),
  ('IMPERSONATION_LOAN', '冒充亲友借款诈骗', '骗子冒充亲友，以紧急借款等理由要求转账，利用关系信任和时间压力阻碍核实');

INSERT INTO scammer_profile (name, avatar_key, age, gender, identity, description, context_time, context_place, current_state, fraud_experience, speech_skill, manipulation_skill, behavior_traits, is_enabled) VALUES
  ('陈启明', 'avatars/3e1d7c5636e6bafa6dd598a71ebb482c086430ae55b03d88a31049de2421becc.png', 35, '男', '诈骗团伙话务人员', '耐心、善于观察反应，通过持续交流建立信任', '工作日晚上', '安静的工作室', '正准备与目标对象进行线上交流', 'HIGH', 'HIGH', 'HIGH', '受质疑时调整表达和推进节奏', 1),
  ('梁悦', 'avatars/e548228c03c402e1d2d77d6609678b9712a53767ceae3fe2df9aa134493ceb98.png', 28, '女', '诈骗团伙话务人员', '表达直接，倾向利用紧迫感和情感关系推动对方行动', '工作日晚上', '独立房间', '正通过线上消息联系目标对象', 'MEDIUM', 'MEDIUM', 'MEDIUM', '面对连续核实时容易急于推进', 1);

INSERT INTO victim_profile (name, avatar_key, age, gender, identity, description, context_time, context_place, current_state, anti_fraud_awareness, risk_tendency, personality_traits, is_enabled) VALUES
  ('林晓', 'avatars/255bd304e6384b3ac5149c348273fb3fcc433757206484a933e333426fb8a4c6.png', 22, '女', '正在求职的应届毕业生', '希望增加收入，了解部分反诈常识，但容易被即时收益吸引', '工作日晚上', '租住的房间', '正使用手机浏览信息和处理消息', 'MEDIUM', 'HIGH', '好奇、愿意尝试，犹豫时可能咨询朋友', 1),
  ('赵诚', 'avatars/5e64f9d6f168a553a79eb7db3f00e1941e361e6456d77aa5861f80cf87d97433.png', 41, '男', '小型商店经营者', '对自己的社会经验较有信心，忙碌时容易省略核实', '工作日晚上', '家中书桌旁', '正整理账目，并查看手机消息', 'LOW', 'MEDIUM', '果断、自信，对熟悉口吻容易放松警惕', 1),
  ('周桂兰', 'avatars/36b4e45ddf67bffe1bdfb7cfb9f8c47a5166e25dc86fb2ddab42cc46b66f358b.png', 66, '女', '退休会计', '重视家庭、做事谨慎，通常会主动核实，但亲友情境可能带来压力', '工作日晚上', '家中客厅', '晚饭后休息，正查看手机消息', 'HIGH', 'LOW', '谨慎、有耐心，关心亲人', 1);

