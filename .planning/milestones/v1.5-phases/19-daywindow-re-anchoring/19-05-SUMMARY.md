---
phase: 19-daywindow-re-anchoring
plan: 05
subsystem: solver
tags: [daywindow, bday-04, shift-bandpair, covers, timefold]

requires:
  - phase: 19-daywindow-re-anchoring
    provides: "19-03's DayWindow.anchoredAt(LocalTime) and the anchored* instance methods; 19-04's break-band/net-hours window threading, which this plan's anchor-source bindings reuse (ScheduleOutputService's window, ShiftLibraryGenerationService's BreakConfig.window())"
provides:
  - "ShiftBandPair.covers in exactly two shapes — covers(Timeslot, DayWindow) and the seven-argument static covers(..., DayWindow) — both reaching one implementation; no caller anywhere can omit the anchor"
  - "SolverService.startSolve binding one DayWindow from the loaded Desk and carrying it forward through requireShiftEnvelopeSeatSupply, coveredTimeslotsOnDate, forcedAgentDaysByTimeslotId, coveredSlotCountOnDate and expandMinimumStaffingSeats"
  - "ScheduleOutputService.buildAgentSchedule / buildConstraintViolations binding one window per public method from schedule.getScheduleConfig().dayStart()"
  - "ScheduleEnvelopeRepairService.repairVerified binding one window from the same Schedule accessor, threaded through candidatesFor"
  - "ShiftStartMixTargetService.computeTargets taking an explicit DayWindow parameter (P-02 rule 5), propagated to solveDate's coverage matrix"
affects: [19-06-daywindow-reanchoring, 19-07-daywindow-reanchoring, 19-08-daywindow-reanchoring]

actuals:
  tokens: 23354
  tasks: 1
  commits: 1
  plan_head_before: 48ab5fed4b3e0e6eedc8942220addedee867f3db

tech-stack:
  added: []
  patterns:
    - "Window bound once per public method (or once per top-level entry point for a group of package-private helpers), threaded as a parameter through every private helper that needs it — the same shape plan 19-04 established, applied here to SolverService's seat-supply/expansion helpers and ShiftStartMixTargetService's solveDate."
    - "A class with neither a DeskRepository nor a Schedule parameter of its own (P-02 rule 5) takes a DayWindow parameter and the caller propagates the window it already bound — applied to ShiftStartMixTargetService.computeTargets, whose only caller is SolverService.startSolve."

key-files:
  created: []
  modified:
    - src/main/java/com/wfm/model/ShiftBandPair.java
    - src/main/java/com/wfm/service/SolverService.java
    - src/main/java/com/wfm/service/ScheduleOutputService.java
    - src/main/java/com/wfm/service/ScheduleEnvelopeRepairService.java
    - src/main/java/com/wfm/service/ShiftStartMixTargetService.java
    - src/test/java/com/wfm/service/MidnightWindowSeamTest.java
    - src/test/java/com/wfm/service/MinimumStaffingSeatsTest.java
    - src/test/java/com/wfm/service/ScheduleEnvelopeRepairServiceTest.java
    - src/test/java/com/wfm/service/ScheduleOutputServiceShiftReportingTest.java
    - src/test/java/com/wfm/service/ScheduleServiceShiftSnapshotTest.java
    - src/test/java/com/wfm/service/ShiftEnvelopeSupplyGateTest.java
    - src/test/java/com/wfm/service/ShiftModeMinimumStaffingSeatSupplyTest.java
    - src/test/java/com/wfm/service/ShiftStartMixTargetServiceTest.java
    - src/test/java/com/wfm/service/SolverSeatExpansionAccess.java
    - src/test/java/com/wfm/service/SolverSeatSupplyGateAccess.java
    - src/test/java/com/wfm/solver/LiveShapeShiftDeskFixture.java
    - src/test/java/com/wfm/solver/SeatSupplyDistributionAnalysisTest.java
    - src/test/java/com/wfm/solver/ShiftDeskEndToEndRegressionTest.java
    - src/test/java/com/wfm/solver/ShiftEnvelopeSupplyInvariantTest.java
    - src/test/java/com/wfm/solver/ShiftModeFixtures.java
    - src/test/java/com/wfm/solver/ZeroDemandTimeslotCeilingTest.java

