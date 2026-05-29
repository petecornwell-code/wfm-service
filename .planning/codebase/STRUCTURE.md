# Codebase Structure

**Analysis Date:** 2026-04-02

## Directory Layout

```
wfm-service/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   ├── com/wfm/
│   │   │   │   ├── WfmApplication.java          # Spring Boot entry point
│   │   │   │   ├── config/                      # Filters, CORS, JSON config
│   │   │   │   ├── controller/                  # REST controllers + exception handler
│   │   │   │   ├── dto/                         # Request/response DTOs
│   │   │   │   ├── exception/                   # Typed business exceptions
│   │   │   │   ├── integration/                 # BambooHR client + refresh service
│   │   │   │   ├── model/                       # JPA entities + Timefold planning model
│   │   │   │   ├── repository/                  # Spring Data JPA repositories
│   │   │   │   ├── service/                     # Business logic, solver lifecycle
│   │   │   │   ├── solver/                      # Constraint provider + CH helpers
│   │   │   │   └── util/                        # Pagination, spreadsheet tools
│   │   │   └── utils/                           # Standalone BambooHR utility scripts
│   │   └── resources/
│   │       ├── application.yml                  # App configuration
│   │       ├── solverConfig.xml                 # Timefold solver phases
│   │       ├── db/migration/                    # Flyway migration scripts (V1–V24)
│   │       └── sample-data/                     # Excel sample files for dev/testing
│   └── test/
│       └── java/com/wfm/
│           ├── WfmApplicationTests.java         # Context load smoke test
│           ├── service/                         # Service-layer unit tests
│           └── solver/                          # Solver integration/scale tests
├── frontend/
│   ├── src/
│   │   ├── App.tsx                              # Root routing, tenant selector
│   │   ├── api/client.ts                        # Typed API client (all endpoints)
│   │   ├── components/                          # Shared UI components
│   │   └── pages/                               # One file per page/route
│   ├── package.json
│   └── vite.config.ts (or similar)
├── infra/                                       # Terraform AWS deployment modules
├── .github/workflows/                           # CI/CD pipelines
├── build.gradle                                 # Gradle build config
├── settings.gradle
├── Dockerfile
├── spec.md                                      # Full system specification
├── user_manual.md
└── deployment_plan.md
```

## Directory Purposes

**`src/main/java/com/wfm/config/`:**
- Purpose: Cross-cutting request infrastructure
- Contains: `TenantFilter` (servlet filter, order 1), `TenantContext` (ThreadLocal holder), `CorsConfig`, `JacksonConfig` (custom serializers), `HardSoftScoreSerializer`, `HardSoftScoreDeserializer`
- Key files: `TenantFilter.java`, `TenantContext.java`

**`src/main/java/com/wfm/controller/`:**
- Purpose: All REST endpoints under `/api/v1/`
- Contains: One `@RestController` per resource; `GlobalExceptionHandler` catches all exceptions
- Key files: `ScheduleController.java`, `AgentController.java`, `DeskController.java`, `DeskAgentController.java`, `StaffingRequirementController.java`, `TimeslotController.java`, `ConstraintWeightsController.java`, `GlobalExceptionHandler.java`

**`src/main/java/com/wfm/dto/`:**
- Purpose: Wire-format objects — no business logic
- Contains: Request records (validated with Jakarta annotations), response classes/records, paginated wrappers
- Key files: `SolveRequest.java`, `ScheduleDetailResponse.java`, `ScheduleSummary.java`, `PaginatedResponse.java`, `ErrorResponse.java`

**`src/main/java/com/wfm/exception/`:**
- Purpose: Typed exceptions the service layer throws; mapped to HTTP codes by `GlobalExceptionHandler`
- Contains: `EntityNotFoundException`, `ConflictException`, `UnprocessableException`, `PreSolveValidationException`, `RefreshInProgressException`

**`src/main/java/com/wfm/integration/`:**
- Purpose: External BambooHR API interaction and agent sync
- Contains: `BambooHRClient` (interface), `HttpBambooHRClient` (production), `MockBambooHRClient` (dev/test stub, active when `bamboohr.mock=true`), `DelegatingBambooHRClient` (selects impl), `BambooRefreshService` (orchestrates refresh), `BambooEmployee`, `BambooTimeOff` (data classes)

