package com.wfm.solver;

import ai.timefold.solver.test.api.score.stream.ConstraintVerifier;
import com.wfm.model.Agent;
import com.wfm.model.AgentAssignment;
import com.wfm.model.AgentPreference;
import com.wfm.model.Schedule;
import com.wfm.model.Timeslot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Covers {@link ScheduleConstraintProvider#honourPreferredStartTime} after it stopped being
 * one-directional.
 *
 * <p>Context: the constraint used to fire only on {@code slotStart.isBefore(preferred)}, so a
 * preference behaved as a floor — an agent who asked for 09:00 paid nothing for a 14:00
 * placement. Nothing therefore held a start time steady across a week, which is what Stage 0 of
 * the consistency plan needs before solver-chosen anchors are worth adding.
 *
 * <p>These tests assert raw match weights, not scores: whether the penalty is hard or soft is
 * the {@code honour_start_time_weight} column's job.
 */
class HonourPreferredStartTimeConstraintTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 1, 5);
    private static final LocalDate TUESDAY = LocalDate.of(2026, 1, 6);

    private final ConstraintVerifier<ScheduleConstraintProvider, Schedule> verifier =
            ConstraintVerifier.build(new ScheduleConstraintProvider(), Schedule.class, AgentAssignment.class);

    private static Agent agent() {
        Agent a = new Agent();
        a.setId(UUID.randomUUID());
        return a;
    }

    private static Timeslot timeslot(LocalDate date, LocalTime start, int incrementMinutes) {
        Timeslot ts = new Timeslot();
        ts.setId(UUID.randomUUID());
        ts.setDate(date);
        ts.setStartTime(start);
        ts.setEndTime(start.plusMinutes(incrementMinutes));
        return ts;
    }

    private static AgentAssignment assignment(Timeslot ts, Agent agent) {
        AgentAssignment a = new AgentAssignment();
        a.setId(UUID.randomUUID());
        a.setTimeslot(ts);
        a.setAgent(agent);
        return a;
    }

    /** A contiguous run of {@code slots} assignments for {@code agent}, starting at {@code from}. */
    private static List<AgentAssignment> shift(Agent agent, LocalDate date, LocalTime from,
                                               int slots, int incrementMinutes) {
        List<AgentAssignment> assignments = new ArrayList<>();
        LocalTime t = from;
        for (int i = 0; i < slots; i++) {
            assignments.add(assignment(timeslot(date, t, incrementMinutes), agent));
            t = t.plusMinutes(incrementMinutes);
        }
        return assignments;
    }

    private static AgentPreference preference(Agent agent, LocalDate date, LocalTime preferredStart) {
        AgentPreference p = new AgentPreference();
        p.setId(UUID.randomUUID());
        p.setAgent(agent);
        p.setDate(date);
        p.setDayOfWeek(date.getDayOfWeek());
        p.setPreferredStartTime(preferredStart);
        return p;
    }

    private static Object[] facts(List<AgentAssignment> assignments, Object... extras) {
        List<Object> all = new ArrayList<>(assignments);
        all.addAll(List.of(extras));
        return all.toArray();
    }

    @Test
    @DisplayName("a shift starting exactly at the preferred time is not penalised")
    void exactStartIsFree() {
        Agent a = agent();
        List<AgentAssignment> assignments = shift(a, MONDAY, LocalTime.of(9, 0), 8, 60);
        verifier.verifyThat(ScheduleConstraintProvider::honourPreferredStartTime)
                .given(facts(assignments, preference(a, MONDAY, LocalTime.of(9, 0))))
                .penalizesBy(0);
    }

    @Test
    @DisplayName("starting early is penalised per increment — unchanged from the isBefore form")
    void startingEarlyCostsOnePerIncrement() {
        // The old constraint counted assigned slots before the preference: 07:00 and 08:00 -> 2.
        // Measuring deviation on the shift start reproduces that magnitude exactly.
        Agent a = agent();
        List<AgentAssignment> assignments = shift(a, MONDAY, LocalTime.of(7, 0), 8, 60);
        verifier.verifyThat(ScheduleConstraintProvider::honourPreferredStartTime)
                .given(facts(assignments, preference(a, MONDAY, LocalTime.of(9, 0))))
                .penalizesBy(2);
    }

    @Test
    @DisplayName("starting late is penalised symmetrically — this was free before Stage 0")
    void startingLateNowCosts() {
        Agent a = agent();
        List<AgentAssignment> assignments = shift(a, MONDAY, LocalTime.of(11, 0), 8, 60);
        verifier.verifyThat(ScheduleConstraintProvider::honourPreferredStartTime)
                .given(facts(assignments, preference(a, MONDAY, LocalTime.of(9, 0))))
                .penalizesBy(2);
    }

    @Test
    @DisplayName("equal deviation costs the same either side of the preference")
    void deviationIsSymmetric() {
        Agent early = agent();
        Agent late = agent();
        List<AgentAssignment> earlyShift = shift(early, MONDAY, LocalTime.of(6, 0), 8, 60);
        List<AgentAssignment> lateShift = shift(late, MONDAY, LocalTime.of(12, 0), 8, 60);

        verifier.verifyThat(ScheduleConstraintProvider::honourPreferredStartTime)
                .given(facts(earlyShift, preference(early, MONDAY, LocalTime.of(9, 0))))
                .penalizesBy(3);
        verifier.verifyThat(ScheduleConstraintProvider::honourPreferredStartTime)
                .given(facts(lateShift, preference(late, MONDAY, LocalTime.of(9, 0))))
                .penalizesBy(3);
    }

    @Test
    @DisplayName("deviation is counted in the schedule's own increment, not in hours")
    void deviationUsesTheTimeslotIncrement() {
        // Same two-hour miss as startingLateNowCosts, on 30-minute slots -> four increments.
        Agent a = agent();
        List<AgentAssignment> assignments = shift(a, MONDAY, LocalTime.of(11, 0), 16, 30);
        verifier.verifyThat(ScheduleConstraintProvider::honourPreferredStartTime)
                .given(facts(assignments, preference(a, MONDAY, LocalTime.of(9, 0))))
                .penalizesBy(4);
    }

    @Test
    @DisplayName("a sub-increment miss rounds up rather than costing nothing")
    void subIncrementDeviationRoundsUp() {
        // 09:30 cannot be hit by hourly slots. Rounding to nearest would make 09:00 free and
        // reintroduce a band of cost-free deviation; rounding up charges 1 on both sides, so
        // the floor is constant and does not bias the placement.
        Agent early = agent();
        Agent late = agent();
        verifier.verifyThat(ScheduleConstraintProvider::honourPreferredStartTime)
                .given(facts(shift(early, MONDAY, LocalTime.of(9, 0), 8, 60),
                        preference(early, MONDAY, LocalTime.of(9, 30))))
                .penalizesBy(1);
        verifier.verifyThat(ScheduleConstraintProvider::honourPreferredStartTime)
                .given(facts(shift(late, MONDAY, LocalTime.of(10, 0), 8, 60),
                        preference(late, MONDAY, LocalTime.of(9, 30))))
                .penalizesBy(1);
    }

    @Test
    @DisplayName("a preference with no start time is ignored")
    void nullPreferredStartIsIgnored() {
        Agent a = agent();
        List<AgentAssignment> assignments = shift(a, MONDAY, LocalTime.of(14, 0), 8, 60);
        verifier.verifyThat(ScheduleConstraintProvider::honourPreferredStartTime)
                .given(facts(assignments, preference(a, MONDAY, null)))
                .penalizesBy(0);
    }

    @Test
    @DisplayName("a preference for a date the agent does not work is not penalised")
    void absentAgentDayFormsNoGroup() {
        // The absent grouping-key case. A day with no assignment emits no group, so the
        // preference cannot fire. That is intentional — not working is not a start-time
        // deviation, and whether the day should have been worked is contractedHours*'s job.
        Agent a = agent();
        List<AgentAssignment> mondayShift = shift(a, MONDAY, LocalTime.of(9, 0), 8, 60);
        verifier.verifyThat(ScheduleConstraintProvider::honourPreferredStartTime)
                .given(facts(mondayShift,
                        preference(a, MONDAY, LocalTime.of(9, 0)),
                        preference(a, TUESDAY, LocalTime.of(9, 0))))
                .penalizesBy(0);
    }

    @Test
    @DisplayName("unassigned seats form no group and cannot be penalised")
    void unassignedSeatsAreIgnored() {
        // The present-but-empty grouping-key case: seats exist for the day but hold no agent.
        Agent a = agent();
        List<AgentAssignment> bare = shift(null, MONDAY, LocalTime.of(14, 0), 8, 60);
        verifier.verifyThat(ScheduleConstraintProvider::honourPreferredStartTime)
                .given(facts(bare, preference(a, MONDAY, LocalTime.of(9, 0))))
                .penalizesBy(0);
    }

    @Test
    @DisplayName("each agent-day is judged on its own start")
    void eachAgentDayIsJudgedSeparately() {
        // Monday on the anchor, Tuesday three hours late. Only Tuesday should cost.
        Agent a = agent();
        List<AgentAssignment> assignments = new ArrayList<>();
        assignments.addAll(shift(a, MONDAY, LocalTime.of(9, 0), 8, 60));
        assignments.addAll(shift(a, TUESDAY, LocalTime.of(12, 0), 8, 60));
        verifier.verifyThat(ScheduleConstraintProvider::honourPreferredStartTime)
                .given(facts(assignments,
                        preference(a, MONDAY, LocalTime.of(9, 0)),
                        preference(a, TUESDAY, LocalTime.of(9, 0))))
                .penalizesBy(3);
    }

    @Test
    @DisplayName("one agent's deviation is not charged to another")
    void agentsAreIsolated() {
        Agent onTime = agent();
        Agent late = agent();
        List<AgentAssignment> assignments = new ArrayList<>();
        assignments.addAll(shift(onTime, MONDAY, LocalTime.of(9, 0), 8, 60));
        assignments.addAll(shift(late, MONDAY, LocalTime.of(13, 0), 8, 60));
        verifier.verifyThat(ScheduleConstraintProvider::honourPreferredStartTime)
                .given(facts(assignments,
                        preference(onTime, MONDAY, LocalTime.of(9, 0)),
                        preference(late, MONDAY, LocalTime.of(9, 0))))
                .penalizesBy(4);
    }

    @Test
    @DisplayName("deviation is measured on the earliest slot, not on each slot")
    void gapsDoNotInflateTheDeviation() {
        // A split shift starting at 07:00 with a hole in the middle still deviates by two
        // increments — the old per-slot form would have counted every pre-preference slot.
        Agent a = agent();
        List<AgentAssignment> assignments = new ArrayList<>();
        assignments.addAll(shift(a, MONDAY, LocalTime.of(7, 0), 3, 60));
        assignments.addAll(shift(a, MONDAY, LocalTime.of(12, 0), 4, 60));
        verifier.verifyThat(ScheduleConstraintProvider::honourPreferredStartTime)
                .given(facts(assignments, preference(a, MONDAY, LocalTime.of(9, 0))))
                .penalizesBy(2);
    }
}
