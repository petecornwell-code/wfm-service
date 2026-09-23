package com.wfm.util;

import java.time.LocalTime;

/**
 * The single implementation of "how long is this interval" and "do these intervals overlap" for
 * every scheduling time in this codebase — desk operating windows, timeslots, shift template
 * envelopes and break bands.
 *
 * <h2>Why this class exists</h2>
 *
 * <p>Every scheduling time is stored as a {@link LocalTime}, which has no 24:00 — its maximum is
 * 23:59:59.999999999. A desk whose day runs to midnight therefore has to store its end as
 * {@code 00:00}, the SMALLEST value in the type. Raw {@code Duration.between(15:00, 00:00)} is
 * {@code -900} minutes and raw {@code 00:00.isAfter(23:00)} is {@code false}, so a desk running
 * to midnight silently produced negative envelopes, empty generation loops and coverage checks
 * that could never be satisfied.
 *
 * <h2>The rule</h2>
 *
 * <p>{@code 00:00} is ambiguous in isolation and means different things by POSITION:
 *
 * <ul>
 *   <li>in a <strong>start</strong> position it is the start of the day, minute {@code 0};</li>
 *   <li>in an <strong>end</strong> position it is the end of the day, minute {@link #MINUTES_PER_DAY}.</li>
 * </ul>
 *
 * <p>Callers must therefore pick the function matching the position — {@link #startMinute} or
 * {@link #endMinute} — rather than converting a bare time and hoping. Every interval here is
 * half-open, {@code [start, end)}, which is what makes adjacent timeslots non-overlapping.
 *
 * <p>This class deliberately does NOT model a shift that runs PAST midnight into the next
 * calendar day (22:00&ndash;06:00). That is not a boundary problem but a data-model one: such a
 * shift belongs to two dates, while day-off records, contracted-hours-per-day and the solver's
 * per-day seat model all assume one. {@link #durationMinutes} throws rather than silently
 * returning a wrapped positive number, so the unsupported case fails loudly at the boundary
 * instead of producing a plausible wrong schedule deep in the solver.
 */
public final class DayWindow {

    /** Minutes in a day. The minute-of-day value of an end time of {@code 00:00}. */
    public static final int MINUTES_PER_DAY = 1440;

    private DayWindow() {}

    /**
     * Minute-of-day of a time in a START position, {@code 00:00} &rarr; {@code 0}.
     * Range {@code [0, 1440)}.
     */
    public static int startMinute(LocalTime start) {
        requireNonNull(start, "start");
        return start.getHour() * 60 + start.getMinute();
    }

    /**
     * Minute-of-day of a time in an END position, {@code 00:00} &rarr; {@link #MINUTES_PER_DAY}.
     * Range {@code (0, 1440]}.
     */
    public static int endMinute(LocalTime end) {
        requireNonNull(end, "end");
        return end.equals(LocalTime.MIDNIGHT) ? MINUTES_PER_DAY : end.getHour() * 60 + end.getMinute();
    }

    /**
     * Length of the half-open interval {@code [start, end)} in minutes, correct when {@code end}
     * is midnight.
     *
     * @throws IllegalArgumentException when the interval does not run forward within one day —
     *         i.e. a shift crossing midnight, which this model does not support (see the class
     *         javadoc). Failing here is deliberate: the alternative is a negative duration
     *         travelling silently into net-hours and coverage arithmetic.
     */
    public static int durationMinutes(LocalTime start, LocalTime end) {
        int minutes = endMinute(end) - startMinute(start);
        if (minutes <= 0) {
            throw new IllegalArgumentException(
                    "Interval must run forward within a single day, but got " + start + " to " + end
                            + ". A window ending at midnight is supported (end 00:00); one crossing "
                            + "midnight into the next day is not.");
        }
        return minutes;
    }

    /** True when {@code [start, end)} runs forward within one day — the non-throwing predicate
     *  counterpart of {@link #durationMinutes}, for validators that want to report rather than throw. */
    public static boolean isForwardWithinDay(LocalTime start, LocalTime end) {
        return start != null && end != null && endMinute(end) > startMinute(start);
    }

    /**
     * True when the half-open intervals {@code [s1, e1)} and {@code [s2, e2)} overlap. Intervals
     * that merely touch (one's end equals the other's start) do NOT overlap.
     */
    public static boolean overlaps(LocalTime s1, LocalTime e1, LocalTime s2, LocalTime e2) {
        return startMinute(s1) < endMinute(e2) && endMinute(e1) > startMinute(s2);
    }

    /**
     * True when {@code [innerStart, innerEnd)} lies entirely within {@code [outerStart, outerEnd)}.
     */
    public static boolean contains(LocalTime outerStart, LocalTime outerEnd,
                                   LocalTime innerStart, LocalTime innerEnd) {
        return startMinute(innerStart) >= startMinute(outerStart)
                && endMinute(innerEnd) <= endMinute(outerEnd);
    }

    /**
     * True when {@code start} falls strictly before the END boundary {@code end} — the
     * midnight-correct replacement for {@code start.isBefore(end)} in a generation or scan loop.
     */
    public static boolean startsBefore(LocalTime start, LocalTime end) {
        return startMinute(start) < endMinute(end);
    }

    /**
     * Converts a minute-of-day back to a {@link LocalTime}, mapping {@link #MINUTES_PER_DAY} to
     * {@link LocalTime#MIDNIGHT} so a round trip through {@link #endMinute} is lossless.
     *
     * @throws IllegalArgumentException outside {@code [0, 1440]}.
     */
    public static LocalTime toLocalTime(int minuteOfDay) {
        if (minuteOfDay < 0 || minuteOfDay > MINUTES_PER_DAY) {
            throw new IllegalArgumentException(
                    "minuteOfDay must be within [0, " + MINUTES_PER_DAY + "] but was " + minuteOfDay);
        }
        if (minuteOfDay == MINUTES_PER_DAY) {
            return LocalTime.MIDNIGHT;
        }
        return LocalTime.of(minuteOfDay / 60, minuteOfDay % 60);
    }

    /**
     * Adds {@code minutes} to a time in a START position, returning {@link LocalTime#MIDNIGHT}
     * when the result lands exactly on the end of the day.
     *
     * <p>Unlike {@link LocalTime#plusMinutes}, which wraps silently ({@code 23:00 + 120 = 01:00},
     * an earlier time that then fails every ordering check downstream), this throws past the end
     * of the day.
     */
    public static LocalTime plusWithinDay(LocalTime base, int minutes) {
        return toLocalTime(startMinute(base) + minutes);
    }

    private static void requireNonNull(LocalTime value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
    }
}
