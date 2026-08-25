package com.wfm.dto;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

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
        LocalDate effectiveTo
) {}
