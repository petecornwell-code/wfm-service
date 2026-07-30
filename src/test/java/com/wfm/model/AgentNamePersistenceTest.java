package com.wfm.model;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that Agent.firstName / Agent.lastName persist correctly via JPA
 * (first_name / last_name columns, MDL-01).
 * Uses H2 in-memory database with ddl-auto=create-drop (Flyway disabled in test profile).
 */
@DataJpaTest
@ActiveProfiles("test")
class AgentNamePersistenceTest {

    @Autowired
    private TestEntityManager em;

    @Test
    void persistFirstAndLastName_reloadsUnchanged() {
        Agent agent = new Agent();
        agent.setTenantId(1L);
        agent.setBamboohrId("B001");
        agent.setName("Mary Jane Watson");
        agent.setFirstName("Mary");
        agent.setLastName("Jane Watson");

        Agent saved = em.persistFlushFind(agent);

        assertThat(saved.getFirstName()).isEqualTo("Mary");
        assertThat(saved.getLastName()).isEqualTo("Jane Watson");
        assertThat(saved.getName()).isEqualTo("Mary Jane Watson");
    }

    @Test
    void newAgent_firstAndLastNameDefaultToNull() {
        Agent agent = new Agent();

        assertThat(agent.getFirstName()).isNull();
        assertThat(agent.getLastName()).isNull();
    }
}
