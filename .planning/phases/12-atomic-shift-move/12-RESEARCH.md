# Phase 12: Atomic Shift Move - Research

**Researched:** 2026-08-13
**Domain:** Timefold Solver custom move implementation (constraint satisfaction / local search)
**Confidence:** HIGH (codebase grounding + Timefold source read at the exact pinned version tag) / MEDIUM (docs-site cross-checks, no version-pinned docs mirror exists)

## Summary

The solver is stuck because `AgentAssignment.agent` is a single-entity-at-a-time planning
variable and the default local-search move pool (`ChangeMove` + `SwapMove`, auto-configured by
Timefold because `solverConfig.xml` declares no explicit `moveSelector`) can only flip one seat
at a time. Every incremental path from a partial shift to a complete, correctly-breaked shift
passes through an intermediate state that violates the HARD `Exactly one break` constraint, so
local search rejects the intermediate step and the shift can never grow past the point where a
break would be required. This is not a tuning problem — two prior attempts (moving the break
threshold, softening under-allocation, see `729ba03`/`76a715f`) only relocated the wall and were
reverted. A hand-rolled multi-pass pre-assignment pipeline (`BreakAwareConstructionPhase`) was
already tried and abandoned in this exact codebase for the same class of reason (no
backtracking, quality loss compounding across passes) — it is now a dead no-op class still in
the tree.

Timefold 1.16.0 (the version this project is pinned to) has exactly the API needed: `AbstractMove`
no longer requires a hand-written undo move — the solver auto-generates undo moves internally,
and `createUndoMove` is formally deprecated-for-removal since 1.16.0 — so a custom move only
needs to implement `doMoveOnGenuineVariables(ScoreDirector)`. `CompositeMove.buildMove(Move...)`
lets N single-seat assignment moves be composed into one atomic, automatically-reversible move
with almost no custom plumbing. This is confirmed by reading the actual Timefold source at the
`v1.16.0` git tag (not the latest docs, which describe a materially different, newer
`Neighborhoods` API introduced at 1.31.0 that does **not** apply here).

**Primary recommendation:** Implement a `MoveListFactory<Schedule>` (or `MoveIteratorFactory` if
profiling shows the eagerly-built list is too large) that, per agent-day needing more hours,
finds a contiguous run of free, specialization-matching `AgentAssignment` seats long enough for
the full contracted shift plus a legally-positioned break gap, and returns a
`CompositeMove.buildMove(...)` of one lightweight custom `Move` per seat (do **not** use
Timefold's internal `ChangeMove` class directly — its constructor needs an internal
`GenuineVariableDescriptor` not exposed on the public API; write a trivial `AssignSeatMove`
instead). Wire this factory into `solverConfig.xml` inside an explicit `<unionMoveSelector>`
alongside `<changeMoveSelector/>` and `<swapMoveSelector/>` — Timefold's default move
auto-configuration only fires when `<localSearch>` has **no** `moveSelector` at all; adding the
custom factory without also re-declaring the change/swap selectors will silently delete
fine-grained repair capability.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Candidate shift-window discovery (contiguous free seats, spec match) | Solver (custom `MoveListFactory`) | — | Must run inside the solver loop against the live working solution; no other tier has visibility into per-step assignment state |
| Break-position legality filtering (`breakBlockedHours`, `breakStartAlignment`) at move-generation time | Solver (custom move factory) | Constraint provider (still the source of truth / fallback) | Generating only legal candidates avoids wasting search budget on moves the HARD constraints will reject anyway; the constraint provider remains authoritative so illegal states are still penalized if some other move path produces one |
| Atomic multi-seat assignment + rollback | Solver (`CompositeMove` over custom `Move`s) | — | Reversibility is an in-solver-loop concern (`ScoreDirector` before/after hooks); no persistence-layer concept |
| Solver config wiring (`unionMoveSelector`, termination) | Solver config (`solverConfig.xml` / `SolverConfig` builder) | `SolverService` (programmatic termination override) | Config-layer composition of move selectors is declared once; `SolverService` already overrides termination programmatically per-request |
| Constraint definitions consumed by the move (break, contracted-hours, spec match) | Solver (`ScheduleConstraintProvider`) | — | Unchanged by this phase — the move must respect existing constraints, not redefine them |

## User Constraints

No `12-CONTEXT.md` exists for this phase (skipped per orchestrator instruction). The following
are the locked design constraints stated directly in `ROADMAP.md` Phase 12 (treated with the
same authority as a locked `CONTEXT.md` decision because they were written by the operator, not
inferred):

- Seats are per-timeslot with a required specialization; the move must find free,
  spec-matching seats across a contiguous window.
- Timefold requires exactly reversible moves; incorrect undo produces corrupted-score bugs that
  are hard to diagnose.
- Must compose with existing change moves (`unionMoveSelector`), not replace them — fine-grained
  repair is still needed.
- Should honour `breakBlockedHours` and `breakStartAlignment` at generation time so illegal
  placements are never produced.
- Success must be measured across repeated runs, not one solve — run-to-run variance currently
  exceeds the effect size of most changes.

## Codebase Grounding

All file paths below were read directly in this session (`Read` tool) unless marked otherwise.

### Domain model

**`AgentAssignment`** — `src/main/java/com/wfm/model/AgentAssignment.java:10-41`
```java
@PlanningEntity(difficultyComparatorClass = AgentAssignmentDifficultyComparator.class)
@Entity
@Table(name = "agent_assignment")
public class AgentAssignment {
    @PlanningId
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    ...
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "timeslot_id", nullable = false)
    private Timeslot timeslot;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "specialization_id", nullable = false)
    private Specialization requiredSpecialization;
    @PlanningVariable(valueRangeProviderRefs = "agentRange", nullable = true)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id")
    private Agent agent;
```
One `AgentAssignment` = one seat in one timeslot with one required specialization. `agent` is
the **sole** planning variable, `nullable = true` (unassigned seats are legal, scored by
`unassignedAssignment`/`bulkUnderallocation*` constraints). There is no shift/pattern entity —
"a shift" is a derived concept: the set of `AgentAssignment`s sharing the same `agent` on the
same date, contiguous by `Timeslot.startTime`.

**`Schedule`** (`@PlanningSolution`) — `src/main/java/com/wfm/model/Schedule.java:22-141`. Key
providers:
```java
@ValueRangeProvider(id = "agentRange")
@ProblemFactCollectionProperty
private List<Agent> agents = new ArrayList<>();          // line 106-109

@PlanningEntityCollectionProperty
private List<AgentAssignment> assignments = new ArrayList<>();   // line 139-141

@ProblemFactCollectionProperty
private List<AgentDayConfig> agentDayConfigs = new ArrayList<>(); // line 131-133
```
`agentRange` (line 106) is the exact `valueRangeProviderRefs` string a custom move must resolve
values through if it ever needs to enumerate legal agents — but for this move the agent is
already known (we're filling out one agent's own shift), so this is only relevant if the move
factory ever needs to discover *which* agents are under-hours by scanning `schedule.getAgents()`
and `schedule.getAgentDayConfigs()` (both already `@ProblemFactCollectionProperty`, directly
readable from the `Schedule` passed into `MoveListFactory.createMoveList(Schedule)`).

**`AgentDayConfig`** (record) — `src/main/java/com/wfm/model/AgentDayConfig.java:13-24` — carries
`effectiveHours`, `incrementMinutes`, `breakDurationMinutes`, `breakMinShiftHours`,
`breakBlockedHours`, `breakStartAlignment` per (agent, date). This is the exact fact set the
constraint provider joins against for every break rule (`ScheduleConstraintProvider.java:150-152`,
`203-205`, `232-234`, `272-274`) and is the correct input for the move factory to compute a
*legal* candidate break position before generating the move — matching what the constraint would
check, so the move never produces a state the HARD constraints reject.

**`Timeslot`** — `src/main/java/com/wfm/model/Timeslot.java:10-56`. No sequence/index field;
contiguity is derived purely by `date` + stepping `startTime` by `incrementMinutes`
(`ScheduleConstraintProvider.getGapLengths`, lines 531-559, uses a `TreeSet<LocalTime>` and walks
by `incrementMinutes`). A move factory building candidate windows must replicate this exact
walk — reusing/extracting this logic (or an equivalent) avoids the move generating a "contiguous"
window that the constraint provider's own gap detector disagrees with.

**Difficulty comparator** — `src/main/java/com/wfm/solver/AgentAssignmentDifficultyComparator.java:16-31`
sorts by (date, timeslot start, id) — used only by the construction heuristic
(`FIRST_FIT_DECREASING`, implied default); not consulted by local search moves, irrelevant to
this phase directly but explains why the CH already tends to build shifts sequentially within a
day.

### Solver configuration

**`src/main/resources/solverConfig.xml`** (full file read, 27 lines):
```xml
<solver ...>
    <solutionClass>com.wfm.model.Schedule</solutionClass>
    <entityClass>com.wfm.model.AgentAssignment</entityClass>
    <scoreDirectorFactory>
        <constraintProviderClass>com.wfm.solver.ScheduleConstraintProvider</constraintProviderClass>
    </scoreDirectorFactory>
    <constructionHeuristic/>
    <localSearch>
        <acceptor>
            <simulatedAnnealingStartingTemperature>0hard/3000soft</simulatedAnnealingStartingTemperature>
        </acceptor>
    </localSearch>
</solver>
```
**There is no `unionMoveSelector` or any `moveSelector` element declared today** — contrary to
what the phase description's "must compose with `unionMoveSelector`" implies about current
state, that element does not yet exist in the file; it must be *added* by this phase, not merely
extended. [VERIFIED: src/main/resources/solverConfig.xml:1-27]

