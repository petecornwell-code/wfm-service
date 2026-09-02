package com.wfm.controller;

import com.wfm.dto.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.DayOfWeek;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Direct unit coverage of GlobalExceptionHandler.handleTypeMismatch (WR-02, T-13-25/26):
 * proves the response the handler builds for a malformed path-variable conversion, and pins
 * that the pre-existing mappings did not change shape (P-19). No Spring context, no MockMvc --
 * this codebase has neither for the web layer (P-17), so this is a plain instantiation test.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleTypeMismatch_returns400WithParameterNameOnly() throws NoSuchMethodException {
        MethodParameter parameter = MethodParameter.forExecutable(
                DeskAgentController.class.getMethod("setDayHours", java.util.UUID.class,
                        java.util.UUID.class, DayOfWeek.class,
                        com.wfm.dto.SetDayHoursRequest.class),
                2);
        MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException(
                "notaday", DayOfWeek.class, "day", parameter,
                new IllegalArgumentException("no enum constant"));

        ResponseEntity<ErrorResponse> response = handler.handleTypeMismatch(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error().code()).isEqualTo("VALIDATION_FAILED");
        assertThat(response.getBody().error().message()).contains("day");
        assertThat(response.getBody().error().message()).doesNotContain("notaday");
        assertThat(response.getBody().error().message()).doesNotContain("DayOfWeek");
        assertThat(response.getBody().error().details()).isEmpty();
    }

    @Test
    void preExistingMappings_stillReturnTheirOriginalStatuses() {
        ResponseEntity<ErrorResponse> illegalArg =
                handler.handleIllegalArgument(new IllegalArgumentException("bad"));
        assertThat(illegalArg.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<ErrorResponse> notFound =
                handler.handleNotFound(new com.wfm.exception.EntityNotFoundException("missing"));
        assertThat(notFound.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<ErrorResponse> conflict =
                handler.handleConflict(new com.wfm.exception.ConflictException("conflict"));
        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // G-15-26 gap closure: adding the 405 handler must not disturb the catch-all -- a genuine
        // unhandled exception still returns 500 with its fixed, non-leaking string.
        ResponseEntity<ErrorResponse> uncaught =
                handler.handleUncaught(new RuntimeException("boom"));
        assertThat(uncaught.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(uncaught.getBody()).isNotNull();
        assertThat(uncaught.getBody().error().message()).isEqualTo("An unexpected error occurred");
    }

    // ------------------------------------------------------------------
    //  Task 3 (G-15-26 gap closure) — HttpRequestMethodNotSupportedException -> 405, not 500
    // ------------------------------------------------------------------

    @Test
    void handleMethodNotSupported_returns405WithAllowHeaderNamingSupportedMethods() {
        HttpRequestMethodNotSupportedException ex =
                new HttpRequestMethodNotSupportedException("DELETE", List.of("GET", "POST"));

        ResponseEntity<ErrorResponse> response = handler.handleMethodNotSupported(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getHeaders().getAllow()).contains(
                org.springframework.http.HttpMethod.GET, org.springframework.http.HttpMethod.POST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error().code()).isEqualTo("METHOD_NOT_ALLOWED");
        assertThat(response.getBody().error().message()).contains("GET").contains("POST");
    }

    @Test
    void handleMethodNotSupported_doesNotEchoTheClientSuppliedVerb() {
        // T-15-16-01 / T-13-25/26 precedent: the attempted verb is client-controlled and must
        // never be reflected into the response, unlike the server-derived supported methods.
        HttpRequestMethodNotSupportedException ex =
                new HttpRequestMethodNotSupportedException("PATCH", List.of("GET", "PUT"));

        ResponseEntity<ErrorResponse> response = handler.handleMethodNotSupported(ex);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error().message()).doesNotContain("PATCH");
    }

    @Test
    void handleMethodNotSupported_emptySupportedMethods_stillReturns405WithoutMalformedAllowHeader() {
        HttpRequestMethodNotSupportedException ex =
                new HttpRequestMethodNotSupportedException("GET", List.<String>of());

        ResponseEntity<ErrorResponse> response = handler.handleMethodNotSupported(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error().code()).isEqualTo("METHOD_NOT_ALLOWED");
        List<String> allowHeaderValues = response.getHeaders().get(HttpHeaders.ALLOW);
        if (allowHeaderValues != null) {
            assertThat(allowHeaderValues).noneMatch(String::isBlank);
        }
    }
}
