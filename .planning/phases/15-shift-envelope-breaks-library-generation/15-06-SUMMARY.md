---
phase: 15-shift-envelope-breaks-library-generation
plan: 06
subsystem: solver
tags: [timefold, constraint-streams, mode-gating, break-clustering, band-capacity, flyway]

# Dependency graph
requires:
  - phase: 15-03
    provides: AgentShiftAssignment, ShiftBandPair, shiftEnvelopeCompliance, ScheduleConfig.schedulingMode, the shiftEnvelopeCompliance stream-order performance contract
  - phase: 15-04
    provides: ShiftModeFixtures (reusable deterministic shift-mode Schedule builder), the ENVL-07 ground-truth walker pattern
provides:
  - Six mode-gated constraints (exactlyOneBreak, breakDuration, breakBlockedWindow, breakStartAlignment, honourPreferredStartTime, honourPreferredBreakTime) — inert on SHIFT desks, byte-identical bodies on SLOT desks
  - Break clustering's real body (ENVL-09) — a cross-agent per-timeslot aggregation, the first thing breakClusterThresholdPct has ever driven
  - Band capacity as a real hard constraint (ENVL-08/D-03), its constraint_weights.band_capacity_weight column (V42), and the D-03 pre-solve refusal reusing ShiftLibraryValidationService.validate()
  - Completed XCUT-05 classification — 21 constraints, 21 rows, zero OPEN_RESOLVE_IN_PHASE_15, zero NEEDS_SHIFT_VARIANT
  - A fixed multi-column ALTER TABLE parser bug in MigrationEntityConsistencyTest, surfaced by reconciling constraint_weights for the first time
affects: [15-07-shift-library-generation-frontend, 15-08-ch-ordering-benchmark]

actuals:
  tokens: 30179
  tasks: 3
  commits: 3

tech-stack:
  added: []
  patterns:
    - "ifExists(Class, filtering(...)) as the arity-preserving substitute for join(Class)+filter when a constraint stream is already at Quad arity — Timefold 1.16.0's public Constraint Streams API has no 5-tuple (Penta) stream type, so a literal join is impossible past Quad; ifExists achieves the identical mode-gating effect (existence check against a singleton problem fact) without growing tuple arity"
    - "Tag-then-concat-then-regroup for combining two independently groupBy-derived per-key aggregates from different source entity types — Timefold has no Bi-to-Bi stream join (only Bi-to-Uni and Bi-to-Class); each aggregate is mapped into a shared record shape, the two tagged streams are unioned via BiConstraintStream.concat, and one final groupBy with two sum collectors recombines them per key"
    - "!= SHIFT (not == SLOT) as the null-safe mode-gate polarity — Schedule.schedulingMode is unset (null) in every pre-Phase-15 test fixture that never calls setSchedulingMode(), and != SHIFT correctly resolves null to \"active\", preserving those fixtures' behaviour exactly; == SLOT would have silently disabled the six gated constraints on BreakAwareConstructionTest and similar fixtures"
    - "Package-private static helper extraction for testing an instance method's core logic without a Spring context — appendBandCapacityErrors(mode, deskId, service, errors) lets SolverServiceBandCapacityRefusalTest exercise the D-03 refusal wiring with a Mockito mock, mirroring buildShiftBandPairs/buildShiftAssignments's established precedent"

key-files:
  created:
    - src/test/java/com/wfm/solver/ShiftModeBreakGatingTest.java
    - src/test/java/com/wfm/solver/BreakClusteringConstraintTest.java
    - src/test/java/com/wfm/solver/BandCapacityConstraintTest.java
    - src/test/java/com/wfm/service/SolverServiceBandCapacityRefusalTest.java
    - src/main/resources/db/migration/V42__add_band_capacity_weight.sql
  modified:
    - src/main/java/com/wfm/solver/ScheduleConstraintProvider.java
    - src/main/java/com/wfm/model/ConstraintWeights.java
    - src/main/java/com/wfm/service/SolverService.java
    - src/test/java/com/wfm/solver/ScheduleConstraintClassification.java
    - src/test/java/com/wfm/solver/ScheduleConstraintClassificationTest.java
    - src/test/java/com/wfm/migration/MigrationEntityConsistencyTest.java
    - .planning/phases/14-shift-library-scheduling-mode/XCUT-05-constraint-classification.md

