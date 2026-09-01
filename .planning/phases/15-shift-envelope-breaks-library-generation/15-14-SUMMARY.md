---
phase: 15-shift-envelope-breaks-library-generation
plan: 14
subsystem: testing
tags: [timefold, junit, assertj, solver-quality-guard, shift-mode]

requires:
  - phase: 15 (plans 09-13)
    provides: the shift-envelope/contiguity/seat-supply constraints and shipped solverConfig.xml this guard exercises unmodified
provides:
  - "A default-suite (ungated) solver-quality guard: LiveShapeShiftDeskFixture (live-library-shaped synthetic desk) + SolverQualityGuardTest (three independent structural walkers, a violation-count failure reporter, a five-seed sweep, and a pinned-defaults test)"
  - "Stable invariant identifiers INV-1/2/3/4, referenced by plan 15-15 and by any future failure output"
affects: [15-15]

actuals:
  tokens: 14853
  tasks: 3
  commits: 3

tech-stack:
  added: []
  patterns:
    - "Independent structural walkers (P-17): every invariant is computed from raw LocalTime arithmetic over template/band fields, never ShiftBandPair.covers, ScheduleConstraintProvider, or a score director -- so a walker cannot inherit the production predicate's own blind spot"
    - "Median-of-five, never a mean, for the one score-shaped assertion (violation COUNTS, never raw hard score)"
    - "Bounded convergence/runtime escape hatch, applied and documented rather than silently tuned"

key-files:
  created:
    - src/test/java/com/wfm/solver/LiveShapeShiftDeskFixture.java
    - src/test/java/com/wfm/solver/SolverQualityGuardTest.java
  modified: []

key-decisions:
  - "Escape hatch applied jointly: DAY_COUNT 3->2 AND STEP_COUNT_LIMIT 2000->5000 (see Deviations) -- neither rung alone cleared a genuine interior hole the unmodified fixture left at the plan's initial budget"
  - "TOTAL_VIOLATION_CEILING set from the observed baseline per the pre-committed rule (median + 2, floored at 2): baseline median 1.0 -> ceiling 3"
  - "Runtime-budget acceptance criterion (guard's added wall time <= 90s) measured honestly as noisy on this machine across repeated back-to-back full-suite invocations in one session; the isolated, unloaded guard-class measurement (20-31s) is offered as the reliable evidence and is well within budget -- see Deviations"

requirements-completed: [ENVL-02, ENVL-04, XCUT-04]

coverage:
  - id: D1
    description: "An automated solver-quality guard runs in the DEFAULT ./gradlew test suite, ungated -- no new system property, tag, or gradle task"
    requirement: "XCUT-04"
    verification:
      - kind: unit
        ref: "SolverQualityGuardTest (all 3 test methods)#./gradlew test --tests com.wfm.solver.SolverQualityGuardTest"
        status: pass
      - kind: other
        ref: "git diff --quiet HEAD -- build.gradle .github/workflows/"
        status: pass
    human_judgment: false
  - id: D2
    description: "Three structural invariants (split shifts, edge breaks, edge-hour coverage) computed by independent LocalTime-arithmetic walkers, sharing no code with the production constraint/score director"
    requirement: "ENVL-04"
    verification:
      - kind: unit
        ref: "SolverQualityGuardTest#liveShapeDesk_fiveSeeds_holdEveryStructuralInvariant"
        status: pass
    human_judgment: false
  - id: D3
    description: "A failure report names the broken invariant, the offending rows, the full edge-hour coverage matrix, and a per-constraint violation-COUNT table with the not-comparable-hard-scores instruction (G-15-29), wired into every invariant assertion's AssertJ description"
    verification:
      - kind: unit
        ref: "SolverQualityGuardTest#buildQualityReport (exercised as the .as(...) description on every INV-1/2/3 assertion)"
        status: pass
    human_judgment: false
  - id: D4
    description: "Five seeded, step-count-terminated solves per run; INV-1/2/3 absolute on every seed; INV-4 (median totalHardViolations) asserted against a ceiling committed before the number was read"
    requirement: "XCUT-04"
    verification:
      - kind: unit
        ref: "SolverQualityGuardTest#liveShapeDesk_fiveSeeds_holdEveryStructuralInvariant"
        status: pass
    human_judgment: false
  - id: D5
    description: "The four shipped ConstraintWeights no-arg defaults are pinned by a fast, solve-free assertion (the G-15-30 counterweight to the fixture's own pinned live values)"
    requirement: "ENVL-02"
    verification:
      - kind: unit
        ref: "SolverQualityGuardTest#defaultConstraintWeights_areTheDocumentedShippedValues"
        status: pass
    human_judgment: false
  - id: D6
    description: "The guard's added wall-clock cost to the deploy gate is bounded (<=90s) and does not turn into a second BreakAwareConstructionTest"
    verification:
      - kind: other
        ref: "isolated SolverQualityGuardTest class time, unloaded: 20.155s (first run), 31.009s (later run, same machine under accumulated load) -- both well under 90s"
      - kind: other
        ref: "full-suite before/after delta on this machine: +147s then +235s across two consecutive ./gradlew test --rerun-tasks invocations -- confounded by cumulative thermal/load noise from three back-to-back ~10-12 min suite runs in one session, not attributable to the guard's own cost"
        status: unknown
    human_judgment: true
    rationale: "The isolated measurement is solid evidence the guard itself is cheap (20-31s), but I could not produce a clean single full-suite before/after delta on this machine within this session's time budget -- each ./gradlew test run took 10-12+ minutes and grew monotonically slower on each successive invocation (thermal throttling on a fanless MacBook Air), the same machine-load-sensitivity class of noise this whole plan exists to prevent attributing to solver quality, now observed in build/test infra timing instead. A human should independently confirm the deploy gate's Test job duration does not meaningfully regress (baseline: 12m53s per HANDOFF.md's a320ca7 deploy) rather than trust my in-session full-suite deltas."

