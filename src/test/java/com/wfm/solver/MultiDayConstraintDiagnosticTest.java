package com.wfm.solver;

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.score.director.ScoreDirectorFactoryConfig;
import ai.timefold.solver.core.config.solver.SolverConfig;
import com.wfm.model.*;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Diagnostic test that reproduces the multi-day constraint violation pattern:
 *   Break duration:              HARD  10   -10/0
 *   Contracted hours:            HARD  30   -34/0
 *   Exactly one break:           HARD  110  -110/0
 *   One assignment per timeslot: HARD  75   -75/0
 *
 * Tests multi-day scenarios with BreakAwareConstructionPhase pre-assignment
 * to find the root cause of these violations.
 */
class MultiDayConstraintDiagnosticTest {

    private static final long TENANT = 1L;
    private static final int INCREMENT = 60;
    private static final int BREAK_DURATION = 60;
    private static final BigDecimal BREAK_BLOCKED = new BigDecimal("1.00");
    private static final BigDecimal BREAK_MIN_SHIFT = new BigDecimal("4.00");
    private static final BreakAlignment BREAK_ALIGNMENT = BreakAlignment.ON_HOUR;
    private static final BigDecimal CONTRACTED_HOURS = new BigDecimal("8.00");

    /**
     * 10 agents, 11 days (Mon-Fri × 2 + Mon), 60-min timeslots, 9-hour coverage.
     * This should produce ~110 agent-days. Tests whether the construction phase
     * produces a feasible multi-day solution.
     */
    @Test
    void multiDay_10agents_11days_shouldScoreZeroHard() {
        LocalDate startDate = LocalDate.of(2026, 3, 16); // Monday
        LocalDate endDate = LocalDate.of(2026, 3, 26);   // Thursday (11 days)
        LocalTime startTime = LocalTime.of(9, 0);
        LocalTime endTime = LocalTime.of(18, 0);
        int agentCount = 10;

        Schedule schedule = buildMultiDaySchedule(
                startDate, endDate, startTime, endTime, agentCount);

        // Pre-assign
        BreakAwareConstructionPhase phase = new BreakAwareConstructionPhase();
        int preAssigned = phase.preAssign(schedule);

        System.out.println("Multi-day pre-assigned: " + preAssigned + "/" + schedule.getAssignments().size());

        // Score
        SolverFactory<Schedule> solverFactory = SolverFactory.create(
                new SolverConfig()
                        .withSolutionClass(Schedule.class)
                        .withEntityClasses(AgentAssignment.class)
                        .withScoreDirectorFactory(new ScoreDirectorFactoryConfig()
                                .withConstraintProviderClass(ScheduleConstraintProvider.class)));

        var solutionManager = ai.timefold.solver.core.api.solver.SolutionManager
                .<Schedule, HardSoftScore>create(solverFactory);
        HardSoftScore score = solutionManager.update(schedule);

        System.out.println("Score: " + score);
        System.out.println("Agents: " + schedule.getDeskAgents().size());
        System.out.println("Days: " + java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) + 1);
        System.out.println("Timeslots: " + schedule.getTimeslots().size());
        System.out.println("Assignments: " + schedule.getAssignments().size());
        System.out.println("AgentDayConfigs: " + schedule.getAgentDayConfigs().size());

        if (!score.equals(HardSoftScore.ZERO)) {
            var explanation = solutionManager.explain(schedule);
            System.out.println("=== Constraint Matches ===");
            explanation.getConstraintMatchTotalMap().forEach((name, total) -> {
                if (!total.getScore().equals(HardSoftScore.ZERO)) {
                    System.out.printf("  %-35s => %s (violations: %d)%n",
                            name, total.getScore(), total.getConstraintMatchCount());
                }
            });
        }

        // Verify all assigned
        long assigned = schedule.getAssignments().stream()
                .filter(a -> a.getDeskAgent() != null).count();
        System.out.println("Assigned: " + assigned + "/" + schedule.getAssignments().size());

