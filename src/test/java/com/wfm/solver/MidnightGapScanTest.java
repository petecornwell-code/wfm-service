package com.wfm.solver;

import com.wfm.model.AgentAssignment;
import com.wfm.model.Timeslot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static java.time.Duration.ofSeconds;

/**
 * Regression guard for a shift whose last seat is the 23:00-00:00 slot.
 *
 * <p><strong>The defect.</strong> {@code getGapLengths} and {@code findBreakStart} scanned with a
 * {@link LocalTime} cursor advanced by {@code DayWindow.plusWithinDay}. Stepping past 23:00 yields
 * {@code 00:00}, whose START minute is 0 — so the loop condition was satisfied again and the scan
 * wrapped around the clock rather than terminating. Every lap appended another entry to
 * {@code gapLengths}, so the solver died with {@code OutOfMemoryError: Java heap space} inside
 * {@code ArrayList.grow}, on a heap six times larger than the one it first failed on. It looked
 * like a capacity problem and was not one.
 *
 * <p>Only a shift reaching midnight can trigger it, which is why it survived the midnight release:
 * every other desk closes earlier, and the tests written for that change covered the interval
 * arithmetic rather than the scans that consume it. The version this replaced had the opposite
 * defect — {@code t.isBefore(midnight)} is false immediately, so the scan ran zero times and
 * silently reported no gaps at all.
 *
 * <p>Both failure modes are covered here: the timeout catches the non-terminating scan, and the
 * value assertions catch the one that terminates immediately and returns nothing.
 */
class MidnightGapScanTest {

    /** A contiguous run of hourly seats starting at each given hour. */
    private static List<AgentAssignment> seatsAt(int... hours) {
        List<AgentAssignment> assignments = new ArrayList<>();
        for (int hour : hours) {
            Timeslot ts = new Timeslot();
            ts.setStartTime(LocalTime.of(hour % 24, 0));
            ts.setEndTime(LocalTime.of((hour + 1) % 24, 0));
            AgentAssignment a = new AgentAssignment();
            a.setTimeslot(ts);
            assignments.add(a);
        }
        return assignments;
    }

    @Test
    @DisplayName("a gapless evening shift ending at midnight terminates and reports no gaps")
    void midnightShiftWithNoGaps() {
        // 15:00-00:00: seats at 15..23, the last one ending at midnight.
        List<AgentAssignment> shift = seatsAt(15, 16, 17, 18, 19, 20, 21, 22, 23);

        assertTimeoutPreemptively(ofSeconds(5), () -> {
            assertThat(ScheduleConstraintProvider.getGapLengths(shift, 60)).isEmpty();
            assertThat(ScheduleConstraintProvider.countContiguousGaps(shift, 60)).isZero();
            assertThat(ScheduleConstraintProvider.findBreakStart(shift, 60)).isNull();
        });
    }

    @Test
    @DisplayName("the break hour inside a midnight-ending shift is found, not scanned past")
    void midnightShiftWithABreak() {
        // Same shift with 19:00 unworked — the break.
        List<AgentAssignment> shift = seatsAt(15, 16, 17, 18, 20, 21, 22, 23);

        assertTimeoutPreemptively(ofSeconds(5), () -> {
            assertThat(ScheduleConstraintProvider.getGapLengths(shift, 60))
                    .containsExactly(1);
            assertThat(ScheduleConstraintProvider.countContiguousGaps(shift, 60)).isEqualTo(1);
            assertThat(ScheduleConstraintProvider.totalGapSlots(shift, 60)).isEqualTo(1);
            assertThat(ScheduleConstraintProvider.findBreakStart(shift, 60))
                    .isEqualTo(LocalTime.of(19, 0));
        });
    }

    @Test
    @DisplayName("two separate gaps in a midnight-ending shift are both counted")
    void midnightShiftWithTwoGaps() {
        // 15..23 with 17:00 and 20:00-21:00 missing.
        List<AgentAssignment> shift = seatsAt(15, 16, 18, 19, 22, 23);

        assertTimeoutPreemptively(ofSeconds(5), () -> {
            assertThat(ScheduleConstraintProvider.getGapLengths(shift, 60))
                    .containsExactly(1, 2);
            assertThat(ScheduleConstraintProvider.totalGapSlots(shift, 60)).isEqualTo(3);
            assertThat(ScheduleConstraintProvider.findBreakStart(shift, 60))
                    .isEqualTo(LocalTime.of(17, 0));
        });
    }

    @Test
    @DisplayName("a shift ending well before midnight is unaffected")
    void ordinaryDayShiftStillWorks() {
        // 08:00-17:00 with 12:00 as the break.
        List<AgentAssignment> shift = seatsAt(8, 9, 10, 11, 13, 14, 15, 16);

        assertTimeoutPreemptively(ofSeconds(5), () -> {
            assertThat(ScheduleConstraintProvider.getGapLengths(shift, 60)).containsExactly(1);
            assertThat(ScheduleConstraintProvider.findBreakStart(shift, 60))
                    .isEqualTo(LocalTime.of(12, 0));
        });
    }

    @Test
    @DisplayName("a single 23:00-00:00 seat is a whole shift, not an empty scan")
    void singleMidnightSeat() {
        List<AgentAssignment> shift = seatsAt(23);

        assertTimeoutPreemptively(ofSeconds(5), () -> {
            assertThat(ScheduleConstraintProvider.getGapLengths(shift, 60)).isEmpty();
            assertThat(ScheduleConstraintProvider.findBreakStart(shift, 60)).isNull();
        });
    }

    @Test
    @DisplayName("a half-hour grid reaching midnight terminates too")
    void halfHourGridToMidnight() {
        List<AgentAssignment> shift = new ArrayList<>();
        for (int minute = 22 * 60; minute < 24 * 60; minute += 30) {
            Timeslot ts = new Timeslot();
            ts.setStartTime(LocalTime.of(minute / 60, minute % 60));
            int end = minute + 30;
            ts.setEndTime(end == 1440 ? LocalTime.MIDNIGHT : LocalTime.of(end / 60, end % 60));
            AgentAssignment a = new AgentAssignment();
            a.setTimeslot(ts);
            shift.add(a);
        }

        assertTimeoutPreemptively(ofSeconds(5), () ->
                assertThat(ScheduleConstraintProvider.getGapLengths(shift, 30)).isEmpty());
    }
}
