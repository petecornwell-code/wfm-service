---
last_mapped_commit: 7e18ca2766e5fc34d93136d520c5a1529f8dc3b5
last_mapped_at: 2026-09-17
---
# Codebase Structure

**Analysis Date:** 2026-09-17

## Directory Layout

```
wfm-service/
├── src/main/java/com/wfm/
│   ├── WfmApplication.java                 # Spring Boot entry point
│   │
│   ├── controller/                         # HTTP request handlers
│   │   ├── ScheduleController.java
│   │   ├── DeskController.java
│   │   ├── DeskAgentController.java
│   │   ├── ShiftTemplateController.java
│   │   ├── ShiftLibraryValidationController.java
│   │   ├── AgentController.java
│   │   ├── ConstraintWeightsController.java
│   │   └── ... (13 total)
│   │
│   ├── service/                            # Business logic layer
│   │   ├── SolverService.java             # Core solve orchestration (2164 lines)
│   │   ├── ScheduleOutputService.java     # Post-solve reporting (975 lines)
│   │   ├── ScheduleExportService.java     # Excel/CSV export
│   │   ├── ShiftTemplateService.java      # Shift template CRUD
│   │   ├── ShiftLibraryGenerationService.java  # Auto-generate templates
│   │   ├── ShiftLibraryValidationService.java  # Validate templates
│   │   ├── UsualShiftService.java         # Agent usual-shift preferences
│   │   ├── UsualShiftResolutionService.java   # Resolve targets per agent-day
│   │   ├── DeskAgentService.java          # Agent-desk assignments
│   │   ├── DeskAgentExportService.java    # Export agent lists
│   │   ├── DeskService.java               # Desk CRUD
│   │   ├── ConstraintWeightsService.java  # Constraint weight tuning
│   │   ├── AgentService.java              # Agent CRUD
│   │   ├── InMemoryScheduleStore.java     # In-flight schedule queue
│   │   └── ... (20 total)
│   │
│   ├── solver/                            # Timefold constraint provider
│   │   ├── ScheduleConstraintProvider.java # 24 constraints (1223 lines)
│   │   ├── AgentAssignmentDifficultyComparator.java
│   │   ├── BreakAwareConstructionPhase.java
│   │   └── plugin/
│   │       └── ... (solver SPI implementations)
│   │
│   ├── model/                             # Domain entities (JPA + Timefold)
│   │   ├── Schedule.java                  # @PlanningSolution root
│   │   ├── AgentAssignment.java           # @PlanningEntity (slot-based)
│   │   ├── AgentShiftAssignment.java      # @PlanningEntity (shift-based, Phase 15)
│   │   ├── ShiftTemplate.java             # Shift pattern definition
│   │   ├── ShiftBandPair.java             # (template, band) pair
│   │   ├── ShiftTemplateBreakBand.java    # Break geometry per band
│   │   ├── Agent.java                     # Worker entity
│   │   ├── AgentDayConfig.java            # Per-agent-day contracted hours
│   │   ├── AgentUsualShift.java           # Stored preference per agent-weekday
│   │   ├── ResolvedUsualShiftTarget.java  # Pre-solve-built target per agent-date
│   │   ├── ScheduleConfig.java            # Transient problem fact
│   │   ├── ConstraintWeights.java         # Constraint penalties
│   │   ├── Timeslot.java                  # Time slot entity
│   │   ├── Specialization.java            # Skill/role entity
│   │   ├── StaffingRequirement.java       # Demand per timeslot-spec
│   │   ├── AgentPreference.java           # Preferred start/break times
│   │   ├── AgentDayOff.java               # PTO/unavailable days
│   │   ├── AgentException.java            # Override effective hours
│   │   ├── Desk.java                      # Workplace/call center
│   │   └── ... (20+ total)
│   │
│   ├── dto/                               # Request/response objects
│   │   ├── SolveRequest.java
│   │   ├── ScheduleDetailResponse.java
│   │   ├── ScheduleSummary.java
│   │   ├── ConstraintWeightsDto.java
│   │   ├── ShiftTemplateResponse.java
│   │   ├── DeskAgentResponse.java
│   │   └── ... (32 total)
│   │
│   ├── repository/                       # Data access (Spring Data JPA)
│   │   ├── ScheduleRepository.java
│   │   ├── AgentAssignmentRepository.java
│   │   ├── AgentShiftAssignmentRepository.java
│   │   ├── ShiftTemplateRepository.java
│   │   ├── DeskRepository.java
│   │   ├── AgentRepository.java
│   │   ├── AgentUsualShiftRepository.java
│   │   ├── ConstraintWeightsRepository.java
│   │   └── ... (21 total)
│   │
│   ├── config/                           # Spring configuration
│   │   ├── TenantContext.java            # Thread-local tenant ID
│   │   ├── TenantFilter.java             # Servlet filter for multi-tenancy
│   │   ├── JacksonConfig.java            # Custom JSON serializers
│   │   ├── CorsConfig.java               # CORS setup
│   │   └── HardSoftScore(De)Serializer.java
│   │
│   ├── util/                             # Shared utilities
│   │   ├── BigDecimals.java              # Decimal helpers
│   │   ├── AgentNameSplitter.java
│   │   ├── EnrichedColumnLayout.java
│   │   └── FteSpreadsheetGenerator.java
│   │
│   ├── integration/                      # Third-party integrations
│   │   ├── BambooRefreshService.java     # BambooHR sync
│   │   ├── HttpBambooHRClient.java
│   │   └── ... (data merge services)
│   │
│   └── exception/                        # Custom exceptions
│       ├── ConflictException.java
│       ├── EntityNotFoundException.java
│       └── PreSolveValidationException.java
│
├── src/main/resources/
│   ├── application.properties             # Spring Boot config (dev, auth, DB)
│   ├── application-prod.properties
│   └── db/migration/                      # Flyway migrations (V1-V43+)
│       ├── V1__Initial_schema.sql
│       ├── V15__Phase_15_shift_envelope.sql
│       ├── V17__Phase_17_usual_shifts.sql
│       └── ... (versioned DDL scripts)
│
├── src/test/java/com/wfm/
│   ├── solver/                           # Constraint tests
│   │   ├── ScheduleConstraintClassification.java
│   │   ├── ShiftEnvelopeComplianceConstraintTest.java
│   │   ├── UsualShiftConsistencyConstraintTest.java
│   │   ├── ShiftWorkContiguityConstraintTest.java
│   │   └── ... (35+ constraint/solver tests)
│   │
│   ├── integration/                      # Integration tests
│   │   ├── MergeReportTest.java
│   │   ├── BambooRefreshServiceTest.java
│   │   └── ... (data merge, import/export)
│   │
│   ├── repository/                       # DB tests (with Testcontainers)
│   │   ├── AgentRepositoryPostgresTest.java
│   │   └── ... (PostgreSQL-backed tests)
│   │
│   └── ... (config, util tests)
│
├── .planning/                            # Internal project tracking
│   ├── codebase/
│   │   ├── ARCHITECTURE.md              # (generated)
│   │   └── STRUCTURE.md                 # (generated)
│   └── ... (milestones, progress, debug)
│
├── frontend/
│   ├── src/
│   │   ├── pages/
│   │   │   ├── ScheduleSetup.tsx         # Initiate solve
│   │   │   ├── ScheduleResults.tsx       # Display results, accept/reject
│   │   │   ├── DeskAgents.tsx            # Manage desk assignments
│   │   │   ├── ShiftLibrary.tsx          # Design shift templates
│   │   │   ├── ConstraintWeightsPage.tsx # Tune weights
│   │   │   ├── ClientManagement.tsx      # Tenant config
│   │   │   └── ... (13 pages)
│   │   │
│   │   ├── api/
│   │   │   └── client.ts                 # Auto-generated REST client (29KB)
│   │   │
│   │   ├── components/
│   │   │   └── ... (shared React components)
│   │   │
│   │   ├── App.tsx                       # Root component
│   │   └── main.tsx
│   │
│   ├── package.json                      # React + TypeScript + Vite
│   ├── tsconfig.json
│   └── dist/                             # Built artifacts (post-build)
│
├── infra/                                # Infrastructure as Code
│   ├── docker-compose.yml               # Postgres + app stack
│   ├── k8s/                             # Kubernetes manifests
│   └── ... (Terraform/Helm if present)
│
├── build.gradle                          # Gradle build config
├── settings.gradle
├── gradlew / gradlew.bat                # Gradle wrapper
├── Dockerfile                            # Docker image definition
└── README.md, spec.md, TODO.md           # Documentation
```

