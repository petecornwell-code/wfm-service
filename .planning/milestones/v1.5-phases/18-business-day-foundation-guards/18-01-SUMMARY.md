---
phase: 18-business-day-foundation-guards
plan: 01
subsystem: database
tags: [flyway, jpa, spring-boot, desk-configuration, timeslot]

# Dependency graph
requires: []
provides:
  - "desk.day_start column (TIME NOT NULL DEFAULT '00:00') and Desk.dayStart entity field"
  - "timeslot.business_date column (DATE NOT NULL) and Timeslot.businessDate entity field, populated on both production write paths"
  - "PUT /api/v1/desks/{deskId}/day-start endpoint, 00:00-only gate"
affects: [18-02, 18-03, 18-05, 20-solver-business-date]

# Actuals (#2632) — pairs with the plan's estimate to calibrate future estimates.
actuals:
  tokens: 4790
  tasks: 3
  commits: 2
  plan_head_before: 55f86ea50e2f2af5917c5bf14c57bbcfdec8be25

tech-stack:
  added: []
  patterns:
    - "Tenant-scoped service mutation shape (null check, gate, findByIdAndTenantId, equal-value no-op, write) mirrored from DeskService.switchSchedulingMode"
    - "DERIVING vs PROPAGATING write-path classification for business_date: TimeslotGeneratorService derives, ScheduleService.acceptSchedule propagates a snapshot copy"

key-files:
  created:
    - src/main/resources/db/migration/V53__add_desk_day_start_and_timeslot_business_date.sql
    - src/main/java/com/wfm/dto/DayStartRequest.java
    - src/test/java/com/wfm/service/DeskServiceDayStartTest.java
  modified:
    - src/main/java/com/wfm/model/Desk.java
    - src/main/java/com/wfm/model/Timeslot.java
    - src/main/java/com/wfm/dto/DeskResponse.java
    - src/main/java/com/wfm/controller/DeskController.java
    - src/main/java/com/wfm/service/DeskService.java
    - src/main/java/com/wfm/service/TimeslotGeneratorService.java
    - src/main/java/com/wfm/service/ScheduleService.java
    - src/test/java/com/wfm/service/DeskServiceSchedulingModeTest.java
    - src/test/java/com/wfm/service/ScheduleServiceShiftSnapshotTest.java
    - src/test/java/com/wfm/service/ShiftLibraryGenerationCapConfigTest.java
    - src/test/java/com/wfm/service/ShiftLibraryGenerationServiceTest.java
    - src/test/java/com/wfm/service/ShiftLibraryValidationServiceTest.java

key-decisions:
  - "Task 1 checkpoint resolved as-specified: V53's three-statement, single-migration shape (ADD COLUMN nullable, UPDATE backfill from date, ALTER COLUMN SET NOT NULL) confirmed; timeslot.business_date is a stored, write-time-populated column, neither a Postgres generated column nor a lazily-computed getter."
  - "P-01/F-1 resolved as option (a): confirmOverride stripped entirely from DayStartRequest, DeskController.setDayStart, and DeskService.setDayStart rather than carried as inert dead surface."
  - "D-27's accepted-schedule refusal (ConflictException) is explicitly NOT added in this plan — the plan's own task 2 action scopes only the 84fdc3f gate + equal-value no-op into 18-01; the refusal and the frontend API client land in plan 18-02 per P-01's file allocation."

requirements-completed: [BDAY-01, BDAY-02]

coverage:
  - id: D1
    description: "A desk stores a declared business-day start, defaulting to 00:00 for every pre-existing row"
    requirement: "BDAY-01"
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/DeskServiceDayStartTest.java#createDesk_readsBackWithMidnightDayStart"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/DeskServiceDayStartTest.java#setDayStart_midnight_persistsAndReturnsDesk"
        status: pass
    human_judgment: false
  - id: D2
    description: "PUT /api/v1/desks/{deskId}/day-start accepts 00:00, refuses anything else, refuses null, and no-ops on an unchanged value"
    requirement: "BDAY-01"
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/DeskServiceDayStartTest.java#setDayStart_nonMidnightValue_throwsIllegalArgument_deskRowUnchanged"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/DeskServiceDayStartTest.java#setDayStart_nullValue_throwsIllegalArgumentWithMessage_persistsNothing"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/DeskServiceDayStartTest.java#setDayStart_equalToCurrentValue_returnsDeskUnchanged_isNoOp"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/DeskServiceDayStartTest.java#controller_setDayStart_returnsResponseWithDayStartPopulated"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/DeskServiceDayStartTest.java#controller_setDayStart_nonMidnightValue_throwsIllegalArgument"
        status: pass
    human_judgment: false
  - id: D3
    description: "A timeslot stores a business date, equal to its calendar date for every row after V53's backfill; both production write paths populate it and the full suite is green"
    requirement: "BDAY-02"
    verification:
      - kind: other
        ref: "./gradlew test (full suite, 152 test classes)"
        status: pass
    human_judgment: false
  - id: D4
    description: "No bypass parameter (confirmOverride) for the accepted-schedule refusal exists anywhere in the tree (F-1 option (a))"
    requirement: "BDAY-01"
    verification:
      - kind: other
        ref: "grep -rl 'confirmOverride' src/main src/test frontend/src | wc -l  ->  0"
        status: pass
    human_judgment: false
  - id: D5
    description: "V53 migration applied against a real Postgres schema, not just H2's derived one (deploy-window / lock-duration risk)"
    requirement: "BDAY-02"
    verification: []
    human_judgment: true
    rationale: "The default suite runs H2 with flyway.enabled: false, so V53 is never executed there (P-03). Real-Flyway proof against Postgres under ddl-auto: validate is plan 18-05's [BLOCKING] task, not this plan's."

