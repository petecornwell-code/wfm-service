package com.wfm.solver;

import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import ai.timefold.solver.core.config.heuristic.selector.move.composite.UnionMoveSelectorConfig;
import ai.timefold.solver.core.config.heuristic.selector.move.factory.MoveListFactoryConfig;
import ai.timefold.solver.core.config.heuristic.selector.move.generic.ChangeMoveSelectorConfig;
import ai.timefold.solver.core.config.heuristic.selector.move.generic.SwapMoveSelectorConfig;
import ai.timefold.solver.core.config.localsearch.LocalSearchPhaseConfig;
import ai.timefold.solver.core.config.score.director.ScoreDirectorFactoryConfig;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;
import com.wfm.model.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Controlled A/B benchmark comparing the existing change/swap move pool
 * ("baseline") against the same pool plus {@link AtomicShiftMoveFactory}
 * ("with-move"), five seeded runs per configuration, on one checked-in
 * fixture reproducing the ROADMAP Phase 12 scenario (11 agents, 88 hours
 * needed, 15-minute increments, 8h contracted, 60-minute break, 1h blocked
 * window, 400% over-allocation).
 *
 * <p>Gated behind {@code -Dwfm.benchmark=true} (see {@code build.gradle}'s
 * {@code wfm.benchmark} passthrough) so {@code ./gradlew test} never runs it
 * by default — a controlled 5x5 comparison is orders of magnitude slower
 * than the rest of the suite and is a development/evidence-gathering tool,
 * not a regression gate.
 *
 * <p>Both configurations terminate on {@link TerminationConfig#withStepCountLimit(Integer)}
 * (available at Timefold 1.16.0, confirmed directly against the compiled
 * class) rather than wall-clock time, and both fix
 * {@link SolverConfig#withRandomSeed(Long)} — this is the controlled-variance
 * design 12-RESEARCH.md's Validation Architecture calls for: production
 * {@code SolverService} termination stays wall-clock (untouched by this
 * class), and step-count termination is used here only for this harness.
 *
 * <p>Emits only aggregate metrics and synthetic fixture identifiers
 * (agent bamboohrIds like {@code BENCH-1}) — no live desk data, real agent
 * names/emails or BambooHR IDs are ever read or printed, since this
 * harness's output is destined for a tracked planning document in a public
 * repository (12-BENCHMARK.md).
 */
class AtomicShiftMoveBenchmarkTest {

    private static final long TENANT = 1L;
    private static final LocalDate DAY = LocalDate.of(2026, 3, 16);
    private static final LocalTime START = LocalTime.of(8, 0);
    private static final LocalTime END = LocalTime.of(20, 0);
    private static final int INCREMENT = 15;
    private static final int AGENT_COUNT = 11;
    private static final BigDecimal CONTRACTED_HOURS = new BigDecimal("8.00");
    private static final int BREAK_DURATION = 60;
    private static final BigDecimal BREAK_BLOCKED = new BigDecimal("1.00");
    private static final BigDecimal BREAK_MIN_SHIFT = new BigDecimal("4.00");
    private static final BreakAlignment BREAK_ALIGNMENT = BreakAlignment.ON_QUARTER_HOUR;
    private static final int DEMAND_FTE_PER_SLOT = 2;
    private static final int OVERALLOCATION_PCT_REFERENCE = 400;
    private static final int OVERALLOCATION_PCT_CONSERVATIVE = 130; // Schedule class's own default
    private static final int UNDERALLOCATION_PCT = 70;

    private static final int STEP_COUNT_LIMIT = 2000;
    private static final long[] SEEDS = {1L, 2L, 3L, 4L, 5L};

    /**
     * A single benchmark run's outcome. {@code hoursNeeded} is carried per
     * row (rather than assumed constant) so the conservative-variant run
     * remains self-describing if the fixture ever changes.
     */
    record RunMetrics(
            String config,
            long seed,
            int hardScore,
            int softScore,
            BigDecimal hoursAssigned,
            BigDecimal hoursNeeded,
            int agentDaysNeedingBreakWithNone,
            int agentDaysAtPinnedWall,
            long elapsedMillis) {}

    private record AgentDateKey(UUID agentId, LocalDate date) {}

    // ------------------------------------------------------------------
    //  Benchmark scenarios
    // ------------------------------------------------------------------

    @Test
    @EnabledIfSystemProperty(named = "wfm.benchmark", matches = "true")
    void benchmark_400pctOverallocation_referenceScenario_fiveSeededRunsEachConfig() {
        System.out.println();
        System.out.println("=== 400% Over-allocation Reference Scenario (phase-gating) ===");
        printHarnessConfig(OVERALLOCATION_PCT_REFERENCE);

        List<RunMetrics> baselineRuns = runFiveSeeds("baseline", OVERALLOCATION_PCT_REFERENCE, false);
        List<RunMetrics> withMoveRuns = runFiveSeeds("with-move", OVERALLOCATION_PCT_REFERENCE, true);

        printPerRunTable(baselineRuns, withMoveRuns);
        printSummaryTable(baselineRuns, withMoveRuns);

        // The only must-pass threshold that is deterministically checkable
        // in code. The median-versus-spread comparison and the hard-score
        // should-pass item are operator judgement — see 12-03-PLAN.md Task 3.
        assertThat(withMoveRuns)
                .as("all five with-move runs must report zero agent-days needing a break with none")
                .allSatisfy(m -> assertThat(m.agentDaysNeedingBreakWithNone())
                        .as("with-move run seed=%d", m.seed())
                        .isZero());
    }

    @Test
    @EnabledIfSystemProperty(named = "wfm.benchmark", matches = "true")
    void benchmark_130pctOverallocation_conservativeVariant_informationalOnly() {
        System.out.println();
        System.out.println("=== INFORMATIONAL: 130% Over-allocation Conservative Variant (not phase-gating) ===");
        printHarnessConfig(OVERALLOCATION_PCT_CONSERVATIVE);

        List<RunMetrics> baselineRuns = runFiveSeeds("baseline", OVERALLOCATION_PCT_CONSERVATIVE, false);
        List<RunMetrics> withMoveRuns = runFiveSeeds("with-move", OVERALLOCATION_PCT_CONSERVATIVE, true);

        printPerRunTable(baselineRuns, withMoveRuns);
        printSummaryTable(baselineRuns, withMoveRuns);

        // Informational only — 12-RESEARCH.md Open Question 2. No assertion:
        // the phase's stated thresholds are defined against the 400% scenario.
    }

    // ------------------------------------------------------------------
    //  Run orchestration
    // ------------------------------------------------------------------

    private List<RunMetrics> runFiveSeeds(String configLabel, int overallocationPct, boolean withAtomicMove) {
        List<RunMetrics> runs = new ArrayList<>(SEEDS.length);
        for (long seed : SEEDS) {
            Schedule schedule = buildFixture(overallocationPct);
            SolverConfig config = withAtomicMove ? withMoveConfig(seed) : baselineConfig(seed);

            long startMillis = System.currentTimeMillis();
            Schedule solved = solve(schedule, config);
            long elapsedMillis = System.currentTimeMillis() - startMillis;

            runs.add(computeMetrics(configLabel, seed, solved, elapsedMillis));
        }
        return runs;
    }

    private Schedule solve(Schedule schedule, SolverConfig config) {
        SolverFactory<Schedule> factory = SolverFactory.create(config);
        return factory.buildSolver().solve(schedule);
    }

    /**
     * The baseline solver config: construction heuristic + local search whose
     * move selector is the union of change and swap only — exactly what
     * Timefold auto-builds today when {@code solverConfig.xml} declares no
     * explicit {@code moveSelector}. Fixed seed, step-count termination.
     */
    private SolverConfig baselineConfig(long seed) {
        return new SolverConfig()
                .withSolutionClass(Schedule.class)
                .withEntityClasses(AgentAssignment.class)
                .withRandomSeed(seed)
                .withScoreDirectorFactory(new ScoreDirectorFactoryConfig()
                        .withConstraintProviderClass(ScheduleConstraintProvider.class))
                .withPhases(
                        new ConstructionHeuristicPhaseConfig(),
                        new LocalSearchPhaseConfig()
                                .withMoveSelectorConfig(new UnionMoveSelectorConfig().withMoveSelectors(
                                        new ChangeMoveSelectorConfig(),
                                        new SwapMoveSelectorConfig()))
                                .withTerminationConfig(new TerminationConfig()
                                        .withStepCountLimit(STEP_COUNT_LIMIT)));
    }

    /**
     * Identical to {@link #baselineConfig(long)} except for one appended
     * {@link MoveListFactoryConfig} registering {@link AtomicShiftMoveFactory}
     * — every other call in the chain is the same, so the comparison
     * isolates exactly one variable.
     */
    private SolverConfig withMoveConfig(long seed) {
        return new SolverConfig()
                .withSolutionClass(Schedule.class)
                .withEntityClasses(AgentAssignment.class)
                .withRandomSeed(seed)
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
                                        .withStepCountLimit(STEP_COUNT_LIMIT)));
    }

    // ------------------------------------------------------------------
    //  Metrics
    // ------------------------------------------------------------------

    private RunMetrics computeMetrics(String configLabel, long seed, Schedule solved, long elapsedMillis) {
        Map<UUID, AgentDayConfig> dayConfigByAgent = solved.getAgentDayConfigs().stream()
                .collect(Collectors.toMap(AgentDayConfig::agentId, dc -> dc, (a, b) -> a));

        Map<AgentDateKey, List<AgentAssignment>> byAgentDate = solved.getAssignments().stream()
                .filter(a -> a.getAgent() != null)
                .collect(Collectors.groupingBy(
                        a -> new AgentDateKey(a.getAgent().getId(), a.getTimeslot().getDate())));

        int needingBreakWithNone = 0;
        int atPinnedWall = 0;
        long assignedSeats = 0;

        for (Map.Entry<AgentDateKey, List<AgentAssignment>> entry : byAgentDate.entrySet()) {
            List<AgentAssignment> assignments = entry.getValue();
            assignedSeats += assignments.size();

            AgentDayConfig dayConfig = dayConfigByAgent.get(entry.getKey().agentId());
            if (dayConfig == null) continue;

            boolean needsBreak = dayConfig.effectiveHours().compareTo(dayConfig.breakMinShiftHours()) > 0;
            int gapCount = countContiguousGaps(assignments, dayConfig.incrementMinutes());
            if (needsBreak && gapCount == 0) {
                needingBreakWithNone++;
            }

            int breakThresholdSlots = ShiftWindowFinder.breakThresholdSlots(dayConfig);
            if (assignments.size() == breakThresholdSlots - 1) {
                atPinnedWall++;
            }
        }

        BigDecimal hoursAssigned = BigDecimal.valueOf(assignedSeats)
                .multiply(BigDecimal.valueOf(solved.getIncrementMinutes()))
                .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
        BigDecimal hoursNeeded = CONTRACTED_HOURS.multiply(BigDecimal.valueOf(AGENT_COUNT));

        return new RunMetrics(
                configLabel, seed,
                solved.getScore().hardScore(), solved.getScore().softScore(),
                hoursAssigned, hoursNeeded,
                needingBreakWithNone, atPinnedWall,
                elapsedMillis);
    }

    /**
     * Verbatim copy of {@code ScheduleConstraintProvider.getGapLengths}'
     * {@link TreeSet}-walk arithmetic (that method is private, so this
     * metric must reuse the identical algorithm rather than call it) so the
     * benchmark's "needs a break with none" count agrees exactly with what
     * the {@code exactlyOneBreak} constraint itself would penalize.
     */
    private static int countContiguousGaps(List<AgentAssignment> assignments, int incrementMinutes) {
        if (assignments == null || assignments.isEmpty()) return 0;

        TreeSet<LocalTime> assignedStarts = new TreeSet<>();
        for (AgentAssignment a : assignments) {
            assignedStarts.add(a.getTimeslot().getStartTime());
        }
        if (assignedStarts.isEmpty()) return 0;

        LocalTime shiftStart = assignedStarts.first();
        LocalTime shiftEnd = assignedStarts.last().plusMinutes(incrementMinutes);

        int gapCount = 0;
        int currentGap = 0;
        for (LocalTime t = shiftStart; t.isBefore(shiftEnd); t = t.plusMinutes(incrementMinutes)) {
            if (!assignedStarts.contains(t)) {
                currentGap++;
            } else if (currentGap > 0) {
                gapCount++;
                currentGap = 0;
            }
        }
        if (currentGap > 0) {
            gapCount++;
        }
        return gapCount;
    }

    // ------------------------------------------------------------------
    //  Reporting (markdown tables printed to stdout, captured by Task 2)
    // ------------------------------------------------------------------

    private void printHarnessConfig(int overallocationPct) {
        int timeslotCount = (int) Duration.between(START, END).toMinutes() / INCREMENT;
        int seatsPerSlot = (int) Math.ceil(DEMAND_FTE_PER_SLOT * overallocationPct / 100.0);
        int totalSeats = seatsPerSlot * timeslotCount;
        BigDecimal hoursNeeded = CONTRACTED_HOURS.multiply(BigDecimal.valueOf(AGENT_COUNT));

        System.out.println("Harness configuration: agentCount=" + AGENT_COUNT
                + ", incrementMinutes=" + INCREMENT
                + ", contractedHoursPerDay=" + CONTRACTED_HOURS
                + ", breakDurationMinutes=" + BREAK_DURATION
                + ", breakBlockedHours=" + BREAK_BLOCKED
                + ", breakStartAlignment=" + BREAK_ALIGNMENT
                + ", overallocationHardLimitPct=" + overallocationPct
                + ", timeslots=" + timeslotCount
                + ", seatsPerTimeslot=" + seatsPerSlot
                + ", totalSeats=" + totalSeats
                + ", hoursNeeded=" + hoursNeeded
                + ", timefoldVersion=1.16.0"
                + ", termination=stepCountLimit(" + STEP_COUNT_LIMIT + ")"
                + ", seeds=" + Arrays.toString(SEEDS));
    }

    private void printPerRunTable(List<RunMetrics> baselineRuns, List<RunMetrics> withMoveRuns) {
        System.out.println();
        System.out.println("Per-run results:");
        System.out.println("| config | seed | hardScore | softScore | hoursAssigned | hoursNeeded | "
                + "agentDaysNeedingBreakWithNone | agentDaysAtPinnedWall | elapsedMillis |");
        System.out.println("|---|---|---|---|---|---|---|---|---|");
        for (RunMetrics m : baselineRuns) printRow(m);
        for (RunMetrics m : withMoveRuns) printRow(m);
    }

    private void printRow(RunMetrics m) {
        System.out.println("| " + m.config() + " | " + m.seed() + " | " + m.hardScore() + " | " + m.softScore()
                + " | " + m.hoursAssigned() + " | " + m.hoursNeeded() + " | " + m.agentDaysNeedingBreakWithNone()
                + " | " + m.agentDaysAtPinnedWall() + " | " + m.elapsedMillis() + " |");
    }

    private void printSummaryTable(List<RunMetrics> baselineRuns, List<RunMetrics> withMoveRuns) {
        System.out.println();
        System.out.println("Summary (median and full min/max spread, never a mean):");
        System.out.println("| config | hardScore median | hardScore min | hardScore max | "
                + "hoursAssigned median | hoursAssigned min | hoursAssigned max |");
        System.out.println("|---|---|---|---|---|---|---|");
        printSummaryRow("baseline", baselineRuns);
        printSummaryRow("with-move", withMoveRuns);
    }

    private void printSummaryRow(String label, List<RunMetrics> runs) {
        List<Double> hardScores = runs.stream().map(m -> (double) m.hardScore()).sorted().toList();
        List<Double> hoursAssigned = runs.stream().map(m -> m.hoursAssigned().doubleValue()).sorted().toList();

        System.out.println("| " + label
                + " | " + median(hardScores) + " | " + hardScores.get(0) + " | " + hardScores.get(hardScores.size() - 1)
                + " | " + median(hoursAssigned) + " | " + hoursAssigned.get(0)
                + " | " + hoursAssigned.get(hoursAssigned.size() - 1) + " |");
    }

    private static double median(List<Double> sortedAscending) {
        int n = sortedAscending.size();
        if (n == 0) throw new IllegalStateException("cannot compute median of an empty run set");
        if (n % 2 == 1) return sortedAscending.get(n / 2);
        return (sortedAscending.get(n / 2 - 1) + sortedAscending.get(n / 2)) / 2.0;
    }

    // ------------------------------------------------------------------
    //  Fixture builder — checked-in, deterministic, synthetic-only
    // ------------------------------------------------------------------

    /**
     * Builds the ROADMAP reproduction scenario: {@link #AGENT_COUNT} agents,
     * one day, {@link #START}-{@link #END}, {@link #INCREMENT}-minute
     * increments, {@link #CONTRACTED_HOURS} contracted, {@link #BREAK_DURATION}-minute
     * break, {@code overallocationPct} over-allocation. Every field is a
     * synthetic identifier (e.g. {@code BENCH-1}) — no live desk data.
     */
    private Schedule buildFixture(int overallocationPct) {
        UUID deskId = UUID.randomUUID();
        UUID scheduleId = UUID.randomUUID();

        Specialization basic = spec(deskId, "Benchmark Specialization");

        List<Agent> agentList = new ArrayList<>(AGENT_COUNT);
        for (int i = 0; i < AGENT_COUNT; i++) {
            agentList.add(agent(deskId, "BENCH-" + (i + 1), basic));
        }

        List<Timeslot> timeslots = new ArrayList<>();
        for (LocalTime t = START; t.isBefore(END); t = t.plusMinutes(INCREMENT)) {
            timeslots.add(timeslot(deskId, scheduleId, DAY, t, t.plusMinutes(INCREMENT)));
        }

        List<StaffingRequirement> staffingReqs = new ArrayList<>();
        for (Timeslot ts : timeslots) {
            StaffingRequirement sr = new StaffingRequirement();
            sr.setId(UUID.randomUUID());
            sr.setTenantId(TENANT);
            sr.setDeskId(deskId);
            sr.setScheduleId(scheduleId);
            sr.setTimeslot(ts);
            sr.setSpecialization(basic);
            sr.setRequiredFTEs(DEMAND_FTE_PER_SLOT);
            staffingReqs.add(sr);
        }

        List<AgentAssignment> assignments = expandAssignments(deskId, scheduleId, staffingReqs);
        assignments.addAll(expandOverflowAssignments(deskId, scheduleId, staffingReqs, overallocationPct));

        List<AgentDayConfig> dayConfigs = new ArrayList<>(AGENT_COUNT);
        for (Agent a : agentList) {
            dayConfigs.add(new AgentDayConfig(
                    a.getId(), DAY, CONTRACTED_HOURS, INCREMENT, BREAK_DURATION,
                    BREAK_MIN_SHIFT, BREAK_BLOCKED, BREAK_ALIGNMENT,
                    overallocationPct, UNDERALLOCATION_PCT));
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
        schedule.setOverallocationHardLimitPct(overallocationPct);
        schedule.setUnderallocationHardLimitPct(UNDERALLOCATION_PCT);
        schedule.setStatus(ScheduleStatus.RUNNING);

        schedule.setConstraintWeights(weights);
        schedule.setSpecializations(List.of(basic));
        schedule.setAgents(agentList);
        schedule.setTimeslots(timeslots);
        schedule.setStaffingRequirements(staffingReqs);
        schedule.setAgentPreferences(List.of());
        schedule.setAgentDaysOff(List.of());
        schedule.setAgentExceptions(List.of());
        schedule.setAgentDayConfigs(dayConfigs);
        schedule.setTimeslotDemandConfigs(computeTimeslotDemandConfigs(staffingReqs));
        schedule.setAssignments(assignments);

        return schedule;
    }

    /** Mirrors {@code SolverService.expandAssignments} (lines 875-894, private there). */
    private List<AgentAssignment> expandAssignments(
            UUID deskId, UUID scheduleId, List<StaffingRequirement> staffingRequirements) {
        List<AgentAssignment> assignments = new ArrayList<>();
        for (StaffingRequirement sr : staffingRequirements) {
            for (int i = 0; i < sr.getRequiredFTEs(); i++) {
                assignments.add(seat(deskId, scheduleId, sr.getTimeslot(), sr.getSpecialization()));
            }
        }
        return assignments;
    }

    /**
     * Mirrors {@code SolverService.expandOverflowAssignments} (lines 903-927,
     * private there) exactly, including its {@code (required * pct + 99) / 100}
     * ceiling-division shape, so the fixture's seat headroom matches
     * production seat-generation semantics at 400% (8 seats/timeslot) and
     * 130% (3 seats/timeslot).
     */
    private List<AgentAssignment> expandOverflowAssignments(
            UUID deskId, UUID scheduleId, List<StaffingRequirement> staffingRequirements,
            int overallocationHardLimitPct) {
        if (overallocationHardLimitPct <= 100) return List.of();

        List<AgentAssignment> overflow = new ArrayList<>();
        for (StaffingRequirement sr : staffingRequirements) {
            int requiredAgents = sr.getRequiredFTEs();
            int maxAgents = (requiredAgents * overallocationHardLimitPct + 99) / 100;
            int overflowAgents = maxAgents - requiredAgents;
            for (int i = 0; i < overflowAgents; i++) {
                overflow.add(seat(deskId, scheduleId, sr.getTimeslot(), sr.getSpecialization()));
            }
        }
        return overflow;
    }

    private AgentAssignment seat(UUID deskId, UUID scheduleId, Timeslot ts, Specialization spec) {
        AgentAssignment aa = new AgentAssignment();
        aa.setId(UUID.randomUUID());
        aa.setTenantId(TENANT);
        aa.setDeskId(deskId);
        aa.setScheduleId(scheduleId);
        aa.setTimeslot(ts);
        aa.setRequiredSpecialization(spec);
        return aa;
    }

    private List<TimeslotDemandConfig> computeTimeslotDemandConfigs(List<StaffingRequirement> staffingRequirements) {
        List<TimeslotDemandConfig> configs = new ArrayList<>();
        for (StaffingRequirement sr : staffingRequirements) {
            configs.add(new TimeslotDemandConfig(sr.getTimeslot(), sr.getRequiredFTEs()));
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

    private Agent agent(UUID deskId, String syntheticBambooId, Specialization primary) {
        Agent a = new Agent();
        a.setId(UUID.randomUUID());
        a.setTenantId(TENANT);
        a.setBamboohrId(syntheticBambooId);
        a.setName("Synthetic Agent");
        a.setActive(true);
        a.setDeskId(deskId);
        a.setPrimarySpecialization(primary);
        a.setSecondarySpecializations(new ArrayList<>());
        a.setContractedHoursPerDay(CONTRACTED_HOURS);
        return a;
    }

    private Timeslot timeslot(UUID deskId, UUID scheduleId, LocalDate date, LocalTime start, LocalTime end) {
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