## Directory Purposes

**`src/main/java/com/wfm/controller/`:**

- Purpose: HTTP request handlers; translate REST calls to service method invocations
- Contains: 13 `@RestController` classes
- Key files: `ScheduleController.java`, `DeskAgentController.java`, `ShiftTemplateController.java`

**`src/main/java/com/wfm/service/`:**

- Purpose: Business logic orchestration; database queries via repositories; calls to Timefold solver
- Contains: 20 `@Service` classes ranging from 1.8KB to 126KB
- Key files: `SolverService.java` (2164 lines, core), `ScheduleOutputService.java` (975 lines, reporting), `ShiftLibrary*.java` (validation and generation)

**`src/main/java/com/wfm/solver/`:**

- Purpose: Constraint definition and scoring; construction heuristics
- Contains: `ScheduleConstraintProvider.java` (1223 lines, 24 constraints), difficulty comparator, break-aware construction
- Key files: `ScheduleConstraintProvider.java` (all hard/soft constraints)

**`src/main/java/com/wfm/model/`:**

- Purpose: Domain entity definitions with JPA and Timefold metadata
- Contains: 39 entity classes (agents, assignments, shifts, templates, configurations, enums)
- Key files: `Schedule.java` (root planning solution), `AgentAssignment.java` and `AgentShiftAssignment.java` (planning entities)

