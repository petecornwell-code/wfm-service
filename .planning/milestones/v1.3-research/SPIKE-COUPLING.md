# SPIKE: coupling mechanism for shift-level scheduling (Option A vs Option C)

**Date:** 2026-08-25
**Timefold:** 1.16.0 (verified at runtime: `SolverFactory.class.getPackage().getImplementationVersion()` → `1.16.0`)
**Harness:** `src/test/java/com/wfm/spike/` (throwaway, isolated worktree, no Spring context)
**Status:** conclusive on soundness; open on real-scale performance

---

## Verdict

**Commit to Option A — two independent `@PlanningEntity` classes coupled by a ConstraintStream hard
constraint. Confidence: HIGH, and the evidence is stronger than the 2-1 majority realised.** Option C
is not merely "risky": it is *empirically, reproducibly wrong* at 1.16.0. An entity-level
`@ValueRangeProvider` that reads another planning variable's current value compiles, runs, and passes
both `FULL_ASSERT` and `TRACKED_FULL_ASSERT` with zero corruption warnings — and then, in **8 of 8
random seeds**, returns a solution the solver reports as `0hard/0soft` (perfect and feasible) in which
**9 to 14 of 24 seats are staffed by agents whose shift envelope does not cover that timeslot**. The
failure mode is exactly the one this spike was commissioned to rule out, and it is invisible to every
assertion mode Timefold ships. The reason is structural and not fixable by configuration: a value
range constrains *which moves may be selected*, and nothing in Timefold re-validates an assignment
that was legal when it was made and became illegal when the *other* variable moved underneath it.
Option A, by contrast, showed zero score/ground-truth divergence in every configuration tested, and
its construction heuristic reached a fully feasible initial solution. One inconvenient truth for
Option A: it is sound but not free — the plain change-move neighbourhood converged to a soft-score
plateau (`0hard/-10soft`, or `0hard/-5soft` with a seats-first CH) rather than the known optimum
`0hard/0soft` in all 8 seeds. And one blocker that hits **both** options equally: the moment a second
`@PlanningEntity` exists, the bare `<constructionHeuristic/>` in `solverConfig.xml` throws at solver
build time and must be replaced by explicitly scoped CH phases.

---

## What the existing model actually is (read, not assumed)

- `com.wfm.model.Schedule` — `@PlanningSolution`; one `@ValueRangeProvider(id = "agentRange")` on
  `List<Agent> agents` at solution level; `@PlanningEntityCollectionProperty List<AgentAssignment>`.
- `com.wfm.model.AgentAssignment` — sole `@PlanningEntity`, `difficultyComparatorClass =
  AgentAssignmentDifficultyComparator.class`, single variable
  `@PlanningVariable(valueRangeProviderRefs = "agentRange", nullable = true) Agent agent`.
- `src/main/resources/solverConfig.xml` — declares one `<entityClass>`, a **bare
  `<constructionHeuristic/>`**, and a `<localSearch>` with a simulated-annealing acceptor
  (`0hard/3000soft`); termination is set programmatically.
- `ScheduleConstraintProvider` — 19 constraints, several deliberately written
  `forEachIncludingUnassigned(...)` + `sum(...)` with the comment "*(CH-friendly)*", plus hoisted
  shared grouping lambdas for node sharing.
- `SolverService.runPreSolveScoreDiagnostic` (≈ lines 1109–1171) — builds a throwaway `SolverFactory`
  by hand, uses `SolutionManager.update`/`explain`, assigns one agent to one seat, and logs
  `DIAGNOSTIC FAILURE ... The CH will pick null for every step!` if the hard delta is ≤ 0. This
  diagnostic detects a *scoring* pathology. **It would not have caught Option C's failure**, because
  under Option C the score is internally perfectly consistent — the constraint simply does not exist.

---

## API facts resolved against the real 1.16.0 JAR

Resolved with `javap` against
`~/.gradle/.../timefold-solver-core-1.16.0.jar` and the matching `-sources.jar`. Not from docs for
another version.

