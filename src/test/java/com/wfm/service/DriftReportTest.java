package com.wfm.service;

import ai.timefold.solver.core.api.solver.SolverFactory;
import com.wfm.config.TenantContext;
import com.wfm.dto.ScheduleDetailResponse;
import com.wfm.dto.ScheduleDetailResponse.DriftReport;
import com.wfm.dto.ScheduleDetailResponse.DriftReportEntry;
import com.wfm.dto.ScheduleDetailResponse.DriftStatus;
import com.wfm.dto.ScheduleDetailResponse.DriftSummary;
import com.wfm.dto.ScheduleDetailResponse.ShiftPopularityEntry;
import com.wfm.model.Agent;
import com.wfm.model.AgentShiftAssignment;
import com.wfm.model.AgentUsualShift;
import com.wfm.model.ConstraintWeights;
import com.wfm.model.Schedule;
import com.wfm.model.ScheduleStatus;
import com.wfm.model.ShiftBandPair;
import com.wfm.model.ShiftTemplate;
import com.wfm.repository.AcceptedScheduleDateRepository;
import com.wfm.repository.AgentAssignmentRepository;
import com.wfm.repository.AgentDayOffRepository;
import com.wfm.repository.AgentPreferenceRepository;
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

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Phase 17 plan 17-01, Task 1 (tracer) — {@code ScheduleOutputService.buildDriftReport}
 * (DRFT-01/DRFT-02), including the load-bearing DRFT-03 agreement assertion: the
 * {@code usualShiftConsistency} constraint's penalty magnitude and this report's
 * {@code deltaMinutes} for the same agent-day are both derived from
 * {@code ShiftBandPair.startDeviationMinutes}.
 *
 * <p>Task 2 (TDD) extends this file with the full three-state matrix, the tolerance-band
 * boundary, the non-working-day exclusion, the summary invariant and the sort-order contract.
 */
class DriftReportTest {

    private static final SolverFactory<Schedule> SOLVER_FACTORY =
            SolverFactory.createFromXmlResource("solverConfig.xml");

    private static final long TENANT_ID = 1L;
    private static final UUID DESK_ID = UUID.randomUUID();
    private static final LocalDate MONDAY = LocalDate.of(2026, 9, 7); // a real Monday

    private final ShiftTemplateRepository shiftTemplateRepository = mock(ShiftTemplateRepository.class);
    private final AgentUsualShiftRepository agentUsualShiftRepository = mock(AgentUsualShiftRepository.class);
    private final ScheduleOutputService service = new ScheduleOutputService(SOLVER_FACTORY,
            new UsualShiftResolutionService(shiftTemplateRepository), agentUsualShiftRepository);

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    private static Agent agent(String name) {
        Agent a = new Agent();
        a.setId(UUID.randomUUID());
        a.setName(name);
        return a;
    }

    private static ShiftTemplate template(String name, LocalTime start, LocalTime end) {
        ShiftTemplate t = new ShiftTemplate();
        t.setId(UUID.randomUUID());
        t.setTenantId(TENANT_ID);
        t.setDeskId(DESK_ID);
        t.setName(name);
        t.setStartTime(start);
        t.setEndTime(end);
        t.setValidWeekdays(EnumSet.allOf(DayOfWeek.class));
        t.setEffectiveFrom(LocalDate.of(2026, 1, 1));
        return t;
    }

    private static AgentUsualShift usualShift(Agent agent, DayOfWeek dayOfWeek, ShiftTemplate template) {
        AgentUsualShift u = new AgentUsualShift();
        u.setId(UUID.randomUUID());
        u.setTenantId(TENANT_ID);
        u.setAgent(agent);
        u.setDayOfWeek(dayOfWeek);
        u.setShiftTemplate(template);
        return u;
    }

    private static AgentShiftAssignment shiftRow(Agent agent, LocalDate date, ShiftTemplate assignedTemplate) {
        AgentShiftAssignment sa = new AgentShiftAssignment();
        sa.setId(UUID.randomUUID());
        sa.setAgent(agent);
        sa.setDate(date);
        sa.setShiftBandPair(new ShiftBandPair(assignedTemplate, null));
        return sa;
    }

