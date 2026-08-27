package com.wfm.dto;

import com.wfm.dto.ErrorResponse.ErrorDetail;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * SHLB-07's stateless suggestion response (D-11): editable draft {@link SuggestedTemplate} rows an
 * operator can rename, edit, or drop before saving through the existing {@code
 * ShiftTemplateService} create/validate path unchanged, plus (D-12) any demand windows the draft
 * could not cover, named in the exact {@link ErrorDetail} shape SHLB-05's coverage report already
 * emits ({@code field="coverage"}, the window rendered date/start/end, {@code value=null}). Never
 * persisted anywhere -- no draft table, no status column (D-11), mirroring D-10 (Phase 14)'s
 * one-mechanism reasoning.
 */
public record ShiftLibrarySuggestionResponse(
        List<SuggestedTemplate> templates,
        List<ErrorDetail> uncoveredWindows
) {
    /**
     * Mirrors {@code ShiftTemplateRequest}'s field set (P-10) so a draft row can be handed straight
     * to the existing create endpoint without a translation layer. {@code name} follows P-10
     * ("Suggested 1", "Suggested 2", ... in emission order); {@code effectiveFrom} is today,
     * {@code effectiveTo} is null (open-ended) -- the manual Add form's own defaults.
     */
    public record SuggestedTemplate(
            String name,
            LocalTime startTime,
            LocalTime endTime,
            List<SuggestedBand> bands,
            List<DayOfWeek> validWeekdays,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            BigDecimal netHours
    ) {}

    /** {@code capacity} is always null on a generated band (P-11) -- the editor and generation both default it blank. */
    public record SuggestedBand(int offsetMinutes, int durationMinutes, Integer capacity) {}
}
