package com.wfm.solver;

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import ai.timefold.solver.core.config.localsearch.LocalSearchPhaseConfig;
import ai.timefold.solver.core.config.score.director.ScoreDirectorFactoryConfig;
import ai.timefold.solver.core.config.solver.EnvironmentMode;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;
import com.wfm.model.*;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Diagnostic test that verifies incremental scoring correctness.
 *
 * 1. Scores the all-unassigned initial state
 * 2. Manually assigns ONE agent to ONE seat
 * 3. Re-scores and explains the delta
 * 4. Runs the solver with FULL_ASSERT to validate incremental scoring
 */
class IncrementalScoringDiagnosticTest {

    private static final long TENANT = 1L;
    private static final LocalDate DAY = LocalDate.of(2026, 3, 10);
    private static final LocalTime START = LocalTime.of(8, 0);
    private static final LocalTime END = LocalTime.of(20, 0);
    private static final int INCREMENT = 60;
    private static final int BREAK_DURATION = 60;
    private static final BigDecimal BREAK_BLOCKED = new BigDecimal("1.00");
    private static final BigDecimal BREAK_MIN_SHIFT = new BigDecimal("4.00");
    private static final BreakAlignment BREAK_ALIGNMENT = BreakAlignment.ON_HOUR;
    private static final BigDecimal CONTRACTED_HOURS = new BigDecimal("8.00");

    @Test
    void diagnoseScoreDelta_oneAssignment() {
        Schedule schedule = buildUnassignedSchedule();

        // Score the initial state
        SolverFactory<Schedule> scoringFactory = SolverFactory.create(
                new SolverConfig()
                        .withSolutionClass(Schedule.class)
                        .withEntityClasses(AgentAssignment.class)
                        .withScoreDirectorFactory(new ScoreDirectorFactoryConfig()
                                .withConstraintProviderClass(ScheduleConstraintProvider.class)));

        var sm = ai.timefold.solver.core.api.solver.SolutionManager
                .<Schedule, HardSoftScore>create(scoringFactory);

        HardSoftScore initialScore = sm.update(schedule);
        System.out.println("=== INITIAL STATE ===");
        System.out.println("Score: " + initialScore);

        var initialExplanation = sm.explain(schedule);
        System.out.println("Constraint breakdown:");
        initialExplanation.getConstraintMatchTotalMap().forEach((name, total) -> {
            if (!total.getScore().equals(HardSoftScore.ZERO)) {
                System.out.printf("  %-45s => %s (count: %d)%n",
                        name, total.getScore(), total.getConstraintMatchCount());
            }
        });

        // Verify initial score matches expected
        assertThat(initialScore.hardScore()).isEqualTo(-76504);
        assertThat(initialScore.softScore()).isEqualTo(-720000);

        // Now assign ONE agent to ONE seat
        DeskAgent firstAgent = schedule.getDeskAgents().get(0);
        AgentAssignment firstAssignment = schedule.getAssignments().get(0);
        System.out.println("\n=== ASSIGNING agent " + firstAgent.getAgent().getName()
                + " to timeslot " + firstAssignment.getTimeslot().getStartTime()
                + "-" + firstAssignment.getTimeslot().getEndTime() + " ===");

        firstAssignment.setDeskAgent(firstAgent);

        HardSoftScore afterScore = sm.update(schedule);
        System.out.println("\nScore after 1 assignment: " + afterScore);
        System.out.println("Delta: hard=" + (afterScore.hardScore() - initialScore.hardScore())
                + ", soft=" + (afterScore.softScore() - initialScore.softScore()));

        var afterExplanation = sm.explain(schedule);
        System.out.println("\nConstraint breakdown after assignment:");
        afterExplanation.getConstraintMatchTotalMap().forEach((name, total) -> {
            if (!total.getScore().equals(HardSoftScore.ZERO)) {
                System.out.printf("  %-45s => %s (count: %d)%n",
                        name, total.getScore(), total.getConstraintMatchCount());
            }
        });

        // Print the delta per constraint
        System.out.println("\n=== DELTA PER CONSTRAINT ===");
        initialExplanation.getConstraintMatchTotalMap().forEach((name, initialTotal) -> {
            var afterTotal = afterExplanation.getConstraintMatchTotalMap().get(name);
            HardSoftScore before = initialTotal.getScore();
            HardSoftScore after = afterTotal != null ? afterTotal.getScore() : HardSoftScore.ZERO;
            if (!before.equals(after)) {
                System.out.printf("  %-45s: %s -> %s (delta: %dhard/%dsoft)%n",
                        name, before, after,
                        after.hardScore() - before.hardScore(),
                        after.softScore() - before.softScore());
            }
        });

        // The assignment should IMPROVE the score.
        // With the exactlyOneBreak fix (doesn't fire for < breakMinShiftHours slots),
        // the delta is: +800 (underZero removed) -700 (under added) +1 (bulk) = +101 hard
        assertThat(afterScore.hardScore())
                .as("Assigning one agent should improve hard score")
                .isGreaterThan(initialScore.hardScore());
        assertThat(afterScore.hardScore() - initialScore.hardScore())
                .as("Hard score delta should be +101 (no break penalty for 1 slot)")
                .isEqualTo(101);
    }

