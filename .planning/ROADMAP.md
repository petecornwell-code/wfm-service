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

- [x] **Phase 9: Agent Data Model Foundation** - Per-day contracted hours and first/last name split, migrated without solve-behaviour regression (completed 2026-08-21)
- [x] **Phase 10: Enriched Upload Parsing** - Extended spreadsheet format with unbounded specializations and Mon–Sun hours/days-off/PTO columns, with per-row validation (completed 2026-07-31)
- [x] **Phase 11: BambooHR Merge Engine & Report** - Fresh-sync merge with per-field precedence and an operator-facing merge report (completed 2026-08-21)

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

- [x] 09-03-PLAN.md — SolverService per-day effective-hours resolution, all 3 call sites (MDL-02, MDL-03)
- [x] 09-04-PLAN.md — BambooHR refresh + upload name-split, per-day clear-on-reimport (MDL-01, MDL-02)
- [x] 09-05-PLAN.md — DTO/export first/last exposure + setContractedHours 7-row fan-out (MDL-01, MDL-02)
- [x] 09-06-PLAN.md — V29 Flyway migration + manual data-integrity checkpoint (MDL-03)

### Phase 10: Enriched Upload Parsing

**Goal**: Operators upload one workbook — **one worksheet per desk** (sheet name = desk) — that captures full agent identity, an unbounded number of specializations, and a **single Mon–Sun day-cell group** whose per-cell value encodes status (a number `>= 0` = contracted hours, `MANDATORY` = mandatory day off, `PTO` = recurring PTO; `0`/`MANDATORY`/`PTO` all mean not-worked), with per-row/per-sheet validation, a downloadable pre-seeded template, and both the 6-column legacy shape and the old flat enriched shape retired.
**Depends on**: Phase 9 (the model must support first/last name and per-day hours before the parser can populate them)
**Requirements**: UPL-01, UPL-02, UPL-03, UPL-04, UPL-05, UPL-06, UPL-07, UPL-08, UPL-09
**Design revision (2026-07-31, see `10-CONTEXT.md`)**: the three Mon–Sun column groups collapse into one polymorphic 7-column day group; the day cell (not a separate contracted-hours group) is the authority on which days are worked; old flat enriched sheets are retired too. Phase 10 writes days-off using a coexist/union rule with BambooHR field-4517 blocks — true per-field precedence is Phase 11.
**Success Criteria** (what must be TRUE):

  1. Operator can upload a workbook with one sheet per desk carrying BambooHR ID, first name, last name, job title, email, department, and active status, and every field is parsed and stored (desk comes from the sheet name)
  2. Operator can list any number of specialization columns (`Specialty 1…N`, not just two) and all are parsed and matched against desk specializations
  3. Operator can fill the Mon–Sun day cells; each cell is parsed per agent as hours (`>= 0`, where `0` = day not worked), `MANDATORY` (mandatory day off), or `PTO` (recurring weekly PTO), and a blank cell is invalid
  4. A row that fails validation (blank/invalid day cell, negative hours, unknown BambooHR ID) is skipped with a specific reason; the Upload Results view shows a per-sheet rollup, skip reasons, unmatched-sheet notices, and clamp warnings (>24 → 24); other valid rows in the same file still import
  5. A row whose BambooHR ID is not found is rejected with reason "BambooHR ID not found" rather than creating an agent; uploading a 6-column legacy sheet or an old flat enriched sheet is no longer accepted, and operators use the downloadable pre-seeded per-desk template instead

**Plans**: 6 plans in 3 waves
Plans:

**Wave 1** *(foundation — parallel)*

- [x] 10-01-PLAN.md — V30 day_off_type column + AgentDayHours label + D-12 refresh-wipe regression test (UPL-04, UPL-05)
- [x] 10-02-PLAN.md — EnrichedColumnLayout shared column definition + unit test (UPL-02, UPL-09)

**Wave 2** *(parser + template — parallel, blocked on Wave 1)*

- [x] 10-03-PLAN.md — DeskAssignmentUploadService rewrite: multi-sheet, ID-only match, day-cell parse, retired-shape rejection, multipart limits (UPL-01, UPL-03, UPL-04, UPL-05, UPL-06, UPL-07, UPL-08)
- [x] 10-04-PLAN.md — Pre-seeded per-desk template service + download endpoint + export symmetry + sanitization (UPL-09)

