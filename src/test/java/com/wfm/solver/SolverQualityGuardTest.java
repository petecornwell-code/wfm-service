package com.wfm.solver;

import ai.timefold.solver.core.api.solver.Solver;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.phase.PhaseConfig;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;

import com.wfm.model.AgentAssignment;
import com.wfm.model.AgentShiftAssignment;
import com.wfm.model.Schedule;
import com.wfm.model.ShiftBandPair;
import com.wfm.model.ShiftTemplate;
import com.wfm.model.ShiftTemplateBreakBand;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    //  Task 1 -- the tracer: one seed, one invariant, the whole path proved end to end
    // ------------------------------------------------------------------

    @Test
    @DisplayName("live-shape desk, single seed: solves through the shipped config, and every agent-day is contiguous (INV-1)")
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
        assertThat(splits)
                .as("INV-1 SPLIT SHIFTS: every agent-day must be contiguous apart from its assigned "
                        + "break window -- offending agent-days: %s", splits)
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
        Map<String, List<AgentAssignment>> seatsByAgentDate = new HashMap<>();
        for (AgentAssignment a : solved.getAssignments()) {
            if (a.getAgent() == null) {
                continue;
            }
            String key = a.getAgent().getId() + "@" + a.getTimeslot().getDate();
            seatsByAgentDate.computeIfAbsent(key, k -> new ArrayList<>()).add(a);
        }

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
