---
last_mapped_commit: 7e18ca2766e5fc34d93136d520c5a1529f8dc3b5
last_mapped_at: 2026-09-17
---
# Testing Patterns

**Analysis Date:** 2026-09-17

## Test Framework

**Runner:**

- JUnit 5 (Jupiter API)
- Gradle task: `./gradlew test`
- Platform: `useJUnitPlatform()` configured in `build.gradle`

**Assertion Library:**

- AssertJ (`org.assertj.core.api.Assertions.assertThat`)
- Readable fluent syntax (e.g., `assertThat(list).hasSize(2).containsExactly(...)`)

**Test Count:**

- 112 test classes across 14 directories
- 780+ tests total (11 skipped)
- Breakdown: 57 service tests, 28 solver tests, 8 integration tests, 3 model tests, 2 util tests, 2 repository tests, 2 controller tests, 1 migration test, 1 config test

**Run Commands:**

```bash
./gradlew test                           # Run all tests (780+, 11 skipped)
./gradlew test -Dwfm.benchmark=true     # Include benchmark tests (ShiftModelBenchmarkTest)
./gradlew test --tests BandCapacityConstraintTest  # Run specific test class
```

## Test File Organization

**Location:**

- `src/test/java/com/wfm/{module}/{TestName}Test.java` (mirrors production structure)
- `src/test/resources/` — test data, markdown specifications, fixtures
- Packages mirror `src/main/java`: `com.wfm.service`, `com.wfm.solver`, `com.wfm.integration`, etc.

**Naming Convention:**

- **Unit/service tests:** `{ClassName}Test.java` (e.g., `DeskServiceTest.java`, `AgentNameSplitterTest.java`)
- **Constraint tests:** `{Constraint}ConstraintTest.java` (e.g., `BandCapacityConstraintTest.java`, `ShiftWorkContiguityConstraintTest.java`)
- **Guard tests:** `{SubjectClass}GuardTest.java` (e.g., `UsualShiftWritePathTest.java` as behavioral guard, `SolverUsualShiftWritePathGuardTest.java` as structural+behavioral)
- **Integration tests:** `{Feature}Test.java` (e.g., `BambooRefreshServiceTest.java`, `WorkingDaysParserTest.java`)

**Test Resources:**

- Markdown specifications: `src/test/resources/ushf-05-write-paths.md` (parsed by `UsualShiftWritePathGuardTest` to enforce completeness)
- Solver configuration: `src/main/resources/solverConfig.xml` (loaded at test time by `SolverConfig.createFromXmlResource`)

## Test Structure

**Standard Unit Test:**

```java
@ExtendWith(MockitoExtension.class)
class BambooRefreshServiceTest {
    @Mock
    private DeskRepository deskRepository;
    
    @InjectMocks
    private BambooRefreshService service;

    @Test
    void methodUnderTest_condition_expectedResult() {
        // Given: set up fixtures
        Agent agent = new Agent();
        
        // When: execute
        service.refreshDeskAgents(desk.getId());
        
        // Then: assert
        assertThat(result).isEqualTo(expected);
    }
}
```

**Spring Integration Test (DataJpaTest):**

```java
@DataJpaTest
@Import({DeskService.class, InMemoryScheduleStore.class})
@ActiveProfiles("test")
class UsualShiftWritePathTest {
    @Autowired
    private DeskRepository deskRepository;
    
    @Autowired
    private DeskService deskService;
    
    @MockitoBean
    private ShiftLibraryValidationService shiftLibraryValidationService;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void switchSchedulingMode_roundTrip_leavesStoredUsualShiftsFieldIdentical() {
        // real JPA repositories, real H2 database in-memory
    }
}
```

**Constraint Verifier Test:**

