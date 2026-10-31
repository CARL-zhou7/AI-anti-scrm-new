ALTER TABLE simulation_trust_event
  ADD COLUMN progress_key VARCHAR(128) NULL AFTER subject_key;
UPDATE simulation_trust_event SET progress_key=subject_key;
ALTER TABLE simulation_trust_event
  MODIFY COLUMN progress_key VARCHAR(128) NOT NULL,
  DROP INDEX uq_trust_session_event_subject,
  ADD CONSTRAINT uq_trust_session_event_progress UNIQUE (session_id,event_type,subject_key,progress_key),
  DROP CHECK ck_trust_type,
  ADD CONSTRAINT ck_trust_type CHECK (event_type IN ('NEW_EFFECTIVE_EXPLANATION','PROMISE_FULFILLED',
    'NEW_CONTRADICTION','DECISIVE_COUNTEREVIDENCE','INDEPENDENT_SUPPORT','INDEPENDENT_DOUBT',
    'REPEAT_NO_PROGRESS','DOUBT_RESOLVED','PROFESSIONAL_EXPLANATION','RELEVANT_MATERIAL','EVASIVE_RESPONSE')),
  ADD CONSTRAINT ck_trust_progress CHECK (CHAR_LENGTH(TRIM(progress_key)) > 0);
