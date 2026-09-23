package com.wfm.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins {@link DayWindow}'s position-dependent reading of {@code 00:00} — the whole point of the
 * class. Every test here fails against plain {@link LocalTime} arithmetic, which is what made a
 * desk running to midnight unrepresentable.
 */
class DayWindowTest {

    private static final LocalTime MIDNIGHT = LocalTime.MIDNIGHT;

    @Nested
    @DisplayName("00:00 reads as minute 0 in a start position and 1440 in an end position")
    class PositionMatters {

        @Test
        void startMinuteOfMidnightIsZero() {
            assertThat(DayWindow.startMinute(MIDNIGHT)).isZero();
        }

        @Test
        void endMinuteOfMidnightIsEndOfDay() {
            assertThat(DayWindow.endMinute(MIDNIGHT)).isEqualTo(1440);
        }

        @ParameterizedTest(name = "{0} -> start {1}, end {1}")
        @CsvSource({"08:00,480", "15:00,900", "23:00,1380", "23:45,1425"})
        void everyOtherTimeReadsIdenticallyInBothPositions(LocalTime time, int expected) {
            assertThat(DayWindow.startMinute(time)).isEqualTo(expected);
            assertThat(DayWindow.endMinute(time)).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("durationMinutes")
    class Duration {

        @Test
        @DisplayName("the 15:00-00:00 shift that could not previously be saved is 9 hours")
        void midnightEndingShift() {
            assertThat(DayWindow.durationMinutes(LocalTime.of(15, 0), MIDNIGHT)).isEqualTo(540);
        }

        @Test
        @DisplayName("an 08:00-00:00 operating window is 16 hours")
        void midnightEndingWindow() {
            assertThat(DayWindow.durationMinutes(LocalTime.of(8, 0), MIDNIGHT)).isEqualTo(960);
        }

        @Test
        @DisplayName("the final 23:00-00:00 timeslot is one hour, not minus twenty-three")
        void finalSlotOfTheDay() {
            assertThat(DayWindow.durationMinutes(LocalTime.of(23, 0), MIDNIGHT)).isEqualTo(60);
        }

        @Test
        void ordinarySameDayInterval() {
            assertThat(DayWindow.durationMinutes(LocalTime.of(8, 0), LocalTime.of(17, 0)))
                    .isEqualTo(540);
        }

        @Test
        @DisplayName("a shift crossing midnight throws rather than returning a wrapped value")
        void crossingMidnightIsRejected() {
            assertThatThrownBy(() -> DayWindow.durationMinutes(LocalTime.of(22, 0), LocalTime.of(6, 0)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("crossing midnight");
        }

        @Test
        void zeroLengthIntervalIsRejected() {
            assertThatThrownBy(() -> DayWindow.durationMinutes(LocalTime.of(9, 0), LocalTime.of(9, 0)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("midnight to midnight is a whole day, not zero")
        void midnightToMidnightIsAWholeDay() {
            assertThat(DayWindow.durationMinutes(MIDNIGHT, MIDNIGHT)).isEqualTo(1440);
        }
    }

    @Nested
    @DisplayName("isForwardWithinDay")
    class Forward {

        @Test
        void midnightEndIsForward() {
            assertThat(DayWindow.isForwardWithinDay(LocalTime.of(15, 0), MIDNIGHT)).isTrue();
        }

        @Test
        void crossingMidnightIsNot() {
            assertThat(DayWindow.isForwardWithinDay(LocalTime.of(22, 0), LocalTime.of(6, 0))).isFalse();
        }

        @Test
        void equalEndpointsAreNot() {
            assertThat(DayWindow.isForwardWithinDay(LocalTime.of(9, 0), LocalTime.of(9, 0))).isFalse();
        }

        @Test
        void nullIsNot() {
            assertThat(DayWindow.isForwardWithinDay(null, MIDNIGHT)).isFalse();
            assertThat(DayWindow.isForwardWithinDay(LocalTime.of(9, 0), null)).isFalse();
        }
    }

    @Nested
    @DisplayName("overlaps is half-open on both ends")
    class Overlaps {

        @Test
        @DisplayName("the last slot of the day overlaps a break that runs to midnight")
        void lastSlotOverlapsMidnightBreak() {
            assertThat(DayWindow.overlaps(
                    LocalTime.of(23, 0), MIDNIGHT,
                    LocalTime.of(22, 30), MIDNIGHT)).isTrue();
        }

        @Test
        @DisplayName("a slot ending at midnight does not overlap a break ending where it starts")
        void touchingIntervalsDoNotOverlap() {
            assertThat(DayWindow.overlaps(
                    LocalTime.of(23, 0), MIDNIGHT,
                    LocalTime.of(22, 0), LocalTime.of(23, 0))).isFalse();
        }

        @Test
        void ordinaryOverlap() {
            assertThat(DayWindow.overlaps(
                    LocalTime.of(9, 0), LocalTime.of(10, 0),
                    LocalTime.of(9, 30), LocalTime.of(10, 30))).isTrue();
        }

        @Test
        void disjointIntervals() {
            assertThat(DayWindow.overlaps(
                    LocalTime.of(9, 0), LocalTime.of(10, 0),
                    LocalTime.of(14, 0), LocalTime.of(15, 0))).isFalse();
        }
    }

    @Nested
    @DisplayName("contains")
    class Contains {

        @Test
        @DisplayName("a 22:00-23:00 slot sits inside a 15:00-00:00 envelope")
        void slotInsideMidnightEndingEnvelope() {
            assertThat(DayWindow.contains(LocalTime.of(15, 0), MIDNIGHT,
                    LocalTime.of(22, 0), LocalTime.of(23, 0))).isTrue();
        }

        @Test
        @DisplayName("the final 23:00-00:00 slot sits inside it too, flush with the end")
        void flushSlotIsContained() {
            assertThat(DayWindow.contains(LocalTime.of(15, 0), MIDNIGHT,
                    LocalTime.of(23, 0), MIDNIGHT)).isTrue();
        }

        @Test
        void slotStartingBeforeTheEnvelopeIsNotContained() {
            assertThat(DayWindow.contains(LocalTime.of(15, 0), MIDNIGHT,
                    LocalTime.of(14, 0), LocalTime.of(15, 0))).isFalse();
        }

        @Test
        @DisplayName("a slot running past a non-midnight envelope end is not contained")
        void slotOverrunningIsNotContained() {
            assertThat(DayWindow.contains(LocalTime.of(8, 0), LocalTime.of(17, 0),
                    LocalTime.of(16, 30), LocalTime.of(17, 30))).isFalse();
        }
    }

    @Nested
    @DisplayName("startsBefore")
    class StartsBefore {

        @Test
        @DisplayName("every start in a midnight-ending day is before its end boundary")
        void startsInMidnightEndingDay() {
            assertThat(DayWindow.startsBefore(LocalTime.of(8, 0), MIDNIGHT)).isTrue();
            assertThat(DayWindow.startsBefore(LocalTime.of(23, 0), MIDNIGHT)).isTrue();
        }

        @Test
        void midnightStartIsBeforeMidnightEnd() {
            assertThat(DayWindow.startsBefore(MIDNIGHT, MIDNIGHT)).isTrue();
        }

        @Test
        void aStartOnTheBoundaryIsNotBeforeIt() {
            assertThat(DayWindow.startsBefore(LocalTime.of(17, 0), LocalTime.of(17, 0))).isFalse();
        }
    }

    @Nested
    @DisplayName("toLocalTime and plusWithinDay round-trip through the boundary")
    class Conversion {

        @Test
        void endOfDayMapsBackToMidnight() {
            assertThat(DayWindow.toLocalTime(1440)).isEqualTo(MIDNIGHT);
        }

        @Test
        void zeroMapsToMidnightToo() {
            assertThat(DayWindow.toLocalTime(0)).isEqualTo(MIDNIGHT);
        }

        @Test
        void endMinuteRoundTripsLosslessly() {
            assertThat(DayWindow.toLocalTime(DayWindow.endMinute(MIDNIGHT))).isEqualTo(MIDNIGHT);
            assertThat(DayWindow.toLocalTime(DayWindow.endMinute(LocalTime.of(23, 0))))
                    .isEqualTo(LocalTime.of(23, 0));
        }

        @Test
        @DisplayName("23:00 plus an hour is the end of the day, not 00:00 tomorrow morning")
        void plusLandsOnTheBoundary() {
            assertThat(DayWindow.plusWithinDay(LocalTime.of(23, 0), 60)).isEqualTo(MIDNIGHT);
        }

        @Test
        @DisplayName("15:00 plus nine hours is midnight — the shift that motivated this class")
        void nineHourEveningShift() {
            assertThat(DayWindow.plusWithinDay(LocalTime.of(15, 0), 540)).isEqualTo(MIDNIGHT);
        }

        @Test
        @DisplayName("going past the end of the day throws instead of silently wrapping")
        void wrappingThrows() {
            assertThatThrownBy(() -> DayWindow.plusWithinDay(LocalTime.of(23, 0), 120))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("1440");
            // LocalTime's own arithmetic is what this protects against:
            assertThat(LocalTime.of(23, 0).plusMinutes(120)).isEqualTo(LocalTime.of(1, 0));
        }

        @Test
        void outOfRangeMinuteIsRejected() {
            assertThatThrownBy(() -> DayWindow.toLocalTime(1441))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> DayWindow.toLocalTime(-1))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
