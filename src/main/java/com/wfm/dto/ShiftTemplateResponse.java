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
 * existing component order is undisturbed.
 */
public record ShiftTemplateResponse(
        UUID id,
        String name,
        LocalTime startTime,
        LocalTime endTime,
        int breakOffsetMinutes,
        int breakDurationMinutes,
        LocalTime breakStartTime,
        LocalTime breakEndTime,
        BigDecimal netHours,
        List<DayOfWeek> validWeekdays,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String eraStatus
) {}
