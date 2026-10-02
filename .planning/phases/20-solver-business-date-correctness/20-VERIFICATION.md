---
phase: 20-solver-business-date-correctness
verified: 2026-10-02T14:10:00Z
status: passed
score: 8/8 must-haves verified
covered_files: [".planning/phases/20-solver-business-date-correctness/20-01-PLAN.md", ".planning/phases/20-solver-business-date-correctness/20-01-SUMMARY.md", ".planning/phases/20-solver-business-date-correctness/20-02-PLAN.md", ".planning/phases/20-solver-business-date-correctness/20-02-SUMMARY.md", ".planning/phases/20-solver-business-date-correctness/20-03-PLAN.md", ".planning/phases/20-solver-business-date-correctness/20-03-SUMMARY.md", ".planning/phases/20-solver-business-date-correctness/20-04-PLAN.md", ".planning/phases/20-solver-business-date-correctness/20-04-SUMMARY.md", ".planning/phases/20-solver-business-date-correctness/20-05-PLAN.md", ".planning/phases/20-solver-business-date-correctness/20-05-SUMMARY.md", ".planning/phases/20-solver-business-date-correctness/20-06-PLAN.md", ".planning/phases/20-solver-business-date-correctness/20-06-SUMMARY.md", ".planning/phases/20-solver-business-date-correctness/20-07-PLAN.md", ".planning/phases/20-solver-business-date-correctness/20-07-SUMMARY.md", ".planning/phases/20-solver-business-date-correctness/20-08-PLAN.md", ".planning/phases/20-solver-business-date-correctness/20-08-SUMMARY.md", ".planning/phases/20-solver-business-date-correctness/20-09-PLAN.md", ".planning/phases/20-solver-business-date-correctness/20-09-SUMMARY.md", ".planning/phases/20-solver-business-date-correctness/20-10-PLAN.md", ".planning/phases/20-solver-business-date-correctness/20-10-SUMMARY.md", ".planning/phases/20-solver-business-date-correctness/20-11-PLAN.md", ".planning/phases/20-solver-business-date-correctness/20-11-SUMMARY.md", ".planning/phases/20-solver-business-date-correctness/20-12-PLAN.md", ".planning/phases/20-solver-business-date-correctness/20-12-SUMMARY.md", ".planning/phases/20-solver-business-date-correctness/20-REVIEW.md", "frontend/src/pages/DeskManagement.tsx", "src/main/java/com/wfm/controller/TimeslotController.java", "src/main/java/com/wfm/model/AgentDayConfig.java", "src/main/java/com/wfm/model/AgentShiftAssignment.java", "src/main/java/com/wfm/repository/StaffingRequirementRepository.java", "src/main/java/com/wfm/service/BusinessDayPeriodLoader.java", "src/main/java/com/wfm/service/DeskService.java", "src/main/java/com/wfm/service/ScheduleOutputService.java", "src/main/java/com/wfm/service/ScheduleService.java", "src/main/java/com/wfm/service/ShiftLibraryGenerationService.java", "src/main/java/com/wfm/service/SolverService.java", "src/main/java/com/wfm/service/StaffingRequirementService.java", "src/main/java/com/wfm/solver/ScheduleConstraintProvider.java", "src/test/resources/bday-join-guard.md"]
covered_digest: "v2:sha256:34097e0bc238c0d7a11f4f43dd293e3662a4424345975eeb806b9d6cbb351e2b"
behavior_unverified: 0
overrides_applied: 0
re_verification:
  previous_status: gaps_found
  previous_score: 6/8
  gaps_closed:
    - "SC6: TimeslotController.generateTimeslots now reads desk.getDayStart() (tenant-scoped) instead of a hardcoded LocalTime.MIDNIGHT; a new test, TimeslotControllerDeskAnchorTest, drives the real controller method and proves the tiling refusal fires through it, plus a cross-tenant 404 and a positive control. DeskService.setDayStart now also refuses nonzero seconds/nanoseconds, closing the sub-minute precision hole."
    - "Phase goal / CR-02: SolverService.expandMinimumStaffingSeats's two ts.getDate() reads (shift-template weekday eligibility and the workingAgentDaysByDate count lookup) are now ts.getBusinessDate(), proven on a 21:00-anchored desk by MinimumStaffingSeatsBusinessDateTest with a named non-zero expected seat count and a midnight-anchored control."
  gaps_remaining: []
  regressions: []
