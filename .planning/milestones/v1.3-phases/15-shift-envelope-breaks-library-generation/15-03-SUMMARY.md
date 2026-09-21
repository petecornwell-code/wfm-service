---
phase: 15-shift-envelope-breaks-library-generation
plan: 03
subsystem: solver
tags: [timefold, constraint-streams, planning-entity, flyway, spring-boot, postgres]

# Dependency graph
requires:
  - phase: 15-01
    provides: shift_template_break_band table, ShiftTemplateBreakBand entity/repository, any-band covers() predicate
provides:
  - AgentShiftAssignment — the second @PlanningEntity (one shift choice per working agent-day), entity-level @ValueRangeProvider filtered by AgentDayConfig.effectiveHours
  - ShiftBandPair — immutable (template, band) problem fact with the envelope containment predicate
  - solverConfig.xml two explicitly-scoped construction-heuristic phases (shifts-first)
  - SolverService.buildShiftBandPairs/buildShiftAssignments — mode-gated population of the desk's live pairs and one row per working agent-day
  - V41 migration — agent_shift_assignment table + constraint_weights.shift_envelope_compliance_weight
  - ScheduleConstraintProvider.shiftEnvelopeCompliance — the hard constraint coupling the two planning entities (Option A)
  - SolverConfigBuildTest (XCUT-03), ShiftEnvelopeComplianceConstraintTest, SolverServiceShiftAssignmentTest
affects: [15-04-envl07-ground-truth-benchmark, 15-05-frontend-band-editor, 15-06-break-clustering-constraint, 15-07-shift-library-generation-frontend, 15-08-ch-ordering-benchmark]

actuals:
  tokens: 20057
  tasks: 3
  commits: 3

tech-stack:
  added: []
  patterns:
    - "Entity-level @ValueRangeProvider filtered by a problem fact (P-13) — AgentShiftAssignment.getEligibleShiftBandPairs() filters the desk's pre-sorted pairs by exact BigDecimal-normalized equality against AgentDayConfig.effectiveHours, the ARCHITECTURE.md-sanctioned sound alternative to Option C's rejected filtered-planning-variable range"
    - "Two explicitly-scoped construction-heuristic phases, shifts-first — mandatory the moment a second @PlanningEntity exists; every <entitySelector> carries both an id attribute and a nested entityClass element"
    - "forEachIncludingUnassigned for a join partner whose null genuine variable must still participate — the plain join(Class,...) shorthand silently drops unassigned planning entities, which would have defeated the null-shift-forbids-every-seat design"
    - "Package-private static pure helpers for solver population logic (buildShiftBandPairs/buildShiftAssignments), mirroring resolveEffectiveHours's precedent — directly unit-testable with no Spring context"

key-files:
  created:
    - src/main/java/com/wfm/model/AgentShiftAssignment.java
    - src/main/java/com/wfm/model/ShiftBandPair.java
    - src/main/resources/db/migration/V41__agent_shift_assignment.sql
    - src/test/java/com/wfm/solver/SolverConfigBuildTest.java
    - src/test/java/com/wfm/solver/ShiftEnvelopeComplianceConstraintTest.java
    - src/test/java/com/wfm/solver/TestConstructionHeuristicPhases.java
    - src/test/java/com/wfm/service/SolverServiceShiftAssignmentTest.java
  modified:
    - src/main/resources/solverConfig.xml
    - src/main/java/com/wfm/model/Schedule.java
    - src/main/java/com/wfm/model/ScheduleConfig.java
    - src/main/java/com/wfm/model/ConstraintWeights.java
    - src/main/java/com/wfm/service/SolverService.java
    - src/main/java/com/wfm/solver/ScheduleConstraintProvider.java
    - src/test/java/com/wfm/solver/ScheduleConstraintClassification.java
    - src/test/java/com/wfm/migration/MigrationEntityConsistencyTest.java
    - src/test/java/com/wfm/solver/BreakAwareConstructionTest.java
    - src/test/java/com/wfm/solver/BulkUnderallocationSoftConstraintTest.java
    - src/test/java/com/wfm/solver/FullScale150AgentTest.java
    - src/test/java/com/wfm/solver/IncrementalScoringDiagnosticTest.java
    - src/test/java/com/wfm/solver/MinimumStaffingConstraintTest.java
    - src/test/java/com/wfm/solver/MultiDayConstraintDiagnosticTest.java
    - src/test/java/com/wfm/solver/NinetyAgent12HourTest.java
    - src/test/java/com/wfm/solver/NinetyFiveAgentReproTest.java
    - src/test/java/com/wfm/solver/SingleDaySolvableTest.java
    - src/test/java/com/wfm/solver/TwelveHourUniformDemandTest.java

