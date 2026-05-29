# Architecture

**Analysis Date:** 2026-04-02

## Pattern Overview

**Overall:** Layered Spring Boot service with an embedded constraint optimization engine (Timefold Solver).

**Key Characteristics:**
- REST API backed by a single Spring Boot application (no microservices)
- Multi-tenant: every request must carry `X-Tenant-ID` (long); a servlet filter extracts it into a thread-local `TenantContext`
- All scheduling optimization is performed in-process by Timefold Solver running asynchronously; in-flight solves live in an `InMemoryScheduleStore`, not the database
- Accepted (finalized) schedules are snapshotted into PostgreSQL with immutable copies of timeslots, staffing requirements, and agent assignments
- A React SPA (Vite + TypeScript) communicates exclusively through the REST API

## Layers

**Controller (HTTP):**
- Purpose: Accept requests, validate path/query params, delegate to service, serialize response
- Location: `src/main/java/com/wfm/controller/`
- Contains: `@RestController` classes, `GlobalExceptionHandler` (`@RestControllerAdvice`)
- Depends on: Service layer, `TenantContext`
- Used by: Frontend SPA, external API consumers

**Service (Business Logic):**
- Purpose: Orchestrate domain operations, load and persist data, drive the solver lifecycle
- Location: `src/main/java/com/wfm/service/`
- Contains: `SolverService`, `ScheduleService`, `AgentService`, `ScheduleOutputService`, `ErlangXService`, export/upload services, `InMemoryScheduleStore`
- Depends on: Repository layer, `InMemoryScheduleStore`, Timefold `SolverManager`/`SolutionManager`
- Used by: Controllers

**Repository (Persistence):**
- Purpose: JPA data access scoped to tenant/desk
- Location: `src/main/java/com/wfm/repository/`
- Contains: Spring Data JPA interfaces for every aggregate (Agent, Schedule, Timeslot, StaffingRequirement, AgentAssignment, etc.)
- Depends on: JPA, PostgreSQL
- Used by: Service layer

**Domain Model:**
- Purpose: JPA entities that simultaneously serve as Timefold planning model objects
- Location: `src/main/java/com/wfm/model/`
- Contains: `Schedule` (`@PlanningSolution`), `AgentAssignment` (`@PlanningEntity`), all problem facts, enums, and the `ScheduleConfig` record
- Depends on: Nothing (pure model)
- Used by: All layers

**Solver:**
- Purpose: Define constraint rules and construction heuristic helpers
- Location: `src/main/java/com/wfm/solver/`
- Contains: `ScheduleConstraintProvider` (18 constraints), `BreakAwareConstructionPhase` (delegates all assignment to Timefold CH), `AgentAssignmentDifficultyComparator`, `CustomConstraint` plugin interface
- Depends on: Domain model
- Used by: `SolverService`, Timefold framework

**Integration:**
- Purpose: Sync agent roster and days-off from BambooHR
- Location: `src/main/java/com/wfm/integration/`
- Contains: `BambooHRClient` interface, `HttpBambooHRClient` (production), `MockBambooHRClient` (dev/test), `DelegatingBambooHRClient`, `BambooRefreshService`
- Depends on: Repository layer
- Used by: `DeskAgentController` (refresh endpoint), `ClientManagementController`

**Config:**
- Purpose: Cross-cutting concerns — multi-tenancy, CORS, JSON serialization
- Location: `src/main/java/com/wfm/config/`
- Contains: `TenantFilter`, `TenantContext` (ThreadLocal), `CorsConfig`, `JacksonConfig`, `HardSoftScoreSerializer`/`Deserializer`

**DTO:**
- Purpose: Request/response shapes decoupled from entities
- Location: `src/main/java/com/wfm/dto/`
- Contains: Request records (`SolveRequest`, `DeskRequest`, `ErlangXRequest`, etc.) and response classes (`ScheduleDetailResponse`, `ScheduleSummary`, `AgentResponse`, etc.)

**Exception:**
- Purpose: Typed business exceptions mapped to HTTP status codes by `GlobalExceptionHandler`
- Location: `src/main/java/com/wfm/exception/`
- Contains: `EntityNotFoundException` (404), `ConflictException` (409), `UnprocessableException` (422), `PreSolveValidationException` (400), `RefreshInProgressException` (409)

**Util:**
- Purpose: Standalone helpers
- Location: `src/main/java/com/wfm/util/`
- Contains: `CursorPagination`, `BigDecimals`, `FteSpreadsheetGenerator`

## Data Flow

**Solve Request Flow:**

1. `POST /api/v1/desks/{deskId}/schedules/solve` arrives; `TenantFilter` resolves `X-Tenant-ID`
2. `ScheduleController.startSolve` delegates to `SolverService.startSolve`
3. `SolverService` loads all problem facts (agents, timeslots, staffing requirements, preferences, days off, exceptions, constraint weights) in a single `@Transactional(readOnly=true)` — entities detach when the transaction closes
4. Pre-solve validation runs (12 checks per spec §7.11)
5. `AgentAssignment` planning entities are expanded from staffing requirements: demand seats plus overflow seats up to `overallocationHardLimitPct`
6. `Schedule` object is populated with all collections and placed in `InMemoryScheduleStore`
7. Timefold `SolverManager.solveBuilder()` starts the solve asynchronously; each best-solution callback updates the in-memory store
8. First best solution with `hardScore >= 0` records `feasibleAt` timestamp on the `Schedule`
9. When time limit expires (default 5 min), status transitions to `COMPLETED`
10. Clients poll `GET /{id}` to read solve progress from the in-memory store

