---
gsd_state_version: 1.0
milestone: v1.2
milestone_name: Unified Agent Provisioning
current_phase: 12
current_phase_name: atomic-shift-move
status: executing
stopped_at: Paused at 12-03 Task 3 operator checkpoint — benchmark evidence recorded in 12-BENCHMARK.md, awaiting sign-off
last_updated: "2026-08-13T15:11:43.206Z"
last_activity: 2026-08-13
last_activity_desc: Phase 12 execution started
progress:
  total_phases: 4
  completed_phases: 2
  total_plans: 15
  completed_plans: 14
  percent: 50
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-07-29)

**Core value:** Scheduling managers can produce optimised, constraint-aware agent schedules in minutes instead of hours — without spreadsheets.
**Current focus:** Phase 12 — atomic-shift-move

## Current Position

Phase: 12 (atomic-shift-move) — EXECUTING
Plan: 3 of 3
Status: Ready to execute
Last activity: 2026-08-13 — Phase 12 execution started

Progress: [█████████░] 93%

## Milestone v1.2 Roadmap

18/18 requirements mapped across 3 phases. See `.planning/ROADMAP.md` for full detail.

| Phase | Name | Requirements | Status |
|-------|------|--------------|--------|
| 9 | Agent Data Model Foundation | MDL-01, MDL-02, MDL-03 | Not started |
| 10 | Enriched Upload Parsing | UPL-01–UPL-08 | Not started |
| 11 | BambooHR Merge Engine & Report | MRG-01–MRG-07 | Not started |

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
- [Phase 10]: [Phase 10 Plan 05] D-16 guard (no AgentDayOffRepository dependency) verified via reflection over declared fields rather than Mockito verify(never()), since the collaborator does not exist to verify against
- [Phase 10]: [Phase 10 Plan 06] Frontend TS interfaces matched field-for-field to the already-implemented backend DeskAssignmentUploadResult/SheetSummary/SkippedSheet DTOs (Wave 2 plans 03/04 had already landed); warnings + skippedSheets rendered as a single combined amber notice block per D-11
- [Phase ?]: [Phase 12 Plan 01] Explicit unionMoveSelector re-declares changeMoveSelector/swapMoveSelector alongside AtomicShiftMoveFactory — Timefold 1.16.0 only auto-builds the default change+swap union when localSearch declares no moveSelector at all
- [Phase ?]: [Phase 12 Plan 01] AssignSeatMove implements only doMoveOnGenuineVariables and never overrides createUndoMove — deprecated for removal since 1.16.0; framework auto-generates undo
- [Phase ?]: [Phase 12 Plan 01] ShiftWindowFinder stays a plain Java class with zero Timefold imports, mirroring ScheduleConstraintProvider's HALF_UP vs CEILING rounding modes exactly rather than unifying them
- [Phase ?]: [Phase 12 Plan 02] ShiftWindowFinder.findWindows returns every legal (span start, break offset) pair ordered by span start then break offset ascending, not just the earliest — required for 12-03's seeded benchmark reproducibility
- [Phase ?]: [Phase 12 Plan 02] AtomicShiftMoveFactory.buildSeatMoves rewrites a pinned agent-day atomically: unassign every currently-held seat the target window doesn't reuse, assign every window seat not already held, skipping no-op pairs
- [Phase ?]: [Phase 12 Plan 02] MAX_WINDOWS_PER_AGENT_DAY = 8, down-sampled by fixed stride (ceil(size/8)-th element) rather than truncation, so retained candidates spread across span starts and repeated calls select identically
- [Phase ?]: [Phase 12 Plan 02] Rule 1 fix: AssignSeatMove.getPlanningValues() switched from List.of(toAgent) to Collections.singletonList(toAgent) — List.of rejects null, and unassign moves (toAgent=null) are new in this plan

### Blockers/Concerns

- **⚠ SECURITY — Backlog 999.7:** BambooHR API key (prefix `ad2bb…2be`) exposed 2026-06-02 was never rotated. Still present in tracked planning docs in a **public** repo; integration code has deployed to the live environment. Accepted as risk by operator on 2026-07-29 but unresolved.
- **Solver data quality:** BambooHR field 4517 is ~45% populated / ~24% parseable company-wide. Agents with blank or `Variable` values are excluded from solving via `Agent.workingDaysKnown`. v1.2 Phase 11 (MRG-06) directly targets this by letting spreadsheet-supplied patterns fill the gap.
- **DEFERRED — Backlog 999.1/999.2/999.3:** IAM blocker remains. 9 AWS resources unprovisioned. Resume when root/admin AWS access is available.
- **MDL-02 is the highest-risk item in v1.2** — per-day contracted hours must compose with existing `AgentException` per-date overrides without changing solve behaviour for uniform-hours agents. Sequenced first (Phase 9) to gate parser and merge work.
- Phase 12 plan 03 (atomic shift move) paused at Task 3 operator checkpoint: 12-BENCHMARK.md shows the must-pass median-vs-spread threshold FAILS (with-move median hours assigned exceeds baseline median by only 0.25h against a 5.00h baseline spread). Awaiting operator verdict — see checkpoint in execution transcript.

## Session Continuity

Last session: 2026-08-13T15:11:43.199Z
Stopped at: Paused at 12-03 Task 3 operator checkpoint — benchmark evidence recorded in 12-BENCHMARK.md, awaiting sign-off
Resume file: .planning/phases/12-atomic-shift-move/12-03-PLAN.md

## Operator Next Steps

- Review and approve the v1.2 roadmap, then run `/gsd-plan-phase 9`

## Performance Metrics

| Plan | Duration | Tasks | Files |
|------|----------|-------|-------|
| Phase 12 P01 | 35m | 2 tasks | 6 files |
| Phase 12 P02 | 55m | 2 tasks | 5 files |
