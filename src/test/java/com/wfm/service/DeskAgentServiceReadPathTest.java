package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.dto.DeskAgentResponse;
import com.wfm.model.Agent;
import com.wfm.model.AgentDayHours;
import com.wfm.model.DayOffType;
import com.wfm.model.Desk;
import com.wfm.model.Schedule;
import com.wfm.model.ScheduleStatus;
import com.wfm.repository.AgentDayHoursRepository;
import com.wfm.repository.AgentRepository;
import com.wfm.repository.DeskRepository;
import com.wfm.repository.ScheduleRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Read-path regression suite for I-1/F-1 (MDL-02): the roster must resolve every per-weekday
 * hours figure from agent_day_hours, never from the retired Agent.contractedHoursPerDay scalar
 * and never from Desk.defaultContractedHoursPerDay — "not set" falls back to the desk's
 * most-recently-created persisted Schedule's own default (D-06/P-01), or 8.00 when the desk has
 * zero persisted schedules.
 */
@DataJpaTest
@Import({DeskAgentService.class, UsualShiftResolutionService.class, UsualShiftService.class})
@ActiveProfiles("test")
class DeskAgentServiceReadPathTest {

    @Autowired
    private DeskAgentService deskAgentService;

    @Autowired
    private AgentRepository agentRepository;

    @Autowired
    private DeskRepository deskRepository;

    @Autowired
    private AgentDayHoursRepository agentDayHoursRepository;

    @Autowired
    private ScheduleRepository scheduleRepository;

    private static final long TENANT_ID = 1L;