The acceptor is **Simulated Annealing** (`0hard/3000soft` starting temperature), not Tabu Search.
This matters for the `equals`/`hashCode`/`getPlanningEntities`/`getPlanningValues` contract
(see Timefold API section) — SA does not require them, but they should still be implemented
correctly per the `Move` interface's own recommendation, since (a) nothing prevents an operator
from switching acceptors later, and (b) the phase description explicitly lists tabu-acceptor
correctness as a requirement.

**Termination** is not set in the XML; `SolverService.java:341-345` overrides it per-request via
`SolverConfigOverride<Schedule>().withTerminationConfig(new TerminationConfig().withSpentLimit(...).withUnimprovedSpentLimit(...))`,
driven by `solver.time-limit` (`application.yml`/env, default `PT5M` — `SolverService.java:60`,
`@Value("${solver.time-limit:PT5M}")`). This is wall-clock (`Duration`) based, not step-count
based — directly relevant to the observed run-to-run variance (see Validation Architecture).

### Constraint provider

**`src/main/java/com/wfm/solver/ScheduleConstraintProvider.java`** (full file read, 610 lines).
The relevant HARD constraint:

```java
// lines 143-189
private Constraint exactlyOneBreak(ConstraintFactory factory) {
    return factory.forEach(AgentAssignment.class)
            .filter(a -> a.getAgent() != null)
            .groupBy(a -> a.getAgent().getId(), a -> a.getTimeslot().getDate(), toList())
            .join(AgentDayConfig.class, ...)
            .filter((daId, date, assignments, dayConfig) -> {
                BigDecimal effectiveHours = dayConfig.effectiveHours();
                boolean needsBreak = effectiveHours.compareTo(dayConfig.breakMinShiftHours()) > 0;
                if (!needsBreak) {
                    return countContiguousGaps(assignments, dayConfig.incrementMinutes()) != 0;
                }
                int breakThresholdSlots = dayConfig.breakMinShiftHours()
                        .multiply(BigDecimal.valueOf(60))
                        .divide(BigDecimal.valueOf(dayConfig.incrementMinutes()), 0, RoundingMode.CEILING)
                        .intValue();
                if (assignments.size() < breakThresholdSlots) {
                    return countContiguousGaps(assignments, dayConfig.incrementMinutes()) > 1;
                }
                int gaps = countContiguousGaps(assignments, dayConfig.incrementMinutes());
                if (gaps != 1) return true;
                int expectedBreakSlots = dayConfig.breakDurationMinutes() / dayConfig.incrementMinutes();
                return totalGapSlots(assignments, dayConfig.incrementMinutes()) != expectedBreakSlots;
            })
            .penalizeConfigurable(...)
            .asConstraint("Exactly one break");
}
```
This is CH-friendly (does not fire below `breakThresholdSlots`) but **does** fire hard once an
agent has enough slots to need a break and doesn't have exactly one correctly-sized gap — which
is precisely the wall: single-seat `ChangeMove`s add slots one at a time and cannot create a
multi-slot gap atomically, so every intermediate state above the threshold without a completed
gap is HARD-penalized and rejected by local search's SA acceptor before it can be improved
further.

Companion HARD constraints a candidate move must also satisfy simultaneously:
`breakDuration` (lines 196-218, gap must equal `breakDurationMinutes/incrementMinutes` slots),
`breakBlockedWindow` (lines 225-258, break must not fall in the first/last `breakBlockedHours`),
`breakStartAlignment` (lines 265-287, break start must satisfy `isAligned(time, alignment)` —
`ON_HOUR`/`ON_HALF_HOUR`/`ON_QUARTER_HOUR`, lines 595-602), `specializationMatch` (lines 93-108,
primary or secondary specialization must include the seat's `requiredSpecialization`),
`contractedHoursOver`/`contractedHoursUnder`/`contractedHoursUnderZero` (lines 294-359, exact
slot count must equal `expectedWorkSlots(dayConfig)`, lines 515-520), and
`oneAssignmentPerTimeslot` (lines 119-129, an agent cannot occupy two seats in the same
timeslot — relevant when overflow seats exist for the same timeslot).

The move-generation algorithm in the new factory should mirror `getGapLengths`/`findBreakStart`/
`isAligned` (lines 522-602) exactly when computing where the break should sit within a candidate
window, so the generated move lands in a state all of the above constraints simultaneously
accept in one step.

**Dead code / prior attempt, still in the tree:**
`src/main/java/com/wfm/solver/BreakAwareConstructionPhase.java` (41 lines, full file read) is a
vestigial no-op class — `grep` confirms it is referenced nowhere else in the codebase. Its
javadoc (lines 9-24) documents that a *previous* 6-pass brute-force pre-assignment pipeline
(`computeWorkSlots → assign → repairBreakGeometry → mopUpUnassigned → repairBreakGeometry →
fillUnderassigned`) was removed because "sequential passes with no backtracking lost quality at
each step, and repair cascades caused underassignment." This is directly relevant prior art: a
hand-rolled, non-Timefold-native pre-assignment approach to this exact problem was already tried
in this repo and abandoned for the same structural reason a custom `Move` avoids (Timefold's
search/acceptor provides the backtracking a manual pipeline lacks). **Do not resurrect this
class or its approach** — the roadmap's own conclusion after that experiment was "the real fix —
an atomic 'assign full shift with break' move... is scoped as its own piece of work" (commit
`76a715f`), i.e. this phase.

### SolverService — where the schedule is assembled

`src/main/java/com/wfm/service/SolverService.java` (full file read, 1084 lines).
- `agentDayConfigs` are computed once per solve (`computeAgentDayConfigs`, lines 496-534) and
  attached as a `@ProblemFactCollectionProperty` — directly usable by the move factory without
  recomputation.
- `expandAssignments`/`expandOverflowAssignments` (lines 875-927) create `AgentAssignment` seats:
  one per staffing-requirement FTE (`demandAssignments`) plus additional "overflow" seats up to
  `overallocationHardLimitPct` (default 130%, configurable per schedule,
  `Schedule.java:71-72`). **Overflow seats exist specifically so agents can be assigned beyond
  raw demand to hit exact contracted hours** (`SolverService.java:218-221` comment) — this is the
  seat inventory the new move must search across; a naive search over only `demandAssignments`
  would under-count available seats for agents whose shift needs to extend past demand-covered
  timeslots.
- `runPreSolveScoreDiagnostic` (lines 929-996) is an existing pattern for verifying incremental
  score sanity before a real solve — assigns one agent to one seat, checks the score delta is
  positive, and prints a constraint breakdown via `SolutionManager.explain(...)` if it isn't.
  This is a reusable pattern for a similar pre-solve or unit-test diagnostic specific to the new
  move (assign a full shift via the move, confirm the score delta matches hand-computed
  expectations).

### Existing solver tests — established patterns to follow

- `src/test/java/com/wfm/solver/BreakAwareConstructionTest.java` (573 lines, full file read) —
  the canonical pattern for building a `Schedule` fixture by hand (agents, timeslots, staffing
  requirements, `AgentDayConfig`s) and running a real `SolverFactory`-built solver against it
  with `SolverConfig().withPhases(new ConstructionHeuristicPhaseConfig(), new
  LocalSearchPhaseConfig().withTerminationConfig(...))`. New tests for the shift move should
  follow this exact fixture-construction style rather than introducing a new one.
- `src/test/java/com/wfm/solver/IncrementalScoringDiagnosticTest.java` — contains the only
  existing use of `EnvironmentMode` in the codebase:
  ```java
  // line 139
  .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
  ```
  [VERIFIED: src/test/java/com/wfm/solver/IncrementalScoringDiagnosticTest.java:136-149] This is
  the existing convention to follow for a new "verify the custom move doesn't corrupt score"
  test.
- **No test in this codebase calls `SolverConfig.withRandomSeed(...)`** — `grep -rn
  "withRandomSeed\|RandomFactory\|randomType"` across all `.java`/`.xml` files returns no
  matches. Every existing solver test is time-limited (`Duration`), not seeded. This is a gap
  the Validation Architecture section below addresses directly, and it plausibly contributes to
  the observed run-to-run variance.

### Build / versions

`build.gradle` (grepped): `languageVersion = JavaLanguageVersion.of(21)`, Spring Boot
`3.4.2`, and:
```
implementation platform('ai.timefold.solver:timefold-solver-bom:1.16.0')
implementation 'ai.timefold.solver:timefold-solver-spring-boot-starter'
implementation 'ai.timefold.solver:timefold-solver-jpa'
testImplementation 'ai.timefold.solver:timefold-solver-test'
```
[VERIFIED: build.gradle:3,12,35-37,44]

**Discrepancy note:** `.planning/STATE.md` "Carried forward as active design constraints"
states *"Timefold pinned at 1.33.0 — `ScoreAnalysis` moves to paid tier in 2.0"*. The actual
dependency declared in `build.gradle` is **`1.16.0`**, not `1.33.0`. All API verification in
this document was done against the actual installed version (`1.16.0`), read directly from the
`v1.16.0` tag of the upstream Timefold repository. The planner should flag this version-number
discrepancy for operator confirmation — either `STATE.md` is stale, or a bump to 1.33.0 was
intended but never applied to `build.gradle`. `git grep -rn "1.16.0\|1.33.0" -- '*.gradle*'`
confirms `1.16.0` is the only version string present in the build files. **All findings below
are tagged 1.16.0**; if the version is bumped before/during this phase's execution, the
`AbstractMove`/`CompositeMove`/`EnvironmentMode` findings should be re-verified against the new
version (the "Neighborhoods" API introduced at 1.31.0 is a materially different, not-yet-verified
alternative, see below).

