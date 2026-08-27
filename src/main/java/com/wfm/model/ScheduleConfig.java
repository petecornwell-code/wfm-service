package com.wfm.model;

import java.math.BigDecimal;
import java.time.LocalTime;

/**
 * Immutable problem fact holding schedule configuration values
 * so constraints can access them via join/forEach in constraint streams.
 */
public record ScheduleConfig(
        int incrementMinutes,
        LocalTime startTime,
        LocalTime endTime,
        int breakDurationMinutes,
        BigDecimal breakMinShiftHours,
        BigDecimal breakBlockedHours,
        BreakAlignment breakStartAlignment,
        int breakClusterThresholdPct,
        BigDecimal defaultContractedHoursPerDay,
        int overallocationHardLimitPct,
        int underallocationHardLimitPct,
        SchedulingMode schedulingMode
) {}
