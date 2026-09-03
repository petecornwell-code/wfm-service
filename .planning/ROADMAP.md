# Roadmap: WFM Service

## Milestones

- ⚠ **v1.0 AWS Deployment** — Phases 1–4 (partially shipped 2026-04-21; IAM blocker — see Backlog 999.1–999.3)
- ⚠ **v1.1 Schedule Quality & Reporting** — Phases 5–8 (closed early 2026-07-29; 5–6 shipped, 7–8 deferred — see Backlog 999.4–999.6)
- ✅ **v1.2 Unified Agent Provisioning** — Phases 9–13 (shipped 2026-08-25; override closeout — see Known Gaps → Backlog 999.9)
- 🚧 **v1.3 Shift-Based Scheduling & Consistency** — Phases 14–17 (in progress)

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

### 🚧 v1.3 Shift-Based Scheduling & Consistency (In Progress)

**Milestone Goal:** An agent works a recognisable, repeating shift — not a slot pattern the optimiser
reassembles from scratch every week.

**Settled before phase planning began (do not re-open):**

- **Coupling mechanism:** `SPIKE-COUPLING.md` resolved the research disagreement empirically — two
  independent `@PlanningEntity` classes coupled by a `ConstraintStream` hard constraint (Option A) is
  sound on 8/8 seeds; a filtered value range (Option C) compiles and passes `FULL_ASSERT` clean while
  reporting infeasible schedules as `0hard/0soft` optimal. This is a hard constraint, not a live
  question, for Phase 15.

- **The reverted third attempt:** four full-stack commits (`7861b83`, `9207ceb`, `9f4a96f`, `6fb78c7`),
  1,928 insertions including 1,301 lines of tests, built 2026-08-19/20 and reverted within one minute
  — confirmed as speculative off-roadmap work built while blocked on UAT, cleanly reverted as scope
  discipline, not a technical failure. Treated as candidate salvage material inside Phase 17, not as a
  standalone investigation phase.

- **Soft-quality plateau:** the coupling spike found Option A sound but never reaching the known
  `0soft` optimum on its toy fixture (settling `-10soft`/`-5soft`). Operator ruling: ship the sound
  model, measure the real gap at realistic scale in Phase 15's benchmark, and report it as a finding —
  no custom-move remedy phase is scoped into v1.3.

- [x] **Phase 14: Shift Library & Scheduling Mode** - Operators define per-desk shift templates and switch a desk into shift-scheduled mode, validated against demand and contracted hours (completed 2026-08-26)
- [x] **Phase 15: Shift Envelope, Breaks & Library Generation** - The solver assigns one shift per agent-day via a hard-constraint coupling proven sound and benchmarked honestly, breaks are distributed rather than simultaneous, and a starting library can be suggested from demand (completed 2026-08-27)
- [ ] **Phase 16: Usual Shift Storage** - Each agent's usual shift per weekday is stored, settable by upload or inline edit, and visible everywhere agent data is displayed
- [ ] **Phase 17: Consistency Constraint & Drift Reporting** - The solver is nudged toward each agent's usual shift within a tunable tolerance, and drift is reported per agent/date

## Phase Details

### Phase 14: Shift Library & Scheduling Mode

**Goal**: An operator can define a desk's shift library and switch that desk into shift-scheduled
mode, with both edits validated against real demand and contracted hours before they ever reach the
solver.
**Depends on**: None (first phase of v1.3; extends the existing `Desk`/`Specialization` desk-scoped-list pattern)
**Requirements**: SHLB-01, SHLB-02, SHLB-03, SHLB-04, SHLB-05, SHLB-06, MODE-01, MODE-02, MODE-03, MODE-04, MODE-05

**Success Criteria** (what must be TRUE):

