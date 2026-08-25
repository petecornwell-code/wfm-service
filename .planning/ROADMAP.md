# Roadmap: WFM Service

## Milestones

- ⚠ **v1.0 AWS Deployment** — Phases 1–4 (partially shipped 2026-04-21; IAM blocker — see Backlog 999.1–999.3)
- ⚠ **v1.1 Schedule Quality & Reporting** — Phases 5–8 (closed early 2026-07-29; 5–6 shipped, 7–8 deferred — see Backlog 999.4–999.6)
- ✅ **v1.2 Unified Agent Provisioning** — Phases 9–13 (shipped 2026-08-25; override closeout — see Known Gaps → Backlog 999.9)

## Phases

<details>
<summary>⚠ v1.0 AWS Deployment (Phases 1–4) — ARCHIVED 2026-04-21</summary>

- [x] Phase 1: Local Tooling & State Bootstrap (2/2 plans) — complete
- [~] Phase 2: Security Cleanup & OIDC Setup (1/2 plans) — 02-02 deferred → 999.1
- [~] Phase 3: Infrastructure Provisioning (1/2 plans) — 03-02 deferred → 999.2
- [ ] Phase 4: CI/CD Pipeline & Go-Live (0/TBD) — deferred → 999.3

Full details: `.planning/milestones/v1.0-ROADMAP.md`

</details>

<details>
<summary>⚠ v1.1 Schedule Quality & Reporting (Phases 5–8) — CLOSED 2026-07-29 (4/16 requirements)</summary>

- [x] Phase 5: Agent Data Enrichment & Desk Upload (5/5 plans) — completed 2026-06-02 — DATA-01, DATA-02, DATA-03
- [x] Phase 6: Solver Quality Constraints — PTO & Weekends (3/3 plans) — completed 2026-07-29 — QUAL-01 only
- [ ] Phase 7: Coverage, Utilization & Diagnostics (0/TBD) — never planned → 999.5
- [ ] Phase 8: Export, Score Breakdown & Tuning (0/TBD) — never planned → 999.6

Also deferred: QUAL-02 and QUAL-03 were dropped from Phase 6 during discussion and never re-homed → 999.4

Full details: `.planning/milestones/v1.1-ROADMAP.md`

</details>

<details>
<summary>✅ v1.2 Unified Agent Provisioning (Phases 9–13) — SHIPPED 2026-08-25 (19/19 requirements)</summary>

**Milestone Goal:** One spreadsheet upload fully provisions an agent roster — identity, desk,
specializations, working pattern, days off, and PTO — merged field-by-field with BambooHR as source
of truth and the spreadsheet filling every gap.

- [x] Phase 9: Agent Data Model Foundation (6/6 plans) — completed 2026-08-21 — MDL-01, MDL-02, MDL-03
- [x] Phase 10: Enriched Upload Parsing (6/6 plans) — completed 2026-07-31 — UPL-01…UPL-09
- [x] Phase 11: BambooHR Merge Engine & Report (2/2 plans) — completed 2026-08-21 — MRG-01…MRG-07
- [~] Phase 12: Atomic Shift Move (3/3 plans) — **WITHDRAWN** 2026-08-13, code reverted `299c42c`, goal not claimed
- [x] Phase 13: Per-Day Hours Visibility (6/6 plans) — completed 2026-08-25 — closure phase for audit findings I-1/I-3/I-4/F-1

**Closed under override** (2026-08-25). Known gaps carried forward to **Backlog 999.9**: I-2 (high —
manual "Refresh from BambooHR" bypasses the merge engine), MRG-02 (partial — precedence holds on the
upload path only), I-3 (mitigated — bulk hours edit still destroys MANDATORY/PTO labels, now behind
a warning), NEW-1 (legacy scalar column can disagree with the per-day columns).

Full details: `.planning/milestones/v1.2-ROADMAP.md` · Audit: `.planning/milestones/v1.2-MILESTONE-AUDIT.md`

</details>

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

### Phase 999.4: Solver Fairness & Hours Consistency (BACKLOG)

**Goal:** Solver distributes desirable weekend positions fairly and keeps each agent's daily hours consistent with their contracted pattern
**Source phase:** 06 (Solver Quality Constraints) — dropped during phase discussion, never re-homed
**Deferred at:** 2026-07-29 during v1.1 milestone close
**Requirements:** QUAL-02 (weekend-position fairness), QUAL-03 (day-to-day hours consistency)
**Design constraints carried forward:**

