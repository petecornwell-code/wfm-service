package com.wfm.service;

import com.wfm.model.Agent;
import com.wfm.model.AgentDayHours;
import com.wfm.model.AgentDayOff;
import com.wfm.model.DayOffStatus;
import com.wfm.model.DayOffType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The solver must treat MANDATORY/PTO from the desk-assignment upload as days off.
 *
 * Those labels live on agent_day_hours keyed by day-of-week; the solver previously read that
 * table for getHours() only and built its days-off map purely from agent_day_off (BambooHR).
 * An agent marked MANDATORY on the spreadsheet was therefore still scheduled that day — zero
 * contracted hours alone does not stop an assignment (found in UAT 2026-08-12).
 */
class SolverServiceRecurringDaysOffTest {

    // Mon 2026-01-05 .. Sun 2026-01-18 — exactly two weeks.
    private static final LocalDate FROM = LocalDate.of(2026, 1, 5);
    private static final LocalDate TO = LocalDate.of(2026, 1, 18);

    private final UUID agentId = UUID.randomUUID();

    private AgentDayHours dayHours(DayOfWeek day, DayOffType type, String hours) {
        Agent agent = new Agent();
        agent.setId(agentId);
        AgentDayHours h = new AgentDayHours();
        h.setId(UUID.randomUUID());
        h.setAgent(agent);
        h.setDayOfWeek(day);
        h.setHours(new BigDecimal(hours));
        h.setDayOffType(type);
        return h;
    }

    private List<AgentDayOff> expand(AgentDayHours... rows) {
        return SolverService.buildRecurringDaysOff(1L, List.of(rows), FROM, TO);
    }

    @Test
    void mandatoryDayProducesAFactForEveryMatchingDate() {
        List<AgentDayOff> facts = expand(dayHours(DayOfWeek.SATURDAY, DayOffType.MANDATORY, "0.00"));

        assertThat(facts).extracting(AgentDayOff::getDate).containsExactly(
                LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 17));
        assertThat(facts).allSatisfy(f -> {
            assertThat(f.getType()).isEqualTo(DayOffType.MANDATORY);
            assertThat(f.getAgent().getId()).isEqualTo(agentId);
            assertThat(f.getId()).isNotNull();
        });
    }

    @Test
    void ptoIsMarkedApprovedSoItBlocks() {
        // buildAgentDaysOffMap only blocks PTO when APPROVED; a REQUESTED status would make
        // spreadsheet PTO silently non-blocking.
        List<AgentDayOff> facts = expand(dayHours(DayOfWeek.WEDNESDAY, DayOffType.PTO, "0.00"));

        assertThat(facts).hasSize(2)
                .allSatisfy(f -> assertThat(f.getStatus()).isEqualTo(DayOffStatus.APPROVED));
    }

    @Test
    void workingDayWithHoursProducesNoFact() {
        assertThat(expand(dayHours(DayOfWeek.MONDAY, null, "8.00"))).isEmpty();
    }

    @Test
    void expandedFactsBlockInTheDaysOffMap() {
        // End-to-end through the same helper the solver uses, proving the facts actually block.
        List<AgentDayOff> facts = expand(dayHours(DayOfWeek.SATURDAY, DayOffType.MANDATORY, "0.00"));

        Map<UUID, Set<LocalDate>> map = SolverService.buildAgentDaysOffMap(facts);

        assertThat(map.get(agentId)).containsExactlyInAnyOrder(
                LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 17));
    }

    @Test
    void allSevenDaysMandatoryBlocksTheWholePeriod() {
        // The live data shape that surfaced this: three agents whose spreadsheet rows had
        // MANDATORY in every day cell.
        List<AgentDayOff> facts = expand(
                dayHours(DayOfWeek.MONDAY, DayOffType.MANDATORY, "0.00"),
                dayHours(DayOfWeek.TUESDAY, DayOffType.MANDATORY, "0.00"),
                dayHours(DayOfWeek.WEDNESDAY, DayOffType.MANDATORY, "0.00"),
                dayHours(DayOfWeek.THURSDAY, DayOffType.MANDATORY, "0.00"),
                dayHours(DayOfWeek.FRIDAY, DayOffType.MANDATORY, "0.00"),
                dayHours(DayOfWeek.SATURDAY, DayOffType.MANDATORY, "0.00"),
                dayHours(DayOfWeek.SUNDAY, DayOffType.MANDATORY, "0.00"));

        assertThat(facts).hasSize(14);
        assertThat(SolverService.buildAgentDaysOffMap(facts).get(agentId)).hasSize(14);
    }

}
