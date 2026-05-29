package com.wfm.service;

import com.wfm.model.*;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the PTO filter rules for agentDaysOffMap construction in SolverService:
 *
 *  - APPROVED PTO blocks the day (appears in map)
 *  - REQUESTED PTO does NOT block the day (absent from map)
 *  - MANDATORY entries always block regardless of status
 *
 * Tests call the package-private static helper
 * {@code SolverService.buildAgentDaysOffMap(List<AgentDayOff>)} directly,
 * extracted from the original for-loop so it can be unit-tested without
 * Spring context or reflection tricks. This is cleaner than reflection and
 * produces clearer failure messages (documented in 05-03-SUMMARY.md).
 */
class SolverServicePtoFilterTest {

    private static final long TENANT = 1L;
    private static final LocalDate D1 = LocalDate.of(2026, 4, 7);
    private static final LocalDate D2 = LocalDate.of(2026, 4, 8);
    private static final LocalDate D3 = LocalDate.of(2026, 4, 9);

    // -----------------------------------------------------------------
    // PTO rows
    // -----------------------------------------------------------------

    @Test
    void pto_approved_blocksDay() {
        Agent agent = agent("A1");
        AgentDayOff pto = dayOff(agent, D1, DayOffType.PTO, DayOffStatus.APPROVED);

        Map<UUID, Set<LocalDate>> map = SolverService.buildAgentDaysOffMap(List.of(pto));

        assertThat(map).containsKey(agent.getId());
        assertThat(map.get(agent.getId())).containsExactly(D1);
    }

    @Test
    void pto_requested_doesNotBlockDay() {
        Agent agent = agent("A1");
        AgentDayOff pto = dayOff(agent, D1, DayOffType.PTO, DayOffStatus.REQUESTED);

        Map<UUID, Set<LocalDate>> map = SolverService.buildAgentDaysOffMap(List.of(pto));

        assertThat(map).doesNotContainKey(agent.getId());
    }

    // -----------------------------------------------------------------
    // MANDATORY rows (always block, status irrelevant)
    // -----------------------------------------------------------------

    @Test
    void mandatory_approved_blocksDay() {
        Agent agent = agent("A1");
        AgentDayOff mandatory = dayOff(agent, D1, DayOffType.MANDATORY, DayOffStatus.APPROVED);

        Map<UUID, Set<LocalDate>> map = SolverService.buildAgentDaysOffMap(List.of(mandatory));

        assertThat(map).containsKey(agent.getId());
        assertThat(map.get(agent.getId())).containsExactly(D1);
    }

    @Test
    void mandatory_requested_stillBlocksDay() {
        Agent agent = agent("A1");
        AgentDayOff mandatory = dayOff(agent, D1, DayOffType.MANDATORY, DayOffStatus.REQUESTED);

        Map<UUID, Set<LocalDate>> map = SolverService.buildAgentDaysOffMap(List.of(mandatory));

        assertThat(map).containsKey(agent.getId());
        assertThat(map.get(agent.getId())).containsExactly(D1);
    }

    // -----------------------------------------------------------------
    // Mixed agents: each agent's set is independent
    // -----------------------------------------------------------------

    @Test
    void multipleAgents_eachGetOwnBlockedDates() {
        Agent a1 = agent("A1");
        Agent a2 = agent("A2");

        List<AgentDayOff> daysOff = List.of(
                dayOff(a1, D1, DayOffType.PTO, DayOffStatus.APPROVED),       // blocks A1 D1
                dayOff(a1, D2, DayOffType.PTO, DayOffStatus.REQUESTED),      // does NOT block
                dayOff(a1, D3, DayOffType.MANDATORY, DayOffStatus.REQUESTED), // blocks A1 D3 (mandatory)
                dayOff(a2, D1, DayOffType.PTO, DayOffStatus.REQUESTED),      // does NOT block A2
                dayOff(a2, D2, DayOffType.PTO, DayOffStatus.APPROVED)        // blocks A2 D2
        );

        Map<UUID, Set<LocalDate>> map = SolverService.buildAgentDaysOffMap(daysOff);

        assertThat(map.get(a1.getId())).containsExactlyInAnyOrder(D1, D3);
        assertThat(map.get(a2.getId())).containsExactly(D2);
    }

    @Test
    void emptyList_returnsEmptyMap() {
        Map<UUID, Set<LocalDate>> map = SolverService.buildAgentDaysOffMap(Collections.emptyList());

        assertThat(map).isEmpty();
    }

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------

    private Agent agent(String bambooId) {
        Agent a = new Agent();
        a.setId(UUID.randomUUID());
        a.setTenantId(TENANT);
        a.setBamboohrId(bambooId);
        a.setName(bambooId);
        a.setActive(true);
        return a;
    }

    private AgentDayOff dayOff(Agent agent, LocalDate date, DayOffType type, DayOffStatus status) {
        AgentDayOff d = new AgentDayOff();
        d.setId(UUID.randomUUID());
        d.setTenantId(TENANT);
        d.setAgent(agent);
        d.setDate(date);
        d.setType(type);
        d.setStatus(status);
        return d;
    }
}
