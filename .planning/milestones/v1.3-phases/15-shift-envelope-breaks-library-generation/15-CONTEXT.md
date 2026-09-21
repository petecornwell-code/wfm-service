# Phase 15: Shift Envelope, Breaks & Library Generation - Context

**Gathered:** 2026-08-26
**Status:** Ready for planning

<domain>
## Phase Boundary

On a shift-scheduled desk, the solver assigns each working agent exactly one shift per day from that
desk's library and is hard-constrained from seating them outside it; breaks are distributed across
bands rather than taken simultaneously; the result is legible as shifts in the Agent Allocation
view; and an operator can have a starting library suggested from demand instead of composing one by
hand.

**This is the phase that touches solver code.** Phase 14 deliberately did not: `ShiftTemplate` is a
plain problem fact and `desk.scheduling_mode` is a column nothing reads. Phase 15 adds a second
`@PlanningEntity`, changes `solverConfig.xml` for the first time in this milestone, mode-gates four
existing constraints, and gives an inert constraint a real body.

**Requirements:** ENVL-01…10, SHLB-07, XCUT-01, XCUT-03, XCUT-04, XCUT-05 (completing Phase 14's
partial delivery).

**Settled before this discussion — do NOT re-open:**

- **Option A coupling.** Two independent `@PlanningEntity` classes joined by a `ConstraintStream`
  **hard** constraint. `SPIKE-COUPLING.md` proved Option C (filtered value range reading another
  entity's genuine planning variable) empirically unsound: it compiled, ran clean under
  `FULL_ASSERT`, and reported `0hard/0soft` while 9–14 of 24 seats sat outside their agent's
  envelope, on 8/8 seeds. Load-bearing for ENVL-02 and ENVL-07.
- **No custom Timefold moves.** The soft-quality plateau is *measured and reported*, never remedied
  in this phase or this milestone (operator ruling; Phase 12 already failed once by committing to a
  remedy before measuring). A `SelectionFilter` layered on the hard constraint remains a legitimate
  later search-efficiency tune — never a replacement for the constraint.
- **Break bands AND a real `Break clustering` body** — both, not either (operator ruling
  2026-08-26). Bands give the solver freedom to move a break; the clustering constraint is the force
  that spreads them. A clustering constraint over a fixed offset would only report an unavoidable
  penalty every solve.
- **Explicit construction-heuristic phases.** The bare `<constructionHeuristic/>` throws
  `IllegalArgumentException` at solver-build time the moment a second entity class exists. Every
  `EntitySelectorConfig` needs **both** `entityClass` and `id` — a null `id` makes
  `newMimicSelectorConfig` resolve to the wrong entity descriptor and produces a misleading
  "variableName is not a valid planning variable" error. Plus XCUT-03's test that actually builds a
  solver from the real `solverConfig.xml`.
- **Shift choice value-range-filtered by `AgentDayConfig.effectiveHours`.** Without it,
  `shiftEnvelopeCompliance` and the existing `contractedHoursOver`/`Under` constraints become jointly
  unsatisfiable for that agent-day, and no amount of solver time fixes it.
- **Accept-time snapshot obligation** inherited from Phase 14 D-09.

**Not in this phase** (Phase 16 and later): `agent_usual_shift` storage, the usual-shift upload
column and inline roster edit, the consistency soft constraint, the drift report.

</domain>

<decisions>
## Implementation Decisions

### Break Bands (ENVL-08, ENVL-09)

- **D-01:** Phase 14's `shift_template.break_offset_minutes` and `break_duration_minutes` are
  **retired into a `shift_template_break_band` child table** (offset, duration, capacity). The
  migration moves every existing template's single offset into exactly one band; both columns are
  dropped. Rejected: keeping the columns as an implicit "default band" beside the band rows — that is
  precisely the audit NEW-1 shape (two sources that can disagree about one fact) and the same trap
  D-10 refused when it rejected an `active` boolean beside the effective-date range. One mechanism,
  one predicate.
  — **Reversibility:** one-way — the columns are dropped in a Flyway migration, and by the time it
  matters `agent_shift_assignment` and the Phase 14 editor both read bands.

- **D-02:** `ShiftLibraryValidationService.covers()` generalises to **any-band coverage**: a demand
  window is covered if at least one band leaves that window worked. A one-band template therefore
  behaves **exactly as it does today**, so no Phase 14 desk's validation verdict moves and D-08's
  one-implementation-two-callers guarantee is untouched. This is what allows a single banded template
  to self-cover its own break hour, which is why SHLB-07 can emit one banded template where the
  hand-built StubHub (EN) library needs an overlapping pair. Still structural, not capacity-aware —
  D-04's ruling stands. Rejected: requiring every band to work the window (bands would then do
  nothing for coverage at all) and capacity-weighted coverage (reverses D-04 deliberately).

