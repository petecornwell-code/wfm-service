# WFM Service — Ordered Implementation Tasks

Tasks ordered by dependency: foundations first, then consumers.
Each task builds on completed predecessors.

---

## Phase 1: Foundation (Infrastructure & Model Fixes) ✅ DONE

### Task 1 — Global Exception Handler
Create `GlobalExceptionHandler.java` (`@RestControllerAdvice`) using the existing `ErrorResponse.java` DTO.
Map: `IllegalArgumentException` → 400, entity-not-found → 404, conflict → 409, unprocessable → 422, uncaught → 500.
Add custom exceptions: `EntityNotFoundException`, `ConflictException`, `RefreshInProgressException`.
**Files:** new `GlobalExceptionHandler.java`, new exception classes, existing `ErrorResponse.java`
**Depends on:** nothing
**Ref:** TODO §1.1

### Task 2 — HardSoftScore JSON Serializer
Create a custom Jackson serializer/deserializer for `HardSoftScore` that produces `{ "hardScore": 1, "softScore": 0 }` instead of Timefold's default `"1hard/0soft"` string.
Register via a Jackson module `@Bean`.
**Files:** new `HardSoftScoreSerializer.java`, new `HardSoftScoreDeserializer.java`, new `JacksonConfig.java` (or add to existing config)
**Depends on:** nothing
**Depended on by:** nothing directly — this is a defensive safety net. All controllers use DTOs with plain `ScoreDto` fields, so Jackson never serializes `HardSoftScore` in normal operation. The serializer protects against accidental raw entity leaks.
**Ref:** TODO §1.5

### Task 3 — BigDecimal Normalization Utility
Create a utility method `BigDecimals.normalize(BigDecimal value)` that returns `value.setScale(2, RoundingMode.HALF_UP)`.
Audit and fix all BigDecimal `.equals()` usages → `.compareTo() == 0`.
**Files:** new `BigDecimals.java` utility class
**Depends on:** nothing
**Ref:** TODO §1.6

### Task 4 — Request & Response DTOs
Create missing DTOs, fix existing ones:
- Create: `DeskRequest`, `DeskResponse`, `AgentResponse`, `AgentDayOffResponse`, `SpecializationResponse`, `TimeslotResponse`, `PreferenceResponse`, `ExceptionResponse`, `AssignAgentsRequest`, `SetSpecializationsRequest`, `SetContractedHoursRequest`, `GenerateTimeslotsRequest`
- Fix: `StaffingRequirementRequest.items` → `requirements`, `ErlangXRequest.items` → `parameters`
- Add typed output view sub-DTOs inside `ScheduleDetailResponse` (StaffingSummaryEntry, AgentScheduleEntry, PreferenceReportEntry, ConstraintViolationEntry, etc.)
**Files:** `dto/` package — multiple new and modified files
**Depends on:** nothing (DTOs use their own `ScoreDto` records, not `HardSoftScore` directly)
**Ref:** TODO §1.2, §1.3

### Task 5 — Model Fixes
- `Schedule.score`: remove `insertable = false, updatable = false`
- `AgentAssignment.deskAgent`: add `nullable = false` to `@JoinColumn`
**Files:** `Schedule.java`, `AgentAssignment.java`
**Depends on:** nothing
**Ref:** TODO §17

### Task 6 — Cursor-Based Pagination Utility
Implement generic keyset pagination: Base64 JSON cursor encoding/decoding, `WHERE` clause builder for keyset conditions.
Create a reusable `CursorPagination` utility or extend `PaginatedResponse`.
**Files:** new `CursorPagination.java` utility, modify `PaginatedResponse.java`
**Depends on:** nothing
**Ref:** TODO §1.4

---

## Phase 2: Core CRUD Services (Bottom-Up) ✅ DONE

### Task 7 — DeskService: Complete CRUD ✅
- `createDesk()`: add unique name validation, default `defaultContractedHoursPerDay` to 8.0, normalize BigDecimal
- `getDesk()`: throw 404 instead of returning null
- `updateDesk()`: implement partial update, unique name validation
- `deleteDesk()`: check accepted schedules (409), cascade-delete all desk data, remove in-memory schedule
- Wire `DeskController` to use `DeskRequest`/`DeskResponse` DTOs
**Files:** `DeskService.java`, `DeskController.java`
**Depends on:** Task 1 (exception handler), Task 3 (BigDecimal normalization), Task 4 (DTOs)
**Ref:** TODO §2

