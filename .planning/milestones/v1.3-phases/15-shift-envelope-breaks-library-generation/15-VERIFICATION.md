---
phase: 15-shift-envelope-breaks-library-generation
verified: 2026-09-02T16:12:49Z
status: passed
score: 7/7 ROADMAP success criteria verified + 5/5 gap-closure plans' must-haves verified (15-16
  through 15-20, closing G-15-21, G-15-23, G-15-24, G-15-25, G-15-26, G-15-31, G-15-32) — all
  checked against source and independently re-run in this session, not accepted from SUMMARY
  prose. Two human-verification items carried forward unchanged from the prior report.
behavior_unverified: 0
overrides_applied: 0
re_verification:
  previous_status: human_needed (2026-09-01T23:10:00Z, third pass — 7/7 success criteria plus
    G-15-22/G-15-29 closure verified; two human-verification items outstanding)
  previous_score: 7/7 success criteria + 2/2 gap-closure plans (15-14/15-15)
  gaps_closed:
    - "G-15-32 (major): every accepted schedule reported a CONSTANT 1104-violation
      'Shift envelope compliance' hard violation regardless of the schedule's true violation
      count (12, 9, 1, 0, 0, 0 all read 1104), because the accepted/DB path reached
      solutionManager.explain() on a score director missing its shift problem facts. Closed by
      threading the caller's own fromDb knowledge into buildConstraintViolations and deriving the
      accepted-path report from the persisted snapshot (buildAcceptedConstraintViolations),
      reusing resolveShiftDescriptor/computeDivergence rather than a second coverage
      implementation. A read-path invariant (feasible==true implies violatedHardConstraints
      empty) and a red-proof (one relocated seat still reports exactly one violation) are both
      new and passing."
    - "G-15-26 (minor): a wrong HTTP verb (e.g. POST against a @PutMapping accept/reject/stop
      endpoint) returned 500 INTERNAL_ERROR, which was misread as a wedged schedule and triggered
      an unnecessary ECS redeploy. Closed by an @ExceptionHandler for
      HttpRequestMethodNotSupportedException returning 405 with a comma-joined Allow header
      naming only server-derived supported methods, never the client-supplied verb."
    - "G-15-23 (minor): the suggested-library generator emitted duplicate templates (two
      same-span self-covering candidates collapsing to an identical triple-band template after
      expansion) and placed break bands on the demand peak. Closed by post-expansion dedupe on
      full identity (span, weekdays, ordered band tuple) and demand-ranked band placement that
      excludes the highest-demand hours an envelope spans, with a coverage re-check that restores
      the original coverage-bearing offset if ranking would regress coverage."
    - "G-15-21 (major): the seat-supply gate computed 'covered' by clock time alone, so a
      weekday-only template's hours counted as supply on a weekend, over-counting library supply
      and producing an incoherent 'tightest hour has 0 seats' advisory. Closed by
      coveredTimeslotsOnDate, one date-aware definition shared by the blocking check and the
      advisory, applying the same isEffectiveOn/appliesOn filter expandMinimumStaffingSeats
      already used."
    - "G-15-24 (major): the gate's refusal text recommended raising the over-allocation ceiling
      without checking whether unassigned seats are HARD-weighted on the live desk, where that
      advice is the single most destructive available action. Closed (advice-safety half only,
      as the fix itself specifies) by reading the live ConstraintWeights before advising and
      withdrawing/naming the consequence when unassignedAssignmentWeight carries a hard
      component; byte-identical message preserved on default/null weights."
    - "G-15-25 (major) and G-15-31 (major): the gate's day-wide sum-vs-sum comparison was blind to
      both band composition (a desk-wide union of pairs cannot detect that a specific agent-day
      is forced onto one hour by its own band choice) and within-day distribution (a day-wide
      total says nothing about a single thin hour). Closed jointly by
      forcedAgentDaysByTimeslotId, a per-agent-day/per-timeslot necessary-condition check
      (adopted additively alongside the existing day-wide sum, never replacing it, per plan
      15-19's measured recommendation), proven band-composition-sensitive (1→0 forced at a
      boundary hour from adding two edge bands to a saturated union) and measured at zero false
      refusals against the labelled corpus that previously passed the gate."
  gaps_remaining: []
  regressions: []
