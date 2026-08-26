package com.wfm.controller;

import com.wfm.dto.ShiftLibraryValidationResponse;
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
 */
@RestController
@RequestMapping("/api/v1/desks/{deskId}/shift-library")
public class ShiftLibraryValidationController {

    private final ShiftLibraryValidationService shiftLibraryValidationService;

    public ShiftLibraryValidationController(ShiftLibraryValidationService shiftLibraryValidationService) {
        this.shiftLibraryValidationService = shiftLibraryValidationService;
    }

    @GetMapping("/validation")
    public ShiftLibraryValidationResponse validateShiftLibrary(@PathVariable UUID deskId) {
        return shiftLibraryValidationService.validate(deskId);
    }
}
