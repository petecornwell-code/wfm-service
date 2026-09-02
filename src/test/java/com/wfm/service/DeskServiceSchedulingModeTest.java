package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.controller.DeskController;
import com.wfm.dto.DeskResponse;
import com.wfm.dto.ErrorResponse.ErrorDetail;
import com.wfm.dto.SchedulingModeRequest;
import com.wfm.exception.ConflictException;
import com.wfm.exception.EntityNotFoundException;
import com.wfm.exception.PreSolveValidationException;
import com.wfm.model.Desk;
import com.wfm.model.Schedule;
import com.wfm.model.ScheduleStatus;
import com.wfm.model.SchedulingMode;
import com.wfm.model.Specialization;
import com.wfm.model.StaffingRequirement;
import com.wfm.model.Timeslot;
import com.wfm.repository.DeskRepository;
import com.wfm.repository.ScheduleRepository;
import com.wfm.repository.SpecializationRepository;
import com.wfm.repository.StaffingRequirementRepository;
import com.wfm.repository.TimeslotRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Verifies DeskService.switchSchedulingMode: the 409 in-flight-solve guard (D-13, P-22, P-24),
 * the SLOT-to-SHIFT-only coverage gate delegating to the shared validator (D-08, MODE-03), the
 * SLOT-to-SLOT/SHIFT-to-SHIFT no-op (P-23), and the MODE-04 accepted-schedule invariant.
 *
 * Uses H2 via @DataJpaTest, mirroring JobTitleConfigServiceTest's shape.
 */
@DataJpaTest
@Import({DeskService.class, InMemoryScheduleStore.class, DeskController.class})
@ActiveProfiles("test")
class DeskServiceSchedulingModeTest {

    @Autowired
    private DeskService deskService;

    @Autowired
    private DeskController deskController;

    @Autowired
    private DeskRepository deskRepository;

    @Autowired
    private ScheduleRepository scheduleRepository;

    @Autowired
    private TimeslotRepository timeslotRepository;

    @Autowired
    private StaffingRequirementRepository staffingRequirementRepository;

    @Autowired
    private SpecializationRepository specializationRepository;

    @Autowired
    private InMemoryScheduleStore inMemoryScheduleStore;

    @Autowired
    private TestEntityManager entityManager;

    @MockitoBean
    private ShiftLibraryValidationService shiftLibraryValidationService;

    private static final long TENANT_A = 1L;
    private static final long TENANT_B = 2L;

