package com.wfm.solver;

import ai.timefold.solver.test.api.score.stream.ConstraintVerifier;
import com.wfm.model.Agent;
import com.wfm.model.AgentAssignment;
import com.wfm.model.AgentDayConfig;
import com.wfm.model.AgentShiftAssignment;
import com.wfm.model.BreakAlignment;
import com.wfm.model.Schedule;
import com.wfm.model.Specialization;
import com.wfm.model.Timeslot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Covers {@link ScheduleConstraintProvider#agentNotWorkingThatDay} — the constraint that closes
 * the hole live Vinted week 39 fell through: schedule {@code 5c3d15c7} was ACCEPTED at hard 0
 * while 116 agent-days carried seats with no envelope at 14-16 hours, 23 of them with no break.
 *
 * <p>The cause was absence, not a bad value. {@code computeAgentDayConfigs} omits an agent-day
 * that has a day-off row or resolves to zero effective hours; every other agent-day constraint
 * inner-joins {@link AgentDayConfig} or {@link AgentShiftAssignment}, so an omitted agent-day
 * produced no tuples anywhere while the agent stayed in the seat value range. These tests assert
 * the raw match count rather than a score, mirroring the other {@code penalizeConfigurable()}
 * constraint tests in this package.
 */
class NonWorkingDaySeatConstraintTest {

    private final ConstraintVerifier<ScheduleConstraintProvider, Schedule> verifier =
            ConstraintVerifier.build(new ScheduleConstraintProvider(), Schedule.class,
                    AgentAssignment.class, AgentShiftAssignment.class);

    private static final LocalDate DAY = LocalDate.of(2026, 9, 26); // the live Saturday

    @Test
    @DisplayName("a seat on a day the agent IS rostered for is not penalised")
    void seatOnARosteredDay_notPenalised() {
        Agent agent = agent();
        verifier.verifyThat(ScheduleConstraintProvider::agentNotWorkingThatDay)
                .given(seat(agent, DAY, LocalTime.of(8, 0)), dayConfig(agent, DAY, "8.00"))
                .penalizesBy(0);
    }

    @Test
    @DisplayName("a seat on a day with no AgentDayConfig is penalised once per seat")
    void seatOnANonWorkingDay_penalisedPerSeat() {
        Agent agent = agent();
        // No dayConfig for DAY at all — exactly what computeAgentDayConfigs produces for a
        // MANDATORY or PTO day stored as zero hours in agent_day_hours.
        verifier.verifyThat(ScheduleConstraintProvider::agentNotWorkingThatDay)
                .given(seat(agent, DAY, LocalTime.of(8, 0)))
                .penalizesBy(1);
    }

    @Test
    @DisplayName("the live shape: sixteen unrostered seats cost sixteen, not one")
    void sixteenHourNonWorkingDay_penalisedSixteenTimes() {
        Agent agent = agent();
        Object[] seats = new Object[16];
        for (int h = 0; h < 16; h++) {
            seats[h] = seat(agent, DAY, LocalTime.of(8, 0).plusHours(h));
        }
        verifier.verifyThat(ScheduleConstraintProvider::agentNotWorkingThatDay)
                .given(seats)
                .penalizesBy(16);
    }

    @Test
    @DisplayName("a config for a DIFFERENT date does not excuse the seat")
    void configOnAnotherDate_doesNotExcuse() {
        Agent agent = agent();
        verifier.verifyThat(ScheduleConstraintProvider::agentNotWorkingThatDay)
                .given(seat(agent, DAY, LocalTime.of(8, 0)),
                        dayConfig(agent, DAY.minusDays(1), "8.00"))
                .penalizesBy(1);
    }

    @Test
    @DisplayName("a config for a DIFFERENT agent does not excuse the seat")
    void configForAnotherAgent_doesNotExcuse() {
        Agent working = agent();
        Agent notWorking = agent();
        verifier.verifyThat(ScheduleConstraintProvider::agentNotWorkingThatDay)
                .given(seat(notWorking, DAY, LocalTime.of(8, 0)), dayConfig(working, DAY, "8.00"))
                .penalizesBy(1);
    }

    @Test
    @DisplayName("an unassigned seat is not penalised — nobody is working a day off")
    void unassignedSeat_notPenalised() {
        verifier.verifyThat(ScheduleConstraintProvider::agentNotWorkingThatDay)
                .given(seat(null, DAY, LocalTime.of(8, 0)))
                .penalizesBy(0);
    }

    // ------------------------------------------------------------------

    private static Agent agent() {
        Agent a = new Agent();
        a.setId(UUID.randomUUID());
        return a;
    }

    private static AgentAssignment seat(Agent agent, LocalDate date, LocalTime start) {
        Timeslot ts = new Timeslot();
        ts.setId(UUID.randomUUID());
        ts.setDate(date);
        ts.setStartTime(start);
        ts.setEndTime(start.plusHours(1));

        Specialization spec = new Specialization();
        spec.setId(UUID.randomUUID());
        spec.setName("Security and Item Quality");

        AgentAssignment a = new AgentAssignment();
        a.setId(UUID.randomUUID());
        a.setTimeslot(ts);
        a.setRequiredSpecialization(spec);
        a.setAgent(agent);
        return a;
    }

    private static AgentDayConfig dayConfig(Agent agent, LocalDate date, String hours) {
        return new AgentDayConfig(agent.getId(), date, new BigDecimal(hours), 60, 60,
                new BigDecimal("4.00"), new BigDecimal("1.00"), BreakAlignment.ON_HOUR, 130, 70);
    }
}
