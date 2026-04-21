# Roadmap: WFM Service — AWS Deployment

## Milestones

- ⚠ **v1.0 AWS Deployment** — Phases 1–4 (partially shipped 2026-04-21; IAM blocker — see Backlog)

## Phases

<details>
<summary>⚠ v1.0 AWS Deployment (Phases 1–4) — ARCHIVED 2026-04-21</summary>

- [x] Phase 1: Local Tooling & State Bootstrap (2/2 plans) — complete
- [~] Phase 2: Security Cleanup & OIDC Setup (1/2 plans) — 02-02 deferred → 999.1
- [~] Phase 3: Infrastructure Provisioning (1/2 plans) — 03-02 deferred → 999.2
- [ ] Phase 4: CI/CD Pipeline & Go-Live (0/TBD) — deferred → 999.3

Full details: `.planning/milestones/v1.0-ROADMAP.md`

</details>

## Progress

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Local Tooling & State Bootstrap | 2/2 | Complete | 2026-04-03 |
| 2. Security Cleanup & OIDC Setup | 1/2 | Deferred | - |
| 3. Infrastructure Provisioning | 1/2 | Deferred | - |
| 4. CI/CD Pipeline & Go-Live | 0/TBD | Deferred | - |

## Backlog

### Phase 999.1: Resume Phase 2 — OIDC & IAM Setup (BACKLOG)

**Goal:** Complete 02-02-PLAN.md — fix iam.tf bugs, create terraform.tfvars, apply IAM resources, capture role ARN
**Source phase:** 02 (Security Cleanup & OIDC Setup)
**Deferred at:** 2026-04-21 during v1.0 milestone archive — blocked on iam:CreateRole (PowerUserAccess excludes IAM)
**Blocker:** Requires root/admin AWS access to grant `WFMTerraformIAMPermissions` to `pete.cornwell@helpware.com`
**Plans:**
- [ ] 02-02: Fix iam.tf, terraform apply IAM resources, capture github-actions role ARN

### Phase 999.2: Resume Phase 3 — Infrastructure Verification (BACKLOG)

**Goal:** Complete 03-02-PLAN.md — verify RDS/ECS security groups, Secrets Manager injection, Flyway readiness; capture terraform outputs
**Source phase:** 03 (Infrastructure Provisioning)
**Deferred at:** 2026-04-21 during v1.0 milestone archive — IAM roles not yet provisioned (9 resources pending)
**Blocker:** Depends on 999.1 (IAM roles required before ECS task definition and service can be created)
**Plans:**
- [ ] 03-02: Verify infrastructure, capture outputs for CI/CD phase

### Phase 999.3: Phase 4 — CI/CD Pipeline & Go-Live (BACKLOG)

**Goal:** GitHub secret set, pipeline triggered, application live and verified at CloudFront URL
**Source phase:** 04 (CI/CD Pipeline & Go-Live)
**Deferred at:** 2026-04-21 during v1.0 milestone archive — Phase 3 incomplete
**Blocker:** Depends on 999.1 and 999.2
**Plans:**
- [ ] TBD — plan this phase once infrastructure is fully provisioned
