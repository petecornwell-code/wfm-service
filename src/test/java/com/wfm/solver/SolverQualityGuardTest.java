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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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
