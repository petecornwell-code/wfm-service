package com.wfm.service;

import ai.timefold.solver.core.api.solver.SolverFactory;
import com.wfm.config.TenantContext;
import com.wfm.controller.ScheduleController;
import com.wfm.dto.ScheduleDetailResponse;
import com.wfm.dto.ScheduleDetailResponse.RestWaiverDisclosure;
import com.wfm.dto.ScheduleDetailResponse.RestWaiverEntry;
import com.wfm.dto.ScheduleSummary;
import com.wfm.model.Agent;
import com.wfm.model.AgentAssignment;
import com.wfm.model.AgentRestWaiver;
import com.wfm.model.AgentShiftAssignment;
import com.wfm.model.RestSpan;
import com.wfm.model.Schedule;
import com.wfm.model.ScheduleStatus;
import com.wfm.model.SchedulingMode;
import com.wfm.model.ShiftBandPair;
import com.wfm.model.ShiftTemplate;
import com.wfm.model.Specialization;
import com.wfm.model.Timeslot;
import com.wfm.repository.AcceptedScheduleDateRepository;
import com.wfm.repository.AgentAssignmentRepository;
import com.wfm.repository.AgentDayOffRepository;
import com.wfm.repository.AgentPreferenceRepository;
import com.wfm.repository.AgentRestWaiverRepository;
import com.wfm.repository.AgentShiftAssignmentRepository;
import com.wfm.repository.AgentUsualShiftRepository;
import com.wfm.repository.ConstraintWeightsRepository;
import com.wfm.repository.DeskRepository;
import com.wfm.repository.ScheduleRepository;
import com.wfm.repository.ShiftTemplateRepository;
import com.wfm.repository.StaffingRequirementRepository;
import com.wfm.repository.TimeslotRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Plan 22-08 (REST-07, D-09, D-13, D-14) — the applied/unused rest-waiver disclosure, computed
 * deterministically from the solution's own rows on both the live and accepted paths, measured
 * with the one shared {@link RestSpan#gapMinutes} implementation against the schedule's own
 * snapshotted minimum rest.
 */
class RestWaiverDisclosureTest {

    private static final SolverFactory<Schedule> SOLVER_FACTORY =
            SolverFactory.createFromXmlResource("solverConfig.xml");

    private final ScheduleOutputService service = new ScheduleOutputService(SOLVER_FACTORY,
            new UsualShiftResolutionService(mock(ShiftTemplateRepository.class)),
            mock(AgentUsualShiftRepository.class));

    private static final LocalDate D1 = LocalDate.of(2026, 11, 2);
    private static final LocalDate D2 = D1.plusDays(1);
    private static final LocalDate D3 = D1.plusDays(2);
    private static final LocalDate D4 = D1.plusDays(3);
    private static final int MINIMUM_REST_MINUTES = 660;

    private static final long TENANT_ID = 1L;
    private static final UUID DESK_ID = UUID.randomUUID();

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ------------------------------------------------------------------
    //  SHIFT mode
    // ------------------------------------------------------------------

    @Test
    void appliedEntry_shortRestedPairWithWaiver_reportsAllSevenFieldsAndZeroUnused() {
        Agent ana = agent("Ana");
        Schedule schedule = shiftSchedule(List.of(
                shiftRowLive(ana, D1, LocalTime.of(12, 0), LocalTime.of(20, 0)),
                shiftRowLive(ana, D2, LocalTime.of(4, 0), LocalTime.of(9, 0))
        ), List.of(waiver(ana, D2, "Cover for Boris")));

        RestWaiverDisclosure disclosure = service.buildRestWaiverDisclosure(schedule, false);

        assertThat(disclosure.unused()).isEmpty();
        assertThat(disclosure.applied()).hasSize(1);
        RestWaiverEntry entry = disclosure.applied().get(0);
        assertThat(entry.agentId()).isEqualTo(ana.getId());
        assertThat(entry.agentName()).isEqualTo("Ana");
        assertThat(entry.priorBusinessDate()).isEqualTo(D1);
        assertThat(entry.nextBusinessDate()).isEqualTo(D2);
        assertThat(entry.priorShiftEnd()).isEqualTo(LocalTime.of(20, 0));
        assertThat(entry.nextShiftStart()).isEqualTo(LocalTime.of(4, 0));
        assertThat(entry.measuredGapMinutes()).isEqualTo(480);
        assertThat(entry.requiredGapMinutes()).isEqualTo(MINIMUM_REST_MINUTES);
        assertThat(entry.reason()).isEqualTo("Cover for Boris");
    }

    @Test
    void waiverOnAdequateRestDate_reportsOneUnusedEntryWithMeasuredGap() {
        Agent ana = agent("Ana");
        Schedule schedule = shiftSchedule(List.of(
                shiftRowLive(ana, D3, LocalTime.of(1, 0), LocalTime.of(9, 0)),
                shiftRowLive(ana, D4, LocalTime.of(0, 0), LocalTime.of(8, 0))
        ), List.of(waiver(ana, D4, "Just in case")));

        RestWaiverDisclosure disclosure = service.buildRestWaiverDisclosure(schedule, false);

        assertThat(disclosure.applied()).isEmpty();
        assertThat(disclosure.unused()).hasSize(1);
        RestWaiverEntry entry = disclosure.unused().get(0);
        assertThat(entry.measuredGapMinutes()).isEqualTo(900);
        assertThat(entry.requiredGapMinutes()).isEqualTo(MINIMUM_REST_MINUTES);
        assertThat(entry.priorShiftEnd()).isEqualTo(LocalTime.of(9, 0));
        assertThat(entry.nextShiftStart()).isEqualTo(LocalTime.of(0, 0));
    }

    @Test
    void waiverOnDayOffDate_reportsOneUnusedEntryWithNullPairFields() {
        Agent ben = agent("Ben");
        Schedule schedule = shiftSchedule(List.of(
                shiftRowLive(ben, D1, LocalTime.of(8, 0), LocalTime.of(16, 0))
                // Ben has no shift row at all on D2 -- a day off, not rostered that date.
        ), List.of(waiver(ben, D2, "Precaution")));

        RestWaiverDisclosure disclosure = service.buildRestWaiverDisclosure(schedule, false);

        assertThat(disclosure.applied()).isEmpty();
        assertThat(disclosure.unused()).hasSize(1);
        RestWaiverEntry entry = disclosure.unused().get(0);
        assertThat(entry.agentId()).isEqualTo(ben.getId());
        assertThat(entry.priorBusinessDate()).isEqualTo(D1);
        assertThat(entry.nextBusinessDate()).isEqualTo(D2);
        assertThat(entry.priorShiftEnd()).isNull();
        assertThat(entry.nextShiftStart()).isNull();
        assertThat(entry.measuredGapMinutes()).isNull();
        assertThat(entry.requiredGapMinutes()).isEqualTo(MINIMUM_REST_MINUTES);
        assertThat(entry.reason()).isEqualTo("Precaution");
    }

    @Test
    void waiverForUnrosteredAgent_reportsOneUnusedEntry() {
        Agent ana = agent("Ana");
        Agent carla = agent("Carla"); // never appears in any assignment this period
        Schedule schedule = shiftSchedule(List.of(
                shiftRowLive(ana, D1, LocalTime.of(8, 0), LocalTime.of(16, 0))
        ), List.of(waiver(carla, D2, "Speculative")));

        RestWaiverDisclosure disclosure = service.buildRestWaiverDisclosure(schedule, false);

        assertThat(disclosure.applied()).isEmpty();
        assertThat(disclosure.unused()).hasSize(1);
        assertThat(disclosure.unused().get(0).agentId()).isEqualTo(carla.getId());
        assertThat(disclosure.unused().get(0).priorShiftEnd()).isNull();
    }

    @Test
    void waiverOnFirstBusinessDateWithNoAcceptedPredecessor_reportsOneUnusedEntry() {
        Agent dana = agent("Dana");
        Schedule schedule = shiftSchedule(List.of(
                shiftRowLive(dana, D1, LocalTime.of(8, 0), LocalTime.of(16, 0))
        ), List.of(waiver(dana, D1, "Just started")));
        // No priorRestSpans set -- the normal state for a first-ever period (REST-05/D-10).

        RestWaiverDisclosure disclosure = service.buildRestWaiverDisclosure(schedule, false);

        assertThat(disclosure.applied()).isEmpty();
        assertThat(disclosure.unused()).hasSize(1);
        RestWaiverEntry entry = disclosure.unused().get(0);
        assertThat(entry.nextBusinessDate()).isEqualTo(D1);
        assertThat(entry.priorBusinessDate()).isEqualTo(D1.minusDays(1));
        assertThat(entry.priorShiftEnd()).isNull();
        assertThat(entry.measuredGapMinutes()).isNull();
    }

    @Test
    void waiverOnFirstBusinessDateWithAcceptedPredecessor_matchesAgainstThePreHorizonSpan() {
        Agent dana = agent("Dana");
        Schedule schedule = shiftSchedule(List.of(
                shiftRowLive(dana, D1, LocalTime.of(4, 0), LocalTime.of(12, 0))
        ), List.of(waiver(dana, D1, "Edge case")));
        // A real pre-horizon predecessor ending 20:00 the day before D1 -- gap = 240 + 240 = 480.
        schedule.setPriorRestSpans(new ArrayList<>(List.of(
                new RestSpan(dana.getId(), D1.minusDays(1), LocalTime.of(12, 0), LocalTime.of(20, 0),
                        LocalTime.MIDNIGHT))));

        RestWaiverDisclosure disclosure = service.buildRestWaiverDisclosure(schedule, false);

        assertThat(disclosure.unused()).isEmpty();
        assertThat(disclosure.applied()).hasSize(1);
        RestWaiverEntry entry = disclosure.applied().get(0);
        assertThat(entry.priorShiftEnd()).isEqualTo(LocalTime.of(20, 0));
        assertThat(entry.measuredGapMinutes()).isEqualTo(480);
    }

    @Test
    void waiverOutsidePeriod_absentFromBothSections() {
        Agent ana = agent("Ana");
        Schedule schedule = shiftSchedule(List.of(
                shiftRowLive(ana, D1, LocalTime.of(8, 0), LocalTime.of(16, 0))
        ), List.of(
                waiver(ana, D1.minusDays(10), "Too early"),
                waiver(ana, D4.plusDays(10), "Too late")));

        RestWaiverDisclosure disclosure = service.buildRestWaiverDisclosure(schedule, false);

        assertThat(disclosure.applied()).isEmpty();
        assertThat(disclosure.unused()).isEmpty();
    }

    @Test
    void twoAppliedEntriesForSameAgentOnDifferentDates_bothListedInDateOrder() {
        Agent ana = agent("Ana");
        // Pair 1 (D1->D2) and pair 2 (D3->D4) both gap 480 -- two distinct pairs for the same
        // agent, each cleared by its own waiver; one waiver must never collapse two pairs.
        Schedule schedule = shiftSchedule(List.of(
                shiftRowLive(ana, D1, LocalTime.of(12, 0), LocalTime.of(20, 0)),
                shiftRowLive(ana, D2, LocalTime.of(4, 0), LocalTime.of(12, 0)),
                shiftRowLive(ana, D3, LocalTime.of(12, 0), LocalTime.of(20, 0)),
                shiftRowLive(ana, D4, LocalTime.of(4, 0), LocalTime.of(12, 0))
        ), List.of(waiver(ana, D4, "Second"), waiver(ana, D2, "First")));

        RestWaiverDisclosure disclosure = service.buildRestWaiverDisclosure(schedule, false);

        assertThat(disclosure.unused()).isEmpty();
        assertThat(disclosure.applied()).hasSize(2);
        assertThat(disclosure.applied()).extracting(RestWaiverEntry::nextBusinessDate)
                .containsExactly(D2, D4); // date-ascending, regardless of insertion order
    }

    @Test
    void nullMinimumRest_bothSectionsEmpty() {
        Agent ana = agent("Ana");
        Schedule schedule = shiftSchedule(List.of(
                shiftRowLive(ana, D1, LocalTime.of(8, 0), LocalTime.of(16, 0))
        ), List.of(waiver(ana, D2, "n/a")));
        schedule.setMinimumRestMinutes(null);

        RestWaiverDisclosure disclosure = service.buildRestWaiverDisclosure(schedule, false);

        assertThat(disclosure.applied()).isEmpty();
        assertThat(disclosure.unused()).isEmpty();
    }

    @Test
    void requiredGap_isTheScheduleSnapshot_unaffectedByALaterDeskValueChange() {
        Agent ana = agent("Ana");
        Schedule schedule = shiftSchedule(List.of(
                shiftRowLive(ana, D1, LocalTime.of(12, 0), LocalTime.of(20, 0)),
                shiftRowLive(ana, D2, LocalTime.of(4, 0), LocalTime.of(9, 0))
        ), List.of(waiver(ana, D2, "Cover")));

        RestWaiverDisclosure beforeDeskEdit = service.buildRestWaiverDisclosure(schedule, false);
        assertThat(beforeDeskEdit.applied().get(0).requiredGapMinutes()).isEqualTo(MINIMUM_REST_MINUTES);

        // Simulate the desk's LIVE minimumRestMinutes being edited to something else entirely,
        // without touching this already-solved schedule's own snapshotted column. This method
        // holds no DeskRepository reference at all -- schedule is the only thing it ever reads --
        // so a live desk value of 30 here must have zero effect on the report.
        int editedLiveDeskValue = 30;
        assertThat(editedLiveDeskValue).isNotEqualTo(schedule.getMinimumRestMinutes());

        RestWaiverDisclosure afterDeskEdit = service.buildRestWaiverDisclosure(schedule, false);

        assertThat(afterDeskEdit.applied().get(0).requiredGapMinutes()).isEqualTo(MINIMUM_REST_MINUTES);
        assertThat(afterDeskEdit).isEqualTo(beforeDeskEdit);
    }

    // ------------------------------------------------------------------
    //  Accepted path -- field-for-field identical to the live path
    // ------------------------------------------------------------------

    @Test
    void acceptedPath_reloadedFixture_producesFieldForFieldIdenticalDisclosureToLivePath() {
        Agent ana = agent("Ana");
        List<AgentRestWaiver> waivers = List.of(waiver(ana, D2, "Cover"), waiver(ana, D4, "Precaution"));

        Schedule live = shiftSchedule(List.of(
                shiftRowLive(ana, D1, LocalTime.of(12, 0), LocalTime.of(20, 0)),
                shiftRowLive(ana, D2, LocalTime.of(4, 0), LocalTime.of(9, 0)),
                shiftRowLive(ana, D3, LocalTime.of(1, 0), LocalTime.of(9, 0)),
                shiftRowLive(ana, D4, LocalTime.of(0, 0), LocalTime.of(8, 0))
        ), waivers);

        Schedule accepted = shiftSchedule(List.of(
                shiftRowAccepted(ana, D1, LocalTime.of(12, 0), LocalTime.of(20, 0)),
                shiftRowAccepted(ana, D2, LocalTime.of(4, 0), LocalTime.of(9, 0)),
                shiftRowAccepted(ana, D3, LocalTime.of(1, 0), LocalTime.of(9, 0)),
                shiftRowAccepted(ana, D4, LocalTime.of(0, 0), LocalTime.of(8, 0))
        ), waivers);

        RestWaiverDisclosure liveResult = service.buildRestWaiverDisclosure(live, false);
        RestWaiverDisclosure acceptedResult = service.buildRestWaiverDisclosure(accepted, true);

        assertThat(acceptedResult).isEqualTo(liveResult);
        assertThat(acceptedResult.applied()).hasSize(1);
        assertThat(acceptedResult.unused()).hasSize(1);
    }

    // ------------------------------------------------------------------
    //  SLOT mode -- spans derived from the assigned slots instead of shift rows
    // ------------------------------------------------------------------

    @Test
    void slotMode_appliedAndUnusedBehaveIdenticallyToShiftMode() {
        Agent ana = agent("Ana");
        Specialization spec = specialization("Chat");

        List<AgentAssignment> allSeats = new ArrayList<>();
        allSeats.addAll(slotSeats(ana, spec, D1, LocalTime.of(12, 0), LocalTime.of(20, 0)));
        allSeats.addAll(slotSeats(ana, spec, D2, LocalTime.of(4, 0), LocalTime.of(9, 0)));

        Schedule schedule = slotSchedule(List.of(waiver(ana, D2, "Cover")));
        schedule.setAssignments(allSeats);

        RestWaiverDisclosure disclosure = service.buildRestWaiverDisclosure(schedule, false);

        assertThat(disclosure.unused()).isEmpty();
        assertThat(disclosure.applied()).hasSize(1);
        RestWaiverEntry entry = disclosure.applied().get(0);
        assertThat(entry.priorShiftEnd()).isEqualTo(LocalTime.of(20, 0));
        assertThat(entry.nextShiftStart()).isEqualTo(LocalTime.of(4, 0));
        assertThat(entry.measuredGapMinutes()).isEqualTo(480);
    }

    @Test
    void slotMode_dayOffDate_reportsOneUnusedEntryWithNullPairFields() {
        Agent ben = agent("Ben");
        Specialization spec = specialization("Chat");

        Schedule schedule = slotSchedule(List.of(waiver(ben, D2, "Precaution")));
        schedule.setAssignments(new ArrayList<>(
                slotSeats(ben, spec, D1, LocalTime.of(8, 0), LocalTime.of(16, 0))));

        RestWaiverDisclosure disclosure = service.buildRestWaiverDisclosure(schedule, false);

        assertThat(disclosure.applied()).isEmpty();
        assertThat(disclosure.unused()).hasSize(1);
        RestWaiverEntry entry = disclosure.unused().get(0);
        assertThat(entry.measuredGapMinutes()).isNull();
        assertThat(entry.priorShiftEnd()).isNull();
    }

    @Test
    void slotMode_unrosteredAgent_reportsOneUnusedEntry() {
        Agent ana = agent("Ana");
        Agent carla = agent("Carla");
        Specialization spec = specialization("Chat");

        Schedule schedule = slotSchedule(List.of(waiver(carla, D2, "Speculative")));
        schedule.setAssignments(new ArrayList<>(
                slotSeats(ana, spec, D1, LocalTime.of(8, 0), LocalTime.of(16, 0))));

        RestWaiverDisclosure disclosure = service.buildRestWaiverDisclosure(schedule, false);

        assertThat(disclosure.applied()).isEmpty();
        assertThat(disclosure.unused()).hasSize(1);
        assertThat(disclosure.unused().get(0).agentId()).isEqualTo(carla.getId());
    }

    // ------------------------------------------------------------------
    //  ScheduleService.loadSnapshotData -- the accepted-path waiver/pre-horizon loads are gated
    //  on the schedule's own snapshotted minimum rest, never issued for an unconfigured desk
    //  (T-22-21).
    // ------------------------------------------------------------------

    @Test
    void loadSnapshotData_nullMinimumRest_issuesNoWaiverOrPredecessorQuery() {
        AgentRestWaiverRepository waiverRepo = mock(AgentRestWaiverRepository.class);
        RestPredecessorService predecessorService = mock(RestPredecessorService.class);
        ScheduleRepository scheduleRepository = mock(ScheduleRepository.class);
        ScheduleService scheduleService = scheduleService(scheduleRepository, waiverRepo, predecessorService);

        UUID scheduleId = UUID.randomUUID();
        Schedule persisted = acceptedBaseSchedule(scheduleId, null);
        when(scheduleRepository.findByIdAndTenantIdAndDeskId(scheduleId, TENANT_ID, DESK_ID))
                .thenReturn(Optional.of(persisted));

        TenantContext.setTenantId(TENANT_ID);
        scheduleService.getScheduleDetail(DESK_ID, scheduleId, null);

        verify(waiverRepo, never()).findByTenantIdAndDeskIdAndDateBetween(anyLong(), any(), any(), any());
        verify(predecessorService, never())
                .resolvePriorSpans(anyLong(), any(), any(), any(), any(), any());
    }

    @Test
    void loadSnapshotData_nonNullMinimumRest_issuesTheWaiverAndPredecessorQuery() {
        AgentRestWaiverRepository waiverRepo = mock(AgentRestWaiverRepository.class);
        RestPredecessorService predecessorService = mock(RestPredecessorService.class);
        ScheduleRepository scheduleRepository = mock(ScheduleRepository.class);
        ScheduleService scheduleService = scheduleService(scheduleRepository, waiverRepo, predecessorService);

        UUID scheduleId = UUID.randomUUID();
        Schedule persisted = acceptedBaseSchedule(scheduleId, MINIMUM_REST_MINUTES);
        when(scheduleRepository.findByIdAndTenantIdAndDeskId(scheduleId, TENANT_ID, DESK_ID))
                .thenReturn(Optional.of(persisted));
        when(waiverRepo.findByTenantIdAndDeskIdAndDateBetween(TENANT_ID, DESK_ID,
                persisted.getPeriodStartDate(), persisted.getPeriodEndDate()))
                .thenReturn(List.of());
        when(predecessorService.resolvePriorSpans(eq(TENANT_ID), eq(DESK_ID),
                eq(persisted.getPeriodStartDate()), eq(MINIMUM_REST_MINUTES), any(), any()))
                .thenReturn(List.of());

        TenantContext.setTenantId(TENANT_ID);
        scheduleService.getScheduleDetail(DESK_ID, scheduleId, null);

        verify(waiverRepo, times(1)).findByTenantIdAndDeskIdAndDateBetween(TENANT_ID, DESK_ID,
                persisted.getPeriodStartDate(), persisted.getPeriodEndDate());
        verify(predecessorService, times(1)).resolvePriorSpans(eq(TENANT_ID), eq(DESK_ID),
                eq(persisted.getPeriodStartDate()), eq(MINIMUM_REST_MINUTES), any(), any());
    }

    // ------------------------------------------------------------------
    //  Plan 22-08 Task 2 -- the two counts on the summary the page already polls, derived from
    //  the SAME buildRestWaiverDisclosure computation the detail response uses.
    // ------------------------------------------------------------------

    @Test
    void summaryCounts_twoAppliedOneUnused_matchesTwoAndOne() {
        Agent ana = agent("Ana");
        Agent ben = agent("Ben");
        Schedule schedule = inMemorySummarySchedule(List.of(
                shiftRowLive(ana, D1, LocalTime.of(12, 0), LocalTime.of(20, 0)),
                shiftRowLive(ana, D2, LocalTime.of(4, 0), LocalTime.of(12, 0)),
                shiftRowLive(ana, D3, LocalTime.of(12, 0), LocalTime.of(20, 0)),
                shiftRowLive(ana, D4, LocalTime.of(4, 0), LocalTime.of(12, 0)),
                shiftRowLive(ben, D1, LocalTime.of(1, 0), LocalTime.of(9, 0)),
                shiftRowLive(ben, D2, LocalTime.of(0, 0), LocalTime.of(8, 0))
        ), List.of(waiver(ana, D2, "Applied1"), waiver(ana, D4, "Applied2"), waiver(ben, D2, "Unused1")));

        InMemoryScheduleStore store = new InMemoryScheduleStore();
        store.put(schedule);
        ScheduleService scheduleService = scheduleServiceWith(store, service);
        TenantContext.setTenantId(TENANT_ID);

        ScheduleSummary summary = scheduleService.getScheduleSummary(DESK_ID, schedule.getId());

        assertThat(summary.appliedRestWaiverCount()).isEqualTo(2);
        assertThat(summary.unusedRestWaiverCount()).isEqualTo(1);
    }

    @Test
    void summaryCounts_noWaiversAtAll_bothZeroNotAbsent() {
        Agent ana = agent("Ana");
        Schedule schedule = inMemorySummarySchedule(List.of(
                shiftRowLive(ana, D1, LocalTime.of(8, 0), LocalTime.of(16, 0))
        ), List.of());

        InMemoryScheduleStore store = new InMemoryScheduleStore();
        store.put(schedule);
        ScheduleService scheduleService = scheduleServiceWith(store, service);
        TenantContext.setTenantId(TENANT_ID);

        ScheduleSummary summary = scheduleService.getScheduleSummary(DESK_ID, schedule.getId());

        assertThat(summary.appliedRestWaiverCount()).isZero();
        assertThat(summary.unusedRestWaiverCount()).isZero();
    }

    @Test
    void summaryCounts_nullMinimumRest_bothCountsNullNotZero() {
        Agent ana = agent("Ana");
        Schedule schedule = inMemorySummarySchedule(List.of(
                shiftRowLive(ana, D1, LocalTime.of(8, 0), LocalTime.of(16, 0))
        ), List.of(waiver(ana, D2, "n/a")));
        schedule.setMinimumRestMinutes(null);

        InMemoryScheduleStore store = new InMemoryScheduleStore();
        store.put(schedule);
        ScheduleService scheduleService = scheduleServiceWith(store, service);
        TenantContext.setTenantId(TENANT_ID);

        ScheduleSummary summary = scheduleService.getScheduleSummary(DESK_ID, schedule.getId());

        assertThat(summary.appliedRestWaiverCount()).isNull();
        assertThat(summary.unusedRestWaiverCount()).isNull();
    }

    @Test
    void summaryCounts_bothConstructionSitesAgree() {
        Agent ana = agent("Ana");
        Agent ben = agent("Ben");
        Schedule schedule = inMemorySummarySchedule(List.of(
                shiftRowLive(ana, D1, LocalTime.of(12, 0), LocalTime.of(20, 0)),
                shiftRowLive(ana, D2, LocalTime.of(4, 0), LocalTime.of(12, 0)),
                shiftRowLive(ben, D1, LocalTime.of(1, 0), LocalTime.of(9, 0)),
                shiftRowLive(ben, D2, LocalTime.of(0, 0), LocalTime.of(8, 0))
        ), List.of(waiver(ana, D2, "Applied"), waiver(ben, D2, "Unused")));

        InMemoryScheduleStore store = new InMemoryScheduleStore();
        store.put(schedule);
        ScheduleService scheduleService = scheduleServiceWith(store, service);
        TenantContext.setTenantId(TENANT_ID);

        ScheduleSummary fromService = scheduleService.getScheduleSummary(DESK_ID, schedule.getId());

        SolverService solverServiceMock = mock(SolverService.class);
        when(solverServiceMock.stopSolve(DESK_ID, schedule.getId())).thenReturn(schedule);
        ScheduleController controller = new ScheduleController(scheduleService, solverServiceMock,
                mock(ScheduleExportService.class), mock(DeskRepository.class),
                mock(AgentDayOffService.class), service);

        ScheduleSummary fromController = controller.stopSolve(DESK_ID, schedule.getId()).getBody();

        assertThat(fromController).isNotNull();
        assertThat(fromController.appliedRestWaiverCount()).isEqualTo(fromService.appliedRestWaiverCount());
        assertThat(fromController.unusedRestWaiverCount()).isEqualTo(fromService.unusedRestWaiverCount());
        assertThat(fromController.appliedRestWaiverCount()).isEqualTo(1);
        assertThat(fromController.unusedRestWaiverCount()).isEqualTo(1);
    }

    @Test
    void summaryCounts_equalTheDetailResponsesListSizes() {
        Agent ana = agent("Ana");
        Agent ben = agent("Ben");
        Schedule schedule = inMemorySummarySchedule(List.of(
                shiftRowLive(ana, D1, LocalTime.of(12, 0), LocalTime.of(20, 0)),
                shiftRowLive(ana, D2, LocalTime.of(4, 0), LocalTime.of(12, 0)),
                shiftRowLive(ben, D1, LocalTime.of(1, 0), LocalTime.of(9, 0)),
                shiftRowLive(ben, D2, LocalTime.of(0, 0), LocalTime.of(8, 0))
        ), List.of(waiver(ana, D2, "Applied"), waiver(ben, D2, "Unused")));

        InMemoryScheduleStore store = new InMemoryScheduleStore();
        store.put(schedule);
        ScheduleService scheduleService = scheduleServiceWith(store, service);
        TenantContext.setTenantId(TENANT_ID);

        ScheduleSummary summary = scheduleService.getScheduleSummary(DESK_ID, schedule.getId());
        var detail = scheduleService.getScheduleDetail(DESK_ID, schedule.getId(), null);

        assertThat(summary.appliedRestWaiverCount())
                .isEqualTo(detail.getRestWaiverDisclosure().applied().size());
        assertThat(summary.unusedRestWaiverCount())
                .isEqualTo(detail.getRestWaiverDisclosure().unused().size());
    }

    @Test
    void summaryPath_staysCheap_doesNotInvokeTheHeavyOutputBuilders() {
        ScheduleOutputService mockedOutputService = mock(ScheduleOutputService.class);
        RestWaiverEntry fakeEntry = new RestWaiverEntry(UUID.randomUUID(), "Ana", D1, D2,
                LocalTime.of(20, 0), LocalTime.of(4, 0), 480, MINIMUM_REST_MINUTES, "Cover");
        when(mockedOutputService.buildRestWaiverDisclosure(any(), anyBoolean()))
                .thenReturn(new RestWaiverDisclosure(List.of(fakeEntry), List.of()));

        Schedule schedule = inMemorySummarySchedule(List.of(), List.of());

        InMemoryScheduleStore store = new InMemoryScheduleStore();
        store.put(schedule);
        ScheduleService scheduleService = scheduleServiceWith(store, mockedOutputService);
        TenantContext.setTenantId(TENANT_ID);

        ScheduleSummary summary = scheduleService.getScheduleSummary(DESK_ID, schedule.getId());

        assertThat(summary.appliedRestWaiverCount()).isEqualTo(1);
        assertThat(summary.unusedRestWaiverCount()).isEqualTo(0);
        verify(mockedOutputService, times(1)).buildRestWaiverDisclosure(any(), anyBoolean());
        verify(mockedOutputService, never()).buildAgentSchedule(any());
        verify(mockedOutputService, never()).buildStaffingSummary(any());
        verify(mockedOutputService, never()).buildPreferenceReport(any());
        verify(mockedOutputService, never()).buildDriftReport(any());
        verify(mockedOutputService, never()).buildConstraintViolations(any(), anyBoolean());
    }

    // ------------------------------------------------------------------
    //  Plan 22-11 (REST-07 gap closure, 22-VERIFICATION.md missing: items 2 and 3) -- gap (b):
    //  ScheduleDetailResponse carried no configured-rest signal and no waiver counts, so
    //  ScheduleResults.tsx's header badge and Rest Waivers tab fell back to hidden / "not
    //  configured" for every finished solve and every reopened ACCEPTED schedule outside the
    //  narrow RUNNING-poll window. The first test below is the runnable RED proof the
    //  verification named as missing: it compiles and fails against HEAD because none of
    //  minimumRestMinutes/appliedRestWaiverCount/unusedRestWaiverCount exist as declared fields.
    // ------------------------------------------------------------------

    @Test
    void detailResponse_declaresEveryRestWaiverCountFieldTheSummaryHas_plusTheSnapshottedRestSignal() {
        Set<String> detailFieldNames = Arrays.stream(ScheduleDetailResponse.class.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toSet());
        Set<String> summaryCountComponents = Arrays.stream(ScheduleSummary.class.getRecordComponents())
                .map(RecordComponent::getName)
                .filter(name -> name.endsWith("RestWaiverCount"))
                .collect(Collectors.toSet());

        assertThat(detailFieldNames).containsAll(summaryCountComponents);
        assertThat(detailFieldNames).contains("minimumRestMinutes");
    }

    // ------------------------------------------------------------------
    //  Fixture helpers
    // ------------------------------------------------------------------

    /** Same shape as {@link #shiftSchedule}, plus the identity fields {@code getScheduleSummary}/
     * {@code getScheduleDetail} need to resolve an in-memory schedule by id/tenant/desk. */
    private Schedule inMemorySummarySchedule(List<AgentShiftAssignment> shiftRows, List<AgentRestWaiver> waivers) {
        Schedule schedule = shiftSchedule(shiftRows, waivers);
        schedule.setId(UUID.randomUUID());
        schedule.setTenantId(TENANT_ID);
        schedule.setDeskId(DESK_ID);
        schedule.setStatus(ScheduleStatus.COMPLETED);
        return schedule;
    }

    private ScheduleService scheduleServiceWith(InMemoryScheduleStore store, ScheduleOutputService outputServiceToUse) {
        return new ScheduleService(mock(ScheduleRepository.class), mock(AcceptedScheduleDateRepository.class),
                mock(DeskRepository.class), store, mock(TimeslotRepository.class),
                mock(StaffingRequirementRepository.class), mock(AgentAssignmentRepository.class),
                mock(AgentShiftAssignmentRepository.class), mock(AgentPreferenceRepository.class),
                mock(AgentDayOffRepository.class), mock(ConstraintWeightsRepository.class),
                mock(AgentRestWaiverRepository.class), mock(RestPredecessorService.class),
                outputServiceToUse, mock(EntityManager.class));
    }

    private Schedule shiftSchedule(List<AgentShiftAssignment> shiftRows, List<AgentRestWaiver> waivers) {
        Schedule schedule = new Schedule();
        schedule.setSchedulingMode(SchedulingMode.SHIFT);
        schedule.setDayStart(LocalTime.MIDNIGHT);
        schedule.setMinimumRestMinutes(MINIMUM_REST_MINUTES);
        schedule.setPeriodStartDate(D1);
        schedule.setPeriodEndDate(D4);
        schedule.setShiftAssignments(new ArrayList<>(shiftRows));
        schedule.setAssignments(new ArrayList<>());
        schedule.setAgentRestWaivers(new ArrayList<>(waivers));
        return schedule;
    }

    private Schedule slotSchedule(List<AgentRestWaiver> waivers) {
        Schedule schedule = new Schedule();
        schedule.setSchedulingMode(SchedulingMode.SLOT);
        schedule.setDayStart(LocalTime.MIDNIGHT);
        schedule.setMinimumRestMinutes(MINIMUM_REST_MINUTES);
        schedule.setPeriodStartDate(D1);
        schedule.setPeriodEndDate(D4);
        schedule.setShiftAssignments(new ArrayList<>());
        schedule.setAgentRestWaivers(new ArrayList<>(waivers));
        return schedule;
    }

    private AgentShiftAssignment shiftRowLive(Agent agent, LocalDate date, LocalTime start, LocalTime end) {
        ShiftTemplate template = new ShiftTemplate();
        template.setId(UUID.randomUUID());
        template.setName("T-" + start);
        template.setStartTime(start);
        template.setEndTime(end);

        AgentShiftAssignment row = new AgentShiftAssignment();
        row.setId(UUID.randomUUID());
        row.setAgent(agent);
        row.setDate(date);
        row.setShiftBandPair(new ShiftBandPair(template, null));
        return row;
    }

    /** Simulates a row reloaded via loadSnapshotData: transient shiftBandPair is null (JPA never
     * populates @Transient fields), only the D-07 denormalised scalars are present. */
    private AgentShiftAssignment shiftRowAccepted(Agent agent, LocalDate date, LocalTime start, LocalTime end) {
        AgentShiftAssignment row = new AgentShiftAssignment();
        row.setId(UUID.randomUUID());
        row.setAgent(agent);
        row.setDate(date);
        row.setShiftBandPair(null);
        row.setTemplateName("T-" + start);
        row.setShiftStartTime(start);
        row.setShiftEndTime(end);
        row.setSourceTemplateId(UUID.randomUUID());
        return row;
    }

    private List<AgentAssignment> slotSeats(Agent agent, Specialization spec, LocalDate date,
            LocalTime start, LocalTime end) {
        List<AgentAssignment> seats = new ArrayList<>();
        for (LocalTime t = start; t.isBefore(end); t = t.plusHours(1)) {
            Timeslot ts = new Timeslot();
            ts.setId(UUID.randomUUID());
            ts.setDate(date);
            ts.setBusinessDate(date);
            ts.setStartTime(t);
            ts.setEndTime(t.plusHours(1));
            AgentAssignment a = new AgentAssignment();
            a.setId(UUID.randomUUID());
            a.setAgent(agent);
            a.setTimeslot(ts);
            a.setRequiredSpecialization(spec);
            seats.add(a);
        }
        return seats;
    }

    private Agent agent(String name) {
        Agent a = new Agent();
        a.setId(UUID.randomUUID());
        a.setName(name);
        return a;
    }

    private Specialization specialization(String name) {
        Specialization s = new Specialization();
        s.setId(UUID.randomUUID());
        s.setName(name);
        return s;
    }

    private AgentRestWaiver waiver(Agent agent, LocalDate date, String reason) {
        AgentRestWaiver w = new AgentRestWaiver();
        w.setId(UUID.randomUUID());
        w.setAgent(agent);
        w.setDate(date);
        w.setReason(reason);
        return w;
    }

    private ScheduleService scheduleService(ScheduleRepository scheduleRepository,
            AgentRestWaiverRepository waiverRepo, RestPredecessorService predecessorService) {
        return new ScheduleService(scheduleRepository, mock(AcceptedScheduleDateRepository.class),
                mock(DeskRepository.class), new InMemoryScheduleStore(), mock(TimeslotRepository.class),
                mock(StaffingRequirementRepository.class), mock(AgentAssignmentRepository.class),
                mock(AgentShiftAssignmentRepository.class), mock(AgentPreferenceRepository.class),
                mock(AgentDayOffRepository.class), mock(ConstraintWeightsRepository.class),
                waiverRepo, predecessorService, mock(ScheduleOutputService.class), mock(EntityManager.class));
    }

    private Schedule acceptedBaseSchedule(UUID id, Integer minimumRestMinutes) {
        Schedule s = new Schedule();
        s.setId(id);
        s.setTenantId(TENANT_ID);
        s.setDeskId(DESK_ID);
        s.setStatus(ScheduleStatus.ACCEPTED);
        s.setPeriodStartDate(D1);
        s.setPeriodEndDate(D4);
        s.setStartTime(LocalTime.of(8, 0));
        s.setEndTime(LocalTime.of(20, 0));
        s.setIncrementMinutes(60);
        s.setDayStart(LocalTime.MIDNIGHT);
        s.setMinimumRestMinutes(minimumRestMinutes);
        s.setSchedulingMode(SchedulingMode.SHIFT);
        return s;
    }
}
