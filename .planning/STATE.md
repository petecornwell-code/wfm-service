---
gsd_state_version: 1.0
milestone: v1.2
milestone_name: Unified Agent Provisioning
current_phase: 13
current_phase_name: Per-Day Hours Visibility
status: executing
stopped_at: Completed 13-06-PLAN.md (gap closure) -- phase 13 all 6 plans executed
last_updated: "2026-08-24T12:56:27.543Z"
last_activity: 2026-08-24
last_activity_desc: Phase 13 execution started
state_head: 91b469e9ad66e75aece847257ef9dc47a0e922e7
progress:
  total_phases: 5
  completed_phases: 3
  total_plans: 23
  completed_plans: 23
  percent: 60
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-08-21)

**Core value:** Scheduling managers can produce optimised, constraint-aware agent schedules in minutes instead of hours — without spreadsheets.
**Current focus:** Phase 13 — Per-Day Hours Visibility

## Current Position

Phase: 13 (Per-Day Hours Visibility) — GAP CLOSURE COMPLETE (6/6 plans)
Plan: 6 of 6
Status: All 6 plans executed (13-01..13-06); gap-closure plans 13-05/13-06 addressed the two FAILED
UI truths, the WR-01/WR-02 code-review warnings, and both behavior_unverified_items from
13-VERIFICATION.md. Backend 315 tests green (`./gradlew test --rerun-tasks`, up from 309 baseline);
frontend build clean. Re-verification against 13-VERIFICATION.md's `gaps`/`behavior_unverified_items`
not yet re-run — phase not yet formally re-verified/marked complete.
Next: `/gsd-verify-work 13` to confirm the gap-closure plans resolved the prior `gaps_found` verdict.
Last activity: 2026-08-24 — Phase 13 gap-closure execution (13-05, 13-06) complete

Progress: [████████████████████] 23/23 plans ([██████░░░░] 60%)

## Milestone v1.2 Roadmap

18/18 requirements mapped across 3 phases, plus Phase 12 added mid-milestone. See `.planning/ROADMAP.md` for full detail.

| Phase | Name | Requirements | Status |
|-------|------|--------------|--------|
| 9 | Agent Data Model Foundation | MDL-01, MDL-02, MDL-03 | **Complete (6/6 plans)** — verified 4/4 must-haves 2026-08-21 |
| 10 | Enriched Upload Parsing | UPL-01–UPL-08 | **Complete (6/6 plans)** — verified + UAT 8/8 + SECURITY threats_open:0 2026-08-21 |
| 11 | BambooHR Merge Engine & Report | MRG-01–MRG-07 | **Complete (2/2 plans)** — verified + UAT passed 2026-08-21 |
| 12 | Atomic Shift Move | (added mid-milestone) | **WITHDRAWN** — goal not achieved, code reverted (`299c42c`); see 12-VERIFICATION.md |

## Milestone v1.1 Outcome

Closed early on 2026-07-29 with **4 of 16 requirements shipped**. Phases 5–6 delivered the BambooHR agent-data foundation; Phases 7–8 (reporting, diagnostics, export, solver tuning) were never planned.

| Phase | Name | Requirements | Status |
|-------|------|--------------|--------|
| 5 | Agent Data Enrichment & Desk Upload | DATA-01, DATA-02, DATA-03 | Complete (5/5 plans) |
| 6 | Solver Quality Constraints — PTO & Weekends | QUAL-01 | Complete (3/3 plans) |
| 7 | Coverage, Utilization & Diagnostics | RPT-01, RPT-02, QUAL-04, DIAG-01, DIAG-02 | Deferred → Backlog 999.5 |
| 8 | Export, Score Breakdown & Tuning | RPT-03–RPT-06, QUAL-05 | Deferred → Backlog 999.6 |

Archives: `.planning/milestones/v1.1-ROADMAP.md`, `.planning/milestones/v1.1-REQUIREMENTS.md`, `.planning/milestones/v1.1-phases/`

## Deferred Items

Items deferred at v1.1 milestone close on 2026-07-29:

| Category | Item | Status |
|----------|------|--------|
| requirement | QUAL-02 weekend-position fairness | Deferred → Backlog 999.4 |
| requirement | QUAL-03 day-to-day hours consistency | Deferred → Backlog 999.4 |
| phase | 07: Coverage, Utilization & Diagnostics (RPT-01, RPT-02, QUAL-04, DIAG-01, DIAG-02) | Deferred → Backlog 999.5 |
| phase | 08: Export, Score Breakdown & Tuning (RPT-03–06, QUAL-05) | Deferred → Backlog 999.6 |
| security | BambooHR API key rotation and public-repo scrub | Deferred → Backlog 999.7 |
| debt | Operator-facing UI for data-gap / outlier agents (currently log.warn only) | Deferred → Backlog 999.5 |
| debt | `loadSnapshotData()` missing problem facts for accepted schedules | Deferred → Backlog 999.5 (blocks 999.6) |
| verification | Desk-scale data-gap exclusion proportion on live desks never measured | Open — see 06-VERIFICATION.md human_verification |

