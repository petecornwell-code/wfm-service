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
        List<CapacityAdvisory> capacityAdvisories,
        List<BreakConcentrationAdvisory> breakConcentrationAdvisories
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

    /**
     * The INVERSE of {@link CapacityAdvisory}, and the gap that advisory structurally cannot see.
     *
     * <p>{@code CapacityAdvisory} fires when band capacity is too LOW. It skips any template
     * carrying a blank-capacity band outright ("unlimited by construction" — it genuinely cannot
     * be short). But unlimited is the DEFAULT, and it is what permits every agent on a shift to
     * be given the same break hour. So the single most damaging configuration — one band, blank
     * capacity — passed every existing check in silence.
     *
     * <p>Observed live on desk Stubhub (EN): four templates, one band each, all capacities NULL
     * (V40 migrates every Phase 14 break forward with a NULL capacity by design). The Shift
     * Library page reported nothing. The solve then put 18 of 18 Late agents on a 16:00 break
     * simultaneously, emptied the hour, and had to seat agents through their own break to hold
     * it — 13 hard violations that no advisory had predicted. Splitting each template into three
     * capped bands removed all but 2 of them.
     *
     * <p>{@code worstCaseSimultaneousBreak} is what the library PERMITS, not what a given solve
     * produced: the largest single band capacity (capped by headcount), or the whole headcount
     * when any band is blank. Advisory only — a concentrated library is legal and can be
     * perfectly fine on a shift whose break hour carries little demand.
     */
    public record BreakConcentrationAdvisory(
            UUID templateId,
            String templateName,
            DayOfWeek weekday,
            int bandCount,
            long admissibleHeadcount,
            long worstCaseSimultaneousBreak,
            String message
    ) {}
}
