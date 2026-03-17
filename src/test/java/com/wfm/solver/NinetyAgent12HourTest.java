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
 * 90-agent, 12-hour coverage (08:00-20:00), 60-min timeslots, 8-hour contracts.
 * Pre-assigned solution with shaped demand matching supply distribution.
 *
 * <h3>Agent distribution</h3>
 * <ul>
 *   <li>20 "early" agents (start 08:00, shift 08:00-17:00)</li>
 *   <li>25 "mid-early" agents (start 09:00, shift 09:00-18:00)</li>
 *   <li>25 "mid-late" agents (start 10:00, shift 10:00-19:00)</li>
 *   <li>20 "late" agents (start 11:00, shift 11:00-20:00)</li>
 * </ul>
 *
 * <h3>Demand per slot (hours = agent count for 60-min slots)</h3>
 * <pre>
 * Slot  Time   Demand  Working
 *  0    08:00    20      20
 *  1    09:00    42      42
 *  2    10:00    63      63
 *  3    11:00    79      79
 *  4    12:00    75      75
 *  5    13:00    75      75
 *  6    14:00    75      75
 *  7    15:00    77      77
 *  8    16:00    83      83
 *  9    17:00    66      66
 * 10    18:00    45      45
 * 11    19:00    20      20
 * Total:        720     720  (= 90 × 8, ratio = 100%)
 * </pre>
 */
class NinetyAgent12HourTest {

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
    void ninetyAgents_12hour_shapedDemand_shouldScoreZeroHard() {
        Schedule solution = buildPreAssignedSolution();

        SolverFactory<Schedule> solverFactory = SolverFactory.create(
                new SolverConfig()
                        .withSolutionClass(Schedule.class)
                        .withEntityClasses(AgentAssignment.class)
                        .withScoreDirectorFactory(new ScoreDirectorFactoryConfig()
                                .withConstraintProviderClass(ScheduleConstraintProvider.class)));

        var solutionManager = ai.timefold.solver.core.api.solver.SolutionManager
                .<Schedule, HardSoftScore>create(solverFactory);
        HardSoftScore score = solutionManager.update(solution);

        System.out.println("Score: " + score);
        System.out.println("Agents: " + solution.getDeskAgents().size());
        System.out.println("Timeslots: " + solution.getTimeslots().size());
        System.out.println("Assignments: " + solution.getAssignments().size());

        // Per-slot coverage
        Map<LocalTime, Long> coveragePerSlot = solution.getAssignments().stream()
                .filter(a -> a.getDeskAgent() != null)
                .collect(java.util.stream.Collectors.groupingBy(
                        a -> a.getTimeslot().getStartTime(),
                        java.util.stream.Collectors.counting()));
        System.out.println("Coverage per slot:");
        coveragePerSlot.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> System.out.println("  " + e.getKey() + ": " + e.getValue()));

        if (!score.equals(HardSoftScore.ZERO)) {
            var explanation = solutionManager.explain(solution);
            System.out.println("=== Constraint Matches ===");
            explanation.getConstraintMatchTotalMap().forEach((name, total) -> {
                if (!total.getScore().equals(HardSoftScore.ZERO)) {
                    System.out.println("  " + name + " => " + total.getScore()
                            + " (matches: " + total.getConstraintMatchCount() + ")");
                }
            });
        }

        assertThat(score.hardScore())
                .as("Hard score should be 0 — 90 agents, 12h, shaped demand, all primary")
                .isZero();