Items deferred at v1.0 milestone close on 2026-04-21:

| Category | Item | Status |
|----------|------|--------|
| phase | 02-02: OIDC & IAM setup | Deferred → Backlog 999.1 |
| phase | 03-02: Infrastructure verification | Deferred → Backlog 999.2 |
| phase | 04: CI/CD Pipeline & Go-Live | Deferred → Backlog 999.3 |

**Blocker for 999.1–999.3:** `pete.cornwell@helpware.com` has PowerUserAccess which excludes `iam:CreateRole`. Root/admin must grant `WFMTerraformIAMPermissions`. See `.planning/milestones/v1.0-ROADMAP.md` for full resume steps.

## Accumulated Context

### Roadmap Evolution

- Phase 13 added 2026-08-21: **Per-Day Hours Visibility** — closure phase for the v1.2 milestone audit's critical finding (I-1/F-1). Phase 9 made `agent_day_hours` authoritative and Phase 10 populates it, but `DeskAgentService.toResponse` and `DeskAgentExportService` still read the retired `Agent.contractedHoursPerDay` scalar, which the upload path nulls — so the roster shows a desk-default number unrelated to the uploaded Mon–Sun values. Solver unaffected. Also folds I-3 (Edit Hours wipes MANDATORY/PTO) and I-4 (hardcoded specialty headers). See `.planning/v1.2-MILESTONE-AUDIT.md`.
- v1.2 milestone close PAUSED 2026-08-21 pending Phase 13 — audit returned `gaps_found` (19/19 requirements satisfied, but 1 critical cross-phase integration gap and 1 broken E2E flow). Recorded closeout choices for when it resumes: override_closeout noting Phase 12 withdrawal; acknowledge all 3 open todos as deferred.

- Phase 12 added 2026-08-13: **Atomic Shift Move** — custom Timefold move placing a full contracted shift plus its break in one step. Raised during Phase 10 UAT after the live desk proved unable to produce full-hours shifts: single-slot local search cannot cross the HARD `Exactly one break` rule, so agents pin one slot below the break threshold. Two threshold-tuning attempts were reverted (`76a715f`) before concluding a custom move is required.
- 2026-08-13: **Timefold version corrected.** The previously stated pinned version (recorded as a later 1.3x release) was incorrect — Phase 12 verified the actual pinned version is 1.16.0 against `build.gradle:35` (`ai.timefold.solver:timefold-solver-bom:1.16.0`) and against the running solver (custom-move API confirmed to be `AbstractMove.doMoveOnGenuineVariables` with framework-generated undo, not the newer `Neighborhoods` API introduced at 1.31.0). Assumption A3 in `12-RESEARCH.md` is thereby resolved.

### Decisions

Full decision log with outcomes is in `.planning/PROJECT.md` Key Decisions. Carried forward as active design constraints:

