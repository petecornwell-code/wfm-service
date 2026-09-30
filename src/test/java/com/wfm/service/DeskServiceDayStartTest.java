package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.controller.DeskController;
import com.wfm.dto.DayStartRequest;
import com.wfm.dto.DeskResponse;
import com.wfm.exception.ConflictException;
import com.wfm.model.Desk;
import com.wfm.model.Schedule;
import com.wfm.model.ScheduleStatus;
import com.wfm.repository.DeskRepository;
import com.wfm.repository.ScheduleRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies DeskService.setDayStart: the 00:00-only gate, the null refusal, the equal-value
 * no-op, and the endpoint round trip (BDAY-01); and the unconditional accepted-schedule refusal,
 * including deterministic ordering when a desk holds more than one ACCEPTED schedule.
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

    @Autowired
    private ScheduleRepository scheduleRepository;

    @Autowired
    private TestEntityManager entityManager;

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

    // --- Accepted-schedule refusal (unconditional, no bypass) ---

    @Test
    void setDayStart_acceptedScheduleExists_throwsConflictNamingSchedule() {
        Desk desk = saveDeskWithDayStart(TENANT_A, LocalTime.of(21, 0));
        Schedule accepted = saveAcceptedSchedule(desk.getId(), OffsetDateTime.now());

        assertThatThrownBy(() -> deskService.setDayStart(desk.getId(), LocalTime.MIDNIGHT))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining(accepted.getId().toString())
                .hasMessageContaining(accepted.getPeriodStartDate().toString())
                .hasMessageContaining(accepted.getPeriodEndDate().toString());

        Desk reloaded = deskRepository.findById(desk.getId()).orElseThrow();
        assertThat(reloaded.getDayStart()).isEqualTo(LocalTime.of(21, 0));
    }

    @Test
    void setDayStart_twoAcceptedSchedules_repeatedRefusals_produceIdenticalMessageNamingLatest() {
        Desk desk = saveDeskWithDayStart(TENANT_A, LocalTime.of(21, 0));
        OffsetDateTime now = OffsetDateTime.now();
        saveAcceptedSchedule(desk.getId(), now.minusDays(1));
        Schedule latest = saveAcceptedSchedule(desk.getId(), now);

        String firstMessage = catchConflictMessage(desk.getId());
        String secondMessage = catchConflictMessage(desk.getId());

        assertThat(firstMessage).isEqualTo(secondMessage);
        assertThat(firstMessage).contains(latest.getId().toString());
    }

    @Test
    void setDayStart_noAcceptedSchedule_succeeds() {
        Desk desk = saveDeskWithDayStart(TENANT_A, LocalTime.of(21, 0));

        Desk result = deskService.setDayStart(desk.getId(), LocalTime.MIDNIGHT);

        assertThat(result.getDayStart()).isEqualTo(LocalTime.MIDNIGHT);
    }

    @Test
    void setDayStart_equalToCurrentValue_acceptedScheduleExists_succeeds() {
        Desk desk = saveDesk(TENANT_A);
        saveAcceptedSchedule(desk.getId(), OffsetDateTime.now());

        Desk result = deskService.setDayStart(desk.getId(), desk.getDayStart());

        assertThat(result.getDayStart()).isEqualTo(LocalTime.MIDNIGHT);
    }

    // --- Helpers ---

    private Desk saveDesk(long tenantId) {
        Desk desk = new Desk();
        desk.setTenantId(tenantId);
        desk.setName("Desk " + UUID.randomUUID());
        desk.setDefaultContractedHoursPerDay(new BigDecimal("8.00"));
        return deskRepository.save(desk);
    }

    private Desk saveDeskWithDayStart(long tenantId, LocalTime dayStart) {
        Desk desk = new Desk();
        desk.setTenantId(tenantId);
        desk.setName("Desk " + UUID.randomUUID());
        desk.setDefaultContractedHoursPerDay(new BigDecimal("8.00"));
        desk.setDayStart(dayStart);
        return deskRepository.save(desk);
    }

    private Schedule saveAcceptedSchedule(UUID deskId, OffsetDateTime createdAt) {
        Schedule schedule = new Schedule();
        schedule.setTenantId(TENANT_A);
        schedule.setDeskId(deskId);
        schedule.setIncrementMinutes(30);
        schedule.setStartTime(LocalTime.of(8, 0));
        schedule.setEndTime(LocalTime.of(17, 0));
        schedule.setPeriodStartDate(LocalDate.of(2026, 1, 5));
        schedule.setPeriodEndDate(LocalDate.of(2026, 1, 11));
        schedule.setStatus(ScheduleStatus.ACCEPTED);
        schedule.setCreatedAt(createdAt);
        Schedule saved = scheduleRepository.save(schedule);
        entityManager.flush();
        return saved;
    }

    private String catchConflictMessage(UUID deskId) {
        try {
            deskService.setDayStart(deskId, LocalTime.MIDNIGHT);
            throw new AssertionError("Expected ConflictException but call succeeded");
        } catch (ConflictException e) {
            return e.getMessage();
        }
    }
}
