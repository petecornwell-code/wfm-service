---
last_mapped_commit: 7e18ca2766e5fc34d93136d520c5a1529f8dc3b5
last_mapped_at: 2026-09-17
---
<!-- refreshed: 2026-09-17 -->

# Architecture

**Analysis Date:** 2026-09-17

## System Overview

```text
┌─────────────────────────────────────────────────────────────────────────────┐
│                           Frontend Layer (React/TypeScript)                  │
│                          `frontend/src/pages/`, `src/api/`                   │
│  DeskSelector → DeskAgents → ShiftLibrary → ScheduleSetup → ScheduleResults │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │ REST API calls
                                       ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│                    Controller Layer (HTTP Entry Points)                       │
│                `src/main/java/com/wfm/controller/`                           │
│  ScheduleController → DeskController → ShiftTemplateController → etc.        │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │
                 ┌─────────────────────┼─────────────────────┐
                 ▼                     ▼                     ▼
┌──────────────────────────┐ ┌──────────────────┐ ┌──────────────────┐
│   Service Layer (Core    │ │ Service Layer    │ │ Service Layer    │
│   Scheduling Logic)      │ │ (Shift/Template) │ │ (Data Mgmt)      │
│ `SolverService`          │ │ `ShiftTemplate`  │ │ `Agent*Service`  │
│ `ScheduleOutputService`  │ │ `ShiftLibrary*`  │ │ `DeskService`    │
│ `ScheduleExportService`  │ │ `UsualShiftSvc`  │ │ `ClientMgmt*`    │
└──────────┬───────────────┘ └────────┬─────────┘ └────────┬─────────┘
           │                         │                    │
           ▼                         ▼                    ▼
┌──────────────────────────────────────────────────────────────┐
│             Repository Layer (Data Access)                   │
│   `src/main/java/com/wfm/repository/`                        │
│   Schedule/Agent/Shift/DeskRepository, etc.                  │
└──────────────────────────────────────┬──────────────────────┘
                                       │
            ┌──────────────────────────┼──────────────────────────┐
            ▼                          ▼                          ▼
    ┌──────────────────┐    ┌──────────────────┐    ┌──────────────────┐
    │   PostgreSQL     │    │  Timefold Solver │    │ In-Memory Store  │
    │   Database       │    │  (Constraint     │    │ (Schedule Queue) │
    │                  │    │   Optimization)  │    │                  │
    └──────────────────┘    └──────────────────┘    └──────────────────┘
```

## Component Responsibilities

| Component | Responsibility | File |
|-----------|----------------|------|
| **ScheduleController** | HTTP endpoint: initiate solves, retrieve status, accept/reject schedules | `src/main/java/com/wfm/controller/ScheduleController.java` |
| **SolverService** | Core orchestration: pre-solve setup, schedule building, solve initiation, acceptance flow | `src/main/java/com/wfm/service/SolverService.java` |
| **ScheduleConstraintProvider** | All 24 constraints: hard constraints (specialization, breaks, contiguity), soft constraints (allocation, preferences) | `src/main/java/com/wfm/solver/ScheduleConstraintProvider.java` |
| **ScheduleOutputService** | Post-solve reporting: staffing summary, agent schedules, constraint violations, drift reports | `src/main/java/com/wfm/service/ScheduleOutputService.java` |
| **DeskAgentService** | Agent-desk assignment, eligibility checking, working pattern merging | `src/main/java/com/wfm/service/DeskAgentService.java` |
| **ShiftTemplateService** | Shift template CRUD, break band management | `src/main/java/com/wfm/service/ShiftTemplateService.java` |
| **ShiftLibraryGenerationService** | Generate shift templates from demand patterns | `src/main/java/com/wfm/service/ShiftLibraryGenerationService.java` |
| **ShiftLibraryValidationService** | Validate shift templates against desk demand and capacity | `src/main/java/com/wfm/service/ShiftLibraryValidationService.java` |
| **UsualShiftService** | Manage agent usual-shift preferences (stored per agent per weekday) | `src/main/java/com/wfm/service/UsualShiftService.java` |
| **UsualShiftResolutionService** | Resolve usual-shift targets for each working agent-day before solve | `src/main/java/com/wfm/service/UsualShiftResolutionService.java` |
| **InMemoryScheduleStore** | Volatile queue of in-flight schedules during solving | `src/main/java/com/wfm/service/InMemoryScheduleStore.java` |
| **ScheduleRepository** | Persist and retrieve schedule snapshots | `src/main/java/com/wfm/repository/ScheduleRepository.java` |
| **AgentAssignmentRepository** | Persist slot assignments (one per timeslot-agent pairing) | `src/main/java/com/wfm/repository/AgentAssignmentRepository.java` |
| **AgentShiftAssignmentRepository** | Persist shift assignments (one per agent-date in shift mode) | `src/main/java/com/wfm/repository/AgentShiftAssignmentRepository.java` |
| **Frontend** | React TypeScript UI for desk setup, shift library, schedule visualization, result review | `frontend/src/pages/`, `frontend/src/api/client.ts` |