- Timefold pinned at 1.16.0 (`build.gradle:35`) — `ScoreAnalysis` moves to paid tier in 2.0; custom moves at this version use `AbstractMove.doMoveOnGenuineVariables` with framework-generated undo, and the `Neighborhoods` custom-move API introduced at 1.31.0 is not available
- PDF export must use OpenPDF 3.0.4 (LGPL/MPL) — iText rejected (AGPL)
- Fairness constraints soft score only — hard fairness makes schedules infeasible
- Quadratic penalties for hours consistency, not linear (avoids score traps)
- Score breakdown guarded to in-memory schedules — `loadSnapshotData()` must be fixed before export work
- Solver respects BambooHR fixed weekends rather than choosing them; fairness constraints may therefore only apply to agents without a parseable field-4517 pattern
- v1.2: BambooHR ID always populated → match by ID only, no fuzzy matching; spreadsheet PTO/day-off columns are a recurring weekly pattern, not dated absences; Mon–Sun contracted hours are the authority on which days are worked; 6-col legacy upload shape retired
- [Phase 10]: D-12: recurring MANDATORY/PTO label stored on agent_day_hours.day_off_type (nullable), not dated AgentDayOff rows — BambooRefreshService deletes/regenerates the entire AgentDayOff rolling window on every sync; agent_day_hours is queried without a date range so it survives untouched
- [Phase 10]: V30 confirmed as the correct next Flyway migration version — V29 verified as latest applied version before creating V30
- [Phase 10]: [Phase 10 Plan 02] specialtyIndex() takes an already-normalized (trim+lowercase) header string per the RESEARCH.md/PATTERNS.md verbatim proposal
- [Phase 10]: [Phase 10 Plan 03] Row validation order settled: BambooHR ID presence -> all 7 day cells valid -> specialty resolution -> BambooHR cache lookup -> non-schedulable check (D-09 whole-row skip-and-continue)
- [Phase 10]: [Phase 10 Plan 03] Identity fields read via EnrichedColumnLayout constants (no generic name/email fallback columns in the new per-desk-sheet shape)
- [Phase 10]: [Phase 10 Plan 04] Sheet names derived from desk.getName() pass through WorkbookUtil.createSafeSheetName to prevent runtime failures on invalid/oversized Excel sheet names
- [Phase 10]: [Phase 10 Plan 05] Colliding-sheet-name last-wins test uses a stateful Mockito registry (Map<bamboohrId,Agent> backing thenAnswer stubs) rather than static thenReturn stubs, to faithfully model clearDesk's unassign-then-reimport round trip across two sheets targeting the same desk
- [Phase 12]: Phase 12 (Atomic Shift Move) closed as **WITHDRAWN** on 2026-08-21 — all 3 plans executed, but the seeded 5×5 benchmark put the move's effect (+0.25h median) inside the baseline's own 5.00h noise spread, and it was inert at realistic 130% over-allocation. Code fully reverted in `299c42c`; planning artifacts retained as the record. Goal explicitly **not** claimed. See `12-VERIFICATION.md`. Do not re-plan as gap closure.
- [Phase 12]: Solver changes must be judged by seeded, step-count-terminated A/B runs (median + full min/max spread), never by a single wall-clock solve — run-to-run variance exceeds the effect size of most changes
- [Phase 12]: At realistic over-allocation the binding constraint is seat capacity / cross-agent displacement, not move granularity — successor work filed at `.planning/todos/pending/2026-08-13-cross-agent-seat-displacement.md`
- [Phase 12]: No test under `src/test/java/com/wfm/solver/` loads the Spring context, so a scoped solver-package run cannot catch a `solverConfig.xml` regression — any change to that file must be validated with the full suite
- [Phase 10]: [Phase 10 Plan 05] D-16 guard (no AgentDayOffRepository dependency) verified via reflection over declared fields rather than Mockito verify(never()), since the collaborator does not exist to verify against
- [Phase 10]: [Phase 10 Plan 06] Frontend TS interfaces matched field-for-field to the already-implemented backend DeskAssignmentUploadResult/SheetSummary/SkippedSheet DTOs (Wave 2 plans 03/04 had already landed); warnings + skippedSheets rendered as a single combined amber notice block per D-11
- [Phase ?]: [Phase 12 Plan 01] Explicit unionMoveSelector re-declares changeMoveSelector/swapMoveSelector alongside AtomicShiftMoveFactory — Timefold 1.16.0 only auto-builds the default change+swap union when localSearch declares no moveSelector at all
- [Phase ?]: [Phase 12 Plan 01] AssignSeatMove implements only doMoveOnGenuineVariables and never overrides createUndoMove — deprecated for removal since 1.16.0; framework auto-generates undo
- [Phase ?]: [Phase 12 Plan 01] ShiftWindowFinder stays a plain Java class with zero Timefold imports, mirroring ScheduleConstraintProvider's HALF_UP vs CEILING rounding modes exactly rather than unifying them
- [Phase ?]: [Phase 12 Plan 02] ShiftWindowFinder.findWindows returns every legal (span start, break offset) pair ordered by span start then break offset ascending, not just the earliest — required for 12-03's seeded benchmark reproducibility
- [Phase ?]: [Phase 12 Plan 02] AtomicShiftMoveFactory.buildSeatMoves rewrites a pinned agent-day atomically: unassign every currently-held seat the target window doesn't reuse, assign every window seat not already held, skipping no-op pairs
- [Phase ?]: [Phase 12 Plan 02] MAX_WINDOWS_PER_AGENT_DAY = 8, down-sampled by fixed stride (ceil(size/8)-th element) rather than truncation, so retained candidates spread across span starts and repeated calls select identically
- [Phase ?]: [Phase 12 Plan 02] Rule 1 fix: AssignSeatMove.getPlanningValues() switched from List.of(toAgent) to Collections.singletonList(toAgent) — List.of rejects null, and unassign moves (toAgent=null) are new in this plan
- [Phase ?]: Operator verdict (12-03): keep the atomic shift move (correct, kept), but the phase's must-pass median-vs-spread threshold FAILED and the phase goal of 'more hours assigned' is NOT claimed as achieved. Cross-agent seat displacement filed as follow-up.
- [Phase ?]: [Phase 11 Plan 01] Both-blank identity-field merge leaves the Agent's previously stored value untouched — callers only apply a merged field when AgentMergeService.hasData(merged) is true, since the literal winner formula returns blank when both sources are blank
- [Phase ?]: [Phase 11 Plan 01] HttpBambooHRClient timeout config (@Value) lives on DelegatingBambooHRClient, the Spring-managed caller, and is passed to HttpBambooHRClient as constructor params, since HttpBambooHRClient is manually instantiated (not a Spring bean)
- [Phase ?]: [Phase 11 Plan 01] AgentMergeService.IDENTITY_FIELD_ORDER documents the fixed six-field merge order (First name, Last name, Email, Department, Job title, Active status) used across the merge engine
- [Phase ?]: [Phase 11 Plan 02] Checkpoint resolved: PTO/pattern arbitration runs at solve time (SolverService.arbitratePtoAgainstBambooWindow/unblockSheetWorkedDays, in-memory, re-derived per solve, no AgentDayOffRepository added to the upload path) — human-selected one-way door, per the mechanism sketch attached to the selection
- [Phase ?]: [Phase 11 Plan 02] SolverService.bambooLookaheadWeeks/bambooLookbackWeeks are @Value field injections (not constructor params), matching BambooRefreshService, so every existing SolverService test stays untouched
- [Phase ?]: [Phase 11 Plan 02] MergeReportTest fixtures set customWorkingdays="Mon-Sun" (Rule 3 fix) so the new mergeWorkingPattern check doesn't spuriously add a gap-fill row against the suite's pre-existing full-week workbook fixture
- [Phase 13]: Phase 13 Plan 01: adopted PLAN.md's P-01/P-02/P-03 planner decisions verbatim (schedule-derived D-06 default via ScheduleRepository.findByTenantIdAndDeskIdOrderByCreatedAtDesc; always-7-key DeskAgentResponse.dayHours DTO shape; effectiveContractedHoursPerDay recomputed as max of the 7 resolved weekday values)
- [Phase 13]: Phase 13 Plan 01: relocated the pre-existing editHoursAgentId/editHours/startEditHours/saveHours inline-edit triad into the new expanded-row scaffold in Task 1 rather than Task 2, to keep npm run build green under noUnusedLocals:true once the collapsed cell no longer referenced it
- [Phase 13]: Phase 13 Plan 02: adopted PLAN.md's P-04/P-05/P-06/P-07 planner decisions verbatim (reject-not-clamp; two distinct endpoints; setDayHours leaves Agent.contractedHoursPerDay untouched; D-07 warning computed client-side)
- [Phase 13]: Phase 13 Plan 02: added explicit agentDayHoursRepository.flush() after setContractedHours' recreate loop to make the read-after-write ordering deterministic rather than relying on Hibernate auto-flush
- [Phase 13]: Phase 13 Plan 03: adopted PLAN.md's P-08/P-09 planner decisions verbatim (unset weekday exports resolved effective value, never blank; 7 day columns inserted immediately after Effective Contracted Hours Per Day, shifting First/Last Name from indices 13/14 to 20/21)
- [Phase 13]: Phase 13 Plan 04: adopted PLAN.md's P-10/P-11 planner decisions verbatim (client-side range-only validation, no quarter-hour gate; no frontend test framework introduced)
- [Phase 13]: Phase 13 Plan 04: bulk-action editHours input opens blank (13-UI-SPEC.md E5) rather than pre-seeded with the agent's current effective hours; per-cell validation error text reuses the existing warning-amber #92400e instead of #ef4444, since the phase's color contract forbids destructive red for any new element
- [Phase 13]: Phase 13 Plan 05: shared isEveryDayNotSet predicate drives both the collapsed cell's muted treatment and the expanded empty-state note (P-14); the bulk out-of-range guard runs strictly before confirm() and uses a truncated toast sentence, not an inline element (P-12/P-13); 13-04-PLAN.md's contradicting seed-value action prose was corrected with an inline 'corrected by 13-05' marker rather than a silent rewrite (P-15)
- [Phase 13]: Phase 13 Plan 06: adopted PLAN.md's P-16/P-17/P-18/P-19 planner decisions verbatim (MockitoSpyBean + Propagation.NOT_SUPPORTED + argument-matched stubbing for the rollback proof, no new dependency; WR-02 proof is a direct handler unit test, not a new MockMvc harness; bulk range message names the field; exactly one new exception type intercepted)
- [Phase 13]: Phase 13 Plan 06: routed DeskAgentServiceBulkRollbackTest's @AfterEach cleanup through DeskAgentService.setContractedHours(..., null) rather than calling the @MockitoSpyBean-wrapped AgentDayHoursRepository.deleteByAgent_Id directly -- the spy's delegate does not carry Spring Data's self-transactional proxy behaviour, so a direct write with no ambient transaction throws TransactionRequiredException

