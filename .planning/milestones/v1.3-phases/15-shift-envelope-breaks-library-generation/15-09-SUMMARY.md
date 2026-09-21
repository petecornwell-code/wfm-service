---
phase: 15-shift-envelope-breaks-library-generation
plan: 09
subsystem: solver
tags: [timefold, shift-envelope, minimum-staffing, gap-closure]

# Dependency graph
requires:
  - phase: 15-shift-envelope-breaks-library-generation
    provides: shift envelope model (ShiftBandPair, ShiftTemplate, AgentShiftAssignment), SchedulingMode split, XCUT-05 classification table
provides:
  - Envelope-aware SolverService.expandMinimumStaffingSeats (SchedulingMode/ShiftBandPair/working-agent-day-count parameters)
  - SolverSeatExpansionAccess test-only bridge exposing production seat expansion to com.wfm.solver fixtures
  - ShiftModeFixtures widened to a strictly-wider-than-envelope operating window, filler seats routed through production
  - ZeroDemandTimeslotCeilingTest documenting why the missing-ceiling fact is now safe, not a hazard
  - XCUT-05 "Minimum staffing" row corrected to MODE_GATED
affects: [16-usual-shift-storage, 17-consistency-constraint-drift-reporting]

# Actuals (#2632)
actuals:
  tokens: 18238
  tasks: 3
  commits: 4

tech-stack:
  added: []
  patterns:
    - "Test-only cross-package bridge (SolverSeatExpansionAccess) to expose a package-private production method to a fixture in another test package, instead of widening main-source visibility or reimplementing the method in the fixture"
    - "Shared base-fact construction (buildBase/BaseBuild) with mode-independent filler-seat expansion computed separately per caller, so two sibling fixture builders can share desk-shape facts without one silently inheriting the other's mode-specific seat computation"

key-files:
  created:
    - src/test/java/com/wfm/service/ShiftModeMinimumStaffingSeatSupplyTest.java
    - src/test/java/com/wfm/service/SolverSeatExpansionAccess.java
    - src/test/java/com/wfm/solver/ZeroDemandTimeslotCeilingTest.java
  modified:
    - src/main/java/com/wfm/service/SolverService.java
    - src/main/java/com/wfm/solver/ScheduleConstraintProvider.java
    - src/test/java/com/wfm/solver/ShiftModeFixtures.java
    - src/test/java/com/wfm/solver/ScheduleConstraintClassification.java
    - src/test/java/com/wfm/service/MinimumStaffingSeatsTest.java
    - src/test/java/com/wfm/solver/ScheduleConstraintClassificationTest.java

key-decisions:
  - "expandMinimumStaffingSeats gained SchedulingMode + List<ShiftBandPair> + per-date working-agent-day Map parameters rather than an overload, matching the plan's literal instruction; existing SLOT-mode callers (MinimumStaffingSeatsTest) were updated to pass SchedulingMode.SLOT/empty pairs/empty map -- a Rule 3 compile-blocking fix, not a behavioural change"
  - "buildSlotModeSchedule computes its own SLOT-mode filler seats independently rather than inheriting buildShiftModeSchedule's SHIFT-computed ones, so the SLOT arm keeps its correct unconditional every-zero-seat-timeslot top-up instead of silently absorbing SHIFT-mode's coverage suppression"
  - "ShiftModeFixtures' operating window widened by exactly one INCREMENT_MINUTES (15min) on each side of the envelope -- the minimum shape that proves an out-of-envelope timeslot can exist, keeping the fixture's blast radius small"

requirements-completed: [ENVL-02, ENVL-04, XCUT-05]

