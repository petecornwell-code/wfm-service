package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.controller.DeskController;
import com.wfm.dto.DayStartRequest;
import com.wfm.dto.DeskResponse;
import com.wfm.exception.ConflictException;
import com.wfm.model.Desk;
import com.wfm.model.Schedule;
import com.wfm.model.ScheduleStatus;
import com.wfm.model.ShiftTemplate;
import com.wfm.repository.DeskRepository;
import com.wfm.repository.ScheduleRepository;
import com.wfm.repository.ShiftTemplateRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies DeskService.setDayStart: the 15-minute-boundary gate (SOLV-01), the null refusal, the
 * equal-value no-op, and the endpoint round trip; and the unconditional accepted-schedule
 * refusal, including deterministic ordering when a desk holds more than one ACCEPTED schedule.
 *
 * <p>Written RED against the 15-minute-boundary contract this plan's final commit delivers: at
 * the commit that lands this change, the service still refuses any value other than {@code
 * 00:00}, so every case below that saves a non-midnight day start fails until that commit lands.
 *
 * Uses H2 via @DataJpaTest, mirroring DeskServiceSchedulingModeTest's shape.
 */
@DataJpaTest
@Import({DeskService.class, InMemoryScheduleStore.class, DeskController.class, TimeslotGeneratorService.class})
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
    private ShiftTemplateRepository shiftTemplateRepository;

    @Autowired
    private TimeslotGeneratorService timeslotGeneratorService;

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
    void setDayStart_2100_acceptedPersistedAndReadsBackFromDeskRow() {
        Desk desk = saveDesk(TENANT_A);

        Desk result = deskService.setDayStart(desk.getId(), LocalTime.of(21, 0));

        assertThat(result.getDayStart()).isEqualTo(LocalTime.of(21, 0));
        Desk reloaded = deskRepository.findById(desk.getId()).orElseThrow();
        assertThat(reloaded.getDayStart()).isEqualTo(LocalTime.of(21, 0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"00:15", "06:30", "21:15", "23:45"})
    void setDayStart_quarterHourBoundary_accepted(String boundary) {
        Desk desk = saveDesk(TENANT_A);
        LocalTime value = LocalTime.parse(boundary);

        Desk result = deskService.setDayStart(desk.getId(), value);

        assertThat(result.getDayStart()).isEqualTo(value);
        Desk reloaded = deskRepository.findById(desk.getId()).orElseThrow();
        assertThat(reloaded.getDayStart()).isEqualTo(value);
    }

    @Test
    void setDayStart_2107_refusedNamingRejectedValue() {
        Desk desk = saveDesk(TENANT_A);

        assertThatThrownBy(() -> deskService.setDayStart(desk.getId(), LocalTime.of(21, 7)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("21:07");

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
    void controller_setDayStart_2100_returnsResponseCarrying2100() {
        Desk desk = saveDesk(TENANT_A);

        DeskResponse response = deskController.setDayStart(desk.getId(),
                new DayStartRequest(LocalTime.of(21, 0)));

        assertThat(response.dayStart()).isEqualTo(LocalTime.of(21, 0));
    }

    // --- Sub-minute precision refusal (SOLV-01/SOLV-03): the modulus below discards seconds and
    // nanoseconds, so a value carrying either must be refused BY NAME, separately, before it ---

    @Test
    void setDayStart_060000_accepted_positiveControl() {
        Desk desk = saveDesk(TENANT_A);
        LocalTime value = LocalTime.of(6, 0, 0);

        Desk result = deskService.setDayStart(desk.getId(), value);

        assertThat(result.getDayStart()).isEqualTo(value);
        Desk reloaded = deskRepository.findById(desk.getId()).orElseThrow();
        assertThat(reloaded.getDayStart()).isEqualTo(value);
    }

    @Test
    void setDayStart_060001_refusedNamingRejectedValue_persistsNothing() {
        Desk desk = saveDesk(TENANT_A);
        LocalTime rejected = LocalTime.of(6, 0, 1);

        assertThatThrownBy(() -> deskService.setDayStart(desk.getId(), rejected))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(rejected.toString());

        Desk reloaded = deskRepository.findById(desk.getId()).orElseThrow();
        assertThat(reloaded.getDayStart()).isEqualTo(LocalTime.MIDNIGHT);
    }

    @Test
    void setDayStart_nanosecondComponent_refusedNamingRejectedValue_persistsNothing() {
        Desk desk = saveDesk(TENANT_A);
        LocalTime rejected = LocalTime.of(21, 15, 0, 500);

        assertThatThrownBy(() -> deskService.setDayStart(desk.getId(), rejected))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(rejected.toString());

        Desk reloaded = deskRepository.findById(desk.getId()).orElseThrow();
        assertThat(reloaded.getDayStart()).isEqualTo(LocalTime.MIDNIGHT);
    }

    @Test
    void setDayStart_060701_refusedNamingSubMinuteReason_notBoundaryReason_persistsNothing() {
        Desk desk = saveDesk(TENANT_A);
        LocalTime rejected = LocalTime.of(6, 7, 1);

        assertThatThrownBy(() -> deskService.setDayStart(desk.getId(), rejected))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(rejected.toString())
                .hasMessageContaining("seconds or sub-second precision")
                .hasMessageNotContaining("15-minute boundary");

        Desk reloaded = deskRepository.findById(desk.getId()).orElseThrow();
        assertThat(reloaded.getDayStart()).isEqualTo(LocalTime.MIDNIGHT);
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

    // --- Stranded-template refusal (OVNT-01/D-03): re-anchoring is refused when it would leave a
    // stored template no longer running forward against the proposed anchor, naming the template. ---

    @Test
    void setDayStart_wouldStrandStoredTemplate_throwsConflictNamingTemplate() {
        Desk desk = saveDesk(TENANT_A);
        saveShiftTemplate(desk.getId(), "Overnight Support", LocalTime.of(14, 0), LocalTime.of(23, 0),
                LocalDate.of(2026, 1, 1));

        assertThatThrownBy(() -> deskService.setDayStart(desk.getId(), LocalTime.of(21, 0)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Overnight Support");

        Desk reloaded = deskRepository.findById(desk.getId()).orElseThrow();
        assertThat(reloaded.getDayStart()).isEqualTo(LocalTime.MIDNIGHT);
    }

    @Test
    void setDayStart_templateStillForwardAtProposedAnchor_persists() {
        Desk desk = saveDesk(TENANT_A);
        saveShiftTemplate(desk.getId(), "Day Shift", LocalTime.of(8, 0), LocalTime.of(17, 0),
                LocalDate.of(2026, 1, 1));

        Desk result = deskService.setDayStart(desk.getId(), LocalTime.of(21, 0));

        assertThat(result.getDayStart()).isEqualTo(LocalTime.of(21, 0));
        Desk reloaded = deskRepository.findById(desk.getId()).orElseThrow();
        assertThat(reloaded.getDayStart()).isEqualTo(LocalTime.of(21, 0));
    }

    @Test
    void setDayStart_failsBothAcceptedScheduleAndStrandedTemplate_refusedForAcceptedSchedule() {
        Desk desk = saveDeskWithDayStart(TENANT_A, LocalTime.MIDNIGHT);
        saveShiftTemplate(desk.getId(), "Overnight Support", LocalTime.of(14, 0), LocalTime.of(23, 0),
                LocalDate.of(2026, 1, 1));
        Schedule accepted = saveAcceptedSchedule(desk.getId(), OffsetDateTime.now());

        assertThatThrownBy(() -> deskService.setDayStart(desk.getId(), LocalTime.of(21, 0)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining(accepted.getId().toString())
                .hasMessageNotContaining("Overnight Support");
    }

    @Test
    void setDayStart_noStoredTemplates_acceptsAnyValidChange() {
        Desk desk = saveDesk(TENANT_A);

        Desk result = deskService.setDayStart(desk.getId(), LocalTime.of(21, 0));

        assertThat(result.getDayStart()).isEqualTo(LocalTime.of(21, 0));
    }

    // --- Non-blocking tiling advisory (OVNT-01/D-05): a day start that saves successfully but
    // does not divide evenly into the desk's existing live increment still saves, with an advisory. ---

    @Test
    void dayStartTilingWarning_doesNotTileLiveThirtyMinuteIncrement_returnsAdvisory_saveStillHappened() {
        Desk desk = saveDesk(TENANT_A);
        generateLiveTimeslots(desk.getId(), 30);

        Desk result = deskService.setDayStart(desk.getId(), LocalTime.of(21, 15));
        Optional<String> warning = deskService.dayStartTilingWarning(desk.getId(), LocalTime.of(21, 15));

        assertThat(result.getDayStart()).isEqualTo(LocalTime.of(21, 15));
        Desk reloaded = deskRepository.findById(desk.getId()).orElseThrow();
        assertThat(reloaded.getDayStart()).isEqualTo(LocalTime.of(21, 15));
        assertThat(warning).isPresent();
        assertThat(warning.get()).contains("21:15").contains("30");
    }

    @Test
    void dayStartTilingWarning_tilesLiveThirtyMinuteIncrement_returnsEmpty() {
        Desk desk = saveDesk(TENANT_A);
        generateLiveTimeslots(desk.getId(), 30);

        deskService.setDayStart(desk.getId(), LocalTime.of(21, 30));
        Optional<String> warning = deskService.dayStartTilingWarning(desk.getId(), LocalTime.of(21, 30));

        assertThat(warning).isEmpty();
    }

    @Test
    void dayStartTilingWarning_deskHasNoLiveTimeslots_returnsEmptyForAnyValue() {
        Desk desk = saveDesk(TENANT_A);

        Optional<String> warning = deskService.dayStartTilingWarning(desk.getId(), LocalTime.of(21, 15));

        assertThat(warning).isEmpty();
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

    private ShiftTemplate saveShiftTemplate(UUID deskId, String name, LocalTime startTime, LocalTime endTime,
                                             LocalDate effectiveFrom) {
        ShiftTemplate template = new ShiftTemplate();
        template.setTenantId(TENANT_A);
        template.setDeskId(deskId);
        template.setName(name);
        template.setStartTime(startTime);
        template.setEndTime(endTime);
        template.setValidWeekdays(EnumSet.allOf(DayOfWeek.class));
        template.setEffectiveFrom(effectiveFrom);
        ShiftTemplate saved = shiftTemplateRepository.save(template);
        entityManager.flush();
        return saved;
    }

    private void generateLiveTimeslots(UUID deskId, int incrementMinutes) {
        timeslotGeneratorService.generateTimeslots(deskId, LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 5),
                LocalTime.MIDNIGHT, LocalTime.of(8, 0), LocalTime.of(17, 0), incrementMinutes);
        entityManager.flush();
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
