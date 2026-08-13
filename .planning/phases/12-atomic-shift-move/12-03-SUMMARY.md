---
phase: 12-atomic-shift-move
plan: 03
subsystem: solver
tags: [timefold, benchmark, custom-move, statistical-evidence]

requires:
  - phase: 12-atomic-shift-move (plans 01-02)
    provides: AtomicShiftMoveFactory, AssignSeatMove, ShiftWindowFinder (the move under test)
provides:
  - Seeded, step-count-terminated 5x5 A/B benchmark harness (AtomicShiftMoveBenchmarkTest)
  - 12-BENCHMARK.md evidence record with operator-signed threshold verdicts
  - Cross-agent seat displacement follow-up filed as a pending todo
affects: [12-atomic-shift-move (phase closure), any future phase touching AtomicShiftMoveFactory]

actuals:
  tokens: 16050
  tasks: 3
  commits: 5

tech-stack:
  added: []
  patterns:
    - "Seeded + step-count-terminated benchmark harness gated behind a JUnit @EnabledIfSystemProperty flag, kept out of the default suite, for statistically meaningful A/B comparison under a solver whose production termination is wall-clock and therefore non-reproducible run-to-run"
    - "Median and full min/max spread reported instead of a mean when the underlying distribution is bimodal"

key-files:
  created:
    - src/test/java/com/wfm/solver/AtomicShiftMoveBenchmarkTest.java
  modified:
    - build.gradle
    - .planning/phases/12-atomic-shift-move/12-BENCHMARK.md
    - .planning/STATE.md
    - .planning/ROADMAP.md

key-decisions:
  - "Operator ruling accepted verbatim: the atomic shift move is kept as committed (correct, improves hard score, breaks nothing) but the phase's must-pass median-vs-spread threshold is recorded as FAILED, and the phase goal of 'agents reach more contracted hours' is NOT claimed as achieved by this phase"
  - "Threshold 5 (should-pass, non-blocking) resolved in favour of the within-run reading (1 of 5 with-move runs worse than this harness's own best baseline) rather than the literal historical -4,930 reading, because the historical figure was wall-clock-terminated and unseeded and 12-RESEARCH.md's own root-cause finding is that termination mode is not run-to-run reproducible"
  - "Cross-agent seat displacement filed as a standalone follow-up item (not folded into this phase), scoped from the 130% conservative-variant finding that seat capacity, not move selection, is the binding constraint at realistic over-allocation"

requirements-completed: []

coverage:
  - id: D1
    description: "Seeded, step-count-terminated 5x5 benchmark harness comparing baseline vs. with-move configurations, gated out of the default test suite"
    verification:
      - kind: unit
        ref: "./gradlew test --tests com.wfm.solver.AtomicShiftMoveBenchmarkTest -Dwfm.benchmark=true"
        status: pass
    human_judgment: false
  - id: D2
    description: "5x5 benchmark evidence recorded in 12-BENCHMARK.md with per-threshold PASS/FAIL/INCONCLUSIVE markers against 12-VALIDATION.md"
    verification: []
    human_judgment: true
    rationale: "Median-vs-spread and hard-score-regression thresholds are marked verification: backstop in the plan's must_haves — they require a judgement call across a bimodal 5-run sample that no deterministic assertion can settle. This is exactly what the Task 3 checkpoint exists for."
  - id: D3
    description: "Operator sign-off recorded: threshold 1 (must-pass) is FAILED, the phase goal is not claimed as achieved, and cross-agent seat displacement is filed as follow-up"
    verification: []
    human_judgment: true
    rationale: "This deliverable IS the human verdict — by construction it cannot be machine-verified."

duration: "~25 min (continuation agent, Tasks 3-6 only; Tasks 1-2 by prior executor at ~55 min)"
completed: 2026-08-13
status: complete
---

# Phase 12 Plan 03: Benchmark Evidence & Operator Sign-Off Summary

**Seeded 5x5 A/B benchmark shows the atomic shift move's effect on hours assigned (0.25h) lands
inside the baseline's own run-to-run noise (5.00h spread) — the phase's must-pass threshold FAILS,
and the operator has explicitly ruled the phase goal is NOT achieved, while keeping the move
itself as correct, committed code.**

## Performance

- **Duration:** ~25 min (this continuation) + ~55 min (Tasks 1-2, prior executor)
- **Tasks:** 3 of 3 (Tasks 1-2 executed and committed by a prior executor; Task 3 checkpoint
  resolved by this continuation agent)
- **Files modified:** 5 (1 test class + build.gradle from Tasks 1-2; 12-BENCHMARK.md, STATE.md,
  ROADMAP.md updated by this continuation)

## Accomplishments

- Built `AtomicShiftMoveBenchmarkTest`: ten seeded, step-count-terminated solves (5 baseline,
  5 with-move) on a checked-in fixture reproducing the ROADMAP Phase 12 scenario, gated behind
  `-Dwfm.benchmark=true` so it never runs in the default suite. Asserts the one deterministic
  must-pass threshold in code (zero agent-days needing a break with none, all five with-move runs).
- Recorded the full 5x5 evidence — per-run table, per-configuration median/min/max summary, a
  threshold-by-threshold PASS/FAIL/INCONCLUSIVE assessment, an informational 130%-over-allocation
  conservative variant, and historical pre-fix context — in `12-BENCHMARK.md`.
