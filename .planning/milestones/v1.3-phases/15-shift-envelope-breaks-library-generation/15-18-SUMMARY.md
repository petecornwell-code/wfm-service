---
phase: 15-shift-envelope-breaks-library-generation
plan: 18
subsystem: api
tags: [solver, seat-supply-gate, constraint-weights, gap-closure, junit, assertj]

requires:
  - phase: 15 (plan 15-11)
    provides: requireShiftEnvelopeSeatSupply, the shift-mode seat-supply gate run after seat
      construction (step 10d), and its nine-test corpus in ShiftEnvelopeSupplyGateTest
  - phase: 15 (plan 15-09)
    provides: expandMinimumStaffingSeats' date-aware coverage filter (isEffectiveOn && appliesOn
      before covers(ts)) -- the predicate this plan applies to the gate's own supply count
provides:
  - "SolverService.coveredTimeslotsOnDate -- one date-aware coverage helper shared by the gate's
    blocking supply computation and its trailing tightest-hour advisory, replacing two
    textually-duplicated calendar-blind anyMatch expressions"
  - "requireShiftEnvelopeSeatSupply takes a nullable live ConstraintWeights parameter and
    withdraws the over-allocation-ceiling remedy (naming the consequence instead) whenever
    unassignedAssignmentWeight carries a nonzero hard component; default/null weights keep
    byte-identical wording"
  - "15-BENCHMARK.md's dated, appended live-weights rule and source-vs-live divergence table
    (G-15-24)"
affects: []

actuals:
  tokens: 11500
  tasks: 2
  commits: 2

tech-stack:
  added: []
  patterns:
    - "One shared date-aware coverage helper (coveredTimeslotsOnDate) called from both the
      blocking check and the advisory, rather than two independently-evolving inline
      anyMatch(covers) expressions -- the exact class of drift that produced the incoherent
      zero-seat advisory"
    - "Duplicate-error suppression via a pre-computed distinctLibraryDefectReportedForDate flag,
      checked before the numeric-shortfall branch fires -- the ordering is commented as
      load-bearing so a future refactor cannot silently reorder it back into a double report"
    - "Weight-aware refusal text: a single remedyClause local variable branches on whether the
      live ConstraintWeights carries a hard unassignedAssignmentWeight, keeping the two message
      variants' shared prefix (shortfall figures) textually identical and only the suffix
      (remedy) different"

key-files:
  created: []
  modified:
    - src/main/java/com/wfm/service/SolverService.java
    - src/test/java/com/wfm/service/ShiftEnvelopeSupplyGateTest.java
    - src/test/java/com/wfm/service/SolverSeatSupplyGateAccess.java
    - src/test/java/com/wfm/solver/ShiftEnvelopeSupplyInvariantTest.java
    - src/test/java/com/wfm/solver/ShiftDeskEndToEndRegressionTest.java
    - .planning/phases/15-shift-envelope-breaks-library-generation/15-BENCHMARK.md

key-decisions:
  - "coveredTimeslotsOnDate filters pairs by template.isEffectiveOn(date) && template.appliesOn(date)
    before calling ShiftBandPair.covers(ts) -- the identical two-step expandMinimumStaffingSeats
    already applies (6c82241). ShiftBandPair.covers itself is left untouched: it is deliberately
    calendar-blind and the constraint consuming it directly is already scoped to one assignment's
    own eligible pair"
  - "The numeric-shortfall error is suppressed for any date where the distinct
    retired/weekday-invalid message already fires (distinctLibraryDefectReportedForDate),
    computed BEFORE the shortfall check -- otherwise date-aware coverage would make
    librarySupplySlots collapse to zero for such a date and report the same root cause twice,
    undoing plan 15-11's deliberate message split"
  - "The weights parameter is appended as the LAST parameter (not inserted mid-signature) to
    minimise the diff at all six existing call sites; all solver-package callers pass null,
    preserving exactly what they measure"
  - "The remedy branches on unassignedAssignmentWeight.hardScore() != 0, not on the weight's
    numeric magnitude -- any nonzero hard component is treated as unsafe, matching the live
    desk's ofHard(10000) without hard-coding that specific value into the branch condition"
  - "The G-15-24 destructiveness measurement is explicitly NOT re-established. The advice-safety
    fix rests on the structural seat-count/weight mechanism (expandOverflowAssignments' maxAgents
    scales with the ceiling; a hard unassignedAssignmentWeight makes every unfillable manufactured
    seat a hard violation) rather than on the confounded -20,338 experiment, which changed
    overallocationHardLimitPct AND underallocationHardLimitPct in the same run (G-15-28)"

