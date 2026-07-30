# Phase 9: Agent Data Model Foundation - Research

**Researched:** 2026-07-30
**Domain:** JPA entity modeling + Flyway data migration + solver problem-fact resolution (Spring Boot / Timefold)
**Confidence:** HIGH (all claims verified directly against live repository code, not training-data assumptions)

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Migration — scalar → per-day hours (MDL-03, highest risk)**
- **D-01:** Migrate each existing agent's scalar `contractedHoursPerDay` to **all 7 day-of-week slots** (Mon–Sun). This exactly reproduces today's `getEffectiveHours` behaviour — the scalar already applies to any date not excluded by a day-off row — so there is provably no solve regression (Success Criterion 4). Days the agent doesn't work stay excluded via their existing `MANDATORY AgentDayOff` rows.
- **D-02:** For an existing agent whose scalar is **NULL** today (currently resolves via the `Schedule.defaultContractedHoursPerDay` = 8.00 fallback): **leave all 7 per-day slots empty**. Do not backfill 8.00 — that would hard-code today's schedule default onto the agent and diverge if a schedule later uses a different default. They keep resolving via the schedule-default fallback (see D-04).

**Per-day resolution semantics (MDL-02)**
- **D-03:** Resolution precedence for a given agent+date is: **`AgentException` date-override → per-day value for that weekday → `Schedule.defaultContractedHoursPerDay`.** `AgentException`, dated PTO, and MANDATORY day-offs are untouched and compose exactly as they do today.
- **D-04:** A weekday with **no per-day record** → fall back to the schedule default (preserves today's null-scalar behaviour). A per-day record that **exists and is `0`** → agent does **not work** that weekday (no `AgentDayConfig` emitted). Storage MUST therefore represent "absent" distinctly from `0` (satisfied by D-08... [see D-09]). This satisfies both the null-scalar migration and Phase 10's "0/blank on a contracted-hours column = day not worked" rule.
- **D-05:** In Phase 9 both "recurring not-worked" mechanisms coexist deliberately: a weekday can be excluded via a `MANDATORY AgentDayOff` (from BambooHR field 4517) **and/or** a per-day `0`. "Not worked" is the union. This redundancy is intentional and acceptable for a foundation phase — consolidating so Mon–Sun hours become *the* authority is Phase 10/11 merge work, not Phase 9.

**Name split (MDL-01/03)**
- **D-06:** Split heuristic is **first whitespace token → firstName, remainder → lastName** (`"Mary Jane Watson"` → first=`"Mary"`, last=`"Jane Watson"`). Single-token name → firstName set, lastName empty. Best fit for BambooHR-sourced "First Last" display names.
- **D-07:** The same split rule is applied in **two places**: (1) the one-time Flyway migration of existing `name` values, and (2) the ongoing BambooHR refresh, which supplies only a combined `displayName` — `BambooRefreshService:211` currently does `agent.setName(emp.displayName())` and must instead split into first/last so the refresh keeps populating both columns. (Phase 10's spreadsheet will later supply first/last explicitly.)
- **D-08 (API/DTO compat):** Add `firstName` / `lastName` as new fields on the DTOs/exports, **and keep a derived combined `name`** (`firstName + " " + lastName`) in responses/exports/logs. No breaking change to the API or export format — the frontend adopts the new fields when ready. Aligns with the phase's no-regression goal.

**Storage shape (MDL-02)**
- **D-09:** Store per-day hours in a **new child table `agent_day_hours`** (`agent_id`, `day_of_week`, `hours`) with `@ManyToOne` back to `Agent`, consistent with the existing `AgentException` / `AgentDayOff` / `AgentPreference` conventions. **Absent = no row** for that weekday; **not-worked = a row with `0.00`.** This is the locked contract that Phase 10's parser and Phase 11's merge engine both write into. (Chosen over 7 nullable columns on the agent table, which would widen the row and fit the unbounded per-agent-day writes less naturally.)

### Claude's Discretion
- Exact `day_of_week` representation in `agent_day_hours` (e.g. `java.time.DayOfWeek` enum vs smallint 1–7), FK/unique-constraint layout, and precision/scale on `hours` — planner/researcher decide, mirroring the existing `contracted_hours_per_day` column (`precision = 5, scale = 2`). **Research recommendation: `@Enumerated(EnumType.STRING) DayOfWeek`, mirroring the existing `AgentPreference.dayOfWeek` column exactly (verified in code) — see Standard Stack / Don't Hand-Roll sections below.**
- Flyway version number and whether the migration is one script or split — planner decides. **Research recommendation: single script `V29__agent_first_last_name_and_day_hours.sql` — see Code Examples / Metadata.**

### Deferred Ideas (OUT OF SCOPE)
- **Consolidating the two "recurring not-worked" mechanisms** (per-day `0` vs MANDATORY `AgentDayOff` from field 4517) so Mon–Sun hours become the single authority — Phase 11 merge work (MRG), per STATE decision "Mon–Sun contracted hours are the authority on which days are worked."
- **Spreadsheet population** of per-day hours, first/last name, and recurring day-off/PTO columns — Phase 10 (UPL).
- **BambooHR↔spreadsheet per-field precedence and merge report** — Phase 11 (MRG).

None of these belong in Phase 9 — this phase only builds the model + migration + resolution the later phases write into.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-------------------|
| MDL-01 | Agent stores first name and last name as separate fields | New `firstName`/`lastName` columns on `Agent.java` + Flyway backfill via `AgentNameSplitter` rule (D-06); `BambooRefreshService:211` split point identified; DTO exposure via `AgentResponse`/`DeskAgentResponse` — see Architecture Patterns, Code Examples, Pitfalls 3 & 5 |
| MDL-02 | Agent stores contracted hours per day of week, replacing the single scalar; `AgentDayConfig` resolves effective hours per date from per-day values | New `AgentDayHours` sibling entity (mirrors `AgentDayOff`/`AgentException`); extracted static `resolveEffectiveHours` method threaded through **all 3** `getEffectiveHours` call sites (not just `computeAgentDayConfigs` — see Pitfall 1); precedence exactly per D-03/D-04 — see Architecture Patterns Pattern 1 & 2, Code Examples |
| MDL-03 | Existing agents migrate without data loss — scalar becomes per-day value for working days, `name` splits into first/last | Flyway `V29` migration sketch (fan-out INSERT + name-split UPDATE) in Code Examples; behaviour-equivalence test strategy in Validation Architecture; migration test-infra gap flagged in Pitfall 4 (no Testcontainers/Flyway execution in current test suite) |
</phase_requirements>

## Summary

This phase is a pure backend data-model change: no new libraries, no new external dependencies. All work happens inside the existing Spring Boot + JPA (Hibernate) + Flyway + Timefold 1.16.0 stack already in the repo. The three requirements (MDL-01/02/03) decompose into four concrete, independently-verifiable code changes: (1) a Flyway migration that adds `first_name`/`last_name` columns to `agent` and a new `agent_day_hours` child table, backfilling both from existing data; (2) an `Agent.java` change limited to two new scalar fields (firstName, lastName) — **no collection needs to be added to `Agent.java`**, because the sibling child tables (`AgentException`, `AgentDayOff`, `AgentPreference`) are never navigated from `Agent` — they're always loaded independently via their own repositories and joined in `SolverService` via `Map<UUID, ...>` lookups; the new `agent_day_hours` table follows that exact pattern; (3) a resolution change in `SolverService`, which is **not confined to `getEffectiveHours` + `computeAgentDayConfigs`** as the phase description implies — `getEffectiveHours` is also called from two additional sites inside `runPreSolveValidation` (increment-multiple check, break-window coverage check), both of which build their own independent lookup maps and must also learn about per-day hours or validation will silently diverge from what the solver actually resolves; (4) a `BambooRefreshService` change to split `displayName` at the same line that currently does `agent.setName(emp.displayName())`.

The single biggest risk not fully covered by the locked CONTEXT.md decisions is that **two other live write-paths** — `DeskAgentService.setContractedHours()` (an operator-facing API endpoint) and `DeskAssignmentUploadService` (the current, still-live 6-col/enriched upload, due for retirement only in Phase 10) — write directly to the scalar `contracted_hours_per_day` column and to `name` via `setName(...)`. Since D-03's resolution precedence **completely bypasses the scalar** post-migration, any hours change made through these paths after Phase 9 ships would silently stop affecting the solver until Phase 10/11 catches up, unless the planner explicitly decides to also make these write-paths fan out to `agent_day_hours`. This is flagged as an Open Question below — it's a real gap in the locked decision set, not a re-litigation of it.

A second material finding: the project's test profile (`application-test.yml`) runs `@DataJpaTest` against H2 with `ddl-auto: create-drop` and **`flyway.enabled: false`**. This means the actual Flyway migration SQL for this phase (the data-integrity-critical part — REQUIREMENTS.md calls MDL-02 "the highest-risk change" in the whole milestone) is never executed by the existing automated test suite. No Testcontainers-based Postgres+Flyway test harness exists anywhere in the repo. The planner needs to either introduce one or gate the migration behind a `checkpoint:human-verify` step against a real Postgres instance.

**Primary recommendation:** Keep `Agent.java` changes minimal (add `firstName`/`lastName` only; do not touch `contractedHoursPerDay` or add a JPA collection); add a new sibling child entity `AgentDayHours` mirroring `AgentDayOff`'s exact structure and repository style; extract the hours-resolution logic into a package-private static method (the same testability pattern already used for `SolverService.buildAgentDaysOffMap`, documented in 05-03-SUMMARY.md) so it can be pinned by a fast unit test without Spring context; thread the new per-day-hours map through all three `getEffectiveHours` call sites, not just `computeAgentDayConfigs`.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Agent name storage (first/last) | API / Backend (JPA entity + Flyway) | — | Pure persistence concern; no UI in this phase |
| Per-day contracted hours storage | API / Backend (JPA entity + Flyway) | — | New child table, same tier as `AgentException`/`AgentDayOff` |
| Effective-hours resolution (date → hours) | API / Backend (`SolverService`, pre-solve problem-fact assembly) | Database / Storage (source data) | Resolution is a pure-function computation over already-loaded rows; must stay outside Timefold constraints (score calculation), consistent with existing `AgentDayConfig` pre-computation pattern |
| BambooHR refresh mapping (displayName → first/last) | API / Backend (`BambooRefreshService`, scheduled/triggered sync) | External Service (BambooHR API — read-only source) | BambooHR is the data source; splitting happens entirely on our side |
| DTO/export exposure of new fields | API / Backend (`AgentResponse`, `DeskAgentResponse`, `DeskAgentExportService`) | — | No UI hint on this phase; consumers are API clients only |
| Data migration (scalar → per-day, name split) | Database / Storage (Flyway, forward-only) | API / Backend (entity mapping must match) | One-time backfill; must be idempotent-safe and reproduce current solve behaviour exactly (Success Criterion 4) |

## Standard Stack

No new libraries are introduced by this phase. Everything below is already a pinned dependency; versions confirmed directly from `build.gradle` (not training data).

### Core (already in project — confirmed current)
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Data JPA / Hibernate | (Spring Boot managed) | Entity mapping for new `AgentDayHours` + `Agent` field additions | Existing project ORM; all sibling child tables use it |
| Flyway (`flyway-core`, `flyway-database-postgresql`) | (Spring Boot managed) | Forward-only migration `V29__*.sql` | Existing project migration tool; sequential numbering already at V28 |
| PostgreSQL JDBC driver (`org.postgresql:postgresql`) | runtime | Target DB for the migration | Confirmed live prod target: RDS Postgres at `wfm-service-dev.ctuiw0u2644u.eu-west-2.rds.amazonaws.com` |
| Timefold Solver | **1.16.0** `[VERIFIED: build.gradle]` | Consumes `AgentDayConfig.effectiveHours` unchanged | **Correction to CONTEXT.md/memory framing:** the project's stated ceiling is "do not upgrade past 1.33.0" (per user memory `feedback_timefold_version.md`) — the *currently pinned* version in `build.gradle` is **1.16.0**, well under that ceiling. This phase makes no solver-version change either way; noted only so the planner doesn't assume 1.33.0 is the current version. |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| AssertJ + JUnit 5 | (existing test deps) | Unit-test the extracted static resolver | Already the project's test style (see `SolverServicePtoFilterTest`) |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| New child table `agent_day_hours` (D-09, locked) | 7 nullable columns on `agent` | Locked decision already rejects this — wider row, doesn't fit unbounded per-agent-day writes Phase 10/11 will make |
| `@Enumerated(EnumType.STRING) DayOfWeek` | `SMALLINT` 1–7 | STRING matches the existing `AgentPreference.dayOfWeek` convention exactly (verified in code) and makes migration SQL human-readable (`'MONDAY'` vs `1`); no reason to diverge |
| Java-side name recomposition (`firstName + " " + lastName`) as the stored `name` value | Keep `name` column set directly from the same source string (`displayName` / migrated value), independent of firstName/lastName | See Common Pitfall 3 — `name` is a real, indexed/searched JPA column (`AgentRepository.findFiltered` does `LOWER(a.name) LIKE ...`); recomposing it from parts on every write is unnecessary risk for zero benefit since both derive from the identical source string |

**No installation step required** — no new dependencies.

## Package Legitimacy Audit

**Not applicable.** This phase installs no new external packages. All work uses libraries already present and pinned in `build.gradle` (Spring Data JPA, Flyway, PostgreSQL driver, Timefold 1.16.0). No `npm view` / `pip index` / `cargo search` / slopcheck verification is needed.

## Architecture Patterns

### System Architecture Diagram

```
                     ┌─────────────────────────────┐
                     │   BambooRefreshService       │
                     │   (sync trigger)             │
                     │                               │
  BambooHR API  ───► │  emp.displayName()            │
  (displayName   │   │       │                        │
   only)         │   │       ▼                        │
                 │   │  [NEW] split(displayName)      │
                 │   │       │                        │
                 │   │       ├─► agent.firstName       │
                 │   │       ├─► agent.lastName        │
                 │   │       └─► agent.name (unchanged │
                 │   │           write, same value)    │
                     └───────────┬─────────────────────┘
                                 │ persists via AgentRepository
                                 ▼
                     ┌─────────────────────────────┐
                     │        Postgres              │
                     │  agent (+ first_name,         │
                     │          last_name)           │
                     │  agent_day_hours [NEW]         │◄── Flyway V29 backfill
                     │  agent_exception (unchanged)   │    (scalar → 7 rows,
                     │  agent_day_off (unchanged)      │     D-01/D-02/D-06)
                     └───────────┬─────────────────────┘
                                 │ read at solve time
                                 ▼
                     ┌─────────────────────────────────────────┐
                     │  SolverService.solve(...)                 │
                     │                                             │
                     │  load exceptions ──► agentExceptionMap      │
                     │  load day-offs   ──► agentDaysOffMap        │
                     │  [NEW] load agent_day_hours                  │
                     │        ──► agentDayHoursMap<agentId,          │
                     │             Map<DayOfWeek,BigDecimal>>        │
                     │                 │                              │
                     │                 ▼                              │
                     │  runPreSolveValidation(...)  ◄── [NEW] needs   │
                     │     - increment-multiple check    agentDayHoursMap │
                     │     - break-window check           too (2 call sites)│
                     │                 │                              │
                     │                 ▼                              │
                     │  computeAgentDayConfigs(...)                    │
                     │     for each agent, each date:                  │
                     │       if dayOff.contains(date) → skip           │
                     │       else effectiveHours =                     │
                     │         resolveEffectiveHours(                   │
                     │           exceptionMap, dayHoursMap,              │
                     │           date, schedule.defaultHours)             │
                     │       if effectiveHours <= 0 → skip (D-04/D-05)     │
                     │       else emit AgentDayConfig                      │
                     └───────────────────┬─────────────────────────────────┘
                                         ▼
                              Timefold constraints consume
                              AgentDayConfig.effectiveHours
                              (unchanged — record shape untouched)
```

### Recommended Project Structure
```
src/main/java/com/wfm/
├── model/
│   ├── Agent.java                 # + firstName, lastName fields only
│   └── AgentDayHours.java         # NEW — mirrors AgentDayOff.java structure
├── repository/
│   └── AgentDayHoursRepository.java  # NEW — mirrors AgentDayOffRepository style
├── service/
│   ├── SolverService.java         # resolution change (3 call sites, see Pitfall 1)
│   └── ...
├── integration/
│   └── BambooRefreshService.java  # split displayName at ~line 211
├── util/
│   └── AgentNameSplitter.java     # NEW — extract D-06 split rule as pure static fn,
│                                  #        reused by BambooRefreshService AND unit-tested
│                                  #        directly (Flyway SQL implements the same
│                                  #        rule independently — see Pitfall 5)
└── dto/
    ├── AgentResponse.java         # + firstName, lastName
    └── DeskAgentResponse.java     # + firstName, lastName
src/main/resources/db/migration/
└── V29__agent_first_last_name_and_day_hours.sql   # or split into V29/V30, see Metadata
```

### Pattern 1: Child-table problem fact, resolved via Map lookup (not JPA navigation)
**What:** `AgentException`, `AgentDayOff`, `AgentPreference` are never mapped as `@OneToMany` collections on `Agent` — they're always bulk-loaded independently via their own repository, then folded into a `Map<UUID, ...>` keyed by agent ID inside `SolverService`, before being consumed by `computeAgentDayConfigs`/`getEffectiveHours`/`runPreSolveValidation`. Verified: `Agent.java` (read in full) has zero `@OneToMany` fields.
**When to use:** For `AgentDayHours` — the new child table must follow this identical pattern. Do not add a collection field to `Agent.java`.
**Example (existing pattern for exceptions, to mirror for hours):**
```java
// Source: src/main/java/com/wfm/service/SolverService.java:171-175 (verified live)
Map<UUID, Map<LocalDate, BigDecimal>> agentExceptionMap = new HashMap<>();
for (AgentException ex : exceptions) {
    agentExceptionMap.computeIfAbsent(ex.getAgent().getId(), k -> new HashMap<>())
            .put(ex.getDate(), ex.getContractedHoursOverride());
}
```
The new code follows the same shape:
```java
Map<UUID, Map<DayOfWeek, BigDecimal>> agentDayHoursMap = new HashMap<>();
for (AgentDayHours h : agentDayHours) {
    agentDayHoursMap.computeIfAbsent(h.getAgent().getId(), k -> new HashMap<>())
            .put(h.getDayOfWeek(), h.getHours());
}
```

### Pattern 2: Extract private resolution logic to package-private static method for unit testing
**What:** `SolverServicePtoFilterTest.java` documents (in its own class Javadoc) that `SolverService.buildAgentDaysOffMap` was deliberately extracted from an inline for-loop into a package-private **static** method specifically so it could be unit-tested "without Spring context or reflection tricks."
**When to use:** Apply the identical extraction to the hours-resolution logic currently inline in the private `getEffectiveHours` method (`SolverService.java:801-810`). This is the direct mechanism for pinning Success Criterion 4 (behaviour-equivalence for uniform-hours agents) with a fast unit test.
**Example:**
```java
// Source: src/main/java/com/wfm/service/SolverService.java:801-810 (current, verified live)
private BigDecimal getEffectiveHours(Agent agent, LocalDate date,
                                     Map<LocalDate, BigDecimal> exceptionMap,
                                     Schedule schedule) {
    if (exceptionMap.containsKey(date)) {
        return exceptionMap.get(date);
    }
    return agent.getContractedHoursPerDay() != null
            ? agent.getContractedHoursPerDay()
            : schedule.getDefaultContractedHoursPerDay();
}
```
Recommended refactor shape (implements D-03/D-04 precedence exactly, agent scalar no longer consulted):
```java
// New — package-private static, testable like buildAgentDaysOffMap
static BigDecimal resolveEffectiveHours(Map<LocalDate, BigDecimal> exceptionMap,
                                         Map<DayOfWeek, BigDecimal> dayHoursMap,
                                         LocalDate date,
                                         BigDecimal scheduleDefaultHours) {
    if (exceptionMap.containsKey(date)) {
        return exceptionMap.get(date);              // highest precedence, D-03
    }
    DayOfWeek dow = date.getDayOfWeek();
    if (dayHoursMap.containsKey(dow)) {
        return dayHoursMap.get(dow);                  // per-day value, incl. 0.00 (D-04)
    }
    return scheduleDefaultHours;                       // absent row → schedule default (D-04)
}
```
The three existing call sites (`computeAgentDayConfigs`, and the two inside `runPreSolveValidation`) become thin wrappers that look up the per-agent map and delegate to this static method.

### Anti-Patterns to Avoid
- **Adding a `@OneToMany List<AgentDayHours>` to `Agent.java`:** Breaks from the established sibling pattern, adds unnecessary lazy-loading/cascade complexity, and isn't needed — `SolverService` already loads all three sibling child tables independently.
- **Recomposing the `name` DTO field from `firstName + " " + lastName` by mutating the stored `name` column:** `name` is queried directly in JPQL (`AgentRepository.findFiltered`) for the desk-agent search feature — don't repurpose it as a derived/non-persisted value; keep it a real, independently-set column populated by the same source string as firstName/lastName.
- **Using `BigDecimal.equals()` to test "hours == 0":** Existing code already uses `.compareTo(BigDecimal.ZERO) <= 0` (verified at `SolverService.java:488, 652, 734`) because `equals()` is scale-sensitive (`0` vs `0.00` are unequal via `equals()` but equal via `compareTo()`). The new per-day `0.00` check must follow the same `compareTo` convention.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Per-day-of-week storage keying | Custom bitmask or CSV string column | `@Enumerated(EnumType.STRING) DayOfWeek` (exact `AgentPreference` convention, verified) | Already proven in this codebase; type-safe; human-readable in SQL/migrations |
| BigDecimal scale/precision handling | Manual rounding logic | `BigDecimals.normalize()` (existing utility, `setScale(2, HALF_UP)`) | Already used by `DeskAgentService.setContractedHours`; keeps scale consistent with `precision=5, scale=2` convention |
| Name splitting | Ad-hoc `.split(" ")` repeated in 2+ places (migration SQL + BambooRefreshService + possibly DeskAssignmentUploadService) | A single Java utility (`AgentNameSplitter`) for all Java call sites, with a small representative-sample unit test proving the SQL migration's `split_part`/`substring` logic agrees with the Java rule | Two independent, hand-copied implementations of "split on first whitespace" (one in SQL, one in Java, possibly duplicated across `BambooRefreshService` AND `DeskAssignmentUploadService`) is a classic drift risk — D-06's rule must be defined once and referenced/tested everywhere it's needed in Java |

**Key insight:** The temptation in this phase is to over-engineer the JPA side (adding collections, cascades, orphanRemoval) when the codebase's own established convention for exactly this kind of per-agent-day child data is "load independently, resolve via Map" — copying that convention is both less code and lower risk than inventing a new one.

## Common Pitfalls

### Pitfall 1: `getEffectiveHours` has 3 call sites, not 1 — validation will silently diverge if only 2 are updated
**What goes wrong:** CONTEXT.md's canonical_refs describes the choke point as "`getEffectiveHours` ... and `computeAgentDayConfigs`". Verified against live code: `getEffectiveHours` is actually called from **three** places in `SolverService.java`: line 487 (inside `computeAgentDayConfigs`), and lines 651 and 733 (both inside `runPreSolveValidation`, in the "increment-multiple" check and the "break-window coverage" check respectively).
**Why it happens:** `runPreSolveValidation` builds its **own independent** local `agentExceptionMap` (lines 624-628) rather than reusing the one built later in the main `solve()` method (lines 171-175) — the two are structurally identical but separately constructed. If per-day hours are threaded into the main map but not into validation's local map, pre-solve validation will keep evaluating against exception-only resolution while the actual solve uses full per-day resolution — producing validation errors that don't match reality (or missing errors that should fire).
**How to avoid:** Load `List<AgentDayHours>` once, early (alongside where `exceptions` is loaded at line 142), pass the **raw list** into `runPreSolveValidation` (which already receives raw `daysOff`/`exceptions`/`preferences` lists and builds its own maps — same pattern), and separately build the map again after validation for `computeAgentDayConfigs`'s use (mirrors exactly how `exceptions` already works today).
**Warning signs:** A test that solves successfully but a validation error test (or vice versa) doesn't reflect the same effective hours the solver actually used.

### Pitfall 2: Two live write-paths bypass the new resolution and silently stop working post-migration
**What goes wrong:** `DeskAgentService.setContractedHours(deskId, agentId, hours)` (an existing, live, tested API endpoint at `DeskAgentService.java:180-193`) and `DeskAssignmentUploadService` (current upload flow, `setContractedHoursPerDay(null)` at line 360, plus `setName(...)` at lines 276/301) both write **only** to the scalar `Agent.contractedHoursPerDay` column / `name` field. Per locked decision D-03, post-Phase-9 resolution **never reads the scalar at all** — only `AgentException` → per-day row → schedule default. So any operator who calls `setContractedHours` after this phase ships will have their change silently ignored by the solver (their edit updates a column nothing reads anymore), which is a real solve-behaviour-affecting gap beyond the single migration instant that Success Criterion 4 explicitly covers.
**Why it happens:** CONTEXT.md's locked scope list only names `BambooRefreshService:211` for the name split and doesn't mention `DeskAgentService`/`DeskAssignmentUploadService` at all — they weren't in scope for the *decisions*, but the *resolution change* (D-03) has a side effect on them that wasn't explicitly reasoned through.
**How to avoid:** This is a genuine **Open Question** for the planner (see below), not a locked decision to silently work around. Two honest options: (a) leave it as a documented, deliberately-deferred gap — `setContractedHours` becomes a no-op for solve purposes until Phase 10/11, and the plan explicitly notes this in its scope/out-of-scope section; or (b) make `setContractedHours` also fan out the same value to all 7 `agent_day_hours` rows (mirrors D-01's migration rule exactly, ~10 lines of code), keeping the invariant durable rather than true only at the moment of migration. Recommend (b) given it's low-cost and directly serves the "no solve-behaviour regression" goal the phase is named for.
**Warning signs:** An acceptance/smoke test that calls `setContractedHours` then solves and expects the new hours to apply — if such a test exists or gets written, it will fail unless (b) is chosen.

### Pitfall 3: `name` is a real, queried JPA column — don't quietly change its semantics
**What goes wrong:** `AgentRepository.findFiltered`/`findFilteredAfterCursor` use `LOWER(a.name) LIKE LOWER(CONCAT('%', :search, '%'))` in JPQL (verified) to power the desk-agent search box. If `name` were changed to a derived/computed (non-column) property, this query breaks at startup (JPQL can't reference non-mapped properties) or requires rewriting to `CONCAT(a.firstName, ' ', a.lastName)`.
**Why it happens:** D-08's phrasing ("keep a derived combined name ... in responses/exports/logs") is about the **DTO output**, not necessarily the underlying entity column — it's easy to over-read this as "derive the entity's name field," which would break search.
**How to avoid:** Keep `Agent.name` as a real, independently-set stored column (written from the exact same source string as `firstName`/`lastName` at every write site — migration and refresh both), so it stays trivially consistent with `firstName + " " + lastName` in virtually all cases without needing to be computed from parts. Only the DTO layer (if D-08 is read literally) needs to expose a `name` value equal to `firstName + " " + lastName`; since both are sourced from the same string, this is functionally identical to just passing through the stored value.
**Warning signs:** Desk-agent search returning zero results after this phase ships, or a Hibernate startup error about an unmapped property in JPQL.

### Pitfall 4: Test suite never runs the actual Flyway migration SQL
**What goes wrong:** `src/test/resources/application-test.yml` sets `flyway.enabled: false` and `jpa.hibernate.ddl-auto: create-drop` against H2 — schema for `@DataJpaTest` tests is generated from Hibernate entity mappings, never from the Flyway scripts. There is no Testcontainers dependency anywhere in the repo (`grep` for `testcontainers` returns nothing). This means the migration SQL itself — the exact scalar-to-7-rows fan-out and the name-split logic, which REQUIREMENTS.md calls "the highest-risk change" in the whole v1.2 milestone — is **not exercised by any automated test**, only by manually running it against a real Postgres instance (dev/staging/prod).
**Why it happens:** The project has never needed a data-migration-heavy Flyway script tested end-to-end before (previous migrations — V15, V22, V25-28 — are simpler additive DDL or straightforward 1:1 column copies).
**How to avoid:** Either (a) add a minimal Testcontainers Postgres module scoped just to this migration (new infra for the project — a meaningful lift), or (b) treat migration verification as a `checkpoint:human-verify` step: run the migration against a snapshot/copy of the real RDS data (or a seeded local Postgres via `docker run postgres` + `flyway migrate`), and manually assert row counts (`agent_day_hours` count == `7 × count(agents with non-null scalar)`), spot-check a handful of NULL-scalar agents have zero rows, and spot-check a sample of multi-word names split correctly. Given no existing Testcontainers precedent, (b) is the lower-friction path for a foundation phase; flag (a) as a "nice to have" if the planner wants durable regression coverage for future migrations too.
**Warning signs:** A plan that claims "migration verified" backed only by `@DataJpaTest` tests — those tests never touched the actual `V29__*.sql` file.

### Pitfall 5: SQL split rule and Java split rule can silently drift
**What goes wrong:** D-07 requires the identical "first whitespace token" rule to be applied in **two independently-implemented places**: the one-time Flyway SQL migration (must be pure SQL — `split_part`/`position`/`substring`, no stored procedures needed since this is a single-tenant simple transform) and the Java `BambooRefreshService` code path (and, per Pitfall 2/Don't-Hand-Roll, potentially `DeskAssignmentUploadService` too). These two implementations can diverge on edge cases (leading/trailing whitespace, multiple consecutive spaces, tabs) without either side failing loudly.
**Why it happens:** SQL and Java don't share code; the same logical rule has to be hand-translated into each language.
**How to avoid:** Write the Java-side splitter as a single, well-tested utility (`AgentNameSplitter`), and write a **data-driven** unit test with representative inputs (single-token name, "First Last", "Mary Jane Watson" — the exact example from D-06, leading/trailing whitespace, double-internal-space) proving the Java behaviour matches D-06's stated examples exactly. For the SQL side, since it runs once against real data, recommend the migration verification step in Pitfall 4 include running the *same* representative-input table through both the SQL expression (`SELECT split_part(...), ...`) and the Java utility, confirming outputs match, before trusting the migration against production data.
**Warning signs:** A production agent's `first_name`/`last_name` looking different from what the same displayName would produce via a subsequent BambooHR refresh (i.e., migration-time split and refresh-time split disagree for the same input).

### Pitfall 6: `AgentException.contractedHoursOverride` is `nullable = false` — precedence check must stay a map lookup, not a null check
**What goes wrong:** Someone "simplifying" the resolver might try `if (agent.getException(date) != null)` style logic; the actual code correctly uses `exceptionMap.containsKey(date)` because the map itself may simply not have an entry for a given date (not because a null override exists — the column is NOT NULL, so a present row always has a real value, including potentially `0.00`, which is already handled correctly by the existing `<= 0` skip check downstream).
**How to avoid:** Keep the `containsKey` pattern in the new static resolver exactly as shown in Pattern 2 above — do not change to a null-check based branch.

## Code Examples

### Extracted static resolver (recommended shape — see Pattern 2 for full context)
```java
// Source: pattern verified from src/main/java/com/wfm/service/SolverService.java:801-810
//         and src/test/java/com/wfm/service/SolverServicePtoFilterTest.java (testability convention)
static BigDecimal resolveEffectiveHours(Map<LocalDate, BigDecimal> exceptionMap,
                                         Map<DayOfWeek, BigDecimal> dayHoursMap,
                                         LocalDate date,
                                         BigDecimal scheduleDefaultHours) {
    if (exceptionMap.containsKey(date)) {
        return exceptionMap.get(date);
    }
    if (dayHoursMap.containsKey(date.getDayOfWeek())) {
        return dayHoursMap.get(date.getDayOfWeek());
    }
    return scheduleDefaultHours;
}
```

### New sibling entity (mirrors `AgentDayOff.java`, verified structure)
```java
// Source: structural template from src/main/java/com/wfm/model/AgentDayOff.java (read in full)
@Entity
@Table(name = "agent_day_hours", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"agent_id", "day_of_week"})
})
public class AgentDayHours {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private long tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id", nullable = false)
    private Agent agent;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 9)
    private DayOfWeek dayOfWeek;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal hours;

    // constructor, getters/setters — mirror AgentDayOff exactly
}
```

### New repository (mirrors `AgentDayOffRepository`'s desk-scoped bulk-fetch query)
```java
// Source: pattern from src/main/java/com/wfm/repository/AgentDayOffRepository.java:44-48 (verified live)
@Query("SELECT h FROM AgentDayHours h WHERE h.tenantId = :tenantId " +
       "AND h.agent.deskId = :deskId")
List<AgentDayHours> findByTenantIdAndDeskId(long tenantId, UUID deskId);
```

### Flyway migration sketch (SQL split + fan-out — for planner's task design, not final SQL)
```sql
-- Source: style/pattern from src/main/resources/db/migration/V15__merge_desk_agent_into_agent.sql
--         and V28__add_agent_working_days_known.sql (verified live)

-- 1. New columns on agent
ALTER TABLE agent
    ADD COLUMN first_name VARCHAR(255),
    ADD COLUMN last_name VARCHAR(255);

-- 2. Backfill name split (D-06: first whitespace token → first_name, remainder → last_name)
UPDATE agent
SET first_name = split_part(name, ' ', 1),
    last_name = CASE
        WHEN position(' ' IN name) > 0
        THEN trim(substring(name FROM position(' ' IN name) + 1))
        ELSE ''
    END;

-- 3. New child table (D-09)
CREATE TABLE agent_day_hours (
    id          UUID PRIMARY KEY,
    tenant_id   BIGINT NOT NULL,
    agent_id    UUID NOT NULL REFERENCES agent(id) ON DELETE CASCADE,
    day_of_week VARCHAR(9) NOT NULL,
    hours       NUMERIC(5,2) NOT NULL,
    UNIQUE (agent_id, day_of_week)
);

-- 4. Fan out existing non-null scalar to all 7 weekday rows (D-01)
INSERT INTO agent_day_hours (id, tenant_id, agent_id, day_of_week, hours)
SELECT gen_random_uuid(), a.tenant_id, a.id, dow.name, a.contracted_hours_per_day
FROM agent a
CROSS JOIN (VALUES ('MONDAY'), ('TUESDAY'), ('WEDNESDAY'), ('THURSDAY'),
                    ('FRIDAY'), ('SATURDAY'), ('SUNDAY')) AS dow(name)
WHERE a.contracted_hours_per_day IS NOT NULL;
-- NULL-scalar agents (D-02) intentionally get zero rows — no INSERT for them.
```
Note: `gen_random_uuid()` requires the `pgcrypto` extension, or use `uuid_generate_v4()` if `uuid-ossp` is already enabled — verify which extension is already enabled in this DB before finalizing (V24 enabled `pgvector`, not confirmed for uuid generation; the planner should check existing UUID-generation convention across prior migrations, since some may rely on application-generated UUIDs rather than DB-side generation).

## State of the Art

Not applicable in the traditional sense (no external framework/library upgrade in this phase). The only "old → new" is internal: the resolution rule's authority moves from a single scalar to a per-day child table.

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|---------------|--------|
| `Agent.contractedHoursPerDay` scalar is the sole hours source (`getEffectiveHours` fallback) | `agent_day_hours` per-weekday rows are the source; scalar remains in schema but is no longer read by `getEffectiveHours` | This phase (Phase 9) | Two write-paths (`DeskAgentService.setContractedHours`, `DeskAssignmentUploadService`) still only touch the scalar — see Pitfall 2 |
| Single `name` field | `firstName`/`lastName` added, `name` retained unchanged as a real column | This phase | No breaking change to existing search/DTOs (D-08) |

**Deprecated/outdated:** None yet — the scalar `contracted_hours_per_day` column is *not* dropped in this phase (dropping it, and retiring the redundant dual-mechanism day-off system, is explicitly Phase 10/11 work per D-05 and the Deferred Ideas section).

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `gen_random_uuid()`/`uuid-ossp` extension availability is unconfirmed for this DB — the code example above assumes one is available for DB-side UUID generation in the migration's INSERT | Code Examples (Flyway migration sketch) | If neither extension is enabled, the INSERT needs UUIDs generated a different way (e.g., a temp sequence, or generating a batch in application code as a one-off script instead of pure SQL) — low risk, easy to check by reading V24 or querying `pg_extension` on the live DB before finalizing the migration |
| A2 | Recommending `varchar(255)` for `first_name`/`last_name` with no explicit length constraint given in decisions | Code Examples | Low risk — matches the unconstrained `name` column's own type (no length specified in `Agent.java`, i.e., Hibernate defaults apply); should confirm actual current column type via `\d agent` on live DB if the planner wants an exact match |

**Everything else in this research is `[VERIFIED]`** — confirmed by direct reads of the live repository files listed in canonical_refs, not training-data recall. No package/library claims were made that needed slopcheck-style verification (no new packages).

## Open Questions

1. **Should `DeskAgentService.setContractedHours` and the still-live upload flow (`DeskAssignmentUploadService`) also fan out to `agent_day_hours` on write, to keep the "no solve-behaviour regression" property durable rather than true only at the migration instant?**
   - What we know: D-03's resolution precedence bypasses the scalar entirely once per-day rows exist for an agent; these two write-paths only touch the scalar.
   - What's unclear: CONTEXT.md's locked decisions don't address this — it wasn't in scope for the discuss-phase conversation.
   - Recommendation: Make `setContractedHours` fan out to all 7 `agent_day_hours` rows using the same value (mirrors D-01's own migration rule, ~10 lines), and treat `DeskAssignmentUploadService`'s hours-clearing (`setContractedHoursPerDay(null)`) as needing a corresponding `agent_day_hours` row deletion for that agent, so re-assignment doesn't inherit stale per-day rows from a prior desk assignment. If the planner decides this is explicitly out of scope for Phase 9 (a legitimate call, given the upload flow retires in Phase 10 anyway), the plan should say so explicitly rather than leave it as an unnoticed gap.

2. **Should `DeskAssignmentUploadService`'s two `setName(...)` call sites (lines 276, 301) also be updated to split into first/last, or is `BambooRefreshService:211` the only mandated site (per D-07's literal text)?**
   - What we know: D-07 names `BambooRefreshService:211` explicitly. `DeskAssignmentUploadService` independently sets `name` from BambooHR's cached displayName and from a raw spreadsheet name column, with no mention in CONTEXT.md.
   - What's unclear: Whether leaving these two sites unsplit means agents created via that upload path have stale/empty `firstName`/`lastName` until their next BambooHR refresh.
   - Recommendation: Update both sites using the same `AgentNameSplitter` utility recommended in Don't Hand-Roll — it's the same one-line change repeated, low cost, and avoids a visible data gap for freshly-uploaded agents. If descoped, document explicitly.

3. **Should Phase 9's DTO changes stop at name (firstName/lastName), or also surface `agent_day_hours` read-only in `DeskAgentResponse`?**
   - What we know: CONTEXT.md's canonical_refs list only names `AgentResponse`/`DeskAgentResponse`/`DeskAgentExportService` as "consumers of `name` needing firstName/lastName + derived name" — hours exposure isn't mentioned, and the phase has no UI.
   - What's unclear: Whether any Phase 10/11 planning assumption expects the per-day hours to already be readable via this API.
   - Recommendation: Keep Phase 9's DTO scope to name fields only, consistent with the explicit file-level guidance — exposing per-day hours read-only is cheap to add later once Phase 10/11 actually need it, and adding it now without a consumer risks locking in an API shape prematurely.

## Environment Availability

Skipped — this phase has no new external tool/service dependencies. Postgres, Flyway, and the JVM toolchain are already verified live and in continuous use (RDS instance confirmed reachable per project memory; `build.gradle` confirms Flyway/Postgres driver already present).

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 + AssertJ (existing project convention) |
| Config file | `src/test/resources/application-test.yml` (H2, `ddl-auto: create-drop`, **`flyway.enabled: false`**) |
| Quick run command | `./gradlew test --tests "com.wfm.service.SolverService*Test"` |
| Full suite command | `./gradlew test` |

**Critical caveat (see Pitfall 4):** the config above means Flyway SQL (the actual migration script this phase writes) is **never executed** by `./gradlew test`. Any "migration data-integrity" validation must happen outside this test framework — see Wave 0 Gaps.

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| MDL-01 | `firstName`/`lastName` split from a display name per D-06's rule | unit | `./gradlew test --tests "com.wfm.util.AgentNameSplitterTest"` | ❌ Wave 0 |
| MDL-01 | `firstName`/`lastName` persist and reload via JPA | unit (`@DataJpaTest`) | `./gradlew test --tests "com.wfm.model.AgentNamePersistenceTest"` | ❌ Wave 0 (mirrors existing `AgentEmploymentTypePersistenceTest`) |
| MDL-02 | Resolution precedence: exception > per-day > schedule default | unit | `./gradlew test --tests "com.wfm.service.SolverServiceEffectiveHoursResolutionTest"` | ❌ Wave 0 |
| MDL-02 | Per-day `0.00` row → agent not scheduled that weekday (union with day-off, D-05) | unit | same class as above | ❌ Wave 0 |
| MDL-02 | `AgentDayHours` persists and reloads via JPA (day_of_week as STRING enum) | unit (`@DataJpaTest`) | `./gradlew test --tests "com.wfm.model.AgentDayHoursPersistenceTest"` | ❌ Wave 0 |
| MDL-03 | Behaviour-equivalence: uniform-hours agent's `effectiveHours` identical pre/post migration for every date in a sample period | unit (pure resolver, no Spring) | same class as MDL-02's resolver test — construct a 7-row-uniform dayHoursMap and assert it equals the old scalar-only behaviour for every `DayOfWeek` | ❌ Wave 0 |
| MDL-03 | Flyway migration produces correct row counts/values against real data shape | manual / `checkpoint:human-verify` | run `flyway migrate` against a Postgres instance seeded with representative agent rows (non-null scalar, null scalar, single-token name, multi-token name), assert via `psql` queries | N/A — no automated harness exists (Pitfall 4) |

### Sampling Rate
- **Per task commit:** `./gradlew test --tests "com.wfm.service.SolverService*Test"` and `--tests "com.wfm.model.Agent*Test"` and `--tests "com.wfm.util.AgentNameSplitterTest"`
- **Per wave merge:** `./gradlew test` (full suite)
- **Phase gate:** Full suite green, **plus** a manual `checkpoint:human-verify` migration dry-run against a real/staging Postgres before `/gsd-verify-work` — full suite alone does not exercise the Flyway SQL.

### Wave 0 Gaps
- [ ] `AgentNameSplitterTest` — data-driven test covering D-06's rule (single token, "First Last", "Mary Jane Watson", leading/trailing/double whitespace) — covers MDL-01
- [ ] `AgentNamePersistenceTest` — `@DataJpaTest` mirroring `AgentEmploymentTypePersistenceTest` — covers MDL-01
- [ ] `SolverServiceEffectiveHoursResolutionTest` — extracted static resolver, mirroring `SolverServicePtoFilterTest`'s structure — covers MDL-02 and MDL-03's behaviour-equivalence bar
- [ ] `AgentDayHoursPersistenceTest` — `@DataJpaTest` for the new entity (STRING enum round-trip, precision/scale) — covers MDL-02
- [ ] Migration dry-run harness/checklist (manual, or new Testcontainers module if the planner chooses to invest in one) — covers MDL-03's data-integrity bar; no existing infra to reuse

*(No framework install needed — JUnit5/AssertJ/`@DataJpaTest` are already fully wired.)*

## Security Domain

`security_enforcement` is absent from `.planning/config.json` — treated as enabled per instructions.

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-------------------|
| V2 Authentication | No | Phase touches no auth flows |
| V3 Session Management | No | N/A |
| V4 Access Control | No | Existing tenant-scoping (`tenantId` on every entity/repository query) is unchanged and must be preserved on the new `AgentDayHours` entity/repository (verified: every sibling child entity carries `tenant_id` and every repository method is tenant-scoped) |
| V5 Input Validation | Yes | `BigDecimals.normalize()` for hours scale/precision (existing utility); DB-level `NUMERIC(5,2)` + `NOT NULL` on `agent_day_hours.hours` as a hard backstop, consistent with `agent_exception.contracted_hours_override`'s existing constraint style |
| V6 Cryptography | No | N/A — no secrets/crypto touched by this phase |

### Known Threat Patterns for this stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|----------------------|
| Missing `tenant_id` scoping on new entity/repository (cross-tenant data leak) | Tampering / Information Disclosure | Copy the exact `tenant_id` column + every repository method scoped by `tenantId` pattern already used by `AgentDayOff`/`AgentException`/`AgentPreference` — verified as a hard project convention, no exceptions found |
| SQL injection via Flyway migration | Tampering | N/A here — migration SQL in this phase has no user-supplied input; it's a pure static backfill over existing columns |

## Sources

### Primary (HIGH confidence — direct code reads, this session)
- `src/main/java/com/wfm/model/Agent.java` — current fields, confirmed no `@OneToMany` collections
- `src/main/java/com/wfm/model/AgentDayConfig.java` — record shape, confirmed unchanged by this phase
- `src/main/java/com/wfm/model/AgentException.java`, `AgentDayOff.java`, `AgentPreference.java` — sibling child-table convention (template for `AgentDayHours`)
- `src/main/java/com/wfm/service/SolverService.java` (multiple sections: lines 100-320, 469-810, 949+) — all 3 `getEffectiveHours` call sites, `computeAgentDayConfigs`, map-building conventions
- `src/main/java/com/wfm/repository/AgentExceptionRepository.java`, `AgentDayOffRepository.java`, `AgentPreferenceRepository.java` — repository query conventions
- `src/main/java/com/wfm/integration/BambooRefreshService.java` (lines 190-320) — name/hours defaulting logic, MANDATORY day-off generation
- `src/main/java/com/wfm/integration/BambooEmployee.java` — confirms `displayName` is the only name field BambooHR provides
- `src/main/java/com/wfm/service/DeskAgentService.java`, `DeskAssignmentUploadService.java` (grep-verified sections), `DeskService.java` — the two additional scalar-hours/name write-paths (Pitfall 2, Open Questions 1-2)
- `src/main/java/com/wfm/dto/AgentResponse.java`, `DeskAgentResponse.java`, `src/main/java/com/wfm/service/DeskAgentExportService.java` — DTO/export shape
- `src/main/resources/db/migration/` directory listing + `V15__merge_desk_agent_into_agent.sql`, `V22__add_day_off_status.sql`, `V28__add_agent_working_days_known.sql` — migration naming/style conventions
- `src/test/resources/application-test.yml` — confirmed `flyway.enabled: false`, H2 + `ddl-auto: create-drop` (Pitfall 4)
- `src/test/java/com/wfm/service/SolverServicePtoFilterTest.java` — confirmed static-extraction testability convention (explicitly documented in the file's own Javadoc)
- `src/test/java/com/wfm/model/AgentEmploymentTypePersistenceTest.java` — `@DataJpaTest` persistence test template
- `build.gradle` — confirmed Timefold 1.16.0 (not 1.33.0), Flyway/Postgres deps, no Testcontainers dependency
- `.planning/config.json` — confirmed `nyquist_validation: true`, `security_enforcement` absent, `brave_search`/`firecrawl`/`exa_search` all false

### Secondary (MEDIUM confidence)
- User memory `feedback_timefold_version.md` — states project ceiling is "do not upgrade past 1.33.0"; cross-checked against `build.gradle`'s actual pinned 1.16.0, both are consistent (1.16.0 < 1.33.0 ceiling), no contradiction

### Tertiary (LOW confidence)
- None — no unverified WebSearch claims were needed for this phase (pure internal codebase research, no external library research required)

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — no new libraries; all versions read directly from `build.gradle`
- Architecture: HIGH — every claim verified against live file reads in this session, including the non-obvious "3 call sites" and "no collection needed on Agent.java" findings
- Pitfalls: HIGH for the code-verified ones (1, 2, 3, 6); MEDIUM for the process/testing gap (4) since it's a judgment call about how to close it, not a code fact; HIGH for pitfall 5 (verified D-06/D-07 require duplicate SQL+Java logic)

**Research date:** 2026-07-30
**Valid until:** 30 days (internal codebase research on a stable, non-fast-moving stack; re-verify if `SolverService.java` or the migration directory changes before planning begins)
