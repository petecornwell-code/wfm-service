# Phase 17: Consistency Constraint & Drift Reporting - Research

**Researched:** 2026-09-04
**Domain:** Timefold constraint-stream soft constraint + read-side reporting, on an existing Java/Spring Boot + React codebase
**Confidence:** HIGH

## Summary

This phase adds two new soft Timefold constraints (usual-shift consistency, and a shift-granularity
preferred-start-time tie-break) plus a derived-on-read drift report, on top of an already-mature
constraint provider (22 existing `@ConstraintWeight` fields, 21 registered constraints as of this
session) and an already-mature report layer (`ScheduleOutputService`/`ScheduleDetailResponse`/
`ScheduleExportService`/`ScheduleResults.tsx`). `17-CONTEXT.md`'s 14 decisions (D-01…D-14) already
resolved every open design question this phase has; this document's job — per the phase's own
"Research needed at plan time" instruction — is to verify those decisions against the live
codebase, not to re-litigate them, and to hand the planner exact file/line targets and verified
code idioms.

Everything CONTEXT.md asserted about the codebase was independently re-verified this session by
reading the cited files and running `git show` on the four reverted commits: V38's exact SQL and
comment text, the current absence of `consistentStartWeight` from `ConstraintWeights.java`, the
current (unpatched) `isBefore`-only form of `honourPreferredStartTime` and its `SHIFT`-mode gate,
the 22-row `ConstraintWeightsPage.tsx` `CONSTRAINTS`/`DEFAULTS` arrays, the `ScheduleOutputService`/
`ScheduleDetailResponse`/`ScheduleExportService` plumbing shapes, and V47 as the correct current
migration head. One material finding this research adds beyond CONTEXT.md: **`Schedule` currently
has no problem-fact list carrying resolved usual-shift targets** — `AgentUsualShift` is a
per-weekday catalog row with no date, and `UsualShiftResolutionService.resolve(stored, date)` calls
`ShiftTemplateRepository` directly, which cannot run inside a `ConstraintStream` lambda. The
solver-input plumbing CONTEXT.md's Integration Points section flags as "the phase's one genuinely
new solver-input plumbing" must therefore **pre-resolve** usual-shift targets into dated records
before solving, mirroring `SolverService.resolvePreferences`'s existing pattern exactly — not a new
kind of thing, but new code, and it is the one piece of wiring most likely to be under-scoped if a
plan reads D-11's "reads at read time" language and assumes the same laziness applies inside the
solver.

**Primary recommendation:** Build the shared distance function as a static, four-scalar overload on
`ShiftBandPair` (mirroring its own `covers(...)` precedent exactly), add `AgentDayConfig`-style
pre-solve resolution in `SolverService` producing a new small record type as an
`@ProblemFactCollectionProperty`, write the new constraint against `AgentShiftAssignment` (not
`AgentAssignment` — the reverted commits operated one architecture generation too early), and follow
`ScheduleOutputService.buildPreferenceReport`/`ConstraintWeightsService` byte-for-byte for the two
plumbing chains.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Consistency penalty (solver scoring) | API/Backend (Timefold `ConstraintProvider`) | — | Pure scoring logic; no client or DB role |
| Distance calculation (shared function) | API/Backend (`com.wfm.model` static helper) | — | Must be callable from both the constraint stream (problem facts only) and the report service (JPA entities) with zero divergence — lives as a static method on a plain model class, not a Spring bean |
| Tolerance-band / weight config | API/Backend (persistence + validation) | Browser/Client (`ConstraintWeightsPage.tsx` form) | Per-desk config is a stored row (`constraint_weights`); the client only edits and displays it |
| Usual-shift target resolution for solving | API/Backend (`SolverService` pre-solve step) | — | Must happen before the solver runs since era resolution needs a repository call the constraint stream cannot make |
| Drift report | API/Backend (`ScheduleOutputService`, derived on read) | Browser/Client (`ScheduleResults.tsx` Drift Report tab) | Same house pattern as `buildPreferenceReport` — no new table, no migration |
| Excel export sheet | API/Backend (`ScheduleExportService`, POI) | — | Document-generation code path, not interactive |

## Standard Stack

No new library is introduced by this phase. Every dependency already exists in the codebase and is
pinned.

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `ai.timefold.solver:timefold-solver-bom` | **1.16.0** [VERIFIED: build.gradle:52] | Constraint-stream solver | Already pinned project-wide; `ScoreAnalysis`/paid-tier concerns at 2.0 are an existing, settled project decision (STATE.md), not a Phase 17 question |
| `ai.timefold.solver:timefold-solver-jpa` | bundled with BOM | `HardSoftScoreConverter` for `@Convert`-mapped `HardSoftScore` columns | Already used by every existing `ConstraintWeights` field |
| `ai.timefold.solver:timefold-solver-test` | bundled with BOM | `ConstraintVerifier` for isolated constraint unit tests | Already the house pattern — see `BandCapacityConstraintTest.java`, `ShiftWorkContiguityConstraintTest.java` |
| Apache POI (`org.apache.poi:poi*`) | existing pinned version | Excel export (`ScheduleExportService`) | Already used for `writePreferenceReport`; the new drift sheet is one more `Sheet` in the same workbook |

### Supporting

No new supporting library. Frontend stays hand-rolled inline-`style` React/TS (no component
library), per the UI-SPEC's reconfirmed standing decision.

### Alternatives Considered

Not applicable — this phase's own requirements (CONS-01…06, DRFT-01…04) and CONTEXT.md's D-01…D-14
already selected the implementation approach (target-deviation Timefold soft constraint +
derived-on-read report). No package-selection decision remains open.

**Installation:** None. No `build.gradle` or `package.json` dependency changes.