key-decisions:
  - "ifExists(ScheduleConfig.class, filtering(...)) replaces the plan's literal join(ScheduleConfig.class)+filter sketch for five of the six Task 1 constraints, because those streams are already Quad arity and Timefold 1.16.0 has no Penta stream to join into — this is a framework API limitation, not a design choice, and is documented in exactlyOneBreak's javadoc as the canonical reference the other four/preference constraints point back to"
  - "Break clustering's on-break side joins Timeslot directly, not through AgentAssignment — an on-break agent structurally has no seat at the timeslot their break covers (shiftEnvelopeCompliance forbids it), so deriving 'on break' from seat rows at that timeslot would always see zero; Timeslot is itself a @ProblemFactCollectionProperty, visited whether or not a seat happens to exist there"
  - "The small-denominator case (a 2-agent desk, one on break) is asserted as an accepted soft, proportional characteristic and the formula is NOT adjusted for it — a tiny desk cannot structurally stay under a 20% clustering threshold once one of two agents breaks, and the resulting penalty (1, soft weight 2 by default) neither runs away nor divides by zero"
  - "appendBandCapacityErrors extracted as a package-private static helper from runPreSolveValidation so the D-03 refusal wiring is directly unit-testable with a Mockito-mocked ShiftLibraryValidationService, without constructing a full SolverService or Spring context"

patterns-established:
  - "Every future Quad-or-deeper constraint that needs a cheap mode/singleton-fact gate should reach for ifExists(Class, filtering(...)) rather than assuming join(Class) is always available — arity is a hard ceiling in this Timefold version, not just a style preference"

requirements-completed: [ENVL-04, ENVL-05, ENVL-08, ENVL-09, XCUT-05]