```java
class BandCapacityConstraintTest {
    private final ConstraintVerifier<ScheduleConstraintProvider, Schedule> verifier =
            ConstraintVerifier.build(new ScheduleConstraintProvider(), Schedule.class,
                    AgentAssignment.class, AgentShiftAssignment.class);

    @Test
    @DisplayName("capacity N with exactly N agent-days draws no penalty")
    void capacityN_exactlyNAgentDays_noPenalty() {
        ShiftTemplate t = template();
        ShiftTemplateBreakBand capped = band(t, 240, 2);

        List<Object> facts = new ArrayList<>();
        facts.add(scheduleConfig(SchedulingMode.SHIFT));
        facts.add(shiftRow(agent(), MONDAY, pair));

        verifier.verifyThat(ScheduleConstraintProvider::bandCapacity)
                .given(facts.toArray())
                .penalizesBy(0);
    }
}
```

**Guard Test (Distinctive Pattern — Two Proofs):**

```java
/**

 * Dual proof for invariant: BambooRefreshService never writes AgentUsualShift.
 * (a) Behavioral: exercises actual method with mocked dependencies, asserts zero mutations
 * (b) Structural: reflection on production source, asserts no code line references repository
 */
@DataJpaTest
@Import({DeskService.class, InMemoryScheduleStore.class})
class UsualShiftWritePathTest {
    @Test
    void refreshDeskAgents_leavesStoredUsualShiftsByteIdentical_behavioural() {
        // Save initial state, run refresh, reload and assert byte-identical
        AgentUsualShift before = saveUsualShift(...);
        List<UsualShiftSnapshot> beforeSnapshot = List.of(UsualShiftSnapshot.of(before));
        
        bambooRefreshService.refreshDeskAgents(desk.getId());
        
        AgentUsualShift after = reload(before.getId());
        assertThat(UsualShiftSnapshot.of(after)).isEqualTo(beforeSnapshot);
    }

    @Test
    void refreshDeskAgents_declaresNoAgentUsualShiftRepositoryField_structural() {
        // Reflection: BambooRefreshService must not have AgentUsualShiftRepository field
        boolean hasField = Arrays.stream(BambooRefreshService.class.getDeclaredFields())
                .anyMatch(f -> AgentUsualShiftRepository.class.isAssignableFrom(f.getType()));
        assertThat(hasField).isFalse();
    }
}
```

**Structural Guard with Source Scanning:**

```java
@ExtendWith(MockitoExtension.class)
class SolverUsualShiftWritePathGuardTest {
    private static final List<String> MUTATING_CALL_PATTERNS = List.of(
            "agentUsualShiftRepository.save(",
            "agentUsualShiftRepository.delete(",
            "agentUsualShiftRepository.deleteById(",
            // ... 10 more patterns
    );

    @Test
    void solverServiceSource_noCodeLineInvokesAMutatingMethodOnTheUsualShiftRepository() 
            throws IOException, URISyntaxException {
        Path moduleRoot = resolveModuleRoot();
        Path solverServiceFile = moduleRoot.resolve("src/main/java/com/wfm/service/SolverService.java");
        
        List<String> allLines = Files.readAllLines(solverServiceFile, StandardCharsets.UTF_8);
        List<String> codeLines = stripCommentsAndJavadoc(allLines);
        
        List<String> offendingLines = new ArrayList<>();
        for (String line : codeLines) {
            for (String pattern : MUTATING_CALL_PATTERNS) {
                if (line.contains(pattern)) {
                    offendingLines.add(line.strip());
                    break;
                }
            }
        }
        
        assertThat(offendingLines).isEmpty();
    }
}
```

## Mocking

**Framework:** Mockito (via Spring Test or standalone)

**Patterns:**

**1. Standalone Mocking (No Spring):**

```java
@ExtendWith(MockitoExtension.class)
class BambooRefreshServiceTest {
    @Mock
    private BambooHRClient bambooHRClient;
    
    @InjectMocks
    private BambooRefreshService service;

    @Test
    void method_condition_result() {
        when(bambooHRClient.listEmployees(anyString(), any()))
                .thenReturn(List.of(new BambooEmployee(...)));
        
        service.refreshDeskAgents(deskId);
        
        verify(bambooHRClient).listEmployees(eq("tenant123"), any());
    }
}
```

**2. Spring Test Bean Replacement:**