advisory:
  - finding: "A fresh code review (20-REVIEW.md, committed after gap-closure plan 20-12) found a residual test-coverage gap, not a production defect: no test drives the real ScheduleService.acceptSchedule path at a 21:00 anchor with a decoy row that must be EXCLUDED (calendar date inside the widened fetch, derived business date outside the accepted period). ScheduleServiceShiftSnapshotTest's 21:00-anchored tests (A, B, C) all prove INCLUSION (no truncation) and that derivation — not the stored businessDate column — governs; only the midnight-anchored control (Test D) exercises exclusion of a decoy row, and at midnight business date and calendar date coincide so it cannot discriminate the two code paths. The underlying exclusion logic itself IS proven correct at the unit level (BusinessDayPeriodLoaderTest's Test 3, against a mocked repository, for exactly this scenario) — confirmed by direct read of both the production code and the test file."
    category: other
    reason: "Flagged by this phase's own code-review process as a Warning (not Critical); verified independently by this verifier via direct source read of BusinessDayPeriodLoader.java, ScheduleService.java and ScheduleServiceShiftSnapshotTest.java. Does not negate any of the 7 ROADMAP success criteria as literally written (none names ScheduleService.acceptSchedule's exclusion behavior specifically), and the production code is correct — this is a recommendation to add a fifth real-DB-round-trip test, not evidence of a defect."
    evidence_status: "confirmed by direct read: grep of ScheduleServiceShiftSnapshotTest.java shows no 21:00-anchored test with an excluded decoy row; BusinessDayPeriodLoaderTest Test 3 confirms the loader's own filter logic is correct at the unit level"
---

# Phase 20: Solver Business-Date Correctness Verification Report

**Phase Goal:** Every solver join, the pre-solve seat-supply check, SLOT-mode accounting, and demand
upload/coverage reporting all resolve the same business date for the same timeslot — proven by
per-constraint match counts, not just score — and one small live desk shows the re-anchoring changed
nothing it shouldn't have.

