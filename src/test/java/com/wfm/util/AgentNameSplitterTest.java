package com.wfm.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the D-06 name-split rule: first whitespace token -> firstName,
 * trimmed remainder -> lastName. This is the single shared rule reused by
 * every Java write-site (BambooHR refresh, upload, Flyway migration).
 */
class AgentNameSplitterTest {

    static Stream<Arguments> splitCases() {
        return Stream.of(
                Arguments.of("Mary Jane Watson", "Mary", "Jane Watson"),
                Arguments.of("First Last", "First", "Last"),
                Arguments.of("Alice", "Alice", ""),
                Arguments.of("  Bob  Smith  ", "Bob", "Smith"),
                Arguments.of(null, "", ""),
                Arguments.of("", "", ""),
                Arguments.of("   ", "", "")
        );
    }

    @ParameterizedTest
    @MethodSource("splitCases")
    void split_returnsExpectedFirstAndLastName(String displayName, String expectedFirst, String expectedLast) {
        AgentNameSplitter.Split result = AgentNameSplitter.split(displayName);

        assertThat(result.firstName()).isEqualTo(expectedFirst);
        assertThat(result.lastName()).isEqualTo(expectedLast);
    }

    @Test
    void split_lastNameIsNeverNull_forSingleTokenName() {
        AgentNameSplitter.Split result = AgentNameSplitter.split("Alice");

        assertThat(result.lastName()).isNotNull().isEmpty();
    }

    @Test
    void split_null_returnsEmptySplit() {
        AgentNameSplitter.Split result = AgentNameSplitter.split(null);

        assertThat(result.firstName()).isEmpty();
        assertThat(result.lastName()).isEmpty();
    }

    @Test
    void split_blank_returnsEmptySplit() {
        AgentNameSplitter.Split result = AgentNameSplitter.split("   ");

        assertThat(result.firstName()).isEmpty();
        assertThat(result.lastName()).isEmpty();
    }
}