**`src/main/java/com/wfm/model/`:**
- Purpose: Domain entities and Timefold planning model — dual-annotated with JPA and Timefold annotations
- Contains: All `@Entity` classes, enums (`ScheduleStatus`, `DayOffType`, `DayOffStatus`, `BreakAlignment`, `MatchType`, `StaffingSource`), problem fact records (`ScheduleConfig`, `TimeslotDemandConfig`, `AgentDayConfig`)
- Key files: `Schedule.java`, `AgentAssignment.java`, `Agent.java`, `Timeslot.java`, `StaffingRequirement.java`, `ConstraintWeights.java`, `AgentPreference.java`, `AgentDayOff.java`, `AgentException.java`, `AcceptedScheduleDate.java`

**`src/main/java/com/wfm/repository/`:**
- Purpose: Data access — one Spring Data JPA interface per entity
- Contains: `ScheduleRepository`, `AgentRepository`, `TimeslotRepository`, `StaffingRequirementRepository`, `AgentAssignmentRepository`, `AgentPreferenceRepository`, `AgentDayOffRepository`, `AgentExceptionRepository`, `DeskRepository`, `SpecializationRepository`, `ConstraintWeightsRepository`, `AcceptedScheduleDateRepository`, `AppConfigurationRepository`

**`src/main/java/com/wfm/service/`:**
- Purpose: All business logic; no HTTP concerns
- Contains: `SolverService` (pre-solve validation, data loading, solver lifecycle), `ScheduleService` (list/get/accept/reject/delete), `ScheduleOutputService` (staffing summary, agent schedule, preference report, constraint violations), `AgentService`, `DeskService`, `DeskAgentService`, `SpecializationService`, `StaffingRequirementService`, `TimeslotGeneratorService`, `ErlangXService`, `BambooRefreshService` (in integration), `InMemoryScheduleStore`, `ConstraintWeightsService`, export services (`ScheduleExportService`, `DeskAgentExportService`, `ClientManagementExportService`), upload services (`FteUploadService`, `PreferenceUploadService`, `DeskAssignmentUploadService`)
- Key files: `SolverService.java`, `ScheduleService.java`, `ScheduleOutputService.java`, `InMemoryScheduleStore.java`, `ErlangXService.java`

**`src/main/java/com/wfm/solver/`:**
- Purpose: Timefold constraint definitions and solver helpers
- Contains: `ScheduleConstraintProvider` (implements `ConstraintProvider`, 18 constraints), `BreakAwareConstructionPhase` (no-op — delegates all to Timefold CH), `AgentAssignmentDifficultyComparator`, `plugin/CustomConstraint` (extension point)
- Key files: `ScheduleConstraintProvider.java`

**`src/main/java/com/wfm/util/`:**
- Purpose: Stateless helper classes
- Contains: `CursorPagination` (encode/decode cursor, clamp limit, build page), `BigDecimals` (rounding helpers), `FteSpreadsheetGenerator` (standalone Gradle task to generate sample Excel files)

**`src/main/java/utils/`** (note: outside `com.wfm` package):
- Purpose: Standalone developer utilities, not part of the application
- Contains: `BambooCustomFields.java`, `BambooEmployeesByDepartment.java`
- Not auto-scanned by Spring; used as standalone scripts only

**`src/main/resources/db/migration/`:**
- Purpose: Flyway versioned schema migrations
- Contains: V1–V24 SQL scripts; naming convention `V{N}__{description}.sql`
- Current schema version: V24 (enables pgvector extension)

**`src/main/resources/sample-data/`:**
- Purpose: Excel files used during development for FTE upload testing
- Generated: Can be regenerated via `./gradlew generateFteSpreadsheet`
- Committed: Yes (but not referenced by production code paths)

**`frontend/src/api/`:**
- Purpose: Single typed API client module
- Contains: `client.ts` — all API calls, TypeScript interfaces for every response type, `X-Tenant-ID` header injection, `ApiRequestError` typed error class, `getErrorMessage` helper

**`frontend/src/pages/`:**
- Purpose: One file per application screen
- Contains: `DeskSelector`, `DeskManagement`, `ScheduleSetup`, `ScheduleResults`, `DeskAgents`, `AgentPreferences`, `AgentExceptions`, `Specializations`, `StaffingRequirements`, `ConstraintWeightsPage`, `Configuration`, `ClientManagement`

**`frontend/src/components/`:**
- Purpose: Shared UI components
- Contains: `Toast.tsx` (toast notification system used across pages)

**`infra/`:**
- Purpose: Terraform modules for AWS deployment
- Contains: `vpc.tf`, `ecs.tf`, `rds.tf`, `ecr.tf`, `alb.tf`, `s3_cloudfront.tf`, `iam.tf`, `security_groups.tf`, `main.tf`, `variables.tf`, `outputs.tf`, `terraform.tfvars.example`
- Not part of the application runtime

## Key File Locations

