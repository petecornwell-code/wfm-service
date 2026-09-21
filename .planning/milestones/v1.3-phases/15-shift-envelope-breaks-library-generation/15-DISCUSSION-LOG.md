# Phase 15: Shift Envelope, Breaks & Library Generation - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-08-26
**Phase:** 15-Shift Envelope, Breaks & Library Generation
**Areas discussed:** Break bands & capacity, AgentShiftAssignment shape, SHLB-07 generation, Benchmark threshold

Four gray areas were offered; the operator selected all four. Two further areas — the two
`OPEN_RESOLVE_IN_PHASE_15` preference constraints and ENVL-10's allocation-view grouping — were
offered and declined into Claude's Discretion.

---

## Break bands & capacity

### Q1 — What happens to `shift_template.break_offset_minutes` / `break_duration_minutes` when bands arrive?

| Option | Description | Selected |
|--------|-------------|----------|
| Retire into bands | New `shift_template_break_band` child table (offset, duration, capacity); every existing template's single offset migrates into exactly one band; both columns dropped. One mechanism, one predicate — the D-10 / audit NEW-1 reasoning. One-way migration. | ✓ |
| Duration stays, offsets move | `break_duration_minutes` stays on the template (all bands same length); only offset + capacity become band rows. Smaller migration and `covers()` change. Cost: two tables jointly describe one break. | |
| Both survive; bands optional | Single offset stays as the implicit default band; bands purely additive. Least disruptive to Phase 14's shipped surface. Cost: exactly the audit NEW-1 shape. | |

**User's choice:** Retire into bands
**Notes:** Recorded as D-01 with a one-way reversibility rating.

### Q2 — For a multi-band template, when does `covers()` call a demand window covered?

| Option | Description | Selected |
|--------|-------------|----------|
| Any band works it | Covered if at least one band leaves that window worked. A one-band template behaves exactly as today, so no Phase 14 desk's verdict changes and D-08 holds untouched. Lets a two-band template self-cover its break hour. | ✓ |
| Every band must work it | A break hour is never covered by its own template. Preserves today's predicate verbatim. Cost: bands do nothing for coverage; SHLB-07 must always emit overlapping pairs. | |
| Capacity-weighted | Covered if band capacities leave enough agents working to meet demand FTE. Most truthful to solve time. Cost: reverses Phase 14 D-04's deliberate structural-not-capacity ruling. | |

**User's choice:** Any band works it
**Notes:** Recorded as D-02. The degeneracy to today's behaviour for one-band templates was the
deciding property — no shipped desk's validation verdict moves.

### Q3 — How hard does a band's capacity bind?

| Option | Description | Selected |
|--------|-------------|----------|
| Hard when set, null = unlimited | Explicit operator lever, enforced hard only when a number is entered; editor and generation default it blank. `Break clustering` stays the soft distributing force. Residual risk: caps below headcount give an unexplainable infeasible solve. | ✓ |
| Always soft | Never makes a schedule infeasible; matches the standing "fairness soft-score only" rule. Cost: a stated cap the solver can buy past, and two soft forces on one behaviour with no precedence. | |
| Required and hard | Every band carries a number, validated against eligible headcount at save and mode switch. Strongest distribution guarantee. Cost: couples library editing to roster state — the coupling D-06 rejected. | |

**User's choice:** Hard when set, null = unlimited
**Notes:** Recorded as D-03. The residual infeasibility risk was surfaced at the time of the choice
and is carried into CONTEXT.md as a named risk for the planner to place, not left to be discovered.

### Q4 — How should band choice be modelled as a planning variable?

| Option | Description | Selected |
|--------|-------------|----------|
| One variable, `(shift, band)` pair value | Single `@PlanningVariable` whose value is a template-plus-band problem fact; value range is every live pair for the desk, filtered by `effectiveHours`. Sidesteps Anti-Pattern 2, keeps the CH at two phases, ENVL-01 still holds. | ✓ |
| Second variable on the same entity | `shift` AND `breakBand`, the latter's value range derived from the chosen shift. Most literal reading of ENVL-08. Cost: a value range reading a genuine planning variable — Anti-Pattern 2, the Option C failure mode. | |
| Third planning entity | Separate `AgentBreakAssignment` coupled by a second hard constraint. Structurally consistent with Option A. Cost: a third explicitly-scoped CH phase, when CH XML nesting is already the MEDIUM-confidence research item. | |

**User's choice:** One variable, `(shift, band)` pair value
**Notes:** Recorded as D-04, reversibility costly.

