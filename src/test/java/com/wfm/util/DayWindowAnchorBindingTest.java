package com.wfm.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers the {@code <behavior>} items in Phase 19 plan 03 (the tracer) that concern {@link
 * DayWindow} alone: the null anchor, the midnight-anchor agreement with the still-public
 * midnight-implicit statics, the 21:00 crossing duration, the two equal-endpoint cases, the
 * removed throw, and {@code overlaps} symmetry over a structured sweep of boundary-adjacent
 * quadruples.
 *
 * <p>Deliberately does NOT touch {@code DayWindowTest.java} — plan 19-02 pinned its frozen oracle
 * against the still-callable statics in wave 1, and plan 19-08 owns the flip onto the bound
 * instance.
 *
 * <p>The nine instance methods here are named with an {@code anchored} prefix rather than the
 * bare midnight-implicit names — see {@code DayWindow}'s class javadoc "Naming note" and
 * {@code 19-03-SUMMARY.md} for why: a public static and a public instance method cannot share an
 * identical name and parameter list in the same class, and the nine midnight-implicit statics
 * must stay public and unchanged through wave 19-07.
 */
class DayWindowAnchorBindingTest {

    private static final LocalTime MIDNIGHT = LocalTime.MIDNIGHT;
    private static final LocalTime ANCHOR_2100 = LocalTime.of(21, 0);

    @Nested
    @DisplayName("anchoredAt null-checks the anchor")
    class AnchoredAtNullCheck {

