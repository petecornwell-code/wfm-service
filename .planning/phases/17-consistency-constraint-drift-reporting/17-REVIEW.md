---
phase: 17-consistency-constraint-drift-reporting
reviewed: 2026-09-17T21:59:42Z
depth: standard
files_reviewed: 34
files_reviewed_list:
  - frontend/src/api/client.ts
  - frontend/src/pages/ConstraintWeightsPage.tsx
  - frontend/src/pages/ScheduleResults.tsx
  - src/main/java/com/wfm/dto/ConstraintWeightsDto.java
  - src/main/java/com/wfm/dto/ScheduleDetailResponse.java
  - src/main/java/com/wfm/model/ConstraintWeights.java
  - src/main/java/com/wfm/model/ResolvedUsualShiftTarget.java
  - src/main/java/com/wfm/model/Schedule.java
  - src/main/java/com/wfm/model/ScheduleConfig.java
  - src/main/java/com/wfm/model/ShiftBandPair.java
  - src/main/java/com/wfm/service/ConstraintWeightsService.java
  - src/main/java/com/wfm/service/ScheduleExportService.java
  - src/main/java/com/wfm/service/ScheduleOutputService.java
  - src/main/java/com/wfm/service/ScheduleService.java
  - src/main/java/com/wfm/service/SolverService.java
  - src/main/java/com/wfm/solver/ScheduleConstraintProvider.java
  - src/main/resources/db/migration/V48__add_consistency_tolerance_and_preferred_start_weight.sql
  - src/main/resources/db/migration/V49__set_consistency_weight_defaults.sql
  - src/test/java/com/wfm/controller/ConstraintWeightsControllerTest.java
  - src/test/java/com/wfm/model/ConstraintWeightsMigrationTest.java
  - src/test/java/com/wfm/service/ConstraintWeightsServiceTest.java
  - src/test/java/com/wfm/service/DriftReportTest.java
  - src/test/java/com/wfm/service/ScheduleExportServiceTest.java
  - src/test/java/com/wfm/service/ScheduleOutputServiceShiftReportingTest.java
  - src/test/java/com/wfm/service/ScheduleServiceShiftSnapshotTest.java
  - src/test/java/com/wfm/service/SolverUsualShiftWritePathGuardTest.java
  - src/test/java/com/wfm/service/UsualShiftWritePathGuardTest.java
  - src/test/java/com/wfm/service/UsualShiftWritePathTest.java
  - src/test/java/com/wfm/solver/ConstraintPrecedenceObservabilityTest.java
  - src/test/java/com/wfm/solver/PreferredStartShiftModeConstraintTest.java
  - src/test/java/com/wfm/solver/ScheduleConstraintClassification.java
  - src/test/java/com/wfm/solver/ScheduleConstraintClassificationTest.java
  - src/test/java/com/wfm/solver/UsualShiftConsistencyBenchmarkTest.java
  - src/test/java/com/wfm/solver/UsualShiftConsistencyConstraintTest.java
  - src/test/resources/ushf-05-write-paths.md
findings:
  critical: 1
  warning: 1
  info: 2
  total: 4
status: issues_found
---

# Phase 17: Code Review Report

**Reviewed:** 2026-09-17T21:59:42Z
**Depth:** standard
**Files Reviewed:** 34 (listed above, all read in full)
**Status:** issues_found

## Summary

This phase ships a target-deviation usual-shift consistency constraint (correctly avoiding the
reverted spread-based formulation — confirmed by reading `ScheduleConstraintProvider
.usualShiftConsistency`/`preferredStartShiftMode` and their dedicated `ConstraintVerifier` test
suites) plus an operator-facing drift report on both the results UI and the Excel export. The
constraint math, the D-07/D-08 save-time enforcement in `ConstraintWeightsService`, and the V49
migration's predicated `UPDATE` (verified against `ConstraintWeightsMigrationTest`, which
round-trips the exact predicate against a fixture representing both a still-default row and an
operator-tuned row) are all sound and well covered.

The one blocker found is exactly the class of defect the phase brief asked to be scrutinized for:
the backend's own documented invariant that `driftReport` is `null` on a slot-scheduled desk is
never actually established in code. `ScheduleService.getScheduleDetail` calls
`ScheduleOutputService.buildDriftReport` unconditionally, and that method never returns `null` —
it always returns a `DriftReport` record. For a SLOT-mode desk this produces an empty main table
(never a "not applicable" message) plus a **populated** "Most-Subscribed Usual Shifts" section in
the Excel export, because `popularity` is derived from stored usual-shift rows independent of the
desk's current scheduling mode. The React UI is unaffected — `DriftTab` branches on
`schedule.schedulingMode` before ever touching `driftReport` — but the Excel export
(`ScheduleExportService.writeDriftReport`) branches on `report == null`, a condition that can no
longer occur, and the one test that appears to cover this case never actually calls the real
`ScheduleService` code path, so the regression is untested.

