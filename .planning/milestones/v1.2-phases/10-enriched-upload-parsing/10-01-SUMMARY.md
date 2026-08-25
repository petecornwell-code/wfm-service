---
phase: 10-enriched-upload-parsing
plan: 01
subsystem: database
tags: [flyway, jpa, hibernate, timefold, postgres, junit5, assertj]

# Dependency graph
requires:
  - phase: 09-agent-data-model-foundation
    provides: agent_day_hours table (V29) and AgentDayHours entity — the table this plan extends
provides:
  - Nullable day_off_type column on agent_day_hours (V30 Flyway migration)
  - AgentDayHours.dayOffType field (nullable DayOffType, @Enumerated STRING) with getter/setter
  - Structural regression test proving BambooRefreshService has no AgentDayHoursRepository dependency
affects: [10-02, 10-03, 10-04, 10-05, 10-06]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Recurring day-off descriptive flavor (MANDATORY/PTO) stored as a nullable label column on the weekday-keyed agent_day_hours table, not as dated AgentDayOff rows — avoids BambooRefreshService's rolling-window delete/regenerate wiping spreadsheet-sourced state"
    - "Structural (reflection-based field-absence) regression test used in place of Mockito interaction-verification when the class under test has no collaborator to mock"

key-files:
  created:
    - src/main/resources/db/migration/V30__agent_day_hours_recurring_status.sql
  modified:
    - src/main/java/com/wfm/model/AgentDayHours.java
    - src/test/java/com/wfm/model/AgentDayHoursPersistenceTest.java
    - src/test/java/com/wfm/integration/BambooRefreshServiceTest.java

key-decisions:
  - "D-12 storage confirmed: recurring MANDATORY/PTO label lives on agent_day_hours.day_off_type (nullable), not on dated AgentDayOff rows, because BambooRefreshService deletes and regenerates every AgentDayOff row in its rolling window on every sync"
  - "V30 is the correct next migration version — confirmed V29 is the latest applied version in the migration directory before creating V30"
  - "dayOffType is intentionally nullable (diverges from AgentDayOff.type's nullable=false) — null represents the worked-day / unlabelled-0 state that AgentDayOff has no equivalent for"

patterns-established:
  - "Weekday-keyed durable label column (no date range, no expiry) as the safe storage location for anything BambooRefreshService's window-scoped delete/regenerate must never touch"

requirements-completed: [UPL-04, UPL-05]

# Metrics
duration: 20min
completed: 2026-07-31
---

# Phase 10 Plan 01: D-12 Recurring Day-Off Storage Foundation Summary

**Nullable `day_off_type` column added to `agent_day_hours` (Flyway V30) plus a reflection-based structural test proving `BambooRefreshService` can never touch it — the refresh-safe storage foundation the Phase 10 parser (plan 03) writes MANDATORY/PTO into.**

## Performance

- **Duration:** 20 min
- **Started:** 2026-07-31T21:06:48Z
- **Completed:** 2026-07-31T21:18:14Z
- **Tasks:** 3 (Task 2 executed as TDD: RED then GREEN)
- **Files modified:** 4 (1 created, 3 modified)

## Accomplishments
- Forward-only Flyway `V30__agent_day_hours_recurring_status.sql` adds a nullable `day_off_type VARCHAR(9)` column to `agent_day_hours`, with no backfill and no down migration, mirroring V29's additive-column style. Confirmed V29 was the true latest version and no V30/V31 collision exists.
- `AgentDayHours.dayOffType` (nullable `DayOffType`, `@Enumerated(EnumType.STRING)`, mapped to `day_off_type`) added with getter/setter, following TDD: a failing test was written first (compile failure with `getDayOffType`/`setDayOffType` undefined), then the field/accessors were added and the test went green.
- A structural regression test (`refreshService_declaresNoAgentDayHoursRepositoryDependency`) proves via reflection that `BambooRefreshService` declares no `AgentDayHoursRepository` field and no `AgentDayHours`-typed field — the type-level guarantee that a BambooHR sync structurally cannot delete or mutate `agent_day_hours` rows, so spreadsheet-sourced MANDATORY/PTO/hours labels survive every refresh (D-12, Pitfall 2, T-10-01). No change was made to `BambooRefreshService`'s constructor or fields.

