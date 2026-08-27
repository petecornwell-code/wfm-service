package com.wfm.solver;

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.solver.Solver;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.api.solver.SolutionManager;
import ai.timefold.solver.core.config.phase.PhaseConfig;
import ai.timefold.solver.core.config.score.director.ScoreDirectorFactoryConfig;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;
import com.wfm.model.Agent;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ENVL-07 — the independent check that would have caught the spike's Option C
 * ({@code SPIKE-COUPLING.md}): Option C compiled, ran clean under {@code FULL_ASSERT}, and reported
 * {@code 0hard/0soft} on 8/8 seeds while 9-14 of 24 seats sat outside their agent's envelope. No
 * Timefold assertion mode can catch that, because {@code FULL_ASSERT} only checks score
 * consistency, never value-range validity. The only thing that catches it is an external walker.
 *
 * <p>Task 1 solves a real shift-mode fixture through the shipped {@code solverConfig.xml} (never a
 * hand-built {@code SolverConfig} — the point is to exercise the shipped two-phase construction
 * heuristic) and proves the independent walker agrees with the reported score on a clean solve.
 * Task 2 proves the walker can go red: a check that has never failed proves nothing about its
 * ability to fail.
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
    //  Task 2 -- the disagreement proof: the walker must be able to fail
    // ------------------------------------------------------------------

    @Test
    @DisplayName("case 1 -- leading edge: a seat one increment before the envelope start is flagged, exactly once")
    void leadingEdge_oneIncrementBeforeEnvelopeStart_flaggedOnce() {
        Schedule solved = solveCleanFixture();
        AgentShiftAssignment shiftRow = solved.getShiftAssignments().get(0);
        ShiftTemplate template = shiftRow.getShiftBandPair().template();

        AgentAssignment victim = seatAt(solved, shiftRow, template.getStartTime());
        LocalTime newStart = template.getStartTime().minusMinutes(ShiftModeFixtures.INCREMENT_MINUTES);
        relocateSeat(victim, shiftRow.getDate(), newStart);

        List<String> violations = findEnvelopeViolations(solved);
        assertThat(violations).hasSize(1);
        assertThat(violations.get(0))
                .contains(shiftRow.getAgent().getId().toString())
                .contains(shiftRow.getDate().toString());
    }

    @Test
    @DisplayName("case 2 -- trailing edge: starting exactly at envelope end is flagged; ending exactly at envelope end stays legal")
    void trailingEdge_startingAtEnvelopeEnd_flagged_endingAtEnvelopeEndLegal() {
        Schedule solved = solveCleanFixture();
        AgentShiftAssignment shiftRow = solved.getShiftAssignments().get(0);
        ShiftTemplate template = shiftRow.getShiftBandPair().template();

        // Relocate the FIRST slot's seat (not the natural last slot) so the natural last slot --
        // which already ends exactly at the envelope end -- stays untouched and provably legal.
        AgentAssignment victim = seatAt(solved, shiftRow, template.getStartTime());
        relocateSeat(victim, shiftRow.getDate(), template.getEndTime());

        List<String> violations = findEnvelopeViolations(solved);
        assertThat(violations)
                .as("exactly one violation -- the relocated seat -- and NOT the untouched natural last "
                        + "slot, which ends exactly at the envelope end and stays legal: the half-open "
                        + "boundary is pinned in both directions")
                .hasSize(1);
        assertThat(violations.get(0)).contains(template.getEndTime().toString());
    }

    @Test
    @DisplayName("case 3 -- inside the break: starting at break start is flagged; starting at break end is legal")
    void insideBreak_atBreakStartFlagged_atBreakEndLegal() {
        Schedule solved = solveCleanFixture();
        AgentShiftAssignment shiftRow = solved.getShiftAssignments().get(0);
        ShiftBandPair pair = shiftRow.getShiftBandPair();
        ShiftTemplate template = pair.template();
        ShiftTemplateBreakBand band = pair.band();
        LocalTime breakStart = template.getStartTime().plusMinutes(band.getOffsetMinutes());
        LocalTime breakEnd = breakStart.plusMinutes(band.getDurationMinutes());

        AgentAssignment victim = seatAt(solved, shiftRow, template.getStartTime());

        relocateSeat(victim, shiftRow.getDate(), breakStart);
        assertThat(findEnvelopeViolations(solved))
                .as("a seat starting exactly at the band's break start is forbidden")
                .hasSize(1);

        relocateSeat(victim, shiftRow.getDate(), breakEnd);
        assertThat(findEnvelopeViolations(solved))
                .as("a seat starting exactly at the band's break end is legal")
                .isEmpty();
    }

    @Test
    @DisplayName("case 4 -- the null shift: every seat that agent holds that day is flagged, not just one")
    void nullShift_flagsEverySeatThatAgentHoldsThatDay() {
        Schedule solved = solveCleanFixture();
        AgentShiftAssignment shiftRow = solved.getShiftAssignments().get(0);

        long expectedSeatCount = solved.getAssignments().stream()
                .filter(a -> a.getAgent() != null
                        && a.getAgent().getId().equals(shiftRow.getAgent().getId())
                        && a.getTimeslot().getDate().equals(shiftRow.getDate()))
                .count();
        assertThat(expectedSeatCount)
                .as("sanity: this agent must actually hold seats that day for the case to be meaningful")
                .isGreaterThan(0L);

        shiftRow.setShiftBandPair(null);

        List<String> violations = findEnvelopeViolations(solved);
        assertThat(violations).hasSize((int) expectedSeatCount);
        assertThat(violations).allMatch(v -> v.contains(shiftRow.getAgent().getId().toString()));
    }

    @Test
    @DisplayName("case 5 -- score agreement on a broken solution: the hard score is non-zero too")
    void scoreAgreesOnBrokenSolution() {
        Schedule solved = solveCleanFixture();
        AgentShiftAssignment shiftRow = solved.getShiftAssignments().get(0);
        ShiftTemplate template = shiftRow.getShiftBandPair().template();

        AgentAssignment victim = seatAt(solved, shiftRow, template.getStartTime());
        relocateSeat(victim, shiftRow.getDate(), template.getStartTime().minusMinutes(ShiftModeFixtures.INCREMENT_MINUTES));

        assertThat(findEnvelopeViolations(solved))
                .as("sanity: the walker must see the corruption before checking the score agrees")
                .hasSize(1);

        SolverFactory<Schedule> scoringFactory = SolverFactory.create(new SolverConfig()
                .withSolutionClass(Schedule.class)
                .withEntityClasses(AgentShiftAssignment.class, AgentAssignment.class)
                .withScoreDirectorFactory(new ScoreDirectorFactoryConfig()
                        .withConstraintProviderClass(ScheduleConstraintProvider.class)));
        SolutionManager<Schedule, HardSoftScore> solutionManager = SolutionManager.create(scoringFactory);
        HardSoftScore freshScore = solutionManager.update(solved);

        // The requirement is that the two agree on a BROKEN schedule being broken, not only on a
        // good one being good -- this is the assertion pair Option C would have failed.
        assertThat(freshScore.hardScore())
                .as("the walker and the reported score must agree that a broken schedule is broken")
                .isNotZero();
    }

    @Test
    @DisplayName("case 6 -- ENVL-03: specialization stays free within the shift")
    void specializationVariesWithinShift_notFlaggedByThisWalker() {
        Schedule solved = solveCleanFixture();

        boolean anyAgentHoldsBothSpecializations = solved.getAgents().stream().anyMatch(agent -> {
            Set<UUID> specIds = solved.getAssignments().stream()
                    .filter(a -> a.getAgent() != null && a.getAgent().getId().equals(agent.getId()))
                    .map(a -> a.getRequiredSpecialization().getId())
                    .collect(Collectors.toSet());
            return specIds.size() >= 2;
        });
        assertThat(anyAgentHoldsBothSpecializations)
                .as("at least one agent must hold seats requiring two different specializations within the same shift")
                .isTrue();

        assertThat(findEnvelopeViolations(solved))
                .as("the envelope walker is silent on specialization -- it never flags this")
                .isEmpty();
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

    private static Schedule solveCleanFixture() {
        Schedule solved = solve(ShiftModeFixtures.buildShiftModeSchedule(AGENT_COUNT, DAY_COUNT, TEMPLATE_COUNT, 1).schedule());
        assertThat(solved.getScore().hardScore())
                .as("the base fixture for every Task 2 case must itself be feasible before it is corrupted")
                .isZero();
        return solved;
    }

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

    private static AgentAssignment seatAt(Schedule solved, AgentShiftAssignment shiftRow, LocalTime slotStart) {
        return solved.getAssignments().stream()
                .filter(a -> a.getAgent() != null
                        && a.getAgent().getId().equals(shiftRow.getAgent().getId())
                        && a.getTimeslot().getDate().equals(shiftRow.getDate())
                        && a.getTimeslot().getStartTime().equals(slotStart))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No seat found for agent=" + shiftRow.getAgent().getId()
                        + " date=" + shiftRow.getDate() + " slotStart=" + slotStart));
    }

    /**
     * Reassigns {@code seat} to a brand-new synthetic {@link Timeslot} at {@code newStart} — never
     * mutates an existing shared {@link Timeslot} instance in place, since multiple seats (one per
     * agent) reference the SAME timeslot object at any given slot in this fixture.
     */
    private static void relocateSeat(AgentAssignment seat, LocalDate date, LocalTime newStart) {
        Timeslot moved = new Timeslot();
        moved.setId(UUID.randomUUID());
        moved.setTenantId(seat.getTenantId());
        moved.setDeskId(seat.getDeskId());
        moved.setScheduleId(seat.getScheduleId());
        moved.setDate(date);
        moved.setStartTime(newStart);
        moved.setEndTime(newStart.plusMinutes(ShiftModeFixtures.INCREMENT_MINUTES));
        seat.setTimeslot(moved);
    }
}