## Pattern Overview

**Overall:** Spring Boot REST backend + Constraint Optimization + Multi-Mode Scheduling

**Key Characteristics:**

- **Planning Problem:** Timefold-based workforce scheduling with two planning entities (`AgentAssignment` and `AgentShiftAssignment`)
- **Dual Scheduling Modes:** SLOT-mode (legacy, timeslot-based) and SHIFT-mode (new, shift-envelope-based per agent-day)
- **Hard/Soft Constraint Model:** Hard constraints (specialization, breaks, contiguity, shift envelope compliance), soft constraints (allocation balance, preferences, usual-shift consistency)
- **Lazy Entity Persistence:** Planning entities only exist in-memory during solving; they are persisted only on schedule acceptance via denormalized snapshots
- **Multi-Tenant:** Every entity is scoped to `tenant_id`; isolation enforced via `TenantContext` and `TenantFilter`
- **Asynchronous Solving:** Solves run in background via Timefold's `SolverManager`; frontend polls status and retrieves results via REST

## Layers

**Presentation (Frontend):**

- Purpose: User interface for desk configuration, shift library design, schedule request and review
- Location: `frontend/src/`
- Contains: React components, TypeScript client, pages
- Depends on: REST API layer (via `frontend/src/api/client.ts`)
- Used by: Browser clients

**Controller (HTTP/REST):**

- Purpose: Expose operations as HTTP endpoints, translate requests/responses
- Location: `src/main/java/com/wfm/controller/`
- Contains: `@RestController` classes mapping HTTP to service methods
- Depends on: Service layer, DTOs
- Used by: Frontend, external callers

**Service (Business Logic):**

- Purpose: Orchestrate domain operations, enforce rules, call repositories
- Location: `src/main/java/com/wfm/service/`
- Contains: Multi-purpose services (solver, output, shifts, agents, data import/export)
- Depends on: Repository layer, model entities, Timefold API
- Used by: Controllers

**Solver (Constraint Optimization):**

- Purpose: Define all hard and soft constraints and score the solution
- Location: `src/main/java/com/wfm/solver/`
- Contains: `ScheduleConstraintProvider` (24 constraints), difficulty comparator
- Depends on: Timefold API, model entities
- Used by: SolverService via Timefold's `SolverManager`

**Repository (Data Access):**

- Purpose: Database queries and persistence
- Location: `src/main/java/com/wfm/repository/`
- Contains: Spring Data JPA `Repository` interfaces
- Depends on: JPA/Hibernate, PostgreSQL driver
- Used by: Service layer

**Model (Domain Objects):**

- Purpose: Entity definitions with JPA and Timefold annotations
- Location: `src/main/java/com/wfm/model/`
- Contains: `@Entity` classes and enums (Schedule, Agent, AgentAssignment, etc.)
- Depends on: JPA, Timefold core API
- Used by: All layers above repository

**DTO (Data Transfer Objects):**

- Purpose: API request/response shape isolation from entities
- Location: `src/main/java/com/wfm/dto/`
- Contains: Request and response records/classes
- Depends on: Model entities (for copying/mapping)
- Used by: Controllers, service export paths

## Data Flow

### Primary Request Path: Solving a Schedule

1. **Frontend initiates** (`ScheduleSetup.tsx` → POST `/api/schedule`)
   - Sends `SolveRequest`: deskId, periodStartDate, periodEndDate, etc.

2. **ScheduleController.startSolve()** (`src/main/java/com/wfm/controller/ScheduleController.java`)
   - Validates request, calls `SolverService.startSolve(deskId, request)`

3. **SolverService.startSolve()** (transactional read-only, `src/main/java/com/wfm/service/SolverService.java`)
   - Load desk, agents, timeslots, staffing requirements, constraints, shift templates
   - Call `buildSchedule()` → constructs `Schedule` entity with all problem facts
   - Call `buildAssignments()` → creates `AgentAssignment` entities (one per timeslot-specialization pair)
   - Call `buildShiftAssignments()` → creates `AgentShiftAssignment` entities if SHIFT mode (one per agent-date)
   - Call `resolveUsualShiftTargets()` → populates `ResolvedUsualShiftTarget` for consistency checking
   - Call `startSolveAsync()` → detach entities and hand to Timefold's `SolverManager`

4. **Timefold Solving** (async, background thread)
   - `SolverManager` instantiates `SolverConfigOverride` with `ScheduleConstraintProvider`
   - Construction heuristic + local search + termination (5-min default)
   - Modifies planning variables: `AgentAssignment.agent` and `AgentShiftAssignment.shiftBandPair`
   - Computes `HardSoftScore` from constraints