**Wave 3** *(tests + UI — parallel, blocked on Wave 2)*

- [x] 10-05-PLAN.md — Parser behavioral test suite (multi-sheet, specialty, day-cell, validation, ID-reject) (UPL-01, UPL-02, UPL-03, UPL-04, UPL-05, UPL-06, UPL-07)
- [x] 10-06-PLAN.md — Frontend Upload Results rollup/warnings + template download button (UPL-06, UPL-09)

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

**Plans**: 2/2 plans executed in 2 waves
Plans:

**Wave 1** *(tracer — fresh-sync merge path end to end)*

- [x] 11-01-PLAN.md — Fresh-sync-before-merge upload path, per-field identity precedence, merge report, sync-failure abort (MRG-01, MRG-02, MRG-04, MRG-05, MRG-07)

**Wave 2** *(expansion — blocked on Wave 1)*

- [x] 11-02-PLAN.md — Spreadsheet-sourced solver eligibility with refresh downgrade guard, dated-vs-recurring PTO arbitration, working-pattern report row (MRG-03, MRG-06)

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
| 9. Agent Data Model Foundation | v1.2 | 6/6 | Complete    | 2026-08-21 |
| 10. Enriched Upload Parsing | v1.2 | 6/6 | Complete    | 2026-07-31 |
| 11. BambooHR Merge Engine & Report | v1.2 | 2/2 | Complete    | 2026-08-21 |

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

### Phase 12: Atomic Shift Move — WITHDRAWN (goal not achieved, 2026-08-13)

> **Disposition:** All 3 plans executed, but the phase goal is **not** claimed as achieved and the
> implementation was deliberately reverted in `299c42c`. The seeded 5×5 benchmark showed the move's
> effect on hours assigned (+0.25h median) sat inside the baseline's own run-to-run noise (5.00h
> spread), and it was inert at realistic 130% over-allocation because seat capacity — not move
> selection — is the binding constraint. Operator ruling (`12-03-SUMMARY.md`): keep the record,
> withdraw the code. Full report: `12-VERIFICATION.md`. Successor work (cross-agent seat
> displacement) filed at `.planning/todos/pending/2026-08-13-cross-agent-seat-displacement.md`.
>
> **Do not re-plan this phase as gap closure** — the missing artifacts are absent by decision, not
> by incomplete execution.

**Goal:** The solver can place a full contracted shift — contiguous work slots plus one correctly positioned break — as a single move, so rosters where agents work their full contracted hours are actually reachable.
**Requirements**: TBD
**Depends on:** Nothing (solver-internal; independent of Phase 11)

**Why (evidence, 2026-08-13):** `AgentAssignment.agent` is the planning variable, one entity per seat, so local search moves one slot at a time. Every path from a partial shift to a complete one passes through a state that violates the HARD `Exactly one break` rule (wrong gap count or length), so those moves are rejected and shifts cannot grow. Observed on the live Stubhub (EN) desk: agents pinned at exactly 15 slots (3.75h), one slot below the 16-slot break threshold, with no breaks anywhere.

Measured on 2026-08-13, identical settings (400% over-allocation, 15-min increments, 8h contracted, 60-min break, 1h blocked window), four 5-minute runs:

| Run | Hard score | Hours assigned (of 88 needed) |
|-----|-----------|-------------------------------|
| 1   | -4,930    | 80.50 |
| 2   | -29,810   | 30.25 |
| 3   | -29,810   | 35.50 |

Two independent runs converging on the *identical* -29,810 indicates a structural attractor, not noise — the 80.5h run was the outlier. Two attempts at moving the break-enforcement threshold (to `expectedWorkSlots`, and softening under-allocation) only relocated the wall and were reverted; see `76a715f`.

**Known design constraints for planning:**

- Seats are per-timeslot with a required specialization; a shift move must find free, spec-matching seats across a contiguous window
- Timefold requires exactly reversible moves — incorrect undo produces corrupted-score bugs that are hard to diagnose
- Must compose with existing change moves (`unionMoveSelector`), not replace them — fine-grained repair is still needed
- Should honour `breakBlockedHours` and `breakStartAlignment` at generation time so illegal placements are never produced
- Success must be measured across repeated runs, not one solve: run-to-run variance currently exceeds the effect size of most changes

**Plans:** 3/3 plans executed — code subsequently withdrawn (`299c42c`); phase closed as WITHDRAWN, goal not claimed

