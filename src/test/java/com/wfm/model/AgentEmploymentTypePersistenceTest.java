package com.wfm.model;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that Agent.employmentType persists correctly via JPA (V25 migration + entity field).
 * Uses H2 in-memory database with ddl-auto=create-drop (Flyway disabled in test profile).
 */
@DataJpaTest
@ActiveProfiles("test")
class AgentEmploymentTypePersistenceTest {

    @Autowired
    private TestEntityManager em;

    @Test
    void persistPartTime_reloadsAsPartTime() {
        Agent agent = new Agent();
        agent.setTenantId(1L);
        agent.setBamboohrId("B001");
        agent.setName("Alice");
        agent.setEmploymentType(EmploymentType.PART_TIME);

        Agent saved = em.persistFlushFind(agent);

        assertThat(saved.getEmploymentType()).isEqualTo(EmploymentType.PART_TIME);
    }

    @Test
    void newAgent_defaultsToFullTime() {
        Agent agent = new Agent();

        assertThat(agent.getEmploymentType()).isEqualTo(EmploymentType.FULL_TIME);
    }
}
