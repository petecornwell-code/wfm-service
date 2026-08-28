package com.wfm.service;

import com.wfm.dto.ErrorResponse.ErrorDetail;
import com.wfm.dto.ShiftLibraryValidationResponse;
import com.wfm.dto.ShiftLibraryValidationResponse.CapacityAdvisory;
import com.wfm.model.SchedulingMode;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Phase 15 plan 15-06, Task 3 (P-29) — {@link SolverService#appendBandCapacityErrors}, the
 * SolverService-level proof that the refusal reuses {@link ShiftLibraryValidationService#validate}
 * rather than re-deriving the capacity-shortfall computation: the message a shift-mode desk's
 * pre-solve refusal carries is character-identical to the advisory the shift-library report emits
 * for the same data, because both come from the same {@link CapacityAdvisory#message()}.
 *
 * <p>Tests call the package-private static helper directly with a Mockito mock of {@link
 * ShiftLibraryValidationService} — no Spring context, no solve, mirroring {@code
 * SolverServiceEligibilityFilterTest}'s precedent for testing a SolverService collaborator in
 * isolation.
 */
class SolverServiceBandCapacityRefusalTest {

    private static final UUID DESK_ID = UUID.randomUUID();
    private static final UUID TEMPLATE_ID = UUID.randomUUID();

    @Test
    void shiftModeDeskWithCapacityShortfall_appendsABandCapacityErrorDetail_messageCharacterIdenticalToTheAdvisory() {
        String advisoryMessage = "Shift template 'Early' has total break-band capacity 4 on Monday, "
                + "but 6 agent(s) could be scheduled on it that day. Increase capacity or add a band before solving.";
        CapacityAdvisory advisory = new CapacityAdvisory(TEMPLATE_ID, "Early", DayOfWeek.MONDAY, 4, 6, advisoryMessage);
        ShiftLibraryValidationService validationService = mock(ShiftLibraryValidationService.class);
        when(validationService.validate(DESK_ID)).thenReturn(new ShiftLibraryValidationResponse(
                true, List.of(), List.of(), List.of(), List.of(), List.of(advisory), List.of()));

        List<ErrorDetail> errors = new ArrayList<>();
        SolverService.appendBandCapacityErrors(SchedulingMode.SHIFT, DESK_ID, validationService, errors);

        assertThat(errors).hasSize(1);
        ErrorDetail detail = errors.get(0);
        assertThat(detail.field()).isEqualTo("bandCapacity");
        assertThat(detail.message())
                .as("the refusal's message must be character-identical to the save-time advisory "
                        + "the shift-library report already renders for the same data (D-08 extended)")
                .isEqualTo(advisoryMessage);
        assertThat(detail.value()).isEqualTo("Early");
    }

    @Test
    void shiftModeDeskWithMultipleShortfalls_appendsOneErrorDetailPerAdvisory() {
        CapacityAdvisory advisory1 = new CapacityAdvisory(TEMPLATE_ID, "Early", DayOfWeek.MONDAY, 4, 6, "msg1");
        CapacityAdvisory advisory2 = new CapacityAdvisory(UUID.randomUUID(), "Late", DayOfWeek.TUESDAY, 2, 5, "msg2");
        ShiftLibraryValidationService validationService = mock(ShiftLibraryValidationService.class);
        when(validationService.validate(DESK_ID)).thenReturn(new ShiftLibraryValidationResponse(
                true, List.of(), List.of(), List.of(), List.of(), List.of(advisory1, advisory2), List.of()));

        List<ErrorDetail> errors = new ArrayList<>();
        SolverService.appendBandCapacityErrors(SchedulingMode.SHIFT, DESK_ID, validationService, errors);

        assertThat(errors).hasSize(2);
        assertThat(errors).extracting(ErrorDetail::message).containsExactly("msg1", "msg2");
    }

    @Test
    void slotModeDesk_neverCallsValidate_noErrorsAppended() {
        ShiftLibraryValidationService validationService = mock(ShiftLibraryValidationService.class);

        List<ErrorDetail> errors = new ArrayList<>();
        SolverService.appendBandCapacityErrors(SchedulingMode.SLOT, DESK_ID, validationService, errors);

        assertThat(errors).isEmpty();
    }

    @Test
    void shiftModeDeskWithNoShortfall_noErrorsAppended() {
        ShiftLibraryValidationService validationService = mock(ShiftLibraryValidationService.class);
        when(validationService.validate(DESK_ID)).thenReturn(new ShiftLibraryValidationResponse(
                true, List.of(), List.of(), List.of(), List.of(), List.of(), List.of()));

        List<ErrorDetail> errors = new ArrayList<>();
        SolverService.appendBandCapacityErrors(SchedulingMode.SHIFT, DESK_ID, validationService, errors);

        assertThat(errors).isEmpty();
    }
}
