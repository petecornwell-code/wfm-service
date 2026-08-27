package com.wfm.model;

import java.math.BigDecimal;
import java.time.LocalTime;

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
     */
    public boolean covers(Timeslot ts) {
        LocalTime envelopeStart = template.getStartTime();
        LocalTime envelopeEnd = template.getEndTime();
        LocalTime slotStart = ts.getStartTime();
        LocalTime slotEnd = ts.getEndTime();

        if (slotStart.isBefore(envelopeStart) || slotEnd.isAfter(envelopeEnd)) {
            return false;
        }
        if (band == null || band.getDurationMinutes() <= 0) {
            return true;
        }
        LocalTime breakStart = band.getBreakStartTime(template);
        LocalTime breakEnd = band.getBreakEndTime(template);
        boolean overlapsBreak = slotStart.isBefore(breakEnd) && slotEnd.isAfter(breakStart);
        return !overlapsBreak;
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