1. Operator can create, edit, and retire per-desk shift templates — each with a start time, end time, break placement rule, valid weekdays, and an effective date range — without corrupting schedules that already reference a retired template, and every template is visible in the desk's shift-library admin view. (SHLB-01, SHLB-02, SHLB-03, SHLB-04, XCUT-01)
2. At shift-template definition time, the operator sees which of the desk's staffing-demand windows no combination of library shifts can cover, and which shift templates cannot match any agent's effective contracted hours for a valid weekday — reported before a solve is ever attempted, not discovered by one. (SHLB-05, SHLB-06)
3. Every existing desk defaults to slot-scheduled with zero behaviour change; an operator can switch a desk to shift-scheduled mode from desk configuration, and the switch is refused with the specific uncovered demand windows named when the library can't cover demand. (MODE-01, MODE-02, MODE-03)
4. Switching a desk's mode never alters or invalidates an already-accepted schedule for that desk, and a solve on a slot-scheduled desk produces the same result it did before this phase shipped. (MODE-04, MODE-05)
5. Every one of the 19 existing solver constraints is classified mode-agnostic, mode-gated, or needing-a-shift-mode-variant in a table produced as a deliverable of this phase — analysis starts here since the mode concept now exists, even though the four break constraints it identifies aren't actually mode-gated until Phase 15. (XCUT-05, partial)

**Notes:**

