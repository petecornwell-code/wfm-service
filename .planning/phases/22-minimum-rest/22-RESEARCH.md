# Phase 22: Minimum Rest - Research

**Researched:** 2026-10-03
**Domain:** Timefold Constraint Streams (self-join across business dates), Spring/JPA schema evolution, pre-solve validation
**Confidence:** HIGH for the solver mechanics and schema shape (all load-bearing claims verified against HEAD this session); MEDIUM for the exact numeric bound and weight magnitude (Claude's Discretion, no regulatory default exists per `FEATURES.md:150`)

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01:** Minimum rest enforces in BOTH scheduling modes (SHIFT and SLOT) — operator chose the wider scope knowing a SHIFT-only constraint would have been structurally inert (and REST-04 free) in SLOT mode.
- **D-02:** In SLOT mode a "shift" is the agent's whole assigned span on a business date — first slot start to last slot end, intra-day break gap ignored. (Not a maximal-contiguous-run split, which would false-positive on every compliant SLOT agent-day at `exactlyOneBreak`'s mandated gap.)
- **D-03:** Two mode-gated constraints, not one fused `concat()`ed stream — the shape every other mode-specific constraint in `ScheduleConstraintProvider` already uses. This makes D-08's shared predicate + guard load-bearing, not optional.
- **D-04:** The value is a nullable `minimum_rest_minutes` column on `desk` (V55), `PUT /desks/{deskId}/minimum-rest` sibling to `/day-start`, sixth column on `DeskManagement.tsx`. Weight lives on `constraint_weights` as usual. `NULL` = unambiguous unset (REST-04).
- **D-05:** The configured value is bounded below 24 hours — this is what makes single-step business-date adjacency provably sufficient for the pairwise comparison. Exact bound and refusal wording: Claude's discretion.
- **D-06:** A waiver recorded for business date D waives the rest *coming into* D (the gap from D-1's shift end to D's shift start) — "this agent may start early on D." Not the rest *after* D, not both ends.
- **D-07:** A new `agent_rest_waiver` table, keyed `(tenant_id, desk_id, agent_id, date)` unique, required `reason`, its own `@ProblemFactCollectionProperty`. NOT a reuse of `agent_exception` — `contracted_hours_override` is `NOT NULL` and required at three unconditional map-build sites plus the resolver's stated invariant; a rest-only exception row would relax that invariant everywhere. REST-06's "existing per-agent exception mechanism" is the **Agent Exceptions surface**, not the `agent_exception` row.
- **D-08:** One shared "is this pair waived" predicate, called by all three consumers (SHIFT constraint, SLOT constraint, REST-03 refusal), plus a structural guard test that fails the build if a second implementation appears.
- **D-09:** A waiver that waives nothing is inert and reported as unused — two sections, applied and unused, in the same REST-07 output. Never refused at save time (adequate-rest-anyway is not knowable pre-solve).
- **D-10:** The solve looks back at the agent's real pre-horizon shift — ACCEPTED dates only, one date-filtered batched query per mode, bounded to the max configured rest period across desks. A business date with no ACCEPTED predecessor is explicitly unconstrained.
- **D-11:** The horizon's last day is unconstrained — enforced later when the next period treats this one as its day-0 predecessor. No symmetric forward lookahead.
- **D-12:** REST-03's pre-solve refusal reasons about the pre-horizon predecessor too, not only in-horizon pairs. Accepting one period can newly refuse the next period's solve — accepted deliberately, mirroring `requireShiftEnvelopeSeatSupply`'s precedent. The predicate is a cross-date "every combination of (D-1 pair, D pair) violates the minimum" claim — harder than ENVL-07's per-date existence check.
- **D-13:** `appliedRestWaivers` and `unusedRestWaivers`, computed deterministically from the solution — never from `explain()` (which produces no match at all for a waived, legal pair, and which the accepted/DB path never calls). Counts go on `/summary` (the ~4MB detail payload must never be polled).
- **D-14:** `minimum_rest_minutes` is snapshotted on `schedule` (V55), 15th `ScheduleConfig` component — mirrors `dayStart`'s precedent exactly, for the same "the score was computed against this value" reason.
- **D-15:** Schedule UI carries a waiver count on the already-polled summary, plus an applied/unused section on `ScheduleResults.tsx`.

### Claude's Discretion

- The exact upper bound on `minimum_rest_minutes`, and the wording of its refusal.
- Whether D-10's lookback resolves the predecessor day's anchor from that schedule's own snapshotted `dayStart` or the desk's current value (CONTEXT.md recommends the snapshot).
- What happens to a stored rest value when a desk switches scheduling mode.
- Whether `unusedRestWaivers` is scoped to the solved period or lists every waiver on the desk.
- Whether the Excel export also carries the waiver.
- The hard weight magnitude for the rest constraint(s), and its `constraint_weights` column name.
- Naming of D-08's shared predicate, its home, and the guard's registry file.
- Whether the waiver entry UI extends `AgentExceptions.tsx` or gets its own page and route — **RESOLVED by 22-UI-SPEC.md: extends `AgentExceptions.tsx` with a new "Rest Waivers" section, no new route.**
- Task ordering and plan decomposition throughout, including how the three V55 schema changes are sequenced.

### Deferred Ideas (OUT OF SCOPE)

- A standing per-agent exclusion from minimum rest (whole-horizon, no date). Its own requirement and phase.
- Premium-pay / consent-to-override machinery. No payroll subsystem exists.
- A symmetric forward lookahead at the horizon's last day into an already-accepted later period.
- Refusing a desk's rest value change while an ACCEPTED schedule exists.
- An operating-window containment refusal for same-day templates (carried from Phase 21).
- A frontend test harness (carried from Phase 21 — `tsc -b` is still the only gate).
- Renaming `date` → `businessDate` across the six business-date-shaped problem facts.
- Cross-agent seat displacement, blank upload template, Terraform drift — reviewed todos, none folded in.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| REST-01 | Operator can set a minimum rest period between an agent's consecutive shifts, per desk | D-04 schema/endpoint verified against `DeskController`'s `/day-start` sibling (confirmed `PUT /{deskId}/day-start` at `DeskController.java:67`); see Architecture Patterns §1 |
| REST-02 | Solver treats insufficient rest as a hard violation, measured between actual end/start instants, same-day and overnight, unless waived | Self-join + `DayWindow` gap-minutes formula verified against `DayWindow.java` (quoted below); mode-gated constraint shape verified against `exactlyOneBreak`'s `ifExists(ScheduleConfig.class, filtering(...))` gate |
| REST-03 | Pre-solve refusal where structurally unavoidable, naming agent and both shifts, separate mechanism, waived occurrence does not trigger it | `requireShiftEnvelopeSeatSupply` precedent read in full (`SolverService.java:1448+`) — per-date rows, `ErrorDetail` accumulation, `PreSolveValidationException`, separate warnings channel, all confirmed on HEAD |
| REST-04 | Desk with no minimum rest solves exactly as today | `NULL`-means-no-tuples pattern verified against `AgentException.contractedHoursOverride`'s `nullable = false` contrast and `ConstraintWeights` materialize-on-miss behavior |
| REST-05 | Defined, tested behaviour at first/last day of horizon | D-10/D-11 lookback mechanism; `AcceptedScheduleDateRepository.findByTenantIdAndDeskIdAndDateInAndStatus` confirmed to exist and answer "is this date accepted" in one call |
| REST-06 | Waive for one agent, one date, recorded reason, via existing per-agent exception mechanism | `AgentException`/`AgentExceptionService` read in full — confirmed `nullable=false` column and the unconditional "required" check at the exact cited lines; confirms D-07's new-table decision is forced, not a preference |
| REST-07 | Waived violations visible in solved schedule's output | `ScheduleOutputService`'s `explain()`-vs-accepted-path split verified (`buildConstraintViolations:625`, `isAcceptedSnapshot` branch at `:630`, `buildAcceptedConstraintViolations:828`); `ScheduleDetailResponse.ViolationDetail`'s typed fields confirmed at `:227-235` |
</phase_requirements>