duration: 26min
completed: 2026-09-30
status: complete
---

# Phase 18 Plan 1: Business-Day Foundation Tracer Summary

**Desk.dayStart and Timeslot.businessDate land end to end through V53, JPA, DTO, controller and service — the 00:00-only gate is the real enforcement, both production write paths populate business_date, and no bypass parameter for the accepted-schedule refusal exists anywhere in the tree.**

## Performance

- **Started:** 2026-09-30T14:24:22Z (phase execution start, per STATE.md)
- **Completed:** 2026-09-30T14:50:38Z
- **Duration:** ~26 min (continuation agent portion, from checkpoint resolution through commit)
- **Tasks:** 3 (1 checkpoint, 1 tracer, 1 auto)
- **Files modified:** 15 (3 created, 12 modified)

## Task 1 — Checkpoint: V53 migration and stored business-date column

**Resolved by human as: `as-specified`.**

The confirmed shape is V53's three DDL statements in one migration, in this order:

1. `ALTER TABLE desk ADD COLUMN day_start TIME NOT NULL DEFAULT '00:00'`
2. `ALTER TABLE timeslot ADD COLUMN business_date DATE` (nullable) — `UPDATE timeslot SET business_date = date` (backfill every existing row) — `ALTER TABLE timeslot ALTER COLUMN business_date SET NOT NULL` (tighten)

`timeslot.business_date` is a **stored, write-time-populated column** — explicitly neither a Postgres generated column (`GENERATED ALWAYS AS (date) STORED` was rejected because it cannot be assigned, which would force a later migration to drop and re-add it as writable) nor a lazily-computed getter (rejected on incremental-scoring performance and correctness grounds). The `defer-backfill` alternative (ship nullable, backfill in a later migration) was presented and explicitly NOT chosen — it would create an "unset" state every reader must handle, which is exactly the silent non-join failure mode this milestone exists to close.

**Deploy-window constraint (T-18-04, surfaced by the checkpoint and accepted by the human):** the backfill `UPDATE` rewrites every row of the live `timeslot` table and holds a lock for its duration. `dev` is the live system with real tenant data — there is no separate production tier. Deploying also terminates any solve in flight, because solver state is heap-only. **V53 must be applied in a window with no running solve**, not alongside one. This plan does not itself deploy V53; it is recorded here as the constraint the eventual deploy must respect.

This task made no code changes and has no commit of its own — its output is this recorded decision.

## Accomplishments

- V53 migration created with the exact three-statement, no-nullable-window shape (`desk.day_start`, `timeslot.business_date`)
- `Desk.dayStart` and `Timeslot.businessDate` entity fields added, mirroring existing field/accessor conventions
- `DayStartRequest` DTO created as a single-field record — `confirmOverride` never landed (P-01/F-1)
- `PUT /api/v1/desks/{deskId}/day-start` endpoint added; `DeskResponse.dayStart` appended
- `DeskService.setDayStart`: null check, 00:00-only gate, tenant-scoped lookup, equal-value no-op — javadoc names the 15-minute-boundary target range BDAY-04 opens onto
- `DeskServiceDayStartTest` created: gate, null refusal, equal-value no-op, controller round trip (7 tests, all passing)
- `TimeslotGeneratorService` (sole deriving writer) and `ScheduleService.acceptSchedule` (sole propagating writer) both populate `business_date`
- Six persisting `Timeslot` fixtures across five test files updated to set `business_date` alongside their existing calendar-date write — no persisting fixture beyond these five named files needed the line
- Full suite green: 152 test classes, 0 failures, 0 errors

## Task Commits

Each task was committed atomically:

1. **Task 1: Checkpoint (V53/business-date confirmation)** — no commit (decision-only, recorded above)
2. **Task 2: End-to-end desk day-start tracer** — `81f22e3` (feat)
3. **Task 3: Populate business_date on both write paths** — `1cd52e2` (feat)

