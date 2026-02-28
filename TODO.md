# WFM Service — Implementation TODO

Comprehensive gap analysis between `spec.md` and the current codebase.
Organized by layer, then by priority within each section.

---

## 1. Cross-Cutting / Infrastructure

### 1.1 Global Exception Handler (MISSING)
- [ ] Create `@RestControllerAdvice` that maps exceptions to the spec's standard error envelope (`ErrorResponse` with `code`, `message`, `details[]`).
- [ ] Map `IllegalArgumentException` → 400 `VALIDATION_FAILED`
- [ ] Map entity-not-found → 404 `NOT_FOUND`
- [ ] Map conflict states → 409 `CONFLICT` / `REFRESH_IN_PROGRESS`
- [ ] Map unprocessable → 422 `UNPROCESSABLE_ENTITY`
- [ ] Map uncaught exceptions → 500 `INTERNAL_ERROR`
- **Files:** new `GlobalExceptionHandler.java`; existing `ErrorResponse.java` (already defined, never used)

### 1.2 Response DTO Layer (PARTIALLY EXISTS — UNUSED)
Several DTOs already exist in `com.wfm.dto` but are **never used by any controller** — controllers return raw JPA entities instead:
- `SolveRequest` — exists but `ScheduleController.startSolve()` accepts raw `Schedule` entity
- `ScheduleSummary` — exists but `ScheduleController.listSchedules()` returns `List<Schedule>`
- `ScheduleDetailResponse` — exists but `ScheduleController.getScheduleDetail()` returns raw `Schedule`
- `ConstraintWeightsDto` — exists but `ConstraintWeightsController` returns raw `ConstraintWeights`
- `StaffingRequirementRequest` — exists but `StaffingRequirementController` accepts raw `Object`
- `StaffingRequirementResponse` — exists but not returned by any endpoint
- `ErrorResponse` — exists but no global exception handler uses it

Raw JPA entities are returned from almost every endpoint, leaking `tenantId`, `bamboohrId`, `deskId`, `scheduleId`. Additional response DTOs needed:
- [ ] `DeskResponse` — `{ id, name, description, defaultContractedHoursPerDay }`
- [ ] `AgentResponse` — `{ id, name, email, department, jobTitle, active, lastRefreshedAt }` (no `bamboohrId`)
- [ ] `AgentDayOffResponse` — `{ id, date, type }` (per-agent) and `{ id, agent: {id, name}, date, type }` (list-all)
- [ ] `SpecializationResponse` — `{ id, name }` (no `tenantId`/`deskId`)
- [ ] `TimeslotResponse` — `{ id, date, startTime, endTime }` (no `tenantId`/`deskId`/`scheduleId`)
- [ ] `PreferenceResponse` — `{ id, dayOfWeek, date, isStanding, preferredStartTime, preferredBreakTime }`
- [ ] `ExceptionResponse` — `{ id, date, contractedHoursOverride, reason }`
- [ ] Strongly typed output view sub-DTOs for `ScheduleDetailResponse` (currently uses `List<Object>` / `Object` for staffingSummary, agentSchedule, preferenceReport, constraintViolations)
- [ ] Wire all existing DTOs into their respective controllers

### 1.3 Request DTO Layer (MISSING / WRONG)
Several controllers use `Map<String, Object>` or raw `Object` as request bodies instead of typed DTOs:
- [ ] Create `DeskRequest` for `POST/PUT /desks`
- [ ] Create `AssignAgentsRequest` for `POST /desks/{deskId}/agents` — `{ agentIds: [uuid] }`
- [ ] Create `SetSpecializationsRequest` — `{ primarySpecializationId, secondarySpecializationIds }`
- [ ] Create `SetContractedHoursRequest` — `{ contractedHoursPerDay }`
- [ ] Create `GenerateTimeslotsRequest` for `POST .../timeslots/generate`
- [ ] Fix `StaffingRequirementRequest.items` → rename field to `requirements` (spec uses `requirements`)
- [ ] Fix `ErlangXRequest.items` → rename field to `parameters` (spec uses `parameters`)

### 1.4 Cursor-Based Pagination (NOT IMPLEMENTED)
The `PaginatedResponse` DTO exists but no endpoint actually computes cursors or applies keyset pagination. Affects 5 endpoints:
- [ ] `GET /desks/{deskId}/agents` — sort by (agent name asc, desk_agent.id)
- [ ] `GET /agents` — sort by (agent name asc, agent.id)
- [ ] `GET /days-off` — sort by (date asc, agent name asc, agent_day_off.id)
- [ ] `GET /desks/{deskId}/staffing-requirements` — sort by (date asc, startTime asc, specName asc, id)
- [ ] `GET /desks/{deskId}/schedules` — sort by (createdAt desc, schedule.id)
- [ ] Implement Base64-encoded JSON cursor encoding/decoding
- [ ] Implement `WHERE` clause keyset pagination (not OFFSET-based)

