package com.wfm.solver;

import ai.timefold.solver.test.api.score.stream.ConstraintVerifier;
import com.wfm.model.Agent;
import com.wfm.model.AgentAssignment;
import com.wfm.model.AgentDayConfig;
import com.wfm.model.AgentRestWaiver;
import com.wfm.model.AgentShiftAssignment;
import com.wfm.model.BreakAlignment;
import com.wfm.model.RestSpan;
import com.wfm.model.Schedule;
import com.wfm.model.ScheduleConfig;
import com.wfm.model.SchedulingMode;
import com.wfm.model.ShiftBandPair;
import com.wfm.model.ShiftTemplate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end proof of {@link ScheduleConstraintProvider#minimumRestShift} (REST-02, REST-04): the
 * SHIFT-mode rest penalty, its boundary at exactly the configured minimum, its NULL inertness, and
 * the 15:00-anchored back-to-back case the requirement's clause is actually about
 * (22-CONTEXT.md Phase Boundary item 4).
 *
 * <p>Note these tests assert the raw match count / configured penalty amount, not a score —
 * mirroring {@code ShiftEnvelopeComplianceConstraintTest}'s precedent for another
 * {@code penalizeConfigurable()} constraint.
 */
class MinimumRestShiftConstraintTest {

    private final ConstraintVerifier<ScheduleConstraintProvider, Schedule> verifier =
            ConstraintVerifier.build(new ScheduleConstraintProvider(), Schedule.class,
                    AgentAssignment.class, AgentShiftAssignment.class);

    private static final LocalDate D_MINUS_1 = LocalDate.of(2026, 9, 7);
    private static final LocalDate D = LocalDate.of(2026, 9, 8);

    private static Agent agent() {
        Agent a = new Agent();
        a.setId(UUID.randomUUID());
        return a;
    }

    private static ShiftTemplate template(LocalTime start, LocalTime end) {
        ShiftTemplate t = new ShiftTemplate();
        t.setId(UUID.randomUUID());
        t.setName("Template-" + UUID.randomUUID());
        t.setStartTime(start);
        t.setEndTime(end);
        return t;
    }

    private static ShiftBandPair pair(LocalTime start, LocalTime end) {
        return new ShiftBandPair(template(start, end), null);
    }

    private static AgentRestWaiver waiver(Agent agent, LocalDate date) {
        AgentRestWaiver w = new AgentRestWaiver();
        w.setId(UUID.randomUUID());
        w.setTenantId(1L);
        w.setDeskId(UUID.randomUUID());
        w.setAgent(agent);
        w.setDate(date);
        w.setReason("test");
        return w;
    }

    /** A shift row anchored at {@code dayStart} via its own {@link AgentDayConfig} problem fact —
     * {@link ScheduleConstraintProvider#minimumRestShift} resolves the anchor through
     * {@code anchorFor(sa)}, which reads this row's own {@code dayConfig}, not {@code
     * ScheduleConfig}'s anchor (Quad-arity carrier precedent, see {@code AgentDayConfig}'s javadoc). */
    private static AgentShiftAssignment shiftRow(Agent agent, LocalDate date, ShiftBandPair pair, LocalTime dayStart) {
        AgentShiftAssignment sa = new AgentShiftAssignment();
        sa.setId(UUID.randomUUID());
        sa.setAgent(agent);
        sa.setDate(date);
        sa.setShiftBandPair(pair);
        sa.setDayConfig(new AgentDayConfig(agent.getId(), date, new BigDecimal("8.00"), 60, 60,
                new BigDecimal("4.00"), new BigDecimal("1.00"), BreakAlignment.ON_HOUR, 130, 70, dayStart));
        return sa;
    }

    private static AgentShiftAssignment shiftRow(Agent agent, LocalDate date, ShiftBandPair pair) {
        return shiftRow(agent, date, pair, LocalTime.MIDNIGHT);
    }

    private static ScheduleConfig scheduleConfig(SchedulingMode mode, Integer minimumRestMinutes) {
        return new ScheduleConfig(15, LocalTime.of(0, 0), LocalTime.of(23, 59),
                60, new BigDecimal("4.00"), new BigDecimal("1.00"),
                BreakAlignment.ON_HOUR, 20, new BigDecimal("8.00"), 130, 70, mode,
                ScheduleConfig.DEFAULT_CONSISTENCY_TOLERANCE_MINUTES, LocalTime.MIDNIGHT,
                minimumRestMinutes);
    }

    @Test
    @DisplayName("a 480-minute gap against a 660-minute minimum is penalised once, by the 180-minute shortfall")
    void gap480_minimum660_penalisedByShortfall() {
        Agent a = agent();
        AgentShiftAssignment prev = shiftRow(a, D_MINUS_1, pair(LocalTime.of(14, 0), LocalTime.of(22, 0)));
        AgentShiftAssignment next = shiftRow(a, D, pair(LocalTime.of(6, 0), LocalTime.of(14, 0)));

        verifier.verifyThat(ScheduleConstraintProvider::minimumRestShift)
                .given(prev, next, scheduleConfig(SchedulingMode.SHIFT, 660))
                .penalizesBy(180);
    }

    @Test
    @DisplayName("a gap exactly equal to the configured minimum is not penalised")
    void gapExactlyAtMinimum_notPenalised() {
        Agent a = agent();
        AgentShiftAssignment prev = shiftRow(a, D_MINUS_1, pair(LocalTime.of(14, 0), LocalTime.of(22, 0)));
        AgentShiftAssignment next = shiftRow(a, D, pair(LocalTime.of(9, 0), LocalTime.of(17, 0)));

        verifier.verifyThat(ScheduleConstraintProvider::minimumRestShift)
                .given(prev, next, scheduleConfig(SchedulingMode.SHIFT, 660))
                .penalizesBy(0);
    }

    @Test
    @DisplayName("a gap one minute short of the configured minimum is penalised by exactly one minute")
    void gapOneMinuteShortOfMinimum_penalisedByOne() {
        Agent a = agent();
        AgentShiftAssignment prev = shiftRow(a, D_MINUS_1, pair(LocalTime.of(14, 0), LocalTime.of(22, 0)));
        AgentShiftAssignment next = shiftRow(a, D, pair(LocalTime.of(8, 59), LocalTime.of(17, 0)));

        verifier.verifyThat(ScheduleConstraintProvider::minimumRestShift)
                .given(prev, next, scheduleConfig(SchedulingMode.SHIFT, 660))
                .penalizesBy(1);
    }

    @Test
    @DisplayName("a predecessor ending at the business-day boundary paired with a successor starting at it yields a zero gap, not 1440")
    void backToBackAtBoundary_zeroGap_penalisedByFullMinimum() {
        Agent a = agent();
        AgentShiftAssignment prev = shiftRow(a, D_MINUS_1, pair(LocalTime.of(16, 0), LocalTime.MIDNIGHT));
        AgentShiftAssignment next = shiftRow(a, D, pair(LocalTime.MIDNIGHT, LocalTime.of(8, 0)));

        verifier.verifyThat(ScheduleConstraintProvider::minimumRestShift)
                .given(prev, next, scheduleConfig(SchedulingMode.SHIFT, 660))
                .penalizesBy(660);
    }

    @Test
    @DisplayName("a desk anchored at 15:00 computes the gap identically to the midnight case -- the REST-02 back-to-back clause")
    void fifteenHundredAnchor_overlappingCalendarDates_gapComputedCorrectly() {
        Agent a = agent();
        LocalTime anchor = LocalTime.of(15, 0);
        AgentShiftAssignment prev = shiftRow(a, D_MINUS_1, pair(LocalTime.of(5, 0), LocalTime.of(14, 0)), anchor);
        AgentShiftAssignment next = shiftRow(a, D, pair(LocalTime.of(20, 0), LocalTime.of(5, 0)), anchor);

        // remainingInPrevDay = 1440 - anchoredEndMinute(14:00 @ 15:00 anchor) = 1440 - 1380 = 60
        // elapsedIntoNextDay = anchoredStartMinute(20:00 @ 15:00 anchor) = 300
        // gap = 360, shortfall against 660 = 300
        verifier.verifyThat(ScheduleConstraintProvider::minimumRestShift)
                .given(prev, next, scheduleConfig(SchedulingMode.SHIFT, 660))
                .penalizesBy(300);
    }

    @Test
    @DisplayName("an overnight SUCCESSOR (20:00-05:00) paired with a non-wrapping predecessor is numerically "
            + "unchanged by this fix -- the successor side was deliberately not touched")
    void overnightSuccessorWithNonWrappingPredecessor_unchangedByTheFix() {
        Agent a = agent();
        AgentShiftAssignment prev = shiftRow(a, D_MINUS_1, pair(LocalTime.of(6, 0), LocalTime.of(14, 0)));
        AgentShiftAssignment next = shiftRow(a, D, pair(LocalTime.of(20, 0), LocalTime.of(5, 0)));

        // remainingInPrevDay = 1440 - anchoredWrappedEndMinute(06:00,14:00) = 1440 - 840 = 600
        // elapsedIntoNextDay = anchoredStartMinute(20:00) = 1200
        // gap = 1800 -- identical before and after this phase's change, since the successor side
        // (next.startTime() only) never needed wrap information and was not touched.
        verifier.verifyThat(ScheduleConstraintProvider::minimumRestShift)
                .given(prev, next, scheduleConfig(SchedulingMode.SHIFT, 660))
                .penalizesBy(0);
    }

    @Test
    @DisplayName("an overnight predecessor (22:00-06:00) measures the true 60-minute gap against a 07:00 successor, not the pre-fix 1500, penalised by 600 under a 660-minute minimum")
    void overnightPredecessor_trueGapMeasuredCorrectly() {
        Agent a = agent();
        AgentShiftAssignment prev = shiftRow(a, D_MINUS_1, pair(LocalTime.of(22, 0), LocalTime.of(6, 0)));
        AgentShiftAssignment next = shiftRow(a, D, pair(LocalTime.of(7, 0), LocalTime.of(15, 0)));

        verifier.verifyThat(ScheduleConstraintProvider::minimumRestShift)
                .given(prev, next, scheduleConfig(SchedulingMode.SHIFT, 660))
                .penalizesBy(600);
    }

    @Test
    @DisplayName("an overnight predecessor's gap exactly at a 60-minute minimum is not penalised -- the exact-equality boundary")
    void overnightPredecessor_gapExactlyAtMinimum_notPenalised() {
        Agent a = agent();
        AgentShiftAssignment prev = shiftRow(a, D_MINUS_1, pair(LocalTime.of(22, 0), LocalTime.of(6, 0)));
        AgentShiftAssignment next = shiftRow(a, D, pair(LocalTime.of(7, 0), LocalTime.of(15, 0)));

        verifier.verifyThat(ScheduleConstraintProvider::minimumRestShift)
                .given(prev, next, scheduleConfig(SchedulingMode.SHIFT, 60))
                .penalizesBy(0);
    }

    @Test
    @DisplayName("an overnight predecessor's gap one minute short of the minimum is penalised by exactly one")
    void overnightPredecessor_gapOneMinuteShortOfMinimum_penalisedByOne() {
        Agent a = agent();
        AgentShiftAssignment prev = shiftRow(a, D_MINUS_1, pair(LocalTime.of(22, 0), LocalTime.of(6, 0)));
        AgentShiftAssignment next = shiftRow(a, D, pair(LocalTime.of(7, 0), LocalTime.of(15, 0)));

        verifier.verifyThat(ScheduleConstraintProvider::minimumRestShift)
                .given(prev, next, scheduleConfig(SchedulingMode.SHIFT, 61))
                .penalizesBy(1);
    }

    @Test
    @DisplayName("a NULL minimumRestMinutes produces no match, whatever the gap")
    void nullMinimumRestMinutes_noMatch() {
        Agent a = agent();
        AgentShiftAssignment prev = shiftRow(a, D_MINUS_1, pair(LocalTime.of(14, 0), LocalTime.of(22, 0)));
        AgentShiftAssignment next = shiftRow(a, D, pair(LocalTime.of(6, 0), LocalTime.of(14, 0)));

        verifier.verifyThat(ScheduleConstraintProvider::minimumRestShift)
                .given(prev, next, scheduleConfig(SchedulingMode.SHIFT, null))
                .penalizesBy(0);
    }

    @Test
    @DisplayName("a configured minimum of zero builds tuples but never penalises -- the gap formula cannot be negative")
    void zeroMinimumRestMinutes_tuplesBuiltButNeverPenalised() {
        Agent a = agent();
        AgentShiftAssignment prev = shiftRow(a, D_MINUS_1, pair(LocalTime.of(14, 0), LocalTime.of(22, 0)));
        AgentShiftAssignment next = shiftRow(a, D, pair(LocalTime.of(6, 0), LocalTime.of(14, 0)));

        verifier.verifyThat(ScheduleConstraintProvider::minimumRestShift)
                .given(prev, next, scheduleConfig(SchedulingMode.SHIFT, 0))
                .penalizesBy(0);
    }

    @Test
    @DisplayName("a SLOT-mode desk is never penalised, even with a configured minimum and a violating gap")
    void slotMode_neverPenalised() {
        Agent a = agent();
        AgentShiftAssignment prev = shiftRow(a, D_MINUS_1, pair(LocalTime.of(14, 0), LocalTime.of(22, 0)));
        AgentShiftAssignment next = shiftRow(a, D, pair(LocalTime.of(6, 0), LocalTime.of(14, 0)));

        verifier.verifyThat(ScheduleConstraintProvider::minimumRestShift)
                .given(prev, next, scheduleConfig(SchedulingMode.SLOT, 660))
                .penalizesBy(0);
    }

    @Test
    @DisplayName("an unassigned predecessor shift produces no match")
    void unassignedPredecessor_noMatch() {
        Agent a = agent();
        AgentShiftAssignment prev = shiftRow(a, D_MINUS_1, null);
        AgentShiftAssignment next = shiftRow(a, D, pair(LocalTime.of(6, 0), LocalTime.of(14, 0)));

        verifier.verifyThat(ScheduleConstraintProvider::minimumRestShift)
                .given(prev, next, scheduleConfig(SchedulingMode.SHIFT, 660))
                .penalizesBy(0);
    }

    @Test
    @DisplayName("an unassigned successor shift produces no match")
    void unassignedSuccessor_noMatch() {
        Agent a = agent();
        AgentShiftAssignment prev = shiftRow(a, D_MINUS_1, pair(LocalTime.of(14, 0), LocalTime.of(22, 0)));
        AgentShiftAssignment next = shiftRow(a, D, null);

        verifier.verifyThat(ScheduleConstraintProvider::minimumRestShift)
                .given(prev, next, scheduleConfig(SchedulingMode.SHIFT, 660))
                .penalizesBy(0);
    }

    @Test
    @DisplayName("two agents on the same dates are evaluated independently -- only the violating agent's pair matches")
    void twoAgents_onlyViolatingAgentMatches() {
        Agent violating = agent();
        Agent compliant = agent();
        AgentShiftAssignment violatingPrev = shiftRow(violating, D_MINUS_1, pair(LocalTime.of(14, 0), LocalTime.of(22, 0)));
        AgentShiftAssignment violatingNext = shiftRow(violating, D, pair(LocalTime.of(6, 0), LocalTime.of(14, 0)));
        AgentShiftAssignment compliantPrev = shiftRow(compliant, D_MINUS_1, pair(LocalTime.of(14, 0), LocalTime.of(22, 0)));
        AgentShiftAssignment compliantNext = shiftRow(compliant, D, pair(LocalTime.of(9, 0), LocalTime.of(17, 0)));

        verifier.verifyThat(ScheduleConstraintProvider::minimumRestShift)
                .given(violatingPrev, violatingNext, compliantPrev, compliantNext,
                        scheduleConfig(SchedulingMode.SHIFT, 660))
                .penalizesBy(180);
    }

    @Test
    @DisplayName("RestSpan.gapMinutes refuses two spans anchored at different day starts")
    void gapMinutes_differentDayStarts_throws() {
        UUID agentId = UUID.randomUUID();
        RestSpan prev = new RestSpan(agentId, D_MINUS_1, LocalTime.of(14, 0), LocalTime.of(22, 0), LocalTime.MIDNIGHT);
        RestSpan next = new RestSpan(agentId, D, LocalTime.of(6, 0), LocalTime.of(14, 0), LocalTime.of(15, 0));

        assertThatThrownBy(() -> RestSpan.gapMinutes(prev, next))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("RestSpan.gapMinutes at a 00:00 anchor matches the plain minute-of-day arithmetic")
    void gapMinutes_midnightAnchor_matchesPlainArithmetic() {
        UUID agentId = UUID.randomUUID();
        RestSpan prev = new RestSpan(agentId, D_MINUS_1, LocalTime.of(14, 0), LocalTime.of(22, 0), LocalTime.MIDNIGHT);
        RestSpan next = new RestSpan(agentId, D, LocalTime.of(6, 0), LocalTime.of(14, 0), LocalTime.MIDNIGHT);

        assertThat(RestSpan.gapMinutes(prev, next)).isEqualTo(480);
    }

    @Test
    @DisplayName("RestSpan.gapMinutes with an overnight predecessor measures the true gap, not the pre-fix 1500")
    void gapMinutes_overnightPredecessor_matchesTrueGap() {
        UUID agentId = UUID.randomUUID();
        RestSpan prev = new RestSpan(agentId, D_MINUS_1, LocalTime.of(22, 0), LocalTime.of(6, 0), LocalTime.MIDNIGHT);
        RestSpan next = new RestSpan(agentId, D, LocalTime.of(7, 0), LocalTime.of(15, 0), LocalTime.MIDNIGHT);

        assertThat(RestSpan.gapMinutes(prev, next)).isEqualTo(60);
    }

    // ------------------------------------------------------------------
    //  Phase 22 (REST-06, D-06/D-08) -- waiver exclusion via RestWaiverLookup
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a waiver on the successor date D waives the violating pair entering D -- zero matches")
    void waiverOnSuccessorDate_zeroMatches() {
        Agent a = agent();
        AgentShiftAssignment prev = shiftRow(a, D_MINUS_1, pair(LocalTime.of(14, 0), LocalTime.of(22, 0)));
        AgentShiftAssignment next = shiftRow(a, D, pair(LocalTime.of(6, 0), LocalTime.of(14, 0)));

        verifier.verifyThat(ScheduleConstraintProvider::minimumRestShift)
                .given(prev, next, scheduleConfig(SchedulingMode.SHIFT, 660), waiver(a, D))
                .penalizesBy(0);
    }

    @Test
    @DisplayName("D-06: a waiver on the PREDECESSOR date D-1 does not clear the rest coming into D -- one match")
    void waiverOnPredecessorDate_doesNotClear_oneMatch() {
        Agent a = agent();
        AgentShiftAssignment prev = shiftRow(a, D_MINUS_1, pair(LocalTime.of(14, 0), LocalTime.of(22, 0)));
        AgentShiftAssignment next = shiftRow(a, D, pair(LocalTime.of(6, 0), LocalTime.of(14, 0)));

        verifier.verifyThat(ScheduleConstraintProvider::minimumRestShift)
                .given(prev, next, scheduleConfig(SchedulingMode.SHIFT, 660), waiver(a, D_MINUS_1))
                .penalizesBy(180);
    }

    @Test
    @DisplayName("a waiver on D for a different agent does not clear this agent's violation -- one match")
    void waiverForDifferentAgent_doesNotClear_oneMatch() {
        Agent a = agent();
        Agent other = agent();
        AgentShiftAssignment prev = shiftRow(a, D_MINUS_1, pair(LocalTime.of(14, 0), LocalTime.of(22, 0)));
        AgentShiftAssignment next = shiftRow(a, D, pair(LocalTime.of(6, 0), LocalTime.of(14, 0)));

        verifier.verifyThat(ScheduleConstraintProvider::minimumRestShift)
                .given(prev, next, scheduleConfig(SchedulingMode.SHIFT, 660), waiver(other, D))
                .penalizesBy(180);
    }

    @Test
    @DisplayName("one waiver on D clears exactly the D-into-D+1 pair of two consecutive violations, not both")
    void twoConsecutiveViolatingPairs_oneWaiverOnD_clearsExactlyOnePair() {
        Agent a = agent();
        LocalDate dPlus1 = D.plusDays(1);
        AgentShiftAssignment dMinus1Shift = shiftRow(a, D_MINUS_1, pair(LocalTime.of(14, 0), LocalTime.of(22, 0)));
        AgentShiftAssignment dShift = shiftRow(a, D, pair(LocalTime.of(6, 0), LocalTime.of(22, 0)));
        AgentShiftAssignment dPlus1Shift = shiftRow(a, dPlus1, pair(LocalTime.of(6, 0), LocalTime.of(14, 0)));

        // gap(D-1 -> D) = 480, gap(D -> D+1) = 480; both violate a 660-minute minimum by 180 each.
        // Waiving D clears only the pair whose successor business date is D (D-1 -> D); the
        // D -> D+1 pair's successor date is D+1, so it is untouched and still penalised by 180.
        verifier.verifyThat(ScheduleConstraintProvider::minimumRestShift)
                .given(dMinus1Shift, dShift, dPlus1Shift,
                        scheduleConfig(SchedulingMode.SHIFT, 660), waiver(a, D))
                .penalizesBy(180);
    }

    @Test
    @DisplayName("a desk with no waivers at all behaves identically to before this plan -- gap480 fixture unaffected")
    void emptyWaiverList_behavesIdenticallyToBeforeThisPlan() {
        Agent a = agent();
        AgentShiftAssignment prev = shiftRow(a, D_MINUS_1, pair(LocalTime.of(14, 0), LocalTime.of(22, 0)));
        AgentShiftAssignment next = shiftRow(a, D, pair(LocalTime.of(6, 0), LocalTime.of(14, 0)));

        verifier.verifyThat(ScheduleConstraintProvider::minimumRestShift)
                .given(prev, next, scheduleConfig(SchedulingMode.SHIFT, 660))
                .penalizesBy(180);
    }

    @Test
    @DisplayName("a pair with adequate rest and a waiver on its successor date is still zero matches")
    void adequateRestWithWaiverOnSuccessorDate_stillZeroMatches() {
        Agent a = agent();
        AgentShiftAssignment prev = shiftRow(a, D_MINUS_1, pair(LocalTime.of(14, 0), LocalTime.of(22, 0)));
        AgentShiftAssignment next = shiftRow(a, D, pair(LocalTime.of(9, 0), LocalTime.of(17, 0)));

        verifier.verifyThat(ScheduleConstraintProvider::minimumRestShift)
                .given(prev, next, scheduleConfig(SchedulingMode.SHIFT, 660), waiver(a, D))
                .penalizesBy(0);
    }
}