key-decisions:
  - "ShiftBandPair ends up in exactly TWO covers() shapes, not three. The dispatch's own grounded-counts note (pre-measured before this plan ran) flagged the file as having FOUR declarations and suggested the post-plan count should be 4 -> 3 (deleting only the one-argument instance form, keeping the six-argument static). I measured the file myself and read the plan's own must_haves/acceptance_criteria text, which unambiguously specifies two final shapes with the static form taking 'the six existing arguments plus a DayWindow' (seven parameters total) — that sentence describes only ONE static form. The six-argument static's only two remaining callers before this plan were ScheduleOutputService (lines 837/873) and MidnightWindowSeamTest (line 125) — both explicitly named in this plan's own task scope as call sites to re-point onto a real window. Keeping the six-argument deprecated delegate (which always resolves to DayWindow.anchoredAt(LocalTime.MIDNIGHT) internally) while re-pointing its only two callers elsewhere would have left a dead, unreachable method — or worse, left ScheduleOutputService silently midnight-anchored if I had NOT re-pointed it, which is exactly the T-19-17 threat this plan's own threat register names. Deleted both transitional forms; verified exactly 2 declarations remain (`grep -cE '(boolean|public|static).*covers\(' ShiftBandPair.java` == 2) and exactly 0 one-argument declarations remain."
  - "ShiftBandPair.java retains 2 occurrences of the literal `LocalTime.MIDNIGHT`, in the netHours() method's deprecated delegate (javadoc + body) — plan 19-04's transitional form, explicitly out of this plan's own file scope (its own javadoc says so). The plan's acceptance criteria literally ask for a zero count of `LocalTime.MIDNIGHT` across all five touched files; I did not touch netHours() (it has no covers() relationship and plan 19-04's own SUMMARY names it as a separate migration), so this one criterion is unmet by exactly the two pre-existing, out-of-scope lines. Every covers()-related MIDNIGHT literal in all five files is gone; the two that remain predate this plan and belong to a different method this plan was never scoped to touch."
  - "SolverService binds its one DayWindow in startSolve (the method that loads the Desk), not inside the static buildSchedule helper the plan's prose names — buildSchedule is static, takes no window-consuming call site, and only returns a Schedule; the binding has to live where the window is actually threaded forward to requireShiftEnvelopeSeatSupply and expandMinimumStaffingSeats, which are both called from startSolve after buildSchedule returns. This satisfies the plan's own intent ('bind the window there and carry it forward') without literally editing a method that has no use for it."
  - "Three pre-existing test fixtures (ScheduleOutputServiceShiftReportingTest, ScheduleServiceShiftSnapshotTest, ScheduleEnvelopeRepairServiceTest) built a Schedule with no dayStart and crashed on this plan's own new DayWindow.anchoredAt(schedule.getScheduleConfig().dayStart()) binding (IllegalArgumentException: dayStart must not be null) once the full suite ran. Fixed each by calling schedule.setDayStart(LocalTime.MIDNIGHT) explicitly at every Schedule-construction site in those three files (9 sites total) — matching the anchor_source_rule's own instruction that test callers pass an explicit midnight anchor — rather than adding a null-coalescing fallback inside ScheduleOutputService/ScheduleEnvelopeRepairService, which the threat register (T-19-17) and this plan's own zero-MIDNIGHT-literal acceptance criterion for those two files specifically forbid. This is a NEW surface this plan introduces (ScheduleOutputService and ScheduleEnvelopeRepairService did not read dayStart before), distinct from the 29 fixtures plan 19-03 already handled with a production-side fallback inside ScheduleConstraintProvider — that fallback's own execution-constraint note says not to further 'fix' those SPECIFIC fixtures, not that every future null-dayStart crash must be absorbed in production code."

requirements-completed: [BDAY-04]

