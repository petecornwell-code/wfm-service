package com.wfm.dto;

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
        List<Employee> employees
) {
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
