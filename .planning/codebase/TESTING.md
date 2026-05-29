# Testing Patterns

**Analysis Date:** 2026-04-02

## Test Framework

**Runner:**
- JUnit 5 (Jupiter) — declared via `useJUnitPlatform()` in `build.gradle`
- Spring Boot Test (`spring-boot-starter-test`) for integration test support
- Timefold solver test (`timefold-solver-test`) for solver-specific assertions

**Assertion Library:**
- AssertJ (`org.assertj.core.api.Assertions.assertThat`) — used exclusively; no raw JUnit `assertEquals`

**In-memory database:**
- H2 (`com.h2database:h2`, `testRuntimeOnly`) used for the Spring context integration test

**Run Commands:**
```bash
./gradlew test --no-daemon     # Run all tests
./gradlew test                 # Run all tests (daemon)
```
Coverage reporting is not configured. No watch-mode task is defined.

## Test File Organization

**Location:** All tests under `src/test/java/` mirroring the main package structure

**Naming:**
- Test classes: `*Test` suffix (e.g., `SingleDaySolvableTest`, `ResolvePreferencesPtoFilterTest`)
- Test methods: descriptive snake-case describing scenario and expected outcome (e.g., `standingPreference_excludedOnPtoDay`, `singleDay_twoAgents_preAssigned_shouldScoreFeasible`)

**Structure:**
```
src/test/java/
  com/wfm/
    WfmApplicationTests.java                  # Spring context smoke test
    service/
      ResolvePreferencesPtoFilterTest.java     # Unit test (no Spring context)
    solver/
      SingleDaySolvableTest.java               # Solver scoring test (no Spring context)
      BreakAwareConstructionTest.java          # Solver CH + LS test (no Spring context)
      MultiDayConstraintDiagnosticTest.java    # Multi-day solver test (no Spring context)
      IncrementalScoringDiagnosticTest.java    # Incremental scoring diagnostic
      TwelveHourUniformDemandTest.java         # Solver feasibility test
      NinetyFiveAgentReproTest.java            # Reproduction test for 95-agent scenario
      NinetyAgent12HourTest.java               # 90-agent 12-hour solver test
      FullScale150AgentTest.java               # Full-scale 150-agent feasibility test
src/test/resources/
  application-test.yml                         # H2 datasource, Flyway disabled, mock BambooHR
```

## Test Structure

**Suite Organization:**

Unit and solver tests follow this pattern:
```java
class ResolvePreferencesPtoFilterTest {

    // Shared constants
    private static final long TENANT = 1L;
    private static final LocalDate MON = LocalDate.of(2026, 3, 9);

    @Test
    void standingPreference_excludedOnPtoDay() throws Exception {
        // Arrange: build domain objects via factory helpers
        Agent agent = agent("A1", "Alice", deskId);
        AgentPreference standing = standingPref(agent, deskId, DayOfWeek.TUESDAY, LocalTime.of(9, 0));

        // Act: invoke method under test
        List<AgentPreference> resolved = invokeResolvePreferences(...);

        // Assert: AssertJ assertions
        assertThat(resolved).isEmpty();
    }

    // --- Factory helpers ---
    private Agent agent(String bambooId, String name, UUID deskId) { ... }
    private AgentPreference standingPref(...) { ... }
}
```

**Solver test pattern:**
```java
class SingleDaySolvableTest {

    @Test
    void singleDay_twoAgents_preAssigned_shouldScoreFeasible() {
        Schedule solution = buildPreAssignedSolution();     // arrange
        HardSoftScore score = solutionManager.update(solution);  // act (score only, no search)
        assertThat(score.hardScore()).isZero();             // assert
    }

    // --- Schedule builder ---
    private Schedule buildPreAssignedSolution() { ... }

    // --- Factory helpers ---
    private Agent agent(...) { ... }
    private Timeslot timeslot(...) { ... }
}
```

**Patterns:**
- No `@BeforeEach` / `@AfterEach` — all setup is inline via factory helpers
- No test lifecycle annotations except `@Test`
- `@SpringBootTest` + `@ActiveProfiles("test")` only in `WfmApplicationTests` (context smoke test)
- All solver tests avoid the Spring context entirely — they construct `SolverConfig` and `SolverFactory` programmatically

## Mocking

**Framework:** None — no Mockito or similar mocking library is used

**Approach:**
- Solver tests build the full domain object graph by hand using factory helper methods
- `ResolvePreferencesPtoFilterTest` accesses private methods via **reflection** rather than mocking:

```java
private SolverService createSolverServiceWithNullDeps() throws Exception {
    var ctors = SolverService.class.getDeclaredConstructors();
    var ctor = ctors[0];
    ctor.setAccessible(true);
    Object[] nullArgs = new Object[ctor.getParameterCount()];
    return (SolverService) ctor.newInstance(nullArgs);
}

Method method = SolverService.class.getDeclaredMethod(
        "resolvePreferences", List.class, Schedule.class, Map.class);
method.setAccessible(true);
return (List<AgentPreference>) method.invoke(service, ...);
```

**What to mock:** Nothing — the convention is to build real domain objects
**What NOT to mock:** No Spring beans, no repositories, no solver infrastructure in tests

## Fixtures and Factories

**Test Data Pattern:**

Each test class defines its own private factory helpers at the bottom, separated by section comments. There are no shared fixture files or factory utilities.

