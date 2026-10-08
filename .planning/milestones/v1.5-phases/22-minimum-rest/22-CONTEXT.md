# Phase 22: Minimum Rest - Context

**Gathered:** 2026-10-03
**Status:** Ready for planning

<domain>
## Phase Boundary

An operator can require a minimum shift-to-shift gap per desk, enforced as a hard constraint the
solver cannot silently violate, refused before the solve where it is structurally unavoidable, and
waivable for one agent on one business date with the waiver visible in the solved schedule's output.

Requirements: **REST-01 … REST-07.**

**The phase is substantially larger than the ROADMAP entry implies, by operator decision taken in
this discussion.** Measured on HEAD during this discussion, not assumed:

1. **This is greenfield in the solver.** The constraint registry holds 26 constraints
   (`ScheduleConstraintProvider.defineConstraints`) and none concerns rest. There is no
   `minimumRest`/`minRest`/`restPeriod` anywhere in `src/main/java`. Schema head is **V54**; the
   next free migration is **V55**.
2. **The "existing per-agent exception mechanism" REST-06 names cannot express a rest waiver.**
   `AgentException` is a contracted-hours override channel and nothing else — see D-07 for the
   measured reasons, which is why this phase adds a table rather than widening that one.
3. **Minimum rest now applies in BOTH scheduling modes (D-01).** A shift-row-based constraint is
   structurally inert on a SLOT desk, which would have made REST-04 free; the operator took the
   wider scope with that cost named. SLOT mode has no shift entity and no contiguity constraint, so
   "a shift" has to be defined there for the first time (D-02).
4. **REST-02's "same-day back-to-back" clause is live, not vacuous — but not for the reason it
   reads.** A SHIFT desk cannot give one agent two shifts on one *business* date:
   `AgentShiftAssignment`'s identity is `(agent, date)`, one row per `AgentDayConfig` with
   `effectiveHours > 0`. Two shifts on the same *calendar* date are entirely possible, as two
   adjacent business dates on a mid-day-anchored desk — on a `15:00` desk a `05:00–14:00` shift sits
   in business day D-1 and a `20:00–05:00` shift in business day D. That is exactly why REST-02
   insists on real instants rather than calendar-date buckets. A planner must not "fix" this by
   looking for two rows on one business date; there are none.

**Observable behaviour change for an operator:** a desk gains a minimum-rest setting on the desk
configuration page; a desk that leaves it unset solves byte-identically to today. On a desk that sets
one, a roster that would put an agent back on shift too soon is refused before the solve where it is
structurally impossible, and scored as a hard violation otherwise. An operator can waive one agent on
one business date with a reason, and every applied and unused waiver is listed in the solved
schedule's output.