**Accept/Persist Flow:**

1. `PUT /api/v1/desks/{deskId}/schedules/{id}/accept?version=N`
2. `ScheduleService.acceptSchedule` validates optimistic lock version, status (`COMPLETED` or `STOPPED`), and tenant/desk ownership
3. Supersedes any existing `ACCEPTED` date rows for overlapping dates in `accepted_schedule_date`
4. `entityManager.flush()` ensures supersede writes land before the insert
5. Snapshots live timeslots → new `Timeslot` rows with `schedule_id` set
6. Snapshots staffing requirements and solver `AgentAssignment` list, remapping to snapshot timeslot IDs
7. Inserts `AcceptedScheduleDate` rows for every covered date
8. `Schedule` is persisted with status `ACCEPTED`; removed from `InMemoryScheduleStore`

**BambooHR Refresh Flow:**

1. `POST /api/v1/desks/{deskId}/agents/refresh` triggers `BambooRefreshService`
2. `refreshInProgress` map (per deskId) prevents concurrent refreshes — throws `RefreshInProgressException` if busy
3. `BambooHRClient.listEmployees` fetches roster; agents are upserted using `bamboohrId` as natural key
4. `BambooHRClient.listTimeOff` fetches absences over a configurable lookback/lookahead window; upserts `AgentDayOff` records
5. Desk assignment and specialization mapping applied per hardcoded desk-name → specialization rules in `BambooRefreshService`

**Staffing Requirements via Erlang X:**

1. Client submits call volumes and SLA parameters via `POST .../staffing-requirements/erlang-x`
2. `ErlangXService.calculateRequiredAgents` runs iterative Erlang X formula (converges within 100 iterations)
3. Result: required FTE integer per timeslot per specialization; saved as `StaffingRequirement` rows

## Domain Model

**`Schedule`** (`@PlanningSolution`, `@Entity`):
- Dual-purpose: JPA entity (persisted: id, tenant_id, desk_id, schedule window, break config, score, status, version) and Timefold solution wrapper (transient: agents, timeslots, staffingRequirements, assignments, preferences, daysOff, constraintWeights, agentDayConfigs, timeslotDemandConfigs)
- Status lifecycle: `RUNNING → COMPLETED | STOPPED | FAILED` (in-memory), then `ACCEPTED` (persisted)
- `version` column provides optimistic locking for the accept operation

**`AgentAssignment`** (`@PlanningEntity`, `@Entity`):
- The sole planning variable: `agent` (`@PlanningVariable`, nullable=true) pointing to an `Agent`
- Fixed fields: `timeslot`, `requiredSpecialization`, `scheduleId`, `deskId`, `tenantId`
- One row = one agent seat in one timeslot for one required specialization

**`Agent`** (`@Entity`, `@PlanningId`):
- Linked to BambooHR via unique `(tenant_id, bamboohr_id)`
- Carries `primarySpecialization` (ManyToOne), `secondarySpecializations` (ManyToMany), and `contractedHoursPerDay`
- `deskId` column is non-null when assigned to a desk; null when unassigned

**`Timeslot`** (`@Entity`):
- One scheduling slot: `date`, `startTime`, `endTime`
- `scheduleId` null = live template; non-null = immutable snapshot attached to an accepted schedule

**`StaffingRequirement`** (`@Entity`):
- Demand for a `(timeslot, specialization)` pair expressed in integer FTEs
- `source` enum distinguishes `DIRECT` (manually entered) from `ERLANG_X` (calculated)
- `scheduleId` null = live; non-null = snapshot

**`ConstraintWeights`** (`@ConstraintConfiguration`, `@Entity`):
- One row per (tenant, desk) with a `HardSoftScore` weight for each of the 18 constraints
- Loaded into `Schedule.constraintWeights` before every solve; defaults applied if no row exists

**`AgentPreference`** (`@Entity`):
- Preferred start time and/or break time per agent per day-of-week (standing) or per specific date (weekly override)
- Weekly overrides supersede standing preferences during solve and in output reports (spec §5.8)

**`AgentDayOff`** (`@Entity`):
- Records PTO/holiday/sick days; drives the hard `Agent day off` constraint (weight 10,000 hard)
- `status` enum: `APPROVED` | `PENDING` (solver only uses `APPROVED`)

**`AgentException`** (`@Entity`):
- Overrides `contractedHoursPerDay` for a specific agent on a specific date within a desk context
- Used to compute `AgentDayConfig` problem facts before each solve

