package com.wfm.util;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the shared enriched-column-layout contract (D-13): specialty-header detection
 * (UPL-02's unbounded Specialty N), day-header title-casing, identity header order/content,
 * and the normalize() trim+lowercase convention.
 */
class EnrichedColumnLayoutTest {

    @Test
    void specialtyIndex_detectsStandardHeader() {
        assertThat(EnrichedColumnLayout.specialtyIndex("specialty 1")).contains(1);
    }

    @Test
    void specialtyIndex_toleratesExtraWhitespace() {
        assertThat(EnrichedColumnLayout.specialtyIndex("specialty  2")).contains(2);
    }

    @Test
    void specialtyIndex_isCaseInsensitive() {
        assertThat(EnrichedColumnLayout.specialtyIndex("SPECIALTY 3")).contains(3);
    }

    @Test
    void specialtyIndex_rejectsNonMatchingWords() {
        assertThat(EnrichedColumnLayout.specialtyIndex("specialties")).isEmpty();
        assertThat(EnrichedColumnLayout.specialtyIndex("email")).isEmpty();
    }

    @Test
    void specialtyIndex_overflowingDigitGroup_returnsEmptyInsteadOfThrowing() {
        // WR-02: a header with an absurd digit count must not throw an uncaught
        // NumberFormatException and crash the whole upload with a 500.
        assertThat(EnrichedColumnLayout.specialtyIndex("specialty 99999999999999999999")).isEmpty();
    }

    @Test
    void dayHeader_titleCasesBoundaryDays() {
        assertThat(EnrichedColumnLayout.dayHeader(DayOfWeek.MONDAY)).isEqualTo("Monday");
        assertThat(EnrichedColumnLayout.dayHeader(DayOfWeek.SUNDAY)).isEqualTo("Sunday");
    }

    @Test
    void identityHeaders_hasSevenEntriesInOrder() {
        assertThat(EnrichedColumnLayout.identityHeaders())
                .hasSize(7)
                .startsWith("BambooHR ID")
                .contains("Active");
    }

    @Test
    void normalize_trimsAndLowercases() {
        assertThat(EnrichedColumnLayout.normalize("  Monday ")).isEqualTo("monday");
    }

    @Test
    void normalize_nullBecomesEmptyString() {
        assertThat(EnrichedColumnLayout.normalize(null)).isEqualTo("");
    }
}
