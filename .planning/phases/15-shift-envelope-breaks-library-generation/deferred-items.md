# Deferred Items — Phase 15

Out-of-scope discoveries logged during execution, not fixed (per executor scope-boundary rules).

## 15-01

- **`com.wfm.solver.BreakAwareConstructionTest.solverCH_30agents_30minSlots_shouldProduceFeasibleSolution`
  is flaky under concurrent CPU load, unrelated to this plan.** Observed twice during full-suite
  runs while executing 15-01 (hard score `-670` and `-1010` against a `>= -500` tolerance
  threshold on different runs). Passes reliably in isolation (`./gradlew test --tests
  "com.wfm.solver.BreakAwareConstructionTest"` — BUILD SUCCESSFUL). This test exercises the
  slot-mode construction heuristic over `AgentAssignment`/`Timeslot` and has no relationship to
  `ShiftTemplate`, break bands, or any file this plan touches — it is a step-count/time-limited
  solver run whose score threshold is sensitive to machine load during parallel test execution.
  Not auto-fixed per the scope boundary (pre-existing, unrelated file). Worth a follow-up: either
  widen the tolerance or move this fixture off a wall-clock-sensitive step budget.

## SUPERSEDED: "BreakAwareConstructionTest is JVM-state sensitive, not a Phase 15 defect"

**The conclusion recorded here in `1971b3f` was WRONG and is retracted.** It attributed the
failing `BreakAwareConstructionTest` gate to accumulated JVM state in a long-lived test JVM,
and explicitly stated "this is not a Phase 15 regression". It was a Phase 15 regression --
a performance one -- and the gate was telling the truth.

**Actual root causes, both introduced by 15-03 and both now fixed:**

1. `2ee41e2` -- naming an `entitySelector` explicitly replaced the selector config the solver
   would otherwise derive, silently dropping `AgentAssignment`'s `difficultyComparatorClass`
   from the construction heuristic. Cost ~40 hard points. Restoring the
   `cacheType`/`selectionOrder`/`sorterManner` triple recovered exact parity with `61bdd7d`.

2. `90bf3d2` -- the real one. `shiftEnvelopeCompliance` led with
   `forEach(AgentAssignment.class)` and applied the `SchedulingMode.SHIFT` gate only after
   joining. On a slot-mode desk it can never match, yet all 480 seat entities still flowed
   through an extra filter node and a two-key join index on every move: ~3x construction-heuristic
   cost (1049ms -> 266ms) and ~35-40% of local-search throughput (28.6k -> 43.2k moves/sec).
   A wall-clock-bounded search converts lost throughput directly into lost solution quality.

**Result after both fixes:** the 30-agent slot-mode scenario scores `480/480, 0hard/0soft`
in the full suite -- better than the pre-phase baseline of `479/480, -100hard/-1soft`, with
500 points of headroom against the `-500` assertion instead of sitting on a knife's edge.

**Why the wrong conclusion was reached:** isolation runs gave the solver enough CPU to mask a
throughput regression, so the isolation-vs-suite gap was misread as environmental noise rather
than as the symptom of a constraint tax. Lesson: when a wall-clock-bounded solver test degrades,
check `move evaluation speed` in the solver log FIRST -- throughput is the direct signal, and
score is only its downstream shadow.

**What remains true and still worth doing (unchanged recommendation):** the assertion is
wall-clock bounded, so it measures hardware as well as solver quality. It is now passing with
wide margin, but terminating on `stepCountLimit`/`moveCountLimit` instead of `spentLimit` would
make it a deterministic quality gate. Widening the `-500` tolerance is still NOT recommended --
that threshold is what surfaced both defects above.

**The interim gate carve-out recorded in `1971b3f` is WITHDRAWN.** It is no longer needed and
must not be applied: the full suite passes outright (452 tests, 0 failures). Any future failure
of this test should be treated as a real signal and investigated, not waived.

## Wave 4 throughput observation: mode-gating costs ~2x CH time, quality unchanged

Measured on `BreakAwareConstructionTest`'s 30-agent slot-mode scenario after wave 4 merged
(15-06's six `ifExists(Class, filtering(...))` mode gates plus break-clustering and
band-capacity constraints):

| | wave 3 | wave 4 |
|---|---|---|
| CH phase (1) time | 266ms (as reported by the wave-2 fix agent) | **532ms** (measured) |
| LS throughput | 43.2k moves/sec (reported) | **40.6k moves/sec** (measured) |
| CH quality, isolation | `480/480, 0hard/0soft` (measured) | `480/480, 0hard/0soft` (measured) |
| Full-suite canary | `480/480, 0hard/0soft` | `479/480, -320hard/-1soft` |
| Suite duration | 7m47s | 24m31s |

**Not treated as blocking, and here is the reasoning.** Quality per step is IDENTICAL --
both reach `0hard/0soft` in isolation. Only throughput moved. Plan 15-08's benchmark is
specified by XCUT-04 as *seeded, step-count-terminated* A/B runs, so both arms execute the
same step count and a time cost does not bias the comparison. Had the benchmark been
wall-clock bounded, this WOULD have been blocking -- a slot arm carrying a shift-support tax
would have flattered the shift model.

**Two things this does mean:**

