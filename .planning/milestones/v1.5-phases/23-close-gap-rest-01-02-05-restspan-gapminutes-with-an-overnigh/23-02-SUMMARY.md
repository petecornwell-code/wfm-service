---
phase: 23-close-gap-rest-01-02-05-restspan-gapminutes-with-an-overnigh
plan: 02
subsystem: scheduling-solver
tags: [timefold, rest-constraint, day-window, interval-arithmetic, junit5, test-coverage]

# Dependency graph
requires:
  - phase: 23-01
    provides: DayWindow.anchoredWrappedEndMinute, the corrected RestSpan.gapMinutes, and requireRestFeasibility's corrected SLOT pre-horizon branch
provides:
  - Overnight-predecessor fixture coverage in the four remaining rest-gap classes (RestHorizonEdgeTest, MinimumRestSlotConstraintTest, RestPredecessorServiceTest, RestWaiverDisclosureTest)
  - The sixth affected class (RestHorizonEdgeTest) identified by planner finding PF-01, not named in 23-RESEARCH.md Finding 5
  - A real regression guarantee (not a manufactured one) for the crossing-midnight-but-not-the-anchor SLOT case, per PF-02
affects: [23-03 (structural guard RestGapArithmeticGuardTest targets exactly the call sites 23-01 fixed plus the primitive this coverage exercises)]

actuals:
  tokens: 3817
  tasks: 3
  commits: 3
  plan_head_before: c70ab37
  plan_head_after: 23ebc294f17d28c08cfc743dd9ec5ac0fa9ebe27

tech-stack:
  added: []
  patterns:
    - "Every new positive case names a number that was wrong before plan 23-01 landed (600, 600, 600, 60, 60), making each a red-before/green-after proof rather than a tautology"
    - "Wrapping predecessors are always directly-constructed pre-horizon RestSpan facts, never a wrapping slot set -- PF-02 established RestSpan.ofSlots cannot itself emit one from grid-aligned data"

key-files:
  created: []
  modified:
    - src/test/java/com/wfm/solver/RestHorizonEdgeTest.java
    - src/test/java/com/wfm/solver/MinimumRestSlotConstraintTest.java
    - src/test/java/com/wfm/service/RestPredecessorServiceTest.java
    - src/test/java/com/wfm/service/RestWaiverDisclosureTest.java

key-decisions:
  - "PF-01 followed verbatim: RestHorizonEdgeTest counted as the phase's sixth affected class (not the five 23-RESEARCH.md Finding 5 named), given its own pair of pre-horizon overnight cases (SHIFT and SLOT) plus the no-successor-row paired negative"
  - "PF-02 followed verbatim: the SLOT-mode wrapping predecessor in both MinimumRestSlotConstraintTest and RestPredecessorServiceTest is a directly-constructed pre-horizon RestSpan, never a synthesized wrapping slot set -- compliantDaySeats and RestSpan.ofSlots were left untouched"
  - "The RestPredecessorServiceTest SLOT case asserts the crossing-midnight-but-not-the-anchor shape is numerically UNCHANGED at 1740, re-derived against the live DayWindow implementation during execution rather than trusted from the plan text -- it matched exactly, so no discrepancy needed recording"

requirements-completed: [REST-02, REST-05, OVNT-01, OVNT-03]

