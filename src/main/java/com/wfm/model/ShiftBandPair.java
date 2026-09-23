package com.wfm.model;

import java.math.BigDecimal;
import java.time.LocalTime;
import com.wfm.util.DayWindow;

/**
 * Immutable problem fact — a live {@code (template, band)} pair the solver may choose for one
 * agent-day (D-04). {@code band} may be {@code null}, meaning the template has no break (P-02:
 * "zero bands = no break"). Every value the solver reads that is not a genuine planning variable
 * is a plain immutable record in this codebase ({@link ScheduleConfig}, {@link AgentDayConfig},
 * {@link TimeslotDemandConfig}) — this follows that exactly, never a mutable class.
 *
 * <p>Identity is the record's own component equality: {@link ShiftTemplate} and
 * {@link ShiftTemplateBreakBand} are JPA entities with no {@code equals()} override, so two
 * templates with identical start/end times but different ids are never interchangeable values
 * (P-12) — this is load-bearing for the entity-level value range and for ENVL-02's coupling.
 */
public record ShiftBandPair(ShiftTemplate template, ShiftTemplateBreakBand band) {

    /**
     * True when {@code ts} sits inside this pair's envelope and — when {@code band} is
     * non-null — does not overlap the band's break interval. Both ends are half-open: a
     * timeslot starting exactly at the envelope start is inside, one starting exactly at the
     * envelope end is outside (its own end necessarily runs past the envelope end); a timeslot
     * starting exactly at the band's break start is forbidden, one starting exactly at the
     * band's break end is legal. Compares {@link LocalTime} values directly — no rounding here:
     * timeslot and template boundaries are both already grid-aligned by Phase 14's D-02 rule, and
     * {@code ScheduleConstraintProvider} already carries two rounding modes (HALF_UP and
     * CEILING) in other constraints; this predicate must not introduce a third.
     *
     * <p>Delegates to {@link #covers(LocalTime, LocalTime, Integer, Integer, LocalTime, LocalTime)}
     * with no behavioural change — see that method for the load-bearing single-implementation
     * discipline (D-08 / Phase 15 plan 10, T-15-10-04).
     */
    public boolean covers(Timeslot ts) {
        return covers(template.getStartTime(), template.getEndTime(),
                band == null ? null : band.getOffsetMinutes(),
                band == null ? null : band.getDurationMinutes(),
                ts.getStartTime(), ts.getEndTime());
    }

    /**
     * The static form of the coverage predicate above, taking the four scalars a descriptor
     * carries directly rather than the live {@link ShiftTemplate}/{@link ShiftTemplateBreakBand}
     * references this record wraps. {@link #covers(Timeslot)} delegates to this with no
     * behavioural change — this IS the predicate, exactly once.
     *
     * <p>This is the second caller D-08's "one coverage validator/predicate, two callers"
     * discipline requires for envelope/break coverage (Phase 15 plan 10): the report layer
     * ({@code ScheduleOutputService}) holds only a {@code ShiftDescriptor}'s four scalars, never a
     * live template/band pair, so it calls this overload directly instead of re-deriving the
     * arithmetic. Any future change to what "covered" means — boundary semantics, break-window
     * handling, a new edge case — must be made HERE, once, so the solver's {@link #covers(Timeslot)}
     * and the report layer can never disagree about what an envelope covers.
     *
     * <p>{@code bandOffsetMinutes}/{@code bandDurationMinutes} are {@code null} together exactly
     * when the pair has no band ({@code band == null}), preserving the same "null or non-positive
     * duration = whole envelope is covered" short-circuit as the instance method.
     */
    public static boolean covers(LocalTime envelopeStart, LocalTime envelopeEnd,
            Integer bandOffsetMinutes, Integer bandDurationMinutes,
            LocalTime slotStart, LocalTime slotEnd) {
        // DayWindow.contains, not slotEnd.isAfter(envelopeEnd): an envelope ending at midnight
        // stores 00:00, so the raw comparison declared EVERY slot in it out of bounds except the
        // final one -- silently making a midnight-ending shift unseatable.
        if (!DayWindow.contains(envelopeStart, envelopeEnd, slotStart, slotEnd)) {
            return false;
        }
        if (bandOffsetMinutes == null || bandDurationMinutes == null || bandDurationMinutes <= 0) {
            return true;
        }
        LocalTime breakStart = DayWindow.plusWithinDay(envelopeStart, bandOffsetMinutes);
        LocalTime breakEnd = DayWindow.plusWithinDay(breakStart, bandDurationMinutes);
        return !DayWindow.overlaps(slotStart, slotEnd, breakStart, breakEnd);
    }

    /**
     * The ONE distance calculation DRFT-03 requires — {@code ScheduleConstraintProvider
     * .usualShiftConsistency} and {@code ScheduleOutputService.buildDriftReport} both call this,
     * never re-implementing the delta inline (Phase 17, D-01/D-03).
     *
     * <p>D-03: compares the ASSIGNED ENVELOPE START ({@code this.template().getStartTime()}),
     * never a seat-derived earliest {@link com.wfm.model.AgentAssignment} time — the shift IS the
     * envelope in this model, and it never depends on how seats fell inside it. Accepted D-01
     * blind spot, stated here rather than only in planning docs: a template with the same start
     * and a different end reads zero deviation — duration is already policed by the
     * contracted-hours constraints, and the column being adopted ({@code consistent_start_weight})
     * is a start-time column by name.
     *
     * <p>Returns an absolute magnitude — this is what makes D-05's symmetric single-value
     * tolerance band possible: one stored number applied to {@code abs(delta)}, not an early
     * bound and a late bound. Asymmetry (early vs. late) is deferred under D-05, not rejected —
     * addable later without changing what any stored value means.
     *
     * <p>Either argument {@code null} yields zero deviation — a missing envelope start (unassigned
     * shift) or a missing usual-shift target (USHF-04's penalty-free state) both mean "nothing to
     * compare", never a computed distance.
     */
    public static int startDeviationMinutes(LocalTime assignedEnvelopeStart, LocalTime usualStartTime) {
        if (assignedEnvelopeStart == null || usualStartTime == null) {
            return 0;
        }
        return (int) Math.abs(
                java.time.temporal.ChronoUnit.MINUTES.between(usualStartTime, assignedEnvelopeStart));
    }

    /** Net working duration for this specific pair — delegates to the band-parameterised helper. */
    public BigDecimal netHours() {
        return template.getNetHours(band == null ? 0 : band.getDurationMinutes());
    }

    /** Diagnostic label — never parsed, only displayed. */
    public String displayName() {
        return band == null
                ? template.getName()
                : template.getName() + " (+" + band.getOffsetMinutes() + "m break)";
    }
}
