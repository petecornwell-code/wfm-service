package com.wfm.dto;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

public record ShiftTemplateRequest(
        String name,
        LocalTime startTime,
        LocalTime endTime,
        List<BreakBandRequest> bands,
        Set<DayOfWeek> validWeekdays,
        LocalDate effectiveFrom,
        LocalDate effectiveTo
) {
    /**
     * One requested break band (D-01). A null or empty {@code bands} list on the parent request
     * is legal and means no break. {@code capacity} blank/null means unlimited (D-03); a supplied
     * capacity below 1 is rejected at save time.
     */
    public record BreakBandRequest(Integer offsetMinutes, Integer durationMinutes, Integer capacity) {}
}