coverage:
  - id: D1
    description: "ShiftBandPair.covers exists in exactly two shapes (covers(Timeslot, DayWindow) and the seven-argument static), both reaching one implementation; the transitional one-argument instance form and the transitional six-argument static form are both gone, so a caller that omits the anchor is a compile failure."
    requirement: BDAY-04
    verification:
      - kind: other
        ref: "grep -cE 'covers\\(Timeslot [A-Za-z]+\\)' src/main/java/com/wfm/model/ShiftBandPair.java == 0"
        status: pass
      - kind: other
        ref: "grep -cE '(boolean|public|static).*covers\\(' src/main/java/com/wfm/model/ShiftBandPair.java == 2"
        status: pass
      - kind: integration
        ref: "./gradlew compileJava compileTestJava (exit 0) — the tree-wide proof that no caller omits the anchor"
        status: pass
    human_judgment: false
  - id: D2
    description: "ScheduleOutputService and ScheduleEnvelopeRepairService each bind one window per public method from the Schedule they already receive (schedule.getScheduleConfig().dayStart()), and SolverService binds exactly one window in startSolve and carries it forward rather than re-deriving it per call site."
    requirement: BDAY-04
    verification:
      - kind: other
        ref: "grep -c 'getScheduleConfig().dayStart()' ScheduleOutputService.java == 2, ScheduleEnvelopeRepairService.java == 1"
        status: pass
      - kind: other
        ref: "grep -c 'anchoredAt' src/main/java/com/wfm/service/SolverService.java == 1"
        status: pass
    human_judgment: false
  - id: D3
    description: "MidnightWindowSeamTest's SeatLegality.covers helper moves to the seven-argument static form with no asserted value changed — the only edit plan 19-05 owns in that shared file, leaving plan 19-04's getNetHours lines untouched."
    requirement: BDAY-04
    verification:
      - kind: unit
        ref: "./gradlew test --tests com.wfm.service.MidnightWindowSeamTest (17 tests, 0 failures)"
        status: pass
      - kind: other
        ref: "git diff -- src/test/java/com/wfm/service/MidnightWindowSeamTest.java shows only the covers() call-shape line changed"
        status: pass
    human_judgment: false
  - id: D4
    description: "Full unfiltered suite is green, including all eight Phase 18 guard tests, after this plan's edits."
    requirement: BDAY-04
    verification:
      - kind: integration
        ref: "./gradlew test (full, unfiltered) -- 1134 tests, 0 failures, 0 errors, 4 skipped (pre-existing)"
        status: pass
      - kind: other
        ref: "Per-class XML tallies for MidnightBoundaryRegressionTest, MidnightBoundaryPropertyTest, BusinessDateWritePathGuardTest, TimeslotGeneratorBusinessDateTest, MidnightGapScanTest, MidnightWindowSeamTest, MidnightTimeArithmeticGuardTest, DayWindowTest -- all 0 failures, 0 errors"
        status: pass
    human_judgment: false

duration: 95min
completed: 2026-10-01
status: complete
---

# Phase 19 Plan 05: Retire The Transitional Covers Form, Every Caller Supplies A Window Summary

**`ShiftBandPair.covers` drops both transitional midnight-implicit forms (the one-argument instance delegate and the six-argument static delegate) down to its two real, window-taking shapes, and all five remaining production callers — `SolverService`, `ScheduleOutputService`, `ScheduleEnvelopeRepairService` and `ShiftStartMixTargetService` — now bind a real `DayWindow` instead of a compiled-in midnight anchor.**

## Performance

- **Duration:** ~95 min
- **Completed:** 2026-10-01T03:18:38Z
- **Tasks:** 1
- **Files modified:** 21 (5 main, 16 test)

