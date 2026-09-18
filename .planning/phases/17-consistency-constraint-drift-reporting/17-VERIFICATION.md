---
phase: 17-consistency-constraint-drift-reporting
verified: 2026-09-18T14:05:00Z
status: human_needed
score: 4/5 roadmap success criteria fully verified (1 split — code portion verified, benchmark-wording portion needs human sign-off)
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
  - .planning/phases/17-consistency-constraint-drift-reporting/17-REVIEW-FIX.md
  - .planning/phases/17-consistency-constraint-drift-reporting/17-REVIEW.md
  - .planning/phases/17-consistency-constraint-drift-reporting/17-UAT.md
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
  - src/test/java/com/wfm/service/DriftReportTest.java
  - src/test/java/com/wfm/service/ScheduleExportServiceTest.java
  - src/test/java/com/wfm/service/ScheduleServiceShiftSnapshotTest.java
covered_digest: "v1:sha256:5372577a919ebf86a01086bf5e9be0e76d645812f9ee79a373899fb5624a3b00"
behavior_unverified: 0
overrides_applied: 0
re_verification:
  previous_status: gaps_found
  previous_score: 3/5
  gaps_closed:
    - "CR-01: ScheduleService.getScheduleDetail now gates response.setDriftReport(...) on schedule.getSchedulingMode() == SchedulingMode.SHIFT (ea9b6ac), restoring ScheduleDetailResponse.DriftReport's documented null-on-SLOT contract. Because the gate short-circuits before buildDriftReport is even called, the popularity leak into the Excel export on a SLOT desk is also closed as a side effect, not just the main table's empty-vs-null distinction."
    - "G-17-9 (Excel export HTTP 500 on every multi-day schedule, ScheduleExportService.writeStaffingSummary NullPointerException on the GRAND TOTAL null-date row) fixed (04c8651), unblocking end-to-end observation of the CR-01 fix through the real export path for the first time."
    - "G-17-5 (Drift Report 'No usual shift' status colour #d1d5db measuring 1.47:1, failing WCAG AA) fixed (37309f4) by moving to #6b7280 (4.83:1) — a deliberate, documented deviation from 17-UI-SPEC.md's literal #d1d5db value that preserves its stated 'muted gray' intent."
  gaps_remaining: []
  regressions: []
gaps: []
advisory:
  - finding: "7af2ab5 and 8782d9c re-tone the Drift Report's Honoured status (and, in 8782d9c, the same green app-wide across 6 other files: Toast.tsx, ClientManagement.tsx, Configuration.tsx, ConstraintWeightsPage.tsx, ScheduleSetup.tsx, StaffingRequirements.tsx) from #16a34a (3.30:1, WCAG AA large-text only) to #15803d (5.02:1, passes normal-text AA). This is new scope beyond both the original 17-UI-SPEC.md and the G-17-5 gap (which named only the no-usual-shift colour)."
    category: other
    reason: "Independently recomputed the contrast math for the three Drift Report colours used in this phase (dc2626 4.83:1, 15803d 5.02:1, 6b7280 4.83:1) and confirmed all three pass WCAG AA normal-text 4.5:1 — the phase-scoped portion of this change is verified, not merely asserted. The app-wide sweep's other 6 files are outside Phase 17's file set and were not independently re-verified pixel-by-pixel here (only tsc --noEmit clean and the commit's own stated verification were checked); flagged for visibility, not as a phase-blocking concern, since none of those files gate this phase's success criteria."
    evidence_status: "phase-scoped colours independently recomputed and confirmed; app-wide sweep files not independently re-verified"
