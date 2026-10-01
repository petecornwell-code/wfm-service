package com.wfm.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Pre-computed per-agent-day configuration used as a problem fact during solving.
 * Resolves the effective contracted hours for each agent on each day,
 * accounting for AgentExceptions. Also carries schedule-level break/increment
 * config so constraints can access everything from a single join.
 *
 * <p>{@code dayStart} (SOLV-03) is the Quad-arity constraint carrier for the desk's anchor:
 * Timefold 1.16.0's public Constraint Streams API tops out at {@code QuadConstraintStream}, so
 * the constraints that reach Quad arity before the desk anchor is needed cannot take a further
 * {@code .join(ScheduleConfig.class)}. This record already duplicates seven other schedule-level
 * scalars for exactly that reason, and {@code dayStart} is the next one.
 */
public record AgentDayConfig(
        UUID agentId,
        LocalDate date,
        BigDecimal effectiveHours,
        int incrementMinutes,
        int breakDurationMinutes,
        BigDecimal breakMinShiftHours,
        BigDecimal breakBlockedHours,
        BreakAlignment breakStartAlignment,
        int overallocationHardLimitPct,
        int underallocationHardLimitPct,
        LocalTime dayStart
) {

    /**
     * Delegating constructor preserving the pre-SOLV-03 10-argument shape, so every pre-existing
     * test construction site compiles unchanged — supplies {@link LocalTime#MIDNIGHT} for the new
     * 11th component. This midnight default exists for test fixtures only; the single production
     * construction site ({@code SolverService#computeAgentDayConfigs}) passes the value
     * explicitly. Deliberate carve-out from the D-07 allowlist, whose scope is
     * {@code src/main/java}.
     */
    public AgentDayConfig(
            UUID agentId,
            LocalDate date,
            BigDecimal effectiveHours,
            int incrementMinutes,
            int breakDurationMinutes,
            BigDecimal breakMinShiftHours,
            BigDecimal breakBlockedHours,
            BreakAlignment breakStartAlignment,
            int overallocationHardLimitPct,
            int underallocationHardLimitPct
    ) {
        this(agentId, date, effectiveHours, incrementMinutes, breakDurationMinutes,
                breakMinShiftHours, breakBlockedHours, breakStartAlignment,
                overallocationHardLimitPct, underallocationHardLimitPct, LocalTime.MIDNIGHT);
    }

    /**
     * The number of grid slots this agent-day is contracted to work — {@code effectiveHours}
     * converted to slots at {@code incrementMinutes}. This is the ONE implementation
     * {@code ScheduleConstraintProvider}'s hard contracted-hours constraints
     * (over/under/under-zero) and {@code SolverService}'s shift-mode seat-supply gate
     * (Phase 15 plan 15-11) both call — the two must never re-derive this arithmetic
     * independently, or the gate could refuse a solvable desk (if it under-counts) or wave
     * through an unsolvable one (if it over-counts) relative to what the hard constraints
     * actually enforce.
     *
     * <p>Rounding mode is {@link RoundingMode#HALF_UP} and that is load-bearing, not
     * incidental: this codebase already carries a second, different rounding mode
     * ({@link RoundingMode#CEILING}) in other constraints, so introducing a third mode here
     * would silently disagree with the constraints this method now backs.
     */
    public int expectedWorkSlots() {
        return effectiveHours()
                .multiply(BigDecimal.valueOf(60))
                .divide(BigDecimal.valueOf(incrementMinutes()), 0, RoundingMode.HALF_UP)
                .intValue();
    }
}