**`src/main/java/com/wfm/dto/`:**

- Purpose: Request/response data transfer objects, isolated from entities
- Contains: 32 request/response classes
- Key files: `SolveRequest.java`, `ScheduleDetailResponse.java`, `ConstraintWeightsDto.java`

**`src/main/java/com/wfm/repository/`:**

- Purpose: Spring Data JPA repository interfaces; database query abstraction
- Contains: 21 repository interfaces with custom query methods
- Key files: `ScheduleRepository.java`, `AgentRepository.java`, `AgentShiftAssignmentRepository.java`

**`src/main/java/com/wfm/config/`:**

- Purpose: Spring Boot configuration and cross-cutting setup
- Contains: Multi-tenancy context, CORS, JSON serialization, TenantFilter servlet
- Key files: `TenantContext.java` (thread-local tenant), `TenantFilter.java` (servlet filter)

**`src/main/java/com/wfm/integration/`:**

- Purpose: External system integrations (currently BambooHR PTO sync)
- Contains: HTTP clients, merge logic, refresh services
- Key files: `BambooRefreshService.java`, `HttpBambooHRClient.java`

**`src/main/resources/db/migration/`:**

- Purpose: Flyway database migrations; versioned schema and data DDL
- Contains: 43+ SQL migration files (V1 through V43+)
- Key files: `V1__Initial_schema.sql`, `V15__Phase_15_shift_envelope.sql`, `V17__Phase_17_usual_shifts.sql`

**`src/test/java/com/wfm/solver/`:**

- Purpose: Unit and integration tests for constraints and solver behavior
- Contains: 35+ test classes covering all constraints, solver configuration, benchmark tests
- Key files: `ShiftEnvelopeComplianceConstraintTest.java`, `UsualShiftConsistencyConstraintTest.java`, `ShiftWorkContiguityConstraintTest.java`

**`src/test/java/com/wfm/repository/`:**

- Purpose: PostgreSQL-backed integration tests using Testcontainers
- Contains: Tests requiring a real database (vs. H2 in-memory)
- Key files: `AgentRepositoryPostgresTest.java`, `AgentUsualShiftPostgresTest.java`

**`frontend/src/pages/`:**

- Purpose: React page components for major workflows
- Contains: 13 `.tsx` components
- Key files: `ScheduleSetup.tsx` (initiate), `ScheduleResults.tsx` (review/accept), `ShiftLibrary.tsx` (design), `DeskAgents.tsx` (assign)

