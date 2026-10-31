CREATE TABLE simulation_trust_event (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  session_id BIGINT UNSIGNED NOT NULL,
  round_no TINYINT UNSIGNED NOT NULL,
  message_id BIGINT UNSIGNED NULL,
  event_type VARCHAR(32) NOT NULL,
  subject_key VARCHAR(128) NOT NULL,
  base_delta SMALLINT NOT NULL,
  factor DECIMAL(3,2) NOT NULL,
  delta SMALLINT NOT NULL,
  score_before TINYINT UNSIGNED NOT NULL,
  score_after TINYINT UNSIGNED NOT NULL,
  evidence_source VARCHAR(16) NOT NULL,
  evidence_quote VARCHAR(255) NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  CONSTRAINT fk_trust_session FOREIGN KEY (session_id) REFERENCES simulation_session(id),
  CONSTRAINT fk_trust_message FOREIGN KEY (message_id) REFERENCES simulation_message(id),
  CONSTRAINT uq_trust_session_event_subject UNIQUE (session_id, event_type, subject_key),
  INDEX idx_trust_message (message_id),
  CONSTRAINT ck_trust_round CHECK (round_no BETWEEN 1 AND 20),
  CONSTRAINT ck_trust_type CHECK (event_type IN ('NEW_EFFECTIVE_EXPLANATION','PROMISE_FULFILLED','NEW_CONTRADICTION',
    'DECISIVE_COUNTEREVIDENCE','INDEPENDENT_SUPPORT','INDEPENDENT_DOUBT','REPEAT_NO_PROGRESS')),
  CONSTRAINT ck_trust_text CHECK (CHAR_LENGTH(TRIM(subject_key)) > 0 AND CHAR_LENGTH(TRIM(evidence_quote)) > 0),
  CONSTRAINT ck_trust_delta CHECK (base_delta BETWEEN -10 AND 5 AND delta BETWEEN -100 AND 100 AND factor BETWEEN 0.10 AND 3.00),
  CONSTRAINT ck_trust_score CHECK (score_before BETWEEN 0 AND 100 AND score_after BETWEEN 0 AND 100 AND score_after = score_before + delta),
  CONSTRAINT ck_trust_source CHECK ((evidence_source IN ('CONTENT','ACTION') AND message_id IS NOT NULL) OR evidence_source='SYSTEM')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE simulation_waiting_item (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  session_id BIGINT UNSIGNED NOT NULL,
  item_type VARCHAR(24) NOT NULL,
  subject_key VARCHAR(128) NOT NULL,
  request_text VARCHAR(255) NOT NULL,
  basis_text VARCHAR(255) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
  resolution_text VARCHAR(255) NULL,
  opened_round_no TINYINT UNSIGNED NOT NULL,
  resolved_round_no TINYINT UNSIGNED NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  CONSTRAINT fk_waiting_session FOREIGN KEY (session_id) REFERENCES simulation_session(id),
  CONSTRAINT uq_waiting_session_type_subject UNIQUE (session_id, item_type, subject_key),
  INDEX idx_waiting_status (session_id, status),
  CONSTRAINT ck_waiting_type CHECK (item_type IN ('MATERIAL','INFORMATION','CONSULTATION','VERIFICATION')),
  CONSTRAINT ck_waiting_status CHECK (status IN ('OPEN','RESPONDED','SATISFIED','UNAVAILABLE','ABANDONED')),
  CONSTRAINT ck_waiting_text CHECK (CHAR_LENGTH(TRIM(subject_key)) > 0 AND CHAR_LENGTH(TRIM(request_text)) > 0 AND CHAR_LENGTH(TRIM(basis_text)) > 0),
  CONSTRAINT ck_waiting_round CHECK (opened_round_no BETWEEN 1 AND 20 AND
    (resolved_round_no IS NULL OR resolved_round_no BETWEEN opened_round_no AND 20)),
  CONSTRAINT ck_waiting_resolution CHECK (status NOT IN ('RESPONDED','SATISFIED','UNAVAILABLE')
    OR (resolution_text IS NOT NULL AND CHAR_LENGTH(TRIM(resolution_text)) > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE simulation_policy (
  id TINYINT UNSIGNED NOT NULL DEFAULT 1 PRIMARY KEY,
  trust_threshold_accept SMALLINT UNSIGNED NOT NULL DEFAULT 60,
  trust_threshold_small SMALLINT UNSIGNED NOT NULL DEFAULT 80,
  trust_threshold_large SMALLINT UNSIGNED NOT NULL DEFAULT 90,
  row_version INT UNSIGNED NOT NULL DEFAULT 0,
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  CONSTRAINT ck_policy_singleton CHECK (id=1),
  CONSTRAINT ck_policy_thresholds CHECK (trust_threshold_accept BETWEEN 0 AND 100 AND trust_threshold_small BETWEEN 0 AND 100
    AND trust_threshold_large BETWEEN 0 AND 100 AND trust_threshold_accept < trust_threshold_small AND trust_threshold_small < trust_threshold_large)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
INSERT INTO simulation_policy(id, trust_threshold_accept, trust_threshold_small, trust_threshold_large, row_version)
VALUES (1,60,80,90,0);
