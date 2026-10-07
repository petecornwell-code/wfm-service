package com.wfm.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record StaffingRequirementResponse(List<Item> requirements) {
    /**
     * {@code date} is the CALENDAR date (SOLV-07/D-10: it answers "when does this happen").
     * {@code businessDate} is the additive join key -- the timeslot's stored business date, which
     * the schedule grid and allocation rows key on; it differs from {@code date} for post-midnight
     * rows on a desk whose day start is not {@code 00:00}.
     */
    public record Item(UUID id, UUID timeslotId, UUID specializationId, LocalDate date,
                       LocalDate businessDate, LocalTime startTime, LocalTime endTime, String specializationName,
                       int requiredFTEs, String source) {}
}
