package com.wfm.service;

import com.wfm.model.Agent;
import com.wfm.model.AgentDayOff;
import com.wfm.model.DayOffStatus;
import com.wfm.model.DayOffType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WR-01: the "Agent day off" HARD constraint joins {@link AgentDayOff} problem facts directly, so
 * two facts for one real-world day-off are matched twice — the date is blocked either way, but the
 * reported violation count and hard-score magnitude for that agent-date roughly double. BambooHR
 * and the spreadsheet agreeing an agent is off (both marking a weekend day MANDATORY) is the
 * common case, so the recurring facts must be deduplicated against the persisted rows before both
 * are handed to the solver.
 *
 * Tests call {@code SolverService.dedupeAgainstPersisted} directly — no Spring context, no
 * repository mocks, fixed dates so the suite cannot drift with the calendar.
 */
class RecurringFactDedupeTest {

    private static final LocalDate SATURDAY = LocalDate.of(2026, 1, 3); // a Saturday
    private static final LocalDate SUNDAY = LocalDate.of(2026, 1, 4);
    private static final LocalDate MONDAY = LocalDate.of(2026, 1, 5);

    private final UUID agentId = UUID.randomUUID();
    private final UUID otherAgentId = UUID.randomUUID();

    private AgentDayOff dayOff(UUID agent, LocalDate date, DayOffType type) {
        Agent a = new Agent();
        a.setId(agent);
        AgentDayOff d = new AgentDayOff();
        d.setId(UUID.randomUUID());
        d.setAgent(a);
        d.setDate(date);
        d.setType(type);
        d.setStatus(DayOffStatus.APPROVED);
        return d;
    }

    @Test
    void recurringFactDuplicatingAPersistedRowForTheSameAgentAndDateIsDropped() {
        List<AgentDayOff> persisted = new ArrayList<>(List.of(dayOff(agentId, SATURDAY, DayOffType.MANDATORY)));
        List<AgentDayOff> recurring = List.of(dayOff(agentId, SATURDAY, DayOffType.MANDATORY));

        List<AgentDayOff> result = SolverService.dedupeAgainstPersisted(recurring, persisted);

        assertThat(result).isEmpty();
    }

    @Test
    void recurringFactForADateNoPersistedRowCoversIsKept() {
        List<AgentDayOff> persisted = new ArrayList<>(List.of(dayOff(agentId, SATURDAY, DayOffType.MANDATORY)));
        AgentDayOff sundayFact = dayOff(agentId, SUNDAY, DayOffType.MANDATORY);

        List<AgentDayOff> result = SolverService.dedupeAgainstPersisted(List.of(sundayFact), persisted);

        assertThat(result).containsExactly(sundayFact);
    }

    @Test
    void aDifferentAgentOnTheSameDateIsNotTreatedAsADuplicate() {
        List<AgentDayOff> persisted = new ArrayList<>(List.of(dayOff(agentId, SATURDAY, DayOffType.MANDATORY)));
        AgentDayOff otherAgentFact = dayOff(otherAgentId, SATURDAY, DayOffType.MANDATORY);

        List<AgentDayOff> result = SolverService.dedupeAgainstPersisted(List.of(otherAgentFact), persisted);

        assertThat(result).containsExactly(otherAgentFact);
    }

    /** The date is blocked either way, so one fact is enough regardless of which label it carries. */
    @Test
    void aDifferingDayOffTypeOnTheSameAgentAndDateIsStillADuplicate() {
        List<AgentDayOff> persisted = new ArrayList<>(List.of(dayOff(agentId, SATURDAY, DayOffType.PTO)));
        List<AgentDayOff> recurring = List.of(dayOff(agentId, SATURDAY, DayOffType.MANDATORY));

        List<AgentDayOff> result = SolverService.dedupeAgainstPersisted(recurring, persisted);

        assertThat(result).isEmpty();
    }

    @Test
    void twoRecurringFactsForTheSameAgentAndDateCollapseToOne() {
        AgentDayOff first = dayOff(agentId, MONDAY, DayOffType.MANDATORY);
        AgentDayOff second = dayOff(agentId, MONDAY, DayOffType.MANDATORY);

        List<AgentDayOff> result = SolverService.dedupeAgainstPersisted(List.of(first, second), List.of());

        assertThat(result).containsExactly(first);
    }

    @Test
    void neitherInputListIsModified() {
        List<AgentDayOff> persisted = new ArrayList<>(List.of(dayOff(agentId, SATURDAY, DayOffType.MANDATORY)));
        List<AgentDayOff> recurring = new ArrayList<>(List.of(
                dayOff(agentId, SATURDAY, DayOffType.MANDATORY),
                dayOff(agentId, SUNDAY, DayOffType.MANDATORY)));

        SolverService.dedupeAgainstPersisted(recurring, persisted);

        assertThat(persisted).hasSize(1);
        assertThat(recurring).hasSize(2);
    }

    @Test
    void withNoPersistedRowsEveryDistinctRecurringFactSurvivesInOrder() {
        AgentDayOff sat = dayOff(agentId, SATURDAY, DayOffType.MANDATORY);
        AgentDayOff sun = dayOff(agentId, SUNDAY, DayOffType.MANDATORY);

        List<AgentDayOff> result = SolverService.dedupeAgainstPersisted(List.of(sat, sun), List.of());

        assertThat(result).containsExactly(sat, sun);
    }
}
