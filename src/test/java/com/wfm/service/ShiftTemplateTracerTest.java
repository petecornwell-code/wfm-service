package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.controller.ShiftTemplateController;
import com.wfm.dto.ShiftTemplateRequest;
import com.wfm.dto.ShiftTemplateRequest.BreakBandRequest;
import com.wfm.dto.ShiftTemplateResponse;
import com.wfm.model.Desk;
import com.wfm.model.SchedulingMode;
import com.wfm.repository.DeskRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * End-to-end tracer: controller -> service -> repository -> H2 -> DTO, no mocked layer
 * (14-01-PLAN.md P-04 — this codebase has no MockMvc harness). Written RED-first per the plan's
 * tdd="true" flag: every method below must fail against a controller/service/repository/entity
 * that does not yet exist, then pass once the tracer slice is implemented.
 *
 * <p>Updated by Phase 15 Plan 01 (D-01): the tracer's break assertions now go through the band
 * list rather than the retired scalar break fields — this file is not itself a Phase 15 target
 * file, but it breaks to compile the moment ShiftTemplateRequest/Response change shape, so it is
 * mechanically ported here (Rule 1/3) alongside the rest of the promotion.
 */
@DataJpaTest
@Import({ShiftTemplateService.class, ShiftTemplateController.class, TimeslotGeneratorService.class})
@ActiveProfiles("test")
class ShiftTemplateTracerTest {

    @Autowired
    private ShiftTemplateController controller;

    @Autowired
    private DeskRepository deskRepository;

    private static final long TENANT_A = 1L;
    private static final long TENANT_B = 2L;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_A);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void createAndList_returnsSubmittedTemplate() {
        UUID deskId = saveDesk(TENANT_A);
        ShiftTemplateRequest request = new ShiftTemplateRequest(
                "S1", LocalTime.of(8, 0), LocalTime.of(17, 0),
                List.of(new BreakBandRequest(240, 60, null)),
                Set.of(DayOfWeek.MONDAY), LocalDate.of(2026, 1, 1), null);

        controller.createShiftTemplate(deskId, request);
        List<ShiftTemplateResponse> result = controller.listShiftTemplates(deskId);

        assertThat(result).hasSize(1);
        ShiftTemplateResponse response = result.get(0);
        assertThat(response.name()).isEqualTo("S1");
        assertThat(response.startTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(response.endTime()).isEqualTo(LocalTime.of(17, 0));
        assertThat(response.validWeekdays()).containsExactly(DayOfWeek.MONDAY);
    }

    @Test
    void create_derivesBreakWindowAndNetHours() {
        // D-01's worked example: 08:00-17:00, offset 240 (break at 12:00), duration 60 -> net 8h.
        UUID deskId = saveDesk(TENANT_A);
        ShiftTemplateRequest request = new ShiftTemplateRequest(
                "S1", LocalTime.of(8, 0), LocalTime.of(17, 0),
                List.of(new BreakBandRequest(240, 60, null)),
                Set.of(DayOfWeek.MONDAY), LocalDate.of(2026, 1, 1), null);

        ShiftTemplateResponse response = controller.createShiftTemplate(deskId, request).getBody();

        assertThat(response.bands()).hasSize(1);
        assertThat(response.bands().get(0).breakStartTime()).isEqualTo(LocalTime.of(12, 0));
        assertThat(response.bands().get(0).breakEndTime()).isEqualTo(LocalTime.of(13, 0));
        assertThat(response.bands().get(0).netHours()).isEqualByComparingTo(new BigDecimal("8.00"));
    }

    @Test
    void create_validWeekdaysReturnedInMondayFirstOrder() {
        UUID deskId = saveDesk(TENANT_A);
        ShiftTemplateRequest request = new ShiftTemplateRequest(
                "S1", LocalTime.of(8, 0), LocalTime.of(17, 0),
                List.of(new BreakBandRequest(240, 60, null)),
                Set.of(DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
                LocalDate.of(2026, 1, 1), null);

        ShiftTemplateResponse response = controller.createShiftTemplate(deskId, request).getBody();

        assertThat(response.validWeekdays())
                .containsExactly(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.SUNDAY);
    }

    @Test
    void create_zeroBands_netHoursEqualsFullEnvelope() {
        UUID deskId = saveDesk(TENANT_A);
        ShiftTemplateRequest request = new ShiftTemplateRequest(
                "S1", LocalTime.of(8, 0), LocalTime.of(12, 0), List.of(),
                Set.of(DayOfWeek.MONDAY), LocalDate.of(2026, 1, 1), null);

        ShiftTemplateResponse response = controller.createShiftTemplate(deskId, request).getBody();

        assertThat(response.bands()).isEmpty();
    }

    @Test
    void desk_savedWithoutModeSet_readsBackAsSlot() {
        UUID deskId = saveDesk(TENANT_A);

        Desk reloaded = deskRepository.findById(deskId).orElseThrow();

        assertThat(reloaded.getSchedulingMode()).isEqualTo(SchedulingMode.SLOT);
    }

    @Test
    void list_crossTenant_returnsEmpty() {
        UUID deskId = saveDesk(TENANT_A);
        ShiftTemplateRequest request = new ShiftTemplateRequest(
                "S1", LocalTime.of(8, 0), LocalTime.of(17, 0),
                List.of(new BreakBandRequest(240, 60, null)),
                Set.of(DayOfWeek.MONDAY), LocalDate.of(2026, 1, 1), null);
        controller.createShiftTemplate(deskId, request);

        TenantContext.setTenantId(TENANT_B);
        List<ShiftTemplateResponse> result = controller.listShiftTemplates(deskId);

        assertThat(result).isEmpty();
    }

    private UUID saveDesk(long tenantId) {
        Desk desk = new Desk();
        desk.setTenantId(tenantId);
        desk.setName("Desk " + UUID.randomUUID());
        return deskRepository.save(desk).getId();
    }
}