requirements-completed: [ENVL-01, ENVL-02, ENVL-06]

coverage:
  - id: D1
    description: "One date-aware definition of covered supply, shared by the blocking check and
      the advisory -- a weekday-only template contributes zero supply on a weekend date, proven
      by a red-proof asserting the exact post-fix shortfall figure (8 supplied pre-fix vs 5
      post-fix, shortfall of 3)"
    requirement: "ENVL-02"
    verification:
      - kind: unit
        ref: "ShiftEnvelopeSupplyGateTest#refusesWeekendOvercountFromWeekdayOnlyTemplate"
        status: pass
      - kind: unit
        ref: "ShiftEnvelopeSupplyGateTest#advisoryNeverNamesAnHourOnlyAWeekdayInvalidTemplateReaches"
        status: pass
    human_judgment: false
  - id: D2
    description: "A wholly-retired or wholly-weekday-invalid date produces exactly one error, not
      the distinct message plus a restated numeric shortfall"
    requirement: "ENVL-02"
    verification:
      - kind: unit
        ref: "ShiftEnvelopeSupplyGateTest#refusesWhollyRetiredLibraryDistinctFromShortfall"
        status: pass
    human_judgment: false
  - id: D3
    description: "Zero fixtures that pass the gate today are refused after the change -- the
      existing nine ShiftEnvelopeSupplyGateTest cases plus both solver-package gate callers
      (ShiftEnvelopeSupplyInvariantTest, ShiftDeskEndToEndRegressionTest) all still pass"
    requirement: "ENVL-01"
    verification:
      - kind: unit
        ref: "com.wfm.service.ShiftEnvelopeSupplyGateTest (14/14 pass)"
        status: pass
      - kind: unit
        ref: "com.wfm.solver.ShiftEnvelopeSupplyInvariantTest (6/6 pass)"
        status: pass
      - kind: unit
        ref: "com.wfm.solver.ShiftDeskEndToEndRegressionTest (3/3 pass)"
        status: pass
    human_judgment: false
  - id: D4
    description: "The default-weight refusal message is byte-identical to today's (literal
      equality, not substring); null weights fall back to the same wording; the hard-weight
      variant withdraws the ceiling remedy, names the consequence, and still reports the current
      percentage"
    requirement: "ENVL-06"
    verification:
      - kind: unit
        ref: "ShiftEnvelopeSupplyGateTest#defaultWeightsMessageIsByteIdenticalToBeforeThisPlan"
        status: pass
      - kind: unit
        ref: "ShiftEnvelopeSupplyGateTest#nullWeightsFallBackToDefaultWording"
        status: pass
      - kind: unit
        ref: "ShiftEnvelopeSupplyGateTest#hardUnassignedWeightWithdrawsCeilingRemedyAndNamesConsequence"
        status: pass
    human_judgment: false
  - id: D5
    description: "15-BENCHMARK.md carries a dated, appended live-weights rule with the
      source-vs-live weight divergence table; the file's pre-existing sections are untouched"
    verification:
      - kind: other
        ref: "manual diff review -- new '## Live Weights Discipline (G-15-24)' section appended
          strictly after line 450 (the prior EOF), no edits to any existing section"
        status: pass
    human_judgment: false
  - id: D6
    description: "SolverQualityGuardTest stays green, proving this change did not leak into
      solver search quality -- it does NOT prove the gate itself is correct, since the guard is
      deliberately not routed through the gate"
    verification:
      - kind: unit
        ref: "com.wfm.solver.SolverQualityGuardTest (10/10 pass)"
        status: pass
    human_judgment: false
  - id: D7
    description: "Full suite green with no reduction in test count against the 616/0/0/2 baseline
      this executor was handed (600/0/0/2 was the plan's own stale baseline text)"
    verification:
      - kind: unit
        ref: "./gradlew test (621 tests, 0 failures, 0 errors, 2 skipped)"
        status: pass
    human_judgment: false

duration: ~50 min
completed: 2026-09-02
status: complete
---

# Phase 15 Plan 18: Date-aware seat-supply gate + weight-aware refusal advice Summary

**One date-aware `coveredTimeslotsOnDate` helper closes the seat-supply gate's third
calendar-blindness site (G-15-21), and the refusal text now reads the desk's live
`unassignedAssignmentWeight` before recommending the most destructive lever on it (G-15-24).**

## Performance

- **Duration:** ~50 min
- **Tasks:** 2
- **Files modified:** 6 (1 production, 4 test, 1 planning doc)
- **Commits:** 2

## Accomplishments

- Extracted `SolverService.coveredTimeslotsOnDate` as the single date-aware coverage predicate
  (`template.isEffectiveOn(date) && template.appliesOn(date)` before `ShiftBandPair.covers(ts)`),
  replacing the two textually-duplicated calendar-blind `anyMatch` expressions in the blocking
  supply check and the tightest-hour advisory. `ShiftBandPair.covers` itself is untouched.
- Suppressed the numeric-shortfall error for any date where the distinct retired/weekday-invalid
  message already fires, computed via a `distinctLibraryDefectReportedForDate` flag evaluated
  BEFORE the shortfall check — date-aware coverage would otherwise report the same wholly-retired
  root cause twice.
- Added a weekend over-count red-proof with exact pre/post figures: a two-template fixture (a
  weekday-only template and a weekend-valid template whose clock-time footprints overlap) where
  the pre-fix calendar-blind union counts 8 supplied slots (meets 8 contracted, passes) and the
  post-fix date-aware count counts only 5 (shortfall of 3, refused) — the message asserts both
  numbers literally, not merely that the call throws.
- Added an advisory-coherence test proving the tightest-hour warning never names an hour reachable
  only by a weekday-invalid template — the mechanism behind the live desk's incoherent "tightest
  at 08:00-09:00 with 0 seat(s)" symptom.
- Threaded a nullable live `ConstraintWeights` into `requireShiftEnvelopeSeatSupply` (appended as
  the last parameter), supplied at the call site from the `weights` object already resolved
  earlier in `SolverService.solve`. Updated the test bridge `SolverSeatSupplyGateAccess` and all
  six existing solver-package call sites to pass `null`, preserving exactly what they measure.
- The shortfall refusal now branches on whether `unassignedAssignmentWeight` carries a nonzero
  hard component: default/null weights emit exactly today's wording (pinned by a literal-equality
  test, not substring matching); a hard weight withdraws the "raise the ceiling" suggestion,
  states the consequence (every manufactured, unfillable seat is a hard violation at the desk's
  named weight), and still reports the current percentage.
