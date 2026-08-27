package com.wfm.controller;

import com.wfm.dto.ShiftLibrarySuggestionResponse;
import com.wfm.dto.ShiftLibraryValidationResponse;
import com.wfm.service.ShiftLibraryGenerationService;
import com.wfm.service.ShiftLibraryValidationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * The shift-library editor's read of {@link ShiftLibraryValidationService#validate} (D-08's
 * first caller — the mode-switch endpoint, D-08's second caller, is a separate resource because
 * a mode switch acts on the desk, not the library). Always returns 200 with the report, even when
 * the report carries blocking findings — this endpoint reports, it never refuses.
 *
 * <p>{@code /suggestion} (SHLB-07, D-11) sits beside the validation report an operator already
 * reads here: a stateless, read-only computation that performs no write and returns an editable
 * draft the operator can save through the existing {@code ShiftTemplateService} path unchanged.
 */
@RestController
@RequestMapping("/api/v1/desks/{deskId}/shift-library")
public class ShiftLibraryValidationController {

    private final ShiftLibraryValidationService shiftLibraryValidationService;
    private final ShiftLibraryGenerationService shiftLibraryGenerationService;

    public ShiftLibraryValidationController(ShiftLibraryValidationService shiftLibraryValidationService,
                                              ShiftLibraryGenerationService shiftLibraryGenerationService) {
        this.shiftLibraryValidationService = shiftLibraryValidationService;
        this.shiftLibraryGenerationService = shiftLibraryGenerationService;
    }

    @GetMapping("/validation")
    public ShiftLibraryValidationResponse validateShiftLibrary(@PathVariable UUID deskId) {
        return shiftLibraryValidationService.validate(deskId);
    }

    @GetMapping("/suggestion")
    public ShiftLibrarySuggestionResponse suggestShiftLibrary(@PathVariable UUID deskId) {
        return shiftLibraryGenerationService.generateSuggestion(deskId);
    }
}