coverage:
  - id: D1
    description: "SolverService.expandMinimumStaffingSeats suppresses filler seats on SHIFT-desk timeslots no live ShiftBandPair covers (operator ruling OR-1)"
    requirement: "ENVL-02"
    verification:
      - kind: unit
        ref: "com.wfm.service.ShiftModeMinimumStaffingSeatSupplyTest#uncoveredHourGetsNoSeats"
        status: pass
      - kind: unit
        ref: "com.wfm.solver.ZeroDemandTimeslotCeilingTest#shiftMode_uncoveredZeroDemandTimeslot_carriesNoSeatAtAll"
        status: pass
    human_judgment: false
  - id: D2
    description: "A covered, zero-forecast-demand SHIFT timeslot gets max(MIN_AGENTS_PER_TIMESLOT, workingAgentDaysOn(date)) seats, cycling desk specializations starting at the predominant one"
    requirement: "ENVL-04"
    verification:
      - kind: unit
        ref: "com.wfm.service.ShiftModeMinimumStaffingSeatSupplyTest#coveredZeroDemandHourGetsSeatPerWorkingAgentDay"
        status: pass
      - kind: unit
        ref: "com.wfm.service.ShiftModeMinimumStaffingSeatSupplyTest#fillerSeatsCycleSpecializationsDeterministically"
        status: pass
    human_judgment: false
  - id: D3
    description: "SLOT-mode seat expansion output is unchanged -- element-for-element identical whether or not shift context is supplied"
    verification:
      - kind: unit
        ref: "com.wfm.service.ShiftModeMinimumStaffingSeatSupplyTest#slotModeOutputIsInvariantToShiftContext"
        status: pass
      - kind: unit
        ref: "com.wfm.service.MinimumStaffingSeatsTest (all 7 tests, unmodified assertions against the new signature)"
        status: pass
    human_judgment: false
  - id: D4
    description: "ShiftModeFixtures' operating window is strictly wider than every template envelope, and its filler seats originate in SolverService.expandMinimumStaffingSeats via the SolverSeatExpansionAccess bridge"
    verification:
      - kind: unit
        ref: "com.wfm.solver.ShiftEnvelopeGroundTruthTest, ShiftModeBreakGatingTest, BreakClusteringConstraintTest, ScheduleConstraintClassificationTest, MinimumStaffingConstraintTest (all re-verified green, no consumer needed a correction)"
        status: pass
    human_judgment: false
  - id: D5
    description: "XCUT-05 classification table no longer claims Minimum staffing is mode-independent"
    requirement: "XCUT-05"
    verification:
      - kind: unit
        ref: "com.wfm.solver.ScheduleConstraintClassificationTest#thePhase15ModeGatedSetIsExactlyTheTenExpectedRows"
        status: pass
    human_judgment: false

duration: 46min
completed: 2026-08-27
status: complete
---

# Phase 15 Plan 09: Envelope-Aware Minimum-Staffing Seat Expansion Summary

**`SolverService.expandMinimumStaffingSeats` now branches on `SchedulingMode`: a SHIFT desk suppresses filler seats at timeslots no live shift envelope reaches (operator ruling OR-1) and guarantees enough seats at covered zero-demand timeslots for every working agent-day, while `ShiftModeFixtures` was widened to actually be able to construct that out-of-envelope shape.**

## Performance

- **Duration:** 46 min
- **Started:** 2026-08-27T15:20:00Z (approx.)
- **Completed:** 2026-08-27T16:06:23Z
- **Tasks:** 3
- **Files modified/created:** 9

## Accomplishments

- Closed the D2 half of gap G-15-10: `SolverService.expandMinimumStaffingSeats` gained a `SchedulingMode`, the desk's live `List<ShiftBandPair>`, and a per-date working-agent-day count, and now branches per timeslot — SLOT desks (or a SHIFT desk with an empty library) keep the unconditional top-up unchanged; a SHIFT desk suppresses seats where no pair covers the timeslot and guarantees `max(MIN_AGENTS_PER_TIMESLOT, workingAgentDaysOn(date))` seats where a pair does cover it but demand doesn't.
- Fixed the instrument that could not see the defect: `ShiftModeFixtures`' operating window is now strictly wider than every template's envelope (one 15-min increment on each side), and both `buildShiftModeSchedule` and `buildSlotModeSchedule` route their filler seats through production's real `expandMinimumStaffingSeats` (via the new `SolverSeatExpansionAccess` test-only bridge), each computed independently in its own scheduling mode.
- Corrected the XCUT-05 classification table: "Minimum staffing" is now `MODE_GATED`, not `MODE_AGNOSTIC` — its reachable domain is mode-dependent because seat supply is.
- Converted the two characterising test files under `.planning/debug/characterising-tests/` into permanent regression tests (`ShiftModeMinimumStaffingSeatSupplyTest`, `ZeroDemandTimeslotCeilingTest`), both observed RED against unmodified source before the fix.
- Full suite verified green: 516 tests, 0 failures, 0 errors.

