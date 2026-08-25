package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.dto.ErrorResponse.ErrorDetail;
import com.wfm.dto.ShiftTemplateRequest;
import com.wfm.dto.TimeslotBoundsResponse;
import com.wfm.exception.ConflictException;
import com.wfm.exception.EntityNotFoundException;
import com.wfm.exception.PreSolveValidationException;
import com.wfm.model.Desk;
import com.wfm.model.ShiftTemplate;
import com.wfm.repository.DeskRepository;
import com.wfm.repository.ShiftTemplateRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;

/**
 * Full validation, identity, non-overlap, grid-alignment and retirement coverage for
 * ShiftTemplateService (14-03). TimeslotGeneratorService.getLiveBounds runs a Postgres-only
 * native query (EXTRACT(EPOCH FROM ...)) that cannot execute under H2, so it is supplied here
 * as a @MockitoBean and stubbed per test rather than the real bean.
 */
@DataJpaTest
@Import(ShiftTemplateService.class)
@ActiveProfiles("test")
class ShiftTemplateServiceTest {

    @Autowired
    private ShiftTemplateService service;

    @Autowired
    private ShiftTemplateRepository shiftTemplateRepository;

    @Autowired
    private DeskRepository deskRepository;

    @MockitoBean
    private TimeslotGeneratorService timeslotGeneratorService;

