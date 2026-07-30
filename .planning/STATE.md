---
gsd_state_version: 1.0
milestone: v1.2
milestone_name: Unified Agent Provisioning
status: planning
stopped_at: Phase 9 context gathered
last_updated: "2026-07-30T12:24:58.582Z"
last_activity: 2026-07-29 — ROADMAP.md and REQUIREMENTS.md traceability written for v1.2
progress:
  total_phases: 10
  completed_phases: 0
  total_plans: 0
  completed_plans: 0
  percent: 0
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-07-29)

**Core value:** Scheduling managers can produce optimised, constraint-aware agent schedules in minutes instead of hours — without spreadsheets.
**Current focus:** Planning Phase 9 (Agent Data Model Foundation)

## Current Position

Phase: 9 of 11 (Agent Data Model Foundation) — 1st of 3 phases in v1.2
Plan: — (not yet planned)
Status: Roadmap approved, ready to plan Phase 9
Last activity: 2026-07-29 — ROADMAP.md and REQUIREMENTS.md traceability written for v1.2

Progress: [░░░░░░░░░░] 0%

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

### Decisions

Full decision log with outcomes is in `.planning/PROJECT.md` Key Decisions. Carried forward as active design constraints:

- Timefold pinned at 1.33.0 — `ScoreAnalysis` moves to paid tier in 2.0
- PDF export must use OpenPDF 3.0.4 (LGPL/MPL) — iText rejected (AGPL)
- Fairness constraints soft score only — hard fairness makes schedules infeasible
- Quadratic penalties for hours consistency, not linear (avoids score traps)
- Score breakdown guarded to in-memory schedules — `loadSnapshotData()` must be fixed before export work
- Solver respects BambooHR fixed weekends rather than choosing them; fairness constraints may therefore only apply to agents without a parseable field-4517 pattern
- v1.2: BambooHR ID always populated → match by ID only, no fuzzy matching; spreadsheet PTO/day-off columns are a recurring weekly pattern, not dated absences; Mon–Sun contracted hours are the authority on which days are worked; 6-col legacy upload shape retired

### Blockers/Concerns

- **⚠ SECURITY — Backlog 999.7:** BambooHR API key (prefix `ad2bb…2be`) exposed 2026-06-02 was never rotated. Still present in tracked planning docs in a **public** repo; integration code has deployed to the live environment. Accepted as risk by operator on 2026-07-29 but unresolved.
- **Solver data quality:** BambooHR field 4517 is ~45% populated / ~24% parseable company-wide. Agents with blank or `Variable` values are excluded from solving via `Agent.workingDaysKnown`. v1.2 Phase 11 (MRG-06) directly targets this by letting spreadsheet-supplied patterns fill the gap.
- **DEFERRED — Backlog 999.1/999.2/999.3:** IAM blocker remains. 9 AWS resources unprovisioned. Resume when root/admin AWS access is available.
- **MDL-02 is the highest-risk item in v1.2** — per-day contracted hours must compose with existing `AgentException` per-date overrides without changing solve behaviour for uniform-hours agents. Sequenced first (Phase 9) to gate parser and merge work.

## Session Continuity

Last session: 2026-07-30T12:24:58.578Z
Stopped at: Phase 9 context gathered
Resume file: .planning/phases/09-agent-data-model-foundation/09-CONTEXT.md

## Operator Next Steps

- Review and approve the v1.2 roadmap, then run `/gsd-plan-phase 9`