coverage:
  - id: D1
    description: "The four D-03-named break constraints and the two preference constraints are gated off for SHIFT desks and provably unchanged for SLOT desks, closing ENVL-05 and completing XCUT-05 (zero rows remain OPEN_RESOLVE_IN_PHASE_15 or NEEDS_SHIFT_VARIANT)"
    requirement: "ENVL-05"
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/solver/ShiftModeBreakGatingTest.java (6 isolated per-constraint scenarios, each asserting SHIFT=0 and SLOT=unchanged)"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/solver/ScheduleConstraintClassificationTest.java#thePhase15ModeGatedSetIsExactlyTheNineExpectedRows"
        status: pass
      - kind: integration
        ref: "git diff --quiet HEAD -- src/test/java/com/wfm/solver/BreakAwareConstructionTest.java (unmodified, byte-identical)"
        status: pass
    human_judgment: false
  - id: D2
    description: "ENVL-04's structural consequence — a seated agent-day is contiguous except exactly its assigned band's break — proven on a solved shift-mode fixture, not asserted by inspection"
    requirement: "ENVL-04"
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/solver/ShiftModeBreakGatingTest.java#everySeatedAgentDay_contiguousExceptTheAssignedBreak"
        status: pass
    human_judgment: false
  - id: D3
    description: "Break clustering has a real body: a single-band library measurably starves a mid-shift timeslot (0/2 demand met) and a two-band library does not (2/2 met), reported as staffing numbers"
    requirement: "ENVL-09"
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/solver/BreakClusteringConstraintTest.java#singleBandLibrary_starvesTheMidShiftTimeslot"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/solver/BreakClusteringConstraintTest.java#twoBandLibrary_keepsTheMidShiftTimeslotStaffed"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/solver/BreakClusteringConstraintTest.java#smallDesk_twoAgentsStaggered_unavoidableProportionalPenalty"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/solver/BreakClusteringConstraintTest.java#slotMode_penalisesNothing_evenWithClusteredShiftRowsPresent"
        status: pass
    human_judgment: false
  - id: D4
    description: "A band's set capacity is a real hard cap (exact-N legal, N+1th a violation, linear excess beyond); a blank capacity is genuinely unlimited; capacity is scoped per date; two bands on one template have independent capacities; SLOT desks stay silent"
    requirement: "ENVL-08"
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/solver/BandCapacityConstraintTest.java (8 tests: exact-N, N+1, linear excess, blank-unlimited, per-date scoping, independent bands, SLOT silence, null-pair non-error)"
        status: pass
    human_judgment: false
  - id: D5
    description: "An operator whose band capacities total below the admissible headcount is refused before the solve with a named PreSolveValidationException detail whose message is character-identical to the shift-library report's save-time advisory for the same data (D-08 discipline extended to a third caller)"
    requirement: "ENVL-08"
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/SolverServiceBandCapacityRefusalTest.java#shiftModeDeskWithCapacityShortfall_appendsABandCapacityErrorDetail_messageCharacterIdenticalToTheAdvisory"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/SolverServiceBandCapacityRefusalTest.java (3 more: multi-shortfall, SLOT never calls validate, no-shortfall no-op)"
        status: pass
    human_judgment: false
  - id: D6
    description: "Full suite green (77 test classes, 0 failures/errors) and BreakAwareConstructionTest's 30-agent slot-mode scenario still reports 480/480, 0hard/0soft — no reintroduction of the shiftEnvelopeCompliance stream-order performance regression this plan's constraint_performance_contract exists to prevent"
    verification:
      - kind: integration
        ref: "./gradlew test (full suite, retried once after a transient Gradle XML-report I/O failure with zero actual test failures; second run: BUILD SUCCESSFUL, 77/77 result files report failures=\"0\" errors=\"0\")"
        status: pass
      - kind: integration
        ref: "com.wfm.solver.BreakAwareConstructionTest — printed line: '30-agent 30-min solver: assigned=480/480, score=0hard/0soft'"
        status: pass
    human_judgment: false

duration: ~65min
completed: 2026-08-27
status: complete
---

# Phase 15 Plan 6: Shift Envelope, Breaks & Library Generation — Mode-Gating, Break Clustering & Band Capacity Summary

**Six pre-existing constraints mode-gated off for shift desks via an arity-preserving `ifExists` substitute for Timefold's missing Penta stream, `Break clustering` given a real cross-agent body that measurably starves a single-band library's mid-shift timeslot, and `Band capacity` shipped as a hard cap with a pre-solve refusal that reuses the shift-library report's own computation — closing XCUT-05 with 21 constraints, 21 classified, zero open.**

## Performance

- **Duration:** ~65 min
- **Tasks:** 3
- **Files modified:** 12 (5 created, 7 modified)

## Accomplishments