**Version verification:** `ai.timefold.solver:timefold-solver-bom:1.16.0` confirmed by direct read
of `build.gradle:52` this session [VERIFIED: build.gradle:52] — matches STATE.md's recorded pin and
`.planning/phases/15-*/15-BENCHMARK.md`'s harness config, which also states `Timefold version:
1.16.0`.

## Package Legitimacy Audit

**Not applicable — no external packages are installed by this phase.** Every library used
(Timefold BOM, POI, React/TS toolchain) is already a pinned, in-use dependency. No `npm install` /
`pip install` / registry lookup is required.

## Architecture Patterns

### System Architecture Diagram

```
                     ┌─────────────────────────────────────────────┐
                     │  SolverService.solve(...)  (pre-solve)       │
                     │                                               │
  AgentUsualShift ──▶│  resolveUsualShiftTargets(...)                │  NEW — mirrors
  (per-weekday,       │    for each (agent, working date):            │  resolvePreferences'
   catalog FK)        │      UsualShiftResolutionService.resolve(     │  shape: raw catalog
                     │        stored, date) -> Optional<ShiftTemplate>│  rows in, dated
                     │    -> List<ResolvedUsualShiftTarget>           │  resolved records out
                     │       (agentId, date, usualStartTime)          │
                     └───────────────┬───────────────────────────────┘
                                     │ @ProblemFactCollectionProperty
                                     ▼
                     ┌─────────────────────────────────────────────┐
                     │  ScheduleConstraintProvider                   │
                     │                                                │
  AgentShiftAssignment──▶ usualShiftConsistency(factory)              │  NEW constraint —
  (shiftBandPair =     │    forEachIncludingUnassigned(               │  target-deviation,
   planning variable)  │      AgentShiftAssignment.class)             │  shares distance fn
                     │    .join(ResolvedUsualShiftTarget, agent+date) │  with drift report
                     │    .filter(excess past tolerance band)         │
                     │    .penalizeConfigurable(excess increments)    │
                     │                                                │
  AgentPreference   ──▶ preferredStartShiftMode(factory)              │  NEW constraint —
  (preferredStartTime) │    forEach(AgentShiftAssignment)             │  D-08's tie-break,
                     │    .join(AgentPreference, agent+date)          │  own weight column,
                     │    .penalizeConfigurable(anchor-style delta)   │  save-time rejected
                     │                                                │  below consistency
                     └───────────────┬───────────────────────────────┘
                                     │ shared static distance fn
                                     │ ShiftBandPair.driftMinutes(...)
                                     ▼
                     ┌─────────────────────────────────────────────┐
                     │  ScheduleOutputService.buildDriftReport(...)  │  NEW — derived on READ,
                     │    for each AgentShiftAssignment:              │  same pattern as
                     │      resolve target via                       │  buildPreferenceReport
                     │      UsualShiftResolutionService.resolve(...)  │  (no snapshot table)
                     │      -> NO_USUAL_SHIFT / HONOURED / DRIFTED    │
                     │    + popularity ranking (reads AgentUsualShift │
                     │      directly, NOT solve results — D-13)       │
                     └───────┬────────────────────┬──────────────────┘
                             │                     │
                             ▼                     ▼
              ScheduleDetailResponse      ScheduleExportService
              .driftReport (JSON)         .writeDriftReport (new sheet,
                             │              headers byte-identical to UI)
                             ▼
              ScheduleResults.tsx
              new "Drift Report" tab
              (DriftTab component, mirrors PreferenceTab)
```

### Recommended Project Structure

No new packages or directories — every new file lands beside its existing sibling.

```
src/main/resources/db/migration/
└── V48__*.sql                                   # tolerance-band column, new pref weight column,
                                                    # consistentStartWeight backfill/default (D-06)

src/main/java/com/wfm/model/
├── ConstraintWeights.java                        # +consistentStartWeight (V38 orphan), +tolerance
│                                                   #  band int column, +new preference weight
├── ShiftBandPair.java                             # +static driftMinutes(...) overload — the ONE
│                                                   #  distance calculation (DRFT-03)
└── ResolvedUsualShiftTarget.java (NEW, small record)  # agentId, date, usualStartTime — problem fact

src/main/java/com/wfm/solver/ScheduleConstraintProvider.java
                                                    # +usualShiftConsistency(factory)
                                                    # +preferredStartShiftMode(factory)  (D-08)

src/main/java/com/wfm/service/
├── SolverService.java                             # +resolveUsualShiftTargets(...) pre-solve step
├── ScheduleOutputService.java                      # +buildDriftReport(schedule)
├── ScheduleService.java                            # wire + date-filter driftReport (mirror :220-224)
├── ScheduleExportService.java                      # +writeDriftReport(...) sheet
└── ConstraintWeightsService.java                   # +save-time D-07/D-08 validation, +3 new fields

src/main/java/com/wfm/dto/
├── ConstraintWeightsDto.java                       # +3 fields (2 ScoreDto, 1 plain int)
└── ScheduleDetailResponse.java                     # +DriftReport/DriftReportEntry/DriftSummary/
                                                    #  ShiftPopularityEntry records + field

src/test/java/com/wfm/solver/ScheduleConstraintClassification.java   # +2 rows (MODE_GATED)

frontend/src/pages/
├── ScheduleResults.tsx                             # +'drift' tab, +DriftTab component
└── ConstraintWeightsPage.tsx                       # +3 CONSTRAINTS/DEFAULTS rows, +tolerance-band
                                                    #  rendering branch (non-Score field)
frontend/src/api/client.ts                          # +DriftReport* TS interfaces, +driftReport field
```

### Pattern 1: One computation, multiple callers (D-08's `ShiftBandPair.covers` precedent)

**What:** A distance/coverage predicate is written exactly once as a static method taking plain
scalars, and every caller — solver constraint or report service — calls that same static method.
The instance-level convenience method (if any) delegates to it with zero behavioural difference.

**When to use:** Whenever a value must be computed identically by the constraint provider (which
only has problem facts / planning variables in scope) and by the report/export layer (which has
live JPA entities or accepted-schedule denormalised columns in scope).

**Example — the exact precedent to model the new distance function on** (already in this codebase):

```java
// Source: src/main/java/com/wfm/model/ShiftBandPair.java (verified this session)
public boolean covers(Timeslot ts) {
    return covers(template.getStartTime(), template.getEndTime(),
            band == null ? null : band.getOffsetMinutes(),
            band == null ? null : band.getDurationMinutes(),
            ts.getStartTime(), ts.getEndTime());
}