human_verification:
  - test: "Confirm whether XCUT-04's success criterion (\"A seeded A/B benchmark confirms the consistency constraint's weight doesn't regress coverage/break quality before a default ships\") is satisfied by 17-BENCHMARK.md's actual result, or should be tracked as an open item pending real drift telemetry."
    expected: >
      Unchanged since the previous verification (2026-09-17) and still unresolved — 17-BENCHMARK.md
      has had no commits since ea0b625 (2026-09-17), and 17-UAT.md's test 8 (this exact question,
      re-surfaced from this file's human_verification) is recorded as `result: [pending]`. The
      committed A/B benchmark did NOT measure a coverage-vs-consistency trade-off: every arm
      (weight 0/1/2/10), every seed, converged on an identical construction-heuristic outcome, so
      the comparative pass rule was satisfied trivially by identity, not by evidence that a nonzero
      weight is safe. The shipped default (consistentStartWeight=2/preferredStartShiftModeWeight=1)
      instead rests on a redone sizing-arithmetic projection validated against explain(), disclosing
      a worst-case 12% overshoot of minStaffingWeight's 1000-soft ceiling, accepted at the 17-04
      blocking-human checkpoint as documented residual risk. The write-up is honest (a null result
      reported as a null result). This verifier does not resolve the question — it is carried
      forward exactly as posed by the prior verification and by 17-UAT.md test 8, for an explicit
      human decision.
    why_human: "Judgment call about whether a disclosed compensating analytical method satisfies the roadmap's literal wording; already the subject of one blocking-human checkpoint (17-04) and one pending UAT question (test 8) that this verification does not override or resolve."
  - test: "Status-column visual distinctness — confirm a Drifted row draws the eye at a glance in the Drift Report tab, and that all three status colours (now #dc2626, #15803d, #6b7280) read as clearly distinct in an actual browser render, not just in computed contrast ratios."
    expected: "Drifted red/bold, Honoured green, No usual shift muted gray — all three now independently confirmed ≥4.5:1 contrast on white by this verification's own recomputation, and per 17-UAT.md test 5, already visually confirmed live in-browser this session with matching computed rgb() values."
    why_human: "Visual/backstop truth per 17-UI-SPEC.md; already substantially covered by this session's live browser evidence (17-UAT.md test 5, result: issue → now addressed by the two subsequent contrast-fix commits), carried forward only because that live check predates the final 8782d9c sweep commit and was not re-run against it."
  - test: "Most-Subscribed Usual Shifts list density/wrap on a desk with an unusually large shift library, and long shift-template-name wrapping."
    expected: "The ranked list remains readable without a scroll or density problem; a long operator-authored template name wraps rather than clips."
    why_human: "Visual/backstop truth per 17-UI-SPEC.md; not automatable in this project (no frontend test framework). 17-UAT.md test 6 explicitly notes this was NOT exercised — the seeded library only has 2 short template names."
  - test: "Minutes badge and merged tolerance-band cell read as a different kind of field from Hard/Soft rows on the Constraint Weights page."
    expected: "The tolerance-band row (Minutes badge, single input, colSpan=2) reads as clearly distinct from a broken score row, not as a rendering bug."
    why_human: "Visual/backstop truth per 17-UI-SPEC.md; not automatable in this project. 17-UAT.md test 7 confirms the row renders with correct structure (27 rows, no console error) but this is a visual read, not a structural one."
---

# Phase 17: Consistency Constraint & Drift Reporting Verification Report

**Phase Goal:** The solver is nudged toward each agent's usual shift within an operator-tunable
tolerance band, without ever making an otherwise-feasible schedule infeasible, and the operator can
see exactly which agents drifted from their usual shift, on which dates, and by how much.

**Verified:** 2026-09-18T14:05:00Z
**Status:** human_needed
**Re-verification:** Yes — after gap closure (previous: `gaps_found`, 2026-09-17T22:40:00Z, 3/5, committed 0a8608e)

## Goal Achievement

