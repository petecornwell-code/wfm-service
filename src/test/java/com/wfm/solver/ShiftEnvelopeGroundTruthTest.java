package com.wfm.solver;

import ai.timefold.solver.core.api.solver.Solver;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.phase.PhaseConfig;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;
import com.wfm.model.Agent;
import com.wfm.model.AgentAssignment;
import com.wfm.model.AgentShiftAssignment;
import com.wfm.model.Schedule;
import com.wfm.model.ShiftBandPair;
import com.wfm.model.Timeslot;

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
 * ENVL-07 — the independent check that would have caught the spike's Option C
 * ({@code SPIKE-COUPLING.md}): Option C compiled, ran clean under {@code FULL_ASSERT}, and reported
 * {@code 0hard/0soft} on 8/8 seeds while 9-14 of 24 seats sat outside their agent's envelope. No
 * Timefold assertion mode can catch that, because {@code FULL_ASSERT} only checks score
 * consistency, never value-range validity. The only thing that catches it is an external walker.
 *
 * <p>Task 1 (this file, so far) solves a real shift-mode fixture through the shipped
 * {@code solverConfig.xml} (never a hand-built {@code SolverConfig} — the point is to exercise the
 * shipped two-phase construction heuristic) and proves the independent walker agrees with the
 * reported score on a clean solve. Task 2 will prove the walker can go red: a check that has never
 * failed proves nothing about its ability to fail.
 *
 * <p>{@link #findEnvelopeViolations} is written from raw {@link LocalTime} comparisons only (P-17)
 * — it calls no production membership helper ({@link ShiftBandPair#covers}, any constraint, or any
 * score director). Sharing a helper with the constraint it is checking would make the walker
 * inherit the constraint's own blind spot, which is exactly how Option C passed everything pointed
 * at it.
 */
class ShiftEnvelopeGroundTruthTest {

    private static final int AGENT_COUNT = 2;
    private static final int DAY_COUNT = 1;
    private static final int TEMPLATE_COUNT = 2;
    private static final int STEP_COUNT_LIMIT = 20_000;

    // ------------------------------------------------------------------
    //  Task 1 -- solve, assert non-vacuous feasibility, walk, assert agreement
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a shift-mode fixture solves feasibly through the real solverConfig.xml, and the independent walker agrees")
    void shiftModeFixture_solvesFeasibly_walkerAgrees() {
        Schedule solved = solve(ShiftModeFixtures.buildShiftModeSchedule(AGENT_COUNT, DAY_COUNT, TEMPLATE_COUNT, 1).schedule());

        assertNonVacuouslyFeasible(solved);

        assertThat(solved.getScore()).as("solved schedule must carry a score").isNotNull();
        assertThat(solved.getScore().hardScore())
                .as("a shift-mode fixture must reach 0 hard through the shipped construction heuristic + local search alone")
                .isZero();

        List<String> violations = findEnvelopeViolations(solved);
        assertThat(violations)
                .as("independent walker must find zero envelope violations: %s", violations)
                .isEmpty();

        // The plan's recorded prohibition, made mechanical: the two answers must agree in the
        // affirmative direction as well as the negative -- asserted as ONE compound claim so a
        // future change that makes one true while the other is false fails HERE, not at UAT.
        assertThat(violations.isEmpty() && solved.getScore().hardScore() == 0)
                .as("the walker and the reported score must agree that this schedule is feasible")
                .isTrue();
    }

    private void assertNonVacuouslyFeasible(Schedule solved) {
        assertThat(solved.getShiftAssignments())
                .as("solved schedule must actually carry shift rows -- a vacuous pass over zero rows proves nothing")
                .isNotEmpty();
        assertThat(solved.getShiftAssignments())
                .as("every shift row must hold a chosen pair")
                .allMatch(sa -> sa.getShiftBandPair() != null);
        assertThat(solved.getAssignments())
                .as("solved schedule must actually carry seats")
                .isNotEmpty();
        long seatedCount = solved.getAssignments().stream().filter(a -> a.getAgent() != null).count();
        assertThat(seatedCount)
                .as("at least one seat must actually be filled -- a walker over zero seats is a vacuous pass")
                .isGreaterThan(0L);
    }

    // ------------------------------------------------------------------
    //  The independent ground-truth walker (P-17) -- shares no code with the constraint it checks
    // ------------------------------------------------------------------

    /**
     * Computes envelope membership from raw {@link LocalTime} comparisons only. Deliberately does
     * NOT call {@link ShiftBandPair#covers}, {@code ScheduleConstraintProvider}, a
     * {@code SolutionManager}, or any other score director -- a walker that reuses the production
     * predicate inherits the production bug and proves nothing (P-17).
     */
    private static List<String> findEnvelopeViolations(Schedule solved) {
        record AgentDateKey(UUID agentId, LocalDate date) {}

        Map<AgentDateKey, ShiftBandPair> resolvedShift = new HashMap<>();
        for (AgentShiftAssignment sa : solved.getShiftAssignments()) {
            resolvedShift.put(new AgentDateKey(sa.getAgent().getId(), sa.getDate()), sa.getShiftBandPair());
        }

        List<String> violations = new ArrayList<>();
        for (AgentAssignment a : solved.getAssignments()) {
            Agent agent = a.getAgent();
            if (agent == null) {
                continue;
            }
            Timeslot ts = a.getTimeslot();
            ShiftBandPair pair = resolvedShift.get(new AgentDateKey(agent.getId(), ts.getDate()));

            boolean violated;
            if (pair == null) {
                violated = true;
            } else {
                LocalTime slotStart = ts.getStartTime();
                LocalTime slotEnd = ts.getEndTime();
                LocalTime envelopeStart = pair.template().getStartTime();
                LocalTime envelopeEnd = pair.template().getEndTime();

                if (slotStart.isBefore(envelopeStart) || slotEnd.isAfter(envelopeEnd)) {
                    violated = true;
                } else if (pair.band() != null) {
                    // Break interval computed as the template start plus the band offset, through
                    // plus the band duration -- raw values only, no ShiftTemplateBreakBand helper.
                    LocalTime breakStart = envelopeStart.plusMinutes(pair.band().getOffsetMinutes());
                    LocalTime breakEnd = breakStart.plusMinutes(pair.band().getDurationMinutes());
                    violated = slotStart.isBefore(breakEnd) && slotEnd.isAfter(breakStart);
                } else {
                    violated = false; // zero bands = no break (P-02) -- envelope containment is enough
                }
            }

            if (violated) {
                violations.add("agent=" + agent.getId() + " date=" + ts.getDate()
                        + " timeslot=" + ts.getStartTime() + "-" + ts.getEndTime());
            }
        }
        return violations;
    }

    // ------------------------------------------------------------------
    //  Test-only helpers
    // ------------------------------------------------------------------

    /**
     * Solves through the real {@code solverConfig.xml} (P-18) -- both construction-heuristic
     * phases run unbounded (they self-terminate once every entity is placed), and only the trailing
     * local-search phase is bounded, by step count -- never wall-clock, so the test is reproducible
     * in CI. {@code StepCountTermination} is phase-scoped only in Timefold 1.16.0 (a solver-level
     * {@code TerminationConfig} throws {@code UnsupportedOperationException}), so the termination is
     * attached to the last configured phase rather than the solver as a whole.
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
