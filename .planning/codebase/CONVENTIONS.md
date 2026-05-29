# Coding Conventions

**Analysis Date:** 2026-04-02

## Naming Patterns

**Files:**
- Classes: `PascalCase` matching the class name exactly (e.g., `AgentService.java`, `ScheduleConstraintProvider.java`)
- Interfaces: `PascalCase`, often ending in `Client` or `Repository` (e.g., `BambooHRClient.java`, `AgentRepository.java`)
- Enums: `PascalCase` (e.g., `BreakAlignment.java`, `DayOffType.java`)
- Outlier: two standalone utility classes live under `src/main/java/utils/` (no package prefix) rather than `com.wfm.*`

**Classes:**
- Controllers: `*Controller` (e.g., `AgentController`, `ScheduleController`)
- Services: `*Service` (e.g., `AgentService`, `SolverService`)
- Repositories: `*Repository` (e.g., `AgentRepository`, `TimeslotRepository`)
- DTOs: noun + purpose suffix — `*Request`, `*Response`, `*Dto` (e.g., `SolveRequest`, `AgentResponse`, `ConstraintWeightsDto`)
- Exceptions: descriptive + `Exception` (e.g., `EntityNotFoundException`, `PreSolveValidationException`)
- Models/Entities: plain noun (e.g., `Agent`, `Schedule`, `Timeslot`)

**Methods:**
- camelCase throughout
- Boolean accessors: `is*` prefix for primitive booleans (`isActive()`), `get*` for object getters
- Service list methods: `list*` prefix (e.g., `listAgents`, `listDesks`)
- Service fetch methods: `get*` prefix (e.g., `getAgent`, `getSchedule`)
- Private helpers: descriptive verbs (e.g., `toResponse`, `buildPage`, `resolvePreferences`)

**Variables and Fields:**
- camelCase for instance fields and local variables
- `SCREAMING_SNAKE_CASE` for `static final` constants (e.g., `TENANT_HEADER`, `DEFAULT_LIMIT`, `MAX_LIMIT`)
- Test fixtures: short but descriptive local constants — `TENANT`, `DAY`, `START`, `END`, `INCREMENT`

**Types:**
- Entity IDs: `UUID` (generated via `@GeneratedValue(strategy = GenerationType.UUID)`)
- Tenant isolation: `long tenantId` on every entity and every repository query
- Monetary/hour values: `BigDecimal` with explicit precision/scale in `@Column` annotations
- Timestamps: `OffsetDateTime` for audit fields; `LocalDate` / `LocalTime` for schedule domain values

## Code Style

**Formatting:**
- No formatter configuration file present (no `.editorconfig`, no Checkstyle, no Spotless)
- Indentation: 4 spaces (consistent throughout all observed files)
- Opening braces on same line as declaration
- Single blank line between methods; section comments (`// ---`) used to group methods within long classes

**Linting:**
- No static analysis tooling configured (no PMD, SpotBugs, or Checkstyle in `build.gradle`)

## Import Organization

**Order (observed pattern):**
1. Framework/library imports (Spring, Timefold, Jakarta)
2. Project-internal imports (`com.wfm.*`)
3. Java standard library (`java.*`)
4. Static imports last (`import static ...`)

**Wildcard imports:**
- Used when importing many items from the same package (e.g., `import com.wfm.model.*` in test files, `import static ai.timefold.solver.core.api.score.stream.ConstraintCollectors.*`)

## Dependency Injection

- Constructor injection only — no field injection (`@Autowired` is not used)
- All dependencies declared `private final` and assigned in a single explicit constructor
- `@Value` used for externalized config (e.g., `@Value("${solver.time-limit:PT5M}") Duration defaultTimeLimit`)
- Spring beans annotated with `@Service`, `@RestController`, `@Component`, `@Configuration`, `@Repository`

## DTOs and Data Transfer

- Response DTOs are Java `record` types (immutable, no setters): e.g., `AgentResponse`, `SolveRequest`, `TimeslotResponse`
- Request bodies also use `record` where no validation annotations are needed
- Service `toResponse(Entity)` private helper methods convert entities to DTOs — never expose raw entities from controllers
- Pagination wrapped in `PaginatedResponse<T>` record (`src/main/java/com/wfm/dto/PaginatedResponse.java`) with cursor, hasMore, and total fields

