# WFM Service

## What This Is

A workforce management scheduling service for Helpware — used to build and optimise agent schedules across multiple client desks. Built with Spring Boot + Timefold Solver (constraint-based optimisation) + React SPA. Deployed to AWS (ECS Fargate + RDS + CloudFront). Live at `d2bbtcc80peap7.cloudfront.net`.

Operators configure desks (queues), upload staffing demand (FTE spreadsheets), sync agents from BambooHR, capture preferences and exceptions, then run the solver to produce an optimised weekly schedule.

Since v1.3 a desk is scheduled in one of two modes. **Slot-scheduled** is the original behaviour: the solver composes each agent-day out of independent per-timeslot seat decisions. **Shift-scheduled** gives the desk a library of shift templates and has the solver assign each working agent exactly one shift per day from it, with seat and specialization assignment happening *inside* that envelope — so an agent works a recognisable, repeating shift, and can still change specialization mid-day. Every desk defaults to slot-scheduled; the mode is per-desk and reversible.

Since v1.5 each desk has a **day start**, and every timeslot carries a **business date** distinct from its calendar date. A shift can therefore span midnight and still belong to the business day it starts on, in the solver, the grid, the Excel export and every report. A desk can also set a **minimum rest** period between an agent's consecutive shifts. It is enforced as a hard constraint, refused before solving when unavoidable, and waivable per agent and date with a recorded reason. A desk left at the `00:00` default with no minimum rest behaves exactly as it did before.

## Core Value

Scheduling managers can produce optimised, constraint-aware agent schedules in minutes instead of hours — without spreadsheets.

## Current State

**Shipped:** v1.5 Overnight Shifts & Business Dates — closed 2026-10-08 with **29 of 29
requirements** delivered across Phases 18–24 plus one quick task. Archived to
`.planning/milestones/v1.5-ROADMAP.md`.

v1.5 made the **business day** the scheduling unit. A desk sets the time its day begins, and
timeslot generation produces a contiguous 24-hour business day across two calendar dates.
`DayWindow` measures every interval from that anchor instead of an implicit midnight. Every solver
join, the seat-supply check, SLOT accounting, demand upload, coverage reporting and shift-library
validation resolve the same business date for the same timeslot. On top of that, overnight shift
templates save with correct net hours, consume the starting weekday's contracted hours, and render
as one block. A per-desk minimum rest is enforced with a pre-solve refusal and per-date waivers.

**The audit loop earned its keep.** Four audit passes were needed. Each of the first three found a
real defect at a seam between phases, and each was closed in turn:

- **G-1:** the rest gap after an overnight predecessor. Closed by Phase 23.
- **N-1/N-2:** calendar-date keys in shift-library validation and in the Agent Allocation rows.
  Closed by Phase 24.
- **CR-01:** a pre-solve rest fallback that was not scoped to the horizon edge. Closed by quick task
  261008-eby.

CR-01 had been recorded as `critical` in Phase 23's own code review four days earlier and was missed
by one audit pass. **Open review findings are audit inputs, not phase-local notes.**

**Closed under `override_closeout`**, with the milestone audit at `tech_debt` (no gaps).

- **Stale verifications:** Phases 18–23 closed with stale verification digests, because later
  phases changed source they cover. Every report body passed, but re-verification is still owed.
- **3 artifacts acknowledged:** Phase 21's UAT status label, archived v1.2 Phase 12's withdrawn
  verification, and the `MultiDayConstraintDiagnosticTest` wall-clock flake.
- **Nyquist:** 0 compliant, 1 partial (18), 6 not validated (19–24).

See `.planning/milestones/v1.5-MILESTONE-AUDIT.md`.

<details>
<summary>Previous state: v1.3 Shift-Based Scheduling & Consistency (closed 2026-09-21)</summary>

**Shipped:** v1.3 Shift-Based Scheduling & Consistency — closed 2026-09-21 with **43 of 43
requirements** delivered across Phases 14–17. Archived to `.planning/milestones/v1.3-ROADMAP.md`.

v1.3 replaced the slot pattern with a **recognisable, repeating shift**. A desk now defines a
library of shift templates; the operator switches that desk into `SHIFT` mode — refused, with the
uncovered demand windows named, if the library cannot cover the demand — and the solver assigns each
working agent exactly one shift per day from it, never seating them outside that envelope. Breaks
come from the template's bands rather than from four emergent constraints. Each agent has a stored
usual shift per weekday, settable in bulk by upload or inline in the roster, visible in the roster
and the Excel export; the solver is nudged toward it by a soft constraint with an operator-tunable
tolerance band and weight, and the drift from it is reported per agent and date — from the same
distance calculation the constraint uses, not a second implementation.

**Closed under `override_closeout`** — not for unmet requirements (all 43 are satisfied and the
milestone audit found zero integration gaps across six seams and four E2E flows), but because 10
open artifacts were acknowledged as deferred rather than resolved: 3 diagnosed-but-unfixed debug
sessions and 7 deferred items. See `.planning/milestones/v1.3-MILESTONE-AUDIT.md` and STATE.md's
Deferred Items table.

**What the audit found worth naming.** The milestone's real strength is **self-enforcing structural
guards** — a reflection-derived constraint-classification test, a write-path allowlist proven able
to fail twice, a migration-vs-entity reconciliation, and a Testcontainers Postgres base class — so
four classes of regression now fail the suite instead of depending on review attention. Its real
weakness is **stale planning records**: three documents (`14-VERIFICATION.md`, `REQUIREMENTS.md`,
`STATE.md`) each asserted something a later event had already falsified, and none propagated.