## Task Commits

1. **Task 1 (RED): failing tests for envelope-aware seat expansion** - `b1a905b` (test)
2. **Task 1 (GREEN): envelope-aware minimum-staffing seat expansion** - `f893e97` (feat)
3. **Task 2: widen ShiftModeFixtures to express an out-of-envelope timeslot** - `2e97c19` (feat)
4. **Task 3: reclassify Minimum staffing as MODE_GATED** - `2aa3ddf` (fix)

**Plan metadata:** (this commit, docs: complete plan)

_Task 1 is `type="tracer" tdd="true"` — RED then GREEN commits, per the TDD flow._

## Pre-fix RED Evidence

**Test 1/2/6 (`ShiftModeMinimumStaffingSeatSupplyTest`)** — observed RED as a **compile failure** against unmodified source, since the six-behaviour test suite calls the method with the new 10-argument signature the unmodified method does not have:

```
error: method expandMinimumStaffingSeats in class SolverService cannot be applied to given types;
  required: long,UUID,UUID,List<Timeslot>,List<AgentAssignment>,List<StaffingRequirement>,List<Specialization>
  found:    long,UUID,UUID,List<Timeslot>,ArrayList<AgentAssignment>,List<StaffingRequirement>,List<Specialization>,SchedulingMode,List<ShiftBandPair>,Map<LocalDate,Integer>
  reason: actual and formal argument lists differ in length
```

This mirrors (and supersedes) the pre-existing diagnose-only characterising evidence in `.planning/debug/min-staffing-seats-zero-demand.md`, where `theMethodIsStructurallyEnvelopeBlind` asserted via reflection that the unmodified method's parameter list contained neither `SchedulingMode` nor `ShiftBandPair` — the same structural fact, now inverted and pinned as a regression test (Test 6).

## Files Created/Modified

- `src/main/java/com/wfm/service/SolverService.java` - `expandMinimumStaffingSeats` gains SchedulingMode/ShiftBandPair-list/working-agent-day-count params and branches on mode; new `fillerSeat`/`specializationCycleFrom` private helpers; step-10d call site computes the per-date working-agent-day map from `shiftAssignments`
- `src/main/java/com/wfm/solver/ScheduleConstraintProvider.java` - `minimumStaffing` javadoc corrected: the "solver packs every shift against the first non-zero hour" premise is SLOT-mode-only; on SHIFT mode the floor is reachable only at timeslots a live pair covers
- `src/test/java/com/wfm/service/ShiftModeMinimumStaffingSeatSupplyTest.java` (new) - six regression tests for the envelope-aware branch, SLOT invariance, and the structural signature check
- `src/test/java/com/wfm/service/SolverSeatExpansionAccess.java` (new) - test-only public bridge in `com.wfm.service`, forwarding to the package-private `expandMinimumStaffingSeats`, so `com.wfm.solver` fixtures can call production's real expansion
- `src/test/java/com/wfm/service/MinimumStaffingSeatsTest.java` - seven call sites updated to the new 10-arg signature (`SchedulingMode.SLOT, List.of(), Map.of()`), assertions unchanged
- `src/test/java/com/wfm/solver/ShiftModeFixtures.java` - `OPERATING_START`/`OPERATING_END` (grid) split from new `ENVELOPE_START`/`ENVELOPE_END` (template envelope, unchanged values); shared `buildBase`/`BaseBuild` extraction; both mode builders route filler seats through `SolverSeatExpansionAccess`, each in its own mode; production-returned seat ids re-stamped with the fixture's sequential counter
- `src/test/java/com/wfm/solver/ZeroDemandTimeslotCeilingTest.java` (new) - converted from the characterising test; retains the falsification control, documents why the missing-ceiling fact is now safe, adds SHIFT-uncovered-no-seat and SLOT-unchanged assertions
- `src/test/java/com/wfm/solver/ScheduleConstraintClassification.java` - "Minimum staffing" row changed `MODE_AGNOSTIC` → `MODE_GATED` with a new basis; `MODE_GATED` tag javadoc reworded to stop repeating a row count that had already gone stale once
- `src/test/java/com/wfm/solver/ScheduleConstraintClassificationTest.java` - `thePhase15ModeGatedSetIsExactlyTheNineExpectedRows` renamed to `...TenExpectedRows`, `"Minimum staffing"` added to the expected `MODE_GATED` set

