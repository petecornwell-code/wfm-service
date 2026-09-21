---
phase: 15-shift-envelope-breaks-library-generation
plan: 04
subsystem: testing
tags: [timefold, solver-config, construction-heuristic, ground-truth-test, junit5]

# Dependency graph
requires:
  - phase: 15-03
    provides: AgentShiftAssignment, ShiftBandPair, shiftEnvelopeCompliance, the two-phase solverConfig.xml, TestConstructionHeuristicPhases
provides:
  - ShiftModeFixtures — reusable, deterministic shift-mode Schedule builder (agents, banded shift templates, two-specialization demand split at the break boundary), a SLOT-mode sibling, and parameters for agent/day/template/band counts
  - ShiftEnvelopeGroundTruthTest — solves a real shift-mode fixture through solverConfig.xml and proves an independent, production-code-free walker agrees with the reported score on both a clean solve and six deliberately-corrupted ones
affects: [15-06-break-clustering-constraint, 15-07-shift-library-generation-frontend, 15-08-ch-ordering-benchmark]

actuals:
  tokens: 8900
  tasks: 2
  commits: 2

tech-stack:
  added: []
  patterns:
    - "Deterministic sequential UUIDs (new UUID(0L, n)) for every fixture-builder entity id, not UUID.randomUUID() — AgentAssignmentDifficultyComparator breaks difficulty ties on entity id, so random ids randomised the construction heuristic's seat placement order across runs and made an otherwise-identical fixture converge to 0hard on some runs and not others under a fixed step budget"
    - "StepCountTermination attached to the trailing phase in a SolverConfig loaded from the real solverConfig.xml, not the solver as a whole — Timefold 1.16.0 throws UnsupportedOperationException on a solver-level step-count termination (phase-scoped only)"
    - "Ground-truth walker as a private static method computing envelope membership from raw LocalTime arithmetic only, sharing no code path with ShiftBandPair.covers, ScheduleConstraintProvider, or any score director"

key-files:
  created:
    - src/test/java/com/wfm/solver/ShiftModeFixtures.java
    - src/test/java/com/wfm/solver/ShiftEnvelopeGroundTruthTest.java
  modified: []

key-decisions:
  - "All shift templates in the default fixture share one envelope window (08:00-17:00) and one break offset — deliberate simplification that keeps demand construction exact (every working agent breaks at the same time) while still exercising genuine template-choice diversity in the value range; ShiftModeFixtures documents that bandsPerTemplate > 1 needs a caller-supplied demand shape, since staggered breaks make per-slot demand non-uniform"
  - "Two specializations split at the break boundary (before-break / after-break) are baked into the default fixture rather than left optional, so ENVL-03's 'at least one agent holds two specializations within a shift' truth is exercised by construction on every solve, not by a special-cased fixture variant"
  - "AGENT_COUNT=2 (not 3) — reduces the exact break-geometry search space Timefold's default change/swap local search has to explore under a bounded, reproducible step count, while remaining a genuine multi-agent construction-heuristic exercise"

patterns-established:
  - "Any future fixture builder feeding solverConfig.xml's construction heuristic through a difficulty-sorted entity selector must use deterministic, non-random entity ids — random ids defeat run-to-run reproducibility the moment two entities tie on the sorted comparator's primary keys"

requirements-completed: [ENVL-02, ENVL-03, ENVL-06, ENVL-07]

coverage:
  - id: D1
    description: "A shift-mode fixture solves to a placed, feasible (0 hard) solution through the shipped solverConfig.xml via construction heuristic + local search, with no pre-assignment pipeline — every AgentShiftAssignment and AgentAssignment is placed by the solver"
    requirement: "ENVL-06"
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/solver/ShiftEnvelopeGroundTruthTest.java#shiftModeFixture_solvesFeasibly_walkerAgrees"
        status: pass
    human_judgment: false
  - id: D2
    description: "An independent, score-director-free walker (P-17: shares no code with ShiftBandPair.covers, ScheduleConstraintProvider, or SolutionManager) agrees with the reported hard score on a clean solve, and confirms no seat sits outside its agent's envelope"
    requirement: "ENVL-07"
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/solver/ShiftEnvelopeGroundTruthTest.java#shiftModeFixture_solvesFeasibly_walkerAgrees"
        status: pass
    human_judgment: false
  - id: D3
    description: "The walker flags a seat exactly one timeslot outside the envelope at both the leading and trailing edge (half-open boundary pinned in both directions), inside a break band, on a null chosen shift (every seat that day, not just one), and agrees via a fresh score-director score that a corrupted schedule is hard-infeasible too"
    requirement: "ENVL-07"
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/solver/ShiftEnvelopeGroundTruthTest.java#leadingEdge_oneIncrementBeforeEnvelopeStart_flaggedOnce"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/solver/ShiftEnvelopeGroundTruthTest.java#trailingEdge_startingAtEnvelopeEnd_flagged_endingAtEnvelopeEndLegal"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/solver/ShiftEnvelopeGroundTruthTest.java#insideBreak_atBreakStartFlagged_atBreakEndLegal"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/solver/ShiftEnvelopeGroundTruthTest.java#nullShift_flagsEverySeatThatAgentHoldsThatDay"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/solver/ShiftEnvelopeGroundTruthTest.java#scoreAgreesOnBrokenSolution"
        status: pass
    human_judgment: false
  - id: D4
    description: "Specialization varies freely within a shift envelope — the envelope walker never flags an agent holding seats requiring two different specializations across a single shift"
    requirement: "ENVL-03"
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/solver/ShiftEnvelopeGroundTruthTest.java#specializationVariesWithinShift_notFlaggedByThisWalker"
        status: pass
    human_judgment: false
  - id: D5
    description: "ShiftEnvelopeComplianceConstraintTest's own boundary/null-pair coverage (Option A hard constraint, prior plan) confirmed independently by this plan's out-of-band walker, closing the loop opened by SPIKE-COUPLING.md's Option C finding"
    requirement: "ENVL-02"
    verification:
      - kind: integration
        ref: "./gradlew test --tests com.wfm.solver.* (full solver package, 0 failures including this file)"
        status: pass
    human_judgment: false