**Not this phase:** a standing per-agent exclusion from minimum rest (raised and explicitly ruled out
as REST-06's per-date waiver instead — see Specific Ideas); weekly rest-day guarantees, which the
existing MANDATORY day-off machinery already covers and which `.planning/research/FEATURES.md:137`
separates from the REST group; premium-pay or consent-to-override machinery (`FEATURES.md:65` — no
payroll subsystem exists and none is being built); the Phil-US live migration (MIGR-01..04, deferred
out of v1.5).

</domain>

<decisions>
## Implementation Decisions

### Scope — which modes, and what a "shift" is there

- **D-01:** **Minimum rest enforces in BOTH scheduling modes.** Chosen by the operator over a
  SHIFT-only scope after the cost was laid out. The measured alternative was strong: the constraint
  would lead with the `AgentShiftAssignment` stream, which has **zero rows** on a SLOT desk (the
  provider's own javadocs call it "the (empty-in-SLOT-mode) `AgentShiftAssignment` stream"), so every
  SLOT desk would have been provably unaffected rather than merely quiet — REST-04 satisfied by
  construction, and matching `requireShiftEnvelopeSeatSupply`, which is also SHIFT-only. The operator
  took the wider scope knowingly. `Desk.schedulingMode` defaults to `SLOT`, so this is the majority
  of desks, not an edge.
  — **Reversibility:** `costly` — withdrawing SLOT enforcement later means removing a constraint, its
  own lookback query and its share of the guard, and re-amending what REST-02 is understood to cover.

- **D-02:** **In SLOT mode a "shift" is the agent's whole assigned span on a business date** — the
  start of their first assigned slot to the end of their last, with the intra-day break gap ignored.
  This mirrors SHIFT mode's one-per-agent-day shape, so both modes compare the same thing.

  **The trap this decision exists to defuse:** `exactlyOneBreak` is gated `!= SchedulingMode.SHIFT`,
  so it is the **SLOT-mode** break rule, and it requires *exactly one gap* in an agent's assigned
  timeslots of exactly the break duration — in SLOT mode the break **is** a gap in assignment. A
  maximal-contiguous-run definition of "shift" would therefore split every compliant SLOT agent-day in
  two at its mandated break, and any realistic rest minimum would fire on **every compliant agent-day
  on every SLOT desk**. Rejected: maximal contiguous runs (that false positive); runs with the break
  duration exempted (avoids the false positive, but makes the rest constraint depend on break geometry
  — two rules that must agree forever, with no shared predicate today).
  — **Reversibility:** `reversible` — one span-derivation helper over an existing `groupBy`.

- **D-03:** **Two mode-gated constraints, not one fused stream.** The operator chose this after the
  divergence cost was named, and it is the shape every other mode-specific constraint in the provider
  already uses. Rejected: mapping both sources into a common per-agent-day span record and `concat()`ing
  them into a single comparison under one constraint name — the `breakClustering` precedent, which
  would have given one weight, one name in the violation report, and one predicate the two modes could
  not disagree about. **Because that mitigation was declined, D-08's shared predicate plus structural
  guard is load-bearing rather than belt-and-braces:** it is now the only thing stopping the two
  constraints drifting from each other and from the pre-solve refusal.
  — **Reversibility:** `reversible` — two constraint methods.

- **D-04:** **The value is a nullable `minimum_rest_minutes` column on `desk`** (V55), with
  `PUT /desks/{deskId}/minimum-rest` as a sibling of the existing `/day-start` and `/scheduling-mode`
  endpoints, and a sixth column on `DeskManagement.tsx`'s desk table. The constraint's **weight** still
  goes on `constraint_weights`, as every constraint's weight does. `NULL` is an unambiguous "unset",
  which is what REST-04 needs.
  Rejected: storing the minutes on `ConstraintWeights` beside the weight, following
  `consistencyToleranceMinutes` (17-CONTEXT D-04, the precedent for a per-desk constraint *parameter*
  living there) — two measured reasons against it. `ConstraintWeightsService` **materialises a defaults
  row when a desk has none** (`new ConstraintWeights()` on miss), so absence is never an unset signal
  there and "unset" would have to be encoded as `0`; and `consistencyToleranceMinutes` itself defaults
  to a real `60`, not an off-sentinel. Separately, ROADMAP success criterion 1 names the **desk
  configuration UI**, which is `DeskManagement.tsx`, not `ConstraintWeightsPage.tsx`. Rejected: a desk
  value with a weights-level fallback — two sources of truth for one number, and `FEATURES.md:150`
  is explicit that there should be no baked-in default, so that "an unconfigured desk is unaffected,
  correctly reflecting that there is no legal minimum being silently assumed".
  — **Reversibility:** `one-way` — a schema column on a live table with an operator-facing endpoint;
  undoing it needs a migration against `dev`, which is the live system with real tenant data.

- **D-05:** **The configured value is bounded below 24 hours.** This is what makes single-step
  business-date adjacency provably sufficient for the pair comparison: a shift two business dates back
  ends no later than the start of business day D-1, so it is at least one whole business day away from
  any shift in business day D and can never be in range. Without the bound, the constraint would need
  to walk more than one date back and the whole join shape changes. The exact bound and its refusal
  message are Claude's discretion; it pairs with 21-CONTEXT D-08's 16-hour maximum shift span, which
  exists specifically so this phase's constraint would not meet a fat-fingered 22-hour template as a
  structurally unsatisfiable roster.
  — **Reversibility:** `reversible` — one validation condition and a constant.

### The waiver (REST-06)

- **D-06:** **A waiver recorded for business date D waives the rest *coming into* D** — the gap from
  D-1's shift end to D's shift start. Reads as "this agent may start early on D": the waiver attaches to
  the shift that is short-rested, which is also the one an operator would move. **The deciding argument
  is the horizon edge:** a violation on the first day of a period pairs a pre-horizon day 0 against day
  1, and only this reading lets the operator waive a date that exists in the schedule. Rejected: waiving
  the rest *after* D's shift (a first-day violation would need a waiver on a date outside the solved
  period); waiving either end touching D (one waiver would then clear two distinct pairs — a wider grant
  than intended — and REST-07's report could no longer say which waiver cleared which violation).
  — **Reversibility:** `costly` — this is the stored meaning of a row. Changing it later silently
  re-points every waiver an operator has already recorded.

- **D-07:** **A new `agent_rest_waiver` table** keyed `(tenant_id, desk_id, agent_id, date)` unique with
  a required `reason`, carried into the solve as its own `@ProblemFactCollectionProperty` on `Schedule`.
  "Waived" is the presence of a row.

  **The real pull was toward reusing `agent_exception`, and it lost on a measured invariant.**
  `AgentException` is *already* a `@ProblemFactCollectionProperty` (`Schedule.java:143`, populated at
  `SolverService:484`), so a constraint could have joined it on `(agentId, date)` today with no new
  plumbing at all — exactly how `agentDayOff` and `preferredStartShiftMode` reach their facts. Against
  that:
  - `contracted_hours_override` is `NOT NULL` in the schema **and** explicitly required at
    `AgentExceptionService:66-68`, and its non-nullability is a *stated* invariant:
    `SolverService.resolveEffectiveHours`'s javadoc says "Precedence checks use `containsKey`, never a
    null-check: a present `AgentException` row is always a real value (`nullable=false`)".
  - **Three** sites build the lookup map unconditionally — `SolverService:302`, `SolverService:1159`,
    and the resolver itself — each as `.put(ex.getDate(), ex.getContractedHoursOverride())`. A rest-only
    row with null hours makes that agent-date's effective hours `null`, which flows straight into
    `AgentDayConfig.effectiveHours()` and the `> 0` gate that decides whether an `AgentShiftAssignment`
    row exists at all. All three would need a non-null filter added in the same change.
  - `SolverService:1302` already refuses any solve where an agent has both an exception and a day off on
    the same date. A rest waiver riding that table would inherit that refusal on a date where a rest
    waiver is harmless — there is no shift to rest from.

  Rejected: nullable hours plus a `rest_waived` flag (relaxes that invariant at all three sites).
  Rejected: required hours plus a `rest_waived` flag with the UI pre-filling the agent's standard hours
  — no invariant relaxed and no new table, but a rest waiver then silently freezes a contracted-hours
  override that diverges the moment the agent's `agent_day_hours` change.

  Note on REST-06's wording: "through the existing per-agent exception mechanism" is honoured as the
  **Agent Exceptions surface** an operator actually uses, not as the `agent_exception` row. If the
  planner reads that as a requirement conflict, it is a wording question to amend in the 21-CONTEXT D-11
  mould, not a reason to revisit the invariant.
  — **Reversibility:** `one-way` — a new table against the live schema.

- **D-08:** **One shared "is this pair waived" predicate, called by all three consumers, plus a
  structural guard test that fails the build if a second implementation appears.** D-03's two-constraint
  split means three things must agree: the SHIFT constraint, the SLOT constraint, and REST-03's
  pre-solve refusal. This is the idiom the milestone already runs on — `src/test/resources/
  bday-join-guard.md`, `src/test/resources/midnight-time-arithmetic.md`, the provider's "single
  `resolveAnchor` helper", and `ShiftLibraryValidationService`'s class javadoc ("so the report and the
  refusal can never disagree"). Rejected: a shared predicate with no guard (correct today, relies on
  reviewers noticing a fourth inlined check); three idiomatic implementations with one equality test
  (the invariant is then only as good as that test's scenario table).
  — **Reversibility:** `costly` — the guard is the only mechanism preventing the divergence D-03
  accepted, and removing it is invisible until the two constraints have already drifted.

- **D-09:** **A waiver that waives nothing is inert, and reported as unused.** Adequate rest on that
  date, a day off, an agent not rostered — the waiver simply never matches, and the same output surface
  REST-07 builds carries two sections: applied and unused. REST-07 exists so a waiver cannot silently
  hide a roster problem; the mirror image is a waiver pile nobody prunes. Rejected: silently inert
  (waivers accumulate invisibly and "who has rest waived" becomes a list with no indication which
  entries are live); refusing at save time (the day-off and out-of-period cases are knowable without
  solving, but adequate-rest-anyway is not, so the report is needed regardless).

### Horizon edges (REST-05)

- **D-10:** **The solve looks back at the agent's real pre-horizon shift — ACCEPTED dates only, one
  date-filtered batched query per mode.** A business date with no ACCEPTED predecessor is explicitly
  unconstrained, and that is what the test pins.

  What makes it buildable: accepted schedules persist both shapes, and `ScheduleService:398-410`
  denormalises `shift_start_time`/`shift_end_time` onto the accepted `agent_shift_assignment` row
  deliberately so that "a later `updateShiftTemplate` can never rewrite what history says this agent
  actually worked". `AcceptedScheduleDateRepository.findByTenantIdAndDeskIdAndDateInAndStatus` already
  answers "is this date accepted" in one call with a single-element collection.

  What it needs built: **neither `AgentAssignmentRepository` nor `AgentShiftAssignmentRepository` has a
  date-filtered read** — both only fetch a whole schedule. In SHIFT mode a whole prior period is a few
  thousand rows; in SLOT mode, which D-01 brought in scope, `agent_assignment` is one row per assigned
  seat per slot, so a month on a large desk is six figures. **A date-filtered query is a precondition
  there, not an optimisation.** The ROADMAP note's constraint stands: bound the lookback to the maximum
  configured rest period across desks and issue it as one batched query, never N+1 per agent.
  — **Reversibility:** `costly` — two repository methods plus lookback logic on both solve paths.

- **D-11:** **The horizon's last day is unconstrained, and a test pins that as deliberate.** Its
  outgoing rest has no successor inside the period, and the pair is genuinely enforced later: the next
  period's solve treats this one as its day-0 predecessor under D-10. The lookback is what makes the end
  edge safe rather than merely empty. Rejected: a symmetric forward lookahead into an already-accepted
  *later* period (closes the one case the lookback misses, at the cost of a second query, a second
  direction for the predicate to get wrong, and constraining a re-solve against a schedule the operator
  may be about to replace); unconstrained plus an advisory.

- **D-12:** **REST-03's pre-solve refusal reasons about the pre-horizon predecessor, not only about
  pairs wholly inside the period.** If day 0's accepted shift ended at 06:00 and every eligible template
  on day 1 starts before the rest minimum allows, that agent-day is structurally impossible and the
  solve is refused up front, naming the agent, the day-0 shift and the day-1 options.

  **Accepted consequence, taken deliberately: accepting one period can newly refuse the next period's
  solve.** The argument for paying that is `requireShiftEnvelopeSeatSupply`'s own comment about the
  weekday-invalid case — refusing "is what converts that silent, near-feasible wrong answer into an
  up-front, actionable refusal". Without the edge, the case most likely to be structurally impossible —
  a night shift rolling into a new period — is the one case that surfaces as residual hard score instead.
  Rejected: in-horizon pairs only (keeps the refusal independent of prior-period accept state, which is
  the genuine attraction); the edge as a non-blocking advisory (a genuinely impossible agent-day still
  burns the whole solve).

  **Mechanism precedent:** `SolverService.requireShiftEnvelopeSeatSupply` is the shape to follow —
  per-date analysis, an accumulated `ErrorDetail` list naming entities, one
  `PreSolveValidationException` carrying all of them, and a **separate non-blocking warnings channel**
  alongside for advisories. Note what "structurally unavoidable" means here and that it is harder than
  the ENVL-07 precedent: it is the cross-date claim that **every** combination of (eligible pair on D-1,
  eligible pair on D) violates the minimum — a product over two days' value ranges, not a per-date
  existence check. `requireShiftEnvelopeSeatSupply` already holds both days' rows and pairs, so the data
  is in scope; the predicate is not.
  — **Reversibility:** `reversible` as code — one predicate in one method. The operator-visible
  consequence is not, which is why it is recorded rather than left to discretion.

### Disclosure (REST-07)

- **D-13:** **`appliedRestWaivers` and `unusedRestWaivers`, computed deterministically from the
  solution — not from `explain()`.** Each entry carries the agent, both business dates, both shift
  instants, the measured gap, the required gap, and the recorded reason. Counts go on `/summary`.

  **Why `explain()` cannot carry this.** It is the live-path channel only:
  `ScheduleOutputService:817+` builds the accepted/DB-path violation report from persisted rows and
  deliberately never calls `explain()` there. And a waived pair that the solver treats as legal
  (REST-06) produces **no match at all**, so `explain()` has nothing to report on either path. Rejected:
  a zero-penalty marker constraint matching only waived pairs — cheapest, reuses a channel that already
  renders, but invisible on the accepted/DB path, which is precisely the gap 20-CONTEXT D-14 hit, and it
  puts a constraint in the registry whose only purpose is reporting. Rejected: export-only disclosure.

  This extends a contract 21-CONTEXT D-14 has just made structured: `ViolationDetail` now carries typed
  `businessDate`, `calendarDate`, `startTime` and `endTime` (confirmed on HEAD at
  `ScheduleDetailResponse.java:206-235`) and both substring parsers were re-pointed at them. Adding
  fields here is extending a typed channel, not reopening a string-parsing one. **Counts on `/summary`
  are load-bearing:** the detail payload is ~4 MB and must never be polled, so a disclosure that lives
  only in detail is effectively invisible to an operator.
  — **Reversibility:** `costly` — a response-DTO shape change with a frontend consumer.

- **D-14:** **`minimum_rest_minutes` is snapshotted on `schedule`** (V55) and carried as
  `ScheduleConfig`'s 15th component. The rest minimum is what the score was computed against, so
  21-CONTEXT D-15's argument for `dayStart` applies unchanged — and `dayStart` is the worked precedent
  for exactly this, snapshotted onto `Schedule` by V54 and confirmed on HEAD at
  `ScheduleDetailResponse.java:23` and `ScheduleSummary.java:23`. Without the snapshot the accepted-path
  report re-derives "was this gap short" against today's desk setting, so changing the desk value
  silently rewrites history for every accepted schedule — the silent-disagreement class this milestone
  keeps finding. Rejected: reading the desk's current value through `ScheduleConfig` the way
  `consistencyToleranceMinutes` does (one column instead of two, no new constructor overload — and that
  rewriting). Rejected: snapshot plus refusing desk edits while an ACCEPTED schedule exists, mirroring
  `DeskService.setDayStart`'s unconditional refusal — `setDayStart` has no bypass because MIGR-04 owns
  the documented reversal, there is no equivalent owner for rest, and rest is desk policy an operator may
  legitimately change mid-quarter.
  — **Reversibility:** `one-way` — a schema column plus a fourth delegating `ScheduleConfig`
  constructor, which every existing test construction site depends on staying compilable.

- **D-15:** **The schedule UI carries a waiver count on the summary it already polls, plus an
  applied/unused section on `ScheduleResults.tsx`.** REST-07's point is that a waiver cannot silently
  hide a roster problem, and an API-only answer hides it from exactly the person it is for. The section
  reads D-13's structured fields, so it introduces no new string parsing. Rejected: summary count only
  (the operator learns something was waived but must leave the page to find out what); API and export
  only.
  **Constraint inherited from Phase 21:** the frontend still has no test runner — `tsc -b` is the only
  gate. `frontend/src/utils/dayWindow.ts` exists on HEAD and exports a branded
  `DayOffset = number & { readonly __brand: 'DayOffset' }`, so any time comparison in the new section
  must route through it or it will not compile. That is the mechanism, not a convention.

### Claude's Discretion

- The exact upper bound D-05 imposes on `minimum_rest_minutes`, and the wording of its refusal.
- Whether the D-10 lookback resolves the predecessor day's anchor from **that schedule's own
  snapshotted `dayStart`** or from the desk's current value. Recommended: the predecessor's own
  snapshot, by D-14's and 21-CONTEXT D-15's argument — the anchor is part of that schedule's identity,
  and a desk re-anchored since would otherwise re-interpret a historical shift's instants.
- What happens to a stored rest value when a desk switches scheduling mode (`switchSchedulingMode`
  already refuses a SHIFT-mode switch and names the uncovered demand windows — whether rest earns a
  similar refusal, a warning, or nothing is open).
- Whether `unusedRestWaivers` is scoped to the solved period or lists every waiver on the desk.
- Whether the Excel export also carries the waiver, following 21-CONTEXT D-12's Roster cell + legend
  row convention. D-15 covers the UI; the export was not decided.
- The hard weight magnitude for the rest constraint(s) and its `constraint_weights` column name.
  Existing hard weights span `ofHard(1)` (`shiftEnvelopeCompliance`) to `ofHard(10_000)`
  (`agentDayOff`).
- Naming of D-08's shared predicate, its home, and the guard's registry file.
- Whether the waiver entry UI extends `AgentExceptions.tsx` or gets its own page and route.
- Task ordering and plan decomposition throughout, including how the three V55 schema changes
  (desk column, schedule column, new table) are sequenced relative to each other and to the code
  that reads them.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Milestone scope and sequencing

- `.planning/ROADMAP.md` §"Phase 22: Minimum Rest" — the five success criteria, the research flag, and
  the Notes paragraph naming the two questions this discussion settled (horizon-edge lookback → D-10;
  how the pre-solve refusal composes → D-12), plus the standing instruction to bound any lookback query
  to the maximum configured rest period and batch it as one query.
- `.planning/REQUIREMENTS.md` §"Minimum Rest (REST)" — REST-01..07 as written. **REST-02's "same-day
  back-to-back" clause is live but means two adjacent business dates on one calendar day, not two shifts
  on one business date — see Phase Boundary item 4. REST-06's "existing per-agent exception mechanism"
  is read as the Agent Exceptions surface, not the `agent_exception` row — see D-07.**
- `.planning/research/FEATURES.md` §"Minimum Rest — Specification Norms and Regulatory Baselines"
  (`:129-175`) — the regulatory tiers (EU/UK 11h hard statutory; US municipal 10–11h soft-with-premium;
  Philippines no statutory figure, 10–12h industry practice), and `:150` on why there must be no
  baked-in default. `:111-116` is the dependency note that minimum rest "is not exclusive to" overnight
  shifts — read it together with Phase Boundary item 4, which measures what that means on this data
  model.
- `.planning/research/FEATURES.md:49`, `:64-65` — why hard-and-pre-solve-refused is stricter than
  everything researched, and why premium-pay/consent-override machinery is explicitly out.

### Prior phase decisions this phase consumes

- `.planning/phases/21-overnight-shift-templates/21-CONTEXT.md` — D-08 (the 16h maximum shift span,
  created specifically so this phase's constraint would not meet a fat-fingered template as a
  structurally unsatisfiable roster), D-14 (the structured violation DTO this phase extends), D-15 (the
  `dayStart`-is-schedule-identity argument D-14 here reuses, and the branded `DayOffset` the new UI
  section must route through), D-01 (the now-editable desk day-start control the new desk column sits
  beside).
- `.planning/phases/20-solver-business-date-correctness/20-CONTEXT.md` — D-05/D-06
  (`agent_shift_assignment.date` IS the business date, derived, structurally guarded both ways),
  D-14 (the accepted/DB-path reporting gap this phase's D-13 must not repeat).
- `.planning/phases/19-daywindow-re-anchoring/19-CONTEXT.md` — D-11 (why a bound on shift length does
  **not** belong in `DayWindow`; D-05's rest bound follows the same reasoning and lives in validation,
  not in interval arithmetic).
- `.planning/phases/18-business-day-foundation-guards/18-CONTEXT.md` — D-13..D-18 (the constructed
  regression suite's design), D-24 (comments cite requirement IDs, never phase numbers).

### The guard contracts this phase extends

- `src/test/resources/bday-join-guard.md` — the structural-guard format D-08's waiver guard should
  follow.
- `src/test/resources/midnight-time-arithmetic.md` — the Java structural scan technique.
- `src/test/java/com/wfm/support/MidnightBoundaryScenarioRegistryTest.java` — the both-directions
  set-equality validator pattern, if D-08's guard needs a registry.

### Code this phase changes

- `src/main/java/com/wfm/solver/ScheduleConstraintProvider.java` — `defineConstraints:109-137` (the
  26-constraint registry both new constraints join); `exactlyOneBreak:304-351` (the SLOT-mode break rule
  whose gap semantics D-02 turns on); `usualShiftConsistency` / `preferredStartShiftMode:1042-1058` and
  `breakClustering:1266-1300` (the stream-shape and `.concat()`/`.map()`-to-stay-in-arity precedents);
  `resolveAnchor` (the file's single anchor helper).
- `src/main/java/com/wfm/service/SolverService.java` — `requireShiftEnvelopeSeatSupply` (D-12's
  mechanism precedent, and it already holds both days' rows and value ranges); `:302` and `:1159` (the
  exception map builds D-07's reasoning rests on); `resolveEffectiveHours:1823-1833` and its javadoc
  (the stated `nullable=false` invariant); `:484` (problem-fact population, where the new waiver
  collection is wired); `:1302` (the inherited exception-coincides-with-day-off refusal).
- `src/main/java/com/wfm/model/Desk.java` — D-04's new column.
- `src/main/java/com/wfm/model/Schedule.java` — `:143` (`agentExceptions` as an existing
  `@ProblemFactCollectionProperty`, the pattern D-07's new collection copies); D-14's snapshot field.
- `src/main/java/com/wfm/model/ScheduleConfig.java` — the 15th component and a fourth delegating
  constructor; read the existing three and their javadocs first, they explain why each exists.
- `src/main/java/com/wfm/model/ConstraintWeights.java` — the new `@ConstraintWeight` and its column.
- `src/main/java/com/wfm/model/AgentShiftAssignment.java` — `:56-68` (identity is `(agent, date)`, the
  fact behind Phase Boundary item 4); the accept-time denormalised columns D-10 reads.
- `src/main/java/com/wfm/service/ScheduleService.java` — `:398-410` (accept-time denormalisation that
  makes D-10's SHIFT lookback authoritative).
- `src/main/java/com/wfm/service/ScheduleOutputService.java` — `:641-756` (the live `explain()` path),
  `:817+` (the accepted/DB path that never calls it — D-13's reason for existing).
- `src/main/java/com/wfm/dto/ScheduleDetailResponse.java` — `:23` (`dayStart`, V54's precedent),
  `:206-235` (`ViolationDetail`'s typed fields D-13 extends).
- `src/main/java/com/wfm/dto/ScheduleSummary.java` — `:23`; D-13's waiver counts.
- `src/main/java/com/wfm/repository/AgentAssignmentRepository.java` and
  `AgentShiftAssignmentRepository.java` — both need D-10's date-filtered read; neither has one.
- `src/main/java/com/wfm/repository/AcceptedScheduleDateRepository.java` —
  `findByTenantIdAndDeskIdAndDateInAndStatus`, D-10's accepted-date check.
- `src/main/java/com/wfm/controller/DeskController.java` — `:67` (`PUT /{deskId}/day-start`, the sibling
  D-04's endpoint mirrors).
- `src/main/java/com/wfm/service/AgentExceptionService.java` — read `:52-97` before designing the waiver
  service; it is the shape to mirror and the invariant to leave alone.
- `frontend/src/pages/DeskManagement.tsx` — the desk table and its day-start cell (D-04's new column).
- `frontend/src/pages/ScheduleResults.tsx` — D-15's new section.
- `frontend/src/utils/dayWindow.ts` — the branded `DayOffset` D-15 must route through.
- `src/main/resources/db/migration/` — head is **V54**; V55 carries all three schema changes.

### Project conventions

- `.planning/codebase/CONVENTIONS.md` — naming, and the
  `{subject}_{condition}_{expectedResult}[_{proofType}]` test-method convention.
- Comments state their reason inline and cite **requirement IDs, never phase numbers** (18-CONTEXT
  D-24) — phase numbers have moved between v1.4 and v1.5, and twice within v1.5.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets

- **`AgentException` is already a solver problem fact**, so the per-agent-per-date join on
  `(agentId, date)` is a solved problem — `agentDayOff` and `preferredStartShiftMode` both do exactly
  it. D-07's new collection copies that wiring rather than inventing it; only the table is new.
- **`AgentException.date` is already interpreted as a business date** by consumption: it is matched
  against `computeAgentDayConfigs`'s period walk, which Phase 20 established is the business date. The
  new waiver table inherits that semantic for free — REST-06's "one business date" needs no new
  derivation.
- **`requireShiftEnvelopeSeatSupply` already holds everything D-12's structural check needs** — the
  per-date rows, each day's eligible `ShiftBandPair` set, the anchor, and both an error list and a
  warnings list. The predicate is new; the plumbing is not.
- **`countContiguousGaps` and `totalGapSlots` already exist and are anchor-aware**, which is what D-02's
  SLOT-mode span derivation builds on.
- **`AcceptedScheduleDateRepository.findByTenantIdAndDeskIdAndDateInAndStatus`** answers D-10's
  "is this date accepted" with a single-element collection — no new query needed for that half.
- **`ScheduleConfig` is already joined seventeen times** and already carries a per-desk parameter
  (`consistencyToleranceMinutes`) and the anchor (`dayStart`), so D-14's 15th component adds no new
  join to any constraint.

### Established Patterns

- **One computation, two callers, so the report and the refusal can never disagree** —
  `ShiftLibraryValidationService`'s class javadoc states it outright. D-08 generalises it to three
  callers, which is the cost of D-03's split.
- **A provable no-op is the standard safety argument for touching shared code.** 21-CONTEXT D-13/D-16
  used "offset equals clock minute at a `00:00` anchor". REST-04's equivalent is stronger: a `NULL`
  `minimum_rest_minutes` means neither constraint's stream produces a tuple at all.
- **Additive first, consume second** — Phase 18's anchored helpers, Phase 19's `ScheduleConfig` field,
  Phase 21 D-14's structured-fields-then-label-text. D-13 and D-14 are both shaped this way.
- **Compiler-forced migration where the surface is bounded** — Phase 19's removed overload, Phase 21's
  branded `DayOffset`. D-15 inherits the latter as a constraint rather than choosing it.
- **`.map()` into a small record to stay inside Timefold's tuple arity** — `breakClustering`'s
  `ClusterMark`. Relevant to both new constraints: the provider's own comment records that "Timefold
  1.16.0's public Constraint Streams API has no 5-tuple (Penta) stream type", and a per-agent-day span
  carries agent, date, start and end, so a pairwise comparison across two days cannot be expressed as
  groupBy-of-four joined to groupBy-of-four.
- **`forEach`-based `groupBy` is preferred over `forEachUniquePair`** on cost grounds —
  `oneAssignmentPerTimeslot`'s own comment says so. A self-join keyed on `equal(sa -> sa.getDate()
  .plusDays(1), AgentShiftAssignment::getDate)` is an indexed join rather than a filtered Cartesian
  product, which is what makes D-05's sub-24h bound worth having.

### Integration Points

- `Desk.minimumRestMinutes` → `Schedule` snapshot → `ScheduleConfig` → both new constraints — D-04 and
  D-14's channel, mirroring `dayStart`'s exactly.
- `agent_rest_waiver` → `Schedule.@ProblemFactCollectionProperty` → both constraints **and** the
  pre-solve refusal — D-07/D-08's channel. Three consumers, one predicate.
- accepted `agent_shift_assignment` / `agent_assignment` + `accepted_schedule_date` → D-10's lookback →
  the pre-solve refusal and both constraints. The only place this phase reads across a period boundary.
- solution → `appliedRestWaivers`/`unusedRestWaivers` → `ScheduleDetailResponse` + `ScheduleSummary` →
  `ScheduleResults.tsx` — D-13/D-15's channel, deliberately bypassing `explain()`.

### Constraints the architecture imposes

- **`dev` is the live system with real tenant data.** Three V55 schema changes land against it, and at
  least one live desk already holds an accepted schedule.
- **Never poll `GET /schedules/{id}`** — ~4 MB, competes with the solver for its two cores. This is why
  D-13 puts counts on `/summary`.
- **A deploy kills a running solve** — solver state is heap-only.
- **The frontend has no test runner.** `tsc -b` is the only gate; any frontend correctness claim rests
  on the compiler or on manual verification.
- **This project's desks do not reliably solve to hard 0.** Adding a hard constraint to such a problem
  is why REST-04's inertness has to be structural rather than a weight of zero — a `NULL`
  `minimum_rest_minutes` must produce no tuples, not cheap ones.

</code_context>

<specifics>
## Specific Ideas

- **The operator chose BOTH scheduling modes over a SHIFT-only scope with the cost explicitly laid
  out**, including that a shift-row constraint is structurally inert in SLOT mode and would have made
  REST-04 free. Treat D-01 as a deliberate, informed widening, not a default.
- **The operator chose two mode-gated constraints over one fused `concat()`ed stream, also with the
  divergence cost named.** That is why D-08 is written as load-bearing rather than optional. Do not
  quietly re-fuse them at planning time to simplify — if the planner believes the fused form is
  necessary, raise it as a conflict rather than applying it.
- **"Overridden by agent via an exclusion" was raised mid-discussion and clarified down to REST-06's
  per-date waiver.** A standing per-agent exclusion from minimum rest — a flag meaning rest never
  applies to this person for the whole horizon — was offered and explicitly **not** chosen. It has no
  REST id. If it resurfaces, it is a deferred idea (listed below), not a reading of REST-06.
- **REST-02's "same-day back-to-back" clause caused a false finding mid-discussion and should not cause
  another.** A planner reasoning from REQUIREMENTS alone will look for two shift rows on one business
  date and find the entity model forbids it. The clause is about two *calendar*-same-day shifts that are
  two *adjacent business dates* on a mid-day-anchored desk. The measurement is in Phase Boundary item 4.
- **The scope-growth flag, for the planner.** These decisions put three schema changes in V55 (desk
  column, schedule column, new table), two constraints plus a shared predicate plus a structural guard,
  two new date-filtered repository queries plus lookback logic on both solve paths, a pre-solve refusal
  spanning the horizon edge, DTO changes on both detail and summary, and three UI surfaces (desk column,
  waiver entry, schedule section). Phases 20 and 21 each ran to twelve plans on less. **Evaluate a split
  up front rather than discovering it halfway** — this was raised with the operator before CONTEXT.md
  was written and the scope was reaffirmed, so a `PHASE SPLIT RECOMMENDED` return is a legitimate
  outcome, not a scope dispute.
- **The research's "no baked-in default" is a design instruction, not background.**
  `FEATURES.md:150` is explicit that an unconfigured desk must be unaffected, "correctly reflecting that
  there is no legal minimum being silently assumed" — which is why D-04 chose a nullable column over a
  defaulted one, and why Phil-US (the one live desk in a jurisdiction with no statutory figure) is the
  case the per-desk model exists for.

</specifics>

<deferred>
## Deferred Ideas

- **A standing per-agent exclusion from minimum rest** — a flag meaning rest never applies to an agent
  for a whole horizon, no date needed. Raised in discussion and ruled out in favour of REST-06's
  per-date waiver. It would also change REST-03 (the refusal would skip excluded agents entirely, not
  just waived occurrences) and REST-07 (an excluded agent's short rest would never register as
  anything at all). Its own requirement and its own phase.
- **Premium-pay / consent-to-override machinery for a rest violation**, mirroring the US "clopening"
  ordinance pattern. Explicitly ruled out at milestone scoping — no payroll subsystem exists
  (`FEATURES.md:65`). Recorded here only so a later reader knows it was considered and why.
- **A symmetric forward lookahead at the horizon's last day**, into an already-accepted later period.
  D-11 declined it; it closes the one case D-10's lookback cannot reach.
- **Refusing a desk's rest value change while an ACCEPTED schedule exists**, mirroring
  `DeskService.setDayStart`. D-14 declined it for want of an owner for the documented reversal.
- **An operating-window containment refusal for same-day templates** — carried from 21-CONTEXT; needs a
  survey of live templates first.
- **A frontend test harness** — carried from 21-CONTEXT. D-15 inherits the absence as a constraint.
- **Rename `date` → `businessDate` across the six business-date-shaped problem facts** — carried from
  20-CONTEXT via 21-CONTEXT; ~200 accessor sites, and renaming fewer than all six is worse than none.
- **Restoring a genuine live-desk drift check** for Phil-US (20-CONTEXT D-14).
- **Deleting the frozen `DayWindow` oracle** (19-CONTEXT D-13) once v1.5 ships.
- **Blocked-break-hours has no enforcement point in SHIFT mode** — carried from 21-CONTEXT; deferred by
  operator ruling OR-2 at the v1.3 close.

### Reviewed Todos (not folded)

All three `todo.match-phase` hits were reviewed and the operator ruled **none** into Phase 22 —
consistent with Phases 19, 20 and 21, which declined the same three.

- **Cross-agent seat displacement for the atomic shift move**
  (`.planning/todos/pending/2026-08-13-cross-agent-seat-displacement.md`, `solver`, score **0.9** — the
  highest match this phase drew). Its own file says "Confirm this is scoped as its own phase (do not
  fold into an in-flight phase) — it changes move semantics and score-corruption risk surface … A named
  displacement move is a `costly`/architectural decision, not a `reversible` one." It also requires the
  seeded 5×5 step-count benchmark harness re-run as its only trustworthy evidence format, plus new
  undo-correctness coverage; and compound swap moves are a known hazard here — sound on a settled
  solution, corrupting during search, with `FULL_ASSERT` not catching it. **Raise as a candidate phase
  for the next milestone.** Phase 21 said "after Phase 22"; Phase 22 is the milestone's last phase, so
  the natural home is the next roadmap.
- **Provide a blank upload template spreadsheet, one sheet per desk**
  (`.planning/todos/pending/2026-07-30-blank-upload-template-one-sheet-per-desk.md`, `upload`, score
  0.6). Changes the workbook structure the desk-assignment upload parser must accept, on a path that
  clears each desk before parsing and is destructive if layout and parser disagree. No shared
  correctness concern with minimum rest. **Raise as a candidate phase for the next milestone.**
- **Terraform state diverges from live RDS password and `publicly_accessible`**
  (`.planning/todos/pending/2026-08-14-terraform-db-password-drift.md`, `infra`, score 0.6). Matched on
  the word "phase" alone. Infrastructure drift, unrelated. Declined in Phases 19, 20 and 21 too.

</deferred>

---

*Phase: 22-Minimum Rest*
*Context gathered: 2026-10-03*
