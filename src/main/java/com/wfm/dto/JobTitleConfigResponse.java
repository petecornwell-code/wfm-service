package com.wfm.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record JobTitleConfigResponse(
        UUID id,
        String jobTitle,
        boolean nonSchedulable,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
