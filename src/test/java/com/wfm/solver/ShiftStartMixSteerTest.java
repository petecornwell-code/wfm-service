package com.wfm.solver;

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.solver.Solver;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.phase.PhaseConfig;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;
import com.wfm.model.AgentShiftAssignment;
import com.wfm.model.Schedule;
import com.wfm.model.ShiftStartMixTarget;
import com.wfm.service.ShiftStartMixAllocator;
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
 * The end-to-end question neither unit suite answers: does a computed mix target actually reach
 * the schedule?
 *
 * <p>{@code ShiftStartMixTargetServiceTest} proves the target is the right one and
 * {@code ShiftStartMixConstraintTest} proves the constraint charges the right number. A mechanism
 * that computed a perfect target and was then ignored would pass both — which is exactly what
 * happened to the weighted steer, and is why {@link #reportModeAloneDoesNotMoveTheMix} is written
 * as an assertion rather than left as a comment.
 */
class ShiftStartMixSteerTest {

    private static final int STEP_COUNT_LIMIT = 5_000;
    private static final long SEED = 1L;
    private static final int AGENT_COUNT =
            LiveShapeShiftDeskFixture.TEMPLATE_SPECS.size() * LiveShapeShiftDeskFixture.IDEAL_HOLDERS_PER_TEMPLATE;

    @Test
    void enforceMakesTheSolvedMixEqualTheTargetExactly() {
        Schedule baseline = solve(build(List.of(), false, null));
        Map<LocalDate, Map<LocalTime, Integer>> baselineMix = mixByDate(baseline);

        // Deliberately a mix the desk should not want: everything on the latest start. If the
        // solved mix comes back equal to THIS, the target is binding structurally rather than
        // being weighed against the rest of the constraint set — which is the entire claim.
        LocalTime latest = baselineMix.values().iterator().next().keySet().stream()
                .max(LocalTime::compareTo).orElseThrow();
        List<ShiftStartMixTarget> contrary = new ArrayList<>();
        baselineMix.forEach((date, mix) -> mix.keySet().forEach(start ->
                contrary.add(new ShiftStartMixTarget(date, start, start.equals(latest) ? AGENT_COUNT : 0))));

        Schedule enforced = solve(build(contrary, true, null));
        Map<LocalDate, Map<LocalTime, Integer>> enforcedMix = mixByDate(enforced);

        assertThat(enforcedMix).isNotEqualTo(baselineMix);
        assertThat(enforcedMix.keySet()).isEqualTo(baselineMix.keySet());
        enforcedMix.forEach((date, mix) -> assertThat(mix)
                .as("every agent-day on %s must sit on the targeted start", date)
                .isEqualTo(Map.of(latest, AGENT_COUNT)));
    }

    @Test
    void enforceHandsEveryAgentDayAnEnvelopeItIsStillEligibleFor() {
        // Narrowing FILTERS the value range, never replaces it, so a narrowed row must never be
        // handed an envelope the unnarrowed rules would have refused — and must never be left
        // unassigned, which is how an over-narrowed range fails (shiftBandPair is
        // allowsUnassigned = true, so an empty range is silent).
        Schedule baseline = solve(build(List.of(), false, null));
        List<ShiftStartMixTarget> asTargets = new ArrayList<>();
        mixByDate(baseline).forEach((date, mix) -> mix.forEach((start, count) ->
                asTargets.add(new ShiftStartMixTarget(date, start, count))));

        Schedule enforced = solve(build(asTargets, true, null));
        assertThat(enforced.getShiftAssignments())
                .allSatisfy(sa -> assertThat(sa.getShiftBandPair()).isNotNull());
        assertThat(enforced.getShiftAssignments())
                .allSatisfy(sa -> assertThat(sa.getAllocatedShiftBandPairs()).contains(sa.getShiftBandPair()));
    }

    @Test
    void reportModeAloneDoesNotMoveTheMix() {
        // Locks in the measurement that made narrowing necessary. A contrary target priced at
        // 5,000 soft per over-target agent-day leaves the solved mix untouched: the CH fixes the
        // mix and nothing afterwards revises it, because revising it means re-pointing an envelope
        // AND its seats while the 0hard annealing temperature refuses every intermediate state.
        // If this ever starts failing, a weighted steer has become viable and ENFORCE's existence
        // should be revisited.
        Schedule baseline = solve(build(List.of(), false, null));
        Map<LocalDate, Map<LocalTime, Integer>> baselineMix = mixByDate(baseline);

        LocalTime latest = baselineMix.values().iterator().next().keySet().stream()
                .max(LocalTime::compareTo).orElseThrow();
        List<ShiftStartMixTarget> contrary = new ArrayList<>();
        baselineMix.forEach((date, mix) -> mix.keySet().forEach(start ->
                contrary.add(new ShiftStartMixTarget(date, start, start.equals(latest) ? AGENT_COUNT : 0))));

        Schedule reported = solve(build(contrary, false, 5_000));
        assertThat(mixByDate(reported)).isEqualTo(baselineMix);
    }

    // ---------- helpers ----------

    private static Schedule build(List<ShiftStartMixTarget> targets, boolean enforce, Integer weightOverride) {
        LiveShapeShiftDeskFixture.Fixture f =
                LiveShapeShiftDeskFixture.build(AGENT_COUNT, LiveShapeShiftDeskFixture.DAY_COUNT);
        Schedule schedule = f.schedule();
        schedule.setShiftStartMixTargets(new ArrayList<>(targets));
        if (weightOverride != null) {
            schedule.getConstraintWeights().setShiftStartMixWeight(HardSoftScore.ofSoft(weightOverride));
        }
        if (enforce) {
            ShiftStartMixAllocator.Allocation allocation = new ShiftStartMixAllocator().allocate(
                    schedule.getShiftAssignments(), targets, schedule.getResolvedUsualShiftTargets());
            assertThat(allocation.applied())
                    .as("allocation must apply, else the test below would pass for the wrong reason: %s",
                            allocation.skippedReason())
                    .isTrue();
        }
        return schedule;
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
}