// The static form other callers (report layer) use directly, since they hold only scalars:
public static boolean covers(LocalTime envelopeStart, LocalTime envelopeEnd,
        Integer bandOffsetMinutes, Integer bandDurationMinutes,
        LocalTime slotStart, LocalTime slotEnd) {
    if (slotStart.isBefore(envelopeStart) || slotEnd.isAfter(envelopeEnd)) {
        return false;
    }
    if (bandOffsetMinutes == null || bandDurationMinutes == null || bandDurationMinutes <= 0) {
        return true;
    }
    LocalTime breakStart = envelopeStart.plusMinutes(bandOffsetMinutes);
    LocalTime breakEnd = breakStart.plusMinutes(bandDurationMinutes);
    boolean overlapsBreak = slotStart.isBefore(breakEnd) && slotEnd.isAfter(breakStart);
    return !overlapsBreak;
}
```

**Recommended shape for D-01's drift distance function**, following this exactly (same file,
`ShiftBandPair.java`, since D-03 makes the "start" comparison a `ShiftBandPair`-scoped concept —
`shiftBandPair.template().getStartTime()`):

```java
/** The ONE distance calculation DRFT-03 requires — constraint and report both call this. */
public static int startDeviationMinutes(LocalTime assignedEnvelopeStart, LocalTime usualStartTime) {
    if (assignedEnvelopeStart == null || usualStartTime == null) return 0;
    return (int) Math.abs(ChronoUnit.MINUTES.between(usualStartTime, assignedEnvelopeStart));
}
```

Both the constraint (which has `sa.getShiftBandPair().template().getStartTime()` and a resolved
`ResolvedUsualShiftTarget.usualStartTime()`) and `buildDriftReport` (which has
`resolveShiftDescriptor(sa).startTime()` and `UsualShiftResolutionService.resolve(...).get()
.getStartTime()`) call this identically.

### Pattern 2: Pre-solve resolution into a dated record, fed as a problem fact

**What:** Raw catalog data that needs date-aware precedence resolution (weekly-vs-standing,
era-by-name) is resolved **before** the solver runs, in `SolverService`, into a flat list of dated
records that carry no further resolution logic — the constraint stream only ever does an
`equal(agentId, date)` join against pre-resolved facts.

**When to use:** Any time a constraint needs a value whose correct resolution requires a repository
call or non-trivial precedence logic that cannot run inside a `ConstraintStream` lambda (Timefold
lambdas run millions of times during search and must be pure/cheap).

**Verified existing precedent — `SolverService.resolvePreferences`:**

```java
// Source: src/main/java/com/wfm/service/SolverService.java:567 (verified this session, signature only)
private List<AgentPreference> resolvePreferences(List<AgentPreference> allPreferences,
                                                 Schedule schedule,
                                                 Map<UUID, Set<LocalDate>> agentDaysOffMap) {
    // ... indexes standing vs weekly, resolves precedence per (agent, date) across the whole
    // schedule period, returns date-specific AgentPreference objects with isStanding=false
    // and an exact date set, so downstream constraints never re-resolve precedence.
}
```

**This is the pattern the usual-shift target resolution must follow — NOT a raw
`@ProblemFactCollectionProperty List<AgentUsualShift>`.** `AgentUsualShift` is keyed by
`(agent, dayOfWeek)` with no date and no era resolution; feeding it raw would force the constraint
stream to re-implement `UsualShiftResolutionService`'s era-by-name logic inside a lambda, which
duplicates the "ONE implementation" rule that service's own javadoc states explicitly (verified:
`UsualShiftResolutionService.java`, "Do not create a second copy of this method").

**Recommended new record and wiring point:**

```java
// New small model class, com.wfm.model — a plain immutable record, same style as
// AgentDayConfig/ScheduleConfig/TimeslotDemandConfig (verified: ShiftBandPair.java javadoc
// names this house convention explicitly — "Every value the solver reads that is not a genuine
// planning variable is a plain immutable record in this codebase").
public record ResolvedUsualShiftTarget(UUID agentId, LocalDate date, LocalTime usualStartTime) {}
```

```java
// Schedule.java gains:
@ProblemFactCollectionProperty
private List<ResolvedUsualShiftTarget> resolvedUsualShiftTargets = new ArrayList<>();
```

```java
// SolverService, new private method mirroring resolvePreferences's shape and call site
// (called from the same pre-solve block that already calls resolvePreferences, around
// SolverService.java:248):
private List<ResolvedUsualShiftTarget> resolveUsualShiftTargets(
        List<AgentUsualShift> allUsualShifts, Schedule schedule,
        Map<UUID, Set<LocalDate>> agentDaysOffMap, List<AgentDayConfig> agentDayConfigs) {
    // Index AgentUsualShift by (agentId, dayOfWeek); for each AgentDayConfig with
    // effectiveHours > 0 (working day — mirrors AgentShiftAssignment's own creation gate),
    // resolve via usualShiftResolutionService.resolve(stored, date) and, if present, emit one
    // ResolvedUsualShiftTarget(agentId, date, template.getStartTime()).
    // An agent with no stored row for that weekday emits nothing — USHF-04's "no penalty" case
    // falls out naturally: no problem fact means the join in usualShiftConsistency finds no
    // match and produces no tuple, matching CONS-04/D-02's requirement with zero special-casing.
}
```

### Pattern 3: Timefold stream-ordering performance contract for a SHIFT-mode-only constraint

**What:** A constraint that only ever fires on shift-scheduled desks must lead with the
`AgentShiftAssignment` stream (structurally empty on a SLOT-mode desk) and apply the
`SchedulingMode.SHIFT` gate before joining anything else — never lead with the larger
`AgentAssignment` stream and filter down.

**When to use:** Both new constraints in this phase (`usualShiftConsistency`,
`preferredStartShiftMode`) are SHIFT-mode-only by nature (usual shifts and the new preference
tie-break only make sense where an envelope exists), so both must follow this contract.

**Verified existing precedent, with the measured cost of getting it backwards:**

```java
// Source: src/main/java/com/wfm/solver/ScheduleConstraintProvider.java:422 (verified this session)
Constraint shiftEnvelopeCompliance(ConstraintFactory factory) {
    return factory.forEachIncludingUnassigned(AgentShiftAssignment.class)
            .join(ScheduleConfig.class)
            .filter((sa, cfg) -> cfg.schedulingMode() == SchedulingMode.SHIFT)
            .join(AgentAssignment.class,
                    equal((sa, cfg) -> sa.getAgent().getId(), a -> a.getAgent().getId()),
                    equal((sa, cfg) -> sa.getDate(), a -> a.getTimeslot().getDate()))
            .filter((sa, cfg, a) -> sa.getShiftBandPair() == null
                    || !sa.getShiftBandPair().covers(a.getTimeslot()))
            .penalizeConfigurable()
            .asConstraint(SHIFT_ENVELOPE_COMPLIANCE_CONSTRAINT_NAME);
}
```

The javadoc directly above this method (verified, same file) states the measured cost of the
wrong order: leading with `AgentAssignment` and joining shift rows second cost "roughly 3x
construction-heuristic throughput (1049ms -> 347ms for the same 480 steps)" on the reused
benchmark fixture, and the comment ends "Do not reorder these joins." The two new constraints in
this phase must lead with `AgentShiftAssignment` (or `AgentPreference`/`ResolvedUsualShiftTarget`
joined onto it) for the identical reason — never lead with a per-slot `AgentAssignment` stream.

### Anti-Patterns to Avoid

- **Reviving the reverted `9207ceb`'s `consistentDailyStart` shape.** That constraint operates on
  `AgentAssignment` (per-timeslot seats) directly, groups to a day's earliest slot, then groups
  *again* across the week to `min`/`max`, and penalises the **spread** between an agent's best and
  worst day. Its own javadoc (verified, `git show 9207ceb`) states the flaw: "The search sees a
  plateau, not a gradient... an agent perfect Monday to Wednesday and three hours out on Thursday
  costs exactly what a scattered week costs." D-01/D-02 require **target-deviation** (every day
  compared against `AgentUsualShift`'s stored target, penalised per agent-day, not per-agent
  spread) specifically to avoid this. It is also architecturally stale: it predates
  `AgentShiftAssignment` entirely and reads `AgentAssignment.getTimeslot().getStartTime()`
  directly, whereas D-03 requires the **assigned envelope start**
  (`shiftBandPair.template().getStartTime()`), which did not exist when `9207ceb` was written.
- **Reviving `9207ceb`'s Java/DTO/frontend `ConstraintWeightsPage.tsx` diff verbatim.** The literal
  diff (verified via `git show`) adds only `consistentStartWeight`, a plain `ScoreDto`/`HardSoftScore`
  field — it has no tolerance-band concept at all (D-04 is new to Phase 17; D-04's own text calls
  out that the reverted attempt never faced this problem). The **plumbing shape** (one field added
  in four files, per commit `9207ceb`'s stat: `ConstraintWeightsPage.tsx` +2, `ConstraintWeightsDto`
  +3, `ConstraintWeights.java` +17, `ConstraintWeightsService.java` +4) transfers; the literal patch
  does not apply cleanly (V38 has since moved past V39, and current `ConstraintWeights.java` has 22
  fields, not the ~19 present when `9207ceb` was written).
- **Treating `honourPreferredStartTime` (`7861b83`'s target) as ready to receive the anchor-not-floor
  patch directly.** Verified this session: current HEAD's `honourPreferredStartTime`
  (`ScheduleConstraintProvider.java:813`) still has the pre-`7861b83` `isBefore`-only form AND is
  now `SHIFT`-mode-gated OFF (`ifExists(ScheduleConfig.class, filtering(cfg.schedulingMode() !=
  SchedulingMode.SHIFT))`, added by Phase 15's ENVL-05/P-26 — a change `7861b83` predates and never
  saw). Applying `7861b83`'s patch as-is would edit a constraint that structurally cannot fire on the
  shift-scheduled desks CONS-05 is about. D-08 correctly calls for a **new** constraint at shift
  granularity; only `7861b83`'s **idea** (penalise absolute deviation in both directions, "anchor not
  floor") transfers, not the patch.
- **Feeding `AgentUsualShift` directly to the constraint stream as a problem fact.** See Pattern 2 —
  it has no date and no era resolution; the constraint would either need to duplicate
  `UsualShiftResolutionService`'s logic inline (violating its own "do not create a second copy"
  rule) or silently use the *stored* template rather than the era-resolved one, breaking Phase 16's
  D-01 promise that "Ana's usual shift is Early" follows Early across template eras.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Envelope-start-vs-target distance | A second ad hoc `Math.abs(ChronoUnit.MINUTES.between(...))` in the report layer | `ShiftBandPair.startDeviationMinutes(...)` (new static method) called from both the constraint and `buildDriftReport` | DRFT-03 is a literal requirement, not a style preference — a second implementation is exactly what audit-class divergences (v1.2 I-1/I-2) come from |
| Era-aware usual-shift lookup | Any new resolution logic anywhere | `UsualShiftResolutionService.resolve(stored, date)` — verified as already used by the roster read and the export, and its own javadoc explicitly names this phase's drift report as an intended caller | It is already the one, tested implementation; Phase 16's javadoc states "Do not create a second copy of this method" |
| Weight-vs-tolerance validation before shipping a default | Guessing a default and shipping it | `SolutionManager.explain()`'s `getConstraintMatchTotalMap()` — already used at `SolverService.java:1830` (`runPreSolveScoreDiagnostic`) and `ScheduleOutputService.java:464` | Existing, already-proven mechanism for exactly this purpose; D-06 requires reading this breakdown before committing the migration default |
| A/B benchmark harness | A new harness from scratch | `ShiftModeFixtures` + the `ShiftModelBenchmarkTest` pattern from Phase 15 (`15-BENCHMARK.md`), gated `@EnabledIfSystemProperty("wfm.benchmark")`, step-count-terminated, seeded, median+spread reported | Phase 15 already built and proved this harness; XCUT-04's discipline (committed threshold before the run) is documented there verbatim and this phase inherits the same obligation |

**Key insight:** Every "don't hand-roll" item in this phase is really the same rule restated —
this codebase already has exactly-once implementations for shift-start distance, era resolution,
weight-validation, and benchmarking, and each was hard-won (V38's arithmetic comment, Phase 16's
javadoc warning, Phase 15's `15-BENCHMARK.md`). Phase 17's job is to extend these, not parallel them.

## Runtime State Inventory

Not applicable — Phase 17 is net-new feature work (new constraint, new report), not a rename,
refactor, or migration of existing data. No existing stored data, live service config, or
OS-registered state changes name or shape. (D-06's migration backfills `consistent_start_weight`'s
default value on existing rows, which is an ordinary Flyway `UPDATE`/`ALTER ... DEFAULT`, not a
rename requiring this inventory.)

## Common Pitfalls

### Pitfall 1: Building the new constraint against `AgentAssignment` instead of `AgentShiftAssignment`

**What goes wrong:** The reverted commits (pre-Phase-15) had no `AgentShiftAssignment` to compare
against and worked entirely from per-timeslot `AgentAssignment` rows. A plan that copies their
`groupBy` shape without noticing this will produce a constraint measuring the *seat-derived*
earliest slot rather than the *assigned envelope start* — exactly the D-03 "blind spot" CONTEXT.md
warns about (an agent on the Early envelope whose first seat falls 90 minutes in would then read
as 90 minutes of drift, when D-03 says it must read as zero).

**Why it happens:** The `groupBy(agent, date, ...)` pattern "is confirmed to compile from the
reverted commits" (per ROADMAP.md's own research note) — true, but it compiles against the wrong
entity for this phase's D-03 decision.

**How to avoid:** Lead the new constraint with `factory.forEachIncludingUnassigned(
AgentShiftAssignment.class)` (matching `shiftEnvelopeCompliance`'s stream-ordering contract,
Pattern 3 above), and read `sa.getShiftBandPair().template().getStartTime()` for the assigned
start — never `AgentAssignment.getTimeslot().getStartTime()`.

**Warning signs:** A test asserting drift changes when seats move within an unchanged envelope; a
constraint that groups `AgentAssignment` by `(agent, date)` and takes `min(startTime)`.

### Pitfall 2: Forgetting the `ScheduleConstraintClassificationTest` completeness guard

**What goes wrong:** Adding two new constraints (with `@ConstraintWeight` annotations and
`Constraint`-returning builder methods) without adding two corresponding rows to
`ScheduleConstraintClassification.classifications()` fails the build — by design (verified: the
test derives the constraint set by reflection over both `ConstraintWeights`'s annotations and
`ScheduleConstraintProvider`'s builder methods, and asserts the map's key set agrees with both).

**Why it happens:** It is easy to treat this as a Phase-14/15-only artifact since it lives in
`src/test/`, not `src/main/`.

**How to avoid:** Add both new rows (`"Usual shift consistency"`, and D-08's new preference
constraint name) to `ScheduleConstraintClassification.classifications()` as `MODE_GATED` — both are
structurally inert on a SLOT-mode desk, matching `shiftEnvelopeCompliance`'s classification.

**Warning signs:** `ScheduleConstraintClassificationTest` fails immediately after adding the new
constraint methods; the failure message names the missing constraint string.

### Pitfall 3: `ConstraintWeightsPage.tsx`'s generic `Record<string, Score>` cast breaks on the tolerance-band field

**What goes wrong:** The current page (verified, `ConstraintWeightsPage.tsx:99`) reads every row
via `const score = (weights as Record<string, Score>)[key] || DEFAULTS[key]` and always renders two
`hardScore`/`softScore` number inputs. The tolerance-band field is a **plain integer**, not a
`Score` — feeding it through this cast either throws (no `hardScore`/`softScore` properties) or
silently renders `undefined` inputs.

**Why it happens:** Every one of the 22 existing rows is a `Score`; this is the page's first
non-`Score` field ever (UI-SPEC's own framing: "the only non-`ScoreDto` field among 22 score
fields").

**How to avoid:** The tolerance-band row needs its own render branch, not a `CONSTRAINTS.map(...)`
iteration entry treated identically to the rest — per UI-SPEC's Component Specifications §3,
render it as a `<tr>` with a merged `colSpan={2}` single `<input type="number">` cell, read/written
through its own state path (`consistencyToleranceMinutes: number`), never through the
`(weights as Record<string, Score>)` cast.

**Warning signs:** A TypeScript compile error on the tolerance-band field, or a runtime
`Cannot read properties of undefined (reading 'hardScore')`.

### Pitfall 4: V38's per-agent arithmetic silently inherited as per-agent-day

**What goes wrong:** V38's default `0hard/2soft` was sized (per its own committed comment, verified
this session) for a **per-agent** penalty: "on the live desk's 28 CSRs a four-increment spread each
is `28 x 4 x 2 = 224 soft`." D-02's target-deviation formulation charges **per agent-day**. The
same desk, same weight, five working days: `28 x 5 x 4 x 2 ≈ 1,120 soft` — which now sits *above*
`minStaffingWeight`'s `1000`, meaning the solver could rationally trade uncovered staffing hours for
consistency. This is precisely the failure mode V38's comment was written to prevent, and it is
invisible unless the arithmetic is redone for the new charging model.

**Why it happens:** `consistent_start_weight` already exists in the schema with a shipped-looking
default; it is tempting to treat the value as settled rather than as sized for a discarded
formulation.

**How to avoid:** D-06 already mandates this: run the seeded A/B benchmark, read
`SolutionManager.explain()`'s constraint-match breakdown against the live soft hierarchy, and only
then write the migration's default. Do not carry `2` forward unexamined.

**Warning signs:** A benchmark run (or `explain()` breakdown) showing consistency's total penalty
exceeding `minStaffingWeight`'s or `unassignedAssignmentWeight`'s soft contribution on a
realistically-loaded desk.

### Pitfall 5: Save-time validation added to `ConstraintWeightsService.updateWeights` without checking `null`-partial-update semantics

**What goes wrong:** `updateWeights` (verified, `ConstraintWeightsService.java`) is a
partial-update method — every field is applied only `if (updates.getXWeight() != null)`. A save-time
rejection (D-07's hard-must-be-0 check, D-08's precedence-ordering check) that reads
`updates.getConsistentStartWeight()` directly, rather than the **resulting** `weights` entity
after merge, would incorrectly pass when the client sends a partial update that only touches an
unrelated field, or incorrectly fail against a value the client never intended to change.

**Why it happens:** Every other field in this service has no cross-field invariant to check, so
there is no existing precedent in this exact file for "validate against the merged result."

**How to avoid:** Apply the merge first (as today), then validate the **resulting** `weights`
entity's `consistentStartWeight.hardScore() == 0` and
`preferredStartShiftModeWeight.softScore() < consistentStartWeight.softScore()` before calling
`constraintWeightsRepository.save(weights)` — mirroring `DeskAgentService.setDayHours`'s
"reject, don't clamp" pattern (throw `IllegalArgumentException`, mapped to 400 `VALIDATION_FAILED`
by `GlobalExceptionHandler.handleIllegalArgument`, verified at
`GlobalExceptionHandler.java:32-34`).

**Warning signs:** A test that saves only the tolerance-band field and unexpectedly gets a 400
about the preference weight, or a test that sets an invalid combination via two separate partial
PUTs and it silently succeeds.

## Code Examples

### The reverted-and-since-superseded `honourPreferredStartTime` anchor fix (idea only, not the patch)

```java
// Source: git show 7861b83 -- src/main/java/com/wfm/solver/ScheduleConstraintProvider.java
// (verified this session). Historical reference for the "anchor not floor" idea D-08 reuses —
// do NOT apply this patch; the target method has since been mode-gated off for SHIFT (Phase 15
// ENVL-05/P-26) and this patch predates that gate entirely.
private int startDeviationIncrements(List<AgentAssignment> assignments, LocalTime preferredStart) {
    if (preferredStart == null) return 0;
    LocalTime shiftStart = getShiftStart(assignments);
    if (shiftStart == null) return 0;
    long deviationMinutes = Math.abs(
            java.time.temporal.ChronoUnit.MINUTES.between(preferredStart, shiftStart));
    if (deviationMinutes == 0) return 0;
    return BigDecimal.valueOf(deviationMinutes)
            .divide(BigDecimal.valueOf(deriveIncrement(assignments)), 0, RoundingMode.CEILING)
            .intValue();
}
```

The reusable idea: absolute deviation, both directions, rounds up so no deviation is free. D-08's
new constraint should express this same shape but against `AgentShiftAssignment`'s envelope start
(D-03), not against `getShiftStart(assignments)` (a seat-derived helper that predates the envelope
model).

### Current (unpatched) `honourPreferredStartTime`, verified on HEAD — confirms it is mode-gated OFF for SHIFT

```java
// Source: src/main/java/com/wfm/solver/ScheduleConstraintProvider.java:813 (verified this session)
Constraint honourPreferredStartTime(ConstraintFactory factory) {
    return factory.forEach(AgentAssignment.class)
            .filter(a -> a.getAgent() != null)
            .join(AgentPreference.class,
                    equal(a -> a.getAgent().getId(), p -> p.getAgent().getId()),
                    equal(a -> a.getTimeslot().getDate(), AgentPreference::getDate))
            .ifExists(ScheduleConfig.class,
                    filtering((a, p, cfg) -> cfg.schedulingMode() != SchedulingMode.SHIFT))
            .filter((a, p) -> {
                if (p.getPreferredStartTime() == null) return false;
                return a.getTimeslot().getStartTime().isBefore(p.getPreferredStartTime());
            })
            .penalizeConfigurable((a, p) -> 1)
            .asConstraint("Honour preferred start time");
}
```

This is the constraint whose javadoc (also verified, same file) states: "Phase 17's CONS-05 use of
`preferredStartTime` at shift granularity is a new use, not a reason to leave this per-slot
constraint on." This confirms D-08's finding directly from the current source, not from inference.

### `ConstraintVerifier` test harness pattern for isolating a new SHIFT-mode constraint

```java
// Source: src/test/java/com/wfm/solver/BandCapacityConstraintTest.java (verified this session,
// header/setup only) — model the new consistency-constraint test on this shape.
class UsualShiftConsistencyConstraintTest {
    private final ConstraintVerifier<ScheduleConstraintProvider, Schedule> verifier =
            ConstraintVerifier.build(new ScheduleConstraintProvider(), Schedule.class,
                    AgentAssignment.class, AgentShiftAssignment.class);
    // ... verifier.forConstraint(ScheduleConstraintProvider::usualShiftConsistency)
    //         .given(...)
    //         .penalizesBy(...)
}
```

### `buildPreferenceReport`'s exact shape — the template `buildDriftReport` must mirror

```java
// Source: src/main/java/com/wfm/service/ScheduleOutputService.java:289 (verified this session,
// abridged to the structural shape)
public PreferenceReport buildPreferenceReport(Schedule schedule) {
    Map<UUID, Map<LocalDate, ShiftDescriptor>> shiftDescriptorsByAgentDate =
            buildShiftDescriptorsByAgentDate(schedule);   // <- reuse this exact helper (:636)
    // ... group AgentAssignment by (agentId, date), resolve actual start/breaks ...
    // for buildDriftReport: iterate schedule.getShiftAssignments() (AgentShiftAssignment) instead
    // of schedule.getAgentPreferences(), resolve each row's usual-shift target via
    // UsualShiftResolutionService.resolve(...), and classify NO_USUAL_SHIFT / HONOURED / DRIFTED
    // using ShiftBandPair.startDeviationMinutes(...) against the desk's tolerance band.
    return new PreferenceReport(entries, summary);
}
```

### `writePreferenceReport`'s exact shape — the template `writeDriftReport` must mirror

```java
// Source: src/main/java/com/wfm/service/ScheduleExportService.java:172 (verified this session)
private void writePreferenceReport(XSSFWorkbook workbook, CellStyle headerStyle,
                                    PreferenceReport report) {
    Sheet sheet = workbook.createSheet("Preference Report");
    Row header = sheet.createRow(0);
    String[] cols = {"Agent", "Date", "Source", "Preferred Start", "Actual Start",
                     "Start Honoured", "Preferred Break", "Actual Break", "Break Honoured"};
    // ... header cells with headerStyle ...
    if (report == null || report.entries() == null) return;   // guard-then-header-only pattern
    // ... one row per entry ...
}
```

D-14's drift sheet headers must be byte-identical to the UI table headers per the UI-SPEC:
`Agent`, `Date`, `Status`, `Usual Start`, `Actual Start`, `Delta (min)`.

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|---------------|--------|
| Spread-based consistency (`min`/`max` of daily starts across the week, penalise the spread) | Target-deviation (every agent-day compared against a stored per-weekday target) | This phase (per CONTEXT.md D-02, reformulating the reverted `9207ceb`) | Removes the "search sees a plateau, not a gradient" flaw the reverted attempt's own javadoc names; gives the solver a gradient on every agent-day rather than only on the two extreme days |
| `honourPreferredStartTime` as a per-slot, `isBefore`-only floor | (Unrelated to this phase directly, but structurally superseded for SHIFT desks) A new shift-granularity `preferredStartShiftMode` constraint (D-08), anchor-style in both directions | This phase | `honourPreferredStartTime` itself stays unpatched and mode-gated off for SHIFT (Phase 15's decision, unchanged); the "anchor not floor" idea is reused, not the constraint |
| Per-agent consistency weight sizing (V38's comment, `28 x 4 x 2 = 224`) | Per-agent-day sizing, benchmark-derived (D-06) | This phase | The inherited `2 soft` default cannot be trusted as-is; must be re-derived against the new per-agent-day charging model before shipping |

**Deprecated/outdated:**
- V38's `consistent_start_weight` column's *default value* (`2 soft`) is sized for a discarded
  per-agent formulation and must not be inherited unexamined (Pitfall 4). The column itself (name,
  type, table) is adopted as-is (ROADMAP.md's salvage note, confirmed still true this session:
  `grep` across `src/` finds zero readers of `consistent_start_weight` besides the DDL/comment
  cross-reference in V42).

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | The new preference constraint's weight-column name and the exact new-constraint string names are left to planner discretion (per CONTEXT.md's own "Claude's Discretion" note) — this document assumed illustrative names (`preferredStartShiftModeWeight`, `"Usual shift consistency"`, `"Preferred start (shift mode)"`) rather than asserting them as final | Recommended Project Structure, Code Examples | Low — CONTEXT.md explicitly defers this to the planner ("follow existing conventions... V45/V46 are the most recent models"); no behaviour depends on the literal string chosen, only that `ScheduleConstraintClassification`, `@ConstraintWeight`, and the UI copy stay consistent with whatever is chosen |
| A2 | `ResolvedUsualShiftTarget` as a new small record type is this researcher's proposed shape for the pre-solve problem fact (Pattern 2) — CONTEXT.md names the *need* for new solver-input plumbing but does not specify its exact type shape | Architecture Patterns, Pattern 2 | Low-Medium — the underlying requirement (pre-resolve before solving, since the constraint stream cannot call a repository) is verified from the codebase's own `resolvePreferences` precedent and `UsualShiftResolutionService`'s repository dependency; the specific record's field list is a reasonable but not verified-in-a-commit design |

**All other claims in this research were verified directly against the live codebase this session**
(file reads, `git show` on the four reverted commits, `grep` for the V38 orphan) or cited from
`17-CONTEXT.md`/`17-UI-SPEC.md`, both already-approved artifacts for this phase.

## Open Questions

1. **Exact final column/field/constraint names for the two new weight-bearing rows.**
   - What we know: `consistentStartWeight` is fixed (adopts the V38 orphan, per ROADMAP.md salvage
     note, D-06). The new preference constraint's name/column is explicitly left to planner
     discretion by CONTEXT.md.
   - What's unclear: Whether the planner will name it `preferredStartShiftModeWeight`,
     `shiftPreferredStartWeight`, or something else, and the exact Flyway column name.
   - Recommendation: Follow `shiftWorkContiguityWeight`/V45/V46's naming convention (verb-object,
     `snake_case` column matching the Java field via standard Spring/Hibernate conversion) — the
     planner should pick once and use it consistently across migration, entity, DTO, service, and
     frontend `CONSTRAINTS` key.

2. **Whether `ResolvedUsualShiftTarget` needs a fourth field for the tolerance-band-inclusive
   "honoured" flag precomputed, vs. leaving all classification to the constraint/report.**
   - What we know: The constraint only needs `usualStartTime` to compute deviation against the
     tolerance band (a per-desk config value it can join separately via `ConstraintWeights`/
     `ScheduleConfig`-style problem facts).
   - What's unclear: Whether resolving the tolerance band as a per-desk value inside the
     constraint stream needs its own `ConstraintFactory.forEach(ConstraintWeights.class)` join (mirroring
     how other constraints already read `ScheduleConfig` as a problem fact) — this session did not
     verify whether `ConstraintWeights` itself is already exposed as a solver problem fact or must be
     newly wired in.
   - Recommendation: Check at plan time whether `ConstraintWeights` (or a subset of its fields) is
     already a `@ProblemFactCollectionProperty`/singleton problem fact on `Schedule`; if not, the
     tolerance-band value likely needs to travel via `ScheduleConfig` (which several existing SHIFT
     constraints already join against) rather than a new fact type.

## Environment Availability

Not applicable — this phase has no external tool/service dependency beyond the already-running
Postgres/Testcontainers/Gradle/npm toolchain every prior phase in this milestone used. No new CLI,
runtime, or service is introduced.

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 + AssertJ + Timefold `ConstraintVerifier` (backend); no frontend test framework exists in this repo (reconfirmed by `17-UI-SPEC.md` this session — `frontend/package.json` has zero UI-library/test dependencies) |
| Config file | `build.gradle` (Gradle `test` task); `src/test/resources/application-test.yml` for Spring context tests |
| Quick run command | `./gradlew test --tests "com.wfm.solver.UsualShiftConsistencyConstraintTest"` (constraint-only, no Spring context, sub-second per `ConstraintVerifier` precedent) |
| Full suite command | `./gradlew test` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| CONS-01 | Solver penalises deviation from stored usual shift, target-deviation shape | unit (`ConstraintVerifier`) | `./gradlew test --tests "com.wfm.solver.UsualShiftConsistencyConstraintTest"` | ❌ Wave 0 |
| CONS-02 | Tolerance band is a genuine dead zone (zero penalty inside, not tapered) | unit (`ConstraintVerifier`) | same class, dedicated `@Test` case at exactly the band boundary | ❌ Wave 0 |
| CONS-03 | Per-desk weight configurable, respected by the constraint | unit + integration (`ConstraintWeightsService`) | `./gradlew test --tests "com.wfm.service.ConstraintWeightsServiceTest"` | ❌ Wave 0 |
| CONS-04 | Consistency never makes an otherwise-feasible schedule infeasible (soft-only, D-07 save-time enforcement) | unit (service validation) + structural (D-07 400 rejection) | `./gradlew test --tests "com.wfm.service.ConstraintWeightsServiceTest"` | ❌ Wave 0 |
| CONS-05 | Preference decides ties, own constraint fires independently (D-09) | unit (`ConstraintVerifier`) | `./gradlew test --tests "com.wfm.solver.PreferredStartShiftModeConstraintTest"` | ❌ Wave 0 |
| CONS-06 | Precedence documented + observable (D-10's three artefacts) | unit (save-time rejection test) + manual (UI copy, `explain()` two-line breakdown) | `./gradlew test --tests "com.wfm.service.ConstraintWeightsServiceTest"` (rejection); UI copy is `backstop` per UI-SPEC | ❌ Wave 0 (unit); n/a (manual) |
| DRFT-01 | Drift report shows agent/date/magnitude | unit (`ScheduleOutputServiceTest` or new `DriftReportTest`) | `./gradlew test --tests "com.wfm.service.*DriftReport*"` | ❌ Wave 0 |
| DRFT-02 | Three-state distinction (NO_USUAL_SHIFT/HONOURED/DRIFTED) | unit, one case per state | same class | ❌ Wave 0 |
| DRFT-03 | Report derived from the same distance calculation as the constraint | unit — assert `ShiftBandPair.startDeviationMinutes` is the sole call site in both | reflection/structural test mirroring `ScheduleConstraintClassificationTest`'s style, or a direct shared-fixture test asserting constraint penalty and report delta agree on the same schedule | ❌ Wave 0 |
| DRFT-04 | Shift-template popularity ranking | unit | `./gradlew test --tests "com.wfm.service.*DriftReport*"` | ❌ Wave 0 |
| XCUT-01 | Drift data visible in every surface (roster N/A here — export + tab) | unit (export sheet) + manual (tab render, `backstop` per UI-SPEC) | `./gradlew test --tests "com.wfm.service.ScheduleExportServiceTest"` | ❌ Wave 0 (export); n/a (UI) |
| XCUT-02 | Solver never writes `agent_usual_shift` | structural | Extend `src/test/resources/ushf-05-write-paths.md` reasoning / a guard test asserting the solver path has no `AgentUsualShiftRepository.save` call | ❌ Wave 0 |
| XCUT-04 | Seeded A/B benchmark, threshold committed first | benchmark (gated, not in default suite) | `./gradlew test --tests "com.wfm.solver.*Benchmark*" -Dwfm.benchmark=true` | ❌ Wave 0 (new class, modeled on `ShiftModelBenchmarkTest`) |

### Sampling Rate

- **Per task commit:** `./gradlew test --tests "com.wfm.solver.UsualShiftConsistencyConstraintTest"` (or the equivalent scoped class for the task's own file)
- **Per wave merge:** `./gradlew test` (full suite — required per this project's own recorded discipline: no solver-package test loads the Spring context, so a scoped run cannot catch a `solverConfig.xml`-adjacent regression; XCUT-03's existing full-suite-only rule applies here too if `solverConfig.xml` is touched, though this phase is not expected to touch it since no CH/moves change)
- **Phase gate:** Full suite green before `/gsd-verify-work`, plus the benchmark run (`-Dwfm.benchmark=true`) executed and its result committed to a `17-BENCHMARK.md` per D-06/XCUT-04's threshold-first discipline (mirroring `15-BENCHMARK.md`'s structure exactly)

### Wave 0 Gaps

- [ ] `src/test/java/com/wfm/solver/UsualShiftConsistencyConstraintTest.java` — covers CONS-01, CONS-02, CONS-04 (tolerance-band boundary cases, per-agent-day charging, `SchedulingMode.SHIFT` gate, unassigned-shift null-safety mirroring `shiftEnvelopeCompliance`'s `forEachIncludingUnassigned` handling)
- [ ] `src/test/java/com/wfm/solver/PreferredStartShiftModeConstraintTest.java` — covers CONS-05, CONS-06's constraint-firing half; D-09's "fires independently of stored usual shift" case
- [ ] `src/test/java/com/wfm/service/ConstraintWeightsServiceTest.java` (or extend the existing test if one exists — not confirmed present this session; `find` for it at plan time) — covers D-07's hard-must-be-0 rejection, D-08's precedence-ordering rejection, both against the merged-result semantics (Pitfall 5)
- [ ] A drift-report unit test class (name TBD by planner, e.g. `DriftReportTest.java` in `src/test/java/com/wfm/service/`) — covers DRFT-01/02/03/04
- [ ] `src/test/java/com/wfm/solver/UsualShiftConsistencyBenchmarkTest.java` (or extend `ShiftModelBenchmarkTest`) — covers XCUT-04, gated `@EnabledIfSystemProperty("wfm.benchmark")`
- [ ] Extend `ScheduleConstraintClassificationTest`'s backing map in `ScheduleConstraintClassification.java` — required for the build to pass at all once the two new constraints exist (Pitfall 2); not a new test file, an edit to an existing one
- [ ] Confirm at plan time whether `ConstraintWeightsServiceTest`/`ConstraintWeightsControllerTest` already exist (not verified this session) — if absent, Wave 0 must create the harness, not just extend it

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-------------------|
| V2 Authentication | No | Unchanged — this phase adds no new auth surface |
| V3 Session Management | No | Unchanged |
| V4 Access Control | Yes | Every new endpoint/read path must go through the existing tenant+desk scoping pattern already used by `ConstraintWeightsController`/`ScheduleService` (`TenantContext.getTenantId()`, desk-scoped repository finders) — no new IDOR surface should be introduced; the drift report reads through `ScheduleService.getScheduleDetail`'s existing tenant-scoped path, not a new controller |
| V5 Input Validation | Yes | D-07's hard-score-must-be-0 rejection and D-08's precedence-ordering rejection, both server-side (not merely client-side), following `DeskAgentService.setDayHours`'s "reject, don't clamp" `IllegalArgumentException` → 400 pattern |
| V6 Cryptography | No | Not applicable — no new secret, token, or cryptographic material |

### Known Threat Patterns for this stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|----------------------|
| Solver-computed usual-shift resolution accidentally persisting a write to `agent_usual_shift` | Tampering | XCUT-02's explicit requirement: the new pre-solve resolution step must be read-only against `AgentUsualShiftRepository`; a structural test (mirroring `src/test/resources/ushf-05-write-paths.md`'s enumerated-write-paths discipline) should assert the solver path never calls `.save(...)` on that repository |
| Config-save race allowing a hard-nonzero `consistentStartWeight` to persist between the merge and the validation check | Tampering / Elevation of Privilege (of a sort — bypassing a stated safety invariant) | Validate against the fully-merged `weights` entity inside the same `@Transactional` method, before `save(...)` — not against the raw partial `updates` DTO (Pitfall 5) |
| Tenant/desk-scoping bypass on the new drift-report read path | Information Disclosure | Reuse `ScheduleService.getScheduleDetail`'s existing tenant-scoped `Schedule` lookup — the drift report is computed from an already-authorized `Schedule` object, never a second independent query keyed only by IDs from the request |

## Sources

### Primary (HIGH confidence — direct file reads and `git show` this session)

- `17-CONTEXT.md`, `17-UI-SPEC.md` — the phase's own approved upstream artifacts
- `src/main/resources/db/migration/V38__add_consistent_start_weight.sql` — full comment read, quoted verbatim above
- `git show 7861b83`, `git show 9207ceb`, `git show 9f4a96f`, `git show 6fb78c7` (full diffs and commit messages read)
- `src/main/java/com/wfm/model/ConstraintWeights.java` (full file, 256 lines) — confirmed 22 `@ConstraintWeight` fields, confirmed `consistentStartWeight` absent
- `src/main/java/com/wfm/model/AgentShiftAssignment.java`, `ShiftBandPair.java`, `AgentUsualShift.java`, `AgentDayConfig.java`, `Schedule.java` (relevant sections) — confirmed field shapes, problem-fact annotations, absence of a resolved-usual-shift-target list
- `src/main/java/com/wfm/service/UsualShiftResolutionService.java` (full file) — confirmed sole-implementation javadoc, `resolve(stored, date)` signature
- `src/main/java/com/wfm/service/ScheduleOutputService.java` (relevant sections: imports, `buildPreferenceReport`, `buildShiftDescriptorsByAgentDate`, `resolveShiftDescriptor`, `computeDivergence`) — confirmed report-building pattern
- `src/main/java/com/wfm/service/ScheduleService.java:200-228` — confirmed date-filtering pattern including `PreferenceReport` filtering
- `src/main/java/com/wfm/service/ScheduleExportService.java:1-10,155-230` — confirmed `writePreferenceReport` shape
- `src/main/java/com/wfm/service/ConstraintWeightsService.java`, `ConstraintWeightsController.java` (full files) — confirmed no existing save-time validation, confirmed partial-update `if (x != null)` merge shape
- `src/main/java/com/wfm/controller/GlobalExceptionHandler.java` — confirmed `IllegalArgumentException` → 400 `VALIDATION_FAILED` mapping
- `src/main/java/com/wfm/solver/ScheduleConstraintProvider.java` (relevant sections: constraint array, `shiftEnvelopeCompliance` + javadoc, `honourPreferredStartTime` + javadoc, `honourPreferredBreakTime`) — confirmed current mode-gating and unpatched `isBefore` form
- `src/test/java/com/wfm/solver/ScheduleConstraintClassification.java` (header/enum/doc) — confirmed reflection-derived completeness-guard mechanism
- `src/test/java/com/wfm/solver/BandCapacityConstraintTest.java` — confirmed `ConstraintVerifier` harness pattern
- `frontend/src/pages/ConstraintWeightsPage.tsx` (full file, 120 lines) — confirmed 22-row `CONSTRAINTS`/`DEFAULTS` arrays and the generic `Record<string, Score>` cast
- `build.gradle:52` — confirmed Timefold 1.16.0 pin
- `.planning/phases/15-shift-envelope-breaks-library-generation/15-BENCHMARK.md` — confirmed existing benchmark harness shape, threshold-first discipline, gate mechanism
- `.planning/REQUIREMENTS.md`, `.planning/STATE.md`, `.planning/ROADMAP.md` — phase requirements and history, read in full this session
- `ls src/main/resources/db/migration/` — confirmed V47 is the current head, so **V48** is next

### Secondary (MEDIUM confidence)

None used — every claim in this document was either verified directly (Primary) or is a proposed
design (Assumptions Log), not sourced from external documentation.

### Tertiary (LOW confidence)

None.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — zero new dependencies; existing pins verified directly against `build.gradle`
- Architecture: HIGH — every pattern cited is read directly from the current codebase this session, not inferred from CONTEXT.md's prose alone
- Pitfalls: HIGH — each pitfall traces to a specific verified line/file (V38's comment, the current mode-gate on `honourPreferredStartTime`, the `Record<string, Score>` cast, the partial-update merge shape)

**Research date:** 2026-09-04
**Valid until:** Effectively the life of this phase — the codebase facts verified here (migration head, constraint gating, plumbing shapes) are stable until the next phase's commits land; re-verify migration head (`ls db/migration`) immediately before writing V48 if any time has passed since this research.