duration: 35min
completed: 2026-08-27
status: complete
---

# Phase 15 Plan 4: Shift Envelope, Breaks & Library Generation — Ground-Truth Feasibility Proof Summary

**A reusable, deterministic shift-mode fixture builder and an independent envelope-membership walker that agrees with `solverConfig.xml`'s reported score on a clean solve and disagrees, correctly, on six deliberately corrupted ones — the exact check `SPIKE-COUPLING.md`'s Option C would have failed.**

## Performance

- **Duration:** ~35 min
- **Tasks:** 2
- **Files modified:** 2 (both created)

## Accomplishments

- `ShiftModeFixtures` — a parametrized (agent/day/template/band count), deterministic shift-mode `Schedule` builder: agents contracted to matching hours, a banded shift library, demand split across two specializations at the break boundary, every `AgentAssignment` and `AgentShiftAssignment` left unassigned for the solver to place. A SLOT-mode sibling (zero shift rows/pairs) reuses the same code path.
- `ShiftEnvelopeGroundTruthTest` Task 1: solves the fixture through the real `solverConfig.xml` (step-count terminated, never wall-clock, per P-18), asserts non-vacuous placement, asserts `0hard`, and asserts a from-scratch walker independently agrees.
- `ShiftEnvelopeGroundTruthTest` Task 2: six disagreement-proof cases constructing violations by direct mutation of the solved solution — leading edge, trailing edge (with the opposite boundary proven still legal), inside vs. at the edge of a break, a null chosen shift flagging every seat that day, score-director agreement on the corrupted solution, and ENVL-03's specialization-freedom guarantee.
- Full `com.wfm.solver.*` package green (0 failures) after both commits, and the full project test suite (73 test classes) green in a separate full-suite run.

## Task Commits

1. **Task 1: Solve a real shift-mode fixture and walk it** - `ab21542` (feat)
2. **Task 2: The disagreement proof — the walker must be able to fail** - `95d801e` (feat)

## Files Created/Modified

- `src/test/java/com/wfm/solver/ShiftModeFixtures.java` - deterministic shift-mode/slot-mode `Schedule` fixture builder, reusable by plans 15-06 and 15-08 (D-09/P-19)
- `src/test/java/com/wfm/solver/ShiftEnvelopeGroundTruthTest.java` - the ENVL-07 ground-truth walker and its 7 tests (1 feasibility/agreement test + 6 disagreement-proof cases)

## Decisions Made

- All templates in the default fixture share one envelope and one break offset (functionally identical but distinct entities) so demand construction stays exact — every working agent breaks in the same window, so per-slot demand (`agentCount`) never needs to account for staggered break overlap. `ShiftModeFixtures`'s javadoc documents this as a documented limitation: `bandsPerTemplate > 1` (needed for plan 15-06's staggered-break clustering demonstration) requires the caller to build its own demand shape.
- Two specializations split at the break boundary are baked into the default fixture (not a separate variant), so ENVL-03's "at least one agent holds two specializations within a shift" truth is exercised by construction on every run rather than by a special-cased fixture.
- `AGENT_COUNT = 2` (plan permitted "a small agent roster" without pinning a count) — keeps the exact single-contiguous-break-gap search space small enough to converge reliably within a bounded, reproducible step count.
- `StepCountTermination` is attached to the trailing local-search phase of a `SolverConfig` loaded via `SolverConfig.createFromXmlResource("solverConfig.xml")`, not the solver as a whole — see Deviations.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Solver-level `StepCountTermination` throws `UnsupportedOperationException` in Timefold 1.16.0**
- **Found during:** Task 1, first test run
- **Issue:** The plan's literal instruction ("configure a step-count termination") was implemented as `SolverConfig.withTerminationConfig(new TerminationConfig().withStepCountLimit(N))` at the solver level, following the pattern of existing `withSpentLimit` usage elsewhere in the test suite. This threw `java.lang.UnsupportedOperationException: StepCountTermination can only be used for phase termination.` at `AbstractSolver.runPhases` — `StepCountTermination` (unlike time-based terminations) is phase-scoped only in this Timefold version.
- **Fix:** Attached the `TerminationConfig` to the last configured phase (`solverConfig.getPhaseConfigList()`, the trailing `LocalSearchPhaseConfig` from the real XML) instead of the solver as a whole. Both construction-heuristic phases self-terminate once every entity is placed and remain unbounded; only local search is step-bounded.
- **Files modified:** `src/test/java/com/wfm/solver/ShiftEnvelopeGroundTruthTest.java`
- **Verification:** `./gradlew test --tests "com.wfm.solver.ShiftEnvelopeGroundTruthTest"` green after the fix.
- **Committed in:** `ab21542` (Task 1 commit)