- `exactlyOneBreak`, `breakDuration`, `breakBlockedWindow`, `breakStartAlignment`, `honourPreferredStartTime`, `honourPreferredBreakTime` are gated off for SHIFT desks and byte-identical, unchanged, for SLOT desks — proven via `ShiftModeBreakGatingTest`'s six isolated fixtures and `BreakAwareConstructionTest`'s untouched, still-green suite
- **Framework limitation discovered and worked around:** the plan's own sketch (`join(ScheduleConfig.class)` + filter) is impossible for five of the six constraints because their streams are already at Quad (4-tuple) arity and Timefold 1.16.0's public Constraint Streams API has no 5-tuple stream type — `ifExists(ScheduleConfig.class, filtering(...))` achieves the identical gating effect without growing arity, documented in `exactlyOneBreak`'s javadoc as the reference every other gated constraint points back to
- The mode gate reads `!= SHIFT`, not `== SLOT` — null-safe, so every pre-Phase-15 fixture that never calls `setSchedulingMode()` (an unset `schedulingMode` defaults to `null`) still resolves to "active", exactly matching its behaviour before this phase
- ENVL-04's structural consequence (a seated agent-day is contiguous except exactly its assigned band's break) proven on an actually-solved `ShiftModeFixtures` schedule, not asserted by inspection
- `Break clustering` replaced its `penalizeConfigurable(a -> 0)` placeholder with a real cross-agent, per-timeslot aggregation — the ROADMAP's required contrast fixture shows a single-band library leaving a mid-shift timeslot at 0/2 demanded seats filled (every agent shares one break window) while a two-band library keeps it at 2/2 (breaks staggered)
- `Band capacity` is a new hard constraint: exact-N legal, N+1th an exact hard violation, blank capacity genuinely unlimited (not zero), capacity scoped per date, two bands on one template independently capped
- `SolverService.appendBandCapacityErrors` refuses an under-capacity shift-mode library before the solve, reusing `ShiftLibraryValidationService.validate()`'s `capacityAdvisories()` — the same computation the shift-library report already calls — so the save-time advisory and the pre-solve refusal are one computation, never two that could disagree
- XCUT-05 complete: 21 constraints, 21 classification rows, zero `OPEN_RESOLVE_IN_PHASE_15`, zero `NEEDS_SHIFT_VARIANT` — also closed a pre-existing drift between the Java classification and its markdown mirror (plan 15-03 added "Shift envelope compliance" to the Java map but never mirrored it in `XCUT-05-constraint-classification.md`)
- Fixed a latent parser bug in `MigrationEntityConsistencyTest`: its multi-column `ALTER TABLE ... ADD COLUMN a, ADD COLUMN b, ...` regex only ever matched the clause immediately after the `ALTER TABLE` keyword, silently dropping every subsequent column in the same statement — surfaced only once `constraint_weights` (which V2 populates via exactly this multi-column syntax) was added to the reconciliation table
- Full suite green: 77 test classes, 0 failures/errors; `BreakAwareConstructionTest`'s 30-agent slot-mode scenario still reports `480/480, 0hard/0soft`, confirming no reintroduction of the `shiftEnvelopeCompliance` stream-order performance regression this plan's constraint-performance contract exists to prevent

## Task Commits

1. **Task 1: Mode-gate six constraints without touching a single body, and close the classification table** - `ffeb8cf` (feat)
2. **Task 2: Break clustering gets a real body, demonstrated on the contrast fixture** - `275a942` (feat)
3. **Task 3: Band capacity as a hard cap, and the joint-unsatisfiability refusal that explains it** - `13acdc5` (feat)

## Files Created/Modified

