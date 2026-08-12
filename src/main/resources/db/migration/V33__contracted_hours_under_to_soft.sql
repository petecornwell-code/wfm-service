-- Move the contracted-hours UNDER constraints from hard to soft.
--
-- Under-allocation is usually a demand/roster mismatch, not an illegal schedule. When a desk has
-- more contracted agent-hours than staffing demand, the shortfall cannot be removed by ANY
-- arrangement of assignments — so as a hard constraint it made the entire problem infeasible.
-- Observed 2026-08-12 on the live desk: 112 contracted agent-hours against 27 hours of demand,
-- giving a flat hard score of -10860, feasible=false, and no usable schedule at all.
--
-- As soft constraints the solver returns the best achievable roster and reports the shortfall in
-- the score breakdown instead of failing outright.
--
-- Contracted hours (OVER) deliberately stays hard: assigning more hours than contracted is a real
-- breach and is always avoidable by not making the assignment.
--
-- Weights are stored per desk, so the Java defaults in ConstraintWeights only apply to rows
-- created after this; existing rows must be converted here. Magnitude is preserved (100), only
-- the hard/soft level changes.
UPDATE constraint_weights
   SET contracted_hours_under_weight = '0hard/100soft'
 WHERE contracted_hours_under_weight LIKE '%hard%'
   AND contracted_hours_under_weight NOT LIKE '0hard/%';

UPDATE constraint_weights
   SET contracted_hours_under_zero_weight = '0hard/100soft'
 WHERE contracted_hours_under_zero_weight LIKE '%hard%'
   AND contracted_hours_under_zero_weight NOT LIKE '0hard/%';