Plans:
**Wave 1**

- [x] 12-01-PLAN.md — Tracer: one atomic shift move executes end-to-end under FULL_ASSERT, composed with the existing change/swap selectors

**Wave 2** *(blocked on Wave 1 completion)*

- [x] 12-02-PLAN.md — Enumerate every legal window, rewrite pinned agent-days atomically, bound the move pool

**Wave 3** *(blocked on Wave 2 completion)*

- [x] 12-03-PLAN.md — Seeded step-count benchmark (5 baseline vs 5 with-move), recorded evidence and operator sign-off

### Phase 13: Per-Day Hours Visibility

**Goal:** An operator who uploads Mon–Sun contracted hours can see exactly what the system stored — the roster and the Excel export resolve effective hours from the authoritative `agent_day_hours` model rather than the retired `Agent.contractedHoursPerDay` scalar, and per-weekday hours, `MANDATORY` days and `PTO` markers are visible in the UI.
**Requirements**: none new — closes v1.2 audit gaps I-1/F-1 (and I-3, I-4) against existing MDL-02, UPL-03/04/05, UPL-09
**Depends on:** Phases 9 and 10 (the per-day model and the parser that populates it)
**Source:** `.planning/v1.2-MILESTONE-AUDIT.md` (2026-08-21, status `gaps_found`)
**Plans:** 4 plans

**Why (audit evidence, 2026-08-21):** Phase 9 made `agent_day_hours` authoritative and Phase 10's upload writes those rows, but the read path was never migrated. `DeskAgentService.toResponse` (`DeskAgentService.java:72-74`) computes effective hours as `getContractedHoursPerDay() ?: deskDefault` and never touches `AgentDayHoursRepository` — which is injected into the class but used only by the write path at `:210`. Meanwhile `DeskAssignmentUploadService.clearDesk` (`:561`) nulls that scalar on every re-import and never restores it. Net effect: after any enriched upload the roster (`frontend/src/pages/DeskAgents.tsx:362`) and the export (`DeskAgentExportService.java:57-58`) show a flat desk-default number unrelated to what was uploaded, and no component in `frontend/src` surfaces per-day hours, MANDATORY days or PTO markers at all. The solver is unaffected — it resolves correctly through `SolverService.resolveEffectiveHours`. This is a reporting/verification defect, not a scheduling-correctness one.

**Scope (from the audit):**

- **I-1 / F-1 (critical):** `DeskAgentService.toResponse` and `DeskAgentExportService` resolve from the per-day model; roster UI surfaces Mon–Sun values, `MANDATORY` and `PTO`
- **I-3 (medium):** `DeskAgentService.setContractedHours` (`:206-217`) deletes all seven rows and recreates them uniformly with `dayOffType` unset, silently wiping upload-set MANDATORY/PTO — preserve them, or warn the operator
- **I-4 (low):** `DeskAssignmentTemplateService.java:31-32` hardcodes `"Specialty 1"`/`"Specialty 2"`; source them from `EnrichedColumnLayout` (which today exposes only the `specialtyIndex` detection regex)

**Out of scope:** I-2 (the manual "Refresh from BambooHR" button bypassing `AgentMergeService`) is a scoping decision, not a defect — either route it through the merge engine or document that the merge-report guarantee covers the upload path only. Deferred to backlog.

**Known design constraints for planning:**

- `Agent.contractedHoursPerDay` stays a live field by decision (D-05, V29 migration comment) — this phase changes readers, not the schema
- Five writers touch the scalar (`BambooRefreshService:244`, `DeskAgentService:143`/`:198`, `DeskAssignmentUploadService:561`, `DeskService:147`); do not assume it is null
- `SolverService.resolveEffectiveHours` is the existing correct resolution — prefer reusing it over a second implementation

Plans:

- [ ] 13-01-PLAN.md — Tracer: roster resolves per-day hours end-to-end from `agent_day_hours` (wave 1)
- [ ] 13-02-PLAN.md — Per-cell edit endpoint: one weekday, one row (wave 2)
- [ ] 13-03-PLAN.md — Export gains seven Mon–Sun columns; specialty headers sourced from `EnrichedColumnLayout` (wave 2)
- [ ] 13-04-PLAN.md — Per-cell combo UI and warning-guarded "Set all days to…" bulk action (wave 3)

---

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
