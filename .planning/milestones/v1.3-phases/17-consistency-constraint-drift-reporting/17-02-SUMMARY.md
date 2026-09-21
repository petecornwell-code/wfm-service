---
phase: 17-consistency-constraint-drift-reporting
plan: 02
subsystem: solver
tags: [timefold, constraint-streams, spring-mvc, mockito]

requires:
  - phase: 17-consistency-constraint-drift-reporting
    provides: "17-01's ConstraintWeights.consistentStartWeight/consistencyToleranceMinutes/preferredStartShiftModeWeight fields, ShiftBandPair.startDeviationMinutes, ScheduleConfig.consistencyToleranceMinutes"
provides:
  - "ScheduleConstraintProvider.preferredStartShiftMode -- anchor-style shift-granularity preference constraint, independent of stored usual shifts (D-09)"
  - "ConstraintWeights.preferredStartShiftModeWeight's @ConstraintWeight annotation (17-01's deliberately deferred carry-over)"
  - "ConstraintWeightsDto/Service/Controller round-trip for consistentStartWeight, consistencyToleranceMinutes, preferredStartShiftModeWeight, with D-07/D-08/T-17-04 save-time rejections"
  - "ConstraintPrecedenceObservabilityTest -- proof that both constraints appear as separate SolutionManager.explain() lines"
affects: [17-04-benchmark-and-defaults, 17-05-frontend]

actuals:
  tokens: 14106
  tasks: 3
  commits: 5

tech-stack:
  added: []
  patterns:
    - "Compiled-language TDD RED via an intentionally-wrong stub (penalizeConfigurable returning a hardcoded 0), not a missing method -- a Timefold ConstraintVerifier test needs the method reference to compile before any test can run, so RED is a real assertion failure against a stub, never a compile error"
    - "ConstraintMatchTotal map keys carry a constraintPackage prefix derived from the @PlanningSolution class -- match on getConstraintName() (as ScheduleOutputService/SolverService already do), never assume the map key equals the bare asConstraint(...) string"

key-files:
  created:
    - src/test/java/com/wfm/solver/PreferredStartShiftModeConstraintTest.java
    - src/test/java/com/wfm/service/ConstraintWeightsServiceTest.java
    - src/test/java/com/wfm/solver/ConstraintPrecedenceObservabilityTest.java
    - src/test/java/com/wfm/controller/ConstraintWeightsControllerTest.java
  modified:
    - src/main/java/com/wfm/solver/ScheduleConstraintProvider.java
    - src/main/java/com/wfm/model/ConstraintWeights.java
    - src/main/java/com/wfm/dto/ConstraintWeightsDto.java
    - src/main/java/com/wfm/service/ConstraintWeightsService.java
    - src/test/java/com/wfm/solver/ScheduleConstraintClassification.java
    - src/test/java/com/wfm/solver/ScheduleConstraintClassificationTest.java

key-decisions:
  - "preferredStartShiftMode leads with plain forEach(AgentShiftAssignment.class), not forEachIncludingUnassigned -- an unassigned shift has no envelope start to compare, and forEach's implicit null-filtering on the genuine shiftBandPair planning variable already produces the required behavior without an extra guard."
  - "ConstraintPrecedenceObservabilityTest matches on ConstraintMatchTotal.getConstraintName() rather than the map key, after discovering the map key is 'com.wfm.model/<name>' (constraintPackage derived from the @PlanningSolution class, since ScheduleConstraintProvider names none explicitly) -- the same pattern ScheduleOutputService and SolverService already use for display."
  - "ConstraintWeightsServiceTest uses @ExtendWith(MockitoExtension.class) per the plan's explicit instruction; ConstraintWeightsControllerTest uses plain Mockito.mock(...) with no extension, closer to GlobalExceptionHandlerTest's plain-instantiation precedent -- both are equally 'no Spring context', the difference is cosmetic."

patterns-established:
  - "A second Timefold constraint sharing ShiftBandPair.startDeviationMinutes as its third caller (after usualShiftConsistency and the drift report) -- one computation, three callers, never a re-derivation."

requirements-completed: [CONS-04, CONS-05]

