package com.wfm.controller;

import com.wfm.dto.ErrorResponse;
import com.wfm.dto.ErrorResponse.Error;
import com.wfm.dto.ErrorResponse.ErrorDetail;
import com.wfm.exception.BambooHRRateLimitedException;
import com.wfm.exception.BambooHRSyncFailedException;
import com.wfm.exception.ConflictException;
import com.wfm.exception.EntityNotFoundException;
import com.wfm.exception.PreSolveValidationException;
import com.wfm.exception.RefreshInProgressException;
import com.wfm.exception.UnprocessableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", ex.getMessage(), List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        List<ErrorDetail> details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ErrorDetail(fe.getField(), fe.getDefaultMessage(),
                        fe.getRejectedValue() != null ? fe.getRejectedValue().toString() : null))
                .toList();
        return buildResponse(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed", details);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Malformed request body", List.of());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        // Required because handleUncaught below would otherwise return a 500 for a mistyped
        // path segment (e.g. an unrecognised {day} value) -- Spring's enum path-variable
        // converter throws this before any handler method runs (WR-02). The message names only
        // the parameter declared in our own controller signature: it is not attacker-controlled,
        // unlike the rejected token or the internal target type, neither of which is echoed
        // (T-13-25/26).
        return buildResponse(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                "Invalid value for path parameter '" + ex.getName() + "'", List.of());
    }

    @ExceptionHandler(PreSolveValidationException.class)
    public ResponseEntity<ErrorResponse> handlePreSolveValidation(PreSolveValidationException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", ex.getMessage(), ex.getDetails());
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(EntityNotFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage(), List.of());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConflictException ex) {
        return buildResponse(HttpStatus.CONFLICT, "CONFLICT", ex.getMessage(), List.of());
    }

    @ExceptionHandler(RefreshInProgressException.class)
    public ResponseEntity<ErrorResponse> handleRefreshInProgress(RefreshInProgressException ex) {
        return buildResponse(HttpStatus.CONFLICT, "REFRESH_IN_PROGRESS", ex.getMessage(), List.of());
    }

    @ExceptionHandler(BambooHRRateLimitedException.class)
    public ResponseEntity<ErrorResponse> handleBambooHRRateLimited(BambooHRRateLimitedException ex) {
        return buildResponse(HttpStatus.SERVICE_UNAVAILABLE, "BAMBOOHR_RATE_LIMITED", ex.getMessage(), List.of());
    }

    @ExceptionHandler(BambooHRSyncFailedException.class)
    public ResponseEntity<ErrorResponse> handleBambooHRSyncFailed(BambooHRSyncFailedException ex) {
        // Required because handleUncaught below replaces any message with a fixed string --
        // without this dedicated handler a timeout or a BambooHR 500 during an upload-triggered
        // sync would reach the operator with no reason at all (MRG-07).
        return buildResponse(HttpStatus.SERVICE_UNAVAILABLE, "BAMBOOHR_SYNC_FAILED", ex.getMessage(), List.of());
    }

    @ExceptionHandler(UnprocessableException.class)
    public ResponseEntity<ErrorResponse> handleUnprocessable(UnprocessableException ex) {
        List<ErrorDetail> details = ex.getDetails().stream()
                .map(d -> new ErrorDetail(null, d, null))
                .toList();
        return buildResponse(HttpStatus.UNPROCESSABLE_ENTITY, "UNPROCESSABLE_ENTITY", ex.getMessage(), details);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUncaught(Exception ex) {
        log.error("Unhandled exception", ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred", List.of());
    }

    private ResponseEntity<ErrorResponse> buildResponse(HttpStatus status, String code,
                                                         String message, List<ErrorDetail> details) {
        return ResponseEntity.status(status)
                .body(new ErrorResponse(new Error(code, message, details)));
    }
}
