---
phase: 09-agent-data-model-foundation
plan: 06
subsystem: database
tags: [flyway, postgres, migration, jpa, agent-model]

# Dependency graph
requires:
  - phase: 09-agent-data-model-foundation (plan 01)
    provides: AgentNameSplitter.split(displayName) D-06 rule, Agent.firstName/lastName columns
  - phase: 09-agent-data-model-foundation (plan 02)
    provides: AgentDayHours JPA entity + AgentDayHoursRepository (agent_day_hours table shape)
provides:
  - "V29__agent_first_last_name_and_day_hours.sql: additive DDL + name-split backfill + scalar fan-out (VERIFIED against seeded postgres:16 on 2026-07-30; name-split hardened to match AgentNameSplitter)"
affects: [09-07-onward (any plan reading agent_day_hours/first_name/last_name from a real DB), /gsd-verify-work]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Flyway multi-step numbered-comment migration mirroring V15 (ALTER + UPDATE + CREATE TABLE + INSERT) and V28 (explanatory-comment) styles"
    - "gen_random_uuid() PG16 core builtin used for DB-side UUID generation in a bulk fan-out INSERT (first migration of this shape in the repo)"

key-files:
  created:
    - src/main/resources/db/migration/V29__agent_first_last_name_and_day_hours.sql
  modified: []

key-decisions:
  - "Migration structure (4 numbered steps) follows 09-PATTERNS.md's verified recommended shape verbatim: no deviation needed"
  - "contracted_hours_per_day scalar column is retained, not dropped (D-05 defers the drop to a later phase)"

patterns-established: []

requirements-completed: [MDL-03]  # Task 2 manual dry-run run against seeded postgres:16 on 2026-07-30; passed after hardening the name-split backfill (see Checkpoint Resolution).

# Metrics
duration: 12min
completed: 2026-07-30
---

# Phase 9 Plan 06: V29 Migration (Name Split + Agent Day Hours Fan-Out) Summary

**V29 Flyway migration SQL written and committed (name-split backfill + agent_day_hours fan-out); plan is PAUSED at a mandatory manual data-integrity checkpoint that has not yet been run.**

## Performance

- **Duration:** ~12 min (autonomous portion only; Task 2 checkpoint time not included, awaiting human)
- **Started:** 2026-07-30T10:30:00-04:00 (approx)
- **Completed (Task 1 only):** 2026-07-30T10:42:54-04:00
- **Tasks:** 1 of 2 completed (Task 2 is a blocking `checkpoint:human-verify`)
- **Files modified:** 1 (new migration file)

## Accomplishments
- `V29__agent_first_last_name_and_day_hours.sql` written and committed: adds `first_name`/`last_name` to `agent`, backfills them via `split_part`/`position`/`substring` reproducing `AgentNameSplitter`'s D-06 rule exactly, creates `agent_day_hours` (tenant-scoped, `UNIQUE(agent_id, day_of_week)`, `NUMERIC(5,2) NOT NULL hours`), and fans each agent's non-null `contracted_hours_per_day` scalar out to all 7 weekday rows via `gen_random_uuid()` + `CROSS JOIN` over weekday literals, guarded by `WHERE a.contracted_hours_per_day IS NOT NULL`
- Automated structural verification passed: file exists, contains `CREATE TABLE agent_day_hours`, `split_part`, `contracted_hours_per_day IS NOT NULL`, `gen_random_uuid`; does not contain `DROP COLUMN` or a `CREATE EXTENSION` statement
- **NOT accomplished (by design):** Task 2's mandatory manual dry-run against a real/seeded Postgres has NOT been run. This SUMMARY does not claim MDL-03 is verified or complete.

## Task Commits

1. **Task 1: Write V29 migration (name columns + split backfill + agent_day_hours fan-out)** - `55c5c31` (feat)

**Task 2 (Manual migration dry-run, MDL-03 data-integrity gate): NOT STARTED.** This is a `checkpoint:human-verify` task with `gate="blocking"` — it requires a real/seeded Postgres instance and cannot be performed by an autonomous agent. See "Checkpoint Status" below.

## Files Created/Modified
- `src/main/resources/db/migration/V29__agent_first_last_name_and_day_hours.sql` - New Flyway migration: name-split backfill (D-06) + `agent_day_hours` table (D-09) + non-null-scalar fan-out (D-01/D-02); does not touch `contracted_hours_per_day` (D-05)

