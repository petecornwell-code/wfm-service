package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.controller.DeskAgentController;
import com.wfm.dto.RestWaiverResponse;
import com.wfm.exception.EntityNotFoundException;
import com.wfm.integration.BambooRefreshService;
import com.wfm.model.Agent;
import com.wfm.model.AgentRestWaiver;
import com.wfm.repository.AgentDayOffRepository;
import com.wfm.repository.AgentRepository;
import com.wfm.repository.AgentRestWaiverRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 22 plan 22-03, Task 1 — {@link RestWaiverService}'s validation, upsert-by-natural-key,
 * three-key agent resolution, and the explicitly-absent day-off refusal (D-07).
 *
 * <p>Plain {@code @ExtendWith(MockitoExtension.class)} unit test with mocked
 * {@link AgentRestWaiverRepository} and {@link AgentRepository}, mirroring
 * {@code ConstraintWeightsServiceTest}'s wiring idiom — no {@code @DataJpaTest}, no Spring context.
 */
@ExtendWith(MockitoExtension.class)
class RestWaiverServiceTest {

    private static final long TENANT_ID = 1L;
    private static final UUID DESK_ID = UUID.randomUUID();
    private static final UUID AGENT_ID = UUID.randomUUID();
    private static final LocalDate DATE = LocalDate.of(2026, 10, 12);

    @Mock
    private AgentRestWaiverRepository agentRestWaiverRepository;

    @Mock
    private AgentRepository agentRepository;