### Blockers/Concerns

- **⚠ SECURITY — Backlog 999.7:** BambooHR API key (prefix `ad2bb…2be`) exposed 2026-06-02 was never rotated. Still present in tracked planning docs in a **public** repo; integration code has deployed to the live environment. Accepted as risk by operator on 2026-07-29 but unresolved.
- **Solver data quality — mitigated by Phase 11 (2026-08-21).** BambooHR field 4517 is ~45% populated / ~24% parseable company-wide. Agents with blank or `Variable` values were excluded from solving via `Agent.workingDaysKnown`. MRG-06 shipped: a spreadsheet-supplied pattern now sets `Agent.workingDaysSource=SPREADSHEET` (V36) and makes the agent solver-eligible, with `BambooRefreshService.shouldDowngradeWorkingDaysKnown` preventing a later refresh from reclaiming it. Residual risk: the field-4517 alias dependency below.
- **⚠ [Phase 11] BambooHR field-4517 alias is a silent single point of failure.** The request asks for field id `4517` but the parser reads back the JSON key `customWorkingdays`. If the tenant has no Field Alias configured, the value is always null in production and MRG-03 window arbitration + MRG-06 gap-fill/replace reporting never activate — while every unit test still passes, because the fixtures hand-construct `BambooEmployee`. Confirmed by operator at UAT 2026-08-21 (test 5); re-check after any BambooHR account change. Origin: code review IN-03.
- **DEFERRED — Backlog 999.1/999.2/999.3:** IAM blocker remains. 9 AWS resources unprovisioned. Resume when root/admin AWS access is available.
- **MDL-02 is the highest-risk item in v1.2** — per-day contracted hours must compose with existing `AgentException` per-date overrides without changing solve behaviour for uniform-hours agents. Sequenced first (Phase 9) to gate parser and merge work.
- **Phase 12 must-pass threshold FAILED — phase goal not achieved.** 12-BENCHMARK.md shows the must-pass median-vs-spread threshold FAILS (with-move median hours assigned exceeds baseline median by only 0.25h against a 5.00h baseline spread). Operator ruling (2026-08-13): keep the atomic shift move as committed code (correct, improves hard score, breaks nothing), but do NOT claim the phase goal — record threshold 1 as FAILED rather than the "more hours assigned" goal achieved. Cross-agent seat displacement filed as follow-up (`.planning/todos/pending/2026-08-13-cross-agent-seat-displacement.md`, `resolves_phase: 12`) since the 130% conservative-variant data shows seat capacity, not move selection, is the binding constraint at realistic over-allocation. Phase 12 should not be marked complete against its original success criteria on this ruling.