- Appended a dated "Live Weights Discipline (G-15-24)" section to `15-BENCHMARK.md` with a
  source-vs-live divergence table, strictly below the file's existing sections.

## Task Commits

1. **Task 1: One date-aware definition of covered supply, used by both the blocking check and the advisory** — `32c3240` (test)
2. **Task 2: The refusal advises against a lever it has actually checked, and the live-weights rule is written down** — `c5baa55` (feat)

**Plan metadata:** (this commit) `docs(15-18): complete date-aware seat-supply gate plan`

## Files Created/Modified

- `src/main/java/com/wfm/service/SolverService.java` — `coveredTimeslotsOnDate` helper; duplicate
  numeric-shortfall suppression; nullable `weights` parameter and weight-aware remedy text
- `src/test/java/com/wfm/service/ShiftEnvelopeSupplyGateTest.java` — weekend red-proof, advisory
  coherence test, retired-library `hasSize(1)` tightening, and the three weight-branching tests
- `src/test/java/com/wfm/service/SolverSeatSupplyGateAccess.java` — bridge signature updated with
  the new `weights` parameter
- `src/test/java/com/wfm/solver/ShiftEnvelopeSupplyInvariantTest.java` — three call sites pass `null`
- `src/test/java/com/wfm/solver/ShiftDeskEndToEndRegressionTest.java` — three call sites pass `null`
- `.planning/phases/15-shift-envelope-breaks-library-generation/15-BENCHMARK.md` — appended
  live-weights rule and divergence table