### Observable Truths (mapped to ROADMAP.md's 5 Success Criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Soft-only, target-deviation consistency penalty; a solve never overwrites the stored usual-shift target (CONS-01, CONS-04, XCUT-02) | ✓ VERIFIED (regression check — unchanged since prior pass) | No commits since the prior `passed` verdict touch `ScheduleConstraintProvider`, `SolverService`, or the usual-shift write-path guard tests. `./gradlew test` re-run this session: 788 tests, 0 failures, 0 errors, 4 skipped (gated benchmark tests) — includes `UsualShiftConsistencyConstraintTest` and `SolverUsualShiftWritePathGuardTest`. Live evidence this session independently confirms: a real solve on a 12-agent SHIFT desk with 50 stored usual-shift rows left `agent_usual_shift` byte-identical (md5 unchanged) before/after. |
| 2 | Per-desk tolerance band (genuine dead zone) and weight, validated via `explain()` before a default ships (CONS-02, CONS-03) | ✓ VERIFIED (regression check) | No functional change since the prior pass. WR-01 (7ac545e) replaced an unchecked `as Record<string, Score>` cast in `ConstraintWeightsPage.tsx` with a runtime `isScore` guard, purely a hardening fix for a hypothetical future field — the commit's own note and this session's re-check confirm behavior for all 24 existing rows (including the tolerance-band row) is unchanged: `npx tsc --noEmit` clean, and 17-UAT.md test 7 confirms the page still renders 27 rows including the three new Consistency rows with no console error. |
| 3 | Equal-scoring ties decided by `AgentPreference.preferredStartTime`; precedence documented and observable (CONS-05, CONS-06) | ✓ VERIFIED (regression check) | No commits since the prior pass touch `preferredStartShiftMode`, `ConstraintWeightsService`'s D-08 precedence rejection, or the precedence-note render. `ConstraintPrecedenceObservabilityTest` still green in the full-suite re-run. |
| 4 | Drift report distinguishes no-usual-shift / honoured / drifted, from the same distance calculation, rendered as a visible panel (DRFT-01, DRFT-02, DRFT-03, XCUT-01) | ✓ VERIFIED — previously FAILED, gap now closed | `ScheduleService.getScheduleDetail` (line 158-159, confirmed by direct read) now reads `schedule.getSchedulingMode() == SchedulingMode.SHIFT ? scheduleOutputService.buildDriftReport(schedule) : null` — the gate fires *before* `buildDriftReport` is ever invoked, so the popularity leak into the Excel export (the other half of the original CR-01 finding) is closed as a structural side effect, not merely filtered after construction. Two new behavioral tests in `ScheduleServiceShiftSnapshotTest` (`getScheduleDetail_inMemorySlotModeSchedule_driftReportIsNullAndNeverBuilt`, `getScheduleDetail_inMemoryShiftModeSchedule_driftReportIsBuilt`) assert via `Mockito.verify(..., never())`/`times(1)` that the call itself is/isn't made — both re-run green this session. `ScheduleExportServiceTest`'s null-drift-report test was renamed and re-commented to stop claiming end-to-end SLOT-mode coverage it never had. The pre-existing export-blocking NPE (G-17-9, `ScheduleExportService.writeStaffingSummary` dereferencing a null GRAND TOTAL date) is independently fixed (04c8651) with two new regression tests, both confirmed by the commit message to fail with the guard reverted. This unblocked the first-ever real end-to-end observation, confirmed live this session: a SLOT-solved desk with 50 stored usual-shift rows returned `driftReport: null` and its Excel export's Drift Report sheet had only the header row, zero popularity content. Export headers (`Agent, Date, Status, Usual Start, Actual Start, Delta (min)`) confirmed byte-identical between `ScheduleExportService.writeDriftReport` and the on-screen `DriftTab` table by direct source comparison. Full suite re-run: 788/788 non-skipped tests green (`DriftReportTest`'s in-memory fixture required its own follow-up fix, 49ab8bd, to set `SchedulingMode.SHIFT` explicitly once the CR-01 gate started enforcing it — a stale-fixture-meets-new-gate breakage, not a logic regression, and it is now green). See "Nuance on the closure method" below. |
| 5 | Over-subscription popularity ranking, no mitigation; A/B benchmark confirms the weight doesn't regress coverage before a default ships (DRFT-04, XCUT-04) | ⚠️ Popularity: VERIFIED. Benchmark confirmation: **STILL NEEDS HUMAN** (unchanged) | `ScheduleOutputService.buildDriftReport`'s popularity block (unchanged since prior pass) ranks templates by distinct-agent count descending, alphabetical tie-break, with no rebalancing logic. 17-UAT.md test 6 independently confirms correct counts against seeded data (Early Shift 8, Late Shift 2) with the disclosure subtext present. `17-BENCHMARK.md` has had zero commits since the prior verification (last commit `ea0b625`, 2026-09-17) — the benchmark's null result and the honest write-up are unchanged, and the question of whether the arithmetic-plus-`explain()` substitute satisfies the roadmap's literal "a seeded A/B benchmark confirms..." wording remains genuinely open. 17-UAT.md's own test 8 records this exact question with `result: [pending]` as of this session. This verifier does not resolve it — see Human Verification. |