No `unionMoveSelector`, `CustomPhaseCommand`, or existing custom move factory exists anywhere in
`src/main` today — this move is being added from a clean slate. `grep -rln
"unionMoveSelector|MoveListFactory|MoveIteratorFactory|CustomPhaseCommand"` across all `.java`
files returns no matches.

## Package Legitimacy Audit

Not applicable — this phase adds no new external package dependencies. It is pure Java
implementation against the already-declared `ai.timefold.solver:timefold-solver-*:1.16.0` BOM
(`build.gradle:35-37`) plus (optionally, for the benchmark harness, see Validation Architecture)
`ai.timefold.solver:timefold-solver-benchmark`, which is part of the same BOM/group and requires
no separate legitimacy check — it is the community-edition benchmarking module published by the
same vendor as the already-approved core dependency.

## Timefold Custom Move API (verified against 1.16.0, the version this repo uses)

All class/method signatures below were read directly from
`github.com/TimefoldAI/timefold-solver` at git tag `v1.16.0` via `gh api
/repos/TimefoldAI/timefold-solver/contents/<path>?ref=v1.16.0`, i.e. the exact source that
compiles into the `1.16.0` JARs this project depends on — not the latest docs site (which
documents a newer, materially different API surface introduced in later 1.3x/2.x releases).
Because the seam's `classify-confidence` tool has no provider bucket for "read pinned-version
source from the vendor's own repository," these are tagged `[VERIFIED: github.com/...]` on the
strength that this is literally the compiled implementation, stronger evidence than any
documentation page for a specific pinned version.

### 1. Undo model: `createUndoMove` is dead; moves are auto-reversible

[VERIFIED: github.com/TimefoldAI/timefold-solver/blob/v1.16.0/core/src/main/java/ai/timefold/solver/core/impl/heuristic/move/AbstractMove.java]
```java
public abstract class AbstractMove<Solution_> implements Move<Solution_> {
    @Override
    public final void doMoveOnly(ScoreDirector<Solution_> scoreDirector) {
        var recordingScoreDirector = scoreDirector instanceof VariableChangeRecordingScoreDirector<Solution_> v
                ? v : new VariableChangeRecordingScoreDirector<>(scoreDirector);
        doMoveOnGenuineVariables(recordingScoreDirector);
        scoreDirector.triggerVariableListeners();
    }

    @Deprecated(forRemoval = true, since = "1.16.0")
    protected Move<Solution_> createUndoMove(ScoreDirector<Solution_> scoreDirector) {
        throw new UnsupportedOperationException("Operation requires an undo move, which is no longer supported.");
    }

    protected abstract void doMoveOnGenuineVariables(ScoreDirector<Solution_> scoreDirector);
}
```
This directly answers the research priority's open question ("Timefold changed this; confirm
which applies here"): **at 1.16.0, the ephemeral/auto-undo model applies. `createUndoMove` must
NOT be overridden — it is deprecated for removal and throws if called.** A custom move only
implements `doMoveOnGenuineVariables(ScoreDirector)`; Timefold wraps it in a
`VariableChangeRecordingScoreDirector` that records the variable changes so it can construct the
reverse automatically. This removes an entire, historically bug-prone category of hand-written
undo logic from the risk surface — the "Timefold requires exactly reversible moves" constraint
from the roadmap is satisfied *by the framework*, not by code this phase has to get right by
hand, provided `doMoveOnGenuineVariables` correctly brackets every variable write with
`beforeVariableChanged`/`afterVariableChanged`.

The base `Move<Solution_>` interface confirms the exact contract:
[VERIFIED: github.com/TimefoldAI/timefold-solver/blob/v1.16.0/core/src/main/java/ai/timefold/solver/core/impl/heuristic/move/Move.java]
```java
public interface Move<Solution_> {
    boolean isMoveDoable(ScoreDirector<Solution_> scoreDirector);
    default void doMoveOnly(ScoreDirector<Solution_> scoreDirector) { ... } // AbstractMove overrides as final
    default Move<Solution_> rebase(ScoreDirector<Solution_> destinationScoreDirector) { throw ... }
    default Collection<?> getPlanningEntities() { throw new UnsupportedOperationException(...); }
    default Collection<?> getPlanningValues() { throw new UnsupportedOperationException(...); }
}
```
`getPlanningEntities()`/`getPlanningValues()` throw `UnsupportedOperationException` by default —
**must be overridden** or Entity/Value Tabu Search breaks (not in play today, SA acceptor is
configured, but implement them anyway per the interface's own recommendation and in case the
acceptor changes later). `rebase()` throws by default too — **must be overridden** if
multithreaded solving is ever enabled (`moveThreadCount` in solver config); not currently set in
`solverConfig.xml`, but implement it for forward-compatibility since it's a two-line
`lookUpWorkingObject` translation per held entity/value.

`ScoreDirector`'s public contract confirms the exact hooks a `doMoveOnGenuineVariables`
implementation must call:
[VERIFIED: github.com/TimefoldAI/timefold-solver/blob/v1.16.0/core/src/main/java/ai/timefold/solver/core/api/score/director/ScoreDirector.java]
```java
void beforeVariableChanged(Object entity, String variableName);
void afterVariableChanged(Object entity, String variableName);
void triggerVariableListeners();
```
(the last is called automatically by `AbstractMove.doMoveOnly` — a custom move must NOT call it
itself when extending `AbstractMove`, only when implementing `doMoveOnGenuineVariables`.)

### 2. `CompositeMove` — the low-risk path for the atomic shift move

[VERIFIED: github.com/TimefoldAI/timefold-solver/blob/v1.16.0/core/src/main/java/ai/timefold/solver/core/impl/heuristic/move/CompositeMove.java]
```java
public final class CompositeMove<Solution_> extends AbstractMove<Solution_> {
    public static <Solution_, Move_ extends Move<Solution_>> Move<Solution_> buildMove(Move_... moves) {
        return switch (moves.length) {
            case 0 -> NoChangeMove.getInstance();
            case 1 -> moves[0];
            default -> new CompositeMove<>(moves);
        };
    }
    public static <Solution_, Move_ extends Move<Solution_>> Move<Solution_> buildMove(List<Move_> moveList) { ... }

    @Override
    public boolean isMoveDoable(ScoreDirector<Solution_> scoreDirector) {
        for (Move<Solution_> move : moves) if (move.isMoveDoable(scoreDirector)) return true;
        return false;   // NOTE: ANY doable, not ALL
    }

    @Override
    protected void doMoveOnGenuineVariables(ScoreDirector<Solution_> scoreDirector) {
        for (Move<Solution_> move : moves) {
            if (!move.isMoveDoable(scoreDirector)) continue;   // silently skips non-doable submoves
            move.doMoveOnly(scoreDirector);  // triggers variable listeners between moves
        }
    }
}
```
This **is** a viable, low-risk composition path, confirming the research priority's hypothesis —
but with two behavioral traps the plan must account for:
1. **`isMoveDoable` is an OR, not an AND** across submoves. If the shift-move's semantics require
   "place all N seats or none," `CompositeMove`'s default doability check is insufficient on its
   own — the move factory must pre-filter and only ever construct a `CompositeMove` from a fully
   valid, all-doable set of seat-assignments in the first place (i.e., do the "are all N seats
   free and spec-matching" check when *generating* candidates, not rely on `isMoveDoable` to
   reject a partially-valid composite at evaluation time).
2. **`doMoveOnGenuineVariables` silently skips any submove that isn't doable** at execution time
   rather than failing the whole move — this is a second, independent reason candidate
   generation must guarantee all-or-nothing validity up front; do not depend on `CompositeMove`
   to enforce atomicity for you.

`CompositeMove` does **not** use Timefold's internal `ChangeMove` — it composes any
`Move<Solution_>`. Timefold's own `ChangeMove` class was also read directly:
[VERIFIED: github.com/TimefoldAI/timefold-solver/blob/v1.16.0/core/src/main/java/ai/timefold/solver/core/impl/heuristic/selector/move/generic/ChangeMove.java]
```java
public ChangeMove(GenuineVariableDescriptor<Solution_> variableDescriptor, Object entity, Object toPlanningValue)
```
`GenuineVariableDescriptor` is an internal (`impl` package) type obtained through Timefold's
domain-metamodel machinery, not exposed as a stable public constructor argument for user code to
build from scratch. **Do not attempt to construct Timefold's `ChangeMove` directly** — it is not
designed for direct instantiation outside the framework's own selector factories. Instead, write
a small custom `Move<Schedule>` (e.g. `AssignSeatMove`) that holds an `AgentAssignment entity`
and target `Agent toAgent`/`null`, and does the assignment via plain getters/setters:
```java
final class AssignSeatMove extends AbstractMove<Schedule> {
    private final AgentAssignment assignment;
    private final Agent toAgent;
    AssignSeatMove(AgentAssignment assignment, Agent toAgent) { this.assignment = assignment; this.toAgent = toAgent; }

    @Override public boolean isMoveDoable(ScoreDirector<Schedule> sd) {
        return !java.util.Objects.equals(assignment.getAgent(), toAgent);
    }
    @Override protected void doMoveOnGenuineVariables(ScoreDirector<Schedule> scoreDirector) {
        scoreDirector.beforeVariableChanged(assignment, "agent");
        assignment.setAgent(toAgent);
        scoreDirector.afterVariableChanged(assignment, "agent");
    }
    @Override public Collection<?> getPlanningEntities() { return List.of(assignment); }
    @Override public Collection<?> getPlanningValues() { return List.of(toAgent); }
    @Override public AssignSeatMove rebase(ScoreDirector<Schedule> destinationScoreDirector) {
        return new AssignSeatMove(destinationScoreDirector.lookUpWorkingObject(assignment),
                destinationScoreDirector.lookUpWorkingObject(toAgent));
    }
    @Override public boolean equals(Object o) { ... } // needed if any Tabu acceptor is ever configured
    @Override public int hashCode() { ... }
}
```
The literal string `"agent"` passed to `beforeVariableChanged`/`afterVariableChanged` must match
the field name of the `@PlanningVariable` annotation on `AgentAssignment` — confirmed as `agent`
at `src/main/java/com/wfm/model/AgentAssignment.java:40` (`@PlanningVariable(...) private Agent
agent;`). Then `CompositeMove.buildMove(listOfAssignSeatMoves)` produces the atomic shift move.
This entirely sidesteps the internal `GenuineVariableDescriptor` problem and needs zero
Timefold-internal API access. `[VERIFIED: src/main/java/com/wfm/model/AgentAssignment.java:37-40]`

