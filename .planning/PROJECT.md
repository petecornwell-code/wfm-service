# WFM Service

## What This Is

A workforce management scheduling service for Helpware — used to build and optimise agent schedules across multiple client desks. Built with Spring Boot + Timefold Solver (constraint-based optimisation) + React SPA. Deployed to AWS (ECS Fargate + RDS + CloudFront). Live at `d2bbtcc80peap7.cloudfront.net`.

Operators configure desks (queues), upload staffing demand (FTE spreadsheets), sync agents from BambooHR, capture preferences and exceptions, then run the solver to produce an optimised weekly schedule.

## Core Value

Scheduling managers can produce optimised, constraint-aware agent schedules in minutes instead of hours — without spreadsheets.

## Current State

**Shipped:** v1.2 Unified Agent Provisioning — closed 2026-08-25 with **19 of 19 requirements**
delivered across Phases 9–13 (Phase 12 withdrawn). Archived to
`.planning/milestones/v1.2-ROADMAP.md`.

v1.2 made **Mon–Sun contracted hours a first-class data model** and carried it all the way to the
operator's eyes. An operator downloads a per-desk template, fills seven day cells per agent (a
number, `MANDATORY`, or `PTO`), uploads it; the system syncs BambooHR, merges by explicit
precedence, reports what came from where — and then shows the stored result back in the roster and
the Excel export, resolved from the authoritative `agent_day_hours` model rather than the retired
`Agent.contractedHoursPerDay` scalar.

That last clause is why Phase 13 exists. The first milestone audit (2026-08-21) found v1.2 had
built the storage and the parser but never migrated the *display*, so an operator verifying their
own upload saw a flat desk default unrelated to what they submitted. Phase 13 closed it, along with
the hardcoded specialty headers and the silent MANDATORY/PTO destruction on hours edits.

**Closed under override, with known gaps.** The re-audit still returned `gaps_found`: the manual
"Refresh from BambooHR" button bypasses the Phase 11 merge engine entirely (I-2, high) and has now
survived two audits untouched. Carried to Backlog 999.9. See
`.planning/milestones/v1.2-MILESTONE-AUDIT.md`.

**Phase 12 (Atomic Shift Move) was withdrawn, not shipped** — the seeded benchmark put its effect
inside the baseline's own noise, code reverted in `299c42c`, goal explicitly not claimed.

<details>
<summary>Previous state: v1.1 Schedule Quality & Reporting (closed 2026-07-29)</summary>

Closed with 4 of 16 requirements delivered (Phases 5–6 of 8). The remaining 12 requirements are
preserved in `.planning/ROADMAP.md` Backlog 999.4–999.6.

v1.1 delivered the **agent-data foundation**: BambooHR supplies employment type, job title, and —
the significant one — each agent's fixed weekly working-days pattern (field 4517), which the solver
honours as hard MANDATORY day-off blocks. It did **not** deliver the reporting, diagnostics, export,
or solver-tuning surfaces that were the milestone's other half.

</details>

## Current Milestone: v1.3 Shift-Based Scheduling & Consistency

**Goal:** An agent works a recognisable, repeating shift — not a slot pattern the optimiser
reassembles from scratch every week.

**Target features:**

- **Desk shift library** — each desk defines its allowed shifts (e.g. `08:00–17:00, 8h + 1h break`).
  The solver picks one per agent-day from that library instead of composing a day out of ~36
  independent slot decisions.
- **Shift as an availability envelope** — a new planning unit fixing *when* an agent is present and
  where their break falls. Seat/specialization assignment stays per-slot *inside* the envelope, so an
  agent can still change specialization mid-day.
- **Stored usual shift per agent, per weekday** — the target the solver aims at. Ana can be `S1`
  Mon–Thu and `S2` Fri, matching the existing per-weekday `agent_day_hours` model.
- **Two population paths** — a Usual Shift column in the per-desk upload template *and* inline
  editing in the roster UI, mirroring exactly what v1.2 built for contracted hours.
- **Consistency constraint** — soft penalty on distance from the agent's usual shift, with an
  operator-configurable tolerance band and weight per desk.
- **Per-desk mode switch** — a desk is either shift-scheduled or slot-scheduled. Pilot on one desk
  without touching the rest; keeps a fallback if it underperforms live.
