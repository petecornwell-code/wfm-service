---
phase: 19-daywindow-re-anchoring
reviewed: 2026-10-01T06:55:26Z
depth: standard
files_reviewed: 59
files_reviewed_list:
  - src/main/java/com/wfm/controller/ShiftTemplateController.java
  - src/main/java/com/wfm/dto/ScheduleDetailResponse.java
  - src/main/java/com/wfm/model/Schedule.java
  - src/main/java/com/wfm/model/ScheduleConfig.java
  - src/main/java/com/wfm/model/ShiftBandPair.java
  - src/main/java/com/wfm/model/ShiftTemplate.java
  - src/main/java/com/wfm/model/ShiftTemplateBreakBand.java
  - src/main/java/com/wfm/service/DeskAgentService.java
  - src/main/java/com/wfm/service/DeskService.java
  - src/main/java/com/wfm/service/FteUploadService.java
  - src/main/java/com/wfm/service/ScheduleEnvelopeRepairService.java
  - src/main/java/com/wfm/service/ScheduleExportService.java
  - src/main/java/com/wfm/service/ScheduleOutputService.java
  - src/main/java/com/wfm/service/ScheduleService.java
  - src/main/java/com/wfm/service/ShiftLibraryGenerationService.java
  - src/main/java/com/wfm/service/ShiftLibraryValidationService.java
  - src/main/java/com/wfm/service/ShiftStartMixTargetService.java
  - src/main/java/com/wfm/service/ShiftTemplateService.java
  - src/main/java/com/wfm/service/SolverService.java
  - src/main/java/com/wfm/service/StaffingRequirementService.java
  - src/main/java/com/wfm/service/TimeslotGeneratorService.java
  - src/main/java/com/wfm/solver/ScheduleConstraintProvider.java
  - src/main/java/com/wfm/util/DayWindow.java
  - src/main/java/com/wfm/util/FteSpreadsheetGenerator.java
  - src/main/resources/db/migration/V54__add_schedule_day_start.sql
  - frontend/src/pages/DeskManagement.tsx
  - src/test/java/com/wfm/service/MidnightBoundaryPropertyTest.java
  - src/test/java/com/wfm/service/MidnightTimeArithmeticGuardTest.java
  - src/test/java/com/wfm/service/MidnightWindowSeamTest.java
  - src/test/java/com/wfm/service/MinimumStaffingSeatsTest.java
  - src/test/java/com/wfm/service/ScheduleAllocationExportTest.java
  - src/test/java/com/wfm/service/ScheduleEnvelopeRepairServiceTest.java
  - src/test/java/com/wfm/service/ScheduleExportServiceTest.java
  - src/test/java/com/wfm/service/ScheduleOutputServiceShiftReportingTest.java
  - src/test/java/com/wfm/service/ScheduleRosterExportTest.java
  - src/test/java/com/wfm/service/ScheduleServiceShiftSnapshotTest.java
  - src/test/java/com/wfm/service/ShiftEnvelopeSupplyGateTest.java
  - src/test/java/com/wfm/service/ShiftLibraryValidationServiceTest.java
  - src/test/java/com/wfm/service/ShiftModeMinimumStaffingSeatSupplyTest.java
  - src/test/java/com/wfm/service/ShiftStartMixTargetServiceTest.java
  - src/test/java/com/wfm/service/ShiftTemplateServiceTest.java
  - src/test/java/com/wfm/service/SolverSeatExpansionAccess.java
  - src/test/java/com/wfm/service/SolverSeatSupplyGateAccess.java
  - src/test/java/com/wfm/service/SolverServiceBuildScheduleAccess.java
  - src/test/java/com/wfm/service/StaffingRequirementErlangTest.java
  - src/test/java/com/wfm/service/TimeslotGeneratorBusinessDateTest.java
  - src/test/java/com/wfm/solver/DeskAnchorReachesConstraintTest.java
  - src/test/java/com/wfm/solver/LiveShapeShiftDeskFixture.java
  - src/test/java/com/wfm/solver/MidnightBoundaryFixture.java
  - src/test/java/com/wfm/solver/MidnightGapScanTest.java
  - src/test/java/com/wfm/solver/ScheduleConfigAnchorPlumbingTest.java
  - src/test/java/com/wfm/solver/SeatSupplyDistributionAnalysisTest.java
  - src/test/java/com/wfm/solver/ShiftDeskEndToEndRegressionTest.java
  - src/test/java/com/wfm/solver/ShiftEnvelopeSupplyInvariantTest.java
  - src/test/java/com/wfm/solver/ShiftModeFixtures.java
  - src/test/java/com/wfm/solver/ZeroDemandTimeslotCeilingTest.java
  - src/test/java/com/wfm/util/DayWindowAnchorBindingTest.java
  - src/test/java/com/wfm/util/DayWindowTest.java
  - src/test/resources/midnight-time-arithmetic.md
