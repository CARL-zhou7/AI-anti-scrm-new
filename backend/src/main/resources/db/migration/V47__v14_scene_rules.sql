-- V1.4 Phase 3: scenario knowledge base and the frozen per-session rule snapshot.
-- One rule per scene title; one intervention may be the source of at most one rule (merge only, never split).

CREATE TABLE scene_rule (
  id            BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT,
  scenario_code VARCHAR(32)       NOT NULL,
  title         VARCHAR(128)      NOT NULL,
  rule_text     TEXT              NOT NULL,
  version_no    INT UNSIGNED      NOT NULL DEFAULT 1,
  status        VARCHAR(16)       NOT NULL DEFAULT 'ENABLED',
  created_by    BIGINT UNSIGNED   NOT NULL,
  created_at    DATETIME(3)       NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at    DATETIME(3)       NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uq_scene_rule_scene_title (scenario_code, title),
  KEY idx_scene_rule_active (scenario_code, status, version_no DESC, id),
  CONSTRAINT fk_scene_rule_user FOREIGN KEY (created_by) REFERENCES sys_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT ck_scene_rule_status CHECK (status IN ('ENABLED','DISABLED')),
  CONSTRAINT ck_scene_rule_scene CHECK (scenario_code IN ('TASK_REBATE','FAKE_INVESTMENT','IMPERSONATION_LOAN')),
  CONSTRAINT ck_scene_rule_version CHECK (version_no >= 1),
  CONSTRAINT ck_scene_rule_text CHECK (CHAR_LENGTH(TRIM(title)) > 0 AND CHAR_LENGTH(TRIM(rule_text)) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE scene_rule_source (
  rule_id         BIGINT UNSIGNED NOT NULL,
  intervention_id BIGINT UNSIGNED NOT NULL,
  admin_user_id   BIGINT UNSIGNED NOT NULL,
  created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (rule_id, intervention_id),
  UNIQUE KEY uq_source_intervention (intervention_id),
  KEY idx_source_rule (rule_id, intervention_id),
  CONSTRAINT fk_source_rule FOREIGN KEY (rule_id) REFERENCES scene_rule (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_source_intervention FOREIGN KEY (intervention_id) REFERENCES simulation_intervention (id)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_source_user FOREIGN KEY (admin_user_id) REFERENCES sys_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

ALTER TABLE simulation_session
  ADD COLUMN scene_rule_snapshot JSON NULL AFTER intervention_mode,
  ADD CONSTRAINT ck_session_rule_snapshot CHECK (
    scene_rule_snapshot IS NULL OR JSON_TYPE(scene_rule_snapshot) = 'ARRAY'
  );
