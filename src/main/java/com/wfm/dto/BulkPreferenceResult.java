package com.wfm.dto;

public record BulkPreferenceResult(
        int updatedAgentCount,
        int preferencesPerAgent,
        int totalPreferencesSaved
) {}
