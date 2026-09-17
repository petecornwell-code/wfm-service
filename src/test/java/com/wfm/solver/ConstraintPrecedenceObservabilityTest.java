package com.wfm.solver;

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.score.constraint.ConstraintMatchTotal;
import ai.timefold.solver.core.api.solver.Solver;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.api.solver.SolutionManager;
import ai.timefold.solver.core.config.phase.PhaseConfig;
import ai.timefold.solver.core.config.score.director.ScoreDirectorFactoryConfig;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;
import com.wfm.model.AgentPreference;
import com.wfm.model.AgentShiftAssignment;
import com.wfm.model.ResolvedUsualShiftTarget;
import com.wfm.model.Schedule;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 17 plan 17-02, Task 3 — D-10's second backend artefact: proves the precedence between
 * {@code Usual shift consistency} and {@code Preferred start (shift mode)} is OBSERVABLE in
 * {@code SolutionManager.explain()}'s score breakdown, not merely inferable from the two
 * constraints' relative weights. A single merged entry here is exactly the failure this test
 * exists to catch — it is the reason D-08 rejected a lexicographic single-constraint alternative.
 *
 * <p>Solves a real shift-mode fixture through the shipped {@code solverConfig.xml} (P-18, the same
 * convention {@link ShiftEnvelopeGroundTruthTest} established), then mutates the (post-solve, plain
 * problem-fact) {@code resolvedUsualShiftTargets} and {@code agentPreferences} lists so one agent-day
 * is simultaneously drifted from her usual shift AND away from her preferred start, and re-scores
 * through a fresh, hand-built {@link SolutionManager} (mirroring {@code
 * ShiftEnvelopeGroundTruthTest#scoreAgreesOnBrokenSolution}'s case-5 pattern) — never a second solve.
 */
class ConstraintPrecedenceObservabilityTest {

    private static final int AGENT_COUNT = 2;
    private static final int DAY_COUNT = 1;
    private static final int TEMPLATE_COUNT = 1;
    private static final int STEP_COUNT_LIMIT = 20_000;

    @Test
    @DisplayName("both constraints appear as two distinct explain() entries, each with a positive match count, when one agent-day is both drifted and off-preference")
    void bothConstraints_appearAsTwoDistinctEntriesEachWithAPositiveMatchCount() {
        Schedule solved = solve(ShiftModeFixtures.buildShiftModeSchedule(
                AGENT_COUNT, DAY_COUNT, TEMPLATE_COUNT, 1).schedule());

        assertThat(solved.getScore()).as("solved schedule must carry a score").isNotNull();
        assertThat(solved.getScore().hardScore())
                .as("the base fixture must itself be feasible before facts are mutated")
                .isZero();

        AgentShiftAssignment shiftRow = solved.getShiftAssignments().stream()
                .filter(sa -> sa.getShiftBandPair() != null)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("no assigned shift row in the solved fixture"));
        UUID agentId = shiftRow.getAgent().getId();
        LocalDate date = shiftRow.getDate();
        LocalTime assignedEnvelopeStart = shiftRow.getShiftBandPair().template().getStartTime();

        // A usual-shift target far from the assigned envelope start -- well past the default
        // 60-minute tolerance band -- so usualShiftConsistency fires for this agent-day.
        LocalTime usualStart = assignedEnvelopeStart.plusHours(4);
        solved.setResolvedUsualShiftTargets(
                List.of(new ResolvedUsualShiftTarget(agentId, date, usualStart)));

        // A preferred start one hour off the assigned envelope start -- preferredStartShiftMode
        // has no tolerance band at all, so any non-zero deviation fires it.
        AgentPreference preference = new AgentPreference();
        preference.setId(UUID.randomUUID());
        preference.setAgent(shiftRow.getAgent());
        preference.setDate(date);
        preference.setStanding(false);
        preference.setPreferredStartTime(assignedEnvelopeStart.plusHours(1));
        solved.setAgentPreferences(List.of(preference));

        SolverFactory<Schedule> explainFactory = SolverFactory.create(new SolverConfig()
                .withSolutionClass(Schedule.class)
                .withEntityClasses(AgentShiftAssignment.class, com.wfm.model.AgentAssignment.class)
                .withScoreDirectorFactory(new ScoreDirectorFactoryConfig()
                        .withConstraintProviderClass(ScheduleConstraintProvider.class)));
        SolutionManager<Schedule, HardSoftScore> solutionManager = SolutionManager.create(explainFactory);

        var explanation = solutionManager.explain(solved);
        Map<String, ConstraintMatchTotal<HardSoftScore>> totals = explanation.getConstraintMatchTotalMap();

        // The map key is "constraintPackage/constraintName" (Timefold derives constraintPackage
        // from the @PlanningSolution class's package here, com.wfm.model, since
        // ScheduleConstraintProvider names no explicit constraintPackage) -- ScheduleOutputService
        // and SolverService both read the human-readable name via getConstraintName(), never the
        // raw key, so this test matches the same way rather than assuming the key IS the bare name.
        List<ConstraintMatchTotal<HardSoftScore>> usualShiftMatches = totals.values().stream()
                .filter(total -> "Usual shift consistency".equals(total.getConstraintName()))
                .toList();
        List<ConstraintMatchTotal<HardSoftScore>> preferredStartMatches = totals.values().stream()
                .filter(total -> "Preferred start (shift mode)".equals(total.getConstraintName()))
                .toList();

        assertThat(usualShiftMatches)
                .as("exactly one 'Usual shift consistency' entry must be present")
                .hasSize(1);
        assertThat(preferredStartMatches)
                .as("exactly one 'Preferred start (shift mode)' entry must be present")
                .hasSize(1);

        ConstraintMatchTotal<HardSoftScore> usualShiftTotal = usualShiftMatches.get(0);
        ConstraintMatchTotal<HardSoftScore> preferredStartTotal = preferredStartMatches.get(0);

        assertThat(usualShiftTotal)
                .as("the two constraints must be two DISTINCT map entries, never merged into one")
                .isNotSameAs(preferredStartTotal);

        assertThat(usualShiftTotal.getConstraintMatchCount())
                .as("Usual shift consistency must have fired at least once")
                .isPositive();
        assertThat(preferredStartTotal.getConstraintMatchCount())
                .as("Preferred start (shift mode) must have fired at least once")
                .isPositive();
    }

    /**
     * Solves through the real {@code solverConfig.xml} (P-18) — copied from {@link
     * ShiftEnvelopeGroundTruthTest#solve}, this test's own solve step needs no D-08 phase-reordering
     * behaviour, only a feasible starting fixture to mutate afterward.
     */
    private static Schedule solve(Schedule unsolved) {
        SolverConfig solverConfig = SolverConfig.createFromXmlResource("solverConfig.xml");
        List<PhaseConfig> phases = solverConfig.getPhaseConfigList();
        phases.get(phases.size() - 1)
                .setTerminationConfig(new TerminationConfig().withStepCountLimit(STEP_COUNT_LIMIT));

        SolverFactory<Schedule> solverFactory = SolverFactory.create(solverConfig);
        Solver<Schedule> solver = solverFactory.buildSolver();
        return solver.solve(unsolved);
    }
}
