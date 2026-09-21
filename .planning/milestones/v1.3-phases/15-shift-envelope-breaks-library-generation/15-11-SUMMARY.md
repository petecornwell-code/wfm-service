---
phase: 15-shift-envelope-breaks-library-generation
plan: 11
subsystem: solver
tags: [timefold, shift-envelope, pre-solve-validation, gap-closure]

# Dependency graph
requires:
  - phase: 15-shift-envelope-breaks-library-generation
    provides: envelope-aware SolverService.expandMinimumStaffingSeats (plan 15-09) — the gate counts the seats that method now produces, so the two can never disagree
provides:
  - AgentDayConfig.expectedWorkSlots() — the single expected-work-slot computation, shared by ScheduleConstraintProvider's hard contracted-hours constraints and the new gate
  - SolverService.requireShiftEnvelopeSeatSupply — the shift-mode seat-supply gate, invoked after step 10d (seats exist) and before schedule population
  - SolverSeatSupplyGateAccess — test-only cross-package bridge exposing the gate to com.wfm.solver fixtures
  - ShiftEnvelopeSupplyInvariantTest — the zero-slack invariant promoted from a diagnose-only characterising test into a permanent regression suite
affects: [15-12, 15-13, 16-usual-shift-storage, 17-consistency-constraint-drift-reporting]

# Actuals (#2632)
actuals:
  tokens: 19724
  tasks: 3
  commits: 4

tech-stack:
  added: []
  patterns:
    - "Test-only cross-package bridge (SolverSeatSupplyGateAccess) exposing a package-private production gate to a fixture/test in another package, mirroring plan 15-09's SolverSeatExpansionAccess precedent exactly — keeps main-source visibility unchanged so a future gate regression surfaces wherever the bridge is exercised, not in a fixture-local reimplementation"
    - "A pre-solve precondition gate invoked mid-method (SolverService step 10e, after seats exist) rather than inside the traditional runPreSolveValidation checkpoint (step 7, before seats exist) — establishes that not every precondition belongs at the same checkpoint; a precondition that needs the solver's actual input data must run after that data is constructed"

key-files:
  created:
    - src/test/java/com/wfm/service/ShiftEnvelopeSupplyGateTest.java
    - src/test/java/com/wfm/service/SolverSeatSupplyGateAccess.java
    - src/test/java/com/wfm/solver/ShiftEnvelopeSupplyInvariantTest.java
  modified:
    - src/main/java/com/wfm/model/AgentDayConfig.java
    - src/main/java/com/wfm/solver/ScheduleConstraintProvider.java
    - src/main/java/com/wfm/service/SolverService.java

key-decisions:
  - "The zero-slack equality (D-04's exact net-hours value-range filter) stays unrelaxed; the fix is a checked precondition on seat supply, not a tolerance on the value range — three reasons (shift definition, arithmetic doesn't close, weakens the null-pair guard) recorded in ShiftEnvelopeSupplyInvariantTest's class javadoc, not only in planning documents"
  - "The gate's demand/supply check deliberately uses coverage by ANY live pair, not the agent's own eventual pair — the shift planning variable is still free when the gate runs (before solving), so the looser test is the only sound necessary condition and it errs toward permitting a solvable desk"
  - "A wholly retired/upcoming library on a date emits ONE refusal detail for that date, not one per rostered agent — distinguishes a library-configuration problem from a per-agent hours mismatch, per the plan's explicit 'rather than an hours mismatch for every agent' instruction"
  - "ShiftEnvelopeSupplyInvariantTest's fixtures route filler-seat construction through the real production SolverSeatExpansionAccess bridge (from plan 15-09) rather than reimplementing the pre-15-09 naive top-up the original characterising test used — necessary for CASE 1 to actually exercise plan 15-09's envelope-aware fix rather than silently testing stale behavior"
  - "shiftEnvelopeComplianceWeight stays at ofHard(1), tied with four other constraints (specMatch, bulkOverallocationLimit, bulkUnderallocationHard, bandCapacity) — deliberately not raised, since once supply is a checked precondition there is no surplus agent-slot left to arbitrage; the empirical refutation (-4 envelope to -400 contracted hours on a deficit desk) is recorded in the weight-ladder test"

requirements-completed: [ENVL-01, ENVL-02, ENVL-06, XCUT-03]