## Critical Issues

### CR-01: `driftReport` is never null on a SLOT-scheduled desk, contradicting its own documented contract and leaking a populated popularity table into the Excel export

**File:** `src/main/java/com/wfm/service/ScheduleService.java:157`
**Also:** `src/main/java/com/wfm/service/ScheduleOutputService.java:458-565`, `src/main/java/com/wfm/dto/ScheduleDetailResponse.java:190-199`, `src/main/java/com/wfm/service/ScheduleExportService.java:244-258`

**Issue:**
`ScheduleDetailResponse.DriftReport`'s javadoc states:

> Phase 17's drift report (DRFT-01…04) — derived on read, alongside `PreferenceReport`, from
> `ScheduleOutputService.buildDriftReport(schedule)`. `{@code null}` on a slot-scheduled desk.

But `ScheduleService.getScheduleDetail` never gates this call on scheduling mode:

```java
response.setDriftReport(scheduleOutputService.buildDriftReport(schedule));
```

and `ScheduleOutputService.buildDriftReport` has no code path that returns `null` — it always
constructs and returns a `DriftReport(entries, summary, popularity)`. For a SLOT-scheduled desk:

- `entries` is always empty, because `schedule.getShiftAssignments()` is structurally empty in
  SLOT mode (`SolverService.buildShiftAssignments` mode-gates on `SchedulingMode.SHIFT`) — so the
  main table renders with headers only and zero data rows, an *empty table*, not the "not
  applicable" message the phase explicitly requires for slot-scheduled desks.
- `popularity` is **not** mode-gated at all — it is built directly from
  `agentUsualShiftRepository.findByTenantIdAndDeskId(...)`, independent of the schedule or the
  desk's current mode. `src/test/resources/ushf-05-write-paths.md`'s own "Scheduling-mode switch"
  row documents that stored usual-shift rows survive a SHIFT→SLOT switch byte-identical, and
  `UsualShiftWritePathTest.switchSchedulingMode_roundTrip_leavesStoredUsualShiftsFieldIdentical`
  proves it end to end (SLOT→SHIFT→SLOT, row count and every field unchanged) — so a desk that
  used to be SHIFT-mode and still carries usual-shift rows will show a real, non-trivial
  "Most-Subscribed Usual Shifts" table in the Excel export of a schedule solved under SLOT mode
  today. This is a reachable, tested-as-persistent state, not a hypothetical.

`ScheduleExportService.writeDriftReport`'s own javadoc and code depend on the DTO's documented
`null`-on-SLOT contract:

```java
if (report == null || report.entries() == null) {
    autoSizeColumns(sheet, cols.length);
    return;
}
```

— this branch is now dead for the real end-to-end path, because `report` is never null in
practice.

The React frontend is not affected by this — `DriftTab` in `ScheduleResults.tsx` checks
`schedule.schedulingMode !== 'SHIFT'` and returns the not-applicable message before ever reading
`schedule.driftReport` — but any other consumer of the same `ScheduleDetailResponse` (the Excel
export, a future API client, automated tooling) inherits the bug.

**Test gap confirming this is unexercised:** `ScheduleExportServiceTest
.exportToExcel_nullDriftReport_sheetHasOnlyTheMainHeaderRow` claims in its own comment to be
"exercising a SLOT-scheduled desk", but it only constructs a `ScheduleDetailResponse` directly
and never sets `driftReport` — it never calls `ScheduleService.getScheduleDetail` for an actual
SLOT-mode schedule, so it cannot catch the fact that the real code path never produces that
`null`. No test in `DriftReportTest` or `ScheduleServiceShiftSnapshotTest` exercises
`getScheduleDetail` for a SLOT-mode schedule and asserts on `response.getDriftReport()`.

**Fix:**
Gate the call in `ScheduleService.getScheduleDetail` on the schedule's own recorded scheduling
mode (the same field `AgentAllocationTab`/`DriftTab` already trust on the frontend), matching the
DTO's documented contract:

```java
response.setDriftReport(
        schedule.getSchedulingMode() == SchedulingMode.SHIFT
                ? scheduleOutputService.buildDriftReport(schedule)
                : null);
```

