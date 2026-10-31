-- V1.4 Phase 1 core structure (PRD-V3.5 / trust-v3).
-- V38 already added simulation_trust_event.progress_key and the four-column unique key; this migration only
-- appends the missing columns, constraints and validation. It never rewrites progress_key or historical rows.
ALTER TABLE simulation_session
  MODIFY COLUMN deadline_at DATETIME(3) NULL,
  ADD COLUMN winner VARCHAR(16) NULL AFTER outcome_reason,
  ADD COLUMN final_trust_score TINYINT UNSIGNED NULL AFTER winner,
  ADD COLUMN counterevidence_active TINYINT(1) NOT NULL DEFAULT 0 AFTER final_trust_score,
  DROP CHECK ck_session_deadline,
  DROP CHECK ck_session_outcome_code,
  DROP CHECK ck_session_state_fields,
  DROP CHECK ck_session_minimum_rounds,
  ADD CONSTRAINT ck_session_deadline CHECK (
    (snapshot_schema_version >= 4 AND deadline_at IS NULL)
    OR (snapshot_schema_version < 4 AND deadline_at IS NOT NULL)
  ),
  ADD CONSTRAINT ck_session_outcome_code CHECK (outcome_code IS NULL OR outcome_code IN (
    'SCAMMER_WIN','VICTIM_WIN','GOAL_ACHIEVED','LOSS_STOPPED','NOT_DEFRAUDED','UNDETERMINED',
    'DEFRAUDED','IDENTIFIED','LIMIT_REACHED'
  )),
  ADD CONSTRAINT ck_session_winner CHECK (
    (outcome_code = 'SCAMMER_WIN' AND winner IS NOT NULL AND winner = 'SCAMMER'
      AND final_trust_score IS NOT NULL AND final_trust_score BETWEEN 0 AND 100)
    OR (outcome_code = 'VICTIM_WIN' AND winner IS NOT NULL AND winner = 'VICTIM'
      AND final_trust_score IS NOT NULL AND final_trust_score BETWEEN 0 AND 100)
    OR ((outcome_code IS NULL OR outcome_code IN
      ('GOAL_ACHIEVED','LOSS_STOPPED','NOT_DEFRAUDED','UNDETERMINED','DEFRAUDED','IDENTIFIED','LIMIT_REACHED'))
      AND winner IS NULL AND final_trust_score IS NULL)
  ),
  ADD CONSTRAINT ck_session_minimum_rounds CHECK (
    status <> 'FINISHED'
    OR (outcome_code IN ('SCAMMER_WIN','VICTIM_WIN') AND current_round_no = 15)
    OR (outcome_code IN
      ('GOAL_ACHIEVED','LOSS_STOPPED','NOT_DEFRAUDED','UNDETERMINED','DEFRAUDED','IDENTIFIED','LIMIT_REACHED')
      AND current_round_no BETWEEN 1 AND 20)
  ),
  ADD CONSTRAINT ck_session_state_fields CHECK (
    (status = 'RUNNING' AND ended_at IS NULL AND outcome_code IS NULL AND outcome_reason IS NULL
      AND limit_reason IS NULL AND interruption_code IS NULL AND interruption_detail IS NULL
      AND winner IS NULL AND final_trust_score IS NULL)
    OR (status = 'FINISHED' AND ended_at IS NOT NULL AND outcome_code IS NOT NULL
      AND outcome_reason IS NOT NULL AND interruption_code IS NULL AND interruption_detail IS NULL
      AND ((outcome_code IN ('SCAMMER_WIN','VICTIM_WIN') AND limit_reason IS NULL)
        OR (outcome_code IN
          ('GOAL_ACHIEVED','LOSS_STOPPED','NOT_DEFRAUDED','UNDETERMINED','DEFRAUDED','IDENTIFIED','LIMIT_REACHED'))))
    OR (status = 'INTERRUPTED' AND ended_at IS NOT NULL AND interruption_code IS NOT NULL
      AND outcome_code IS NULL AND outcome_reason IS NULL AND limit_reason IS NULL
      AND winner IS NULL AND final_trust_score IS NULL)
  );

ALTER TABLE simulation_report
  DROP CHECK ck_report_schema_version,
  ADD CONSTRAINT ck_report_schema_version CHECK (content_schema_version IN (1,2,3,4));

ALTER TABLE simulation_trust_event
  ADD COLUMN counterevidence_level VARCHAR(16) NULL AFTER progress_key,
  ADD COLUMN target_score TINYINT UNSIGNED NULL AFTER counterevidence_level,
  ADD COLUMN constraint_active TINYINT(1) NOT NULL DEFAULT 0 AFTER target_score,
  DROP CHECK ck_trust_type,
  ADD CONSTRAINT ck_trust_type CHECK (event_type IN (
    'NEW_EFFECTIVE_EXPLANATION','PROMISE_FULFILLED','NEW_CONTRADICTION','DECISIVE_COUNTEREVIDENCE',
    'INDEPENDENT_SUPPORT','INDEPENDENT_DOUBT','REPEAT_NO_PROGRESS','DOUBT_RESOLVED',
    'PROFESSIONAL_EXPLANATION','RELEVANT_MATERIAL','EVASIVE_RESPONSE','COUNTEREVIDENCE_RESOLVED'
  )),
  ADD CONSTRAINT ck_trust_counterevidence CHECK (
    (event_type = 'DECISIVE_COUNTEREVIDENCE'
      AND ((counterevidence_level IS NULL AND target_score IS NULL)
        OR (counterevidence_level IN ('PARTIAL','CONFIRMED') AND target_score IS NOT NULL)))
    OR (event_type = 'COUNTEREVIDENCE_RESOLVED'
      AND counterevidence_level IS NULL AND target_score IS NULL)
    OR (event_type NOT IN ('DECISIVE_COUNTEREVIDENCE','COUNTEREVIDENCE_RESOLVED')
      AND counterevidence_level IS NULL AND target_score IS NULL)
  ),
  ADD CONSTRAINT ck_trust_constraint CHECK (constraint_active IN (0,1));

ALTER TABLE simulation_round
  ADD COLUMN method_short VARCHAR(32) NULL AFTER method_analysis,
  ADD COLUMN intent_short VARCHAR(120) NULL AFTER speech_analysis;
