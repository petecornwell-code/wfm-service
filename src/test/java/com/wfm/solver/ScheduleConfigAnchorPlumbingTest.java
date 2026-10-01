package com.wfm.solver;

import com.wfm.dto.SolveRequest;
import com.wfm.model.Desk;
import com.wfm.model.Schedule;
import com.wfm.model.SchedulingMode;
import com.wfm.service.SolverServiceBuildScheduleAccess;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BDAY-04, plan 19-01 (D-10's additive commit): proves the desk-to-solver anchor channel end to
 * end — {@code Desk.dayStart -> SolverService.buildSchedule -> Schedule.dayStart ->
 * Schedule.getScheduleConfig() -> ScheduleConfig.dayStart()} — without anything downstream
 * reading the value yet. This plan's own tests are the proof that the addition is provably a
 * no-op; nothing here is a tracer for plan 19-03's consumption.
 *
 * <p>The desk's {@code dayStart} is set directly on the entity (bypassing
 * {@code DeskService.setDayStart}'s gated refusal of anything but {@code 00:00}, exactly as
 * {@code TimeslotGeneratorBusinessDateTest} already does) because this test proves the plumbing
 * hop, not the gate — the gate itself stays untouched by this phase (D-01).
 *
 * <p>{@link SolverServiceBuildScheduleAccess} is a test-only bridge exposing
 * {@code SolverService.buildSchedule} — package-private and static in {@code com.wfm.service} —
 * to this test in {@code com.wfm.solver}, mirroring the {@code SolverSeatSupplyGateAccess} /
 * {@code SolverSeatExpansionAccess} precedent already established in this codebase.
 */
class ScheduleConfigAnchorPlumbingTest {

    private static final long TENANT_ID = 1L;

    @Test
    @DisplayName("a Schedule built from a desk whose dayStart is 21:00 exposes 21:00 through getScheduleConfig().dayStart()")
    void deskDayStartReachesScheduleConfig() {
        Desk desk = new Desk();
        desk.setId(UUID.randomUUID());
        desk.setTenantId(TENANT_ID);
        desk.setName("Overnight Desk");
        desk.setSchedulingMode(SchedulingMode.SLOT);
        desk.setDayStart(LocalTime.of(21, 0)); // bypasses DeskService's gated setter, on purpose

        SolveRequest request = new SolveRequest(
                LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 11),
                LocalTime.of(0, 0), LocalTime.of(0, 0),
                30, null, null, null, null, null, null, null, null, null, null);

        Schedule schedule = SolverServiceBuildScheduleAccess.buildSchedule(
                TENANT_ID, desk.getId(), request, desk);

        assertThat(schedule.getDayStart()).isEqualTo(LocalTime.of(21, 0));
        assertThat(schedule.getScheduleConfig().dayStart()).isEqualTo(LocalTime.of(21, 0));
    }

    @Test
    @DisplayName("a Schedule with no dayStart set exposes null from getScheduleConfig().dayStart(), never a silently substituted midnight")
    void unsetDayStartSurfacesAsNullNotMidnight() {
        Schedule schedule = new Schedule();

        assertThat(schedule.getDayStart()).isNull();
        assertThat(schedule.getScheduleConfig().dayStart()).isNull();
    }
}