### 3. `MoveListFactory` vs `MoveIteratorFactory`

Both interfaces read directly from source:
[VERIFIED: github.com/TimefoldAI/timefold-solver/blob/v1.16.0/core/src/main/java/ai/timefold/solver/core/impl/heuristic/selector/move/factory/MoveListFactory.java]
```java
public interface MoveListFactory<Solution_> {
    List<? extends Move<Solution_>> createMoveList(Solution_ solution);
}
```
[VERIFIED: github.com/TimefoldAI/timefold-solver/blob/v1.16.0/core/src/main/java/ai/timefold/solver/core/impl/heuristic/selector/move/factory/MoveIteratorFactory.java]
```java
public interface MoveIteratorFactory<Solution_, Move_ extends Move<Solution_>> {
    long getSize(ScoreDirector<Solution_> scoreDirector);
    Iterator<Move_> createOriginalMoveIterator(ScoreDirector<Solution_> scoreDirector);
    Iterator<Move_> createRandomMoveIterator(ScoreDirector<Solution_> scoreDirector, Random workingRandom);
}
```
**Recommendation: start with `MoveListFactory`.** The candidate space is bounded — one candidate
shift-completion move per (under-hours agent × legal window start × legal break position) per
solve step, not per (agent × seat) — this is small relative to the schedule size (order of
"agents needing more hours" × "a handful of legal window starts per agent-day"), not the
combinatorial product of all seats. `MoveIteratorFactory` (just-in-time generation) is the
correct escalation path only if profiling later shows `createMoveList` is too expensive to call
every local-search step — do not pre-optimize into the iterator form without evidence.

### 4. Wiring into solver config — the union-selector pitfall (high-value finding)

[VERIFIED: github.com/TimefoldAI/timefold-solver/blob/v1.16.0/core/src/main/java/ai/timefold/solver/core/impl/localsearch/DefaultLocalSearchPhaseFactory.java, lines ~180-250]
```java
protected MoveSelector<Solution_> buildMoveSelector(HeuristicConfigPolicy<Solution_> configPolicy) {
    var moveSelectorConfig = phaseConfig.getMoveSelectorConfig();
    if (moveSelectorConfig == null) {
        moveSelector = new UnionMoveSelectorFactory<>(determineDefaultMoveSelectorConfig(configPolicy))
                .buildMoveSelector(...);
    } else {
        // uses ONLY what you declared — no merging with the default
        moveSelector = MoveSelectorFactory.create(moveSelectorConfig).buildMoveSelector(...);
    }
}

private UnionMoveSelectorConfig determineDefaultMoveSelectorConfig(...) {
    ... // for AgentAssignment's case: one non-chained basic variable, no list variable
    return new UnionMoveSelectorConfig()
            .withMoveSelectors(new ChangeMoveSelectorConfig(), new SwapMoveSelectorConfig());
}
```
This is the exact mechanism behind today's working (if incomplete) behavior: because
`solverConfig.xml` declares **no** `moveSelector` at all under `<localSearch>`, Timefold silently
builds `unionMoveSelector(changeMoveSelector, swapMoveSelector)` for you — this is what "fine
grained repair" currently *is*, invisibly. **The moment this phase adds any explicit
`moveSelector` (required to register the new move factory), that auto-configuration path is
bypassed entirely and only what is explicitly declared survives.** Concretely, the new
`solverConfig.xml` `<localSearch>` block must look like:
```xml
<localSearch>
    <unionMoveSelector>
        <changeMoveSelector/>
        <swapMoveSelector/>
        <moveListFactory>
            <moveListFactoryClass>com.wfm.solver.AtomicShiftMoveFactory</moveListFactoryClass>
        </moveListFactory>
    </unionMoveSelector>
    <acceptor>
        <simulatedAnnealingStartingTemperature>0hard/3000soft</simulatedAnnealingStartingTemperature>
    </acceptor>
</localSearch>
```
Omitting `<changeMoveSelector/>`/`<swapMoveSelector/>` here would silently delete today's
fine-grained repair capability — directly the "must compose with... not replace them" constraint
from the roadmap, now root-caused to a specific config mechanism rather than a vague warning.
`unionMoveSelector`'s XML schema was also confirmed to accept `moveListFactory`/
`moveIteratorFactory` as siblings of `changeMoveSelector`/`swapMoveSelector`:
[VERIFIED: github.com/TimefoldAI/timefold-solver/blob/v1.16.0/core/src/main/java/ai/timefold/solver/core/config/heuristic/selector/move/composite/UnionMoveSelectorConfig.java]
(`@XmlElements` list includes both `MoveListFactoryConfig.XML_ELEMENT_NAME` = `"moveListFactory"`
and `MoveIteratorFactoryConfig.XML_ELEMENT_NAME`).

**Selection weight/probability control**, requested by the research priorities, is the
`fixedProbabilityWeight` element inherited by every `MoveSelectorConfig` subclass (including
`MoveListFactoryConfig`), settable per selector inside the `unionMoveSelector`:
[VERIFIED: github.com/TimefoldAI/timefold-solver/blob/v1.16.0/core/src/main/java/ai/timefold/solver/core/config/heuristic/selector/move/MoveSelectorConfig.java, `fixedProbabilityWeight` field + `withFixedProbabilityWeight` builder method]
```xml
<moveListFactory>
    <moveListFactoryClass>com.wfm.solver.AtomicShiftMoveFactory</moveListFactoryClass>
    <fixedProbabilityWeight>1.0</fixedProbabilityWeight>
</moveListFactory>
```
Start at parity (`1.0`, same default weight as the unweighted change/swap selectors) and tune
empirically per the Validation Architecture benchmark below — do not guess a weight without
measuring.

### 5. `EnvironmentMode` — verified enum values for this version

[VERIFIED: github.com/TimefoldAI/timefold-solver/blob/v1.16.0/core/src/main/java/ai/timefold/solver/core/config/solver/EnvironmentMode.java]
```java
public enum EnvironmentMode {
    TRACKED_FULL_ASSERT(true),   // slowest; tracks genuine+shadow vars, reports exactly what corrupted
    FULL_ASSERT(true),           // turns on all assertions; "horribly slow"
    NON_INTRUSIVE_FULL_ASSERT(true),
    FAST_ASSERT(true),           // "reasonable" cost in development
    REPRODUCIBLE(false),         // DEFAULT — same code path + same result on repeat runs, EXCEPT
                                  // when a time-based termination has a large CPU-time difference run to run
    NON_REPRODUCIBLE(false);
}
```
This exact enum matches (not `STEP_ASSERT`/`NON_REPRODUCIBLE` as loosely guessed in the research
priorities — the real names are `TRACKED_FULL_ASSERT`/`FULL_ASSERT`/`NON_INTRUSIVE_FULL_ASSERT`/
`FAST_ASSERT`/`REPRODUCIBLE`/`NON_REPRODUCIBLE`).
- **`FULL_ASSERT`** (already used once in this codebase, see Codebase Grounding above) is the
  right mode to catch "incorrect undo produces corrupted-score bugs" — it re-derives the score
  from scratch after every move and compares to the incrementally-calculated score, failing loud
  on mismatch. Use it in a dedicated JUnit test that runs the new move through a short local
  search and asserts no exception.
- **`TRACKED_FULL_ASSERT`** is strictly more diagnostic (reports exactly which variable
  corrupted) at higher cost — reserve for local debugging when `FULL_ASSERT` fails and the cause
  isn't obvious from the exception alone; too slow for routine CI.
- **The javadoc directly explains the observed run-to-run variance**: *"In this mode [REPRODUCIBLE,
  the default], 2 runs on the same computer will execute the same code in the same order. They
  will also yield the same result, except if they use a time based termination and they have a
  sufficiently large difference in allocated CPU time."* `SolverService.java` uses exactly that —
  `TerminationConfig().withSpentLimit(Duration)` — so the -29,810/-29,810/-4,930 variance
  observed on 2026-08-13 is consistent with normal `REPRODUCIBLE`-mode behavior under wall-clock
  termination, not a bug. This is the direct explanation for why "run-to-run variance currently
  exceeds the effect size of most changes," and it dictates the Validation Architecture design
  below (control for CPU time, not just wall-clock).

