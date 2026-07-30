---
phase: 09-agent-data-model-foundation
plan: 05
subsystem: api
tags: [java, spring-boot, jpa, dto, excel-export, tdd]

# Dependency graph
requires:
  - phase: 09-01
    provides: Agent.getFirstName()/getLastName() (name split)
  - phase: 09-02
    provides: AgentDayHours entity + AgentDayHoursRepository (deleteByAgent_Id, save, findByTenantIdAndAgent_Id)
provides:
  - AgentResponse and DeskAgentResponse expose firstName/lastName alongside the combined name
  - Desk Agents Excel export includes First Name/Last Name trailing columns (indices 13/14)
  - DeskAgentService.setContractedHours fans an hours edit out to all 7 agent_day_hours rows, replacing rather than appending
affects: [09-06, phase-10-spreadsheet-upload]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Derived Spring Data delete-then-flush before re-insert to avoid unique-constraint races when a delete and its replacement inserts happen in the same transaction"

key-files:
  created:
    - src/test/java/com/wfm/service/DeskAgentServiceContractedHoursTest.java
  modified:
    - src/main/java/com/wfm/dto/AgentResponse.java
    - src/main/java/com/wfm/service/AgentService.java
    - src/main/java/com/wfm/service/ClientManagementService.java
    - src/main/java/com/wfm/dto/DeskAgentResponse.java
    - src/main/java/com/wfm/service/DeskAgentService.java
    - src/main/java/com/wfm/service/DeskAgentExportService.java

key-decisions:
  - "Flush AgentDayHoursRepository immediately after deleteByAgent_Id, before writing the 7 replacement rows in the same transaction — Hibernate's default insert-before-delete flush ordering otherwise violates the (agent_id, day_of_week) unique constraint"

patterns-established:
  - "New DTO fields appended after the existing field they augment (firstName/lastName after name) to keep positional-arg call sites easy to diff"
  - "Excel export columns for new fields appended as trailing indices, never inserted mid-array, so existing column positions never shift"

requirements-completed: [MDL-01, MDL-02]

duration: 3min
completed: 2026-07-30
---

# Phase 9 Plan 05: Agent DTO/Export Name Exposure + Hours Fan-out Summary

**AgentResponse/DeskAgentResponse and the Excel export now surface firstName/lastName (D-08/D-12), and DeskAgentService.setContractedHours fans an operator hours edit out to all 7 agent_day_hours rows so the solver keeps honouring it post-migration (D-10).**

## Performance

- **Duration:** ~3 min
- **Started:** 2026-07-30T10:41:55-04:00
- **Completed:** 2026-07-30T10:44:50-04:00
- **Tasks:** 3
- **Files modified:** 6 (+ 1 test file created)

## Accomplishments
- `AgentResponse` and `DeskAgentResponse` gain `firstName`/`lastName` fields (keeping the combined `name`), with all four construction sites updated to match
- Desk Agents Excel export appends "First Name"/"Last Name" as trailing columns 13/14 without shifting existing columns 0-12
- `DeskAgentService.setContractedHours` now injects `AgentDayHoursRepository` and fans the normalized hours value out to one `AgentDayHours` row per `DayOfWeek` (replace semantics via delete+flush then insert), pinned by a new `@DataJpaTest`

## Task Commits

Each task was committed atomically:

1. **Task 1: AgentResponse firstName/lastName + its two construction sites (D-08)** - `32a8bfc` (feat)
2. **Task 2: DeskAgentResponse firstName/lastName + toResponse + export columns (D-08/D-12)** - `0ed4b8b` (feat)
3. **Task 3: setContractedHours fans out to 7 agent_day_hours rows (D-10 set side)**
   - RED: `917ef56` (test) — failing test asserting 7-row fan-out and replace-not-append
   - GREEN: `9852e14` (feat) — fan-out implementation + flush fix

**Plan metadata:** (this commit)

## Files Created/Modified
- `src/main/java/com/wfm/dto/AgentResponse.java` - added `firstName`/`lastName` fields after `name`
- `src/main/java/com/wfm/service/AgentService.java` - `toResponse` passes the new fields
- `src/main/java/com/wfm/service/ClientManagementService.java` - bulk-assign `AgentResponse` construction passes the new fields
- `src/main/java/com/wfm/dto/DeskAgentResponse.java` - added `firstName`/`lastName` fields after `name` (no per-day hours per D-12)
- `src/main/java/com/wfm/service/DeskAgentService.java` - `toResponse` passes the new fields; `setContractedHours` injects `AgentDayHoursRepository` and fans out to 7 per-day rows
- `src/main/java/com/wfm/service/DeskAgentExportService.java` - appended "First Name"/"Last Name" header columns and matching `createCell(13)`/`createCell(14)` writes
- `src/test/java/com/wfm/service/DeskAgentServiceContractedHoursTest.java` (new) - `@DataJpaTest` + `@Import(DeskAgentService.class)` pinning the 7-row fan-out and replace-not-append behaviour

## Decisions Made
- Flush `AgentDayHoursRepository` immediately after `deleteByAgent_Id` and before the 7 replacement inserts in `setContractedHours`. Without the flush, Hibernate's default action ordering (inserts flushed before deletes within the same auto-flush) executed the new inserts while the old rows were still present in H2, violating the `(agent_id, day_of_week)` unique constraint on the second `setContractedHours` call for the same agent. This is a Rule 1 (bug) fix discovered while turning the RED test GREEN — not a plan deviation requiring a separate task, since it's part of making Task 3's stated behavior actually work.

## Deviations from Plan

None beyond the flush fix noted above, which was folded into Task 3's GREEN commit per the TDD execution flow (RED → GREEN, debug/iterate until GREEN as expected by `<tdd_execution>` guidance) rather than tracked as a separate deviation.

## Issues Encountered
- Second `setContractedHours` call for the same agent threw `DataIntegrityViolationException` on the `agent_day_hours` unique constraint before the `agentDayHoursRepository.flush()` fix was added between the delete and the replacement inserts (see Decisions Made).

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- Plan 06 (same phase) and later phases can now rely on `AgentResponse`/`DeskAgentResponse` exposing `firstName`/`lastName`, and on operator hours edits reaching `agent_day_hours` durably.
- No blockers. Per-day hours remain intentionally unsurfaced on the DTOs (D-12) — Phase 10's spreadsheet work is expected to introduce that consumer.

---
*Phase: 09-agent-data-model-foundation*
*Completed: 2026-07-30*
