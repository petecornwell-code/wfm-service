---
phase: 18-business-day-foundation-guards
reviewed: 2026-09-30T00:00:00Z
depth: standard
files_reviewed: 39
files_reviewed_list:
  - build.gradle
  - frontend/src/api/client.ts
  - frontend/src/pages/DeskManagement.tsx
  - src/main/java/com/wfm/controller/DeskController.java
  - src/main/java/com/wfm/controller/TimeslotController.java
  - src/main/java/com/wfm/dto/DayStartRequest.java
  - src/main/java/com/wfm/dto/DeskResponse.java
  - src/main/java/com/wfm/model/Desk.java
  - src/main/java/com/wfm/model/Timeslot.java
  - src/main/java/com/wfm/repository/ScheduleRepository.java
  - src/main/java/com/wfm/service/DeskService.java
  - src/main/java/com/wfm/service/FteUploadService.java
  - src/main/java/com/wfm/service/ScheduleService.java
  - src/main/java/com/wfm/service/TimeslotGeneratorService.java
  - src/main/java/com/wfm/util/DayWindow.java
  - src/main/resources/db/migration/V53__add_desk_day_start_and_timeslot_business_date.sql
  - src/test/java/com/wfm/migration/MigrationEntityConsistencyTest.java
  - src/test/java/com/wfm/repository/MidnightTimeslotPostgresTest.java
  - src/test/java/com/wfm/service/BusinessDateWritePathGuardTest.java
  - src/test/java/com/wfm/service/DeskServiceDayStartTest.java
  - src/test/java/com/wfm/service/DeskServiceSchedulingModeTest.java
  - src/test/java/com/wfm/service/MidnightBoundaryPropertyTest.java
  - src/test/java/com/wfm/service/MidnightTimeArithmeticGuardTest.java
  - src/test/java/com/wfm/service/MidnightWindowSeamTest.java
  - src/test/java/com/wfm/service/ScheduleServiceShiftSnapshotTest.java
  - src/test/java/com/wfm/service/ShiftLibraryGenerationCapConfigTest.java
  - src/test/java/com/wfm/service/ShiftLibraryGenerationServiceTest.java
  - src/test/java/com/wfm/service/ShiftLibraryValidationServiceTest.java
  - src/test/java/com/wfm/service/TimeslotGeneratorBusinessDateTest.java
  - src/test/java/com/wfm/service/TimeslotGeneratorServiceTest.java
  - src/test/java/com/wfm/solver/MidnightBoundaryFixture.java
  - src/test/java/com/wfm/solver/MidnightBoundaryFixtureLoadsTest.java
  - src/test/java/com/wfm/solver/MidnightBoundaryRegressionTest.java
  - src/test/java/com/wfm/support/AssertsTodaysBehaviour.java
  - src/test/java/com/wfm/support/MidnightBoundaryScenarioRegistryTest.java
  - src/test/java/com/wfm/util/DayWindowTest.java
  - src/test/resources/bday-02-write-paths.md
  - src/test/resources/midnight-boundary-scenarios.md
  - src/test/resources/midnight-guard-offender/OffendingSample.java
  - src/test/resources/midnight-time-arithmetic.md
findings:
  critical: 0
  warning: 2
  info: 2
  total: 4
status: issues_found
---

# Phase 18: Code Review Report

**Reviewed:** 2026-09-30T00:00:00Z
**Depth:** standard
**Files Reviewed:** 39
**Status:** issues_found

## Summary

This phase adds a desk-level business-day anchor (`day_start`), a derived `timeslot.business_date`
column, and the `DayWindow` anchor-aware arithmetic that will let Phase 19 re-point the codebase
onto non-midnight business days. I traced the core interval arithmetic in `DayWindow`
(`startMinuteFromDayStart`, `endMinuteFromDayStart`, `timeAtDayStartOffset`, `businessDateOf`,
`calendarDateAtDayStartOffset`) by hand against several anchor values (00:00, 21:00, boundary
offsets 0/179/180/1439/1440) and found the arithmetic correct at every boundary I checked,
including the 1440-minute wraparound and the "slot ending exactly at the anchor still belongs to
the business day it closes" rule. I re-derived `TimeslotGeneratorService.generateTimeslots`'s
business-day walk the same way — `slotKey` staying keyed on the calendar date while
`periodStart`/`periodEnd` became business dates does not create a stale-slot-blocks-its-own-
replacement bug, because both the survivor map and the newly-generated keys are built through the
same `slotKey` function and business date is never part of a row's identity.

The structural guard tests (`BusinessDateWritePathGuardTest`, `MidnightTimeArithmeticGuardTest`,
`MidnightBoundaryScenarioRegistryTest`) all use genuine bidirectional set-equality
(`containsExactlyInAnyOrderElementsOf`), all reject an empty expected/allowlist set, and all carry
an explicit test-of-the-test proving the assertion can actually go red through its full pipeline —
none of them were weakened to a subset or containment check. I did not find a case where the guard
can pass vacuously.

Because the desk `day_start` value is gated to `00:00` only this phase (`DeskService.setDayStart`),
none of the anchor-aware code paths are reachable in production yet; their correctness is
established entirely by the unit/property tests, which is appropriate given the phase's stated
scope. Two findings below are both about deploy-time and dead-code hygiene rather than the
anchor arithmetic itself.

## Warnings

