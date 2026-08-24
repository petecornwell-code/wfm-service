package com.wfm.controller;

import com.wfm.dto.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.DayOfWeek;

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

        ResponseEntity<ErrorResponse> uncaught =
                handler.handleUncaught(new RuntimeException("boom"));
        assertThat(uncaught.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(uncaught.getBody()).isNotNull();
        assertThat(uncaught.getBody().error().message()).isEqualTo("An unexpected error occurred");
    }
}
