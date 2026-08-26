package com.wfm.dto;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.util.List;
import java.util.UUID;

/**
 * The single report {@code ShiftLibraryValidationService.validate} produces (P-17, D-08). Never
 * thrown — the shift-library editor reads this directly; {@code requireShiftModeReady} converts
 * a subset of these findings into a {@code PreSolveValidationException}.
 */
public record ShiftLibraryValidationResponse(
        boolean hasLiveDemand,
        List<String> uncoveredWindows,
        List<String> misalignedTemplates,
        List<HoursAdvisory> hoursAdvisories,
        List<String> unsatisfiableWeekdays
) {
    /** SHLB-06 advisory (D-06/D-07): never blocking, except folded into unsatisfiableWeekdays. */
    public record HoursAdvisory(
            UUID templateId,
            String templateName,
            DayOfWeek weekday,
            BigDecimal netHours,
            String message
    ) {}
}
