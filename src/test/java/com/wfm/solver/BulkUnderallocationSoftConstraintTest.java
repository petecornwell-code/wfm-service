package com.wfm.solver;

import ai.timefold.solver.test.api.score.stream.ConstraintVerifier;
import com.wfm.model.Agent;
import com.wfm.model.AgentAssignment;
import com.wfm.model.AgentShiftAssignment;
import com.wfm.model.Schedule;
import com.wfm.model.Timeslot;
import com.wfm.model.TimeslotDemandConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Covers {@link ScheduleConstraintProvider#bulkUnderallocationSoft}, the only constraint
 * that makes covering satisfiable demand preferable to leaving it unstaffed.
 *
 * <p>Regression context: this constraint shipped as {@code penalizeConfigurable(a -> 0)} — a
 * declared no-op. Because the only other under-allocation constraint is a binary hard floor
 * at {@code underallocationHardLimitPct}, the solver saw no difference between staffing an
 * hour and ignoring it once above that floor. A real schedule scored a flawless 0soft while
 * three hours of demand went completely unstaffed and later hours were covered at 234%.
 */
class BulkUnderallocationSoftConstraintTest {

    private final ConstraintVerifier<ScheduleConstraintProvider, Schedule> verifier =
            ConstraintVerifier.build(new ScheduleConstraintProvider(), Schedule.class, AgentAssignment.class, AgentShiftAssignment.class);

    private static Timeslot timeslot(LocalTime start) {
        Timeslot ts = new Timeslot();
        ts.setId(UUID.randomUUID());
        ts.setDate(LocalDate.of(2026, 1, 5));
        ts.setStartTime(start);
        ts.setEndTime(start.plusHours(1));
        return ts;
    }

    private static Agent agent() {
        Agent a = new Agent();
        a.setId(UUID.randomUUID());
        return a;
    }

    /** An assignment for {@code ts}, staffed when {@code agent} is non-null. */
    private static AgentAssignment assignment(Timeslot ts, Agent agent) {
        AgentAssignment a = new AgentAssignment();
        a.setId(UUID.randomUUID());
        a.setTimeslot(ts);
        a.setAgent(agent);
        return a;
    }

    @Test
    @DisplayName("demand fully covered is not penalised")
    void fullyCoveredIsNotPenalised() {
        Timeslot ts = timeslot(LocalTime.of(8, 0));
        verifier.verifyThat(ScheduleConstraintProvider::bulkUnderallocationSoft)
                .given(new TimeslotDemandConfig(ts, 2),
                        assignment(ts, agent()),
                        assignment(ts, agent()))
                .penalizesBy(0);
    }

    @Test
    @DisplayName("each uncovered demand FTE is penalised once")
    void shortfallIsPenalisedProportionally() {
        Timeslot ts = timeslot(LocalTime.of(9, 0));
        // demand 3, one agent assigned -> shortfall of 2
        verifier.verifyThat(ScheduleConstraintProvider::bulkUnderallocationSoft)
                .given(new TimeslotDemandConfig(ts, 3),
                        assignment(ts, agent()),
                        assignment(ts, null),
                        assignment(ts, null))
                .penalizesBy(2);
    }

    @Test
    @DisplayName("a completely unstaffed timeslot is penalised by its full demand")
    void completelyUnstaffedIsPenalisedByFullDemand() {
        // This is the 08:00 hour from the real schedule: demand present, nobody assigned.
        // Under the old no-op this scored 0, which is why the solver ignored the morning.
        Timeslot ts = timeslot(LocalTime.of(8, 0));
        verifier.verifyThat(ScheduleConstraintProvider::bulkUnderallocationSoft)
                .given(new TimeslotDemandConfig(ts, 4),
                        assignment(ts, null),
                        assignment(ts, null))
                .penalizesBy(4);
    }

    @Test
    @DisplayName("over-allocation is not rewarded — excess above demand scores zero here")
    void excessAboveDemandIsNotRewarded() {
        Timeslot ts = timeslot(LocalTime.of(16, 0));
        // demand 1, three agents assigned. Over-allocation is bulkOverallocationLimit's
        // concern; this constraint must stay silent rather than paying for the excess.
        verifier.verifyThat(ScheduleConstraintProvider::bulkUnderallocationSoft)
                .given(new TimeslotDemandConfig(ts, 1),
                        assignment(ts, agent()),
                        assignment(ts, agent()),
                        assignment(ts, agent()))
                .penalizesBy(0);
    }

    @Test
    @DisplayName("shortfalls accumulate across timeslots")
    void shortfallsAccumulateAcrossTimeslots() {
        Timeslot morning = timeslot(LocalTime.of(8, 0));   // demand 1, unstaffed -> 1
        Timeslot midday = timeslot(LocalTime.of(12, 0));   // demand 3, one staffed -> 2
        verifier.verifyThat(ScheduleConstraintProvider::bulkUnderallocationSoft)
                .given(new TimeslotDemandConfig(morning, 1),
                        new TimeslotDemandConfig(midday, 3),
                        assignment(morning, null),
                        assignment(midday, agent()),
                        assignment(midday, null))
                .penalizesBy(3);
    }
}
