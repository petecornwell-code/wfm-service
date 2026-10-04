package com.wfm.service;

import com.wfm.model.AcceptedScheduleDate;
import com.wfm.model.AcceptedScheduleDateStatus;
import com.wfm.model.Agent;
import com.wfm.model.AgentAssignment;
import com.wfm.model.AgentShiftAssignment;
import com.wfm.model.RestSpan;
import com.wfm.model.Schedule;
import com.wfm.model.SchedulingMode;
import com.wfm.model.Timeslot;
import com.wfm.repository.AcceptedScheduleDateRepository;
import com.wfm.repository.AgentAssignmentRepository;
import com.wfm.repository.AgentShiftAssignmentRepository;
import com.wfm.repository.ScheduleRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Phase 22 plan 22-06, Task 1 (REST-05, D-10) — {@link RestPredecessorService#resolvePriorSpans}'s
 * query-count discipline, mode-from-predecessor-not-desk resolution, and every degradation path.
 *
 * <p>Plain {@code @ExtendWith(MockitoExtension.class)} unit test with mocked repositories,
 * mirroring {@code RestWaiverServiceTest}'s wiring idiom (D-07's precedent in this package) — no
 * {@code @DataJpaTest}, no Spring context. The two load-bearing assertions in this class are the
 * query-count proof against a 300-agent fixture (an N+1 shape passes every behavioural assertion
 * and only a count catches it) and the predecessor-mode case, which is the one a future reader is
 * most likely to "fix" into reading the desk's current mode.
 */
@ExtendWith(MockitoExtension.class)
class RestPredecessorServiceTest {

    private static final long TENANT_ID = 1L;
    private static final UUID DESK_ID = UUID.randomUUID();
    private static final LocalDate PERIOD_START = LocalDate.of(2026, 10, 12);
    private static final LocalDate LOOKBACK_DATE = PERIOD_START.minusDays(1);
    private static final LocalTime DAY_START = LocalTime.MIDNIGHT;

    @Mock
    private AcceptedScheduleDateRepository acceptedScheduleDateRepository;

    @Mock
    private ScheduleRepository scheduleRepository;

    @Mock
    private AgentShiftAssignmentRepository agentShiftAssignmentRepository;

    @Mock
    private AgentAssignmentRepository agentAssignmentRepository;

    private RestPredecessorService service;

    @BeforeEach
    void setUp() {
        service = new RestPredecessorService(acceptedScheduleDateRepository, scheduleRepository,
                agentShiftAssignmentRepository, agentAssignmentRepository);
    }

    private static Agent agent() {
        Agent a = new Agent();
        a.setId(UUID.randomUUID());
        return a;
    }

    private static AcceptedScheduleDate acceptedDate(UUID scheduleId) {
        return new AcceptedScheduleDate(scheduleId, TENANT_ID, DESK_ID, LOOKBACK_DATE);
    }

    private static Schedule predecessorSchedule(SchedulingMode mode, LocalTime dayStart) {
        Schedule schedule = new Schedule();
        schedule.setSchedulingMode(mode);
        schedule.setDayStart(dayStart);
        return schedule;
    }

    private static AgentShiftAssignment shiftRow(Agent agent, LocalTime start, LocalTime end) {
        AgentShiftAssignment sa = new AgentShiftAssignment();
        sa.setAgent(agent);
        sa.setDate(LOOKBACK_DATE);
        sa.setShiftStartTime(start);
        sa.setShiftEndTime(end);
        return sa;
    }

    private static AgentAssignment slotRow(Agent agent, LocalTime start, LocalTime end) {
        Timeslot ts = new Timeslot();
        ts.setDate(LOOKBACK_DATE);
        ts.setBusinessDate(LOOKBACK_DATE);
        ts.setStartTime(start);
        ts.setEndTime(end);
        AgentAssignment aa = new AgentAssignment();
        aa.setAgent(agent);
        aa.setTimeslot(ts);
        return aa;
    }

