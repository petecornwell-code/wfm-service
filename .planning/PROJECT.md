# WFM Service

## What This Is

A workforce management scheduling service for Helpware — used to build and optimise agent schedules across multiple client desks. Built with Spring Boot + Timefold Solver (constraint-based optimisation) + React SPA. Deployed to AWS (ECS Fargate + RDS + CloudFront). Live at `d2bbtcc80peap7.cloudfront.net`.

Operators configure desks (queues), upload staffing demand (FTE spreadsheets), sync agents from BambooHR, capture preferences and exceptions, then run the solver to produce an optimised weekly schedule.

## Core Value

Scheduling managers can produce optimised, constraint-aware agent schedules in minutes instead of hours — without spreadsheets.

## Current State

**Shipped:** v1.1 Schedule Quality & Reporting — closed 2026-07-29 with 4 of 16 requirements delivered (Phases 5–6 of 8). The remaining 12 requirements are preserved in `.planning/ROADMAP.md` Backlog 999.4–999.6.

v1.1 delivered the **agent-data foundation**: BambooHR now supplies employment type, job title, and — the significant one — each agent's fixed weekly working-days pattern (field 4517), which the solver honours as hard MANDATORY day-off blocks. It did **not** deliver the reporting, diagnostics, export, or solver-tuning surfaces that were the milestone's other half.

## Current Milestone: v1.2 Unified Agent Provisioning

**Goal:** One spreadsheet upload fully provisions an agent roster — identity, desk, specializations, working pattern, days off, and PTO — merged field-by-field with BambooHR as source of truth and the spreadsheet filling every gap.

**Target features:**
- Enriched upload workbook — **one worksheet per desk** (sheet name = desk) — carrying: BambooHR ID, first name, last name, job title, email, department, active, unbounded `Specialty 1…N` columns, and a **single Mon–Sun day-cell group** whose per-cell value encodes status (a number `>= 0` = contracted hours, `MANDATORY` = mandatory day off, `PTO` = recurring PTO)
- Downloadable **pre-seeded template** — one sheet per desk, current roster identity filled, schedule cells blank; template + parser + export share one column-layout definition
- Per-field merge engine — BambooHR authoritative where it has data, spreadsheet fills gaps (Phase 11)
- Merge report surfaced in the upload result — which fields BambooHR overrode, which the spreadsheet supplied (Phase 11)
- Per-day contracted hours model, replacing the single `contractedHoursPerDay` scalar (0 hours = day off)
- Agent name split into first name / last name
- Unbounded specialization column parsing (the `@ManyToMany` model already supports it; only the parser is hard-coded to `specialty 1`/`specialty 2`)
- Retire **both** the 6-col legacy upload shape **and** the old flat enriched shape

**Design decisions taken at milestone start:**
- BambooHR ID is always populated in the spreadsheet → every row matches by ID; no fuzzy name/email matching required
- Spreadsheet PTO expresses a **recurring weekly pattern** (Mon–Sun), applied across the horizon like mandatory days off — not dated absences
- BambooHR's dated PTO wins for dates it covers; the spreadsheet's recurring PTO pattern applies only to dates BambooHR has no record for (the two are not directly comparable values, so "BambooHR wins" needed this refinement)
- ~~Mon–Sun contracted hours are the single authority on which days are worked: 0 or blank = day off. Mandatory-days-off columns act as a cross-check~~ — **superseded 2026-07-31 (see below)**
- ~~New columns extend the existing 16-col enriched shape rather than adding a third format~~ — **superseded 2026-07-31 (see below)**

**Design decisions revised at Phase 10 discussion (2026-07-31, see `10-CONTEXT.md`):**
- The three Mon–Sun column groups (contracted-hours / mandatory-day-off / recurring-PTO, ~21 columns) collapse into **one polymorphic 7-column day group**. The **day cell is the authority** on which days are worked: a number `>= 0` = hours (`0` = day not worked), `MANDATORY` = mandatory day off, `PTO` = recurring PTO. All of `0`/`MANDATORY`/`PTO` mean "not schedulable that day"; every cell is required (**blank is invalid**). Keywords case-insensitive.
- The workbook has **one worksheet per desk** (sheet name = desk); there is no per-row Desk column. Desk comes from the sheet name.
- The upload shape is **redefined**, not extended — both the 6-col legacy and the old flat enriched shape are retired; operators re-download the pre-seeded template once.
- **Phase 10 boundary:** the parser writes days-off using a **coexist/union** rule with BambooHR field-4517 blocks (a day is off if either source says so). True per-field precedence and un-blocking arrive with the Phase 11 merge engine.
- Numeric hours accepted 0–24; values > 24 clamped to 24 with a non-silent warning; the Upload Results view gains a per-sheet rollup plus skip/clamp/unmatched-sheet notices.