    private RestWaiverService service;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_ID);
        service = new RestWaiverService(agentRestWaiverRepository, agentRepository);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private static Agent agent() {
        Agent agent = new Agent();
        agent.setId(AGENT_ID);
        agent.setTenantId(TENANT_ID);
        agent.setDeskId(DESK_ID);
        return agent;
    }

    private void givenAgentOnDesk() {
        when(agentRepository.findByIdAndTenantIdAndDeskId(AGENT_ID, TENANT_ID, DESK_ID))
                .thenReturn(Optional.of(agent()));
    }

    private void stubSaveReturnsItsArgumentWithGeneratedId() {
        when(agentRestWaiverRepository.save(any(AgentRestWaiver.class))).thenAnswer(invocation -> {
            AgentRestWaiver entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(UUID.randomUUID());
            }
            return entity;
        });
    }

    // ---------- saveWaivers ----------

    @Test
    void saveWaivers_newWaiver_persistsAndReturnsItWithGeneratedId() {
        givenAgentOnDesk();
        when(agentRestWaiverRepository.findByTenantIdAndDeskIdAndAgent_IdAndDate(
                TENANT_ID, DESK_ID, AGENT_ID, DATE)).thenReturn(Optional.empty());
        stubSaveReturnsItsArgumentWithGeneratedId();

        List<RestWaiverResponse> result = service.saveWaivers(DESK_ID, AGENT_ID,
                List.of(new RestWaiverResponse(null, DATE, "Late cover for X")));

        assertThat(result).hasSize(1);
        RestWaiverResponse saved = result.get(0);
        assertThat(saved.id()).isNotNull();
        assertThat(saved.date()).isEqualTo(DATE);
        assertThat(saved.reason()).isEqualTo("Late cover for X");
    }

    @Test
    void saveWaivers_nullDate_throwsIllegalArgumentExceptionNamingDateField() {
        givenAgentOnDesk();

        assertThatThrownBy(() -> service.saveWaivers(DESK_ID, AGENT_ID,
                List.of(new RestWaiverResponse(null, null, "Late cover for X"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("date");
    }

    @Test
    void saveWaivers_nullReason_throwsIllegalArgumentExceptionNamingReasonField() {
        givenAgentOnDesk();

        assertThatThrownBy(() -> service.saveWaivers(DESK_ID, AGENT_ID,
                List.of(new RestWaiverResponse(null, DATE, null))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reason");
    }

    @Test
    void saveWaivers_blankReason_throwsIllegalArgumentExceptionNamingReasonField() {
        givenAgentOnDesk();

        assertThatThrownBy(() -> service.saveWaivers(DESK_ID, AGENT_ID,
                List.of(new RestWaiverResponse(null, DATE, "   "))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reason");
    }

    @Test
    void saveWaivers_calledTwiceForSameAgentDate_updatesReasonInPlaceRatherThanCreatingASecondRow() {
        givenAgentOnDesk();
        AgentRestWaiver existing = new AgentRestWaiver();
        existing.setId(UUID.randomUUID());
        existing.setTenantId(TENANT_ID);
        existing.setDeskId(DESK_ID);
        existing.setAgent(agent());
        existing.setDate(DATE);
        existing.setReason("First reason");

        when(agentRestWaiverRepository.findByTenantIdAndDeskIdAndAgent_IdAndDate(
                TENANT_ID, DESK_ID, AGENT_ID, DATE))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existing));
        stubSaveReturnsItsArgumentWithGeneratedId();

        service.saveWaivers(DESK_ID, AGENT_ID, List.of(new RestWaiverResponse(null, DATE, "First reason")));
        List<RestWaiverResponse> secondResult = service.saveWaivers(DESK_ID, AGENT_ID,
                List.of(new RestWaiverResponse(null, DATE, "Second reason")));

        ArgumentCaptor<AgentRestWaiver> captor = ArgumentCaptor.forClass(AgentRestWaiver.class);
        verify(agentRestWaiverRepository, times(2)).save(captor.capture());

        List<AgentRestWaiver> savedEntities = captor.getAllValues();
        assertThat(savedEntities.get(1).getId()).isEqualTo(existing.getId());
        assertThat(savedEntities.get(1).getReason()).isEqualTo("Second reason");
        assertThat(secondResult).hasSize(1);
        assertThat(secondResult.get(0).reason()).isEqualTo("Second reason");
    }

    @Test
    void saveWaivers_agentNotOnDesk_throwsEntityNotFoundException() {
        when(agentRepository.findByIdAndTenantIdAndDeskId(AGENT_ID, TENANT_ID, DESK_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.saveWaivers(DESK_ID, AGENT_ID,
                List.of(new RestWaiverResponse(null, DATE, "Late cover for X"))))
                .isInstanceOf(EntityNotFoundException.class);

        verify(agentRestWaiverRepository, never()).save(any());
    }

    /**
     * The deliberately omitted refusal (D-07): a rest waiver on a date the agent has off must
     * succeed with no conflict, because there is no shift to rest from on that date. There is no
     * day-off repository wired into this service at all, so any successful save on an arbitrary
     * date — day-off or not — proves this behaviorally; {@link #declaresNoAgentDayOffRepositoryField_structural()}
     * proves it cannot regress structurally.
     */
    @Test
    void saveWaivers_onDateAgentHasOff_succeedsWithNoConflictNoException() {
        givenAgentOnDesk();
        when(agentRestWaiverRepository.findByTenantIdAndDeskIdAndAgent_IdAndDate(
                TENANT_ID, DESK_ID, AGENT_ID, DATE)).thenReturn(Optional.empty());
        stubSaveReturnsItsArgumentWithGeneratedId();

        assertThatCode(() -> service.saveWaivers(DESK_ID, AGENT_ID,
                List.of(new RestWaiverResponse(null, DATE, "Covering a day-off shift"))))
                .doesNotThrowAnyException();
    }

    /**
     * Structural: {@link RestWaiverService} must declare no field assignable from
     * {@link AgentDayOffRepository} — the Phase 10 D-16 guard precedent, used here because the
     * collaborator does not exist on this service to verify against with a mock.
     */
    @Test
    void declaresNoAgentDayOffRepositoryField_structural() {
        boolean hasField = Arrays.stream(RestWaiverService.class.getDeclaredFields())
                .anyMatch((Field f) -> AgentDayOffRepository.class.isAssignableFrom(f.getType()));
        assertThat(hasField)
                .as("RestWaiverService must not depend on AgentDayOffRepository")
                .isFalse();
    }

    // ---------- listWaivers ----------

    @Test
    void listWaivers_returnsThatAgentsWaiversOnThatDesk() {
        AgentRestWaiver waiver = new AgentRestWaiver();
        waiver.setId(UUID.randomUUID());
        waiver.setDate(DATE);
        waiver.setReason("Late cover for X");
        when(agentRestWaiverRepository.findByTenantIdAndDeskIdAndAgent_Id(TENANT_ID, DESK_ID, AGENT_ID))
                .thenReturn(List.of(waiver));

        List<RestWaiverResponse> result = service.listWaivers(DESK_ID, AGENT_ID, null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).date()).isEqualTo(DATE);
        assertThat(result.get(0).reason()).isEqualTo("Late cover for X");
    }

    @Test
    void listWaivers_withFromAndTo_returnsOnlyWaiversInTheInclusiveRange() {
        LocalDate from = LocalDate.of(2026, 10, 1);
        LocalDate to = LocalDate.of(2026, 10, 31);
        AgentRestWaiver inRange = new AgentRestWaiver();
        inRange.setId(UUID.randomUUID());
        inRange.setDate(DATE);
        inRange.setReason("In range");
        when(agentRestWaiverRepository.findByTenantIdAndDeskIdAndAgent_IdAndDateBetween(
                TENANT_ID, DESK_ID, AGENT_ID, from, to)).thenReturn(List.of(inRange));

        List<RestWaiverResponse> result = service.listWaivers(DESK_ID, AGENT_ID,
                from.toString(), to.toString());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).reason()).isEqualTo("In range");
        verify(agentRestWaiverRepository, never())
                .findByTenantIdAndDeskIdAndAgent_Id(anyLong(), any(UUID.class), any(UUID.class));
    }

    // ---------- deleteWaiver ----------

    @Test
    void deleteWaiver_removesTheRow() {
        AgentRestWaiver existing = new AgentRestWaiver();
        existing.setId(UUID.randomUUID());
        existing.setDate(DATE);
        when(agentRestWaiverRepository.findByTenantIdAndDeskIdAndAgent_IdAndDate(
                TENANT_ID, DESK_ID, AGENT_ID, DATE)).thenReturn(Optional.of(existing));

        service.deleteWaiver(DESK_ID, AGENT_ID, DATE);

        verify(agentRestWaiverRepository).delete(existing);
    }

    @Test
    void deleteWaiver_nonExistentDate_throwsEntityNotFoundExceptionRatherThanSucceedingSilently() {
        when(agentRestWaiverRepository.findByTenantIdAndDeskIdAndAgent_IdAndDate(
                TENANT_ID, DESK_ID, AGENT_ID, DATE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteWaiver(DESK_ID, AGENT_ID, DATE))
                .isInstanceOf(EntityNotFoundException.class);

        verify(agentRestWaiverRepository, never()).delete(any());
    }

    // ---------- DeskAgentController delegation (Task 2) ----------
    //
    // No MockMvc harness here, deliberately: this package's existing guard tests (e.g.
    // ConstraintWeightsControllerTest) prove delegation by direct handler invocation against a
    // controller built with mocked collaborators, and 22-RESEARCH.md records no need for a new
    // test harness for this controller.

    private static DeskAgentController controllerWithMockedRestWaiverService(RestWaiverService restWaiverService) {
        return new DeskAgentController(
                mock(DeskAgentService.class),
                mock(DeskAgentExportService.class),
                mock(AgentPreferenceService.class),
                mock(AgentExceptionService.class),
                restWaiverService,
                mock(BambooRefreshService.class),
                mock(PreferenceUploadService.class),
                mock(UsualShiftService.class));
    }

    @Test
    void listRestWaivers_delegatesToRestWaiverServiceWithPathVariablesInOrder() {
        RestWaiverService mockService = mock(RestWaiverService.class);
        DeskAgentController controller = controllerWithMockedRestWaiverService(mockService);
        List<RestWaiverResponse> expected = List.of(new RestWaiverResponse(UUID.randomUUID(), DATE, "reason"));
        when(mockService.listWaivers(DESK_ID, AGENT_ID, "2026-10-01", "2026-10-31")).thenReturn(expected);

        List<RestWaiverResponse> result = controller.listRestWaivers(DESK_ID, AGENT_ID, "2026-10-01", "2026-10-31");

        assertThat(result).isSameAs(expected);
        verify(mockService).listWaivers(DESK_ID, AGENT_ID, "2026-10-01", "2026-10-31");
    }

    @Test
    void saveRestWaivers_delegatesToRestWaiverServiceWithPathVariablesAndBody() {
        RestWaiverService mockService = mock(RestWaiverService.class);
        DeskAgentController controller = controllerWithMockedRestWaiverService(mockService);
        List<RestWaiverResponse> body = List.of(new RestWaiverResponse(null, DATE, "Late cover for X"));
        List<RestWaiverResponse> expected = List.of(new RestWaiverResponse(UUID.randomUUID(), DATE, "Late cover for X"));
        when(mockService.saveWaivers(DESK_ID, AGENT_ID, body)).thenReturn(expected);

        List<RestWaiverResponse> result = controller.saveRestWaivers(DESK_ID, AGENT_ID, body);

        assertThat(result).isSameAs(expected);
        verify(mockService).saveWaivers(DESK_ID, AGENT_ID, body);
    }

    @Test
    void deleteRestWaiver_delegatesToRestWaiverServiceWithPathVariablesAndDate() {
        RestWaiverService mockService = mock(RestWaiverService.class);
        DeskAgentController controller = controllerWithMockedRestWaiverService(mockService);

        controller.deleteRestWaiver(DESK_ID, AGENT_ID, DATE);

        verify(mockService).deleteWaiver(DESK_ID, AGENT_ID, DATE);
    }
}