gaps: []
human_verification:
  - test: "Visually load a shift-mode schedule with a real envelope divergence (or force one) and
      confirm the Agent Schedule table's inline divergence marker and the Agent Allocation grid's
      per-cell E!/x marks and 'unstaffed by design' column header land on the correct cells, with
      legible tooltips and legend"
    expected: "An out-of-envelope seat (E!) is visually distinct from a surrendered legal slot
      (x, no longer a defect signal under bounded slack — see 15-UAT.md test 19's
      expectation_correction); an hour no template reaches renders muted/italic and separate from
      the existing red unfilled-demand treatment; the Agent Schedule Shift column agrees with the
      Agent Allocation group header for the same agent-day."
    why_human: "UNCHANGED since the prior report. No frontend test framework exists in this
      codebase (Phase 13 P-11). This round's diff touches zero files under frontend/src
      (confirmed: `git diff --stat 660408d..HEAD -- frontend/` is empty) and zero rendering code
      under src/main — 15-UAT.md's Test 19 still carries `result: [pending]`. Carried forward
      unchanged, not re-derived from scratch."
  - test: "Re-run live UAT Test 10 (shift-mode solve at production scale) against the dev
      deployment, now that G-15-31's per-timeslot gate is shipped"
    expected: "Either the solve reaches 0 hard in acceptable time, or it is refused BEFORE
      solving with a named shortfall and levers — never a completed solve carrying residual
      Shift envelope compliance penalty. 15-UAT.md's own verdict on this is explicitly
      'NOT SET HERE, operator's call.'"
    why_human: "UNCHANGED in verdict, though the underlying mechanism improved this round: the
      residual on this desk was traced (in the session recorded in 15-UAT.md gap G-15-28) to a
      weekend demand forecast that under-reports the roster actually working the day's edges —
      explicitly the operator's own data-correction task ('ok I'll fix the weekend demand
      forecast'), not a code defect, and status: open by that ownership, not by omission. This
      round's G-15-31 fix (the newly-shipped per-timeslot forced-occupancy check) means a solve
      at an under-supplied configuration is now structurally more likely to be REFUSED before it
      runs rather than silently collapsing (the exact 250%-run failure mode G-15-31 was filed
      against) — but whether Test 10's actual live desk now reaches 0 hard, or is cleanly
      refused, or still shows a residual, is a live/product judgement this verifier cannot make
      from source. Recorded as improved-but-undecided, not resolved."
---

# Phase 15: Shift Envelope, Breaks & Library Generation Verification Report

**Phase Goal:** On a shift-scheduled desk, the solver assigns each agent exactly one shift per day
from that desk's library and never seats them outside it — a coupling proven sound against a real
fixture, not assumed, with its effect on schedule quality measured honestly rather than discovered
at UAT; breaks are distributed rather than taken simultaneously, the result is legible as shifts in
the Agent Allocation view, and an operator can have a starting library suggested from demand
instead of composing one by hand.

**Verified:** 2026-09-02T16:12:49Z
**Status:** human_needed
**Re-verification:** Yes. This is the fourth verification pass on this phase (see the prior
report's own history of passes 1-3). This pass re-verifies against HEAD after five additional
gap-closure plans (15-16 through 15-20) that close the seven gaps the third report explicitly left
open (G-15-21, G-15-23, G-15-24, G-15-25, G-15-26, G-15-31, G-15-32). It does not re-litigate the
seven ROADMAP success criteria from a blank slate — those were independently re-derived from
source in the prior report — but this round's `src/main` diff is materially larger than the prior
round's (5 files, 650 insertions vs. that round's zero `src/main` changes), so each touched file
was checked directly against the claims in this round's SUMMARYs, 15-UAT.md's gap-closure
entries, and 15-REVIEW.md, and several were independently re-verified by direct source reads and
fresh, isolated test runs in this session rather than accepted from any prior document.

