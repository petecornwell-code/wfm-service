package com.wfm.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the effective-hours resolution rules for {@code SolverService.resolveEffectiveHours}
 * (D-03/D-04):
 *
 *  - AgentException override (keyed by date) — highest precedence
 *  - per-day value for that weekday (keyed by DayOfWeek) — second precedence, including 0.00
 *  - schedule default — fallback when neither of the above has an entry
 *
 * Tests call the package-private static helper
 * {@code SolverService.resolveEffectiveHours(...)} directly, extracted from the original
 * {@code getEffectiveHours} instance method so it can be unit-tested without Spring context,
 * mirroring the {@code buildAgentDaysOffMap} precedent in {@code SolverServicePtoFilterTest}.
 */
class SolverServiceEffectiveHoursResolutionTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 8, 3); // confirmed Monday
    private static final BigDecimal SCHEDULE_DEFAULT = new BigDecimal("8.00");

    @Test
    void exceptionPresent_returnsExceptionOverride_ignoringDayHoursMap() {
        Map<LocalDate, BigDecimal> exceptionMap = new HashMap<>();
        exceptionMap.put(MONDAY, new BigDecimal("4.00"));
        Map<DayOfWeek, BigDecimal> dayHoursMap = new HashMap<>();
        dayHoursMap.put(DayOfWeek.MONDAY, new BigDecimal("6.00"));

        BigDecimal result = SolverService.resolveEffectiveHours(exceptionMap, dayHoursMap, MONDAY, SCHEDULE_DEFAULT);

        assertThat(result).isEqualByComparingTo("4.00");
    }

    @Test
    void noException_dayHoursMapHasWeekday_returnsPerDayValue() {
        Map<LocalDate, BigDecimal> exceptionMap = new HashMap<>();
        Map<DayOfWeek, BigDecimal> dayHoursMap = new HashMap<>();
        dayHoursMap.put(DayOfWeek.MONDAY, new BigDecimal("6.00"));

        BigDecimal result = SolverService.resolveEffectiveHours(exceptionMap, dayHoursMap, MONDAY, SCHEDULE_DEFAULT);

        assertThat(result).isEqualByComparingTo("6.00");
    }

    @Test
    void noException_dayHoursMapHasZeroForWeekday_returnsZero_notScheduleDefault() {
        Map<LocalDate, BigDecimal> exceptionMap = new HashMap<>();
        Map<DayOfWeek, BigDecimal> dayHoursMap = new HashMap<>();
        dayHoursMap.put(DayOfWeek.MONDAY, BigDecimal.ZERO);

        BigDecimal result = SolverService.resolveEffectiveHours(exceptionMap, dayHoursMap, MONDAY, SCHEDULE_DEFAULT);

        assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result).isNotEqualByComparingTo(SCHEDULE_DEFAULT);
    }

    @Test
    void noException_weekdayAbsentFromDayHoursMap_fallsBackToScheduleDefault() {
        Map<LocalDate, BigDecimal> exceptionMap = new HashMap<>();
        Map<DayOfWeek, BigDecimal> dayHoursMap = new HashMap<>(); // no MONDAY entry

        BigDecimal result = SolverService.resolveEffectiveHours(exceptionMap, dayHoursMap, MONDAY, SCHEDULE_DEFAULT);

        assertThat(result).isEqualByComparingTo(SCHEDULE_DEFAULT);
    }

    /**
     * EQUIVALENCE (Success Criterion 4): a uniform 7-day map with all weekdays set to the same
     * value V, and an empty exception map, must return exactly V for every DayOfWeek — identical
     * to the old scalar-only result. This pins behaviour equivalence for uniform-hours agents.
     */
    @Test
    void uniformDayHoursMap_allSevenWeekdays_returnsExactValueForEveryDate() {
        BigDecimal v = new BigDecimal("7.50");
        Map<LocalDate, BigDecimal> exceptionMap = new HashMap<>();
        Map<DayOfWeek, BigDecimal> dayHoursMap = new HashMap<>();
        for (DayOfWeek dow : DayOfWeek.values()) {
            dayHoursMap.put(dow, v);
        }

        // MONDAY 2026-08-03 is the anchor; walk all 7 dates of that week.
        for (int offset = 0; offset < 7; offset++) {
            LocalDate date = MONDAY.plusDays(offset);
            BigDecimal result = SolverService.resolveEffectiveHours(exceptionMap, dayHoursMap, date, SCHEDULE_DEFAULT);
            assertThat(result)
                    .as("date=%s dayOfWeek=%s", date, date.getDayOfWeek())
                    .isEqualByComparingTo(v);
        }
    }
}
