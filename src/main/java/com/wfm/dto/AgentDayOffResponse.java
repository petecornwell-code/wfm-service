package com.wfm.dto;

import java.time.LocalDate;
import java.util.UUID;

public record AgentDayOffResponse(
        UUID id,
        LocalDate date,
        String type,
        AgentSummary agent
) {
    /** Compact agent info for the list-all endpoint. Null for per-agent queries. */
    public record AgentSummary(UUID id, String name) {}

    /** Factory for per-agent responses (no agent summary needed). */
    public static AgentDayOffResponse forAgent(UUID id, LocalDate date, String type) {
        return new AgentDayOffResponse(id, date, type, null);
    }

    /** Factory for list-all responses (includes agent summary). */
    public static AgentDayOffResponse withAgent(UUID id, LocalDate date, String type,
                                                 UUID agentId, String agentName) {
        return new AgentDayOffResponse(id, date, type, new AgentSummary(agentId, agentName));
    }
}
