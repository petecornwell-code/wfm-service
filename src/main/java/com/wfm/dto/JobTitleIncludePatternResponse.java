package com.wfm.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record JobTitleIncludePatternResponse(
        UUID id,
        String pattern,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
