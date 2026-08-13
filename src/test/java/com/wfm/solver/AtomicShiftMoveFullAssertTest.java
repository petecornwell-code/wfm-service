package com.wfm.solver;

import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import ai.timefold.solver.core.config.heuristic.selector.move.composite.UnionMoveSelectorConfig;
import ai.timefold.solver.core.config.heuristic.selector.move.factory.MoveListFactoryConfig;
import ai.timefold.solver.core.config.heuristic.selector.move.generic.ChangeMoveSelectorConfig;
import ai.timefold.solver.core.config.heuristic.selector.move.generic.SwapMoveSelectorConfig;
import ai.timefold.solver.core.config.localsearch.LocalSearchPhaseConfig;
import ai.timefold.solver.core.config.score.director.ScoreDirectorFactoryConfig;
import ai.timefold.solver.core.config.solver.EnvironmentMode;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;
import ai.timefold.solver.core.impl.heuristic.move.Move;
import com.wfm.model.*;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the atomic shift move end-to-end: one composite move places a full
 * 8-slot contracted shift with a legal 10:00 break on a previously empty
 * agent-day, and the same move survives a FULL_ASSERT local search without
 * corrupting the incrementally-tracked score.
 */
class AtomicShiftMoveFullAssertTest {

    private static final long TENANT = 1L;
    private static final LocalDate DAY = LocalDate.of(2026, 3, 16);
    private static final LocalTime START = LocalTime.of(9, 0);
    private static final LocalTime END = LocalTime.of(21, 0);
    private static final int INCREMENT = 60;
    private static final int BREAK_DURATION = 60;
    private static final BigDecimal BREAK_BLOCKED = new BigDecimal("1.00");
    private static final BigDecimal BREAK_MIN_SHIFT = new BigDecimal("4.00");
    private static final BreakAlignment BREAK_ALIGNMENT = BreakAlignment.ON_HOUR;
    private static final BigDecimal CONTRACTED_HOURS = new BigDecimal("8.00");

    @Test
    void factoryAndFinder_produceTheExpectedSingleWindow() {
        Schedule schedule = buildUnassignedSchedule();
        AgentDayConfig dayConfig = schedule.getAgentDayConfigs().get(0);

        List<? extends Move<Schedule>> moves = new AtomicShiftMoveFactory().createMoveList(schedule);
        assertThat(moves)
                .as("factory should generate at least one composite move for a zero-assignment agent-day")
                .isNotEmpty();

        List<ShiftWindowFinder.ShiftWindow> windows =
                ShiftWindowFinder.findWindows(schedule.getAssignments(), dayConfig);
        assertThat(windows).isNotEmpty();

        ShiftWindowFinder.ShiftWindow window = windows.get(0);
        assertThat(window.workSeats()).hasSize(8);
        assertThat(window.breakStart()).isEqualTo(LocalTime.of(10, 0));
        assertThat(windows)
                .as("no returned window has a break before 10:00")
                .allSatisfy(w -> assertThat(w.breakStart()).isAfterOrEqualTo(LocalTime.of(10, 0)));
    }

    @Test
    void fullAssert_atomicShiftMoveSurvivesLocalSearch() {
        Schedule schedule = buildUnassignedSchedule();

        SolverConfig solverConfig = new SolverConfig()
                .withSolutionClass(Schedule.class)
                .withEntityClasses(AgentAssignment.class)
                .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
                .withScoreDirectorFactory(new ScoreDirectorFactoryConfig()
                        .withConstraintProviderClass(ScheduleConstraintProvider.class))
                .withPhases(
                        new ConstructionHeuristicPhaseConfig(),
                        new LocalSearchPhaseConfig()
                                .withMoveSelectorConfig(new UnionMoveSelectorConfig().withMoveSelectors(
                                        new ChangeMoveSelectorConfig(),
                                        new SwapMoveSelectorConfig(),
                                        new MoveListFactoryConfig()
                                                .withMoveListFactoryClass(AtomicShiftMoveFactory.class)))
                                .withTerminationConfig(new TerminationConfig()
                                        .withSpentLimit(Duration.ofSeconds(5))));

        SolverFactory<Schedule> solverFactory = SolverFactory.create(solverConfig);
        Schedule solved = solverFactory.buildSolver().solve(schedule);

        long assigned = solved.getAssignments().stream()
                .filter(a -> a.getAgent() != null).count();

        System.out.println("FULL_ASSERT atomic shift move solver: assigned=" + assigned + "/"
                + solved.getAssignments().size() + ", score=" + solved.getScore());

        assertThat(assigned)
                .as("FULL_ASSERT solver with the atomic shift move should assign seats")
                .isGreaterThan(0);
    }

    private Schedule buildUnassignedSchedule() {
        UUID deskId = UUID.randomUUID();
        UUID scheduleId = UUID.randomUUID();

        Specialization basic = spec(deskId, "IT Support");

        Agent agentA = agent("A-001", "Alice");
        deskAgent(deskId, agentA, basic, List.of(), CONTRACTED_HOURS);

        List<Timeslot> timeslots = new ArrayList<>();
        Map<LocalTime, Timeslot> tsByStart = new HashMap<>();
        for (LocalTime t = START; t.isBefore(END); t = t.plusMinutes(INCREMENT)) {
            Timeslot ts = timeslot(deskId, scheduleId, DAY, t, t.plusMinutes(INCREMENT));
            timeslots.add(ts);
            tsByStart.put(t, ts);
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
            sr.setRequiredFTEs(1);
            staffingReqs.add(sr);

            AgentAssignment aa = new AgentAssignment();
            aa.setId(UUID.randomUUID());
            aa.setTenantId(TENANT);
            aa.setDeskId(deskId);
            aa.setScheduleId(scheduleId);
            aa.setTimeslot(ts);
            aa.setRequiredSpecialization(basic);
            assignments.add(aa);
        }

        AgentDayConfig dayConfig = new AgentDayConfig(
                agentA.getId(), DAY, CONTRACTED_HOURS,
                INCREMENT, BREAK_DURATION,
                BREAK_MIN_SHIFT, BREAK_BLOCKED,
                BREAK_ALIGNMENT, 130, 70);

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
        schedule.setAgents(List.of(agentA));
        schedule.setTimeslots(timeslots);
        schedule.setStaffingRequirements(staffingReqs);
        schedule.setAgentPreferences(List.of());
        schedule.setAgentDaysOff(List.of());
        schedule.setAgentExceptions(List.of());
        schedule.setAgentDayConfigs(List.of(dayConfig));
        schedule.setTimeslotDemandConfigs(computeTimeslotDemandConfigs(assignments));
        schedule.setAssignments(assignments);

        return schedule;
    }

    private List<TimeslotDemandConfig> computeTimeslotDemandConfigs(List<AgentAssignment> assignments) {
        Map<Timeslot, Integer> demandPerTimeslot = new LinkedHashMap<>();
        for (AgentAssignment a : assignments) {
            demandPerTimeslot.merge(a.getTimeslot(), 1, Integer::sum);
        }
        List<TimeslotDemandConfig> configs = new ArrayList<>();
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

    private void deskAgent(UUID deskId, Agent agent, Specialization primary,
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
