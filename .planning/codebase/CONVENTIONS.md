---
last_mapped_commit: 7e18ca2766e5fc34d93136d520c5a1529f8dc3b5
last_mapped_at: 2026-09-17
---
# Coding Conventions

**Analysis Date:** 2026-09-17

## Naming Patterns

**Files:**

- Java classes: `PascalCase.java` (e.g., `DeskService.java`, `AgentRepository.java`)
- Test files: `{ClassName}Test.java` or `{ClassName}{Variant}Test.java` (e.g., `BandCapacityConstraintTest.java`, `UsualShiftWritePathTest.java`)
- DTOs: `{Name}{Purpose}.java` (e.g., `DeskRequest.java`, `DeskResponse.java`)
- TypeScript: `camelCase.ts` or `PascalCase.tsx` (e.g., `client.ts`, `Toast.tsx`)

**Packages:**

- Pattern: `com.wfm.{module}`
- Modules: `controller`, `service`, `model`, `repository`, `exception`, `dto`, `util`, `config`, `integration`, `solver`
- Example: `com.wfm.service.DeskService`, `com.wfm.repository.AgentRepository`

**Classes:**

- PascalCase
- Services: `{Entity}{Function}Service` (e.g., `DeskService`, `BambooRefreshService`, `UsualShiftService`)
- Repositories: `{Entity}Repository` (e.g., `DeskRepository`, `AgentRepository`, `ConstraintWeightsRepository`)
- Exceptions: `{Error}Exception` or `{Condition}Exception` (e.g., `EntityNotFoundException`, `ConflictException`, `PreSolveValidationException`)
- DTOs: `{Entity}{Type}` (e.g., `DeskRequest`, `DeskResponse`, `ConstraintWeightsDto`)
- Tests: `{SubjectClass}{TestType}` (e.g., `BandCapacityConstraintTest`, `SolverQualityGuardTest`)

**Functions/Methods:**

- camelCase with verb prefix for actions: `get*`, `list*`, `create*`, `update*`, `delete*`, `switch*`, `save*`, `find*`
- Examples: `getDeskId()`, `listDesks()`, `createDesk()`, `switchSchedulingMode()`, `refreshDeskAgents()`
- Test methods: `{subject}_{condition}_{expectedResult}[_{proofType}]` (e.g., `refreshDeskAgents_leavesStoredUsualShiftsByteIdentical_behavioural()`, `capacityN_exactlyNAgentDays_noPenalty()`)

**Variables & Fields:**

- Local variables: camelCase (e.g., `deskId`, `agentName`, `contractedHours`)
- Record fields: camelCase in `record` declarations (e.g., `new UsualShiftSnapshot(id, tenantId, agentId, dayOfWeek, shiftTemplateId)`)
- Instance fields: camelCase (e.g., `private DeskRepository deskRepository;`)
- Constants: assumed UPPER_SNAKE_CASE (rarely used in codebase; see `TENANT_ID = 1L;` in tests)

**Types (TypeScript):**

- Interfaces: PascalCase with suffix `Interface` when necessary (e.g., `ApiErrorBody`, `ToastMessage`, `ApiErrorDetail`)
- Enums: PascalCase, enum values UPPER_SNAKE_CASE (e.g., `type ToastType = 'success' | 'error' | 'warning'`)

## Code Style

**Formatting:**

- No ESLint or Prettier configuration detected in project root
- Backend: IntelliJ IDEA defaults (4-space indentation, Unix line endings)
- Frontend: TypeScript with Vite build (no enforced formatter)
- Line length: no strict limit observed; practical constraint ~120 chars in Java code

**Linting:**

- No eslint, biome, or checkstyle configuration detected
- Code review and test suite provide primary quality gates

**Indentation:**

- Java: 4 spaces
- TypeScript/React: 2 spaces (Vite/React convention)

## Import Organization

**Order (Java):**

1. Java/Jakarta standard library imports (`java.*`, `jakarta.*`)
2. Third-party framework imports (Spring, Timefold, Testcontainers, etc.)
3. Project imports (`com.wfm.*`)
4. Static imports (rare, placed last)

**Example from `DeskService.java`:**

