# Phase 12 — Atomic Shift Move Benchmark Evidence

**Run date:** 2026-08-13
**Harness:** `src/test/java/com/wfm/solver/AtomicShiftMoveBenchmarkTest.java`
**Command:** `./gradlew test --tests com.wfm.solver.AtomicShiftMoveBenchmarkTest -Dwfm.benchmark=true`
**Result:** BUILD SUCCESSFUL, 2/2 tests passed (the one deterministic must-pass assertion —
`agentDaysNeedingBreakWithNone` zero across all five with-move runs — held in the phase-gating
scenario), total wall time 25s.

Numbers below are recorded exactly as the harness printed them. No fixture, seed, or step-budget
value was adjusted after seeing the result.

---

## Harness Configuration

| Property | Value |
|---|---|
| Agents | 11 |
| Increment | 15 minutes |
| Contracted hours/agent | 8.00h (32 required work slots) |
| Break duration | 60 minutes (4 slots) |
| Break blocked window | 1.00h at each end |
| Break start alignment | `ON_QUARTER_HOUR` |
| Timeslots | 48 (08:00–20:00) |
| Demand per timeslot | 2 FTE |
| Over-allocation (reference scenario) | 400% → 8 seats/timeslot, 384 total seats |
| Over-allocation (conservative variant) | 130% → 3 seats/timeslot, 144 total seats |
| Hours needed | 88.00h (11 × 8.00h) |
| Timefold version | 1.16.0 |
| Termination | `TerminationConfig.withStepCountLimit(2000)` — step-count, never wall-clock |
| Random seeds | 1, 2, 3, 4, 5 (`SolverConfig.withRandomSeed`) |
| Runs per configuration | 5 (baseline: change+swap only; with-move: change+swap+`AtomicShiftMoveFactory`) |

Both configurations are built by near-identical `SolverConfig` builders (`baselineConfig(seed)` /
`withMoveConfig(seed)`) that differ only in one appended `MoveListFactoryConfig` registering
`AtomicShiftMoveFactory` — no other call in the chain differs.

---

## Per-Run Results — 400% Over-allocation Reference Scenario (phase-gating)

| Config | Seed | Hard score | Soft score | Hours assigned | Hours needed | Agent-days needing break, none | Agent-days at pinned wall | Elapsed (ms) |
|---|---|---|---|---|---|---|---|---|
| baseline | 1 | -5550 | 0 | 79.25 | 88.00 | 0 | 1 | 1525 |
| baseline | 2 | -5760 | 0 | 75.25 | 88.00 | 0 | 3 | 1520 |
| baseline | 3 | -5460 | 0 | 79.25 | 88.00 | 0 | 2 | 1016 |
| baseline | 4 | -5520 | 0 | 80.25 | 88.00 | 0 | 0 | 924 |
| baseline | 5 | -5540 | 0 | 79.25 | 88.00 | 0 | 2 | 1040 |
| with-move | 1 | -5510 | 0 | 84.00 | 88.00 | 0 | 0 | 994 |
| with-move | 2 | -5350 | 0 | 75.75 | 88.00 | 0 | 1 | 1090 |
| with-move | 3 | -5140 | 0 | 79.50 | 88.00 | 0 | 2 | 1225 |
| with-move | 4 | -5040 | 0 | 79.50 | 88.00 | 0 | 2 | 1343 |
| with-move | 5 | -5320 | 0 | 83.75 | 88.00 | 0 | 1 | 1066 |

## Summary — 400% Reference Scenario

Median and full min/max spread, never a mean — the baseline distribution is bimodal.

| Config | Hard score median | Hard score min | Hard score max | Hours assigned median | Hours assigned min | Hours assigned max |
|---|---|---|---|---|---|---|
| baseline | -5540.0 | -5760.0 | -5460.0 | 79.25 | 75.25 | 80.25 |
| with-move | -5320.0 | -5510.0 | -5040.0 | 79.50 | 75.75 | 84.00 |

Baseline hours-assigned spread (min→max): 80.25 − 75.25 = **5.00h**.
With-move median minus baseline median: 79.50 − 79.25 = **0.25h**.

---

## Threshold Assessment (against `12-VALIDATION.md` § "Pass / Fail Thresholds")

