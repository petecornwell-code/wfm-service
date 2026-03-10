package com.wfm.solver;

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.score.stream.ConstraintStreamImplType;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.api.solver.SolverManager;
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
 * Verifies that a carefully constructed single-day scenario scores as feasible
 * (hard score == 0) when all constraints are evaluated.
 *
 * <h3>Scenario</h3>
 * <ul>
 *   <li>1 day: 2026-03-10 (Tuesday)</li>
 *   <li>Coverage window: 08:00 – 17:00 (9 hrs, 36 × 15-min timeslots)</li>
 *   <li>1 specialization: "Billing"</li>
 *   <li>2 agents (Alice, Bob), each contracted 8 hrs → 32 work slots</li>
 *   <li>Break: 60 min (4 slots), blocked first/last 1 hr, ON_HOUR alignment</li>
 *   <li>Demand: 2 agents in all slots EXCEPT 12:00–13:00 (break window, 0 demand)</li>
 *   <li>Pre-assigned: both agents work 08:00–11:45 and 13:00–16:45, break at 12:00</li>
 * </ul>
 *
 * <h3>Why this is feasible</h3>
 * <p>Each agent works 32 of 36 timeslots with one 4-slot break at 12:00–13:00.
 * Total assignments = 64 = 2 × 32. Break geometry satisfies every hard constraint:
 * exactly 1 gap, 4 slots long, starts ON_HOUR, outside the 1-hr blocked zone.
 *
 * <p>Uses {@code SolutionManager.update()} to score the pre-built solution —
 * no solver search needed, no Spring context.
 */
class SingleDaySolvableTest {

    private static final long TENANT = 1L;
    private static final LocalDate DAY = LocalDate.of(2026, 3, 10);
    private static final LocalTime START = LocalTime.of(8, 0);
    private static final LocalTime END = LocalTime.of(17, 0);
    private static final int INCREMENT = 15;

    // Break window: 12:00–13:00 — no demand, agents break here
    private static final LocalTime BREAK_START = LocalTime.of(12, 0);
    private static final LocalTime BREAK_END = LocalTime.of(13, 0);

    @Test
    void singleDay_twoAgents_preAssigned_shouldScoreFeasible() {
        Schedule solution = buildPreAssignedSolution();

        // Build a minimal SolverFactory just for scoring (no phases needed)
        SolverFactory<Schedule> solverFactory = SolverFactory.create(
                new SolverConfig()
                        .withSolutionClass(Schedule.class)
                        .withEntityClasses(AgentAssignment.class)
                        .withScoreDirectorFactory(new ScoreDirectorFactoryConfig()
                                .withConstraintProviderClass(ScheduleConstraintProvider.class)));

        // Score the pre-built solution
        var solutionManager = ai.timefold.solver.core.api.solver.SolutionManager
                .<Schedule, HardSoftScore>create(solverFactory);
        HardSoftScore score = solutionManager.update(solution);

        System.out.println("Score: " + score);

        // Print constraint breakdown if score is non-zero (aids debugging)
        if (!score.equals(HardSoftScore.ZERO)) {
            var explanation = solutionManager.explain(solution);
            System.out.println("=== Constraint Matches ===");
            explanation.getConstraintMatchTotalMap().forEach((name, total) -> {
                if (!total.getScore().equals(HardSoftScore.ZERO)) {
                    System.out.println("  " + name + " => " + total.getScore());
                }
            });
        }

        // Hard score must be 0 (fully feasible)
        assertThat(score.hardScore())
                .as("Hard score should be 0 (feasible)")
                .isZero();

        // Soft score 0 means no soft penalties either (ideal assignment)
        assertThat(score.softScore())
                .as("Soft score should be 0 (optimal)")
                .isZero();
    }

    // ------------------------------------------------------------------
    //  Pre-assigned solution builder
    // ------------------------------------------------------------------

    private Schedule buildPreAssignedSolution() {
        UUID deskId = UUID.randomUUID();
        UUID scheduleId = UUID.randomUUID();

        // --- Specialization ---
        Specialization billing = spec(deskId, "Billing");
        Specialization second = spec(deskId, "second");

        // --- Agents + DeskAgents ---
        Agent agentA = agent("A-001", "Alice");
        Agent agentB = agent("B-002", "Bob");

        DeskAgent daA = deskAgent(deskId, agentA, billing, List.of(second), new BigDecimal("8.00"));
        DeskAgent daB = deskAgent(deskId, agentB, billing, List.of(second), new BigDecimal("8.00"));

        List<DeskAgent> deskAgents = List.of(daA, daB);

        // --- Timeslots: 08:00–17:00 in 15-min increments (36 slots) ---
        List<Timeslot> timeslots = new ArrayList<>();
        for (LocalTime t = START; t.isBefore(END); t = t.plusMinutes(INCREMENT)) {
            timeslots.add(timeslot(deskId, scheduleId, DAY, t, t.plusMinutes(INCREMENT)));
        }

        // --- Staffing Requirements + pre-assigned AgentAssignments ---
        //     Demand: 2 agents for "Billing" in each non-break timeslot.
        //     32 timeslots × 2 seats = 64 assignments = 2 × 32 work slots.
        List<StaffingRequirement> staffingReqs = new ArrayList<>();
        List<AgentAssignment> assignments = new ArrayList<>();

        for (Timeslot ts : timeslots) {
            if (!ts.getStartTime().isBefore(BREAK_START) && ts.getStartTime().isBefore(BREAK_END)) {
                continue; // skip 12:00–12:45 (break window, no demand)
            }

            // Staffing requirement: 0.50 hrs = 2 agents × 15 min
            StaffingRequirement sr = new StaffingRequirement();
            sr.setId(UUID.randomUUID());
            sr.setTenantId(TENANT);
            sr.setDeskId(deskId);
            sr.setScheduleId(scheduleId);
            sr.setTimeslot(ts);
            sr.setSpecialization(billing);
            sr.setRequiredHours(new BigDecimal("0.5000"));
            staffingReqs.add(sr);

            // Seat 1 → Alice
            assignments.add(assignment(deskId, scheduleId, ts, billing, daA, agentA));
            // Seat 2 → Bob
            assignments.add(assignment(deskId, scheduleId, ts, billing, daB, agentB));
        }

        // --- AgentDayConfig (pre-computed, mirrors SolverService logic) ---
        AgentDayConfig configA = new AgentDayConfig(
                daA.getId(), DAY, new BigDecimal("8.00"),
                INCREMENT, 60,
                new BigDecimal("4.00"), new BigDecimal("1.00"),
                BreakAlignment.ON_HOUR);
        AgentDayConfig configB = new AgentDayConfig(
                daB.getId(), DAY, new BigDecimal("8.00"),
                INCREMENT, 60,
                new BigDecimal("4.00"), new BigDecimal("1.00"),
                BreakAlignment.ON_HOUR);

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
        schedule.setDeskAgents(deskAgents);
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