**Not pursued (offered, declined):** the ENVL-09 clustering penalty's exact shape against
`breakClusterThresholdPct`, whether all bands share one duration, and what the band editor does to a
template an accepted schedule references.

---

## AgentShiftAssignment shape

### Q1 — Which agent-days get a row?

| Option | Description | Selected |
|--------|-------------|----------|
| One per `AgentDayConfig` with hours > 0 | Reuses the existing per-agent-day derivation. PTO / mandatory days off / zero-hours days produce no row, so "not working" is never a solver value. Same fact the value-range filter reads. | ✓ |
| One per eligible agent × every date | Null shift means "not working". Uniform and trivially enumerable. Cost: two mechanisms for one fact, competing with `agentDayOff` and `contractedHoursUnderZero`. | |
| Only agents with demand-matched seats | Derived from seat expansion. Smallest entity count. Cost: couples the two planning entities at construction time, defeating Option A's independence. | |

**User's choice:** One per `AgentDayConfig` with hours > 0
**Notes:** Recorded as D-05.

### Q2 — Can the shift variable be left unassigned, and does that need its own constraint?

| Option | Description | Selected |
|--------|-------------|----------|
| Unassigned allowed, no new constraint | `allowsUnassigned()` (non-deprecated at 1.16.0). Null shift forbids every seat via the envelope constraint; the agent trips existing `contractedHoursUnder`/`UnderZero` at 100 hard. No nineteenth-plus constraint. | ✓ |
| Unassigned allowed, plus an Unassigned-shift penalty | Same nullability plus a dedicated constraint mirroring `unassignedAssignment` for better score-breakdown diagnostics. Cost: double-counts contracted-hours, and needs an XCUT-05 row and a weight column. | |
| Shift is required | Every row must receive a template — strongest reading of ENVL-01. Cost: an empty value range fails the solve hard, and that mismatch is SHLB-06's advisory case, re-creatable by any routine hours edit. | |

**User's choice:** Unassigned allowed, no new constraint
**Notes:** Recorded as D-06. The `nullable=true` → `allowsUnassigned()` correction was noted at the
time — `AgentAssignment` still uses the deprecated form, and REQUIREMENTS.md's risk table already
flags it as "worth noting, not worth fixing now"; a new entity gets the current form.

### Q3 — How does an accepted schedule freeze what shift each agent actually worked?

| Option | Description | Selected |
|--------|-------------|----------|
| Denormalise onto the assignment row | Template name, start, end, actual band offset and break duration as plain columns, plus a nullable `source_template_id`. Immune to later edits by construction; allocation view, export and drift report read it with no era-bearing join. | ✓ |
| Snapshot the template rows too | Schedule-scoped copies of `shift_template` + bands, FK remapped — the third instance of a pattern `acceptSchedule` runs twice, and the most literal reading of D-09. Cost: copying a template plus N band rows to record one envelope per agent-day. | |
| FK to the live template only | Relies on Phase 14's no-delete guarantee. Zero new storage. Cost: does not satisfy D-09 — retirement is safe, editing is not. | |

**User's choice:** Denormalise onto the assignment row
**Notes:** Recorded as D-07, reversibility one-way. Flagged in CONTEXT.md as a *documented deviation*
from the `Timeslot`/`StaffingRequirement` snapshot pattern rather than an oversight.

### Q4 — Shifts-first or seats-first construction heuristic?

| Option | Description | Selected |
|--------|-------------|----------|
| Measure both in the benchmark | Third arm on the XCUT-04 run, same seeds and step-count termination; ship the winner. Closes the spike's open item 5 by measurement. Cost: a third arm and its wall-clock. | ✓ |
| Ship seats-first | The toy fixture's winner (−5soft vs −10soft, 8/8 seeds). Cheapest, has evidence. Cost: the spike says the toy's seat demand fully determines shift choice, so it may not transfer. | |
| Ship shifts-first | Coarse decision before fine; conventional CH ordering. Cost: it lost on the only measurement that exists. | |

**User's choice:** Measure both in the benchmark
**Notes:** Recorded as D-08.

**Not pursued (offered, declined):** the two open preference constraints in shift mode, whether the
envelope constraint treats the break window as forbidden seat territory, and how mode-gating reaches
the constraint provider — all moved to Claude's Discretion with recommendations.

---

## SHLB-07 generation

### Q1 — How should generation sequence against the band migration?

