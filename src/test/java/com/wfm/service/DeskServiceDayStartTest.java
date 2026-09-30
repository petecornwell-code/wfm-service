package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.controller.DeskController;
import com.wfm.dto.DayStartRequest;
import com.wfm.dto.DeskResponse;
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

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies DeskService.setDayStart (BDAY-01): the 00:00-only gate, the null refusal, the
 * equal-value no-op, and the endpoint round trip.
 *
 * Uses H2 via @DataJpaTest, mirroring DeskServiceSchedulingModeTest's shape.
 */
@DataJpaTest
@Import({DeskService.class, InMemoryScheduleStore.class, DeskController.class})
@ActiveProfiles("test")
class DeskServiceDayStartTest {

    @Autowired
    private DeskService deskService;

    @Autowired
    private DeskController deskController;

    @Autowired
    private DeskRepository deskRepository;

    @MockitoBean
    private ShiftLibraryValidationService shiftLibraryValidationService;

    private static final long TENANT_A = 1L;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_A);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void createDesk_readsBackWithMidnightDayStart() {
        Desk created = deskService.createDesk("Desk A", "desc", null);

        Desk reloaded = deskService.getDesk(created.getId());

        assertThat(reloaded.getDayStart()).isEqualTo(LocalTime.MIDNIGHT);
    }

    @Test
    void setDayStart_midnight_persistsAndReturnsDesk() {
        Desk desk = saveDesk(TENANT_A);

        Desk result = deskService.setDayStart(desk.getId(), LocalTime.MIDNIGHT);

        assertThat(result.getDayStart()).isEqualTo(LocalTime.MIDNIGHT);
        Desk reloaded = deskRepository.findById(desk.getId()).orElseThrow();
        assertThat(reloaded.getDayStart()).isEqualTo(LocalTime.MIDNIGHT);
    }

    @Test
    void setDayStart_nonMidnightValue_throwsIllegalArgument_deskRowUnchanged() {
        Desk desk = saveDesk(TENANT_A);

        assertThatThrownBy(() -> deskService.setDayStart(desk.getId(), LocalTime.of(21, 0)))
                .isInstanceOf(IllegalArgumentException.class);

        Desk reloaded = deskRepository.findById(desk.getId()).orElseThrow();
        assertThat(reloaded.getDayStart()).isEqualTo(LocalTime.MIDNIGHT);
    }

    @Test
    void setDayStart_nullValue_throwsIllegalArgumentWithMessage_persistsNothing() {
        Desk desk = saveDesk(TENANT_A);

        assertThatThrownBy(() -> deskService.setDayStart(desk.getId(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Day start is required");

        Desk reloaded = deskRepository.findById(desk.getId()).orElseThrow();
        assertThat(reloaded.getDayStart()).isEqualTo(LocalTime.MIDNIGHT);
    }

    @Test
    void setDayStart_equalToCurrentValue_returnsDeskUnchanged_isNoOp() {
        Desk desk = saveDesk(TENANT_A);

        Desk result = deskService.setDayStart(desk.getId(), desk.getDayStart());

        assertThat(result.getDayStart()).isEqualTo(LocalTime.MIDNIGHT);
        Desk reloaded = deskRepository.findById(desk.getId()).orElseThrow();
        assertThat(reloaded.getDayStart()).isEqualTo(LocalTime.MIDNIGHT);
    }

    @Test
    void controller_setDayStart_returnsResponseWithDayStartPopulated() {
        Desk desk = saveDesk(TENANT_A);

        DeskResponse response = deskController.setDayStart(desk.getId(),
                new DayStartRequest(LocalTime.MIDNIGHT));

        assertThat(response.dayStart()).isEqualTo(LocalTime.MIDNIGHT);
    }

    @Test
    void controller_setDayStart_nonMidnightValue_throwsIllegalArgument() {
        Desk desk = saveDesk(TENANT_A);

        assertThatThrownBy(() -> deskController.setDayStart(desk.getId(),
                new DayStartRequest(LocalTime.of(21, 0))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- Helpers ---

    private Desk saveDesk(long tenantId) {
        Desk desk = new Desk();
        desk.setTenantId(tenantId);
        desk.setName("Desk " + UUID.randomUUID());
        desk.setDefaultContractedHoursPerDay(new BigDecimal("8.00"));
        return deskRepository.save(desk);
    }
}
