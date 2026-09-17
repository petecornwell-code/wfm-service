package com.wfm.solver;

import ai.timefold.solver.test.api.score.stream.ConstraintVerifier;
import com.wfm.model.Agent;
import com.wfm.model.AgentAssignment;
import com.wfm.model.AgentPreference;
import com.wfm.model.AgentShiftAssignment;
import com.wfm.model.BreakAlignment;
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
 * Phase 17 plan 17-02, Task 1 — {@code preferredStartShiftMode} (CONS-05/CONS-06/D-08/D-09): a
 * NEW shift-granularity preference constraint, anchor-style in both directions (never
 * lateness-only, unlike {@code honourPreferredStartTime}'s {@code isBefore}-only per-slot form),
 * firing whether or not a usual shift is stored for the agent-day (D-09), mode-gated to SHIFT
 * desks. Built on {@code BandCapacityConstraintTest}'s harness, mirroring
 * {@code UsualShiftConsistencyConstraintTest}'s shape.
 */
class PreferredStartShiftModeConstraintTest {

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

    private static AgentPreference preference(Agent agent, LocalDate date, LocalTime preferredStartTime) {
        AgentPreference p = new AgentPreference();
        p.setId(UUID.randomUUID());
        p.setAgent(agent);
        p.setDate(date);
        p.setStanding(false);
        p.setPreferredStartTime(preferredStartTime);
        return p;
    }

    private static ScheduleConfig scheduleConfig(SchedulingMode mode, int incrementMinutes) {
        return new ScheduleConfig(incrementMinutes, LocalTime.of(0, 0), LocalTime.of(23, 59),
                60, new BigDecimal("4.00"), new BigDecimal("1.00"),
                BreakAlignment.ON_HOUR, 20, new BigDecimal("8.00"), 130, 70, mode);
    }

    // ------------------------------------------------------------------
    //  Exact match -- zero deviation, zero penalty
    // ------------------------------------------------------------------

    @Test
    @DisplayName("assigned envelope start equals preferred start -> penalty 0")
    void assignedMatchesPreferred_noPenalty() {
        Agent a = agent();
        ShiftTemplate t = template(LocalTime.of(9, 0), LocalTime.of(18, 0));
        ShiftBandPair pair = new ShiftBandPair(t, null);
        AgentPreference pref = preference(a, MONDAY, LocalTime.of(9, 0));

        verifier.verifyThat(ScheduleConstraintProvider::preferredStartShiftMode)
                .given(scheduleConfig(SchedulingMode.SHIFT, 30),
                        shiftRow(a, MONDAY, pair), pref)
                .penalizesBy(0);
    }

    // ------------------------------------------------------------------
    //  Later than preferred -- penalised
    // ------------------------------------------------------------------

    @Test
    @DisplayName("assigned envelope start 60 minutes after preferred, 30m increment -> penalty 2")
    void assignedLaterThanPreferred_penalisedByTwo() {
        Agent a = agent();
        ShiftTemplate t = template(LocalTime.of(10, 0), LocalTime.of(19, 0));
        ShiftBandPair pair = new ShiftBandPair(t, null);
        AgentPreference pref = preference(a, MONDAY, LocalTime.of(9, 0));

        verifier.verifyThat(ScheduleConstraintProvider::preferredStartShiftMode)
                .given(scheduleConfig(SchedulingMode.SHIFT, 30),
                        shiftRow(a, MONDAY, pair), pref)
                .penalizesBy(2);
    }

    // ------------------------------------------------------------------
    //  Earlier than preferred -- penalised the SAME as later (anchor, not floor)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("assigned envelope start 60 minutes before preferred, 30m increment -> penalty 2 (anchor, not floor)")
    void assignedEarlierThanPreferred_penalisedTheSameAsLater() {
        Agent a = agent();
        ShiftTemplate t = template(LocalTime.of(8, 0), LocalTime.of(17, 0));
        ShiftBandPair pair = new ShiftBandPair(t, null);
        AgentPreference pref = preference(a, MONDAY, LocalTime.of(9, 0));

        verifier.verifyThat(ScheduleConstraintProvider::preferredStartShiftMode)
                .given(scheduleConfig(SchedulingMode.SHIFT, 30),
                        shiftRow(a, MONDAY, pair), pref)
                .penalizesBy(2);
    }

    // ------------------------------------------------------------------
    //  Sub-increment deviation is not free (CEILING)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a 10-minute deviation on a 30-minute increment still costs one increment (CEILING)")
    void subIncrementDeviation_costsOneIncrement() {
        Agent a = agent();
        ShiftTemplate t = template(LocalTime.of(9, 10), LocalTime.of(18, 10));
        ShiftBandPair pair = new ShiftBandPair(t, null);
        AgentPreference pref = preference(a, MONDAY, LocalTime.of(9, 0));

        verifier.verifyThat(ScheduleConstraintProvider::preferredStartShiftMode)
                .given(scheduleConfig(SchedulingMode.SHIFT, 30),
                        shiftRow(a, MONDAY, pair), pref)
                .penalizesBy(1);
    }

    // ------------------------------------------------------------------
    //  D-09: fires whether or not a usual shift is stored -- no ResolvedUsualShiftTarget fact
    //  exists at all in this fixture, and the penalty still applies.
    // ------------------------------------------------------------------

    @Test
    @DisplayName("D-09: a recorded preference is honoured with no ResolvedUsualShiftTarget fact present at all")
    void noStoredUsualShift_preferenceStillHonoured() {
        Agent a = agent();
        ShiftTemplate t = template(LocalTime.of(10, 0), LocalTime.of(19, 0));
        ShiftBandPair pair = new ShiftBandPair(t, null);
        AgentPreference pref = preference(a, MONDAY, LocalTime.of(9, 0));

        verifier.verifyThat(ScheduleConstraintProvider::preferredStartShiftMode)
                .given(scheduleConfig(SchedulingMode.SHIFT, 30),
                        shiftRow(a, MONDAY, pair), pref)
                .penalizesBy(2);
    }

    // ------------------------------------------------------------------
    //  No AgentPreference for that date -- no tuple, no penalty
    // ------------------------------------------------------------------

    @Test
    @DisplayName("no AgentPreference for that date -> no tuple, penalty 0")
    void noPreferenceForDate_noPenalty() {
        Agent a = agent();
        ShiftTemplate t = template(LocalTime.of(10, 0), LocalTime.of(19, 0));
        ShiftBandPair pair = new ShiftBandPair(t, null);

        verifier.verifyThat(ScheduleConstraintProvider::preferredStartShiftMode)
                .given(scheduleConfig(SchedulingMode.SHIFT, 30),
                        shiftRow(a, MONDAY, pair))
                .penalizesBy(0);
    }

    // ------------------------------------------------------------------
    //  AgentPreference present but preferredStartTime null -- no penalty
    // ------------------------------------------------------------------

    @Test
    @DisplayName("AgentPreference present but preferredStartTime null -> penalty 0")
    void preferenceWithNullStartTime_noPenalty() {
        Agent a = agent();
        ShiftTemplate t = template(LocalTime.of(10, 0), LocalTime.of(19, 0));
        ShiftBandPair pair = new ShiftBandPair(t, null);
        AgentPreference pref = preference(a, MONDAY, null);

        verifier.verifyThat(ScheduleConstraintProvider::preferredStartShiftMode)
                .given(scheduleConfig(SchedulingMode.SHIFT, 30),
                        shiftRow(a, MONDAY, pair), pref)
                .penalizesBy(0);
    }

    // ------------------------------------------------------------------
    //  SLOT-mode silence
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a SLOT-mode fixture draws no penalty even for a large deviation")
    void slotMode_noPenaltyEvenWhenDeviating() {
        Agent a = agent();
        ShiftTemplate t = template(LocalTime.of(12, 0), LocalTime.of(21, 0));
        ShiftBandPair pair = new ShiftBandPair(t, null);
        AgentPreference pref = preference(a, MONDAY, LocalTime.of(9, 0));

        verifier.verifyThat(ScheduleConstraintProvider::preferredStartShiftMode)
                .given(scheduleConfig(SchedulingMode.SLOT, 30),
                        shiftRow(a, MONDAY, pair), pref)
                .penalizesBy(0);
    }

    // ------------------------------------------------------------------
    //  Null shiftBandPair (unassigned shift) -- no penalty, no exception
    // ------------------------------------------------------------------

    @Test
    @DisplayName("an unassigned (null shiftBandPair) shift row draws no penalty")
    void nullShiftBandPair_noPenalty() {
        Agent a = agent();
        AgentPreference pref = preference(a, MONDAY, LocalTime.of(9, 0));

        verifier.verifyThat(ScheduleConstraintProvider::preferredStartShiftMode)
                .given(scheduleConfig(SchedulingMode.SHIFT, 30),
                        shiftRow(a, MONDAY, null), pref)
                .penalizesBy(0);
    }
}
