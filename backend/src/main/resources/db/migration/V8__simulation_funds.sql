-- Existing rows retain schema version 1; their default zero amounts are not recorded facts.
ALTER TABLE simulation_session
  ALTER COLUMN snapshot_schema_version SET DEFAULT 2,
  ADD COLUMN initial_available_funds_yuan INT UNSIGNED NOT NULL DEFAULT 0,
  ADD COLUMN available_balance_yuan INT UNSIGNED NOT NULL DEFAULT 0,
  ADD COLUMN total_paid_yuan INT UNSIGNED NOT NULL DEFAULT 0,
  ADD COLUMN total_refunded_yuan INT UNSIGNED NOT NULL DEFAULT 0,
  ADD COLUMN actual_loss_yuan INT UNSIGNED NOT NULL DEFAULT 0,
  ADD COLUMN additional_payment_count SMALLINT UNSIGNED NOT NULL DEFAULT 0,
  ADD COLUMN reserve_released_yuan INT UNSIGNED NOT NULL DEFAULT 0,
  ADD CONSTRAINT ck_session_actual_loss CHECK (actual_loss_yuan = CASE WHEN total_paid_yuan > total_refunded_yuan THEN total_paid_yuan - total_refunded_yuan ELSE 0 END);

ALTER TABLE simulation_round
  ADD COLUMN round_paid_yuan INT UNSIGNED NOT NULL DEFAULT 0,
  ADD COLUMN round_refunded_yuan INT UNSIGNED NOT NULL DEFAULT 0,
  ADD COLUMN round_reserve_released_yuan INT UNSIGNED NOT NULL DEFAULT 0,
  ADD COLUMN round_balance_yuan INT UNSIGNED NOT NULL DEFAULT 0,
  ADD COLUMN round_actual_loss_yuan INT UNSIGNED NOT NULL DEFAULT 0,
  ADD COLUMN additional_payment_count_total SMALLINT UNSIGNED NOT NULL DEFAULT 0;

CREATE TABLE simulation_fund_flow (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  session_id BIGINT UNSIGNED NOT NULL,
  round_id BIGINT UNSIGNED NOT NULL,
  message_id BIGINT UNSIGNED NULL,
  sequence_no INT UNSIGNED NOT NULL,
  flow_type VARCHAR(16) NOT NULL,
  purpose VARCHAR(32) NOT NULL,
  amount_yuan INT UNSIGNED NOT NULL,
  balance_after_yuan INT UNSIGNED NOT NULL,
  reason VARCHAR(255) NULL,
  occurred_at DATETIME(3) NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  CONSTRAINT fk_fund_session FOREIGN KEY (session_id) REFERENCES simulation_session(id),
  CONSTRAINT fk_fund_round FOREIGN KEY (round_id) REFERENCES simulation_round(id),
  CONSTRAINT fk_fund_message FOREIGN KEY (message_id) REFERENCES simulation_message(id),
  CONSTRAINT uq_fund_session_sequence UNIQUE (session_id, sequence_no),
  INDEX idx_fund_session_round (session_id, round_id),
  INDEX idx_fund_message (message_id),
  CONSTRAINT ck_fund_amount CHECK (amount_yuan > 0 AND balance_after_yuan >= 0),
  CONSTRAINT ck_fund_sequence CHECK (sequence_no >= 1),
  CONSTRAINT ck_fund_type_purpose CHECK (
    (flow_type = 'PAYMENT' AND purpose IN ('INITIAL_PAYMENT','ADDITIONAL_PAYMENT','INVESTMENT_PAYMENT','EXTRA_FEE_PAYMENT','LOAN_TRANSFER','OTHER_PAYMENT'))
    OR (flow_type = 'REFUND' AND purpose = 'REFUND')
    OR (flow_type = 'RESERVE_RELEASE' AND purpose = 'RESERVE_RELEASE' AND reason IS NOT NULL AND CHAR_LENGTH(TRIM(reason)) > 0)
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
