package com.wfm.dto;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * {@code eraStatus} is computed server-side (CURRENT / UPCOMING / PAST, P-13) from the row's
 * effective range against today, so the operator sees the same era the non-overlap invariant
 * guarantees rather than a browser-side re-derivation (XCUT-01). Positioned last so the
 * existing component order is otherwise undisturbed. {@code bands} replaces Phase 14's four
 * scalar break fields and its single {@code netHours} (D-01) -- rendered offset-ascending.
 */
public record ShiftTemplateResponse(
        UUID id,
        String name,
        LocalTime startTime,
        LocalTime endTime,
        List<BreakBandResponse> bands,
        List<DayOfWeek> validWeekdays,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String eraStatus
) {
    /** One band, offset-ascending. {@code capacity} blank/null means unlimited (D-03). */
    public record BreakBandResponse(
            UUID id,
            int offsetMinutes,
            int durationMinutes,
            LocalTime breakStartTime,
            LocalTime breakEndTime,
            Integer capacity,
            BigDecimal netHours
    ) {}
}