- **Contiguity by construction** — fragmented days become impossible on shift-scheduled desks,
  rather than being penalised after the fact.
- **Drift report** — a panel naming which agents broke their usual shift, when, and by how much.

**Why this shape.** Two prior attempts to get shift semantics onto the slot model were abandoned:
`BreakAwareConstructionPhase` is a documented no-op (a 6-pass pre-assignment pipeline removed for
losing quality at each backtrack-free step), and Phase 12's Atomic Shift Move was withdrawn and
reverted (`299c42c`) at +0.25h median against a 5.00h noise spread. Phase 12's own conclusion — that
seat capacity and cross-agent displacement bind at realistic over-allocation, not move granularity —
is what a shift model addresses natively. This milestone is the third option, and the one those two
were reaching for.

**Central architectural question for research.** Because specialization can vary within a shift, the
shift does **not** replace `AgentAssignment` — it constrains it. That leaves two coupled planning
variables that must agree, with nothing structurally preventing the solver placing an agent in a
seat outside their shift. Whether that coupling is a hard constraint, a filtered value range, or a
shadow variable is the difference between a clean model and a solver thrashing between two search
spaces. Timefold is pinned at **1.16.0** (`build.gradle:35`) — `AbstractMove.doMoveOnGenuineVariables`
with framework-generated undo; the `Neighborhoods` API from 1.31.0 is unavailable.

**Not in this milestone.** Backlog 999.4 (solver fairness), 999.7 (BambooHR key rotation) and 999.9
(the v1.2 I-2 audit gap) all stay deferred. The remaining candidates, for the milestone after:

1. **Backlog 999.9 — close v1.2's I-2 gap.** The merge-precedence guarantee holds on the upload path
   but not on the Refresh button. High severity, two audits old, and cheap in at least one of its
   three options.
2. **Backlog 999.7 — rotate the BambooHR API key.** Exposed 2026-06-02, still valid, still in a
   public repo. Accepted as risk twice now. This is the most serious open item in the project and it
   is not a feature.
3. **Backlog 999.5 / 999.6 — the reporting half of v1.1** that was never built: coverage,
   utilization, diagnostics, export, score breakdown, tuning. Twelve deferred requirements.
4. **Backlog 999.4 — solver fairness** (QUAL-02, QUAL-03), dropped from Phase 6 and never re-homed.

<details>
<summary>v1.2 Unified Agent Provisioning — original milestone scope (shipped 2026-08-25)</summary>

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

**Outcome:** all 8 target features above shipped. The per-day model, the name split, the unbounded
specialty parsing, the per-desk template, the merge engine and its report, and both retired shapes
all landed. The one thing the original scope did not anticipate was that building the *model* and
building the *view of the model* are separate jobs — hence Phase 13.

