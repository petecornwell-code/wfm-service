# WFM Service

## What This Is

A workforce management scheduling service for Helpware — used to build and optimise agent schedules across multiple client desks. Built with Spring Boot + Timefold Solver (constraint-based optimisation) + React SPA. Deployed to AWS (ECS Fargate + RDS + CloudFront). Live at `d2bbtcc80peap7.cloudfront.net`.

Operators configure desks (queues), upload staffing demand (FTE spreadsheets), sync agents from BambooHR, capture preferences and exceptions, then run the solver to produce an optimised weekly schedule.

## Core Value

Scheduling managers can produce optimised, constraint-aware agent schedules in minutes instead of hours — without spreadsheets.

## Current Milestone: v1.1 Schedule Quality & Reporting

**Goal:** Improve schedule quality, give operators visibility into coverage and solver decisions, fix PTO sync issues, and streamline agent desk assignment.

**Target features:**
- Coverage gap visibility — per-timeslot demand vs. coverage
- Shift balance / fairness — solver constraints to prevent unfair patterns
- Solver tuning — speed and quality improvements
- Preference satisfaction — verify/improve how well agent preferences are honoured
- Consistent agent hours — same contracted hours day-to-day and week-to-week
- Coverage report — per-timeslot demand vs. actual coverage
- Agent utilization report — hours per agent, overtime risk, underutilization
- Schedule export improvements — better Excel/PDF output
- Solver score breakdown — why this schedule? which constraints fired?
- PTO sync diagnostic / fix — surface what was imported, fix agents whose PTO isn't syncing
- Agent desk upload — bulk-assign BambooHR agents to desks via spreadsheet (manual UI stays)

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

### Active (v1.1)

- Coverage gap visibility
- Shift balance / fairness constraints
- Solver speed / quality tuning
- Preference satisfaction tracking
- Consistent agent hours (day-to-day and week-to-week)
- Coverage report
- Agent utilization report
- Schedule export improvements
- Solver score breakdown
- PTO sync diagnostic / fix
- Agent desk bulk upload via spreadsheet

### Out of Scope

- API authentication / authorization — deferred, internal use
- Custom domain / DNS — using AWS default CloudFront URL
- Multi-environment staging — single environment only
- Monitoring dashboards / alerting — beyond basic CloudWatch logs
- AWS OIDC GitHub Actions role — blocked by `iam:CreateRole` (PowerUserAccess policy); defer until admin access available

## Context

**Live URL:** `https://d2bbtcc80peap7.cloudfront.net`
**AWS account:** 982940000233, region `eu-west-2`
**Deploy:** Push to `main` → GitHub Actions → Docker build → ECS + S3/CloudFront
**BambooHR:** Credentials stored in DB via Configuration UI (not env vars); `DelegatingBambooHRClient` falls back to mock when unconfigured
**Solver:** Timefold OptaPlanner; constraints include staffing demand, specialization match, PTO/exceptions, contracted hours, bulk overallocation limits
**Multi-tenant:** Tenant ID via JWT; all entities scoped by `tenant_id`
**DB:** RDS PostgreSQL 16, `db.t4g.medium`, single AZ

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
*Last updated: 2026-05-07 — reframed from deployment project to active product; v1.0 shipped*
