package com.wfm.exception;

import com.wfm.dto.ErrorResponse.ErrorDetail;

import java.util.List;

/**
 * Thrown when pre-solve validation fails. Carries structured field-level details
 * so the client can display all issues at once.
 */
public class PreSolveValidationException extends RuntimeException {

    private final List<ErrorDetail> details;

    public PreSolveValidationException(String message, List<ErrorDetail> details) {
        super(message);
        this.details = details;
    }

    public List<ErrorDetail> getDetails() {
        return details;
    }
}