**Entry Points:**
- `src/main/java/com/wfm/WfmApplication.java`: Spring Boot main class
- `frontend/src/main.tsx`: React app mount point
- `frontend/src/App.tsx`: Root router and tenant selector

**Configuration:**
- `src/main/resources/application.yml`: All runtime config (DB, CORS, BambooHR, solver, Timefold, logging, actuator)
- `src/main/resources/solverConfig.xml`: Timefold solver phases (CH + local search)
- `build.gradle`: Java dependencies and Gradle tasks

**Core Logic:**
- `src/main/java/com/wfm/service/SolverService.java`: Pre-solve validation, data loading, solver startup
- `src/main/java/com/wfm/service/ScheduleService.java`: Schedule lifecycle (list, get, accept, reject, delete)
- `src/main/java/com/wfm/solver/ScheduleConstraintProvider.java`: All 18 optimization constraints
- `src/main/java/com/wfm/service/ScheduleOutputService.java`: Derives staffing summary, agent schedule, preference report, constraint violations from raw assignments
- `src/main/java/com/wfm/service/InMemoryScheduleStore.java`: Thread-safe in-memory store for in-flight solves
- `src/main/java/com/wfm/service/ErlangXService.java`: Erlang X staffing calculation

**Database Schema:**
- `src/main/resources/db/migration/V1__initial_schema.sql`: Baseline schema
- `src/main/resources/db/migration/V20__accepted_schedule_per_desk_date.sql`: Accepted schedule date tracking

**Testing:**
- `src/test/java/com/wfm/solver/`: Solver integration tests (scale, constraints, single-day)
- `src/test/java/com/wfm/service/`: Service unit tests

## Naming Conventions

**Java files:**
- Entities: `PascalCase` matching table name in singular form (`Agent`, `Timeslot`, `Schedule`)
- Services: `{Domain}Service.java`
- Controllers: `{Domain}Controller.java`
- Repositories: `{Entity}Repository.java`
- DTOs: `{Domain}Request.java` / `{Domain}Response.java`
- Export services: `{Domain}ExportService.java`
- Upload services: `{Domain}UploadService.java`

**API routes:**
- All under `/api/v1/`
- Desk-scoped resources: `/api/v1/desks/{deskId}/{resource}`
- Tenant-scoped resources: `/api/v1/{resource}` (agents, days-off)

**Database tables:**
- `snake_case` singular names matching entity class names
- All tables include `tenant_id` column for multi-tenancy

## Where to Add New Code

**New REST endpoint:**
- Add controller: `src/main/java/com/wfm/controller/{Name}Controller.java`
- Add service: `src/main/java/com/wfm/service/{Name}Service.java`
- Add DTOs: `src/main/java/com/wfm/dto/{Name}Request.java` and `{Name}Response.java`

**New entity:**
- Add model: `src/main/java/com/wfm/model/{Name}.java`
- Add repository: `src/main/java/com/wfm/repository/{Name}Repository.java`
- Add Flyway migration: `src/main/resources/db/migration/V{N+1}__{description}.sql`

**New solver constraint:**
- Add to `src/main/java/com/wfm/solver/ScheduleConstraintProvider.java` — register in `defineConstraints()` return array
- Add weight field to `src/main/java/com/wfm/model/ConstraintWeights.java` with `@ConstraintWeight`
- Add migration to set the default weight value in existing rows

**New frontend page:**
- Add page component: `frontend/src/pages/{PageName}.tsx`
- Register route in `frontend/src/App.tsx`
- Add API calls to `frontend/src/api/client.ts`

**New BambooHR operation:**
- Add method to `src/main/java/com/wfm/integration/BambooHRClient.java` interface
- Implement in `HttpBambooHRClient.java` and `MockBambooHRClient.java`
- Orchestrate in `BambooRefreshService.java`

**Utilities:**
- Shared helpers: `src/main/java/com/wfm/util/`
- Frontend shared components: `frontend/src/components/`

## Special Directories

**`build/`:**
- Purpose: Gradle build output
- Generated: Yes
- Committed: No

**`.planning/codebase/`:**
- Purpose: AI-generated codebase analysis documents consumed by planning tools
- Generated: Yes (by `/gsd:map-codebase`)
- Committed: Yes (tracked in git)

**`infra/`:**
- Purpose: Terraform AWS infrastructure definitions
- Generated: No (handwritten)
- Committed: Yes

**`src/main/java/utils/`:**
- Purpose: Standalone developer scripts outside the Spring package hierarchy
- Generated: No
- Part of application: No (not Spring-managed)

---

*Structure analysis: 2026-04-02*