- **Operator verdict applied:** the must-pass median-vs-spread threshold (with-move median 79.50h
  vs. baseline median 79.25h, a 0.25h margin against a 5.00h baseline spread) is **FAILED**. The
  phase goal ("agents reach more contracted hours") is explicitly **not** claimed as achieved. The
  move itself is kept — it is correct, improves hard score, composes with change/swap, and does
  not corrupt the incremental score — but the phase does not close as complete on a failed
  must-pass.
- The 130% conservative-variant data (identical 36.0h hours-assigned across all 10 runs, both
  configurations) shows the move produces **no measurable benefit** at realistic over-allocation
  because seat capacity, not move selection, is the binding constraint — this is the evidence base
  for the filed follow-up.
- Filed `.planning/todos/pending/2026-08-13-cross-agent-seat-displacement.md`
  (`resolves_phase: 12`) so the deferred cross-agent seat displacement capability — the move can
  currently only target free-or-self-held seats, never displace another agent — is not lost.

## Task Commits

Each task was committed atomically:

1. **Task 1: Seeded, step-count benchmark harness comparing baseline against the new move** -
   `0fff67a` (feat)
2. **Task 2: Record the 5x5 benchmark evidence in 12-BENCHMARK.md** - `ff5bfa5` (docs)
   *(interim state bookkeeping at the operator checkpoint pause: `3a04199`, docs)*
3. **Task 3: Operator sign-off on the benchmark evidence** - resolved by this continuation agent;
   verdict recorded in `12-BENCHMARK.md`, this SUMMARY, and the follow-up todo (see commits below)

**Plan metadata:** (this continuation's commits, in order)

## Files Created/Modified

- `src/test/java/com/wfm/solver/AtomicShiftMoveBenchmarkTest.java` - Ten-run seeded A/B benchmark
  harness, gated out of the default suite (Task 1)
- `build.gradle` - `wfm.benchmark` system-property passthrough for the test JVM (Task 1)
- `.planning/phases/12-atomic-shift-move/12-BENCHMARK.md` - 5x5 evidence, threshold assessment,
  130% conservative variant, historical context, and the operator verdict section (Tasks 2-3)
- `.planning/todos/pending/2026-08-13-cross-agent-seat-displacement.md` - Follow-up item scoped
  from the 130% finding (Task 3)
- `.planning/STATE.md` - Checkpoint resolution and decision recorded (Task 3)
- `.planning/ROADMAP.md` - Plan progress updated for 12-03 (Task 3; phase-level completion status
  intentionally left untouched per the operator's ruling)

## Decisions Made

- **Operator verdict (2026-08-13), applied verbatim:** "Keep the code, but do not claim the phase
  goal." The move is correct and kept; threshold 1 (median-vs-spread) is recorded as FAILED rather
  than the phase goal being marked achieved; a follow-up is opened for cross-agent seat
  displacement; the phase does not get marked complete on a failed must-pass.
- Threshold 5 (should-pass, non-blocking, hard-score regression vs. the historical `-4,930`)
  resolved in favour of the within-run reading (1 of 5) rather than the literal historical reading
  (5 of 5), because the historical figure is wall-clock-terminated/unseeded and not comparable to
  a step-count-terminated run — both readings are recorded in `12-BENCHMARK.md` without softening
  either.
- Cross-agent seat displacement filed as a standalone pending todo (not a new phase, not folded
  into this phase) so it survives to be picked up via `/gsd-discuss-phase` once scoped.

## Deviations from Plan

None - plan executed exactly as written. Task 3 required an operator decision by design (marked
`type="checkpoint:human-verify"` with `verification: backstop` must-haves in the plan); the
decision itself — recording a FAIL rather than manufacturing a pass — is the plan working as
intended, not a deviation from it.

## Issues Encountered

None beyond what the plan anticipated. The benchmark data itself is the "issue" the plan was
designed to surface: a real but small effect (0.25h) that does not clear the noise floor
(5.00h spread) at the reference 400% over-allocation scenario, and no effect at all at realistic
130% over-allocation. Both are now explicitly documented rather than hidden behind a single green
solve.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Phase 12's atomic shift move code is complete, correct, and committed (12-01, 12-02, 12-03), but
  the phase's own success threshold for "more hours assigned" is **not met** and is not claimed as
  met. The orchestrator/operator should NOT mark Phase 12 complete against its original goal
  without either (a) accepting the operator's explicit "keep code, defer goal" ruling as the
  phase's final disposition, or (b) scoping and executing the cross-agent seat displacement
  follow-up first.
- The cross-agent seat displacement follow-up is filed and ready to route through
  `/gsd-discuss-phase` when prioritized — it targets the actual binding constraint the 130% data
  identified (seat capacity / displacement), not a defect in the existing move.
- v1.2 Phases 9-11 (Agent Data Model Foundation, Enriched Upload Parsing, BambooHR Merge Engine)
  remain the next scheduled work per `STATE.md`'s Operator Next Steps, independent of this
  follow-up.

## Self-Check

- FOUND: `src/test/java/com/wfm/solver/AtomicShiftMoveBenchmarkTest.java`
- FOUND: `.planning/phases/12-atomic-shift-move/12-BENCHMARK.md`
- FOUND: `.planning/todos/pending/2026-08-13-cross-agent-seat-displacement.md`
- FOUND: commit `0fff67a` (feat(12-03): seeded step-count A/B benchmark harness for the atomic shift move)
- FOUND: commit `ff5bfa5` (docs(12-03): record 5x5 benchmark evidence in 12-BENCHMARK.md)
- FOUND: commit `3a04199` (docs(12-03): record operator-checkpoint blocker and session position)

---
*Phase: 12-atomic-shift-move*
*Completed: 2026-08-13*
