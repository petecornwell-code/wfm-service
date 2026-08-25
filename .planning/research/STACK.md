# Stack Research

**Domain:** Adding a second, coupled planning dimension (shift envelope) to an existing Timefold Solver 1.16.0 constraint-satisfaction model
**Researched:** 2026-08-25
**Confidence:** MEDIUM overall (see per-claim notes — Context7 was unavailable in this session; all API claims are corroborated across the official `docs.timefold.ai` version-specific pages, the `TimefoldAI/timefold-solver` GitHub release notes for v1.16.0, and the official upgrade guide's 1.15.0→1.16.0 section, but the seam classified them LOW/web-tier rather than curated-docs-tier because they were fetched via WebSearch/WebFetch, not the Context7 MCP tool. Treat as MEDIUM: multiple independent official sources agree, but re-verify the exact annotation signatures against the vendored `timefold-solver-core-1.16.0.jar` Javadoc before writing code.)

## Headline Verdict

**The 1.16.0 pin survives this milestone. No version bump is required or justified.**

Everything this milestone needs — a second `@PlanningEntity` class, a second genuine `@PlanningVariable`, solution-level and entity-level `@ValueRangeProvider`, class-based `@ShadowVariable`/`VariableListener`, difficulty/strength comparators — has existed since Timefold's earliest public docs (0.8.x, 2023) and is unchanged through the 1.15.0→1.16.0 upgrade notes and beyond. Nothing in the target feature list requires `@PlanningListVariable`, chained variables, custom `Move` classes, the `Neighborhoods` API (1.31.0+), or declarative shadow variables (`@ShadowSources`, still PREVIEW as late as 1.25.0). Do not reach for any of those four — see "What NOT to Use" below.

## Recommended Stack

### Core Technologies (already present — no additions)

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| `ai.timefold.solver:timefold-solver-bom` | **1.16.0 (unchanged)** | Solver core, pinned per `build.gradle:35` | Every primitive this milestone needs predates 1.16.0; bumping buys nothing and crosses into the 2.0 paid-tier risk the pin exists to avoid |
| `ai.timefold.solver:timefold-solver-spring-boot-starter` | 1.16.0 | Spring Boot autoconfig for `SolverManager` | Already wired into `SolverService` |
| `ai.timefold.solver:timefold-solver-jpa` | 1.16.0 | JPA integration (`HardSoftScoreConverter` used on `Schedule.score`) | Already the mechanism reconciling the dual JPA-`@Entity`/Timefold-`@PlanningEntity` role on `AgentAssignment`; the new shift entity should follow the identical pattern rather than introduce a second convention |

### New Domain Classes Needed (modelling only — zero new dependencies)

| Class | Kind | Purpose |
|-------|------|---------|
| `Shift` | JPA `@Entity`, plain problem fact (**not** a `@PlanningEntity`) | Per-desk shift definition: name, `startTime`/`endTime`, break offset/duration, optional weekday applicability. Modelled exactly like `Specialization`/`Agent` today — a fact the solver reads, never mutates. |
| `ShiftAssignment` (name TBD by phase planner) | JPA `@Entity` **and** `@PlanningEntity` (dual role, mirroring `AgentAssignment`) | One row per (agent, date) on a shift-scheduled desk. Carries `@PlanningVariable(valueRangeProviderRefs = "shiftRange", nullable = true) Shift shift` — `nullable = true` mirrors `AgentAssignment.agent`'s existing pattern and gives "no shift chosen" a natural meaning (day off). |
| `AgentUsualShift` (name TBD) | JPA `@Entity`, plain problem fact | (agent, weekday) → target `Shift`. Loaded as a `@ProblemFactCollectionProperty` list on `Schedule`, exactly parallel to the existing `AgentDayConfig` list. Feeds the soft consistency constraint; not a planning entity itself. |

None of this requires a new Gradle dependency. It is domain classes plus `ConstraintStream` logic against the solver already on the classpath.

### Supporting Libraries

*(No additions.)* Apache POI 5.3.0 already handles the per-desk upload template (add a "Usual Shift" column to the existing `EnrichedColumnLayout`, following the Phase 10 pattern — do not introduce a second column-layout mechanism). Flyway already handles schema evolution (expect new migrations for `shift`, `shift_assignment`, `agent_usual_shift` tables and a `desk.scheduling_mode` column — table design is an ARCHITECTURE-level decision, not a stack addition). React/the existing frontend stack is unaffected at the library level; the drift report and Usual Shift inline editor are new UI surfaces built with what's already there.

### Development Tools

*(No additions.)* `timefold-solver-test` is already a test dependency and is the correct tool for constraint-stream unit tests on the new coupling constraints (`ConstraintVerifier`), same as existing tests presumably exercise `ScheduleConstraintProvider`.

## Installation

No `build.gradle` changes required for this milestone's Timefold surface. If a `desk.scheduling_mode` enum or similar needs Jackson/JPA converter support, that's standard Spring Boot/Hibernate already on the classpath — no new dependency.

```bash
# Nothing to install for the solver model itself.
# Only additions are Flyway migration files and Java/React source — no new packages.
```

## Question-by-Question Findings

### 1–2. What primitives exist, and at what version did each land?

| Primitive | Package (verify exact FQN against local Javadoc) | Landed | Available at 1.16.0? | Needed for this milestone? |
|---|---|---|---|---|
| Multiple `@PlanningEntity` classes in one `@PlanningSolution` | `ai.timefold.solver.core.api.domain.entity.PlanningEntity`, `ai.timefold.solver.core.api.domain.solution.PlanningEntityCollectionProperty` | 0.8.x docs and earlier (OptaPlanner-era) | **YES** | **YES — this is the core recommended shape** (see below) |
| `@ShadowVariable` + class-based `VariableListener` | `ai.timefold.solver.core.api.domain.variable.ShadowVariable`, `...VariableListener` | 0.8.x docs (already shown as the modern replacement for the older `@CustomShadowVariable`) | **YES** | Optional, not required for the primary design (see §3 below) |
| `@PiggybackShadowVariable`, `@InverseRelationShadowVariable`, `@AnchorShadowVariable` | same package | 0.8.x docs | **YES** | Not needed — no chaining in this design |
| `@PlanningListVariable` | `ai.timefold.solver.core.api.domain.variable.PlanningListVariable` | 0.8.x docs (list variables are mature, not new) | **YES, but wrong tool** | **NO — do not use** (see "What NOT to Use") |
| `@PreviousElementShadowVariable` / `@NextElementShadowVariable` | same package | 0.8.x docs | YES | Not needed (only relevant to list variables) |
| Chained variables (`@AnchorShadowVariable` + `previousStandstill`-style pattern) | same package | 0.8.x docs, pre-dates list variables | YES, but **legacy** — Timefold's own docs position list variables as the modern replacement for new designs | **NO — do not use** |
| `@ValueRangeProvider` — solution-level | `ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider` | 0.8.x docs | YES | **YES — for the shift catalogue** (`shiftRange`, already-used pattern: see `Schedule.agents`/`agentRange`) |
| `@ValueRangeProvider` — entity-level (method on the `@PlanningEntity`, may read other variables' current state on that instance) | same annotation, placed on an instance method of the entity class | 0.8.x docs | YES | Optional refinement (weekday-filtered shift range) — see §3 |
| `@PlanningPin` | `ai.timefold.solver.core.api.domain.variable.PlanningPin` | Pre-1.16 (OptaPlanner-era; deprecated `pinningFilter` attribute is the older mechanism it replaced) | YES | **NOT needed this milestone** — no continuous/real-time replanning requirement in scope; the per-desk mode switch is a config flag, not a pinning problem |
| Difficulty comparator (`difficultyComparatorClass` on `@PlanningEntity`) | same package as `PlanningEntity` | Pre-1.16 | YES — **already in production use** (`AgentAssignmentDifficultyComparator`, `src/main/java/com/wfm/solver/AgentAssignmentDifficultyComparator.java`) | Recommended for the new `ShiftAssignment` entity too, same pattern |
| Strength comparator (`strengthComparatorClass`/`strengthWeightFactoryClass` on `@PlanningVariable`) | same vintage | Pre-1.16 | YES | Optional — could order shift construction by e.g. shift length, not required for MVP |
| Custom `Move` with framework-generated undo (`AbstractMove.doMoveOnGenuineVariables`) | `ai.timefold.solver.core.impl.heuristic.selector.move.generic.AbstractMove` (impl package — not public API) | **Landed exactly at 1.16.0** per GitHub release notes ("developers no longer need to implement undo moves… deprecated for future removal") | YES, and this is the specific 1.16.0 feature PROJECT.md already anchors the pin to | **Not needed for this milestone** — no custom `Move` is proposed (see below) |
| `Neighborhoods` custom-move API | — | **1.31.0** | **NO** | Not needed; confirms the pin holds |
| Declarative shadow variables (`@ShadowSources`, supplier-based) | new package, still evolving | Introduced as a **PREVIEW** feature sometime after 1.16.0 (still PREVIEW as of 1.25.0 per `TimefoldAI/timefold-solver` discussion #1837/#1569, "Full Support" tracked separately as issue #1519) | **NO — not stable at any version this project could reach without moving off the 1.x line, and still not GA even well past 1.16.0** | Not needed — classic `@ShadowVariable` covers everything this milestone could want from shadow state |

### 3. Does anything force a version bump?

**No.** Cross-checked explicitly: the official upgrade guide's version-by-version changelog shows nothing touching `PlanningEntity`, `PlanningVariable`, `PlanningListVariable`, `ShadowVariable`, or `ValueRangeProvider` semantics in the 1.16.0→1.19.0 window (the only changes at 1.16.0 are the undo-move simplification, JSpecify null annotations, and a `ScoreAnalysis` fetch-policy behavior change; 1.19.0 changed two unrelated internal-API signatures). The one genuinely new 1.16.0-era capability — auto-generated undo for custom moves — is not something this milestone needs to invoke, because the recommended design (below) uses zero custom `Move` classes. If a later milestone wants an atomic "swap this agent's whole shift" move (the kind of thing Phase 12's withdrawn Atomic Shift Move was reaching for), that's still buildable at 1.16.0 using `AbstractMove.doMoveOnGenuineVariables` — the exact API PROJECT.md already names — with **no bump required for that either**. The only thing that *would* force a bump is the `Neighborhoods` API (1.31.0) or declarative shadow variables reaching GA (version unknown, still preview past 1.25.0), and neither is proposed here.

### Recommended shape for the "two coupled planning variables" problem

This is the load-bearing design decision for the milestone, so stating it plainly:

**Use two independent `@PlanningEntity` classes joined by `ConstraintStream` hard constraints — not a shadow variable, not a filtered entity-level value range, not a custom move.**

1. `ShiftAssignment` gets its own genuine `@PlanningVariable Shift shift`, value range = the desk's shift library (`@ValueRangeProvider(id = "shiftRange")` on `Schedule`, same pattern as the existing `agentRange`).
2. `AgentAssignment` keeps its existing genuine `@PlanningVariable Agent agent`, completely unchanged.
3. In `ScheduleConstraintProvider`, add hard constraints that **join** `AgentAssignment` (non-null `agent`) to `ShiftAssignment` on `(agentId, date)` — the codebase already has static `AGENT_ID`/`DATE` lambda instances for exactly this kind of join (see the block comment at the top of `ScheduleConstraintProvider.java` explaining why shared lambda *instances* matter for node-sharing performance) — and penalize:
   - the assignment's timeslot falling outside `[shift.startTime, shift.endTime)`, and
   - the assignment's timeslot falling inside the shift's break window.
4. This is exactly the same `ConstraintStream`/`Joiners`/`ConstraintCollectors` machinery already used throughout the existing constraint provider. Zero new Timefold primitives.

**Why not the alternatives:**
- *Entity-level filtered `ValueRangeProvider` on `AgentAssignment.agent`* — technically legal (entity-level ranges can read sibling state), but the wrong tool here: the envelope constrains "is the chosen agent's own shift-day covering this slot," which is a property of the (assignment, chosen-agent) pair, not something that can be pre-filtered into the candidate-agent list without first knowing which agent you're evaluating — it inverts the dependency. A hard-constraint join evaluates the actual combination directly and cheaply.
- *A `@ShadowVariable` propagating the chosen `Shift` onto each `AgentAssignment`* — possible (a `VariableListener` sourced from `ShiftAssignment.shift`, keyed by (agent, date)), and could be used later purely as a **read-time cache** if constraint-join cost ever becomes a measured bottleneck, but it adds a `VariableListener` and its invalidation-order complexity for a benefit the existing `Joiners.equal`-based group-join already gets close to for free. Don't reach for it until profiling says otherwise — this project already has one documented case (the `AGENT_ID`/`DATE` lambda-hoisting comment) of fixing constraint performance by restructuring the constraint stream rather than adding solver machinery; follow that precedent.
- *A custom `Move` that atomically swaps shift + all affected seat assignments* — this is what Phase 12's withdrawn Atomic Shift Move was reaching for and it was reverted for showing no measurable benefit over the baseline at realistic over-allocation. Nothing in this milestone's target feature list requires it. If a future milestone wants it, it's still available at 1.16.0 via `AbstractMove.doMoveOnGenuineVariables`, but don't build it speculatively.
- **Contiguity "by construction"** falls directly out of step 3 above — the milestone claims fragmented shift-days become structurally impossible on shift-scheduled desks. There is no special Timefold "contiguity" primitive at 1.16.0 (or at any version) to reach for; it's an emergent property of the envelope hard-constraint, not an API.

### 4. New libraries needed?

**No.** Every question above resolves to "already on the classpath, use it differently." Resist the urge to add anything — this is domain modelling against a solver that's already present. Specifically **not needed**: any Timefold module beyond `-core`/`-spring-boot-starter`/`-jpa` (already present), no new POI features, no new Flyway plugin, no new frontend charting/scheduling library for the drift report (a table is sufficient; if a visual timeline is wanted later, that's a FEATURES/UI decision, not a stack one).

### 5. Do existing Flyway/JPA/POI/React choices constrain the shift model?

**Yes, three concrete consequences:**

1. **Dual JPA-`@Entity`/`@PlanningEntity` role must be replicated carefully for `ShiftAssignment`, following the existing discipline exactly.** `SolverService.startSolve` is `@Transactional(readOnly = true)` specifically so "all data is loaded in a single read and the persistence context closes… detaching all entities" (see the method's own doc comment, `SolverService.java:101-106`) before the solver ever touches them. This is what makes it safe for `AgentAssignment` to carry both `@Id`/`@ManyToOne` JPA annotations and `@PlanningVariable`/`@PlanningId` Timefold annotations — Timefold's default `FieldAccessingSolutionCloner` clones plain detached POJOs, never Hibernate-managed proxies. **Any new relation on `ShiftAssignment` (`shift`, `agent`) must be pre-fetched or explicitly initialized before the read-only transaction closes**, exactly as `SolverService` already does for `detachedAgents` (`SolverService.java:263-314`) — a lazy `@ManyToOne` touched after detachment will throw `LazyInitializationException` the first time a constraint stream navigates `.getShift()`.
2. **`Schedule` is already desk-scoped** (`deskId` field) and already carries a `@ValueRangeProvider(id = "agentRange")` list plus multiple `@ProblemFactCollectionProperty` lists of different types alongside a single `@PlanningEntityCollectionProperty List<AgentAssignment>`. Adding a second `@PlanningEntityCollectionProperty List<ShiftAssignment> shiftAssignments` field is additive and follows an established pattern in this exact class — no structural surprise for JPA (mark it `@Transient`, same as every other solver-only collection on `Schedule` already is).
3. **Per-desk mode switch means shift-scheduled-desk collections must degrade gracefully to empty on slot-scheduled desks**, and vice versa. `Schedule` already has several `@ProblemFactCollectionProperty` lists that are legitimately empty for some desks (e.g. `agentPreferences`, `agentExceptions`) and the constraint provider already tolerates that. The new coupling constraints (step 3 in the recommended shape above) should be written to no-op cleanly when `shiftAssignments` is empty — don't special-case "slot mode" with an `if` in `SolverService`; let an empty collection do the work, consistent with how the rest of `Schedule`'s optional fact lists already behave.

POI and React are not meaningfully constrained — the Usual Shift column is one more polymorphic-style column in the existing `EnrichedColumnLayout` shared definition (the Phase 10/13 pattern this project already fought to establish and closed the last holdout on — reuse it, don't fork a second layout definition), and the drift report is a new read surface, not a new library.

## Alternatives Considered

| Recommended | Alternative | Why Not |
|-------------|-------------|---------|
| Two independent `@PlanningEntity` classes joined via `ConstraintStream` | `@PlanningListVariable` modelling a shift as an ordered list of its constituent slots | Wrong abstraction — list variables model *sequence/order* (which visit comes after which); the shift/seat relationship here is *containment* (which seat-slots fall inside which time envelope), and specialization can still vary per slot inside the envelope. Forcing this into a list variable would also drag in `@PreviousElementShadowVariable`/`@NextElementShadowVariable` machinery for no purpose. |
| Hard-constraint join for envelope enforcement | `@ShadowVariable`-propagated "effective shift" on `AgentAssignment` | Adds a `VariableListener` and invalidation-order surface area for a cost the existing group-join pattern already amortizes; defer until profiling shows the join is the bottleneck |
| Hard-constraint join | Chained variables / `@AnchorShadowVariable` | Legacy mechanism Timefold's own docs position as superseded by list variables for new work; doesn't fit this shape any better than list variables do, and drags in even more shadow-variable surface area |
| Stay at 1.16.0 | Bump to 1.31.0+ for `Neighborhoods` custom-move API | Nothing in scope needs custom moves at all, let alone the newer API for them; a bump crosses into paid-tier risk the pin exists specifically to avoid |

## What NOT to Use

| Avoid | Why | Use Instead |
|-------|-----|-------------|
| `@PlanningListVariable` for the shift/seat relationship | Models ordered sequences, not time-envelope containment; wrong fit even though it's available at 1.16.0 | Two independent `@PlanningEntity` classes + `ConstraintStream` join |
| Chained variables (`@AnchorShadowVariable` + standstill pattern) | Legacy pre-list-variable mechanism, same wrong fit as above, plus more moving parts | Same as above |
| Declarative shadow variables (`@ShadowSources`) | Still PREVIEW well past 1.16.0 (unclear GA version); not usable without risking instability even if a version bump were on the table | Classic `@ShadowVariable(variableListenerClass=..., sourceVariableName=...)` — but see below, prefer not needing shadow variables at all for v1 |
| A custom `Move` class for atomic shift+seat swaps, built speculatively | Exactly what Phase 12's withdrawn Atomic Shift Move attempted; reverted for no measurable benefit at realistic over-allocation. Nothing in this milestone's scope requires it | Plain genuine-variable moves via the two-entity model; revisit only if profiling after shipping shows a concrete need |
| Bumping past 1.16.0 for any reason tied to this milestone | Crosses into `Neighborhoods`/2.0-paid-tier territory the pin was specifically set to avoid, for zero capability gain — every primitive needed here predates 1.16.0 | Stay pinned at 1.16.0 |
| An entity-level filtered `@ValueRangeProvider` restricting `AgentAssignment.agent` by shift coverage | Inverts the actual dependency (need to know the chosen agent before you can check their shift), awkward and slower than a direct join | Hard-constraint join in `ScheduleConstraintProvider` |

## Stack Patterns by Variant

**If a later milestone wants live re-planning that must not disturb already-published shift-days:**
- Use `@PlanningPin` on `ShiftAssignment` (available since well before 1.16.0, unused today)
- Because it's the standard mechanism for "don't move entities the operator has already locked in," and needs no new dependency

**If constraint-join cost on the (agent, date) grain is later measured to be a real bottleneck (not assumed — measured, the way Phase 12's benchmark discipline already established for this codebase):**
- Add a `@ShadowVariable`-propagated back-reference from `AgentAssignment` to its `ShiftAssignment`
- Because it turns a composite `(agentId, date)` join into a direct reference read, at the cost of one `VariableListener`

## Version Compatibility

| Package A | Compatible With | Notes |
|-----------|-----------------|-------|
| `ai.timefold.solver:timefold-solver-bom:1.16.0` | Every primitive in this document | Confirmed via official upgrade guide's 1.15.0→1.16.0 and 1.16.0→1.19.0 sections — no breaking or additive changes to any of `PlanningEntity`/`PlanningVariable`/`PlanningListVariable`/`ShadowVariable`/`ValueRangeProvider` in that window |
| Existing `AgentAssignmentDifficultyComparator` pattern | New `ShiftAssignment` entity | Same `difficultyComparatorClass` mechanism, no version sensitivity |
| `timefold-solver-jpa` score converters | `Schedule.score` only today | Confirms the JPA-integration module is already in play for exactly the dual-role problem a new planning entity will re-encounter; no new converter needed unless `ShiftAssignment` gets its own persisted score-like field (not proposed) |

## Sources

- `docs.timefold.ai/timefold-solver/0.8.x/shadow-variable/shadow-variable` — confirmed `@ShadowVariable`, `@PiggybackShadowVariable`, `@InverseRelationShadowVariable`, `@AnchorShadowVariable`, `@PreviousElementShadowVariable`, `@NextElementShadowVariable` all present at the earliest publicly documented version — LOW/web-tier per the research seam (WebFetch, not Context7), cross-checked against a second independent WebSearch pass, treat as MEDIUM in practice
- `docs.timefold.ai/timefold-solver/1.x/upgrading-timefold-solver/upgrade-to-latest-version` — version-by-version changelog, confirmed exact 1.16.0 changes (auto-generated undo moves, JSpecify, `ConstraintAnalysis.matchCount()`) and confirmed no changes to the domain-modelling annotations through 1.19.0 — LOW/web-tier, official source
- `github.com/TimefoldAI/timefold-solver/releases/tag/v1.16.0` — official release notes, corroborates the upgrade guide — LOW/web-tier, official source
- `timefold.ai/blog/mixed-models-timefold-solver` — official Timefold blog confirming the multi-entity/mixed-variable-type modelling pattern with code examples — LOW/web-tier
- `github.com/TimefoldAI/timefold-solver` discussions #1837, #1569, issue #1519 — confirms declarative shadow variables (`@ShadowSources`) remained PREVIEW-status past 1.25.0 — LOW/web-tier, but consistent across three independent GitHub threads
- Local: `build.gradle:35` (pin verified directly), `AgentAssignment.java`, `Schedule.java`, `AgentAssignmentDifficultyComparator.java`, `ScheduleConstraintProvider.java` (header comment on lambda-instance node sharing), `SolverService.java:101-314` (detached-entity transaction discipline) — HIGH confidence, read directly from the repository

**Gap:** Context7 was not invoked this session (no `mcp__context7__*` tool calls were made — the environment's available tool list for this run did not surface it as callable). All Timefold API claims above rest on official `docs.timefold.ai` and `github.com/TimefoldAI` pages fetched via WebFetch/WebSearch, cross-checked against each other and against the version-specific docs URLs (`/0.8.x/`, `/1.x/`) rather than a single "latest" page. Recommend a phase planner do one direct Javadoc check against the vendored `timefold-solver-core-1.16.0.jar` for exact annotation attribute names (`sourceVariableName` vs `sourceVariableNames`, etc.) before writing the constraint-provider code, since attribute-name-level precision was not independently confirmed against version-pinned Javadoc in this pass.

---
*Stack research for: shift-level scheduling as a second planning dimension in Timefold 1.16.0*
*Researched: 2026-08-25*