5. **Frontend polls** (`ScheduleResults.tsx` → GET `/api/schedule/{scheduleId}`)
   - Calls `ScheduleController.getScheduleStatus(scheduleId)`
   - Returns `ScheduleSummary` with status (RUNNING/FEASIBLE/INFEASIBLE) and score

6. **Frontend retrieves detail** (after solve completes, GET `/api/schedule/{scheduleId}/detail`)
   - Calls `ScheduleOutputService.buildScheduleDetail(schedule)`
   - Builds staffing summary, agent schedule, constraint violations, drift report

### Secondary Path: Accepting a Schedule

1. **Frontend clicks Accept** (`ScheduleResults.tsx` → POST `/api/schedule/{scheduleId}/accept`)
   - Calls `ScheduleController.acceptSchedule(scheduleId)`

2. **SolverService.acceptSchedule()** (transactional read-write)
   - Reload all entities from in-memory store (entities were detached during solve)
   - Mark each `AgentAssignment` and `AgentShiftAssignment` with denormalized snapshots (D-07)
   - Persist all entities to PostgreSQL
   - Mark schedule status as ACCEPTED
   - Update `AgentUsualShift` rows from the accepted shift assignments (for SHIFT mode)

3. **Database records** the permanent schedule state

### Tertiary Path: Drift Report (Post-Accept)

1. **ScheduleOutputService.buildDriftReport()** 
   - Load the accepted `AgentShiftAssignment` rows from the schedule
   - Load stored `AgentUsualShift` entries per agent per weekday
   - For each agent-day, compute the assigned shift vs. the stored usual preference
   - Generate `DriftReportEntry` per agent-day showing delta

**State Management:**

- **Pre-Solve:** All planning entities live in-memory only, not yet in database
- **During Solve:** In-memory store `InMemoryScheduleStore` holds the `Schedule`; database untouched
- **Post-Solve (Before Accept):** Entities remain in-memory; JSON serialization for API responses
- **Post-Accept:** Entities persisted to database with denormalized snapshots; in-memory copy removed

## Key Abstractions

**Schedule (Planning Solution):**

- Purpose: Root object that holds the problem facts and planning entities
- Examples: `src/main/java/com/wfm/model/Schedule.java`
- Pattern: Timefold `@PlanningSolution` with `@ProblemFactCollectionProperty` and `@PlanningEntityCollectionProperty`

**AgentAssignment (Planning Entity 1):**

- Purpose: One slot to be assigned to zero or one agent
- Examples: `src/main/java/com/wfm/model/AgentAssignment.java`
- Pattern: Timefold `@PlanningEntity` with `@PlanningVariable(valueRangeProviderRefs = "agentRange", nullable = true)`

**AgentShiftAssignment (Planning Entity 2):**

- Purpose: One agent-day's shift envelope choice (SHIFT mode only)
- Examples: `src/main/java/com/wfm/model/AgentShiftAssignment.java`
- Pattern: Timefold `@PlanningEntity` with `@PlanningVariable(valueRangeProviderRefs = "shiftBandRange", allowsUnassigned = true)`

**ShiftTemplate:**

- Purpose: A named shift pattern (start/end time, breaks)
- Examples: `src/main/java/com/wfm/model/ShiftTemplate.java`
- Pattern: Parent to `ShiftTemplateBreakBand` entities; immutable once used in solved schedules

**ShiftBandPair (Problem Fact):**

- Purpose: A (template, band) choice for an agent-day shift envelope
- Examples: `src/main/java/com/wfm/model/ShiftBandPair.java`
- Pattern: Denotes a contiguous working span + break window; immutable; no PK

**ResolvedUsualShiftTarget (Problem Fact):**

- Purpose: Per-agent-date desired shift computed from stored `AgentUsualShift`
- Examples: `src/main/java/com/wfm/model/ResolvedUsualShiftTarget.java`
- Pattern: Pre-solve-built fact used by `usualShiftConsistency` soft constraint; empty list in SLOT mode

**ScheduleConfig (Problem Fact):**

- Purpose: Transient configuration passed to solver (increment, break geometry, modes, consistency tolerance)
- Examples: `src/main/java/com/wfm/model/ScheduleConfig.java`
- Pattern: Derived from `Schedule` and `ConstraintWeights` on each solve; computed at `getScheduleConfig()`

**ConstraintWeights (Configuration):**

- Purpose: Hard and soft constraint penalties; stored per desk or globally
- Examples: `src/main/java/com/wfm/model/ConstraintWeights.java`
- Pattern: Loaded pre-solve and attached to `Schedule`; tuned via UI

## Entry Points