- **D-03:** A band's `capacity` is a **hard cap only when set; blank/null means unlimited.** The
  editor and SHLB-07 generation both default it blank, so nobody creates an infeasible library by
  accident. `Break clustering` (ENVL-09) stays the soft force that actually spreads breaks.
  Rejected: always-soft capacity (an operator's stated cap becomes a suggestion, and capacity plus
  clustering become two soft forces on one behaviour with no documented precedence) and
  required-and-hard capacity validated against headcount (couples library editing to roster state —
  the exact coupling D-06 rejected, where tomorrow's hours upload retroactively invalidates a saved
  template).
  — **Known residual risk, to be surfaced not discovered:** an operator who sets caps totalling below
  the shift's actual headcount produces an infeasible solve that no single constraint explains — the
  same joint-unsatisfiability shape this milestone already named once. The planner should decide
  where this is caught (a save-time advisory in the same shape as SHLB-06, or a named
  `PreSolveValidationException` detail) rather than leaving it to a bare `-Nhard` score.

- **D-04:** Band choice is expressed as **one planning variable whose value is a `(template, band)`
  pair problem fact** — `AgentShiftAssignment` keeps a single `@PlanningVariable`, and its value
  range is every live pair for the desk, filtered by `AgentDayConfig.effectiveHours`. ENVL-01 still
  holds: one pair is exactly one shift. Rejected: a second `breakBand` variable on the same entity
  whose value range derives from the chosen shift — that is a value range reading a genuine planning
  variable, Anti-Pattern 2 in `.planning/research/ARCHITECTURE.md`, the identical mechanism that made
  Option C silently unsound. Also rejected: a third planning entity, which would need a third
  explicitly-scoped CH phase when the CH XML nesting is already the phase's one MEDIUM-confidence
  research item.
  — **Reversibility:** costly — the value-range shape is read by the CH config, the envelope
  constraint, the effective-hours filter, and (in Phase 17) the consistency constraint's comparison
  against a stored usual shift.

### Shift Envelope Entity (ENVL-01, ENVL-02, ENVL-06)

- **D-05:** **One `AgentShiftAssignment` row per `AgentDayConfig` with `effectiveHours > 0`.**
  `SolverService` already computes `AgentDayConfig` per agent-day, resolving contracted hours against
  exceptions and days off; shift rows are derived from that same fact. PTO, mandatory days off and
  zero-hours days simply produce no row, so "not working" is never a value the solver can get wrong —
  `agentDayOff` and `contractedHoursUnderZero` already own that answer. Entity creation and the
  effective-hours value-range filter therefore read the same fact and cannot disagree. Rejected: a
  row per eligible agent × every date with null meaning "not working" (two mechanisms for one fact)
  and deriving rows from seat expansion (couples the two planning entities at construction time,
  when Option A's whole point is that they are independent and coupled only by a constraint).

- **D-06:** The shift variable **allows unassigned** — using `allowsUnassigned()`, the
  non-deprecated form at 1.16.0, **not** the `nullable=true` that `AgentAssignment` still uses
  (`@Deprecated(forRemoval=true, since="1.8.0")`). The CH can therefore start every variable null as
  it does today. A null shift forbids every seat via `shiftEnvelopeCompliance`, and the agent then
  trips the existing `contractedHoursUnder` / `contractedHoursUnderZero` at 100 hard, which already
  mean exactly this. **No new "unassigned shift" constraint.** Rejected: a dedicated soft penalty
  mirroring `unassignedAssignment` (better score-breakdown diagnostics, but it double-counts what
  contracted-hours already penalises, and every new constraint needs an XCUT-05 classification row
  and a weight column) and a required shift (an agent-day whose hours match no template has an empty
  value range and the solve fails hard rather than degrading — and that mismatch is SHLB-06's case,
  which is advisory at save time and re-creatable by any routine hours edit after the mode switch
  passed).

- **D-07:** An accepted schedule freezes the shift by **denormalising the resolved envelope onto the
  accepted `agent_shift_assignment` row** — template name, start, end, the agent's actual band offset
  and break duration — plus a nullable `source_template_id` for lineage. It records what the agent
  actually worked, in one row, immune to any later template edit by construction. The allocation view
  (ENVL-10), the Excel export and Phase 17's drift report all read it without joining into a table
  whose rows have eras. Rejected: schedule-scoped copies of `shift_template` and its band rows
  mirroring the `Timeslot`/`StaffingRequirement` pattern (the most literal reading of D-09, but it
  copies a template plus N band rows per accepted schedule to record one envelope per agent-day) and
  an FK to the live template relying on Phase 14's no-delete guarantee (retirement is safe but
  **editing is not** — `updateShiftTemplate` can mutate times, so a routine correction would silently
  rewrite what history says an agent worked, failing D-09).
  — **Reversibility:** one-way — the accepted-row shape is a migration, and once shift-mode schedules
  have been accepted, changing how history is stored means migrating them.

