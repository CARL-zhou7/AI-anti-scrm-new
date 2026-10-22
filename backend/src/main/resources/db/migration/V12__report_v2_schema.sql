ALTER TABLE simulation_report
  ALTER COLUMN content_schema_version SET DEFAULT 2,
  DROP CHECK ck_report_schema_version,
  ADD CONSTRAINT ck_report_schema_version CHECK (content_schema_version IN (1,2));
