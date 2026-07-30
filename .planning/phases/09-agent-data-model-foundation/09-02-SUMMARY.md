---
phase: 09-agent-data-model-foundation
plan: 02
subsystem: database
tags: [jpa, hibernate, spring-data, postgres, tdd]

# Dependency graph
requires:
  - phase: 09-agent-data-model-foundation (plan 01, wave 1 sibling)
    provides: AgentDayHours is a standalone child entity; no direct dependency on plan 01's output within this plan
provides:
  - AgentDayHours JPA entity (agent_day_hours child table, STRING-enum day_of_week, NUMERIC(5,2) NOT NULL hours)
  - AgentDayHoursRepository (tenant-scoped bulk fetch + agent-scoped delete)
affects: [09-03 (solver resolution), 09-04 (upload clear), 09-05 (fan-out write), 09-06 (Flyway migration)]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Sibling child-entity convention (mirrors AgentDayOff/AgentException/AgentPreference): tenant_id + @ManyToOne LAZY agent, never a @OneToMany back-reference on Agent"
    - "STRING-enum weekday column (DayOfWeek, EnumType.STRING, length 9) mirrors AgentPreference.dayOfWeek"
    - "Row absence = 'no data' (schedule default applies); present row with 0.00 = 'not worked' (D-09)"

key-files:
  created:
    - src/main/java/com/wfm/model/AgentDayHours.java
    - src/main/java/com/wfm/repository/AgentDayHoursRepository.java
    - src/test/java/com/wfm/model/AgentDayHoursPersistenceTest.java
  modified: []

key-decisions:
  - "hours is nullable=false on AgentDayHours (unlike the Agent.contractedHoursPerDay scalar it replaces) — absence is represented by row absence per D-09, so every existing row must carry a real value"
  - "No @OneToMany List<AgentDayHours> added to Agent.java — downstream consumers (SolverService) resolve via repository + Map, matching the existing AgentDayOff/AgentException pattern exactly"

patterns-established:
  - "AgentDayHoursRepository.findByTenantIdAndDeskId joins through h.agent.deskId (no desk_id column on AgentDayHours itself) — same join-through-agent idiom as AgentDayOffRepository.findByTenantIdAndDeskIdAndDateBetween"

requirements-completed: [MDL-02]

# Metrics
duration: 12min
completed: 2026-07-30
---

# Phase 9 Plan 02: AgentDayHours Child Entity & Repository Summary

**AgentDayHours JPA entity and tenant-scoped Spring Data repository establishing the D-09 per-day-hours storage contract, TDD RED/GREEN verified against H2.**

## Performance

- **Duration:** 12 min
- **Started:** 2026-07-30T14:23:50Z
- **Completed:** 2026-07-30T14:36:23Z
- **Tasks:** 2 completed
- **Files modified:** 3 (all new)

## Accomplishments
- `AgentDayHours` entity: `agent_day_hours` table, unique `(agent_id, day_of_week)`, tenant-scoped, `day_of_week` STRING enum, `hours NUMERIC(5,2) NOT NULL` — mirrors `AgentDayOff` field-for-field with the `AgentPreference` DayOfWeek-column convention
- `AgentDayHoursRepository`: `findByTenantIdAndAgent_Id`, `findByTenantIdAndDeskId` (join-through-agent JPQL), `deleteByAgent_Id` — the exact fetch/delete contract Plans 09-03/04/05 consume
- `AgentDayHoursPersistenceTest`: 3 `@DataJpaTest` cases proving STRING-enum round-trip, scale-2 precision round-trip, and tenantId/agent-reference round-trip
- TDD gate followed correctly for Task 1: test committed first against the *absent* entity (confirmed compile-failure RED), then the entity was restored and the same test run GREEN before its own commit

## Task Commits

Each task was committed atomically:

1. **Task 1: AgentDayHours child entity** - `2baeb6b` (test, RED) + `16d4ab4` (feat, GREEN)
2. **Task 2: AgentDayHoursRepository (tenant-scoped)** - `62e84b1` (feat)

**Plan metadata:** (pending — this SUMMARY commit)

_Note: Task 1 is TDD (`tdd="true"`); it produced two commits (test → feat), matching the RED/GREEN gate sequence. Task 2 is a plain `auto` task with a single `feat` commit._

## Files Created/Modified
- `src/main/java/com/wfm/model/AgentDayHours.java` - New child entity: tenant-scoped, `@ManyToOne LAZY` agent, STRING-enum `dayOfWeek`, `NUMERIC(5,2) NOT NULL` `hours`, unique `(agent_id, day_of_week)`
- `src/main/java/com/wfm/repository/AgentDayHoursRepository.java` - `JpaRepository<AgentDayHours, UUID>` with tenant-scoped bulk fetch (direct + join-through-agent) and agent-scoped delete
- `src/test/java/com/wfm/model/AgentDayHoursPersistenceTest.java` - `@DataJpaTest` persistence proof: STRING-enum round-trip, scale-2 precision round-trip, tenantId/agent-id round-trip

## Decisions Made
- `hours` is `nullable=false` (mirrors `AgentException.contractedHoursOverride`'s pattern, not the nullable `Agent.contractedHoursPerDay` scalar it eventually supersedes) — absence-as-signal is expressed by row absence, never a null column value, per D-09
- No `@OneToMany` back-reference added to `Agent.java` — verified anti-pattern from `09-PATTERNS.md`: all three existing sibling child tables (`AgentDayOff`, `AgentException`, `AgentPreference`) are loaded independently via repository + folded into a `Map<UUID, ...>` inside `SolverService`; `AgentDayHours` follows the identical convention so 09-03 can build its map the same way

## Deviations from Plan

None - plan executed exactly as written. The TDD flow for Task 1 required one procedural adjustment not explicit in the plan text: since the entity file was drafted before the test (during context-gathering), it was moved aside so the test could genuinely fail to compile (RED) before being restored for GREEN — this preserves the fail-fast RED/GREEN gate the `tdd="true"` task type requires without any change to the produced code.

## Issues Encountered
None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- The `AgentDayHours` entity and `AgentDayHoursRepository` interface (`findByTenantIdAndAgent_Id`, `findByTenantIdAndDeskId`, `deleteByAgent_Id`) are ready for 09-03 (solver resolution), 09-04 (upload clear), 09-05 (fan-out write) to consume directly
- No Flyway migration was created in this plan (09-06 owns the `agent_day_hours` table DDL + backfill/fan-out INSERT) — the JPA entity and H2-backed test validate the entity mapping only, not the production schema; this is expected and matches `09-PATTERNS.md`'s noted Pitfall 4
- No blockers

---
*Phase: 09-agent-data-model-foundation*
*Completed: 2026-07-30*

## Self-Check: PASSED

All created files verified present on disk (AgentDayHours.java, AgentDayHoursRepository.java, AgentDayHoursPersistenceTest.java, this SUMMARY.md). All 4 commit hashes (2baeb6b, 16d4ab4, 62e84b1, 54244fd) verified present in `git log --oneline --all`.
