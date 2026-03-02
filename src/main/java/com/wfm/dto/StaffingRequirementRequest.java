package com.wfm.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record StaffingRequirementRequest(
        List<Item> requirements
) {
    public record Item(
            UUID timeslotId,
            UUID specializationId,
            BigDecimal requiredHours
    ) {}
}
