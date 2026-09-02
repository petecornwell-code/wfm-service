---
phase: 15-shift-envelope-breaks-library-generation
reviewed: 2026-09-02T15:55:00Z
depth: standard
files_reviewed: 15
files_reviewed_list:
  - src/main/java/com/wfm/controller/GlobalExceptionHandler.java
  - src/main/java/com/wfm/service/ScheduleOutputService.java
  - src/main/java/com/wfm/service/ScheduleService.java
  - src/main/java/com/wfm/service/ShiftLibraryGenerationService.java
  - src/main/java/com/wfm/service/SolverService.java
  - src/main/java/com/wfm/solver/ScheduleConstraintProvider.java
  - src/test/java/com/wfm/controller/GlobalExceptionHandlerTest.java
  - src/test/java/com/wfm/service/ScheduleOutputServiceShiftReportingTest.java
  - src/test/java/com/wfm/service/ScheduleServiceShiftSnapshotTest.java
  - src/test/java/com/wfm/service/ShiftEnvelopeSupplyGateTest.java
  - src/test/java/com/wfm/service/ShiftLibraryGenerationServiceTest.java
  - src/test/java/com/wfm/service/SolverSeatSupplyGateAccess.java
  - src/test/java/com/wfm/solver/SeatSupplyDistributionAnalysisTest.java
  - src/test/java/com/wfm/solver/ShiftDeskEndToEndRegressionTest.java
  - src/test/java/com/wfm/solver/ShiftEnvelopeSupplyInvariantTest.java
findings:
  critical: 0
  warning: 1
  info: 1
  total: 2
status: issues_found
---

# Phase 15: Code Review Report — Gap Closure Round (plans 15-16..15-20)

**Reviewed:** 2026-09-02T15:55:00Z
**Depth:** standard
**Files Reviewed:** 15
**Status:** issues_found (no Critical findings; one Warning, one Info)

## Summary

This round closes seven gaps (G-15-32/G-15-26, G-15-23, G-15-21/G-15-24, G-15-25/G-15-31) across
five production files plus their tests. I traced each of the round's specific claims against the
actual diff rather than trusting the commit messages / javadoc:

- **15-16** (`GlobalExceptionHandler`, `ScheduleOutputService`, `ScheduleService`): the accepted
  read path now derives violations from the persisted D-07 snapshot
  (`buildAcceptedConstraintViolations`) instead of calling `solutionManager.explain` on a score
  director missing its shift problem facts — verified this actually replaces the constant-1104
  code path, that `isAcceptedSnapshot` is threaded from `ScheduleService`'s own `fromDb` local (not
  re-inferred), and that the old `constraintWeights == null` guard is demoted to a live-path-only
  safety net. Confirmed via a fresh, non-cached `cleanTest` run that all 8 touched test classes'
  suites pass. The 405 handler is additive and does not reorder or shadow existing handlers —
  Spring dispatches `@ExceptionHandler` by most-specific exception type, not declaration order, so
  its placement above the catch-all is cosmetic as the code's own comment states.
- **15-17** (`ShiftLibraryGenerationService`): dedupe now runs after band selection, keyed on full
  identity (start, end, weekdays, ordered band tuple) rather than span alone — traced through
  `dedupeKey`/`EmittedRow` and confirmed a same-span/different-weekday collapse cannot occur.
  Demand-ranked band placement reuses the exact same `[incrementMinutes, spanLengthMinutes -
  duration - incrementMinutes]` bound `enumerateCandidates` already enforced when generating the
  candidate, so a candidate's own coverage-bearing offset is always a member of `admissibleOffsets`
  — the `chosen.stream().max(...).orElseThrow()` eviction path in the coverage-recheck branch can
  therefore never run against an empty set (verified this isn't independently reachable, not just
  "the tests don't happen to hit it"). No band can land at the envelope's first or last grid slot;
  the admissible bounds are unchanged from the outward-walk implementation this replaces.
- **15-18/15-20** (`SolverService`): `coveredTimeslotsOnDate` is now the single date-aware
  definition shared by the blocking check and the trailing advisory (previously two textually
  duplicated calendar-blind `anyMatch` expressions). `requireShiftEnvelopeSeatSupply`'s new
  `ConstraintWeights weights` parameter is null-safe on every path — the one production call site
  never passes null (falls back to `new ConstraintWeights()`, whose `unassignedAssignmentWeight`
  defaults to `ofSoft(1000)`), and null is treated identically to that default. The new per-hour
  `forcedAgentDaysByTimeslotId` check accumulates into the same error list as the pre-existing
  day-wide check (both can legitimately fire together, per the round's own test comments) without
  ever double-reporting the retired/weekday-invalid-library case — I traced that
  `distinctLibraryDefectReportedForDate` suppresses only the day-wide numeric-shortfall check, and
  confirmed independently that when a whole date's library is retired or weekday-invalid, every
  row's `getEligibleShiftBandPairs()` is *also* empty by construction (identical
  `isEffectiveOn`/`appliesOn` predicates in `AgentShiftAssignment`), so `forcedAgentDaysByTimeslotId`
  naturally skips all such rows and contributes nothing extra — this is a structural guarantee, not
  a coincidence that happens to hold only for the shipped tests.
