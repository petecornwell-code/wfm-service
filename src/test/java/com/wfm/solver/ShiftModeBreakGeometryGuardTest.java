package com.wfm.solver;

import ai.timefold.solver.test.api.score.stream.ConstraintVerifier;
import com.wfm.model.Agent;
import com.wfm.model.AgentAssignment;
import com.wfm.model.AgentDayConfig;
import com.wfm.model.AgentShiftAssignment;
import com.wfm.model.BreakAlignment;
import com.wfm.model.Schedule;
import com.wfm.model.ScheduleConfig;
import com.wfm.model.SchedulingMode;
import com.wfm.model.ShiftBandPair;
import com.wfm.model.ShiftTemplate;
import com.wfm.model.ShiftTemplateBreakBand;
import com.wfm.model.Timeslot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * (Phase 15, plan 15-13, gap closure G-15-10) A LIVE GUARD, promoted from the diagnose-only
 * {@code .planning/debug/characterising-tests/ShiftModeBreakGeometryCharacterisationTest.java}
 * (see {@code DISPOSITION.md} in that directory). The rename is deliberate: the old name said the
 * system HAS this property; this name says the system is HELD to it.
 *
 * <p><strong>What this file proves.</strong> The five slot-mode break constraints
 * ({@code exactlyOneBreak}, {@code breakDuration}, {@code breakBlockedWindow},
 * {@code breakStartAlignment}) are genuinely inert in SHIFT mode on every geometry this file can
 * construct — the mode gate works, exactly as designed. Test 2 additionally proves the gate did
 * not merely disable a no-op: the SAME constraint (`exactlyOneBreak`) still ranks the three
 * geometries strictly in SLOT mode, so what SHIFT mode lost by gating those constraints off was a
 * real, working signal, not dead code.
 *
 * <p><strong>What this file does NOT prove, and never did.</strong> It does not show that SHIFT
 * mode replaced that signal with anything. It didn't, and shouldn't — see the class-level
 * conclusion below. There is no per-agent geometry term in SHIFT mode; the property that actually
 * governs an agent's non-worked time at feasibility is a CONJUNCTION of three OTHER hard
 * constraints, asserted directly and observably by
 * {@link ShiftDeskEndToEndRegressionTest#shapeCompleteDesk_solvesToZeroHard_neverCarriesResidualEnvelopePenalty_andIsContiguous()}
 * (plan 15-13, Task 1): at a zero-hard solve, {@code AgentShiftAssignment.getEligibleShiftBandPairs}'
 * exact netHours-equals-effectiveHours value range (D-04) plus the two hard contracted-hours
 * constraints plus {@code shiftEnvelopeCompliance} jointly force held seats to equal legal slots
 * exactly, walked outside the score director in both directions. These two files are named here so
 * they are discoverable from each other — this file proves what does NOT price geometry; that one
 * proves what DOES force it to be correct anyway, once the desk is feasible at all.
 *
 * <p><strong>The report-layer case is deliberately ABSENT from this file.</strong> The original
 * characterising test's fifth case ({@code reportLayer_gapDerivedBreaks_relabelEveryHoleAsABreak})
 * asserted that {@code ScheduleOutputService.findBreaks} relabels every seat-gap as a "break",
 * including gaps strictly outside the assigned envelope — the live desk's "adds breaks to fill in
 * the gaps" symptom. Plan 15-10 REPLACED that behaviour (the report layer now reads the
 * authoritative template span and band-derived break instead of re-deriving both from seat gaps),
 * so porting the old case forward here would leave a live test asserting that a seat gap is a
 * break — exactly the defect plan 15-10 fixed. Its replacement is
 * {@code ScheduleOutputServiceShiftReportingTest#buildAgentSchedule_strayOutOfEnvelopeSeat_reportsExactlyOneBandShapedBreak}
 * and its sibling cases in that same class (span, divergence, clean agent-day, slot-mode
 * invariance) — see {@code src/test/java/com/wfm/service/ScheduleOutputServiceShiftReportingTest.java}.
 */
class ShiftModeBreakGeometryGuardTest {

    private final ConstraintVerifier<ScheduleConstraintProvider, Schedule> verifier =
            ConstraintVerifier.build(new ScheduleConstraintProvider(), Schedule.class,
                    AgentAssignment.class, AgentShiftAssignment.class);

    private static final LocalDate DAY = LocalDate.of(2026, 9, 7);
    private static final int INCREMENT = 60;

    /** The desk's operating window — deliberately WIDER than the envelope, as on the live desk. */
    private static final LocalTime OPERATING_START = LocalTime.of(8, 0);
    private static final LocalTime OPERATING_END = LocalTime.of(21, 0);

    /** The "Late" template from the live screenshot (src/main/resources/sample-data/Example.png). */
    private static final LocalTime ENVELOPE_START = LocalTime.of(12, 0);
    private static final LocalTime ENVELOPE_END = LocalTime.of(21, 0);
    private static final int BAND_OFFSET_MINUTES = 240;   // break 16:00
    private static final int BAND_DURATION_MINUTES = 60;  // .. to 17:00

    private static final BigDecimal CONTRACTED_HOURS = new BigDecimal("8.00"); // == the pair's netHours
    private static final BigDecimal BREAK_MIN_SHIFT_HOURS = new BigDecimal("4.00");
    private static final BigDecimal BREAK_BLOCKED_HOURS = new BigDecimal("1.00");

    private static final List<LocalTime> SANE = times(12, 13, 14, 15, 17, 18, 19, 20);
    private static final List<LocalTime> SCATTERED = times(8, 12, 14, 15, 17, 18, 19, 20);
    private static final List<LocalTime> EDGE = times(8, 12, 13, 14, 15, 17, 18, 19);

    // ------------------------------------------------------------------
    //  1. The mode gate works: every gated constraint is silent on every geometry in SHIFT mode
    // ------------------------------------------------------------------

    @Test
    @DisplayName("GUARD: every gated break constraint stays silent on every geometry in SHIFT mode -- the gate works")
    void shiftMode_gatedBreakConstraints_areSilentOnEveryGeometry() {
        for (List<LocalTime> geometry : List.of(SANE, SCATTERED, EDGE)) {
            assertPenalty(ScheduleConstraintProvider::exactlyOneBreak, geometry, SchedulingMode.SHIFT, 0);
            assertPenalty(ScheduleConstraintProvider::breakDuration, geometry, SchedulingMode.SHIFT, 0);
            assertPenalty(ScheduleConstraintProvider::breakBlockedWindow, geometry, SchedulingMode.SHIFT, 0);
            assertPenalty(ScheduleConstraintProvider::breakStartAlignment, geometry, SchedulingMode.SHIFT, 0);
        }
    }

    // ------------------------------------------------------------------
    //  2. The gate did not disable a no-op: SLOT mode still ranks the three geometries strictly
    // ------------------------------------------------------------------

    @Test
    @DisplayName("GUARD: SLOT mode still ranks sane < edge < scattered -- proves the gated signal was real, not dead code")
    void slotMode_exactlyOneBreak_ranksTheThreeGeometries() {
        // SANE: one gap (16:00), exactly the 1-slot expected break -> clean.
        assertPenalty(ScheduleConstraintProvider::exactlyOneBreak, SANE, SchedulingMode.SLOT, 0);

        // EDGE: span 08:00-20:00, gaps at {09,10,11} and {16} -> 2 gaps, 4 gap slots vs 1 expected.
        assertPenalty(ScheduleConstraintProvider::exactlyOneBreak, EDGE, SchedulingMode.SLOT, 3);

        // SCATTERED: span 08:00-21:00, gaps at {09,10,11}, {13}, {16} -> 3 gaps, 5 gap slots vs 1.
        assertPenalty(ScheduleConstraintProvider::exactlyOneBreak, SCATTERED, SchedulingMode.SLOT, 4);
    }

    // ------------------------------------------------------------------
    //  3. Flatness while infeasible is REAL and has no gradient -- cosmetic, not causal
    // ------------------------------------------------------------------

    /**
     * REFRAMED (was: "the core finding"; now: a bounded, dispositioned observation). This proves
     * SHIFT mode cannot distinguish SCATTERED from EDGE while a solve is ALREADY infeasible (the
     * out-of-envelope seat is present in both). That flatness is real, and it means geometry has
     * no gradient defending it during the infeasible transient.
     *
     * <p><strong>Disposition (settled, not re-litigated by this plan).</strong> This is a cosmetic
     * property of an ALREADY-BROKEN schedule, not an independent defect and not the cause of the
     * breakage. Plan 15-11's fix is to PREVENT the infeasibility (the seat-supply gate refuses a
     * desk that cannot reach zero hard, before any solve), not to price the geometry of a
     * transient state a correctly-gated desk should never reach or pass through for long.
     * Restoring the gated slot-mode break constraints in SHIFT mode was considered during gap
     * closure and REJECTED: it would fight the envelope model (an occupancy obligation the model
     * does not otherwise have) and could make an already under-supplied desk permanently
     * unsolvable rather than cleanly refused. If this is ever revisited, the ceiling is a SOFT
     * tie-break candidate at most — never a hard constraint.
     */
    @Test
    @DisplayName("GUARD (reframed): scattered and edge are indistinguishable WHILE infeasible -- cosmetic, no gradient, not the defect's cause")
    void shiftMode_scatteredAndEdge_scoreIdenticallyEverywhere_cosmeticWhileInfeasible() {
        // Identical seat count => contractedHoursOver/Under (private, cardinality-only: they read
        // count() against expectedWorkSlots and nothing else) are identical by construction.
        assertThat(SCATTERED).hasSameSizeAs(EDGE);

        record Named(String name,
                     java.util.function.BiFunction<ScheduleConstraintProvider,
                             ai.timefold.solver.core.api.score.stream.ConstraintFactory,
                             ai.timefold.solver.core.api.score.stream.Constraint> fn) {}

        List<Named> reachable = List.of(
                new Named("exactlyOneBreak", ScheduleConstraintProvider::exactlyOneBreak),
                new Named("breakDuration", ScheduleConstraintProvider::breakDuration),
                new Named("breakBlockedWindow", ScheduleConstraintProvider::breakBlockedWindow),
                new Named("breakStartAlignment", ScheduleConstraintProvider::breakStartAlignment),
                new Named("shiftEnvelopeCompliance", ScheduleConstraintProvider::shiftEnvelopeCompliance),
                new Named("bandCapacity", ScheduleConstraintProvider::bandCapacity),
                new Named("breakClustering", ScheduleConstraintProvider::breakClustering),
                new Named("honourPreferredBreakTime", ScheduleConstraintProvider::honourPreferredBreakTime));

        for (Named c : reachable) {
            int scattered = penaltyOf(c.fn(), SCATTERED, SchedulingMode.SHIFT);
            int edge = penaltyOf(c.fn(), EDGE, SchedulingMode.SHIFT);
            assertThat(scattered)
                    .as("%s must be shown to be BLIND to where the unworked in-envelope slot falls "
                            + "WHILE the desk is already infeasible (an out-of-envelope seat is "
                            + "present in both geometries)", c.name())
                    .isEqualTo(edge);
        }

        // Guard against a VACUOUS pass: the two constraints that are actually live in shift mode
        // must be firing non-zero on both geometries, so "identical" means "sees both and cannot
        // tell them apart", not "sees neither".
        assertThat(penaltyOf(ScheduleConstraintProvider::shiftEnvelopeCompliance, SCATTERED, SchedulingMode.SHIFT))
                .as("shiftEnvelopeCompliance must be live (the 08:00 seat), not silent")
                .isEqualTo(1);
        assertThat(penaltyOf(ScheduleConstraintProvider::breakClustering, SCATTERED, SchedulingMode.SHIFT))
                .as("breakClustering must be live (1 agent on break, 0 seated at 16:00), not silent")
                .isEqualTo(1);
    }

    // ------------------------------------------------------------------
    //  4. shiftEnvelopeCompliance prices the illegal seat, never the compensating hole it forces
    // ------------------------------------------------------------------

    @Test
    @DisplayName("GUARD: envelope compliance charges 1 for the out-of-envelope seat and 0 for the hole it forces")
    void shiftMode_envelopeCompliance_pricesTheSeatNotTheHole() {
        assertPenalty(ScheduleConstraintProvider::shiftEnvelopeCompliance, SANE, SchedulingMode.SHIFT, 0);

        // One seat at 08:00 sits outside 12:00-21:00 -> exactly 1, in BOTH geometries. The
        // compensating unworked in-envelope slot that contractedHoursOver (ofHard 1001) forces the
        // agent to surrender is free wherever it lands -- until plan 15-11's supply gate refuses
        // the desk before this state can ever be reached by a completed solve.
        assertPenalty(ScheduleConstraintProvider::shiftEnvelopeCompliance, SCATTERED, SchedulingMode.SHIFT, 1);
        assertPenalty(ScheduleConstraintProvider::shiftEnvelopeCompliance, EDGE, SchedulingMode.SHIFT, 1);
    }

    // ------------------------------------------------------------------
    //  Fixture plumbing (unchanged from the retired characterising test)
    // ------------------------------------------------------------------

    private void assertPenalty(
            java.util.function.BiFunction<ScheduleConstraintProvider,
                    ai.timefold.solver.core.api.score.stream.ConstraintFactory,
                    ai.timefold.solver.core.api.score.stream.Constraint> constraint,
            List<LocalTime> geometry, SchedulingMode mode, int expected) {
        assertThat(penaltyOf(constraint, geometry, mode)).isEqualTo(expected);
    }

    private int penaltyOf(
            java.util.function.BiFunction<ScheduleConstraintProvider,
                    ai.timefold.solver.core.api.score.stream.ConstraintFactory,
                    ai.timefold.solver.core.api.score.stream.Constraint> constraint,
            List<LocalTime> geometry, SchedulingMode mode) {
        int[] observed = new int[1];
        for (int candidate = 0; candidate <= 64; candidate++) {
            try {
                verifier.verifyThat(constraint).given(facts(geometry, mode)).penalizesBy(candidate);
                observed[0] = candidate;
                return observed[0];
            } catch (AssertionError ignored) {
                // keep probing -- ConstraintVerifier exposes no "read the value" accessor
            }
        }
        throw new AssertionError("penalty exceeded the 64 probe ceiling for geometry " + geometry);
    }

    /** Every fact/entity the targeted constraints need, for one agent holding {@code geometry}. */
    private Object[] facts(List<LocalTime> geometry, SchedulingMode mode) {
        Agent agent = new Agent();
        agent.setId(UUID.randomUUID());

        ShiftTemplate template = new ShiftTemplate();
        template.setId(UUID.randomUUID());
        template.setName("Late");
        template.setStartTime(ENVELOPE_START);
        template.setEndTime(ENVELOPE_END);
        template.setEffectiveFrom(LocalDate.of(2020, 1, 1));

        ShiftTemplateBreakBand band = new ShiftTemplateBreakBand();
        band.setId(UUID.randomUUID());
        band.setShiftTemplate(template);
        band.setOffsetMinutes(BAND_OFFSET_MINUTES);
        band.setDurationMinutes(BAND_DURATION_MINUTES);

        // One Timeslot instance per column, shared by seats so groupBy keys line up.
        Map<LocalTime, Timeslot> slots = new LinkedHashMap<>();
        for (LocalTime t = OPERATING_START; t.isBefore(OPERATING_END); t = t.plusMinutes(INCREMENT)) {
            Timeslot ts = new Timeslot();
            ts.setId(UUID.randomUUID());
            ts.setDate(DAY);
            ts.setStartTime(t);
            ts.setEndTime(t.plusMinutes(INCREMENT));
            slots.put(t, ts);
        }

        List<Object> facts = new ArrayList<>(slots.values());

        for (LocalTime start : geometry) {
            AgentAssignment seat = new AgentAssignment();
            seat.setId(UUID.randomUUID());
            seat.setAgent(agent);
            seat.setTimeslot(slots.get(start));
            facts.add(seat);
        }

        AgentShiftAssignment shiftRow = new AgentShiftAssignment();
        shiftRow.setId(UUID.randomUUID());
        shiftRow.setAgent(agent);
        shiftRow.setDate(DAY);
        shiftRow.setShiftBandPair(new ShiftBandPair(template, band));
        facts.add(shiftRow);

        facts.add(new AgentDayConfig(agent.getId(), DAY, CONTRACTED_HOURS, INCREMENT,
                BAND_DURATION_MINUTES, BREAK_MIN_SHIFT_HOURS, BREAK_BLOCKED_HOURS,
                BreakAlignment.ON_HOUR, 130, 70));

        facts.add(new ScheduleConfig(INCREMENT, OPERATING_START, OPERATING_END,
                BAND_DURATION_MINUTES, BREAK_MIN_SHIFT_HOURS, BREAK_BLOCKED_HOURS,
                BreakAlignment.ON_HOUR, 20, CONTRACTED_HOURS, 130, 70, mode));

        return facts.toArray();
    }

    private static List<LocalTime> times(int... hours) {
        List<LocalTime> out = new ArrayList<>();
        for (int h : hours) {
            out.add(LocalTime.of(h, 0));
        }
        return List.copyOf(out);
    }
}
