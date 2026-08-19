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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * D-05: a weekday the spreadsheet's day group states as worked un-blocks a stale BambooHR
 * field-4517 MANDATORY row for that weekday. Tests call
 * {@code SolverService.unblockSheetWorkedDays} directly — no Spring context, no repository
 * mocks, fixed dates so the suite cannot drift with the calendar.
 */
class SheetPatternUnblockTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 1, 5); // a Monday
    private static final LocalDate TUESDAY = LocalDate.of(2026, 1, 6);

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

    /** type == null models a sheet cell the un-block pass treats as worked (contracted hours,
     *  including an explicit zero-hours cell per the 2026-08-18 operator revision). */
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
    void mandatoryDayOffOnAWeekdayTheSheetGivesContractedHoursForIsRemoved() {
        List<AgentDayOff> persisted = new ArrayList<>(List.of(dayOff(MONDAY, DayOffType.MANDATORY)));
        List<AgentDayHours> sheet = List.of(dayHours(DayOfWeek.MONDAY, null, "8.00"));

        SolverService.unblockSheetWorkedDays(persisted, sheet);

        assertThat(persisted).isEmpty();
    }

    @Test
    void mandatoryDayOffOnAWeekdayTheSheetAlsoLabelsMandatoryIsKept() {
        AgentDayOff row = dayOff(MONDAY, DayOffType.MANDATORY);
        List<AgentDayOff> persisted = new ArrayList<>(List.of(row));
        List<AgentDayHours> sheet = List.of(dayHours(DayOfWeek.MONDAY, DayOffType.MANDATORY, "0.00"));

        SolverService.unblockSheetWorkedDays(persisted, sheet);

        assertThat(persisted).containsExactly(row);
    }

    @Test
    void ptoDayOffOnAWeekdayTheSheetGivesContractedHoursForIsNeverUnblocked() {
        AgentDayOff row = dayOff(MONDAY, DayOffType.PTO);
        List<AgentDayOff> persisted = new ArrayList<>(List.of(row));
        List<AgentDayHours> sheet = List.of(dayHours(DayOfWeek.MONDAY, null, "8.00"));

        SolverService.unblockSheetWorkedDays(persisted, sheet);

        assertThat(persisted).containsExactly(row);
    }

    @Test
    void zeroHoursCellStillCountsAsWorkedNotUnavailable() {
        // 2026-08-18 operator revision: dayOffType == null with hours == 0 still means "worked",
        // not "unavailable" — zero hours describes contracted hours, not eligibility to be
        // scheduled that day.
        AgentDayOff row = dayOff(MONDAY, DayOffType.MANDATORY);
        List<AgentDayOff> persisted = new ArrayList<>(List.of(row));
        List<AgentDayHours> sheet = List.of(dayHours(DayOfWeek.MONDAY, null, "0.00"));

        SolverService.unblockSheetWorkedDays(persisted, sheet);

        assertThat(persisted).isEmpty();
    }

    @Test
    void agentWithNoAgentDayHoursRowsKeepsEveryPersistedRow() {
        AgentDayOff mandatory = dayOff(MONDAY, DayOffType.MANDATORY);
        AgentDayOff pto = dayOff(TUESDAY, DayOffType.PTO);
        List<AgentDayOff> persisted = new ArrayList<>(List.of(mandatory, pto));

        SolverService.unblockSheetWorkedDays(persisted, List.of());

        assertThat(persisted).containsExactlyInAnyOrder(mandatory, pto);
    }

    @Test
    void onlyTheMatchingWeekdayIsUnblockedNotOthers() {
        AgentDayOff mondayRow = dayOff(MONDAY, DayOffType.MANDATORY);
        AgentDayOff tuesdayRow = dayOff(TUESDAY, DayOffType.MANDATORY);
        List<AgentDayOff> persisted = new ArrayList<>(List.of(mondayRow, tuesdayRow));
        List<AgentDayHours> sheet = List.of(dayHours(DayOfWeek.MONDAY, null, "8.00"));

        SolverService.unblockSheetWorkedDays(persisted, sheet);

        assertThat(persisted).containsExactly(tuesdayRow);
    }

    @Test
    void emptyPersistedListStaysEmptyWithNoException() {
        List<AgentDayOff> persisted = new ArrayList<>();

        SolverService.unblockSheetWorkedDays(persisted, List.of(dayHours(DayOfWeek.MONDAY, null, "8.00")));

        assertThat(persisted).isEmpty();
    }

    @Test
    void emptySheetLeavesPersistedRowsUnchangedWithNoException() {
        AgentDayOff row = dayOff(MONDAY, DayOffType.PTO);
        List<AgentDayOff> persisted = new ArrayList<>(List.of(row));

        SolverService.unblockSheetWorkedDays(persisted, List.of());

        assertThat(persisted).containsExactly(row);
    }
}
