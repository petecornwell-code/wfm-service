---
gsd_state_version: 1.0
milestone: v1.3
milestone_name: Shift-Based Scheduling & Consistency
current_phase: 15
current_phase_name: Shift Envelope, Breaks & Library Generation
status: planning
stopped_at: Phase 15 planned — 8 plans, 5 waves, plan-checker VERIFICATION PASSED
last_updated: "2026-08-26T22:57:35.632Z"
last_activity: 2026-08-26
last_activity_desc: Phase 14 complete, transitioned to Phase 15
state_head: 4b2dcd350140c2bb3e52e08a52f84390a5696469
progress:
  total_phases: 4
  completed_phases: 1
  total_plans: 14
  completed_plans: 6
  percent: 25
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-08-26)

**Core value:** Scheduling managers can produce optimised, constraint-aware agent schedules in minutes instead of hours — without spreadsheets.
**Current focus:** Phase 15 — Shift Envelope, Breaks & Library Generation

## Current Position

Phase: 15 — Shift Envelope, Breaks & Library Generation
Plan: Not started
Status: Ready to plan
Last activity: 2026-08-26 — Phase 14 complete, transitioned to Phase 15

Progress: [██▌░░░░░░░] 25% (1/4 phases — Phase 14 complete, 6/6 plans)

## Milestone v1.3 Roadmap

4 phases derived from 34 requirements (6 SHLB, 5 MODE, 7 ENVL, 6 USHF, 6 CONS, 4 DRFT) plus 5
cross-cutting verification requirements (XCUT-01…05) mapped onto the phases they apply to. Coarse
granularity per config.json. Full detail: `.planning/ROADMAP.md` Phase Details.

| Phase | Name | Requirements | Depends on | Research at plan time |
|-------|------|--------------|------------|------------------------|
| 14 | Shift Library & Scheduling Mode | SHLB-01…06, MODE-01…05 | — | No — mirrors `Specialization`/`minimumStaffing` patterns |
| 15 | Shift Envelope, Breaks & Library Generation | ENVL-01…10, SHLB-07 | Phase 14 | Yes — CH placer XML nesting is MEDIUM confidence |
| 16 | Usual Shift Storage | USHF-01…06 | Phase 14 | No — mirrors `AgentDayHours`/`resolvePreferences` |
| 17 | Consistency Constraint & Drift Reporting | CONS-01…06, DRFT-01…04 | Phase 15, 16 | No — but confirm salvage-material rework scope |

**Two decisions this roadmap treats as settled, not re-opened as phases** (both resolved during
v1.3 research, before roadmap creation):

- **Coupling mechanism** — `SPIKE-COUPLING.md` empirically settled a hard-constraint coupling (Option
  A) over a filtered value range (Option C): Option C compiled and passed `FULL_ASSERT` clean while
  reporting infeasible schedules as `0hard/0soft` optimal on 8/8 seeds. Phase 15 builds Option A.

- **Reverted third attempt** (`7861b83`/`9207ceb`/`9f4a96f`/`6fb78c7`, reverted 2026-08-20) — confirmed
  by git archaeology as speculative off-roadmap work reverted as scope discipline, not a technical
  failure. Treated as candidate salvage material inside Phase 17 (see ROADMAP.md Phase 17 Notes), not
  a standalone investigation phase.

- **Soft-quality plateau** — operator ruling: ship the sound (Option A) model, measure the real gap at
  realistic scale in Phase 15's XCUT-04 benchmark, report as a finding. No custom-move remedy phase
  scoped into v1.3.

## Milestone v1.2 Outcome

Shipped 2026-08-25 with **19/19 requirements** across 5 phases (Phase 12 withdrawn, Phase 13 added
mid-milestone as audit closure). Closed under `override_closeout` — see Deferred Items below.

| Phase | Name | Requirements | Status |
|-------|------|--------------|--------|
| 9 | Agent Data Model Foundation | MDL-01, MDL-02, MDL-03 | **Complete (6/6 plans)** — verified 4/4 must-haves 2026-08-21. No SECURITY.md |
| 10 | Enriched Upload Parsing | UPL-01–UPL-09 | **Complete (6/6 plans)** — verified + UAT 8/8 + SECURITY threats_open:0 2026-08-21 |
| 11 | BambooHR Merge Engine & Report | MRG-01–MRG-07 | **Complete (2/2 plans)** — verified + UAT passed 2026-08-21. ⚠ Guarantees hold on the upload path only (I-2) |
| 12 | Atomic Shift Move | (added mid-milestone, none mapped) | **WITHDRAWN** — goal not achieved, code reverted (`299c42c`); see archived 12-VERIFICATION.md |
| 13 | Per-Day Hours Visibility | (none new — closes audit I-1/I-3/I-4/F-1) | **Complete (6/6 plans)** — verified 54/58 + UAT 8/8 + SECURITY 29/29 threats closed 2026-08-25 |

