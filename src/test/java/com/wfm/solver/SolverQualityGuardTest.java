package com.wfm.solver;

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.score.constraint.ConstraintMatchTotal;
import ai.timefold.solver.core.api.solver.Solver;
import ai.timefold.solver.core.api.solver.SolutionManager;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.phase.PhaseConfig;
import ai.timefold.solver.core.config.score.director.ScoreDirectorFactoryConfig;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;

import com.wfm.model.AgentAssignment;
import com.wfm.model.AgentShiftAssignment;
import com.wfm.model.ConstraintWeights;
import com.wfm.model.Schedule;
import com.wfm.model.ShiftBandPair;
import com.wfm.model.ShiftTemplate;
import com.wfm.model.ShiftTemplateBreakBand;
import com.wfm.model.Timeslot;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * (Phase 15, plan 15-14, gap closure G-15-22 / G-15-29) The automated guard on solver quality that
 * did not exist through two sessions that paid for its absence — an acceptor regression that
 * shipped CI-green (G-15-22) and a weight-tuning session that burned ~12 live solves comparing hard
 * scores that were never comparable (G-15-29). Runs in the DEFAULT {@code ./gradlew test} suite,
 * ungated — unlike {@link ShiftModelBenchmarkTest}, which sits behind {@code -Dwfm.benchmark=true}
 * and therefore never ran on the deploy gate (P-43).
 *
 * <p><strong>Why not a hard-score ceiling.</strong> Byte-identical configuration, verified field by
 * field, produced {@code 0 hard FEASIBLE} and {@code -20 hard NOT FEASIBLE} twenty minutes apart on
 * the same build against the live desk on 2026-09-01. A test asserting {@code hardScore >= -N} on
 * one run fails randomly and gets deleted by the third person it annoys. What WAS stable across
 * every live run that day — including the deliberately-misconfigured -120 run — is a set of
 * structural invariants, enforced by constraints rather than found by search: zero split shifts,
 * zero edge breaks, every edge hour staffed. This guard asserts those, walked independently of the
 * score director (P-17, no {@link ShiftBandPair#covers}, no {@code ScheduleConstraintProvider}, no
 * {@code SolutionManager} in any walker), and reserves its one score-shaped assertion for the
 * violation-COUNT median across five seeds (P-42) — never a single run's raw hard score.
 *
 * <p>Every solve here runs through the shipped {@code src/main/resources/solverConfig.xml}
 * ({@link SolverConfig#createFromXmlResource}, never a hand-built config — P-18, the same
 * convention {@link ShiftEnvelopeGroundTruthTest} and {@link ShiftDeskEndToEndRegressionTest}
 * established) and is seeded and step-count terminated, never wall-clock terminated (P-44) — the
 * property whose absence makes {@code BreakAwareConstructionTest} machine-load sensitive
 * ({@code deferred-items.md}). Elapsed time is printed for observability and asserted nowhere.
 *
 * <p><strong>Plan 15-15 -- the guard proven able to fail.</strong> A guard that has only ever
 * passed is exactly as trustworthy as the acceptor test suite that shipped a sevenfold regression
 * green (G-15-22) -- which is to say, not at all. The red-proofs and thesis proof below corrupt an
 * ALREADY-SOLVED clean schedule (never re-solving under a changed configuration) so every proof is
 * deterministic and carries no search variance of its own -- the same variance this guard exists to
 * be immune to. Corruption is always subtractive (an agent unseated from a held slot, or a
 * shift-band pair nulled), matching {@link ShiftEnvelopeGroundTruthTest} Task 2's discipline: "a
 * check that has never failed proves nothing about its ability to fail."
 */
class SolverQualityGuardTest {

    private static final long[] SEEDS = {1L, 2L, 3L, 4L, 5L};

    /**
     * Convergence escape hatch (plan 15-14's bounded escape-hatch clause), applied here rather than
     * left for Task 3: at the plan's literal {@code 2_000} (matching
     * {@code ShiftDeskEndToEndRegressionTest}'s budget on a much smaller 3-agent/3-day, zero-slack
     * shape), this fixture's slack-carrying, 10-agent/3-day, 270-entity problem left a genuine
     * interior hole on seed 1. Escape-hatch rung 1 (step count alone, 2_000 -&gt; 5_000) did not clear
     * it either at {@code DAY_COUNT = 3}. Rung 3 ({@code DAY_COUNT} 3 -&gt; 2, see
     * {@link LiveShapeShiftDeskFixture#DAY_COUNT}) applied alone at 2_000 steps also did not clear
     * it. The two rungs applied TOGETHER -- {@code DAY_COUNT = 2} and {@code STEP_COUNT_LIMIT = 5_000}
     * -- do. Never lowered below 2_000, never switched to wall-clock. This is a fixture-fairness
     * finding on the UNMODIFIED build, not a product regression; recorded verbatim in the SUMMARY.
     */
    private static final int STEP_COUNT_LIMIT = 5_000;

    private static final int AGENT_COUNT =
            LiveShapeShiftDeskFixture.TEMPLATE_SPECS.size() * LiveShapeShiftDeskFixture.IDEAL_HOLDERS_PER_TEMPLATE;

    /**
     * INV-4's ceiling (P-42), committed as a RULE before any number was read: the baseline median of
     * {@code totalHardViolations} across the five seeds, observed at THIS commit, plus a headroom of
     * 2, floored at 2 when the baseline median is 0. This is a coarse trip-wire sized to catch the
     * sevenfold acceptor class of regression (-9 to -66, G-15-22) -- not a fine-grained quality
     * metric; INV-1/2/3 above are the real gate. The median is taken across seeds precisely so one
     * noisy seed can never fail it (median-of-five, never a mean, per the standing 15-BENCHMARK.md
     * discipline).
     *
     * <p><strong>Observed per-seed baseline this constant was set from</strong> (unmodified build,
     * this commit, {@code STEP_COUNT_LIMIT = 5_000}, {@code DAY_COUNT = 2} as declared above -- the
     * per-seed table printed by {@code liveShapeDesk_fiveSeeds_holdEveryStructuralInvariant},
     * transcribed verbatim into {@code 15-14-SUMMARY.md}): seed 1 -&gt; 3, seed 2 -&gt; 1, seed 3
     * -&gt; 2, seed 4 -&gt; 0, seed 5 -&gt; 1 -- sorted {@code [0, 1, 1, 2, 3]}, median 1.0, ceiling
     * = 1 + 2 = 3.
     */
    private static final int TOTAL_VIOLATION_CEILING = 3;

    // ------------------------------------------------------------------
    //  Task 1/2 -- the tracer, widened: one seed, all three structural invariants, the failure
    //  report wired into every assertion's description
    // ------------------------------------------------------------------

    @Test
    @DisplayName("live-shape desk, single seed: solves through the shipped config, and all three structural invariants hold")
    void liveShapeDesk_singleSeed_solvesAndEveryAgentDayIsContiguous() {
        LiveShapeShiftDeskFixture.Fixture fixture =
                LiveShapeShiftDeskFixture.build(AGENT_COUNT, LiveShapeShiftDeskFixture.DAY_COUNT);

        long startMillis = System.currentTimeMillis();
        Schedule solved = solve(fixture.schedule(), SEEDS[0]);
        long elapsedMillis = System.currentTimeMillis() - startMillis;
        System.out.println("[G-15-22 guard] seed=" + SEEDS[0] + " elapsedMillis=" + elapsedMillis
                + " score=" + solved.getScore());

        assertNonVacuouslyFeasible(solved);

        List<SplitShift> splits = findSplitShifts(solved);
        List<EdgeBreak> edgeBreaks = findEdgeBreaks(solved);
        List<UnstaffedEdgeHour> unstaffed = findUnstaffedEdgeHours(solved, LiveShapeShiftDeskFixture.EDGE_HOURS);

        assertThat(splits)
                .as(buildQualityReport("INV-1 SPLIT SHIFTS", SEEDS[0], solved, splits, edgeBreaks, unstaffed))
                .isEmpty();
        assertThat(edgeBreaks)
                .as(buildQualityReport("INV-2 EDGE BREAKS", SEEDS[0], solved, splits, edgeBreaks, unstaffed))
                .isEmpty();
        assertThat(unstaffed)
                .as(buildQualityReport("INV-3 EDGE-HOUR COVERAGE", SEEDS[0], solved, splits, edgeBreaks, unstaffed))
                .isEmpty();
    }

    // ------------------------------------------------------------------
    //  Task 3 -- five seeds, the median violation-count ceiling, the pinned shipped defaults
    // ------------------------------------------------------------------

    /** One seed's outcome from the five-seed sweep. */
    record GuardRun(long seed, int hardScore, int totalHardViolations, Map<String, Integer> violationsByConstraint,
                     int splitShifts, int edgeBreaks, int unstaffedEdgeHours, long elapsedMillis) {}

    @Test
    @DisplayName("live-shape desk, five seeds: every structural invariant holds on every seed, and the median violation count sits under the pre-committed ceiling")
    void liveShapeDesk_fiveSeeds_holdEveryStructuralInvariant() {
        List<GuardRun> runs = new ArrayList<>(SEEDS.length);
        long sweepStartMillis = System.currentTimeMillis();

        for (long seed : SEEDS) {
            // A FRESH fixture per seed -- never reuse a solved Schedule across seeds.
            LiveShapeShiftDeskFixture.Fixture fixture =
                    LiveShapeShiftDeskFixture.build(AGENT_COUNT, LiveShapeShiftDeskFixture.DAY_COUNT);

            long startMillis = System.currentTimeMillis();
            Schedule solved = solve(fixture.schedule(), seed);
            long elapsedMillis = System.currentTimeMillis() - startMillis;

            assertNonVacuouslyFeasible(solved);

            List<SplitShift> splits = findSplitShifts(solved);
            List<EdgeBreak> edgeBreaks = findEdgeBreaks(solved);
            List<UnstaffedEdgeHour> unstaffed = findUnstaffedEdgeHours(solved, LiveShapeShiftDeskFixture.EDGE_HOURS);
            Map<String, Integer> violationsByConstraint = hardMatchCountsByConstraint(solved);
            int totalHardViolations = violationsByConstraint.values().stream().mapToInt(Integer::intValue).sum();

            runs.add(new GuardRun(seed, solved.getScore().hardScore(), totalHardViolations, violationsByConstraint,
                    splits.size(), edgeBreaks.size(), unstaffed.size(), elapsedMillis));

            // INV-1/2/3: per-seed, absolute, structural -- the -120 live run held all three.
            assertThat(splits)
                    .as(buildQualityReport("INV-1 SPLIT SHIFTS", seed, solved, splits, edgeBreaks, unstaffed))
                    .isEmpty();
            assertThat(edgeBreaks)
                    .as(buildQualityReport("INV-2 EDGE BREAKS", seed, solved, splits, edgeBreaks, unstaffed))
                    .isEmpty();
            assertThat(unstaffed)
                    .as(buildQualityReport("INV-3 EDGE-HOUR COVERAGE", seed, solved, splits, edgeBreaks, unstaffed))
                    .isEmpty();
        }

        long sweepElapsedMillis = System.currentTimeMillis() - sweepStartMillis;
        printPerSeedTable(runs);
        printPerConstraintAcrossSeedsTable(runs);
        System.out.println();
        System.out.println("[G-15-22 guard] five-seed sweep total elapsedMillis=" + sweepElapsedMillis);

        // INV-4: the ONLY score-shaped assertion, on the MEDIAN of totalHardViolations across seeds
        // (P-42) -- never a single seed's hardScore.
        List<Double> totalViolationsSorted = runs.stream()
                .map(r -> (double) r.totalHardViolations())
                .sorted()
                .toList();
        double median = median(totalViolationsSorted);
        System.out.println("[G-15-22 guard] median totalHardViolations across " + SEEDS.length
                + " seeds=" + median + " ceiling=" + TOTAL_VIOLATION_CEILING);

        assertThat(median)
                .as("INV-4 VIOLATION-COUNT CEILING: median totalHardViolations across %d seeds is %s, "
                                + "must be <= %d (see TOTAL_VIOLATION_CEILING's javadoc for the pre-committed "
                                + "rule) -- compare violation COUNTS per constraint, never raw hard scores "
                                + "across weight changes (G-15-29) -- per-seed runs: %s",
                        SEEDS.length, median, TOTAL_VIOLATION_CEILING, runs)
                .isLessThanOrEqualTo((double) TOTAL_VIOLATION_CEILING);
    }

    @Test
    @DisplayName("the four Phase 15 ConstraintWeights defaults are pinned to their current shipped values")
    void defaultConstraintWeights_areTheDocumentedShippedValues() {
        // Solve-free (P-41): the counterweight to LiveShapeShiftDeskFixture pinning its OWN weights
        // to HANDOFF.md's live values -- without this, a silent change to a shipped default would be
        // invisible to the guard, which is the G-15-30 shape.
        ConstraintWeights defaults = new ConstraintWeights();

        assertThat(defaults.getShiftEnvelopeComplianceWeight())
                .as("shiftEnvelopeComplianceWeight's shipped default changed -- update this assertion "
                        + "deliberately, or this is a silent regression of the G-15-30 shape")
                .isEqualTo(HardSoftScore.ofHard(1));
        assertThat(defaults.getShiftWorkContiguityWeight())
                .as("shiftWorkContiguityWeight's shipped default changed -- update this assertion "
                        + "deliberately, or this is a silent regression of the G-15-30 shape")
                .isEqualTo(HardSoftScore.ofHard(10));
        assertThat(defaults.getBandCapacityWeight())
                .as("bandCapacityWeight's shipped default changed -- update this assertion "
                        + "deliberately, or this is a silent regression of the G-15-30 shape")
                .isEqualTo(HardSoftScore.ofHard(1));
        assertThat(defaults.getUnassignedAssignmentWeight())
                .as("unassignedAssignmentWeight's shipped default changed -- update this assertion "
                        + "deliberately, or this is a silent regression of the G-15-30 shape")
                .isEqualTo(HardSoftScore.ofSoft(1000));
    }

    // ------------------------------------------------------------------
    //  Task 1 (plan 15-15) -- red-proofs: each invariant walker demonstrated able to go red,
    //  exactly once, on exactly its own injected defect -- plus the negative control that the
    //  break window itself is never mistaken for a hole.
    // ------------------------------------------------------------------

    @Test
    @DisplayName("INV-1 red-proof: a non-break hole between an agent's first and last held slot is flagged once, on the right agent-day")
    void redProof_INV1_aNonBreakHoleIsFlaggedOnceOnTheRightAgentDay() {
        Schedule solved = solveCleanFixture();
        Map<String, List<AgentAssignment>> seatsByAgentDate = seatsByAgentDate(solved);

        AgentShiftAssignment target = solved.getShiftAssignments().stream()
                .filter(sa -> sa.getShiftBandPair() != null
                        && "Weekend Flex".equals(sa.getShiftBandPair().template().getName()))
                .findFirst()
                .or(() -> solved.getShiftAssignments().stream()
                        .filter(sa -> sa.getShiftBandPair() != null)
                        .filter(sa -> heldStartCount(seatsByAgentDate, sa) >= 3)
                        .findFirst())
                .orElseThrow(() -> new IllegalStateException("no agent-day with >= 3 held slots found to corrupt"));

        List<AgentAssignment> seats = seatsByAgentDate.get(target.getAgent().getId() + "@" + target.getDate());
        ShiftTemplate template = target.getShiftBandPair().template();
        ShiftTemplateBreakBand band = target.getShiftBandPair().band();
        LocalTime breakStart = template.getStartTime().plusMinutes(band.getOffsetMinutes());
        LocalTime breakEnd = breakStart.plusMinutes(band.getDurationMinutes());

        List<LocalTime> heldStarts = seats.stream().map(a -> a.getTimeslot().getStartTime()).distinct().sorted().toList();
        assertThat(heldStarts.size())
                .as("sanity: the chosen agent-day must hold at least 3 slots for an interior non-break hole to exist")
                .isGreaterThanOrEqualTo(3);

        LocalTime first = heldStarts.get(0);
        LocalTime last = heldStarts.get(heldStarts.size() - 1);
        LocalTime victimSlot = heldStarts.stream()
                .filter(t -> t.isAfter(first) && t.isBefore(last))
                .filter(t -> t.isBefore(breakStart) || !t.isBefore(breakEnd))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("no interior non-break slot found to corrupt on agent="
                        + target.getAgent().getId() + " date=" + target.getDate()));

        unseat(solved, target.getAgent().getId(), target.getDate(), victimSlot);

        List<SplitShift> splits = findSplitShifts(solved);
        assertThat(splits)
                .as("exactly one split shift must be flagged for the corrupted agent-day: %s", splits)
                .hasSize(1);
        SplitShift split = splits.get(0);
        assertThat(split.agentId()).as("the flagged split must name the corrupted agent").isEqualTo(target.getAgent().getId());
        assertThat(split.date()).as("the flagged split must name the corrupted date").isEqualTo(target.getDate());
        assertThat(split.holes()).as("the flagged split's holes must be exactly the one unseated hour").containsExactly(victimSlot);
    }

    @Test
    @DisplayName("INV-1 negative control: the assigned break window itself is never flagged as a split-shift hole")
    void redProof_INV1_theBreakWindowItselfIsNotAHole() {
        Schedule solved = solveCleanFixture();
        Map<String, List<AgentAssignment>> seatsByAgentDate = seatsByAgentDate(solved);

        AgentShiftAssignment target = solved.getShiftAssignments().stream()
                .filter(sa -> sa.getShiftBandPair() != null && sa.getShiftBandPair().band() != null)
                .filter(sa -> {
                    LocalTime breakStart = sa.getShiftBandPair().template().getStartTime()
                            .plusMinutes(sa.getShiftBandPair().band().getOffsetMinutes());
                    List<AgentAssignment> seats = seatsByAgentDate.getOrDefault(
                            sa.getAgent().getId() + "@" + sa.getDate(), List.of());
                    return seats.stream().noneMatch(a -> a.getTimeslot().getStartTime().equals(breakStart));
                })
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("no agent-day found whose break slot is genuinely unheld"));

        ShiftTemplate template = target.getShiftBandPair().template();
        ShiftTemplateBreakBand band = target.getShiftBandPair().band();
        LocalTime breakStart = template.getStartTime().plusMinutes(band.getOffsetMinutes());

        List<AgentAssignment> seats = seatsByAgentDate.get(target.getAgent().getId() + "@" + target.getDate());
        List<LocalTime> heldStarts = seats.stream().map(a -> a.getTimeslot().getStartTime()).distinct().sorted().toList();
        assertThat(heldStarts)
                .as("sanity: the agent's break slot must genuinely be unheld -- the negative control requires "
                        + "a real gap, not a coincidentally full day")
                .doesNotContain(breakStart);

        assertThat(findSplitShifts(solved))
                .as("a clean solve's break-window gap must never be flagged as a split shift -- the trap "
                        + "ShiftWorkContiguityConstraintTest documents (a rule that merely counts interior gaps "
                        + "mistakes the break for a hole, or permits a hole by calling it the break)")
                .isEmpty();
    }

    @Test
    @DisplayName("INV-1 red-proof: a null shift-band pair is flagged with the (no shift assigned) marker")
    void redProof_INV1_aNullShiftPairIsFlagged() {
        Schedule solved = solveCleanFixture();

        AgentShiftAssignment target = solved.getShiftAssignments().get(0);
        UUID agentId = target.getAgent().getId();
        LocalDate date = target.getDate();

        target.setShiftBandPair(null);

        List<SplitShift> splits = findSplitShifts(solved);
        assertThat(splits)
                .as("exactly one split shift must be flagged for the null-pair agent-day: %s", splits)
                .hasSize(1);
        SplitShift split = splits.get(0);
        assertThat(split.agentId()).as("the flagged split must name the corrupted agent").isEqualTo(agentId);
        assertThat(split.date()).as("the flagged split must name the corrupted date").isEqualTo(date);
        assertThat(split.templateName())
                .as("a null shift-band pair must be recorded with the (no shift assigned) marker, closing "
                        + "the null-pair laundering loophole ShiftDeskEndToEndRegressionTest caught in the "
                        + "production constraint")
                .isEqualTo("(no shift assigned)");
    }

    @Test
    @DisplayName("INV-2 red-proof: unseating every held slot on one side of the break window is flagged as an operational edge break")
    void redProof_INV2_aBreakWithNoWorkOnOneSideIsFlagged() {
        Schedule solved = solveCleanFixture();
        Map<String, List<AgentAssignment>> seatsByAgentDate = seatsByAgentDate(solved);

        AgentShiftAssignment target = solved.getShiftAssignments().stream()
                .filter(sa -> sa.getShiftBandPair() != null && sa.getShiftBandPair().band() != null)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("no agent-day carries a band to corrupt"));

        ShiftTemplate template = target.getShiftBandPair().template();
        ShiftTemplateBreakBand band = target.getShiftBandPair().band();
        LocalTime breakStart = template.getStartTime().plusMinutes(band.getOffsetMinutes());
        LocalTime breakEnd = breakStart.plusMinutes(band.getDurationMinutes());

        List<AgentAssignment> seats = seatsByAgentDate.get(target.getAgent().getId() + "@" + target.getDate());
        List<LocalTime> beforeBreakStarts = seats.stream()
                .map(a -> a.getTimeslot().getStartTime())
                .filter(t -> t.isBefore(breakStart))
                .distinct()
                .toList();
        List<LocalTime> afterBreakStarts = seats.stream()
                .map(a -> a.getTimeslot().getStartTime())
                .filter(t -> !t.isBefore(breakEnd))
                .distinct()
                .toList();
        assertThat(beforeBreakStarts)
                .as("sanity: the chosen agent-day must hold at least one slot before the break")
                .isNotEmpty();
        assertThat(afterBreakStarts)
                .as("sanity: the chosen agent-day must also hold at least one slot after the break, so "
                        + "removing only the before-break slots leaves a meaningful one-sided corruption")
                .isNotEmpty();

        for (LocalTime t : beforeBreakStarts) {
            unseat(solved, target.getAgent().getId(), target.getDate(), t);
        }

        List<EdgeBreak> edgeBreaks = findEdgeBreaks(solved);
        assertThat(edgeBreaks)
                .as("exactly one edge break must be flagged: %s", edgeBreaks)
                .hasSize(1);
        EdgeBreak edgeBreak = edgeBreaks.get(0);
        assertThat(edgeBreak.agentId()).as("the flagged edge break must name the corrupted agent").isEqualTo(target.getAgent().getId());
        assertThat(edgeBreak.date()).as("the flagged edge break must name the corrupted date").isEqualTo(target.getDate());
        assertThat(edgeBreak.reason())
                .as("the flagged reason must identify the operational case (no worked slot on one side), "
                        + "not the structural case")
                .contains("operational");
    }

    @Test
    @DisplayName("INV-3 red-proof: unstaffing every agent at one edge hour on one date is flagged for that date and hour only")
    void redProof_INV3_anUnstaffedEdgeHourIsFlaggedForThatDateAndHourOnly() {
        Schedule solved = solveCleanFixture();

        LocalDate targetDate = LiveShapeShiftDeskFixture.BASE_DATE;
        LocalTime targetHour = LocalTime.of(8, 0);

        Set<UUID> agentsAt0800 = solved.getAssignments().stream()
                .filter(a -> a.getAgent() != null
                        && a.getTimeslot().getDate().equals(targetDate)
                        && a.getTimeslot().getStartTime().equals(targetHour))
                .map(a -> a.getAgent().getId())
                .collect(Collectors.toSet());
        assertThat(agentsAt0800)
                .as("sanity: at least one agent must genuinely hold the 08:00 seat on the target date -- "
                        + "sole-routed to Weekend Opening -- for unseating all of them to be a meaningful corruption")
                .isNotEmpty();

        for (UUID agentId : agentsAt0800) {
            unseat(solved, agentId, targetDate, targetHour);
        }

        List<UnstaffedEdgeHour> unstaffed = findUnstaffedEdgeHours(solved, LiveShapeShiftDeskFixture.EDGE_HOURS);
        assertThat(unstaffed)
                .as("exactly one unstaffed edge hour must be flagged: %s", unstaffed)
                .hasSize(1);
        UnstaffedEdgeHour hour = unstaffed.get(0);
        assertThat(hour.date()).as("the flagged cell must name the corrupted date").isEqualTo(targetDate);
        assertThat(hour.hour()).as("the flagged cell must name the corrupted hour").isEqualTo(targetHour);
        assertThat(hour.agentsWorking()).as("the flagged cell's agentsWorking must be zero").isZero();
    }

    // ------------------------------------------------------------------
    //  Task 3 reporting -- markdown tables printed to stdout, transcribed verbatim into the SUMMARY
    // ------------------------------------------------------------------

    private static void printPerSeedTable(List<GuardRun> runs) {
        System.out.println();
        System.out.println("Per-seed results:");
        System.out.println("| seed | hardScore | totalHardViolations | splitShifts | edgeBreaks | "
                + "unstaffedEdgeHours | elapsedMillis |");
        System.out.println("|---|---|---|---|---|---|---|");
        for (GuardRun r : runs) {
            System.out.println("| " + r.seed() + " | " + r.hardScore() + " | " + r.totalHardViolations()
                    + " | " + r.splitShifts() + " | " + r.edgeBreaks() + " | " + r.unstaffedEdgeHours()
                    + " | " + r.elapsedMillis() + " |");
        }
    }

    private static void printPerConstraintAcrossSeedsTable(List<GuardRun> runs) {
        System.out.println();
        System.out.println("Per-constraint violation counts across seeds:");
        Set<String> constraintNames = new LinkedHashSet<>();
        runs.forEach(r -> constraintNames.addAll(r.violationsByConstraint().keySet()));

        String header = "| constraint | " + runs.stream().map(r -> "seed " + r.seed())
                .collect(Collectors.joining(" | ")) + " |";
        System.out.println(header);
        System.out.println("|---|" + "---|".repeat(runs.size()));
        for (String name : constraintNames) {
            StringBuilder row = new StringBuilder("| ").append(name).append(" | ");
            for (GuardRun r : runs) {
                row.append(r.violationsByConstraint().getOrDefault(name, 0)).append(" | ");
            }
            System.out.println(row);
        }
    }

    /** Never a mean -- median only, per the standing 15-BENCHMARK.md discipline (D-16). */
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

    // ------------------------------------------------------------------
    //  Non-vacuity check -- shared by every test in this class that solves
    // ------------------------------------------------------------------

    private static void assertNonVacuouslyFeasible(Schedule solved) {
        assertThat(solved.getShiftAssignments())
                .as("solved schedule must actually carry shift rows -- a vacuous pass over zero rows proves nothing")
                .isNotEmpty();
        assertThat(solved.getShiftAssignments())
                .as("every shift row must hold a chosen pair")
                .allMatch(sa -> sa.getShiftBandPair() != null);
        long seatedCount = solved.getAssignments().stream().filter(a -> a.getAgent() != null).count();
        assertThat(seatedCount)
                .as("at least one seat must actually be filled -- a walker over zero seats is a vacuous pass")
                .isGreaterThan(0L);
    }

    // ------------------------------------------------------------------
    //  Task 1/2 (plan 15-15) -- corruption helpers, both modelled on
    //  ShiftEnvelopeGroundTruthTest's Task 2 helpers of the same names
    // ------------------------------------------------------------------

    /**
     * Builds a fresh live-shape fixture, solves it at {@code SEEDS[0]}, and asserts as a
     * PRECONDITION that all three structural walkers return empty before anything is corrupted --
     * every red-proof and thesis proof in this class starts from this, so a proof can never
     * accidentally be measuring a pre-existing defect rather than the one it just injected.
     */
    private static Schedule solveCleanFixture() {
        LiveShapeShiftDeskFixture.Fixture fixture =
                LiveShapeShiftDeskFixture.build(AGENT_COUNT, LiveShapeShiftDeskFixture.DAY_COUNT);
        Schedule solved = solve(fixture.schedule(), SEEDS[0]);
        assertNonVacuouslyFeasible(solved);
        assertThat(findSplitShifts(solved))
                .as("solveCleanFixture precondition: zero split shifts before any corruption")
                .isEmpty();
        assertThat(findEdgeBreaks(solved))
                .as("solveCleanFixture precondition: zero edge breaks before any corruption")
                .isEmpty();
        assertThat(findUnstaffedEdgeHours(solved, LiveShapeShiftDeskFixture.EDGE_HOURS))
                .as("solveCleanFixture precondition: every edge hour staffed before any corruption")
                .isEmpty();
        return solved;
    }

    /**
     * Removes {@code agentId}'s held seat at {@code (date, slotStart)} by setting its agent to
     * null -- punches a hole without moving any other agent's data, unlike {@link #relocateSeat}.
     * Fails loudly if no such seat is currently held, so a red-proof can never silently corrupt
     * nothing and report a false pass.
     */
    private static void unseat(Schedule solved, UUID agentId, LocalDate date, LocalTime slotStart) {
        AgentAssignment seat = solved.getAssignments().stream()
                .filter(a -> a.getAgent() != null
                        && a.getAgent().getId().equals(agentId)
                        && a.getTimeslot().getDate().equals(date)
                        && a.getTimeslot().getStartTime().equals(slotStart))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("no held seat found for agent=" + agentId
                        + " date=" + date + " slotStart=" + slotStart));
        seat.setAgent(null);
    }

    /**
     * Reassigns {@code seat} to a brand-new synthetic {@link Timeslot} at {@code newStart} --
     * never mutates an existing shared {@link Timeslot} instance in place, since multiple seats
     * (one per agent) reference the SAME timeslot object at any given slot in this fixture
     * (mirrors {@code ShiftEnvelopeGroundTruthTest.relocateSeat} and its documented reason: seats
     * share {@link Timeslot} instances, so an in-place mutation corrupts every other agent's seat
     * at that slot too). None of this plan's red-proofs need to MOVE a seat -- every one either
     * removes a seat ({@link #unseat}) or nulls a shift-band pair -- so this helper is not called
     * by any test in this class today; it exists so a future corruption case that DOES need
     * relocation does not reinvent the in-place-mutation mistake it avoids.
     */
    @SuppressWarnings("unused")
    private static void relocateSeat(AgentAssignment seat, LocalDate date, LocalTime newStart) {
        Timeslot moved = new Timeslot();
        moved.setId(UUID.randomUUID());
        moved.setTenantId(seat.getTenantId());
        moved.setDeskId(seat.getDeskId());
        moved.setScheduleId(seat.getScheduleId());
        moved.setDate(date);
        moved.setStartTime(newStart);
        moved.setEndTime(newStart.plusMinutes(LiveShapeShiftDeskFixture.INCREMENT_MINUTES));
        seat.setTimeslot(moved);
    }

    /** Count of distinct held slot-start times for one agent-day, read from a precomputed map. */
    private static long heldStartCount(Map<String, List<AgentAssignment>> seatsByAgentDate, AgentShiftAssignment sa) {
        List<AgentAssignment> seats = seatsByAgentDate.getOrDefault(sa.getAgent().getId() + "@" + sa.getDate(), List.of());
        return seats.stream().map(a -> a.getTimeslot().getStartTime()).distinct().count();
    }

    // ------------------------------------------------------------------
    //  Shared seat lookup -- every walker in this class groups held seats by agent-date the same way
    // ------------------------------------------------------------------

    private static Map<String, List<AgentAssignment>> seatsByAgentDate(Schedule solved) {
        Map<String, List<AgentAssignment>> byAgentDate = new HashMap<>();
        for (AgentAssignment a : solved.getAssignments()) {
            if (a.getAgent() == null) {
                continue;
            }
            String key = a.getAgent().getId() + "@" + a.getTimeslot().getDate();
            byAgentDate.computeIfAbsent(key, k -> new ArrayList<>()).add(a);
        }
        return byAgentDate;
    }

    // ------------------------------------------------------------------
    //  INV-1 -- the independent split-shift walker (P-17)
    // ------------------------------------------------------------------

    record SplitShift(UUID agentId, LocalDate date, String templateName, List<LocalTime> holes) {}

    /**
     * Walks the solved schedule from raw {@link LocalTime} arithmetic only -- never
     * {@link ShiftBandPair#covers}, {@code ScheduleConstraintProvider}, {@code SolutionManager}, or
     * any score director (P-17). A walker that reuses the production predicate inherits the
     * production predicate's own blind spot.
     *
     * <p>An agent-day whose {@code shiftBandPair} is null is NOT exempt -- it is recorded as a split
     * with {@code templateName = "(no shift assigned)"}, closing the null-pair laundering loophole
     * {@code ShiftDeskEndToEndRegressionTest} caught in the production constraint.
     */
    static List<SplitShift> findSplitShifts(Schedule solved) {
        Map<String, List<AgentAssignment>> seatsByAgentDate = seatsByAgentDate(solved);

        List<SplitShift> splits = new ArrayList<>();
        for (AgentShiftAssignment sa : solved.getShiftAssignments()) {
            String key = sa.getAgent().getId() + "@" + sa.getDate();
            List<AgentAssignment> seats = seatsByAgentDate.getOrDefault(key, List.of());

            ShiftBandPair pair = sa.getShiftBandPair();
            if (pair == null) {
                splits.add(new SplitShift(sa.getAgent().getId(), sa.getDate(), "(no shift assigned)", List.of()));
                continue;
            }

            List<LocalTime> heldStarts = seats.stream()
                    .map(a -> a.getTimeslot().getStartTime())
                    .distinct()
                    .sorted()
                    .toList();
            if (heldStarts.size() < 2) {
                continue; // fewer than two held slots -- nothing to be split
            }

            ShiftTemplate template = pair.template();
            ShiftTemplateBreakBand band = pair.band();
            LocalTime breakStart = band == null ? null : template.getStartTime().plusMinutes(band.getOffsetMinutes());
            LocalTime breakEnd = breakStart == null ? null : breakStart.plusMinutes(band.getDurationMinutes());

            LocalTime first = heldStarts.get(0);
            LocalTime last = heldStarts.get(heldStarts.size() - 1);
            List<LocalTime> holes = new ArrayList<>();
            for (LocalTime t = first; t.isBefore(last); t = t.plusMinutes(LiveShapeShiftDeskFixture.INCREMENT_MINUTES)) {
                if (heldStarts.contains(t)) {
                    continue;
                }
                boolean isBreak = breakStart != null && !t.isBefore(breakStart) && t.isBefore(breakEnd);
                if (!isBreak) {
                    holes.add(t);
                }
            }
            if (!holes.isEmpty()) {
                splits.add(new SplitShift(sa.getAgent().getId(), sa.getDate(), template.getName(), holes));
            }
        }
        return splits;
    }

    // ------------------------------------------------------------------
    //  INV-2 -- the independent edge-break walker (P-17)
    // ------------------------------------------------------------------

    record EdgeBreak(UUID agentId, LocalDate date, String templateName, LocalTime breakStart,
                      LocalTime breakEnd, String reason) {}

    /**
     * Two independent checks per agent-day carrying a non-null pair with a non-null band and at
     * least one held seat that date. Both computed from raw {@link LocalTime} arithmetic over
     * {@code template.getStartTime()}, {@code band.getOffsetMinutes()} and
     * {@code band.getDurationMinutes()} only (P-17).
     *
     * <p><strong>Structural</strong> -- a band whose offset is 0, or whose break runs flush against
     * the envelope end, is a boundary break: {@code deferred-items.md}'s "Blocked-break-hours has no
     * enforcement point in SHIFT mode" entry records this as legal at save time and {@code 0hard} at
     * solve time, producing a shift with the "break" bolted onto its boundary -- operationally a late
     * start or an early finish, not a break.
     *
     * <p><strong>Operational</strong> -- even a structurally sound band can be defeated on a SOLVED
     * schedule if the agent's actual held seats land entirely on one side of it: no seat before
     * {@code breakStart}, or no seat at/after {@code breakEnd}. The break then falls outside the
     * agent's own worked span rather than inside it, which {@link #findSplitShifts} cannot see (its
     * hole-walk only looks BETWEEN the first and last held seat) -- exactly the Mariami Katcheishvili
     * shape from {@code HANDOFF.md} T7/T6 (8 consecutive worked hours, zero breaks).
     */
    static List<EdgeBreak> findEdgeBreaks(Schedule solved) {
        Map<String, List<AgentAssignment>> seatsByAgentDate = seatsByAgentDate(solved);

        List<EdgeBreak> edgeBreaks = new ArrayList<>();
        for (AgentShiftAssignment sa : solved.getShiftAssignments()) {
            ShiftBandPair pair = sa.getShiftBandPair();
            if (pair == null || pair.band() == null) {
                continue; // no band -- INV-1 already covers the null-pair case; no break window to evaluate here
            }
            String key = sa.getAgent().getId() + "@" + sa.getDate();
            List<AgentAssignment> seats = seatsByAgentDate.getOrDefault(key, List.of());
            if (seats.isEmpty()) {
                continue; // no held seat that date -- nothing to evaluate
            }

            ShiftTemplate template = pair.template();
            ShiftTemplateBreakBand band = pair.band();
            LocalTime breakStart = template.getStartTime().plusMinutes(band.getOffsetMinutes());
            LocalTime breakEnd = breakStart.plusMinutes(band.getDurationMinutes());

            boolean structurallySound = band.getOffsetMinutes() > 0 && breakEnd.isBefore(template.getEndTime());
            if (!structurallySound) {
                edgeBreaks.add(new EdgeBreak(sa.getAgent().getId(), sa.getDate(), template.getName(),
                        breakStart, breakEnd,
                        "structural: band offset=" + band.getOffsetMinutes()
                                + " -- flush against an envelope edge, not a real interior break"));
            }

            boolean hasSeatBeforeBreak = seats.stream()
                    .anyMatch(a -> a.getTimeslot().getStartTime().isBefore(breakStart));
            boolean hasSeatAfterBreak = seats.stream()
                    .anyMatch(a -> !a.getTimeslot().getStartTime().isBefore(breakEnd));
            if (!(hasSeatBeforeBreak && hasSeatAfterBreak)) {
                edgeBreaks.add(new EdgeBreak(sa.getAgent().getId(), sa.getDate(), template.getName(),
                        breakStart, breakEnd,
                        "operational: no worked slot on one side of the break -- hasBefore="
                                + hasSeatBeforeBreak + " hasAfter=" + hasSeatAfterBreak));
            }
        }
        return edgeBreaks;
    }

    // ------------------------------------------------------------------
    //  INV-3 -- the independent edge-hour coverage walker
    // ------------------------------------------------------------------

    record UnstaffedEdgeHour(LocalDate date, LocalTime hour, int agentsWorking) {}

    /** Every {@code (date, hour)} pair (over the schedule's period and the given edge hours) with
     * zero distinct seated agents. */
    static List<UnstaffedEdgeHour> findUnstaffedEdgeHours(Schedule solved, List<LocalTime> edgeHours) {
        Map<LocalDate, Map<LocalTime, Integer>> matrix = edgeHourCoverageMatrix(solved, edgeHours);
        List<UnstaffedEdgeHour> unstaffed = new ArrayList<>();
        matrix.forEach((date, byHour) -> byHour.forEach((hour, count) -> {
            if (count == 0) {
                unstaffed.add(new UnstaffedEdgeHour(date, hour, count));
            }
        }));
        return unstaffed;
    }

    /**
     * Distinct seated-agent count at every {@code (date, hour)} pair, for every date in the
     * schedule's period and every hour in {@code edgeHours} -- including zero-count cells, so
     * {@link #buildQualityReport} can print the whole coverage picture, not just the failing cells.
     */
    private static Map<LocalDate, Map<LocalTime, Integer>> edgeHourCoverageMatrix(Schedule solved, List<LocalTime> edgeHours) {
        Map<LocalDate, Map<LocalTime, Set<UUID>>> agentsWorkingByDateHour = new LinkedHashMap<>();
        for (AgentAssignment a : solved.getAssignments()) {
            if (a.getAgent() == null) {
                continue;
            }
            Timeslot ts = a.getTimeslot();
            if (!edgeHours.contains(ts.getStartTime())) {
                continue;
            }
            agentsWorkingByDateHour.computeIfAbsent(ts.getDate(), d -> new LinkedHashMap<>())
                    .computeIfAbsent(ts.getStartTime(), h -> new HashSet<>())
                    .add(a.getAgent().getId());
        }

        Map<LocalDate, Map<LocalTime, Integer>> matrix = new LinkedHashMap<>();
        for (LocalDate date = solved.getPeriodStartDate(); !date.isAfter(solved.getPeriodEndDate()); date = date.plusDays(1)) {
            Map<LocalTime, Integer> byHour = new LinkedHashMap<>();
            for (LocalTime hour : edgeHours) {
                Set<UUID> agents = agentsWorkingByDateHour
                        .getOrDefault(date, Map.of())
                        .getOrDefault(hour, Set.of());
                byHour.put(hour, agents.size());
            }
            matrix.put(date, byHour);
        }
        return matrix;
    }

    // ------------------------------------------------------------------
    //  INV-4 -- the violation-COUNT reader (never the score)
    // ------------------------------------------------------------------

    /**
     * Reads violation COUNTS per constraint (never scores) via a fresh scoring
     * {@link SolutionManager}, constructed exactly as
     * {@code ShiftDeskEndToEndRegressionTest.hardPenaltiesByConstraint} does. Ordered by descending
     * count so the worst offender reads first.
     */
    static Map<String, Integer> hardMatchCountsByConstraint(Schedule solved) {
        SolverFactory<Schedule> scoringFactory = SolverFactory.create(new SolverConfig()
                .withSolutionClass(Schedule.class)
                .withEntityClasses(AgentShiftAssignment.class, AgentAssignment.class)
                .withScoreDirectorFactory(new ScoreDirectorFactoryConfig()
                        .withConstraintProviderClass(ScheduleConstraintProvider.class)));
        SolutionManager<Schedule, HardSoftScore> solutionManager = SolutionManager.create(scoringFactory);

        Map<String, Integer> counts = new LinkedHashMap<>();
        solutionManager.explain(solved).getConstraintMatchTotalMap().values().stream()
                .filter(total -> total.getScore().hardScore() != 0)
                .sorted(Comparator.comparingInt(ConstraintMatchTotal<HardSoftScore>::getConstraintMatchCount).reversed())
                .forEach(total -> counts.put(total.getConstraintRef().constraintName(), total.getConstraintMatchCount()));
        return counts;
    }

    // ------------------------------------------------------------------
    //  The failure reporter -- wired into every invariant assertion's .as(...) description
    // ------------------------------------------------------------------

    /**
     * Builds the multi-line failure report every invariant assertion in this class carries as its
     * AssertJ description, so whichever assertion trips first, the reader sees: the named invariant,
     * the run's reproduction parameters, the offending rows, a per-constraint violation-COUNT table,
     * and the standing instruction not to compare raw hard scores across weight changes (G-15-29).
     */
    static String buildQualityReport(String brokenInvariant, long seed, Schedule solved,
            List<SplitShift> splits, List<EdgeBreak> edgeBreaks, List<UnstaffedEdgeHour> unstaffed) {
        StringBuilder sb = new StringBuilder();
        sb.append(brokenInvariant).append(System.lineSeparator());
        sb.append("seed=").append(seed)
                .append(" stepCountLimit=").append(STEP_COUNT_LIMIT)
                .append(" agentDays=").append(solved.getShiftAssignments().size())
                .append(System.lineSeparator());
        sb.append("pinned weights: shiftEnvelopeCompliance=10hard shiftWorkContiguity=10hard "
                + "bandCapacity=1hard unassignedAssignment=10000soft").append(System.lineSeparator());

        sb.append(System.lineSeparator()).append("Offending rows:").append(System.lineSeparator());
        if (splits.isEmpty() && edgeBreaks.isEmpty() && unstaffed.isEmpty()) {
            sb.append("  (none)").append(System.lineSeparator());
        }
        for (SplitShift s : splits) {
            sb.append("  SPLIT agent=").append(s.agentId()).append(" date=").append(s.date())
                    .append(" template=").append(s.templateName()).append(" holes=").append(s.holes())
                    .append(System.lineSeparator());
        }
        for (EdgeBreak e : edgeBreaks) {
            sb.append("  EDGE_BREAK agent=").append(e.agentId()).append(" date=").append(e.date())
                    .append(" template=").append(e.templateName()).append(" break=").append(e.breakStart())
                    .append("-").append(e.breakEnd()).append(" reason=").append(e.reason())
                    .append(System.lineSeparator());
        }
        for (UnstaffedEdgeHour u : unstaffed) {
            sb.append("  UNSTAFFED_EDGE_HOUR date=").append(u.date()).append(" hour=").append(u.hour())
                    .append(" agentsWorking=").append(u.agentsWorking())
                    .append(System.lineSeparator());
        }

        sb.append(System.lineSeparator())
                .append("Edge-hour coverage matrix (agents working), the whole picture -- HANDOFF.md §8's shape:")
                .append(System.lineSeparator());
        Map<LocalDate, Map<LocalTime, Integer>> matrix =
                edgeHourCoverageMatrix(solved, LiveShapeShiftDeskFixture.EDGE_HOURS);
        sb.append("  date       ");
        for (LocalTime hour : LiveShapeShiftDeskFixture.EDGE_HOURS) {
            sb.append(String.format("%6s", hour));
        }
        sb.append(System.lineSeparator());
        matrix.forEach((date, byHour) -> {
            sb.append("  ").append(date).append(" ");
            for (LocalTime hour : LiveShapeShiftDeskFixture.EDGE_HOURS) {
                sb.append(String.format("%6d", byHour.getOrDefault(hour, 0)));
            }
            sb.append(System.lineSeparator());
        });

        sb.append(System.lineSeparator()).append("Per-constraint violation counts (never the score):")
                .append(System.lineSeparator());
        sb.append("  constraint | violations").append(System.lineSeparator());
        Map<String, Integer> hardCounts = hardMatchCountsByConstraint(solved);
        if (hardCounts.isEmpty()) {
            sb.append("  (no nonzero hard-scored constraint)").append(System.lineSeparator());
        }
        hardCounts.forEach((name, count) ->
                sb.append("  ").append(name).append(" | ").append(count).append(System.lineSeparator()));
        sb.append("  (reported hard score, CONTEXT ONLY, never asserted on directly: ")
                .append(solved.getScore()).append(")").append(System.lineSeparator());

        sb.append(System.lineSeparator())
                .append("When comparing two solver configurations, compare violation counts per constraint, ")
                .append("not raw hard scores -- hard scores are not comparable across weight changes (run U ")
                .append("scored -4 at envelope weight 1; run V scored -30 at weight 10 and was BETTER, 3 ")
                .append("violations against 4), and a single run is not evidence on this desk, where ")
                .append("byte-identical configuration produced 0 hard and -20 hard twenty minutes apart. ")
                .append("see G-15-29 in 15-UAT.md").append(System.lineSeparator());

        return sb.toString();
    }

    // ------------------------------------------------------------------
    //  Solving -- shipped config, seeded, step-count terminated (P-18, P-44)
    // ------------------------------------------------------------------

    /**
     * Solves through the real {@code solverConfig.xml} -- both construction-heuristic phases run
     * unbounded (self-terminating once every entity is placed); only the trailing local-search phase
     * is bounded, by step count, never wall-clock. {@code StepCountTermination} is phase-scoped only
     * at Timefold 1.16.0 -- a solver-level {@link TerminationConfig} throws
     * {@code UnsupportedOperationException}.
     */
    private static Schedule solve(Schedule unsolved, long seed) {
        SolverConfig solverConfig = SolverConfig.createFromXmlResource("solverConfig.xml").withRandomSeed(seed);
        List<PhaseConfig> phases = solverConfig.getPhaseConfigList();
        phases.get(phases.size() - 1)
                .setTerminationConfig(new TerminationConfig().withStepCountLimit(STEP_COUNT_LIMIT));

        SolverFactory<Schedule> solverFactory = SolverFactory.create(solverConfig);
        Solver<Schedule> solver = solverFactory.buildSolver();
        return solver.solve(unsolved);
    }
}
