---
phase: 12-atomic-shift-move
verified: 2026-08-21T13:29:43Z
status: withdrawn
score: 0/5 truths verified in the current codebase (4 demonstrated in-flight, then withdrawn; 1 failed on measured evidence)
disposition: goal-not-achieved-implementation-withdrawn
withdrawn_by: 299c42c
withdrawn_date: 2026-08-13
goal_claimed: false
behavior_unverified: 0
---

# Phase 12: Atomic Shift Move Verification Report

**Phase Goal:** The solver can place a full contracted shift — contiguous work slots plus one
correctly positioned break — as a single move, so rosters where agents work their full contracted
hours are actually reachable.

**Verified:** 2026-08-21T13:29:43Z
**Status:** WITHDRAWN — the goal is explicitly **not** claimed as achieved, and the implementation
is intentionally absent from the codebase.

## Disposition

Phase 12 executed all three plans to completion (`12-01`, `12-02`, `12-03` each have a SUMMARY).
The atomic shift move was built, proven correct under `EnvironmentMode.FULL_ASSERT`, and
benchmarked. The benchmark then showed the move did not move the metric the phase goal rests on,
the operator ruled the must-pass threshold FAILED and declined to claim the goal, and the code was
subsequently reverted in full ahead of the hourly-slot trial.

This report records that outcome. It is **not** a gaps-found report: the missing artifacts are
missing by deliberate operator decision, not by incomplete execution. Re-planning Phase 12 as gap
closure would re-add code that was knowingly removed.

## Goal Achievement

### Observable Truths

Truths are the five behaviours enumerated in `12-VALIDATION.md` ("Behaviors covered", derived from
the ROADMAP Phase 12 goal — no REQ-IDs are mapped to this phase).

| # | Truth | Status in codebase today | Evidence |
|---|-------|--------------------------|----------|
| 1 | A full contracted shift (contiguous work slots + one correctly positioned break) is reachable as a single atomic move | ✗ ABSENT (withdrawn) | `ShiftWindowFinder`, `AssignSeatMove`, `AtomicShiftMoveFactory` deleted by `299c42c`. Was demonstrated in-flight by `12-01`/`12-02`. |
| 2 | The custom move never corrupts the incremental score (undo correctness) | ✗ ABSENT (withdrawn) | `AtomicShiftMoveFullAssertTest` deleted by `299c42c`. Passed under `FULL_ASSERT` while it existed (`12-01-SUMMARY.md`). |
| 3 | The move composes with, not replaces, existing change/swap moves | ✗ ABSENT (withdrawn) | `<unionMoveSelector>` block removed from `solverConfig.xml` by `299c42c`; local search is back to Timefold's default change+swap union. |
| 4 | Illegal break placements (misaligned, or inside `breakBlockedHours`) are never generated | ✗ ABSENT (withdrawn) | `ShiftWindowFinderTest` (13 cases) deleted by `299c42c`. Passed while it existed (`12-02-SUMMARY.md`). |
| 5 | Agents previously pinned one slot below the break threshold reach full contracted hours **across repeated runs** | ✗ **FAILED** (on measured evidence, before withdrawal) | Seeded 5×5 A/B benchmark: with-move median **79.50h** vs baseline median **79.25h** — a **0.25h** effect against a **5.00h** baseline spread. See `12-BENCHMARK.md`. |

**Score:** 0/5 verified against the current codebase.

Truth 5 is the goal-bearing one, and it is the one that failed on evidence rather than on
withdrawal. Truths 1–4 were genuinely demonstrated during execution; they are marked absent here
only because the code implementing them was deliberately removed afterwards.

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `src/main/java/com/wfm/solver/ShiftWindowFinder.java` | Shift-window geometry | ✗ REMOVED | Deleted by `299c42c` (176 lines) |
| `src/main/java/com/wfm/solver/AssignSeatMove.java` | Reversible single-seat move | ✗ REMOVED | Deleted by `299c42c` (81 lines) |
| `src/main/java/com/wfm/solver/AtomicShiftMoveFactory.java` | `MoveListFactory<Schedule>` | ✗ REMOVED | Deleted by `299c42c` (170 lines) |
| `src/test/java/com/wfm/solver/ShiftWindowFinderTest.java` | Pure-function window tests | ✗ REMOVED | Deleted by `299c42c` (272 lines) |
| `src/test/java/com/wfm/solver/AtomicShiftMoveFactoryTest.java` | Move-factory contract tests | ✗ REMOVED | Deleted by `299c42c` (357 lines) |
| `src/test/java/com/wfm/solver/AtomicShiftMoveFullAssertTest.java` | `FULL_ASSERT` integration | ✗ REMOVED | Deleted by `299c42c` (243 lines) |
| `src/test/java/com/wfm/solver/AtomicShiftMoveBenchmarkTest.java` | Seeded 5×5 A/B harness | ✗ REMOVED | Deleted by `299c42c` (547 lines) |
| `src/main/resources/solverConfig.xml` | `<unionMoveSelector>` wiring | ✗ REVERTED | `299c42c` restored the default change+swap union |
| `build.gradle` | `wfm.benchmark` system-property gate | ✗ REVERTED | Removed by `299c42c` (8 lines) |
| `.planning/phases/12-atomic-shift-move/12-BENCHMARK.md` | Benchmark evidence + operator verdict | ✓ RETAINED | Deliberately kept as the record of why |
| `12-01/02/03-SUMMARY.md` | Plan execution records | ✓ RETAINED | Deliberately kept |

