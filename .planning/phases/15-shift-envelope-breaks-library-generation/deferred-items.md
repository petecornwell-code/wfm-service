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
