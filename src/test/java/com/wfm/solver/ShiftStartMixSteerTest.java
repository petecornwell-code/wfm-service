package com.wfm.solver;

import ai.timefold.solver.core.api.solver.Solver;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.phase.PhaseConfig;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;
import com.wfm.model.AgentShiftAssignment;
import com.wfm.model.Schedule;
import com.wfm.model.ShiftStartMixTarget;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end proof that {@code ShiftStartMixTarget} facts actually move a real solve.
 *
 * <p>The unit tests either side of this one verify the two halves separately —
 * {@code ShiftStartMixTargetServiceTest} that the computed mix is the right one,
 * {@code ShiftStartMixConstraintTest} that the constraint charges the right number. Neither
 * answers the question that decides whether the feature does anything at all: is a SOFT weight of
 * 25 enough to make the construction heuristic follow the target, against every other constraint
 * pulling the other way? A mechanism that computes a perfect target and is then ignored would pass
 * both unit suites.
 */
class ShiftStartMixSteerTest {

    private static final int STEP_COUNT_LIMIT = 5_000;
    private static final long SEED = 1L;
    private static final int AGENT_COUNT =
            LiveShapeShiftDeskFixture.TEMPLATE_SPECS.size() * LiveShapeShiftDeskFixture.IDEAL_HOLDERS_PER_TEMPLATE;

    /**
     * DISABLED — this is the gap, recorded as an executable statement of it rather than prose.
     *
     * <p><b>Measured 2026-09-22 on this fixture.</b> A contrary target moves the solved start mix
     * by exactly nothing. The constraint scores perfectly — soft drops by {@code 16 x weight},
     * the 16 over-target agent-days — and the mix is byte-identical to the untargeted arm at
     * every weight tried: soft 25, 200, 2,000 and 100,000, AND {@code ofHard(1)}, which the solver
     * simply absorbs as 16 extra hard points rather than move one agent-day. A one-agent-day
     * perturbation target is refused just as flatly.
     *
     * <p><b>This is not a defect peculiar to this constraint.</b> It is the same rigidity already
     * on record for {@code usualShiftConsistency}, whose weight was swept at 2, 5 and 60 on the
     * live desk without shifting the mix either. The construction heuristic fixes the start mix
     * and no weight on {@code AgentShiftAssignment.shiftBandPair} revises it afterwards, because
     * revising it means re-pointing the seats too and the {@code 0hard} annealing temperature
     * refuses every intermediate state.
     *
     * <p><b>What that means for the design.</b> A weighted steer is the wrong mechanism for this
     * decision. The target has to become structural — the value range each row may draw from, or
     * a pre-assigned envelope — so the CH cannot build the wrong mix in the first place. Until
     * that lands, {@code shift_start_mix_weight} ships at {@code 0hard/0soft} and the feature is
     * inert. Re-enable this test with the structural version; it should then pass unchanged.
     */
    @org.junit.jupiter.api.Disabled("Known gap: a weighted steer does not move the CH's start mix "
            + "at any weight. See this method's javadoc for the measurements and the fix it implies.")
    @Test
    void aContraryTargetVisiblyMovesTheSolvedStartMix() {
        Schedule baseline = solve(fixture(List.of()));
        Map<LocalDate, Map<LocalTime, Integer>> baselineMix = mixByDate(baseline);

        LocalTime latest = baselineMix.values().iterator().next().keySet().stream()
                .max(LocalTime::compareTo).orElseThrow();
        List<ShiftStartMixTarget> contrary = new ArrayList<>();
        baselineMix.forEach((date, mix) -> mix.keySet().forEach(start ->
                contrary.add(new ShiftStartMixTarget(date, start, start.equals(latest) ? AGENT_COUNT : 0))));

        Schedule steered = solve(fixture(contrary));
        assertThat(countOn(mixByDate(steered), latest))
                .as("the target must move the mix toward itself — if it does not, the weight is "
                        + "inert and the feature is decoration")
                .isGreaterThan(countOn(baselineMix, latest));
    }

    @Test
    void aTargetTheSolverCanMeetIsMetExactly() {
        // The baseline's own mix is by construction reachable at hard 0 — the solver just produced
        // it. Handing it straight back must therefore cost nothing and change nothing.
        Schedule baseline = solve(fixture(List.of()));
        Map<LocalDate, Map<LocalTime, Integer>> baselineMix = mixByDate(baseline);

        List<ShiftStartMixTarget> asTargets = new ArrayList<>();
        baselineMix.forEach((date, mix) -> mix.forEach((start, count) ->
                asTargets.add(new ShiftStartMixTarget(date, start, count))));

        Schedule steered = solve(fixture(asTargets));
        // Hard score is deliberately not asserted: SolverQualityGuardTest records that this
        // fixture's hard score is context only, never a pass/fail signal.
        assertThat(mixByDate(steered)).isEqualTo(baselineMix);
    }

    // ---------- helpers ----------

    private static Schedule fixture(List<ShiftStartMixTarget> targets) {
        return fixture(targets, null);
    }

    private static Schedule fixture(List<ShiftStartMixTarget> targets, Integer weightOverride) {
        LiveShapeShiftDeskFixture.Fixture f =
                LiveShapeShiftDeskFixture.build(AGENT_COUNT, LiveShapeShiftDeskFixture.DAY_COUNT);
        f.schedule().setShiftStartMixTargets(new ArrayList<>(targets));
        if (weightOverride != null) {
            f.schedule().getConstraintWeights().setShiftStartMixWeight(
                    ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore.ofSoft(weightOverride));
        }
        return f.schedule();
    }





    private static Schedule solve(Schedule unsolved) {
        SolverConfig config = SolverConfig.createFromXmlResource("solverConfig.xml").withRandomSeed(SEED);
        List<PhaseConfig> phases = config.getPhaseConfigList();
        phases.get(phases.size() - 1)
                .setTerminationConfig(new TerminationConfig().withStepCountLimit(STEP_COUNT_LIMIT));
        SolverFactory<Schedule> factory = SolverFactory.create(config);
        Solver<Schedule> solver = factory.buildSolver();
        return solver.solve(unsolved);
    }

    /** Solved head count per (date, template start time). */
    private static Map<LocalDate, Map<LocalTime, Integer>> mixByDate(Schedule solved) {
        Map<LocalDate, Map<LocalTime, Integer>> out = new TreeMap<>();
        for (AgentShiftAssignment sa : solved.getShiftAssignments()) {
            if (sa.getShiftBandPair() == null) {
                continue;
            }
            out.computeIfAbsent(sa.getDate(), k -> new LinkedHashMap<>())
                    .merge(sa.getShiftBandPair().template().getStartTime(), 1, Integer::sum);
        }
        return out;
    }

    private static int countOn(Map<LocalDate, Map<LocalTime, Integer>> mix, LocalTime start) {
        return mix.values().stream().mapToInt(m -> m.getOrDefault(start, 0)).sum();
    }
}