| Option | Description | Selected |
|--------|-------------|----------|
| Band schema first, in the generation slice | The V40 band migration, entity, editor and `covers()` change land WITH generation; the solver-side band variable and ENVL-09 follow with the envelope work. Generation still ships first and emits banded templates against final schema. Cost: a bigger first slice. | ✓ |
| Generation first, exactly as roadmapped | Build against Phase 14's single-offset schema, emitting overlapping pairs, then rework when bands land. Minimal first slice, shares no code with the rest of the phase. Cost: built twice, the second time against dropped columns. | |
| Bands fully first, generation second | All of ENVL-08/09 before generation. Cleanest dependency order. Cost: gives up SHLB-07's immediacy — the explicit reason it was pulled into this phase. | |

**User's choice:** Band schema first, in the generation slice
**Notes:** This adjusts a ROADMAP note. The "SHLB-07 is the first plan slice" ordering is preserved;
the accompanying claim that the slice "shares no code path with the rest of the phase" is not, and
CONTEXT.md says so explicitly rather than letting the planner carry it forward unexamined. The
trigger was D-01 — generating against columns dropped later in the same phase.

### Q2 — What does the generated library optimise for?

| Option | Description | Selected |
|--------|-------------|----------|
| Fewest templates, over-coverage as tiebreak | Minimum set cover with envelope-hours-beyond-demand breaking ties deterministically, so the same demand always yields the same draft. A small library is maintainable and Phase 17's consistency work degrades as the library sprawls. | ✓ |
| Closest fit to the demand curve | Minimise over-coverage first, accepting more templates. Wastes less capacity. Cost: a dozen near-identical templates are hostile to edit and weaken "usual shift". | |
| Fewest templates, hard cap on over-coverage | A per-desk dial between the two. Cost: a new knob with no evidence behind it, beside `breakClusterThresholdPct`. | |

**User's choice:** Fewest templates, over-coverage as tiebreak
**Notes:** Recorded as D-10. Contracted-hours matching stays a *filter* on candidates (D-07 exact
`BigDecimal` equality), not an objective.

### Q3 — How does the draft reach the operator without ever being auto-applied?

| Option | Description | Selected |
|--------|-------------|----------|
| Stateless endpoint, edit-then-save | Computed draft rendered as editable proposed rows; nothing written until save, through the existing create/validate path. No draft table, no status column. | ✓ |
| Persisted draft rows | A draft/proposed status the solver and validator ignore until promoted. Survives reload. Cost: a second lifecycle mechanism alongside the effective-date range — the D-10 / NEW-1 trap. | |
| Downloadable draft file | Edit offline and re-upload, mirroring contracted-hours provisioning. Cost: a new upload/parse path for a handful of rows, and the round trip removes the immediate feedback. | |

**User's choice:** Stateless endpoint, edit-then-save
**Notes:** Recorded as D-11.

### Q4 — What does generation return when demand cannot be fully covered?

| Option | Description | Selected |
|--------|-------------|----------|
| Best partial draft + named residuals | Draft covering what it can, plus still-uncovered windows in the existing SHLB-05 `ErrorDetail` shape. Zero demand or zero agents with hours → refused with the existing message, never an empty draft. | ✓ |
| Refuse and name the gap | No draft when full coverage is impossible. Guarantees a generated library always validates clean. Cost: the desk that most needs help gets nothing. | |
| Cover everything, relax hours matching | Complete-looking library with the SHLB-06 advisory. Cost: emits exactly the templates that make envelope and contracted-hours jointly unsatisfiable. | |

**User's choice:** Best partial draft + named residuals
**Notes:** Recorded as D-12.

**Not pursued (offered, declined):** candidate enumeration bounds, generated-template naming, bands
per generated template, and where the effective date range comes from.

---

## Benchmark threshold

### Q1 — What does failing the pre-committed threshold actually gate?

| Option | Description | Selected |
|--------|-------------|----------|
| Whether to pilot, not whether to ship | `scheduling_mode` already defaults to `SLOT`, so the fallback is structural: a FAIL means no desk gets switched yet. Verdict recorded verbatim in `15-BENCHMARK.md`. Phase completion judged on ENVL-01…10 correctness, verified by ENVL-07's ground-truth walker. | ✓ |
| Phase completion, as in Phase 12 | A FAIL means the goal is not claimed. Maximally honest and precedented. Cost: contradicts the standing operator ruling, and would withdraw a correctness deliverable over a predicted-mediocre quality measurement. | |
| Only the CH ordering choice | No shift-vs-slot threshold at all. Smallest scope. Cost: abandons XCUT-04 as written. | |

