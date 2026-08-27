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