**ScheduleController.startSolve():**

- Location: `src/main/java/com/wfm/controller/ScheduleController.java:87`
- Triggers: POST `/api/schedule`
- Responsibilities: Validate request, call `SolverService.startSolve()`, return schedule summary

**ScheduleController.getScheduleStatus():**

- Location: `src/main/java/com/wfm/controller/ScheduleController.java:95`
- Triggers: GET `/api/schedule/{scheduleId}`
- Responsibilities: Retrieve in-memory schedule, return status and score

**ScheduleController.acceptSchedule():**

- Location: `src/main/java/com/wfm/controller/ScheduleController.java:115`
- Triggers: POST `/api/schedule/{scheduleId}/accept`
- Responsibilities: Persist solved entities to database, mark ACCEPTED

**DeskAgentController.assignAgentToDeskPartially():**

- Location: `src/main/java/com/wfm/controller/DeskAgentController.java`
- Triggers: POST `/api/desk/{deskId}/agents/assign`
- Responsibilities: Merge agent working patterns into desk, persist desk assignment

**ShiftTemplateController.createShiftTemplate():**

- Location: `src/main/java/com/wfm/controller/ShiftTemplateController.java`
- Triggers: POST `/api/shift-template`
- Responsibilities: Create template, validate against desk demand, return created template

## Architectural Constraints

- **Threading:** Single-threaded event loop on the main Spring Boot thread; Timefold solve runs on managed thread pool (`SolverManager` executor)
- **Global state:** `InMemoryScheduleStore` is a singleton holding at most one in-flight `Schedule` per desk; protected via `ConcurrentHashMap`
- **Circular imports:** None detected; dependency graph flows: Controller → Service → Repository → Model
- **Lazy loading:** All fetch policies set to `LAZY` on JPA `@ManyToOne` relationships; entities detached after `@Transactional(readOnly=true)` methods exit
- **Tenant isolation:** Every query includes `tenant_id` filter; enforced via `TenantFilter` servlet filter on every request
- **Asynchronicity:** Solve initiated via `SolverManager.solve()` (async); frontend polls; no server push (WebSocket)

## Anti-Patterns

### In-Memory Store Over Database

**What happens:** In-flight schedules are held in `InMemoryScheduleStore` (a `ConcurrentHashMap`) during solving, not persisted until acceptance

**Why it's wrong:** Loss of schedule history; no recovery after server crash; violates audit trail expectations

**Do this instead:** Implement optional write-through caching to PostgreSQL (snapshot tables) if durability is needed; add recovery on startup to reload in-flight schedules from snapshots (`src/main/java/com/wfm/service/InMemoryScheduleStore.java`)

### Denormalized Snapshots at Accept Time

**What happens:** `AgentShiftAssignment` rows have nullable columns (`templateName`, `shiftStartTime`, etc.) populated only at accept time (D-07), left `null` during solve

**Why it's wrong:** Confuses readers about the entity's state; normalization principle violated; complicates queries

**Do this instead:** Create a separate `AcceptedShiftAssignmentSnapshot` entity; populate it at accept time instead of mutating the live entity; keep live entities clean

### Constraint Weights Coupling

**What happens:** `ConstraintWeights` is both a persisted entity and a `@ConstraintConfigurationProvider` fed directly into the solver

**Why it's wrong:** Configuration leaks into the domain model; schema changes to tuning require migrations

**Do this instead:** Separate `ConstraintWeightsEntity` (persisted) from `ConstraintWeightDTO` (passed to solver); map on load

## Error Handling

**Strategy:** Fail fast in pre-solve validation; return detailed error responses; log constraint violations and warnings

**Patterns:**

- **Pre-Solve Validation:** `SolverService.startSolve()` validates desk, agents, timeslots; throws `PreSolveValidationException` with `ErrorDetail[]` listing violations
- **Constraint Violations:** `ScheduleOutputService.buildAcceptedConstraintViolations()` reuses Timefold's `SolutionManager` to compute post-solve violations
- **Warnings:** `Schedule.warnings` list accumulates non-fatal issues (e.g., "Agent X has no shift template available")
- **Global Error Handler:** `GlobalExceptionHandler` catches exceptions and returns structured `ErrorResponse` with HTTP status

## Cross-Cutting Concerns

**Logging:** SLF4J with `@Slf4j` on service classes; solver progress logged at INFO; errors at ERROR
**Validation:** `@Valid` on request bodies; `PreSolveValidationException` for domain-level validation; Timefold constraints validate on every move
**Authentication:** None (dev environment assumes admin access); `TenantContext` provides tenant isolation
**Multi-Tenancy:** Via `TenantFilter` (servlet filter), every request populates thread-local `TenantContext.tenantId`; all queries filter by tenant

---

*Architecture analysis: 2026-09-17*
