package com.wfm.solver;

import ai.timefold.solver.test.api.score.stream.ConstraintVerifier;
import com.wfm.model.Agent;
import com.wfm.model.AgentAssignment;
import com.wfm.model.BreakAlignment;
import com.wfm.model.Schedule;
import com.wfm.model.ScheduleConfig;
import com.wfm.model.Timeslot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Covers {@link ScheduleConstraintProvider#consistentBreakOffset}, Stage 2 of the consistency
 * plan.
 *
 * <p>The break is measured as a distance into the shift rather than as a time of day, which is
 * what {@code honourPreferredBreakTime} cannot do — its {@code preferredBreakTime} is an
 * absolute {@code LocalTime}. It also keeps the constraint independent of
 * {@code consistentDailyStart}: measured absolutely, an agent with scattered starts would be
 * charged twice for one fault.
 */
class ConsistentBreakOffsetConstraintTest {

    private static final LocalDate MON = LocalDate.of(2026, 1, 5);
    private static final LocalDate TUE = LocalDate.of(2026, 1, 6);
    private static final LocalDate WED = LocalDate.of(2026, 1, 7);

    private final ConstraintVerifier<ScheduleConstraintProvider, Schedule> verifier =
            ConstraintVerifier.build(new ScheduleConstraintProvider(), Schedule.class, AgentAssignment.class);

    private static ScheduleConfig config(int incrementMinutes) {
        return new ScheduleConfig(
                incrementMinutes,
                LocalTime.of(8, 0), LocalTime.of(21, 0),
                60, BigDecimal.valueOf(6), BigDecimal.valueOf(4),
                BreakAlignment.ON_HOUR, 30,
                BigDecimal.valueOf(8), 130, 70);
    }

    private static Agent agent() {
        Agent a = new Agent();
        a.setId(UUID.randomUUID());
        return a;
    }

    private static AgentAssignment assignment(Agent agent, LocalDate date, LocalTime start,
                                              int incrementMinutes) {
        Timeslot ts = new Timeslot();
        ts.setId(UUID.randomUUID());
        ts.setDate(date);
        ts.setStartTime(start);
        ts.setEndTime(start.plusMinutes(incrementMinutes));

        AgentAssignment a = new AgentAssignment();
        a.setId(UUID.randomUUID());
        a.setTimeslot(ts);
        a.setAgent(agent);
        return a;
    }

    /**
     * A shift for {@code agent} beginning at {@code from} with {@code beforeBreak} worked slots,
     * then a one-slot gap (the break), then {@code afterBreak} more worked slots.
     */
    private static List<AgentAssignment> shiftWithBreak(Agent agent, LocalDate date, LocalTime from,
                                                        int beforeBreak, int afterBreak,
                                                        int incrementMinutes) {
        List<AgentAssignment> assignments = new ArrayList<>();
        LocalTime t = from;
        for (int i = 0; i < beforeBreak; i++) {
            assignments.add(assignment(agent, date, t, incrementMinutes));
            t = t.plusMinutes(incrementMinutes);
        }
        t = t.plusMinutes(incrementMinutes); // the break
        for (int i = 0; i < afterBreak; i++) {
            assignments.add(assignment(agent, date, t, incrementMinutes));
            t = t.plusMinutes(incrementMinutes);
        }
        return assignments;
    }

    /** An unbroken run — a day with no break to place. */
    private static List<AgentAssignment> shiftWithoutBreak(Agent agent, LocalDate date,
                                                           LocalTime from, int slots,
                                                           int incrementMinutes) {
        List<AgentAssignment> assignments = new ArrayList<>();
        LocalTime t = from;
        for (int i = 0; i < slots; i++) {
            assignments.add(assignment(agent, date, t, incrementMinutes));
            t = t.plusMinutes(incrementMinutes);
        }
        return assignments;
    }

    private static Object[] facts(List<AgentAssignment> assignments, int incrementMinutes) {
        List<Object> all = new ArrayList<>(assignments);
        all.add(config(incrementMinutes));
        return all.toArray();
    }

    @Test
    @DisplayName("the same break offset every day is not penalised")
    void identicalOffsetsAreFree() {
        Agent a = agent();
        List<AgentAssignment> all = new ArrayList<>();
        all.addAll(shiftWithBreak(a, MON, LocalTime.of(9, 0), 4, 4, 60));
        all.addAll(shiftWithBreak(a, TUE, LocalTime.of(9, 0), 4, 4, 60));
        verifier.verifyThat(ScheduleConstraintProvider::consistentBreakOffset)
                .given(facts(all, 60))
                .penalizesBy(0);
    }

    @Test
    @DisplayName("a shifted start with the same offset is free — this is why it is an offset")
    void sameOffsetUnderADifferentStartIsFree() {
        // 09:00 start breaking at 13:00, and 10:00 start breaking at 14:00. Both break four
        // hours in. Measured as a time of day this would cost; measured as an offset it does
        // not, and the start inconsistency stays consistentDailyStart's to charge.
        Agent a = agent();
        List<AgentAssignment> all = new ArrayList<>();
        all.addAll(shiftWithBreak(a, MON, LocalTime.of(9, 0), 4, 4, 60));
        all.addAll(shiftWithBreak(a, TUE, LocalTime.of(10, 0), 4, 4, 60));
        verifier.verifyThat(ScheduleConstraintProvider::consistentBreakOffset)
                .given(facts(all, 60))
                .penalizesBy(0);
    }

    @Test
    @DisplayName("a one-increment difference in offset costs one")
    void oneIncrementOffsetSpread() {
        Agent a = agent();
        List<AgentAssignment> all = new ArrayList<>();
        all.addAll(shiftWithBreak(a, MON, LocalTime.of(9, 0), 4, 4, 60));
        all.addAll(shiftWithBreak(a, TUE, LocalTime.of(9, 0), 5, 3, 60));
        verifier.verifyThat(ScheduleConstraintProvider::consistentBreakOffset)
                .given(facts(all, 60))
                .penalizesBy(1);
    }

    @Test
    @DisplayName("the penalty is the full spread of offsets")
    void spreadIsMeasuredEndToEnd() {
        // Breaks at +2h, +4h and +5h. The spread is three increments, set by the extremes.
        Agent a = agent();
        List<AgentAssignment> all = new ArrayList<>();
        all.addAll(shiftWithBreak(a, MON, LocalTime.of(9, 0), 2, 6, 60));
        all.addAll(shiftWithBreak(a, TUE, LocalTime.of(9, 0), 4, 4, 60));
        all.addAll(shiftWithBreak(a, WED, LocalTime.of(9, 0), 5, 3, 60));
        verifier.verifyThat(ScheduleConstraintProvider::consistentBreakOffset)
                .given(facts(all, 60))
                .penalizesBy(3);
    }

    @Test
    @DisplayName("offset spread is counted in the schedule's own increment")
    void spreadUsesTheConfiguredIncrement() {
        // A two-hour difference in offset: two increments at 60 minutes, eight at 15.
        Agent hourly = agent();
        List<AgentAssignment> hourlyWeek = new ArrayList<>();
        hourlyWeek.addAll(shiftWithBreak(hourly, MON, LocalTime.of(9, 0), 2, 6, 60));
        hourlyWeek.addAll(shiftWithBreak(hourly, TUE, LocalTime.of(9, 0), 4, 4, 60));

        Agent quarterly = agent();
        List<AgentAssignment> quarterlyWeek = new ArrayList<>();
        quarterlyWeek.addAll(shiftWithBreak(quarterly, MON, LocalTime.of(9, 0), 8, 24, 15));
        quarterlyWeek.addAll(shiftWithBreak(quarterly, TUE, LocalTime.of(9, 0), 16, 16, 15));

        verifier.verifyThat(ScheduleConstraintProvider::consistentBreakOffset)
                .given(facts(hourlyWeek, 60))
                .penalizesBy(2);
        verifier.verifyThat(ScheduleConstraintProvider::consistentBreakOffset)
                .given(facts(quarterlyWeek, 15))
                .penalizesBy(8);
    }

    @Test
    @DisplayName("a day with no break contributes nothing")
    void breaklessDayIsSkipped() {
        // Tuesday is an unbroken run — a short shift not obliged to carry a break. It has no
        // offset to compare, so the agent is judged on the days that do, leaving one day and
        // therefore no spread. Whether Tuesday should have had a break is exactlyOneBreak's job.
        Agent a = agent();
        List<AgentAssignment> all = new ArrayList<>();
        all.addAll(shiftWithBreak(a, MON, LocalTime.of(9, 0), 4, 4, 60));
        all.addAll(shiftWithoutBreak(a, TUE, LocalTime.of(15, 0), 3, 60));
        verifier.verifyThat(ScheduleConstraintProvider::consistentBreakOffset)
                .given(facts(all, 60))
                .penalizesBy(0);
    }

    @Test
    @DisplayName("breakless days do not mask a spread among the days that do have breaks")
    void breaklessDayDoesNotHideASpread() {
        Agent a = agent();
        List<AgentAssignment> all = new ArrayList<>();
        all.addAll(shiftWithBreak(a, MON, LocalTime.of(9, 0), 2, 6, 60));
        all.addAll(shiftWithoutBreak(a, TUE, LocalTime.of(15, 0), 3, 60));
        all.addAll(shiftWithBreak(a, WED, LocalTime.of(9, 0), 5, 3, 60));
        verifier.verifyThat(ScheduleConstraintProvider::consistentBreakOffset)
                .given(facts(all, 60))
                .penalizesBy(3);
    }

    @Test
    @DisplayName("an agent with one break-carrying day is not penalised")
    void singleBreakDayIsFree() {
        Agent a = agent();
        verifier.verifyThat(ScheduleConstraintProvider::consistentBreakOffset)
                .given(facts(shiftWithBreak(a, MON, LocalTime.of(9, 0), 4, 4, 60), 60))
                .penalizesBy(0);
    }

    @Test
    @DisplayName("an agent with no assignments forms no group")
    void absentAgentFormsNoGroup() {
        verifier.verifyThat(ScheduleConstraintProvider::consistentBreakOffset)
                .given(config(60))
                .penalizesBy(0);
    }

    @Test
    @DisplayName("unassigned seats form no group and cannot be penalised")
    void unassignedSeatsAreIgnored() {
        List<AgentAssignment> bare = new ArrayList<>();
        bare.addAll(shiftWithBreak(null, MON, LocalTime.of(9, 0), 2, 6, 60));
        bare.addAll(shiftWithBreak(null, TUE, LocalTime.of(9, 0), 5, 3, 60));
        verifier.verifyThat(ScheduleConstraintProvider::consistentBreakOffset)
                .given(facts(bare, 60))
                .penalizesBy(0);
    }

    @Test
    @DisplayName("each agent is judged on their own week")
    void agentsAreIsolated() {
        Agent steady = agent();
        Agent drifting = agent();
        List<AgentAssignment> all = new ArrayList<>();
        all.addAll(shiftWithBreak(steady, MON, LocalTime.of(9, 0), 4, 4, 60));
        all.addAll(shiftWithBreak(steady, TUE, LocalTime.of(9, 0), 4, 4, 60));
        all.addAll(shiftWithBreak(drifting, MON, LocalTime.of(9, 0), 2, 6, 60));
        all.addAll(shiftWithBreak(drifting, TUE, LocalTime.of(9, 0), 5, 3, 60));
        verifier.verifyThat(ScheduleConstraintProvider::consistentBreakOffset)
                .given(facts(all, 60))
                .penalizesBy(3);
    }
}
