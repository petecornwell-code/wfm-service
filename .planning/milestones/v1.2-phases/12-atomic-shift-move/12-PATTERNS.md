# Phase 12: Atomic Shift Move - Pattern Map

**Mapped:** 2026-08-13
**Files analyzed:** 7 (3 new main, 1 config change, 3 new tests)
**Analogs found:** 4 / 7 (role-match or partial); 3 net-new with no in-repo analog (Timefold API docs are the source instead)

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|--------------------|------|-----------|-----------------|----------------|
| `src/main/java/com/wfm/solver/AtomicShiftMoveFactory.java` | solver move factory (`MoveListFactory<Schedule>`) | event-driven (called every LS step, reads live solution) | `src/main/java/com/wfm/solver/BreakAwareConstructionPhase.java` (structural/package analog only — cautionary, not a pattern to copy) | weak / no true analog |
| `src/main/java/com/wfm/solver/AssignSeatMove.java` | solver `Move<Schedule>` (custom reversible move) | CRUD (single planning-variable write) | none in repo — first custom `Move` implementation in this codebase | no analog (Timefold API-verified in RESEARCH.md is authoritative source) |
| `src/main/java/com/wfm/solver/ShiftWindowFinder.java` | pure-function utility | transform (assignments+config → legal candidate windows) | `ScheduleConstraintProvider.getGapLengths`/`findBreakStart`/`isAligned`/`expectedWorkSlots` (private pure functions, same file, lines 515-608) | strong role-match (same computation family, different call site) |
| `src/main/resources/solverConfig.xml` | config | request-response (declarative solver wiring) | itself (existing file, being extended) | exact — extending in place |
| `src/test/java/com/wfm/solver/ShiftWindowFinderTest.java` | test (pure-function unit test) | transform | none directly (`ScheduleConstraintProvider` has no dedicated unit test for its private gap functions today) — closest available is the fixture style in `BreakAwareConstructionTest.java` | partial (fixture style only) |
| `src/test/java/com/wfm/solver/AtomicShiftMoveFactoryTest.java` | test (`MoveListFactory` contract test) | event-driven | `src/test/java/com/wfm/solver/BreakAwareConstructionTest.java` | strong role-match (fixture-construction + `SolverFactory` pattern) |
| `src/test/java/com/wfm/solver/AtomicShiftMoveFullAssertTest.java` | test (solver integration, `EnvironmentMode.FULL_ASSERT`) | event-driven | `src/test/java/com/wfm/solver/IncrementalScoringDiagnosticTest.java` (lines 131-159, `fullAssert_solverShouldAssignAgents`) | exact — same `EnvironmentMode.FULL_ASSERT` pattern, same test file family |

## Pattern Assignments

### `src/main/java/com/wfm/solver/AtomicShiftMoveFactory.java` (move factory, event-driven)

**No true in-repo analog.** `BreakAwareConstructionPhase.java` is the only file in this package that
touches the same problem space, but RESEARCH.md flags it explicitly as **dead, abandoned code —
do not imitate its approach** (a manual multi-pass pre-assignment pipeline, no Timefold `Move`
machinery, no backtracking). Its only reusable value is the **package placement and Javadoc style**:

**Package + logging convention** (`BreakAwareConstructionPhase.java:1-27`):
```java
package com.wfm.solver;

import com.wfm.model.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AtomicShiftMoveFactory implements MoveListFactory<Schedule> {

    private static final Logger log = LoggerFactory.getLogger(AtomicShiftMoveFactory.class);
    ...
}
```
Use `com.wfm.model.*` wildcard import (matches `ScheduleConstraintProvider.java:3` and
`BreakAwareConstructionPhase.java:3`) and an SLF4J logger field, both established conventions in
this package.