    @Test
    @DisplayName("a null minimumRestMinutes returns an empty list and issues zero queries")
    void nullMinimumRestMinutes_returnsEmptyList_zeroQueries() {
        List<String> warnings = new ArrayList<>();

        List<RestSpan> result = service.resolvePriorSpans(
                TENANT_ID, DESK_ID, PERIOD_START, null, DAY_START, warnings);

        assertThat(result).isEmpty();
        assertThat(warnings).isEmpty();
        verifyNoMoreInteractions(acceptedScheduleDateRepository, scheduleRepository,
                agentShiftAssignmentRepository, agentAssignmentRepository);
    }

    @Test
    @DisplayName("no ACCEPTED predecessor returns an empty list, no warning, and issues exactly one query")
    void noAcceptedPredecessor_returnsEmptyList_oneQuery_noWarning() {
        List<String> warnings = new ArrayList<>();
        when(acceptedScheduleDateRepository.findByTenantIdAndDeskIdAndDateInAndStatus(
                eq(TENANT_ID), eq(DESK_ID), any(), eq(AcceptedScheduleDateStatus.ACCEPTED)))
                .thenReturn(List.of());

        List<RestSpan> result = service.resolvePriorSpans(
                TENANT_ID, DESK_ID, PERIOD_START, 660, DAY_START, warnings);

        assertThat(result).isEmpty();
        // D-10: unconstrained is the explicit decision for a first-ever period -- no warning.
        assertThat(warnings).isEmpty();
        verify(acceptedScheduleDateRepository, times(1)).findByTenantIdAndDeskIdAndDateInAndStatus(
                eq(TENANT_ID), eq(DESK_ID), any(), eq(AcceptedScheduleDateStatus.ACCEPTED));
        verifyNoMoreInteractions(acceptedScheduleDateRepository, scheduleRepository,
                agentShiftAssignmentRepository, agentAssignmentRepository);
    }