- `src/main/java/com/wfm/solver/ScheduleConstraintProvider.java` - six constraints mode-gated via `ifExists`; `breakClustering`'s real body (`ClusterMark` record, tag-concat-regroup shape, `isOnBreak` helper); new `bandCapacity` hard constraint
- `src/main/java/com/wfm/model/ConstraintWeights.java` - `bandCapacityWeight` field, `@ConstraintWeight("Band capacity")`, hard by default
- `src/main/resources/db/migration/V42__add_band_capacity_weight.sql` - `constraint_weights.band_capacity_weight` column
- `src/main/java/com/wfm/service/SolverService.java` - `ShiftLibraryValidationService` dependency; `appendBandCapacityErrors` package-private static helper wired into `runPreSolveValidation`
- `src/test/java/com/wfm/solver/ScheduleConstraintClassification.java` - six rows reclassified `MODE_GATED` (Task 1), `Break clustering` reclassified (Task 2), new `Band capacity` row (Task 3)
- `src/test/java/com/wfm/solver/ScheduleConstraintClassificationTest.java` - replaced the two exact-set assertions that pinned `NEEDS_SHIFT_VARIANT`/`OPEN_RESOLVE_IN_PHASE_15` to their pre-Phase-15 values with empty-set assertions, plus a new nine-row `MODE_GATED` exact-set assertion
- `src/test/java/com/wfm/migration/MigrationEntityConsistencyTest.java` - `constraint_weights` added to `DECLARED_TABLES`; multi-clause `ALTER TABLE` parsing rewritten to fold every comma-separated `ADD COLUMN`/`DROP COLUMN` clause, not just the first
- `.planning/phases/14-shift-library-scheduling-mode/XCUT-05-constraint-classification.md` - full reconciliation with the Java map: six reclassified rows, the previously-unmirrored "Shift envelope compliance" row, "Break clustering"'s reclassification, and the new "Band capacity" row
- `src/test/java/com/wfm/solver/ShiftModeBreakGatingTest.java` - six isolated mode-gate scenarios (one per constraint) plus the ENVL-04 solved-fixture contiguity proof
- `src/test/java/com/wfm/solver/BreakClusteringConstraintTest.java` - the required single-band/two-band contrast fixture, the small-denominator case, SLOT-mode silence, ENVL-03 sanity
- `src/test/java/com/wfm/solver/BandCapacityConstraintTest.java` - exact-N/N+1/linear-excess, blank-unlimited, per-date scoping, independent bands, SLOT silence, null-pair non-error
- `src/test/java/com/wfm/service/SolverServiceBandCapacityRefusalTest.java` - the D-03 refusal wiring, message-identity with the advisory, multi-shortfall, SLOT no-op, no-shortfall no-op

## Decisions Made

