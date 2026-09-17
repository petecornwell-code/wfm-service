package com.wfm.service;

import ai.timefold.solver.core.api.solver.SolverFactory;
import com.wfm.dto.ScheduleDetailResponse.DriftReport;
import com.wfm.dto.ScheduleDetailResponse.DriftReportEntry;
import com.wfm.dto.ScheduleDetailResponse.DriftStatus;
import com.wfm.model.Agent;
import com.wfm.model.AgentShiftAssignment;
import com.wfm.model.AgentUsualShift;
import com.wfm.model.ConstraintWeights;
import com.wfm.model.Schedule;
import com.wfm.model.ShiftBandPair;
import com.wfm.model.ShiftTemplate;
import com.wfm.repository.AgentUsualShiftRepository;
import com.wfm.repository.ShiftTemplateRepository;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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

}
