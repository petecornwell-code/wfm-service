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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
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

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        // G-15-26: a wrong HTTP verb used to fall through to handleUncaught below and come back
        // as 500 -- a plain client mistake read as a wedged desk, which cost an unnecessary ECS
        // force-new-deployment. This handler is placed above the catch-all (handler specificity,
        // not ordering, is what actually resolves it -- Spring dispatches to the most specific
        // matching handler regardless of declaration order -- but the file's existing
        // narrow-to-broad reading order is kept for the next reader).
        //
        // The message and Allow header are built ONLY from ex.getSupportedMethods() -- the
        // server's own knowledge of the endpoint's supported verbs. The client-supplied verb
        // (ex.getMethod()) is deliberately never echoed, following this file's own
        // handleTypeMismatch T-13-25/26 precedent: naming what IS allowed is sufficient for the
        // caller to self-diagnose, and reflecting an attacker-controlled token back is unnecessary
        // risk (T-15-16-01).
        String[] supportedMethods = ex.getSupportedMethods();
        boolean hasSupportedMethods = supportedMethods != null && supportedMethods.length > 0;
        String allowValue = hasSupportedMethods ? String.join(", ", supportedMethods) : null;
        String message = hasSupportedMethods
                ? "Method not allowed. Supported methods: " + allowValue
                : "Method not allowed.";

        ResponseEntity.BodyBuilder builder = ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED);
        if (allowValue != null) {
            // Only set the Allow header when there is a non-empty value to set -- an empty
            // supported-methods report must still return 405 without emitting a malformed
            // (empty-valued) Allow header. A single comma-joined value (not one header entry per
            // method) so HttpHeaders.getAllow() -- which reads only the FIRST Allow value and
            // tokenizes it -- sees every supported method, not just the first.
            builder = builder.header(HttpHeaders.ALLOW, allowValue);
        }
        return builder.body(new ErrorResponse(new Error("METHOD_NOT_ALLOWED", message, List.of())));
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