- `ifExists(ScheduleConfig.class, filtering(...))` replaces the plan's literal `join(ScheduleConfig.class)` sketch for the four break constraints and `honourPreferredBreakTime` (all already Quad arity) and, for uniformity, `honourPreferredStartTime` too (which could have used a plain join at Bi→Tri, but the uniform pattern across all six was judged clearer than a mixed one) — Timefold 1.16.0 has no Penta constraint stream, so a literal join was never available for five of the six.
- The mode gate's polarity is `!= SchedulingMode.SHIFT`, not `== SchedulingMode.SLOT` — `Schedule.schedulingMode` is `@Transient` with no default and stays `null` in every hand-built test fixture that never calls `setSchedulingMode()` (including `BreakAwareConstructionTest`). `== SLOT` would have silently disabled all six constraints on those fixtures; `!= SHIFT` resolves `null` to "active", matching pre-Phase-15 behaviour exactly.
- `Break clustering`'s on-break count joins `Timeslot` directly rather than through `AgentAssignment`, because an on-break agent structurally has no seat at the timeslot their break covers — deriving "on break" from seat rows there would always see zero, including in the exact scenario (everyone breaks at once, zero seats exist) this requirement was written to catch.
- The small-denominator case (2 agents, one on break = 100% clustered) is asserted and left as-is, not "fixed" — a 2-agent desk cannot structurally stay under a 20% threshold once one agent breaks, and the resulting penalty is small, soft, and proportional, not a formula defect.
- `appendBandCapacityErrors` was extracted as a package-private static helper (mirroring `buildShiftBandPairs`/`buildShiftAssignments`'s established precedent) specifically so the D-03 refusal wiring could be unit-tested with a Mockito mock, without needing to construct a full `SolverService` or Spring context.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] The plan's literal `join(ScheduleConfig.class)` mode-gate sketch is impossible for five of the six Task 1 constraints — Timefold 1.16.0 has no Penta constraint stream**
- **Found during:** Task 1, first compile attempt after implementing the literal sketch
- **Issue:** `exactlyOneBreak`, `breakDuration`, `breakBlockedWindow`, `breakStartAlignment`, and `honourPreferredBreakTime` are already Quad-arity (4-tuple) streams by the point the mode filter needed to be inserted. Timefold's public Constraint Streams API (`ai.timefold.solver.core.api.score.stream`) defines only Uni/Bi/Tri/Quad stream types — there is no Penta stream, and `QuadConstraintStream` has no `.join(Class)` overload that returns a 5-tuple stream (confirmed by decompiling `QuadConstraintStream.class` from the pinned 1.16.0 jar). The literal plan instruction could not compile.
- **Fix:** Replaced the join+filter with `ifExists(ScheduleConfig.class, filtering(...))`, which stays at the stream's existing arity while achieving an identical existence-check gate against the `ScheduleConfig` singleton fact. Applied uniformly to all six gated constraints (including `honourPreferredStartTime`, which could have used a plain join at its lower arity, for pattern consistency).
- **Files modified:** `src/main/java/com/wfm/solver/ScheduleConstraintProvider.java`
- **Verification:** `./gradlew compileJava` succeeds; `ShiftModeBreakGatingTest` proves both directions (SHIFT=0, SLOT=unchanged) for all six.
- **Committed in:** `ffeb8cf` (Task 1 commit)

**2. [Rule 1 - Bug] `== SLOT` mode-gate polarity would have silently disabled all six constraints on every pre-Phase-15 test fixture**
- **Found during:** Task 1, while designing `ShiftModeBreakGatingTest`'s ENVL-04 solved-fixture check and cross-checking `BreakAwareConstructionTest`
- **Issue:** `Schedule.schedulingMode` is `@Transient` with no field default, so it is `null` in any `Schedule` built without an explicit `setSchedulingMode()` call — which is every hand-built fixture in `BreakAwareConstructionTest` and several other pre-existing solver tests. A gate written as `cfg.schedulingMode() == SchedulingMode.SLOT` would evaluate `null == SLOT` as `false`, incorrectly treating those fixtures as "not SLOT" and disabling the six constraints — silently changing `BreakAwareConstructionTest`'s behaviour, which the plan explicitly forbids ("must stay green and byte-identical").
- **Fix:** Used `!= SchedulingMode.SHIFT` instead, which resolves `null` to "active" (matching every pre-Phase-15 fixture's implicit SLOT-equivalent behaviour), mirroring the same null-safe convention `shiftEnvelopeCompliance` already established with its own `== SHIFT` gate (also null-safe, in the opposite direction).
- **Files modified:** `src/main/java/com/wfm/solver/ScheduleConstraintProvider.java`
- **Verification:** `git diff --quiet HEAD -- src/test/java/com/wfm/solver/BreakAwareConstructionTest.java` returns clean; the full suite (including `BreakAwareConstructionTest`) is green; `ShiftModeBreakGatingTest` explicitly exercises SLOT-mode fixtures that never call `setSchedulingMode`.
- **Committed in:** `ffeb8cf` (Task 1 commit)

**3. [Rule 1 - Bug] Timefold has no Bi-to-Bi constraint stream join, making the plan's "join the two [groupBy] streams on timeslot" sketch for `breakClustering` impossible as literally written**
- **Found during:** Task 2, implementing the combined assigned-count/on-break-count comparison
- **Issue:** Both "agents seated per timeslot" and "agents on break per timeslot" are naturally expressed as `BiConstraintStream<Timeslot, Integer>` via independent `groupBy` calls (from `AgentAssignment` and `AgentShiftAssignment` respectively). `BiConstraintStream.join(...)` only accepts a `UniConstraintStream` or a `Class` as the join partner — there is no overload accepting another `BiConstraintStream` (confirmed by decompiling `BiConstraintStream.class`).
- **Fix:** Tagged each count into a shared `ClusterMark(int assigned, int onBreak)` record via `.map(...)`, unioned the two tagged `BiConstraintStream<Timeslot, ClusterMark>`s with `BiConstraintStream.concat` (a genuine union operator Timefold does expose), then ran one final `groupBy` with two `sum` collectors to recombine both counts per timeslot.
- **Files modified:** `src/main/java/com/wfm/solver/ScheduleConstraintProvider.java`
- **Verification:** `BreakClusteringConstraintTest`'s five tests, including the required single-band/two-band contrast, all pass with hand-computed expected values matching on the first run.
- **Committed in:** `275a942` (Task 2 commit)

**4. [Rule 1 - Bug] `MigrationEntityConsistencyTest`'s multi-column `ALTER TABLE` regex silently dropped every column after the first in a comma-separated clause list**
- **Found during:** Task 3, adding `constraint_weights` to the reconciliation table (T-15-24)
- **Issue:** `ALTER_ADD_COLUMN`'s regex (`ALTER TABLE\s+(\w+)\s+ADD COLUMN\s+(\w+)\s+(type)`) matches only the clause immediately following the literal `ALTER TABLE` keyword. `V2__convert_score_columns_to_varchar.sql` declares fifteen `ADD COLUMN` clauses in one Postgres-style compound `ALTER TABLE constraint_weights ADD COLUMN a ..., ADD COLUMN b ..., ...;` statement — the old regex found only the first (`agent_day_off_weight`) and silently missed the other fourteen, including `spec_match_weight`. This bug was latent because no table populated via multi-column `ALTER TABLE` had ever been added to `DECLARED_TABLES` before `constraint_weights`.
- **Fix:** Rewrote the parser to first match the whole `ALTER TABLE <table> <clauses>;` statement (`ALTER_TABLE_STATEMENT`), then split the captured clause list on top-level commas (reusing the existing `splitTopLevelCommaList` helper) and classify each clause as `ADD COLUMN`/`DROP COLUMN`/other (ignored). Applied the same multi-column-aware parsing to `DROP COLUMN` for consistency (the old `ALTER_DROP_COLUMN` regex had the identical latent limitation).
- **Files modified:** `src/test/java/com/wfm/migration/MigrationEntityConsistencyTest.java`
- **Verification:** `migrationDeclaredColumns_reconcileWithEntityMappings` passes with `constraint_weights` in `DECLARED_TABLES`; `v40DroppedColumns_absentFromMigrationMapAndEntity` (unrelated `shift_template` assertions) stays green, unaffected.
- **Committed in:** `13acdc5` (Task 3 commit)

**5. [Rule 3 - Blocking] `ScheduleConstraintClassificationTest`'s two exact-set assertions pinned to the pre-Phase-15 `NEEDS_SHIFT_VARIANT`/`OPEN_RESOLVE_IN_PHASE_15` sets would fail the moment Task 1 reclassified those six rows**
- **Found during:** Task 1, immediately after reclassifying the six rows in `ScheduleConstraintClassification`
- **Issue:** `exactlyTheFourNamedBreakConstraintsCarryNeedsShiftVariant` and `exactlyThePreferenceConstraintsCarryOpenResolveInPhase15WithANamedOwner` (Phase 14 tests) asserted the classification map contained exactly those six rows under their pre-Phase-15 tags — assertions that become definitionally false the instant this plan's own stated goal (reclassify them to `MODE_GATED`) is achieved. Not fixing this would leave the completeness-test suite red on a correct implementation.
- **Fix:** Replaced both tests with `needsShiftVariantIsEmptyAfterPhase15` and `zeroConstraintsRemainOpenResolveInPhase15` (asserting the sets are now empty, still derived from the enum reflectively, not hardcoded), and added a new `thePhase15ModeGatedSetIsExactlyTheNineExpectedRows` test asserting the full final `MODE_GATED` set and that `PHASE_15_OWNER` is retained verbatim on the two preference rows (P-27).
- **Files modified:** `src/test/java/com/wfm/solver/ScheduleConstraintClassificationTest.java` (not listed in the plan's `files_modified`, but required by the plan's own explicit instruction to reclassify these rows and its `must_haves.truths` claim of zero `OPEN_RESOLVE_IN_PHASE_15` rows)
- **Verification:** `./gradlew test --tests ScheduleConstraintClassificationTest` green (6/6, including the two replaced and one new test)
- **Committed in:** `ffeb8cf` (Task 1 commit)

---

**Total deviations:** 5 auto-fixed (4 Rule 1 bugs — all Timefold 1.16.0 API-limitation discoveries the plan's own sketches could not have anticipated without the direct decompilation this session did, plus one null-safety correctness bug; 1 Rule 3 blocking fix to a pre-existing test file the plan's own goal necessarily invalidated).
**Impact on plan:** Every deviation was necessary to make the plan's own stated intent compile and behave correctly; none changed the plan's scope or introduced functionality beyond what was asked. The two most consequential (API-limitation substitutions) are documented in the affected constraints' own javadoc so future readers understand why the code doesn't literally match the plan's sketch.

## Known Stubs

None — every constraint shipped in this plan has a real, tested body. No placeholders remain in scope (`breakClustering`'s pre-existing placeholder is exactly what this plan replaces).

## Threat Flags

None beyond what the plan's own `<threat_model>` already named and mitigated (T-15-21 through T-15-25, T-15-SC) — no new network endpoints, auth paths, or trust-boundary-crossing file access was introduced by this plan; all changes are internal solver/service logic and a schema migration reconciled by `MigrationEntityConsistencyTest`.

## Issues Encountered

- The first full-suite run (`./gradlew cleanTest test`) failed with `Could not write XML test results` for ~40 test classes — a Gradle test-report I/O error, not a test-logic failure (grep confirmed zero individual test method `FAILED` lines in that run's output). A second run (`./gradlew test`, no `clean`) completed `BUILD SUCCESSFUL` in 7m 57s with all 77 test-result XML files reporting `failures="0" errors="0"`. Most likely transient contention with the concurrently-running sibling plan 15-07's own gradle test process on the same machine; not a defect in this plan's code.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- `ScheduleConstraintClassification`/`XCUT-05-constraint-classification.md` are fully reconciled and complete (21/21, zero open) — no further classification work is owed to any later phase.
- `Break clustering`'s real body and `Band capacity`'s hard cap are both mode-gated the same way `shiftEnvelopeCompliance` is, so plan 15-08's CH-ordering benchmark measures a fully-realized shift-mode constraint set, not a partially-stubbed one.
- The `ifExists`-not-`join` and `concat`-not-Bi-join mechanism notes in `ScheduleConstraintProvider.java`'s javadoc are reusable reference points for any future constraint that needs a mode gate on an already-Quad stream, or a combined aggregate from two different source entities.
- `SolverService.appendBandCapacityErrors` is package-private and directly testable — a template for extracting any future `runPreSolveValidation` addition into an independently unit-testable helper.

## Self-Check: PASSED

- All created files verified present on disk: `ShiftModeBreakGatingTest.java`, `BreakClusteringConstraintTest.java`, `BandCapacityConstraintTest.java`, `SolverServiceBandCapacityRefusalTest.java`, `V42__add_band_capacity_weight.sql`.
- All three task commits verified present in `git log`: `ffeb8cf`, `275a942`, `13acdc5`.
- Plan `<verification>` re-run: `./gradlew test` green (77 test classes, 0 failures/errors after one transient-I/O retry); `git diff --quiet HEAD -- src/test/java/com/wfm/solver/BreakAwareConstructionTest.java` clean; `ScheduleConstraintClassificationTest` green with zero `OPEN_RESOLVE_IN_PHASE_15`/`NEEDS_SHIFT_VARIANT` rows; the classification constant and its markdown mirror moved together in each task commit; `BreakClusteringConstraintTest`'s contrast fixture reports both the penalty (4 vs 2) and the staffing shortfall (0/2 vs 2/2) in its assertion messages.
- `BreakAwareConstructionTest`'s 30-agent slot-mode scenario confirmed printing `assigned=480/480, score=0hard/0soft` in the final full-suite run's XML output — no reintroduction of the `shiftEnvelopeCompliance` performance regression.

---
*Phase: 15-shift-envelope-breaks-library-generation*
*Completed: 2026-08-27*
