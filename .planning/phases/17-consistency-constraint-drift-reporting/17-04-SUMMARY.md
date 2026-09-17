---
phase: 17-consistency-constraint-drift-reporting
plan: 04
subsystem: solver-config
tags: [timefold, flyway, postgres, constraint-weights, benchmark, xcut-04]

# Dependency graph
requires:
  - phase: 17-01
    provides: "ConstraintWeights.consistentStartWeight field, ShiftBandPair.startDeviationMinutes, consistencyToleranceMinutes"
  - phase: 17-02
    provides: "ConstraintWeightsService's D-07/D-08 save-time validation, 'Usual shift consistency' / 'Preferred start (shift mode)' explain() constraint names"
provides:
  - "V49 migration shipping the human-decided consistentStartWeight=2/preferredStartShiftModeWeight=1 defaults, predicated so no operator-tuned row is overwritten"
  - "17-BENCHMARK.md: threshold-first XCUT-04 record with seeded results, redone per-agent-day sizing arithmetic, and a documented residual worst-case risk"
  - "Gated UsualShiftConsistencyBenchmarkTest harness (never runs in default suite)"
  - "ConstraintWeightsMigrationTest proving V49's column defaults and its predicated-UPDATE safety against a real Postgres/Flyway chain"
affects: [17-05, constraint-weights-page, drift-report]

# Actuals (#2632)
actuals:
  tokens: 18100
  tasks: 3
  commits: 4

tech-stack:
  added: []
  patterns:
    - "Migration-comment-as-audit-trail: the corrective note for a prior migration's now-stale sizing arithmetic lives in the NEW migration's comment, since Flyway is forward-only and the old comment cannot be edited"
    - "Predicated UPDATE on a prior shipped-default value (T-17-06) as the standard shape for changing a per-desk default without touching operator-tuned rows"
    - "Re-executing a migration's literal UPDATE SQL inside a PostgresBackedTest fixture to prove predicate safety, when the migration itself runs once at container bootstrap before test fixtures exist"

key-files:
  created:
    - .planning/phases/17-consistency-constraint-drift-reporting/17-BENCHMARK.md
    - src/test/java/com/wfm/solver/UsualShiftConsistencyBenchmarkTest.java
    - src/main/resources/db/migration/V49__set_consistency_weight_defaults.sql
    - src/test/java/com/wfm/model/ConstraintWeightsMigrationTest.java
  modified: []

key-decisions:
  - "Human checkpoint decision: 'proposed' — consistentStartWeight=2 soft, preferredStartShiftModeWeight=1 soft. Identical to what V38/V48 already ship; V49's UPDATE confirms incumbent values rather than changing behaviour."
  - "consistentStartWeight=2 is the smallest positive integer that can carry a nonzero, strictly-lower preferredStartShiftModeWeight under D-08's ordering invariant — weight 1 clears the 1000-soft ceiling alone but leaves no positive integer below it."
  - "Worst-case projected total (entire 28-agent roster drifting every one of 5 working days) is ~1,120 soft, 12% over minStaffingWeight's 1000-soft ceiling — accepted as a documented residual risk, not eliminated, because no compliant weight pair avoids it under D-08."
  - "The A/B benchmark itself produced no measurable difference between weight arms (a construction-heuristic plateau, ruled out as a step-budget or list-order artifact at 20,000 steps / 6.7x the committed budget) — written up honestly as a null result per the committed pass rule, never as a win. The shipped weight is justified by the redone per-agent-day arithmetic and the explain() breakdown, not by the A/B."
  - "No production telemetry exists on actual drift rates; the ~20%-of-roster 'typical case' figure is an explicit stated judgement, not a measurement, and is presented as such in both 17-BENCHMARK.md and the V49 migration comment."

requirements-completed: [CONS-02, CONS-03, XCUT-04]

