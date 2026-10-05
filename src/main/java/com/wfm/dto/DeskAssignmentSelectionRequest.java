package com.wfm.dto;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * A desk-assignment workbook built from an explicitly chosen set of people rather than from a
 * desk's current roster.
 *
 * <p>The employees are sent by the caller rather than re-fetched by department, because a selection
 * spans several department searches and the client already holds every row it is asking for.
 * Re-querying BambooHR per department would multiply calls against a rate-limited API for data the
 * caller can simply hand over.
 *
 * <p>{@code bamboohrId} must be the {@code id} the employee search returned. The upload parser
 * resolves rows against the same field of the same BambooHR snapshot, so a workbook generated from
 * a search round-trips by construction.
 *
 * @param deskId    the desk this roster is for; names the single sheet, which is what the upload
 *                  parser reads a sheet name as
 * @param employees the chosen people, in the order they should appear
 */
public record DeskAssignmentSelectionRequest(
        UUID deskId,
        List<Employee> employees,
        /**
         * Any date in the week to pre-populate. Normalised to that ISO week's Monday, so the seven
         * day columns always line up Monday..Sunday. Null leaves every day cell blank — the
         * pre-population behaviour, which the upload parser skips row-by-row.
         */
        LocalDate weekStart,
        /**
         * Hours written into every day that is NOT approved time off. Null falls back to the desk's
         * {@code defaultContractedHoursPerDay}. Zero is legal and meaningful: it produces a roster
         * of agents the solver treats as unavailable until the hours are edited.
         */
        BigDecimal workingDayHours
) {
    /**
     * No week and no hours: every day cell is left blank, exactly as before day-cell
     * pre-population existed. Kept so a caller that does not care about hours need not say so.
     */
    public DeskAssignmentSelectionRequest(UUID deskId, List<Employee> employees) {
        this(deskId, employees, null, null);
    }

    /** Mirrors {@link BambooEmployeeResponse} so a search result can be passed straight through. */
    public record Employee(
            String bamboohrId,
            String displayName,
            String workEmail,
            String department,
            String jobTitle,
            String status
    ) {}
}
