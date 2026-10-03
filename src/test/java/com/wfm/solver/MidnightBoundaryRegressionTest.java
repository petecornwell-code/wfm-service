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
import com.wfm.model.Timeslot;
import com.wfm.util.DayWindow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
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

    /**
     * OVNT-03 (plan 21-06, Task 3): the day-off join already resolves business date
     * ({@code ScheduleConstraintProvider.agentDayOff} joins on
     * {@code a.getTimeslot().getBusinessDate()}, not calendar date) -- this class proves that
     * directly against a genuinely midnight-crossing agent-day rather than against a shift whose
     * seats happen to share one calendar date. This is proof work, not a flip of solver
     * behaviour: {@code agentDayOff}'s join itself is unchanged by this task.
     */
    @Nested
    @DisplayName("a day-off record attributes to the business day a midnight-spanning shift starts on")
    class DayOffAttributesToStartingBusinessDate {

        @Test
        @DisplayName("blocks every seat of the shift, including the ones stamped with the following calendar date")
        void dayOffOnStartingBusinessDate_blocksEverySeatIncludingTheFollowingCalendarDate() {
            SolutionManager<Schedule, HardSoftScore> solutionManager = newSolutionManager();

            // Argued: every one of this agent-day's eight seats (22:00-23:00 through 05:00-06:00)
            // resolves to the SAME business date (the first two slots carry calendar date
            // BASE_DATE, the remaining six carry BASE_DATE.plusDays(1), but all eight share one
            // business date, BASE_DATE) -- a day-off record dated that one business date matches
            // every seat via the business-date join, including the six stamped with the
            // FOLLOWING calendar date.
            Schedule onStartingBusinessDate =
                    MidnightBoundaryFixture.dayOffOnCrossingShiftStartingBusinessDateScenario();
            assertThat(requireConstraint(solutionManager, onStartingBusinessDate, "Agent day off")
                    .getConstraintMatchCount())
                    .as("a day-off on the shift's starting business date must block every one of "
                            + "its eight seats, including the six stamped with the following "
                            + "calendar date")
                    .isEqualTo(8);

            // Argued, the complement that keeps the above from passing for the wrong reason: a
            // day-off record dated the NEXT business date matches none of the same eight seats --
            // a join that matched every date would satisfy the first assertion too.
            Schedule onFollowingBusinessDate =
                    MidnightBoundaryFixture.dayOffOnCrossingShiftFollowingBusinessDateScenario();
            assertThat(requireConstraint(solutionManager, onFollowingBusinessDate, "Agent day off")
                    .getConstraintMatchCount())
                    .as("a day-off on the NEXT business date must match none of this agent-day's seats")
                    .isEqualTo(0);
        }
    }

    /**
     * SOLV-03 (D-11): the phase's tracer. Every assertion below is argued against what the
     * MIGRATED solver (plan 20-05's join and anchor migration) must produce -- never copied from
     * this test's own output -- so a RED result here is falsifiability evidence, not a
     * self-fulfilling baseline. At the commit these assertions were written against, BEFORE plan
     * 20-05's migration landed, the solver ran every interval on {@code
     * ScheduleConstraintProvider.PENDING_DESK_ANCHOR} (a hardcoded midnight anchor) and joined
     * {@code AgentShiftAssignment}/{@code Timeslot} pairs on CALENDAR date rather than business
     * date, so a 21:00-anchored scenario was scored wrong in both dimensions at once. The
     * migration has since landed (SOLV-01, SOLV-03), and both are gone from the solver.
     */
    @Nested
    @DisplayName("SOLV-03: assertions at a 21:00 (non-midnight) anchor")
    class NonMidnightAnchor {

        @Test
        @DisplayName("envelope at a non-midnight anchor: re-anchoring alone changes nothing about which seats a band covers")
        void envelopeComplianceAtNonMidnightAnchor_matchesMidnightAnchorGeometry() {
            SolutionManager<Schedule, HardSoftScore> solutionManager = newSolutionManager();

            // The 00:00-anchor baseline: moving the flush band one minute off-boundary creates
            // exactly one violation (proven directly by bandOneMinuteOffFlush_envelopeComplianceChangesByOne
            // above). Re-deriving it here, independently, is the comparison point for the 21:00 case.
            Schedule midnightSchedule = MidnightBoundaryFixture.breakBandFlushToEnvelopeEndScenario();
            midnightSchedule.getShiftBandPairs().get(0).band().setOffsetMinutes(479);
            int midnightMovedCount = requireConstraint(solutionManager, midnightSchedule,
                    ScheduleConstraintProvider.SHIFT_ENVELOPE_COMPLIANCE_CONSTRAINT_NAME)
                    .getConstraintMatchCount();
            assertThat(midnightMovedCount)
                    .as("00:00-anchor baseline: moving the band one minute off flush must violate exactly one seat")
                    .isEqualTo(1);

            // Argued: the SAME relative geometry (the band one increment off its flush position),
            // re-anchored to 21:00, must ALSO violate exactly one seat -- re-anchoring alone
            // changes nothing about which seats a band covers (SOLV-03's must_haves.truths).
            Schedule ninePmSchedule = MidnightBoundaryFixture.ninePmBreakBandFlushToEnvelopeEndScenario();
            ninePmSchedule.getShiftBandPairs().get(0).band().setOffsetMinutes(479);
            int ninePmMovedCount = requireConstraint(solutionManager, ninePmSchedule,
                    ScheduleConstraintProvider.SHIFT_ENVELOPE_COMPLIANCE_CONSTRAINT_NAME)
                    .getConstraintMatchCount();
            assertThat(ninePmMovedCount)
                    .as("21:00-anchor: moving the band one minute off flush must ALSO violate exactly one "
                            + "seat, matching the 00:00-anchor count -- before plan 20-05's migration "
                            + "landed, the solver joined AgentShiftAssignment/Timeslot pairs on calendar "
                            + "date, which silently dropped every seat on this scenario's business date "
                            + "from the join")
                    .isEqualTo(midnightMovedCount);
        }

        @Test
        @DisplayName("band flush to the 21:00 anchor: legal at the flush position, violated one increment later")
        void bandFlushToNonMidnightAnchor_everyRelevantConstraintReadsZero() {
            Schedule schedule = MidnightBoundaryFixture.ninePmBreakBandFlushToEnvelopeEndScenario();
            SolutionManager<Schedule, HardSoftScore> solutionManager = newSolutionManager();

            // Argued, both: Break duration/Break start alignment are ifExists-gated to a schedule
            // whose scheduling mode is NOT shift -- this scenario's mode is SHIFT, so every tuple
            // entering that gate is dropped and each constraint is structurally inert (zero
            // matches), regardless of the band's own geometry or the desk's anchor.
            assertThat(requireConstraint(solutionManager, schedule, "Break duration").getConstraintMatchCount())
                    .isEqualTo(0);
            assertThat(requireConstraint(solutionManager, schedule, "Break start alignment")
                    .getConstraintMatchCount())
                    .isEqualTo(0);

            int flushCount = requireConstraint(solutionManager, schedule,
                    ScheduleConstraintProvider.SHIFT_ENVELOPE_COMPLIANCE_CONSTRAINT_NAME)
                    .getConstraintMatchCount();
            assertThat(flushCount)
                    .as("flush to the 21:00 envelope end must be legal -- zero violated seats")
                    .isEqualTo(0);

            ShiftBandPair pair = schedule.getShiftBandPairs().get(0);
            pair.band().setOffsetMinutes(479);
            int movedCount = requireConstraint(solutionManager, schedule,
                    ScheduleConstraintProvider.SHIFT_ENVELOPE_COMPLIANCE_CONSTRAINT_NAME)
                    .getConstraintMatchCount();
            assertThat(movedCount)
                    .as("moving the band one minute off the 21:00 flush boundary must violate exactly one seat")
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("an agent-day deep in its business day's tail reads its true hole count, not zero")
        void earlyMorningAgentDay_readsItsTrueHoleCount() {
            Schedule schedule = MidnightBoundaryFixture.ninePmOvernightContiguityScenario();
            SolutionManager<Schedule, HardSoftScore> solutionManager = newSolutionManager();

            // Argued: four worked seats (02:00-03:00, 03:00-04:00, 05:00-06:00, 07:00-08:00) with
            // two one-hour gaps (04:00-05:00, 06:00-07:00) and no break band to explain either --
            // shiftWorkContiguity's "at most one interior gap is free" fallback exempts ONE gap and
            // penalises the other, so a correctly migrated solver (business-date join, SOLV-03)
            // reads exactly one hole here. Before plan 20-05's migration landed, the solver joined
            // AgentShiftAssignment/Timeslot pairs on CALENDAR date: this agent-day's business date
            // is BASE_DATE, but every one of its timeslots carries calendar date
            // BASE_DATE.plusDays(1), so NONE of the four worked seats ever joined this row -- it
            // dropped out of the constraint's grouping entirely and read zero, not one (a silent
            // non-join, not a wrong number).
            assertThat(requireConstraint(solutionManager, schedule, "Shift work contiguity")
                    .getConstraintMatchCount())
                    .as("an agent-day whose timeslots fall entirely in its business day's "
                            + "calendar-date-shifted tail must still read its true hole count (one, "
                            + "per the fallback's 'at most one free gap' rule) -- before plan 20-05's "
                            + "migration landed, a calendar-date join silently dropped every one of "
                            + "this agent-day's seats, reading zero instead")
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("adjacency, null and ordering edges at the 21:00 anchor")
        void adjacencyNullAndOrderingEdges() {
            // Edge, adjacency at the anchor (must_haves.truths, SOLV-01): equal-to-the-anchor in a
            // START position belongs to the NEW business day; equal-to-the-anchor in an END
            // position closes the OLD one. Read directly off ninePmCoverageScenario's own
            // adjacency-only timeslots, never hand-computed here.
            Schedule schedule = MidnightBoundaryFixture.ninePmCoverageScenario();
            LocalTime anchor = LocalTime.of(21, 0);
            List<Timeslot> timeslots = schedule.getTimeslots();

            Timeslot flushToAnchorEnd = timeslots.stream()
                    .filter(ts -> ts.getStartTime().equals(LocalTime.of(20, 45)) && ts.getEndTime().equals(anchor))
                    .findFirst().orElseThrow();
            Timeslot flushToAnchorStart = timeslots.stream()
                    .filter(ts -> ts.getStartTime().equals(anchor) && ts.getEndTime().equals(LocalTime.of(21, 15)))
                    .findFirst().orElseThrow();

            assertThat(flushToAnchorEnd.getBusinessDate())
                    .as("a 20:45-21:00 slot's business date is exactly one day before its calendar date")
                    .isEqualTo(flushToAnchorEnd.getDate().minusDays(1));
            assertThat(flushToAnchorStart.getBusinessDate())
                    .as("a 21:00-21:15 slot's business date equals its calendar date")
                    .isEqualTo(flushToAnchorStart.getDate());
            assertThat(flushToAnchorStart.getBusinessDate())
                    .as("adjacency at the anchor separates, it does not merge -- the two slots on either "
                            + "side of the anchor land one business day apart")
                    .isEqualTo(flushToAnchorEnd.getBusinessDate().plusDays(1));

            // Direct DayWindow check, independent of any fixture construction helper.
            LocalDate calendarDate = LocalDate.of(2026, 1, 5);
            assertThat(DayWindow.businessDateOf(anchor, calendarDate, LocalTime.of(20, 45)))
                    .isEqualTo(calendarDate.minusDays(1));
            assertThat(DayWindow.businessDateOf(anchor, calendarDate, LocalTime.of(21, 0)))
                    .isEqualTo(calendarDate);

            // Edge, empty/null (must_haves.truths, SOLV-01): every timeslot in every 21:00
            // scenario has a non-null businessDate.
            for (Schedule s : List.of(MidnightBoundaryFixture.ninePmCoverageScenario(),
                    MidnightBoundaryFixture.ninePmBreakBandFlushToEnvelopeEndScenario(),
                    MidnightBoundaryFixture.ninePmOvernightContiguityScenario())) {
                assertThat(s.getTimeslots())
                        .as("every timeslot in a 21:00 scenario must carry a non-null businessDate")
                        .allSatisfy(ts -> assertThat(ts.getBusinessDate()).isNotNull());
            }

            // Edge, ordering (must_haves.truths, SOLV-01): the pinning comparator remains total,
            // so a second build of the same scenario produces an identical assignment set.
            Schedule first = MidnightBoundaryFixture.ninePmCoverageScenario();
            Schedule second = MidnightBoundaryFixture.ninePmCoverageScenario();
            List<UUID> firstAgentOrder = first.getAssignments().stream()
                    .map(a -> a.getAgent() == null ? null : a.getAgent().getId()).toList();
            List<UUID> secondAgentOrder = second.getAssignments().stream()
                    .map(a -> a.getAgent() == null ? null : a.getAgent().getId()).toList();
            assertThat(secondAgentOrder)
                    .as("a second build of the same scenario must produce an identical assignment set")
                    .isEqualTo(firstAgentOrder);
        }
    }

    /**
     * OVNT-02 (plan 21-06, Task 1): the genuinely midnight-crossing scenario Phase 18 deferred.
     * Tests 1 and 2 are expected to FAIL until Task 2 anchors the three clock-ordered first/last
     * reads in {@link ScheduleConstraintProvider} -- the proof that the fix is load-bearing, not
     * decorative. Tests 3 and 4 are green throughout: Test 3 is a structural property this
     * scenario's construction already satisfies, and Test 4 is the no-op control for Task 2's
     * fix, asserted against pre-existing scenarios whose assigned starts never cross the anchor.
     */
    @Nested
    @DisplayName("OVNT-02: a genuinely midnight-crossing shift (22:00-06:00 envelope at a 21:00 anchor)")
    class ShiftCrossingMidnightContiguity {

        private static final DayWindow NINE_PM_ANCHOR = DayWindow.anchoredAt(LocalTime.of(21, 0));

        @Test
        @DisplayName("the contiguity constraint reports the agent-day's one interior gap, not zero")
        void reportsItsOneInteriorGap() {
            Schedule schedule = MidnightBoundaryFixture.ninePmShiftCrossingMidnight();
            SolutionManager<Schedule, HardSoftScore> solutionManager = newSolutionManager();

            // Argued: this scenario is constructed with exactly one interior gap (03:00-04:00)
            // strictly between its first worked seat (22:00) and its last (05:00), with one break
            // band (00:00-01:00) explained by the assigned ShiftBandPair -- shiftWorkContiguity's
            // break-aware path must therefore report exactly one hole. Today it reports zero: the
            // worked set's clock-ordered first() (01:00, anchored minute 240) sorts AFTER its
            // clock-ordered last() (23:00, anchored minute 120), so the scan range [240, 120) is
            // empty and the gap at 03:00 is never visited (P-02).
            assertThat(requireConstraint(solutionManager, schedule, "Shift work contiguity")
                    .getConstraintMatchCount())
                    .as("the agent-day's one genuine interior gap must be counted, not silently "
                            + "scanned past by a scan range that collapsed to empty")
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("the break-start scan locates the agent-day's break slot, not null")
        void locatesItsBreakSlot() {
            Schedule schedule = MidnightBoundaryFixture.ninePmShiftCrossingMidnight();
            List<AgentAssignment> workedAssignments = schedule.getAssignments();

            // Argued: the same clock-order collapse that zeroes the hole count above also empties
            // findBreakStart's scan range -- today it returns null for an agent-day that
            // genuinely has a break. The break band is anchored to start at 00:00 (template start
            // 22:00, anchored minute 60, plus the band's 120-minute offset = anchored minute 180
            // = 00:00) -- the FIRST unassigned slot the scan reaches once the range is anchored
            // correctly, since its anchored minute (180) precedes the gap's (360).
            assertThat(ScheduleConstraintProvider.findBreakStart(workedAssignments, 60, NINE_PM_ANCHOR))
                    .as("the scan must locate the agent-day's genuine break slot, not return null "
                            + "because its range collapsed to empty")
                    .isEqualTo(LocalTime.MIDNIGHT);
        }

        @Test
        @DisplayName("every one of its seats resolves to one business date, across two calendar dates")
        void resolvesToOneBusinessDateAcrossTwoCalendarDates() {
            Schedule schedule = MidnightBoundaryFixture.ninePmShiftCrossingMidnight();
            LocalTime dayStart = LocalTime.of(21, 0);

            // Derived through DayWindow.businessDateOf against each slot's own stored calendar
            // date and start time -- never against the stored business-date column -- so this
            // proves the DERIVATION, not merely that the fixture wrote a consistent value.
            List<LocalDate> derivedBusinessDates = schedule.getTimeslots().stream()
                    .map(ts -> DayWindow.businessDateOf(dayStart, ts.getDate(), ts.getStartTime()))
                    .distinct()
                    .toList();
            assertThat(derivedBusinessDates)
                    .as("every slot's DERIVED business date must be the same single date (OVNT-04)")
                    .hasSize(1);

            List<LocalDate> calendarDates = schedule.getTimeslots().stream()
                    .map(Timeslot::getDate)
                    .distinct()
                    .toList();
            assertThat(calendarDates)
                    .as("the slots must genuinely span two distinct calendar dates")
                    .hasSize(2);

            assertThat(schedule.getTimeslots())
                    .as("at least one slot must start exactly at calendar midnight -- the clock "
                            + "time this scenario's siblings deliberately avoid")
                    .anyMatch(ts -> ts.getStartTime().equals(LocalTime.MIDNIGHT));
        }

        @Test
        @DisplayName("no-op control: every pre-existing scenario's per-constraint match counts are unchanged")
        void preExistingScenarios_matchCountsUnchanged() {
            SolutionManager<Schedule, HardSoftScore> solutionManager = newSolutionManager();

            // None of these pre-existing scenarios' assigned starts cross the anchor the way this
            // plan's new scenario deliberately does, so Task 2's anchored-min/max fix must leave
            // every one of these byte-identical to its already-asserted value elsewhere in this
            // file -- the regression surface for Task 2's production change.
            assertThat(requireConstraint(solutionManager, MidnightBoundaryFixture.midnightCoverageScenario(),
                    "Minimum staffing").getConstraintMatchCount())
                    .isEqualTo(0);
            assertThat(requireConstraint(solutionManager,
                    MidnightBoundaryFixture.breakBandFlushToEnvelopeEndScenario(),
                    ScheduleConstraintProvider.SHIFT_ENVELOPE_COMPLIANCE_CONSTRAINT_NAME)
                    .getConstraintMatchCount())
                    .isEqualTo(0);
            assertThat(requireConstraint(solutionManager,
                    MidnightBoundaryFixture.ninePmBreakBandFlushToEnvelopeEndScenario(),
                    ScheduleConstraintProvider.SHIFT_ENVELOPE_COMPLIANCE_CONSTRAINT_NAME)
                    .getConstraintMatchCount())
                    .isEqualTo(0);
            assertThat(requireConstraint(solutionManager,
                    MidnightBoundaryFixture.ninePmOvernightContiguityScenario(), "Shift work contiguity")
                    .getConstraintMatchCount())
                    .isEqualTo(1);
        }
    }
}
