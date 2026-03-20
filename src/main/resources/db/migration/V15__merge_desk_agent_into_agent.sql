-- Merge desk_agent fields into agent table, then drop desk_agent.
-- After this migration, agent is the single entity for both tenant-level
-- and desk-level concerns. desk_id IS NULL means the agent is unassigned.

-- 1. Add desk-specific columns to agent
ALTER TABLE agent
    ADD COLUMN desk_id UUID REFERENCES desk(id) ON DELETE SET NULL,
    ADD COLUMN primary_specialization_id UUID REFERENCES specialization(id) ON DELETE SET NULL,
    ADD COLUMN contracted_hours_per_day NUMERIC(5,2);

-- 2. Migrate data from desk_agent into agent
UPDATE agent a
SET desk_id = da.desk_id,
    primary_specialization_id = da.primary_specialization_id,
    contracted_hours_per_day = da.contracted_hours_per_day
FROM desk_agent da
WHERE da.agent_id = a.id;

-- 3. Create agent_secondary_specialization from desk_agent_secondary_specialization
CREATE TABLE agent_secondary_specialization (
    agent_id            UUID NOT NULL REFERENCES agent(id) ON DELETE CASCADE,
    specialization_id   UUID NOT NULL REFERENCES specialization(id) ON DELETE CASCADE,
    PRIMARY KEY (agent_id, specialization_id)
);

INSERT INTO agent_secondary_specialization (agent_id, specialization_id)
SELECT da.agent_id, dass.specialization_id
FROM desk_agent_secondary_specialization dass
JOIN desk_agent da ON da.id = dass.desk_agent_id;

-- 4. Update agent_assignment: copy desk_agent_id references to agent_id
UPDATE agent_assignment aa
SET agent_id = da.agent_id
FROM desk_agent da
WHERE aa.desk_agent_id = da.id AND aa.agent_id IS NULL;

-- 5. Drop the desk_agent_id column from agent_assignment BEFORE dropping desk_agent
--    (the FK constraint agent_assignment_desk_agent_id_fkey blocks the DROP TABLE)
ALTER TABLE agent_assignment DROP COLUMN desk_agent_id;

-- 6. Drop desk_agent_secondary_specialization and desk_agent tables
DROP TABLE desk_agent_secondary_specialization;
DROP TABLE desk_agent;

-- 7. Add indexes for the new columns on agent
CREATE INDEX idx_agent_desk ON agent(tenant_id, desk_id) WHERE desk_id IS NOT NULL;
CREATE UNIQUE INDEX idx_agent_tenant_desk ON agent(tenant_id, desk_id, id) WHERE desk_id IS NOT NULL;