## Accomplishments
- `ShiftBandPair.covers(Timeslot)` — the transitional one-argument form plan 19-03 kept to avoid breaking ten callers in one commit — is gone. `ShiftBandPair`'s six-argument static `covers(LocalTime, ..., LocalTime)` delegate is also gone, since this plan re-points its only two remaining callers (`ScheduleOutputService`'s two report-layer call sites and the `MidnightWindowSeamTest` guard test) onto the real seven-argument static form. `covers` now exists in exactly two shapes — `covers(Timeslot, DayWindow)` and the seven-argument static — both reaching one implementation.
- `SolverService.startSolve` binds one `DayWindow` (`DayWindow.anchoredAt(desk.getDayStart())`) immediately after loading the `Desk`, and carries it forward as a trailing parameter through `requireShiftEnvelopeSeatSupply`, `coveredTimeslotsOnDate`, `forcedAgentDaysByTimeslotId`, `coveredSlotCountOnDate` and `expandMinimumStaffingSeats` — one binding, five call sites, never re-derived.
- `ScheduleOutputService.buildAgentSchedule` and `buildConstraintViolations` each bind one window from `schedule.getScheduleConfig().dayStart()`, threaded through `computeDivergence`/`outOfEnvelopeAssignments` and `buildAcceptedConstraintViolations` respectively.
- `ScheduleEnvelopeRepairService.repairVerified` binds one window the same way, threaded through `candidatesFor`.
- `ShiftStartMixTargetService.computeTargets` — which holds neither a `DeskRepository` nor a `Schedule` parameter of its own — takes an explicit `DayWindow` parameter per P-02 rule 5; its only caller, `SolverService.startSolve`, passes the same window it already bound, so no second anchor source exists in that class.
- Every test caller across `com.wfm.service` and `com.wfm.solver` that called the retired forms now passes `DayWindow.anchoredAt(LocalTime.MIDNIGHT)` explicitly, with no asserted value changed anywhere.
- Full unfiltered `./gradlew test`: **1134 tests, 0 failures, 0 errors** (4 pre-existing skips, unrelated). All eight Phase 18 guard tests confirmed green individually.

## Task Commits

Each task was committed atomically:

1. **Task 1: Retire the transitional covers form and re-point its remaining callers** - `faed18a` (feat)

**Plan metadata:** (this commit, docs(19-05): complete plan)

## Files Created/Modified
- `src/main/java/com/wfm/model/ShiftBandPair.java` - deleted the one-argument instance `covers(Timeslot)` and the six-argument static `covers(...)` delegates; fixed the `netHours()` javadoc's now-dangling `{@link #covers(Timeslot)}` reference
- `src/main/java/com/wfm/service/SolverService.java` - binds `window` in `startSolve` right after loading `desk`; threaded into `requireShiftEnvelopeSeatSupply`, `coveredTimeslotsOnDate`, `forcedAgentDaysByTimeslotId`, `coveredSlotCountOnDate`, `expandMinimumStaffingSeats`, and `shiftStartMixTargetService.computeTargets`; updated the stale comment near `expandMinimumStaffingSeats` that named the deleted one-argument signature
- `src/main/java/com/wfm/service/ScheduleOutputService.java` - `buildAgentSchedule` and `buildConstraintViolations` bind a window each from `schedule.getScheduleConfig().dayStart()`; threaded through `computeDivergence`, `outOfEnvelopeAssignments`, `buildAcceptedConstraintViolations`
- `src/main/java/com/wfm/service/ScheduleEnvelopeRepairService.java` - `repairVerified` binds a window from the `Schedule` it receives; threaded through `candidatesFor`
- `src/main/java/com/wfm/service/ShiftStartMixTargetService.java` - `computeTargets` and `solveDate` take an explicit `DayWindow` parameter (P-02 rule 5); the one `covers()` call site in the coverage-matrix builder now passes it
- 16 test files across `src/test/java/com/wfm/service/` and `src/test/java/com/wfm/solver/` - every call site of the retired forms updated to the window-taking shape, explicit `DayWindow.anchoredAt(LocalTime.MIDNIGHT)` for test callers, no asserted value changed; `ShiftModeMinimumStaffingSeatSupplyTest`'s reflective signature check updated to include the new trailing `DayWindow.class` parameter; three fixtures (`ScheduleOutputServiceShiftReportingTest`, `ScheduleServiceShiftSnapshotTest`, `ScheduleEnvelopeRepairServiceTest`) gained an explicit `schedule.setDayStart(LocalTime.MIDNIGHT)` at every `Schedule`-construction site

## Decisions Made

