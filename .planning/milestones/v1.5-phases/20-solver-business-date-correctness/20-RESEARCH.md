# Phase 20: Solver Business-Date Correctness - Research

**Researched:** 2026-10-01
**Domain:** Timefold constraint-stream migration (calendar-date → business-date joins), structural
guard-test design, pre-solve seat-supply arithmetic, solver anchor plumbing consumption
**Confidence:** HIGH (every finding below is grounded in direct reads of the current HEAD source,
not training-data recall of the codebase)

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01:** The `00:00`-only gate deletion is Phase 20's LAST commit, after every join and anchor
  is migrated and guarded.
- **D-02:** The line is replaced by a 15-minute-boundary refusal at save time, naming the rejected
  value. Phase 18's D-08 generation-time tiling refusal becomes reachable through the API for the
  first time in this phase; the plan must prove it fires, not assume it.
- **D-03:** `DeskManagement.tsx`'s day-start copy is corrected; the cell stays read-only.
- **D-04:** A sixth ROADMAP success criterion is added covering the gate deletion and the opened
  range.
- **D-05:** No `business_date` column on `agent_shift_assignment` — `date` already IS the business
  date and stays derived. The three joins at `ScheduleConstraintProvider:510, :608, :1193` re-point
  the **Timeslot side only** to `getBusinessDate()`.
- **D-06:** A guard test over the derivation chain (`AgentDayConfig.date` sourced only from the
  schedule period; `AgentShiftAssignment.date` only from `AgentDayConfig`) makes "derived" safe,
  failing in both directions, mirroring `BusinessDateWritePathGuardTest`.
- **D-07:** `PENDING_DESK_ANCHOR`'s 19-site migration gets its own (seventh) ROADMAP success
  criterion: the constant is gone, its allowlist entry is gone, the guard goes red if either
  returns.
- **D-08:** `BusinessDateJoinGuardTest` scans key positions only — `join` / `equal` / `groupBy` /
  `computeIfAbsent` — across three files: `ScheduleConstraintProvider`, `ScheduleOutputService`,
  and (per D-13) `ShiftLibraryGenerationService`. Display/label sites are out of scope by
  construction, not by allowlist entry.
- **D-09:** The scanned token is `.getDate()` on a `Timeslot` receiver, and nothing else — not
  `.getDayOfWeek()`, `.plusDays(`, or `ChronoUnit.DAYS`.
- **D-10:** The two operator-facing timeslot labels (`ScheduleOutputService:664, :759`) keep the
  calendar date, documented as deliberate. All labelling change is OVNT-07's, Phase 21.
- **D-11:** The `21:00`-anchored scenarios land BEFORE the migration, red. Extend
  `MidnightBoundaryFixture`'s three constructed scenarios to a non-midnight anchor; Phase 20 owns
  this explicitly rather than inheriting it by accident.
- **D-12:** One migration commit; the per-constraint match-count assertions do the diagnosis. "One
  deliberate pass" is the commit; "names which constraint moved" is SOLV-06's assertion set.
- **D-13:** `ShiftLibraryGenerationService` is the third guarded file, and both of its weekday
  derivations (`:180, :486`) migrate in this phase — surfaced during discussion, not inherited.
- **D-14:** BDAY-07 and ROADMAP criterion 5 are rewritten as a Phil-US-*shaped* constructed drift
  guard (48 agents, its desk/specialization shape), asserting unchanged per-constraint match counts
  across the re-anchoring. No golden-byte comparison; no live-desk proof (knowingly given up for
  v1.5).

### Claude's Discretion

- The exact spelling/structure of the per-constraint match-count assertion set across the 26
  registered constraints — one parameterised test deriving expected counts from a single table
  (the `ScheduleConstraintClassification` precedent) vs. each constraint carrying its own
  assertion.
- Whether `BusinessDateJoinGuardTest` is a new class or a fourth scan inside
  `MidnightTimeArithmeticGuardTest`.
