-- Structure only: old snapshots, rule versions, prompt bindings and report defaults stay unchanged.
ALTER TABLE victim_profile DROP CHECK ck_victim_reserved_funds,
  ADD COLUMN general_reserve_funds_yuan INT UNSIGNED NOT NULL DEFAULT 0,
  ADD COLUMN emergency_reserve_funds_yuan INT UNSIGNED NOT NULL DEFAULT 0;
UPDATE victim_profile SET general_reserve_funds_yuan = reserved_funds_yuan;
ALTER TABLE victim_profile DROP COLUMN reserved_funds_yuan,
  ADD COLUMN reserved_funds_yuan INT UNSIGNED GENERATED ALWAYS AS (general_reserve_funds_yuan + emergency_reserve_funds_yuan) STORED,
  ADD CONSTRAINT ck_victim_reserved_funds CHECK (reserved_funds_yuan <= initial_savings_yuan);

ALTER TABLE victim_scenario_profile
  ADD COLUMN initial_trust_score TINYINT UNSIGNED NULL,
  ADD COLUMN positive_factor DECIMAL(3,2) NULL,
  ADD COLUMN negative_factor DECIMAL(3,2) NULL,
  ADD COLUMN small_amount_cap_yuan INT UNSIGNED NULL;

-- NULL means trust was not recorded for a legacy session; do not invent historical scores.
ALTER TABLE simulation_session
  ADD COLUMN event_prompt_id BIGINT UNSIGNED NULL,
  ADD CONSTRAINT fk_session_event_prompt FOREIGN KEY (event_prompt_id) REFERENCES agent_prompt(id),
  ADD COLUMN minimum_rounds_exception TINYINT UNSIGNED NOT NULL DEFAULT 0,
  ADD COLUMN total_rewarded_yuan INT UNSIGNED NOT NULL DEFAULT 0,
  ADD COLUMN general_reserve_total_yuan INT UNSIGNED NOT NULL DEFAULT 0,
  ADD COLUMN emergency_reserve_total_yuan INT UNSIGNED NOT NULL DEFAULT 0,
  ADD COLUMN general_reserve_released_yuan INT UNSIGNED NOT NULL DEFAULT 0,
  ADD COLUMN emergency_reserve_released_yuan INT UNSIGNED NOT NULL DEFAULT 0,
  ADD COLUMN initial_trust_score TINYINT UNSIGNED NULL,
  ADD COLUMN current_trust_score TINYINT UNSIGNED NULL,
  ADD COLUMN trust_positive_factor DECIMAL(3,2) NULL,
  ADD COLUMN trust_negative_factor DECIMAL(3,2) NULL,
  ADD COLUMN trust_small_cap_yuan INT UNSIGNED NULL,
  ADD COLUMN trust_threshold_accept SMALLINT UNSIGNED NULL,
  ADD COLUMN trust_threshold_small SMALLINT UNSIGNED NULL,
  ADD COLUMN trust_threshold_large SMALLINT UNSIGNED NULL;