coverage:
  - id: D1
    description: "Preferred start (shift mode) penalises the absolute deviation between assigned envelope start and preferredStartTime, symmetrically in both directions (anchor, not floor)"
    requirement: CONS-05
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/solver/PreferredStartShiftModeConstraintTest.java#assignedLaterThanPreferred_penalisedByTwo"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/solver/PreferredStartShiftModeConstraintTest.java#assignedEarlierThanPreferred_penalisedTheSameAsLater"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/solver/PreferredStartShiftModeConstraintTest.java#subIncrementDeviation_costsOneIncrement"
        status: pass
    human_judgment: false
  - id: D2
    description: "The preference constraint fires whether or not a usual shift is stored for the agent-day (D-09), and is mode-gated to SHIFT desks"
    requirement: CONS-05
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/solver/PreferredStartShiftModeConstraintTest.java#noStoredUsualShift_preferenceStillHonoured"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/solver/PreferredStartShiftModeConstraintTest.java#slotMode_noPenaltyEvenWhenDeviating"
        status: pass
    human_judgment: false
  - id: D3
    description: "Both new weights and the tolerance band are settable per desk through the existing PUT endpoint"
    requirement: CONS-03
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/ConstraintWeightsServiceTest.java#getWeights_returnsAllThreeNewFieldsPopulatedFromTheEntity"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/controller/ConstraintWeightsControllerTest.java#updateWeights_passesDeskIdAndDtoThroughAndReturnsTheServiceResultUnchanged"
        status: pass
    human_judgment: false
  - id: D4
    description: "A save whose merged consistentStartWeight carries a non-zero hard component is rejected (D-07); softness is structural, not documented"
    requirement: CONS-04
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/ConstraintWeightsServiceTest.java#partialUpdate_consistentStartWeightNonZeroHard_rejectedAndNeverSaved"
        status: pass
    human_judgment: false
  - id: D5
    description: "A save whose preferredStartShiftModeWeight soft score is not strictly below consistentStartWeight's soft score is rejected, boundary-tested at equal and one-below, and against the MERGED entity (a field the client didn't send still participates)"
    requirement: CONS-05
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/ConstraintWeightsServiceTest.java#partialUpdate_preferredWeightSoftEqualToConsistencyWeightSoft_rejectedAndNeverSaved"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/ConstraintWeightsServiceTest.java#partialUpdate_preferredWeightSoftOneBelowConsistencyWeightSoft_accepted"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/ConstraintWeightsServiceTest.java#partialUpdate_loweringConsistencyToTheStoredPreferenceValue_rejectedAgainstTheMergedEntity"
        status: pass
    human_judgment: false
  - id: D6
    description: "A negative consistencyToleranceMinutes is rejected; zero is accepted"
    requirement: CONS-03
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/ConstraintWeightsServiceTest.java#partialUpdate_negativeToleranceMinutes_rejectedAndNeverSaved"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/ConstraintWeightsServiceTest.java#partialUpdate_zeroToleranceMinutes_accepted"
        status: pass
    human_judgment: false
  - id: D7
    description: "SolutionManager.explain() lists 'Usual shift consistency' and 'Preferred start (shift mode)' as two separate entries, never merged into one, on a fixture where both fire"
    requirement: CONS-05
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/solver/ConstraintPrecedenceObservabilityTest.java#bothConstraints_appearAsTwoDistinctEntriesEachWithAPositiveMatchCount"
        status: pass
    human_judgment: false
  - id: D8
    description: "A service-thrown IllegalArgumentException propagates uncaught through ConstraintWeightsController and maps to 400 VALIDATION_FAILED via the existing GlobalExceptionHandler"
    requirement: CONS-04
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/controller/ConstraintWeightsControllerTest.java#updateWeights_propagatesTheServicesIllegalArgumentExceptionUncaught"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/controller/ConstraintWeightsControllerTest.java#theControllersIllegalArgumentException_mapsTo400ValidationFailedThroughTheGlobalHandler"
        status: pass
    human_judgment: false

duration: 39min
completed: 2026-09-17
status: complete
---

# Phase 17 Plan 02: Preferred Start (Shift Mode) Constraint & Structural Precedence Summary