- Whether `ScheduleConfig`'s defensive null-anchor fallback (`dayStart != null ? dayStart :
  LocalTime.MIDNIGHT`) survives this phase or is tightened once no pre-Phase-19 `Schedule` can
  reach the solver.
- Task ordering and plan decomposition within the D-12 commit boundary, and where SOLV-05's
  per-business-day seat-supply shortfall reporting lands in that ordering.
- The wording of the three ROADMAP/REQUIREMENTS amendments D-04, D-07 and D-14 call for.

### Deferred Ideas (OUT OF SCOPE)

- Rename `date` → `businessDate` across all six business-date-shaped problem facts
  (`AgentShiftAssignment`, `AgentDayConfig`, `AgentDayOff`, `AgentPreference`,
  `ResolvedUsualShiftTarget`, `ShiftStartMixTarget`) — ~200 accessor sites, deferred not dismissed.
- An editable day-start control in the desk-configuration UI — Phase 21, with OVNT-07.
- OVNT-07's calendar-span label disclosure — Phase 21.
- Tightening `ScheduleConfig`'s defensive null-anchor fallback — Claude's Discretion this phase; if
  not taken, carries forward.
- Restoring a genuine live-desk drift check for Phil-US — v1.5 gives this up; natural home is
  MIGR-01..04.
- The three BDAY-06 scenarios Phase 18 deferred (18-CONTEXT D-16) — Phase 21.
- Deleting the frozen `DayWindow` oracle (19-CONTEXT D-13) once v1.5 ships.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| SOLV-01 | Every constraint that groups an agent's day joins on business date | §"The 12/18 claim, corrected" and §"The 19 PENDING_DESK_ANCHOR sites" below name every site and the exact one-lambda-plus-seven-joins edit set |
| SOLV-02 | A test fails if any constraint joins on calendar date where business date is meant | §"The join guard (D-08/D-09)" gives the receiver-disambiguation problem the guard must solve and the concrete false-positive/false-negative pairs to design against |
| SOLV-03 | Break bands, contiguity and envelope compliance hold across midnight | §"The 19 PENDING_DESK_ANCHOR sites" classifies each of the 6 constraint methods by whether the `shiftEnvelopeCompliance` join-shape applies or a different mechanism is needed |
| SOLV-04 | SLOT mode counts an overnight stretch against a single business day | §"SOLV-04: is the premise real?" verifies structurally (not by claim) that today's SLOT-mode data model already permits a midnight-spanning assignment |
| SOLV-05 | Pre-solve seat-supply check reports shortfalls per business day | §"SOLV-05: the seat-supply gate's actual defect" identifies the exact calendar/business key mismatch in `requireShiftEnvelopeSeatSupply` and distinguishes it from `computeCapacityWarnings`, which has no per-day dimension at all |
| SOLV-06 | Each migrated join is proven non-vacuous via per-constraint match counts | §"The per-constraint match-count mechanism (D-12)" names the reusable reflection precedent |
| SOLV-07 | Demand upload, coverage reporting and solver agree on business date | §"SOLV-07: the uncovered fourth file" flags `StaffingRequirementService` as outside D-08's 3-file guard scope despite being the literal "demand upload" consumer |
| BDAY-07 | Phil-US-shaped constructed drift guard, unchanged per-constraint match counts | §"D-14's fixture: reaching `explain()`" names the API and precedent test to copy |
</phase_requirements>

## Summary

This phase is a pure internal-code migration with no new external dependencies: it re-points
calendar-date joins to business-date joins in three files, replaces a hardcoded midnight anchor
placeholder with the already-plumbed real anchor at 19 call sites, extends one pre-solve gate to
key on business date, and adds two structural guard tests following patterns already proven four
times in this codebase. The hardest engineering problem is not the join re-point itself — it is
that 5 of the 6 constraint methods carrying `PENDING_DESK_ANCHOR` are already at Timefold's maximum
tuple arity (Quad) before the anchor is needed, so the `shiftEnvelopeCompliance` worked example
(bind a `DayWindow` from a freshly `.join`-ed `ScheduleConfig`) cannot be copied verbatim at most of
those sites — a different, lower-arity-cost mechanism is needed, and this research identifies one
that costs zero additional joins.

A second major finding corrects CONTEXT.md's own re-measured counts: of the "11 direct `getDate()`
sites" it lists, four (`:678, :916, :972, :1028`) are `AgentShiftAssignment.getDate()` calls, not
`Timeslot.getDate()` calls — they are already business-date-shaped by D-05's own design and need no
edit. The real edit surface is one shared lambda plus seven `equal()` call sites, not eleven
standalone edits. A third finding is that `ShiftLibraryGenerationService` contains four
`sr.getTimeslot().getDate()` occurrences, not the two D-13 names — two of them do not chain
`.getDayOfWeek()` directly but still feed the identical weekday-attribution defect one and two lines
later, and sit outside the `join`/`equal`/`groupBy`/`computeIfAbsent` scan `.distinct()` and
`Collectors.toCollection(TreeSet::new)` are not among those four verbs.

A fourth finding, directly answering the research brief's skepticism about SOLV-04: the "already
live today" framing is **verified**, not assumed — `AgentAssignment`'s planning variable is the
agent (timeslot is fixed), nothing in `ScheduleConstraintProvider` restricts the same agent being
chosen for the last timeslot of calendar day D and the first timeslot of D+1, and
`contractedHoursOver`/`contractedHoursUnder` already group by calendar date today, so a SLOT-mode
desk with tight supply has a direct incentive to do exactly this, independent of any overnight
*shift template* (which does not exist until Phase 21).

A fifth finding narrows SOLV-05 precisely: `requireShiftEnvelopeSeatSupply` (SHIFT-mode only)
already threads a `DayWindow` end to end (Phase 19's work), but its internal
`rowsByDate`/`timeslotsByDate` maps are keyed by two *different* date systems — `AgentShiftAssignment
::getDate` (business date, already correct) against `Timeslot::getDate` (calendar date) — so on any
non-midnight-anchor desk, `timeslotsByDate.getOrDefault(businessDate, List.of())` silently returns
an empty list and the gate would falsely refuse every shift-mode solve. `computeCapacityWarnings`,
by contrast, has no date dimension at all — it is a schedule-wide total, so "reports shortfalls per
business day" is a widening of that method's contract, not a key-mismatch fix.

**Primary recommendation:** Treat the 19 `PENDING_DESK_ANCHOR` sites as two populations with two
different fixes — `shiftWorkContiguity` (genuinely Bi-arity, can take `shiftEnvelopeCompliance`'s
join-based shape verbatim) vs. the five constraints already at Quad arity before the anchor is
needed (fix via a new `dayStart` field on `AgentDayConfig`, populated for free in
`computeAgentDayConfigs` from `schedule.getDayStart()`, which that method already has in scope) —
and decompose SOLV-01's task list around the *corrected* 8-edit join surface (1 lambda + 7 `equal()`
calls), not the originally-cited 18-line list.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Business-date join resolution | Solver (constraint streams) | — | `ScheduleConstraintProvider` is the sole place agent-day grouping happens; no other tier joins on `Timeslot.date` |
| Interval/anchor arithmetic | Solver (constraint streams) + shared util | `com.wfm.util.DayWindow` | `DayWindow` is the single implementation; constraints consume it, never reimplement it |
| Pre-solve seat-supply refusal | API/Backend (service layer) | — | `SolverService.requireShiftEnvelopeSeatSupply` runs before the solve, in the request-handling thread, not inside the solver |
| Coverage/demand reporting | API/Backend (service layer) | — | `ScheduleOutputService`, `StaffingRequirementService` — read paths for the operator UI |
| Shift-library generation | API/Backend (service layer) | — | `ShiftLibraryGenerationService` — a one-shot generation algorithm, not a solver constraint |
| Day-start gate/save validation | API/Backend (service layer) | Database (schema) | `DeskService.setDayStart`, backed by the `desk.day_start` column |
| Day-start disclosure copy | Frontend (React) | — | `DeskManagement.tsx` — read-only display correction, no new control |
| Structural guards (join/anchor/derivation) | Test infrastructure | — | `src/test/java` — build-time enforcement, not a runtime tier |

## Standard Stack

No new external dependency is introduced by this phase. It is a migration inside the existing
Timefold Solver 1.16.0 constraint-stream API, the existing JUnit 5 + AssertJ test stack, and the
existing Flyway/Hibernate persistence layer (no new migration is needed — D-05 explicitly rejects a
new `business_date` column on `agent_shift_assignment`).

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Timefold Solver (constraint-streams module) | 1.16.0 (already in use; unchanged this phase) | Constraint-stream joins/groupBy this phase re-points | Already the project's sole solver; this phase migrates calls, not the dependency |
| JUnit 5 + AssertJ | already in use | Structural guard tests, fixture-based regression tests | Matches the four existing guard-test precedents exactly |

### Supporting

Not applicable — no new supporting library.

### Alternatives Considered

Not applicable — this phase's "Decisions taken at scoping" (REQUIREMENTS.md) already rejected the
two architectural alternatives for this problem class (a `BusinessDate` wrapper type; a type-level
guard) at the milestone level, before Phase 20 was scoped. Re-litigating either is out of this
phase's boundary per CONTEXT.md.

**Installation:** None — no new package.

## Package Legitimacy Audit

**Not applicable.** This phase installs no external package in any ecosystem (npm, Maven/Gradle,
PyPI). Every file touched is pre-existing project source or test code. The Package Legitimacy Gate
is a no-op for this phase; no `gsd_run query package-legitimacy check` call is needed.

## Architecture Patterns

### System Architecture Diagram

```
 Operator (API/UI)
     |
     | PUT /desks/{id}/day-start  (D-02: 15-min-boundary refusal at save time)
     v
 DeskService.setDayStart ---------------------------> Desk.dayStart (DB column)
                                                              |
                                                              | read at solve-build time
                                                              v
 SolverService.buildSchedule --------> Schedule.dayStart --> ScheduleConfig.dayStart
                                                              |   (Phase 19 channel, proven live)
                      +---------------------------------------+---------------------------+
                      |                                       |                           |
                      v                                       v                           v
      ScheduleConstraintProvider              requireShiftEnvelopeSeatSupply      computeAgentDayConfigs
      (19 PENDING_DESK_ANCHOR sites;           (pre-solve gate; SHIFT-mode only;    (builds AgentDayConfig
       12/18-site date joins -- see            SOLV-05 fix: key timeslotsByDate     per agent-day; this
       corrected count below)                  by Timeslot::getBusinessDate)        phase proposes adding
            |                                        |                              dayStart here, free)
            | per-constraint match counts            |
            v                                        v
     SolutionManager.explain()              PreSolveValidationException
     (SOLV-06 non-vacuity proof;            (seat-supply shortfall,
      D-14's in-process fixture             now per BUSINESS day)
      reads this same API)
            |
            v
     ScheduleOutputService / StaffingRequirementService
     (coverage reporting, demand upload -- SOLV-07;
      display labels at :664/:759 deliberately stay
      calendar-date, D-10)
            |
            v
     ShiftLibraryGenerationService
     (weekday-bucketed library generation -- SOLV-07/D-13;
      4 getDate()-on-Timeslot sites found, not 2 -- see below)
```

### Recommended Project Structure

No new files/folders. Existing locations:

```
src/main/java/com/wfm/solver/ScheduleConstraintProvider.java   # 12/18-site join migration + 19-site anchor migration
src/main/java/com/wfm/service/SolverService.java               # SOLV-05 seat-supply gate; AgentDayConfig.dayStart addition
src/main/java/com/wfm/service/ScheduleOutputService.java       # SOLV-07 coverage grouping (6 sites) + 2 display labels (unchanged)
src/main/java/com/wfm/service/ShiftLibraryGenerationService.java # SOLV-07/D-13 weekday derivations (4 sites found)
src/main/java/com/wfm/service/StaffingRequirementService.java  # SOLV-07's uncovered 4th file -- see below
src/main/java/com/wfm/service/DeskService.java                 # D-01/D-02 gate deletion, FINAL commit
frontend/src/pages/DeskManagement.tsx                           # D-03 copy correction
src/test/java/com/wfm/service/BusinessDateJoinGuardTest.java   # NEW or 4th scan in MidnightTimeArithmeticGuardTest (D-08 discretion)
src/test/java/com/wfm/service/AgentDayDerivationGuardTest.java # NEW, D-06's two-directional chain guard (name is this research's suggestion; Claude's Discretion covers naming)
src/test/java/com/wfm/solver/MidnightBoundaryFixture.java      # D-11: extend to 21:00 anchor (653 lines today)
src/test/resources/midnight-time-arithmetic.md                  # D-07: delete the PENDING_DESK_ANCHOR allowlist entry
src/test/resources/midnight-boundary-scenarios.md               # D-11: manifest growth if a new predicate is needed
```

### Pattern 1: The already-migrated anchor shape (`shiftEnvelopeCompliance`)

**What:** Bind a `DayWindow` per-match from a freshly `.join`-ed `ScheduleConfig` tuple member,
with a defensive midnight fallback for pre-Phase-19 fixtures.
**When to use:** Any constraint stream whose arity is Uni/Bi/Tri *before* the anchor is needed —
`.join(ScheduleConfig.class)` adds exactly one tuple member, which is affordable up to Quad.
**Example (verbatim, current HEAD, `ScheduleConstraintProvider.java:504-521`):**
```java
// Source: src/main/java/com/wfm/solver/ScheduleConstraintProvider.java:504-521 (read this session)
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
Note the date-join at line 510 (`sa.getDate()` vs `a.getTimeslot().getDate()`) is a **second,
separate** defect in this same method — the anchor is already fixed here, but the Timeslot side of
this join is not yet `getBusinessDate()`. This is one of the two migrations D-12's single commit
must absorb together on this exact constraint (noted explicitly under "Constraints the architecture
imposes" in CONTEXT.md).

**A second, previously uncited worked example exists in the same file** — `breakClustering`'s
`onBreakMarks` sub-stream (`ScheduleConstraintProvider.java:1189-1205`) uses the identical
`cfg.dayStart()` pattern:
```java
// Source: src/main/java/com/wfm/solver/ScheduleConstraintProvider.java:1193-1203 (read this session)
.join(Timeslot.class, equal((sa, cfg) -> sa.getDate(), Timeslot::getDate))
.filter((sa, cfg, ts) -> {
    LocalTime dayStart = cfg.dayStart();
    DayWindow window = DayWindow.anchoredAt(dayStart != null ? dayStart : LocalTime.MIDNIGHT);
    return isOnBreak(ts, sa.getShiftBandPair(), window);
})
```
This constraint's anchor-consumption is *already correct*; only its join at line 1193
(`Timeslot::getDate` — calendar date) needs the business-date fix. CONTEXT.md's canonical refs name
only `shiftEnvelopeCompliance` as "already migrated" — the planner should know `breakClustering`'s
anchor half needs no touching at all, narrowing its task to the join fix alone.

### Pattern 2: The lower-cost mechanism for Quad-arity sites (proposed, not yet in the codebase)

**What:** Add a `dayStart` field to the `AgentDayConfig` record, populated in
`SolverService.computeAgentDayConfigs` from `schedule.getDayStart()` — which that method already
has in scope as a parameter (`Schedule schedule`) — at **zero additional join cost**, because
`AgentDayConfig` is already a tuple member in 4 of the 6 `PENDING_DESK_ANCHOR`-bearing constraints.
**When to use:** Any constraint stream that is already at Quad arity (4 tuple members) by the time
the anchor is needed, where one of those four members is already `AgentDayConfig` or
`AgentShiftAssignment` (both already carry a `@Transient`/record-field channel that can cheaply
carry one more scalar).
**Why this is sound, not a new idea:** `AgentDayConfig` already duplicates five other
schedule-level scalars this exact way — `incrementMinutes`, `breakDurationMinutes`,
`breakMinShiftHours`, `breakBlockedHours`, `breakStartAlignment`, `overallocationHardLimitPct`,
`underallocationHardLimitPct` — precisely to avoid the same Quad-arity ceiling
(`AgentDayConfig.java:14-25`, read this session — the record's own javadoc states: "carries
schedule-level break/increment config so constraints can access everything from a single join").
Adding `dayStart` is the ninth field in that established pattern, not a new technique.
**`[CITED: docs.timefold.ai/timefold-solver/latest/constraints-and-score/score-calculation]`** —
Timefold Solver's public Constraint Streams API tops out at `QuadConstraintStream` (4 tuple
members); there is no five-argument ("Penta") stream, confirming the in-code javadoc's claim rather
than merely trusting it. The same source notes tuple *mapping* (folding several logical values into
one carried object) is the documented workaround for needing a fifth value — exactly what this
pattern and the existing `ClusterMark` tagged-union technique (`breakClustering`,
`ScheduleConstraintProvider.java:1176`) both do.
**Example of the existing tuple-mapping precedent already in this file:**
```java
// Source: src/main/java/com/wfm/solver/ScheduleConstraintProvider.java:1176 (read this session)
private record ClusterMark(int assigned, int onBreak) {}
```

**Also already present and directly reusable:** `AgentShiftAssignment` carries a `@Transient
AgentDayConfig dayConfig` field, set once per row in `SolverService.buildShiftAssignments`
(`SolverService.java:991`, `sa.setDayConfig(config);` — read this session). Any constraint already
leading with `AgentShiftAssignment` as its first tuple member (`shiftWorkContiguity` does, via its
`sa` parameter) can read `sa.getDayConfig().dayStart()` with **zero new joins at all**, once
`dayStart` is added to the `AgentDayConfig` record — not even the one-join cost Pattern 1 needs.

### Anti-Patterns to Avoid

- **Assuming every `PENDING_DESK_ANCHOR` site can copy `shiftEnvelopeCompliance`'s shape
  verbatim:** the allowlist file's own prose says "every one of them is already a Quad (four-
  argument) stream" for the `ifExists`-gated group, but `shiftWorkContiguity`'s own stream, read
  directly, is Bi (`(sa, seats)`) *before* its `ifExists(ScheduleConfig.class, ...)` call — see
  §"The 19 PENDING_DESK_ANCHOR sites" below. Treat the allowlist file's "every one of them" as an
  approximation to re-verify per constraint, not a given.
- **Treating "11 direct `getDate()` sites" as 11 Timeslot-bearing edits:** four of the eleven cited
  lines (`:678, :916, :972, :1028`) are `AgentShiftAssignment.getDate()`, not
  `Timeslot.getDate()` — see the corrected count below. Editing them would be a no-op at best and a
  needless diff at worst.
- **Treating `computeCapacityWarnings` and `requireShiftEnvelopeSeatSupply` as the same gate:**
  they are two separate methods with two separate defect shapes. The first has no per-day key at
  all (schedule-wide total); the second has a per-day key that is *wrong* (calendar date mismatched
  against business date). "Reports shortfalls per business day" (ROADMAP criterion 2) most directly
  describes a fix to the second; whether the first also needs a per-day breakdown is a task-scoping
  decision the plan should make explicitly rather than conflate the two methods.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Scanning `src/main/java` for a forbidden token in a key position | A new parsing/AST library or regex-only scanner from scratch | The existing `scanProductionSources` + comment-stripping + markdown-allowlist technique in `MidnightTimeArithmeticGuardTest` | Proven four times already (`MidnightTimeArithmeticGuardTest`, `BusinessDateWritePathGuardTest`, `UsualShiftWritePathGuardTest`, `SolverUsualShiftWritePathGuardTest`); re-deriving the technique risks missing the documented limitations (receiver-name heuristic gaps) the existing javadoc already names |
| Deriving the set of registered constraints for a match-count assertion table | A hand-typed list of 26 constraint names kept in sync by convention | The `ScheduleConstraintClassification`/`ScheduleConstraintClassificationTest` reflection technique (`constraintWeightNames()` via `@ConstraintWeight` reflection, `constraintBuilderMethodCount()` via method-signature reflection) | Already proven to catch a 27th constraint added with no corresponding row — exactly the failure mode SOLV-06's own match-count table would otherwise be silently incomplete against |
| Threading the desk's anchor into a Quad-arity constraint stream | A speculative Timefold API upgrade, a custom join operator, or abandoning the constraint and re-deriving the anchor from `LocalTime.now()`/thread-local state | Add one scalar field to the already-duplicated `AgentDayConfig` record (Pattern 2 above) | `AgentDayConfig` already exists specifically to carry schedule-level scalars past the arity ceiling; inventing a second mechanism duplicates a solved problem |

**Key insight:** every mechanism this phase needs (structural guard scanning, per-constraint
match-count derivation, carrying a scalar past Timefold's arity ceiling) already has one working,
tested implementation in this codebase. The research risk in this phase is not "what library do we
need" — it is "which existing pattern applies to which of the 19+12 sites," which is exactly what
the per-site classification below answers.

## Runtime State Inventory

Not applicable — this is not a rename/refactor/migration-of-existing-data phase. No stored data,
live service config, OS-registered state, secret/env var, or build artifact carries the old
calendar-date semantics in a way that needs backfilling: D-05 explicitly establishes that
`agent_shift_assignment.date` is *already* the business date today (business date and calendar date
are identical for every existing `00:00`-anchored desk), so there is no historical row whose stored
value becomes wrong when this phase ships. The only schema-adjacent change this research identifies
(adding `dayStart` to the in-memory `AgentDayConfig` record, Pattern 2 above) is a solver problem
fact, never persisted — no migration, no backfill question.

## Common Pitfalls

### Pitfall 1: Copying the `shiftEnvelopeCompliance` anchor pattern onto an already-Quad stream

**What goes wrong:** Adding a second `.join(ScheduleConfig.class)` to a stream that already has
four tuple members produces a compile error (no `Penta` stream type exists in Timefold 1.16.0's
public API).
**Why it happens:** `shiftEnvelopeCompliance` and `breakClustering`'s on-break side both start from
a low-arity stream (`AgentShiftAssignment` alone), so `.join(ScheduleConfig.class)` is cheap there.
Five of the six `PENDING_DESK_ANCHOR`-bearing constraints (`exactlyOneBreak`, `breakDuration`,
`breakBlockedWindow`, `breakStartAlignment`, `honourPreferredBreakTime`) reach Quad arity via an
earlier `.groupBy(...).join(AgentDayConfig.class)` or `.join(AgentPreference.class)` chain, *before*
the anchor is ever needed.
**How to avoid:** Use Pattern 2 (bake `dayStart` into `AgentDayConfig`) for the five Quad-arity
constraints; reserve the `shiftEnvelopeCompliance` join pattern for `shiftWorkContiguity`, whose
pre-`ifExists` stream is genuinely Bi.
**Warning signs:** A Timefold compile error naming a missing `PentaConstraintStream` or
`.join` overload; or, if the fix is attempted by reordering joins instead, a silent loss of the
`AgentDayConfig`/`AgentPreference` object the constraint's own filter/penalize lambdas still need.

### Pitfall 2: Assuming `AgentShiftAssignment.getDate()` and `Timeslot.getDate()` are the same hazard

**What goes wrong:** A reviewer or implementer sees `.getDate()` anywhere in
`ScheduleConstraintProvider` and assumes it needs `getBusinessDate()`. Four of CONTEXT.md's own
cited "direct getDate() sites" (`:678, :916, :972, :1028`) are calls on `sa` (an
`AgentShiftAssignment`), which already returns the business date by D-05's design — editing these
is a no-op, and a careless global find-replace on `.getDate()` would try (and fail, since
`AgentShiftAssignment` has no `getBusinessDate()` method) to "fix" something that was never broken.
**Why it happens:** Both `Timeslot` and `AgentShiftAssignment` expose a method literally named
`getDate()`, and both appear as the lambda parameter named `sa`/`a` interchangeably across
different constraints in the same file.
**How to avoid:** Before editing any `.getDate()` call, confirm the receiver's declared type by
reading the enclosing stream's lead type (`factory.forEach(X.class)` / the preceding `.join(X.class,
...)`), not just the local variable name.
**Warning signs:** A compile error (`AgentShiftAssignment` has no `getBusinessDate()`); or, worse,
a successful compile with no actual behavior change (if someone defines a confused helper) that
gives false confidence the migration is complete.

### Pitfall 3: Treating the `midnight-time-arithmetic.md` "seven constraints" claim as a line-by-line count

**What goes wrong:** The allowlist file's own prose says "the single named constant seven
`ifExists(ScheduleConfig.class, filtering(...))`-gated constraints ... pass instead of the real
desk anchor," then names only six (`exactlyOneBreak`, `breakDuration`, `breakBlockedWindow`,
`breakStartAlignment`, `shiftWorkContiguity`, `honourPreferredBreakTime`). Sizing the migration
against "seven" when six constraint methods (holding 19 call sites total, confirmed by direct
count) is the ground truth creates an off-by-one expectation during review.
**Why it happens:** The documentation was written once and not re-counted against the current file
shape, the same drift CONTEXT.md itself flags for the "12 joins"/"11 sites" figures.
**How to avoid:** Re-grep `PENDING_DESK_ANCHOR` at plan/execute time (`grep -n "PENDING_DESK_ANCHOR"
ScheduleConstraintProvider.java`, excluding the declaration line) rather than trusting either the
"seven" prose or this research's "six constraint methods, 19 sites" count if the tree has moved.
**Warning signs:** The guard's "notAllowlisted" or "staleEntries" diff after the migration naming a
constraint not on either list.

### Pitfall 4: Fixing `requireShiftEnvelopeSeatSupply` without checking `computeCapacityWarnings`'s separate shape

**What goes wrong:** A plan that migrates `requireShiftEnvelopeSeatSupply`'s `timeslotsByDate` key
to `Timeslot::getBusinessDate` and calls SOLV-05 done may leave `computeCapacityWarnings`
(`SolverService.java:1015`, called at `:320`) untouched — it has no per-day key at all (a single
schedule-wide total), so it cannot have a calendar/business mismatch, but it also cannot yet
"report a shortfall per business day" as ROADMAP criterion 2 asks, because it reports no per-day
figure of any kind today.
**Why it happens:** Both methods compute "demand vs. supply," inviting the assumption they are the
same check at different granularity.
**How to avoid:** Confirm with the discuss-phase/planner whether criterion 2's "per business day"
language is satisfied by `requireShiftEnvelopeSeatSupply`'s fix alone (it is the only one of the
two that is genuinely a per-day *check*/refusal) or whether `computeCapacityWarnings` also needs a
new per-day breakdown. CONTEXT.md's "Claude's Discretion" list leaves "where SOLV-05... lands"
undecided — this distinction is the concrete content of that discretion.
**Warning signs:** A SOLV-05 acceptance test that only exercises the SHIFT-mode gate, leaving the
schedule-wide warning untested in either direction.

## Code Examples

### The corrected 12/18-site classification (research question 1)

Every site in `ScheduleConstraintProvider.java` where `.getDate()` is called, classified by
receiver type (verified by reading the enclosing stream's lead type, not by name alone):

**Needs migration — `.getDate()` on a `Timeslot` receiver, in a join/groupBy key position:**

```java
// Source: ScheduleConstraintProvider.java:90-92 (read this session) -- ONE edit fixes 7 consumers below
private static final java.util.function.Function<AgentAssignment, java.time.LocalDate> DATE =
        a -> a.getTimeslot().getDate();   // -> a.getTimeslot().getBusinessDate()
```
Feeds `groupBy(AGENT_ID, DATE, ...)` at `:299` (exactlyOneBreak), `:356` (breakDuration), `:389`
(breakBlockedWindow), `:438` (breakStartAlignment), `:692` (contractedHoursOver), `:718`
(contractedHoursUnder), `:1109` (honourPreferredBreakTime) — 7 sites, fixed by this 1 edit.

```java
// Source: ScheduleConstraintProvider.java:175 (agentDayOff, read this session)
equal(a -> a.getTimeslot().getDate(), AgentDayOff::getDate))   // left side -> getBusinessDate()
// Source: ScheduleConstraintProvider.java:222 (agentNotWorkingThatDay)
equal(a -> a.getTimeslot().getDate(), AgentDayConfig::date))   // left side -> getBusinessDate()
// Source: ScheduleConstraintProvider.java:510 (shiftEnvelopeCompliance)
equal((sa, cfg) -> sa.getDate(), a -> a.getTimeslot().getDate()))  // right side -> getBusinessDate()
// Source: ScheduleConstraintProvider.java:608 (shiftWorkContiguity)
equal(AgentShiftAssignment::getDate, a -> a.getTimeslot().getDate()))  // right side -> getBusinessDate()
// Source: ScheduleConstraintProvider.java:745 (contractedHoursUnderZero)
equal(AgentDayConfig::date, a -> a.getTimeslot().getDate()))   // right side -> getBusinessDate()
// Source: ScheduleConstraintProvider.java:1082 (honourPreferredStartTime)
equal(a -> a.getTimeslot().getDate(), AgentPreference::getDate))  // left side -> getBusinessDate()
// Source: ScheduleConstraintProvider.java:1193 (breakClustering, onBreakMarks)
.join(Timeslot.class, equal((sa, cfg) -> sa.getDate(), Timeslot::getDate))  // right side -> getBusinessDate()
```
7 standalone `equal()` sites. **Total genuine edit surface: 1 lambda + 7 `equal()` calls = 8 source
edits, covering 14 logical join/groupBy operations (7 groupBy consumers + 7 direct joins).**

**Does NOT need migration — `.getDate()` on an `AgentShiftAssignment` receiver (already
business-date-shaped per D-05; CONTEXT.md's own "11 direct sites" list includes these four in
error):**

```java
// Source: ScheduleConstraintProvider.java:678 (bandCapacity) -- sa is AgentShiftAssignment
.groupBy((sa, cfg) -> sa.getDate(), (sa, cfg) -> sa.getShiftBandPair(), countBi())
// Source: ScheduleConstraintProvider.java:916 (usualShiftConsistency) -- sa is AgentShiftAssignment
equal((sa, cfg) -> sa.getDate(), ResolvedUsualShiftTarget::date))
// Source: ScheduleConstraintProvider.java:972 (shiftStartMix) -- sa is AgentShiftAssignment
.groupBy((sa, cfg) -> sa.getDate(), (sa, cfg) -> sa.getShiftBandPair().template().getStartTime(), countBi())
// Source: ScheduleConstraintProvider.java:1028 (preferredStartShiftMode) -- sa is AgentShiftAssignment
equal((sa, cfg) -> sa.getDate(), AgentPreference::getDate))
```
In all four, `sa` is bound by `factory.forEach(AgentShiftAssignment.class)` /
`forEachIncludingUnassigned(AgentShiftAssignment.class)` earlier in the same method — confirmed by
reading each method's lead stream, not inferred from the parameter name. `[VERIFIED:
src/main/java/com/wfm/solver/ScheduleConstraintProvider.java:504-523,598-614,912-928,966-980,1021-1037
(read this session)]`

### The 19 `PENDING_DESK_ANCHOR` sites, classified by mechanism (research question 2)

19 non-declaration occurrences (confirmed: `grep -c "PENDING_DESK_ANCHOR"` returns 20 including the
declaration at `:66`), across exactly 6 constraint methods:

| Constraint | Lines | Stream arity *before* anchor is needed | Mechanism |
|---|---|---|---|
| `exactlyOneBreak` | `311,321,325,328,338` | Quad — `(daId, date, assignments, dayConfig)` after `.groupBy(AGENT_ID,DATE,TO_LIST).join(AgentDayConfig.class,...)` | Pattern 2: add `dayStart` to `AgentDayConfig`, read `dayConfig.dayStart()` — zero new joins |
| `breakDuration` | `369` | Quad, identical shape | Pattern 2 |
| `breakBlockedWindow` | `401,404,408,416,417,419,420` | Quad, identical shape | Pattern 2 |
| `breakStartAlignment` | `450` | Quad, identical shape | Pattern 2 |
| `shiftWorkContiguity` | `612,613` | **Bi** — `(sa, seats)` before its `.ifExists(ScheduleConfig.class,...)` call (verified by reading `ScheduleConstraintProvider.java:598-614` this session — the stream is `factory.forEachIncludingUnassigned(AgentShiftAssignment.class).join(AgentAssignment.class,...).groupBy((sa,a)->sa, toList((sa,a)->a))`, which is Bi, not Quad) | Pattern 1 (`shiftEnvelopeCompliance`'s shape) applies directly — `.join(ScheduleConfig.class)` is affordable here; OR, more cheaply, Pattern 2 via `sa.getDayConfig().dayStart()` since `sa` already carries a populated `@Transient AgentDayConfig` (`AgentShiftAssignment.java`, `SolverService.java:991`) |
| `honourPreferredBreakTime` | `1120,1121` | Quad — `(agentId, date, assignments, pref)` after `.groupBy(AGENT_ID,DATE,TO_LIST).join(AgentPreference.class,...)` | Pattern 2 is the only option here, and the member it must attach to is `AgentDayConfig`, which this constraint does NOT currently join — **this is the one genuinely hard site**: either add a *second* join to `AgentDayConfig` (not possible at Quad) or extend the shared `(AGENT_ID, DATE, TO_LIST)` groupBy's collector to also capture the agent-day's `dayStart` alongside the list (a custom collector returning a small record, mirroring `ClusterMark`'s tagged-value technique), keeping the groupBy's own output at 3 members (agentId, date, combinedResult) so the subsequent `.join(AgentPreference.class)` still lands at Quad |

**The allowlist file's "seven constraints" claim does not match a direct count — six constraint
methods hold all 19 sites.** `[VERIFIED: src/test/resources/midnight-time-arithmetic.md "Permitted
midnight anchors" section + src/main/java/com/wfm/solver/ScheduleConstraintProvider.java (read this
session) — the allowlist file quotes "seven ... constraints (exactlyOneBreak, breakDuration,
breakBlockedWindow, breakStartAlignment, shiftWorkContiguity, honourPreferredBreakTime, reached
through their shared interval-arithmetic helpers)" — six names are given, not seven.]` This is a
documentation drift to flag, not a blocker.

