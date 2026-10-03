-- Phase 22: Minimum Rest (REST-01, REST-02, REST-04, REST-06). All five DDL changes this phase
-- needs land in one atomic migration (22-RESEARCH.md Discretion Resolution 9a) -- later plans add
-- the entities and readers; a table or column with no reader is inert, this project's established
-- additive-first order.
--
-- 1/2. desk.minimum_rest_minutes and schedule.minimum_rest_minutes are both deliberately NULLable
-- with NO DEFAULT clause -- unlike V53's day_start column, this is NOT the add-nullable/backfill/
-- SET-NOT-NULL idiom. NULL is the unambiguous "no legal minimum is being assumed" state REST-04
-- depends on (research/FEATURES.md:150: there must be no baked-in default). An unconfigured desk
-- must solve byte-identically to today, and a schedule snapshots whatever its desk held at solve
-- time (D-14) -- a later desk edit cannot retroactively change what an already-scored schedule was
-- measured against, the same reasoning 21-CONTEXT D-15 established for day_start.
ALTER TABLE desk ADD COLUMN minimum_rest_minutes INTEGER;
ALTER TABLE schedule ADD COLUMN minimum_rest_minutes INTEGER;

-- 3. agent_rest_waiver (D-07): "waived" is the presence of a row, so this table carries no hours
-- column of any kind -- deliberately NOT a widened agent_exception, which is a contracted-hours
-- override channel whose NOT NULL hours column and three unconditional lookup sites would have had
-- to grow a null-hours branch at each one, and whose day-off-coincidence refusal would wrongly
-- block a rest waiver on a date with no shift to rest from. Mirrors agent_exception's column types
-- and its (tenant_id, desk_id, agent_id, date) unique constraint exactly (see V1__initial_schema.sql).
CREATE TABLE agent_rest_waiver (
    id          UUID PRIMARY KEY,
    tenant_id   BIGINT NOT NULL,
    desk_id     UUID NOT NULL REFERENCES desk(id) ON DELETE CASCADE,
    agent_id    UUID NOT NULL REFERENCES agent(id) ON DELETE CASCADE,
    date        DATE NOT NULL,
    reason      TEXT NOT NULL,
    UNIQUE (tenant_id, desk_id, agent_id, date)
);

-- 4/5. Constraint weights for both mode-gated rest constraints (D-03: two constraints, not one
-- fused stream). Hard at 1000 -- the same tier as no_overlap_weight and non_working_day_seat_weight
-- ("this schedule is operationally illegal", not "low quality"), deliberately below
-- agent_day_off_weight's 10000 hard. minimum_rest_slot_weight lands now, unread until plan 22-02,
-- so that plan needs no second migration.
ALTER TABLE constraint_weights
    ADD COLUMN minimum_rest_shift_weight VARCHAR(50) NOT NULL DEFAULT '1000hard/0soft';
ALTER TABLE constraint_weights
    ADD COLUMN minimum_rest_slot_weight VARCHAR(50) NOT NULL DEFAULT '1000hard/0soft';
