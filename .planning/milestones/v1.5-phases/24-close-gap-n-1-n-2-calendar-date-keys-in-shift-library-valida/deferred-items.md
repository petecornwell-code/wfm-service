## Deferred Items

- MultiDayConstraintDiagnosticTest.multiDay_5agents_5days_shouldScoreZeroHard failed the 24-03 phase-gate full-suite run (hard score -610 against a -500 floor) and passed in isolation (2 tests, 0 failures, 3m44s). This is the wall-clock time-boxed solver test already acknowledged at the v1.3 close as flaky under suite contention (16/deferred-items.md); it builds its own hand-made schedule and runs the Timefold solver for a fixed 90 s, and none of this phase's changes sit on its path (the only solver-package edit is a comment in AgentAssignmentDifficultyComparator). Not fixed here: out of scope, and widening the -500 floor is not recommended. Recommendation stands: terminate on stepCountLimit rather than a wall-clock limit.
  status: acknowledged
