# Roadmap: WFM Service — AWS Deployment

## Milestones

- ⚠ **v1.0 AWS Deployment** — Phases 1–4 (partially shipped 2026-04-21; IAM blocker — see Backlog)
- [ ] **v1.1 Schedule Quality & Reporting** — Phases 5–8

## Phases

<details>
<summary>⚠ v1.0 AWS Deployment (Phases 1–4) — ARCHIVED 2026-04-21</summary>

- [x] Phase 1: Local Tooling & State Bootstrap (2/2 plans) — complete
- [~] Phase 2: Security Cleanup & OIDC Setup (1/2 plans) — 02-02 deferred → 999.1
- [~] Phase 3: Infrastructure Provisioning (1/2 plans) — 03-02 deferred → 999.2
- [ ] Phase 4: CI/CD Pipeline & Go-Live (0/TBD) — deferred → 999.3

Full details: `.planning/milestones/v1.0-ROADMAP.md`

</details>

<details open>
<summary>v1.1 Schedule Quality & Reporting (Phases 5–8)</summary>

- [ ] **Phase 5: Agent Data Enrichment & Desk Upload** - BambooHR employment type + job title sync, non-schedulable exclusion, desk bulk upload frontend, PTO sync bug fixes
- [ ] **Phase 6: Solver Quality Constraints** - Contiguous days-off, fairness distribution, day-to-day hours consistency constraints
- [ ] **Phase 7: Coverage, Utilization & Diagnostics** - Coverage report, agent utilization report, preference satisfaction metric, PTO diagnostic, week-over-week hours variance
- [ ] **Phase 8: Export, Score Breakdown & Tuning** - Excel/PDF export improvements, solver score breakdown UI, constraint weight tuning UI

</details>

## Progress

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Local Tooling & State Bootstrap | 2/2 | Complete | 2026-04-03 |
| 2. Security Cleanup & OIDC Setup | 1/2 | Deferred | - |
| 3. Infrastructure Provisioning | 1/2 | Deferred | - |
| 4. CI/CD Pipeline & Go-Live | 0/TBD | Deferred | - |
| 5. Agent Data Enrichment & Desk Upload | 0/5 | Planned | - |
| 6. Solver Quality Constraints | 0/TBD | Not started | - |
| 7. Coverage, Utilization & Diagnostics | 0/TBD | Not started | - |
| 8. Export, Score Breakdown & Tuning | 0/TBD | Not started | - |

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

---

## v1.1 Schedule Quality & Reporting — Phase Details

<!-- Keep the version token in the heading above. The SDK resolves the active milestone
     by matching the milestone version against a markdown heading; if it is absent here,
     roadmap.analyze and gsd-stats under-report zero phases because the phase detail
     sections fall outside the current-milestone slice. -->

### Phase 5: Agent Data Enrichment & Desk Upload
**Goal**: Operators have richer agent data from BambooHR and can bulk-assign agents to desks via spreadsheet; PTO sync is reliable
**Depends on**: Nothing (first v1.1 phase — all v1.0 infrastructure in place)
**Requirements**: DATA-01, DATA-02, DATA-03
**Success Criteria** (what must be TRUE):
  1. After a BambooHR sync, each agent displays their employment type (full-time / part-time) and operators can filter the agent list by employment type
  2. After a BambooHR sync, each agent displays their job title; an operator can mark specific job titles as non-schedulable and those agents are excluded from subsequent solve runs and desk allocation
  3. Operator can upload an Excel spreadsheet to bulk-assign agents to desks; the UI shows which rows succeeded and which failed (with reasons), and existing manual per-agent assignment still works
  4. BambooHR 503 rate-limit responses surface a human-readable "retry in 60 seconds" message instead of a generic server error
  5. Only "approved" PTO creates hard day-off blocks; "requested" PTO is visible in the diagnostic view but does not block scheduling
