# Requirements: WFM Service — v1.3 Shift-Based Scheduling & Consistency

**Defined:** 2026-08-25
**Core Value:** Scheduling managers can produce optimised, constraint-aware agent schedules in minutes instead of hours — without spreadsheets.
**Milestone goal:** An agent works a recognisable, repeating shift — not a slot pattern the optimiser reassembles from scratch every week.

## Context

Requirement IDs continue the project's existing scheme. Categories are new to this milestone.
Phase numbering continues from **14**. Next Flyway migration is **V39** (schema head is V38).

Three prior attempts at shift or consistency semantics exist in this codebase. Two were abandoned
(`BreakAwareConstructionPhase`, now a no-op; Phase 12's Atomic Shift Move, withdrawn and reverted),
and a third — four full-stack commits including 1,301 lines of tests — was built and reverted
2026-08-19/20. Git archaeology during roadmap creation confirmed this as speculative off-roadmap
work built while blocked on UAT, then cleanly reverted as scope discipline — not a technical
failure. See `.planning/research/SUMMARY.md` §"A Third, Previously Undocumented Prior Attempt" and
`.planning/ROADMAP.md` Phase 17 Notes for what transfers, what needs rework, and what needs
reformulation.

The coupling mechanism was settled empirically by `.planning/research/SPIKE-COUPLING.md`:
**a hard constraint, not a filtered value range.** A filtered value range compiles and runs clean
under `FULL_ASSERT` while reporting infeasible schedules as `0hard/0soft` optimal. That finding is
load-bearing for ENVL-02 and ENVL-07, and is not to be revisited without new evidence.

## v1.3 Requirements

### Shift Library (SHLB)

- [x] **SHLB-01**: Operator can define a per-desk library of shift templates, each with a start time, an end time, and a break placement rule
- [x] **SHLB-02**: Operator can set which weekdays each shift template is valid on
- [x] **SHLB-03**: Operator can give each shift template an effective date range, so a desk's shift set can change without rewriting history
- [x] **SHLB-04**: Operator can edit and retire shift templates without corrupting schedules that already reference them
- [x] **SHLB-05**: The system validates a desk's shift library against that desk's staffing demand curve and reports which demand windows no combination of library shifts can cover
- [x] **SHLB-06**: A shift template whose net working duration cannot match any agent's effective contracted hours for a valid weekday is reported to the operator at definition time, not discovered at solve time
- [x] **SHLB-07**: Operator can request a suggested shift library computed from the desk's staffing demand and its agents' effective contracted hours — returned as an editable draft that is never applied automatically, and derived from the same coverage predicate the library validation already uses, not a second implementation

### Scheduling Mode (MODE)

- [x] **MODE-01**: Each desk is either shift-scheduled or slot-scheduled, and existing desks default to slot-scheduled with no behaviour change
- [x] **MODE-02**: Operator can switch a desk to shift-scheduled mode from the desk configuration UI
- [x] **MODE-03**: Switching a desk to shift-scheduled mode is refused, with a message naming the uncovered demand windows, when its shift library cannot cover that desk's demand (SHLB-05)
- [x] **MODE-04**: Switching a desk's mode does not alter or invalidate any already-accepted schedule for that desk
- [x] **MODE-05**: A solve on a slot-scheduled desk produces the same result it does today — mode is additive, not a rewrite

### Shift Envelope (ENVL)

- [x] **ENVL-01**: On a shift-scheduled desk, the solver assigns each working agent exactly one shift per day from that desk's library
- [x] **ENVL-02**: An agent is never assigned a seat in a timeslot outside their assigned shift envelope, enforced as a hard constraint
- [x] **ENVL-03**: An agent's specialization may still vary between timeslots within their shift
- [x] **ENVL-04**: An agent's working day on a shift-scheduled desk is contiguous, apart from their break
- [x] **ENVL-05**: Break placement on a shift-scheduled desk comes from the shift template rather than from the four emergent break constraints
- [x] **ENVL-06**: The solver reaches a feasible initial solution on a shift-scheduled desk without any pre-assignment pipeline
- [x] **ENVL-07**: A shift-mode solve reports a score that agrees with an independent check of the resulting schedule — no schedule is ever reported feasible while agents sit outside their envelopes
- [x] **ENVL-08**: A shift template defines one or more break bands (offset plus capacity), and the solver assigns each agent-day to exactly one band within its shift — so agents sharing a shift do not all break simultaneously
- [x] **ENVL-09**: Break clustering is enforced by a constraint that actually penalises concentration — agents on break in a timeslot exceeding `breakClusterThresholdPct` of that timeslot's assigned agents — replacing the `penalizeConfigurable(a -> 0)` placeholder
- [x] **ENVL-10**: On a shift-scheduled desk, the Agent Allocation view groups agents under the shift they were assigned, each group naming the shift and its headcount; a slot-scheduled desk is unchanged

### Usual Shift (USHF)

- [x] **USHF-01**: Each agent can have a stored usual shift per weekday, referencing a shift template from their desk's library
- [x] **USHF-02**: Operator can set usual shifts in bulk via a column in the per-desk upload template
- [x] **USHF-03**: Operator can set and correct an agent's usual shift inline in the roster
- [x] **USHF-04**: An agent with no stored usual shift is scheduled without penalty rather than being forced toward an arbitrary default
- [x] **USHF-05**: Every write path that can change agent scheduling data — upload, inline edit, BambooHR refresh, desk clear, mode switch — leaves usual-shift data in a defined, documented state
- [x] **USHF-06**: A stored usual shift is visible everywhere agent scheduling data is displayed, including the roster and the Excel export

### Consistency (CONS)

- [x] **CONS-01**: The solver is penalised for assigning an agent a shift that differs from their stored usual shift for that weekday
- [x] **CONS-02**: Operator can configure a tolerance band per desk, within which deviation from the usual shift carries no penalty at all
- [x] **CONS-03**: Operator can configure the consistency penalty weight per desk
- [x] **CONS-04**: Consistency is a soft constraint — it never makes an otherwise-feasible schedule infeasible
- [x] **CONS-05**: Where the consistency constraint scores two shifts equally, an agent's recorded `AgentPreference` start time decides between them
- [x] **CONS-06**: The precedence between usual shift and agent preference is documented and observable, not implicit in relative weights

### Drift Reporting (DRFT)

- [x] **DRFT-01**: After a solve, the operator can see which agents were assigned a shift other than their usual one, on which dates, and by how much
- [x] **DRFT-02**: The drift report distinguishes an agent with no stored usual shift from an agent whose usual shift was honoured
- [x] **DRFT-03**: The drift report is derived from the same distance calculation the consistency constraint uses, not a second implementation
- [x] **DRFT-04**: Operator can see which shift templates are most over-subscribed as agents' usual shifts, making the consistency-versus-fairness tension visible

## Cross-Cutting Requirements

These apply to every phase and are verification criteria, not features. They exist because this
project has already been burned by each one.

- [x] **XCUT-01**: Every phase that writes shift or usual-shift data verifies that the written value is visible to the operator in every surface that displays it — roster, export, accepted-schedule view, drift report. *(v1.2 audit I-1: the model was built and the view never migrated, costing an entire extra phase.)*
- [x] **XCUT-02**: Every guarantee is verified on every reachable write path, not only the one its phase built. *(v1.2 audit I-2: open across two consecutive audits because a second entry point bypassed the merge engine.)*
- [x] **XCUT-03**: Any change to `solverConfig.xml` is validated by a test that actually builds a solver. *(No test under `src/test/java/com/wfm/solver/` currently does, and the coupling spike found a solver-build failure that would therefore have shipped silently.)*
- [x] **XCUT-04**: The shift model's effect on schedule quality is judged by seeded, step-count-terminated A/B runs reporting median **and** full min/max spread, against a threshold committed before the run, including at realistic (~130%) over-allocation. *(Phase 12 produced a +0.25h median inside a 5.00h noise spread and was withdrawn.)*
- [x] **XCUT-05**: Every one of the existing constraints is explicitly classified as mode-agnostic, mode-gated, or needing a shift-mode variant — no constraint is left unclassified. *(A test suite where every fixture is single-mode is structurally blind to interaction bugs.)*

## Deferred to a Future Milestone

Tracked, not in this roadmap.

### Fairness (→ Backlog 999.4)

- **FAIR-01**: Shift bidding, rotation, or automated reshuffle of over-subscribed shifts
- **FAIR-02**: Weekend-position fairness (QUAL-02) and day-to-day hours consistency (QUAL-03)

DRFT-04 makes the fairness tension *visible* in v1.3; it deliberately builds no mitigation.

## Out of Scope

Explicitly excluded, with reasoning, to prevent re-adding.

| Feature | Reason |
|---------|--------|
| Real-time adherence monitoring | Different axis entirely (clock-time vs. schedule) and requires a time-and-attendance data source this project does not have |
| Shift-level skill or queue restriction | Contradicts ENVL-03 — specialization assignment deliberately stays inside the shift envelope |
| Minimum/maximum staffing caps inside a shift template | Staffing demand is an already-solved subsystem; a second signal creates two sources of truth |
| Predictive-scheduling compliance machinery | The researched fair-workweek laws are US state and municipal; this is a single EU/UK internal tenant |
| Custom Timefold moves (atomic shift move or successor) | Phase 12 built exactly this and it was withdrawn. The coupling spike confirms a soft-quality plateau that a combined shift-plus-seats move would target — but reopening it needs its own evidence-led decision, not inheritance by assumption. Operator ruling (2026-08-25): benchmark it in Phase 15, decide later — no remedy phase scoped into v1.3. See Risks below |
| Timefold version bump | Research confirms 1.16.0 supplies every needed primitive; bumping crosses into 2.0 paid-tier risk for zero capability gain |
| Retiring the slot model | Per-desk optionality is the whole pilot strategy — the fallback must remain |

## Known Risks Carried Into the Roadmap

| Risk | Evidence | Consequence if ignored |
|------|----------|------------------------|
| **Soft-quality plateau** | The coupling spike found Option A sound on all 8 seeds but never reaching the known `0soft` optimum (settling `-10soft`/`-5soft`) — improving soft needs a shift and its seats to move together, which change-moves cannot do | The milestone ships a correct but mediocre optimiser. The obvious remedy is the custom move Phase 12 already failed at. Phase 15's XCUT-04 benchmark measures this directly and reports it as a finding rather than discovering it at UAT — operator ruling: benchmark it, decide later, no remedy in v1.3 |
| **Reverted third attempt, now investigated** | Four full-stack commits with 1,301 lines of tests, reverted within one minute on 2026-08-20 — confirmed by git archaeology (commit timestamps, timing relative to the Phase 11 pause) as speculative off-roadmap work reverted as scope discipline, not a technical failure. Limit of the claim: nobody recorded whether the constraints actually worked — "removed for scope" is well-supported, "it worked" is not established | Treated as candidate salvage material in Phase 17 (transfers as-is / transfers with rework / needs reformulation), not a standalone investigation phase — see ROADMAP.md Phase 17 Notes |
| **Solver-build failure invisible to tests** | Spike hit a hard solver-build error on adding a second `@PlanningEntity`; no solver-package test builds a solver | A `solverConfig.xml` regression reaches production. Covered by XCUT-03, gating Phase 15 |
| **Envelope and contracted-hours constraints jointly unsatisfiable** | ARCHITECTURE.md §1 — a shift template whose duration mismatches an agent's effective hours makes both constraints impossible together, unfixable by solver time | A desk that solves today stops solving, for a reason no single constraint explains. Covered by SHLB-06 (Phase 14) and effective-hours value-range filtering (Phase 15) |
| **`nullable=true` deprecated** | Spike verified against the 1.16.0 JAR: `@Deprecated(forRemoval=true, since="1.8.0")`, superseded by `allowsUnassigned()`. `AgentAssignment` uses it today | Not urgent at 1.16.0, but any future bump breaks it. Worth noting, not worth fixing now |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| SHLB-01…06 | Phase 14 | Complete (2026-08-26) — phase verification `passed` (refreshed 2026-09-21, after UAT 9/9 discharged all three human-verification items). See the SHLB-04 note below on Phase 15's guarded delete control. |
| SHLB-07 | Phase 15 | Complete |
| MODE-01…05 | Phase 14 | Complete (2026-08-26) — phase verification `passed` (refreshed 2026-09-21, after UAT 9/9). |
| ENVL-01…10 | Phase 15 | Complete (2026-08-27) — verification `passed`; this row read "Pending" until 2026-09-03 while the checkbox list above already marked all ten `[x]` |
| USHF-01…06 | Phase 16 | Complete (2026-09-04) — all six verified against the codebase by `16-VERIFICATION.md`, phase verification `passed`. This row read "Complete in code … phase status is `human_needed`" until 2026-09-21; that was stale. All three human-verification items were discharged with live evidence and recorded in `16-VERIFICATION.md`'s frontmatter: the Excel open-and-inspect (2026-09-04 — real Microsoft Excel, no repair prompt, working Usual Shift dropdown, `formula1` 104 chars against the 255-char explicit-list limit), the roster tile three-state QA (2026-09-03 — `getComputedStyle` read from the live DOM, not eyeballed: NOT_SET `#d1d5db`/w400/upright, LIVE `#3b82f6`/w600/upright, STORED_INACTIVE `#9ca3af`/w400/italic), and the XCUT-01 roster-inline-write → export round trip (2026-09-03 — cols U/V carried the raw stored names). UAT 34/34, 0 issues; suite 723 green. One documented sub-caveat: the STORED_INACTIVE/RETIRED *reason word* was never rendered live (unreachable without retiring a template in use on the live desk) and is covered by `DeskAgentServiceUsualShiftTest` only. |
| CONS-01…06, DRFT-01…04 | Phase 17 | Complete in code (2026-09-18) — all ten verified against the codebase. Supersedes the 2026-09-17 `gaps_found` note: that report's single failed gap (CR-01, the drift report's slot-mode contract) was closed by `ea9b6ac`, confirmed live on a SLOT-solved desk holding stored usual-shift rows. Phase verification is `passed` (2026-09-18) — 5/5 roadmap success criteria verified. The visual backstop truths this project has no frontend test framework to automate were closed by live browser measurement during UAT (drift status colours, precedence note, tolerance-band row, and the popularity list's long-name wrap at 74 chars). Validation: `17-VALIDATION.md` is `nyquist_compliant: true`, 13/13 requirements with automated verification. Security: `17-SECURITY.md` `threats_open: 0` (T-17-07 closed via documented accepted risk R-17-01, not a working mitigation). |
| XCUT-01 (display verification) | Phases 14, 15, 16, 17 | Complete in code (2026-09-17) — Phases 14/15 complete; Phase 16's store → roster → export trace proven end-to-end in one test (`UsualShiftTracerTest#happyPath_storeRosterExport_endToEnd`); Phase 17 plan 17-03 added the drift report's backend/export leg (`ScheduleDetailResponse.driftReport` + the Excel `Drift Report` sheet, headers byte-identical between the two surfaces); plan 17-05 closed the frontend leg with the Drift Report tab, whose headers are byte-identical to the export sheet's — the on-screen visual read remains human-verification-only, no frontend test framework exists in this project |
| XCUT-02 (every write path) | Phases 16, 17 | Complete (2026-09-17) — Phase 16 delivered nine enumerated write paths in `src/test/resources/ushf-05-write-paths.md`, one test per path, plus `UsualShiftWritePathGuardTest`'s set-equality structural guard proven able to fail twice (test-of-the-test and a real deliberate break). Phase 17 plan 17-01 added the solver's new read path (row 7, updated); plan 17-03 added a tenth row and `SolverUsualShiftWritePathGuardTest`, proving that read path is genuinely read-only by a behavioural mock-interaction proof plus a structural comment-stripped source scan, not asserted in prose |
| XCUT-03 (solverConfig.xml build test) | Phase 15 | Complete (2026-08-27) — every gap-closure plan's solver tests (15-04, 15-08, 15-09, 15-11, 15-13) build a solver via `SolverConfig.createFromXmlResource("solverConfig.xml")` and solve through it; `ShiftDeskEndToEndRegressionTest` (15-13) is the closing end-to-end proof |
| XCUT-04 (seeded A/B benchmark) | Phases 15, 17 | Complete (2026-09-17) — Phase 15 benchmark delivered (`15-BENCHMARK.md`, PASS verdict, D-08 CH-ordering decided); Phase 17 plan 17-04 delivered its own threshold-first, seeded A/B (`17-BENCHMARK.md`) for the consistency weight — threshold committed before any result, honest null-result write-up (construction-heuristic plateau, not a win/loss), median + full min/max spread reported, and the redone per-agent-day sizing arithmetic gating the V49 default |
| SHLB-04 (superseding note) | Phase 14 → Phase 15 | Still **Complete**, but the mechanism changed and the requirement is satisfied more strongly, not less. Phase 14 delivered retire-only and deliberately shipped *no* delete path (decision D-10, citing v1.2 audit I-3 on destructive controls). Phase 15's `81117e3` added one at the operator's own request during UAT (`15-UAT.md:535`) — because retire-only stranded typos, duplicates and test rows in the library forever. It is not a loosening: `ShiftTemplateService.deleteShiftTemplate` refuses with 409 when the template is referenced by **any** agent-day assignment *or* **any** stored usual shift, naming the count and telling the operator to retire instead, so a template that ever shaped a roster still cannot be destroyed. REQUIREMENTS.md's own SHLB-04 text ("edit and retire … without corrupting schedules that already reference them") holds either way. Recorded here because `14-VERIFICATION.md`'s observable truths #5 and #22 asserted the pre-Phase-15 state and were corrected on 2026-09-21. |
| XCUT-05 (constraint classification) | Phases 14, 15 | Complete (2026-08-27) — Phase 14 classification delivered (`XCUT-05-constraint-classification.md`); Phase 15 mode-gating (plans 15-06, 15-09) closed the remaining "Minimum staffing" row |

**Coverage:**

- v1.3 requirements: **38 total** (7 SHLB, 5 MODE, 10 ENVL, 6 USHF, 6 CONS, 4 DRFT) — **38/38 mapped ✓**
- Cross-cutting: 5 XCUT (apply to all phases they concern) — **5/5 mapped ✓**
- **43 requirement IDs in total**, all `[x]` and all traced to a phase.
- No orphaned requirements.

> This line read "34 total (6 SHLB, 5 MODE, 7 ENVL, …)" until 2026-09-21. That breakdown was the
> original 2026-08-25 count and was never updated when SHLB-07 and ENVL-08/09/10 were added during
> scoping — the four IDs that arrived later are exactly the four the old arithmetic was missing. The
> checkbox list above has always carried all 38; only this summary was behind. Corrected at the v1.3
> milestone audit, where the count was re-derived by grep rather than by trusting either figure.

---
*Requirements defined: 2026-08-25*
*Roadmap created: 2026-08-25 — see `.planning/ROADMAP.md` Phases 14–17*
*Research: `.planning/research/SUMMARY.md`, `.planning/research/SPIKE-COUPLING.md`*
