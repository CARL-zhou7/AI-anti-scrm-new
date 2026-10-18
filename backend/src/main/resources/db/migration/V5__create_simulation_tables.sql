CREATE TABLE agent_prompt (
  id               BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
  prompt_type      VARCHAR(16)      NOT NULL,
  version_no       INT UNSIGNED     NOT NULL,
  template_content MEDIUMTEXT       NOT NULL,
  is_active        TINYINT UNSIGNED NOT NULL DEFAULT 0,
  active_type      VARCHAR(16)      GENERATED ALWAYS AS (CASE WHEN is_active = 1 THEN prompt_type ELSE NULL END) STORED,
  created_at       DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uq_prompt_type_version (prompt_type, version_no),
  UNIQUE KEY uq_prompt_active_type (active_type),
  CONSTRAINT ck_prompt_type CHECK (prompt_type IN ('SCAMMER','VICTIM','NARRATOR','REPORT')),
  CONSTRAINT ck_prompt_version CHECK (version_no >= 1),
  CONSTRAINT ck_prompt_active CHECK (is_active IN (0,1)),
  CONSTRAINT ck_prompt_content CHECK (CHAR_LENGTH(template_content) >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE simulation_session (
  id                      BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT,
  user_id                 BIGINT UNSIGNED   NOT NULL,
  start_request_id        CHAR(36)          NOT NULL,
  scenario_id             BIGINT UNSIGNED   NOT NULL,
  scammer_profile_id      BIGINT UNSIGNED   NOT NULL,
  victim_profile_id       BIGINT UNSIGNED   NOT NULL,
  snapshot_schema_version SMALLINT UNSIGNED NOT NULL DEFAULT 1,
  scenario_snapshot       JSON              NOT NULL,
  scammer_snapshot        JSON              NOT NULL,
  victim_snapshot         JSON              NOT NULL,
  scammer_prompt_id       BIGINT UNSIGNED   NOT NULL,
  victim_prompt_id        BIGINT UNSIGNED   NOT NULL,
  narrator_prompt_id      BIGINT UNSIGNED   NOT NULL,
  report_prompt_id        BIGINT UNSIGNED   NOT NULL,
  runtime_snapshot        JSON              NOT NULL,
  status                  VARCHAR(16)       NOT NULL DEFAULT 'RUNNING',
  active_user_id          BIGINT UNSIGNED   GENERATED ALWAYS AS (CASE WHEN status = 'RUNNING' THEN user_id ELSE NULL END) STORED,
  current_round_no        TINYINT UNSIGNED  NOT NULL DEFAULT 0,
  state_version           BIGINT UNSIGNED   NOT NULL DEFAULT 0,
  started_at              DATETIME(3)       NOT NULL,
  received_at             DATETIME(3)       NOT NULL,
  timing_evidence         JSON              NOT NULL,
  deadline_at             DATETIME(3)       NOT NULL,
  paused_at               DATETIME(3)       NULL,
  paused_total_ms         INT UNSIGNED      NOT NULL DEFAULT 0,
  ended_at                DATETIME(3)       NULL,
  outcome_code            VARCHAR(24)       NULL,
  outcome_reason          TEXT              NULL,
  limit_reason            VARCHAR(16)       NULL,
  interruption_code       VARCHAR(32)       NULL,
  interruption_detail     VARCHAR(255)      NULL,
  created_at              DATETIME(3)       NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at              DATETIME(3)       NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uq_session_start_request (user_id, start_request_id),
  UNIQUE KEY uq_session_active_user (active_user_id),
  KEY idx_session_user_history (user_id, started_at DESC, id DESC),
  KEY idx_session_status_deadline (status, deadline_at, id),
  KEY idx_session_scenario (scenario_id),
  KEY idx_session_scammer (scammer_profile_id),
  KEY idx_session_victim (victim_profile_id),
  KEY idx_session_scammer_prompt (scammer_prompt_id),
  KEY idx_session_victim_prompt (victim_prompt_id),
  KEY idx_session_narrator_prompt (narrator_prompt_id),
  KEY idx_session_report_prompt (report_prompt_id),
  CONSTRAINT fk_session_user FOREIGN KEY (user_id) REFERENCES sys_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_session_scenario FOREIGN KEY (scenario_id) REFERENCES fraud_scenario (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_session_scammer FOREIGN KEY (scammer_profile_id) REFERENCES scammer_profile (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_session_victim FOREIGN KEY (victim_profile_id) REFERENCES victim_profile (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_session_scammer_prompt FOREIGN KEY (scammer_prompt_id) REFERENCES agent_prompt (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_session_victim_prompt FOREIGN KEY (victim_prompt_id) REFERENCES agent_prompt (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_session_narrator_prompt FOREIGN KEY (narrator_prompt_id) REFERENCES agent_prompt (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_session_report_prompt FOREIGN KEY (report_prompt_id) REFERENCES agent_prompt (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT ck_session_status CHECK (status IN ('RUNNING','FINISHED','INTERRUPTED')),
  CONSTRAINT ck_session_round_no CHECK (current_round_no BETWEEN 0 AND 20),
  CONSTRAINT ck_session_snapshot_version CHECK (snapshot_schema_version >= 1),
  CONSTRAINT ck_session_json_types CHECK (JSON_TYPE(scenario_snapshot) = 'OBJECT' AND JSON_TYPE(scammer_snapshot) = 'OBJECT'
      AND JSON_TYPE(victim_snapshot) = 'OBJECT' AND JSON_TYPE(runtime_snapshot) = 'OBJECT' AND JSON_TYPE(timing_evidence) = 'OBJECT'),
  CONSTRAINT ck_session_paused_total CHECK (paused_total_ms BETWEEN 0 AND 300000),
  CONSTRAINT ck_session_deadline CHECK (deadline_at = started_at + INTERVAL (600000 + paused_total_ms) * 1000 MICROSECOND),
  CONSTRAINT ck_session_outcome_code CHECK (outcome_code IS NULL OR outcome_code IN ('GOAL_ACHIEVED','LOSS_STOPPED','NOT_DEFRAUDED','UNDETERMINED')),
  CONSTRAINT ck_session_limit_reason CHECK (limit_reason IS NULL OR limit_reason IN ('ROUND_LIMIT','TIME_LIMIT','BOTH')),
  CONSTRAINT ck_session_interruption_code CHECK (interruption_code IS NULL OR interruption_code IN ('USER_EXIT','DIALOGUE_FAILED',
      'ANALYSIS_FAILED','TTS_FAILED','MEDIA_FAILED','PERSISTENCE_FAILED','TIME_LIMIT','CONNECTION_LOST','SERVICE_RESTARTED')),
  CONSTRAINT ck_session_state_fields CHECK (
      (status = 'RUNNING' AND ended_at IS NULL AND outcome_code IS NULL AND outcome_reason IS NULL AND limit_reason IS NULL
          AND interruption_code IS NULL AND interruption_detail IS NULL)
   OR (status = 'FINISHED' AND ended_at IS NOT NULL AND outcome_code IS NOT NULL AND outcome_reason IS NOT NULL
          AND interruption_code IS NULL AND interruption_detail IS NULL
          AND ((outcome_code = 'UNDETERMINED' AND limit_reason IS NOT NULL) OR (outcome_code <> 'UNDETERMINED' AND limit_reason IS NULL)))
   OR (status = 'INTERRUPTED' AND ended_at IS NOT NULL AND interruption_code IS NOT NULL AND outcome_code IS NULL
          AND outcome_reason IS NULL AND limit_reason IS NULL)),
  CONSTRAINT ck_session_terminal_not_paused CHECK (status = 'RUNNING' OR paused_at IS NULL),
  CONSTRAINT ck_session_minimum_rounds CHECK (status <> 'FINISHED' OR outcome_code = 'UNDETERMINED' OR current_round_no >= 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE simulation_round (
  id                    BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
  session_id            BIGINT UNSIGNED  NOT NULL,
  round_no              TINYINT UNSIGNED NOT NULL,
  phase                 VARCHAR(32)      NOT NULL DEFAULT 'SCAMMER_GENERATING',
  fraud_stage           TEXT             NULL,
  speech_analysis       TEXT             NULL,
  method_analysis       TEXT             NULL,
  victim_psychology     TEXT             NULL,
  risk_score            TINYINT UNSIGNED NULL,
  risk_reason           TEXT             NULL,
  actual_fraud_occurred TINYINT UNSIGNED NULL,
  goal_achieved         TINYINT UNSIGNED NULL,
  cooperation_stopped   TINYINT UNSIGNED NULL,
  evidence_json         JSON             NULL,
  candidate_outcome     VARCHAR(24)      NULL,
  candidate_reason      TEXT             NULL,
  analysis_saved_at     DATETIME(3)      NULL,
  analysis_presented_at DATETIME(3)      NULL,
  reading_until         DATETIME(3)      NULL,
  created_at            DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uq_round_session_no (session_id, round_no),
  CONSTRAINT fk_round_session FOREIGN KEY (session_id) REFERENCES simulation_session (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT ck_round_no CHECK (round_no BETWEEN 1 AND 20),
  CONSTRAINT ck_round_phase CHECK (phase IN ('SCAMMER_GENERATING','SCAMMER_PRESENTING','VICTIM_GENERATING','VICTIM_PRESENTING',
      'ANALYZING','ANALYSIS_PRESENTING','COMPLETE')),
  CONSTRAINT ck_round_risk_score CHECK (risk_score IS NULL OR risk_score IN (0,25,50,75,100)),
  CONSTRAINT ck_round_flags CHECK ((actual_fraud_occurred IS NULL OR actual_fraud_occurred IN (0,1))
      AND (goal_achieved IS NULL OR goal_achieved IN (0,1)) AND (cooperation_stopped IS NULL OR cooperation_stopped IN (0,1))),
  CONSTRAINT ck_round_analysis_group CHECK (
      (fraud_stage IS NULL AND speech_analysis IS NULL AND method_analysis IS NULL AND victim_psychology IS NULL
          AND risk_score IS NULL AND risk_reason IS NULL AND actual_fraud_occurred IS NULL AND goal_achieved IS NULL
          AND cooperation_stopped IS NULL AND evidence_json IS NULL AND analysis_saved_at IS NULL
          AND candidate_outcome IS NULL AND candidate_reason IS NULL)
   OR (fraud_stage IS NOT NULL AND speech_analysis IS NOT NULL AND method_analysis IS NOT NULL AND victim_psychology IS NOT NULL
          AND risk_score IS NOT NULL AND risk_reason IS NOT NULL AND actual_fraud_occurred IS NOT NULL AND goal_achieved IS NOT NULL
          AND cooperation_stopped IS NOT NULL AND evidence_json IS NOT NULL AND JSON_TYPE(evidence_json) = 'ARRAY'
          AND analysis_saved_at IS NOT NULL)),
  CONSTRAINT ck_round_goal_requires_fraud CHECK (goal_achieved IS NULL OR goal_achieved = 0 OR actual_fraud_occurred = 1),
  CONSTRAINT ck_round_candidate CHECK (
      (candidate_outcome IS NULL AND candidate_reason IS NULL)
   OR (candidate_outcome IS NOT NULL AND candidate_reason IS NOT NULL
          AND candidate_outcome IN ('GOAL_ACHIEVED','LOSS_STOPPED','NOT_DEFRAUDED'))),
  CONSTRAINT ck_round_candidate_min_rounds CHECK (round_no >= 5 OR (candidate_outcome IS NULL AND candidate_reason IS NULL)),
  CONSTRAINT ck_round_presented CHECK (
      (analysis_presented_at IS NULL AND reading_until IS NULL)
   OR (analysis_presented_at IS NOT NULL AND analysis_saved_at IS NOT NULL AND phase = 'COMPLETE'
          AND reading_until IS NOT NULL AND reading_until >= analysis_presented_at
          -- 5 s reading pause, plus at most 300 s of pause credit added by resume (V1.1).
          AND reading_until <= analysis_presented_at + INTERVAL 305 SECOND))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE simulation_message (
  id                        BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  round_id                  BIGINT UNSIGNED NOT NULL,
  speaker_role              VARCHAR(16)     NOT NULL,
  content                   TEXT            NOT NULL,
  action_text               TEXT            NULL,
  internal_note             TEXT            NULL,
  presentation_status       VARCHAR(16)     NOT NULL DEFAULT 'PENDING',
  presentation_completed_at DATETIME(3)     NULL,
  presentation_error_code   VARCHAR(32)     NULL,
  created_at                DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uq_message_round_role (round_id, speaker_role),
  CONSTRAINT fk_message_round FOREIGN KEY (round_id) REFERENCES simulation_round (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT ck_message_speaker CHECK (speaker_role IN ('SCAMMER','VICTIM')),
  CONSTRAINT ck_message_content CHECK (CHAR_LENGTH(content) >= 1),
  CONSTRAINT ck_message_status CHECK (
      (presentation_status IN ('PENDING','PRESENTING') AND presentation_completed_at IS NULL AND presentation_error_code IS NULL)
   OR (presentation_status = 'COMPLETED' AND presentation_completed_at IS NOT NULL AND presentation_error_code IS NULL)
   OR (presentation_status = 'FAILED' AND presentation_error_code IS NOT NULL AND presentation_completed_at IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE simulation_report (
  session_id             BIGINT UNSIGNED   NOT NULL,
  status                 VARCHAR(16)       NOT NULL DEFAULT 'PENDING',
  attempt_no             INT UNSIGNED      NOT NULL DEFAULT 0,
  task_token             CHAR(36)          NULL,
  queued_at              DATETIME(3)       NOT NULL,
  started_at             DATETIME(3)       NULL,
  attempt_deadline_at    DATETIME(3)       NULL,
  finished_at            DATETIME(3)       NULL,
  content_schema_version SMALLINT UNSIGNED NOT NULL DEFAULT 1,
  content_json           JSON              NULL,
  error_code             VARCHAR(32)       NULL,
  created_at             DATETIME(3)       NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at             DATETIME(3)       NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (session_id),
  KEY idx_report_queue (status, queued_at, session_id),
  KEY idx_report_deadline (status, attempt_deadline_at, session_id),
  CONSTRAINT fk_report_session FOREIGN KEY (session_id) REFERENCES simulation_session (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT ck_report_status CHECK (status IN ('PENDING','RUNNING','SUCCEEDED','FAILED')),
  CONSTRAINT ck_report_schema_version CHECK (content_schema_version >= 1),
  CONSTRAINT ck_report_error_code CHECK (error_code IS NULL OR error_code IN ('GENERATION_FAILED','VALIDATION_FAILED',
      'PERSISTENCE_FAILED','TIMEOUT','SERVICE_RESTARTED')),
  CONSTRAINT ck_report_fields CHECK (
      (status = 'PENDING' AND task_token IS NULL AND started_at IS NULL AND attempt_deadline_at IS NULL
          AND finished_at IS NULL AND content_json IS NULL AND error_code IS NULL)
   OR (status = 'RUNNING' AND attempt_no >= 1 AND task_token IS NOT NULL AND started_at IS NOT NULL
          AND attempt_deadline_at IS NOT NULL AND attempt_deadline_at > started_at
          AND finished_at IS NULL AND content_json IS NULL AND error_code IS NULL)
   OR (status = 'SUCCEEDED' AND attempt_no >= 1 AND task_token IS NOT NULL AND started_at IS NOT NULL
          AND finished_at IS NOT NULL AND content_json IS NOT NULL AND JSON_TYPE(content_json) = 'OBJECT' AND error_code IS NULL)
   OR (status = 'FAILED' AND attempt_no >= 1 AND task_token IS NOT NULL AND started_at IS NOT NULL
          AND finished_at IS NOT NULL AND error_code IS NOT NULL AND content_json IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
