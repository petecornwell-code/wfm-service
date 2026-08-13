package com.wfm.solver;

import com.wfm.model.AgentAssignment;
import com.wfm.model.AgentDayConfig;
import com.wfm.model.BreakAlignment;
import com.wfm.model.Timeslot;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure-function coverage of {@link ShiftWindowFinder} geometry: full window
 * enumeration, rounding-mode parity against {@link ScheduleConstraintProvider},
 * and break-legality filtering at generation time. No solver bootstrap of any
 * kind — every fixture is a hand-built {@link AgentAssignment}/{@link
 * Timeslot}/{@link AgentDayConfig} triple, following the constants-block
 * style of {@code BreakAwareConstructionTest}.
 */
class ShiftWindowFinderTest {

    private static final UUID AGENT_ID = UUID.randomUUID();
    private static final LocalDate DAY = LocalDate.of(2026, 3, 16);

    // ------------------------------------------------------------------
    //  Rounding parity (RESEARCH.md Pitfall 3)
    // ------------------------------------------------------------------

    @Test
    void requiredWorkSlots_usesHalfUpRounding() {
        AgentDayConfig dayConfig = dayConfig(new BigDecimal("7.25"), 60, 60,
                new BigDecimal("4.00"), new BigDecimal("1.00"), BreakAlignment.ON_HOUR);

        assertThat(ShiftWindowFinder.requiredWorkSlots(dayConfig)).isEqualTo(7);
    }

    @Test
    void breakThresholdSlots_usesCeilingRounding() {
        AgentDayConfig dayConfig = dayConfig(new BigDecimal("8.00"), 60, 60,
                new BigDecimal("4.25"), new BigDecimal("1.00"), BreakAlignment.ON_HOUR);

        assertThat(ShiftWindowFinder.breakThresholdSlots(dayConfig)).isEqualTo(5);
    }

    @Test
    void breakSlotCount_derivedFromDurationAndIncrement() {
        AgentDayConfig dayConfig = dayConfig(new BigDecimal("8.00"), 15, 60,
                new BigDecimal("4.00"), new BigDecimal("1.00"), BreakAlignment.ON_HOUR);

        assertThat(ShiftWindowFinder.breakSlotCount(dayConfig)).isEqualTo(4);
    }

    @Test
    void needsBreak_falseAtExactThreshold_trueOneIncrementAbove() {
        AgentDayConfig atThreshold = dayConfig(new BigDecimal("4.00"), 60, 60,
                new BigDecimal("4.00"), new BigDecimal("1.00"), BreakAlignment.ON_HOUR);
        assertThat(ShiftWindowFinder.needsBreak(atThreshold)).isFalse();

        AgentDayConfig aboveThreshold = dayConfig(new BigDecimal("5.00"), 60, 60,
                new BigDecimal("4.00"), new BigDecimal("1.00"), BreakAlignment.ON_HOUR);
        assertThat(ShiftWindowFinder.needsBreak(aboveThreshold)).isTrue();
    }

    // ------------------------------------------------------------------
    //  Full enumeration
    // ------------------------------------------------------------------

    @Test
    void findWindows_twelveHourFixture_returnsExactly28Windows() {
        List<AgentAssignment> seats = hourlySeats(LocalTime.of(9, 0), 12, 60);
        AgentDayConfig dayConfig = referenceDayConfig();

        List<ShiftWindowFinder.ShiftWindow> windows = ShiftWindowFinder.findWindows(seats, dayConfig);

        // Four span starts (09:00, 10:00, 11:00, 12:00) times seven legal
        // break offsets each.
        assertThat(windows).hasSize(28);
    }

    @Test
    void findWindows_neverPlacesBreakInsideLeadingBlockedMargin() {
        List<AgentAssignment> seats = hourlySeats(LocalTime.of(9, 0), 12, 60);
        AgentDayConfig dayConfig = referenceDayConfig();

        List<ShiftWindowFinder.ShiftWindow> windows = ShiftWindowFinder.findWindows(seats, dayConfig);
        assertThat(windows).isNotEmpty();

        for (ShiftWindowFinder.ShiftWindow w : windows) {
            LocalTime spanStart = spanStartOf(w);
            assertThat(w.breakStart())
                    .as("break must not start before one hour after its own span start")
                    .isAfterOrEqualTo(spanStart.plusHours(1));
        }
    }