- This phase touches no solver code. `ShiftTemplate` is a plain problem fact, a structural sibling of `Specialization` (desk-scoped list, separate table, `desk_id` FK — not nested inside `Desk`).
- Next Flyway migration is **V39**; this phase needs at minimum a `shift_template` table and a `desk.scheduling_mode` column (`SLOT`/`SHIFT`, default `SLOT`) — exact migration numbering is a plan-time decision, confirmed against the actual latest-applied version before writing, not assumed from this document.
- SHLB-05's coverage validator and MODE-03's refusal message are the same check reused twice — build it once (extending `runPreSolveValidation`'s existing `ErrorDetail` pattern) and call it from both the shift-library editor and the mode-switch endpoint.

**Research needed at plan time**: No — shift-library CRUD mirrors `Specialization`'s existing desk-scoped list pattern; the mode switch mirrors `minimumStaffing`'s existing configuration-driven toggle pattern. Both are proven in-codebase patterns (SUMMARY.md §Research Flags).

**Plans**: 6/6 plans executed (5 waves; tracer-first, one `blocking-human` decision gate on the one-way D-11 migration)

Plans:
**Wave 1**

- [x] 14-01-PLAN.md — Tracer: end-to-end "create and see one shift template" through migration, entity, repository, service, controller, API client and a new Shift Library page (SHLB-01, MODE-01)
- [x] 14-02-PLAN.md — XCUT-05 constraint classification with a code-derived completeness test, plus MODE-05's structural proof that no production solver file changed (MODE-05)

**Wave 2** *(blocked on Wave 1 completion)*

- [x] 14-03-PLAN.md — Template lifecycle: full save-time validation, the D-02 grid check, edit and retire, era identity and non-overlap, era-aware list ordering (SHLB-01…04)

**Wave 3** *(blocked on Wave 2 completion)*

- [x] 14-04-PLAN.md — The shared coverage / grid / contracted-hours validator and its read-only report endpoint — D-08's one implementation, two callers (SHLB-05, SHLB-06)

**Wave 4** *(blocked on Wave 3 completion)*

- [x] 14-05-PLAN.md — Scheduling-mode switch: the 409 in-flight guard, the MODE-03 coverage gate, the single-column write proving MODE-04 (MODE-02, MODE-03, MODE-04)

**Wave 5** *(blocked on Wave 4 completion)*

- [x] 14-06-PLAN.md — Operator surface: shift-library editor, coverage panel, SHLB-06 advisory, mode toggle, and the read-only mode column in Desk Management (SHLB-01…04, MODE-02, XCUT-01)

**UI hint**: yes

### Phase 15: Shift Envelope, Breaks & Library Generation

**Goal**: On a shift-scheduled desk, the solver assigns each agent exactly one shift per day from
that desk's library and never seats them outside it — a coupling proven sound against a real fixture,
not assumed, with its effect on schedule quality measured honestly rather than discovered at UAT;
breaks are distributed rather than taken simultaneously, the result is legible as shifts in the
Agent Allocation view, and an operator can have a starting library suggested from demand instead of
composing one by hand.
**Depends on**: Phase 14 (needs a valid `ShiftTemplate` FK target and the `scheduling_mode` field to gate on)
**Requirements**: ENVL-01, ENVL-02, ENVL-03, ENVL-04, ENVL-05, ENVL-06, ENVL-07, ENVL-08, ENVL-09, ENVL-10, SHLB-07

**Success Criteria** (what must be TRUE):

1. On a shift-scheduled desk, the solver assigns each working agent exactly one shift per day from the desk's library, via a new `AgentShiftAssignment` planning entity, visible in the roster and accepted-schedule view; an agent's specialization can still vary between timeslots inside that shift. The Agent Allocation view groups agents under their assigned shift — each group naming the shift and its headcount — rather than listing every agent flat, so a shift-mode solve can be sanity-checked by eye; a slot-scheduled desk renders exactly as it does today. (ENVL-01, ENVL-03, ENVL-10, XCUT-01)
2. An agent is never seated in a timeslot outside their assigned shift's envelope — enforced as a hard constraint (`shiftEnvelopeCompliance`), and confirmed by an independent ground-truth check that walks the solved schedule outside the score director, not by trusting the reported score alone. (ENVL-02, ENVL-07)
3. An agent's working day is contiguous apart from their break; break placement comes from the shift template as a structural attribute rather than from the four emergent break constraints, which are mode-gated off for shift-scheduled desks and provably unchanged (same test suite, still green) for slot-scheduled desks — completing Phase 14's classification table. (ENVL-04, ENVL-05, XCUT-05)
4. Agents sharing a shift do not all break at once: a shift template carries one or more **break bands** (offset plus capacity), band choice is a planning variable resolved per agent-day inside the assigned shift, and the previously inert `Break clustering` constraint is given a real body that penalises agents-on-break in a timeslot exceeding `breakClusterThresholdPct` of that timeslot's assigned agents. Demonstrated on a fixture where a single-band library measurably starves a mid-shift timeslot and a multi-band library does not. (ENVL-08, ENVL-09)
5. The construction heuristic reaches a feasible initial solution on a shift-scheduled desk with no pre-assignment pipeline, using two explicitly-scoped sequential CH phases (shift choice, then seat choice), each `QueuedEntityPlacerConfig`/`EntitySelectorConfig` carrying both an `entityClass` and an `id`; any resulting `solverConfig.xml` change is validated by a test that actually builds a solver, not a Spring-context-free scoped run. (ENVL-06, XCUT-03)
6. A seeded, step-count-terminated A/B benchmark — median and full min/max spread reported, threshold pre-committed before the run — measures the shift model's schedule-quality effect against the slot model at realistic (~130%) over-allocation, and explicitly states the soft-score plateau (shift and seats must move together; a plain change-move neighbourhood only moves one at a time) as a measured finding, not a discovery left for UAT. (XCUT-04)
7. Operator can ask a desk for a **suggested shift library** computed from its staffing demand and its agents' effective contracted hours, and receives an editable draft — never an auto-applied library. The suggestion is produced by the same coverage predicate `ShiftLibraryValidationService` already uses, so a generated draft can never be one the validator then rejects, and running the existing SHLB-05/SHLB-06 validation over the draft reports zero uncovered windows for a desk whose demand is coverable at all. (SHLB-07)

**Notes:**

- **The coupling mechanism is settled, not re-litigated here.** `SPIKE-COUPLING.md` demonstrated Option A (two independent `@PlanningEntity` classes, coupled by a `ConstraintStream` hard constraint) sound on 8/8 seeds, and Option C (a filtered value range reading the shift's genuine planning variable) empirically unsound — it compiled, ran clean under `FULL_ASSERT`, and reported `0hard/0soft` while 9–14 of 24 seats sat outside their agent's envelope. Build Option A. A `SelectionFilter` layered on top of the hard constraint, as a pure search-efficiency tune, remains a legitimate later optimisation once Option A's own baseline is measured — not a replacement for the constraint.
- **Operator ruling on the soft-quality plateau (settled, not a phase to plan around):** ship the sound model as-is. Do not scope a custom-move remedy into this phase or this milestone — the spike's 24-entity toy fixture may overstate the real-scale gap, and Phase 12 already failed once by committing to a remedy before measuring. This phase's job is to measure the real gap honestly (success criterion 5) and report it, not to close it.
- **Renamed 2026-08-26, formerly "Phase 15: Shift Envelope & Coupling."** The old name described only the coupling; the phase now also owns break distribution (ENVL-08/09), shift-grouped allocation (ENVL-10), and library generation (SHLB-07). The former name deliberately still appears in Phase 14's shipped artifacts (`14-02-SUMMARY.md`, `14-VERIFICATION.md`, `14-02-PLAN.md`, `14-CONTEXT.md`, `XCUT-05-constraint-classification.md`) and in the `PHASE_15_OWNER` constant in `src/test/java/com/wfm/solver/ScheduleConstraintClassification.java` — those are historical records of what was verified at the time, and `14-VERIFICATION.md` item 21 asserts that constant matches its markdown mirror, so rewriting either would falsify a verified claim. The phase *number* is the stable identifier; if the constant is ever updated, update its markdown mirror in the same commit.
- **Shift-library generation (SHLB-07) sits here by operator decision (2026-08-26), and should be the phase's FIRST plan slice.** It is deliberately not solver work: generation reads `staffing_requirement` and `agent_day_hours` and reuses Phase 14's `covers()` predicate, so it depends on **Phase 14 only** and has no dependency on `AgentShiftAssignment`, the coupling constraint, or the CH config. Sequence it first so its effects are testable immediately, without waiting for the envelope work — that immediacy is the stated reason it was pulled forward rather than left to a later milestone. Scope note recorded honestly: this raises Phase 15 from 7 requirements to 11 and mixes a design-time feature into an otherwise solver-mechanics phase; the mitigation is that it shares no code path with the rest of the phase and can be planned, executed, and verified as an independent slice.
- **Why generation is tractable here (do not over-engineer it).** Contracted hours pin the duration: with every agent on 8h, a viable template is 8h net — a 9h span on the timeslot grid. Within an 08:00–21:00 operating window that is roughly five candidate envelopes times a handful of break offsets, so the candidate set is tens, not thousands. This is a small **set-covering** problem solvable exactly (Timefold is already in the stack; plain ILP or even exhaustive search over the candidate set is also viable). It does not need a heuristic, and it must not acquire its own quality-plateau argument.
- **Generation and break bands interact.** A single template with one break offset can never cover its own break hour — the break punches a hole nothing in that template fills. So any generated library is necessarily either two-or-more overlapping templates or one template with multiple bands (ENVL-08). If ENVL-08 lands first, generation can emit banded templates; if not, it emits overlapping pairs, as the hand-built StubHub (EN) library does today. Neither ordering blocks the other.
- **Break balancing: bands plus a real clustering constraint (operator ruling 2026-08-26).** Phase 14 shipped `shift_template.break_offset_minutes` as a *single* offset, so every agent on a shift breaks at the same minute. On the live StubHub (EN) library that means ~15 Early-shift agents all break 12:00–13:00, leaving that window's 8–12 FTE demand to Late-shift agents alone. The constraint that should flag this is inert: `breakClustering` is `penalizeConfigurable(a -> 0)`, a documented placeholder, while `breakClusterThresholdPct` is a live config knob wired to nothing. Ruling: implement **break bands** (a template carries N offsets with capacities; band choice is a planning variable per agent-day) **and** give `Break clustering` a real body. Both, not either — a real clustering constraint on top of a fixed offset gives the solver no freedom to move breaks, so it would only report an unavoidable penalty every solve. Rejected alternative: a continuous break *window* (earliest/latest offset with a free start time) — strictly more expressive, but it re-opens the four emergent break constraints this phase is trying to mode-gate off, and bands are enough to break up the clustering. Note the existing Out-of-Scope row "Minimum/maximum staffing caps inside a shift template" is about *staffing demand* and does not bar band capacity, which governs break distribution only.
- Band storage is likely a `shift_template_break_band` child table rather than more columns on `shift_template` — an N-per-template relationship. Confirm at plan time; it also affects the Phase 14 UI, which currently edits a single break offset per template.
- Break-as-structural-attribute and value-range filtering of shift choice by `AgentDayConfig.effectiveHours` belong in this phase, not a separate one — without the `effectiveHours` filtering, a shift template whose net duration doesn't match an agent-day's contracted hours makes `shiftEnvelopeCompliance` and the existing `contractedHoursOver`/`Under` constraints jointly unsatisfiable, unfixable by any amount of solver time (ARCHITECTURE.md §1).
- Next Flyway migration after Phase 14's additions is likely the `agent_shift_assignment` table — confirm the actual latest-applied version before writing it.

**Research needed at plan time**: Yes — the coupling *mechanism* is settled (see Notes), but the exact `ConstructionHeuristicPhaseConfig`/`QueuedEntityPlacerConfig`/`EntitySelectorConfig` XML nesting for two sequential CH phases is only MEDIUM confidence per ARCHITECTURE.md, and the spike found the `EntitySelectorConfig` needs both `entityClass` and `id` set or the mimic-selector reference resolves to the wrong entity. Verify against a fixture before writing the real `solverConfig.xml`.

**Plans**: 8/8 executed, plus 5 gap-closure plans (15-09…15-13, waves 1–3), 2 further gap-closure
plans (15-14…15-15, waves 1–2) for G-15-22 / G-15-29, and 5 more (15-16…15-20, waves 1–3) for the
seven remaining open gaps. The 15-09…15-13 set addresses UAT gap
**G-15-10** (blocker). Test 10 reached an irreducible hard score of -19 entirely on Shift envelope
compliance on the live Stubhub (EN) desk. Diagnosis is settled across three debug lanes
(`.planning/debug/`): envelope-blind minimum-staffing seat supply, a zero-slack value range with no
margin to absorb a missing seat, cost arbitrage onto the cheapest hard weight, and a report layer
that redraws the envelope around the violating seat so the breach cannot be displayed as one.

Plans:

- [x] 15-01-PLAN.md — Break bands become the template's break: V40 migration, `ShiftTemplateBreakBand`, any-band `covers()`, capacity advisory, migration-vs-entity guard (wave 1)
- [x] 15-02-PLAN.md — SHLB-07 suggested shift library: stateless generation endpoint, set cover, D-12 partial coverage and refusal (wave 2)
- [x] 15-03-PLAN.md — Shift envelope entity, two explicitly-scoped CH phases, `shiftEnvelopeCompliance`, and the build-a-solver test (wave 2)
- [x] 15-04-PLAN.md — ENVL-07 ground-truth walker outside the score director, plus the disagreement proof (wave 3)
- [x] 15-05-PLAN.md — Shift Library UI: repeatable break-band editor and Suggested Library draft panel (wave 3)
- [x] 15-06-PLAN.md — Band capacity hard cap, real `Break clustering` body, mode-gating six constraints, XCUT-05 closed (wave 4)
- [x] 15-07-PLAN.md — Accept-time shift snapshot (D-07) and shift-grouped Agent Allocation (wave 4)
- [x] 15-08-PLAN.md — Seeded A/B benchmark, CH-ordering arm, and `15-BENCHMARK.md` (wave 5)

*Gap closure for G-15-10 (blocker) — the phase's completion claim above stands only once these land:*

- [x] 15-09-PLAN.md — Envelope-aware seat supply: suppress filler seats where no live shift reaches, guarantee seats where one does, un-blind `ShiftModeFixtures`, correct the stale XCUT-05 row (wave 1)
- [x] 15-10-PLAN.md — Report layer reads the authoritative envelope and band instead of deriving both from seat gaps; envelope divergence becomes visible data; export and preference KPIs corrected (wave 1)
- [x] 15-11-PLAN.md — Pre-solve in-envelope seat-supply gate: refuse with named shortfall and levers rather than returning an unlabelled residual; zero-slack pinned as a deliberate invariant (wave 2)
- [x] 15-12-PLAN.md — Agent Schedule reads the authoritative shift; envelope breach and deliberately-unstaffed hours rendered visibly in Agent Allocation (wave 2)
- [x] 15-13-PLAN.md — Shape-complete end-to-end regression, characterising-test disposition, deferral record for the two latent defects left out of scope (wave 3)

*Gap closure for G-15-22 / G-15-29 (major, the same gap stated twice) — no automated guard exists on
solver quality, so a tuning change that wrecks convergence is discovered on a live desk. The guard
must be invariant-based, not score-based: a hard-score ceiling would itself be flaky (byte-identical
configuration gave 0 hard and -20 hard twenty minutes apart on 2026-09-01). Scoped deliberately
narrow by operator decision on 2026-09-01 — G-15-21, G-15-23, G-15-24, G-15-25, G-15-26, G-15-28 and
G-15-31 are excluded from this round:*

- [x] 15-14-PLAN.md — Solver quality guard: live-shape synthetic fixture, five seeded step-count-terminated solves through the shipped `solverConfig.xml`, three structural invariants (zero split shifts, zero edge breaks, every edge hour staffed) walked outside the score director, a median violation-COUNT ceiling, and the shipped weight defaults pinned — in the default suite, ungated (wave 1)
- [x] 15-15-PLAN.md — Red-proofs that each walker can fail on exactly its own injected defect, a mechanical demonstration that a zero weight blinds the violation-count table while the walker still sees the split, plus the documented solver comparison rule and closure of both gap entries (wave 2)

*Gap closure for the seven remaining open gaps (G-15-21, G-15-23, G-15-24, G-15-25, G-15-26, G-15-31,
G-15-32). Sequenced safest-first: the self-contained read-path and generator defects land in wave 1
alongside the gate's written-down calendar fix; the two gaps needing design work follow behind the
analysis that measures them. G-15-28 (weekend demand forecast) is excluded — it is DATA owned by the
operator, who is correcting it; G-15-10 remains a retest obligation, not new work; and OR-2's two
deferred items (breakBlockedHours enforcement, envelope-vs-operating-window validation) stay
deferred:*

- [x] 15-16-PLAN.md — The read path stops lying: accepted schedules report envelope violations derived from their own persisted snapshot instead of a mis-explained score director, `feasible` can no longer coexist with a named violated hard constraint, and a wrong HTTP verb answers 405 (G-15-32, G-15-26) (wave 1)
- [x] 15-17-PLAN.md — Suggested library stops emitting duplicate templates and stops placing breaks on the demand peak, with a round-trip guard tying the generator to the validator that judges it (G-15-23) (wave 1)
- [x] 15-18-PLAN.md — Seat-supply gate: one date-aware definition of covered supply shared by the blocking check and the advisory, plus refusal advice the gate has actually checked against the desk's live `unassignedAssignmentWeight` (G-15-21, G-15-24) (wave 1)
- [x] 15-19-PLAN.md — The analysis nobody has done: candidate within-day blocking rules evaluated against a labelled corpus with measured false-refusal counts, producing `15-SEAT-SUPPLY-GATE-ANALYSIS.md`. Changes no production behaviour by design (G-15-31) (wave 2)
- [x] 15-20-PLAN.md — Supply computed against each agent-day's own eligible pairs rather than a desk-wide union, proven band-composition-sensitive by the experiment that proved the old one blind, and the analysis's recommendation implemented or declined in writing (G-15-25, G-15-31) (wave 3)

**Planning note (2026-08-26):** CONTEXT.md D-09 amends the SHLB-07 note above. The
first-slice ordering stands, but the claim that the slice "shares no code path with the rest of the
phase" does **not**: D-01 drops `shift_template.break_offset_minutes`/`break_duration_minutes`, so
the band schema — migration, entity, editor and the `covers()` generalisation — lands *inside* the
generation slice, and only the solver-side `(template, band)` value range and the ENVL-09 clustering
constraint follow with the envelope work. Generating against columns dropped later in the same phase
would mean building it twice.

**UI hint**: yes

### Phase 16: Usual Shift Storage

**Goal**: Each agent's usual shift per weekday is stored, settable via the per-desk upload template
or inline roster editing, and visible everywhere agent scheduling data is displayed.
**Depends on**: Phase 14 (needs a valid `ShiftTemplate` FK target). Independently shippable relative to Phase 15 — this phase's data model and resolution service don't need the coupling constraint to exist yet.
**Requirements**: USHF-01, USHF-02, USHF-03, USHF-04, USHF-05, USHF-06

**Success Criteria** (what must be TRUE):

1. Each agent can have a stored usual shift per weekday, referencing a valid, active shift template from their own desk's library; an agent with no stored usual shift is scheduled without penalty rather than being nudged toward an arbitrary default. (USHF-01, USHF-04)
2. Operator can set usual shifts in bulk via a column in the per-desk upload template, resolved through the same shared `EnrichedColumnLayout` definition the parser and export already use — not a second, parallel column-layout source of truth. (USHF-02)
3. Operator can set and correct an agent's usual shift inline in the roster, through a single choke-point write method every caller goes through, mirroring `DeskAgentService.setDayHours`'s narrowly-scoped, single-weekday-edit shape rather than each caller writing the entity directly. (USHF-03)
4. Every write path that can change agent scheduling data — upload, inline edit, BambooHR refresh, desk clear, mode switch, and the solver itself — is enumerated in a table stating what must hold after that path runs, and verified against the real code rather than assumed. (USHF-05, XCUT-02)
5. A stored usual shift is visible everywhere agent scheduling data is displayed, including the roster and the Excel export — end-to-end traced (store → roster → export), not accepted on the strength of "the model is done." (USHF-06, XCUT-01)

**Notes:**

- `AgentUsualShift` is a new table (`agent_id, day_of_week, shift_template_id`, unique `(agent_id, day_of_week)`) modelled directly on `AgentDayHours`'s shape — not an extension of `AgentPreference`. The resolution service is a near-copy of `SolverService.resolvePreferences`'s standing-vs-weekly precedence shape, reading a different source table; do not conflate the two concepts (a preference is a free-form wish, a usual shift is a catalog-valued target with referential integrity to the shift library).
- This is this milestone's own named-risk repeat of v1.2's audit finding I-2 (a guarantee holding on the upload path only, discovered broken on Refresh two audits later) and I-1 (model built, view never migrated, costing Phase 13). Both are explicit success criteria here (4 and 5), not left implicit.
- The solver must never let solving overwrite the stored usual-shift *target* — only the *solved* shift assignment (`AgentShiftAssignment.shift`, Phase 15) is solver output. Keep target and result as distinct fields from day one, the same way `AgentDayHours` (contracted target) and `AgentAssignment` (solved seat) are already distinct today.

**Research needed at plan time**: No — directly parallels the proven `AgentDayHours`/`resolvePreferences` shape from v1.2 (SUMMARY.md §Research Flags).

**Plans**: 3/5 plans executed (3 waves)
**Wave 1**

- [x] 16-01-PLAN.md — Tracer: store → roster → export for one weekday (table, entity, repository, resolution service, choke-point write + endpoint, roster DTO, seven export columns, migration guards) — wave 1

**Wave 2** *(blocked on Wave 1 completion)*

- [x] 16-02-PLAN.md — Three-state resolution depth (D-01/D-02/D-04), the desk-move clear (D-12), and a delete-refusal guard on referenced templates — wave 2
- [x] 16-03-PLAN.md — Upload column group: template pre-fill and dropdown (D-09/D-10) then the parser and `clearDesk` wipe (D-06/D-07/D-08/D-11) — wave 2

**Wave 3** *(blocked on Wave 2 completion)*

- [ ] 16-04-PLAN.md — USHF-05 write-path table (9 rows), one test per path, and the D-14 structural completeness guard — wave 3
- [ ] 16-05-PLAN.md — Roster day-tile usual-shift line and inline `<select>` editor (D-15/D-16/D-17) — wave 3

**UI hint**: yes

### Phase 17: Consistency Constraint & Drift Reporting

**Goal**: The solver is nudged toward each agent's usual shift within an operator-tunable tolerance
band, without ever making an otherwise-feasible schedule infeasible, and the operator can see exactly
which agents drifted from their usual shift, on which dates, and by how much.
**Depends on**: Phase 15 (needs `AgentShiftAssignment` to compare against) and Phase 16 (needs the stored usual-shift target)
**Requirements**: CONS-01, CONS-02, CONS-03, CONS-04, CONS-05, CONS-06, DRFT-01, DRFT-02, DRFT-03, DRFT-04

**Success Criteria** (what must be TRUE):

1. The solver is penalised as a soft constraint — never able to make an otherwise-feasible schedule infeasible — for assigning an agent a shift that differs from their stored usual shift for that weekday, using a **target-deviation** formulation (every day compared against the stored target) rather than the reverted attempt's spread-based one; a solve never overwrites the stored usual-shift target itself. (CONS-01, CONS-04, XCUT-02)
2. Operator can configure, per desk, a tolerance band within which deviation carries zero penalty (a genuine dead zone, not a taper) and a consistency penalty weight — both validated via `SolutionManager.explain()`'s constraint-match breakdown against the existing soft-constraint hierarchy before a default ships. (CONS-02, CONS-03)
3. Where the consistency constraint scores two shifts equally, the agent's recorded `AgentPreference` start time decides between them, and the precedence between usual shift and preference is documented and observable — not implicit in relative constraint weights a reader would have to reverse-engineer. (CONS-05, CONS-06)
4. After a solve, the operator can see which agents were assigned a shift other than their usual one, on which dates, and by how much — distinguishing "no stored usual shift" from "usual shift honoured" — computed from the same distance calculation the consistency constraint uses, not a second implementation, and rendered as its own visible report panel. (DRFT-01, DRFT-02, DRFT-03, XCUT-01)
5. Operator can see which shift templates are most over-subscribed as agents' usual shifts, making the consistency-versus-fairness tension visible — deliberately without building any mitigation for it (fairness/rebalancing is out of scope, tracked at Backlog 999.4). A seeded A/B benchmark confirms the consistency constraint's weight doesn't regress coverage/break quality before it ships a default. (DRFT-04, XCUT-04)

**Notes — candidate salvage from the reverted third attempt (`7861b83`, `9207ceb`, `9f4a96f`, `6fb78c7`, all confirmed reverted 2026-08-20, cause confirmed unrelated to code correctness — speculative off-roadmap work reverted as scope discipline):**

- **Transfers as-is:** `7861b83` (preferred start time as an anchor rather than a floor) is a straight bug fix to the existing `honourPreferredStartTime` constraint, independent of shift architecture — apply it directly as part of CONS-05/CONS-06's precedence work.
- **Transfers with rework:** the `ConstraintWeights` → DTO → service → `ConstraintWeightsPage.tsx` plumbing (relevant to CONS-02/CONS-03's per-desk weight/tolerance config) and the spread-report API + UI shape (relevant to DRFT-01/DRFT-03's report surface) are reusable scaffolding — rework the underlying calculation, not the plumbing shape.
- **Needs reformulation, do not resurrect verbatim:** the abandoned constraint itself was **spread-based** (penalised only by an agent's two extreme days across the week) — its own javadoc names the flaw: "the search sees a plateau, not a gradient." This milestone's **target-deviation** formulation (every day compared against a stored target) does not share that flaw and is what criterion 1 requires.
- **Schema:** `V38__add_consistent_start_weight.sql` is applied on dev and inert (nothing in `src/` reads `consistent_start_weight`). Adopt this existing column for the consistency weight rather than adding a colliding duplicate under a new name.
- **Process:** whatever the prior attempt's original failure mode actually was, "reverted six commits within one minute" makes the one certain lesson procedural — this constraint must go through the normal CI/deploy gate end to end before merge, not be fast-tracked because "the pattern already compiled once."

**Research needed at plan time**: No dedicated research phase — the `groupBy(agent, date, ...)` pattern is confirmed to compile from the reverted commits, and weight validation via `SolutionManager.explain()` is an existing, already-used mechanism (`SolverService.runPreSolveScoreDiagnostic`). Confirm the salvage-material rework scope (Notes above) during planning.

**Plans**: TBD
**UI hint**: yes

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

**Also required here** (carried from Phase 6 debt):

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

### Phase 999.7: BambooHR Credential Rotation & Scrub — REMOVED FROM TRACKING

Removed from the GSD backlog on 2026-08-25 at operator request. Ownership sits with the operator
outside this planning system; the ID is retired rather than reused so 999.8/999.9 keep their
existing references.

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