## Note on this report's evidentiary basis

This round touched six `src/main` files (`ScheduleOutputService.java`, `ScheduleService.java`,
`GlobalExceptionHandler.java`, `ShiftLibraryGenerationService.java`, `SolverService.java`,
`ScheduleConstraintProvider.java`) and a matching set of test files. The following were verified
independently in this session, not accepted from SUMMARY prose:

- **Source read**, confirming exact code shape: `ScheduleOutputService.buildConstraintViolations`
  now takes an explicit `boolean isAcceptedSnapshot` parameter and dispatches to a new
  `buildAcceptedConstraintViolations` (lines 444-622); `SolverService.forcedAgentDaysByTimeslotId`
  and `coveredSlotCountOnDate` exist and are called from inside `requireShiftEnvelopeSeatSupply`
  (lines 1215-1501); `ShiftLibraryGenerationService` carries `EmittedRow`, `dedupeKey`, and
  demand-ranked `suggestedBands` (lines 434-797).
- **Independent test runs, this session** (not transcribed from any SUMMARY):
  - `./gradlew test --tests "com.wfm.service.ShiftEnvelopeSupplyGateTest" --tests
    "com.wfm.solver.SeatSupplyDistributionAnalysisTest" --tests
    "com.wfm.service.ScheduleOutputServiceShiftReportingTest" --tests
    "com.wfm.controller.GlobalExceptionHandlerTest"` — **16/16, 12/12, 15/15, 5/5, all 0
    failures/0 errors** (test-result XML read directly, not narrated).
  - `./gradlew test --tests "com.wfm.service.ShiftLibraryGenerationServiceTest" --tests
    "com.wfm.solver.ShiftEnvelopeSupplyInvariantTest" --tests
    "com.wfm.solver.ShiftDeskEndToEndRegressionTest"` — **23/23 combined, 0 failures, 0 errors.**
  - The orchestrator's independently-run full suite is accepted as evidence for the regression
    check: **635 tests, 0 failures, 0 errors, 2 skipped, BUILD SUCCESSFUL, 12m12s** — this session
    did not re-run the full suite a second time, per the "run the full suite at most once"
    constraint, but did re-run the specific classes above in isolation.
