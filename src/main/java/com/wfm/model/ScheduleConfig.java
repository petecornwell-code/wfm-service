package com.wfm.model;

import java.math.BigDecimal;
import java.time.LocalTime;

/**
 * Immutable problem fact holding schedule configuration values
 * so constraints can access them via join/forEach in constraint streams.
 *
 * <p>{@code consistencyToleranceMinutes} (Phase 17, D-04) is this record's solver-facing
 * TRANSPORT of the desk's tolerance band — the band is STORED per desk on
 * {@code ConstraintWeights.consistencyToleranceMinutes} (a {@code @ConstraintConfiguration}
 * entity field, D-04's decision about where the value lives), but {@code ConstraintWeights} is a
 * {@code @ConstraintConfigurationProvider}, not a problem fact: no constraint in
 * {@code ScheduleConstraintProvider} joins it directly. {@code ScheduleConfig}, by contrast, is
 * already joined seventeen times and is already in scope for {@code usualShiftConsistency}'s
 * {@code SchedulingMode.SHIFT} mode gate, so carrying the band here adds no new join. This is a
 * transport decision only — it does not revisit D-04's storage decision.
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
        SchedulingMode schedulingMode,
        int consistencyToleranceMinutes
) {
    /** Default tolerance band (minutes) used by the 12-argument delegating constructor below. */
    public static final int DEFAULT_CONSISTENCY_TOLERANCE_MINUTES = 60;

    /**
     * Delegating constructor preserving the pre-Phase-17 12-argument shape, so every existing
     * test construction site compiles unchanged — each supplies
     * {@link #DEFAULT_CONSISTENCY_TOLERANCE_MINUTES} for the new 13th component.
     */
    public ScheduleConfig(
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
    ) {
        this(incrementMinutes, startTime, endTime, breakDurationMinutes, breakMinShiftHours,
                breakBlockedHours, breakStartAlignment, breakClusterThresholdPct,
                defaultContractedHoursPerDay, overallocationHardLimitPct, underallocationHardLimitPct,
                schedulingMode, DEFAULT_CONSISTENCY_TOLERANCE_MINUTES);
    }
}
