---
phase: 12-atomic-shift-move
plan: 02
subsystem: solver
tags: [timefold, custom-move, local-search, break-geometry]
dependency-graph:
  requires:
    - ShiftWindowFinder (12-01, single-window tracer)
    - AssignSeatMove (12-01, reversible single-seat move)
    - AtomicShiftMoveFactory (12-01, zero-assignment-only tracer)
  provides:
    - ShiftWindowFinder.findWindows full enumeration
    - AtomicShiftMoveFactory.buildSeatMoves (atomic clear-and-replace rewrite)
    - AtomicShiftMoveFactory.MAX_WINDOWS_PER_AGENT_DAY bound
  affects:
    - src/main/java/com/wfm/solver/AssignSeatMove.java
tech-stack:
  added: []
  patterns:
    - "Deterministic down-sampling by fixed stride (ceil(size/N)) rather than truncation, to keep a bounded candidate pool spread across the search space"
    - "Package-private contract method (buildSeatMoves) exposed specifically so a unit test can assert move shape without depending on CompositeMove internals"
key-files:
  created:
    - src/test/java/com/wfm/solver/ShiftWindowFinderTest.java
    - src/test/java/com/wfm/solver/AtomicShiftMoveFactoryTest.java
  modified:
    - src/main/java/com/wfm/solver/ShiftWindowFinder.java
    - src/main/java/com/wfm/solver/AtomicShiftMoveFactory.java
    - src/main/java/com/wfm/solver/AssignSeatMove.java
decisions:
  - "ShiftWindowFinder.findWindows returns every legal (span start, break offset) pair in one call, ordered by span start ascending then break offset ascending, rather than the tracer's earliest-only behaviour — the benchmark harness in 12-03 depends on this ordering being stable across repeated calls on identical input"
  - "Duplicate seats sharing a timeslot start time are deduplicated by lowest Timeslot.getId(), not first-seen-in-list, so the same input list produces identical seat selections regardless of iteration order"
  - "AtomicShiftMoveFactory's candidate pool per agent-day now includes seats the agent already holds (not just free seats) — this is what lets a single composite move both unassign stale seats and assign new ones in the same atomic step"
  - "MAX_WINDOWS_PER_AGENT_DAY = 8 with fixed-stride down-sampling (ceil(size/8)-th element) rather than truncating to the first 8 — keeps retained candidates spread across span starts instead of clustering at the earliest one, while staying deterministic across repeated calls"
  - "AssignSeatMove.getPlanningValues() switched from List.of(toAgent) to Collections.singletonList(toAgent) — List.of rejects null elements and this plan is the first to construct unassign moves (toAgent = null); the bug was latent since 12-01 and only surfaces now that buildSeatMoves generates them"
metrics:
  duration: "~55 minutes"
  completed: 2026-08-13
status: complete
actuals:
  tokens: 10651
  tasks: 2
  commits: 2
---

# Phase 12 Plan 02: Full Window Enumeration & Atomic Day Rewrite Summary

Widened the 12-01 tracer from "one window on an empty agent-day" to the full move the phase goal
requires: `ShiftWindowFinder` now enumerates every legal shift window (not just the earliest), and
`AtomicShiftMoveFactory` rewrites an agent-day pinned one slot below the break threshold —
`breakThresholdSlots - 1` slots, no break — into a complete contracted shift with a single atomic
composite move, while satisfied agent-days are filtered out before any window search runs.

## What Was Built

- **`ShiftWindowFinder.findWindows`** — broadened from returning at most one window to returning
  every legal `(span start, break offset)` combination: for the 09:00–21:00 / 8h / 60-min-break /
  1h-blocked / ON_HOUR reference fixture, this is exactly 28 windows (four span starts × seven
  legal break offsets each), verified by test. Windows are ordered by span start ascending then
  break offset ascending so repeated calls on the same input return identical, stably-ordered
  lists — required by 12-03's seeded benchmark harness. Both rounding modes from 12-01
  (`requiredWorkSlots` HALF_UP, `breakThresholdSlots` CEILING) are unchanged and now pinned by
  discriminating test cases (7.25h → 7 slots HALF_UP; 4.25h → 5 slots CEILING). Duplicate seats
  sharing a timeslot start time are deduplicated by lowest `Timeslot.getId()`, not
  first-seen-in-list order, so the same input always selects the same seat regardless of list
  iteration order.
