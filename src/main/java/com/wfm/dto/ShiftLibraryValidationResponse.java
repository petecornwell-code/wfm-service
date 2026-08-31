package com.wfm.dto;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
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
        List<BreakConcentrationAdvisory> breakConcentrationAdvisories,
        List<PeakShortfallAdvisory> peakShortfallAdvisories
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

    /**
     * A single hour whose demand EXCEEDS every agent who could possibly be working it.
     *
     * <p>The gap this closes: every other supply check on this desk is a per-DATE aggregate.
     * {@code SolverService.requireShiftEnvelopeSeatSupply} compares a day's contracted slots
     * against its library-covered seat supply, and the staffing summary reports daily coverage
     * percentages. Both can report a comfortable surplus while one hour inside that day is
     * unmeetable — and they did. Observed live: the desk ran 143 demand-hours against 200 staffed
     * (140% coverage, every aggregate check clean) while Saturday 11:00 required 44 FTE against
     * 25 agents on the entire desk. Short by 19 people, invisible to everything.
     *
     * <p>{@code reachableAgents} is a deliberate UPPER bound: every agent rostered that weekday
     * whose contracted hours match at least one (template, band) pair covering this hour, ignoring
     * that those same agents must also cover other hours. A real schedule can only do worse. So a
     * shortfall reported here is PROVABLE — no library edit and no amount of solve time can close
     * it — which is what makes it worth blocking an operator's attention rather than another
     * number to weigh.
     *
     * <p>Advisory, never blocking. An unmeetable peak is a staffing fact, not a reason to refuse a
     * solve: the operator still wants the best partial schedule that hour admits.
     */
    public record PeakShortfallAdvisory(
            LocalDate date,
            LocalTime startTime,
            LocalTime endTime,
            int requiredFTEs,
            long reachableAgents,
            long shortfall,
            String message
    ) {}
}
