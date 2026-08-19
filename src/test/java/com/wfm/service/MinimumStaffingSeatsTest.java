package com.wfm.service;

import com.wfm.model.AgentAssignment;
import com.wfm.model.Specialization;
import com.wfm.model.StaffingRequirement;
import com.wfm.model.Timeslot;
import com.wfm.solver.ScheduleConstraintProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers {@link SolverService#expandMinimumStaffingSeats}.
 *
 * <p>Regression context: the "Minimum staffing" constraint shipped unable to fire on the very
 * hours it was written for. {@code FteUploadService} skips a demand cell of zero, so no
 * StaffingRequirement is persisted and {@code expandAssignments} creates no seat; the
 * constraint groups AgentAssignment by timeslot, and a groupBy emits no group for a key
 * absent from the stream, so a bare hour produced no penalty at ANY weight — promoting the
 * constraint to hard changed nothing. Its own unit tests missed this because every case
 * supplied an existing-but-unassigned seat rather than the real no-seat-at-all state.
 */
class MinimumStaffingSeatsTest {

    private static final long TENANT = 1L;
    private static final UUID DESK = UUID.randomUUID();
    private static final UUID SCHEDULE = UUID.randomUUID();

    private static Timeslot timeslot(LocalTime start) {
        Timeslot ts = new Timeslot();
        ts.setId(UUID.randomUUID());
        ts.setDate(LocalDate.of(2026, 1, 10));
        ts.setStartTime(start);
        ts.setEndTime(start.plusHours(1));
        return ts;
    }

    private static Specialization spec(String name) {
        Specialization s = new Specialization();
        s.setId(UUID.randomUUID());
        s.setName(name);
        return s;
    }

    private static StaffingRequirement requirement(Timeslot ts, Specialization s, int ftes) {
        StaffingRequirement sr = new StaffingRequirement();
        sr.setId(UUID.randomUUID());
        sr.setTimeslot(ts);
        sr.setSpecialization(s);
        sr.setRequiredFTEs(ftes);
        return sr;
    }

    private static AgentAssignment seat(Timeslot ts, Specialization s) {
        AgentAssignment a = new AgentAssignment();
        a.setId(UUID.randomUUID());
        a.setTimeslot(ts);
        a.setRequiredSpecialization(s);
        return a;
    }

    @Test
    @DisplayName("a timeslot with no seat at all gets one — the zero-demand hour")
    void bareTimeslotGetsASeat() {
        Specialization english = spec("English");
        Timeslot bare = timeslot(LocalTime.of(8, 0));
        Timeslot busy = timeslot(LocalTime.of(11, 0));

        List<AgentAssignment> extra = SolverService.expandMinimumStaffingSeats(
                TENANT, DESK, SCHEDULE,
                List.of(bare, busy),
                new ArrayList<>(List.of(seat(busy, english))),
                List.of(requirement(busy, english, 44)),
                List.of(english));

        assertThat(extra).hasSize(1);
        assertThat(extra.get(0).getTimeslot()).isEqualTo(bare);
        assertThat(extra.get(0).getAgent()).isNull();
        assertThat(extra.get(0).getTenantId()).isEqualTo(TENANT);
        assertThat(extra.get(0).getDeskId()).isEqualTo(DESK);
        assertThat(extra.get(0).getScheduleId()).isEqualTo(SCHEDULE);
    }

    @Test
    @DisplayName("the seat carries a real specialization — a null would NPE in specializationMatch")
    void seatCarriesARealSpecialization() {
        Specialization english = spec("English");
        Timeslot bare = timeslot(LocalTime.of(9, 0));

        List<AgentAssignment> extra = SolverService.expandMinimumStaffingSeats(
                TENANT, DESK, SCHEDULE, List.of(bare), new ArrayList<>(),
                List.of(), List.of(english));

        assertThat(extra).hasSize(1);
        assertThat(extra.get(0).getRequiredSpecialization()).isNotNull();
        assertThat(extra.get(0).getRequiredSpecialization().getId()).isNotNull();
    }

    @Test
    @DisplayName("the seat uses the specialization carrying the most demand on the desk")
    void seatUsesPredominantSpecialization() {
        Specialization english = spec("English");
        Specialization payments = spec("Payments and Safety");
        Timeslot bare = timeslot(LocalTime.of(8, 0));
        Timeslot busy = timeslot(LocalTime.of(11, 0));

        List<AgentAssignment> extra = SolverService.expandMinimumStaffingSeats(
                TENANT, DESK, SCHEDULE,
                List.of(bare, busy),
                new ArrayList<>(List.of(seat(busy, english))),
                List.of(requirement(busy, payments, 3), requirement(busy, english, 44)),
                List.of(english, payments));

        assertThat(extra).hasSize(1);
        assertThat(extra.get(0).getRequiredSpecialization().getName()).isEqualTo("English");
    }

    @Test
    @DisplayName("a timeslot that already has a seat is left alone")
    void staffedTimeslotIsUntouched() {
        Specialization english = spec("English");
        Timeslot busy = timeslot(LocalTime.of(11, 0));

        List<AgentAssignment> extra = SolverService.expandMinimumStaffingSeats(
                TENANT, DESK, SCHEDULE,
                List.of(busy),
                new ArrayList<>(List.of(seat(busy, english), seat(busy, english))),
                List.of(requirement(busy, english, 2)),
                List.of(english));

        assertThat(extra).isEmpty();
    }

    @Test
    @DisplayName("the Saturday shape: three bare hours get one seat each")
    void everyBareHourGetsItsOwnSeat() {
        // 2026-01-10 demand: 0,0,0 at 08:00-10:00 then 44 at 11:00.
        Specialization english = spec("English");
        Timeslot eight = timeslot(LocalTime.of(8, 0));
        Timeslot nine = timeslot(LocalTime.of(9, 0));
        Timeslot ten = timeslot(LocalTime.of(10, 0));
        Timeslot eleven = timeslot(LocalTime.of(11, 0));

        List<AgentAssignment> extra = SolverService.expandMinimumStaffingSeats(
                TENANT, DESK, SCHEDULE,
                List.of(eight, nine, ten, eleven),
                new ArrayList<>(List.of(seat(eleven, english))),
                List.of(requirement(eleven, english, 44)),
                List.of(english));

        assertThat(extra).hasSize(3);
        assertThat(extra).allSatisfy(a -> assertThat(a.getAgent()).isNull());
        assertThat(extra).extracting(a -> a.getTimeslot().getStartTime())
                .containsExactlyInAnyOrder(LocalTime.of(8, 0), LocalTime.of(9, 0), LocalTime.of(10, 0));
    }

    @Test
    @DisplayName("expansion is deterministic — the same input expands identically")
    void expansionIsDeterministic() {
        Specialization a1 = spec("Alpha");
        Specialization b1 = spec("Beta");
        Timeslot bare = timeslot(LocalTime.of(8, 0));

        // Equal demand on both, so the tiebreak (lowest id) must decide, not map ordering.
        var reqs = List.of(requirement(bare, a1, 5), requirement(bare, b1, 5));
        String first = SolverService.expandMinimumStaffingSeats(
                TENANT, DESK, SCHEDULE, List.of(bare), new ArrayList<>(), reqs, List.of(a1, b1))
                .get(0).getRequiredSpecialization().getId().toString();
        for (int i = 0; i < 5; i++) {
            String again = SolverService.expandMinimumStaffingSeats(
                    TENANT, DESK, SCHEDULE, List.of(bare), new ArrayList<>(), reqs, List.of(a1, b1))
                    .get(0).getRequiredSpecialization().getId().toString();
            assertThat(again).isEqualTo(first);
        }
    }

    @Test
    @DisplayName("a desk with no specialization produces no seats rather than an NPE later")
    void noSpecializationYieldsNoSeats() {
        Timeslot bare = timeslot(LocalTime.of(8, 0));

        List<AgentAssignment> extra = SolverService.expandMinimumStaffingSeats(
                TENANT, DESK, SCHEDULE, List.of(bare), new ArrayList<>(), List.of(), List.of());

        assertThat(extra).isEmpty();
    }

    @Test
    @DisplayName("seat count matches the constraint's declared floor")
    void seatCountTracksTheConstraintFloor() {
        Specialization english = spec("English");
        Timeslot bare = timeslot(LocalTime.of(8, 0));

        List<AgentAssignment> extra = SolverService.expandMinimumStaffingSeats(
                TENANT, DESK, SCHEDULE, List.of(bare), new ArrayList<>(),
                List.of(), List.of(english));

        assertThat(extra).hasSize(ScheduleConstraintProvider.MIN_AGENTS_PER_TIMESLOT);
    }
}