- **`AtomicShiftMoveFactory`** — the tracer's "zero-assignment agent-days only" guard is replaced
  with a genuine under-hours filter: an agent-day is skipped, before any window search runs, once
  its currently-held slot count reaches `ShiftWindowFinder.requiredWorkSlots`. The candidate seat
  pool per agent-day now includes both free seats and seats the agent already holds (still
  filtered by specialization match); seats held by a different agent are excluded outright, so
  `ShiftWindowFinder` treats them as unavailable rather than the factory ever displacing another
  agent's seat. A new package-private `buildSeatMoves(agent, currentlyHeld, window)` builds the
  unassign-then-assign seat-move list that rewrites a pinned agent-day into the shape of a target
  window in one atomic step — one unassign move (target `null`) per currently-held seat the window
  doesn't reuse, one assign move per work seat not already held, no-op pairs skipped. `createMoveList`
  wraps each non-empty seat-move list in `CompositeMove.buildMove`. `MAX_WINDOWS_PER_AGENT_DAY = 8`
  caps the pool after enumeration using fixed-stride down-sampling (`ceil(size/8)`-th element)
  rather than truncating to the first 8, so retained candidates spread across span starts instead
  of clustering at the earliest one, and repeated calls select identically.
- **`ShiftWindowFinderTest`** (new, 13 cases) — pure-function suite with no solver bootstrap:
  rounding-mode parity (HALF_UP vs CEILING, discriminating cases), the 28-window reference
  fixture, leading/trailing blocked-margin refusal, ON_HALF_HOUR vs ON_QUARTER_HOUR alignment
  strictness, a contiguity hole (removed mid-run seat), duplicate-seat one-per-timeslot dedup, the
  no-break case, insufficient-seats-returns-empty, and call-to-call determinism.
- **`AtomicShiftMoveFactoryTest`** (new, 9 cases) — instantiates `AtomicShiftMoveFactory` directly
  with no `SolverFactory`: satisfied and over-hours agent-days produce zero moves; an empty
  agent-day still produces moves (tracer behaviour preserved); the pinned agent-day rewrite shape
  is asserted directly against `buildSeatMoves`' unassign/assign lists; foreign seats and
  specialization mismatches never appear in any generated move (via a `CompositeMove`-unwrapping
  `flatten` helper); no generated move spans two dates; the 28-window reference fixture is capped
  at ≤8 moves whose span starts are not all identical; and two consecutive `createMoveList` calls
  never mutate any `AgentAssignment.getAgent()` value.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] `AssignSeatMove.getPlanningValues()` threw `NullPointerException` for unassign moves**
