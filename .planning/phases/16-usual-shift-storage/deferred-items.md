# Phase 16 — Deferred Items

Out-of-scope discoveries logged during execution, per the executor's scope-boundary rule
(auto-fix only issues directly caused by the current task's changes).

## Plan 16-02

- **`MultiDayConstraintDiagnosticTest#multiDay_10agents_11days_shouldScoreZeroHard` is a flaky,
  wall-clock-time-boxed solver test, unrelated to this plan.** Observed as the sole failure
  (`-6011` vs. the `-5000` tolerance) during the full-suite run required by Task 3's verification
  gate. The test time-boxes the local-search phase (`Duration.ofSeconds(120)`) rather than
  terminating on a fixed step count, so under contention from the rest of the suite running
  concurrently it makes less progress and reports a worse score — exactly the wall-clock-variance
  class of flake the project's own Phase 12 notes warn about ("must be judged by seeded,
  step-count-terminated A/B runs... run-to-run variance exceeds effect size"). Confirmed flaky by
  re-running the class in isolation immediately afterward: green in `3m 34s` with no code changes.
  `src/test/java/com/wfm/solver/MultiDayConstraintDiagnosticTest.java` is solver-package code this
  plan's phase-specific constraints explicitly forbid touching ("Do not touch solver code").
  Out of scope for plan 16-02; not fixed here.