```java
@DataJpaTest
@Import(DeskService.class)
class UsualShiftWritePathTest {
    @MockitoBean
    private ShiftLibraryValidationService shiftLibraryValidationService;

    @Autowired
    private DeskService deskService; // uses mocked validation service
}
```

**3. Reflective Method Mocking (testing private behavior):**

```java
@Test
void shouldDowngradeWorkingDaysKnown_spreadsheetSourced_neverDowngraded() throws Exception {
    Method m = BambooRefreshService.class.getDeclaredMethod(
            "shouldDowngradeWorkingDaysKnown", Agent.class);
    m.setAccessible(true);
    
    boolean result = (boolean) m.invoke(null, agent);
    
    assertThat(result).isFalse();
}
```

**What to Mock:**

- External dependencies (HTTP clients, file systems)
- Database repositories (when testing business logic in isolation)
- Spring beans in @DataJpaTest slices
- DO NOT mock the class under test

**What NOT to Mock:**

- The service/class being tested
- JPA repositories in @DataJpaTest (use real H2 in-memory DB)
- Core JDK classes (String, List, UUID)
- Timefold solver pieces (use ConstraintVerifier instead)

## Fixtures and Factories

**Test Data Builders (static helpers):**

```java
private static Agent agent() {
    Agent a = new Agent();
    a.setId(UUID.randomUUID());
    a.setTenantId(1L);
    a.setName("Agent Name");
    return a;
}

private static ShiftTemplate template() {
    ShiftTemplate t = new ShiftTemplate();
    t.setId(UUID.randomUUID());
    t.setName("Template-" + UUID.randomUUID());
    t.setStartTime(LocalTime.of(8, 0));
    t.setEndTime(LocalTime.of(17, 0));
    return t;
}
```

**Snapshot Records (field-by-field comparison):**

```java
private record UsualShiftSnapshot(UUID id, long tenantId, UUID agentId, DayOfWeek dayOfWeek, UUID shiftTemplateId) {
    static UsualShiftSnapshot of(AgentUsualShift row) {
        return new UsualShiftSnapshot(row.getId(), row.getTenantId(), row.getAgent().getId(),
                row.getDayOfWeek(), row.getShiftTemplate().getId());
    }
}
```

**Location:**

- Test helper methods: same test class or package-scoped helper class (e.g., `ShiftModeFixtures.java`)
- Shared fixtures: `src/test/java/com/wfm/support/` (e.g., `PostgresBackedTest.java`)
- Constants: package-private `static final` fields in test classes (e.g., `private static final long TENANT_ID = 1L;`)

## Coverage

**Requirements:** Not enforced by build (no Jacoco or similar observed)

**View Coverage (manual):**

```bash

# No built-in task; use IDE or external tools

# IntelliJ: Run → Run with Coverage

# Gradle plugin could be added to build.gradle if needed

```

**Test Coverage Gaps:**

- Frontend: no test framework detected (Vite build only)
- Migration-specific testing: `MigrationEntityConsistencyTest` regex-based DDL validation
- E2E testing: not observed in suite

## Test Types

### Unit Tests

**Scope:** Single service method or utility function in isolation
**Approach:** Mockito mocks, no Spring context, fast execution
**Examples:**

- `BambooRefreshServiceTest#mapEmploymentType_*` — private method reflection tests
- `AgentNameSplitterTest` — utility function tests
- `WorkingDaysSourceGuardTest` — private static method tests

### Service Tests (Spring Data JPA)

**Scope:** Service layer with real JPA repositories on H2
**Approach:** `@DataJpaTest` with in-memory H2, transactional, real entity relationships
**Examples:**

- `UsualShiftWritePathTest` — write-path proofs (rows 5, 6, 7 of the ledger)
- `DeskServiceSchedulingModeTest` — round-trip mode switching
- `DeskAgentServiceUsualShiftTest` — agent removal side effects

### Postgres-Backed Tests

**Scope:** Real PostgreSQL with Flyway migrations
**Approach:** Extends `PostgresBackedTest`, Testcontainers singleton, `spring.flyway.enabled=true`
**Examples:**

