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

**Data provenance (T-15-32):** this run reads NO real tenant data — it uses `ShiftModeFixtures`
scaled to `agentCount=30`, the SAME synthetic fixture builder (fake agent names `Agent-N`,
synthetic `bamboohrId`s `A-N`) as the comparative arms and the ENVL correctness tests, matching
this project's established convention (`AtomicShiftMoveBenchmarkTest`'s own javadoc made the
identical defensive statement about its synthetic `BENCH-N` fixture). "Real-desk" in D-16's naming
refers to matching the REAL DESK SCALE `SPIKE-COUPLING.md` open item 1 names (30 agents, 19+
constraints) — not to reading an actual production tenant's data. This is a stronger mitigation of
T-15-32 than filtering PII from real data after the fact: no agent name, email, or BambooHR
identifier from any real desk is ever read, so none can leak into this document by construction.

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

**PASS.**

Applying the Pass Rule exactly as committed in the Pass Rule section above, against the Results
recorded above and nothing else:

| # | Criterion | Measured | Result |
|---|---|---|---|
| 1 | Must-pass: shift arm reaches `0hard` on every seed | `shift-shifts-first`: `0hard` on seeds 1,2,3,4,5 (5/5). `shift-seats-first`: `0hard` on seeds 1,2,3,4,5 (5/5). | **PASS** |
| 2 | Comparative: shift arm's median unstaffed demand slots no worse than the slot arm's median | Shift median = **0.0**. Slot median = **28.0**. `0.0 ≤ 28.0`. | **PASS** (literal wording — "no worse than" is satisfied) |
| 3 | Noise rule applied to the SIZE of that comparative difference | Difference = `28.0 − 0.0 = 28.0`. Slot arm's own spread = `43.0 − 0.0 = 43.0`. `28.0 < 43.0`. | Per the noise rule applied literally, this specific difference is **smaller than the slot arm's own spread** and is therefore written up as **"no measurable difference" on this metric's magnitude** — not claimed as a 28-slot win. Same result for hours assigned: median difference `64.0 − 55.5 = 8.5h` vs slot's own spread `64.00 − 51.25 = 12.75h`; `8.5 < 12.75` → also "no measurable difference" on magnitude. |

**Why the verdict is PASS despite criterion 3's "no measurable difference" on magnitude:** criterion
1 is not a magnitude comparison — it is a discrete, deterministic, per-seed outcome, and it is not
subject to the noise rule (there is no "spread" concept for a binary pass/fail). The shift arm
reached full feasibility on **5 of 5** seeds under both CH orderings; the slot arm reached full
feasibility on **1 of 5**. That is a **100% vs 20%** convergence-reliability difference, which is
the actual signal this benchmark surfaces — not the median unstaffed-slot-count gap, whose specific
*size* the noise rule correctly disqualifies from being called a "28-slot win" given how variable
(bimodal) the slot arm's own results are. Both readings are true simultaneously and are reported
here without softening either, exactly as `12-BENCHMARK.md`'s own two-reading disclosure did for
its threshold #5 — this document does not pick the flattering one and hide the other.

**This is precisely the discipline XCUT-04 exists to enforce.** A less careful write-up would have
reported "the shift model resolves 28 more unstaffed slots on average" as the headline number.
Applying the noise rule literally, that specific number does not clear the bar Phase 12's
withdrawal established. The number that DOES clear the bar, cleanly, is the discrete convergence
outcome (5/5 vs 1/5) — reported as such, not inflated by borrowing the disqualified metric's size.

## Plateau Finding — soft score (reported, explicitly NOT thresholded, per D-14)

| arm | soft score (all 5 seeds) |
|---|---|
| slot | median -147.0, min -149.0, max -128.0 |
| shift-shifts-first | -192.0 (identical on every seed) |
| shift-seats-first | -192.0 (identical on every seed) |

The shift arm's soft score is worse (more negative) than the slot arm's. **This is named as a
finding and is explicitly not a regression call** — the reason is stated plainly, as D-14 requires:
shift mode gives `Break clustering` a real body and adds `shiftEnvelopeCompliance` and
`bandCapacity`, three constraints the slot arm never evaluates at all. The two arms are scoring
different constraint sets, so their soft totals are incommensurable; thresholding on raw soft score
here would declare a "regression" that is partly an artefact of measuring a constraint set the
baseline was never subject to.

