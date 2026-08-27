---
phase: 15-shift-envelope-breaks-library-generation
plan: 08
subsystem: testing
tags: [timefold, benchmark, xcut-04, construction-heuristic, solver-config]

# Dependency graph
requires:
  - phase: 15-04
    provides: ShiftModeFixtures (reusable deterministic shift-mode/slot-mode Schedule builder)
  - phase: 15-06
    provides: the completed mode-gated constraint set (Break clustering, Band capacity, shiftEnvelopeCompliance) the shift arm actually measures
provides:
  - ShiftModelBenchmarkTest — seeded, step-count-terminated A/B benchmark (slot vs shift-shifts-first vs shift-seats-first), gated behind -Dwfm.benchmark=true
  - 15-BENCHMARK.md — the XCUT-04 pass rule committed before any result, the recorded results, a PASS verdict, the soft-score plateau finding, the D-08 CH-ordering decision (no measurable difference, shifts-first ships unchanged), and a piloting recommendation
  - Restored build.gradle wfm.benchmark system-property passthrough (removed alongside the Phase 12 harness at 299c42c)
affects: [17-consistency-and-drift-report]

actuals:
  tokens: 11184
  tasks: 3
  commits: 4

tech-stack:
  added: []
  patterns:
    - "Reordering a loaded SolverConfig's phase list in place (Collections.swap) to build a construction-heuristic-ordering A/B arm, rather than maintaining a second XML file that could drift from the shipped solverConfig.xml — verified defensively via an entity-class introspection assertion (isConstructionHeuristicForEntity) that fails loudly if solverConfig.xml's phase order ever changes"
    - "TimeslotKey(date, start) record as the map key for demand-vs-filled accounting, instead of relying on Timeslot object identity/equality across a Timefold solution clone — avoids any assumption about whether problem-fact references survive planning-clone unchanged"

key-files:
  created:
    - src/test/java/com/wfm/solver/ShiftModelBenchmarkTest.java
  modified:
    - build.gradle
    - .planning/phases/15-shift-envelope-breaks-library-generation/15-BENCHMARK.md

key-decisions:
  - "The comparative fixture's seat-to-demand ratio is 1:1 (agentCount seats per timeslot, matching agentCount demand) rather than literal seat headroom above demand as in 12-BENCHMARK.md's terminology — inherited unmodified from ShiftModeFixtures per the plan's own reuse mandate (P-19); ~130% over-allocation is satisfied via ShiftModeFixtures.OVERALLOCATION_PCT (the AgentDayConfig hour-limit field), not via extra seats. Documented as a scale caveat in the piloting recommendation rather than silently assumed equivalent to Phase 12's seat-headroom framing."
  - "D-16's 'real-desk' run reads no real tenant data — it is the same synthetic ShiftModeFixtures builder scaled to agentCount=30, matching the real-desk SCALE SPIKE-COUPLING.md's open item 1 names, not a literal production-data pull. Documented explicitly in 15-BENCHMARK.md to close T-15-32 by construction (no real data touched at all, a stronger mitigation than filtering PII after the fact)."
  - "The noise rule was applied literally even where it produced a counterintuitive result: the shift arm's unstaffed-demand-slots median (0.0) and hours-assigned median (64.0h) beat the slot arm's medians (28.0, 55.5h) by margins SMALLER than the slot arm's own min/max spread (43, 12.75h) — so both comparative deltas are written up as 'no measurable difference' on magnitude, per D-15, even though they look like a clear win at a glance. The verdict is still PASS, driven instead by the must-pass criterion (a discrete 5/5-vs-1/5 convergence-reliability difference, not subject to the noise rule)."
  - "A supplementary ad-hoc 20,000-step sweep (20x the committed 1,000-step budget) was run outside the committed suite to check whether the slot arm's convergence gap was a step-budget artifact. It was not: 3 of 4 previously-failing seeds remained at essentially unchanged scores at 20x budget, confirming a genuine local-search plateau. Reported in 15-BENCHMARK.md, clearly separated from the committed-test numbers, per this plan's runtime-budget guidance."