- `AgentRepositoryPostgresTest` — untyped null parameter handling (GET /agents bug)
- `AgentUsualShiftPostgresTest` — FK cascade behavior on desk delete

**Base Class Contract (`PostgresBackedTest`):**

- Starts Postgres 16 container once per JVM (shared singleton, not per-class)
- Skips gracefully if Docker unavailable (`disabledWithoutDocker = true`)
- Runs real Flyway migrations (V1..Vn in order)
- Sets `spring.jpa.hibernate.ddl-auto=validate` (schema must match entities post-migration)
- All migrations must execute without error; schema drift fails fast

### Constraint Verifier Tests (Timefold)

**Scope:** Individual soft/hard constraint definitions
**Approach:** `ConstraintVerifier.build()`, fluent API, assertions on penalty scores
**Examples:**

- `BandCapacityConstraintTest` — 5 capacity scenarios
- `ShiftWorkContiguityConstraintTest` — contiguity invariants with break placement
- `MinimumStaffingConstraintTest` — FTE fulfillment

**Pattern:**

```java
verifier.verifyThat(ScheduleConstraintProvider::bandCapacity)
        .given(facts.toArray())
        .penalizesBy(expectedScore);
```

### Integration Tests

**Scope:** Multi-layer integration without full Spring context
**Approach:** Mock external clients, test parsing/merging logic
**Examples:**

- `BambooRefreshServiceTest` — employment type mapping, job title deduplication
- `MergePrecedenceTest` — merge rule enforcement
- `WorkingDaysParserTest` — spreadsheet parsing

### Guard Tests (Distinctive Pattern)

**Scope:** Architectural invariants and write-path completeness
**Approach:** Dual proof (behavioral + structural) enforced by build
**Examples:**

**Behavioral Guards:**

- `UsualShiftWritePathTest#refreshDeskAgents_leavesStoredUsualShiftsByteIdentical_behavioural()` — exercises code path with real/mocked deps, asserts side-effect-free
- `SolverUsualShiftWritePathGuardTest#resolveUsualShiftTargets_zeroMutatingInteractionsOnTheRepository()` — mocked repo captures zero mutation calls

**Structural Guards:**

- `UsualShiftWritePathTest#solverPackage_declaresNoAgentUsualShiftReference_structural()` — reflection on class fields
- `SolverUsualShiftWritePathGuardTest#solverServiceSource_noCodeLineInvokesAMutatingMethodOnTheUsualShiftRepository()` — source code scanning with comment stripping
- `ScheduleConstraintClassificationTest` — reflective assertion on constraint weight annotations vs constraint builder methods

**Why Two Proofs Are Required (from XCUT-05):**

- Behavioral-only guard: comment-only fix could bypass it
- Structural-only guard: cannot catch wrong behavior (e.g., wrong entity updated)
- Together: code change AND behavior are verified

**Write-Path Ledger (USHF-05):**

- Canonical source: `src/test/resources/ushf-05-write-paths.md`
- Parsed at test time by `UsualShiftWritePathGuardTest`
- Enumerates every write path to `agent_usual_shift` table (9 rows as of Phase 17)
- Guard allowlists enforce set equality: classes that reference `AgentUsualShift` or `AgentUsualShiftRepository` must exactly match the ledger's row list
- Violations fail build (missing or stale allowlist entries)

### Solver Quality Guard Tests

**Scope:** Structural invariants on solved output (not score thresholds)
**Approach:** Multiple random seeds, deterministic assertion, post-solve validation
**Example:** `SolverQualityGuardTest`

- Solves 5 seeds against corrupted base schedules
- Asserts zero split shifts, zero edge breaks, all edge hours staffed
- Reserves one score assertion for violation-count median (P-42)
- Never hard-codes hard/soft score limits (variance defeats test reliability)

### E2E/Benchmark Tests (Gated)

**Scope:** Full solver end-to-end, performance measurement
**Approach:** `@EnabledIfSystemProperty(named = "wfm.benchmark", matches = "true")`
**Example:** `ShiftModelBenchmarkTest`