**Score:** 4/5 truths fully verified by code+test evidence; 1 truth split (code-verified popularity half; benchmark-wording half explicitly deferred to human judgment, unresolved both in the prior verification and in this session's own UAT).

### Nuance on the CR-01 Closure Method

The prior gap's `missing:` list asked for "a regression test that solves/accepts a SLOT-mode
schedule for a desk carrying at least one stored `AgentUsualShift` row, calls `getScheduleDetail`,
and asserts `response.getDriftReport()` is null." What shipped instead
(`ScheduleServiceShiftSnapshotTest`, mocked `ScheduleOutputService`) asserts `buildDriftReport` is
**never invoked** in SLOT mode, using an in-memory schedule with no usual-shift repository data
involved at all (the repository is mocked out).

Judged as **at least equivalent, arguably stronger**, not weaker: the actual code fix is a pure
`SchedulingMode` conditional with no data-dependent branch (confirmed by direct source read — there
is no `if (usualShifts.isEmpty())`-style logic anywhere in the gate). A "never invoked" proof holds
regardless of what stored usual-shift data exists, which is a strictly more general guarantee than
one specific desk-with-data fixture would have been — a data-carrying fixture could only prove the
same gate for the one dataset it constructs, while the mock-verify proof holds for all data shapes
by construction. The narrower, data-specific case the original gap asked about is separately closed
by this session's own live manual evidence (real Postgres desk with 50 stored usual-shift rows,
solved under SLOT mode, confirmed `driftReport: null` and a header-only export sheet) — so both the
structural (all data shapes) and the concrete (real data, real DB) forms of the guarantee are now
independently established, just by two different pieces of evidence rather than one combined test.

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `src/main/java/com/wfm/service/ScheduleService.java` | SchedulingMode gate on `buildDriftReport` | ✓ VERIFIED | Lines 157-160: ternary gate confirmed present, matches CR-01's prescribed fix exactly. |
| `src/test/java/com/wfm/service/ScheduleServiceShiftSnapshotTest.java` | CR-01 regression tests | ✓ VERIFIED | Both new tests read and confirmed present; re-run green this session. |
| `src/main/java/com/wfm/service/ScheduleExportService.java` | Null-safe GRAND TOTAL date + reachable null-drift-report branch | ✓ VERIFIED | `e.date() != null ? e.date().toString() : ""` guard confirmed at `writeStaffingSummary`; `writeDriftReport`'s `report == null` branch confirmed reachable via the CR-01 gate, with an explanatory comment against future removal-as-dead-code. |
| `src/test/java/com/wfm/service/ScheduleExportServiceTest.java` | NPE regression tests + re-commented null-drift test | ✓ VERIFIED | Two new tests (`exportToExcel_multiDayStaffingSummary_grandTotalNullDateWritesBlankAndDoesNotThrow`, `..._laterSheetsAreStillWritten`) read and confirmed present; renamed null-drift-report test's new comment confirmed accurate to what it actually tests. |
| `frontend/src/pages/ScheduleResults.tsx` (`DriftTab`) | Slot-mode not-applicable message; three WCAG-AA-passing status colours | ✓ VERIFIED | `schedulingMode !== 'SHIFT'` branch confirmed at top of `DriftTab`. Colours confirmed at lines ~1111-1119: `#dc2626` (Drifted), `#15803d` (Honoured), `#6b7280` (No usual shift) — all three independently recomputed to ≥4.5:1 contrast on white by this verifier (4.83, 5.02, 4.83 respectively), matching the commits' own claimed figures. |
| `frontend/src/pages/ConstraintWeightsPage.tsx` | `isScore` runtime guard (WR-01) | ✓ VERIFIED | Guard function and per-row narrowing confirmed present; `npx tsc --noEmit` clean. |

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| `ScheduleService` | `SchedulingMode` gate on `buildDriftReport` | Documented DTO contract: `driftReport` null on a SLOT-scheduled desk | ✓ WIRED (previously NOT WIRED) | Gate confirmed present and behaviorally proven by two new Mockito-verify tests plus this session's live-DB evidence. |
| `ScheduleExportService.writeDriftReport` | `ScheduleDetailResponse.driftReport` | Null-report early-return branch | ✓ WIRED (previously PARTIAL/dead) | Branch is now reachable in production for real SLOT-mode desks; confirmed live this session (header-only sheet, no popularity leak). |
| `ScheduleExportService.writeStaffingSummary` | `StaffingSummaryEntry.date()` | Null-safe dereference on the synthetic GRAND TOTAL row | ✓ WIRED (previously threw NPE, HTTP 500) | Guard confirmed present; live export confirmed 200/20,958 bytes with all five sheets, replacing the prior 500. |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|---|---|---|---|---|
| CONS-01 | 17-01 | Solver penalised for shift differing from stored usual shift | ✓ SATISFIED | Unchanged from prior pass; regression-checked green. |
| CONS-02 | 17-01, 17-02, 17-04, 17-05 | Configurable tolerance band, no penalty within it | ✓ SATISFIED | Unchanged from prior pass; regression-checked green. |
| CONS-03 | 17-02, 17-04, 17-05 | Configurable consistency weight per desk | ✓ SATISFIED | Unchanged from prior pass. |
| CONS-04 | 17-01, 17-02 | Soft-only, never makes feasible infeasible | ✓ SATISFIED | Unchanged from prior pass. |
| CONS-05 | 17-02, 17-05 | Ties decided by `AgentPreference` start time | ✓ SATISFIED | Unchanged from prior pass. |
| CONS-06 | 17-02, 17-05 | Precedence documented and observable | ✓ SATISFIED | Unchanged from prior pass. |
| DRFT-01 | 17-01, 17-03, 17-05 | Operator sees drift per agent/date/magnitude | ✓ SATISFIED — gap closed | Both UI and export legs now confirmed correct end to end. |
| DRFT-02 | 17-01, 17-05 | Distinguishes no-usual-shift from honoured | ✓ SATISFIED | Unchanged from prior pass. |
| DRFT-03 | 17-01 | Same distance calculation as the constraint | ✓ SATISFIED | Unchanged from prior pass. |
| DRFT-04 | 17-03, 17-05 | Over-subscribed templates visible, no mitigation | ✓ SATISFIED | Unchanged from prior pass; independently re-confirmed by 17-UAT.md test 6 counts. |
| XCUT-01 | 17-03, 17-05 | Written value visible on every display surface | ✓ SATISFIED — previously BLOCKED | Export leg no longer leaks; both surfaces now agree, including on a SLOT-mode desk. |
| XCUT-02 | 17-01, 17-03 | Every write path verified | ✓ SATISFIED | Unchanged from prior pass. |
| XCUT-04 | 17-04 | Seeded A/B, threshold pre-committed | ⚠️ Process satisfied; outcome-confirmation wording still open | Unchanged from prior pass — carried to human verification, not resolved by this session's other fixes (none touch the benchmark). |