### WR-01: V53 holds an ACCESS EXCLUSIVE lock on `timeslot` for the duration of a full-table `UPDATE`, against a live system with no staging tier

**File:** `src/main/resources/db/migration/V53__add_desk_day_start_and_timeslot_business_date.sql:16-18`
**Issue:** The migration runs `ALTER TABLE timeslot ADD COLUMN business_date DATE;` then
`UPDATE timeslot SET business_date = date;` then `ALTER TABLE timeslot ALTER COLUMN business_date
SET NOT NULL;`, all as one Flyway migration (one transaction). Postgres holds the `ALTER TABLE`'s
ACCESS EXCLUSIVE lock on `timeslot` until the transaction commits — not just for the DDL
statements — so the `UPDATE`, which rewrites every existing row in the table, runs inside that lock
window. Any concurrent reader or writer of `timeslot` (timeslot generation, staffing uploads,
solver reads) blocks for the full duration of the backfill, not merely until the ALTER TABLE
statement itself returns. This is a materially larger table than the small config tables (`V5`,
`V7`, `V8`, `V9`) this project has previously backfilled with an in-migration `UPDATE` — `timeslot`
can hold thousands of rows per desk across a live scheduling horizon. There is no batching, no
`CONCURRENTLY`-style incremental approach, and — per this project's own operating model — the
environment this runs against first is the live system with real tenant data, not a staging tier
absorbing the risk first. The phase's own migration comment argues the backfill "can't miss
concurrent writes" because the nullable window is zero-length; that argument is correct (the lock
prevents concurrent writes from being missed), but it does not address the lock-duration/downtime
cost of holding that lock across a full-table rewrite.
**Fix:** For this table size, prefer the standard safe pattern: add the column nullable with a
`DEFAULT` referencing `date` is not possible for a non-constant default, so instead (a) add the
column nullable in one migration, (b) backfill in batches (e.g. `UPDATE ... WHERE business_date IS
NULL AND id IN (SELECT id FROM timeslot WHERE business_date IS NULL LIMIT 10000)`) in a follow-up
migration or a background job, committing between batches so the exclusive lock is not held for
the whole backfill, then (c) add the `NOT NULL` constraint (Postgres 12+ can validate `NOT NULL`
against an existing `CHECK (business_date IS NOT NULL) NOT VALID` without a full table scan) once
the backfill is confirmed complete. At minimum, measure current `timeslot` row counts in the `dev`
environment before this migration runs there, to confirm the lock window is actually short enough
to be acceptable.

### WR-02: `desks.setDayStart` is added to the frontend API client but never called from any component

**File:** `frontend/src/api/client.ts:106-107`
**Issue:** `setDayStart` is exported from the `desks` API object but `grep` across `frontend/src`
shows no caller — `DeskManagement.tsx` renders `desk.dayStart` as a read-only string and never
invokes `desks.setDayStart`. Compare with `desks.setSchedulingMode`, added in an earlier phase in
the same file, which is wired into `ShiftLibrary.tsx`. This is unreachable/dead code as shipped.
**Fix:** Either wire a (disabled, since only `00:00` is accepted) call site now so the function is
exercised, or leave a comment at the declaration noting it is deliberately unused until Phase 19
wires the editable control the `DeskManagement.tsx` comment already anticipates — so a future
reviewer does not have to re-derive that this is intentional dead code rather than an oversight.

## Info

### IN-01: `TimeslotGeneratorService.generateTimeslots`'s duplicated obsolete-slot deletion pattern relies on the caller passing a consistent `dayStart` across calls for the same desk

**File:** `src/main/java/com/wfm/service/TimeslotGeneratorService.java:104-120`
**Issue:** `isDesired` (and therefore the survivor/obsolete partition) classifies each *existing*
row's business date using the `dayStart` passed to the *current* call, not the anchor that was in
effect when that row was originally generated. Today this is a no-op observation, since
`DeskService.setDayStart` refuses any value other than `00:00`, so every call for a given desk uses
the same anchor. It is worth a forward-looking note (not a phase-18 defect) because once BDAY-04
lifts that gate, a desk whose `day_start` changes between two `generateTimeslots` calls (e.g. via a
future re-anchor operation) would have its pre-existing rows reclassified under the *new* anchor by
this function, which is a different operation from what the business-date column's "one deriving
writer" write-path guard document describes. No action needed this phase; flagged so it is not
rediscovered as a surprise when BDAY-04 widens the accepted range.
**Fix:** No change required in Phase 18. When BDAY-04 lands, confirm that a desk's `day_start`
re-anchor path either regenerates (not merely reclassifies) existing timeslots, or is explicitly
scoped to deny re-anchoring a desk with live timeslots outside the generation window.

### IN-02: `DeskManagement.tsx` repeats the same day-start caption string in both the edit and display branches

**File:** `frontend/src/pages/DeskManagement.tsx:112,124`
**Issue:** The literal `{desk.dayStart} (only 00:00 is supported until overnight scheduling lands)`
is duplicated verbatim across the edit-mode and display-mode `<td>`. This mirrors the pre-existing
`schedulingMode` cell's same duplication pattern in the same file, so it is consistent with the
file's existing style rather than a new regression, but it is still two places to update if the
caption text or `dayStart` formatting ever changes.
**Fix:** Optional: extract a small `DayStartCell` helper or a shared constant for the caption text
if this file gains more read-only mirrored cells in future phases.

---

_Reviewed: 2026-09-30T00:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
