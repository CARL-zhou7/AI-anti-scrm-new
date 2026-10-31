-- V1.4 Phase 3 (option A): separate "adopted, waiting for the knowledge base" from "already used as a rule source".
-- PENDING stays the transient state before the formal message exists; ADOPTED means adopted and waiting for the
-- knowledge base; LINKED means the source of at least one scene rule.
ALTER TABLE simulation_intervention
  DROP CHECK ck_intervention_status,
  DROP CHECK ck_intervention_linked,
  ADD CONSTRAINT ck_intervention_status CHECK (status IN ('PENDING','ADOPTED','LINKED')),
  ADD CONSTRAINT ck_intervention_linked CHECK (
    (status = 'PENDING' AND final_message_id IS NULL)
    OR (status IN ('ADOPTED','LINKED') AND final_message_id IS NOT NULL)
  );

-- Existing adopted records already carry their formal message: they are waiting for the knowledge base.
UPDATE simulation_intervention SET status = 'ADOPTED' WHERE status = 'LINKED';
