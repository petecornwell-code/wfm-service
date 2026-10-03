package com.wfm.model;

import com.wfm.util.DayWindow;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Immutable problem-fact shape for one agent's worked span on one business date (REST-02), and the
 * single gap-minutes implementation every rest consumer calls — in the same single-implementation
 * register as {@link ShiftBandPair#covers}.
 *
 * <p>{@code dayStart} rides on every instance rather than being threaded separately through each
 * caller, because {@link #gapMinutes} needs it to bind the anchored {@link DayWindow} the gap is
 * measured through, and carrying it here is what lets that method refuse to compare two spans
 * anchored under different anchors (see its own javadoc).
 */
public record RestSpan(UUID agentId, LocalDate businessDate, LocalTime startTime, LocalTime endTime,
        LocalTime dayStart) {

    /**
     * Builds a span from a live {@link AgentShiftAssignment} row. Reads the envelope through
     * {@code sa.getShiftBandPair().template()} — the accept-time denormalised
     * {@code shiftStartTime}/{@code shiftEndTime} columns on that entity are {@code null} on every
     * live row (they are populated only at accept time), so they are never the source here.
     *
     * @throws NullPointerException if {@code sa.getShiftBandPair()} is {@code null}; callers must
     *         filter unassigned rows before calling this (REST-02's "an agent whose shiftBandPair
     *         is unassigned on either date: zero matches" case is handled by the caller's filter,
     *         not by this method tolerating a null pair).
     */
    public static RestSpan ofShift(AgentShiftAssignment sa, LocalTime dayStart) {
        ShiftTemplate template = sa.getShiftBandPair().template();
        return new RestSpan(sa.getAgent().getId(), sa.getDate(),
                template.getStartTime(), template.getEndTime(), dayStart);
    }

    /**
     * The one and only gap-minutes implementation in this codebase (REST-02). Every interval value
     * comes from a {@link DayWindow} anchored accessor — never raw {@link java.time.Duration}
     * arithmetic or a raw {@link LocalTime} comparison — so this file carries no raw time
     * arithmetic and no raw time comparison at all; {@code MidnightTimeArithmeticGuardTest}
     * enforces set equality over {@code src/main/java} on both families.
     *
     * <p>Binds one {@link DayWindow} to {@code prev}'s anchor and returns
     * {@link DayWindow#MINUTES_PER_DAY} minus that window's {@link DayWindow#anchoredEndMinute}
     * of the predecessor's end, plus the window's {@link DayWindow#anchoredStartMinute} of the
     * successor's start. A predecessor ending exactly at the business-day boundary paired with a
     * successor starting exactly at it yields {@code 0}, never {@code 1440} — this needs no special
     * branch because {@code anchoredEndMinute} of a time equal to the anchor already returns
     * {@code MINUTES_PER_DAY}, so the subtraction collapses to zero before the successor's own
     * (zero) start offset is added.
     *
     * @throws IllegalArgumentException when {@code prev} and {@code next} carry different
     *         {@code dayStart} values. This is unreachable in production because
     *         {@code DeskService.setDayStart} already refuses a re-anchor once an ACCEPTED
     *         schedule exists — the check exists so a future bypass of that refusal fails loudly
     *         here instead of silently returning a wrapped, meaningless number.
     */
    public static int gapMinutes(RestSpan prev, RestSpan next) {
        if (!prev.dayStart().equals(next.dayStart())) {
            throw new IllegalArgumentException(
                    "Cannot compute a rest gap between spans anchored at different day starts: "
                            + prev.dayStart() + " vs " + next.dayStart());
        }
        DayWindow window = DayWindow.anchoredAt(prev.dayStart());
        int remainingInPrevDay = DayWindow.MINUTES_PER_DAY - window.anchoredEndMinute(prev.endTime());
        int elapsedIntoNextDay = window.anchoredStartMinute(next.startTime());
        return remainingInPrevDay + elapsedIntoNextDay;
    }
}
