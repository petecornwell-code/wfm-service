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
 * Covers {@link ScheduleConstraintProvider#consistentDailyStart}, Stage 1 of the consistency
 * plan.
 *
 * <p>Stage 0 made a stated preference an anchor rather than a floor, but only for agents who
 * have a preference on file. This constraint reaches every agent and leaves the anchor to the
 * solver: it says nothing about which start time an agent should have, only that it should be
 * the same one on every day they work.
 *
 * <p>These tests assert raw match weights, not scores — hard-vs-soft is the
 * {@code consistent_start_weight} column's decision, and it is expected to stay soft.
 */
class ConsistentDailyStartConstraintTest {

    private static final LocalDate MON = LocalDate.of(2026, 1, 5);
    private static final LocalDate TUE = LocalDate.of(2026, 1, 6);
    private static final LocalDate WED = LocalDate.of(2026, 1, 7);
    private static final LocalDate THU = LocalDate.of(2026, 1, 8);

    private final ConstraintVerifier<ScheduleConstraintProvider, Schedule> verifier =
            ConstraintVerifier.build(new ScheduleConstraintProvider(), Schedule.class, AgentAssignment.class);

    /** The single ScheduleConfig problem fact the constraint joins for the increment. */
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

    /** A contiguous run of {@code slots} assignments for {@code agent} on {@code date}. */
    private static List<AgentAssignment> day(Agent agent, LocalDate date, LocalTime from,
                                             int slots, int incrementMinutes) {
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
    @DisplayName("an agent starting at the same time every day is not penalised")
    void identicalStartsAreFree() {
        Agent a = agent();
        List<AgentAssignment> all = new ArrayList<>();
        all.addAll(day(a, MON, LocalTime.of(9, 0), 8, 60));
        all.addAll(day(a, TUE, LocalTime.of(9, 0), 8, 60));
        all.addAll(day(a, WED, LocalTime.of(9, 0), 8, 60));
        verifier.verifyThat(ScheduleConstraintProvider::consistentDailyStart)
                .given(facts(all, 60))
                .penalizesBy(0);
    }

    @Test
    @DisplayName("the anchor is the solver's to pick — any consistent time is free")
    void anyConsistentAnchorIsFree() {
        // Nothing here prefers 09:00 over 13:00. Consistency is the whole of the ask.
        Agent a = agent();
        List<AgentAssignment> all = new ArrayList<>();
        all.addAll(day(a, MON, LocalTime.of(13, 0), 8, 60));
        all.addAll(day(a, TUE, LocalTime.of(13, 0), 8, 60));
        verifier.verifyThat(ScheduleConstraintProvider::consistentDailyStart)
                .given(facts(all, 60))
                .penalizesBy(0);
    }

    @Test
    @DisplayName("a one-increment spread costs one")
    void oneIncrementSpread() {
        Agent a = agent();
        List<AgentAssignment> all = new ArrayList<>();
        all.addAll(day(a, MON, LocalTime.of(9, 0), 8, 60));
        all.addAll(day(a, TUE, LocalTime.of(10, 0), 8, 60));
        verifier.verifyThat(ScheduleConstraintProvider::consistentDailyStart)
                .given(facts(all, 60))
                .penalizesBy(1);
    }

    @Test
    @DisplayName("the penalty is the full spread, not the number of differing days")
    void spreadIsMeasuredEndToEnd() {
        Agent a = agent();
        List<AgentAssignment> all = new ArrayList<>();
        all.addAll(day(a, MON, LocalTime.of(9, 0), 8, 60));
        all.addAll(day(a, TUE, LocalTime.of(12, 0), 8, 60));
        verifier.verifyThat(ScheduleConstraintProvider::consistentDailyStart)
                .given(facts(all, 60))
                .penalizesBy(3);
    }

    @Test
    @DisplayName("only the extremes set the penalty — the documented plateau")
    void middleDaysDoNotChangeThePenalty() {
        // Both weeks span 09:00-12:00 and so cost 3, even though the first is consistent on
        // three of its four days and the second is scattered. Moving a middle day buys nothing
        // until it becomes an extreme. This is a property of spread, recorded here so a future
        // reader does not mistake it for a bug — the per-day-deviation variant is the fix if
        // Stage 1 stalls on it.
        Agent tidy = agent();
        List<AgentAssignment> tidyWeek = new ArrayList<>();
        tidyWeek.addAll(day(tidy, MON, LocalTime.of(9, 0), 8, 60));
        tidyWeek.addAll(day(tidy, TUE, LocalTime.of(9, 0), 8, 60));
        tidyWeek.addAll(day(tidy, WED, LocalTime.of(9, 0), 8, 60));
        tidyWeek.addAll(day(tidy, THU, LocalTime.of(12, 0), 8, 60));

        Agent scattered = agent();
        List<AgentAssignment> scatteredWeek = new ArrayList<>();
        scatteredWeek.addAll(day(scattered, MON, LocalTime.of(9, 0), 8, 60));
        scatteredWeek.addAll(day(scattered, TUE, LocalTime.of(10, 0), 8, 60));
        scatteredWeek.addAll(day(scattered, WED, LocalTime.of(11, 0), 8, 60));
        scatteredWeek.addAll(day(scattered, THU, LocalTime.of(12, 0), 8, 60));

        verifier.verifyThat(ScheduleConstraintProvider::consistentDailyStart)
                .given(facts(tidyWeek, 60))
                .penalizesBy(3);
        verifier.verifyThat(ScheduleConstraintProvider::consistentDailyStart)
                .given(facts(scatteredWeek, 60))
                .penalizesBy(3);
    }

    @Test
    @DisplayName("a day's start is its earliest slot, not its longest block")
    void dayStartIsTheEarliestSlot() {
        // Tuesday is a split shift: 08:00-09:00 then 14:00 onward. Its start is 08:00, so the
        // spread against Monday's 09:00 is one increment.
        Agent a = agent();
        List<AgentAssignment> all = new ArrayList<>();
        all.addAll(day(a, MON, LocalTime.of(9, 0), 8, 60));
        all.addAll(day(a, TUE, LocalTime.of(8, 0), 1, 60));
        all.addAll(day(a, TUE, LocalTime.of(14, 0), 6, 60));
        verifier.verifyThat(ScheduleConstraintProvider::consistentDailyStart)
                .given(facts(all, 60))
                .penalizesBy(1);
    }

    @Test
    @DisplayName("spread is counted in the schedule's own increment")
    void spreadUsesTheConfiguredIncrement() {
        // The same two-hour spread is two increments at 60 minutes and eight at 15.
        Agent hourly = agent();
        List<AgentAssignment> hourlyWeek = new ArrayList<>();
        hourlyWeek.addAll(day(hourly, MON, LocalTime.of(9, 0), 8, 60));
        hourlyWeek.addAll(day(hourly, TUE, LocalTime.of(11, 0), 8, 60));

        Agent quarterly = agent();
        List<AgentAssignment> quarterlyWeek = new ArrayList<>();
        quarterlyWeek.addAll(day(quarterly, MON, LocalTime.of(9, 0), 32, 15));
        quarterlyWeek.addAll(day(quarterly, TUE, LocalTime.of(11, 0), 32, 15));

        verifier.verifyThat(ScheduleConstraintProvider::consistentDailyStart)
                .given(facts(hourlyWeek, 60))
                .penalizesBy(2);
        verifier.verifyThat(ScheduleConstraintProvider::consistentDailyStart)
                .given(facts(quarterlyWeek, 15))
                .penalizesBy(8);
    }

    @Test
    @DisplayName("a spread finer than the increment still costs one")
    void subIncrementSpreadRoundsUp() {
        // Rounding to nearest would make a half-increment drift free, the same hole Stage 0
        // closed in honourPreferredStartTime.
        Agent a = agent();
        List<AgentAssignment> all = new ArrayList<>();
        all.addAll(day(a, MON, LocalTime.of(9, 0), 4, 30));
        all.addAll(day(a, TUE, LocalTime.of(9, 30), 4, 30));
        verifier.verifyThat(ScheduleConstraintProvider::consistentDailyStart)
                .given(facts(all, 60))
                .penalizesBy(1);
    }

    @Test
    @DisplayName("an agent who works a single day is not penalised")
    void singleWorkedDayIsFree() {
        Agent a = agent();
        verifier.verifyThat(ScheduleConstraintProvider::consistentDailyStart)
                .given(facts(day(a, MON, LocalTime.of(14, 0), 8, 60), 60))
                .penalizesBy(0);
    }

    @Test
    @DisplayName("an agent with no assignments forms no group")
    void absentAgentFormsNoGroup() {
        // The absent grouping-key case. An agent who is not scheduled at all has nothing to be
        // consistent about, and no entity exists to carry a penalty.
        verifier.verifyThat(ScheduleConstraintProvider::consistentDailyStart)
                .given(config(60))
                .penalizesBy(0);
    }

    @Test
    @DisplayName("unassigned seats form no group and cannot be penalised")
    void unassignedSeatsAreIgnored() {
        // The present-but-empty case: minimum-staffing seats exist on both days but hold no
        // agent, so there is no per-agent group for them to land in.
        List<AgentAssignment> bare = new ArrayList<>();
        bare.addAll(day(null, MON, LocalTime.of(9, 0), 8, 60));
        bare.addAll(day(null, TUE, LocalTime.of(15, 0), 8, 60));
        verifier.verifyThat(ScheduleConstraintProvider::consistentDailyStart)
                .given(facts(bare, 60))
                .penalizesBy(0);
    }

    @Test
    @DisplayName("each agent is judged on their own week")
    void agentsAreIsolated() {
        Agent steady = agent();
        Agent drifting = agent();
        List<AgentAssignment> all = new ArrayList<>();
        all.addAll(day(steady, MON, LocalTime.of(9, 0), 8, 60));
        all.addAll(day(steady, TUE, LocalTime.of(9, 0), 8, 60));
        all.addAll(day(drifting, MON, LocalTime.of(9, 0), 8, 60));
        all.addAll(day(drifting, TUE, LocalTime.of(13, 0), 8, 60));
        verifier.verifyThat(ScheduleConstraintProvider::consistentDailyStart)
                .given(facts(all, 60))
                .penalizesBy(4);
    }
}
