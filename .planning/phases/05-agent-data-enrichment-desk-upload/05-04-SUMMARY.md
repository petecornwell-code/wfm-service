---
phase: 05-agent-data-enrichment-desk-upload
plan: "04"
subsystem: backend-service-dto-repository
tags: [upload, shape-detection, skipped-row, non-schedulable, pto, employment-type]
dependency_graph:
  requires:
    - "AgentEligibilityService.isNonSchedulable (Plan 01)"
    - "EmploymentType enum (Plan 01)"
    - "JobTitleConfig entity + repository (Plan 01)"
  provides:
    - "SkippedRow DTO with rowNumber/bamboohrId/name/reason"
    - "DeskAssignmentUploadService: header-based shape detection + UploadShape enum"
    - "DeskAssignmentUploadService: structured SkippedRow list (replaces List<String>)"
    - "DeskAssignmentUploadService: non-schedulable rejection"
    - "ClientManagementService: non-schedulable rejection in assignEmployeesToDesk"
    - "DeskAgentResponse: employmentType + pendingPtoCount + pendingPtoDates fields"
    - "DeskAgentService: bulk pending PTO fetch (single query)"
    - "AgentDayOffRepository: findByAgentDeskIdAndTypeAndStatusAndDateGreaterThanEqual"
  affects:
    - "src/main/java/com/wfm/service/DeskAssignmentUploadService.java"
    - "src/main/java/com/wfm/service/ClientManagementService.java"
    - "src/main/java/com/wfm/service/DeskAgentService.java"
    - "src/main/java/com/wfm/dto/DeskAgentResponse.java"
    - "src/main/java/com/wfm/repository/AgentDayOffRepository.java"
tech_stack:
  added: []
  patterns:
    - "Header-based spreadsheet shape detection (lowercase-trimmed header map + enriched-wins-ties)"
    - "Structured DTO for skip reasons (record with named fields)"
    - "Bulk pre-fetch Map<UUID, List<LocalDate>> before stream.map loop (no N+1)"
    - "JPQL @Query scoped by deskId (already tenant-verified upstream)"
key_files:
  created:
    - src/main/java/com/wfm/dto/SkippedRow.java
    - src/test/java/com/wfm/service/DeskAssignmentUploadLegacyShapeTest.java
    - src/test/java/com/wfm/service/DeskAssignmentUploadEnrichedShapeTest.java
    - src/test/java/com/wfm/service/DeskAssignmentUploadNonSchedulableRejectTest.java
    - src/test/java/com/wfm/service/ClientManagementServiceNonSchedulableTest.java
  modified:
    - src/main/java/com/wfm/service/DeskAssignmentUploadService.java
    - src/main/java/com/wfm/service/ClientManagementService.java
    - src/main/java/com/wfm/service/DeskAgentService.java
    - src/main/java/com/wfm/dto/DeskAgentResponse.java
    - src/main/java/com/wfm/repository/AgentDayOffRepository.java
decisions:
  - "AgentDayOffRepository bulk PTO query uses @Query (JPQL) scoped by deskId — deskId is already tenant-verified upstream in DeskAgentService.listDeskAgentResponses; no redundant tenant_id filter needed in the new query"
  - "DeskAgentResponse new fields appended at end of record (positional constructor) to minimise call-site churn — only DeskAgentService.toResponse updated"
  - "DeskAssignmentUploadService test helpers use single-row workbook factories (not List.of(String[])) to avoid Java type inference issues with varargs"
  - "Non-schedulable check in DeskAssignmentUploadService placed AFTER agent resolution but BEFORE desk assignment — ensures agent jobTitle is populated from BambooHR cache before check"
metrics:
  duration_minutes: 18
  completed_date: "2026-05-29"
  tasks_completed: 2
  files_changed: 10
---

# Phase 5 Plan 4: Upload Shape Detection + DeskAgentResponse Enrichment Summary

**One-liner:** Header-based XLSX shape detection with structured SkippedRow records, non-schedulable rejection in both upload and manual-assign paths, and DeskAgentResponse enriched with employmentType + pendingPtoCount + pendingPtoDates via single-query bulk PTO fetch.

## Tasks Completed

| Task | Name | Commit | Key Files |
|------|------|--------|-----------|
| 1 (RED) | Failing tests for DeskAssignmentUpload shape + non-schedulable | b91fd16 | SkippedRow.java, 3 test files |
| 1 (GREEN) | Header-based shape detection, SkippedRow, non-schedulable rejection in DeskAssignmentUploadService | 79126bd | DeskAssignmentUploadService.java, 3 test files |
| 2 (RED) | Failing test for ClientManagementService non-schedulable rejection | eb40d1c | ClientManagementServiceNonSchedulableTest.java |
| 2 (GREEN) | ClientManagementService + DeskAgentResponse + DeskAgentService + AgentDayOffRepository | 27d5ca3 | ClientManagementService.java, DeskAgentResponse.java, DeskAgentService.java, AgentDayOffRepository.java |

## Verification Results

- `./gradlew test --tests "com.wfm.service.DeskAssignmentUpload*"` — 11 tests pass (4 legacy + 4 enriched + 3 non-schedulable)
- `./gradlew test --tests "com.wfm.service.ClientManagementServiceNonSchedulableTest"` — 4 tests pass
- `./gradlew test` — full suite BUILD SUCCESSFUL (8m 19s)
- `./gradlew compileJava` — BUILD SUCCESSFUL

