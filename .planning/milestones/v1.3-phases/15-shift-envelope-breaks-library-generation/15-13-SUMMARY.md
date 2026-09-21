---
phase: 15-shift-envelope-breaks-library-generation
plan: 13
subsystem: solver
tags: [timefold, shift-envelope, gap-closure, characterising-tests, end-to-end]

# Dependency graph
requires:
  - phase: 15-shift-envelope-breaks-library-generation
    provides: "envelope-aware minimum-staffing seats (15-09), authoritative report-layer envelope/breaks (15-10), the shift-mode seat-supply gate (15-11), and frontend divergence rendering (15-12) -- the four links this plan proves closed end to end"
provides:
  - "ShiftDeskEndToEndRegressionTest -- a shape-complete (not scale-complete) shift desk regression: staggered multi-template envelopes, thin/zero edge demand, several consecutive days, proven either zero-hard-and-contiguous or cleanly refused, never a completed solve carrying residual envelope penalty"
  - "ShiftModeBreakGeometryGuardTest -- the live guard succeeding ShiftModeBreakGeometryCharacterisationTest, cross-referencing the end-to-end test for the property it does not itself prove"
  - "DISPOSITION.md -- accounts for all four characterising files in .planning/debug/characterising-tests/, marking the directory a historical record"
  - "Two deferred items filed with mechanism, evidence, and chosen fix location: blocked-break-hours has no SHIFT-mode enforcement point; a template's envelope is never validated against the operating window at save time"
affects: [16-usual-shift-storage, 17-consistency-constraint-drift-reporting]

# Actuals (#2632)
actuals:
  tokens: 15700
  tasks: 3
  commits: 3

tech-stack:
  added: []
  patterns:
    - "Shape-complete fixtures over scale-complete ones for solver regression tests: a desk with staggered multi-envelope structure and edges genuinely outside every envelope reproduces a class of defect that a larger but structurally-degenerate fixture (single shared envelope, uniform demand) cannot, no matter how many agents or days it scales to"
    - "Demand engineered against a template-independent 'core' window (legal for every template in the library, given the exact-net-hours-equals-contract value range) so a hand-built multi-template fixture's forecast is achievable regardless of which template the solver assigns to which agent"

key-files:
  created:
    - src/test/java/com/wfm/solver/ShiftDeskEndToEndRegressionTest.java
    - src/test/java/com/wfm/solver/ShiftModeBreakGeometryGuardTest.java
    - .planning/debug/characterising-tests/DISPOSITION.md
  modified:
    - .planning/phases/15-shift-envelope-breaks-library-generation/deferred-items.md

key-decisions:
  - "The zero-hard case's demand curve is zero everywhere covered except a two-hour 'core' window every one of the four staggered templates legally reaches outside its own break -- the only shape a hand-built fixture can size to exactly N agents without depending on the solver's own per-agent template choice; everywhere else, production's envelope-aware filler (plan 15-09) supplies the seats"
  - "The refusal case reuses the identical desk shape with demand thinned to a uniform 1 FTE at every covered slot -- demand-side insufficiency, not a template/agent-count mismatch -- since any nonzero demand at a slot suppresses production's filler there entirely, so a uniformly thin forecast is the one shape guaranteed to trigger the aggregate seat-supply shortfall regardless of which agents occupy which slots"
  - "Step budget reduced from the phase's existing precedent (20,000) to 2,000 after measuring the shape-complete case reaches 0 hard well inside a 2,000-step local-search budget (~8.4s observed) -- the plan's own instruction to keep runtime inside the suite's tolerance, chosen over reducing agent/day count since the shape (not the count) carries the regression value"
  - "The healthy control reuses ShiftModeFixtures.buildShiftModeSchedule directly rather than reinventing a third fixture builder -- it already IS a fully-covered-window/ample-demand desk (P-19 precedent: one fixture, not three drifting builders)"
  - "The break-geometry guard's scattered/edge case is kept but re-labelled: the flatness it proves is real but cosmetic to an ALREADY-infeasible solve, not the cause of the live defect -- restoring the gated slot-mode break constraints was considered and rejected in the class javadoc (would fight the envelope model, could make an under-supplied desk permanently unsolvable)"
  - "The report-layer characterising case is dropped, not ported -- plan 15-10 replaced the behaviour it characterised, so keeping it would assert that a seat gap is a break, the exact defect 15-10 fixed"

requirements-completed: [ENVL-02, ENVL-04, ENVL-07, XCUT-03, XCUT-04]