    @Test
    void findWindows_neverPlacesBreakInsideTrailingBlockedMargin() {
        List<AgentAssignment> seats = hourlySeats(LocalTime.of(9, 0), 12, 60);
        AgentDayConfig dayConfig = referenceDayConfig();

        List<ShiftWindowFinder.ShiftWindow> windows = ShiftWindowFinder.findWindows(seats, dayConfig);
        assertThat(windows).isNotEmpty();

        int breakSlotCount = ShiftWindowFinder.breakSlotCount(dayConfig);
        for (ShiftWindowFinder.ShiftWindow w : windows) {
            LocalTime spanStart = spanStartOf(w);
            LocalTime spanEnd = spanStart.plusMinutes((long) (w.workSeats().size() + breakSlotCount) * 60);
            LocalTime breakEnd = w.breakStart().plusMinutes((long) breakSlotCount * 60);
            assertThat(breakEnd)
                    .as("break must not end later than one hour before its own span end")
                    .isBeforeOrEqualTo(spanEnd.minusHours(1));
        }
    }

    @Test
    void findWindows_halfHourAlignment_stricterThanQuarterHourAlignment() {
        List<AgentAssignment> seats = hourlySeats(LocalTime.of(9, 0), 16, 15);
        AgentDayConfig quarterConfig = dayConfig(new BigDecimal("2.00"), 15, 15,
                new BigDecimal("1.00"), new BigDecimal("0.25"), BreakAlignment.ON_QUARTER_HOUR);
        AgentDayConfig halfHourConfig = dayConfig(new BigDecimal("2.00"), 15, 15,
                new BigDecimal("1.00"), new BigDecimal("0.25"), BreakAlignment.ON_HALF_HOUR);

        List<ShiftWindowFinder.ShiftWindow> quarterWindows = ShiftWindowFinder.findWindows(seats, quarterConfig);
        List<ShiftWindowFinder.ShiftWindow> halfHourWindows = ShiftWindowFinder.findWindows(seats, halfHourConfig);

        assertThat(halfHourWindows).isNotEmpty();
        assertThat(halfHourWindows).allSatisfy(w -> {
            int minute = w.breakStart().getMinute();
            assertThat(minute).as("ON_HALF_HOUR break start minute").isIn(0, 30);
        });
        assertThat(halfHourWindows.size())
                .as("ON_HALF_HOUR is strictly more restrictive than ON_QUARTER_HOUR on 15-min increments")
                .isLessThan(quarterWindows.size());
    }

    @Test
    void findWindows_contiguityHole_noWindowSpansTheMissingSlot() {
        // 20 hourly seats (02:00-21:00) so that removing 13:00 still leaves
        // an 11-slot run (02:00-12:00) long enough for the reference
        // fixture's 9-slot span, while the 8-slot remainder (14:00-21:00)
        // stays too short — proving the hole, not just seat scarcity, is
        // what blocks any window from crossing it.
        List<AgentAssignment> seats = new ArrayList<>(hourlySeats(LocalTime.of(2, 0), 20, 60));
        seats.removeIf(s -> s.getTimeslot().getStartTime().equals(LocalTime.of(13, 0)));
        AgentDayConfig dayConfig = referenceDayConfig();

        List<ShiftWindowFinder.ShiftWindow> windows = ShiftWindowFinder.findWindows(seats, dayConfig);
        assertThat(windows).isNotEmpty();

        int breakSlotCount = ShiftWindowFinder.breakSlotCount(dayConfig);
        LocalTime removed = LocalTime.of(13, 0);
        for (ShiftWindowFinder.ShiftWindow w : windows) {
            LocalTime spanStart = spanStartOf(w);
            LocalTime spanEnd = spanStart.plusMinutes((long) (w.workSeats().size() + breakSlotCount) * 60);
            boolean spansRemovedSlot = !removed.isBefore(spanStart) && removed.isBefore(spanEnd);
            assertThat(spansRemovedSlot).as("no window may span the removed 13:00 slot").isFalse();
        }
    }

    @Test
    void findWindows_duplicateSeatAtSameStartTime_neverProducesWindowWithTwoWorkSeatsAtSameStart() {
        List<AgentAssignment> seats = new ArrayList<>(hourlySeats(LocalTime.of(9, 0), 12, 60));
        // A second, overflow seat sharing the 09:00 start time.
        seats.add(seat(DAY, LocalTime.of(9, 0), 60));
        AgentDayConfig dayConfig = referenceDayConfig();

        List<ShiftWindowFinder.ShiftWindow> windows = ShiftWindowFinder.findWindows(seats, dayConfig);
        assertThat(windows).isNotEmpty();

        for (ShiftWindowFinder.ShiftWindow w : windows) {
            long distinctStarts = w.workSeats().stream()
                    .map(a -> a.getTimeslot().getStartTime())
                    .distinct()
                    .count();
            assertThat(distinctStarts)
                    .as("every returned window must have as many distinct timeslot starts as work seats")
                    .isEqualTo(w.workSeats().size());
        }
    }

