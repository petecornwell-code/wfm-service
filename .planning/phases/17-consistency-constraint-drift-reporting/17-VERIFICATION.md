---
phase: 17-consistency-constraint-drift-reporting
verified: 2026-09-17T22:40:00Z
status: gaps_found
score: 3/5 roadmap success criteria fully verified (1 failed, 1 human-judgment-needed)
covered_files:
  - .planning/REQUIREMENTS.md
  - .planning/phases/17-consistency-constraint-drift-reporting/17-01-PLAN.md
  - .planning/phases/17-consistency-constraint-drift-reporting/17-01-SUMMARY.md
  - .planning/phases/17-consistency-constraint-drift-reporting/17-02-PLAN.md
  - .planning/phases/17-consistency-constraint-drift-reporting/17-02-SUMMARY.md
  - .planning/phases/17-consistency-constraint-drift-reporting/17-03-PLAN.md
  - .planning/phases/17-consistency-constraint-drift-reporting/17-03-SUMMARY.md
  - .planning/phases/17-consistency-constraint-drift-reporting/17-04-PLAN.md
  - .planning/phases/17-consistency-constraint-drift-reporting/17-04-SUMMARY.md
  - .planning/phases/17-consistency-constraint-drift-reporting/17-05-PLAN.md
  - .planning/phases/17-consistency-constraint-drift-reporting/17-05-SUMMARY.md
  - .planning/phases/17-consistency-constraint-drift-reporting/17-BENCHMARK.md
  - .planning/phases/17-consistency-constraint-drift-reporting/17-REVIEW.md
  - frontend/src/api/client.ts
  - frontend/src/pages/ConstraintWeightsPage.tsx
  - frontend/src/pages/ScheduleResults.tsx
  - src/main/java/com/wfm/dto/ConstraintWeightsDto.java
  - src/main/java/com/wfm/dto/ScheduleDetailResponse.java
  - src/main/java/com/wfm/model/ConstraintWeights.java
  - src/main/java/com/wfm/model/ResolvedUsualShiftTarget.java
  - src/main/java/com/wfm/model/Schedule.java
  - src/main/java/com/wfm/model/ScheduleConfig.java
  - src/main/java/com/wfm/model/ShiftBandPair.java
  - src/main/java/com/wfm/service/ConstraintWeightsService.java
  - src/main/java/com/wfm/service/ScheduleExportService.java
  - src/main/java/com/wfm/service/ScheduleOutputService.java
  - src/main/java/com/wfm/service/ScheduleService.java
  - src/main/java/com/wfm/service/SolverService.java
  - src/main/java/com/wfm/solver/ScheduleConstraintProvider.java
  - src/main/resources/db/migration/V48__add_consistency_tolerance_and_preferred_start_weight.sql
  - src/main/resources/db/migration/V49__set_consistency_weight_defaults.sql
