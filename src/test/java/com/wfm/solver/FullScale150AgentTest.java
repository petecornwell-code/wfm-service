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
 * Full-scale feasibility test with 150 agents drawn from the mock BambooHR
 * Vinted data set, 1 specialization, 60-minute timeslots, single day.
 *
 * <h3>Scenario</h3>
 * <ul>
 *   <li>1 day: 2026-03-16 (Monday)</li>
 *   <li>Coverage window: 09:00 – 18:00 (9 hours, 9 × 60-min timeslots)</li>
 *   <li>1 specialization: "Basic"</li>
 *   <li>150 agents, each contracted 8 hrs/day → 8 work slots + 1 break slot per agent</li>
 *   <li>Break: 60 min, blocked first/last 1 hr, ON_HOUR alignment</li>
 *   <li>Eligible break hours: 10:00, 11:00, 12:00, 13:00, 14:00, 15:00, 16:00 (7 options)</li>
 *   <li>Break distribution: round-robin across 7 hours → groups of 21-22 agents per break hour</li>
 *   <li>Demand per timeslot: exactly matches the number of working agents in that slot</li>
 *   <li>Total assignments: 150 × 8 = 1200</li>
 * </ul>
 *
 * <h3>Why this is feasible</h3>
 * <p>Each agent is pre-assigned to exactly 8 of 9 timeslots (their contracted hours).
 * Each has exactly one 60-min break at their assigned break hour — outside the blocked
 * first/last-hour zone, starting on the hour. Demand per timeslot is set to the exact
 * number of agents working that timeslot (150 minus agents on break), so staffing
 * requirements are perfectly met with no over/under-allocation.
 */
class FullScale150AgentTest {

    private static final long TENANT = 1L;
    private static final LocalDate DAY = LocalDate.of(2026, 3, 16); // Monday
    private static final LocalTime START = LocalTime.of(9, 0);
    private static final LocalTime END = LocalTime.of(18, 0);
    private static final int INCREMENT = 60; // 60-minute timeslots

    // Break parameters
    private static final int BREAK_DURATION = 60;
    private static final BigDecimal BREAK_BLOCKED = new BigDecimal("1.00");
    private static final BigDecimal BREAK_MIN_SHIFT = new BigDecimal("4.00");
    private static final BreakAlignment BREAK_ALIGNMENT = BreakAlignment.ON_HOUR;
    private static final BigDecimal CONTRACTED_HOURS = new BigDecimal("8.00");

    // Mock BambooHR names (matches MockBambooHRClient.buildVintedAgents)
    private static final String[] FIRST_NAMES = {
        "Olivia", "Liam", "Emma", "Noah", "Ava", "Elijah", "Sophia", "James",
        "Isabella", "William", "Mia", "Benjamin", "Charlotte", "Lucas", "Amelia",
        "Henry", "Harper", "Alexander", "Evelyn", "Sebastian", "Luna", "Jack",
        "Ella", "Daniel", "Scarlett", "Michael", "Grace", "Owen", "Chloe", "Samuel",
        "Penelope", "David", "Layla", "Joseph", "Riley", "Carter", "Zoey", "Wyatt",
        "Nora", "John", "Lily", "Luke", "Eleanor", "Gabriel", "Hannah", "Anthony",
        "Lillian", "Isaac", "Addison", "Dylan", "Aubrey", "Leo", "Ellie", "Lincoln",
        "Stella", "Jaxon", "Natalie", "Asher", "Zoe", "Christopher", "Leah", "Josiah",
        "Hazel", "Andrew", "Violet", "Thomas", "Aurora", "Joshua", "Savannah", "Ezra",
        "Audrey", "Adrian", "Brooklyn", "Charles", "Bella", "Caleb", "Claire", "Ryan",
        "Skylar", "Nathan", "Lucy", "Eli", "Paisley", "Matthew", "Everly", "Connor",
        "Anna", "Aaron", "Caroline", "Landon", "Nova", "Jonathan", "Genesis", "Nolan",
        "Emilia", "Hunter", "Kennedy", "Cameron", "Samantha", "Miles", "Maya"
    };

    private static final String[] LAST_NAMES = {
        "Smith", "Johnson", "Williams", "Brown", "Jones", "Garcia", "Miller", "Davis",
        "Rodriguez", "Martinez", "Hernandez", "Lopez", "Gonzalez", "Wilson", "Anderson",
        "Thomas", "Taylor", "Moore", "Jackson", "Martin", "Lee", "Perez", "Thompson",
        "White", "Harris", "Sanchez", "Clark", "Ramirez", "Lewis", "Robinson",
        "Walker", "Young", "Allen", "King", "Wright", "Scott", "Torres", "Nguyen",
        "Hill", "Flores", "Green", "Adams", "Nelson", "Baker", "Hall", "Rivera",
        "Campbell", "Mitchell", "Carter", "Roberts"
    };

