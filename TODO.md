# WFM Service — Implementation TODO

Comprehensive gap analysis between `spec.md` and the current codebase.
Organized by layer, then by priority within each section.

---

## 1. Cross-Cutting / Infrastructure

### 1.1 Global Exception Handler ✅ DONE (Phase 1, Task 1)
- [x] Create `@RestControllerAdvice` that maps exceptions to the spec's standard error envelope (`ErrorResponse` with `code`, `message`, `details[]`).
- [x] Map `IllegalArgumentException` → 400 `VALIDATION_FAILED`
- [x] Map entity-not-found → 404 `NOT_FOUND`
- [x] Map conflict states → 409 `CONFLICT` / `REFRESH_IN_PROGRESS`
- [x] Map unprocessable → 422 `UNPROCESSABLE_ENTITY`
- [x] Map uncaught exceptions → 500 `INTERNAL_ERROR`
- **Files:** `GlobalExceptionHandler.java`, `EntityNotFoundException.java`, `ConflictException.java`, `RefreshInProgressException.java`, `UnprocessableException.java`

### 1.2 Response DTO Layer ✅ DONE (Phase 1, Task 4 + Phase 2 controller wiring)
- [x] `DeskResponse` — `{ id, name, description, defaultContractedHoursPerDay }`
- [x] `AgentResponse` — `{ id, name, email, department, jobTitle, active, lastRefreshedAt }` (no `bamboohrId`)
- [x] `AgentDayOffResponse` — `{ id, date, type }` (per-agent) and `{ id, agent: {id, name}, date, type }` (list-all)
- [x] `SpecializationResponse` — `{ id, name }` (no `tenantId`/`deskId`)
- [x] `TimeslotResponse` — `{ id, date, startTime, endTime }` (no `tenantId`/`deskId`/`scheduleId`)
- [x] `PreferenceResponse` — `{ id, dayOfWeek, date, isStanding, preferredStartTime, preferredBreakTime }`
- [x] `ExceptionResponse` — `{ id, date, contractedHoursOverride, reason }`
- [x] Strongly typed output view sub-DTOs for `ScheduleDetailResponse` (StaffingSummaryEntry, AgentScheduleEntry, PreferenceReportEntry, ConstraintViolationEntry, etc.)
- [x] Wire DTOs into Phase 2 controllers (Desk, Agent, DeskAgent, Specialization, AgentDayOff, ConstraintWeights, Preferences, Exceptions)
- [ ] Wire DTOs into Phase 3+ controllers (Timeslot, StaffingRequirement, Schedule)

### 1.3 Request DTO Layer ✅ DONE (Phase 1, Task 4)
- [x] Create `DeskRequest` for `POST/PUT /desks`
- [x] Create `AssignAgentsRequest` for `POST /desks/{deskId}/agents` — `{ agentIds: [uuid] }`
- [x] Create `SetSpecializationsRequest` — `{ primarySpecializationId, secondarySpecializationIds }`
- [x] Create `SetContractedHoursRequest` — `{ contractedHoursPerDay }`
- [x] Create `GenerateTimeslotsRequest` for `POST .../timeslots/generate`
- [x] Fix `StaffingRequirementRequest.items` → rename field to `requirements` (spec uses `requirements`)
- [x] Fix `ErlangXRequest.items` → rename field to `parameters` (spec uses `parameters`)

### 1.4 Cursor-Based Pagination (PARTIALLY DONE — Phase 1 Task 6 + Phase 2)
- [x] Implement Base64-encoded JSON cursor encoding/decoding (`CursorPagination.java`)
- [x] Implement `WHERE` clause keyset pagination (not OFFSET-based)
- [x] `GET /agents` — sort by (agent name asc, agent.id)
- [x] `GET /days-off` — sort by (date asc, agent_day_off.id), with `JOIN FETCH` to avoid N+1
- [ ] `GET /desks/{deskId}/agents` — search filter, cursor pagination (currently returns all, ignores params)
- [ ] `GET /desks/{deskId}/staffing-requirements` — sort by (date asc, startTime asc, specName asc, id)
- [ ] `GET /desks/{deskId}/schedules` — sort by (createdAt desc, schedule.id)

