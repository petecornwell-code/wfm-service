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

**Status: EMPTY — placeholder committed ahead of any run, per the discipline stated at the top of
this document.** Task 2 fills in the per-run table, the summary table, the explain() breakdown, the
sizing arithmetic's numbers, and the two candidate default proposals.