    @Test
    void fullScale_150agents_preAssigned_shouldScoreZeroHard() {
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
        System.out.println("Staffing requirements: " + solution.getStaffingRequirements().size());

        if (!score.equals(HardSoftScore.ZERO)) {
            var explanation = solutionManager.explain(solution);
            System.out.println("=== Constraint Matches ===");
            explanation.getConstraintMatchTotalMap().forEach((name, total) -> {
                if (!total.getScore().equals(HardSoftScore.ZERO)) {
                    System.out.println("  " + name + " => " + total.getScore());
                }
            });
        }

        assertThat(score.hardScore())
                .as("Hard score should be 0 (feasible) with 150 agents")
                .isZero();
    }

    // ------------------------------------------------------------------
    //  Pre-assigned solution builder
    // ------------------------------------------------------------------

    private Schedule buildPreAssignedSolution() {
        UUID deskId = UUID.randomUUID();
        UUID scheduleId = UUID.randomUUID();

        // --- Specialization ---
        Specialization basic = spec(deskId, "Basic");
        Specialization second = spec(deskId, "second");

        // --- Build 150 agents matching mock BambooHR name pattern ---
        List<Agent> agentList = new ArrayList<>(150);
        List<DeskAgent> deskAgentList = new ArrayList<>(150);
        int id = 1;
        outer:
        for (String firstName : FIRST_NAMES) {
            for (String lastName : LAST_NAMES) {
                if (agentList.size() >= 150) break outer;
                String name = firstName + " " + lastName;
                Agent a = agent(String.valueOf(id), name);
                DeskAgent da = deskAgent(deskId, a, basic, List.of(second), CONTRACTED_HOURS);
                agentList.add(a);
                deskAgentList.add(da);
                id++;
            }
        }

        // --- Timeslots: 08:00–18:00 in 60-min increments (10 slots) ---
        List<Timeslot> timeslots = new ArrayList<>();
        for (LocalTime t = START; t.isBefore(END); t = t.plusMinutes(INCREMENT)) {
            timeslots.add(timeslot(deskId, scheduleId, DAY, t, t.plusMinutes(INCREMENT)));
        }

        // --- Break assignment: round-robin across eligible break hours ---
        // Eligible break hours: 10:00, 11:00, 12:00, 13:00, 14:00, 15:00, 16:00
        // (not 09:00 blocked-first-hour, not 17:00 blocked-last-hour)
        List<LocalTime> eligibleBreakHours = List.of(
                LocalTime.of(10, 0), LocalTime.of(11, 0),
                LocalTime.of(12, 0), LocalTime.of(13, 0), LocalTime.of(14, 0),
                LocalTime.of(15, 0), LocalTime.of(16, 0));

        // Assign each agent a break hour: agent i gets break at eligibleBreakHours[i % 8]
        LocalTime[] agentBreakHour = new LocalTime[150];
        // Count how many agents break in each timeslot (to compute demand)
        int[] breaksPerSlot = new int[9]; // index 0 = 09:00, 1 = 10:00, ..., 8 = 17:00
        for (int i = 0; i < 150; i++) {
            LocalTime breakHour = eligibleBreakHours.get(i % eligibleBreakHours.size());
            agentBreakHour[i] = breakHour;
            int slotIndex = breakHour.getHour() - START.getHour();
            breaksPerSlot[slotIndex]++;
        }

        // --- Staffing Requirements: demand = working agents per timeslot ---
        // In each timeslot, demand = 150 - breaksPerSlot[slotIndex] agents × 1 hour
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
                // requiredHours = workingAgents × 1 hour (each agent fills 1 hour in a 60-min slot)
                sr.setRequiredHours(new BigDecimal(workingAgents));
                staffingReqs.add(sr);
            }
        }

        // --- Pre-assigned AgentAssignments ---
        // Each agent is assigned to every timeslot EXCEPT their break hour.
        // This gives each agent exactly 8 assignments × 60 min = 8 hrs = contracted hours.
        List<AgentAssignment> assignments = new ArrayList<>(1200);
        for (int i = 0; i < 150; i++) {
            DeskAgent da = deskAgentList.get(i);
            Agent a = agentList.get(i);
            for (Timeslot ts : timeslots) {
                if (ts.getStartTime().equals(agentBreakHour[i])) {
                    continue; // this is the agent's break hour — skip
                }
                assignments.add(assignment(deskId, scheduleId, ts, basic, da, a));
            }
        }

        // --- AgentDayConfig (one per agent) ---
        List<AgentDayConfig> dayConfigs = new ArrayList<>(150);
        for (DeskAgent da : deskAgentList) {
            dayConfigs.add(new AgentDayConfig(
                    da.getId(), DAY, CONTRACTED_HOURS,
                    INCREMENT, BREAK_DURATION,
                    BREAK_MIN_SHIFT, BREAK_BLOCKED,
                    BREAK_ALIGNMENT, 130, 70));
        }

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
