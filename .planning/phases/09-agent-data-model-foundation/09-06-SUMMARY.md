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
  - "V29__agent_first_last_name_and_day_hours.sql: additive DDL + name-split backfill + scalar fan-out (NOT YET verified against real Postgres)"
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

requirements-completed: []  # MDL-03 is NOT complete — Task 2 (manual migration dry-run) is a blocking checkpoint, not yet approved. Do not mark MDL-03 done until a human runs and approves Task 2.

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

## Self-Check: PASSED

Created file verified present on disk:
- FOUND: src/main/resources/db/migration/V29__agent_first_last_name_and_day_hours.sql

Commit hash verified present in git log:
- FOUND: 55c5c31 (feat(09-06): add V29 migration for name split + agent_day_hours fan-out)

---
*Phase: 09-agent-data-model-foundation*
*Status: PAUSED at Task 2 checkpoint (not complete)*
