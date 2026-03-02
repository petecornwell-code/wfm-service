-- Increase unassigned assignment weight from 1hard to 1000hard so that
-- the construction heuristic always prefers assigning an agent over
-- leaving a seat empty (contracted hours deviation can reach ~32hard
-- during incremental CH placement, so the unassigned weight must dominate).
UPDATE constraint_weights
   SET unassigned_assignment_weight = '1000hard/0soft';
