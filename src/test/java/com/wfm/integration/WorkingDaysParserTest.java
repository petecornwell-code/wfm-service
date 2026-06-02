package com.wfm.integration;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.DayOfWeek;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static java.time.DayOfWeek.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Parameterized test covering the full live BambooHR {@code customWorkingdays} (field 4517)
 * value catalog. Each row exercises {@link WorkingDaysParser#parseWorkingDays(String)}.
 *
 * <p>Test lives in the same package as the parser (package-private access; no reflection).
 *
 * <p>When {@code expectedWorking} is {@code null} the value is a data-gap case
 * (null / blank / "Variable") and the result must be an empty Optional.
 */
class WorkingDaysParserTest {

    @ParameterizedTest(name = "[{index}] input={0}")
    @MethodSource("workingDaysSource")
    void parseWorkingDays_allFormats(String input, Set<DayOfWeek> expectedWorking) {
        Optional<Set<DayOfWeek>> result = WorkingDaysParser.parseWorkingDays(input);

        if (expectedWorking == null) {
            // Data-gap cases: null, blank, "Variable"
            assertThat(result).isEmpty();
        } else {
            assertThat(result).isPresent();
            assertThat(result.get()).containsExactlyInAnyOrderElementsOf(expectedWorking);
        }
    }

    static Stream<Arguments> workingDaysSource() {
        return Stream.of(
                // Standard 5-day Mon-Fri
                Arguments.of("Mon-Fri",                    Set.of(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY)),
                // Non-Monday start ranges (no week-wrap)
                Arguments.of("Wed-Sun",                    Set.of(WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY)),
                Arguments.of("Sun-Thu",                    Set.of(SUNDAY, MONDAY, TUESDAY, WEDNESDAY, THURSDAY)),
                Arguments.of("Tue-Sat",                    Set.of(TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY)),
                // Week-wrapping range: Fri end-idx (4) < Tue start-idx... wait, Fri=4, Tue=1
                // Fri-Tue: startIdx=4, endIdx=1 → wraps → Fri,Sat,Sun,Mon,Tue
                Arguments.of("Fri-Tue",                    Set.of(FRIDAY, SATURDAY, SUNDAY, MONDAY, TUESDAY)),
                // All 7 days — 0 off-days outlier
                Arguments.of("Mon - Sun",                  Set.of(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY)),
                // All 7 days with trailing non-day annotation "HOOP"
                Arguments.of("Mon - Sun HOOP",             Set.of(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY)),
                // "to" separator with period-terminated tokens and Thurs. variant (4-day week)
                Arguments.of("Mon. to Thurs.",             Set.of(MONDAY, TUESDAY, WEDNESDAY, THURSDAY)),
                // Comma-separated list (non-consecutive off-days)
                Arguments.of("Mon, Tue, Wed, Thu, Sat",    Set.of(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, SATURDAY)),
                // Garbage / unknown input — must return empty, never throw
                Arguments.of("xyz garbage 123",            null),
                // Data-gap cases
                Arguments.of("Variable",                   null),
                Arguments.of("variable",                   null),  // case-insensitive
                Arguments.of("",                           null),
                Arguments.of(null,                         null)
        );
    }
}