- **15-19**: confirmed via `git show --stat` on every commit in the plan's range that it touched
  only test and `.planning` files — no production code changed, matching its own claim.
- `SolverSeatSupplyGateAccess`'s new `forcedAgentDaysByTimeslotId` bridge method stays a thin
  forward to the still-package-private `SolverService` method; it does not widen production
  visibility (the bridge class itself lives in `src/test`, and both bridged methods remain
  package-private, non-public, in `SolverService`).

I ran a `./gradlew cleanTest` against all eight touched test classes plus a full
`compileJava`/`compileTestJava` for the whole module — all pass.

The two findings below are a narrow, low-probability defensive gap in the new accepted-path
violation report and a stale comment; neither is a regression in the round's stated goals, and
neither was fabricated to pad the report — I traced each to a concrete, if unlikely, runtime
scenario.

## Warnings

### WR-01: Accepted-path violation report can show a HARD violation with a zero weight/penalty when `ConstraintWeights` is unresolved

**File:** `src/main/java/com/wfm/service/ScheduleOutputService.java` (`buildAcceptedConstraintViolations`, the block computing `weightDto`/`totalPenalty`)

**Issue:** The live-solver path in `buildConstraintViolations` still guards on
`schedule.getConstraintWeights() == null` and returns `List.of()` before building any report
("Secondary safety net for the LIVE-SOLVER path only"). The new accepted-path branch
(`isAcceptedSnapshot == true`, dispatched *before* that guard) has no equivalent guard:
`buildAcceptedConstraintViolations` proceeds to compute out-of-envelope violations regardless of
whether `schedule.getConstraintWeights()` is null, and silently falls back to `HardSoftScore.ZERO`
for the displayed weight:

```java
ConstraintWeights weights = schedule.getConstraintWeights();
HardSoftScore envelopeWeight = weights != null
        ? weights.getShiftEnvelopeComplianceWeight()
        : HardSoftScore.ZERO;
...
return List.of(new ConstraintViolationEntry(
        ScheduleConstraintProvider.SHIFT_ENVELOPE_COMPLIANCE_CONSTRAINT_NAME,
        "HARD", weightDto, violationCount, totalPenalty, violations));
```

**Concrete failure scenario:** an accepted schedule genuinely has out-of-envelope seats, but its
desk's `ConstraintWeights` row is unavailable at read time (e.g. deleted after acceptance, or a
legacy desk row that predates weights being seeded). The response then names the constraint level
as `"HARD"` and reports a non-zero `violationCount`, but shows `weight = {hard: 0, soft: 0}` and
`totalPenalty = {hard: 0, soft: 0}` — an internally inconsistent "hard violation worth nothing"
answer, one instance of exactly the class of contradiction (a schedule's own reported numbers
disagreeing with each other) this whole gap-closure round exists to eliminate on the
`feasible`/`violatedHardConstraints` axis. In practice this is low-probability today because
`DeskService.createDesk` always seeds a default `ConstraintWeights` row at desk creation, but the
fallback path itself is untested: `ScheduleOutputServiceShiftReportingTest` and
`ScheduleServiceShiftSnapshotTest` both exercise null-weights only on the *live* path
(`buildConstraintViolations_liveUnaccepted_nullWeightsGuardStaysScopedToLivePath`) — there is no
accepted-path test for a violation-plus-null-weights combination, so this behavior was never
pinned as intentional.

**Fix:** Either extend the guard to also apply to the accepted path when weights are null and
violations are non-empty, or make the "unknown weight" case explicit instead of a misleading zero:
```java
HardSoftScore envelopeWeight = weights != null ? weights.getShiftEnvelopeComplianceWeight() : null;
ScheduleSummary.ScoreDto weightDto = envelopeWeight != null
        ? new ScheduleSummary.ScoreDto(envelopeWeight.hardScore(), envelopeWeight.softScore())
        : null; // explicit "unknown", never a false zero
```
and add a test pinning whichever behavior is chosen.

## Info

### IN-01: Stale comment no longer describes what the accepted path does

**File:** `src/main/java/com/wfm/service/ScheduleService.java:477`

**Issue:** `// Load constraint weights so buildConstraintViolations can explain the score` predates
this round's change. On the accepted path, `buildConstraintViolations` no longer calls
`solutionManager.explain` at all (that is precisely the bug this round fixed) — the loaded
`ConstraintWeights` is now used only to look up `shiftEnvelopeComplianceWeight` for display in
`buildAcceptedConstraintViolations`. The comment will mislead a future reader into thinking the
accepted path still explains the score via Timefold.

**Fix:**
```java
// Load constraint weights so the accepted-path violation report can display the shift-envelope
// weight (buildAcceptedConstraintViolations) — the live-path explain() is not used here.
```

---

_Reviewed: 2026-09-02T15:55:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