| Question | Answer at 1.16.0 |
|---|---|
| `@ShadowVariable` member name — `sourceVariableName` or `sourceVariableNames`? | **`sourceVariableName()`, singular.** The annotation declares exactly `variableListenerClass()`, `sourceEntityClass()`, `sourceVariableName()`. There is no plural form. |
| Does `@ValueRangeProvider` support anything beyond `id`? | No. `public abstract String id();` is its only member. |
| Are entity-level value ranges supported at all? | Yes — `impl.domain.valuerange.descriptor.FromEntityPropertyValueRangeDescriptor` exists and is selected when `isEntityIndependent()` is false. |
| Does the Javadoc forbid a value range depending on a planning variable? | **No — it says nothing either way.** The researcher who rated its own "forbidden" argument MEDIUM was right about the documentation. It is not forbidden; it just does not do what the option needs. |
| `@PlanningVariable(nullable = true)` — still valid? | Valid but `@Deprecated(forRemoval = true, since = "1.8.0")`; superseded by `allowsUnassigned()`. `AgentAssignment` uses the deprecated form today. |
| Can a `<valueSelector><filterClass>` express "this agent covers this timeslot"? | **No.** `SelectionFilter.accept(ScoreDirector, T)` receives the *value* only. `FilteringValueSelector` is parameterised `SelectionFilter<Solution_, Object>` over the selected value — the entity is not passed. A value-level filter structurally cannot see the timeslot. |

---

## Shared blocker: the bare `<constructionHeuristic/>` dies on the second entity class

This is not an Option A or Option C finding — it is the price of admission for **any** second
`@PlanningEntity`, including a shift entity, and neither option avoids it.

Run `A-0  BASELINE | bare <constructionHeuristic/> with TWO entity classes`:

```
EXCEPTION java.lang.IllegalArgumentException
MESSAGE   The config (QueuedEntityPlacerConfig(null, null)) has no entityClass configured and because
there are multiple in the entityClassSet ([class com.wfm.spike.ShiftAssignment,
class com.wfm.spike.SeatAssignmentA]), it cannot be deduced automatically.
```

Root cause, from `impl/AbstractFromConfigFactory.java` in the 1.16.0 sources:

```java
protected EntityDescriptor<Solution_> getTheOnlyEntityDescriptor(SolutionDescriptor<Solution_> sd) {
    Collection<EntityDescriptor<Solution_>> entityDescriptors = sd.getGenuineEntityDescriptors();
    if (entityDescriptors.size() != 1) {
        throw new IllegalArgumentException("The config (" + config
            + ") has no entityClass configured and because there are multiple in the entityClassSet ("
            + sd.getEntityClassSet() + "), it cannot be deduced automatically.");
    }
    ...
```

`QueuedEntityPlacerFactory.buildEntitySelectorConfig` calls it whenever the CH phase has no explicit
entity placer. The fix is two (or more) explicitly scoped CH phases, each with a
`QueuedEntityPlacerConfig` whose `EntitySelectorConfig` carries **both** an `entityClass` and an
**`id`** — the id matters, because `AbstractEntityPlacerFactory.buildChangeMoveSelectorConfig` builds
`EntitySelectorConfig.newMimicSelectorConfig(entitySelectorConfigId)`, and a null id makes the mimic
reference resolve to the wrong entity descriptor. My first attempt omitted the id and produced this
misleading error:

```
MESSAGE   The config (ValueSelectorConfig(shift)) has a variableName (shift) which is not a valid
planning variable on entityClass (class com.wfm.spike.SeatAssignmentA).
```

Working form (this is the pattern the roadmap will need):

```java
new ConstructionHeuristicPhaseConfig()
    .withEntityPlacerConfig(new QueuedEntityPlacerConfig()
        .withEntitySelectorConfig(new EntitySelectorConfig()
            .withId(entityClass.getName())      // required — mimic ref resolves through this
            .withEntityClass(entityClass)));
```

**Consequence for phase planning:** `solverConfig.xml` must gain explicit CH phases, and the
existing note that "no test under `src/test/java/com/wfm/solver/` loads the Spring context, so a
scoped solver-package run cannot catch a `solverConfig.xml` regression" becomes materially more
dangerous — this failure is a solver-build-time exception in production, invisible to the current
test suite.

---

## The harness

