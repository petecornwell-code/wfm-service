-- Increase contracted_hours_weight from 1hard to 1001hard so it dominates
-- the unassigned_assignment_weight (1000hard). Without this, the default
-- construction heuristic over-assigns agents to fill empty seats because
-- filling one seat (+1000hard) far outweighs the contracted hours penalty
-- (-1hard per extra timeslot), causing agents to work 9-10 hours instead
-- of their contracted 8.
UPDATE constraint_weights
   SET contracted_hours_weight = '1001hard/0soft';
