# Phase 9: Agent Data Model Foundation - Context

**Gathered:** 2026-07-30
**Status:** Ready for planning

<domain>
## Phase Boundary

Agent stores **first/last name separately** and **per-day-of-week contracted hours** (replacing the single `contractedHoursPerDay` scalar), migrated with no data loss and **no solve-behaviour regression** for agents whose hours are uniform across worked days.

`AgentDayConfig.effectiveHours` resolution must compose with existing `AgentException` per-date overrides exactly as before for the uniform case.

**In scope:** Data model changes (Agent name split, per-day hours storage), the resolution rule in `SolverService.getEffectiveHours`, the Flyway migration of existing agents, DTO/export exposure of the new fields, and keeping the BambooHR refresh mapping working.

**Out of scope (later phases):** Spreadsheet parsing of the new columns (Phase 10), BambooHR↔spreadsheet merge/precedence and making Mon–Sun hours *the* authority that retires the field-4517 MANDATORY-day-off mechanism (Phase 11). Any UI (Phase 9 has no UI).

</domain>

<decisions>
## Implementation Decisions

### Migration — scalar → per-day hours (MDL-03, highest risk)
- **D-01:** Migrate each existing agent's scalar `contractedHoursPerDay` to **all 7 day-of-week slots** (Mon–Sun). This exactly reproduces today's `getEffectiveHours` behaviour — the scalar already applies to any date not excluded by a day-off row — so there is provably no solve regression (Success Criterion 4). Days the agent doesn't work stay excluded via their existing `MANDATORY AgentDayOff` rows.
- **D-02:** For an existing agent whose scalar is **NULL** today (currently resolves via the `Schedule.defaultContractedHoursPerDay` = 8.00 fallback): **leave all 7 per-day slots empty**. Do not backfill 8.00 — that would hard-code today's schedule default onto the agent and diverge if a schedule later uses a different default. They keep resolving via the schedule-default fallback (see D-04).