```java
import com.wfm.config.TenantContext;
import com.wfm.exception.ConflictException;
import com.wfm.exception.EntityNotFoundException;
import com.wfm.model.ConstraintWeights;
import com.wfm.model.Desk;
import com.wfm.repository.AgentRepository;
import com.wfm.repository.*;
import com.wfm.util.BigDecimals;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
```

**Wildcard Imports:**

- Used sparingly in production code when many repository dependencies exist (e.g., `import com.wfm.repository.*;` in `DeskService`)
- Avoided in test files

**Path Aliases:**

- No path aliases configured in frontend `tsconfig.json` or backend build
- Absolute package names used throughout

## Error Handling

**Custom Exception Hierarchy:**

- All custom exceptions extend `RuntimeException` (unchecked)
- Located in `src/main/java/com/wfm/exception/`

**Exception Classes:**

- `EntityNotFoundException(String entityName, Object id)` — missing entity, e.g., `new EntityNotFoundException("Desk", deskId)`
- `ConflictException(String message)` — business logic violation, e.g., `new ConflictException("A desk with name '" + name + "' already exists")`
- `PreSolveValidationException(String message)` — pre-solve constraint failure
- `UnprocessableException(String message)` — malformed input or state
- `BambooHRSyncFailedException(String message)` — external integration failure
- `BambooHRRateLimitedException(String message)` — rate limit hit
- `RefreshInProgressException(String message)` — concurrent operation collision

**Validation Pattern:**

- Null/blank checks at method entry: `if (name == null || name.isBlank()) throw new IllegalArgumentException(...)`
- Business rule violations: throw specific custom exceptions, never IllegalArgumentException
- Example from `DeskService#createDesk`:

```java
if (name == null || name.isBlank()) {
    throw new IllegalArgumentException("Desk name is required");
}
if (deskRepository.existsByTenantIdAndName(tenantId, name)) {
    throw new ConflictException("A desk with name '" + name + "' already exists");
}
```

## Logging

**Framework:** `org.slf4j` (SLF4J, via Spring Boot starter)

**Patterns:**

- Logging calls not explicitly observed in code review (Spring Data and Timefold logs dominate output)
- Assume standard SLF4J conventions: `logger.debug()`, `logger.info()`, `logger.warn()`, `logger.error()`
- Error conditions logged by exception handlers, not at every throw site

## Comments

**When to Comment:**

- Complex business logic and architectural decisions (phased work, constraints, invariants)
- Why a choice was made, not what the code does
- Phase/plan references for traceability (e.g., "Phase 17 plan 17-01")
- Warnings about subtle bugs or side effects

**Example from `PostgresBackedTest`:**

```java
/**

 * Base class for tests that must run against a REAL Postgres with the REAL Flyway migrations
 * applied, rather than against H2 with a schema generated from the entity mappings.
 *
 * <p><b>Why this exists.</b> The rest of the suite runs on H2 with {@code ddl-auto: create-drop}
 * and {@code flyway.enabled: false}. That configuration is structurally blind to two whole classes
 * of defect, and both shipped:
 * <ul>
 *   <li><b>Postgres type resolution.</b> An untyped JDBC null reaches Postgres as {@code unknown}...</li>
 *   <li><b>The migrations themselves.</b> With {@code ddl-auto: create-drop} the test schema comes
 *       from the JPA entities...</li>
 * </ul>
 */
```

**JSDoc/Javadoc:**

- Extensive Javadoc on public methods, classes, and complex helper methods
- Format: `/** ... */` with `<p>`, `<b>`, `<ul>`, `<li>` for structure
- `{@code ...}` for code references, `{@link ...}` for cross-references
- Method docstrings include purpose, parameters (when not obvious), side effects, and examples

**Example from `UsualShiftWritePathTest`:**

```java
/**

 * Discharges table rows 5 (BambooHR refresh), 6 (mode switch) and 7 (the solver) of {@code
 * src/test/resources/ushf-05-write-paths.md} — the three USHF-05 paths no earlier plan runs.
 * ...
 */
@DataJpaTest
@Import({DeskService.class, InMemoryScheduleStore.class})
@ActiveProfiles("test")
class UsualShiftWritePathTest {
```

## Function Design

**Size:**

- Methods kept focused (50-150 lines typical for services, smaller for utilities)
- Complex business logic extracted to helper methods or services

**Parameters:**

