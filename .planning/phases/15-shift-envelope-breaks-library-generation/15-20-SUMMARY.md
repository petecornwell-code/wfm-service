---
phase: 15-shift-envelope-breaks-library-generation
plan: 20
subsystem: solver
tags: [timefold, shift-envelope, gap-closure, seat-supply-gate, junit, assertj, forced-occupancy]

requires:
  - phase: 15 (plan 15-18)
    provides: SolverService.coveredTimeslotsOnDate (date-aware coverage helper) and the nullable
      live ConstraintWeights parameter on requireShiftEnvelopeSeatSupply -- this plan's new
      per-hour check sits behind the same date filter and reuses the same weight-aware remedy
      branching
  - phase: 15 (plan 15-19)
    provides: 15-SEAT-SUPPLY-GATE-ANALYSIS.md and SeatSupplyDistributionAnalysisTest -- R2 (the
      forced-occupancy necessary condition), its proof, and the labelled fixture corpus this plan
      implements against and re-measures
provides:
  - "SolverService.forcedAgentDaysByTimeslotId -- a per-agent-day forced-occupancy necessary
    condition (R2), computed against each agent-day's OWN getEligibleShiftBandPairs() rather than
    a desk-wide anyMatch union, accumulated alongside the pre-existing day-wide sum inside
    requireShiftEnvelopeSeatSupply. Band-composition-sensitive by construction (G-15-25) and
    per-timeslot rather than day-wide (G-15-31) -- one check closes both gaps."
  - "SolverSeatSupplyGateAccess.forcedAgentDaysByTimeslotId -- a new test-only bridge exposing the
    package-private production method to SeatSupplyDistributionAnalysisTest (com.wfm.solver),
    eliminating that class's former test-local reimplementation of R2 (T-15-20-04)"
  - "SeatSupplyDistributionAnalysisTest re-measured against the SHIPPED gate: R2's own row now
    calls production via the bridge, a new 'Shipped gate' row invokes the full throwing method,
    and a first-class band-composition experiment (re-measured every run) proves the shipped
    forced-count figure changes with band composition while the day-wide union figure does not"
  - "G-15-25 and G-15-31 resolved in 15-UAT.md with band-composition figures and shipped-gate
    false-refusal measurement as evidence; 15-SEAT-SUPPLY-GATE-ANALYSIS.md carries an appended
    outcome section (never editing the pre-change measurement sections)"
affects: []

actuals:
  tokens: 19500
  tasks: 3
  commits: 3

tech-stack:
  added: []
  patterns:
    - "A per-agent-day forced-occupancy check (R2) accumulated ALONGSIDE the pre-existing day-wide
      sum (R0) into the same accumulate-then-throw error list, never replacing it -- both checks
      are genuine necessary conditions in their own right and can both fire on the same date"
    - "Per-hour refusal consolidated to the single WORST timeslot per date (largest
      forced-minus-seats deficit), mirroring the trailing tightest-hour advisory's own 'worst'
      precedent, rather than one error entry per offending hour"
    - "A production method exposed to a cross-package test class via a thin, additive bridge
      (SolverSeatSupplyGateAccess), mirroring that class's own pre-existing
      requireShiftEnvelopeSeatSupply bridge exactly -- the specific countermeasure for
      T-15-20-04 (duplicate rule implementations drifting apart), already hit three times in
      this phase (G-15-10 root cause B, G-15-21, the gate's own two coveredTimeslots sites)"
    - "The analysis harness's candidate rules distinguish 'the rule alone' (R2, via the shipped
      counting logic) from 'what the desk actually experiences' (Shipped gate, the full throwing
      method combining R0+R2) -- two distinct, useful rows rather than one conflated row"

key-files:
  created: []
  modified:
    - src/main/java/com/wfm/service/SolverService.java
    - src/test/java/com/wfm/service/ShiftEnvelopeSupplyGateTest.java
    - src/test/java/com/wfm/service/SolverSeatSupplyGateAccess.java
    - src/test/java/com/wfm/solver/SeatSupplyDistributionAnalysisTest.java
    - .planning/phases/15-shift-envelope-breaks-library-generation/15-SEAT-SUPPLY-GATE-ANALYSIS.md
    - .planning/phases/15-shift-envelope-breaks-library-generation/15-UAT.md

