package com.wfm.controller;

import com.wfm.dto.ErlangCCalculationRequest;
import com.wfm.dto.ErlangCalculationResponse;
import com.wfm.dto.ErlangXCalculationRequest;
import com.wfm.dto.ErrorResponse;
import com.wfm.exception.UnprocessableException;
import com.wfm.service.ErlangCalculatorService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ErlangCalculatorController} in the shape this package already uses for web-layer tests
 * (see {@link ConstraintWeightsControllerTest}): plain instantiation with a mocked service, no
 * Spring context and no MockMvc.
 *
 * <p>Two things are worth pinning beyond the passthrough. First, the path: the calculator must stay
 * off {@code /desks/{deskId}}, because that prefix belongs to the endpoints that persist. Second,
 * the failure mapping: both exception types the service raises must reach the operator as a 4xx,
 * since every one of them is caused by what was typed.
 */
class ErlangCalculatorControllerTest {

    private final ErlangCalculatorService service = mock(ErlangCalculatorService.class);
    private final ErlangCalculatorController controller = new ErlangCalculatorController(service);
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private static ErlangCalculationResponse someAnswer(String model) {
        return new ErlangCalculationResponse(model, 14, 14, 20, 0, 6,
                10.0, 10.0, 0.888, 0.449, 0.714, 0.714, null, 12.3, null);
    }

    @Test
    @DisplayName("the Erlang C endpoint passes the request through and returns the answer unchanged")
    void erlangCPassthrough() {
        ErlangCCalculationRequest request =
                new ErlangCCalculationRequest(100, 30, 180, 0.80, 20, null);
        ErlangCalculationResponse expected = someAnswer("ERLANG_C");
        when(service.calculateErlangC(request)).thenReturn(expected);

        assertThat(controller.calculateErlangC(request)).isSameAs(expected);
        verify(service).calculateErlangC(request);
    }

    @Test
    @DisplayName("the Erlang X endpoint passes the request through and returns the answer unchanged")
    void erlangXPassthrough() {
        ErlangXCalculationRequest request =
                new ErlangXCalculationRequest(100, 30, 180, 90, 0.25, 0.80, 20, null, null);
        ErlangCalculationResponse expected = someAnswer("ERLANG_X");
        when(service.calculateErlangX(request)).thenReturn(expected);

        assertThat(controller.calculateErlangX(request)).isSameAs(expected);
        verify(service).calculateErlangX(request);
    }

    @Test
    @DisplayName("the calculator is not mounted under a desk, because it writes nothing")
    void pathIsDeskIndependent() {
        String[] paths = ErlangCalculatorController.class
                .getAnnotation(RequestMapping.class).value();

        assertThat(paths).containsExactly("/api/v1/calc");
        // A desk-scoped path would imply the stored-requirements semantics of
        // POST /desks/{deskId}/staffing-requirements/erlang-x, which deletes before it inserts.
        assertThat(paths[0]).doesNotContain("desks");
    }

    @Test
    @DisplayName("a bad number reaches the caller as 400, an impossible one as 422")
    void failuresMapToTheCaller() {
        when(service.calculateErlangC(any()))
                .thenThrow(new IllegalArgumentException("volume must be zero or more: -1.0"));
        assertThatThrownBy(() -> controller.calculateErlangC(
                new ErlangCCalculationRequest(-1, 30, 180, 0.80, 20, null)))
                .isInstanceOf(IllegalArgumentException.class);

        ResponseEntity<ErrorResponse> badRequest = handler.handleIllegalArgument(
                new IllegalArgumentException("volume must be zero or more: -1.0"));
        assertThat(badRequest.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(badRequest.getBody().error().code()).isEqualTo("VALIDATION_FAILED");

        ResponseEntity<ErrorResponse> unprocessable = handler.handleUnprocessable(
                new UnprocessableException("Offered load of 600000 Erlangs exceeds the 2000 this "
                        + "calculator will search", List.of("Check that volume is per interval")));
        assertThat(unprocessable.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(unprocessable.getBody().error().details()).hasSize(1);
    }
}
