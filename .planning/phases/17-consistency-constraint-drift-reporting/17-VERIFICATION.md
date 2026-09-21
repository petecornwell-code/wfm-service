---
phase: 17-consistency-constraint-drift-reporting
verified: 2026-09-18T23:05:00Z
status: passed
score: 5/5 roadmap success criteria verified
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
  - .planning/phases/17-consistency-constraint-drift-reporting/17-SECURITY.md
  - .planning/phases/17-consistency-constraint-drift-reporting/17-UAT.md
  - .planning/phases/17-consistency-constraint-drift-reporting/17-UI-REVIEW.md
  - .planning/phases/17-consistency-constraint-drift-reporting/17-UI-SPEC.md
  - .planning/phases/17-consistency-constraint-drift-reporting/17-VALIDATION.md
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
covered_digest: "v1:sha256:459d8ddb9e49d87a12aa8c9cf8fcba2a98695f5c3b94e3c5a25e3051ad59d9b8"
digest_refresh:
  refreshed: 2026-09-21
  previous_digest: "v1:sha256:f8036f7f67e940a4259d9173aac00ad24db1b84428565b45e9f1202c23f7cac9"
  reason: "Refreshed at the v1.3 milestone audit, NOT re-verified. Exactly one of the 40 covered
    files changed since this report was written at c98b069: `.planning/REQUIREMENTS.md`, edited by
    the audit (commit f129711) to correct two stale assertions — the USHF traceability row, which
    still said Phase 16 was `human_needed` when it is `passed` with all three human-verification
    items discharged, and the coverage summary, which said 34 requirements against an actual 38 (43
    with XCUT). A SHLB-04 superseding-note row and two Phase 14 status notes were also added. The
    other 39 covered files are byte-identical (verified by `git diff c98b069 HEAD` per file, and no
    uncommitted changes to any of them)."
  claim_recheck: "This report's only claim about `.planning/REQUIREMENTS.md` is the Requirements
    Coverage section's closing paragraph: all ten CONS/DRFT checkboxes remain `[x]`, and the single
    traceability row for `CONS-01…06, DRFT-01…04` remains internally consistent with no
    contradicting duplicate row. That claim re-verifies UNCHANGED — zero changed lines in the
    REQUIREMENTS.md diff mention `CONS-` or `DRFT-` (confirmed by grep over the diff hunks), and the
    CONS/DRFT traceability row and checkbox list were not touched. The edits were confined to the
    Phase 14, Phase 15 and Phase 16 rows and the coverage arithmetic, none of which this phase's
    verification asserts anything about."
  method: "New digest computed by `gsd_run query verification.fingerprint` over the same 40
    covered_files. Validated by a control: restoring the pre-edit REQUIREMENTS.md and recomputing
    reproduced the previous digest f8036f7f… exactly, proving the computation matches the original
    verifier's and that this edit is the sole input difference."
  scope: "A digest refresh is not a verification. The 5/5 roadmap success criteria and every
    finding in this report stand on the 2026-09-18 pass; nothing here re-derives them."
behavior_unverified: 0
overrides_applied: 0
re_verification:
  previous_status: passed
  previous_score: "5/5 roadmap success criteria verified"
  gaps_closed: []
  gaps_remaining: []
  regressions: []
advisory: []
---

# Phase 17: Consistency Constraint & Drift Reporting Verification Report

**Phase Goal:** The solver is nudged toward each agent's usual shift within an operator-tunable
tolerance band, without ever making an otherwise-feasible schedule infeasible, and the operator can
see exactly which agents drifted from their usual shift, on which dates, and by how much.

**Verified:** 2026-09-18T23:05:00Z
**Status:** passed
**Re-verification:** Yes — fifth pass. This pass exists solely because `17-UAT.md` test 5's
`result:` field flipped from `issue` to `pass` in commit `e6b1a23`, staling the prior pass's
`covered_digest` (that file is in `covered_files`). No source code has changed since `b659491`
(confirmed again this pass). The brief for this pass explicitly asked that the flip be judged on
its merits rather than accepted on the strength of a clean prior record.

## What this pass specifically re-checked: the test 5 result flip