key-decisions:
  - "R2's forced-occupancy predicate ships EXACTLY as plan 15-19's analysis proved it: an
    agent-day is forced at a timeslot when EVERY eligible pair both covers it and has zero slack
    for that agent-day (covered-slot count == expectedWorkSlots exactly). No stronger model (a
    full achievable-assignment computation) was adopted, per the plan's own instruction not to
    ship a rule the analysis never measured."
  - "Per-hour violations are consolidated to ONE error per date (the worst timeslot by deficit),
    not one per offending hour -- keeps the refusal message singular and actionable, and avoids
    an explosion of near-duplicate entries on a desk that is short at many hours for the same
    underlying reason."
  - "SolverSeatSupplyGateAccess.forcedAgentDaysByTimeslotId was added even though
    15-20-PLAN.md's own files_modified frontmatter did not list SolverSeatSupplyGateAccess.java --
    a deliberate Rule 2 deviation (see below), justified by the plan's own explicit,
    threat-register-backed instruction not to keep a test-local duplicate of a rule that shipped."
  - "The three pre-existing weight-branching tests (Tests 10-12, ShiftEnvelopeSupplyGateTest) were
    updated to isolate the day-wide shortfall detail via a new dayWideShortfallDetail helper,
    since their shared fixture (2 agents, 1 pair, 1 seat/hour) now ALSO trips the new per-hour
    check -- both checks correctly firing on a desk that is genuinely short both in total and at
    every hour, not a regression to paper over."
  - "G-15-31 was resolved (not resolved-with-decision or left open): the analysis's recommendation
    (adopt R2 additively alongside R0) was implemented in full, matching the recommendation's own
    shape exactly, with the false-refusal count measured at zero against the unchanged corpus."

requirements-completed: [ENVL-01, ENVL-02, ENVL-06, XCUT-04]

coverage:
  - id: D1
    description: "Supply computed against each agent-day's own eligible pairs, not a desk-wide
      anyMatch union -- band-composition sensitivity proven with two different forced-count
      figures (1 -> 0) from two runs differing only in band composition on a saturated union,
      while the desk-wide union figure stays byte-identical (G-15-25)"
    requirement: "ENVL-02"
    verification:
      - kind: unit
        ref: "ShiftEnvelopeSupplyGateTest#bandCompositionChangesForcedCountButNotTheSaturatedUnion"
        status: pass
      - kind: unit
        ref: "SeatSupplyDistributionAnalysisTest#bandCompositionExperiment_shippedFigureChangesButUnionStaysSaturated"
        status: pass
    human_judgment: false
  - id: D2
    description: "A per-timeslot forced-occupancy check catches a distribution-blind shortfall the
      day-wide sum structurally misses (day-wide-abundant desk, one genuinely thin hour), while
      zero KNOWN-SOLVES fixtures in plan 15-19's labelled corpus are refused (G-15-31)"
    requirement: "ENVL-01"
    verification:
      - kind: unit
        ref: "ShiftEnvelopeSupplyGateTest#perHourForcedOccupancyRefusesWhatTheDayWideSumMisses"
        status: pass
      - kind: unit
        ref: "SeatSupplyDistributionAnalysisTest#distributionBlindFixture_shippedGateNowRefusesIt"
        status: pass
      - kind: unit
        ref: "SeatSupplyDistributionAnalysisTest#ruleByFixtureTable_falseAndTrueRefusalCounts (asserts falseRefusals(shipped gate) == 0)"
        status: pass
    human_judgment: false
  - id: D3
    description: "The per-hour refusal names the date, the hour, the forced agent-day count and
      the seat count, and follows the same weight-aware advice rule plan 15-18 established
      (withholds the over-allocation-ceiling remedy on a hard-weighted desk)"
    requirement: "ENVL-06"
    verification:
      - kind: unit
        ref: "ShiftEnvelopeSupplyGateTest#perHourForcedOccupancyRefusesWhatTheDayWideSumMisses"
        status: pass
    human_judgment: false
  - id: D4
    description: "The analysis harness invokes the SHIPPED gate rather than a duplicate rule
      implementation (T-15-20-04) -- R2's own row calls production via a new bridge, and a
      'Shipped gate' row invokes the full throwing production method"
    verification:
      - kind: unit
        ref: "SeatSupplyDistributionAnalysisTest#r2ForcedSet_provenOnHandBuiltCase"
        status: pass
      - kind: unit
        ref: "SeatSupplyDistributionAnalysisTest#ruleByFixtureTable_falseAndTrueRefusalCounts"
        status: pass
    human_judgment: false
  - id: D5
    description: "advisoryOnThinTimeslotDoesNotBlock is settled explicitly in writing (stays
      non-blocking, untouched) rather than silently left alone or silently changed"
    verification:
      - kind: other
        ref: "git diff on ShiftEnvelopeSupplyGateTest.java's advisoryOnThinTimeslotDoesNotBlock
          method across this plan's commits is empty; 15-SEAT-SUPPLY-GATE-ANALYSIS.md section 8.5
          records the verdict"
        status: pass
    human_judgment: false
  - id: D6
    description: "G-15-25 and G-15-31 closed in 15-UAT.md with resolved_by/resolved_evidence
      matching the established shape; G-15-28 and G-15-10 left untouched"
    verification:
      - kind: other
        ref: "git diff on 15-UAT.md shows no lines touching the G-15-28 or G-15-10 entries"
        status: pass
    human_judgment: false
  - id: D7
    description: "Full suite green with no test-count regression against the 632/0/0/2 baseline
      this executor was handed"
    verification:
      - kind: unit
        ref: "./gradlew test (635 tests, 0 failures, 0 errors, 2 skipped)"
        status: pass
    human_judgment: false

