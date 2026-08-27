---
phase: 15-shift-envelope-breaks-library-generation
verified: 2026-08-27T18:45:00Z
status: human_needed
score: 7/7 success criteria verified (11/11 requirement-level truths) — automated evidence only
behavior_unverified: 0
overrides_applied: 0
re_verification:
  previous_status: passed (2026-08-27T13:15:00Z, 11/11 — this was BEFORE UAT Test 10 surfaced
    gap G-15-10, so it did not know about the defect this round closes)
  previous_score: 11/11 (pre-G-15-10 scope)
  gaps_closed:
    - "G-15-10 (blocker): shift-mode solve on a real desk converges on an irreducible -19 hard
      score, entirely on Shift envelope compliance, because minimum-staffing seat expansion is
      envelope-blind (D2), the value range's zero-slack equality has no seat-supply precondition
      (D1), the cheapest-hard-violation arbitrage lands the residual on the phase's own headline
      guarantee (D3), and the report layer redraws the envelope around the violating seat so the
      breach cannot be displayed as one (D4)."
  gaps_remaining: []
  regressions: []
gaps: []
deferred:
  - truth: "Blocked-break-hours (breakBlockedHours) has no enforcement point on a shift-scheduled desk"
    addressed_in: "Not yet scheduled — filed in deferred-items.md by explicit operator ruling OR-2
      (out of scope for this round, not rejected). Concrete fix location recorded
      (ShiftTemplateService.validateBands, save-time check)."
    evidence: "deferred-items.md '15-13' section; confirmed still true by direct source read —
      ShiftTemplateService.validateBands (:182 onward) never references breakBlockedHours."
  - truth: "A shift template's envelope is never validated against the desk's operating window at save time"
    addressed_in: "Not yet scheduled — filed in deferred-items.md by explicit operator ruling OR-2.
      Concrete fix location recorded (the dead TimeslotBoundsResponse.endTime() accessor)."
    evidence: "deferred-items.md '15-13' section; confirmed still true by direct source read —
      TimeslotBoundsResponse.endTime() is read by no caller in src/main."
human_verification:
  - test: "Visually load a shift-mode schedule with a real envelope divergence (or force one) and
      confirm the Agent Schedule table's inline divergence marker and the Agent Allocation grid's
      per-cell E!/x marks and 'unstaffed by design' column header land on the correct cells, with
      legible tooltips and legend"
    expected: "Matches 15-12's own description: an out-of-envelope seat is marked distinctly from a
      surrendered legal slot, an hour no template reaches renders muted/italic and separate from
      the existing red unfilled-demand treatment, and the Agent Schedule Shift column agrees with
      the Agent Allocation group header for the same agent-day."
    why_human: "No frontend test framework exists in this codebase (a standing project constraint,
      not new to this phase); 15-12-SUMMARY.md itself marks all three of its own rendering
      must-haves human_judgment: true for exactly this reason. `npm run build` (re-run
      independently for this report) only proves the code compiles and bundles, not that the
      marks are visually legible or correctly positioned."
  - test: "Re-run live UAT Test 10 (shift-mode solve at production scale) against the dev deployment"
    expected: "The StubHub (EN) desk that previously converged on an irreducible -19 hard score
      (all on Shift envelope compliance) now either reaches 0 hard in acceptable time, or is
      refused BEFORE solving with a named shortfall and levers — never a completed/near-complete
      solve carrying residual envelope penalty."
    why_human: "ShiftDeskEndToEndRegressionTest proves this shape-complete property on a hand-built
      fixture reproducing the live defect's SHAPE (staggered multi-template envelopes, zero-demand
      edges) through the real solverConfig.xml — strong automated evidence — but it is not the
      literal live desk/data the UAT gap was filed against, and involves real-time solve behavior
      at production scale this verifier cannot exercise from static code. 15-13's own SUMMARY
      names this exact re-run as the intended closing step, not yet executed as of this report."
---

# Phase 15: Shift Envelope, Breaks & Library Generation Verification Report

**Phase Goal:** On a shift-scheduled desk, the solver assigns each agent exactly one shift per day
from that desk's library and never seats them outside it — a coupling proven sound against a real
fixture, not assumed, with its effect on schedule quality measured honestly rather than discovered
at UAT; breaks are distributed rather than taken simultaneously, the result is legible as shifts in
the Agent Allocation view, and an operator can have a starting library suggested from demand
instead of composing one by hand.