key-decisions:
  - "schedulingMode threaded through Schedule as a @Transient field, not a persisted column — no migration in this plan touches the schedule table, and the value is solver-input-only (never read back from a stored row)"
  - "shiftEnvelopeCompliance joins AgentShiftAssignment via forEachIncludingUnassigned, not the plain join(Class,...) shorthand — RESEARCH.md's Open Question 2 (whether a null shiftBandPair naturally forbids every seat via the positive-join form) was answered empirically FALSE by a failing test before this fix; Timefold's default forEach/join silently filters out planning entities with a null genuine variable"
  - "shiftEnvelopeCompliance carries an explicit ScheduleConfig join + SHIFT-mode filter as defence in depth beyond structural inertness (no AgentShiftAssignment rows exist on a SLOT desk), so the constraint is provably silent even in an adversarial fixture with shift rows present under a SLOT config"

patterns-established:
  - "Every hand-built SolverConfig test fixture that declares both @PlanningEntity classes and calls buildSolver() must use TestConstructionHeuristicPhases' two explicit phases, not a bare ConstructionHeuristicPhaseConfig() — the same ambiguity XCUT-03 exists to catch in solverConfig.xml"

requirements-completed: [ENVL-01, ENVL-02, ENVL-03, ENVL-06, XCUT-03]

coverage:
  - id: D1
    description: "AgentShiftAssignment (second @PlanningEntity) and ShiftBandPair (problem fact) exist; the desk's (template,band) pairs are filtered per agent-day by exact effectiveHours match and sorted deterministically"
    requirement: "ENVL-01"
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/SolverServiceShiftAssignmentTest.java#buildShiftBandPairs_sortsByTemplateNameThenEffectiveFromThenBandOffset"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/SolverServiceShiftAssignmentTest.java#eligibleShiftBandPairs_matchingHours_returnsThePair"
        status: pass
    human_judgment: false
  - id: D2
    description: "shiftEnvelopeCompliance is a genuine hard ConstraintStream constraint (Option A) forbidding a seat outside its agent's chosen envelope, including every stated boundary case and the null-chosen-pair trace"
    requirement: "ENVL-02"
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/solver/ShiftEnvelopeComplianceConstraintTest.java#seatAtEnvelopeStart_legal"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/solver/ShiftEnvelopeComplianceConstraintTest.java#seatAtEnvelopeEnd_illegal"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/solver/ShiftEnvelopeComplianceConstraintTest.java#seatAtBreakStart_forbidden"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/solver/ShiftEnvelopeComplianceConstraintTest.java#seatAtBreakEnd_legal"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/solver/ShiftEnvelopeComplianceConstraintTest.java#nullChosenPair_penalisesEverySeatThatDay"
        status: pass
    human_judgment: false
  - id: D3
    description: "Specialization still varies freely between timeslots inside a shift envelope — shiftEnvelopeCompliance is silent on it"
    requirement: "ENVL-03"
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/solver/ShiftEnvelopeComplianceConstraintTest.java#specializationVariesWithinShift_noPenaltyFromThisConstraint"
        status: pass
    human_judgment: false
  - id: D4
    description: "The construction heuristic reaches a feasible initial solution across two explicitly-scoped phases with no pre-assignment pipeline"
    requirement: "ENVL-06"
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/solver/SolverConfigBuildTest.java#solverConfigXmlBuildsARealSolverWithTwoPlanningEntityClasses"
        status: pass
      - kind: integration
        ref: "src/test/java/com/wfm/solver/BreakAwareConstructionTest.java (all 3 tests, real two-phase CH + local search)"
        status: pass
    human_judgment: false
  - id: D5
    description: "solverConfig.xml's construction-heuristic change is validated by a test that actually builds a solver from the real XML"
    requirement: "XCUT-03"
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/solver/SolverConfigBuildTest.java#solverConfigXmlBuildsARealSolverWithTwoPlanningEntityClasses"
        status: pass
    human_judgment: false
  - id: D6
    description: "A slot-scheduled desk's solve is structurally unchanged: zero AgentShiftAssignment rows, zero (template,band) pairs, shiftEnvelopeCompliance silent even under an adversarial fixture, and every pre-existing slot-mode test stays green"
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/SolverServiceShiftAssignmentTest.java#buildShiftAssignments_slotMode_returnsEmpty"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/solver/ShiftEnvelopeComplianceConstraintTest.java#slotMode_penalisesNothingEvenWithShiftRowsPresent"
        status: pass
      - kind: integration
        ref: "./gradlew test (full suite, 444 tests, 0 failures)"
        status: pass
    human_judgment: false