**`frontend/src/api/`:**

- Purpose: REST client for backend communication
- Contains: Auto-generated TypeScript client (from OpenAPI schema)
- Key files: `client.ts` (29KB, all API methods)

## Key File Locations

**Entry Points:**

- `src/main/java/com/wfm/WfmApplication.java` — Spring Boot `main()` entry point
- `frontend/src/main.tsx` — React root entry point
- `frontend/src/App.tsx` — Root component router

**Configuration:**

- `src/main/resources/application.properties` — Spring Boot application config (dev, profiles, DB connection, solver time limit)
- `build.gradle` — Gradle dependencies and build tasks
- `frontend/package.json` — Node.js dependencies and build scripts

**Core Logic:**

- `src/main/java/com/wfm/service/SolverService.java` — Schedule building, solve initiation, acceptance
- `src/main/java/com/wfm/solver/ScheduleConstraintProvider.java` — All 24 constraints
- `src/main/java/com/wfm/service/ScheduleOutputService.java` — Result reporting and visualization data
- `src/main/java/com/wfm/service/ShiftLibraryGenerationService.java` — Auto-generate shift templates

**Testing:**

- `src/test/java/com/wfm/solver/` — Constraint tests (35+)
- `src/test/java/com/wfm/repository/` — PostgreSQL integration tests
- `src/test/java/com/wfm/integration/` — BambooHR sync, merge logic tests

## Naming Conventions

**Files:**

- `*.java` — Java source files (classes, interfaces)
- `*.tsx` — React TypeScript components (pages, components)
- `*.ts` — TypeScript utilities and clients
- `*Test.java` — Unit/integration test classes
- `*Test.ts` — Frontend test files (if present)
- Suffix patterns: `*Service.java` (business logic), `*Repository.java` (data access), `*Controller.java` (HTTP handlers), `*Response.java` (DTOs), `*Exception.java` (custom exceptions)

**Directories:**

- `src/main/` — Production source code
- `src/test/` — Test source code
- `src/main/java/com/wfm/{controller,service,solver,model,dto,repository}/` — Logical layers
- `src/main/resources/` — Configuration files, migration scripts
- `frontend/src/{pages,components,api}/` — Frontend feature organization

**Classes:**

- `*Service.java` — Spring `@Service` classes (business logic)
- `*Repository.java` — Spring Data `@Repository` interfaces
- `*Controller.java` — Spring `@RestController` HTTP handlers
- `*Exception.java` — Custom checked exceptions
- `*Response.java` / `*Request.java` — DTOs
- `*Provider.java` — Timefold `ConstraintProvider`, plugin interfaces
- `*Factory.java` — Object factory methods
- `*Comparator.java` — Difficulty ordering for solver

**Functions/Methods:**

- camelCase: `startSolve()`, `buildSchedule()`, `acceptSchedule()`
- Verbs for operations: `create*()`, `update*()`, `delete*()`, `fetch*()`, `validate*()`
- Adjectives for queries: `find*()`, `get*()`
- Boolean prefixes: `is*()`, `has*()`, `can*()`

**Variables:**

- camelCase: `scheduleId`, `agentId`, `tenantId`
- Prefix for collections: `agents`, `assignments`, `timeslots` (plural)
- Prefix for optional: `optional*`, `maybe*` (rare; prefer explicit nullability)

**Types/Enums:**

- PascalCase: `Schedule`, `Agent`, `AgentAssignment`, `ScheduleStatus`, `SchedulingMode`
- Enum values: UPPERCASE with underscores: `SLOT`, `SHIFT`, `RUNNING`, `ACCEPTED`

## Where to Add New Code

**New Feature (e.g., a new reporting endpoint):**

- Primary code: `src/main/java/com/wfm/service/ScheduleOutputService.java` (add report builder method)
- Controller endpoint: `src/main/java/com/wfm/controller/ScheduleController.java` (add `@GetMapping` or `@PostMapping`)
- DTO: `src/main/java/com/wfm/dto/ScheduleDetailResponse.java` (add nested response class)
- Tests: `src/test/java/com/wfm/service/` (add `*Test.java` class or method)
- Frontend: `frontend/src/pages/ScheduleResults.tsx` (add new tab or section)