**`AcceptedScheduleDate`** (`@Entity`, composite PK `scheduleId + date`):
- Maps accepted schedule IDs to individual dates; status `ACCEPTED` or `SUPERSEDED`
- Enables one active accepted schedule per (tenant, desk, date) without deleting historical data

**`ScheduleConfig`** (record, `@ProblemFact`):
- Immutable snapshot of schedule-level parameters passed directly to constraint streams

**`TimeslotDemandConfig`** (record, `@ProblemFact`):
- Pre-computed total demand FTEs per timeslot; used by `unassignedAssignment` and bulk allocation constraints

**`AgentDayConfig`** (`@ProblemFact`):
- Pre-computed effective contracted hours per agent per day, accounting for exceptions and days off

## Solver Design

**Algorithm:** Timefold Solver 1.16.0 (Constraint Streams API, `HardSoftScore`)
**Config file:** `src/main/resources/solverConfig.xml`

**Construction Heuristic (CH):**
- Default Timefold CH configuration
- All `AgentAssignment.agent` variables start null; CH assigns them evaluating all 18 constraints simultaneously per move
- `AgentAssignmentDifficultyComparator` orders assignments by descending difficulty for CH traversal

**Local Search:**
- Tabu search with `entityTabuSize=7` combined with simulated annealing (`startingTemperature=0hard/3000soft`)
- Time limit set programmatically via `SolverConfigOverride` from `solver.time-limit` property (default PT5M)

**18 Constraints in `ScheduleConstraintProvider`:**

Hard constraints (default weights shown):
1. `Agent day off` — 10,000 hard
2. `Specialization match` — 1 hard
3. `One assignment per timeslot` — 1,000 hard
4. `Exactly one break` — 100 hard
5. `Break duration` — 10 hard
6. `Break blocked window` — 10 hard
7. `Break start alignment` — 10 hard
8. `Contracted hours (over)` — 1,001 hard
9. `Contracted hours (under)` — 100 hard
10. `Contracted hours (under, zero)` — 100 hard
11. `Bulk over-allocation limit` — 1 hard
12. `Bulk under-allocation hard` — 1 hard

Soft constraints (default weights shown):
13. `Unassigned assignment` — 1,000 soft
14. `Prefer primary specialization` — 1 soft
15. `Honour preferred start time` — 5 soft
16. `Honour preferred break time` — 5 soft
17. `Break clustering` — 2 soft
18. `Bulk under-allocation soft` — 1 soft

All weights are stored in `ConstraintWeights` per desk and can be overridden via the API.

## In-Memory Schedule Store

`InMemoryScheduleStore` (`src/main/java/com/wfm/service/InMemoryScheduleStore.java`) holds `RUNNING / COMPLETED / STOPPED / FAILED` schedules in a `ConcurrentHashMap` with a `ReentrantLock` guarding writes. A secondary `deskToScheduleIndex` map enforces the one-in-flight-per-desk invariant. Accepted schedules are removed from the store and exist only in the database.

## Error Handling

**Strategy:** Typed exceptions thrown from service layer; `GlobalExceptionHandler` (`@RestControllerAdvice` in `src/main/java/com/wfm/controller/GlobalExceptionHandler.java`) maps each to an HTTP status.

**Mapping:**
- `EntityNotFoundException` → 404 NOT_FOUND
- `ConflictException` → 409 CONFLICT
- `RefreshInProgressException` → 409 REFRESH_IN_PROGRESS
- `PreSolveValidationException` → 400 VALIDATION_FAILED (with `details` array)
- `UnprocessableException` → 422 UNPROCESSABLE_ENTITY
- `IllegalArgumentException` → 400 VALIDATION_FAILED
- `MethodArgumentNotValidException` → 400 VALIDATION_FAILED with field-level details
- Uncaught exceptions → 500 INTERNAL_ERROR (logged)

**Error response shape:** `{ "error": { "code": "...", "message": "...", "details": [...] } }`

## Multi-Tenancy

**Mechanism:** HTTP header `X-Tenant-ID` (long integer). `TenantFilter` (order 1) extracts it, stores in `TenantContext` ThreadLocal, clears in `finally`. All repository queries include `tenantId` as a filter — tenant isolation is enforced in application code only; there is no database-level row security.

## Cross-Cutting Concerns

**Logging:** SLF4J + Logback via Spring Boot. Solver phases and `SolverService` logged at DEBUG; root at INFO.
**Validation:** Jakarta Bean Validation on DTO fields; 12-point pre-solve validation in `SolverService.runPreSolveValidation`.
**Transactions:** `@Transactional` on service methods that write; `@Transactional(readOnly=true)` on `SolverService.startSolve` to load all data in a single session before entity detachment.
**Pagination:** Cursor-based via `CursorPagination` utility (`src/main/java/com/wfm/util/CursorPagination.java`); opaque base64 cursor encoding a map (e.g. `{ "id": "..." }`).
**Schema migrations:** Flyway; 24 versioned scripts in `src/main/resources/db/migration/` (V1–V24).
**Actuator:** Only `/actuator/health` exposed; excluded from `TenantFilter`.

---

*Architecture analysis: 2026-04-02*