patterns-established:
  - "Ad-hoc heavier sweeps used to interpret a committed benchmark's result must be run outside the committed test (keeping suite runtime bounded) and reported in the benchmark document with an explicit label distinguishing them from the committed numbers used against the pass rule."

requirements-completed: [XCUT-04]

coverage:
  - id: D1
    description: "A seeded, step-count-terminated A/B benchmark with a pre-committed threshold (committed in a commit that precedes any result) measures the shift model against the slot model at ~130% over-allocation, reporting median and full min/max spread for every metric, no mean anywhere"
    requirement: "XCUT-04"
    verification:
      - kind: integration
        ref: "./gradlew test --tests com.wfm.solver.ShiftModelBenchmarkTest -Dwfm.benchmark=true"
        status: pass
      - kind: manual_procedural
        ref: "15-BENCHMARK.md Pass Rule section committed at cd26db9, Results section committed at 836033a (later commit) — git log order verified"
        status: pass
    human_judgment: false
  - id: D2
    description: "The soft-score plateau is reported as a named finding, explicitly not thresholded, with its mechanism stated and the reason the two arms' soft totals are incommensurable"
    requirement: "XCUT-04"
    verification:
      - kind: manual_procedural
        ref: "15-BENCHMARK.md § Plateau Finding"
        status: pass
    human_judgment: false
  - id: D3
    description: "The D-08 CH-ordering question is answered by measurement (shifts-first vs seats-first run as separate arms on the same seeds/termination) and the winner ships in solverConfig.xml, or the file is explicitly left unchanged with the reason stated"
    requirement: "XCUT-04"
    verification:
      - kind: integration
        ref: "./gradlew test (full suite, SolverConfigBuildTest green against the unmodified solverConfig.xml)"
        status: pass
      - kind: manual_procedural
        ref: "15-BENCHMARK.md § CH-Ordering Decision — no measurable difference, shifts-first ships unchanged"
        status: pass
    human_judgment: false
  - id: D4
    description: "The verdict gates piloting, not phase completion — a FAIL would not withdraw the ENVL-01..10 correctness deliverable, which ShiftEnvelopeGroundTruthTest verifies independently of any score in this benchmark"
    requirement: "XCUT-04"
    verification:
      - kind: manual_procedural
        ref: "15-BENCHMARK.md § What This Verdict Gates (D-13)"
        status: pass
    human_judgment: false
  - id: D5
    description: "Full project test suite stays green with the benchmark skipped by default"
    verification:
      - kind: integration
        ref: "./gradlew test (79 test classes, 0 failures/errors)"
        status: pass
    human_judgment: false

duration: 45min
completed: 2026-08-27
status: complete
---

# Phase 15 Plan 8: Shift Envelope, Breaks & Library Generation — Shift-Model Benchmark Summary

**A seeded, step-count-terminated A/B benchmark recovered from Phase 12's withdrawn harness measures the shift model against the slot model honestly: PASS, driven by a clean 5/5-vs-1/5 feasibility-convergence gap — not by the comparative medians, which the plan's own noise rule correctly disqualifies as too small relative to the slot arm's own spread — plus a "no measurable difference" answer to the D-08 construction-heuristic-ordering question.**

## Performance

- **Duration:** ~45 min
- **Tasks:** 3
- **Files modified:** 3 (1 created, 2 modified)

## Accomplishments