Deliberately tiny and synthetic, per the brief: 1 day, 12 slots, 2 seats per slot (24 seat entities),
4 agents, 3 library shifts. Shifts: `EARLY[0,6)` cost 0, `LATE[6,12)` cost 0, `FULL[0,12)` cost 5.
The shift cost exists so the solver has a *reason* to move shifts during local search — which is
precisely where Option C's hazard lives.

A perfect solution exists and is easy to state: 2 agents `EARLY` + 2 agents `LATE`, all 24 seats
filled → `0hard/0soft`. Every run is checked against an **external ground-truth verifier** that walks
the solution outside the solver and counts seats whose agent's shift does not cover the timeslot.
That external check is the whole point: it is the only thing that can catch a solver that lies.

Shared constraints: `Seat unfilled` (1 hard each), `Agent double booked` (1 hard each), `Shift cost`
(soft). Option A adds `Agent outside shift envelope`. Option C adds nothing — its coupling is meant
to be structural.

Option A coupling constraint (`ConstraintsA.java`):

```java
return f.forEach(SeatAssignmentA.class)
        .join(ShiftAssignment.class,
                equal(SeatAssignmentA::getAgent, ShiftAssignment::getAgent),
                equal(s -> s.getTimeslot().getDay(), ShiftAssignment::getDay))
        .filter((seat, sa) -> !sa.getShift().covers(seat.getTimeslot().getSlotIndex()))
        .penalize(HardSoftScore.ONE_HARD)
        .asConstraint("Agent outside shift envelope");
```

Option C coupling — the exact construct under test (`SeatAssignmentC.java`):

```java
@PlanningVariable(valueRangeProviderRefs = "eligibleAgentRange", nullable = true)
private SpikeAgent agent;

/** THE CRUX: an entity-level value range whose contents depend on another planning
    variable's current value. */
@ValueRangeProvider(id = "eligibleAgentRange")
public List<SpikeAgent> getEligibleAgents() {
    List<SpikeAgent> out = new ArrayList<>();
    for (ShiftAssignment sa : shiftAssignments) {          // live planning entities
        if (!sa.getDay().equals(timeslot.getDay())) continue;
        SpikeShift shift = sa.getShift();                  // <-- ANOTHER PLANNING VARIABLE
        if (shift != null && shift.covers(timeslot.getSlotIndex())) out.add(sa.getAgent());
    }
    return out;
}
```

Per the brief, **no custom `Move` or `MoveIteratorFactory` was written or benchmarked.**

---

## Option A findings

**Compiles and runs at 1.16.0:** yes, with no API surprises beyond the shared CH-config blocker.

**Sound under `FULL_ASSERT`:** yes. Five configurations were run under `EnvironmentMode.FULL_ASSERT`
(CH-only, CH+LS, both CH orderings, and a stricter `ifNotExists` variant of the coupling constraint).
**No score corruption was reported in any run** — no `assertWorkingScoreFromScratch` failure, no
`assertExpectedUndoMoveScore` failure, no shadow-variable staleness warning. More importantly, the
external verifier agreed with the reported hard score in **every single run**: whenever
`envelope viols > 0`, the hard score accounted for it.

**CH builds a feasible initial solution:** yes — and the ordering matters, in Option A's favour.

```
A-1  Option A | CH(shifts) + CH(seats) | FULL_ASSERT
Construction Heuristic phase (0) ended: ... best score (-24hard/0soft), step total (4).
Construction Heuristic phase (1) ended: ... best score (-12hard/0soft), step total (24).
RESULT score            = -12hard/0soft
RESULT unfilled seats   = 12 / 24
RESULT envelope viols   = 0  (external check)
RESULT shifts           = [A0@MON->EARLY[0,6), A1@MON->EARLY[0,6), A2@MON->EARLY[0,6), A3@MON->EARLY[0,6)]
```

Shifts-first the CH greedily gives everyone `EARLY` (cheapest) and can then only fill the morning —
12 of 24 seats. Seats-first, the CH reaches a **fully feasible** initial solution:

```
A-3  Option A | CH(seats) FIRST then CH(shifts) + LS(300) | FULL_ASSERT
Construction Heuristic phase (0) ended: ... best score (-4init/0hard/0soft), step total (24).
Construction Heuristic phase (1) ended: ... best score (0hard/-10soft), step total (4).
Local Search phase (2) ended: ... best score (0hard/-10soft), step total (300).
RESULT score            = 0hard/-10soft
RESULT unfilled seats   = 0 / 24
RESULT envelope viols   = 0  (external check)
```