    private Schedule schedule(int consistencyToleranceMinutes, List<AgentShiftAssignment> shiftAssignments) {
        Schedule schedule = new Schedule();
        schedule.setId(UUID.randomUUID());
        schedule.setTenantId(TENANT_ID);
        schedule.setDeskId(DESK_ID);
        ConstraintWeights weights = new ConstraintWeights();
        weights.setConsistencyToleranceMinutes(consistencyToleranceMinutes);
        schedule.setConstraintWeights(weights);
        schedule.setShiftAssignments(new ArrayList<>(shiftAssignments));
        return schedule;
    }

    // ------------------------------------------------------------------
    //  DRIFTED — status, usualStartTime, actualStartTime, signed deltaMinutes
    // ------------------------------------------------------------------

    @Test
    void driftedEntry_carriesUsualStartActualStartAndSignedDelta() {
        Agent ana = agent("Ana");
        ShiftTemplate usualTemplate = template("Early", LocalTime.of(8, 0), LocalTime.of(17, 0));
        ShiftTemplate assignedTemplate = template("Late", LocalTime.of(12, 0), LocalTime.of(21, 0));

        when(agentUsualShiftRepository.findByTenantIdAndDeskId(TENANT_ID, DESK_ID))
                .thenReturn(List.of(usualShift(ana, MONDAY.getDayOfWeek(), usualTemplate)));
        when(shiftTemplateRepository.findByTenantIdAndDeskIdAndName(TENANT_ID, DESK_ID, "Early"))
                .thenReturn(List.of(usualTemplate));

        Schedule schedule = schedule(60, List.of(shiftRow(ana, MONDAY, assignedTemplate)));

        DriftReport report = service.buildDriftReport(schedule);

        assertThat(report.entries()).hasSize(1);
        DriftReportEntry entry = report.entries().get(0);
        assertThat(entry.status()).isEqualTo(DriftStatus.DRIFTED);
        assertThat(entry.usualStartTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(entry.actualStartTime()).isEqualTo(LocalTime.of(12, 0));
        // Assigned (12:00) is LATER than usual (08:00) -> positive sign.
        assertThat(entry.deltaMinutes()).isEqualTo(240);
    }

    // ------------------------------------------------------------------
    //  NO_USUAL_SHIFT — usualStartTime null, deltaMinutes null, actualStartTime still populated
    // ------------------------------------------------------------------

    @Test
    void noUsualShiftEntry_carriesNullUsualStartAndNullDeltaButRealActualStart() {
        Agent ben = agent("Ben");
        ShiftTemplate assignedTemplate = template("Late", LocalTime.of(12, 0), LocalTime.of(21, 0));

        when(agentUsualShiftRepository.findByTenantIdAndDeskId(TENANT_ID, DESK_ID))
                .thenReturn(List.of());

        Schedule schedule = schedule(60, List.of(shiftRow(ben, MONDAY, assignedTemplate)));

        DriftReport report = service.buildDriftReport(schedule);

        assertThat(report.entries()).hasSize(1);
        DriftReportEntry entry = report.entries().get(0);
        assertThat(entry.status()).isEqualTo(DriftStatus.NO_USUAL_SHIFT);
        assertThat(entry.usualStartTime()).isNull();
        assertThat(entry.deltaMinutes()).isNull();
        assertThat(entry.actualStartTime()).isEqualTo(LocalTime.of(12, 0));
    }

    // ------------------------------------------------------------------
    //  DRFT-03 — the constraint's penalty and the report's delta agree on the same schedule,
    //  because both are derived from ShiftBandPair.startDeviationMinutes.
    // ------------------------------------------------------------------

    @Test
    void driftedDay_reportDeltaMagnitudeMatchesTheOneSharedDistanceCalculation() {
        // Same usual/assigned start pair, tolerance band and increment as
        // UsualShiftConsistencyConstraintTest#drifted_penalisedByExpectedIncrementCount — that
        // sibling test proves the CONSTRAINT side of DRFT-03 (penalty 6, via the real
        // ConstraintVerifier, package-private and therefore not directly callable from this
        // package); this test proves the REPORT side. Both derive their number from
        // ShiftBandPair.startDeviationMinutes and no other calculation — that shared static
        // method, not a re-run of the solver, is what DRFT-03 requires to agree.
        Agent ana = agent("Ana");
        ShiftTemplate usualTemplate = template("Early", LocalTime.of(8, 0), LocalTime.of(17, 0));
        ShiftTemplate assignedTemplate = template("Late", LocalTime.of(12, 0), LocalTime.of(21, 0));
        int toleranceMinutes = 60;
        int incrementMinutes = 30;

        when(agentUsualShiftRepository.findByTenantIdAndDeskId(TENANT_ID, DESK_ID))
                .thenReturn(List.of(usualShift(ana, MONDAY.getDayOfWeek(), usualTemplate)));
        when(shiftTemplateRepository.findByTenantIdAndDeskIdAndName(TENANT_ID, DESK_ID, "Early"))
                .thenReturn(List.of(usualTemplate));

        Schedule schedule = schedule(toleranceMinutes, List.of(shiftRow(ana, MONDAY, assignedTemplate)));
        DriftReport report = service.buildDriftReport(schedule);
        DriftReportEntry entry = report.entries().get(0);

        int sharedMagnitude = ShiftBandPair.startDeviationMinutes(
                assignedTemplate.getStartTime(), usualTemplate.getStartTime());
        int expectedPenalty = (int) Math.ceil((double) (sharedMagnitude - toleranceMinutes) / incrementMinutes);

        assertThat(Math.abs(entry.deltaMinutes())).isEqualTo(sharedMagnitude);
        // The sibling constraint test's hardcoded expectation (6) for this identical fixture —
        // asserted here too so a change to either side that breaks agreement fails visibly in
        // both files, not just one.
        assertThat(expectedPenalty).isEqualTo(6);
    }

    // ==================================================================
    //  Task 2 (TDD) — the full three-state matrix, band boundary, non-working-day exclusion,
    //  summary invariant and sort order.
    // ==================================================================

    // ------------------------------------------------------------------
    //  HONOURED — deviation within the band
    // ------------------------------------------------------------------

    @Test
    void honouredEntry_deviationWithinBand() {
        Agent ana = agent("Ana");
        ShiftTemplate usualTemplate = template("Early", LocalTime.of(8, 0), LocalTime.of(17, 0));
        ShiftTemplate assignedTemplate = template("EarlyPlus", LocalTime.of(8, 30), LocalTime.of(17, 30));

        when(agentUsualShiftRepository.findByTenantIdAndDeskId(TENANT_ID, DESK_ID))
                .thenReturn(List.of(usualShift(ana, MONDAY.getDayOfWeek(), usualTemplate)));
        when(shiftTemplateRepository.findByTenantIdAndDeskIdAndName(TENANT_ID, DESK_ID, "Early"))
                .thenReturn(List.of(usualTemplate));

        Schedule schedule = schedule(60, List.of(shiftRow(ana, MONDAY, assignedTemplate)));
        DriftReport report = service.buildDriftReport(schedule);

        assertThat(report.entries()).hasSize(1);
        DriftReportEntry entry = report.entries().get(0);
        assertThat(entry.status()).isEqualTo(DriftStatus.HONOURED);
        assertThat(entry.deltaMinutes()).isNull();
    }

    // ------------------------------------------------------------------
    //  Tolerance-band boundary — exactly at the band is HONOURED, one minute beyond is DRIFTED
    // ------------------------------------------------------------------

    @Test
    void deviationExactlyAtBand_isHonoured() {
        Agent ana = agent("Ana");
        ShiftTemplate usualTemplate = template("Early", LocalTime.of(8, 0), LocalTime.of(17, 0));
        // Exactly 60 minutes deviation, band 60 -> genuine dead zone (D-05), HONOURED.
        ShiftTemplate assignedTemplate = template("EarlyOneHourLater", LocalTime.of(9, 0), LocalTime.of(18, 0));

        when(agentUsualShiftRepository.findByTenantIdAndDeskId(TENANT_ID, DESK_ID))
                .thenReturn(List.of(usualShift(ana, MONDAY.getDayOfWeek(), usualTemplate)));
        when(shiftTemplateRepository.findByTenantIdAndDeskIdAndName(TENANT_ID, DESK_ID, "Early"))
                .thenReturn(List.of(usualTemplate));

        Schedule schedule = schedule(60, List.of(shiftRow(ana, MONDAY, assignedTemplate)));
        DriftReportEntry entry = service.buildDriftReport(schedule).entries().get(0);

        assertThat(entry.status()).isEqualTo(DriftStatus.HONOURED);
    }

    @Test
    void deviationOneMinuteBeyondBand_isDrifted() {
        Agent ana = agent("Ana");
        ShiftTemplate usualTemplate = template("Early", LocalTime.of(8, 0), LocalTime.of(17, 0));
        // 61 minutes deviation, band 60 -> the first penalised state (CONS-02).
        ShiftTemplate assignedTemplate = template("EarlyJustPast", LocalTime.of(9, 1), LocalTime.of(18, 1));

        when(agentUsualShiftRepository.findByTenantIdAndDeskId(TENANT_ID, DESK_ID))
                .thenReturn(List.of(usualShift(ana, MONDAY.getDayOfWeek(), usualTemplate)));
        when(shiftTemplateRepository.findByTenantIdAndDeskIdAndName(TENANT_ID, DESK_ID, "Early"))
                .thenReturn(List.of(usualTemplate));

        Schedule schedule = schedule(60, List.of(shiftRow(ana, MONDAY, assignedTemplate)));
        DriftReportEntry entry = service.buildDriftReport(schedule).entries().get(0);

        assertThat(entry.status()).isEqualTo(DriftStatus.DRIFTED);
    }

    // ------------------------------------------------------------------
    //  "Stored row resolves to no template for this date" is also NO_USUAL_SHIFT (Phase 16
    //  D-01/D-02: no era in effect is identical to unset)
    // ------------------------------------------------------------------

    @Test
    void storedRowWithNoEffectiveEraForThisDate_isNoUsualShift() {
        Agent ana = agent("Ana");
        // Stored row points at a template named "Early", but NO era of that name is returned by
        // the repository lookup for this date -- "no era in effect" (Phase 16 D-01/D-02).
        ShiftTemplate storedPointer = template("Early", LocalTime.of(8, 0), LocalTime.of(17, 0));
        ShiftTemplate assignedTemplate = template("Late", LocalTime.of(12, 0), LocalTime.of(21, 0));

        when(agentUsualShiftRepository.findByTenantIdAndDeskId(TENANT_ID, DESK_ID))
                .thenReturn(List.of(usualShift(ana, MONDAY.getDayOfWeek(), storedPointer)));
        when(shiftTemplateRepository.findByTenantIdAndDeskIdAndName(TENANT_ID, DESK_ID, "Early"))
                .thenReturn(List.of()); // no era effective on MONDAY

        Schedule schedule = schedule(60, List.of(shiftRow(ana, MONDAY, assignedTemplate)));
        DriftReportEntry entry = service.buildDriftReport(schedule).entries().get(0);

        assertThat(entry.status()).isEqualTo(DriftStatus.NO_USUAL_SHIFT);
        assertThat(entry.usualStartTime()).isNull();
        assertThat(entry.deltaMinutes()).isNull();
    }

    // ------------------------------------------------------------------
    //  Non-working day: a stored usual shift with no AgentShiftAssignment produces no entry
    // ------------------------------------------------------------------

    @Test
    void nonWorkingDay_producesNoEntryEvenWithAStoredUsualShift() {
        Agent ana = agent("Ana");
        ShiftTemplate usualTemplate = template("Early", LocalTime.of(8, 0), LocalTime.of(17, 0));

        // Ana has a stored usual shift for Monday, but no AgentShiftAssignment row exists for
        // her on this schedule at all -- SolverService never creates one for a non-working day
        // (0 contracted hours / MANDATORY / PTO), and this report only ever walks
        // schedule.getShiftAssignments(), never a separate "everyone with a usual shift" list.
        when(agentUsualShiftRepository.findByTenantIdAndDeskId(TENANT_ID, DESK_ID))
                .thenReturn(List.of(usualShift(ana, MONDAY.getDayOfWeek(), usualTemplate)));

        Schedule schedule = schedule(60, List.of());
        DriftReport report = service.buildDriftReport(schedule);

        assertThat(report.entries()).isEmpty();
        assertThat(report.summary().workingAgentDays()).isZero();
    }

    // ------------------------------------------------------------------
    //  Summary invariant: workingAgentDays == noUsualShiftCount + honouredCount + driftedCount
    //  on a fixture containing all three states on the same date.
    // ------------------------------------------------------------------

    @Test
    void summaryInvariant_holdsOnAMixedFixtureWithAllThreeStatesOnTheSameDate() {
        Agent ana = agent("Ana");     // will be DRIFTED
        Agent ben = agent("Ben");     // will be NO_USUAL_SHIFT
        Agent cara = agent("Cara");   // will be HONOURED

        ShiftTemplate anaUsual = template("Early", LocalTime.of(8, 0), LocalTime.of(17, 0));
        ShiftTemplate anaAssigned = template("Late", LocalTime.of(12, 0), LocalTime.of(21, 0));
        ShiftTemplate benAssigned = template("Mid", LocalTime.of(10, 0), LocalTime.of(19, 0));
        ShiftTemplate caraUsual = template("Standard", LocalTime.of(9, 0), LocalTime.of(18, 0));
        ShiftTemplate caraAssigned = template("StandardPlus15", LocalTime.of(9, 15), LocalTime.of(18, 15));

        when(agentUsualShiftRepository.findByTenantIdAndDeskId(TENANT_ID, DESK_ID)).thenReturn(List.of(
                usualShift(ana, MONDAY.getDayOfWeek(), anaUsual),
                usualShift(cara, MONDAY.getDayOfWeek(), caraUsual)));
        when(shiftTemplateRepository.findByTenantIdAndDeskIdAndName(TENANT_ID, DESK_ID, "Early"))
                .thenReturn(List.of(anaUsual));
        when(shiftTemplateRepository.findByTenantIdAndDeskIdAndName(TENANT_ID, DESK_ID, "Standard"))
                .thenReturn(List.of(caraUsual));

        Schedule schedule = schedule(60, List.of(
                shiftRow(ana, MONDAY, anaAssigned),
                shiftRow(ben, MONDAY, benAssigned),
                shiftRow(cara, MONDAY, caraAssigned)));

        DriftReport report = service.buildDriftReport(schedule);

        assertThat(report.entries()).hasSize(3);
        assertThat(report.summary().workingAgentDays())
                .isEqualTo(report.summary().noUsualShiftCount()
                        + report.summary().honouredCount()
                        + report.summary().driftedCount());
        assertThat(report.summary().noUsualShiftCount()).isEqualTo(1);
        assertThat(report.summary().honouredCount()).isEqualTo(1);
        assertThat(report.summary().driftedCount()).isEqualTo(1);
    }

    // ------------------------------------------------------------------
    //  Sort order: date ascending, then agent name ascending (17-UI-SPEC.md Component
    //  Specifications §1) -- a deliberate divergence from buildPreferenceReport's agent-then-date
    //  order.
    // ------------------------------------------------------------------

    @Test
    void entries_sortDateAscendingThenAgentNameAscending() {
        Agent zoe = agent("Zoe");
        Agent amir = agent("Amir");
        LocalDate tuesday = MONDAY.plusDays(1);

        ShiftTemplate assignedTemplate = template("Mid", LocalTime.of(10, 0), LocalTime.of(19, 0));

        when(agentUsualShiftRepository.findByTenantIdAndDeskId(TENANT_ID, DESK_ID))
                .thenReturn(List.of());

        // Deliberately inserted out of the expected final order: Tuesday/Zoe first, then
        // Monday/Zoe, then Monday/Amir -- correct output must be Monday/Amir, Monday/Zoe,
        // Tuesday/Zoe.
        Schedule schedule = schedule(60, List.of(
                shiftRow(zoe, tuesday, assignedTemplate),
                shiftRow(zoe, MONDAY, assignedTemplate),
                shiftRow(amir, MONDAY, assignedTemplate)));

        DriftReport report = service.buildDriftReport(schedule);

        assertThat(report.entries()).extracting(DriftReportEntry::date, DriftReportEntry::agentName)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(MONDAY, "Amir"),
                        org.assertj.core.groups.Tuple.tuple(MONDAY, "Zoe"),
                        org.assertj.core.groups.Tuple.tuple(tuesday, "Zoe"));
    }

