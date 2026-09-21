---
phase: 16-usual-shift-storage
plan: 01
subsystem: scheduling
tags: [jpa, flyway, testcontainers, spring-boot, apache-poi, rest-api]

# Dependency graph
requires:
  - phase: 14-shift-library-scheduling-mode
    provides: ShiftTemplate entity/repository, era model (effectiveFrom/effectiveTo, valid_weekdays mask, isEffectiveOn)
provides:
  - agent_usual_shift table (V47) with real FK to shift_template, no denormalized name column
  - AgentUsualShift entity + AgentUsualShiftRepository
  - UsualShiftResolutionService — the one era-resolution implementation for stored usual-shift rows
  - UsualShiftService.setUsualShift/clearUsualShifts — the single choke-point write, ready for
    plan 16-02/16-03 to call clearUsualShifts from removeDeskAgent/clearDesk
  - PUT /api/v1/desks/{deskId}/agents/{agentId}/usual-shift/{day} endpoint
  - DeskAgentResponse.usualShift (always-7-key, D-16 three-state discriminator DTO shape) that
    16-02/16-05 extend with the NOT_WORKED reason and the roster tile UI
  - EnrichedColumnLayout.usualShiftHeader — the header definition 16-03's upload template/parser reuse
  - Seven Usual Shift export columns in DeskAgentExportService (First/Last Name now at indices 27/28)
  - Fixed: PostgresBackedTest now shares one Testcontainers Postgres across all subclasses
affects: [16-02-agent-usual-shift-write-paths, 16-03-upload-template-usual-shift, 16-05-roster-ui-usual-shift]

# Actuals (#2632)
actuals:
  tokens: 17622
  tasks: 2
  commits: 3

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "One era-resolution implementation, called by roster/export/future drift report (UsualShiftResolutionService), never a resolvePreferences-shaped duplicate"
    - "Write-then-read controller composition (UsualShiftService write, DeskAgentService.getDeskAgentResponse read) to keep the service dependency graph acyclic"
    - "Testcontainers singleton-container pattern (start once in a static initializer, never stop) for Postgres-backed test base classes shared across multiple subclasses"

key-files:
  created:
    - src/main/resources/db/migration/V47__add_agent_usual_shift.sql
    - src/main/java/com/wfm/model/AgentUsualShift.java
    - src/main/java/com/wfm/repository/AgentUsualShiftRepository.java
    - src/main/java/com/wfm/service/UsualShiftResolutionService.java
    - src/main/java/com/wfm/service/UsualShiftService.java
    - src/main/java/com/wfm/dto/SetUsualShiftRequest.java
    - src/test/java/com/wfm/service/UsualShiftTracerTest.java
    - src/test/java/com/wfm/repository/AgentUsualShiftPostgresTest.java
  modified:
    - src/main/java/com/wfm/dto/DeskAgentResponse.java
    - src/main/java/com/wfm/controller/DeskAgentController.java
    - src/main/java/com/wfm/service/DeskAgentService.java
    - src/main/java/com/wfm/util/EnrichedColumnLayout.java
    - src/main/java/com/wfm/service/DeskAgentExportService.java
    - src/test/java/com/wfm/migration/MigrationEntityConsistencyTest.java
    - src/test/java/com/wfm/service/DeskAgentExportServiceTest.java
    - src/test/java/com/wfm/support/PostgresBackedTest.java

key-decisions:
  - "Adopted P-01..P-06 verbatim: real FK with resolve-by-name (P-01), both FKs ON DELETE CASCADE (P-02), server-side dead-template rejection (P-03), P-04 package placement, P-05 NOT_WORKED deferred to 16-02, P-06 write-then-read controller composition"
  - "Fixed PostgresBackedTest's shared static container: switched from @Container's per-test-class lifecycle to Testcontainers' singleton pattern (start once, never stop) after adding this plan's second subclass exposed a stop-collision that killed the first subclass's container out from under the second"

requirements-completed: []  # blocked: USHF-01/USHF-06 shared with 16-02, USHF-03/USHF-06/XCUT-01 shared with 16-05 — none of those sibling plans have finished yet (shared-ID gate, #2388)