**2. [Rule 1 - Bug] Random fixture-entity ids made a fixed step-count budget flaky across runs**
- **Found during:** Task 1, after fixing deviation #1 — some runs reached `0hard` within the step budget, others (same parameters, fresh random UUIDs) stalled at scores like `-1710`/`-1200`/`-3425`
- **Issue:** `AgentAssignmentDifficultyComparator` (production code, unchanged) breaks difficulty ties — same date, same timeslot start, exactly the shape of every one of this fixture's same-slot seats — by comparing entity ids. `ShiftModeFixtures` originally used `UUID.randomUUID()` for every entity, so the sorted construction-heuristic entity selector's tie-break order (and therefore the initial solution and local-search trajectory) varied non-deterministically run to run, occasionally failing to converge within the step budget. This also directly violates the plan's own `must_haves` instruction: "Keep the builder deterministic — no random ids that vary per run beyond what the test seeds explicitly."
- **Fix:** Replaced every `UUID.randomUUID()` call in `ShiftModeFixtures` with a per-invocation sequential `UUID` (`new UUID(0L, n)` from a local counter), making two calls with the same parameters produce byte-identical entity graphs. Verified stable convergence to `0hard/-32soft` across 3 repeated full runs before reducing the step budget to its final value.
- **Files modified:** `src/test/java/com/wfm/solver/ShiftModeFixtures.java`
- **Verification:** `./gradlew test --tests "com.wfm.solver.ShiftEnvelopeGroundTruthTest"` green on 3 consecutive `--rerun` invocations; full `com.wfm.solver.*` package (0 failures) and full project suite (73 test classes, 0 failures) both green in separate runs.
- **Committed in:** `ab21542` (Task 1 commit) — the deterministic-id fix landed before Task 1's commit, since it was required for Task 1's own done-criteria (reliable `0hard`) to hold at all.

---

**Total deviations:** 2 auto-fixed (both Rule 1 — genuine bugs in the test's own solving setup, not production code changes)
**Impact on plan:** Both fixes were necessary for the plan's own stated done-criteria (a shift-mode fixture reliably reaches `0hard` through CH + local search, reproducibly in CI) and directly implement the plan's own explicit `must_haves` instruction on determinism. No production code was touched; no scope creep.

## Issues Encountered

None beyond the deviations above.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- `ShiftModeFixtures.buildShiftModeSchedule`/`buildSlotModeSchedule` are ready for plan 15-06 (break clustering — note the documented `bandsPerTemplate > 1` demand-shape caveat) and plan 15-08 (CH-ordering benchmark, which can vary `agentCount`/`dayCount`/`templateCount` directly).
- `ShiftEnvelopeGroundTruthTest`'s walker pattern (private static, raw-`LocalTime`-only, zero production-helper calls) is available as the template for any future independent-verification test this milestone needs.
- The pre-existing flaky `BreakAwareConstructionTest` (documented in `deferred-items.md` as JVM-state-sensitive, unrelated to this plan) passed cleanly in every full-suite run executed during this plan.

## Self-Check: PASSED

- All created files verified present on disk: `ShiftModeFixtures.java`, `ShiftEnvelopeGroundTruthTest.java`.
- Both task commits verified present in `git log`: `ab21542`, `95d801e`.
- Plan `<verification>` re-run: `./gradlew test --tests "com.wfm.solver.ShiftEnvelopeGroundTruthTest"` green (7/7 tests); the walker's code path (`findEnvelopeViolations`) contains no call into `ShiftBandPair`, `ScheduleConstraintProvider`, `SolutionManager`, or a score director (confirmed by inspection and by grep); every one of Task 2's six negative cases asserts a violation is produced before Task 1's clean-pass claim is trusted.
- Full `./gradlew test --tests "com.wfm.solver.*"` (solver package) and a full-suite `./gradlew test` (73 test classes) both green, 0 failures.

---
*Phase: 15-shift-envelope-breaks-library-generation*
*Completed: 2026-08-27*
