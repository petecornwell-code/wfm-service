package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.dto.ShiftTemplateRequest;
import com.wfm.dto.ShiftTemplateRequest.BreakBandRequest;
import com.wfm.model.Agent;
import com.wfm.model.AgentAssignment;
import com.wfm.model.AgentShiftAssignment;
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
import static org.mockito.Mockito.when;

/**
 * Phase 15 Plan 07 Task 1: proves the D-07 accept-time denormalised shift snapshot end to end —
 * an accepted shift-mode schedule records what the agent actually worked, immune to a later
 * template edit.
 *
 * <p>{@code ScheduleOutputService} is supplied as a {@code @MockitoBean} rather than the real
 * bean: its constructor requires a Timefold {@code SolverFactory<Schedule>}, which is normally
 * auto-configured by {@code timefold-solver-spring-boot-starter} outside the {@code @DataJpaTest}
 * slice — the same reason {@code ShiftTemplateServiceTest} mocks
 * {@code TimeslotGeneratorService.getLiveBounds} rather than exercising the real Postgres-only
 * native query under H2. This plan's scope (the repository, the accept-time write, and the
 * accepted-schedule reload) is exercised directly against
 * {@code AgentShiftAssignmentRepository} rather than through {@code ScheduleOutputService}'s
 * response shape, which is Plan 07 Task 2's concern.
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

    @MockitoBean
    private TimeslotGeneratorService timeslotGeneratorService;

    @MockitoBean
    private ScheduleOutputService scheduleOutputService;

    private static final long TENANT_A = 1L;
    private static final LocalDate MONDAY = LocalDate.now().plusMonths(1)
            .with(java.time.temporal.TemporalAdjusters.next(DayOfWeek.MONDAY));

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