duration: ~70 min
completed: 2026-09-02
status: complete
---

# Phase 15 Plan 20: Per-Agent-Day Forced-Occupancy Seat-Supply Check (G-15-25/G-15-31) Summary

**`SolverService.forcedAgentDaysByTimeslotId` replaces the seat-supply gate's blindness to band
composition and within-day distribution with one per-agent-day, per-timeslot necessary-condition
check (R2, from plan 15-19's analysis) that accumulates alongside the pre-existing day-wide sum —
proven band-composition-sensitive (1 → 0 forced at a boundary hour from adding two edge bands to a
saturated union) and measured at zero false refusals against plan 15-19's labelled corpus.**

## Performance

- **Duration:** ~70 min
- **Started:** 2026-09-02T14:42:00Z
- **Completed:** 2026-09-02T15:27:40Z
- **Tasks:** 3
- **Files modified:** 6 (1 production, 3 test, 2 planning docs)

## Accomplishments

- Added `SolverService.forcedAgentDaysByTimeslotId`: for each date and each of that date's
  timeslots, counts how many rostered agent-days are FORCED there — every one of the agent-day's
  eligible pairs (`getEligibleShiftBandPairs`) both covers the timeslot and has zero slack for
  that agent-day (its own covered-slot count on that date equals `expectedWorkSlots` exactly).
  `requireShiftEnvelopeSeatSupply` refuses when the forced count at any covered timeslot exceeds
  the seats there, consolidated to the single worst timeslot per date, accumulating into the same
  error list as the pre-existing day-wide sum (never replacing it) and following the identical
  weight-aware remedy branching plan 15-18 established.
- Proved band-composition sensitivity by construction and by test: on a saturated-union library (3
  single-band pairs sharing one envelope), the forced count at a boundary hour is 1; adding two
  edge bands to the SAME envelope (5 bands total) changes that figure to 0, while the desk-wide
  union figure stays byte-identical — the direct inverse of the byte-identical live measurement
  that filed G-15-25. Proved distribution sensitivity separately: a day-wide-abundant desk (30
  seats vs 24 demand) with one genuinely thin hour (2 seats vs 3 forced agent-days) is now refused,
  naming the date, hour, forced count and seat count — a shortfall the day-wide sum structurally
  cannot see.
- Re-measured `SeatSupplyDistributionAnalysisTest` against the SHIPPED implementation rather than
  leaving it as a test-local copy: R2's own row now calls
  `SolverSeatSupplyGateAccess.forcedAgentDaysByTimeslotId` (a new, minimal bridge added for this
  purpose), and a new "Shipped gate" row invokes the full, throwing production method directly.
  The shipped gate refuses the distribution-blind fixture that PASSED it in plan 15-19, and its
  false-refusal count against the corpus's three KNOWN-SOLVES fixtures is asserted at zero. Added
  the band-composition experiment as a first-class, always-re-measured test rather than a one-off
  assertion.
- Updated the three pre-existing weight-branching tests (`ShiftEnvelopeSupplyGateTest` Tests
  10-12) to isolate the day-wide shortfall detail specifically, since their shared fixture now
  ALSO trips the new per-hour check (both correctly fire on a desk that is genuinely short both in
  total and at every hour). All three gate-calling test classes (`ShiftEnvelopeSupplyGateTest`,
  `ShiftEnvelopeSupplyInvariantTest`, `ShiftDeskEndToEndRegressionTest`) pass unchanged — 25/25.
- Appended an outcome section (§8) to `15-SEAT-SUPPLY-GATE-ANALYSIS.md`: which recommendation was
  implemented (R2 adopted additively, exactly as recommended), the post-change rule-by-fixture
  table, the band-composition figures, what remains unsettled (R0 redundancy, runtime
  benchmarking, named rather than implied closed), and an explicit settlement of
  `advisoryOnThinTimeslotDoesNotBlock` (stays non-blocking, untouched — proven this session against
  the SHIPPED per-hour check, not merely predicted at analysis time).
- Closed `G-15-25` and `G-15-31` in `15-UAT.md` with `resolved_by`/`resolved_evidence` matching
  the established shape, leading with the band-composition figures for G-15-25 and the
  full-recommendation-implemented accounting for G-15-31 (not resolved on G-15-25's strength
  alone, per the gap's own `related_to` caveat). `G-15-28` and `G-15-10` left untouched.

## Task Commits

1. **Task 1: Supply computed against each agent-day's own eligible pairs, not a desk-wide union** — `2ef0252` (test)
2. **Task 2: Re-run the measurement — false refusals, band sensitivity, and no solver-quality regression** — `4cc01b6` (test)
3. **Task 3: Act on the analysis in writing, and close the last two gap entries** — `5f621a2` (docs)

## Files Created/Modified

- `src/main/java/com/wfm/service/SolverService.java` — `forcedAgentDaysByTimeslotId` (new,
  package-private) and `coveredSlotCountOnDate` helper; per-hour forced-occupancy check added
  inside `requireShiftEnvelopeSeatSupply`, accumulating alongside the day-wide sum
- `src/test/java/com/wfm/service/ShiftEnvelopeSupplyGateTest.java` — two new gap-closure tests
  (band composition, distribution-blind refusal); three weight-branching tests updated to isolate
  the day-wide message via a new `dayWideShortfallDetail` helper
- `src/test/java/com/wfm/service/SolverSeatSupplyGateAccess.java` — new bridge method
  `forcedAgentDaysByTimeslotId`
- `src/test/java/com/wfm/solver/SeatSupplyDistributionAnalysisTest.java` — R2's row now calls
  production via the bridge (`isForcedAt` removed); new "Shipped gate" rule; band-composition
  experiment; renamed/updated `distributionBlindFixture_shippedGateNowRefusesIt`; printed table
  now names, per row, which gap it is evidence for
- `.planning/phases/15-shift-envelope-breaks-library-generation/15-SEAT-SUPPLY-GATE-ANALYSIS.md` —
  appended outcome section (§8), never editing the pre-existing measurement sections
- `.planning/phases/15-shift-envelope-breaks-library-generation/15-UAT.md` — `G-15-25`/`G-15-31`
  resolved with evidence; `G-15-28`/`G-15-10` untouched

## Decisions Made

See `key-decisions` in frontmatter. Notably: R2 ships exactly as measured (no stronger,
unmeasured model adopted); per-hour violations consolidate to one error per date, not one per
offending hour; the two pre-existing gaps close via one combined production check without being
treated as the same gap (each keeps its own leading evidence in `15-UAT.md`).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical] Added `SolverSeatSupplyGateAccess.forcedAgentDaysByTimeslotId`, a
file not listed in `15-20-PLAN.md`'s `files_modified` frontmatter**
- **Found during:** Task 2 (re-measuring the analysis harness against the shipped gate)
- **Issue:** Task 2's own action text requires the analysis harness to call the SHIPPED
  `forcedAgentDaysByTimeslotId` rather than keep a test-local reimplementation (explicitly named
  as this phase's third recurrence of the duplicate-rule defect class, T-15-20-04, "critical"
  severity in the plan's own threat register). `forcedAgentDaysByTimeslotId` is package-private in
  `com.wfm.service`; `SeatSupplyDistributionAnalysisTest` lives in `com.wfm.solver` and cannot
  call it directly. Task 2's declared `<files>` list only names
  `SeatSupplyDistributionAnalysisTest.java`, which cannot alone satisfy the plan's own explicit
  instruction.
- **Fix:** Added one bridge method to the pre-existing `SolverSeatSupplyGateAccess` test-support
  class (already public, already exists for exactly this purpose — bridging package-private
  `SolverService` methods to other test packages), mirroring its existing
  `requireShiftEnvelopeSeatSupply` bridge exactly. No production visibility was widened; the
  bridge is test-only source.
- **Files modified:** `src/test/java/com/wfm/service/SolverSeatSupplyGateAccess.java`
- **Verification:** `SeatSupplyDistributionAnalysisTest` and
  `ShiftEnvelopeSupplyGateTest`/`ShiftEnvelopeSupplyInvariantTest`/`ShiftDeskEndToEndRegressionTest`
  all pass; `git diff --stat -- src/main` shows only the intended `SolverService.java` change.
- **Committed in:** `4cc01b6` (Task 2 commit)

---

**Total deviations:** 1 auto-fixed (Rule 2 — a small, additive test-infrastructure file needed to
satisfy the plan's own explicit, threat-register-backed anti-duplication instruction).
**Impact:** None on production behaviour or plan scope — the added method is test-only, mirrors an
existing precedent exactly, and is what makes Task 2's own stated requirement achievable at all.

## Issues Encountered

None beyond the deviation above. No node-repair cycles were needed on any task.

## Known Stubs

None.

## User Setup Required

None — no external service configuration required.

## `SolverQualityGuardTest` — What It Does and Does Not Prove Here

`./gradlew test --tests "com.wfm.solver.SolverQualityGuardTest"` — 10/10 pass. This proves the new
per-hour check did NOT leak into solver search quality (the guard solves a live-shape synthetic
desk five times and checks structural invariants plus a violation-count ceiling). It is
deliberately NOT routed through `requireShiftEnvelopeSeatSupply` (`15-14-PLAN.md` key_links,
re-confirmed in `15-VERIFICATION.md`'s Key Link table), so this green run is evidence about search
quality only — it is NOT evidence that the gate itself is correct. That claim rests on
`ShiftEnvelopeSupplyGateTest`, `ShiftEnvelopeSupplyInvariantTest`, `ShiftDeskEndToEndRegressionTest`
(25/25) and `SeatSupplyDistributionAnalysisTest` (13/13, all against the shipped gate or its
bridge).

## Full Suite

`./gradlew test` — **635 tests, 0 failures, 0 errors, 2 skipped**, against the 632/0/0/2 baseline
this executor was handed. The 3-test increase is exactly this plan's new tests: 2 in
`ShiftEnvelopeSupplyGateTest` (Task 1), 1 in `SeatSupplyDistributionAnalysisTest` (Task 2); no
test-count regression anywhere else.

## Next Phase Readiness

- This plan is the last plan of phase 15 (`15-20-PLAN.md`'s own frontmatter: `wave: 3`,
  `depends_on: [15-18, 15-19]`, no further plans reference it as a dependency). All seven gaps this
  phase's gap-closure round tracked are now accounted for: `G-15-21`/`G-15-24` (plan 15-18),
  `G-15-25`/`G-15-31` (this plan) resolved; `G-15-22`/`G-15-26`/`G-15-23`/`G-15-32` resolved by
  earlier plans (15-15/15-16/15-17); `G-15-28` (operator-owned demand-data correction, in
  progress) and `G-15-10` (`closed_pending_retest`, a live-deployment retest obligation) remain
  exactly as `15-UAT.md` records them — neither is this round's to close.
- Phase 15 is ready for `/gsd-verify-work 15` and phase-level verification against ROADMAP.md's
  success criteria. The seat-supply gate's known residual scope boundaries (R0/R2 redundancy
  unmeasured, R2 runtime cost structurally bounded but not wall-clock-benchmarked, the 23
  referenced-but-not-individually-re-run pre-existing fixtures) are named in
  `15-SEAT-SUPPLY-GATE-ANALYSIS.md` §8.4 and in `G-15-31`'s own `resolved_evidence` — none of them
  contradicts the recommendation that was implemented; they are the analysis's own honestly-stated
  scope boundary, carried forward rather than silently dropped.

---
*Phase: 15-shift-envelope-breaks-library-generation*
*Completed: 2026-09-02*

## Self-Check: PASSED

All 7 tracked files verified present on disk (`[ -f ]`); all three commit hashes (`2ef0252`,
`4cc01b6`, `5f621a2`) verified present via `git log --oneline --all`; full suite confirmed
635/0/0/2 this session (up from the 632/0/0/2 baseline); `git diff` confirms no lines touching
`G-15-28`/`G-15-10` in `15-UAT.md`.