- **Found during:** Task 2, while designing `buildSeatMoves`' unassign move (`new
  AssignSeatMove(seat, null)`).
- **Issue:** `getPlanningValues()` returned `List.of(toAgent)`. `List.of(...)` throws
  `NullPointerException` if the element is `null`. This was latent since 12-01 — the tracer only
  ever constructed assign moves (`toAgent` always non-null) — but Task 2 is the first code path to
  construct an unassign move with `toAgent == null`, so the bug would have surfaced the first time
  Timefold called `getPlanningValues()` on one (e.g. under a Tabu-Search acceptor, or any
  diagnostic tooling that walks planning values).
- **Fix:** Switched to `Collections.singletonList(toAgent)`, which tolerates a `null` element.
- **Files modified:** `src/main/java/com/wfm/solver/AssignSeatMove.java`
- **Commit:** `733086c`

No other deviations — Task 1 and Task 2 otherwise executed exactly as written, including the two
test-fixture adjustments below (not deviations from the plan's *behavior* list, just necessary
fixture sizing to actually exercise it).

### Test-fixture notes (not deviations, just sizing corrections)

Three tests (`ShiftWindowFinderTest.findWindows_contiguityHole_noWindowSpansTheMissingSlot`,
`AtomicShiftMoveFactoryTest.foreignSeats_areNeverTargetedByGeneratedMoves`, and
`AtomicShiftMoveFactoryTest.specializationMismatch_seatNeverAppearsInGeneratedMoves`) initially
used the 12-seat (09:00–21:00) reference fixture and excluded one mid-run seat. With only 12 total
seats, excluding one mid-run seat splits the run into two pieces (4 and 7 slots) both shorter than
the 9-slot span the fixture requires, so `findWindows` correctly returned an empty list — which
made the "no window spans/targets the excluded seat" assertion vacuously fail (`isNotEmpty()` on
an empty list). Widened all three fixtures to 20 hourly seats (02:00–21:00) so excluding one
mid-run seat still leaves an 11-slot run long enough for the 9-slot span, proving the exclusion
itself (not seat scarcity) is what blocks any window from touching the excluded seat. Caught by
running the new tests before committing, not by CI after the fact.

## Verification

- `./gradlew test --tests com.wfm.solver.ShiftWindowFinderTest --tests com.wfm.solver.AtomicShiftMoveFullAssertTest` — BUILD SUCCESSFUL (Task 1 gate; confirms the tracer's `AtomicShiftMoveFullAssertTest` survives the widened enumeration).
- `./gradlew test --tests com.wfm.solver.AtomicShiftMoveFactoryTest` — BUILD SUCCESSFUL, 9/9 (Task 2 gate).
- `./gradlew test --tests "com.wfm.solver.*"` — BUILD SUCCESSFUL in 7m37s, whole solver package green including `BreakAwareConstructionTest` and `IncrementalScoringDiagnosticTest`.
- `./gradlew build` (full, unfiltered suite, per the phase's documented detection gap — no solver-package test loads the Spring context) — BUILD SUCCESSFUL in 8m11s, **199 tests, 0 failures** (177 pre-existing baseline + 22 new: 13 `ShiftWindowFinderTest` + 9 `AtomicShiftMoveFactoryTest`).
- `git diff --exit-code src/test/java/com/wfm/solver/BreakAwareConstructionTest.java` — exits 0 (unmodified).
- `git diff --exit-code src/main/resources/solverConfig.xml` — exits 0 relative to the 12-01 commit (Java-only plan, selector wiring untouched, and the `<fixedProbabilityWeight>`-before-`<moveListFactoryClass>` ordering fix from 12-01's post-merge gate correction was not disturbed).
- `git diff --exit-code src/main/java/com/wfm/solver/BreakAwareConstructionPhase.java` — exits 0 (abandoned prior attempt stays untouched).
- Source-level acceptance-criteria greps — `RoundingMode.HALF_UP`/`RoundingMode.CEILING` present in `ShiftWindowFinder.java`; no `ai.timefold` import in `ShiftWindowFinder.java`; no `SolverFactory` reference in `ShiftWindowFinderTest.java`; `MAX_WINDOWS_PER_AGENT_DAY` appears 5 times (declaration + javadoc + 3 uses) in `AtomicShiftMoveFactory.java`; no `setAgent` call anywhere in `AtomicShiftMoveFactory.java` outside comments — all confirmed directly via `grep`.

## Known Stubs

None. Both `ShiftWindowFinder` and `AtomicShiftMoveFactory` are production-quality for this plan's
full scope (full enumeration, atomic partial-day rewrite, bounded move pool). Cross-agent
displacement remains out of scope per the phase's own staged plan — 12-RESEARCH.md Open Question 1
explicitly recommends measuring whether empty-seat-plus-own-seats search closes the gap before
adding displacement, and that measurement is 12-03's benchmark, not this plan's.

## Threat Flags

None beyond what the plan's own `<threat_model>` already disposed. T-12-02 (DoS via move-list
throughput) is mitigated by the under-hours pre-filter and `MAX_WINDOWS_PER_AGENT_DAY`, both
asserted by `AtomicShiftMoveFactoryTest` and measured end-to-end in 12-03. T-12-06 (working-solution
tampering) is mitigated by the read-only generation contract, asserted by
`readOnlyGeneration_repeatedCallsLeaveAssignmentsUnchanged` and by the `setAgent`-absence source
check. No new HTTP endpoint, persisted field, or authorization surface was introduced.

## Self-Check: PASSED

- FOUND: src/main/java/com/wfm/solver/ShiftWindowFinder.java
- FOUND: src/main/java/com/wfm/solver/AtomicShiftMoveFactory.java
- FOUND: src/main/java/com/wfm/solver/AssignSeatMove.java
- FOUND: src/test/java/com/wfm/solver/ShiftWindowFinderTest.java
- FOUND: src/test/java/com/wfm/solver/AtomicShiftMoveFactoryTest.java
- FOUND: commit 733086c (feat(12-02): enumerate every legal shift window, pin rounding parity)
- FOUND: commit 89be2b7 (feat(12-02): rewrite pinned agent-days atomically, bound the move pool)