- Recovered `AtomicShiftMoveBenchmarkTest`'s proven mechanics (deleted at commit `299c42c`) into `ShiftModelBenchmarkTest`: fixed seeds 1–5, `TerminationConfig.withStepCountLimit` (never wall-clock), gated behind `-Dwfm.benchmark=true`, loading the REAL `solverConfig.xml` (never a hand-built config, per the `ShiftEnvelopeGroundTruthTest` convention).
- Restored `build.gradle`'s `wfm.benchmark` system-property test-JVM passthrough, also removed at `299c42c` — without it the gate is silently inert (Rule 3 blocking fix, logged to `.planning/WINDOWS.md` as a deviation).
- `15-BENCHMARK.md` carries the D-15 pass rule, the D-13 piloting-not-shipping gate, and the noise rule, committed in `cd26db9` — BEFORE the Results commit (`836033a`) — so `git log` order is itself the evidence the threshold predates the numbers (P-36).
- D-08's third arm (seats-first CH ordering) built by reordering the SAME loaded `solverConfig.xml` resource's phase list in place (P-35), verified defensively against phase-order drift, never a second XML file.
- **Result: both shift arms reach `0hard` on every one of 5 seeds, deterministically** (identical scores across all seeds — construction heuristic alone suffices, zero local-search improvement occurs or is needed). **The slot arm reaches `0hard` on only 1 of 5 seeds.** A supplementary ad-hoc 20,000-step sweep (20x budget, not part of the committed test) confirmed this is a genuine local-search plateau, not a step-budget artifact — 3 of 4 previously-failing slot seeds remained essentially unchanged at 20x the steps.
- Applied the noise rule literally, including where it produced a counterintuitive result: the shift arm's comparative medians beat the slot arm's by margins smaller than the slot arm's own spread, so those specific deltas are honestly reported as "no measurable difference" — the PASS verdict rests on the (noise-rule-exempt) discrete convergence outcome instead.
- D-08 answered by measurement: the two CH orderings produced byte-identical results on every metric and every seed — "no measurable difference"; `solverConfig.xml` is unchanged.
- Soft-score plateau named as a finding (shift arm's soft score is worse than slot's because it evaluates three more constraints slot never does), explicitly not thresholded, mechanism stated, not remedied (custom moves stay out of v1.3 scope).
- Full suite green: 79 test classes, 0 failures/errors, including `SolverConfigBuildTest` against the unmodified `solverConfig.xml`.

## Task Commits

1. **Task 1: Recover the harness, commit the threshold, run one seed end to end** - `cd26db9` (feat)
2. **Task 2: The full run — three arms, five seeds, plus one indicative real-desk run** - `836033a` (feat)
3. **Task 3: The verdict, the plateau finding, and the ordering that ships** - `57ad2a7` (docs) + `5655436` (docs, T-15-32 clarification)

## Files Created/Modified

- `src/test/java/com/wfm/solver/ShiftModelBenchmarkTest.java` - the seeded A/B benchmark harness, three arms, five seeds, one indicative real-desk-scale run
- `build.gradle` - restored `wfm.benchmark` system-property test-JVM passthrough (Rule 3 deviation)
- `.planning/phases/15-shift-envelope-breaks-library-generation/15-BENCHMARK.md` - pass rule (committed first), results, verdict, plateau finding, CH-ordering decision, piloting recommendation

## Decisions Made