- Constructor injection preferred for dependencies (Spring services)
- Method parameters: primitive types, collections, entities
- Avoid boolean parameters (use enums or builder pattern)
- Example: `void switchSchedulingMode(UUID deskId, SchedulingMode mode)` ✓

**Return Values:**

- Services return domain entities or DTOs, never raw database results
- Repositories return entities or `List<Entity>` or `Optional<Entity>`
- Controllers return DTOs (mapped from entities via helper methods)
- Example from `DeskController`:

```java
private DeskResponse toResponse(Desk desk) {
    return new DeskResponse(desk.getId(), desk.getName(), desk.getDescription(),
            desk.getDefaultContractedHoursPerDay(), desk.getSchedulingMode());
}
```

## Module Design

**Exports:**

- Each package exports its primary class (e.g., `DeskService` from `com.wfm.service`)
- Repositories exposed as Spring Data interfaces
- Exceptions public and imported by client code
- DTOs/models exposed via package-level classes

**Package Structure:**

- `com.wfm.model` — JPA entities, enums (no interfaces)
- `com.wfm.dto` — Request/Response records and DTOs
- `com.wfm.repository` — JPA repository interfaces (extends `JpaRepository`)
- `com.wfm.service` — business logic, transactional operations
- `com.wfm.controller` — REST endpoints, request mapping
- `com.wfm.exception` — custom exception classes
- `com.wfm.util` — stateless utility classes
- `com.wfm.config` — Spring configuration, context holders
- `com.wfm.integration` — external system clients (BambooHR)
- `com.wfm.solver` — Timefold constraint definitions and solvers

**No Barrel Files:**

- Each import is explicit (no `index.ts` exports in frontend; Java has no equivalent pattern)

## Annotation Usage

**Spring Framework:**

- `@Service` — service classes for business logic
- `@Repository` — DAO/repository layer (often implicit with `JpaRepository`)
- `@RestController` — REST endpoints
- `@RequestMapping`, `@GetMapping`, `@PostMapping`, etc. — endpoint routing
- `@Transactional` — transaction boundaries on write operations
- `@Autowired` — field injection (constructor injection preferred in new code)

**JPA/Jakarta:**

- `@Entity` — persistent classes
- `@Table` — table mapping
- `@Column`, `@JoinColumn` — column mappings
- `@Id`, `@GeneratedValue` — primary key
- `@Enumerated(EnumType.STRING)` — enum storage
- `@ManyToOne`, `@OneToMany`, `@ManyToMany` — relationships

**Timefold Solver:**

- `@PlanningSolution` — optimization problem class
- `@PlanningEntity` — movable elements (e.g., `AgentAssignment`)
- `@PlanningVariable` — variable to optimize (e.g., `Agent agent`)
- `@PlanningScore` — score field
- `@ConstraintWeight` — weight annotation on ConstraintWeights
- `@ProblemFactCollectionProperty`, `@ValueRangeProvider` — problem facts

**Testing:**

- `@Test` — test method (JUnit 5)
- `@DataJpaTest` — Spring Data test slice (in-memory database)
- `@ExtendWith(MockitoExtension.class)` — Mockito support
- `@Mock`, `@InjectMocks` — Mockito annotations
- `@MockitoBean` — Spring test bean replacement
- `@EnabledIfSystemProperty` — conditional test execution
- `@DisplayName` — readable test name

## Type Patterns

**Records (DTOs/Requests/Responses):**

- Used for immutable data transfer objects
- Examples: `DeskRequest`, `DeskResponse`, `SetUsualShiftRequest`

```java
// Frontend DTOs are records in TypeScript via interfaces
export interface DeskRequest {
  name: string
  description?: string
  defaultContractedHoursPerDay: string
}

// Backend uses Java records for simple DTOs
public record DeskRequest(String name, String description, BigDecimal defaultContractedHoursPerDay) {}
```

**Entities:**

- Mutable JPA entity classes with getters/setters
- Never records (JPA requires no-arg constructor)
- Example: `Agent.java`, `Schedule.java`

**Enums:**

- Java: `public enum SchedulingMode { SLOT, SHIFT }` or `@Enumerated(EnumType.STRING) private SchedulingMode mode`
- TypeScript: union types (`type ToastType = 'success' | 'error' | 'warning'`)

---

*Convention analysis: 2026-09-17*