covered_digest: "v1:sha256:651ae31d43c007b26391bc82e9dd7c2376f44719deb9b1359ce0c2bacd715e13"
behavior_unverified: 0
overrides_applied: 0
gaps:
  - truth: "The drift report and its Excel export honour the same slot-mode contract the drift tab itself honours — the backend's own documented invariant that ScheduleDetailResponse.driftReport is null on a slot-scheduled desk."
    status: failed
    reason: >
      ScheduleService.getScheduleDetail (line 157) calls
      scheduleOutputService.buildDriftReport(schedule) unconditionally, with no
      SchedulingMode gate. ScheduleOutputService.buildDriftReport (lines 458-565)
      has no code path that returns null -- it always constructs and returns a
      DriftReport(entries, summary, popularity). On a SLOT-scheduled desk this
      produces an empty main table (never the documented not-applicable state) AND
      a genuinely POPULATED "Most-Subscribed Usual Shifts" popularity table in the
      Excel export, because ScheduleOutputService.buildDriftReport's popularity
      block (lines 556-562) reads agentUsualShiftRepository.findByTenantIdAndDeskId
      independent of the desk's current scheduling mode -- and stored usual-shift
      rows are proven to survive a SHIFT->SLOT mode switch byte-identical
      (UsualShiftWritePathTest.switchSchedulingMode_roundTrip_leavesStoredUsualShiftsFieldIdentical),
      so this is a reachable, not hypothetical, state on a desk that used to run
      SHIFT mode. This directly contradicts 17-01-PLAN.md's own
      <verification> line: "ScheduleDetailResponse.driftReport is non-null on a
      shift-desk schedule detail and null-safe on a slot desk" -- the "null-safe on
      a slot desk" half was never implemented. The React DriftTab is unaffected
      (frontend/src/pages/ScheduleResults.tsx:1054 branches on
      schedule.schedulingMode before ever reading driftReport), but the Excel
      export inherits the bug because ScheduleExportService.writeDriftReport's
      `report == null` guard (lines 255-258) can now never fire on the real
      end-to-end path.
    artifacts:
      - path: "src/main/java/com/wfm/service/ScheduleService.java"
        issue: "Line 157 calls buildDriftReport unconditionally with no SchedulingMode gate, contradicting the DTO's own documented null-on-SLOT contract (ScheduleDetailResponse.java:190-199) and this plan's own committed <verification> claim"
      - path: "src/main/java/com/wfm/service/ScheduleOutputService.java"
        issue: "buildDriftReport (lines 458-565) has no return-null path for any scheduling mode; popularity (lines 546-562) is not mode-gated at all, so it renders a real, non-trivial table in the Excel export of a SLOT-mode schedule for any desk carrying stored usual-shift rows"
      - path: "src/main/java/com/wfm/service/ScheduleExportService.java"
        issue: "writeDriftReport's `report == null` early return (lines 255-258) is dead code on the real /desks/{deskId}/schedules/{id}/export path today, since report is never null in practice"
      - path: "src/test/java/com/wfm/service/ScheduleExportServiceTest.java"
        issue: "exportToExcel_nullDriftReport_sheetHasOnlyTheMainHeaderRow (line 158) claims in its own comment to exercise a SLOT-scheduled desk but only constructs a ScheduleDetailResponse directly and never sets driftReport -- it never calls the real ScheduleService.getScheduleDetail code path for a SLOT-mode schedule, so it cannot and does not catch this regression"
    missing:
      - "Gate the call in ScheduleService.getScheduleDetail on schedule.getSchedulingMode() == SchedulingMode.SHIFT, matching the DTO's documented null-on-SLOT contract (the fix the 17-REVIEW.md CR-01 finding already specifies verbatim)"
      - "A regression test that solves/accepts a SLOT-mode schedule for a desk carrying at least one stored AgentUsualShift row, calls getScheduleDetail, and asserts response.getDriftReport() is null"
      - "Update or rename ScheduleExportServiceTest's null-drift-report test to actually go through ScheduleService for a SLOT-mode schedule, so it stops claiming to prove something the current test cannot prove"