duration: 41min
completed: 2026-09-01
status: complete
---

# Phase 15 Plan 14: Solver Quality Guard (G-15-22 / G-15-29) Summary

**An ungated, default-suite guard on solver quality: a live-library-shaped synthetic desk solved five times (seeded, step-count terminated) through the shipped `solverConfig.xml`, asserting zero split shifts, zero edge breaks, and full edge-hour coverage on every seed via independent structural walkers, plus a median-violation-count trip-wire and pinned shipped-default weights — never a raw hard-score assertion.**

## Performance

- **Duration:** 41 min
- **Started:** ~2026-09-01T20:00:00Z
- **Completed:** 2026-09-01T20:41:08Z
- **Tasks:** 3
- **Files modified:** 2 (both new)

## Accomplishments

- `LiveShapeShiftDeskFixture` — a synthetic desk transcribing the live Stubhub (EN) library's exact template/band geometry and HANDOFF.md §2's live constraint weights verbatim, including the `Weekend Flex` slack template (the one hour of slack that makes a split shift representable at all) and a class-load-time static validator that fails loudly if a future library edit removes the slack or violates the band-edge margin.
- `SolverQualityGuardTest` — three independent structural walkers (INV-1 split shifts, INV-2 edge breaks [structural + operational], INV-3 edge-hour coverage), a violation-COUNT failure reporter (`buildQualityReport`) wired into every invariant assertion, a five-seed sweep with a pre-committed median-violation-count ceiling (INV-4), and a fast solve-free test pinning the four shipped `ConstraintWeights` defaults.
- The guard runs in the DEFAULT `./gradlew test` suite with no new Gradle task, system property, or CI workflow change — the deploy gate picks it up automatically via its existing `./gradlew build`.

## Task Commits

Each task was committed atomically:

1. **Task 1: End-to-end tracer — one seed, one invariant, the whole path proved** — `8ade3f9` (feat)
2. **Task 2: Edge-break and edge-hour-coverage invariants, plus the violation-count failure report** — `ec84ed3` (feat)
3. **Task 3: Five seeds, the median violation-count ceiling, and the pinned shipped defaults** — `8d7e411` (feat)

**Plan metadata:** committed separately with STATE.md/ROADMAP.md updates.

## Files Created/Modified

- `src/test/java/com/wfm/solver/LiveShapeShiftDeskFixture.java` — the live-shape synthetic desk builder (P-37 through P-41)
- `src/test/java/com/wfm/solver/SolverQualityGuardTest.java` — the guard test class: three walkers, the violation-count reader, the failure reporter, and three test methods

## Decisions Made

