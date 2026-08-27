package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.controller.ShiftTemplateController;
import com.wfm.dto.ShiftTemplateRequest;
import com.wfm.dto.ShiftTemplateRequest.BreakBandRequest;
import com.wfm.dto.ShiftTemplateResponse;
import com.wfm.dto.ShiftTemplateResponse.BreakBandResponse;
import com.wfm.model.Desk;
import com.wfm.repository.DeskRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;

/**
 * The band round trip this plan's promotion rests on (D-01, Task 1): a two-band template
 * persists and reads back offset-ascending with correct per-band net hours; zero bands is a
 * legal "no break" state; a zero/negative capacity is rejected; a blank capacity saves as null
 * (unlimited, D-03); a duplicate (offset, duration) pair is rejected (P-05), while touching bands
 * remain distinct and legal; and a save replaces the template's bands wholesale.
 */
@DataJpaTest
@Import({ShiftTemplateService.class, ShiftTemplateController.class})
@ActiveProfiles("test")
class ShiftTemplateBreakBandServiceTest {

    @Autowired
    private ShiftTemplateController controller;

    @Autowired
    private DeskRepository deskRepository;

    @MockitoBean
    private TimeslotGeneratorService timeslotGeneratorService;

    private static final long TENANT_A = 1L;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_A);
        when(timeslotGeneratorService.getLiveBounds(any())).thenReturn(Optional.empty());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void create_twoBands_roundTripOffsetAscendingWithCorrectNetHours() {
        UUID deskId = saveDesk();
        ShiftTemplateRequest request = new ShiftTemplateRequest("S1", LocalTime.of(8, 0), LocalTime.of(17, 0),
                List.of(new BreakBandRequest(300, 60, null), new BreakBandRequest(240, 60, null)),
                Set.of(DayOfWeek.MONDAY), LocalDate.of(2026, 1, 1), null);

        ShiftTemplateResponse created = controller.createShiftTemplate(deskId, request).getBody();
        List<ShiftTemplateResponse> list = controller.listShiftTemplates(deskId);

        assertThat(created.bands()).hasSize(2);
        assertThat(created.bands()).extracting(BreakBandResponse::offsetMinutes).containsExactly(240, 300);
        assertThat(created.bands()).allSatisfy(b -> assertThat(b.netHours()).isEqualByComparingTo("8.00"));
        assertThat(list).hasSize(1);
        assertThat(list.get(0).bands()).extracting(BreakBandResponse::offsetMinutes).containsExactly(240, 300);
    }

    @Test
    void create_zeroBands_savesAndReadsBackWithEmptyBandList() {
        UUID deskId = saveDesk();
        ShiftTemplateRequest request = new ShiftTemplateRequest("S1", LocalTime.of(8, 0), LocalTime.of(17, 0),
                List.of(), Set.of(DayOfWeek.MONDAY), LocalDate.of(2026, 1, 1), null);

        ShiftTemplateResponse created = controller.createShiftTemplate(deskId, request).getBody();

        assertThat(created.bands()).isEmpty();
        assertThat(controller.listShiftTemplates(deskId).get(0).bands()).isEmpty();
    }

    @Test
    void create_nullBands_treatedAsNoBreak() {
        UUID deskId = saveDesk();
        ShiftTemplateRequest request = new ShiftTemplateRequest("S1", LocalTime.of(8, 0), LocalTime.of(17, 0),
                null, Set.of(DayOfWeek.MONDAY), LocalDate.of(2026, 1, 1), null);

        ShiftTemplateResponse created = controller.createShiftTemplate(deskId, request).getBody();

        assertThat(created.bands()).isEmpty();
    }

    @Test
    void create_zeroCapacity_rejected() {
        UUID deskId = saveDesk();
        ShiftTemplateRequest request = new ShiftTemplateRequest("S1", LocalTime.of(8, 0), LocalTime.of(17, 0),
                List.of(new BreakBandRequest(240, 60, 0)),
                Set.of(DayOfWeek.MONDAY), LocalDate.of(2026, 1, 1), null);

        assertThatThrownBy(() -> controller.createShiftTemplate(deskId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("capacity")
                .hasMessageContaining("240");
    }

    @Test
    void create_negativeCapacity_rejected() {
        UUID deskId = saveDesk();
        ShiftTemplateRequest request = new ShiftTemplateRequest("S1", LocalTime.of(8, 0), LocalTime.of(17, 0),
                List.of(new BreakBandRequest(240, 60, -1)),
                Set.of(DayOfWeek.MONDAY), LocalDate.of(2026, 1, 1), null);

        assertThatThrownBy(() -> controller.createShiftTemplate(deskId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("capacity");
    }

    @Test
    void create_blankCapacity_savesAsNull() {
        UUID deskId = saveDesk();
        ShiftTemplateRequest request = new ShiftTemplateRequest("S1", LocalTime.of(8, 0), LocalTime.of(17, 0),
                List.of(new BreakBandRequest(240, 60, null)),
                Set.of(DayOfWeek.MONDAY), LocalDate.of(2026, 1, 1), null);

        ShiftTemplateResponse created = controller.createShiftTemplate(deskId, request).getBody();

        assertThat(created.bands().get(0).capacity()).isNull();
    }

    @Test
    void create_duplicateOffsetAndDuration_rejected() {
        UUID deskId = saveDesk();
        ShiftTemplateRequest request = new ShiftTemplateRequest("S1", LocalTime.of(8, 0), LocalTime.of(17, 0),
                List.of(new BreakBandRequest(240, 60, null), new BreakBandRequest(240, 60, null)),
                Set.of(DayOfWeek.MONDAY), LocalDate.of(2026, 1, 1), null);

        assertThatThrownBy(() -> controller.createShiftTemplate(deskId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("240");
    }

    @Test
    void create_touchingBands_bothLegal() {
        // Band A break 12:00-13:00 (offset 240, duration 60), band B break 13:00-14:00
        // (offset 300, duration 60) touch exactly at 13:00 -- distinct, not duplicates.
        UUID deskId = saveDesk();
        ShiftTemplateRequest request = new ShiftTemplateRequest("S1", LocalTime.of(8, 0), LocalTime.of(17, 0),
                List.of(new BreakBandRequest(240, 60, null), new BreakBandRequest(300, 60, null)),
                Set.of(DayOfWeek.MONDAY), LocalDate.of(2026, 1, 1), null);

        assertThatCode(() -> controller.createShiftTemplate(deskId, request)).doesNotThrowAnyException();
    }

    @Test
    void update_replacesTemplatesBandsWholesale() {
        UUID deskId = saveDesk();
        ShiftTemplateResponse created = controller.createShiftTemplate(deskId,
                new ShiftTemplateRequest("S1", LocalTime.of(8, 0), LocalTime.of(17, 0),
                        List.of(new BreakBandRequest(240, 60, null)),
                        Set.of(DayOfWeek.MONDAY), LocalDate.of(2026, 1, 1), null)).getBody();

        ShiftTemplateResponse updated = controller.updateShiftTemplate(deskId, created.id(),
                new ShiftTemplateRequest("S1", LocalTime.of(8, 0), LocalTime.of(17, 0),
                        List.of(new BreakBandRequest(300, 60, null)),
                        Set.of(DayOfWeek.MONDAY), LocalDate.of(2026, 1, 1), null));

        assertThat(updated.bands()).hasSize(1);
        assertThat(updated.bands().get(0).offsetMinutes()).isEqualTo(300);
    }

    private UUID saveDesk() {
        Desk desk = new Desk();
        desk.setTenantId(TENANT_A);
        desk.setName("Desk " + UUID.randomUUID());
        return deskRepository.save(desk).getId();
    }
}