## Decisions Made

See `key-decisions` in frontmatter. Notably: the `weights` parameter is appended last (not
inserted mid-signature) to minimise the diff at six pre-existing call sites, and the hard-weight
branch fires on `hardScore() != 0` rather than a hard-coded threshold, so it generalises beyond
the live desk's specific `ofHard(10000)` value.

## Deviations from Plan

None — plan executed exactly as written. Both tasks completed within their fix-attempt budget
with no Rule 1-4 triggers.

## Issues Encountered

None.

## Gate-Passing Fixture Count (verification item 1)

Before this plan, 7 fixtures across the three test classes asserted the gate passes without
throwing (`ShiftEnvelopeSupplyGateTest`: `noFalseRefusalWhenSupplyMeetsDemand`,
`doesNotRefuseWhenTemplateCoversThatWeekday`, `slotDeskNeverEvaluated`,
`advisoryOnThinTimeslotDoesNotBlock`; `ShiftEnvelopeSupplyInvariantTest`: CASE 1;
`ShiftDeskEndToEndRegressionTest`: Case 1 and Case 3 (healthy control)). All 7 still pass after
this plan — verified by the full 23/23 green run across the three classes. This plan's own new
`advisoryNeverNamesAnHourOnlyAWeekdayInvalidTemplateReaches` test is an 8th, newly-added passing
fixture, not part of the before/after comparison.

## Weekend Red-Proof — Pre-Fix and Post-Fix Supply Figures