## Session Continuity

Last session: 2026-08-24T12:56:27.295Z
Stopped at: Completed 13-06-PLAN.md (gap closure) -- phase 13 all 6 plans executed
Resume file: None

## Operator Next Steps

- `/gsd-plan-phase 9` — Phase 9 has CONTEXT.md; ready to plan
- Close the two remaining verification gaps: Phase 10 UAT (stalled at test 3 since 2026-08-12), Phases 9 and 12 have no VERIFICATION.md

## Performance Metrics

| Plan | Duration | Tasks | Files |
|------|----------|-------|-------|
| Phase 12 P01 | 35m | 2 tasks | 6 files |
| Phase 12 P02 | 55m | 2 tasks | 5 files |
| Phase 12 P03 | 25m (continuation; +55m prior) | 3 tasks | 5 files |
| Phase 11 P01 | 45min | 3 tasks | 23 files |
| Phase 11 P02 | ~40min (continuation) | 4 tasks | 15 files |
| Phase 13 P01 | 22min | 2 tasks | 7 files |
| Phase 13 P02 | 14min | 3 tasks | 6 files |
| Phase 13 P03 | 25min | 2 tasks | 5 files |
| Phase 13 P04 | 8 min | 2 tasks | 2 files |
| Phase 13 P05 | ~10 min | 3 tasks | 2 files |
| Phase 13 P06 | 14min | 3 tasks | 6 files |