- Runs only with `-Dwfm.benchmark=true`
- Not executed by default or on CI deploy gate
- Step-count terminated, seeded for determinism
- Elapsed time printed for observability (not asserted)

## Common Patterns

### Async Testing

**Pattern:** Not observed; solver runs synchronous in tests

- Timefold solves are step-count or time-terminated (5 seconds typical)
- Database operations use `@Transactional` test context (automatic rollback per test)

### Error Testing

```java
@Test
void createDesk_blankName_throwsIllegalArgumentException() {
    assertThatThrownBy(() -> deskService.createDesk("", null, BigDecimal.ONE))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Desk name is required");
}

@Test
void createDesk_duplicateName_throwsConflictException() {
    deskService.createDesk("Marketing", null, BigDecimal.ONE);
    
    assertThatThrownBy(() -> deskService.createDesk("Marketing", null, BigDecimal.ONE))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("already exists");
}
```

### Reflection-Based Testing

**Use case:** Testing private methods or validating no reference to a class exists

```java
// Accessing private method
Method m = BambooRefreshService.class.getDeclaredMethod("shouldDowngradeWorkingDaysKnown", Agent.class);
m.setAccessible(true);
boolean result = (boolean) m.invoke(null, agent);

// Scanning source for forbidden patterns
Path solverServiceFile = moduleRoot.resolve("src/main/java/com/wfm/service/SolverService.java");
List<String> lines = Files.readAllLines(solverServiceFile, StandardCharsets.UTF_8);
// ... filter and scan for patterns
```

### Fixture Construction (Minimal Workable Example)

```java
private static Schedule buildMinimalSlotSchedule() {
    long tenant = 999L;
    LocalDate day = LocalDate.of(2026, 3, 10);
    
    Specialization spec = new Specialization();
    spec.setId(UUID.randomUUID());
    spec.setTenantId(tenant);
    
    Agent agent = new Agent();
    agent.setId(UUID.randomUUID());
    agent.setTenantId(tenant);
    agent.setContractedHoursPerDay(new BigDecimal("1.00"));
    
    // Build timeslots, staffing requirements, assignments
    // Keep data set small for test speed (4 timeslots, 1 agent, 1 day)
    
    return schedule;
}
```

### Termination Override (for Deterministic Solving)

```java
SolverConfig solverConfig = SolverConfig.createFromXmlResource("solverConfig.xml");
List<PhaseConfig> phases = solverConfig.getPhaseConfigList();
phases.get(phases.size() - 1).setTerminationConfig(
        new TerminationConfig().withStepCountLimit(50));
// Solver-level hard wall-clock cap for safety
solverConfig.setTerminationConfig(new TerminationConfig().withSecondsSpentLimit(5L));
SolverFactory<Schedule> solverFactory = SolverFactory.create(solverConfig);
```

## Special Annotations & Configuration

### Test Slice Annotations

- `@DataJpaTest` — JPA/Spring Data tests with H2 (no web layer)
- `@WebMvcTest` — Spring MVC controller tests (not observed in current suite)
- `@ExtendWith(MockitoExtension.class)` — standalone Mockito without Spring

### Conditional Execution

```java
@EnabledIfSystemProperty(named = "wfm.benchmark", matches = "true")
public void benchmarkTest() { ... }
```

### Active Profiles

```java
@ActiveProfiles("test")  // Loads application-test.properties
```

## Test Data Files

**Markdown Specifications:**

- `src/test/resources/ushf-05-write-paths.md` — write-path table with 9 rows (paths 1-7 + planner additions 8-9), guard allowlists for Set A (repository references) and Set B (entity references)

**Solver Configuration:**

- `src/main/resources/solverConfig.xml` — loaded by `SolverConfig.createFromXmlResource("solverConfig.xml")`

## Skipped Tests

**Count:** 11 tests skipped (out of 780+)

**Reason:** `@Testcontainers(disabledWithoutDocker = true)` on Postgres-backed test classes

- Tests skip (not fail) if Docker unavailable
- CI and deploy gate run on `ubuntu-latest` (Docker present), so full suite always runs there

---

*Testing analysis: 2026-09-17*