    private Desk desk;
    private Agent agent;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_ID);

        desk = new Desk();
        desk.setTenantId(TENANT_ID);
        desk.setName("Support Desk");
        desk.setDefaultContractedHoursPerDay(new BigDecimal("5.00"));
        desk = deskRepository.save(desk);

        agent = new Agent();
        agent.setTenantId(TENANT_ID);
        agent.setBamboohrId("B100");
        agent.setName("Jane Doe");
        agent.setDeskId(desk.getId());
        agent = agentRepository.save(agent);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private void persistSchedule(BigDecimal defaultHours, OffsetDateTime createdAt) {
        Schedule schedule = new Schedule();
        schedule.setTenantId(TENANT_ID);
        schedule.setDeskId(desk.getId());
        schedule.setIncrementMinutes(15);
        schedule.setStartTime(LocalTime.of(8, 0));
        schedule.setEndTime(LocalTime.of(18, 0));
        schedule.setPeriodStartDate(LocalDate.of(2026, 1, 5));
        schedule.setPeriodEndDate(LocalDate.of(2026, 1, 11));
        schedule.setStatus(ScheduleStatus.COMPLETED);
        schedule.setDefaultContractedHoursPerDay(defaultHours);
        schedule.setCreatedAt(createdAt);
        scheduleRepository.save(schedule);
    }

    private void persistDayHours(DayOfWeek day, BigDecimal hours, DayOffType dayOffType) {
        AgentDayHours row = new AgentDayHours();
        row.setTenantId(TENANT_ID);
        row.setAgent(agent);
        row.setDayOfWeek(day);
        row.setHours(hours);
        row.setDayOffType(dayOffType);
        agentDayHoursRepository.save(row);
    }

    private DeskAgentResponse fetchRosterAgent() {
        List<DeskAgentResponse> roster = deskAgentService.listDeskAgentResponses(desk.getId(), null, null, 100);
        return roster.stream().filter(r -> r.id().equals(agent.getId())).findFirst().orElseThrow();
    }

    @Test
    void rosterIgnoresScalar_whenScalarDisagreesWithPerDayRows() {
        persistSchedule(new BigDecimal("9.00"), OffsetDateTime.now());
        agent.setContractedHoursPerDay(new BigDecimal("12.00"));
        agentRepository.save(agent);
        for (DayOfWeek day : DayOfWeek.values()) {
            persistDayHours(day, new BigDecimal("6.00"), null);
        }

        DeskAgentResponse response = fetchRosterAgent();

        for (DayOfWeek day : DayOfWeek.values()) {
            assertThat(response.dayHours().get(day).effectiveHours())
                    .isEqualByComparingTo(new BigDecimal("6.00"));
        }
        assertThat(response.effectiveContractedHoursPerDay()).isEqualByComparingTo(new BigDecimal("6.00"));
    }

    @Test
    void absentWeekdayRow_resolvesToScheduleDefault_notDeskDefault() {
        persistSchedule(new BigDecimal("9.00"), OffsetDateTime.now());
        persistDayHours(DayOfWeek.MONDAY, new BigDecimal("6.50"), null);

        DeskAgentResponse response = fetchRosterAgent();

        DeskAgentResponse.DayHoursEntry monday = response.dayHours().get(DayOfWeek.MONDAY);
        assertThat(monday.hasRow()).isTrue();
        assertThat(monday.effectiveHours()).isEqualByComparingTo(new BigDecimal("6.50"));

        for (DayOfWeek day : DayOfWeek.values()) {
            if (day == DayOfWeek.MONDAY) continue;
            DeskAgentResponse.DayHoursEntry entry = response.dayHours().get(day);
            assertThat(entry.hasRow()).isFalse();
            assertThat(entry.effectiveHours()).isEqualByComparingTo(new BigDecimal("9.00"));
        }
    }

    @Test
    void noPersistedSchedule_fallsBackToEightHours() {
        DeskAgentResponse response = fetchRosterAgent();

        for (DayOfWeek day : DayOfWeek.values()) {
            assertThat(response.dayHours().get(day).effectiveHours())
                    .isEqualByComparingTo(new BigDecimal("8.00"));
        }
    }

    @Test
    void mostRecentlyCreatedScheduleWins() {
        persistSchedule(new BigDecimal("6.00"), OffsetDateTime.now().minusDays(2));
        persistSchedule(new BigDecimal("10.00"), OffsetDateTime.now());

        DeskAgentResponse response = fetchRosterAgent();

        assertThat(response.dayHours().get(DayOfWeek.TUESDAY).effectiveHours())
                .isEqualByComparingTo(new BigDecimal("10.00"));
    }

    @Test
    void explicitZeroRow_isNotTreatedAsNotSet() {
        persistSchedule(new BigDecimal("9.00"), OffsetDateTime.now());
        persistDayHours(DayOfWeek.WEDNESDAY, new BigDecimal("0.00"), null);

        DeskAgentResponse response = fetchRosterAgent();

        DeskAgentResponse.DayHoursEntry wednesday = response.dayHours().get(DayOfWeek.WEDNESDAY);
        assertThat(wednesday.hasRow()).isTrue();
        assertThat(wednesday.hours()).isEqualByComparingTo(new BigDecimal("0.00"));
        assertThat(wednesday.dayOffType()).isNull();
        assertThat(wednesday.effectiveHours()).isEqualByComparingTo(new BigDecimal("0.00"));
    }

    @Test
    void mandatoryAndPtoRows_keepTheirLabels() {
        persistDayHours(DayOfWeek.SATURDAY, new BigDecimal("0.00"), DayOffType.MANDATORY);
        persistDayHours(DayOfWeek.SUNDAY, new BigDecimal("0.00"), DayOffType.PTO);

        DeskAgentResponse response = fetchRosterAgent();

        DeskAgentResponse.DayHoursEntry saturday = response.dayHours().get(DayOfWeek.SATURDAY);
        assertThat(saturday.dayOffType()).isEqualTo(DayOffType.MANDATORY);
        assertThat(saturday.hours()).isEqualByComparingTo(new BigDecimal("0.00"));

        DeskAgentResponse.DayHoursEntry sunday = response.dayHours().get(DayOfWeek.SUNDAY);
        assertThat(sunday.dayOffType()).isEqualTo(DayOffType.PTO);
        assertThat(sunday.hours()).isEqualByComparingTo(new BigDecimal("0.00"));
    }

    @Test
    void dayHoursMapAlwaysHasSevenEntries() {
        DeskAgentResponse response = fetchRosterAgent();

        assertThat(response.dayHours().keySet()).containsExactlyInAnyOrder(DayOfWeek.values());
    }

    @Test
    void effectiveContractedHoursPerDay_isTheLongestContractedWeekday() {
        for (DayOfWeek day : List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)) {
            persistDayHours(day, new BigDecimal("8.00"), null);
        }
        persistDayHours(DayOfWeek.SATURDAY, new BigDecimal("0.00"), null);
        persistDayHours(DayOfWeek.SUNDAY, new BigDecimal("0.00"), null);

        DeskAgentResponse response = fetchRosterAgent();

        assertThat(response.effectiveContractedHoursPerDay()).isEqualByComparingTo(new BigDecimal("8.00"));
    }
}
