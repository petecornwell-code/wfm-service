package com.wfm.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers the {@code <behavior>} items in Phase 19 plan 03 (the tracer) that concern {@link
 * DayWindow} alone: the null anchor, the midnight-anchor agreement with the former
 * midnight-implicit statics (pinned as literal expected values below as of plan 19-08, since the
 * statics those values were originally asserted against are now private — D-06), the 21:00
 * crossing duration, the two equal-endpoint cases, the removed throw, and {@code overlaps}
 * symmetry over a structured sweep of boundary-adjacent quadruples.
 *
 * <p>Deliberately does NOT touch {@code DayWindowTest.java} — plan 19-02 pinned its frozen oracle
 * against the still-callable statics in wave 1, and plan 19-08 owns the flip onto the bound
 * instance there.
 *
 * <p>The nine instance methods here are named with an {@code anchored} prefix rather than the
 * bare midnight-implicit names — see {@code DayWindow}'s class javadoc "Naming note" and
 * {@code 19-03-SUMMARY.md} for why: a public static and a public instance method cannot share an
 * identical name and parameter list in the same class, and through wave 19-07 the nine
 * midnight-implicit statics stayed public and unchanged for call sites not yet migrated. Plan
 * 19-08 (D-06) then demoted them to private.
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
    @DisplayName("at a 00:00 anchor, every instance method agrees with its (now-private, BDAY-04 D-06) static counterpart")
    // The nine midnight-implicit statics this class originally compared against directly were
    // demoted to private by plan 19-08 (D-06) and are no longer reachable from this class. Each
    // comparison below is rewritten against a literal frozen reference value -- the exact formula
    // the static implemented, reproduced locally -- rather than deleted; the asserted agreement is
    // unchanged.
    class MidnightAnchorAgreement {

        private final DayWindow window = DayWindow.anchoredAt(MIDNIGHT);

        /** The frozen {@code startMinute} formula, reproduced locally (D-06 made the static unreachable here). */
        private int frozenStartMinute(LocalTime t) {
            return t.getHour() * 60 + t.getMinute();
        }

        /** The frozen {@code endMinute} formula, reproduced locally (D-06 made the static unreachable here). */
        private int frozenEndMinute(LocalTime t) {
            return t.equals(MIDNIGHT) ? 1440 : t.getHour() * 60 + t.getMinute();
        }

        @Test
        void startAndEndMinuteAgree() {
            for (int m = 0; m < 1440; m++) {
                LocalTime t = window.anchoredToLocalTime(m);
                assertThat(window.anchoredStartMinute(t)).as("startMinute(%s)", t)
                        .isEqualTo(frozenStartMinute(t));
                assertThat(window.anchoredEndMinute(t)).as("endMinute(%s)", t)
                        .isEqualTo(frozenEndMinute(t));
            }
        }

        @Test
        void toLocalTimeAgrees() {
            for (int m = 0; m <= 1440; m++) {
                LocalTime expected = (m == 1440) ? MIDNIGHT : LocalTime.of(m / 60, m % 60);
                assertThat(window.anchoredToLocalTime(m)).as("toLocalTime(%d)", m)
                        .isEqualTo(expected);
            }
        }

        @Test
        void durationMinutesAgreesOutsideTheThrowDomain() {
            // Representative forward intervals -- today's durationMinutes did not throw on these,
            // and the anchored instance method still returns the identical value.
            assertThat(window.anchoredDurationMinutes(LocalTime.of(8, 0), LocalTime.of(17, 0)))
                    .isEqualTo(540);
            assertThat(window.anchoredDurationMinutes(LocalTime.of(15, 0), MIDNIGHT))
                    .isEqualTo(540);
            assertThat(window.anchoredDurationMinutes(LocalTime.of(23, 0), MIDNIGHT))
                    .isEqualTo(60);
            assertThat(window.anchoredDurationMinutes(MIDNIGHT, MIDNIGHT))
                    .isEqualTo(1440);
        }

        @Test
        void isForwardWithinDayAgrees() {
            assertThat(window.anchoredIsForwardWithinDay(LocalTime.of(15, 0), MIDNIGHT)).isTrue();
            assertThat(window.anchoredIsForwardWithinDay(LocalTime.of(22, 0), LocalTime.of(6, 0))).isFalse();
            assertThat(window.anchoredIsForwardWithinDay(LocalTime.of(9, 0), LocalTime.of(9, 0))).isFalse();
            assertThat(window.anchoredIsForwardWithinDay(null, MIDNIGHT)).isFalse();
            assertThat(window.anchoredIsForwardWithinDay(LocalTime.of(9, 0), null)).isFalse();
        }

        @Test
        void startsBeforeAgrees() {
            assertThat(window.anchoredStartsBefore(LocalTime.of(8, 0), MIDNIGHT)).isTrue();
            assertThat(window.anchoredStartsBefore(LocalTime.of(17, 0), LocalTime.of(17, 0))).isFalse();
        }

        @Test
        void overlapsAgrees() {
            assertThat(window.anchoredOverlaps(
                    LocalTime.of(23, 0), MIDNIGHT, LocalTime.of(22, 30), MIDNIGHT)).isTrue();
            assertThat(window.anchoredOverlaps(
                    LocalTime.of(9, 0), LocalTime.of(10, 0), LocalTime.of(14, 0), LocalTime.of(15, 0))).isFalse();
        }

        @Test
        void containsAgrees() {
            assertThat(window.anchoredContains(LocalTime.of(15, 0), MIDNIGHT,
                    LocalTime.of(23, 0), MIDNIGHT)).isTrue();
            assertThat(window.anchoredContains(LocalTime.of(8, 0), LocalTime.of(17, 0),
                    LocalTime.of(16, 30), LocalTime.of(17, 30))).isFalse();
        }

        @Test
        void plusWithinDayAgrees() {
            assertThat(window.anchoredPlusWithinDay(LocalTime.of(23, 0), 60)).isEqualTo(MIDNIGHT);
            assertThat(window.anchoredPlusWithinDay(LocalTime.of(15, 0), 540)).isEqualTo(MIDNIGHT);
            assertThatThrownBy(() -> window.anchoredPlusWithinDay(LocalTime.of(23, 0), 120))
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
            // thrown under the former midnight-implicit static (now private, D-06, plan 19-08); the
            // anchored instance method must not throw.
            DayWindow midnightWindow = DayWindow.anchoredAt(MIDNIGHT);
            int result = midnightWindow.anchoredDurationMinutes(LocalTime.of(15, 0), LocalTime.of(14, 0));
            assertThat(result).isGreaterThan(0);
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
