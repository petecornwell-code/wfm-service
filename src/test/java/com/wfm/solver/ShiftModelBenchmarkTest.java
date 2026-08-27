package com.wfm.solver;

import ai.timefold.solver.core.api.solver.Solver;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import ai.timefold.solver.core.config.constructionheuristic.placer.QueuedEntityPlacerConfig;
import ai.timefold.solver.core.config.heuristic.selector.entity.EntitySelectorConfig;
import ai.timefold.solver.core.config.phase.PhaseConfig;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;
import com.wfm.model.AgentAssignment;
import com.wfm.model.AgentShiftAssignment;
import com.wfm.model.Schedule;
import com.wfm.model.StaffingRequirement;
import com.wfm.model.Timeslot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * XCUT-04 — the seeded, step-count-terminated A/B benchmark measuring the shift model's effect on
 * schedule quality against the slot model, honestly, at ~130% over-allocation (D-16), plus D-08's
 * construction-heuristic-ordering arm. This is why the harness exists: Phase 12 produced a +0.25h
 * median hours-assigned improvement inside a 5.00h min/max noise spread, read it as a win, and the
 * phase was withdrawn (see {@code 12-BENCHMARK.md}). This harness's noise rule — any difference
 * smaller than the slot arm's OWN min/max spread is "no measurable difference", never a win or a
 * loss — is that lesson encoded as a rule rather than relearned. The pass rule itself lives in
 * {@code 15-BENCHMARK.md}, committed in a commit that precedes any recorded result (P-36).
 *
 * <p>Recovered and adapted from {@code AtomicShiftMoveBenchmarkTest} (deleted at commit
 * {@code 299c42c}, retrievable at {@code 299c42c^}), keeping its proven mechanics: system-property
 * gated so it never runs in the default suite, {@code SolverConfig.withRandomSeed} per run,
 * {@code TerminationConfig.withStepCountLimit} for termination (never wall-clock, never
 * {@code withSpentLimit}), fixed seeds 1 through 5, and a print-a-markdown-table-of-per-run-rows
 * output style whose numbers {@code 15-BENCHMARK.md} transcribes verbatim.
 *
 * <p>Every fixture is built through the real {@code solverConfig.xml} (P-18, the same convention
 * {@code ShiftEnvelopeGroundTruthTest} established) — never a hand-built {@code SolverConfig} — so
 * the benchmark exercises the shipped two-phase construction heuristic exactly as production would.
 * {@code StepCountTermination} is phase-scoped only at Timefold 1.16.0 (a solver-level
 * {@code TerminationConfig} throws {@code UnsupportedOperationException}, per 15-04's own
 * discovery), so it is attached to the trailing local-search phase.
 *
 * <p>The shift and slot arms are built from {@link ShiftModeFixtures} (15-04) so this benchmark and
 * the ENVL correctness tests agree on what a shift-mode schedule is (P-19) — the two arms differ
 * ONLY in the desk's scheduling mode and the presence of a shift library, never in demand, roster,
 * grid or weights. {@link ShiftModeFixtures#OVERALLOCATION_PCT} is already fixed at 130 — the
 * realistic figure XCUT-04 and D-16 name — so no separate re-parameterisation is needed.
 *
 * <p>D-08's third arm (seats-first construction-heuristic ordering) is built from the SAME loaded
 * {@code solverConfig.xml} resource with its phase list reordered in place (P-35), never from a
 * second XML file that could drift from the shipped one — see {@link #loadSolverConfig}.
 */
class ShiftModelBenchmarkTest {

    private static final long[] SEEDS = {1L, 2L, 3L, 4L, 5L};
    private static final int STEP_COUNT_LIMIT = 1000;

    // Comparative-fixture scale — small enough that 3 arms x 5 seeds (15 solves) stays bounded when
    // this benchmark is actually invoked; the indicative real-desk-scale run below is separate and
    // deliberately larger (D-16).
    private static final int AGENT_COUNT = 4;
    private static final int DAY_COUNT = 2;
    private static final int TEMPLATE_COUNT = 2;

    // Indicative real-desk-scale run (D-16, non-comparative) — 30 agents matches the scale
    // SPIKE-COUPLING.md's open item 1 names ("real problem size: 30 agents ... 19 existing
    // constraints", now 21 after plan 15-06). Single seed, larger step budget: reported for scale
    // only, never a pass/fail criterion.
    private static final int INDICATIVE_AGENT_COUNT = 30;
    private static final int INDICATIVE_DAY_COUNT = 1;
    private static final int INDICATIVE_TEMPLATE_COUNT = 3;
    private static final int INDICATIVE_STEP_COUNT_LIMIT = 3000;
    private static final long INDICATIVE_SEED = 1L;

    /** One benchmark run's outcome — model-independent metrics (D-14) plus soft score, reported but
     * never thresholded, since the two arms do not evaluate the same constraint set. */
    record RunMetrics(
            String arm,
            long seed,
            int hardScore,
            int softScore,
            int unstaffedDemandSlots,
            BigDecimal hoursAssigned,
            BigDecimal hoursNeeded,
            long elapsedMillis) {}

    private record TimeslotKey(LocalDate date, LocalTime start) {}

    // ------------------------------------------------------------------
    //  Task 1 — wiring check: one seed of each of the two mode arms, not recorded as a result
    // ------------------------------------------------------------------

    @Test
    @EnabledIfSystemProperty(named = "wfm.benchmark", matches = "true")
    void wiringCheck_bothModeArmsProduceACompleteMetricRowForOneSeed() {
        System.out.println();
        System.out.println("=== WIRING CHECK — one seed per mode arm (NOT recorded as a benchmark result) ===");

        RunMetrics slot = runOnce("slot", buildSlotSchedule(), loadSolverConfig(1L, false), 1L, AGENT_COUNT, DAY_COUNT);
        RunMetrics shift = runOnce("shift-shifts-first", buildShiftSchedule(), loadSolverConfig(1L, false), 1L, AGENT_COUNT, DAY_COUNT);

        printPerRunTable(List.of(slot, shift));

        assertThat(slot).as("slot arm must print a complete metric row").isNotNull();
        assertThat(shift).as("shift arm must print a complete metric row").isNotNull();
    }

    // ------------------------------------------------------------------
    //  Task 2 — the full run: three arms, five seeds, plus one indicative real-desk-scale run
    // ------------------------------------------------------------------

    @Test
    @EnabledIfSystemProperty(named = "wfm.benchmark", matches = "true")
    void fullRun_threeArmsFiveSeeds_plusOneIndicativeRealDeskScaleRun() {
        System.out.println();
        System.out.println("=== XCUT-04 FULL RUN — slot vs shift(shifts-first) vs shift(seats-first, D-08), 5 seeds ===");
        System.out.println("No fixture, seed, or step-budget value is adjusted after this point (D-15/P-36).");

        List<RunMetrics> slotRuns = runSeeds("slot", this::buildSlotSchedule, false, AGENT_COUNT, DAY_COUNT);
        List<RunMetrics> shiftsFirstRuns = runSeeds("shift-shifts-first", this::buildShiftSchedule, false, AGENT_COUNT, DAY_COUNT);
        List<RunMetrics> seatsFirstRuns = runSeeds("shift-seats-first", this::buildShiftSchedule, true, AGENT_COUNT, DAY_COUNT);

        List<RunMetrics> all = new ArrayList<>();
        all.addAll(slotRuns);
        all.addAll(shiftsFirstRuns);
        all.addAll(seatsFirstRuns);
        printPerRunTable(all);
        printSummaryTable(List.of(slotRuns, shiftsFirstRuns, seatsFirstRuns));

        // Must-pass, deterministic per D-15: the shift arm reaches 0hard on every seed, under BOTH
        // CH orderings — the only assertion this test enforces in code. The comparative
        // median-vs-spread reading and the CH-ordering decision are operator/write-up judgement
        // against the pass rule already committed in 15-BENCHMARK.md, not something this test
        // adjudicates (mirrors 12-03's own division of labour between code and record).
        assertThat(shiftsFirstRuns)
                .as("shift-shifts-first must reach 0hard on every one of the 5 seeds")
                .allSatisfy(m -> assertThat(m.hardScore()).as("seed=%d", m.seed()).isZero());
        assertThat(seatsFirstRuns)
                .as("shift-seats-first must reach 0hard on every one of the 5 seeds")
                .allSatisfy(m -> assertThat(m.hardScore()).as("seed=%d", m.seed()).isZero());

        System.out.println();
        System.out.println("=== INDICATIVE ONLY (D-16) — one real-desk-scale run, non-comparative, no pass/fail ===");
        RunMetrics indicative = runIndicativeRealDeskScaleRun();
        printPerRunTable(List.of(indicative));
    }

    private RunMetrics runIndicativeRealDeskScaleRun() {
        Schedule schedule = ShiftModeFixtures.buildShiftModeSchedule(
                INDICATIVE_AGENT_COUNT, INDICATIVE_DAY_COUNT, INDICATIVE_TEMPLATE_COUNT, 1).schedule();
        SolverConfig config = loadSolverConfig(INDICATIVE_SEED, false);
        List<PhaseConfig> phases = config.getPhaseConfigList();
        phases.get(phases.size() - 1)
                .setTerminationConfig(new TerminationConfig().withStepCountLimit(INDICATIVE_STEP_COUNT_LIMIT));
        return runOnce("shift-indicative-30agent", schedule, config, INDICATIVE_SEED,
                INDICATIVE_AGENT_COUNT, INDICATIVE_DAY_COUNT);
    }

    // ------------------------------------------------------------------
    //  Fixture builders — SAME desk shape (agent roster, grid, weights); differ only in mode and
    //  the presence of a shift library (P-19)
    // ------------------------------------------------------------------

    private Schedule buildSlotSchedule() {
        return ShiftModeFixtures.buildSlotModeSchedule(AGENT_COUNT, DAY_COUNT);
    }

    private Schedule buildShiftSchedule() {
        return ShiftModeFixtures.buildShiftModeSchedule(AGENT_COUNT, DAY_COUNT, TEMPLATE_COUNT, 1).schedule();
    }

    // ------------------------------------------------------------------
    //  Run orchestration
    // ------------------------------------------------------------------

    private List<RunMetrics> runSeeds(String arm, Supplier<Schedule> fixtureSupplier, boolean seatsFirst,
                                       int agentCount, int dayCount) {
        List<RunMetrics> runs = new ArrayList<>(SEEDS.length);
        for (long seed : SEEDS) {
            Schedule schedule = fixtureSupplier.get();
            SolverConfig config = loadSolverConfig(seed, seatsFirst);
            runs.add(runOnce(arm, schedule, config, seed, agentCount, dayCount));
        }
        return runs;
    }

    private RunMetrics runOnce(String arm, Schedule schedule, SolverConfig config, long seed,
                                int agentCount, int dayCount) {
        SolverFactory<Schedule> factory = SolverFactory.create(config);
        Solver<Schedule> solver = factory.buildSolver();

        long startMillis = System.currentTimeMillis();
        Schedule solved = solver.solve(schedule);
        long elapsedMillis = System.currentTimeMillis() - startMillis;

        return computeMetrics(arm, seed, solved, elapsedMillis, agentCount, dayCount);
    }

    /**
     * Loads the REAL {@code solverConfig.xml} (never a hand-built config) and attaches step-count
     * termination to the trailing local-search phase (P-18). When {@code seatsFirst} is true, swaps
     * the first two (construction-heuristic) phases in place — the ONLY change between the two D-08
     * orderings (P-35) — so the load-bearing {@code cacheType}/{@code selectionOrder}/
     * {@code sorterManner} triple on the seat entity selector (commit {@code 2ee41e2}) travels with
     * it automatically in both arms, since both arms share the exact same {@code EntitySelectorConfig}
     * object instances from the one loaded resource.
     */
    private static SolverConfig loadSolverConfig(long seed, boolean seatsFirst) {
        SolverConfig config = SolverConfig.createFromXmlResource("solverConfig.xml").withRandomSeed(seed);

        List<PhaseConfig> phases = config.getPhaseConfigList();
        if (phases.size() != 3
                || !isConstructionHeuristicForEntity(phases.get(0), AgentShiftAssignment.class)
                || !isConstructionHeuristicForEntity(phases.get(1), AgentAssignment.class)) {
            throw new IllegalStateException(
                    "solverConfig.xml's phase order is no longer [shift CH, seat CH, local search] — "
                            + "update ShiftModelBenchmarkTest.loadSolverConfig's D-08 ordering-swap logic to match");
        }
        if (seatsFirst) {
            Collections.swap(phases, 0, 1);
        }
        phases.get(phases.size() - 1)
                .setTerminationConfig(new TerminationConfig().withStepCountLimit(STEP_COUNT_LIMIT));
        return config;
    }

    private static boolean isConstructionHeuristicForEntity(PhaseConfig<?> phase, Class<?> entityClass) {
        if (!(phase instanceof ConstructionHeuristicPhaseConfig chPhase)) {
            return false;
        }
        if (!(chPhase.getEntityPlacerConfig() instanceof QueuedEntityPlacerConfig placer)) {
            return false;
        }
        EntitySelectorConfig selector = placer.getEntitySelectorConfig();
        return selector != null && entityClass.equals(selector.getEntityClass());
    }

    // ------------------------------------------------------------------
    //  Metrics — model-independent only (D-14): 0hard reached, unstaffed demand slots, hours
    //  assigned/needed, elapsed time. Soft score is captured too but never thresholded.
    // ------------------------------------------------------------------

    private RunMetrics computeMetrics(String arm, long seed, Schedule solved, long elapsedMillis,
                                       int agentCount, int dayCount) {
        Map<TimeslotKey, Integer> requiredByTimeslot = new HashMap<>();
        for (StaffingRequirement sr : solved.getStaffingRequirements()) {
            requiredByTimeslot.merge(keyOf(sr.getTimeslot()), sr.getRequiredFTEs(), Integer::sum);
        }

        Map<TimeslotKey, Integer> filledByTimeslot = new HashMap<>();
        long seatedCount = 0;
        for (AgentAssignment a : solved.getAssignments()) {
            if (a.getAgent() != null) {
                filledByTimeslot.merge(keyOf(a.getTimeslot()), 1, Integer::sum);
                seatedCount++;
            }
        }

        int unstaffedDemandSlots = 0;
        for (Map.Entry<TimeslotKey, Integer> entry : requiredByTimeslot.entrySet()) {
            int filled = filledByTimeslot.getOrDefault(entry.getKey(), 0);
            if (filled < entry.getValue()) {
                unstaffedDemandSlots++;
            }
        }

        BigDecimal hoursAssigned = BigDecimal.valueOf(seatedCount)
                .multiply(BigDecimal.valueOf(solved.getIncrementMinutes()))
                .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
        BigDecimal hoursNeeded = ShiftModeFixtures.CONTRACTED_HOURS.multiply(BigDecimal.valueOf((long) agentCount * dayCount));

        return new RunMetrics(
                arm, seed,
                solved.getScore().hardScore(), solved.getScore().softScore(),
                unstaffedDemandSlots, hoursAssigned, hoursNeeded,
                elapsedMillis);
    }

    private static TimeslotKey keyOf(Timeslot ts) {
        return new TimeslotKey(ts.getDate(), ts.getStartTime());
    }

    // ------------------------------------------------------------------
    //  Reporting — markdown tables printed to stdout, transcribed verbatim into 15-BENCHMARK.md
    // ------------------------------------------------------------------

    private void printPerRunTable(List<RunMetrics> runs) {
        System.out.println();
        System.out.println("Per-run results:");
        System.out.println("| arm | seed | hardScore | softScore | unstaffedDemandSlots | hoursAssigned | "
                + "hoursNeeded | elapsedMillis |");
        System.out.println("|---|---|---|---|---|---|---|---|");
        for (RunMetrics m : runs) {
            printRow(m);
        }
    }

    private void printRow(RunMetrics m) {
        System.out.println("| " + m.arm() + " | " + m.seed() + " | " + m.hardScore() + " | " + m.softScore()
                + " | " + m.unstaffedDemandSlots() + " | " + m.hoursAssigned() + " | " + m.hoursNeeded()
                + " | " + m.elapsedMillis() + " |");
    }

    private void printSummaryTable(List<List<RunMetrics>> armRunLists) {
        System.out.println();
        System.out.println("Summary (median and full min/max spread, never a mean):");
        System.out.println("| arm | hardScore median | hardScore min | hardScore max | "
                + "unstaffedDemandSlots median | unstaffedDemandSlots min | unstaffedDemandSlots max | "
                + "hoursAssigned median | hoursAssigned min | hoursAssigned max | "
                + "softScore median | softScore min | softScore max |");
        System.out.println("|---|---|---|---|---|---|---|---|---|---|---|---|---|");
        for (List<RunMetrics> runs : armRunLists) {
            printSummaryRow(runs);
        }
    }

    private void printSummaryRow(List<RunMetrics> runs) {
        String arm = runs.get(0).arm();
        List<Double> hard = runs.stream().map(m -> (double) m.hardScore()).sorted().toList();
        List<Double> unstaffed = runs.stream().map(m -> (double) m.unstaffedDemandSlots()).sorted().toList();
        List<Double> hours = runs.stream().map(m -> m.hoursAssigned().doubleValue()).sorted().toList();
        List<Double> soft = runs.stream().map(m -> (double) m.softScore()).sorted().toList();

        System.out.println("| " + arm
                + " | " + median(hard) + " | " + hard.get(0) + " | " + hard.get(hard.size() - 1)
                + " | " + median(unstaffed) + " | " + unstaffed.get(0) + " | " + unstaffed.get(unstaffed.size() - 1)
                + " | " + median(hours) + " | " + hours.get(0) + " | " + hours.get(hours.size() - 1)
                + " | " + median(soft) + " | " + soft.get(0) + " | " + soft.get(soft.size() - 1) + " |");
    }

    private static double median(List<Double> sortedAscending) {
        int n = sortedAscending.size();
        if (n == 0) {
            throw new IllegalStateException("cannot compute median of an empty run set");
        }
        if (n % 2 == 1) {
            return sortedAscending.get(n / 2);
        }
        return (sortedAscending.get(n / 2 - 1) + sortedAscending.get(n / 2)) / 2.0;
    }
}
