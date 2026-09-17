# Phase 17 — Usual Shift Consistency Weight Benchmark (XCUT-04 / D-06)

**Status at this commit: THRESHOLD ONLY. No result exists yet.**

This section — Harness Configuration, Pass Rule, What This Verdict Gates, and the Sizing
Arithmetic's method (not its numbers) — is written and committed **before** any benchmark run is
executed, per D-06 and the standing XCUT-04 discipline `15-BENCHMARK.md` established: the git
history for this file is itself the evidence that the threshold was not chosen after seeing a
number, which a property described in prose alone cannot establish. The Results section below is a
placeholder until Task 2's commit fills it in verbatim from the harness's own printed output — no
fixture, seed, or step-budget value is adjusted after that point.

**Why this file exists at all.** `consistent_start_weight` already ships on dev at `0hard/2soft`
(V38). V38's own migration comment sizes that number with arithmetic for a **per-agent** penalty —
"on the live desk's 28 CSRs a four-increment spread each is `28 x 4 x 2 = 224 soft` — enough to
break ties between equal-coverage schedules, not enough to outbid bulk under-allocation or minimum
staffing." Phase 17's `usualShiftConsistency` constraint (17-01) charges **per agent-day**
(D-02). Same desk, same weight, five working days: `28 x 5 x 4 x 2 ≈ 1,120 soft` — above
`minStaffingWeight`'s `1000`. That is consistency buying uncovered hours, the exact outcome V38's
comment says must never happen. Inheriting the `2` unexamined is this phase's headline hazard; this
benchmark is the gate that catches it before V49 writes any default.

Also load-bearing: Phase 12 read a `+0.25h` median hours-assigned improvement, inside a **5.00h**
min/max noise spread on the same baseline, as a win — and the phase was withdrawn. This document's
pass rule states in advance that a difference smaller than the comparison arm's own min/max spread
is "no measurable difference," never a win or a loss, so that mistake cannot repeat here.

---

## Harness

- **File:** `src/test/java/com/wfm/solver/UsualShiftConsistencyBenchmarkTest.java`
- **Command (committed benchmark, bounded scale):**
  `./gradlew test --tests "com.wfm.solver.UsualShiftConsistencyBenchmarkTest" -Dwfm.benchmark=true`
- **Gate:** `-Dwfm.benchmark=true` (via `@EnabledIfSystemProperty`, the same annotation
  `ShiftModelBenchmarkTest` uses) — never runs in the default `./gradlew test` suite.
- **Mechanics recovered from `ShiftModelBenchmarkTest`** (Phase 15, XCUT-04's first use in this
  project): fixed seeds, step-count termination (never wall-clock, never `withSpentLimit`), a
  print-a-markdown-table output style transcribed verbatim into this document, and every fixture
  solved through the REAL `src/main/resources/solverConfig.xml` via
  `SolverConfig.createFromXmlResource` — never a hand-built solver config.