**Two functional gaps ship knowingly**, both deferred by operator ruling OR-2 rather than missed:
blocked-break-hours has no enforcement point in SHIFT mode (a band at the envelope boundary is a
late start, not a break, and scores `0hard`), and a template's envelope is never validated against
the desk's operating window at save time.

</details>

<details>
<summary>Previous state: v1.2 Unified Agent Provisioning (closed 2026-08-25)</summary>

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

</details>

<details>
<summary>Previous state: v1.1 Schedule Quality & Reporting (closed 2026-07-29)</summary>

Closed with 4 of 16 requirements delivered (Phases 5–6 of 8). The remaining 12 requirements are
preserved in `.planning/ROADMAP.md` Backlog 999.4–999.6.

v1.1 delivered the **agent-data foundation**: BambooHR supplies employment type, job title, and —
the significant one — each agent's fixed weekly working-days pattern (field 4517), which the solver
honours as hard MANDATORY day-off blocks. It did **not** deliver the reporting, diagnostics, export,
or solver-tuning surfaces that were the milestone's other half.

</details>

## Next Milestone Goals

Not yet defined. Run `/gsd-new-milestone`. Candidates, in the order the evidence suggests:

1. **Phil-US migration onto real overnight shifts.** v1.5 deliberately deferred this so the capability
   stayed desk-agnostic. The desk still carries its +3h offset hack. Moving it onto the client's real
   `2100-0600` / `2200-0700` / `2300-0800` / `0000-0900` shifts, with a tested reversal, is the
   natural first consumer of what v1.5 built.
2. **v1.5 verification debt.** Re-verify Phases 18–23, whose digests are stale. Run Nyquist
   validation on 19–24. Triage the open review findings: Phase 21 WR-01/02/03, Phase 22 WR-03/04 and
   IN-02, Phase 23 WR-01 and IN-01/02, Phase 24 WR-01/02/03 and IN-01/02.
3. **Backlog 999.9 — close v1.2's I-2 gap.** The merge-precedence guarantee holds on the upload
   path but not on the Refresh button. High severity, now four milestones old.
4. **Backlog 999.5 / 999.6 — the reporting half of v1.1:** coverage, utilization, diagnostics,
   export, score breakdown and tuning. Twelve deferred requirements.
5. **Backlog 999.4 — solver fairness** (QUAL-02, QUAL-03).

<details>
<summary>v1.5 Overnight Shifts & Business Dates — original milestone scope (shipped 2026-10-08)</summary>

**Goal:** A shift can span midnight and belongs to the business day it starts on — for any desk,
any day start, any future data set.

**Target features:**

- **Desk-level day start** — one uniform anchor per desk. A desk that has never set one defaults to
  `00:00` and behaves exactly as it does today; only night desks opt in.
- **Business date on every timeslot** — distinct from its calendar date, populated at generation.
- **`DayWindow` re-anchored** on the desk's day start instead of midnight, retiring the
  `00:00`-means-end-of-day convention. 112 references across 16 main-source files, all routed
  through one utility by design (P-19/D-02) — one coordinated change, and the riskiest edit in the
  milestone.
- **Overnight shift templates** — an end clock earlier than the start clock, with correct net
  hours; a day-off or PTO marking on the *starting* business day blocks the shift; contracted hours
  consume the starting weekday's row rather than splitting across two; the envelope is validated
  against the desk's business day; export and UI render one continuous block, not two fragments.
- **Solver business-date correctness** — all 12 `timeslot.getDate()` joins in
  `ScheduleConstraintProvider` move to business date, plus a test that fails if any constraint joins
  calendar date where business date is meant. The failure mode here is a *silent non-join*, not an
  error, which is why the guard is a requirement rather than a review item.
- **SLOT-mode correctness** — an overnight stretch counts against one business day instead of
  under-allocating both calendar days. Already wrong today, independent of the new capability.
- **Minimum rest, hard constraint** — per desk, refused pre-solve where structurally unavoidable,
  naming the agent and the two shifts. A desk that sets none solves exactly as it does today.
- **Regression safety** — constructed midnight-spanning scenarios, chosen by the property under
  test, carry the midnight coverage; **Phil-US (48 agents)** is a single small drift guard.

**Why this shape.** v1.4 attempted the same capability and was cancelled on 2026-09-30 before
shipping, with all 32 commits unwound (state preserved at tag
`rescue/phase-18-unwind-20260930`). It did not fail on execution: it failed because BDAY-06's
regression fixture sourced its inputs from four captured live desks, which are simultaneously
over-sensitive (Vinted's 287 agents mean any scoring change anywhere shifts the golden bytes with
no diagnostic signal) and under-powered on midnight — three of the four live desks never cross it,
no desk has a 23:00–00:00 slot, and no break band touches an envelope edge. A baseline built to
catch a midnight regression rested on data that barely contains midnight, because *this* milestone
is what introduces overnight shifts. v1.5 inverts that: correctness is proven on constructed cases
chosen by the property under test, and live data is demoted to a drift guard.

**Settled at v1.4 scoping, carried forward — do not relitigate without new evidence:**

- **Desk-level day start**, not a per-template `ends_next_day` flag. One uniform anchor; the flag
  was considered and rejected for leaving `DayWindow`'s midnight convention in place alongside a
  second mechanism.
- **Minimum rest is HARD.** Enabling overnight shifts without it would *create* a way to produce
  illegal back-to-back rosters scoring `0hard` — a gap this milestone would introduce, not inherit.
- **Guard tests land before the re-anchoring.** `DayWindow` is the only thing standing between this
  change and a silently wrong schedule.
- **`agent_day_hours` needs no schema change** — it is keyed by `DayOfWeek`, and a Sunday-night
  shift correctly consumes Sunday's row.

**Deliberately deferred:**