### Task 8 — SpecializationService: Complete CRUD ✅
- `createSpecialization()`: add unique name validation per desk
- `updateSpecialization()`: implement rename with unique name validation
- `deleteSpecialization()`: check references by desk-agents and staffing requirements (409)
- Wire `SpecializationController` to use `SpecializationResponse` DTO
**Files:** `SpecializationService.java`, `SpecializationController.java`
**Depends on:** Task 1 (exception handler), Task 4 (DTOs)
**Ref:** TODO §6

### Task 9 — AgentService: Implement Listing + DTO Wiring ✅
- `listAgents()`: query by tenantId, search filter (case-insensitive name), `unassigned=true` filter, cursor pagination
- Wire `AgentController.listAgents()` to return `PaginatedResponse<AgentResponse>`
- Wire `AgentController.getAgent()` to return `AgentResponse` instead of raw `Agent` (currently leaks `bamboohrId`)
**Files:** `AgentService.java`, `AgentController.java`, `AgentRepository.java`
**Depends on:** Task 4 (DTOs), Task 6 (pagination)
**Ref:** TODO §4

### Task 10 — DeskAgentService: Complete All Methods ✅ (with deferred items)
- ✅ `assignAgents()`: validate agents exist/active, single-desk check, all-or-nothing, return `DeskAgentResponse[]`
- ✅ `removeDeskAgent()`: cascade-delete preferences + exceptions, 404 if not found
  - **Deferred:** InMemoryScheduleStore non-accepted schedule check (409) → add in Phase 4 when solver exists
- ✅ `setSpecializations()`: validate specializations belong to desk, return `DeskAgentResponse`
  - **Deferred:** "primary not in secondary" validation → low priority, frontend can enforce
- ✅ `setContractedHours()`: normalize BigDecimal, return `DeskAgentResponse`
- ✅ `listDeskAgents`/`listDeskAgentResponses`: returns all with eager-loaded agent + specializations via `@EntityGraph`
  - **Deferred:** search/cursor pagination → desk agent counts typically <50, all-in-one-page is acceptable
- ✅ Wire `DeskAgentController`: use typed DTOs for assign, specializations, contracted-hours endpoints
**Files:** `DeskAgentService.java`, `DeskAgentController.java`
**Depends on:** Task 1, Task 3, Task 4, Task 6
**Ref:** TODO §3

### Task 11 — AgentDayOffService: Complete Methods ✅
- `listDaysOffForAgent()`: implement `from`/`to` date range filtering
- `listAllDaysOff()`: query all days off for tenant, pagination, date range, enriched format
- Wire `AgentDayOffController` to use `AgentDayOffResponse` and `PaginatedResponse`
**Files:** `AgentDayOffService.java`, `AgentDayOffController.java`, `AgentDayOffRepository.java`
**Depends on:** Task 4, Task 6
**Ref:** TODO §5

### Task 12 — ConstraintWeightsService: Implement Update ✅
- `updateWeights()`: load existing (or defaults), apply partial update (omitted fields keep current values), save
- Wire `ConstraintWeightsController` to use `ConstraintWeightsDto` instead of raw entity
- Convert between JPA entity (`HardSoftScore` fields) and DTO (`ScoreDto` fields) in service layer
**Files:** `ConstraintWeightsService.java`, `ConstraintWeightsController.java`
**Depends on:** Task 4 (ConstraintWeightsDto). Note: does NOT need Task 2 — the controller uses `ConstraintWeightsDto` (with plain `ScoreDto`), and the JPA entity uses `HardSoftScoreConverter` for DB persistence, so Jackson never serializes `HardSoftScore` on this path.
**Ref:** TODO §9

### Task 13 — AgentPreferenceService: Complete Methods ✅
- `savePreferences()`: set tenantId/deskId, standing replacement logic, derive dayOfWeek from date, save, return full list
- `deletePreference()`: validate ownership, delete, 404
- `listPreferences()`: date range filtering (all standing + weekly in range)
- Wire controller to use `PreferenceResponse` DTO
**Files:** `AgentPreferenceService.java`, `DeskAgentController.java`
**Depends on:** Task 1, Task 4
**Ref:** TODO §7

### Task 14 — AgentExceptionService: Complete Methods ✅
- `saveExceptions()`: validate no conflict with days off (409 Conflict — changed from original 400; 409 is correct since it conflicts with existing server state), upsert by (desk, agent, date), normalize BigDecimal, validate reason
- `deleteException()`: delete by desk+agent+date, 404
- `listExceptions()`: date range filtering
- Wire controller to use `ExceptionResponse` DTO
**Files:** `AgentExceptionService.java`, `DeskAgentController.java`
**Depends on:** Task 1, Task 3, Task 4
**Ref:** TODO §8

---

## Phase 3: Staffing & Timeslots ✅ DONE

