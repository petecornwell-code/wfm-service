---
gsd_state_version: 1.0
milestone: v1.1
milestone_name: Schedule Quality & Reporting
status: planning
stopped_at: Phase 5 context gathered
last_updated: "2026-05-11T14:37:15.149Z"
last_activity: 2026-05-07 — Milestone v1.1 roadmap created (Phases 5–8)
progress:
  total_phases: 4
  completed_phases: 1
  total_plans: 6
  completed_plans: 4
  percent: 67
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-05-07)

**Core value:** Scheduling managers can produce optimised, constraint-aware agent schedules in minutes instead of hours — without spreadsheets.
**Current focus:** Milestone v1.1 — Schedule Quality & Reporting

## Current Position

Phase: Phase 5 (not started — roadmap complete, awaiting plan)
Plan: —
Status: Roadmap created; ready for phase planning
Last activity: 2026-05-07 — Milestone v1.1 roadmap created (Phases 5–8)

## Phase Summary (v1.1)

| Phase | Name | Requirements | Status |
|-------|------|--------------|--------|
| 5 | Agent Data Enrichment & Desk Upload | DATA-01, DATA-02, DATA-03 | Not started |
| 6 | Solver Quality Constraints | QUAL-01, QUAL-02, QUAL-03 | Not started |
| 7 | Coverage, Utilization & Diagnostics | RPT-01, RPT-02, QUAL-04, DIAG-01, DIAG-02 | Not started |
| 8 | Export, Score Breakdown & Tuning | RPT-03, RPT-04, RPT-05, RPT-06, QUAL-05 | Not started |

## Deferred Items

Items deferred at v1.0 milestone close on 2026-04-21:

| Category | Item | Status |
|----------|------|--------|
| phase | 02-02: OIDC & IAM setup | Deferred → Backlog 999.1 |
| phase | 03-02: Infrastructure verification | Deferred → Backlog 999.2 |
| phase | 04: CI/CD Pipeline & Go-Live | Deferred → Backlog 999.3 |

**Blocker for all deferred items:** `pete.cornwell@helpware.com` has PowerUserAccess which excludes `iam:CreateRole`. Root/admin must grant `WFMTerraformIAMPermissions`. See `.planning/milestones/v1.0-ROADMAP.md` for full resume steps.

## Accumulated Context

### Decisions

- Project init: Keep existing Terraform as-is — infra code is complete and correct
- Project init: OIDC for GitHub Actions — no long-lived AWS credentials in CI
- Project init: Single environment ("dev") — internal use, cost control
- Phase 01: Used existing IAM user pete.cornwell@helpware.com — iam:CreateUser not available
- Phase 01: AWS CLI region set to eu-west-2
- Phase 01: S3 bucket named wfm-terraform-state-521757869980 (original name taken)
- Phase 02: Deleted BambooHR credential files via plain rm — untracked, no commit needed
- Phase 02: SEC-03 pre-satisfied — terraform.tfvars already gitignored
- Phase 03: Fixed RDS engine_version 16.4→16.6 — 16.4 not available in eu-west-2
- Phase 03: Fixed shared_preload_libraries apply_method to pending-reboot
- v1.1 Roadmap: Timefold pinned at 1.33.0 — ScoreAnalysis moves to paid tier in 2.0
- v1.1 Roadmap: PDF export uses OpenPDF 3.0.4 (LGPL/MPL) — iText rejected (AGPL)
- v1.1 Roadmap: Fairness constraints must use soft score only — hard fairness makes schedules infeasible
- v1.1 Roadmap: Quadratic penalties for hours consistency, not linear (avoids score traps)
- v1.1 Roadmap: Score breakdown guarded to in-memory schedules only — loadSnapshotData() missing problem facts for accepted schedules must be fixed in Phase 7 before Phase 8 export

### Blockers/Concerns

- **DEFERRED — Backlog 999.1/999.2/999.3:** IAM blocker remains. `pete.cornwell@helpware.com` PowerUserAccess excludes `iam:CreateRole`. 9 AWS resources unprovisioned. Resume when root/admin AWS access is available.

## Session Continuity

Last session: 2026-05-11T14:37:15.144Z
Stopped at: Phase 5 context gathered
Resume file: .planning/phases/05-agent-data-enrichment-desk-upload/05-CONTEXT.md