- **Phil-US migration.** v1.4 made the live desk its proof; v1.5 does not. The desk keeps its +3h
  offset hack — every roster time stored offset purely so the base shift reads `00:00-09:00` — and
  moving it onto the client's real `2100-0600` / `2200-0700` / `2300-0800` / `0000-0900` shifts,
  with a tested reversal, becomes the natural next milestone. Deferring it is what keeps the
  capability desk-agnostic.
- **Timezones** (the Phil-US roster is PHT, its forecast US Pacific, 15h apart) stay the operator's
  job, done before load. Keeps the re-anchoring self-contained.

**Outcome:** every target feature above shipped, and all v1.4 decisions carried forward held. The
constructed-scenario regression strategy worked: Phase 20's 26-constraint match-count table and the
48-agent Phil-US-shaped drift guard caught a silent non-join that score alone would have passed.

What the original scope did not anticipate was how many **non-solver consumers of a timeslot's
date** existed. Shift-library validation, envelope repair, start-mix targets and the Agent
Allocation demand rows each kept a calendar-date key after the solver had moved to business date.
Two of them survived to the milestone audit (N-1, N-2). The widened `BusinessDateJoinGuardTest` now
scans eight services with an empty allowlist; six more files remain outside it.

</details>

<details>
<summary>v1.3 Shift-Based Scheduling & Consistency — original milestone scope (shipped 2026-09-21)</summary>

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

**Not in this milestone.** Backlog 999.4 (solver fairness) and 999.9 (the v1.2 I-2 audit gap) stay
deferred. The remaining candidates, for the milestone after:

1. **Backlog 999.9 — close v1.2's I-2 gap.** The merge-precedence guarantee holds on the upload path
   but not on the Refresh button. High severity, two audits old, and cheap in at least one of its
   three options.
2. **Backlog 999.5 / 999.6 — the reporting half of v1.1** that was never built: coverage,
   utilization, diagnostics, export, score breakdown, tuning. Twelve deferred requirements.
3. **Backlog 999.4 — solver fairness** (QUAL-02, QUAL-03), dropped from Phase 6 and never re-homed.

**Outcome:** all nine target features above shipped, and the central architectural question was
answered empirically before any phase was planned — `milestones/v1.3-research/SPIKE-COUPLING.md` settled the coupling as a
hard `ConstraintStream` constraint between two independent `@PlanningEntity` classes (Option A),
after the filtered-value-range alternative (Option C) compiled, passed `FULL_ASSERT` clean, and
reported infeasible schedules as `0hard/0soft` optimal on 8/8 seeds. The one thing the original
scope did not anticipate was how much of the phase-15 effort would go into *refusing* bad solves
rather than producing good ones: the seat-supply gate, its calendar-awareness fix, its
band-composition blindness and its day-wide-only comparison were four separate rounds of the same
class of defect.

</details>

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
- ✓ Per-desk shift library: templates with start/end time, break placement rule, valid weekdays and an effective date range, editable and retirable without corrupting schedules that reference them — v1.3 Phase 14 (SHLB-01…04)
- ✓ Shift library validated against real demand at definition time: uncovered `(date, timeslot)` windows named specifically, and templates whose net duration matches no agent's effective contracted hours flagged as an advisory — v1.3 Phase 14 (SHLB-05, SHLB-06)
- ✓ Per-desk `SLOT`/`SHIFT` scheduling mode; every existing desk backfilled to `SLOT` with zero behaviour change, switch refused with the same named uncovered windows the report shows, and refused with a readable 409 during an in-flight solve without stopping it — v1.3 Phase 14 (MODE-01…03)
- ✓ Mode switching never alters an already-accepted schedule, and no production solver file changed — proven structurally, not asserted — v1.3 Phase 14 (MODE-04, MODE-05)
- ✓ All 19 existing solver constraints classified mode-agnostic / mode-gated / needs-shift-variant, with a reflection-derived completeness test that cannot be silenced by editing a count — v1.3 Phase 14 (XCUT-05, partial; mode-gating lands in Phase 15)
- ✓ On a shift-scheduled desk the solver assigns each agent exactly one shift per day from that desk's library and never seats them outside it — a hard-constraint coupling (Option A) proven sound against a real-shaped fixture by a walker that shares no code path with the constraint it checks, with a non-vacuity assertion so a green pass cannot be empty — v1.3 Phase 15 (ENVL-01…04, XCUT-05 completed)
- ✓ Breaks are distributed across bands rather than taken simultaneously; band capacity is operator-settable, `0` rejected and blank meaning unlimited, with a save-time advisory when capacity cannot seat the agents the envelope admits — v1.3 Phase 15 (ENVL-05, ENVL-06)
- ✓ Shift assignment is legible in the operator view: the Agent Schedule Shift column agrees with the Agent Allocation group header, out-of-envelope seats (`E!`) are visually distinct from surrendered legal slots (`x`), and an hour no template reaches renders as deliberately unstaffed rather than as unfilled demand — v1.3 Phase 15 (ENVL-08, ENVL-09)
- ✓ A starting shift library can be suggested from demand instead of composed by hand — the generator writes nothing, dedupes on full template identity, and places break bands away from demand peaks with a coverage re-check — v1.3 Phase 15 (ENVL-10, SHLB-07)
- ✓ A shift-mode solve that cannot succeed is refused *before* it runs, naming the date, the per-hour seat shortfall and the levers the operator controls — including a per-agent-day forced-occupancy check, and refusal advice that withdraws the raise-the-ceiling suggestion when unassigned seats are hard-weighted — v1.3 Phase 15 (ENVL-07)
- ✓ Shift-mode benchmarked honestly at realistic scale: threshold committed before results (`cd26db9`), and a result inside the comparison arm's own spread written up as "no measurable difference" rather than a win — v1.3 Phase 15 (XCUT-04)
- ✓ Each agent stores a usual shift per weekday against a live template from their desk's library, with an absent value representable as genuinely absent — `Optional.empty()` / `NOT_SET`, never a substitute default that would drag the solver toward an arbitrary shift — v1.3 Phase 16 (USHF-01, USHF-04)
- ✓ Usual shifts settable both ways, mirroring exactly what v1.2 built for contracted hours: seven `Usual Shift {Day}` columns in the per-desk upload template (pre-filled, with a working Excel dropdown) and an inline `<select>` in each roster day tile writing through one choke point — v1.3 Phase 16 (USHF-02, USHF-03)
- ✓ Every reachable write path that can change usual-shift data enumerated in a nine-row table and verified one test per path, guarded by a static-source-scan set-equality test proven able to go red twice — the exact shape of v1.2 audit finding I-2, closed structurally rather than by discipline — v1.3 Phase 16 (USHF-05, XCUT-02)
- ✓ A stored usual shift is visible everywhere agent data is displayed — roster tile (four states: live, never set, stored-but-retired, not worked) and Excel export — traced store → roster → export in one continuous test rather than three per-layer ones — v1.3 Phase 16 (USHF-06, XCUT-01)
- ✓ The solver is penalised for assigning a shift that differs from an agent's stored usual shift, inside an operator-configurable per-desk tolerance band and weight, soft-only — the non-zero-hard-component guard is enforced at save time, so CONS-04 cannot be violated by configuration — v1.3 Phase 17 (CONS-01…04)
- ✓ Where consistency scores two shifts equally, the agent's recorded `AgentPreference` start time decides, and that precedence is documented and observable rather than implicit in relative weights — v1.3 Phase 17 (CONS-05, CONS-06)
- ✓ Post-solve drift reporting: which agents were assigned a shift other than their usual one, on which dates and by how much; an agent with no stored usual shift distinguished from one whose shift was honoured; and the most over-subscribed templates ranked, making the consistency-versus-fairness tension visible without building a mitigation for it — v1.3 Phase 17 (DRFT-01, DRFT-02, DRFT-04)
- ✓ The drift report is derived from the same distance calculation the consistency constraint uses (`ShiftBandPair.startDeviationMinutes`), not a second implementation — and its Excel sheet headers are byte-identical to the frontend tab's — v1.3 Phase 17 (DRFT-03, XCUT-01)

