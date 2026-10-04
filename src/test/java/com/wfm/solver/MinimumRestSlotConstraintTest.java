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
import com.wfm.model.Timeslot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end proof of {@link ScheduleConstraintProvider#minimumRestSlot} (REST-02, REST-04) and of
 * {@link RestSpan#ofSlots} (D-02) — the SLOT-mode rest penalty, and the regression this plan exists
 * for: a genuinely {@code exactlyOneBreak}-compliant SLOT agent-day must NOT trip the rest
 * constraint on its own mandated break.
 *
 * <p><b>What this test class refuses, citing requirement ids only (REST-02, REST-04, D-02):</b> a
 * maximal-contiguous-run definition of a SLOT shift would split every compliant agent-day at its
 * mandated break and fire the rest rule on every compliant day on every SLOT desk. The alternative
 * of exempting the break duration from the scan avoids that false positive but ties the rest rule to
 * break geometry — two rules that would then have to agree forever, with no shared predicate. This
 * class proves the ignore-the-gap definition ({@link RestSpan#ofSlots}) avoids both: the
 * {@link #twoCompliantConsecutiveDays_minimum660_zeroMatches()} /
 * {@link #twoCompliantConsecutiveDays_raisedMinimum_oneMatch()} pair is the load-bearing evidence —
 * a zero-match assertion alone would pass against a dead constraint just as readily as against a
 * correct one, so the paired positive (raising the minimum above the real gap produces exactly one
 * match) is what proves the zero is genuine.
 *
 * <p>Note these tests assert the raw match count / configured penalty amount, not a score —
 * mirroring {@code MinimumRestShiftConstraintTest}'s precedent for another
 * {@code penalizeConfigurable()} constraint.
 */
class MinimumRestSlotConstraintTest {

    private final ConstraintVerifier<ScheduleConstraintProvider, Schedule> verifier =
            ConstraintVerifier.build(new ScheduleConstraintProvider(), Schedule.class,
                    AgentAssignment.class, AgentShiftAssignment.class);

    private static final LocalDate D_MINUS_1 = LocalDate.of(2026, 9, 7);
    private static final LocalDate D = LocalDate.of(2026, 9, 8);
    private static final int INCREMENT = 60;
    private static final BigDecimal CONTRACTED_HOURS = new BigDecimal("8.00");
    private static final BigDecimal BREAK_MIN_SHIFT_HOURS = new BigDecimal("4.00");
    private static final BigDecimal BREAK_BLOCKED_HOURS = new BigDecimal("1.00");
    private static final int BREAK_DURATION_MINUTES = 60;

    private static Agent agent() {
        Agent a = new Agent();
        a.setId(UUID.randomUUID());
        return a;
    }

    private static Timeslot timeslot(LocalDate businessDate, LocalTime start, LocalTime end) {
        Timeslot ts = new Timeslot();
        ts.setId(UUID.randomUUID());
        ts.setDate(businessDate);
        ts.setBusinessDate(businessDate);
        ts.setStartTime(start);
        ts.setEndTime(end);
        return ts;
    }

    private static AgentAssignment seat(Agent agent, Timeslot ts) {
        AgentAssignment a = new AgentAssignment();
        a.setId(UUID.randomUUID());
        a.setAgent(agent);
        a.setTimeslot(ts);
        return a;
    }

    private static AgentDayConfig dayConfig(Agent agent, LocalDate date, LocalTime dayStart) {
        return new AgentDayConfig(agent.getId(), date, CONTRACTED_HOURS, INCREMENT,
                BREAK_DURATION_MINUTES, BREAK_MIN_SHIFT_HOURS, BREAK_BLOCKED_HOURS,
                BreakAlignment.ON_HOUR, 130, 70, dayStart);
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

    private static ScheduleConfig scheduleConfig(SchedulingMode mode, Integer minimumRestMinutes, LocalTime dayStart) {
        return new ScheduleConfig(INCREMENT, LocalTime.of(0, 0), LocalTime.of(23, 59),
                BREAK_DURATION_MINUTES, BREAK_MIN_SHIFT_HOURS, BREAK_BLOCKED_HOURS,
                BreakAlignment.ON_HOUR, 20, CONTRACTED_HOURS, 130, 70, mode,
                ScheduleConfig.DEFAULT_CONSISTENCY_TOLERANCE_MINUTES, dayStart, minimumRestMinutes);
    }

    /**
     * Eight hourly seats — four worked, a one-hour gap, four worked — starting at
     * {@code shiftStart}. Genuinely compliant under {@code exactlyOneBreak}, not an approximation:
     * at this fixture's {@code AgentDayConfig} (4.00h {@code breakMinShiftHours}, 60-min increment),
     * {@code breakThresholdSlots} is {@code CEILING(4.00 * 60 / 60) = 4}, so these 8 assignments sit
     * in the "fully assigned" branch requiring exactly one gap of exactly
     * {@code breakDurationMinutes / incrementMinutes = 1} slot — satisfied here. Wall-clock span is
     * 9 hours ({@code shiftStart} to {@code shiftStart + 9h}); worked hours are the contracted 8.00.
     */
    private static List<AgentAssignment> compliantDaySeats(Agent agent, LocalDate date, LocalTime shiftStart) {
        List<AgentAssignment> seats = new ArrayList<>();
        LocalTime t = shiftStart;
        for (int i = 0; i < 4; i++) {
            seats.add(seat(agent, timeslot(date, t, t.plusMinutes(INCREMENT))));
            t = t.plusMinutes(INCREMENT);
        }
        t = t.plusMinutes(INCREMENT); // the one-hour mandated break -- a gap in assignment, not a seat
        for (int i = 0; i < 4; i++) {
            seats.add(seat(agent, timeslot(date, t, t.plusMinutes(INCREMENT))));
            t = t.plusMinutes(INCREMENT);
        }
        return seats;
    }

    // ------------------------------------------------------------------
    //  RestSpan.ofSlots — direct unit proof of D-02's span definition
    // ------------------------------------------------------------------

    @Test
    @DisplayName("RestSpan.ofSlots on a genuinely exactlyOneBreak-compliant agent-day returns one span covering the first slot's start to the last slot's end")
    void ofSlots_compliantAgentDay_returnsSpanCoveringFirstSlotStartToLastSlotEnd() {
        Agent a = agent();
        List<AgentAssignment> seats = compliantDaySeats(a, D_MINUS_1, LocalTime.of(8, 0));

        RestSpan span = RestSpan.ofSlots(a.getId(), D_MINUS_1, seats, LocalTime.MIDNIGHT);

        assertThat(span.startTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(span.endTime()).isEqualTo(LocalTime.of(17, 0));
    }

    @Test
    @DisplayName("RestSpan.ofSlots on a fragmented agent-day with two gaps still returns one span covering first start to last end")
    void ofSlots_fragmentedAgentDayTwoGaps_stillOneSpanCoveringFirstToLast() {
        Agent a = agent();
        List<AgentAssignment> seats = List.of(
                seat(a, timeslot(D, LocalTime.of(8, 0), LocalTime.of(9, 0))),
                seat(a, timeslot(D, LocalTime.of(10, 0), LocalTime.of(11, 0))),
                seat(a, timeslot(D, LocalTime.of(13, 0), LocalTime.of(14, 0))));

        RestSpan span = RestSpan.ofSlots(a.getId(), D, seats, LocalTime.MIDNIGHT);

        assertThat(span.startTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(span.endTime()).isEqualTo(LocalTime.of(14, 0));
    }

    @Test
    @DisplayName("RestSpan.ofSlots on a 15:00-anchored desk orders by anchored minute, not clock time")
    void ofSlots_fifteenHundredAnchor_orderedByAnchoredMinuteNotClockTime() {
        Agent a = agent();
        LocalTime anchor = LocalTime.of(15, 0);
        List<AgentAssignment> seats = List.of(
                seat(a, timeslot(D, LocalTime.of(23, 45), LocalTime.MIDNIGHT)),
                seat(a, timeslot(D, LocalTime.of(0, 15), LocalTime.of(0, 30))));

        RestSpan span = RestSpan.ofSlots(a.getId(), D, seats, anchor);

        assertThat(span.startTime()).isEqualTo(LocalTime.of(23, 45));
        assertThat(span.endTime()).isEqualTo(LocalTime.of(0, 30));
    }

    @Test
    @DisplayName("RestSpan.ofSlots throws on an empty slot list rather than returning a degenerate span")
    void ofSlots_emptySlotList_throws() {
        assertThatThrownBy(() -> RestSpan.ofSlots(UUID.randomUUID(), D, List.of(), LocalTime.MIDNIGHT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ------------------------------------------------------------------
    //  minimumRestSlot — full pipeline proofs
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a 600-minute gap against a 660-minute minimum is penalised once, by the 60-minute shortfall")
    void shortGap_minimum660_penalisedByShortfall() {
        Agent a = agent();
        List<Object> facts = new ArrayList<>();
        facts.addAll(compliantDaySeats(a, D_MINUS_1, LocalTime.of(9, 0))); // ends 18:00
        facts.addAll(compliantDaySeats(a, D, LocalTime.of(4, 0))); // starts 04:00 -- gap 600
        facts.add(dayConfig(a, D_MINUS_1, LocalTime.MIDNIGHT));
        facts.add(dayConfig(a, D, LocalTime.MIDNIGHT));
        facts.add(scheduleConfig(SchedulingMode.SLOT, 660, LocalTime.MIDNIGHT));

        verifier.verifyThat(ScheduleConstraintProvider::minimumRestSlot)
                .given(facts.toArray())
                .penalizesBy(60);
    }

    @Test
    @DisplayName("a gap exactly equal to the configured minimum is not penalised")
    void gapExactlyAtMinimum_notPenalised() {
        Agent a = agent();
        List<Object> facts = new ArrayList<>();
        facts.addAll(compliantDaySeats(a, D_MINUS_1, LocalTime.of(9, 0))); // ends 18:00
        facts.addAll(compliantDaySeats(a, D, LocalTime.of(5, 0))); // starts 05:00 -- gap exactly 660
        facts.add(dayConfig(a, D_MINUS_1, LocalTime.MIDNIGHT));
        facts.add(dayConfig(a, D, LocalTime.MIDNIGHT));
        facts.add(scheduleConfig(SchedulingMode.SLOT, 660, LocalTime.MIDNIGHT));

        verifier.verifyThat(ScheduleConstraintProvider::minimumRestSlot)
                .given(facts.toArray())
                .penalizesBy(0);
    }

    // ------------------------------------------------------------------
    //  Pre-horizon OVERNIGHT predecessor against real in-horizon seats (PF-02) -- the wrapping
    //  side is a directly-constructed pre-horizon RestSpan (the genuine production route;
    //  RestSpan.ofSlots cannot itself emit a wrapping span from grid-aligned slots, see PF-02),
    //  paired with real compliantDaySeats on D. The predecessor wrapped past the MIDNIGHT anchor:
    //  its true end offset is 1800 (1320 plus the 480-minute wrapped duration), the in-horizon
    //  span starts 07:00 for 420 elapsed minutes, so the gap is 60. Before the fix, the
    //  predecessor's end offset read as 360, producing a gap of 1500 -- above any configurable
    //  minimum, so this fixture scored zero.
    // ------------------------------------------------------------------

    @Test
    @DisplayName("an overnight pre-horizon predecessor measures the true 60-minute gap against real in-horizon seats, penalised by 600 -- before the fix this scored zero")
    void overnightPreHorizonPredecessor_trueGapMeasuredCorrectly() {
        Agent a = agent();
        RestSpan prev = new RestSpan(a.getId(), D_MINUS_1, LocalTime.of(22, 0), LocalTime.of(6, 0),
                LocalTime.MIDNIGHT);
        List<AgentAssignment> dSeats = compliantDaySeats(a, D, LocalTime.of(7, 0));
        List<Object> facts = new ArrayList<>();
        facts.add(prev);
        facts.addAll(dSeats);
        facts.add(dayConfig(a, D, LocalTime.MIDNIGHT));
        facts.add(scheduleConfig(SchedulingMode.SLOT, 660, LocalTime.MIDNIGHT));

        verifier.verifyThat(ScheduleConstraintProvider::minimumRestSlot)
                .given(facts.toArray())
                .penalizesBy(600);
    }

    @Test
    @DisplayName("the identical overnight pre-horizon fixture against a 60-minute minimum is the exact-equality boundary -- not penalised")
    void overnightPreHorizonPredecessor_gapExactlyAtMinimum_notPenalised() {
        Agent a = agent();
        RestSpan prev = new RestSpan(a.getId(), D_MINUS_1, LocalTime.of(22, 0), LocalTime.of(6, 0),
                LocalTime.MIDNIGHT);
        List<AgentAssignment> dSeats = compliantDaySeats(a, D, LocalTime.of(7, 0));
        List<Object> facts = new ArrayList<>();
        facts.add(prev);
        facts.addAll(dSeats);
        facts.add(dayConfig(a, D, LocalTime.MIDNIGHT));
        facts.add(scheduleConfig(SchedulingMode.SLOT, 60, LocalTime.MIDNIGHT));

        verifier.verifyThat(ScheduleConstraintProvider::minimumRestSlot)
                .given(facts.toArray())
                .penalizesBy(0);
    }

    /**
     * The load-bearing regression (D-02's trap): two consecutive agent-days, each genuinely
     * compliant under {@code exactlyOneBreak} with its own mandated one-hour break, produce ZERO
     * matches at a realistic 660-minute minimum. A maximal-contiguous-run definition of "shift"
     * would instead see four spans here (two per day, split at each day's break) and would very
     * likely fire on at least one of the resulting short adjacent gaps.
     */
    @Test
    @DisplayName("two exactlyOneBreak-compliant consecutive agent-days produce zero matches at a realistic minimum -- the D-02 false-positive this plan exists to refuse")
    void twoCompliantConsecutiveDays_minimum660_zeroMatches() {
        Agent a = agent();
        List<Object> facts = new ArrayList<>();
        facts.addAll(compliantDaySeats(a, D_MINUS_1, LocalTime.of(8, 0))); // 08:00-17:00
        facts.addAll(compliantDaySeats(a, D, LocalTime.of(8, 0))); // 08:00-17:00 -- gap 900
        facts.add(dayConfig(a, D_MINUS_1, LocalTime.MIDNIGHT));
        facts.add(dayConfig(a, D, LocalTime.MIDNIGHT));
        facts.add(scheduleConfig(SchedulingMode.SLOT, 660, LocalTime.MIDNIGHT));

        verifier.verifyThat(ScheduleConstraintProvider::minimumRestSlot)
                .given(facts.toArray())
                .penalizesBy(0);
    }

    /**
     * The paired positive for the zero-match case above, on the IDENTICAL fixture: raising the
     * configured minimum above the real 900-minute inter-day gap produces exactly one match. This is
     * what proves the zero above is a genuine "adequately rested", not a dead constraint — a
     * zero-match assertion alone would pass against either.
     */
    @Test
    @DisplayName("the identical two-compliant-day fixture produces one match once the minimum is raised above the real gap")
    void twoCompliantConsecutiveDays_raisedMinimum_oneMatch() {
        Agent a = agent();
        List<Object> facts = new ArrayList<>();
        facts.addAll(compliantDaySeats(a, D_MINUS_1, LocalTime.of(8, 0))); // 08:00-17:00
        facts.addAll(compliantDaySeats(a, D, LocalTime.of(8, 0))); // 08:00-17:00 -- gap 900
        facts.add(dayConfig(a, D_MINUS_1, LocalTime.MIDNIGHT));
        facts.add(dayConfig(a, D, LocalTime.MIDNIGHT));
        facts.add(scheduleConfig(SchedulingMode.SLOT, 1000, LocalTime.MIDNIGHT));

        verifier.verifyThat(ScheduleConstraintProvider::minimumRestSlot)
                .given(facts.toArray())
                .penalizesBy(100);
    }

    @Test
    @DisplayName("a NULL minimumRestMinutes produces no match, whatever the gap")
    void nullMinimumRestMinutes_noMatch() {
        Agent a = agent();
        List<Object> facts = new ArrayList<>();
        facts.addAll(compliantDaySeats(a, D_MINUS_1, LocalTime.of(9, 0)));
        facts.addAll(compliantDaySeats(a, D, LocalTime.of(4, 0)));
        facts.add(dayConfig(a, D_MINUS_1, LocalTime.MIDNIGHT));
        facts.add(dayConfig(a, D, LocalTime.MIDNIGHT));
        facts.add(scheduleConfig(SchedulingMode.SLOT, null, LocalTime.MIDNIGHT));

        verifier.verifyThat(ScheduleConstraintProvider::minimumRestSlot)
                .given(facts.toArray())
                .penalizesBy(0);
    }

    @Test
    @DisplayName("a SHIFT-mode desk is never penalised by this constraint, even with a configured minimum and a violating gap")
    void shiftModeDesk_neverPenalised() {
        Agent a = agent();
        List<Object> facts = new ArrayList<>();
        facts.addAll(compliantDaySeats(a, D_MINUS_1, LocalTime.of(9, 0)));
        facts.addAll(compliantDaySeats(a, D, LocalTime.of(4, 0)));
        facts.add(dayConfig(a, D_MINUS_1, LocalTime.MIDNIGHT));
        facts.add(dayConfig(a, D, LocalTime.MIDNIGHT));
        facts.add(scheduleConfig(SchedulingMode.SHIFT, 660, LocalTime.MIDNIGHT));

        verifier.verifyThat(ScheduleConstraintProvider::minimumRestSlot)
                .given(facts.toArray())
                .penalizesBy(0);
    }

    @Test
    @DisplayName("a schedule with zero assigned AgentAssignment rows produces zero matches (REST-04 empty edge)")
    void noAssignedRows_zeroMatches() {
        verifier.verifyThat(ScheduleConstraintProvider::minimumRestSlot)
                .given(scheduleConfig(SchedulingMode.SLOT, 660, LocalTime.MIDNIGHT))
                .penalizesBy(0);
    }

    // ------------------------------------------------------------------
    //  Phase 22 (REST-06, D-06/D-08) -- waiver exclusion via RestWaiverLookup
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a waiver on the successor date D waives the violating pair entering D -- zero matches")
    void waiverOnSuccessorDate_zeroMatches() {
        Agent a = agent();
        List<Object> facts = new ArrayList<>();
        facts.addAll(compliantDaySeats(a, D_MINUS_1, LocalTime.of(9, 0))); // ends 18:00
        facts.addAll(compliantDaySeats(a, D, LocalTime.of(4, 0))); // starts 04:00 -- gap 600
        facts.add(dayConfig(a, D_MINUS_1, LocalTime.MIDNIGHT));
        facts.add(dayConfig(a, D, LocalTime.MIDNIGHT));
        facts.add(scheduleConfig(SchedulingMode.SLOT, 660, LocalTime.MIDNIGHT));
        facts.add(waiver(a, D));

        verifier.verifyThat(ScheduleConstraintProvider::minimumRestSlot)
                .given(facts.toArray())
                .penalizesBy(0);
    }

    @Test
    @DisplayName("D-06: a waiver on the PREDECESSOR date D-1 does not clear the rest coming into D -- still penalised")
    void waiverOnPredecessorDate_doesNotClear() {
        Agent a = agent();
        List<Object> facts = new ArrayList<>();
        facts.addAll(compliantDaySeats(a, D_MINUS_1, LocalTime.of(9, 0)));
        facts.addAll(compliantDaySeats(a, D, LocalTime.of(4, 0)));
        facts.add(dayConfig(a, D_MINUS_1, LocalTime.MIDNIGHT));
        facts.add(dayConfig(a, D, LocalTime.MIDNIGHT));
        facts.add(scheduleConfig(SchedulingMode.SLOT, 660, LocalTime.MIDNIGHT));
        facts.add(waiver(a, D_MINUS_1));

        verifier.verifyThat(ScheduleConstraintProvider::minimumRestSlot)
                .given(facts.toArray())
                .penalizesBy(60);
    }

    @Test
    @DisplayName("a waiver on D for a different agent does not clear this agent's violation")
    void waiverForDifferentAgent_doesNotClear() {
        Agent a = agent();
        Agent other = agent();
        List<Object> facts = new ArrayList<>();
        facts.addAll(compliantDaySeats(a, D_MINUS_1, LocalTime.of(9, 0)));
        facts.addAll(compliantDaySeats(a, D, LocalTime.of(4, 0)));
        facts.add(dayConfig(a, D_MINUS_1, LocalTime.MIDNIGHT));
        facts.add(dayConfig(a, D, LocalTime.MIDNIGHT));
        facts.add(scheduleConfig(SchedulingMode.SLOT, 660, LocalTime.MIDNIGHT));
        facts.add(waiver(other, D));

        verifier.verifyThat(ScheduleConstraintProvider::minimumRestSlot)
                .given(facts.toArray())
                .penalizesBy(60);
    }

    @Test
    @DisplayName("one waiver on D clears exactly the D-into-D+1 pair of two consecutive violations, not both")
    void twoConsecutiveViolatingPairs_oneWaiverOnD_clearsExactlyOnePair() {
        Agent a = agent();
        LocalDate dPlus1 = D.plusDays(1);
        List<Object> facts = new ArrayList<>();
        facts.addAll(compliantDaySeats(a, D_MINUS_1, LocalTime.of(13, 0))); // ends 22:00
        facts.addAll(compliantDaySeats(a, D, LocalTime.of(6, 0))); // 06:00-15:00 -- gap(D-1,D) 480
        facts.addAll(compliantDaySeats(a, dPlus1, LocalTime.of(1, 0))); // 01:00-10:00 -- gap(D,D+1) 600
        facts.add(dayConfig(a, D_MINUS_1, LocalTime.MIDNIGHT));
        facts.add(dayConfig(a, D, LocalTime.MIDNIGHT));
        facts.add(dayConfig(a, dPlus1, LocalTime.MIDNIGHT));
        facts.add(scheduleConfig(SchedulingMode.SLOT, 660, LocalTime.MIDNIGHT));
        facts.add(waiver(a, D));

        // Without the waiver: gap(D-1,D)=480 (shortfall 180) and gap(D,D+1)=600 (shortfall 60),
        // total 240. Waiving D clears only the pair whose successor business date is D -- the
        // D-1->D pair -- leaving the D->D+1 pair's 60-minute shortfall penalised.
        verifier.verifyThat(ScheduleConstraintProvider::minimumRestSlot)
                .given(facts.toArray())
                .penalizesBy(60);
    }

    @Test
    @DisplayName("a desk with no waivers at all behaves identically to before this plan -- shortGap fixture unaffected")
    void emptyWaiverList_behavesIdenticallyToBeforeThisPlan() {
        Agent a = agent();
        List<Object> facts = new ArrayList<>();
        facts.addAll(compliantDaySeats(a, D_MINUS_1, LocalTime.of(9, 0)));
        facts.addAll(compliantDaySeats(a, D, LocalTime.of(4, 0)));
        facts.add(dayConfig(a, D_MINUS_1, LocalTime.MIDNIGHT));
        facts.add(dayConfig(a, D, LocalTime.MIDNIGHT));
        facts.add(scheduleConfig(SchedulingMode.SLOT, 660, LocalTime.MIDNIGHT));

        verifier.verifyThat(ScheduleConstraintProvider::minimumRestSlot)
                .given(facts.toArray())
                .penalizesBy(60);
    }

    @Test
    @DisplayName("a pair with adequate rest and a waiver on its successor date is still zero matches")
    void adequateRestWithWaiverOnSuccessorDate_stillZeroMatches() {
        Agent a = agent();
        List<Object> facts = new ArrayList<>();
        facts.addAll(compliantDaySeats(a, D_MINUS_1, LocalTime.of(9, 0))); // ends 18:00
        facts.addAll(compliantDaySeats(a, D, LocalTime.of(5, 0))); // starts 05:00 -- gap exactly 660
        facts.add(dayConfig(a, D_MINUS_1, LocalTime.MIDNIGHT));
        facts.add(dayConfig(a, D, LocalTime.MIDNIGHT));
        facts.add(scheduleConfig(SchedulingMode.SLOT, 660, LocalTime.MIDNIGHT));
        facts.add(waiver(a, D));

        verifier.verifyThat(ScheduleConstraintProvider::minimumRestSlot)
                .given(facts.toArray())
                .penalizesBy(0);
    }
}
