package com.wfm.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

public record SolveRequest(
        LocalDate periodStartDate,
        LocalDate periodEndDate,
        LocalTime startTime,
        LocalTime endTime,
        int incrementMinutes,
        BigDecimal breakBlockedHours,
        Integer breakDurationMinutes,
        BigDecimal breakMinShiftHours,
        String breakStartAlignment,
        Integer breakClusterThresholdPct,
        BigDecimal defaultContractedHoursPerDay,
        Integer overallocationHardLimitPct,
        Integer underallocationHardLimitPct,
        Integer solveTimeSeconds,
        Integer shiftEnvelopeSlackSlots
) {}