        assertThat(score.softScore())
                .as("Soft score should be 0 — all primary, no preferences")
                .isZero();
    }

    /**
     * 90 agents, 12-hour coverage, all assignments start UNASSIGNED.
     * Runs CH + brief local search. This replicates the live scenario where
     * the solver must build the initial solution from scratch.
     */
    @Test
    void ninetyAgents_solverCH_shouldAssignAgents() {
        Schedule schedule = buildPreAssignedSolution();

        // Clear all assignments to simulate all-unassigned start
        for (AgentAssignment a : schedule.getAssignments()) {
            a.setDeskAgent(null);
            a.setAgent(null);
        }

        // Verify all unassigned
        assertThat(schedule.getAssignments().stream().filter(a -> a.getDeskAgent() != null).count())
                .as("All assignments should start unassigned").isZero();

        System.out.println("Starting 90-agent solver test with " + schedule.getAssignments().size()
                + " assignments and " + schedule.getDeskAgents().size() + " agents");

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
                .filter(a -> a.getDeskAgent() != null).count();
        System.out.println("90-agent solver: assigned=" + assigned + "/"
                + solved.getAssignments().size() + ", score=" + solved.getScore()
                + ", elapsed=" + elapsed + "ms");

        // Print constraint breakdown
        SolverFactory<Schedule> scoringFactory = SolverFactory.create(
                new SolverConfig()
                        .withSolutionClass(Schedule.class)
                        .withEntityClasses(AgentAssignment.class)
                        .withScoreDirectorFactory(new ScoreDirectorFactoryConfig()
                                .withConstraintProviderClass(ScheduleConstraintProvider.class)));
        var solutionManager = ai.timefold.solver.core.api.solver.SolutionManager
                .<Schedule, HardSoftScore>create(scoringFactory);
        if (solved.getScore() != null && !solved.getScore().equals(HardSoftScore.ZERO)) {
            var explanation = solutionManager.explain(solved);
            System.out.println("=== Constraint Matches ===");
            explanation.getConstraintMatchTotalMap().forEach((name, total) -> {
                if (!total.getScore().equals(HardSoftScore.ZERO)) {
                    System.out.printf("  %-35s => %s (violations: %d)%n",
                            name, total.getScore(), total.getConstraintMatchCount());
                }
            });
        }

        // The solver should assign a significant portion of seats
        assertThat(assigned)
                .as("Solver should assign agents to seats")
                .isGreaterThan(0);

        // With 90 agents × 8hr × 60-min = 720 supply slots = 720 demand,
        // exact per-agent contracted hours make this a tight problem.
        // Verify at least 95% of assignments are filled.
        assertThat(assigned)
                .as("Most assignments should be filled (supply = demand)")
                .isGreaterThanOrEqualTo((long)(720 * 0.95));
    }

    // ------------------------------------------------------------------
    //  Pre-assigned solution builder
    // ------------------------------------------------------------------

    /**
     * Agent groups:
     *   Group 0 (n=20): shift 08:00-17:00, breaks at slots 1-7 (7 positions)
     *   Group 1 (n=25): shift 09:00-18:00, breaks at slots 2-7 (6 positions)
     *   Group 2 (n=25): shift 10:00-19:00, breaks at slots 3-8 (6 positions)
     *   Group 3 (n=20): shift 11:00-20:00, breaks at slots 4-9 (6 positions)
     *
     * Break distribution (round-robin within each group):
     *   Group 0 (20, 7 pos): 3,3,3,3,3,3,2
     *   Group 1 (25, 6 pos): 4,4,5,4,4,4
     *   Group 2 (25, 6 pos): 4,4,5,4,4,4
     *   Group 3 (20, 6 pos): 3,3,3,4,3,4
     *
     * Resulting breaks per slot:
     *   Slot 1: 3      Slot 5: 3+4+5+3=15
     *   Slot 2: 3+4=7  Slot 6: 3+4+4+4=15
     *   Slot 3: 3+4+4=11  Slot 7: 2+4+4+3=13
     *   Slot 4: 3+5+4+3=15  Slot 8: 4+4=8 (actually, we need 7 here)
     *   Slot 9: 4
     *
     * I'll use specific integer assignments to get exact totals.
     */
    private Schedule buildPreAssignedSolution() {
        UUID deskId = UUID.randomUUID();
        UUID scheduleId = UUID.randomUUID();

        Specialization basic = spec(deskId, "IT Support");
        Specialization second = spec(deskId, "IT Support (Spanish)");
        Specialization none = spec(deskId, "None");

        // --- Timeslots: 08:00-20:00, 60-min ---
        List<Timeslot> timeslots = new ArrayList<>();
        Map<LocalTime, Timeslot> tsByStart = new HashMap<>();
        for (LocalTime t = START; t.isBefore(END); t = t.plusMinutes(INCREMENT)) {
            Timeslot ts = timeslot(deskId, scheduleId, DAY, t, t.plusMinutes(INCREMENT));
            timeslots.add(ts);
            tsByStart.put(t, ts);
        }

        // --- Agent groups ---
        // Group def: (shiftStart, shiftEnd, breakSlots[])
        // Each agent works 8 of 9 shift slots (skip their break)
        int[][] groups = {
                // group 0: 20 agents, shift 08:00-17:00 (slots 0-8)
                // eligible breaks: slots 1-7
                {20, 0, 8},
                // group 1: 25 agents, shift 09:00-18:00 (slots 1-9)
                // eligible breaks: slots 2-7
                {25, 1, 9},
                // group 2: 25 agents, shift 10:00-19:00 (slots 2-10)
                // eligible breaks: slots 3-8
                {25, 2, 10},
                // group 3: 20 agents, shift 11:00-20:00 (slots 3-11)
                // eligible breaks: slots 4-9
                {20, 3, 11},
        };

        // Break slot assignments per group (list of slot indices)
        // Distributed to achieve desired per-slot break counts
        int[][] breakPositions = {
                // Group 0 (20 agents): eligible 1-7
                // 3,3,3,3,3,3,2 across slots 1-7
                {1,1,1, 2,2,2, 3,3,3, 4,4,4, 5,5,5, 6,6,6, 7,7},
                // Group 1 (25 agents): eligible 2-7
                // 4,4,5,4,4,4 across slots 2-7
                {2,2,2,2, 3,3,3,3, 4,4,4,4,4, 5,5,5,5, 6,6,6,6, 7,7,7,7},
                // Group 2 (25 agents): eligible 3-8
                // 4,4,4,5,4,4 across slots 3-8
                {3,3,3,3, 4,4,4,4, 5,5,5,5, 6,6,6,6,6, 7,7,7,7, 8,8,8,8},
                // Group 3 (20 agents): eligible 4-9
                // 3,3,4,3,3,4 across slots 4-9
                {4,4,4, 5,5,5, 6,6,6,6, 7,7,7, 8,8,8, 9,9,9,9},
        };

        List<DeskAgent> allDeskAgents = new ArrayList<>(90);
        List<AgentAssignment> assignments = new ArrayList<>(720);
        List<AgentDayConfig> dayConfigs = new ArrayList<>(90);
        // Track working agents per slot for demand
        int[] workingPerSlot = new int[12];

        int agentIdx = 0;
        for (int g = 0; g < groups.length; g++) {
            int count = groups[g][0];
            int shiftStartSlot = groups[g][1];
            int shiftEndSlot = groups[g][2]; // exclusive (last slot + 1)

            for (int i = 0; i < count; i++) {
                Agent a = agent(String.valueOf(agentIdx + 1), "Agent-" + (agentIdx + 1));
                Specialization primary = agentIdx < 20 ? none : basic;
                List<Specialization> secondaries = agentIdx < 20 ? List.of(second) : List.of();
                DeskAgent da = deskAgent(deskId, a, primary, secondaries, CONTRACTED_HOURS);
                allDeskAgents.add(da);
                dayConfigs.add(new AgentDayConfig(
                        da.getId(), DAY, CONTRACTED_HOURS,
                        INCREMENT, BREAK_DURATION,
                        BREAK_MIN_SHIFT, BREAK_BLOCKED,
                        BREAK_ALIGNMENT, 130, 70));

                int breakSlot = breakPositions[g][i];

                // Assign to all shift slots except break
                for (int s = shiftStartSlot; s <= shiftEndSlot; s++) {
                    if (s == breakSlot) continue;
                    LocalTime slotTime = START.plusMinutes((long) s * INCREMENT);
                    Timeslot ts = tsByStart.get(slotTime);
                    assignments.add(assignment(deskId, scheduleId, ts, basic, da, a));
                    workingPerSlot[s]++;
                }
                agentIdx++;
            }
        }

        // Print demand table
        System.out.println("=== Demand per slot ===");
        int totalDemand = 0;
        for (int s = 0; s < 12; s++) {
            LocalTime t = START.plusMinutes((long) s * INCREMENT);
            System.out.println("  Slot " + s + " (" + t + "): " + workingPerSlot[s]);
            totalDemand += workingPerSlot[s];
        }
        System.out.println("  Total demand: " + totalDemand);

        // --- Staffing Requirements: demand = working agents per slot ---
        List<StaffingRequirement> staffingReqs = new ArrayList<>();
        for (int s = 0; s < 12; s++) {
            if (workingPerSlot[s] <= 0) continue;
            LocalTime t = START.plusMinutes((long) s * INCREMENT);
            Timeslot ts = tsByStart.get(t);
            StaffingRequirement sr = new StaffingRequirement();
            sr.setId(UUID.randomUUID());
            sr.setTenantId(TENANT);
            sr.setDeskId(deskId);
            sr.setScheduleId(scheduleId);
            sr.setTimeslot(ts);
            sr.setSpecialization(basic);
            sr.setRequiredFTEs(workingPerSlot[s]);
            staffingReqs.add(sr);
        }

        // TimeslotDemandConfig based on assignments per timeslot
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
        schedule.setDeskAgents(allDeskAgents);
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
        List<TimeslotDemandConfig> configs = new ArrayList<>();
        for (Map.Entry<Timeslot, Integer> e : demandPerTimeslot.entrySet()) {
            configs.add(new TimeslotDemandConfig(e.getKey(), e.getValue()));
        }
        return configs;
    }

    private AgentAssignment assignment(UUID deskId, UUID scheduleId,
                                       Timeslot ts, Specialization spec,
                                       DeskAgent da, Agent agent) {
        AgentAssignment aa = new AgentAssignment();
        aa.setId(UUID.randomUUID());
        aa.setTenantId(TENANT);
        aa.setDeskId(deskId);
        aa.setScheduleId(scheduleId);
        aa.setTimeslot(ts);
        aa.setRequiredSpecialization(spec);
        aa.setDeskAgent(da);
        aa.setAgent(agent);
        return aa;
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