coverage:
  - id: D1
    description: "An operator can store an agent's usual shift for Monday through the choke-point PUT endpoint and see it in the same request's response body"
    requirement: USHF-01
    verification:
      - kind: integration
        ref: "UsualShiftTracerTest#happyPath_storeRosterExport_endToEnd"
        status: pass
    human_judgment: false
  - id: D2
    description: "The stored shift is visible on the roster GET (listDeskAgentResponses) and in the Excel export, at the correct column indices, with no mocked layer between store and read"
    requirement: XCUT-01
    verification:
      - kind: integration
        ref: "UsualShiftTracerTest#happyPath_storeRosterExport_endToEnd"
        status: pass
    human_judgment: false
  - id: D3
    description: "An agent with no stored usual shift resolves to NOT_SET with a null name, never a substitute value"
    requirement: USHF-01
    verification:
      - kind: integration
        ref: "UsualShiftTracerTest#happyPath_storeRosterExport_endToEnd"
        status: pass
    human_judgment: false
  - id: D4
    description: "Setting a usual shift is rejected inline (400) when the template's weekday mask excludes the target day (D-03) or the template is not currently effective (P-03)"
    requirement: USHF-03
    verification:
      - kind: integration
        ref: "UsualShiftTracerTest#weekdayMaskViolation_isRejected"
        status: pass
      - kind: integration
        ref: "UsualShiftTracerTest#retiredTemplate_isRejected"
        status: pass
    human_judgment: false
  - id: D5
    description: "The write is a genuine choke point: cross-desk agent access and cross-desk template references are both rejected, and a repeated set for the same weekday leaves exactly one row"
    requirement: USHF-03
    verification:
      - kind: integration
        ref: "UsualShiftTracerTest#wrongDesk_throwsEntityNotFound_andWritesNoRow"
        status: pass
      - kind: integration
        ref: "UsualShiftTracerTest#crossDeskTemplate_isRejected"
        status: pass
      - kind: integration
        ref: "UsualShiftTracerTest#repeatedSetForSameWeekday_leavesExactlyOneRow"
        status: pass
    human_judgment: false
  - id: D6
    description: "The seven Usual Shift export columns land at the correct indices (20-26) immediately after the day-hours group, with First/Last Name shifted to 27/28, and the exported cell carries the raw stored template name"
    requirement: USHF-06
    verification:
      - kind: integration
        ref: "UsualShiftTracerTest#happyPath_storeRosterExport_endToEnd"
        status: pass
      - kind: unit
        ref: "DeskAgentExportServiceTest#headerRow_matchesTheFullExpectedOrder"
        status: pass
    human_judgment: false
  - id: D7
    description: "The migration and entity are proven consistent by two independent mechanisms, one of which runs real Flyway migrations against real Postgres with ddl-auto=validate (G-14-1 class of bug cannot ship green again)"
    verification:
      - kind: unit
        ref: "MigrationEntityConsistencyTest#migrationDeclaredColumns_reconcileWithEntityMappings"
        status: pass
      - kind: integration
        ref: "AgentUsualShiftPostgresTest#persistAndReload_roundTripsEveryField"
        status: pass
      - kind: integration
        ref: "AgentUsualShiftPostgresTest#dayOfWeekColumn_isVariableLengthNine"
        status: pass
    human_judgment: false
  - id: D8
    description: "Deleting a desk with stored usual shifts still succeeds — the shift_template_id cascade is not blocked by a dangling reference (T-16-03)"
    verification:
      - kind: integration
        ref: "AgentUsualShiftPostgresTest#deletingDesk_cascadesThroughShiftTemplateToUsualShiftRows"
        status: pass
    human_judgment: false

# Metrics
duration: 43min
completed: 2026-09-03
status: complete
---

# Phase 16 Plan 01: Usual Shift Tracer Summary

**End-to-end usual-shift tracer: `agent_usual_shift` table with a real FK to `shift_template`, a single choke-point write endpoint, a resolve-by-name three-state roster discriminator, and seven Excel export columns — proven in one runnable test with no mocked layer, plus a real-Flyway-and-Postgres migration proof.**

## Performance

- **Duration:** 43 min
- **Started:** 2026-09-03T15:15:05Z
- **Completed:** 2026-09-03T15:58:12Z
- **Tasks:** 2
- **Files modified:** 22 (8 created, 14 modified)