## Architecture Patterns

### System flow: where the new move sits

```
SolverService.startSolve()
   |
   builds Schedule (agents, timeslots, staffing reqs, AgentDayConfig facts, AgentAssignment seats)
   |
   v
SolverManager.solve(schedule)
   |
   +--> Construction Heuristic (unchanged) --------> builds initial feasible-ish solution,
   |                                                  one AgentAssignment.agent at a time
   v
   Local Search (SimulatedAnnealing acceptor)
       |
       v
   unionMoveSelector  <-- THIS PHASE ADDS A THIRD BRANCH
       |-- changeMoveSelector   (existing, single-seat repair)
       |-- swapMoveSelector     (existing, single-seat repair)
       |-- moveListFactory: AtomicShiftMoveFactory   (NEW)
       |        |
       |        +--> reads Schedule.agents, Schedule.assignments, Schedule.agentDayConfigs
       |        +--> for each agent-day under contracted hours:
       |              scan AgentAssignment seats for that agent's date, spec-matching,
       |              currently agent==null, for a legal contiguous window
       |              (work-slots + correctly positioned/aligned/unblocked break gap)
       |        +--> builds CompositeMove.buildMove(N x AssignSeatMove) per legal window found
       v
   ScheduleConstraintProvider scores the candidate move (all 18 constraints, unchanged)
       |
       v
   SimulatedAnnealing acceptor decides accept/reject
       |
       v
   best solution tracked -> SolverService.withBestSolutionConsumer -> InMemoryScheduleStore
```

### Recommended file layout

```
src/main/java/com/wfm/solver/
├── ScheduleConstraintProvider.java      # unchanged
├── AgentAssignmentDifficultyComparator.java  # unchanged
├── AtomicShiftMoveFactory.java          # NEW — implements MoveListFactory<Schedule>
├── AssignSeatMove.java                  # NEW — implements AbstractMove<Schedule>
└── ShiftWindowFinder.java               # NEW (optional extraction) — pure function:
                                          #   (agentId, date, free seats, AgentDayConfig) ->
                                          #   List<List<AgentAssignment>> legal candidate windows
                                          #   shared/testable independent of Timefold API
```
Extracting the window-finding logic into a plain, Timefold-independent class
(`ShiftWindowFinder`) mirrors the existing pattern where `getGapLengths`/`findBreakStart`/
`isAligned` in `ScheduleConstraintProvider` are private pure functions — makes the core geometry
logic unit-testable without spinning up a solver, and testable for parity against the
constraint's own gap-detection so the two never disagree.

### Anti-Patterns to Avoid

- **Resurrecting the `BreakAwareConstructionPhase` multi-pass pipeline approach.** Already tried
  in this exact codebase and abandoned — no backtracking, cascading repair failures. Use
  Timefold's own search/acceptor machinery via a real `Move`, not a manual pre-processing pass.
- **Constructing Timefold's internal `ChangeMove` directly.** Its constructor requires a
  `GenuineVariableDescriptor`, an internal type not meant for external instantiation. Write a
  minimal custom `Move` instead (see `AssignSeatMove` above).
- **Declaring a `moveListFactory` without also declaring `changeMoveSelector`/
  `swapMoveSelector`** inside the same `unionMoveSelector`. Silently deletes existing
  fine-grained repair — verified as the actual `DefaultLocalSearchPhaseFactory` behavior, not a
  guess.
- **Relying on `CompositeMove.isMoveDoable()` for all-or-nothing semantics.** It is an OR across
  submoves, and `doMoveOnGenuineVariables` silently skips non-doable submoves rather than
  failing. Pre-validate full-window legality at candidate-generation time instead.
- **Benchmarking with wall-clock-only termination and treating a single run as signal.** The
  `EnvironmentMode.REPRODUCIBLE` javadoc explicitly documents that time-based termination breaks
  run-to-run comparability even in the default reproducible mode. See Validation Architecture.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Reversible multi-step assignment change | A manual "snapshot state, apply, compare, rollback" wrapper | `AbstractMove.doMoveOnGenuineVariables` + Timefold's auto-generated undo (1.16.0+) | Timefold already solved this; a hand-rolled undo is exactly the historically bug-prone pattern the framework removed in 1.16.0 |
| Composing several single-seat changes into one atomic unit | A custom "batch move" wrapper class with its own undo bookkeeping | `CompositeMove.buildMove(List<Move>)` | Public, stable, already handles ordering + shadow-variable listener triggering between submoves |
| Detecting a valid contiguous free-seat window with a legal break | A greedy pass that mutates the working solution while scanning | A pure function reading `Schedule.assignments`/`agentDayConfigs` without mutation, mirroring the constraint provider's own `getGapLengths`/`findBreakStart`/`isAligned` | The constraint provider already has to solve this exact geometry problem for scoring; duplicating divergent logic risks a move that looks legal to the factory but is scored illegal by the constraint (or vice versa) |
| Verifying a custom move doesn't corrupt incremental scoring | Manual score-diffing before/after in production code | `EnvironmentMode.FULL_ASSERT` in a dedicated test (pattern already exists: `IncrementalScoringDiagnosticTest.java:139`) | Purpose-built assertion machinery in the framework; already an established pattern in this codebase |

**Key insight:** every piece of this phase has either (a) a public, documented Timefold 1.16.0
API that does it correctly, or (b) an existing pattern already in this codebase to imitate. There
is no part of this problem that legitimately needs new hand-rolled infrastructure — the prior
attempt at hand-rolling it (`BreakAwareConstructionPhase`) is the cautionary tale, not a
component to build on.

## Common Pitfalls

### Pitfall 1: Declaring the custom move factory without re-declaring change/swap selectors
**What goes wrong:** Fine-grained single-seat repair silently disappears; only the new
coarse-grained move remains, which cannot fix small local infeasibilities (e.g., a single
misassigned specialization).
**Why it happens:** Timefold's default-move auto-configuration (`ChangeMove`+`SwapMove`) only
activates when `<localSearch>` has **no** `moveSelector` at all; the instant an explicit selector
of any kind is present, the default path is bypassed entirely.
**How to avoid:** Always wrap the new factory inside an explicit `<unionMoveSelector>` that also
lists `<changeMoveSelector/>` and `<swapMoveSelector/>`.
**Warning signs:** Local search converges to a *worse* hard score than before this phase, or gets
stuck unable to fix small violations that the old default move pool used to clean up easily.

### Pitfall 2: `CompositeMove`'s OR-doability and skip-on-execute semantics
**What goes wrong:** A composite move that was intended as "place all 9 slots of a shift" ends up
placing only 6 of them if 3 seats became non-doable between candidate generation and execution
(e.g., another thread/step already filled them), producing a partial, likely illegal shift that
then gets penalized — wasted search effort, not a crash.
**Why it happens:** `isMoveDoable` is an OR across submoves and `doMoveOnGenuineVariables` skips
non-doable submoves silently rather than aborting.
**How to avoid:** Re-validate full-window seat availability at the moment the move is generated
(same solve step, same `ScoreDirector`/`Schedule` state, no interleaving), and keep move
generation cheap enough that this validation is not stale by execution time — Timefold single-
threaded local search (no `moveThreadCount` configured today) means the state doesn't change
between candidate generation in `createMoveList` and evaluation within a single step, but be
aware this assumption breaks if multithreaded solving is enabled later.
**Warning signs:** Constraint violations specifically on partially-filled shifts after enabling
the new move; a mismatch between "seats found by factory" and "seats actually filled" in a
diagnostic log.

### Pitfall 3: Move-generation geometry disagreeing with constraint-provider geometry
**What goes wrong:** The move factory computes a "legal" break position that the constraint
provider's own `isAligned`/`breakBlockedWindow` logic then penalizes anyway, because the two
implementations of "is this break legal" drift apart over time (e.g., one uses
`RoundingMode.CEILING` for slot-count division, the other `HALF_UP`).
**Why it happens:** `expectedWorkSlots` (line 515-520) uses `RoundingMode.HALF_UP`; the break-
threshold calc inside `exactlyOneBreak` (lines 162-165) uses `RoundingMode.CEILING`. These are
two different, easy-to-miss rounding modes already coexisting in the current constraint provider.
**How to avoid:** Reuse the exact same rounding-mode-sensitive calculations (or extract them into
shared, tested utility methods called from both the constraint provider and the new move
factory) rather than re-deriving equivalent-looking arithmetic independently.
**Warning signs:** Off-by-one-slot mismatches specifically at fractional-hour contracted-hours
boundaries.

