package com.wfm.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record StaffingRequirementResponse(List<Item> requirements) {
    public record Item(UUID id, UUID timeslotId, UUID specializationId, LocalDate date,
                       LocalTime startTime, LocalTime endTime, String specializationName,
                       int requiredFTEs, String source) {}
}