**New Constraint:**

- Implementation: `src/main/java/com/wfm/solver/ScheduleConstraintProvider.java` (add `private Constraint xyz(ConstraintFactory factory)` method)
- Add to `defineConstraints()` array return
- Weights: Add configuration to `src/main/java/com/wfm/model/ConstraintWeights.java` (new field + getter)
- Database: Add migration in `src/main/resources/db/migration/V*__*.sql` (add column to `constraint_weights` table)
- Tests: `src/test/java/com/wfm/solver/XyzConstraintTest.java` (new test class following `*ConstraintTest` pattern)

**New Planning Entity (new decision variable type):**

- Model: `src/main/java/com/wfm/model/NewEntity.java` (annotate with `@PlanningEntity`, `@PlanningVariable`)
- Repository: `src/main/java/com/wfm/repository/NewEntityRepository.java` (Spring Data interface)
- Service integration: `src/main/java/com/wfm/service/SolverService.java` (populate in `buildSchedule()`)
- Constraint impact: Review and update constraints in `ScheduleConstraintProvider.java`
- Database: Add migration (`V*__*.sql` DDL for new table)
- Tests: `src/test/java/com/wfm/solver/NewEntityConstraintTest.java`

**New Agent or Desk Management Feature:**

- Service: `src/main/java/com/wfm/service/DeskAgentService.java` or new `*Service.java`
- Repository: `src/main/java/com/wfm/repository/DeskAgentRepository.java` or extend existing
- Controller: `src/main/java/com/wfm/controller/DeskAgentController.java` (add endpoint)
- Frontend: `frontend/src/pages/DeskAgents.tsx` (add section or page)

**Utilities and Helpers:**

- Shared helpers: `src/main/java/com/wfm/util/` (new `Util*.java` or extend existing)
- Frontend utilities: `frontend/src/api/client.ts` (if API-related) or `frontend/src/pages/` (if feature-specific)

**Database Schema Change:**

- Always add Flyway migration: `src/main/resources/db/migration/V{nextVersion}__Description.sql`
- Update model entity if adding JPA-mapped column: `src/main/java/com/wfm/model/*.java`
- Update repository queries if schema affects queries
- Add test in `src/test/java/com/wfm/repository/*PostgresTest.java` if Postgres-specific

## Special Directories

**`.planning/`:**

- Purpose: Internal project tracking and GSD milestone files
- Generated: No (manually maintained by project team via GSD)
- Committed: Yes
- Contains: Milestones, phase specs, progress logs, debug artifacts, codebase analysis (ARCHITECTURE.md, STRUCTURE.md, etc.)

**`build/`:**

- Purpose: Gradle build output directory
- Generated: Yes (by `gradle build`)
- Committed: No (gitignored)
- Contains: Compiled classes, JAR artifacts, reports

**`frontend/dist/`:**

- Purpose: Built React application artifacts
- Generated: Yes (by `npm run build` or Vite build)
- Committed: No (gitignored)
- Contains: Bundled JavaScript, CSS, static assets

**`frontend/node_modules/`:**

- Purpose: npm installed dependencies
- Generated: Yes (by `npm install`)
- Committed: No (gitignored)
- Contains: Third-party JavaScript libraries

**`.git/`:**

- Purpose: Git version control metadata
- Generated: Yes (by `git init` or `git clone`)
- Committed: No (internal to Git)
- Contains: Commit history, branches, remotes

**`infra/`:**

- Purpose: Infrastructure-as-Code and deployment configurations
- Generated: No (manually maintained)
- Committed: Yes
- Contains: Docker Compose, Kubernetes manifests, Terraform or Helm charts (if present)

**`src/main/resources/db/migration/`:**

- Purpose: Flyway database schema migrations
- Generated: No (manually written per feature)
- Committed: Yes (critical for data consistency)
- Contains: SQL migration files versioned by Flyway

---

*Structure analysis: 2026-09-17*