- Kept the comparative fixture small (4 agents, 2 days) to stay within the committed suite's runtime budget; the 1:1 seat-to-demand ratio this yields (no idle seat headroom, unlike Phase 12's literal over-allocation framing) is documented as a scale caveat rather than silently equated to Phase 12's concept.
- Treated D-16's "real-desk" run as matching real-desk SCALE (30 agents, per `SPIKE-COUPLING.md` open item 1's own framing), not a literal production-data pull — no Spring context, no database connection, entirely synthetic via `ShiftModeFixtures`. Documented explicitly to close threat T-15-32 by construction.
- Ran a supplementary ad-hoc 20,000-step sweep (outside the committed test) specifically to rule out "the slot arm just needed more steps" as an alternative explanation for its convergence gap — it did not explain the gap, and this is reported transparently, separated from the committed numbers.
- Did not change `solverConfig.xml`: the two CH orderings measured identically, so the plan's own instruction ("record 'no measurable difference', keep the committed shifts-first ordering") was followed literally.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] `build.gradle`'s `wfm.benchmark` system-property passthrough was missing**
- **Found during:** Task 1, first attempt to run `./gradlew test --tests "com.wfm.solver.ShiftModelBenchmarkTest" -Dwfm.benchmark=true`
- **Issue:** The passthrough (`systemProperty 'wfm.benchmark', System.getProperty('wfm.benchmark', 'false')` in the `test` task) was removed at commit `299c42c` alongside the entire Phase 12 harness it gated. Without it, `-Dwfm.benchmark=true` on the Gradle command line sets a property on the Gradle daemon's own JVM, never the forked test JVM, so `@EnabledIfSystemProperty(named = "wfm.benchmark", matches = "true")` would silently see no matching property and the benchmark test would silently be skipped in every invocation, including the ones this plan's own `<verify>` commands specify.
- **Fix:** Restored the exact passthrough (same narrow, single-property form, same rationale comment) that existed before `299c42c`.
- **Files modified:** `build.gradle`
- **Verification:** `./gradlew test --tests "com.wfm.solver.ShiftModelBenchmarkTest" -Dwfm.benchmark=true` executes and prints complete per-run/summary markdown tables to stdout (captured verbatim into `15-BENCHMARK.md`) — proof the tests actually ran rather than being silently skipped. A subsequent DEFAULT `./gradlew test` (no flag) correctly shows `skipped="2"` in `TEST-com.wfm.solver.ShiftModelBenchmarkTest.xml`, confirming the gate is bidirectionally correct: on with the flag, off without it.
- **Committed in:** `cd26db9` (Task 1 commit)
- **Logged to:** `.planning/WINDOWS.md` (kind: deviation, phase 15)

---

**Total deviations:** 1 auto-fixed (Rule 3 — blocking, necessary for the plan's own `<verify>` commands to execute at all, not skip silently)
**Impact on plan:** Necessary for the plan's stated verification commands to run; no scope creep, no production code touched.

## Issues Encountered

None beyond the deviation above.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- `ShiftModelBenchmarkTest` and `15-BENCHMARK.md` are the permanent XCUT-04 evidence for this milestone; Phase 17's own XCUT-04 obligation (per REQUIREMENTS.md's "XCUT-04 (seeded A/B benchmark) | Phases 15, 17") can reuse this harness's mechanics and `ShiftModeFixtures` directly rather than rebuilding them.
- `solverConfig.xml` is unchanged (shifts-first CH ordering ships) — D-08 is closed, not left open for Phase 17.
- The cross-agent seat-displacement todo (`.planning/todos/pending/2026-08-13-cross-agent-seat-displacement.md`) remains deliberately unlinked to any phase, as `PROJECT.md` records — this plan measures the same underlying gap but does not close it and does not link the todo.
- The piloting recommendation (supervised single-desk pilot, on the convergence-reliability evidence, real-desk-scale validation before widening) is ready for an operator decision outside this plan's scope.

## Self-Check: PASSED

- All created/modified files verified present on disk: `src/test/java/com/wfm/solver/ShiftModelBenchmarkTest.java`, `build.gradle`, `.planning/phases/15-shift-envelope-breaks-library-generation/15-BENCHMARK.md`.
- All four task commits verified present in `git log`: `cd26db9`, `836033a`, `57ad2a7`, `5655436`.
- Plan `<verification>` re-run: `./gradlew test` green (79 test classes, 0 failures/errors, `ShiftModelBenchmarkTest` correctly `skipped="2"` without the flag); `./gradlew test --tests "com.wfm.solver.ShiftModelBenchmarkTest" -Dwfm.benchmark=true` completes all three arms across all five seeds plus the indicative run, verified twice for reproducibility (identical results both runs, as expected from fixed seeds); `git log --oneline` confirms `cd26db9` (pass rule) precedes `836033a` (results); `grep -n '\bmean\b'` on `15-BENCHMARK.md` shows only negation contexts ("never a mean") — no computed mean anywhere; `SolverConfigBuildTest` green against the unmodified `solverConfig.xml`.

---
*Phase: 15-shift-envelope-breaks-library-generation*
*Completed: 2026-08-27*