1. The canary's margin against its `-500` assertion fell from 500 to 180 points under
   full-suite load. It is drifting back toward the knife's edge it occupied before `90bf3d2`.
   This strengthens the existing recommendation to terminate it on `stepCountLimit` rather
   than `spentLimit`.
2. Mode-gating six previously-mode-agnostic constraints with `ifExists` adds a per-tuple check
   in SLOT mode WITHOUT removing work there (those constraints must still fire on a slot desk).
   If slot-mode solve latency ever becomes a product concern, that is the place to look --
   a `SchedulingMode`-keyed split of the constraint list, evaluated once per solve rather than
   per tuple, would avoid it.

Caveat on provenance: the wave-3 CH/LS figures above are as reported by the wave-2 fix agent
and were not independently re-measured. The QUALITY figures -- which are what the
non-blocking conclusion rests on -- were measured directly at both commits.

## 15-13 (gap closure G-15-10 — final round; two latent defects deliberately not fixed this round)

Both items below were diagnosed during the G-15-10 debug session
(`.planning/debug/shift-mode-break-geometry-ungoverned.md` RC-2;
`.planning/debug/shift-envelope-unsatisfiable-hard.md` T3) and are put out of scope for this round
by **operator ruling OR-2**, not rejected as non-issues. Both are still live and unfixed as of this
plan's completion — verified against current source, not merely carried forward from the debug
report. Neither is fixable inside this plan's own boundary, which forbids any production source
change.

- **Blocked-break-hours has no enforcement point in SHIFT mode.**
  Mechanism: `breakBlockedWindow` (`ScheduleConstraintProvider.java`, ~line 307) is mode-gated off
  for `SchedulingMode.SHIFT` (`ifExists(ScheduleConfig, filtering(cfg.schedulingMode() != SHIFT))`),
  and `ShiftTemplateService.validateBands` (`ShiftTemplateService.java:182-207`) never checks a
  band's offset against the desk's `breakBlockedHours` — it validates only non-negative
  offset/duration, envelope containment of the band itself, capacity, and duplicate-band detection.
  A band at offset `0` (break starts the instant the shift starts) or at
  `offset == envelopeMinutes - duration` (break ends the instant the shift ends) is therefore fully
  legal at save time and scores `0hard` at solve time, producing a full shift with the "break"
  bolted onto its boundary — operationally a late start or an early finish, not a break. In SLOT
  mode this exact shape cost `ofHard(10)`.
  Fix location: this is settled as a **save-time** fix in `ShiftTemplateService`, not a restored
  solver constraint — restoring the gated constraint was considered during this round and rejected
  (it would fight the envelope model and could make an already under-supplied desk permanently
  unsolvable; see `ShiftModeBreakGeometryGuardTest`'s class javadoc, plan 15-13 Task 2). That
  decision is already settled and should not be relitigated by whoever picks this up.
  Visibility: it is invisible in the hard score (scores `0hard`), so it survives this round's D1/D2
  fix (plans 15-09/15-11) completely untouched — the seat-supply gate checks slot COUNTS, not break
  PLACEMENT within a legal shift.
  Live agent-day it explains: Mariami Katcheishvili, 2026-01-10 — 8 consecutive worked hours, zero
  breaks, on the live Stubhub desk (`shift-envelope-unsatisfiable-hard.md` T7, T6).
  Scope: out of scope for this round by operator ruling OR-2, not rejected.

- **A template's envelope is never validated against the desk's operating window at save time.**
  Mechanism: `ShiftTemplateService.validateGridAlignment` (`ShiftTemplateService.java:216-255`)
  checks only that `startTime`, `endTime`, and each band's break boundaries land on the desk's
  timeslot grid — it never checks that the envelope's END fits within the operating window's
  close. `TimeslotBoundsResponse.endTime()` is read by no caller anywhere in `src/main` (confirmed
  by grep, unchanged since the debug session). A template whose envelope runs past the desk's
  closing time saves cleanly with an advisory that literally reads "It will still save".
  Consequence at solve time: plan 15-11's seat-supply gate (`SolverService
  .requireShiftEnvelopeSeatSupply`) now catches the RUNTIME symptom — a desk carrying such a
  template is refused before solving with a named shortfall (`ShiftEnvelopeSupplyInvariantTest
  #envelopeRunningPastClosingTime_nowRefusedByGate`, plan 15-11) — but the operator still cannot be
  told AT SAVE TIME, when they create the template, that it cannot fit. The message belongs at
  save time, where the operator can act on it immediately, not at solve time as a refusal against a
  desk they may not think to connect back to the template edit.
  Concrete starting point: the dead `TimeslotBoundsResponse.endTime()` accessor named above —
  wiring a containment check into `validateGridAlignment` (or a sibling validator) is the natural
  next step for whoever picks this up.
  Scope: out of scope for this round by operator ruling OR-2, not rejected.

### Staleness check (plan 15-13, per its own Task 3 instruction)

Every entry above this section (15-01's flaky-test note, its SUPERSEDED retraction, and the wave-4
throughput observation) was re-read against this round's changes. None references shift-envelope
seat supply, break geometry, or minimum-staffing seat expansion — the surface this round's five
plans (15-09 through 15-13) touched — so none is made stale by this round. No correction was
needed.
