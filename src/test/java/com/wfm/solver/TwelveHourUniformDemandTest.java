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
 * Verifies feasibility for a 12-hour coverage window with uniform 60 agents/slot demand.
 *
 * <h3>Scenario</h3>
 * <ul>
 *   <li>1 day: 2026-03-10 (Tuesday)</li>
 *   <li>Coverage window: 08:00 – 20:00 (12 hrs, 12 × 60-min timeslots)</li>
 *   <li>1 specialization: "Support" (all primary, 0 secondary)</li>
 *   <li>120 agents, each contracted 8 hrs → 8 work slots + 1 break slot</li>
 *   <li>Break: 60 min, blocked first/last 1 hr, ON_HOUR alignment</li>
 *   <li>Demand: 60 agents in every slot (720 total demand seats)</li>
 *   <li>Over-allocation limit: 134% (to accommodate 960/720 = 133.3%)</li>
 *   <li>Under-allocation limit: 70%</li>
 * </ul>
 *
 * <h3>Agent distribution</h3>
 * <ul>
 *   <li>60 "early" agents: shift 08:00-17:00, break at 11:00 (slot 3)</li>
 *   <li>60 "late" agents: shift 11:00-20:00, break at 12:00 (slot 4)</li>
 * </ul>
 *
 * <h3>Per-slot coverage</h3>
 * <pre>
 * Slot  Time   Early(60)  Late(60)  Working  Demand  Overflow
 *  0    08:00    60          0         60      60       0
 *  1    09:00    60          0         60      60       0
 *  2    10:00    60          0         60      60       0
 *  3    11:00   BREAK       60         60      60       0
 *  4    12:00    60        BREAK       60      60       0
 *  5    13:00    60         60        120      60      60
 *  6    14:00    60         60        120      60      60
 *  7    15:00    60         60        120      60      60
 *  8    16:00    60         60        120      60      60
 *  9    17:00     0         60         60      60       0
 * 10    18:00     0         60         60      60       0
 * 11    19:00     0         60         60      60       0
 * </pre>
 *
 * <p>Total assignments = 720 demand + 240 overflow = 960 = 120 agents × 8 slots.
 * Each agent works exactly 8 slots (contracted hours). Each has exactly one 60-min
 * break at an ON_HOUR position outside the blocked zone. Over-allocation = 133.3% &lt; 134%.
 */
class TwelveHourUniformDemandTest {

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

    private static final int AGENTS_PER_SHIFT = 60;
    private static final int DEMAND_PER_SLOT = 60;

    // Early shift: 08:00-17:00, break at 11:00 (slot 3)
    private static final LocalTime EARLY_START = LocalTime.of(8, 0);
    private static final LocalTime EARLY_END = LocalTime.of(17, 0);
    private static final LocalTime EARLY_BREAK = LocalTime.of(11, 0);

    // Late shift: 11:00-20:00, break at 12:00 (slot 4)
    private static final LocalTime LATE_START = LocalTime.of(11, 0);
    private static final LocalTime LATE_END = LocalTime.of(20, 0);
    private static final LocalTime LATE_BREAK = LocalTime.of(12, 0);

    @Test
    void twelveHour_120agents_uniformDemand60_allPrimary_shouldScoreZeroHard() {
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

        // Print per-agent assignment counts
        Map<UUID, Integer> agentSlotCounts = new HashMap<>();
        for (AgentAssignment aa : solution.getAssignments()) {
            if (aa.getDeskAgent() != null) {
                agentSlotCounts.merge(aa.getDeskAgent().getId(), 1, Integer::sum);
            }
        }
        System.out.println("Agent slot counts — min: " +
                agentSlotCounts.values().stream().mapToInt(i -> i).min().orElse(0) +
                ", max: " + agentSlotCounts.values().stream().mapToInt(i -> i).max().orElse(0));

        // Print per-slot coverage
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

        // Hard score must be 0 (fully feasible)
        assertThat(score.hardScore())
                .as("Hard score should be 0 (feasible) — 120 agents, 12h, uniform 60/slot")
                .isZero();

        // Soft score should be 0 — all primary spec, no preferences
        assertThat(score.softScore())
                .as("Soft score should be 0 (optimal) — all primary, no preferences")
                .isZero();
    }

    // ------------------------------------------------------------------
    //  Pre-assigned solution builder
    // ------------------------------------------------------------------