duration: 50min
completed: 2026-08-27
status: complete
---

# Phase 15 Plan 3: Shift Envelope, Breaks & Library Generation — Shift Envelope Solver Model Summary

**The solver's second `@PlanningEntity` (`AgentShiftAssignment`) is coupled to seat assignment by a genuine hard `ConstraintStream` constraint (`shiftEnvelopeCompliance`), with an entity-level value range filtered by contracted hours, a two-phase shifts-first construction heuristic, and a test that actually builds a `SolverFactory` from the real `solverConfig.xml`.**

## Performance

- **Duration:** ~50 min
- **Tasks:** 3
- **Files modified:** 25 (7 created, 18 modified)

## Accomplishments

- `ShiftBandPair` (immutable problem fact) and `AgentShiftAssignment` (dual `@Entity`+`@PlanningEntity`, mirroring `AgentAssignment`) — the second planning entity, using `allowsUnassigned()` (D-06) and an entity-level `@ValueRangeProvider` filtered by `AgentDayConfig.effectiveHours` (P-13)
- `solverConfig.xml` rewritten with two explicitly-scoped construction-heuristic phases (shifts-first, P-15) — verified against the pinned Timefold 1.16.0 `solver.xsd`
- `SolverConfigBuildTest` (XCUT-03) — the first test under `src/test/java/com/wfm/solver/` that builds a real `SolverFactory` from `solverConfig.xml`
- V41 migration: `agent_shift_assignment` table (one row per working agent-day) + `constraint_weights.shift_envelope_compliance_weight` (hard by default)
- `SolverService.buildShiftBandPairs`/`buildShiftAssignments` — pure, mode-gated static helpers populating the desk's sorted `(template,band)` pairs and one `AgentShiftAssignment` per working agent-day; both empty on a SLOT-mode desk
- `ScheduleConstraintProvider.shiftEnvelopeCompliance` — the hard constraint Option A's coupling rests on, joining `AgentAssignment` to `AgentShiftAssignment` (via `forEachIncludingUnassigned`, not the plain join shorthand — see Deviations) then `ScheduleConfig`, penalising a definite disagreement
- `ScheduleConstraintClassification` gains a `MODE_GATED` row for the new constraint; `ScheduleConstraintClassificationTest` stays green (20 constraints, 20 weights)
- Full suite green: 444 tests, 0 failures

## Task Commits

1. **Task 1: A solver builds and runs with two planning entities** - `16440e2` (feat)
2. **Task 2: One row per working agent-day** - `72cf183` (feat)
3. **Task 3: shiftEnvelopeCompliance** - `9af53cf` (feat)

## Files Created/Modified