No orphaned requirements: every ID in `.planning/REQUIREMENTS.md`'s CONS/DRFT rows for Phase 17
traces to a plan's `requirements:` frontmatter field (confirmed: `17-01` through `17-05` PLAN
frontmatter collectively cover CONS-01…06 and DRFT-01…04).

### Anti-Patterns Found

No `TBD`/`FIXME`/`XXX`/`TODO`/`HACK`/`PLACEHOLDER` markers found in any file touched by the commits
since the prior verification (`ScheduleService.java`, `ScheduleOutputService.java`,
`ScheduleExportService.java`, `ScheduleResults.tsx`, `ConstraintWeightsPage.tsx`,
`ScheduleServiceShiftSnapshotTest.java`, `ScheduleExportServiceTest.java`, `DriftReportTest.java`).
No stub or empty-implementation patterns found. No blocker-level anti-patterns.

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|---|---|---|---|
| Full regression suite | `./gradlew test` (full run, once, this session) | 788 tests, 0 failures, 0 errors, 4 skipped (gated benchmark tests) | ✓ PASS |
| CR-01 regression (SLOT mode never invokes buildDriftReport; SHIFT mode does) | `./gradlew test --tests com.wfm.service.ScheduleServiceShiftSnapshotTest --tests com.wfm.service.DriftReportTest --tests com.wfm.service.ScheduleExportServiceTest --rerun` | `BUILD SUCCESSFUL`, all green | ✓ PASS |
| Frontend type-check (WR-01, contrast changes) | `npx tsc --noEmit -p frontend/tsconfig.json` | No output (clean) | ✓ PASS |
| Contrast recomputation (Drifted/Honoured/No-usual-shift) | Independent WCAG relative-luminance calculation for `#dc2626`, `#15803d`, `#6b7280` on white | 4.83:1, 5.02:1, 4.83:1 — all ≥4.5:1 | ✓ PASS |
| Export header parity | Direct source comparison of `ScheduleExportService.writeDriftReport`'s `cols` array against `DriftTab`'s `<th>` labels | Byte-identical: Agent, Date, Status, Usual Start, Actual Start, Delta (min) | ✓ PASS |

