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
    // CR-01 fixtures: DAY_OFF never gets a row or a config (a real day off is omitted everywhere).
    private static final LocalDate DAY_OFF = LocalDate.of(2026, 9, 9);
    private static final LocalDate AFTER_DAY_OFF = LocalDate.of(2026, 9, 10);
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
        return scheduleConfig(mode, minimumRestMinutes, LocalTime.of(0, 0), LocalTime.of(23, 59));
    }

    private static ScheduleConfig scheduleConfig(SchedulingMode mode, Integer minimumRestMinutes,
            LocalTime operatingStart, LocalTime operatingEnd) {
        return new ScheduleConfig(60, operatingStart, operatingEnd,
                60, new BigDecimal("4.00"), new BigDecimal("1.00"),
                BreakAlignment.ON_HOUR, 20, new BigDecimal("8.00"), 130, 70, mode,
                ScheduleConfig.DEFAULT_CONSISTENCY_TOLERANCE_MINUTES, LocalTime.MIDNIGHT,
                minimumRestMinutes);
    }

    /** A SLOT-mode agent-day config with a 60-minute increment, so {@code expectedWorkSlots() *
     *  incrementMinutes()} equals {@code hours * 60} exactly -- clean hand-computable minute
     *  figures for every SLOT fixture below. */
    private static AgentDayConfig slotDayConfig(UUID agentId, LocalDate date, int hours) {
        return new AgentDayConfig(agentId, date, new BigDecimal(hours), 60, 60,
                new BigDecimal("4.00"), new BigDecimal("1.00"), BreakAlignment.ON_HOUR, 100, 70,
                LocalTime.MIDNIGHT);
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
    @DisplayName("SHIFT: pre-horizon overnight predecessor (22:00-06:00) refused on the true 60-minute gap, not the pre-fix false-large figure")
    void shift_preHorizonOvernightSpan_refusedOnTheTrueGap() {
        Agent a = agent("Uma");
        RestSpan priorSpan = new RestSpan(a.getId(), D_MINUS_1, LocalTime.of(22, 0), LocalTime.of(6, 0),
                LocalTime.MIDNIGHT);
        AgentShiftAssignment dRow = shiftRow(a, D, pair(LocalTime.of(7, 0), LocalTime.of(15, 0)));
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
                            .contains("achieves only 60 minute(s)").contains("required 660");
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

    // ------------------------------------------------------------------
    //  SLOT mode (REST-04, D-01/D-02) -- a sound sufficient condition, never a false refusal
    // ------------------------------------------------------------------

    @Test
    @DisplayName("SLOT: 08:00-20:00 window, 8h contracted both days, minimum 660 -- best achievable gap is 20h, not refused")
    void slot_eightHourWindow_eightHourContract_minimum660_notRefused() {
        Agent a = agent("Morgan");
        AgentDayConfig dMinus1 = slotDayConfig(a.getId(), D_MINUS_1, 8);
        AgentDayConfig dConfig = slotDayConfig(a.getId(), D, 8);
        List<String> warnings = new ArrayList<>();

        assertThatCode(() -> SolverService.requireRestFeasibility(SchedulingMode.SLOT,
                MINIMUM_REST_MINUTES, List.of(), List.of(dMinus1, dConfig), List.of(), List.of(),
                List.of(), scheduleConfig(SchedulingMode.SLOT, MINIMUM_REST_MINUTES,
                        LocalTime.of(8, 0), LocalTime.of(20, 0)),
                warnings, MIDNIGHT_WINDOW))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("SLOT: window narrowed to 08:00-18:00, 9h contracted both days, minimum 660 -- best gap is 16h, not refused")
    void slot_narrowedWindow_nineHourContract_minimum660_notRefused() {
        Agent a = agent("Noor");
        AgentDayConfig dMinus1 = slotDayConfig(a.getId(), D_MINUS_1, 9);
        AgentDayConfig dConfig = slotDayConfig(a.getId(), D, 9);
        List<String> warnings = new ArrayList<>();

        assertThatCode(() -> SolverService.requireRestFeasibility(SchedulingMode.SLOT,
                MINIMUM_REST_MINUTES, List.of(), List.of(dMinus1, dConfig), List.of(), List.of(),
                List.of(), scheduleConfig(SchedulingMode.SLOT, MINIMUM_REST_MINUTES,
                        LocalTime.of(8, 0), LocalTime.of(18, 0)),
                warnings, MIDNIGHT_WINDOW))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("SLOT: identical narrowed-window fixture, minimum raised to 1000 -- refused, reporting the exact 960-minute best gap")
    void slot_narrowedWindow_nineHourContract_minimum1000_refusedWithExactFigure() {
        Agent a = agent("Oakley");
        AgentDayConfig dMinus1 = slotDayConfig(a.getId(), D_MINUS_1, 9);
        AgentDayConfig dConfig = slotDayConfig(a.getId(), D, 9);
        List<String> warnings = new ArrayList<>();

        assertThatThrownBy(() -> SolverService.requireRestFeasibility(SchedulingMode.SLOT,
                1000, List.of(), List.of(dMinus1, dConfig), List.of(), List.of(),
                List.of(), scheduleConfig(SchedulingMode.SLOT, 1000,
                        LocalTime.of(8, 0), LocalTime.of(18, 0)),
                warnings, MIDNIGHT_WINDOW))
                .isInstanceOf(PreSolveValidationException.class)
                .satisfies(ex -> {
                    PreSolveValidationException pve = (PreSolveValidationException) ex;
                    assertThat(pve.getDetails()).hasSize(1);
                    assertThat(pve.getDetails().get(0).value()).isEqualTo(a.getId().toString());
                    assertThat(pve.getDetails().get(0).message())
                            .contains(D_MINUS_1.toString()).contains(D.toString())
                            .contains("960").contains("1000");
                });
    }

    @Test
    @DisplayName("SLOT: a day whose required slot minutes exceed the operating window produces no rest refusal")
    void slot_requiredMinutesExceedWindow_noRestRefusal() {
        Agent a = agent("Peyton");
        // 8h (480 min) required, but the window is only 08:00-12:00 (240 min) -- this date cannot
        // hold the agent's contracted hours at all, a contracted-hours-versus-window failure with
        // its own surface, never a rest failure.
        AgentDayConfig dConfig = slotDayConfig(a.getId(), D, 8);
        List<String> warnings = new ArrayList<>();

        assertThatCode(() -> SolverService.requireRestFeasibility(SchedulingMode.SLOT,
                MINIMUM_REST_MINUTES, List.of(), List.of(dConfig), List.of(), List.of(),
                List.of(), scheduleConfig(SchedulingMode.SLOT, MINIMUM_REST_MINUTES,
                        LocalTime.of(8, 0), LocalTime.of(12, 0)),
                warnings, MIDNIGHT_WINDOW))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("SLOT: a waiver on the successor date suppresses the refusal through the same shared predicate")
    void slot_waiverOnSuccessorDate_suppressesRefusal() {
        Agent a = agent("Quinn");
        AgentDayConfig dMinus1 = slotDayConfig(a.getId(), D_MINUS_1, 9);
        AgentDayConfig dConfig = slotDayConfig(a.getId(), D, 9);
        List<String> warnings = new ArrayList<>();

        assertThatCode(() -> SolverService.requireRestFeasibility(SchedulingMode.SLOT,
                1000, List.of(), List.of(dMinus1, dConfig), List.of(), List.of(waiver(a, D)),
                List.of(), scheduleConfig(SchedulingMode.SLOT, 1000,
                        LocalTime.of(8, 0), LocalTime.of(18, 0)),
                warnings, MIDNIGHT_WINDOW))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("SLOT: pre-horizon edge uses the accepted span's actual end, not an earliest-possible estimate, and the refusal reports the exact figure")
    void slot_preHorizonEdge_usesAcceptedActualEnd_refusedWithExactFigure() {
        Agent a = agent("Reese");
        // No AgentDayConfig for D_MINUS_1 at all -- this agent's predecessor is pre-horizon. The
        // accepted span's actual end (19:00) is what must be used, never an earliest-possible
        // figure computed from a D-1 AgentDayConfig that does not exist here.
        RestSpan priorSpan = new RestSpan(a.getId(), D_MINUS_1, LocalTime.of(10, 0), LocalTime.of(19, 0),
                LocalTime.MIDNIGHT);
        AgentDayConfig dConfig = slotDayConfig(a.getId(), D, 8);
        List<String> warnings = new ArrayList<>();

        // window 08:00-20:00 (480-1200), requiredMinutesD = 480, successorLatestStart = 1200-480=720.
        // predecessorEndMinute = anchoredEndMinute(19:00) = 1140. bestGap = 1440-1140+720 = 1020.
        assertThatThrownBy(() -> SolverService.requireRestFeasibility(SchedulingMode.SLOT,
                1080, List.of(), List.of(dConfig), List.of(priorSpan), List.of(),
                List.of(), scheduleConfig(SchedulingMode.SLOT, 1080,
                        LocalTime.of(8, 0), LocalTime.of(20, 0)),
                warnings, MIDNIGHT_WINDOW))
                .isInstanceOf(PreSolveValidationException.class)
                .satisfies(ex -> {
                    PreSolveValidationException pve = (PreSolveValidationException) ex;
                    assertThat(pve.getDetails()).hasSize(1);
                    assertThat(pve.getDetails().get(0).message())
                            .contains("1020").contains("1080");
                });
    }

    @Test
    @DisplayName("SLOT: pre-horizon overnight span (22:00-06:00) refused on the true wrapped end, not the pre-fix false-large best gap")
    void slot_preHorizonOvernightSpan_refusedOnTheTrueWrappedEnd() {
        Agent a = agent("Val");
        // No AgentDayConfig for D_MINUS_1 at all -- this agent's predecessor is pre-horizon, and the
        // accepted span wrapped past the anchor (22:00-06:00).
        RestSpan priorSpan = new RestSpan(a.getId(), D_MINUS_1, LocalTime.of(22, 0), LocalTime.of(6, 0),
                LocalTime.MIDNIGHT);
        AgentDayConfig dConfig = slotDayConfig(a.getId(), D, 8);
        List<String> warnings = new ArrayList<>();

        // window 08:00-20:00 (480-1200), requiredMinutesD = 480, successorLatestStart = 1200-480=720.
        // predecessorEndMinute = anchoredWrappedEndMinute(22:00,06:00) = anchoredStartMinute(22:00)
        // 1320 plus the 480-minute wrapped duration = 1800. bestGap = 1440-1800+720 = 360. Before
        // this fix, predecessorEndMinute read the isolated anchoredEndMinute(06:00) = 360, giving a
        // falsely-large bestGap of 1800, and nothing was refused.
        assertThatThrownBy(() -> SolverService.requireRestFeasibility(SchedulingMode.SLOT,
                660, List.of(), List.of(dConfig), List.of(priorSpan), List.of(),
                List.of(), scheduleConfig(SchedulingMode.SLOT, 660,
                        LocalTime.of(8, 0), LocalTime.of(20, 0)),
                warnings, MIDNIGHT_WINDOW))
                .isInstanceOf(PreSolveValidationException.class)
                .satisfies(ex -> {
                    PreSolveValidationException pve = (PreSolveValidationException) ex;
                    assertThat(pve.getDetails()).hasSize(1);
                    assertThat(pve.getDetails().get(0).message())
                            .contains("only 360 minute(s)").contains("required 660");
                });
    }

    @Test
    @DisplayName("SLOT: a null minimumRestMinutes is a structural no-op -- no work, no refusal")
    void slot_nullMinimumRestMinutes_structuralNoOp() {
        Agent a = agent("Sage");
        AgentDayConfig dMinus1 = slotDayConfig(a.getId(), D_MINUS_1, 9);
        AgentDayConfig dConfig = slotDayConfig(a.getId(), D, 9);
        List<String> warnings = new ArrayList<>();

        assertThatCode(() -> SolverService.requireRestFeasibility(SchedulingMode.SLOT,
                null, List.of(), List.of(dMinus1, dConfig), List.of(), List.of(),
                List.of(), scheduleConfig(SchedulingMode.SLOT, null,
                        LocalTime.of(8, 0), LocalTime.of(18, 0)),
                warnings, MIDNIGHT_WINDOW))
                .doesNotThrowAnyException();
        assertThat(warnings).isEmpty();
    }

    @Test
    @DisplayName("SLOT: a SHIFT-mode desk never runs the SLOT branch, even with agentDayConfigs that would otherwise refuse")
    void slot_shiftModeDesk_slotBranchDoesNotRun() {
        Agent a = agent("Tatum");
        AgentDayConfig dMinus1 = slotDayConfig(a.getId(), D_MINUS_1, 9);
        AgentDayConfig dConfig = slotDayConfig(a.getId(), D, 9);
        List<String> warnings = new ArrayList<>();

        assertThatCode(() -> SolverService.requireRestFeasibility(SchedulingMode.SHIFT,
                1000, List.of(), List.of(dMinus1, dConfig), List.of(), List.of(),
                List.of(), scheduleConfig(SchedulingMode.SHIFT, 1000,
                        LocalTime.of(8, 0), LocalTime.of(18, 0)),
                warnings, MIDNIGHT_WINDOW))
                .doesNotThrowAnyException();
    }

    // ------------------------------------------------------------------
    //  CR-01 -- the pre-horizon span is a predecessor only at the true horizon edge (D-12, REST-05)
    // ------------------------------------------------------------------

    // D plays the period's first business date, and D_MINUS_1 is the date RestPredecessorService
    // resolved the pre-horizon span for. DAY_OFF is absent from every row list and config list,
    // exactly as computeAgentDayConfigs and buildShiftAssignments omit a real day off, so
    // AFTER_DAY_OFF has no in-horizon D-1 entry and must NOT fall back to the pre-horizon span.

    @Test
    @DisplayName("SHIFT: a working day after a mid-horizon day off is not checked against the pre-horizon span -- no refusal")
    void shift_midHorizonDayOff_dayAfterIsNotCheckedAgainstPreHorizonSpan_noRefusal() {
        Agent a = agent("Wren");
        RestSpan priorSpan = new RestSpan(a.getId(), D_MINUS_1, LocalTime.of(15, 0), LocalTime.of(23, 0),
                LocalTime.MIDNIGHT);
        // Pre-horizon end 23:00 = wrapped minute 1380, leaving 60 minutes. D at 12:00: 60 + 720 = 780
        // (fine). AFTER_DAY_OFF at 06:00 against the pre-horizon span would be 60 + 360 = 420 (false refusal).
        AgentShiftAssignment dRow = shiftRow(a, D, pair(LocalTime.of(12, 0), LocalTime.of(20, 0)));
        AgentShiftAssignment afterRow = shiftRow(a, AFTER_DAY_OFF, pair(LocalTime.of(6, 0), LocalTime.of(14, 0)));
        List<String> warnings = new ArrayList<>();

        assertThatCode(() -> SolverService.requireRestFeasibility(SchedulingMode.SHIFT,
                MINIMUM_REST_MINUTES, List.of(dRow, afterRow), List.of(), List.of(priorSpan), List.of(),
                List.of(), scheduleConfig(SchedulingMode.SHIFT, MINIMUM_REST_MINUTES), warnings,
                MIDNIGHT_WINDOW))
                .doesNotThrowAnyException();
        assertThat(warnings).hasSize(1);
        assertThat(warnings.get(0)).contains(D.toString()).contains("at 780 minute(s)");
    }

    @Test
    @DisplayName("SHIFT control: an early start on the first business date is still refused against the pre-horizon span; the day after a day off is not")
    void shift_horizonEdgeControl_earlyStartOnFirstBusinessDateStillRefused_dayAfterDayOffIsNot() {
        Agent a = agent("Xan");
        RestSpan priorSpan = new RestSpan(a.getId(), D_MINUS_1, LocalTime.of(15, 0), LocalTime.of(23, 0),
                LocalTime.MIDNIGHT);
        // D at 06:00: 60 + 360 = 420 < 660 -- the genuine REST-05 refusal. AFTER_DAY_OFF adds none.
        AgentShiftAssignment dRow = shiftRow(a, D, pair(LocalTime.of(6, 0), LocalTime.of(14, 0)));
        AgentShiftAssignment afterRow = shiftRow(a, AFTER_DAY_OFF, pair(LocalTime.of(6, 0), LocalTime.of(14, 0)));
        List<String> warnings = new ArrayList<>();

        assertThatThrownBy(() -> SolverService.requireRestFeasibility(SchedulingMode.SHIFT,
                MINIMUM_REST_MINUTES, List.of(dRow, afterRow), List.of(), List.of(priorSpan), List.of(),
                List.of(), scheduleConfig(SchedulingMode.SHIFT, MINIMUM_REST_MINUTES), warnings,
                MIDNIGHT_WINDOW))
                .isInstanceOf(PreSolveValidationException.class)
                .satisfies(ex -> {
                    PreSolveValidationException pve = (PreSolveValidationException) ex;
                    assertThat(pve.getDetails()).hasSize(1);
                    assertThat(pve.getDetails().get(0).value()).isEqualTo(a.getId().toString());
                    assertThat(pve.getDetails().get(0).message())
                            .contains(D_MINUS_1.toString()).contains(D.toString())
                            .contains("achieves only 420 minute(s)").contains("required 660")
                            .doesNotContain(DAY_OFF.toString()).doesNotContain(AFTER_DAY_OFF.toString());
                });
    }

    @Test
    @DisplayName("SLOT: a working day after a mid-horizon day off is not checked against the pre-horizon span -- no refusal")
    void slot_midHorizonDayOff_dayAfterIsNotCheckedAgainstPreHorizonSpan_noRefusal() {
        Agent a = agent("Yael");
        RestSpan priorSpan = new RestSpan(a.getId(), D_MINUS_1, LocalTime.of(15, 0), LocalTime.of(23, 0),
                LocalTime.MIDNIGHT);
        // Window 08:00-20:00 (480-1200). D, 8h: latest start 1200-480 = 720, gap 60 + 720 = 780 (fine).
        // AFTER_DAY_OFF, 11h: latest start 1200-660 = 540, gap against the pre-horizon span would be
        // 60 + 540 = 600 < 660 (false refusal).
        AgentDayConfig dConfig = slotDayConfig(a.getId(), D, 8);
        AgentDayConfig afterConfig = slotDayConfig(a.getId(), AFTER_DAY_OFF, 11);
        List<String> warnings = new ArrayList<>();

        assertThatCode(() -> SolverService.requireRestFeasibility(SchedulingMode.SLOT,
                MINIMUM_REST_MINUTES, List.of(), List.of(dConfig, afterConfig), List.of(priorSpan), List.of(),
                List.of(), scheduleConfig(SchedulingMode.SLOT, MINIMUM_REST_MINUTES,
                        LocalTime.of(8, 0), LocalTime.of(20, 0)),
                warnings, MIDNIGHT_WINDOW))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("SLOT control: required hours on the first business date are still refused against the pre-horizon span; the day after a day off is not")
    void slot_horizonEdgeControl_requiredHoursOnFirstBusinessDateStillRefused_dayAfterDayOffIsNot() {
        Agent a = agent("Zion");
        RestSpan priorSpan = new RestSpan(a.getId(), D_MINUS_1, LocalTime.of(15, 0), LocalTime.of(23, 0),
                LocalTime.MIDNIGHT);
        // D, 11h: latest start 1200-660 = 540, gap 60 + 540 = 600 < 660 -- the genuine REST-05 refusal.
        AgentDayConfig dConfig = slotDayConfig(a.getId(), D, 11);
        AgentDayConfig afterConfig = slotDayConfig(a.getId(), AFTER_DAY_OFF, 11);
        List<String> warnings = new ArrayList<>();

        assertThatThrownBy(() -> SolverService.requireRestFeasibility(SchedulingMode.SLOT,
                MINIMUM_REST_MINUTES, List.of(), List.of(dConfig, afterConfig), List.of(priorSpan), List.of(),
                List.of(), scheduleConfig(SchedulingMode.SLOT, MINIMUM_REST_MINUTES,
                        LocalTime.of(8, 0), LocalTime.of(20, 0)),
                warnings, MIDNIGHT_WINDOW))
                .isInstanceOf(PreSolveValidationException.class)
                .satisfies(ex -> {
                    PreSolveValidationException pve = (PreSolveValidationException) ex;
                    assertThat(pve.getDetails()).hasSize(1);
                    assertThat(pve.getDetails().get(0).message())
                            .contains(D_MINUS_1.toString()).contains(D.toString())
                            .contains("only 600 minute(s)").contains("required 660")
                            .doesNotContain(DAY_OFF.toString()).doesNotContain(AFTER_DAY_OFF.toString());
                });
    }
}