## Summary

This phase is unusually well-specified already: `22-CONTEXT.md` is itself a research-grade document with file-and-line citations for nearly every claim, and `22-UI-SPEC.md` is approved and complete. This RESEARCH.md's job is narrower than usual: (1) independently verify the load-bearing claims against HEAD rather than trust them blind, (2) supply the concrete Timefold Constraint Streams code shape for the two new constraints and the self-join gap computation (not fully worked out in CONTEXT.md), and (3) resolve the open "Claude's Discretion" items the planner needs numbers for, not just reasoning.

Every claim I independently checked this session — the V54 migration head, `defineConstraints`'s 26-entry registry, `AgentShiftAssignment`'s `(agent, date)` identity, `AgentException.contractedHoursOverride`'s `nullable = false` constraint and its three unconditional consumer sites, `requireShiftEnvelopeSeatSupply`'s full mechanism, the `ViolationDetail`/`ScheduleSummary` typed-field shapes, `DayWindow`'s anchored-offset semantics, the 16-hour `MAX_SPAN_MINUTES` constant, and `ConstraintWeights`'s weight range — **confirmed exactly as CONTEXT.md describes them.** Nothing found contradicts the plan; this materially raises confidence that the planner can proceed directly from CONTEXT.md's decisions without a second architecture pass.

