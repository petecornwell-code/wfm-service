package com.wfm.service;

import com.wfm.model.Agent;
import com.wfm.model.AgentDayHours;
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

    @Test
    void mandatoryDayBlocksEveryMatchingDateInPeriod() {
        Map<UUID, Set<LocalDate>> map = new HashMap<>();

        SolverService.addRecurringDaysOff(map,
                List.of(dayHours(DayOfWeek.SATURDAY, DayOffType.MANDATORY, "0.00")), FROM, TO);

        assertThat(map.get(agentId)).containsExactlyInAnyOrder(
                LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 17));
    }

    @Test
    void ptoDayBlocksToo() {
        Map<UUID, Set<LocalDate>> map = new HashMap<>();

        SolverService.addRecurringDaysOff(map,
                List.of(dayHours(DayOfWeek.WEDNESDAY, DayOffType.PTO, "0.00")), FROM, TO);

        assertThat(map.get(agentId)).containsExactlyInAnyOrder(
                LocalDate.of(2026, 1, 7), LocalDate.of(2026, 1, 14));
    }

    @Test
    void workingDayWithHoursDoesNotBlock() {
        Map<UUID, Set<LocalDate>> map = new HashMap<>();

        SolverService.addRecurringDaysOff(map,
                List.of(dayHours(DayOfWeek.MONDAY, null, "8.00")), FROM, TO);

        assertThat(map).isEmpty();
    }

    @Test
    void unionsWithExistingBambooHrDaysOffWithoutLosingThem() {
        LocalDate bambooDate = LocalDate.of(2026, 1, 6);
        Map<UUID, Set<LocalDate>> map = new HashMap<>();
        map.put(agentId, new HashSet<>(Set.of(bambooDate)));

        SolverService.addRecurringDaysOff(map,
                List.of(dayHours(DayOfWeek.SATURDAY, DayOffType.MANDATORY, "0.00")), FROM, TO);

        assertThat(map.get(agentId)).contains(bambooDate);
        assertThat(map.get(agentId)).hasSize(3); // the BambooHR date + two Saturdays
    }

    @Test
    void overlappingDateIsNotDuplicated() {
        LocalDate saturday = LocalDate.of(2026, 1, 10);
        Map<UUID, Set<LocalDate>> map = new HashMap<>();
        map.put(agentId, new HashSet<>(Set.of(saturday)));

        SolverService.addRecurringDaysOff(map,
                List.of(dayHours(DayOfWeek.SATURDAY, DayOffType.MANDATORY, "0.00")), FROM, TO);

        assertThat(map.get(agentId)).hasSize(2); // 10th already present, 17th added
    }

    @Test
    void allSevenDaysMandatoryBlocksTheWholePeriod() {
        // The live data shape that surfaced this: three agents whose spreadsheet rows had
        // MANDATORY in every day cell.
        Map<UUID, Set<LocalDate>> map = new HashMap<>();
        List<AgentDayHours> everyDayOff = List.of(
                dayHours(DayOfWeek.MONDAY, DayOffType.MANDATORY, "0.00"),
                dayHours(DayOfWeek.TUESDAY, DayOffType.MANDATORY, "0.00"),
                dayHours(DayOfWeek.WEDNESDAY, DayOffType.MANDATORY, "0.00"),
                dayHours(DayOfWeek.THURSDAY, DayOffType.MANDATORY, "0.00"),
                dayHours(DayOfWeek.FRIDAY, DayOffType.MANDATORY, "0.00"),
                dayHours(DayOfWeek.SATURDAY, DayOffType.MANDATORY, "0.00"),
                dayHours(DayOfWeek.SUNDAY, DayOffType.MANDATORY, "0.00"));

        SolverService.addRecurringDaysOff(map, everyDayOff, FROM, TO);

        assertThat(map.get(agentId)).hasSize(14); // every date in the two-week period
    }
}