</details>

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
- ✓ Every upload runs a fresh BambooHR sync before any merge decision — v1.2 Phase 11 (MRG-01)
- ✓ Per-field precedence: BambooHR authoritative where populated, spreadsheet fills gaps — v1.2 Phase 11 (MRG-02)
- ✓ BambooHR dated PTO wins for the dates it covers; spreadsheet recurring PTO applies only outside that window — v1.2 Phase 11 (MRG-03)
- ✓ Merge report in the Upload Results modal showing per-field source attribution — v1.2 Phase 11 (MRG-04)
- ✓ Merge report flags spreadsheet values overridden by BambooHR, surfacing source disagreement — v1.2 Phase 11 (MRG-05)
- ✓ A spreadsheet-supplied working pattern makes a BambooHR-unknown agent solver-eligible, with a refresh downgrade guard — v1.2 Phase 11 (MRG-06)
- ✓ BambooHR sync failure during upload aborts the whole upload with a clear operator message and zero partial writes — v1.2 Phase 11 (MRG-07)
- ⚠ *Caveat on MRG-01…07:* all seven hold **on the upload path**. The manual "Refresh from BambooHR" button bypasses `AgentMergeService` entirely (audit finding I-2, open) — MRG-02 is the one whose wording is not upload-scoped and is therefore violated as literally written. → 999.9
- ✓ Agent stores first name and last name as separate fields — v1.2 Phase 9 (MDL-01)
- ✓ Per-day contracted hours replace the `contractedHoursPerDay` scalar; `AgentDayConfig` resolves effective hours per date — v1.2 Phase 9 (MDL-02)
- ✓ Existing agents migrated without data loss — scalar became the per-day value, single `name` split into first/last (V29) — v1.2 Phase 9 (MDL-03)
- ✓ One workbook, one worksheet per desk, provisioning agents by BambooHR ID with optional identity fields — v1.2 Phase 10 (UPL-01)
- ✓ Unbounded `Specialty 1…N` column parsing; first non-blank is primary — v1.2 Phase 10 (UPL-02)
- ✓ Mon–Sun day cells parsed as contracted hours, `0` marking a day not worked, blank invalid — v1.2 Phase 10 (UPL-03)
- ✓ `MANDATORY` day cell marks a mandatory day off for that weekday — v1.2 Phase 10 (UPL-04)
- ✓ `PTO` day cell marks recurring weekly PTO across the horizon — v1.2 Phase 10 (UPL-05)
- ✓ Invalid rows skipped with per-row reasons; Upload Results shows per-sheet rollup, clamp warnings, unmatched-sheet notices — v1.2 Phase 10 (UPL-06)
- ✓ Rows whose BambooHR ID is not found are rejected, never created — v1.2 Phase 10 (UPL-07)
- ✓ Both the 6-column legacy shape and the flat enriched shape retired — v1.2 Phase 10 (UPL-08)
- ✓ Pre-seeded per-desk template download; template, parser and export share one `EnrichedColumnLayout` definition — v1.2 Phase 10 + 13 (UPL-09; the specialty-header literals were the last holdout, closed by Phase 13)
- ✓ Roster and Excel export resolve contracted hours from `agent_day_hours`, not the retired scalar; per-weekday values, `MANDATORY` and `PTO` visible in the UI — v1.2 Phase 13 (closes audit I-1/F-1)

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
- **⚠ BambooHR field-4517 alias dependency** — emerged at Phase 11 (code review IN-03): the `/reports/custom` request asks for field id `4517` but the parser reads the key `customWorkingdays`. Without a tenant Field Alias the value is always null in production and MRG-03/MRG-06 silently never activate, with the unit suite still green. Operator confirmed the alias at UAT 2026-08-21; needs re-checking after any BambooHR account change.
- **Cross-agent seat displacement** — emerged at Phase 12: seat capacity, not move selection, is the binding constraint at realistic over-allocation (`.planning/todos/pending/2026-08-13-cross-agent-seat-displacement.md`). **Likely absorbed by v1.3** — a shift-level model displaces a whole agent-day in one move, which is precisely what the slot model could not do
- **⚠ Merge precedence holds on the upload path only** (audit I-2) → 999.9. The manual "Refresh from BambooHR" button overwrites spreadsheet-sourced identity data with no precedence rule and no merge report. Open across two consecutive milestone audits; accepted as debt at v1.2 close.
- **Bulk "Set all days to…" still destroys MANDATORY/PTO labels** (audit I-3, mitigated) → 999.9. A `confirm()` names the count at risk and a safe per-cell edit path now exists, but the destructive seven-row delete-and-recreate is unchanged.
- **Legacy `contractedHoursPerDay` scalar still exported as its own column** (audit NEW-1) → 999.9. It can silently disagree with the per-day columns after any single-cell edit.
- **Nyquist validation debt** — Phases 10 and 13 have `VALIDATION.md` at `status: draft`; validate-phase never reconciled them → 999.9
- **Phase 9 never had a security review** — no `09-SECURITY.md` exists → 999.9

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
**DB:** RDS PostgreSQL 16, `db.t4g.medium`, single AZ. Schema head is **V38**, not V36 — corrected 2026-08-25 during v1.3 research, which found `V38__add_consistent_start_weight.sql` on disk and applied on dev. Key migrations: V29 name-split + per-day fan-out, V30 `day_off_type`, V36 `working_days_source`, **V38 `consistent_start_weight` (orphaned and inert — see Known Issues)**. The next migration is **V39**.
**Codebase after v1.2:** +10,233 / −857 across 105 files in `src/` and `frontend/` over 252 commits (2026-07-30 → 2026-08-25). Backend suite 315 tests green.
**Agent eligibility for solving:** four filters — active status, desk assignment, schedulable job title, and `workingDaysKnown` (parseable BambooHR field 4517)

