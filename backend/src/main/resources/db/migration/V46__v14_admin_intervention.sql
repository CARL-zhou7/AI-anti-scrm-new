-- V1.4 Phase 2: administrator intervention (draft review gate).
-- Old rows keep NONE and the old round phases; only ADMIN_TEST sessions use the review phase.

ALTER TABLE simulation_session
  ADD COLUMN intervention_mode VARCHAR(16) NOT NULL DEFAULT 'NONE' AFTER report_prompt_id,
  ADD CONSTRAINT ck_session_intervention_mode CHECK (intervention_mode IN ('NONE','ADMIN_TEST'));

ALTER TABLE simulation_round
  DROP CHECK ck_round_phase,
  ADD CONSTRAINT ck_round_phase CHECK (phase IN ('SCAMMER_GENERATING','SCAMMER_REVIEW','SCAMMER_PRESENTING',
    'VICTIM_GENERATING','VICTIM_PRESENTING','ANALYZING','ANALYSIS_PRESENTING','COMPLETE'));

CREATE TABLE simulation_scammer_draft (
  id                BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT,
  session_id        BIGINT UNSIGNED   NOT NULL,
  round_id          BIGINT UNSIGNED   NOT NULL,
  round_no          TINYINT UNSIGNED  NOT NULL,
  revision_no       INT UNSIGNED      NOT NULL,
  status            VARCHAR(16)       NOT NULL DEFAULT 'DRAFT',
  original_content  TEXT              NOT NULL,
  original_action   TEXT              NULL,
  draft_content     TEXT              NOT NULL,
  draft_action      TEXT              NULL,
  guidance_text     VARCHAR(500)      NULL,
  -- Only one DRAFT may hold a round; ADOPTED/DISCARDED rows never block the next draft.
  active_round_id   BIGINT UNSIGNED   GENERATED ALWAYS AS (CASE WHEN status = 'DRAFT' THEN round_id ELSE NULL END) STORED,
  created_by        BIGINT UNSIGNED   NOT NULL,
  created_at        DATETIME(3)       NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at        DATETIME(3)       NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uq_draft_round_revision (round_id, revision_no),
  UNIQUE KEY uq_draft_round_active (active_round_id),
  KEY idx_draft_session (session_id, round_no),
  CONSTRAINT fk_draft_session FOREIGN KEY (session_id) REFERENCES simulation_session (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_draft_round FOREIGN KEY (round_id) REFERENCES simulation_round (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_draft_user FOREIGN KEY (created_by) REFERENCES sys_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT ck_draft_status CHECK (status IN ('DRAFT','ADOPTED','DISCARDED')),
  CONSTRAINT ck_draft_round_no CHECK (round_no BETWEEN 1 AND 20),
  CONSTRAINT ck_draft_revision CHECK (revision_no >= 0),
  CONSTRAINT ck_draft_content CHECK (CHAR_LENGTH(TRIM(draft_content)) >= 1 AND CHAR_LENGTH(TRIM(original_content)) >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE simulation_intervention (
  id                BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT,
  session_id        BIGINT UNSIGNED   NOT NULL,
  round_id          BIGINT UNSIGNED   NOT NULL,
  round_no          TINYINT UNSIGNED  NOT NULL,
  draft_id          BIGINT UNSIGNED   NOT NULL,
  final_message_id  BIGINT UNSIGNED   NULL,
  status            VARCHAR(16)       NOT NULL DEFAULT 'PENDING',
  mode              VARCHAR(16)       NOT NULL DEFAULT 'ADMIN_TEST',
  scene_code        VARCHAR(32)       NOT NULL,
  admin_user_id     BIGINT UNSIGNED   NOT NULL,
  original_content  TEXT              NOT NULL,
  original_action   TEXT              NULL,
  final_content     TEXT              NOT NULL,
  final_action      TEXT              NULL,
  intervention_note VARCHAR(500)      NULL,
  created_at        DATETIME(3)       NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at        DATETIME(3)       NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uq_intervention_draft (draft_id),
  UNIQUE KEY uq_intervention_final_message (final_message_id),
  KEY idx_intervention_admin (admin_user_id, status, id),
  KEY idx_intervention_scene (scene_code, status, id),
  CONSTRAINT fk_intervention_session FOREIGN KEY (session_id) REFERENCES simulation_session (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_intervention_round FOREIGN KEY (round_id) REFERENCES simulation_round (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_intervention_draft FOREIGN KEY (draft_id) REFERENCES simulation_scammer_draft (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_intervention_message FOREIGN KEY (final_message_id) REFERENCES simulation_message (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_intervention_user FOREIGN KEY (admin_user_id) REFERENCES sys_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT ck_intervention_status CHECK (status IN ('PENDING','LINKED')),
  CONSTRAINT ck_intervention_mode CHECK (mode IN ('ADMIN_TEST')),
  CONSTRAINT ck_intervention_scene CHECK (scene_code IN ('TASK_REBATE','FAKE_INVESTMENT','IMPERSONATION_LOAN')),
  CONSTRAINT ck_intervention_round_no CHECK (round_no BETWEEN 1 AND 20),
  CONSTRAINT ck_intervention_content CHECK (CHAR_LENGTH(TRIM(original_content)) >= 1 AND CHAR_LENGTH(TRIM(final_content)) >= 1),
  CONSTRAINT ck_intervention_linked CHECK ((status = 'PENDING' AND final_message_id IS NULL) OR status = 'LINKED')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