**The plateau's mechanism, in one line (per `SPIKE-COUPLING.md`):** improving soft score requires a
shift and its seats to move together, and a plain change-move neighbourhood only ever moves one
variable at a time — each half of that joint improvement is uphill through
`shiftEnvelopeCompliance`'s hard wall. **This phase does not remedy it.** A custom Timefold move
(combined shift-plus-seats) is the obvious remedy and is explicitly out of scope for v1.3 — Phase
12 built exactly this kind of move, it was withdrawn on this same benchmark discipline, and the
code was reverted. Reopening it needs its own evidence-led decision, not inheritance from this
benchmark's numbers.

**A supporting observation, not a separate finding:** the two CH orderings produced BYTE-IDENTICAL
soft scores (`-192.0`, every seed, both orderings) on this fixture — unlike `SPIKE-COUPLING.md`'s
toy fixture, where seats-first measurably beat shifts-first (`-5soft` vs `-10soft`). This is exactly
the divergence `SPIKE-COUPLING.md` open item 5 warned might happen ("its toy fixture has seat
demand fully determining shift choice, so its `-5soft` seats-first result may not transfer") — and
it did not transfer. See the CH-Ordering Decision below.

## CH-Ordering Decision (D-08)

**No measurable difference — the committed shifts-first ordering ships unchanged.**
`solverConfig.xml` is NOT modified.

`shift-shifts-first` and `shift-seats-first` produced IDENTICAL results on every metric, across all
5 seeds: `0hard`, `-192soft`, 0 unstaffed demand slots, 64.00h assigned — with no variation at all.
The difference between the two orderings is therefore `0`, trivially smaller than any spread,
satisfying the plan's own instruction: "If the two orderings differ by less than the noise spread,
record 'no measurable difference', keep the committed shifts-first ordering, and say in the report
that the ordering was measured and found not to matter at this scale."

This is a genuine, measured answer to D-08 — not an assumption inherited from the toy spike. The
spike's own open item 5 predicted exactly this outcome was possible ("may not transfer"), because
the spike's toy fixture had seat demand fully determining shift choice while this fixture's
templates all share one identical envelope, giving the CH no real choice to make between orderings
in the first place. **Either outcome (a measured winner, or no measurable difference) is a result;
neither is a failure to decide** — this benchmark decided by measurement, and measurement said the
ordering does not matter at this scale.

## Piloting Recommendation (D-13)

**The shift model is a candidate for a supervised single-desk pilot, on the strength of the
convergence-reliability result — not on the strength of the (noise-disqualified) magnitude of the
unstaffed-slot or hours-assigned medians.**

What an operator should do next, and on what evidence:

1. **Pilot on one shift-scheduled desk under supervision**, comparing its actual solved schedules
   against what the same desk would have produced in slot mode over a few real solve cycles. The
   evidence for taking this step is the must-pass criterion's clean, non-noise-bound result: the
   shift arm reached full feasibility on every one of 5 seeds under both CH orderings; the slot arm
   reached full feasibility on only 1 of 5, under the identical step budget, on the identical
   fixture, with the gap confirmed as a genuine local-search plateau (not a step-budget artifact) by
   the supplementary 20,000-step sweep above.
2. **Do not read a specific "N fewer unstaffed slots" or "N more hours" number into pilot-selling
   material.** The comparative medians measured here (0 vs 28 unstaffed; 64.0h vs 55.5h) are
   directionally favorable but do not individually clear this benchmark's own noise rule at this
   fixture's scale — reporting them as sized wins would repeat exactly the Phase 12 mistake XCUT-04
   exists to prevent.
3. **Expect a lower soft score on shift-mode schedules and do not treat it as a defect.** The
   Plateau Finding above explains why: shift mode evaluates three more constraints than slot mode
   ever did, and the mechanism (a plain change-move neighbourhood can't move a shift and its seats
   together) is understood, named, and explicitly not remedied in this milestone.
4. **A FAIL was not returned here, but even if it had been, `scheduling_mode` already defaults to
   `SLOT` on every desk** — nothing is switched automatically by this document. `Desk`-level
   `scheduling_mode` remains an explicit, reversible, per-desk operator choice regardless of this
   verdict.
5. **Re-run this benchmark against real desk data before widening the pilot past one desk.** The
   comparative arms here use a small, maximally-tight synthetic fixture (4 agents, exactly
   `agentCount` seats per timeslot — no idle seat headroom at all, a tighter ratio than
   `12-BENCHMARK.md`'s literal "over-allocation" concept, which created seats IN EXCESS of demand).
   This tightness plausibly amplifies the slot arm's convergence difficulty; whether the same
   100%-vs-20% reliability gap holds at looser, more realistic staffing ratios is untested by this
   plan and should be checked before a multi-desk rollout decision.

## What This Benchmark Does Not Close

- **The cross-agent seat-displacement gap** filed at
  `.planning/todos/pending/2026-08-13-cross-agent-seat-displacement.md` is *measured* by the same
  kind of numbers this document reports (a plain change-move neighbourhood's limits under
  contention) and is explicitly **not resolved** by this phase. Per `PROJECT.md`'s deliberate
  recording, that todo **stays unlinked to any phase** at Phase 15 close, so a phase close cannot
  auto-sweep it away unresolved.
- **The soft-score plateau** is named and explained here, not remedied. A custom Timefold move
  remains explicitly out of scope for v1.3 (Phase 12 already attempted this and it was withdrawn);
  reopening it is a separate, evidence-led decision for a later milestone.
- **Real-desk-scale reliability** is only indicatively touched (one 30-agent run, one seed,
  non-comparative — D-16). Whether the slot arm's convergence-reliability gap measured on the small
  comparative fixture holds, narrows, or widens at real desk scale and real (looser) staffing
  ratios is not established by this benchmark and is named above as a pre-widening check, not
  assumed.
- **Multi-day and cross-midnight envelope interactions beyond 2 days** are not exercised by the
  comparative fixture (`DAY_COUNT = 2`); `SPIKE-COUPLING.md` open item 4 already named multi-day
  envelopes as untested by its own toy spike, and this benchmark does not close that gap either.

## Solver Comparison Rule (G-15-29) — added 2026-09-01, after the Pass Rule above was committed

Written down here because the argument behind it is exactly the discipline this file's own Pass
Rule already lives by (measure first, commit the rule before reading a number that could bias it)
— this section is a RULE, appended after a session that burned ~12 live solves without one. See
`15-UAT.md` gap `G-15-29` for the full session record this rule closes.

1. Whenever two runs differ in ANY constraint weight, compare VIOLATION COUNTS per constraint,
   never raw hard scores. Evidence: run U scored -4 with `shiftEnvelopeCompliance` at weight 1;
   run V scored -30 at weight 10 and was BETTER — 3 envelope violations against U's 4.
2. A single run is not evidence on this desk. Evidence: byte-identical configuration — period
   2026-01-05..2026-01-11, 08:00-21:00, 60min grid, breaks 60min/`ON_HOUR`/4.0h-min-shift/
   20%-blocked, contracted 8.0h, over/under 500/50, mode `SHIFT` — verified field by field through
   the API, run at the current commit (`a320ca7`) three separate times gave three different
   readings:
       `b88cc98f    0 hard   FEASIBLE`
       `60523b98  -20 hard   NOT FEASIBLE  (2 envelope violations)`
       `aaf17313  -20 hard   NOT FEASIBLE  (2 envelope violations)`
   Same weights, same solver config, three runs — two of the three even agree on the hard score
   (`-20`) while disagreeing on which agent-hours it names (see item 3). Earlier in the same
   investigation, envelope violations across runs all meeting every operator requirement were
   2, 3, 3, 4, 6, 8, with no consistent ordering by configuration. Three runs is three runs — this
   is not claimed as a larger sample than it is.
3. A recurring violation LOCATION is far stronger evidence than a recurring score — the file's own
   standing methodological note, confirmed twice (Sunday 20:00 across runs D and E; weekday 08:00
   across the 2026-09-01 runs). `60523b98`'s two violations were Mon 2026-01-05 08:00 and Tue
   2026-01-06 08:00; `aaf17313`'s two were BOTH Mon 2026-01-05 08:00 (Augusto Correia, Liliana
   Kulish). `Weekend Opening` (08:00-17:00) is the only template reaching 08:00 on a weekday — the
   same "single template is the sole route to a boundary hour" shape already recorded for Sunday
   20:00 (`sunday_2000_is_structural`, `15-UAT.md`).
4. Structural invariants beat scores as a pass/fail gate. Evidence: 0 split shifts, 0 edge breaks
   and non-zero coverage at every edge hour held on ALL THREE of 2026-09-01's live runs including
   the -120 one, while hard score decided nothing. A fourth run (`aaf17313`, the same correct
   500/50 configuration as `b88cc98f`/`60523b98`) also held all three invariants — 0 split shifts
   / 138 agent-days, 0 edge breaks, all four edge hours staffed on all 7 days — strengthening this
   reading rather than complicating it.
5. This extends D-14's model-independent-metrics discipline from "which metric" to "which
   comparison": D-14 already forbids thresholding a soft score across arms evaluating different
   constraint sets; the same logic forbids comparing hard scores across differing weights.

This rule is the documented half of `G-15-29`'s `fix:` in `15-UAT.md`. `SolverQualityGuardTest`
prints it in its own failure output (`buildQualityReport`'s closing paragraph) so a red run tells
the reader the rule rather than assuming they know it.

## Solver Quality Guard (G-15-22) — baseline, added 2026-09-01

Recorded here, dated, appended after the Pass Rule above was committed — the same append-only
discipline as the Solver Comparison Rule section above, and for the same reason: this file's git
history is the evidence its numbers were not adjusted after being read.

**File and command.** `src/test/java/com/wfm/solver/SolverQualityGuardTest.java`, run via
`./gradlew test` — the DEFAULT suite, ungated, no `-D` system property. The existing benchmark
(`ShiftModelBenchmarkTest`, above) sits behind `-Dwfm.benchmark=true` and therefore never runs on
the deploy gate — that is precisely how G-15-22's sevenfold acceptor regression shipped with a
green build. This guard is deliberately NOT behind the same flag.

**Fixture parameters** (`LiveShapeShiftDeskFixture`, plan 15-14):
- 10 agents, 2 days (`DAY_COUNT`, the escape-hatch value — see plan 15-14's Deviations)
- 5 shift templates (Weekend Opening, Early, Flex, Late, Closing), 3 bands each
- 60-minute grid, operating window 08:00-21:00
- Contracted hours 8.00h/agent/day, break 60 minutes, `ON_HOUR` alignment
- Over-allocation 500%, under-allocation 50%
- Pinned weights: `shiftEnvelopeComplianceWeight` 10 hard, `shiftWorkContiguityWeight` 10 hard,
  `bandCapacityWeight` 1 hard, `unassignedAssignmentWeight` 10000 soft
- 5 seeds (1, 2, 3, 4, 5), step-count limit 5,000 (the escape-hatch value)

**Per-seed results** (transcribed verbatim from `15-14-SUMMARY.md`, unmodified build):

| seed | hardScore | totalHardViolations | splitShifts | edgeBreaks | unstaffedEdgeHours | elapsedMillis |
|---|---|---|---|---|---|---|
| 1 | -120 | 3 | 0 | 0 | 0 | 3262 |
| 2 | -10 | 1 | 0 | 0 | 0 | 3477 |
| 3 | -20 | 2 | 0 | 0 | 0 | 3512 |
| 4 | 0 | 0 | 0 | 0 | 0 | 3651 |
| 5 | -10 | 1 | 0 | 0 | 0 | 3370 |

**Per-constraint violation counts across seeds:**

| constraint | seed 1 | seed 2 | seed 3 | seed 4 | seed 5 |
|---|---|---|---|---|---|
| Shift envelope compliance | 2 | 1 | 2 | 0 | 1 |
| Contracted hours (under) | 1 | 0 | 0 | 0 | 0 |

**The ceiling and its arithmetic.** Sorted `totalHardViolations`: `[0, 1, 1, 2, 3]`, median `1.0`.
Rule (committed before the number was read, plan 15-14's P-42): ceiling = median + headroom(2),
floored at 2 when the median is 0. `1.0 + 2 = 3` → `TOTAL_VIOLATION_CEILING = 3`. A reader can
check this arithmetic against the per-seed table above without trusting the constant's javadoc.

**Runtime.** Isolated `SolverQualityGuardTest` class time (unloaded machine, 15-14's original 3
test methods, before this plan's 7 additional proofs): 20.155s (first run), 31.009s (a later run
under accumulated load) — both well under the plan's 90-second budget. Full-suite `./gradlew test`
delta against `HANDOFF.md`'s recorded 8m08s baseline: +147s (10m35s) on the first invocation,
+235s (12m03s) on a forced `--rerun-tasks` invocation — both OVER the 90s ceiling on their face,
reported plainly rather than hidden. Judged NOT attributable to the guard's own cost (the isolated
cost is two orders of magnitude smaller than either delta; the delta grew monotonically across two
runs of byte-identical code, the signature of thermal throttling on a fanless dev machine, not a
fixed per-run cost) — see `15-14-SUMMARY.md`'s Runtime Budget section and coverage `D6` for the
full reasoning and the still-open human-confirmation ask (compare the next CI deploy gate's actual
Test job duration against the 12m53s baseline recorded in `HANDOFF.md`).

**What this guard does NOT cover.** It is a synthetic fixture at a fraction of the live desk's
scale (10 agents / 2 days against the live desk's 138 agent-days across 7 days). It makes no claim
about absolute schedule quality — `TOTAL_VIOLATION_CEILING` is a coarse trip-wire sized to catch
the SEVENFOLD acceptor class of regression this gap was filed over (-9 to -66, G-15-22), not a
fine-grained quality metric. `INV-1`/`INV-2`/`INV-3` (the structural walkers) are the real gate;
`INV-4` (the ceiling) exists only to catch a regression severe enough to move the median by more
than 2. This is stated plainly, not softened, per this file's own established precedent of
reporting both readings and hiding neither.

## Live Weights Discipline (G-15-24) — added 2026-09-02, appended below every section above

Plan 15-18's second fix clause for `G-15-24` (`15-UAT.md`) is a binding rule for anyone analysing
this desk, written here per the same append-only discipline as the two sections above: read the
desk's LIVE `ConstraintWeights` through the API, never `ConstraintWeights.java`'s defaults. The
gate (`SolverService.requireShiftEnvelopeSeatSupply`) now enforces the same rule in code — it
reads the caller-supplied live `weights` object and withdraws the over-allocation-ceiling remedy
whenever `unassignedAssignmentWeight` carries a HARD component — so the written rule and the
executable one point the same way.

**Divergences recorded across `G-15-24`'s own `detail` and `HANDOFF.md` §2, source vs. live,
side by side.** `G-15-24`'s `fix:` field states the two differ "on at least five constraints";
four are documented by name across those two sources as of this writing, reported here plainly
rather than rounded up to match that figure:

| Constraint | Source default (`ConstraintWeights.java`) | Live value (desk `6170be17-...`) | Divergence |
|---|---|---|---|
| `unassignedAssignmentWeight` | `ofSoft(1000)` | `ofHard(10000)` | Kind AND magnitude — soft becomes hard. This is the one the gate's remedy text must read (Task 2). |
| `minStaffingWeight` | `ofSoft(1000)` | `ofHard(10)` | Kind — soft becomes hard, though at a much lower magnitude than the source's soft weight. |
| `contractedHoursUnderWeight` | `ofHard(100)` | `ofHard(10)` | Magnitude only — both hard, live is an order of magnitude lower. |
| `shiftEnvelopeComplianceWeight` | `ofHard(1)` | `10` | Magnitude only — both hard, live is 10x the source default. |
| `shiftWorkContiguityWeight` | `ofHard(10)` | `10` | None currently — V46 (`HANDOFF.md` §4) moved the SOURCE default 100→10 to match the live value that had already been set via the API; recorded here because it was a real divergence before V46 shipped, not because it still is one. |
| `bandCapacityWeight` | `ofHard(1)` | `1` | None — matches. |

Reading `ConstraintWeights.java` alone for this desk would have silently assumed
`unassignedAssignmentWeight` and `minStaffingWeight` are soft and `contractedHoursUnderWeight`/
`shiftEnvelopeComplianceWeight` are an order of magnitude lower than they actually are live —
exactly the assumption `G-15-24`'s `detail` section shows produced the destructive "raise the
ceiling" advice.

## Seat-Supply Distribution Analysis (G-15-31) — added 2026-09-02, appended below every section above

Plan 15-19 did the analysis `G-15-31`'s `fix:` field asked for rather than skipped it: candidate
within-day blocking rules, evaluated against fixtures known to solve and fixtures known to collapse,
with the false-refusal rate measured per rule. Full write-up:
`15-SEAT-SUPPLY-GATE-ANALYSIS.md`. Rule-evaluation test:
`src/test/java/com/wfm/solver/SeatSupplyDistributionAnalysisTest.java`.

**One-line recommendation:** adopt R2 (a proven forced-occupancy necessary condition, zero measured
false refusals across a small four-date-slice corpus) as an additional per-timeslot blocking check
alongside the existing day-wide sum — not implemented by plan 15-19, left for plan 15-20 to act on.
`advisoryOnThinTimeslotDoesNotBlock` stays exactly as shipped; the naive "promote the raw tightest-hour
advisory" rule (R1) is measured to false-refuse 3 of 4 known-solving date-slices in this corpus, which
is the measured justification for that test's own name never having been an accident.