**Why this matters beyond data entry:** field 4517 is only ~24% parseable, and agents whose pattern cannot be parsed are excluded from solving via `workingDaysKnown`. Spreadsheet-supplied Mon–Sun days off fills that gap directly. The eligible agent pool could grow several-fold, which is a plausible root cause of the solver failing to find solutions on live desks.

**Next milestone:** v1.1 backlog (999.4–999.7) remains deferred and untouched.

<details>
<summary>v1.1 target features (original scope, for reference)</summary>

- ✓ Agent desk upload — bulk-assign BambooHR agents to desks via spreadsheet (manual UI stays)
- ✓ PTO sync fix — MANDATORY day-offs sourced from BambooHR; APPROVED-only PTO blocking
- ✗ Coverage gap visibility / coverage report — per-timeslot demand vs. coverage
- ✗ Shift balance / fairness — solver constraints to prevent unfair patterns
- ✗ Solver tuning — speed and quality improvements
- ✗ Preference satisfaction — verify/improve how well agent preferences are honoured
- ✗ Consistent agent hours — day-to-day and week-to-week
- ✗ Agent utilization report — hours per agent, overtime risk, underutilization
- ✗ Schedule export improvements — better Excel/PDF output
- ✗ Solver score breakdown — why this schedule? which constraints fired?
- ✗ PTO sync diagnostic UI — surface what was imported and what failed

</details>

## Requirements

### Validated

- ✓ AWS infrastructure provisioned (VPC, ECR, RDS, ALB, CloudFront, S3) — v1.0
- ✓ CI/CD pipeline: GitHub Actions deploys backend to ECS, frontend to S3/CloudFront — v1.0
- ✓ BambooHR integration: agent sync, PTO import, employee cache — v1.0
- ✓ Desk management: create desks, define specializations, set contracted hours — v1.0
- ✓ Agent assignment: BambooHR sync, spreadsheet upload, manual UI assignment — v1.0
- ✓ FTE upload: staffing requirements from Excel (flexible sheet names, start-time headers) — v1.0
- ✓ Preferences & exceptions: agent shift preferences and one-off exceptions — v1.0
- ✓ Solver: Timefold-based schedule optimisation with specialization, PTO, contracted hours constraints — v1.0
- ✓ Schedule output: accept/reject flow, export — v1.0
- ✓ CORS configured for CloudFront deployment — v1.0
- ✓ Secondary specialization optional (not required for solver eligibility) — v1.0
- ✓ BambooHR employment type (full-time/part-time) synced onto Agent, filterable in UI — v1.1 (DATA-02)
- ✓ BambooHR job title synced; non-schedulable job titles excluded from solver and desk allocation — v1.1 (DATA-03)
- ✓ Desk bulk assignment via spreadsheet upload with per-row failure reporting — v1.1 (DATA-01)
- ✓ BambooHR fixed weekly working-days (field 4517) imported as recurring MANDATORY day-off blocks, honoured as hard solver constraints — v1.1 (QUAL-01)
- ✓ Data-gap agents (blank/`Variable` working days) excluded from solving rather than mis-scheduled — v1.1
- ✓ PTO correctness: only APPROVED PTO blocks; REQUESTED is visible-only — v1.1
- ✓ BambooHR 503/429 rate limits surface a human-readable retry message — v1.1

### Active (carried to next milestone — see ROADMAP.md Backlog)

- Weekend-position fairness across agents (QUAL-02) → 999.4
- Day-to-day hours consistency (QUAL-03) → 999.4
- Per-timeslot coverage report (RPT-01) → 999.5
- Agent utilization report (RPT-02) → 999.5
- Preference satisfaction rate after solve (QUAL-04) → 999.5
- PTO sync diagnostic UI (DIAG-01) → 999.5
- Week-over-week hours variance (DIAG-02) → 999.5
- Excel and PDF schedule export (RPT-03, RPT-04) → 999.6
- Solver score breakdown + export (RPT-05, RPT-06) → 999.6
- Solver constraint weight / time limit tuning UI (QUAL-05) → 999.6
- **Operator-facing surface for data-gap and outlier agents** — currently CloudWatch logs only
- **⚠ BambooHR API key rotation and public-repo scrub** → 999.7 (security, unresolved)

### Out of Scope

- API authentication / authorization — deferred, internal use
- Custom domain / DNS — using AWS default CloudFront URL
- Multi-environment staging — single environment only
- Monitoring dashboards / alerting — beyond basic CloudWatch logs
- AWS OIDC GitHub Actions role — blocked by `iam:CreateRole` (PowerUserAccess policy); defer until admin access available

## Context

