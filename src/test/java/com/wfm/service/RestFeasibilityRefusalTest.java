package com.wfm.service;

import com.wfm.exception.PreSolveValidationException;
import com.wfm.model.Agent;
import com.wfm.model.AgentAssignment;
import com.wfm.model.AgentDayConfig;
import com.wfm.model.AgentRestWaiver;
import com.wfm.model.AgentShiftAssignment;
import com.wfm.model.BreakAlignment;
import com.wfm.model.RestSpan;
import com.wfm.model.ScheduleConfig;
import com.wfm.model.SchedulingMode;
import com.wfm.model.ShiftBandPair;
import com.wfm.model.ShiftTemplate;
import com.wfm.util.DayWindow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 22 plan 22-07 (REST-03, D-12) — proves {@link SolverService#requireRestFeasibility} makes
 * structural rest impossibility a CHECKED PRECONDITION of every solve, by a mechanism genuinely
 * separate from the in-solve hard constraints ({@code ScheduleConstraintProvider.minimumRestShift}
 * / {@code minimumRestSlot}): a universal cross-date claim refuses, a single satisfying combination
 * never does, the pre-horizon predecessor is reasoned about exactly like an in-horizon one, and a
 * waived occurrence never reaches accumulation at all.
 *
 * <p>Shared fixture shape: every template nets exactly 8h (matching every {@link AgentDayConfig}'s
 * {@code effectiveHours} below) so {@link AgentShiftAssignment#getEligibleShiftBandPairs()}'s
 * exact-equality filter admits it, is valid every weekday and effective from 2020-01-01 with no
 * retirement date, mirroring {@code ShiftEnvelopeSupplyGateTest}'s and
 * {@code MinimumRestShiftConstraintTest}'s own fixture idiom.
 */
class RestFeasibilityRefusalTest {

    private static final LocalDate D_MINUS_1 = LocalDate.of(2026, 9, 7);
    private static final LocalDate D = LocalDate.of(2026, 9, 8);
    private static final int MINIMUM_REST_MINUTES = 660;

    // ------------------------------------------------------------------
    //  Fixture builders
    // ------------------------------------------------------------------

    private static Agent agent(String name) {
        Agent a = new Agent();
        a.setId(UUID.randomUUID());
        a.setName(name);
        a.setActive(true);
        return a;
    }

    private static ShiftTemplate template(LocalTime start, LocalTime end) {
        ShiftTemplate t = new ShiftTemplate();
        t.setId(UUID.randomUUID());
        t.setName("Template-" + UUID.randomUUID());
        t.setStartTime(start);
        t.setEndTime(end);
        t.setValidWeekdays(EnumSet.allOf(DayOfWeek.class));
        t.setEffectiveFrom(LocalDate.of(2020, 1, 1));
        t.setEffectiveTo(null);
        return t;
    }

    private static ShiftBandPair pair(LocalTime start, LocalTime end) {
        return new ShiftBandPair(template(start, end), null);
    }

    private static AgentDayConfig dayConfig(UUID agentId, LocalDate date) {
        return new AgentDayConfig(agentId, date, new BigDecimal("8.00"), 60, 60,
                new BigDecimal("4.00"), new BigDecimal("1.00"), BreakAlignment.ON_HOUR, 100, 70,
                LocalTime.MIDNIGHT);
    }

    private static AgentShiftAssignment shiftRow(Agent agent, LocalDate date, List<ShiftBandPair> eligiblePairs) {
        AgentShiftAssignment row = new AgentShiftAssignment();
        row.setId(UUID.randomUUID());
        row.setAgent(agent);
        row.setDate(date);
        row.setDeskShiftBandPairs(eligiblePairs);
        row.setDayConfig(dayConfig(agent.getId(), date));
        return row;
    }

    private static AgentShiftAssignment shiftRow(Agent agent, LocalDate date, ShiftBandPair singlePair) {
        return shiftRow(agent, date, List.of(singlePair));
    }

    private static AgentRestWaiver waiver(Agent agent, LocalDate date) {
        AgentRestWaiver w = new AgentRestWaiver();
        w.setId(UUID.randomUUID());
        w.setTenantId(1L);
        w.setDeskId(UUID.randomUUID());
        w.setAgent(agent);
        w.setDate(date);
        w.setReason("test");
        return w;
    }

    private static ScheduleConfig scheduleConfig(SchedulingMode mode, Integer minimumRestMinutes) {
        return new ScheduleConfig(15, LocalTime.of(0, 0), LocalTime.of(23, 59),
                60, new BigDecimal("4.00"), new BigDecimal("1.00"),
                BreakAlignment.ON_HOUR, 20, new BigDecimal("8.00"), 130, 70, mode,
                ScheduleConfig.DEFAULT_CONSISTENCY_TOLERANCE_MINUTES, LocalTime.MIDNIGHT,
                minimumRestMinutes);
    }

    private static final DayWindow MIDNIGHT_WINDOW = DayWindow.anchoredAt(LocalTime.MIDNIGHT);

    // ------------------------------------------------------------------
    //  SHIFT mode -- universal violation refuses, a single satisfying combination never does
    // ------------------------------------------------------------------

    @Test
    @DisplayName("exactly one eligible template on each date, the only combination violates -- refused naming the agent and both dates")
    void oneTemplateCombination_violatesMinimum_refusedNamingAgentAndBothShifts() {
        Agent a = agent("Alex");
        AgentShiftAssignment prev = shiftRow(a, D_MINUS_1, pair(LocalTime.of(14, 0), LocalTime.of(22, 0)));
        AgentShiftAssignment next = shiftRow(a, D, pair(LocalTime.of(6, 0), LocalTime.of(14, 0)));
        List<String> warnings = new ArrayList<>();

        assertThatThrownBy(() -> SolverService.requireRestFeasibility(SchedulingMode.SHIFT,
                MINIMUM_REST_MINUTES, List.of(prev, next), List.of(), List.of(), List.of(),
                List.of(), scheduleConfig(SchedulingMode.SHIFT, MINIMUM_REST_MINUTES), warnings,
                MIDNIGHT_WINDOW))
                .isInstanceOf(PreSolveValidationException.class)
                .satisfies(ex -> {
                    PreSolveValidationException pve = (PreSolveValidationException) ex;
                    assertThat(pve.getDetails()).hasSize(1);
                    assertThat(pve.getDetails().get(0).value()).isEqualTo(a.getId().toString());
                    assertThat(pve.getDetails().get(0).message())
                            .contains("Alex").contains(D_MINUS_1.toString()).contains(D.toString())
                            .contains("480").contains(String.valueOf(MINIMUM_REST_MINUTES));
                });
    }

    @Test
    @DisplayName("two eligible templates on D, at least one combination satisfies the minimum -- not refused")
    void twoEligibleTemplatesOnD_atLeastOneCombinationSatisfies_noRefusal() {
        Agent a = agent("Blair");
        AgentShiftAssignment prev = shiftRow(a, D_MINUS_1, pair(LocalTime.of(14, 0), LocalTime.of(22, 0)));
        AgentShiftAssignment next = shiftRow(a, D, List.of(
                pair(LocalTime.of(6, 0), LocalTime.of(14, 0)),
                pair(LocalTime.of(9, 0), LocalTime.of(17, 0))));
        List<String> warnings = new ArrayList<>();

        assertThatCode(() -> SolverService.requireRestFeasibility(SchedulingMode.SHIFT,
                MINIMUM_REST_MINUTES, List.of(prev, next), List.of(), List.of(), List.of(),
                List.of(), scheduleConfig(SchedulingMode.SHIFT, MINIMUM_REST_MINUTES), warnings,
                MIDNIGHT_WINDOW))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("three agents each structurally impossible -- one exception carrying three ErrorDetails, not three exceptions")
    void threeAgentsEachStructurallyImpossible_oneExceptionWithThreeErrorDetails() {
        Agent a = agent("Casey");
        Agent b = agent("Drew");
        Agent c = agent("Ellis");
        List<AgentShiftAssignment> rows = new ArrayList<>();
        for (Agent agent : List.of(a, b, c)) {
            rows.add(shiftRow(agent, D_MINUS_1, pair(LocalTime.of(14, 0), LocalTime.of(22, 0))));
            rows.add(shiftRow(agent, D, pair(LocalTime.of(6, 0), LocalTime.of(14, 0))));
        }
        List<String> warnings = new ArrayList<>();

        assertThatThrownBy(() -> SolverService.requireRestFeasibility(SchedulingMode.SHIFT,
                MINIMUM_REST_MINUTES, rows, List.of(), List.of(), List.of(), List.of(),
                scheduleConfig(SchedulingMode.SHIFT, MINIMUM_REST_MINUTES), warnings, MIDNIGHT_WINDOW))
                .isInstanceOf(PreSolveValidationException.class)
                .satisfies(ex -> assertThat(((PreSolveValidationException) ex).getDetails()).hasSize(3));
    }

    // ------------------------------------------------------------------
    //  Pre-horizon predecessor (D-12, REST-05)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("pre-horizon edge: every day-1 template violates against the accepted day-0 shift -- refused")
    void preHorizonEdge_everyDay1TemplateViolatesAgainstAcceptedDay0Shift_refused() {
        Agent a = agent("Finley");
        RestSpan priorSpan = new RestSpan(a.getId(), D_MINUS_1, LocalTime.of(14, 0), LocalTime.of(22, 0),
                LocalTime.MIDNIGHT);
        AgentShiftAssignment dRow = shiftRow(a, D, pair(LocalTime.of(6, 0), LocalTime.of(14, 0)));
        List<String> warnings = new ArrayList<>();

        assertThatThrownBy(() -> SolverService.requireRestFeasibility(SchedulingMode.SHIFT,
                MINIMUM_REST_MINUTES, List.of(dRow), List.of(), List.of(priorSpan), List.of(),
                List.of(), scheduleConfig(SchedulingMode.SHIFT, MINIMUM_REST_MINUTES), warnings,
                MIDNIGHT_WINDOW))
                .isInstanceOf(PreSolveValidationException.class)
                .satisfies(ex -> {
                    PreSolveValidationException pve = (PreSolveValidationException) ex;
                    assertThat(pve.getDetails()).hasSize(1);
                    assertThat(pve.getDetails().get(0).message())
                            .contains("Finley").contains(D_MINUS_1.toString()).contains(D.toString());
                });
    }

    @Test
    @DisplayName("pre-horizon edge cleared by a waiver on the period's first business date -- not refused")
    void preHorizonEdge_withWaiverOnFirstBusinessDate_noRefusal() {
        Agent a = agent("Gray");
        RestSpan priorSpan = new RestSpan(a.getId(), D_MINUS_1, LocalTime.of(14, 0), LocalTime.of(22, 0),
                LocalTime.MIDNIGHT);
        AgentShiftAssignment dRow = shiftRow(a, D, pair(LocalTime.of(6, 0), LocalTime.of(14, 0)));
        List<String> warnings = new ArrayList<>();

        assertThatCode(() -> SolverService.requireRestFeasibility(SchedulingMode.SHIFT,
                MINIMUM_REST_MINUTES, List.of(dRow), List.of(), List.of(priorSpan), List.of(waiver(a, D)),
                List.of(), scheduleConfig(SchedulingMode.SHIFT, MINIMUM_REST_MINUTES), warnings,
                MIDNIGHT_WINDOW))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("an in-horizon impossible pair with a waiver on its successor date -- not refused")
    void inHorizonImpossiblePair_withWaiverOnSuccessorDate_noRefusal() {
        Agent a = agent("Harper");
        AgentShiftAssignment prev = shiftRow(a, D_MINUS_1, pair(LocalTime.of(14, 0), LocalTime.of(22, 0)));
        AgentShiftAssignment next = shiftRow(a, D, pair(LocalTime.of(6, 0), LocalTime.of(14, 0)));
        List<String> warnings = new ArrayList<>();

        assertThatCode(() -> SolverService.requireRestFeasibility(SchedulingMode.SHIFT,
                MINIMUM_REST_MINUTES, List.of(prev, next), List.of(), List.of(), List.of(waiver(a, D)),
                List.of(), scheduleConfig(SchedulingMode.SHIFT, MINIMUM_REST_MINUTES), warnings,
                MIDNIGHT_WINDOW))
                .doesNotThrowAnyException();
    }

    // ------------------------------------------------------------------
    //  Empty eligible range on either date -- the seat-supply gate's own refusal, not this one
    // ------------------------------------------------------------------

    @Test
    @DisplayName("an empty eligible range on the successor date produces no rest refusal from this check")
    void emptyEligibleRangeOnSuccessorDate_noRestRefusalFromThisCheck() {
        Agent a = agent("Indigo");
        AgentShiftAssignment prev = shiftRow(a, D_MINUS_1, pair(LocalTime.of(14, 0), LocalTime.of(22, 0)));
        AgentShiftAssignment next = shiftRow(a, D, List.of());
        List<String> warnings = new ArrayList<>();

        assertThatCode(() -> SolverService.requireRestFeasibility(SchedulingMode.SHIFT,
                MINIMUM_REST_MINUTES, List.of(prev, next), List.of(), List.of(), List.of(),
                List.of(), scheduleConfig(SchedulingMode.SHIFT, MINIMUM_REST_MINUTES), warnings,
                MIDNIGHT_WINDOW))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("an empty eligible range on the predecessor date produces no rest refusal from this check")
    void emptyEligibleRangeOnPredecessorDate_noRestRefusalFromThisCheck() {
        Agent a = agent("Jules");
        AgentShiftAssignment prev = shiftRow(a, D_MINUS_1, List.of());
        AgentShiftAssignment next = shiftRow(a, D, pair(LocalTime.of(6, 0), LocalTime.of(14, 0)));
        List<String> warnings = new ArrayList<>();

        assertThatCode(() -> SolverService.requireRestFeasibility(SchedulingMode.SHIFT,
                MINIMUM_REST_MINUTES, List.of(prev, next), List.of(), List.of(), List.of(),
                List.of(), scheduleConfig(SchedulingMode.SHIFT, MINIMUM_REST_MINUTES), warnings,
                MIDNIGHT_WINDOW))
                .doesNotThrowAnyException();
    }

    // ------------------------------------------------------------------
    //  Structural no-ops (REST-04)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a null minimumRestMinutes is a structural early return -- no work at all, not merely no refusal")
    void nullMinimumRestMinutes_structuralEarlyReturn_noWorkAtAll() {
        Agent a = agent("Kai");
        AgentShiftAssignment prev = shiftRow(a, D_MINUS_1, pair(LocalTime.of(14, 0), LocalTime.of(22, 0)));
        AgentShiftAssignment next = shiftRow(a, D, pair(LocalTime.of(6, 0), LocalTime.of(14, 0)));
        List<String> warnings = new ArrayList<>();

        assertThatCode(() -> SolverService.requireRestFeasibility(SchedulingMode.SHIFT,
                null, List.of(prev, next), List.of(), List.of(), List.of(),
                List.of(), scheduleConfig(SchedulingMode.SHIFT, null), warnings, MIDNIGHT_WINDOW))
                .doesNotThrowAnyException();
        assertThat(warnings).isEmpty();
    }

    @Test
    @DisplayName("a SLOT-mode desk: the SHIFT branch does not accumulate anything, even with violating shift rows present")
    void slotModeDeskWithShiftAssignmentsPresent_shiftBranchDoesNotAccumulate() {
        Agent a = agent("Lane");
        AgentShiftAssignment prev = shiftRow(a, D_MINUS_1, pair(LocalTime.of(14, 0), LocalTime.of(22, 0)));
        AgentShiftAssignment next = shiftRow(a, D, pair(LocalTime.of(6, 0), LocalTime.of(14, 0)));
        List<String> warnings = new ArrayList<>();

        assertThatCode(() -> SolverService.requireRestFeasibility(SchedulingMode.SLOT,
                MINIMUM_REST_MINUTES, List.of(prev, next), List.of(), List.of(), List.of(),
                List.<AgentAssignment>of(), scheduleConfig(SchedulingMode.SLOT, MINIMUM_REST_MINUTES),
                warnings, MIDNIGHT_WINDOW))
                .doesNotThrowAnyException();
    }
}
