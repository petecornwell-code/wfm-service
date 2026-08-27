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

**Run date:** 2026-08-27
**Command:** `./gradlew test --tests "com.wfm.solver.ShiftModelBenchmarkTest" -Dwfm.benchmark=true`
**Result:** BUILD SUCCESSFUL. Numbers below are transcribed verbatim from the harness's own stdout.
No fixture, seed, or step-budget value was adjusted after this run.

### Per-Run Results — Comparative Arms (5 seeds each)

| arm | seed | hardScore | softScore | unstaffedDemandSlots | hoursAssigned | hoursNeeded | elapsedMillis |
|---|---|---|---|---|---|---|---|
| slot | 1 | 0 | -128 | 0 | 64.00 | 64.00 | 5410 |
| slot | 2 | -5140 | -149 | 43 | 51.25 | 64.00 | 4207 |
| slot | 3 | -3420 | -147 | 28 | 55.50 | 64.00 | 4197 |
| slot | 4 | -1700 | -144 | 17 | 59.75 | 64.00 | 3999 |
| slot | 5 | -3420 | -149 | 34 | 55.50 | 64.00 | 3072 |
| shift-shifts-first | 1 | 0 | -192 | 0 | 64.00 | 64.00 | 82 |
| shift-shifts-first | 2 | 0 | -192 | 0 | 64.00 | 64.00 | 66 |
| shift-shifts-first | 3 | 0 | -192 | 0 | 64.00 | 64.00 | 62 |
| shift-shifts-first | 4 | 0 | -192 | 0 | 64.00 | 64.00 | 58 |
| shift-shifts-first | 5 | 0 | -192 | 0 | 64.00 | 64.00 | 61 |
| shift-seats-first | 1 | 0 | -192 | 0 | 64.00 | 64.00 | 59 |
| shift-seats-first | 2 | 0 | -192 | 0 | 64.00 | 64.00 | 58 |
| shift-seats-first | 3 | 0 | -192 | 0 | 64.00 | 64.00 | 58 |
| shift-seats-first | 4 | 0 | -192 | 0 | 64.00 | 64.00 | 62 |
| shift-seats-first | 5 | 0 | -192 | 0 | 64.00 | 64.00 | 62 |

### Summary — Median and Full Min/Max Spread (never a mean)

| arm | hardScore median | hardScore min | hardScore max | unstaffedDemandSlots median | unstaffedDemandSlots min | unstaffedDemandSlots max | hoursAssigned median | hoursAssigned min | hoursAssigned max | softScore median | softScore min | softScore max |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| slot | -3420.0 | -5140.0 | 0.0 | 28.0 | 0.0 | 43.0 | 55.5 | 51.25 | 64.0 | -147.0 | -149.0 | -128.0 |
| shift-shifts-first | 0.0 | 0.0 | 0.0 | 0.0 | 0.0 | 0.0 | 64.0 | 64.0 | 64.0 | -192.0 | -192.0 | -192.0 |
| shift-seats-first | 0.0 | 0.0 | 0.0 | 0.0 | 0.0 | 0.0 | 64.0 | 64.0 | 64.0 | -192.0 | -192.0 | -192.0 |

**Slot arm's own min/max spread (the noise-rule denominator):** unstaffed demand slots 0–43 (spread
**43**); hours assigned 51.25h–64.00h (spread **12.75h**).

### Indicative Real-Desk-Scale Run (D-16, non-comparative — no pass/fail attaches)

30 agents, 1 day, 3 shift templates, step-count limit 3000, seed 1 (single run, not a seed sweep):

| arm | seed | hardScore | softScore | unstaffedDemandSlots | hoursAssigned | hoursNeeded | elapsedMillis |
|---|---|---|---|---|---|---|---|
| shift-indicative-30agent | 1 | 0 | -720 | 0 | 240.00 | 240.00 | 321 |

**Reading (indicative only, no conclusion drawn on timing per the project's run-to-run-variance
lesson — 12-RESEARCH.md, `SPIKE-COUPLING.md`):** the 30-agent shift-mode fixture reaches `0hard`
in 321ms wall time within a 3000-step budget, with every demand slot staffed. This partly answers
`SPIKE-COUPLING.md` open item 1 (whether the extra `AgentAssignment × AgentShiftAssignment` join
costs materially at real scale) — at this scale the join did not prevent a fast, fully-feasible
solve. No performance figure from this single indicative run is treated as a benchmark result; per
the spike's own standing caveat, only a proper seeded sweep would license that.

### Supplementary ad-hoc sweep — not part of the committed test (reported per this plan's runtime-budget guidance)

The slot arm's failure to reach `0hard` on 4 of 5 seeds (above) was checked against a 20x larger
step budget (20,000 steps instead of 1,000, same 5 seeds, same fixture, same solverConfig.xml) to
determine whether the gap is a step-budget artifact or a genuine local-search plateau. This sweep
was run ad hoc — it is NOT part of the committed `ShiftModelBenchmarkTest` (keeping the committed
suite's runtime bounded) and its numbers are reported here for interpretation only, clearly
separated from the committed-test numbers above:

| arm | seed | hardScore | softScore | unstaffedDemandSlots | hoursAssigned | hoursNeeded | elapsedMillis |
|---|---|---|---|---|---|---|---|
| slot (20k steps) | 1 | 0 | -128 | 0 | 64.00 | 64.00 | 97165 |
| slot (20k steps) | 2 | -5130 | -148 | 43 | 51.25 | 64.00 | 80142 |
| slot (20k steps) | 3 | -3420 | -140 | 24 | 55.50 | 64.00 | 99152 |
| slot (20k steps) | 4 | 0 | -128 | 0 | 64.00 | 64.00 | 110499 |
| slot (20k steps) | 5 | -3420 | -146 | 34 | 55.50 | 64.00 | 94543 |
| shift-shifts-first (20k steps) | 1–5 | 0 (all 5) | -192 (all 5) | 0 (all 5) | 64.00 (all 5) | 64.00 | 735–917 |
| shift-seats-first (20k steps) | 1–5 | 0 (all 5) | -192 (all 5) | 0 (all 5) | 64.00 (all 5) | 64.00 | 915–960 |

**Reading:** at 20x the committed step budget, one previously-failing slot seed (seed 4) converges
to `0hard`; the other three (seeds 2, 3, 5) remain at essentially the SAME hard/soft scores and
unstaffed-slot counts as at 1,000 steps — not merely slower, but stuck at what appears to be a
genuine local-search plateau that the default change/swap neighbourhood cannot escape even with
20x more search. The shift arm, by contrast, reaches its perfect result via construction heuristic
alone (0 local-search steps needed) on every seed at both budgets. This supplementary sweep is
reported to rule out "the committed test's 1,000-step budget was simply too stingy to the slot arm"
as an alternative explanation for the gap recorded above — it was checked, and it is not the
explanation. It does not change the committed-test numbers used against the Pass Rule.

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