### Acceptance Criteria

| Check | Result |
|-------|--------|
| SkippedRow.java exists | PASS |
| `public record SkippedRow` count = 1 | PASS |
| `rowNumber` in SkippedRow | PASS |
| `UploadShape` count >= 2 in DeskAssignmentUploadService | 6 (PASS) |
| `List<SkippedRow>` count >= 2 in DeskAssignmentUploadService | 2 (PASS) |
| `new SkippedRow(` count >= 5 in DeskAssignmentUploadService | 6 (PASS) |
| `agentEligibilityService.isNonSchedulable` count = 1 in DeskAssignmentUploadService | 1 (PASS) |
| `Unrecognised spreadsheet shape` count = 1 in DeskAssignmentUploadService | 1 (PASS) |
| All 3 test classes exist | PASS |
| `isNonSchedulable` count = 1 in ClientManagementService | 1 (PASS) |
| `ConflictException` count >= 1 in ClientManagementService | 3 (PASS) |
| `employmentType` in DeskAgentResponse | 1 (PASS) |
| `pendingPtoCount` in DeskAgentResponse | 1 (PASS) |
| `pendingPtoDates` in DeskAgentResponse | 1 (PASS) |
| `pendingByAgent` in DeskAgentService | 6 (PASS) |
| `@Query` / bulk PTO method in AgentDayOffRepository | 5 (PASS) |
| ClientManagementServiceNonSchedulableTest exists | PASS |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Java type inference issue in test workbook helpers**
- **Found during:** Task 1 GREEN implementation + test run
- **Issue:** `List.of(new String[]{...})` caused Java type inference failure: `inference variable E has incompatible bounds` when passing `String[]` elements as varargs. The `List.of(E... elements)` form could not resolve `E` as `String[]`.
- **Fix:** Rewrote test workbook helper signatures from `legacyWorkbook(List<String[]> dataRows)` to explicit single/two-row factories `legacyWorkbook(String bamboohrId, String name, String email, String desk)` — cleaner API, no inference ambiguity.
- **Files modified:** DeskAssignmentUploadLegacyShapeTest.java, DeskAssignmentUploadEnrichedShapeTest.java, DeskAssignmentUploadNonSchedulableRejectTest.java
- **Commit:** 79126bd (incorporated into GREEN implementation commit)

## TDD Gate Compliance

- Task 1 RED gate: `test(05-04)` commit `b91fd16` — DeskAssignmentUploadLegacyShapeTest + DeskAssignmentUploadEnrichedShapeTest + DeskAssignmentUploadNonSchedulableRejectTest (11 tests, compile-error RED)
- Task 1 GREEN gate: `feat(05-04)` commit `79126bd` — DeskAssignmentUploadService implementation + fixed tests (11 tests green)
- Task 2 RED gate: `test(05-04)` commit `eb40d1c` — ClientManagementServiceNonSchedulableTest (4 tests, compile-error RED)
- Task 2 GREEN gate: `feat(05-04)` commit `27d5ca3` — ClientManagementService + DeskAgentResponse + DeskAgentService + AgentDayOffRepository (4 tests green, full suite green)

## Known Stubs

None — all fields are wired to real data sources. `DeskAgentResponse.employmentType` maps from `Agent.employmentType` (populated in Plan 01). `pendingPtoDates` is fetched from `AgentDayOff` with `type=PTO, status=REQUESTED, date >= today`.

## Threat Flags

No new threat surface beyond the plan's threat model (T-05-04-01 through T-05-04-07). All mitigations applied:
- T-05-04-02: Headers trimmed + lowercased before Map key assignment; error message echoes lowercase keys only
- T-05-04-03: Bulk PTO query scoped by `deskId`; `deskId` is already tenant-verified upstream in `listDeskAgentResponses` before the query is called
- T-05-04-05: Non-XLSX files throw `POIXMLException` / `InvalidFormatException` — handled by existing `GlobalExceptionHandler`
- T-05-04-06: Both upload path (`DeskAssignmentUploadService`) and manual-assign path (`ClientManagementService`) gate on `AgentEligibilityService.isNonSchedulable`; tests assert both

## Self-Check: PASSED

Files exist:
- src/main/java/com/wfm/dto/SkippedRow.java ✓
- src/main/java/com/wfm/service/DeskAssignmentUploadService.java ✓
- src/main/java/com/wfm/service/ClientManagementService.java ✓
- src/main/java/com/wfm/service/DeskAgentService.java ✓
- src/main/java/com/wfm/dto/DeskAgentResponse.java ✓
- src/main/java/com/wfm/repository/AgentDayOffRepository.java ✓
- src/test/java/com/wfm/service/DeskAssignmentUploadLegacyShapeTest.java ✓
- src/test/java/com/wfm/service/DeskAssignmentUploadEnrichedShapeTest.java ✓
- src/test/java/com/wfm/service/DeskAssignmentUploadNonSchedulableRejectTest.java ✓
- src/test/java/com/wfm/service/ClientManagementServiceNonSchedulableTest.java ✓

Commits exist: b91fd16, 79126bd, eb40d1c, 27d5ca3 ✓
