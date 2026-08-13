package com.wfm.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers {@link TimeslotGeneratorService#isDesired}, the predicate that decides which
 * existing live timeslots survive a regeneration.
 *
 * <p>Regression context: the predicate originally validated a slot's start time but not
 * its duration. Any stale slot whose start happened to land on the new increment grid was
 * judged still-wanted and kept, while the insert loop — which keys on date|start|end —
 * added the correctly-sized slot alongside it. Refining granularity therefore retained
 * 100% of the old slots, and the partial unique index (which includes end_time) was happy
 * to store both. This suite pins the behaviour for every 15/30/60 transition.
 */
class TimeslotGeneratorServiceTest {

    private static final LocalDate DAY = LocalDate.of(2026, 8, 13);
    private static final LocalDate PERIOD_START = LocalDate.of(2026, 8, 10);
    private static final LocalDate PERIOD_END = LocalDate.of(2026, 8, 16);
    private static final LocalTime OPEN = LocalTime.of(8, 0);
    private static final LocalTime CLOSE = LocalTime.of(18, 0);

    /** Convenience wrapper: is a slot [start, start+durationMinutes) wanted at this increment? */
    private static boolean desired(LocalTime start, int durationMinutes, int incrementMinutes) {
        return TimeslotGeneratorService.isDesired(
                DAY, start, start.plusMinutes(durationMinutes),
                PERIOD_START, PERIOD_END, OPEN, CLOSE, incrementMinutes);
    }

    @Nested
    @DisplayName("a slot is kept only when its duration matches the requested increment")
    class DurationIsChecked {

        @Test
        @DisplayName("correctly-sized slot on the grid is kept")
        void correctlySizedSlotIsKept() {
            assertThat(desired(LocalTime.of(9, 0), 30, 30)).isTrue();
            assertThat(desired(LocalTime.of(9, 0), 15, 15)).isTrue();
            assertThat(desired(LocalTime.of(9, 0), 60, 60)).isTrue();
        }

        @ParameterizedTest(name = "{0}-minute slot at 09:00 is discarded when increment becomes {1}")
        @CsvSource({
                // the regression: start aligns with the new grid, duration does not
                "60, 30", "60, 15", "30, 15", "30, 60", "15, 30", "15, 60",
        })
        void wrongDurationIsDiscardedEvenWhenStartAligns(int oldDuration, int newIncrement) {
            LocalTime onGrid = LocalTime.of(9, 0); // a multiple of 15, 30 and 60 past 08:00
            assertThat(onGrid.until(onGrid, java.time.temporal.ChronoUnit.MINUTES)).isZero();
            assertThat(desired(onGrid, oldDuration, newIncrement))
                    .as("%d-min slot must not survive a change to %d-min", oldDuration, newIncrement)
                    .isFalse();
        }

        @Test
        @DisplayName("refining 60 -> 30 discards every old slot, not just the misaligned ones")
        void refiningGranularityDiscardsAllOldSlots() {
            // Every hourly start (08:00, 09:00, ... 17:00) aligns with a 30-minute grid,
            // so the start-only predicate kept all ten. All ten must now go.
            for (int hour = 8; hour < 18; hour++) {
                assertThat(desired(LocalTime.of(hour, 0), 60, 30))
                        .as("hourly slot at %02d:00 must not survive 60 -> 30", hour)
                        .isFalse();
            }
        }

        @Test
        @DisplayName("coarsening 30 -> 60 discards the aligned half too")
        void coarseningDiscardsAlignedSlotsWithWrongDuration() {
            assertThat(desired(LocalTime.of(9, 0), 30, 60)).isFalse();  // aligned, wrong duration
            assertThat(desired(LocalTime.of(9, 30), 30, 60)).isFalse(); // misaligned
        }
    }

    @Nested
    @DisplayName("grid alignment and window bounds")
    class AlignmentAndBounds {

        @Test
        void offGridStartIsDiscarded() {
            assertThat(desired(LocalTime.of(9, 15), 60, 60)).isFalse();
            assertThat(desired(LocalTime.of(9, 30), 60, 60)).isFalse();
        }

        @Test
        void slotBeforeOpeningTimeIsDiscarded() {
            assertThat(desired(LocalTime.of(7, 0), 60, 60)).isFalse();
        }

        @Test
        void slotAtOrAfterClosingTimeIsDiscarded() {
            assertThat(desired(CLOSE, 60, 60)).isFalse();
            assertThat(desired(LocalTime.of(19, 0), 60, 60)).isFalse();
        }

        @Test
        void lastSlotOfTheDayIsKept() {
            assertThat(desired(LocalTime.of(17, 0), 60, 60)).isTrue();
        }
    }

    @Nested
    @DisplayName("period bounds")
    class PeriodBounds {

        private static boolean desiredOn(LocalDate date) {
            LocalTime start = LocalTime.of(9, 0);
            return TimeslotGeneratorService.isDesired(
                    date, start, start.plusMinutes(60),
                    PERIOD_START, PERIOD_END, OPEN, CLOSE, 60);
        }

        @Test
        @DisplayName("slots outside a shortened period are discarded")
        void outOfRangeDatesAreDiscarded() {
            assertThat(desiredOn(PERIOD_START.minusDays(1))).isFalse();
            assertThat(desiredOn(PERIOD_END.plusDays(1))).isFalse();
        }

        @Test
        void inRangeDatesAreKeptInclusiveOfBothEnds() {
            assertThat(desiredOn(PERIOD_START)).isTrue();
            assertThat(desiredOn(PERIOD_END)).isTrue();
            assertThat(desiredOn(PERIOD_START.plusDays(1))).isTrue();
        }
    }
}