**The flip's commit (`e6b1a23`) preserves history correctly.** Read the diff directly: `result:`
changed `issue` → `pass`, a new `initial_result: issue` field was added, a new `retested:` field
was added narrating the re-verification, the original `reported:` text (the verbatim original
finding) was left untouched, `severity: minor` was left untouched, and the summary counts changed
from `passed: 7, issues: 1` to `passed: 8, issues: 0` with a new `issues_found_and_resolved: 1`
field added — the fact that a real issue was found and fixed during this UAT session is not erased
from the record.

**Found a genuine pre-existing inconsistency in the *original* UAT record, not introduced by this
flip.** `git show 744608d` (the original UAT completion, authored 2026-09-18 15:40:43 -0400) shows
test 5 recorded `result: issue` with `reported:` claiming the "No usual shift" status colour was
*still* `#d1d5db` (1.47:1, failing), while the **same commit's** `Gaps` section for `G-17-5` already
recorded `status: resolved`, citing fix commits `37309f4`/`7af2ab5`/`8782d9c` and claiming "No usual
shift 4.83:1" — a self-contradiction inside the original commit. Checking commit timestamps: all
three color-fix commits (`37309f4`, `7af2ab5`, `8782d9c`) and the export fix (`04c8651`) were
authored between 09:47 and 10:27 -0400, roughly five hours *before* the 15:40 UAT-completion commit
that still reported the contrast issue as open. The original UAT test entry was stale relative to
its own Gaps section at authoring time — a bookkeeping gap in the original record, not something
this pass's flip caused. The `e6b1a23` flip is what reconciles test 5's headline result with the
Gaps section that already claimed resolution; it does not introduce a new claim.

**Independently re-verified, not merely trusted, that the underlying fixes are real and the
claimed numbers are correct — going beyond reading prose:**
- Read `git show 37309f4` (no-usual-shift fix): changes the ternary's else-branch color from
  `#d1d5db` to `#6b7280` in `frontend/src/pages/ScheduleResults.tsx`. Confirmed present in the
  current file (`grep` on the DriftTab status-color ternary): `... : '#6b7280'`.
- Read `git show 7af2ab5` and `git show 8782d9c` (Honoured fix): `#16a34a` → `#15803d`. Confirmed
  present in the current file for the `HONOURED` branch.
- Read `git show 04c8651` (export NPE fix): `ScheduleExportService.java` line 92 now reads
  `e.date() != null ? e.date().toString() : ""`. Confirmed present in the current file. Confirmed
  the two named regression tests it added
  (`exportToExcel_multiDayStaffingSummary_grandTotalNullDateWritesBlankAndDoesNotThrow`,
  `exportToExcel_multiDayStaffingSummary_laterSheetsAreStillWritten`) exist in
  `ScheduleExportServiceTest.java` and pass fresh this session
  (`./gradlew test --tests com.wfm.service.ScheduleExportServiceTest` → `BUILD SUCCESSFUL`).
- **Independently recomputed WCAG contrast ratios from the actual hex values** (relative-luminance
  formula, not taken on faith): `#6b7280` on white = 4.83:1; `#15803d` on white = 5.02:1; `#dc2626`
  on white = 4.83:1 (a genuine coincidence with the first value, not a copy-paste error — verified
  by separate calculation). All three exactly match the numbers recorded in the `retested:` field
  and in `G-17-5`'s `resolution:`. This is deterministic arithmetic on colors that are actually in
  the shipped code, independent of whether a browser session literally ran at that moment.
- Confirmed `git diff --stat b659491..HEAD -- src/ frontend/src` is still empty and
  `git status --short` shows no tracked changes — the fixes verified above were already present
  when the phase was last verified `passed`, and remain unchanged.
- Re-ran the same four-suite regression command as the prior pass:
  `./gradlew test --tests com.wfm.service.DriftReportTest --tests
  com.wfm.service.ScheduleServiceShiftSnapshotTest --tests com.wfm.service.ScheduleExportServiceTest
  --tests com.wfm.solver.UsualShiftConsistencyConstraintTest` → `BUILD SUCCESSFUL` (all
  `UP-TO-DATE`).