### SOLV-04: is the premise real? (research question 3)

**Verified, not assumed**, via direct structural reading rather than trusting the ROADMAP's framing:

```java
// Source: src/main/java/com/wfm/model/AgentAssignment.java:31,37-38 (read this session)
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "timeslot_id", nullable = false)
private Timeslot timeslot;                              // FIXED per entity

@PlanningVariable(valueRangeProviderRefs = "agentRange", nullable = true)
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "agent_id")
private Agent agent;                                     // the ONLY planning variable
```
The timeslot is fixed per `AgentAssignment` entity; the agent is the sole planning variable, chosen
from the full `agentRange`. No constraint in `ScheduleConstraintProvider` restricts which calendar
dates an agent's chosen timeslots may span (`shiftWorkContiguity`, the one constraint that *would*
restrict cross-day continuity, is gated `== SHIFT` only — SLOT-mode desks have zero
`AgentShiftAssignment` rows, so it is structurally inert there). Meanwhile:
```java
// Source: ScheduleConstraintProvider.java:686-696 (contractedHoursOver, read this session)
return factory.forEach(AgentAssignment.class)
        .filter(a -> a.getAgent() != null)
        .groupBy(AGENT_ID, DATE, COUNT)     // DATE = a.getTimeslot().getDate(), calendar date, TODAY
        .join(AgentDayConfig.class, ...)
```
already groups by **calendar** date today. If the solver places the same agent on, say, 23:45 of
day D and 00:00 of day D+1 (nothing forbids it, and under-covered demand on both sides of midnight
makes it attractive), `contractedHoursOver`/`contractedHoursUnder` independently score each half
against that agent's *full* daily `expectedWorkSlots()`, registering both days as under-allocated —
exactly the mechanism REQUIREMENTS.md's "Why this milestone exists" section describes. **This
confirms SOLV-04's defect is real and reachable today, independent of any overnight shift
template** — the gap is in SLOT mode's lack of any shift/contiguity concept at all, not in the
existence of overnight templates (which don't exist until OVNT-01, Phase 21).
`[VERIFIED: src/main/java/com/wfm/model/AgentAssignment.java:1-61,
src/main/java/com/wfm/solver/ScheduleConstraintProvider.java:686-710 (read this session)]`

**Recommendation for the plan:** build a small constructed SLOT-mode test scenario (tight agent
supply, demand spanning a midnight boundary) that demonstrates the double-under-allocation before
the SOLV-04 fix and a single, correctly-business-dated allocation after — this is the only way to
make "closing a defect that is already live today" a checkable acceptance criterion rather than an
assertion. It can reuse `MidnightBoundaryFixture`'s deterministic-pinning technique.

### SOLV-05: the seat-supply gate's actual defect (research question 4)

```java
// Source: src/main/java/com/wfm/service/SolverService.java:1408 (method start, read this session)
static void requireShiftEnvelopeSeatSupply(
        SchedulingMode schedulingMode, List<AgentShiftAssignment> shiftAssignments,
        List<ShiftBandPair> shiftBandPairs, List<Timeslot> timeslots,
        List<AgentAssignment> assignments, int overallocationHardLimitPct,
        List<String> warnings, ConstraintWeights weights, DayWindow window) {
    ...
    // Source: SolverService.java:1432-1436 (read this session)
    Map<LocalDate, List<AgentShiftAssignment>> rowsByDate = shiftAssignments.stream()
            .collect(Collectors.groupingBy(AgentShiftAssignment::getDate, ...));   // business date (D-05)
    Map<LocalDate, List<Timeslot>> timeslotsByDate = (timeslots == null ? List.<Timeslot>of() : timeslots)
            .stream()
            .collect(Collectors.groupingBy(Timeslot::getDate, ...));              // CALENDAR date -- the bug
```
Then, inside the per-date loop (`SolverService.java:1448-1455`):
```java
for (Map.Entry<LocalDate, List<AgentShiftAssignment>> entry : rowsByDate.entrySet()) {
    LocalDate date = entry.getKey();                                 // a BUSINESS date
    List<Timeslot> dateTimeslots = timeslotsByDate.getOrDefault(date, List.of());  // looked up by that
                                                                                    // same key against a
                                                                                    // CALENDAR-date map
    List<Timeslot> coveredTimeslots = coveredTimeslotsOnDate(date, dateTimeslots, pairs, window);
```
On any desk whose `dayStart` is not midnight, `timeslotsByDate.getOrDefault(businessDate, ...)`
returns an empty list for a business day whose timeslots carry a different calendar date (every
post-midnight slot of an overnight-spanning business day) — `dateTimeslots` is empty,
`coveredTimeslots` is empty, `librarySupplySlots` computes to zero, and the gate would **falsely
refuse every shift-mode solve** on a re-anchored desk, even a fully solvable one.
**The fix:** re-key `timeslotsByDate` by `Timeslot::getBusinessDate` (`SolverService.java:1435`).
`window` is already threaded through to `coveredTimeslotsOnDate`/`forcedAgentDaysByTimeslotId` by
Phase 19's plan 19-05 (confirmed by the javadoc at `SolverService.java:1404-1406`) — only the map
key needs the fix, not the anchor plumbing.

**`computeCapacityWarnings` (`SolverService.java:1015`, called at `:320`) is a different method
entirely** — it sums `totalDemandSlots`/`totalSupplySlots` schedule-wide with no `Map<LocalDate,
...>` at all (`SolverService.java:1020-1037`, read this session). It cannot have a calendar/business
mismatch because it has no date key to mismatch — but it also does not yet satisfy "reports a
shortfall per business day" in any sense. The plan must decide explicitly whether ROADMAP criterion
2 is satisfied by the `requireShiftEnvelopeSeatSupply` fix alone or requires widening
`computeCapacityWarnings` too; this research recommends the former reading (the gate is the thing
that actually blocks a solve; the warning is advisory) but flags it as exactly the kind of decision
CONTEXT.md's "Claude's Discretion — where SOLV-05... lands" leaves open.
`[VERIFIED: src/main/java/com/wfm/service/SolverService.java:1015-1037,1408-1443 (read this
session)]`

### The join guard (D-08/D-09): the receiver-disambiguation problem it must solve (research question 5)

`MidnightTimeArithmeticGuardTest`'s existing comparison-token scan (`isRawComparison`,
`MidnightTimeArithmeticGuardTest.java` — read this session) uses a **name-based** receiver
heuristic: a receiver counts if it case-insensitively ends with `time`/`start`/`end` or begins with
`envelope`/`band`/`break`/`slot`. That heuristic works because no OTHER type in scope shares a
method name with the scheduling-time accessors it is trying to catch.

