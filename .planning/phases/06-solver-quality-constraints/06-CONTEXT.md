# Phase 6: Solver Quality Constraints - Context

**Gathered:** 2026-06-02
**Status:** Ready for planning

<domain>
## Phase Boundary

**This phase is re-scoped to "PTO & Weekends" — the day-off data foundation only.**

Deliver correct, BambooHR-sourced mandatory day-off (weekend) data into the solver,
plus confirmed PTO blocking behaviour. This is QUAL-01's foundation: every scheduled
agent's fixed weekly days off are honoured as hard blocks.

**In scope:**
- Import each agent's weekly working pattern from BambooHR ("Working days" field 4517)
  during the refresh, and generate recurring `DayOffType.MANDATORY` `AgentDayOff` rows.
- Honour non-standard patterns literally; flag outliers and missing data to the operator.
- Confirm/keep PTO blocking: APPROVED blocks, REQUESTED visible-only (already shipped 05-03).
- Retire the dead `"MANDATORY".equalsIgnoreCase(type)` time-off match.

**Out of scope (deferred — see Deferred Ideas):**
- QUAL-02 weekend-position fairness distribution.
- QUAL-03 day-to-day hours consistency.
These belong to a follow-on phase (Phase 6b / 7). The roadmap should be split when planning.
</domain>

<decisions>
## Implementation Decisions

### Weekend / mandatory day-off source
- **D-01:** Mandatory days off (weekends) come from BambooHR custom field **"Working days"
  = field id `4517`, report alias `customWorkingdays`** (Personal → Schedule). Confirmed live
  on the helpware tenant. NOT the empty `Monday`…`Sunday` fields (ids 5553-5563 — empty for
  all employees) and NOT the desk-upload spreadsheet columns.
- **D-02:** Pull it via the EXISTING bulk `POST /reports/custom` call in
  `HttpBambooHRClient.listEmployees` (add `4517` to the fields array; read `customWorkingdays`).
  No per-employee fetch needed. Sibling `Shift` (4516) = work hours — NOT used for days off.
- **D-03:** `DayOffType.MANDATORY` rows = the days NOT in the parsed working-days set, generated
  recurring across the schedule horizon in `BambooRefreshService`. Replaces the dead
  `"MANDATORY".equalsIgnoreCase(type)` match (`BambooRefreshService.java:263`).

### Non-standard working-days values → honour but flag
- **D-04:** Honour the BambooHR value **literally**: days off = `{Mon..Sun}` minus working days,
  whether that yields 0, 1, 2, 3+, or non-consecutive days off. BambooHR is the source of truth.
- **D-05:** **Flag outliers** to the operator for review when a pattern is unusual — specifically
  ≠ 2 contiguous days off, or 0 days off (e.g. `Mon - Sun`). Block as-is, but surface it.
- **D-06:** Parser must be tolerant of free-text formats seen live: ranges (`Mon-Fri`, `Wed-Sun`,
  week-wrapping like `Fri-Tue`), `"X to Y"` (`Mon. to Thurs.`), comma lists
  (`Mon, Tue, Wed, Thu, Sat`), trailing annotations (`Mon - Sun HOOP`), and day-token spellings
  (`Mon`/`Mon.`/`Thu`/`Thur`/`Thurs.`). `Variable` and blank → see D-07.

### Agents with no fixed weekend → data gap, exclude
- **D-07:** When `Working days` is `Variable` or blank, treat as a **data gap**: do NOT auto-schedule
  the agent until BambooHR is populated, and surface them to the operator. Reuse the
  eligibility-exclusion pattern from Phase 5 (`AgentEligibilityService`) rather than inventing days off.

### PTO behaviour → unchanged
- **D-08:** Keep current PTO behaviour (shipped in plan 05-03): `DayOffStatus.APPROVED` PTO hard-blocks
  the day; `REQUESTED` PTO is visible in diagnostics but does NOT block scheduling. No change.

### Solver
- **D-09:** No solver-engine change for mandatory blocks — `SolverService.buildAgentDaysOffMap()`
  (`SolverService.java:951-957`) already treats MANDATORY as "always blocks". This phase feeds it
  correct data; it does not alter the constraint.

### Claude's Discretion
- Exact persistence shape of the per-agent weekly pattern (transient generation vs stored column/table).
- Exact surfacing mechanism for the "data gap" + "outlier" flags (likely extends the BambooHR Sync
  Status surface / a diagnostics view — coordinate with Phase 7 DIAG work).

