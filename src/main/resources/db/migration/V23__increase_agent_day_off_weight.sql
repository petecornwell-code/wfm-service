-- Increase agent day off constraint weight to 10000hard so the solver
-- never schedules agents on their PTO/day-off dates.
-- Previous default (1hard or 0/0) was too low relative to competing
-- constraints like contracted hours under (100hard), allowing the solver
-- to trade off PTO violations for staffing coverage.
UPDATE constraint_weights
   SET agent_day_off_weight = '10000hard/0soft';
