ALTER TABLE simulation_session
  DROP CHECK ck_session_deadline,
  ADD CONSTRAINT ck_session_deadline CHECK (
    deadline_at = started_at + INTERVAL (
      CASE WHEN JSON_UNQUOTE(JSON_EXTRACT(runtime_snapshot,'$.trust_config.trust_ruleset_version'))='trust-v2'
        THEN COALESCE(CAST(JSON_UNQUOTE(JSON_EXTRACT(runtime_snapshot,'$.pacing_config.max_active_duration_ms')) AS UNSIGNED),600000)
        ELSE 600000 END + paused_total_ms
    ) * 1000 MICROSECOND
  );
