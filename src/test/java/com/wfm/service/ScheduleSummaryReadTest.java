package com.wfm.service;

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import com.wfm.config.TenantContext;
import com.wfm.model.Desk;
import com.wfm.model.Schedule;
import com.wfm.model.ScheduleStatus;
import com.wfm.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code ScheduleService.getScheduleSummary} — the cheap read the results page polls while a solve
 * runs, instead of re-fetching the whole detail.
 *
 * <p><b>Why it exists.</b> The detail response is 4.16 MB on the live Vinted desk and ~2.4 s to
 * build (2.62 MB of {@code agentSchedule} across 1 356 agent-days alone). Polling it every 2 s for a
 * score both lagged the display and stole CPU from the solve, on a task whose
 * {@code parallelSolverCount} is 1 because it has two cores.
 *
 * <p>Plain instantiation with mocked repositories and a real {@link InMemoryScheduleStore} — no
 * Spring context, no MockMvc, following this package's existing precedent.
 */
class ScheduleSummaryReadTest {

    private static final long TENANT = 1L;
    private static final UUID DESK = UUID.randomUUID();

    private ScheduleRepository scheduleRepository;
    private DeskRepository deskRepository;
    private InMemoryScheduleStore inMemoryStore;
    private ScheduleOutputService outputService;
    private ScheduleService service;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT);
        scheduleRepository = mock(ScheduleRepository.class);
        deskRepository = mock(DeskRepository.class);
        inMemoryStore = new InMemoryScheduleStore();
        outputService = mock(ScheduleOutputService.class);

        Desk desk = new Desk();
        desk.setId(DESK);
        desk.setName("Vinted");
        when(deskRepository.findByIdAndTenantId(DESK, TENANT)).thenReturn(Optional.of(desk));

        service = new ScheduleService(scheduleRepository,
                mock(AcceptedScheduleDateRepository.class), deskRepository, inMemoryStore,
                mock(TimeslotRepository.class), mock(StaffingRequirementRepository.class),
                mock(AgentAssignmentRepository.class), mock(AgentShiftAssignmentRepository.class),
                mock(AgentPreferenceRepository.class), mock(AgentDayOffRepository.class),
                mock(ConstraintWeightsRepository.class), outputService, mock(EntityManager.class));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Schedule schedule(UUID id, ScheduleStatus status, int hard, int soft) {
        Schedule s = new Schedule();
        s.setId(id);
        s.setTenantId(TENANT);
        s.setDeskId(DESK);
        s.setStatus(status);
        s.setPeriodStartDate(LocalDate.of(2026, 9, 21));
        s.setPeriodEndDate(LocalDate.of(2026, 9, 27));
        s.setStartTime(LocalTime.of(8, 0));
        s.setEndTime(LocalTime.MIDNIGHT);
        s.setIncrementMinutes(60);
        s.setScore(HardSoftScore.of(hard, soft));
        return s;
    }

    @Test
    @DisplayName("a RUNNING schedule is served from the in-memory store, where it only exists")
    void runningScheduleComesFromMemory() {
        UUID id = UUID.randomUUID();
        inMemoryStore.put(schedule(id, ScheduleStatus.RUNNING, -4413, -40746));

        var summary = service.getScheduleSummary(DESK, id);

        assertThat(summary.status()).isEqualTo("RUNNING");
        assertThat(summary.score().hardScore()).isEqualTo(-4413);
        assertThat(summary.score().softScore()).isEqualTo(-40746);
        assertThat(summary.feasible()).isFalse();
        assertThat(summary.deskName()).isEqualTo("Vinted");
        verify(scheduleRepository, never()).findByIdAndTenantIdAndDeskId(any(), any(Long.class), any());
    }

    @Test
    @DisplayName("an accepted schedule falls back to the database")
    void acceptedScheduleComesFromTheDatabase() {
        UUID id = UUID.randomUUID();
        when(scheduleRepository.findByIdAndTenantIdAndDeskId(id, TENANT, DESK))
                .thenReturn(Optional.of(schedule(id, ScheduleStatus.ACCEPTED, 0, -2434)));

        var summary = service.getScheduleSummary(DESK, id);

        assertThat(summary.status()).isEqualTo("ACCEPTED");
        assertThat(summary.score().hardScore()).isZero();
        assertThat(summary.feasible()).isTrue();
    }

    @Test
    @DisplayName("it never builds the detail payload — that is the entire point")
    void doesNotTouchTheOutputService() {
        UUID id = UUID.randomUUID();
        inMemoryStore.put(schedule(id, ScheduleStatus.RUNNING, -1, -2426));

        service.getScheduleSummary(DESK, id);

        // ScheduleOutputService assembles the heavy sections — agentSchedule alone is 2.62 MB on
        // the live desk. If a future change routes this read through any of them, the endpoint
        // stops being cheap and this fails.
        verify(outputService, never()).buildAgentSchedule(any());
        verify(outputService, never()).buildStaffingSummary(any());
        verify(outputService, never()).buildPreferenceReport(any());
        verify(outputService, never()).buildDriftReport(any());
    }

    @Test
    @DisplayName("another tenant's schedule in memory is not visible")
    void foreignTenantIsNotServed() {
        UUID id = UUID.randomUUID();
        Schedule other = schedule(id, ScheduleStatus.RUNNING, 0, 0);
        other.setTenantId(TENANT + 1);
        inMemoryStore.put(other);
        when(scheduleRepository.findByIdAndTenantIdAndDeskId(id, TENANT, DESK))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getScheduleSummary(DESK, id))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("a schedule belonging to a different desk is not served")
    void foreignDeskIsNotServed() {
        UUID id = UUID.randomUUID();
        Schedule other = schedule(id, ScheduleStatus.RUNNING, 0, 0);
        other.setDeskId(UUID.randomUUID());
        inMemoryStore.put(other);
        when(scheduleRepository.findByIdAndTenantIdAndDeskId(id, TENANT, DESK))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getScheduleSummary(DESK, id))
                .isInstanceOf(RuntimeException.class);
    }
}