coverage:
  - id: D1
    description: "A shape-complete shift desk (staggered multi-template envelopes, thin/zero edge demand) either solves to zero hard or is refused with an actionable message; a completed solve carrying residual envelope penalty fails the test"
    requirement: "ENVL-02"
    verification:
      - kind: unit
        ref: "com.wfm.solver.ShiftDeskEndToEndRegressionTest#shapeCompleteDesk_solvesToZeroHard_neverCarriesResidualEnvelopePenalty_andIsContiguous"
        status: pass
      - kind: unit
        ref: "com.wfm.solver.ShiftDeskEndToEndRegressionTest#shapeCompleteDesk_insufficientDemand_refusedBeforeSolvingWithActionableMessage"
        status: pass
      - kind: unit
        ref: "com.wfm.solver.ShiftDeskEndToEndRegressionTest#healthyControlCase_fullyCoveredWindowAmpleDemand_solvesToZeroHard"
        status: pass
    human_judgment: false
  - id: D2
    description: "On the zero-hard branch, every agent-day's held seats equal its legal slots exactly, in both directions, walked outside the score director (ENVL-04 asserted observably)"
    requirement: "ENVL-04"
    verification:
      - kind: unit
        ref: "com.wfm.solver.ShiftDeskEndToEndRegressionTest#shapeCompleteDesk_solvesToZeroHard_neverCarriesResidualEnvelopePenalty_andIsContiguous"
        status: pass
    human_judgment: false
  - id: D3
    description: "All four characterising-test files are accounted for: three fully promoted to permanent regression suites (plans 15-09/15-11), the fourth partially promoted (this plan) with its report-layer case explicitly dropped and superseded; none remains a live guard against the defect it characterised"
    requirement: "ENVL-07"
    verification:
      - kind: unit
        ref: "com.wfm.solver.ShiftModeBreakGeometryGuardTest (4 tests, all pass)"
        status: pass
      - kind: unit
        ref: "com.wfm.solver.ShiftModeBreakGatingTest (unchanged, re-verified green)"
        status: pass
      - kind: other
        ref: "test -f .planning/debug/characterising-tests/DISPOSITION.md"
        status: pass
    human_judgment: false
  - id: D4
    description: "Both known-latent defects this round deliberately did not fix (blocked-break-hours unenforced in SHIFT mode; template envelope never validated against the operating window at save time) are recorded with mechanism, evidence, and chosen fix location"
    verification:
      - kind: other
        ref: "grep -c '## 15-13' .planning/phases/15-shift-envelope-breaks-library-generation/deferred-items.md"
        status: pass
    human_judgment: false
  - id: D5
    description: "The full backend suite and frontend build are both green after all four gap-closure plans (15-09..15-13) -- the closing gate for a round that touched the solver's input construction, a constraint provider helper, the report layer, a response DTO shape, the export, and the frontend"
    requirement: "XCUT-03"
    verification:
      - kind: other
        ref: "./gradlew test (546 tests, 0 failures, 0 errors, 2 pre-existing skips)"
        status: pass
      - kind: other
        ref: "cd frontend && npm run build"
        status: pass
    human_judgment: false

duration: 33min
completed: 2026-08-27
status: complete
---

# Phase 15 Plan 13: Shape-Complete Closing Evidence for G-15-10 Summary

**A shift desk built to the live defect's exact SHAPE (staggered multi-template envelopes, two hours genuinely outside every envelope, thin/zero edge demand, several consecutive days) now either reaches zero hard with observable ENVL-04 contiguity or is cleanly refused before solving — closing the gap the phase's existing suite could not detect at any scale — while all four characterising-test files are accounted for and the round's two deliberately-deferred defects are filed for the next planner.**

## Performance

- **Duration:** 33 min
- **Started:** 2026-08-27T13:46:25-04:00 (immediately after 15-12)
- **Completed:** 2026-08-27T14:18:48-04:00
- **Tasks:** 3
- **Files modified/created:** 4

## Accomplishments

