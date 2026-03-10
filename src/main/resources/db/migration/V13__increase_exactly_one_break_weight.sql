-- Increase exactly_one_break_weight from 10hard to 100hard.
-- V12 reduced break weights from 1000 to 10 to avoid blocking the CH,
-- but at 10hard the solver trades break integrity for contracted-hours
-- compliance (100hard/slot), creating multiple breaks per agent.
-- The CH-friendly guard in the constraint (only penalises when
-- assignments >= breakThresholdSlots) already protects CH progress,
-- so 100hard is safe. The penalty now also scales with |gaps - expected|,
-- giving the solver a gradient to consolidate fragmented breaks.
UPDATE constraint_weights
   SET exactly_one_break_weight = '100hard/0soft'
 WHERE exactly_one_break_weight = '10hard/0soft';
