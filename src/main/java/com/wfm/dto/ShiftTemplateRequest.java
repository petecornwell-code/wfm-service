package com.wfm.dto;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

public record ShiftTemplateRequest(
        String name,
        LocalTime startTime,
        LocalTime endTime,
        Integer breakOffsetMinutes,
        Integer breakDurationMinutes,
        Set<DayOfWeek> validWeekdays,
        LocalDate effectiveFrom,
        LocalDate effectiveTo
) {}