- **Escape hatch applied, jointly (Task 1, before any expansion task):** at the plan's literal `STEP_COUNT_LIMIT = 2_000` (matching `ShiftDeskEndToEndRegressionTest`'s budget on a much smaller, zero-slack 3-agent/3-day fixture), this fixture's slack-carrying, 10-agent/3-day, 270-entity problem left a genuine interior hole on seed 1 — a fixture-fairness finding on the unmodified build, not a product regression. Tried, in order, per the plan's bounded ladder: rung 1 alone (step count 2,000 → 5,000, `DAY_COUNT` still 3) did NOT clear it. Rung 3 alone (`DAY_COUNT` 3 → 2, step count still 2,000) did NOT clear it either. The two rungs applied TOGETHER — `DAY_COUNT = 2` and `STEP_COUNT_LIMIT = 5_000` — did clear it, and all three invariants then held on every one of the five seeds. Never dropped below `STEP_COUNT_LIMIT = 2_000`, never switched to wall-clock termination, never weakened an invariant, never removed the `Weekend Flex` slack template. Documented in both files' javadoc.
- **`TOTAL_VIOLATION_CEILING = 3`**, set from the observed baseline per the pre-committed rule (P-42): five-seed sweep on the unmodified build gave `totalHardViolations` = 3, 1, 2, 0, 1 (seeds 1-5 respectively) — sorted `[0, 1, 1, 2, 3]`, median 1.0, ceiling = median + headroom(2) = 3.
- **`unassignedAssignmentWeight` pinned to `ofSoft(10000)`** (not promoted to hard) in the fixture, matching HANDOFF.md §2's live setting at the same soft score level as the shipped default.

## Per-Seed Results (baseline, unmodified build, transcribed verbatim from the harness's own stdout)

| seed | hardScore | totalHardViolations | splitShifts | edgeBreaks | unstaffedEdgeHours | elapsedMillis |
|---|---|---|---|---|---|---|
| 1 | -120 | 3 | 0 | 0 | 0 | 3262 |
| 2 | -10 | 1 | 0 | 0 | 0 | 3477 |
| 3 | -20 | 2 | 0 | 0 | 0 | 3512 |
| 4 | 0 | 0 | 0 | 0 | 0 | 3651 |
| 5 | -10 | 1 | 0 | 0 | 0 | 3370 |

Per-constraint violation counts across seeds:

| constraint | seed 1 | seed 2 | seed 3 | seed 4 | seed 5 |
|---|---|---|---|---|---|
| Shift envelope compliance | 2 | 1 | 2 | 0 | 1 |
| Contracted hours (under) | 1 | 0 | 0 | 0 | 0 |

Median `totalHardViolations` = 1.0. All five seeds: 0 split shifts, 0 edge breaks, 0 unstaffed edge hours (INV-1/2/3 hold on every seed). Five-seed sweep total elapsed: 17.4s (this run).

## Runtime Budget — measured, with an honest caveat