coverage:
  - id: D1
    description: "RestHorizonEdgeTest (the sixth affected class, PF-01) proves the pre-horizon lookback against an overnight predecessor in both SHIFT and SLOT mode at 600 points, plus the no-successor-row paired negative at 0 in both modes"
    requirement: "REST-05"
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/solver/RestHorizonEdgeTest.java#shift_preHorizonOvernightPredecessor_firstDayConstrained_oneMatch"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/solver/RestHorizonEdgeTest.java#slot_preHorizonOvernightPredecessor_firstDayConstrained_oneMatch"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/solver/RestHorizonEdgeTest.java#overnightPriorSpanWithNoSuccessorRowOnD_zeroMatches"
        status: pass
    human_judgment: false
  - id: D2
    description: "MinimumRestSlotConstraintTest proves the SLOT-mode constraint against an overnight pre-horizon predecessor paired with real in-horizon seats, at 600 points, plus the exact-equality boundary at 0"
    requirement: "REST-02"
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/solver/MinimumRestSlotConstraintTest.java#overnightPreHorizonPredecessor_trueGapMeasuredCorrectly"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/solver/MinimumRestSlotConstraintTest.java#overnightPreHorizonPredecessor_gapExactlyAtMinimum_notPenalised"
        status: pass
    human_judgment: false
  - id: D3
    description: "RestPredecessorServiceTest proves the lookback service resolves an overnight accepted SHIFT row into a usable span measuring 60 minutes, and pins the crossing-midnight-but-not-the-anchor SLOT case at 1740 as explicitly unchanged (PF-02 regression safety)"
    requirement: "REST-05"
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/RestPredecessorServiceTest.java#shiftModeOvernightRow_spanCarriesTheWrappedEnd_gapMeasuredCorrectly"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/RestPredecessorServiceTest.java#slotModeSpanCrossingMidnightButNotTheAnchor_unchangedByTheFix"
        status: pass
    human_judgment: false
  - id: D4
    description: "RestWaiverDisclosureTest proves the operator-facing waiver-disclosure report is corrected for free: an overnight pre-horizon span now reports under applied with measuredGapMinutes 60, not unused as it did before the fix"
    requirement: "REST-02"
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/RestWaiverDisclosureTest.java#waiverOnFirstBusinessDateWithOvernightAcceptedPredecessor_reportsTheTrueGap"
        status: pass
    human_judgment: false
  - id: D5
    description: "OVNT-01 and OVNT-03 are covered as regression safety only -- no Phase 21 overnight-shift-template behaviour or new feature work was re-implemented; the no-successor-row negative is the OVNT-03-shaped guarantee"
    requirement: "OVNT-01"
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/solver/RestHorizonEdgeTest.java#overnightPriorSpanWithNoSuccessorRowOnD_zeroMatches"
        status: pass
    human_judgment: false

duration: 18min
completed: 2026-10-04
status: complete
---

# Phase 23 Plan 2: Close the coverage gap across the remaining four rest-gap test classes Summary

**Proved 23-01's overnight-predecessor rest-gap fix through every remaining route a rest gap can be measured — the pre-horizon lookback in both constraint modes, the SLOT-mode constraint against real in-horizon seats, the lookback resolution service itself, and the operator-facing waiver-disclosure report — closing the phase's coverage deliverable across all six affected test classes (five from 23-RESEARCH.md plus the sixth PF-01 found during planning).**

## Performance

- **Duration:** ~18 min
- **Started:** 2026-10-04T20:11:25Z (session start)
- **Completed:** 2026-10-04T20:29:00Z
- **Tasks:** 3
- **Files modified:** 4

## Accomplishments
- `RestHorizonEdgeTest` (the sixth affected class, PF-01) now proves the pre-horizon lookback against an overnight 22:00-06:00 predecessor in both SHIFT and SLOT mode (600-point shortfall against a 660-minute minimum), plus the day-off/PTO-shaped no-successor-row paired negative in both modes
- `MinimumRestSlotConstraintTest` proves the SLOT-mode constraint measures a directly-constructed overnight pre-horizon predecessor against real `compliantDaySeats` correctly (600-point shortfall), with the exact-equality boundary (60-minute minimum) asserting zero
- `RestPredecessorServiceTest` proves the lookback service resolves an overnight accepted SHIFT row (22:00-06:00) into a span that measures a true 60-minute gap, and pins the crossing-midnight-but-not-the-anchor SLOT case at 1740 as explicitly unchanged by the fix (PF-02)
- `RestWaiverDisclosureTest` proves the waiver-disclosure report is corrected for free — an overnight pre-horizon span with a waiver now reports `applied` with `measuredGapMinutes()` 60, where before the fix it measured 1500 and was reported as `unused` (an operator-facing transparency fix)
- Every new assertion names a number that was wrong before plan 23-01 landed (600, 600, 600, 60, 60), so each case is a red-before/green-after proof, not a tautology
- No production source was modified — this plan is test-only, confirmed by `git diff --exit-code -- src/main/java` after every task

## Task Commits

Each task was committed atomically:

