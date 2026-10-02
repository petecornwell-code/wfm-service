---
phase: 20-solver-business-date-correctness
reviewed: 2026-10-01T00:00:00Z
depth: standard
files_reviewed: 54
files_reviewed_list:
  - frontend/src/pages/DeskManagement.tsx
  - src/main/java/com/wfm/model/AgentDayConfig.java
  - src/main/java/com/wfm/model/AgentShiftAssignment.java
  - src/main/java/com/wfm/repository/StaffingRequirementRepository.java
  - src/main/java/com/wfm/service/DeskService.java
  - src/main/java/com/wfm/service/ScheduleOutputService.java
  - src/main/java/com/wfm/service/ShiftLibraryGenerationService.java
  - src/main/java/com/wfm/service/SolverService.java
  - src/main/java/com/wfm/service/StaffingRequirementService.java
  - src/main/java/com/wfm/solver/ScheduleConstraintProvider.java
  - src/test/java/com/wfm/service/AgentDayDerivationGuardTest.java
  - src/test/java/com/wfm/service/BusinessDateJoinGuardTest.java
  - src/test/java/com/wfm/service/DeskDayStartGenerationReachabilityTest.java
  - src/test/java/com/wfm/service/DeskServiceDayStartTest.java
  - src/test/java/com/wfm/service/ScheduleOutputServiceShiftReportingTest.java
  - src/test/java/com/wfm/service/ScheduleServiceShiftSnapshotTest.java
  - src/test/java/com/wfm/service/ShiftEnvelopeSupplyGateTest.java
  - src/test/java/com/wfm/service/ShiftLibraryGenerationServiceTest.java
  - src/test/java/com/wfm/service/StaffingRequirementBusinessDateDeleteTest.java
  - src/test/java/com/wfm/service/UsualShiftWritePathTest.java
  - src/test/java/com/wfm/solver/BreakAwareConstructionTest.java
  - src/test/java/com/wfm/solver/BreakClusteringConstraintTest.java
  - src/test/java/com/wfm/solver/ConstraintMatchCountNonVacuityTest.java
  - src/test/java/com/wfm/solver/DeskAnchorReachesConstraintTest.java
  - src/test/java/com/wfm/solver/FullScale150AgentTest.java
  - src/test/java/com/wfm/solver/IncrementalScoringDiagnosticTest.java
  - src/test/java/com/wfm/solver/LiveShapeShiftDeskFixture.java
  - src/test/java/com/wfm/solver/MidnightBoundaryFixture.java
  - src/test/java/com/wfm/solver/MidnightBoundaryRegressionTest.java
  - src/test/java/com/wfm/solver/MultiDayConstraintDiagnosticTest.java
  - src/test/java/com/wfm/solver/NinetyAgent12HourTest.java
  - src/test/java/com/wfm/solver/NinetyFiveAgentReproTest.java
  - src/test/java/com/wfm/solver/NonWorkingDaySeatConstraintTest.java
  - src/test/java/com/wfm/solver/PhilUsShapedDriftGuardTest.java
  - src/test/java/com/wfm/solver/SeatSupplyDistributionAnalysisTest.java
  - src/test/java/com/wfm/solver/ShiftDeskEndToEndRegressionTest.java
  - src/test/java/com/wfm/solver/ShiftEnvelopeComplianceConstraintTest.java
  - src/test/java/com/wfm/solver/ShiftEnvelopeGroundTruthTest.java
  - src/test/java/com/wfm/solver/ShiftEnvelopeSupplyInvariantTest.java
  - src/test/java/com/wfm/solver/ShiftModeBreakGatingTest.java
  - src/test/java/com/wfm/solver/ShiftModeBreakGeometryGuardTest.java
  - src/test/java/com/wfm/solver/ShiftModeFixtures.java
  - src/test/java/com/wfm/solver/ShiftWorkContiguityConstraintTest.java
  - src/test/java/com/wfm/solver/SingleDaySolvableTest.java
  - src/test/java/com/wfm/solver/SlotModeOvernightContractedHoursTest.java
  - src/test/java/com/wfm/solver/SolverQualityGuardTest.java
  - src/test/java/com/wfm/solver/TwelveHourUniformDemandTest.java
  - src/test/java/com/wfm/solver/UsualShiftConsistencyBenchmarkTest.java
  - src/test/java/com/wfm/support/MidnightBoundaryScenarioRegistryTest.java
  - src/test/resources/agent-day-derivation.md
  - src/test/resources/bday-join-guard-offender/OffendingSample.java
  - src/test/resources/bday-join-guard.md
  - src/test/resources/midnight-boundary-scenarios.md
  - src/test/resources/midnight-time-arithmetic.md