## Accomplishments
- `agent_usual_shift` table (V47) and `AgentUsualShift` entity: a real FK to `shift_template`, no denormalized template-name column (P-01), both foreign keys `ON DELETE CASCADE` (P-02)
- `UsualShiftResolutionService` — the one era-resolution implementation, resolving a stored row's template NAME across `shift_template` eras and returning whichever era is effective on a given date (D-01/D-02)
- `UsualShiftService.setUsualShift` — the single choke-point write: T-13-05/T-16-01 IDOR guard (tenant+desk-scoped agent resolution before any repository call), T-16-02 cross-desk template guard, P-03 dead-template rejection, D-03 weekday-mask rejection (reject, not clamp)
- `DeskAgentResponse.usualShift` — always-7-key, D-16 three-state discriminator (`NOT_SET` / `LIVE` / `STORED_INACTIVE`); `PUT /api/v1/desks/{deskId}/agents/{agentId}/usual-shift/{day}` composes the write (`UsualShiftService`) with a read (`DeskAgentService.getDeskAgentResponse`, P-06)
- `EnrichedColumnLayout.usualShiftHeader` — the only place the `"Usual Shift "` header prefix is written; seven export columns added to `DeskAgentExportService` immediately after the day-hours group, shifting First/Last Name from indices 20/21 to 27/28
- `UsualShiftTracerTest` (10 methods) proves store → roster → export end-to-end with no mocked layer; `AgentUsualShiftPostgresTest` (5 assertions) proves the migration against real Flyway/Postgres with `ddl-auto=validate`
- `MigrationEntityConsistencyTest` extended with `agent_usual_shift`/`AgentUsualShift.class` — the cheap, no-container half of the G-14-1 guard

## Task Commits

Each task was committed atomically:

1. **Task 1: End-to-end "an agent has a usual shift on Monday" — one path only** - `680a24a` (feat)
2. **Task 2: Prove the new table against real Flyway and real Postgres** - `b6db358` (test)

**Deviation fix (not a plan task):** `def2c47` (fix) — `PostgresBackedTest` shared-container lifecycle bug, discovered while running Task 2's verification under the full suite.

_Note: Task 1's [Rule 3] compile-ripple fixes (7 test files) are committed as part of `680a24a` since they were required for that same commit to compile._

## Files Created/Modified
- `src/main/resources/db/migration/V47__add_agent_usual_shift.sql` — `agent_usual_shift` table
- `src/main/java/com/wfm/model/AgentUsualShift.java` — entity, real FK to `ShiftTemplate`
- `src/main/java/com/wfm/repository/AgentUsualShiftRepository.java` — 4 methods mirroring `AgentDayHoursRepository`
- `src/main/java/com/wfm/service/UsualShiftResolutionService.java` — the one era-resolution implementation
- `src/main/java/com/wfm/service/UsualShiftService.java` — the choke-point write + `clearUsualShifts`
- `src/main/java/com/wfm/dto/SetUsualShiftRequest.java` — write DTO
- `src/main/java/com/wfm/dto/DeskAgentResponse.java` — `usualShift` field + `UsualShiftEntry`/`UsualShiftStatus`/`UsualShiftReason`
- `src/main/java/com/wfm/controller/DeskAgentController.java` — `PUT .../usual-shift/{day}` endpoint
- `src/main/java/com/wfm/service/DeskAgentService.java` — `loadUsualShiftsByAgent`, `getDeskAgentResponse`, widened `toResponse`
- `src/main/java/com/wfm/util/EnrichedColumnLayout.java` — `usualShiftHeader(DayOfWeek)`
- `src/main/java/com/wfm/service/DeskAgentExportService.java` — `FIRST_USUAL_SHIFT_COLUMN`, `writeUsualShiftCells`
- `src/test/java/com/wfm/service/UsualShiftTracerTest.java` — 10-method end-to-end tracer
- `src/test/java/com/wfm/migration/MigrationEntityConsistencyTest.java` — `agent_usual_shift` added to `DECLARED_TABLES`
- `src/test/java/com/wfm/service/DeskAgentExportServiceTest.java` — extended `expectedHeaders()`, moved First Name index assertion
- `src/test/java/com/wfm/repository/AgentUsualShiftPostgresTest.java` — 5-assertion real-Postgres proof
- `src/test/java/com/wfm/support/PostgresBackedTest.java` — container-sharing fix (deviation)
- `src/test/java/com/wfm/service/DeskAgentServiceBulkRollbackTest.java`, `DeskAgentServiceContractedHoursTest.java`, `DeskAgentServiceDayHoursTest.java`, `DeskAgentServiceReadPathTest.java`, `DeskAssignmentTemplateFilterTest.java`, `DeskAssignmentTemplateServiceTest.java` — compile-ripple updates for `DeskAgentResponse`'s new record component / `DeskAgentService`'s new constructor dependency (deviation)

