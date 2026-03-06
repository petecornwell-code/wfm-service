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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the BreakAwareConstructionPhase by having it pre-assign 150 agents
 * to 1200 assignments (60-min timeslots, 9-hour day), then scoring the result.
 * This simulates what SolverService.startSolve() does before launching the solver.
 */
class BreakAwareConstructionTest {

    private static final long TENANT = 1L;
    private static final LocalDate DAY = LocalDate.of(2026, 3, 16);
    private static final LocalTime START = LocalTime.of(9, 0);
    private static final LocalTime END = LocalTime.of(18, 0);
    private static final int INCREMENT = 60;
    private static final int BREAK_DURATION = 60;
    private static final BigDecimal BREAK_BLOCKED = new BigDecimal("1.00");
    private static final BigDecimal BREAK_MIN_SHIFT = new BigDecimal("4.00");
    private static final BreakAlignment BREAK_ALIGNMENT = BreakAlignment.ON_HOUR;
    private static final BigDecimal CONTRACTED_HOURS = new BigDecimal("8.00");

    @Test
    void preAssign_150agents_shouldProduceFeasibleSolution() {
        Schedule schedule = buildUnassignedSchedule();

        // Verify all assignments start unassigned
        assertThat(schedule.getAssignments().stream().filter(a -> a.getDeskAgent() != null).count())
                .as("All assignments should start unassigned")
                .isZero();

        // Run the break-aware pre-assignment
        BreakAwareConstructionPhase phase = new BreakAwareConstructionPhase();
        int preAssigned = phase.preAssign(schedule);

        System.out.println("Pre-assigned: " + preAssigned + "/" + schedule.getAssignments().size());

        // Score the pre-assigned solution
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
        System.out.println("Timeslots: " + schedule.getTimeslots().size());
        System.out.println("Assignments: " + schedule.getAssignments().size());

        if (!score.equals(HardSoftScore.ZERO)) {
            var explanation = solutionManager.explain(schedule);
            System.out.println("=== Constraint Matches ===");
            explanation.getConstraintMatchTotalMap().forEach((name, total) -> {
                if (!total.getScore().equals(HardSoftScore.ZERO)) {
                    System.out.println("  " + name + " => " + total.getScore());
                }
            });
        }

        // All assignments should be pre-assigned
        assertThat(preAssigned)
                .as("All assignments should be pre-assigned")
                .isEqualTo(schedule.getAssignments().size());

        // Hard score should be 0 (feasible)
        assertThat(score.hardScore())
                .as("Hard score should be 0 (feasible) after break-aware pre-assignment")
                .isZero();
    }

    @Test
    void preAssign_twoAgents_15minSlots_shouldProduceFeasibleSolution() {
        Schedule schedule = buildTwoAgentSchedule();

        BreakAwareConstructionPhase phase = new BreakAwareConstructionPhase();
        int preAssigned = phase.preAssign(schedule);

        System.out.println("2-agent pre-assigned: " + preAssigned + "/" + schedule.getAssignments().size());

        SolverFactory<Schedule> solverFactory = SolverFactory.create(
                new SolverConfig()
                        .withSolutionClass(Schedule.class)
                        .withEntityClasses(AgentAssignment.class)
                        .withScoreDirectorFactory(new ScoreDirectorFactoryConfig()
                                .withConstraintProviderClass(ScheduleConstraintProvider.class)));

        var solutionManager = ai.timefold.solver.core.api.solver.SolutionManager
                .<Schedule, HardSoftScore>create(solverFactory);
        HardSoftScore score = solutionManager.update(schedule);

        System.out.println("2-agent score: " + score);

        if (!score.equals(HardSoftScore.ZERO)) {
            var explanation = solutionManager.explain(schedule);
            System.out.println("=== Constraint Matches ===");
            explanation.getConstraintMatchTotalMap().forEach((name, total) -> {
                if (!total.getScore().equals(HardSoftScore.ZERO)) {
                    System.out.println("  " + name + " => " + total.getScore());
                }
            });
        }

        assertThat(preAssigned).isEqualTo(schedule.getAssignments().size());
        assertThat(score.hardScore())
                .as("Hard score should be 0 (feasible) for 2-agent scenario")
                .isZero();
    }

    // ------------------------------------------------------------------
    //  Schedule builders
    // ------------------------------------------------------------------