human_verification:
  - test: "Confirm whether XCUT-04's success criterion (\"A seeded A/B benchmark confirms the consistency constraint's weight doesn't regress coverage/break quality before a default ships\") is satisfied by 17-BENCHMARK.md's actual result."
    expected: >
      The committed A/B benchmark did NOT measure a coverage-vs-consistency
      trade-off -- every arm (weight 0/1/2/10), every seed, converged on an
      identical construction-heuristic outcome (all 50 agent-days assigned
      "Late"), so the comparative "no worse than baseline" pass rule was satisfied
      trivially (by identity), not by evidence that a nonzero weight is safe. The
      shipped default (consistentStartWeight=2/preferredStartShiftModeWeight=1) is
      instead justified by a separate arithmetic projection
      (agentCount x workingDays x excessIncrements x weight) validated against
      explain()'s per-match magnitude, which shows a WORST-CASE total (~1,120
      soft, entire 28-agent roster drifting every day of a 5-day period) that is
      12% OVER minStaffingWeight's 1000-soft ceiling -- the exact failure mode
      this benchmark exists to catch. This was surfaced to, and accepted by, a
      human at 17-04's blocking checkpoint as a documented residual risk (no
      compliant nonzero weight pair avoids it under D-08's ordering invariant).
      The write-up itself is honest and follows the committed pass rule (a null
      result reported as a null result, never a win) -- but the roadmap's
      criterion, read literally, describes a confirmation that did not happen by
      measurement. Confirm whether the arithmetic-plus-explain()-plus-documented-
      residual-risk substitute is an acceptable satisfaction of Success Criterion
      5, or whether it should be tracked as an open item pending real drift
      telemetry (which 17-BENCHMARK.md itself proposes the shipped Phase 17 drift
      report can supply retroactively).
    why_human: "This is a judgment call about whether a compensating analytical method (sizing arithmetic + explain() breakdown), explicitly disclosed as a substitute after the intended solve-driven A/B hit a known construction-heuristic plateau, satisfies the roadmap's specific wording -- not something a grep or test run can decide, and it was already the subject of one blocking-human checkpoint (17-04) whose decision this verification is not overriding, only surfacing for final sign-off against the roadmap's literal success-criterion text."
  - test: "Status-column visual distinctness -- confirm a Drifted row draws the eye at a glance in the Drift Report tab without a fourth colour."
    expected: "Drifted renders red/bold (#dc2626, weight 600), Honoured renders green (#16a34a), No usual shift renders muted gray (#d1d5db) -- frontend/src/pages/ScheduleResults.tsx:1099-1105. Confirm this reads as clearly distinct in the browser, not just in the style values."
    why_human: "Visual/backstop truth per 17-UI-SPEC.md; no frontend test framework exists in this project (17-05-SUMMARY.md, confirmed)."
  - test: "Most-Subscribed Usual Shifts list density/wrap on a desk with an unusually large shift library."
    expected: "The ranked list remains readable without a scroll or density problem."
    why_human: "Visual/backstop truth per 17-UI-SPEC.md; not automatable in this project."
  - test: "Long shift-template name wrapping in the popularity table."
    expected: "An operator-authored long template name wraps rather than clips and still reads cleanly."
    why_human: "Visual/backstop truth per 17-UI-SPEC.md; not automatable in this project."
  - test: "Minutes badge and merged tolerance-band cell read as a different kind of field from Hard/Soft rows."
    expected: "The tolerance-band row (Minutes badge, single input, colSpan=2) reads as clearly distinct from a broken score row, not as a rendering bug."
    why_human: "Visual/backstop truth per 17-UI-SPEC.md; not automatable in this project."
---

# Phase 17: Consistency Constraint & Drift Reporting Verification Report

**Phase Goal:** The solver is nudged toward each agent's usual shift within an operator-tunable
tolerance band, without ever making an otherwise-feasible schedule infeasible, and the operator can
see exactly which agents drifted from their usual shift, on which dates, and by how much.

