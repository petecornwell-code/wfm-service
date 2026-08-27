# Phase 15 — Shift Model Benchmark (XCUT-04)

**Status at this commit: THRESHOLD ONLY. No result exists yet.**

This section — Harness Configuration, Pass Rule, What The Verdict Gates, and Noise Rule — is
written and committed **before** any benchmark run is executed, per D-15/D-16 and the plan's
own P-36 discipline: the git history for this file is itself the evidence that the threshold was
not chosen after seeing a number, a property prose alone cannot establish. The Results section
below is a placeholder until Task 2's commit fills it in verbatim from the harness's own printed
output — no fixture, seed, or step-budget value is adjusted after that point.

**Why this file exists at all:** Phase 12 measured a +0.25h median hours-assigned improvement from
the atomic shift move, inside a **5.00h** min/max noise spread on the very same baseline, read the
0.25h as an improvement, and the phase was withdrawn (`12-BENCHMARK.md`). XCUT-04 exists so that
mistake is structurally impossible to repeat: the pass rule below is committed first, the noise
rule is applied literally, and no reading of "smaller than the spread" is allowed to become a win.

---

## Harness

- **File:** `src/test/java/com/wfm/solver/ShiftModelBenchmarkTest.java`
- **Command (committed benchmark, bounded scale):**
  `./gradlew test --tests "com.wfm.solver.ShiftModelBenchmarkTest" -Dwfm.benchmark=true`
- **Gate:** `-Dwfm.benchmark=true` (via `@EnabledIfSystemProperty`) — never runs in the default
  `./gradlew test` suite. `build.gradle`'s `wfm.benchmark` system-property passthrough was
  restored (it was removed alongside the Phase 12 harness at commit `299c42c`) so this flag
  actually reaches the test JVM.
- **Recovered from:** `AtomicShiftMoveBenchmarkTest.java` (Phase 12, deleted at `299c42c`,
  retrievable at `299c42c^`) — same mechanics: fixed seeds, step-count termination, a
  print-a-markdown-table output style transcribed verbatim into this document (P-34).
- **Fixture source:** `ShiftModeFixtures` (plan 15-04) — the SAME builder the ENVL correctness
  tests use, so this benchmark and the correctness tests agree on what a shift-mode schedule is
  (P-19). The shift and slot arms differ ONLY in `SchedulingMode` and the presence of a shift
  library — never in demand, roster, grid, or constraint weights.
- **Solver config:** the REAL `src/main/resources/solverConfig.xml`, loaded via
  `SolverConfig.createFromXmlResource` (never a hand-built config) — the same convention
  `ShiftEnvelopeGroundTruthTest` established (P-18). D-08's seats-first arm reorders the SAME
  loaded resource's phase list in place (P-35), never a second XML file that could drift.

### Harness Configuration — comparative arms (slot / shift-shifts-first / shift-seats-first)

| Property | Value |
|---|---|
| Agents | 4 |
| Days | 2 |
| Shift templates (shift arms only) | 2, sharing one envelope (08:00–17:00) |
| Bands per template | 1 |
| Increment | 15 minutes |
| Contracted hours/agent/day | 8.00h |
| Break duration | 60 minutes |
| Break blocked window | 1.00h at each end |
| Break alignment | `ON_HOUR` |
| Over-allocation (`AgentDayConfig.overallocationHardLimitPct`) | **130%** — `ShiftModeFixtures.OVERALLOCATION_PCT`, the realistic figure XCUT-04 and D-16 name; already baked into the reused fixture, not re-derived |
| Under-allocation (`underallocationHardLimitPct`) | 70% |
| Timefold version | 1.16.0 |
| Termination | `TerminationConfig.withStepCountLimit(1000)` attached to the trailing local-search phase — step-count, never wall-clock, never `withSpentLimit` (per the standing 12-RESEARCH.md lesson) |
| Random seeds | 1, 2, 3, 4, 5 (`SolverConfig.withRandomSeed`) |
| Runs per arm | 5 (one per seed) |
| Arms | `slot`, `shift-shifts-first` (solverConfig.xml's committed order), `shift-seats-first` (D-08's third arm — the two construction-heuristic phases swapped, nothing else) |

### Harness Configuration — indicative real-scale run (D-16, non-comparative)

| Property | Value |
|---|---|
| Agents | 30 |
| Days | 1 |
| Shift templates | 3, sharing one envelope |
| Termination | `TerminationConfig.withStepCountLimit(3000)` |
| Seeds | 1 (single run — indicative, not a seed sweep) |
| Purpose | Partly closes `SPIKE-COUPLING.md` open item 1 — whether the extra `AgentAssignment × AgentShiftAssignment` join costs materially at real scale (~30 agents, 21 constraints). Reported for scale only; no pass/fail criterion attaches to it. |

---

## Pass Rule — committed before any result exists (D-15, verbatim)

- **Must-pass:** the shift arm reaches `0hard` on **every** seed (both CH orderings).
- **Comparative:** median unstaffed demand slots for the shift arm is **no worse** than the slot
  arm's median unstaffed demand slots.
- **Noise rule:** any difference between the shift arm and the slot arm smaller than the **slot
  arm's own min/max spread** is written up as **"no measurable difference"** — never as a win
  *or* a loss, in either direction. This is Phase 12's lesson encoded as a rule rather than
  relearned: a +0.25h median sitting inside a 5.00h spread should never have been read as an
  improvement.

The same noise rule applies to the D-08 CH-ordering question: shifts-first vs seats-first are
compared against each other under this identical noise rule, and the winner ships.

**Metrics measured for the threshold (D-14 — model-independent only):** whether `0hard` was
reached, the unstaffed demand slot count, and total hours assigned. The soft score is captured and
reported for every run, but it is **held separately and never thresholded** — shift mode gives
`Break clustering` a real body and adds `shiftEnvelopeCompliance` and `bandCapacity`, constraints
the slot arm never evaluates, so the two arms' soft totals are incommensurable. Thresholding on raw
soft score would declare a regression that is partly an artefact of measuring a constraint set the
baseline was never subject to.

**Median AND full min/max spread are reported for every metric in the Results section below. No
mean appears anywhere in this document, for any metric, under any arm.**

## What This Verdict Gates (D-13)

`Desk.scheduling_mode` already defaults to `SLOT` on every desk — the fallback is structural, not a
promise. **A FAIL on the pass rule above means no desk gets switched to shift mode yet; it does
not withdraw a correctness deliverable.** Phase completion is judged on ENVL-01…10 correctness,
which `ShiftEnvelopeGroundTruthTest`'s ground-truth walker (plan 15-04) verifies independently of
any score in this document — that walker proved sound against the exact failure mode
(`SPIKE-COUPLING.md`'s Option C) this benchmark's own numbers cannot see. This benchmark's verdict
gates **piloting a shift-mode desk**, not shipping the phase.

---

## Results

*(To be filled in by Task 2's commit, verbatim as the harness prints it. No fixture, seed, or
step-budget value will be adjusted after this point.)*

## Verdict

*(To be filled in by Task 3's commit, applying the Pass Rule above exactly as committed.)*

## Plateau Finding

*(To be filled in by Task 3's commit.)*

## CH-Ordering Decision (D-08)

*(To be filled in by Task 3's commit.)*

## Piloting Recommendation

*(To be filled in by Task 3's commit.)*

## What This Benchmark Does Not Close

*(To be filled in by Task 3's commit.)*