findings:
  critical: 2
  warning: 1
  info: 0
  total: 3
status: issues_found
---

# Phase 20: Code Review Report

**Reviewed:** 2026-10-01T00:00:00Z
**Depth:** standard
**Files Reviewed:** 54
**Status:** issues_found

## Summary

The migration itself — re-pointing `ScheduleConstraintProvider`, `ScheduleOutputService`,
`ShiftLibraryGenerationService` and `StaffingRequirementService` from `Timeslot.getDate()`
(calendar) to `Timeslot.getBusinessDate()` (business) at every join/groupBy/computeIfAbsent key
position — is thorough, consistently documented, and matches the structural guard
(`BusinessDateJoinGuardTest`) that locks the four-file scope. `AgentDayConfig.dayStart`,
`AgentShiftAssignment.date`, the `resolveAnchor` null-coalescing fallback, and the
tagged-collector shape in `honourPreferredBreakTime` are all correct and match their own
documentation; none of those four "worth your attention" items in the phase brief turned up a
real defect.

However, the migration did not fully close the business-date-vs-calendar-date gap in
`SolverService`, which sits just outside the four-file structural guard's scope and was not
re-scanned with the same discipline. Two genuine defects were found, both in the exact bug class
this phase exists to eliminate, and both currently silent only because every live desk is
anchored at 00:00 — i.e. exactly the "latent, not harmless" risk the phase brief calls out:

1. A reachable REST endpoint (`timeslots/generate`) still hardcodes a midnight anchor instead of
   the desk's real `dayStart`, even though `DeskService.setDayStart` (this phase) now allows any
   15-minute boundary.
2. `SolverService.expandMinimumStaffingSeats` uses a timeslot's *calendar* date to look up a map
   that is keyed by *business* date, and to evaluate shift-template weekday/effective-range
   eligibility — the identical key-system bug this phase's own commit fixed four hundred lines
   below, in `requireShiftEnvelopeSeatSupply`, but left unfixed in this sibling function.

One additional, lower-severity robustness gap was found in the widened `DeskService.setDayStart`
validation (seconds/nanoseconds silently ignored).

## Critical Issues

### CR-01: `timeslots/generate` endpoint still anchors at hardcoded MIDNIGHT, not the desk's real day start

**File:** `src/main/java/com/wfm/controller/TimeslotController.java:45-55`
**Issue:** `TimeslotController.generateTimeslots` passes a hardcoded `LocalTime.MIDNIGHT` as the
`dayStart` argument to `TimeslotGeneratorService.generateTimeslots`, regardless of the desk's
actual `Desk.dayStart`:

```java
// BDAY-01: DeskService.setDayStart's 00:00-only gate makes any other anchor unreachable
// through this endpoint this phase; Phase 19 re-anchors this to the desk's own value.
List<Timeslot> generated = timeslotGeneratorService.generateTimeslots(
        deskId,
        request.periodStartDate(),
        request.periodEndDate(),
        LocalTime.MIDNIGHT,
        request.startTime(),
        request.endTime(),
        request.incrementMinutes()
);
```

The comment's own premise — "`DeskService.setDayStart`'s 00:00-only gate makes any other anchor
unreachable ... Phase 19 re-anchors this to the desk's own value" — is exactly what this phase
(SOLV-01, `DeskService.setDayStart`) invalidated: `setDayStart` now accepts any 15-minute
boundary (`DeskService.java:241-245`), but this call site was never updated to match. The
promised "Phase 19/20 re-anchor" landed for `FteUploadService` (which now correctly passes
`desk.getDayStart()`, with a comment explicitly noting the fix: `FteUploadService.java:146-151`)
but not for this controller.