- **D-08:** **CH ordering is measured, not inherited.** Shifts-first vs seats-first is added as a
  third arm to the XCUT-04 benchmark (same seeds, same step-count termination, median plus full
  min/max spread) and the winner ships. `SPIKE-COUPLING.md` open item 5 says plainly that its toy
  fixture has seat demand fully determining shift choice, so its `-5soft` seats-first result may not
  transfer. Rejected: shipping the toy's winner (inheritance-by-assumption, the failure mode this
  milestone repeatedly warns about) and shipping shifts-first on convention alone.

### Library Generation (SHLB-07)

- **D-09:** **The band schema lands inside the generation slice, ahead of the envelope work.** ENVL-08
  splits: the band migration, entity, editor change and D-02's `covers()` generalisation ship WITH
  SHLB-07; the solver-side `(template, band)` value range and the ENVL-09 clustering constraint
  follow with the envelope work. Generation still ships first and is testable immediately — the
  ROADMAP's stated reason for pulling SHLB-07 forward — and emits banded templates against final
  schema, so nothing is built twice.
  **This adjusts the ROADMAP note that SHLB-07 "should be the phase's FIRST plan slice" and shares
  no code path with the rest of the phase.** After D-01, generating against Phase 14's single-offset
  columns would mean writing generation against columns dropped later in the same phase. The
  first-slice ordering and the independence of the *generation logic* are preserved; the claim that
  the slice shares no code with the rest of the phase is not, and the planner should not carry that
  claim forward unexamined. Rejected: generation first exactly as roadmapped, emitting overlapping
  pairs and reworked when bands land (built twice), and all of ENVL-08/09 before generation (gives
  up the immediacy that justified pulling SHLB-07 into this phase).

- **D-10:** Generation **minimises template count** — a minimum set cover over the candidate
  envelopes — with total envelope-hours beyond demand as a **deterministic tiebreak**, so the same
  demand always produces the same draft. Contracted-hours matching is a **filter on candidates, not
  an objective**: D-07's exact `BigDecimal` equality of net duration to an agent's `AgentDayHours`
  value for that weekday already decides which envelopes are admissible. Rationale: a small library
  is what an operator can maintain and edit, and Phase 17's consistency work degrades as the library
  sprawls (a "usual shift" means less when a dozen near-identical templates exist). Rejected:
  closest-fit-to-the-demand-curve (better capacity utilisation, hostile to edit) and a per-desk
  over-coverage cap (a new knob with no evidence behind it, sitting beside
  `breakClusterThresholdPct` — a knob wired to nothing is a failure mode this phase is fixing).

