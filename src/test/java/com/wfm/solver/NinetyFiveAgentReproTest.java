package com.wfm.solver;

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import ai.timefold.solver.core.config.localsearch.LocalSearchPhaseConfig;
import ai.timefold.solver.core.config.score.director.ScoreDirectorFactoryConfig;
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
 * Reproducer for the 95-agent "no agents assigned" bug.
 * Starts with all assignments UNASSIGNED and verifies the solver CH assigns agents.
 *
 * Setup: 95 agents, 12-hour coverage (08:00-20:00), 60-min timeslots, 8-hour contracts.
 * Demand = 720 slots (slightly less than 95 × 8 = 760 supply).
 */
class NinetyFiveAgentReproTest {

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
    void ninetyFiveAgents_allUnassigned_solverShouldAssign() {
        Schedule schedule = buildUnassignedSchedule();

        // Verify initial state: all unassigned
        assertThat(schedule.getAssignments().stream().filter(a -> a.getAgent() != null).count())
                .as("All assignments should start unassigned").isZero();
        assertThat(schedule.getAgents().size()).isEqualTo(95);

        System.out.println("Starting 95-agent reproducer with " + schedule.getAssignments().size()
                + " assignments and " + schedule.getAgents().size() + " agents");

        // Score initial state
        SolverFactory<Schedule> scoringFactory = SolverFactory.create(
                new SolverConfig()
                        .withSolutionClass(Schedule.class)
                        .withEntityClasses(AgentAssignment.class)
                        .withScoreDirectorFactory(new ScoreDirectorFactoryConfig()
                                .withConstraintProviderClass(ScheduleConstraintProvider.class)));
        var solutionManager = ai.timefold.solver.core.api.solver.SolutionManager
                .<Schedule, HardSoftScore>create(scoringFactory);
        HardSoftScore initialScore = solutionManager.update(schedule);
        System.out.println("Initial score: " + initialScore);

        // Print initial constraint breakdown
        var initExplanation = solutionManager.explain(schedule);
        System.out.println("=== Initial Constraint Matches ===");
        initExplanation.getConstraintMatchTotalMap().forEach((name, total) -> {
            if (!total.getScore().equals(HardSoftScore.ZERO)) {
                System.out.printf("  %-40s => %s (violations: %d)%n",
                        name, total.getScore(), total.getConstraintMatchCount());
            }
        });

        // Run solver: CH + 30s local search
        SolverConfig solverConfig = new SolverConfig()
                .withSolutionClass(Schedule.class)
                .withEntityClasses(AgentAssignment.class)
                .withScoreDirectorFactory(new ScoreDirectorFactoryConfig()
                        .withConstraintProviderClass(ScheduleConstraintProvider.class))
                .withPhases(
                        new ConstructionHeuristicPhaseConfig(),
                        new LocalSearchPhaseConfig()
                                .withTerminationConfig(new TerminationConfig()
                                        .withSpentLimit(Duration.ofSeconds(30))
                                        .withUnimprovedSpentLimit(Duration.ofSeconds(15))));

        long startTime = System.currentTimeMillis();
        SolverFactory<Schedule> solverFactory = SolverFactory.create(solverConfig);
        Schedule solved = solverFactory.buildSolver().solve(schedule);
        long elapsed = System.currentTimeMillis() - startTime;

        long assigned = solved.getAssignments().stream()
                .filter(a -> a.getAgent() != null).count();
        System.out.println("\n95-agent solver: assigned=" + assigned + "/"
                + solved.getAssignments().size() + ", score=" + solved.getScore()
                + ", elapsed=" + elapsed + "ms");

        // Print final constraint breakdown
        SolverFactory<Schedule> scoringFactory2 = SolverFactory.create(
                new SolverConfig()
                        .withSolutionClass(Schedule.class)
                        .withEntityClasses(AgentAssignment.class)
                        .withScoreDirectorFactory(new ScoreDirectorFactoryConfig()
                                .withConstraintProviderClass(ScheduleConstraintProvider.class)));
        var sm2 = ai.timefold.solver.core.api.solver.SolutionManager
                .<Schedule, HardSoftScore>create(scoringFactory2);
        if (solved.getScore() != null && !solved.getScore().equals(HardSoftScore.ZERO)) {
            var explanation = sm2.explain(solved);
            System.out.println("=== Final Constraint Matches ===");
            explanation.getConstraintMatchTotalMap().forEach((name, total) -> {
                if (!total.getScore().equals(HardSoftScore.ZERO)) {
                    System.out.printf("  %-40s => %s (violations: %d)%n",
                            name, total.getScore(), total.getConstraintMatchCount());
                }
            });
        }

        // Count unique agents assigned
        long uniqueAgents = solved.getAssignments().stream()
                .filter(a -> a.getAgent() != null)
                .map(a -> a.getAgent().getId())
                .distinct()
                .count();
        System.out.println("Unique agents assigned: " + uniqueAgents + " / 95");

        // The solver MUST assign agents
        assertThat(assigned)
                .as("Solver should assign agents to seats")
                .isGreaterThan(0);

        // With 95 agents needing exactly 8hr each (760 slots) but only 720 demand,
        // the solver cannot perfectly satisfy all per-agent contracted hours.
        // At most 90 agents can get exactly 8 hours; remaining 5 will be under-assigned.
        // Verify that most demand is filled (at least 90%).
        assertThat(assigned)
                .as("Most assignments should be filled")
                .isGreaterThanOrEqualTo((long)(720 * 0.90));
    }