Adds a new anchor-style `Preferred start (shift mode)` Timefold constraint that fires independently
of stored usual shifts, wires its weight (plus consistency's weight and tolerance band) through the
existing per-desk `ConstraintWeightsDto`/`Service`/`Controller` chain, and converts CONS-04's softness
and CONS-06's precedence from documented conventions into save-time-enforced, test-covered invariants.

## Performance

- **Duration:** 39 min
- **Started:** 2026-09-17T16:33:20Z
- **Completed:** 2026-09-17T17:12:28Z
- **Tasks:** 3 completed
- **Files modified:** 10 (4 created, 6 modified)

## Accomplishments

- `ScheduleConstraintProvider.preferredStartShiftMode` penalises the absolute deviation between an
  agent's assigned shift envelope start and their `preferredStartTime`, symmetrically in both
  directions — an anchor, never the old per-slot `honourPreferredStartTime`'s lateness-only floor —
  and fires whether or not a usual shift is stored (D-09), reusing `ShiftBandPair
  .startDeviationMinutes` as its third caller.
- `ConstraintWeights.preferredStartShiftModeWeight` gained the `@ConstraintWeight` annotation
  17-01 deliberately deferred (its carry-over item), landing together with the constraint method
  that reads it — `ScheduleConstraintClassificationTest`'s reflective completeness guard stays
  green throughout.
- `ConstraintWeightsDto`/`ConstraintWeightsService` now round-trip `consistentStartWeight`,
  `consistencyToleranceMinutes` (a plain `Integer`, not a `ScoreDto`), and
  `preferredStartShiftModeWeight` through the existing `PUT /api/v1/desks/{deskId}/constraint-weights`
  endpoint, with three save-time rejections against the fully-merged entity: D-07 (consistency's
  hard score must be 0), D-08 (preference weight must stay strictly below consistency's), and
  T-17-04 (tolerance band must not go negative).
- `ConstraintPrecedenceObservabilityTest` proves CONS-06's precedence is genuinely observable:
  solving a real shift-mode fixture, mutating one agent-day's post-solve
  `ResolvedUsualShiftTarget`/`AgentPreference` facts so it is both drifted and off-preference, and
  re-scoring through a fresh `SolutionManager` shows "Usual shift consistency" and "Preferred start
  (shift mode)" as two distinct, non-zero `explain()` entries.
- `ConstraintWeightsControllerTest`, the second from-scratch web-layer test class this plan builds
  (`GlobalExceptionHandlerTest`'s plain-instantiation style, no Spring context), proves the
  controller's passthrough shape and that a service-thrown `IllegalArgumentException` maps to 400
  `VALIDATION_FAILED` through the existing `GlobalExceptionHandler`.

## Task Commits

Each task was committed atomically (Tasks 1 and 2 carried `tdd="true"` — genuine RED/GREEN cycles):

1. **Task 1: The shift-granularity preferred-start constraint (anchor, not floor)**
   - `bac2c40` (test) — RED: 4 of 9 `PreferredStartShiftModeConstraintTest` cases fail against an
     intentionally-wrong `-> 0` stub (real `java.lang.AssertionError`, not a compile error)
   - `6f33bcf` (feat) — GREEN: real deviation calculation, all 9 cases + classification test pass
2. **Task 2: Per-desk config for both weights and the tolerance band, with save-time enforcement**
   - `dd13906` (test) — RED: 9 of 10 `ConstraintWeightsServiceTest` cases fail (service doesn't yet
     read/write the three fields or enforce D-07/D-08/T-17-04)
   - `4f7b5bf` (feat) — GREEN: merge blocks + three validation throws added, all 10 cases pass
3. **Task 3: Prove the precedence is observable, not inferred** - `c8e3272` (test) — two new
   from-scratch test classes, no production change

**Plan metadata:** (this commit)

_TDD Gate Compliance: both TDD tasks show a `test(...)` commit strictly preceding its `feat(...)`
commit, each RED phase confirmed by a real `./gradlew test` run showing genuine assertion failures
(not compile errors, not unrelated failures) before the corresponding GREEN commit. No REFACTOR
commits were needed — neither GREEN implementation required cleanup beyond the initial correct
form._

## Files Created/Modified

- `src/test/java/com/wfm/solver/PreferredStartShiftModeConstraintTest.java` - new, 9 tests covering all `<behavior>` rows
- `src/main/java/com/wfm/solver/ScheduleConstraintProvider.java` - `preferredStartShiftMode` constraint, registered after `usualShiftConsistency`
- `src/main/java/com/wfm/model/ConstraintWeights.java` - `@ConstraintWeight("Preferred start (shift mode)")` annotation added (17-01 carry-over)
- `src/test/java/com/wfm/solver/ScheduleConstraintClassification.java` - `"Preferred start (shift mode)"` classification row
- `src/test/java/com/wfm/solver/ScheduleConstraintClassificationTest.java` - `MODE_GATED` expected set extended to thirteen rows
- `src/main/java/com/wfm/dto/ConstraintWeightsDto.java` - `consistentStartWeight`, `consistencyToleranceMinutes` (Integer), `preferredStartShiftModeWeight` fields + accessors
- `src/main/java/com/wfm/service/ConstraintWeightsService.java` - merge blocks in `updateWeights`, three D-07/D-08/T-17-04 validation throws against the merged entity, `toDto` extended
- `src/test/java/com/wfm/service/ConstraintWeightsServiceTest.java` - new, from scratch, 10 tests
- `src/test/java/com/wfm/solver/ConstraintPrecedenceObservabilityTest.java` - new, D-10's second backend artefact
- `src/test/java/com/wfm/controller/ConstraintWeightsControllerTest.java` - new, from scratch, 4 tests

## Decisions Made

- `preferredStartShiftMode` leads with plain `forEach(AgentShiftAssignment.class)`, not
  `forEachIncludingUnassigned` — an unassigned shift has no envelope start, and `forEach`'s implicit
  null-filtering on the genuine `shiftBandPair` planning variable already produces the required
  behavior with no extra guard needed.
- `ConstraintPrecedenceObservabilityTest` matches `ConstraintMatchTotal`s by `getConstraintName()`
  rather than the raw map key — the key carries a `constraintPackage` prefix
  (`com.wfm.model/<name>`, derived from the `@PlanningSolution` class since
  `ScheduleConstraintProvider` names no explicit package), the same pattern `ScheduleOutputService`
  and `SolverService` already use for display.
- `ConstraintWeightsServiceTest` uses `@ExtendWith(MockitoExtension.class)` per the plan's explicit
  instruction; `ConstraintWeightsControllerTest` uses plain `Mockito.mock(...)` with no extension,
  closer to `GlobalExceptionHandlerTest`'s plain-instantiation precedent — both carry zero Spring
  imports, the difference between them is cosmetic.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking issue] `ScheduleConstraintClassificationTest`'s `MODE_GATED` set updated (not in `files_modified`)**
- **Found during:** Task 1, adding the `preferredStartShiftMode` builder method
- **Issue:** `ScheduleConstraintClassificationTest`'s reflective derivation counts every
  `Constraint`-returning builder method on `ScheduleConstraintProvider`, regardless of whether it is
  registered in `defineConstraints`. Adding the new method (even as a RED-phase stub) immediately
  broke `bothReflectionDerivationsAgreeWithEachOther`, `classificationKeySetExactlyEqualsTheRegisteredConstraintSet`,
  and `thePhase15ModeGatedSetIsExactlyTheExpectedRows` unless the `@ConstraintWeight` annotation, a
  classification row, and the `MODE_GATED` expected-set update all landed in the same commit.
- **Fix:** Added the annotation, classification row, and expected-set update together with the RED
  commit — mirroring 17-01's own precedent (Deviation 3) for the identical class of issue.
- **Files modified:** `src/test/java/com/wfm/solver/ScheduleConstraintClassificationTest.java`
- **Verification:** `./gradlew test --tests "com.wfm.solver.ScheduleConstraintClassificationTest"` exits 0
- **Committed in:** `bac2c40`

**2. [Rule 1 - Bug found during test authoring] `ConstraintMatchTotal` map key is not the bare constraint name**
- **Found during:** Task 3, first run of `ConstraintPrecedenceObservabilityTest`
- **Issue:** The test initially asserted `totals.containsKeys("Usual shift consistency",
  "Preferred start (shift mode)")` directly against `getConstraintMatchTotalMap()`'s key set. The
  actual keys are `"com.wfm.model/Usual shift consistency"` etc. — Timefold derives
  `constraintPackage` from the `@PlanningSolution` class's package (`com.wfm.model`, i.e.
  `Schedule`'s package) when `ScheduleConstraintProvider` names no explicit package, not from the
  provider's own package (`com.wfm.solver`). The test failed on this assertion before reaching the
  match-count checks it exists to prove.
- **Fix:** Filter `totals.values()` by `ConstraintMatchTotal.getConstraintName()` instead of the raw
  map key — the same access pattern `ScheduleOutputService.buildConstraintViolations` and
  `SolverService.runPreSolveScoreDiagnostic` already use for the human-readable name.
- **Files modified:** `src/test/java/com/wfm/solver/ConstraintPrecedenceObservabilityTest.java`
- **Verification:** `./gradlew test --tests "com.wfm.solver.ConstraintPrecedenceObservabilityTest"` exits 0
- **Committed in:** `c8e3272` (test written and fixed before its single commit; no separate fix commit needed since this is a `type="auto"` task, not TDD)

---

**Total deviations:** 2 auto-fixed (1 Rule 3 — pre-existing test's completeness guard required a
same-commit update; 1 Rule 1 — a test authoring bug, corrected before commit).
**Impact on plan:** Both auto-fixes were necessary for the plan's own acceptance criteria (`./gradlew
test` exits 0 for the whole suite) to hold. No scope creep — no new architecture, no functionality
beyond what the plan specified.

## Issues Encountered

None — both TDD cycles produced clean RED/GREEN pairs on the first attempt; no node-repair or
architectural escalation was needed.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Plan 17-03 (over-subscription ranking, drift date-filter, Excel export, XCUT-02 write-path guard)
and plan 17-04 (benchmark, redone per-agent-day arithmetic, human-gated default weights, V49) can
both proceed unblocked. Plan 17-04 explicitly depends on `ConstraintWeightsService`'s D-07/D-08
rejections (verified present, `preferredStartShiftMode` correctly registered and classified) and on
`ConstraintWeights.consistentStartWeight`/`preferredStartShiftModeWeight`'s current provisional
defaults (`0hard/2soft`, `0hard/1soft`), both unchanged by this plan. Plan 17-05's frontend work can
consume `ConstraintWeightsDto`'s three new fields exactly as named in this plan's `<interfaces>`
block (`consistentStartWeight`, `consistencyToleranceMinutes`, `preferredStartShiftModeWeight`) —
confirmed byte-for-byte against 17-05-PLAN.md's own field references. No blockers.

## Self-Check: PASSED

- All 4 newly created files verified present on disk (`PreferredStartShiftModeConstraintTest.java`,
  `ConstraintWeightsServiceTest.java`, `ConstraintPrecedenceObservabilityTest.java`,
  `ConstraintWeightsControllerTest.java`).
- All 5 commit hashes (`bac2c40`, `6f33bcf`, `dd13906`, `4f7b5bf`, `c8e3272`) verified present via
  `git log`.
- All task-level `<acceptance_criteria>` re-run and passing: region-scoped `honourPreferredStartTime`
  untouched (`isBefore` count 1, `ifExists(ScheduleConfig.class,` present), classification grep
  present, region-scoped `MINUTES.between` count 0 in `preferredStartShiftMode`, symmetric-penalty
  pair asserts equal values, D-09 test asserts non-zero with no `ResolvedUsualShiftTarget` fixture;
  `ConstraintWeightsServiceTest` has 10 `@Test` methods (≥8), zero Spring-context imports, all three
  rejection tests assert exact message + never-saved; `ConstraintWeightsControllerTest` has zero
  Spring-context imports, asserts 400/`VALIDATION_FAILED`, `ConstraintWeightsController.java` shows
  no diff.
- Plan-level `<verification>` re-run: `./gradlew test` green — **762 tests, 0 failures, 0 errors**
  across all 106 test classes (fresh, unfiltered run, not a stale cached result); `./gradlew
  assemble` succeeds.
- `CONS-04` and `CONS-05` marked complete in `REQUIREMENTS.md` (diff-verified: only those two
  checkboxes flipped, no traceability-table drift). `CONS-03` and `CONS-06` remain open — both are
  shared with plans 17-04/17-05, which have not yet finished (`requirements.ready-ids` correctly
  reports them `blocked`, not `ready`).