### 1.5 HardSoftScore Serialization (BROKEN)
- [ ] Timefold's `HardSoftScore` serializes as `"1hard/0soft"` (string) by default. Spec requires `{ "hardScore": 1, "softScore": 0 }`. Add a custom Jackson serializer/deserializer, or use `ConstraintWeightsDto` consistently.

### 1.6 BigDecimal Normalization (MISSING — Cross-Cutting)
Spec §5.1 mandates: *"Service code should normalise values to scale 2 with `HALF_UP` rounding on input."* and *"Arithmetic and comparison operations in Java must use `compareTo()` — never `equals()`."*
- [ ] Create a utility method for normalizing `BigDecimal` to scale 2 with `HALF_UP`.
- [ ] Apply normalization on input in: `DeskService` (defaultContractedHoursPerDay), `DeskAgentService` (contractedHoursPerDay), `AgentExceptionService` (contractedHoursOverride), `SolverService` (breakBlockedHours, breakMinShiftHours, defaultContractedHoursPerDay).
- [ ] Audit all BigDecimal comparisons — replace any `.equals()` with `.compareTo() == 0`.

### 1.7 Pre-Solve Entity Preparation (MISSING)
Spec §5.12 requires careful entity handling before solver execution:
- [ ] **Hibernate proxy unwrapping** — Copy Hibernate proxy collections (`DeskAgent.secondarySpecializations`, etc.) into plain `ArrayList`/`HashSet` during pre-solve assembly. Timefold's best-solution cloning cannot clone Hibernate proxies.
- [ ] **Entity detachment** — Ensure all planning solution entities are detached from the JPA persistence context before handing to the solver. The `@Transactional(readOnly = true)` scope should close the persistence context after loading.
- [ ] **Tenant context propagation** — Capture `TenantContext.getTenantId()` on the request thread and set it on the solver thread in a `try/finally` block (ThreadLocal doesn't propagate to child threads).

---

## 2. Desk Management (Spec §7.1)

### DeskService
- [ ] **`updateDesk`** — complete stub (returns null). Implement: load existing desk, apply partial updates (omitted fields keep current values), validate name uniqueness if changed, save and return.
- [ ] **`deleteDesk`** — complete stub (empty body). Implement: check for accepted schedules (409 Conflict), cascade-delete all desk-scoped data (desk-agents + their preferences/exceptions, specializations, timeslots, staffing requirements, constraint weights), remove any in-memory schedule for this desk.
- [ ] **`createDesk`** — missing unique name validation per tenant. Missing default for `defaultContractedHoursPerDay` (should default to 8.0).
- [ ] **`getDesk`** — returns null instead of 404.

### DeskController
- [ ] Use request/response DTOs instead of raw `Desk` entity.

---

## 3. Desk Agents (Spec §7.2)

### DeskAgentService
- [ ] **`assignAgents`** — complete stub (returns empty list). Implement: validate agents exist and are active, validate not already assigned to ANY desk (single-desk constraint), all-or-nothing semantics (if any agent fails, none assigned), create DeskAgent records, return `DeskAgentResponse[]`.
- [ ] **`removeDeskAgent`** — complete stub (empty body). Implement: check for non-accepted schedule on desk (409 Conflict via `InMemoryScheduleStore.hasDeskSchedule`), delete desk-agent and cascade-delete preferences + exceptions, 404 if not found.
- [ ] **`setSpecializations`** — complete stub (returns null). Implement: validate specializations belong to this desk, validate primary is not in secondary list (400), load and update DeskAgent, return `DeskAgentResponse`.
- [ ] **`setContractedHours`** — complete stub (returns null). Implement: load and update DeskAgent, normalize BigDecimal to scale 2, return `DeskAgentResponse`.
- [ ] **`listDeskAgents` / `listDeskAgentResponses`** — pagination and search filtering not implemented (ignores `search`, `cursor`, `limit`).

### DeskAgentController
- [ ] `PUT .../specializations` — has TODO, never calls service, returns empty 200. Wire up to `setSpecializations`.
- [ ] `PUT .../contracted-hours` — has TODO, never calls service, returns empty 200. Wire up to `setContractedHours`.
- [ ] `POST .../agents` (assign) — returns `List<DeskAgent>` instead of `List<DeskAgentResponse>`.

---

## 4. Agents — Tenant Level (Spec §7.3)

### AgentService
- [ ] **`listAgents`** — complete stub (returns empty list). Implement: query by tenantId, cursor-based pagination, search filter (case-insensitive substring on name), `unassigned=true` filter (LEFT JOIN / NOT EXISTS on desk_agent).

### AgentController
- [ ] `GET /agents` — returns `List<Agent>` (leaks `tenantId`, `bamboohrId`). Should return `PaginatedResponse<AgentResponse>`.
- [ ] `GET /agents/{agentId}` — returns raw `Agent` entity (leaks `bamboohrId`). Use `AgentResponse` DTO.

---

## 5. Agent Days Off (Spec §7.4)

### AgentDayOffService
- [ ] **`listDaysOffForAgent`** — date range filtering not implemented (`from`/`to` ignored).
- [ ] **`listAllDaysOff`** — complete stub (returns empty list). Implement: query all days off for tenant, pagination, date range filter, return enriched format `{ id, agent: {id, name}, date, type }`.

### AgentDayOffController
- [ ] `GET /agents/{agentId}/days-off` — returns raw entity (leaks `tenantId`, full Agent). Use response DTO.
- [ ] `GET /days-off` — returns `List<AgentDayOff>` instead of `PaginatedResponse` with enriched format.

---

## 6. Specializations (Spec §7.5)

### SpecializationService
- [ ] **`updateSpecialization`** — complete stub (returns null). Implement: load, validate unique name per desk, rename, save.
- [ ] **`deleteSpecialization`** — complete stub (empty body). Implement: check if referenced by any desk-agent (primary or secondary) or staffing requirement → 409 Conflict. Else delete.
- [ ] **`createSpecialization`** — missing unique name validation per desk.

### SpecializationController
- [ ] Returns raw `Specialization` entity (leaks `tenantId`/`deskId`). Use `{ id, name }` response DTO.

---

## 7. Agent Preferences (Spec §7.6)

### AgentPreferenceService
- [ ] **`savePreferences`** — complete stub (returns empty list). Implement: set tenantId/deskId/agent on each, standing replacement logic (delete previous standing for same desk-agent+dayOfWeek), derive `dayOfWeek` from `date` for weekly prefs, save, return full updated list.
- [ ] **`deletePreference`** — complete stub (empty body). Implement: validate ownership, delete, 404 if not found.
- [ ] **`listPreferences`** — date range filtering not implemented. Should return all standing prefs + weekly prefs within `from`-`to`.

### DeskAgentController (preferences section)
- [ ] Returns raw `AgentPreference` entity (leaks `tenantId`/`deskId`/full Agent). Use response DTO.

---

## 8. Agent Exceptions (Spec §7.7)

### AgentExceptionService
- [ ] **`saveExceptions`** — complete stub (returns empty list). Implement: validate no conflict with days off on same date (400), upsert by (desk, agent, date), set tenantId/deskId, validate reason is provided, normalize BigDecimal, return full updated list.
- [ ] **`deleteException`** — complete stub (empty body). Implement: delete by desk+agent+date, 404 if not found.
- [ ] **`listExceptions`** — date range filtering not implemented.

### DeskAgentController (exceptions section)
- [ ] Returns `List<?>` (wildcard type). Use typed response DTO.

---

## 9. Constraint Weights (Spec §7.8)

### ConstraintWeightsService
- [ ] **`updateWeights`** — complete stub (returns null). Implement: load existing (or create from defaults), apply partial update (omitted fields keep current values), save, return updated weights.

### ConstraintWeightsController
- [ ] Returns raw `ConstraintWeights` JPA entity (HardSoftScore serializes wrong, leaks id/tenantId/deskId). Use `ConstraintWeightsDto`.

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

### Schedule.score Bug
- [ ] **`score` column has `insertable = false, updatable = false`** — the score will never be written to the database when a schedule is accepted. Remove these attributes. (Also listed in §17.)

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
- [ ] **Error mapping** — `IllegalStateException` from concurrency guard maps to 500 by default. Should be mapped to 409 `REFRESH_IN_PROGRESS` in the global exception handler (or throw a custom exception that the handler catches).

### HttpBambooHRClient — EXPECTED STUB
- [ ] All three methods throw `UnsupportedOperationException`. Implement when real BambooHR credentials are available.

### MockBambooHRClient
- [ ] `listTimeOff()` returns empty list — should return some day-off test data for development/testing.

---

## 17. Model / Schema Gaps

- [ ] **`Schedule.score`** — remove `insertable = false, updatable = false` (see §14 above).
- [ ] **`AgentAssignment.deskAgent`** — JPA column not marked `nullable = false`. Spec says planning variable is not nullable. Note: DB column intentionally allows NULL for `ON DELETE SET NULL` referential integrity, but JPA annotation should enforce non-null for application logic.
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
| Infrastructure (error handler, DTOs, pagination) | ~15% |
| Desk CRUD | ~30% |
| Desk Agent management | ~15% |
| Agent listing (tenant-level) | ~15% |
| Agent days off | ~20% |
| Specializations CRUD | ~30% |
| Agent preferences | ~10% |
| Agent exceptions | ~10% |
| Constraint weights | ~40% |
| Timeslot management | ~80% |
| Staffing requirements | ~0% |
| Erlang X algorithm | ~0% |
| Solver constraints (15 constraints) | ~0% |
| Solver lifecycle (start/stop/validate) | ~5% |
| Schedule management (list/accept/reject) | ~10% |
| Schedule output views (4 views) | ~0% |
| Schedule Excel export | ~0% |
| BambooHR refresh | ~60% |
| Frontend | ~25% |