    @Test
    void findWindows_noBreakCase_returnsContiguousWindowWithNullBreakStart() {
        List<AgentAssignment> seats = hourlySeats(LocalTime.of(9, 0), 5, 60);
        AgentDayConfig dayConfig = dayConfig(new BigDecimal("3.00"), 60, 60,
                new BigDecimal("4.00"), new BigDecimal("1.00"), BreakAlignment.ON_HOUR);
        assertThat(ShiftWindowFinder.needsBreak(dayConfig)).isFalse();

        List<ShiftWindowFinder.ShiftWindow> windows = ShiftWindowFinder.findWindows(seats, dayConfig);
        assertThat(windows).isNotEmpty();

        int requiredWorkSlots = ShiftWindowFinder.requiredWorkSlots(dayConfig);
        for (ShiftWindowFinder.ShiftWindow w : windows) {
            assertThat(w.breakStart()).isNull();
            assertThat(w.workSeats()).hasSize(requiredWorkSlots);
        }
    }

    @Test
    void findWindows_insufficientSeats_returnsEmptyList() {
        List<AgentAssignment> seats = hourlySeats(LocalTime.of(9, 0), 5, 60);
        AgentDayConfig dayConfig = referenceDayConfig(); // requiredWorkSlots(8) + breakSlotCount(1) = 9 > 5

        List<ShiftWindowFinder.ShiftWindow> windows = ShiftWindowFinder.findWindows(seats, dayConfig);

        assertThat(windows).isEmpty();
    }

    @Test
    void findWindows_deterministicOrderAcrossRepeatedCalls() {
        List<AgentAssignment> seats = hourlySeats(LocalTime.of(9, 0), 12, 60);
        AgentDayConfig dayConfig = referenceDayConfig();

        List<ShiftWindowFinder.ShiftWindow> first = ShiftWindowFinder.findWindows(seats, dayConfig);
        List<ShiftWindowFinder.ShiftWindow> second = ShiftWindowFinder.findWindows(seats, dayConfig);

        assertThat(first).isEqualTo(second);
    }

    // ------------------------------------------------------------------
    //  Fixture helpers
    // ------------------------------------------------------------------

    /** The 09:00-21:00 / 8h / 60-min-break / 1h-blocked / ON_HOUR reference fixture. */
    private static AgentDayConfig referenceDayConfig() {
        return dayConfig(new BigDecimal("8.00"), 60, 60,
                new BigDecimal("4.00"), new BigDecimal("1.00"), BreakAlignment.ON_HOUR);
    }

    private static LocalTime spanStartOf(ShiftWindowFinder.ShiftWindow w) {
        return w.workSeats().stream()
                .map(a -> a.getTimeslot().getStartTime())
                .min(LocalTime::compareTo)
                .orElseThrow();
    }

    private static AgentDayConfig dayConfig(BigDecimal effectiveHours, int incrementMinutes,
            int breakDurationMinutes, BigDecimal breakMinShiftHours, BigDecimal breakBlockedHours,
            BreakAlignment alignment) {
        return new AgentDayConfig(AGENT_ID, DAY, effectiveHours, incrementMinutes, breakDurationMinutes,
                breakMinShiftHours, breakBlockedHours, alignment, 130, 70);
    }

    private static List<AgentAssignment> hourlySeats(LocalTime start, int count, int incrementMinutes) {
        List<AgentAssignment> seats = new ArrayList<>();
        LocalTime t = start;
        for (int i = 0; i < count; i++) {
            seats.add(seat(DAY, t, incrementMinutes));
            t = t.plusMinutes(incrementMinutes);
        }
        return seats;
    }

    private static AgentAssignment seat(LocalDate date, LocalTime start, int incrementMinutes) {
        Timeslot ts = new Timeslot();
        ts.setId(UUID.randomUUID());
        ts.setDate(date);
        ts.setStartTime(start);
        ts.setEndTime(start.plusMinutes(incrementMinutes));

        AgentAssignment aa = new AgentAssignment();
        aa.setId(UUID.randomUUID());
        aa.setTimeslot(ts);
        return aa;
    }
}
