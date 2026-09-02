package com.wfm.service;

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.solver.SolverFactory;
import com.wfm.config.TenantContext;
import com.wfm.dto.ScheduleDetailResponse;
import com.wfm.dto.ScheduleDetailResponse.ConstraintViolationEntry;
import com.wfm.dto.ScheduleDetailResponse.ViolationDetail;
import com.wfm.dto.ShiftTemplateRequest;
import com.wfm.dto.ShiftTemplateRequest.BreakBandRequest;
import com.wfm.model.Agent;
import com.wfm.model.AgentAssignment;
import com.wfm.model.AgentShiftAssignment;
import com.wfm.model.ConstraintWeights;
import com.wfm.model.Desk;
import com.wfm.model.Schedule;
import com.wfm.model.ScheduleStatus;
import com.wfm.model.SchedulingMode;
import com.wfm.model.ShiftBandPair;
import com.wfm.model.ShiftTemplate;
import com.wfm.model.ShiftTemplateBreakBand;
import com.wfm.model.Specialization;
import com.wfm.model.Timeslot;
import com.wfm.repository.AgentRepository;
import com.wfm.repository.AgentShiftAssignmentRepository;
import com.wfm.repository.ConstraintWeightsRepository;
import com.wfm.repository.DeskRepository;
import com.wfm.repository.ShiftTemplateBreakBandRepository;
import com.wfm.repository.SpecializationRepository;
import com.wfm.repository.TimeslotRepository;
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
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;

/**
 * Phase 15 Plan 07: Task 1 proves the D-07 accept-time denormalised shift snapshot end to end —
 * an accepted shift-mode schedule records what the agent actually worked, immune to a later
 * template edit. Task 2 proves {@code schedulingMode} on {@code ScheduleDetailResponse} and the
 * per-entry {@code ShiftDescriptor} are populated correctly for both the in-memory and accepted
 * paths, from the one {@code ScheduleOutputService.buildAgentSchedule} builder.
 *
 * <p>{@code ScheduleOutputService} is supplied as a {@code @MockitoBean} for the {@code
 * ScheduleService}-level tests: its constructor requires a Timefold
 * {@code SolverFactory<Schedule>}, which is normally auto-configured by
 * {@code timefold-solver-spring-boot-starter} outside the {@code @DataJpaTest} slice — the same
 * reason {@code ShiftTemplateServiceTest} mocks {@code TimeslotGeneratorService.getLiveBounds}
 * rather than exercising the real Postgres-only native query under H2. Task 1's assertions are
 * exercised directly against {@code AgentShiftAssignmentRepository}. Task 2's
 * {@code buildAgentSchedule} assertions instantiate the REAL {@code ScheduleOutputService} plainly
 * (no Spring), backed by a real {@code SolverFactory.createFromXmlResource("solverConfig.xml")} —
 * mirroring {@code SolverConfigBuildTest} — since {@code buildAgentSchedule} needs no database and
 * no solved schedule, only the plain POJO graph it reads.
 */
@DataJpaTest
@Import({ScheduleService.class, InMemoryScheduleStore.class, ShiftTemplateService.class})
@ActiveProfiles("test")
class ScheduleServiceShiftSnapshotTest {

    @Autowired
    private ScheduleService scheduleService;

    @Autowired
    private ShiftTemplateService shiftTemplateService;

    @Autowired
    private InMemoryScheduleStore inMemoryStore;

    @Autowired
    private DeskRepository deskRepository;

    @Autowired
    private AgentRepository agentRepository;

    @Autowired
    private SpecializationRepository specializationRepository;

    @Autowired
    private TimeslotRepository timeslotRepository;

    @Autowired
    private ShiftTemplateBreakBandRepository shiftTemplateBreakBandRepository;

    @Autowired
    private AgentShiftAssignmentRepository agentShiftAssignmentRepository;

    @Autowired
    private ConstraintWeightsRepository constraintWeightsRepository;

    @MockitoBean
    private TimeslotGeneratorService timeslotGeneratorService;

    @MockitoBean
    private ScheduleOutputService scheduleOutputService;

    private static final long TENANT_A = 1L;
    private static final LocalDate MONDAY = LocalDate.now().plusMonths(1)
            .with(java.time.temporal.TemporalAdjusters.next(DayOfWeek.MONDAY));