- Fairness constraints must be **soft score only** — hard fairness makes schedules infeasible
- Use **quadratic** penalties for hours consistency, not linear (avoids score traps)
- Interacts with QUAL-01: agents with a fixed BambooHR pattern have their weekend *determined*, so fairness may only apply to agents without a parseable field-4517 value

**Plans:**

- [ ] TBD

### Phase 999.5: Coverage, Utilization & Diagnostics (BACKLOG)

**Goal:** Operators can see where the schedule is thin, which agents are over- or under-utilised, whether preferences were honoured, and why PTO may not have synced
**Source phase:** 07 — planned in the v1.1 roadmap but never planned in detail or executed
**Deferred at:** 2026-07-29 during v1.1 milestone close
**Requirements:** RPT-01, RPT-02, QUAL-04, DIAG-01, DIAG-02
**Success criteria carried forward:**

1. Per-timeslot coverage table (demand FTEs, assigned count, gap, coverage %) colour-coded red/amber/green; missing demand data marked "No data", not "0% gap"
2. Agent utilization table (weekly hours, contracted hours, delta, overtime-risk flag); agents at/above contracted +5% highlighted
3. Preference satisfaction rate (% honoured) shown after a solve without requiring an export
4. PTO sync status panel showing which agents imported (date counts, approved/requested) and which failed, with reason
5. Week-over-week hours variance table per agent across accepted schedule history

**Also required here (carried from Phase 6 debt):**

- Operator-facing UI for data-gap and outlier agents — currently CloudWatch `log.warn` only
- Fix `loadSnapshotData()` missing problem facts for accepted schedules — **blocks 999.6 score breakdown**

**UI hint:** yes
**Plans:**

- [ ] TBD

### Phase 999.6: Export, Score Breakdown & Tuning (BACKLOG)

**Goal:** Operators can export publication-ready schedules, understand solver decisions, and adjust solver behaviour from the UI
**Source phase:** 08 — planned in the v1.1 roadmap but never planned in detail or executed
**Deferred at:** 2026-07-29 during v1.1 milestone close
**Requirements:** RPT-03, RPT-04, RPT-05, RPT-06, QUAL-05
**Blocker:** Depends on 999.5 (`loadSnapshotData()` fix required before score breakdown; coverage/utilization data methods required before export tabs)
**Success criteria carried forward:**

1. Excel (.xlsx) export with Coverage and Utilization tabs, colour-coded cells, correctly sorting date/time cells
2. PDF export in readable tabular layout (OpenPDF 3.0.4 — LGPL/MPL; iText rejected as AGPL)
3. Score breakdown panel: every constraint that fired, violation count, score impact; stub constraints (`breakClustering`, `bulkUnderallocationSoft`) labelled "Inactive"
4. Score breakdown guarded to in-memory solves; DB-loaded accepted schedules show a clear message, not empty data or a 500
5. Score breakdown exportable to Excel
6. Constraint weights and time limit adjustable from the UI without redeploy; time limit labelled "Local Search time limit" with a tooltip

**Constraint:** Timefold pinned at **1.16.0** (corrected 2026-08-13 against `build.gradle:35`; the previously recorded 1.33.0 was wrong) — `ScoreAnalysis` moves to paid tier in 2.0
**UI hint:** yes
**Plans:**

- [ ] TBD

### Phase 999.7: BambooHR Credential Rotation & Scrub (BACKLOG — SECURITY)

**Goal:** The BambooHR API key exposed on 2026-06-02 is rotated, stored only in a secret manager, and scrubbed from the public repository
**Source:** 06-01-PLAN.md Task 0 — a `blocking-human` gate that was bypassed by operator directive; recorded as a FAILED must-have in `06-VERIFICATION.md` with an accepted override
**Deferred at:** 2026-07-29 during v1.1 milestone close; re-confirmed unresolved at v1.2 close 2026-08-25
**Severity:** The old key (prefix `ad2bb…2be`) is still valid and still present in tracked planning docs in a **public** repo (`petecornwell-code/wfm-service`); BambooHR-integration code has already deployed to the sole live environment
**Work:**

