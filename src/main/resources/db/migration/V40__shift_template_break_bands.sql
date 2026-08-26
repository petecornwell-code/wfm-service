-- Phase 15 Plan 01: Shift Envelope, Breaks & Library Generation -- break bands (D-01).
--
-- Phase 14's shift_template.break_offset_minutes/break_duration_minutes gave every agent on a
-- shift exactly one shared break window -- on the live StubHub (EN) library ~15 Early-shift
-- agents all break 12:00-13:00, starving that hour's demand. This migration promotes the break
-- from a single fixed offset to N break bands (a child table), so the solver has bands to
-- distribute an agent's break across (ENVL-08/09).
--
-- D-01 (15-CONTEXT.md): bands become primary and the singular columns are DROPPED, not kept
-- alongside -- the same audit-NEW-1 shape (two fields that can disagree about one fact) this
-- project has already been burned by twice, and the same trap Phase 14's D-10 refused when it
-- rejected an `active` boolean beside the effective-date range. One mechanism, one predicate.
--
-- Three explicit statements, forward-only, no PL/pgSQL procedural block -- matching every
-- migration V2 through V39 (pure DDL/DML):
--   1. CREATE the child table. `capacity` is nullable -- D-03: blank/null means unlimited.
--   2. INSERT one band per existing template whose break_duration_minutes > 0, carrying its
--      offset and duration forward with a NULL (unlimited) capacity. A template whose break
--      duration is zero deliberately receives ZERO bands, preserving Phase 14's "no break"
--      affordance as zero rows rather than a zero-duration row.
--   3. DROP the two retired columns.
--
-- gen_random_uuid() is a Postgres 13+ builtin needing no extension. This exact SQL never executes
-- under test: src/test/resources/application-test.yml sets flyway.enabled: false with
-- ddl-auto: create-drop against H2, so the test schema is built from the entities and migration
-- SQL never runs (UAT gap G-14-1, unchanged by this migration). MigrationEntityConsistencyTest
-- (this plan's Task 3) is what stands in for the absent execution -- it reconciles this file's
-- declared columns and SQL types against the entity mappings without a database, closing exactly
-- the class of drift (CHAR vs varchar) that shipped in V39 with a fully green test suite.

CREATE TABLE shift_template_break_band (
    id UUID PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    shift_template_id UUID NOT NULL REFERENCES shift_template(id) ON DELETE CASCADE,
    offset_minutes INTEGER NOT NULL,
    duration_minutes INTEGER NOT NULL,
    capacity INTEGER
);

INSERT INTO shift_template_break_band (id, tenant_id, shift_template_id, offset_minutes, duration_minutes, capacity)
SELECT gen_random_uuid(), tenant_id, id, break_offset_minutes, break_duration_minutes, NULL
FROM shift_template
WHERE break_duration_minutes > 0;

ALTER TABLE shift_template DROP COLUMN break_offset_minutes;
ALTER TABLE shift_template DROP COLUMN break_duration_minutes;