### Task 15 — TimeslotController: Add Typed DTO and Accepted Schedule Check ✅
- ✅ Replace `Map<String, Object>` with `GenerateTimeslotsRequest` in generate endpoint
- ✅ Add 409 Conflict check for accepted schedules on delete endpoint
- ✅ Use `TimeslotResponse` DTO for all responses (list, generate, delete)
- ✅ `GET .../timeslots` also returns raw `Timeslot` entity — use `TimeslotResponse`
**Files:** `TimeslotController.java`, `TimeslotGeneratorService.java`
**Depends on:** Task 1, Task 4
**Ref:** TODO §10

### Task 16 — ErlangXService: Implement Algorithm ✅
Full Erlang X (Extended Erlang C) implementation:
1. ✅ Erlang C baseline via Jagerman formula
2. ✅ Abandonment probability (exponential patience model)
3. ✅ Retrial-adjusted load
4. ✅ Convergence loop (max 100 iterations)
5. ✅ Return smallest integer meeting service level
**Files:** `ErlangXService.java`
**Depends on:** nothing
**Ref:** TODO §12

### Task 17 — StaffingRequirementService: Complete All Methods ✅
- ✅ `listRequirements()`: cursor-based pagination with 4-part sort key (date, startTime, specName, id), JOIN FETCH for eager loading, date range filter
- ✅ `saveRequirements()`: validate timeslots/specializations exist and belong to desk, validate uniqueness of timeslot+spec combos, derive date range from payload, delete+insert in single tx
- ✅ `calculateErlangX()`: parse ErlangXRequest, call ErlangXService per item, persist with ERLANG_X source, delete+insert for from/to range
- ✅ Wire `StaffingRequirementController`: typed DTOs for all 3 endpoints
- **Spec change:** §7.10 POST save: updated from 400 to 404 for missing timeslot/specialization references (consistent with EntityNotFoundException pattern). Added 400 for duplicate timeslot+spec combos.
- **Known limitation:** JOIN FETCH + Pageable triggers Hibernate in-memory pagination on first page (no cursor). Acceptable for bounded data sets.
**Files:** `StaffingRequirementService.java`, `StaffingRequirementController.java`, `StaffingRequirementRepository.java`
**Depends on:** Task 4 (DTOs), Task 6 (pagination), Task 16 (ErlangXService)
**Ref:** TODO §11

### Codebase Review Fixes (Post-Phase 3)
The following cross-cutting fixes were identified and applied during a comprehensive codebase review:
- ✅ **BambooRefreshService:** immutable `List.of()` → mutable `ArrayList` for JPA-managed collection; delete-before-insert for days-off; case-insensitive type comparison
- ✅ **DeskService.deleteDesk:** expanded cascade to delete ALL desk-scoped data (agent assignments, all timeslots/requirements, schedule records) in FK-safe order, not just live data
- ✅ **AgentPreferenceService:** weekly preference upsert by (tenant, desk, agent, date, isStanding=false) to prevent duplicate records
- ✅ **DeskAgentRepository:** added `@EntityGraph` to `findByTenantIdAndDeskIdAndAgent_Id` to prevent N+1 lazy loading
- ✅ **AgentDayOffRepository/Service:** added `OrderByDateAsc` sort to per-agent query methods for deterministic results
- ✅ **Spec updated:** §7.1 DELETE desk now documents FK-safe cascade delete order

---

## Phase 4: Solver ✅ DONE

### Task 18 — Pre-Solve Validation (12 Checks) ✅
Implement all validation checks from spec §7.11 in `SolverService.startSolve()`:
Period length, timeslots exist, increment/time match, specializations assigned, contracted hours divisible, staffing requirements exist, active agents available, break duration valid, break alignment conformance, coverage window check, exception/day-off conflict, specialization coverage.
Return all failures in `ErrorResponse.details[]`.
**Files:** `SolverService.java`
**Depends on:** Task 1 (exception handler for structured validation errors)
**Ref:** TODO §13 (SolverService.startSolve)

### Task 19 — Pre-Solve Data Loading & Entity Preparation ✅
Load all problem facts from database: desk-agents, specializations, timeslots, staffing requirements, preferences, days off, exceptions, constraint weights.
Expand staffing requirements into `AgentAssignment` planning entities.
Unwrap Hibernate proxy collections into plain `ArrayList`/`HashSet`.
Ensure all entities are detached from persistence context.
**Files:** `SolverService.java`
**Depends on:** Task 18 (validation completes first)
**Ref:** TODO §1.7, §13