1. **Task 1: Pre-horizon overnight predecessor through both constraint streams (PF-01's sixth class)** - `12833f6` (test)
2. **Task 2: SLOT-mode constraint against real in-horizon seats, with the exact-equality boundary** - `33ca87c` (test)
3. **Task 3: Lookback resolution and the waiver-disclosure report path** - `23ebc29` (test)

**Plan metadata:** (pending — docs commit below)

## Files Created/Modified
- `src/test/java/com/wfm/solver/RestHorizonEdgeTest.java` - 3 new tests: the SHIFT and SLOT pre-horizon overnight-predecessor proofs (600 each) and the no-successor-row paired negative (0 in both modes)
- `src/test/java/com/wfm/solver/MinimumRestSlotConstraintTest.java` - 2 new tests: the SLOT-mode overnight pre-horizon proof against real in-horizon seats (600) and the exact-equality boundary (0)
- `src/test/java/com/wfm/service/RestPredecessorServiceTest.java` - 2 new tests: the SHIFT-mode overnight row resolution + gap proof (60) and the crossing-midnight-but-not-the-anchor SLOT regression pin (1740)
- `src/test/java/com/wfm/service/RestWaiverDisclosureTest.java` - 1 new test: the overnight accepted predecessor waiver-disclosure regression case (60, reported as applied)

## Decisions Made
- Adopted PF-01 verbatim: `RestHorizonEdgeTest` counted as this plan's sixth affected class, with its own matched positive/negative pair mirrored from the file's existing non-wrapping idiom
- Adopted PF-02 verbatim: every SLOT-mode wrapping predecessor in this plan is a directly-constructed pre-horizon `RestSpan`, never a synthesized wrapping slot set — `compliantDaySeats`, `RestSpan.ofSlots`, and every other shared fixture builder in both files were left untouched (confirmed by `git diff` showing no builder-definition changes)
- Re-derived the 21:00-anchor numbers in `RestPredecessorServiceTest`'s SLOT regression case against the live `DayWindow` implementation during execution (per the plan's own instruction) rather than trusting the plan text blindly — the derivation (start offset 120, end offset 420, gap 1740) matched exactly, so no discrepancy needed recording

## Deviations from Plan

None — plan executed exactly as written. Every acceptance criterion and `<verify>` command passed on the first attempt; no fix-attempt budget was consumed.

**Total deviations:** 0 auto-fixed.
**Impact on plan:** None.

## Issues Encountered
None.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Plan 23-03 can proceed: all six affected classes (`MinimumRestShiftConstraintTest` and `RestFeasibilityRefusalTest` from 23-01; `RestHorizonEdgeTest`, `MinimumRestSlotConstraintTest`, `RestPredecessorServiceTest` and `RestWaiverDisclosureTest` from this plan) now carry an overnight-predecessor fixture, exactly as `23-RESEARCH.md` Finding 5 plus PF-01 require. 23-03's structural guard (`RestGapArithmeticGuardTest`) targets the two call sites 23-01 fixed plus the `DayWindow.anchoredWrappedEndMinute` primitive this plan's fixtures exercise.
- No full-suite run was attempted in this plan, by design — a filtered `--tests` run deletes every other class's JUnit XML, so no suite-wide aggregate may be read after any command this plan ran. Plan 23-03 owns the single `./gradlew --stop` + `./gradlew test` full-suite gate.
- No blockers. Each of the four modified test files was run individually via `./gradlew test --tests` and reported 0 failures/0 errors (12, 19, 12, 31 tests respectively, including every pre-existing figure intact).

## Self-Check: PASSED

All 4 modified files confirmed present on disk with the expected new test method names (verified via `grep -c`). All 3 task commits (`12833f6`, `33ca87c`, `23ebc29`) confirmed in `git log --oneline`. All plan-level `<acceptance_criteria>` and `<verify>` commands re-run fresh and passing: `RestHorizonEdgeTest` 12/12, `MinimumRestSlotConstraintTest` 19/19, `RestPredecessorServiceTest` 12/12, `RestWaiverDisclosureTest` 31/31 — 0 failures, 0 errors across all four. `git diff --exit-code -- src/main/java` silent after every task, confirmed this plan modified no production source.

---
*Phase: 23-close-gap-rest-01-02-05-restspan-gapminutes-with-an-overnigh*
*Completed: 2026-10-04*
