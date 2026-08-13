package com.wfm.solver;

import com.wfm.model.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * Pure, Timefold-independent geometry for locating a legal contiguous shift
 * window — the required work slots plus (when needed) exactly one correctly
 * positioned, aligned, unblocked break gap — within a set of candidate free
 * seats for a single agent-day.
 *
 * <p>Every calculation here mirrors {@link ScheduleConstraintProvider}'s own
 * arithmetic exactly (expectedWorkSlots' {@code RoundingMode.HALF_UP},
 * exactlyOneBreak's threshold {@code RoundingMode.CEILING},
 * breakBlockedWindow's blocked-minutes check, breakStartAlignment's
 * three-branch switch) so a window this class accepts is never subsequently
 * rejected by the constraint provider. This class has zero Timefold API
 * imports so it is unit-testable without a solver.
 */
public class ShiftWindowFinder {

    private ShiftWindowFinder() {}

    /**
     * A single legal candidate shift window: the work seats to assign, plus
     * the break start time (null when the agent-day does not require a
     * break).
     */
    public record ShiftWindow(List<AgentAssignment> workSeats, LocalTime breakStart) {}

    /**
     * Same arithmetic as {@code ScheduleConstraintProvider.expectedWorkSlots}
     * (line 515): {@code effectiveHours * 60 / incrementMinutes}, HALF_UP.
     */
    public static int requiredWorkSlots(AgentDayConfig dayConfig) {
        return dayConfig.effectiveHours()
                .multiply(BigDecimal.valueOf(60))
                .divide(BigDecimal.valueOf(dayConfig.incrementMinutes()), 0, RoundingMode.HALF_UP)
                .intValue();
    }

    /**
     * Same arithmetic as the threshold calculation inside
     * {@code ScheduleConstraintProvider.exactlyOneBreak} (line 164):
     * {@code breakMinShiftHours * 60 / incrementMinutes}, CEILING. This is a
     * genuinely different rounding mode from {@link #requiredWorkSlots} and
     * must not be unified with it.
     */
    public static int breakThresholdSlots(AgentDayConfig dayConfig) {
        return dayConfig.breakMinShiftHours()
                .multiply(BigDecimal.valueOf(60))
                .divide(BigDecimal.valueOf(dayConfig.incrementMinutes()), 0, RoundingMode.CEILING)
                .intValue();
    }

    public static int breakSlotCount(AgentDayConfig dayConfig) {
        return dayConfig.breakDurationMinutes() / dayConfig.incrementMinutes();
    }

    public static boolean needsBreak(AgentDayConfig dayConfig) {
        return dayConfig.effectiveHours().compareTo(dayConfig.breakMinShiftHours()) > 0;
    }

    /**
     * Same three-branch switch as {@code ScheduleConstraintProvider.isAligned}
     * (line 595).
     */
    public static boolean isAligned(LocalTime time, BreakAlignment alignment) {
        int minute = time.getMinute();
        return switch (alignment) {
            case ON_HOUR -> minute == 0;
            case ON_HALF_HOUR -> minute == 0 || minute == 30;
            case ON_QUARTER_HOUR -> minute % 15 == 0;
        };
    }

    /**
     * Finds every legal candidate shift window within {@code candidateSeats}
     * for the given agent-day. Indexes seats by timeslot start time —
     * exactly one seat per distinct start time, so a returned window can
     * never violate {@code oneAssignmentPerTimeslot}; when several seats
     * share a start time, the one with the lowest {@code Timeslot.getId()}
     * is kept, so repeated calls with the same input select identically —
     * then walks every contiguous run of present start times of length
     * {@code requiredWorkSlots + breakSlotCount} (or just
     * {@code requiredWorkSlots} when no break is required). For every
     * break-requiring span, every break offset from 1 to
     * {@code requiredWorkSlots - 1} is tried and a window is emitted for
     * each one that is aligned and clears both blocked-margin checks — this
     * does not relax legality, it only stops returning after the first
     * legal candidate.
     *
     * <p>Returned windows are ordered by span start ascending, then break
     * offset ascending, so the order is stable across calls on the same
     * input (the 12-03 benchmark harness depends on this for seeded
     * reproducibility).
     */
    public static List<ShiftWindow> findWindows(List<AgentAssignment> candidateSeats, AgentDayConfig dayConfig) {
        if (candidateSeats == null || candidateSeats.isEmpty()) return List.of();

        TreeMap<LocalTime, AgentAssignment> seatsByStart = new TreeMap<>();
        for (AgentAssignment seat : candidateSeats) {
            LocalTime start = seat.getTimeslot().getStartTime();
            AgentAssignment existing = seatsByStart.get(start);
            if (existing == null
                    || seat.getTimeslot().getId().compareTo(existing.getTimeslot().getId()) < 0) {
                seatsByStart.put(start, seat);
            }
        }

        int incrementMinutes = dayConfig.incrementMinutes();
        int requiredWorkSlots = requiredWorkSlots(dayConfig);
        if (requiredWorkSlots <= 0) return List.of();

        boolean needsBreak = needsBreak(dayConfig);
        int breakSlotCount = needsBreak ? breakSlotCount(dayConfig) : 0;
        int spanLength = requiredWorkSlots + breakSlotCount;

        List<ShiftWindow> windows = new ArrayList<>();

        for (LocalTime spanStart : seatsByStart.keySet()) {
            List<LocalTime> spanTimes = new ArrayList<>(spanLength);
            LocalTime t = spanStart;
            boolean allPresent = true;
            for (int i = 0; i < spanLength; i++) {
                if (!seatsByStart.containsKey(t)) {
                    allPresent = false;
                    break;
                }
                spanTimes.add(t);
                t = t.plusMinutes(incrementMinutes);
            }
            if (!allPresent) continue;

            if (!needsBreak) {
                List<AgentAssignment> workSeats = new ArrayList<>(spanLength);
                for (LocalTime slotTime : spanTimes) {
                    workSeats.add(seatsByStart.get(slotTime));
                }
                windows.add(new ShiftWindow(workSeats, null));
                continue;
            }

            LocalTime shiftEnd = spanStart.plusMinutes((long) spanLength * incrementMinutes);
            long blockedMinutes = dayConfig.breakBlockedHours()
                    .multiply(BigDecimal.valueOf(60)).longValue();
            LocalTime earliestBreakStart = spanStart.plusMinutes(blockedMinutes);
            LocalTime latestBreakEnd = shiftEnd.minusMinutes(blockedMinutes);

            for (int k = 1; k <= requiredWorkSlots - 1; k++) {
                LocalTime breakStart = spanStart.plusMinutes((long) k * incrementMinutes);
                LocalTime breakEnd = breakStart.plusMinutes((long) breakSlotCount * incrementMinutes);

                if (!isAligned(breakStart, dayConfig.breakStartAlignment())) continue;
                if (breakStart.isBefore(earliestBreakStart)) continue;
                if (breakEnd.isAfter(latestBreakEnd)) continue;

                List<AgentAssignment> workSeats = new ArrayList<>(requiredWorkSlots);
                for (LocalTime slotTime : spanTimes) {
                    boolean inBreakWindow = !slotTime.isBefore(breakStart) && slotTime.isBefore(breakEnd);
                    if (inBreakWindow) continue;
                    workSeats.add(seatsByStart.get(slotTime));
                }
                windows.add(new ShiftWindow(workSeats, breakStart));
            }
        }

        return windows;
    }
}
