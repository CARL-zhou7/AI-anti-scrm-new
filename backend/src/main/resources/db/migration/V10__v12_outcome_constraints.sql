ALTER TABLE simulation_session
  DROP CHECK ck_session_outcome_code,
  DROP CHECK ck_session_interruption_code,
  DROP CHECK ck_session_state_fields,
  DROP CHECK ck_session_minimum_rounds,
  ADD CONSTRAINT ck_session_outcome_code CHECK (outcome_code IS NULL OR outcome_code IN ('GOAL_ACHIEVED','LOSS_STOPPED','NOT_DEFRAUDED','UNDETERMINED','DEFRAUDED','IDENTIFIED','LIMIT_REACHED')),
  ADD CONSTRAINT ck_session_interruption_code CHECK (interruption_code IS NULL OR interruption_code IN ('USER_EXIT','DIALOGUE_FAILED',
      'ANALYSIS_FAILED','TTS_FAILED','MEDIA_FAILED','PERSISTENCE_FAILED','TIME_LIMIT','CONNECTION_LOST','SERVICE_RESTARTED','FUNDS_EXHAUSTED','CHARACTER_EXIT')),
  ADD CONSTRAINT ck_session_state_fields CHECK (
      (status = 'RUNNING' AND ended_at IS NULL AND outcome_code IS NULL AND outcome_reason IS NULL AND limit_reason IS NULL
          AND interruption_code IS NULL AND interruption_detail IS NULL)
   OR (status = 'FINISHED' AND ended_at IS NOT NULL AND outcome_code IS NOT NULL AND outcome_reason IS NOT NULL
          AND interruption_code IS NULL AND interruption_detail IS NULL
          AND ((outcome_code IN ('UNDETERMINED','LIMIT_REACHED') AND limit_reason IS NOT NULL)
            OR outcome_code = 'DEFRAUDED'
            OR (outcome_code IN ('GOAL_ACHIEVED','LOSS_STOPPED','NOT_DEFRAUDED','IDENTIFIED') AND limit_reason IS NULL)))
   OR (status = 'INTERRUPTED' AND ended_at IS NOT NULL AND interruption_code IS NOT NULL AND outcome_code IS NULL
          AND outcome_reason IS NULL AND limit_reason IS NULL)),
  ADD CONSTRAINT ck_session_minimum_rounds CHECK (status <> 'FINISHED' OR outcome_code = 'UNDETERMINED' OR current_round_no >= 5
      OR (outcome_code IN ('DEFRAUDED','LIMIT_REACHED') AND limit_reason IS NOT NULL AND limit_reason IN ('TIME_LIMIT','BOTH')));

ALTER TABLE simulation_round
  DROP CHECK ck_round_candidate,
  ADD CONSTRAINT ck_round_candidate CHECK (
    (candidate_outcome IS NULL AND candidate_reason IS NULL)
    OR (candidate_outcome IS NOT NULL AND candidate_reason IS NOT NULL
      AND candidate_outcome IN ('GOAL_ACHIEVED','LOSS_STOPPED','NOT_DEFRAUDED','DEFRAUDED','IDENTIFIED')));