    private Schedule buildPreAssignedSolution() {
        UUID deskId = UUID.randomUUID();
        UUID scheduleId = UUID.randomUUID();

        // --- Single specialization (all primary, no secondary) ---
        Specialization support = spec(deskId, "Support");

        // --- 120 agents: 60 early + 60 late ---
        List<DeskAgent> deskAgents = new ArrayList<>(120);
        List<Agent> agents = new ArrayList<>(120);
        for (int i = 0; i < 120; i++) {
            String label = (i < AGENTS_PER_SHIFT) ? "Early" : "Late";
            Agent a = agent(String.valueOf(i + 1), label + "-Agent-" + (i + 1));
            DeskAgent da = deskAgent(deskId, a, support, CONTRACTED_HOURS);
            agents.add(a);
            deskAgents.add(da);
        }

        // --- 12 timeslots: 08:00–20:00 ---
        List<Timeslot> timeslots = new ArrayList<>();
        for (LocalTime t = START; t.isBefore(END); t = t.plusMinutes(INCREMENT)) {
            timeslots.add(timeslot(deskId, scheduleId, DAY, t, t.plusMinutes(INCREMENT)));
        }

        // Index timeslots by start time for lookup
        Map<LocalTime, Timeslot> timeslotByStart = new HashMap<>();
        for (Timeslot ts : timeslots) {
            timeslotByStart.put(ts.getStartTime(), ts);
        }

        // --- Staffing Requirements: 60 agents per slot ---
        List<StaffingRequirement> staffingReqs = new ArrayList<>();
        for (Timeslot ts : timeslots) {
            StaffingRequirement sr = new StaffingRequirement();
            sr.setId(UUID.randomUUID());
            sr.setTenantId(TENANT);
            sr.setDeskId(deskId);
            sr.setScheduleId(scheduleId);
            sr.setTimeslot(ts);
            sr.setSpecialization(support);
            sr.setRequiredHours(new BigDecimal(DEMAND_PER_SLOT)); // 60 agents × 1 hr
            staffingReqs.add(sr);
        }

        // --- Pre-assigned AgentAssignments ---
        // Early agents (0-59): work 08-17 except break at 11:00
        //   demand seats at 08,09,10,12,13,14,15,16
        // Late agents (60-119): work 11-20 except break at 12:00
        //   demand seats at 11,17,18,19 + overflow at 13,14,15,16

        List<AgentAssignment> assignments = new ArrayList<>(960);

        // Track demand seat consumption per slot
        Map<LocalTime, Integer> demandSeatsUsed = new HashMap<>();

        // Assign early agents (indices 0-59)
        for (int i = 0; i < AGENTS_PER_SHIFT; i++) {
            DeskAgent da = deskAgents.get(i);
            Agent a = agents.get(i);
            for (LocalTime t = EARLY_START; t.isBefore(EARLY_END); t = t.plusMinutes(INCREMENT)) {
                if (t.equals(EARLY_BREAK)) continue; // break at 11:00
                Timeslot ts = timeslotByStart.get(t);
                assignments.add(assignment(deskId, scheduleId, ts, support, da, a));
                demandSeatsUsed.merge(t, 1, Integer::sum);
            }
        }

        // Assign late agents (indices 60-119)
        for (int i = AGENTS_PER_SHIFT; i < 2 * AGENTS_PER_SHIFT; i++) {
            DeskAgent da = deskAgents.get(i);
            Agent a = agents.get(i);
            for (LocalTime t = LATE_START; t.isBefore(LATE_END); t = t.plusMinutes(INCREMENT)) {
                if (t.equals(LATE_BREAK)) continue; // break at 12:00
                Timeslot ts = timeslotByStart.get(t);
                assignments.add(assignment(deskId, scheduleId, ts, support, da, a));
            }
        }

        // --- AgentDayConfigs ---
        List<AgentDayConfig> dayConfigs = new ArrayList<>(120);
        for (DeskAgent da : deskAgents) {
            dayConfigs.add(new AgentDayConfig(
                    da.getId(), DAY, CONTRACTED_HOURS,
                    INCREMENT, BREAK_DURATION,
                    BREAK_MIN_SHIFT, BREAK_BLOCKED,
                    BREAK_ALIGNMENT, 130, 70));
        }

        // --- DayDemandConfig: based on DEMAND seats only (720) ---
        // The bulk allocation constraint compares total assigned (960) vs demand (720).
        // 960/720 = 133.3% which is within 134% limit.
        List<DayDemandConfig> dayDemandConfigs = List.of(
                new DayDemandConfig(DAY, DEMAND_PER_SLOT * timeslots.size())); // 720

        // --- Constraint Weights (all defaults) ---
        ConstraintWeights weights = new ConstraintWeights();
        weights.setId(UUID.randomUUID());
        weights.setTenantId(TENANT);
        weights.setDeskId(deskId);

        // --- Assemble Schedule ---
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
        schedule.setOverallocationHardLimitPct(134);
        schedule.setUnderallocationHardLimitPct(70);
        schedule.setStatus(ScheduleStatus.RUNNING);

        schedule.setConstraintWeights(weights);
        schedule.setSpecializations(List.of(support));
        schedule.setDeskAgents(deskAgents);
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

    // ------------------------------------------------------------------
    //  Factory helpers
    // ------------------------------------------------------------------

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
                                BigDecimal contractedHours) {
        DeskAgent da = new DeskAgent();
        da.setId(UUID.randomUUID());
        da.setTenantId(TENANT);
        da.setDeskId(deskId);
        da.setAgent(agent);
        da.setPrimarySpecialization(primary);
        da.setSecondarySpecializations(new ArrayList<>()); // no secondary
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