**User's choice:** Whether to pilot, not whether to ship
**Notes:** Recorded as D-13. This is the reconciliation between XCUT-04's pre-commitment requirement
and the standing operator ruling — neither is quietly dropped.

### Q2 — What does the threshold measure?

| Option | Description | Selected |
|--------|-------------|----------|
| Model-independent operational metrics | `0hard` reached, unstaffed demand slot-count, total assigned hours. Soft-score gap reported as the plateau finding, explicitly not a threshold, because the two arms don't evaluate the same constraint set. | ✓ |
| Soft score gap | Directly comparable to the spike's −10soft/−5soft result. Cost: the shift arm carries penalties the slot arm structurally cannot produce. | |
| Coverage only | One number plus feasibility. Simplest to pre-commit. Cost: could hold coverage while wrecking break distribution or hours accuracy. | |

**User's choice:** Model-independent operational metrics
**Notes:** Recorded as D-14. The incommensurability point — `Break clustering` fires in one arm and
not the other — was raised before the choice.

### Q3 — What is the pass rule, committed before the run?

| Option | Description | Selected |
|--------|-------------|----------|
| `0hard` always; coverage not worse, noise-aware | Must-pass `0hard` on every seed; median unstaffed slots no worse than the slot arm's median; any difference smaller than the slot arm's own min/max spread written up as "no measurable difference", never a win or a loss. | ✓ |
| Explicit tolerance band | A stated percentage of acceptable coverage regression. Names the trade. Cost: an evidence-free number. | |
| Must strictly beat slot mode | Unambiguous and demanding. Cost: the spike predicts the plateau, so it pre-commits to a FAIL and makes the benchmark ceremonial. | |

**User's choice:** `0hard` always; coverage not worse, noise-aware
**Notes:** Recorded as D-15. The noise rule encodes Phase 12's lesson as a rule: +0.25h against a
5.00h spread should never have read as an improvement in either direction.

### Q4 — What fixture does the benchmark run against?

| Option | Description | Selected |
|--------|-------------|----------|
| Seeded synthetic A/B + one indicative real run | Phase 12's harness shape re-parameterised to ~130% over-allocation carries the threshold; a separate non-comparative real-desk run, labelled indicative, partly closes spike open item 1. | ✓ |
| Seeded synthetic only | Strictly reproducible. Cost: leaves real-scale join cost untested until a live pilot. | |
| Real desk data only | Closest to the question that matters. Cost: not reproducible, so it cannot support a pre-committed seeded threshold. | |

**User's choice:** Seeded synthetic A/B + one indicative real run
**Notes:** Recorded as D-16.

---

## Claude's Discretion

Offered to the operator and declined into discretion, each recorded in CONTEXT.md with a
recommendation and its reasoning:

- The two `OPEN_RESOLVE_IN_PHASE_15` constraints (`honourPreferredStartTime`,
  `honourPreferredBreakTime`) — recommendation: mode-gate both off in shift mode, reclassify
  `MODE_GATED`.
- `shiftEnvelopeCompliance`'s stream form — recommendation: plain positive join, not `ifNotExists`.
- Where ENVL-07's ground-truth check lives — recommendation: a test-side walker outside the score
  director.
- How mode-gating reaches the constraint provider — recommendation: a `SchedulingMode` field on
  `ScheduleConfig`, filtered at the top of each affected constraint, bodies untouched.
- ENVL-10's Agent Allocation view grouping design.
- Whether a zero-band template still means "no break" — recommendation: yes.
- SHLB-07 candidate enumeration bounds and solve method — with the ROADMAP's do-not-over-engineer
  instruction attached.
- Generated-template naming and effective date range.
- The exact Flyway migration number — confirm the latest applied version, do not assume.
- Whether the Flyway/entity migration-coverage blind spot (G-14-1) is closed in this phase.

## Deferred Ideas

- Continuous break *window* (solver-chosen start between an earliest and latest offset) — rejected in
  the ROADMAP; re-opens the four break constraints this phase gates off.
- A `SelectionFilter` layered on `shiftEnvelopeCompliance` — a later search-efficiency tune, never a
  replacement for the hard constraint.
- Any custom Timefold move for the plateau — Out of Scope for v1.3; needs its own evidence-led
  decision.
- Capacity-aware coverage validation — rejected again here; carried from Phase 14.
- Promoting break config from `Schedule` to `Desk` — carried from Phase 14 D-03.
- Multiple *distinct* breaks per agent-day (as opposed to bands offering a choice of one).
- A Testcontainers-backed migration boot test.
