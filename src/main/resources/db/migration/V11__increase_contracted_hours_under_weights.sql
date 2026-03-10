-- Increase contracted_hours_under and contracted_hours_under_zero weights
-- from 1hard to 100hard to match the updated Java defaults in ConstraintWeights.
-- At 1hard these constraints were too weak relative to other constraints,
-- causing the solver to leave agents underassigned rather than filling their
-- contracted hours.

UPDATE constraint_weights
   SET contracted_hours_under_weight = '100hard/0soft'
 WHERE contracted_hours_under_weight = '1hard/0soft';

UPDATE constraint_weights
   SET contracted_hours_under_zero_weight = '100hard/0soft'
 WHERE contracted_hours_under_zero_weight = '1hard/0soft';
