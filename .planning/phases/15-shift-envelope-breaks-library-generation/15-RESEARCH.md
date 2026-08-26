# Phase 15: Shift Envelope, Breaks & Library Generation - Research

**Researched:** 2026-08-26
**Domain:** Timefold Solver (1.16.0) construction-heuristic XML config, entity-level value ranges,
Constraint Streams cross-agent aggregation; Java/Spring service layer; React/TypeScript frontend
**Confidence:** HIGH on the construction-heuristic XML shape and API facts (verified this session
directly against the pinned JAR's `solver.xsd` and sources — see §A); MEDIUM-HIGH on the rest
(grounded in code read this session plus the settled spike/architecture research); LOW/ASSUMED only
where flagged inline (SHLB-07 candidate-enumeration specifics, exact clustering formula parameters).

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions (D-01…D-16 — verbatim from 15-CONTEXT.md, do not re-litigate)

- **D-01:** `shift_template.break_offset_minutes`/`break_duration_minutes` retired into a
  `shift_template_break_band` child table (offset, duration, capacity). Migration moves every
  existing template's single offset into exactly one band; both columns dropped. One-way.
- **D-02:** `ShiftLibraryValidationService.covers()` generalises to any-band coverage: a demand
  window is covered if at least one band leaves that window worked. One-band template behaves
  exactly as today.
- **D-03:** A band's `capacity` is a hard cap only when set; blank/null means unlimited. Editor and
  SHLB-07 generation both default it blank. Known residual risk (caps totalling below headcount →
  infeasible solve, no single constraint explains it) — planner must decide where this is surfaced
  (save-time advisory in SHLB-06's shape, or a named `PreSolveValidationException` detail).
- **D-04:** Band choice is ONE planning variable whose value is a `(template, band)` pair problem
  fact — `AgentShiftAssignment` keeps a single `@PlanningVariable`; value range is every live pair
  for the desk, filtered by `AgentDayConfig.effectiveHours`. Rejected: a second `breakBand`
  variable reading a genuine planning variable (Anti-Pattern 2); a third planning entity.
- **D-05:** One `AgentShiftAssignment` row per `AgentDayConfig` with `effectiveHours > 0`. Same
  fact source as the effective-hours filter — cannot disagree by construction.
- **D-06:** Shift variable uses `allowsUnassigned()`, not deprecated `nullable=true`. No new
  "unassigned shift" constraint — a null shift forbids every seat via `shiftEnvelopeCompliance`,
  and `contractedHoursUnder`/`UnderZero` already penalise the resulting emptiness.
- **D-07:** Accepted schedule denormalises the resolved envelope onto the accepted
  `agent_shift_assignment` row (template name, start, end, actual band offset/duration, nullable
  `source_template_id`). Rejected: schedule-scoped copies of `shift_template`+bands; FK to live
  template (editable, breaks D-09 history guarantee).
- **D-08:** CH ordering (shifts-first vs seats-first) is measured as a third XCUT-04 benchmark arm,
  not inherited from the spike's toy fixture.
- **D-09:** Band schema (migration, entity, editor, `covers()` generalisation) ships WITH SHLB-07,
  ahead of the envelope work. Solver-side `(template,band)` value range and ENVL-09 clustering
  follow with envelope work. SHLB-07 still ships first; the "shares no code path with the rest of
  the phase" ROADMAP claim does NOT hold — planner must not carry it forward.
- **D-10:** Generation minimises template count (minimum set cover), total envelope-hours beyond
  demand as deterministic tiebreak. Contracted-hours matching is a filter on candidates, not an
  objective (exact `BigDecimal` equality per D-07 Phase 14).
- **D-11:** Stateless suggestion endpoint — computed on request, no draft table, no status column.
  Saving goes through existing `ShiftTemplateService` create/validate path unchanged.
- **D-12:** Partial coverage returns best partial draft + uncovered windows in SHLB-05's
  `ErrorDetail` shape. Zero live demand or zero contracted-hours agents → REFUSED with existing
  "no staffing demand loaded" message, never an empty draft.
- **D-13:** Benchmark threshold gates piloting, not phase completion. Verdict recorded PASS/FAIL
  verbatim in `15-BENCHMARK.md`.
- **D-14:** Threshold measures model-independent operational metrics (hard feasibility, unstaffed
  slot-count, total assigned hours) — soft-score gap reported separately as a named plateau
  finding, not thresholded (constraint sets differ between arms).
- **D-15:** Pass rule: must-pass `0hard` on every seed; comparative median unstaffed slots no worse
  than slot arm's median; noise rule — any difference smaller than slot arm's own min/max spread is
  "no measurable difference," never a win or loss.
- **D-16:** Fixture: seeded synthetic A/B (Phase 12 harness shape, ~130% over-allocation, system-
  property gated, out of default suite) plus one indicative real-desk run (non-comparative).

### Claude's Discretion (recommendations recorded in 15-CONTEXT.md — adopt or deviate deliberately)

- Mode-gate `honourPreferredStartTime`/`honourPreferredBreakTime` off for shift desks, reclassify
  `MODE_GATED`.
- `shiftEnvelopeCompliance` uses the plain positive-join form, not `ifNotExists`.
- ENVL-07's ground-truth check is a test-side walker outside the score director.
- Mode-gating mechanism: a `SchedulingMode` field on `ScheduleConfig`, read as a filter at the top
  of each affected constraint, bodies untouched.
- ENVL-10's Agent Allocation view: grouping design is discretionary; slot desks must render exactly
  as today.
- A template with zero band rows means "no break" (preserves Phase 14's zero-duration-break
  affordance).
- SHLB-07 candidate enumeration bounds and cover-solving method (exhaustive/ILP/Timefold) are
  discretionary — tens not thousands, do not over-engineer, no new quality-plateau argument.
- Naming of generated templates and effective date range: discretionary.
- Exact Flyway migration number: confirm actual latest-applied version before writing (see §F).
- Whether to close the migration-coverage blind spot (Testcontainers boot test): planner judgement
  call, not mandated.

### Deferred Ideas (OUT OF SCOPE for this phase)

- Continuous break *window* (earliest/latest offset, solver-chosen start) — re-opens the four
  emergent break constraints; bands are sufficient.
- `SelectionFilter` layered on `shiftEnvelopeCompliance` — legitimate later search-efficiency tune,
  never a correctness mechanism, never built speculatively before Option A's baseline is measured.
- Any custom Timefold move (combined shift-plus-seats) — explicitly out of scope for v1.3.
- Capacity-aware coverage validation — rejected again (D-02 keeps coverage structural).
- Promoting break config from `Schedule` to `Desk` — legitimate cleanup, not this phase.
- Multiple *distinct* breaks per agent per day — bands choose one break's timing, not a second
  break.
- Testcontainers-backed migration boot test — discretionary, not mandated.
- `agent_usual_shift` storage, upload column, inline roster edit, consistency constraint, drift
  report — all Phase 16/17.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| ENVL-01 | Solver assigns each working agent exactly one shift/day from desk's library | §B (value range mechanism), §D-05 row source confirmed at `SolverService.computeAgentDayConfigs:559-597` |
| ENVL-02 | Agent never seated outside envelope, hard constraint | §Option A pattern (SPIKE-COUPLING.md, settled) — `shiftEnvelopeCompliance` |
| ENVL-03 | Specialization may vary within shift | No change needed — `specializationMatch` already MODE_AGNOSTIC per XCUT-05 table |
| ENVL-04 | Working day contiguous apart from break | Structural — break becomes a template/band attribute, not search-discovered |
| ENVL-05 | Break placement from template, not the four emergent constraints | §C (mode-gating mechanism) |
| ENVL-06 | Feasible initial solution, no pre-assignment pipeline | §A (two explicit CH phases) |
| ENVL-07 | Score agrees with independent ground-truth check | §Discretion — test-side walker outside score director (pattern from SPIKE-COUPLING.md's external verifier) |
| ENVL-08 | Band choice as planning variable, agents don't all break simultaneously | §B (the `(template,band)` value range) |
| ENVL-09 | Real `Break clustering` body | §D (cross-agent aggregation shape) |
| ENVL-10 | Agent Allocation view groups by shift | §Frontend surface, `ScheduleResults.tsx:285` |
| SHLB-07 | Suggested shift library from demand | §E (set-covering approach) |
| XCUT-01 | Display verification on every surface | §Frontend surface + accepted-row read paths |
| XCUT-03 | Test that builds a real solver from `solverConfig.xml` | §A (exact XML verified; test pattern below) |
| XCUT-04 | Seeded A/B benchmark | §G (harness shape from 12-BENCHMARK.md) |
| XCUT-05 | Complete Phase 14's constraint classification | §C (mode-gating the four break constraints + resolving the two OPEN rows) |
</phase_requirements>

## Summary

This phase's technical core is well-settled by two prior research artifacts
(`SPIKE-COUPLING.md`, `ARCHITECTURE.md`) that this research does not re-litigate. What remained
genuinely open — the construction-heuristic XML shape — is now resolved to **HIGH confidence**:
I extracted `solver.xsd` directly from the pinned `timefold-solver-core-1.16.0.jar` and confirmed
the exact element/attribute nesting. The one correction to the mental model carried in
CONTEXT.md/ARCHITECTURE.md: `entityClass` on `<entitySelector>` is a **nested XML element**
(`<entityClass>com.wfm.model.X</entityClass>`), not an XML attribute, while `id` **is** an
attribute (`<entitySelector id="...">`). The spike's own recommendation text ("EntitySelectorConfig
carrying both an entityClass and an id") is accurate as a *fact-carrying* statement but does not
specify XML surface form — this research supplies that missing piece directly from the XSD, not
from inference over the Java builder API.

The remaining open items are the four things ROADMAP/CONTEXT flagged as requiring active design
rather than verification: (1) where the `(template,band)` value range provider lives and how it
composes with `allowsUnassigned()`; (2) the cheapest, most provably-safe mode-gating point for the
four break constraints; (3) a Constraint Streams shape for `Break clustering`'s real body, which is
a genuine cross-agent, per-timeslot aggregation the codebase has never needed before; and (4)
SHLB-07's set-covering scope, which the ROADMAP explicitly warns against over-engineering. All four
are addressed below with concrete code shapes grounded in patterns already proven elsewhere in
`ScheduleConstraintProvider`.

**Primary recommendation:** Build the two-phase CH exactly as specified in §A (verified against the
XSD); implement the `(template,band)` value range as an entity-level `@ValueRangeProvider` on
`AgentShiftAssignment` reading only `AgentShiftLibraryFact`-style problem facts filtered by
`AgentDayConfig.effectiveHours` (§B); mode-gate the four break constraints with a single top-of-body
filter reading a new `ScheduleConfig.schedulingMode()` field, bodies untouched (§C); implement
`Break clustering` as a `groupBy(timeslot, ...)` cross-agent aggregation mirroring
`bulkUnderallocationHard`'s existing shape (§D); and keep SHLB-07 to plain exhaustive/greedy search
over a candidate set in the tens, reusing `ShiftLibraryValidationService`'s `covers()` predicate
verbatim (§E) — introducing no new dependency.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Shift envelope assignment (which template+band an agent works) | API/Backend (solver) | Database (persisted `AgentShiftAssignment`) | New `@PlanningEntity`, solved server-side; persisted for accepted-schedule history (D-07) |
| Envelope-compliance enforcement | API/Backend (solver) | — | `ConstraintStream` hard constraint, pure server-side computation |
| Break band distribution | API/Backend (solver) | Database (`shift_template_break_band`) | Solver picks a band from a desk-scoped fact table |
| Break clustering penalty | API/Backend (solver) | — | Cross-agent, per-timeslot Constraint Streams aggregation |
| Shift library suggestion (SHLB-07) | API/Backend (service layer) | — | Stateless computation over existing demand/hours data, reuses `ShiftLibraryValidationService.covers()`; no solver involvement (deliberately, per ROADMAP) |
| Agent Allocation grouping display | Frontend Server/Client (React) | API/Backend (DTO shape) | `ScheduleResults.tsx` `AgentAllocationTab` groups client-side over data the backend already returns; backend must expose the resolved `AgentShiftAssignment` alongside `AgentAssignment` rows |
| Migration of `break_offset_minutes`/`break_duration_minutes` → bands | Database | API/Backend (Flyway + JPA entity) | Forward-only schema change; data migration logic lives in the migration SQL, portability constraint is H2-vs-Postgres |
| Benchmark harness | API/Backend (test source set) | — | Pure JVM test harness, no UI surface |

## Standard Stack

### Core

No new external libraries are introduced by this phase — everything below is existing project
infrastructure used in new ways.

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `ai.timefold.solver:timefold-solver-core` | 1.16.0 (pinned, `build.gradle:35`) [VERIFIED: build.gradle] | Constraint-based solver, CH + local search | Already the project's solver; version bump explicitly out of scope (ROADMAP "Timefold version bump" row) |
| `ai.timefold.solver:timefold-solver-test` | 1.16.0 (test scope, `build.gradle:44`) [VERIFIED: build.gradle:44] | `ConstraintVerifier` for constraint-unit tests | Already used by existing constraint tests |
| Spring Boot / Spring Data JPA | (existing, unchanged) | Entity/repository/service layer for `ShiftTemplate`, `AgentShiftAssignment`, `shift_template_break_band` | Matches every existing entity in the codebase |
| Flyway | (existing, unchanged) | Forward-only migration for band table + `agent_shift_assignment` table + column drops | Existing migration mechanism, V39 is latest applied |

**Installation:** No new dependencies. `build.gradle` is unchanged by this phase.

**Version verification:** Confirmed directly from `build.gradle:35` this session — no registry
lookup needed since the version is pinned and unchanged.

```
timefold-solver-core: 1.16.0 [VERIFIED: build.gradle:35, grep run this session]
```

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| (none new) | — | — | — |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Plain exhaustive/greedy set cover for SHLB-07 | An ILP solver (e.g. OR-Tools) or a third Timefold `@PlanningEntity`/`@PlanningSolution` | ROADMAP is explicit: candidate set is tens, not thousands — introducing a new dependency or a second solver model is over-engineering a problem exhaustive search or a simple greedy-with-verification solves exactly. Rejected per explicit ROADMAP guidance ("must not acquire its own quality-plateau argument"). |
| Entity-level `@ValueRangeProvider` for `(template,band)` filtered by `effectiveHours` | Solution-level `@ValueRangeProvider` + a separate filter constraint | Entity-level is the pattern ARCHITECTURE.md §1/§C already validated as the **sound** use of entity-level value ranges (dependency on a problem fact, not a genuine planning variable) — a solution-level range would need an extra hard constraint to enforce the same filter, which is strictly more code for the same guarantee. |

## Package Legitimacy Audit

**Not applicable — no external packages are installed by this phase.** All work uses existing
project dependencies (Timefold 1.16.0, Spring Boot, Flyway, React/TypeScript — all already present
in `build.gradle`/`package.json`). No `npm view`/`pip index`/`cargo search` verification is
required because nothing new is added to either `build.gradle` or `frontend/package.json`.

## Architecture Patterns

### System Architecture Diagram

```
                    ┌─────────────────────────────────────────────────────┐
                    │  SolverService.solve() (existing, extended)          │
                    │                                                       │
  AgentDayHours ───►│  computeAgentDayConfigs()  ──► List<AgentDayConfig>  │
  AgentDaysOff       │       (unchanged — filters effectiveHours>0,        │
  AgentException      │        day-off)                                    │
                    │            │                                          │
                    │            ▼                                          │
                    │  NEW: buildShiftAssignments(agentDayConfigs)          │
                    │       one AgentShiftAssignment row per config entry   │
                    │       (D-05 — same source, cannot disagree)           │
                    │            │                                          │
                    │            ▼                                          │
  ShiftTemplate ───►│  schedule.setShiftAssignments(...)                   │
  + bands (fact)     │  schedule.setShiftTemplatePairs(...) [value-range    │
                    │       problem fact, filtered per AgentDayConfig]       │
                    │            │                                          │
                    │            ▼                                          │
                    │  ┌───────────────────────────────────────────────┐  │
                    │  │  Timefold SolverFactory (solverConfig.xml)     │  │
                    │  │                                                 │  │
                    │  │  CH phase 1: AgentShiftAssignment.shift chosen │  │
                    │  │    (entity-level value range: live (template,  │  │
                    │  │     band) pairs filtered by effectiveHours)    │  │
                    │  │            │                                   │  │
                    │  │            ▼                                   │  │
                    │  │  CH phase 2: AgentAssignment.agent chosen      │  │
                    │  │    (existing agentRange, unchanged)             │  │
                    │  │            │                                   │  │
                    │  │            ▼                                   │  │
                    │  │  Local Search (simulated annealing, unchanged) │  │
                    │  │    ScheduleConstraintProvider evaluates:       │  │
                    │  │      - shiftEnvelopeCompliance (NEW, hard)     │  │
                    │  │      - breakClustering (NEW real body, soft)   │  │
                    │  │      - 4 break constraints (mode-gated OFF     │  │
                    │  │        for SHIFT desks, unchanged for SLOT)    │  │
                    │  │      - 13 unchanged MODE_AGNOSTIC constraints  │  │
                    │  └───────────────────────────────────────────────┘  │
                    │            │                                          │
                    └────────────┼──────────────────────────────────────────┘
                                 ▼
                    Schedule (score, AgentAssignment[], AgentShiftAssignment[])
                                 │
                    ┌────────────┴───────────────────────────────────┐
                    ▼                                                 ▼
       ENVL-07: test-side ground-truth walker         Accept flow: denormalise
       (outside score director, asserts no seat        resolved envelope onto
       falls outside its agent's chosen envelope)       accepted AgentShiftAssignment
                                                          row (D-07)
                                                                 │
                                                                 ▼
                                              ScheduleResults.tsx AgentAllocationTab
                                              groups by shift (ENVL-10); slot desks
                                              render unchanged (mode check client-side
                                              or via a response field)

SEPARATE, independent flow (SHLB-07, no solver involvement):
  StaffingRequirement (live demand) ──┐
                                       ├──► ShiftLibraryGenerationService
  AgentDayHours (contracted hours) ───┘        │  candidate envelope enumeration
                                                │  (tens, per Discretion bounds)
                                                │  set-cover minimisation (D-10)
                                                │  reuses ShiftLibraryValidationService
                                                │  .covers() verbatim (D-02/D-08 reuse)
                                                ▼
                                    Editable draft rows (D-11 — stateless, no
                                    persistence until operator saves through the
                                    existing ShiftTemplateService path)
```

### Recommended Project Structure

No new top-level packages — this phase extends existing packages:

```
src/main/java/com/wfm/
├── model/
│   ├── ShiftTemplate.java              # loses break*Minutes fields, gains bands relation
│   ├── ShiftTemplateBreakBand.java     # NEW — offset, duration, capacity (D-01)
│   ├── AgentShiftAssignment.java       # NEW — @PlanningEntity + @Entity dual-purpose (mirrors AgentAssignment)
│   ├── ShiftBandPair.java              # NEW — problem-fact record wrapping (ShiftTemplate, ShiftTemplateBreakBand)
│   ├── ScheduleConfig.java             # gains schedulingMode field
│   └── ConstraintWeights.java          # gains shiftEnvelopeComplianceWeight
├── solver/
│   └── ScheduleConstraintProvider.java # +shiftEnvelopeCompliance, breakClustering real body,
│                                         mode-gate 4 break constraints + 2 preference constraints
├── service/
│   ├── ShiftTemplateService.java       # band CRUD folded into existing validate() path
│   ├── ShiftLibraryValidationService.java # covers() generalised to any-band (D-02)
│   ├── ShiftLibraryGenerationService.java # NEW — SHLB-07 stateless suggestion endpoint
│   └── SolverService.java              # +buildShiftAssignments, mode-aware AgentShiftAssignment population
└── resources/
    ├── solverConfig.xml                # two explicit <constructionHeuristic> phases
    └── db/migration/
        ├── V40__....sql                # confirm actual number at write time (see §F)
        └── ...

src/test/java/com/wfm/solver/
├── ScheduleConstraintClassification.java     # move 4 break rows + 2 OPEN rows to MODE_GATED/resolved
├── SolverConfigBuildTest.java           # NEW — XCUT-03: actually builds a solver from real XML
├── ShiftEnvelopeGroundTruthTest.java    # NEW — ENVL-07: walks solved schedule outside score director
└── ShiftModelBenchmarkTest.java         # NEW — XCUT-04: seeded A/B, system-property gated
```

### Pattern 1: Two explicitly-scoped CH phases in `solverConfig.xml`

**What:** Replace the bare `<constructionHeuristic/>` with two sequential, explicitly-scoped CH
phases, one per entity class, each using a `<queuedEntityPlacer>` with an `<entitySelector>`
carrying both an `id` attribute and an `<entityClass>` child element.

**When to use:** Mandatory the moment a second `@PlanningEntity` class exists in the solution — the
bare form throws `IllegalArgumentException` at solver-build time (confirmed both by the spike and
independently by this session's read of `AbstractFromConfigFactory.getTheOnlyEntityDescriptor`,
lines 62-71 of the 1.16.0 sources jar).

**Example — verified against `solver.xsd` extracted from the pinned JAR this session:**

```xml
<!-- Source: XML shape verified this session against solver.xsd inside
     timefold-solver-core-1.16.0.jar (ai/timefold/solver/core/config/... package, confirmed via
     javap-equivalent source read of EntitySelectorConfig.java, QueuedEntityPlacerConfig.java,
     ConstructionHeuristicPhaseConfig.java, and cross-checked against the compiled solver.xsd
     complexType "entitySelectorConfig" at line 346-388). HIGH confidence — this is the actual
     schema compiled into the pinned JAR, not a version-unspecified doc page. -->
<solver xmlns="https://timefold.ai/xsd/solver" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xsi:schemaLocation="https://timefold.ai/xsd/solver https://timefold.ai/xsd/solver/solver.xsd">

    <solutionClass>com.wfm.model.Schedule</solutionClass>
    <entityClass>com.wfm.model.AgentShiftAssignment</entityClass>
    <entityClass>com.wfm.model.AgentAssignment</entityClass>

    <scoreDirectorFactory>
        <constraintProviderClass>com.wfm.solver.ScheduleConstraintProvider</constraintProviderClass>
    </scoreDirectorFactory>

    <!-- Phase 1: choose shift+band envelope for every agent-day BEFORE any seat is placed. -->
    <constructionHeuristic>
        <queuedEntityPlacer>
            <entitySelector id="shiftEntitySelector">
                <entityClass>com.wfm.model.AgentShiftAssignment</entityClass>
            </entitySelector>
        </queuedEntityPlacer>
    </constructionHeuristic>

    <!-- Phase 2: choose seat occupancy, now that every agent-day has an envelope to check against. -->
    <constructionHeuristic>
        <queuedEntityPlacer>
            <entitySelector id="seatEntitySelector">
                <entityClass>com.wfm.model.AgentAssignment</entityClass>
            </entitySelector>
        </queuedEntityPlacer>
    </constructionHeuristic>

    <localSearch>
        <acceptor>
            <simulatedAnnealingStartingTemperature>0hard/3000soft</simulatedAnnealingStartingTemperature>
        </acceptor>
    </localSearch>

</solver>
```

**Critical, previously-unverified fact this research resolves:** `id` is an **XML attribute** on
`<entitySelector>` (`@XmlAttribute protected String id`, `EntitySelectorConfig.java` line ~48-49),
while `entityClass` is a **nested XML element** (`<xs:element minOccurs="0" name="entityClass"
type="xs:string"/>`, `solver.xsd` line 354; confirmed by the `@XmlType(propOrder = {"id", ...,
"entityClass", ...})` on the class, which JAXB uses to order **elements**, while `id` and
`mimicSelectorRef` are excluded from `propOrder` precisely because they are attributes, not
elements). **D-08's benchmark arm for CH ordering only needs to swap which `<constructionHeuristic>`
block comes first** — nothing else in the XML changes.

**Why `id` is mandatory (spike's finding, independently confirmed this session):**
`AbstractFromConfigFactory.getTheOnlyEntityDescriptor` (1.16.0 sources,
`ai/timefold/solver/core/impl/AbstractFromConfigFactory.java:62-71`) throws exactly the
`IllegalArgumentException` the spike quoted when `entityClassSet` has more than one entry and no
explicit `entityClass` is configured on the placer's selector. Internally, Timefold's
`AbstractEntityPlacerFactory.buildChangeMoveSelectorConfig` builds a mimic-selector reference
(`EntitySelectorConfig.newMimicSelectorConfig(entitySelectorConfigId)`) to reuse the CH's own
entity selection inside any auto-generated local-search move selectors for that phase; a null `id`
makes that internal mimic-reference resolution ambiguous, producing the spike's misleading
"variableName is not a valid planning variable" error.

**Top-level `<entityClass>` declaration order does not matter for CH deduction** once each phase's
placer is explicitly scoped — the top-level list only matters for the (now-avoided) automatic
single-entity deduction path.

### Pattern 2: Entity-level `@ValueRangeProvider` filtered by a problem fact (sound, per ARCHITECTURE.md §C)

**What:** `AgentShiftAssignment.getEligibleShiftBandPairs()` is annotated
`@ValueRangeProvider(id = "shiftBandRange")`, returns `List<ShiftBandPair>` filtered to templates
whose `getNetHours()` matches this row's `AgentDayConfig.effectiveHours` (via the same
`BigDecimals.normalize`/`compareTo` exact-equality pattern `ShiftLibraryValidationService
.anyHoursMatch` already uses at Phase 14).

**When to use:** This is the ARCHITECTURE.md §C-sanctioned sound use of entity-level value ranges —
dependency on a **problem fact** (`AgentDayConfig`, resolved once per solve before solving starts),
never on another entity's **genuine planning variable**. Contrast with the rejected Option C, which
depended on `ShiftAssignment.shift`, a live planning variable.

**Example (sketch, following the existing `ShiftTemplate.getNetHours()` pattern read this session
at `ShiftTemplate.java:150-158`):**

```java
// AgentShiftAssignment.java — sketch, not verbatim; follows the AgentAssignment dual-entity
// pattern (@PlanningEntity + @Entity) read this session at AgentAssignment.java:1-66.
@PlanningEntity
@Entity
@Table(name = "agent_shift_assignment")
public class AgentShiftAssignment {

    @PlanningId @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // identity: (agent, date) — D-05
    @ManyToOne(fetch = FetchType.LAZY) private Agent agent;
    private LocalDate date;

    // problem fact, resolved once per solve, NEVER a genuine planning variable dependency
    @Transient private AgentDayConfig dayConfig;
    // desk-scoped live (template, band) pairs — also a problem fact, resolved before solving
    @Transient private List<ShiftBandPair> deskShiftBandPairs;

    @PlanningVariable(valueRangeProviderRefs = "shiftBandRange", allowsUnassigned = true)
    @Transient
    private ShiftBandPair shiftBandPair;

    @ValueRangeProvider(id = "shiftBandRange")
    public List<ShiftBandPair> getEligibleShiftBandPairs() {
        return deskShiftBandPairs.stream()
                .filter(p -> BigDecimals.normalize(p.template().getNetHours())
                        .compareTo(BigDecimals.normalize(dayConfig.effectiveHours())) == 0)
                .toList();
    }
    // ...
}
```

**`allowsUnassigned()` confirmed present at 1.16.0** [VERIFIED: `PlanningVariable.java` inside
`timefold-solver-core-1.16.0-sources.jar`, read this session — `boolean allowsUnassigned() default
false;` at line 48, with `nullable()` at line 57 marked `@deprecated`]. D-06 is correct to use it.

### Pattern 3: Mode-gating a constraint body without touching it

**What:** Add a single `.filter(...)` (or an early `if`) at the very top of each of the four break
constraints and the two preference constraints, reading `ScheduleConfig.schedulingMode()`.

**When to use:** Whenever a constraint's entire behaviour must become inert for one mode while
staying byte-identical for the other — mirrors the existing `minimumStaffing` precedent where
hard-vs-soft is "a per-desk configuration row, not a code decision" (javadoc read this session at
`ScheduleConstraintProvider.java:456-461`).

**Example, applied to `exactlyOneBreak` (read this session at lines 188-231) — only the join and
filter line changes, the entire penalize/asConstraint body is untouched:**

```java
private Constraint exactlyOneBreak(ConstraintFactory factory) {
    return factory.forEach(AgentAssignment.class)
            .filter(a -> a.getAgent() != null)
            .groupBy(AGENT_ID, DATE, TO_LIST)
            .join(AgentDayConfig.class,
                    equal((daId, date, assignments) -> daId, AgentDayConfig::agentId),
                    equal((daId, date, assignments) -> date, AgentDayConfig::date))
            .join(ScheduleConfig.class)   // NEW join, cheap — ScheduleConfig is a
                                           // @ProblemFactProperty singleton (Schedule.java:255-264)
            .filter((daId, date, assignments, dayConfig, cfg) ->
                    cfg.schedulingMode() == SchedulingMode.SLOT)  // NEW — the only change to this line
            .filter((daId, date, assignments, dayConfig, cfg) -> {
                // ... existing body, completely unchanged, just re-shaped for the extra tuple arg
            })
            .penalizeConfigurable((daId, date, assignments, dayConfig, cfg) -> { /* unchanged */ })
            .asConstraint("Exactly one break");
}
```

**Why this is the cheapest correct filter point:** `ScheduleConfig` is already a
`@ProblemFactProperty` singleton record [VERIFIED: `Schedule.java:255-264`, read this session —
`@ProblemFactProperty @Transient public ScheduleConfig getScheduleConfig() { return new
ScheduleConfig(...); }`]. Joining a `ConstraintFactory.forEach(ScheduleConfig.class)` stream costs
one row (there is exactly one `ScheduleConfig` per solve), so the join is effectively free compared
to the existing `AgentDayConfig` join already in every one of these four constraints. Filtering at
the very first `.filter(...)` after the join means the constraint's existing lambda bodies do not
need to change their internal logic at all — only their tuple arity grows by one argument.

**Adding `SchedulingMode` to `ScheduleConfig` — verified ripple:** `ScheduleConfig` is a `record`
[VERIFIED: `ScheduleConfig.java:10-22`, read this session — 11-field record, constructed in exactly
one place]. Its sole construction site is `Schedule.getScheduleConfig()`
[VERIFIED: `Schedule.java:255-264`]. Adding a 12th field requires updating that one constructor call
and every constraint that constructs a `ScheduleConfig` directly in a test fixture (none do in
production code — constraints only ever consume it via `.join(ScheduleConfig.class)`). This is a
narrow, single-call-site change; there is no other construction site to find.

**`Desk.schedulingMode` is already a persisted enum** [VERIFIED: `Desk.java:30` — `private
SchedulingMode schedulingMode = SchedulingMode.SLOT;`]. `SolverService.buildSchedule` must read
`desk.getSchedulingMode()` and populate the new `ScheduleConfig` field the same way it already
threads `desk`-derived values through `Schedule` (pattern confirmed at
`SolverService.java:124-129`, where `desk` is loaded specifically to seed `Schedule` fields).

### Pattern 4: Cross-agent, per-timeslot aggregation for `Break clustering` (ENVL-09)

**What:** `Break clustering` must penalise, per timeslot, `agentsOnBreak > breakClusterThresholdPct%
* agentsAssigned`. This requires two aggregates over the same timeslot: a count of agents currently
on break, and a count of agents currently assigned — a shape the codebase has never built before
(every existing constraint either counts one or the other, never both joined on the same key).

**When to use:** Only fires in shift mode — "on break" is derivable structurally from the assigned
`(template,band)` pair's break window, not from assignment gaps (per the CONTEXT.md note: "on
break" in shift mode is derivable from the assigned pair, not from assignment gaps).

**Recommended shape**, following the existing `bulkUnderallocationHard`/`minimumStaffing` pattern
of `groupBy(timeslot, sum(...))` joined to a demand-like fact (read this session at
`ScheduleConstraintProvider.java:423-441` and `491-498`):

```java
// Sketch — two groupBy streams over AgentAssignment joined on Timeslot, one counting total
// assigned, one counting "this agent is on break at this timeslot" (derived by joining to the
// agent's chosen AgentShiftAssignment.shiftBandPair and checking whether the timeslot falls
// inside that pair's break window — a cheap, purely structural check, no search involved).
private Constraint breakClustering(ConstraintFactory factory) {
    UniConstraintStream<Timeslot> agentsAssignedPerTimeslot = factory
            .forEach(AgentAssignment.class)
            .filter(a -> a.getAgent() != null)
            .groupBy(a -> a.getTimeslot(), count());   // total assigned, per timeslot

    return factory.forEach(AgentAssignment.class)
            .filter(a -> a.getAgent() != null)
            .join(AgentShiftAssignment.class,
                    equal(a -> a.getAgent().getId(), sa -> sa.getAgent().getId()),
                    equal(a -> a.getTimeslot().getDate(), AgentShiftAssignment::getDate))
            .join(ScheduleConfig.class)
            .filter((a, sa, cfg) -> cfg.schedulingMode() == SchedulingMode.SHIFT
                    && sa.getShiftBandPair() != null
                    && onBreakAt(a.getTimeslot(), sa.getShiftBandPair()))
            .groupBy((a, sa, cfg) -> a.getTimeslot(), count())        // on-break count, per timeslot
            .join(agentsAssignedPerTimeslot,
                    equal((ts, onBreak) -> ts, (ts, total) -> ts))
            .join(ScheduleConfig.class)
            .filter((ts, onBreak, total, cfg) ->
                    onBreak * 100 > total * cfg.breakClusterThresholdPct())
            .penalizeConfigurable((ts, onBreak, total, cfg) ->
                    onBreak * 100 - total * cfg.breakClusterThresholdPct() / 100)
            .asConstraint("Break clustering");
}
```

**Confidence: MEDIUM** — the join/groupBy mechanics mirror proven existing patterns in this exact
file (`bulkUnderallocationHard`'s `groupBy(timeslot, sum(...))` then `.join(...)` shape, read this
session), so the Constraint Streams API surface is HIGH confidence; the exact penalty formula
(linear excess vs. some other shape) and the precise semantics of "assigned agents" (all
`AgentAssignment`-seated agents in that timeslot, vs. only agents whose envelope covers that
timeslot) are a **planner/implementer judgement call**, not verified against a spec — flag as an
open question requiring the fixture-based demonstration ROADMAP already requires (single-band
library starves a timeslot; multi-band library does not) to validate the formula empirically rather
than by inspection alone.

**This constraint currently has weight `HardSoftScore.ofSoft(2)`** [VERIFIED:
`ConstraintWeights.java:82-85`, `breakClusteringWeight = HardSoftScore.ofSoft(2)`] — CONTEXT.md's
code_context section confirms no new weight column is needed for this constraint, only for
`shiftEnvelopeCompliance`.

### Anti-Patterns to Avoid

- **Filtering `AgentAssignment.agent`'s value range by the agent's currently-chosen
  `AgentShiftAssignment.shift`:** Empirically proven unsound on 8/8 seeds
  (`SPIKE-COUPLING.md`) — reports `0hard/0soft` while 9-14/24 seats sit outside envelope. Settled,
  do not revisit.
- **Touching the bodies of the four break constraints to add mode-awareness inline:** Per
  ARCHITECTURE.md §7 and CONTEXT.md's Discretion recommendation, the bodies must stay byte-for-byte
  identical for slot mode — only a filter/join is added at the top. A body edit risks a slot-mode
  regression that "cannot leak into slot-mode desks by construction" if done correctly, but *can*
  leak if the body itself is touched.
- **Building `Break clustering`'s real body as a naive nested loop or nested join without a shared
  `groupBy` node:** The existing codebase deliberately hoists shared grouping lambdas to single
  static instances specifically to avoid duplicate constraint-stream nodes (comment block read this
  session at `ScheduleConstraintProvider.java:29-47`, citing a measured 32,489/sec → 44,000+/sec
  improvement). A new cross-agent aggregation constraint should follow the same discipline if it
  shares a grouping key with an existing constraint.
- **Introducing a solution-level `@ValueRangeProvider` for the `(template,band)` pair and a
  *separate* hard constraint to enforce the effective-hours filter:** Functionally equivalent to
  the entity-level filtered range but strictly more code (an extra constraint, an extra weight
  column, an extra classification row) for the same guarantee. Entity-level range is D-04's chosen
  shape and ARCHITECTURE.md §C's validated sound pattern.
- **Over-engineering SHLB-07 as a Timefold `@PlanningSolution`/ILP model:** ROADMAP is explicit —
  "the candidate set is tens, not thousands... it does not need a heuristic, and it must not
  acquire its own quality-plateau argument." Plain exhaustive/greedy-with-exact-verification is
  correct and simpler.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Coverage predicate for SHLB-07's candidate templates | A second implementation of "does this template cover this demand window" | `ShiftLibraryValidationService.covers()`, generalised for any-band coverage per D-02 | D-08 (Phase 14) and D-02 (this phase) both require exactly one coverage implementation — a generation-side reimplementation would be the audit NEW-1 shape (two sources that can disagree). |
| Exact contracted-hours matching | A new tolerance-band comparison | `BigDecimals.normalize(...).compareTo(...)` exact equality, same pattern as `ShiftLibraryValidationService.anyHoursMatch` (read this session, lines 267-274) | D-07 (Phase 14) already settled exact `BigDecimal` equality with no tolerance; a second, looser comparison in generation would silently propose templates the validator then rejects — violating SHLB-07's own acceptance criterion. |
| Set-cover minimisation | A new ILP dependency or ad-hoc heuristic search | Exhaustive search over the tens-sized candidate set, or a simple greedy-with-verification pass (pick the candidate covering the most still-uncovered demand, repeat, verify the final draft covers everything via `covers()`) | ROADMAP explicitly forbids over-engineering this; exhaustive search over "tens" of candidates is fast and provably optimal, avoiding both a new dependency and a second quality-plateau argument. |
| Ground-truth feasibility check | Trusting the reported Timefold score alone | A test-side walker that iterates every `AgentAssignment` and checks its timeslot falls inside its agent's `AgentShiftAssignment.shiftBandPair` envelope, run **outside** `SolutionManager`/score director | This is the exact mechanism `SPIKE-COUPLING.md`'s external verifier used to catch Option C's `0hard/0soft`-but-actually-broken solutions — no Timefold assertion mode (`FULL_ASSERT`, `TRACKED_FULL_ASSERT`) can catch a scoring-consistent-but-wrong model. |

**Key insight:** Every "don't hand-roll" item above exists because this codebase has already been
burned by parallel/duplicate implementations of the same fact (audit NEW-1, audit I-1, and this
milestone's own Option C near-miss). The discipline that generalizes: wherever a new piece of code
needs an answer that an existing, already-tested piece of code already computes, call it — don't
recompute it, even approximately.

## Runtime State Inventory

**Not applicable — this is not a rename/refactor/migration phase in the sense that triggers this
section.** This phase does add a schema migration (dropping two columns, adding two tables), which
IS covered under §F below (Flyway forward-only mechanics) and the standard `Runtime State
Inventory` categories are addressed inline there. For completeness, applying the five categories
explicitly:

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | `shift_template.break_offset_minutes`/`break_duration_minutes` on every existing row for every existing template | Data migration: copy each template's single offset/duration into exactly one new `shift_template_break_band` row (D-01), then drop the two columns, in the same forward-only migration |
| Live service config | None found — no external service (n8n, Datadog, etc.) references shift template break fields; this project has no such integrations for shift data | None |
| OS-registered state | None found — no scheduled tasks, no pm2/launchd/systemd units reference shift or break config | None |
| Secrets/env vars | None found — no secret key names reference `break_offset_minutes`/`break_duration_minutes` | None |
| Build artifacts | None found — no compiled/installed artifact caches shift template shape outside the JPA entity itself, which is rebuilt on every deploy | None |

## Common Pitfalls

### Pitfall 1: Trusting the reported score without an external ground-truth check
**What goes wrong:** A solution reports `0hard/0soft` (feasible, optimal) while agents are actually
seated outside their envelopes.
**Why it happens:** A structurally unsound coupling mechanism (filtered value range reading a
genuine planning variable) computes an internally-consistent-but-wrong score — the incremental and
from-scratch scores agree with each other, but both are wrong relative to the actual constraint
that should have fired. This is `SPIKE-COUPLING.md`'s central finding, demonstrated on 8/8 seeds.
**How to avoid:** Build `shiftEnvelopeCompliance` as a genuine `ConstraintStream` hard constraint
(Option A, already settled) AND add the ENVL-07 ground-truth walker as an independent, score-
director-free check in the acceptance tests.
**Warning signs:** A "correctness" claim resting solely on `0hard` with no independent walker; any
`FULL_ASSERT`-passing claim being treated as sufficient proof of soundness (it is not — `FULL_ASSERT`
only checks score-consistency, never value-range-validity, per the spike's read of
`AbstractScoreDirector`'s assertion surface).

### Pitfall 2: The bare `<constructionHeuristic/>` silently working today, then breaking at solver-build time
**What goes wrong:** `solverConfig.xml` currently has a bare `<constructionHeuristic/>` that works
fine with one `@PlanningEntity`. The moment `AgentShiftAssignment` is added as a second entity
class, `SolverFactory.buildSolver()` throws `IllegalArgumentException` — and **no existing test
catches this**, because no test under `src/test/java/com/wfm/solver/` loads the real
`solverConfig.xml` through a `SolverFactory`.
**Why it happens:** `QueuedEntityPlacerFactory.buildEntitySelectorConfig` auto-deduces the single
entity class only when exactly one exists in the `entityClassSet`; two or more makes deduction
ambiguous.
**How to avoid:** Ship both the XML fix (§Pattern 1 above) AND the XCUT-03 test
(`SolverFactory.createFromXmlResource("solverConfig.xml").buildSolver()`, no Spring context
required) in the same plan/commit — the fix without the regression test leaves the exact failure
mode this phase exists to close.
**Warning signs:** Any `solverConfig.xml` edit reviewed/merged without a corresponding
solver-build test change.

### Pitfall 3: A mode-gating filter that silently changes slot-mode behaviour
**What goes wrong:** Adding a `.filter(mode == SHIFT)` (inverted logic, or added in the wrong
position in the stream) accidentally changes what fires for `SLOT`-mode desks too.
**Why it happens:** Constraint Streams pipelines are order-sensitive for readability but not for
correctness — a filter added *after* a `.groupBy` that already discarded information needed to
re-derive the mode-agnostic path can silently narrow the slot-mode result set.
**How to avoid:** Add the mode filter as the **very first** operation after the existing joins that
already exist (§Pattern 3), never restructure the existing pipeline; run the full existing test
suite (which is entirely single-mode/slot fixtures today, per XCUT-05's own observation that "a
test suite where every fixture is single-mode is structurally blind to interaction bugs") and
additionally add at least one shift-mode fixture that proves the four break constraints are inert
for that desk.
**Warning signs:** Any diff to the four break constraints' internal filter/penalize lambdas, not
just the new top-of-stream mode check.

### Pitfall 4: Set-cover generation proposing templates the validator then rejects
**What goes wrong:** SHLB-07's suggested draft, when run back through
`ShiftLibraryValidationService.validate()`, reports uncovered windows or hours mismatches — breaking
the phase's own stated acceptance criterion ("running the existing SHLB-05/SHLB-06 validation over
the draft reports zero uncovered windows").
**Why it happens:** A second, slightly-different coverage or hours-match implementation drifts from
the validator's exact semantics (grid alignment, effective-range inclusivity, `BigDecimal` scale
normalisation).
**How to avoid:** Generation must call the *same* `covers()` method (post-D-02 generalisation) and
the *same* `BigDecimals.normalize(...).compareTo(...)` exact-equality check the validator uses —
not a re-derivation of the same logic.
**Warning signs:** Any new coverage-checking or hours-matching code inside a
`ShiftLibraryGenerationService` that doesn't call into `ShiftLibraryValidationService`.

### Pitfall 5: Judging the coupling constraint's quality effect on a single unseeded run
**What goes wrong:** A quick manual solve before/after adding `shiftEnvelopeCompliance` looks like
an improvement or regression that is actually inside the noise band.
**Why it happens:** Phase 12's own definitive evidence: a 0.25h effect against a 5.00h run-to-run
spread under wall-clock termination looked plausible in one run and was noise.
**How to avoid:** D-15/D-16's seeded, step-count-terminated harness with median + full min/max
spread, reused from `12-BENCHMARK.md`'s shape (§G below) — never a single run, never wall-clock
termination.
**Warning signs:** Any benchmark claim citing a single number without a spread; any termination
config using `withSpentLimit` instead of `withStepCountLimit` for the comparative arm.

## Code Examples

### A. Solver-build regression test (XCUT-03)

```java
// Source: pattern recommended verbatim in SPIKE-COUPLING.md "Recommendation for phase planning"
// item 3, read this session. No Spring context required.
package com.wfm.solver;

import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.api.solver.Solver;
import ai.timefold.solver.core.config.solver.SolverConfig;
import com.wfm.model.Schedule;
import org.junit.jupiter.api.Test;

class SolverConfigBuildTest {
    @Test
    void solverConfigXmlBuildsARealSolver() {
        SolverFactory<Schedule> factory =
                SolverFactory.createFromXmlResource("solverConfig.xml");
        Solver<Schedule> solver = factory.buildSolver();
        // If buildSolver() doesn't throw, the two-phase CH XML is structurally valid —
        // this is exactly the check the spike found nothing in the current suite performs.
    }
}
```

### B. Ground-truth walker (ENVL-07)

```java
// Sketch — pattern mirrors SPIKE-COUPLING.md's external verifier (SpikeMain.java), which is
// the mechanism that caught Option C's 8/8-seed false-feasible result. Runs on a solved Schedule,
// completely outside SolutionManager/ScoreDirector.
static List<String> findEnvelopeViolations(Schedule solved) {
    Map<Pair<UUID, LocalDate>, ShiftBandPair> resolvedShift = solved.getShiftAssignments().stream()
            .collect(toMap(sa -> Pair.of(sa.getAgent().getId(), sa.getDate()), AgentShiftAssignment::getShiftBandPair));

    List<String> violations = new ArrayList<>();
    for (AgentAssignment a : solved.getAssignments()) {
        if (a.getAgent() == null) continue;
        ShiftBandPair pair = resolvedShift.get(Pair.of(a.getAgent().getId(), a.getTimeslot().getDate()));
        if (pair == null || !pair.covers(a.getTimeslot())) {
            violations.add("agent=" + a.getAgent().getId() + " timeslot=" + a.getTimeslot());
        }
    }
    return violations;
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|---------------|--------|
| Break position discovered by 4 hard constraints scanning assignment gaps | Break position is a structural template/band attribute, decided once by an operator | This phase (ENVL-05) | Removes the exact search-space wall that broke `BreakAwareConstructionPhase` and constrained Phase 12's Atomic Shift Move — per ARCHITECTURE.md §4 |
| Single fixed `break_offset_minutes` per template (Phase 14) | N break bands per template, one planning variable choosing among them | This phase (ENVL-08, D-01) | Gives the solver freedom to distribute breaks — the mechanism that lets `Break clustering` (ENVL-09) actually have something to optimise |
| `@PlanningVariable(nullable = true)` | `@PlanningVariable(allowsUnassigned = true)` | Deprecated since Timefold 1.8.0 [VERIFIED: `PlanningVariable.java`, `@Deprecated(forRemoval = true, since = "1.8.0")` on `nullable()`] | `AgentAssignment` still uses the deprecated form; `AgentShiftAssignment` (new, this phase) must use the non-deprecated form per D-06 — a documented inconsistency between the two entities, acceptable since fixing `AgentAssignment` retroactively is out of scope |
| Bare `<constructionHeuristic/>` (auto-deduces the single entity) | Two explicit `<constructionHeuristic>` phases, each scoped to one entity class | This phase (mandatory once a 2nd `@PlanningEntity` exists) | `solverConfig.xml` changes for the first time this milestone; XCUT-03 test is the required companion |

**Deprecated/outdated:**
- `@PlanningVariable(nullable = true)`: still valid at 1.16.0 but marked `forRemoval = true` since
  1.8.0 — do not use it on the new `AgentShiftAssignment` entity; use `allowsUnassigned()` instead
  (D-06 already mandates this).

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `Break clustering`'s exact penalty formula (linear excess of `onBreak*100 - total*thresholdPct`) is a reasonable shape | Pattern 4 | If the formula produces perverse incentives (e.g. penalising a timeslot with very few agents disproportionately), the fixture demonstration (single-band starves, multi-band doesn't) may not show a clean contrast — planner/implementer should validate empirically against the required fixture before committing to a specific formula, per the phase's own "must be demonstrated, not merely coded" framing |
| A2 | "Agents assigned" in the clustering denominator means all seated `AgentAssignment` rows in that timeslot, not only agents whose envelope covers it | Pattern 4 | If demand-only counts differ from actual assigned counts (e.g. overflow seats), the threshold percentage could be computed against the wrong base — needs an explicit decision recorded in the plan, not inferred from this research |
| A3 | The `(template,band)` value range provider is best placed as an entity-level `@ValueRangeProvider` method on `AgentShiftAssignment` itself, rather than a solution-level provider pre-filtered per agent-day in `SolverService` | Pattern 2 | Both are sound (problem-fact-dependent), but a solution-level provider requiring per-entity lookup logic duplicated across every access is more error-prone than an entity-level method with direct access to `this.dayConfig` — implementer should confirm this shape compiles cleanly against the `ValueRangeProvider` placement rules before committing |
| A4 | SHLB-07's set-cover can be solved by plain exhaustive/greedy search without any solver library | §Don't Hand-Roll, Alternatives Considered | If candidate counts are larger than the "tens" the ROADMAP assumes (e.g. a desk with many distinct contracted-hours values × many break-offset steps), exhaustive search could become slow — the Discretion section explicitly leaves enumeration bounds to the planner; if real data shows hundreds of candidates, a simple greedy-with-verification approach (not exhaustive) should be used instead, still without a new dependency |

**Note on package-name provenance:** no packages were discovered via WebSearch or training data in
this research — every fact above was either read directly from the repository this session or
extracted directly from the pinned Timefold 1.16.0 JAR's `solver.xsd`/sources. There is therefore no
`[ASSUMED]` package name in this document.

## Open Questions

1. **Exact `Break clustering` penalty formula and clustering-denominator semantics (A1/A2 above).**
   - What we know: the constraint must penalise `agentsOnBreak > thresholdPct% * agentsAssigned` per
     timeslot, in shift mode only.
   - What's unclear: whether "agentsAssigned" should include overflow seats, and whether the
     penalty should scale linearly with excess or use some other shape.
   - Recommendation: implement the linear-excess sketch in Pattern 4, then run the required
     ENVL-09 fixture (single-band starves a mid-shift timeslot; multi-band does not) to validate the
     formula empirically before locking it in — this is explicitly the requirement's own acceptance
     test, per CONTEXT.md's Specific Ideas section.

2. **Whether `shiftEnvelopeCompliance` should also penalise a `null` chosen shift when the agent
   has at least one seat that day, or whether that's fully covered by `contractedHoursUnder`/
   `UnderZero` alone (D-06's stated rationale).**
   - What we know: D-06 explicitly rejects a dedicated "unassigned shift" penalty, reasoning that
     `contractedHoursUnder`/`UnderZero` already mean exactly this.
   - What's unclear: whether `shiftEnvelopeCompliance`'s join (`AgentAssignment` ×
     `AgentShiftAssignment` on `(agent,date)`) needs an explicit `ifNotExists`/null-shift branch, or
     whether a `null` shift on the `AgentShiftAssignment` side naturally forbids every seat via the
     positive-join form (CONTEXT.md's Discretion recommendation) already firing for every seat that
     agent-day.
   - Recommendation: trace through the plain positive-join form's behaviour when the joined
     `AgentShiftAssignment.shiftBandPair` is null — if `covers()`-equivalent logic on a null pair
     naturally evaluates to "does not cover," the join fires the hard penalty for every seat that
     day, achieving the same effect as an explicit branch. Verify with a unit test before assuming.

3. **The real-scale performance question SPIKE-COUPLING.md's "What remains open" item 1 left
   explicitly untested** (the extra `AgentAssignment × AgentShiftAssignment` join's cost at 30
   agents / 19+ constraints).
   - What we know: the toy spike's 24-entity fixture showed no performance concern, but the spike
     itself explicitly says "do not quote any number from this spike as a performance figure."
   - What's unclear: whether the extra join materially slows down real-scale solves.
   - Recommendation: this is exactly what D-16's "one non-comparative indicative real-desk run" is
     for — report it for scale, explicitly labelled indicative, not as a pass/fail criterion.

## Environment Availability

Skipped — this phase has no new external tool/service dependencies. Timefold, Spring Boot, Flyway,
PostgreSQL/H2, and the Node/npm frontend toolchain are all pre-existing project dependencies,
already verified available and in use by the phases this project has already shipped (Phase 14 and
earlier).

## Validation Architecture

`.planning/config.json`'s `workflow.nyquist_validation` was checked — not explicitly `false`, so
this section is included per the treat-as-enabled default.

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 (`useJUnitPlatform()`, `build.gradle:49`) + `ai.timefold.solver:timefold-solver-test`'s `ConstraintVerifier` for constraint-unit tests [VERIFIED: `build.gradle:43-44`] |
| Config file | `build.gradle` (no separate JUnit platform config file found) |
| Quick run command | `./gradlew test --tests "com.wfm.solver.*"` (existing solver-package test scope) |
| Full suite command | `./gradlew test` (required for any `solverConfig.xml` change per the standing XCUT-03/Phase-12 lesson — a scoped solver-package run cannot catch a Spring-context regression, but a full-suite run CAN catch `SolverConfigBuildTest` and `ScheduleConstraintClassificationTest` failures, both of which are plain JUnit, no Spring context needed) |

Frontend: **no test framework is present** in `frontend/package.json` (confirmed by grep this
session, and consistent with the explicit Phase 13 decision "no frontend test framework
introduced" recorded in STATE.md). ENVL-10's Agent Allocation view change and SHLB-07's suggestion
panel therefore rely on manual/UAT verification, consistent with every prior frontend phase in this
project.

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| ENVL-01/02 | Solver assigns exactly one shift/agent-day; never seats outside envelope | unit + constraint-stream (`ConstraintVerifier`) | `./gradlew test --tests com.wfm.solver.ScheduleConstraintProviderTest` (name TBD by planner) | ❌ Wave 0 — new test class needed |
| ENVL-06 | Feasible initial solution, no pre-assignment pipeline | integration (real `SolverFactory`) | `./gradlew test --tests com.wfm.solver.SolverConfigBuildTest` | ❌ Wave 0 |
| ENVL-07 | Score agrees with independent ground-truth check | integration (solve + walk) | `./gradlew test --tests com.wfm.solver.ShiftEnvelopeGroundTruthTest` (name TBD) | ❌ Wave 0 |
| ENVL-05/XCUT-05 | 4 break constraints mode-gated, provably unchanged for slot desks | unit (existing suite must stay green + one new shift-mode fixture) | `./gradlew test --tests com.wfm.solver.BreakAwareConstructionTest` (existing, must stay green + unmodified) plus a new shift-mode fixture test | ✅ existing test exists; ❌ new shift-mode fixture needed |
| ENVL-09 | Real `Break clustering` body, demonstrable single-band-starves/multi-band-doesn't | unit (constraint-specific fixture) | New test class, name TBD | ❌ Wave 0 |
| SHLB-07 | Suggested draft passes SHLB-05/06 validation with zero uncovered windows | integration (service-level) | New test class in `src/test/java/com/wfm/service/` | ❌ Wave 0 |
| XCUT-03 | `solverConfig.xml` change validated by a real solver build | integration | `SolverConfigBuildTest` (above) | ❌ Wave 0 |
| XCUT-04 | Seeded A/B benchmark, system-property gated | benchmark (excluded from default suite) | `./gradlew test --tests com.wfm.solver.ShiftModelBenchmarkTest -Dwfm.benchmark=true` (mirrors Phase 12's `AtomicShiftMoveBenchmarkTest` invocation exactly, read this session at `12-BENCHMARK.md:5`) | ❌ Wave 0 |

### Sampling Rate

- **Per task commit:** `./gradlew test --tests "com.wfm.solver.*"` (fast solver-package scope for
  routine iteration)
- **Per wave merge:** `./gradlew test` (full suite — mandatory for any `solverConfig.xml` or
  `ScheduleConstraintProvider.java` change, per the standing lesson that no solver-package-scoped
  run loads the Spring context)
- **Phase gate:** Full suite green before `/gsd-verify-work`, plus the benchmark run
  (`-Dwfm.benchmark=true`) executed and recorded in `15-BENCHMARK.md` before the piloting
  recommendation is made (D-13)

### Wave 0 Gaps

- [ ] `src/test/java/com/wfm/solver/SolverConfigBuildTest.java` — covers XCUT-03, ENVL-06
- [ ] `src/test/java/com/wfm/solver/ShiftEnvelopeGroundTruthTest.java` — covers ENVL-07
- [ ] A shift-mode fixture added to (or alongside) `BreakAwareConstructionTest.java` or a new
      sibling test — covers ENVL-04/05, proves the 4 break constraints are inert in shift mode
- [ ] A new constraint-unit test for `shiftEnvelopeCompliance` — covers ENVL-01/02/03
- [ ] A new constraint-unit test for the real `Break clustering` body, with the required
      single-band-starves/multi-band-doesn't fixture — covers ENVL-08/09
- [ ] `src/test/java/com/wfm/service/ShiftLibraryGenerationServiceTest.java` (name TBD) — covers
      SHLB-07, including the D-12 partial-coverage and zero-demand-refusal cases
- [ ] `src/test/java/com/wfm/solver/ShiftModelBenchmarkTest.java` (name TBD, gated behind
      `-Dwfm.benchmark=true`, mirroring `AtomicShiftMoveBenchmarkTest`'s exact harness shape) —
      covers XCUT-04
- [ ] Framework install: none — JUnit 5 and `timefold-solver-test` are already present

## Security Domain

No `security_enforcement` key was found set to `false` in `.planning/config.json` reviewed for this
research — treated as enabled per the absent-means-enabled default.

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | No | This phase adds no new authentication surface |
| V3 Session Management | No | No session-related change |
| V4 Access Control | Yes | Every new entity (`AgentShiftAssignment`, `ShiftTemplateBreakBand`) must carry `tenant_id` and be read only through `TenantContext`-scoped repository methods — the existing project-wide multi-tenancy pattern (application-code-enforced, no DB row security), confirmed as the standing convention across every existing entity read this session (`ShiftTemplate`, `AgentAssignment`, `ConstraintWeights`, etc., all carry `tenant_id`) |
| V5 Input Validation | Yes | `ShiftTemplateBreakBand`'s `capacity` field (D-03: null = unlimited) must be validated non-negative when set, mirroring `ShiftTemplateService.validate`'s existing non-negative checks on `breakOffsetMinutes`/`breakDurationMinutes` (read this session, lines 121-123); SHLB-07's generated draft must go through the *same* `ShiftTemplateService.validate` path on save (D-11), not a bypass |
| V6 Cryptography | No | No cryptographic operation introduced |

### Known Threat Patterns for this stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Cross-tenant data leakage via a missing `tenant_id` filter on a new repository method | Information Disclosure | Every new repository method for `AgentShiftAssignment`/`ShiftTemplateBreakBand` must take `tenantId` as an explicit parameter and filter on it, matching every existing repository method's signature pattern (e.g. `findByTenantIdAndDeskId`, confirmed as the universal convention this session) |
| A `PreSolveValidationException` detail leaking another tenant's data (e.g. in an uncovered-window message) | Information Disclosure | `ErrorDetail`'s `value` field (read this session, `ErrorResponse.java:7`) should carry only the current tenant's own template/desk names, consistent with how `ShiftLibraryValidationService` already scopes every query through `TenantContext.getTenantId()` |
| A generated SHLB-07 draft silently applied without operator review | Tampering (of intent, not data) | D-11 already mandates a stateless, non-persisted draft — "nothing written until they save" — this is itself the mitigation; no additional control needed beyond enforcing that the generation endpoint never calls any persistence method |

## Sources

### Primary (HIGH confidence — read/extracted directly, this session)

- `timefold-solver-core-1.16.0.jar` (`solver.xsd`, extracted and read directly this session) —
  confirmed `<entitySelector>`'s `id`/`mimicSelectorRef` are XML attributes and `entityClass` is a
  nested element; confirmed the top-level `<solver>` schema permits `maxOccurs="unbounded"`
  sequential `<constructionHeuristic>` phases
- `timefold-solver-core-1.16.0-sources.jar` (`ConstructionHeuristicPhaseConfig.java`,
  `QueuedEntityPlacerConfig.java`, `EntitySelectorConfig.java`, `AbstractFromConfigFactory.java`,
  `PlanningVariable.java`, `ValueRangeProvider.java` — all read directly this session)
- `.planning/research/SPIKE-COUPLING.md` (read in full this session)
- `.planning/research/ARCHITECTURE.md` (read in full this session)
- `.planning/phases/15-shift-envelope-breaks-library-generation/15-CONTEXT.md` (read in full this
  session)
- `.planning/ROADMAP.md` § Phase 15 (read in full this session)
- `.planning/REQUIREMENTS.md` (read in full this session)
- `.planning/STATE.md` (read in full this session)
- Codebase files read directly this session: `src/main/resources/solverConfig.xml`,
  `src/main/java/com/wfm/model/ScheduleConfig.java`, `AgentDayConfig.java`, `AgentAssignment.java`,
  `ShiftTemplate.java`, `Schedule.java`, `ConstraintWeights.java`, `Desk.java` (schedulingMode
  field), `src/main/java/com/wfm/service/ShiftTemplateService.java`,
  `ShiftLibraryValidationService.java`, `src/main/java/com/wfm/solver/ScheduleConstraintProvider.java`
  (multiple ranges), `src/main/java/com/wfm/service/SolverService.java` (multiple ranges),
  `src/main/resources/db/migration/V39__add_shift_template_and_scheduling_mode.sql`,
  `src/test/java/com/wfm/solver/ScheduleConstraintClassification.java`,
  `.planning/phases/14-shift-library-scheduling-mode/XCUT-05-constraint-classification.md`,
  `.planning/milestones/v1.2-phases/12-atomic-shift-move/12-BENCHMARK.md`,
  `frontend/src/pages/ScheduleResults.tsx` (grep for `AgentAllocationTab`), `frontend/src/pages/ShiftLibrary.tsx`
  (grep for break fields), `build.gradle` (Timefold version, test framework), `src/main/java/com/wfm/dto/ErrorResponse.java`,
  `src/main/java/com/wfm/model/AgentDayHours.java`, `src/main/java/com/wfm/repository/StaffingRequirementRepository.java`
- `ls src/main/resources/db/migration/` (this session) — confirmed V39 is the latest applied
  migration on disk; next is V40

### Secondary (MEDIUM confidence)

- The exact `Break clustering` penalty formula (Pattern 4) — Constraint Streams API usage is
  HIGH confidence (mirrors proven existing patterns in the same file), but the specific penalty
  shape is a design proposal requiring empirical validation against the required fixture, not a
  verified fact.

### Tertiary (LOW confidence)

- None — no WebSearch was needed for this research; every open question was resolvable by reading
  the codebase and the pinned JAR directly.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — no new dependencies, everything confirmed against `build.gradle` directly
- Architecture (coupling, CH XML): HIGH — the CH XML shape (this phase's only prior MEDIUM item) is
  now verified directly against the compiled `solver.xsd` inside the pinned JAR; the coupling
  mechanism itself is inherited HIGH confidence from `SPIKE-COUPLING.md`
- Break clustering formula: MEDIUM — API shape is HIGH, exact formula is a design proposal pending
  the fixture-based empirical validation ROADMAP requires
- SHLB-07 approach: MEDIUM-HIGH — the "exhaustive/greedy over tens of candidates" approach is
  strongly supported by ROADMAP's own sizing argument, but exact enumeration bounds are left to
  planner discretion per CONTEXT.md
- Pitfalls: HIGH — every pitfall is grounded in an actual prior incident in this codebase (Phase 12
  withdrawal, the spike's Option C finding, UAT gap G-14-1, audit NEW-1/I-1)

**Research date:** 2026-08-26
**Valid until:** Timefold 1.16.0 is pinned and stable for this milestone (no version-drift risk);
treat this research as valid for the duration of Phase 15's execution — re-verify the CH XML shape
only if the pinned Timefold version ever changes.