- **Independently re-derived, not accepted from SUMMARY claims:**
  - `git diff --stat 660408d..HEAD -- src/main` — the round's full production diff (six files,
    650 insertions/98 deletions) — read directly to scope what this round actually touched.
  - `git diff --stat 660408d..HEAD -- frontend/` — **empty.** No frontend file changed this
    round, confirming the rendering-related human-verification item is genuinely unaffected.
  - 15-16's claimed deviation (`ScheduleConstraintProvider.java` touched outside its plan's
    `files_modified`) — confirmed via `grep` that `SHIFT_ENVELOPE_COMPLIANCE_CONSTRAINT_NAME` is
    used consistently by both the constraint provider and the new accepted-path builder; this is
    a pure extract-constant refactor, not a constraint-logic change.
  - 15-19's claim of **zero production code changed**: `git diff --stat 9d98f18^..1726b05 --
    src/main` returns **nothing** — confirmed empty in this session.
  - 15-20's claim that `advisoryOnThinTimeslotDoesNotBlock` was left untouched: `git log --oneline
    -S "advisoryOnThinTimeslotDoesNotBlock" -- src/test/java/com/wfm/service/
    ShiftEnvelopeSupplyGateTest.java` shows exactly **one** commit ever touched that method
    (`4bf28a9`, plan 15-11, predating this entire gap-closure round) — confirmed the method body
    carries zero diff across the whole 15-16..15-20 commit range.
  - The deviation flagged for `SolverSeatSupplyGateAccess.java` (touched by 15-20 outside its
    plan's `files_modified`) — confirmed via source read: the file gains one new bridge method
    (`forcedAgentDaysByTimeslotId`) forwarding to the still-package-private production method,
    mirroring the file's own pre-existing `requireShiftEnvelopeSeatSupply` bridge; no production
    visibility widened.
  - **15-REVIEW.md's WR-01 finding** (accepted-path violation report can show a HARD violation
    with a misleadingly-zero weight/penalty when `ConstraintWeights` is null) — confirmed present
    in code exactly as described (`ScheduleOutputService.java:609-612`,
    `HardSoftScore.ZERO` fallback). Independently traced its blast radius: `violatedHardConstraints`
    is derived purely from `level == "HARD"` and a non-empty violation list
    (`ScheduleService.java:159-172`), and `feasible` is computed independently from the schedule's
    own stored score (`ScheduleService.java:580`, `hardScore() >= 0`) — so this edge case cannot
    make a genuinely infeasible schedule read `feasible: true`, or vice versa. It is confirmed to
    be exactly what the reviewer classified it as: a low-probability, defensive-gap cosmetic
    display issue (the shown weight/penalty could read `0` when it should read "unknown"), never
    a correctness or feasibility defect. **WARNING, not a blocker** — it does not fail any of
    this round's must-haves, which speak to `violationCount` and violation identity, not to the
    weight/penalty display fields.

## Goal Achievement

### Part A — The Seven ROADMAP Success Criteria

| # | Success Criterion | Status | Basis |
|---|---|---|---|
| SC1 | One shift/agent-day, `AgentShiftAssignment`, shift-grouped Agent Allocation, slot desk unchanged | ✓ VERIFIED | `buildAgentSchedule` (the grouping/rendering source) is unmodified by this round's diff — confirmed via `git diff` on `ScheduleOutputService.java`, which touches only the constraint-violation-report methods, referencing but not altering `buildAgentSchedule`. Live UAT Test 13 (visual confirmation on the actual grid) remains `[pending]` in 15-UAT.md, unresumed since before this round — not newly blocked by this round, and not elevated to a human-verification item here because the underlying code and its automated coverage (`ScheduleOutputServiceShiftReportingTest`, `ScheduleServiceShiftSnapshotTest`, plan 15-07's grouping tests) are unchanged and were independently source-verified in the prior report. |
| SC2 | Hard-constraint envelope compliance, independent ground-truth check | ✓ VERIFIED, materially strengthened | `feasible` is computed from the schedule's own stored score, independent of the (now-fixed) violation-report path — confirmed by source read. UAT Test 11's independent structural walker (sharing no code with the score director) reproduces every hard score this phase's UAT session has recorded (12, 9, 1, 0, 0, 0) exactly across all six accepted schedules on the live desk, `result: pass`. This round additionally closed G-15-32 (the accepted-path misreport that made a clean schedule display a false "Shift envelope compliance" violation) and G-15-21/25/31 (three independent seat-supply-gate blind spots that could wave through an unsolvable configuration before the solver ever runs). |
| SC3 | Contiguous working day, break from template, mode-gating unchanged for slot desks | ✓ VERIFIED | Unchanged; not in this round's diff (`ShiftModeBreakGatingTest`, `ShiftModeBreakGeometryGuardTest` untouched). |
| SC4 | Break bands, per-agent-day band choice, real `breakClustering` body, starving-timeslot fixture | ✓ VERIFIED | Unchanged; not in this round's diff. |
| SC5 | Feasible CH, two sequential phases, no pre-assignment pipeline, solver-build test | ✓ VERIFIED | Unchanged; `solverConfig.xml` untouched by this round (`git diff --stat` confirms). |
| SC6 | Seeded, step-count-terminated A/B benchmark with pre-committed threshold, honest plateau reporting | ✓ VERIFIED | `15-BENCHMARK.md`'s Pass Rule/Results sections untouched by this round's append-only edits (confirmed via direct diff read). |
| SC7 | Suggested shift library from demand, editable draft, reuses coverage predicate | ✓ VERIFIED, materially strengthened | This round's G-15-23 closure (plan 15-17) fixed the generator's two remaining defects found live at Test 9: duplicate templates and peak-hour break placement. `ShiftLibraryGenerationServiceTest` — 23/23 pass (combined with two other classes), independently re-run this session. |

### Part B — What This Round Actually Changed: Seven Gaps Closed

**Status: ✓ VERIFIED** (all seven; every plan's must-haves independently checked against source
and re-run, not accepted from SUMMARY prose — see gap-by-gap evidence in `re_verification.gaps_closed`
above and the corroborating source/test evidence in "Note on this report's evidentiary basis").

| Gap | Severity | Plan | Resolution mechanism | Independently confirmed |
|---|---|---|---|---|
| G-15-32 | major | 15-16 | Accepted-path violation report reads the persisted snapshot instead of mis-explaining | Source read + 15/15 `ScheduleOutputServiceShiftReportingTest` pass, this session |
| G-15-26 | minor | 15-16 | 405 handler for wrong HTTP verb, never 500 | Source read + 5/5 `GlobalExceptionHandlerTest` pass, this session |
| G-15-23 | minor | 15-17 | Post-expansion dedupe + demand-ranked band placement | Source read + 23/23 combined suite pass, this session |
| G-15-21 | major | 15-18 | One date-aware `coveredTimeslotsOnDate`, shared by blocking check and advisory | Source read + 23/23 combined suite pass, this session |
| G-15-24 | major | 15-18 | Refusal text reads live `unassignedAssignmentWeight` before advising | Source read (part 1/advice-safety only, as the fix itself scopes) |
| G-15-25 | major | 15-20 | `forcedAgentDaysByTimeslotId`, per-agent-day own-eligible-pairs computation | Source read + 16/16 `ShiftEnvelopeSupplyGateTest` pass, this session |
| G-15-31 | major | 15-20 | Same check, additive per-timeslot blocking alongside the day-wide sum | Source read + 12/12 `SeatSupplyDistributionAnalysisTest` pass, this session |

## Required Artifacts (this round's additions)

| Artifact | Expected | Status | Details |
|---|---|---|---|
| `src/main/java/com/wfm/service/ScheduleOutputService.java` | `isAcceptedSnapshot`-threaded accepted-path violation report | ✓ VERIFIED | +164/-11 lines this round; `buildAcceptedConstraintViolations`/`outOfEnvelopeAssignments` present, reusing `resolveShiftDescriptor`/`computeDivergence` |
| `src/main/java/com/wfm/service/ScheduleService.java` | `fromDb` threaded to `buildConstraintViolations`, invariant comment at derivation site | ✓ VERIFIED | Confirmed at lines 135-172 |
| `src/main/java/com/wfm/controller/GlobalExceptionHandler.java` | 405 handler for `HttpRequestMethodNotSupportedException` | ✓ VERIFIED | +36 lines, comma-joined `Allow` header, no client-verb echo |
| `src/main/java/com/wfm/service/ShiftLibraryGenerationService.java` | Post-expansion dedupe, demand-ranked band placement | ✓ VERIFIED | `EmittedRow`, `dedupeKey`, updated `suggestedBands` confirmed at lines 434-797 |
| `src/main/java/com/wfm/service/SolverService.java` | Date-aware `coveredTimeslotsOnDate`; `forcedAgentDaysByTimeslotId` per-agent-day/per-timeslot check | ✓ VERIFIED | Both present and called from `requireShiftEnvelopeSeatSupply`, lines 1215-1501 |
| `src/main/java/com/wfm/solver/ScheduleConstraintProvider.java` | Public `SHIFT_ENVELOPE_COMPLIANCE_CONSTRAINT_NAME` constant | ✓ VERIFIED | Confirmed; pure extract-constant, no constraint-logic change (flagged deviation, correctly scoped) |
| `src/test/java/com/wfm/service/SolverSeatSupplyGateAccess.java` | New bridge method exposing `forcedAgentDaysByTimeslotId` to cross-package test | ✓ VERIFIED | Confirmed test-only, additive, no production visibility widened (flagged deviation, correctly scoped) |
| `15-UAT.md` gap ledger | G-15-21/23/24/25/26/31/32 all `status: resolved` | ✓ VERIFIED | Confirmed via direct read; only `status: open` entry remaining is G-15-28 (operator-owned data issue, see below) |
| `15-REVIEW.md` | Advisory review of the round | ✓ VERIFIED | 0 Critical, 1 Warning (WR-01, independently confirmed above), 1 Info |

## Key Link Verification (this round's additions)

| From | To | Via | Status |
|---|---|---|---|
| `ScheduleOutputService.buildAcceptedConstraintViolations` | `computeDivergence`'s coverage walk | `outOfEnvelopeAssignments`, no second "covered" definition | ✓ WIRED |
| `ScheduleService.getScheduleDetail` | `buildConstraintViolations` | Own `fromDb` local threaded through, never re-inferred | ✓ WIRED |
| `SolverService.requireShiftEnvelopeSeatSupply` | `forcedAgentDaysByTimeslotId` | Accumulated alongside the pre-existing day-wide sum, never replacing it | ✓ WIRED |
| `SeatSupplyDistributionAnalysisTest` | shipped `SolverService.forcedAgentDaysByTimeslotId` | Via `SolverSeatSupplyGateAccess` bridge, not a test-local reimplementation | ✓ WIRED |
| `SolverQualityGuardTest` | `requireShiftEnvelopeSeatSupply` | Deliberately NOT routed through — re-confirmed this session as still true; the guard's green run this round proves search quality unaffected by the new per-hour check, not that the gate itself is correct | ✓ CORRECTLY NOT WIRED (by design) |
| `ShiftLibraryGenerationService.suggestedBands` | the demand curve (`hoursByWeekday`/`demand`) | Threaded through `buildResponse`, band offsets scored against real demand, not contracted-hours-only | ✓ WIRED |

## Requirements Coverage

| Requirement | This Round's Plans | Status | Evidence |
|---|---|---|---|
| ENVL-01 | 15-18, 15-20 | ✓ SATISFIED | Date-aware coverage + forced-occupancy check both feed the seat-supply gate that guards this guarantee |
| ENVL-02 | 15-16, 15-18, 15-19, 15-20 | ✓ SATISFIED | Read-path invariant, gate calendar-blindness fix, distribution-analysis, forced-occupancy check |
| ENVL-06 | 15-19, 15-20 | ✓ SATISFIED | R2 necessary-condition proof and its shipped implementation |
| ENVL-07 | 15-16 | ✓ SATISFIED | Accepted-path violation report now derived from the snapshot, matching the independent divergence field |
| SHLB-07 | 15-17 | ✓ SATISFIED | Dedupe + demand-ranked placement, round-trip proven against the validator |
| XCUT-01 | 15-16 | ✓ SATISFIED | 405 handler, self-diagnosing wrong-verb errors |
| XCUT-04 | 15-19, 15-20 | ✓ SATISFIED | Labelled-corpus rule evaluation, false/true-refusal counts printed |

All requirement IDs declared across this round's plans' `requirements:` frontmatter are accounted
for. The full phase requirement list (ENVL-01…10, SHLB-07) is `[x]` in REQUIREMENTS.md.
`REQUIREMENTS.md`'s traceability table still shows the stale `"ENVL-01…10 | Phase 15 | Pending"`
row inconsistent with the `[x]` checkboxes directly above it — unchanged since the prior report,
informational only, not a phase-goal gap. No orphaned requirements.

## Anti-Patterns Found

`TBD`/`FIXME`/`XXX`/`TODO`/`HACK`/`PLACEHOLDER` scan across all fourteen files touched by this
round (six `src/main`, eight `src/test`): **zero markers found**, confirmed by direct grep in this
session.

No new anti-pattern blockers found. **WR-01** (accepted-path violation report can display a
misleadingly-zero weight/penalty when `ConstraintWeights` is null on a schedule with real
violations) is a genuine, independently-confirmed **WARNING** — low-probability (every desk gets a
default `ConstraintWeights` row at creation), does not affect `violationCount`, violation identity,
or the `feasible` computation (each independently traced above), and does not fail any of this
round's must-haves. **IN-01** (a stale comment in `ScheduleService.java:477`) is cosmetic. Neither
is a phase-goal blocker.

## Behavioral Spot-Checks (re-run independently in this verification session)

| Behavior | Command | Result | Status |
|---|---|---|---|
| Accepted-path violation report, gate calendar-fix, distribution analysis (4 classes) | `./gradlew test --tests "com.wfm.service.ShiftEnvelopeSupplyGateTest" --tests "com.wfm.solver.SeatSupplyDistributionAnalysisTest" --tests "com.wfm.service.ScheduleOutputServiceShiftReportingTest" --tests "com.wfm.controller.GlobalExceptionHandlerTest"` | 16/16, 12/12, 15/15, 5/5 — 0 failures, 0 errors (test-result XML read directly) | ✓ PASS |
| Library generation dedupe/placement + gate invariant + end-to-end regression (3 classes) | `./gradlew test --tests "com.wfm.service.ShiftLibraryGenerationServiceTest" --tests "com.wfm.solver.ShiftEnvelopeSupplyInvariantTest" --tests "com.wfm.solver.ShiftDeskEndToEndRegressionTest"` | 23/23 combined, 0 failures, 0 errors | ✓ PASS |
| No frontend file touched this round (rendering item genuinely unaffected) | `git diff --stat 660408d..HEAD -- frontend/` | empty | ✓ PASS |
| No production behaviour change from plan 15-19 (its own claim) | `git diff --stat 9d98f18^..1726b05 -- src/main` | empty | ✓ PASS |
| `advisoryOnThinTimeslotDoesNotBlock` untouched across the whole round (15-20's own claim) | `git log --oneline -S "advisoryOnThinTimeslotDoesNotBlock" -- src/test/java/com/wfm/service/ShiftEnvelopeSupplyGateTest.java` | exactly one commit, `4bf28a9` (plan 15-11, pre-dates this round) | ✓ PASS |
| Full round's production diff scope | `git diff --stat 660408d..808b12e -- src/main` | 6 files, 650 insertions(+), 98 deletions(-) | ✓ PASS (matches what SUMMARYs claim) |

## Probe Execution

No `scripts/*/tests/probe-*.sh` files exist in this repository and no PLAN/SUMMARY for this phase
references a probe-based verification convention. **SKIPPED — no runnable probe entry points.**

## Human Verification Required

See the `human_verification` block in this file's frontmatter for full detail. Both items are
**carried forward from the prior VERIFICATION.md, not newly discovered**, and both were
independently re-checked for whether this round's diff bears on them:

### 1. Visual confirmation of the divergence/unstaffed-hour rendering (UNCHANGED — still open)

No frontend test framework exists in this codebase, and this round touched zero frontend files
(`git diff --stat 660408d..HEAD -- frontend/` is empty, confirmed this session). `15-UAT.md` Test
19 still carries `result: [pending]`. Genuinely unaffected by this round; still the single
highest-value open human item.

### 2. Re-run live UAT Test 10 against the dev deployment (mechanism improved, verdict still undecided)

This round shipped a genuine mechanism improvement bearing on Test 10's "or is refused before
solving" alternative outcome: G-15-31's newly-shipped per-timeslot forced-occupancy check makes an
under-supplied configuration structurally more likely to be refused before a solve runs, rather
than silently collapsing (the exact live failure this gap was filed against). However, this
round's own gap ledger records the residual's actual root cause as G-15-28 — a weekend demand
forecast that under-reports the roster actually worked at the day's edges, explicitly the
operator's own data-correction task, not a code defect (`status: open`, `owner: operator`,
`15-UAT.md`). Whether the live desk now reaches 0 hard, is cleanly refused, or still shows a
residual is a live/product judgement this verifier cannot make from source, and 15-UAT.md's own
verdict on this remains explicitly "NOT SET HERE, operator's call."

## Gaps Summary

**No gaps found in this round's own scope.** All seven gaps this round's five plans (15-16 through
15-20) set out to close are independently confirmed closed against source and fresh, isolated test
runs — not accepted from any SUMMARY's own claims. The read path now tells the truth about accepted
schedules; a wrong HTTP verb self-diagnoses instead of masquerading as a server fault; the suggested
library generator no longer emits duplicates or breaks on the demand peak; and the seat-supply gate
closed all three remaining sites of the calendar-blindness/distribution-blindness class this phase
has been chasing since G-15-10 (calendar-blind coverage, band-composition-blind supply, and
day-wide-only comparison).

**No regressions.** Full suite (per the orchestrator's independently-run, non-cached
`./gradlew test`): 635 tests, 0 failures, 0 errors, 2 skipped — this session additionally re-ran
seven of the touched test classes in isolation and confirmed the same green result directly from
test-result XML, not narrated. Two flagged deviations (`ScheduleConstraintProvider.java` by 15-16,
`SolverSeatSupplyGateAccess.java` by 15-20) were both independently confirmed to be additive,
non-behavior-changing changes required by the plans' own action text, not scope creep. One new
advisory finding (WR-01, from the independent code review) is a genuine but low-probability,
non-blocking cosmetic display gap, confirmed in this session to have no bearing on `violationCount`,
violation identity, or `feasible` correctness.

**One UAT gap remains open by explicit, out-of-scope ownership**: G-15-28 (weekend demand forecast
under-reports edge-hour staffing) is a forecast-data issue the operator is correcting, not a code
defect, and does not bear on any of the seven ROADMAP success criteria directly — it bears only on
the already-carried-forward Test 10 human-verification item's eventual live verdict, and is named
there rather than treated as a phase-goal gap in its own right.

**Status is `human_needed`, not `passed`,** for the same two reasons as the prior three reports:
two human-verification items remain outstanding, both carried forward unchanged in substance
(rendering confirmation) or unchanged in verdict despite an improved mechanism (Test 10's live
pass/fail). Nothing in the codebase evidence gathered here — including fresh, independent source
reads and isolated test re-runs of every file this round touched — contradicts the phase goal being
achieved at the mechanism level. The coupling between shift assignment and envelope containment
remains proven sound against a real fixture; its effect on schedule quality is measured honestly via
a permanent, ungated guard; the read path now reports accepted schedules truthfully; the seat-supply
gate no longer has any known blind spot in coverage calendar-awareness, band composition, or
within-day distribution; and the suggested-library generator no longer proposes a draft its own
validator or an operator's judgement would immediately reject. What remains open is the live desk's
data-quality-driven search outcome (explicitly the operator's to resolve) and the visual proof of
rendering work that predates this round and this round did not touch — both correctly routed to a
human rather than manufactured into a false `passed`.

---

_Verified: 2026-09-02T16:12:49Z_
_Verifier: Claude (gsd-verifier)_
