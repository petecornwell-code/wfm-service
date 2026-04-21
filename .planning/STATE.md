---
gsd_state_version: 1.0
milestone: v1.0
milestone_name: AWS Deployment
status: archived
last_updated: "2026-04-21T00:00:00.000Z"
last_activity: 2026-04-21
progress:
  total_phases: 4
  completed_phases: 1
  total_plans: 6
  completed_plans: 4
  percent: 67
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-04-21)

**Core value:** Internal users can access the WFM scheduling tool via a stable cloud URL without running it locally
**Current focus:** Planning next milestone — v1.0 AWS Deployment archived; IAM blocker work deferred to backlog

## Current Position

Milestone v1.0 archived on 2026-04-21.
Status: Partially shipped — 38/45 AWS resources provisioned; IAM-blocked items deferred to backlog (999.1, 999.2, 999.3).
Ready to start new milestone with `/gsd-new-milestone`.

## Deferred Items

Items deferred at milestone close on 2026-04-21:

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

### Blockers/Concerns

- **DEFERRED — Backlog 999.1/999.2/999.3:** IAM blocker remains. `pete.cornwell@helpware.com` PowerUserAccess excludes `iam:CreateRole`. 9 AWS resources unprovisioned. Resume when root/admin AWS access is available.

## Session Continuity

Last session: 2026-04-21
Stopped at: v1.0 milestone archived; ready for new milestone
Resume file: None