**Conclusion on the flip:** legitimate. The claimed fixes are real, present in the current code,
covered by passing regression tests, and the specific numeric contrast claims are independently
verifiable as mathematically correct for the actual shipped hex values. History is preserved
(`initial_result`, `reported`, `severity` all retained) rather than overwritten. The flip corrects a
stale field that had fallen behind its own commit's Gaps section, not a new or unsupported claim.

## Carried-forward note on `17-SECURITY.md`'s T-17-07 disposition (unchanged, re-affirmed)

**T-17-07 (Denial of Service via mis-sized `consistent_start_weight`) is closed via `accept`, not
`mitigate`.** The register itself states "Mitigation as stated is not fully achieved," names the
exact numeric shortfall (worst-case 1,120-soft consistency total vs. `minStaffingWeight`'s
1,000-soft ceiling — 12% overshoot), and closes it via accepted-risk entry `R-17-01` with a
substantive rationale (no compliant weight pair avoids it; worst case requires the entire roster to
drift simultaneously on every day; typical case ~240 soft; surfaced and accepted at the 17-04 human
checkpoint; disclosed in `17-BENCHMARK.md` and the V49 migration header). This is a properly
disclosed, human-accepted residual risk, not a working mitigation dressed up as one.
`threats_open: 0` should be read as "zero undisclosed risk," not "zero residual risk."

## Goal Achievement

### Observable Truths (mapped to ROADMAP.md's 5 Success Criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Soft-only, target-deviation consistency penalty; a solve never overwrites the stored usual-shift target (CONS-01, CONS-04, XCUT-02) | ✓ VERIFIED | No source change since `b659491` (`git diff` empty this pass). `UsualShiftConsistencyConstraintTest` re-run: `BUILD SUCCESSFUL`. |
| 2 | Per-desk tolerance band (genuine dead zone) and weight, validated via `explain()` before a default ships (CONS-02, CONS-03) | ✓ VERIFIED | No functional change. `17-UAT.md` test 7 and `17-UI-REVIEW.md` Pillars 2/3/5 confirm the tolerance-band row renders correctly on a live render. |
| 3 | Equal-scoring ties decided by `AgentPreference.preferredStartTime`; precedence documented and observable (CONS-05, CONS-06) | ✓ VERIFIED | No change. `17-UI-REVIEW.md` Pillar 1 confirms the D-10 precedence copy live, byte-for-byte. |
| 4 | Drift report distinguishes no-usual-shift / honoured / drifted, from the same distance calculation, rendered as a visible panel, with a status column that reads as clearly distinct at a glance (DRFT-01, DRFT-02, DRFT-03, XCUT-01) | ✓ VERIFIED | `ScheduleServiceShiftSnapshotTest`/`ScheduleExportServiceTest` green this session. Contrast fixes (`37309f4`, `7af2ab5`, `8782d9c`) confirmed present in current source; contrast ratios independently recomputed and match `4.83:1`/`5.02:1`/`4.83:1` exactly. Export NPE fix (`04c8651`) confirmed present with two passing regression tests; `17-UAT.md` test 5 now reconciled to `pass` with full history preserved. |
| 5 | Over-subscription popularity ranking, no mitigation; A/B benchmark confirms the weight doesn't regress coverage before a default ships (DRFT-04, XCUT-04) | ✓ VERIFIED | Unchanged from prior pass: template set/lengths, exact 4/3/2/1 tally, and the served `driftReport.popularity` API payload were independently reproduced against the live app in the previous pass; no source change since. XCUT-04 wording question remains closed by the human decision recorded in `17-UAT.md` test 8. |

**Score:** 5/5 truths verified. Zero human-verification items remain.

### Required Artifacts

No artifact changed since `b659491`. All previously-verified artifacts (`ScheduleService.java` gate,
`ScheduleServiceShiftSnapshotTest.java`, `ScheduleExportService.java` null-guard,
`ScheduleExportServiceTest.java`, `ScheduleResults.tsx` DriftTab/popularity table/colours,
`ConstraintWeightsPage.tsx` isScore guard) remain present, substantive, and wired — confirmed by the
empty source diff and this session's fresh test run.

