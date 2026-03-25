package com.wfm.dto;

import java.util.List;
import java.util.UUID;

public record BulkPreferenceRequest(
        List<UUID> agentIds,
        List<PreferenceResponse> preferences
) {}
