package com.wfm.model;

import java.math.BigDecimal;
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
) {}
