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
import com.wfm.model.Timeslot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 22 plan 22-06 (REST-05) — proves both horizon edges of {@link
 * ScheduleConstraintProvider#minimumRestShift} and {@link ScheduleConstraintProvider#minimumRestSlot}
 * once the pre-horizon predecessor stream (concatenated {@code factory.forEach(RestSpan.class)}) is
 * joined onto the predecessor side only.
 *
 * <p><b>First business date, constrained (D-10).</b> A registered pre-horizon {@link RestSpan}
 * (standing in for {@code RestPredecessorService}'s resolved output) pairs with the period's first
 * in-horizon shift/slot-span exactly like an in-horizon predecessor would. The with-and-without pair
 * is the load-bearing evidence: a bare "scores one match" assertion would pass against a constraint
 * that matched for an unrelated reason, so the paired zero on the identical fixture with no
 * registered pre-horizon spans is what proves the match is genuinely sourced from the concat.
 *
 * <p><b>No ACCEPTED predecessor, unconstrained (D-10).</b> Registering zero pre-horizon
 * {@link RestSpan} facts is exactly what {@code RestPredecessorService.resolvePriorSpans} returns
 * when the preceding business date has no ACCEPTED row — this is the explicit decision D-10 records,
 * not an accident, and is the SAME fixture shape as the "without" half of the with-and-without pair
 * above (both are "no pre-horizon fact registered"); it is named separately here because it answers
 * a different question (is the gap a real unconstrained state, not merely an untested one).
 *
 * <p><b>Last business date, unconstrained on purpose (D-11).</b> A period's last in-horizon shift
 * never gets a successor-side join against anything outside the horizon, because the concat in both
 * constraints is applied to the PREDECESSOR stream only — the successor stream stays in-horizon-only
 * by construction. There is no code path in {@code ScheduleConstraintProvider} that could look
 * forward into an already-accepted LATER period even if one existed; the pair this day's outgoing
 * rest forms is enforced later, when THAT period's own solve treats this one as its day-0
 * predecessor under D-10. A symmetric forward lookahead was considered and declined (CONTEXT.md
 * D-11) specifically to avoid a second query, a second direction for the predicate to get wrong, and
 * constraining a re-solve against a schedule the operator may be about to replace.
 *
 * <p><b>Registered-fact-count (Timefold fact-matching semantics).</b> {@code
 * factory.forEach(RestSpan.class)} reaches ONLY the facts explicitly registered as {@code RestSpan}
 * instances — never the in-horizon spans both constraints build via {@code .map()}, which are
 * stream-produced tuples, not problem facts. {@code ConstraintVerifier} can only target a
 * constraint {@code ScheduleConstraintProvider.defineConstraints} actually registers (a bespoke
 * throwaway lambda is rejected outright — verified directly against the real API), so this is
 * proved through the real {@link ScheduleConstraintProvider#minimumRestShift} with a fixture where
 * two agents exist ONLY as registered facts and a third is a genuine in-horizon pair: the total
 * match count is exactly the sum of both counts, proving neither source is dropped nor duplicated
 * against the other.
 */
class RestHorizonEdgeTest {

    private final ConstraintVerifier<ScheduleConstraintProvider, Schedule> verifier =
            ConstraintVerifier.build(new ScheduleConstraintProvider(), Schedule.class,
                    AgentAssignment.class, AgentShiftAssignment.class);

    private static final LocalDate D_MINUS_1 = LocalDate.of(2026, 9, 7);
    private static final LocalDate D = LocalDate.of(2026, 9, 8);
    private static final LocalDate D_PLUS_1 = LocalDate.of(2026, 9, 9);
    private static final int MINIMUM_REST_MINUTES = 660;

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

    /** A shift row anchored at {@code dayStart} via its own {@link AgentDayConfig} problem fact —
     * mirrors {@code MinimumRestShiftConstraintTest}'s fixture idiom exactly. */
    private static AgentShiftAssignment shiftRow(Agent agent, LocalDate date, ShiftBandPair pair) {
        AgentShiftAssignment sa = new AgentShiftAssignment();
        sa.setId(UUID.randomUUID());
        sa.setAgent(agent);
        sa.setDate(date);
        sa.setShiftBandPair(pair);
        sa.setDayConfig(new AgentDayConfig(agent.getId(), date, new BigDecimal("8.00"), 60, 60,
                new BigDecimal("4.00"), new BigDecimal("1.00"), BreakAlignment.ON_HOUR, 130, 70,
                LocalTime.MIDNIGHT));
        return sa;
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

    private static AgentDayConfig slotDayConfig(Agent agent, LocalDate date) {
        return new AgentDayConfig(agent.getId(), date, new BigDecimal("8.00"), 60, 60,
                new BigDecimal("4.00"), new BigDecimal("1.00"), BreakAlignment.ON_HOUR, 130, 70,
                LocalTime.MIDNIGHT);
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

    private static ScheduleConfig scheduleConfig(SchedulingMode mode, Integer minimumRestMinutes) {
        return new ScheduleConfig(15, LocalTime.of(0, 0), LocalTime.of(23, 59),
                60, new BigDecimal("4.00"), new BigDecimal("1.00"),
                BreakAlignment.ON_HOUR, 20, new BigDecimal("8.00"), 130, 70, mode,
                ScheduleConfig.DEFAULT_CONSISTENCY_TOLERANCE_MINUTES, LocalTime.MIDNIGHT,
                minimumRestMinutes);
    }

    // ------------------------------------------------------------------
    //  First business date, constrained (D-10) -- SHIFT mode
    // ------------------------------------------------------------------

    @Test
    @DisplayName("SHIFT: a registered pre-horizon predecessor pairs with the period's first shift and scores one match")
    void shift_withPriorSpan_firstDayConstrained_oneMatch() {
        Agent a = agent();
        // Stands in for RestPredecessorService's resolved output -- an agent's real pre-horizon
        // shift on D-1, ending too soon before D's shift starts.
        RestSpan priorSpan = new RestSpan(a.getId(), D_MINUS_1, LocalTime.of(14, 0), LocalTime.of(22, 0),
                LocalTime.MIDNIGHT);
        AgentShiftAssignment dShift = shiftRow(a, D, pair(LocalTime.of(6, 0), LocalTime.of(14, 0)));

        verifier.verifyThat(ScheduleConstraintProvider::minimumRestShift)
                .given(priorSpan, dShift, scheduleConfig(SchedulingMode.SHIFT, MINIMUM_REST_MINUTES))
                .penalizesBy(180);
    }

    @Test
    @DisplayName("SHIFT: the identical fixture with NO registered pre-horizon predecessor scores zero -- the paired negative that makes the positive above evidence")
    void shift_withoutPriorSpan_firstDayUnconstrained_zeroMatches() {
        Agent a = agent();
        AgentShiftAssignment dShift = shiftRow(a, D, pair(LocalTime.of(6, 0), LocalTime.of(14, 0)));

        verifier.verifyThat(ScheduleConstraintProvider::minimumRestShift)
                .given(dShift, scheduleConfig(SchedulingMode.SHIFT, MINIMUM_REST_MINUTES))
                .penalizesBy(0);
    }

    // ------------------------------------------------------------------
    //  First business date, constrained (D-10) -- SLOT mode
    // ------------------------------------------------------------------

    @Test
    @DisplayName("SLOT: a registered pre-horizon predecessor pairs with the period's first slot-span and scores one match")
    void slot_withPriorSpan_firstDayConstrained_oneMatch() {
        Agent a = agent();
        RestSpan priorSpan = new RestSpan(a.getId(), D_MINUS_1, LocalTime.of(14, 0), LocalTime.of(22, 0),
                LocalTime.MIDNIGHT);
        Timeslot ts = timeslot(D, LocalTime.of(6, 0), LocalTime.of(14, 0));
        AgentAssignment dSeat = seat(a, ts);

        verifier.verifyThat(ScheduleConstraintProvider::minimumRestSlot)
                .given(priorSpan, dSeat, slotDayConfig(a, D),
                        scheduleConfig(SchedulingMode.SLOT, MINIMUM_REST_MINUTES))
                .penalizesBy(180);
    }

    @Test
    @DisplayName("SLOT: the identical fixture with NO registered pre-horizon predecessor scores zero")
    void slot_withoutPriorSpan_firstDayUnconstrained_zeroMatches() {
        Agent a = agent();
        Timeslot ts = timeslot(D, LocalTime.of(6, 0), LocalTime.of(14, 0));
        AgentAssignment dSeat = seat(a, ts);

        verifier.verifyThat(ScheduleConstraintProvider::minimumRestSlot)
                .given(dSeat, slotDayConfig(a, D), scheduleConfig(SchedulingMode.SLOT, MINIMUM_REST_MINUTES))
                .penalizesBy(0);
    }

    // ------------------------------------------------------------------
    //  Pre-horizon OVERNIGHT predecessor (PF-01, this plan's sixth affected class) -- every
    //  fixture above uses a non-wrapping 14:00-22:00 priorSpan. These three cases are the
    //  wrapping 22:00-06:00 shape: the pre-correction formula measured a 1500-minute gap (above
    //  any configurable minimum, so zero matches); the corrected formula measures the true
    //  60-minute gap, penalised by 600 against a 660-minute minimum.
    // ------------------------------------------------------------------

    @Test
    @DisplayName("SHIFT: a pre-horizon OVERNIGHT predecessor (22:00-06:00) pairs with the period's first shift and scores the true 600-point shortfall -- before the fix this scored zero")
    void shift_preHorizonOvernightPredecessor_firstDayConstrained_oneMatch() {
        Agent a = agent();
        // The pre-horizon shift WRAPPED past the business-day anchor: the true rest is 60
        // minutes (22:00-06:00 into 07:00), not the pre-correction formula's 1500, which
        // produced no match at all against any configurable minimum (REST-05, the audit gap).
        RestSpan priorSpan = new RestSpan(a.getId(), D_MINUS_1, LocalTime.of(22, 0), LocalTime.of(6, 0),
                LocalTime.MIDNIGHT);
        AgentShiftAssignment dShift = shiftRow(a, D, pair(LocalTime.of(7, 0), LocalTime.of(15, 0)));

        verifier.verifyThat(ScheduleConstraintProvider::minimumRestShift)
                .given(priorSpan, dShift, scheduleConfig(SchedulingMode.SHIFT, MINIMUM_REST_MINUTES))
                .penalizesBy(600);
    }

    @Test
    @DisplayName("SLOT: a pre-horizon OVERNIGHT predecessor (22:00-06:00) pairs with the period's first slot-span and scores the true 600-point shortfall -- before the fix this scored zero")
    void slot_preHorizonOvernightPredecessor_firstDayConstrained_oneMatch() {
        Agent a = agent();
        RestSpan priorSpan = new RestSpan(a.getId(), D_MINUS_1, LocalTime.of(22, 0), LocalTime.of(6, 0),
                LocalTime.MIDNIGHT);
        Timeslot ts = timeslot(D, LocalTime.of(7, 0), LocalTime.of(15, 0));
        AgentAssignment dSeat = seat(a, ts);

        verifier.verifyThat(ScheduleConstraintProvider::minimumRestSlot)
                .given(priorSpan, dSeat, slotDayConfig(a, D),
                        scheduleConfig(SchedulingMode.SLOT, MINIMUM_REST_MINUTES))
                .penalizesBy(600);
    }

    @Test
    @DisplayName("an agent marked off on D (day-off/PTO shape) has no successor span against the overnight pre-horizon predecessor -- nothing to measure, nothing to penalise, in either mode (OVNT-03 regression safety)")
    void overnightPriorSpanWithNoSuccessorRowOnD_zeroMatches() {
        Agent a = agent();
        RestSpan priorSpan = new RestSpan(a.getId(), D_MINUS_1, LocalTime.of(22, 0), LocalTime.of(6, 0),
                LocalTime.MIDNIGHT);

        verifier.verifyThat(ScheduleConstraintProvider::minimumRestShift)
                .given(priorSpan, scheduleConfig(SchedulingMode.SHIFT, MINIMUM_REST_MINUTES))
                .penalizesBy(0);

        verifier.verifyThat(ScheduleConstraintProvider::minimumRestSlot)
                .given(priorSpan, scheduleConfig(SchedulingMode.SLOT, MINIMUM_REST_MINUTES))
                .penalizesBy(0);
    }

    // ------------------------------------------------------------------
    //  No ACCEPTED predecessor -- the explicit D-10 decision, not an accident
    // ------------------------------------------------------------------

    @Test
    @DisplayName("no ACCEPTED predecessor: zero registered pre-horizon spans scores zero on the first business date -- D-10's explicit decision, not an oversight")
    void noAcceptedPredecessor_firstDayScoresZero_deliberateNotAccidental() {
        // This is RestPredecessorService.resolvePriorSpans' real return value when the preceding
        // business date has no ACCEPTED accepted_schedule_date row: an empty list, registering no
        // RestSpan facts at all. Asserted separately from the with/without pair above because it
        // answers a different question -- that the resulting unconstrained state is correct
        // behaviour, not merely the absence of a test.
        Agent a = agent();
        AgentShiftAssignment dShift = shiftRow(a, D, pair(LocalTime.of(6, 0), LocalTime.of(14, 0)));

        verifier.verifyThat(ScheduleConstraintProvider::minimumRestShift)
                .given(dShift, scheduleConfig(SchedulingMode.SHIFT, MINIMUM_REST_MINUTES))
                .penalizesBy(0);
    }

    // ------------------------------------------------------------------
    //  Last business date, unconstrained on purpose (D-11)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("last business date: a shift ending late with no successor inside the period scores zero -- D-11's declined forward lookahead, not a missing feature")
    void lastDayUnconstrained_noForwardLookahead_zeroMatches_deliberate() {
        // D-11: the horizon's last day's outgoing rest has no successor INSIDE this period. The
        // pair it would form is enforced later, when the NEXT period's own solve treats this
        // schedule as its day-0 predecessor (D-10) -- not here, and not by a symmetric forward
        // lookahead into an already-accepted later period, which CONTEXT.md D-11 explicitly
        // considered and declined (a second query, a second direction for the predicate to get
        // wrong, and constraining a re-solve against a schedule the operator may be about to
        // replace). There is no RestSpan fact for D_PLUS_1 registered at all -- not even an
        // in-horizon one -- which is exactly what "no forward lookahead is built" means in code.
        Agent a = agent();
        AgentShiftAssignment lastShift = shiftRow(a, D_PLUS_1, pair(LocalTime.of(16, 0), LocalTime.MIDNIGHT));

        verifier.verifyThat(ScheduleConstraintProvider::minimumRestShift)
                .given(lastShift, scheduleConfig(SchedulingMode.SHIFT, MINIMUM_REST_MINUTES))
                .penalizesBy(0);
    }

    // ------------------------------------------------------------------
    //  A waiver on the first business date clears a violation against the pre-horizon predecessor
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a waiver on the period's first business date clears a violation against the pre-horizon predecessor")
    void waiverOnFirstDay_clearsViolationAgainstPriorSpan() {
        Agent a = agent();
        RestSpan priorSpan = new RestSpan(a.getId(), D_MINUS_1, LocalTime.of(14, 0), LocalTime.of(22, 0),
                LocalTime.MIDNIGHT);
        AgentShiftAssignment dShift = shiftRow(a, D, pair(LocalTime.of(6, 0), LocalTime.of(14, 0)));

        verifier.verifyThat(ScheduleConstraintProvider::minimumRestShift)
                .given(priorSpan, dShift, scheduleConfig(SchedulingMode.SHIFT, MINIMUM_REST_MINUTES),
                        waiver(a, D))
                .penalizesBy(0);
    }

    // ------------------------------------------------------------------
    //  Empty pre-horizon list -- identical to the pre-plan fixtures in both modes
    // ------------------------------------------------------------------

    @Test
    @DisplayName("an empty pre-horizon list scores identically to the pre-plan (22-05) fixtures in both modes -- no regression from this plan's concat")
    void emptyPriorList_scoresIdenticallyToPrePlanFixtures_bothModes() {
        Agent a = agent();
        AgentShiftAssignment prev = shiftRow(a, D_MINUS_1, pair(LocalTime.of(14, 0), LocalTime.of(22, 0)));
        AgentShiftAssignment next = shiftRow(a, D, pair(LocalTime.of(6, 0), LocalTime.of(14, 0)));

        // Identical to MinimumRestShiftConstraintTest#gap480_minimum660_penalisedByShortfall --
        // the in-horizon pair alone, no pre-horizon fact registered, same 180-minute shortfall.
        verifier.verifyThat(ScheduleConstraintProvider::minimumRestShift)
                .given(prev, next, scheduleConfig(SchedulingMode.SHIFT, MINIMUM_REST_MINUTES))
                .penalizesBy(180);
    }

    // ------------------------------------------------------------------
    //  Registered-fact-count: forEach(RestSpan.class) reaches exactly the registered facts,
    //  additively alongside (never instead of, and never duplicating) the in-horizon spans.
    // ------------------------------------------------------------------

    /**
     * Timefold's {@code ConstraintVerifier} can only target a constraint that
     * {@link ScheduleConstraintProvider#defineConstraints} actually registers (a bespoke
     * throwaway lambda constraint is rejected with "has no constraint" — verified directly
     * against the real API before writing this test this way), so this proof runs through the
     * real {@link ScheduleConstraintProvider#minimumRestShift} rather than an isolated
     * {@code forEach(RestSpan.class)} probe. Two agents (A, B) exist ONLY as registered
     * pre-horizon {@link RestSpan} facts — no backing {@link AgentShiftAssignment} row for either
     * on {@code D_MINUS_1} at all — so their predecessor candidate can only have reached the join
     * through {@code factory.forEach(RestSpan.class)}. A third agent (C) is a genuine in-horizon
     * pair, sourced the pre-existing way (an {@code AgentShiftAssignment} on both dates, mapped by
     * the local {@code spans} stream). All three violate rest by the identical 180-minute
     * shortfall. The total penalty is exactly {@code 3 * 180}: not more (which an in-horizon
     * tuple being double-counted as if it were also a registered fact, or vice versa, would
     * produce) and not less (which a dropped path would produce) — each source contributes
     * exactly as many matches as the facts it alone is responsible for, independently and
     * additively.
     */
    @Test
    @DisplayName("registered pre-horizon facts and in-horizon spans each contribute exactly their own match count, additively and without duplication")
    void registeredPriorFacts_andInHorizonSpans_eachContributeExactlyTheirOwnCount() {
        Agent a = agent();
        Agent b = agent();
        Agent c = agent();

        // A and B exist ONLY as registered pre-horizon RestSpan facts -- no AgentShiftAssignment
        // row for either on D_MINUS_1, so their predecessor candidate cannot have come from the
        // in-horizon `spans` stream.
        RestSpan priorA = new RestSpan(a.getId(), D_MINUS_1, LocalTime.of(14, 0), LocalTime.of(22, 0),
                LocalTime.MIDNIGHT);
        RestSpan priorB = new RestSpan(b.getId(), D_MINUS_1, LocalTime.of(14, 0), LocalTime.of(22, 0),
                LocalTime.MIDNIGHT);
        AgentShiftAssignment aNext = shiftRow(a, D, pair(LocalTime.of(6, 0), LocalTime.of(14, 0)));
        AgentShiftAssignment bNext = shiftRow(b, D, pair(LocalTime.of(6, 0), LocalTime.of(14, 0)));

        // C is a genuine in-horizon pair -- the pre-existing (pre-Phase-22-06) predecessor source,
        // with no registered RestSpan fact at all.
        AgentShiftAssignment cPrev = shiftRow(c, D_MINUS_1, pair(LocalTime.of(14, 0), LocalTime.of(22, 0)));
        AgentShiftAssignment cNext = shiftRow(c, D, pair(LocalTime.of(6, 0), LocalTime.of(14, 0)));

        verifier.verifyThat(ScheduleConstraintProvider::minimumRestShift)
                .given(priorA, priorB, aNext, bNext, cPrev, cNext,
                        scheduleConfig(SchedulingMode.SHIFT, MINIMUM_REST_MINUTES))
                .penalizesBy(3 * 180);
    }
}