**Known issues after v1.2:**
- **⚠ An undocumented third attempt at schedule consistency was built and reverted 2026-08-19/20** — discovered 2026-08-25 during v1.3 architecture research; recorded in no planning document until now. Four feature commits, all ancestors of HEAD, all reverted: `7861b83` (preferred start time as an **anchor, not a floor** — fixing the defect that `honourPreferredStartTime` only penalises slots *before* the preference), `9f4a96f` (consistent break offset across an agent's week), `9207ceb` (consistent daily start with a solver-chosen anchor), `6fb78c7` (per-agent start and break-offset spread reporting — effectively the drift report). Two supporting perf commits shared one agent-day grouping across nine constraints. Reverted by `2da56fd`, `3aba7c6`, `65ccb34`, `ac395f2`, `b6188c8`, `12315ed`. **Why it was unwound is not recorded in any commit body and remains an open question** — the revert message explains only why the migration was retained. This is the closest prior art to v1.3 and must be understood before re-implementing: it is either a recoverable asset or a warning, and which one is not yet known.
- **⚠ `V38__add_consistent_start_weight.sql` is an orphaned live migration.** Deliberately retained during the revert above because it had already been applied to dev and recorded in `flyway_schema_history` — deleting or editing it would fail Flyway validation and block all dev deploys. It adds `consistent_start_weight VARCHAR(50) NOT NULL DEFAULT '0hard/2soft'` to the constraint-weights table. Nothing reads it: the only reference anywhere in `src/` is the migration file itself. v1.3 should adopt this column rather than add a duplicate.
- **⚠ Security:** the BambooHR API key exposed 2026-06-02 (`ad2bb…2be`) was never rotated and is still present in tracked planning docs in this **public** repo. Integration code has since deployed to the live environment. Tracked as Backlog 999.7 — accepted as risk at two consecutive milestone closes and still unresolved.
- **⚠ The "Refresh from BambooHR" button bypasses the merge engine** (audit I-2). It overwrites spreadsheet-sourced identity data with no precedence rule and emits no merge report. A normal operator action that silently discards the guarantees MRG-02/04/05 describe. Tracked as Backlog 999.9.
- **⚠ BambooHR field-4517 alias is a silent single point of failure.** The request asks for field id `4517`; the parser reads the JSON key `customWorkingdays`. With no tenant Field Alias configured, the value is always null in production and MRG-03/MRG-06 never activate — while every unit test stays green, because the fixtures hand-construct `BambooEmployee`. Confirmed present by operator at Phase 11 UAT; re-check after any BambooHR account change.
- **BambooHR field 4517 is sparsely populated** — ~45% company-wide, ~24% parseable. Mitigated but not eliminated by v1.2: a spreadsheet-supplied pattern now makes an agent solver-eligible (MRG-06). The exclusion proportion on live desks was never measured.
- **Bulk "Set all days to…" destroys MANDATORY/PTO labels** (audit I-3). Warned via `confirm()`, not prevented. The per-cell edit path is safe.
- Data-gap and outlier agents are surfaced only as CloudWatch `log.warn` lines — no operator UI.
- The legacy `contractedHoursPerDay` scalar survives as a live multi-writer field and is still exported as its own column; it can disagree with the per-day columns after a single-cell edit (audit NEW-1).

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
| Timefold pinned at 1.16.0 | `ScoreAnalysis` moves to paid tier in 2.0. Corrected 2026-08-13 — this row previously read 1.33.0; Phase 12 verified the actual pin against `build.gradle:35` and the running solver's custom-move API | ✓ Good |
| PDF export via OpenPDF 3.0.4 | LGPL/MPL licensed; iText rejected as AGPL | — Pending (unbuilt) |
| Fairness soft-score only; quadratic hours-consistency penalties | Hard fairness makes schedules infeasible; linear penalties create score traps | — Pending (unbuilt) |
| Phase 6 narrowed to QUAL-01 only | Data foundation had to land before fairness/consistency constraints | ⚠ Revisit — QUAL-02/03 were never re-homed and nearly lost |
| BambooHR key rotation gate bypassed | Operator directive 2026-07-29, accepted risk | ⚠ Revisit — still unresolved, key is public |
| Fresh BambooHR sync fetched *before* the upload transaction opens, not inside it (Phase 11) | Makes zero-write on sync failure structural rather than a caught-exception discipline: the fetch throws before `transactionTemplate.executeWithoutResult` is ever reached (MRG-07/T-11-01) | ✓ Good |
| PTO/pattern arbitration runs at solve time, in-memory, re-derived per solve (Phase 11, D-10) | Operator-selected one-way door: no new storage, no `AgentDayOffRepository` in the upload path. Deterministic and reproducible from inputs | ⚠ Revisit — leaves no persisted audit of which recurring PTO facts a given solve suppressed (accepted risk R-11-02) |
| `Agent.workingDaysSource` provenance marker (V36), defaulting to `BAMBOOHR` (Phase 11, D-15) | A BambooHR refresh must never reclaim ownership of a week an operator corrected via spreadsheet; the default keeps every existing agent's eligibility unchanged at deploy time | ✓ Good |
| One polymorphic 7-column Mon–Sun day group instead of three ~21-column groups (Phase 10, revised 2026-07-31) | The day cell's *value* encodes status (number / `MANDATORY` / `PTO`), so the three concepts cannot contradict each other. Blank is invalid — an unfilled cell is an error, not a silent default | ✓ Good |
| `EnrichedColumnLayout` as the single column-layout definition shared by template, parser and export (Phase 10, D-13) | Header drift between the three was the standing risk. Phase 13 had to finish the job — two specialty-header literals had survived in `DeskAssignmentTemplateService` (audit I-4) | ✓ Good — but only after Phase 13 closed the last holdout |
| `Agent.contractedHoursPerDay` scalar kept as a live field after MDL-02/03 made it non-authoritative (Phase 9, D-05) | Deferred deliberately to avoid a wide refactor during the migration | ⚠ Revisit — this is the direct root cause of audit finding I-1 (readers were never migrated when the scalar stopped being the source of truth) and of NEW-1 (the scalar column can still disagree with the per-day columns) |
| `setDayHours` edits exactly one weekday; the destructive seven-row fan-out survives only as an explicitly labelled bulk action (Phase 13) | Closes audit I-3 *by construction* for the common case rather than by discipline. The bulk path stays destructive by design (D-10) and warns instead | ⚠ Revisit — the warning is mitigation, not preservation; an operator who clicks through still loses labels |
| The per-cell editor opens **empty** with the stored value as placeholder, guarded by `cellDirtyRef` (Phase 13, G-13-DD) | A seeded `<input>` collapses the native `<datalist>` to its single self-matching option, making the 100-entry picklist unreachable exactly when editing a cell that already has a value. The guard stops an untouched blur from firing `clearRow` | ✓ Good |
| `Not set (default)` clipping at the 90px per-cell input accepted, not fixed (Phase 13, G-13-8) | Widening to ~140px would take the expanded grid ~678px → ~1028px and trade away verified E3 overflow behaviour; shortening the literal would change the string `saveDayHours` matches for `clearRow`. The entry stays selectable and unambiguous | ✓ Good — spec corrected to assert the clipping rather than deny it |
| Phase 12 withdrawn rather than shipped or re-planned (2026-08-13 operator ruling) | The seeded 5×5 benchmark put the move's effect (+0.25h median) inside the baseline's own 5.00h noise spread, and it was inert at realistic 130% over-allocation. Keeping code that cannot be shown to help is worse than reverting it | ✓ Good — a phase goal explicitly not claimed is a healthier outcome than one quietly assumed |
| v1.2 closed under `override_closeout` with I-2 accepted as debt (2026-08-25) | The milestone's own headline defect (I-1/F-1) was fixed and every requirement satisfied on the upload path. I-2 predates Phase 13's scope and was never assigned to any phase | ⚠ Revisit — carried to 999.9; two consecutive audits recorded it untouched, which is how gaps become permanent |

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
*Last updated: 2026-08-25 at the start of milestone v1.3 Shift-Based Scheduling & Consistency — scoped to shift-level modelling only; 999.4 / 999.7 / 999.9 remain deferred*