```java
// --- Factory helpers ---

private Agent agent(String bambooId, String name) {
    Agent a = new Agent();
    a.setId(UUID.randomUUID());
    a.setTenantId(TENANT);
    a.setBamboohrId(bambooId);
    a.setName(name);
    a.setActive(true);
    return a;
}

private Timeslot timeslot(UUID deskId, UUID scheduleId,
                          LocalDate date, LocalTime start, LocalTime end) {
    Timeslot ts = new Timeslot();
    ts.setId(UUID.randomUUID());
    ts.setTenantId(TENANT);
    ts.setDeskId(deskId);
    ts.setScheduleId(scheduleId);
    ts.setDate(date);
    ts.setStartTime(start);
    ts.setEndTime(end);
    return ts;
}
```

**Location:** Factory helpers are private methods within each test class — no shared test support classes exist

## Coverage

**Requirements:** None enforced — no JaCoCo or other coverage plugin in `build.gradle`

**View Coverage:** Not configured. To generate a basic report, add JaCoCo to `build.gradle` and run `./gradlew jacocoTestReport`.

## Test Types

**Context smoke test (`WfmApplicationTests`):**
- Verifies the Spring application context loads without errors
- Uses `@SpringBootTest` + `@ActiveProfiles("test")` (H2, Flyway disabled, mock BambooHR)
- Single test: `contextLoads()` with empty body
- File: `src/test/java/com/wfm/WfmApplicationTests.java`

**Unit tests (service logic, `service/` package):**
- No Spring context; no I/O; no solver
- Test private business logic via reflection
- File: `src/test/java/com/wfm/service/ResolvePreferencesPtoFilterTest.java`

**Solver scoring tests (`solver/` package — score-only):**
- Build a pre-assigned `Schedule` and call `SolutionManager.update()` to compute score
- No solver search; verify hard/soft score is zero
- Example: `src/test/java/com/wfm/solver/SingleDaySolvableTest.java`, `FullScale150AgentTest.java`

**Solver integration tests (`solver/` package — CH + local search):**
- Run full `SolverFactory.buildSolver().solve(schedule)` with a time-bounded termination
- Verify the solver reaches a feasible (hard score = 0) or near-feasible result
- Use `Duration.ofSeconds(10)` to `Duration.ofSeconds(120)` depending on problem size
- Examples: `BreakAwareConstructionTest.java`, `MultiDayConstraintDiagnosticTest.java`

**Scale/stress tests (`solver/` package):**
- Test solver performance at realistic agent counts (90, 95, 150 agents)
- Use looser assertions (`isGreaterThanOrEqualTo(-200)`) where full feasibility is not guaranteed within the time budget
- Examples: `NinetyFiveAgentReproTest.java`, `NinetyAgent12HourTest.java`, `FullScale150AgentTest.java`

**E2E Tests:** None — no REST layer tests (no `MockMvc`, no `@WebMvcTest`, no `RestAssured`)

## Common Patterns

**Async / solver testing:**
```java
// Synchronous solve — blocks until termination condition is met
Schedule solved = solverFactory.buildSolver().solve(schedule);
assertThat(solved.getScore().hardScore()).isZero();
```

**Constraint explanation (diagnostic output):**
```java
if (!score.equals(HardSoftScore.ZERO)) {
    var explanation = solutionManager.explain(solution);
    explanation.getConstraintMatchTotalMap().forEach((name, total) -> {
        if (!total.getScore().equals(HardSoftScore.ZERO)) {
            System.out.println("  " + name + " => " + total.getScore());
        }
    });
}
```

**Score-only verification (no search):**
```java
SolverFactory<Schedule> solverFactory = SolverFactory.create(
        new SolverConfig()
                .withSolutionClass(Schedule.class)
                .withEntityClasses(AgentAssignment.class)
                .withScoreDirectorFactory(new ScoreDirectorFactoryConfig()
                        .withConstraintProviderClass(ScheduleConstraintProvider.class)));

var solutionManager = SolutionManager.<Schedule, HardSoftScore>create(solverFactory);
HardSoftScore score = solutionManager.update(solution);
assertThat(score.hardScore()).isZero();
```

## CI Setup

**Pipeline:** GitHub Actions, defined in `.github/workflows/ci.yml`

**Triggers:** Pull requests and pushes to `main`

**Jobs:**
- `test-backend`: Sets up Java 21 (Temurin), runs `./gradlew test --no-daemon`, then builds JAR. Test results uploaded as artifact from `build/reports/tests/`
- `build-frontend`: Sets up Node 20, runs `npm ci` and `npm run build` in `frontend/`
- `docker-build`: Builds Docker image (`docker build -t wfm-service:ci .`), depends on `test-backend` passing

**No coverage enforcement** in CI — test results artifact only.

## Coverage Gaps

**REST/Controller layer:**
- No tests for any controller class (`src/main/java/com/wfm/controller/`)
- No `MockMvc` or `@WebMvcTest` tests exist
- HTTP request validation, pagination, and error response formatting are untested

**Service layer (most services):**
- Only `SolverService.resolvePreferences` has a dedicated unit test
- `AgentService`, `ScheduleService`, `DeskService`, `StaffingRequirementService`, etc. have no tests
- Cursor pagination logic in `CursorPagination` (`src/main/java/com/wfm/util/CursorPagination.java`) is untested

**Integration / repository layer:**
- No repository tests or `@DataJpaTest` slices
- Multi-tenant data isolation (the `X-Tenant-ID` filter in `TenantFilter`) is untested

**BambooHR integration:**
- `HttpBambooHRClient` and `BambooRefreshService` have no tests

**Solver constraint coverage:**
- Soft constraints (`honourPreferredStartTime`, `honourPreferredBreakTime`, `breakClustering`, etc.) are exercised only indirectly by the solver integration tests; no targeted constraint unit tests using `ConstraintVerifier` (from `timefold-solver-test`)

---

*Testing analysis: 2026-04-02*
