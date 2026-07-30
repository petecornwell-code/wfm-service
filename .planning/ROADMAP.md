# Roadmap: WFM Service

## Milestones

- ⚠ **v1.0 AWS Deployment** — Phases 1–4 (partially shipped 2026-04-21; IAM blocker — see Backlog 999.1–999.3)
- ⚠ **v1.1 Schedule Quality & Reporting** — Phases 5–8 (closed early 2026-07-29; 5–6 shipped, 7–8 deferred — see Backlog 999.4–999.6)
- 🚧 **v1.2 Unified Agent Provisioning** — Phases 9–11 (in progress)

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

### 🚧 v1.2 Unified Agent Provisioning (Phases 9–11, in progress)

**Milestone Goal:** One spreadsheet upload fully provisions an agent roster — identity, desk, specializations, working pattern, days off, and PTO — merged field-by-field with BambooHR as source of truth and the spreadsheet filling every gap.

- [ ] **Phase 9: Agent Data Model Foundation** - Per-day contracted hours and first/last name split, migrated without solve-behaviour regression
- [ ] **Phase 10: Enriched Upload Parsing** - Extended spreadsheet format with unbounded specializations and Mon–Sun hours/days-off/PTO columns, with per-row validation
- [ ] **Phase 11: BambooHR Merge Engine & Report** - Fresh-sync merge with per-field precedence and an operator-facing merge report

## Phase Details

### Phase 9: Agent Data Model Foundation
**Goal**: Agent stores first/last name separately and per-day contracted hours, so the solver's `AgentDayConfig.effectiveHours` resolution composes with existing `AgentException` per-date overrides without changing solve behaviour for agents whose hours are uniform across worked days.
**Depends on**: Nothing (first phase of v1.2; builds on v1.1 Phase 6 agent-data foundation)
**Requirements**: MDL-01, MDL-02, MDL-03
**Success Criteria** (what must be TRUE):
  1. Every agent record (existing and new) shows first name and last name as separate fields, not a single combined `name`
  2. Agent stores contracted hours per day of the week (Mon–Sun); `AgentDayConfig` resolves effective hours per date from these per-day values rather than a single scalar
  3. Migrating an existing agent produces no data loss: the prior scalar `contractedHoursPerDay` becomes that agent's per-day value on every day they previously worked, and the single `name` splits cleanly into first/last
  4. The solver produces the same schedule for agents whose contracted hours are uniform across worked days as it did before the migration — no behaviour regression for the common case
**Plans**: 6 plans
Plans:
**Wave 1**
- [x] 09-01-PLAN.md — Name-split utility + Agent first/last name columns (MDL-01)
- [x] 09-02-PLAN.md — agent_day_hours child entity + tenant-scoped repository (MDL-02)

**Wave 2** *(blocked on Wave 1 completion)*
- [ ] 09-03-PLAN.md — SolverService per-day effective-hours resolution, all 3 call sites (MDL-02, MDL-03)
- [ ] 09-04-PLAN.md — BambooHR refresh + upload name-split, per-day clear-on-reimport (MDL-01, MDL-02)
- [ ] 09-05-PLAN.md — DTO/export first/last exposure + setContractedHours 7-row fan-out (MDL-01, MDL-02)
- [ ] 09-06-PLAN.md — V29 Flyway migration + manual data-integrity checkpoint (MDL-03)

### Phase 10: Enriched Upload Parsing
**Goal**: Operators upload one extended spreadsheet — building on the existing 16-column enriched shape — that captures full agent identity, an unbounded number of specializations, and Mon–Sun contracted-hours/mandatory-days-off/recurring-PTO patterns, with per-row validation and the 6-column legacy shape retired.
**Depends on**: Phase 9 (the model must support first/last name and per-day hours before the parser can populate them)
**Requirements**: UPL-01, UPL-02, UPL-03, UPL-04, UPL-05, UPL-06, UPL-07, UPL-08
**Success Criteria** (what must be TRUE):
  1. Operator can upload a spreadsheet carrying BambooHR ID, first name, last name, job title, email, department, desk, and active status, and every field is parsed and stored
  2. Operator can list any number of specialization columns (not just two) and all are parsed and matched against desk specializations
  3. Operator can fill Mon–Sun contracted-hours, mandatory-day-off, and recurring-PTO columns; each is parsed per agent, with `0` or blank on a contracted-hours column correctly read as a day the agent does not work
  4. A row that fails validation on any new column is skipped with a specific reason shown in the existing Upload Results view, while other valid rows in the same file still import
  5. A row whose BambooHR ID is not found is rejected with reason "BambooHR ID not found" rather than creating an agent, and uploading a 6-column legacy sheet is no longer accepted while existing enriched sheets (without the new columns) still import successfully