Add a regression test (mirroring `ScheduleServiceShiftSnapshotTest`'s accepted-schedule shape)
that solves/accepts a SLOT-mode schedule for a desk carrying at least one stored `AgentUsualShift`
row, calls `getScheduleDetail`, and asserts `response.getDriftReport()` is `null` — and update
`ScheduleExportServiceTest`'s "null drift report" test to go through `ScheduleService` (or at
minimum rename/re-comment it so it stops claiming to prove something it does not).

## Warnings

### WR-01: `ConstraintWeightsData`'s `Score | number` widening is bridged entirely by unchecked casts, so a future numeric field would silently misrender rather than fail to compile

**File:** `frontend/src/api/client.ts:379`, `frontend/src/pages/ConstraintWeightsPage.tsx:104-125`

**Issue:**
`ConstraintWeightsData` was widened from a pure `{ [key: string]: Score }` index signature to
`{ [key: string]: Score | number }` to accommodate `consistencyToleranceMinutes`. That widening is
itself sound for the one field it was introduced for — the render function's branch (line 104)
correctly narrows with `typeof rawValue === 'number'` before treating the value as a plain number.

However, every *other* row in the same table bridges the resulting union back down to `Score`
with an unchecked type assertion rather than a runtime check:

```ts
const score = (weights as Record<string, Score>)[key] || (DEFAULTS[key] as Score)
const level = score.hardScore > 0 ? 'Hard' : 'Soft'
```

If a future backend change adds a second plain-number field to `ConstraintWeightsDto` (mirroring
how `consistencyToleranceMinutes` was added) and a row for it is added to `CONSTRAINTS` without
also adding a dedicated branch like the tolerance-band one, this cast silently reinterprets the
number as `{ hardScore: undefined, softScore: undefined }` at runtime — `score.hardScore > 0`
evaluates to `false` (not a thrown error), so the row renders as "Soft" with `NaN`/blank inputs
rather than failing to compile or throwing visibly. TypeScript's structural typing cannot catch
this because the cast (`as Record<string, Score>`) discards the union entirely instead of using a
type guard.

**Fix:** Either keep the widened type but replace the blanket cast with a per-row runtime guard
(e.g. `if (typeof weights[key] === 'number') throw/skip` as a defensive default case), or model
`ConstraintWeightsData` as a discriminated shape (e.g. `Record<string, Score> &
{ consistencyToleranceMinutes: number }`) so the compiler — not a runtime `typeof` check scattered
through the render loop — enforces that every other key is a `Score`.

## Info

### IN-01: V49's predicated `UPDATE` statements are self-referential no-ops

**File:** `src/main/resources/db/migration/V49__set_consistency_weight_defaults.sql:54-60`

**Issue:** Both `UPDATE` statements set a column to the exact value their own `WHERE` clause
requires it already holds (`SET consistent_start_weight = '0hard/2soft' WHERE
consistent_start_weight = '0hard/2soft'`). This is deliberate per the migration's own comment
("this migration's UPDATE statements therefore CONFIRM the incumbent values rather than change
behaviour") and is exercised by `ConstraintWeightsMigrationTest`, so it is not a defect — but as
written the two `UPDATE` statements can never change a single row's data; they exist purely to
document/prove the predicate shape via the test, not to perform a migration. Worth a one-line
comment callout that these are intentionally no-ops today and would only start doing real work if
a future migration changes the *chosen* default away from what V38/V48 already shipped (at which
point the `SET` value and the `WHERE` value would diverge).

**Fix:** No code change required; consider adding one sentence to the migration header noting the
statements are currently no-ops by construction, to save a future reader the same investigation.

### IN-02: `writeDriftReport`'s null-report branch is now unreachable in production

**File:** `src/main/java/com/wfm/service/ScheduleExportService.java:255-258`

**Issue:** As a direct consequence of CR-01, `writeDriftReport`'s `report == null` early return
can never fire via the real `/desks/{deskId}/schedules/{id}/export` endpoint today, since
`ScheduleService` always supplies a non-null `DriftReport`. This is dead code until CR-01 is
fixed — noted separately because once CR-01 lands, this branch becomes live again and should be
left in place (it is the correct behaviour for a genuinely `null` report), not removed.

**Fix:** No action needed beyond fixing CR-01; flagged so the fix for CR-01 doesn't also delete
this branch as apparently-dead code.

---

_Reviewed: 2026-09-17T21:59:42Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