`TimeslotGeneratorService.generateTimeslots` uses its `dayStart` parameter directly as the
anchor for every date/offset computation in the method (`DayWindow.startMinuteFromDayStart`,
`DayWindow.endMinuteFromDayStart`, `requireDayStartTiles`, and the business-day cursor walk) —
it never reads `desk.getDayStart()` itself. So a desk saved with a non-midnight `dayStart` that
is then regenerated through this endpoint gets every timeslot's calendar/business-date
relationship computed against the wrong anchor, silently.

This is reachable: the endpoint is live at `POST /api/v1/desks/{deskId}/timeslots/generate`, and
the frontend API client already exposes it (`frontend/src/api/client.ts:223-224`,
`timeslots.generate`), even though no page currently calls it. No test exercises this endpoint at
all (`grep -rln "TimeslotController" src/test/java` finds nothing) — `DeskDayStartGenerationReachabilityTest`,
the test that proves the generation-time tiling refusal is reachable, deliberately bypasses the
controller and calls `TimeslotGeneratorService.generateTimeslots` directly with
`reloaded.getDayStart()`, so it cannot and does not catch this gap.

Per the phase's own domain fact ("every live desk is anchored at 00:00 today ... a defect that
only manifests at a non-midnight anchor is latent, not harmless"): this is exactly that class of
defect, and it sits directly downstream of this phase's own `DeskService` change.

**Fix:**
```java
@PostMapping("/generate")
public ResponseEntity<List<TimeslotResponse>> generateTimeslots(@PathVariable UUID deskId,
                                                                  @RequestBody GenerateTimeslotsRequest request) {
    Desk desk = deskService.getDesk(deskId); // or inject DeskRepository directly
    List<Timeslot> generated = timeslotGeneratorService.generateTimeslots(
            deskId,
            request.periodStartDate(),
            request.periodEndDate(),
            desk.getDayStart(),
            request.startTime(),
            request.endTime(),
            request.incrementMinutes()
    );
    ...
}
```
and delete the stale BDAY-01 comment, mirroring `FteUploadService`'s fix.

### CR-02: `SolverService.expandMinimumStaffingSeats` keys a business-date map with a calendar date

**File:** `src/main/java/com/wfm/service/SolverService.java:1955, 1966`
**Issue:** Inside the SHIFT-mode branch of `expandMinimumStaffingSeats`:

```java
LocalDate tsDate = ts.getDate();
boolean covered = shiftBandPairs.stream()
        .filter(pair -> pair.template().isEffectiveOn(tsDate) && pair.template().appliesOn(tsDate))
        .anyMatch(pair -> pair.covers(ts, window));
if (!covered) {
    continue; // OR-1: the library does not reach this hour -- no seat
}

int target = Math.max(ScheduleConstraintProvider.MIN_AGENTS_PER_TIMESLOT,
        workingAgentDaysByDate == null
                ? 0
                : workingAgentDaysByDate.getOrDefault(ts.getDate(), 0));
```

`ts.getDate()` is `Timeslot`'s *calendar* date. `workingAgentDaysByDate` (built just above, at
`SolverService.java:400-402`) is keyed by *business* date:

```java
Map<LocalDate, Integer> workingAgentDaysByDate = shiftAssignments.stream()
        .collect(Collectors.groupingBy(AgentShiftAssignment::getDate, ...));
```

— and `AgentShiftAssignment::getDate` is documented, elsewhere in this same review's required
reading, as *already being* the business date (`AgentShiftAssignment.java:57-65`, SOLV-07).

This is the identical key-system mismatch that this phase's own diff explicitly found and fixed
four hundred lines below, in `requireShiftEnvelopeSeatSupply`
(`SolverService.java:1450-1467`), with an extensive comment describing exactly this failure mode:

> "Before this fix the two maps disagreed on a re-anchored (non-midnight) desk ... That made
> librarySupplySlots compute to zero and the gate throw a FALSE REFUSAL of a solvable desk ...
> The lookup's getOrDefault makes a key-system disagreement silent rather than loud, which is
> what made this bug invisible until a desk was actually re-anchored."

`expandMinimumStaffingSeats` was not touched by this phase's diff (confirmed via
`git diff d595097..HEAD -- SolverService.java`) and still has the pre-migration calendar-date
read in both of this method's own date-sensitive branches:

