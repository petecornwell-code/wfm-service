package com.wfm.solver;

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.score.constraint.ConstraintMatchTotal;
import ai.timefold.solver.core.api.solver.SolutionManager;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.score.director.ScoreDirectorFactoryConfig;
import ai.timefold.solver.core.config.solver.SolverConfig;

import com.wfm.model.AgentAssignment;
import com.wfm.model.AgentShiftAssignment;
import com.wfm.model.Schedule;
import com.wfm.model.ShiftBandPair;
import com.wfm.support.AssertsTodaysBehaviour;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BDAY-06's constraint-level midnight-boundary regression suite. Every scenario is pinned by
 * {@link MidnightBoundaryFixture} -- the optimiser is never run anywhere in this class. A
 * {@link SolutionManager} scores a hand-built {@link Schedule} via {@code update}/{@code explain}
 * only, which is pure evaluation: the score function is deterministic where the optimiser is not,
 * and that is the entire reason an exact per-constraint match count is assertable here at all.
 *
 * <p>Every expected count below is a named constant or an inline integer with a comment beside it
 * arguing the number from {@link ScheduleConstraintProvider}'s own constraint definitions and the
 * scenario's pinned facts -- never a value obtained by running this test and copying its output.
 */
class MidnightBoundaryRegressionTest {

    private static SolutionManager<Schedule, HardSoftScore> newSolutionManager() {
        SolverFactory<Schedule> factory = SolverFactory.create(new SolverConfig()
                .withSolutionClass(Schedule.class)
                .withEntityClasses(AgentShiftAssignment.class, AgentAssignment.class)
                .withScoreDirectorFactory(new ScoreDirectorFactoryConfig()
                        .withConstraintProviderClass(ScheduleConstraintProvider.class)));
        return SolutionManager.create(factory);
    }

    /**
     * Asserts {@code constraintName} is present in {@code schedule}'s explanation before reading
     * its match count -- a count read against an absent constraint name would otherwise pass
     * vacuously, since a constraint that matched nothing looks identical to one that was never
     * looked up at all.
     */
    private static ConstraintMatchTotal<HardSoftScore> requireConstraint(
            SolutionManager<Schedule, HardSoftScore> solutionManager, Schedule schedule, String constraintName) {
        List<ConstraintMatchTotal<HardSoftScore>> matches = solutionManager.explain(schedule)
                .getConstraintMatchTotalMap().values().stream()
                // The map key carries a constraintPackage prefix -- match on getConstraintName(),
                // never the raw key (BDAY-06).
                .filter(total -> constraintName.equals(total.getConstraintName()))
                .toList();
        assertThat(matches)
                .as("constraint '%s' must be present in the explanation before its match count is read",
                        constraintName)
                .hasSize(1);
        return matches.get(0);
    }

    @Nested
    @DisplayName("evaluation performs no search")
    class NoSearchDuringEvaluation {

        @Test
        @DisplayName("scoring a deliberately sub-optimal pinned schedule mutates no planning variable")
        void scoringMutatesNoPlanningVariable() {
            Schedule schedule = MidnightBoundaryFixture.midnightCoverageScenario();
            List<AgentAssignment> assignments = schedule.getAssignments();
            AgentAssignment secondSeat = assignments.get(1);

            // Deliberately pin a sub-optimal state: unassign the final slot's agent. A real search
            // would have something to improve here -- Minimum staffing wants an agent on every
            // timeslot, so a solve would move an agent back onto this seat. Pure evaluation must
            // not.
            secondSeat.setAgent(null);

            UUID firstSeatAgentIdBefore = assignments.get(0).getAgent().getId();
            java.util.List<UUID> shiftBandPairTemplateIdsBefore = schedule.getShiftAssignments().stream()
                    .map(sa -> sa.getShiftBandPair() == null ? null : sa.getShiftBandPair().template().getId())
                    .toList();

            SolutionManager<Schedule, HardSoftScore> solutionManager = newSolutionManager();
            solutionManager.update(schedule);

            assertThat(assignments.get(0).getAgent().getId())
                    .as("the untouched seat's pinned agent must be unchanged after scoring")
                    .isEqualTo(firstSeatAgentIdBefore);
            assertThat(assignments.get(1).getAgent())
                    .as("the deliberately unassigned seat must STAY unassigned after scoring -- a "
                            + "search would have reassigned it")
                    .isNull();
            assertThat(schedule.getShiftAssignments().stream()
                    .map(sa -> sa.getShiftBandPair() == null ? null : sa.getShiftBandPair().template().getId())
                    .toList())
                    .as("every shift-row band-pair choice must be unchanged after scoring")
                    .isEqualTo(shiftBandPairTemplateIdsBefore);
        }
    }