        @Test
        @DisplayName("anchoredAt(null) throws rather than binding an implicit midnight")
        void anchoredAtNullThrows() {
            assertThatThrownBy(() -> DayWindow.anchoredAt(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("dayStart");
        }
    }

    @Nested
    @DisplayName("at a 00:00 anchor, every instance method agrees with its static counterpart")
    class MidnightAnchorAgreement {

        private final DayWindow window = DayWindow.anchoredAt(MIDNIGHT);

        @Test
        void startAndEndMinuteAgree() {
            for (int m = 0; m < 1440; m++) {
                LocalTime t = DayWindow.toLocalTime(m);
                assertThat(window.anchoredStartMinute(t)).as("startMinute(%s)", t)
                        .isEqualTo(DayWindow.startMinute(t));
                assertThat(window.anchoredEndMinute(t)).as("endMinute(%s)", t)
                        .isEqualTo(DayWindow.endMinute(t));
            }
        }

        @Test
        void toLocalTimeAgrees() {
            for (int m = 0; m <= 1440; m++) {
                assertThat(window.anchoredToLocalTime(m)).as("toLocalTime(%d)", m)
                        .isEqualTo(DayWindow.toLocalTime(m));
            }
        }

        @Test
        void durationMinutesAgreesOutsideTheThrowDomain() {
            // Representative forward intervals -- today's durationMinutes does not throw on these.
            assertThat(window.anchoredDurationMinutes(LocalTime.of(8, 0), LocalTime.of(17, 0)))
                    .isEqualTo(DayWindow.durationMinutes(LocalTime.of(8, 0), LocalTime.of(17, 0)));
            assertThat(window.anchoredDurationMinutes(LocalTime.of(15, 0), MIDNIGHT))
                    .isEqualTo(DayWindow.durationMinutes(LocalTime.of(15, 0), MIDNIGHT));
            assertThat(window.anchoredDurationMinutes(LocalTime.of(23, 0), MIDNIGHT))
                    .isEqualTo(DayWindow.durationMinutes(LocalTime.of(23, 0), MIDNIGHT));
            assertThat(window.anchoredDurationMinutes(MIDNIGHT, MIDNIGHT))
                    .isEqualTo(DayWindow.durationMinutes(MIDNIGHT, MIDNIGHT));
        }

        @Test
        void isForwardWithinDayAgrees() {
            assertThat(window.anchoredIsForwardWithinDay(LocalTime.of(15, 0), MIDNIGHT))
                    .isEqualTo(DayWindow.isForwardWithinDay(LocalTime.of(15, 0), MIDNIGHT));
            assertThat(window.anchoredIsForwardWithinDay(LocalTime.of(22, 0), LocalTime.of(6, 0)))
                    .isEqualTo(DayWindow.isForwardWithinDay(LocalTime.of(22, 0), LocalTime.of(6, 0)));
            assertThat(window.anchoredIsForwardWithinDay(LocalTime.of(9, 0), LocalTime.of(9, 0)))
                    .isEqualTo(DayWindow.isForwardWithinDay(LocalTime.of(9, 0), LocalTime.of(9, 0)));
            assertThat(window.anchoredIsForwardWithinDay(null, MIDNIGHT))
                    .isEqualTo(DayWindow.isForwardWithinDay(null, MIDNIGHT));
            assertThat(window.anchoredIsForwardWithinDay(LocalTime.of(9, 0), null))
                    .isEqualTo(DayWindow.isForwardWithinDay(LocalTime.of(9, 0), null));
        }

        @Test
        void startsBeforeAgrees() {
            assertThat(window.anchoredStartsBefore(LocalTime.of(8, 0), MIDNIGHT))
                    .isEqualTo(DayWindow.startsBefore(LocalTime.of(8, 0), MIDNIGHT));
            assertThat(window.anchoredStartsBefore(LocalTime.of(17, 0), LocalTime.of(17, 0)))
                    .isEqualTo(DayWindow.startsBefore(LocalTime.of(17, 0), LocalTime.of(17, 0)));
        }

        @Test
        void overlapsAgrees() {
            assertThat(window.anchoredOverlaps(
                    LocalTime.of(23, 0), MIDNIGHT, LocalTime.of(22, 30), MIDNIGHT))
                    .isEqualTo(DayWindow.overlaps(
                            LocalTime.of(23, 0), MIDNIGHT, LocalTime.of(22, 30), MIDNIGHT));
            assertThat(window.anchoredOverlaps(
                    LocalTime.of(9, 0), LocalTime.of(10, 0), LocalTime.of(14, 0), LocalTime.of(15, 0)))
                    .isEqualTo(DayWindow.overlaps(
                            LocalTime.of(9, 0), LocalTime.of(10, 0), LocalTime.of(14, 0), LocalTime.of(15, 0)));
        }

        @Test
        void containsAgrees() {
            assertThat(window.anchoredContains(LocalTime.of(15, 0), MIDNIGHT,
                    LocalTime.of(23, 0), MIDNIGHT))
                    .isEqualTo(DayWindow.contains(LocalTime.of(15, 0), MIDNIGHT,
                            LocalTime.of(23, 0), MIDNIGHT));
            assertThat(window.anchoredContains(LocalTime.of(8, 0), LocalTime.of(17, 0),
                    LocalTime.of(16, 30), LocalTime.of(17, 30)))
                    .isEqualTo(DayWindow.contains(LocalTime.of(8, 0), LocalTime.of(17, 0),
                            LocalTime.of(16, 30), LocalTime.of(17, 30)));
        }

        @Test
        void plusWithinDayAgrees() {
            assertThat(window.anchoredPlusWithinDay(LocalTime.of(23, 0), 60))
                    .isEqualTo(DayWindow.plusWithinDay(LocalTime.of(23, 0), 60));
            assertThat(window.anchoredPlusWithinDay(LocalTime.of(15, 0), 540))
                    .isEqualTo(DayWindow.plusWithinDay(LocalTime.of(15, 0), 540));
            assertThatThrownBy(() -> window.anchoredPlusWithinDay(LocalTime.of(23, 0), 120))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> DayWindow.plusWithinDay(LocalTime.of(23, 0), 120))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("at a 21:00 anchor, a shift crossing the anchor no longer throws")
    class AnchoredBehaviorAt2100 {

        private final DayWindow window = DayWindow.anchoredAt(ANCHOR_2100);

        @Test
        @DisplayName("durationMinutes(22:00, 02:00) is 240 -- a positive duration for an interval "
                + "that crosses the anchor's calendar midnight but not the anchor itself")
        void crossingCalendarMidnightButNotTheAnchor() {
            assertThat(window.anchoredDurationMinutes(LocalTime.of(22, 0), LocalTime.of(2, 0)))
                    .isEqualTo(240);
        }

        @Test
        @DisplayName("criterion 2: an interval that crosses the anchor returns a value rather than throwing")
        void crossingTheAnchorReturnsAValueRatherThanThrowing() {
            // At a 00:00 anchor this exact pair (15:00 -> 14:00) crosses the anchor and would have
            // thrown under the deprecated static; the anchored instance method must not throw.
            DayWindow midnightWindow = DayWindow.anchoredAt(MIDNIGHT);
            int result = midnightWindow.anchoredDurationMinutes(LocalTime.of(15, 0), LocalTime.of(14, 0));
            assertThat(result).isGreaterThan(0);
            assertThatThrownBy(() -> DayWindow.durationMinutes(LocalTime.of(15, 0), LocalTime.of(14, 0)))
                    .as("the deprecated static form still throws -- unchanged by this plan")
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("the two equal-endpoint cases at a 00:00 anchor")
    class EqualEndpointCases {

        private final DayWindow window = DayWindow.anchoredAt(MIDNIGHT);

        @Test
        @DisplayName("durationMinutes(09:00, 09:00) is 0 -- a non-anchor instant equal to itself")
        void nonAnchorEqualEndpointIsZero() {
            assertThat(window.anchoredDurationMinutes(LocalTime.of(9, 0), LocalTime.of(9, 0)))
                    .isEqualTo(0);
        }

        @Test
        @DisplayName("durationMinutes(00:00, 00:00) is 1440 -- the anchor equal to itself is a full "
                + "business day, a time equal to the anchor in an end position is the end of the day")
        void anchorEqualEndpointIsAWholeDay() {
            assertThat(window.anchoredDurationMinutes(MIDNIGHT, MIDNIGHT)).isEqualTo(1440);
        }
    }

    @Nested
    @DisplayName("overlaps is symmetric, including at the anchor, across anchors and boundary-adjacent quadruples")
    class OverlapsSymmetry {

        private final int[] candidates = {0, 1, 2, 359, 360, 361, 719, 720, 721, 1079, 1080, 1081, 1438, 1439, 1440};
        private final LocalTime[] anchors = {MIDNIGHT, ANCHOR_2100, LocalTime.of(6, 15)};

        @Test
        @DisplayName("overlaps(a, b, c, d) == overlaps(c, d, a, b) for every quadruple tested")
        void overlapsIsSymmetricOverBoundaryAdjacentQuadruples() {
            for (LocalTime anchor : anchors) {
                DayWindow window = DayWindow.anchoredAt(anchor);
                for (int a : candidates) {
                    LocalTime s1 = window.anchoredToLocalTime(a);
                    for (int b : candidates) {
                        LocalTime e1 = window.anchoredToLocalTime(b);
                        for (int c : candidates) {
                            LocalTime s2 = window.anchoredToLocalTime(c);
                            for (int d : candidates) {
                                LocalTime e2 = window.anchoredToLocalTime(d);
                                assertThat(window.anchoredOverlaps(s1, e1, s2, e2))
                                        .as("anchor=%s overlaps(%s,%s,%s,%s) vs overlaps(%s,%s,%s,%s)",
                                                anchor, s1, e1, s2, e2, s2, e2, s1, e1)
                                        .isEqualTo(window.anchoredOverlaps(s2, e2, s1, e1));
                            }
                        }
                    }
                }
            }
        }
    }
}
