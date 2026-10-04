package com.wfm.model;

import com.wfm.util.DayWindow;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
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
     * Builds a span from a SLOT-mode agent-day's assigned seats (REST-02, D-02). In SLOT mode
     * there is no shift entity and no contiguity constraint, so "a shift" is defined here for the
     * first time: the agent's whole assigned span on a business date, the start of their first
     * assigned slot to the end of their last, with the intra-day break gap deliberately IGNORED.
     *
     * <p><b>Why the gap is ignored, not split on.</b> {@code exactlyOneBreak} is gated
     * {@code != SchedulingMode.SHIFT}, so it is the SLOT-mode break rule, and in SLOT mode the
     * break IS a gap in assignment — an agent compliant under that rule holds a contiguous run of
     * slots, one gap of exactly the break duration, then the rest of the run. A maximal-
     * contiguous-run definition of "shift" would therefore split every compliant SLOT agent-day in
     * two at its own mandated break, and any realistic rest minimum would fire on every compliant
     * agent-day on every SLOT desk. Exempting the break duration from a contiguous-run scan was
     * also rejected: it avoids that false positive but ties the rest rule to break geometry — two
     * rules that would then have to agree forever, with no shared predicate. Ignoring the gap
     * entirely (this method) needs neither.
     *
     * <p><b>Ordering is by ANCHORED minute, never clock time</b> — the same idiom
     * {@code ScheduleConstraintProvider.countContiguousGaps} already uses for a day's slot order.
     * On a desk anchored after midnight, a {@code 23:45} slot and a {@code 00:15} slot in the same
     * business day compare in the opposite order by clock time; the anchored integer minute keeps
     * the ordering correct on either side of the anchor. No raw {@link LocalTime} comparison of
     * any kind is used here — only the integer anchored minutes from {@code window}.
     *
     * @throws IllegalArgumentException if {@code slots} is empty. The only caller is a
     *         {@code groupBy} node that only ever emits an agent-day with at least one assignment,
     *         so this is unreachable in production; a loud failure here is better than silently
     *         returning a degenerate span.
     */
    public static RestSpan ofSlots(UUID agentId, LocalDate businessDate, List<AgentAssignment> slots,
            LocalTime dayStart) {
        if (slots == null || slots.isEmpty()) {
            throw new IllegalArgumentException(
                    "Cannot derive a RestSpan from an empty slot list for agent " + agentId
                            + " on business date " + businessDate);
        }
        DayWindow window = DayWindow.anchoredAt(dayStart);
        LocalTime start = null;
        LocalTime end = null;
        int minStartMinute = Integer.MAX_VALUE;
        int maxEndMinute = Integer.MIN_VALUE;
        for (AgentAssignment slot : slots) {
            LocalTime slotStart = slot.getTimeslot().getStartTime();
            LocalTime slotEnd = slot.getTimeslot().getEndTime();
            int startMinute = window.anchoredStartMinute(slotStart);
            int endMinute = window.anchoredEndMinute(slotEnd);
            if (startMinute < minStartMinute) {
                minStartMinute = startMinute;
                start = slotStart;
            }
            if (endMinute > maxEndMinute) {
                maxEndMinute = endMinute;
                end = slotEnd;
            }
        }
        return new RestSpan(agentId, businessDate, start, end, dayStart);
    }

    /**
     * The one and only gap-minutes implementation in this codebase (REST-02). Every interval value
     * comes from a {@link DayWindow} anchored accessor — never raw {@link java.time.Duration}
     * arithmetic or a raw {@link LocalTime} comparison — so this file carries no raw time
     * arithmetic and no raw time comparison at all; {@code MidnightTimeArithmeticGuardTest}
     * enforces set equality over {@code src/main/java} on both families.
     *
     * <p>Binds one {@link DayWindow} to {@code prev}'s anchor and returns
     * {@link DayWindow#MINUTES_PER_DAY} minus that window's {@link DayWindow#anchoredWrappedEndMinute}
     * of the predecessor's start and end, plus the window's {@link DayWindow#anchoredStartMinute} of
     * the successor's start. A predecessor ending exactly at the business-day boundary paired with a
     * successor starting exactly at it yields {@code 0}, never {@code 1440} — this needs no special
     * branch because {@code anchoredWrappedEndMinute} of a non-wrapping interval ending at the anchor
     * returns {@code MINUTES_PER_DAY} (identical to {@code anchoredEndMinute} in that case), so the
     * subtraction collapses to zero before the successor's own (zero) start offset is added. A
     * predecessor that wraps past the anchor (e.g. {@code 22:00}&ndash;{@code 06:00}) makes
     * {@code remainingInPrevDay} negative by design — that is the corrected behaviour: the
     * {@code 22:00}-{@code 06:00} predecessor paired with a {@code 07:00} successor measures a true
     * gap of {@code 60} minutes, where the pre-correction formula produced {@code 1500}.
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
        int remainingInPrevDay = DayWindow.MINUTES_PER_DAY
                - window.anchoredWrappedEndMinute(prev.startTime(), prev.endTime());
        int elapsedIntoNextDay = window.anchoredStartMinute(next.startTime());
        return remainingInPrevDay + elapsedIntoNextDay;
    }
}