### Task 20 — Solver Lifecycle (Start/Stop/Callbacks) ✅
- Check no existing non-accepted schedule for this desk (409 Conflict via `InMemoryScheduleStore.hasDeskSchedule`)
- Inject Timefold `SolverManager`, start solver asynchronously
- Propagate tenant context to solver thread via `ThreadLocal` wrapper
- Handle completion callback (status → COMPLETED, capture score)
- Handle failure callback (status → FAILED, capture error message)
- `stopSolve()`: validate RUNNING status, terminate solver, status → STOPPED
- Set `createdAt` timestamp
**Files:** `SolverService.java`
**Depends on:** Task 19 (data loading)
**Ref:** TODO §13

### Task 21 — Wire ScheduleController to DTOs ✅
- `startSolve()`: accept `SolveRequest` instead of `Schedule`, return `ScheduleSummary` (202)
- `listSchedules()`: return `List<ScheduleSummary>` (pagination deferred to Task 23)
- `getScheduleDetail()`: return raw `Schedule` (full output views deferred to Task 24)
- `stopSolve()`: return `ScheduleSummary`
**Files:** `ScheduleController.java`
**Depends on:** Task 4 (DTOs), Task 20 (solver works)
**Ref:** TODO §14

### Task 22 — Implement All 15 Solver Constraints ✅
Replace all stub constraints with real logic in `ScheduleConstraintProvider`:

**Hard (10):** Agent day off, Specialization match, One assignment per timeslot, Exactly one break, Break duration, Break blocked window, Break start alignment, Contracted hours, Bulk over-allocation limit, Bulk under-allocation hard.

**Soft (5):** Prefer primary specialization, Honour preferred start time, Honour preferred break time, Break clustering, Bulk under-allocation soft.
**Files:** `ScheduleConstraintProvider.java`
**Depends on:** Task 19 (entity model is correct for solver)
**Ref:** TODO §13

**Implementation notes:**
- Created `ScheduleConfig` record as `@ProblemFactProperty` on Schedule to expose config to constraints
- Made `AgentAssignment.deskAgent` `nullable = true` for Timefold construction heuristic (spec updated to match)
- Bulk allocation constraints (13, 14, 15) and break clustering (11) are no-op placeholders (penalize 0) — these are input-determined or require cross-agent aggregation deferred to Phase 5
- Created `ScheduleExportService` and `ScheduleOutputService` as stubs with TODO placeholders (Phase 5)

### Codebase Evaluation Fixes (Post-Phase 4)
Comprehensive review against spec identified and fixed the following issues:

**Critical Bug Fixes:**
- ✅ **Contracted hours constraint ignored AgentExceptions:** Created `AgentDayConfig` record as a per-agent-day problem fact that pre-computes effective contracted hours (accounting for exceptions). All break constraints and the contracted hours constraint now join `AgentDayConfig` instead of `ScheduleConfig`, ensuring exception overrides are respected.
- ✅ **Preference resolution not implemented:** Added `resolvePreferences()` to SolverService — resolves weekly vs. standing preferences per agent-day per spec §5.8. Solver now receives only effective preferences with exact dates, eliminating double-counting.
- ✅ **defaultContractedHoursPerDay didn't inherit from Desk:** SolverService now loads the `Desk` entity and inherits `Desk.defaultContractedHoursPerDay` when the solve request omits the field (spec §5.12).
- ✅ **StopSolve race condition:** Reordered `stopSolve()` to set `STOPPED` status before calling `terminateEarly()`. The finalBestSolution callback now checks if status is still `RUNNING` before setting `COMPLETED`, preventing the race from overwriting `STOPPED`.
- ✅ **ContractedHours groupBy used DeskAgent object reference:** Changed to `deskAgent.getId()` (UUID) for consistent and reliable grouping.
- ✅ **Desk existence not validated before solve:** SolverService now loads and validates the desk exists (404 if not).

**New files:**
- `model/AgentDayConfig.java` — per-agent-day problem fact (deskAgentId, date, effectiveHours, break config)

**Modified files:**
- `model/Schedule.java` — added `agentDayConfigs` as `@ProblemFactCollectionProperty`
- `service/SolverService.java` — added DeskRepository, preference resolution, AgentDayConfig computation, race condition fix
- `solver/ScheduleConstraintProvider.java` — break/contracted-hours constraints use AgentDayConfig instead of ScheduleConfig

**Remaining known issues (Phase 5+):**
- Break clustering constraint is a no-op placeholder (requires cross-agent per-timeslot aggregation)
- Bulk allocation constraints are no-op placeholders (supply-demand ratio is input-determined, not solver-controlled)
- listSchedules returns flat list, not paginated response (Task 23)
- getScheduleDetail returns raw entity, not full output views (Task 24)

---

## Phase 5: Schedule Management ✅ DONE

