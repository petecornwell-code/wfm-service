package com.wfm.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AgentResponse(
        UUID id,
        String name,
        String firstName,
        String lastName,
        String email,
        String department,
        String jobTitle,
        boolean active,
        OffsetDateTime lastRefreshedAt
) {}