    @Nested
    @DisplayName("a 23:00-to-00:00 timeslot scores like any other slot")
    class MidnightCoverage {

        @Test
        @DisplayName("fully staffed: Minimum staffing and One assignment per timeslot both read zero, and the total score is exactly zero")
        void fullyStaffed_bothCountsZero_totalScoreZero() {
            Schedule schedule = MidnightBoundaryFixture.midnightCoverageScenario();
            SolutionManager<Schedule, HardSoftScore> solutionManager = newSolutionManager();
            HardSoftScore score = solutionManager.update(schedule);

            // Argued: every one of the 26 constraints this provider defines either finds no tuple
            // at all (every SHIFT-only constraint, since this scenario carries zero
            // AgentShiftAssignment rows) or finds a tuple that is satisfied by construction (each
            // of the two demanded seats holds exactly the one agent the deterministic pinning rule
            // assigned it, each agent's one contracted slot exactly matches its one assigned slot,
            // both agents' primary specialization matches the seat's required one, and no
            // AgentDayOff row exists anywhere in this schedule). Minimum staffing's floor of one
            // agent per timeslot is met by both slots.
            requireConstraint(solutionManager, schedule, "Minimum staffing");
            assertThat(requireConstraint(solutionManager, schedule, "Minimum staffing").getConstraintMatchCount())
                    .isEqualTo(0);
            assertThat(requireConstraint(solutionManager, schedule, "One assignment per timeslot")
                    .getConstraintMatchCount())
                    .isEqualTo(0);
            assertThat(score)
                    .as("every constraint reads zero by construction, so the total score is exactly zero")
                    .isEqualTo(HardSoftScore.ZERO);
        }