Note the CH here is *not starved*: the seat phase fills every seat, then the shift phase chooses
envelopes that make those seats legal. This is the opposite of the `BreakAwareConstructionPhase`
failure mode — no pre-assignment pipeline, no quality lost per step, just two ordinary CH phases.

**The `ifNotExists` variant** of the coupling constraint (`ConstraintsA2`, which also fires while the
agent's shift is still uninitialised) is sound but converges worse and is CH-hostile with seats-first
ordering (`A-5` finished `-7hard/-10soft` with 3 real violations at 300 steps). **Recommend the plain
positive-join form** (`ConstraintsA`), which penalises only a *definite* disagreement — consistent
with the codebase's existing "CH-friendly" convention.

**Behaviour / progress:** the solver makes steady progress; there is no thrash between two search
spaces. What it *does* show is a plateau — see "inconvenient truth" below.

---

## Option C findings

**Compiles and runs at 1.16.0:** **yes.** This is the trap. `FromEntityPropertyValueRangeDescriptor`
handles it, `ValueSelectorFactory` builds a non-`EntityIndependentValueSelector` for it, the range is
re-extracted just-in-time (my counter recorded 53,091 extractions in one 2,000-step run — it is
genuinely live, not stale), and moves on the seat variable never select an ineligible agent.
Everything about it looks like it works.

**Sound under `FULL_ASSERT`:** **no corruption is reported — and that is the problem.**
`FULL_ASSERT` and `TRACKED_FULL_ASSERT` both ran clean. They cannot catch this. Reading
`AbstractScoreDirector` in the 1.16.0 sources, the entire assertion surface is:
`assertExpectedWorkingScore`, `assertShadowVariablesAreNotStale`, `assertWorkingScoreFromScratch`,
`assertPredictedScoreFromScratch`, `assertExpectedUndoMoveScore`. **There is no assertion anywhere
that a genuine variable's current value still lies within its value range.** Under Option C the
incremental score and the from-scratch score agree perfectly, because both are computing the same
(incomplete) constraint set.

Here is the result:

```
SWEEP — reported score vs external ground truth, LS(20000), seeds 1..8
opt    seed     reported score       unfilled  envViol   verdict
A      1        0hard/-10soft        0         0         score agrees with truth
C      1        0hard/0soft          0         12        *** REPORTS FEASIBLE BUT IS NOT ***
A-rev  1        0hard/-5soft         0         0         score agrees with truth
C+A    1        0hard/-10soft        0         0         score agrees with truth

A      2        0hard/-10soft        0         0         score agrees with truth
C      2        0hard/0soft          0         11        *** REPORTS FEASIBLE BUT IS NOT ***
A-rev  2        0hard/-5soft         0         0         score agrees with truth
C+A    2        0hard/-10soft        0         0         score agrees with truth

A      3        0hard/-10soft        0         0         score agrees with truth
C      3        0hard/0soft          0         12        *** REPORTS FEASIBLE BUT IS NOT ***
...
A      8        0hard/-10soft        0         0         score agrees with truth
C      8        0hard/0soft          0         12        *** REPORTS FEASIBLE BUT IS NOT ***
A-rev  8        0hard/-5soft         0         0         score agrees with truth
C+A    8        0hard/-10soft        0         0         score agrees with truth
```

**8 seeds out of 8, Option C returns `0hard/0soft`** — a schedule the system would publish as
feasible *and optimal* — with 9 to 14 of 24 seats staffed by agents who are not rostered to be there.
It even scores *better* than Option A on soft cost (`0soft` vs `-10soft`), because cheating is
cheaper: it puts every agent on a free `EARLY`/`LATE` shift and then staffs the hours they are absent
for. In production terms this is a roster that looks perfect on the dashboard and is unstaffed for
half the day.

**The mechanism, demonstrated deterministically.** Take a legal solution and apply exactly what a
`ChangeMove` on the shift variable does — nothing exotic, the single most common move in the search:

```
DEMO 1 — Option C: change a shift under a seat that is already assigned
before: score=0hard/-20soft envelopeViols=0 outOfRange=0
after A0 -> LATE (a legal ChangeMove on the shift variable):
  score       = 0hard/-15soft
  envelopeViols(external) = 6
  outOfRange(external)    = 6
  --> the score does NOT reflect the 6 seats A0 now illegally occupies

DEMO 2 — Option A: same mutation, with the hard constraint present
before: score=0hard/-20soft envelopeViols=0
after A0 -> LATE:
  score       = -6hard/-15soft
  envelopeViols(external) = 6
```

Option C's score *improves* (`-20soft` → `-15soft`) while the solution silently becomes infeasible.
The search is therefore actively rewarded for breaking the coupling. Option A registers `-6hard`
immediately and the search repairs it.

**Does Timefold repair a pre-existing out-of-range assignment?** No.

```
DEMO 3 — Option C: solve() starting from a state that is already out of range
pre-solve outOfRange = 1, envelopeViols = 1
...
solved score = -7hard/-5soft outOfRange = 7 envelopeViols = 7
```

It does not reject the input, does not warn, and ends with *more* violations than it started with.

**Can the CH build a feasible initial solution?** Only under a mandatory ordering, and it starves
outright under the other one. With shifts-first, `C-1` matches Option A (`-12hard/0soft`, 12 of 24
filled). With seats-first, the seat CH phase runs while every shift is still `null`, every
`getEligibleAgents()` returns empty, and the CH assigns `null` to all 24 seats:

```
C-3  Option C | CH(seats) FIRST then CH(shifts) + LS(300) | FULL_ASSERT
Construction Heuristic phase (0) ended: ... best score (-4init/-24hard/0soft), step total (24).
```

24 steps, zero progress. So Option C forbids the CH ordering (`A-3` / `A-rev`) that turned out to be
Option A's *best* one.

### Direct answer to the crux question

> **Does Timefold 1.16.0 permit an entity-level `@ValueRangeProvider` (or a `SelectionFilter` /
> `MoveFilter` achieving the same) to depend on another planning variable's current value?**

**It permits it in the sense that it compiles, builds, and runs without error or warning — and it is
nevertheless unusable as a coupling mechanism, because it enforces nothing.** Concretely, at 1.16.0:

1. **It is not rejected.** No validation, no exception, no log line. `FromEntityPropertyValueRangeDescriptor`
   calls your getter just-in-time and uses whatever it returns.
2. **It is not silently ignored either.** The range genuinely is live: seat-variable moves respect it.
3. **But a value range constrains move *selection*, not solution *validity*.** Timefold never
   re-checks that an existing assignment is still inside its (now-changed) range. `ChangeMove.isMoveDoable`
   for the shift variable is simply `!Objects.equals(oldValue, toPlanningValue)` — it has no idea the
   move invalidates twenty seats elsewhere. (`SwapMove` *does* consult entity-dependent ranges for its
   own two entities — see `SwapMove.java:53-64` — which shows Timefold is aware of the concept and has
   deliberately scoped the check to the move's own entities, not to the rest of the solution.)
4. **Therefore the guarantee runs in one direction only.** It is safe for a range that depends on
   *problem facts*. It is unsound for a range that depends on a *planning variable*, because the other
   variable can move afterwards.
5. **No assertion mode detects the resulting state.** Not `FAST_ASSERT`, not `FULL_ASSERT`, not
   `TRACKED_FULL_ASSERT` — the assertion surface is score-consistency only.
6. **A `SelectionFilter` cannot substitute.** A `<valueSelector><filterClass>` receives only the value
   (`SelectionFilter.accept(ScoreDirector, T)`; `FilteringValueSelector` is over `SelectionFilter<Solution_, Object>`
   on the selected value), so it cannot even express "covers this timeslot". A move-level filter on the
   change-move selector *can* see entity and value — but it is strictly weaker than the entity-level
   value range I tested, since it also only gates selection. The a-fortiori argument holds: if the
   strongest selection-side mechanism (a live, JIT-extracted value range) fails to prevent the state,
   a weaker selection-side filter cannot prevent it either. **Every selection-side mechanism shares the
   defect: it cannot undo damage done by a move on the other variable, and it cannot make the score
   reflect that damage.**

So the researcher who preferred Option C was not wrong that Timefold *allows* it, and the researcher
who rejected it was right for a reason it had not articulated. The majority reached the right answer;
this spike upgrades that from a vote to a demonstrated fact.

### The hybrid (Option C + hard constraint)

Tested as `C+A` / `ConstraintsCGuarded`: entity-level range **and** the Option A hard constraint.
It is sound (0 envelope violations, 8/8 seeds) and its solution quality is indistinguishable from
plain Option A (`0hard/-10soft`, once `0hard/-5soft`). But the range then buys nothing the constraint
does not already provide, while costing: mandatory shifts-first CH ordering, forfeiture of the better
seats-first ordering, ~10k–53k live range extractions per solve in a 24-entity toy, and loss of
`SelectionCacheType.PHASE` value caching plus the sorted / probabilistic / shuffled value-selector
options, which `ValueSelectorFactory` rejects for non-`EntityIndependentValueSelector`s:

```
The valueSelectorConfig (...) with resolvedCacheType (...) and resolvedSelectionOrder (...) needs to
be based on an EntityIndependentValueSelector (...). Check your @ValueRangeProvider annotations.
```

**Not recommended.** It is Option A carrying Option C's baggage.

---

## The inconvenient truth about Option A

Option A is sound, but it is not a free lunch, and the roadmap should budget for this.

The toy problem's true optimum is `0hard/0soft` (2 agents `EARLY` + 2 `LATE`). Option A reached
`0hard` in every seed but **never found `0soft`** — it settled at `-10soft` (shifts-first CH) or
`-5soft` (seats-first CH) across all 8 seeds at 20,000 local-search steps. The reason is exactly the
structural tension the coupling creates: improving the soft score requires *simultaneously* narrowing
an agent's shift and moving their seats, and a plain change-move neighbourhood only ever does one at a
time — each half of the swap is uphill through a hard-constraint wall.

This is the same plateau that motivated Phase 12's "Atomic Shift Move", which was withdrawn and fully
reverted (+0.25h median against a 5.00h noise spread). **This spike does not reopen that.** It just
records honestly that Option A's failure mode is *lower soft-score quality*, which is visible,
measurable, and safe — as opposed to Option C's failure mode, which is *invisible infeasibility*.
Given the choice, a schedule that is 2 hours more expensive beats a schedule that is unstaffed.

(All timing figures in the raw output are labelled `INDICATIVE ONLY` and no performance conclusion is
drawn from them, per the project's run-to-run-variance lesson.)

---

## Evidence index

Harness (throwaway, delete with the worktree):

- `src/test/java/com/wfm/spike/SpikeAgent.java`, `SpikeShift.java`, `SpikeTimeslot.java` — problem facts
- `src/test/java/com/wfm/spike/ShiftAssignment.java` — the proposed new agent-day shift entity
- `src/test/java/com/wfm/spike/SeatAssignmentA.java` — Option A seat entity (mirrors `AgentAssignment`)
- `src/test/java/com/wfm/spike/SeatAssignmentC.java` — Option C seat entity, entity-level value range
- `src/test/java/com/wfm/spike/SolutionA.java`, `SolutionC.java`
- `src/test/java/com/wfm/spike/ConstraintsA.java` (join form), `ConstraintsA2.java` (ifNotExists form),
  `ConstraintsC.java` (no coupling constraint), `ConstraintsCGuarded.java` (hybrid)
- `src/test/java/com/wfm/spike/SpikeMain.java` — runner, external ground-truth verifier, seed sweep
- `spike-build.sh`, `spike-run.sh`, `spike-capture.sh`; `spikeCp` task appended to `build.gradle`
- Full captured output: `build/spike-output.txt`

The harness deliberately avoids Spring: it compiles with plain `javac` against the Gradle-resolved
test runtime classpath and runs as a `main`, so nothing here depends on context loading.

---

## What remains open

Stated plainly — these were **not** established:

1. **Real-scale performance of Option A.** Everything here is a 24-seat toy. Whether the extra
   `AgentAssignment × ShiftAssignment` join costs materially at the real problem size (30 agents,
   30-minute increments, 19 existing constraints) is untested. Do not quote any number from this
   spike as a performance figure.
2. **Interaction with the real constraint set.** The spike used 3–4 synthetic constraints. It did not
   exercise `ScheduleConstraintProvider`'s break constraints, contracted-hours constraints,
   `minimumStaffing`, or the `MIN_AGENTS_PER_TIMESLOT` seat-expansion in `SolverService`. Whether the
   envelope constraint interacts badly with the break window or contracted-hours logic is unknown.
3. **Interaction with the production local search.** The spike used Timefold's default LS acceptor,
   not the project's `simulatedAnnealingStartingTemperature 0hard/3000soft`. The soft-score plateau
   reported above may be better or worse under simulated annealing.
4. **Multi-day and cross-midnight envelopes.** One day, one shift row per agent. Multi-day periods,
   day-off interaction, and shifts that cross midnight were not modelled.
5. **The exact CH ordering to ship.** Seats-first was better here on a toy where seat demand fully
   determines the shift choice. On real data, where contracted hours and preferences constrain shifts
   independently, shifts-first may win. This needs a measurement on real data, not a decision now.
6. **The move-filter variant of Option C was not separately executed.** The conclusion for it rests on
   the a-fortiori argument in crux point 6 plus the `SelectionFilter` signature, not on a run. I regard
   it as settled, but it is reasoning rather than observation, and I am flagging the difference.
7. **Shadow variables were not exercised.** I confirmed the `sourceVariableName` naming question
   against the JAR but did not build a shadow-variable-based coupling; none of the three researchers
   proposed one and it was out of scope.

---

## Recommendation for phase planning

**Commit to:**

1. **Option A.** A new agent-day `@PlanningEntity` with its own `@PlanningVariable` for the shift
   envelope, coupled to `AgentAssignment` by a ConstraintStream **hard** constraint. Use the plain
   positive-join form (penalise only a definite disagreement between two initialised variables), not
   `ifNotExists` — the join form is CH-friendly and matches the codebase's existing convention.
2. **Explicit construction-heuristic phases in `solverConfig.xml`.** The bare `<constructionHeuristic/>`
   *will* throw at solver build time once a second entity class exists. Each CH phase needs a
   `<queuedEntityPlacer>` with an `<entitySelector>` carrying both an `id` and an `entityClass`.
   Treat this as a task in its own right, not a footnote.
3. **A regression test that loads the real `solverConfig.xml` and builds a solver.** The known gap —
   no test under `src/test/java/com/wfm/solver/` loads the Spring context — means this exact class of
   failure ships silently today. One `SolverFactory.createFromXmlResource("solverConfig.xml").buildSolver()`
   assertion closes it, with no Spring required.
4. **A ground-truth feasibility assertion in the acceptance tests.** Independent of the score, walk
   the solved schedule and assert no `AgentAssignment` falls outside its agent's shift envelope. This
   spike exists because a score can be internally consistent and still wrong; the cheap insurance is a
   check that does not go through the score director. This generalises beyond shifts.

**Explicitly rule out:**

5. **Option C in its pure form.** Not "risky" — demonstrably produces solutions reported as feasible
   and optimal that are neither, on 8 of 8 seeds, undetected by every Timefold assertion mode.
6. **The C+A hybrid.** Sound but strictly dominated by A: same quality, extra constraints on CH
   ordering and value-selector configuration, live range extraction on every selection.
7. **Any entity-level value range anywhere in this model that reads a planning variable.** Worth
   adding to `.planning/research/PITFALLS.md` as a standing rule, with the one-line reason: *a value
   range gates move selection, it does not validate the solution, and nothing re-checks an assignment
   after the variable it depended on moves.* Ranges over problem facts remain fine.

**Leave open (measure, do not pre-decide):**

8. **CH phase ordering (seats-first vs shifts-first).** Both are sound under Option A. Seats-first won
   on the toy; pick on real data with the project's existing benchmarking discipline, and remember the
   run-to-run-variance lesson before reading anything into a single solve.
9. **Whether the soft-score plateau needs addressing at all.** Option A converges to a feasible but
   soft-suboptimal solution because shift and seat changes must move together. Measure the real
   magnitude on real data *first*. If it turns out to matter, that is a separate, evidence-backed
   conversation — and it should start by reading the Phase 12 post-mortem, not by rebuilding an atomic
   shift move.