- ✓ An operator sets the time a desk's day begins; a desk that never sets one behaves exactly as before, and changing it under an ACCEPTED schedule is refused — v1.5 Phases 18, 20, 21 (BDAY-01)
- ✓ Every timeslot records its business date, distinct from its calendar date, written by exactly one deriving path and one propagating path, held by a two-directional write-path guard — v1.5 Phase 18 (BDAY-02, BDAY-08); re-satisfied at non-00:00 anchors for shift-library validation and allocation rows by Phase 24
- ✓ A 21:00-anchored desk generates a contiguous 24-hour business day spanning two calendar dates — v1.5 Phase 18 (BDAY-03)
- ✓ `DayWindow` interval arithmetic anchored on the desk's day start, with the midnight-implicit statics made private so a missed call site is a compile error — v1.5 Phase 19 (BDAY-04)
- ✓ Structural guards fail the build on a scheduling comparison or arithmetic that bypasses `DayWindow`, and on a constraint joining calendar date where business date is meant, both proven able to go red — v1.5 Phases 18, 20, 24 (BDAY-05, SOLV-02)
- ✓ A constructed midnight-boundary regression suite with a class-load non-vacuity validator, and a 48-agent Phil-US-shaped drift guard proving the re-anchoring changed nothing it shouldn't have — v1.5 Phases 18, 20 (BDAY-06, BDAY-07)
- ✓ Overnight shift templates (end before start) save with correct net hours, are reported against and consume the contracted hours of the business day they start on, are blocked by a day off or PTO on that day, are refused if their envelope leaves the desk's business day, and render as one continuous block labelled with both calendar dates in the grid and Excel export — v1.5 Phase 21 (OVNT-01…OVNT-07); OVNT-05/06/07 re-satisfied at non-00:00 anchors by Phase 24
- ✓ Every solver join, the seat-supply check, SLOT-mode overnight accounting, break bands, contiguity and envelope compliance resolve business date, each migrated join proven non-vacuous by per-constraint match counts, and demand upload / coverage reporting / solver provably agree on a timeslot's business date — v1.5 Phase 20 (SOLV-01…SOLV-07); SOLV-07 extended to the Agent Allocation demand rows by Phase 24
- ✓ A per-desk minimum rest period between an agent's consecutive shifts, enforced as a hard solver violation on the actual end/start instants (same-day back-to-back included, not only overnight), with a structurally-unavoidable case refused *pre-solve* by a mechanism separate from the in-solve constraint, horizon-edge behaviour defined and tested rather than accidental, a desk that sets no minimum rest solving exactly as before, a per-agent per-business-date waiver carrying a mandatory recorded reason, and every waived violation disclosed in the solved output so a waiver cannot silently hide a roster problem — v1.5 Phase 22 (REST-01…REST-07); REST-01/02/05 re-satisfied for an overnight (wrapping) predecessor by Phase 23 gap closure G-1 — `RestSpan.gapMinutes` and the SLOT pre-horizon refusal now share one wrap-aware primitive, `DayWindow.anchoredWrappedEndMinute`, held single by a build-time guard; the pre-solve fallback to pre-horizon history was scoped to the horizon's first date by quick task 261008-eby (CR-01), so a working day after a mid-horizon day off is no longer falsely refused