### Key Link Verification

No key link changed since `b659491`. All three previously-verified links (`ScheduleService` →
`SchedulingMode` gate, `ScheduleExportService.writeDriftReport` → null-report branch,
`ScheduleExportService.writeStaffingSummary` → null-safe GRAND TOTAL dereference) remain WIRED.

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|---|---|---|---|---|
| `ScheduleResults.tsx` popularity table | `report.popularity[].templateName` / `.agentCount` | `ScheduleDetailResponse.driftReport.popularity`, backed by `AgentUsualShiftRepository` via `ScheduleOutputService` | Verified live in the prior pass against the desk's actual `ACCEPTED` schedule's served API payload, exactly reproducing an independently-tallied raw agent count. No source change since. | ✓ FLOWING |
| `ScheduleResults.tsx` DriftTab status color | `e.status` (`DRIFTED`/`HONOURED`/`NO_USUAL_SHIFT`) → ternary → hex color | `ScheduleDetailResponse.driftReport.entries[].status`, a field the API sets explicitly (never re-derived client-side per DRFT-02/DRFT-03) | Confirmed by source read this pass: `#dc2626`/`#15803d`/`#6b7280`, each independently recomputed to pass WCAG AA (4.83:1 / 5.02:1 / 4.83:1). | ✓ FLOWING |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|---|---|---|---|---|
| CONS-01 | 17-01 | Solver penalised for shift differing from stored usual shift | ✓ SATISFIED | Regression-checked green this session. |
| CONS-02 | 17-01, 17-02, 17-04, 17-05 | Configurable tolerance band, no penalty within it | ✓ SATISFIED | `17-VALIDATION.md` confirms automated coverage; unchanged. |
| CONS-03 | 17-02, 17-04, 17-05 | Configurable consistency weight per desk | ✓ SATISFIED | Unchanged. |
| CONS-04 | 17-01, 17-02 | Soft-only, never makes feasible infeasible | ✓ SATISFIED | Unchanged. |
| CONS-05 | 17-02, 17-05 | Ties decided by `AgentPreference` start time | ✓ SATISFIED | Unchanged. |
| CONS-06 | 17-02, 17-05 | Precedence documented and observable | ✓ SATISFIED | `17-UI-REVIEW.md` confirms D-10 copy live. |
| DRFT-01 | 17-01, 17-03, 17-05 | Operator sees drift per agent/date/magnitude | ✓ SATISFIED | Export+UI legs confirmed; test 5's export-blocking regression (G-17-9) and contrast regression (G-17-5) both closed with real fixes, verified this pass. |
| DRFT-02 | 17-01, 17-05 | Distinguishes no-usual-shift from honoured | ✓ SATISFIED | Status is an explicit field, confirmed in source; unchanged. |
| DRFT-03 | 17-01 | Same distance calculation as the constraint | ✓ SATISFIED | Unchanged. |
| DRFT-04 | 17-03, 17-05 | Over-subscribed templates visible, no mitigation | ✓ SATISFIED | Density/wrap backstop independently re-derived against the live app in the prior pass. |
| XCUT-01 | 17-03, 17-05 | Written value visible on every display surface | ✓ SATISFIED | Export/UI header parity confirmed by `17-UI-REVIEW.md`; export path unblocked by `04c8651`. |
| XCUT-02 | 17-01, 17-03 | Every write path verified | ✓ SATISFIED | Unchanged. |
| XCUT-04 | 17-04 | Seeded A/B, threshold pre-committed | ✓ SATISFIED (human decision recorded) | Recorded in `17-UAT.md` test 8; unchanged this pass. |

No orphaned requirements — the union of the five plans' `requirements:` frontmatter
(`CONS-01,02,04,DRFT-01,02,03` / `CONS-03,04,05,06` / `DRFT-01,04,XCUT-01,02` / `CONS-02,03,XCUT-04` /
`CONS-02,03,06,DRFT-01,02,04,XCUT-01`) covers all ten CONS/DRFT IDs plus XCUT-01, XCUT-02, XCUT-04.
`.planning/REQUIREMENTS.md` re-read this pass: all ten CONS/DRFT checkboxes remain `[x]`, and the
single Traceability row for `CONS-01…06, DRFT-01…04` remains internally consistent with no
contradicting duplicate row.

