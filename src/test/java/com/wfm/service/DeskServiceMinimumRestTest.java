package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.controller.DeskController;
import com.wfm.dto.DeskResponse;
import com.wfm.dto.MinimumRestRequest;
import com.wfm.exception.EntityNotFoundException;
import com.wfm.model.Desk;
import com.wfm.model.Schedule;
import com.wfm.model.ScheduleStatus;
import com.wfm.model.SchedulingMode;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Verifies {@code DeskService.setMinimumRest} (REST-01, D-04): the below-zero/at-or-above-24h
 * bound, the null-clears-the-setting semantics (never coerced to {@code 0}), the equal-value
 * no-op, and -- the load-bearing cases per D-14's declined mirror of {@code setDayStart}'s lock --
 * that the setter carries NO ACCEPTED-schedule refusal and NO scheduling-mode interaction.
 *
 * <p>Uses H2 via {@code @DataJpaTest}, mirroring {@code DeskServiceDayStartTest}'s shape.
 */
@DataJpaTest
@Import({DeskService.class, InMemoryScheduleStore.class, DeskController.class, TimeslotGeneratorService.class})
@ActiveProfiles("test")
class DeskServiceMinimumRestTest {

    @Autowired
    private DeskService deskService;

    @Autowired
    private DeskController deskController;

    @MockitoSpyBean
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
    void setMinimumRest_660_persistsAndReturnsDesk() {
        Desk desk = saveDesk(TENANT_A);

        Desk result = deskService.setMinimumRest(desk.getId(), 660);

        assertThat(result.getMinimumRestMinutes()).isEqualTo(660);
        Desk reloaded = deskRepository.findById(desk.getId()).orElseThrow();
        assertThat(reloaded.getMinimumRestMinutes()).isEqualTo(660);
    }

    @Test
    void setMinimumRest_null_clearsSettingToSqlNull_neverCoercedToZero() {
        Desk desk = saveDeskWithMinimumRest(TENANT_A, 660);

        Desk result = deskService.setMinimumRest(desk.getId(), null);

        assertThat(result.getMinimumRestMinutes()).isNull();
        Desk reloaded = deskRepository.findById(desk.getId()).orElseThrow();
        assertThat(reloaded.getMinimumRestMinutes()).isNull();
    }

    @Test
    void setMinimumRest_zero_succeeds_realConfiguredValueDistinctFromNull() {
        Desk desk = saveDesk(TENANT_A);

        Desk result = deskService.setMinimumRest(desk.getId(), 0);

        assertThat(result.getMinimumRestMinutes()).isZero();
        Desk reloaded = deskRepository.findById(desk.getId()).orElseThrow();
        assertThat(reloaded.getMinimumRestMinutes()).isZero();
    }

    @Test
    void setMinimumRest_1439_succeeds() {
        Desk desk = saveDesk(TENANT_A);

        Desk result = deskService.setMinimumRest(desk.getId(), 1439);

        assertThat(result.getMinimumRestMinutes()).isEqualTo(1439);
    }

    @Test
    void setMinimumRest_1440_refusedWithExactMessage_persistsNothing() {
        Desk desk = saveDesk(TENANT_A);

        assertThatThrownBy(() -> deskService.setMinimumRest(desk.getId(), 1440))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Minimum rest must be less than 24 hours");

        Desk reloaded = deskRepository.findById(desk.getId()).orElseThrow();
        assertThat(reloaded.getMinimumRestMinutes()).isNull();
    }