    // ==================================================================
    //  Plan 17-03, Task 1 -- popularity ranking (DRFT-04, D-13): counts DISTINCT agents per
    //  resolved template name, read from stored usual shifts, never from this solve's results.
    // ==================================================================

    @Test
    void popularityRanking_fourAgentsEarlyTwoAgentsLate_sortedDescendingByCount() {
        ShiftTemplate early = template("Early", LocalTime.of(8, 0), LocalTime.of(17, 0));
        ShiftTemplate late = template("Late", LocalTime.of(12, 0), LocalTime.of(21, 0));

        List<AgentUsualShift> rows = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            rows.add(usualShift(agent("Early-" + i), DayOfWeek.MONDAY, early));
        }
        for (int i = 0; i < 2; i++) {
            rows.add(usualShift(agent("Late-" + i), DayOfWeek.MONDAY, late));
        }

        when(agentUsualShiftRepository.findByTenantIdAndDeskId(TENANT_ID, DESK_ID)).thenReturn(rows);
        when(shiftTemplateRepository.findByTenantIdAndDeskIdAndName(TENANT_ID, DESK_ID, "Early"))
                .thenReturn(List.of(early));
        when(shiftTemplateRepository.findByTenantIdAndDeskIdAndName(TENANT_ID, DESK_ID, "Late"))
                .thenReturn(List.of(late));