## Model / Entity Design

- Plain Java classes (no Lombok) — explicit no-arg constructor, explicit getters/setters, one per line
- JPA annotations on fields, not on getter methods
- `@ManyToOne(fetch = FetchType.LAZY)` default for all associations
- `@EntityGraph` used on repository methods that need eager loading to avoid N+1 (e.g., `AgentRepository`)
- Timefold planning annotations (`@PlanningSolution`, `@PlanningEntity`, `@PlanningVariable`) co-located with JPA annotations on the same class

## Error Handling

**Strategy:** Custom exception hierarchy mapped to HTTP status codes in `src/main/java/com/wfm/controller/GlobalExceptionHandler.java`

**Exception types:**
- `EntityNotFoundException` → 404 NOT_FOUND
- `ConflictException` → 409 CONFLICT
- `RefreshInProgressException` → 409 CONFLICT (distinct error code `REFRESH_IN_PROGRESS`)
- `UnprocessableException` → 422 UNPROCESSABLE_ENTITY
- `PreSolveValidationException` → 400 VALIDATION_FAILED (with structured `ErrorDetail` list)
- `IllegalArgumentException` → 400 VALIDATION_FAILED
- Uncaught `Exception` → 500 INTERNAL_ERROR (logged via SLF4J)

**Pattern:** Exceptions are simple `RuntimeException` subclasses with message-only constructors. Structured details (`List<ErrorDetail>`) added only where field-level feedback is needed.

**Error response shape:**
```json
{ "error": { "code": "NOT_FOUND", "message": "...", "details": [] } }
```
Defined in `src/main/java/com/wfm/dto/ErrorResponse.java` as nested records (`ErrorResponse`, `Error`, `ErrorDetail`).

## Logging

**Framework:** SLF4J with Logback (via Spring Boot default)

**Declaration pattern:**
```java
private static final Logger log = LoggerFactory.getLogger(SolverService.class);
```

**Usage:**
- `log.error(...)` for uncaught exceptions in `GlobalExceptionHandler`
- `log.debug(...)` / `log.info(...)` in service layer for solver lifecycle events
- `System.out.println(...)` used inside solver tests for diagnostic output (score breakdowns, assignment counts) — not in production code

## Comments

**Javadoc:**
- Class-level Javadoc on test classes explaining scenario, agents, constraints, and why a solution is feasible
- Class-level Javadoc on utility classes explaining algorithm (e.g., `CursorPagination`)
- Method-level Javadoc on public utility methods and constraint methods in `ScheduleConstraintProvider`

**Inline comments:**
- Section dividers: `// --- Section Name ---` used to separate logical groups within long methods and classes
- Constraint methods in `src/main/java/com/wfm/solver/ScheduleConstraintProvider.java` each have a numbered Javadoc comment describing the constraint intent
- No TODO/FIXME/HACK/XXX comments found anywhere in the codebase

## Function Design

**Size:** Services have medium-length methods; complex orchestration (e.g., `SolverService`) broken into private helpers with descriptive names

**Parameters:** Constructor injection preferred over method parameters for dependencies; public method parameters kept minimal

**Return Values:** Services return DTOs or `void`; repositories return `Optional<T>` for single-entity lookups, `List<T>` for collections

## Module Design

**Package structure:**
- `com.wfm.controller` — REST layer (`src/main/java/com/wfm/controller/`)
- `com.wfm.service` — business logic (`src/main/java/com/wfm/service/`)
- `com.wfm.repository` — Spring Data JPA interfaces (`src/main/java/com/wfm/repository/`)
- `com.wfm.model` — JPA entities + Timefold planning model (`src/main/java/com/wfm/model/`)
- `com.wfm.dto` — request/response records (`src/main/java/com/wfm/dto/`)
- `com.wfm.exception` — custom exception types (`src/main/java/com/wfm/exception/`)
- `com.wfm.config` — Spring configuration and servlet filters (`src/main/java/com/wfm/config/`)
- `com.wfm.integration` — BambooHR client interface and implementations (`src/main/java/com/wfm/integration/`)
- `com.wfm.solver` — Timefold constraint provider and construction phase (`src/main/java/com/wfm/solver/`)
- `com.wfm.util` — stateless utility classes (`src/main/java/com/wfm/util/`)

---

*Convention analysis: 2026-04-02*
