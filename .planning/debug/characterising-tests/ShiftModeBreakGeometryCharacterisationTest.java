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
 * CHARACTERISATION ONLY — this test asserts what the code CURRENTLY DOES, not what it should do.
 * It is diagnostic evidence for debug session
 * {@code .planning/debug/shift-mode-break-geometry-ungoverned.md}. It is expected to PASS on the
 * defective code and it must be REPLACED (not merely kept green) by whatever gap closure lands.
 *
 * <p><b>The question it settles.</b> Once ENVL-05 gated the five slot-mode break constraints off
 * for SHIFT desks, what governs the geometry of an agent's non-worked time? The answer this file
 * demonstrates is: nothing prices it. Contiguity in shift mode is only an arithmetic by-product of
 * three independent hard constraints holding SIMULTANEOUSLY —
 * {@code shiftEnvelopeCompliance} (held seats are a subset of the legal slots),
 * {@code contractedHoursOver} and {@code contractedHoursUnder} (the held count equals the expected
 * count) — combined with
 * {@code AgentShiftAssignment.getEligibleShiftBandPairs}' exact netHours == effectiveHours value
 * range, which makes the legal count equal the expected count. Only the CONJUNCTION forces
 * "held == legal", and hence contiguity. No constraint expresses geometry directly, so the property
 * has no gradient defending it: the instant one conjunct is violated, the unworked slots scatter at
 * zero marginal cost.
 *
 * <p><b>The fixture is the live UAT screenshot</b> ({@code src/main/resources/sample-data/Example.png},
 * group "Late · 12:00–21:00"), reduced to one agent: a 9h envelope, a 1h band at +240m (16:00–17:00),
 * an agent contracted to exactly the 8h net. Three geometries, all holding exactly 8 seats:
 *
 * <pre>
 *   column      08 09 10 11 | 12 13 14 15 16 17 18 19 20      seats  outside envelope
 *   SANE         .  .  .  . |  W  W  W  W  b  W  W  W  W        8           0
 *   SCATTERED    W  .  .  . |  W  .  W  W  b  W  W  W  W        8           1
 *   EDGE         W  .  .  . |  W  W  W  W  b  W  W  W  .        8           1
 *                                    ^                    ^
 *                          hole mid-run (the defect)   hole at the envelope edge
 * </pre>
 *
 * SCATTERED is what the live desk actually produced for Evelina Yasinchuk and Melina Noemi Aparicio.
 * EDGE is the operationally sane way to pay the exact same price. The two are one swap move apart.
 */
class ShiftModeBreakGeometryCharacterisationTest {

    private final ConstraintVerifier<ScheduleConstraintProvider, Schedule> verifier =
            ConstraintVerifier.build(new ScheduleConstraintProvider(), Schedule.class,
                    AgentAssignment.class, AgentShiftAssignment.class);

    private static final LocalDate DAY = LocalDate.of(2026, 9, 7);
    private static final int INCREMENT = 60;

    /** The desk's operating window — deliberately WIDER than the envelope, as on the live desk. */
    private static final LocalTime OPERATING_START = LocalTime.of(8, 0);
    private static final LocalTime OPERATING_END = LocalTime.of(21, 0);

    /** The "Late" template from the screenshot. */
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
    //  1. The five gated constraints are silent on ALL THREE geometries in SHIFT mode
    // ------------------------------------------------------------------

    @Test
    @DisplayName("SHIFT: every gated break constraint scores all three geometries at 0 — nothing replaced them")
    void shiftMode_gatedBreakConstraints_areSilentOnEveryGeometry() {
        for (List<LocalTime> geometry : List.of(SANE, SCATTERED, EDGE)) {
            assertPenalty(ScheduleConstraintProvider::exactlyOneBreak, geometry, SchedulingMode.SHIFT, 0);
            assertPenalty(ScheduleConstraintProvider::breakDuration, geometry, SchedulingMode.SHIFT, 0);
            assertPenalty(ScheduleConstraintProvider::breakBlockedWindow, geometry, SchedulingMode.SHIFT, 0);
            assertPenalty(ScheduleConstraintProvider::breakStartAlignment, geometry, SchedulingMode.SHIFT, 0);
        }
    }

    // ------------------------------------------------------------------
    //  2. SLOT mode DOES rank the three geometries — the gate removed a real signal
    // ------------------------------------------------------------------

    @Test
    @DisplayName("SLOT: exactlyOneBreak ranks sane < edge < scattered — the exact signal SHIFT mode lost")
    void slotMode_exactlyOneBreak_ranksTheThreeGeometries() {
        // SANE: one gap (16:00), exactly the 1-slot expected break -> clean.
        assertPenalty(ScheduleConstraintProvider::exactlyOneBreak, SANE, SchedulingMode.SLOT, 0);

        // EDGE: span 08:00-20:00, gaps at {09,10,11} and {16} -> 2 gaps, 4 gap slots vs 1 expected.
        assertPenalty(ScheduleConstraintProvider::exactlyOneBreak, EDGE, SchedulingMode.SLOT, 3);

        // SCATTERED: span 08:00-21:00, gaps at {09,10,11}, {13}, {16} -> 3 gaps, 5 gap slots vs 1.
        assertPenalty(ScheduleConstraintProvider::exactlyOneBreak, SCATTERED, SchedulingMode.SLOT, 4);
    }

