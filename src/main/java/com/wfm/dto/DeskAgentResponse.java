package com.wfm.dto;

import com.wfm.model.DayOffType;
import com.wfm.model.EmploymentType;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record DeskAgentResponse(UUID id, UUID deskId, String bamboohrId, String name,
                                String firstName, String lastName, String email,
                                String department, String jobTitle, boolean active,
                                OffsetDateTime lastRefreshedAt,
                                SpecSummary primarySpecialization, List<SpecSummary> secondarySpecializations,
                                BigDecimal contractedHoursPerDay, BigDecimal effectiveContractedHoursPerDay,
                                EmploymentType employmentType,
                                int pendingPtoCount,
                                List<LocalDate> pendingPtoDates,
                                Map<DayOfWeek, DayHoursEntry> dayHours) {
    public record SpecSummary(UUID id, String name) {}

    /**
     * One weekday's resolved contracted-hours state (D-04/D-06/D-12).
     * hasRow distinguishes an operator-set weekday from a resolved default — never inferred
     * from hours/dayOffType being null, since a stored 0.00 row is itself a real state.
     */
    public record DayHoursEntry(boolean hasRow, BigDecimal hours, DayOffType dayOffType, BigDecimal effectiveHours) {}
}