    @Test
    void setMinimumRest_negativeOne_refusedWithSameMessage() {
        Desk desk = saveDesk(TENANT_A);

        assertThatThrownBy(() -> deskService.setMinimumRest(desk.getId(), -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Minimum rest must be less than 24 hours");
    }

    @Test
    void setMinimumRest_equalToCurrentValue_returnsDeskWithNoRepositorySave() {
        Desk desk = saveDeskWithMinimumRest(TENANT_A, 660);
        org.mockito.Mockito.clearInvocations(deskRepository);

        Desk result = deskService.setMinimumRest(desk.getId(), 660);

        assertThat(result.getMinimumRestMinutes()).isEqualTo(660);
        verify(deskRepository, never()).save(any());
    }

    @Test
    void setMinimumRest_equalToCurrentNullValue_returnsDeskWithNoRepositorySave() {
        Desk desk = saveDesk(TENANT_A);
        org.mockito.Mockito.clearInvocations(deskRepository);

        Desk result = deskService.setMinimumRest(desk.getId(), null);

        assertThat(result.getMinimumRestMinutes()).isNull();
        verify(deskRepository, never()).save(any());
    }

    // --- D-14's declined mirror of setDayStart's lock: no ACCEPTED-schedule refusal, no
    // scheduling-mode interaction. These are the load-bearing cases -- a future reader must see a
    // deliberate decision, not assume the lock was simply forgotten. ---

    @Test
    void setMinimumRest_acceptedScheduleExists_stillSucceeds() {
        Desk desk = saveDesk(TENANT_A);
        saveAcceptedSchedule(desk.getId(), OffsetDateTime.now());

        Desk result = deskService.setMinimumRest(desk.getId(), 660);

        assertThat(result.getMinimumRestMinutes()).isEqualTo(660);
        Desk reloaded = deskRepository.findById(desk.getId()).orElseThrow();
        assertThat(reloaded.getMinimumRestMinutes()).isEqualTo(660);
    }

    @Test
    void setMinimumRest_slotModeDesk_succeeds() {
        Desk desk = saveDesk(TENANT_A);
        assertThat(desk.getSchedulingMode()).isEqualTo(SchedulingMode.SLOT);

        Desk result = deskService.setMinimumRest(desk.getId(), 660);

        assertThat(result.getMinimumRestMinutes()).isEqualTo(660);
    }

    @Test
    void setMinimumRest_shiftModeDesk_succeeds() {
        Desk desk = saveDesk(TENANT_A);
        desk.setSchedulingMode(SchedulingMode.SHIFT);
        deskRepository.save(desk);
        entityManager.flush();

        Desk result = deskService.setMinimumRest(desk.getId(), 660);

        assertThat(result.getMinimumRestMinutes()).isEqualTo(660);
    }

    @Test
    void setMinimumRest_unknownDeskId_throwsEntityNotFoundException() {
        assertThatThrownBy(() -> deskService.setMinimumRest(UUID.randomUUID(), 660))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void getDeskAndListDesks_carryMinimumRestMinutes_nullWhenUnset() {
        Desk unset = saveDesk(TENANT_A);
        Desk set = saveDeskWithMinimumRest(TENANT_A, 660);

        DeskResponse getResponse = deskController.getDesk(unset.getId());
        List<DeskResponse> listResponses = deskController.listDesks();

        assertThat(getResponse.minimumRestMinutes()).isNull();
        assertThat(listResponses.stream().filter(r -> r.id().equals(set.getId())).findFirst().orElseThrow()
                .minimumRestMinutes()).isEqualTo(660);
        assertThat(listResponses.stream().filter(r -> r.id().equals(unset.getId())).findFirst().orElseThrow()
                .minimumRestMinutes()).isNull();
    }

    @Test
    void controller_setMinimumRest_roundTripsThroughResponse() {
        Desk desk = saveDesk(TENANT_A);

        DeskResponse response = deskController.setMinimumRest(desk.getId(), new MinimumRestRequest(660));

        assertThat(response.minimumRestMinutes()).isEqualTo(660);
    }

    // --- Helpers ---

    private Desk saveDesk(long tenantId) {
        Desk desk = new Desk();
        desk.setTenantId(tenantId);
        desk.setName("Desk " + UUID.randomUUID());
        desk.setDefaultContractedHoursPerDay(new BigDecimal("8.00"));
        return deskRepository.save(desk);
    }

    private Desk saveDeskWithMinimumRest(long tenantId, Integer minimumRestMinutes) {
        Desk desk = new Desk();
        desk.setTenantId(tenantId);
        desk.setName("Desk " + UUID.randomUUID());
        desk.setDefaultContractedHoursPerDay(new BigDecimal("8.00"));
        desk.setMinimumRestMinutes(minimumRestMinutes);
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
}