**Verified:** 2026-08-27T18:45:00Z
**Status:** human_needed
**Re-verification:** Yes — this phase previously reported `passed 11/11` before UAT surfaced
gap G-15-10 (blocker). This report supersedes that one: it re-derives every one of the phase's
seven ROADMAP success criteria directly from HEAD (`d490171`), not from the prior VERIFICATION.md,
the gap-closure SUMMARYs, or `15-REVIEW.md`'s own conclusions, and additionally judges whether
G-15-10 is genuinely closed and whether the round's deliberately-deferred items are honestly
recorded.

**Why `human_needed` and not `passed`, despite every automated truth verifying:** every one of the
phase's seven success criteria and every root cause of G-15-10 checks out against the codebase by
direct source read and independently re-run tests (details below) — there are no `gaps`. But this
round added significant new, never-visually-exercised frontend surface (envelope-divergence marks,
a new "unstaffed by design" column treatment) that this codebase has no test framework to assert
against, and the live UAT gap this round exists to close has automated shape-complete evidence but
not yet a literal live-desk re-run. Per this verifier's own decision tree, any non-empty human
verification list routes the status to `human_needed` even when every other truth is VERIFIED —
that rule is followed here rather than argued around.

## Note on this report's evidentiary basis

Every claim below was checked directly against source at commit `d490171` (HEAD) — not accepted
from SUMMARY.md or 15-REVIEW.md prose. Where a test class is cited as passing, it was **re-run
independently in this verification session** (not merely read as green in a prior report):

- `ShiftDeskEndToEndRegressionTest`, `ShiftEnvelopeSupplyInvariantTest`,
  `ShiftModeBreakGeometryGuardTest`, `ShiftEnvelopeSupplyGateTest`,
  `ScheduleOutputServiceShiftReportingTest`, `ShiftModeMinimumStaffingSeatSupplyTest`,
  `ZeroDemandTimeslotCeilingTest` — all seven gap-closure-round test classes, run together:
  `39 tests, 0 failures, 0 errors` (per-class breakdown in the Behavioral Spot-Checks table below).
- `cd frontend && npm run build` — re-run independently: `tsc -b && vite build`, 57 modules,
  clean, no errors.
- The **full backend suite** (`./gradlew test`, no test filter) was also run independently, end to
  end, in this session: `BUILD SUCCESSFUL in 12m 15s`. Aggregated directly from all 87
  `TEST-*.xml` files under `build/test-results/test/` produced by that run:
  **546 tests, 0 failures, 0 errors, 2 skipped** — an exact, independent match to the
  orchestrator's own reported count for this same commit (`d490171`), reproduced byte-for-byte
  rather than accepted on trust. The 2 skips are the pre-existing `-Dwfm.benchmark=true`-gated
  benchmark methods, unrelated to this round.

## Goal Achievement — the Seven ROADMAP Success Criteria

### SC1 — one shift per agent-day, `AgentShiftAssignment`, shift-grouped Agent Allocation, slot desk unchanged (ENVL-01, ENVL-03, ENVL-10, XCUT-01)

**Status: ✓ VERIFIED**

- `AgentShiftAssignment` is a genuine `@PlanningEntity` (one row per working agent-day, D-05),
  distinct from `AgentAssignment` — confirmed by direct read of
  `src/main/java/com/wfm/model/AgentShiftAssignment.java`.
- Per-agent-day eligibility is enforced in the actual value-range provider
  (`getEligibleShiftBandPairs()`, lines 165-178) via `ShiftTemplate#isEffectiveOn(date)` — a prior
  gap (CR-01, UPCOMING/retired templates assignable) is closed and unchanged since the last
  verification; re-confirmed by direct read, not re-accepted.
- `SolverService.requireShiftEnvelopeSeatSupply` (step 10e, `SolverService.java:1147-1260`) now
  makes seat supply inside the envelope a **checked precondition** of every shift-mode solve
  (plan 15-11, closing D1 of G-15-10) — this is new since the prior `passed` verification and is
  the core of what makes "exactly one shift per agent-day" actually achievable rather than merely
  modelled.
- `frontend/src/pages/ScheduleResults.tsx` groups agents by assigned shift in the shift-grouped
  branch (`:592` onward), naming the template and headcount per group (`:660-662`); the slot-mode
  branch (mode guard `:422`) is untouched by any gap-closure commit — confirmed via
  `git diff --unified=0` on the plan-12 commit range, matching the plan's own T-15-12 verification.
- `Schedule.schedulingMode` is a persisted, non-inferred column (V43) written once at accept time
  (CR-02, closed prior to this round, re-confirmed unchanged in `ScheduleService.java`).