**Primary recommendation:** Build the two new constraints as a straight application of the existing `exactlyOneBreak`/`breakClustering` idioms (mode-gate via `ifExists(ScheduleConfig.class, filtering(...))`, use `.map()` into a small record to dodge Timefold 1.16.0's missing Penta stream), compute the cross-date gap with `DayWindow.anchoredEndMinute`/`anchoredStartMinute` via the formula verified below (never hand-rolled `Duration.between`), and self-join `AgentShiftAssignment`/`AgentAssignment` via an indexed `equal(sa -> sa.getDate().plusDays(1), ...::getDate)` — never `forEachUniquePair`, which would make this an O(N²) Cartesian scan instead of an indexed join. Given the measured scope (three V55 schema changes, two constraints plus a shared predicate plus a structural guard, two new repository methods plus lookback logic on both solve paths, a horizon-spanning pre-solve refusal, two DTO extensions, three UI surfaces — already built and approved), **the planner should seriously evaluate a phase split** before committing to a single wave sequence; Phases 20 and 21 each ran to twelve plans on less.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Minimum-rest value storage & operator config | API / Backend (`Desk` entity, `DeskController`) | Browser / Client (`DeskManagement.tsx` column) | D-04 — a desk-level policy value, standard CRUD-on-entity shape, already has a sibling endpoint (`/day-start`) to mirror exactly |
| In-solve rest violation detection | API / Backend (Timefold solver, in-process) | — | Must run inside the constraint-stream score calculation; no other tier has access to the planning entities mid-solve |
| Pre-solve structural-impossibility refusal | API / Backend (`SolverService`, pre-solve validation pipeline) | — | Runs before `buildSolver()`/`solve()` is invoked, in the same request thread as `requireShiftEnvelopeSeatSupply` — not a solver concern, a request-validation concern |
| Waiver storage & CRUD | API / Backend (new `agent_rest_waiver` table/service) | Browser / Client (`AgentExceptions.tsx` section) | D-07 — mirrors `AgentExceptionService`'s existing per-agent-per-date CRUD shape exactly |
| Waiver visibility in solved output | API / Backend (`ScheduleOutputService`, DTOs) | Browser / Client (`ScheduleResults.tsx` badge + tab) | D-13 — computed deterministically from the persisted/in-memory solution, never recomputed client-side; UI only renders what the DTO sends |
| Historical lookback across period boundary | API / Backend (new repository methods + `SolverService`) | Database / Storage (`accepted_schedule_date`, `agent_shift_assignment`, `agent_assignment`) | D-10 — this is the one place this phase reads across a period boundary; must be a bounded, batched query, never N+1 |

## Package Legitimacy Audit

**Not applicable — no new external package is introduced by this phase.** Every mechanism (constraint streams, self-joins, pre-solve validation, JPA entities/migrations, React/TSX forms) is built entirely on dependencies already present and pinned:

- `ai.timefold.solver:timefold-solver-bom:1.16.0` (`build.gradle:52`) — confirmed on HEAD this session, matches the pinned version already recorded in `STATE.md`. No Timefold API this phase needs (self-join via `Joiners.equal`, `.map()`, `ifExists(..., filtering(...))`) requires a version bump; all are already in use elsewhere in `ScheduleConstraintProvider`.
- `frontend/package.json` — three runtime dependencies (`react`, `react-dom`, `react-router-dom`), confirmed unchanged by `22-UI-SPEC.md`'s own Design System section. No new frontend dependency.

If a planner later finds a genuine need for a new package (unlikely for this phase's scope), route it through the Package Legitimacy Gate protocol before adding it.

## Architecture Patterns

### System Architecture Diagram

```
Operator (DeskManagement.tsx)
   │  PUT /desks/{id}/minimum-rest
   ▼
DeskController → DeskService ──writes──▶ desk.minimum_rest_minutes (nullable, V55)
                                               │
                                               │ read at solve time
                                               ▼
SolverService.buildSchedule
   │                                    ┌─────────────────────────────┐
   │ 1. Resolve predecessor shift ──────▶ AcceptedScheduleDateRepository │
   │    (D-10 lookback, bounded,        │ + new date-filtered reads on  │
   │    batched — ACCEPTED dates only)  │ AgentAssignment/AgentShiftAssignment│
   │                                    └─────────────────────────────┘
   │
   ├─ 2. requireRestFeasibility (new, mirrors requireShiftEnvelopeSeatSupply)
   │      per agent-day: is EVERY (D-1 pair, D pair) combination a rest violation?
   │      → PreSolveValidationException naming agent + both shifts (REST-03)
   │      → waived pairs (D-08 predicate) are excluded from this check (REST-03)
   │
   ├─ 3. Solve runs. ScheduleConstraintProvider:
   │      minimumRestShift (SHIFT mode, self-join on AgentShiftAssignment)
   │      minimumRestSlot  (SLOT mode, self-join on per-agent-day span derived
   │                         from AgentAssignment via groupBy, D-02)
   │      both skip a pair where D-08's isRestWaived(...) predicate is true
   │      both produce NO tuple at all when minimum_rest_minutes is NULL (REST-04)
   │
   └─ 4. Output: ScheduleOutputService computes appliedRestWaivers/unusedRestWaivers
          deterministically from the persisted solution (never explain()) (D-13)
          → ScheduleSummary (counts, polled every 2s)
          → ScheduleDetailResponse (full rows, polled every 30s)
          → ScheduleResults.tsx renders badge + "Rest Waivers" tab
```

### Recommended Project Structure

No new packages/directories — every file this phase touches already exists (see `22-CONTEXT.md`'s Canonical References → "Code this phase changes" for the exhaustive list). One new table, one new service (`RestWaiverService`, mirroring `AgentExceptionService`), one new repository (`AgentRestWaiverRepository`), one new shared predicate (home: Claude's discretion — recommend a new small class or a package-private static on `ScheduleConstraintProvider`, see Discretion Resolutions below).

### Pattern 1: Mode-gated constraint via `ifExists(ScheduleConfig.class, filtering(...))`

**What:** Gate a constraint to one `SchedulingMode` without growing tuple arity, since Timefold 1.16.0 has no 5-tuple (Penta) stream.
**When to use:** Both `minimumRestShift` (gate `== SHIFT`) and `minimumRestSlot` (gate `!= SHIFT`, matching `exactlyOneBreak`'s existing gate) need exactly this shape.
**Example (verified against `ScheduleConstraintProvider.java:304-316`, `exactlyOneBreak`):**
```java
// Source: src/main/java/com/wfm/solver/ScheduleConstraintProvider.java:304-316 (exactlyOneBreak)
Constraint exactlyOneBreak(ConstraintFactory factory) {
    return factory.forEach(AgentAssignment.class)
            .filter(a -> a.getAgent() != null)
            .groupBy(AGENT_ID, DATE, TO_LIST)
            .join(AgentDayConfig.class,
                    equal((daId, date, assignments) -> daId, AgentDayConfig::agentId),
                    equal((daId, date, assignments) -> date, AgentDayConfig::date))
            .ifExists(ScheduleConfig.class,
                    filtering((daId, date, assignments, dayConfig, cfg) ->
                            cfg.schedulingMode() != SchedulingMode.SHIFT))
            .filter((daId, date, assignments, dayConfig) -> { /* ... */ })
```
The new `minimumRestSlot` constraint should copy this `ifExists(ScheduleConfig.class, filtering(... != SchedulingMode.SHIFT))` gate verbatim; `minimumRestShift` gates the opposite way, but more simply — it can lead with `factory.forEach(AgentShiftAssignment.class)`, which already has zero rows in SLOT mode by construction (D-01's own argument for why SHIFT-only would have been free), so an explicit mode filter is optional there (confirm this reasoning in review: it stands only because `AgentShiftAssignment`'s value-range filter already keys on `AgentDayConfig` with `effectiveHours > 0` and SLOT desks never populate it — verified at `AgentShiftAssignment.java:17-24`'s javadoc, quoted above).

### Pattern 2: Self-join across adjacent business dates (indexed, not `forEachUniquePair`)

**What:** Join an entity stream to itself on `date` vs `date.plusDays(1)` to compare a shift against the *next* business date's shift for the same agent.
**When to use:** Both new constraints' core self-join; also the D-12 pre-solve refusal's cross-date pairing.
**Why indexed matters (verified against the project's own stated reasoning):**
```
// Source: src/main/java/com/wfm/solver/ScheduleConstraintProvider.java:256-258
// (oneAssignmentPerTimeslot's own comment)
"Uses forEach-based groupBy instead of forEachUniquePair to avoid
 O(N²) pairing of unassigned entities."
```
A self-join keyed `equal(sa -> sa.getDate().plusDays(1), AgentShiftAssignment::getDate)` is an indexed join — Timefold builds a hash index on the join key, so the comparison cost is proportional to matching pairs, not every pair in the schedule. D-05's sub-24h bound on `minimum_rest_minutes` is what makes a **single** `plusDays(1)` step sufficient — a shift two business dates back ends no later than the start of business day D-1, so it can never be in range, and the join never needs to walk further back.

```java
// Sketch — not yet in the codebase; follows the verified self-join + AGENT_ID grouping idiom
// used throughout ScheduleConstraintProvider (AGENT_ID/DATE/TO_LIST constants at :96-103)
factory.forEach(AgentShiftAssignment.class)
    .filter(sa -> sa.getShiftBandPair() != null)
    .join(AgentShiftAssignment.class,
          Joiners.equal(AgentShiftAssignment::getAgentId),   // same agent
          Joiners.equal(sa -> sa.getDate().plusDays(1), AgentShiftAssignment::getDate))
    .filter((prev, next) -> next.getShiftBandPair() != null)
    .filter((prev, next) -> !isRestWaived(prev, next, restWaivers))   // D-08 predicate
    .filter((prev, next) -> gapMinutes(prev, next, window) < minimumRestMinutes)
    .penalize(...)
```

### Pattern 3: Cross-date gap computation via `DayWindow`'s anchored offsets (never raw `Duration.between`)

**What:** Compute the minutes between the end of business day D-1's shift and the start of business day D's shift, both expressed as day-start-relative `LocalTime` offsets, with no raw time arithmetic (the `midnight-time-arithmetic.md` guard forbids `Duration.between`/`.plusMinutes`/`.minusMinutes` outside the allowlist).
**Verified formula**, derived from reading `DayWindow.java` this session (quoting the exact method contracts, not paraphrasing):

> `anchoredEndMinute` javadoc: *"Instance equivalent of `endMinute(LocalTime)`, day-start-relative... at a `00:00` anchor this equals `endMinute(LocalTime)` exactly."* Range confirmed by its delegate `endMinuteFromDayStart`: *"Day-start-relative minute of a time in an END position, `(0, 1440]`"* (`DayWindow.java:353-355`).
> `anchoredStartMinute` delegate `startMinuteFromDayStart`: *"Day-start-relative minute of a time in a START position, `[0, 1440)`"* (`DayWindow.java:340-342`).

Because D-1's shift end and D's shift start are each expressed relative to their *own* business day's start boundary, and the two business days are exactly one calendar day apart by construction (the self-join's `plusDays(1)` guarantees this), the elapsed minutes between the two real instants is:

```java
// gapMinutes = (minutes remaining in D-1's business day after prevEnd)
//            + (minutes elapsed into D's business day before nextStart)
int gapMinutes(AgentShiftAssignment prev, AgentShiftAssignment next, DayWindow window) {
    int remainingInPrevDay = DayWindow.MINUTES_PER_DAY - window.anchoredEndMinute(prevEndTime);
    int elapsedIntoNextDay = window.anchoredStartMinute(nextStartTime);
    return remainingInPrevDay + elapsedIntoNextDay;
}
```
This needs no `LocalDate`/`Instant` conversion at all — it stays entirely in the anchored-minute vocabulary the rest of the constraint provider already uses, which is also why it needs no new allowlist entry in `midnight-time-arithmetic.md`. **Anchor resolution:** use the existing `anchorFor(AgentShiftAssignment sa)` private helper (`ScheduleConstraintProvider.java:70-73`) — *"reaches the desk's real anchor this way at zero extra join cost, since `sa` already carries it"* — rather than re-deriving a `DayWindow` from a second join. For the SLOT-mode constraint, the per-agent-day span (D-02) will need an equivalent anchor source; follow the same `resolveAnchor(dayConfig.dayStart())` pattern `exactlyOneBreak` already uses (`:315`).

**Edge case to verify explicitly in tests:** `anchoredEndMinute` can equal `1440` exactly (a shift ending precisely at the business-day boundary) and `anchoredStartMinute` can equal `0` exactly (a shift starting precisely at the boundary) — confirm the gap formula produces `0` in that case (back-to-back, zero rest) rather than `1440` from an off-by-one, since this is precisely the "same-day back-to-back" case REST-02 calls out as live.

### Pattern 4: `.map()` into a small record to stay inside tuple arity

**What:** When a pairwise comparison needs more fields than Timefold's Quad stream carries, map into a small record and `groupBy`/`concat` rather than trying to join a fifth source.
**Verified precedent:** `breakClustering`'s `ClusterMark` record (`ScheduleConstraintProvider.java:1219`, `private record ClusterMark(int assigned, int onBreak) {}`), justified in the provider's own comment: *"Timefold 1.16.0's public Constraint Streams API has no 5-tuple (Penta) stream type to join into."* If the self-join above needs to carry the resolved `DayWindow`/waiver lookup alongside `(prev, next)`, consider the same `.map()`-to-record indirection rather than a third join.

### Pattern 5: Shared predicate + structural guard (D-08)

**What:** One `isRestWaived(agentId, date, waivers)` (or equivalent) predicate called by all three consumers — `minimumRestShift`, `minimumRestSlot`, and the REST-03 pre-solve refusal — plus a test that fails the build if a second implementation of the same check appears.
**Precedent to mirror:** `src/test/resources/bday-join-guard.md` is a parsed-at-test-time allowlist file (`BusinessDateJoinGuardTest` reads it); its own stated purpose: *"a join/equal/groupBy/computeIfAbsent key position that resolves a Timeslot's calendar date where business date is meant is a silent non-join... Neither failure mode throws, and neither is visible to a behavioural test that only checks the final score."* The rest-waiver guard should follow the same shape: either (a) a reflective/source-scan test asserting exactly one method/call site implements the waived-pair check (mirroring `MidnightTimeArithmeticGuardTest`'s structural scan), or (b) a shared static predicate with a test that fails if the three call sites' results ever disagree on a constructed fixture (both-directions set-equality, per `MidnightBoundaryScenarioRegistryTest`'s pattern). Recommend (a) — a structural guard catches a *second implementation* existing at all, which is the exact failure D-03's two-constraint split invites; (b) only catches disagreement on the fixtures someone thought to write.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Cross-date gap/duration math | Raw `Duration.between(prevEnd, nextStart)` or `LocalDateTime` conversion | `DayWindow.anchoredEndMinute`/`anchoredStartMinute` + the verified arithmetic formula above | `Duration.between`/`.plusMinutes`/`.minusMinutes` are enforced-by-test forbidden tokens outside `midnight-time-arithmetic.md`'s allowlist; a raw computation on a midnight-anchored (`00:00`) end time silently produces a negative or wrapped duration (the exact bug class `DayWindow`'s own javadoc documents as the reason it exists) |
| Self-join pairing | `forEachUniquePair` filtered down to adjacent dates | An indexed `.join(X.class, Joiners.equal(...))` on `date.plusDays(1)` | `forEachUniquePair` is an O(N²) Cartesian scan; the project's own `oneAssignmentPerTimeslot` comment states this reasoning explicitly for an analogous case |
| Waiver storage | Widening `AgentException` with a nullable-hours + flag column | A new `agent_rest_waiver` table | `AgentException.contractedHoursOverride` is `nullable = false` **and** required by an explicit `IllegalArgumentException` check at `AgentExceptionService.java` (verified, matches CONTEXT.md's cited `:66-68`); three call sites build the exception lookup map unconditionally keying on that non-null value — widening it would require relaxing a stated invariant at all three sites plus inheriting an unrelated day-off-coincidence refusal |
| Pre-solve structural-impossibility detection | A bespoke validation pass | Mirror `requireShiftEnvelopeSeatSupply`'s exact shape: per-date rows, `ErrorDetail` accumulation into a `List`, thrown as one `PreSolveValidationException(message, details)`, plus a separate non-blocking warnings `List<String>` | This is a proven, tested pattern already in `SolverService` for exactly this class of problem (ENVL-07); `PreSolveValidationException` (verified, `src/main/java/com/wfm/exception/PreSolveValidationException.java`) is a two-field `RuntimeException` carrying a `List<ErrorDetail>` — trivial to reuse, no reason to invent a second shape |
| Reporting waived violations | A zero-penalty marker constraint, or reading `explain()` on the accepted path | Deterministic computation directly from the persisted/solution rows (D-13) | `explain()` is live-path-only (`ScheduleOutputService.buildConstraintViolations:625` branches to `buildAcceptedConstraintViolations` for `isAcceptedSnapshot`, verified — the accepted path never calls `explain()`); a waived pair also produces **no match at all** in `explain()` even on the live path, since the solver treats it as fully legal — there is nothing for a marker constraint to report |

**Key insight:** Every "don't hand-roll" item in this phase has a working precedent already in the codebase, doing the *same job* for a structurally analogous problem (day-off exceptions, seat-supply pre-solve refusal, midnight-safe duration math). The implementation risk in this phase is not inventing new mechanisms — it is correctly composing four or five existing ones across two scheduling modes without letting them drift (which is exactly what D-08's guard exists to prevent).

## Common Pitfalls

### Pitfall 1: Computing the gap with raw `LocalTime` comparison instead of anchored offsets
**What goes wrong:** `nextStart.isAfter(prevEnd)` or `Duration.between(prevEnd, nextStart)` silently produces nonsense the moment `prevEnd` is `00:00` (end-of-day) or either time is on a non-midnight-anchored desk.
**Why it happens:** `LocalTime` has no `24:00`; a shift ending at the business-day boundary stores `00:00`, the *smallest* value the type holds — this is the exact bug class `DayWindow`'s class javadoc exists to prevent, and it recurred at least once already in this milestone (`ScheduleExportService`'s roster-cell tracking, fixed as `5ddd8dc`).
**How to avoid:** Use only `DayWindow`'s `anchored*` instance methods (Pattern 3 above); never a bare `isAfter`/`isBefore`/`Duration.between` on the raw stored `LocalTime`s.
**Warning signs:** Any new line matching `Duration.between(`, `ChronoUnit.MINUTES`, `.plusMinutes(`, `.minusMinutes(`, or a raw time comparison — `MidnightTimeArithmeticGuardTest` will fail the build on an unlisted occurrence, which is the intended outcome; do not add an allowlist entry to silence it, fix the call site.

### Pitfall 2: Re-fusing the two mode-gated constraints "to simplify"
**What goes wrong:** A planner or implementer, noticing the two constraints (`minimumRestShift`/`minimumRestSlot`) look nearly identical, is tempted to `concat()` them into one stream the way `breakClustering` does.
**Why it happens:** It looks like duplicated logic. But the operator explicitly chose the two-constraint split (D-03) *after* being told the fused-stream alternative existed and costed less — this is a deliberate, informed decision, not an oversight.
**How to avoid:** Keep two constraint methods. If they turn out truly identical in body, extract a shared private helper they both call — do not merge the constraint registrations themselves. Flag any perceived need to re-fuse as a conflict to raise with the user, not something to resolve unilaterally.
**Warning signs:** A code review suggesting "these two constraints could be `concat()`ed" — check this against 22-CONTEXT D-03 before agreeing.

### Pitfall 3: Treating REST-02's "same-day back-to-back" clause as needing a same-business-date join
**What goes wrong:** Looking for two `AgentShiftAssignment` rows sharing one business date and concluding the requirement is already vacuously satisfied (there are none — identity is `(agent, date)`, confirmed this session at `AgentShiftAssignment.java:17-24`).
**Why it happens:** "Same-day back-to-back" reads naturally as "two shifts, same business date," but the actual live case is two *adjacent business dates* that fall on the same *calendar* day — e.g., a `15:00`-anchored desk where a `05:00–14:00` shift sits in business day D-1 and a `20:00–05:00` shift sits in business day D, both occurring on the same calendar date.
**How to avoid:** The self-join is always across `date` and `date.plusDays(1)` (business dates), never a same-date join. The pairwise gap-minutes formula (Pattern 3) handles this case identically to the overnight case — there is no special branch needed.
**Warning signs:** Any code path that special-cases "same calendar day" differently from "adjacent business day" for this constraint.

### Pitfall 4: N+1 lookback queries per agent
**What goes wrong:** D-10's horizon-edge lookback is implemented as one query per agent instead of one batched query for the whole desk/period.
**Why it happens:** The natural per-agent loop shape (`for (Agent agent : eligibleAgents) { repo.findLastShiftBefore(agent, date) }`) is the easy thing to write first.
**How to avoid:** The ROADMAP's own standing instruction, repeated in CONTEXT.md D-10: bound the lookback to the maximum `minimum_rest_minutes` configured across all desks, and issue it as **one** batched, date-filtered query. Neither `AgentAssignmentRepository` nor `AgentShiftAssignmentRepository` currently has a date-filtered read (confirmed this session — both only expose `findByTenantIdAndDeskIdAndScheduleId`-shaped methods); this is a new repository method to add, not an existing one to reuse as-is.
**Warning signs:** A new repository method taking a single `agentId` rather than a date range and a desk.

### Pitfall 5: SLOT-mode span derivation colliding with `exactlyOneBreak`'s gap semantics
**What goes wrong:** Defining a SLOT-mode "shift" as the maximal contiguous run of assigned slots (splitting at the mandated break) rather than the whole first-slot-to-last-slot span.
**Why it happens:** "Contiguous run" sounds like the more natural definition of a shift.
**How to avoid:** `exactlyOneBreak` is gated `!= SchedulingMode.SHIFT` (confirmed, `ScheduleConstraintProvider.java:311-313`) and requires *exactly one gap* of the break duration in an agent's assigned timeslots — in SLOT mode, the mandated break **is** a gap in assignment. A maximal-contiguous-run definition would split every compliant SLOT agent-day at its own mandated break and false-positive the rest constraint on every compliant day. D-02's span (first slot start → last slot end, gap ignored) is the only definition that doesn't collide with this existing rule.
**Warning signs:** A SLOT-mode test fixture that has exactly one legal break and still trips the new rest constraint on itself.

### Pitfall 6: `minimum_rest_minutes` sourced from the desk's live value instead of the schedule's snapshot
**What goes wrong:** The accepted-path violation report (REST-07) re-reads `desk.getMinimumRestMinutes()` at render time instead of the value the schedule was actually solved against.
**Why it happens:** It looks redundant to snapshot a value that "is right there on the desk."
**How to avoid:** D-14 snapshots `minimum_rest_minutes` onto `schedule` (V55) for exactly the reason `dayStart` already is — *"the rest minimum is what the score was computed against."* If the desk's value changes after a schedule is accepted, re-deriving from the live desk would silently rewrite history for every previously accepted schedule. Always read from `Schedule`/`ScheduleConfig`'s 15th component on the accepted/report path, never from `Desk` directly.
**Warning signs:** Any accepted-path report code calling `deskRepository.findById(...)` or `desk.getMinimumRestMinutes()`.

## Code Examples

### Verified: zero-tuple no-op from a nullable constraint-config value (REST-04's mechanism)

```java
// Source: src/main/java/com/wfm/model/ConstraintWeights.java (confirmed via grep this session)
// Band capacity (ENVL-08/D-03) precedent for "nullable = unlimited/no tuple":
// "a band's set capacity is a hard cap only when set; blank/null capacity is unlimited
//  and never produces a tuple for this constraint to penalise at all."
```
Apply the identical pattern: both new constraints `.filter(... -> scheduleConfig.minimumRestMinutes() != null && gapMinutes < scheduleConfig.minimumRestMinutes())` — a `NULL` value must short-circuit the stream to zero tuples, not merely compute a zero-weight penalty (REST-04 requires the former; `ConstraintWeights` materializing a defaults row on miss, confirmed, is exactly the trap D-04's own rejection of storing the value there avoids).

### Verified: the `requireShiftEnvelopeSeatSupply` precedent's accumulate-then-throw shape

```java
// Source: src/main/java/com/wfm/service/SolverService.java:1448-1540+ (read in full this session)
static void requireShiftEnvelopeSeatSupply(
        SchedulingMode schedulingMode,
        List<AgentShiftAssignment> shiftAssignments,
        List<ShiftBandPair> shiftBandPairs,
        List<Timeslot> timeslots,
        List<AgentAssignment> assignments,
        int overallocationHardLimitPct,
        List<String> warnings,
        ConstraintWeights weights,
        DayWindow window) {

    if (schedulingMode != SchedulingMode.SHIFT || shiftAssignments == null || shiftAssignments.isEmpty()) {
        return;  // REST-04-equivalent: structural no-op, not a cheap check
    }
    // ... per-date Map<LocalDate, List<...>> grouping (business-date keyed, SOLV-05 discipline) ...
    List<ErrorDetail> errors = new ArrayList<>();
    for (Map.Entry<LocalDate, List<AgentShiftAssignment>> entry : rowsByDate.entrySet()) {
        // ... per-date structural check, errors.add(new ErrorDetail(...)) on violation ...
    }
    // (elsewhere in the caller) if (!errors.isEmpty()) throw new PreSolveValidationException(msg, errors);
}
```
The new REST-03 refusal (`requireRestFeasibility` or similar) should follow this exact shape: a `static` package-private method, taking the already-resolved per-date rows/pairs/window the caller already has in scope (per D-12, "the data is in scope; the predicate is not"), accumulating `ErrorDetail`s, with the caller wrapping a non-empty list in one `PreSolveValidationException`.

### Verified: `AgentException`'s forced invariant (the reason D-07 is a new table, not a widened column)

```java
// Source: src/main/java/com/wfm/model/AgentException.java (read in full this session)
@Column(name = "contracted_hours_override", nullable = false, precision = 5, scale = 2)
private BigDecimal contractedHoursOverride;

// Source: src/main/java/com/wfm/service/AgentExceptionService.java (read in full this session)
if (ex.contractedHoursOverride() == null) {
    throw new IllegalArgumentException("contractedHoursOverride is required");
}
```
This is independent confirmation of CONTEXT.md D-07's reasoning — the column is `NOT NULL` at the schema level *and* enforced again at the service level before the invariant could ever be bypassed via direct repository access. A rest-only waiver riding this table would need both relaxed.

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Gradle (`./gradlew test`) |
| Config file | `build.gradle` (no separate test config file); Postgres-backed tests opt in via `src/test/java/com/wfm/support/PostgresBackedTest.java` (Testcontainers) |
| Quick run command | `./gradlew test --tests "com.wfm.solver.ScheduleConstraintProviderTest"` (or the specific new test class) |
| Full suite command | `./gradlew test` (per `.planning/config.json`'s `test_command`; `test_gate_timeout: 3600`) |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| REST-01 | Operator sets/clears minimum rest per desk via PUT endpoint | integration (controller/service) | `./gradlew test --tests "com.wfm.controller.DeskControllerTest"` | ❌ new test method — Wave 0 |
| REST-02 | Hard violation fires for insufficient rest, both modes, same-day and overnight | unit (constraint stream, `ConstraintVerifier`) | `./gradlew test --tests "com.wfm.solver.ScheduleConstraintProviderTest"` | ❌ new test methods — Wave 0 |
| REST-03 | Pre-solve refusal names agent + both shifts for structurally unavoidable case | unit (`SolverService` static method) | `./gradlew test --tests "com.wfm.service.SolverServiceTest"` (or new class mirroring `ShiftEnvelopeSupplyInvariantTest`) | ❌ new test class — Wave 0 |
| REST-04 | NULL minimum rest produces zero tuples (byte-identical solve) | unit (match-count non-vacuity, mirrors `ConstraintMatchCountNonVacuityTest`) | `./gradlew test --tests "com.wfm.solver.ConstraintMatchCountNonVacuityTest"` | ✅ existing test class to extend |
| REST-05 | Horizon-edge behaviour (first/last day) is deliberate, tested | unit (constructed boundary scenarios, mirrors `MidnightBoundaryScenarioRegistryTest` idiom) | new test class | ❌ Wave 0 |
| REST-06 | Waiver CRUD, solver treats waived pair as legal | integration (service) + unit (constraint stream with waiver fact) | new test class(es) mirroring `AgentExceptionServiceTest` | ❌ Wave 0 |
| REST-07 | Waived violations visible in output, counts on `/summary` | unit (`ScheduleOutputService`) + integration (DTO round-trip) | new test methods on `ScheduleOutputServiceTest`/`ScheduleDetailResponseTest` | ❌ Wave 0 |

### Sampling Rate
- **Per task commit:** targeted `--tests` run against the touched class(es)
- **Per wave merge:** `./gradlew test` (full suite) — note the project's own recorded gotcha: a filtered `--tests` run deletes every other class's JUnit XML, so never read a suite-wide aggregate immediately after one (per this session's operator memory)
- **Phase gate:** Full suite green before `/gsd-verify-work`, plus the structural guards (`MidnightTimeArithmeticGuardTest`, `BusinessDateJoinGuardTest`, and the new D-08 rest-waiver guard) explicitly passing

### Wave 0 Gaps
- [ ] A structural guard test for D-08's shared "is this pair waived" predicate, mirroring `bday-join-guard.md`'s parsed-allowlist pattern or `MidnightTimeArithmeticGuardTest`'s source-scan pattern — does not exist yet, is new infrastructure this phase must build, not reuse
- [ ] `AgentShiftAssignmentRepository`/`AgentAssignmentRepository` date-filtered read methods — confirmed absent this session; needed before any REST-05/D-10 test can exercise the real lookback path
- [ ] `AgentRestWaiverRepository` + `RestWaiverService` + their test doubles
- [ ] A constructed fixture desk with a tight-but-not-impossible shift library, to distinguish "hard violation, solvable with a different choice" from "structurally impossible, pre-solve refused" in the same test file (these are different code paths and need different fixtures)

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | Unchanged — existing tenant/session auth applies uniformly |
| V3 Session Management | no | Unchanged |
| V4 Access Control | yes | New `agent_rest_waiver` table and its repository methods **must** filter on `tenant_id` and `desk_id` explicitly at every query, mirroring `AgentShiftAssignmentRepository`'s own stated reasoning: *"there is no database row-level security, so this is the mitigation"* (verified, `AgentShiftAssignmentRepository.java` class javadoc) |
| V5 Input Validation | yes | `minimum_rest_minutes` bound validation (D-05, negative and `>= 1440` rejected) at the service layer, matching `DeskService.setDayStart`'s existing refusal-ordering precedent (sub-minute precision checked before range); waiver `reason` required-non-blank, mirroring `AgentExceptionService`'s existing `IllegalArgumentException` check |
| V6 Cryptography | no | Not applicable — no new secret/credential material |

### Known Threat Patterns for this stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Cross-tenant waiver read/write via a guessed/enumerated `agent_rest_waiver` id | Elevation of Privilege / Information Disclosure | Every repository method takes `tenantId` explicitly as a parameter and filters on it, per the existing `AgentShiftAssignmentRepository`/`AgentExceptionService` convention (`findByTenantIdAndDeskIdAndAgent_Id...`) — confirmed this session as the established pattern across every sibling repository |
| Cross-desk agent resolution (an id from desk A used against desk B) | Tampering | `AgentExceptionService.saveExceptions` resolves the agent via `agentRepository.findByIdAndTenantIdAndDeskId(...)` (confirmed, three-key lookup) — the new `RestWaiverService` must do the same, not a bare `findById` |
| Unbounded lookback query cost as a DoS vector (D-10) | Denial of Service | The ROADMAP's own standing instruction — bound the lookback to the max configured `minimum_rest_minutes` across desks and batch as one query — is itself the mitigation; an unbounded or N+1 per-agent query is both a correctness and an availability risk on a large desk (SLOT mode's `agent_assignment` is one row per seat per slot — "a month on a large desk is six figures," per CONTEXT.md D-10, independently plausible given `AgentAssignment`'s per-timeslot-per-seat row shape confirmed in its repository) |

## Open Questions

1. **Phase split — evaluate before committing to a wave sequence.**
   - What we know: three V55 schema changes (desk column, schedule column, new table), two constraints plus a shared predicate plus a structural guard, two new date-filtered repository methods plus lookback logic on both solve paths, a horizon-spanning pre-solve refusal, two DTO extensions (detail + summary), and three UI surfaces (all three already fully speced and approved in `22-UI-SPEC.md`). Phases 20 and 21 each ran to twelve plans on comparable or lesser scope (per `STATE.md`'s performance metrics table).
   - What's unclear: whether a single-phase, multi-wave plan can sequence this safely, or whether REST-01/04 (desk config, pure CRUD) should ship as an independently valuable, lower-risk slice before REST-02/03/05 (solver + pre-solve, the highest-risk wave) and REST-06/07 (waiver + disclosure, dependent on both).
   - Recommendation: the planner should treat `PHASE SPLIT RECOMMENDED` as a legitimate return per CONTEXT.md's own "Specific Ideas" section, which states this was raised with and reaffirmed by the operator before CONTEXT.md was written — it is not a scope dispute to re-litigate, but a live option to exercise if the wave count estimate during planning exceeds what a single phase should carry.

2. **D-08's shared-predicate home and name.**
   - What we know: it must be callable from both constraint methods (in `ScheduleConstraintProvider`) and from the pre-solve refusal (in `SolverService`) — two different classes.
   - What's unclear: whether it should live as a small new class (e.g. `RestWaiverPredicate`) both classes depend on, or as a package-private static utility method duplicated-by-reference (e.g. both call through a shared `com.wfm.solver.RestWaivers` or similar helper class in a package both can see).
   - Recommendation: a new small, dependency-free utility class (e.g. `com.wfm.model.RestWaiverLookup` or similar, taking `List<AgentRestWaiver>` and exposing `boolean isWaived(UUID agentId, LocalDate date)`) that both `ScheduleConstraintProvider` and `SolverService` can import — avoids a package-visibility workaround and makes the structural guard's job ("exactly one implementation exists") a simple grep for the predicate's method name.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `minimumRestShift` does not need an explicit `ifExists(ScheduleConfig.class, filtering(schedulingMode == SHIFT))` gate because `AgentShiftAssignment` already has zero rows in SLOT mode by construction | Architecture Patterns, Pattern 1 | LOW — if wrong, the constraint simply never matches in SLOT mode anyway (same outcome), but an explicit gate would make the invariant self-documenting and testable the same way `exactlyOneBreak`'s gate is; recommend the planner add the gate explicitly regardless, for symmetry with D-03's "two mode-gated constraints" framing rather than relying on an implicit structural guarantee |
| A2 | A new small utility class is the best home for D-08's shared predicate, rather than a static method on `ScheduleConstraintProvider` | Open Questions #2 | LOW — reversible (`D-08`'s own reversibility note calls this "reversible — two constraint methods"); a planner preferring the static-method approach loses nothing structurally, as long as exactly one implementation exists and the guard test enforces it |
| A3 | `ofHard(1000)` (matching `noOverlapWeight`/`nonWorkingDaySeatWeight`'s tier) is an appropriate default hard weight for the rest constraint(s), rather than the `ofHard(10_000)` tier reserved for `agentDayOff` | Discretion Resolutions below | MEDIUM — this is a genuine product/compliance judgment call (how severe is a rest violation relative to a day-off violation) with no regulatory default available per `FEATURES.md:150`; wrong choice is cheap to correct (one migration constant), but should be confirmed with the operator at discuss-phase or plan-review rather than shipped silently |
| A4 | The exact bound on `minimum_rest_minutes` should be "strictly less than 1440 minutes (24h)," matching the UI-SPEC's already-written copy ("Minimum rest must be less than 24 hours") rather than some other sub-24h ceiling | Discretion Resolutions below | LOW — the UI-SPEC is already approved with this exact wording, so this is effectively locked by the UI contract already in place, not a genuinely open choice |

**If this table is empty:** N/A — see above.

## Discretion Resolutions (Claude's Discretion items, with recommendations)

1. **Exact upper bound on `minimum_rest_minutes` + refusal wording.** Recommend: reject when `minutes < 0 || minutes >= 1440`. This is the simplest bound satisfying D-05's "below 24 hours" requirement and is **already the exact wording shipped in the approved UI-SPEC**: *"Minimum rest must be less than 24 hours."* (22-UI-SPEC.md, Copywriting Contract). Do not introduce a different numeric ceiling (e.g. 23h) without amending the UI-SPEC first.

2. **D-10 lookback anchor source.** Recommend the predecessor schedule's own snapshotted `dayStart`, exactly as CONTEXT.md itself recommends, by the same D-14/21-CONTEXT-D-15 argument: the anchor is part of that historical schedule's identity, not a property of "the desk right now." This also avoids an inconsistency where a desk re-anchored since the predecessor was solved would silently reinterpret the predecessor's own instants.

3. **Rest value behaviour on scheduling-mode switch.** Recommend **no refusal and no warning** — leave `minimum_rest_minutes` untouched and fully effective across a `switchSchedulingMode` call. Rationale: `switchSchedulingMode`'s existing SHIFT-only coverage refusal exists because shift libraries and SLOT timeslots are structurally incompatible concepts; minimum rest has no such incompatibility by D-01/D-02's own design — a desk-level minutes value applies identically regardless of mode, with D-02 having already defined what "a shift" means in SLOT mode specifically so this generalizes. Treat this as a non-event, unlike `setDayStart`'s accepted-schedule lock (which has a genuine reason, per D-14's explicit discussion).

4. **`unusedRestWaivers` scope.** Recommend period-scoped (restricted to waivers whose date falls within the solved schedule's `[periodStartDate, periodEndDate]`), not desk-wide. Rationale: D-13 is explicit the whole disclosure is *"computed deterministically from the solution"* — a desk-wide listing would require a second, solution-independent query and would mix "waivers irrelevant to this solve" into a report whose stated purpose is "so a waiver cannot silently hide a roster problem" *in this schedule*. A desk-wide waiver management view, if wanted, is a separate, simpler listing endpoint (already effectively available via the `AgentExceptions.tsx` Rest Waivers table itself, which is desk/agent-scoped but not period-scoped) — not this report.

5. **Excel export disclosure.** Recommend implementing it if the phase is not split (reuse 21-CONTEXT D-12's established Roster-cell-plus-legend-row convention, which is a proven, cheap mechanism for exactly this kind of per-cell disclosure); if the phase *is* split per Open Question #1, defer export disclosure to the wave that ships REST-07's UI tab, since the export's data dependency (D-13's computed fields) is identical.

6. **Hard weight magnitude.** Recommend `HardSoftScore.ofHard(1000)`, matching the tier already used for `noOverlapWeight` and `nonWorkingDaySeatWeight` (both confirmed `ofHard(1000)` this session) — both are, like a rest violation, "this schedule is operationally illegal" rather than "this schedule is low quality" (the `ofHard(1)` tier: `specMatchWeight`, `shiftEnvelopeComplianceWeight`) but are not the single most severe violation in the system (`agentDayOff`'s `ofHard(10_000)`, confirmed). Use the **same weight constant** for both `minimumRestShift` and `minimumRestSlot` (one `@ConstraintWeight` column, e.g. `minimum_rest_weight`) — D-08's "the two constraints must never disagree" argument extends naturally to sharing one weight, not just one predicate. Flag this as Assumption A3 for operator confirmation.

7. **D-08 predicate naming/home.** See Open Question #2 — recommend a new small utility class, not a static method buried in one of the two consuming classes.

8. **Waiver entry UI surface.** **Already resolved** — `22-UI-SPEC.md` Section 2 confirms extending `AgentExceptions.tsx` with a new "Rest Waivers" section, immediate (non-batched) Add/Delete, no new route. No further discretion needed here.

9. **Task ordering / plan decomposition.** Recommend, in dependency order: (a) V55 migration landing all three schema changes in one file (desk column, schedule column, new table) as a single atomic commit, matching the project's established one-migration-per-logical-change discipline; (b) D-08's shared predicate + its structural guard, built and tested against a hand-constructed waiver list before any constraint reads it; (c) the two new repository date-filtered read methods (D-10), tested independently of the solver; (d) the two mode-gated constraints plus the self-join gap math (Patterns 1-3), the highest-risk wave — needs the fixture-construction care Pitfall 5 and the edge-case note in Pattern 3 call out; (e) the D-12 pre-solve refusal, which can reuse (c)'s lookback and needs (b)'s predicate to exclude waived pairs; (f) the two DTO/output extensions (D-13/D-14); (g) the three already-speced UI surfaces (D-04's column, D-06's waiver section, D-15's badge+tab), which have no remaining design risk since `22-UI-SPEC.md` is approved.

## Sources

### Primary (HIGH confidence — read in full this session, this repo, HEAD)
- `src/main/java/com/wfm/solver/ScheduleConstraintProvider.java` — `defineConstraints` registry (26 entries, confirmed), `exactlyOneBreak`, `breakClustering`/`ClusterMark`, `resolveAnchor`/`anchorFor`, `AGENT_ID`/`DATE`/`TO_LIST` grouping constants, `oneAssignmentPerTimeslot`'s forEach-vs-forEachUniquePair comment, `ofHard`/weight usage
- `src/main/java/com/wfm/util/DayWindow.java` — full file read; `anchoredEndMinute`/`anchoredStartMinute`/`anchoredDurationMinutes` contracts and ranges quoted verbatim
- `src/main/java/com/wfm/service/SolverService.java` — `requireShiftEnvelopeSeatSupply` (lines ~1448-1560+), exception-map build sites, the exception/day-off coincidence refusal (~line 1300)
- `src/main/java/com/wfm/model/AgentShiftAssignment.java`, `AgentException.java`, `Schedule.java`, `ScheduleConfig.java`, `ConstraintWeights.java`, `Desk.java` — full or targeted reads confirming identity shape, nullability, weight values, column names
- `src/main/java/com/wfm/service/AgentExceptionService.java` — full file read, confirming the forced-invariant reasoning behind D-07
- `src/main/java/com/wfm/dto/ScheduleDetailResponse.java`, `ScheduleSummary.java` — `ViolationDetail`/`dayStart` fields confirmed
- `src/main/java/com/wfm/repository/AgentAssignmentRepository.java`, `AgentShiftAssignmentRepository.java`, `AcceptedScheduleDateRepository.java` — full files read, confirming absence of date-filtered reads
- `src/main/java/com/wfm/controller/DeskController.java` — `/day-start` endpoint shape confirmed as the sibling to mirror
- `src/main/java/com/wfm/service/ShiftTemplateService.java` — `MAX_SPAN_MINUTES = 16 * 60` confirmed
- `src/main/java/com/wfm/exception/PreSolveValidationException.java` — full file read
- `src/test/resources/bday-join-guard.md`, `midnight-time-arithmetic.md` — guard-file format and stated purpose read in full
- `build.gradle` — Timefold `1.16.0` pin confirmed
- `src/main/resources/db/migration/` directory listing — V54 head confirmed, V55 free

### Secondary (MEDIUM confidence)
- `.planning/phases/22-minimum-rest/22-CONTEXT.md` — extensively cross-checked against HEAD this session; every spot-checked claim confirmed exact
- `.planning/phases/22-minimum-rest/22-UI-SPEC.md` — read in full; approved, 7/7 dimensions PASS

### Tertiary (LOW confidence)
- None — no WebSearch or external-source claims were needed for this phase; it is entirely an in-repo, Timefold-API-already-pinned implementation.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — no new dependency, Timefold 1.16.0 pin independently confirmed
- Architecture: HIGH — every pattern cited has a working precedent read in full this session
- Pitfalls: HIGH — each pitfall is grounded in a specific, quoted existing guard/comment/javadoc in this codebase, several already having caused a real bug in this milestone (`5ddd8dc`)
- Discretion resolutions: MEDIUM — reasoned recommendations, but genuinely product/operator judgment calls (weight magnitude, waiver scope) that should be confirmed, not silently assumed

**Research date:** 2026-10-03
**Valid until:** Next schema change to `desk`/`schedule`/`constraint_weights` or Timefold version bump — this is an internal-codebase research doc, not time-decaying external documentation; treat as valid for the life of this phase's planning and execution.