### 1.5 HardSoftScore Serialization ✅ DONE (Phase 1, Task 2)
- [x] Custom Jackson serializer/deserializer producing `{ "hardScore": 1, "softScore": 0 }`. Registered via `JacksonConfig` `@Bean`. Purely defensive — all controllers use `ConstraintWeightsDto`/`ScoreDto`.

### 1.6 BigDecimal Normalization ✅ DONE (Phase 1, Task 3)
- [x] Create `BigDecimals.normalize(BigDecimal)` utility — `setScale(2, RoundingMode.HALF_UP)`.
- [x] Applied in: `DeskService` (defaultContractedHoursPerDay), `DeskAgentService` (contractedHoursPerDay), `AgentExceptionService` (contractedHoursOverride).
- [ ] Apply in: `SolverService` (breakBlockedHours, breakMinShiftHours, defaultContractedHoursPerDay) — Phase 4.
- [ ] Audit all BigDecimal comparisons — replace any `.equals()` with `.compareTo() == 0`.

### 1.7 Pre-Solve Entity Preparation (NOT YET — Phase 4)
- [ ] **Hibernate proxy unwrapping** — Copy Hibernate proxy collections (`DeskAgent.secondarySpecializations`, etc.) into plain `ArrayList`/`HashSet` during pre-solve assembly. Timefold's best-solution cloning cannot clone Hibernate proxies.
- [ ] **Entity detachment** — Ensure all planning solution entities are detached from the JPA persistence context before handing to the solver. The `@Transactional(readOnly = true)` scope should close the persistence context after loading.
- [ ] **Tenant context propagation** — Capture `TenantContext.getTenantId()` on the request thread and set it on the solver thread in a `try/finally` block (ThreadLocal doesn't propagate to child threads).

---

## 2. Desk Management (Spec §7.1) ✅ DONE (Phase 2, Task 7)

### DeskService
- [x] **`updateDesk`** — Partial update (null = don't change), unique name validation if changed.
- [x] **`deleteDesk`** — Checks for accepted schedules (409), cascade-deletes all desk-scoped data in FK order (preferences → exceptions → staffing reqs → timeslots → desk-agents → specializations → constraint weights), removes in-memory schedule.
- [x] **`createDesk`** — Unique name validation per tenant. Defaults `defaultContractedHoursPerDay` to 8.00. Auto-creates `ConstraintWeights` for new desk.
- [x] **`getDesk`** — Throws `EntityNotFoundException` (404) instead of returning null.

### DeskController
- [x] Uses `DeskRequest`/`DeskResponse` DTOs instead of raw `Desk` entity.

---

## 3. Desk Agents (Spec §7.2) ✅ MOSTLY DONE (Phase 2, Task 10)

### DeskAgentService
- [x] **`assignAgents`** — Validates agents exist and are active, single-desk constraint, all-or-nothing semantics, returns `List<DeskAgentResponse>`.
- [x] **`removeDeskAgent`** — Cascade-deletes preferences + exceptions, 404 if not found.
  - **Deferred:** InMemoryScheduleStore non-accepted schedule check (409) — will add in Phase 4 when solver is implemented.
- [x] **`setSpecializations`** — Validates specializations belong to this desk, loads and updates DeskAgent, returns `DeskAgentResponse`.
  - **Deferred:** "primary not in secondary" validation — low priority, frontend can enforce.
- [x] **`setContractedHours`** — Normalizes BigDecimal to scale 2, returns `DeskAgentResponse`.
- [x] **`listDeskAgents` / `listDeskAgentResponses`** — Returns all desk agents with eager-loaded agent + specializations via `@EntityGraph`.
  - **Deferred:** Search filter and cursor pagination — desk agent counts are typically <50 per desk, so all-in-one-page is acceptable.

### DeskAgentController
- [x] `PUT .../specializations` — wired to `setSpecializations`.
- [x] `PUT .../contracted-hours` — wired to `setContractedHours`.
- [x] `POST .../agents` (assign) — returns `List<DeskAgentResponse>` with 201 status.

---

## 4. Agents — Tenant Level (Spec §7.3) ✅ DONE (Phase 2, Task 9)

### AgentService
- [x] **`listAgents`** — Query by tenantId, cursor-based pagination, search filter (case-insensitive substring on name), `unassigned=true` filter (NOT EXISTS on desk_agent).

### AgentController
- [x] `GET /agents` — returns `PaginatedResponse<AgentResponse>`.
- [x] `GET /agents/{agentId}` — returns `AgentResponse` (no `bamboohrId` leak).

---

## 5. Agent Days Off (Spec §7.4) ✅ DONE (Phase 2, Task 11)

### AgentDayOffService
- [x] **`listDaysOffForAgent`** — Date range filtering (`from`/`to`).
- [x] **`listAllDaysOff`** — Cursor-based pagination with keyset queries, date range filter, eager-loaded agent via `JOIN FETCH`, returns `AgentDayOffResponse` with agent summary.

### AgentDayOffController
- [x] `GET /agents/{agentId}/days-off` — returns `List<AgentDayOffResponse>` (no tenantId/full Agent leak).
- [x] `GET /days-off` — returns `PaginatedResponse<AgentDayOffResponse>` with cursor pagination.

---

## 6. Specializations (Spec §7.5) ✅ DONE (Phase 2, Task 8)

### SpecializationService
- [x] **`updateSpecialization`** — Validates unique name per desk, renames, saves.
- [x] **`deleteSpecialization`** — Checks references by desk-agents (primary and secondary) and staffing requirements → 409 Conflict.
- [x] **`createSpecialization`** — Unique name validation per desk.

### SpecializationController
- [x] Returns `SpecializationResponse` DTO (strips `tenantId`/`deskId`).

---

## 7. Agent Preferences (Spec §7.6) ✅ DONE (Phase 2, Task 13)

### AgentPreferenceService
- [x] **`savePreferences`** — Sets tenantId/deskId/agent, standing upsert (replaces existing for same desk-agent+dayOfWeek), derives `dayOfWeek` from `date` for weekly prefs, saves, returns updated list.
- [x] **`deletePreference`** — Validates tenant+desk ownership, deletes, 404 if not found.
- [x] **`listPreferences`** — Date range filtering: all standing prefs + weekly prefs within `from`-`to`.

### DeskAgentController (preferences section)
- [x] Returns `PreferenceResponse` DTO (no `tenantId`/`deskId`/full Agent leak).

---

## 8. Agent Exceptions (Spec §7.7) ✅ DONE (Phase 2, Task 14)

### AgentExceptionService
- [x] **`saveExceptions`** — Validates no conflict with days off on same date (409 Conflict), upsert by (desk, agent, date), validates reason is provided, normalizes BigDecimal, returns updated list.
  - **Note:** Uses 409 ConflictException for day-off conflict (changed from original spec's 400). 409 is more semantically correct — the conflict is with existing server state (a day off record), not a malformed request.
- [x] **`deleteException`** — Deletes by desk+agent+date, 404 if not found.
- [x] **`listExceptions`** — Date range filtering.

### DeskAgentController (exceptions section)
- [x] Returns typed `ExceptionResponse` DTO.

---

## 9. Constraint Weights (Spec §7.8) ✅ DONE (Phase 2, Task 12)

### ConstraintWeightsService
- [x] **`updateWeights`** — Loads existing (404 if not found), applies partial update (null fields keep current values), converts between `HardSoftScore` and `ScoreDto`, saves.
- [x] **`getWeights`** — Falls back to transient default `ConstraintWeights` if not found (defensive).

### ConstraintWeightsController
- [x] Returns `ConstraintWeightsDto` (with `ScoreDto` fields) instead of raw JPA entity.

---

## 10. Timeslots (Spec §7.9)

### TimeslotController
- [ ] `POST .../timeslots/generate` — accepts `Map<String, Object>` with unsafe casts instead of a typed `GenerateTimeslotsRequest` DTO.
- [ ] `DELETE .../timeslots` — missing 409 Conflict check when affected timeslots are referenced by an accepted schedule (spec §7.9: *"Returns 409 Conflict if any of the affected timeslots are referenced by an accepted schedule."*).

---

## 11. Staffing Requirements (Spec §7.10)

### StaffingRequirementService — ALL THREE METHODS ARE STUBS
- [ ] **`listRequirements`** — implement: query live requirements (schedule_id IS NULL), pagination, date range filter, return enriched format with joined timeslot/specialization data.
- [ ] **`saveRequirements`** — implement: validate referenced timeslots and specializations exist and belong to desk, derive replacement date range from min/max timeslot dates, delete existing live requirements in range, insert new ones with source=DIRECT, single transaction.
- [ ] **`calculateErlangX`** — implement: parse `ErlangXRequest`, call `ErlangXService` for each entry, delete existing requirements in from-to range, persist with source=ERLANG_X. Inject `ErlangXService` (not currently injected).

### StaffingRequirementController
- [ ] `POST .../staffing-requirements` — has TODO, accepts raw `Object`, never parses or delegates. Wire up to `StaffingRequirementRequest` DTO and service.
- [ ] `POST .../staffing-requirements/erlang-x` — has TODO, accepts raw `Object`. Wire up to `ErlangXRequest` DTO and service.

---

## 12. Erlang X Algorithm (Spec §4.4)

### ErlangXService
- [ ] **`calculateRequiredAgents`** — complete stub (returns 0). Implement the full Erlang X (Extended Erlang C) algorithm:
  1. Compute Erlang C baseline (Jagerman formula for overflow safety)
  2. Calculate abandonment probability
  3. Adjust load by retry rate
  4. Iterate until convergence
  5. Return smallest integer meeting service level target

---

## 13. Solver (Spec §6, §7.11)

### ScheduleConstraintProvider — ALL 15 CONSTRAINTS ARE STUBS
Each constraint method has the correct name but penalizes all assignments unconditionally instead of implementing the actual logic:

**Hard constraints:**
- [ ] Agent day off — penalize only when agent is assigned on a day off
- [ ] Specialization match — penalize only when agent lacks matching specialization (primary or secondary)
- [ ] One assignment per timeslot — penalize only when same agent has >1 assignment in the same timeslot
- [ ] Exactly one break — for agents with shift > breakMinShiftHours, enforce exactly one contiguous break
- [ ] Break duration — break gap must equal configured `breakDurationMinutes`
- [ ] Break blocked window — break cannot start in first/last `breakBlockedHours` of shift
- [ ] Break start alignment — break must start on boundary matching `breakStartAlignment`
- [ ] Contracted hours — agent must work exactly their effective contracted hours per day
- [ ] Bulk over-allocation limit — total supply must not exceed demand by more than `overallocationHardLimitPct`
- [ ] Bulk under-allocation hard — demand below `underallocationHardLimitPct` floor is infeasible

**Soft constraints:**
- [ ] Prefer primary specialization — penalty when secondary specialization is used
- [ ] Honour preferred start time — penalty for assignments before agent's preferred start
- [ ] Honour preferred break time — penalty when break does not match preferred time
- [ ] Break clustering — penalty when too many agents on break in same timeslot (> `breakClusterThresholdPct`)
- [ ] Bulk under-allocation soft — penalty scaling linearly with demand shortfall

### SolverService.startSolve — MOSTLY STUB
- [ ] Check no existing non-accepted schedule for this desk (409 Conflict via `InMemoryScheduleStore`)
- [ ] Implement all 12 pre-solve validation checks (spec §7.11):
  - Period length 1–31 days
  - Timeslots exist for every day in range
  - incrementMinutes/startTime/endTime match existing timeslot structure
  - Every active desk-agent has primary + secondary specializations
  - Contracted hours are multiples of incrementMinutes/60
  - At least one staffing requirement exists
  - At least one active desk-agent available
  - breakDurationMinutes is a positive multiple of incrementMinutes
  - Break alignment conformance for preferred break times
  - Coverage window ≥ contracted hours + break where applicable
  - No exception/day-off conflict on same date
  - Every specialization with demand has at least one eligible agent
- [ ] Load all problem facts from database (desk-agents, specializations, timeslots, staffing requirements, preferences, days off, exceptions, constraint weights)
- [ ] Expand staffing requirements into `AgentAssignment` planning entities
- [ ] Start solver asynchronously via Timefold `SolverManager`
- [ ] Propagate tenant context to solver thread (ThreadLocal doesn't propagate)
- [ ] Handle solver completion callback (update status to COMPLETED, capture score)
- [ ] Handle solver failure (set status to FAILED, capture error message)
- [ ] Set `createdAt` timestamp on schedule

### SolverService.stopSolve — STUB
- [ ] Validate schedule is in RUNNING status (409 if not)
- [ ] Actually terminate the solver via `SolverManager`
- [ ] Set status to STOPPED, retain best solution and score

---

## 14. Schedule Management (Spec §7.11)

### ScheduleController — WRONG DTOs
All endpoints use raw `Schedule` JPA entity instead of the existing DTOs:
- [ ] `startSolve()` — accepts `@RequestBody Schedule` instead of `SolveRequest` DTO. Should return `ScheduleSummary` with `202 Accepted`.
- [ ] `listSchedules()` — returns `List<Schedule>` instead of `PaginatedResponse<ScheduleSummary>`.
- [ ] `getScheduleDetail()` — returns raw `Schedule` instead of `ScheduleDetailResponse` (with output views).
- [ ] `stopSolve()` — returns raw `Schedule` instead of `ScheduleSummary`.
- [ ] `acceptSchedule()` — returns raw `Schedule`. Spec unclear on response format but should at minimum not leak internal fields.

### ScheduleService
- [ ] **`listSchedules`** — complete stub (returns empty list). Implement: merge in-memory schedule (if exists for this desk) with database accepted schedules, cursor-based pagination, return `ScheduleSummary` format.
- [ ] **`getScheduleDetail`** — partially implemented. Missing: `?date` query parameter to filter output views to a single day. Missing: call `ScheduleOutputService` to build staffingSummary, agentSchedule, preferenceReport, constraintViolations. Missing: tenant/desk validation on in-memory path.
- [ ] **`acceptSchedule`** — complete stub (returns null). This is the most complex operation. Implement: validate status is COMPLETED or STOPPED (409 otherwise), snapshot live timeslots and staffing requirements with new IDs tied to schedule, remap assignment foreign keys to snapshot IDs, persist schedule + snapshots + assignments in single transaction, delete overlapping accepted schedules for same desk/date range, set status to ACCEPTED, remove from in-memory store.
- [ ] **`rejectSchedule`** — partially implemented (removes from store). Missing: validate status is COMPLETED/STOPPED/FAILED (409 if RUNNING).

### Schedule.score Bug ✅ FIXED (Phase 1, Task 5)
- [x] **`score` column** — removed `insertable = false, updatable = false`.

---

## 15. Schedule Output Views (Spec §8)

### ScheduleOutputService — ALL FOUR METHODS ARE STUBS
- [ ] **`buildStaffingSummary`** — compute per-day, per-specialization: predictedHours, actualHours, deltaHours, coveragePct. Include per-day totals row and grand-total row. Null coveragePct when predictedHours is zero.
- [ ] **`buildAgentSchedule`** — compute per-agent, per-day: shiftStart, shiftEnd, totalHours, assignments list (with matchType PRIMARY/SECONDARY), breaks list (contiguous unassigned gaps).
- [ ] **`buildPreferenceReport`** — compute per-agent, per-day: preferenceSource (WEEKLY/STANDING/NONE), preferred vs actual times, honoured flags. Include summary counters (totalPreferences, startTimeHonouredCount, breakTimeHonouredCount, overallHonouredPct).
- [ ] **`buildConstraintViolations`** — extract constraint violations from Timefold score explanation. Group by constraint name, level, weight. Include per-violation details (agent, timeslot, description). Build `violatedHardConstraints` list for schedule top level.

### ScheduleExportService — STUB
- [ ] **`exportToExcel`** — returns empty byte array. Implement with Apache POI XSSFWorkbook:
  - Tab 1: Staffing Summary
  - Tab 2: Agent Schedule
  - Tab 3: Preference Report

---

## 16. BambooHR Integration (Spec §9)

### BambooRefreshService
- [ ] **Soft-delete removed employees** — agents present in previous refresh but absent from current should be marked `active = false`.
- [ ] **Day-off upsert** — currently creates new records without checking for duplicates. Will throw unique constraint violation on `(agent_id, date)` if same day off is refreshed twice. Use upsert by (agent, date).
- [ ] **Day-off deletion for stale records** — days off within the refreshed range no longer present in BambooHR should be deleted (for refreshed agents only).
- [ ] **Day-off type mapping** — code checks `"MANDATORY"` (case-sensitive uppercase). Spec says `"holiday"` and `"mandatory"` (lowercase) should both map to MANDATORY. Use case-insensitive comparison.
- [ ] **Transaction scope** — BambooHR API calls are currently inside `@Transactional`. Spec says external API calls happen before the transaction; only DB writes should be transactional.
- [ ] **Case-insensitive desk name matching** — spec says `project` field matched case-insensitively to `Desk.name`.
- [ ] **Cross-desk conflict logging** — when an agent is already assigned to a different desk, log a warning and skip (currently doesn't distinguish this desk vs another desk).
- [x] **Error mapping** — ~~`IllegalStateException` from concurrency guard maps to 500~~ Fixed in Phase 2 review: now throws `RefreshInProgressException` (409). Also changed desk-not-found from `IllegalArgumentException` (400) to `EntityNotFoundException` (404).

### HttpBambooHRClient — EXPECTED STUB
- [ ] All three methods throw `UnsupportedOperationException`. Implement when real BambooHR credentials are available.

### MockBambooHRClient
- [ ] `listTimeOff()` returns empty list — should return some day-off test data for development/testing.

---

## 17. Model / Schema Gaps ✅ DONE (Phase 1, Task 5)

- [x] **`Schedule.score`** — removed `insertable = false, updatable = false`.
- [x] **`AgentAssignment.deskAgent`** — added `nullable = false` to `@JoinColumn`.
- ~~**`AgentPreference`** partial unique indexes~~ — **ALREADY EXISTS** in `V1__initial_schema.sql` (lines 85-92): `idx_agent_preference_standing` and `idx_agent_preference_weekly`.
- ~~**`Timeslot`** partial unique index~~ — **ALREADY EXISTS** in `V1__initial_schema.sql` (lines 182-184): `idx_timeslot_live`.
- ~~**`StaffingRequirement`** partial unique index~~ — **ALREADY EXISTS** in `V1__initial_schema.sql` (lines 201-203): `idx_staffing_requirement_live`.

---

## 18. Frontend

### 18.1 Critical (Core Features Non-Functional)

- [ ] **Schedule Results: all four tab contents are empty stubs** — Staffing Summary, Agent Schedule, Preference Report, Constraint Violations show only placeholder text. Need TypeScript interfaces for output view types (currently `unknown[]`).
- [ ] **Staffing Requirements: no save button** — demand grid values cannot be persisted. Grid inputs are uncontrolled (`defaultValue`). Need state tracking, save button calling `POST /desks/{deskId}/staffing-requirements`.
- [ ] **Staffing Requirements: grid not pre-populated** — should load existing requirements via `GET /desks/{deskId}/staffing-requirements`.
- [ ] **Desk Agents: assign button is dead** — no onClick handler, no modal, no `GET /agents?unassigned=true`, no `POST /desks/{deskId}/agents`.
- [ ] **Desk Agents: no remove agent button** per row (spec requires with confirmation dialog).
- [ ] **Desk Agents: no edit specializations** per agent (no dropdowns, no `PUT .../specializations`).
- [ ] **Desk Agents: no edit contracted hours** per agent (no input, no `PUT .../contracted-hours`).
- [ ] **Agent Preferences: not editable** — read-only table, no date picker, no time pickers, no save. API methods exist but are never called.
- [ ] **Agent Exceptions: not editable** — read-only table, no date picker, no inputs, no save. API methods exist but are never called.
- [ ] **Desk Management: no edit desk** — `PUT /desks/{id}` never called, no inline/modal edit form.

### 18.2 High Priority

- [ ] **Sidebar missing Desk Management link** — Desk Management page (`/desk-management`) is only accessible from the DeskSelector page. Spec §12.2 says it should be *"accessible from the desk selector or via the sidebar"*. The sidebar nav in `App.tsx:DeskLayout` does not include a link.
- [ ] **No Erlang X calculation mode** on Staffing Requirements page (spec §12.7).
- [ ] **No validation summary panel** on Schedule Setup page (spec §12.9).
- [ ] **No past schedules list** on Schedule Setup page (`GET /desks/{deskId}/schedules` never called).
- [ ] **No active desk indicator** in navigation bar — sidebar shows static "WFM Service" instead of selected desk name.
- [ ] **No progress indicator** (spinner) while solver is running.
- [ ] **Desk Agents: no active/inactive filter** (spec says default to active only).
- [ ] **Desk Agents: no days off view** per agent (expandable row or modal showing `GET /agents/{id}/days-off`).
- [ ] **Desk Agents: missing table columns** — job title, last refreshed timestamp.
- [ ] **Agent Preferences/Exceptions: no agent selector dropdown** — shows raw UUID instead of name.
- [ ] **Constraint Weights: no reset to defaults button**.
- [ ] **Error handling: all errors except timeslot generation are silently swallowed** (`console.error` only). API client discards `error.details[]` and `error.code` from the structured error envelope.

### 18.3 Medium Priority

- [ ] Desk Management: missing "number of assigned agents" column.
- [ ] Desk Management: delete not disabled when desk has accepted schedules.
- [ ] Desk Management: no `defaultContractedHoursPerDay` field in create form.
- [ ] Specializations: no rename capability (`PUT .../specializations/{id}` never called).
- [ ] Specializations: delete not disabled/warned when in use.
- [ ] Constraint Weights: missing description column and level (Hard/Soft) dropdown.
- [ ] Schedule Setup: break duration not filtered to multiples of increment.
- [ ] Schedule Results: non-optimal accept dialog uses plain `confirm()` — should list violated constraints.
- [ ] Staffing Requirements: no "copy day" button.
- [ ] Agent Preferences: delete does not update local state (stale row remains).
- [ ] Agent Exceptions: delete does not update local state.
- [ ] Agent Exceptions: no "standard hours" column, no day-off awareness (greyed-out days).
- [ ] Hardcoded tenant ID (`'1'`) with no UI to change it.
- [ ] Export endpoint (`schedules.export`) has no error handling.
- [ ] No loading states on most pages.
- [ ] No confirmation dialogs for delete on Specializations page.

---

## Summary Scorecard

| Area | Estimated Completion |
|------|---------------------|
| Infrastructure (error handler, DTOs, pagination) | ~85% |
| Desk CRUD | ~100% |
| Desk Agent management | ~85% |
| Agent listing (tenant-level) | ~100% |
| Agent days off | ~100% |
| Specializations CRUD | ~100% |
| Agent preferences | ~95% |
| Agent exceptions | ~95% |
| Constraint weights | ~100% |
| Timeslot management | ~80% |
| Staffing requirements | ~0% |
| Erlang X algorithm | ~0% |
| Solver constraints (15 constraints) | ~0% |
| Solver lifecycle (start/stop/validate) | ~5% |
| Schedule management (list/accept/reject) | ~10% |
| Schedule output views (4 views) | ~0% |
| Schedule Excel export | ~0% |
| BambooHR refresh | ~65% |
| Frontend | ~25% |