### Active

No milestone is active. v1.5 closed 2026-10-08 with 29/29 requirements validated above; the next
milestone's requirements are defined by `/gsd-new-milestone`.

#### Carried forward — see ROADMAP.md Backlog

- **Phil-US migration onto real overnight shifts.** Deferred from v1.5 by design; first candidate
  for the next milestone.
- **v1.5 verification debt.**
  - Phases 18–23 closed with stale verification digests and need re-verifying, not
    fingerprint-refreshing.
  - Thirteen review findings are still open across Phases 21–24. The ones that matter most:
    - Phase 21 WR-03: the Agent Allocation tab can throw past the end of the business day, with no
      error boundary.
    - Phase 24 WR-03: a malformed business-range cursor returns 500, not 400.
    - Phase 23 WR-01: the rest-gap arithmetic guard can be evaded.
- **`BusinessDateJoinGuardTest` breadth.** These files are not scanned: `SolverService`,
  `ScheduleService`, `ShiftStartMixAllocator`, `ScheduleExportService`, `BusinessDayPeriodLoader`
  and `TimeslotController`. None has a defect today. This is the regression risk N-1/N-2 showed is
  real.

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
- **⚠ BambooHR field-4517 alias dependency** — emerged at Phase 11 (code review IN-03): the `/reports/custom` request asks for field id `4517` but the parser reads the key `customWorkingdays`. Without a tenant Field Alias the value is always null in production and MRG-03/MRG-06 silently never activate, with the unit suite still green. Operator confirmed the alias at UAT 2026-08-21; needs re-checking after any BambooHR account change.
- **Cross-agent seat displacement** — emerged at Phase 12: seat capacity, not move selection, is the binding constraint at realistic over-allocation (`.planning/todos/pending/2026-08-13-cross-agent-seat-displacement.md`). **Not resolved by v1.3** — an earlier note here read "likely absorbed by v1.3", which the coupling spike and the operator's plateau ruling disproved. Phase 15 *measures* the same underlying gap (a shift and its seats must move together; change-moves shift one at a time) and explicitly does not close it. The todo stays unlinked to any phase, per its own frontmatter warning, so a phase close cannot auto-sweep it away unresolved
- **⚠ Merge precedence holds on the upload path only** (audit I-2) → 999.9. The manual "Refresh from BambooHR" button overwrites spreadsheet-sourced identity data with no precedence rule and no merge report. Open across two consecutive milestone audits; accepted as debt at v1.2 close.
- **Bulk "Set all days to…" still destroys MANDATORY/PTO labels** (audit I-3, mitigated) → 999.9. A `confirm()` names the count at risk and a safe per-cell edit path now exists, but the destructive seven-row delete-and-recreate is unchanged.
- **Legacy `contractedHoursPerDay` scalar still exported as its own column** (audit NEW-1) → 999.9. It can silently disagree with the per-day columns after any single-cell edit.
- ~~**⚠ No test executes the real Flyway migrations**~~ — **CLOSED in v1.3.** Emerged at Phase 14 (UAT gap G-14-1): V39 shipped with `valid_weekdays CHAR(7)` against an entity mapping of `varchar(7)`, the migration applied cleanly, the application then failed to boot under `ddl-auto=validate` — with a fully green 402-test suite, because `application-test.yml` sets `flyway.enabled: false` with `ddl-auto: create-drop` against H2. Fixed in place (`9a98029`), and the blind spot itself was then closed twice over, both in Phase 15: `MigrationEntityConsistencyTest` (`d909074`) reconciles migration DDL text against entity mappings statically, and `PostgresBackedTest` (`d5b4169`) is the Testcontainers-backed base class this entry asked for — real Postgres 16 matching dev RDS, `flyway.enabled=true`, `ddl-auto=validate`, every migration V1..Vn run in order. Four test classes use it (Phase 18 added `MidnightTimeslotPostgresTest`, which is what exercises V53). **Residual:** it carries `disabledWithoutDocker = true`, a deliberate trade so a Docker-less developer machine skips rather than fails — which means CI must actually have Docker for the guard to be live. **Sharpened at Phase 18 (T-18-05-04):** nothing in the repository asserts the class actually ran — a grep for `skipped="0"` across `src/test/java` and `build.gradle` returns nothing. Phase 18's own check was a one-time executor observation recorded in `18-05-SUMMARY.md` (`tests="6" skipped="0"`), not a standing control. GitHub Actions supplies Docker so CI is covered today; a developer running `./gradlew build` with Docker down still gets a green build with the class silently skipped. Closing this means a standing assertion, not another manual observation.
- **⚠ Blocked-break-hours has no enforcement point in SHIFT mode** — emerged at Phase 15, deferred by operator ruling OR-2. `breakBlockedWindow` is mode-gated off for `SHIFT`, and `ShiftTemplateService.validateBands` never checks a band's offset against the desk's `breakBlockedHours`. A band at offset `0`, or at `envelopeMinutes - duration`, is legal at save time and scores `0hard` — operationally a late start or an early finish, not a break. Invisible in the hard score, so the seat-supply gate cannot catch it. One live agent-day already exhibits it (8 consecutive worked hours, zero breaks, live Stubhub desk). The fix location is settled as **save-time in `ShiftTemplateService`**, not a restored solver constraint — that alternative was considered and rejected for fighting the envelope model; do not relitigate it.
- **⚠ A template's envelope is never validated against the desk's operating window at save time** — emerged at Phase 15, deferred by operator ruling OR-2. `validateGridAlignment` checks grid alignment but never that the envelope's end fits inside the operating window's close, so a template that cannot fit saves cleanly with an advisory literally reading "It will still save". The seat-supply gate catches the runtime symptom, but the operator is told at solve time about a desk they may not connect back to the template edit. `TimeslotBoundsResponse.endTime()` is read by no caller in `src/main` and is the natural starting point.
- **Two wall-clock-bounded solver tests should terminate on step count** — `BreakAwareConstructionTest` and `MultiDayConstraintDiagnosticTest` time-box the local-search phase, so they measure hardware and suite contention alongside solver quality and flake under parallel load. `BreakAwareConstructionTest`'s margin against its `-500` assertion fell from 500 to 180 points after Phase 15's mode-gating. Widening that tolerance is explicitly **not** the fix — the threshold is what surfaced two real defects (a dropped `difficultyComparatorClass` and a constraint tax costing ~35–40% of local-search throughput).
- **Nyquist validation debt — now eleven phases across three milestones** → 999.9. v1.5 added six (19–24 at `status: draft`; 18 validated-but-PARTIAL). Before that: Phases 10, 13, **14 and 15** have `VALIDATION.md` at `status: draft` (seeded by plan-phase, never reconciled by validate-phase, so their `nyquist_compliant: false` is not authoritative). **Phases 16 and 18 are genuine PARTIALs** — `status: validated` *and* `nyquist_compliant: false`. Only Phase 17 is COMPLIANT. Phase 18 was validated at its own close (2026-09-30) with zero gaps: all six BDAY requirements have automated tests, and its single manual-only item (the `day_start` disclosure copy, D-28) is a knowingly accepted exception that has now been UAT-verified against the deployed environment. The v1.3 audit flagged the accumulation as having drifted "from an oversight into a pattern".
- **Phase 9 never had a security review** — no `09-SECURITY.md` exists → 999.9
- ~~**No real-DB test proves business-date *exclusion* at a non-midnight anchor**~~ — **CLOSED 2026-10-02 (`597f171`).** Emerged at Phase 20 (code review WR-01, advisory in `20-VERIFICATION.md`): `BusinessDayPeriodLoader`'s *exclusion* half was proven only at the unit/mocked level (`BusinessDayPeriodLoaderTest` Test 3) and, through the real `acceptSchedule`/JPA path, only at a `00:00` anchor where calendar and business date coincide and the assertion cannot discriminate. Closed by `ScheduleServiceShiftSnapshotTest` Tests E and F — 21:00-anchored accept-path tests carrying one out-of-range decoy per side (calendar MONDAY 08:00 derives to SUNDAY, below; calendar TUESDAY 21:00 derives to TUESDAY, above), both *inside* the widened fetch window so only the derived filter can remove them. Mutation-verified: the real gap was not "nothing catches a deleted filter" (Test D did) but that each existing group was blind to exactly one failure mode — A/B/C miss a deleted filter, D cannot tell derived from calendar date. E/F are the only tests sensitive to both. Absence is asserted on the `(calendarDate, startTime)` pair, since both decoy start times also occur on legitimately in-range rows

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
**DB:** RDS PostgreSQL 16, `db.t4g.medium`, single AZ. Schema head is **V55** after v1.5 added three: V53 `desk.day_start` + `timeslot.business_date`, V54 `schedule.day_start`, V55 minimum rest + `agent_rest_waiver`. The next migration is **V56**. Before v1.5 the head was V52; v1.3 left it at V49. Key migrations: V29 name-split + per-day fan-out, V30 `day_off_type`, V36 `working_days_source`, V38 `consistent_start_weight` (**no longer orphaned — adopted by v1.3 rather than duplicated, exactly as planned**), V39 `shift_template` + `desk.scheduling_mode`, V40 break bands, V43 `schedule.scheduling_mode` (persisted at accept time), V44 bounded envelope slack, V47 `agent_usual_shift`, V48 consistency tolerance + preferred-start weight, V49 consistency weight defaults.
**Codebase after v1.5:** +27,013 / −916 across 183 files in `src/` and `frontend/src/` over 396 commits (2026-09-30 → 2026-10-08); backend suite ~1,510 tests.
**Codebase after v1.3:** +31,010 / −293 across 169 files in `src/` and `frontend/src/` over 370 commits (2026-08-25 → 2026-09-21). 114 backend test files; the suite grew 402 → 723 tests across the milestone.
**Agent eligibility for solving:** four filters — active status, desk assignment, schedulable job title, and `workingDaysKnown` (parseable BambooHR field 4517)