**Verified:** 2026-10-02T14:10:00Z
**Status:** passed
**Re-verification:** Yes — after two gap-closure rounds (plans 20-09/20-10/20-11, then 20-12)

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | SC1: All business-date-relevant joins in `ScheduleConstraintProvider` key on business date, backed by a structural guard, both directions (SOLV-01, SOLV-02) | ✓ VERIFIED | Unchanged since prior round. Shared `DATE` lambda resolves `getBusinessDate()`; all `groupBy`/`equal`/`join` consumers migrated. `BusinessDateJoinGuardTest` passes 6/6 (re-run: `tests="6" failures="0" errors="0"`), allowlist (`bday-join-guard.md`) is empty with two independent liveness proofs |
| 2 | SC2: Break-band, contiguity and envelope-compliance constraints hold across midnight; the pre-solve seat-supply check reports a shortfall against the business day it actually affects (SOLV-03, SOLV-05) | ✓ VERIFIED | `requireShiftEnvelopeSeatSupply` (`SolverService.java:1477-1571`) keys both `rowsByDate` and `timeslotsByDate` on `getBusinessDate()`. Confirmed by direct read that the shortfall error message **literally embeds the business-date variable** (`"On " + date + ", rostered agent-days need..."`, line 1561). `ShiftEnvelopeSupplyGateTest.ninePmAnchor_genuineShortfallIsStillRefused` asserts `d.message()).contains(NINE_PM_BUSINESS_DAY.toString())` — a real executable assertion naming the correct business day, not prose. Re-run green |
| 3 | SC3: An overnight SLOT-mode stretch counts against a single business day's contracted hours (SOLV-04) | ✓ VERIFIED | `SlotModeOvernightContractedHoursTest` passes 2/2 (re-run), 21:00 anchor, 8 assignments spanning 2 business days |
| 4 | SC4: Every migrated join carries a non-vacuity assertion and a per-constraint match-count assertion (SOLV-06) | ✓ VERIFIED | `ConstraintMatchCountNonVacuityTest` passes 5/5 (re-run), reflective 26-constraint completeness. See Advisory for a separate, non-blocking test-coverage recommendation on `BusinessDayPeriodLoader` (not a constraint join, outside this criterion's literal scope) |
| 5 | SC5: Demand upload, coverage reporting and the solver resolve the same business date for the same timeslot, guarded by a test; a Phil-US-shaped constructed fixture shows unchanged per-constraint match counts and score across the re-anchoring (SOLV-07, BDAY-07) | ✓ VERIFIED | `PhilUsShapedDriftGuardTest` re-run: 2/2 pass. Directly read the assertions: `composition_fixtureMatchesItsOwnDeclaredShape` asserts 48 agents and `EXPECTED_TOTAL_FTE` (44.6) by exact `BigDecimal` comparison; `driftGuard_everyConstraintMatchCountAndTheScoreMatchTheLiteralBaseline` iterates a literal `EXPECTED_MATCH_COUNTS` map per constraint name and asserts the mismatch map is empty, then asserts `actualScore` equals a literal `EXPECTED_SCORE` — real executable per-constraint and per-score assertions, not SUMMARY prose |
| 6 | SC6: `DeskService`'s `00:00`-only gate is gone, any 15-minute boundary is accepted, refusing anything else by name at save time; Phase 18's generation-time tiling refusal is reachable through the API and proven to fire (SOLV-01) | ✓ VERIFIED (gap closed) | **Both sub-gaps from the prior round closed by plan 20-09.** `TimeslotController.generateTimeslots` (`TimeslotController.java:44-61`) now resolves `Desk desk = deskService.getDesk(deskId)` (tenant-scoped) and passes `desk.getDayStart()` to the generator — confirmed by direct read, stale comment and unused `LocalTime` import removed. `TimeslotControllerDeskAnchorTest` (new, 4/4 pass on re-run) drives `timeslotController.generateTimeslots(...)` **directly**, not the generator behind it: a 21:00-anchored desk's one-business-day window returns 24 rows across 2 calendar dates; a 21:15-anchored desk is refused naming `"21:15"`, `"30"` and `"tile"`; a 21:30-anchored desk succeeds (positive control); a cross-tenant desk id throws `EntityNotFoundException` before any row persists. `DeskService.setDayStart` (`:250-252`) now refuses `dayStart.getSecond() != 0 \|\| dayStart.getNano() != 0` **before** the 15-minute modulus check; `DeskServiceDayStartTest` (20/20 pass on re-run) proves `06:00:00` accepted, `06:00:01` and a nanosecond-bearing value refused, `06:07:01` refused naming the sub-minute reason (not the boundary reason), and the pre-existing adjacency pair (`21:07` refused / `21:15` accepted) is intact |
| 7 | SC7: `PENDING_DESK_ANCHOR` is gone, all call sites read the real anchor, its allowlist entry is removed, the guard goes red if either returns (SOLV-03) | ✓ VERIFIED | Unchanged. `grep -rn PENDING_DESK_ANCHOR src/main/` returns nothing; `midnight-time-arithmetic.md` carries no such allowlist entry; `MidnightTimeArithmeticGuardTest` passes 12/12 (re-run) |
| 8 | Phase goal: every solver join resolves business date, not calendar date, for the same timeslot | ✓ VERIFIED (gap closed, and widened) | **CR-02 closed by plan 20-10**: `SolverService.expandMinimumStaffingSeats` (`:1993`, `:2004`) now reads `ts.getBusinessDate()` at both date-sensitive sites — confirmed by direct read. `MinimumStaffingSeatsBusinessDateTest` (5/5 pass on re-run) proves this on a 21:00-anchored desk with a **named non-zero expected seat count** (so a silent zero-default cannot pass as correct) and a midnight-anchored control. **Beyond the original gap**, plan 20-11's audit (prompted by the prior verification's advisory 1 — "is CR-02 the only remaining instance?") found and fixed two more `SolverService` sites (`runPreSolveValidation`'s period-coverage and end-time checks, `:1109`, `:1136`), proven by `PreSolveValidationBusinessDateTest` (5/5 pass). Plan 20-12 then found and fixed four repository-level truncation sites (`SolverService.startSolve`'s two problem-fact loads, `ScheduleService.acceptSchedule`'s two snapshot loads) via the new shared `BusinessDayPeriodLoader`, proven end-to-end against a real H2 database at a 21:00 anchor (`ScheduleServiceShiftSnapshotTest`, 21/21 pass) plus the loader's own unit tests (`BusinessDayPeriodLoaderTest`, 8/8 pass). `bday-join-guard.md` now records all seven of these non-join-verb-scanned sites by name, with the reasoning for why the structural guard cannot see any of them — turning "is this the only instance?" from an assumption into a documented, re-checkable answer |

**Score:** 8/8 truths verified (0 present, behavior-unverified)

### Advisory (New Scope, Unevidenced)

A fresh code review ran after gap-closure plan 20-12 completed and found one new, non-blocking Warning
(0 Critical). Reported here for transparency; does not affect the status above.

| # | Finding | Category | Why Advisory |
|---|---------|----------|---------------|
| 1 | No test drives the real `ScheduleService.acceptSchedule` path at a 21:00 anchor with a decoy row that must be EXCLUDED by the derived-business-date filter (calendar date inside the widened fetch, derived business date outside the accepted period). The three 21:00-anchored tests in `ScheduleServiceShiftSnapshotTest` all prove inclusion/derivation-governs-over-stored-column; only the midnight control proves exclusion, and at midnight the two candidate code paths (filter on calendar vs. filter on derived business date) are indistinguishable | other | The exclusion logic itself is proven correct at the unit level (`BusinessDayPeriodLoaderTest` Test 3, mocked repository, the identical scenario); production code confirmed correct by direct read; the project's own review classified this as Warning, not Critical; recommended fix is a 5th test, not a code change |

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `src/main/java/com/wfm/controller/TimeslotController.java` | `/generate` reads `desk.getDayStart()` | ✓ VERIFIED | Fixed; stale comment and unused import removed |
| `src/test/java/com/wfm/controller/TimeslotControllerDeskAnchorTest.java` | Proof driving the real controller method | ✓ VERIFIED | New, 4/4 pass, drives `timeslotController.generateTimeslots(...)` directly |
| `src/main/java/com/wfm/service/DeskService.java` | Sub-minute precision refusal | ✓ VERIFIED | `getSecond()`/`getNano()` check added before the 15-minute modulus |
| `src/test/java/com/wfm/service/DeskServiceDayStartTest.java` | Boundary/precision proofs | ✓ VERIFIED | 20/20 pass, includes 06:00:00/06:00:01/nanosecond/06:07:01 cases |
| `src/main/java/com/wfm/service/SolverService.java` (`expandMinimumStaffingSeats`) | Reads business date | ✓ VERIFIED | Both sites fixed (`:1993`, `:2004`) |
| `src/test/java/com/wfm/service/MinimumStaffingSeatsBusinessDateTest.java` | Non-midnight proof, named non-zero count | ✓ VERIFIED | 5/5 pass |
| `src/main/java/com/wfm/service/SolverService.java` (`runPreSolveValidation`) | Reads business date for period-coverage and end-time checks | ✓ VERIFIED | `:1109`, `:1136` fixed (plan 20-11, beyond the original gap) |
| `src/test/java/com/wfm/service/PreSolveValidationBusinessDateTest.java` | 21:00-anchor proof with midnight control | ✓ VERIFIED | 5/5 pass |
| `src/main/java/com/wfm/service/BusinessDayPeriodLoader.java` | Shared widen-then-derive loader, 4 call sites | ✓ VERIFIED | New, substantive (79 lines), wired into both `SolverService.startSolve` and `ScheduleService.acceptSchedule` (2 call sites each, confirmed by grep) |
| `src/test/java/com/wfm/service/BusinessDayPeriodLoaderTest.java` | Loader's own edge coverage | ✓ VERIFIED | 8/8 pass |
| `src/test/java/com/wfm/service/ScheduleServiceShiftSnapshotTest.java` | Real-DB accept-path proof at 21:00 with midnight control | ✓ VERIFIED (with advisory) | 21/21 pass; proves inclusion and derivation-governs-column at 21:00, exclusion only at midnight — see Advisory |
| `src/test/resources/bday-join-guard.md` | Documents every site the structural guard cannot see | ✓ VERIFIED | Extended twice more (plans 20-11, 20-12); now names 3 distinct blind-spot shapes and 7+ specific sites with reasoning |

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| `TimeslotController.generateTimeslots` | `desk.getDayStart()` | `DeskService.getDesk(deskId)` (tenant-scoped) | ✓ WIRED | Was `✗ NOT_WIRED` (hardcoded `LocalTime.MIDNIGHT`) in the prior round; now fixed, confirmed by direct read |
| `SolverService.expandMinimumStaffingSeats` | `Timeslot.getBusinessDate()` | direct read, `workingAgentDaysByDate.getOrDefault(ts.getBusinessDate(), 0)` | ✓ WIRED | Was `✗ NOT_WIRED` (`ts.getDate()`) in the prior round; now fixed |
| `SolverService.startSolve` / `ScheduleService.acceptSchedule` | `TimeslotRepository`/`StaffingRequirementRepository` | `BusinessDayPeriodLoader.loadLiveTimeslots`/`loadLiveStaffingRequirements` | ✓ WIRED | New in this round (plan 20-12); confirmed by grep at all 4 call sites |
| `DeskService.setDayStart` | `TimeslotGeneratorService.requireDayStartTiles` | `TimeslotController.generateTimeslots(..., desk.getDayStart(), ...)` | ✓ WIRED | Was `✗ NOT_WIRED` in the prior round; now fixed |

### Behavioral Spot-Checks / Targeted Re-Runs

Re-ran every test class touched by the gap-closure rounds directly (not the full 13-minute suite, per the
orchestrator's guidance); the orchestrator's own full unfiltered run (`./gradlew cleanTest test`, 1217
tests, 0 failures, 0 errors, 4 skipped) is relied upon for the suite-wide baseline.

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| `TimeslotControllerDeskAnchorTest` (SC6 gap 1) | `--tests com.wfm.controller.TimeslotControllerDeskAnchorTest` | 4 tests, 0 failures | ✓ PASS |
| `DeskServiceDayStartTest` (SC6 gap 1 precision) | `--tests com.wfm.service.DeskServiceDayStartTest` | 20 tests, 0 failures | ✓ PASS |
| `MinimumStaffingSeatsBusinessDateTest` (phase-goal gap, CR-02) | `--tests com.wfm.service.MinimumStaffingSeatsBusinessDateTest` | 5 tests, 0 failures | ✓ PASS |
| `PreSolveValidationBusinessDateTest` (advisory-1 audit) | `--tests com.wfm.service.PreSolveValidationBusinessDateTest` | 5 tests, 0 failures | ✓ PASS |
| `BusinessDayPeriodLoaderTest` (CR-01/WR-01) | `--tests com.wfm.service.BusinessDayPeriodLoaderTest` | 8 tests, 0 failures | ✓ PASS |
| `ScheduleServiceShiftSnapshotTest` (CR-01 sites 3-4) | `--tests com.wfm.service.ScheduleServiceShiftSnapshotTest` | 21 tests, 0 failures | ✓ PASS |
| `PhilUsShapedDriftGuardTest` (SC5/BDAY-07) | `--tests com.wfm.solver.PhilUsShapedDriftGuardTest` | 2 tests, 0 failures | ✓ PASS |
| `BusinessDateJoinGuardTest` (SC1, regression) | `--tests com.wfm.service.BusinessDateJoinGuardTest` | 6 tests, 0 failures | ✓ PASS |
| `MidnightTimeArithmeticGuardTest` (SC7, regression) | `--tests com.wfm.service.MidnightTimeArithmeticGuardTest` | 12 tests, 0 failures | ✓ PASS |
| `AgentDayDerivationGuardTest` (D-06, regression) | `--tests com.wfm.service.AgentDayDerivationGuardTest` | 11 tests, 0 failures | ✓ PASS |
| `ConstraintMatchCountNonVacuityTest` (SC4, regression) | `--tests com.wfm.solver.ConstraintMatchCountNonVacuityTest` | 5 tests, 0 failures | ✓ PASS |
| `SlotModeOvernightContractedHoursTest` (SC3, regression) | `--tests com.wfm.solver.SlotModeOvernightContractedHoursTest` | 2 tests, 0 failures | ✓ PASS |
| `MidnightBoundaryRegressionTest` (SC2/SC7, regression + stale-javadoc fix) | `--tests com.wfm.solver.MidnightBoundaryRegressionTest` | 10 tests (5 nested classes), 0 failures | ✓ PASS |
| `StaffingRequirementBusinessDateDeleteTest` (SC5, regression) | `--tests com.wfm.service.StaffingRequirementBusinessDateDeleteTest` | 3 tests, 0 failures | ✓ PASS |
| `ShiftEnvelopeSupplyGateTest` (SC2, business-day-named shortfall) | direct source + test read | `d.message()).contains(NINE_PM_BUSINESS_DAY.toString())` present and asserted | ✓ PASS (confirmed by reading, included in full-suite run) |

All re-runs used `./gradlew test --tests <Class>` per-invocation (each a fresh, isolated filtered run); no
suite-level aggregate was read after a filtered run.

### Probe Execution

Not applicable — no `scripts/*/tests/probe-*.sh` conventions declared or present for this phase. SKIPPED.

### Requirements Coverage

| Requirement | Source Plan(s) | Description | Status | Evidence |
|-------------|-----------------|--------------|--------|----------|
| SOLV-01 | 20-01, 20-05, 20-08, 20-09, 20-10, 20-11, 20-12 | Every constraint grouping an agent's day joins on business date | ✓ SATISFIED | `ScheduleConstraintProvider` migration + all gap-closure fixes (controller anchor, `expandMinimumStaffingSeats`, pre-solve checks, `BusinessDayPeriodLoader`) |
| SOLV-02 | 20-02, 20-11, 20-12 | A test fails if any constraint joins on calendar date where business date is meant | ✓ SATISFIED | `BusinessDateJoinGuardTest` green; `bday-join-guard.md` now documents 3 blind-spot shapes the guard structurally cannot see, closing the "is this guard's silence trustworthy?" question |
| SOLV-03 | 20-01, 20-05 | Break bands, contiguity, envelope compliance hold across midnight | ✓ SATISFIED | `MidnightBoundaryRegressionTest.NonMidnightAnchor`; `PENDING_DESK_ANCHOR` removal |
| SOLV-04 | 20-04, 20-05 | SLOT mode counts overnight stretch against one business day | ✓ SATISFIED | `SlotModeOvernightContractedHoursTest` |
| SOLV-05 | 20-04, 20-10 | Pre-solve seat-supply check reports shortfalls per business day | ✓ SATISFIED | `requireShiftEnvelopeSeatSupply` re-keyed; shortfall message names the business date, asserted directly; `expandMinimumStaffingSeats` (the sibling defect, CR-02) now fixed too |
| SOLV-06 | 20-02, 20-03, 20-05 | Each migrated join proven non-vacuous via match counts | ✓ SATISFIED | `ConstraintMatchCountNonVacuityTest`; see Advisory for a narrower, non-blocking recommendation outside this requirement's literal (constraint-join) scope |
| SOLV-07 | 20-02, 20-06, 20-07 | Demand upload, coverage reporting and solver resolve same business date, guarded by test | ✓ SATISFIED | `BusinessDateJoinGuardTest` + `StaffingRequirementBusinessDateDeleteTest` |
| BDAY-07 | 20-03 | Constructed Phil-US-shaped fixture shows unchanged schedule across re-anchoring | ✓ SATISFIED | `PhilUsShapedDriftGuardTest`: 48 agents, 44.6 FTE, literal per-constraint match-count map, exact score — real assertions confirmed by direct read |

No orphaned requirements: the union of `requirements:` across all 12 plans (`SOLV-01..07`, `BDAY-07`)
exactly matches REQUIREMENTS.md's Phase 20 mapping, and REQUIREMENTS.md's own checklist (lines 114,
129-135) is marked `[x]` consistent with this verification's findings.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `src/test/java/com/wfm/service/ScheduleServiceShiftSnapshotTest.java` | n/a (absence) | Missing test: no 21:00-anchored real-DB accept-path test with an excluded decoy row | ⚠️ Warning | Advisory 1 above — test-coverage gap, not a production defect; does not trigger `gaps_found` |

No `TBD`/`FIXME`/`XXX` markers found in any file this phase or its gap-closure plans modified
(`TimeslotController.java`, `DeskService.java`, `SolverService.java`, `ScheduleService.java`,
`BusinessDayPeriodLoader.java` all checked directly).

### Human Verification Required

None. Every finding in this report was confirmed by direct source inspection and/or a passing/failing
automated test re-run by this verifier; nothing here depends on visual appearance, real-time behavior,
or external-service integration.

### Gaps Summary

**Both gaps from the previous verification round are closed, confirmed by direct source inspection
and passing, substantive tests — not by trusting SUMMARY claims:**

1. **SC6 (gate removal + API reachability + precision gate)** — `TimeslotController.generateTimeslots`
   now resolves and passes `desk.getDayStart()` through a tenant-scoped lookup; a new test drives the
   real controller method (not the generator behind it) and proves the tiling refusal fires through it,
   with a positive control and a cross-tenant refusal; `DeskService.setDayStart` now refuses nonzero
   seconds/nanoseconds.

2. **Phase goal / CR-02 (`expandMinimumStaffingSeats`)** — both calendar-date reads are now
   business-date reads, proven on a 21:00-anchored desk with a named non-zero expected seat count so a
   silent zero-default cannot pass as correct.

**Beyond closing those two gaps**, this round's gap-closure work went further than required: plan
20-11 discharged the previous verification's own advisory ("is CR-02 the only remaining instance?") by
auditing all of `SolverService` and finding two more calendar-date reads (`runPreSolveValidation`'s
period-coverage and end-time checks) that the original phase's structural guard could never see; plan
20-12 then found and fixed four more sites at the repository-query level (`SolverService.startSolve`'s
problem-fact loads, `ScheduleService.acceptSchedule`'s snapshot loads) via a new shared
`BusinessDayPeriodLoader`, proven end-to-end against a real database. `bday-join-guard.md` now carries a
complete, named account of every one of these blind-spot shapes, turning "is this the only instance?"
from an assumption into a standing, documented answer.

**One new, non-blocking item surfaced by this round's own code review** (see Advisory): the
`BusinessDayPeriodLoader` fix proves inclusion and derivation-governs-the-stored-column at a 21:00
anchor, and proves exclusion at a midnight anchor, but no single test proves exclusion of an
out-of-range row through the real accept path at a non-midnight anchor specifically (the exact logic is
proven correct at the unit level). This does not negate any of the 7 ROADMAP success criteria as
literally written and the underlying production code was confirmed correct by direct read; it is
recorded here transparently as a recommended follow-up test, not a blocking gap.

All 8 requirement IDs (SOLV-01 through SOLV-07, BDAY-07) are satisfied. The phase goal is achieved: a
re-anchored desk's solver joins, pre-solve seat-supply check, SLOT-mode accounting, and demand
upload/coverage reporting all now resolve the same business date for the same timeslot, proven by
per-constraint match counts and a Phil-US-shaped live-desk-shaped fixture showing the re-anchoring
changed nothing it shouldn't have.

---

_Verified: 2026-10-02T14:10:00Z_
_Verifier: Claude (gsd-verifier)_