### Pitfall 4: Move explosion from generating candidates for every agent regardless of need
**What goes wrong:** `createMoveList` is called every local-search step; if it scans all agents
(not just under-hours ones) or all possible window starts (not just legal ones), the candidate
list becomes large enough to dominate step time, degrading overall solver throughput —
documented externally as a known Timefold failure mode (small/easy problem instances sometimes
generate *more* moves per step than harder ones, because a high fraction of naively-generated
candidates are filtered out as invalid, forcing the selector to over-generate to satisfy its
accepted-move-count budget).
[CITED: github.com/TimefoldAI/timefold-solver/discussions/917 — maintainer response: "with
smaller problems, many generated moves may be invalid... forcing the solver to generate
substantially more moves within each step"]
**Why it happens:** Naive candidate generation that doesn't filter by "agent actually needs more
hours today" before doing the expensive contiguous-window search.
**How to avoid:** Filter to `AgentDayConfig`s where current assigned-slot count for that
agent-date is strictly less than `expectedWorkSlots(dayConfig)` **before** doing any window
search; skip agents already at or above their contracted hours entirely.
**Warning signs:** Solver throughput (steps/second, visible via Timefold's own step logging or a
custom `PhaseLifecycleListener`) drops sharply after the new move is enabled, even before any
score-quality change is visible.

### Pitfall 5: Trusting a single benchmark run
**What goes wrong:** A 5-minute run showing improvement (or regression) is treated as conclusive,
when the phase's own evidence (`-4,930`/`80.50h` vs `-29,810`/`-29,810` across 3 runs of
identical config) shows the *current* system's run-to-run variance already exceeds plausible
per-change effect sizes.
**Why it happens:** `EnvironmentMode.REPRODUCIBLE` (the default, unchanged by this phase) is only
reproducible under non-time-based termination; `SolverService` uses wall-clock
(`Duration`)-based `TerminationConfig`, so CPU scheduling noise on the host machine changes how
far the solver gets each run.
**How to avoid:** See Validation Architecture below — use step-count or unimproved-step-count
termination for controlled A/B comparisons in dev/CI, reserving wall-clock termination for
production only.
**Warning signs:** Any single "before/after" comparison presented without multiple repeated runs
per configuration.

## Code Examples

### Custom reversible move (verified pattern, 1.16.0 API)
```java
// Source: derived directly from ai.timefold.solver.core.api.score.director.ScoreDirector
// and ai.timefold.solver.core.impl.heuristic.move.AbstractMove, both read at git tag v1.16.0
package com.wfm.solver;

import ai.timefold.solver.core.api.score.director.ScoreDirector;
import ai.timefold.solver.core.impl.heuristic.move.AbstractMove;
import com.wfm.model.Agent;
import com.wfm.model.AgentAssignment;
import com.wfm.model.Schedule;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

final class AssignSeatMove extends AbstractMove<Schedule> {
    private final AgentAssignment assignment;
    private final Agent toAgent;

    AssignSeatMove(AgentAssignment assignment, Agent toAgent) {
        this.assignment = assignment;
        this.toAgent = toAgent;
    }

    @Override
    public boolean isMoveDoable(ScoreDirector<Schedule> scoreDirector) {
        return !Objects.equals(assignment.getAgent(), toAgent);
    }

    @Override
    protected void doMoveOnGenuineVariables(ScoreDirector<Schedule> scoreDirector) {
        scoreDirector.beforeVariableChanged(assignment, "agent");
        assignment.setAgent(toAgent);
        scoreDirector.afterVariableChanged(assignment, "agent");
    }

    @Override
    public AssignSeatMove rebase(ScoreDirector<Schedule> destinationScoreDirector) {
        return new AssignSeatMove(
                destinationScoreDirector.lookUpWorkingObject(assignment),
                destinationScoreDirector.lookUpWorkingObject(toAgent));
    }

    @Override
    public Collection<?> getPlanningEntities() { return List.of(assignment); }

    @Override
    public Collection<?> getPlanningValues() { return List.of(toAgent); }

    @Override
    public boolean equals(Object o) {
        return o instanceof AssignSeatMove other
                && Objects.equals(assignment, other.assignment)
                && Objects.equals(toAgent, other.toAgent);
    }

    @Override
    public int hashCode() { return Objects.hash(assignment, toAgent); }

    @Override
    public String toString() { return assignment + " -> " + toAgent; }
}
```

### Composing into the atomic shift move
```java
// Source: ai.timefold.solver.core.impl.heuristic.move.CompositeMove, read at git tag v1.16.0
import ai.timefold.solver.core.impl.heuristic.move.CompositeMove;
import ai.timefold.solver.core.impl.heuristic.move.Move;

List<AssignSeatMove> seatMoves = legalWindowSeats.stream()
        .map(seat -> new AssignSeatMove(seat, agent))
        .toList();
Move<Schedule> shiftMove = CompositeMove.buildMove(seatMoves);
```

### Solver config wiring
```xml
<!-- Source: ai.timefold.solver.core.config.heuristic.selector.move.composite.UnionMoveSelectorConfig
     and ai.timefold.solver.core.config.heuristic.selector.move.factory.MoveListFactoryConfig,
     both read at git tag v1.16.0 -->
<localSearch>
    <unionMoveSelector>
        <changeMoveSelector/>
        <swapMoveSelector/>
        <moveListFactory>
            <moveListFactoryClass>com.wfm.solver.AtomicShiftMoveFactory</moveListFactoryClass>
            <fixedProbabilityWeight>1.0</fixedProbabilityWeight>
        </moveListFactory>
    </unionMoveSelector>
    <acceptor>
        <simulatedAnnealingStartingTemperature>0hard/3000soft</simulatedAnnealingStartingTemperature>
    </acceptor>
</localSearch>
```

