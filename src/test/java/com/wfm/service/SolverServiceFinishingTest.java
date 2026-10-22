package com.wfm.service;

import ai.timefold.solver.core.api.solver.SolverManager;
import com.wfm.model.Schedule;
import com.wfm.model.ScheduleStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * The post-solve repairs rewrite seats in place on a schedule the store is already serving. A
 * results-page read mid-repair crashed the violation report ("AgentAssignment.getAgent() is
 * null"), and Accept could persist a half-repaired roster. The schedule must therefore read
 * FINISHING for the whole repair, and only then settle on COMPLETED -- or STOPPED when the
 * operator pressed Stop.
 */
class SolverServiceFinishingTest {

    private InMemoryScheduleStore store;
    @SuppressWarnings("unchecked")
    private final SolverManager<Schedule, UUID> solverManager = mock(SolverManager.class);
    private final ScheduleConsistencyRepairService consistencyRepair = mock(ScheduleConsistencyRepairService.class);
    private final ScheduleEnvelopeRepairService envelopeRepair = mock(ScheduleEnvelopeRepairService.class);
    private SolverService solverService;

    @BeforeEach
    void setUp() {
        store = new InMemoryScheduleStore();
        solverService = new SolverService(Duration.ofMinutes(1), store, solverManager,
                consistencyRepair, envelopeRepair, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null);
    }

    private Schedule runningSchedule() {
        Schedule schedule = new Schedule();
        schedule.setId(UUID.randomUUID());
        schedule.setTenantId(1L);
        schedule.setDeskId(UUID.randomUUID());
        schedule.setStatus(ScheduleStatus.RUNNING);
        store.put(schedule);
        return schedule;
    }

    /** Records the status a reader of the store would see while each repair runs. */
    private AtomicReference<ScheduleStatus> statusSeenDuringEnvelopeRepair(Schedule schedule) {
        AtomicReference<ScheduleStatus> seen = new AtomicReference<>();
        doAnswer(inv -> {
            seen.set(store.get(schedule.getId()).orElseThrow().getStatus());
            return null;
        }).when(envelopeRepair).repairVerified(eq(schedule), any());
        return seen;
    }

    @Test
    void finishSolve_readsFinishingDuringTheRepairs_thenCompleted() {
        Schedule schedule = runningSchedule();
        AtomicReference<ScheduleStatus> seen = statusSeenDuringEnvelopeRepair(schedule);

        solverService.finishSolve(schedule);

        assertThat(seen.get()).isEqualTo(ScheduleStatus.FINISHING);
        assertThat(store.get(schedule.getId()).orElseThrow().getStatus()).isEqualTo(ScheduleStatus.COMPLETED);
        verify(consistencyRepair).repairVerified(eq(schedule), any());
    }

    @Test
    void stopSolve_readsFinishingNotStopped_untilTheRepairsAreDone() {
        Schedule schedule = runningSchedule();
        AtomicReference<ScheduleStatus> seen = statusSeenDuringEnvelopeRepair(schedule);

        solverService.stopSolve(schedule.getDeskId(), schedule.getId());

        // Between the Stop click and the final callback the page must not see STOPPED -- that is
        // what let it load its "final" tables, and enable Accept, before the repairs had run.
        assertThat(store.get(schedule.getId()).orElseThrow().getStatus()).isEqualTo(ScheduleStatus.FINISHING);
        verify(solverManager).terminateEarly(schedule.getId());

        solverService.finishSolve(schedule);

        assertThat(seen.get()).isEqualTo(ScheduleStatus.FINISHING);
        assertThat(store.get(schedule.getId()).orElseThrow().getStatus()).isEqualTo(ScheduleStatus.STOPPED);
    }

    @Test
    void finishSolve_settlesTheStatusEvenWhenARepairThrowsAnError() {
        Schedule schedule = runningSchedule();
        doAnswer(inv -> { throw new AssertionError("simulated non-RuntimeException failure"); })
                .when(envelopeRepair).repairVerified(eq(schedule), any());

        try {
            solverService.finishSolve(schedule);
        } catch (AssertionError expected) {
            // an Error is not swallowed -- but the status must still settle
        }

        assertThat(store.get(schedule.getId()).orElseThrow().getStatus()).isEqualTo(ScheduleStatus.COMPLETED);
    }
}