### Task 23 — ScheduleService.listSchedules ✅
Merge in-memory schedule (if exists for desk) with database accepted schedules.
Apply cursor-based pagination. Return `ScheduleSummary` format.
**Files:** `ScheduleService.java`
**Depends on:** Task 6 (pagination), Task 21 (DTOs)
**Ref:** TODO §14

### Task 24 — ScheduleService.getScheduleDetail ✅
Implement `?date` query parameter filter. Call `ScheduleOutputService` for all four output views.
Add tenant/desk validation on in-memory path.
**Files:** `ScheduleService.java`
**Depends on:** Task 25 (output views)
**Ref:** TODO §14

### Task 25 — Schedule Output Views (4 views) ✅
Implement all four `ScheduleOutputService` methods:
1. `buildStaffingSummary()`: per-day per-specialization predicted vs actual hours, coverage %, totals
2. `buildAgentSchedule()`: per-agent per-day shifts, assignments with matchType, breaks
3. `buildPreferenceReport()`: per-agent per-day preference resolution, honoured flags, summary counters
4. `buildConstraintViolations()`: extract from Timefold score explanation, group by constraint
**Files:** `ScheduleOutputService.java`
**Depends on:** Task 22 (constraints produce meaningful violations)
**Ref:** TODO §15

### Task 26 — ScheduleService.acceptSchedule (MOST COMPLEX) ✅
1. Validate status COMPLETED/STOPPED (409 otherwise)
2. Snapshot live timeslots → new IDs with schedule_id
3. Snapshot staffing requirements → remap to snapshot timeslots
4. Write agent assignments → remap to snapshot timeslots
5. Delete overlapping accepted schedules for same desk/date range
6. Persist everything in single transaction
7. Set status ACCEPTED, remove from in-memory store
**Files:** `ScheduleService.java`, `ScheduleRepository.java`, `TimeslotRepository.java`, `StaffingRequirementRepository.java`, `AgentAssignmentRepository.java`
**Depends on:** Task 5 (Schedule.score column fix), Task 20 (solver produces results to accept)
**Ref:** TODO §14

### Task 27 — ScheduleService.rejectSchedule ✅
Add status validation (COMPLETED/STOPPED/FAILED only, 409 if RUNNING).
**Files:** `ScheduleService.java`
**Depends on:** Task 1 (exception handler)
**Ref:** TODO §14

### Task 28 — Schedule Excel Export ✅
Implement `ScheduleExportService.exportToExcel()` using Apache POI XSSFWorkbook:
- Tab 1: Staffing Summary
- Tab 2: Agent Schedule
- Tab 3: Preference Report
**Files:** `ScheduleExportService.java`
**Depends on:** Task 25 (output views provide the data)
**Ref:** TODO §15

### Architect Review Fixes (Post-Phase 5)
Comprehensive review identified and fixed the following issues:

**Critical Bug Fixes:**
- ✅ **Tenant isolation missing for in-memory schedules:** `getScheduleDetail`, `acceptSchedule`, and `rejectSchedule` now validate `tenantId` and `deskId` when accessing schedules from the in-memory store. Previously, cross-tenant access was possible.
- ✅ **`startTimeHonoured` used exact match instead of `>=`:** Spec §8.3 says `true` if `actualStartTime >= preferredStartTime`. Changed from `!isBefore && !isAfter` (equals) to `!isBefore` (greater-or-equal).
- ✅ **`breakTimeHonoured` used exact match instead of overlap:** Spec §8.3 says `true` if the agent's break overlaps the preferred break timeslot. Changed from `actBreak.equals(prefBreak)` to proper time-range overlap check.
- ✅ **`actualBreakTime` always used first break instead of closest:** Spec §8.3 says "start of actual break closest to the preferred time." Changed from `breaks.get(0)` to finding the break with minimum distance to preferred time.
- ✅ **SolverFactory created per request:** `buildConstraintViolations()` created a new `SolverFactory` on every call — an extremely expensive operation. Now injects the Spring-managed `SolverFactory<Schedule>` and creates `SolutionManager` once in the constructor.
- ✅ **Cursor pagination broken in `listSchedules`:** The `cursor` parameter was accepted but never applied. Now decodes the cursor, finds the last-seen ID, and skips past it.
- ✅ **`ConstraintViolationEntry` missing spec fields:** Added `violationCount` (int) and `totalPenalty` (ScoreDto) per spec §8.4.
- ✅ **`ViolationDetail` missing structured IDs:** Added `agentId` (UUID), `timeslotId` (UUID), and `timeslotLabel` (String) fields.

