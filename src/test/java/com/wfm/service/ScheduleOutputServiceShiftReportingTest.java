package com.wfm.service;

import ai.timefold.solver.core.api.solver.SolverFactory;
import com.wfm.dto.ScheduleDetailResponse.AgentScheduleEntry;
import com.wfm.dto.ScheduleDetailResponse.BreakDetail;
import com.wfm.dto.ScheduleDetailResponse.ShiftEnvelopeDivergence;
import com.wfm.model.Agent;
import com.wfm.model.AgentAssignment;
import com.wfm.model.AgentShiftAssignment;
import com.wfm.model.Schedule;
import com.wfm.model.ShiftBandPair;
import com.wfm.model.ShiftTemplate;
import com.wfm.model.ShiftTemplateBreakBand;
import com.wfm.model.Specialization;
import com.wfm.model.Timeslot;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 15 plan 10 (G-15-10 D4 gap closure) — inverts
 * {@code ShiftModeBreakGeometryCharacterisationTest#reportLayer_gapDerivedBreaks_relabelEveryHoleAsABreak}.
 * Fixture mirrors that characterisation test and the live UAT screenshot it diagnosed: a "Late"
 * template, envelope 12:00-21:00, one band at offset 240m (16:00) for 60m, an agent contracted to
 * exactly the pair's 8h net, on a desk grid strictly WIDER than the envelope (08:00-21:00) so an
 * out-of-envelope seat is constructible. The SCATTERED geometry — seats at
 * 08,12,14,15,17,18,19,20 — is the exact shape the live desk produced: one out-of-envelope seat
 * (08:00) forcing exactly one surrendered in-envelope slot (13:00), alongside the real band break
 * (16:00).
 */
class ScheduleOutputServiceShiftReportingTest {

    private static final SolverFactory<Schedule> SOLVER_FACTORY =
            SolverFactory.createFromXmlResource("solverConfig.xml");

    private final ScheduleOutputService service = new ScheduleOutputService(SOLVER_FACTORY);

    private static final LocalDate DAY = LocalDate.of(2026, 9, 7);
    private static final int INCREMENT = 60;

    /** The desk's grid — deliberately WIDER than the envelope, as on the live desk. */
    private static final LocalTime OPERATING_START = LocalTime.of(8, 0);
    private static final LocalTime OPERATING_END = LocalTime.of(21, 0);

    /** The "Late" template from the live screenshot. */
    private static final LocalTime ENVELOPE_START = LocalTime.of(12, 0);
    private static final LocalTime ENVELOPE_END = LocalTime.of(21, 0);
    private static final int BAND_OFFSET_MINUTES = 240; // break 16:00
    private static final int BAND_DURATION_MINUTES = 60; // .. to 17:00

    private static final List<LocalTime> SANE = times(12, 13, 14, 15, 17, 18, 19, 20);
    private static final List<LocalTime> SCATTERED = times(8, 12, 14, 15, 17, 18, 19, 20);

    // ------------------------------------------------------------------
    //  Test 1 — span
    // ------------------------------------------------------------------

    @Test
    void buildAgentSchedule_strayOutOfEnvelopeSeat_reportsTheTemplateSpanNotTheSeatSpan() {
        Schedule schedule = scheduleWithLiveDescriptor(SCATTERED);

        AgentScheduleEntry entry = onlyEntry(schedule);

        assertThat(entry.shiftStart()).isEqualTo(ENVELOPE_START);
        assertThat(entry.shiftEnd()).isEqualTo(ENVELOPE_END);
    }

    // ------------------------------------------------------------------
    //  Test 2 — breaks
    // ------------------------------------------------------------------

    @Test
    void buildAgentSchedule_strayOutOfEnvelopeSeat_reportsExactlyOneBandShapedBreak() {
        Schedule schedule = scheduleWithLiveDescriptor(SCATTERED);

        AgentScheduleEntry entry = onlyEntry(schedule);

        assertThat(entry.breaks()).hasSize(1);
        BreakDetail brk = entry.breaks().get(0);
        assertThat(brk.startTime()).isEqualTo(LocalTime.of(16, 0));
        assertThat(brk.endTime()).isEqualTo(LocalTime.of(17, 0));
        assertThat(brk.durationMinutes()).isEqualTo(BAND_DURATION_MINUTES);
    }

    // ------------------------------------------------------------------
    //  Test 3 — divergence
    // ------------------------------------------------------------------

    @Test
    void buildAgentSchedule_strayOutOfEnvelopeSeat_namesBothSidesOfTheDivergenceWithEqualSizeLists() {
        Schedule schedule = scheduleWithLiveDescriptor(SCATTERED);

        AgentScheduleEntry entry = onlyEntry(schedule);

        ShiftEnvelopeDivergence divergence = entry.divergence();
        assertThat(divergence).isNotNull();
        assertThat(divergence.outOfEnvelopeSeats()).containsExactly(LocalTime.of(8, 0));
        assertThat(divergence.unworkedLegalSlots()).containsExactly(LocalTime.of(13, 0));
        assertThat(divergence.outOfEnvelopeSeats()).hasSameSizeAs(divergence.unworkedLegalSlots());
    }

    // ------------------------------------------------------------------
    //  Test 4 — clean agent-day
    // ------------------------------------------------------------------

    @Test
    void buildAgentSchedule_cleanAgentDay_reportsNullDivergenceTemplateSpanAndOneBandBreak() {
        Schedule schedule = scheduleWithLiveDescriptor(SANE);

        AgentScheduleEntry entry = onlyEntry(schedule);

        assertThat(entry.divergence()).isNull();
        assertThat(entry.shiftStart()).isEqualTo(ENVELOPE_START);
        assertThat(entry.shiftEnd()).isEqualTo(ENVELOPE_END);
        assertThat(entry.breaks()).hasSize(1);
        assertThat(entry.breaks().get(0).startTime()).isEqualTo(LocalTime.of(16, 0));
    }

    // ------------------------------------------------------------------
    //  Test 5 — unassigned shift
    // ------------------------------------------------------------------

    @Test
    void buildAgentSchedule_unassignedShift_fallsBackToSeatDerivedOutputWithNoDivergenceAndDoesNotThrow() {
        Agent agent = agent("Ana");
        Specialization spec = specialization("S1");

        // Two seats with a gap between them — the pre-existing seat-derived/gap-derived
        // behaviour this branch must preserve exactly.
        Timeslot ts1 = timeslot(LocalTime.of(8, 0));
        Timeslot ts2 = timeslot(LocalTime.of(10, 0));
        AgentAssignment a1 = assignment(agent, ts1, spec);
        AgentAssignment a2 = assignment(agent, ts2, spec);

        // The shift row exists (a shift-mode agent-day) but the solver left it unassigned: no
        // shiftBandPair AND no D-07 denormalised columns, so resolveShiftDescriptor returns null.
        AgentShiftAssignment unassignedShiftRow = new AgentShiftAssignment();
        unassignedShiftRow.setId(UUID.randomUUID());
        unassignedShiftRow.setAgent(agent);
        unassignedShiftRow.setDate(DAY);
        unassignedShiftRow.setShiftBandPair(null);

        Schedule schedule = new Schedule();
        schedule.setIncrementMinutes(INCREMENT);
        schedule.setAssignments(new ArrayList<>(List.of(a1, a2)));
        schedule.setShiftAssignments(new ArrayList<>(List.of(unassignedShiftRow)));
        schedule.setTimeslots(new ArrayList<>(List.of(ts1, ts2)));

        AgentScheduleEntry entry = onlyEntry(schedule);

        assertThat(entry.shift()).isNull();
        assertThat(entry.divergence()).isNull();
        assertThat(entry.shiftStart()).isEqualTo(LocalTime.of(8, 0));
        assertThat(entry.shiftEnd()).isEqualTo(LocalTime.of(11, 0));
        assertThat(entry.breaks()).hasSize(1);
        assertThat(entry.breaks().get(0).startTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(entry.breaks().get(0).endTime()).isEqualTo(LocalTime.of(10, 0));
    }

    // ------------------------------------------------------------------
    //  Test 6 — slot invariance
    // ------------------------------------------------------------------

    @Test
    void buildAgentSchedule_slotMode_producesByteIdenticalEntriesToToday() {
        Agent agent = agent("Ana");
        Specialization spec = specialization("S1");

        Timeslot ts1 = timeslot(LocalTime.of(8, 0));
        Timeslot ts2 = timeslot(LocalTime.of(10, 0));
        AgentAssignment a1 = assignment(agent, ts1, spec);
        AgentAssignment a2 = assignment(agent, ts2, spec);

        Schedule schedule = new Schedule();
        schedule.setIncrementMinutes(INCREMENT);
        schedule.setAssignments(new ArrayList<>(List.of(a1, a2)));
        // A slot-scheduled desk carries no shift assignments at all.
        schedule.setShiftAssignments(new ArrayList<>());
        schedule.setTimeslots(new ArrayList<>(List.of(ts1, ts2)));

        AgentScheduleEntry entry = onlyEntry(schedule);

        assertThat(entry.shift()).isNull();
        assertThat(entry.divergence()).isNull();
        assertThat(entry.shiftStart()).isEqualTo(LocalTime.of(8, 0));
        assertThat(entry.shiftEnd()).isEqualTo(LocalTime.of(11, 0));
        assertThat(entry.breaks()).hasSize(1);
        assertThat(entry.breaks().get(0).startTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(entry.breaks().get(0).endTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(entry.breaks().get(0).durationMinutes()).isEqualTo(60);
    }

    // ------------------------------------------------------------------
    //  Test 7 — accepted schedule (D-07 denormalised columns)
    // ------------------------------------------------------------------

    @Test
    void buildAgentSchedule_acceptedScheduleDenormalisedColumns_resolvesThroughTheSameDescriptorPath() {
        Schedule schedule = scheduleWithAcceptedDescriptor(SCATTERED);

        AgentScheduleEntry entry = onlyEntry(schedule);

        assertThat(entry.shiftStart()).isEqualTo(ENVELOPE_START);
        assertThat(entry.shiftEnd()).isEqualTo(ENVELOPE_END);
        assertThat(entry.breaks()).hasSize(1);
        assertThat(entry.breaks().get(0).startTime()).isEqualTo(LocalTime.of(16, 0));
        assertThat(entry.divergence()).isNotNull();
        assertThat(entry.divergence().outOfEnvelopeSeats()).containsExactly(LocalTime.of(8, 0));
        assertThat(entry.divergence().unworkedLegalSlots()).containsExactly(LocalTime.of(13, 0));
    }

    // ------------------------------------------------------------------
    //  Fixture plumbing
    // ------------------------------------------------------------------

    private AgentScheduleEntry onlyEntry(Schedule schedule) {
        List<AgentScheduleEntry> entries = service.buildAgentSchedule(schedule);
        assertThat(entries).hasSize(1);
        return entries.get(0);
    }

    /** Every operating-window timeslot, hourly, keyed by start time. */
    private List<Timeslot> allOperatingTimeslots() {
        List<Timeslot> slots = new ArrayList<>();
        for (LocalTime t = OPERATING_START; t.isBefore(OPERATING_END); t = t.plusMinutes(INCREMENT)) {
            slots.add(timeslot(t));
        }
        return slots;
    }

    private Schedule scheduleWithLiveDescriptor(List<LocalTime> heldSeatStarts) {
        Agent agent = agent("Evelina");
        Specialization spec = specialization("Chat");

        ShiftTemplate template = new ShiftTemplate();
        template.setId(UUID.randomUUID());
        template.setName("Late");
        template.setStartTime(ENVELOPE_START);
        template.setEndTime(ENVELOPE_END);
        template.setEffectiveFrom(LocalDate.of(2020, 1, 1));

        ShiftTemplateBreakBand band = new ShiftTemplateBreakBand();
        band.setId(UUID.randomUUID());
        band.setShiftTemplate(template);
        band.setOffsetMinutes(BAND_OFFSET_MINUTES);
        band.setDurationMinutes(BAND_DURATION_MINUTES);

        List<Timeslot> allTimeslots = allOperatingTimeslots();
        List<AgentAssignment> heldSeats = heldSeats(agent, spec, allTimeslots, heldSeatStarts);

        AgentShiftAssignment shiftRow = new AgentShiftAssignment();
        shiftRow.setId(UUID.randomUUID());
        shiftRow.setAgent(agent);
        shiftRow.setDate(DAY);
        shiftRow.setShiftBandPair(new ShiftBandPair(template, band));

        Schedule schedule = new Schedule();
        schedule.setIncrementMinutes(INCREMENT);
        schedule.setAssignments(new ArrayList<>(heldSeats));
        schedule.setShiftAssignments(new ArrayList<>(List.of(shiftRow)));
        schedule.setTimeslots(new ArrayList<>(allTimeslots));
        return schedule;
    }

    private Schedule scheduleWithAcceptedDescriptor(List<LocalTime> heldSeatStarts) {
        Agent agent = agent("Evelina");
        Specialization spec = specialization("Chat");

        List<Timeslot> allTimeslots = allOperatingTimeslots();
        List<AgentAssignment> heldSeats = heldSeats(agent, spec, allTimeslots, heldSeatStarts);

        // Simulates a row reloaded via loadSnapshotData: transient shiftBandPair is null (JPA
        // never populates @Transient fields), only the D-07 denormalised scalars are present.
        AgentShiftAssignment shiftRow = new AgentShiftAssignment();
        shiftRow.setId(UUID.randomUUID());
        shiftRow.setAgent(agent);
        shiftRow.setDate(DAY);
        shiftRow.setShiftBandPair(null);
        shiftRow.setTemplateName("Late");
        shiftRow.setShiftStartTime(ENVELOPE_START);
        shiftRow.setShiftEndTime(ENVELOPE_END);
        shiftRow.setBandOffsetMinutes(BAND_OFFSET_MINUTES);
        shiftRow.setBandDurationMinutes(BAND_DURATION_MINUTES);
        shiftRow.setSourceTemplateId(UUID.randomUUID());

        Schedule schedule = new Schedule();
        schedule.setIncrementMinutes(INCREMENT);
        schedule.setAssignments(new ArrayList<>(heldSeats));
        schedule.setShiftAssignments(new ArrayList<>(List.of(shiftRow)));
        schedule.setTimeslots(new ArrayList<>(allTimeslots));
        return schedule;
    }

    private List<AgentAssignment> heldSeats(Agent agent, Specialization spec,
            List<Timeslot> allTimeslots, List<LocalTime> heldSeatStarts) {
        List<AgentAssignment> seats = new ArrayList<>();
        for (LocalTime start : heldSeatStarts) {
            Timeslot ts = allTimeslots.stream()
                    .filter(t -> t.getStartTime().equals(start))
                    .findFirst()
                    .orElseThrow();
            seats.add(assignment(agent, ts, spec));
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

    private Timeslot timeslot(LocalTime start) {
        Timeslot ts = new Timeslot();
        ts.setId(UUID.randomUUID());
        ts.setDate(DAY);
        ts.setStartTime(start);
        ts.setEndTime(start.plusMinutes(INCREMENT));
        return ts;
    }

    private AgentAssignment assignment(Agent agent, Timeslot ts, Specialization spec) {
        AgentAssignment a = new AgentAssignment();
        a.setId(UUID.randomUUID());
        a.setAgent(agent);
        a.setTimeslot(ts);
        a.setRequiredSpecialization(spec);
        return a;
    }

    private static List<LocalTime> times(int... hours) {
        List<LocalTime> out = new ArrayList<>();
        for (int h : hours) {
            out.add(LocalTime.of(h, 0));
        }
        return List.copyOf(out);
    }
}