- **D-11:** The draft is delivered by a **stateless suggestion endpoint** — computed on request,
  rendered in the Shift Library page as proposed rows the operator can edit, drop or rename, with
  nothing written until they save, at which point the rows go through the existing
  `ShiftTemplateService` create/validate path unchanged. **No draft table, no status column** — the
  same one-mechanism reasoning D-10 (Phase 14) used to refuse an `active` boolean. Recomputing is
  free and always reflects current demand. Rejected: persisted draft rows with a proposed status
  (a second lifecycle mechanism alongside the effective-date range) and a downloadable draft file
  (a new upload/parse path for a handful of rows, and the round trip removes the immediate feedback
  that justified building generation at all).

- **D-12:** When demand cannot be fully covered, generation returns the **best partial draft plus the
  still-uncovered windows, named in the same `ErrorDetail` shape the SHLB-05 coverage report already
  emits** — so the panel an operator reads after generating is the panel they read after validating.
  A desk with **zero live demand rows, or zero agents with contracted hours, is REFUSED** with the
  existing "no staffing demand loaded for this desk" message and never handed an empty draft (D-05's
  never-pass-vacuously rule). Rejected: refusing outright when full coverage is impossible (the desk
  that most needs help gets nothing) and relaxing the hours filter to force full coverage (that would
  emit exactly the templates that make the envelope and contracted-hours constraints jointly
  unsatisfiable).

### Benchmark (XCUT-04)

- **D-13:** The pre-committed threshold **gates whether to pilot, not whether to ship.**
  `scheduling_mode` already defaults to `SLOT` on every desk, so the fallback is structural rather
  than a promise: a FAIL means no desk gets switched yet. The verdict is recorded PASS or FAIL
  verbatim in a `15-BENCHMARK.md` and becomes the piloting recommendation. Phase completion is judged
  on ENVL-01…10 correctness, which ENVL-07's ground-truth walker verifies independently of any score.
  This is how XCUT-04's pre-commitment requirement and the standing operator ruling ("ship the sound
  model, measure the gap, report it") reconcile without either being quietly dropped. Rejected:
  gating phase completion as in Phase 12 (would withdraw a correctness deliverable proven sound on
  8/8 seeds over a quality measurement the ruling already predicted would be mediocre) and reducing
  the benchmark to the CH-ordering question only (abandons XCUT-04 as written).

