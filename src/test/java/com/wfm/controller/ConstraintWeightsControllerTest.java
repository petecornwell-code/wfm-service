package com.wfm.controller;

import com.wfm.dto.ConstraintWeightsDto;
import com.wfm.dto.ConstraintWeightsDto.ScoreDto;
import com.wfm.dto.ErrorResponse;
import com.wfm.service.ConstraintWeightsService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 17 plan 17-02, Task 3 — the second from-scratch web-layer test class this plan adds.
 * Follows {@link GlobalExceptionHandlerTest}'s shape (P-17: no Spring context, no MockMvc), not
 * {@code @WebMvcTest}: plain instantiation of {@link ConstraintWeightsController} with a mocked
 * {@link ConstraintWeightsService}.
 *
 * <p>{@link ConstraintWeightsController} itself is unchanged by this plan — it is already a
 * two-method passthrough with no validation of its own. This test class proves that passthrough
 * shape (arguments and results flow unchanged) and proves the {@code IllegalArgumentException}
 * plan 17-02's {@code ConstraintWeightsService.updateWeights} now throws (D-07/D-08/T-17-04)
 * propagates out of the controller uncaught, so {@link GlobalExceptionHandler
 * #handleIllegalArgument} maps it to 400 {@code VALIDATION_FAILED} — verified here directly
 * against the handler, mirroring {@link GlobalExceptionHandlerTest}'s own assertions.
 */
class ConstraintWeightsControllerTest {

    private final ConstraintWeightsService service = mock(ConstraintWeightsService.class);
    private final ConstraintWeightsController controller = new ConstraintWeightsController(service);

    @Test
    void getWeights_passesDeskIdThroughAndReturnsTheServiceResultUnchanged() {
        UUID deskId = UUID.randomUUID();
        ConstraintWeightsDto expected = new ConstraintWeightsDto();
        expected.setConsistentStartWeight(new ScoreDto(0, 5));
        when(service.getWeights(deskId)).thenReturn(expected);

        ConstraintWeightsDto result = controller.getWeights(deskId);

        assertThat(result).isSameAs(expected);
        verify(service).getWeights(deskId);
    }

    @Test
    void updateWeights_passesDeskIdAndDtoThroughAndReturnsTheServiceResultUnchanged() {
        UUID deskId = UUID.randomUUID();
        ConstraintWeightsDto updates = new ConstraintWeightsDto();
        updates.setConsistencyToleranceMinutes(30);
        ConstraintWeightsDto expected = new ConstraintWeightsDto();
        expected.setConsistencyToleranceMinutes(30);
        when(service.updateWeights(eq(deskId), any(ConstraintWeightsDto.class))).thenReturn(expected);

        ConstraintWeightsDto result = controller.updateWeights(deskId, updates);

        assertThat(result).isSameAs(expected);
        verify(service).updateWeights(deskId, updates);
    }

    @Test
    void updateWeights_propagatesTheServicesIllegalArgumentExceptionUncaught() {
        UUID deskId = UUID.randomUUID();
        ConstraintWeightsDto updates = new ConstraintWeightsDto();
        String message = "Usual Shift Consistency's hard score must be 0 — a hard score would "
                + "risk making an otherwise-feasible schedule infeasible.";
        when(service.updateWeights(eq(deskId), any(ConstraintWeightsDto.class)))
                .thenThrow(new IllegalArgumentException(message));

        assertThatThrownBy(() -> controller.updateWeights(deskId, updates))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(message);
    }

    @Test
    void theControllersIllegalArgumentException_mapsTo400ValidationFailedThroughTheGlobalHandler() {
        // Mirrors GlobalExceptionHandlerTest's own preExistingMappings assertion -- proves the
        // 400/VALIDATION_FAILED mapping this controller relies on (it declares no @ExceptionHandler
        // of its own) actually exists and produces the right status and code.
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        String message = "Preferred Start (Shift Mode) weight must be lower than Usual Shift "
                + "Consistency's weight.";

        ResponseEntity<ErrorResponse> response =
                handler.handleIllegalArgument(new IllegalArgumentException(message));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error().code()).isEqualTo("VALIDATION_FAILED");
        assertThat(response.getBody().error().message()).isEqualTo(message);
    }
}