    // ------------------------------------------------------------------
    //  3. THE CORE FINDING — in SHIFT mode nothing distinguishes SCATTERED from EDGE
    // ------------------------------------------------------------------

    @Test
    @DisplayName("SHIFT: scattered and edge geometries are indistinguishable to every reachable constraint")
    void shiftMode_scatteredAndEdge_scoreIdenticallyEverywhere() {
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
                    .as("%s must be shown to be BLIND to where the unworked in-envelope slot falls",
                            c.name())
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
    //  4. shiftEnvelopeCompliance prices the illegal seat, never the hole it forces
    // ------------------------------------------------------------------

    @Test
    @DisplayName("SHIFT: envelope compliance charges 1 for the out-of-envelope seat and 0 for the hole")
    void shiftMode_envelopeCompliance_pricesTheSeatNotTheHole() {
        assertPenalty(ScheduleConstraintProvider::shiftEnvelopeCompliance, SANE, SchedulingMode.SHIFT, 0);

        // One seat at 08:00 sits outside 12:00-21:00 -> exactly 1, in BOTH geometries. The
        // compensating unworked in-envelope slot that contractedHoursOver (ofHard 1001) forces the
        // agent to surrender is free wherever it lands.
        assertPenalty(ScheduleConstraintProvider::shiftEnvelopeCompliance, SCATTERED, SchedulingMode.SHIFT, 1);
        assertPenalty(ScheduleConstraintProvider::shiftEnvelopeCompliance, EDGE, SchedulingMode.SHIFT, 1);
    }

    // ------------------------------------------------------------------
    //  5. The report layer relabels every hole as a break (H4)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("report layer: gap-derived break detection turns the SCATTERED geometry into five 'B' cells")
    void reportLayer_gapDerivedBreaks_relabelEveryHoleAsABreak() {
        // Mirrors ScheduleOutputService.findBreaks exactly: a BreakDetail per discontinuity between
        // consecutive HELD seats. It never reads the assigned band, so an out-of-envelope seat
        // stretches the agent's span leftward and converts the whole non-working run into "breaks".
        assertThat(gapSlotsInsideSpan(SANE))
                .as("the sane geometry yields exactly the one real band break")
                .containsExactly(LocalTime.of(16, 0));

        assertThat(gapSlotsInsideSpan(SCATTERED))
                .as("matches Example.png: three span-artifact cells, one real hole, one real break")
                .containsExactly(LocalTime.of(9, 0), LocalTime.of(10, 0), LocalTime.of(11, 0),
                        LocalTime.of(13, 0), LocalTime.of(16, 0));

        // THE H4 DISCRIMINATOR. Three of the five 'B' cells sit OUTSIDE the 12:00-21:00 envelope
        // entirely, so 'B' can be neither "assigned break band" nor "empty slot inside the
        // envelope". It is strictly "hole between two consecutive HELD seats" — a span artifact.
        // Remove the single illegal 08:00 seat and all three vanish with no solver change at all.
        assertThat(gapSlotsInsideSpan(SCATTERED).stream()
                .filter(t -> t.isBefore(ENVELOPE_START))
                .toList())
                .as("'B' cells can appear OUTSIDE the envelope — settles H4 against both framings")
                .containsExactly(LocalTime.of(9, 0), LocalTime.of(10, 0), LocalTime.of(11, 0));

        // Exactly ONE 'B' is a real unworked IN-envelope slot (13:00), and exactly one out-of-
        // envelope seat was taken (08:00). That 1:1 pairing is forced arithmetic, not coincidence:
        // contractedHours pins |held| == expectedWorkSlots, and the exact-netHours value range
        // pins |legal| == expectedWorkSlots, so |held \ legal| == |legal \ held| always.
        long realInEnvelopeHoles = gapSlotsInsideSpan(SCATTERED).stream()
                .filter(t -> !t.isBefore(ENVELOPE_START))
                .filter(t -> !t.equals(LocalTime.of(16, 0))) // exclude the genuine band break
                .count();
        long outOfEnvelopeSeats = SCATTERED.stream().filter(t -> t.isBefore(ENVELOPE_START)).count();
        assertThat(realInEnvelopeHoles)
                .as("one surrendered envelope slot per illegal seat — the forced 1:1 pairing")
                .isEqualTo(outOfEnvelopeSeats)
                .isEqualTo(1);
    }

    // ------------------------------------------------------------------
    //  Fixture plumbing
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
                // keep probing — ConstraintVerifier exposes no "read the value" accessor
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

    /** Reimplements ScheduleOutputService.findBreaks' semantics at slot granularity. */
    private static List<LocalTime> gapSlotsInsideSpan(List<LocalTime> geometry) {
        List<LocalTime> sorted = new ArrayList<>(geometry);
        sorted.sort(LocalTime::compareTo);
        List<LocalTime> gaps = new ArrayList<>();
        for (LocalTime t = sorted.get(0); t.isBefore(sorted.get(sorted.size() - 1)); t = t.plusMinutes(INCREMENT)) {
            if (!sorted.contains(t)) {
                gaps.add(t);
            }
        }
        return gaps;
    }

    private static List<LocalTime> times(int... hours) {
        List<LocalTime> out = new ArrayList<>();
        for (int h : hours) {
            out.add(LocalTime.of(h, 0));
        }
        return List.copyOf(out);
    }
}