    /**
     * 150-agent, 60-min timeslot scenario with demand matching exact supply.
     * Assignments start unassigned (deskAgent = null).
     */
    private Schedule buildUnassignedSchedule() {
        UUID deskId = UUID.randomUUID();
        UUID scheduleId = UUID.randomUUID();

        Specialization basic = spec(deskId, "Basic");

        // 150 agents
        List<DeskAgent> deskAgentList = new ArrayList<>(150);
        int id = 1;
        for (int i = 0; i < 150; i++) {
            Agent a = agent(String.valueOf(id), "Agent-" + id);
            DeskAgent da = deskAgent(deskId, a, basic, List.of(basic), CONTRACTED_HOURS);
            deskAgentList.add(da);
            id++;
        }

        // 9 timeslots: 09:00-18:00 in 60-min increments
        List<Timeslot> timeslots = new ArrayList<>();
        for (LocalTime t = START; t.isBefore(END); t = t.plusMinutes(INCREMENT)) {
            timeslots.add(timeslot(deskId, scheduleId, DAY, t, t.plusMinutes(INCREMENT)));
        }

        // Break assignment for demand calculation (same logic as FullScale150AgentTest)
        List<LocalTime> eligibleBreakHours = List.of(
                LocalTime.of(10, 0), LocalTime.of(11, 0),
                LocalTime.of(12, 0), LocalTime.of(13, 0), LocalTime.of(14, 0),
                LocalTime.of(15, 0), LocalTime.of(16, 0));

        int[] breaksPerSlot = new int[9];
        for (int i = 0; i < 150; i++) {
            LocalTime breakHour = eligibleBreakHours.get(i % eligibleBreakHours.size());
            int slotIndex = breakHour.getHour() - START.getHour();
            breaksPerSlot[slotIndex]++;
        }

        // Staffing requirements: demand = 150 - agents on break
        List<StaffingRequirement> staffingReqs = new ArrayList<>();
        for (int s = 0; s < timeslots.size(); s++) {
            int workingAgents = 150 - breaksPerSlot[s];
            if (workingAgents > 0) {
                StaffingRequirement sr = new StaffingRequirement();
                sr.setId(UUID.randomUUID());
                sr.setTenantId(TENANT);
                sr.setDeskId(deskId);
                sr.setScheduleId(scheduleId);
                sr.setTimeslot(timeslots.get(s));
                sr.setSpecialization(basic);
                sr.setRequiredHours(new BigDecimal(workingAgents));
                staffingReqs.add(sr);
            }
        }

        // Unassigned assignments (one per required agent per timeslot)
        List<AgentAssignment> assignments = new ArrayList<>(1200);
        for (StaffingRequirement sr : staffingReqs) {
            int requiredAgents = sr.getRequiredHours().intValue();
            for (int i = 0; i < requiredAgents; i++) {
                AgentAssignment aa = new AgentAssignment();
                aa.setId(UUID.randomUUID());
                aa.setTenantId(TENANT);
                aa.setDeskId(deskId);
                aa.setScheduleId(scheduleId);
                aa.setTimeslot(sr.getTimeslot());
                aa.setRequiredSpecialization(basic);
                // deskAgent = null (to be pre-assigned)
                assignments.add(aa);
            }
        }

        // AgentDayConfigs
        List<AgentDayConfig> dayConfigs = new ArrayList<>(150);
        for (DeskAgent da : deskAgentList) {
            dayConfigs.add(new AgentDayConfig(
                    da.getId(), DAY, CONTRACTED_HOURS,
                    INCREMENT, BREAK_DURATION,
                    BREAK_MIN_SHIFT, BREAK_BLOCKED,
                    BREAK_ALIGNMENT));
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
        schedule.setDeskAgents(deskAgentList);
        schedule.setTimeslots(timeslots);
        schedule.setStaffingRequirements(staffingReqs);
        schedule.setAgentPreferences(List.of());
        schedule.setAgentDaysOff(List.of());
        schedule.setAgentExceptions(List.of());
        schedule.setAgentDayConfigs(dayConfigs);
        schedule.setAssignments(assignments);

        return schedule;
    }

    /**
     * 2-agent, 15-min timeslot scenario (mirrors SingleDaySolvableTest structure).
     */
    private Schedule buildTwoAgentSchedule() {
        UUID deskId = UUID.randomUUID();
        UUID scheduleId = UUID.randomUUID();
        LocalTime start = LocalTime.of(8, 0);
        LocalTime end = LocalTime.of(17, 0);
        int increment = 15;

        Specialization billing = spec(deskId, "Billing");

        Agent agentA = agent("A-001", "Alice");
        Agent agentB = agent("B-002", "Bob");
        DeskAgent daA = deskAgent(deskId, agentA, billing, List.of(billing), new BigDecimal("8.00"));
        DeskAgent daB = deskAgent(deskId, agentB, billing, List.of(billing), new BigDecimal("8.00"));

        // 36 timeslots: 08:00-17:00 in 15-min increments
        List<Timeslot> timeslots = new ArrayList<>();
        for (LocalTime t = start; t.isBefore(end); t = t.plusMinutes(increment)) {
            timeslots.add(timeslot(deskId, scheduleId, DAY, t, t.plusMinutes(increment)));
        }

        // Demand: 2 agents for all timeslots except 12:00-13:00 (break window)
        LocalTime breakStart = LocalTime.of(12, 0);
        LocalTime breakEnd = LocalTime.of(13, 0);
        List<StaffingRequirement> staffingReqs = new ArrayList<>();
        List<AgentAssignment> assignments = new ArrayList<>();

        for (Timeslot ts : timeslots) {
            if (!ts.getStartTime().isBefore(breakStart) && ts.getStartTime().isBefore(breakEnd)) {
                continue;
            }
            StaffingRequirement sr = new StaffingRequirement();
            sr.setId(UUID.randomUUID());
            sr.setTenantId(TENANT);
            sr.setDeskId(deskId);
            sr.setScheduleId(scheduleId);
            sr.setTimeslot(ts);
            sr.setSpecialization(billing);
            sr.setRequiredHours(new BigDecimal("0.5000")); // 2 agents × 15 min
            staffingReqs.add(sr);

            // 2 unassigned seats per timeslot
            for (int i = 0; i < 2; i++) {
                AgentAssignment aa = new AgentAssignment();
                aa.setId(UUID.randomUUID());
                aa.setTenantId(TENANT);
                aa.setDeskId(deskId);
                aa.setScheduleId(scheduleId);
                aa.setTimeslot(ts);
                aa.setRequiredSpecialization(billing);
                assignments.add(aa);
            }
        }

        AgentDayConfig configA = new AgentDayConfig(
                daA.getId(), DAY, new BigDecimal("8.00"),
                increment, 60, new BigDecimal("4.00"), new BigDecimal("1.00"),
                BreakAlignment.ON_HOUR);
        AgentDayConfig configB = new AgentDayConfig(
                daB.getId(), DAY, new BigDecimal("8.00"),
                increment, 60, new BigDecimal("4.00"), new BigDecimal("1.00"),
                BreakAlignment.ON_HOUR);

        ConstraintWeights weights = new ConstraintWeights();
        weights.setId(UUID.randomUUID());
        weights.setTenantId(TENANT);
        weights.setDeskId(deskId);

        Schedule schedule = new Schedule();
        schedule.setId(scheduleId);
        schedule.setTenantId(TENANT);
        schedule.setDeskId(deskId);
        schedule.setIncrementMinutes(increment);
        schedule.setStartTime(start);
        schedule.setEndTime(end);
        schedule.setPeriodStartDate(DAY);
        schedule.setPeriodEndDate(DAY);
        schedule.setBreakBlockedHours(new BigDecimal("1.00"));
        schedule.setBreakDurationMinutes(60);
        schedule.setBreakMinShiftHours(new BigDecimal("4.00"));
        schedule.setBreakStartAlignment(BreakAlignment.ON_HOUR);
        schedule.setDefaultContractedHoursPerDay(new BigDecimal("8.00"));
        schedule.setOverallocationHardLimitPct(130);
        schedule.setUnderallocationHardLimitPct(70);
        schedule.setStatus(ScheduleStatus.RUNNING);

        schedule.setConstraintWeights(weights);
        schedule.setSpecializations(List.of(billing));
        schedule.setDeskAgents(List.of(daA, daB));
        schedule.setTimeslots(timeslots);
        schedule.setStaffingRequirements(staffingReqs);
        schedule.setAgentPreferences(List.of());
        schedule.setAgentDaysOff(List.of());
        schedule.setAgentExceptions(List.of());
        schedule.setAgentDayConfigs(List.of(configA, configB));
        schedule.setAssignments(assignments);

        return schedule;
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