    @Test
    @DisplayName("a SHIFT-mode predecessor returns one RestSpan per accepted shift row, from the denormalised instants and the predecessor's own anchor")
    void shiftModePredecessor_returnsOneSpanPerRow() {
        UUID scheduleId = UUID.randomUUID();
        Agent a1 = agent();
        Agent a2 = agent();
        List<String> warnings = new ArrayList<>();

        when(acceptedScheduleDateRepository.findByTenantIdAndDeskIdAndDateInAndStatus(
                eq(TENANT_ID), eq(DESK_ID), any(), eq(AcceptedScheduleDateStatus.ACCEPTED)))
                .thenReturn(List.of(acceptedDate(scheduleId)));
        when(scheduleRepository.findByIdAndTenantIdAndDeskId(scheduleId, TENANT_ID, DESK_ID))
                .thenReturn(Optional.of(predecessorSchedule(SchedulingMode.SHIFT, DAY_START)));
        when(agentShiftAssignmentRepository.findWithRelationsByTenantIdAndDeskIdAndScheduleIdAndDate(
                TENANT_ID, DESK_ID, scheduleId, LOOKBACK_DATE))
                .thenReturn(List.of(
                        shiftRow(a1, LocalTime.of(14, 0), LocalTime.of(22, 0)),
                        shiftRow(a2, LocalTime.of(6, 0), LocalTime.of(14, 0))));

        List<RestSpan> result = service.resolvePriorSpans(
                TENANT_ID, DESK_ID, PERIOD_START, 660, DAY_START, warnings);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(RestSpan::agentId).containsExactlyInAnyOrder(a1.getId(), a2.getId());
        assertThat(result).allMatch(span -> span.dayStart().equals(DAY_START));
        assertThat(result).allMatch(span -> span.businessDate().equals(LOOKBACK_DATE));
        assertThat(warnings).isEmpty();
        verify(agentAssignmentRepository, never())
                .findWithRelationsByTenantIdAndDeskIdAndScheduleIdAndBusinessDate(
                        anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("a SHIFT-mode overnight accepted row resolves to a span carrying the wrapped end, and that span measures a true 60-minute gap against a 07:00 successor -- before the fix this measured 1500")
    void shiftModeOvernightRow_spanCarriesTheWrappedEnd_gapMeasuredCorrectly() {
        UUID scheduleId = UUID.randomUUID();
        Agent a = agent();
        List<String> warnings = new ArrayList<>();

        when(acceptedScheduleDateRepository.findByTenantIdAndDeskIdAndDateInAndStatus(
                eq(TENANT_ID), eq(DESK_ID), any(), eq(AcceptedScheduleDateStatus.ACCEPTED)))
                .thenReturn(List.of(acceptedDate(scheduleId)));
        when(scheduleRepository.findByIdAndTenantIdAndDeskId(scheduleId, TENANT_ID, DESK_ID))
                .thenReturn(Optional.of(predecessorSchedule(SchedulingMode.SHIFT, DAY_START)));
        when(agentShiftAssignmentRepository.findWithRelationsByTenantIdAndDeskIdAndScheduleIdAndDate(
                TENANT_ID, DESK_ID, scheduleId, LOOKBACK_DATE))
                .thenReturn(List.of(shiftRow(a, LocalTime.of(22, 0), LocalTime.of(6, 0))));

        List<RestSpan> result = service.resolvePriorSpans(
                TENANT_ID, DESK_ID, PERIOD_START, 660, DAY_START, warnings);

        assertThat(result).hasSize(1);
        RestSpan span = result.get(0);
        assertThat(span.startTime()).isEqualTo(LocalTime.of(22, 0));
        assertThat(span.endTime()).isEqualTo(LocalTime.of(6, 0));
        assertThat(span.businessDate()).isEqualTo(LOOKBACK_DATE);
        assertThat(span.dayStart()).isEqualTo(DAY_START);
        assertThat(warnings).isEmpty();

        // Proves the resolved span is usable, not merely well-shaped: before the correction this
        // same assertion would have produced 1500, the overstated gap REST-05 names.
        assertThat(RestSpan.gapMinutes(span,
                new RestSpan(a.getId(), PERIOD_START, LocalTime.of(7, 0), LocalTime.of(15, 0),
                        LocalTime.MIDNIGHT)))
                .isEqualTo(60);

        verify(agentAssignmentRepository, never())
                .findWithRelationsByTenantIdAndDeskIdAndScheduleIdAndBusinessDate(
                        anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("a SLOT-mode predecessor groups that date's accepted rows by agent, one RestSpan per agent spanning first-slot-start to last-slot-end")
    void slotModePredecessor_groupsByAgent_oneSpanPerAgent() {
        UUID scheduleId = UUID.randomUUID();
        Agent a1 = agent();
        List<String> warnings = new ArrayList<>();

        when(acceptedScheduleDateRepository.findByTenantIdAndDeskIdAndDateInAndStatus(
                eq(TENANT_ID), eq(DESK_ID), any(), eq(AcceptedScheduleDateStatus.ACCEPTED)))
                .thenReturn(List.of(acceptedDate(scheduleId)));
        when(scheduleRepository.findByIdAndTenantIdAndDeskId(scheduleId, TENANT_ID, DESK_ID))
                .thenReturn(Optional.of(predecessorSchedule(SchedulingMode.SLOT, DAY_START)));
        when(agentAssignmentRepository.findWithRelationsByTenantIdAndDeskIdAndScheduleIdAndBusinessDate(
                TENANT_ID, DESK_ID, scheduleId, LOOKBACK_DATE))
                .thenReturn(List.of(
                        slotRow(a1, LocalTime.of(9, 0), LocalTime.of(10, 0)),
                        slotRow(a1, LocalTime.of(10, 0), LocalTime.of(11, 0)),
                        slotRow(a1, LocalTime.of(16, 0), LocalTime.of(17, 0))));

        List<RestSpan> result = service.resolvePriorSpans(
                TENANT_ID, DESK_ID, PERIOD_START, 660, DAY_START, warnings);

        assertThat(result).hasSize(1);
        RestSpan span = result.get(0);
        assertThat(span.agentId()).isEqualTo(a1.getId());
        assertThat(span.startTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(span.endTime()).isEqualTo(LocalTime.of(17, 0));
        verify(agentShiftAssignmentRepository, never())
                .findWithRelationsByTenantIdAndDeskIdAndScheduleIdAndDate(anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("a SLOT-mode span crossing calendar midnight but NOT the desk's 21:00 anchor is numerically unchanged by this phase -- its wrapped end offset collapses onto the single-argument anchored end offset (PF-02)")
    void slotModeSpanCrossingMidnightButNotTheAnchor_unchangedByTheFix() {
        // PF-02: RestSpan.ofSlots orders slots by anchored start minute and takes the first start
        // and last end, so it cannot emit a span wrapping past the anchor unless an individual
        // timeslot itself crosses the anchor -- which grid generation from the day start does not
        // produce. At a 21:00 anchor this fixture's span starts offset 120 and ends offset 420,
        // so the interval does NOT cross the anchor, and anchoredWrappedEndMinute collapses onto
        // the single-argument anchored end offset at 420 -- the gap of 1740 is byte-identical
        // before and after this phase's change.
        UUID scheduleId = UUID.randomUUID();
        Agent a = agent();
        LocalTime anchor = LocalTime.of(21, 0);
        List<String> warnings = new ArrayList<>();

        when(acceptedScheduleDateRepository.findByTenantIdAndDeskIdAndDateInAndStatus(
                eq(TENANT_ID), eq(DESK_ID), any(), eq(AcceptedScheduleDateStatus.ACCEPTED)))
                .thenReturn(List.of(acceptedDate(scheduleId)));
        when(scheduleRepository.findByIdAndTenantIdAndDeskId(scheduleId, TENANT_ID, DESK_ID))
                .thenReturn(Optional.of(predecessorSchedule(SchedulingMode.SLOT, anchor)));
        when(agentAssignmentRepository.findWithRelationsByTenantIdAndDeskIdAndScheduleIdAndBusinessDate(
                TENANT_ID, DESK_ID, scheduleId, LOOKBACK_DATE))
                .thenReturn(List.of(
                        slotRow(a, LocalTime.of(23, 0), LocalTime.MIDNIGHT),
                        slotRow(a, LocalTime.of(3, 0), LocalTime.of(4, 0))));

        List<RestSpan> result = service.resolvePriorSpans(
                TENANT_ID, DESK_ID, PERIOD_START, 660, anchor, warnings);

        assertThat(result).hasSize(1);
        RestSpan span = result.get(0);
        assertThat(span.startTime()).isEqualTo(LocalTime.of(23, 0));
        assertThat(span.endTime()).isEqualTo(LocalTime.of(4, 0));
        assertThat(span.dayStart()).isEqualTo(anchor);
        assertThat(warnings).isEmpty();

        assertThat(RestSpan.gapMinutes(span,
                new RestSpan(a.getId(), PERIOD_START, LocalTime.of(9, 0), LocalTime.of(17, 0), anchor)))
                .isEqualTo(1740);
    }

    @Test
    @DisplayName("the predecessor's own snapshotted mode decides the branch, even though this method is never told the desk's current mode at all")
    void predecessorModeDecidesBranch_regardlessOfAnyNotionOfCurrentMode() {
        // This is the same fixture shape as slotModePredecessor_groupsByAgent_oneSpanPerAgent --
        // the point under test is structural, not behavioural: resolvePriorSpans has NO parameter
        // carrying the desk's current scheduling mode at all, so a SLOT-scheduled predecessor
        // resolves SLOT spans unconditionally. A future "fix" that reads a desk's current mode
        // here would be a regression this test is positioned to catch once such a parameter is
        // (wrongly) introduced -- today it asserts the only mode source that exists is honoured.
        UUID scheduleId = UUID.randomUUID();
        Agent a1 = agent();
        List<String> warnings = new ArrayList<>();

        when(acceptedScheduleDateRepository.findByTenantIdAndDeskIdAndDateInAndStatus(
                eq(TENANT_ID), eq(DESK_ID), any(), eq(AcceptedScheduleDateStatus.ACCEPTED)))
                .thenReturn(List.of(acceptedDate(scheduleId)));
        when(scheduleRepository.findByIdAndTenantIdAndDeskId(scheduleId, TENANT_ID, DESK_ID))
                .thenReturn(Optional.of(predecessorSchedule(SchedulingMode.SLOT, DAY_START)));
        when(agentAssignmentRepository.findWithRelationsByTenantIdAndDeskIdAndScheduleIdAndBusinessDate(
                TENANT_ID, DESK_ID, scheduleId, LOOKBACK_DATE))
                .thenReturn(List.of(slotRow(a1, LocalTime.of(9, 0), LocalTime.of(17, 0))));

        List<RestSpan> result = service.resolvePriorSpans(
                TENANT_ID, DESK_ID, PERIOD_START, 660, DAY_START, warnings);

        assertThat(result).hasSize(1);
        verify(agentShiftAssignmentRepository, never())
                .findWithRelationsByTenantIdAndDeskIdAndScheduleIdAndDate(anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("issues at most three queries in total for a 300-agent fixture: the accepted-date check, the predecessor-schedule load, and one date-filtered row read")
    void threeHundredAgents_issuesAtMostThreeQueriesTotal() {
        UUID scheduleId = UUID.randomUUID();
        List<AgentShiftAssignment> rows = new ArrayList<>();
        for (int i = 0; i < 300; i++) {
            rows.add(shiftRow(agent(), LocalTime.of(14, 0), LocalTime.of(22, 0)));
        }
        List<String> warnings = new ArrayList<>();

        when(acceptedScheduleDateRepository.findByTenantIdAndDeskIdAndDateInAndStatus(
                eq(TENANT_ID), eq(DESK_ID), any(), eq(AcceptedScheduleDateStatus.ACCEPTED)))
                .thenReturn(List.of(acceptedDate(scheduleId)));
        when(scheduleRepository.findByIdAndTenantIdAndDeskId(scheduleId, TENANT_ID, DESK_ID))
                .thenReturn(Optional.of(predecessorSchedule(SchedulingMode.SHIFT, DAY_START)));
        when(agentShiftAssignmentRepository.findWithRelationsByTenantIdAndDeskIdAndScheduleIdAndDate(
                TENANT_ID, DESK_ID, scheduleId, LOOKBACK_DATE))
                .thenReturn(rows);

        List<RestSpan> result = service.resolvePriorSpans(
                TENANT_ID, DESK_ID, PERIOD_START, 660, DAY_START, warnings);

        assertThat(result).hasSize(300);
        verify(acceptedScheduleDateRepository, times(1)).findByTenantIdAndDeskIdAndDateInAndStatus(
                eq(TENANT_ID), eq(DESK_ID), any(), eq(AcceptedScheduleDateStatus.ACCEPTED));
        verify(scheduleRepository, times(1))
                .findByIdAndTenantIdAndDeskId(scheduleId, TENANT_ID, DESK_ID);
        verify(agentShiftAssignmentRepository, times(1))
                .findWithRelationsByTenantIdAndDeskIdAndScheduleIdAndDate(
                        TENANT_ID, DESK_ID, scheduleId, LOOKBACK_DATE);
        verifyNoMoreInteractions(acceptedScheduleDateRepository, scheduleRepository,
                agentShiftAssignmentRepository, agentAssignmentRepository);
    }

    @Test
    @DisplayName("an accepted shift row with a null denormalised start or end time is skipped and a warning is added")
    void shiftRowWithNullInstant_isSkipped_oneWarningAdded() {
        UUID scheduleId = UUID.randomUUID();
        Agent good = agent();
        Agent bad = agent();
        AgentShiftAssignment nullStart = shiftRow(bad, null, LocalTime.of(22, 0));
        List<String> warnings = new ArrayList<>();

        when(acceptedScheduleDateRepository.findByTenantIdAndDeskIdAndDateInAndStatus(
                eq(TENANT_ID), eq(DESK_ID), any(), eq(AcceptedScheduleDateStatus.ACCEPTED)))
                .thenReturn(List.of(acceptedDate(scheduleId)));
        when(scheduleRepository.findByIdAndTenantIdAndDeskId(scheduleId, TENANT_ID, DESK_ID))
                .thenReturn(Optional.of(predecessorSchedule(SchedulingMode.SHIFT, DAY_START)));
        when(agentShiftAssignmentRepository.findWithRelationsByTenantIdAndDeskIdAndScheduleIdAndDate(
                TENANT_ID, DESK_ID, scheduleId, LOOKBACK_DATE))
                .thenReturn(List.of(nullStart, shiftRow(good, LocalTime.of(14, 0), LocalTime.of(22, 0))));

        List<RestSpan> result = service.resolvePriorSpans(
                TENANT_ID, DESK_ID, PERIOD_START, 660, DAY_START, warnings);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).agentId()).isEqualTo(good.getId());
        assertThat(warnings).hasSize(1);
    }

    @Test
    @DisplayName("a predecessor schedule the accepted-date row points at but that no longer loads produces an empty list and one warning, never an exception")
    void predecessorScheduleMissing_returnsEmptyList_oneWarning_noException() {
        UUID scheduleId = UUID.randomUUID();
        List<String> warnings = new ArrayList<>();

        when(acceptedScheduleDateRepository.findByTenantIdAndDeskIdAndDateInAndStatus(
                eq(TENANT_ID), eq(DESK_ID), any(), eq(AcceptedScheduleDateStatus.ACCEPTED)))
                .thenReturn(List.of(acceptedDate(scheduleId)));
        when(scheduleRepository.findByIdAndTenantIdAndDeskId(scheduleId, TENANT_ID, DESK_ID))
                .thenReturn(Optional.empty());

        List<RestSpan> result = service.resolvePriorSpans(
                TENANT_ID, DESK_ID, PERIOD_START, 660, DAY_START, warnings);

        assertThat(result).isEmpty();
        assertThat(warnings).hasSize(1);
        verify(agentShiftAssignmentRepository, never())
                .findWithRelationsByTenantIdAndDeskIdAndScheduleIdAndDate(anyLong(), any(), any(), any());
        verify(agentAssignmentRepository, never())
                .findWithRelationsByTenantIdAndDeskIdAndScheduleIdAndBusinessDate(anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("a predecessor schedule anchored differently from the current schedule produces no spans and one warning")
    void predecessorDayStartDiffersFromCurrent_producesNoSpans_oneWarning() {
        UUID scheduleId = UUID.randomUUID();
        Agent a1 = agent();
        LocalTime predecessorAnchor = LocalTime.of(15, 0);
        LocalTime currentAnchor = LocalTime.MIDNIGHT;
        List<String> warnings = new ArrayList<>();

        when(acceptedScheduleDateRepository.findByTenantIdAndDeskIdAndDateInAndStatus(
                eq(TENANT_ID), eq(DESK_ID), any(), eq(AcceptedScheduleDateStatus.ACCEPTED)))
                .thenReturn(List.of(acceptedDate(scheduleId)));
        when(scheduleRepository.findByIdAndTenantIdAndDeskId(scheduleId, TENANT_ID, DESK_ID))
                .thenReturn(Optional.of(predecessorSchedule(SchedulingMode.SHIFT, predecessorAnchor)));
        when(agentShiftAssignmentRepository.findWithRelationsByTenantIdAndDeskIdAndScheduleIdAndDate(
                TENANT_ID, DESK_ID, scheduleId, LOOKBACK_DATE))
                .thenReturn(List.of(shiftRow(a1, LocalTime.of(14, 0), LocalTime.of(22, 0))));

        List<RestSpan> result = service.resolvePriorSpans(
                TENANT_ID, DESK_ID, PERIOD_START, 660, currentAnchor, warnings);

        assertThat(result).isEmpty();
        assertThat(warnings).hasSize(1);
    }

    @Test
    @DisplayName("a predecessor schedule anchored identically to the current schedule returns spans unfiltered")
    void predecessorDayStartMatchesCurrent_returnsSpans() {
        UUID scheduleId = UUID.randomUUID();
        Agent a1 = agent();
        List<String> warnings = new ArrayList<>();

        when(acceptedScheduleDateRepository.findByTenantIdAndDeskIdAndDateInAndStatus(
                eq(TENANT_ID), eq(DESK_ID), any(), eq(AcceptedScheduleDateStatus.ACCEPTED)))
                .thenReturn(List.of(acceptedDate(scheduleId)));
        when(scheduleRepository.findByIdAndTenantIdAndDeskId(scheduleId, TENANT_ID, DESK_ID))
                .thenReturn(Optional.of(predecessorSchedule(SchedulingMode.SHIFT, DAY_START)));
        when(agentShiftAssignmentRepository.findWithRelationsByTenantIdAndDeskIdAndScheduleIdAndDate(
                TENANT_ID, DESK_ID, scheduleId, LOOKBACK_DATE))
                .thenReturn(List.of(shiftRow(a1, LocalTime.of(14, 0), LocalTime.of(22, 0))));

        List<RestSpan> result = service.resolvePriorSpans(
                TENANT_ID, DESK_ID, PERIOD_START, 660, DAY_START, warnings);

        assertThat(result).hasSize(1);
        assertThat(warnings).isEmpty();
    }
}