| # | Threshold | Measured | Verdict |
|---|---|---|---|
| 1 | (must-pass) Median hours assigned (with-move) exceeds median hours assigned (baseline) by more than the baseline min/max spread | With-move median 79.50h vs baseline median 79.25h → margin **0.25h**. Baseline spread is **5.00h** (75.25h–80.25h). 0.25h does not exceed 5.00h. | **FAIL** — the fix's effect lands well inside the existing noisy band; this is not merely a near-miss, the margin is 5% of the required spread. |
| 2 | (must-pass) Zero agent-days across all 5 post-fix runs show `effectiveHours > breakMinShiftHours` with zero breaks | With-move `agentDaysNeedingBreakWithNone` = 0, 0, 0, 0, 0 across seeds 1–5 | **PASS** — asserted deterministically in code (`AtomicShiftMoveBenchmarkTest`), the harness build failed if any run had been non-zero. |
| 3 | (must-pass) `AtomicShiftMoveFullAssertTest` green — no score corruption under `FULL_ASSERT` | Re-verified as part of this plan's full-suite run (`./gradlew build`) with no source changes to that test or to `AssignSeatMove`/`AtomicShiftMoveFactory`/`ShiftWindowFinder` since plan 12-02 | **PASS** |
| 4 | (must-pass) `BreakAwareConstructionTest` still green, unmodified — proves the new move composed with, not displaced, change/swap | `git diff --exit-code src/test/java/com/wfm/solver/BreakAwareConstructionTest.java` exits 0 (file untouched since 12-01); re-verified green as part of this plan's full-suite run | **PASS** |
| 5 | (should-pass, non-blocking) Post-fix hard score regresses below the best observed *historical* baseline hard score (-4,930, ROADMAP, wall-clock-terminated, unseeded) in no more than 1 of 5 with-move runs | With-move hard scores: -5510, -5350, -5140, -5040, -5320 — **all five** are below -4,930 | **FAIL** (literal reading against the ROADMAP's historical number) — see caveat below |

**Caveat on threshold #5 — two readings, reported without softening either:**

- **Literal reading**, comparing against the ROADMAP's historical `-4,930` figure exactly as
  `12-VALIDATION.md` states it: **5 of 5** with-move runs regress below it. This is a clean FAIL
  of the "no more than 1 of 5" bar, not a near-miss.
- **Within-run reading**, comparing with-move hard scores against *this run's own* best baseline
  hard score (-5460, the max/least-negative of the 5 seeded baseline runs in this harness, the
  directly comparable step-count-terminated number): -5510 (worse), -5350 (better), -5140
  (better), -5040 (better), -5320 (better) → only **1 of 5** with-move runs is worse than this
  run's own best baseline. That reading PASSES the "no more than 1 of 5" bar.
- **Why both readings are reported:** the historical -4,930 was measured under
  `EnvironmentMode.REPRODUCIBLE` with wall-clock (`withSpentLimit`) termination and no fixed seed
  — 12-RESEARCH.md's own root-cause finding is that this termination mode is *not* reproducible
  run-to-run under CPU-time variance, which is exactly why this harness exists. The -4,930 number
  is therefore not a like-for-like comparison target for a step-count-terminated run. The
  within-run reading against this harness's own baseline is the directly comparable number; the
  literal reading against the historical figure is what `12-VALIDATION.md`'s threshold text says
  verbatim. Both are true simultaneously and are handed to the operator as-is at the Task 3
  checkpoint — this document does not adjudicate which one governs.

**Overall: 3 of 5 thresholds PASS outright (2, 3, 4); 1 FAILS clearly (1); 1 depends on which of
two legitimate readings is applied (5).** The must-pass median-vs-spread threshold (#1) is the one
the whole phase turns on per `12-VALIDATION.md`, and it is a clear FAIL in this measurement.

---

## Conservative Variant — 130% Over-allocation (informational only, not phase-gating)

Answers `12-RESEARCH.md` Open Question 2: does the benefit depend on the 400% reference
scenario's generous seat headroom? At 130% (the `Schedule` class's own default), total seat
capacity is 144 (3 seats × 48 timeslots) against 352 slot-fills needed for all 11 agents to reach
8.00 contracted hours (11 × 32 required work slots). Seat capacity is the binding constraint
before any move-selection question is reached.

### Per-Run Results — 130% Conservative Variant

| Config | Seed | Hard score | Soft score | Hours assigned | Hours needed | Agent-days needing break, none | Agent-days at pinned wall | Elapsed (ms) |
|---|---|---|---|---|---|---|---|---|
| baseline | 1 | -20938 | -48000 | 36.00 | 88.00 | 0 | 1 | 1289 |
| baseline | 2 | -20938 | -48000 | 36.00 | 88.00 | 1 | 2 | 1257 |
| baseline | 3 | -20958 | -48000 | 36.00 | 88.00 | 2 | 0 | 1299 |
| baseline | 4 | -20978 | -48000 | 36.00 | 88.00 | 0 | 5 | 1190 |
| baseline | 5 | -20948 | -48000 | 36.00 | 88.00 | 1 | 1 | 1315 |
| with-move | 1 | -20968 | -48000 | 36.00 | 88.00 | 0 | 2 | 1545 |
| with-move | 2 | -20928 | -48000 | 36.00 | 88.00 | 1 | 3 | 1244 |
| with-move | 3 | -20958 | -48000 | 36.00 | 88.00 | 0 | 4 | 1326 |
| with-move | 4 | -20948 | -48000 | 36.00 | 88.00 | 1 | 1 | 1348 |
| with-move | 5 | -20948 | -48000 | 36.00 | 88.00 | 1 | 1 | 1441 |

### Summary — 130% Conservative Variant

| Config | Hard score median | Hard score min | Hard score max | Hours assigned median | Hours assigned min | Hours assigned max |
|---|---|---|---|---|---|---|
| baseline | -20948.0 | -20978.0 | -20938.0 | 36.0 | 36.0 | 36.0 |
| with-move | -20948.0 | -20968.0 | -20928.0 | 36.0 | 36.0 | 36.0 |

**Reading:** hours assigned is identical (36.0h, all 10 runs, both configs) and hard-score medians
are identical (-20948.0). At 130% over-allocation the atomic shift move produces **no measurable
benefit** over the existing change/swap pool — both configurations are seat-starved before either
one reaches a state where the move's "find a legal contiguous window among free-or-self-held
seats" capability is even the limiting factor. This is a clear demonstration that the 400%
reference scenario's benefit (such as it is — see threshold #1 above) does **not** transfer to a
realistic over-allocation setting. **This is the trigger named in `12-RESEARCH.md`'s Open
Question 1/2 recommendation and in plan 12-02's own scoping note: cross-agent seat displacement
(not just empty-seat-plus-own-seat search) is required follow-up work**, tracked as a named gap
rather than closed by this phase.

---

## Historical Context — ROADMAP Pre-Fix Evidence (not directly comparable)

`ROADMAP.md`'s Phase 12 section records three wall-clock-terminated, unseeded 5-minute runs at
the same 400% reference scenario, prior to this phase's fix:

| Run | Hard score | Hours assigned (of 88 needed) |
|-----|-----------|-------------------------------|
| 1   | -4,930    | 80.50 |
| 2   | -29,810   | 30.25 |
| 3   | -29,810   | 35.50 |

Two of three runs converged on the identical -29,810/30.25h — evidence of a structural attractor,
not noise; the -4,930/80.5h run was the outlier. These three runs were measured under
`EnvironmentMode.REPRODUCIBLE` with `TerminationConfig.withSpentLimit` (wall-clock) and no fixed
seed. Per 12-RESEARCH.md's own root-cause finding, reproducibility under that mode explicitly does
**not** hold across runs with materially different allocated CPU time — this is the exact defect
this benchmark harness (fixed seed + step-count termination) was built to correct for. **The
historical numbers above are not a like-for-like comparison against the in-run baseline column
in this document; the in-run baseline (median 79.25h, min/max 75.25h/80.25h) is the comparison
that actually controls for the confound and is what threshold #1 above is evaluated against.**
The historical -4,930 hard score is still the literal number `12-VALIDATION.md`'s should-pass
threshold #5 names, which is why both the literal and within-run readings are reported above
rather than only one.