Archives: `.planning/milestones/v1.2-ROADMAP.md`, `.planning/milestones/v1.2-REQUIREMENTS.md`,
`.planning/milestones/v1.2-MILESTONE-AUDIT.md`, `.planning/milestones/v1.2-phases/`

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

Items acknowledged and deferred at milestone close, most recent first:

| Category | Item | Status | Deferred At | Milestone |
|----------|------|--------|-------------|-----------|
| integration | I-2 — manual "Refresh from BambooHR" bypasses the Phase 11 merge engine; overwrites spreadsheet identity data with no precedence rule and no merge report | Accepted as debt (high severity, open across two audits) | 2026-08-25 | v1.2 |
| requirement | MRG-02 — precedence guarantee holds on the upload path only; violated as literally worded by the Refresh path (root cause I-2) | partial | 2026-08-25 | v1.2 |
| integration | I-3 — bulk "Set all days" still destroys MANDATORY/PTO labels; mitigated by a confirm() warning, not fixed | MITIGATED | 2026-08-25 | v1.2 |
| debt | NEW-1 — legacy contractedHoursPerDay scalar still exported as its own column; can silently disagree with per-day columns after a single-cell edit | Open | 2026-08-25 | v1.2 |
| todos | 2026-07-30-blank-upload-template-one-sheet-per-desk.md | (presence-only) | 2026-08-25 | v1.2 |
| todos | 2026-08-13-cross-agent-seat-displacement.md | (presence-only; likely absorbed by v1.3 Phase 15 — see ROADMAP.md) | 2026-08-25 | v1.2 |
| todos | 2026-08-14-terraform-db-password-drift.md | (presence-only) | 2026-08-25 | v1.2 |
| nyquist | Phases 10 and 13 VALIDATION.md still status: draft — validate-phase never reconciled them | Open | 2026-08-25 | v1.2 |
| security | Phase 9 has no SECURITY.md — /gsd-secure-phase 9 never ran | Open | 2026-08-25 | v1.2 |

Items deferred at v1.1 milestone close on 2026-07-29:

| Category | Item | Status |
|----------|------|--------|
| requirement | QUAL-02 weekend-position fairness | Deferred → Backlog 999.4 |
| requirement | QUAL-03 day-to-day hours consistency | Deferred → Backlog 999.4 |
| phase | 07: Coverage, Utilization & Diagnostics (RPT-01, RPT-02, QUAL-04, DIAG-01, DIAG-02) | Deferred → Backlog 999.5 |
| phase | 08: Export, Score Breakdown & Tuning (RPT-03–06, QUAL-05) | Deferred → Backlog 999.6 |
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

- **2026-08-25: v1.3 ROADMAP.md created.** 34 v1.3 requirements (SHLB, MODE, ENVL, USHF, CONS, DRFT) plus 5 cross-cutting XCUT verification requirements mapped to 4 phases (14–17), continuing numbering from v1.2's Phase 13. Coarse granularity. Phases 3/4 of the original 9-phase research draft (revert investigation, coupling spike) were dropped — both were resolved during research/orchestrator archaeology before roadmap creation, not left as phases. See Milestone v1.3 Roadmap above and `.planning/ROADMAP.md` Phase Details for full success criteria, dependencies, and salvage-material notes.
- Phase 13 added 2026-08-21: **Per-Day Hours Visibility** — closure phase for the v1.2 milestone audit's critical finding (I-1/F-1). Phase 9 made `agent_day_hours` authoritative and Phase 10 populates it, but `DeskAgentService.toResponse` and `DeskAgentExportService` still read the retired `Agent.contractedHoursPerDay` scalar, which the upload path nulls — so the roster shows a desk-default number unrelated to the uploaded Mon–Sun values. Solver unaffected. Also folds I-3 (Edit Hours wipes MANDATORY/PTO) and I-4 (hardcoded specialty headers). See `.planning/v1.2-MILESTONE-AUDIT.md`.
- v1.2 milestone close PAUSED 2026-08-21 pending Phase 13 — audit returned `gaps_found` (19/19 requirements satisfied, but 1 critical cross-phase integration gap and 1 broken E2E flow). Recorded closeout choices for when it resumes: override_closeout noting Phase 12 withdrawal; acknowledge all 3 open todos as deferred.