        DriftReport report = service.buildDriftReport(schedule(60, List.of()));

        assertThat(report.popularity()).containsExactly(
                new ShiftPopularityEntry("Early", 4),
                new ShiftPopularityEntry("Late", 2));
    }

    @Test
    void popularityRanking_tiedCounts_sortedAscendingByTemplateName() {
        ShiftTemplate zed = template("Zed", LocalTime.of(8, 0), LocalTime.of(17, 0));
        ShiftTemplate alpha = template("Alpha", LocalTime.of(9, 0), LocalTime.of(18, 0));

        when(agentUsualShiftRepository.findByTenantIdAndDeskId(TENANT_ID, DESK_ID)).thenReturn(List.of(
                usualShift(agent("A1"), DayOfWeek.MONDAY, zed),
                usualShift(agent("A2"), DayOfWeek.MONDAY, alpha)));
        when(shiftTemplateRepository.findByTenantIdAndDeskIdAndName(TENANT_ID, DESK_ID, "Zed"))
                .thenReturn(List.of(zed));
        when(shiftTemplateRepository.findByTenantIdAndDeskIdAndName(TENANT_ID, DESK_ID, "Alpha"))
                .thenReturn(List.of(alpha));

        DriftReport report = service.buildDriftReport(schedule(60, List.of()));

        assertThat(report.popularity()).extracting(ShiftPopularityEntry::templateName)
                .containsExactly("Alpha", "Zed");
    }

    @Test
    void popularityRanking_templateNoAgentHolds_doesNotAppearInTheList() {
        ShiftTemplate early = template("Early", LocalTime.of(8, 0), LocalTime.of(17, 0));

        // "Unsubscribed" is a live template in the library, but no AgentUsualShift row anywhere
        // references it -- it must never surface as a zero-count row (17-UI-SPEC.md §2: "one row
        // per template with at least one subscribing agent").
        when(agentUsualShiftRepository.findByTenantIdAndDeskId(TENANT_ID, DESK_ID)).thenReturn(List.of(
                usualShift(agent("Ana"), DayOfWeek.MONDAY, early)));
        when(shiftTemplateRepository.findByTenantIdAndDeskIdAndName(TENANT_ID, DESK_ID, "Early"))
                .thenReturn(List.of(early));

        DriftReport report = service.buildDriftReport(schedule(60, List.of()));

        assertThat(report.popularity()).extracting(ShiftPopularityEntry::templateName)
                .containsExactly("Early");
    }

    @Test
    void popularityRanking_noStoredUsualShifts_returnsEmptyNotNull() {
        when(agentUsualShiftRepository.findByTenantIdAndDeskId(TENANT_ID, DESK_ID)).thenReturn(List.of());

        DriftReport report = service.buildDriftReport(schedule(60, List.of()));

        assertThat(report.popularity()).isNotNull().isEmpty();
    }

    @Test
    void popularityRanking_agentWithSameUsualShiftOnThreeWeekdays_countsOnce() {
        ShiftTemplate early = template("Early", LocalTime.of(8, 0), LocalTime.of(17, 0));
        Agent ana = agent("Ana");

        when(agentUsualShiftRepository.findByTenantIdAndDeskId(TENANT_ID, DESK_ID)).thenReturn(List.of(
                usualShift(ana, DayOfWeek.MONDAY, early),
                usualShift(ana, DayOfWeek.TUESDAY, early),
                usualShift(ana, DayOfWeek.WEDNESDAY, early)));
        when(shiftTemplateRepository.findByTenantIdAndDeskIdAndName(TENANT_ID, DESK_ID, "Early"))
                .thenReturn(List.of(early));

        DriftReport report = service.buildDriftReport(schedule(60, List.of()));

        assertThat(report.popularity()).containsExactly(new ShiftPopularityEntry("Early", 1));
    }

    // ==================================================================
    //  Plan 17-03, Task 1 -- ScheduleService's date-filter block (DRFT-01): the drift summary
    //  must be RECOMPUTED from the filtered entries (unlike PreferenceReport's whole-schedule
    //  summary, carried through unfiltered), and popularity must survive untouched.
    //
    //  ScheduleService is instantiated directly (plain Mockito mocks for every repository
    //  dependency, a real InMemoryScheduleStore since it is a plain POJO with no Spring wiring
    //  needed for direct use, and a mocked ScheduleOutputService) so this narrow filtering
    //  behaviour is provable without a @DataJpaTest slice.
    // ==================================================================

    private static ScheduleService buildScheduleService(ScheduleOutputService scheduleOutputServiceMock,
            InMemoryScheduleStore store) {
        return new ScheduleService(
                mock(ScheduleRepository.class),
                mock(AcceptedScheduleDateRepository.class),
                mock(DeskRepository.class),
                store,
                mock(TimeslotRepository.class),
                mock(StaffingRequirementRepository.class),
                mock(AgentAssignmentRepository.class),
                mock(AgentShiftAssignmentRepository.class),
                mock(AgentPreferenceRepository.class),
                mock(AgentDayOffRepository.class),
                mock(ConstraintWeightsRepository.class),
                scheduleOutputServiceMock,
                mock(EntityManager.class));
    }

    private static Schedule inMemorySchedule(UUID scheduleId) {
        Schedule schedule = new Schedule();
        schedule.setId(scheduleId);
        schedule.setTenantId(TENANT_ID);
        schedule.setDeskId(DESK_ID);
        schedule.setStatus(ScheduleStatus.COMPLETED);
        return schedule;
    }

    private DriftReport unfilteredTwoDateDriftReport(Agent ana, Agent ben, LocalDate tuesday) {
        DriftReportEntry mondayEntry = new DriftReportEntry(ana.getId(), "Ana", MONDAY,
                DriftStatus.DRIFTED, LocalTime.of(8, 0), LocalTime.of(9, 30), 90);
        DriftReportEntry tuesdayEntry = new DriftReportEntry(ben.getId(), "Ben", tuesday,
                DriftStatus.HONOURED, LocalTime.of(8, 0), LocalTime.of(8, 10), null);
        return new DriftReport(
                List.of(mondayEntry, tuesdayEntry),
                new DriftSummary(2, 0, 1, 1),
                List.of(new ShiftPopularityEntry("Early", 2)));
    }

    @Test
    void dateFilter_narrowsDriftEntriesToThatDateOnly() {
        TenantContext.setTenantId(TENANT_ID);
        UUID scheduleId = UUID.randomUUID();
        Schedule schedule = inMemorySchedule(scheduleId);
        InMemoryScheduleStore store = new InMemoryScheduleStore();
        store.put(schedule);

        Agent ana = agent("Ana");
        Agent ben = agent("Ben");
        LocalDate tuesday = MONDAY.plusDays(1);
        DriftReport unfiltered = unfilteredTwoDateDriftReport(ana, ben, tuesday);

        ScheduleOutputService outputMock = mock(ScheduleOutputService.class);
        when(outputMock.buildStaffingSummary(schedule)).thenReturn(List.of());
        when(outputMock.buildAgentSchedule(schedule)).thenReturn(List.of());
        when(outputMock.buildPreferenceReport(schedule)).thenReturn(null);
        when(outputMock.buildConstraintViolations(schedule, false)).thenReturn(List.of());
        when(outputMock.buildDriftReport(schedule)).thenReturn(unfiltered);

        ScheduleService scheduleService = buildScheduleService(outputMock, store);
        ScheduleDetailResponse response = scheduleService.getScheduleDetail(DESK_ID, scheduleId, MONDAY.toString());

        assertThat(response.getDriftReport().entries())
                .extracting(DriftReportEntry::date)
                .containsExactly(MONDAY);
    }

    @Test
    void dateFilter_recomputesSummaryFromTheFilteredEntries() {
        TenantContext.setTenantId(TENANT_ID);
        UUID scheduleId = UUID.randomUUID();
        Schedule schedule = inMemorySchedule(scheduleId);
        InMemoryScheduleStore store = new InMemoryScheduleStore();
        store.put(schedule);

        Agent ana = agent("Ana");
        Agent ben = agent("Ben");
        LocalDate tuesday = MONDAY.plusDays(1);
        DriftReport unfiltered = unfilteredTwoDateDriftReport(ana, ben, tuesday);

        ScheduleOutputService outputMock = mock(ScheduleOutputService.class);
        when(outputMock.buildStaffingSummary(schedule)).thenReturn(List.of());
        when(outputMock.buildAgentSchedule(schedule)).thenReturn(List.of());
        when(outputMock.buildPreferenceReport(schedule)).thenReturn(null);
        when(outputMock.buildConstraintViolations(schedule, false)).thenReturn(List.of());
        when(outputMock.buildDriftReport(schedule)).thenReturn(unfiltered);

        ScheduleService scheduleService = buildScheduleService(outputMock, store);
        ScheduleDetailResponse response = scheduleService.getScheduleDetail(DESK_ID, scheduleId, MONDAY.toString());

        DriftSummary summary = response.getDriftReport().summary();
        assertThat(summary.workingAgentDays()).isEqualTo(1);
        assertThat(summary.driftedCount()).isEqualTo(1);
        assertThat(summary.honouredCount()).isEqualTo(0);
        assertThat(summary.noUsualShiftCount()).isEqualTo(0);
        assertThat(summary.workingAgentDays())
                .isEqualTo(summary.noUsualShiftCount() + summary.honouredCount() + summary.driftedCount());
    }

    @Test
    void dateFilter_popularityListUnchangedFromTheUnfilteredResponse() {
        TenantContext.setTenantId(TENANT_ID);
        UUID scheduleId = UUID.randomUUID();
        Schedule schedule = inMemorySchedule(scheduleId);
        InMemoryScheduleStore store = new InMemoryScheduleStore();
        store.put(schedule);

        Agent ana = agent("Ana");
        Agent ben = agent("Ben");
        LocalDate tuesday = MONDAY.plusDays(1);
        DriftReport unfiltered = unfilteredTwoDateDriftReport(ana, ben, tuesday);

        ScheduleOutputService outputMock = mock(ScheduleOutputService.class);
        when(outputMock.buildStaffingSummary(schedule)).thenReturn(List.of());
        when(outputMock.buildAgentSchedule(schedule)).thenReturn(List.of());
        when(outputMock.buildPreferenceReport(schedule)).thenReturn(null);
        when(outputMock.buildConstraintViolations(schedule, false)).thenReturn(List.of());
        when(outputMock.buildDriftReport(schedule)).thenReturn(unfiltered);

        ScheduleService scheduleService = buildScheduleService(outputMock, store);
        ScheduleDetailResponse response = scheduleService.getScheduleDetail(DESK_ID, scheduleId, MONDAY.toString());

        assertThat(response.getDriftReport().popularity()).isEqualTo(unfiltered.popularity());
    }

    @Test
    void dateFilter_malformedDate_stillThrowsIllegalArgumentException() {
        TenantContext.setTenantId(TENANT_ID);
        UUID scheduleId = UUID.randomUUID();
        Schedule schedule = inMemorySchedule(scheduleId);
        InMemoryScheduleStore store = new InMemoryScheduleStore();
        store.put(schedule);

        Agent ana = agent("Ana");
        Agent ben = agent("Ben");
        LocalDate tuesday = MONDAY.plusDays(1);
        DriftReport unfiltered = unfilteredTwoDateDriftReport(ana, ben, tuesday);

        ScheduleOutputService outputMock = mock(ScheduleOutputService.class);
        when(outputMock.buildStaffingSummary(schedule)).thenReturn(List.of());
        when(outputMock.buildAgentSchedule(schedule)).thenReturn(List.of());
        when(outputMock.buildPreferenceReport(schedule)).thenReturn(null);
        when(outputMock.buildConstraintViolations(schedule, false)).thenReturn(List.of());
        when(outputMock.buildDriftReport(schedule)).thenReturn(unfiltered);

        ScheduleService scheduleService = buildScheduleService(outputMock, store);

        assertThatThrownBy(() -> scheduleService.getScheduleDetail(DESK_ID, scheduleId, "not-a-date"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