    private static final long TENANT_A = 1L;
    private static final long TENANT_B = 2L;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_A);
        when(timeslotGeneratorService.getLiveBounds(any())).thenReturn(Optional.empty());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ---------- Identity and eras (D-11) ----------

    @Test
    void create_duplicateNameAndEffectiveFrom_throwsConflictException() {
        UUID deskId = saveDesk(TENANT_A);
        service.createShiftTemplate(deskId, request("S1", LocalDate.of(2026, 1, 1), null));

        assertThatThrownBy(() -> service.createShiftTemplate(deskId, request("S1", LocalDate.of(2026, 1, 1), null)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("S1")
                .hasMessageContaining("2026-01-01");
    }

    @Test
    void create_sameNameDifferentNonOverlappingEffectiveFrom_succeeds() {
        UUID deskId = saveDesk(TENANT_A);
        service.createShiftTemplate(deskId, request("S1", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30)));

        assertThatCode(() -> service.createShiftTemplate(deskId, request("S1", LocalDate.of(2026, 7, 1), null)))
                .doesNotThrowAnyException();
    }

    @Test
    void create_touchingEras_bothSaveSuccessfully() {
        UUID deskId = saveDesk(TENANT_A);
        service.createShiftTemplate(deskId, request("S1", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30)));

        assertThatCode(() -> service.createShiftTemplate(deskId, request("S1", LocalDate.of(2026, 7, 1), null)))
                .doesNotThrowAnyException();

        assertThat(shiftTemplateRepository.findByTenantIdAndDeskIdAndName(TENANT_A, deskId, "S1")).hasSize(2);
    }

    @Test
    void create_overlappingErasByOneDay_rejected() {
        UUID deskId = saveDesk(TENANT_A);
        service.createShiftTemplate(deskId, request("S1", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 7, 1)));

        assertThatThrownBy(() -> service.createShiftTemplate(deskId, request("S1", LocalDate.of(2026, 7, 1), null)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("S1")
                .hasMessageContaining("already has an effective range covering");
    }

    @Test
    void create_openEndedEraBlocksLaterEra_rejected() {
        UUID deskId = saveDesk(TENANT_A);
        service.createShiftTemplate(deskId, request("S1", LocalDate.of(2026, 1, 1), null));

        assertThatThrownBy(() -> service.createShiftTemplate(deskId, request("S1", LocalDate.of(2026, 7, 1), null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void create_namesDifferingByCase_areDistinct() {
        UUID deskId = saveDesk(TENANT_A);
        service.createShiftTemplate(deskId, request("S1", LocalDate.of(2026, 1, 1), null));

        assertThatCode(() -> service.createShiftTemplate(deskId, request("s1", LocalDate.of(2026, 1, 1), null)))
                .doesNotThrowAnyException();
    }

    @Test
    void update_toOwnExistingIdentity_doesNotCollideWithItself() {
        UUID deskId = saveDesk(TENANT_A);
        ShiftTemplate created = service.createShiftTemplate(deskId, request("S1", LocalDate.of(2026, 1, 1), null));

        assertThatCode(() -> service.updateShiftTemplate(deskId, created.getId(),
                request("S1", LocalDate.of(2026, 1, 1), null)))
                .doesNotThrowAnyException();
    }

    // ---------- Field validation ----------

    @Test
    void create_startTimeEqualsEndTime_rejected() {
        UUID deskId = saveDesk(TENANT_A);
        ShiftTemplateRequest req = new ShiftTemplateRequest("S1", LocalTime.of(8, 0), LocalTime.of(8, 0),
                0, 0, Set.of(DayOfWeek.MONDAY), LocalDate.of(2026, 1, 1), null);

        assertThatThrownBy(() -> service.createShiftTemplate(deskId, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Shift template end time must be after its start time");
    }

    @Test
    void create_endTimeBeforeStartTime_rejected() {
        UUID deskId = saveDesk(TENANT_A);
        ShiftTemplateRequest req = new ShiftTemplateRequest("S1", LocalTime.of(17, 0), LocalTime.of(8, 0),
                0, 0, Set.of(DayOfWeek.MONDAY), LocalDate.of(2026, 1, 1), null);

        assertThatThrownBy(() -> service.createShiftTemplate(deskId, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Shift template end time must be after its start time");
    }

    @Test
    void create_negativeBreakValues_rejected() {
        UUID deskId = saveDesk(TENANT_A);
        ShiftTemplateRequest req = new ShiftTemplateRequest("S1", LocalTime.of(8, 0), LocalTime.of(17, 0),
                -10, 30, Set.of(DayOfWeek.MONDAY), LocalDate.of(2026, 1, 1), null);

        assertThatThrownBy(() -> service.createShiftTemplate(deskId, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Shift template break offset and duration cannot be negative");
    }

    @Test
    void create_breakExceedsEnvelope_rejected() {
        UUID deskId = saveDesk(TENANT_A);
        ShiftTemplateRequest req = new ShiftTemplateRequest("S1", LocalTime.of(8, 0), LocalTime.of(9, 0),
                55, 30, Set.of(DayOfWeek.MONDAY), LocalDate.of(2026, 1, 1), null);

        assertThatThrownBy(() -> service.createShiftTemplate(deskId, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Shift template break must finish before the shift ends");
    }

    @Test
    void create_zeroDurationBreak_accepted_netHoursEqualsFullEnvelope() {
        UUID deskId = saveDesk(TENANT_A);
        ShiftTemplateRequest req = new ShiftTemplateRequest("S1", LocalTime.of(8, 0), LocalTime.of(12, 0),
                120, 0, Set.of(DayOfWeek.MONDAY), LocalDate.of(2026, 1, 1), null);

        ShiftTemplate created = service.createShiftTemplate(deskId, req);

        assertThat(created.getNetHours()).isEqualByComparingTo(new BigDecimal("4.00"));
    }

    @Test
    void create_nullWeekdaySet_rejected() {
        UUID deskId = saveDesk(TENANT_A);
        ShiftTemplateRequest req = new ShiftTemplateRequest("S1", LocalTime.of(8, 0), LocalTime.of(17, 0),
                0, 0, null, LocalDate.of(2026, 1, 1), null);

        assertThatThrownBy(() -> service.createShiftTemplate(deskId, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A shift template must be valid on at least one weekday");
    }

    @Test
    void create_emptyWeekdaySet_rejected() {
        UUID deskId = saveDesk(TENANT_A);
        ShiftTemplateRequest req = new ShiftTemplateRequest("S1", LocalTime.of(8, 0), LocalTime.of(17, 0),
                0, 0, Set.of(), LocalDate.of(2026, 1, 1), null);

        assertThatThrownBy(() -> service.createShiftTemplate(deskId, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A shift template must be valid on at least one weekday");
    }

    @Test
    void create_overlappingWeekdaySets_bothSucceed() {
        UUID deskId = saveDesk(TENANT_A);
        service.createShiftTemplate(deskId, new ShiftTemplateRequest("Morning", LocalTime.of(8, 0), LocalTime.of(12, 0),
                0, 0, Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY), LocalDate.of(2026, 1, 1), null));

        assertThatCode(() -> service.createShiftTemplate(deskId, new ShiftTemplateRequest("Afternoon",
                LocalTime.of(13, 0), LocalTime.of(17, 0), 0, 0,
                Set.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), LocalDate.of(2026, 1, 1), null)))
                .doesNotThrowAnyException();
    }

    @Test
    void create_nullEffectiveFrom_rejected() {
        UUID deskId = saveDesk(TENANT_A);
        ShiftTemplateRequest req = new ShiftTemplateRequest("S1", LocalTime.of(8, 0), LocalTime.of(17, 0),
                0, 0, Set.of(DayOfWeek.MONDAY), null, null);

        assertThatThrownBy(() -> service.createShiftTemplate(deskId, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Shift template effective from date is required");
    }

    @Test
    void create_effectiveToBeforeEffectiveFrom_rejected() {
        UUID deskId = saveDesk(TENANT_A);
        ShiftTemplateRequest req = new ShiftTemplateRequest("S1", LocalTime.of(8, 0), LocalTime.of(17, 0),
                0, 0, Set.of(DayOfWeek.MONDAY), LocalDate.of(2026, 6, 1), LocalDate.of(2026, 1, 1));

        assertThatThrownBy(() -> service.createShiftTemplate(deskId, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Shift template effective to date cannot be before its effective from date");
    }

    // ---------- Grid check (D-02) ----------

    @Test
    void create_offGridStartTime_rejectedWithStartTimeDetail() {
        UUID deskId = saveDesk(TENANT_A);
        when(timeslotGeneratorService.getLiveBounds(deskId)).thenReturn(Optional.of(
                new TimeslotBoundsResponse(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                        LocalTime.of(8, 0), LocalTime.of(20, 0), 30)));
        ShiftTemplateRequest req = new ShiftTemplateRequest("S1", LocalTime.of(8, 15), LocalTime.of(17, 0),
                0, 0, Set.of(DayOfWeek.MONDAY), LocalDate.of(2026, 1, 1), null);

        PreSolveValidationException ex = catchThrowableOfType(
                () -> service.createShiftTemplate(deskId, req), PreSolveValidationException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.getDetails()).extracting(ErrorDetail::field).contains("startTime");
        assertThat(ex.getDetails()).extracting(ErrorDetail::message)
                .anyMatch(m -> m.contains("30-minute schedule grid"));
    }

    @Test
    void create_offGridBreakStart_rejectedWithBreakStartTimeDetail() {
        UUID deskId = saveDesk(TENANT_A);
        when(timeslotGeneratorService.getLiveBounds(deskId)).thenReturn(Optional.of(
                new TimeslotBoundsResponse(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                        LocalTime.of(8, 0), LocalTime.of(20, 0), 30)));
        // start 08:00 (aligned), break offset 15 -> break start 08:15 (misaligned on a 30-min grid)
        ShiftTemplateRequest req = new ShiftTemplateRequest("S1", LocalTime.of(8, 0), LocalTime.of(17, 0),
                15, 30, Set.of(DayOfWeek.MONDAY), LocalDate.of(2026, 1, 1), null);

        PreSolveValidationException ex = catchThrowableOfType(
                () -> service.createShiftTemplate(deskId, req), PreSolveValidationException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.getDetails()).extracting(ErrorDetail::field).contains("breakStartTime");
    }

    @Test
    void create_onGridWithBreak_accepted() {
        UUID deskId = saveDesk(TENANT_A);
        when(timeslotGeneratorService.getLiveBounds(deskId)).thenReturn(Optional.of(
                new TimeslotBoundsResponse(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                        LocalTime.of(8, 0), LocalTime.of(20, 0), 30)));
        ShiftTemplateRequest req = new ShiftTemplateRequest("S1", LocalTime.of(8, 0), LocalTime.of(17, 0),
                240, 60, Set.of(DayOfWeek.MONDAY), LocalDate.of(2026, 1, 1), null);

        assertThatCode(() -> service.createShiftTemplate(deskId, req)).doesNotThrowAnyException();
    }

    @Test
    void create_boundsAbsent_offGridAccepted() {
        UUID deskId = saveDesk(TENANT_A);
        when(timeslotGeneratorService.getLiveBounds(deskId)).thenReturn(Optional.empty());
        ShiftTemplateRequest req = new ShiftTemplateRequest("S1", LocalTime.of(8, 15), LocalTime.of(17, 0),
                0, 0, Set.of(DayOfWeek.MONDAY), LocalDate.of(2026, 1, 1), null);

        assertThatCode(() -> service.createShiftTemplate(deskId, req)).doesNotThrowAnyException();
    }

    // ---------- Retirement and tenancy ----------

    @Test
    void update_effectiveToToToday_retiresTemplate_rowStillExists() {
        UUID deskId = saveDesk(TENANT_A);
        ShiftTemplate created = service.createShiftTemplate(deskId, request("S1", LocalDate.of(2026, 1, 1), null));

        ShiftTemplate retired = service.updateShiftTemplate(deskId, created.getId(),
                new ShiftTemplateRequest("S1", LocalTime.of(8, 0), LocalTime.of(17, 0), 240, 60,
                        Set.of(DayOfWeek.MONDAY), LocalDate.of(2026, 1, 1), LocalDate.now()));

        assertThat(retired.getEffectiveTo()).isEqualTo(LocalDate.now());
        assertThat(shiftTemplateRepository.findById(created.getId())).isPresent();
    }

    @Test
    void crossTenant_invisibleToListing_updateThrowsEntityNotFound() {
        UUID deskId = saveDesk(TENANT_A);
        ShiftTemplate created = service.createShiftTemplate(deskId, request("S1", LocalDate.of(2026, 1, 1), null));

        TenantContext.setTenantId(TENANT_B);
        assertThat(service.listShiftTemplates(deskId)).isEmpty();
        assertThatThrownBy(() -> service.updateShiftTemplate(deskId, created.getId(),
                request("S1", LocalDate.of(2026, 1, 1), null)))
                .isInstanceOf(EntityNotFoundException.class);
    }

    // ---------- helpers ----------

    private ShiftTemplateRequest request(String name, LocalDate effectiveFrom, LocalDate effectiveTo) {
        return new ShiftTemplateRequest(name, LocalTime.of(8, 0), LocalTime.of(17, 0), 240, 60,
                Set.of(DayOfWeek.MONDAY), effectiveFrom, effectiveTo);
    }

    private UUID saveDesk(long tenantId) {
        Desk desk = new Desk();
        desk.setTenantId(tenantId);
        desk.setName("Desk " + UUID.randomUUID());
        return deskRepository.save(desk).getId();
    }
}