- `src/main/java/com/wfm/model/ShiftBandPair.java` - immutable (template,band) problem fact + `covers()`/`netHours()`/`displayName()`
- `src/main/java/com/wfm/model/AgentShiftAssignment.java` - second `@PlanningEntity`, entity-level value range, D-07 denormalised columns
- `src/main/resources/solverConfig.xml` - two explicitly-scoped CH phases (shifts-first)
- `src/test/java/com/wfm/solver/SolverConfigBuildTest.java` - XCUT-03
- `src/main/java/com/wfm/model/Schedule.java` - `shiftAssignments`/`shiftBandPairs` collections + transient `schedulingMode`
- `src/main/java/com/wfm/model/ScheduleConfig.java` - `schedulingMode` component
- `src/main/resources/db/migration/V41__agent_shift_assignment.sql` - new table + weight column
- `src/main/java/com/wfm/model/ConstraintWeights.java` - `shiftEnvelopeComplianceWeight`
- `src/main/java/com/wfm/service/SolverService.java` - `buildShiftBandPairs`/`buildShiftAssignments`, mode-gated population wiring, `runPreSolveScoreDiagnostic` entity-class fix
- `src/test/java/com/wfm/service/SolverServiceShiftAssignmentTest.java` - ordering, one-row-per-working-day, shared-list-instance, slot-mode-empty, empty-value-range coverage
- `src/test/java/com/wfm/migration/MigrationEntityConsistencyTest.java` - extended to `agent_shift_assignment`
- `src/main/java/com/wfm/solver/ScheduleConstraintProvider.java` - `shiftEnvelopeCompliance`
- `src/test/java/com/wfm/solver/ScheduleConstraintClassification.java` - new `MODE_GATED` row
- `src/test/java/com/wfm/solver/ShiftEnvelopeComplianceConstraintTest.java` - boundary cases, null-pair trace, distinct-values, SLOT-mode adversarial case
- `src/test/java/com/wfm/solver/TestConstructionHeuristicPhases.java` - shared two-phase CH builder for hand-built test `SolverConfig`s
- `src/test/java/com/wfm/solver/{BreakAwareConstructionTest,BulkUnderallocationSoftConstraintTest,FullScale150AgentTest,IncrementalScoringDiagnosticTest,MinimumStaffingConstraintTest,MultiDayConstraintDiagnosticTest,NinetyAgent12HourTest,NinetyFiveAgentReproTest,SingleDaySolvableTest,TwelveHourUniformDemandTest}.java` - `withEntityClasses`/`ConstraintVerifier.build` calls updated to declare both planning entity classes (Rule 3 fix)

## Decisions Made

- `Schedule.schedulingMode` is `@Transient`, not persisted — this plan's migration touches only `agent_shift_assignment` and `constraint_weights`; a persisted column would need its own migration this plan doesn't carry.
- `shiftEnvelopeCompliance` uses `forEachIncludingUnassigned(AgentShiftAssignment.class)` rather than the plain `.join(AgentShiftAssignment.class, ...)` shorthand — see Deviations, this is a load-bearing correctness fix, not a style choice.
- `shiftEnvelopeCompliance` keeps an explicit `ScheduleConfig`/SHIFT-mode filter even though structural inertness (no shift rows on SLOT desks) already makes it inert — defence in depth, proven by an adversarial test fixture with shift rows manually present under a SLOT config.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] `shiftEnvelopeCompliance`'s plain positive-join form silently dropped the null-chosen-pair case**
- **Found during:** Task 3 (writing `ShiftEnvelopeComplianceConstraintTest`)
- **Issue:** RESEARCH.md's Open Question 2 flagged this trace as needing verification rather than assumption. The plan's recommended plain-join form (`.join(AgentShiftAssignment.class, ...)`) reported **zero** matches for a fixture with three seats and a null `shiftBandPair`, not the expected three — Timefold's `ConstraintFactory.forEach`/`.join(Class,...)` family silently filters out planning entities whose genuine planning variable is null, exactly the case the null-shift branch needed to catch.
- **Fix:** Built the join against `factory.forEachIncludingUnassigned(AgentShiftAssignment.class)` instead of the class-shorthand join, which does not apply that filtering.
- **Files modified:** `src/main/java/com/wfm/solver/ScheduleConstraintProvider.java`
- **Verification:** `ShiftEnvelopeComplianceConstraintTest#nullChosenPair_penalisesEverySeatThatDay` — failed with the plain form (0 vs. expected 3), passes after the fix.
- **Committed in:** `9af53cf` (Task 3 commit)