### Per-day resolution semantics (MDL-02)
- **D-03:** Resolution precedence for a given agent+date is: **`AgentException` date-override → per-day value for that weekday → `Schedule.defaultContractedHoursPerDay`.** `AgentException`, dated PTO, and MANDATORY day-offs are untouched and compose exactly as they do today.
- **D-04:** A weekday with **no per-day record** → fall back to the schedule default (preserves today's null-scalar behaviour). A per-day record that **exists and is `0`** → agent does **not work** that weekday (no `AgentDayConfig` emitted). Storage MUST therefore represent "absent" distinctly from `0` (satisfied by D-08). This satisfies both the null-scalar migration and Phase 10's "0/blank on a contracted-hours column = day not worked" rule.
- **D-05:** In Phase 9 both "recurring not-worked" mechanisms coexist deliberately: a weekday can be excluded via a `MANDATORY AgentDayOff` (from BambooHR field 4517) **and/or** a per-day `0`. "Not worked" is the union. This redundancy is intentional and acceptable for a foundation phase — consolidating so Mon–Sun hours become *the* authority is Phase 10/11 merge work, not Phase 9.

### Name split (MDL-01/03)
- **D-06:** Split heuristic is **first whitespace token → firstName, remainder → lastName** (`"Mary Jane Watson"` → first=`"Mary"`, last=`"Jane Watson"`). Single-token name → firstName set, lastName empty. Best fit for BambooHR-sourced "First Last" display names.
- **D-07:** The same split rule is applied in **two places**: (1) the one-time Flyway migration of existing `name` values, and (2) the ongoing BambooHR refresh, which supplies only a combined `displayName` — `BambooRefreshService:211` currently does `agent.setName(emp.displayName())` and must instead split into first/last so the refresh keeps populating both columns. (Phase 10's spreadsheet will later supply first/last explicitly.)
- **D-08 (API/DTO compat):** Add `firstName` / `lastName` as new fields on the DTOs/exports, **and keep a derived combined `name`** (`firstName + " " + lastName`) in responses/exports/logs. No breaking change to the API or export format — the frontend adopts the new fields when ready. Aligns with the phase's no-regression goal.

### Storage shape (MDL-02)
- **D-09:** Store per-day hours in a **new child table `agent_day_hours`** (`agent_id`, `day_of_week`, `hours`) with `@ManyToOne` back to `Agent`, consistent with the existing `AgentException` / `AgentDayOff` / `AgentPreference` conventions. **Absent = no row** for that weekday; **not-worked = a row with `0.00`.** This is the locked contract that Phase 10's parser and Phase 11's merge engine both write into. (Chosen over 7 nullable columns on the agent table, which would widen the row and fit the unbounded per-agent-day writes less naturally.)

### Claude's Discretion
- Exact `day_of_week` representation in `agent_day_hours` (e.g. `java.time.DayOfWeek` enum vs smallint 1–7), FK/unique-constraint layout, and precision/scale on `hours` — planner/researcher decide, mirroring the existing `contracted_hours_per_day` column (`precision = 5, scale = 2`).
- Flyway version number and whether the migration is one script or split — planner decides.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Requirements & roadmap
- `.planning/REQUIREMENTS.md` — MDL-01, MDL-02, MDL-03 (and the "MDL-02 is the highest-risk change" constraint note)
- `.planning/ROADMAP.md` §"Phase 9: Agent Data Model Foundation" — goal + 4 success criteria

### Code the phase modifies (verify current state before editing)
- `src/main/java/com/wfm/model/Agent.java` — `name` (line 29), `contractedHoursPerDay` scalar (line 68) — the fields being changed
- `src/main/java/com/wfm/model/AgentDayConfig.java` — the `effectiveHours` problem-fact record fed to the solver
- `src/main/java/com/wfm/service/SolverService.java` — `getEffectiveHours` (line 801) resolution chain; `computeAgentDayConfigs` (line 469) which skips day-off dates and emits `AgentDayConfig`
- `src/main/java/com/wfm/model/AgentException.java` — per-date `contracted_hours_override`; must keep highest precedence
- `src/main/java/com/wfm/integration/BambooRefreshService.java` §~211 (`setName(displayName)`), §255–303 (MANDATORY `AgentDayOff` generation from field 4517)
- `src/main/java/com/wfm/integration/BambooEmployee.java` — source provides only `displayName` (no separate first/last)
- `src/main/java/com/wfm/dto/AgentResponse.java`, `src/main/java/com/wfm/dto/DeskAgentResponse.java`, `src/main/java/com/wfm/service/DeskAgentExportService.java` — consumers of `name` needing `firstName`/`lastName` + derived `name`
- `src/main/resources/db/migration/` — latest is `V28__add_agent_working_days_known.sql`; new migration follows this sequence

No external ADRs/specs — requirements fully captured in the decisions above.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **`AgentException` / `AgentDayOff` / `AgentPreference`** child-table pattern (`@ManyToOne` to Agent, per-day/per-date rows) — the direct model for the new `agent_day_hours` table.
- **`getEffectiveHours(agent, date, exceptionMap, schedule)`** (SolverService:801) — the single choke point for the resolution change; only this method and `computeAgentDayConfigs` need to learn about per-day hours.
- **`WorkingDaysParser`** — already parses BambooHR field 4517 into a `Set<DayOfWeek>`; not directly reused here, but establishes the DayOfWeek convention.

### Established Patterns
- Flyway forward-only migrations under `db/migration/` (sequential `V##__*.sql`); latest `V28`.
- BigDecimal hours with `precision = 5, scale = 2` (existing `contracted_hours_per_day`).
- Solver consumes pre-computed problem facts (`AgentDayConfig`) built in `SolverService` before solve — per-day hours resolve there, not inside constraints.

### Integration Points
- `SolverService.getEffectiveHours` / `computeAgentDayConfigs` — resolution.
- `BambooRefreshService:211` — refresh must split `displayName` into first/last (D-07).
- DTO/export layer — expose first/last + derived `name` (D-08).
- Flyway migration — data migration of existing `name` and scalar hours (D-01, D-02, D-06).

</code_context>

<specifics>
## Specific Ideas

- Behaviour-equivalence is the acceptance bar: for a uniform-hours agent, `AgentDayConfig.effectiveHours` for every date must equal the pre-migration value. This is the thing tests should pin (regression guard for Criterion 4).
- Timefold pinned at 1.33.0 (project-wide constraint) — no solver-version changes.

</specifics>

<deferred>
## Deferred Ideas

- **Consolidating the two "recurring not-worked" mechanisms** (per-day `0` vs MANDATORY `AgentDayOff` from field 4517) so Mon–Sun hours become the single authority — Phase 11 merge work (MRG), per STATE decision "Mon–Sun contracted hours are the authority on which days are worked."
- **Spreadsheet population** of per-day hours, first/last name, and recurring day-off/PTO columns — Phase 10 (UPL).
- **BambooHR↔spreadsheet per-field precedence and merge report** — Phase 11 (MRG).

None of these belong in Phase 9 — this phase only builds the model + migration + resolution the later phases write into.

</deferred>

---

*Phase: 9-agent-data-model-foundation*
*Context gathered: 2026-07-30*
