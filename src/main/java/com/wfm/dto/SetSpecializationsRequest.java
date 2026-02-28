package com.wfm.dto;

import java.util.List;
import java.util.UUID;

public record SetSpecializationsRequest(
        UUID primarySpecializationId,
        List<UUID> secondarySpecializationIds
) {}