coverage:
  - id: D1
    description: "In-envelope seat supply is a checked precondition of every shift-mode solve: contracted demand vs. library-covered seat supply, per date"
    requirement: "ENVL-02"
    verification:
      - kind: unit
        ref: "com.wfm.service.ShiftEnvelopeSupplyGateTest#refusesOnShortfall"
        status: pass
      - kind: unit
        ref: "com.wfm.service.ShiftEnvelopeSupplyGateTest#noFalseRefusalWhenSupplyMeetsDemand"
        status: pass
      - kind: unit
        ref: "com.wfm.service.ShiftEnvelopeSupplyGateTest#slotDeskNeverEvaluated"
        status: pass
    human_judgment: false
  - id: D2
    description: "The shortfall refusal message names the date, the shortfall in slots and hours, and every lever: the desk's current over-allocation limit, correcting the forecast, reducing rostered hours, and changing the library"
    requirement: "ENVL-02"
    verification:
      - kind: unit
        ref: "com.wfm.service.ShiftEnvelopeSupplyGateTest#messageNamesTheLevers"
        status: pass
    human_judgment: false
  - id: D3
    description: "An agent-day whose effective hours match no live pair is refused by name; a wholly retired/upcoming library reads as one message distinguishing itself from a numeric shortfall, not a per-agent hours-mismatch repeat"
    requirement: "ENVL-01"
    verification:
      - kind: unit
        ref: "com.wfm.service.ShiftEnvelopeSupplyGateTest#refusesAgentWhoseHoursMatchNoLivePair"
        status: pass
      - kind: unit
        ref: "com.wfm.service.ShiftEnvelopeSupplyGateTest#refusesWhollyRetiredLibraryDistinctFromShortfall"
        status: pass
    human_judgment: false
  - id: D4
    description: "A non-blocking advisory records the tightest covered timeslot per rostered date and never blocks the solve"
    verification:
      - kind: unit
        ref: "com.wfm.service.ShiftEnvelopeSupplyGateTest#advisoryOnThinTimeslotDoesNotBlock"
        status: pass
    human_judgment: false
  - id: D5
    description: "One implementation of expected-work-slots (AgentDayConfig.expectedWorkSlots()) shared by ScheduleConstraintProvider's hard contracted-hours constraints and the new gate — score-neutral"
    requirement: "XCUT-03"
    verification:
      - kind: other
        ref: "./gradlew test (full suite, 539 tests, 0 failures, 0 errors)"
        status: pass
    human_judgment: false
  - id: D6
    description: "The zero-slack identity (a pair's legal slots exactly equal an agent-day's expected work slots) is preserved deliberately and asserted structurally, not incidentally"
    requirement: "ENVL-06"
    verification:
      - kind: unit
        ref: "com.wfm.solver.ShiftEnvelopeSupplyInvariantTest#valueRangePairCoversExactlyExpectedWorkSlots_zeroSlack"
        status: pass
      - kind: unit
        ref: "com.wfm.solver.ShiftEnvelopeSupplyInvariantTest#zeroSlackIsStructural_notCoincidental"
        status: pass
    human_judgment: false
  - id: D7
    description: "All three 'hard score cannot reach zero' characterisations converted: zero-demand-inside-envelope now solves feasibly at 0hard; hours-mismatch-no-template now refuses by name; envelope-overruns-operating-window now refuses"
    verification:
      - kind: unit
        ref: "com.wfm.solver.ShiftEnvelopeSupplyInvariantTest#zeroDemandSlotsInsideEnvelope_nowSolvesToZeroHard"
        status: pass
      - kind: unit
        ref: "com.wfm.solver.ShiftEnvelopeSupplyInvariantTest#contractedHoursMatchingNoTemplate_nowRefusedByGate"
        status: pass
      - kind: unit
        ref: "com.wfm.solver.ShiftEnvelopeSupplyInvariantTest#envelopeRunningPastClosingTime_nowRefusedByGate"
        status: pass
    human_judgment: false
  - id: D8
    description: "The weight-ladder decision (shiftEnvelopeComplianceWeight deliberately unchanged at ofHard(1)) is recorded in test with its empirical refutation attached"
    verification:
      - kind: unit
        ref: "com.wfm.solver.ShiftEnvelopeSupplyInvariantTest#shiftEnvelopeComplianceTiesForTheLowestHardWeight_deliberatelyNotChanged"
        status: pass
    human_judgment: false