**`BusinessDateJoinGuardTest` faces a harder version of the same problem**: `.getDate()` is called
on *both* `Timeslot` (ambiguous, must flag) and `AgentShiftAssignment` (already business-date-
shaped, must NOT flag) — **same method name, different receiver type** — confirmed by this
research's own site-by-site classification above (e.g. `a.getTimeslot().getDate()` at `:175` vs.
`sa.getDate()` at `:678`, both literally spelled `.getDate()`). A purely name-based heuristic
mirroring the comparison scan's style would need to positively match `.getTimeslot().getDate()`,
bare `ts.getDate()`, and the method reference `Timeslot::getDate`, while excluding bare
`sa.getDate()` and every other record-accessor method reference (`AgentDayOff::getDate`,
`AgentPreference::getDate`, `ResolvedUsualShiftTarget::date`, `AgentDayConfig::date`). Every
Timeslot-typed occurrence found in this codebase's `ScheduleConstraintProvider` spells the receiver
as one of exactly three shapes: `X.getTimeslot().getDate()` (chained accessor), a bare variable
named `ts`, or the literal method reference `Timeslot::getDate`. **Recommend the guard's receiver
predicate match these three shapes specifically** (ends-with `.getTimeslot()`, variable name `ts`,
or literal `Timeslot::`) rather than a broader name pattern — a broader pattern risks either missing
a renamed local or (more likely, given this codebase's `sa` naming convention for
`AgentShiftAssignment`) producing a false negative on a future site that reuses `sa` for a different
entity type.