1. The `tsDate`-driven `isEffectiveOn`/`appliesOn` check decides whether a shift template is
   eligible for this timeslot's day — exactly the "weekday-invalid assignment" class this
   codebase already names and fixed elsewhere (`AgentShiftAssignment.getEligibleShiftBandPairs()`'s
   own javadoc: "a weekday a template explicitly marked as not applying to that day ... was the
   sole surviving driver of a frozen -8 hard score"). On a re-anchored desk, evaluating this
   against the calendar date instead of the business date re-opens that exact defect for
   minimum-staffing filler seats.
2. `workingAgentDaysByDate.getOrDefault(ts.getDate(), 0)` will silently return `0` (or, worse, an
   unrelated date's count, if some other business day's rostered-agent count happens to be keyed
   under that same calendar-date value) for any timeslot whose calendar date differs from its
   business date — under- or mis-provisioning the minimum-staffing top-up seats this method
   exists to create, with no error, no warning, and no failed test.

No test in the suite exercises `expandMinimumStaffingSeats` against a non-midnight-anchored desk
(cross-checked against every test file that references it:
`ShiftEnvelopeSupplyInvariantTest`, `ZeroDemandTimeslotCeilingTest`,
`ShiftModeMinimumStaffingSeatSupplyTest`, `ShiftDeskEndToEndRegressionTest`,
`MinimumStaffingSeatsTest` — none pass a desk with a non-midnight `dayStart`), so this gap is
silent exactly as `requireShiftEnvelopeSeatSupply`'s sibling bug was before it was fixed.

**Fix:**
```java
LocalDate tsDate = ts.getBusinessDate();
boolean covered = shiftBandPairs.stream()
        .filter(pair -> pair.template().isEffectiveOn(tsDate) && pair.template().appliesOn(tsDate))
        .anyMatch(pair -> pair.covers(ts, window));
if (!covered) {
    continue;
}

int target = Math.max(ScheduleConstraintProvider.MIN_AGENTS_PER_TIMESLOT,
        workingAgentDaysByDate == null
                ? 0
                : workingAgentDaysByDate.getOrDefault(ts.getBusinessDate(), 0));
```

## Warnings

### WR-01: `DeskService.setDayStart`'s 15-minute-boundary gate ignores seconds/nanoseconds

**File:** `src/main/java/com/wfm/service/DeskService.java:241-245`
**Issue:**
```java
int dayStartMinuteOfDay = dayStart.getHour() * 60 + dayStart.getMinute();
if (dayStartMinuteOfDay % 15 != 0) {
    throw new IllegalArgumentException(
            "Desk day start " + dayStart + " is not a 15-minute boundary");
}
```
only inspects `getHour()`/`getMinute()`. The gate this replaced was a strict `.equals(LocalTime.MIDNIGHT)` check, which only ever accepted an exact `00:00:00.000000000` — any nonzero
second or nanosecond was rejected along with any nonzero hour/minute. The new gate is strictly
weaker on that dimension: a value such as `06:00:01` or `21:15:00.5` (hour/minute pass the
modulus test) is now silently *accepted* and persisted on `Desk.dayStart`, even though it is not
actually a clean 15-minute-boundary value.

Downstream, most minute-of-day arithmetic in this codebase (`DayWindow`, the constraint
provider's `resolveAnchor`, etc.) is built from `getHour()*60+getMinute()` and would silently
truncate the stray seconds — but the no-op check in the same method (`dayStart.equals(desk.getDayStart())`,
line ~251) and any future `.equals()`-based comparison would not, creating a value that behaves
inconsistently depending on which code path reads it. `DeskServiceDayStartTest` and
`DeskDayStartGenerationReachabilityTest` (this phase's own new tests) only ever construct
`LocalTime.of(hour, minute)` values, so this gap is untested.

**Fix:** reject a nonzero second/nanosecond explicitly, alongside the modulus check:
```java
if (dayStart.getSecond() != 0 || dayStart.getNano() != 0) {
    throw new IllegalArgumentException(
            "Desk day start " + dayStart + " must not carry seconds or sub-second precision");
}
```

---

_Reviewed: 2026-10-01T00:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
