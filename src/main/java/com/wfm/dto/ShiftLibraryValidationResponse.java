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
        List<String> unsatisfiableWeekdays,
        List<CapacityAdvisory> capacityAdvisories
) {
    /** SHLB-06 advisory (D-06/D-07): never blocking, except folded into unsatisfiableWeekdays. */
    public record HoursAdvisory(
            UUID templateId,
            String templateName,
            DayOfWeek weekday,
            BigDecimal netHours,
            String message
    ) {}

    /**
     * D-03's named residual risk, placed (Task 3, P-06): an operator whose band capacities total
     * below a shift's admissible headcount sees this rather than a bare hard score at solve time.
     * Advisory only in this plan — never thrown by {@code requireShiftModeReady}.
     */
    public record CapacityAdvisory(
            UUID templateId,
            String templateName,
            DayOfWeek weekday,
            int capacityTotal,
            long admissibleHeadcount,
            String message
    ) {}
}
