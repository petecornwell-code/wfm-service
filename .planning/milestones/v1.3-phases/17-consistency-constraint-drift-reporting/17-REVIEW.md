---
phase: 17-consistency-constraint-drift-reporting
reviewed: 2026-09-18T12:10:42Z
depth: standard
files_reviewed: 6
files_reviewed_list:
  - frontend/src/pages/ConstraintWeightsPage.tsx
  - src/main/java/com/wfm/service/ScheduleExportService.java
  - src/main/java/com/wfm/service/ScheduleService.java
  - src/main/resources/db/migration/V49__set_consistency_weight_defaults.sql
  - src/test/java/com/wfm/service/ScheduleExportServiceTest.java
  - src/test/java/com/wfm/service/ScheduleServiceShiftSnapshotTest.java
findings:
  critical: 0
  warning: 0
  info: 0
  total: 0
status: clean
---

# Phase 17: Code Review Report (Incremental Re-Review)

**Reviewed:** 2026-09-18T12:10:42Z
**Depth:** standard
**Files Reviewed:** 6
**Status:** clean

## Summary

This is a re-review of exactly the diff introduced by five fix commits (`ea9b6ac`, `7ac545e`,
`0b484c2`, `102e28f`, plus the report-only `820bcbf`) against the previous review baseline
`9b28b06`. Each commit was independently re-derived from `git diff 9b28b06..HEAD` (not taken on
faith from the commit messages) and checked against the live source, not just the stated intent.

**CR-01 (driftReport gated on SchedulingMode.SHIFT) — verified fixed and total.**
`ScheduleService.getScheduleDetail` (`ScheduleService.java:157-160`) now reads:
```java
response.setDriftReport(
        schedule.getSchedulingMode() == SchedulingMode.SHIFT
                ? scheduleOutputService.buildDriftReport(schedule)
                : null);
```
- `grep` across `src/main` confirms this is the ONLY call site of
  `ScheduleOutputService.buildDriftReport`. `ScheduleDetailResponse.setDriftReport` has one further
  production call site (`ScheduleService.java:252`, the date-filter recompute), but it is nested
  inside `if (response.getDriftReport() != null)` and so is unreachable once the gate above has
  nulled the report — there is no second, ungated path that could still leak a non-null drift
  report for a SLOT-mode desk.
- `SchedulingMode` is a plain two-value enum (`SLOT`, `SHIFT`); `schedule.getSchedulingMode()` is
  set unconditionally by `SolverService.buildSchedule` for in-memory schedules and is a non-nullable
  persisted column (V43) for accepted schedules reloaded from the DB. Even in the structurally
  impossible case it were `null`, `null == SchedulingMode.SHIFT` evaluates `false` and the code
  fails closed to the documented `null`-on-SLOT contract (`ScheduleDetailResponse.java:188-193`)
  rather than leaking a report.
- The later date-filter recompute block (`ScheduleService.java:236-254`) only touches
  `response.getDriftReport()` when it is non-null, so it cannot reintroduce a report for a SLOT
  desk that the gate above already nulled out.
- The new regression test `getScheduleDetail_inMemorySlotModeSchedule_driftReportIsNullAndNeverBuilt`
  asserts `verify(scheduleOutputService, never()).buildDriftReport(any())`, not merely that the
  response field is null — reverting the gate to the old unconditional call would make
  `buildDriftReport` execute for the SLOT-mode schedule and this test would genuinely fail (Mockito's
  default `null` return for an unstubbed method would otherwise have made a field-only assertion
  pass either way, which is exactly the trap the previous review's tests fell into and this
  rewrite avoids).
- `ScheduleExportService.writeDriftReport`'s `report == null` branch is correctly live
  (`ScheduleExportServiceTest.exportToExcel_nullDriftReport_writesOnlyTheMainHeaderRow` and the
  IN-02 comment both accurately describe it as reachable production behaviour for SLOT desks, not
  dead code).

**WR-01 (isScore runtime guard) — verified correct, no behavioural regression found.**
`ConstraintWeightsPage.tsx:68-70` replaces the unchecked `as Record<string, Score>` cast with a
proper type guard. Cross-checked against the actual wire shape: `ConstraintWeightsDto`
(`src/main/java/com/wfm/dto/ConstraintWeightsDto.java`) declares every one of the 24 weight fields
as `ScoreDto(Integer hardScore, Integer softScore)` and only `consistencyToleranceMinutes` as a
plain `Integer`; the frontend's `CONSTRAINTS` and `DEFAULTS` tables list exactly those same 25 keys.
`isScore` correctly returns `true` for any object carrying both `hardScore` and `softScore` keys
regardless of their values (including `0`), correctly returns `false` for `null`, arrays, and plain
numbers, and does not depend on truthiness the way the old `||` fallback did (the old
`(weights as Record<string, Score>)[key] || (DEFAULTS[key] as Score)` was already latently safe
here only because a `Score` object is always truthy — but it type-cast a `number` value
unconditionally, which the new guard now actually checks). No key on the current wire contract can
trigger the skip-and-log fallback branch (`ConstraintWeightsPage.tsx:137-143`); that branch is
reachable only for a hypothetical future field added to `CONSTRAINTS` without either a `Score`
shape or a dedicated render branch, and is accurately documented as such. No case was found where a
row that rendered correctly before this change would now be dropped.

**IN-01 / IN-02 — comment-only changes, verified accurate.**
- V49's two `UPDATE` statements have byte-identical `SET`/`WHERE` literals
  (`'0hard/2soft'` / `'0hard/2soft'` and `'0hard/1soft'` / `'0hard/1soft'`), confirming the added
  comment's claim that they are self-referential no-ops by construction today.
- `ScheduleExportService.writeDriftReport`'s `report == null` branch is reachable, as verified under
  CR-01 above; the added comment correctly warns against removing it as apparently-unreachable code.

**New defects introduced by these five commits:** none found. No unused imports, no dead code
newly introduced, no console/debug artifacts, no test compilation hazards (the new `never`,
`times`, `verify` static imports in `ScheduleServiceShiftSnapshotTest` are all used).

All reviewed files meet quality standards. No issues found.