**Plan metadata:** commit pending (this SUMMARY + STATE.md/ROADMAP.md update)

## Files Created/Modified

- `src/main/resources/db/migration/V53__add_desk_day_start_and_timeslot_business_date.sql` - three-statement migration (add desk.day_start, backfill + tighten timeslot.business_date)
- `src/main/java/com/wfm/model/Desk.java` - `dayStart` field (`LocalTime.MIDNIGHT` default) + accessors
- `src/main/java/com/wfm/model/Timeslot.java` - `businessDate` field (no Java default) + accessors
- `src/main/java/com/wfm/dto/DayStartRequest.java` - single-field record `DayStartRequest(LocalTime dayStart)`
- `src/main/java/com/wfm/dto/DeskResponse.java` - `dayStart` appended as 6th component
- `src/main/java/com/wfm/controller/DeskController.java` - `PUT /{deskId}/day-start` endpoint
- `src/main/java/com/wfm/service/DeskService.java` - `setDayStart(UUID, LocalTime)` method
- `src/test/java/com/wfm/service/DeskServiceDayStartTest.java` - gate/null/no-op/round-trip tests
- `src/main/java/com/wfm/service/TimeslotGeneratorService.java` - deriving write of `business_date` in the generation loop
- `src/main/java/com/wfm/service/ScheduleService.java` - propagating write of `business_date` in the accept-schedule snapshot copy
- `src/test/java/com/wfm/service/DeskServiceSchedulingModeTest.java` - `saveTimeslot` helper sets `business_date`
- `src/test/java/com/wfm/service/ScheduleServiceShiftSnapshotTest.java` - two call sites (`liveTimeslot`, `saveTimeslots` helper) set `business_date`
- `src/test/java/com/wfm/service/ShiftLibraryGenerationCapConfigTest.java` - `saveDemand` helper sets `business_date`
- `src/test/java/com/wfm/service/ShiftLibraryGenerationServiceTest.java` - `saveTimeslot` helper sets `business_date`
- `src/test/java/com/wfm/service/ShiftLibraryValidationServiceTest.java` - `saveTimeslot` helper sets `business_date`

## Decisions Made

- Task 1 checkpoint resolved `as-specified` (see above) — locked D-09/D-10 confirmed, not relitigated.
- P-01/F-1 resolved as option (a): `confirmOverride` stripped entirely rather than kept as inert dead surface, per the planner's binding decision.
- Per the plan's own task 2 action text, D-27's accepted-schedule refusal (`ConflictException`) was deliberately **not** added in this plan — only 84fdc3f's gate + equal-value no-op landed here. The refusal and the frontend API client are explicitly plan 18-02's scope (P-01: "Three land in this plan; the refusal and the API client land in plan 18-02").
- V53's header/body comments were rewritten to remove `D-18`/`D-22`/`D-24` decision-ID citations that the verbatim salvaged text carried, replacing them with requirement-ID-based reasoning (BDAY-04, BDAY-08, BDAY-03) — required by D-24 and by the task's own explicit acceptance criterion checking the diff for `D-[0-9]+` citations (see Deviations below).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] V53's copied comment text violated its own task's acceptance criterion (decision-ID citations in the diff)**
- **Found during:** Task 2 (V53 migration creation)
- **Issue:** 18-PATTERNS.md's "verbatim" V53 SQL content (and the analog commit `84fdc3f`) actually contains `(D-18)`, `per D-24)`, and `D-22:` citations in its comments, despite PATTERNS.md's own claim that "the content above already avoids `D-nn` citations ... it has none — verify at copy time." That claim is incorrect for the content as shown. Task 2's own acceptance criterion explicitly checks `git diff ... | grep -cE '^\+.*\bD-[0-9]+\b'` prints `0`, and D-24 (cited directly in the task's `<action>` text) requires requirement IDs rather than decision-document identifiers in every comment this task writes or copies.
- **Fix:** Rewrote the SQL header comments to remove `D-18`/`D-22`/`D-24` citations, replacing them with plain reasoning and requirement IDs (BDAY-04, BDAY-08, BDAY-03), while preserving the three-statement/no-nullable-window shape and reasoning verbatim in substance. Kept the "Phase 18:" title-line convention, which matches every other recent migration file's header (V47-V51) and is not caught by the `D-[0-9]+` grep.
- **Files modified:** `src/main/resources/db/migration/V53__add_desk_day_start_and_timeslot_business_date.sql`
- **Verification:** `grep -cE '^\+.*\bD-[0-9]+\b' <diff>` prints `0`; all other V53 acceptance criteria (3 ALTER, 1 UPDATE, ordering, 0 GENERATED ALWAYS) still pass.
- **Committed in:** `81f22e3` (Task 2 commit)

