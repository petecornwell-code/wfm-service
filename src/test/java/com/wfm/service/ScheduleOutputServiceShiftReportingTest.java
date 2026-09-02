package com.wfm.service;

import ai.timefold.solver.core.api.solver.SolverFactory;
import com.wfm.dto.ScheduleDetailResponse.AgentScheduleEntry;
import com.wfm.dto.ScheduleDetailResponse.BreakDetail;
import com.wfm.dto.ScheduleDetailResponse.ConstraintViolationEntry;
import com.wfm.dto.ScheduleDetailResponse.PreferenceReport;
import com.wfm.dto.ScheduleDetailResponse.PreferenceReportEntry;
import com.wfm.dto.ScheduleDetailResponse.ShiftEnvelopeDivergence;
import com.wfm.dto.ScheduleDetailResponse.ViolationDetail;
import com.wfm.model.Agent;
import com.wfm.model.AgentAssignment;
import com.wfm.model.AgentPreference;
import com.wfm.model.AgentShiftAssignment;
import com.wfm.model.ConstraintWeights;
import com.wfm.model.Schedule;
import com.wfm.model.ShiftBandPair;
import com.wfm.model.ShiftTemplate;
import com.wfm.model.ShiftTemplateBreakBand;
import com.wfm.model.Specialization;
import com.wfm.model.Timeslot;
import com.wfm.solver.ScheduleConstraintProvider;
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
    //  UAT test 20 / review CR-04 — the warning headline must be IDEMPOTENT
    //
    //  buildAgentSchedule is a READ path. ScheduleService calls it on every GET of the schedule
    //  results, the results page polls that endpoint ~every 2s while a solve is RUNNING, and
    //  InMemoryScheduleStore.get returns the SAME Schedule instance by reference. Before the fix
    //  this method did a bare warnings.add(...), so the operator's warnings panel grew by one
    //  duplicate line per refresh, unbounded, for as long as the page stayed open.
    //
    //  No test in this suite asserted on getWarnings() at all, which is precisely how that
    //  shipped. These three are the missing guard.
    // ------------------------------------------------------------------

    @Test
    void buildAgentSchedule_repeatedPolls_doNotGrowTheWarningsList() {
        Schedule schedule = scheduleWithLiveDescriptor(SCATTERED);

        // Ten polls stands in for a results page left open ~20 seconds.
        for (int poll = 0; poll < 10; poll++) {
            service.buildAgentSchedule(schedule);
        }

        assertThat(schedule.getWarnings())
                .as("one divergence headline regardless of how many times the page polled")
                .filteredOn(w -> w.contains("fall outside their assigned shift envelope"))
                .hasSize(1);
    }

    @Test
    void buildAgentSchedule_preservesWarningsItDoesNotOwn() {
        Schedule schedule = scheduleWithLiveDescriptor(SCATTERED);
        // The solver's own capacity advisory (SolverService sets these once per solve). The
        // divergence republish must not collaterally drop another producer's warning.
        String capacityAdvisory = "Demand (100 FTE-slots) exceeds supply (80 hrs, 80 slots) by 20 slots.";
        schedule.getWarnings().add(capacityAdvisory);

        service.buildAgentSchedule(schedule);
        service.buildAgentSchedule(schedule);

        assertThat(schedule.getWarnings()).contains(capacityAdvisory);
        assertThat(schedule.getWarnings()).filteredOn(w -> w.equals(capacityAdvisory)).hasSize(1);
    }

    @Test
    void buildAgentSchedule_divergenceThatClears_removesItsStaleWarning() {
        // A divergent poll publishes the headline...
        Schedule diverged = scheduleWithLiveDescriptor(SCATTERED);
        service.buildAgentSchedule(diverged);
        assertThat(diverged.getWarnings())
                .anyMatch(w -> w.contains("fall outside their assigned shift envelope"));

        // ...and a later poll, once the solver has pulled every seat back inside the envelope,
        // must RETRACT it. Add-if-absent dedup would leave this stale line on screen forever.
        Schedule clean = scheduleWithLiveDescriptor(SANE);
        clean.getWarnings().add("1 agent-day seat(s) fall outside their assigned shift envelope; "
                + "1 legal envelope slot(s) went unworked.");

        service.buildAgentSchedule(clean);

        assertThat(clean.getWarnings())
                .noneMatch(w -> w.contains("fall outside their assigned shift envelope"));
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
    //  T-15-10-05 — preference-report break KPI switches to the band window on shift desks
    // ------------------------------------------------------------------

    @Test
    void buildPreferenceReport_shiftDesk_actualBreakTimeAndHonouredFlagUseTheBandWindowNotAGapDerivedHole() {
        Schedule schedule = scheduleWithLiveDescriptor(SCATTERED);

        // Pre-fix, findBreaks(dayAssignments) on SCATTERED yields a merged 09:00-12:00 (180m)
        // "break" — a pure span artifact from the 08:00 stray seat, not a real break — plus
        // 13:00-14:00 and the real 16:00-17:00 band break. A preference for 09:00 would have been
        // (wrongly) reported as honoured against that artifact. Post-fix, the only actual break is
        // the band window at 16:00, so a 09:00 preference must NOT be honoured.
        Agent agent = schedule.getAssignments().get(0).getAgent();
        AgentPreference pref = new AgentPreference();
        pref.setId(UUID.randomUUID());
        pref.setAgent(agent);
        pref.setDate(DAY);
        pref.setDayOfWeek(DAY.getDayOfWeek());
        pref.setPreferredBreakTime(LocalTime.of(9, 0));
        schedule.setAgentPreferences(new ArrayList<>(List.of(pref)));
        schedule.setBreakDurationMinutes(60);

        PreferenceReport report = service.buildPreferenceReport(schedule);

        assertThat(report.entries()).hasSize(1);
        PreferenceReportEntry entry = report.entries().get(0);
        assertThat(entry.actualBreakTime()).isEqualTo(LocalTime.of(16, 0));
        assertThat(entry.breakTimeHonoured()).isFalse();
    }

    // ------------------------------------------------------------------
    //  Task 2 (G-15-32 gap closure) — accepted-path constraint violation report
    // ------------------------------------------------------------------
    //
    //  Named-row shape from 15-UAT.md's G-15-32 proof: Armaz Dugashvili, 2026-01-05, shift
    //  "Mid 11:00-20:00", bandOffset 300 (break 16:00-17:00), held seats 11,12,13,14,15,17,18,19
    //  -- every seat inside the envelope, none in the break window, server divergence null. Before
    //  the fix ALL EIGHT were wrongly reported as violations (the constant "every staffed seat"
    //  arithmetic); this section pins the fix at the ScheduleOutputService level directly.

    private static final LocalTime NAMED_ROW_ENVELOPE_START = LocalTime.of(11, 0);
    private static final LocalTime NAMED_ROW_ENVELOPE_END = LocalTime.of(20, 0);
    private static final int NAMED_ROW_BAND_OFFSET_MINUTES = 300; // break 16:00
    private static final int NAMED_ROW_BAND_DURATION_MINUTES = 60; // .. to 17:00
    private static final List<LocalTime> NAMED_ROW_HELD_SEATS =
            times(11, 12, 13, 14, 15, 17, 18, 19);

    @Test
    void buildConstraintViolations_acceptedNamedRowShape_reportsNoEnvelopeViolation() {
        Schedule schedule = acceptedScheduleWithEnvelope(NAMED_ROW_ENVELOPE_START, NAMED_ROW_ENVELOPE_END,
                NAMED_ROW_BAND_OFFSET_MINUTES, NAMED_ROW_BAND_DURATION_MINUTES,
                new AgentDayFixture("Armaz Dugashvili", DAY, NAMED_ROW_HELD_SEATS));
        schedule.setConstraintWeights(new ConstraintWeights());

        List<ConstraintViolationEntry> violations = service.buildConstraintViolations(schedule, true);

        assertThat(violations).isEmpty();
    }

    @Test
    void buildConstraintViolations_acceptedCleanMultiAgentDay_countIsZeroNeverTheStaffedSeatConstant() {
        // Constant-1104 regression: 1104 == 138 agent-days x 8 contracted hours, i.e. N*H where N
        // is agent-day count and H is legal-seat count -- exactly the impossible arithmetic every
        // held seat failing the coverage predicate produces. Two agent-days (N=2) each legally
        // holding the same 8-seat named-row shape (H=8) must report 0, explicitly pinned as NOT
        // N*H (16), not merely "some number other than 1104".
        Schedule schedule = acceptedScheduleWithEnvelope(NAMED_ROW_ENVELOPE_START, NAMED_ROW_ENVELOPE_END,
                NAMED_ROW_BAND_OFFSET_MINUTES, NAMED_ROW_BAND_DURATION_MINUTES,
                new AgentDayFixture("Armaz Dugashvili", DAY, NAMED_ROW_HELD_SEATS),
                new AgentDayFixture("Beso Kapanadze", DAY.plusDays(1), NAMED_ROW_HELD_SEATS));
        schedule.setConstraintWeights(new ConstraintWeights());

        List<ConstraintViolationEntry> violations = service.buildConstraintViolations(schedule, true);

        int impossibleConstant = 2 * NAMED_ROW_HELD_SEATS.size(); // N*H = 16
        int reportedCount = violations.stream().mapToInt(ConstraintViolationEntry::violationCount).sum();
        assertThat(reportedCount).isNotEqualTo(impossibleConstant);
        assertThat(reportedCount).isZero();
        assertThat(violations).isEmpty();
    }

    @Test
    void buildConstraintViolations_acceptedRedProof_oneRelocatedSeatReportsExactlyOneViolationNamingIt() {
        Schedule schedule = acceptedScheduleWithEnvelope(NAMED_ROW_ENVELOPE_START, NAMED_ROW_ENVELOPE_END,
                NAMED_ROW_BAND_OFFSET_MINUTES, NAMED_ROW_BAND_DURATION_MINUTES,
                new AgentDayFixture("Armaz Dugashvili", DAY, NAMED_ROW_HELD_SEATS));
        schedule.setConstraintWeights(new ConstraintWeights());

        // Sanity: clean before relocation -- the red-proof means nothing without a green start.
        assertThat(service.buildConstraintViolations(schedule, true)).isEmpty();

        // Relocate ONE seat (11:00) to sit BEFORE the envelope start, via a NEW synthetic Timeslot
        // on that one assignment only -- never mutate a Timeslot other seats might share, the trap
        // ShiftEnvelopeGroundTruthTest.relocateSeat documents.
        AgentAssignment victim = schedule.getAssignments().get(0);
        UUID expectedAgentId = victim.getAgent().getId();
        Timeslot relocated = new Timeslot();
        relocated.setId(UUID.randomUUID());
        relocated.setDate(DAY);
        relocated.setStartTime(LocalTime.of(9, 0));
        relocated.setEndTime(LocalTime.of(10, 0));
        victim.setTimeslot(relocated);

        List<ConstraintViolationEntry> violations = service.buildConstraintViolations(schedule, true);

        assertThat(violations).hasSize(1);
        ConstraintViolationEntry entry = violations.get(0);
        assertThat(entry.constraintName())
                .isEqualTo(ScheduleConstraintProvider.SHIFT_ENVELOPE_COMPLIANCE_CONSTRAINT_NAME);
        assertThat(entry.level()).isEqualTo("HARD");
        assertThat(entry.violationCount()).isEqualTo(1);
        assertThat(entry.violations()).hasSize(1);
        ViolationDetail detail = entry.violations().get(0);
        assertThat(detail.agentId()).isEqualTo(expectedAgentId);
        assertThat(detail.timeslotId()).isEqualTo(relocated.getId());
        assertThat(detail.timeslotLabel()).contains("09:00");
    }

    @Test
    void buildConstraintViolations_liveUnaccepted_nullWeightsGuardStaysScopedToLivePath() {
        // The demoted safety net (Task 1): with isAcceptedSnapshot=false and no ConstraintWeights,
        // the pre-existing guard must still return empty rather than attempting to explain() —
        // proving the guard survived being moved to AFTER the provenance branch, not just removed.
        Schedule schedule = scheduleWithLiveDescriptor(SANE);
        assertThat(schedule.getConstraintWeights()).isNull();

        List<ConstraintViolationEntry> violations = service.buildConstraintViolations(schedule, false);

        assertThat(violations).isEmpty();
    }

    private record AgentDayFixture(String agentName, LocalDate date, List<LocalTime> heldSeatStarts) {}

    /**
     * Accepted/reloaded-shaped schedule fixture for the constraint-violation-report tests: every
     * agent-day carries the D-07 denormalised scalars (no live transient {@code shiftBandPair}),
     * exactly as {@link #scheduleWithAcceptedDescriptor} does for the agent-schedule tests above.
     * {@code buildAcceptedConstraintViolations} reads only assignments + shiftAssignments +
     * constraintWeights, so no timeslot grid is needed here.
     */
    private Schedule acceptedScheduleWithEnvelope(LocalTime envelopeStart, LocalTime envelopeEnd,
            int bandOffsetMinutes, int bandDurationMinutes, AgentDayFixture... agentDays) {
        Specialization spec = specialization("Chat");
        java.util.Map<String, Agent> agentsByName = new java.util.HashMap<>();

        List<AgentAssignment> allAssignments = new ArrayList<>();
        List<AgentShiftAssignment> allShiftRows = new ArrayList<>();

        for (AgentDayFixture day : agentDays) {
            Agent agent = agentsByName.computeIfAbsent(day.agentName(), this::agent);

            for (LocalTime start : day.heldSeatStarts()) {
                Timeslot ts = new Timeslot();
                ts.setId(UUID.randomUUID());
                ts.setDate(day.date());
                ts.setStartTime(start);
                ts.setEndTime(start.plusMinutes(INCREMENT));
                allAssignments.add(assignment(agent, ts, spec));
            }

            AgentShiftAssignment shiftRow = new AgentShiftAssignment();
            shiftRow.setId(UUID.randomUUID());
            shiftRow.setAgent(agent);
            shiftRow.setDate(day.date());
            shiftRow.setShiftBandPair(null);
            shiftRow.setTemplateName("Mid");
            shiftRow.setShiftStartTime(envelopeStart);
            shiftRow.setShiftEndTime(envelopeEnd);
            shiftRow.setBandOffsetMinutes(bandOffsetMinutes);
            shiftRow.setBandDurationMinutes(bandDurationMinutes);
            shiftRow.setSourceTemplateId(UUID.randomUUID());
            allShiftRows.add(shiftRow);
        }

        Schedule schedule = new Schedule();
        schedule.setIncrementMinutes(INCREMENT);
        schedule.setAssignments(new ArrayList<>(allAssignments));
        schedule.setShiftAssignments(new ArrayList<>(allShiftRows));
        schedule.setTimeslots(new ArrayList<>());
        return schedule;
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
        template.setValidWeekdays(java.util.EnumSet.allOf(java.time.DayOfWeek.class));
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
