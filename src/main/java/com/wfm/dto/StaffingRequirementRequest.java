package com.wfm.dto;

import java.util.List;
import java.util.UUID;

public record StaffingRequirementRequest(
        List<Item> items
) {
    public record Item(
            UUID timeslotId,
            UUID specializationId,
            int requiredAgents
    ) {}
}