**Known issues after v1.2:**
- **⚠ An undocumented third attempt at schedule consistency was built and reverted 2026-08-19/20** — discovered 2026-08-25 during v1.3 architecture research; recorded in no planning document until now. Four feature commits, all ancestors of HEAD, all reverted: `7861b83` (preferred start time as an **anchor, not a floor** — fixing the defect that `honourPreferredStartTime` only penalises slots *before* the preference), `9f4a96f` (consistent break offset across an agent's week), `9207ceb` (consistent daily start with a solver-chosen anchor), `6fb78c7` (per-agent start and break-offset spread reporting — effectively the drift report). Two supporting perf commits shared one agent-day grouping across nine constraints. Reverted by `2da56fd`, `3aba7c6`, `65ccb34`, `ac395f2`, `b6188c8`, `12315ed`. **Why it was unwound is not recorded in any commit body and remains an open question** — the revert message explains only why the migration was retained. This is the closest prior art to v1.3 and must be understood before re-implementing: it is either a recoverable asset or a warning, and which one is not yet known.
- ~~**⚠ `V38__add_consistent_start_weight.sql` is an orphaned live migration.**~~ — **RESOLVED in v1.3 Phase 17, as intended.** The column was adopted rather than duplicated: `ConstraintWeights.consistentStartWeight` maps it (`@Column(name = "consistent_start_weight")`, default `ofSoft(2)`), the solver reads it, `ConstraintWeightsDto` exposes it, and V49 set its shipped default after a threshold-first seeded A/B — which returned an honest null result (construction-heuristic plateau) and landed on values identical to the incumbents, now backed by redone per-agent-day arithmetic instead of V38's unexamined sizing.
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
| BambooHR key rotation gate bypassed | Operator directive 2026-07-29, accepted risk | — Operator-owned; removed from GSD tracking 2026-08-25 at operator request |
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
| One coverage validator, two callers — the shift-library report endpoint and the mode-switch refusal (Phase 14, D-08) | The report an operator reads and the refusal that blocks them can never disagree, because they are the same computation. Verified at UAT: the report named four uncovered windows and the refusal named the same four verbatim | ✓ Good |
| `SHIFT → SLOT` is unconditional, with no confirmation dialog (Phase 14, D-12) | A dialog was rejected deliberately, citing audit I-3 — a confirm() that fires on a *non-destructive* action trains operators to click through the ones that matter. `switchSchedulingMode` only validates when the target is `SHIFT` | ✓ Good |
| Mode switch during a RUNNING solve refuses rather than terminating the solve (Phase 14, T-14-22) | Discarding minutes of solver work on a click that doesn't look destructive is a self-inflicted availability loss — and `STOPPED` is a legitimate accept state, so the loss wouldn't even read as one | ✓ Good |
| ~~Shift templates have no delete endpoint; retirement is an effective-date range edit (Phase 14, T-14-14)~~ — **superseded 2026-09-03** | True as shipped in Phase 14, but Phase 15's `81117e3` ("a delete control for the shift library") added `DELETE /desks/{deskId}/shift-templates/{id}` and this row was never updated. The *intent* survives as a guarded delete rather than an absent one: the endpoint refuses with a 409 when the template is referenced by any `agent_shift_assignment` (Phase 15) or any `agent_usual_shift` (Phase 16, plan 16-02), directing the operator to retire it instead. An unreferenced template is hard-deleted along with its break bands. Retirement itself is still never blocked | ⚠ Revisit — the stale wording survived two milestones and propagated into `16-CONTEXT.md`; Phase 16 only caught it because the FK cascade forced the question |
| Contracted-hours mismatch is advisory on save, blocking only at the mode switch (Phase 14, D-06) | Operators build libraries incrementally; a hard block at save time would make the intermediate states unreachable. The fatal case is still caught before it can reach the solver | ✓ Good |
| V39 edited in place to fix G-14-1 rather than superseded by a V40 (2026-08-26) | V39 was unreleased, so the forward-only rule was not yet engaged; a corrective V40 would have permanently encoded a type mismatch that no environment had consumed | ✓ Good — and the migration-coverage gap it exposed is now closed by `MigrationEntityConsistencyTest` + `PostgresBackedTest` |
| Coupling settled empirically by spike *before* any phase was planned (v1.3, `milestones/v1.3-research/SPIKE-COUPLING.md`) | Option C (filtered value range) compiled, passed `FULL_ASSERT` clean, and reported infeasible schedules as `0hard/0soft` optimal on 8/8 seeds. A hard `ConstraintStream` coupling (Option A) was the only sound choice, and knowing that before planning is what kept Phase 15 from becoming a third abandoned attempt | ✓ Good — the single highest-leverage decision in the milestone |
| Shift-mode solves that cannot succeed are REFUSED before running, not allowed to degrade (Phase 15) | An irreducible hard score mislabelled "Shift envelope compliance" is indistinguishable from a solver that needs more time. Refusing names the date, the per-hour shortfall and the operator's levers instead | ✓ Good — though it took four rounds (calendar-blind coverage, band-composition blindness, day-wide-only comparison, forced-occupancy) to get the gate's own logic right |
| Refusal advice checks the live weights before recommending a lever (Phase 15, G-15-24) | The gate recommended raising the over-allocation ceiling — the single most destructive available action on a desk where unassigned seats are hard-weighted. It now reads `ConstraintWeights` first and withdraws the advice, naming the consequence | ✓ Good — a refusal that gives dangerous advice is worse than no refusal |
| USHF-05's write-path table enforced by a static source scan, not by review (Phase 16, D-14) | v1.2's I-2 stayed open across two audits precisely because a second entry point bypassed a guarantee. A set-equality test over a source-derived class list, proven red twice, makes that failure mode structural rather than a matter of attention | ✓ Good — the most transferable pattern this milestone produced |
| Consistency's hard-component guard enforced at save time, not asserted in the constraint (Phase 17) | CONS-04 ("soft only — never makes a feasible schedule infeasible") becomes impossible to violate by configuration, rather than being a property someone must remember to preserve | ✓ Good |
| v1.3's XCUT-04 benchmark reported as a null result (Phase 17, `17-BENCHMARK.md`) | The consistency-weight A/B hit a construction-heuristic plateau — neither a win nor a loss against the pre-committed threshold. Reported as that, with median and full min/max spread, rather than reframed as a win | ✓ Good — the same discipline that correctly withdrew Phase 12 |
| Template delete shipped as a *guarded* delete, superseding Phase 14's no-delete stance (Phase 15, `81117e3`) | Retire-only stranded typos, duplicates and probe rows in the library forever; the operator asked for the control during UAT. The guard refuses with 409 when any agent-day assignment *or* any stored usual shift references the template, so nothing that ever shaped a roster can be destroyed | ✓ Good — SHLB-04 is satisfied more strongly than before, but the stale "no delete endpoint" wording survived into `14-VERIFICATION.md` for 18 days after `PROJECT.md` had already corrected it |
| v1.5 proves midnight correctness on constructed scenarios, with one small live desk (Phil-US, 48 agents) demoted to a drift guard (2026-09-30) | v1.4's captured-live-desk golden file was over-sensitive and under-powered on the one boundary it existed to protect. A scenario chosen by the property under test has a knowable answer | ✓ Good — the match-count table caught a silent non-join that score alone passed |
| `DayWindow`'s nine midnight-implicit statics demoted to private, not deprecated (Phase 19) | A missed call site becomes a compile error rather than a judgement call. 83 production sites moved in one revertible range, proven byte-identical at 00:00 by a frozen oracle | ✓ Good |
| The 00:00-only day-start gate lifted as Phase 20's final commit, after every join was migrated and guarded | Exposing non-midnight anchors before the solver agreed on business date would have let operators create silently wrong schedules | ✓ Good |
| `agent_shift_assignment` gets no `business_date` column; its `date` already IS the business date (Phase 20, D-05) | Avoids a second source of truth, held by a derivation-chain guard test (D-06) | ✓ Good |
| Minimum rest is HARD, with a pre-solve refusal separate from the in-solve constraint, and per-date waivers that must be disclosed (Phase 22) | Overnight shifts without it would create a way to produce illegal back-to-back rosters scoring `0hard`. A waiver that hides a violation is worse than no waiver | ✓ Good |
| Rest-gap arithmetic routed through one wrap-aware primitive, `DayWindow.anchoredWrappedEndMinute`, held single by `RestGapArithmeticGuardTest` (Phase 23) | G-1 was the second inline copy of a composition that was wrong on overnight predecessors | ✓ Good — but the guard is evadable (23 WR-01, open) |
| v1.5 closed under `override_closeout` with Phases 18–23 verification digests stale (2026-10-08) | All 29 requirements were satisfied, with 7/7 seams and 5/5 flows confirmed by integration checks and targeted tests. Re-verifying six phases was judged not worth blocking the close | ⚠ Revisit — the third consecutive override close. Stale verification is debt with a known cost, and the CR-01 miss shows that recorded findings can slip through an audit |
| v1.3 closed under `override_closeout` with 10 artifacts acknowledged (2026-09-21) | All 43 requirements satisfied and zero integration gaps, but 3 debug sessions remain `diagnosed` rather than fixed and 7 deferred items — including two known functional gaps — carry forward by operator ruling OR-2 | ⚠ Revisit — v1.2 closed the same way and its I-2 is now three audits old; two consecutive override closeouts is how debt becomes permanent |

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
*Last updated: 2026-10-08 after v1.5 milestone close — 29/29 requirements validated across Phases 18–24 plus quick task 261008-eby (CR-01). Milestone audit `tech_debt` after four passes (G-1, N-1/N-2, CR-01 each found and closed), 7/7 seams, 5/5 flows; closed under `override_closeout` with 3 artifacts acknowledged and Phases 18–23 verification digests stale. Next: `/gsd-new-milestone`. Previously: 2026-10-08 after Phase 23 (gap closure G-1). (gap closure G-1) — rest gap after an overnight predecessor measured correctly (60, not 1500); UAT D5 passed live (pre-solve refusal at 60 min), with the caveat that the API's template validation means no live overnight template wraps its business day, so the live run cannot tell the old arithmetic from the new — the wrapped case is proven by unit fixtures only. All v1.5 phases complete. Previously: 2026-10-08 after Phase 24 (gap closure N-1/N-2) — shift-library validation, envelope/usual-shift repair, start-mix targets and the Agent Allocation demand rows now all key on the stored business date; OVNT-05/06/07, SOLV-07, BDAY-02/05 re-satisfied at non-00:00 anchors. Phase 23 (G-1) awaits human UAT before v1.5 can close. Previously: 2026-10-04 after Phase 22 (Minimum Rest) — all 29 v1.5
requirements Complete. Previously: 2026-10-02 after Phase 20 — all eight requirements
(SOLV-01..07, BDAY-07) satisfied, verified 8/8 must-haves after two gap-closure rounds; 12/12 plans.
Round 2 (plan 20-12) found four repository-level truncation sites that fed business-date period bounds
into calendar-date finders, and closed them behind one shared `BusinessDayPeriodLoader`; the
accept-path case was silently corrupting re-anchored desks' persisted snapshots. SOLV/BDAY stay in
`REQUIREMENTS.md` rather than moving to Validated above until v1.5 closes (Phases 21–22 outstanding).
Previous update: 2026-09-30 at v1.5 milestone start (Overnight Shifts & Business Dates). v1.4, which
attempted the same capability, was cancelled the same day before shipping — all 32 commits unwound,
one salvaged (the `ScheduleExportService` roster-cell end-tracking fix), state preserved at tag
`rescue/phase-18-unwind-20260930`. v1.5 keeps v1.4's settled decisions (desk-level day start,
minimum rest hard, guard tests before re-anchoring) and changes two things: the regression proof
moves from four captured live desks to constructed midnight-spanning scenarios plus one small live
drift guard, and the Phil-US migration is deferred so the capability stays desk-agnostic. Previous
update: 2026-09-30 after Phase 18 (Business-Day Foundation & Guards) — all six BDAY requirements
validated; desk `day_start` and timeslot `business_date` shipped gated to a provable no-op; 38 threats
closed with `threats_open: 0`; Nyquist validated PARTIAL (1 manual-only item, UAT-passed). Prior entry:
2026-09-21 at v1.3 milestone close — 43/43 requirements validated across Phases 14–17; milestone audit
`tech_debt` with zero integration gaps across six seams and four E2E flows; closed under
`override_closeout` with 10 artifacts acknowledged. 999.4 / 999.5 / 999.6 / 999.9 remain deferred*