- Added `ShiftDeskEndToEndRegressionTest`: a hand-built desk carrying the live UAT defect's SHAPE (four templates with staggered envelope starts, each narrower than the operating window, net hours exactly equal to the roster's contracted hours, two hours truly outside every envelope, thin-or-zero demand at the edges, several consecutive days) — proven, in order, non-vacuously feasible, either zero-hard-with-no-residual-envelope-penalty or cleanly refused with an actionable message, and (on the zero-hard branch) contiguous by an independent walk outside the score director that shares no code with the `shiftEnvelopeCompliance` constraint it is checking (P-17 discipline).
- Proved the refusal branch on purpose: the identical desk shape with demand thinned to a uniform 1 FTE per covered slot is refused by the seat-supply gate before any solve, naming the date, the shortfall, and every operator lever.
- Reframed `ShiftModeBreakGeometryCharacterisationTest` into `ShiftModeBreakGeometryGuardTest`: kept and re-labelled the four cases that still carry real information (the mode gate works; SLOT mode still ranks geometries strictly; envelope compliance prices the seat not the hole; scattered/edge flatness is real but cosmetic to an already-infeasible solve), dropped the report-layer case (superseded by plan 15-10's `ScheduleOutputServiceShiftReportingTest`), and cross-referenced `ShiftDeskEndToEndRegressionTest` for the property the guard file explicitly does not itself prove.
- Wrote `DISPOSITION.md`, accounting for all four files under `.planning/debug/characterising-tests/`: which regression test each became, which plan did it, and — for the one intentionally-dropped case — the reason.
- Filed two deferred items with mechanism, evidence, and chosen fix location: blocked-break-hours has no enforcement point in SHIFT mode (save-time fix, restoring the gated constraint explicitly rejected), and a template's envelope is never validated against the operating window at save time (the dead `TimeslotBoundsResponse.endTime()` accessor named as the concrete starting point).
- Closing gate: full backend suite green (546 tests, 0 failures, 0 errors, 2 pre-existing skips) and frontend build green, after all four gap-closure plans (15-09 through 15-13) touched the solver's input construction, a constraint-provider helper, the report layer, a response DTO shape, the export, and the frontend.

## Task Commits

Each task was committed atomically:

1. **Task 1: Shape-complete end-to-end regression on a desk built like the live one** - `52a1656` (test)
2. **Task 2: Reframe the geometry characterisation into a guard, and dispose of the diagnostic set** - `d62ea6d` (test)
3. **Task 3: Record what this round deliberately did not fix, and gate on the full suite** - `5f9f935` (docs)

**Plan metadata:** (this commit, docs: complete plan)

## Files Created/Modified

- `src/test/java/com/wfm/solver/ShiftDeskEndToEndRegressionTest.java` (new) — 3 tests: the zero-hard/contiguous case, the refusal case, the healthy control
- `src/test/java/com/wfm/solver/ShiftModeBreakGeometryGuardTest.java` (new) — 4 reframed guard tests, cross-referencing the end-to-end test
- `.planning/debug/characterising-tests/DISPOSITION.md` (new) — disposition table for all four characterising files
- `.planning/phases/15-shift-envelope-breaks-library-generation/deferred-items.md` — two new deferred items filed under `## 15-13`, plus a staleness check of prior entries

## Decisions Made

See `key-decisions` in frontmatter. In short: the zero-hard fixture's demand curve targets a template-independent "core" window so it stays achievable regardless of the solver's own per-agent template choice; the refusal fixture uniformly thins demand rather than perturbing agent/template counts, since production's filler expansion only ever tops up a slot that has zero demand seats; the step budget was reduced from the phase's 20,000-step precedent to 2,000 after measuring convergence, per the plan's own instruction to reduce budget/scale rather than shape; and the healthy control reuses the existing `ShiftModeFixtures` builder rather than adding a third one.

## Deviations from Plan

None - plan executed exactly as written. No production source was touched, per the plan's explicit prohibition; every finding that might have suggested one (the two latent defects) was instead filed as a deferred item, per the plan's own instruction that a needed production change would mean an earlier plan is incomplete — no such case arose.

## Issues Encountered

The zero-hard fixture's initial step budget (20,000, matching sibling tests' precedent) took ~62s for a single test case — well inside a single test's own timeout but disproportionate against the suite's overall runtime tolerance for one test class. Diagnosed empirically (not by guessing): local search runs its full configured step budget regardless of when the score first reaches zero, so runtime scales with budget, not with problem difficulty once the problem is this slack. Reduced to 2,000 after confirming convergence held at 3,000 and 1,500 as well, landing on 2,000 for comfortable margin (~8.4s observed for the full 3-test class run). Resolved by budget change alone, per the plan's explicit permission to do so before touching agent/day count.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- G-15-10 is now closed on all sides this round scoped: D1 (seat-supply gate, plan 15-11), D2 (envelope-aware minimum-staffing seats, plan 15-09), the report-layer disagreement (plan 15-10), the frontend rendering (plan 15-12), and — this plan — closing evidence that the whole chain holds on a shape-complete desk, plus honest disposition of every characterising test and an honest deferral record for what remains.
- Two latent, independent defects remain, both filed and both explicitly out of scope for this round by operator ruling OR-2: blocked-break-hours has no SHIFT-mode enforcement point, and a template's envelope is never validated against the operating window at save time. Both have a concrete fix location recorded for the next planner.
- UAT Test 10 (shift-mode solve at production scale, the original G-15-10 report) is ready for re-run against the dev deployment by `/gsd-verify-work` — this plan is the automated evidence that makes that re-run worth doing, per the phase's own success criterion.
- No blockers for Phase 16 (Usual Shift Storage) or Phase 17 (Consistency Constraint & Drift Reporting).

---
*Phase: 15-shift-envelope-breaks-library-generation*
*Completed: 2026-08-27*

## Self-Check: PASSED

All 4 tracked files verified present on disk (`[ -f ]`); all 3 task commit hashes
(`52a1656`, `d62ea6d`, `5f9f935`) verified present via `git log --oneline --all`.