    private Schedule buildUnassignedSchedule() {
        UUID deskId = UUID.randomUUID();
        UUID scheduleId = UUID.randomUUID();

        Specialization basic = spec(deskId, "IT Support");
        Specialization second = spec(deskId, "IT Support (Spanish)");

        // --- Timeslots: 08:00-20:00, 60-min ---
        List<Timeslot> timeslots = new ArrayList<>();
        Map<LocalTime, Timeslot> tsByStart = new HashMap<>();
        for (LocalTime t = START; t.isBefore(END); t = t.plusMinutes(INCREMENT)) {
            Timeslot ts = timeslot(deskId, scheduleId, DAY, t, t.plusMinutes(INCREMENT));
            timeslots.add(ts);
            tsByStart.put(t, ts);
        }

        // --- 95 Agents ---
        List<Agent> allAgents = new ArrayList<>(95);
        List<AgentDayConfig> dayConfigs = new ArrayList<>(95);

        for (int i = 0; i < 95; i++) {
            Agent a = agent(String.valueOf(i + 1), "Agent-" + (i + 1));
            List<Specialization> secondaries = i < 20 ? List.of(second) : List.of();
            configureAgent(a, deskId, basic, secondaries, CONTRACTED_HOURS);
            allAgents.add(a);
            dayConfigs.add(new AgentDayConfig(
                    a.getId(), DAY, CONTRACTED_HOURS,
                    INCREMENT, BREAK_DURATION,
                    BREAK_MIN_SHIFT, BREAK_BLOCKED,
                    BREAK_ALIGNMENT, 130, 70));
        }

        // --- Demand: shaped to match 90-agent test (720 total slots) ---
        int[] demandPerSlot = {20, 42, 63, 79, 75, 76, 74, 77, 83, 66, 45, 20};
        int totalDemand = 0;
        for (int d : demandPerSlot) totalDemand += d;
        System.out.println("Total demand: " + totalDemand + " (supply: " + (95 * 8) + ")");

        // --- Staffing Requirements ---
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
            sr.setRequiredFTEs(demandPerSlot[s]);
            staffingReqs.add(sr);

            // Create assignments (ALL UNASSIGNED - agent = null)
            for (int i = 0; i < demandPerSlot[s]; i++) {
                AgentAssignment aa = new AgentAssignment();
                aa.setId(UUID.randomUUID());
                aa.setTenantId(TENANT);
                aa.setDeskId(deskId);
                aa.setScheduleId(scheduleId);
                aa.setTimeslot(ts);
                aa.setRequiredSpecialization(basic);
                // agent = null (solver assigns)
                assignments.add(aa);
            }
        }

        List<TimeslotDemandConfig> timeslotDemandConfigs = computeTimeslotDemandConfigs(assignments);

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
        schedule.setAgents(allAgents);
        schedule.setTimeslots(timeslots);
        schedule.setStaffingRequirements(staffingReqs);
        schedule.setAgentPreferences(List.of());
        schedule.setAgentDaysOff(List.of());
        schedule.setAgentExceptions(List.of());
        schedule.setAgentDayConfigs(dayConfigs);
        schedule.setTimeslotDemandConfigs(timeslotDemandConfigs);
        schedule.setAssignments(assignments);

        return schedule;
    }

    // Factory helpers

    private List<TimeslotDemandConfig> computeTimeslotDemandConfigs(List<AgentAssignment> assignments) {
        Map<Timeslot, Integer> demandPerTimeslot = new java.util.LinkedHashMap<>();
        for (AgentAssignment a : assignments) {
            demandPerTimeslot.merge(a.getTimeslot(), 1, Integer::sum);
        }
        List<TimeslotDemandConfig> configs = new java.util.ArrayList<>();
        for (Map.Entry<Timeslot, Integer> e : demandPerTimeslot.entrySet()) {
            configs.add(new TimeslotDemandConfig(e.getKey(), e.getValue()));
        }
        return configs;
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

    private void configureAgent(Agent agent, UUID deskId, Specialization primary,
                                List<Specialization> secondaries, BigDecimal contractedHours) {
        agent.setDeskId(deskId);
        agent.setPrimarySpecialization(primary);
        agent.setSecondarySpecializations(new ArrayList<>(secondaries));
        agent.setContractedHoursPerDay(contractedHours);
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
