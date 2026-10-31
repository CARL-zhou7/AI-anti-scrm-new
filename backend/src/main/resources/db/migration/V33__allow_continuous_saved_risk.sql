-- Keep historical scores; new trust-driven analyses may save any integer in the range.
ALTER TABLE simulation_round
  DROP CHECK ck_round_risk_score,
  ADD CONSTRAINT ck_round_risk_score CHECK (risk_score IS NULL OR risk_score BETWEEN 0 AND 100);