**Actual implementation contract** — copy directly from RESEARCH.md's verified Timefold 1.16.0
source reads (`## Code Examples`, "Custom reversible move" and "Composing into the atomic shift
move" sections), not from any file in this repo:
```java
public interface MoveListFactory<Solution_> {
    List<? extends Move<Solution_>> createMoveList(Solution_ solution);
}
```
Implementation must: read `Schedule.getAgents()`, `Schedule.getAssignments()`,
`Schedule.getAgentDayConfigs()` (all `@ProblemFactCollectionProperty`/`@PlanningEntityCollectionProperty`,
`Schedule.java:106-141`); filter to agent-days where assigned-slot count < `expectedWorkSlots`
(mirror `ScheduleConstraintProvider.expectedWorkSlots`, lines 515-520, `RoundingMode.HALF_UP` —
do not re-derive with a different rounding mode, see Pitfall 3 in RESEARCH.md); delegate window
discovery to `ShiftWindowFinder`; build one `CompositeMove.buildMove(List<AssignSeatMove>)` per
legal window found.

**Anti-pattern warning (from RESEARCH.md):** do not resurrect `BreakAwareConstructionPhase`'s
multi-pass mutation-while-scanning approach. The factory must be read-only against `Schedule`
state when generating candidates — only `AssignSeatMove.doMoveOnGenuineVariables` mutates.

---

### `src/main/java/com/wfm/solver/AssignSeatMove.java` (custom `Move<Schedule>`, CRUD)

**No analog exists in this codebase** — this is the first hand-written Timefold `Move`
implementation in the project (confirmed by RESEARCH.md: `grep -rln "MoveListFactory\|CustomPhaseCommand"`
returns no matches in `src/main`). The authoritative source is the Timefold 1.16.0 API itself, as
already verified and reproduced verbatim in RESEARCH.md `## Code Examples` → "Custom reversible
move." Key structural rules an executor must follow, extracted from that verified excerpt:

- Extend `AbstractMove<Schedule>`, implement only `doMoveOnGenuineVariables(ScoreDirector<Schedule>)`
  — never override `createUndoMove` (deprecated-for-removal since 1.16.0, throws if invoked).
- Bracket every variable write with `beforeVariableChanged`/`afterVariableChanged`, using the
  literal string `"agent"` (must match the `@PlanningVariable` field name on `AgentAssignment`,
  confirmed at `src/main/java/com/wfm/model/AgentAssignment.java:37-40`).
- Override `getPlanningEntities()`, `getPlanningValues()`, `rebase()`, `equals()`, `hashCode()`,
  `toString()` — all throw/are unimplemented by default in the base interface and must be
  supplied even though the current `simulatedAnnealingStartingTemperature` acceptor
  (`solverConfig.xml`) does not strictly require Tabu-Search-only methods today.

Field/domain types to use: `com.wfm.model.Agent`, `com.wfm.model.AgentAssignment`,
`com.wfm.model.Schedule` (all read at `src/main/java/com/wfm/model/*.java` this session).

---

### `src/main/java/com/wfm/solver/ShiftWindowFinder.java` (pure-function utility, transform)

**Analog:** `src/main/java/com/wfm/solver/ScheduleConstraintProvider.java` lines 510-608 (private
pure helper methods at the bottom of the constraint provider — same computational family: gap
detection, break-start discovery, alignment checking).

**Signature style to imitate** (lines 522-608):
```java
private int expectedWorkSlots(AgentDayConfig dayConfig) {
    return dayConfig.effectiveHours()
            .multiply(BigDecimal.valueOf(60))
            .divide(BigDecimal.valueOf(dayConfig.incrementMinutes()), 0, RoundingMode.HALF_UP)
            .intValue();
}

private List<Integer> getGapLengths(List<AgentAssignment> assignments, int incrementMinutes) {
    if (assignments == null || assignments.isEmpty()) return List.of();
    TreeSet<LocalTime> assignedStarts = new TreeSet<>();
    for (AgentAssignment a : assignments) {
        assignedStarts.add(a.getTimeslot().getStartTime());
    }
    ...
}

private boolean isAligned(LocalTime time, BreakAlignment alignment) {
    int minute = time.getMinute();
    return switch (alignment) {
        case ON_HOUR -> minute == 0;
        case ON_HALF_HOUR -> minute == 0 || minute == 30;
        case ON_QUARTER_HOUR -> minute % 15 == 0;
    };
}
```

**Reuse mandate (RESEARCH.md Pitfall 3, HIGH confidence):** `ShiftWindowFinder` must use the
*exact same* rounding mode as `expectedWorkSlots` (`RoundingMode.HALF_UP`) — note `exactlyOneBreak`'s
own threshold calc (constraint provider lines 200-203) uses `RoundingMode.CEILING` for a *different*
computation; do not conflate the two. Ideally extract these methods to a shared static utility
called from both `ScheduleConstraintProvider` and `ShiftWindowFinder` rather than copy-pasting, so
the two never drift. Static/package-private methods, no Timefold API imports (must stay a plain,
solver-independent class per RESEARCH.md's `## Recommended file layout`), taking `List<AgentAssignment>`,
`AgentDayConfig`, `BreakAlignment` (all `com.wfm.model` types already imported this way elsewhere
in the package).

---

### `src/main/resources/solverConfig.xml` (config, request-response)

**Analog:** itself — extend the existing 27-line file, do not create new.

**Current state** (full file, already read):
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
**Required change** (verified against Timefold 1.16.0 `DefaultLocalSearchPhaseFactory` /
`UnionMoveSelectorConfig` source — RESEARCH.md `## Architecture Patterns` §4):
```xml
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
**Critical pitfall (HIGH confidence, verified in framework source):** `<changeMoveSelector/>` and
`<swapMoveSelector/>` MUST be re-declared alongside the new `<moveListFactory>` inside the
`<unionMoveSelector>`. Declaring *any* explicit `moveSelector` bypasses Timefold's silent
default-union auto-configuration entirely — omitting the two existing selectors deletes today's
fine-grained single-seat repair capability.

---

### `src/test/java/com/wfm/solver/ShiftWindowFinderTest.java` (unit test, transform)

**Analog (partial — fixture-construction style only):** `src/test/java/com/wfm/solver/BreakAwareConstructionTest.java`

No dedicated unit test for the constraint provider's own gap/alignment helpers exists to copy
directly (they're private and only exercised indirectly through full-solver tests today) — this
is a genuinely new test shape for the codebase: a pure JUnit 5 test with no `SolverFactory`
involved at all, just direct calls into `ShiftWindowFinder`'s static methods against hand-built
`AgentAssignment`/`Timeslot`/`AgentDayConfig` fixtures.

**Fixture-building conventions to reuse from `BreakAwareConstructionTest.java`** (lines 34-45,
constants block):
```java
private static final long TENANT = 1L;
private static final LocalDate DAY = LocalDate.of(2026, 3, 16);
private static final LocalTime START = LocalTime.of(9, 0);
private static final LocalTime END = LocalTime.of(18, 0);
private static final int INCREMENT = 60;
private static final int BREAK_DURATION = 60;
private static final BigDecimal BREAK_BLOCKED = new BigDecimal("1.00");
private static final BigDecimal BREAK_MIN_SHIFT = new BigDecimal("4.00");
private static final BreakAlignment BREAK_ALIGNMENT = BreakAlignment.ON_HOUR;
private static final BigDecimal CONTRACTED_HOURS = new BigDecimal("8.00");
```
Use AssertJ (`assertThat`, already the project convention — `org.junit.jupiter.api.Test` +
`static org.assertj.core.api.Assertions.assertThat`) rather than plain JUnit assertions.

---

### `src/test/java/com/wfm/solver/AtomicShiftMoveFactoryTest.java` (integration test, event-driven)

**Analog:** `src/test/java/com/wfm/solver/BreakAwareConstructionTest.java` (573 lines, full pattern).

**Imports pattern** (lines 1-24):
```java
package com.wfm.solver;

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import ai.timefold.solver.core.config.localsearch.LocalSearchPhaseConfig;
import ai.timefold.solver.core.config.score.director.ScoreDirectorFactoryConfig;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;
import com.wfm.model.*;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
```

**Solver-runner pattern** (lines 131-147) — copy this shape, adding the new
`unionMoveSelector`/`moveListFactory` config from RESEARCH.md's "FULL_ASSERT correctness test"
code example when the test needs the new move enabled explicitly (rather than relying on
`solverConfig.xml`'s runtime-loaded config):
```java
private Schedule runSolver(Schedule schedule, Duration localSearchDuration) {
    SolverConfig solverConfig = new SolverConfig()
            .withSolutionClass(Schedule.class)
            .withEntityClasses(AgentAssignment.class)
            .withScoreDirectorFactory(new ScoreDirectorFactoryConfig()
                    .withConstraintProviderClass(ScheduleConstraintProvider.class))
            .withPhases(
                    new ConstructionHeuristicPhaseConfig(),
                    new LocalSearchPhaseConfig()
                            .withMoveSelectorConfig(new UnionMoveSelectorConfig().withMoveSelectors(
                                    new ChangeMoveSelectorConfig(),
                                    new SwapMoveSelectorConfig(),
                                    new MoveListFactoryConfig().withMoveListFactoryClass(AtomicShiftMoveFactory.class)))
                            .withTerminationConfig(new TerminationConfig()
                                    .withSpentLimit(localSearchDuration)
                                    .withUnimprovedSpentLimit(
                                            Duration.ofMillis(localSearchDuration.toMillis() / 2))));

    SolverFactory<Schedule> solverFactory = SolverFactory.create(solverConfig);
    return solverFactory.buildSolver().solve(schedule);
}
```

**Constraint-breakdown / `SolutionManager.explain` pattern** (lines 149-170) — reuse verbatim for
diagnostics when a candidate move produces an unexpected score:
```java
private void printConstraintBreakdown(Schedule solved) {
    SolverFactory<Schedule> scoringFactory = SolverFactory.create(
            new SolverConfig()
                    .withSolutionClass(Schedule.class)
                    .withEntityClasses(AgentAssignment.class)
                    .withScoreDirectorFactory(new ScoreDirectorFactoryConfig()
                            .withConstraintProviderClass(ScheduleConstraintProvider.class)));

    var solutionManager = ai.timefold.solver.core.api.solver.SolutionManager
            .<Schedule, HardSoftScore>create(scoringFactory);

    if (solved.getScore() != null && !solved.getScore().equals(HardSoftScore.ZERO)) {
        var explanation = solutionManager.explain(solved);
        System.out.println("=== Constraint Matches ===");
        explanation.getConstraintMatchTotalMap().forEach((name, total) -> {
            if (!total.getScore().equals(HardSoftScore.ZERO)) {
                System.out.println("  " + name + " => " + total.getScore()
                        + " (matches: " + total.getConstraintMatchCount() + ")");
            }
        });
    }
}
```

For a `MoveListFactory` contract-specific test (asserting `createMoveList` produces the expected
`CompositeMove`s given a fixture `Schedule`), instantiate `AtomicShiftMoveFactory` directly and
call `createMoveList(schedule)` without going through `SolverFactory` at all — no existing test in
this codebase does this yet (net-new pattern), but should still use the same fixture-construction
constants/helpers as `BreakAwareConstructionTest.java`.

---

### `src/test/java/com/wfm/solver/AtomicShiftMoveFullAssertTest.java` (integration test, `FULL_ASSERT`)

**Analog:** `src/test/java/com/wfm/solver/IncrementalScoringDiagnosticTest.java:131-159`
(`fullAssert_solverShouldAssignAgents`) — the only existing use of `EnvironmentMode` in the
codebase.

**Exact pattern to copy** (lines 131-159):
```java
@Test
void fullAssert_solverShouldAssignAgents() {
    Schedule schedule = buildUnassignedSchedule();

    // Run with FULL_ASSERT to catch incremental scoring bugs
    SolverConfig solverConfig = new SolverConfig()
            .withSolutionClass(Schedule.class)
            .withEntityClasses(AgentAssignment.class)
            .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
            .withScoreDirectorFactory(new ScoreDirectorFactoryConfig()
                    .withConstraintProviderClass(ScheduleConstraintProvider.class))
            .withPhases(
                    new ConstructionHeuristicPhaseConfig(),
                    new LocalSearchPhaseConfig()
                            .withTerminationConfig(new TerminationConfig()
                                    .withSpentLimit(Duration.ofSeconds(5))));

    SolverFactory<Schedule> solverFactory = SolverFactory.create(solverConfig);
    Schedule solved = solverFactory.buildSolver().solve(schedule);

    long assigned = solved.getAssignments().stream()
            .filter(a -> a.getAgent() != null).count();

    System.out.println("FULL_ASSERT solver: assigned=" + assigned + "/"
            + solved.getAssignments().size() + ", score=" + solved.getScore());

    assertThat(assigned)
            .as("FULL_ASSERT solver should assign agents")
            .isGreaterThan(0);
}
```
**Required addition for this phase:** add `.withMoveSelectorConfig(new UnionMoveSelectorConfig()...)`
(same shape as the `AtomicShiftMoveFactoryTest` runner above) to this `LocalSearchPhaseConfig` so
`FULL_ASSERT` specifically exercises the new `AtomicShiftMoveFactory`/`AssignSeatMove` path —
`IncrementalScoringDiagnosticTest`'s original version does not include the new move (it predates
this phase), so this is an extension of the pattern, not a verbatim copy. `FULL_ASSERT` re-derives
the score from scratch after every move and fails loudly on any incremental-score mismatch —
exactly what would surface a bug in `AssignSeatMove.doMoveOnGenuineVariables`'s
`beforeVariableChanged`/`afterVariableChanged` bracketing.

## Shared Patterns

### Package + import conventions
**Source:** every file in `src/main/java/com/wfm/solver/`
**Apply to:** `AtomicShiftMoveFactory.java`, `AssignSeatMove.java`, `ShiftWindowFinder.java`
```java
package com.wfm.solver;
import com.wfm.model.*;
```
Wildcard-import the model package; do not import individual model classes one by one (established
convention, not a rule enforced by tooling, but consistent across `ScheduleConstraintProvider.java`
and `BreakAwareConstructionPhase.java`).

### SLF4J logging
**Source:** `src/main/java/com/wfm/solver/BreakAwareConstructionPhase.java:5-6,27`
**Apply to:** `AtomicShiftMoveFactory.java` (log candidate-generation stats, e.g. count of
under-hours agents scanned, moves generated per step — useful for diagnosing Pitfall 4 in
RESEARCH.md, move-list explosion)
```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
private static final Logger log = LoggerFactory.getLogger(AtomicShiftMoveFactory.class);
```

### `SolverFactory` + `SolverConfig` builder chain for tests
**Source:** `src/test/java/com/wfm/solver/BreakAwareConstructionTest.java:131-147`,
`src/test/java/com/wfm/solver/IncrementalScoringDiagnosticTest.java:136-149`
**Apply to:** all three new test files
Consistent fluent builder: `new SolverConfig().withSolutionClass(...).withEntityClasses(...)
.withScoreDirectorFactory(...).withPhases(new ConstructionHeuristicPhaseConfig(), new
LocalSearchPhaseConfig()...)`. Do not hand-roll solver bootstrapping any other way.

### `SolutionManager.explain(...)` for constraint diagnostics
**Source:** `src/test/java/com/wfm/solver/BreakAwareConstructionTest.java:149-170`,
`src/main/java/com/wfm/service/SolverService.java` `runPreSolveScoreDiagnostic` (lines 929-996)
**Apply to:** `AtomicShiftMoveFactoryTest.java`, `AtomicShiftMoveFullAssertTest.java` — reuse for
any assertion that needs to explain *why* a score is non-zero, rather than hand-computing expected
constraint deltas.

### Rounding-mode consistency between constraint provider and move factory
**Source:** `src/main/java/com/wfm/solver/ScheduleConstraintProvider.java:515-520` (`RoundingMode.HALF_UP`
in `expectedWorkSlots`) vs. lines 200-203 (`RoundingMode.CEILING` in `exactlyOneBreak`'s own
threshold calc — a *different* computation, do not conflate)
**Apply to:** `ShiftWindowFinder.java`, `AtomicShiftMoveFactory.java` — any slot-count arithmetic
must match whichever of these two calculations it is mirroring, exactly, or generated "legal"
windows will be scored illegal by the constraint provider (RESEARCH.md Pitfall 3).

## No Analog Found

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| `src/main/java/com/wfm/solver/AssignSeatMove.java` | custom `Move<Schedule>` | CRUD | First hand-written Timefold `Move` in this codebase; no prior custom move exists to imitate. Use the Timefold 1.16.0-verified API pattern reproduced in RESEARCH.md `## Code Examples` instead — it was read directly from the pinned-version source (`AbstractMove.java`, `Move.java` at git tag `v1.16.0`), not guessed. |
| `src/main/java/com/wfm/solver/AtomicShiftMoveFactory.java` | `MoveListFactory<Schedule>` | event-driven | No `MoveListFactory`/`MoveIteratorFactory`/`unionMoveSelector` exists anywhere in `src/main` today (confirmed by `grep` in RESEARCH.md). `BreakAwareConstructionPhase.java` occupies similar conceptual territory but is explicitly flagged as abandoned, wrong-approach prior art — copy only its package/logging conventions, not its algorithm. |
| `src/test/java/com/wfm/solver/ShiftWindowFinderTest.java` (as a pure-function-only test with no solver bootstrap) | test | transform | No existing test in this codebase exercises a solver-independent pure function in isolation — every existing solver test spins up a full `SolverFactory`. Use plain JUnit 5 + AssertJ with hand-built fixtures, borrowing only the fixture-construction constants style from `BreakAwareConstructionTest.java`. |

## Metadata

**Analog search scope:** `src/main/java/com/wfm/solver/`, `src/main/java/com/wfm/model/`,
`src/main/java/com/wfm/service/SolverService.java`, `src/main/resources/solverConfig.xml`,
`src/test/java/com/wfm/solver/` (all files referenced in RESEARCH.md's `## Sources` → Primary,
this repository section)
**Files scanned:** `AgentAssignment.java`, `Schedule.java`, `Timeslot.java`, `AgentDayConfig.java`,
`ScheduleConstraintProvider.java` (full, 610 lines), `AgentAssignmentDifficultyComparator.java`,
`BreakAwareConstructionPhase.java` (full, 41 lines), `SolverService.java` (relevant sections),
`solverConfig.xml` (full, 27 lines), `BreakAwareConstructionTest.java` (full, 573 lines, read in
two non-overlapping passes: lines 1-120 and 127-171), `IncrementalScoringDiagnosticTest.java`
(lines 90-159, RESEARCH.md's cited range)
**Pattern extraction date:** 2026-08-13