    private static final String IN_FLIGHT_MESSAGE =
            "This desk has a schedule currently solving. Wait for it to finish before changing scheduling mode.";

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_A);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // --- Default and happy paths ---

    @Test
    void createDesk_readsBackWithSlotMode() {
        Desk created = deskService.createDesk("Desk A", "desc", null);

        Desk reloaded = deskService.getDesk(created.getId());

        assertThat(reloaded.getSchedulingMode()).isEqualTo(SchedulingMode.SLOT);
    }

    @Test
    void switchSchedulingMode_slotToShift_validatorPasses_persistsShiftAndReturnsShift() {
        Desk desk = saveDesk(TENANT_A, SchedulingMode.SLOT);

        Desk result = deskService.switchSchedulingMode(desk.getId(), SchedulingMode.SHIFT);

        assertThat(result.getSchedulingMode()).isEqualTo(SchedulingMode.SHIFT);
        Desk reloaded = deskRepository.findById(desk.getId()).orElseThrow();
        assertThat(reloaded.getSchedulingMode()).isEqualTo(SchedulingMode.SHIFT);
    }

    @Test
    void switchSchedulingMode_shiftToSlot_validatorStubbedToThrow_succeedsAndValidatorNeverCalled() {
        Desk desk = saveDesk(TENANT_A, SchedulingMode.SHIFT);
        doThrow(new PreSolveValidationException("should never be called", List.of()))
                .when(shiftLibraryValidationService).requireShiftModeReady(any());

        Desk result = deskService.switchSchedulingMode(desk.getId(), SchedulingMode.SLOT);

        assertThat(result.getSchedulingMode()).isEqualTo(SchedulingMode.SLOT);
        verify(shiftLibraryValidationService, never()).requireShiftModeReady(any());
    }

    @Test
    void switchSchedulingMode_slotToSlot_isNoOp_validatorNotCalled_storeNotConsulted_rowUnchanged() {
        Desk desk = saveDesk(TENANT_A, SchedulingMode.SLOT);
        // A RUNNING schedule is registered for this desk. If the no-op path consulted the store
        // (guard runs before the no-op check), this would throw ConflictException. It must not.
        putScheduleInStore(desk.getId(), ScheduleStatus.RUNNING);

        Desk result = deskService.switchSchedulingMode(desk.getId(), SchedulingMode.SLOT);

        assertThat(result.getSchedulingMode()).isEqualTo(SchedulingMode.SLOT);
        verify(shiftLibraryValidationService, never()).requireShiftModeReady(any());
        Desk reloaded = deskRepository.findById(desk.getId()).orElseThrow();
        assertThat(reloaded.getSchedulingMode()).isEqualTo(SchedulingMode.SLOT);
    }

    @Test
    void switchSchedulingMode_shiftToShift_isNoOp() {
        Desk desk = saveDesk(TENANT_A, SchedulingMode.SHIFT);

        Desk result = deskService.switchSchedulingMode(desk.getId(), SchedulingMode.SHIFT);

        assertThat(result.getSchedulingMode()).isEqualTo(SchedulingMode.SHIFT);
        verify(shiftLibraryValidationService, never()).requireShiftModeReady(any());
    }

    // --- Controller: mode-switch endpoint and desk responses carry schedulingMode (XCUT-01) ---

    @Test
    void controller_switchSchedulingMode_shiftOnPassingDesk_returnsResponseWithShiftMode() {
        Desk desk = saveDesk(TENANT_A, SchedulingMode.SLOT);

        DeskResponse response = deskController.switchSchedulingMode(desk.getId(),
                new SchedulingModeRequest(SchedulingMode.SHIFT));

        assertThat(response.schedulingMode()).isEqualTo(SchedulingMode.SHIFT);
    }

    @Test
    void controller_listDesks_responsesCarrySchedulingModeForEveryDesk() {
        saveDesk(TENANT_A, SchedulingMode.SLOT);
        saveDesk(TENANT_A, SchedulingMode.SHIFT);

        List<DeskResponse> responses = deskController.listDesks();

        assertThat(responses).hasSize(2);
        assertThat(responses).extracting(DeskResponse::schedulingMode)
                .containsExactlyInAnyOrder(SchedulingMode.SLOT, SchedulingMode.SHIFT);
    }

    // --- Refusals ---

    @Test
    void switchSchedulingMode_slotToShift_validatorThrows_propagatesUnchangedAndLeavesModeAtSlot() {
        Desk desk = saveDesk(TENANT_A, SchedulingMode.SLOT);
        List<ErrorDetail> details = List.of(new ErrorDetail("coverage", "2026-01-01 08:00-09:00", null));
        doThrow(new PreSolveValidationException("1 demand window(s) have no covering shift template", details))
                .when(shiftLibraryValidationService).requireShiftModeReady(eq(desk.getId()));

        assertThatThrownBy(() -> deskService.switchSchedulingMode(desk.getId(), SchedulingMode.SHIFT))
                .isInstanceOf(PreSolveValidationException.class)
                .satisfies(ex -> {
                    PreSolveValidationException psve = (PreSolveValidationException) ex;
                    assertThat(psve.getDetails()).isEqualTo(details);
                    assertThat(psve.getMessage()).isEqualTo("1 demand window(s) have no covering shift template");
                });

        Desk reloaded = deskRepository.findById(desk.getId()).orElseThrow();
        assertThat(reloaded.getSchedulingMode()).isEqualTo(SchedulingMode.SLOT);
    }

    @Test
    void switchSchedulingMode_runningSchedule_slotToShift_throwsConflictWithVerbatimSentence_modeUnchanged() {
        Desk desk = saveDesk(TENANT_A, SchedulingMode.SLOT);
        putScheduleInStore(desk.getId(), ScheduleStatus.RUNNING);

        assertThatThrownBy(() -> deskService.switchSchedulingMode(desk.getId(), SchedulingMode.SHIFT))
                .isInstanceOf(ConflictException.class)
                .hasMessage(IN_FLIGHT_MESSAGE);

        Desk reloaded = deskRepository.findById(desk.getId()).orElseThrow();
        assertThat(reloaded.getSchedulingMode()).isEqualTo(SchedulingMode.SLOT);
    }

    @Test
    void switchSchedulingMode_runningSchedule_shiftToSlot_alsoThrowsConflict() {
        Desk desk = saveDesk(TENANT_A, SchedulingMode.SHIFT);
        putScheduleInStore(desk.getId(), ScheduleStatus.RUNNING);

        assertThatThrownBy(() -> deskService.switchSchedulingMode(desk.getId(), SchedulingMode.SLOT))
                .isInstanceOf(ConflictException.class)
                .hasMessage(IN_FLIGHT_MESSAGE);

        Desk reloaded = deskRepository.findById(desk.getId()).orElseThrow();
        assertThat(reloaded.getSchedulingMode()).isEqualTo(SchedulingMode.SHIFT);
    }

    @ParameterizedTest
    @EnumSource(value = ScheduleStatus.class, names = "RUNNING", mode = EnumSource.Mode.EXCLUDE)
    void switchSchedulingMode_nonRunningSchedule_switchAllowed(ScheduleStatus status) {
        Desk desk = saveDesk(TENANT_A, SchedulingMode.SLOT);
        putScheduleInStore(desk.getId(), status);

        Desk result = deskService.switchSchedulingMode(desk.getId(), SchedulingMode.SHIFT);

        assertThat(result.getSchedulingMode()).isEqualTo(SchedulingMode.SHIFT);
    }

    @Test
    void switchSchedulingMode_runningGuardFires_validatorNeverCalled() {
        Desk desk = saveDesk(TENANT_A, SchedulingMode.SLOT);
        putScheduleInStore(desk.getId(), ScheduleStatus.RUNNING);

        assertThatThrownBy(() -> deskService.switchSchedulingMode(desk.getId(), SchedulingMode.SHIFT))
                .isInstanceOf(ConflictException.class);

        verify(shiftLibraryValidationService, never()).requireShiftModeReady(any());
    }

    @Test
    void switchSchedulingMode_crossTenantDeskId_throwsEntityNotFound() {
        Desk desk = saveDesk(TENANT_B, SchedulingMode.SLOT);

        assertThatThrownBy(() -> deskService.switchSchedulingMode(desk.getId(), SchedulingMode.SHIFT))
                .isInstanceOf(EntityNotFoundException.class);
    }

    // --- MODE-04: accepted schedule + live rows untouched across a round trip ---

    @Test
    void switchSchedulingMode_roundTrip_leavesAcceptedScheduleAndSnapshotRowsExactlyUnchanged() {
        Desk desk = saveDesk(TENANT_A, SchedulingMode.SLOT);
        Specialization specialization = saveSpecialization(desk.getId());

        // The accepted schedule is deliberately SHIFT while the desk round-trips SLOT -> SHIFT ->
        // SLOT. The mismatch is what gives the schedulingMode assertion teeth: if a desk mode
        // switch ever wrote through to accepted schedules, this row would come back SLOT (the
        // desk's final mode) and the snapshot comparison would fail. Were the schedule left at the
        // SLOT default instead, that regression would end on SLOT too and the guard would pass
        // while the behaviour was broken.
        Schedule accepted = saveSchedule(desk.getId(), ScheduleStatus.ACCEPTED);
        accepted.setSchedulingMode(SchedulingMode.SHIFT);
        accepted = scheduleRepository.save(accepted);
        Timeslot snapshotTimeslot = saveTimeslot(desk.getId(), accepted.getId(), LocalDate.of(2026, 1, 5));
        StaffingRequirement snapshotRequirement =
                saveStaffingRequirement(desk.getId(), accepted.getId(), snapshotTimeslot, specialization);

        Timeslot liveTimeslot = saveTimeslot(desk.getId(), null, LocalDate.of(2026, 1, 6));
        StaffingRequirement liveRequirement =
                saveStaffingRequirement(desk.getId(), null, liveTimeslot, specialization);

        entityManager.flush();
        entityManager.clear();

        UUID scheduleId = accepted.getId();
        UUID snapshotTimeslotId = snapshotTimeslot.getId();
        UUID snapshotRequirementId = snapshotRequirement.getId();
        UUID liveTimeslotId = liveTimeslot.getId();
        UUID liveRequirementId = liveRequirement.getId();

        ScheduleSnapshot beforeSchedule = ScheduleSnapshot.of(scheduleRepository.findById(scheduleId).orElseThrow());
        TimeslotSnapshot beforeSnapshotTimeslot =
                TimeslotSnapshot.of(timeslotRepository.findById(snapshotTimeslotId).orElseThrow());
        StaffingRequirementSnapshot beforeSnapshotRequirement =
                StaffingRequirementSnapshot.of(staffingRequirementRepository.findById(snapshotRequirementId).orElseThrow());
        TimeslotSnapshot beforeLiveTimeslot =
                TimeslotSnapshot.of(timeslotRepository.findById(liveTimeslotId).orElseThrow());
        StaffingRequirementSnapshot beforeLiveRequirement =
                StaffingRequirementSnapshot.of(staffingRequirementRepository.findById(liveRequirementId).orElseThrow());

        deskService.switchSchedulingMode(desk.getId(), SchedulingMode.SHIFT);
        entityManager.flush();
        entityManager.clear();
        deskService.switchSchedulingMode(desk.getId(), SchedulingMode.SLOT);
        entityManager.flush();
        entityManager.clear();

        assertThat(ScheduleSnapshot.of(scheduleRepository.findById(scheduleId).orElseThrow()))
                .isEqualTo(beforeSchedule);
        assertThat(TimeslotSnapshot.of(timeslotRepository.findById(snapshotTimeslotId).orElseThrow()))
                .isEqualTo(beforeSnapshotTimeslot);
        assertThat(StaffingRequirementSnapshot.of(staffingRequirementRepository.findById(snapshotRequirementId).orElseThrow()))
                .isEqualTo(beforeSnapshotRequirement);
        assertThat(TimeslotSnapshot.of(timeslotRepository.findById(liveTimeslotId).orElseThrow()))
                .isEqualTo(beforeLiveTimeslot);
        assertThat(StaffingRequirementSnapshot.of(staffingRequirementRepository.findById(liveRequirementId).orElseThrow()))
                .isEqualTo(beforeLiveRequirement);

        Desk reloadedDesk = deskRepository.findById(desk.getId()).orElseThrow();
        assertThat(reloadedDesk.getSchedulingMode()).isEqualTo(SchedulingMode.SLOT);
    }

    // --- Helpers ---

    private Desk saveDesk(long tenantId, SchedulingMode mode) {
        Desk desk = new Desk();
        desk.setTenantId(tenantId);
        desk.setName("Desk " + UUID.randomUUID());
        desk.setDefaultContractedHoursPerDay(new BigDecimal("8.00"));
        desk.setSchedulingMode(mode);
        return deskRepository.save(desk);
    }

    private Specialization saveSpecialization(UUID deskId) {
        Specialization specialization = new Specialization();
        specialization.setTenantId(TENANT_A);
        specialization.setDeskId(deskId);
        specialization.setName("Spec " + UUID.randomUUID());
        return specializationRepository.save(specialization);
    }

    private Schedule saveSchedule(UUID deskId, ScheduleStatus status) {
        Schedule schedule = new Schedule();
        schedule.setTenantId(TENANT_A);
        schedule.setDeskId(deskId);
        schedule.setIncrementMinutes(30);
        schedule.setStartTime(LocalTime.of(8, 0));
        schedule.setEndTime(LocalTime.of(17, 0));
        schedule.setPeriodStartDate(LocalDate.of(2026, 1, 5));
        schedule.setPeriodEndDate(LocalDate.of(2026, 1, 11));
        schedule.setStatus(status);
        return scheduleRepository.save(schedule);
    }

    private Timeslot saveTimeslot(UUID deskId, UUID scheduleId, LocalDate date) {
        Timeslot timeslot = new Timeslot();
        timeslot.setTenantId(TENANT_A);
        timeslot.setDeskId(deskId);
        timeslot.setScheduleId(scheduleId);
        timeslot.setDate(date);
        timeslot.setStartTime(LocalTime.of(8, 0));
        timeslot.setEndTime(LocalTime.of(8, 30));
        return timeslotRepository.save(timeslot);
    }

    private StaffingRequirement saveStaffingRequirement(UUID deskId, UUID scheduleId, Timeslot timeslot,
                                                          Specialization specialization) {
        StaffingRequirement requirement = new StaffingRequirement();
        requirement.setTenantId(TENANT_A);
        requirement.setDeskId(deskId);
        requirement.setScheduleId(scheduleId);
        requirement.setTimeslot(timeslot);
        requirement.setSpecialization(specialization);
        requirement.setRequiredFTEs(2);
        return staffingRequirementRepository.save(requirement);
    }

    private void putScheduleInStore(UUID deskId, ScheduleStatus status) {
        Schedule schedule = new Schedule();
        schedule.setId(UUID.randomUUID());
        schedule.setTenantId(TENANT_A);
        schedule.setDeskId(deskId);
        schedule.setIncrementMinutes(30);
        schedule.setStartTime(LocalTime.of(8, 0));
        schedule.setEndTime(LocalTime.of(17, 0));
        schedule.setPeriodStartDate(LocalDate.of(2026, 1, 5));
        schedule.setPeriodEndDate(LocalDate.of(2026, 1, 11));
        schedule.setStatus(status);
        inMemoryScheduleStore.put(schedule);
    }

    /**
     * {@code schedulingMode} is in this record deliberately, and it was MISSING until 2026-09-02.
     *
     * <p>The column arrived with CR-02 (V43, {@code 75349d8}) while this test was last touched in
     * phase 14 ({@code b7f5b3b}), so the snapshot simply predated the field. The consequence was
     * that {@link #switchSchedulingMode_roundTrip_leavesAcceptedScheduleAndSnapshotRowsExactlyUnchanged}
     * asserted "ExactlyUnchanged" while ignoring the one column CR-02 exists to protect — a desk
     * mode switch could have written through to every accepted schedule and this test would still
     * have passed. Found during UAT test 16, where the behaviour had to be verified by hand
     * against the live desk because nothing in the suite covered it.
     */
    private record ScheduleSnapshot(UUID id, long tenantId, UUID deskId, int incrementMinutes,
                                     LocalTime startTime, LocalTime endTime, LocalDate periodStartDate,
                                     LocalDate periodEndDate, BigDecimal breakBlockedHours,
                                     int breakDurationMinutes, BigDecimal breakMinShiftHours,
                                     int breakClusterThresholdPct, BigDecimal defaultContractedHoursPerDay,
                                     int overallocationHardLimitPct, int underallocationHardLimitPct,
                                     ScheduleStatus status, String errorMessage, int version,
                                     SchedulingMode schedulingMode) {
        static ScheduleSnapshot of(Schedule s) {
            return new ScheduleSnapshot(s.getId(), s.getTenantId(), s.getDeskId(), s.getIncrementMinutes(),
                    s.getStartTime(), s.getEndTime(), s.getPeriodStartDate(), s.getPeriodEndDate(),
                    s.getBreakBlockedHours(), s.getBreakDurationMinutes(), s.getBreakMinShiftHours(),
                    s.getBreakClusterThresholdPct(), s.getDefaultContractedHoursPerDay(),
                    s.getOverallocationHardLimitPct(), s.getUnderallocationHardLimitPct(),
                    s.getStatus(), s.getErrorMessage(), s.getVersion(), s.getSchedulingMode());
        }
    }

    private record TimeslotSnapshot(UUID id, long tenantId, UUID deskId, UUID scheduleId, LocalDate date,
                                     LocalTime startTime, LocalTime endTime) {
        static TimeslotSnapshot of(Timeslot t) {
            return new TimeslotSnapshot(t.getId(), t.getTenantId(), t.getDeskId(), t.getScheduleId(),
                    t.getDate(), t.getStartTime(), t.getEndTime());
        }
    }

    private record StaffingRequirementSnapshot(UUID id, long tenantId, UUID deskId, UUID scheduleId,
                                                 UUID timeslotId, UUID specializationId, int requiredFTEs) {
        static StaffingRequirementSnapshot of(StaffingRequirement sr) {
            return new StaffingRequirementSnapshot(sr.getId(), sr.getTenantId(), sr.getDeskId(), sr.getScheduleId(),
                    sr.getTimeslot().getId(), sr.getSpecialization().getId(), sr.getRequiredFTEs());
        }
    }
}