- Phase 12 added 2026-08-13: **Atomic Shift Move** — custom Timefold move placing a full contracted shift plus its break in one step. Raised during Phase 10 UAT after the live desk proved unable to produce full-hours shifts: single-slot local search cannot cross the HARD `Exactly one break` rule, so agents pin one slot below the break threshold. Two threshold-tuning attempts were reverted (`76a715f`) before concluding a custom move is required.
- 2026-08-13: **Timefold version corrected.** The previously stated pinned version (recorded as a later 1.3x release) was incorrect — Phase 12 verified the actual pinned version is 1.16.0 against `build.gradle:35` (`ai.timefold.solver:timefold-solver-bom:1.16.0`) and against the running solver (custom-move API confirmed to be `AbstractMove.doMoveOnGenuineVariables` with framework-generated undo, not the newer `Neighborhoods` API introduced at 1.31.0). Assumption A3 in `12-RESEARCH.md` is thereby resolved.

### Decisions

Full decision log with outcomes is in `.planning/PROJECT.md` Key Decisions. Carried forward as active design constraints:

- **[v1.3 research]** Coupling mechanism settled empirically by `SPIKE-COUPLING.md`: two independent `@PlanningEntity` classes coupled by a `ConstraintStream` hard constraint (Option A), not a filtered value range (Option C is unsound — reports `0hard/0soft` on schedules with 9–14/24 seats outside their agent's envelope, on 8/8 seeds, undetected by `FULL_ASSERT`). Load-bearing for Phase 15; not to be revisited without new evidence.
- **[v1.3 research]** Next Flyway migration is **V39** (schema head is V38, not V36 as previously recorded — corrected in PROJECT.md 2026-08-25). `V38__add_consistent_start_weight.sql` is applied on dev and inert; Phase 17 should adopt this existing column rather than add a colliding duplicate.
- **[v1.3 research]** The reverted third attempt (`7861b83`/`9207ceb`/`9f4a96f`/`6fb78c7`) is confirmed speculative off-roadmap work reverted as scope discipline, not a technical failure — see ROADMAP.md Phase 17 Notes for what transfers as-is, what transfers with rework, and what needs reformulation (the abandoned spread-based penalty is not to be resurrected; v1.3 uses target-deviation).
- **[Phase 14]** D-08 — the coverage validator has **one implementation, two callers**: the read-only `GET /shift-library/validation` report and the `PUT /scheduling-mode` refusal. The report an operator reads and the refusal that blocks them can never disagree. Verified at UAT: report named 4 uncovered windows, refusal named the same 4 verbatim. Phase 15's SHLB-07 library generation must derive from this same predicate, not a second implementation.
- **[Phase 14]** D-12 — `SHIFT → SLOT` is unconditional with **no confirmation dialog**; `switchSchedulingMode` only validates when the target is `SHIFT`. A dialog was rejected deliberately, citing audit I-3 (a `confirm()` on a non-destructive action trains operators to click through the ones that matter).
- **[Phase 14]** D-06 — contracted-hours mismatch is **advisory on save, blocking only at the mode switch**. Operators build libraries incrementally, so a hard block at save time would make the intermediate states unreachable.
- **[Phase 14]** Shift templates have **no delete endpoint** — retirement is an effective-date range edit (T-14-14), so no request can destroy a row an accepted schedule's snapshot lineage or Phase 15/16 FKs would need. Era identity is `(tenant_id, desk_id, name, effective_from)` unique **plus** a service-level same-name non-overlap check; the unique key alone permits overlapping ranges.
- **[Phase 14]** Shift-template times must align to the desk's timeslot grid (D-02, `ShiftTemplateService.validateGridAlignment`) — a hard 400 on start, end, break-start and break-end. This is a separate rule from the hours advisory and fires first.
- **[v1.3 research]** Operator ruling on the soft-quality plateau: ship the sound Option A model; Phase 15's XCUT-04 benchmark measures the real gap at realistic scale and reports it as a finding. No custom-move remedy phase scoped into v1.3 — Phase 12 already failed once by committing to a remedy before measuring.
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
- [Phase 12]: At realistic over-allocation the binding constraint is seat capacity / cross-agent displacement, not move granularity — successor work filed at `.planning/todos/pending/2026-08-13-cross-agent-seat-displacement.md`, likely absorbed by v1.3 Phase 15 (a shift model displaces a whole agent-day in one move)
- [Phase 12]: No test under `src/test/java/com/wfm/solver/` loads the Spring context, so a scoped solver-package run cannot catch a `solverConfig.xml` regression — any change to that file must be validated with the full suite (carried forward as XCUT-03, gating Phase 15)
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
- [Phase 14]: Phase 14 Plan 01 checkpoint: D-11 non-overlap invariant enforced app-level in ShiftTemplateService (not DB EXCLUDE constraint); unique key (tenant_id, desk_id, name, effective_from)
- [Phase 14]: Phase 14 Plan 01: exported DAY_ORDER/DAY_LABELS from DeskAgents.tsx (Rule 3 fix) so ShiftLibrary.tsx could import rather than re-declare weekday constants
- [Phase 14]: Phase 14 Plan 02: Both reflection derivations (annotation-value set, builder-method count) confirmed 19 constraints and agreed with each other -- ARCHITECTURE.md's stale 18 corroborated as wrong, ROADMAP's 19 confirmed
- [Phase 14]: Phase 14 Plan 02: honourPreferredStartTime and honourPreferredBreakTime tagged OPEN_RESOLVE_IN_PHASE_15 with owner 'Phase 15 -- Shift Envelope & Coupling', per D-15's requirement that an explicit open row with a named owner IS a classification
- [Phase 14]: Phase 14 Plan 02: MODE_GATED left with zero rows by design -- the four break constraints are tagged NEEDS_SHIFT_VARIANT, not MODE_GATED, since actual gating is Phase 15's ENVL-05 work
- [Phase 14]: [Phase 14]: Phase 14 Plan 03: full ShiftTemplateService validation (name/time/break/weekday/effective-range), D-02 grid alignment via TimeslotGeneratorService.getLiveBounds, and D-11 identity+non-overlap invariants implemented behind one shared validate(...) path used by both create and updateShiftTemplate (T-14-11)
- [Phase 14]: [Phase 14]: Phase 14 Plan 03: updateShiftTemplate is the sole edit/retire mechanism (P-11) -- setting effectiveTo retires a template; no delete or retire method exists on ShiftTemplateService or ShiftTemplateController
- [Phase 14]: [Phase 14]: Phase 14 Plan 03: eraStatus (CURRENT/UPCOMING/PAST) computed server-side in ShiftTemplateController.toResponse (P-13); list order is name-ascending then effectiveFrom-descending, applied in ShiftTemplateService.listShiftTemplates (P-14)
- [Phase 14]: Phase 14 Plan 04: ShiftLibraryValidationService is one computation (D-08) shared by validate() (non-throwing report) and requireShiftModeReady() (refusal) — structural single-template envelope coverage over live demand only (D-04/D-05), D-02 grid re-check via ShiftTemplateService.isAligned, exact-equality D-06/D-07 hours match via BigDecimals.normalize+compareTo with no tolerance, fatal case only when a demanded weekday has no workable (template, agent) pair at all. StaffingRequirementRepository.findAllLiveByDesk added (P-16) since open-ended template effective ranges cannot be expressed by the existing date-ranged query.
- [Phase 14]: Phase 14 Plan 05: P-21 adopted verbatim — D-13's 409 is ConflictException reading InMemoryScheduleStore.getByDeskId, not a new RefreshInProgressException/ConcurrentHashMap pair; zero new exception class, zero new map, zero new handler mapping
- [Phase 14]: Phase 14 Plan 05: P-22 adopted verbatim — the RUNNING-solve 409 guard applies symmetrically to both switch directions, while the coverage gate (requireShiftModeReady) applies only SLOT-to-SHIFT; D-12's 'ungated' refers to the coverage validation only, not the in-flight guard
- [Phase 14]: Phase 14 Plan 05: switchSchedulingMode writes exactly one column (desk.scheduling_mode); MODE-04 proven field-by-field across a SLOT-SHIFT-SLOT round trip against an ACCEPTED Schedule plus its snapshot Timeslot/StaffingRequirement rows and untouched live rows
- [Phase 14]: [Phase 14] Phase 14 Plan 06: era legibility split across two cells (Current badge in Name cell, Upcoming/Past muted text beside Effective range dates); mode-switch 400 refusal updates Coverage panel directly from err.details rather than a second GET; SHLB-06's second toast reuses the existing 'warning' toast type

### Blockers/Concerns

- BambooHR credential rotation was removed from GSD tracking on 2026-08-25 at operator request; ownership sits with the operator outside this planning system.
- **[v1.3] Soft-quality plateau, measure not remedy.** `SPIKE-COUPLING.md` found the sound Option A coupling never reaches the known `0soft` optimum on its toy fixture (settles `-10soft`/`-5soft`). Operator ruling: Phase 15's XCUT-04 benchmark must measure this at realistic scale and report it honestly — no custom-move remedy is scoped into v1.3. If the real-scale gap turns out to matter, that is a future milestone's evidence-led decision, not an assumption to inherit.
- **[v1.3] Migration numbering.** V39 is now applied (Phase 14) — next migration is **V40**. Confirm the actual latest-applied version before each phase's migration, per the project's own recorded discipline (V30 was confirmed against V29 the same way at Phase 10).
- **⚠ [Phase 14] No test executes the real Flyway migrations.** `src/test/resources/application-test.yml` sets `flyway.enabled: false` with `ddl-auto: create-drop` against H2, so the test schema is built from the entities and migration SQL never runs. V39 shipped declaring `valid_weekdays CHAR(7)` against an entity mapped to `varchar(7)`: the migration applied cleanly and the app then failed to boot under `ddl-auto=validate`, with all 402 tests green. Fixed in place (`9a98029`, UAT gap G-14-1) but the blind spot is unchanged — future migration-vs-entity drift surfaces at first startup, not in CI. Wants a Testcontainers-backed boot test.
- **[Phase 14] Pre-existing defect, unrelated to Phase 14.** `GET /api/v1/agents` returns 500 (`function lower(bytea) does not exist`). Observed during Phase 14 UAT on the deployed dev environment; not a Phase 14 file and not tracked as a Phase 14 gap. The desk-scoped `GET /desks/{id}/agents` path is unaffected.
- **Solver data quality — mitigated by Phase 11 (2026-08-21).** BambooHR field 4517 is ~45% populated / ~24% parseable company-wide. Agents with blank or `Variable` values were excluded from solving via `Agent.workingDaysKnown`. MRG-06 shipped: a spreadsheet-supplied pattern now sets `Agent.workingDaysSource=SPREADSHEET` (V36) and makes the agent solver-eligible, with `BambooRefreshService.shouldDowngradeWorkingDaysKnown` preventing a later refresh from reclaiming it. Residual risk: the field-4517 alias dependency below.
- **⚠ [Phase 11] BambooHR field-4517 alias is a silent single point of failure.** The request asks for field id `4517` but the parser reads back the JSON key `customWorkingdays`. If the tenant has no Field Alias configured, the value is always null in production and MRG-03 window arbitration + MRG-06 gap-fill/replace reporting never activate — while every unit test still passes, because the fixtures hand-construct `BambooEmployee`. Confirmed by operator at UAT 2026-08-21 (test 5); re-check after any BambooHR account change. Origin: code review IN-03.
- **DEFERRED — Backlog 999.1/999.2/999.3:** IAM blocker remains. 9 AWS resources unprovisioned. Resume when root/admin AWS access is available.
- **Phase 12 must-pass threshold FAILED — phase goal not achieved.** 12-BENCHMARK.md shows the must-pass median-vs-spread threshold FAILS (with-move median hours assigned exceeds baseline median by only 0.25h against a 5.00h baseline spread). Operator ruling (2026-08-13): keep the atomic shift move as committed code (correct, improves hard score, breaks nothing), but do NOT claim the phase goal — record threshold 1 as FAILED rather than the "more hours assigned" goal achieved. Cross-agent seat displacement filed as follow-up (`.planning/todos/pending/2026-08-13-cross-agent-seat-displacement.md`, `resolves_phase: 12`) since the 130% conservative-variant data shows seat capacity, not move selection, is the binding constraint at realistic over-allocation. Phase 12 should not be marked complete against its original success criteria on this ruling.

## Session Continuity

Last session: 2026-08-26T22:57:35.270Z
Stopped at: Phase 15 planned — 8 plans, 5 waves, plan-checker VERIFICATION PASSED
Resume file: .planning/phases/15-shift-envelope-breaks-library-generation/15-01-PLAN.md

## Operator Next Steps

- Discuss Phase 15 with `/gsd-discuss-phase 15` — no CONTEXT.md exists yet
- Consider a Testcontainers-backed migration boot test — Phase 14's G-14-1 showed the suite cannot catch migration-vs-entity drift

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
| Phase 14 P01 | 23min | 2 tasks | 14 files |
| Phase 14 P02 | 25min | 2 tasks | 3 files |
| Phase 14 P03 | 24min | 2 tasks | 5 files |
| Phase 14 P04 | 30min | 2 tasks | 5 files |
| Phase 14 P05 | 22min | 2 tasks | 5 files |
| Phase 14 P06 | 25min | 3 tasks | 3 files |