    // Task 2's buildAgentSchedule assertions instantiate the real ScheduleOutputService plainly
    // (no Spring bean) — built once, mirroring SolverConfigBuildTest's own plain-JUnit factory.
    private static final SolverFactory<Schedule> SOLVER_FACTORY =
            SolverFactory.createFromXmlResource("solverConfig.xml");

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_A);
        // No live timeslots exist for any desk in this test — validateGridAlignment's own P-10
        // rule already skips the check in that case, but the underlying query is a Postgres-only
        // native query that cannot execute under H2 at all, so it is mocked regardless.
        when(timeslotGeneratorService.getLiveBounds(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Optional.empty());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void acceptSchedule_shiftMode_recordedEnvelopeSurvivesALaterTemplateEdit() {
        UUID deskId = saveDesk(TENANT_A, SchedulingMode.SHIFT);
        Agent agent = saveAgent(TENANT_A, deskId, "A1");
        ShiftTemplate template = saveTemplate(deskId, "Early", LocalTime.of(8, 0), LocalTime.of(17, 0),
                new BreakBandRequest(240, 60, null));

        UUID inMemoryScheduleId = UUID.randomUUID();
        Schedule schedule = buildInMemorySchedule(deskId, inMemoryScheduleId);
        schedule.setSchedulingMode(SchedulingMode.SHIFT);

        ShiftTemplateBreakBand band = shiftTemplateBreakBandRepository
                .findByTenantIdAndShiftTemplateIdOrderByOffsetMinutesAsc(TENANT_A, template.getId())
                .get(0);
        AgentShiftAssignment shiftAssignment = new AgentShiftAssignment();
        shiftAssignment.setId(UUID.randomUUID());
        shiftAssignment.setTenantId(TENANT_A);
        shiftAssignment.setDeskId(deskId);
        shiftAssignment.setScheduleId(inMemoryScheduleId);
        shiftAssignment.setAgent(agent);
        shiftAssignment.setDate(MONDAY);
        shiftAssignment.setShiftBandPair(new ShiftBandPair(template, band));
        schedule.setShiftAssignments(new ArrayList<>(List.of(shiftAssignment)));

        inMemoryStore.put(schedule);

        Schedule saved = scheduleService.acceptSchedule(deskId, inMemoryScheduleId, 0);

        List<AgentShiftAssignment> persistedBefore = agentShiftAssignmentRepository
                .findByTenantIdAndDeskIdAndScheduleId(TENANT_A, deskId, saved.getId());
        assertThat(persistedBefore).hasSize(1);
        AgentShiftAssignment rowBefore = persistedBefore.get(0);
        assertThat(rowBefore.getTemplateName()).isEqualTo("Early");
        assertThat(rowBefore.getShiftStartTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(rowBefore.getShiftEndTime()).isEqualTo(LocalTime.of(17, 0));
        assertThat(rowBefore.getBandOffsetMinutes()).isEqualTo(240);
        assertThat(rowBefore.getBandDurationMinutes()).isEqualTo(60);
        assertThat(rowBefore.getSourceTemplateId()).isEqualTo(template.getId());

        // Edit the live template to different start/end times through the same
        // ShiftTemplateService.updateShiftTemplate path an operator would use.
        ShiftTemplateRequest editRequest = new ShiftTemplateRequest(
                "Early", LocalTime.of(9, 0), LocalTime.of(18, 0),
                List.of(new BreakBandRequest(240, 60, null)),
                Set.of(DayOfWeek.values()),
                LocalDate.now().minusDays(30), null);
        shiftTemplateService.updateShiftTemplate(deskId, template.getId(), editRequest);

        List<AgentShiftAssignment> persistedAfter = agentShiftAssignmentRepository
                .findByTenantIdAndDeskIdAndScheduleId(TENANT_A, deskId, saved.getId());
        assertThat(persistedAfter).hasSize(1);
        AgentShiftAssignment rowAfter = persistedAfter.get(0);
        assertThat(rowAfter.getTemplateName()).isEqualTo("Early");
        assertThat(rowAfter.getShiftStartTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(rowAfter.getShiftEndTime()).isEqualTo(LocalTime.of(17, 0));
        assertThat(rowAfter.getBandOffsetMinutes()).isEqualTo(240);
        assertThat(rowAfter.getBandDurationMinutes()).isEqualTo(60);
    }

    @Test
    void acceptSchedule_shiftMode_persistedRowsCarryTheSavedScheduleIdNotTheInMemoryOne() {
        UUID deskId = saveDesk(TENANT_A, SchedulingMode.SHIFT);
        Agent agent = saveAgent(TENANT_A, deskId, "A1");
        ShiftTemplate template = saveTemplate(deskId, "Early", LocalTime.of(8, 0), LocalTime.of(17, 0));

        UUID inMemoryScheduleId = UUID.randomUUID();
        Schedule schedule = buildInMemorySchedule(deskId, inMemoryScheduleId);
        schedule.setSchedulingMode(SchedulingMode.SHIFT);

        AgentShiftAssignment shiftAssignment = new AgentShiftAssignment();
        shiftAssignment.setId(UUID.randomUUID());
        shiftAssignment.setTenantId(TENANT_A);
        shiftAssignment.setDeskId(deskId);
        shiftAssignment.setScheduleId(inMemoryScheduleId);
        shiftAssignment.setAgent(agent);
        shiftAssignment.setDate(MONDAY);
        shiftAssignment.setShiftBandPair(new ShiftBandPair(template, null));
        schedule.setShiftAssignments(new ArrayList<>(List.of(shiftAssignment)));

        inMemoryStore.put(schedule);

        Schedule saved = scheduleService.acceptSchedule(deskId, inMemoryScheduleId, 0);

        // acceptSchedule remaps the schedule to a freshly generated id (schedule.setId(null)
        // before persist) — the accepted rows must carry THAT id, never the in-memory one.
        assertThat(saved.getId()).isNotEqualTo(inMemoryScheduleId);

        List<AgentShiftAssignment> rowsForSavedId = agentShiftAssignmentRepository
                .findByTenantIdAndDeskIdAndScheduleId(TENANT_A, deskId, saved.getId());
        assertThat(rowsForSavedId).hasSize(1);

        List<AgentShiftAssignment> rowsForOldId = agentShiftAssignmentRepository
                .findByTenantIdAndDeskIdAndScheduleId(TENANT_A, deskId, inMemoryScheduleId);
        assertThat(rowsForOldId).isEmpty();
    }

    @Test
    void acceptSchedule_slotMode_writesZeroShiftRowsAndLeavesAssignmentSnapshotUnchanged() {
        UUID deskId = saveDesk(TENANT_A, SchedulingMode.SLOT);
        Agent agent = saveAgent(TENANT_A, deskId, "A1");
        Specialization spec = saveSpecialization(TENANT_A, deskId, "S1");

        Timeslot liveTimeslot = new Timeslot();
        liveTimeslot.setTenantId(TENANT_A);
        liveTimeslot.setDeskId(deskId);
        liveTimeslot.setDate(MONDAY);
        liveTimeslot.setStartTime(LocalTime.of(8, 0));
        liveTimeslot.setEndTime(LocalTime.of(9, 0));
        liveTimeslot = timeslotRepository.save(liveTimeslot);

        UUID inMemoryScheduleId = UUID.randomUUID();
        Schedule schedule = buildInMemorySchedule(deskId, inMemoryScheduleId);
        // SLOT-mode desks always reach acceptSchedule with an empty shiftAssignments list
        // (SolverService.buildShiftAssignments is mode-gated) — asserted here directly.
        schedule.setShiftAssignments(new ArrayList<>());

        AgentAssignment seat = new AgentAssignment();
        seat.setId(UUID.randomUUID());
        seat.setTenantId(TENANT_A);
        seat.setDeskId(deskId);
        seat.setScheduleId(inMemoryScheduleId);
        seat.setTimeslot(liveTimeslot);
        seat.setRequiredSpecialization(spec);
        seat.setAgent(agent);
        schedule.setAssignments(new ArrayList<>(List.of(seat)));

        inMemoryStore.put(schedule);

        Schedule saved = scheduleService.acceptSchedule(deskId, inMemoryScheduleId, 0);

        assertThat(agentShiftAssignmentRepository
                .findByTenantIdAndDeskIdAndScheduleId(TENANT_A, deskId, saved.getId()))
                .isEmpty();

        // The pre-existing AgentAssignment snapshot behaviour is untouched: the seat is still
        // persisted, remapped onto the new snapshot timeslot, field-for-field identical to today.
        List<AgentAssignment> persistedAssignments = agentAssignmentRepository
                .findByTenantIdAndDeskIdAndScheduleId(TENANT_A, deskId, saved.getId());
        assertThat(persistedAssignments).hasSize(1);
        assertThat(persistedAssignments.get(0).getAgent().getId()).isEqualTo(agent.getId());
    }

    @Test
    void deleteSchedule_shiftMode_deletesAgentShiftAssignmentRows() {
        // CR-03 regression: before the fix, deleteSchedule never called any deleteBy* method on
        // AgentShiftAssignmentRepository (it exposed none), so agent_shift_assignment rows for a
        // deleted accepted SHIFT-mode schedule were silently orphaned (schedule_id carries no FK,
        // per V41, so the schedule row's own deletion cannot cascade to them).
        UUID deskId = saveDesk(TENANT_A, SchedulingMode.SHIFT);
        Agent agent = saveAgent(TENANT_A, deskId, "A1");
        ShiftTemplate template = saveTemplate(deskId, "Early", LocalTime.of(8, 0), LocalTime.of(17, 0));

        UUID inMemoryScheduleId = UUID.randomUUID();
        Schedule schedule = buildInMemorySchedule(deskId, inMemoryScheduleId);
        schedule.setSchedulingMode(SchedulingMode.SHIFT);

        AgentShiftAssignment shiftAssignment = new AgentShiftAssignment();
        shiftAssignment.setId(UUID.randomUUID());
        shiftAssignment.setTenantId(TENANT_A);
        shiftAssignment.setDeskId(deskId);
        shiftAssignment.setScheduleId(inMemoryScheduleId);
        shiftAssignment.setAgent(agent);
        shiftAssignment.setDate(MONDAY);
        shiftAssignment.setShiftBandPair(new ShiftBandPair(template, null));
        schedule.setShiftAssignments(new ArrayList<>(List.of(shiftAssignment)));

        inMemoryStore.put(schedule);
        Schedule saved = scheduleService.acceptSchedule(deskId, inMemoryScheduleId, 0);

        assertThat(agentShiftAssignmentRepository
                .findByTenantIdAndDeskIdAndScheduleId(TENANT_A, deskId, saved.getId()))
                .hasSize(1);

        scheduleService.deleteSchedule(deskId, saved.getId());

        assertThat(agentShiftAssignmentRepository
                .findByTenantIdAndDeskIdAndScheduleId(TENANT_A, deskId, saved.getId()))
                .isEmpty();
    }

    // ---------- Task 2: schedulingMode and the per-entry shift descriptor ----------

    @Test
    void buildAgentSchedule_shiftModeInMemory_returnsShiftDescriptorOnEveryWorkingAgentDay() {
        Agent agent = new Agent();
        agent.setId(UUID.randomUUID());
        agent.setName("Ana");

        Specialization spec = new Specialization();
        spec.setId(UUID.randomUUID());
        spec.setName("S1");

        Timeslot ts = new Timeslot();
        ts.setId(UUID.randomUUID());
        ts.setDate(MONDAY);
        ts.setStartTime(LocalTime.of(8, 0));
        ts.setEndTime(LocalTime.of(9, 0));

        AgentAssignment assignment = new AgentAssignment();
        assignment.setId(UUID.randomUUID());
        assignment.setTimeslot(ts);
        assignment.setRequiredSpecialization(spec);
        assignment.setAgent(agent);

        ShiftTemplate template = new ShiftTemplate();
        template.setValidWeekdays(java.util.EnumSet.allOf(java.time.DayOfWeek.class));
        template.setId(UUID.randomUUID());
        template.setName("Early");
        template.setStartTime(LocalTime.of(8, 0));
        template.setEndTime(LocalTime.of(17, 0));

        ShiftTemplateBreakBand band = new ShiftTemplateBreakBand();
        band.setId(UUID.randomUUID());
        band.setOffsetMinutes(240);
        band.setDurationMinutes(60);

        AgentShiftAssignment shiftAssignment = new AgentShiftAssignment();
        shiftAssignment.setId(UUID.randomUUID());
        shiftAssignment.setAgent(agent);
        shiftAssignment.setDate(MONDAY);
        shiftAssignment.setShiftBandPair(new ShiftBandPair(template, band));

        Schedule schedule = new Schedule();
        schedule.setIncrementMinutes(60);
        schedule.setAssignments(new ArrayList<>(List.of(assignment)));
        schedule.setShiftAssignments(new ArrayList<>(List.of(shiftAssignment)));

        List<ScheduleDetailResponse.AgentScheduleEntry> entries =
                realOutputService().buildAgentSchedule(schedule);

        assertThat(entries).hasSize(1);
        ScheduleDetailResponse.ShiftDescriptor shift = entries.get(0).shift();
        assertThat(shift).isNotNull();
        assertThat(shift.sourceTemplateId()).isEqualTo(template.getId());
        assertThat(shift.templateName()).isEqualTo("Early");
        assertThat(shift.startTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(shift.endTime()).isEqualTo(LocalTime.of(17, 0));
        assertThat(shift.bandOffsetMinutes()).isEqualTo(240);
        assertThat(shift.bandDurationMinutes()).isEqualTo(60);
    }

    @Test
    void buildAgentSchedule_afterAcceptAndReload_returnsIdenticalDescriptorReadFromDenormalisedRow() {
        Agent agent = new Agent();
        agent.setId(UUID.randomUUID());
        agent.setName("Ana");

        Specialization spec = new Specialization();
        spec.setId(UUID.randomUUID());
        spec.setName("S1");

        Timeslot ts = new Timeslot();
        ts.setId(UUID.randomUUID());
        ts.setDate(MONDAY);
        ts.setStartTime(LocalTime.of(8, 0));
        ts.setEndTime(LocalTime.of(9, 0));

        AgentAssignment assignment = new AgentAssignment();
        assignment.setId(UUID.randomUUID());
        assignment.setTimeslot(ts);
        assignment.setRequiredSpecialization(spec);
        assignment.setAgent(agent);

        // Simulates a row reloaded via loadSnapshotData: transient shiftBandPair is null (JPA
        // never populates @Transient fields), only the D-07 denormalised scalars are present.
        UUID sourceTemplateId = UUID.randomUUID();
        AgentShiftAssignment shiftAssignment = new AgentShiftAssignment();
        shiftAssignment.setId(UUID.randomUUID());
        shiftAssignment.setAgent(agent);
        shiftAssignment.setDate(MONDAY);
        shiftAssignment.setShiftBandPair(null);
        shiftAssignment.setTemplateName("Early");
        shiftAssignment.setShiftStartTime(LocalTime.of(8, 0));
        shiftAssignment.setShiftEndTime(LocalTime.of(17, 0));
        shiftAssignment.setBandOffsetMinutes(240);
        shiftAssignment.setBandDurationMinutes(60);
        shiftAssignment.setSourceTemplateId(sourceTemplateId);

        Schedule schedule = new Schedule();
        schedule.setIncrementMinutes(60);
        schedule.setAssignments(new ArrayList<>(List.of(assignment)));
        schedule.setShiftAssignments(new ArrayList<>(List.of(shiftAssignment)));

        List<ScheduleDetailResponse.AgentScheduleEntry> entries =
                realOutputService().buildAgentSchedule(schedule);

        assertThat(entries).hasSize(1);
        ScheduleDetailResponse.ShiftDescriptor shift = entries.get(0).shift();
        assertThat(shift).isNotNull();
        assertThat(shift.sourceTemplateId()).isEqualTo(sourceTemplateId);
        assertThat(shift.templateName()).isEqualTo("Early");
        assertThat(shift.startTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(shift.endTime()).isEqualTo(LocalTime.of(17, 0));
        assertThat(shift.bandOffsetMinutes()).isEqualTo(240);
        assertThat(shift.bandDurationMinutes()).isEqualTo(60);
    }

    @Test
    void buildAgentSchedule_slotMode_returnsNullShiftOnEveryEntryAndOtherFieldsUnchanged() {
        Agent agent = new Agent();
        agent.setId(UUID.randomUUID());
        agent.setName("Ana");

        Specialization spec = new Specialization();
        spec.setId(UUID.randomUUID());
        spec.setName("S1");

        Timeslot ts = new Timeslot();
        ts.setId(UUID.randomUUID());
        ts.setDate(MONDAY);
        ts.setStartTime(LocalTime.of(8, 0));
        ts.setEndTime(LocalTime.of(9, 0));

        AgentAssignment assignment = new AgentAssignment();
        assignment.setId(UUID.randomUUID());
        assignment.setTimeslot(ts);
        assignment.setRequiredSpecialization(spec);
        assignment.setAgent(agent);

        Schedule schedule = new Schedule();
        schedule.setIncrementMinutes(60);
        schedule.setAssignments(new ArrayList<>(List.of(assignment)));
        schedule.setShiftAssignments(new ArrayList<>()); // SLOT-mode desks always reach here empty

        List<ScheduleDetailResponse.AgentScheduleEntry> entries =
                realOutputService().buildAgentSchedule(schedule);

        assertThat(entries).hasSize(1);
        ScheduleDetailResponse.AgentScheduleEntry entry = entries.get(0);
        assertThat(entry.shift()).isNull();
        // Every other field is unchanged from today's expectations (Phase 14 behaviour).
        assertThat(entry.agentName()).isEqualTo("Ana");
        assertThat(entry.date()).isEqualTo(MONDAY);
        assertThat(entry.shiftStart()).isEqualTo(LocalTime.of(8, 0));
        assertThat(entry.shiftEnd()).isEqualTo(LocalTime.of(9, 0));
        assertThat(entry.assignments()).hasSize(1);
        assertThat(entry.breaks()).isEmpty();
    }

    @Test
    void getScheduleDetail_inMemoryShiftModeSchedule_reportsSchedulingModeShift() {
        UUID deskId = saveDesk(TENANT_A, SchedulingMode.SHIFT);
        UUID scheduleId = UUID.randomUUID();
        Schedule schedule = buildInMemorySchedule(deskId, scheduleId);
        schedule.setSchedulingMode(SchedulingMode.SHIFT);
        inMemoryStore.put(schedule);

        ScheduleDetailResponse response = scheduleService.getScheduleDetail(deskId, scheduleId, null);

        assertThat(response.getSchedulingMode()).isEqualTo("SHIFT");
    }

    @Test
    void getScheduleDetail_inMemorySlotModeSchedule_reportsSchedulingModeSlot() {
        UUID deskId = saveDesk(TENANT_A, SchedulingMode.SLOT);
        UUID scheduleId = UUID.randomUUID();
        Schedule schedule = buildInMemorySchedule(deskId, scheduleId);
        schedule.setSchedulingMode(SchedulingMode.SLOT);
        inMemoryStore.put(schedule);

        ScheduleDetailResponse response = scheduleService.getScheduleDetail(deskId, scheduleId, null);

        assertThat(response.getSchedulingMode()).isEqualTo("SLOT");
    }

    @Test
    void getScheduleDetail_acceptedShiftModeSchedule_reportsPersistedSchedulingMode() {
        // CR-02 gap closure rename: this test previously asserted the OLD inferred-from-
        // shift-row-presence mechanism (hence its old name,
        // ...derivesSchedulingModeFromShiftRowPresence) — that inference is exactly the CR-02
        // defect (see the dedicated zero-shift regression test below), so this test now sets
        // schedulingMode explicitly on the in-memory schedule (mirroring what
        // SolverService.buildSchedule always does before a real solve) and asserts it survives
        // accept + reload via the new persisted scheduling_mode column (V43), not derivation.
        UUID deskId = saveDesk(TENANT_A, SchedulingMode.SHIFT);
        Agent agent = saveAgent(TENANT_A, deskId, "A1");
        ShiftTemplate template = saveTemplate(deskId, "Early", LocalTime.of(8, 0), LocalTime.of(17, 0));

        UUID inMemoryScheduleId = UUID.randomUUID();
        Schedule schedule = buildInMemorySchedule(deskId, inMemoryScheduleId);
        schedule.setSchedulingMode(SchedulingMode.SHIFT);
        AgentShiftAssignment shiftAssignment = new AgentShiftAssignment();
        shiftAssignment.setId(UUID.randomUUID());
        shiftAssignment.setTenantId(TENANT_A);
        shiftAssignment.setDeskId(deskId);
        shiftAssignment.setScheduleId(inMemoryScheduleId);
        shiftAssignment.setAgent(agent);
        shiftAssignment.setDate(MONDAY);
        shiftAssignment.setShiftBandPair(new ShiftBandPair(template, null));
        schedule.setShiftAssignments(new ArrayList<>(List.of(shiftAssignment)));

        inMemoryStore.put(schedule);
        Schedule saved = scheduleService.acceptSchedule(deskId, inMemoryScheduleId, 0);

        ScheduleDetailResponse response = scheduleService.getScheduleDetail(deskId, saved.getId(), null);

        assertThat(response.getSchedulingMode()).isEqualTo("SHIFT");
        assertFeasibleImpliesNoViolatedHardConstraints(response);
    }

    @Test
    void getScheduleDetail_acceptedShiftModeSchedule_zeroPlacedShifts_stillReportsShift() {
        // CR-02 regression: the exact case the old inference got wrong. acceptSchedule is
        // reachable for any COMPLETED/STOPPED schedule regardless of feasibility, and only ever
        // writes an agent_shift_assignment row when shiftAssignment.getShiftBandPair() is
        // non-null — a SHIFT-mode solve stopped early (or whose live library matches no agent's
        // contracted hours) can legitimately reach COMPLETED/STOPPED with zero placed shifts.
        // Before the fix, loadSnapshotData inferred schedulingMode from
        // shiftAssignments.isEmpty(), so this exact scenario read back mislabeled SLOT. With the
        // fix, schedulingMode is a persisted column written once at accept time from the
        // in-memory schedule's own recorded mode, independent of how many (if any) shift rows
        // were written.
        UUID deskId = saveDesk(TENANT_A, SchedulingMode.SHIFT);
        Agent agent = saveAgent(TENANT_A, deskId, "A1");
        ShiftTemplate template = saveTemplate(deskId, "Early", LocalTime.of(8, 0), LocalTime.of(17, 0));

        UUID inMemoryScheduleId = UUID.randomUUID();
        Schedule schedule = buildInMemorySchedule(deskId, inMemoryScheduleId);
        schedule.setSchedulingMode(SchedulingMode.SHIFT);

        // A shift assignment row exists on the in-memory solution, but its shiftBandPair is null
        // — e.g. the construction heuristic never got to it before the solve stopped. acceptSchedule
        // skips any such row (pair == null), so zero agent_shift_assignment rows are written.
        AgentShiftAssignment unplacedShiftAssignment = new AgentShiftAssignment();
        unplacedShiftAssignment.setId(UUID.randomUUID());
        unplacedShiftAssignment.setTenantId(TENANT_A);
        unplacedShiftAssignment.setDeskId(deskId);
        unplacedShiftAssignment.setScheduleId(inMemoryScheduleId);
        unplacedShiftAssignment.setAgent(agent);
        unplacedShiftAssignment.setDate(MONDAY);
        unplacedShiftAssignment.setShiftBandPair(null);
        schedule.setShiftAssignments(new ArrayList<>(List.of(unplacedShiftAssignment)));
        schedule.setAssignments(new ArrayList<>());

        inMemoryStore.put(schedule);
        Schedule saved = scheduleService.acceptSchedule(deskId, inMemoryScheduleId, 0);

        // Zero agent_shift_assignment rows were actually persisted — confirms this test exercises
        // the exact CR-02 scenario, not the ordinary "at least one placed shift" case above.
        assertThat(agentShiftAssignmentRepository
                .findByTenantIdAndDeskIdAndScheduleId(TENANT_A, deskId, saved.getId()))
                .isEmpty();

        ScheduleDetailResponse response = scheduleService.getScheduleDetail(deskId, saved.getId(), null);

        assertThat(response.getSchedulingMode()).isEqualTo("SHIFT");
        assertFeasibleImpliesNoViolatedHardConstraints(response);
    }

    @Test
    void getScheduleDetail_acceptedSlotModeSchedule_reportsSchedulingModeSlot() {
        UUID deskId = saveDesk(TENANT_A, SchedulingMode.SLOT);

        UUID inMemoryScheduleId = UUID.randomUUID();
        Schedule schedule = buildInMemorySchedule(deskId, inMemoryScheduleId);
        schedule.setShiftAssignments(new ArrayList<>());
        schedule.setAssignments(new ArrayList<>());

        inMemoryStore.put(schedule);
        Schedule saved = scheduleService.acceptSchedule(deskId, inMemoryScheduleId, 0);

        ScheduleDetailResponse response = scheduleService.getScheduleDetail(deskId, saved.getId(), null);

        assertThat(response.getSchedulingMode()).isEqualTo("SLOT");
        assertFeasibleImpliesNoViolatedHardConstraints(response);
    }

    // ---------- Task 2 (G-15-32 gap closure): read-path invariant + red-proof ----------
    //
    // End-to-end through getScheduleDetail, exercising the REAL ScheduleOutputService accepted
    // path (Task 1) rather than the @MockitoBean default — scheduleOutputService.
    // buildConstraintViolations is stubbed to DELEGATE to a real instance so this DB-backed
    // accept+reload round trip genuinely proves the fix, not just the derivation logic in
    // ScheduleService (which was never broken).

    /**
     * G-15-32's own invariant: a schedule that reads {@code feasible: true} must never also name
     * a violated hard constraint. Applied to every accepted-schedule assertion in this class
     * (including the pre-existing ones above) so a future regression anywhere in the accepted
     * read path trips this, not only the two dedicated cases below.
     */
    private static void assertFeasibleImpliesNoViolatedHardConstraints(ScheduleDetailResponse response) {
        if (Boolean.TRUE.equals(response.getFeasible())) {
            assertThat(response.getViolatedHardConstraints())
                    .as("G-15-32: a feasible schedule must never report a violated hard constraint")
                    .isEmpty();
        }
    }

    /** Delegates the mocked buildConstraintViolations to a real instance for this test only. */
    private void stubRealConstraintViolations() {
        ScheduleOutputService real = realOutputService();
        when(scheduleOutputService.buildConstraintViolations(any(), anyBoolean()))
                .thenAnswer(inv -> real.buildConstraintViolations(inv.getArgument(0), inv.getArgument(1)));
    }

    private void saveDefaultConstraintWeights(long tenantId, UUID deskId) {
        ConstraintWeights weights = new ConstraintWeights();
        weights.setTenantId(tenantId);
        weights.setDeskId(deskId);
        constraintWeightsRepository.save(weights);
    }

    private List<Timeslot> saveTimeslots(UUID deskId, LocalDate date, List<LocalTime> starts) {
        List<Timeslot> saved = new ArrayList<>();
        for (LocalTime start : starts) {
            Timeslot ts = new Timeslot();
            ts.setTenantId(TENANT_A);
            ts.setDeskId(deskId);
            ts.setDate(date);
            ts.setStartTime(start);
            ts.setEndTime(start.plusHours(1));
            saved.add(timeslotRepository.save(ts));
        }
        return saved;
    }

    private List<AgentAssignment> heldSeatAssignments(Agent agent, Specialization spec, UUID deskId,
            UUID scheduleId, List<Timeslot> timeslots) {
        List<AgentAssignment> seats = new ArrayList<>();
        for (Timeslot ts : timeslots) {
            AgentAssignment seat = new AgentAssignment();
            seat.setId(UUID.randomUUID());
            seat.setTenantId(TENANT_A);
            seat.setDeskId(deskId);
            seat.setScheduleId(scheduleId);
            seat.setTimeslot(ts);
            seat.setRequiredSpecialization(spec);
            seat.setAgent(agent);
            seats.add(seat);
        }
        return seats;
    }

    @Test
    void getScheduleDetail_acceptedNamedRowShape_feasibleTrueImpliesNoViolatedHardConstraints() {
        // Named-row regression (G-15-32 15-UAT.md): Armaz Dugashvili, 2026-01-05, shift
        // "Mid 11:00-20:00", bandOffset 300 (break 16:00-17:00), held seats
        // 11,12,13,14,15,17,18,19 -- every seat inside the envelope, none in the break window.
        // Before the fix ALL EIGHT were reported as violations (the constant-1104 arithmetic,
        // N*H for N=1 agent-day and H=8 legal seats); after the fix this accepted schedule
        // reports zero, matching its own (feasible) persisted score.
        UUID deskId = saveDesk(TENANT_A, SchedulingMode.SHIFT);
        Agent agent = saveAgent(TENANT_A, deskId, "Armaz");
        Specialization spec = saveSpecialization(TENANT_A, deskId, "S1");
        saveDefaultConstraintWeights(TENANT_A, deskId);
        ShiftTemplate template = saveTemplate(deskId, "Mid", LocalTime.of(11, 0), LocalTime.of(20, 0),
                new BreakBandRequest(300, 60, null));
        ShiftTemplateBreakBand band = shiftTemplateBreakBandRepository
                .findByTenantIdAndShiftTemplateIdOrderByOffsetMinutesAsc(TENANT_A, template.getId())
                .get(0);

        List<Timeslot> heldTimeslots = saveTimeslots(deskId, MONDAY, List.of(
                LocalTime.of(11, 0), LocalTime.of(12, 0), LocalTime.of(13, 0), LocalTime.of(14, 0),
                LocalTime.of(15, 0), LocalTime.of(17, 0), LocalTime.of(18, 0), LocalTime.of(19, 0)));

        UUID inMemoryScheduleId = UUID.randomUUID();
        Schedule schedule = buildInMemorySchedule(deskId, inMemoryScheduleId);
        schedule.setSchedulingMode(SchedulingMode.SHIFT);
        schedule.setScore(HardSoftScore.ZERO);

        AgentShiftAssignment shiftAssignment = new AgentShiftAssignment();
        shiftAssignment.setId(UUID.randomUUID());
        shiftAssignment.setTenantId(TENANT_A);
        shiftAssignment.setDeskId(deskId);
        shiftAssignment.setScheduleId(inMemoryScheduleId);
        shiftAssignment.setAgent(agent);
        shiftAssignment.setDate(MONDAY);
        shiftAssignment.setShiftBandPair(new ShiftBandPair(template, band));
        schedule.setShiftAssignments(new ArrayList<>(List.of(shiftAssignment)));
        schedule.setAssignments(new ArrayList<>(
                heldSeatAssignments(agent, spec, deskId, inMemoryScheduleId, heldTimeslots)));

        inMemoryStore.put(schedule);
        Schedule saved = scheduleService.acceptSchedule(deskId, inMemoryScheduleId, 0);

        stubRealConstraintViolations();

        ScheduleDetailResponse response = scheduleService.getScheduleDetail(deskId, saved.getId(), null);

        assertThat(response.getFeasible()).isTrue();
        assertThat(response.getConstraintViolations())
                .as("constant-1104 regression: N*H (1 agent-day x 8 legal seats = 8) must never appear")
                .isEmpty();
        assertThat(response.getViolatedHardConstraints()).isEmpty();
        assertFeasibleImpliesNoViolatedHardConstraints(response);
    }

    @Test
    void getScheduleDetail_acceptedRedProof_oneOutOfEnvelopeSeatReportsExactlyOneNamedViolation() {
        // The load-bearing proof that the accepted path can still go non-empty (Task 2's own
        // done-criterion): without this, "the accepted path is correct" and "the accepted path
        // returns nothing" are indistinguishable. Same named-row shape as above, but the 11:00
        // seat is relocated to 09:00 -- BEFORE the 11:00 envelope start -- via a live timeslot the
        // solver would never have placed there.
        UUID deskId = saveDesk(TENANT_A, SchedulingMode.SHIFT);
        Agent agent = saveAgent(TENANT_A, deskId, "Armaz");
        Specialization spec = saveSpecialization(TENANT_A, deskId, "S1");
        saveDefaultConstraintWeights(TENANT_A, deskId);
        ShiftTemplate template = saveTemplate(deskId, "Mid", LocalTime.of(11, 0), LocalTime.of(20, 0),
                new BreakBandRequest(300, 60, null));
        ShiftTemplateBreakBand band = shiftTemplateBreakBandRepository
                .findByTenantIdAndShiftTemplateIdOrderByOffsetMinutesAsc(TENANT_A, template.getId())
                .get(0);

        List<Timeslot> heldTimeslots = saveTimeslots(deskId, MONDAY, List.of(
                LocalTime.of(9, 0), LocalTime.of(12, 0), LocalTime.of(13, 0), LocalTime.of(14, 0),
                LocalTime.of(15, 0), LocalTime.of(17, 0), LocalTime.of(18, 0), LocalTime.of(19, 0)));

        UUID inMemoryScheduleId = UUID.randomUUID();
        Schedule schedule = buildInMemorySchedule(deskId, inMemoryScheduleId);
        schedule.setSchedulingMode(SchedulingMode.SHIFT);
        // Genuinely infeasible: exactly one envelope breach at the desk's own weight (ofHard(1)).
        schedule.setScore(HardSoftScore.ofHard(-1));

        AgentShiftAssignment shiftAssignment = new AgentShiftAssignment();
        shiftAssignment.setId(UUID.randomUUID());
        shiftAssignment.setTenantId(TENANT_A);
        shiftAssignment.setDeskId(deskId);
        shiftAssignment.setScheduleId(inMemoryScheduleId);
        shiftAssignment.setAgent(agent);
        shiftAssignment.setDate(MONDAY);
        shiftAssignment.setShiftBandPair(new ShiftBandPair(template, band));
        schedule.setShiftAssignments(new ArrayList<>(List.of(shiftAssignment)));
        schedule.setAssignments(new ArrayList<>(
                heldSeatAssignments(agent, spec, deskId, inMemoryScheduleId, heldTimeslots)));

        inMemoryStore.put(schedule);
        Schedule saved = scheduleService.acceptSchedule(deskId, inMemoryScheduleId, 0);

        stubRealConstraintViolations();

        ScheduleDetailResponse response = scheduleService.getScheduleDetail(deskId, saved.getId(), null);

        assertThat(response.getFeasible()).isFalse();
        assertThat(response.getConstraintViolations()).hasSize(1);
        ConstraintViolationEntry entry = response.getConstraintViolations().get(0);
        assertThat(entry.constraintName()).isEqualTo("Shift envelope compliance");
        assertThat(entry.level()).isEqualTo("HARD");
        assertThat(entry.violationCount()).isEqualTo(1);
        assertThat(entry.violations()).hasSize(1);
        ViolationDetail detail = entry.violations().get(0);
        assertThat(detail.agentId()).isEqualTo(agent.getId());
        // The snapshot timeslot carries a freshly generated id (accept-time remap) — the relocated
        // seat's identity is proven through its start time, which the remap preserves verbatim.
        assertThat(detail.timeslotLabel()).contains("09:00");
        assertThat(response.getViolatedHardConstraints()).containsExactly("Shift envelope compliance");
        assertFeasibleImpliesNoViolatedHardConstraints(response);
    }

    @Test
    void getScheduleDetail_inMemoryFeasibleSchedule_feasibleTrueImpliesNoViolatedHardConstraints() {
        // Live in-memory side of the read-path invariant (behaviour unchanged by Task 1 -- the
        // live branch of buildConstraintViolations was not touched). The default @MockitoBean
        // answer for buildConstraintViolations is an empty list, matching a genuinely clean solve.
        UUID deskId = saveDesk(TENANT_A, SchedulingMode.SHIFT);
        UUID scheduleId = UUID.randomUUID();
        Schedule schedule = buildInMemorySchedule(deskId, scheduleId);
        schedule.setSchedulingMode(SchedulingMode.SHIFT);
        schedule.setScore(HardSoftScore.ZERO);
        inMemoryStore.put(schedule);

        ScheduleDetailResponse response = scheduleService.getScheduleDetail(deskId, scheduleId, null);

        assertThat(response.getFeasible()).isTrue();
        assertFeasibleImpliesNoViolatedHardConstraints(response);
    }

    private ScheduleOutputService realOutputService() {
        return new ScheduleOutputService(SOLVER_FACTORY);
    }

    // ---------- helpers ----------

    @Autowired
    private com.wfm.repository.AgentAssignmentRepository agentAssignmentRepository;

    private Schedule buildInMemorySchedule(UUID deskId, UUID scheduleId) {
        Schedule schedule = new Schedule();
        schedule.setId(scheduleId);
        schedule.setTenantId(TENANT_A);
        schedule.setDeskId(deskId);
        schedule.setIncrementMinutes(60);
        schedule.setStartTime(LocalTime.of(8, 0));
        schedule.setEndTime(LocalTime.of(17, 0));
        schedule.setPeriodStartDate(MONDAY);
        schedule.setPeriodEndDate(MONDAY);
        schedule.setStatus(ScheduleStatus.COMPLETED);
        schedule.setVersion(0);
        schedule.setCreatedAt(OffsetDateTime.now());
        return schedule;
    }

    private UUID saveDesk(long tenantId, SchedulingMode mode) {
        Desk desk = new Desk();
        desk.setTenantId(tenantId);
        desk.setName("Desk " + UUID.randomUUID());
        desk.setSchedulingMode(mode);
        return deskRepository.save(desk).getId();
    }

    private Agent saveAgent(long tenantId, UUID deskId, String bamboohrId) {
        Agent agent = new Agent();
        agent.setTenantId(tenantId);
        agent.setDeskId(deskId);
        agent.setBamboohrId(bamboohrId);
        agent.setName("Agent " + bamboohrId);
        return agentRepository.save(agent);
    }

    private Specialization saveSpecialization(long tenantId, UUID deskId, String name) {
        Specialization spec = new Specialization();
        spec.setTenantId(tenantId);
        spec.setDeskId(deskId);
        spec.setName(name);
        return specializationRepository.save(spec);
    }

    private ShiftTemplate saveTemplate(UUID deskId, String name, LocalTime start, LocalTime end,
                                        BreakBandRequest... bands) {
        ShiftTemplateRequest request = new ShiftTemplateRequest(
                name, start, end, List.of(bands), Set.of(DayOfWeek.values()),
                LocalDate.now().minusDays(30), null);
        return shiftTemplateService.createShiftTemplate(deskId, request);
    }
}
