package com.wfm.dto;

import java.time.OffsetDateTime;

public record BambooSyncEventResponse(
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt,
        boolean success,
        String errorMessage,
        Integer agentsSynced,
        Integer timeOffPulled,
        Integer retryAfterSeconds
) {}