- **D-14:** The threshold measures **model-independent operational metrics**: hard feasibility
  (`0hard` reached), unstaffed demand slot-count, and total assigned hours (Phase 12's own metric).
  The soft-score gap is **reported alongside as the named plateau finding, explicitly not as a
  threshold** — shift mode gives `Break clustering` a real body and adds `shiftEnvelopeCompliance`,
  so the two arms do not evaluate the same constraint set and their soft totals are incommensurable.
  Thresholding on raw soft score would declare a regression that is partly an artefact of measuring a
  constraint the baseline never evaluated.

- **D-15:** **Pass rule, committed before the run:**
  - **Must-pass:** the shift arm reaches `0hard` on **every** seed.
  - **Comparative:** median unstaffed demand slots **no worse** than the slot arm's median.
  - **Noise rule:** any difference smaller than the slot arm's **own min/max spread** is written up as
    "no measurable difference" — never as a win *or* a loss, in either direction.

  The noise rule is Phase 12's lesson encoded as a rule rather than relearned: +0.25h against a 5.00h
  spread should never have read as an improvement. Rejected: an explicit tolerance band (a guess with
  no evidence behind it — only marginally better than no number) and requiring shift mode to strictly
  beat slot mode (the spike already predicts the plateau, so this pre-commits to a FAIL and makes the
  benchmark ceremonial).

- **D-16:** **Fixture: seeded synthetic A/B plus one indicative real run.** Reuse Phase 12's harness
  shape (`.planning/milestones/v1.2-phases/12-atomic-shift-move/12-BENCHMARK.md` — fixed seeds,
  `withStepCountLimit`, never wall-clock, median plus full min/max spread, never a mean),
  re-parameterised to ~130% over-allocation, system-property gated and out of the default suite;
  that arm carries the comparative threshold. Separately, **one non-comparative run against a real
  desk**, reported for scale and explicitly labelled indicative, which partly closes
  `SPIKE-COUPLING.md` open item 1 — whether the extra `AgentAssignment × AgentShiftAssignment` join
  costs materially at real scale (30 agents, 19 existing constraints) is currently untested.

### Claude's Discretion

Recorded with a recommendation and its reasoning, so the planner adopts or deviates deliberately
rather than guessing:

- **The two `OPEN_RESOLVE_IN_PHASE_15` constraints.** Phase 14 named this phase as owner of
  `honourPreferredStartTime` and `honourPreferredBreakTime`
  (`14-02-SUMMARY.md`, `XCUT-05-constraint-classification.md`). Recommendation: **mode-gate both off
  for shift-scheduled desks** and reclassify them `MODE_GATED`. In shift mode the start comes from the
  library and the break comes from the assigned band, so both constraints tune against a signal the
  operator no longer controls per-slot; `research/ARCHITECTURE.md` §1 reaches the same conclusion.
  Note the forward link: Phase 17's CONS-05 makes `AgentPreference.preferredStartTime` the tiebreak
  between two shifts the consistency constraint scores equally — that is a *new* use of the
  preference at shift granularity, not a reason to leave the per-slot constraints on.
- **`shiftEnvelopeCompliance`'s stream form.** Recommendation: the **plain positive-join form**
  (penalise only a definite disagreement between two initialised variables), not `ifNotExists` — the
  spike's explicit recommendation, CH-friendly, and matching the codebase's existing convention.
- **Where ENVL-07's ground-truth check lives.** Recommendation: a test-side walker over the solved
  schedule asserting no `AgentAssignment` falls outside its agent's envelope, run **outside** the
  score director, so it cannot inherit the score's own blind spot. This is the check that would have
  caught Option C.
- **How mode-gating reaches the constraint provider.** Recommendation: a `SchedulingMode` field on
  `ScheduleConfig` (already a `@ProblemFactProperty` record carrying per-desk scalars), read as a
  filter **at the top of** each affected constraint, leaving the four break constraints' existing
  bodies **untouched** — so a shift-mode regression cannot leak into slot-mode desks by construction.
- **ENVL-10's Agent Allocation view.** Grouping design, group-header content beyond shift name +
  headcount, and how an agent with no assigned shift renders. Constraint: a slot-scheduled desk must
  render exactly as it does today. `frontend/src/pages/ScheduleResults.tsx` `AgentAllocationTab`
  (~line 285) is the surface.
- **Whether a template with zero band rows still means "no break"** (Phase 14 permitted a
  zero-duration break). Recommendation: yes — zero bands = no break, preserving that affordance.
- **Candidate enumeration bounds for SHLB-07** — deriving the operating window and the break-offset
  step from the desk's timeslot grid, and whether the set cover is solved exhaustively, by ILP, or by
  Timefold. The ROADMAP is explicit that the candidate set is tens, not thousands: **do not
  over-engineer it, and it must not acquire its own quality-plateau argument.**
- **Naming of generated templates** and what effective date range they carry.
- **Exact Flyway migration number.** V39 is applied (Phase 14), so V40 is expected — but confirm the
  actual latest-applied version before writing, per this project's own recorded discipline (V30 was
  confirmed against V29 at Phase 10, and V39 shipped a type mismatch that no test could catch).
- **Whether the migration-coverage blind spot is closed here.** `application-test.yml` sets
  `flyway.enabled: false` with `ddl-auto: create-drop`, so no test runs the real migrations (UAT gap
  G-14-1). This phase adds a table, a child table, and drops two columns. A Testcontainers-backed
  boot test is a standing recommendation in STATE.md's Operator Next Steps — in scope as a planner
  judgement call, not mandated here.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase scope and requirements
- `.planning/ROADMAP.md` § "Phase 15: Shift Envelope, Breaks & Library Generation" — goal, 7 success
  criteria, and the Notes: the settled coupling mechanism, the operator ruling on the plateau, the
  break-bands ruling, why generation is tractable, and the generation/bands interaction.
  **Note D-09 adjusts the "shares no code path with the rest of the phase" claim in the SHLB-07 note.**
- `.planning/ROADMAP.md` § "🚧 v1.3" → "Settled before phase planning began (do not re-open)" — the
  three items that are not live questions.
- `.planning/ROADMAP.md` § "Phase 16" and § "Phase 17" Notes — what this phase must leave intact for
  them (`AgentShiftAssignment.shift` is solver output; the usual-shift *target* is not).
