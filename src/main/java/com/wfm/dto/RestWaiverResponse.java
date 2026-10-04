package com.wfm.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Wire shape for a rest waiver — the same shape as {@link ExceptionResponse} minus the hours
 * component. Adding an hours component here would silently resurrect the design D-07 rejected.
 */
public record RestWaiverResponse(
        UUID id,
        LocalDate date,
        String reason
) {}
