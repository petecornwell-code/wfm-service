package com.wfm.service;

import ai.timefold.solver.core.api.solver.SolverFactory;
import com.wfm.dto.ScheduleDetailResponse.AgentScheduleEntry;
import com.wfm.dto.ScheduleDetailResponse.BreakDetail;
import com.wfm.dto.ScheduleDetailResponse.ConstraintViolationEntry;
import com.wfm.dto.ScheduleDetailResponse.PreferenceReport;
import com.wfm.dto.ScheduleDetailResponse.PreferenceReportEntry;
import com.wfm.dto.ScheduleDetailResponse.ShiftEnvelopeDivergence;
import com.wfm.dto.ScheduleDetailResponse.StaffingSummaryEntry;
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
import com.wfm.model.StaffingRequirement;
import com.wfm.model.Timeslot;
import com.wfm.repository.AgentUsualShiftRepository;
import com.wfm.repository.ShiftTemplateRepository;
import com.wfm.solver.ScheduleConstraintProvider;
import com.wfm.util.DayWindow;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

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

    // Phase 17: ScheduleOutputService's constructor widened to take the drift-report
    // dependencies. This test exercises only buildAgentSchedule/buildPreferenceReport
    // (buildDriftReport is exercised separately in DriftReportTest), so plain mocks are
    // sufficient here — mirrors ScheduleServiceShiftSnapshotTest's realOutputService() helper.
    private final ScheduleOutputService service = new ScheduleOutputService(SOLVER_FACTORY,
            new UsualShiftResolutionService(mock(ShiftTemplateRepository.class)),
            mock(AgentUsualShiftRepository.class));

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
        // BDAY-04 (plan 19-05): buildAgentSchedule/buildConstraintViolations now bind a DayWindow
        // from this Schedule's own dayStart — an explicit midnight anchor here, not a production
        // fallback, mirroring every other migrated test caller's convention.
        schedule.setDayStart(LocalTime.MIDNIGHT);
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
        // BDAY-04 (plan 19-05): buildAgentSchedule/buildConstraintViolations now bind a DayWindow
        // from this Schedule's own dayStart — an explicit midnight anchor here, not a production
        // fallback, mirroring every other migrated test caller's convention.
        schedule.setDayStart(LocalTime.MIDNIGHT);
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
    //  OVNT-02/plan 21-12 — the preference-report start check now compares anchored minute, not
    //  raw clock order. A 22:00 actual start against a 06:00 preferred start is the textbook case:
    //  at a 21:00 anchor, 22:00 is only ONE hour into the business day while 06:00 is NINE hours
    //  in, so the actual start genuinely came BEFORE the preference in business-day order and
    //  must NOT be honoured -- the opposite of what raw isBefore on clock values would ever say
    //  (22:00 is never clock-before 06:00, so the old check read "honoured" when the agent
    //  started objectively too early). At a 00:00 anchor the two orderings coincide, so the
    //  control test is byte-identical to the pre-conversion verdict.
    // ------------------------------------------------------------------

    @Test
    void buildPreferenceReport_anchoredDesk_startCheckUsesAnchoredOrderNotClockOrder() {
        Agent nightOwl = agent("Night Owl");
        Specialization spec = specialization("Chat");
        AgentAssignment lateClockEarlyAnchored = assignment(nightOwl, timeslot(LocalTime.of(22, 0)), spec);

        Schedule schedule = new Schedule();
        schedule.setIncrementMinutes(INCREMENT);
        schedule.setDayStart(LocalTime.of(21, 0));
        schedule.setAssignments(new ArrayList<>(List.of(lateClockEarlyAnchored)));
        schedule.setShiftAssignments(new ArrayList<>());
        schedule.setTimeslots(new ArrayList<>());

        AgentPreference pref = new AgentPreference();
        pref.setId(UUID.randomUUID());
        pref.setAgent(nightOwl);
        pref.setDate(DAY);
        pref.setPreferredStartTime(LocalTime.of(6, 0));
        schedule.setAgentPreferences(new ArrayList<>(List.of(pref)));

        PreferenceReport report = service.buildPreferenceReport(schedule);

        assertThat(report.entries()).hasSize(1);
        PreferenceReportEntry entry = report.entries().get(0);
        assertThat(entry.actualStartTime()).isEqualTo(LocalTime.of(22, 0));
        assertThat(entry.startTimeHonoured())
                .as("the actual start (22:00) is genuinely earlier than the preference (06:00) in "
                        + "business-day order at a 21:00 anchor, so it must NOT be honoured")
                .isFalse();
    }

    @Test
    void buildPreferenceReport_midnightAnchor_startCheckUnchangedFromClockOrder() {
        Agent nightOwl = agent("Night Owl");
        Specialization spec = specialization("Chat");
        AgentAssignment lateClockEarlyAnchored = assignment(nightOwl, timeslot(LocalTime.of(22, 0)), spec);

        Schedule schedule = new Schedule();
        schedule.setIncrementMinutes(INCREMENT);
        schedule.setDayStart(LocalTime.MIDNIGHT);
        schedule.setAssignments(new ArrayList<>(List.of(lateClockEarlyAnchored)));
        schedule.setShiftAssignments(new ArrayList<>());
        schedule.setTimeslots(new ArrayList<>());

        AgentPreference pref = new AgentPreference();
        pref.setId(UUID.randomUUID());
        pref.setAgent(nightOwl);
        pref.setDate(DAY);
        pref.setPreferredStartTime(LocalTime.of(6, 0));
        schedule.setAgentPreferences(new ArrayList<>(List.of(pref)));

        PreferenceReport report = service.buildPreferenceReport(schedule);

        assertThat(report.entries()).hasSize(1);
        PreferenceReportEntry entry = report.entries().get(0);
        // At a 00:00 anchor the anchored minute equals the clock minute, so this is a
        // byte-identical no-op against the pre-conversion verdict: 22:00 is clock-after 06:00, so
        // "at or after preferred" holds -> honoured.
        assertThat(entry.startTimeHonoured()).isTrue();
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
        // SOLV-07 (plan 20-06): ScheduleOutputService's grouping keys now read getBusinessDate(),
        // not getDate() -- every hand-built Timeslot in this suite must carry one or its
        // agent-day grouping silently falls apart (null key). This fixture is implicitly
        // 00:00-anchored, so businessDate == date, behaviourally inert.
        relocated.setBusinessDate(DAY);
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

    // ------------------------------------------------------------------
    //  Plan 21-03 Task 1 (OVNT-02/OVNT-07, D-14) — ViolationDetail carries structured
    //  businessDate/calendarDate/startTime/endTime alongside the unchanged timeslotLabel.
    //  Exercised through the accepted path (buildAcceptedConstraintViolations), reusing the
    //  relocateSeat trap the existing red-proof test above documents: a NEW synthetic Timeslot on
    //  one assignment only, never mutating a Timeslot other seats might share.
    // ------------------------------------------------------------------

    /**
     * Relocates the first assignment of a clean {@link #acceptedScheduleWithEnvelope} fixture to
     * a single hand-built {@link Timeslot} outside the NAMED_ROW envelope (11:00-20:00), which
     * produces exactly one HARD violation naming that timeslot — the fixture this plan's three
     * behavior tests all share.
     */
    private ViolationDetail singleRelocatedViolation(LocalTime dayStart, LocalDate calendarDate,
            LocalDate businessDate, LocalTime start, LocalTime end) {
        Schedule schedule = acceptedScheduleWithEnvelope(NAMED_ROW_ENVELOPE_START, NAMED_ROW_ENVELOPE_END,
                NAMED_ROW_BAND_OFFSET_MINUTES, NAMED_ROW_BAND_DURATION_MINUTES,
                new AgentDayFixture("Armaz Dugashvili", DAY, NAMED_ROW_HELD_SEATS));
        schedule.setConstraintWeights(new ConstraintWeights());
        schedule.setDayStart(dayStart);

        AgentAssignment victim = schedule.getAssignments().get(0);
        Timeslot relocated = new Timeslot();
        relocated.setId(UUID.randomUUID());
        relocated.setDate(calendarDate);
        relocated.setBusinessDate(businessDate);
        relocated.setStartTime(start);
        relocated.setEndTime(end);
        victim.setTimeslot(relocated);

        List<ConstraintViolationEntry> violations = service.buildConstraintViolations(schedule, true);
        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).violations()).hasSize(1);
        return violations.get(0).violations().get(0);
    }

    @Test
    void buildConstraintViolations_21_00AnchoredDesk_violationCarriesDistinctBusinessAndCalendarDates() {
        // A 02:00-03:00 slot on a 21:00-anchored desk: business date DAY, calendar date DAY+1.
        ViolationDetail detail = singleRelocatedViolation(LocalTime.of(21, 0),
                DAY.plusDays(1), DAY, LocalTime.of(2, 0), LocalTime.of(3, 0));

        assertThat(detail.businessDate()).isEqualTo(DAY);
        assertThat(detail.calendarDate()).isEqualTo(DAY.plusDays(1));
        assertThat(detail.startTime()).isEqualTo(LocalTime.of(2, 0));
        assertThat(detail.endTime()).isEqualTo(LocalTime.of(3, 0));
    }

    @Test
    void buildConstraintViolations_00_00AnchoredDesk_businessAndCalendarDatesAreEqual() {
        ViolationDetail detail = singleRelocatedViolation(LocalTime.MIDNIGHT,
                DAY, DAY, LocalTime.of(9, 0), LocalTime.of(10, 0));

        assertThat(detail.businessDate()).isEqualTo(detail.calendarDate());
        assertThat(detail.businessDate()).isEqualTo(DAY);
    }

    @Test
    void buildConstraintViolations_anchoredDesk_labelNowDisclosesTheBusinessDateWhenDatesDiffer_staysPlainWhenTheyAgree() {
        // Plan 21-11 (OVNT-07/D-14) inverts this control. It used to pin the label as
        // byte-identical to today's shape even though business date and calendar date diverged
        // -- that premise held only while a consumer still split the label as data. Both former
        // parsers (ScheduleExportService, ScheduleResults.tsx) moved onto the structured fields
        // in 21-03/21-10, so the text can now safely disclose the divergence. This test is NOT
        // deleted and replaced -- it is inverted, so it keeps protecting the property it was
        // written for: what the label's text actually is.
        ViolationDetail differing = singleRelocatedViolation(LocalTime.of(21, 0),
                DAY.plusDays(1), DAY, LocalTime.of(2, 0), LocalTime.of(3, 0));
        assertThat(differing.timeslotLabel())
                .isEqualTo(DAY.plusDays(1) + " 02:00-03:00 (business day: " + DAY + ")");

        // The byte-identical half of the control survives unchanged: every midnight-anchored
        // desk's label, and every same-day slot on an anchored desk, is exactly what it was
        // before this plan.
        ViolationDetail same = singleRelocatedViolation(LocalTime.MIDNIGHT,
                DAY, DAY, LocalTime.of(9, 0), LocalTime.of(10, 0));
        assertThat(same.timeslotLabel()).isEqualTo(DAY + " 09:00-10:00");
    }

    // ------------------------------------------------------------------
    //  Plan 21-11 Task 1 (OVNT-07, D-14) -- the label itself now discloses the calendar span,
    //  built by one shared private helper (timeslotLabel) called from both violation paths.
    //  Confirmed before this plan changed the text: neither ScheduleExportService nor
    //  ScheduleResults.tsx recovers a date or a time from the label by splitting the string --
    //  both read ViolationDetail's structured fields (21-03, 21-10).
    // ------------------------------------------------------------------

    @Test
    void timeslotLabel_differingBusinessAndCalendarDates_disclosesBothAsALabelledSuffix() {
        // A 20:00-anchored desk, a 01:30-02:30 slot: business date DAY, calendar date DAY+1.
        ViolationDetail detail = singleRelocatedViolation(LocalTime.of(20, 0),
                DAY.plusDays(1), DAY, LocalTime.of(1, 30), LocalTime.of(2, 30));

        assertThat(detail.timeslotLabel())
                .as("the calendar date stays leading -- that's what finds the row on a calendar --"
                        + " and the business date is disclosed as an explicit, labelled suffix so"
                        + " the two can never be confused for each other")
                .isEqualTo(DAY.plusDays(1) + " 01:30-02:30 (business day: " + DAY + ")");
    }

    @Test
    void timeslotLabel_equalBusinessAndCalendarDates_isByteIdenticalToToday() {
        // A 00:00-anchored desk: business date and calendar date are the same LocalDate for
        // every slot. The label must carry no disclosure suffix at all. 08:00 sits before the
        // NAMED_ROW envelope (11:00-20:00) so the fixture's single relocated seat still reports
        // exactly one violation.
        ViolationDetail detail = singleRelocatedViolation(LocalTime.MIDNIGHT,
                DAY, DAY, LocalTime.of(8, 0), LocalTime.of(9, 0));

        assertThat(detail.timeslotLabel()).isEqualTo(DAY + " 08:00-09:00");
    }

    @Test
    void buildConstraintViolations_liveViolation_carriesTheDisclosingLabel() {
        ViolationDetail detail = singleLiveSpecializationMismatchViolation(LocalTime.of(21, 0),
                DAY.plusDays(1), DAY, LocalTime.of(2, 0), LocalTime.of(3, 0));

        assertThat(detail.timeslotLabel())
                .isEqualTo(DAY.plusDays(1) + " 02:00-03:00 (business day: " + DAY + ")");
    }

    @Test
    void unassignedSeatDescription_isBuiltFromTheSameTimeslotLabelVariable() throws java.io.IOException {
        // The "Unassigned assignment" constraint is a groupBy/join/join/filter/penalizeConfigurable
        // aggregate (ScheduleConstraintProvider.unassignedAssignment) whose ConstraintMatch
        // justification is the (Timeslot key, summed int, TimeslotDemandConfig, ScheduleConfig)
        // tuple -- never an individual AgentAssignment. Confirmed empirically against the real
        // solver (a hand-built unassigned seat, and a two-seat group with one assigned and one
        // unassigned, run through solutionManager.explain()): specName and timeslotLabel are
        // always null for this constraint's violations, so the
        // "No agent assigned for " + specName + " at " + timeslotLabel branch can never execute
        // in the live path today. This is a genuine, pre-existing gap (not introduced by this
        // plan's label-text change) and is out of scope to fix here -- it would mean restructuring
        // that constraint's stream shape in ScheduleConstraintProvider, a solver file this plan's
        // threat model does not touch. Recorded in this plan's SUMMARY. What this test proves
        // instead is the source-level invariant that IS true: wherever that branch does execute,
        // it is built from the exact same timeslotLabel variable this plan's shared helper
        // populates, so it can never disagree with it.
        String source = java.nio.file.Files.readString(
                java.nio.file.Path.of("src/main/java/com/wfm/service/ScheduleOutputService.java"));
        assertThat(source)
                .contains("description = \"No agent assigned for \" + specName + \" at \" + timeslotLabel;");
    }

    @Test
    void buildConstraintViolations_liveAndAcceptedPaths_produceIdenticalLabelsForTheSameTimeslot() {
        LocalTime dayStart = LocalTime.of(21, 0);
        LocalDate calendarDate = DAY.plusDays(1);
        LocalDate businessDate = DAY;
        LocalTime start = LocalTime.of(2, 0);
        LocalTime end = LocalTime.of(3, 0);

        ViolationDetail accepted = singleRelocatedViolation(dayStart, calendarDate, businessDate, start, end);
        ViolationDetail live = singleLiveSpecializationMismatchViolation(dayStart, calendarDate, businessDate, start, end);

        assertThat(live.timeslotLabel())
                .as("two violation-reporting paths, one shared label helper -- they must never"
                        + " disagree about the same slot's label")
                .isEqualTo(accepted.timeslotLabel());
    }

    // ------------------------------------------------------------------
    //  #14 (WINDOWS.md) -- the "Unassigned assignment" groupBy aggregate indicts the Timeslot
    //  group key directly, never an individual AgentAssignment (see
    //  unassignedSeatDescription_isBuiltFromTheSameTimeslotLabelVariable above for that proof).
    //  Before this fix that meant every one of its ViolationDetail rows carried null
    //  businessDate/calendarDate/startTime/endTime, so ScheduleResults.tsx's unfilledSlots map
    //  (keyed businessDate|startTime) could never match a real slot and the operator-facing
    //  unfilled-seat marker could never render in either grid branch.
    // ------------------------------------------------------------------

    @Test
    void buildConstraintViolations_unassignedAssignment_violationNowCarriesTimeslotAttribution() {
        // Drives the REAL live path end to end (solutionManager.explain against the real
        // solverConfig.xml) rather than a narrower seam: the bug is about which object
        // ScheduleConstraintProvider.unassignedAssignment's ConstraintMatch indicts, and only a
        // genuine explain() call against the real constraint stream can prove that one way or
        // the other. A seam-level test that just called a private extraction method directly
        // would have to assume the match shape rather than prove it.
        Timeslot ts = timeslot(LocalTime.of(10, 0));

        // One AgentAssignment entity, left unassigned (agent == null) -- forEachIncludingUnassigned
        // requires the entity to exist even when its planning variable is null.
        AgentAssignment unassigned = new AgentAssignment();
        unassigned.setId(UUID.randomUUID());
        unassigned.setTimeslot(ts);

        // Demand 2, assigned 0: minRequired = 2 * underallocationHardLimitPct(70) / 100 = 1,
        // and 0 < 1, so unassignedAssignment's hard-limit filter fires exactly once.
        Schedule schedule = new Schedule();
        schedule.setIncrementMinutes(INCREMENT);
        schedule.setDayStart(LocalTime.MIDNIGHT);
        schedule.setAssignments(new ArrayList<>(List.of(unassigned)));
        schedule.setTimeslotDemandConfigs(
                new ArrayList<>(List.of(new com.wfm.model.TimeslotDemandConfig(ts, 2))));
        schedule.setConstraintWeights(new ConstraintWeights());

        List<ConstraintViolationEntry> violations = service.buildConstraintViolations(schedule, false);
        ConstraintViolationEntry entry = violations.stream()
                .filter(e -> "Unassigned assignment".equals(e.constraintName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Unassigned assignment constraint did not fire; violations=" + violations));
        assertThat(entry.violations()).hasSize(1);
        ViolationDetail detail = entry.violations().get(0);

        assertThat(detail.timeslotId()).isEqualTo(ts.getId());
        assertThat(detail.businessDate()).isEqualTo(DAY);
        assertThat(detail.calendarDate()).isEqualTo(DAY);
        assertThat(detail.startTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(detail.endTime()).isEqualTo(LocalTime.of(10, 0).plusMinutes(INCREMENT));

        // No AgentAssignment is indicted for this aggregate constraint and TimeslotDemandConfig
        // carries no specialization field -- agent identity and specName are correctly absent,
        // never fabricated. Description choice (a): falls through to the generic form.
        assertThat(detail.agentId()).isNull();
        assertThat(detail.agentName()).isNull();
        assertThat(entry.violations().get(0).description()).isEqualTo("Unassigned assignment violation");
    }

    @Test
    void buildConstraintViolations_liveAgentAssignmentIndictedConstraint_attributionUnaffectedByNewTimeslotBranch() {
        // No-regression requirement for #14's fix: a constraint that DOES indict an individual
        // AgentAssignment (specializationMatch forEach()s AgentAssignment directly, unlike the
        // groupBy aggregate above) must come out byte-identical to before the fix -- agentId,
        // agentName, and all five structured timeslot fields -- proving the new Timeslot branch
        // (guarded on timeslotId == null) never clobbers values the AgentAssignment branch
        // already set.
        Agent probe = agent("Probe"); // no primary/secondary specialization -- guarantees a mismatch
        Specialization spec = specialization("Chat");
        Timeslot ts = timeslot(LocalTime.of(14, 0));

        AgentAssignment held = new AgentAssignment();
        held.setId(UUID.randomUUID());
        held.setAgent(probe);
        held.setTimeslot(ts);
        held.setRequiredSpecialization(spec);

        Schedule schedule = new Schedule();
        schedule.setIncrementMinutes(INCREMENT);
        schedule.setDayStart(LocalTime.MIDNIGHT);
        schedule.setAssignments(new ArrayList<>(List.of(held)));
        schedule.setConstraintWeights(new ConstraintWeights());

        List<ConstraintViolationEntry> violations = service.buildConstraintViolations(schedule, false);
        ConstraintViolationEntry entry = violations.stream()
                .filter(e -> "Specialization match".equals(e.constraintName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Specialization match did not fire; violations=" + violations));
        assertThat(entry.violations()).hasSize(1);
        ViolationDetail detail = entry.violations().get(0);

        assertThat(detail.agentId()).isEqualTo(probe.getId());
        assertThat(detail.agentName()).isEqualTo("Probe");
        assertThat(detail.timeslotId()).isEqualTo(ts.getId());
        assertThat(detail.businessDate()).isEqualTo(DAY);
        assertThat(detail.calendarDate()).isEqualTo(DAY);
        assertThat(detail.startTime()).isEqualTo(LocalTime.of(14, 0));
        assertThat(detail.endTime()).isEqualTo(LocalTime.of(14, 0).plusMinutes(INCREMENT));
    }

    /**
     * A single held seat whose agent has no primary or secondary specialization at all, run
     * through the LIVE path's real {@code solutionManager.explain()} -- deterministically fires
     * {@code ScheduleConstraintProvider.specializationMatch} ("Specialization match"), which
     * {@code forEach}s {@link AgentAssignment} directly rather than through a {@code groupBy}
     * aggregate, so its {@code ConstraintMatch} DOES indict the individual {@link AgentAssignment}
     * -- unlike "Unassigned assignment" (see {@code unassignedSeatDescription_...} above).
     */
    private ViolationDetail singleLiveSpecializationMismatchViolation(LocalTime dayStart, LocalDate calendarDate,
            LocalDate businessDate, LocalTime start, LocalTime end) {
        Specialization spec = specialization("Chat");

        Timeslot ts = new Timeslot();
        ts.setId(UUID.randomUUID());
        ts.setDate(calendarDate);
        ts.setBusinessDate(businessDate);
        ts.setStartTime(start);
        ts.setEndTime(end);

        AgentAssignment held = new AgentAssignment();
        held.setId(UUID.randomUUID());
        held.setAgent(agent("Probe")); // no primary/secondary specialization -- guarantees a mismatch
        held.setTimeslot(ts);
        held.setRequiredSpecialization(spec);

        Schedule schedule = new Schedule();
        schedule.setIncrementMinutes(INCREMENT);
        schedule.setDayStart(dayStart);
        schedule.setAssignments(new ArrayList<>(List.of(held)));
        schedule.setConstraintWeights(new ConstraintWeights());

        List<ConstraintViolationEntry> violations = service.buildConstraintViolations(schedule, false);
        ConstraintViolationEntry entry = violations.stream()
                .filter(e -> "Specialization match".equals(e.constraintName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Specialization match constraint did not fire; violations=" + violations));
        assertThat(entry.violations()).hasSize(1);
        return entry.violations().get(0);
    }

    // ------------------------------------------------------------------
    //  SOLV-07 (plan 20-06) — coverage reporting buckets a timeslot under the business day it
    //  belongs to, not the calendar day. buildStaffingSummary's predicted/actual maps are the
    //  coverage report (spec §8.1); this proves the six migrated key positions (:62, :71, :164,
    //  :173, :323, :739) resolve the same business date a 21:00-anchored desk's solver already
    //  resolves, and that a 00:00-anchored desk — every live desk today — is provably unchanged.
    // ------------------------------------------------------------------

    private static final LocalTime NIGHT_ANCHOR = LocalTime.of(21, 0);

    @Test
    void buildStaffingSummary_21_00AnchoredDesk_bucketsTheCrossMidnightBusinessDayOnce() {
        Specialization spec = specialization("Chat");
        Agent agent = agent("Night");

        // Business day DAY spans two calendar dates at a 21:00 anchor: 22:00 on calendar DAY (at
        // or after the anchor -> business date DAY) and 02:00 on calendar DAY+1 (before the
        // anchor -> business date rolls back to DAY). Both derived through DayWindow.businessDateOf
        // -- the shared day-window derivation -- rather than hand-computed.
        Timeslot late = anchoredTimeslot(DAY, LocalTime.of(22, 0), NIGHT_ANCHOR);
        Timeslot early = anchoredTimeslot(DAY.plusDays(1), LocalTime.of(2, 0), NIGHT_ANCHOR);

        StaffingRequirement srLate = staffingRequirement(late, spec, 1);
        StaffingRequirement srEarly = staffingRequirement(early, spec, 1);
        AgentAssignment aLate = assignment(agent, late, spec);
        AgentAssignment aEarly = assignment(agent, early, spec);

        Schedule schedule = new Schedule();
        schedule.setIncrementMinutes(INCREMENT);
        schedule.setDayStart(NIGHT_ANCHOR);
        schedule.setStaffingRequirements(new ArrayList<>(List.of(srLate, srEarly)));
        schedule.setAssignments(new ArrayList<>(List.of(aLate, aEarly)));

        List<StaffingSummaryEntry> entries = service.buildStaffingSummary(schedule);

        List<LocalDate> dates = entries.stream()
                .map(StaffingSummaryEntry::date)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        assertThat(dates)
                .as("both timeslots belong to business day DAY and must key the report once, "
                        + "not split across DAY and DAY+1")
                .containsExactly(DAY);
    }

    @Test
    void buildStaffingSummary_00_00AnchoredDesk_figuresUnchanged() {
        Specialization spec = specialization("Chat");
        Agent agent = agent("Day");

        // At a 00:00 anchor business date equals calendar date for every time of day -- two
        // ordinary calendar days must still report as two distinct coverage rows, unchanged from
        // today's behaviour.
        Timeslot day1 = anchoredTimeslot(DAY, LocalTime.of(8, 0), LocalTime.MIDNIGHT);
        Timeslot day2 = anchoredTimeslot(DAY.plusDays(1), LocalTime.of(8, 0), LocalTime.MIDNIGHT);

        StaffingRequirement sr1 = staffingRequirement(day1, spec, 1);
        StaffingRequirement sr2 = staffingRequirement(day2, spec, 1);
        AgentAssignment a1 = assignment(agent, day1, spec);
        AgentAssignment a2 = assignment(agent, day2, spec);

        Schedule schedule = new Schedule();
        schedule.setIncrementMinutes(INCREMENT);
        schedule.setDayStart(LocalTime.MIDNIGHT);
        schedule.setStaffingRequirements(new ArrayList<>(List.of(sr1, sr2)));
        schedule.setAssignments(new ArrayList<>(List.of(a1, a2)));

        List<StaffingSummaryEntry> entries = service.buildStaffingSummary(schedule);

        List<LocalDate> dates = entries.stream()
                .map(StaffingSummaryEntry::date)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        assertThat(dates).containsExactly(DAY, DAY.plusDays(1));
    }

    /**
     * A {@link Timeslot} whose calendar {@code date} and {@code businessDate} are derived
     * independently through {@link DayWindow#businessDateOf} (the shared day-window derivation),
     * never hand-computed -- so this fixture cannot silently agree with a bug in the production
     * accessor it is exercising.
     */
    private Timeslot anchoredTimeslot(LocalDate calendarDate, LocalTime start, LocalTime anchor) {
        Timeslot ts = new Timeslot();
        ts.setId(UUID.randomUUID());
        ts.setDate(calendarDate);
        ts.setBusinessDate(DayWindow.businessDateOf(anchor, calendarDate, start));
        ts.setStartTime(start);
        ts.setEndTime(start.plusMinutes(INCREMENT));
        return ts;
    }

    private StaffingRequirement staffingRequirement(Timeslot ts, Specialization spec, int requiredFTEs) {
        StaffingRequirement sr = new StaffingRequirement();
        sr.setId(UUID.randomUUID());
        sr.setTimeslot(ts);
        sr.setSpecialization(spec);
        sr.setRequiredFTEs(requiredFTEs);
        return sr;
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
                // SOLV-07 (plan 20-06): see the identical note in timeslot(LocalTime) below --
                // this fixture is implicitly 00:00-anchored, so businessDate == date.
                ts.setBusinessDate(day.date());
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
        // BDAY-04 (plan 19-05): buildAgentSchedule/buildConstraintViolations now bind a DayWindow
        // from this Schedule's own dayStart — an explicit midnight anchor here, not a production
        // fallback, mirroring every other migrated test caller's convention.
        schedule.setDayStart(LocalTime.MIDNIGHT);
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
        // BDAY-04 (plan 19-05): buildAgentSchedule/buildConstraintViolations now bind a DayWindow
        // from this Schedule's own dayStart — an explicit midnight anchor here, not a production
        // fallback, mirroring every other migrated test caller's convention.
        schedule.setDayStart(LocalTime.MIDNIGHT);
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
        // BDAY-04 (plan 19-05): buildAgentSchedule/buildConstraintViolations now bind a DayWindow
        // from this Schedule's own dayStart — an explicit midnight anchor here, not a production
        // fallback, mirroring every other migrated test caller's convention.
        schedule.setDayStart(LocalTime.MIDNIGHT);
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
        // SOLV-07 (plan 20-06): ScheduleOutputService's grouping keys now read getBusinessDate(),
        // not getDate() -- every hand-built Timeslot this shared helper produces must carry one or
        // agent-day grouping (buildAgentSchedule/buildPreferenceReport/buildAcceptedConstraintViolations)
        // silently keys itself on null instead of DAY. This fixture is implicitly 00:00-anchored
        // (every test in this class binds schedule.setDayStart(LocalTime.MIDNIGHT) or leaves it
        // unset), so businessDate == date here is correct and behaviourally inert -- the same
        // pattern plan 20-05 applied to its 18 fixture fixes.
        ts.setBusinessDate(DAY);
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