- `.planning/REQUIREMENTS.md` § "Shift Envelope (ENVL)", § "Shift Library (SHLB)" SHLB-07,
  § "Cross-Cutting Requirements" XCUT-01/03/04/05, § "Out of Scope" (custom moves, shift-level skill
  restriction, staffing caps inside a template), § "Known Risks Carried Into the Roadmap".

### Settled research — read before designing the solver model
- `.planning/research/SPIKE-COUPLING.md` — the empirical Option A vs Option C finding; § "Shared
  blocker: the bare `<constructionHeuristic/>` dies on the second entity class" (the exact working
  `QueuedEntityPlacerConfig`/`EntitySelectorConfig` form, `id` **and** `entityClass`); § "The
  inconvenient truth about Option A" (the plateau); § "What remains open" (7 items not established);
  § "Recommendation for phase planning".
- `.planning/research/ARCHITECTURE.md` § 1 "Coverage constraints" — the per-constraint table and the
  joint-unsatisfiability argument behind effective-hours filtering; § 2 "Break modelling"; § 3
  "Per-desk dual mode" (one solution class, one solver config, one constraint provider, mode-gated
  internally); § 5 "Data model"; § 7 "Migration/compat"; § "Anti-patterns to avoid" (Anti-Pattern 2
  is load-bearing for D-04).

### Prior phase decisions this phase inherits or amends
- `.planning/phases/14-shift-library-scheduling-mode/14-CONTEXT.md` — D-01 (fixed break offset, which
  D-01 here supersedes with bands), D-02 (grid alignment), D-04/D-05 (structural coverage over live
  demand; zero-demand refusal), D-06/D-07 (advisory hours mismatch; exact `BigDecimal` equality),
  D-08 (one validator, two callers), **D-09 (the accept-time snapshot obligation this phase must
  discharge)**, D-10 (no `active` boolean — the reasoning D-01 and D-11 here reuse), D-11 (template
  identity and the era hand-off), D-15 (the classification completeness test).
- `.planning/phases/14-shift-library-scheduling-mode/XCUT-05-constraint-classification.md` — the
  table this phase completes; the four `NEEDS_SHIFT_VARIANT` break constraints and the two
  `OPEN_RESOLVE_IN_PHASE_15` preference constraints with this phase named as owner.
- `src/test/java/com/wfm/solver/ScheduleConstraintClassification.java` — the completeness test that
  fails the build on an unclassified constraint. Its `PHASE_15_OWNER` constant still reads the phase's
  **former** name; `14-VERIFICATION.md` item 21 asserts the constant matches its markdown mirror, so
  if either is updated both must change in the same commit.

### Project state
- `.planning/PROJECT.md` § "Current Milestone: v1.3", § "Key Decisions", § "Known issues after v1.2"
  — audits I-1, I-2, I-3, NEW-1 (cited as rationale in D-01, D-03, D-07, D-11); the reverted third
  attempt and the orphaned `V38__add_consistent_start_weight.sql`.
- `.planning/STATE.md` § "Accumulated Context → Decisions" and § "Blockers/Concerns" — the migration
  numbering discipline, the Flyway test blind spot (G-14-1), and the pre-existing
  `GET /api/v1/agents` 500 unrelated to this phase.
- `.planning/milestones/v1.2-phases/12-atomic-shift-move/12-BENCHMARK.md` — the benchmark harness
  shape D-16 reuses and the +0.25h-against-5.00h-spread result D-15's noise rule encodes.
- `.planning/todos/pending/2026-08-13-cross-agent-seat-displacement.md` — this phase **measures** the
  same underlying gap and explicitly does not close it; the todo stays unlinked to any phase.

### Codebase maps
- `.planning/codebase/ARCHITECTURE.md` — layers, the `Schedule`/`AgentAssignment` planning model, the
  accept/persist snapshot flow, the error-shape contract. **Stale in two places:** it says 18
  constraints (the reflection-derived answer is 19) and V1–V24 migrations (head is V39).

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `src/main/java/com/wfm/service/ShiftLibraryValidationService.java` (288 lines) — `covers()` at
  ~line 161 is the predicate D-02 generalises and SHLB-07 must reuse rather than reimplement;
  `validate()` / `requireShiftModeReady()` are D-08's one-implementation-two-callers pair, and D-12
  reuses their `ErrorDetail` shape verbatim.