### Folded Todos
- **Import BambooHR weekly work pattern for MANDATORY day-offs**
  (`.planning/todos/pending/2026-06-02-import-bamboohr-weekly-work-pattern-for-mandatory-day-offs.md`)
  — the full root-cause analysis, live field findings (field 4517, coverage, value formats), and the
  step-by-step fix plan. This phase IS the execution of that todo. MUST read before planning.
</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### This phase's scope + findings
- `.planning/todos/pending/2026-06-02-import-bamboohr-weekly-work-pattern-for-mandatory-day-offs.md`
  — confirmed source (field 4517), coverage stats, value-format catalog, 5-step plan.
- `.planning/REQUIREMENTS.md` — QUAL-01 (every agent 2 contiguous days off / "weekend").
- `.planning/ROADMAP.md` §"v1.1 … Phase Details" → Phase 6.

### Code the plan touches
- `src/main/java/com/wfm/integration/HttpBambooHRClient.java:130-184` — `listEmployees` custom-report pull (add field 4517).
- `src/main/java/com/wfm/integration/BambooEmployee.java` — add weekly-pattern field.
- `src/main/java/com/wfm/integration/BambooRefreshService.java:256-280` — day-off persistence + dead MANDATORY match at :263.
- `src/main/java/com/wfm/service/AgentEligibilityService.java` — reuse for the data-gap exclusion (D-07).
- `src/main/java/com/wfm/service/SolverService.java:951-957` — `buildAgentDaysOffMap` (consumer, unchanged).
- `src/main/java/com/wfm/model/DayOffType.java`, `DayOffStatus.java` — MANDATORY/PTO, APPROVED/REQUESTED.
- `src/main/java/com/wfm/integration/MockBambooHRClient.java` — update mock to emit a `customWorkingdays`-style value.

### External
- BambooHR API — custom report fields by id: https://documentation.bamboohr.com/reference (field 4517 returns under alias `customWorkingdays`; built-in Work Schedule is NOT exposed, custom fields are).
</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `AgentEligibilityService` (Phase 5): the non-schedulable-exclusion pattern — reuse for D-07 data-gap exclusion of agents with Variable/blank Working days.
- `BambooHR rate-limit handling` (Phase 5, 503/429): already in place; the refresh stays a bulk call so no extra rate pressure.
- `BambooSyncEvent` + Sync Status card (Phase 5): natural home for surfacing the data-gap / outlier flags.

### Established Patterns
- Day-offs are stored as `AgentDayOff(type, status)` and consumed by `SolverService.buildAgentDaysOffMap()` — MANDATORY always blocks, PTO blocks only when APPROVED. This phase produces the rows; the solver logic is untouched.
- BambooHR enrichment flows through `BambooRefreshService.persistRefreshData`.

### Integration Points
- New: read `customWorkingdays` in `HttpBambooHRClient.listEmployees`; parse → off-days; generate MANDATORY `AgentDayOff` rows in `BambooRefreshService`.
</code_context>

<specifics>
## Specific Ideas

Live BambooHR (helpware) probe, 2026-06-02 — real `Working days` values to design against:
`Mon-Fri`, `Wed-Sun`, `Sun-Thu`, `Tue-Sat`, `Mon. to Thurs.`, `Mon, Tue, Wed, Thu, Sat`,
`Mon - Sun`, `Mon - Sun HOOP`, `Variable`, blank. Coverage company-wide: 5,241/11,707 populated
(45%), 2,432 `Variable` (21%), rest blank.

**Key risk to validate during planning:** D-07 excludes agents with blank/`Variable` Working days.
Company-wide that's ~55%. Confirm coverage for the DESKS/PROJECTS actually scheduled (e.g. StubHub-GE,
Vinted-UA) before shipping — if a live desk has many blank-field agents, the exclusion would gut its
schedule. The 3 StubHub samples were all populated, but verify at desk scale.
</specifics>

<deferred>
## Deferred Ideas

- **QUAL-02 — weekend-position fairness distribution** (rotate desirable weekends fairly across agents).
  Belongs in the solver-quality follow-on; depends on this phase's fixed-vs-flexible weekend data.
- **QUAL-03 — day-to-day hours consistency** (penalise erratic daily-hours variation). Follow-on phase.
- **Roadmap split:** formally split Phase 6 into "6a PTO & Weekends" (this) and "6b Fairness & Hours"
  (QUAL-02/03) via `/gsd-phase` when convenient.

### Reviewed Todos (not folded)
None — the one matching todo was folded.
</deferred>

---

*Phase: 6-Solver Quality Constraints*
*Context gathered: 2026-06-02*
