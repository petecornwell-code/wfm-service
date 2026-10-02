package com.wfm.dto;

import com.wfm.model.SchedulingMode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * {@code dayStartLockedByScheduleId}, {@code dayStartLockedPeriodStart} and {@code
 * dayStartLockedPeriodEnd} (OVNT-01, D-04) are populated whenever an ACCEPTED schedule exists on
 * the desk -- all three or none, never a partial set -- disclosing the permanent lock on {@code
 * dayStart} the frontend's disabled-control rendering needs. {@code dayStartTilingWarning}
 * (OVNT-01, D-05) is populated only on the day-start write response, when the proposed value does
 * not tile the desk's existing live generation increment; it is never a refusal.
 */
public record DeskResponse(
        UUID id,
        String name,
        String description,
        BigDecimal defaultContractedHoursPerDay,
        SchedulingMode schedulingMode,
        LocalTime dayStart,
        UUID dayStartLockedByScheduleId,
        LocalDate dayStartLockedPeriodStart,
        LocalDate dayStartLockedPeriodEnd,
        String dayStartTilingWarning
) {}
