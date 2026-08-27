package com.wfm.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Pre-computed per-agent-day configuration used as a problem fact during solving.
 * Resolves the effective contracted hours for each agent on each day,
 * accounting for AgentExceptions. Also carries schedule-level break/increment
 * config so constraints can access everything from a single join.
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
        int underallocationHardLimitPct
) {

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