## Decisions Made
- Adopted planner decisions P-01 through P-06 verbatim (see PLAN.md `<planner_decisions>`): real FK with resolve-by-name, both FKs cascade on delete, server-side rejection of an already-retired template, `AgentUsualShiftPostgresTest` in `com.wfm.repository` (not `com.wfm.support`), `NOT_WORKED` deferred to plan 16-02, and the write-then-read controller composition that keeps `UsualShiftService` injecting no other service.
- Migration head was confirmed as V46 before naming this migration V47, per the phase-specific constraint — the head had not moved since `16-CONTEXT.md`/`16-PATTERNS.md` last checked it.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Updated 7 test files for `DeskAgentResponse`'s new record component and `DeskAgentService`'s new constructor dependency**
- **Found during:** Task 1 (widening `DeskAgentResponse`/`DeskAgentService`)
- **Issue:** Adding the `usualShift` record component to `DeskAgentResponse` and two new constructor parameters (`AgentUsualShiftRepository`, `UsualShiftResolutionService`) to `DeskAgentService` broke compilation of every file that directly constructs `DeskAgentResponse` or `@Import`s `DeskAgentService` under `@DataJpaTest`.
- **Fix:** Added a trailing `Map.of()` argument to the `DeskAgentResponse` constructor calls in `DeskAgentExportServiceTest`, `DeskAssignmentTemplateServiceTest`, and `DeskAssignmentTemplateFilterTest`; added `UsualShiftResolutionService.class` to the `@Import` list in `DeskAgentServiceDayHoursTest`, `DeskAgentServiceContractedHoursTest`, `DeskAgentServiceReadPathTest`, and `DeskAgentServiceBulkRollbackTest`.
- **Files modified:** the 7 files listed above.
- **Verification:** `./gradlew compileTestJava` clean; all 7 files' own test suites pass individually and under the full run.
- **Committed in:** `680a24a` (part of Task 1's commit — the plan's own `<verification>` names these exact classes as ones the DTO/signature change would touch)

**2. [Rule 3 - Blocking] Fixed `PostgresBackedTest`'s shared-static-container lifecycle**
- **Found during:** Task 2 verification, running `./gradlew test` (full suite) for the first time
- **Issue:** `AgentUsualShiftPostgresTest` is the second subclass of `PostgresBackedTest` (`AgentRepositoryPostgresTest` was the only one before this plan). The base class's `@Container`-annotated static `POSTGRES` field is started before, and stopped after, each test CLASS's run — but the field itself is one shared static slot declared on the superclass, not duplicated per subclass. Under the full suite, whichever Postgres-backed class ran second failed all 5 of its tests with `PSQLException: Connection ... refused`, because the first class's teardown had already stopped the shared container.
- **Fix:** Switched to Testcontainers' documented "singleton container" pattern: removed `@Container` from the field, start the container once in a static initializer (guarded on `DockerClientFactory.instance().isDockerAvailable()` so class loading itself never throws before `@Testcontainers(disabledWithoutDocker = true)` can skip), and never call `stop()` — Testcontainers' Ryuk reaper cleans it up at JVM exit.
- **Files modified:** `src/test/java/com/wfm/support/PostgresBackedTest.java`
- **Verification:** `./gradlew test --tests AgentRepositoryPostgresTest --tests AgentUsualShiftPostgresTest` green together; full suite re-run green (660 tests, 0 failures, 10m44s).
- **Committed in:** `def2c47`

---

**Total deviations:** 2 auto-fixed (both Rule 3 — blocking compile/infrastructure issues)
**Impact on plan:** Both fixes were necessary to reach a green full suite; neither changed scope beyond what this plan's own file-shape and Postgres-test-count changes required. No scope creep.

## Issues Encountered
None beyond the two deviations above (both resolved).

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- The `UsualShiftService` choke point, `UsualShiftResolutionService`, and `AgentUsualShiftRepository` are ready for plan 16-02 to build on (write-path guards, `clearUsualShifts` call sites in `removeDeskAgent`, the `NOT_WORKED` resolution arm) and plan 16-03 (upload template columns via `EnrichedColumnLayout.usualShiftHeader`, `clearDesk` call site).
- `DeskAgentResponse.usualShift` is stable for plan 16-05's roster UI tile.
- `PostgresBackedTest` now safely supports additional subclasses — future Postgres-backed test classes (e.g. in 16-04) will not hit the container-stop collision this plan found and fixed.
- No blockers.

## Self-Check: PASSED

All 8 created files verified present on disk; SUMMARY.md itself verified present; all 3 commit
hashes (`680a24a`, `b6db358`, `def2c47`) verified present in git log.

---
*Phase: 16-usual-shift-storage*
*Completed: 2026-09-03*