**Live URL:** `https://d2bbtcc80peap7.cloudfront.net`
**AWS account:** 982940000233, region `eu-west-2`
**Deploy:** Push to `main` **or `claude/create-system-specification-451ge`** → GitHub Actions (`.github/workflows/deploy.yml`) → Docker build → ECS + S3/CloudFront. Auth is OIDC role assumption (`wfm-service-dev-github-actions`) — there are no long-lived AWS credentials, so deploys run only from CI, not from a developer machine. ⚠ The second trigger means pushing that working branch deploys straight to the live environment with no review gate.
**BambooHR:** Credentials stored in DB via Configuration UI (not env vars); `DelegatingBambooHRClient` falls back to mock when unconfigured
**Solver:** Timefold OptaPlanner; constraints include staffing demand, specialization match, PTO/exceptions, contracted hours, bulk overallocation limits
**Multi-tenant:** Tenant ID via JWT; all entities scoped by `tenant_id`
**DB:** RDS PostgreSQL 16, `db.t4g.medium`, single AZ (schema at V28 after v1.1)
**Agent eligibility for solving:** four filters — active status, desk assignment, schedulable job title, and `workingDaysKnown` (parseable BambooHR field 4517)

**Known issues after v1.1:**
- **⚠ Security:** the BambooHR API key exposed 2026-06-02 (`ad2bb…2be`) was never rotated and is still present in tracked planning docs in this **public** repo. Integration code has since deployed to the live environment. Tracked as Backlog 999.7.
- **BambooHR field 4517 is sparsely populated** — ~45% company-wide, ~24% parseable. Unparseable agents are silently excluded from solving; the exclusion proportion on live desks was never measured. This may be why the solver struggles to find solutions on real desks.
- Data-gap and outlier agents are surfaced only as CloudWatch `log.warn` lines — no operator UI.

## Constraints

- **Region:** `eu-west-2` (London)
- **Runtime:** ECS Fargate, 2 vCPU / 4 GB
- **Database:** RDS PostgreSQL 16, `db.t4g.medium`
- **Frontend:** React SPA served from S3 + CloudFront
- **Solver time limit:** Configurable; default short for interactive use

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Single environment ("dev") | Internal use, cost control | ✓ Good |
| AWS default CloudFront URL | Simplest path, no DNS needed | ✓ Good |
| BambooHR config in DB not env vars | Runtime-configurable without redeploy | ✓ Good |
| Mock BambooHR fallback | Dev/test without real credentials | ✓ Good |
| Secondary specialization optional | Real agents often have only primary skill | ✓ Good |
| OIDC for GitHub Actions (deferred) | Blocked by IAM permissions; using token-auth workaround | ⚠ Deferred |
| S3 bucket wfm-terraform-state-521757869980 | Original name taken; account-ID suffix is best practice | ✓ Good |
| Solver *respects* BambooHR fixed weekends rather than *choosing* 2 contiguous days off | Employees have fixed weekly patterns in BambooHR field 4517; choosing would override real contracts | ✓ Good |
| Pull working days from BambooHR API (field 4517), not the desk-upload spreadsheet | Automated sync, no manual upload dependency — though the spreadsheet's Mon–Sun columns carry the same data as a proven fallback | ✓ Good |
| `Agent.working_days_known` DEFAULT TRUE kept permanently | Avoids retro-flagging pre-existing agents as data gaps on migration | ✓ Good |
| Timefold pinned at 1.33.0 | `ScoreAnalysis` moves to paid tier in 2.0 | ✓ Good |
| PDF export via OpenPDF 3.0.4 | LGPL/MPL licensed; iText rejected as AGPL | — Pending (unbuilt) |
| Fairness soft-score only; quadratic hours-consistency penalties | Hard fairness makes schedules infeasible; linear penalties create score traps | — Pending (unbuilt) |
| Phase 6 narrowed to QUAL-01 only | Data foundation had to land before fairness/consistency constraints | ⚠ Revisit — QUAL-02/03 were never re-homed and nearly lost |
| BambooHR key rotation gate bypassed | Operator directive 2026-07-29, accepted risk | ⚠ Revisit — still unresolved, key is public |

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition** (via `/gsd-transition`):
1. Requirements invalidated? → Move to Out of Scope with reason
2. Requirements validated? → Move to Validated with phase reference
3. New requirements emerged? → Add to Active
4. Decisions to log? → Add to Key Decisions
5. "What This Is" still accurate? → Update if drifted

**After each milestone** (via `/gsd-complete-milestone`):
1. Full review of all sections
2. Core Value check — still the right priority?
3. Audit Out of Scope — reasons still valid?
4. Update Context with current state

---
*Last updated: 2026-07-29 after v1.1 milestone close (4/16 requirements shipped; 12 carried to Backlog 999.4–999.6)*