    @Test
    void fullAssert_solverShouldAssignAgents() {
        Schedule schedule = buildUnassignedSchedule();

        // Run with FULL_ASSERT to catch incremental scoring bugs
        SolverConfig solverConfig = new SolverConfig()
                .withSolutionClass(Schedule.class)
                .withEntityClasses(AgentAssignment.class)
                .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
                .withScoreDirectorFactory(new ScoreDirectorFactoryConfig()
                        .withConstraintProviderClass(ScheduleConstraintProvider.class))
                .withPhases(
                        new ConstructionHeuristicPhaseConfig(),
                        new LocalSearchPhaseConfig()
                                .withTerminationConfig(new TerminationConfig()
                                        .withSpentLimit(Duration.ofSeconds(5))));

        SolverFactory<Schedule> solverFactory = SolverFactory.create(solverConfig);
        Schedule solved = solverFactory.buildSolver().solve(schedule);

        long assigned = solved.getAssignments().stream()
                .filter(a -> a.getDeskAgent() != null).count();

        System.out.println("FULL_ASSERT solver: assigned=" + assigned + "/"
                + solved.getAssignments().size() + ", score=" + solved.getScore());

        assertThat(assigned)
                .as("FULL_ASSERT solver should assign agents")
                .isGreaterThan(0);
    }