### Probe Execution

Not applicable — no `scripts/*/tests/probe-*.sh` files declared or found for this phase.

### Human Verification Required

See frontmatter `human_verification`. Four items, of which one is a carried-forward, still-unresolved
decision this verifier explicitly does not make: (1) whether XCUT-04's Success Criterion 5 wording is
satisfied by the disclosed arithmetic-plus-`explain()` substitute given the benchmark's honest null
result — unchanged and still pending in 17-UAT.md test 8; (2)-(4) visual/backstop truths (status-column
distinctness, popularity list density/wrap on a large library, Minutes-badge/merged-cell read) that
this project has no frontend test framework to automate, though (2) is already substantially covered
by this session's own live-browser evidence and only needs a final confirmation pass against the last
contrast-fix commit.

### Gaps Summary

No blocking gaps. The one previously FAILED success criterion (4: drift report's slot-mode contract)
is now closed with both automated regression tests and live end-to-end evidence against a real
database. The dependent export-blocking NPE (G-17-9) is independently fixed with its own regression
tests. The G-17-5 WCAG contrast gap is closed, and two further contrast issues discovered along the
way (Honoured green, and the same green's other 10 app-wide call sites) were also fixed, with the
phase-scoped colours independently re-verified by this report. The one remaining open item —
Success Criterion 5's literal "a seeded A/B benchmark confirms... doesn't regress" wording against an
honest null result — was already surfaced for human judgment by the prior verification and remains
open in this session's own UAT (test 8, pending); this report carries it forward unresolved rather
than adopting either the prior verifier's framing or 17-UAT.md's framing as a de facto answer.

---

_Verified: 2026-09-18T14:05:00Z_
_Verifier: Claude (gsd-verifier)_
