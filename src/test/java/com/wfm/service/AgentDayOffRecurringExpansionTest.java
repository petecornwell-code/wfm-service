package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.dto.AgentDayOffResponse;
import com.wfm.model.*;
import com.wfm.repository.AgentDayHoursRepository;
import com.wfm.repository.AgentDayOffRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The desk PTO view must show MANDATORY/PTO entered on the desk-assignment upload spreadsheet.
 *
 * The upload writes those to agent_day_hours (keyed by day-of-week); the PTO tab reads
 * agent_day_off (keyed by date, populated only from BambooHR). Before 2026-08-12 the two never
 * met, so nothing uploaded ever appeared on the tab.
 */
class AgentDayOffRecurringExpansionTest {

    private static final long TENANT = 1L;
    private static final UUID DESK = UUID.randomUUID();

    private AgentDayOffRepository dayOffRepository;
    private AgentDayHoursRepository dayHoursRepository;
    private AgentDayOffService service;

    private Agent agent;

    @BeforeEach
    void setUp() {
        dayOffRepository = mock(AgentDayOffRepository.class);
        dayHoursRepository = mock(AgentDayHoursRepository.class);
        service = new AgentDayOffService(dayOffRepository, dayHoursRepository);
        TenantContext.setTenantId(TENANT);

        agent = new Agent();
        agent.setId(UUID.randomUUID());
        agent.setTenantId(TENANT);
        agent.setDeskId(DESK);
        agent.setName("Ada Lovelace");

        when(dayOffRepository.findByTenantIdAndDeskIdAndDateBetween(anyLong(), any(), any(), any()))
                .thenReturn(List.of());
        when(dayHoursRepository.findDaysOffByTenantIdAndDeskId(anyLong(), any())).thenReturn(List.of());
    }

    private AgentDayHours dayHours(DayOfWeek day, DayOffType type) {
        AgentDayHours h = new AgentDayHours();
        h.setId(UUID.randomUUID());
        h.setTenantId(TENANT);
        h.setAgent(agent);
        h.setDayOfWeek(day);
        h.setHours(BigDecimal.ZERO);
        h.setDayOffType(type);
        return h;
    }

    private AgentDayOff dayOff(LocalDate date, DayOffType type) {
        AgentDayOff d = new AgentDayOff();
        d.setId(UUID.randomUUID());
        d.setTenantId(TENANT);
        d.setAgent(agent);
        d.setDate(date);
        d.setType(type);
        d.setStatus(DayOffStatus.APPROVED);
        return d;
    }

    // Mon 2026-01-05 .. Sun 2026-01-18 — exactly two weeks.
    private static final LocalDate FROM = LocalDate.of(2026, 1, 5);
    private static final LocalDate TO = LocalDate.of(2026, 1, 18);

    private List<AgentDayOffResponse> list() {
        return service.listDaysOffForDesk(DESK, FROM.toString(), TO.toString());
    }

    @Test
    void recurringMandatoryAppearsOnEveryMatchingDateInRange() {
        when(dayHoursRepository.findDaysOffByTenantIdAndDeskId(anyLong(), any()))
                .thenReturn(List.of(dayHours(DayOfWeek.SATURDAY, DayOffType.MANDATORY)));

        List<AgentDayOffResponse> result = list();

        // Two Saturdays in the range: 10th and 17th.
        assertThat(result).hasSize(2);
        assertThat(result).allSatisfy(r -> assertThat(r.type()).isEqualTo("MANDATORY"));
        assertThat(result).extracting(AgentDayOffResponse::date)
                .containsExactly(LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 17));
    }

    @Test
    void recurringPtoIsExpandedToo() {
        when(dayHoursRepository.findDaysOffByTenantIdAndDeskId(anyLong(), any()))
                .thenReturn(List.of(dayHours(DayOfWeek.WEDNESDAY, DayOffType.PTO)));

        assertThat(list()).hasSize(2)
                .allSatisfy(r -> assertThat(r.type()).isEqualTo("PTO"));
    }

    @Test
    void workingDaysWithNoDayOffTypeAreNotExpanded() {
        AgentDayHours working = dayHours(DayOfWeek.MONDAY, null);
        working.setHours(new BigDecimal("8.00"));
        when(dayHoursRepository.findDaysOffByTenantIdAndDeskId(anyLong(), any())).thenReturn(List.of(working));

        assertThat(list()).isEmpty();
    }

    @Test
    void realDayOffWinsOverRecurringForTheSameDate() {
        // A BambooHR-sourced row carries approval status and a real id, so it must not be
        // duplicated by the derived entry for the same agent and date.
        LocalDate saturday = LocalDate.of(2026, 1, 10);
        when(dayOffRepository.findByTenantIdAndDeskIdAndDateBetween(anyLong(), any(), any(), any()))
                .thenReturn(List.of(dayOff(saturday, DayOffType.PTO)));
        when(dayHoursRepository.findDaysOffByTenantIdAndDeskId(anyLong(), any()))
                .thenReturn(List.of(dayHours(DayOfWeek.SATURDAY, DayOffType.MANDATORY)));

        List<AgentDayOffResponse> result = list();

        assertThat(result).hasSize(2); // the real one on the 10th, the derived one on the 17th
        assertThat(result).filteredOn(r -> r.date().equals(saturday))
                .singleElement()
                .satisfies(r -> assertThat(r.type()).isEqualTo("PTO")); // real row won
    }

    @Test
    void bambooHrDaysOffStillReturnedWhenNoRecurringDataExists() {
        // Regression guard: the expansion must not disturb the pre-existing behaviour.
        when(dayOffRepository.findByTenantIdAndDeskIdAndDateBetween(anyLong(), any(), any(), any()))
                .thenReturn(List.of(dayOff(LocalDate.of(2026, 1, 6), DayOffType.PTO)));

        assertThat(list()).singleElement()
                .satisfies(r -> assertThat(r.date()).isEqualTo(LocalDate.of(2026, 1, 6)));
    }
}