**Plans**: TBD
**UI hint**: yes

### Phase 11: BambooHR Merge Engine & Report
**Goal**: Every upload runs a fresh BambooHR sync and merges spreadsheet data against it using documented per-field precedence — BambooHR authoritative where populated, spreadsheet filling gaps — and the operator can see exactly which value came from which source.
**Depends on**: Phase 10 (parsed spreadsheet fields are the merge engine's input)
**Requirements**: MRG-01, MRG-02, MRG-03, MRG-04, MRG-05, MRG-06, MRG-07
**Success Criteria** (what must be TRUE):
  1. Uploading a spreadsheet triggers a fresh BambooHR sync before any merge decision is made, so the merge always runs against current BambooHR data
  2. For every field carried by both sources, BambooHR's value is used wherever BambooHR has data; the spreadsheet's value is used only where BambooHR's is absent
  3. BambooHR's dated PTO blocks the dates it covers; the spreadsheet's recurring weekly PTO pattern applies only to dates BambooHR has no record for
  4. Operator sees a merge report after upload, in the Upload Results modal, showing per field whether the value came from BambooHR or the spreadsheet, and which spreadsheet values were overridden by BambooHR
  5. An agent whose working pattern BambooHR doesn't know but the spreadsheet supplies becomes solver-eligible — `workingDaysKnown` resolves true and the agent is no longer filtered out
  6. If the BambooHR sync fails during upload (e.g. 503 rate limit), the operator sees a clear message and no partial merge is written
**Plans**: TBD
**UI hint**: yes

## Progress

**Execution Order:**
Phases execute in numeric order: 9 → 10 → 11

| Phase | Milestone | Plans Complete | Status | Completed |
|-------|-----------|----------------|--------|-----------|
| 1. Local Tooling & State Bootstrap | v1.0 | 2/2 | Complete | 2026-04-03 |
| 2. Security Cleanup & OIDC Setup | v1.0 | 1/2 | Deferred | - |
| 3. Infrastructure Provisioning | v1.0 | 1/2 | Deferred | - |
| 4. CI/CD Pipeline & Go-Live | v1.0 | 0/TBD | Deferred | - |
| 5. Agent Data Enrichment & Desk Upload | v1.1 | 5/5 | Complete | 2026-06-02 |
| 6. Solver Quality Constraints | v1.1 | 3/3 | Complete | 2026-07-29 |
| 7. Coverage, Utilization & Diagnostics | v1.1 | 0/TBD | Deferred → 999.5 | - |
| 8. Export, Score Breakdown & Tuning | v1.1 | 0/TBD | Deferred → 999.6 | - |
| 9. Agent Data Model Foundation | v1.2 | 0/6 | Not started | - |
| 10. Enriched Upload Parsing | v1.2 | 0/TBD | Not started | - |
| 11. BambooHR Merge Engine & Report | v1.2 | 0/TBD | Not started | - |

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
**Constraint:** Timefold pinned at 1.33.0 — `ScoreAnalysis` moves to paid tier in 2.0
**UI hint:** yes
**Plans:**
- [ ] TBD

### Phase 999.7: BambooHR Credential Rotation & Scrub (BACKLOG — SECURITY)

**Goal:** The BambooHR API key exposed on 2026-06-02 is rotated, stored only in a secret manager, and scrubbed from the public repository
**Source:** 06-01-PLAN.md Task 0 — a `blocking-human` gate that was bypassed by operator directive; recorded as a FAILED must-have in `06-VERIFICATION.md` with an accepted override
**Deferred at:** 2026-07-29 during v1.1 milestone close
**Severity:** The old key (prefix `ad2bb…2be`) is still valid and still present in tracked planning docs in a **public** repo (`petecornwell-code/wfm-service`); BambooHR-integration code has already deployed to the sole live environment
**Work:**
- [ ] Rotate the BambooHR API key for the helpware tenant in the BambooHR admin console
- [ ] Store the new key only in AWS Secrets Manager / deploy env config — never in chat or committed files
- [ ] Scrub the value from tracked planning docs (`.planning/codebase/CONCERNS.md`, `02-01-PLAN.md`, `02-RESEARCH.md`, 06-01 planning docs) and confirm `git grep -i ad2bb` is clean
- [ ] Decide whether git history rewrite is warranted given the repo is public