findings:
  critical: 0
  warning: 1
  info: 2
  total: 3
status: issues_found
---

# Phase 19: Code Review Report

**Reviewed:** 2026-10-01T06:55:26Z
**Depth:** standard
**Files Reviewed:** 59
**Status:** issues_found

## Summary

Phase 19 re-anchors `DayWindow`'s interval arithmetic from an implicit midnight onto a
caller-supplied desk day-start, across 8 plans, ~30 production call sites, and 59 changed files.
This review traced anchor provenance at every migrated call site named in the review brief as
high-risk: the `PENDING_DESK_ANCHOR` scoping in `ScheduleConstraintProvider` (all six consuming
constraints are genuinely Quad-arity-capped before `.ifExists(ScheduleConfig...)`, confirmed by
reading each constraint's stream shape, not merely trusting the plan's own claim), the null-anchor
fallback in `shiftEnvelopeCompliance`/`breakClustering` (defensible — production never supplies
null, and the two cross-tenant-sensitive call sites that gained a window lookup,
`ShiftTemplateController.listShiftTemplates` and `TimeslotGeneratorService.getLiveBounds`, both
correctly check the tenant-scoped result empty *before* resolving the desk anchor, avoiding the
T-14-15-class leak the phase's own plan 19-04 caught and fixed once already), the `V54` migration
against `Schedule.dayStart`'s entity mapping (DB `NOT NULL DEFAULT '00:00'` vs. a nullable Java
field — reconciled: the single production write path, `SolverService.buildSchedule`, always sets
it from `Desk.dayStart`, which itself defaults to `MIDNIGHT`), `ShiftTemplateService.validate`'s
D-11 forward-interval refusal (byte-identical message, position, and outcome — confirmed by
reading the method directly), the frozen-oracle flip in `DayWindowTest` (genuinely compares against
an independent, pre-migration-copied implementation, not against the thing under test — confirmed
the `live*` wrapper methods route through `MIDNIGHT_WINDOW.anchored*` while the oracle comparisons
stay on `FrozenOracle`'s own methods), and `StaffingRequirementService`'s reproduced
`durationMinutes` throw (faithful, same message, same call sites the pinned test exercises).

No Critical issues were found. One Warning and two Info items are recorded below — all three are
forward-looking robustness/maintainability observations rather than present-day correctness bugs,
since every desk's anchor is still `00:00` today (the `DeskService` gate, confirmed untouched by
this phase's diff). None of the eight "do not report" items in the review brief are treated as
findings here.

## Warnings

### WR-01: `ShiftLibraryGenerationService.resolveBreakConfig` anchors shift-library suggestions on a stale, schedule-snapshotted day-start rather than the desk's live one

**File:** `src/main/java/com/wfm/service/ShiftLibraryGenerationService.java:240-258`
**Issue:** `generateSuggestion(UUID deskId)` binds its `DayWindow` by loading the desk's
*most-recently-created `Schedule`* and reading `latest.getDayStart()` (falling back to
`LocalTime.MIDNIGHT` only when the desk has no persisted schedule at all), rather than loading the
`Desk` itself and reading `Desk.getDayStart()` directly. This mirrors an existing pattern in the
same method (the break-duration/threshold fields are read from the same snapshot for the same
reason), and it is byte-identical to today's behaviour because `DeskService`'s `00:00`-only gate
means every desk's anchor and every schedule's snapshotted anchor are always `MIDNIGHT`.

Once Phase 20 lifts that gate, this stops being byte-identical: a desk operator can change a
desk's day-start anchor (`DeskService.setDayStart`) at any time, but `generateSuggestion` will keep
scoring and enumerating shift-library candidates against whatever anchor happened to be in effect
when the desk's *last schedule was solved* — which can be stale by any amount of time, including a
day-start the desk no longer holds. Every other `DayWindow`-consuming service in this phase binds
its anchor from either the live `Desk` (`ShiftTemplateService.dayWindowFor`,
`DeskAgentService.dayWindowFor`, `TimeslotGeneratorService.dayWindowFor`,
`StaffingRequirementService.dayWindowFor`, `FteUploadService.uploadFtes`) or from the `Schedule`
actually being read/exported (`ScheduleOutputService`, `ScheduleExportService`,
`ScheduleEnvelopeRepairService` — all correctly schedule-scoped, since their job IS to describe
that specific schedule). `ShiftLibraryGenerationService.generateSuggestion`, by contrast, is a
desk-level operation (it takes only a `deskId`, no schedule) that incidentally reads a `Schedule`
for unrelated config fields and now also inherits that schedule's anchor as a side effect — a
different category of staleness risk from the two break-duration fields, which are expected to be
desk-wide constants rather than something an operator can edit independently of re-solving.
**Fix:** When Phase 20 (or whichever phase lifts the `00:00`-only gate) revisits this file, give
`ShiftLibraryGenerationService` its own `DeskRepository` lookup for the anchor (the same shape
`TimeslotGeneratorService`, `FteUploadService` and `StaffingRequirementService` already adopted in
this phase for the identical "no reachable anchor source" problem), decoupling it from
`resolveBreakConfig`'s Schedule-snapshot read. At minimum, leave a code comment at this call site
(not just in the phase SUMMARY, which will not be read at that point) noting that the anchor here
is schedule-snapshotted, not desk-live, so the next editor does not assume it already matches
`Desk.dayStart`.

## Info

### IN-01: `Schedule.dayStart` is nullable in the Java entity while the backing column is `NOT NULL`

**File:** `src/main/java/com/wfm/model/Schedule.java:45-51`, `src/main/resources/db/migration/V54__add_schedule_day_start.sql:9`
**Issue:** The migration declares `day_start TIME NOT NULL DEFAULT '00:00'`, but
`Schedule.dayStart`'s `@Column(name = "day_start")` carries no `nullable = false` and the field has
no initializer, so an in-memory `Schedule` that skips `setDayStart` holds a genuine Java `null`.
This is a deliberate, documented choice (so `ScheduleConfigAnchorPlumbingTest` can distinguish "no
anchor set" from "set to midnight"), and the one production write path
(`SolverService.buildSchedule`) always populates it from `Desk.dayStart`, which itself defaults to
`MIDNIGHT` — so no row is ever persisted with a genuine null today, and `MigrationEntityConsistencyTest`
only reconciles column existence/type, not nullability, so this asymmetry does not fail any existing
guard. Flagging only because a future JPA-level `INSERT` of an un-initialized `Schedule` (bypassing
`buildSchedule`) would fail at the database with a generic NOT NULL violation rather than a
`DayWindow`-level `IllegalArgumentException: dayStart must not be null` closer to the actual cause.
**Fix:** No action required for this phase. If a second `Schedule` construction path is ever added,
either call `setDayStart` explicitly at that site or revisit whether the entity-level
`nullable = false` should be added (accepting that this reopens the "un-persisted Schedule exposes
null" test design plan 19-01 deliberately chose against).

### IN-02: `ScheduleOutputService.buildConstraintViolations`'s window binding is conditional on `isAcceptedSnapshot`, with no inline note explaining why the other branch needs none

**File:** `src/main/java/com/wfm/service/ScheduleOutputService.java:604-614`
**Issue:** The method binds a `DayWindow` only inside the `if (isAcceptedSnapshot)` branch (passed
straight into `buildAcceptedConstraintViolations`). The `else` branch (the live-solver path) never
references `window`/`DayWindow` at all — confirmed correct, since that branch defers to
`solutionManager.explain(schedule)`, which re-derives violations through
`ScheduleConstraintProvider`'s own anchor bindings, not this method's. This is not a bug, but the
asymmetry (one branch binds an anchor, the other silently doesn't) is easy to misread on a future
edit as an oversight rather than a deliberate split, especially since every other public method in
this class binds its window unconditionally at the top.
**Fix:** A one-line comment at the `if (isAcceptedSnapshot)` branch noting that the live-solver path
intentionally needs no window of its own (it reads the anchor through `solutionManager.explain`'s
own constraint evaluation) would save the next reader the trace this review just performed.

---

_Reviewed: 2026-10-01T06:55:26Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
