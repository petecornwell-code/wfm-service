package com.wfm.service;

import com.wfm.model.ShiftBandPair;
import com.wfm.model.ShiftTemplate;
import com.wfm.model.ShiftTemplateBreakBand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The seams a desk whose day runs to midnight has to pass through, each exercised directly as the
 * pure predicate production code calls — timeslot survival, grid alignment, net hours, seat
 * legality inside an envelope, and shift-library coverage.
 *
 * <p>Every assertion here returned the WRONG answer before {@link com.wfm.util.DayWindow} existed,
 * because each one ultimately compared a {@link LocalTime} of {@code 00:00} — which is the
 * smallest value the type can hold — against a time later in the day. The failures were silent:
 * no exception, just an empty generation loop, a template that covered nothing, or a seat the
 * solver believed was out of bounds.
 *
 * <p>Deliberately no Spring context and no database: these are the shared static predicates, and
 * the behaviour under test is arithmetic, not wiring. The service-level counterparts live in
 * {@code ShiftTemplateServiceTest} (midnight section) and {@code TimeslotGeneratorServiceTest}.
 */
class MidnightWindowSeamTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 9, 21);
    private static final LocalTime OPEN = LocalTime.of(8, 0);
    private static final LocalTime CLOSE = LocalTime.MIDNIGHT;

    @Nested
    @DisplayName("timeslot survival across a regeneration (TimeslotGeneratorService.isDesired)")
    class TimeslotSurvival {

        private boolean desired(LocalTime slotStart, LocalTime slotEnd) {
            return TimeslotGeneratorService.isDesired(
                    MONDAY, slotStart, slotEnd, MONDAY, MONDAY, OPEN, CLOSE, 60);
        }

        @Test
        @DisplayName("the final 23:00-00:00 slot of a midnight-ending day is wanted")
        void finalSlotSurvives() {
            assertThat(desired(LocalTime.of(23, 0), LocalTime.MIDNIGHT)).isTrue();
        }

        @Test
        @DisplayName("so is every earlier slot — the old check discarded the whole day")
        void earlierSlotsSurvive() {
            assertThat(desired(LocalTime.of(8, 0), LocalTime.of(9, 0))).isTrue();
            assertThat(desired(LocalTime.of(15, 0), LocalTime.of(16, 0))).isTrue();
            assertThat(desired(LocalTime.of(22, 0), LocalTime.of(23, 0))).isTrue();
        }

        @Test
        @DisplayName("a slot before the window opens is still unwanted")
        void slotBeforeOpenIsNotWanted() {
            assertThat(desired(LocalTime.of(7, 0), LocalTime.of(8, 0))).isFalse();
        }

        @Test
        @DisplayName("a wrongly-sized slot is still unwanted")
        void wrongDurationIsNotWanted() {
            assertThat(desired(LocalTime.of(23, 0), LocalTime.of(23, 30))).isFalse();
        }
    }

    @Nested
    @DisplayName("grid alignment (ShiftTemplateService.isAligned)")
    class GridAlignment {

        @Test
        @DisplayName("a midnight end is aligned to an hourly grid opening at 08:00")
        void midnightAlignsToHourlyGrid() {
            assertThat(ShiftTemplateService.isAligned(OPEN, 60, LocalTime.MIDNIGHT)).isTrue();
        }

        @Test
        @DisplayName("and to a 15- and 30-minute grid — 1440 divides by all three")
        void midnightAlignsToFinerGrids() {
            assertThat(ShiftTemplateService.isAligned(OPEN, 30, LocalTime.MIDNIGHT)).isTrue();
            assertThat(ShiftTemplateService.isAligned(OPEN, 15, LocalTime.MIDNIGHT)).isTrue();
        }

        @Test
        @DisplayName("an off-grid time is still misaligned")
        void offGridStillRejected() {
            assertThat(ShiftTemplateService.isAligned(OPEN, 60, LocalTime.of(15, 30))).isFalse();
        }
    }

    @Nested
    @DisplayName("net hours (ShiftTemplate.getNetHours)")
    class NetHours {

        @Test
        @DisplayName("15:00-00:00 less a one-hour break is 8 hours, not minus sixteen")
        void midnightEndingShiftNetHours() {
            assertThat(template(LocalTime.of(15, 0), LocalTime.MIDNIGHT).getNetHours(60))
                    .isEqualByComparingTo(BigDecimal.valueOf(8.0));
        }

        @Test
        @DisplayName("a break-less midnight-ending envelope is its full nine hours")
        void noBreak() {
            assertThat(template(LocalTime.of(15, 0), LocalTime.MIDNIGHT).getNetHours(0))
                    .isEqualByComparingTo(BigDecimal.valueOf(9.0));
        }
    }

    @Nested
    @DisplayName("seat legality inside an envelope (ShiftBandPair.covers)")
    class SeatLegality {

        /** The 15:00-00:00 envelope with a one-hour break four hours in (19:00-20:00). */
        private boolean covers(LocalTime slotStart, LocalTime slotEnd) {
            return ShiftBandPair.covers(LocalTime.of(15, 0), LocalTime.MIDNIGHT, 240, 60,
                    slotStart, slotEnd);
        }

        @Test
        @DisplayName("a mid-envelope slot is seatable — the old check rejected all but the last")
        void midEnvelopeSlotIsSeatable() {
            assertThat(covers(LocalTime.of(16, 0), LocalTime.of(17, 0))).isTrue();
            assertThat(covers(LocalTime.of(22, 0), LocalTime.of(23, 0))).isTrue();
        }

        @Test
        @DisplayName("the final slot, flush with midnight, is seatable")
        void finalSlotIsSeatable() {
            assertThat(covers(LocalTime.of(23, 0), LocalTime.MIDNIGHT)).isTrue();
        }

        @Test
        @DisplayName("the break hour is not seatable")
        void breakIsNotSeatable() {
            assertThat(covers(LocalTime.of(19, 0), LocalTime.of(20, 0))).isFalse();
        }

        @Test
        @DisplayName("a slot outside the envelope is not seatable")
        void outsideEnvelopeIsNotSeatable() {
            assertThat(covers(LocalTime.of(14, 0), LocalTime.of(15, 0))).isFalse();
        }
    }

    @Nested
    @DisplayName("shift-library coverage (ShiftLibraryValidationService.covers)")
    class LibraryCoverage {

        private boolean covers(LocalTime windowStart, LocalTime windowEnd) {
            ShiftTemplate t = template(LocalTime.of(15, 0), LocalTime.MIDNIGHT);
            ShiftTemplateBreakBand band = new ShiftTemplateBreakBand();
            band.setOffsetMinutes(240);
            band.setDurationMinutes(60);
            return ShiftLibraryValidationService.covers(t, List.of(band),
                    new ShiftLibraryValidationService.Window(MONDAY, windowStart, windowEnd));
        }

        @Test
        @DisplayName("a midnight-ending template covers the last demand window of the day")
        void coversFinalWindow() {
            assertThat(covers(LocalTime.of(23, 0), LocalTime.MIDNIGHT)).isTrue();
        }

        @Test
        @DisplayName("and windows in the middle of its envelope — without this, no desk running "
                + "to midnight could ever be switched into SHIFT mode")
        void coversMidEnvelopeWindows() {
            assertThat(covers(LocalTime.of(16, 0), LocalTime.of(17, 0))).isTrue();
            assertThat(covers(LocalTime.of(22, 0), LocalTime.of(23, 0))).isTrue();
        }

        @Test
        @DisplayName("it does not cover its break hour")
        void doesNotCoverBreak() {
            assertThat(covers(LocalTime.of(19, 0), LocalTime.of(20, 0))).isFalse();
        }

        @Test
        @DisplayName("nor a window starting before it opens")
        void doesNotCoverEarlierWindow() {
            assertThat(covers(LocalTime.of(14, 0), LocalTime.of(15, 0))).isFalse();
        }
    }

    private static ShiftTemplate template(LocalTime start, LocalTime end) {
        ShiftTemplate t = new ShiftTemplate();
        t.setStartTime(start);
        t.setEndTime(end);
        t.setValidWeekdays(Set.of(DayOfWeek.values()));
        t.setEffectiveFrom(LocalDate.of(2026, 1, 1));
        return t;
    }
}
