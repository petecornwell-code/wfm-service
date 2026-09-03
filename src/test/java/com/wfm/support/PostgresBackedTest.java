package com.wfm.support;

import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class for tests that must run against a REAL Postgres with the REAL Flyway migrations
 * applied, rather than against H2 with a schema generated from the entity mappings.
 *
 * <p><b>Why this exists.</b> The rest of the suite runs on H2 with {@code ddl-auto: create-drop}
 * and {@code flyway.enabled: false}. That configuration is structurally blind to two whole classes
 * of defect, and both shipped:
 * <ul>
 *   <li><b>Postgres type resolution.</b> An untyped JDBC null reaches Postgres as {@code unknown},
 *       which it resolves to {@code bytea} inside {@code lower('%' || ? || '%')}. H2 tolerates it.
 *       {@code GET /api/v1/agents} with no parameters returned 500 in the field while every test
 *       passed — see {@link com.wfm.repository.AgentRepository#findFiltered} and
 *       {@code AgentRepositoryPostgresTest}.</li>
 *   <li><b>The migrations themselves.</b> With {@code ddl-auto: create-drop} the test schema comes
 *       from the JPA entities, so <em>no test executes a migration</em>. V40-V43 reached production
 *       rehearsal on dev without a single test having run them.
 *       {@code MigrationEntityConsistencyTest} is a static regex reconciliation of DDL text against
 *       entity mappings — a real guard, but it cannot catch Postgres-specific syntax, a constraint
 *       violated by real rows, or an incorrect data fan-out.</li>
 * </ul>
 *
 * <p><b>What subclasses get.</b> A Postgres 16 container matching the dev RDS instance (16.13),
 * with {@code spring.flyway.enabled=true} and {@code ddl-auto=validate}: every migration V1..Vn
 * runs, in order, on a real Postgres, and the resulting schema is then validated against the
 * entity mappings. A migration that disagrees with its entity fails the test rather than the
 * next deploy's startup.
 *
 * <p><b>Docker.</b> {@code disabledWithoutDocker = true} means these tests SKIP where Docker is
 * absent instead of failing a developer's local build. That is a deliberate trade, and it has a
 * sharp edge worth stating: a skip is not a pass. CI runs on {@code ubuntu-latest}, which has
 * Docker, and the deploy gate runs the full unfiltered suite — so these DO gate a deploy. If that
 * ever stops being true, this guard silently stops existing.
 *
 * <p><b>Cost / lifecycle (revised, Phase 16).</b> The container is a JVM-wide singleton, started
 * once in a static initializer and never explicitly stopped -- Testcontainers' own Ryuk reaper
 * container cleans it up when the JVM exits. This is deliberately NOT the {@code @Container}
 * annotation's per-test-class lifecycle: {@code @Container} starts the container before, and
 * stops it after, EACH test class's run, but {@code POSTGRES} is one shared static field slot
 * declared on THIS superclass, not duplicated per subclass. With only one subclass
 * ({@code AgentRepositoryPostgresTest}) that collision was unreachable; adding a second
 * ({@code AgentUsualShiftPostgresTest}, Phase 16) surfaced it immediately -- every test in
 * whichever class ran second failed with a JDBC "connection refused" against the first class's
 * now-stopped container port. The singleton pattern (start once, never stop, no {@code @Container}
 * annotation on the field) is Testcontainers' own documented fix for exactly this
 * shared-across-classes scenario. Keep the number of Postgres-backed classes small regardless --
 * each still pays its own migration run against the shared container -- and put related
 * assertions together in one class rather than spreading them.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
public abstract class PostgresBackedTest {

    /**
     * {@code pgvector/pgvector:pg16} rather than plain {@code postgres:16} — V24 runs
     * {@code CREATE EXTENSION vector}, which the stock image cannot satisfy
     * ({@code ERROR: extension "vector" is not available}). pg16 matches the dev RDS instance's
     * major version (16.13). {@code asCompatibleSubstituteFor} is required because the image name
     * is not the official {@code postgres} one Testcontainers otherwise expects.
     *
     * <p>Deliberately NOT {@code @Container} (see the class javadoc's Lifecycle note) -- this is
     * the Testcontainers "singleton container" pattern instead, started once below and shared by
     * every subclass for the life of the JVM.
     */
    @SuppressWarnings("resource") // Testcontainers' Ryuk reaper owns cleanup at JVM exit
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(
                    DockerImageName.parse("pgvector/pgvector:pg16")
                            .asCompatibleSubstituteFor("postgres"))
                    .withDatabaseName("wfm_test")
                    .withUsername("wfm")
                    .withPassword("wfm");

    static {
        // Guarded on Docker availability so class LOADING itself never throws -- static
        // initializers run at class-load time, which can happen before
        // @Testcontainers(disabledWithoutDocker = true)'s own ExecutionCondition gets a chance to
        // disable the test class. If Docker is absent this leaves POSTGRES unstarted; the
        // disabledWithoutDocker skip (backed by the same DockerClientFactory check) still applies
        // before any test method would otherwise touch the unstarted container.
        if (DockerClientFactory.instance().isDockerAvailable()) {
            POSTGRES.start();
        }
    }

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");

        // The point of the exercise: run the real migrations, then hold the entity mappings to the
        // schema they produce. `validate` rather than `none` so a drift between a migration and its
        // entity fails here instead of at the next production startup.
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }
}