        @Test
        @DisplayName("removing the pinned agent from the final (00:00-ending) slot changes Minimum staffing by exactly one")
        void removingFinalSlotAgent_minimumStaffingChangesByOne() {
            Schedule baseline = MidnightBoundaryFixture.midnightCoverageScenario();
            SolutionManager<Schedule, HardSoftScore> solutionManager = newSolutionManager();
            int baselineCount = requireConstraint(solutionManager, baseline, "Minimum staffing")
                    .getConstraintMatchCount();

            Schedule mutated = MidnightBoundaryFixture.midnightCoverageScenario();
            AgentAssignment finalSlotSeat = mutated.getAssignments().get(1);
            assertThat(finalSlotSeat.getTimeslot().getEndTime())
                    .as("the mutated seat must be the slot whose end is exactly the end of the day")
                    .isEqualTo(java.time.LocalTime.MIDNIGHT);
            finalSlotSeat.setAgent(null);

            // Argued: removing the sole seat on the 23:00-00:00 slot drops its assigned-agent count
            // from one to zero, below the floor of one -- this is the end-of-day slot being
            // correctly INCLUDED in coverage, the assertion a raw (non-DayWindow) comparison would
            // have silently failed to make.
            int mutatedCount = requireConstraint(solutionManager, mutated, "Minimum staffing")
                    .getConstraintMatchCount();
            assertThat(mutatedCount - baselineCount)
                    .as("removing the final slot's agent must change Minimum staffing by exactly one")
                    .isEqualTo(1);
            assertThat(baselineCount).isEqualTo(0);
            assertThat(mutatedCount).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("a break band flush to an envelope edge")
    class BreakBandFlushToEnvelopeEnd {

        @Test
        @DisplayName("Break duration, Break start alignment, Band capacity and Shift envelope compliance all read zero")
        void flushBand_everyRelevantConstraintReadsZero() {
            Schedule schedule = MidnightBoundaryFixture.breakBandFlushToEnvelopeEndScenario();
            SolutionManager<Schedule, HardSoftScore> solutionManager = newSolutionManager();

            // Argued, all four: Break duration/Break start alignment/Band capacity are
            // ifExists-gated to a schedule whose scheduling mode is NOT shift -- this scenario's
            // mode is SHIFT, so every tuple entering that gate is dropped and each constraint is
            // structurally inert (zero matches), regardless of the band's own geometry. Shift
            // envelope compliance IS active in shift mode, and reads zero because the sole shift
            // row's assigned band-pair covers every one of the eight demanded, non-break seats --
            // none of them overlaps the 23:00-00:00 break window.
            assertThat(requireConstraint(solutionManager, schedule, "Break duration").getConstraintMatchCount())
                    .isEqualTo(0);
            assertThat(requireConstraint(solutionManager, schedule, "Break start alignment")
                    .getConstraintMatchCount())
                    .isEqualTo(0);
            assertThat(requireConstraint(solutionManager, schedule, "Band capacity").getConstraintMatchCount())
                    .isEqualTo(0);
            assertThat(requireConstraint(solutionManager, schedule,
                            ScheduleConstraintProvider.SHIFT_ENVELOPE_COMPLIANCE_CONSTRAINT_NAME)
                    .getConstraintMatchCount())
                    .isEqualTo(0);
        }

        @Test
        @DisplayName("moving the band one minute off the flush boundary changes Shift envelope compliance by exactly one")
        void bandOneMinuteOffFlush_envelopeComplianceChangesByOne() {
            // The flush position (offset 480, duration 60) is already the LATEST valid offset for
            // this 540-minute envelope -- offset 480 + duration 60 == 540 exactly. Moving it any
            // later would push its break past the envelope's own end, which this data model cannot
            // represent (DayWindow.plusWithinDay throws outside [0, 1440]); that is exactly the
            // save-time refusal MidnightBoundaryPropertyTest proves directly against
            // ShiftTemplateService. The representable one-step-off-the-boundary direction for a
            // PURE EVALUATION is therefore one minute EARLIER (offset 479) -- still the
            // one-step-either-side half of the same boundary edge, approached from the only side
            // this model can express without throwing.
            Schedule schedule = MidnightBoundaryFixture.breakBandFlushToEnvelopeEndScenario();
            SolutionManager<Schedule, HardSoftScore> solutionManager = newSolutionManager();
            int flushCount = requireConstraint(solutionManager, schedule,
                    ScheduleConstraintProvider.SHIFT_ENVELOPE_COMPLIANCE_CONSTRAINT_NAME)
                    .getConstraintMatchCount();

            ShiftBandPair pair = schedule.getShiftBandPairs().get(0);
            pair.band().setOffsetMinutes(479);

            // Argued: at offset 479 the break window becomes [22:59, 23:59) -- it now OVERLAPS the
            // 22:00-23:00 seat (their intervals share [22:59, 23:00)), so that seat's shift-band
            // pair no longer covers it. Exactly one of the eight demanded seats is affected.
            int movedCount = requireConstraint(solutionManager, schedule,
                    ScheduleConstraintProvider.SHIFT_ENVELOPE_COMPLIANCE_CONSTRAINT_NAME)
                    .getConstraintMatchCount();
            assertThat(movedCount - flushCount)
                    .as("moving the band one minute off the flush boundary must change Shift "
                            + "envelope compliance by exactly one")
                    .isEqualTo(1);
            assertThat(flushCount).isEqualTo(0);
            assertThat(movedCount).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("PTO on the starting versus the following calendar day")
    class PtoOnAdjacentCalendarDate {

        @Test
        @AssertsTodaysBehaviour(flippedBy = "OVNT-03",
                to = "a day-off record attributes to the business day a midnight-spanning shift "
                        + "starts on, not to each individually stamped calendar date")
        @DisplayName("today: attribution is per calendar date only, not per business day")
        void attributionIsPerCalendarDateOnly() {
            SolutionManager<Schedule, HardSoftScore> solutionManager = newSolutionManager();

            // Argued: all three of this agent-day's seats (21:00-22:00, 22:00-23:00, 23:00-00:00)
            // are stamped with the SAME calendar date the day-off record itself is dated, so all
            // three (agent, date) joins against that one AgentDayOff row match -- three tuples.
            Schedule onStartingDate = MidnightBoundaryFixture.ptoOnShiftStartingDateScenario();
            assertThat(requireConstraint(solutionManager, onStartingDate, "Agent day off")
                    .getConstraintMatchCount())
                    .isEqualTo(3);

            // Argued: every seat is stamped with the shift's own starting calendar date, never the
            // following one -- today's attribution is per calendar date only, so a day-off record
            // dated the day after never reaches a seat dated the day before, no matter how close to
            // midnight that seat's times run. This is the property OVNT-03 changes.
            Schedule onFollowingDate = MidnightBoundaryFixture.ptoOnFollowingDateScenario();
            assertThat(requireConstraint(solutionManager, onFollowingDate, "Agent day off")
                    .getConstraintMatchCount())
                    .isEqualTo(0);
        }
    }
}