**2. [Rule 3 - Blocking] Every hand-built `SolverConfig`/`ConstraintVerifier` in the test suite needed both planning entity classes declared**
- **Found during:** Task 3 (full-suite run after adding `shiftEnvelopeCompliance`)
- **Issue:** `defineConstraints()` now unconditionally builds a constraint referencing `AgentShiftAssignment.class`. Any `SolverConfig`/`ConstraintVerifier` declaring only `AgentAssignment.class` as an entity class threw `IllegalArgumentException: Cannot use class (AgentShiftAssignment) in a constraint stream...` at score-director-factory build time — this broke 11 test files plus `SolverService.runPreSolveScoreDiagnostic` (production code, wrapped in a non-fatal try/catch but silently defeating the diagnostic on every real solve).
- **Fix:** Added `AgentShiftAssignment.class` to every `withEntityClasses(...)`/`ConstraintVerifier.build(...)` call site. A second, related failure surfaced once entity classes were fixed: any hand-built `SolverConfig` that called `buildSolver()` with a bare `new ConstructionHeuristicPhaseConfig()` (relying on auto-deduction) now hit the exact `IllegalArgumentException("no entityClass configured ... cannot be deduced automatically")` XCUT-03 exists to catch in `solverConfig.xml` — fixed by introducing `TestConstructionHeuristicPhases`, a shared two-phase CH builder mirroring `solverConfig.xml` exactly, and using it in the 5 affected test files.
- **Files modified:** `src/main/java/com/wfm/service/SolverService.java`, `src/test/java/com/wfm/solver/{BreakAwareConstructionTest,BulkUnderallocationSoftConstraintTest,FullScale150AgentTest,IncrementalScoringDiagnosticTest,MinimumStaffingConstraintTest,MultiDayConstraintDiagnosticTest,NinetyAgent12HourTest,NinetyFiveAgentReproTest,SingleDaySolvableTest,TwelveHourUniformDemandTest}.java`, new `TestConstructionHeuristicPhases.java`
- **Verification:** Full suite green (444 tests, 0 failures) after the fix; each affected file's own assertions (unrelated to this plan's scope) are byte-identical to before.
- **Committed in:** `9af53cf` (Task 3 commit)

---

**Total deviations:** 2 auto-fixed (1 bug — the load-bearing null-shift join fix; 1 blocking — entity-class/CH-phase propagation across the test suite and one production diagnostic)
**Impact on plan:** Both were necessary for the plan's own stated done-criteria (full suite green) and for correctness (D-06's null-shift trace, explicitly flagged in RESEARCH.md as needing empirical verification rather than assumption). No scope creep — every touched file was in the direct blast radius of adding a second `@PlanningEntity` class to `ScheduleConstraintProvider.defineConstraints()`.

## Issues Encountered

None beyond the deviations above.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- `AgentShiftAssignment`/`ShiftBandPair` and `shiftEnvelopeCompliance` are ready for plan 15-06's break-clustering constraint (which reads `AgentShiftAssignment.getShiftBandPair()` to derive "on break") and for the ENVL-07 ground-truth walker's independent verification.
- `AgentShiftAssignment`'s D-07 denormalised accepted-row columns (`templateName`, `shiftStartTime`, etc.) are declared but not yet populated — that is the accept-time snapshot obligation for a later plan in this phase (not in this plan's `files_modified`).
- `TestConstructionHeuristicPhases` is available for any later plan's hand-built `SolverConfig` test fixtures needing the same two-phase CH shape (e.g. plan 15-08's CH-ordering benchmark, which swaps which phase comes first).
- The pre-existing flaky `BreakAwareConstructionTest` (noted in 15-01-SUMMARY.md as load-sensitive, unrelated to that plan) passed cleanly in this run's full-suite execution.

## Self-Check: PASSED

- All created files verified present on disk: `AgentShiftAssignment.java`, `ShiftBandPair.java`,
  `V41__agent_shift_assignment.sql`, `SolverConfigBuildTest.java`,
  `ShiftEnvelopeComplianceConstraintTest.java`, `TestConstructionHeuristicPhases.java`,
  `SolverServiceShiftAssignmentTest.java`.
- All three task commits verified present in `git log`: `16440e2`, `72cf183`, `9af53cf`.
- Plan `<verification>` re-run: `./gradlew test` green (444 tests, 0 failures);
  `SolverConfigBuildTest` proves the two-phase CH XML is structurally valid;
  `ScheduleConstraintClassificationTest` green with the new row (6/6);
  every pre-existing slot-mode test passes (entity-class/CH-phase plumbing was the only touch,
  assertions unmodified).

---
*Phase: 15-shift-envelope-breaks-library-generation*
*Completed: 2026-08-27*