### FULL_ASSERT correctness test (established pattern in this codebase)
```java
// Existing pattern, source: src/test/java/com/wfm/solver/IncrementalScoringDiagnosticTest.java:131-149
SolverConfig solverConfig = new SolverConfig()
        .withSolutionClass(Schedule.class)
        .withEntityClasses(AgentAssignment.class)
        .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)   // fails fast on corrupted score / bad undo
        .withScoreDirectorFactory(new ScoreDirectorFactoryConfig()
                .withConstraintProviderClass(ScheduleConstraintProvider.class))
        .withPhases(
                new ConstructionHeuristicPhaseConfig(),
                new LocalSearchPhaseConfig()
                        .withMoveSelectorConfig(new UnionMoveSelectorConfig().withMoveSelectors(
                                new ChangeMoveSelectorConfig(),
                                new SwapMoveSelectorConfig(),
                                new MoveListFactoryConfig().withMoveListFactoryClass(AtomicShiftMoveFactory.class)))
                        .withTerminationConfig(new TerminationConfig().withSpentLimit(Duration.ofSeconds(5))));
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|---------------|--------|
| Hand-written `createUndoMove` per custom `Move` | Framework auto-generates the undo via `VariableChangeRecordingScoreDirector`; `createUndoMove` throws if called | 1.16.0 (this project's pinned version) | Removes an entire historically bug-prone class of hand-written undo bugs — directly addresses the roadmap's "incorrect undo produces corrupted-score bugs" concern at the framework level |
| Multi-pass hand-rolled pre-assignment pipeline (`BreakAwareConstructionPhase`'s predecessor) | All seat allocation delegated to Timefold's construction heuristic + local search moves | Already changed in this codebase before this phase (see `BreakAwareConstructionPhase.java` javadoc) | Confirms the project's own prior conclusion that hand-rolled multi-pass repair does not scale; this phase continues that trajectory rather than reversing it |
| `Move.doMove(ScoreDirector)` returning an undo move | `Move.doMoveOnly(ScoreDirector)`, no return value | Deprecated-for-removal since 1.16.0 | Any code copied from pre-1.16.0 Timefold/OptaPlanner examples (common in blog posts, Stack Overflow, and the OptaPlanner-era docs) using `doMove`/`createUndoMove` will compile (deprecated, not removed) but represents a stale pattern signal, not something to imitate |

**Deprecated/outdated (do not follow tutorials describing these for this project's version):**
- `Move.doMove(ScoreDirector)` / `AbstractMove.createUndoMove(ScoreDirector)` — both
  `@Deprecated(forRemoval = true, since = "1.16.0")`; present for backward source compatibility
  only.
- The `Neighborhoods`/newer custom-move API referenced in current `docs.timefold.ai/latest`
  pages (e.g. "Neighborhoods: A new way to define custom moves") was introduced at **1.31.0** —
  this project is pinned to **1.16.0**, 15 minor versions earlier. That newer API was **not**
  verified against 1.16.0's actual class tree and should not be assumed available; the
  `AbstractMove`/`CompositeMove`/`MoveListFactory` path documented above is what exists at
  1.16.0 and is what this research verified directly against source.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|----------------|
| A1 | The `timefold-solver-benchmark` module (same BOM, version 1.16.0) is a drop-in dependency requiring no additional legitimacy check beyond the already-approved `timefold-solver-bom` | Package Legitimacy Audit / Validation Architecture | Low — it is optional tooling for the benchmark harness, not required for the core move implementation; if unavailable, the lightweight hand-rolled harness (already the primary recommendation) suffices |
| A2 | No multithreaded solving (`moveThreadCount`) is configured anywhere in this project today | Timefold API section, Pitfall 2 | Low-medium — confirmed by absence of the setting in `solverConfig.xml`/`SolverService.java`, but if a future change enables it, the "state doesn't change between candidate generation and execution" assumption in the move factory would need re-verification |
| A3 | `.planning/STATE.md`'s "Timefold pinned at 1.33.0" note is stale/incorrect relative to `build.gradle`'s actual `1.16.0` | Codebase Grounding | Medium — if 1.33.0 is actually intended and `build.gradle` should be bumped as prerequisite work, all API verification in this document (done against 1.16.0 source) would need re-doing against 1.33.0, which has the newer `Neighborhoods` custom-move API that was explicitly not verified here |

## Open Questions

1. **Should the move factory search only "overflow-eligible" seats, or also seats currently
   filled by a different agent (requiring a displacement, not just a fill)?**
   - What we know: `expandOverflowAssignments` (`SolverService.java:903-927`) creates unfilled
     seats specifically so agents can reach exact contracted hours without displacing anyone;
     the simplest, lowest-risk version of this move only ever targets `agent == null` seats.
   - What's unclear: whether overflow seat *capacity* (bounded by
     `overallocationHardLimitPct`, default 130%) is always sufficient headroom for every
     under-hours agent to complete a shift purely via empty seats, or whether some scenarios
     require displacing a different agent's overflow assignment to free the needed contiguous
     window.
   - Recommendation: implement the "fill empty seats only" version first (matches the roadmap's
     literal "find free, spec-matching seats" wording) and measure against the benchmark
     scenario in Validation Architecture; only add displacement logic if empty-seat-only search
     measurably fails to close the gap on the reference desk data.

2. **Does the desk's overallocation percentage (measured at 400% in the roadmap's own
   evidence) always provide enough overflow seat headroom, or was 400% specifically chosen to
   guarantee headroom for this diagnostic measurement and not representative of real desk
   configs?**
   - What we know: the roadmap's reproduction used "400% over-allocation" as a deliberately
     generous setting; production desks are not confirmed to use the same percentage.
   - What's unclear: whether the new move's benefit is contingent on high overallocation
     percentages that aren't realistic in production, in which case the fix might look effective
     in the benchmark but not transfer.
   - Recommendation: the benchmark harness (see below) should include at least one run at a
     more conservative overallocation percentage (e.g. 130%, the `Schedule` class's own default,
     `Schedule.java:71-72`) in addition to reproducing the 400% scenario from the roadmap.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|-------------|-----------|---------|----------|
| Java (JDK) | Compile/run the solver | Yes | OpenJDK 21.0.12 (matches `build.gradle` `languageVersion = JavaLanguageVersion.of(21)`) | — |
| Gradle wrapper | Build/test | Yes | Gradle 8.12 (via `./gradlew -v`) | — |
| `ai.timefold.solver:timefold-solver-*` | Core dependency, this entire phase | Yes | 1.16.0 (declared in `build.gradle:35-37`, see version discrepancy note above) | — |
| `ai.timefold.solver:timefold-solver-benchmark` | Optional richer benchmark harness | Not currently a declared dependency | 1.16.0 available on the same BOM if added | Use the lightweight hand-rolled harness pattern already established in `BreakAwareConstructionTest.java`/`FullScale150AgentTest.java` (primary recommendation; no new dependency required) |
| `gh` CLI (GitHub) | Used only during this research to read pinned-version source | Yes, authenticated | — | N/A — not a runtime dependency of the phase itself |

**Missing dependencies with no fallback:** None.

**Missing dependencies with fallback:** `timefold-solver-benchmark` — recommended fallback is the
existing hand-rolled `SolverFactory` + `Duration`-based test pattern already used throughout
`src/test/java/com/wfm/solver/`, which requires zero new dependencies and matches established
project convention.

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 (Jupiter) + AssertJ, via `ai.timefold.solver:timefold-solver-test` (declared `testImplementation`, `build.gradle:44`) and standard Spring Boot test starter conventions already used across `src/test/java/com/wfm/solver/` |
| Config file | None — no `pytest.ini`/`jest.config`-equivalent; Gradle's built-in `test` task (JUnit Platform, implied by `timefold-solver-test`'s own JUnit 5 dependency and by every existing test in the module using `@Test`/`org.junit.jupiter.api.Test`) |
| Quick run command | `./gradlew test --tests "com.wfm.solver.*"` |
| Full suite command | `./gradlew test` |

### Phase Requirement → Test Map

No `REQUIREMENTS.md` requirement IDs are mapped to Phase 12 (`ROADMAP.md`: "**Requirements**:
TBD"; `REQUIREMENTS.md` has no `PHZ`-prefixed or otherwise Phase-12-tagged entries — confirmed
by reading the full file, which stops at `MDL-03`/Phase 9). The phase's own "why" section
supplies the effective acceptance criteria in its place; the table below maps to those.

| Behavior (from ROADMAP.md Phase 12 goal) | Test Type | Automated Command | File Exists? |
|---|---|---|---|
| A full contracted shift (work slots + 1 correctly positioned break) is reachable as a single atomic move | unit | `./gradlew test --tests com.wfm.solver.AtomicShiftMoveFactoryTest` | ❌ Wave 0 |
| The custom move never produces a state with corrupted incremental score (undo correctness) | integration (solver, `FULL_ASSERT`) | `./gradlew test --tests com.wfm.solver.AtomicShiftMoveFullAssertTest` | ❌ Wave 0 |
| The move composes with, not replaces, existing change/swap moves — fine-grained repair still works | integration (solver, reused fixture pattern) | `./gradlew test --tests com.wfm.solver.BreakAwareConstructionTest` (existing — must still pass unmodified after config change) | ✅ already exists |
| Illegal break placements (misaligned, inside blocked window) are never generated by the move factory | unit (pure function, no solver needed) | `./gradlew test --tests com.wfm.solver.ShiftWindowFinderTest` | ❌ Wave 0 |
| Agents previously pinned at 15/16 slots with no break can now reach full contracted hours with a break, measured across repeated runs | benchmark (manual + scripted, see below) | scripted harness (not a `./gradlew test` target — see Sampling Rate) | ❌ Wave 0 |

### Sampling Rate
- **Per task commit:** `./gradlew test --tests "com.wfm.solver.*"` (fast unit + short solver
  tests, mirrors existing `BreakAwareConstructionTest`/`IncrementalScoringDiagnosticTest` runtime,
  each under ~2 minutes)
- **Per wave merge:** `./gradlew test` (full suite)
- **Phase gate:** Full suite green, **plus** the repeated-run benchmark comparison below with a
  concrete pass/fail threshold — a single green test run is explicitly insufficient evidence for
  this phase per the roadmap's own stated concern about run-to-run variance.

### Wave 0 Gaps
- [ ] `src/test/java/com/wfm/solver/ShiftWindowFinderTest.java` — pure-function unit tests for
  the (agent-day, free seats, `AgentDayConfig`) → legal candidate windows logic, no solver
  required, fast
- [ ] `src/test/java/com/wfm/solver/AtomicShiftMoveFactoryTest.java` — `MoveListFactory`
  contract tests (produces the expected `CompositeMove`s given a fixture `Schedule`)
- [ ] `src/test/java/com/wfm/solver/AtomicShiftMoveFullAssertTest.java` — `EnvironmentMode.
  FULL_ASSERT` run through a short local search with the new move enabled, following the
  existing pattern at `IncrementalScoringDiagnosticTest.java:131-149`
- [ ] A repeatable benchmark harness script/class (see below) — does not exist yet in any form;
  every existing solver test runs once and asserts a single-run score/assignment-count
  threshold, which is exactly the pattern the roadmap's own evidence shows is unreliable for
  this phase

### Benchmark Harness Design (addresses "run-to-run variance exceeds effect size")

**Root cause of the variance, verified against source (see EnvironmentMode section above):**
`SolverService` terminates on wall-clock `Duration` (`TerminationConfig.withSpentLimit`), and
Timefold's own `EnvironmentMode.REPRODUCIBLE` javadoc states reproducibility across runs
explicitly does **not** hold under time-based termination when CPU time allocated differs run to
run. This single fact, not solver randomness per se, is the most likely primary driver of the
`-4,930`/`-29,810`/`-29,810` spread reported in the roadmap.

**Harness design:**
1. **Fixed dataset:** reuse (or extend) the existing fixture-builder pattern from
   `BreakAwareConstructionTest`/`FullScale150AgentTest` to construct a `Schedule` matching the
   roadmap's reproduction scenario (400% over-allocation, 15-min increments, 8h contracted,
   60-min break, 1h blocked window) as a checked-in, version-controlled fixture — not
   reconstructed ad hoc per run, so every comparison run starts from bit-identical input.
2. **Fixed termination for controlled comparison runs (dev/CI only, NOT production):** replace
   `Duration`-based `withSpentLimit` with `withUnimprovedStepCountLimit(...)` or a fixed
   `withStepCountLimit(...)` for this harness specifically. Per the GitHub maintainer discussion
   read during this research [CITED: github.com/TimefoldAI/timefold-solver/discussions/917 —
   "step count family of terminations was never intended for use in production"], step-count
   termination is explicitly a development/debugging tool, not a production setting — keep
   `SolverService`'s production wall-clock termination untouched; use step-count termination
   only inside the benchmark harness to get genuinely comparable runs.
3. **Fixed seed:** call `SolverConfig.withRandomSeed(<constant>)`
   [VERIFIED: github.com/TimefoldAI/timefold-solver/blob/v1.16.0/core/src/main/java/ai/timefold/solver/core/config/solver/SolverConfig.java,
   `randomSeed` field + `withRandomSeed(Long)` builder, confirmed present at 1.16.0] — not used
   anywhere in this codebase today (`grep` confirms zero matches), so this is a net-new but
   directly-supported practice to adopt for this harness specifically.
4. **N repeated runs per configuration:** given the roadmap's own data (2 of 3 runs converged
   identically at -29,810, 1 diverged to -4,930/80.5h), a **minimum of 5 runs per configuration**
   (baseline vs. with-new-move) is the smallest N that can distinguish "this config has one
   dominant attractor with occasional escape" from "this config's typical result changed."
   Report median and the full spread (min/max), not mean alone — the roadmap's own data is
   bimodal, not normally distributed, so a mean would be misleading.
5. **Metrics to record per run:**
   - Hard score, soft score (final)
   - Hours assigned vs. hours needed (as already tracked in the roadmap's evidence table)
   - Count of agent-days with zero breaks despite `effectiveHours > breakMinShiftHours`
     (directly reproduces the roadmap's "pinned at exactly 15 slots... no breaks anywhere"
     symptom as a concrete, automatable assertion)
   - Count of agent-days at *exactly* the pre-fix wall (`breakThresholdSlots - 1` assigned
     slots) — should trend toward zero with the fix
   - Move-type acceptance counts (Timefold exposes step-level statistics via
     `PhaseLifecycleListener`/solver event logging — reuse `SolutionManager.explain(...)`, the
     same API already used in `runPreSolveScoreDiagnostic`, `SolverService.java:944-957`, and in
     `BreakAwareConstructionTest.printConstraintBreakdown`, lines 149-170)
6. **Timefold Benchmarker:** available at 1.16.0 (`ai.timefold.solver:timefold-solver-benchmark`
   confirmed present in the module tree at the pinned tag — see Environment Availability) but
   **not currently a project dependency** and would require build.gradle changes + new XML/
   config plumbing beyond what any existing test uses. Given the existing, already-working
   hand-rolled `SolverFactory`-based test pattern in this codebase does everything the harness
   above needs (fixture construction, repeated invocation, score/assignment inspection) without
   a new dependency, **recommend the hand-rolled harness as primary**; treat the Benchmarker
   module as a nice-to-have escalation only if the hand-rolled harness proves insufficient for
   presenting results (e.g. if HTML/chart output becomes a real requirement).

### Concrete Pass/Fail Threshold for This Phase

Given the reference scenario (400% over-allocation, 8h contracted, 60-min break, 88 hours
needed):
- **Must-pass:** across 5 repeated benchmark runs with the new move enabled, the median hours
  assigned must exceed the median of 5 baseline (pre-fix) runs by a margin larger than the
  observed baseline spread (baseline spread ≈ 30.25–80.50h per the roadmap's 3 data points; the
  fix must not merely land inside that same noisy band).
- **Must-pass:** zero agent-days across all 5 post-fix runs show `effectiveHours >
  breakMinShiftHours` with zero breaks (directly closes the reported symptom).
- **Should-pass (not phase-blocking, track as follow-up if missed):** hard score in the post-fix
  runs should not regress below the *best* observed baseline hard score (`-4,930`) in more than
  1 of 5 runs — a controlled amount of exploration variance is expected and acceptable, but the
  fix should not make the *typical* case worse than today's best case.

## Security Domain

This phase changes only in-process solver search behavior. It adds no new HTTP endpoint, no new
user-supplied input parsing, no new persisted data, and no new authentication/authorization
surface — the `Schedule`/`AgentAssignment`/`Agent` inputs it operates on are already fully
validated and loaded by `SolverService.startSolve` (existing pre-solve validation,
`SolverService.java:594-836`) before the solver — including any custom moves — ever runs.

| ASVS Category | Applies | Standard Control |
|----------------|---------|-------------------|
| V2 Authentication | No | Unchanged — no new endpoint |
| V3 Session Management | No | Unchanged |
| V4 Access Control | No | Unchanged — `TenantContext`/tenant-scoped repositories already gate all data this phase touches, untouched by this phase |
| V5 Input Validation | No new surface | The move factory reads only already-validated, already-tenant-scoped in-memory `Schedule` state; no new external input path is introduced |
| V6 Cryptography | No | Not applicable |

### Known Threat Patterns for this stack

Not applicable — this is a solver-internal optimization change with no new external attack
surface. The one operationally-relevant risk is **not** a STRIDE-style security threat but a
resource/availability concern: a badly-sized move pool (Pitfall 4 above) could degrade solver
throughput enough to make the existing wall-clock `solver.time-limit` produce materially worse
schedules than before, which is a quality regression, not a security vulnerability. Track it via
the Validation Architecture benchmark, not a security control.

## Sources

### Primary (HIGH confidence — direct source read at the exact pinned version tag)
- `github.com/TimefoldAI/timefold-solver` at git tag `v1.16.0`, files read directly via `gh api`:
  - `core/src/main/java/ai/timefold/solver/core/impl/heuristic/move/AbstractMove.java`
  - `core/src/main/java/ai/timefold/solver/core/impl/heuristic/move/Move.java`
  - `core/src/main/java/ai/timefold/solver/core/impl/heuristic/move/CompositeMove.java`
  - `core/src/main/java/ai/timefold/solver/core/impl/heuristic/selector/move/generic/ChangeMove.java`
  - `core/src/main/java/ai/timefold/solver/core/impl/heuristic/selector/move/factory/MoveListFactory.java`
  - `core/src/main/java/ai/timefold/solver/core/impl/heuristic/selector/move/factory/MoveIteratorFactory.java`
  - `core/src/main/java/ai/timefold/solver/core/config/solver/EnvironmentMode.java`
  - `core/src/main/java/ai/timefold/solver/core/config/solver/SolverConfig.java`
  - `core/src/main/java/ai/timefold/solver/core/api/score/director/ScoreDirector.java`
  - `core/src/main/java/ai/timefold/solver/core/impl/localsearch/DefaultLocalSearchPhaseFactory.java`
  - `core/src/main/java/ai/timefold/solver/core/config/heuristic/selector/move/composite/UnionMoveSelectorConfig.java`
  - `core/src/main/java/ai/timefold/solver/core/config/heuristic/selector/move/factory/MoveListFactoryConfig.java`
  - `core/src/main/java/ai/timefold/solver/core/config/heuristic/selector/move/MoveSelectorConfig.java`
  - `benchmark/pom.xml` (existence/artifact-id check only)
- This repository (all read via `Read` tool this session):
  `src/main/java/com/wfm/model/AgentAssignment.java`,
  `src/main/java/com/wfm/model/Schedule.java`,
  `src/main/java/com/wfm/model/Timeslot.java`,
  `src/main/java/com/wfm/model/AgentDayConfig.java`,
  `src/main/java/com/wfm/model/ScheduleConfig.java`,
  `src/main/java/com/wfm/model/BreakAlignment.java`,
  `src/main/java/com/wfm/model/Agent.java`,
  `src/main/java/com/wfm/solver/ScheduleConstraintProvider.java`,
  `src/main/java/com/wfm/solver/AgentAssignmentDifficultyComparator.java`,
  `src/main/java/com/wfm/solver/BreakAwareConstructionPhase.java`,
  `src/main/java/com/wfm/service/SolverService.java`,
  `src/main/resources/solverConfig.xml`,
  `src/test/java/com/wfm/solver/BreakAwareConstructionTest.java`,
  `src/test/java/com/wfm/solver/IncrementalScoringDiagnosticTest.java` (partial, lines 100-160),
  `src/test/java/com/wfm/solver/FullScale150AgentTest.java` (partial, lines 1-90),
  `build.gradle`, `.planning/REQUIREMENTS.md`, `.planning/STATE.md`, `.planning/ROADMAP.md`,
  `.planning/config.json`
- Git history in this repository: `729ba03`, `76a715f` (both read via `git show`), `git log`
  survey of `src/main/java/com/wfm/solver/BreakAwareConstructionPhase.java`'s ancestry

### Secondary (MEDIUM confidence)
- `docs.timefold.ai/timefold-solver/latest/optimization-algorithms/move-selector-reference`
  (WebFetch) — confirms `MoveListFactory`/`MoveIteratorFactory` XML element names and general
  shape, but documents the **latest** (2.5.0-era) docs, not 1.16.0-specific; used only to
  cross-check, not as primary source — all concrete API claims above were independently
  confirmed against the pinned-version source instead.
- github.com/TimefoldAI/timefold-solver/discussions/917 and /597 (WebFetch) — community/
  maintainer discussion, not official docs, used for the move-explosion pitfall and
  `MoveIteratorFactory` usage pattern.

### Tertiary (LOW confidence, flagged for validation)
- General WebSearch results describing `AbstractMove`/`createUndoMove` in pre-1.16.0 or
  unspecified-version context (multiple OptaPlanner-era javadoc links surfaced in search) — not
  relied upon; superseded entirely by the pinned-version source read above. Included in the
  initial search sweep only to identify which pages to disregard as stale for this project's
  version.

## Metadata

**Confidence breakdown:**
- Standard stack / Timefold API: HIGH — every load-bearing API claim (undo model,
  `CompositeMove`, `MoveListFactory`/`MoveIteratorFactory`, `EnvironmentMode`, union-selector
  default-bypass behavior, `fixedProbabilityWeight`) was confirmed by reading the actual source
  at the exact `v1.16.0` git tag this project depends on, not general-version documentation.
- Architecture / codebase grounding: HIGH — every model/constraint/config claim cites a specific
  file and line range read directly in this session.
- Pitfalls: HIGH for pitfalls 1-3 and 5 (root-caused to verified source behavior); MEDIUM for
  pitfall 4 (move explosion) since that is corroborated by community discussion, not this
  project's own measured data yet.
- Validation architecture: MEDIUM — the variance root-cause explanation (time-based termination
  under `REPRODUCIBLE` mode) is HIGH confidence (direct source quote), but the specific
  recommended run-count (5) and thresholds are this researcher's reasoned interpretation of the
  roadmap's 3-data-point sample, not independently verified against a larger dataset — treat as
  a starting point the planner/executor should be willing to revise once more runs exist.

**Research date:** 2026-08-13
**Valid until:** 2026-09-12 (30 days — stable, version-pinned API; re-verify sooner if
`build.gradle`'s Timefold version changes, since the "State of the Art" section documents a
materially different newer API path introduced at 1.31.0 that was not verified here)
