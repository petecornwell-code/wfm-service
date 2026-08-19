package com.wfm.service;

import com.wfm.model.Agent;
import com.wfm.model.AgentDayOff;
import com.wfm.model.DayOffStatus;
import com.wfm.model.DayOffType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * D-09: BambooHR's dated PTO governs every date inside its synced window; the spreadsheet's
 * recurring weekly PTO pattern applies only to dates outside it. Tests call
 * {@code SolverService.arbitratePtoAgainstBambooWindow} directly — no Spring context, no
 * repository mocks, fixed dates so the suite cannot drift with the calendar.
 */
class PtoArbitrationTest {

    // Fixed six-week window, chosen away from month/year boundaries so "one day outside"
    // probes stay unambiguous.
    private static final LocalDate WINDOW_FROM = LocalDate.of(2026, 1, 10);
    private static final LocalDate WINDOW_TO = LocalDate.of(2026, 2, 20);

    private final UUID agentId = UUID.randomUUID();

    private AgentDayOff dayOff(LocalDate date, DayOffType type) {
        Agent agent = new Agent();
        agent.setId(agentId);
        AgentDayOff d = new AgentDayOff();
        d.setId(UUID.randomUUID());
        d.setAgent(agent);
        d.setDate(date);
        d.setType(type);
        d.setStatus(DayOffStatus.APPROVED);
        return d;
    }

    private List<AgentDayOff> arbitrate(AgentDayOff... facts) {
        return SolverService.arbitratePtoAgainstBambooWindow(List.of(facts), WINDOW_FROM, WINDOW_TO);
    }

    @Test
    void ptoInsideTheWindowIsDroppedSoTheAgentWorks() {
        AgentDayOff fact = dayOff(WINDOW_FROM.plusDays(5), DayOffType.PTO);

        assertThat(arbitrate(fact)).isEmpty();
    }

    @Test
    void ptoInsideTheWindowIsDroppedEvenWhenBambooHrAlsoHoldsAPtoRowThatDate() {
        // arbitratePtoAgainstBambooWindow only ever sees the recurring spreadsheet facts — the
        // persisted BambooHR PTO row this fact would be "redundant" against lives in the separate
        // list SolverService.startSolve already loaded (agentDayOffRepository, step 4) and is
        // untouched by this method. Dropping the recurring fact is correct either way: the
        // persisted row still blocks on its own.
        AgentDayOff recurringFact = dayOff(WINDOW_FROM.plusDays(5), DayOffType.PTO);

        assertThat(arbitrate(recurringFact)).isEmpty();
    }

    @Test
    void ptoOneDayAfterTheWindowsLastDayIsKept() {
        AgentDayOff fact = dayOff(WINDOW_TO.plusDays(1), DayOffType.PTO);

        assertThat(arbitrate(fact)).containsExactly(fact);
    }

    @Test
    void ptoOneDayBeforeTheWindowsFirstDayIsKept() {
        AgentDayOff fact = dayOff(WINDOW_FROM.minusDays(1), DayOffType.PTO);

        assertThat(arbitrate(fact)).containsExactly(fact);
    }

    @Test
    void ptoExactlyOnTheWindowsFirstDayIsInsideAndDropped() {
        AgentDayOff fact = dayOff(WINDOW_FROM, DayOffType.PTO);

        assertThat(arbitrate(fact)).isEmpty();
    }

    @Test
    void ptoExactlyOnTheWindowsLastDayIsInsideAndDropped() {
        AgentDayOff fact = dayOff(WINDOW_TO, DayOffType.PTO);

        assertThat(arbitrate(fact)).isEmpty();
    }

    @Test
    void ptoOneDayInsideEachBoundIsDropped() {
        AgentDayOff justAfterFrom = dayOff(WINDOW_FROM.plusDays(1), DayOffType.PTO);
        AgentDayOff justBeforeTo = dayOff(WINDOW_TO.minusDays(1), DayOffType.PTO);

        assertThat(arbitrate(justAfterFrom, justBeforeTo)).isEmpty();
    }

    @Test
    void mandatoryFactInsideTheWindowIsNeverTouchedByArbitration() {
        AgentDayOff fact = dayOff(WINDOW_FROM.plusDays(5), DayOffType.MANDATORY);

        assertThat(arbitrate(fact)).containsExactly(fact);
    }

    @Test
    void mandatoryFactOutsideTheWindowIsAlsoKept() {
        AgentDayOff fact = dayOff(WINDOW_TO.plusDays(10), DayOffType.MANDATORY);

        assertThat(arbitrate(fact)).containsExactly(fact);
    }

    @Test
    void emptyInputProducesEmptyOutputWithNoException() {
        assertThat(SolverService.arbitratePtoAgainstBambooWindow(List.of(), WINDOW_FROM, WINDOW_TO))
                .isEmpty();
    }

    @Test
    void preservesRelativeOrderOfKeptFacts() {
        AgentDayOff before = dayOff(WINDOW_FROM.minusDays(3), DayOffType.PTO);
        AgentDayOff mandatoryInside = dayOff(WINDOW_FROM.plusDays(2), DayOffType.MANDATORY);
        AgentDayOff after = dayOff(WINDOW_TO.plusDays(3), DayOffType.PTO);

        assertThat(arbitrate(before, mandatoryInside, after))
                .containsExactly(before, mandatoryInside, after);
    }

    @Test
    void doesNotMutateTheInputList() {
        AgentDayOff insideWindow = dayOff(WINDOW_FROM.plusDays(1), DayOffType.PTO);
        List<AgentDayOff> input = List.of(insideWindow);

        SolverService.arbitratePtoAgainstBambooWindow(input, WINDOW_FROM, WINDOW_TO);

        assertThat(input).containsExactly(insideWindow);
    }
}
