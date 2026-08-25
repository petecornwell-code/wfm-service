-- Phase 14: Shift Library & Scheduling Mode.
--
-- 1. New table `shift_template` — a desk-scoped library of allowed shifts (structural sibling of
--    `specialization`: own table, desk_id FK, tenant-scoped, no nesting inside `desk`).
--
--    Retirement is expressed by `effective_from`/`effective_to` ALONE (D-10). There is
--    deliberately no `is_active`/`enabled`/`retired` column: this project has been burned twice
--    by two fields that can disagree about the same fact (audit NEW-1 — the legacy
--    contractedHoursPerDay scalar vs. the per-day columns; audit I-1 — model vs. view). A second
--    retirement mechanism alongside the date range would be the same shape of trap. One
--    mechanism, one predicate: a template applies to date D iff
--    effective_from <= D <= effective_to (NULL effective_to = open-ended).
--
--    Identity is UNIQUE (tenant_id, desk_id, name, effective_from) — D-11, LOCKED and one-way:
--    by the time it matters, Phase 15's agent_shift_assignment FK and Phase 16's
--    agent_usual_shift FK both point at these rows. This widens `specialization`'s
--    (tenant_id, desk_id, name) key with effective_from so the same name ("S1") can have more
--    than one era.
--
--    The same-name effective-range non-overlap invariant (also required by D-11) is enforced
--    APPLICATION-LEVEL in ShiftTemplateService, not by a DB constraint — checkpoint decision
--    recorded in 14-01-SUMMARY.md. Portable across H2 (@DataJpaTest) and Postgres, avoids a
--    `CREATE EXTENSION btree_gist` inside this one-way migration, and produces a named,
--    operator-readable validation error via the existing ConflictException path instead of an
--    opaque constraint-violation stack trace. This migration therefore adds only the identity
--    UNIQUE constraint below — no EXCLUDE USING gist, no CHECK constraint for overlap.
--
-- 2. `desk.scheduling_mode` — a per-desk SLOT/SHIFT flag (MODE-01), NOT NULL DEFAULT 'SLOT' so no
--    desk row can ever hold a null mode and every pre-existing desk backfills to slot-scheduled
--    (the behaviour every desk already has today). Nothing in the solve path reads this column
--    yet (Phase 15 adds that) — MODE-05 holds structurally because a column nothing reads cannot
--    change behaviour.

CREATE TABLE shift_template (
    id UUID PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    desk_id UUID NOT NULL REFERENCES desk(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    break_offset_minutes INTEGER NOT NULL DEFAULT 0,
    break_duration_minutes INTEGER NOT NULL DEFAULT 0,
    valid_weekdays CHAR(7) NOT NULL,
    effective_from DATE NOT NULL,
    effective_to DATE,
    UNIQUE (tenant_id, desk_id, name, effective_from)
);

ALTER TABLE desk ADD COLUMN scheduling_mode VARCHAR(10) NOT NULL DEFAULT 'SLOT';
