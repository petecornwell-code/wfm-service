-- Add constraint weight column for the "Unassigned assignment" hard constraint.
-- This penalizes AgentAssignment entities left without an assigned DeskAgent.
ALTER TABLE constraint_weights
    ADD COLUMN unassigned_assignment_weight VARCHAR(255) NOT NULL DEFAULT '1hard/0soft';