## Decisions Made
- Followed `09-PATTERNS.md`'s verified recommended V29 SQL shape verbatim — no deviation from the researched/verified structure

## Deviations from Plan

None - Task 1 executed exactly as written and structurally verified.

## Checkpoint Status

**This plan is PAUSED, not complete.** Task 2 is a mandatory `checkpoint:human-verify` (`gate="blocking"`) that requires a human to run the migration dry-run against a real or seeded Postgres 16 instance — this cannot be automated or self-approved by this agent. See the CHECKPOINT REACHED block in this agent's final response for the exact verification steps, acceptance criteria, and resume signal.

**`requirements-completed` above is intentionally empty.** MDL-03 must not be marked complete until Task 2 is run and approved by a human with the row-count/split evidence described in the plan.

## Issues Encountered

The worktree's initial branch (`worktree-agent-af590a347729c4ea4`) was based on a stale commit that predated the phase-09 planning commits (phase 09 directories were entirely absent). Corrected via `git reset --hard` to the expected base commit `d1791274fd90a0f831e0ef3cbbe5497ca97d8f71` per the mandatory branch-check protocol, before any file reads or edits.

## User Setup Required

**Required before this plan can be marked complete:**
1. Start a local Postgres 16 (e.g. `docker run postgres:16`) or use a disposable copy of dev RDS data.
2. Seed representative agent rows before V29: one with non-null `contracted_hours_per_day`, one with NULL scalar, one single-token name (e.g. "Alice"), one multi-word name (e.g. "Mary Jane Watson").
3. Run migrations through V29 (`flyway migrate`, or the app pointed at that DB with Flyway enabled).
4. Assert `agent_day_hours` row count == 7 × count(agents with non-null scalar); NULL-scalar agents have 0 rows; every fanned-out agent has exactly 7 rows equal to its original scalar.
5. Spot-check name splits against `AgentNameSplitter` output for the same inputs.
6. Report results (approved or discrepancy) to resume this plan.

## Next Phase Readiness

- V29 migration SQL is written, structurally verified, and committed — ready for the manual dry-run
- This plan (and MDL-03) cannot be marked complete, and `/gsd-verify-work` should not run for phase 9, until Task 2 is approved
- No other phase 9 plan is blocked by this pause — 09-01 through 09-05 are already complete per their own SUMMARYs

## Checkpoint Resolution (2026-07-30) — VERIFIED + HARDENED

Task 2's mandatory manual dry-run was executed against a throwaway `postgres:16`
(16.14) Docker container (matches prod RDS 16.6). A minimal pre-V29 `agent` table
(id/tenant_id/name/contracted_hours_per_day, real types) was seeded with 9
representative rows and V29 was run end-to-end (`ON_ERROR_STOP=1`, exit 0 — no
`gen_random_uuid` extension error).

**Fan-out (D-01/D-02): PASS, no changes needed.**
- `agent_day_hours` rows = 49 = 7 × 7 non-null-scalar agents (exact).
- Each non-null-scalar agent has exactly 7 rows across 7 distinct weekdays, all
  equal to its original scalar (0 mismatches). Grace (scalar `0.00`, non-null)
  correctly gets 7 rows of `0.00`.
- NULL-scalar agents (Bob Smith, Cher) get 0 rows.

**Name split (D-06): FAILED as written, FIXED.** Cross-checking the SQL output
against the real `AgentNameSplitter.split()` (run on the same 9 inputs) revealed
two divergences, both because the original SQL split the raw `name` while the Java
utility trims first and coalesces null/blank to empty:
- `' Leading Space'` → SQL gave `('', 'Leading Space')`; Java gives `('Leading','Space')`.
- `NULL` name → SQL gave `(NULL, '')`; Java gives `('','')`.

Fixed in commit `6af92bd` by normalising once via
`trim(both E' \t\n\r' from coalesce(name,''))` in a subquery before
`split_part`/`position`/`substring`. Re-ran the full dry-run: SQL name split now
matches `AgentNameSplitter` on **all 9** inputs, and the fan-out re-verified
identical (49=49, 0 mismatches). Container torn down after verification.

## Self-Check: PASSED

Created file verified present on disk:
- FOUND: src/main/resources/db/migration/V29__agent_first_last_name_and_day_hours.sql

Commit hash verified present in git log:
- FOUND: 55c5c31 (feat(09-06): add V29 migration for name split + agent_day_hours fan-out)

---
*Phase: 09-agent-data-model-foundation*
*Status: PAUSED at Task 2 checkpoint (not complete)*