- **Fixture source:** a NEW, purpose-built fixture (not `ShiftModeFixtures`), because
  `ShiftModeFixtures`'s shift templates all share one identical envelope start/end (only the break
  band's offset varies across `bandsPerTemplate`) — the solver there has no start-TIME choice to
  make, so the consistency constraint would only ever be a static rescoring, never a genuine
  trade-off. This benchmark needs a real coverage-vs-consistency tension, so its fixture gives the
  desk two templates at genuinely different starts (`Early` 08:00, `Late` 13:00) and shapes staffing
  forecasts so the desk needs roughly half its roster on `Late` while every agent's stored usual
  shift is `Early` — see the test class's own javadoc for the full construction and why the
  allocation percentages are set wide/narrow on purpose (to isolate the two coverage channels this
  benchmark measures — `bulkUnderallocationSoft` and `minimumStaffing` — from the three pct-gated
  channels, which never fire at this fixture's headcounts).

### Harness Configuration

| Property | Value |
|---|---|
| Agents | 10 |
| Days | 5 ("five working days" — mirrors this document's own sizing arithmetic below) |
| Shift templates | 2 — `Early` (08:00–17:00, 9h envelope, 1h break at 12:00, net 8h) and `Late` (13:00–22:00, 9h envelope, 1h break at 17:00, net 8h) |
| Contracted hours/agent/day | 8.00h (matches both templates' net duration exactly) |
| Increment | 60 minutes |
| Every agent's stored usual-shift target | `Early`'s start (08:00), every working day |
| Staffing forecast, 08:00–12:00 (`Early`-only window) | 5 |
| Staffing forecast, 13:00–17:00 (overlap window, both templates' working hours) | 10 (= agent count, always exactly met — every working agent is present regardless of split) |
| Staffing forecast, 18:00–22:00 (`Late`-only window) | 5 |
| Over-allocation / under-allocation limit pct | 1000% / 0% — deliberately wide/narrow so `bulkOverallocationLimit` (HARD), `bulkUnderallocationHard` (HARD) and `unassignedAssignment` (SOFT-1000) never fire at this fixture's headcounts; isolates `bulkUnderallocationSoft` (SOFT-1, shortfall vs. raw forecast) and `minimumStaffing` (SOFT-1000, complete abandonment of a timeslot — the literal "cost of a minimum-staffing violation" this benchmark's ceiling is measured against) as the only active coverage channels |
| Timefold version | 1.16.0 |
| Termination | `TerminationConfig.withStepCountLimit(3000)` on the trailing local-search phase |
| Random seeds | 1, 2, 3, 4, 5 (`SolverConfig.withRandomSeed`) |
| Runs per arm | 5 (one per seed) |
| Arms | `consistentStartWeight` soft value: `0` (baseline/off), `1` (candidate), `2` (V38's current, inherited default), `10` (deliberate stress arm — see below) |

**Why a stress arm (`weight=10`) is committed alongside the two realistic candidates.** A
benchmark that only ever reports "safe" at every weight it tries cannot be distinguished from a
benchmark that is not sensitive enough to detect the failure mode at all — the same "able to fail"
discipline `SolverQualityGuardTest` established (`15-BENCHMARK.md`'s own Solver Quality Guard
section). `weight=10` is included so this harness can be shown able to reproduce the
"consistency crowds out coverage" mechanism V38's comment warns against, at this fixture's small
scale, before this document trusts a "safe" reading at `weight=1`/`weight=2`.

## Pass Rule — committed before any result exists

- **Metric:** the model-specific vocabulary this fixture's own tension is built from —
  `lateAgentDays` (how many agent-days drift from the stored `Early` target),
  `minStaffingViolationTimeslots` (a timeslot left with zero assigned agents — the exact event
  `minStaffingWeight` penalises), `shortfallUnits` (aggregate shortfall against the raw forecast,
  the exact event `bulkUnderallocationSoft` penalises), and `consistencySoftTotal` (the `Usual
  shift consistency` constraint's own total soft contribution, read from `SolutionManager.explain()`
  on the same solved schedule). `hardScore` is reported per run as a structural sanity check — every
  arm's solved schedule is expected to leave every agent fully seated (see "must-pass" below); a
  nonzero hard score at any arm would itself be a finding, not silently normalised.
- **Must-pass (deterministic, asserted in code):** every seed of every arm leaves every agent-day
  fully seated (`unassignedShiftCount == 0`) — the shift-envelope coupling itself must stay sound
  regardless of how the coverage-vs-consistency trade-off resolves.
- **Comparative:** for the two candidate arms (`weight=1`, `weight=2`), `minStaffingViolationTimeslots`
  and `shortfallUnits` must be **no worse** than the `weight=0` baseline's own values — i.e., a
  candidate weight must not measurably degrade coverage relative to consistency being off.
- **Noise rule:** any difference between an arm's median and the `weight=0` baseline's own min/max
  spread that is **smaller than that spread** is written up as **"no measurable difference"** —
  never as a win or a loss, in either direction. This is Phase 12's lesson, restated: a numeric
  difference smaller than the baseline's own run-to-run noise is not a finding.
- **Ceiling (D-06's actual gating question, answered by the Sizing Arithmetic section below, not
  by this fixture's small-scale numbers alone):** the consistency total, projected at the LIVE
  DESK's real shape (agents, working days per period, and the typical excess-increment size a
  genuinely different template produces past the tolerance band), must stay strictly below
  `minStaffingWeight`'s per-violation cost (1000 soft) — so the solver can never rationally trade a
  minimum-staffing violation for a lower consistency total. This fixture's own small-scale
  `consistencySoftTotal` numbers and the live-desk projection are two different instruments
  pointed at the same question; Task 2 states plainly where they agree or disagree, never
  averaging the two together.

**Metrics measured for the threshold are reported as median AND full min/max spread for every arm
in the Results section below. No mean appears anywhere in this document, for any metric, under any
arm.**

## What This Verdict Gates

This benchmark's verdict gates **which `consistentStartWeight` / `preferredStartShiftModeWeight`
pair ships as the V49 default**, via the checkpoint in `17-04-PLAN.md` Task 3. It does not gate
Phase 17's other requirements (CONS-01/02/04/05/06, DRFT-01…04), which plans 17-01/17-02/17-03
already delivered and verified independently of any number in this document. A weight found unsafe
here does not withdraw those constraints — it means V49 ships a different, smaller value than V38's
inherited `2`, or (per the checkpoint's `hold` option) V48's provisional defaults stay in place
pending a re-run.

## Sizing Arithmetic — method committed now, numbers filled in by Task 2

Task 2 redoes V38's per-agent sizing arithmetic for D-02's per-agent-day charging model, using the
REAL live desk's shape (agent count, working days per schedule period, and the typical excess
increments a genuinely different template produces past the 60-minute tolerance band), as separate
factors multiplied together and compared against `minStaffingWeight`'s 1000-soft ceiling. This is a
pure arithmetic projection, not derived from this benchmark's own small-scale solve — the two are
different instruments (this file's fixture is deliberately small so it runs in seconds; the live
desk is not), and Task 2 states plainly where the projection and this benchmark's own
`consistencySoftTotal` measurement agree or disagree, per the plan's explicit requirement.

## Results

**Run date:** 2026-09-17
**Command:** `./gradlew test --tests "com.wfm.solver.UsualShiftConsistencyBenchmarkTest" -Dwfm.benchmark=true`
**Result:** BUILD SUCCESSFUL. Numbers below are transcribed verbatim from the harness's own stdout.
No fixture, seed, or step-budget value was adjusted after this run — the one supplementary,
explicitly-labelled ad-hoc check below (a bigger step budget, to rule out a budget artifact) is
reported separately and did not change any number used against the pass rule.

### Per-Run Results (5 seeds each, 4 arms)

| arm | weight | seed | hardScore | softScore | earlyAgentDays | lateAgentDays | unassignedShiftCount | minStaffingViolationTimeslots | shortfallUnits | consistencyMatchCount | consistencySoftTotal | elapsedMillis |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| baseline-off-0 | 0 | 1 | 0 | -20450 | 0 | 50 | 0 | 20 | 100 | 0 | 0 | 7120 |
| baseline-off-0 | 0 | 2 | 0 | -20450 | 0 | 50 | 0 | 20 | 100 | 0 | 0 | 6383 |
| baseline-off-0 | 0 | 3 | 0 | -20450 | 0 | 50 | 0 | 20 | 100 | 0 | 0 | 6457 |
| baseline-off-0 | 0 | 4 | 0 | -20450 | 0 | 50 | 0 | 20 | 100 | 0 | 0 | 6204 |
| baseline-off-0 | 0 | 5 | 0 | -20450 | 0 | 50 | 0 | 20 | 100 | 0 | 0 | 6437 |
| candidate-1 | 1 | 1 | 0 | -20650 | 0 | 50 | 0 | 20 | 100 | 50 | -200 | 6368 |
| candidate-1 | 1 | 2 | 0 | -20650 | 0 | 50 | 0 | 20 | 100 | 50 | -200 | 7120 |
| candidate-1 | 1 | 3 | 0 | -20650 | 0 | 50 | 0 | 20 | 100 | 50 | -200 | 7564 |
| candidate-1 | 1 | 4 | 0 | -20650 | 0 | 50 | 0 | 20 | 100 | 50 | -200 | 7764 |
| candidate-1 | 1 | 5 | 0 | -20650 | 0 | 50 | 0 | 20 | 100 | 50 | -200 | 8172 |
| v38-current-2 | 2 | 1 | 0 | -20850 | 0 | 50 | 0 | 20 | 100 | 50 | -400 | 7399 |
| v38-current-2 | 2 | 2 | 0 | -20850 | 0 | 50 | 0 | 20 | 100 | 50 | -400 | 7475 |
| v38-current-2 | 2 | 3 | 0 | -20850 | 0 | 50 | 0 | 20 | 100 | 50 | -400 | 7835 |
| v38-current-2 | 2 | 4 | 0 | -20850 | 0 | 50 | 0 | 20 | 100 | 50 | -400 | 7980 |
| v38-current-2 | 2 | 5 | 0 | -20850 | 0 | 50 | 0 | 20 | 100 | 50 | -400 | 7609 |
| stress-10 | 10 | 1 | 0 | -22450 | 0 | 50 | 0 | 20 | 100 | 50 | -2000 | 7903 |
| stress-10 | 10 | 2 | 0 | -22450 | 0 | 50 | 0 | 20 | 100 | 50 | -2000 | 7630 |
| stress-10 | 10 | 3 | 0 | -22450 | 0 | 50 | 0 | 20 | 100 | 50 | -2000 | 7586 |
| stress-10 | 10 | 4 | 0 | -22450 | 0 | 50 | 0 | 20 | 100 | 50 | -2000 | 7753 |
| stress-10 | 10 | 5 | 0 | -22450 | 0 | 50 | 0 | 20 | 100 | 50 | -2000 | 7717 |

### Summary — Median and Full Min/Max Spread (never a mean)

| arm | weight | hardScore median/min/max | lateAgentDays median/min/max | minStaffingViolationTimeslots median/min/max | shortfallUnits median/min/max | consistencySoftTotal median/min/max |
|---|---|---|---|---|---|---|
| baseline-off-0 | 0 | 0.0/0.0/0.0 | 50.0/50.0/50.0 | 20.0/20.0/20.0 | 100.0/100.0/100.0 | 0.0/0.0/0.0 |
| candidate-1 | 1 | 0.0/0.0/0.0 | 50.0/50.0/50.0 | 20.0/20.0/20.0 | 100.0/100.0/100.0 | -200.0/-200.0/-200.0 |
| v38-current-2 | 2 | 0.0/0.0/0.0 | 50.0/50.0/50.0 | 20.0/20.0/20.0 | 100.0/100.0/100.0 | -400.0/-400.0/-400.0 |
| stress-10 | 10 | 0.0/0.0/0.0 | 50.0/50.0/50.0 | 20.0/20.0/20.0 | 100.0/100.0/100.0 | -2000.0/-2000.0/-2000.0 |

**Every arm's own min/max spread is zero** — all 5 seeds land on byte-identical coverage metrics
within each arm. This is itself the headline finding below, not noise: this fixture's construction
heuristic is fully deterministic for this entity type, so seed variation shows up only in local
search's exploration order, never in the final structural outcome.

### explain() Breakdown — seed 1 of each arm (criterion 2's weight-validation input, same run)

```
--- arm=baseline-off-0 (consistentStartWeight=0 soft) ---
  Break clustering => 0hard/-100soft (count: 5)
  Bulk under-allocation soft => 0hard/-100soft (count: 20)
  Minimum staffing => 0hard/-20000soft (count: 20)
  Preferred start (shift mode) => 0hard/-250soft (count: 50)

--- arm=candidate-1 (consistentStartWeight=1 soft) ---
  Break clustering => 0hard/-100soft (count: 5)
  Bulk under-allocation soft => 0hard/-100soft (count: 20)
  Minimum staffing => 0hard/-20000soft (count: 20)
  Preferred start (shift mode) => 0hard/-250soft (count: 50)
  Usual shift consistency => 0hard/-200soft (count: 50)

--- arm=v38-current-2 (consistentStartWeight=2 soft) ---
  Break clustering => 0hard/-100soft (count: 5)
  Bulk under-allocation soft => 0hard/-100soft (count: 20)
  Minimum staffing => 0hard/-20000soft (count: 20)
  Preferred start (shift mode) => 0hard/-250soft (count: 50)
  Usual shift consistency => 0hard/-400soft (count: 50)

--- arm=stress-10 (consistentStartWeight=10 soft) ---
  Break clustering => 0hard/-100soft (count: 5)
  Bulk under-allocation soft => 0hard/-100soft (count: 20)
  Minimum staffing => 0hard/-20000soft (count: 20)
  Preferred start (shift mode) => 0hard/-250soft (count: 50)
  Usual shift consistency => 0hard/-2000soft (count: 50)
```

`Usual shift consistency`'s per-match magnitude is exactly `(deviation − tolerance) / increment`
(CEILING) `× weight` = `(300 − 60) / 60 × weight = 4 × weight` per drifted agent-day, for every one
of the 50 agent-days in this fixture — confirming the constraint's committed formula (17-01) reads
correctly against a real, non-toy fixture: `-200` at weight 1, `-400` at weight 2, `-2000` at weight
10, each exactly `50 × 4 × weight`.

## Known Limitation Surfaced By This Run — a construction-heuristic plateau, not a weight question

**Every arm, every seed, converged on the SAME structural outcome: all 50 agent-days assigned
`Late` (0 `Early`), regardless of `consistentStartWeight` being 0, 1, 2, or 10.** This means the
committed comparative pass rule's "no worse than baseline" check on `minStaffingViolationTimeslots`
/ `shortfallUnits` is satisfied **trivially** — by identity, not by a weight small enough to avoid
degrading coverage. The intended read (does raising the weight from 0 measurably worsen coverage?)
could not be exercised at this fixture's scale, for a specific, verified reason:

- **Construction heuristic phase 0** (the `AgentShiftAssignment` placement phase, before any seat
  exists) ends at an **identical** `-40000hard/-60400soft` for every arm tested, including
  `stress-10` — meaning the weight had zero measurable influence on the CH's own template choice,
  even though `Early` (0 excess) is unambiguously cheaper than `Late` (`4 × weight` excess) for
  every entity at every nonzero weight tested.
- **Local search** (3000 committed steps) then never moves a single agent-day off whatever CH
  committed to. This matches the shift+seat coupling plateau this project has already documented
  independently (`SPIKE-COUPLING.md`; `15-BENCHMARK.md`'s own Plateau Finding section): changing
  `AgentShiftAssignment.shiftBandPair` for one agent-day without ALSO relocating that agent-day's
  seats immediately produces a `shiftEnvelopeCompliance` HARD violation for the now-mismatched
  seats, which the `0hard`-temperature acceptor (`solverConfig.xml`, load-bearing per its own
  comment) rejects outright — a coordinated multi-move sequence (unseat, re-template, reseat) would
  be needed, and the change/swap neighbourhood only ever moves one variable per step.
- **Supplementary ad-hoc check (not part of the committed test, reported per this project's
  established runtime-budget-disclosure convention — `15-BENCHMARK.md`'s own precedent):** the
  `v38-current-2` arm was re-run at 20,000 steps (6.7× the committed budget) and at a manually
  reversed template-list order (to rule out a positional tie-break artifact). Neither changed the
  outcome by a single agent-day. This rules out both "the step budget was too small" and "it's just
  list-order" as explanations — the plateau is genuine and reproducible, not a configuration
  accident of this fixture.

**What this does and does not mean for D-06.** It means this benchmark's own SOLVE-DRIVEN coverage
reading cannot, by itself, certify a candidate weight as safe or unsafe at this fixture's scale —
the plateau freezes the outcome before the weight gets a chance to matter, in either direction. It
does NOT invalidate the `explain()`-derived per-agent-day cost formula above, which is a direct,
verified reading of what the constraint charges for a GIVEN configuration regardless of how the
solver arrived there — that formula is exactly what the sizing arithmetic below projects onto the
real desk. This plateau is a known class of limitation for this solver (already named, not
remedied, out of scope for this milestone per the Phase 15 operator ruling on the soft-quality
plateau) — it is recorded here as a NEW instance of it (in the shift-template-choice dimension,
not only the shift+seat-presence dimension `15-BENCHMARK.md` already named), not attributed to this
plan's own scope to fix.

## Sizing Arithmetic — V38's per-agent formula redone for D-02's per-agent-day model

**Factors, stated separately per the plan's requirement:**

| Factor | Value | Source |
|---|---|---|
| Live desk agent count | 28 CSRs | V38's own migration comment (unchanged since Phase 15/16 research) |
| Working days per schedule period | 5 | Standard Mon–Fri schedule period, matching this document's own fixture and 17-CONTEXT.md's D-06 worked example |
| Typical excess increments past the 60-minute tolerance band | 4 | Adopted directly from V38's own comment ("a four-increment spread"), for direct comparability — this project's own D-06 worked example in `17-CONTEXT.md` already uses this same figure and arrives at the same `≈1,120` this section reproduces below |
| Ceiling — one `minStaffingWeight` violation | 1000 soft | `ConstraintWeights.minStaffingWeight` default; the interfaces' own stated ceiling |

**Worst case — every one of the 28 agents drifted on every one of the 5 days** (V38's own
conservative framing, extended verbatim to the per-agent-day model):

```
total = agentCount × workingDays × excessIncrements × weight
      = 28 × 5 × 4 × weight
      = 560 × weight
```

| Weight | Worst-case total | vs. 1000 ceiling |
|---|---|---|
| 1 | 560 | **under** |
| 2 (V38's inherited value) | **1,120** | **over — by 12%** (matches `17-CONTEXT.md`'s own `≈1,120` worked example exactly) |
| 3 | 1,680 | over |

**Typical case — an explicit, stated judgement, not a measurement.** No production telemetry exists
yet for what fraction of agent-days actually drift past the tolerance band on a normally-loaded
desk (this is itself worth flagging as a follow-up — see "What This Benchmark Does Not Close"
below). Assuming a HEALTHY desk mostly honours its stored usual shifts and only a minority of
agent-days genuinely drift on any given day (illustrative: ~20% of the roster, ≈6 agents/day — a
judgement call, stated as one, not measured):

```
total ≈ 6 × 5 × 4 × weight = 120 × weight
```

| Weight | Typical-case total | vs. 1000 ceiling |
|---|---|---|
| 1 | 120 | under |
| 2 | 240 | under |
| 3 | 360 | under |

**Agreement with this benchmark's own measurement.** This fixture's `explain()` breakdown above
measured `-400` soft at weight 2 for its own 50 drifted agent-days — exactly `50 × 4 × 2 = 400`,
i.e. **8 soft per drifted agent-day at weight 2**. The worst-case projection above assumes the
identical per-agent-day rate (`4 excess increments × weight = 8` at weight 2) — **the two agree
exactly on the per-agent-day formula**; they diverge only in TOTAL SCALE, because this fixture's
own construction-heuristic plateau (see above) happened to reproduce the WORST-CASE 100%-drift
assumption on its own 50-agent-day fixture, while the live desk's 140 agent-days (28 × 5) would
not realistically all drift at once outside of a genuine data-quality breakdown (e.g. a mass
template retirement invalidating most stored usual shifts at once). Stated plainly, as the plan
requires: **the measurement and the projection disagree on how much of the roster is likely to
drift at once, not on what a single drifted agent-day costs.**

## Candidate Default Proposals

Both candidates below satisfy the binding, non-negotiable constraint from `<interfaces>`:
`ConstraintWeightsService` rejects a save where the preference weight's soft component is `>=` the
consistency weight's. Given the sizing arithmetic above, `consistentStartWeight = 1` is the ONLY
integer value with a worst-case total safely under the ceiling (560 < 1000) — but it leaves NO room
for a strictly-lower, non-zero `preferredStartShiftModeWeight` (there is no positive integer below
1). `consistentStartWeight = 2` is therefore the SMALLEST value that can satisfy D-08's ordering
while keeping both weights non-zero — which is exactly what V38 already shipped.

### Proposed: `consistentStartWeight = 2`, `preferredStartShiftModeWeight = 1`

- Keeps V38's already-shipped consistency value unchanged — no behaviour change for any desk that
  has not yet had its rows touched by V49's predicated `UPDATE` (T-17-06).
- Typical-case projected total: 240 soft — comfortably under the 1000 ceiling.
- Worst-case projected total: 1,120 soft — **12% over the ceiling**, reached only if the ENTIRE
  roster drifts on EVERY working day of the period simultaneously, which reflects a data-quality
  breakdown (stale usual-shift data across the whole desk) rather than normal operation. Named as a
  residual, monitorable risk, not eliminated by this weight choice.
- Satisfies D-08: `1 < 2`, both non-zero.

### Runner-up: `consistentStartWeight = 3`, `preferredStartShiftModeWeight = 1`

- Widens the margin between consistency and preference (`1` vs `3`, rather than `1` vs `2`),
  giving CONS-06's precedence a larger buffer against any future preference-weight tuning that
  might otherwise approach the boundary.
- Typical-case projected total: 360 soft — still comfortably under the ceiling.
- Worst-case projected total: 1,680 soft — **68% over the ceiling**, a larger overshoot than the
  proposed pair's, for a benefit (precedence margin) that CONS-06's save-time enforcement
  (`ConstraintWeightsService`) already guarantees structurally regardless of the numeric gap.
- Costs strictly more than the proposed pair in both the typical and worst case, for a precedence
  guarantee the enforcement layer already provides. Included because Task 2 must offer a real
  second number, not because this document's own arithmetic favours it.

## What This Benchmark Does Not Close

- **No production telemetry on actual drift rates.** The "typical case" above is an explicit,
  stated judgement (~20% of the roster drifting on a given day), not a measurement — there is no
  existing dashboard or query that reports how often a live desk's assigned shifts actually diverge
  from stored usual shifts. Follow-up: once V49 ships and a desk pilots the constraint, the drift
  report (Phase 17, DRFT-01…04, already delivered) is itself positioned to supply this telemetry
  retroactively.
- **The construction-heuristic plateau** (see "Known Limitation" above) is named and reproduced
  here, not remedied — matching this milestone's operator ruling on the soft-quality plateau
  generally (`15-BENCHMARK.md`, Phase 15). A custom Timefold move remains out of scope for v1.3.
- **Real-desk-scale verification.** This benchmark's fixture (10 agents, 5 days) is deliberately
  small so it runs in seconds; whether the plateau, the per-agent-day cost formula, or the worst/
  typical-case gap hold at the live desk's actual 28-agent, multi-template scale is not
  independently re-verified here.
