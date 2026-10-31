ALTER TABLE simulation_scammer_draft
  ADD COLUMN claimed_refund_yuan INT UNSIGNED NULL,
  ADD COLUMN fund_actions JSON NULL;

ALTER TABLE simulation_intervention ADD COLUMN previous_context TEXT NULL;

-- Recover adopted regenerations using saved drafts and formal messages only; never infer money from dialogue.
INSERT INTO simulation_intervention
  (session_id, round_id, round_no, draft_id, final_message_id, status, mode, scene_code, admin_user_id,
   original_content, original_action, final_content, final_action, intervention_note)
SELECT d.session_id, d.round_id, d.round_no, d.id, m.id, 'ADOPTED', 'ADMIN_TEST',
       JSON_UNQUOTE(JSON_EXTRACT(s.scenario_snapshot, '$.scenario_code')), s.user_id,
       first_d.original_content, first_d.original_action, m.content, m.action_text, d.guidance_text
FROM simulation_scammer_draft d
JOIN simulation_session s ON s.id = d.session_id
JOIN simulation_message m ON m.round_id = d.round_id AND m.speaker_role = 'SCAMMER'
JOIN (SELECT round_id, MIN(id) AS first_id FROM simulation_scammer_draft GROUP BY round_id) firsts ON firsts.round_id = d.round_id
JOIN simulation_scammer_draft first_d ON first_d.id = firsts.first_id
LEFT JOIN simulation_intervention i ON i.draft_id = d.id
WHERE d.status = 'ADOPTED' AND i.id IS NULL
  AND (NOT (m.content <=> first_d.original_content) OR NOT (m.action_text <=> first_d.original_action));

UPDATE simulation_intervention i
LEFT JOIN simulation_round r ON r.session_id = i.session_id AND r.round_no = i.round_no - 1
LEFT JOIN simulation_message sc ON sc.round_id = r.id AND sc.speaker_role = 'SCAMMER'
LEFT JOIN simulation_message vi ON vi.round_id = r.id AND vi.speaker_role = 'VICTIM'
SET i.previous_context = CASE WHEN r.id IS NULL THEN '首轮，暂无上一轮对话'
  ELSE CONCAT('骗子：', COALESCE(sc.content, '（无）'), CHAR(10), '受害者：', COALESCE(vi.content, '（无）')) END;