**Major Fixes:**
- ✅ **`BreakDetail` missing `durationMinutes`:** Added per spec §8.2.
- ✅ **Staffing summary missing totals:** Added per-day "TOTAL" rows and a "GRAND TOTAL" row per spec §8.1.
- ✅ **Constraint violations for accepted schedules:** Added early-return when `constraintWeights == null` (accepted schedules lack solver problem facts).
- ✅ **N+1 lazy loading for DB-loaded assignments:** Added `findWithRelationsByTenantIdAndDeskIdAndScheduleId()` with JOIN FETCH for timeslot, requiredSpecialization, deskAgent, agent, and primarySpecialization.
- ✅ **Accept snapshot inserts unbatched:** Changed from individual `save()` calls to `saveAll()` for timeslots, staffing requirements, and agent assignments.
- ✅ **`dateFilter` parsing unhandled:** Added `DateTimeParseException` catch that converts to 400 Bad Request.
- ✅ **Date filter excludes total rows:** Staffing summary total rows with `date == null` are now correctly filtered out when date filter is applied.

**Modified files:**
- `dto/ScheduleDetailResponse.java` — `BreakDetail` +durationMinutes, `ConstraintViolationEntry` +violationCount +totalPenalty, `ViolationDetail` +agentId +timeslotId +timeslotLabel
- `service/ScheduleOutputService.java` — inject SolverFactory, fix startTimeHonoured/breakTimeHonoured/actualBreakTime logic, add staffing totals, add durationMinutes to breaks, fix constraint violations for accepted schedules
- `service/ScheduleService.java` — tenant validation, cursor pagination, saveAll batching, dateFilter exception handling
- `repository/AgentAssignmentRepository.java` — add JOIN FETCH query for snapshot loading
- `controller/ScheduleController.java` — `listSchedules` returns `PaginatedResponse<ScheduleSummary>`, `getScheduleDetail` returns `ScheduleDetailResponse`, `exportToExcel` uses `ScheduleDetailResponse`
- `service/ScheduleExportService.java` — 3-tab Excel export from `ScheduleDetailResponse` (staffing summary, agent schedule, preference report)

**Remaining known limitations:**
- Preference report is empty for accepted (DB) schedules — resolved preferences are not snapshotted during accept. Could be addressed by snapshotting preferences or re-resolving from current DB state.
- Derived delete methods (`deleteByTenantIdAndDeskIdAndScheduleId`) use SELECT+individual DELETE pattern. Could be converted to bulk `@Modifying @Query` for large schedules.
- `toSummary(Schedule)` helper is duplicated in ScheduleController and ScheduleService.

---

## Phase 6: BambooHR Improvements ✅ DONE

### Task 29 — BambooRefreshService Fixes ✅
1. ✅ Soft-delete removed employees (mark `active = false`) — agents assigned to the desk but no longer in BambooHR response are marked `active = false`
2. ~~Day-off upsert by (agent, date) to avoid unique constraint violations~~ ✅ Done in Phase 3 review: delete-before-insert for days-off in lookahead window
3. ~~Stale day-off deletion for refreshed agents~~ ✅ Done in Phase 3 review: deletes existing days-off per agent before re-inserting
4. ~~Case-insensitive type mapping (`"holiday"`, `"mandatory"` → MANDATORY)~~ ✅ Done in Phase 3 review: equalsIgnoreCase for both type values
5. ✅ Move API calls before `@Transactional` boundary — split `refreshDeskAgents()` into non-transactional public method (API calls) and `@Transactional` protected `persistRefreshData()` method
6. ✅ Case-insensitive desk name matching — `MockBambooHRClient.listEmployees()` now uses `equalsIgnoreCase` for project matching
7. ✅ Cross-desk conflict logging (warn + skip) — when an agent is already assigned to a different desk, logs a warning and skips the desk assignment
8. ~~Throw custom exception for concurrency guard → 409 `REFRESH_IN_PROGRESS`~~ ✅ Done in Phase 2 review: now throws `RefreshInProgressException` (409). Also changed desk-not-found from `IllegalArgumentException` to `EntityNotFoundException` (404).
**Files:** `BambooRefreshService.java`
**Depends on:** Task 1 (exception handler for 409 mapping)
**Ref:** TODO §16

### Task 30 — MockBambooHRClient: Add Test Day-Off Data ✅
✅ `listTimeOff()` now generates realistic test day-off data for the first 5 mock employees:
- Employee 1: mandatory holiday on first Monday of each month
- Employee 2: PTO every other Friday
- Employee 3: one-week PTO block starting 2 weeks from 'from'
- Employee 4: mandatory holiday on the 15th of each month
- Employee 5: PTO on the last Friday of each month
**Files:** `MockBambooHRClient.java`
**Depends on:** nothing
**Ref:** TODO §16

