-- Replace required_agents (int) with required_hours (decimal) on staffing_requirement.
-- required_hours represents the total staff-hours of coverage needed for a timeslot+specialization.
-- The solver converts hours to agent seats at solve time using the timeslot increment.

-- 1. Add the new column with a default so existing rows are valid
ALTER TABLE staffing_requirement ADD COLUMN required_hours DECIMAL(10,4) NOT NULL DEFAULT 0;

-- 2. Back-fill from existing data: hours = agents × timeslot_duration_in_hours
UPDATE staffing_requirement sr
SET required_hours = sr.required_agents
    * EXTRACT(EPOCH FROM (t.end_time - t.start_time)) / 3600.0
FROM timeslot t
WHERE t.id = sr.timeslot_id;

-- 3. Drop the old column
ALTER TABLE staffing_requirement DROP COLUMN required_agents;

-- 4. Remove the default now that migration is complete
ALTER TABLE staffing_requirement ALTER COLUMN required_hours DROP DEFAULT;