- [ ] Rotate the BambooHR API key for the helpware tenant in the BambooHR admin console
- [ ] Store the new key only in AWS Secrets Manager / deploy env config — never in chat or committed files
- [ ] Scrub the value from tracked planning docs (`.planning/codebase/CONCERNS.md`, `02-01-PLAN.md`, `02-RESEARCH.md`, 06-01 planning docs) and confirm `git grep -i ad2bb` is clean
- [ ] Decide whether git history rewrite is warranted given the repo is public

### Phase 999.8: Decommission Orphaned v1.0 Infrastructure (BACKLOG — COST)

**Goal:** The v1.0 AWS resources in the abandoned account are audited and either destroyed or knowingly retained, so nothing bills silently
**Source:** Discovered 2026-08-10 while reconciling stale endpoints in planning docs during Phase 10 UAT
**Detail:** v1.0 was provisioned in AWS account **521757869980** (`03-01-SUMMARY.md`); the live environment is now account **982940000233** (`infra/main.tf`, `.github/workflows/deploy.yml`). Endpoints from the old account still resolve:

- `d3f4cgjy3bqy.cloudfront.net` — live CloudFront distribution, S3 origin returns `AccessDenied`
- `wfm-service-dev-1135113453.eu-west-2.elb.amazonaws.com` — live ALB (`18.171.68.68`), returns 503, zero healthy targets

An idle ALB plus NAT gateway plus an RDS instance in that account would be the material cost; RDS/NAT status was **not** verified — only the two public endpoints above were probed from outside.
**Work:**

- [ ] Confirm whether account 521757869980 is still open and billing, and who owns it
- [ ] Inventory surviving v1.0 resources there (RDS `wfm-service-dev`, NAT gateway, ALB, CloudFront, ECR, Secrets Manager)
- [ ] Confirm no data in the old RDS instance is still needed before destroying
- [ ] `terraform destroy` against the old state (`wfm-terraform-state-521757869980`) or delete manually if state is unrecoverable

### Phase 999.9: Close v1.2 Integration Gap I-2 (BACKLOG — carried from v1.2 close)

**Goal:** The merge-precedence guarantee holds on every write path, not just the upload path
**Severity:** high — recorded in two consecutive milestone audits (2026-08-21, 2026-08-25) and never scoped into a phase
**Source:** v1.2 milestone audit finding I-2; accepted as debt at milestone close 2026-08-25

**Why.** The manual "Refresh from BambooHR" button (`DeskAgents.tsx:448` → `POST /desks/{id}/agents/refresh`
→ `BambooRefreshService.persistRefreshData:224-234`) overwrites `name`, `firstName`, `lastName`,
`email`, `department`, `jobTitle` and `active` straight from BambooHR with no precedence rule and
emits no `MergeReportEntry`. `grep` confirms zero references to `AgentMergeService` in that file. It
is a normal, expected operator action that silently discards the guarantees MRG-02, MRG-04 and
MRG-05 describe — the same failure shape as I-1 (a requirement verified `passed` in its own phase
while a second reachable entry point violates it).

**Three options recorded at close:**

1. Route the manual refresh through `AgentMergeService` — the real fix; makes MRG-02 true on every path
2. Constrain the button to fields BambooHR owns outright, leaving spreadsheet-sourced data alone — cheaper, removes the silent-overwrite risk without building report plumbing
3. Scope MRG-02 to the upload path explicitly and label the button — no code change, but converts an undiscovered limitation into a stated product decision

**Also fold in** (same area, recorded at v1.2 close):

- [ ] **I-3 residual** — `DeskAgentService.setContractedHours:236-279` still calls `deleteByAgent_Id` then recreates seven rows with `dayOffType` unset, destroying MANDATORY/PTO labels. Preserve labels across the fan-out, or retire the bulk action now that the safe per-cell `setDayHours` path exists
- [ ] **NEW-1** — stop exporting the legacy `contractedHoursPerDay` scalar as its own column, or keep it in sync from `setDayHours`; today they can silently disagree after any single-cell edit
- [ ] **Nyquist coverage** — `/gsd-validate-phase 10` and `/gsd-validate-phase 13` (both `VALIDATION.md` still `status: draft`)
- [ ] **Phase 9 security** — `/gsd-secure-phase 9` has never run; no `09-SECURITY.md` exists

**Plans:**

- [ ] TBD

Full analysis: `.planning/milestones/v1.2-MILESTONE-AUDIT.md`