UPDATE simulation_session SET general_reserve_released_yuan = reserve_released_yuan;
ALTER TABLE simulation_session DROP COLUMN reserve_released_yuan,
  ADD COLUMN reserve_released_yuan INT UNSIGNED GENERATED ALWAYS AS (general_reserve_released_yuan + emergency_reserve_released_yuan) STORED,
  DROP CHECK ck_session_actual_loss,
  ADD CONSTRAINT ck_session_actual_loss CHECK (actual_loss_yuan = CASE WHEN total_paid_yuan > total_refunded_yuan + total_rewarded_yuan
    THEN total_paid_yuan - total_refunded_yuan - total_rewarded_yuan ELSE 0 END),
  DROP CHECK ck_session_minimum_rounds,
  ADD CONSTRAINT ck_session_minimum_rounds CHECK (status <> 'FINISHED' OR outcome_code = 'UNDETERMINED' OR current_round_no >= 5
    OR minimum_rounds_exception = 1 OR (outcome_code IN ('DEFRAUDED','LIMIT_REACHED') AND limit_reason IS NOT NULL AND limit_reason IN ('TIME_LIMIT','BOTH'))),
  ADD CONSTRAINT ck_session_round_exception CHECK (minimum_rounds_exception IN (0,1)),
  ADD CONSTRAINT ck_session_trust_scores CHECK (initial_trust_score BETWEEN 0 AND 100 AND current_trust_score BETWEEN 0 AND 100),
  ADD CONSTRAINT ck_session_trust_factors CHECK (trust_positive_factor BETWEEN 0.10 AND 3.00 AND trust_negative_factor BETWEEN 0.10 AND 3.00),
  ADD CONSTRAINT ck_session_trust_thresholds CHECK (trust_threshold_accept BETWEEN 0 AND 100
    AND trust_threshold_small BETWEEN 0 AND 100 AND trust_threshold_large BETWEEN 0 AND 100
    AND trust_threshold_accept < trust_threshold_small AND trust_threshold_small < trust_threshold_large),
  -- Legacy snapshots never recorded the split totals, so only v3 can enforce these bounds.
  ADD CONSTRAINT ck_session_reserve_totals CHECK (snapshot_schema_version < 3 OR
    (general_reserve_released_yuan <= general_reserve_total_yuan AND emergency_reserve_released_yuan <= emergency_reserve_total_yuan));

ALTER TABLE simulation_round
  ADD COLUMN round_rewarded_yuan INT UNSIGNED NOT NULL DEFAULT 0,
  DROP CHECK ck_round_candidate_min_rounds,
  DROP CHECK ck_round_candidate,
  ADD CONSTRAINT ck_round_candidate CHECK ((candidate_outcome IS NULL AND candidate_reason IS NULL)
    OR (candidate_outcome IS NOT NULL AND candidate_reason IS NOT NULL
      AND candidate_outcome IN ('GOAL_ACHIEVED','LOSS_STOPPED','NOT_DEFRAUDED','DEFRAUDED','IDENTIFIED')));

ALTER TABLE simulation_fund_flow ADD COLUMN reserve_tier VARCHAR(16) NULL AFTER reason;
UPDATE simulation_fund_flow SET reserve_tier='GENERAL' WHERE flow_type='RESERVE_RELEASE';
ALTER TABLE simulation_fund_flow
  DROP CHECK ck_fund_type_purpose,
  ADD CONSTRAINT ck_fund_type_purpose CHECK (
    (flow_type='PAYMENT' AND purpose IN ('INITIAL_PAYMENT','ADDITIONAL_PAYMENT','INVESTMENT_PAYMENT','EXTRA_FEE_PAYMENT','LOAN_TRANSFER','OTHER_PAYMENT'))
    OR (flow_type='REFUND' AND purpose='REFUND') OR (flow_type='REWARD' AND purpose='REWARD')
    OR (flow_type='RESERVE_RELEASE' AND purpose='RESERVE_RELEASE' AND reason IS NOT NULL AND CHAR_LENGTH(TRIM(reason)) > 0)),
  DROP CHECK ck_fund_related_payment,
  ADD CONSTRAINT ck_fund_related_payment CHECK ((flow_type='REFUND' AND related_payment_id IS NOT NULL)
    OR (flow_type<>'REFUND' AND related_payment_id IS NULL)),
  ADD CONSTRAINT ck_fund_reserve_tier CHECK ((flow_type='RESERVE_RELEASE' AND reserve_tier IS NOT NULL AND reserve_tier IN ('GENERAL','EMERGENCY'))
    OR (flow_type<>'RESERVE_RELEASE' AND reserve_tier IS NULL));

ALTER TABLE simulation_report DROP CHECK ck_report_schema_version,
  ADD CONSTRAINT ck_report_schema_version CHECK (content_schema_version IN (1,2,3));
ALTER TABLE agent_prompt DROP CHECK ck_prompt_type,
  ADD CONSTRAINT ck_prompt_type CHECK (prompt_type IN ('SCAMMER','VICTIM','NARRATOR','REPORT','EVENT'));