- `src/main/java/com/wfm/service/ShiftTemplateService.java` (238 lines) — the create/update/validate
  path a saved SHLB-07 draft goes through unchanged (D-11); `validateGridAlignment` and `isAligned`
  are the D-02 grid rules; `updateShiftTemplate` is the sole edit/retire mechanism and the reason
  D-07 cannot rely on an FK.
- `src/main/java/com/wfm/model/ShiftTemplate.java` — the entity D-01 amends; note
  `validWeekdaysMask` is a fixed-position 7-char string with `getValidWeekdays()`/`setValidWeekdays()`
  translating to `Set<DayOfWeek>`, and `getBreakStartTime()`/`getBreakEndTime()` are derived helpers
  that move to the band.
- `src/main/java/com/wfm/model/AgentAssignment.java` — the dual-purpose `@Entity` + `@PlanningEntity`
  shape `AgentShiftAssignment` mirrors. Its `nullable=true` is the deprecated form D-06 avoids.
- `src/main/java/com/wfm/model/AgentDayConfig.java` — `effectiveHours` per agent-day; D-05's row
  source and the value-range filter's input, already resolved before every solve.
- `src/main/java/com/wfm/model/ScheduleConfig.java` — the `@ProblemFactProperty` record carrying
  per-desk scalars (including `breakClusterThresholdPct`); the mode field rides here.
- `src/main/java/com/wfm/model/ConstraintWeights.java` — `breakClusteringWeight` defaults to
  `HardSoftScore.ofSoft(2)` and is already wired; ENVL-09 gives the constraint a body, it does not
  need a new weight. A new weight column IS needed for `shiftEnvelopeCompliance`.
- `src/main/java/com/wfm/util/BigDecimals.java` — the comparison helper D-07's exact-equality match
  already uses.
- `.planning/milestones/v1.2-phases/12-atomic-shift-move/12-BENCHMARK.md` — the seeded,
  step-count-terminated harness D-16 reuses.

### Established Patterns
- **Accept-time snapshot** (`ScheduleService.acceptSchedule`): live rows carry `scheduleId` null,
  accepting copies them into immutable schedule-scoped rows with FKs remapped. D-07 deliberately
  chooses denormalisation over a third instance of this pattern — a documented deviation, not an
  oversight.
- **Configuration-driven constraint behaviour**: `minimumStaffing`'s javadoc records that
  hard-vs-soft is "a per-desk configuration row, not a code decision". Mode-gating is the same
  pattern at slightly larger scope.
- **Typed exception → HTTP status** via `GlobalExceptionHandler`;
  `PreSolveValidationException` → 400 `VALIDATION_FAILED` with a populated `details` array.
- **Multi-tenancy in application code only** — every entity carries `tenant_id`, enforced by
  repository filters, no DB row security. Both new tables follow this without exception.
- **Flyway forward-only** — an applied migration can never be edited or deleted. (V39 was edited in
  place only because it was unreleased; that exemption is spent.)

### Integration Points
- `src/main/resources/solverConfig.xml` — **changed for the first time this milestone**: a second
  `<entityClass>` plus two explicitly-scoped CH phases. Gated by XCUT-03's build-a-solver test; note
  the standing caveat that no test under `src/test/java/com/wfm/solver/` loads the Spring context.
- `src/main/java/com/wfm/solver/ScheduleConstraintProvider.java` — `breakClustering` at line 573 is
  the `penalizeConfigurable(a -> 0)` placeholder ENVL-09 replaces; `exactlyOneBreak` (188),
  `breakDuration` (238), `breakBlockedWindow` (264), `breakStartAlignment` (301) are the four to
  mode-gate without touching their bodies; `defineConstraints` at ~line 75 registers the set the
  completeness test derives.
- `src/main/java/com/wfm/service/SolverService.java` — `buildSchedule` reads `Desk.schedulingMode`
  and populates `AgentShiftAssignment` rows; `runPreSolveValidation` (~657) is where any new refusal
  belongs; `resolvePreferences` (~488) is the shape Phase 16 copies, not this phase.
