package com.wfm.solver;

import ai.timefold.solver.test.api.score.stream.ConstraintVerifier;
import com.wfm.model.Agent;
import com.wfm.model.AgentAssignment;
import com.wfm.model.Schedule;
import com.wfm.model.Timeslot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Covers {@link ScheduleConstraintProvider#minimumStaffing}, the floor that keeps at least
 * one agent on every timeslot regardless of forecast demand.
 *
 * <p>Context: the bulk under-allocation constraints take their floor from demand
 * ({@code underallocationHardLimitPct} of the slot's demand FTEs), so an hour forecast at
 * zero obliges nothing — 50% of 0 is 0. On the live desk for 2026-01-10 that left 08:00-10:00
 * with no cover at all, and since an 8h shift plus a 1h break spans exactly 9 of the 13h
 * window, all 25 rostered agents packed onto the one 11:00-20:00 placement.
 *
 * <p>Note these tests assert the raw match count, not a score: whether the penalty lands on
 * the hard or soft level is the {@code minStaffingWeight} column's job, not the constraint's.
 */
class MinimumStaffingConstraintTest {

    private final ConstraintVerifier<ScheduleConstraintProvider, Schedule> verifier =
            ConstraintVerifier.build(new ScheduleConstraintProvider(), Schedule.class, AgentAssignment.class);

    private static Timeslot timeslot(LocalTime start) {
        Timeslot ts = new Timeslot();
        ts.setId(UUID.randomUUID());
        ts.setDate(LocalDate.of(2026, 1, 10));
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
    @DisplayName("a timeslot with one agent is not penalised")
    void oneAgentSatisfiesTheFloor() {
        Timeslot ts = timeslot(LocalTime.of(8, 0));
        verifier.verifyThat(ScheduleConstraintProvider::minimumStaffing)
                .given(assignment(ts, agent()))
                .penalizesBy(0);
    }

    @Test
    @DisplayName("a timeslot with several agents is not penalised")
    void moreThanOneAgentSatisfiesTheFloor() {
        Timeslot ts = timeslot(LocalTime.of(11, 0));
        verifier.verifyThat(ScheduleConstraintProvider::minimumStaffing)
                .given(assignment(ts, agent()), assignment(ts, agent()), assignment(ts, agent()))
                .penalizesBy(0);
    }

    @Test
    @DisplayName("an unstaffed timeslot is penalised once — this is the zero-demand hour")
    void unstaffedTimeslotIsPenalised() {
        Timeslot ts = timeslot(LocalTime.of(8, 0));
        verifier.verifyThat(ScheduleConstraintProvider::minimumStaffing)
                .given(assignment(ts, null))
                .penalizesBy(1);
    }

    @Test
    @DisplayName("each unstaffed timeslot is penalised independently")
    void everyUnstaffedTimeslotCounts() {
        // The Saturday 2026-01-10 shape: 08:00, 09:00 and 10:00 forecast at zero and left bare,
        // 11:00 covered. Three bare hours -> three penalties, not one.
        Timeslot eight = timeslot(LocalTime.of(8, 0));
        Timeslot nine = timeslot(LocalTime.of(9, 0));
        Timeslot ten = timeslot(LocalTime.of(10, 0));
        Timeslot eleven = timeslot(LocalTime.of(11, 0));
        verifier.verifyThat(ScheduleConstraintProvider::minimumStaffing)
                .given(assignment(eight, null),
                        assignment(nine, null),
                        assignment(ten, null),
                        assignment(eleven, agent()))
                .penalizesBy(3);
    }

    @Test
    @DisplayName("a slot is judged on its own staffing, not the schedule's total")
    void staffingElsewhereDoesNotCoverABareSlot() {
        Timeslot bare = timeslot(LocalTime.of(9, 0));
        Timeslot busy = timeslot(LocalTime.of(15, 0));
        verifier.verifyThat(ScheduleConstraintProvider::minimumStaffing)
                .given(assignment(bare, null),
                        assignment(busy, agent()),
                        assignment(busy, agent()),
                        assignment(busy, agent()))
                .penalizesBy(1);
    }

    @Test
    @DisplayName("a partly-staffed slot is not penalised — the floor is one, not full demand")
    void mixedSlotWithAtLeastOneAgentPasses() {
        // Under-covering real demand is bulkUnderallocation*'s concern; this constraint only
        // asserts that somebody is on. Two of three entities unassigned still leaves one agent.
        Timeslot ts = timeslot(LocalTime.of(12, 0));
        verifier.verifyThat(ScheduleConstraintProvider::minimumStaffing)
                .given(assignment(ts, agent()), assignment(ts, null), assignment(ts, null))
                .penalizesBy(0);
    }
}
