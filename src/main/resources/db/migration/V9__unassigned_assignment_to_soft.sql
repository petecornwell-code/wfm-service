-- Change unassigned assignment weight from hard to soft.
-- Unallocated slots within the underflow/overflow tolerance (enforced by
-- bulk under/over-allocation hard constraints) should not be hard violations.
-- The solver still strongly prefers filling slots via the 1000soft weight.
UPDATE constraint_weights
   SET unassigned_assignment_weight = '0hard/1000soft';