**Note:** `HttpBambooHRClient` (TODO §16) is intentionally excluded — all three methods throw `UnsupportedOperationException` and should be implemented when real BambooHR credentials are available.

---

## Phase 7: Frontend ✅ DONE

### Task 31 — Frontend Error Handling Infrastructure ✅
- ✅ Parse full `ErrorResponse` (code, message, details[]) in API client — new `ApiRequestError` class with `status`, `code`, `details` fields
- ✅ Replace `console.error` with user-visible error toasts — new `Toast` component with `showToast()` global function, `ToastContainer` in App root
- ✅ Add loading states to all pages — all data fetches show loading indicators
- ✅ `getErrorMessage()` utility extracts user-friendly messages from any error type
**Files:** `api/client.ts`, `components/Toast.tsx`, all page components
**Depends on:** Task 1 (backend error responses)
**Ref:** TODO §18.2

### Task 32 — Desk Agents: Functional Buttons ✅
- ✅ Assign button: modal with unassigned agents (`GET /agents?unassigned=true`), multi-select with search, `POST /desks/{deskId}/agents`
- ✅ Remove button per row: confirmation dialog, `DELETE /desks/{deskId}/agents/{agentId}`
- ✅ Edit specializations: inline dropdowns for primary + checkboxes for secondary, `PUT .../specializations`
- ✅ Edit contracted hours: inline input with save/cancel, `PUT .../contracted-hours`
- ✅ Active/inactive filter (default active only)
- ✅ Days off view per agent — modal showing days off from `GET /agents/{agentId}/days-off`
- ✅ Added columns: job title, last refreshed
**Files:** `pages/DeskAgents.tsx`
**Depends on:** Task 10 (backend endpoints work)
**Ref:** TODO §18.1, §18.2