## Task Commits

Each task was committed atomically:

1. **Task 1: V30 migration — add nullable day_off_type column** - `c19a81a` (feat)
2. **Task 2: Add nullable dayOffType field to AgentDayHours** - TDD: `643426c` (test, RED) → `0199b7a` (feat, GREEN)
3. **Task 3: D-12 refresh-safety structural regression test** - `b23f90d` (test)

_Task 2 was executed as TDD: failing test committed first (confirmed via `./gradlew compileTestJava` failure — 5 "cannot find symbol" errors for `getDayOffType`/`setDayOffType`), then the entity field/accessors were added and `./gradlew test --tests com.wfm.model.AgentDayHoursPersistenceTest` passed._

## Files Created/Modified
- `src/main/resources/db/migration/V30__agent_day_hours_recurring_status.sql` - Adds nullable `day_off_type VARCHAR(9)` column to `agent_day_hours`, with SQL comments documenting the null/MANDATORY/PTO semantics and the refresh-safety rationale.
- `src/main/java/com/wfm/model/AgentDayHours.java` - Adds `dayOffType` field (`@Enumerated(EnumType.STRING)`, `@Column(name = "day_off_type", length = 9)`, no `nullable = false`) with getter/setter and Javadoc noting it is reporting metadata only, never read by the solver.
- `src/test/java/com/wfm/model/AgentDayHoursPersistenceTest.java` - Adds 3 tests covering `dayOffType` left unset (persists null), `MANDATORY` round-trip, `PTO` round-trip.
- `src/test/java/com/wfm/integration/BambooRefreshServiceTest.java` - Adds `refreshService_declaresNoAgentDayHoursRepositoryDependency`, asserting via `Class.getDeclaredFields()` reflection that no field's type simple-name is `AgentDayHoursRepository` or `AgentDayHours`.

## Decisions Made
- Confirmed D-12's research recommendation is the correct implementation: label the day-off flavor on `agent_day_hours` (a table refresh never queries by date range), not on dated `AgentDayOff` rows (which refresh unconditionally deletes and regenerates within its lookback/lookahead window).
- Verified by direct source read that `BambooRefreshService`'s 8-parameter constructor (`BambooHRClient`, `AgentRepository`, `DeskRepository`, `AgentDayOffRepository`, `SpecializationRepository`, `TransactionTemplate`, `JobTitleConfigService`, `BambooSyncEventService`) has no `AgentDayHoursRepository` dependency, and its rolling-window delete (`agentDayOffRepository.deleteByAgent_IdAndDateBetween`) is scoped to `AgentDayOffRepository` only — confirming the structural test's premise before writing it.
- Chose a reflection-based field-absence assertion over a Mockito interaction-verification test, since `BambooRefreshService` has no `AgentDayHoursRepository` collaborator to mock (matches the plan's explicit guidance and the file's existing reflection-based test idiom).

## Deviations from Plan

None - plan executed exactly as written. Task 2's TDD RED/GREEN cycle used the existing `AgentDayHoursPersistenceTest.java` file (not a new file) since the plan's `files_modified` list didn't specify a dedicated test file for Task 2 but the `tdd="true"` flag and `<behavior>` block required one; extending the existing persistence test for this same entity was the natural, lowest-friction location and required no new file creation.

## Issues Encountered
None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- The D-12 storage foundation (`agent_day_hours.day_off_type`) is in place and verified refresh-safe. Plan 03 (the parser) can now write `hours`/`dayOffType` pairs per the D-05 table (worked hours → null label; `0`/`MANDATORY`/`PTO` → `0.00` hours with `null`/`MANDATORY`/`PTO` label respectively) with no further schema work.
- `./gradlew test` (full suite) passes; `./gradlew compileJava compileTestJava` passes.
- No blockers for subsequent Phase 10 plans.

## Self-Check: PASSED

All 5 created/modified files found on disk; all 4 task commit hashes (c19a81a, 643426c, 0199b7a, b23f90d) found in git log.

---
*Phase: 10-enriched-upload-parsing*
*Completed: 2026-07-31*