Whether to make this a new class or a fourth scan in `MidnightTimeArithmeticGuardTest` (explicitly
Claude's Discretion per CONTEXT.md): the existing file's three scans already share
`scanProductionSources`/`parseAllowlist`-style helpers and the same markdown-file convention
(`midnight-time-arithmetic.md`). A fourth scan reuses that machinery directly but conflates two
different bug classes (raw time arithmetic vs. calendar/business date confusion) under one file
name and one javadoc. A new class (`BusinessDateJoinGuardTest` + `bday-join-guard.md` or similar)
costs re-stating the shared scanning helper (a handful of lines, already proven safe to copy — see
`BusinessDateWritePathGuardTest`'s own javadoc, which explicitly mirrors
`MidnightTimeArithmeticGuardTest`'s technique as a *second* file, not a fourth scan in the first
one) but keeps each guard's javadoc focused on one bug class. This research's recommendation: new
class, following `BusinessDateWritePathGuardTest`'s own precedent of copying the technique into a
fresh file rather than extending the original — but this is explicitly the discussion's own
discretion call, not a locked decision, and either is structurally sound.

### SOLV-07: the uncovered fourth file (research question — surfaced during this research, not in CONTEXT.md)

D-08 scopes `BusinessDateJoinGuardTest` to exactly three files: `ScheduleConstraintProvider`,
`ScheduleOutputService`, `ShiftLibraryGenerationService`. But `StaffingRequirementService.java` —
explicitly named in CONTEXT.md's own canonical refs as relevant to SOLV-07 — contains two
`Timeslot.getDate()`-derived reads outside that guard's scope:

```java
// Source: StaffingRequirementService.java:172-175 (read this session)
LocalDate minDate = timeslotMap.values().stream().map(Timeslot::getDate).min(LocalDate::compareTo).orElseThrow();
LocalDate maxDate = timeslotMap.values().stream().map(Timeslot::getDate).max(LocalDate::compareTo).orElseThrow();
// used as: staffingRequirementRepository.deleteLiveByDeskAndDateRange(tenantId, deskId, minDate, maxDate);
```
```java
// Source: StaffingRequirementService.java:378 (toResponseItem, read this session)
t.getDate(),   // -> StaffingRequirementResponse.Item's "date" field
```
Investigated: `deleteLiveByDeskAndDateRange`'s backing query (`StaffingRequirementRepository.java:
75-80`, read this session) filters `t.date BETWEEN :from AND :to` — **calendar date on both sides**
of this specific delete-then-reinsert flow, and the batch's own timeslot IDs (not a reconstructed
date range) are what's actually being replaced. `[VERIFIED:
src/main/java/com/wfm/repository/StaffingRequirementRepository.java:75-80 (read this session) —
quoted above verbatim]` This specific range-query usage appears internally self-consistent (both
sides of the bounding box are calendar date) and is not obviously a cross-system key mismatch like
the one found in SOLV-05 — but it has not been proven safe by a test, and `toResponseItem`'s
`t.getDate()` populating the response DTO's "date" field is operator-facing in the same way the two
D-10-exempted display labels are, raising the identical "is this a deliberate display choice"
question D-10 answers for `ScheduleOutputService`.

**Recommendation:** the planner must explicitly resolve this gap rather than let it pass silently
through D-08's 3-file guard. Two sound options: (a) treat both `StaffingRequirementService` sites
as deliberately calendar-scoped, documented with the same D-10-style reasoning ("a bounding-box
delete and a display DTO field are not key-position joins"), and explicitly note this in the
guard's own documentation (mirroring D-10's requirement that the guard record *why* a site is out
of scope); or (b) widen the guard's file list to four. Given D-08 is a locked decision naming
exactly three files, option (a) is the lower-risk path that does not relitigate the lock — but it
requires an explicit decision, not silence, because "demand upload" is literally what SOLV-07 names
and `StaffingRequirementService` is literally where demand upload lives.

### ShiftLibraryGenerationService: four sites found, not two (research question — D-13 re-verification)

```bash
$ grep -n "getDate()" src/main/java/com/wfm/service/ShiftLibraryGenerationService.java
180:  .filter(sr -> cluster.contains(sr.getTimeslot().getDate().getDayOfWeek()))
226:  sr.getTimeslot().getDate(), sr.getTimeslot().getStartTime(), sr.getTimeslot().getEndTime()))
486:  byWeekday.computeIfAbsent(sr.getTimeslot().getDate().getDayOfWeek(), k -> new TreeMap<>())
616:  .map(sr -> sr.getTimeslot().getDate())
```
`[VERIFIED: src/main/java/com/wfm/service/ShiftLibraryGenerationService.java:180,226,486,616 (read
this session)]` D-13 names exactly `:180` and `:486` — the two that chain `.getDayOfWeek()`
directly on the `.getDate()` call. Lines `226` and `616` do not chain `.getDayOfWeek()` on the same
line but still feed the identical defect class one to two lines later:

- **Line 226** builds a `Window(date, startTime, endTime)` record consumed by `.distinct()` — a
  deduplication key, not named among D-08's four scanned verbs (`join`/`equal`/`groupBy`/
  `computeIfAbsent`). Whether this window's `date` should be business date is a real open question
  the guard as scoped will not catch either way.
- **Line 616** feeds `supplyHours`'s `demandedDates` (`Set<LocalDate>`, built via
  `Collectors.toCollection(TreeSet::new)` — also not among the four scanned verbs), which the very
  next lines (`ShiftLibraryGenerationService.java:619-621`, read this session) convert via
  `date.getDayOfWeek()` to look up `hoursByWeekday` — **the same weekday-attribution shape D-13
  names as the defect**, just split across three lines instead of chained on one.

**Recommendation:** flag both for the planner's explicit decision alongside D-13's two named sites.
Neither is caught by D-08's four-verb scan as currently scoped (`.distinct()` and
`Collectors.toCollection` are not `join`/`equal`/`groupBy`/`computeIfAbsent`), so if they need
migrating, they will need to be migrated by task instruction, not discovered by the guard going
green — the guard's own documentation should record this as a known scope boundary, following the
precedent D-10 sets for documented exclusions.

### D-14's fixture: reaching `explain()` (research question 7)

```java
// Source: ScheduleOutputService.java (read this session) -- the API D-14's fixture reuses in-process
var explanation = solutionManager.explain(schedule);
Map<String, ConstraintMatchTotal<HardSoftScore>> totals = explanation.getConstraintMatchTotalMap();
```
This is the exact API `ScheduleOutputService`'s live-solver path already calls
(`buildConstraintViolations`, read this session) and that `ScheduleOutputService:582-597`
deliberately refuses to call on the ACCEPTED/DB path (confirmed by reading the surrounding
provenance-check code this session: `if (schedule.getConstraintWeights() == null) { return
List.of(); }` guards entry into the `explain()`-calling branch). A test-scope fixture can call
`solutionManager.explain(schedule)` directly against an **in-memory, never-persisted** `Schedule`
object built the same way `MidnightBoundaryFixture` builds its three scenarios — fully assembled
with every planning variable pre-pinned, so no actual solve ever runs and the call is deterministic.
`ShiftDeskEndToEndRegressionTest` and `ShiftModeFixtures` (named in canonical refs) are the
precedent for assembling a realistically-sized (48-agent) fixture outside the solver's own
construction heuristic.

**Mechanical before/after comparison, given the "before" code won't exist after the migration
commit (D-12 is one commit):** capture the expected "before" per-constraint match-count map as a
literal, hand-written `Map<String, Integer>` (or a small table, matching the
`ScheduleConstraintClassification` precedent's style) committed in the SAME pre-migration test file
the D-11 tracer adds — i.e., write the test's *expected-counts* table once, by running it against
today's code before the D-12 commit, and commit that literal table alongside the red D-11 scenarios.
After the D-12 migration commit, the SAME literal table is asserted again — now it should still
match (D-14's claim is "unchanged... across the re-anchoring"), proving the migration did not move
any constraint's behavior on this fixture shape. This avoids needing to check out the pre-migration
commit at test-run time; the literal numbers ARE the recorded baseline.

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|---------------|--------|
| `DayWindow.anchoredAt(LocalTime.MIDNIGHT)` as the sole anchor everywhere | `ScheduleConfig.dayStart` read per-schedule, with `PENDING_DESK_ANCHOR` as the sole remaining holdout | Phase 19 (2026-10-01) | This phase consumes an already-built channel; no new plumbing |
| `Timeslot.date` as the sole date on a timeslot | `Timeslot.businessDate` stored alongside `date`, sole writer `TimeslotGeneratorService`, guarded by `BusinessDateWritePathGuardTest` | Phase 18 (2026-09-30) | `getBusinessDate()` is the target accessor for every join site this phase migrates |
| Captured-live-desk golden-byte regression fixtures (v1.4, cancelled) | Constructed scenarios chosen by the property under test, plus a Phil-US-*shaped* (not captured) drift guard | v1.4 cancellation, 2026-09-30; D-14, 2026-10-01 | BDAY-07's fixture must be built, not captured — no live Phil-US data read |

**Deprecated/outdated:**
- The "00:00 means end of day" convention outside `DayWindow` — retired by Phase 19 (BDAY-04); this
  phase must not reintroduce raw `LocalTime` comparisons when fixing join sites (the existing
  `MidnightTimeArithmeticGuardTest` will catch a reintroduction).
- `PENDING_DESK_ANCHOR` itself — named with an explicit removal owner (`SOLV-01`) from the moment it
  was created in Phase 19; this phase is that removal.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `honourPreferredBreakTime`'s mechanism requires a custom tagged-collector (mirroring `ClusterMark`) rather than a simpler fix, because it joins `AgentPreference` (not `AgentDayConfig`) and is already Quad | Pattern 1/2, "The 19 PENDING_DESK_ANCHOR sites" table | If a simpler mechanism exists (e.g. restructuring join order), the plan may over-scope this one site; worth a focused design pass at plan time rather than treating this research's proposed mechanism as final |
| A2 | `requireShiftEnvelopeSeatSupply`'s fix is the full discharge of ROADMAP criterion 2's "per business day" language, and `computeCapacityWarnings` needs no change | "SOLV-05: the seat-supply gate's actual defect" | If the discuss-phase/planner reads criterion 2 as requiring the schedule-wide warning to also be bucketed per day, this assumption under-scopes SOLV-05's task list |
| A3 | `StaffingRequirementService`'s two uncovered sites are safe to leave as deliberately calendar-scoped (option (a)) rather than requiring a guard-scope widening | "SOLV-07: the uncovered fourth file" | If an overnight desk's demand-upload delete-range or response DTO actually needs business-date semantics, leaving it undocumented-but-unmigrated reintroduces exactly the "guard's green is misleading" failure `MidnightTimeArithmeticGuardTest`'s own javadoc warns against |
| A4 | `ShiftLibraryGenerationService:226,616` are genuine instances of D-13's defect class and not already-correct uses (e.g. if `Window.date()`/`demandedDates` turn out to be purely calendar-scoped display/reporting, not weekday-pattern-matching) | "ShiftLibraryGenerationService: four sites found, not two" | If assumed migration-worthy but actually benign, extra unnecessary work; if assumed benign but actually a defect (more likely given line 616's clear weekday chain two lines later), a known instance of the bug ships undetected, exactly the failure `MidnightTimeArithmeticGuardTest`'s javadoc warns about |
| A5 | Timefold Solver 1.16.0's public Constraint Streams API genuinely has no 5-argument stream type, confirming the in-code javadoc rather than merely restating it | Standard Stack, Pattern 2 | `[CITED: docs.timefold.ai]` — low risk; this is a documented framework limit, cross-checked against an external source in this session, not training-data recall alone |

## Open Questions (RESOLVED)

**All three were closed downstream during planning on 2026-10-01 — recorded here so the heading does
not read as outstanding work:**

- **OQ1** (`honourPreferredBreakTime`'s mechanism) → **resolved as a deliberate deferral.** It is a
  blocking `checkpoint:decision` at Task 1 of `20-05-PLAN.md`, with the implementing task explicitly
  deferring to its outcome. Not silently picked, which was this question's actual risk.
- **OQ2** (whether the join guard's receiver heuristic needs type-awareness) → **resolved in
  `20-02-PLAN.md`** as documented accepted risk, with the observed three-shape name pattern taken as
  sufficient and the reasoning recorded in the guard's own documentation.
- **OQ3** (whether SOLV-04 also needs live-data demonstration) → **resolved: constructed fixture
  only**, per 20-CONTEXT.md D-14 and the amended BDAY-07. Matches this milestone's own
  v1.4-cancellation lesson, which the research itself cited in making the recommendation.


1. **Does `honourPreferredBreakTime` need a new custom collector, or can its join order be
   restructured instead?**
   - What we know: it is Quad after `.groupBy(AGENT_ID,DATE,TO_LIST).join(AgentPreference.class,
     ...)`; `AgentDayConfig` (which would need to supply `dayStart`) is not currently joined at all
     in this constraint.
   - What's unclear: whether a tagged-collector mirroring `ClusterMark` is simpler or more
     error-prone than restructuring the stream to lead with `AgentDayConfig` instead of raw
     `AgentAssignment` groupBy.
   - Recommendation: resolve at plan time with a short spike against this one constraint before
     committing to a decomposition for all 19 sites — it is the one genuinely novel engineering
     problem in SOLV-03's scope; everything else in the 19-site list reuses an existing pattern.

2. **Does the join guard's receiver heuristic need to be type-aware, or is the three-shape
   name-based pattern (`.getTimeslot()`-chained, `ts`, `Timeslot::`) sufficient for this
   codebase's actual naming conventions?**
   - What we know: every current Timeslot-typed `.getDate()` call site in
     `ScheduleConstraintProvider` matches one of those three shapes; no counter-example exists in
     the current tree.
   - What's unclear: whether a future constraint could introduce a Timeslot variable named
     something else (e.g. `slot`, `demandSlot`) that a three-shape pattern would miss.
   - Recommendation: given `MidnightTimeArithmeticGuardTest`'s own documented acceptance of this
     exact class of false-negative risk for its comparison-token heuristic ("a false negative is
     only possible under one of the three limitations... narrowing that gap further is deferred"),
     apply the same risk tolerance here rather than building a type-aware parser.

3. **Is there a live desk today whose SLOT-mode data already demonstrates SOLV-04's defect, or
   does the fixture need to be purely constructed?**
   - What we know: the mechanism is structurally confirmed (see "SOLV-04: is the premise real?"
     above); no specific live desk/week was checked for an *actual* occurrence of cross-midnight
     SLOT-mode assignment in this research pass.
   - What's unclear: whether a live example would strengthen SOLV-04's acceptance proof beyond a
     constructed fixture, or whether — per this milestone's own v1.4-cancellation lesson — a
     constructed fixture is the right choice regardless (matching BDAY-06's "constructed, not
     captured" precedent).
   - Recommendation: default to a constructed fixture (consistent with D-14's reasoning for
     BDAY-07), and treat a live-data check as optional confirmatory evidence only, not a
     blocking research gap.

## Environment Availability

Skipped — this phase has no new external dependency (no new tool, service, runtime, or package).
Everything needed (JDK, Gradle, the existing PostgreSQL/Flyway stack, Timefold 1.16.0) is already a
project dependency and unchanged by this phase.

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 + AssertJ (already in use project-wide) |
| Config file | `build.gradle` (existing; no change needed) |
| Quick run command | `./gradlew test --tests "com.wfm.solver.ScheduleConstraintProviderTest"` style per-class filters, per this project's own documented hazard: **a filtered `--tests` run deletes every other class's JUnit XML — never read a suite aggregate after a filtered run** (operator memory) |
| Full suite command | `./gradlew test` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| SOLV-01/02 | Every join resolves business date; guard fails on calendar-date regression | unit (structural guard) | `./gradlew test --tests "com.wfm.service.BusinessDateJoinGuardTest"` | ❌ Wave 0 — new file (or new scan method, per D-08 discretion) |
| SOLV-03 | Break/contiguity/envelope hold across midnight at a 21:00 anchor | unit (constructed fixture, red before migration) | `./gradlew test --tests "com.wfm.solver.MidnightBoundaryRegressionTest"` | ✅ exists, extend per D-11 |
| SOLV-04 | SLOT-mode overnight stretch counts against one business day | unit (new constructed scenario) | new test class, e.g. `SlotModeOvernightContractedHoursTest` | ❌ Wave 0 — new file |
| SOLV-05 | Seat-supply shortfall keyed by business date | unit, extending existing | `./gradlew test --tests "com.wfm.service.ShiftEnvelopeSupplyGateTest"` and `ShiftEnvelopeSupplyInvariantTest`/`ShiftModeMinimumStaffingSeatSupplyTest` | ✅ exist, extend with a non-midnight-anchor case |
| SOLV-06 | Per-constraint match-count non-vacuity | unit (new assertion table + reflection check) | new test class, e.g. `ConstraintMatchCountNonVacuityTest`, mirroring `ScheduleConstraintClassificationTest`'s reflection pattern | ❌ Wave 0 — new file |
| SOLV-07 | Demand/coverage/solver agree on business date | unit (structural guard, 3 files per D-08) + explicit decision on `StaffingRequirementService` | same `BusinessDateJoinGuardTest` as SOLV-02 | ❌ Wave 0 |
| BDAY-07 | Phil-US-shaped drift guard, unchanged match counts | unit (in-process fixture using `explain()`) | new test class, e.g. `PhilUsShapedDriftGuardTest` | ❌ Wave 0 — new file, 48-agent fixture |
| D-06 | `AgentDayConfig`/`AgentShiftAssignment` derivation-chain guard | unit (structural guard) | new test class, e.g. `AgentDayDerivationGuardTest` | ❌ Wave 0 |
| D-02 | 15-min save-time refusal + generation-time tiling refusal reachable | unit/integration | `DeskServiceTest` (save-time) + a new or extended `FteUploadService`/`TimeslotGeneratorService` test proving `requireDayStartTiles` fires through the real upload path | Save-time: ✅ extend; generation-time reachability proof: ❌ Wave 0 |

### Sampling Rate

- **Per task commit:** targeted `./gradlew test --tests "<TestClass>"` for the constraint(s)/file
  just touched (being mindful of the JUnit-XML-deletion hazard — do not treat a filtered run's
  absence of other classes' results as those tests having failed or been skipped).
- **Per wave merge:** `./gradlew test` (full suite) — required before any wave is considered
  closed, since this phase's risk is a *silent* non-join (same score, wrong behavior), which only a
  full-suite green (including the three existing guard tests and the extended `MidnightBoundary*`
  suite) can catch.
- **Phase gate:** Full suite green, plus a manual re-grep of `PENDING_DESK_ANCHOR` and the
  `.getDate()` corrected-count list above, before `/gsd-verify-work` — both counts are known to
  drift as the tree moves (CONTEXT.md's own stated practice).

### Wave 0 Gaps

- [ ] `BusinessDateJoinGuardTest.java` (or 4th scan in `MidnightTimeArithmeticGuardTest`) — covers
      SOLV-02, SOLV-07
- [ ] `AgentDayDerivationGuardTest.java` (name per this research's suggestion) — covers D-06
- [ ] `SlotModeOvernightContractedHoursTest.java` (or similar) — covers SOLV-04
- [ ] `ConstraintMatchCountNonVacuityTest.java` (or similar), reusing
      `ScheduleConstraintClassification`'s reflection technique — covers SOLV-06
- [ ] `PhilUsShapedDriftGuardTest.java` (or similar), 48-agent in-process fixture reading
      `solutionManager.explain()` — covers BDAY-07
- [ ] `MidnightBoundaryFixture` extension to a `21:00` anchor (D-11) — covers SOLV-01/02/03 tracer
- [ ] Generation-time tiling-refusal reachability test through the real
      `DeskService.setDayStart` → `FteUploadService` → `TimeslotGeneratorService.requireDayStartTiles`
      path — covers D-02/ROADMAP criterion 6

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | No | Unchanged by this phase — no auth surface touched |
| V3 Session Management | No | Unchanged |
| V4 Access Control | No | `DeskService.setDayStart`'s tenant-scoped lookup (`deskRepository.findByIdAndTenantId`) is unchanged by D-01/D-02; no new endpoint or role surface |
| V5 Input Validation | Yes | D-02's new 15-minute-boundary refusal IS input validation — `setDayStart` must reject any `LocalTime` not a multiple of 15 minutes, naming the rejected value, mirroring the existing `IllegalArgumentException` pattern already used for the `00:00`-only gate |
| V6 Cryptography | No | Not applicable |

### Known Threat Patterns for this stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Operator sets a day-start value that silently desyncs the solver's view of "today" from the UI's (a correctness/availability concern, not a confidentiality one) | Tampering (of scheduling data, not security-relevant data) | D-02's save-time refusal plus D-08's structural guard are the mitigation already designed; no additional security control needed — this is a data-correctness defect class, not a security vulnerability class |
| A future constraint re-introduces a calendar-date join silently (regressing SOLV-01/02) | Tampering (code-level, not an external attacker) | `BusinessDateJoinGuardTest`'s build-time failure is the mitigation; this is the same class of "guard, not convention" control already used four times in this codebase |

No new attack surface (no new endpoint, no new input field beyond the already-existing
`PUT /desks/{id}/day-start` whose accepted range this phase widens under an already-existing
validation discipline). The security-relevant work in this phase is entirely the V5 input
validation on the widened day-start range, which D-02 already specifies.

## Sources

### Primary (HIGH confidence — direct reads of current HEAD source, this session)

- `src/main/java/com/wfm/solver/ScheduleConstraintProvider.java` (1387 lines, read in full this
  session) — every join/groupBy/anchor site cited above
- `src/main/java/com/wfm/service/SolverService.java` (relevant sections read: ~170-460, 820-1040,
  1280-1740) — `computeAgentDayConfigs`, `buildShiftAssignments`, `computeCapacityWarnings`,
  `requireShiftEnvelopeSeatSupply`, `coveredTimeslotsOnDate`, `forcedAgentDaysByTimeslotId`
- `src/main/java/com/wfm/service/ScheduleOutputService.java` (relevant sections read) — grouping
  sites and display-label sites
- `src/main/java/com/wfm/service/ShiftLibraryGenerationService.java` (relevant sections read) —
  all four `getDate()` sites
- `src/main/java/com/wfm/service/StaffingRequirementService.java` (relevant sections read) —
  demand-upload date handling
- `src/main/java/com/wfm/service/DeskService.java` (lines 195-258 read) — `setDayStart` gate
- `src/main/java/com/wfm/service/TimeslotGeneratorService.java` (relevant sections read) —
  `requireDayStartTiles`, its caller chain through `FteUploadService`
- `src/main/java/com/wfm/model/AgentDayConfig.java`, `AgentShiftAssignment.java`, `Timeslot.java`,
  `AgentAssignment.java`, `Schedule.java` (read in full or in relevant part)
- `src/main/resources/db/migration/V41__agent_shift_assignment.sql` (read in full)
- `src/test/java/com/wfm/service/MidnightTimeArithmeticGuardTest.java` (lines 1-330 read) —
  scanning mechanism, receiver heuristic, both-directions machinery
- `src/test/java/com/wfm/service/BusinessDateWritePathGuardTest.java` (lines 1-90 read) —
  two-allowlist write-path pattern
- `src/test/java/com/wfm/solver/MidnightBoundaryFixture.java` (relevant sections read) — scenario
  structure, hardcoded `MIDNIGHT_WINDOW`
- `src/test/java/com/wfm/solver/ScheduleConstraintClassification.java` and
  `ScheduleConstraintClassificationTest.java` (read) — reflection-based completeness precedent
- `src/test/resources/midnight-time-arithmetic.md`, `midnight-boundary-scenarios.md` (read in full)
- `frontend/src/pages/DeskManagement.tsx` (lines 95-130 read)
- `.planning/phases/20-solver-business-date-correctness/20-CONTEXT.md`,
  `.planning/REQUIREMENTS.md`, `.planning/STATE.md`, `.planning/ROADMAP.md` (read in full/relevant
  sections)

### Secondary (MEDIUM confidence)

- [Timefold Solver — Score calculation / Constraint Streams docs](https://docs.timefold.ai/timefold-solver/latest/constraints-and-score/score-calculation)
  — cross-checked the "no Penta stream, Quad is the maximum arity" claim already made repeatedly in
  this codebase's own comments

### Tertiary (LOW confidence)

None — every substantive claim in this document is either a direct code read (HIGH) or a
documentation cross-check (MEDIUM), consistent with this phase's nature as a pure internal
migration with no external-library unknowns.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — no new library; existing Timefold/JUnit/AssertJ stack confirmed unchanged
- Architecture (join/anchor site classification): HIGH — every site classified by direct code
  read of the enclosing stream's lead type, not by name inference
- Pitfalls: HIGH — each pitfall is a verified discrepancy between CONTEXT.md's claimed counts and
  this session's direct count, not a speculative risk
- SOLV-04 premise: HIGH — verified structurally via `AgentAssignment`'s planning-variable shape and
  `contractedHoursOver`/`Under`'s existing calendar-date grouping, not merely trusted from the
  ROADMAP's own framing
- SOLV-05 defect mechanism: HIGH — verified via direct read of `requireShiftEnvelopeSeatSupply`'s
  two map-building statements and their key types
- Guard-design recommendations (D-08/D-09 receiver heuristic, D-12 assertion-table mechanism):
  MEDIUM — grounded in existing proven precedents in this codebase, but the specific implementation
  (new class vs. 4th scan; collector-based vs. join-reorder fix for `honourPreferredBreakTime`) is
  this research's recommendation, not a locked decision — flagged as Open Questions 1-2

**Research date:** 2026-10-01
**Valid until:** Until the tree moves on `ScheduleConstraintProvider.java`,
`SolverService.java`, `ShiftLibraryGenerationService.java`, or `StaffingRequirementService.java` —
re-grep every cited line number before planning if any commit touches these files between now and
plan execution (matching this project's own established practice for these exact counts).