coverage:
  - id: D1
    description: "Threshold-first XCUT-04 benchmark record (17-BENCHMARK.md) with the threshold committed to git before any result, and a gated seeded A/B harness (UsualShiftConsistencyBenchmarkTest) that never runs in the default suite"
    requirement: XCUT-04
    verification:
      - kind: integration
        ref: "git log --oneline -- 17-BENCHMARK.md (threshold commit f4c90e2 precedes harness/result commits)"
        status: pass
      - kind: unit
        ref: "com.wfm.solver.UsualShiftConsistencyBenchmarkTest (gated, skipped=2 in default suite; executed and printed both A/B table and explain() breakdown under -Dwfm.benchmark=true)"
        status: pass
    human_judgment: false
  - id: D2
    description: "Benchmark results transcribed verbatim (median + full min/max spread), the construction-heuristic plateau honestly named as a null result, and V38's per-agent sizing arithmetic redone for D-02's per-agent-day model with two candidate default pairs"
    requirement: CONS-02
    verification: []
    human_judgment: true
    rationale: "Whether the arithmetic is sound, whether the null-result write-up honestly applies the pre-committed pass rule, and whether the two candidate proposals are fairly stated are judgment calls no automated check can certify — the automated check (grep for 'min/max') only proves the spread was transcribed, not that the reasoning is correct."
  - id: D3
    description: "Human checkpoint decision recorded and honoured: 'proposed' pair (2/1) selected, with the rationale (D-08 minimality, incumbent-value safety, documented worst-case residual risk) carried into both 17-BENCHMARK.md and the V49 migration comment"
    requirement: CONS-02
    verification: []
    human_judgment: true
    rationale: "A blocking-human checkpoint decision is inherently a human judgment call, not something automation proves — recorded here as the resolution, not re-litigated."
  - id: D4
    description: "V49 migration ships the chosen defaults via ALTER COLUMN ... SET DEFAULT plus a predicated UPDATE (T-17-06) that leaves any operator-tuned row untouched; ConstraintWeights.java's field initialisers already matched (no change needed); proven against a real Postgres/Flyway chain"
    requirement: CONS-03
    verification:
      - kind: integration
        ref: "com.wfm.model.ConstraintWeightsMigrationTest#columnDefaults_matchTheChosenPair"
        status: pass
      - kind: integration
        ref: "com.wfm.model.ConstraintWeightsMigrationTest#predicatedUpdate_confirmsShippedDefaultRow_leavesOperatorTunedRowUntouched"
        status: pass
      - kind: integration
        ref: "com.wfm.repository.AgentRepositoryPostgresTest, com.wfm.repository.AgentUsualShiftPostgresTest, com.wfm.service.ConstraintWeightsServiceTest (all green under the same real-migration run)"
        status: pass
      - kind: unit
        ref: "./gradlew test (full suite): 784 tests, 0 failures, 0 errors, 4 skipped (2 benchmark, gated as designed)"
        status: pass
    human_judgment: false

duration: "~46min across two sessions (Tasks 1-2 in a prior session, human checkpoint decision, Task 3 in this continuation)"
completed: 2026-09-17
status: complete
---

# Phase 17 Plan 4: Consistency Weight Benchmark & V49 Defaults Summary

**Benchmark-gated V49 migration shipping `consistentStartWeight=2`/`preferredStartShiftModeWeight=1` — identical to the incumbent values, now backed by redone per-agent-day arithmetic, an honestly-reported null-result A/B, and a documented 12%-over-ceiling worst-case risk instead of V38's unexamined per-agent sizing.**

## Performance

- **Duration:** ~46 min across two sessions (Tasks 1-2 prior session + checkpoint wait; Task 3 this continuation ~25 min)
- **Started:** 2026-09-17T20:43:02Z (first commit, f4c90e2)
- **Completed:** 2026-09-17T21:40:40Z
- **Tasks:** 3 (plus 1 blocking-human checkpoint, now resolved)
- **Files modified:** 4 created, 0 modified

## Accomplishments
- Committed `17-BENCHMARK.md`'s threshold, pass rule, and ceiling — before any benchmark result existed — per XCUT-04's git-history-as-evidence discipline.
- Built `UsualShiftConsistencyBenchmarkTest`, a gated seeded A/B harness (5 seeds x 4 weight arms) producing both a coverage-metrics table and a `SolutionManager.explain()` constraint-match breakdown in one run.
- Ran the harness, transcribed every number verbatim, and honestly reported a construction-heuristic plateau that made the solve-driven "no worse than baseline" check trivially true rather than a genuine signal — named as a null result, not a win, per the Phase 12 lesson the committed pass rule exists to prevent.
- Redid V38's per-agent sizing arithmetic for D-02's per-agent-day charging model: worst case `28 agents x 5 days x 4 increments x weight = 560 x weight` soft, landing at 1,120 soft (12% over the 1000-soft ceiling) at weight 2 — the exact trap this plan existed to catch.
- Presented two candidate default pairs to a human checkpoint; the human selected "proposed" (`2`/`1`), reasoned on D-08's minimality (no smaller weight can satisfy the ordering invariant) and on the values being identical to what's already live.
- Shipped `V49__set_consistency_weight_defaults.sql`: `ALTER COLUMN ... SET DEFAULT` for both columns plus a predicated `UPDATE` (T-17-06) that only touches rows still holding the prior shipped default, leaving any operator-tuned row untouched.
- Confirmed `ConstraintWeights.java`'s field initialisers already equalled the chosen values (`ofSoft(2)` / `ofSoft(1)`) — no entity change was needed.
- Added `ConstraintWeightsMigrationTest`, extending `PostgresBackedTest`, proving both the DB-level column defaults and the predicated-UPDATE's untouched-row guarantee against a real Postgres/Flyway chain.