**Artifacts:** 9 removed by design, 2 retained by design (1,872 deletions across 9 files).

## Why the Goal Was Not Achieved

From `12-BENCHMARK.md` and the operator verdict recorded in `12-03-SUMMARY.md`:

- **Reference scenario (400% over-allocation):** the move's effect on hours assigned was **+0.25h**
  (median 79.50h vs 79.25h) against a baseline run-to-run spread of **5.00h**. The effect is real
  but sits well inside the noise floor — exactly the failure mode `12-VALIDATION.md` warned about
  ("a single green solve is explicitly insufficient evidence for this phase").
- **Realistic scenario (130% over-allocation):** hours assigned were **identical (36.0h) across all
  ten runs, both configurations** — the move was inert, because **seat capacity, not move
  selection, is the binding constraint** at realistic over-allocation.
- The move could only ever target free-or-self-held seats; it can never displace another agent.
  Cross-agent seat displacement is the capability that would actually address the binding
  constraint, and it was filed as a follow-up rather than folded into this phase.

The move itself was never found to be *wrong* — it was correct, improved the hard score, composed
with change/swap, and did not corrupt the incremental score. It simply did not buy the outcome the
phase existed to buy.

## Why the Code Was Removed

Per the revert commit `299c42c` (2026-08-13), operator-authored:

> Moving to hourly timeslots changes the window geometry underneath it anyway — `ON_QUARTER_HOUR`
> alignment becomes meaningless and a 60-minute break collapses from four slots to one — so the
> move is withdrawn rather than carried forward on assumptions that no longer hold.

The revert deliberately preserved the planning artifacts, and preserved the `12-RESEARCH.md` XSD
ordering correction so the known-wrong XML example is not restored.

Full suite green at the pre-phase baseline after the revert: **175 tests, 0 failures**.

## Regression Check

The withdrawal is clean — the codebase is back at its pre-phase behaviour:

- `solverConfig.xml` contains no `moveListFactory` / `AtomicShiftMove` reference.
- No `ShiftWindowFinder`, `AssignSeatMove`, or `AtomicShiftMoveFactory` remains under
  `src/main/java/com/wfm/solver/`.
- `BreakAwareConstructionPhase.java` and `BreakAwareConstructionTest.java` were untouched
  throughout the phase and remain so.

## Carried-Forward Value

The phase produced durable value even though its goal was not met:

1. **A measurement discipline.** The seeded, step-count-terminated A/B harness pattern (5×5,
   median + full min/max spread, gated out of the default suite) is the method by which any future
   solver change should be judged. Wall-clock-terminated, unseeded solves are not run-to-run
   comparable — that finding is now evidence-backed.
2. **The real binding constraint is identified.** At realistic over-allocation the limit is seat
   capacity / cross-agent displacement, not move granularity. Filed as
   `.planning/todos/pending/2026-08-13-cross-agent-seat-displacement.md`.
3. **A detection gap is on record.** No test under `src/test/java/com/wfm/solver/` loads the Spring
   context, so a scoped solver-package run cannot catch a `solverConfig.xml` regression (this bit
   `12-01` — six `@SpringBootTest` suites failed on an XSD element-ordering error that the scoped
   run passed). Any future change to `solverConfig.xml` must be validated with the full suite.
4. **An XSD correction.** `12-RESEARCH.md`'s XML snippets were corrected so
   `<fixedProbabilityWeight>` precedes `<moveListFactoryClass>`, preventing re-inheritance of the
   defect.

## Next Action

None for Phase 12 — it is closed as withdrawn and requires no further work.

The successor question (cross-agent seat displacement, targeting the actual binding constraint) is
filed as a pending todo and can be routed through `/gsd-discuss-phase` when prioritised. It should
be scoped against hourly timeslots, not the 15-minute geometry this phase assumed.