Two-template fixture (weekday-only "Weekday" 08:00-17:00/break 12:00-13:00, valid Mon-Fri only;
weekend-valid "Weekend" 10:00-19:00/break 14:00-15:00, valid Sat/Sun only) on a Saturday
(2026-09-05), 1 agent contracted 8.00h (matches only the weekend template's net hours), 8 seats
placed at 08:00, 09:00, 14:00 (weekday-only clock-time coverage), 10:00, 11:00, 12:00, 13:00,
15:00 (weekend-covered):

- **Pre-fix (calendar-blind union of both pairs' clock-time coverage):** 8 slots counted as
  supplied == 8 contracted — the gate PASSES.
- **Post-fix (date-aware — only the weekend-valid pair counts on a Saturday):** 5 slots counted
  as supplied (10, 11, 12, 13, 15) against 8 contracted — a shortfall of 3, and the gate REFUSES,
  naming both figures exactly ("only reaches 5 slot(s)", "a shortfall of 3 slot(s)").

## Default-Weight and Hard-Weight Messages, Side by Side

Same shortfall shape (2 agents x 8.00h contracted = 16 slots demand, 8 slots supplied,
`overallocationHardLimitPct` 100, date `2026-09-07`):

**Default/null weights** (pinned by literal equality):
> On 2026-09-07, rostered agent-days need 16 slot(s) (16.00h) inside the shift library's live
> envelopes, but the library only reaches 8 slot(s) (8.00h) there — a shortfall of 8 slot(s)
> (8.00h). On a shift-scheduled desk an agent works exactly their assigned shift, so this cannot
> be resolved by solving for longer. To fix it: raise the desk's over-allocation limit (currently
> 100%), correct the demand forecast for the hours the library covers, reduce rostered hours for
> 2026-09-07, or change the library so its envelopes sit over demand-bearing hours.

**Hard `unassignedAssignmentWeight` (10,000 hard)**:
> On 2026-09-07, rostered agent-days need 16 slot(s) (16.00h) inside the shift library's live
> envelopes, but the library only reaches 8 slot(s) (8.00h) there — a shortfall of 8 slot(s)
> (8.00h). On a shift-scheduled desk an agent works exactly their assigned shift, so this cannot
> be resolved by solving for longer. Raising the desk's over-allocation limit (currently 100%) is
> NOT a safe fix here: on this desk every seat it manufactures beyond contracted demand is a HARD
> violation, worth 10000 hard, if no agent can fill it. To fix it: correct the demand forecast for
> the hours the library covers, reduce rostered hours for 2026-09-07, or change the library so its
> envelopes sit over demand-bearing hours.

Only the sentence between "solving for longer." and "To fix it:" differs; the shortfall figures
and the closing three-lever list are identical in both variants, and the current percentage
(`100%`) is reported in both.

## `SolverQualityGuardTest` — What It Does and Does Not Prove Here

`./gradlew test --tests "com.wfm.solver.SolverQualityGuardTest"` — 10/10 pass. This proves the
gate change did NOT leak into solver search quality (the guard solves a live-shape synthetic desk
five times and checks structural invariants plus a violation-count ceiling). It is deliberately
NOT routed through `requireShiftEnvelopeSeatSupply` (`15-14-PLAN.md` key_links, re-confirmed in
`15-VERIFICATION.md`'s Key Link table as "CORRECTLY NOT WIRED (by design)"), so a green run is
evidence about search quality only — it is NOT evidence that the gate itself is correct. That
claim rests entirely on `ShiftEnvelopeSupplyGateTest`, `ShiftEnvelopeSupplyInvariantTest`, and
`ShiftDeskEndToEndRegressionTest` (23/23 pass).

## G-15-24 Destructiveness Measurement — Explicitly NOT Re-Established

This plan does not re-run the -20,338 live experiment or any other live solve to re-prove that
raising the over-allocation ceiling is destructive on the live desk. That experiment is confirmed
confounded (it changed `overallocationHardLimitPct` and `underallocationHardLimitPct` in the same
run — G-15-28 recorded a clean raise, measured separately, as beneficial). Per G-15-29's binding
Solver Comparison Rule (`15-BENCHMARK.md`), a single run is never evidence on this desk, and the
live desk's weekend forecast is currently mid-correction by the operator (G-15-28) — any
measurement taken now would be against data about to change. The fix delivered here rests on the
structural, checkable mechanism alone: `expandOverflowAssignments` derives `maxAgents` as
`ceil(requiredFTEs * pct / 100)`, so raising the ceiling manufactures additional seats regardless
of measurement, and a nonzero hard `unassignedAssignmentWeight` makes every one of those seats
that no agent can fill a hard violation by construction — reading that weight before advising is
correct independent of how a controlled re-experiment would come out.

## Full Suite

`./gradlew test` — **621 tests, 0 failures, 0 errors, 2 skipped**, against the 616/0/0/2 baseline
this executor was handed (the plan's own verification text cited a stale 600/0/0/2 figure). The
5-test increase is exactly this plan's new tests (2 in Task 1, 3 in Task 2); no test count
regression.

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

- G-15-21 and G-15-24 are code-fixed and test-proven; `15-UAT.md`'s gap entries for both were
  deliberately left untouched by this plan (out of scope per the plan's own `files_modified`
  list, which does not include `15-UAT.md`) — marking them `resolved` is a follow-up action for
  whoever next reconciles `15-UAT.md` against shipped fixes, not part of this plan's output.
- The other Phase 15 gaps this session's context flagged as still open (G-15-25, G-15-28,
  G-15-31) are unaffected by this plan and remain exactly as `15-UAT.md` records them.

---
*Phase: 15-shift-envelope-breaks-library-generation*
*Completed: 2026-09-02*

## Self-Check: PASSED