## Task Commits

Each task was committed atomically (Tasks 1-2 committed by the prior executor session; Task 3 by this continuation):

1. **Task 1a: Commit the threshold before any result** - `f4c90e2` (docs)
2. **Task 1b: Build the gated harness** - `ae0653f` (test)
3. **Task 2: Transcribe results honestly, redo the arithmetic** - `ea0b625` (docs)
4. **Task 3: Write the chosen defaults as V49** - `31fc5e7` (feat)

**Plan metadata:** commit pending (this SUMMARY + STATE.md/ROADMAP.md/REQUIREMENTS.md)

## Files Created/Modified
- `.planning/phases/17-consistency-constraint-drift-reporting/17-BENCHMARK.md` - Threshold-first benchmark record: harness config, pass rule, per-run results, plateau finding, redone sizing arithmetic, two candidate proposals
- `src/test/java/com/wfm/solver/UsualShiftConsistencyBenchmarkTest.java` - Gated seeded A/B harness (5 seeds x 4 weight arms), never runs in the default suite
- `src/main/resources/db/migration/V49__set_consistency_weight_defaults.sql` - Ships the chosen defaults; predicated UPDATE per T-17-06; comment carries the full re-derivation and cites 17-BENCHMARK.md
- `src/test/java/com/wfm/model/ConstraintWeightsMigrationTest.java` - Proves V49's column defaults and untouched-row guarantee against real Postgres/Flyway

## Decisions Made
- **Checkpoint decision "proposed":** `consistentStartWeight=2`, `preferredStartShiftModeWeight=1`. Rationale: (1) the A/B produced no measurable difference — ruled out as a construction-heuristic plateau, not a step-budget or list-order artifact, so the shipped weight rests on the redone arithmetic and the `explain()` validation, not the A/B; (2) D-08 requires the preference weight strictly below and nonzero, so `2` is the smallest consistency value that can satisfy that ordering at all; (3) both values are identical to what V38/V48 already ship, so V49's UPDATE confirms incumbent behaviour rather than changing it — the property that makes the write safe against live tenant rows; (4) the worst-case total (~1,120 soft, 12% over the 1000-soft ceiling, entire roster drifting every day) is accepted as a documented residual risk since no compliant pair avoids it, while the illustrative typical-case total (~240 soft) is comfortably safe and explicitly labelled a judgement, not a measurement, since no production drift telemetry exists yet.
- **No change to `ConstraintWeights.java` was needed** — plan 17-01/17-02 had already set the field initialisers to the values this checkpoint independently arrived at (`ofSoft(2)` / `ofSoft(1)`). Verified by inspection rather than edited.

## Deviations from Plan

None - plan executed exactly as written, including the human-selected checkpoint option.

## Issues Encountered

None. Docker was not running at the start of this continuation session (required for the `PostgresBackedTest`-based verification); started it and waited for it to become available before running the migration test, per the plan's explicit requirement that a Docker-absent skip must not be mistaken for a pass.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Plan 17-05 is next; it shares requirement IDs CONS-02, CONS-03, CONS-06 with earlier plans in this phase — those requirement checkboxes remain gated until 17-05 also completes (per the shared-ID readiness rule), even though this plan's own work on them is done.
- V49 is a one-way migration already written to be safe against live tenant rows (values identical to incumbent, predicated UPDATE) — no rollback path exists or is needed since no behaviour changes.
- The construction-heuristic plateau this benchmark surfaced (shift-template-choice dimension) is a new instance of an already-named, already-accepted class of limitation (15-BENCHMARK.md's plateau finding) — not remediated here, consistent with this milestone's operator ruling to measure and report, not build a custom move.

---
*Phase: 17-consistency-constraint-drift-reporting*
*Completed: 2026-09-17*

## Self-Check: PASSED

- All 4 created files confirmed present on disk (17-BENCHMARK.md, UsualShiftConsistencyBenchmarkTest.java, V49__set_consistency_weight_defaults.sql, ConstraintWeightsMigrationTest.java).
- All 5 commits confirmed in git history (f4c90e2, ae0653f, ea0b625, 31fc5e7, e2e3361).
- Task 3's plan-level `<verify>` command re-run and green: 2/2 tests in ConstraintWeightsMigrationTest, plus AgentRepositoryPostgresTest, AgentUsualShiftPostgresTest, ConstraintWeightsServiceTest — none skipped.
- Full `./gradlew test` re-confirmed green: 784 tests, 0 failures, 0 errors, 4 skipped (2 of which are the benchmark's own designed gate).
