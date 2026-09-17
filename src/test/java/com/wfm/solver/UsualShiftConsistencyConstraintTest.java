package com.wfm.solver;

import ai.timefold.solver.test.api.score.stream.ConstraintVerifier;
import com.wfm.model.Agent;
import com.wfm.model.AgentAssignment;
import com.wfm.model.AgentShiftAssignment;
import com.wfm.model.BreakAlignment;
import com.wfm.model.ResolvedUsualShiftTarget;
import com.wfm.model.Schedule;
import com.wfm.model.ScheduleConfig;
import com.wfm.model.SchedulingMode;
import com.wfm.model.ShiftBandPair;
import com.wfm.model.ShiftTemplate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.UUID;

/**
 * Phase 17 plan 17-01, Task 1 (tracer) — {@code usualShiftConsistency} (CONS-01/CONS-02/CONS-04):
 * a per-agent-day TARGET-DEVIATION penalty against the era-resolved usual shift, never the
 * reverted attempt's per-agent SPREAD. Built on {@code BandCapacityConstraintTest}'s harness.
 *
 * <p>Task 2 (TDD) adds the tolerance-band boundary pair (exactly at the band vs. one minute past
 * it) and the null-{@code shiftBandPair} case already covered here is extended with additional
 * assertions there — see that task's commits for the RED/GREEN history.
 */
class UsualShiftConsistencyConstraintTest {

    private final ConstraintVerifier<ScheduleConstraintProvider, Schedule> verifier =
            ConstraintVerifier.build(new ScheduleConstraintProvider(), Schedule.class,
                    AgentAssignment.class, AgentShiftAssignment.class);

    private static final LocalDate MONDAY = LocalDate.of(2026, 9, 7);

    private static Agent agent() {
        Agent a = new Agent();
        a.setId(UUID.randomUUID());
        return a;
    }

    private static ShiftTemplate template(LocalTime startTime, LocalTime endTime) {
        ShiftTemplate t = new ShiftTemplate();
        t.setValidWeekdays(EnumSet.allOf(java.time.DayOfWeek.class));
        t.setId(UUID.randomUUID());
        t.setName("Template-" + UUID.randomUUID());
        t.setStartTime(startTime);
        t.setEndTime(endTime);
        t.setEffectiveFrom(LocalDate.of(2026, 1, 1));
        return t;
    }

    private static AgentShiftAssignment shiftRow(Agent agent, LocalDate date, ShiftBandPair pair) {
        AgentShiftAssignment sa = new AgentShiftAssignment();
        sa.setId(UUID.randomUUID());
        sa.setAgent(agent);
        sa.setDate(date);
        sa.setShiftBandPair(pair);
        return sa;
    }

    private static ScheduleConfig scheduleConfig(SchedulingMode mode, int incrementMinutes,
            int consistencyToleranceMinutes) {
        return new ScheduleConfig(incrementMinutes, LocalTime.of(0, 0), LocalTime.of(23, 59),
                60, new BigDecimal("4.00"), new BigDecimal("1.00"),
                BreakAlignment.ON_HOUR, 20, new BigDecimal("8.00"), 130, 70, mode,
                consistencyToleranceMinutes);
    }

    // ------------------------------------------------------------------
    //  A drifted day is penalised by the excess increment count
    // ------------------------------------------------------------------

    @Test
    @DisplayName("usual start 08:00, envelope start 12:00, 60m band, 30m increment penalises by 6")
    void drifted_penalisedByExpectedIncrementCount() {
        Agent a = agent();
        ShiftTemplate t = template(LocalTime.of(12, 0), LocalTime.of(21, 0));
        ShiftBandPair pair = new ShiftBandPair(t, null);
        ResolvedUsualShiftTarget target = new ResolvedUsualShiftTarget(a.getId(), MONDAY, LocalTime.of(8, 0));

        verifier.verifyThat(ScheduleConstraintProvider::usualShiftConsistency)
                .given(scheduleConfig(SchedulingMode.SHIFT, 30, 60),
                        shiftRow(a, MONDAY, pair), target)
                .penalizesBy(6);
    }

    // ------------------------------------------------------------------
    //  A deviation exactly at the band is a genuine dead zone — zero penalty
    // ------------------------------------------------------------------

    @Test
    @DisplayName("deviation exactly equal to the band penalises by 0")
    void deviationEqualsBand_noPenalty() {
        Agent a = agent();
        // Usual start 08:00, envelope start 09:00 -> exactly 60 minutes deviation, band 60.
        ShiftTemplate t = template(LocalTime.of(9, 0), LocalTime.of(18, 0));
        ShiftBandPair pair = new ShiftBandPair(t, null);
        ResolvedUsualShiftTarget target = new ResolvedUsualShiftTarget(a.getId(), MONDAY, LocalTime.of(8, 0));

        verifier.verifyThat(ScheduleConstraintProvider::usualShiftConsistency)
                .given(scheduleConfig(SchedulingMode.SHIFT, 30, 60),
                        shiftRow(a, MONDAY, pair), target)
                .penalizesBy(0);
    }

    // ------------------------------------------------------------------
    //  SLOT-mode silence
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a SLOT-mode fixture draws no penalty even for a large deviation")
    void slotMode_noPenaltyEvenWhenDrifted() {
        Agent a = agent();
        ShiftTemplate t = template(LocalTime.of(12, 0), LocalTime.of(21, 0));
        ShiftBandPair pair = new ShiftBandPair(t, null);
        ResolvedUsualShiftTarget target = new ResolvedUsualShiftTarget(a.getId(), MONDAY, LocalTime.of(8, 0));

        verifier.verifyThat(ScheduleConstraintProvider::usualShiftConsistency)
                .given(scheduleConfig(SchedulingMode.SLOT, 30, 60),
                        shiftRow(a, MONDAY, pair), target)
                .penalizesBy(0);
    }

    // ------------------------------------------------------------------
    //  Null shiftBandPair (unassigned shift, admitted by forEachIncludingUnassigned)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("an unassigned (null shiftBandPair) shift row draws no penalty")
    void nullShiftBandPair_noPenalty() {
        Agent a = agent();
        ResolvedUsualShiftTarget target = new ResolvedUsualShiftTarget(a.getId(), MONDAY, LocalTime.of(8, 0));

        verifier.verifyThat(ScheduleConstraintProvider::usualShiftConsistency)
                .given(scheduleConfig(SchedulingMode.SHIFT, 30, 60),
                        shiftRow(a, MONDAY, null), target)
                .penalizesBy(0);
    }
}
