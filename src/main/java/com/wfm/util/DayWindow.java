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
 * <p>A time equal to the business day's START ANCHOR is ambiguous in isolation and means
 * different things by POSITION (BDAY-04 generalises this from the {@code 00:00}-only wording it
 * started as):
 *
 * <ul>
 *   <li>in a <strong>start</strong> position it is the start of the business day, offset {@code 0};</li>
 *   <li>in an <strong>end</strong> position it is the end of the business day, offset {@link #MINUTES_PER_DAY}.</li>
 * </ul>
 *
 * <p>Callers must therefore pick the function matching the position — {@link #anchoredStartMinute}
 * or {@link #anchoredEndMinute} — rather than converting a bare time and hoping. Every interval
 * here is half-open, {@code [start, end)}, which is what makes adjacent timeslots non-overlapping.
 * For every desk today the anchor is {@code 00:00}, so this collapses onto exactly the midnight
 * rule above.
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
 *
 * <h2>A bound instance (BDAY-04)</h2>
 *
 * <p>{@link #anchoredAt(LocalTime)} returns a {@code DayWindow} bound to one desk's anchor,
 * exposing instance equivalents of every function above the BDAY-03 banner so a call site binds
 * the anchor once per scope instead of repeating it as an argument at every call (D-04). The rule
 * generalises rather than disappears: a time equal to the ANCHOR in an END position is the end of
 * the business day, exactly as {@code 00:00} is today when the anchor is midnight.
 *
 * <p><b>Naming note (deviation from the BDAY-04 plan text):</b> the nine instance methods below
 * are named with an {@code anchored} prefix ({@link #anchoredStartMinute}, {@link
 * #anchoredDurationMinutes}, etc.) rather than reusing the nine midnight-implicit forms' bare
 * names verbatim. A public static and a public instance method cannot share an identical name and
 * parameter list in the same class — confirmed by direct compilation during plan 19-03 — and
 * through wave 19-07 the nine midnight-implicit statics stayed public, unchanged, and under their
 * current names for call sites not yet migrated. Plan 19-08 then demoted the nine to private
 * (D-06, below) and kept the {@code anchored} prefix rather than reclaiming the bare names, to
 * avoid churn with no remaining benefit. See {@code 19-03-SUMMARY.md} for the full naming
 * reasoning.
 */
public final class DayWindow {

    /** Minutes in a day. The minute-of-day value of an end time of {@code 00:00}. */
    public static final int MINUTES_PER_DAY = 1440;

    /** The day-start anchor this bound instance was constructed with (BDAY-04). */
    private final LocalTime dayStart;

    private DayWindow(LocalTime dayStart) {
        this.dayStart = dayStart;
    }

    /**
     * Binds a {@code DayWindow} to one desk's day-start anchor, exposing the instance methods
     * below. Each instance method keeps the exact parameter list of its midnight-implicit static
     * counterpart (D-04) — only the anchor itself moves from a repeated argument to this bound
     * field.
     *
     * @throws IllegalArgumentException when {@code dayStart} is null — this never binds an
     *         implicit midnight.
     */
    public static DayWindow anchoredAt(LocalTime dayStart) {
        requireNonNull(dayStart, "dayStart");
        return new DayWindow(dayStart);
    }

    /**
     * The anchor this instance was bound with (OVNT-01, D-02). Read-only — no arithmetic, no
     * policy. Added so {@code ShiftTemplateService}'s forward-interval refusal can name the
     * desk's own day start in its message without re-loading the desk a second time (the caller
     * already resolved it once, in {@code dayWindowFor}).
     */
    public LocalTime dayStart() {
        return dayStart;
    }

    /**
     * Instance equivalent of {@link #startMinute(LocalTime)}, day-start-relative. Delegates to
     * {@link #startMinuteFromDayStart(LocalTime, LocalTime)} against the bound anchor; at a
     * {@code 00:00} anchor this equals {@link #startMinute(LocalTime)} exactly.
     */
    public int anchoredStartMinute(LocalTime t) {
        return startMinuteFromDayStart(dayStart, t);
    }

    /**
     * Instance equivalent of {@link #endMinute(LocalTime)}, day-start-relative. Delegates to
     * {@link #endMinuteFromDayStart(LocalTime, LocalTime)} against the bound anchor; at a
     * {@code 00:00} anchor this equals {@link #endMinute(LocalTime)} exactly.
     */
    public int anchoredEndMinute(LocalTime t) {
        return endMinuteFromDayStart(dayStart, t);
    }

    /**
     * Instance equivalent of {@link #durationMinutes(LocalTime, LocalTime)}, bound to this
     * instance's anchor. Unlike the deprecated static, this does NOT throw when the interval does
     * not run forward relative to the anchor — that condition now means "crosses the anchor"
     * (BDAY-04 criterion 2) and yields the wrapped-forward duration instead. A pair equal to each
     * other maps to {@code 0} minutes unless the pair equals the anchor itself, which maps to a
     * full {@link #MINUTES_PER_DAY} — the same "equal to the anchor in an end position is the end
     * of the business day" rule the rest of this class follows.
     */
    public int anchoredDurationMinutes(LocalTime start, LocalTime end) {
        int startOffset = startMinuteFromDayStart(dayStart, start);
        int endOffset = endMinuteFromDayStart(dayStart, end);
        int raw = endOffset - startOffset;
        if (raw > 0) {
            return raw;
        }
        if (raw == 0) {
            return 0;
        }
        return raw + MINUTES_PER_DAY;
    }

    /**
     * The day-start-relative minute an interval's END instant truly falls at, allowed to exceed
     * {@link #MINUTES_PER_DAY} when the interval wraps past the anchor — unlike {@link
     * #anchoredEndMinute}, which is bounded to {@code (0, 1440]} and therefore cannot distinguish
     * "ends within this business day" from "ends the calendar day after wrapping past the anchor"
     * (REST-02). The worked wrapping pair {@code 22:00}&ndash;{@code 06:00} at a {@code 00:00}
     * anchor returns {@code 1800}.
     *
     * <p>Collapses onto {@link #anchoredEndMinute(LocalTime)} exactly whenever the interval does
     * not wrap: the worked non-wrapping pair {@code 14:00}&ndash;{@code 22:00} returns {@code 1320},
     * identical to {@code anchoredEndMinute(22:00)} — which is why no existing measurement built on
     * the non-wrapping case changes when callers switch to this method.
     *
     * <p>Wrap detection is delegated entirely to {@link #anchoredDurationMinutes}'s existing
     * negative-raw branch, never a new hand-rolled comparison — this method exists as a named
     * member, rather than being inlined as a two-call expression at each call site, because that
     * exact composition was already inlined twice in this codebase and the second copy drifted into
     * a defect (REST-02, REST-05). The maximum possible return is {@code 2880} (two values each
     * bounded at {@link #MINUTES_PER_DAY}), so a caller's {@code MINUTES_PER_DAY} subtraction may
     * legitimately go negative — that negative is meaningful, not an error to guard away.
     */
    public int anchoredWrappedEndMinute(LocalTime start, LocalTime end) {
        return anchoredStartMinute(start) + anchoredDurationMinutes(start, end);
    }

    /**
     * Instance equivalent of {@link #isForwardWithinDay(LocalTime, LocalTime)}, bound to this
     * instance's anchor — the non-throwing ordering predicate {@code ShiftTemplateService}'s
     * save-path refusal relies on (D-11). Returns {@code false} on a null argument, exactly as the
     * deprecated static does, rather than throwing.
     */
    public boolean anchoredIsForwardWithinDay(LocalTime start, LocalTime end) {
        return start != null && end != null
                && endMinuteFromDayStart(dayStart, end) > startMinuteFromDayStart(dayStart, start);
    }

    /**
     * Instance equivalent of {@link #overlaps(LocalTime, LocalTime, LocalTime, LocalTime)}, bound
     * to this instance's anchor. Symmetric in its two interval arguments, exactly like the
     * deprecated static.
     */
    public boolean anchoredOverlaps(LocalTime s1, LocalTime e1, LocalTime s2, LocalTime e2) {
        return startMinuteFromDayStart(dayStart, s1) < endMinuteFromDayStart(dayStart, e2)
                && endMinuteFromDayStart(dayStart, e1) > startMinuteFromDayStart(dayStart, s2);
    }

    /**
     * Instance equivalent of {@link #contains(LocalTime, LocalTime, LocalTime, LocalTime)}, bound
     * to this instance's anchor.
     */
    public boolean anchoredContains(LocalTime outerStart, LocalTime outerEnd,
                                     LocalTime innerStart, LocalTime innerEnd) {
        return startMinuteFromDayStart(dayStart, innerStart) >= startMinuteFromDayStart(dayStart, outerStart)
                && endMinuteFromDayStart(dayStart, innerEnd) <= endMinuteFromDayStart(dayStart, outerEnd);
    }

    /**
     * Instance equivalent of {@link #startsBefore(LocalTime, LocalTime)}, bound to this instance's
     * anchor.
     */
    public boolean anchoredStartsBefore(LocalTime start, LocalTime end) {
        return startMinuteFromDayStart(dayStart, start) < endMinuteFromDayStart(dayStart, end);
    }

    /**
     * Instance equivalent of {@link #toLocalTime(int)} — a day-start-relative offset back to a
     * {@link LocalTime}. Delegates to {@link #timeAtDayStartOffset(LocalTime, int)} against the
     * bound anchor; at a {@code 00:00} anchor this equals {@link #toLocalTime(int)} exactly.
     *
     * @throws IllegalArgumentException outside {@code [0, 1440]}.
     */
    public LocalTime anchoredToLocalTime(int minuteOfDay) {
        return timeAtDayStartOffset(dayStart, minuteOfDay);
    }

    /**
     * Instance equivalent of {@link #plusWithinDay(LocalTime, int)}, bound to this instance's
     * anchor. Throws past the end of the business day, exactly like the deprecated static.
     */
    public LocalTime anchoredPlusWithinDay(LocalTime base, int minutes) {
        return timeAtDayStartOffset(dayStart, startMinuteFromDayStart(dayStart, base) + minutes);
    }

    /**
     * Minute-of-day of a time in a START position, {@code 00:00} &rarr; {@code 0}.
     * Range {@code [0, 1440)}.
     *
     * <p>Retired from public surface by BDAY-04 (D-06); kept private because
     * {@link #startMinuteFromDayStart(LocalTime, LocalTime)} still delegates to it internally.
     */
    private static int startMinute(LocalTime start) {
        requireNonNull(start, "start");
        return start.getHour() * 60 + start.getMinute();
    }

    /**
     * Minute-of-day of a time in an END position, {@code 00:00} &rarr; {@link #MINUTES_PER_DAY}.
     * Range {@code (0, 1440]}.
     *
     * <p>Retired from public surface by BDAY-04 (D-06); no longer called internally — superseded
     * by {@link #endMinuteFromDayStart(LocalTime, LocalTime)}.
     */
    private static int endMinute(LocalTime end) {
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
     * <p>Retired from public surface by BDAY-04 (D-06); no longer called internally — superseded
     * by {@link #anchoredDurationMinutes(LocalTime, LocalTime)}, which does NOT throw on a
     * backward interval (BDAY-04 criterion 2: it means "crosses the anchor" instead).
     */
    private static int durationMinutes(LocalTime start, LocalTime end) {
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
     * <p>Retired from public surface by BDAY-04 (D-06); no longer called internally — superseded
     * by {@link #anchoredIsForwardWithinDay(LocalTime, LocalTime)}. */
    private static boolean isForwardWithinDay(LocalTime start, LocalTime end) {
        return start != null && end != null && endMinute(end) > startMinute(start);
    }

    /**
     * True when the half-open intervals {@code [s1, e1)} and {@code [s2, e2)} overlap. Intervals
     * that merely touch (one's end equals the other's start) do NOT overlap.
     *
     * <p>Retired from public surface by BDAY-04 (D-06); no longer called internally — superseded
     * by {@link #anchoredOverlaps(LocalTime, LocalTime, LocalTime, LocalTime)}.
     */
    private static boolean overlaps(LocalTime s1, LocalTime e1, LocalTime s2, LocalTime e2) {
        return startMinute(s1) < endMinute(e2) && endMinute(e1) > startMinute(s2);
    }

    /**
     * True when {@code [innerStart, innerEnd)} lies entirely within {@code [outerStart, outerEnd)}.
     *
     * <p>Retired from public surface by BDAY-04 (D-06); no longer called internally — superseded
     * by {@link #anchoredContains(LocalTime, LocalTime, LocalTime, LocalTime)}.
     */
    private static boolean contains(LocalTime outerStart, LocalTime outerEnd,
                                   LocalTime innerStart, LocalTime innerEnd) {
        return startMinute(innerStart) >= startMinute(outerStart)
                && endMinute(innerEnd) <= endMinute(outerEnd);
    }

    /**
     * True when {@code start} falls strictly before the END boundary {@code end} — the
     * midnight-correct replacement for {@code start.isBefore(end)} in a generation or scan loop.
     *
     * <p>Retired from public surface by BDAY-04 (D-06); no longer called internally — superseded
     * by {@link #anchoredStartsBefore(LocalTime, LocalTime)}.
     */
    private static boolean startsBefore(LocalTime start, LocalTime end) {
        return startMinute(start) < endMinute(end);
    }

    /**
     * Converts a minute-of-day back to a {@link LocalTime}, mapping {@link #MINUTES_PER_DAY} to
     * {@link LocalTime#MIDNIGHT} so a round trip through {@link #endMinute} is lossless.
     *
     * @throws IllegalArgumentException outside {@code [0, 1440]}.
     *
     * <p>Retired from public surface by BDAY-04 (D-06); kept private because
     * {@link #timeAtDayStartOffset(LocalTime, int)} still delegates to it internally.
     */
    private static LocalTime toLocalTime(int minuteOfDay) {
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
     * <p>Retired from public surface by BDAY-04 (D-06); no longer called internally — superseded
     * by {@link #anchoredPlusWithinDay(LocalTime, int)}.
     */
    private static LocalTime plusWithinDay(LocalTime base, int minutes) {
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