duration: 40min
completed: 2026-08-27
status: complete
---

# Phase 15 Plan 11: Shift-Mode Seat-Supply Gate Summary

**In-envelope seat supply is now a CHECKED PRECONDITION of every shift-mode solve — a shortfall or a hours/library mismatch refuses before any solving occurs, naming the date, the shortfall, and every operator lever, instead of degrading into an irreducible hard score mislabelled as "Shift envelope compliance".**

## Performance

- **Duration:** 40 min
- **Started:** 2026-08-27T16:56:00Z (approx.)
- **Completed:** 2026-08-27T17:36:00Z
- **Tasks:** 3
- **Files modified/created:** 6

## Accomplishments

- Closed the D1 half of gap G-15-10: moved the expected-work-slots calculation onto `AgentDayConfig.expectedWorkSlots()` as the single implementation `ScheduleConstraintProvider`'s hard contracted-hours constraints and the new gate both call — the two can never disagree, and the change is score-neutral (full suite green with unchanged expectations).
- Added `SolverService.requireShiftEnvelopeSeatSupply`, invoked immediately after step 10d (once seats exist) and before schedule population — never inside `runPreSolveValidation` (step 7), which runs before any seat is constructed. It refuses a shift-mode solve when library-covered seat supply falls short of contracted demand for any date, or when an agent-day's effective hours match no live pair, naming the date, the shortfall in slots and hours, and every operator lever (raise the over-allocation limit and quote its current value, correct the demand forecast, reduce rostered hours, or change the library). A non-blocking advisory records the tightest covered timeslot per date.
- Promoted the diagnose-only `ShiftEnvelopeUnsatisfiableHardTest` characterising class into a permanent regression suite, `ShiftEnvelopeSupplyInvariantTest`: the zero-slack identity and its universal sweep are kept as a deliberate invariant (with the three reasons a tolerance was rejected recorded in the class javadoc), and all three "hard score cannot reach zero" cases are inverted — one now solves feasibly to 0hard (plan 15-09's envelope-aware filler closes the gap exactly), and two now refuse by name before any solve is attempted.
- The weight-ladder test is corrected to state the tie at `ofHard(1)` (alongside four other constraints) as a measured fact deliberately left unchanged, with the empirical refutation (-4 envelope to -400 contracted hours) recorded as the reason.
- Full suite verified green: 539 tests, 0 failures, 0 errors.

## Task Commits

1. **Task 1: One implementation of expected work slots** - `e77674b` (refactor)
2. **Task 2 (RED): failing tests for the shift-mode seat-supply gate** - `4bf28a9` (test)
3. **Task 2 (GREEN): the shift-mode seat-supply gate** - `4492355` (feat)
4. **Task 3: promote the unsatisfiable-hard characterisation into a supply invariant** - `528d9de` (test)

**Plan metadata:** (this commit, docs: complete plan)

_Task 2 is `type="auto" tdd="true"` — RED then GREEN commits, per the TDD flow._

## Pre-fix RED Evidence

**Tests 1-7 (`ShiftEnvelopeSupplyGateTest`)** — observed RED as a **compile failure** against unmodified source, since every test calls `SolverService.requireShiftEnvelopeSeatSupply`, which the unmodified `SolverService` does not declare:

```
error: cannot find symbol
  symbol:   method requireShiftEnvelopeSeatSupply(SchedulingMode,List<AgentShiftAssignment>,List<ShiftBandPair>,List<Timeslot>,List<AgentAssignment>,int,List<String>)
  location: class SolverService
7 errors
```

This is a structural RED (the method literally does not exist), covering the plan's Test 1/2/4/6 requirement in the strongest possible form — none of the seven behaviours could pass without the implementation existing at all.

## Files Created/Modified

- `src/main/java/com/wfm/model/AgentDayConfig.java` - new `expectedWorkSlots()` instance method; the single implementation the gate and the hard contracted-hours constraints now share
- `src/main/java/com/wfm/solver/ScheduleConstraintProvider.java` - private `expectedWorkSlots(AgentDayConfig)` helper removed (not delegated-to); all 5 call sites now call `dayConfig.expectedWorkSlots()` directly; unused `RoundingMode` import removed
- `src/main/java/com/wfm/service/SolverService.java` - new `requireShiftEnvelopeSeatSupply` (the gate) and `slotsToHours` helper; new step 10e call site in `startSolve`, between step 10d's minimum-staffing top-up and schedule population
- `src/test/java/com/wfm/service/ShiftEnvelopeSupplyGateTest.java` (new) - seven behaviours: shortfall refusal, lever-naming, no-false-refusal, hours-mismatch refusal, slot-mode no-op, wholly-retired-library refusal, non-blocking advisory
- `src/test/java/com/wfm/service/SolverSeatSupplyGateAccess.java` (new) - test-only public bridge in `com.wfm.service`, forwarding to the package-private gate, so `com.wfm.solver` fixtures can call production's real gate
- `src/test/java/com/wfm/solver/ShiftEnvelopeSupplyInvariantTest.java` (new) - the promoted invariant suite: two LEMMA tests (kept, reframed), three inverted "cannot reach zero" cases, and the corrected weight-ladder test

## Decisions Made

See `key-decisions` in frontmatter — five decisions: the zero-slack equality stays unrelaxed (three reasons recorded in test javadoc), the demand/supply check uses coverage by any live pair (not the agent's eventual pair), a retired library emits one message per date not per agent, the invariant test's fixtures route filler seats through the real production expansion (not a stale reimplementation), and `shiftEnvelopeComplianceWeight` stays unchanged with its empirical refutation recorded.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Added `SolverSeatSupplyGateAccess.java`, a test-only cross-package bridge**
- **Found during:** Task 3
- **Issue:** `requireShiftEnvelopeSeatSupply` is package-private in `com.wfm.service` (matching `appendBandCapacityErrors`'s and `expandMinimumStaffingSeats`'s precedent). `ShiftEnvelopeSupplyInvariantTest` lives in `com.wfm.solver` per the plan's own file list and needs to invoke the real gate directly to assert refusal/pass behaviour against its fixtures — a different package cannot call a package-private method.
- **Fix:** Added a one-method static forwarding bridge, mirroring plan 15-09's `SolverSeatExpansionAccess` precedent exactly (same package, same shape, same javadoc pattern). Main-source visibility of the gate is unchanged.
- **Files modified:** `src/test/java/com/wfm/service/SolverSeatSupplyGateAccess.java`
- **Verification:** Compiles; all six `ShiftEnvelopeSupplyInvariantTest` tests pass using the bridge.
- **Committed in:** `528d9de` (Task 3 commit)

---

**Total deviations:** 1 auto-fixed (Rule 3 — a mechanical, already-precedented consequence of package-private visibility, not a scope change)
**Impact on plan:** No new production behaviour; the bridge exposes no new logic, only forwards to the gate this plan already built. No scope creep.

## Issues Encountered

None. Both full-suite runs (`./gradlew test`, once after Task 2 and once after Task 3) completed clean on the first attempt; no node-repair cycles were needed on any task.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- G-15-10's D1 half (shift-envelope seat-supply gate) is closed: a shift-mode solve now refuses before starting when library-covered supply cannot meet contracted demand, or when an agent's hours match no live pair — the operator gets a named, actionable refusal instead of an irreducible hard score parked on `Shift envelope compliance`.
- Combined with plan 15-09 (D2 half, envelope-aware minimum-staffing seats), G-15-10 is now closed on both halves the two plans were scoped to cover.
- Explicitly NOT addressed by this plan (per its own scope boundary): raising `shiftEnvelopeComplianceWeight` (the tie is recorded, not changed), any change to the D-04 value-range filter, and save-time envelope-containment validation in `ShiftTemplateService` — deferred alongside D5, recorded as plan 15-13's scope.
- Remaining 15-12/15-13 gap-closure plans (per `55c9ae7`) still need to run to fully close the rest of G-15-10's scope.

---
*Phase: 15-shift-envelope-breaks-library-generation*
*Completed: 2026-08-27*

## Self-Check: PASSED

All 6 tracked files verified present on disk (`[ -f ]`); all 4 commit hashes
(`e77674b`, `4bf28a9`, `4492355`, `528d9de`) verified present via
`git log --oneline --all`.