See `key-decisions` in the frontmatter for full reasoning. In brief:
- `ShiftBandPair.covers` ends at exactly two shapes (not three) — both transitional forms retired, not just the one-argument instance form, because this plan's own task scope already re-points the six-argument static's only two remaining callers.
- `ShiftBandPair.java`'s 2 remaining `LocalTime.MIDNIGHT` literals belong to `netHours()`, out of this plan's file scope (plan 19-04's own transitional form) — documented as the one unmet acceptance-criterion literal, with justification.
- `SolverService`'s single `DayWindow` binds in `startSolve` (where `desk` is loaded and the window is actually threaded forward), not inside the static `buildSchedule` helper, which has no use for it.
- Three test fixtures crashing on a null `dayStart` were fixed by setting an explicit midnight anchor on the fixture, not by adding a production-side null fallback — the latter is exactly what T-19-17 and this plan's own acceptance criteria forbid for these two files.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Mechanical, pre-flagged by dispatch] `ShiftBandPair.covers` declaration-count target corrected from the dispatch's suggested 3 back to the plan's own literal 2**
- **Found during:** Task 1, before any edit (reconciling the dispatch's grounded-counts note against the plan's own acceptance_criteria text)
- **Issue:** The dispatch prompt's pre-measurement suggested the post-plan declaration count should be 3 (keep the six-argument static), but the plan's own must_haves/acceptance_criteria explicitly specify two final shapes, with the static form taking "the six existing arguments plus a DayWindow" (seven parameters) — a sentence that describes one static form, not two.
- **Fix:** Deleted both the one-argument instance delegate and the six-argument static delegate, re-pointing their last remaining callers (`ScheduleOutputService`, `MidnightWindowSeamTest`) onto the window-taking forms, exactly as the plan's own task action text instructs for those two call sites.
- **Files modified:** `src/main/java/com/wfm/model/ShiftBandPair.java`, `src/main/java/com/wfm/service/ScheduleOutputService.java`, `src/test/java/com/wfm/service/MidnightWindowSeamTest.java`
- **Verification:** `grep -cE '(boolean|public|static).*covers\(' ShiftBandPair.java` reports 2; full suite green.
- **Committed in:** `faed18a` (Task 1 commit)

**2. [Rule 1 - Bug, caught by this plan's own mandated full-suite run] Three pre-existing test fixtures crashed on this plan's new null-dayStart-sensitive binding**
- **Found during:** Task 1's mandated full unfiltered `./gradlew test` run (after the targeted `MidnightWindowSeamTest`/`ScheduleEnvelopeRepairServiceTest` runs passed)
- **Issue:** `ScheduleOutputServiceShiftReportingTest` (13 failures), `ScheduleServiceShiftSnapshotTest` (5 failures) and `ShiftModeMinimumStaffingSeatSupplyTest` (1 failure, a reflective signature check) all failed. The first two built a `Schedule` via `new Schedule()` with no `setDayStart` call, which `ScheduleOutputService.buildAgentSchedule`/`buildConstraintViolations`'s new `DayWindow.anchoredAt(schedule.getScheduleConfig().dayStart())` binding throws `IllegalArgumentException` on. The third was a `getDeclaredMethod` call asserting `expandMinimumStaffingSeats`'s exact parameter-type list, which no longer matched after the new trailing `DayWindow` parameter.
- **Fix:** Added `schedule.setDayStart(LocalTime.MIDNIGHT)` at every `Schedule`-construction site in the two fixture files (9 sites total, via `replace_all` matched against each file's own unique `setIncrementMinutes` call count) — an explicit test-side midnight anchor, not a production fallback. Added `DayWindow.class` to the reflective parameter-type list in the third file.
- **Files modified:** `src/test/java/com/wfm/service/ScheduleOutputServiceShiftReportingTest.java`, `src/test/java/com/wfm/service/ScheduleServiceShiftSnapshotTest.java`, `src/test/java/com/wfm/service/ShiftModeMinimumStaffingSeatSupplyTest.java`
- **Verification:** Full unfiltered `./gradlew test` afterward — 1134 tests, 0 failures, 0 errors.
- **Committed in:** `faed18a` (Task 1 commit; caught and fixed before committing)

**3. [Rule 3 - Blocking, compiler-forced] ~40 test call sites across 13 files needed a trailing `DayWindow` argument added**
- **Found during:** Task 1 (compile step, after `expandMinimumStaffingSeats`/`requireShiftEnvelopeSeatSupply`/`forcedAgentDaysByTimeslotId`/`computeTargets`'s signatures changed)
- **Issue:** Every existing test caller of the four re-signatured `SolverService`/`ShiftStartMixTargetService` methods, plus the two test-only `SolverSeatSupplyGateAccess`/`SolverSeatExpansionAccess` bridge classes, needed a trailing `DayWindow.anchoredAt(LocalTime.MIDNIGHT)` argument added to keep compiling.
- **Fix:** Added the argument mechanically at every call site (each one individually verified not to run through a string literal or nested nested nested parens before applying), added `import com.wfm.util.DayWindow;` where missing. Four `pair::covers` method references (no longer type-compatible as a `Predicate<Timeslot>`) were converted to explicit lambdas passing `DayWindow.anchoredAt(LocalTime.MIDNIGHT)`.
- **Files modified:** the 15 test files listed under Files Created/Modified above (excluding the three fixed under Deviation 2) plus `SolverSeatExpansionAccess.java`/`SolverSeatSupplyGateAccess.java`
- **Verification:** `./gradlew compileJava compileTestJava` exits 0; full suite green.
- **Committed in:** `faed18a` (Task 1 commit)

---

**Total deviations:** 3 auto-fixed (1 Rule 3 mechanical correction to a pre-flagged dispatch note, 1 Rule 1 bug caught by the plan's own mandated full-suite run, 1 Rule 3 compiler-forced mechanical update spanning ~40 call sites)
**Impact on plan:** None of these change what any production caller's anchor resolves to, or weaken the zero-midnight-fallback discipline this plan exists to establish — the two production files that needed a defensive fix (Deviation 2) were fixed at the TEST level specifically to preserve that discipline rather than erode it.

## Issues Encountered
None beyond the three deviations above, all resolved before the task commit.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- `ShiftBandPair.covers` is down to its final two shapes; no `DayWindow`-aware model-class signature in this phase's scope carries a transitional delegate anymore (compare `netHours()`, left for a later plan per its own javadoc).
- `SolverService`'s single bound window (in `startSolve`) is the binding plan 19-07 must reuse rather than introducing a second anchor source in that class — it already carries the window through every `covers()` call site; plan 19-07 owns the remaining DIRECT `DayWindow` static calls in that file (the `durationMinutes` calls at lines ~1103/1221, confirmed still present and untouched by this plan).
- `ScheduleOutputService`'s two window bindings (`buildAgentSchedule`, `buildConstraintViolations`) are the binding plan 19-06 must reuse for that file's own remaining direct `DayWindow` static calls (`plusWithinDay`, `endMinute`, `startMinute`, `overlaps` — all confirmed still present, untouched by this plan).
- No blockers. Full unfiltered `./gradlew test` is green (1134 tests, 0 failures, 0 errors, 4 pre-existing skips).

## Self-Check: PASSED

- FOUND: `src/main/java/com/wfm/model/ShiftBandPair.java`
- FOUND: `src/main/java/com/wfm/service/SolverService.java`
- FOUND: `src/main/java/com/wfm/service/ScheduleOutputService.java`
- FOUND: `src/main/java/com/wfm/service/ScheduleEnvelopeRepairService.java`
- FOUND: `src/main/java/com/wfm/service/ShiftStartMixTargetService.java`
- FOUND commit `faed18a` (task commit) in `git log --oneline --all`
- CONFIRMED: `grep -cE 'covers\(Timeslot [A-Za-z]+\)' src/main/java/com/wfm/model/ShiftBandPair.java` reports `0`
- CONFIRMED: `grep -cE '(boolean|public|static).*covers\(' src/main/java/com/wfm/model/ShiftBandPair.java` reports `2`
- CONFIRMED: `grep -c 'anchoredAt' src/main/java/com/wfm/service/SolverService.java` reports `1`
- CONFIRMED: `grep -c 'getScheduleConfig().dayStart()'` reports `2` for ScheduleOutputService.java, `1` for ScheduleEnvelopeRepairService.java
- CONFIRMED: `./gradlew compileJava compileTestJava` exits 0
- CONFIRMED: `./gradlew test --tests "com.wfm.service.MidnightWindowSeamTest" --tests "com.wfm.service.ScheduleEnvelopeRepairServiceTest"` — both green
- CONFIRMED: full, unfiltered `./gradlew test` — 1134 tests, 0 failures, 0 errors, 4 skipped
- CONFIRMED: all eight Phase 18 guard test classes individually 0 failures / 0 errors
- CONFIRMED: `git diff --name-only HEAD~1 HEAD` touches exactly the 21 files listed above, every one referencing `DayWindow`

---
*Phase: 19-daywindow-re-anchoring*
*Completed: 2026-10-01*