### SC2 — hard-constraint envelope compliance, independent ground-truth check (ENVL-02, ENVL-07)

**Status: ✓ VERIFIED**

- `ScheduleConstraintProvider.shiftEnvelopeCompliance` is unchanged, still a genuine hard
  constraint (`ConstraintWeights.shiftEnvelopeComplianceWeight` = `ofHard(1)`).
- `ShiftEnvelopeGroundTruthTest` (plan 15-04's independent walker, outside the score director) —
  unaffected by any gap-closure commit, still present and green.
- New, stronger evidence this round: `ShiftDeskEndToEndRegressionTest` (plan 15-13) is a *second*,
  independently-implemented containment walker (`walkEnvelopeContainment`, deliberately not
  calling `ShiftBandPair#covers` — raw `LocalTime` arithmetic only, so it cannot inherit that
  method's own blind spot) run against a real solve through the actual `solverConfig.xml`, on a
  fixture reproducing the live UAT desk's *shape* (staggered multi-template envelopes, hours
  genuinely outside every envelope, thin/zero edge demand). Re-run independently in this session:
  **3/3 pass**, including the explicit assertion that a completed solve never carries residual
  `"Shift envelope compliance"` hard penalty — the live defect's exact signature.
- `ShiftEnvelopeSupplyInvariantTest` (plan 15-11) proves the D-04 zero-slack value-range equality
  is a **deliberately preserved invariant**, not an accidental one, and that all three "hard score
  cannot reach zero" characterisations from the debug session are now either solved feasibly or
  refused by name before solving. Re-run independently: **6/6 pass**.

### SC3 — contiguous working day, break from template not emergent constraints, mode-gating provably unchanged for slot desks (ENVL-04, ENVL-05, XCUT-05)

**Status: ✓ VERIFIED**

- `ShiftModeBreakGatingTest` (unaffected by gap closure, re-confirmed unmodified by the gap-closure
  commit range) still proves the four emergent break constraints are gated off in SHIFT mode and
  unchanged in SLOT mode.
- `ShiftModeBreakGeometryGuardTest` (plan 15-13's promotion of the diagnose-only characterising
  test) proves, and is honest about, exactly what governs contiguity in SHIFT mode: not a
  per-agent geometry constraint, but the conjunction of the D-04 zero-slack value range +
  `shiftEnvelopeCompliance` + the D1 seat-supply gate — cross-referencing
  `ShiftDeskEndToEndRegressionTest` for the property it does not itself prove. Re-run
  independently: **4/4 pass**.
- XCUT-05's classification table (`ScheduleConstraintClassification.java:259-271`) correctly
  reclassifies "Minimum staffing" from `MODE_AGNOSTIC` to `MODE_GATED` (a stale row this round
  caught and fixed) — the constraint body is shared but its reachable domain is now genuinely
  mode-dependent because seat supply is (plan 15-09). Confirmed by direct source read.

### SC4 — break bands, per-agent-day band choice, real `Break clustering` body, demonstrated on a starving-timeslot fixture (ENVL-08, ENVL-09)

**Status: ✓ VERIFIED**

Unaffected by the gap-closure round (no gap-closure commit touches `ShiftTemplateBreakBand`,
band-choice value-range, or `breakClustering`'s body — confirmed via `git diff --stat
55c9ae7..be3765c`). Re-confirmed present and unchanged by direct source read: `ShiftTemplateBreakBand`
(child table, N-per-template), band choice as a planning variable resolved per agent-day, and
`ScheduleConstraintProvider.breakClustering` penalising agents-on-break exceeding
`breakClusterThresholdPct` of a timeslot's assigned agents (not the prior
`penalizeConfigurable(a -> 0)` placeholder). `BandCapacityConstraintTest` and
`BreakClusteringConstraintTest` are part of the 546-test full-suite run confirmed green above, and
were not in this round's own scope per every gap-closure SUMMARY's no-regression check.

### SC5 — feasible CH with two sequential phases, no pre-assignment pipeline, solver-build test (ENVL-06, XCUT-03)

**Status: ✓ VERIFIED**

`solverConfig.xml` is untouched by the gap-closure round (confirmed: `git diff --stat
55c9ae7..be3765c -- src/main/resources` returns nothing under that path). `SolverConfigBuildTest`
(unaffected) still builds a real solver from the shipped XML. This round adds a **second**
solver-build proof at greater rigor: `ShiftDeskEndToEndRegressionTest` builds and solves through
`SolverConfig.createFromXmlResource("solverConfig.xml")` directly (never a hand-built config),
re-run independently and green. `AgentDayConfig.expectedWorkSlots()` (plan 15-11) is a
byte-identical extraction of arithmetic `ScheduleConstraintProvider` used inline before — a
refactor, not a behavioural change, and confirmed score-neutral by the full suite (independently
re-run, 546/0/0/2 as above).

### SC6 — seeded, step-count-terminated A/B benchmark with a pre-committed threshold, reporting the soft-quality plateau honestly (XCUT-04)

**Status: ✓ VERIFIED**

`15-BENCHMARK.md` is untouched by the gap-closure round (git diff confirms no commit in
`55c9ae7..be3765c` touches this file or `ShiftModelBenchmarkTest.java`). Re-read directly: the
pass rule is committed before the Results section (verifiable in the file's own structure), the
verdict is **PASS** (shift arm reaches `0hard` on every seed, both CH orderings; shift median
unstaffed demand = 0.0 vs. slot median = 28.0), and the soft-quality plateau is reported as a
measured finding rather than discovered at UAT, matching the operator ruling recorded in
ROADMAP.md.

**Caveat inherited honestly, not newly discovered:** `15-BENCHMARK.md`'s own fixture
(`ShiftModeFixtures`, pre-15-09 shape) is exactly what G-15-10 proved structurally cannot express
an out-of-envelope timeslot — so this benchmark's PASS verdict measures schedule-quality
*comparison*, not the seat-supply defect the rest of this round closes. That is consistent with
the ROADMAP's own framing of SC6 (quality measurement) as distinct from SC2 (envelope soundness) —
the two are verified by different evidence in this report, as they were designed to be.

### SC7 — suggested shift library from demand + contracted hours, editable draft, reuses the coverage predicate, zero uncovered windows for a coverable desk (SHLB-07)

**Status: ✓ VERIFIED**

Unaffected by the gap-closure round (no gap-closure commit touches
`ShiftLibraryGenerationService.java`, `ShiftLibraryValidationService.java`'s `covers()` predicate
usage by the generator, or the frontend suggestion panel). `ShiftLibraryGenerationServiceTest`
(399 lines, 8 tests) confirmed present, unmodified by `git diff --stat` across the gap-closure
commit range, and part of the 546/0/0/2 full-suite result independently confirmed above.

---

## G-15-10 Closure Judgment

**G-15-10 is genuinely closed by the code now on disk**, on all four defects the debug session
identified, each independently re-derived from source in this session (not accepted from the
gap-closure SUMMARYs' own claims):

| Defect | Fix | Verified by direct code read | Verified by independent test re-run |
|---|---|---|---|
| D1 — zero-slack value range, no seat-supply precondition | `SolverService.requireShiftEnvelopeSeatSupply` (step 10e, after seats exist) refuses a shift-mode solve before it starts when library-covered supply < contracted demand for any date, or an agent-day's hours match no live pair | Yes — `SolverService.java:1147-1260` | `ShiftEnvelopeSupplyGateTest` 7/7, `ShiftEnvelopeSupplyInvariantTest` 6/6 |
| D2 — envelope-blind minimum-staffing seat supply | `expandMinimumStaffingSeats` now branches on `SchedulingMode`: suppresses filler seats at hours no live `ShiftBandPair` covers (operator ruling OR-1), guarantees `max(MIN_AGENTS_PER_TIMESLOT, workingAgentDaysOn(date))` seats at covered zero-demand hours | Yes — `SolverService.java:1380-1440` | `ShiftModeMinimumStaffingSeatSupplyTest` 6/6, `ZeroDemandTimeslotCeilingTest` 5/5 |
| D3 — cheapest-hard-violation arbitrage | A direct consequence of D1+D2; not a separate code change. `shiftEnvelopeComplianceWeight` deliberately left at `ofHard(1)` (raising it was empirically refuted — it only relocates the residual onto `contractedHoursUnder` at 100x cost) — recorded as a measured, tested decision, not an assumption | Yes — `ConstraintWeights.java:138` unchanged; decision recorded in `ShiftEnvelopeSupplyInvariantTest`'s weight-ladder case | `ShiftEnvelopeSupplyInvariantTest` (weight-ladder test, part of the 6/6 above) |
| D4 — report layer redraws the envelope, hides the breach | `ScheduleOutputService.buildAgentSchedule` reads the authoritative `ShiftDescriptor` (template span, band-derived break) instead of deriving both from seat gaps; a new `ShiftEnvelopeDivergence` on `AgentScheduleEntry` makes the breach a first-class, visible fact; frontend renders it | Yes — `ScheduleOutputService.java:190-242, 501-558`; `ScheduleDetailResponse.java:83`; `ScheduleResults.tsx:692-739, 920-927` | `ScheduleOutputServiceShiftReportingTest` 8/8 |

The shape-complete closing evidence (`ShiftDeskEndToEndRegressionTest`) is the strongest single
piece of evidence: it is not a unit test of one function but a real solve, through the shipped
`solverConfig.xml`, on a fixture deliberately built to the live desk's *shape* (not scale) —
staggered multi-template envelopes with hours genuinely outside every envelope, thin/zero edge
demand — and it asserts explicitly that a completed solve must never carry the exact signature the
live UAT desk exhibited (`hardByConstraint` must not contain `"Shift envelope compliance"`),
re-run independently in this session and green. It is automated proof of the mechanism, not a
substitute for re-running the literal live UAT case — that gap is named explicitly in Human
Verification Required below.

### Characterising tests — none left green against a defect

`.planning/debug/characterising-tests/*.java` (4 files) are all under `.planning/debug/`, **outside
`src/test`** — confirmed by `find`, so none of them compiles or runs as part of `./gradlew test`
(the independently-run full suite's 87 classes / 546 tests contain none of these four names);
they cannot silently pass against a live defect because they are not wired into the build at all.
`DISPOSITION.md` (created by plan 15-13) honestly accounts for all four: three fully promoted to
permanent regression tests, the fourth (`ShiftModeBreakGeometryCharacterisationTest`) partially
promoted (4 of 5 cases) with the 5th case explicitly **dropped, not ported** — because porting it
forward would assert the exact defect (a seat gap mislabelled as a break) that plan 15-10 fixed.
This is the honest disposition the task instructions asked me to check for, and it holds up: no
case was silently kept green against unmodified-defect behavior.

---

## Required Artifacts

| Artifact | Expected | Status | Details |
|---|---|---|---|
| `src/main/java/com/wfm/service/SolverService.java` | Envelope-aware `expandMinimumStaffingSeats`; `requireShiftEnvelopeSeatSupply` gate | ✓ VERIFIED | Both present, substantive, wired at steps 10d/10e of `startSolve` |
| `src/main/java/com/wfm/model/AgentDayConfig.java` | `expectedWorkSlots()` shared implementation | ✓ VERIFIED | Present; `ScheduleConstraintProvider`'s 5 call sites and the gate both call it |
| `src/main/java/com/wfm/model/ShiftBandPair.java` | One static coverage predicate, two callers | ✓ VERIFIED | `covers(Timeslot)` delegates to the static overload; report layer calls the static overload directly (`ScheduleOutputService.java:537,547`) |
| `src/main/java/com/wfm/service/ScheduleOutputService.java` | Authoritative envelope/breaks, divergence computation | ✓ VERIFIED | `computeDivergence` (:525-558), descriptor-first branch (:198-214) |
| `src/main/java/com/wfm/dto/ScheduleDetailResponse.java` | `ShiftEnvelopeDivergence` record on `AgentScheduleEntry` | ✓ VERIFIED | Present, nullable, trailing component |
| `frontend/src/pages/ScheduleResults.tsx` | Divergence + unstaffed-hour rendering | ✓ VERIFIED (compiles/wired) — visual correctness is the human-verification item above | `:692-739` (Agent Allocation per-cell marks), `:920-927` (Agent Schedule marker) |
| `frontend/src/api/client.ts` | `ShiftEnvelopeDivergence` type | ✓ VERIFIED | `:393-395`, optional field on `AgentScheduleEntry` (:408) |
| `src/test/java/com/wfm/solver/ShiftDeskEndToEndRegressionTest.java` | Shape-complete end-to-end regression | ✓ VERIFIED | 3/3 pass (re-run independently) |
| `.planning/debug/characterising-tests/DISPOSITION.md` | Honest disposition of all 4 characterising files | ✓ VERIFIED | Present, accurate against direct source re-check |
| `.planning/phases/15-shift-envelope-breaks-library-generation/deferred-items.md` | Two latent defects filed with mechanism + fix location | ✓ VERIFIED | `## 15-13` section present; both claims (breakBlockedHours unchecked, `TimeslotBoundsResponse.endTime()` unread) independently re-confirmed true against current source |

## Key Link Verification

| From | To | Via | Status |
|---|---|---|---|
| `SolverService.startSolve` step 10d | `expandMinimumStaffingSeats` | `desk.getSchedulingMode(), shiftBandPairs, workingAgentDaysByDate` passed in | ✓ WIRED |
| `SolverService.startSolve` step 10e | `requireShiftEnvelopeSeatSupply` | Called after seats exist (step 10d), before schedule population — confirmed at `SolverService.java:355-360` | ✓ WIRED |
| `ScheduleOutputService.buildAgentSchedule` | `shiftDescriptorsByAgentDate` | Descriptor resolved before span/break computation (:190-214), not discarded | ✓ WIRED |
| `ScheduleOutputService.computeDivergence` | `ShiftBandPair.covers(LocalTime...)` static overload | Called directly (:537, :547), not re-derived | ✓ WIRED |
| `AgentScheduleEntry.divergence` | `ScheduleResults.tsx` `entry.divergence` | `client.ts` type + `.divergence?.` reads in both tabs | ✓ WIRED |
| `ScheduleExportService` | `AgentScheduleEntry.breaks()` | Confirmed reads breaks() with no export-side re-derivation (per 15-10 Task 3) | ✓ WIRED |

## Requirements Coverage

| Requirement | Source Plan(s) | Status | Evidence |
|---|---|---|---|
| ENVL-01 | 15-03, 15-09, 15-11 | ✓ SATISFIED | `getEligibleShiftBandPairs` per-row filter + seat-supply gate refusal; `ShiftEnvelopeSupplyGateTest`, `SolverServiceShiftAssignmentTest` |
| ENVL-02 | 15-03, 15-04, 15-09, 15-11, 15-13 | ✓ SATISFIED | `shiftEnvelopeCompliance` unchanged, hard; supply gate closes the seat-shortage trigger; `ShiftDeskEndToEndRegressionTest` proves it on the live defect's shape |
| ENVL-03 | 15-03, 15-04 | ✓ SATISFIED | Unaffected by gap closure; `ShiftEnvelopeGroundTruthTest` |
| ENVL-04 | 15-06, 15-09, 15-13 | ✓ SATISFIED | `ShiftModeBreakGatingTest`, `ShiftDeskEndToEndRegressionTest`'s containment walker |
| ENVL-05 | 15-06, 15-10 | ✓ SATISFIED | `ShiftModeBreakGatingTest`; report layer now reads band-derived breaks, `ScheduleOutputServiceShiftReportingTest` |
| ENVL-06 | 15-03, 15-04, 15-11 | ✓ SATISFIED | `SolverConfigBuildTest`; `ShiftDeskEndToEndRegressionTest` builds+solves through the real config |
| ENVL-07 | 15-04, 15-13 | ✓ SATISFIED | `ShiftEnvelopeGroundTruthTest`; second independent walker in `ShiftDeskEndToEndRegressionTest` |
| ENVL-08 | 15-01, 15-05, 15-06 | ✓ SATISFIED | Unaffected by gap closure |
| ENVL-09 | 15-06 | ✓ SATISFIED | Unaffected by gap closure |
| ENVL-10 | 15-07, 15-10, 15-12 | ✓ SATISFIED | Group header unaffected; Agent Schedule table now agrees with it (15-10/15-12), visual confirmation pending (human item) |
| SHLB-07 | 15-01, 15-02, 15-05 | ✓ SATISFIED | Unaffected by gap closure |
| XCUT-01 | 15-05, 15-07, 15-10, 15-12 | ✓ SATISFIED | Divergence now visible in both Agent Schedule and Agent Allocation, not just one; visual confirmation pending (human item) |
| XCUT-03 | 15-03, 15-04, 15-08, 15-09, 15-11, 15-13 | ✓ SATISFIED | Multiple solver-build tests; `ShiftDeskEndToEndRegressionTest` is the strongest |
| XCUT-04 | 15-08 | ✓ SATISFIED | `15-BENCHMARK.md`, unaffected by gap closure, PASS verdict re-read directly |
| XCUT-05 | 15-06, 15-09 | ✓ SATISFIED | Stale "Minimum staffing" row corrected to `MODE_GATED` |

No orphaned requirements. Every requirement ID declared across all 13 plans'
`requirements:` frontmatter (ENVL-01…10, SHLB-07, XCUT-01, XCUT-03, XCUT-04, XCUT-05) is accounted
for above. `.planning/REQUIREMENTS.md`'s per-requirement checkboxes for all of these are already
`[x]`; its Traceability *table* still reads "ENVL-01…10 | Phase 15 | Pending" — a stale summary row
inconsistent with the checkboxes directly above it in the same file. This is a documentation
bookkeeping gap, not a phase-goal gap (informational, not included in the score).

## Anti-Patterns Found

`TBD`/`FIXME`/`XXX`/`TODO`/`HACK`/`PLACEHOLDER` scan across every file touched by the gap-closure
commit range (`55c9ae7..be3765c`): zero markers found in `src/main` or `src/test`.

One real, provable defect was found by re-verifying `15-REVIEW.md`'s carried findings directly
against current source (not accepted from the review's own severity label):

### CR-04 (confirmed present; judged WARNING for phase-goal purposes, not BLOCKER)

`ScheduleOutputService.buildAgentSchedule` (`:235-238`) appends a divergence-summary string to
`schedule.getWarnings()` on **every call**, with no dedup guard and no idempotency check:

```java
if (outOfEnvelopeSeatCount > 0 && schedule.getWarnings() != null) {
    schedule.getWarnings().add(outOfEnvelopeSeatCount + " agent-day seat(s) fall outside...");
}
```

Confirmed directly: `getScheduleDetail` calls `buildAgentSchedule` on every `GET
/desks/{deskId}/schedules/{id}` request; `InMemoryScheduleStore.get()` returns the live object
reference (not a copy) for a pre-accept schedule; the frontend polls this endpoint every 2 seconds
while `status === 'RUNNING'`. For a SHIFT-mode schedule with any envelope divergence, this
duplicates the warning string on every poll for the life of the solve, and again on every
subsequent view/export before accept/reject.

**Why this is judged a WARNING, not a BLOCKER, against this phase's own success criteria:** none
of the seven ROADMAP success criteria assert bounded or deduplicated warning-list growth — the
substantive claim each criterion makes (envelope compliance is hard-enforced; the divergence is
*visible*, not silently absorbed) is not falsified by this bug. The divergence information itself
is correctly computed and correctly rendered on the first call; the defect is that repeat calls
duplicate it rather than replace or skip it. This is a real, reachable, user-visible reliability
defect that should be fixed promptly (per 15-REVIEW.md's own recommended fix — return the
summary from the builder rather than mutating the input, or dedupe by exact string) — flagged here
so it is not lost, but it does not block this phase's completion since it does not undermine any
of the seven success criteria this report verified. This is my own judgment, not an inheritance of
the code reviewer's "critical" label, per this task's explicit instruction not to simply defer to
review severity.

### WR-01 (confirmed present, unchanged; carried forward)

`ShiftTemplateBreakBandRepository.deleteByShiftTemplate_Id` is the one unscoped write in an
otherwise tenant-scoped repository — confirmed by direct read (`:28`, no `tenantId` parameter). Its
one caller resolves tenant scope first, so it is safe in practice today but has no defense-in-depth
against a future caller that skips that resolution. Not a phase-goal blocker; a hygiene item.

### WR-02 (confirmed present, unchanged; carried forward)

The "unstaffed by design" column header treatment in `ScheduleResults.tsx` (`:616-623`) derives its
envelope span from that day's *actually-assigned* shift groups, not the desk's full live shift
template list (the component has no such list in scope). On a day where the library reaches an
hour but no agent happened to be assigned a template covering it, the header's literal tooltip
claim ("No shift in this desk's library covers this hour") can be inaccurate. Confirmed by direct
read. Not a phase-goal blocker — it is a display-accuracy nuance in an entirely new UI surface this
round added, not a regression of anything the phase's success criteria assert.

## Behavioral Spot-Checks (all re-run independently in this verification session)

| Behavior | Command | Result | Status |
|---|---|---|---|
| Shape-complete end-to-end regression (ENVL-02/04/06/07/XCUT-03) | `./gradlew test --tests 'com.wfm.solver.ShiftDeskEndToEndRegressionTest'` | `tests="3" failures="0" errors="0"` | ✓ PASS |
| Seat-supply invariant, zero-slack, weight-ladder (ENVL-02/06) | `./gradlew test --tests 'com.wfm.solver.ShiftEnvelopeSupplyInvariantTest'` | `tests="6" failures="0" errors="0"` | ✓ PASS |
| Break-geometry guard, superseding characterisation (ENVL-04/05/07) | `./gradlew test --tests 'com.wfm.solver.ShiftModeBreakGeometryGuardTest'` | `tests="4" failures="0" errors="0"` | ✓ PASS |
| Seat-supply gate refusal/advisory behaviors (ENVL-01/02) | `./gradlew test --tests 'com.wfm.service.ShiftEnvelopeSupplyGateTest'` | `tests="7" failures="0" errors="0"` | ✓ PASS |
| Report-layer divergence/span/break correction (ENVL-05/07/10, XCUT-01) | `./gradlew test --tests 'com.wfm.service.ScheduleOutputServiceShiftReportingTest'` | `tests="8" failures="0" errors="0"` | ✓ PASS |
| Envelope-aware minimum-staffing seat expansion (ENVL-02/04) | `./gradlew test --tests 'com.wfm.service.ShiftModeMinimumStaffingSeatSupplyTest'` | `tests="6" failures="0" errors="0"` | ✓ PASS |
| Zero-demand timeslot ceiling behavior (D2 companion) | `./gradlew test --tests 'com.wfm.solver.ZeroDemandTimeslotCeilingTest'` | `tests="5" failures="0" errors="0"` | ✓ PASS |
| Frontend build | `cd frontend && npm run build` | `tsc -b && vite build`, 57 modules, no errors | ✓ PASS |
| Full backend suite, no filter | `./gradlew test` | `BUILD SUCCESSFUL in 12m 15s`; aggregated from all 87 `TEST-*.xml`: **546 tests, 0 failures, 0 errors, 2 skipped** — exact independent match to the orchestrator's reported figure for the same commit | ✓ PASS |

## Probe Execution

No `scripts/*/tests/probe-*.sh` files exist in this repository and no PLAN/SUMMARY for this phase
references a probe-based verification convention. **SKIPPED — no runnable probe entry points.**

## Human Verification Required

See the `human_verification` block in this file's frontmatter for the full test/expected/why-human
detail. Summary:

### 1. Visual confirmation of the new divergence/unstaffed-hour rendering

Load a shift-mode schedule carrying a real (or forced) envelope divergence and confirm the Agent
Schedule table's inline marker and the Agent Allocation grid's per-cell `E!`/`×` marks plus the
"unstaffed by design" column header land on the right cells, with legible tooltips and a correct
legend. No frontend test framework exists in this codebase; 15-12's own SUMMARY marks all three of
its rendering must-haves `human_judgment: true` for the same reason.

### 2. Re-run live UAT Test 10 against the dev deployment

The automated shape-complete evidence (`ShiftDeskEndToEndRegressionTest`) is strong mechanism-level
proof, but it is a hand-built fixture reproducing the live defect's *shape*, not the literal live
StubHub (EN) desk and its real data at real scale. 15-13's own completion notes name this exact
re-run as the intended closing step for `/gsd-verify-work`.

## Gaps Summary

No gaps. Every one of the phase's seven ROADMAP success criteria is verified true against the
codebase at HEAD (`d490171`), independently re-derived from source and, for the gap-closure round's
own test evidence, independently re-run in this session (including a full, unfiltered
`./gradlew test` that reproduced the orchestrator's 546/0/0/2 claim exactly) rather than accepted
from any SUMMARY. G-15-10 (the blocker this phase's completion previously turned on) is judged
genuinely closed on all four AND-gated root causes (D1-D4), with the strongest evidence being a
real solve through the shipped solver configuration on a fixture built to the live defect's shape,
asserting explicitly that the defect's exact signature (a completed solve carrying residual
`Shift envelope compliance` hard penalty) never recurs. The four characterising tests that proved
the defect existed are honestly disposed of — three fully promoted to permanent regression suites,
the fourth's stale case explicitly dropped rather than silently kept green against fixed behavior —
and none is a live guard that could be mistaken for ongoing protection. Two latent, independent
defects (blocked-break-hours unenforced in SHIFT mode; envelope-vs-operating-window containment
unchecked at save time) are honestly filed as deferred, not silently dropped, consistent with
operator ruling OR-2, and independently re-confirmed still true against current source rather than
merely carried forward from the debug report. One real code-quality defect (CR-04, unbounded
duplicate warning-list growth) was independently re-confirmed present and is flagged prominently as
a WARNING that should be fixed promptly, judged not to rise to a phase-goal blocker because none of
the seven success criteria depend on bounded warning-list behavior.

**Status is `human_needed`, not `passed`, solely because of the two human-verification items
above** — new UI surface with no automated visual proof, and a live-desk UAT re-run this round's
own closing evidence recommends but does not itself constitute. Nothing in the codebase evidence
gathered here contradicts the phase goal being achieved; the two items are the last mile between
strong automated/mechanism-level proof and full operator confidence.

---

_Verified: 2026-08-27T18:45:00Z_
_Verifier: Claude (gsd-verifier)_
