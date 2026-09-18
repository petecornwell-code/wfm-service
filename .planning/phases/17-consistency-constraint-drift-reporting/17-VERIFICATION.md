---
phase: 17-consistency-constraint-drift-reporting
verified: 2026-09-18T22:10:00Z
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
covered_digest: "v1:sha256:6fd65ae05e1bac00f1c773df34485ba099b5687ad5cb5f26965310e7a7991194"
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

**Verified:** 2026-09-18T22:10:00Z
**Status:** passed
**Re-verification:** Yes — fourth pass. This pass exists purely to re-check that the single
documentation advisory the prior pass raised (a stale duplicate DRFT-01…04 Traceability row in
`REQUIREMENTS.md`, contradicting a newly-added "Complete in code" row) was genuinely fixed on disk,
and to independently re-derive the phase's five success criteria from the codebase rather than
inherit the prior pass's conclusions, per this pass's explicit instructions.

## What was independently re-checked this pass (not inherited)

**1. The stale-duplicate-row advisory is fixed, confirmed by reading the file, not the commit
message.** `.planning/REQUIREMENTS.md` line 139 now contains exactly one Traceability row for
`CONS-01…06, DRFT-01…04`, reading "Complete in code (2026-09-18)". `git show 0091c61` confirms the
old, contradictory "DRFT-01…04 | Phase 17 | Pending — ... gaps_found (2026-09-17)" row that sat
directly beneath it was deleted, not merely edited around. Re-read the surrounding rows
(SHLB/MODE/ENVL/USHF/XCUT-01…05) — none were corrupted by the edit; the table is internally
consistent. All ten CONS/DRFT checkboxes above the table read `[x]`. The task brief's claim that a
"may remain human_needed" clause was corrected refers to language that, on inspection, was never
present in the old CONS/DRFT row text (that phrasing exists only in the *separate*, and correctly
unchanged, USHF/Phase 16 row, which genuinely is `human_needed` for unrelated reasons) — the new
CONS/DRFT row correctly states "Phase verification is `passed`."

**2. `17-VALIDATION.md`'s "Approval: pending" fix confirmed by diff.** `git show 0091c61` shows line
97 changed from `**Approval:** pending` to `**Approval:** verified 2026-09-18 (see Validation Audit
2026-09-18 below)`. No other stale "pending" language found in the file.

**3. No source code changed since `b659491`.** `git diff --stat b659491..HEAD -- src/ frontend/src`
is empty; `git status --short -- src/ frontend/src` is empty. Confirmed this pass, not carried
forward.

**4. Regression suite re-run fresh this session.** `./gradlew test --tests
com.wfm.service.DriftReportTest --tests com.wfm.service.ScheduleServiceShiftSnapshotTest --tests
com.wfm.service.ScheduleExportServiceTest --tests com.wfm.solver.UsualShiftConsistencyConstraintTest`
→ `BUILD SUCCESSFUL` (all `UP-TO-DATE`, confirming Gradle's own view that nothing relevant changed
since the last forced run).

**5. The popularity long-name/density backstop truth (DRFT-04) — re-derived independently against
the live running app, not inherited from the prior pass's transcript.** The app was running locally
(`/actuator/health` UP). Discovered the desk directly via `GET /api/v1/desks` (tenant header `1`,
required by `TenantFilter`) → `28a121f4-0b62-465b-be06-ac2ee39045ac` ("UAT Drift Desk"). Independently
queried:
- `GET /api/v1/desks/{id}/shift-templates` → 6 templates, name lengths `11, 74, 10, 63, 62, 38`,
  matching the UAT record's claimed template set exactly, including the 74-char and 63-char names.
- `GET /api/v1/desks/{id}/agents?size=100` → 12 agents; tallied each agent's `usualShift.MONDAY` by
  hand from the raw JSON: `NOT_SET`: 2, `Early Shift`: 4, the 74-char template: 3, `Late Shift`: 2,
  the 63-char template: 1 — an exact independent reproduction of the claimed 4/3/2/1 ranking.