- **Isolated `SolverQualityGuardTest` class time (unloaded machine):** 20.155s (first run, 3 test methods), 31.009s (a later run on the same machine after accumulated load). Both comfortably under the 90-second budget.
- **Full-suite `./gradlew test` (593 tests, up from HANDOFF.md's recorded 590):** BUILD SUCCESSFUL both times, 0 failures, 0 errors, 2 skipped.
  - Run 1 (`./gradlew test`, first invocation this session): 10m35s — a +2m27s (147s) delta against HANDOFF.md's recorded 8m08s baseline.
  - Run 2 (`./gradlew test --rerun-tasks`, forced re-execution): 12m03s — a +3m55s (235s) delta against the same baseline.
- **These two full-suite deltas exceed the plan's 90-second ceiling on their face, and I am reporting that plainly rather than silently reducing seed count to make the number look better.** I judge them NOT attributable to the guard's own cost, for three reasons: (1) the isolated, unloaded guard-class cost is consistently 20-31s, two orders of magnitude smaller than either full-suite delta; (2) the delta grew monotonically across two consecutive full-suite runs (147s → 235s) despite the code under test being byte-identical between them, which is the signature of accumulating machine load/thermal throttling on a fanless MacBook Air after three back-to-back ~10-12 minute `./gradlew test` invocations in one session, not a fixed per-run cost; (3) this project's own `deferred-items.md` records the identical failure mode for `BreakAwareConstructionTest` — a wall-clock-bounded measurement that "measures hardware as well as solver quality" — and this plan exists specifically to avoid inheriting that class of mistake into a *new* gate. Applying the runtime ladder's rung 2 (`SEEDS` five → three) would not address a 150-350s delta with a ~5-7s saving, and would directly weaken the plan's own "Five seeded... solves" success criterion for no real fix.
- **Not resolved by this plan; flagged for human confirmation (see coverage D6):** the actual CI-runner Test job (a clean, single-purpose environment, unlike my repeated-invocation dev machine) is the number that actually matters for the 90-second budget's intent. HANDOFF.md records the last live deploy's Test job at 12m53s (a320ca7, deploy `33543912228`). A human should confirm the next deploy's Test job does not regress materially past that figure; I was not able to produce a trustworthy in-session full-suite before/after delta on this machine to close that loop myself.
- **No ladder step was taken to reduce runtime** (Day-count and step-count changes made were for CONVERGENCE, not runtime, and are documented separately above). `SEEDS` remains five; `DAY_COUNT` remains 2 (from the convergence escape hatch, which incidentally also reduces problem size).

## Deviations from Plan

### Escape Hatch (plan-authorized, not a Rule 1-4 deviation)

**1. Convergence escape hatch applied jointly (rung 1 + rung 3)**
- **Found during:** Task 1 (tracer), before any expansion task
- **Issue:** At the plan's literal `STEP_COUNT_LIMIT = 2_000` and `DAY_COUNT = 3`, the unmodified fixture/build left a genuine interior hole (INV-1 violation) on seed 1 — a fixture-fairness gap, not a code defect (per this plan's own `<reporting_honesty>` clause).
- **Fix:** Applied `STEP_COUNT_LIMIT = 5_000` (rung 1) and `DAY_COUNT = 2` (rung 3) together, after confirming neither alone sufficed. Never lowered step count below 2,000; never touched an invariant; never removed the `Weekend Flex` slack template.
- **Files modified:** both new files, in their initial commits (Task 1)
- **Verification:** All three invariants (INV-1/2/3) then hold on every one of the five seeds (Task 3's sweep)
- **Committed in:** `8ade3f9` (Task 1), constant declared with full javadoc explaining the rung history

### Reporting-Honesty Finding (not fixed, surfaced per `<reporting_honesty>`)

**2. Full-suite runtime-budget delta measured noisy, not brought under 90s via the ladder**
- **Found during:** Task 3, runtime budget measurement step
- **Issue:** Two consecutive full-suite `./gradlew test` runs (10m35s, then 12m03s under `--rerun-tasks`) showed deltas of 147s and 235s against HANDOFF.md's recorded 8m08s baseline — both over the plan's 90s ceiling. The isolated guard-class cost (20-31s) is well under budget.
- **Judgment call:** Attributed the full-suite deltas to accumulated machine load across repeated back-to-back Gradle invocations on a fanless dev laptop (monotonically increasing delta on byte-identical code), not to the guard's own cost. Did not apply the runtime ladder's SEEDS reduction, since it would not address a delta two orders of magnitude larger than the guard's own runtime, and would weaken the plan's own five-seed success criterion for no real fix.
- **Not auto-resolved:** flagged in coverage `D6` with `human_judgment: true` — a human should confirm the CI runner's actual Test job duration against the recorded 12m53s deploy-gate baseline.

---

**Total deviations:** 1 plan-authorized escape hatch (documented, bounded, per the plan's own clause) + 1 unresolved reporting-honesty finding (surfaced for human confirmation, not silently worked around).
**Impact on plan:** The escape hatch was necessary for the guard to pass at all and stayed within the plan's explicit bounds. The runtime-budget finding does not block the guard's correctness or its landing in the default suite — `./gradlew test` is green — but the 90-second ceiling's satisfaction against the REAL deploy-gate environment is not independently confirmed by this execution and needs a human's eyes on the next CI run.

## Issues Encountered

See "Deviations from Plan" above — both items are disclosed there rather than repeated here (per the template's guidance, "Deviations" documents unplanned work handled automatically or judgment calls made; "Issues Encountered" is for problems during planned work). No additional issues beyond those two.

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

- The guard (`SolverQualityGuardTest`) is live in the default suite and green; `INV-1`/`INV-2`/`INV-3`/`INV-4` are the stable identifiers plan 15-15 (and any future failure output) can reference.
- **Recommended before the next `git push`:** run the full CI deploy gate once and confirm its Test job duration against the 12m53s baseline recorded in HANDOFF.md — this execution could not produce a trustworthy in-session equivalent measurement (see Runtime Budget section and coverage `D6`).
- G-15-22 and G-15-29 are closed: a solver-tuning change that wrecks convergence now fails `./gradlew test` — the same command the deploy gate runs — via a structural-invariant guard that cannot be defeated by run-to-run score noise, with a failure report that names the broken invariant and instructs the reader not to compare raw hard scores across weight changes.

---
*Phase: 15-shift-envelope-breaks-library-generation*
*Completed: 2026-09-01*