- `frontend/src/pages/ShiftLibrary.tsx` — gains the band editor (D-01 changes what it writes) and the
  SHLB-07 suggestion panel (D-11).
- `frontend/src/pages/ScheduleResults.tsx` — `AgentAllocationTab` (~line 285) is ENVL-10's surface;
  slot-scheduled desks must render unchanged.
- New: `shift_template_break_band` and `agent_shift_assignment` tables, a
  `shift_envelope_compliance_weight` column on `constraint_weights`, and the drop of
  `break_offset_minutes` / `break_duration_minutes`.

</code_context>

<specifics>
## Specific Ideas

- The live failure this phase's break work exists to fix, in concrete terms: on the StubHub (EN)
  library, ~15 Early-shift agents all break 12:00–13:00, leaving that window's 8–12 FTE demand to
  Late-shift agents alone — while the constraint that should flag it (`breakClustering`) is
  `penalizeConfigurable(a -> 0)` and the knob that should tune it (`breakClusterThresholdPct`) is
  wired to nothing.
- The ENVL-09 demonstration the ROADMAP asks for: a fixture where a **single-band** library
  measurably starves a mid-shift timeslot and a **multi-band** library does not. That contrast is the
  requirement's own acceptance test, not an illustration.
- Worked example carrying D-01 forward: template `08:00–17:00` with bands at offsets 240 and 300,
  each 60 minutes → some agents break `12:00–13:00`, others `13:00–14:00`, net working duration 8h
  either way, and D-02 now calls the 12:00–13:00 window covered by that single template.
- The phase's honesty commitment, in one line: the plateau is a **measured finding reported in
  `15-BENCHMARK.md`**, not a discovery left for UAT and not a problem this phase tries to solve.

</specifics>

<deferred>
## Deferred Ideas

- **Continuous break *window*** (earliest/latest offset with a solver-chosen start) — strictly more
  expressive than bands, rejected in the ROADMAP because it re-opens the four emergent break
  constraints this phase is gating off. Bands are enough to break up the clustering.
- **A `SelectionFilter` layered on `shiftEnvelopeCompliance`** — a legitimate later search-efficiency
  tune once Option A's own baseline is measured. Never a replacement for the hard constraint.
- **Any custom Timefold move** (combined shift-plus-seats) — the obvious remedy for the plateau, and
  explicitly Out of Scope for v1.3. Reopening it needs its own evidence-led decision, not inheritance
  from this phase's benchmark.
- **Capacity-aware coverage validation** — rejected again here (D-02 keeps coverage structural),
  carried from Phase 14's deferred list.
- **Promoting break config from `Schedule` to `Desk`** — carried from Phase 14 D-03; a legitimate
  cleanup once shift mode is proven, still not this phase.
- **Multiple *distinct* breaks per agent per day** (as opposed to multiple bands to choose one from)
  — still not modelled. Bands give the solver a choice of *when* to take the one break, not a second
  break.
- **A Testcontainers-backed migration boot test** — standing recommendation from Phase 14's G-14-1;
  noted in Claude's Discretion as a planner judgement call, otherwise carried forward.

### Reviewed Todos (not folded)
- `2026-08-13-cross-agent-seat-displacement.md` (score 0.9, area: solver) — **reviewed and
  deliberately not folded.** This phase *measures* the same underlying gap (a shift and its seats must
  move together; a plain change-move neighbourhood only moves one at a time) and explicitly does not
  close it. PROJECT.md records the todo as deliberately unlinked from any phase so a phase close
  cannot auto-sweep it away unresolved; its own frontmatter carries that warning. It must remain
  unlinked at Phase 15 close.
- `2026-07-30-blank-upload-template-one-sheet-per-desk.md` (score 0.6, area: upload) — upload-template
  work; overlaps Phase 16 (USHF-02 adds a Usual Shift column to that same template), not this phase.
- `2026-08-14-terraform-db-password-drift.md` (score 0.6, area: infra) — infrastructure; matched on
  the word "phase" only.

</deferred>

---

*Phase: 15-Shift Envelope, Breaks & Library Generation*
*Context gathered: 2026-08-26*