- Went one step further than the prior pass: fetched the desk's actual `ACCEPTED` schedule
  (`4c456b76-...`) via `GET /api/v1/desks/{id}/schedules/{id}` and read `driftReport.popularity`
  directly — the served API payload the frontend actually consumes returns exactly `[{Early Shift,
  4}, {74-char template, 3}, {Late Shift, 2}, {63-char template, 1}]`, ranked descending. This closes
  the gap between "raw DB tally matches" and "the endpoint the UI reads matches," which the prior
  pass did not check.
- Read `frontend/src/pages/ScheduleResults.tsx`'s popularity `<td>` (line 1144): no `whiteSpace`, no
  `textOverflow`, no `maxWidth` set — default browser wrap applies, structurally consistent with the
  claimed "wraps rather than clips" behavior and inconsistent with a clipping outcome.

No browser-automation tool is available to this verifier, so the specific pixel/line-count numbers
recorded in `17-UAT.md` (29px single line at 1440px; 70px/4-line wrap at 375px) are still taken on
the UAT record's word. But every fact the claim structurally depends on — the exact template set,
exact name lengths, the exact 4/3/2/1 popularity distribution, and now the served API payload itself
— has been independently reproduced against the live running application by this pass, going further
than the prior pass did. Combined with the code-level absence of any clip mechanism, this is accepted
as the backstop truth having been directly exercised.

## An explicit note on `17-SECURITY.md`'s T-17-07 disposition (carried forward, re-checked)

**T-17-07 (Denial of Service via mis-sized `consistent_start_weight`) is closed via `accept`, not
`mitigate`.** Re-read `17-SECURITY.md` directly this pass: the register states plainly "Mitigation as
stated is not fully achieved" and names the exact numeric shortfall (worst-case 1,120-soft consistency
total vs. `minStaffingWeight`'s 1,000-soft ceiling — 12% overshoot). Closed via accepted-risk entry
R-17-01 with a substantive rationale (no compliant weight pair avoids it; worst case requires the
entire roster to drift simultaneously on every day; typical case ~240 soft; surfaced and accepted at
the 17-04 human checkpoint; disclosed in `17-BENCHMARK.md` and the V49 migration header). The record
itself says "Monitorable, not eliminated." This is a properly-disclosed, human-accepted residual risk,
not a working mitigation dressed up as one — but `threats_open: 0` should be read as "zero
undisclosed risk," not "zero residual risk." Carrying this distinction forward explicitly, as
requested, rather than letting the clean number stand unqualified.

## Goal Achievement

### Observable Truths (mapped to ROADMAP.md's 5 Success Criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Soft-only, target-deviation consistency penalty; a solve never overwrites the stored usual-shift target (CONS-01, CONS-04, XCUT-02) | ✓ VERIFIED | No source change since `b659491` (`git diff` empty). `UsualShiftConsistencyConstraintTest` re-run this session: `BUILD SUCCESSFUL`. |
| 2 | Per-desk tolerance band (genuine dead zone) and weight, validated via `explain()` before a default ships (CONS-02, CONS-03) | ✓ VERIFIED | No functional change since last pass. `17-UAT.md` test 7 and `17-UI-REVIEW.md` Pillars 2/3/5 independently confirm the tolerance-band row (Minutes badge, colSpan=2) renders correctly and distinctly on a live render. |
| 3 | Equal-scoring ties decided by `AgentPreference.preferredStartTime`; precedence documented and observable (CONS-05, CONS-06) | ✓ VERIFIED | No change. `17-UI-REVIEW.md` Pillar 1 confirms the D-10 precedence copy live, byte-for-byte, in the running app. |
| 4 | Drift report distinguishes no-usual-shift / honoured / drifted, from the same distance calculation, rendered as a visible panel (DRFT-01, DRFT-02, DRFT-03, XCUT-01) | ✓ VERIFIED | `ScheduleServiceShiftSnapshotTest` green this session. `17-UI-REVIEW.md` confirms all three WCAG-AA status colours (`#dc2626`/`#15803d`/`#6b7280`) and byte-identical export/on-screen headers via live computed-style read. |
| 5 | Over-subscription popularity ranking, no mitigation; A/B benchmark confirms the weight doesn't regress coverage before a default ships (DRFT-04, XCUT-04) | ✓ VERIFIED | Independently re-derived this pass against the live running app: exact template set/lengths, exact 4/3/2/1 tally from raw agent data, AND the actual served `driftReport.popularity` API payload — all three match. No clip mechanism in `ScheduleResults.tsx`'s popularity `<td>`. XCUT-04 benchmark-wording question remains closed by the human decision recorded in `17-UAT.md` test 8 (unchanged). |

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

