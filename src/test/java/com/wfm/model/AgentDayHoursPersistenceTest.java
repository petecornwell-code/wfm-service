package com.wfm.model;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.DayOfWeek;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that AgentDayHours persists correctly via JPA (V29 migration + entity).
 * Uses H2 in-memory database with ddl-auto=create-drop (Flyway disabled in test profile).
 *
 * Per D-09: per-day hours live in the agent_day_hours child table (agent_id, day_of_week,
 * hours) as a sibling row, never navigated from Agent. Absence of a row means "no data"
 * (schedule default applies); a present row with 0.00 means "not worked".
 */
@DataJpaTest
@ActiveProfiles("test")
class AgentDayHoursPersistenceTest {

    @Autowired
    private TestEntityManager em;

    private Agent persistAgent() {
        Agent agent = new Agent();
        agent.setTenantId(1L);
        agent.setBamboohrId("B001");
        agent.setName("Alice");
        return em.persist(agent);
    }

    @Test
    void persistMondaySevenPointFive_reloadsAsMondaySevenPointFive() {
        Agent agent = persistAgent();

        AgentDayHours dayHours = new AgentDayHours();
        dayHours.setTenantId(1L);
        dayHours.setAgent(agent);
        dayHours.setDayOfWeek(DayOfWeek.MONDAY);
        dayHours.setHours(new BigDecimal("7.50"));

        AgentDayHours saved = em.persistFlushFind(dayHours);

        assertThat(saved.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(saved.getHours()).isEqualByComparingTo(new BigDecimal("7.50"));
    }

    @Test
    void hours_preservesScaleTwo_wholeNumberInputReloadsCompatibleWithScaleTwo() {
        Agent agent = persistAgent();

        AgentDayHours dayHours = new AgentDayHours();
        dayHours.setTenantId(1L);
        dayHours.setAgent(agent);
        dayHours.setDayOfWeek(DayOfWeek.TUESDAY);
        dayHours.setHours(new BigDecimal("8"));

        AgentDayHours saved = em.persistFlushFind(dayHours);

        assertThat(saved.getHours()).isEqualByComparingTo(new BigDecimal("8.00"));
    }

    @Test
    void tenantIdAndAgentReference_roundTrip() {
        Agent agent = persistAgent();

        AgentDayHours dayHours = new AgentDayHours();
        dayHours.setTenantId(42L);
        dayHours.setAgent(agent);
        dayHours.setDayOfWeek(DayOfWeek.WEDNESDAY);
        dayHours.setHours(new BigDecimal("5.25"));

        AgentDayHours saved = em.persistFlushFind(dayHours);

        assertThat(saved.getTenantId()).isEqualTo(42L);
        assertThat(saved.getAgent().getId()).isEqualTo(agent.getId());
    }

    // -----------------------------------------------------------------------
    //  D-12: nullable dayOffType (day_off_type) -- recurring PTO/MANDATORY label,
    //  refresh-safe storage on agent_day_hours (never touched by BambooRefreshService).
    // -----------------------------------------------------------------------

    @Test
    void dayOffTypeLeftUnset_persistsAsNull() {
        Agent agent = persistAgent();

        AgentDayHours dayHours = new AgentDayHours();
        dayHours.setTenantId(1L);
        dayHours.setAgent(agent);
        dayHours.setDayOfWeek(DayOfWeek.THURSDAY);
        dayHours.setHours(new BigDecimal("8.00"));
        // dayOffType intentionally left unset -- worked day / unlabelled 0

        AgentDayHours saved = em.persistFlushFind(dayHours);

        assertThat(saved.getDayOffType()).isNull();
    }

    @Test
    void dayOffTypeMandatory_roundTripsAsMandatory() {
        Agent agent = persistAgent();

        AgentDayHours dayHours = new AgentDayHours();
        dayHours.setTenantId(1L);
        dayHours.setAgent(agent);
        dayHours.setDayOfWeek(DayOfWeek.FRIDAY);
        dayHours.setHours(BigDecimal.ZERO);
        dayHours.setDayOffType(DayOffType.MANDATORY);

        AgentDayHours saved = em.persistFlushFind(dayHours);

        assertThat(saved.getDayOffType()).isEqualTo(DayOffType.MANDATORY);
    }

    @Test
    void dayOffTypePto_roundTripsAsPto() {
        Agent agent = persistAgent();

        AgentDayHours dayHours = new AgentDayHours();
        dayHours.setTenantId(1L);
        dayHours.setAgent(agent);
        dayHours.setDayOfWeek(DayOfWeek.SATURDAY);
        dayHours.setHours(BigDecimal.ZERO);
        dayHours.setDayOffType(DayOffType.PTO);

        AgentDayHours saved = em.persistFlushFind(dayHours);

        assertThat(saved.getDayOffType()).isEqualTo(DayOffType.PTO);
    }
}
