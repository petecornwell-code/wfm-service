-- Rename required_hours to required_ftes (integer FTE count per timeslot).
-- Back-fill: convert hours to agent count using the timeslot duration.
-- For a 60-min slot, 8 hours = 8 agents; for 15-min slots, 2 hours = 8 agents, etc.

ALTER TABLE staffing_requirement ADD COLUMN required_ftes INT;

UPDATE staffing_requirement sr
SET required_ftes = CEIL(
    sr.required_hours * 60.0 / (
        EXTRACT(EPOCH FROM (t.end_time - t.start_time)) / 60.0
    )
)
FROM timeslot t
WHERE sr.timeslot_id = t.id;

-- Default any NULLs (orphan rows) to 0
UPDATE staffing_requirement SET required_ftes = 0 WHERE required_ftes IS NULL;

ALTER TABLE staffing_requirement ALTER COLUMN required_ftes SET NOT NULL;
ALTER TABLE staffing_requirement DROP COLUMN required_hours;
