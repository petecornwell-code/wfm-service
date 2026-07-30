-- Migrate existing agent data to the new per-field name and per-day-hours model
-- (MDL-03, "the highest-risk change" in v1.2): add first_name/last_name and
-- backfill by splitting the existing name, then create agent_day_hours and
-- fan each agent's non-null scalar contracted_hours_per_day out to all 7
-- weekday rows. contracted_hours_per_day itself is NOT dropped here -- it is
-- retired only in a later phase (D-05).

-- 1. New name columns on agent (mirrors V15 step-numbering style)
ALTER TABLE agent
    ADD COLUMN first_name VARCHAR(255),
    ADD COLUMN last_name VARCHAR(255);

-- 2. Backfill name split (D-06: first whitespace token -> first_name, remainder -> last_name).
--    Must reproduce AgentNameSplitter.split(...) exactly: single-token names get an
--    empty (never null) last_name; multi-token names put the trimmed remainder in last_name.
UPDATE agent
SET first_name = split_part(name, ' ', 1),
    last_name = CASE
        WHEN position(' ' IN name) > 0
        THEN trim(substring(name FROM position(' ' IN name) + 1))
        ELSE ''
    END;

-- 3. New child table (D-09) -- mirrors agent_day_off / agent_exception FK+unique style.
--    Row absence = "no data" (schedule default applies); a present row with 0.00
--    hours means the agent does not work that weekday.
CREATE TABLE agent_day_hours (
    id          UUID PRIMARY KEY,
    tenant_id   BIGINT NOT NULL,
    agent_id    UUID NOT NULL REFERENCES agent(id) ON DELETE CASCADE,
    day_of_week VARCHAR(9) NOT NULL,
    hours       NUMERIC(5,2) NOT NULL,
    UNIQUE (agent_id, day_of_week)
);

-- 4. Fan out each existing agent's non-null scalar contracted_hours_per_day to all
--    7 weekday rows (D-01). This exactly reproduces today's getEffectiveHours
--    behaviour for every agent that has a scalar set. gen_random_uuid() is a
--    PostgreSQL 13+ core builtin (confirmed: engine_version 16.6 in infra/rds.tf)
--    -- no CREATE EXTENSION needed, unlike V24's pgvector.
INSERT INTO agent_day_hours (id, tenant_id, agent_id, day_of_week, hours)
SELECT gen_random_uuid(), a.tenant_id, a.id, dow.name, a.contracted_hours_per_day
FROM agent a
CROSS JOIN (VALUES ('MONDAY'), ('TUESDAY'), ('WEDNESDAY'), ('THURSDAY'),
                    ('FRIDAY'), ('SATURDAY'), ('SUNDAY')) AS dow(name)
WHERE a.contracted_hours_per_day IS NOT NULL;
-- NULL-scalar agents (D-02) intentionally get zero rows -- no INSERT for them.
-- They keep resolving via the schedule's defaultContractedHoursPerDay fallback.