### Task 33 — Staffing Requirements: Full Implementation ✅
- ✅ Pre-populate demand grid from `GET /desks/{deskId}/staffing-requirements`
- ✅ Controlled inputs with state tracking
- ✅ Save button → `POST /desks/{deskId}/staffing-requirements`
- ✅ Erlang X mode with parameter inputs (call volume per timeslot/spec, calculated results display)
- ✅ Copy day button (copies one day's demand to all other days)
**Files:** `pages/StaffingRequirements.tsx`
**Depends on:** Task 17 (backend endpoints work)
**Ref:** TODO §18.1, §18.2

### Task 34 — Agent Preferences: Editable ✅
- ✅ Agent name displayed (resolved from desk agents list, not raw UUID)
- ✅ Date range picker (from/to filters)
- ✅ Add preference form with time pickers for start and break
- ✅ Standing checkbox per row
- ✅ Save button → `PUT .../preferences` (saves all preferences)
- ✅ Delete updates local state and calls backend
- ✅ Back to Agents link
**Files:** `pages/AgentPreferences.tsx`
**Depends on:** Task 13 (backend endpoints work)
**Ref:** TODO §18.1

### Task 35 — Agent Exceptions: Editable ✅
- ✅ Agent name displayed (resolved from desk agents list)
- ✅ Date range picker (from/to filters)
- ✅ Add exception form with numeric override hours and reason input
- ✅ Reason text input (required when override set)
- ✅ Standard hours column showing agent's contracted hours
- ✅ Day-off awareness (greyed-out rows for dates with days off)
- ✅ Save button → `PUT .../exceptions`
**Files:** `pages/AgentExceptions.tsx`
**Depends on:** Task 14 (backend endpoints work)
**Ref:** TODO §18.1

### Task 36 — Desk Management: Edit & Polish ✅
- ✅ Edit desk form (inline editing in table) → `PUT /desks/{deskId}`
- ✅ `defaultContractedHoursPerDay` field in create form
- ✅ Loading state while fetching desks
- ✅ Toast notifications for create/update/delete
**Files:** `pages/DeskManagement.tsx`
**Depends on:** Task 7 (backend endpoints work)
**Ref:** TODO §18.1, §18.3

### Task 37 — Specializations: Rename & Delete Guards ✅
- ✅ Rename capability → `PUT .../specializations/{id}` (inline rename with Save/Cancel)
- ✅ Confirmation dialog for delete warning about in-use specializations
- ✅ Toast notifications for all operations
**Files:** `pages/Specializations.tsx`
**Depends on:** Task 8 (backend endpoints work)
**Ref:** TODO §18.3

### Task 38 — Constraint Weights: Polish ✅
- ✅ Description column for each constraint
- ✅ Level badge (Hard/Soft) with colour coding
- ✅ Reset to defaults button
- ✅ Toast notifications for save
**Files:** `pages/ConstraintWeightsPage.tsx`
**Depends on:** Task 12 (backend endpoints work)
**Ref:** TODO §18.2, §18.3

### Task 39 — Schedule Setup: Pre-Solve Enhancements ✅
- ✅ Validation summary panel (active agent count, specialization count, staffing requirement count with warnings)
- ✅ Past schedules list (`GET /desks/{deskId}/schedules`) with status badges, scores, links to view
- ✅ Break duration filtered to multiples of increment (dropdown)
- ✅ Toast + inline error display for solve failures
**Files:** `pages/ScheduleSetup.tsx`
**Depends on:** Task 23 (listSchedules works)
**Ref:** TODO §18.2, §18.3

### Task 40 — Schedule Results: Output View Tabs ✅
Implemented all four tab contents with typed TypeScript interfaces:
1. ✅ Staffing Summary table — colour-coded coverage %, delta with +/- colouring, total/grand-total rows, date filter
2. ✅ Agent Schedule grid — grouped by agent, per-day shifts with match type colour coding (PRIMARY=green, SECONDARY=yellow, NONE=red), breaks with duration, legend
3. ✅ Preference Report table — honoured flags (Yes/No colour coded), summary counters (total, start honoured, break honoured, overall %), preference source, date filter
4. ✅ Constraint Violations table — expandable per-constraint detail rows, filter by level (All/Hard/Soft), violation count, total penalty
- ✅ Non-optimal banner with violated hard constraints list
- ✅ Progress indicator (spinner animation) while solver is running
- ✅ Proper accept confirmation dialog for non-optimal solutions
- ✅ Export error handling with toast notifications
**Files:** `pages/ScheduleResults.tsx`, `api/client.ts` (typed interfaces for all output views)
**Depends on:** Task 25 (backend output views), Task 21 (ScheduleDetailResponse)
**Ref:** TODO §18.1, §18.2, §18.3

### Task 41 — Navigation & UI Polish ✅
- ✅ Sidebar: added Desk Management link
- ✅ Active desk indicator in header (desk name loaded from API, replaces "WFM Service")
- ✅ Active nav link highlighting based on current route
- ✅ Hardcoded tenant ID → configurable via input in top bar (`TenantSelector` component)
- ✅ Export error handling (checks response.ok, shows toast on failure)
- ✅ Global CSS improvements: input/select/label styling, disabled button state, focus outlines, spinner/fade-in animations
**Files:** `App.tsx`, `index.css`, `components/Toast.tsx`
**Depends on:** nothing
**Ref:** TODO §18.2, §18.3

---

## Summary: Dependency Graph

```
Phase 1 (Tasks 1-6): Foundation — no inter-phase deps, all 6 parallelizable
  ↓
Phase 2 (Tasks 7-14): CRUD Services — depend on Phase 1 only
  ↓ (frontend tasks depend on these, but solver does NOT)
Phase 3 (Tasks 15-17): Staffing — depends on Phase 1 only (not Phase 2)
  ↓
Phase 4 (Tasks 18-22): Solver — depends on Phase 1 only (not Phase 2/3)
  ↓               ↘ (Task 22 branches from 19, parallel with 20)
Phase 5 (Tasks 23-28): Schedule Management — depends on Phase 4
  ↓
Phase 6 (Tasks 29-30): BambooHR — depends on Task 1 only (independent of Phases 2-5)
  ↓
Phase 7 (Tasks 31-41): Frontend — each task depends on its corresponding backend task
```

**Key insight:** Phases 2, 3, 4, and 6 all depend only on Phase 1 and can start in parallel once Phase 1 is done. Phase 5 is the only phase that requires Phase 4 completion. Frontend tasks (Phase 7) each wait only on their specific backend dependency.

**Critical path (longest chain):**
`1 → 18 → 19 → 22 → 25 → 24` (6 tasks, solver → output views → detail endpoint)

Alternate long chains:
- `1 → 18 → 19 → 20 → 26` (5 tasks, solver lifecycle → accept schedule)
- `1 → 18 → 19 → 20 → 21 → 40` (6 tasks, solver → DTO wiring → frontend results)

**Parallelizable within phases:**
- Phase 1: All 6 tasks can run in parallel
- Phase 2: Tasks 7-14 can mostly run in parallel (share Task 1/4 deps)
- Phase 3: Can run in parallel with Phases 2 and 4
- Phase 4: Task 22 (constraints) can run in parallel with Task 20 (lifecycle) — both branch from Task 19
- Phase 5: Tasks 23+27 can start immediately; Tasks 24, 26, 28 depend on specific Phase 4/5 predecessors
- Phase 6: Independent of Phases 2-5 — can start as soon as Task 1 is done
- Phase 7: Each frontend task can start as soon as its backend dependency completes
