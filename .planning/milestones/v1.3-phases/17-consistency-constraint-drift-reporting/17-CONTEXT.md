# Phase 17: Consistency Constraint & Drift Reporting - Context

**Gathered:** 2026-09-04
**Status:** Ready for planning

<domain>
## Phase Boundary

Make the solver **aim at** the usual shift Phase 16 stored — a soft, operator-tunable penalty on
deviation from it — and make the resulting drift visible to the operator. This is the first phase in
which the solver reads `agent_usual_shift` at all.

Three things ship together: a **target-deviation** consistency constraint (every worked agent-day
compared against its stored target), **per-desk configuration** of the tolerance band and weight, and
a **drift report** derived from the same distance calculation the constraint uses.

A solve **never writes** the stored usual-shift target. The target/result separation Phase 16
established (`AgentUsualShift` = target, `AgentShiftAssignment` = result) is preserved exactly.

**Not in this phase:** any mitigation for the consistency-versus-fairness tension DRFT-04 makes
visible (Backlog 999.4), break-placement consistency (the reverted `9f4a96f`'s subject), and any
custom Timefold move (explicitly Out of Scope in REQUIREMENTS.md; Phase 12 built and withdrew one).

Depends on Phase 15 (`AgentShiftAssignment` to compare against) and Phase 16 (the stored target and
its era resolver).

</domain>

<decisions>
## Implementation Decisions

### Distance definition — the one calculation DRFT-03 forces everything to share

- **D-01:** Deviation is **`|assigned envelope start − resolved usual shift start|`, in minutes**.
  Rejected: template-name identity (collapses CONS-02 — there is no magnitude to be *inside* a
  tolerance band, and DRFT-01's "by how much" degenerates to yes/no) and a composite
  `max(start delta, end delta)` (catches the same-start/different-duration case but is not strictly
  more informative, and a wholesale shift move scores identically either way). Known blind spot,
  accepted: a template with the same start and a different end scores **zero** drift — duration is
  already policed by the contracted-hours constraints, and the column being adopted
  (`consistent_start_weight`) is a start-time column by name.
  — **Reversibility:** costly — DRFT-03 makes this one function with two callers (constraint and
  report), so changing it changes both surfaces at once *and* invalidates the D-06 benchmark that
  set the shipped default. Follows Phase 16 D-01's precedent of rating a decision that would need
  re-derivation across dependents as `costly` rather than `one-way`.

- **D-02:** Past the tolerance band the penalty is **linear in excess increments**
  (`excess minutes / ScheduleConfig.incrementMinutes`), charged **per agent-day**. Rejected:
  quadratic (PROJECT.md carries an unbuilt Key Decision favouring quadratic, but that row was
  reasoned for QUAL-03 *hours* consistency, deferred to 999.4 — and it multiplies dangerously with a
  default the phase has not yet validated) and flat-1-per-drifting-agent-day (reintroduces a plateau,
  which is precisely the flaw the reverted attempt's own javadoc named: *"the search sees a plateau,
  not a gradient"*). Charging in increments rather than raw minutes keeps the weight comparable
  across 15/30/60-minute desks.

- **D-03:** The agent's "start" for drift purposes is the **assigned envelope start**
  (`shiftBandPair.template().getStartTime()`), **not** their earliest seated `AgentAssignment`.
  Rationale: in this model the shift *is* the envelope, and it is the thing with a name the operator
  recognises. It is also a pure problem-fact comparison — it never depends on how seats fell inside
  the envelope, so the constraint and the report read the same field with no derivation between them.
  The reverted `6fb78c7` used earliest-assigned-slot, but that was a **slot-mode** definition; under
  V44's bounded envelope slack it would mix shift choice with seat placement and make the drift
  report move when nothing about the shift changed. Accepted consequence, to be stated in the code
  comment: an agent on the Early envelope whose first seat falls 90 minutes in still reads as zero
  drift.

### Tolerance band and weight configuration

- **D-04:** The tolerance band is an **integer-minutes column on `constraint_weights`**, alongside
  `consistent_start_weight`, so the band and the weight acting on it are one row, one screen, one
  API call. Rejected: `Desk` (clean typing, but splits band and weight across two operator screens —
  the shape that gets one tuned and the other forgotten) and `Schedule`/`SolveRequest` (the actual
  house pattern for tunable scalars — `breakClusterThresholdPct`, `overallocationHardLimitPct`,
  `breakBlockedHours` all work that way — but it is **per-solve, not per-desk**, and so does not
  satisfy CONS-02 as written).
  **This deliberately breaks a convention and the plan must say so in the code, not diverge
  silently:** `constraint_weights` is a Timefold `@ConstraintConfiguration` in which every non-key
  column is a `HardSoftScore`. A plain `int` is a new kind of column there, and
  `ConstraintWeightsDto` / `ConstraintWeightsPage.tsx` both assume `ScoreDto` for every field, so
  both grow a second field shape.
  — **Reversibility:** costly — undoing it means a migration plus a change to an operator-facing
  config contract (the Constraint Weights API and page) that operators will have already used.

- **D-05:** The band is **symmetric** — one value, applied to `abs(delta)`. CONS-02 says "a tolerance
  band", singular, and criterion 2 requires the shipped default be validated against the existing
  soft-constraint hierarchy via `SolutionManager.explain()`; one knob is one validation, two knobs is
  a matrix. Asymmetry (early ≠ late) is a real operational distinction — an agent pulled 90 minutes
  *earlier* has a childcare and commute problem, 90 minutes *later* is mostly a coverage question —
  but it can be added later without changing what any stored value means. Deferred, not rejected.

- **D-06:** The shipped default weight is **decided by the benchmark, inside this phase**. Commit the
  threshold first (XCUT-04), run the seeded A/B at ~130% over-allocation, read the `explain()`
  constraint-match breakdown against the existing soft hierarchy, then write the resulting value as
  the migration's default.
  **Plan-ordering consequence, and it is load-bearing:** the benchmark task **gates** the migration
  that sets the default, so the benchmark cannot be the last task in the phase.
  **The trap this decision exists to avoid:** V38 already ships `consistent_start_weight` at
  `0hard/2soft` on dev, and its migration comment justifies that number with arithmetic for a
  **per-agent** penalty — *"on the live desk's 28 CSRs a four-increment spread each is
  28 × 4 × 2 = 224 soft — enough to break ties between equal-coverage schedules, not enough to outbid
  bulk under-allocation or minimum staffing."* D-02 charges **per agent-day**. Same desk, same 2 soft,
  five working days ≈ **1,120 soft**, which now sits *above* `minStaffingWeight`'s 1,000 and can start
  buying consistency with uncovered hours — the exact outcome that comment was written to prevent.
  Inheriting 2 soft unexamined is the failure mode.

- **D-07:** CONS-04 is **enforced, not documented**: the service layer rejects a
  `consistent_start_weight` with a non-zero hard component (400, in the shape
  `DeskAgentService.setDayHours` already uses for out-of-range hours). This makes "never renders a
  feasible schedule infeasible" a structural property rather than a discipline — the same
  enforce-don't-document reasoning behind Phase 16's D-14 guard and Phase 14's reflection test.
  **This deliberately diverges from Phase 15's precedent** that hard-vs-soft is *"a per-desk
  configuration row, not a code decision"* (`shiftEnvelopeComplianceWeight`, `bandCapacityWeight`),
  and the divergence must be stated in the code comment: for those constraints **hard is the correct
  setting**; here hard is a documented failure mode. V38's own comment says so — *"A hard consistency
  rule does not force consistent starts; it forces shorter shifts… setting `1hard/0soft` here is
  available but is the known failure mode, not a stricter setting."* Rejected: enforcing softness
  inside the constraint while letting any value be stored — that makes the stored config lie about
  behaviour, which is the divergence class audit I-1 was about.

### Preference precedence (CONS-05 / CONS-06)

- **D-08:** A **new shift-granularity preference constraint** penalising
  `|assigned envelope start − preferredStartTime|`, with its **own weight column**, weighted strictly
  below consistency — and the ordering is **enforced at config-save** (reject a preference weight
  `>=` the consistency weight) and covered by a **named test**.
  **Why this shape and not the obvious one:** CONS-06 says the precedence must not be *"implicit in
  relative constraint weights a reader would have to reverse-engineer"*. A lower weight alone *is*
  relative weights; the save-time rejection plus the test is what converts it into a stated,
  checkable invariant.
  **The finding that forces a new constraint at all:** `honourPreferredStartTime` is **mode-gated OFF
  on shift desks** (Phase 15, ENVL-05/P-26), and its javadoc says so pointedly —
  *"Phase 17's CONS-05 use of `preferredStartTime` at shift granularity is a new use, not a reason to
  leave this per-slot constraint on."* The roadmap's "transfers as-is" salvage item (`7861b83`,
  preferred start as an **anchor rather than a floor**) therefore patches a constraint that **never
  fires** on the desks this phase is about. Its *insight* still transfers: write the new constraint
  anchor-style from the outset — penalise the absolute delta in both directions, never only lateness.
  Rejected: a search-order tie-break via value-range ordering or a difficulty comparator (zero weight
  interaction and trivially CONS-04-safe, but it is a nudge rather than a guarantee and contributes
  nothing to `explain()`, leaving CONS-06's "observable" with nothing to point at), and a single
  constraint with lexicographic arithmetic `(consistency excess × K) + preference excess` (precedence
  becomes provable arithmetic, but `explain()` then shows one line, so criterion 2's breakdown can no
  longer separate the two effects).
  — **Reversibility:** costly — a Timefold constraint name is a contract: it appears in
  `ScoreAnalysis`/`explain()` output, in `ConstraintWeights` as a `@ConstraintWeight` annotation
  value, and in `ScheduleConstraintClassification`. Removing it later means a migration plus a
  classification-table edit.

- **D-09:** The new preference constraint **fires independently of whether a usual shift is stored**.
  Consistency is silent for agents with no target (USHF-04 makes "no stored usual shift" a
  penalty-free state), so preference becomes their only start-time signal. This also closes a
  regression Phase 15 opened without closing: gating `honourPreferredStartTime` off for shift desks
  left agents who have a recorded preference and no usual shift with **nothing honouring it at all**.
  This reads **wider than CONS-05's literal wording** ("where the consistency constraint scores two
  shifts equally"), and the plan must say so explicitly rather than let it look accidental.

- **D-10:** CONS-06's "documented and observable" means exactly **three artefacts**, no more:
  1. the D-08 save-time rejection that makes the ordering true;
  2. **two separate constraint lines in `SolutionManager.explain()`** so the breakdown shows which
     fired and by how much (this is the same mechanism criterion 2 requires for weight validation —
     `SolverService.runPreSolveScoreDiagnostic` at `SolverService.java:1830` is the existing caller);
  3. **copy on the Constraint Weights page** stating the precedence, next to the two adjacent weights.
  Rejected: per-row attribution in the drift report marking ties resolved by preference (most direct
  for the operator asking "why did Ana get Late?", but it makes the drift report reason about a second
  constraint, coupling two things DRFT-03 exists to keep as one calculation), and documentation +
  test only (observable to a developer reading the repo, which is close to the reverse-engineering
  outcome the requirement was written against).

### Drift report surface (DRFT-01…04)

- **D-11:** The report is **derived on read** — a `buildDriftReport(schedule)` alongside
  `ScheduleOutputService.buildPreferenceReport(schedule)` (`ScheduleOutputService.java:289`),
  resolving each agent-day's target through `UsualShiftResolutionService` at read time. No new table,
  no migration. This is the house pattern, and `preferenceReport` already behaves this way — edit a
  preference after a solve and the report moves. It is also coherent with Phase 16 D-01: "Ana's usual
  shift is Early" follows Early across eras, so a report that follows it too makes the same promise.
  **Accepted consequence, to be stated openly rather than discovered:** a past schedule's drift report
  can change when nobody touched that schedule — because the target moved. Rejected: a solve-time
  snapshot (better for "what did we know when we ran this", but it needs a table and a migration and
  it freezes exactly what D-01 decided not to freeze) and stamping the resolved target onto
  `AgentShiftAssignment` (snapshot stability with no new table, but it makes the **solver** a writer
  of usual-shift-derived data, which Phase 16's USHF-05 write-path table would need a new row and a
  new test for).

- **D-12:** **One row per agent-date**, mirroring `PreferenceReportEntry`
  (`ScheduleDetailResponse.java:120`) — `agentId`, `agentName`, `date`, plus an explicit
  **three-state field**: `NO_USUAL_SHIFT` / `HONOURED` / `DRIFTED`, with usual start, actual start
  and delta-minutes populated when drifted. DRFT-02's distinction is therefore a **field**, not an
  inference from a null — the same shape Phase 16 D-16 gave the roster tile, and the same distinction
  audit I-1 was about. Date filtering comes free from the existing `ScheduleService.java:220-224`
  pattern. Rejected: per-agent aggregation (DRFT-01 asks for "on which dates" explicitly, so the
  dates end up in a nested list anyway — the per-agent-date shape with extra steps) and detail rows
  *plus* a per-agent rollup (most informative, but `PreferenceReport`'s summary is a single
  whole-report block, so it would introduce a third summary level the codebase does not have).

- **D-13:** DRFT-04's over-subscription view is a **second section of the same drift tab** — template
  name, how many agents hold it as their usual shift, ranked — keeping the consistency-versus-fairness
  tension next to the drift it explains. It answers a **different question** from the rest of the tab
  (it reads stored usual shifts, not solve results) and therefore needs a heading that says so.
  Rejected: its own tab (a whole tab for one small table, and it separates the tension from the
  symptom) and putting it on the Shift Library page (arguably the most actionable placement, but
  DRFT-04 is worded as something seen after a solve, and it would put Phase 17 work into a Phase 14
  surface).

- **D-14:** The drift report is **also written into the Excel schedule export**, as its own sheet
  alongside the preference report (`ScheduleExportService.writePreferenceReport`,
  `ScheduleExportService.java:172`). XCUT-01 names "export" as a surface that must show what was
  written, and leaving it to a judgement call about whether the export counts is the kind of ambiguity
  that produced audit I-1.

### Claude's Discretion

- **Rounding mode** for the minutes→increments conversion in D-02. `ShiftBandPair`'s javadoc warns
  that `ScheduleConstraintProvider` *"already carries two rounding modes (HALF_UP and CEILING) in
  other constraints; this predicate must not introduce a third"* — pick one of the two that already
  exist and say which and why in the code.
- **Whether the penalty is skipped on days the agent does not work.** Phase 16 D-04 stores a usual
  shift on non-working days deliberately, "stored and inert"; the natural reading is that the
  constraint produces no tuple for a `0`-hours / `MANDATORY` / `PTO` day, but confirm against how
  `AgentDayConfig` is already consulted in the provider.
- **Mode gating for both new constraints.** `shiftEnvelopeCompliance` is structurally inert on a SLOT
  desk yet still carries an explicit `SchedulingMode.SHIFT` filter — partly defence in depth, and
  partly a **documented performance contract** about stream ordering (leading with the empty
  `AgentShiftAssignment` stream keeps the node network dead on a slot desk; the javadoc measures ~3×
  construction-heuristic throughput and says *"Do not reorder these joins"*). Follow that shape.
- **Constraint names, weight-column names, DTO shape, endpoint paths, test organisation** — follow the
  existing conventions (`shiftWorkContiguityWeight` / `V45` / `V46` are the most recent models).
- **Migration number:** head on disk is **V47** (`V47__add_agent_usual_shift.sql`), so the next is
  **V48** — confirm the actual latest-applied version before writing it, per this project's own
  recorded discipline.
- **Whether the band's default minutes value is benchmark-derived too, or set by judgement.** D-06
  binds the *weight* to the benchmark; the band was not separately discussed.
- **How the band renders on the Constraint Weights page** given it is the only non-`ScoreDto` field
  among 22 score fields.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase scope and requirements
- `.planning/ROADMAP.md` §"Phase 17: Consistency Constraint & Drift Reporting" — goal, the five
  success criteria, and the **Notes** block sorting the reverted third attempt into transfers-as-is /
  transfers-with-rework / needs-reformulation. Note that D-08 above **revises** the "transfers as-is"
  entry on evidence.
- `.planning/REQUIREMENTS.md` §"Consistency (CONS)" CONS-01…06 and §"Drift (DRFT)" DRFT-01…04
  verbatim; §"Cross-Cutting Requirements" XCUT-01 (display verified in every surface), XCUT-02 (every
  reachable write path), XCUT-04 (seeded A/B with the threshold committed first); §"Out of Scope" —
  custom Timefold moves and shift-level skill restriction are excluded by name; §"Known Risks" — the
  soft-quality plateau, and the reverted-attempt row whose limit-of-claim is explicit
  (*"'removed for scope' is well-supported, 'it worked' is not established"*).
- `.planning/PROJECT.md` §"Current Milestone" (the consistency-constraint and drift-report target
  features); §"Known issues after v1.2" (the V38 orphan, and the reverted third attempt with all six
  revert SHAs); §"Key Decisions" (the unbuilt quadratic-penalty row, Timefold pinned at 1.16.0, the
  Phase 12 withdrawal ruling).

### The column being adopted
- `src/main/resources/db/migration/V38__add_consistent_start_weight.sql` — **read the whole comment.**
  It is the single most load-bearing document for D-06 and D-07: it explains why the weight must stay
  soft, and its sizing arithmetic is stated for a **per-agent** penalty that D-02 replaces with a
  per-agent-day one. The comment cannot be edited (Flyway forward-only, already applied on dev), so
  the corrective note belongs in the new migration.

### Prior-phase decisions this phase inherits
- `.planning/phases/16-usual-shift-storage/16-CONTEXT.md` — D-01/D-02 (era resolution by **name**;
  "no era in effect" is identical to unset), D-04 (a usual shift may be stored on a non-working day,
  inert), D-16 (the roster tile's three states, which D-12 mirrors), and the Claude's Discretion note
  that `UsualShiftResolutionService` is deliberately the **only** implementation of that precedence
  shape — *"Do not create a second copy of this method."*
- `.planning/phases/15-shift-envelope-breaks-library-generation/15-CONTEXT.md` — how the
  target-vs-result separation actually landed, and the ENVL-05/P-26 reclassification that gated
  `honourPreferredStartTime` and `honourPreferredBreakTime` off for shift desks.
- `.planning/phases/15-shift-envelope-breaks-library-generation/15-BENCHMARK.md` — the format and
  discipline D-06's benchmark must follow (threshold committed before the run in `cd26db9`, median
  **and** full min/max spread, honest write-up of a null result).
- `.planning/phases/14-shift-library-scheduling-mode/XCUT-05-constraint-classification.md` — the
  constraint classification table. **This phase adds two constraints and must extend it**;
  `src/test/java/com/wfm/solver/ScheduleConstraintClassificationTest.java` is a reflection-derived
  completeness test that will fail otherwise, by design.
- `.planning/milestones/v1.2-MILESTONE-AUDIT.md` — findings I-1 (model built, view never migrated)
  and I-2 (guarantee held on one write path only). D-12 and D-14 exist because of these two.

### Handoff / open items
- `.planning/phases/15-shift-envelope-breaks-library-generation/HANDOFF.md` — suite baseline and
  runtime budget.
- `.planning/phases/16-usual-shift-storage/deferred-items.md` — what Phase 16 left open, so this
  phase does not inherit it by accident.
- `.planning/phases/16-usual-shift-storage/COVERAGE.md` — Phase 16's coverage record.
- `src/test/resources/ushf-05-write-paths.md` — Phase 16's nine enumerated write paths. This phase
  adds no new writer of `agent_usual_shift`; the solver reading it must be proven **not** to write it.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `src/main/java/com/wfm/service/UsualShiftResolutionService.java` — `resolve(stored, date)`, the ONE
  era resolver. Its javadoc **already names this phase's drift report as a caller**. Do not write a
  second copy; the codebase's own cautionary example (`resolvePreferences` duplicated at
  `SolverService.java:567` and `ScheduleService.java:486`) is called out there by name.
- `src/main/java/com/wfm/service/ScheduleOutputService.java:289` `buildPreferenceReport(schedule)` →
  `:412` `new PreferenceReport(entries, summary)` — the exact template for `buildDriftReport`. It
  already holds the cached Timefold `SolutionManager` (`:32`, `:35`).
- `src/main/java/com/wfm/dto/ScheduleDetailResponse.java:115-130` — `PreferenceReport` /
  `PreferenceReportEntry` records; `:37` the response field; the drift equivalents sit alongside.
- `src/main/java/com/wfm/service/ScheduleService.java:156` (wiring) and `:220-224` (date filtering of
  report entries) — copy both.
- `src/main/java/com/wfm/service/ScheduleExportService.java:172` `writePreferenceReport` and its call
  site at `:28` — the model for D-14's drift sheet.
- `frontend/src/pages/ScheduleResults.tsx:259` (tab bar) and `:989` `PreferenceTab` — the tab to
  mirror. Existing tabs: Staffing Summary, Agent Schedule, Agent Allocation, Preference Report,
  Constraint Violations, PTO.
- `src/main/java/com/wfm/model/ConstraintWeights.java` — the `@ConstraintConfiguration`. Note
  `consistent_start_weight` is the **one column in the table with no field here** (the V38 orphan);
  `shiftWorkContiguityWeight` is the most recent addition and its javadoc is the model for
  documenting a weight's sizing rationale in code.
- `src/main/java/com/wfm/dto/ConstraintWeightsDto.java` +
  `src/main/java/com/wfm/service/ConstraintWeightsService.java` +
  `src/main/java/com/wfm/controller/ConstraintWeightsController.java` +
  `frontend/src/pages/ConstraintWeightsPage.tsx` (the `CONSTRAINTS` array at `:6`) — the four-step
  chain every new weight walks. **This is not salvage:** `ConstraintWeightsPage.tsx` dates from the
  initial skeleton (`3a56835`) and Phases 15–16 already extended the chain three times. The roadmap's
  "transfers with rework" characterisation understates how routine this is.
- `src/main/java/com/wfm/solver/ScheduleConstraintProvider.java:422` `shiftEnvelopeCompliance` — the
  pattern for a SHIFT-mode constraint, including the **stream-ordering performance contract** in its
  javadoc (lead with the `AgentShiftAssignment` stream, gate on `SchedulingMode.SHIFT` before touching
  `AgentAssignment`; measured ~3× construction-heuristic throughput; *"Do not reorder these joins"*).
- `src/main/java/com/wfm/solver/ScheduleConstraintProvider.java:813` `honourPreferredStartTime` — read
  its javadoc before writing D-08's constraint. It is mode-gated off for SHIFT and states the
  reasoning that makes D-08 a new constraint rather than a revival.
- `src/main/java/com/wfm/model/AgentShiftAssignment.java` — `shiftBandPair` is the planning variable;
  `templateName` / `shiftStartTime` / `sourceTemplateId` are denormalised fact alongside it.
- `src/main/java/com/wfm/model/ShiftBandPair.java` — the `covers(...)` static overload is this
  project's worked example of D-08 "one predicate, two callers", written up in its own javadoc. The
  drift distance function should be built the same way from the start.
- `src/main/java/com/wfm/service/SolverService.java:1830` `runPreSolveScoreDiagnostic` and `:1840`
  `SolutionManager` — the existing `explain()` caller criterion 2's weight validation uses.
- `src/test/java/com/wfm/support/PostgresBackedTest.java` — real Flyway + `ddl-auto=validate` via
  Testcontainers, **only for classes that extend it**. The default suite runs `flyway.enabled: false`
  with `ddl-auto: create-drop` on H2, which is how V39 shipped a `CHAR(7)`/`varchar(7)` mismatch
  through a fully green 402-test suite. The V48 migration wants a test that extends it.

### Established Patterns
- **One computation, multiple callers** (Phase 14 D-08). DRFT-03 is this rule restated as a
  requirement: the distance function must have exactly one implementation, called by the constraint
  and by `buildDriftReport`.
- **Reject, don't clamp**, on interactive single-value writes (`DeskAgentService.setDayHours`) — D-07's
  hard-weight rejection follows it.
- **Structural / reflection completeness guards over editable counts** (Phase 14's classification
  test, Phase 10 D-16, Phase 16 D-14). `ScheduleConstraintClassificationTest` is one and will fail
  until the two new constraints are classified.
- **Per-desk weights live on `constraint_weights`; per-solve scalars live on `Schedule` and travel via
  the `ScheduleConfig` record.** D-04 deliberately puts a scalar in the first place because CONS-02
  says per-desk — the only convention break in this phase, and it must be commented as intentional.
- **Benchmark discipline** (XCUT-04, Phase 15): threshold committed to git *before* the run, median
  and full min/max spread reported, a result inside the comparison arm's own spread written up as
  "no measurable difference". Phase 12 was withdrawn under exactly this rule.

### Integration Points
- New Flyway migration (**V48**, confirm the live head first): the tolerance-band minutes column, the
  new preference weight column, and D-06's benchmark-determined default for `consistent_start_weight`.
- `ConstraintWeights` gains the missing `consistentStartWeight` field (the V38 orphan) plus the band
  and the new preference weight; `ConstraintWeightsDto` / Service / Controller / `ConstraintWeightsPage`
  follow.
- `ScheduleConstraintProvider` gains two constraints and their entries in the constraint list at `:85`.
- `ScheduleConstraintClassification` (test) gains two rows.
- `ScheduleOutputService` gains `buildDriftReport`; `ScheduleDetailResponse` gains the records and
  field; `ScheduleService` wires and date-filters it; `ScheduleExportService` writes the sheet;
  `ScheduleResults.tsx` gains the tab.
- `SolverService` must supply the resolved usual-shift target as a **problem fact** to the solver —
  this is the phase's one genuinely new solver-input plumbing, and the read path must be proven not to
  write (XCUT-02).

</code_context>

<specifics>
## Specific Ideas

- **The V38 arithmetic is the phase's headline hazard, stated in the operator's own terms:** at
  `0hard/2soft` on the live desk's 28 CSRs over five working days, a per-agent-day target-deviation
  penalty reaches ≈1,120 soft — above `minStaffingWeight`'s 1,000. That is consistency buying
  uncovered hours, which is exactly what V38's comment says must never happen. D-06 exists to catch
  this before it ships, not after.
- **`7861b83` does not "transfer as-is".** The roadmap says it does; the code says
  `honourPreferredStartTime` is mode-gated off for shift desks and its javadoc names Phase 17's use as
  *new*. Take the idea (anchor, not floor — penalise the absolute delta) into the new D-08 constraint;
  do not re-apply the commit.
- **The operator-language test for D-03:** "Ana was put on Late, not Early." That is a statement about
  which envelope she got, not about when her first seat happened to fall — which is why envelope start
  is the right measure even though the reverted report measured the other thing.
- **D-11's accepted consequence should be visible, not buried:** because the target follows the
  template name across eras (Phase 16 D-01), editing a shift template can change a *past* schedule's
  drift report. This is coherent, but an operator will find it surprising the first time. Worth a line
  in the panel, not just a code comment.
- **D-04 and D-07 both break a stated precedent on purpose** (a non-score column in a
  `@ConstraintConfiguration`; enforcing soft where Phase 15 made hard-vs-soft configurable). Phase 16
  D-03 set the house style for this: say so in the code comment, with the reason, rather than diverge
  silently.

</specifics>

<deferred>
## Deferred Ideas

- **Asymmetric tolerance (early vs late)** — a real operational distinction, deliberately deferred
  under D-05 because it doubles the criterion-2 validation surface. Addable later without changing the
  meaning of any stored value.
- **A matching shift-granularity preferred-**break**-time constraint.** `honourPreferredBreakTime` is
  mode-gated off for shift desks exactly as `honourPreferredStartTime` is, and the reverted `9f4a96f`
  was a break-offset consistency constraint. Phase 17's requirements cover start times only, so this
  is a real hole the phase does not close. Its own decision, later.
- **Any mitigation for the over-subscription DRFT-04 makes visible** — shift bidding, rotation,
  automated reshuffle. Explicitly out of scope (FAIR-01, Backlog 999.4); DRFT-04 deliberately builds
  visibility with no remedy.
- **Per-row attribution in the drift report** marking ties resolved by preference — rejected under
  D-10 for coupling the report to a second constraint's reasoning, but it is the most direct answer to
  an operator asking "why did Ana get Late?".
- **Subscription counts on the Shift Library page** — rejected under D-13 as Phase 14 surface, but it
  is where the operator would actually act on over-subscription.
- **Composite start+end distance** — rejected under D-01, leaving same-start/different-duration
  templates scoring zero drift. Revisit if operators report it.

### Reviewed Todos (not folded)
- `2026-08-13-cross-agent-seat-displacement.md` — matched at 0.9 on solver keywords. Its own
  frontmatter says to keep it unlinked from any phase so a phase close cannot auto-sweep it away
  unresolved, and PROJECT.md records that v1.3 measures the underlying gap without closing it. Same
  ruling as Phase 16: reviewed, not folded.
- `2026-07-30-blank-upload-template-one-sheet-per-desk.md` — matched at 0.6, but it is Phase 10 scope
  and appears delivered by UPL-09. Unrelated to this phase.
- `2026-08-14-terraform-db-password-drift.md` — matched at 0.6 on generic keywords; infrastructure,
  unrelated.

</deferred>

---

*Phase: 17-Consistency Constraint & Drift Reporting*
*Context gathered: 2026-09-04*