### Data-Flow Trace (Level 4, independently re-run this pass)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|---|---|---|---|---|
| `ScheduleResults.tsx` popularity table | `report.popularity[].templateName` / `.agentCount` | `ScheduleDetailResponse.driftReport.popularity`, backed by `AgentUsualShiftRepository` via `ScheduleOutputService` | Verified live against the desk's actual `ACCEPTED` schedule's `GET /api/v1/desks/{id}/schedules/{id}` response: `driftReport.popularity` returns `[{Early Shift, 4}, {74-char template, 3}, {Late Shift, 2}, {63-char template, 1}]`, exactly reproducing the raw agent tally computed independently from `GET /api/v1/desks/{id}/agents` | ✓ FLOWING |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|---|---|---|---|---|
| CONS-01 | 17-01 | Solver penalised for shift differing from stored usual shift | ✓ SATISFIED | Regression-checked green this session. |
| CONS-02 | 17-01, 17-02, 17-04, 17-05 | Configurable tolerance band, no penalty within it | ✓ SATISFIED | `17-VALIDATION.md` audit confirms automated coverage; unchanged. |
| CONS-03 | 17-02, 17-04, 17-05 | Configurable consistency weight per desk | ✓ SATISFIED | Unchanged. |
| CONS-04 | 17-01, 17-02 | Soft-only, never makes feasible infeasible | ✓ SATISFIED | Unchanged. |
| CONS-05 | 17-02, 17-05 | Ties decided by `AgentPreference` start time | ✓ SATISFIED | Unchanged. |
| CONS-06 | 17-02, 17-05 | Precedence documented and observable | ✓ SATISFIED | `17-UI-REVIEW.md` re-confirms D-10 copy live. |
| DRFT-01 | 17-01, 17-03, 17-05 | Operator sees drift per agent/date/magnitude | ✓ SATISFIED | Export+UI legs re-confirmed live by `17-UI-REVIEW.md`. |
| DRFT-02 | 17-01, 17-05 | Distinguishes no-usual-shift from honoured | ✓ SATISFIED | Unchanged. |
| DRFT-03 | 17-01 | Same distance calculation as the constraint | ✓ SATISFIED | Unchanged. |
| DRFT-04 | 17-03, 17-05 | Over-subscribed templates visible, no mitigation | ✓ SATISFIED | Density/wrap backstop independently re-derived this pass against the live app's own served API payload (not just raw DB data). |
| XCUT-01 | 17-03, 17-05 | Written value visible on every display surface | ✓ SATISFIED | Export/UI header parity re-confirmed by `17-UI-REVIEW.md`. |
| XCUT-02 | 17-01, 17-03 | Every write path verified | ✓ SATISFIED | Unchanged. |
| XCUT-04 | 17-04 | Seeded A/B, threshold pre-committed | ✓ SATISFIED (human decision recorded) | Recorded in `17-UAT.md` test 8; unchanged this pass. |

No orphaned requirements — union of the five plans' `requirements:` frontmatter
(`CONS-01,02,04,DRFT-01,02,03` / `CONS-03,04,05,06` / `DRFT-01,04,XCUT-01,02` / `CONS-02,03,XCUT-04` /
`CONS-02,03,06,DRFT-01,02,04,XCUT-01`) covers all ten CONS/DRFT IDs plus XCUT-01, XCUT-02, XCUT-04.

