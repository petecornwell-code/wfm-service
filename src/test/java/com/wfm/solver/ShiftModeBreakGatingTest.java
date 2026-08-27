package com.wfm.solver;

import ai.timefold.solver.core.api.solver.Solver;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.phase.PhaseConfig;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;
import ai.timefold.solver.test.api.score.stream.ConstraintVerifier;
import com.wfm.model.Agent;
import com.wfm.model.AgentAssignment;
import com.wfm.model.AgentDayConfig;
import com.wfm.model.AgentPreference;
import com.wfm.model.AgentShiftAssignment;
import com.wfm.model.BreakAlignment;
import com.wfm.model.Schedule;
import com.wfm.model.ScheduleConfig;
import com.wfm.model.SchedulingMode;
import com.wfm.model.ShiftBandPair;
import com.wfm.model.Timeslot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 15 plan 15-06, Task 1 — proves both directions of the mode-gate for the six constraints
 * {@code exactlyOneBreak}, {@code breakDuration}, {@code breakBlockedWindow},
 * {@code breakStartAlignment}, {@code honourPreferredStartTime} and
 * {@code honourPreferredBreakTime}: inert under a SHIFT {@link ScheduleConfig}, and unchanged
 * under SLOT — the exact must_haves.truths #2 claim ("BreakAwareConstructionTest is green AND
 * unmodified" is asserted separately, by the plan's own {@code git diff --quiet} verify step; this
 * file asserts the six constraints' own before/after values directly).
 *
 * <p>Each of the six scenarios below is deliberately built to isolate its own named constraint —
 * the plan's "an agent-day with two gaps, a gap of the wrong length, a gap inside the blocked
 * window, a gap starting off the alignment boundary, a seat before the preferred start time, and a
 * gap not at the preferred break time" is six distinct fixtures, not one fixture tripping all six
 * simultaneously (several of the six constraints' own bodies structurally cannot all fire together
 * on one assignment pattern — e.g. {@code breakDuration} explicitly defers to
 * {@code exactlyOneBreak} whenever there isn't exactly one gap). Every expected SLOT-mode penalty
 * below is derived directly from each constraint's own unmodified body (P-25), not guessed.
 */
class ShiftModeBreakGatingTest {

    private final ConstraintVerifier<ScheduleConstraintProvider, Schedule> verifier =
            ConstraintVerifier.build(new ScheduleConstraintProvider(), Schedule.class,
                    AgentAssignment.class, AgentShiftAssignment.class);

    private static final LocalDate DAY = LocalDate.of(2026, 9, 7);
    private static final int INCREMENT = 15;
    private static final BigDecimal CONTRACTED_HOURS = new BigDecimal("8.00");
    private static final BigDecimal BREAK_MIN_SHIFT_HOURS = new BigDecimal("4.00");
    private static final BigDecimal BREAK_BLOCKED_HOURS = new BigDecimal("1.00");
    private static final int BREAK_DURATION_MINUTES = 60;
    private static final BreakAlignment ALIGNMENT = BreakAlignment.ON_HOUR;

    private static Agent agent() {
        Agent a = new Agent();
        a.setId(UUID.randomUUID());
        return a;
    }

    private static AgentDayConfig dayConfig(Agent agent) {
        return new AgentDayConfig(agent.getId(), DAY, CONTRACTED_HOURS, INCREMENT,
                BREAK_DURATION_MINUTES, BREAK_MIN_SHIFT_HOURS, BREAK_BLOCKED_HOURS, ALIGNMENT, 130, 70);
    }

    private static ScheduleConfig scheduleConfig(SchedulingMode mode) {
        return new ScheduleConfig(INCREMENT, LocalTime.of(0, 0), LocalTime.of(23, 59),
                BREAK_DURATION_MINUTES, BREAK_MIN_SHIFT_HOURS, BREAK_BLOCKED_HOURS,
                ALIGNMENT, 20, CONTRACTED_HOURS, 130, 70, mode);
    }

    private static Timeslot ts(LocalTime start) {
        Timeslot t = new Timeslot();
        t.setId(UUID.randomUUID());
        t.setDate(DAY);
        t.setStartTime(start);
        t.setEndTime(start.plusMinutes(INCREMENT));
        return t;
    }

    private static AgentAssignment seat(Agent agent, LocalTime start) {
        AgentAssignment a = new AgentAssignment();
        a.setId(UUID.randomUUID());
        a.setAgent(agent);
        a.setTimeslot(ts(start));
        return a;
    }

    /** Every 15-min slot in {@code [start, end)} except any excluded interval, one seat per slot. */
    private static List<AgentAssignment> seatsExcept(Agent agent, LocalTime start, LocalTime end,
                                                       LocalTime excludeStart, LocalTime excludeEnd) {
        List<AgentAssignment> seats = new ArrayList<>();
        for (LocalTime t = start; t.isBefore(end); t = t.plusMinutes(INCREMENT)) {
            boolean excluded = excludeStart != null
                    && !t.isBefore(excludeStart) && t.isBefore(excludeEnd);
            if (!excluded) {
                seats.add(seat(agent, t));
            }
        }
        return seats;
    }

    // ------------------------------------------------------------------
    //  Scenario A -- exactlyOneBreak: two gaps
    // ------------------------------------------------------------------

    @Test
    @DisplayName("exactlyOneBreak: two gaps -- inert under SHIFT, unchanged under SLOT")
    void exactlyOneBreak_twoGaps() {
        Agent a = agent();
        List<AgentAssignment> seats = new ArrayList<>();
        for (LocalTime t = LocalTime.of(8, 0); t.isBefore(LocalTime.of(17, 0)); t = t.plusMinutes(INCREMENT)) {
            boolean gap1 = !t.isBefore(LocalTime.of(10, 0)) && t.isBefore(LocalTime.of(10, 15));
            boolean gap2 = !t.isBefore(LocalTime.of(13, 0)) && t.isBefore(LocalTime.of(14, 0));
            if (!gap1 && !gap2) {
                seats.add(seat(a, t));
            }
        }
        AgentDayConfig cfg = dayConfig(a);

        assertGatedOffInShiftUnchangedInSlot(ScheduleConstraintProvider::exactlyOneBreak,
                seats, cfg, /* expectedSlotPenalty */ 1);
    }

    // ------------------------------------------------------------------
    //  Scenario B -- breakDuration: single gap, wrong length (30min instead of 60min)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("breakDuration: wrong-length single gap -- inert under SHIFT, unchanged under SLOT")
    void breakDuration_wrongLength() {
        Agent a = agent();
        List<AgentAssignment> seats = seatsExcept(a, LocalTime.of(8, 0), LocalTime.of(17, 0),
                LocalTime.of(12, 0), LocalTime.of(12, 30)); // 30-min gap, expected 60
        AgentDayConfig cfg = dayConfig(a);

        assertGatedOffInShiftUnchangedInSlot(ScheduleConstraintProvider::breakDuration,
                seats, cfg, 1);
    }

    // ------------------------------------------------------------------
    //  Scenario C -- breakBlockedWindow: correctly-sized, aligned gap inside the blocked zone
    // ------------------------------------------------------------------

    @Test
    @DisplayName("breakBlockedWindow: gap inside the blocked zone -- inert under SHIFT, unchanged under SLOT")
    void breakBlockedWindow_gapInBlockedZone() {
        Agent a = agent();
        // Shift effectively spans 07:45-17:00 (shiftStart derived from assignments, not
        // ScheduleConfig operating hours); blockedStartEnd = 07:45+60min = 08:45. A 60-min gap at
        // 08:00-09:00 starts before that -- a blocked-window violation -- while itself being
        // ON_HOUR aligned and exactly the expected 60-min length, so breakStartAlignment/
        // breakDuration/exactlyOneBreak all stay silent on this same fixture (isolated).
        List<AgentAssignment> seats = seatsExcept(a, LocalTime.of(7, 45), LocalTime.of(17, 0),
                LocalTime.of(8, 0), LocalTime.of(9, 0));
        AgentDayConfig cfg = dayConfig(a);

        assertGatedOffInShiftUnchangedInSlot(ScheduleConstraintProvider::breakBlockedWindow,
                seats, cfg, 1);
    }

    // ------------------------------------------------------------------
    //  Scenario D -- breakStartAlignment: correctly-sized, correctly-positioned, off-alignment gap
    // ------------------------------------------------------------------

    @Test
    @DisplayName("breakStartAlignment: off-alignment gap -- inert under SHIFT, unchanged under SLOT")
    void breakStartAlignment_offAlignment() {
        Agent a = agent();
        // 60-min gap at 12:15-13:15 -- correct length, well clear of the blocked zone (shift
        // 08:00-17:00, blocked zone is the first/last hour), but starts at :15 not an hour mark.
        List<AgentAssignment> seats = seatsExcept(a, LocalTime.of(8, 0), LocalTime.of(17, 0),
                LocalTime.of(12, 15), LocalTime.of(13, 15));
        AgentDayConfig cfg = dayConfig(a);

        assertGatedOffInShiftUnchangedInSlot(ScheduleConstraintProvider::breakStartAlignment,
                seats, cfg, 1);
    }

    // ------------------------------------------------------------------
    //  Scenario E -- honourPreferredStartTime: a seat before the preferred start time
    // ------------------------------------------------------------------

    @Test
    @DisplayName("honourPreferredStartTime: a seat before the preference -- inert under SHIFT, unchanged under SLOT")
    void honourPreferredStartTime_seatBeforePreference() {
        Agent a = agent();
        AgentAssignment early = seat(a, LocalTime.of(8, 45)); // before the 09:00 preference
        AgentPreference pref = new AgentPreference();
        pref.setId(UUID.randomUUID());
        pref.setAgent(a);
        pref.setDate(DAY);
        pref.setPreferredStartTime(LocalTime.of(9, 0));

        verifier.verifyThat(ScheduleConstraintProvider::honourPreferredStartTime)
                .given(early, pref, scheduleConfig(SchedulingMode.SHIFT))
                .penalizesBy(0);
        verifier.verifyThat(ScheduleConstraintProvider::honourPreferredStartTime)
                .given(early, pref, scheduleConfig(SchedulingMode.SLOT))
                .penalizesBy(1);
    }

    // ------------------------------------------------------------------
    //  Scenario F -- honourPreferredBreakTime: a gap not at the preferred break time
    // ------------------------------------------------------------------

    @Test
    @DisplayName("honourPreferredBreakTime: a gap not at the preference -- inert under SHIFT, unchanged under SLOT")
    void honourPreferredBreakTime_gapNotAtPreference() {
        Agent a = agent();
        // 60-min gap at 13:00-14:00 (ON_HOUR aligned, clear of the blocked zone, correct length --
        // isolated from the other four break constraints), preference asks for 12:00.
        List<AgentAssignment> seats = seatsExcept(a, LocalTime.of(8, 0), LocalTime.of(17, 0),
                LocalTime.of(13, 0), LocalTime.of(14, 0));
        AgentPreference pref = new AgentPreference();
        pref.setId(UUID.randomUUID());
        pref.setAgent(a);
        pref.setDate(DAY);
        pref.setPreferredBreakTime(LocalTime.of(12, 0));

        List<Object> factsShift = new ArrayList<>(seats);
        factsShift.add(pref);
        factsShift.add(scheduleConfig(SchedulingMode.SHIFT));
        verifier.verifyThat(ScheduleConstraintProvider::honourPreferredBreakTime)
                .given(factsShift.toArray())
                .penalizesBy(0);

        List<Object> factsSlot = new ArrayList<>(seats);
        factsSlot.add(pref);
        factsSlot.add(scheduleConfig(SchedulingMode.SLOT));
        verifier.verifyThat(ScheduleConstraintProvider::honourPreferredBreakTime)
                .given(factsSlot.toArray())
                .penalizesBy(1);
    }

    // ------------------------------------------------------------------
    //  Shared assertion helper for the four AgentDayConfig-joined constraints (A-D)
    // ------------------------------------------------------------------

    private void assertGatedOffInShiftUnchangedInSlot(
            java.util.function.BiFunction<ScheduleConstraintProvider,
                    ai.timefold.solver.core.api.score.stream.ConstraintFactory,
                    ai.timefold.solver.core.api.score.stream.Constraint> constraint,
            List<AgentAssignment> seats, AgentDayConfig cfg, int expectedSlotPenalty) {
        List<Object> factsShift = new ArrayList<>(seats);
        factsShift.add(cfg);
        factsShift.add(scheduleConfig(SchedulingMode.SHIFT));
        verifier.verifyThat(constraint).given(factsShift.toArray()).penalizesBy(0);

        List<Object> factsSlot = new ArrayList<>(seats);
        factsSlot.add(cfg);
        factsSlot.add(scheduleConfig(SchedulingMode.SLOT));
        verifier.verifyThat(constraint).given(factsSlot.toArray()).penalizesBy(expectedSlotPenalty);
    }

    // ------------------------------------------------------------------
    //  ENVL-04 -- structural consequence, proven on a solved shift-mode fixture
    // ------------------------------------------------------------------

    /**
     * ENVL-04: "an agent's working day on a shift-scheduled desk is contiguous, apart from their
     * break" is a structural consequence of {@code shiftEnvelopeCompliance} (forbids every seat
     * outside the envelope and inside the assigned band's break) rather than a rule any new
     * constraint enforces (15-06-PLAN.md flagged assumption). Solves
     * {@link ShiftModeFixtures#buildShiftModeSchedule} through the real {@code solverConfig.xml}
     * (mirroring {@code ShiftEnvelopeGroundTruthTest}'s pattern) and, for every seated agent-day,
     * sorts its held timeslots and confirms the only discontinuity is the assigned band's break
     * interval -- no second gap, and no seat outside the envelope (the latter re-confirms, on this
     * fixture, what {@code ShiftEnvelopeComplianceConstraintTest}/{@code ShiftEnvelopeGroundTruthTest}
     * already prove in general).
     */
    @Test
    @DisplayName("ENVL-04: every seated agent-day is contiguous except exactly its assigned band's break")
    void everySeatedAgentDay_contiguousExceptTheAssignedBreak() {
        Schedule solved = solveShiftModeFixture();
        assertThat(solved.getScore().hardScore())
                .as("the fixture must be feasible before its contiguity is checked")
                .isZero();

        record AgentDateKey(UUID agentId, LocalDate date) {}
        var shiftByAgentDate = new java.util.HashMap<AgentDateKey, AgentShiftAssignment>();
        for (AgentShiftAssignment sa : solved.getShiftAssignments()) {
            shiftByAgentDate.put(new AgentDateKey(sa.getAgent().getId(), sa.getDate()), sa);
        }

        var seatsByAgentDate = new java.util.LinkedHashMap<AgentDateKey, List<AgentAssignment>>();
        for (AgentAssignment assignment : solved.getAssignments()) {
            if (assignment.getAgent() == null) {
                continue;
            }
            AgentDateKey key = new AgentDateKey(assignment.getAgent().getId(), assignment.getTimeslot().getDate());
            seatsByAgentDate.computeIfAbsent(key, k -> new ArrayList<>()).add(assignment);
        }
        assertThat(seatsByAgentDate)
                .as("sanity: at least one agent-day must actually hold seats for this check to be meaningful")
                .isNotEmpty();

        for (var entry : seatsByAgentDate.entrySet()) {
            AgentShiftAssignment shiftRow = shiftByAgentDate.get(entry.getKey());
            assertThat(shiftRow)
                    .as("every seated agent-day must have a chosen shift envelope")
                    .isNotNull();
            ShiftBandPair pair = shiftRow.getShiftBandPair();
            assertThat(pair)
                    .as("a feasible (0hard) solve cannot leave a seated agent-day's shift unassigned "
                            + "(shiftEnvelopeCompliance forbids every seat when the pair is null)")
                    .isNotNull();

            List<LocalTime> starts = entry.getValue().stream()
                    .map(a -> a.getTimeslot().getStartTime())
                    .sorted()
                    .toList();

            LocalTime envelopeStart = pair.template().getStartTime();
            LocalTime envelopeEnd = pair.template().getEndTime();
            for (LocalTime start : starts) {
                assertThat(start).as("no seat may start before the envelope").isBefore(envelopeEnd);
                assertThat(start).as("no seat may start before the envelope").isAfterOrEqualTo(envelopeStart);
            }

            int discontinuities = 0;
            for (int i = 1; i < starts.size(); i++) {
                LocalTime prevEnd = starts.get(i - 1).plusMinutes(ShiftModeFixtures.INCREMENT_MINUTES);
                if (!prevEnd.equals(starts.get(i))) {
                    discontinuities++;
                    if (pair.band() != null) {
                        LocalTime breakStart = envelopeStart.plusMinutes(pair.band().getOffsetMinutes());
                        LocalTime breakEnd = breakStart.plusMinutes(pair.band().getDurationMinutes());
                        assertThat(prevEnd)
                                .as("the sole discontinuity must be exactly the assigned band's break start")
                                .isEqualTo(breakStart);
                        assertThat(starts.get(i))
                                .as("the sole discontinuity must be exactly the assigned band's break end")
                                .isEqualTo(breakEnd);
                    }
                }
            }
            assertThat(discontinuities)
                    .as("agent=%s date=%s must have at most one discontinuity (the break)",
                            entry.getKey().agentId(), entry.getKey().date())
                    .isLessThanOrEqualTo(1);
            if (pair.band() == null) {
                assertThat(discontinuities)
                        .as("a null band (zero bands = no break, P-02) means no discontinuity at all")
                        .isZero();
            }
        }
    }

    private static Schedule solveShiftModeFixture() {
        Schedule unsolved = ShiftModeFixtures.buildShiftModeSchedule(2, 1, 2, 1).schedule();
        SolverConfig solverConfig = SolverConfig.createFromXmlResource("solverConfig.xml");
        List<PhaseConfig> phases = solverConfig.getPhaseConfigList();
        phases.get(phases.size() - 1)
                .setTerminationConfig(new TerminationConfig().withStepCountLimit(20_000));
        SolverFactory<Schedule> solverFactory = SolverFactory.create(solverConfig);
        Solver<Schedule> solver = solverFactory.buildSolver();
        return solver.solve(unsolved);
    }
}
