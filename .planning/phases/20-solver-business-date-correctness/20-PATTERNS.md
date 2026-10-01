# Phase 20: Solver Business-Date Correctness - Pattern Map

**Mapped:** 2026-10-01
**Files analyzed:** 25 (10 production edits, 10 new/extended test files, 2 allowlist markdown edits,
2 frontend/client files, 1 existing-test extension already covered above)
**Analogs found:** 25 / 25 — every file has at least a role-match analog; several have exact,
line-cited, copy-ready analogs already in this codebase

All analog paths below were verified git-tracked via `git ls-files -- <path>` in this session. No
path points at a gitignored install/runtime mirror.

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `src/main/java/com/wfm/solver/ScheduleConstraintProvider.java` (8 join edits + 19 anchor edits) | solver constraint provider | transform (constraint-stream join/groupBy) | itself — `shiftEnvelopeCompliance` (:505-540) and `breakClustering`'s `onBreakMarks` (:1189-1205) are the two already-migrated worked examples in the SAME file | exact (self-referential) |
| `src/main/java/com/wfm/model/AgentDayConfig.java` (+`dayStart` field) | model (record/problem fact) | transform | itself — the record already carries 7 other schedule-level scalars for the identical arity-ceiling reason | exact |
| `src/main/java/com/wfm/service/SolverService.java` (`computeAgentDayConfigs` dayStart population; `requireShiftEnvelopeSeatSupply` key-type fix) | service (pre-solve/solve orchestration) | batch / request-response | itself — `computeAgentDayConfigs` (:825-855) already populates 7 sibling scalars the same way | exact |
| `src/main/java/com/wfm/service/ScheduleOutputService.java` (6 grouping keys) | service (read/reporting) | transform (grouping for reports) | `StaffingRequirementService`'s own grouping style, and `ScheduleOutputService`'s own already-correct `AgentShiftAssignment::getDate` call sites elsewhere in the file | role-match (same file holds both the sites to fix and the sites to leave alone — see D-10) |
| `src/main/java/com/wfm/service/ShiftLibraryGenerationService.java` (4 key positions: `:180,:226,:486,:616`) | service (generation algorithm) | batch / transform | itself — `:180`/`:486` are D-13's named defect shape; `:226`/`:616` are the same shape one line later (research finding) | exact |
| `src/main/java/com/wfm/service/StaffingRequirementService.java` (`:172-175` delete range) + `src/main/java/com/wfm/repository/StaffingRequirementRepository.java` (`deleteLiveByDeskAndDateRange` query) | service + repository (destructive write path) | CRUD (delete) | `Timeslot.getBusinessDate()`'s existing accessor plus the repository's own `findAllLiveByDesk`/other delete methods as the query-editing precedent in the same file | role-match |
| `src/main/java/com/wfm/service/DeskService.java` (`:236-237` gate deletion + 15-min refusal) | service (validation/save path) | request-response | itself — `switchSchedulingMode` in the same class is the explicitly-named shape precedent (`setDayStart`'s own javadoc: "mirroring switchSchedulingMode's shape") | exact |
| `frontend/src/pages/DeskManagement.tsx:109-124` | component (display copy) | request-response (read-only display) | itself — both read and edit branches already share the exact string to correct | exact |
| `src/test/java/com/wfm/service/BusinessDateJoinGuardTest.java` (NEW, or 4th scan in `MidnightTimeArithmeticGuardTest`) | test (structural guard) | batch (build-time scan) | `MidnightTimeArithmeticGuardTest.java` (three-scan, both-directions machinery); `BusinessDateWritePathGuardTest.java` (the "new class copying the technique" precedent D-08's discretion note recommends following) | exact |
| `src/test/java/com/wfm/service/AgentDayDerivationGuardTest.java` (NEW) | test (structural guard, derivation chain) | batch | `BusinessDateWritePathGuardTest.java` — two-allowlist (deriving vs. propagating), set-equality, both-directions | exact |
| `src/test/java/com/wfm/solver/ConstraintMatchCountNonVacuityTest.java` (NEW, name per discretion) | test (reflection + per-constraint assertion) | batch | `ScheduleConstraintClassification.java` / `ScheduleConstraintClassificationTest.java` (reflective completeness); `MidnightBoundaryRegressionTest.java`'s `requireConstraint` helper (per-constraint match-count read) | exact |
| `src/test/java/com/wfm/solver/SlotModeOvernightContractedHoursTest.java` (NEW, name per discretion) | test (constructed solver fixture) | event-driven (solver scoring) | `MidnightBoundaryFixture.java` + `MidnightBoundaryRegressionTest.java` (deterministic-pinning technique, `SolutionManager.update`/`explain`) | exact |
| `src/test/java/com/wfm/solver/PhilUsShapedDriftGuardTest.java` (NEW, 48-agent in-process fixture) | test (constructed fixture + `explain()`) | event-driven (solver scoring) | `ShiftDeskEndToEndRegressionTest.java` (realistic multi-agent fixture assembly, `hardPenaltiesByConstraint`/`SolutionManager.explain` pattern); `ShiftModeFixtures.java` (fixture-builder style); `MidnightBoundaryFixture.java` (fully-pinned, never-solved construction) | exact |
| `src/test/java/com/wfm/solver/MidnightBoundaryFixture.java` (extend to a `21:00` anchor, D-11) | test fixture (extend existing) | event-driven | itself — the existing three `00:00`-anchored scenarios are the template; `MIDNIGHT_WINDOW` (`:99`) is the one constant to generalize | exact |
| `src/test/resources/midnight-time-arithmetic.md` (delete `PENDING_DESK_ANCHOR` row, `:181`) | config (allowlist manifest) | — | itself — the "Why each midnight anchor is permitted" convention (`:186-210`) | exact |
| `src/test/resources/midnight-boundary-scenarios.md` (grow manifest for D-11's new scenario/predicate, if any) | config (scenario manifest) | — | itself — "Boundary predicates" fenced block + "Why each predicate is a real boundary" convention | exact |
| `src/test/resources/bday-join-guard.md` or equivalent (NEW, if `BusinessDateJoinGuardTest` is a new class) | config (allowlist manifest) | — | `src/test/resources/bday-02-write-paths.md` (two-heading, deriving/propagating convention) and `midnight-time-arithmetic.md` (three-heading convention) | exact |
| D-02 reachability proof (extend `DeskServiceTest` + a generation-path test proving `requireDayStartTiles` fires) | test (unit + reachability) | request-response | `TimeslotGeneratorService.requireDayStartTiles` (`:220-237`, already written and tested in isolation) + `FteUploadService` (the real caller chain, `:97-152`) | exact |

## Pattern Assignments

### `ScheduleConstraintProvider.java` — the 8-edit join migration (SOLV-01/02)

**Analog:** itself, the shared `DATE` lambda and the 7 standalone `equal()` sites

**The one-lambda-fixes-seven-consumers edit** (`:90-92`):
```java
// Source: src/main/java/com/wfm/solver/ScheduleConstraintProvider.java:90-92 (verified this session)
private static final java.util.function.Function<AgentAssignment, java.time.LocalDate> DATE =
        a -> a.getTimeslot().getDate();   // -> a.getTimeslot().getBusinessDate()
```
Feeds `groupBy(AGENT_ID, DATE, …)` at `:299, :356, :389, :438, :692, :718, :1109` — no edit needed
at those 7 sites themselves, only at the lambda.

**The 7 standalone `equal()` edits** (exact right/left side to change, verified this session):
```java
// :175 (agentDayOff) — LEFT side -> getBusinessDate()
equal(a -> a.getTimeslot().getDate(), AgentDayOff::getDate)
// :222 (agentNotWorkingThatDay) — LEFT side -> getBusinessDate()
equal(a -> a.getTimeslot().getDate(), AgentDayConfig::date)
// :510 (shiftEnvelopeCompliance) — RIGHT side -> getBusinessDate()  [D-05's three named sites]
equal((sa, cfg) -> sa.getDate(), a -> a.getTimeslot().getDate())
// :608 (shiftWorkContiguity) — RIGHT side -> getBusinessDate()  [D-05]
equal(AgentShiftAssignment::getDate, a -> a.getTimeslot().getDate())
// :745 (contractedHoursUnderZero) — RIGHT side -> getBusinessDate()
equal(AgentDayConfig::date, a -> a.getTimeslot().getDate())
// :1082 (honourPreferredStartTime) — LEFT side -> getBusinessDate()
equal(a -> a.getTimeslot().getDate(), AgentPreference::getDate)
// :1193 (breakClustering, onBreakMarks) — RIGHT side -> getBusinessDate()  [D-05]
.join(Timeslot.class, equal((sa, cfg) -> sa.getDate(), Timeslot::getDate))
```

**Do NOT touch** (already business-date-shaped, `AgentShiftAssignment.getDate()` not
`Timeslot.getDate()` — confirmed by reading each enclosing stream's lead type):
```java
// :678 (bandCapacity), :916 (usualShiftConsistency), :972 (shiftStartMix), :1028 (preferredStartShiftMode)
// all read `sa.getDate()` where sa is bound by forEach(AgentShiftAssignment.class) — no edit.
```

**Receiver-disambiguation rule the guard (and any human editor) must apply**, per RESEARCH.md's
own analysis: every genuine `Timeslot`-typed `.getDate()` call site in this file spells its
receiver as exactly one of three shapes — `X.getTimeslot().getDate()` (chained accessor), a bare
variable named `ts`, or the literal method reference `Timeslot::getDate`. A bare `sa.getDate()` is
never a Timeslot receiver in this file.

---

### `ScheduleConstraintProvider.java` — the 19-site anchor migration (SOLV-03)

**Analog 1 (verbatim, already migrated) — `shiftEnvelopeCompliance`** (`:505-523`):
```java
// Source: src/main/java/com/wfm/solver/ScheduleConstraintProvider.java:504-521 (verified this session)
Constraint shiftEnvelopeCompliance(ConstraintFactory factory) {
    return factory.forEachIncludingUnassigned(AgentShiftAssignment.class)
            .join(ScheduleConfig.class)
            .filter((sa, cfg) -> cfg.schedulingMode() == SchedulingMode.SHIFT)
            .join(AgentAssignment.class,
                    equal((sa, cfg) -> sa.getAgent().getId(), a -> a.getAgent().getId()),
                    equal((sa, cfg) -> sa.getDate(), a -> a.getTimeslot().getDate()))
            .filter((sa, cfg, a) -> {
                LocalTime dayStart = cfg.dayStart();
                DayWindow window = DayWindow.anchoredAt(dayStart != null ? dayStart : LocalTime.MIDNIGHT);
                return sa.getShiftBandPair() == null || !sa.getShiftBandPair().covers(a.getTimeslot(), window);
            })
            .penalizeConfigurable()
            .asConstraint(SHIFT_ENVELOPE_COMPLIANCE_CONSTRAINT_NAME);
}
```
Note the join at the `equal(...sa.getDate()..., a -> a.getTimeslot().getDate())` line is the SAME
method's SOLV-01 defect — D-12's single commit must fix both together here.

**When to use Pattern 1 (`.join(ScheduleConfig.class)`):** any stream still Uni/Bi/Tri before the
anchor is needed. Confirmed applicable to `shiftWorkContiguity` (genuinely Bi, `(sa, seats)`,
before its `.ifExists(ScheduleConfig.class, ...)` call).

**Analog 2 (verbatim, already migrated, anchor half) — `breakClustering`'s `onBreakMarks`**
(`:1189-1205`):
```java
// Source: src/main/java/com/wfm/solver/ScheduleConstraintProvider.java:1193-1203 (verified this session)
.join(Timeslot.class, equal((sa, cfg) -> sa.getDate(), Timeslot::getDate))
.filter((sa, cfg, ts) -> {
    LocalTime dayStart = cfg.dayStart();
    DayWindow window = DayWindow.anchoredAt(dayStart != null ? dayStart : LocalTime.MIDNIGHT);
    return isOnBreak(ts, sa.getShiftBandPair(), window);
})
```
This constraint's anchor consumption is already correct — ONLY the join's `Timeslot::getDate`
needs `getBusinessDate()` (the SOLV-01 half above), not the anchor binding.

**Pattern 2 (new to this codebase, for the 5 Quad-arity sites — `exactlyOneBreak`, `breakDuration`,
`breakBlockedWindow`, `breakStartAlignment`, `honourPreferredBreakTime`):** add a `dayStart` field
to `AgentDayConfig` (analog below), populated for free in `computeAgentDayConfigs`. The existing
tagged-value precedent already in this file, directly reusable as the technique if a collector is
needed instead of a field read (e.g. for `honourPreferredBreakTime`, which does not currently join
`AgentDayConfig` at all):
```java
// Source: src/main/java/com/wfm/solver/ScheduleConstraintProvider.java:1176 (verified this session)
private record ClusterMark(int assigned, int onBreak) {}
```

**Allowlist edit required alongside this migration:** `src/test/resources/midnight-time-arithmetic.md`
loses exactly ONE row under "Permitted midnight anchors" (`:181`):
```
com.wfm.solver.ScheduleConstraintProvider :: private static final DayWindow PENDING_DESK_ANCHOR = DayWindow.anchoredAt(LocalTime.MIDNIGHT);
```
The other three rows at `:180, :182, :183` (`FteSpreadsheetGenerator`, `ShiftLibraryGenerationService
.resolveBreakConfig`, `ShiftBandPair.netHours`) are NOT this phase's concern — do not remove them.

---

### `AgentDayConfig.java` + `SolverService.computeAgentDayConfigs` — the Pattern-2 carrier

**Analog:** the record's own existing 7 scalars, and its own javadoc stating the pattern explicitly
(`:7-12`, verified this session):
```java
/**
 * Pre-computed per-agent-day configuration used as a problem fact during solving.
 * Resolves the effective contracted hours for each agent on each day,
 * accounting for AgentExceptions. Also carries schedule-level break/increment
 * config so constraints can access everything from a single join.
 */
public record AgentDayConfig(
        UUID agentId, LocalDate date, BigDecimal effectiveHours, int incrementMinutes,
        int breakDurationMinutes, BigDecimal breakMinShiftHours, BigDecimal breakBlockedHours,
        BreakAlignment breakStartAlignment, int overallocationHardLimitPct, int underallocationHardLimitPct
)
```
Add `LocalTime dayStart` as the tenth component. Populate it in `computeAgentDayConfigs`
(`SolverService.java:825-855`, verified this session) the same way the other 7 scalars are threaded
from `schedule`:
```java
// Source: src/main/java/com/wfm/service/SolverService.java:841-849 (verified this session, abridged)
configs.add(new AgentDayConfig(
        agent.getId(), d, effectiveHours,
        schedule.getIncrementMinutes(), schedule.getBreakDurationMinutes(),
        schedule.getBreakMinShiftHours(), schedule.getBreakBlockedHours(),
        /* ... add schedule.getDayStart() here as the new argument ... */));
```
`AgentShiftAssignment` already carries a populated `@Transient AgentDayConfig dayConfig` field
(`SolverService.java:991`, `sa.setDayConfig(config);`, verified this session) — any constraint
leading with `AgentShiftAssignment` (e.g. `shiftWorkContiguity`) can read
`sa.getDayConfig().dayStart()` at zero additional join cost once this field exists.

---

### `AgentDayDerivationGuardTest.java` (NEW) — D-06's derivation-chain guard

**Analog:** `src/test/java/com/wfm/service/BusinessDateWritePathGuardTest.java` (full file read this
session, 351 lines) — this is a near-verbatim template, not just a shape to imitate. Its two-heading
design (deriving vs. propagating writers, each pinned to its own exactly-one-entry allowlist) maps
directly onto D-06's two-link chain: `AgentDayConfig.date` sourced only from the schedule period,
`AgentShiftAssignment.date` only from `AgentDayConfig`.

**Imports** (`:1-19`):
```java
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList; import java.util.HashSet; import java.util.LinkedHashSet;
import java.util.List; import java.util.Set; import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
```

**Core scan + two-directional set-equality pattern** (`:92-113`, copy verbatim, retarget token):
```java
private static void assertSetEquality(Set<String> derived, Set<String> allowlist, String kind) {
    Set<String> notAllowlisted = new HashSet<>(derived);
    notAllowlisted.removeAll(allowlist);
    Set<String> staleEntries = new HashSet<>(allowlist);
    staleEntries.removeAll(derived);

    assertThat(derived)
            .as("""
                    %s ... call sites in src/main/java must equal the allowlist in %s exactly, \
                    in BOTH directions. ...

                    NEW, not allowlisted -- ...: %s

                    STALE, allowlisted but no longer present -- remove the entry: %s""",
                    kind, RESOURCE, notAllowlisted, staleEntries)
            .containsExactlyInAnyOrderElementsOf(allowlist);
}
```

**"Test of the test" red-proof pattern** (`:174-185`, `deliberatelyBrokenAllowlist_isDetectedAsAMismatch`)
and **matcher-liveness proof** (`:136-150`, `theScanDetectsAFreshOccurrence`) — both copy verbatim,
retargeted at `AgentDayConfig`'s constructor call sites and `AgentShiftAssignment.setDate(` call
sites respectively, instead of `setBusinessDate(`.

**Error handling / vacuity guard:** `requireNonEmpty` (`:294-303`) and `missingAllowlistHeading_failsLoudly`
(`:152-160`) — copy verbatim; both are generic over the heading string.

---

### `BusinessDateJoinGuardTest.java` (NEW or 4th scan) — D-08/D-09's join guard

**Analog:** `src/test/java/com/wfm/service/MidnightTimeArithmeticGuardTest.java` (full file read this
session, 657 lines) — the three-scan machinery (`scanProductionSources`, `parseFencedBlock`,
`stripComment`, `toFullyQualifiedName`) is copy-ready verbatim regardless of which discretion option
is taken.

**Imports** (`:1-21`): identical to `AgentDayDerivationGuardTest`'s above, plus `java.util.Locale`
and `java.util.function.Predicate`.

**The receiver-disambiguation matcher this guard needs** (new, not yet in the codebase — modeled on
`isRawComparison`'s existing receiver-heuristic shape, `:491-510`, and `receiverName`, `:518-529`):
```java
// Source: src/main/java/com/wfm/solver/ScheduleConstraintProvider.java -- the three shapes every
// genuine Timeslot-typed .getDate() call uses in this codebase (verified this session, no
// counter-example found): X.getTimeslot().getDate() (chained), bare `ts.getDate()`, or the literal
// method reference `Timeslot::getDate`. Recommend the predicate match these three shapes, not a
// broader name heuristic -- see RESEARCH.md "The join guard (D-08/D-09)" for the full false-
// positive/false-negative analysis this choice is based on.
```
Scanned verbs per D-08: `join`, `equal`, `groupBy`, `computeIfAbsent` — a token-containment check on
the comment-stripped line (mirror `isRawArithmetic`, `:464-470`) combined with the receiver shape
check above.

**Shared scanning core, copy verbatim** (`:443-461`, `scanProductionSources`):
```java
private Set<String> scanProductionSources(Path root, Predicate<String> matcher) throws IOException {
    Set<String> found = new LinkedHashSet<>();
    try (Stream<Path> files = Files.walk(root)) {
        List<Path> javaFiles = files.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        for (Path file : javaFiles) {
            String fqcn = toFullyQualifiedName(root, file);
            if (IMPLEMENTATION_CLASS.equals(fqcn)) { continue; }
            for (String rawLine : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String code = stripComment(rawLine);
                if (matcher.test(rawLine)) { found.add(fqcn + " :: " + code); }
            }
        }
    }
    return found;
}
```
**Scan root is 4 files, not all of `src/main/java`** — this guard must either take an explicit list
of 4 target files (`ScheduleConstraintProvider`, `ScheduleOutputService`,
`ShiftLibraryGenerationService`, `StaffingRequirementService`, per D-08/D-15) rather than walking
the whole source tree, since the scanned verbs (`groupBy`, `computeIfAbsent`) fire broadly outside
date handling elsewhere in the codebase. This is a deliberate divergence from
`MidnightTimeArithmeticGuardTest`'s whole-tree walk — document it in the new guard's own javadoc, the
same way D-10 and D-09 require documenting the guard's own scope boundaries.

**Both-directions red-proof pattern** (`:335-350`,
`midnightAnchorScan_failsOnBothAnUnlistedOccurrenceAndAStaleEntry`) — copy verbatim as the shape for
proving set-equality (not subset) is actually enforced.

**Allowlist file, if a new class is chosen:** model on `src/test/resources/bday-02-write-paths.md`'s
two-heading convention (`### Timeslot#setBusinessDate call sites` /
`### Timeslot#setBusinessDate call sites -- snapshot-copy`) — one heading per scanned verb group, or
one heading total if D-08's "plausibly empty" prediction holds and no entries are needed beyond a
documented-empty section (mirroring `allowlist_parsesAsNonEmpty`'s requirement that the section still
exist and be non-empty — if truly zero legitimate occurrences exist, the test suite needs its own
red-proof fixture the way `MidnightTimeArithmeticGuardTest`'s `COMPARISON_OFFENDER_ROOT` fixture
proves the pipeline can go red even when production code currently has zero real matches).

---

### `ConstraintMatchCountNonVacuityTest.java` (NEW) — D-12's per-constraint diagnosis (SOLV-06)

**Analog 1 (reflection-derived expected set):** `src/test/java/com/wfm/solver/ScheduleConstraintClassification.java`
and `ScheduleConstraintClassificationTest.java` (both read in full this session).

**Reflection technique, copy verbatim** (`ScheduleConstraintClassificationTest.java:42-71`):
```java
private static Set<String> constraintWeightNames() {
    Set<String> names = new HashSet<>();
    for (Field field : ConstraintWeights.class.getDeclaredFields()) {
        ConstraintWeight annotation = field.getAnnotation(ConstraintWeight.class);
        if (annotation != null) { names.add(annotation.value()); }
    }
    return names;
}

private static long constraintBuilderMethodCount() {
    long count = 0;
    for (Method method : ScheduleConstraintProvider.class.getDeclaredMethods()) {
        if (method.getReturnType().equals(Constraint.class)
                && method.getParameterCount() == 1
                && method.getParameterTypes()[0].equals(ConstraintFactory.class)) {
            count++;
        }
    }
    return count;
}
```
This is directly reusable to derive "the 26 registered constraints" set that any match-count table
must cover completely — a 27th constraint added later fails the build until a row is added, exactly
the property D-12's assertion set should inherit per RESEARCH.md's explicit recommendation.

**Analog 2 (per-constraint match-count read against a pinned Schedule):**
`src/test/java/com/wfm/solver/MidnightBoundaryRegressionTest.java`'s `requireConstraint` helper
(`:38-66`, read this session):
```java
private static SolutionManager<Schedule, HardSoftScore> newSolutionManager() {
    SolverFactory<Schedule> factory = SolverFactory.create(new SolverConfig()
            .withSolutionClass(Schedule.class)
            .withEntityClasses(AgentShiftAssignment.class, AgentAssignment.class)
            .withScoreDirectorFactory(new ScoreDirectorFactoryConfig()
                    .withConstraintProviderClass(ScheduleConstraintProvider.class)));
    return SolutionManager.create(factory);
}

private static ConstraintMatchTotal<HardSoftScore> requireConstraint(
        SolutionManager<Schedule, HardSoftScore> solutionManager, Schedule schedule, String constraintName) {
    List<ConstraintMatchTotal<HardSoftScore>> matches = solutionManager.explain(schedule)
            .getConstraintMatchTotalMap().values().stream()
            .filter(total -> constraintName.equals(total.getConstraintName()))
            .toList();
    assertThat(matches)
            .as("constraint '%s' must be present ...", constraintName)
            .hasSize(1);
    return matches.get(0);
}
```
Note the map key carries a `constraintPackage` prefix — always match on `getConstraintName()`, never
the raw map key (the comment at `:57-58` flags this explicitly as a past mistake).

**Pre/post migration comparison mechanism (D-12's "names which constraint moved"):** per
RESEARCH.md's "D-14's fixture: reaching explain()" section, write the expected-counts table as a
literal, hand-written `Map<String, Integer>` committed BEFORE the D-12 migration commit (run against
today's code to capture the literal numbers), then assert the SAME literal table again after the
migration — the literal numbers ARE the recorded baseline, avoiding any need to check out a
pre-migration commit at test-run time.

---

### `SlotModeOvernightContractedHoursTest.java` (NEW) — SOLV-04's constructed proof

**Analog:** `MidnightBoundaryFixture.java` + `MidnightBoundaryRegressionTest.java`'s deterministic
pinning technique. Build a small constructed SLOT-mode schedule (no `AgentShiftAssignment` rows —
SLOT mode has none), tight agent supply, demand spanning a non-midnight business-day boundary, pin
the same agent onto the last timeslot of day D and first timeslot of D+1 (nothing in
`ScheduleConstraintProvider` currently forbids this for SLOT mode), and assert
`contractedHoursOver`/`Under`'s match counts BEFORE the SOLV-01 fix show the double-under-allocation
and AFTER show single, correctly-business-dated allocation.

**Reuse `MidnightBoundaryFixture`'s factory helpers directly** (all `private static`, would need
either duplication or a visibility widening — `agent`, `template`, `timeslot`, `staffingRequirement`,
`seat`, `dayConfig`, `baseSchedule`, `defaultWeights`, verified present at `:535-660` this session).

---

### `PhilUsShapedDriftGuardTest.java` (NEW) — BDAY-07/D-14's 48-agent fixture

**Analog 1 (realistic multi-agent fixture + match-count extraction):**
`src/test/java/com/wfm/solver/ShiftDeskEndToEndRegressionTest.java` (613 lines, key sections read
this session).

**Imports** (`:1-51`, copy the subset relevant to fixture-building + `SolutionManager`):
```java
import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.solver.SolutionManager;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.score.director.ScoreDirectorFactoryConfig;
import ai.timefold.solver.core.config.solver.SolverConfig;
// + com.wfm.model.* entity imports as needed by the 48-agent, Phil-US-shaped fixture
```

**Per-constraint match-count extraction, copy verbatim** (`:500-515`,
`hardPenaltiesByConstraint` — generalize to ALL constraints, not only nonzero-hard ones, since D-14
needs the full per-constraint map, not just the hard violations):
```java
private static Map<String, Long> hardPenaltiesByConstraint(Schedule solved) {
    SolverFactory<Schedule> scoringFactory = SolverFactory.create(new SolverConfig()
            .withSolutionClass(Schedule.class)
            .withEntityClasses(AgentShiftAssignment.class, AgentAssignment.class)
            .withScoreDirectorFactory(new ScoreDirectorFactoryConfig()
                    .withConstraintProviderClass(ScheduleConstraintProvider.class)));
    SolutionManager<Schedule, HardSoftScore> sm = SolutionManager.create(scoringFactory);
    Map<String, Long> out = new LinkedHashMap<>();
    sm.explain(solved).getConstraintMatchTotalMap().forEach((name, total) -> {
        int hard = total.getScore().hardScore();
        if (hard != 0) { out.put(total.getConstraintRef().constraintName(), (long) hard); }
    });
    return out;
}
```
**Critical for D-14: never call `.solve(...)`.** `ShiftDeskEndToEndRegressionTest`'s own `solve(...)`
helper (`:521-529`) runs the real optimiser — D-14 explicitly requires NO actual solve (per
RESEARCH.md: "no actual solve ever runs"). Instead follow `MidnightBoundaryFixture`'s pattern: build
every `AgentAssignment`/`AgentShiftAssignment` with its planning variable ALREADY SET (pinned), then
call `solutionManager.update(schedule)` or `.explain(schedule)` directly, per
`MidnightBoundaryRegressionTest`'s `NoSearchDuringEvaluation` nested class (`:68-106`, read this
session) — which explicitly proves evaluation mutates no planning variable.

**Analog 2 (fixture-builder factory-helper style, for assembling 48 agents / Phil-US's desk shape):**
`src/test/java/com/wfm/solver/ShiftModeFixtures.java` (460 lines) and `MidnightBoundaryFixture.java`'s
`agent`/`specialization`/`template`/`timeslot`/`staffingRequirement`/`seat`/`dayConfig` factory
methods (`:535-660`) — all deterministic-id, `AtomicLong`-seeded builders, directly copyable.

**The composition data this fixture must be shaped from (not captured from):** `.planning/` memory
notes record Phil-US's live shape as 48 agents, 44.6 FTE, specific desk/specialization/template IDs
— D-14 requires the fixture be CONSTRUCTED to this shape (agent count, specialization mix), never
captured from live bytes. Do not read live Phil-US data at test-authoring time; use the composition
numbers only.

---

### `MidnightBoundaryFixture.java` extension — D-11's `21:00`-anchored scenarios

**Analog:** itself. The single constant to generalize is `MIDNIGHT_WINDOW` (`:99`, verified this
session):
```java
private static final DayWindow MIDNIGHT_WINDOW = DayWindow.anchoredAt(LocalTime.MIDNIGHT);
```
Add a sibling (e.g. `NINE_PM_WINDOW = DayWindow.anchoredAt(LocalTime.of(21, 0))`) and new scenario
builder methods mirroring `midnightCoverageScenario()`/`breakBandFlushToEnvelopeEndScenario()`
(`:133-256`) but anchored at `21:00` instead of `00:00` — these must be written and RED before the
SOLV-01/03 migration lands (D-11), i.e. against today's `PENDING_DESK_ANCHOR`-driven code, which
cannot honour a non-midnight anchor yet.

**The deterministic-pinning rule to preserve exactly** (class javadoc `:50-72`, verified this
session): every `AgentAssignment`/`AgentShiftAssignment` planning variable is assigned by ONE rule
applied uniformly — sort by date then start-minute then id, assign by index-mod-eligible-count.
Do not vary this per new scenario.

**Manifest growth, if a new structural predicate is needed:** `src/test/resources/midnight-boundary-scenarios.md`'s
"Boundary predicates" fenced block (`:32-39`) and "Why each predicate is a real boundary this
codebase can get wrong" convention (`:41-58`) — one bullet per predicate, arguing why it is a real
boundary rather than an arbitrary check. If D-11's scenarios reuse the FOUR existing predicates
(verified: "timeslot ending at day end", "final hour timeslot before day end", "band flush to
envelope edge", "envelope crossing the day anchor" — none of these are midnight-specific in their
own wording), no manifest edit is required; only add a predicate if a genuinely new structural
property is being proven.

**If a scenario asserts TODAY's (pre-migration) behaviour that the migration flips:** follow the
`@AssertsTodaysBehaviour` convention (manifest `:60-80`, a registry table of
`ClassName.NestedClass#method -> REQUIREMENT_ID` entries) — but note D-11's scenarios are the
OPPOSITE shape (red before the fix, green after), so they likely do NOT need this annotation unless
one of them deliberately pins a sub-case that stays wrong even after SOLV-03.

---

### `DeskService.setDayStart` — D-01/D-02's gate deletion and refusal (last commit)

**Analog:** itself — `switchSchedulingMode` in the same class, explicitly named in `setDayStart`'s
own javadoc as the shape to mirror: "null check, gate, tenant-scoped lookup, equal-value no-op, then
write."

**Current gate to delete** (`:236-237`, verified this session):
```java
if (!dayStart.equals(LocalTime.MIDNIGHT)) {
    throw new IllegalArgumentException("Day start other than 00:00 is not yet supported");
}
```

**Replacement shape (D-02):** a 15-minute-boundary refusal naming the rejected value, modeled on the
EXISTING `requireDayStartTiles` refusal style already written and tested
(`TimeslotGeneratorService.java:220-237`, verified this session):
```java
static void requireDayStartTiles(LocalTime dayStart, int incrementMinutes) {
    if (dayStart == null) {
        throw new IllegalArgumentException(
                "Day start is required to check tiling against the generation increment");
    }
    int dayStartMinuteOfDay = dayStart.getHour() * 60 + dayStart.getMinute();
    if (dayStartMinuteOfDay % incrementMinutes != 0) {
        throw new IllegalArgumentException(
                "Desk day-start " + dayStart + " is not a whole multiple of the "
                        + incrementMinutes + "-minute generation increment and cannot tile a day");
    }
}
```
D-02's new check is increment-INDEPENDENT (15-minute boundary, not tiling against a per-call
increment) — same exception type and "name the rejected value" convention, different modulus
(`dayStartMinuteOfDay % 15 != 0`).

**Untouched code in the same method** (`:247-253`, the unconditional ACCEPTED-schedule refusal) —
leave byte-identical; this phase does not touch it.

**Reachability proof (D-02's second half):** extend or add a test proving
`TimeslotGeneratorService.requireDayStartTiles` actually fires through the REAL upload path
(`DeskService.setDayStart` → `FteUploadService` → `TimeslotGeneratorService`), not just in
isolation. `FteUploadService.java:97-152` (read this session) is where the increment is inferred
from the spreadsheet (`:127-137`) and passed through — the reachability test should drive a real
`FteUploadService` call with a non-tiling day-start and assert the exception surfaces end to end.

---

### `frontend/src/pages/DeskManagement.tsx` — D-03's copy correction

**Analog:** itself — both occurrences of the stale string.

**Current text, both branches** (`:112` edit branch, `:124` display branch, verified this session):
```tsx
<td>{desk.dayStart} (only 00:00 is supported until overnight scheduling lands)</td>
```
**Correction per D-03:** state the desk's anchor and that the accepted range is 15-minute
boundaries, e.g. `{desk.dayStart} (15-minute boundaries accepted)` — exact wording is Claude's
discretion per CONTEXT.md, but must not claim overnight-scheduling-gated restriction (that becomes
false the moment D-01 lands) and must not imply the cell becomes editable (it stays read-only, per
the adjacent comment block at `:108-111`/`:120-123` which should also be corrected to match, since it
currently argues the SAME now-false premise).

**Untouched:** `frontend/src/api/client.ts:106-107`'s `setDayStart` call — already generic over any
`LocalTime` string; no change needed there, it already PUTs whatever value the (still-read-only) cell
holds.

---

### `StaffingRequirementService` + `StaffingRequirementRepository` — D-15's destructive delete range

**Analog:** `Timeslot.getBusinessDate()` (already exists, `Timeslot.java:38,64`) and the repository's
own existing delete-method style (`deleteLiveByDeskAndTimeslotIds`, same file, as the sibling
precedent for how this repository already expresses destructive scoped deletes).

**Current (calendar-date) range + delete, verified this session** (`StaffingRequirementService.java:172-176`):
```java
LocalDate minDate = timeslotMap.values().stream()
        .map(Timeslot::getDate).min(LocalDate::compareTo).orElseThrow();
LocalDate maxDate = timeslotMap.values().stream()
        .map(Timeslot::getDate).max(LocalDate::compareTo).orElseThrow();
staffingRequirementRepository.deleteLiveByDeskAndDateRange(tenantId, deskId, minDate, maxDate);
```
**The repository query that must move together with the service-side range** (`StaffingRequirementRepository.java:75-80`):
```java
@Modifying
@Query("DELETE FROM StaffingRequirement sr WHERE sr.tenantId = :tenantId AND sr.deskId = :deskId " +
       "AND sr.scheduleId IS NULL AND sr.timeslot.id IN " +
       "(SELECT t.id FROM Timeslot t WHERE t.tenantId = :tenantId AND t.deskId = :deskId " +
       "AND t.scheduleId IS NULL AND t.date BETWEEN :from AND :to)")
void deleteLiveByDeskAndDateRange(long tenantId, UUID deskId, LocalDate from, LocalDate to);
```
**D-15's explicit hazard:** change BOTH `Timeslot::getDate` (service, min/max derivation) AND
`t.date BETWEEN :from AND :to` (repository query, `t.businessDate BETWEEN :from AND :to`) in the SAME
edit — a half-migration (service re-keyed, query not, or vice versa) silently changes which span is
deleted, which the javadoc/CONTEXT.md flags as "worse than none."

**Line `:378` (response DTO, `toResponseItem`) — do NOT migrate per D-10's display rule:**
```java
return new StaffingRequirementResponse.Item(
        sr.getId(), t.getId(), s.getId(),
        t.getDate(), // <-- stays calendar date, same reasoning as ScheduleOutputService's :664/:759
        t.getStartTime(), t.getEndTime(), s.getName(), sr.getRequiredFTEs(), sr.getSource().name());
```

---

### `ScheduleOutputService.java` — SOLV-07's 6 grouping keys (migrate) vs. 2 display labels (D-10, do NOT migrate)

**Analog:** itself — all `.getDate()` occurrences in this one file split cleanly into the two
categories; CONTEXT.md's own warning applies verbatim: "any instruction phrased as 'migrate
ScheduleOutputService's date usage' is wrong in one direction or the other."

**Migrate — grouping/key positions** (verified this session, each `a.getTimeslot().getDate()` or
`sr.getTimeslot().getDate()` used as a `Map` key or `computeIfAbsent` key):
```java
// :62  predicted.computeIfAbsent(sr.getTimeslot().getDate(), k -> new LinkedHashMap<>())
// :71  actualCounts.computeIfAbsent(a.getTimeslot().getDate(), k -> new LinkedHashMap<>())
// :164 timeslotsByDate.computeIfAbsent(ts.getDate(), k -> new ArrayList<>())
// :173 .computeIfAbsent(a.getTimeslot().getDate(), k -> new ArrayList<>())   [nested under agentId]
// :323 .computeIfAbsent(a.getTimeslot().getDate(), k -> new ArrayList<>())   [nested under agentId]
// :739 .computeIfAbsent(a.getTimeslot().getDate(), k -> new ArrayList<>())   [nested under agentId]
```
All six -> `.getBusinessDate()`.

**Do NOT migrate — display labels (D-10, documented deliberate non-change):**
```java
// :664 (buildConstraintViolations)
timeslotLabel = aa.getTimeslot().getDate() + " " + aa.getTimeslot().getStartTime() + "-" + aa.getTimeslot().getEndTime();
// :759 (buildAcceptedConstraintViolations)
String timeslotLabel = ts.getDate() + " " + ts.getStartTime() + "-" + ts.getEndTime();
```
Both stay on calendar date — "when does this happen" is a calendar question, per D-10's reasoning.
If `BusinessDateJoinGuardTest`'s scan verbs (`join`/`equal`/`groupBy`/`computeIfAbsent`) do not
syntactically match these two lines (they don't — they're string concatenation, not a key
position), no allowlist entry is needed for them; otherwise add one with D-10's reasoning inline,
mirroring how the existing midnight-anchor allowlist documents ITS own exclusions.

**Also present in this file but requiring NO edit (per D-14's own constraint):** `:582-597`'s
provenance check (`if (schedule.getConstraintWeights() == null) { return List.of(); }`) that refuses
to call `solutionManager.explain()` on the ACCEPTED/DB path — this is the exact architectural fact
that makes D-14's in-process fixture the only cheap option; do not attempt to "fix" this refusal as
part of this phase.

---

### `ShiftLibraryGenerationService.java` — D-13's 4 key positions (SOLV-07)

**Analog:** itself — `:180`/`:486` are the two D-13 explicitly names; `:226`/`:616` are the two the
research pass found feeding the identical defect class.

```java
// :180 (weekday-filtered demand clustering)
List<StaffingRequirement> clusterDemand = demand.stream()
        .filter(sr -> cluster.contains(sr.getTimeslot().getDate().getDayOfWeek()))
        .toList();
// :226 (distinct/sort identity -- NOT among D-08's four scanned verbs: .distinct())
.map(sr -> new ShiftLibraryValidationService.Window(
        sr.getTimeslot().getDate(), sr.getTimeslot().getStartTime(), sr.getTimeslot().getEndTime()))
.distinct()
// :486 (weekday-bucketed demand aggregation)
byWeekday.computeIfAbsent(sr.getTimeslot().getDate().getDayOfWeek(), k -> new TreeMap<>())
        .merge(sr.getTimeslot().getStartTime(), sr.getRequiredFTEs(), Integer::sum);
// :616 (demanded-dates collection -- NOT among D-08's four scanned verbs: Collectors.toCollection)
Set<LocalDate> demandedDates = demand.stream()
        .map(sr -> sr.getTimeslot().getDate())
        .collect(Collectors.toCollection(TreeSet::new));
```
All four `.getDate()` -> `.getBusinessDate()`. **Important:** `:226` and `:616` are NOT caught by
`BusinessDateJoinGuardTest`'s four-verb scan (`.distinct()` and `Collectors.toCollection` are
neither `join`/`equal`/`groupBy`/`computeIfAbsent`) — these two must be migrated by explicit task
instruction, not discovered by the guard going green. Document this scope boundary in the guard's
own javadoc, mirroring D-10's precedent of recording a documented exclusion rather than leaving it
implicit.

## Shared Patterns

### The two-directional, set-equality structural guard

**Source:** `MidnightTimeArithmeticGuardTest.java`, `BusinessDateWritePathGuardTest.java`,
`UsualShiftWritePathGuardTest.java`, `SolverUsualShiftWritePathGuardTest.java` (four proven instances)
**Apply to:** `BusinessDateJoinGuardTest`, `AgentDayDerivationGuardTest`
```java
assertThat(derived)
        .as("""... NEW, not allowlisted -- %s ... STALE, allowlisted but no longer present -- %s""",
                notAllowlisted, staleEntries)
        .containsExactlyInAnyOrderElementsOf(allowlist);
```
Never widen to `isSubsetOf`/`containsAnyOf`/bare `.contains(...)` — every one of the four precedent
javadocs calls this out explicitly as the guard-becomes-decoration failure mode.

### Reflective constraint-set completeness

**Source:** `ScheduleConstraintClassification.java` / `ScheduleConstraintClassificationTest.java`
**Apply to:** `ConstraintMatchCountNonVacuityTest` (SOLV-06), optionally `PhilUsShapedDriftGuardTest`
(BDAY-07) if its expected-count table also wants completeness-by-reflection against the 26
registered constraints.
```java
for (Field field : ConstraintWeights.class.getDeclaredFields()) {
    ConstraintWeight annotation = field.getAnnotation(ConstraintWeight.class);
    if (annotation != null) { names.add(annotation.value()); }
}
```

### `SolutionManager.explain()` in-process, no actual solve

**Source:** `MidnightBoundaryFixture.java` + `MidnightBoundaryRegressionTest.java` (deterministic
pinning, `solutionManager.update`/`.explain` only — `NoSearchDuringEvaluation` nested class proves no
planning variable mutates)
**Apply to:** `SlotModeOvernightContractedHoursTest`, `PhilUsShapedDriftGuardTest`,
`ConstraintMatchCountNonVacuityTest`'s fixture-reading tests
```java
SolverFactory<Schedule> factory = SolverFactory.create(new SolverConfig()
        .withSolutionClass(Schedule.class)
        .withEntityClasses(AgentShiftAssignment.class, AgentAssignment.class)
        .withScoreDirectorFactory(new ScoreDirectorFactoryConfig()
                .withConstraintProviderClass(ScheduleConstraintProvider.class)));
SolutionManager<Schedule, HardSoftScore> sm = SolutionManager.create(factory);
sm.explain(schedule).getConstraintMatchTotalMap()... // match on getConstraintName(), never the raw map key
```

### Deterministic fixture-pinning rule

**Source:** `MidnightBoundaryFixture.java` class javadoc (`:50-72`)
**Apply to:** any new constructed-schedule fixture in this phase (`SlotModeOvernightContractedHoursTest`,
`PhilUsShapedDriftGuardTest`, D-11's `21:00` extension) — sort assignments by date then start-minute
then id; assign the i-th by `i mod eligibleCount` against agents sorted by id. Never call the solver.

### `ScheduleConfig.dayStart` → every constraint, a single already-proven channel

**Source:** `ScheduleConfigAnchorPlumbingTest.java` (Phase 19) proves `ScheduleConfig.dayStart` is
already populated and reaches the solver.
**Apply to:** every Pattern-1/Pattern-2 anchor-migration site in `ScheduleConstraintProvider` — no
new plumbing is built in this phase; every site reads the existing channel.

### Javadoc states its reason inline, cites requirement IDs not phase numbers (D-24)

**Source:** every analog file above follows this convention already (e.g. `DeskService.setDayStart`'s
own javadoc cites "SOLV-01", `AgentDayConfig`'s record javadoc states its reason, the allowlist
markdown's "Why each X is permitted" sections).
**Apply to:** every new/edited file in this phase — comments should name `SOLV-0N`/`BDAY-07`/`D-0N`,
never "Phase 20".

## No Analog Found

None. Every file in this phase's scope has at least a role-match analog already in the codebase —
consistent with RESEARCH.md's own "Don't Hand-Roll" finding that every mechanism this phase needs
(structural guard scanning, per-constraint match-count derivation, carrying a scalar past Timefold's
arity ceiling) already has one working, tested implementation here.

**One open engineering question with no existing analog to copy verbatim:** `honourPreferredBreakTime`
(one of the 19 anchor sites) joins `AgentPreference`, not `AgentDayConfig`, and is already Quad —
Pattern 2 cannot attach a `dayStart` field to a join this constraint doesn't have. RESEARCH.md's
Open Question 1 flags this as needing either a custom tagged-collector (mirroring the `ClusterMark`
technique) or a join-order restructure, to be resolved with a short spike at plan time rather than
assumed solved by this pattern map.

## Metadata

**Analog search scope:** `src/main/java/com/wfm/solver/`, `src/main/java/com/wfm/service/`,
`src/main/java/com/wfm/model/`, `src/main/java/com/wfm/repository/`, `src/test/java/com/wfm/solver/`,
`src/test/java/com/wfm/service/`, `src/test/resources/`, `frontend/src/pages/`, `frontend/src/api/`
**Files scanned:** ~30 directly read or grepped this session, all confirmed git-tracked via
`git ls-files`
**Pattern extraction date:** 2026-10-01
