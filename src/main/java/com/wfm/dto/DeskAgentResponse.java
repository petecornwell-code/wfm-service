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
                                Map<DayOfWeek, DayHoursEntry> dayHours,
                                Map<DayOfWeek, UsualShiftEntry> usualShift) {
    public record SpecSummary(UUID id, String name) {}

    /**
     * One weekday's resolved contracted-hours state (D-04/D-06/D-12).
     * hasRow distinguishes an operator-set weekday from a resolved default — never inferred
     * from hours/dayOffType being null, since a stored 0.00 row is itself a real state.
     */
    public record DayHoursEntry(boolean hasRow, BigDecimal hours, DayOffType dayOffType, BigDecimal effectiveHours) {}

    /**
     * One weekday's resolved usual-shift state (D-16, three-state discriminator). {@code name} is
     * the RAW stored template name and is non-null for both LIVE and STORED_INACTIVE; {@code
     * reason} is non-null only for STORED_INACTIVE. Backend-computed so the roster tile never
     * recomputes the classification client-side (16-UI-SPEC.md Component Specifications §1).
     *
     * <p>{@code hoursAdvisory} (D-05) is non-null only when a template is stored for this weekday
     * (LIVE or STORED_INACTIVE) AND none of that stored template's bands' net durations equals
     * the agent's effective contracted hours for that weekday; it is always null for {@code
     * NOT_SET} (nothing stored to measure) and for {@code STORED_INACTIVE}/{@code NOT_WORKED}
     * (no contracted hours to mismatch against). Computed on the READ path only —
     * {@code UsualShiftService.setUsualShift} never gains an hours check, so a mismatch introduced
     * LATER by a contracted-hours edit under an unchanged usual shift stays visible.
     */
    public record UsualShiftEntry(UsualShiftStatus status, String name, UsualShiftReason reason,
                                   String hoursAdvisory) {}

    public enum UsualShiftStatus { NOT_SET, LIVE, STORED_INACTIVE }

    /** RETIRED = D-02 era no longer effective. NOT_WORKED = P-08's not-worked weekday rule. */
    public enum UsualShiftReason { RETIRED, NOT_WORKED }
}
