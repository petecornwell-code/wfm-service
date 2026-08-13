---
created: 2026-08-13T00:00:00Z
title: Cross-agent seat displacement for the atomic shift move
area: solver
# resolves_phase intentionally unset: this is follow-up work Phase 12 does NOT
# resolve — it is what Phase 12's benchmark proved is still missing. Setting it
# to 12 would make close_phase_todos auto-sweep this into completed/ the moment
# Phase 12 is marked complete, silently losing it. Set it to the phase that
# actually takes this on, once that phase exists.
raised_during_phase: 12
files:
  - src/main/java/com/wfm/solver/AtomicShiftMoveFactory.java
  - src/main/java/com/wfm/solver/AssignSeatMove.java
  - src/main/java/com/wfm/solver/ShiftWindowFinder.java
---

## Origin (Phase 12 Plan 03, operator checkpoint, 2026-08-13)

Phase 12 built `AtomicShiftMoveFactory`, a custom Timefold move that places a full contracted
shift plus its break in one atomic step. The seeded, step-count-terminated 5x5 benchmark
(`.planning/phases/12-atomic-shift-move/12-BENCHMARK.md`) measured the move against the phase's
own pass/fail thresholds and the operator ruled:

- **Threshold 1 (must-pass, median-vs-spread hours assigned): FAILED.** With-move median hours
  assigned (79.50h) exceeds baseline median (79.25h) by only 0.25h, against a 5.00h baseline
  min/max spread — well inside the existing noisy band, not a real effect.
- The move itself is correct and is being kept (improves hard score, composes with change/swap,
  no score corruption) — this follow-up is not about a bug in 12-01/12-02.
- The phase goal ("agents reach more contracted hours") is **not** claimed as achieved by Phase 12
  and remains open.

## The actual lever, per the 130% conservative-variant data

`12-BENCHMARK.md`'s informational 130%-over-allocation variant (the `Schedule` class's own
default, i.e. the realistic regime — not the 400% reference scenario the phase-gating measurement
used) showed **zero measurable benefit** from the move: hours assigned identical (36.0h) across
all 10 runs, both configurations, hard-score medians identical. At 130%, total seat capacity (144)
is already below what all 11 agents need to reach 8.00 contracted hours (352 slot-fills) — seat
capacity is the binding constraint before move selection is ever reached.

`AtomicShiftMoveFactory`'s candidate seat pool (per 12-02) is explicitly restricted to seats an
agent already holds or that are free — seats held by a **different** agent are excluded outright,
so the factory never displaces another agent's seat. That restriction is correct for 12-01/12-02's
staged scope, but it is also exactly why the move is inert once seats are scarce: there is no
legal window left to find when every remaining seat in a candidate span is already claimed by
someone else.

## Scope for this follow-up

Extend the move (or add a new move) that can **displace another agent from a seat it holds**, not
just claim free-or-self-held seats, when doing so improves the objective (e.g. moving a
lower-priority or already-satisfied agent off a seat a still-under-hours agent needs to complete a
window). This is the capability `12-RESEARCH.md` Open Question 1/2 and `12-02-SUMMARY.md`'s "Known
Stubs" section both named as deliberately deferred pending this measurement.

Needs before planning:
- Confirm this is scoped as its own phase (do not fold into an in-flight v1.2 phase) — it changes
  move semantics and score-corruption risk surface (undo correctness under displacement is a new
  correctness question `AtomicShiftMoveFullAssertTest`-style coverage will need to re-prove).
  A named displacement move is a `costly`/architectural decision, not a `reversible` one — treat
  it as Rule 4 (ask), not an auto-fix, when planning starts.
  - Fairness/priority rule for *which* agent gets displaced (avoid thrashing — two agents
    endlessly displacing each other's seats).
  - Whether displacement needs its own opt-in threshold-tuning knob or should always be part of the
    default union move selector once implemented.
  - Re-run the same seeded 5x5 (400% reference) plus 130% conservative-variant benchmark harness
    from `AtomicShiftMoveBenchmarkTest` against the new move, since that is the only evidence
    format this phase has established as trustworthy (step-count-terminated, not wall-clock).

## Suggested handling

Route through `/gsd-discuss-phase` for a new phase once the v1.2 roadmap (Phases 9-11) is planned,
or fold in earlier if a scheduling manager reports the same "pinned below break threshold" symptom
recurring on a live desk at realistic (non-400%) over-allocation.