**Verified:** 2026-09-17T22:40:00Z
**Status:** gaps_found
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths (mapped to ROADMAP.md's 5 Success Criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Soft-only, target-deviation consistency penalty; a solve never overwrites the stored usual-shift target (CONS-01, CONS-04, XCUT-02) | ✓ VERIFIED | `ScheduleConstraintProvider.usualShiftConsistency` (lines 815-836) computes `ShiftBandPair.startDeviationMinutes(assignedStart, target.usualStartTime())` per agent-day, penalising only the excess past `cfg.consistencyToleranceMinutes()` — a target-deviation formula, not the reverted spread-based one. `ConstraintWeightsService.updateWeights` (lines 133-136) rejects any save with a nonzero hard component on `consistentStartWeight`. `SolverUsualShiftWritePathGuardTest#resolveUsualShiftTargets_zeroMutatingInteractionsOnTheRepository` and `#solverServiceSource_noCodeLineInvokesAMutatingMethodOnTheUsualShiftRepository` prove zero writes to `agent_usual_shift` from the solver path, both behaviourally and via structural source scan. |
| 2 | Per-desk tolerance band (genuine dead zone) and weight, validated via `explain()` before a default ships (CONS-02, CONS-03) | ✓ VERIFIED | `usualShiftConsistency`'s `.filter(...)` (line 825) only fires when deviation `>` tolerance — `UsualShiftConsistencyConstraintTest#deviationEqualsBand_noPenalty` (60-min deviation, 60-min band → penalty 0) and `#deviationOneMinuteBeyondBand_penalisedByOne` (61-min deviation → penalty 1) prove a true dead zone, not a taper, at the exact boundary. `ConstraintWeightsService` (lines 133-150) enforces hard=0, D-08 precedence, and non-negative tolerance at save time, mapped to HTTP 400 `VALIDATION_FAILED` by `GlobalExceptionHandler.handleIllegalArgument`. `ConstraintPrecedenceObservabilityTest` and `17-BENCHMARK.md`'s `explain()` breakdown section both independently confirm the shipped weight's per-match magnitude against `SolutionManager.explain()` before V49 shipped the default. |
| 3 | Equal-scoring ties decided by `AgentPreference.preferredStartTime`; precedence documented and observable (CONS-05, CONS-06) | ✓ VERIFIED | `ScheduleConstraintProvider.preferredStartShiftMode` (lines 877-894) penalises the absolute (both-direction) deviation from `preferredStartTime`, independent of whether a usual shift is stored (D-09). `ConstraintWeightsService` (lines 141-145) rejects any save where `preferredStartShiftModeWeight.softScore() >= consistentStartWeight.softScore()`, converting precedence into a checked invariant. `ConstraintPrecedenceObservabilityTest#bothConstraints_appearAsTwoDistinctEntriesEachWithAPositiveMatchCount` proves two separate `explain()` entries, never merged. `ConstraintWeightsPage.tsx` (lines 143-159) renders the precedence note in plain words directly beneath the third new row. |
| 4 | Drift report distinguishes no-usual-shift / honoured / drifted, from the same distance calculation, rendered as a visible panel (DRFT-01, DRFT-02, DRFT-03, XCUT-01) | ✗ **FAILED** (see Gap below) | The on-screen `DriftTab` (`ScheduleResults.tsx:1050-1144`) itself is correct: it branches on `schedule.schedulingMode` first, and renders `DriftStatus`-keyed rows (`NO_USUAL_SHIFT`/`HONOURED`/`DRIFTED`) computed from `ShiftBandPair.startDeviationMinutes` in both the constraint and `ScheduleOutputService.buildDriftReport` (confirmed: single shared static method, both call sites verified). **However**, `ScheduleService.getScheduleDetail` (line 157) never gates `buildDriftReport` on scheduling mode, contradicting both the DTO's own documented `null`-on-SLOT contract and 17-01-PLAN.md's own committed `<verification>` line — see the Gap below. This leaks a populated "Most-Subscribed Usual Shifts" table into the Excel export of a SLOT-mode schedule for any desk with stored usual-shift rows, which is exactly the kind of report-vs-export mismatch XCUT-01 exists to catch (v1.2 audit finding I-1). |
| 5 | Over-subscription popularity ranking, no mitigation; A/B benchmark confirms the weight doesn't regress coverage before a default ships (DRFT-04, XCUT-04) | ⚠️ Popularity: VERIFIED. Benchmark confirmation: **NEEDS HUMAN** (see Human Verification) | `ScheduleOutputService.buildDriftReport`'s popularity block (lines 546-562) ranks templates by distinct-agent count descending, alphabetical tie-break, with no rebalancing logic anywhere in the diff. `DriftTab`'s "Most-Subscribed Usual Shifts" section and its subtext ("Reads each agent's current usual shift... does not change when you re-solve") render this correctly. The A/B benchmark itself (`17-BENCHMARK.md`) is honestly reported but produced **no measurable difference** at any weight — a construction-heuristic plateau, verified as reproducible (20,000-step re-run, reversed list order) rather than a step-budget/list-order artifact — so it did not, by measurement, confirm the weight is safe. The shipped default instead rests on a redone sizing-arithmetic projection plus `explain()`, with a documented worst-case overshoot of the `minStaffingWeight` ceiling (12% over) accepted as residual risk at a blocking-human checkpoint. This is an honest null-result write-up per the phase's own Phase-12 rule, not a silent gap — but it does not literally satisfy "a seeded A/B benchmark confirms... doesn't regress." |

**Score:** 3/5 roadmap success criteria fully verified; 1 failed (CR-01, unresolved code-review CRITICAL); 1 needs an explicit human decision on whether a disclosed compensating method satisfies the literal wording.

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `src/main/java/com/wfm/model/ResolvedUsualShiftTarget.java` | Pre-resolved problem fact | ✓ VERIFIED | `public record ResolvedUsualShiftTarget(UUID agentId, LocalDate date, LocalTime usualStartTime)` present, used by `SolverService.resolveUsualShiftTargets` and `usualShiftConsistency`'s join. |
| `src/main/java/com/wfm/model/ShiftBandPair.java` | Shared distance calculation | ✓ VERIFIED | `startDeviationMinutes` (lines 97-103): `Math.abs(ChronoUnit.MINUTES.between(...))`, null-safe, called from both the constraint and `buildDriftReport`. |
| `src/main/resources/db/migration/V48__...sql` | New columns | ✓ VERIFIED | `consistency_tolerance_minutes` present (confirmed via 17-01-SUMMARY and code reading `cfg.consistencyToleranceMinutes()`). |
| `src/main/resources/db/migration/V49__set_consistency_weight_defaults.sql` | Benchmark-derived shipped defaults | ✓ VERIFIED | Predicated `UPDATE`, cites `17-BENCHMARK.md`; `ConstraintWeightsMigrationTest` proves both column defaults and untouched-row safety against a real Postgres/Flyway chain. |
| `src/test/java/com/wfm/solver/UsualShiftConsistencyConstraintTest.java` | CONS-01/02/04 coverage | ✓ VERIFIED | Dead-zone boundary, SLOT-mode silence, null-band-pair no-penalty, penalised-magnitude tests all present. |
| `src/test/java/com/wfm/service/DriftReportTest.java` | DRFT-01/02/03 coverage | ✓ VERIFIED (exists, referenced by 17-01/17-03 SUMMARYs; not independently re-read line-by-line beyond confirming the shared-calculation call sites it targets are real) | |
| `src/test/java/com/wfm/solver/UsualShiftConsistencyBenchmarkTest.java` | Gated seeded A/B harness | ✓ VERIFIED (exists, gated, produced the transcribed results in `17-BENCHMARK.md`) | `@EnabledIfSystemProperty`-gated; confirmed skipped in the default 784-test suite per 17-04-SUMMARY's "4 skipped (2 benchmark)" count. |
| `frontend/src/pages/ScheduleResults.tsx` (`DriftTab`) | Drift Report tab | ✓ VERIFIED | Lines 1050-1144: slot-mode not-applicable message, summary bar recomputed from the date-filtered set, status-coloured rows, popularity section with its own empty state. |
| `frontend/src/pages/ConstraintWeightsPage.tsx` | Three new config rows + precedence note | ✓ VERIFIED | Lines 28-30 (CONSTRAINTS entries), 104-123 (tolerance-band own render branch), 143-159 (precedence note). |

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| `SolverService` | `Schedule` | `resolveUsualShiftTargets(...)` sets `resolvedUsualShiftTargets` problem facts | ✓ WIRED | Confirmed by `SolverUsualShiftWritePathGuardTest` and `usualShiftConsistency`'s join against `ResolvedUsualShiftTarget`. |
| `ScheduleConstraintProvider` | `ShiftBandPair` | `startDeviationMinutes` shared static method | ✓ WIRED | Both `usualShiftConsistency` (line 823/827) and `preferredStartShiftMode` (line 886) call it. |
| `ScheduleOutputService` | `ShiftBandPair` | `startDeviationMinutes` shared static method | ✓ WIRED | Called by `buildDriftReport`'s per-entry deviation computation (confirmed in the file read). |
| `ScheduleOutputService` | `AgentUsualShiftRepository` | tenant-and-desk-scoped `findByTenantIdAndDeskId` | ✓ WIRED | Used for both the drift entries' stored-shift lookup and the popularity ranking (lines 459-460). |
| `ScheduleService` | `ScheduleDetailResponse` | `setDriftReport` + date-filter recompute block | ✓ WIRED | Lines 157, 232-249: recomputes `DriftSummary` from the filtered entry set; popularity is deliberately carried through unfiltered. |
| `ScheduleService` | `SchedulingMode` gate on `buildDriftReport` | Documented DTO contract: `driftReport` null on a SLOT-scheduled desk | ✗ **NOT WIRED** | See Gap. No gate exists; `buildDriftReport` is called unconditionally and never returns null. |
| `ScheduleExportService` | `ScheduleDetailResponse` | `writeDriftReport` consumes `detail.getDriftReport()` | ⚠️ PARTIAL | The write path itself is correctly wired to the same payload the UI reads (byte-identical headers confirmed against `ScheduleExportServiceTest`'s header-literal tests), but its `null`-report branch is now dead code because of the gap above — the export can no longer distinguish a genuinely not-applicable (SLOT) desk from an empty-but-applicable (SHIFT, zero drift) one. |
| `ConstraintWeightsService` | `GlobalExceptionHandler` | `IllegalArgumentException` → 400 `VALIDATION_FAILED` | ✓ WIRED | `GlobalExceptionHandler.handleIllegalArgument` (lines 32-35) confirmed. |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|---|---|---|---|---|
| CONS-01 | 17-01 | Solver penalised for shift differing from stored usual shift | ✓ SATISFIED | `usualShiftConsistency` |
| CONS-02 | 17-01, 17-02, 17-04, 17-05 | Configurable tolerance band, deviation within it carries no penalty | ✓ SATISFIED | Dead-zone tests + `ConstraintWeightsPage` tolerance row + `explain()` validation |
| CONS-03 | 17-02, 17-04, 17-05 | Configurable consistency weight per desk | ✓ SATISFIED | `PUT /constraint-weights` round-trip + page row |
| CONS-04 | 17-01, 17-02 | Consistency is soft-only, never makes feasible infeasible | ✓ SATISFIED | Hard=0 save-time rejection |
| CONS-05 | 17-02, 17-05 | Ties decided by `AgentPreference` start time | ✓ SATISFIED | `preferredStartShiftMode` anchor, D-09 independence |
| CONS-06 | 17-02, 17-05 | Precedence documented and observable, not implicit | ✓ SATISFIED | D-08 save-time rejection + two `explain()` entries + UI precedence note |
| DRFT-01 | 17-01, 17-03, 17-05 | Operator sees which agents drifted, dates, magnitude | ✓ SATISFIED (UI panel) — see Gap for the export-surface caveat | `DriftTab`, date-filter recompute |
| DRFT-02 | 17-01, 17-05 | Distinguishes no-usual-shift from honoured | ✓ SATISFIED | Explicit `DriftStatus` field, three-state tests |
| DRFT-03 | 17-01 | Same distance calculation as the constraint | ✓ SATISFIED | Shared `ShiftBandPair.startDeviationMinutes` |
| DRFT-04 | 17-03, 17-05 | Over-subscribed templates visible, no mitigation | ✓ SATISFIED | Popularity ranking, no rebalancing logic found |
| XCUT-01 | 17-03, 17-05 | Written value visible in every display surface (roster, export, accepted view, drift report) | ✗ **BLOCKED** | UI leg correct; export leg leaks a populated popularity table on a SLOT-mode desk instead of the documented not-applicable state — see Gap (CR-01) |
| XCUT-02 | 17-01, 17-03 | Every write path verified, not only the one its phase built | ✓ SATISFIED | `SolverUsualShiftWritePathGuardTest` (behavioural + structural proof) |
| XCUT-04 | 17-04 | Seeded, step-count-terminated A/B, median + min/max, threshold pre-committed | ✓ SATISFIED (process) / ⚠️ outcome inconclusive by measurement | See Human Verification — process was followed correctly and honestly; the specific "confirms no regression" claim was not established by the A/B itself |

No orphaned requirements: every ID in `.planning/REQUIREMENTS.md`'s CONS/DRFT/XCUT rows for Phase 17 traces to a plan's `requirements:` frontmatter field.

### Anti-Patterns Found

No `TBD`/`FIXME`/`XXX`/`TODO`/`HACK`/`PLACEHOLDER` markers found in the phase's modified backend or frontend files. No stub `return null`/`return {}`/empty-handler patterns found in the reviewed constraint, service, DTO, or React files. The one substantive defect found (CR-01) is a missing conditional branch, not a stub or placeholder — already fully documented above and in the Gaps section.

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|---|---|---|---|
| Dead-zone boundary (deviation == band ⇒ 0 penalty) | `./gradlew test --tests "com.wfm.solver.UsualShiftConsistencyConstraintTest"` (cached UP-TO-DATE from the prior full-suite green run) | `deviationEqualsBand_noPenalty` / `deviationOneMinuteBeyondBand_penalisedByOne` both pass | ✓ PASS |
| Two distinct `explain()` entries for consistency + preference | `./gradlew test --tests "com.wfm.solver.ConstraintPrecedenceObservabilityTest"` (cached UP-TO-DATE) | `bothConstraints_appearAsTwoDistinctEntriesEachWithAPositiveMatchCount` passes | ✓ PASS |
| Drift report / shared-calculation agreement | `./gradlew test --tests "com.wfm.service.DriftReportTest"` (cached UP-TO-DATE) | Passes as part of the confirmed 784-test green suite | ✓ PASS |
| Full regression suite | Already run and reported by the executor/prior gate (`784 tests, 0 failures, 0 errors, 4 skipped`); re-confirmed here via targeted `--tests` runs returning `UP-TO-DATE`/`BUILD SUCCESSFUL`, not re-run in full per the single-full-run-per-verification constraint | 784/784 non-skipped green | ✓ PASS |
| CR-01 reproduction (static-analysis form) | Read `ScheduleService.java:157`, `ScheduleOutputService.java:458-565`, `ScheduleExportService.java:244-258`, `ScheduleExportServiceTest.java:158-166` directly | No `SchedulingMode` gate found on the `getScheduleDetail`→`buildDriftReport` call; `buildDriftReport` has no `return null`; the "null drift report" test never calls the real `ScheduleService` path | ✗ FAIL — confirms 17-REVIEW.md's CR-01 exactly |

### Probe Execution

Not applicable — no `scripts/*/tests/probe-*.sh` files declared or found for this phase.

### Human Verification Required

See frontmatter `human_verification`. Five items: (1) an explicit decision on whether XCUT-04's Success Criterion 5 wording is satisfied by the disclosed arithmetic-plus-explain() substitute given the benchmark's honest null result, and (2)-(5) the four visual/backstop truths 17-05-SUMMARY.md itself already flagged as `human_judgment: true` (Status-column distinctness, popularity list density/wrap, long-name wrap, Minutes-badge/merged-cell read) — none automatable in this project, which has no frontend test framework.

### Gaps Summary

**One BLOCKER (CR-01, carried over unresolved from `17-REVIEW.md`):** `ScheduleService.getScheduleDetail` calls `ScheduleOutputService.buildDriftReport` unconditionally, and `buildDriftReport` has no code path that returns `null`. This contradicts (a) the `ScheduleDetailResponse.DriftReport` javadoc's own documented contract ("`null` on a slot-scheduled desk"), and (b) 17-01-PLAN.md's own committed `<verification>` line ("`ScheduleDetailResponse.driftReport` is... null-safe on a slot desk"). The practical consequence, verified directly against source: on a SLOT-scheduled desk that still carries stored `AgentUsualShift` rows (a proven-reachable, tested-as-persistent state per `UsualShiftWritePathTest`), the Excel export's "Drift Report" sheet gets an empty main table (not the required not-applicable state) plus a genuinely populated "Most-Subscribed Usual Shifts" section, because the popularity ranking is not mode-gated at all. The React `DriftTab` is unaffected because it branches on `schedule.schedulingMode` before ever reading `driftReport` — the leak is export-only, but the export is one of the four surfaces XCUT-01 explicitly requires ("roster, export, accepted-schedule view, drift report"). The one test whose name and comment claim to cover this (`ScheduleExportServiceTest.exportToExcel_nullDriftReport_sheetHasOnlyTheMainHeaderRow`) never calls the real `ScheduleService` code path, so the regression is genuinely untested, not merely undertested. This is squarely inside Phase 17's own scope (17-01/17-03), not a pre-existing or deferred issue, and it has a small, well-specified fix already written out in `17-REVIEW.md`'s CR-01 finding.

**One item requiring an explicit human decision, not a code fix:** Success Criterion 5's clause "A seeded A/B benchmark confirms the consistency constraint's weight doesn't regress coverage/break quality before a default ships" was not established by measurement — the benchmark hit a known, independently-verified construction-heuristic plateau and produced a null result across every arm and seed. The team's response (redone sizing arithmetic, `explain()`-verified per-match cost, and an explicit blocking-human checkpoint accepting a documented 12%-over-ceiling worst-case residual risk) is honest, well-documented, and consistent with this project's Phase-12 lesson about not overclaiming noise as a win — but it is a different form of evidence than the roadmap's literal wording describes. This verifier is not able to determine on its own whether that substitution is an acceptable closure of Success Criterion 5 or should remain an open item pending real drift telemetry from Phase 17's own (now-shipped) drift report.

---

_Verified: 2026-09-17T22:40:00Z_
_Verifier: Claude (gsd-verifier)_
