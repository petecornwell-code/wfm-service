-- Add weight column for the "contracted hours (under, zero)" constraint.
-- This constraint penalises agents who have an AgentDayConfig (expected to work)
-- but have ZERO assignments on that day. The existing contracted_hours_under
-- constraint only detects agents with some-but-not-enough assignments because
-- it starts from AgentAssignment (which excludes unassigned agents entirely).

ALTER TABLE constraint_weights
    ADD COLUMN contracted_hours_under_zero_weight VARCHAR(255) DEFAULT '1hard/0soft';

UPDATE constraint_weights
   SET contracted_hours_under_zero_weight = contracted_hours_under_weight;