**Plans**: 5 plans
Plans:
- [x] 05-01-PLAN.md — Schema + model foundation: V25/V26/V27 migrations, EmploymentType enum, JobTitleConfig + BambooSyncEvent entities & repositories, Agent.employmentType, AgentEligibilityService, BambooHRRateLimitedException + 503 handler
- [x] 05-02-PLAN.md — BambooHR integration: employmentHistoryStatus pull, 503/429 onStatus translation, mapping rule, JobTitleConfig auto-populate, BambooSyncEvent recording, /api/v1/job-titles and /api/v1/configuration/bamboohr/sync-status endpoints
- [x] 05-03-PLAN.md — Solver fixes: PTO filter (APPROVED-only, MANDATORY-always) + non-schedulable agent eligibility filter in SolverService
- [x] 05-04-PLAN.md — Upload service + DTO enrichment: header-based shape detection (6-col legacy + 16-col enriched), structured SkippedRow, non-schedulable rejection on both upload and manual assign paths, DeskAgentResponse adds employmentType + pendingPto (bulk-fetched)
- [ ] 05-05-PLAN.md — Frontend UI: api/client.ts types + endpoints, DeskAgents Emp Type column + filter + PTO badge, Configuration Non-Schedulable Job Titles section + Sync Status card, ClientManagement Upload Results modal

### Phase 6: Solver Quality Constraints
**Goal**: Solver enforces fair, predictable shift patterns — every agent gets contiguous days off, desirable positions rotate fairly, and daily hours stay consistent
**Depends on**: Phase 5 (DATA-03 non-schedulable exclusion must be in place before solving)
**Requirements**: QUAL-01, QUAL-02, QUAL-03
**Success Criteria** (what must be TRUE):
  1. Every agent in a generated schedule has exactly 2 contiguous days off per week; no agent has split or isolated off-days
  2. Weekend-position distribution (e.g. Sat/Sun off, Fri/Sat off) visibly rotates across agents over successive solves — no single agent always receives the most desirable or least desirable pattern
  3. Each agent's daily scheduled hours match their contracted daily pattern within the week; erratic day-to-day variation is penalised and no longer appears in typical schedules
  4. New fairness constraints use soft score only; accepted schedules remain feasible (0 hard violations) after the constraints are deployed
**Plans**: TBD

### Phase 7: Coverage, Utilization & Diagnostics
**Goal**: Operators can see exactly where the schedule is thin, which agents are over- or under-utilised, whether preferences were honoured, and why PTO may not have synced
**Depends on**: Phase 5 (agent data), Phase 6 (solver constraints stabilised before reporting on them)
**Requirements**: RPT-01, RPT-02, QUAL-04, DIAG-01, DIAG-02
**Success Criteria** (what must be TRUE):
  1. Operator can view a per-timeslot coverage table on the schedule results page showing demand FTEs, assigned agent count, gap, and coverage percentage — colour-coded red/amber/green; timeslots with missing demand data are marked "No data" not "0% gap"
  2. Operator can view an agent utilization table showing weekly hours per agent, contracted hours, delta, and an overtime-risk flag; agents at or above contracted + 5% are highlighted
  3. After a solve completes, the schedule results page shows a preference satisfaction rate (% of shift preferences honoured) without requiring any export
  4. Operator can view a PTO sync status panel showing which agents had PTO imported (with date counts and approved/requested status) and which failed to sync with the specific reason (e.g. bamboohrId not matched in WFM)
  5. Operator can view a week-over-week hours variance table per agent to identify inconsistent scheduling patterns across accepted schedule history
**Plans**: TBD
**UI hint**: yes

### Phase 8: Export, Score Breakdown & Tuning
**Goal**: Operators can export rich, publication-ready schedules, understand why the solver made its decisions, and adjust solver behaviour from the UI
**Depends on**: Phase 7 (coverage and utilization data methods must exist before export tabs can use them; score breakdown requires loadSnapshotData() fix from Phase 7)
**Requirements**: RPT-03, RPT-04, RPT-05, RPT-06, QUAL-05
**Success Criteria** (what must be TRUE):
  1. Operator can export the published schedule to Excel (.xlsx); the file includes Coverage and Utilization tabs with colour-coded cells; date/time cells sort correctly in Excel
  2. Operator can export the published schedule to PDF; the file contains the schedule in a readable tabular layout
  3. After a solve, operator can view a solver score breakdown panel showing every constraint that fired, its violation count, and its score impact; stub/inactive constraints (breakClustering, bulkUnderallocationSoft) are labelled "Inactive" not shown as active violations
  4. Score breakdown is only available for in-memory (just-solved) schedules; accessing it for an accepted DB-loaded schedule shows a clear "Score breakdown only available for recent solves" message rather than empty data or a 500 error
  5. Operator can export the solver score breakdown to Excel (.xlsx)
  6. Operator can adjust solver constraint weights and time limit from the UI; changes take effect on the next solve without a code deployment; the UI labels the time limit as "Local Search time limit" with a tooltip explaining construction happens before it
**Plans**: TBD
**UI hint**: yes
