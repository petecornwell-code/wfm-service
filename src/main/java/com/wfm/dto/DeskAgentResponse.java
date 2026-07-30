package com.wfm.dto;

import com.wfm.model.EmploymentType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record DeskAgentResponse(UUID id, UUID deskId, String bamboohrId, String name,
                                String firstName, String lastName, String email,
                                String department, String jobTitle, boolean active,
                                OffsetDateTime lastRefreshedAt,
                                SpecSummary primarySpecialization, List<SpecSummary> secondarySpecializations,
                                BigDecimal contractedHoursPerDay, BigDecimal effectiveContractedHoursPerDay,
                                EmploymentType employmentType,
                                int pendingPtoCount,
                                List<LocalDate> pendingPtoDates) {
    public record SpecSummary(UUID id, String name) {}
}
