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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    /**
     * Tests the scenario that previously failed: 12-hour coverage window (08:00-20:00)
     * with 8-hour contracted agents. Each agent should work at most 8 slots, not 11
     * (which is 12 minus 1 break). The key assertion: no agent exceeds contracted hours.
     */
    @Test
    void preAssign_wideCoverageWindow_shouldRespectContractedHours() {
        Schedule schedule = buildWideCoverageSchedule();

        BreakAwareConstructionPhase phase = new BreakAwareConstructionPhase();
        int preAssigned = phase.preAssign(schedule);

        System.out.println("Wide-coverage pre-assigned: " + preAssigned + "/" + schedule.getAssignments().size());

        // Count assignments per agent
        Map<UUID, Integer> agentSlotCounts = new HashMap<>();
        for (AgentAssignment aa : schedule.getAssignments()) {
            if (aa.getDeskAgent() != null) {
                agentSlotCounts.merge(aa.getDeskAgent().getId(), 1, Integer::sum);
            }
        }

        // With 8-hour contracts and 60-min slots, max 8 assignments per agent
        int maxSlotsPerAgent = 8;
        List<Map.Entry<UUID, Integer>> overAssigned = agentSlotCounts.entrySet().stream()
                .filter(e -> e.getValue() > maxSlotsPerAgent)
                .toList();

        System.out.println("Agent slot counts — min: " +
                agentSlotCounts.values().stream().mapToInt(i -> i).min().orElse(0) +
                ", max: " + agentSlotCounts.values().stream().mapToInt(i -> i).max().orElse(0) +
                ", avg: " + String.format("%.1f",
                        agentSlotCounts.values().stream().mapToInt(i -> i).average().orElse(0)));

        if (!overAssigned.isEmpty()) {
            System.out.println("Over-assigned agents: " + overAssigned.size());
            overAssigned.stream().limit(5).forEach(e ->
                    System.out.println("  Agent " + e.getKey() + " => " + e.getValue() + " slots"));
        }

        // The key assertion: no agent exceeds their contracted hours
        assertThat(overAssigned)
                .as("No agent should exceed 8 slots (contracted hours) in a 12-hour window")
                .isEmpty();
    }

    /**
     * 30-agent, 9-hour coverage (09:00-18:00), 30-min increments, 8-hour contracts.
     * Each agent's 18-slot shift window = the full 18-slot day, so every agent
     * covers every slot (minus their 2-slot break). This tests break-aware
     * pre-assignment with 30-min granularity and scores the result.
     *
     * Break distribution: 7 eligible break positions (10:00-16:00), ~4 agents each.
     * Demand = 26 per slot × 18 slots = 468 seats. Supply = 30 × 16 = 480. Feasible.
     */
    @Test
    void preAssign_30min_wideCoverage_shouldProduceFeasibleSolution() {
        UUID deskId = UUID.randomUUID();
        UUID scheduleId = UUID.randomUUID();
        LocalTime start = LocalTime.of(9, 0);
        LocalTime end = LocalTime.of(18, 0);
        int increment = 30;
        BigDecimal contractedHours = new BigDecimal("8.00");
        int agentCount = 30;
        int demandPerSlot = 25; // max feasible: 30 agents - 5 on break at peak

        Specialization basic = spec(deskId, "Basic");
        Specialization second = spec(deskId, "second");

        List<DeskAgent> deskAgentList = new ArrayList<>(agentCount);
        for (int i = 0; i < agentCount; i++) {
            Agent a = agent(String.valueOf(i + 1), "Agent-" + (i + 1));
            DeskAgent da = deskAgent(deskId, a, basic, List.of(second), contractedHours);
            deskAgentList.add(da);
        }

        // 24 timeslots: 08:00-20:00 in 30-min increments
        List<Timeslot> timeslots = new ArrayList<>();
        for (LocalTime t = start; t.isBefore(end); t = t.plusMinutes(increment)) {
            timeslots.add(timeslot(deskId, scheduleId, DAY, t, t.plusMinutes(increment)));
        }

        List<StaffingRequirement> staffingReqs = new ArrayList<>();
        List<AgentAssignment> assignments = new ArrayList<>();

        for (Timeslot ts : timeslots) {
            StaffingRequirement sr = new StaffingRequirement();
            sr.setId(UUID.randomUUID());
            sr.setTenantId(TENANT);
            sr.setDeskId(deskId);
            sr.setScheduleId(scheduleId);
            sr.setTimeslot(ts);
            sr.setSpecialization(basic);
            sr.setRequiredHours(BigDecimal.valueOf(demandPerSlot)
                    .multiply(BigDecimal.valueOf(increment))
                    .divide(BigDecimal.valueOf(60), 4, java.math.RoundingMode.HALF_UP));
            staffingReqs.add(sr);

            for (int i = 0; i < demandPerSlot; i++) {
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

        List<AgentDayConfig> dayConfigs = new ArrayList<>(agentCount);
        for (DeskAgent da : deskAgentList) {
            dayConfigs.add(new AgentDayConfig(
                    da.getId(), DAY, contractedHours,
                    increment, BREAK_DURATION,
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
        schedule.setIncrementMinutes(increment);
        schedule.setStartTime(start);
        schedule.setEndTime(end);
        schedule.setPeriodStartDate(DAY);
        schedule.setPeriodEndDate(DAY);
        schedule.setBreakBlockedHours(BREAK_BLOCKED);
        schedule.setBreakDurationMinutes(BREAK_DURATION);
        schedule.setBreakMinShiftHours(BREAK_MIN_SHIFT);
        schedule.setBreakStartAlignment(BREAK_ALIGNMENT);
        schedule.setDefaultContractedHoursPerDay(contractedHours);
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

        // Pre-assign
        BreakAwareConstructionPhase phase = new BreakAwareConstructionPhase();
        int preAssigned = phase.preAssign(schedule);
        long unassigned = schedule.getAssignments().stream()
                .filter(a -> a.getDeskAgent() == null).count();

        System.out.println("30-min wide: pre-assigned=" + preAssigned + "/"
                + schedule.getAssignments().size() + ", unassigned=" + unassigned);

        // Per-agent counts
        Map<UUID, Integer> agentSlotCounts = new HashMap<>();
        for (AgentAssignment aa : schedule.getAssignments()) {
            if (aa.getDeskAgent() != null) {
                agentSlotCounts.merge(aa.getDeskAgent().getId(), 1, Integer::sum);
            }
        }
        System.out.println("Agent slot counts — min: " +
                agentSlotCounts.values().stream().mapToInt(i -> i).min().orElse(0) +
                ", max: " + agentSlotCounts.values().stream().mapToInt(i -> i).max().orElse(0) +
                ", avg: " + String.format("%.1f",
                        agentSlotCounts.values().stream().mapToInt(i -> i).average().orElse(0)));

        // Coverage per slot
        Map<LocalTime, Long> coveragePerSlot = schedule.getAssignments().stream()
                .filter(a -> a.getDeskAgent() != null)
                .collect(java.util.stream.Collectors.groupingBy(
                        a -> a.getTimeslot().getStartTime(),
                        java.util.stream.Collectors.counting()));
        System.out.println("Coverage per slot (demand=" + demandPerSlot + "):");
        coveragePerSlot.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> {
                    long deficit = demandPerSlot - e.getValue();
                    System.out.println("  " + e.getKey() + ": " + e.getValue()
                            + (deficit > 0 ? " (DEFICIT " + deficit + ")" : ""));
                });

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

        if (!score.equals(HardSoftScore.ZERO)) {
            var explanation = solutionManager.explain(schedule);
            System.out.println("=== Constraint Matches ===");
            explanation.getConstraintMatchTotalMap().forEach((name, total) -> {
                if (!total.getScore().equals(HardSoftScore.ZERO)) {
                    System.out.println("  " + name + " => " + total.getScore()
                            + " (matches: " + total.getConstraintMatchCount() + ")");
                }
            });
        }

        // With overflow seats, all 30 agents work their full 8 hours.
        // Total supply = 480 slots, demand = 450 slots, 480/450 = 106.7% < 130%.
        // No contracted hours deviations, no bulk allocation violations.
        assertThat(score.hardScore())
                .as("Hard score should be 0 — all agents work full hours within 130% limit")
                .isZero();
    }

    /**
     * Tests the scenario where the coverage window is wider than demand: timeslots
     * exist at times with no staffing requirements (no assignments). The construction
     * phase must still allocate agents their full contracted hours by extending windows
     * past non-demand slots.
     *
     * <p>Coverage: 07:00-20:00 (13 slots), Demand: 08:00-19:00 (11 slots),
     * 30 agents with 8h contracts. An agent whose best window starts at 07:00
     * would previously lose a work slot because 07:00 has no demand seats.
     */
    @Test
    void preAssign_coverageWiderThanDemand_shouldNotUnderallocate() {
        UUID deskId = UUID.randomUUID();
        UUID scheduleId = UUID.randomUUID();
        LocalTime start = LocalTime.of(7, 0);   // coverage starts at 07:00
        LocalTime end = LocalTime.of(20, 0);     // coverage ends at 20:00
        int increment = 60;
        BigDecimal contractedHours = new BigDecimal("8.00");
        int agentCount = 30;
        int demandPerSlot = 25;

        Specialization basic = spec(deskId, "Basic");
        Specialization second = spec(deskId, "second");

        List<DeskAgent> deskAgentList = new ArrayList<>(agentCount);
        for (int i = 0; i < agentCount; i++) {
            Agent a = agent(String.valueOf(i + 1), "Agent-" + (i + 1));
            DeskAgent da = deskAgent(deskId, a, basic, List.of(second), contractedHours);
            deskAgentList.add(da);
        }

        // 13 timeslots: 07:00-20:00 (full coverage window)
        List<Timeslot> timeslots = new ArrayList<>();
        for (LocalTime t = start; t.isBefore(end); t = t.plusMinutes(increment)) {
            timeslots.add(timeslot(deskId, scheduleId, DAY, t, t.plusMinutes(increment)));
        }

        // Demand only from 08:00-19:00 (11 slots) — 07:00 and 19:00 have NO demand
        List<StaffingRequirement> staffingReqs = new ArrayList<>();
        List<AgentAssignment> assignments = new ArrayList<>();
        LocalTime demandStart = LocalTime.of(8, 0);
        LocalTime demandEnd = LocalTime.of(19, 0);

        for (Timeslot ts : timeslots) {
            if (ts.getStartTime().isBefore(demandStart) || !ts.getStartTime().isBefore(demandEnd)) {
                continue; // no demand at 07:00 or 19:00
            }
            StaffingRequirement sr = new StaffingRequirement();
            sr.setId(UUID.randomUUID());
            sr.setTenantId(TENANT);
            sr.setDeskId(deskId);
            sr.setScheduleId(scheduleId);
            sr.setTimeslot(ts);
            sr.setSpecialization(basic);
            sr.setRequiredHours(BigDecimal.valueOf(demandPerSlot));
            staffingReqs.add(sr);

            for (int i = 0; i < demandPerSlot; i++) {
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

        List<AgentDayConfig> dayConfigs = new ArrayList<>(agentCount);
        for (DeskAgent da : deskAgentList) {
            dayConfigs.add(new AgentDayConfig(
                    da.getId(), DAY, contractedHours,
                    increment, BREAK_DURATION,
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
        schedule.setIncrementMinutes(increment);
        schedule.setStartTime(start);
        schedule.setEndTime(end);
        schedule.setPeriodStartDate(DAY);
        schedule.setPeriodEndDate(DAY);
        schedule.setBreakBlockedHours(BREAK_BLOCKED);
        schedule.setBreakDurationMinutes(BREAK_DURATION);
        schedule.setBreakMinShiftHours(BREAK_MIN_SHIFT);
        schedule.setBreakStartAlignment(BREAK_ALIGNMENT);
        schedule.setDefaultContractedHoursPerDay(contractedHours);
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

        // Pre-assign
        BreakAwareConstructionPhase phase = new BreakAwareConstructionPhase();
        int preAssigned = phase.preAssign(schedule);

        // Per-agent counts — every agent should have exactly 8 slots
        Map<UUID, Integer> agentSlotCounts = new HashMap<>();
        for (AgentAssignment aa : schedule.getAssignments()) {
            if (aa.getDeskAgent() != null) {
                agentSlotCounts.merge(aa.getDeskAgent().getId(), 1, Integer::sum);
            }
        }

        System.out.println("Coverage-wider-than-demand: pre-assigned=" + preAssigned
                + "/" + schedule.getAssignments().size());
        System.out.println("Agent slot counts — min: "
                + agentSlotCounts.values().stream().mapToInt(i -> i).min().orElse(0)
                + ", max: " + agentSlotCounts.values().stream().mapToInt(i -> i).max().orElse(0)
                + ", avg: " + String.format("%.1f",
                agentSlotCounts.values().stream().mapToInt(i -> i).average().orElse(0)));

        int underallocated = (int) agentSlotCounts.values().stream()
                .filter(c -> c < 8).count();
        System.out.println("Under-allocated agents: " + underallocated);

        // The key assertion: no agent should have fewer than 8 work slots
        assertThat(underallocated)
                .as("No agent should be underallocated when coverage is wider than demand")
                .isZero();

        // Score the solution
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
                    System.out.println("  " + name + " => " + total.getScore()
                            + " (matches: " + total.getConstraintMatchCount() + ")");
                }
            });
        }

        // Verify no contracted hours violations (the key assertion for underallocation).
        // Break geometry violations may remain in the construction phase output —
        // these are repaired by the solver's local search phase.
        var explanation = solutionManager.explain(schedule);
        var constraintMap = explanation.getConstraintMatchTotalMap();

        HardSoftScore contractedUnder = constraintMap.containsKey("com.wfm.model/Contracted hours (under)")
                ? constraintMap.get("com.wfm.model/Contracted hours (under)").getScore()
                : HardSoftScore.ZERO;
        HardSoftScore contractedUnderZero = constraintMap.containsKey("com.wfm.model/Contracted hours (under, zero)")
                ? constraintMap.get("com.wfm.model/Contracted hours (under, zero)").getScore()
                : HardSoftScore.ZERO;

        assertThat(contractedUnder)
                .as("No contracted hours (under) violations when coverage is wider than demand")
                .isEqualTo(HardSoftScore.ZERO);
        assertThat(contractedUnderZero)
                .as("No contracted hours (under, zero) violations when coverage is wider than demand")
                .isEqualTo(HardSoftScore.ZERO);
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
        Specialization second = spec(deskId, "second");

        // 150 agents
        List<DeskAgent> deskAgentList = new ArrayList<>(150);
        int id = 1;
        for (int i = 0; i < 150; i++) {
            Agent a = agent(String.valueOf(id), "Agent-" + id);
            DeskAgent da = deskAgent(deskId, a, basic, List.of(second), CONTRACTED_HOURS);
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
        schedule.setDayDemandConfigs(computeDayDemandConfigs(assignments));
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
        Specialization second = spec(deskId, "second");

        Agent agentA = agent("A-001", "Alice");
        Agent agentB = agent("B-002", "Bob");
        DeskAgent daA = deskAgent(deskId, agentA, billing, List.of(second), new BigDecimal("8.00"));
        DeskAgent daB = deskAgent(deskId, agentB, billing, List.of(second), new BigDecimal("8.00"));

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
        schedule.setDayDemandConfigs(computeDayDemandConfigs(assignments));
        schedule.setAssignments(assignments);

        return schedule;
    }

    /**
     * 150-agent, 12-hour coverage window (08:00-20:00) with 8-hour contracted hours.
     * Demand is 50 agents per timeslot = 600 total assignments.
     * The key scenario: coverage window (12h) > contracted hours (8h),
     * so agents must NOT be assigned to all 11 non-break slots.
     * With 150 × 8 = 1200 supply vs 600 demand, the scenario is clearly feasible
     * and any contracted hours violations indicate the phase isn't limiting properly.
     */
    private Schedule buildWideCoverageSchedule() {
        UUID deskId = UUID.randomUUID();
        UUID scheduleId = UUID.randomUUID();
        LocalTime start = LocalTime.of(8, 0);
        LocalTime end = LocalTime.of(20, 0);
        int increment = 60;
        BigDecimal contractedHours = new BigDecimal("8.00");

        Specialization basic = spec(deskId, "Basic");
        Specialization second = spec(deskId, "second");

        // 150 agents
        List<DeskAgent> deskAgentList = new ArrayList<>(150);
        for (int i = 0; i < 150; i++) {
            Agent a = agent(String.valueOf(i + 1), "Agent-" + (i + 1));
            DeskAgent da = deskAgent(deskId, a, basic, List.of(second), contractedHours);
            deskAgentList.add(da);
        }

        // 12 timeslots: 08:00-20:00 in 60-min increments
        List<Timeslot> timeslots = new ArrayList<>();
        for (LocalTime t = start; t.isBefore(end); t = t.plusMinutes(increment)) {
            timeslots.add(timeslot(deskId, scheduleId, DAY, t, t.plusMinutes(increment)));
        }

        // 50 agents demanded per timeslot = 600 total assignments
        int demandPerSlot = 50;
        List<StaffingRequirement> staffingReqs = new ArrayList<>();
        List<AgentAssignment> assignments = new ArrayList<>(demandPerSlot * 12);

        for (Timeslot ts : timeslots) {
            StaffingRequirement sr = new StaffingRequirement();
            sr.setId(UUID.randomUUID());
            sr.setTenantId(TENANT);
            sr.setDeskId(deskId);
            sr.setScheduleId(scheduleId);
            sr.setTimeslot(ts);
            sr.setSpecialization(basic);
            sr.setRequiredHours(new BigDecimal(demandPerSlot));
            staffingReqs.add(sr);

            for (int i = 0; i < demandPerSlot; i++) {
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

        // AgentDayConfigs — 8-hour contracts, 60-min break, 1hr blocked window
        List<AgentDayConfig> dayConfigs = new ArrayList<>(150);
        for (DeskAgent da : deskAgentList) {
            dayConfigs.add(new AgentDayConfig(
                    da.getId(), DAY, contractedHours,
                    increment, BREAK_DURATION,
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
        schedule.setIncrementMinutes(increment);
        schedule.setStartTime(start);
        schedule.setEndTime(end);
        schedule.setPeriodStartDate(DAY);
        schedule.setPeriodEndDate(DAY);
        schedule.setBreakBlockedHours(BREAK_BLOCKED);
        schedule.setBreakDurationMinutes(BREAK_DURATION);
        schedule.setBreakMinShiftHours(BREAK_MIN_SHIFT);
        schedule.setBreakStartAlignment(BREAK_ALIGNMENT);
        schedule.setDefaultContractedHoursPerDay(contractedHours);
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

    /**
     * Computes DayDemandConfig from assignment list (must be called BEFORE construction phase).
     */
    private List<DayDemandConfig> computeDayDemandConfigs(List<AgentAssignment> assignments) {
        Map<LocalDate, Integer> demandPerDay = new HashMap<>();
        for (AgentAssignment a : assignments) {
            demandPerDay.merge(a.getTimeslot().getDate(), 1, Integer::sum);
        }
        List<DayDemandConfig> configs = new ArrayList<>();
        for (Map.Entry<LocalDate, Integer> e : demandPerDay.entrySet()) {
            configs.add(new DayDemandConfig(e.getKey(), e.getValue()));
        }
        return configs;
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
