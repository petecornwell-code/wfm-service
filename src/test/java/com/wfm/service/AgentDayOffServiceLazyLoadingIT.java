package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.dto.AgentDayOffResponse;
import com.wfm.model.*;
import com.wfm.repository.AgentDayHoursRepository;
import com.wfm.repository.AgentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Exercises listDaysOffForDesk against real JPA, with NO surrounding transaction — the same
 * conditions as an HTTP request, since spring.jpa.open-in-view is false.
 *
 * This exists because the mocked unit test (AgentDayOffRecurringExpansionTest) cannot catch
 * lazy-loading faults: a Mockito-returned entity has its association already populated. The
 * first version of the recurring-day-off expansion queried agent_day_hours without an
 * @EntityGraph, so reading agent.name outside a transaction threw LazyInitializationException
 * and the endpoint 500'd in production while every unit test stayed green.
 *
 * Deliberately NOT annotated @Transactional — adding it would keep a session open and mask
 * exactly the failure this guards against.
 */
@SpringBootTest
@ActiveProfiles("test")
class AgentDayOffServiceLazyLoadingIT {

    private static final long TENANT = 42L;

    @Autowired private AgentDayOffService agentDayOffService;
    @Autowired private AgentRepository agentRepository;
    @Autowired private AgentDayHoursRepository agentDayHoursRepository;

    private UUID deskId;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT);
        deskId = UUID.randomUUID();

        Agent agent = new Agent();
        agent.setTenantId(TENANT);
        agent.setDeskId(deskId);
        agent.setBamboohrId("LAZY-" + UUID.randomUUID());
        agent.setName("Grace Hopper");
        agent.setActive(true);
        agent = agentRepository.save(agent);

        AgentDayHours saturdayOff = new AgentDayHours();
        saturdayOff.setTenantId(TENANT);
        saturdayOff.setAgent(agent);
        saturdayOff.setDayOfWeek(DayOfWeek.SATURDAY);
        saturdayOff.setHours(BigDecimal.ZERO);
        saturdayOff.setDayOffType(DayOffType.MANDATORY);
        agentDayHoursRepository.save(saturdayOff);

        AgentDayHours mondayWorking = new AgentDayHours();
        mondayWorking.setTenantId(TENANT);
        mondayWorking.setAgent(agent);
        mondayWorking.setDayOfWeek(DayOfWeek.MONDAY);
        mondayWorking.setHours(new BigDecimal("8.00"));
        mondayWorking.setDayOffType(null);
        agentDayHoursRepository.save(mondayWorking);
    }

    @Test
    void expandsRecurringDaysOffWithoutLazyInitializationError() {
        assertThatCode(() -> agentDayOffService.listDaysOffForDesk(
                deskId, "2026-01-05", "2026-01-18"))
                .doesNotThrowAnyException();
    }

    @Test
    void returnsBothSaturdaysWithAgentNameResolved() {
        List<AgentDayOffResponse> result =
                agentDayOffService.listDaysOffForDesk(deskId, "2026-01-05", "2026-01-18");

        assertThat(result).hasSize(2);
        assertThat(result).extracting(AgentDayOffResponse::date)
                .containsExactly(LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 17));
        assertThat(result).allSatisfy(r -> {
            assertThat(r.type()).isEqualTo("MANDATORY");
            // Reading through the association is the part that used to blow up.
            assertThat(r.agent()).isNotNull();
            assertThat(r.agent().name()).isEqualTo("Grace Hopper");
        });
    }

    @Test
    void workingDaysAreNotReportedAsDaysOff() {
        // The Monday row has hours and no dayOffType — the query filters it out.
        List<AgentDayOffResponse> result =
                agentDayOffService.listDaysOffForDesk(deskId, "2026-01-05", "2026-01-05");

        assertThat(result).isEmpty();
    }
}
