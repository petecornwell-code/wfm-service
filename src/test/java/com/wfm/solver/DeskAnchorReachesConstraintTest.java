package com.wfm.solver;

import ai.timefold.solver.test.api.score.stream.ConstraintVerifier;
import com.wfm.dto.SolveRequest;
import com.wfm.model.Agent;
import com.wfm.model.AgentAssignment;
import com.wfm.model.AgentShiftAssignment;
import com.wfm.model.Desk;
import com.wfm.model.Schedule;
import com.wfm.model.SchedulingMode;
import com.wfm.model.ShiftBandPair;
import com.wfm.model.ShiftTemplate;
import com.wfm.model.Timeslot;
import com.wfm.service.SolverServiceBuildScheduleAccess;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The tracer's end-to-end proof (BDAY-04, plan 19-03): a desk's {@code dayStart} reaches {@link
 * ScheduleConstraintProvider#shiftEnvelopeCompliance} through {@code SolverService.buildSchedule ->
 * Schedule.getScheduleConfig() -> cfg.dayStart()}, binds a {@code DayWindow}, and changes whether a
 * seat spanning the calendar midnight is legal.
 *
 * <p>One test walks the whole chain in a single method, not a set of per-layer unit tests, per the
 * plan's own instruction. A desk whose {@code dayStart} is {@code 21:00} and a desk whose {@code
 * dayStart} is {@code 00:00} are each carried through the real {@code buildSchedule} path, and the
 * SAME envelope/slot pair is asserted to produce a DIFFERENT {@code shiftEnvelopeCompliance} verdict
 * for each -- the first proof in this milestone that a desk anchor changes a solver decision.
 *
 * <p>The desk's {@code dayStart} is set directly on the entity, bypassing {@code
 * DeskService.setDayStart}'s gated refusal of anything but {@code 00:00}, exactly as {@code
 * ScheduleConfigAnchorPlumbingTest} and {@code TimeslotGeneratorBusinessDateTest} already do --
 * this test proves the constraint-layer consumption, not the gate itself (D-01, untouched by this
 * phase).
 */
class DeskAnchorReachesConstraintTest {

    private static final long TENANT_ID = 1L;
    private static final LocalDate DAY = LocalDate.of(2026, 10, 5);

    private final ConstraintVerifier<ScheduleConstraintProvider, Schedule> verifier =
            ConstraintVerifier.build(new ScheduleConstraintProvider(), Schedule.class,
                    AgentAssignment.class, AgentShiftAssignment.class);

    private static Agent agent() {
        Agent a = new Agent();
        a.setId(UUID.randomUUID());
        return a;
    }

    /** The envelope {@code 22:00}-{@code 02:00}: crosses the calendar midnight. No break band. */
    private static ShiftTemplate overnightTemplate() {
        ShiftTemplate t = new ShiftTemplate();
        t.setValidWeekdays(java.util.EnumSet.allOf(java.time.DayOfWeek.class));
        t.setId(UUID.randomUUID());
        t.setName("Overnight-" + UUID.randomUUID());
        t.setStartTime(LocalTime.of(22, 0));
        t.setEndTime(LocalTime.of(2, 0));
        t.setEffectiveFrom(LocalDate.of(2026, 1, 1));
        return t;
    }

    /** The timeslot {@code 23:00}-{@code 00:00}: inside the overnight envelope either way you read it. */
    private static Timeslot slot() {
        Timeslot ts = new Timeslot();
        ts.setId(UUID.randomUUID());
        ts.setDate(DAY);
        ts.setStartTime(LocalTime.of(23, 0));
        ts.setEndTime(LocalTime.MIDNIGHT);
        return ts;
    }

    private static AgentAssignment seat(Agent agent, Timeslot ts) {
        AgentAssignment a = new AgentAssignment();
        a.setId(UUID.randomUUID());
        a.setTimeslot(ts);
        a.setAgent(agent);
        return a;
    }

    private static AgentShiftAssignment shiftRow(Agent agent, ShiftBandPair pair) {
        AgentShiftAssignment sa = new AgentShiftAssignment();
        sa.setId(UUID.randomUUID());
        sa.setAgent(agent);
        sa.setDate(DAY);
        sa.setShiftBandPair(pair);
        return sa;
    }

    private static Desk desk(LocalTime dayStart) {
        Desk d = new Desk();
        d.setId(UUID.randomUUID());
        d.setTenantId(TENANT_ID);
        d.setName("Anchor-tracer desk " + dayStart);
        d.setSchedulingMode(SchedulingMode.SHIFT);
        d.setDayStart(dayStart); // bypasses DeskService's gated setter, on purpose (D-01)
        return d;
    }

    private static Schedule scheduleFor(Desk desk) {
        SolveRequest request = new SolveRequest(
                DAY, DAY.plusDays(6),
                LocalTime.of(0, 0), LocalTime.of(0, 0),
                30, null, null, null, null, null, null, null, null, null, null);
        return SolverServiceBuildScheduleAccess.buildSchedule(TENANT_ID, desk.getId(), request, desk);
    }

    @Test
    @DisplayName("BDAY-04 tracer: the same overnight envelope and slot are legal at a 21:00 anchor "
            + "and illegal at a 00:00 anchor, through the real buildSchedule -> ScheduleConfig -> "
            + "shiftEnvelopeCompliance chain")
    void deskAnchorChangesTheConstraintVerdict() {
        ShiftTemplate template = overnightTemplate();
        ShiftBandPair pair = new ShiftBandPair(template, null);
        Agent agent2100 = agent();
        Agent agentMidnight = agent();
        Timeslot ts = slot();

        Schedule schedule2100 = scheduleFor(desk(LocalTime.of(21, 0)));
        Schedule scheduleMidnight = scheduleFor(desk(LocalTime.MIDNIGHT));

        // Sanity: the anchor really did reach ScheduleConfig through the real production path,
        // not a test-only shortcut.
        assertThat(schedule2100.getScheduleConfig().dayStart()).isEqualTo(LocalTime.of(21, 0));
        assertThat(scheduleMidnight.getScheduleConfig().dayStart()).isEqualTo(LocalTime.MIDNIGHT);

        verifier.verifyThat(ScheduleConstraintProvider::shiftEnvelopeCompliance)
                .given(seat(agent2100, ts), shiftRow(agent2100, pair), schedule2100.getScheduleConfig())
                .penalizesBy(0);

        verifier.verifyThat(ScheduleConstraintProvider::shiftEnvelopeCompliance)
                .given(seat(agentMidnight, ts), shiftRow(agentMidnight, pair), scheduleMidnight.getScheduleConfig())
                .penalizesBy(1);
    }
}
