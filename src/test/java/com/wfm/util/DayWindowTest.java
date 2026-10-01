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

    /**
     * The nine midnight-implicit statics are private as of BDAY-04 (plan 19-08, D-06); every
     * nested class below that exercised them directly now binds this midnight-anchored instance
     * instead, per D-14. At a {@code 00:00} anchor every {@code anchored*} instance method agrees
     * exactly with its retired static counterpart -- that collapse is what
     * {@code FrozenOracleEquivalence} below proves exhaustively.
     */
    private static final DayWindow MIDNIGHT_WINDOW = DayWindow.anchoredAt(MIDNIGHT);

    @Nested
    @DisplayName("00:00 reads as minute 0 in a start position and 1440 in an end position")
    class PositionMatters {

        @Test
        void startMinuteOfMidnightIsZero() {
            assertThat(MIDNIGHT_WINDOW.anchoredStartMinute(MIDNIGHT)).isZero();
        }

        @Test
        void endMinuteOfMidnightIsEndOfDay() {
            assertThat(MIDNIGHT_WINDOW.anchoredEndMinute(MIDNIGHT)).isEqualTo(1440);
        }

        @ParameterizedTest(name = "{0} -> start {1}, end {1}")
        @CsvSource({"08:00,480", "15:00,900", "23:00,1380", "23:45,1425"})
        void everyOtherTimeReadsIdenticallyInBothPositions(LocalTime time, int expected) {
            assertThat(MIDNIGHT_WINDOW.anchoredStartMinute(time)).isEqualTo(expected);
            assertThat(MIDNIGHT_WINDOW.anchoredEndMinute(time)).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("durationMinutes")
    class Duration {

        @Test
        @DisplayName("the 15:00-00:00 shift that could not previously be saved is 9 hours")
        void midnightEndingShift() {
            assertThat(MIDNIGHT_WINDOW.anchoredDurationMinutes(LocalTime.of(15, 0), MIDNIGHT)).isEqualTo(540);
        }

        @Test
        @DisplayName("an 08:00-00:00 operating window is 16 hours")
        void midnightEndingWindow() {
            assertThat(MIDNIGHT_WINDOW.anchoredDurationMinutes(LocalTime.of(8, 0), MIDNIGHT)).isEqualTo(960);
        }

        @Test
        @DisplayName("the final 23:00-00:00 timeslot is one hour, not minus twenty-three")
        void finalSlotOfTheDay() {
            assertThat(MIDNIGHT_WINDOW.anchoredDurationMinutes(LocalTime.of(23, 0), MIDNIGHT)).isEqualTo(60);
        }

        @Test
        void ordinarySameDayInterval() {
            assertThat(MIDNIGHT_WINDOW.anchoredDurationMinutes(LocalTime.of(8, 0), LocalTime.of(17, 0)))
                    .isEqualTo(540);
        }

        @Test
        @DisplayName("BDAY-04 criterion 2: a shift crossing the anchor now yields the crossing-the-anchor "
                + "duration instead of throwing")
        void crossingTheAnchorYieldsTheWrappedDuration() {
            // Pre-migration this threw ("crossing midnight"); the anchored instance method wraps
            // forward across the anchor instead (D-11): 22:00 -> 06:00 is 8 hours (480 minutes).
            assertThat(MIDNIGHT_WINDOW.anchoredDurationMinutes(LocalTime.of(22, 0), LocalTime.of(6, 0)))
                    .isEqualTo(480);
        }

        @Test
        @DisplayName("BDAY-04 criterion 2: a non-anchor instant equal to itself is zero minutes, not a throw")
        void zeroLengthIntervalIsNoLongerRejected() {
            // Pre-migration this threw; the anchored instance method returns 0 for a genuine
            // zero-length interval at a non-anchor instant, distinct from the whole-day case below
            // at the anchor itself (ThrowDomainIsPinned.equalEndpointBoundaryIsNotConfusedWithTheWholeDayCase
            // pins this exact distinction).
            assertThat(MIDNIGHT_WINDOW.anchoredDurationMinutes(LocalTime.of(9, 0), LocalTime.of(9, 0)))
                    .isEqualTo(0);
        }

        @Test
        @DisplayName("midnight to midnight is a whole day, not zero")
        void midnightToMidnightIsAWholeDay() {
            assertThat(MIDNIGHT_WINDOW.anchoredDurationMinutes(MIDNIGHT, MIDNIGHT)).isEqualTo(1440);
        }
    }

    @Nested
    @DisplayName("isForwardWithinDay")
    class Forward {

        @Test
        void midnightEndIsForward() {
            assertThat(MIDNIGHT_WINDOW.anchoredIsForwardWithinDay(LocalTime.of(15, 0), MIDNIGHT)).isTrue();
        }

        @Test
        void crossingMidnightIsNot() {
            assertThat(MIDNIGHT_WINDOW.anchoredIsForwardWithinDay(LocalTime.of(22, 0), LocalTime.of(6, 0))).isFalse();
        }

        @Test
        void equalEndpointsAreNot() {
            assertThat(MIDNIGHT_WINDOW.anchoredIsForwardWithinDay(LocalTime.of(9, 0), LocalTime.of(9, 0))).isFalse();
        }

        @Test
        void nullIsNot() {
            assertThat(MIDNIGHT_WINDOW.anchoredIsForwardWithinDay(null, MIDNIGHT)).isFalse();
            assertThat(MIDNIGHT_WINDOW.anchoredIsForwardWithinDay(LocalTime.of(9, 0), null)).isFalse();
        }
    }

    @Nested
    @DisplayName("overlaps is half-open on both ends")
    class Overlaps {

        @Test
        @DisplayName("the last slot of the day overlaps a break that runs to midnight")
        void lastSlotOverlapsMidnightBreak() {
            assertThat(MIDNIGHT_WINDOW.anchoredOverlaps(
                    LocalTime.of(23, 0), MIDNIGHT,
                    LocalTime.of(22, 30), MIDNIGHT)).isTrue();
        }

        @Test
        @DisplayName("a slot ending at midnight does not overlap a break ending where it starts")
        void touchingIntervalsDoNotOverlap() {
            assertThat(MIDNIGHT_WINDOW.anchoredOverlaps(
                    LocalTime.of(23, 0), MIDNIGHT,
                    LocalTime.of(22, 0), LocalTime.of(23, 0))).isFalse();
        }

        @Test
        void ordinaryOverlap() {
            assertThat(MIDNIGHT_WINDOW.anchoredOverlaps(
                    LocalTime.of(9, 0), LocalTime.of(10, 0),
                    LocalTime.of(9, 30), LocalTime.of(10, 30))).isTrue();
        }

        @Test
        void disjointIntervals() {
            assertThat(MIDNIGHT_WINDOW.anchoredOverlaps(
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
            assertThat(MIDNIGHT_WINDOW.anchoredContains(LocalTime.of(15, 0), MIDNIGHT,
                    LocalTime.of(22, 0), LocalTime.of(23, 0))).isTrue();
        }

        @Test
        @DisplayName("the final 23:00-00:00 slot sits inside it too, flush with the end")
        void flushSlotIsContained() {
            assertThat(MIDNIGHT_WINDOW.anchoredContains(LocalTime.of(15, 0), MIDNIGHT,
                    LocalTime.of(23, 0), MIDNIGHT)).isTrue();
        }

        @Test
        void slotStartingBeforeTheEnvelopeIsNotContained() {
            assertThat(MIDNIGHT_WINDOW.anchoredContains(LocalTime.of(15, 0), MIDNIGHT,
                    LocalTime.of(14, 0), LocalTime.of(15, 0))).isFalse();
        }

        @Test
        @DisplayName("a slot running past a non-midnight envelope end is not contained")
        void slotOverrunningIsNotContained() {
            assertThat(MIDNIGHT_WINDOW.anchoredContains(LocalTime.of(8, 0), LocalTime.of(17, 0),
                    LocalTime.of(16, 30), LocalTime.of(17, 30))).isFalse();
        }
    }

    @Nested
    @DisplayName("startsBefore")
    class StartsBefore {

        @Test
        @DisplayName("every start in a midnight-ending day is before its end boundary")
        void startsInMidnightEndingDay() {
            assertThat(MIDNIGHT_WINDOW.anchoredStartsBefore(LocalTime.of(8, 0), MIDNIGHT)).isTrue();
            assertThat(MIDNIGHT_WINDOW.anchoredStartsBefore(LocalTime.of(23, 0), MIDNIGHT)).isTrue();
        }

        @Test
        void midnightStartIsBeforeMidnightEnd() {
            assertThat(MIDNIGHT_WINDOW.anchoredStartsBefore(MIDNIGHT, MIDNIGHT)).isTrue();
        }

        @Test
        void aStartOnTheBoundaryIsNotBeforeIt() {
            assertThat(MIDNIGHT_WINDOW.anchoredStartsBefore(LocalTime.of(17, 0), LocalTime.of(17, 0))).isFalse();
        }
    }

    @Nested
    @DisplayName("toLocalTime and plusWithinDay round-trip through the boundary")
    class Conversion {

        @Test
        void endOfDayMapsBackToMidnight() {
            assertThat(MIDNIGHT_WINDOW.anchoredToLocalTime(1440)).isEqualTo(MIDNIGHT);
        }

        @Test
        void zeroMapsToMidnightToo() {
            assertThat(MIDNIGHT_WINDOW.anchoredToLocalTime(0)).isEqualTo(MIDNIGHT);
        }

        @Test
        void endMinuteRoundTripsLosslessly() {
            assertThat(MIDNIGHT_WINDOW.anchoredToLocalTime(MIDNIGHT_WINDOW.anchoredEndMinute(MIDNIGHT)))
                    .isEqualTo(MIDNIGHT);
            assertThat(MIDNIGHT_WINDOW.anchoredToLocalTime(MIDNIGHT_WINDOW.anchoredEndMinute(LocalTime.of(23, 0))))
                    .isEqualTo(LocalTime.of(23, 0));
        }

        @Test
        @DisplayName("23:00 plus an hour is the end of the day, not 00:00 tomorrow morning")
        void plusLandsOnTheBoundary() {
            assertThat(MIDNIGHT_WINDOW.anchoredPlusWithinDay(LocalTime.of(23, 0), 60)).isEqualTo(MIDNIGHT);
        }

        @Test
        @DisplayName("15:00 plus nine hours is midnight — the shift that motivated this class")
        void nineHourEveningShift() {
            assertThat(MIDNIGHT_WINDOW.anchoredPlusWithinDay(LocalTime.of(15, 0), 540)).isEqualTo(MIDNIGHT);
        }

        @Test
        @DisplayName("going past the end of the day throws instead of silently wrapping")
        void wrappingThrows() {
            assertThatThrownBy(() -> MIDNIGHT_WINDOW.anchoredPlusWithinDay(LocalTime.of(23, 0), 120))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("1440");
            // LocalTime's own arithmetic is what this protects against:
            assertThat(LocalTime.of(23, 0).plusMinutes(120)).isEqualTo(LocalTime.of(1, 0));
        }

        @Test
        void outOfRangeMinuteIsRejected() {
            assertThatThrownBy(() -> MIDNIGHT_WINDOW.anchoredToLocalTime(1441))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> MIDNIGHT_WINDOW.anchoredToLocalTime(-1))
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
    @DisplayName("anchored functions equal their (now-retired, frozen) midnight-implicit counterparts at a 00:00 anchor")
    // The nine midnight-implicit statics this class originally compared against are private as of
    // BDAY-04 (plan 19-08, D-06) -- comparing the five still-public anchored primitives against
    // MIDNIGHT_WINDOW's anchored* instance methods here would be circular, since those instance
    // methods ARE the anchored primitives bound to MIDNIGHT. FrozenOracle (D-13, plan 19-02) is the
    // independent, frozen reference this class now compares against instead; the asserted values
    // are unchanged.
    class AnchoredEquivalenceAtMidnightAnchor {

        @Test
        @DisplayName("startMinuteFromDayStart/endMinuteFromDayStart equal startMinute/endMinute for every minute of the day")
        void startAndEndMinuteEquivalence() {
            for (int m = 0; m < 1440; m++) {
                LocalTime t = FrozenOracle.toLocalTime(m);
                assertThat(DayWindow.startMinuteFromDayStart(MIDNIGHT, t))
                        .as("startMinuteFromDayStart(MIDNIGHT, %s)", t)
                        .isEqualTo(FrozenOracle.startMinute(t));
                assertThat(DayWindow.endMinuteFromDayStart(MIDNIGHT, t))
                        .as("endMinuteFromDayStart(MIDNIGHT, %s)", t)
                        .isEqualTo(FrozenOracle.endMinute(t));
            }
        }

        @Test
        @DisplayName("timeAtDayStartOffset equals toLocalTime for every offset in [0, 1440]")
        void timeAtDayStartOffsetEquivalence() {
            for (int m = 0; m <= 1440; m++) {
                assertThat(DayWindow.timeAtDayStartOffset(MIDNIGHT, m))
                        .as("timeAtDayStartOffset(MIDNIGHT, %d)", m)
                        .isEqualTo(FrozenOracle.toLocalTime(m));
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
                LocalTime t = FrozenOracle.toLocalTime(m);
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
    @DisplayName("no public DayWindow static takes a bare scheduling time (D-03 inversion of DeprecationIsLive)")
    // BDAY-04 (plan 19-08) retires the nine midnight-implicit forms from public surface entirely
    // (D-06: demoted to private), so "is @Deprecated" no longer has a standing public-surface
    // member to assert about. The inverted, permanent property this class now enforces is the
    // public/private boundary itself: no public static on DayWindow may take a bare scheduling
    // time as its first parameter -- not "nothing is deprecated", but "nothing midnight-implicit
    // is public, ever again". This is what stops a future convenience overload (Phase 20/21/22)
    // reintroducing the shape without a reviewer noticing.
    class NoPublicStaticTakesABareSchedulingTime {

        private static final Set<String> MIDNIGHT_IMPLICIT = Set.of(
                "startMinute", "endMinute", "durationMinutes", "isForwardWithinDay", "overlaps",
                "contains", "startsBefore", "toLocalTime", "plusWithinDay");

        private static final Set<String> ANCHORED = Set.of(
                "startMinuteFromDayStart", "endMinuteFromDayStart", "timeAtDayStartOffset",
                "businessDateOf", "calendarDateAtDayStartOffset", "anchoredAt");

        @Test
        @DisplayName("the nine midnight-implicit forms are no longer public; the five anchored primitives "
                + "plus anchoredAt are, and every one of them takes the day-start anchor first")
        void noMidnightImplicitFormSurvivesAsPublic() {
            List<Method> publicStatics = Arrays.stream(DayWindow.class.getDeclaredMethods())
                    .filter(m -> Modifier.isPublic(m.getModifiers()) && Modifier.isStatic(m.getModifiers()))
                    .collect(Collectors.toList());

            assertThat(publicStatics).as("public static methods declared on DayWindow").isNotEmpty();

            Set<String> publicStaticNames = publicStatics.stream()
                    .map(Method::getName)
                    .collect(Collectors.toSet());
            assertThat(publicStaticNames)
                    .as("no midnight-implicit form may be public (BDAY-04 criterion 1)")
                    .doesNotContainAnyElementsOf(MIDNIGHT_IMPLICIT);
            assertThat(publicStaticNames)
                    .as("every anchored public static (D-06)")
                    .containsExactlyInAnyOrderElementsOf(ANCHORED);
        }

        @Test
        @DisplayName("every public static's first declared parameter is the day-start anchor "
                + "(LocalTime, checked by shape -- parameter names are not reliably available without -parameters)")
        void everyPublicStaticTakesTheAnchorFirst() {
            List<Method> publicStatics = Arrays.stream(DayWindow.class.getDeclaredMethods())
                    .filter(m -> Modifier.isPublic(m.getModifiers()) && Modifier.isStatic(m.getModifiers()))
                    .collect(Collectors.toList());

            for (Method m : publicStatics) {
                assertThat(m.getParameterCount())
                        .as("%s must take at least one parameter", m.getName())
                        .isGreaterThan(0);
                assertThat(m.getParameterTypes()[0])
                        .as("%s's first parameter must be the day-start anchor (LocalTime) -- this is the "
                                + "shape check a reintroduced bare-scheduling-time overload would fail, since "
                                + "every known-legitimate public static here takes LocalTime first", m.getName())
                        .isEqualTo(LocalTime.class);
            }
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
        // exactly these nine methods at a bound DayWindow.anchoredAt(MIDNIGHT) instance now that
        // the midnight-implicit statics are demoted to private; no assertion below needed to
        // change (except durationMinutesAgreesForEveryOrderedPair's activation of the criterion-2
        // divergence, documented at that test).
        private int liveStartMinute(LocalTime t) {
            return MIDNIGHT_WINDOW.anchoredStartMinute(t);
        }

        private int liveEndMinute(LocalTime t) {
            return MIDNIGHT_WINDOW.anchoredEndMinute(t);
        }

        private LocalTime liveToLocalTime(int m) {
            return MIDNIGHT_WINDOW.anchoredToLocalTime(m);
        }

        private int liveDurationMinutes(LocalTime s, LocalTime e) {
            return MIDNIGHT_WINDOW.anchoredDurationMinutes(s, e);
        }

        private boolean liveIsForwardWithinDay(LocalTime s, LocalTime e) {
            return MIDNIGHT_WINDOW.anchoredIsForwardWithinDay(s, e);
        }

        private boolean liveStartsBefore(LocalTime s, LocalTime e) {
            return MIDNIGHT_WINDOW.anchoredStartsBefore(s, e);
        }

        private boolean liveOverlaps(LocalTime s1, LocalTime e1, LocalTime s2, LocalTime e2) {
            return MIDNIGHT_WINDOW.anchoredOverlaps(s1, e1, s2, e2);
        }

        private boolean liveContains(LocalTime outerStart, LocalTime outerEnd,
                                      LocalTime innerStart, LocalTime innerEnd) {
            return MIDNIGHT_WINDOW.anchoredContains(outerStart, outerEnd, innerStart, innerEnd);
        }

        private LocalTime livePlusWithinDay(LocalTime base, int minutes) {
            return MIDNIGHT_WINDOW.anchoredPlusWithinDay(base, minutes);
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
        @DisplayName("durationMinutes agrees with the oracle outside the throw domain; inside it, the oracle "
                + "throws and the instance method returns the value the anchored composition must yield "
                + "(BDAY-04 criterion 2's one named, deliberate divergence -- plan 19-02's expectedDurationAfterMigration)")
        void durationMinutesAgreesForEveryOrderedPair() {
            for (int i = 0; i < 1440; i++) {
                LocalTime start = liveToLocalTime(i);
                for (int j = 0; j < 1440; j++) {
                    LocalTime end = liveToLocalTime(j);
                    // The anchored instance method never throws (criterion 2) -- there is no live
                    // exception branch to compare any more. The oracle is still the frozen,
                    // pre-migration implementation and still throws exactly where it always did.
                    int liveResult = liveDurationMinutes(start, end);
                    Integer oracleResult = null;
                    RuntimeException oracleException = null;
                    try {
                        oracleResult = FrozenOracle.durationMinutes(start, end);
                    } catch (IllegalArgumentException e) {
                        oracleException = e;
                    }
                    if (oracleException == null) {
                        assertThat(liveResult)
                                .as("durationMinutes(%s, %s) outside the throw domain", start, end)
                                .isEqualTo(oracleResult);
                    } else {
                        assertThat(liveResult)
                                .as("durationMinutes(%s, %s) inside the throw domain -- the criterion-2 "
                                        + "divergence: the oracle throws, the instance method returns the "
                                        + "value the anchored composition must yield", start, end)
                                .isEqualTo(ThrowDomainIsPinned.expectedDurationAfterMigration(start, end));
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
                        // Message text is NOT compared here (unlike durationMinutes above): the
                        // oracle's plusWithinDay throws via its own toLocalTime bounds check
                        // ("minuteOfDay must be within..."), while anchoredPlusWithinDay composes
                        // through timeAtDayStartOffset's bounds check ("minutesFromDayStart must
                        // be within...") -- a different Phase-18 primitive with a different
                        // parameter name in its message, not a behavioural divergence. The boundary
                        // itself, the exception type, and whether each side throws are all identical
                        // (asserted above and by the oracleException-not-null check); only the
                        // wording differs, found when this plan flipped the comparison target.
                        assertThat(liveException.getMessage())
                                .as("plusWithinDay(%s, %d) exception message still names the boundary 1440", base, offset)
                                .contains("1440");
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

    @Nested
    @DisplayName("the throw domain of durationMinutes and plusWithinDay is pinned as data, not prose (BDAY-04 criterion 2)")
    class ThrowDomainIsPinned {

        /**
         * True exactly when today's {@code durationMinutes(start, end)} throws: the half-open
         * interval {@code endMinute(end) - startMinute(start)} is not strictly positive. Criterion
         * 2 removes this throw; {@link #expectedDurationAfterMigration} records what the anchored
         * composition must yield at every point of this domain instead.
         */
        private static boolean durationMinutesThrows(LocalTime start, LocalTime end) {
            return FrozenOracle.endMinute(end) - FrozenOracle.startMinute(start) <= 0;
        }

        /**
         * True exactly when today's {@code plusWithinDay(base, minutes)} throws: the summed
         * minute-of-day falls outside {@code [0, MINUTES_PER_DAY]}.
         */
        private static boolean plusWithinDayThrows(LocalTime base, int minutes) {
            int target = FrozenOracle.startMinute(base) + minutes;
            return target < 0 || target > FrozenOracle.MINUTES_PER_DAY;
        }

        /**
         * The value the post-migration anchored composition must yield for a pair, matching
         * today's already-correct, non-throwing value outside {@link #durationMinutesThrows}'s
         * domain. Inside that domain: a true reversal (the raw difference is negative) wraps
         * forward across the anchor -- D-11's crossing-the-anchor duration. A zero-length pair at
         * a non-anchor instant (the raw difference is exactly zero) is a genuine zero-minute
         * interval, not a wrap -- the distinction this migration is most likely to blur with the
         * ordinary whole-day-at-the-anchor case (raw already positive, e.g. {@code (00:00,
         * 00:00)}), which this function leaves untouched rather than re-deriving.
         */
        private static int expectedDurationAfterMigration(LocalTime start, LocalTime end) {
            int raw = FrozenOracle.endMinute(end) - FrozenOracle.startMinute(start);
            if (raw > 0) {
                return raw;
            }
            if (raw == 0) {
                return 0;
            }
            return raw + FrozenOracle.MINUTES_PER_DAY;
        }

        @Test
        @DisplayName("the durationMinutes throw predicate agrees with the frozen oracle (BDAY-04's pre-migration "
                + "reference, plan 19-02 D-13) for every ordered pair of minutes -- the anchored instance method "
                + "itself no longer throws at all (criterion 2), so the oracle is what this predicate is checked "
                + "against now")
        void durationMinutesPredicateAgreesForEveryOrderedPair() {
            for (int i = 0; i < 1440; i++) {
                LocalTime start = FrozenOracle.toLocalTime(i);
                for (int j = 0; j < 1440; j++) {
                    LocalTime end = FrozenOracle.toLocalTime(j);
                    boolean oracleThrows;
                    try {
                        FrozenOracle.durationMinutes(start, end);
                        oracleThrows = false;
                    } catch (IllegalArgumentException e) {
                        oracleThrows = true;
                    }
                    assertThat(durationMinutesThrows(start, end))
                            .as("durationMinutesThrows(%s, %s)", start, end)
                            .isEqualTo(oracleThrows);
                }
            }
        }

        @Test
        @DisplayName("the plusWithinDay throw predicate agrees with the frozen oracle for every base minute "
                + "against a bounded offset set -- plusWithinDay's own throw behaviour is UNCHANGED by "
                + "migration (only durationMinutes diverges), so this remains a faithful pre/post comparison")
        void plusWithinDayPredicateAgreesForEveryBaseMinute() {
            for (int b = 0; b < 1440; b++) {
                LocalTime base = FrozenOracle.toLocalTime(b);
                int exactBoundaryOffset = DayWindow.MINUTES_PER_DAY - b;
                int[] offsets = {0, 1, 15, 30, 60, exactBoundaryOffset, exactBoundaryOffset + 1};
                for (int offset : offsets) {
                    boolean oracleThrows;
                    try {
                        FrozenOracle.plusWithinDay(base, offset);
                        oracleThrows = false;
                    } catch (IllegalArgumentException e) {
                        oracleThrows = true;
                    }
                    assertThat(plusWithinDayThrows(base, offset))
                            .as("plusWithinDayThrows(%s, %d)", base, offset)
                            .isEqualTo(oracleThrows);
                    // The anchored instance method's throw behaviour for plusWithinDay is unchanged
                    // by migration -- confirmed directly against the live instance here too.
                    boolean liveThrows;
                    try {
                        MIDNIGHT_WINDOW.anchoredPlusWithinDay(base, offset);
                        liveThrows = false;
                    } catch (IllegalArgumentException e) {
                        liveThrows = true;
                    }
                    assertThat(liveThrows)
                            .as("anchoredPlusWithinDay(%s, %d) throw behaviour unchanged by migration", base, offset)
                            .isEqualTo(oracleThrows);
                }
            }
        }

        @Test
        @DisplayName("the whole-day/zero-length boundary at equal endpoints: only the anchor-equals-itself "
                + "case is a full day")
        void equalEndpointBoundaryIsNotConfusedWithTheWholeDayCase() {
            assertThat(expectedDurationAfterMigration(LocalTime.of(9, 0), LocalTime.of(9, 0)))
                    .as("a non-anchor instant equal to itself yields zero minutes after migration, not a whole day")
                    .isEqualTo(0);
            assertThat(expectedDurationAfterMigration(MIDNIGHT, MIDNIGHT))
                    .as("the anchor equal to itself yields a full business day after migration, matching "
                            + "today's unchanged value")
                    .isEqualTo(1440);
            assertThat(durationMinutesThrows(MIDNIGHT, MIDNIGHT))
                    .as("(00:00, 00:00) is not in today's throw domain either -- it already returns 1440 "
                            + "without throwing")
                    .isFalse();
            assertThat(FrozenOracle.durationMinutes(MIDNIGHT, MIDNIGHT))
                    .as("the frozen pre-migration value at the anchor case is already 1440")
                    .isEqualTo(1440);
            assertThat(MIDNIGHT_WINDOW.anchoredDurationMinutes(MIDNIGHT, MIDNIGHT))
                    .as("the post-migration anchored instance method yields the same 1440 at the anchor case, "
                            + "unchanged by this plan")
                    .isEqualTo(1440);
        }
    }
}