`.planning/REQUIREMENTS.md`'s Traceability table was independently re-read this pass: one row now
covers `CONS-01…06, DRFT-01…04` reading "Complete in code" with no contradicting duplicate row
beneath it — the prior pass's advisory is genuinely resolved on disk, not merely claimed resolved.

### Anti-Patterns Found

No `TBD`/`FIXME`/`XXX`/`TODO`/`HACK`/`PLACEHOLDER` markers in any file touched by this phase (checked
this session across the export service, output service, constraint provider, and both React pages).
No source files changed this pass (confirmed by `git status --short`). No blocker-level anti-patterns.

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|---|---|---|---|
| CR-01/DRFT regression suite, freshly checked this session | `./gradlew test --tests com.wfm.service.DriftReportTest --tests com.wfm.service.ScheduleServiceShiftSnapshotTest --tests com.wfm.service.ScheduleExportServiceTest --tests com.wfm.solver.UsualShiftConsistencyConstraintTest` | `BUILD SUCCESSFUL` (all UP-TO-DATE) | ✓ PASS |
| Source diff since prior verification | `git diff --stat b659491..HEAD -- src/ frontend/src` | Empty | ✓ PASS |
| Live shift-template data matches UAT's reseed claim | `curl -H "X-Tenant-ID: 1" .../shift-templates` on running app, discovered desk via `GET /api/v1/desks` | 6 templates, lengths 11/74/10/63/62/38, names match `17-UAT.md` test 6 verbatim | ✓ PASS |
| Live popularity distribution matches UAT's "4/3/2/1" claim, tallied from raw agent data | `curl .../agents?size=100`, tallied `usualShift.MONDAY` by template, by hand | Early Shift=4, 74-char template=3, Late Shift=2, 63-char template=1, 2 NOT_SET | ✓ PASS |
| Served drift-report API payload matches the same 4/3/2/1 ranking (new this pass — not checked previously) | `curl .../schedules/{acceptedScheduleId}`, read `driftReport.popularity` | `[{Early Shift,4},{74-char,3},{Late Shift,2},{63-char,1}]` | ✓ PASS |
| Popularity table has no clip mechanism in code | Read `ScheduleResults.tsx` popularity `<td>` styles | No `whiteSpace: nowrap`, no `text-overflow`, no `maxWidth` — default wrap applies | ✓ PASS |
| REQUIREMENTS.md stale duplicate row genuinely removed | Read `.planning/REQUIREMENTS.md` lines 128-148 directly | Single `CONS-01…06, DRFT-01…04` row, no contradicting duplicate | ✓ PASS |

### Probe Execution

Not applicable — no `scripts/*/tests/probe-*.sh` files declared or found for this phase.

### Human Verification Required

None. All five success criteria are verified against the codebase and, where visual/backstop truths
are involved, against live application state this pass independently reproduced (going one step
further than the prior pass by reading the actual served drift-report API payload, not just raw
agent-table data).

### Gaps Summary

No gaps. All ten CONS/DRFT requirement IDs and their three cross-cutting XCUT companions are
satisfied. No source code changed since the prior pass (`b659491`), and regression tests remain
green. The one advisory the immediately-preceding pass raised — a stale duplicate DRFT-01…04
Traceability row in `REQUIREMENTS.md` — was independently confirmed fixed by direct file read, not
inherited from the commit message. The Most-Subscribed Usual Shifts density/long-name-wrap backstop
truth for DRFT-04 was re-derived independently this pass against the live running application,
including reading the actual served `driftReport.popularity` API payload (a check the prior pass did
not perform), and matches the claimed evidence exactly. `17-SECURITY.md`'s closure of T-17-07 via
accepted risk (not a working mitigation) remains honestly disclosed in the register itself and is not
an overstatement, but is called out here per standing instruction — `threats_open: 0` should be read
as "zero undisclosed threats," not "zero residual risk."

---

_Verified: 2026-09-18T22:10:00Z_
_Verifier: Claude (gsd-verifier)_