        // Check per-agent-day assignment counts
        Map<String, Integer> agentDayCounts = new LinkedHashMap<>();
        for (AgentAssignment aa : schedule.getAssignments()) {
            if (aa.getDeskAgent() != null) {
                String key = aa.getDeskAgent().getAgent().getName() + "|" + aa.getTimeslot().getDate();
                agentDayCounts.merge(key, 1, Integer::sum);
            }
        }
        int minCount = agentDayCounts.values().stream().mapToInt(i -> i).min().orElse(0);
        int maxCount = agentDayCounts.values().stream().mapToInt(i -> i).max().orElse(0);
        System.out.println("Agent-day slot counts — min: " + minCount + ", max: " + maxCount);

        assertThat(score.hardScore())
                .as("Hard score should be 0 (feasible)")
                .isZero();
    }

    /**
     * 22 agents, 5 days (Mon-Fri), 60-min timeslots, 9-hour coverage.
     * Alternative decomposition of ~110 agent-days.
     */
    @Test
    void multiDay_22agents_5days_shouldScoreZeroHard() {
        LocalDate startDate = LocalDate.of(2026, 3, 16); // Monday
        LocalDate endDate = LocalDate.of(2026, 3, 20);   // Friday (5 days)
        LocalTime startTime = LocalTime.of(9, 0);
        LocalTime endTime = LocalTime.of(18, 0);
        int agentCount = 22;

        Schedule schedule = buildMultiDaySchedule(
                startDate, endDate, startTime, endTime, agentCount);

        BreakAwareConstructionPhase phase = new BreakAwareConstructionPhase();
        int preAssigned = phase.preAssign(schedule);

        System.out.println("22-agent 5-day pre-assigned: " + preAssigned + "/" + schedule.getAssignments().size());

        SolverFactory<Schedule> solverFactory = SolverFactory.create(
                new SolverConfig()
                        .withSolutionClass(Schedule.class)
                        .withEntityClasses(AgentAssignment.class)
                        .withScoreDirectorFactory(new ScoreDirectorFactoryConfig()
                                .withConstraintProviderClass(ScheduleConstraintProvider.class)));

        var solutionManager = ai.timefold.solver.core.api.solver.SolutionManager
                .<Schedule, HardSoftScore>create(solverFactory);
        HardSoftScore score = solutionManager.update(schedule);

        System.out.println("Score: " + score);

        if (!score.equals(HardSoftScore.ZERO)) {
            var explanation = solutionManager.explain(schedule);
            System.out.println("=== Constraint Matches ===");
            explanation.getConstraintMatchTotalMap().forEach((name, total) -> {
                if (!total.getScore().equals(HardSoftScore.ZERO)) {
                    System.out.printf("  %-35s => %s (violations: %d)%n",
                            name, total.getScore(), total.getConstraintMatchCount());
                }
            });
        }

        assertThat(score.hardScore())
                .as("Hard score should be 0 (feasible)")
                .isZero();
    }

    // ------------------------------------------------------------------
    //  Multi-day schedule builder
    // ------------------------------------------------------------------

    private Schedule buildMultiDaySchedule(
            LocalDate startDate, LocalDate endDate,
            LocalTime startTime, LocalTime endTime,
            int agentCount) {

        UUID deskId = UUID.randomUUID();
        UUID scheduleId = UUID.randomUUID();

        Specialization basic = spec(deskId, "Basic");

        // Build agents
        List<DeskAgent> deskAgentList = new ArrayList<>(agentCount);
        for (int i = 0; i < agentCount; i++) {
            Agent a = agent(String.valueOf(i + 1), "Agent-" + (i + 1));
            DeskAgent da = deskAgent(deskId, a, basic, List.of(basic), CONTRACTED_HOURS);
            deskAgentList.add(da);
        }

        // Build timeslots for all days
        List<Timeslot> timeslots = new ArrayList<>();
        for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
            for (LocalTime t = startTime; t.isBefore(endTime); t = t.plusMinutes(INCREMENT)) {
                timeslots.add(timeslot(deskId, scheduleId, d, t, t.plusMinutes(INCREMENT)));
            }
        }

        // Break assignment for demand calculation
        List<LocalTime> eligibleBreakHours = List.of(
                LocalTime.of(10, 0), LocalTime.of(11, 0),
                LocalTime.of(12, 0), LocalTime.of(13, 0), LocalTime.of(14, 0),
                LocalTime.of(15, 0), LocalTime.of(16, 0));

        int[] breaksPerSlot = new int[9]; // 09:00-17:00
        for (int i = 0; i < agentCount; i++) {
            LocalTime breakHour = eligibleBreakHours.get(i % eligibleBreakHours.size());
            int slotIndex = breakHour.getHour() - startTime.getHour();
            breaksPerSlot[slotIndex]++;
        }

        // Staffing requirements: demand = agents - agents on break, per day
        List<StaffingRequirement> staffingReqs = new ArrayList<>();
        List<AgentAssignment> assignments = new ArrayList<>();

        for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
            int slotIdx = 0;
            for (LocalTime t = startTime; t.isBefore(endTime); t = t.plusMinutes(INCREMENT)) {
                int workingAgents = agentCount - breaksPerSlot[slotIdx];
                Timeslot ts = findTimeslot(timeslots, d, t);

                if (workingAgents > 0 && ts != null) {
                    StaffingRequirement sr = new StaffingRequirement();
                    sr.setId(UUID.randomUUID());
                    sr.setTenantId(TENANT);
                    sr.setDeskId(deskId);
                    sr.setScheduleId(scheduleId);
                    sr.setTimeslot(ts);
                    sr.setSpecialization(basic);
                    sr.setRequiredHours(new BigDecimal(workingAgents));
                    staffingReqs.add(sr);

                    for (int i = 0; i < workingAgents; i++) {
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
                slotIdx++;
            }
        }

        // AgentDayConfigs for all agent-days
        List<AgentDayConfig> dayConfigs = new ArrayList<>();
        for (DeskAgent da : deskAgentList) {
            for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
                dayConfigs.add(new AgentDayConfig(
                        da.getId(), d, CONTRACTED_HOURS,
                        INCREMENT, BREAK_DURATION,
                        BREAK_MIN_SHIFT, BREAK_BLOCKED,
                        BREAK_ALIGNMENT));
            }
        }

        ConstraintWeights weights = new ConstraintWeights();
        weights.setId(UUID.randomUUID());
        weights.setTenantId(TENANT);
        weights.setDeskId(deskId);

        Schedule schedule = new Schedule();
        schedule.setId(scheduleId);
        schedule.setTenantId(TENANT);
        schedule.setDeskId(deskId);
        schedule.setIncrementMinutes(INCREMENT);
        schedule.setStartTime(startTime);
        schedule.setEndTime(endTime);
        schedule.setPeriodStartDate(startDate);
        schedule.setPeriodEndDate(endDate);
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
        schedule.setDeskAgents(deskAgentList);
        schedule.setTimeslots(timeslots);
        schedule.setStaffingRequirements(staffingReqs);
        schedule.setAgentPreferences(List.of());
        schedule.setAgentDaysOff(List.of());
        schedule.setAgentExceptions(List.of());
        schedule.setAgentDayConfigs(dayConfigs);
        schedule.setDayDemandConfigs(computeDayDemandConfigs(assignments));
        schedule.setAssignments(assignments);

        return schedule;
    }

    private List<DayDemandConfig> computeDayDemandConfigs(List<AgentAssignment> assignments) {
        java.util.Map<java.time.LocalDate, Integer> demandPerDay = new java.util.HashMap<>();
        for (AgentAssignment a : assignments) {
            demandPerDay.merge(a.getTimeslot().getDate(), 1, Integer::sum);
        }
        List<DayDemandConfig> configs = new java.util.ArrayList<>();
        for (java.util.Map.Entry<java.time.LocalDate, Integer> e : demandPerDay.entrySet()) {
            configs.add(new DayDemandConfig(e.getKey(), e.getValue()));
        }
        return configs;
    }

    private Timeslot findTimeslot(List<Timeslot> timeslots, LocalDate date, LocalTime startTime) {
        return timeslots.stream()
                .filter(ts -> ts.getDate().equals(date) && ts.getStartTime().equals(startTime))
                .findFirst().orElse(null);
    }

    // ------------------------------------------------------------------
    //  Factory helpers
    // ------------------------------------------------------------------

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