**2. [Rule 1 - Bug] Stale, cross-session-leaked commit ledger for the actuals/commits count**
- **Found during:** SUMMARY authoring (commit-count measurement, protocol step 0c)
- **Issue:** `.git/gsd-plan-head-before-18-01` already existed on disk, timestamped 2026-09-29T19:31 — leftover from the original `rescue/phase-18-unwind-20260930` build session, not this plan's actual execution. It pointed to a commit (`35929d1`, "docs(18): create phase plan") that is **not an ancestor of current HEAD** (that branch was unwound), which would have inflated the measured commit count to 15 instead of the actual 2.
- **Fix:** Verified `35929d1` is unreachable from HEAD via `git merge-base --is-ancestor`; verified `55f86ea` (the confirmed pre-plan HEAD, matching the continuation prompt's "No commits exist for this plan" state) IS an ancestor; overwrote the stale ledger file with `55f86ea`.
- **Files modified:** none (internal `.git/` bookkeeping file, not a tracked artifact)
- **Verification:** `git rev-list --count 55f86ea..HEAD` now correctly reports `2`, matching the two task commits actually made this session.
- **Committed in:** n/a (not a tracked file)

**3. [Rule 1 - Bug] Task 3's literal acceptance-criterion grep cannot distinguish setter definition from setter call sites**
- **Found during:** Task 3 acceptance criteria verification
- **Issue:** The criterion `grep -rc 'setBusinessDate(' src/main/java --include='*.java' reports a non-zero count for exactly two files: TimeslotGeneratorService.java and ScheduleService.java` is unsatisfiable as literally written once `Timeslot.java`'s own setter method (added correctly in Task 2, per the plan's own spec) exists — `public void setBusinessDate(LocalDate businessDate) { ... }` textually contains the searched string, so `grep -rc` reports a non-zero count for three files, not two.
- **Fix:** No code change — the implementation is correct per both tasks' explicit specs. Verified the true intent (exactly two files that *call*, as opposed to *declare*, the setter) holds: `TimeslotGeneratorService.java` and `ScheduleService.java` are the only two call sites under `src/main/java`.
- **Files modified:** none
- **Verification:** Manually distinguished declaration from call sites; both intended call sites confirmed present and no third call site exists.
- **Committed in:** n/a (documentation-only deviation, no code change required)

---

**Total deviations:** 3 auto-fixed (2 Rule 1 bugs in copied/inherited planning content, 1 Rule 1 stale-state correction)
**Impact on plan:** All three are corrections to planning-artifact inconsistencies (a mis-verified "no D-nn citations" claim, a stale cross-session git-dir file, and an unsatisfiable literal grep against a correctly-implemented setter). No scope creep; no behavior change; no weakening of any guard or assertion.

## Issues Encountered

None beyond the three deviations documented above.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- V53, `Desk.dayStart`, `Timeslot.businessDate`, the 00:00-only gate, and both `business_date` write paths are in place; full suite green.
- Plan 18-02 is next: it adds D-27's accepted-schedule refusal (`ConflictException`, unconditional, no bypass) to `DeskService.setDayStart`, the frontend API client, and `DeskManagement.tsx`'s read-only Day Start column.
- **Deploy-window constraint carried forward:** V53 has not been deployed to `dev` by this plan. Before it is deployed, confirm no solve is running on the target desk(s) — the backfill `UPDATE` locks the live `timeslot` table for its duration and any deploy terminates an in-flight solve regardless (solver state is heap-only).
- Plan 18-05 owns the real-Flyway proof (V53 exercised against Postgres under `ddl-auto: validate`, via `MidnightTimeslotPostgresTest`'s precedent) — not yet done; the default H2 suite never executes this migration.

---
*Phase: 18-business-day-foundation-guards*
*Completed: 2026-09-30*

## Self-Check: PASSED

- `src/main/resources/db/migration/V53__add_desk_day_start_and_timeslot_business_date.sql` - FOUND
- `src/main/java/com/wfm/dto/DayStartRequest.java` - FOUND
- `src/test/java/com/wfm/service/DeskServiceDayStartTest.java` - FOUND
- `.planning/phases/18-business-day-foundation-guards/18-01-SUMMARY.md` - FOUND
- Commit `81f22e3` - FOUND in `git log --oneline --all`
- Commit `1cd52e2` - FOUND in `git log --oneline --all`
- All task-level `<acceptance_criteria>` re-verified (see Deviations for the 2 literal-grep discrepancies, both confirmed non-defects)
- Plan-level `<verification>` re-run: `./gradlew test` green (152/152), `./gradlew compileJava compileTestJava` clean, `confirmOverride` count 0, exactly two call sites populate `business_date`, no migration file other than V53 differs from HEAD