## Decisions Made

- Extended the existing method signature (per the plan's literal instruction) rather than adding an overload — this is a compile-breaking change for existing callers, resolved via Rule 3 (see Deviations).
- `buildSlotModeSchedule` recomputes filler seats independently in `SchedulingMode.SLOT`, rather than reusing `buildShiftModeSchedule`'s already-SHIFT-computed filler seats, to avoid silently giving the SLOT-mode benchmark arm SHIFT-mode's coverage suppression.
- The fixture's operating window widened by exactly one `INCREMENT_MINUTES` (15 min) on each side of the envelope, per the plan's "one increment before... at least one increment after" instruction — the minimal shape that proves an out-of-envelope timeslot is constructible.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Updated `MinimumStaffingSeatsTest.java`'s seven call sites to the new signature**
- **Found during:** Task 1
- **Issue:** Extending `expandMinimumStaffingSeats`'s signature (per the plan's literal instruction) broke compilation of this pre-existing SLOT-mode test file, which was not listed in the plan's `files_modified`.
- **Fix:** Added `SchedulingMode`/`Map` imports and appended `SchedulingMode.SLOT, List.of(), Map.of()` to each of the seven call sites. No assertion text changed.
- **Files modified:** `src/test/java/com/wfm/service/MinimumStaffingSeatsTest.java`
- **Verification:** All 7 tests pass unmodified — this itself proves the plan's "same count, same timeslots, same specialization, same order" SLOT-invariance must-have.
- **Committed in:** `f893e97` (Task 1 GREEN commit)

**2. [Rule 3 - Blocking] Updated `ScheduleConstraintClassificationTest.java`'s exact-set assertion**
- **Found during:** Task 3
- **Issue:** Reclassifying "Minimum staffing" to `MODE_GATED` made the existing `thePhase15ModeGatedSetIsExactlyTheNineExpectedRows` test's hardcoded 9-element expected set stale — a compile-passing, assertion-failing consequence not listed in the plan's `files_modified`.
- **Fix:** Renamed the test to `...TenExpectedRows`, added `"Minimum staffing"` to the expected set, and updated the assertion message. The two independent reflection-derived completeness checks (`bothReflectionDerivationsAgreeWithEachOther`, `classificationKeySetExactlyEqualsTheRegisteredConstraintSet`) needed no change and still pass.
- **Files modified:** `src/test/java/com/wfm/solver/ScheduleConstraintClassificationTest.java`
- **Verification:** `./gradlew test --tests 'com.wfm.solver.ScheduleConstraintClassificationTest'` — 7/7 pass.
- **Committed in:** `2aa3ddf` (Task 3 commit)

---

**Total deviations:** 2 auto-fixed (both Rule 3 — blocking compile/assertion consequences of a signature change and a reclassification the plan itself mandated)
**Impact on plan:** Both fixes are mechanical consequences of exactly the changes the plan specified; neither introduces new behavior or scope. No scope creep.

## Issues Encountered

None. The full test suite (516 tests) ran clean on the first attempt after Task 3's reclassification; no node-repair cycles were needed on any task.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- G-15-10's D2 half (shift-envelope minimum-staffing seat supply) is closed: a SHIFT desk no longer manufactures an out-of-envelope filler seat at any zero-demand hour, and the test suite is now structurally capable of catching a regression (`ShiftModeFixtures` can express the defect shape; before this plan it could not).
- Explicitly NOT addressed by this plan (per its own scope boundary): `shiftEnvelopeComplianceWeight` tuning, any mode-gated break constraint restoration, and D-04's exact-equality net-hours filter — all unchanged.
- Remaining 15-09..15-13 gap-closure plans (per `55c9ae7`) still need to run to fully close G-15-10; this plan (15-09) covers the D2 half only.

---
*Phase: 15-shift-envelope-breaks-library-generation*
*Completed: 2026-08-27*

## Self-Check: PASSED

All 10 tracked files verified present on disk (`[ -f ]`); all 5 commit hashes
(`b1a905b`, `f893e97`, `2e97c19`, `2aa3ddf`, `c4ed87d`) verified present via
`git log --oneline --all`.