    private Schedule buildUnassignedSchedule() {
        UUID deskId = UUID.randomUUID();
        UUID scheduleId = UUID.randomUUID();

        Specialization basic = spec(deskId, "Basic");
        Specialization second = spec(deskId, "Second");

        List<Timeslot> timeslots = new ArrayList<>();
        Map<LocalTime, Timeslot> tsByStart = new HashMap<>();
        for (LocalTime t = START; t.isBefore(END); t = t.plusMinutes(INCREMENT)) {
            Timeslot ts = timeslot(deskId, scheduleId, DAY, t, t.plusMinutes(INCREMENT));
            timeslots.add(ts);
            tsByStart.put(t, ts);
        }

        List<DeskAgent> allDeskAgents = new ArrayList<>(95);
        List<AgentDayConfig> dayConfigs = new ArrayList<>(95);

        for (int i = 0; i < 95; i++) {
            Agent a = agent(String.valueOf(i + 1), "Agent-" + (i + 1));
            DeskAgent da = deskAgent(deskId, a, basic, List.of(second), CONTRACTED_HOURS);
            allDeskAgents.add(da);
            dayConfigs.add(new AgentDayConfig(
                    da.getId(), DAY, CONTRACTED_HOURS,
                    INCREMENT, BREAK_DURATION,
                    BREAK_MIN_SHIFT, BREAK_BLOCKED,
                    BREAK_ALIGNMENT));
        }

        int[] demandPerSlot = {20, 42, 63, 79, 75, 76, 74, 77, 83, 66, 45, 20};
        int totalDemand = 0;
        for (int d : demandPerSlot) totalDemand += d;

        List<StaffingRequirement> staffingReqs = new ArrayList<>();
        List<AgentAssignment> assignments = new ArrayList<>();

        for (int s = 0; s < 12; s++) {
            if (demandPerSlot[s] <= 0) continue;
            LocalTime t = START.plusMinutes((long) s * INCREMENT);
            Timeslot ts = tsByStart.get(t);

            StaffingRequirement sr = new StaffingRequirement();
            sr.setId(UUID.randomUUID());
            sr.setTenantId(TENANT);
            sr.setDeskId(deskId);
            sr.setScheduleId(scheduleId);
            sr.setTimeslot(ts);
            sr.setSpecialization(basic);
            sr.setRequiredHours(new BigDecimal(demandPerSlot[s]));
            staffingReqs.add(sr);

            for (int i = 0; i < demandPerSlot[s]; i++) {
                AgentAssignment aa = new AgentAssignment();
                aa.setId(UUID.randomUUID());
                aa.setTenantId(TENANT);
                aa.setDeskId(deskId);
                aa.setScheduleId(scheduleId);
                aa.setTimeslot(ts);
                aa.setRequiredSpecialization(basic);
                assignments.add(aa);
            }
        }

        List<DayDemandConfig> dayDemandConfigs = List.of(
                new DayDemandConfig(DAY, totalDemand));

        ConstraintWeights weights = new ConstraintWeights();
        weights.setId(UUID.randomUUID());
        weights.setTenantId(TENANT);
        weights.setDeskId(deskId);

        Schedule schedule = new Schedule();
        schedule.setId(scheduleId);
        schedule.setTenantId(TENANT);
        schedule.setDeskId(deskId);
        schedule.setIncrementMinutes(INCREMENT);
        schedule.setStartTime(START);
        schedule.setEndTime(END);
        schedule.setPeriodStartDate(DAY);
        schedule.setPeriodEndDate(DAY);
        schedule.setBreakBlockedHours(BREAK_BLOCKED);
        schedule.setBreakDurationMinutes(BREAK_DURATION);
        schedule.setBreakMinShiftHours(BREAK_MIN_SHIFT);
        schedule.setBreakStartAlignment(BREAK_ALIGNMENT);
        schedule.setDefaultContractedHoursPerDay(CONTRACTED_HOURS);
        schedule.setOverallocationHardLimitPct(130);
        schedule.setUnderallocationHardLimitPct(70);
        schedule.setStatus(ScheduleStatus.RUNNING);

        schedule.setConstraintWeights(weights);
        schedule.setSpecializations(List.of(basic));
        schedule.setDeskAgents(allDeskAgents);
        schedule.setTimeslots(timeslots);
        schedule.setStaffingRequirements(staffingReqs);
        schedule.setAgentPreferences(List.of());
        schedule.setAgentDaysOff(List.of());
        schedule.setAgentExceptions(List.of());
        schedule.setAgentDayConfigs(dayConfigs);
        schedule.setDayDemandConfigs(dayDemandConfigs);
        schedule.setAssignments(assignments);

        return schedule;
    }

    private Specialization spec(UUID deskId, String name) {
        Specialization s = new Specialization();
        s.setId(UUID.randomUUID());
        s.setTenantId(TENANT);
        s.setDeskId(deskId);
        s.setName(name);
        return s;
    }

    private Agent agent(String bambooId, String name) {
        Agent a = new Agent();
        a.setId(UUID.randomUUID());
        a.setTenantId(TENANT);
        a.setBamboohrId(bambooId);
        a.setName(name);
        a.setActive(true);
        return a;
    }

    private DeskAgent deskAgent(UUID deskId, Agent agent, Specialization primary,
                                List<Specialization> secondaries, BigDecimal contractedHours) {
        DeskAgent da = new DeskAgent();
        da.setId(UUID.randomUUID());
        da.setTenantId(TENANT);
        da.setDeskId(deskId);
        da.setAgent(agent);
        da.setPrimarySpecialization(primary);
        da.setSecondarySpecializations(new ArrayList<>(secondaries));
        da.setContractedHoursPerDay(contractedHours);
        return da;
    }

    private Timeslot timeslot(UUID deskId, UUID scheduleId,
                              LocalDate date, LocalTime start, LocalTime end) {
        Timeslot ts = new Timeslot();
        ts.setId(UUID.randomUUID());
        ts.setTenantId(TENANT);
        ts.setDeskId(deskId);
        ts.setScheduleId(scheduleId);
        ts.setDate(date);
        ts.setStartTime(start);
        ts.setEndTime(end);
        return ts;
    }
}
