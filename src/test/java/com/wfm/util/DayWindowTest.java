package com.wfm.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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

    // --------------------------------------------------------------------------------------
    // Day-start-aware vocabulary (BDAY-03). These prove the anchored functions below collapse
    // onto their midnight-implicit counterparts above exactly when the anchor is 00:00 -- the
    // provable-no-op claim of the whole phase -- exhaustively across all 1440 minutes, not at
    // sampled points.
    // --------------------------------------------------------------------------------------

    @Nested
    @DisplayName("anchored functions equal their midnight-implicit counterparts at a 00:00 anchor")
    class AnchoredEquivalenceAtMidnightAnchor {

        @Test
        @DisplayName("startMinuteFromDayStart/endMinuteFromDayStart equal startMinute/endMinute for every minute of the day")
        void startAndEndMinuteEquivalence() {
            for (int m = 0; m < 1440; m++) {
                LocalTime t = DayWindow.toLocalTime(m);
                assertThat(DayWindow.startMinuteFromDayStart(MIDNIGHT, t))
                        .as("startMinuteFromDayStart(MIDNIGHT, %s)", t)
                        .isEqualTo(DayWindow.startMinute(t));
                assertThat(DayWindow.endMinuteFromDayStart(MIDNIGHT, t))
                        .as("endMinuteFromDayStart(MIDNIGHT, %s)", t)
                        .isEqualTo(DayWindow.endMinute(t));
            }
        }

        @Test
        @DisplayName("timeAtDayStartOffset equals toLocalTime for every offset in [0, 1440]")
        void timeAtDayStartOffsetEquivalence() {
            for (int m = 0; m <= 1440; m++) {
                assertThat(DayWindow.timeAtDayStartOffset(MIDNIGHT, m))
                        .as("timeAtDayStartOffset(MIDNIGHT, %d)", m)
                        .isEqualTo(DayWindow.toLocalTime(m));
            }
        }

        @Test
        @DisplayName("calendarDateAtDayStartOffset returns the same calendar date for every offset in [0, 1440)")
        void calendarDateAtDayStartOffsetEquivalence() {
            LocalDate d = LocalDate.of(2026, 10, 1);
            for (int m = 0; m < 1440; m++) {
                assertThat(DayWindow.calendarDateAtDayStartOffset(MIDNIGHT, d, m))
                        .as("calendarDateAtDayStartOffset(MIDNIGHT, %s, %d)", d, m)
                        .isEqualTo(d);
            }
        }

        @Test
        @DisplayName("businessDateOf returns the calendar date for every minute of the day")
        void businessDateOfEquivalence() {
            LocalDate d = LocalDate.of(2026, 10, 1);
            for (int m = 0; m < 1440; m++) {
                LocalTime t = DayWindow.toLocalTime(m);
                assertThat(DayWindow.businessDateOf(MIDNIGHT, d, t))
                        .as("businessDateOf(MIDNIGHT, %s, %s)", d, t)
                        .isEqualTo(d);
            }
        }
    }

    @Nested
    @DisplayName("anchored behaviour at a 21:00 anchor")
    class AnchoredBehaviorAt2100 {

        private final LocalTime anchor = LocalTime.of(21, 0);

        @Test
        void startMinuteFromDayStartCases() {
            assertThat(DayWindow.startMinuteFromDayStart(anchor, LocalTime.of(21, 0))).isZero();
            assertThat(DayWindow.startMinuteFromDayStart(anchor, LocalTime.of(22, 0))).isEqualTo(60);
            assertThat(DayWindow.startMinuteFromDayStart(anchor, MIDNIGHT)).isEqualTo(180);
            assertThat(DayWindow.startMinuteFromDayStart(anchor, LocalTime.of(20, 45))).isEqualTo(1425);
        }

        @Test
        @DisplayName("21:00 in an END position is the end of a 21:00-anchored business day (1440), NOT 00:00's 180")
        void endMinuteFromDayStartCases() {
            assertThat(DayWindow.endMinuteFromDayStart(anchor, LocalTime.of(21, 0))).isEqualTo(1440);
            assertThat(DayWindow.endMinuteFromDayStart(anchor, MIDNIGHT)).isEqualTo(180);
        }

        @Test
        void timeAtDayStartOffsetCases() {
            assertThat(DayWindow.timeAtDayStartOffset(anchor, 0)).isEqualTo(LocalTime.of(21, 0));
            assertThat(DayWindow.timeAtDayStartOffset(anchor, 180)).isEqualTo(MIDNIGHT);
            assertThat(DayWindow.timeAtDayStartOffset(anchor, 1439)).isEqualTo(LocalTime.of(20, 59));
            assertThat(DayWindow.timeAtDayStartOffset(anchor, 1440)).isEqualTo(LocalTime.of(21, 0));
        }

        @Test
        void businessDateOfCases() {
            assertThat(DayWindow.businessDateOf(anchor, LocalDate.of(2026, 10, 1), LocalTime.of(21, 0)))
                    .isEqualTo(LocalDate.of(2026, 10, 1));
            assertThat(DayWindow.businessDateOf(anchor, LocalDate.of(2026, 10, 2), LocalTime.of(3, 0)))
                    .isEqualTo(LocalDate.of(2026, 10, 1));
            assertThat(DayWindow.businessDateOf(anchor, LocalDate.of(2026, 10, 1), LocalTime.of(20, 59)))
                    .isEqualTo(LocalDate.of(2026, 9, 30));
        }

        @Test
        void calendarDateAtDayStartOffsetCases() {
            LocalDate d = LocalDate.of(2026, 10, 1);
            assertThat(DayWindow.calendarDateAtDayStartOffset(anchor, d, 0)).isEqualTo(d);
            assertThat(DayWindow.calendarDateAtDayStartOffset(anchor, d, 179)).isEqualTo(d);
            assertThat(DayWindow.calendarDateAtDayStartOffset(anchor, d, 180)).isEqualTo(d.plusDays(1));
            assertThat(DayWindow.calendarDateAtDayStartOffset(anchor, d, 1439)).isEqualTo(d.plusDays(1));
        }
    }

    @Nested
    @DisplayName("round-trip consistency across several anchors")
    class RoundTripConsistency {

        private final List<LocalTime> anchors = List.of(
                MIDNIGHT, LocalTime.of(21, 0), LocalTime.of(6, 15), LocalTime.of(23, 45));

        @Test
        @DisplayName("startMinuteFromDayStart(anchor, timeAtDayStartOffset(anchor, m)) == m for every offset")
        void startMinuteRoundTrips() {
            for (LocalTime anchor : anchors) {
                for (int m = 0; m < 1440; m++) {
                    LocalTime t = DayWindow.timeAtDayStartOffset(anchor, m);
                    assertThat(DayWindow.startMinuteFromDayStart(anchor, t))
                            .as("anchor=%s, m=%d, t=%s", anchor, m, t)
                            .isEqualTo(m);
                }
            }
        }

        @Test
        @DisplayName("the walk's forward direction and isDesired's classifying direction agree")
        void businessDateRoundTrips() {
            LocalDate d = LocalDate.of(2026, 10, 1);
            for (LocalTime anchor : anchors) {
                for (int m = 0; m < 1440; m++) {
                    LocalDate calendarDate = DayWindow.calendarDateAtDayStartOffset(anchor, d, m);
                    LocalTime time = DayWindow.timeAtDayStartOffset(anchor, m);
                    assertThat(DayWindow.businessDateOf(anchor, calendarDate, time))
                            .as("anchor=%s, m=%d, calendarDate=%s, time=%s", anchor, m, calendarDate, time)
                            .isEqualTo(d);
                }
            }
        }
    }

    @Nested
    @DisplayName("day-start-aware functions fail loudly at the boundary")
    class FailLoudlyAtTheDayStartBoundary {

        private final LocalTime anchor = LocalTime.of(21, 0);
        private final LocalDate date = LocalDate.of(2026, 10, 1);

        @Test
        void startMinuteFromDayStartRejectsNulls() {
            assertThatThrownBy(() -> DayWindow.startMinuteFromDayStart(null, LocalTime.NOON))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("dayStart");
            assertThatThrownBy(() -> DayWindow.startMinuteFromDayStart(anchor, null))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("start");
        }

        @Test
        void endMinuteFromDayStartRejectsNulls() {
            assertThatThrownBy(() -> DayWindow.endMinuteFromDayStart(null, LocalTime.NOON))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("dayStart");
            assertThatThrownBy(() -> DayWindow.endMinuteFromDayStart(anchor, null))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("end");
        }

        @Test
        void timeAtDayStartOffsetRejectsNullDayStartAndOutOfRangeOffsets() {
            assertThatThrownBy(() -> DayWindow.timeAtDayStartOffset(null, 0))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("dayStart");
            assertThatThrownBy(() -> DayWindow.timeAtDayStartOffset(anchor, -1))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> DayWindow.timeAtDayStartOffset(anchor, 1441))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void businessDateOfRejectsNulls() {
            assertThatThrownBy(() -> DayWindow.businessDateOf(null, date, LocalTime.NOON))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("dayStart");
            assertThatThrownBy(() -> DayWindow.businessDateOf(anchor, null, LocalTime.NOON))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("calendarDate");
            assertThatThrownBy(() -> DayWindow.businessDateOf(anchor, date, null))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("timeOfDay");
        }

        @Test
        void calendarDateAtDayStartOffsetRejectsNullsAndOutOfRangeOffsets() {
            assertThatThrownBy(() -> DayWindow.calendarDateAtDayStartOffset(null, date, 0))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("dayStart");
            assertThatThrownBy(() -> DayWindow.calendarDateAtDayStartOffset(anchor, null, 0))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("businessDate");
            assertThatThrownBy(() -> DayWindow.calendarDateAtDayStartOffset(anchor, date, -1))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> DayWindow.calendarDateAtDayStartOffset(anchor, date, 1440))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("deprecation is live: every midnight-implicit function is @Deprecated, no anchored function is")
    class DeprecationIsLive {

        private static final Set<String> MIDNIGHT_IMPLICIT = Set.of(
                "startMinute", "endMinute", "durationMinutes", "isForwardWithinDay", "overlaps",
                "contains", "startsBefore", "toLocalTime", "plusWithinDay");

        private static final Set<String> ANCHORED = Set.of(
                "startMinuteFromDayStart", "endMinuteFromDayStart", "timeAtDayStartOffset",
                "businessDateOf", "calendarDateAtDayStartOffset");

        @Test
        @DisplayName("every midnight-implicit static carries @Deprecated; no anchored static does")
        void deprecationMatchesTheMidnightImplicitSetExactly() {
            List<Method> publicStatics = Arrays.stream(DayWindow.class.getDeclaredMethods())
                    .filter(m -> Modifier.isPublic(m.getModifiers()) && Modifier.isStatic(m.getModifiers()))
                    .collect(Collectors.toList());

            assertThat(publicStatics).as("public static methods declared on DayWindow").isNotEmpty();

            for (Method m : publicStatics) {
                boolean deprecated = m.isAnnotationPresent(Deprecated.class);
                if (MIDNIGHT_IMPLICIT.contains(m.getName())) {
                    assertThat(deprecated).as("%s must carry @Deprecated (BDAY-04)", m.getName()).isTrue();
                } else if (ANCHORED.contains(m.getName())) {
                    assertThat(deprecated).as("%s must NOT carry @Deprecated", m.getName()).isFalse();
                }
            }

            Set<String> actuallyDeprecated = publicStatics.stream()
                    .filter(m -> m.isAnnotationPresent(Deprecated.class))
                    .map(Method::getName)
                    .collect(Collectors.toSet());
            assertThat(actuallyDeprecated).containsExactlyInAnyOrderElementsOf(MIDNIGHT_IMPLICIT);
        }
    }

    // --------------------------------------------------------------------------------------
    // The frozen oracle (BDAY-04, D-13). This pins today's nine midnight-implicit
    // implementations before plan 19-03 rewrites DayWindow and plan 19-08 demotes them to
    // private delegates of the anchored primitives below the BDAY-03 banner. The comparison
    // target here is the live public statics above -- they are what plan 19-08 re-points at a
    // bound anchoredAt(MIDNIGHT) instance once those statics stop being public.
    // --------------------------------------------------------------------------------------

    /**
     * A verbatim, frozen transcription of today's nine midnight-implicit {@link DayWindow}
     * implementations, taken while they are still public and callable. This oracle must never be
     * refactored, simplified, de-duplicated, or made to delegate to {@link DayWindow} or anything
     * else in {@code com.wfm.util} -- its entire value is being independent of whatever
     * {@link DayWindow} becomes once BDAY-04 re-anchors it. Pinned for BDAY-04 (D-13); deletable
     * once v1.5 ships and nothing compares against pre-migration behaviour any more.
     */
    private static final class FrozenOracle {

        private static final int MINUTES_PER_DAY = 1440;

        private FrozenOracle() {}

        static int startMinute(LocalTime start) {
            requireNonNull(start, "start");
            return start.getHour() * 60 + start.getMinute();
        }

        static int endMinute(LocalTime end) {
            requireNonNull(end, "end");
            return end.equals(LocalTime.MIDNIGHT) ? MINUTES_PER_DAY : end.getHour() * 60 + end.getMinute();
        }

        static int durationMinutes(LocalTime start, LocalTime end) {
            int minutes = endMinute(end) - startMinute(start);
            if (minutes <= 0) {
                throw new IllegalArgumentException(
                        "Interval must run forward within a single day, but got " + start + " to " + end
                                + ". A window ending at midnight is supported (end 00:00); one crossing "
                                + "midnight into the next day is not.");
            }
            return minutes;
        }

        static boolean isForwardWithinDay(LocalTime start, LocalTime end) {
            return start != null && end != null && endMinute(end) > startMinute(start);
        }

        static boolean overlaps(LocalTime s1, LocalTime e1, LocalTime s2, LocalTime e2) {
            return startMinute(s1) < endMinute(e2) && endMinute(e1) > startMinute(s2);
        }

        static boolean contains(LocalTime outerStart, LocalTime outerEnd,
                                 LocalTime innerStart, LocalTime innerEnd) {
            return startMinute(innerStart) >= startMinute(outerStart)
                    && endMinute(innerEnd) <= endMinute(outerEnd);
        }

        static boolean startsBefore(LocalTime start, LocalTime end) {
            return startMinute(start) < endMinute(end);
        }

        static LocalTime toLocalTime(int minuteOfDay) {
            if (minuteOfDay < 0 || minuteOfDay > MINUTES_PER_DAY) {
                throw new IllegalArgumentException(
                        "minuteOfDay must be within [0, " + MINUTES_PER_DAY + "] but was " + minuteOfDay);
            }
            if (minuteOfDay == MINUTES_PER_DAY) {
                return LocalTime.MIDNIGHT;
            }
            return LocalTime.of(minuteOfDay / 60, minuteOfDay % 60);
        }

        static LocalTime plusWithinDay(LocalTime base, int minutes) {
            return toLocalTime(startMinute(base) + minutes);
        }

        private static void requireNonNull(LocalTime value, String name) {
            if (value == null) {
                throw new IllegalArgumentException(name + " must not be null");
            }
        }
    }

    @Nested
    @DisplayName("the frozen oracle agrees with today's live DayWindow statics at every point of the input domain (D-13)")
    // Deliberately split the keyword from the identifier across two lines: this class's name
    // begins with the oracle's own name, so keeping them on one line would make this declaration
    // also match Task 1's structural "exactly one nested oracle class" grep, which must count only
    // the private nested oracle holder above and nothing else.
    class
            FrozenOracleEquivalence {

        // One comparison-target method per oracle function (nine total). Plan 19-08 re-points
        // exactly these nine methods at a bound DayWindow.anchoredAt(MIDNIGHT) instance once the
        // midnight-implicit statics are demoted to private; no assertion below needs to change.
        private int liveStartMinute(LocalTime t) {
            return DayWindow.startMinute(t);
        }

        private int liveEndMinute(LocalTime t) {
            return DayWindow.endMinute(t);
        }

        private LocalTime liveToLocalTime(int m) {
            return DayWindow.toLocalTime(m);
        }

        private int liveDurationMinutes(LocalTime s, LocalTime e) {
            return DayWindow.durationMinutes(s, e);
        }

        private boolean liveIsForwardWithinDay(LocalTime s, LocalTime e) {
            return DayWindow.isForwardWithinDay(s, e);
        }

        private boolean liveStartsBefore(LocalTime s, LocalTime e) {
            return DayWindow.startsBefore(s, e);
        }

        private boolean liveOverlaps(LocalTime s1, LocalTime e1, LocalTime s2, LocalTime e2) {
            return DayWindow.overlaps(s1, e1, s2, e2);
        }

        private boolean liveContains(LocalTime outerStart, LocalTime outerEnd,
                                      LocalTime innerStart, LocalTime innerEnd) {
            return DayWindow.contains(outerStart, outerEnd, innerStart, innerEnd);
        }

        private LocalTime livePlusWithinDay(LocalTime base, int minutes) {
            return DayWindow.plusWithinDay(base, minutes);
        }

        @Test
        @DisplayName("startMinute and endMinute of toLocalTime(m) agree with the oracle for every minute of the day")
        void startAndEndMinuteAgreeForEveryMinute() {
            for (int m = 0; m < 1440; m++) {
                LocalTime t = liveToLocalTime(m);
                assertThat(FrozenOracle.startMinute(t)).as("startMinute(%s)", t).isEqualTo(liveStartMinute(t));
                assertThat(FrozenOracle.endMinute(t)).as("endMinute(%s)", t).isEqualTo(liveEndMinute(t));
            }
        }

        @Test
        @DisplayName("toLocalTime agrees with the oracle for every minute in [0, 1440]")
        void toLocalTimeAgreesForEveryOffset() {
            for (int m = 0; m <= 1440; m++) {
                assertThat(FrozenOracle.toLocalTime(m)).as("toLocalTime(%d)", m).isEqualTo(liveToLocalTime(m));
            }
        }

        @Test
        @DisplayName("isForwardWithinDay and startsBefore agree with the oracle for every ordered pair of minutes")
        void forwardAndStartsBeforeAgreeForEveryOrderedPair() {
            for (int i = 0; i < 1440; i++) {
                LocalTime start = liveToLocalTime(i);
                for (int j = 0; j < 1440; j++) {
                    LocalTime end = liveToLocalTime(j);
                    assertThat(FrozenOracle.isForwardWithinDay(start, end))
                            .as("isForwardWithinDay(%s, %s)", start, end)
                            .isEqualTo(liveIsForwardWithinDay(start, end));
                    assertThat(FrozenOracle.startsBefore(start, end))
                            .as("startsBefore(%s, %s)", start, end)
                            .isEqualTo(liveStartsBefore(start, end));
                }
            }
        }

        @Test
        @DisplayName("durationMinutes agrees with the oracle for every ordered pair of minutes, including which pairs throw")
        void durationMinutesAgreesForEveryOrderedPair() {
            for (int i = 0; i < 1440; i++) {
                LocalTime start = liveToLocalTime(i);
                for (int j = 0; j < 1440; j++) {
                    LocalTime end = liveToLocalTime(j);
                    Integer liveResult = null;
                    RuntimeException liveException = null;
                    try {
                        liveResult = liveDurationMinutes(start, end);
                    } catch (IllegalArgumentException e) {
                        liveException = e;
                    }
                    Integer oracleResult = null;
                    RuntimeException oracleException = null;
                    try {
                        oracleResult = FrozenOracle.durationMinutes(start, end);
                    } catch (IllegalArgumentException e) {
                        oracleException = e;
                    }
                    if (liveException == null) {
                        assertThat(oracleException)
                                .as("durationMinutes(%s, %s) oracle should not throw", start, end).isNull();
                        assertThat(oracleResult)
                                .as("durationMinutes(%s, %s)", start, end).isEqualTo(liveResult);
                    } else {
                        assertThat(oracleException)
                                .as("durationMinutes(%s, %s) oracle should throw", start, end).isNotNull();
                        assertThat(oracleException.getClass())
                                .as("durationMinutes(%s, %s) exception type", start, end)
                                .isEqualTo(liveException.getClass());
                        assertThat(oracleException.getMessage())
                                .as("durationMinutes(%s, %s) exception message", start, end)
                                .isEqualTo(liveException.getMessage());
                    }
                }
            }
        }

        @Test
        @DisplayName("plusWithinDay agrees with the oracle for every base minute against a bounded offset set "
                + "including the day-end boundary and one minute past it")
        void plusWithinDayAgreesForEveryBaseMinute() {
            for (int b = 0; b < 1440; b++) {
                LocalTime base = liveToLocalTime(b);
                int exactBoundaryOffset = DayWindow.MINUTES_PER_DAY - b;
                int[] offsets = {0, 1, 15, 30, 60, exactBoundaryOffset, exactBoundaryOffset + 1};
                for (int offset : offsets) {
                    LocalTime liveResult = null;
                    RuntimeException liveException = null;
                    try {
                        liveResult = livePlusWithinDay(base, offset);
                    } catch (IllegalArgumentException e) {
                        liveException = e;
                    }
                    LocalTime oracleResult = null;
                    RuntimeException oracleException = null;
                    try {
                        oracleResult = FrozenOracle.plusWithinDay(base, offset);
                    } catch (IllegalArgumentException e) {
                        oracleException = e;
                    }
                    if (liveException == null) {
                        assertThat(oracleException)
                                .as("plusWithinDay(%s, %d) oracle should not throw", base, offset).isNull();
                        assertThat(oracleResult)
                                .as("plusWithinDay(%s, %d)", base, offset).isEqualTo(liveResult);
                    } else {
                        assertThat(oracleException)
                                .as("plusWithinDay(%s, %d) oracle should throw", base, offset).isNotNull();
                        assertThat(oracleException.getClass())
                                .as("plusWithinDay(%s, %d) exception type", base, offset)
                                .isEqualTo(liveException.getClass());
                        assertThat(oracleException.getMessage())
                                .as("plusWithinDay(%s, %d) exception message", base, offset)
                                .isEqualTo(liveException.getMessage());
                    }
                }
            }
        }

        @Test
        @DisplayName("overlaps and contains agree with the oracle over a structured sweep of boundary-adjacent quadruples")
        void overlapsAndContainsAgreeOverBoundaryAdjacentQuadruples() {
            int[] candidates = {0, 1, 2, 359, 360, 361, 719, 720, 721, 1079, 1080, 1081, 1438, 1439, 1440};
            for (int a : candidates) {
                LocalTime s1 = liveToLocalTime(a);
                for (int b : candidates) {
                    LocalTime e1 = liveToLocalTime(b);
                    for (int c : candidates) {
                        LocalTime s2 = liveToLocalTime(c);
                        for (int d : candidates) {
                            LocalTime e2 = liveToLocalTime(d);
                            assertThat(FrozenOracle.overlaps(s1, e1, s2, e2))
                                    .as("overlaps(%s, %s, %s, %s)", s1, e1, s2, e2)
                                    .isEqualTo(liveOverlaps(s1, e1, s2, e2));
                            assertThat(FrozenOracle.contains(s1, e1, s2, e2))
                                    .as("contains(%s, %s, %s, %s)", s1, e1, s2, e2)
                                    .isEqualTo(liveContains(s1, e1, s2, e2));
                        }
                    }
                }
            }
        }
    }
}