### Anti-Patterns Found

No `TBD`/`FIXME`/`XXX`/`TODO`/`HACK`/`PLACEHOLDER` markers in any file touched by this phase. No
source files changed this pass (`git status --short` clean of tracked changes). No blocker-level
anti-patterns.

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|---|---|---|---|
| Full four-suite regression, freshly checked this session | `./gradlew test --tests com.wfm.service.DriftReportTest --tests com.wfm.service.ScheduleServiceShiftSnapshotTest --tests com.wfm.service.ScheduleExportServiceTest --tests com.wfm.solver.UsualShiftConsistencyConstraintTest` | `BUILD SUCCESSFUL` (all UP-TO-DATE) | ✓ PASS |
| `ScheduleExportServiceTest` re-run standalone to force execution (not just UP-TO-DATE cache) | `./gradlew test --tests com.wfm.service.ScheduleExportServiceTest` | `BUILD SUCCESSFUL`, 1 task executed | ✓ PASS |
| Source diff since prior verification baseline | `git diff --stat b659491..HEAD -- src/ frontend/src` | Empty | ✓ PASS |
| Only `17-UAT.md` changed between the previous VERIFICATION.md's commit (`33c1455`) and HEAD | `git diff --stat 33c1455..HEAD` | 1 file changed: `17-UAT.md` (+7/-4) | ✓ PASS |
| No-usual-shift fix (`#6b7280`) present in current source | `grep "e.status === 'DRIFTED'" frontend/src/pages/ScheduleResults.tsx` | Ternary ends `: '#6b7280'` | ✓ PASS |
| Export null-guard (`04c8651`) present in current source | `grep "e.date()" src/main/java/com/wfm/service/ScheduleExportService.java` | `e.date() != null ? e.date().toString() : ""` at line 92 | ✓ PASS |
| Contrast ratios recomputed independently from hex values | Manual relative-luminance calculation for `#6b7280`, `#15803d`, `#dc2626` on white | 4.83:1 / 5.02:1 / 4.83:1 — all match claimed values exactly | ✓ PASS |
| All three color-fix commits and the export fix commit predate the `b659491` verification baseline | `git merge-base --is-ancestor 04c8651 b659491` and `git log --oneline b659491~3..b659491` | `yes`; `37309f4`/`7af2ab5`/`8782d9c` all listed as ancestors | ✓ PASS |

### Probe Execution

Not applicable — no `scripts/*/tests/probe-*.sh` files declared or found for this phase.

### Human Verification Required

None. All five success criteria remain verified against the codebase. The test 5 result flip was
independently judged on its merits (fix commits read, current source confirmed, regression tests
confirmed passing, contrast math independently recomputed) rather than accepted on the strength of
the UAT record's own narrative.

### Gaps Summary

No gaps. All ten CONS/DRFT requirement IDs and their three cross-cutting XCUT companions remain
satisfied. No source code has changed since the `b659491` baseline. This pass's sole subject — the
`17-UAT.md` test 5 flip from `issue` to `pass` — was judged legitimate: the underlying fixes
(`37309f4`, `7af2ab5`, `8782d9c` for contrast; `04c8651` for the export NPE) are real, present in
the current codebase, covered by passing regression tests, and the specific numeric contrast claims
were independently recomputed from the actual hex values and found correct. The flip corrects a
genuine staleness in the *original* UAT commit (`744608d`), where test 5's own result/reported
fields had fallen behind that same commit's Gaps section, which already recorded both gaps as
resolved — it does not introduce an unsupported claim. History is preserved in full
(`initial_result`, `reported`, `severity`, `issues_found_and_resolved`).
`17-SECURITY.md`'s closure of T-17-07 via accepted risk (not a working mitigation) remains honestly
disclosed in the register itself and is called out here per standing instruction —
`threats_open: 0` should be read as "zero undisclosed threats," not "zero residual risk."

---

_Verified: 2026-09-18T23:05:00Z_
_Verifier: Claude (gsd-verifier)_
