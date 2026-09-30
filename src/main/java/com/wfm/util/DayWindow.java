package com.wfm.util;

import java.time.LocalDate;
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
 *
 * <h2>A business day that begins somewhere other than midnight (BDAY-03)</h2>
 *
 * <p>Every function above this heading assumes a business day starts at {@code 00:00}. A desk can
 * declare a different day-start anchor (BDAY-01), and the functions below this heading are the
 * anchor-aware counterparts: {@link #startMinuteFromDayStart}, {@link #endMinuteFromDayStart},
 * {@link #timeAtDayStartOffset}, {@link #businessDateOf} and {@link #calendarDateAtDayStartOffset}.
 * They collapse onto their midnight-implicit counterparts above exactly when the anchor is
 * {@code 00:00} — {@code DayWindowTest} proves this exhaustively, across all 1440 minutes of the
 * day rather than at sampled points, not merely at a few spot checks.
 */
public final class DayWindow {

    /** Minutes in a day. The minute-of-day value of an end time of {@code 00:00}. */
    public static final int MINUTES_PER_DAY = 1440;

    private DayWindow() {}

    /**
     * Minute-of-day of a time in a START position, {@code 00:00} &rarr; {@code 0}.
     * Range {@code [0, 1440)}.
     *
     * @deprecated BDAY-04 removes this midnight-implicit form; callers move to
     *         {@link #startMinuteFromDayStart(LocalTime, LocalTime)}.
     */
    @Deprecated
    public static int startMinute(LocalTime start) {
        requireNonNull(start, "start");
        return start.getHour() * 60 + start.getMinute();
    }

    /**
     * Minute-of-day of a time in an END position, {@code 00:00} &rarr; {@link #MINUTES_PER_DAY}.
     * Range {@code (0, 1440]}.
     *
     * @deprecated BDAY-04 removes this midnight-implicit form; callers move to
     *         {@link #endMinuteFromDayStart(LocalTime, LocalTime)}.
     */
    @Deprecated
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
     *
     * @deprecated BDAY-04 removes this midnight-implicit form; callers move to the anchored pair
     *         {@link #startMinuteFromDayStart(LocalTime, LocalTime)} /
     *         {@link #endMinuteFromDayStart(LocalTime, LocalTime)}.
     */
    @Deprecated
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
     *  counterpart of {@link #durationMinutes}, for validators that want to report rather than throw.
     *
     * @deprecated BDAY-04 removes this midnight-implicit form; callers move to the anchored pair
     *         {@link #startMinuteFromDayStart(LocalTime, LocalTime)} /
     *         {@link #endMinuteFromDayStart(LocalTime, LocalTime)}. */
    @Deprecated
    public static boolean isForwardWithinDay(LocalTime start, LocalTime end) {
        return start != null && end != null && endMinute(end) > startMinute(start);
    }

    /**
     * True when the half-open intervals {@code [s1, e1)} and {@code [s2, e2)} overlap. Intervals
     * that merely touch (one's end equals the other's start) do NOT overlap.
     *
     * @deprecated BDAY-04 removes this midnight-implicit form; callers move to the anchored pair
     *         {@link #startMinuteFromDayStart(LocalTime, LocalTime)} /
     *         {@link #endMinuteFromDayStart(LocalTime, LocalTime)}.
     */
    @Deprecated
    public static boolean overlaps(LocalTime s1, LocalTime e1, LocalTime s2, LocalTime e2) {
        return startMinute(s1) < endMinute(e2) && endMinute(e1) > startMinute(s2);
    }

    /**
     * True when {@code [innerStart, innerEnd)} lies entirely within {@code [outerStart, outerEnd)}.
     *
     * @deprecated BDAY-04 removes this midnight-implicit form; callers move to the anchored pair
     *         {@link #startMinuteFromDayStart(LocalTime, LocalTime)} /
     *         {@link #endMinuteFromDayStart(LocalTime, LocalTime)}.
     */
    @Deprecated
    public static boolean contains(LocalTime outerStart, LocalTime outerEnd,
                                   LocalTime innerStart, LocalTime innerEnd) {
        return startMinute(innerStart) >= startMinute(outerStart)
                && endMinute(innerEnd) <= endMinute(outerEnd);
    }

    /**
     * True when {@code start} falls strictly before the END boundary {@code end} — the
     * midnight-correct replacement for {@code start.isBefore(end)} in a generation or scan loop.
     *
     * @deprecated BDAY-04 removes this midnight-implicit form; callers move to the anchored pair
     *         {@link #startMinuteFromDayStart(LocalTime, LocalTime)} /
     *         {@link #endMinuteFromDayStart(LocalTime, LocalTime)}.
     */
    @Deprecated
    public static boolean startsBefore(LocalTime start, LocalTime end) {
        return startMinute(start) < endMinute(end);
    }

    /**
     * Converts a minute-of-day back to a {@link LocalTime}, mapping {@link #MINUTES_PER_DAY} to
     * {@link LocalTime#MIDNIGHT} so a round trip through {@link #endMinute} is lossless.
     *
     * @throws IllegalArgumentException outside {@code [0, 1440]}.
     *
     * @deprecated BDAY-04 removes this midnight-implicit form; callers move to
     *         {@link #timeAtDayStartOffset(LocalTime, int)}.
     */
    @Deprecated
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
     *
     * @deprecated BDAY-04 removes this midnight-implicit form; callers move to
     *         {@link #timeAtDayStartOffset(LocalTime, int)}.
     */
    @Deprecated
    public static LocalTime plusWithinDay(LocalTime base, int minutes) {
        return toLocalTime(startMinute(base) + minutes);
    }

    // ------------------------------------------------------------------------------------------
    // Day-start-aware vocabulary (BDAY-03). The block above this banner is what BDAY-04 removes:
    // every function above assumes a business day starts at 00:00. The five functions below take
    // an explicit day-start anchor instead, and collapse onto their midnight-implicit counterparts
    // above exactly when that anchor is 00:00 -- DayWindowTest proves this exhaustively.
    // ------------------------------------------------------------------------------------------

    /**
     * Day-start-relative minute of a time in a START position, {@code [0, 1440)} — the direct
     * generalisation of {@link #startMinute}: at a {@code 00:00} anchor the two agree exactly for
     * every time of day (proven exhaustively in {@code DayWindowTest}).
     *
     * @throws IllegalArgumentException when {@code dayStart} or {@code start} is null.
     */
    public static int startMinuteFromDayStart(LocalTime dayStart, LocalTime start) {
        requireNonNull(dayStart, "dayStart");
        requireNonNull(start, "start");
        return Math.floorMod(startMinute(start) - startMinute(dayStart), MINUTES_PER_DAY);
    }

    /**
     * Day-start-relative minute of a time in an END position, {@code (0, 1440]}, mapping a result
     * of zero to {@link #MINUTES_PER_DAY} so a time equal to the anchor reads as the end of the
     * business day — the direct generalisation of {@link #endMinute}'s {@code 00:00}-means-end-of-
     * day rule, not a special case bolted on. At a {@code 00:00} anchor the two agree exactly for
     * every time of day (proven exhaustively in {@code DayWindowTest}).
     *
     * @throws IllegalArgumentException when {@code dayStart} or {@code end} is null.
     */
    public static int endMinuteFromDayStart(LocalTime dayStart, LocalTime end) {
        requireNonNull(dayStart, "dayStart");
        requireNonNull(end, "end");
        int raw = Math.floorMod(startMinute(end) - startMinute(dayStart), MINUTES_PER_DAY);
        return raw == 0 ? MINUTES_PER_DAY : raw;
    }

    /**
     * Converts a day-start-relative offset back to a {@link LocalTime}, adding the offset to the
     * anchor's minute-of-day modulo the day so a round trip through {@link #startMinuteFromDayStart}
     * is lossless. At a {@code 00:00} anchor this agrees exactly with {@link #toLocalTime} for
     * every offset in {@code [0, 1440]} (proven exhaustively in {@code DayWindowTest}).
     *
     * @throws IllegalArgumentException when {@code dayStart} is null, or {@code minutesFromDayStart}
     *         is outside {@code [0, 1440]}.
     */
    public static LocalTime timeAtDayStartOffset(LocalTime dayStart, int minutesFromDayStart) {
        requireNonNull(dayStart, "dayStart");
        if (minutesFromDayStart < 0 || minutesFromDayStart > MINUTES_PER_DAY) {
            throw new IllegalArgumentException("minutesFromDayStart must be within [0, "
                    + MINUTES_PER_DAY + "] but was " + minutesFromDayStart);
        }
        return toLocalTime(Math.floorMod(startMinute(dayStart) + minutesFromDayStart, MINUTES_PER_DAY));
    }

    /**
     * The business date a calendar date and time-of-day belong to, given a day-start anchor: the
     * calendar date itself when the time's minute-of-day is at or after the anchor's, and the
     * previous calendar date otherwise. At a {@code 00:00} anchor this always returns the calendar
     * date unchanged (proven exhaustively in {@code DayWindowTest}) — {@code isDesired} is the
     * second caller (BDAY-03), classifying an existing row's business date from its stored calendar
     * date and start time without ever reading the stored BDAY-02 {@code business_date} column.
     *
     * @throws IllegalArgumentException when {@code dayStart}, {@code calendarDate} or
     *         {@code timeOfDay} is null.
     */
    public static LocalDate businessDateOf(LocalTime dayStart, LocalDate calendarDate, LocalTime timeOfDay) {
        requireNonNull(dayStart, "dayStart");
        requireNonNull(calendarDate, "calendarDate");
        requireNonNull(timeOfDay, "timeOfDay");
        return startMinute(timeOfDay) >= startMinute(dayStart) ? calendarDate : calendarDate.minusDays(1);
    }

    /**
     * The calendar date a business date and day-start-relative offset land on: the business date
     * plus one day for every whole {@link #MINUTES_PER_DAY} the anchor and offset together carry
     * past midnight. At a {@code 00:00} anchor this always returns the business date unchanged for
     * every offset in {@code [0, 1440)} (proven exhaustively in {@code DayWindowTest}) — the
     * generation walk's forward direction (BDAY-03), turning a business date plus offset into the
     * calendar date a {@code Timeslot} row actually stores.
     *
     * @throws IllegalArgumentException when {@code dayStart} or {@code businessDate} is null, or
     *         {@code minutesFromDayStart} is outside {@code [0, 1440)}.
     */
    public static LocalDate calendarDateAtDayStartOffset(LocalTime dayStart, LocalDate businessDate,
                                                          int minutesFromDayStart) {
        requireNonNull(dayStart, "dayStart");
        requireNonNull(businessDate, "businessDate");
        if (minutesFromDayStart < 0 || minutesFromDayStart >= MINUTES_PER_DAY) {
            throw new IllegalArgumentException("minutesFromDayStart must be within [0, "
                    + MINUTES_PER_DAY + ") but was " + minutesFromDayStart);
        }
        int daysForward = (startMinute(dayStart) + minutesFromDayStart) / MINUTES_PER_DAY;
        return businessDate.plusDays(daysForward);
    }

    private static void requireNonNull(LocalTime value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
    }

    private static void requireNonNull(LocalDate value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
    }
}
